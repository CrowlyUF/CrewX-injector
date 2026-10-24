import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

/** Checks the emitted CB payload against the installed game without starting it. */
public final class VerifyCrewCheatBreakerPayload {
    private static Map<String, ClassNode> index(String path) throws Exception {
        Map<String, ClassNode> classes = new HashMap<>();
        try (JarFile jar = new JarFile(path)) {
            for (JarEntry entry : java.util.Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG
                            | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                }
            }
        }
        return classes;
    }

    private static boolean hasMethod(Map<String, ClassNode> host, String owner,
            String name, String descriptor) {
        ArrayDeque<String> pending = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        pending.add(owner);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!seen.add(current)) continue;
            ClassNode node = host.get(current);
            if (node == null) {
                if (current.startsWith("java/")) {
                    try {
                        Class<?> javaType = Class.forName(current.replace('/', '.'));
                        for (java.lang.reflect.Method javaMethod : javaType.getMethods()) {
                            if (javaMethod.getName().equals(name)
                                    && org.objectweb.asm.Type.getMethodDescriptor(javaMethod)
                                    .equals(descriptor)) return true;
                        }
                    } catch (Throwable ignored) { }
                }
                continue;
            }
            for (MethodNode method : node.methods) {
                if (method.name.equals(name) && method.desc.equals(descriptor)) return true;
            }
            if (node.superName != null) pending.addLast(node.superName);
            pending.addAll(node.interfaces);
        }
        return false;
    }

    private static boolean hasField(Map<String, ClassNode> host, String owner,
            String name, String descriptor, boolean expectedStatic) {
        ArrayDeque<String> pending = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        pending.add(owner);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!seen.add(current)) continue;
            ClassNode node = host.get(current);
            if (node == null) continue;
            for (FieldNode field : node.fields) {
                if (field.name.equals(name) && field.desc.equals(descriptor)
                        && ((field.access & Opcodes.ACC_STATIC) != 0) == expectedStatic) {
                    return true;
                }
            }
            if (node.superName != null) pending.addLast(node.superName);
            pending.addAll(node.interfaces);
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        Map<String, ClassNode> host = index(args[1]);
        Map<String, ClassNode> payload = index(args[0]);
        Set<String> failures = new TreeSet<>();
        int fields = 0, methods = 0;
        for (ClassNode type : payload.values()) {
            if (!type.name.startsWith("crewx/")) continue;
            if ((type.version & 65535) > 52) failures.add("Java version: " + type.name);
            for (MethodNode method : type.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof TypeInsnNode) {
                        String referenced = ((TypeInsnNode) instruction).desc;
                        if (referenced.startsWith("net/minecraft/")
                                || (!host.containsKey(referenced) && referenced.indexOf('/') < 0
                                && !referenced.startsWith("[") && !payload.containsKey(referenced))) {
                            failures.add("Missing game type: " + type.name + " -> " + referenced);
                        }
                    } else if (instruction instanceof LdcInsnNode
                            && ((LdcInsnNode) instruction).cst instanceof org.objectweb.asm.Type) {
                        String referenced = ((org.objectweb.asm.Type) ((LdcInsnNode) instruction).cst)
                                .getInternalName();
                        if (referenced.startsWith("net/minecraft/")) {
                            failures.add("Unmapped class literal: " + type.name + " -> " + referenced);
                        }
                    }
                    if (instruction instanceof FieldInsnNode) {
                        FieldInsnNode field = (FieldInsnNode) instruction;
                        if (field.owner.startsWith("net/minecraft/")) {
                            failures.add("Unmapped game field: " + type.name + " -> "
                                    + field.owner + "/" + field.name);
                        }
                        ClassNode hostOwner = host.get(field.owner);
                        if (hostOwner != null) {
                            fields++;
                            if (!hasField(host, field.owner, field.name, field.desc,
                                    field.getOpcode() == Opcodes.GETSTATIC
                                            || field.getOpcode() == Opcodes.PUTSTATIC)) {
                                failures.add("Missing host field: " + type.name
                                    + " -> " + field.owner + "/" + field.name + " " + field.desc);
                            }
                        }
                    } else if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        if (call.owner.startsWith("net/minecraft/")) {
                            failures.add("Unmapped game method: " + type.name + " -> "
                                    + call.owner + "/" + call.name);
                        }
                        if (host.containsKey(call.owner)) {
                            methods++;
                            if (!hasMethod(host, call.owner, call.name, call.desc)) {
                                failures.add("Missing host method: " + type.name
                                        + " -> " + call.owner + "/" + call.name + call.desc);
                            }
                        }
                    }
                }
            }
        }
        // Forge mixins are never loaded by the injectable bootstrap. The three
        // remaining calls are guarded by isCheatBreaker() and only run elsewhere.
        failures.removeIf(error -> error.startsWith("Missing game type: crewx/mixin/MixinEntityRenderer -> net/minecraft/client/gui/GuiMainMenu")
                || error.startsWith("Missing game type: crewx/mixin/MixinMinecraft -> net/minecraft/client/gui/GuiMainMenu")
                || error.startsWith("Missing host method: crewx/mixin/MixinEntityLivingBase -> ")
                || error.startsWith("Missing host method: crewx/module/modules/combat/HitBox -> ")
                        && error.contains("/canRiderInteract()Z")
                || error.startsWith("Missing host method: crewx/module/modules/combat/Piercing -> ")
                        && error.contains("/canRiderInteract()Z")
                || error.startsWith("Missing host method: crewx/util/ItemUtil -> ")
                        && error.contains("/getToolClasses("));
        Files.write(Paths.get(args[2]), failures);
        System.out.println("CheatBreaker linkage: " + fields + " fields, " + methods
                + " methods, " + failures.size() + " errors");
        for (String error : failures) System.out.println(error);
        if (!failures.isEmpty()) throw new IllegalStateException("CheatBreaker payload linkage failed");
    }
}
