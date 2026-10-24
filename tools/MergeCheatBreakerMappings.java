import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Prefer the mappings audited against the local host, then add compatible TXT entries. */
public final class MergeCheatBreakerMappings {
    private static final class Member {
        final String kind, descriptor, named, runtime;
        Member(String kind, String descriptor, String named, String runtime) {
            this.kind = kind;
            this.descriptor = descriptor;
            this.named = named;
            this.runtime = runtime;
        }
        String key() { return kind + "\t" + named + "\t" + descriptor; }
        String line() { return "\t" + kind + "\t" + descriptor + "\t" + named + "\t" + runtime; }
    }

    private static Map<String, String> csv(String path) throws Exception {
        Map<String, String> result = new HashMap<>();
        List<String> lines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); ++i) {
            String[] parts = lines.get(i).replace("\"", "").split(",", 3);
            if (parts.length >= 2) result.put(parts[0], parts[1]);
        }
        return result;
    }

    private static String owner(String full) { return full.substring(0, full.lastIndexOf('/')); }
    private static String simple(String full) { return full.substring(full.lastIndexOf('/') + 1); }

    public static void main(String[] args) throws Exception {
        List<String> original = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);
        List<String> audited = Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8);
        Map<String, String> methodsCsv = csv(args[2]);
        Map<String, String> fieldsCsv = csv(args[3]);
        Map<String, String> originalClasses = new LinkedHashMap<>();
        Map<String, String> classes = new LinkedHashMap<>();
        String current = null;
        Map<String, Map<String, Member>> originalMembers = new HashMap<>();
        for (String line : original) {
            String[] p = line.split("\t");
            if (p.length == 3 && p[0].equals("c")) {
                current = p[1];
                originalClasses.put(current, p[2]);
                classes.put(current, p[2]);
            } else if (current != null && p.length == 5 && (p[1].equals("f") || p[1].equals("m"))) {
                Member member = new Member(p[1], p[2], p[3], p[4]);
                originalMembers.computeIfAbsent(current, k -> new LinkedHashMap<>())
                        .put(member.key(), member);
            }
        }
        for (String line : audited) {
            String[] p = line.trim().split(" +");
            if (p.length == 3 && p[0].equals("CL:")) classes.put(p[1], p[2]);
        }
        Map<String, Map<String, Member>> members = new HashMap<>();
        int skippedConflict = 0;
        for (Map.Entry<String, Map<String, Member>> entry : originalMembers.entrySet()) {
            String namedOwner = entry.getKey();
            if (!originalClasses.get(namedOwner).equals(classes.get(namedOwner))) {
                skippedConflict += entry.getValue().size();
                continue;
            }
            members.put(namedOwner, new LinkedHashMap<>(entry.getValue()));
        }
        int auditedMembers = 0;
        for (String line : audited) {
            String[] p = line.trim().split(" +");
            if (p.length == 3 && p[0].equals("FD:")) {
                String namedOwner = owner(p[1]);
                String named = fieldsCsv.getOrDefault(simple(p[1]), simple(p[1]));
                Map<String, Member> group = members.computeIfAbsent(namedOwner,
                        k -> new LinkedHashMap<>());
                String descriptor = "Ljava/lang/Object;";
                for (Member candidate : group.values()) {
                    if (candidate.kind.equals("f") && candidate.named.equals(named)) {
                        descriptor = candidate.descriptor;
                        break;
                    }
                }
                Member member = new Member("f", descriptor, named, simple(p[2]));
                group.putIfAbsent(member.key(), member);
                auditedMembers++;
            } else if (p.length == 5 && p[0].equals("MD:")) {
                String namedOwner = owner(p[1]);
                String named = methodsCsv.getOrDefault(simple(p[1]), simple(p[1]));
                Member member = new Member("m", p[2], named, simple(p[3]));
                members.computeIfAbsent(namedOwner, k -> new LinkedHashMap<>())
                        .putIfAbsent(member.key(), member);
                auditedMembers++;
            }
        }
        if (!"IlIlIlIIllIllIIIIIIIIIIlI".equals(
                classes.get("net/minecraft/client/Minecraft"))) {
            throw new IllegalArgumentException("CheatBreaker Minecraft mapping changed");
        }
        StringBuilder output = new StringBuilder("tiny\t2\t0\tnamed\tcheatbreaker\n");
        int total = 0;
        for (Map.Entry<String, String> entry : new TreeMap<>(classes).entrySet()) {
            output.append("c\t").append(entry.getKey()).append('\t')
                    .append(entry.getValue()).append('\n');
            Map<String, Member> group = members.get(entry.getKey());
            if (group != null) {
                for (Member member : group.values()) {
                    output.append(member.line()).append('\n');
                    total++;
                }
            }
        }
        Files.write(Paths.get(args[4]), output.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("Merged CB mapping: " + classes.size() + " classes, "
                + total + " members; audited overrides=" + auditedMembers
                + ", skipped conflicting TXT members=" + skippedConflict);
    }
}
