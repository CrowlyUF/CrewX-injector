package crewx.gui;

import crewx.accountmanager.gui.GuiAccountManager;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;


public final class CrewXMainMenu extends GuiScreen {
    public static final class MenuButton extends GuiButton {
        private final int crewxWidth;
        private final int crewxHeight;

        public MenuButton(int id, int x, int y, int width, int height, String label) {
            super(id, x, y, width, height, label);
            this.crewxWidth = width;
            this.crewxHeight = height;
        }

        public int menuWidth() { return crewxWidth; }
        public int menuHeight() { return crewxHeight; }
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int wide = Math.min(300, Math.max(220, width / 5));
        int narrow = (wide - 12) / 2;
        int left = width / 2 - wide / 2;
        int top = height / 2 - 48;
        buttonList.add(new MenuButton(1, left, top, wide, 28, "Singleplayer"));
        buttonList.add(new MenuButton(2, left, top + 36, wide, 28, "Multiplayer"));
        buttonList.add(new MenuButton(0, left, top + 72, narrow, 28, "Options"));
        buttonList.add(new MenuButton(1337, left + narrow + 12, top + 72, narrow, 28, "Alts"));
        buttonList.add(new MenuButton(1338, width - 86, height - 31, 74, 20, "Quit"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        CrewXTheme.drawBackground(width, height);
        CrewXTheme.drawMainTitle(width, height);


        for (GuiButton button : buttonList) {
            CrewXTheme.drawButton(button, mouseX, mouseY);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null || !button.enabled) return;
        switch (button.id) {
            case 1:
                mc.displayGuiScreen(new GuiSelectWorld(this));
                break;
            case 2:
                mc.displayGuiScreen(new GuiMultiplayer(this));
                break;
            case 0:
                mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
                break;
            case 1337:
                mc.displayGuiScreen(new GuiAccountManager(this));
                break;
            case 1338:
                mc.shutdown();
                break;
            default:
                break;
        }
    }
}
