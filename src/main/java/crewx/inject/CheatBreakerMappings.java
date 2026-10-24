package crewx.inject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/** Mappings for the audited CheatBreaker 1.8.9 build. Lookups are cached at startup. */
public final class CheatBreakerMappings {
    public static final String MINECRAFT_CLASS = "IlIlIlIIllIllIIIIIIIIIIlI";
    public static final String GET_MINECRAFT = "IlIIIllIlllllIlIlIlllIIIl";
    private static final Mapping MAP = load();

    private CheatBreakerMappings() { }

    public static String className(String named) {
        return MAP.toRuntime.getOrDefault(named.replace('.', '/'), named.replace('.', '/'))
                .replace('/', '.');
    }

    public static String classInternalName(String named) {
        return MAP.toRuntime.get(named);
    }

    public static String methodName(String owner, String name, String descriptor) {
        return MAP.member(MAP.methods, owner.replace('.', '/'), name + " " + descriptor);
    }

    public static String fieldName(String owner, String name) {
        return MAP.member(MAP.fields, owner.replace('.', '/'), name);
    }

    public static boolean matchesMethod(String namedName, Method candidate) {
        String owner = candidate.getDeclaringClass().getName().replace('.', '/');
        String descriptor = Type.getMethodDescriptor(candidate);
        return namedName.equals(MAP.runtimeMethods.get(owner + "/" + candidate.getName()
                + " " + descriptor));
    }

    public static byte[] toNamed(byte[] bytes) {
        return remap(bytes, MAP.toNamed);
    }

    public static byte[] toCheatBreaker(byte[] bytes) {
        return remap(bytes, MAP.toCheatBreaker);
    }

    private static byte[] remap(byte[] bytes, Remapper remapper) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassRemapper(writer, remapper), 0);
        return writer.toByteArray();
    }

    private static Mapping load() {
        Mapping mapping = new Mapping();
        List<String> lines = new ArrayList<String>();
        InputStream input = CheatBreakerMappings.class.getResourceAsStream("cheatbreaker189.tiny");
        if (input == null) throw new IllegalStateException("CheatBreaker 1.8.9 mappings missing");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read CheatBreaker mappings", error);
        }
        if (lines.isEmpty() || !"tiny\t2\t0\tnamed\tcheatbreaker".equals(lines.get(0))) {
            throw new IllegalStateException("Unexpected CheatBreaker mapping format");
        }
        String owner = null;
        for (String line : lines) {
            String[] parts = line.split("\t");
            if (parts.length == 3 && "c".equals(parts[0])) {
                owner = parts[1];
                mapping.toRuntime.put(owner, parts[2]);
                mapping.toNamedClasses.put(parts[2], owner);
            }
        }
        for (String line : lines) {
            String[] parts = line.split("\t");
            if (parts.length == 3 && "c".equals(parts[0])) {
                owner = parts[1];
            } else if (parts.length == 5 && owner != null && "f".equals(parts[1])) {
                mapping.fields.put(owner + "/" + parts[3], parts[4]);
                mapping.runtimeFields.put(mapping.toRuntime.get(owner) + "/" + parts[4], parts[3]);
            } else if (parts.length == 5 && owner != null && "m".equals(parts[1])) {
                mapping.methods.put(owner + "/" + parts[3] + " " + parts[2], parts[4]);
                String runtimeDescriptor = mapping.toCheatBreaker.mapMethodDesc(parts[2]);
                mapping.runtimeMethods.put(mapping.toRuntime.get(owner) + "/" + parts[4]
                        + " " + runtimeDescriptor, parts[3]);
            }
        }
        if (!MINECRAFT_CLASS.equals(mapping.toRuntime.get("net/minecraft/client/Minecraft"))
                || !GET_MINECRAFT.equals(mapping.methods.get(
                        "net/minecraft/client/Minecraft/getMinecraft "
                        + "()Lnet/minecraft/client/Minecraft;"))) {
            throw new IllegalStateException("CheatBreaker mapping sentinel mismatch");
        }
        return mapping;
    }

    private static final class Mapping {
        final Map<String, String> toRuntime = new HashMap<String, String>();
        final Map<String, String> toNamedClasses = new HashMap<String, String>();
        final Map<String, String> methods = new HashMap<String, String>();
        final Map<String, String> fields = new HashMap<String, String>();
        final Map<String, String> runtimeMethods = new HashMap<String, String>();
        final Map<String, String> runtimeFields = new HashMap<String, String>();
        final Map<String, String[]> parents = new HashMap<String, String[]>();

        String member(Map<String, String> members, String owner, String suffix) {
            String direct = members.get(owner + "/" + suffix);
            if (direct != null) return direct;
            ArrayDeque<String> queue = new ArrayDeque<String>();
            Set<String> seen = new HashSet<String>();
            queue.add(owner);
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                if (!seen.add(current)) continue;
                if (!owner.equals(current)) {
                    String inherited = members.get(current + "/" + suffix);
                    if (inherited != null) return inherited;
                }
                for (String parent : superTypes(current)) queue.addLast(parent);
            }
            int space = suffix.indexOf(' ');
            return space < 0 ? suffix : suffix.substring(0, space);
        }

        String[] superTypes(String named) {
            String[] cached = parents.get(named);
            if (cached != null) return cached;
            String runtime = toRuntime.get(named);
            if (runtime == null) return new String[0];
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) loader = CheatBreakerMappings.class.getClassLoader();
            InputStream input = loader.getResourceAsStream(runtime + ".class");
            if (input == null) return new String[0];
            try {
                ClassReader reader = new ClassReader(input);
                List<String> list = new ArrayList<String>();
                if (reader.getSuperName() != null) list.add(toNamedClasses.getOrDefault(
                        reader.getSuperName(), reader.getSuperName()));
                for (String iface : reader.getInterfaces()) {
                    list.add(toNamedClasses.getOrDefault(iface, iface));
                }
                cached = list.toArray(new String[list.size()]);
                parents.put(named, cached);
                return cached;
            } catch (IOException ignored) {
                return new String[0];
            } finally {
                try { input.close(); } catch (IOException ignored) { }
            }
        }

        final Remapper toCheatBreaker = new Remapper() {
            @Override public String map(String name) {
                return toRuntime.getOrDefault(name, name);
            }
            @Override public String mapFieldName(String owner, String name, String descriptor) {
                return member(fields, owner, name);
            }
            @Override public String mapMethodName(String owner, String name, String descriptor) {
                return name.startsWith("<") ? name : member(methods, owner, name + " " + descriptor);
            }
        };
        final Remapper toNamed = new Remapper() {
            @Override public String map(String name) {
                return toNamedClasses.getOrDefault(name, name);
            }
            @Override public String mapFieldName(String owner, String name, String descriptor) {
                return runtimeFields.getOrDefault(owner + "/" + name, name);
            }
            @Override public String mapMethodName(String owner, String name, String descriptor) {
                return runtimeMethods.getOrDefault(owner + "/" + name + " " + descriptor, name);
            }
        };
    }
}
