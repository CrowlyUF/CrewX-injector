package crewx.clickgui.clone.mc;

import crewx.CrewX;
import crewx.clickgui.clone.CrewXClickGuiBridge;
import crewx.clickgui.clone.core.ClickGui;
import crewx.inject.CrewXRuntimeHooks;
import crewx.module.Module;
import crewx.module.modules.render.GuiModule;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.function.IntFunction;

/**
 * Tela do Minecraft 1.8.9 que hospeda a {@link ClickGui}.
 *
 * Uso:  mc.displayGuiScreen(new McClickGuiScreen(gui));
 * Mantenha o mesmo {@code ClickGui} entre aberturas para preservar posição dos painéis e estados.
 */
public class McClickGuiScreen extends GuiScreen {
    private static McGfx gfx; // reaproveitado: os atlas de fonte ficam na GPU
    private static volatile long drawCount;
    private static boolean graphicsStateLogged;

    public static long getDrawCount() {
        return drawCount;
    }
    private final ClickGui gui;
    private final int closeKey;
    private long last = System.nanoTime();

    public McClickGuiScreen(ClickGui gui) {
        this(gui, Keyboard.KEY_RSHIFT);
    }

    /** @param closeKey tecla de reserva quando o módulo ClickGui não estiver disponível (-1 = só ESC). */
    public McClickGuiScreen(ClickGui gui, int closeKey) {
        this.gui = gui;
        this.closeKey = closeKey;
        gui.setKeyNamer(new IntFunction<String>() {
            @Override
            public String apply(int key) {
                return Keyboard.getKeyName(key);
            }
        });
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // sem fundo escurecido: o mundo continua visível atrás (e atrás dos painéis)
        int dw = mc.displayWidth, dh = mc.displayHeight;
        if (gfx == null) gfx = new McGfx();

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;

        float mx = Mouse.getX(), my = dh - Mouse.getY() - 1;
        CrewXClickGuiBridge.setSkipNumberPull(gui.isDraggingSlider());
        CrewXClickGuiBridge.syncFromModules();
        gui.setScreenSize(dw, dh);
        gui.setModifiers(isCtrlKeyDown(), isShiftKeyDown());

        gfx.begin(dw, dh);
        try {
            if (!graphicsStateLogged) {
                while (org.lwjgl.opengl.GL11.glGetError() != org.lwjgl.opengl.GL11.GL_NO_ERROR) { }
                crewx.inject.CrewXBootstrap.log("ClickGui GL before draw: framebuffer="
                        + org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING)
                        + " status=" + org.lwjgl.opengl.GL30.glCheckFramebufferStatus(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER)
                        + " drawBuffer=" + org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_DRAW_BUFFER)
                        + " program=" + org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM));
            }
            gui.render(gfx, mx, my, dt);
            if (!graphicsStateLogged) {
                graphicsStateLogged = true;
                crewx.inject.CrewXBootstrap.log("ClickGui GL after draw: error="
                        + org.lwjgl.opengl.GL11.glGetError() + " drawBuffer="
                        + org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_DRAW_BUFFER));
            }
        } finally {
            gfx.end();
        }
        drawCount++;
    }

    // O núcleo trabalha em pixels reais, então usamos as coordenadas "cruas" do LWJGL.
    private float rawX() {
        return Mouse.getEventX();
    }

    private float rawY() {
        return mc.displayHeight - Mouse.getEventY() - 1;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        gui.mouseClicked(rawX(), rawY(), mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state != -1) gui.mouseReleased(rawX(), rawY(), state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) gui.mouseWheel(wheel > 0 ? 1 : -1);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (gui.isListening()) {
            gui.keyPressed(keyCode, keyCode == Keyboard.KEY_ESCAPE);
            return;
        }
        if (gui.isEditingText()) {
            // ESC / Enter handled inside keyTyped
            gui.keyTyped(typedChar, keyCode, false);
            return;
        }
        if (keyCode != Keyboard.KEY_NONE && keyCode == configuredCloseKey()) {
            CrewXRuntimeHooks.consumeBindPress(keyCode);
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode); // ESC fecha
    }

    private int configuredCloseKey() {
        if (CrewX.moduleManager != null) {
            Module module = CrewX.moduleManager.modules.get(GuiModule.class);
            if (module != null && module.getKey() > Keyboard.KEY_NONE) {
                return module.getKey();
            }
        }
        return closeKey;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
