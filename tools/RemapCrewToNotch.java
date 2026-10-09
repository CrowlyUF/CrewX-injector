import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

public final class RemapCrewToNotch {
    private static final class Mapping extends Remapper {
        final Map<String,String> classes = new HashMap<>();
        final Map<String,String> namedClasses = new HashMap<>();
        final Map<String,String> mcpMethods = new HashMap<>();
        final Map<String,String> mcpFields = new HashMap<>();
        final Map<String,String> methods = new HashMap<>();
        final Map<String,String> fields = new HashMap<>();
        final Map<String,List<String>> gameParents = new HashMap<>();
        private static final String[] ENTITY_ANCESTORS = {
                "net/minecraft/client/entity/AbstractClientPlayer",
                "net/minecraft/entity/player/EntityPlayer",
                "net/minecraft/entity/EntityLivingBase",
                "net/minecraft/entity/Entity"
        };
        private static final String[] WORLD_ANCESTORS = {
                "net/minecraft/client/multiplayer/WorldClient",
                "net/minecraft/world/World"
        };

        private boolean entitySubclass(String owner) {
            return owner.startsWith("net/minecraft/client/entity/")
                    || owner.startsWith("net/minecraft/entity/player/")
                    || "net/minecraft/entity/EntityLivingBase".equals(owner);
        }

        private boolean worldSubclass(String owner) {
            return owner.startsWith("net/minecraft/client/multiplayer/WorldClient")
                    || "net/minecraft/world/World".equals(owner);
        }

        private boolean crewGuiScreen(String owner) {
            return ("crewx/gui/ClickGui".equals(owner)
                    || "crewx/gui/CrewXMainMenu".equals(owner)
                    || owner.startsWith("crewx/accountmanager/gui/Gui"))
                    && !owner.contains("$");
        }

        private boolean crewGuiSlot(String owner) {
            return owner.startsWith("crewx/accountmanager/gui/Gui")
                    && (owner.endsWith("$GuiAccountList") || owner.endsWith("$StatsList"));
        }

        Mapping(Path joined, Path methodsCsv, Path fieldsCsv, Path gameJar) throws IOException {
            loadCsv(methodsCsv, mcpMethods);
            loadCsv(fieldsCsv, mcpFields);
            for (String line : Files.readAllLines(joined)) {
                String[] p = line.trim().split(" +");
                if (p.length == 3 && "CL:".equals(p[0])) {
                    classes.put(p[2], p[1]);
                    namedClasses.put(p[1], p[2]);
                } else if (p.length == 3 && "FD:".equals(p[0])) {
                    String owner = p[2].substring(0, p[2].lastIndexOf('/'));
                    String srg = p[2].substring(p[2].lastIndexOf('/') + 1);
                    String mcp = mcpFields.getOrDefault(srg, srg);
                    fields.put(owner + "/" + mcp,
                            p[1].substring(p[1].lastIndexOf('/') + 1));
                } else if (p.length == 5 && "MD:".equals(p[0])) {
                    String owner = p[3].substring(0, p[3].lastIndexOf('/'));
                    String srg = p[3].substring(p[3].lastIndexOf('/') + 1);
                    String mcp = mcpMethods.getOrDefault(srg, srg);
                    methods.put(owner + "/" + mcp + " " + p[4],
                            p[1].substring(p[1].lastIndexOf('/') + 1));
                }
            }
            methods.put("net/minecraft/entity/EntityLivingBase/moveFlying (FFF)V", "a");
            if (gameJar != null) {
                try (JarFile jar = new JarFile(gameJar.toFile())) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (!entry.getName().endsWith(".class")) continue;
                        try (InputStream in = jar.getInputStream(entry)) {
                            ClassReader reader = new ClassReader(in);
                            List<String> parents = new ArrayList<>();
                            if (reader.getSuperName() != null) parents.add(reader.getSuperName());
                            parents.addAll(Arrays.asList(reader.getInterfaces()));
                            gameParents.put(reader.getClassName(), parents);
                        }
                    }
                }
            }
        }

        private String inherited(String namedOwner, String suffix, Map<String,String> mappings) {
            String obfOwner = classes.getOrDefault(namedOwner, namedOwner);
            ArrayDeque<String> queue = new ArrayDeque<>(
                    gameParents.getOrDefault(obfOwner, Collections.emptyList()));
            Set<String> seen = new HashSet<>();
            while (!queue.isEmpty()) {
                String obfParent = queue.removeFirst();
                if (!seen.add(obfParent)) continue;
                String namedParent = namedClasses.getOrDefault(obfParent, obfParent);
                String value = mappings.get(namedParent + "/" + suffix);
                if (value != null) return value;
                queue.addAll(gameParents.getOrDefault(obfParent, Collections.emptyList()));
            }
            return null;
        }

        static void loadCsv(Path path, Map<String,String> out) throws IOException {
            List<String> lines = Files.readAllLines(path);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                int a = line.indexOf(',');
                int b = a < 0 ? -1 : line.indexOf(',', a + 1);
                if (a > 0 && b > a) {
                    String srg = line.substring(0, a).replace("\"", "");
                    String mcp = line.substring(a + 1, b).replace("\"", "");
                    out.put(srg, mcp);
                }
            }
        }

        @Override public String map(String internalName) {
            return classes.getOrDefault(internalName, internalName);
        }

        @Override public String mapFieldName(String owner, String name, String descriptor) {
            String namedOwner = namedClasses.getOrDefault(owner, owner);
            String direct = fields.get(namedOwner + "/" + name);
            if (direct != null) return direct;
            String fromParents = inherited(namedOwner, name, fields);
            if (fromParents != null) return fromParents;
            if (crewGuiScreen(namedOwner)) {
                String inherited = fields.get("net/minecraft/client/gui/GuiScreen/" + name);
                if (inherited == null) {
                    inherited = fields.get("net/minecraft/client/gui/Gui/" + name);
                }
                if (inherited != null) return inherited;
            }
            if (crewGuiSlot(namedOwner)) {
                String inherited = fields.get("net/minecraft/client/gui/GuiSlot/" + name);
                if (inherited != null) return inherited;
            }
            if (entitySubclass(namedOwner)) {
                for (String ancestor : ENTITY_ANCESTORS) {
                    String inherited = fields.get(ancestor + "/" + name);
                    if (inherited != null) return inherited;
                }
            }
            if (worldSubclass(namedOwner)) {
                for (String ancestor : WORLD_ANCESTORS) {
                    String inherited = fields.get(ancestor + "/" + name);
                    if (inherited != null) return inherited;
                }
            }
            return name;
        }

        @Override public String mapMethodName(String owner, String name, String descriptor) {
            if (name.startsWith("<")) return name;
            String namedOwner = namedClasses.getOrDefault(owner, owner);
            String direct = methods.get(namedOwner + "/" + name + " " + descriptor);
            if (direct != null) return direct;
            if ("crewx/cosmetics/CosmeticLayer".equals(namedOwner)) {
                String layerMethod = methods.get(
                        "net/minecraft/client/renderer/entity/layers/LayerRenderer/"
                                + name + " " + descriptor);
                if (layerMethod != null) return layerMethod;
            }
            String fromParents = inherited(namedOwner, name + " " + descriptor, methods);
            if (fromParents != null) return fromParents;
            if (crewGuiScreen(namedOwner)) {
                String override = methods.get("net/minecraft/client/gui/GuiScreen/"
                        + name + " " + descriptor);
                if (override == null) {
                    override = methods.get("net/minecraft/client/gui/Gui/"
                            + name + " " + descriptor);
                }
                if (override != null) return override;
            }
            if (crewGuiSlot(namedOwner)) {
                String override = methods.get("net/minecraft/client/gui/GuiSlot/"
                        + name + " " + descriptor);
                if (override != null) return override;
            }
            if (entitySubclass(namedOwner)) {
                for (String ancestor : ENTITY_ANCESTORS) {
                    String inherited = methods.get(ancestor + "/" + name + " " + descriptor);
                    if (inherited != null) return inherited;
                }
            }
            if (worldSubclass(namedOwner)) {
                for (String ancestor : WORLD_ANCESTORS) {
                    String inherited = methods.get(ancestor + "/" + name + " " + descriptor);
                    if (inherited != null) return inherited;
                }
            }
            return name;
        }
    }

    public static void main(String[] args) throws Exception {
        Mapping mapping = new Mapping(Paths.get(args[2]), Paths.get(args[3]), Paths.get(args[4]),
                args.length > 5 ? Paths.get(args[5]) : null);
        int remapped = 0;
        try (JarFile input = new JarFile(args[0]);
             JarOutputStream output = new JarOutputStream(Files.newOutputStream(Paths.get(args[1])))) {
            Enumeration<JarEntry> entries = input.entries();
            Set<String> written = new HashSet<>();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!written.add(entry.getName())) continue;
                JarEntry copy = new JarEntry(entry.getName());
                copy.setTime(entry.getTime());
                output.putNextEntry(copy);
                if (!entry.isDirectory()) {
                    byte[] data;
                    try (InputStream stream = input.getInputStream(entry)) {
                        data = readAll(stream);
                    }
                    if (entry.getName().startsWith("crewx/")
                            && !entry.getName().startsWith("crewx/inject/deps/")
                            && !entry.getName().startsWith("crewx/inject/CrewXRuntimeTransformer")
                            && !entry.getName().startsWith("crewx/inject/NotchMappings")
                            && entry.getName().endsWith(".class")) {
                        ClassReader reader = new ClassReader(data);
                        ClassWriter writer = new ClassWriter(0);
                        reader.accept(new ClassRemapper(writer, mapping), 0);
                        data = writer.toByteArray();
                        remapped++;
                    }
                    output.write(data);
                }
                output.closeEntry();
            }
        }
        System.out.println("Remapped CrewX classes: " + remapped);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}
