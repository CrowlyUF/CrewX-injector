import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class AuditNotchLinks {
    private static final class Info {
        String parent;
        String[] interfaces;
        final Set<String> fields = new HashSet<>();
        final Set<String> methods = new HashSet<>();
    }

    private static final Map<String, Info> classes = new HashMap<>();
    private static final Set<String> missing = new TreeSet<>();

    private static boolean has(String owner, String member, boolean method, Set<String> seen) {
        if (!seen.add(owner)) return false;
        Info info = classes.get(owner);
        if (info == null) return false;
        if ((method ? info.methods : info.fields).contains(member)) return true;
        if (info.parent != null && has(info.parent, member, method, seen)) return true;
        if (info.interfaces != null) {
            for (String iface : info.interfaces) {
                if (has(iface, member, method, seen)) return true;
            }
        }
        return false;
    }

    private static void loadGame(JarFile jar) throws Exception {
        for (JarEntry entry : java.util.Collections.list(jar.entries())) {
            if (!entry.getName().endsWith(".class")) continue;
            try (InputStream in = jar.getInputStream(entry)) {
                ClassReader reader = new ClassReader(in);
                Info info = new Info();
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public void visit(int version, int access, String name,
                            String signature, String superName, String[] interfaces) {
                        info.parent = superName;
                        info.interfaces = interfaces;
                        classes.put(name, info);
                    }
                    @Override public FieldVisitor visitField(int access, String name,
                            String desc, String signature, Object value) {
                        info.fields.add(name + " " + desc);
                        return null;
                    }
                    @Override public MethodVisitor visitMethod(int access, String name,
                            String desc, String signature, String[] exceptions) {
                        info.methods.add(name + " " + desc);
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
    }

    private static void auditPayload(JarFile jar) throws Exception {
        for (JarEntry entry : java.util.Collections.list(jar.entries())) {
            if (!entry.getName().startsWith("crewx/") || !entry.getName().endsWith(".class")) continue;
            try (InputStream in = jar.getInputStream(entry)) {
                ClassReader reader = new ClassReader(in);
                final String source = entry.getName();
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name,
                            String desc, String signature, String[] exceptions) {
                        final String site = source + "#" + name + desc;
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitFieldInsn(int opcode, String owner,
                                    String field, String descriptor) {
                                if (classes.containsKey(owner)
                                        && !has(owner, field + " " + descriptor, false, new HashSet<>())) {
                                    missing.add(site + " FIELD " + owner + "." + field + " " + descriptor);
                                }
                            }
                            @Override public void visitMethodInsn(int opcode, String owner,
                                    String method, String descriptor, boolean itf) {
                                if (classes.containsKey(owner)
                                        && !has(owner, method + " " + descriptor, true, new HashSet<>())) {
                                    missing.add(site + " METHOD " + owner + "." + method + descriptor);
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("game.jar payload.jar");
        try (JarFile game = new JarFile(args[0]); JarFile payload = new JarFile(args[1])) {
            loadGame(game);
            auditPayload(payload);
        }
        System.out.println("gameClasses=" + classes.size() + " missingLinks=" + missing.size());
        for (String link : missing) System.out.println(link);
    }
}
