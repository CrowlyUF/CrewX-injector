package crewx.module.modules.render;

import crewx.gui.ClientFont;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.CrewX;
import crewx.clickgui.render.RoundedUtils;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.events.PacketEvent;
import crewx.events.Render2DEvent;
import crewx.module.Module;
import crewx.util.ColorUtil;
import crewx.util.RenderUtil;
import crewx.util.TeamUtil;
import crewx.util.TimerUtil;
import crewx.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.ResourceLocation;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class TargetHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int PANEL = 0x1C1E23;
    private static final int TRACK = 0x33FFFFFF;
    private static final int BAR = 0xFFE6E6E6;
    private static final int NAME = 0xFFFFFFFF;
    private static final int WIN = 0xFF5BD47A;
    private static final int LOSE = 0xFFEF5B5B;
    private static final int DRAW = 0xFFE9B949;
    private static final float HEIGHT = 32.0F;
    private static final float HEAD = 24.0F;
    private static final float PAD = 4.0F;

    private final TimerUtil lastAttackTimer = new TimerUtil();
    private EntityLivingBase lastTarget = null;
    private EntityLivingBase target = null;
    private float visualHealth = Float.NaN;
    private float scaleAnim = 0.0f;
    private long lastUpdate = System.currentTimeMillis();
    private boolean dragging = false;
    private int dragX, dragY;

    public final FloatProperty scale = new FloatProperty("Scale", 1.0f, 0.5f, 1.5f);
    public final IntProperty offX = new IntProperty("Position X", 100, 0, 2000);
    public final IntProperty offY = new IntProperty("Position Y", 100, 0, 2000);
    public final PercentProperty backgroundAlpha = new PercentProperty("Background Alpha", 92);
    public final BooleanProperty showHead = new BooleanProperty("Show Head", true);

    public final BooleanProperty kaOnly = new BooleanProperty("KA Only", true);

    public TargetHUD() {
        super("TargetHUD", false, true);
    }

    private EntityLivingBase resolveTarget() {
        KillAura killAura = (KillAura) CrewX.moduleManager.modules.get(KillAura.class);
        if (killAura.isEnabled() && killAura.isAttackAllowed() && TeamUtil.isEntityLoaded(killAura.getTarget())) {
            return killAura.getTarget();
        }
        if (!kaOnly.getValue() && !lastAttackTimer.hasTimeElapsed(1500L) && TeamUtil.isEntityLoaded(lastTarget)) {
            return lastTarget;
        }
        if (mc.currentScreen instanceof GuiChat) {
            return mc.thePlayer;
        }
        return null;
    }

    private float getEntityHealth(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            Scoreboard scoreboard = player.getWorldScoreboard();
            ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(2);
            if (objective != null) {
                return (float) scoreboard.getValueFromObjective(player.getName(), objective).getScorePoints();
            }
            for (ScoreObjective obj : scoreboard.getScoreObjectives()) {
                String name = obj.getDisplayName().toLowerCase();
                if (name.contains("health") || name.contains("vida") || name.contains("hp")) {
                    return (float) scoreboard.getValueFromObjective(player.getName(), obj).getScorePoints();
                }
            }
        }
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    private ResourceLocation getSkin(EntityLivingBase entityLivingBase) {
        if (entityLivingBase instanceof EntityPlayer) {
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(entityLivingBase.getName());
            if (playerInfo != null) return playerInfo.getLocationSkin();
        }
        return null;
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;

        EntityLivingBase currentTarget = resolveTarget();
        long now = System.currentTimeMillis();
        float delta = Math.min((now - lastUpdate) / 1000.0f, 0.1f);
        lastUpdate = now;

        if (currentTarget != null) {
            if (currentTarget != target) visualHealth = Float.NaN;
            target = currentTarget;
            scaleAnim = Math.min(1.0f, scaleAnim + delta * 8.0f);
        } else {
            scaleAnim = Math.max(0.0f, scaleAnim - delta * 8.0f);
        }
        if (scaleAnim <= 0.0f) {
            target = null;
            visualHealth = Float.NaN;
            return;
        }
        if (target == null) return;

        float health = getEntityHealth(target);
        float maxHealth = Math.max(target.getMaxHealth() + target.getAbsorptionAmount(), health);
        if (maxHealth <= 0) maxHealth = 20.0f;
        visualHealth = TargetHudMath.animateHealth(visualHealth, health, delta);

        String name = target.getName();
        boolean head = showHead.getValue() && getSkin(target) != null;
        float textX = PAD + (head ? HEAD + 6.0F : 2.0F);
        float letterW = ClientFont.getStringWidthFloat("W");
        float width = Math.max(120.0F, textX + ClientFont.getStringWidthFloat(name) + 8.0F + letterW + PAD + 2.0F);
        float x = offX.getValue();
        float y = offY.getValue();

        if (mc.currentScreen instanceof GuiChat) {
            ScaledResolution sr = new ScaledResolution(mc);
            int mouseX = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
            int mouseY = sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;
            if (Mouse.isButtonDown(0)) {
                if (!dragging && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEIGHT) {
                    dragging = true;
                    dragX = (int) (mouseX - x);
                    dragY = (int) (mouseY - y);
                }
                if (dragging) {
                    offX.setValue(Math.max(0, mouseX - dragX));
                    offY.setValue(Math.max(0, mouseY - dragY));
                }
            } else {
                dragging = false;
            }
        }

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float renderScale = scale.getValue() * scaleAnim;
        GlStateManager.translate(x + width / 2.0f, y + HEIGHT / 2.0f, 0);
        GlStateManager.scale(renderScale, renderScale, 1.0f);
        GlStateManager.translate(-(x + width / 2.0f), -(y + HEIGHT / 2.0f), 0);


        for (int i = 3; i >= 1; i--) {
            RoundedUtils.drawRoundedRect(x - i, y - i + 1, width + i * 2, HEIGHT + i * 2, 0x14000000, 5.0F + i);
        }
        int bgAlpha = Math.round(255 * (backgroundAlpha.getValue() / 100.0f));
        RoundedUtils.drawRoundedRect(x, y, width, HEIGHT, (bgAlpha << 24) | PANEL, 5.0F);

        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        if (head) {
            ResourceLocation skin = getSkin(target);
            float hurt = target.hurtTime > 0 ? 0.6F : 1.0F;
            mc.getTextureManager().bindTexture(skin);
            GlStateManager.color(1.0f, hurt, hurt, 1.0f);
            int hx = Math.round(x + PAD);
            int hy = Math.round(y + PAD);
            Gui.drawScaledCustomSizeModalRect(hx, hy, 8.0f, 8.0f, 8, 8, (int) HEAD, (int) HEAD, 64.0f, 64.0f);
            Gui.drawScaledCustomSizeModalRect(hx, hy, 40.0f, 8.0f, 8, 8, (int) HEAD, (int) HEAD, 64.0f, 64.0f);
        }
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);


        float fontH = ClientFont.getHeight();
        float nameY = y + 6.0F;
        float maxName = width - textX - PAD - letterW - 8.0F;
        String fitted = ClientFont.getStringWidthFloat(name) > maxName
                ? ClientFont.trimStringToWidth(name, Math.max(1, (int) maxName - 6)) + "\u2026" : name;
        ClientFont.drawString(fitted, x + textX, nameY, NAME, false);
        char verdict = TargetHudMath.verdict(getEntityHealth(mc.thePlayer), health);
        int verdictColor = verdict == 'W' ? WIN : verdict == 'L' ? LOSE : DRAW;
        String letter = String.valueOf(verdict);
        ClientFont.drawString(letter, x + width - PAD - 2.0F - ClientFont.getStringWidthFloat(letter), nameY, verdictColor, false);


        float barX = x + textX;
        float barW = width - textX - PAD - 2.0F;
        float barH = 3.0F;
        float barY = Math.max(nameY + fontH + 3.0F, y + HEIGHT - PAD - barH - 3.0F);
        RoundedUtils.drawRoundedRect(barX, barY, barW, barH, TRACK, barH / 2.0F);
        float filled = TargetHudMath.fraction(visualHealth, maxHealth) * barW;
        if (filled > 0.5F) {
            RoundedUtils.drawRoundedRect(barX, barY, Math.max(barH, filled), barH, BAR, barH / 2.0F);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
            if (packet.getAction() == Action.ATTACK) {
                Entity entity = packet.getEntityFromWorld(mc.theWorld);
                if (entity instanceof EntityLivingBase && !(entity instanceof EntityArmorStand)) {
                    this.lastAttackTimer.reset();
                    this.lastTarget = (EntityLivingBase) entity;
                }
            }
        }
    }
}
