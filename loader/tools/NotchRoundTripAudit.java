import crewx.inject.NotchMappings;
import crewx.inject.CrewXRuntimeTransformer;
import crewx.inject.CrewXBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;


public final class NotchRoundTripAudit {
    public static void main(String[] args) throws Exception {
        int checked = 0;
        try (ZipFile jar = new ZipFile(args[0])) {
            String[] names;
            if ("all".equals(args[1])) {
                java.lang.reflect.Field targets = CrewXBootstrap.class.getDeclaredField("RUNTIME_HOOK_TARGETS");
                targets.setAccessible(true);
                names = (String[]) targets.get(null);
            } else {
                names = args[1].split(",");
            }
            for (String requested : names) {
                String name = requested.indexOf('.') >= 0
                        ? NotchMappings.className(requested).replace('.', '/') : requested;
                java.util.zip.ZipEntry entry = jar.getEntry(name + ".class");
                if (entry == null) {
                    System.out.println("absent from vanilla jar: " + requested);
                    continue;
                }
                InputStream input = jar.getInputStream(entry);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int count;
                while ((count = input.read(chunk)) >= 0) buffer.write(chunk, 0, count);
                input.close();
                byte[] original = buffer.toByteArray();
                byte[] named = NotchMappings.toNamed(original);
                byte[] roundTrip = NotchMappings.toNotch(named);
                String actual = new ClassReader(roundTrip).getClassName();
                if (!name.equals(actual)) {
                    throw new IllegalStateException(name + " became " + actual);
                }
                System.out.println(name + " -> " + new ClassReader(named).getClassName()
                        + " -> " + actual + " (" + original.length + "/"
                        + named.length + "/" + roundTrip.length + " bytes)");
                byte[] hooked = CrewXRuntimeTransformer.transform(
                        new ClassReader(named).getClassName(), named);
                if (hooked != null) {
                    String hookedName = new ClassReader(NotchMappings.toNotch(hooked)).getClassName();
                    if (!name.equals(hookedName)) {
                        throw new IllegalStateException("Hook changed class name: " + hookedName);
                    }
                    System.out.println("  hook generated: " + hooked.length + " bytes");
                }
                checked++;
            }
        }
        System.out.println("Round trips passed: " + checked);
    }
}
