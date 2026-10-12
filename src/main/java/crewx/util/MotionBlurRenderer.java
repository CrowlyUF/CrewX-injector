package crewx.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import crewx.mixin.IAccessorMinecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;





public final class MotionBlurRenderer {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final int GL_FRAMEBUFFER_BINDING = 0x8CA6;
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(4);
    private static final ByteBuffer DEPTH_MASK = BufferUtils.createByteBuffer(1);
    private static final FloatBuffer CURRENT_COLOR = BufferUtils.createFloatBuffer(4);
    private static final FloatBuffer CURRENT_TEXCOORD = BufferUtils.createFloatBuffer(4);

    private static int historyTexture;
    private static int textureWidth;
    private static int textureHeight;
    private static boolean historyValid;
    private static Object lastWorld;
    private static boolean failureLogged;

    private MotionBlurRenderer() {
    }

    public static void reset() {
        historyValid = false;
        lastWorld = MC.theWorld;
    }

    public static void render(float strength) {
        if (strength <= 0.001F || MC.theWorld == null || MC.thePlayer == null
                || !OpenGlHelper.isFramebufferEnabled()) return;

        if (lastWorld != MC.theWorld) {
            historyValid = false;
            lastWorld = MC.theWorld;
        }

        Framebuffer framebuffer = MC.getFramebuffer();
        if (framebuffer == null) return;

        int copyWidth = framebuffer.framebufferWidth;
        int copyHeight = framebuffer.framebufferHeight;
        int backingWidth = Math.max(copyWidth, framebuffer.framebufferTextureWidth);
        int backingHeight = Math.max(copyHeight, framebuffer.framebufferTextureHeight);
        org.lwjgl.opengl.ContextCapabilities capabilities = GLContext.getCapabilities();
        if (!capabilities.OpenGL20 && !capabilities.GL_ARB_texture_non_power_of_two) {
            backingWidth = nextPowerOfTwo(backingWidth);
            backingHeight = nextPowerOfTwo(backingHeight);
        }
        if (copyWidth <= 0 || copyHeight <= 0 || backingWidth <= 0 || backingHeight <= 0) return;

        if (historyTexture == 0 || backingWidth != textureWidth || backingHeight != textureHeight) {
            allocateHistory(backingWidth, backingHeight);
            if (historyTexture == 0) return;
        }

        float alpha = Math.max(0.0F, Math.min(0.65F, strength));
        int previousFramebuffer = GL11.glGetInteger(GL_FRAMEBUFFER_BINDING);
        VIEWPORT.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
        int viewportX = VIEWPORT.get(0);
        int viewportY = VIEWPORT.get(1);
        int viewportWidth = VIEWPORT.get(2);
        int viewportHeight = VIEWPORT.get(3);

        try {
            framebuffer.bindFramebuffer(false);
            if (historyValid) {
                blendHistory(alpha, (float) copyWidth / backingWidth, (float) copyHeight / backingHeight);
            }
            copyCurrentFrame(copyWidth, copyHeight);
            historyValid = true;
        } catch (Throwable error) {
            historyValid = false;
            reportFailure(error);
        } finally {
            try {
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previousFramebuffer);
                GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            } catch (Throwable error) {
                reportFailure(error);
            }
        }
    }

    public static void release() {
        if (historyTexture != 0) {
            try {
                GL11.glDeleteTextures(historyTexture);
            } catch (Throwable ignored) {
            }
        }
        historyTexture = 0;
        textureWidth = 0;
        textureHeight = 0;
        historyValid = false;
        lastWorld = null;
    }

    private static void allocateHistory(int width, int height) {
        release();
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            historyTexture = GL11.glGenTextures();
            if (historyTexture == 0) return;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            textureWidth = width;
            textureHeight = height;
        } catch (Throwable error) {
            release();
            reportFailure(error);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
        }
    }

    private static void blendHistory(float alpha, float maxU, float maxV) {
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH)
                >= GL11.glGetInteger(GL11.GL_MAX_MODELVIEW_STACK_DEPTH)
                || GL11.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH)
                >= GL11.glGetInteger(GL11.GL_MAX_PROJECTION_STACK_DEPTH)) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
            return;
        }

        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean textureEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        DEPTH_MASK.clear();
        GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK, DEPTH_MASK);
        boolean depthMask = DEPTH_MASK.get(0) != 0;
        int blendSourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int blendDestinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int blendSourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int blendDestinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        CURRENT_COLOR.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, CURRENT_COLOR);
        float colorRed = CURRENT_COLOR.get(0);
        float colorGreen = CURRENT_COLOR.get(1);
        float colorBlue = CURRENT_COLOR.get(2);
        float colorAlpha = CURRENT_COLOR.get(3);
        CURRENT_TEXCOORD.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_TEXTURE_COORDS, CURRENT_TEXCOORD);
        float texCoordS = CURRENT_TEXCOORD.get(0);
        float texCoordT = CURRENT_TEXCOORD.get(1);
        float texCoordR = CURRENT_TEXCOORD.get(2);
        float texCoordQ = CURRENT_TEXCOORD.get(3);

        boolean projectionPushed = false;
        boolean modelViewPushed = false;

        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            projectionPushed = true;
            GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            modelViewPushed = true;
            GL11.glLoadIdentity();

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTexture);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F, 0.0F); GL11.glVertex2f(-1.0F, -1.0F);
            GL11.glTexCoord2f(maxU, 0.0F); GL11.glVertex2f(1.0F, -1.0F);
            GL11.glTexCoord2f(maxU, maxV); GL11.glVertex2f(1.0F, 1.0F);
            GL11.glTexCoord2f(0.0F, maxV); GL11.glVertex2f(-1.0F, 1.0F);
            GL11.glEnd();
        } finally {
            if (modelViewPushed) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            if (projectionPushed) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
            GL11.glMatrixMode(previousMatrixMode);
            GL11.glDepthMask(depthMask);
            GL14.glBlendFuncSeparate(blendSourceRgb, blendDestinationRgb,
                    blendSourceAlpha, blendDestinationAlpha);
            GL11.glColor4f(colorRed, colorGreen, colorBlue, colorAlpha);
            GL11.glTexCoord4f(texCoordS, texCoordT, texCoordR, texCoordQ);
            setEnabled(GL11.GL_DEPTH_TEST, depthEnabled);
            setEnabled(GL11.GL_CULL_FACE, cullEnabled);
            setEnabled(GL11.GL_TEXTURE_2D, textureEnabled);
            setEnabled(GL11.GL_BLEND, blendEnabled);
        }
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) GL11.glEnable(capability);
        else GL11.glDisable(capability);
    }

    private static int nextPowerOfTwo(int value) {
        if (value <= 1) return 1;
        int highest = Integer.highestOneBit(value - 1);
        return highest > (1 << 29) ? value : highest << 1;
    }

    private static void copyCurrentFrame(int width, int height) {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTexture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
        }
    }

    private static void reportFailure(Throwable error) {
        if (failureLogged) return;
        failureLogged = true;
        try {
            ((IAccessorMinecraft) MC).getLogger().warn("Motion Blur renderer failed", error);
        } catch (Throwable ignored) {
            error.printStackTrace();
        }
    }
}
