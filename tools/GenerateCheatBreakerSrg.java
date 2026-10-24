import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Converts the audited named -> CheatBreaker Tiny v2 mapping for the payload remapper. */
public final class GenerateCheatBreakerSrg {
    private static String descriptor(String source, Map<String, String> classes) {
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < source.length();) {
            if (source.charAt(index) == 'L') {
                int end = source.indexOf(';', index);
                if (end < 0) throw new IllegalArgumentException("Invalid descriptor: " + source);
                String named = source.substring(index + 1, end);
                out.append('L').append(classes.getOrDefault(named, named)).append(';');
                index = end + 1;
            } else {
                out.append(source.charAt(index++));
            }
        }
        return out.toString();
    }

    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals("tiny\t2\t0\tnamed\tcheatbreaker")) {
            throw new IllegalArgumentException("Unexpected CheatBreaker Tiny mapping");
        }
        Map<String, String> classes = new HashMap<>();
        for (String line : lines) {
            String[] p = line.split("\t");
            if (p.length == 3 && p[0].equals("c")) classes.put(p[1], p[2]);
        }
        if (!"IlIlIlIIllIllIIIIIIIIIIlI".equals(
                classes.get("net/minecraft/client/Minecraft"))) {
            throw new IllegalArgumentException("CheatBreaker Minecraft sentinel changed");
        }
        StringBuilder out = new StringBuilder();
        String owner = null;
        int fields = 0, methods = 0;
        for (String line : lines) {
            String[] p = line.split("\t");
            if (p.length == 3 && p[0].equals("c")) {
                owner = p[1];
                out.append("CL: ").append(p[2]).append(' ').append(owner).append('\n');
            } else if (owner != null && p.length == 5 && p[1].equals("f")) {
                out.append("FD: ").append(classes.get(owner)).append('/').append(p[4])
                        .append(' ').append(owner).append('/').append(p[3]).append('\n');
                fields++;
            } else if (owner != null && p.length == 5 && p[1].equals("m")) {
                out.append("MD: ").append(classes.get(owner)).append('/').append(p[4])
                        .append(' ').append(descriptor(p[2], classes))
                        .append(' ').append(owner).append('/').append(p[3])
                        .append(' ').append(p[2]).append('\n');
                methods++;
            }
        }
        if (classes.size() < 1500 || fields < 6000 || methods < 11000) {
            throw new IllegalArgumentException("Incomplete CheatBreaker mappings");
        }
        Files.write(Paths.get(args[1]), out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("CheatBreaker mapping: " + classes.size() + " classes, "
                + fields + " fields, " + methods + " methods");
    }
}
