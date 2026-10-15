package crewx.module.modules.combat;

import crewx.CrewX;
import crewx.enums.BlinkModules;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.event.types.Priority;
import crewx.events.LeftClickMouseEvent;
import crewx.events.Render3DEvent;
import crewx.events.TickEvent;
import crewx.events.UpdateEvent;
import crewx.mixin.IAccessorRenderManager;
import crewx.module.Module;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.FloatProperty;
import crewx.property.properties.ModeProperty;
import crewx.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.opengl.GL11;
import java.util.Random;

/** Adapted from the supplied DisplaceModule for CrewX events and properties. */
public class Displace extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random RANDOM = new Random();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Left", "Right", "Random", "Void"});
    public final FloatProperty angle = new FloatProperty("Angle", 90.0F, 0.0F, 180.0F);
    public final FloatProperty cooldown = new FloatProperty("Cooldown", 0.0F, 0.0F, 5.0F);
    public final BooleanProperty hideRotations = new BooleanProperty("Hide rotations", false);
    public final BooleanProperty onlyKnockbackItems = new BooleanProperty("Only knockback items", false);
    public final BooleanProperty overrideAttacks = new BooleanProperty("Override attacks", false);
    public final BooleanProperty playersOnly = new BooleanProperty("Players only", true);
    public final BooleanProperty renderArrow = new BooleanProperty("Render arrow", false);
    public final BooleanProperty manual = new BooleanProperty("Manual", true);
    public final BooleanProperty autoClicker = new BooleanProperty("AutoClicker", false);
    public final BooleanProperty killAura = new BooleanProperty("KillAura", false);

    private long lastAt;
    private int blinkTicks;
    private int side = -1, lastSide = -1;
    private boolean rotate, queued, replayingClick;
    private EntityLivingBase lastTarget;
    private long arrowAt;
    private double arrowX, arrowY, arrowZ, dirX, dirZ;

    public Displace() { super("Displace", false); }

    @Override
    public void onDisabled() {
        if (blinkTicks > 0) CrewX.blinkManager.setBlinkState(false, BlinkModules.DISPLACE);
        blinkTicks = 0;
        rotate = queued = replayingClick = false;
        side = lastSide = -1;
        arrowAt = 0L;
    }

    @Override
    public String[] getSuffix() { return new String[]{mode.getModeString()}; }

    private boolean canDisplace(Entity entity, boolean checkHurtTime, boolean checkCooldown) {
        if (mc.thePlayer == null || mc.theWorld == null || !(entity instanceof EntityLivingBase)) return false;
        EntityLivingBase living = (EntityLivingBase) entity;
        if (checkHurtTime && living.hurtTime > 0) return false;
        if (playersOnly.getValue() && !(entity instanceof EntityPlayer)) return false;
        int knockback = EnchantmentHelper.getKnockbackModifier(mc.thePlayer);
        if (!mc.thePlayer.isSprinting() && knockback == 0) return false;
        if (onlyKnockbackItems.getValue() && knockback == 0) return false;
        if (checkCooldown && Math.abs(System.currentTimeMillis() - lastAt) < cooldown.getValue() * 1000.0 - 20.0) return false;
        if (mode.getValue() == 3 && (side = (lastSide = dropSide(living, lastSide))) == -1) return false;
        lastTarget = living;
        return true;
    }

    private int dropSide(EntityLivingBase target, int previous) {
        double dx = target.posX - mc.thePlayer.posX, dz = target.posZ - mc.thePlayer.posZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) return -1;
        double rightX = -dz / length, rightZ = dx / length;
        int left = 0, right = 0, y = (int) Math.floor(target.posY - 1.0);
        for (int i = 1; i <= 12; i++) {
            double distance = i * 0.5;
            if (isDrop(target.posX - rightX * distance, y, target.posZ - rightZ * distance)) left++;
            if (isDrop(target.posX + rightX * distance, y, target.posZ + rightZ * distance)) right++;
        }
        if (left == 0 && right == 0) return -1;
        return left == right && previous >= 0 ? previous : left >= right ? 0 : 1;
    }

    private boolean isDrop(double x, int y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int depth = 0; depth < 10; depth++) {
            if (!mc.theWorld.isAirBlock(new BlockPos(bx, y - depth, bz))) return false;
        }
        return true;
    }

    private float offset() {
        float result = angle.getValue();
        if (side >= 0) {
            if (side == 0) result = -result;
            side = -1;
        } else if (mode.getValue() == 0 || mode.getValue() == 2 && RANDOM.nextBoolean()) result = -result;
        result += RANDOM.nextFloat() * 10.0F - 5.0F;
        if (renderArrow.getValue() && lastTarget != null) setArrow(lastTarget, result);
        return result;
    }

    private void trigger(boolean fromAura) {
        if (fromAura) queued = true;
        else rotate = true;
        lastAt = System.currentTimeMillis();
    }

    @EventTarget(Priority.HIGHEST)
    public void onLeftClick(LeftClickMouseEvent event) {
        if (replayingClick) {
            replayingClick = false;
            return;
        }
        if (!isEnabled() || !manual.getValue() || mc.currentScreen != null || event.isCancelled()) return;
        if (!autoClicker.getValue()) {
            AutoClicker clicker = (AutoClicker) CrewX.moduleManager.modules.get(AutoClicker.class);
            if (clicker != null && clicker.isEnabled()) return;
        }
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) return;
        Entity entity = hit.entityHit;
        if (canDisplace(entity, true, true)) {
            event.setCancelled(true);
            trigger(false);
        } else if (overrideAttacks.getValue() && canDisplace(entity, false, false)) event.setCancelled(true);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (isEnabled() && event.getType() == EventType.PRE && blinkTicks > 0 && --blinkTicks == 0)
            CrewX.blinkManager.setBlinkState(false, BlinkModules.DISPLACE);
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) return;
        if (killAura.getValue()) {
            KillAura aura = (KillAura) CrewX.moduleManager.modules.get(KillAura.class);
            if (aura != null && aura.isEnabled() && canDisplace(aura.getTarget(), true, true)) trigger(true);
        }
        if (!rotate && !queued) return;
        rotate = queued = false;
        if (hideRotations.getValue() && CrewX.blinkManager.setBlinkState(true, BlinkModules.DISPLACE)) blinkTicks = 2;
        float yaw = mc.thePlayer.rotationYaw + offset();
        event.setRotation(yaw, mc.thePlayer.rotationPitch, 2);
        event.setPervRotation(yaw, 2);
        replayingClick = true;
        KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
    }

    private void setArrow(EntityLivingBase target, float yawOffset) {
        double direction = Math.toRadians(mc.thePlayer.rotationYaw + yawOffset);
        dirX = -Math.sin(direction);
        dirZ = Math.cos(direction);
        arrowX = target.posX + dirX * 0.8;
        arrowY = target.posY + 1.2;
        arrowZ = target.posZ + dirZ * 0.8;
        arrowAt = System.currentTimeMillis();
    }

    @EventTarget
    public void onRenderWorld(Render3DEvent event) {
        if (!isEnabled() || !renderArrow.getValue() || arrowAt == 0L || mc.theWorld == null) return;
        long elapsed = System.currentTimeMillis() - arrowAt;
        if (elapsed > 600L) { arrowAt = 0L; return; }
        double appear = Math.min(1.0, elapsed / 100.0);
        double fade = elapsed > 500L ? Math.max(0.0, 1.0 - (elapsed - 500L) / 100.0) : 1.0;
        float alpha = (float) ((0.08 + 0.92 * appear * appear * (3.0 - 2.0 * appear)) * fade);
        IAccessorRenderManager render = (IAccessorRenderManager) mc.getRenderManager();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(arrowX - render.getRenderPosX() + dirX * 0.8,
                    arrowY - render.getRenderPosY() + Math.sin(elapsed * 0.015) * 0.02,
                    arrowZ - render.getRenderPosZ() + dirZ * 0.8);
            GlStateManager.rotate((float) Math.toDegrees(Math.atan2(-dirZ, dirX)), 0, 1, 0);
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.disableCull();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.color(1, 1, 1, alpha);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3d(-0.2784, -0.0816, 0);
            GL11.glVertex3d(0, -0.0288, 0);
            GL11.glVertex3d(0, 0.0288, 0);
            GL11.glVertex3d(-0.2784, 0.0816, 0);
            GL11.glEnd();
            GL11.glBegin(GL11.GL_TRIANGLES);
            GL11.glVertex3d(0, -0.144, 0);
            GL11.glVertex3d(0.288, 0, 0);
            GL11.glVertex3d(0, 0.144, 0);
            GL11.glEnd();
        } finally {
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }
    }
}
