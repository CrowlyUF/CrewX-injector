import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;


public final class AccessorRewriter {
    private static final String ACCESSOR = "crewx/mixin/IAccessor";
    private static final String BRIDGE = "crewx/inject/CrewXAccessors";
    private static int rewrittenCalls;
    private static final Set<String> methods = new HashSet<String>();
    private static final Set<String> bridgeMethods = new HashSet<String>();

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.jar output.jar");
        File input = new File(args[0]);
        File output = new File(args[1]);
        if (input.getCanonicalPath().equals(output.getCanonicalPath())) {
            throw new IllegalArgumentException("Input and output must differ");
        }
        try (JarFile jar = new JarFile(input);
             JarOutputStream out = new JarOutputStream(new FileOutputStream(output))) {
            JarEntry bridge = jar.getJarEntry(BRIDGE + ".class");
            if (bridge == null) throw new IllegalStateException("Missing accessor bridge");
            try (InputStream stream = jar.getInputStream(bridge)) {
                new ClassReader(readAll(stream)).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String desc,
                                                                String signature, String[] exceptions) {
                        if ((access & Opcodes.ACC_STATIC) != 0) bridgeMethods.add(name + desc);
                        return null;
                    }
                }, ClassReader.SKIP_CODE);
            }
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                byte[] bytes;
                try (InputStream stream = jar.getInputStream(entry)) {
                    bytes = readAll(stream);
                }
                if (entry.getName().startsWith("crewx/") && entry.getName().endsWith(".class")) {
                    bytes = rewrite(bytes);
                }
                JarEntry copy = new JarEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(bytes);
                out.closeEntry();
            }
        }
        if (rewrittenCalls == 0) throw new IllegalStateException("No accessor calls rewritten");
        Set<String> missing = new HashSet<String>(methods);
        missing.removeAll(bridgeMethods);
        if (!missing.isEmpty()) throw new IllegalStateException("Missing bridge methods: " + missing);
        System.out.println("Rewrote " + rewrittenCalls + " accessor calls across "
                + methods.size() + " bridge methods");
    }

    private static byte[] readAll(InputStream stream) throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = stream.read(buffer)) != -1;) bytes.write(buffer, 0, n);
        return bytes.toByteArray();
    }

    private static byte[] rewrite(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc,
                                                        String signature, String[] exceptions) {
                MethodVisitor output = super.visitMethod(access, name, desc, signature, exceptions);
                if (output == null) return null;
                return new MethodVisitor(Opcodes.ASM9, output) {
                    @Override public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.CHECKCAST && type.startsWith(ACCESSOR)) return;
                        super.visitTypeInsn(opcode, type);
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                                                           String descriptor, boolean isInterface) {
                        if (opcode != Opcodes.INVOKEINTERFACE || !owner.startsWith(ACCESSOR)) {
                            super.visitMethodInsn(opcode, owner, method, descriptor, isInterface);
                            return;
                        }
                        Type originalReturn = Type.getReturnType(descriptor);
                        Type bridgeReturn = originalReturn.getSort() == Type.OBJECT
                                || originalReturn.getSort() == Type.ARRAY
                                ? Type.getType(Object.class) : originalReturn;
                        Type[] parameters = Type.getArgumentTypes(descriptor);
                        Type[] bridgeParameters = new Type[parameters.length + 1];
                        bridgeParameters[0] = Type.getType(Object.class);
                        for (int i = 0; i < parameters.length; i++) {
                            int sort = parameters[i].getSort();
                            bridgeParameters[i + 1] = sort == Type.OBJECT || sort == Type.ARRAY
                                    ? Type.getType(Object.class) : parameters[i];
                        }
                        String bridgeDescriptor = Type.getMethodDescriptor(bridgeReturn, bridgeParameters);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, method,
                                bridgeDescriptor, false);
                        if (bridgeReturn != originalReturn) {
                            super.visitTypeInsn(Opcodes.CHECKCAST, originalReturn.getInternalName());
                        }
                        rewrittenCalls++;
                        methods.add(method + bridgeDescriptor);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }
}
