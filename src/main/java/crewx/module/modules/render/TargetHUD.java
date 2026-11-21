package crewx.module.modules.render;

import crewx.clickgui.clone.mc.TargetHudFont;
import crewx.gui.GuiRender;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.CrewX;
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
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
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
    private static final int PANEL = 0x131313;
    private static final int NAME = 0xDCE6E6EC;
    private static final int ACCENT_FALLBACK = 0xFFB1FF80;
    private static final float HEIGHT = 24.0F;
    private static final float HEAD = 16.0F;
    private static final float PAD = 2.0F;

    private final TimerUtil lastAttackTimer = new TimerUtil();
    private EntityLivingBase lastTarget = null;
    private EntityLivingBase target = null;
    private float visualHealth = Float.NaN;
    private float scaleAnim = 0.0f;
    private long lastUpdate = System.nanoTime();
    private boolean dragging = false;
    private int dragX, dragY;

    public final FloatProperty scale = new FloatProperty("Scale", 1.0f, 0.5f, 1.5f);
    public final IntProperty offX = new IntProperty("Position X", 100, 0, 2000);
    public final IntProperty offY = new IntProperty("Position Y", 100, 0, 2000);
    public final BooleanProperty showHead = new BooleanProperty("Show Head", true);

    public final BooleanProperty kaOnly = new BooleanProperty("KA Only", true);
    /** Screen: fixed, draggable panel. Follow target: smaller, next to the player you hit, until 1.5 s after the last hit. */
    public final ModeProperty position = new ModeProperty("Position", 0, new String[]{"Screen", "Follow target"});

    /** Follow mode: hidden 1.5 s after the last hit on the target. */
    private static final long FOLLOW_HIDE_MS = 1500L;
    /** Follow mode: the panel is drawn smaller, as a tag next to the player rather than a HUD element. */
    private static final float FOLLOW_SCALE = 0.72F;
    /** Follow mode: gap between the player's box and the panel (GUI units). */
    private static final float FOLLOW_GAP = 3.0F;
    /** Last projected bounds of the followed target on screen (scaled GUI units), from the 3D pass. */
    private final double[] followBounds = new double[4];
    private boolean followVisible;
    private EntityLivingBase followProjected;
    private boolean followOnLeft;

    public TargetHUD() {
        super("TargetHUD", false, true);
    }

    private boolean following() {
        return this.position.getValue() == 1;
    }

    public boolean needsSmoothOverlay() {
        return this.isEnabled() && following();
    }

    private EntityLivingBase resolveTarget() {
        if (following()) {
            // whoever we hit last, while hits keep coming (any hit refreshes the timer, KillAura's too)
            if (!lastAttackTimer.hasTimeElapsed(FOLLOW_HIDE_MS) && TeamUtil.isEntityLoaded(lastTarget)
                    && !lastTarget.isDead && lastTarget.getHealth() > 0.0F) {
                return lastTarget;
            }
            return null;
        }
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
            return DefaultPlayerSkin.getDefaultSkin(entityLivingBase.getUniqueID());
        }
        return null;
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.needsSmoothOverlay() && crewx.inject.FrameOverlay.deferNormalDraw()) return;
        if (!this.isEnabled() || mc.thePlayer == null) return;

        EntityLivingBase currentTarget = resolveTarget();
        long now = System.nanoTime();
        float delta = Math.max(0.0f, Math.min((now - lastUpdate) / 1_000_000_000.0f, 0.1f));
        lastUpdate = now;

        if (currentTarget != null) {
            if (currentTarget != target) visualHealth = Float.NaN;   // new target: no drain from the old one
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
        float textX = head ? 19.5F : 2.0F;
        float healthDifference = getEntityHealth(mc.thePlayer) - health;
        float roundedDifference = Math.round(Math.abs(healthDifference) * 10.0F) / 10.0F;
        String difference = roundedDifference == (int) roundedDifference
                ? Integer.toString((int) roundedDifference) : Float.toString(roundedDifference);
        String sign = healthDifference > 0.0F ? "+" : healthDifference < 0.0F ? "-" : "";
        float width = Math.max(80.0F, TargetHudFont.width(12, name) + 21.0F);
        float x = offX.getValue();
        float y = offY.getValue();
        if (following()) {
            if (!this.followVisible || this.followProjected != target) {
                // off screen / behind the camera: fade out like a lost target
                scaleAnim = Math.max(0.0f, scaleAnim - delta * 16.0f);
                if (scaleAnim <= 0.0f) return;
            }
            ScaledResolution sr = new ScaledResolution(mc);
            float boxLeft = (float) this.followBounds[0];
            float boxRight = (float) this.followBounds[2];
            float boxTop = (float) this.followBounds[1];
            float boxBottom = (float) this.followBounds[3];
            // where the (smaller) panel is actually drawn: right next to the box, at chest height
            float drawnW = width * scale.getValue() * FOLLOW_SCALE;
            float drawnH = HEIGHT * scale.getValue() * FOLLOW_SCALE;
            // Side of the box the panel sits on. It only changes when the current side really does not fit (with a
            // margin): near the screen edge it used to flip right/left/right every frame and looked like shaking.
            float rightLeft = boxRight + FOLLOW_GAP;
            float leftLeft = boxLeft - FOLLOW_GAP - drawnW;
            float edge = sr.getScaledWidth() - 4.0F;
            if (!this.followOnLeft && rightLeft + drawnW > edge) {
                this.followOnLeft = true;
            } else if (this.followOnLeft && rightLeft + drawnW < edge - 24.0F) {
                this.followOnLeft = false;
            }
            float left = this.followOnLeft ? leftLeft : rightLeft;
            float top = boxTop + (boxBottom - boxTop) * 0.18F;
            left = Math.max(4.0F, Math.min(sr.getScaledWidth() - drawnW - 4.0F, left));
            top = Math.max(4.0F, Math.min(sr.getScaledHeight() - drawnH - 4.0F, top));
            // the panel scales around its centre
            x = left + drawnW / 2.0F - width / 2.0F;
            y = top + drawnH / 2.0F - HEIGHT / 2.0F;
            // No smoothing: it lags behind the camera's view bobbing and makes the panel shake while walking.
            // Snap to whole framebuffer pixels instead.
            float pixel = 1.0F / Math.max(1, sr.getScaleFactor());
            x = Math.round(x / pixel) * pixel;
            y = Math.round(y / pixel) * pixel;
        }

        if (!following() && mc.currentScreen instanceof GuiChat) {
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
        float renderScale = scale.getValue() * scaleAnim * (following() ? FOLLOW_SCALE : 1.0F);
        GlStateManager.translate(x + width / 2.0f, y + HEIGHT / 2.0f, 0);
        GlStateManager.scale(renderScale, renderScale, 1.0f);
        GlStateManager.translate(-(x + width / 2.0f), -(y + HEIGHT / 2.0f), 0);

        GuiRender.rect(x, y, width, HEIGHT, 0xAA000000 | PANEL);

        TargetHudFont.draw(12, name, x + textX, y + 3.5F, NAME);
        if (!sign.isEmpty()) {
            float numberX = x + width - 2.0F - TargetHudFont.width(10, difference);
            TargetHudFont.draw(8, sign, numberX - TargetHudFont.width(8, sign), y + 16.5F, NAME);
            TargetHudFont.draw(10, difference, numberX, y + 16.0F, NAME);
        }

        HUD hud = (HUD) CrewX.moduleManager.modules.get(HUD.class);
        int accent = hud == null ? ACCENT_FALLBACK : hud.getColor(System.currentTimeMillis()).getRGB();

        float barX = x + 1.5F;
        float barY = y + HEIGHT - 4.5F;
        float barW = width - 3.0F;
        GuiRender.rect(barX, barY, barW, 3.0F, 0x7F27272A);
        GuiRender.rect(barX + 0.5F, barY + 0.5F, barW - 1.0F, 2.0F, 0xAA000000 | PANEL);
        float trail = TargetHudMath.fraction(visualHealth, maxHealth) * (barW - 1.0F);
        float filled = TargetHudMath.fraction(health, maxHealth) * (barW - 1.0F);
        if (trail > 0.0F) {
            GuiRender.rect(barX + 0.5F, barY + 0.5F, trail, 2.0F, darken(accent));
        }
        if (filled > 0.0F) {
            GuiRender.rect(barX + 0.5F, barY + 0.5F, filled, 2.0F, accent);
        }
        float absorption = TargetHudMath.fraction(target.getAbsorptionAmount(), maxHealth) * (barW - 1.0F);
        if (absorption > 0.0F) {
            int bright = 0xFF000000 | (Math.min(255, (int) (((accent >> 16) & 255) / 0.45F)) << 16)
                    | (Math.min(255, (int) (((accent >> 8) & 255) / 0.45F)) << 8)
                    | Math.min(255, (int) ((accent & 255) / 0.45F));
            GuiRender.rect(barX + 0.5F, barY + 0.5F, absorption, 2.0F, bright);
        }

        if (head) {
            ResourceLocation skin = getSkin(target);
            float hurt = target.hurtTime > 0 ? Math.min(1.0F, target.hurtTime / 21.5F) : 0.0F;
            mc.getTextureManager().bindTexture(skin);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GlStateManager.color(1.0F - hurt * (18.0F / 255.0F),
                    1.0F - hurt * (195.0F / 255.0F), 1.0F - hurt * (195.0F / 255.0F), 1.0F);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 2.0F, y + 2.0F, 0.0F);
            Gui.drawScaledCustomSizeModalRect(0, 0, 8.0F, 8.0F, 8, 8, (int) HEAD, (int) HEAD, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect(0, 0, 40.0F, 8.0F, 8, 8, (int) HEAD, (int) HEAD, 64.0F, 64.0F);
            GlStateManager.popMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (target instanceof EntityPlayer) {
            drawEquipment((EntityPlayer) target, x + 20.0F, y + 10.0F);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static int darken(int color) {
        return 0xFF000000 | (((int) (((color >> 16) & 255) * 0.63F)) << 16)
                | (((int) (((color >> 8) & 255) * 0.63F)) << 8)
                | ((int) ((color & 255) * 0.63F));
    }

    private void drawEquipment(EntityPlayer player, float x, float y) {
        ItemStack[] items = new ItemStack[5];
        items[0] = player.getHeldItem();
        for (int i = 0; i < 4; i++) items[i + 1] = player.inventory.armorInventory[3 - i];
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        RenderHelper.enableGUIStandardItemLighting();
        try {
            int offset = 0;
            for (ItemStack item : items) {
                if (item != null) {
                    mc.getRenderItem().renderItemAndEffectIntoGUI(item, offset, 0);
                    offset += 16;
                }
            }
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    /** Follow mode: where the target is on screen this frame, projected with the world camera. */
    @EventTarget
    public void onRender3D(crewx.events.Render3DEvent event) {
        this.followVisible = false;
        if (!this.isEnabled() || !following() || this.target == null || mc.thePlayer == null) return;
        ScaledResolution sr = new ScaledResolution(mc);
        RenderUtil.ScreenRect bounds = RenderUtil.projectToScreen(this.target, sr.getScaleFactor());
        if (bounds == null) return;
        this.followBounds[0] = bounds.x;
        this.followBounds[1] = bounds.y;
        this.followBounds[2] = bounds.z;
        this.followBounds[3] = bounds.w;
        this.followProjected = this.target;
        this.followVisible = bounds.z > 0.0 && bounds.x < sr.getScaledWidth() && bounds.w > 0.0 && bounds.y < sr.getScaledHeight();
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
