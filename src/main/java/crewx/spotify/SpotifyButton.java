package crewx.spotify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

final class SpotifyButton extends GuiButton {
    SpotifyButton(int id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height, text);
    }

    @Override public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
        int background = !enabled ? 0xFF181818 : hovered ? 0xFF2A2A2A : 0xFF202020;
        int edge = hovered && enabled ? 0xFF1ED760 : 0xFF303030;
        drawRect(xPosition, yPosition, xPosition + width, yPosition + height, background);
        drawRect(xPosition, yPosition, xPosition + 2, yPosition + height, edge);
        int color = enabled ? (hovered ? 0xFFFFFFFF : 0xFFE6E6E6) : 0xFF777777;
        drawCenteredString(mc.fontRendererObj, displayString, xPosition + width / 2, yPosition + (height - 8) / 2, color);
    }
}
