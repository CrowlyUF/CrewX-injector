package crewx.util;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;












public final class GlStateSync {
    private static final FloatBuffer FLOATS = BufferUtils.createFloatBuffer(16);
    private static final ByteBuffer BYTES = BufferUtils.createByteBuffer(16);

    private GlStateSync() {
    }


    public static void afterPopAttrib() {
        int activeUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        for (int unit = 1; unit >= 0; unit--) {
            selectUnit(GL13.GL_TEXTURE0 + unit);
            if (GL11.glIsEnabled(GL11.GL_TEXTURE_2D)) GlStateManager.enableTexture2D();
            else GlStateManager.disableTexture2D();
            GlStateManager.bindTexture(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        }
        selectUnit(activeUnit);

        if (GL11.glIsEnabled(GL11.GL_BLEND)) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        if (GL11.glIsEnabled(GL11.GL_ALPHA_TEST)) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
        if (GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        if (GL11.glIsEnabled(GL11.GL_CULL_FACE)) GlStateManager.enableCull(); else GlStateManager.disableCull();
        if (GL11.glIsEnabled(GL11.GL_LIGHTING)) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
        for (int light = 0; light < 2; light++) {
            if (GL11.glIsEnabled(GL11.GL_LIGHT0 + light)) GlStateManager.enableLight(light);
            else GlStateManager.disableLight(light);
        }
        if (GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL)) GlStateManager.enableColorMaterial(); else GlStateManager.disableColorMaterial();
        if (GL11.glIsEnabled(GL11.GL_FOG)) GlStateManager.enableFog(); else GlStateManager.disableFog();
        if (GL11.glIsEnabled(GL11.GL_NORMALIZE)) GlStateManager.enableNormalize(); else GlStateManager.disableNormalize();
        if (GL11.glIsEnabled(0x803A  )) GlStateManager.enableRescaleNormal();
        else GlStateManager.disableRescaleNormal();
        if (GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL)) GlStateManager.enablePolygonOffset();
        else GlStateManager.disablePolygonOffset();
        if (GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP)) GlStateManager.enableColorLogic(); else GlStateManager.disableColorLogic();

        GlStateManager.depthMask(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        GlStateManager.depthFunc(GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        GlStateManager.alphaFunc(GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC), GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF));
        GlStateManager.tryBlendFuncSeparate(GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
        GlStateManager.shadeModel(GL11.glGetInteger(GL11.GL_SHADE_MODEL));
        GlStateManager.cullFace(GL11.glGetInteger(GL11.GL_CULL_FACE_MODE));

        FLOATS.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, FLOATS);
        GlStateManager.color(FLOATS.get(0), FLOATS.get(1), FLOATS.get(2), FLOATS.get(3));
        BYTES.clear();
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, BYTES);
        GlStateManager.colorMask(BYTES.get(0) != 0, BYTES.get(1) != 0, BYTES.get(2) != 0, BYTES.get(3) != 0);
    }


    private static void selectUnit(int unit) {
        GL13.glActiveTexture(unit);
        GlStateManager.setActiveTexture(unit);
    }
}
