package crewx.inject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Method;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;


public final class NotchMappings {
    private static final Mapping MAPPING = load();
    private static Boolean notch;
    private static Boolean srg;

    private NotchMappings() {
    }

    public static synchronized boolean isNotch() {
        if (notch == null) {
            try {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();



                Class<?> minecraft = loader.loadClass("net.minecraft.client.Minecraft");
                try {
                    minecraft.getMethod("getMinecraft");
                    notch = Boolean.FALSE;
                } catch (NoSuchMethodException missingNamedMember) {
                    try {


                        minecraft.getMethod("func_71410_x");
                        notch = Boolean.FALSE;
                        srg = Boolean.TRUE;
                    } catch (NoSuchMethodException missingSrgMember) {
                        notch = loader.getResource("ave.class") != null;
                    }
                }
            } catch (Throwable ignored) {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                notch = loader != null && loader.getResource("ave.class") != null;
            }
        }
        return notch.booleanValue();
    }

    public static String className(String named) {
        if (!isNotch()) return named;
        String internal = named.replace('.', '/');
        return MAPPING.namedToObf.getOrDefault(internal, internal).replace('/', '.');
    }

    public static String methodName(String owner, String name, String descriptor) {
        if (!isNotch()) {
            if (!isSrg()) return name;
            return MAPPING.namedSrgMethods.getOrDefault(
                    owner.replace('.', '/') + "/" + name + " " + descriptor, name);
        }
        return MAPPING.namedMethods.getOrDefault(
                owner.replace('.', '/') + "/" + name + " " + descriptor, name);
    }

    public static String fieldName(String owner, String name) {
        if (!isNotch()) {
            if (!isSrg()) return name;
            return MAPPING.namedSrgFields.getOrDefault(owner.replace('.', '/') + "/" + name, name);
        }

        if ("net.minecraft.client.Minecraft".equals(owner) && "logger".equals(name)) {
            return "K";
        }

        if ("net.minecraft.item.ItemSword".equals(owner) && "toolMaterial".equals(name)) {
            return "b";
        }
        return MAPPING.namedFields.getOrDefault(
                owner.replace('.', '/') + "/" + name, name);
    }

    public static boolean matchesMethod(String namedName, Method candidate) {
        if (namedName.equals(candidate.getName())) return true;
        if (!isNotch()) {
            if (!isSrg()) return false;
            String key = candidate.getDeclaringClass().getName().replace('.', '/')
                    + "/" + candidate.getName() + " " + Type.getMethodDescriptor(candidate);
            return namedName.equals(MAPPING.srgNamedMethods.get(key));
        }
        String key = candidate.getDeclaringClass().getName().replace('.', '/')
                + "/" + candidate.getName() + " " + Type.getMethodDescriptor(candidate);
        return namedName.equals(MAPPING.obfMethods.get(key));
    }

    public static byte[] toNamed(byte[] notchBytes) {
        return remap(notchBytes, MAPPING.toNamed);
    }

    public static boolean isSrg() {
        if (isNotch()) return false;
        if (srg == null) {
            try {
                Class<?> minecraft = Thread.currentThread().getContextClassLoader()
                        .loadClass("net.minecraft.client.Minecraft");
                minecraft.getMethod("func_71410_x");
                srg = Boolean.TRUE;
            } catch (Throwable ignored) {
                srg = Boolean.FALSE;
            }
        }
        return srg.booleanValue();
    }

    public static byte[] srgToNamed(byte[] bytes) {
        return remap(bytes, MAPPING.srgToNamed);
    }

    public static byte[] namedToSrg(byte[] bytes) {
        return remap(bytes, MAPPING.namedToSrg);
    }

    public static byte[] toNotch(byte[] namedBytes) {
        return remap(namedBytes, MAPPING.toNotch);
    }

    public static byte[] namedResourceBytes(String namedInternal, ClassLoader loader)
            throws IOException {
        String obfuscated = MAPPING.namedToObf.get(namedInternal);
        if (obfuscated == null) return null;
        InputStream input = loader.getResourceAsStream(obfuscated + ".class");
        if (input == null) return null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) >= 0) out.write(chunk, 0, count);
            return toNamed(out.toByteArray());
        } finally {
            input.close();
        }
    }

    private static byte[] remap(byte[] bytes, Remapper remapper) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassRemapper(writer, remapper), 0);
        return writer.toByteArray();
    }

    private static Mapping load() {
        Mapping mapping = new Mapping();
        try {
            readCsv("notch-fields.csv", mapping.fieldCsv);
            readCsv("notch-methods.csv", mapping.methodCsv);
            try (BufferedReader lines = resource("notch-joined.srg")) {
                String line;
                while ((line = lines.readLine()) != null) {
                    String[] parts = line.split(" +");
                    if (parts.length == 3 && "CL:".equals(parts[0])) {
                        mapping.obfToNamed.put(parts[1], parts[2]);
                        mapping.namedToObf.put(parts[2], parts[1]);
                    } else if (parts.length == 3 && "FD:".equals(parts[0])) {
                        String obfOwner = owner(parts[1]);
                        String obfName = simple(parts[1]);
                        String namedOwner = owner(parts[2]);
                        String namedName = mapping.fieldCsv.getOrDefault(simple(parts[2]), simple(parts[2]));
                        mapping.obfFields.put(obfOwner + "/" + obfName, namedName);
                        mapping.namedFields.put(namedOwner + "/" + namedName, obfName);
                        mapping.srgNamedFields.put(parts[2], namedName);
                        mapping.namedSrgFields.put(namedOwner + "/" + namedName, simple(parts[2]));
                    } else if (parts.length == 5 && "MD:".equals(parts[0])) {
                        String obfOwner = owner(parts[1]);
                        String obfName = simple(parts[1]);
                        String namedOwner = owner(parts[3]);
                        String namedName = mapping.methodCsv.getOrDefault(simple(parts[3]), simple(parts[3]));
                        mapping.obfMethods.put(obfOwner + "/" + obfName + " " + parts[2], namedName);
                        mapping.namedMethods.put(namedOwner + "/" + namedName + " " + parts[4], obfName);
                        mapping.srgNamedMethods.put(parts[3] + " " + parts[4], namedName);
                        mapping.namedSrgMethods.put(namedOwner + "/" + namedName + " " + parts[4], simple(parts[3]));
                    }
                }
            }


            mapping.obfMethods.put("pr/a (FFF)V", "moveFlying");
            mapping.namedMethods.put("net/minecraft/entity/EntityLivingBase/moveFlying (FFF)V", "a");
            mapping.srgNamedMethods.put("net/minecraft/entity/EntityLivingBase/func_70060_a (FFF)V", "moveFlying");
            mapping.namedSrgMethods.put("net/minecraft/entity/EntityLivingBase/moveFlying (FFF)V", "func_70060_a");


            mapping.srgNamedMethods.put("net/minecraft/client/entity/EntityPlayerSP/func_70115_ae ()Z", "isRiding");
            mapping.namedSrgMethods.put("net/minecraft/client/entity/EntityPlayerSP/isRiding ()Z", "func_70115_ae");
        } catch (IOException error) {
            throw new IllegalStateException("1.8.9 Notch mappings unavailable", error);
        }
        return mapping;
    }

    private static BufferedReader resource(String name) throws IOException {
        InputStream input = NotchMappings.class.getResourceAsStream(name);
        if (input == null) throw new IOException(name);
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    private static void readCsv(String name, Map<String, String> output) throws IOException {
        try (BufferedReader lines = resource(name)) {
            lines.readLine();
            String line;
            while ((line = lines.readLine()) != null) {
                int first = line.indexOf(',');
                int second = first < 0 ? -1 : line.indexOf(',', first + 1);
                if (first > 0 && second > first) {
                    output.put(line.substring(0, first).replace("\"", ""),
                            line.substring(first + 1, second).replace("\"", ""));
                }
            }
        }
    }

    private static String owner(String member) {
        return member.substring(0, member.lastIndexOf('/'));
    }

    private static String simple(String member) {
        return member.substring(member.lastIndexOf('/') + 1);
    }

    private static final class Mapping {
        final Map<String, String> obfToNamed = new HashMap<String, String>();
        final Map<String, String> namedToObf = new HashMap<String, String>();
        final Map<String, String> obfFields = new HashMap<String, String>();
        final Map<String, String> namedFields = new HashMap<String, String>();
        final Map<String, String> obfMethods = new HashMap<String, String>();
        final Map<String, String> namedMethods = new HashMap<String, String>();
        final Map<String, String> fieldCsv = new HashMap<String, String>();
        final Map<String, String> methodCsv = new HashMap<String, String>();
        final Map<String, String> srgNamedFields = new HashMap<String, String>();
        final Map<String, String> namedSrgFields = new HashMap<String, String>();
        final Map<String, String> srgNamedMethods = new HashMap<String, String>();
        final Map<String, String> namedSrgMethods = new HashMap<String, String>();

        final Remapper srgToNamed = new Remapper() {
            @Override public String mapFieldName(String owner, String name, String desc) {
                return srgNamedFields.getOrDefault(owner + "/" + name, name);
            }
            @Override public String mapMethodName(String owner, String name, String desc) {
                return srgNamedMethods.getOrDefault(owner + "/" + name + " " + desc, name);
            }
        };
        final Remapper namedToSrg = new Remapper() {
            @Override public String mapFieldName(String owner, String name, String desc) {
                return namedSrgFields.getOrDefault(owner + "/" + name, name);
            }
            @Override public String mapMethodName(String owner, String name, String desc) {
                return namedSrgMethods.getOrDefault(owner + "/" + name + " " + desc, name);
            }
        };

        final Remapper toNamed = new Remapper() {
            @Override public String map(String name) { return obfToNamed.getOrDefault(name, name); }
            @Override public String mapFieldName(String owner, String name, String desc) {
                return obfFields.getOrDefault(owner + "/" + name, name);
            }
            @Override public String mapMethodName(String owner, String name, String desc) {
                return obfMethods.getOrDefault(owner + "/" + name + " " + desc, name);
            }
        };
        final Remapper toNotch = new Remapper() {
            @Override public String map(String name) { return namedToObf.getOrDefault(name, name); }
            @Override public String mapFieldName(String owner, String name, String desc) {
                return namedFields.getOrDefault(owner + "/" + name, name);
            }
            @Override public String mapMethodName(String owner, String name, String desc) {
                return namedMethods.getOrDefault(owner + "/" + name + " " + desc, name);
            }
        };
    }
}
