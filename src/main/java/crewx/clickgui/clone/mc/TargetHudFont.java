package crewx.clickgui.clone.mc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.Font;
import java.io.InputStream;

/** Tahoma from Adjust, rasterized at framebuffer resolution for sharp HUD text. */
public final class TargetHudFont {
    private static Font family;
    private static TtfFont name;
    private static TtfFont number;
    private static TtfFont sign;
    private static int rasterScale;

    private TargetHudFont() { }

    private static Font family() {
        if (family != null) return family;
        try (InputStream stream = TargetHudFont.class.getResourceAsStream("/assets/crewx/font/Tahoma.ttf")) {
            if (stream != null) return family = Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (Exception ignored) {
        }
        return family = new Font("Tahoma", Font.PLAIN, 12);
    }

    private static TtfFont font(int size) {
        int scale = Math.max(1, new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor());
        if (rasterScale != scale) {
            rasterScale = scale;
            name = number = sign = null;
        }
        if (size == 12) {
            if (name == null) name = new TtfFont(family().deriveFont(6.0F * scale));
            return name;
        }
        if (size == 10) {
            if (number == null) number = new TtfFont(family().deriveFont(5.0F * scale));
            return number;
        }
        if (sign == null) sign = new TtfFont(family().deriveFont(4.0F * scale));
        return sign;
    }

    public static float width(int size, String text) {
        return font(size).width(text) / rasterScale;
    }

    public static void draw(int size, String text, float x, float y, int color) {
        TtfFont renderer = font(size);
        float scale = rasterScale;
        float baseline = y + size * 0.5F - 2.5F;
        int shadow = (color & 0xFF000000) | ((color & 0xFCFCFC) >> 2);
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPushMatrix();
        GL11.glScalef(1.0F / scale, 1.0F / scale, 1.0F);
        renderer.draw(text, (x + 0.5F) * scale, (baseline + 0.5F) * scale, shadow);
        renderer.draw(text, x * scale, baseline * scale, color);
        GL11.glPopMatrix();
        // TtfFont uses raw OpenGL calls; restore the texture state expected by Minecraft's item renderer.
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
    }
}
