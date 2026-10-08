import crewx.inject.NotchMappings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import static org.objectweb.asm.Opcodes.ASM9;


public final class BadlionAccessorAudit {
    public static void main(String[] args) throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(args[1])), StandardCharsets.UTF_8);
        Pattern calls = Pattern.compile("(readField|writeField|invoke)\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"");
        Matcher match = calls.matcher(source);
        int checked = 0;
        int missing = 0;
        try (ZipFile game = new ZipFile(args[0])) {
            Set<String> seen = new HashSet<String>();
            while (match.find()) {
                String kind = match.group(1), owner = match.group(2), member = match.group(3);
                if (!seen.add(kind + owner + member)) continue;
                String className = NotchMappings.className(owner).replace('.', '/');
                ZipEntry entry = game.getEntry(className + ".class");
                if (entry == null) {
                    System.out.println("MISSING CLASS " + owner + " -> " + className);
                    missing++;
                    continue;
                }
                byte[] data = game.getInputStream(entry).readAllBytes();
                boolean method = "invoke".equals(kind);
                String expected = method ? member : NotchMappings.fieldName(owner, member);
                boolean[] found = {false};
                new ClassReader(method ? NotchMappings.toNamed(data) : data)
                        .accept(new ClassVisitor(ASM9) {
                            @Override public FieldVisitor visitField(int access, String name,
                                    String desc, String signature, Object value) {
                                if (!method && expected.equals(name)) found[0] = true;
                                return null;
                            }
                            @Override public MethodVisitor visitMethod(int access, String name,
                                    String desc, String signature, String[] exceptions) {
                                if (method && expected.equals(name)) found[0] = true;
                                return null;
                            }
                        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                checked++;
                if (!found[0]) {
                    System.out.println("MISSING " + kind + " " + owner + "#" + member
                            + " -> " + className + "#" + expected);
                    missing++;
                }
            }
            String[][] bridgeFields = {
                {"net.minecraft.client.entity.EntityPlayerSP", "lastReportedYaw"},
                {"net.minecraft.client.entity.EntityPlayerSP", "lastReportedPitch"},
                {"net.minecraft.client.gui.GuiNewChat", "drawnChatLines"},
                {"net.minecraft.client.gui.GuiNewChat", "scrollPos"},
                {"net.minecraft.client.renderer.ItemRenderer", "itemToRender"},
                {"net.minecraft.client.renderer.ItemRenderer", "equippedProgress"},
                {"net.minecraft.client.renderer.ItemRenderer", "prevEquippedProgress"},
                {"net.minecraft.client.gui.GuiScreen", "buttonList"},
                {"net.minecraft.client.Minecraft", "leftClickCounter"}
            };
            for (String[] pair : bridgeFields) {
                String className = NotchMappings.className(pair[0]).replace('.', '/');
                ZipEntry entry = game.getEntry(className + ".class");
                if (entry == null) { missing++; continue; }
                String expected = NotchMappings.fieldName(pair[0], pair[1]);
                boolean[] found = {false};
                new ClassReader(game.getInputStream(entry).readAllBytes())
                        .accept(new ClassVisitor(ASM9) {
                            @Override public FieldVisitor visitField(int access, String name,
                                    String desc, String signature, Object value) {
                                if (expected.equals(name)) found[0] = true;
                                return null;
                            }
                        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                checked++;
                if (!found[0]) {
                    System.out.println("MISSING BRIDGE FIELD " + pair[0] + "#" + pair[1]
                            + " -> " + className + "#" + expected);
                    missing++;
                }
            }
        }
        System.out.println("Accessors checked=" + checked + " missing=" + missing);
        if (missing > 0) System.exit(1);
    }
}
