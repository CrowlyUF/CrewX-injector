import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Composes MCP's official mappings with Ornithe Calamus intermediary gen2. */
public final class GenerateOrnitheSrg {
    private static String descriptor(String source, Map<String, String> classes) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < source.length();) {
            if (source.charAt(i) == 'L') {
                int end = source.indexOf(';', i);
                if (end < 0) throw new IllegalArgumentException("Invalid descriptor: " + source);
                String name = source.substring(i + 1, end);
                out.append('L').append(classes.getOrDefault(name, name)).append(';');
                i = end + 1;
            } else {
                out.append(source.charAt(i++));
            }
        }
        return out.toString();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("joined.srg tiny-v1 output.srg");
        List<String> tiny = Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8);
        if (tiny.isEmpty() || !"v1\tofficial\tintermediary".equals(tiny.get(0))) {
            throw new IllegalArgumentException("Unexpected Ornithe mapping header");
        }
        Map<String, String> classes = new HashMap<>();
        Map<String, String> fields = new HashMap<>();
        Map<String, String> methods = new HashMap<>();
        for (String line : tiny) {
            String[] p = line.split("\t");
            if (p.length == 3 && "CLASS".equals(p[0])) {
                classes.put(p[1], p[2]);
            } else if (p.length == 5 && "FIELD".equals(p[0])) {
                fields.put(p[1] + '/' + p[3], p[4]);
            } else if (p.length == 5 && "METHOD".equals(p[0])) {
                methods.put(p[1] + '/' + p[3] + ' ' + p[2], p[4]);
            }
        }
        if (!"net/minecraft/client/Minecraft".equals(classes.get("ave"))
                || !"m_20213497".equals(methods.get("ave/A ()Lave;"))) {
            throw new IllegalArgumentException("Ornithe 1.8.9 sentinels changed");
        }
        StringBuilder out = new StringBuilder();
        int classCount = 0, fieldCount = 0, methodCount = 0, missing = 0;
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            String[] p = line.trim().split(" +");
            if (p.length == 3 && "CL:".equals(p[0])) {
                String mapped = classes.get(p[1]);
                if (mapped == null) { missing++; continue; }
                out.append("CL: ").append(mapped).append(' ').append(p[2]).append('\n');
                classCount++;
            } else if (p.length == 3 && "FD:".equals(p[0])) {
                String official = p[1];
                int slash = official.lastIndexOf('/');
                if (slash < 0) continue;
                String owner = official.substring(0, slash);
                String mappedOwner = classes.get(owner);
                String mappedField = fields.get(official);
                if (mappedOwner == null || mappedField == null) { missing++; continue; }
                out.append("FD: ").append(mappedOwner).append('/').append(mappedField)
                        .append(' ').append(p[2]).append('\n');
                fieldCount++;
            } else if (p.length == 5 && "MD:".equals(p[0])) {
                String official = p[1];
                int slash = official.lastIndexOf('/');
                if (slash < 0) continue;
                String owner = official.substring(0, slash);
                String mappedOwner = classes.get(owner);
                String mappedMethod = methods.get(official + ' ' + p[2]);
                // Calamus records IThreadListener's inherited method on the interface only.
                if (mappedMethod == null && "ave/a".equals(official)
                        && "(Ljava/lang/Runnable;)Lcom/google/common/util/concurrent/ListenableFuture;".equals(p[2])) {
                    mappedMethod = methods.get("od/a " + p[2]);
                }
                if (mappedOwner == null || mappedMethod == null) { missing++; continue; }
                out.append("MD: ").append(mappedOwner).append('/').append(mappedMethod)
                        .append(' ').append(descriptor(p[2], classes))
                        .append(' ').append(p[3]).append(' ').append(p[4]).append('\n');
                methodCount++;
            }
        }
        // Calamus contains client-side mappings; MCP's joined SRG also contains server-only members.
        if (classCount < 2000 || fieldCount < 8000 || methodCount < 8000 || missing > 9000) {
            throw new IllegalArgumentException("Incomplete Ornithe mapping: " + classCount
                    + " classes, " + fieldCount + " fields, " + methodCount
                    + " methods, " + missing + " missing");
        }
        Files.write(Paths.get(args[2]), out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("Ornithe mapping: " + classCount + " classes, " + fieldCount
                + " fields, " + methodCount + " methods, " + missing + " missing");
    }
}
