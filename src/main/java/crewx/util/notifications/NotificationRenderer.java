package crewx.util.notifications;

import crewx.clickgui.render.RoundedUtils;
import crewx.gui.BackdropBlur;
import crewx.gui.ClientFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import java.util.List;

public class NotificationRenderer implements INotificationRenderer {
    private static final int MARGIN = 8;
    private static final int BOX_HEIGHT = 32;
    private static final int SPACING = 36;
    private static final int HORIZONTAL_PADDING = 33;
    private static final int SUCCESS = 0xFF54C98A;
    private static final int FAILURE = 0xFFE6656B;
    private static final int NOTICE = 0xFFE7B853;
    private static final int INFO = 0xFF5E98F5;

    @Override
    public void draw(List<INotification> notifications) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();
        int maxTextWidth = Math.max(16, screenWidth - MARGIN * 2 - HORIZONTAL_PADDING);
        float y = Math.max(MARGIN, screenHeight - MARGIN - notifications.size() * SPACING);

        for (INotification value : notifications) {
            Notification notification = (Notification) value;
            String header = trim(notification.getHeader(), maxTextWidth);
            String subtext = trim(notification.getSubtext(), maxTextWidth);
            int textWidth = Math.max(ClientFont.getStringWidth(header), ClientFont.getStringWidth(subtext));
            float boxWidth = Math.min(screenWidth - MARGIN * 2,
                    Math.max(112.0F, textWidth + HORIZONTAL_PADDING));
            boolean leaving = notification.checkTime() >= notification.getDisplayTime() + notification.getStart();
            float targetX = leaving ? screenWidth + MARGIN : screenWidth - MARGIN - boxWidth;
            notification.setTarX((int) targetX);
            notification.translate.interpolate(targetX, y, 0.30F);

            float x = notification.translate.getX();
            float boxY = notification.translate.getY();
            int accent = getColor(notification.getType());
            boolean moduleEnabled = "Enabled".equalsIgnoreCase(notification.getHeader());
            boolean moduleDisabled = "Disabled".equalsIgnoreCase(notification.getHeader());
            boolean moduleToggle = moduleEnabled || moduleDisabled;

            BackdropBlur.drawRoundedPanel(x, boxY, boxWidth, BOX_HEIGHT, 6.0F, 0xB80A0B0E);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 7.0F, boxY + 4.0F, 0.0F);
            GlStateManager.scale(1.5F, 1.5F, 1.0F);
            ClientFont.drawStringWithShadow("!", 0.0F, 0.0F, accent);
            GlStateManager.popMatrix();

            int headerColor = moduleToggle ? accent : 0xFFFFFFFF;
            ClientFont.drawStringWithShadow(header, x + 16.0F, boxY + 4.0F, headerColor);
            ClientFont.drawStringWithShadow(subtext, x + 16.0F, boxY + 17.0F, 0xFFE0E1E4);

            long duration = Math.max(1L, notification.getDisplayTime());
            double remaining = Math.max(0.0D, Math.min(1.0D,
                    1.0D - (double) (notification.checkTime() - notification.getStart()) / duration));
            float progressX = x + 7.0F;
            float progressY = boxY + BOX_HEIGHT - 3.0F;
            float progressWidth = Math.max(0.0F, (boxWidth - 14.0F) * (float) remaining);
            RoundedUtils.drawRoundedRect(progressX, progressY, boxWidth - 14.0F, 1.5F, 0x40FFFFFF, 1.0F);
            if (progressWidth > 0.0F) {
                RoundedUtils.drawRoundedRect(progressX, progressY, progressWidth, 1.5F, accent, 1.0F);
            }

            if (leaving && notification.translate.getX() >= screenWidth - 1.0F) {
                notifications.remove(notification);
            }
            y += SPACING;
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
    }

    private String trim(String text, int maxWidth) {
        if (text == null) return "";
        if (ClientFont.getStringWidth(text) <= maxWidth) return text;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (ClientFont.getStringWidth(builder.toString() + text.charAt(i) + "...") > maxWidth) break;
            builder.append(text.charAt(i));
        }
        return builder.append("...").toString();
    }

    private int getColor(NotificationType type) {
        if (type == NotificationType.WARNING) return FAILURE;
        if (type == NotificationType.OKAY) return SUCCESS;
        if (type == NotificationType.NOTIFY) return NOTICE;
        return INFO;
    }
}
