import java.io.InputStream;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

public final class FindCheatBreakerFormatHolder {
    public static void main(String[] args) throws Exception {
        String format = "L" + args[1] + ";";
        try (JarFile jar = new JarFile(args[0])) {
            for (JarEntry entry : Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode type = new ClassNode();
                    new ClassReader(input).accept(type, ClassReader.SKIP_CODE
                            | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    int count = 0;
                    for (FieldNode field : type.fields) {
                        if (field.desc.equals(format) && (field.access & Opcodes.ACC_STATIC) != 0) {
                            count++;
                        }
                    }
                    if (count >= 2) System.out.println(type.name + " " + count);
                }
            }
        }
    }
}
