package crewx.module.modules.combat;

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.event.types.Priority;
import crewx.events.AttackEvent;
import crewx.events.KnockbackEvent;
import crewx.events.LoadWorldEvent;
import crewx.events.LivingUpdateEvent;
import crewx.events.PacketEvent;
import crewx.events.TickEvent;
import crewx.mixin.IAccessorEntity;
import crewx.module.Module;
import crewx.module.modules.misc.AntiBot;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.ModeProperty;
import crewx.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;

import java.util.ArrayList;
import java.util.List;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int DELAY_TICKS = 40;
    private static final int ATTACK_COOLDOWN_TICKS = 4;

    private int chanceCounter = 0;
    private boolean pendingExplosion = false;
    private boolean allowNext = true;

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Vanilla", "Jump Reset", "Delay"});
    public final PercentProperty chance = new PercentProperty("chance", 100);
    public final PercentProperty horizontal = new PercentProperty("horizontal", 0);
    public final PercentProperty vertical = new PercentProperty("vertical", 100);
    public final PercentProperty explosionHorizontal = new PercentProperty("explosions-horizontal", 100);
    public final PercentProperty explosionVertical = new PercentProperty("explosions-vertical", 100);
    public final BooleanProperty fakeCheck = new BooleanProperty("fake-check", true);

    private final List<Runnable> delayPacketActions = new ArrayList<>();
    private volatile boolean delayActive = false;
    private int delayTicks = 0;
    private volatile int attackCooldownTicks = 0;

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    private boolean isJumpReset() {
        return this.mode.getValue() == 1;
    }

    private boolean isDelayMode() {
        return this.mode.getValue() == 2;
    }

    public Velocity() {
        super("Velocity", false);
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        this.pendingExplosion = false;
        this.allowNext = true;
        this.attackCooldownTicks = 0;
        synchronized (this.delayPacketActions) {
            this.delayPacketActions.clear();
            this.delayActive = false;
            this.delayTicks = 0;
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        this.flushDelayQueue();
        this.pendingExplosion = false;
        this.allowNext = true;
        this.attackCooldownTicks = 0;
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            this.pendingExplosion = false;
            this.allowNext = true;
        } else if (this.isDelayMode()) {
            this.pendingExplosion = false;
            this.allowNext = true;
        } else if (this.isJumpReset()) {
            this.allowNext = true;
            if (mc.thePlayer != null && mc.thePlayer.onGround && !this.isInLiquidOrWeb()) {
                mc.thePlayer.jump();
            }
        } else if (!this.allowNext || !(Boolean) this.fakeCheck.getValue()) {
            this.allowNext = true;
            if (this.pendingExplosion) {
                this.pendingExplosion = false;
                if (this.explosionHorizontal.getValue() > 0) {
                    event.setX(event.getX() * (double) this.explosionHorizontal.getValue() / 100.0);
                    event.setZ(event.getZ() * (double) this.explosionHorizontal.getValue() / 100.0);
                } else {
                    event.setX(mc.thePlayer.motionX);
                    event.setZ(mc.thePlayer.motionZ);
                }
                if (this.explosionVertical.getValue() > 0) {
                    event.setY(event.getY() * (double) this.explosionVertical.getValue() / 100.0);
                } else {
                    event.setY(mc.thePlayer.motionY);
                }
            } else {
                this.chanceCounter = this.chanceCounter % 100 + this.chance.getValue();
                if (this.chanceCounter >= 100) {
                    if (this.horizontal.getValue() > 0) {
                        event.setX(event.getX() * (double) this.horizontal.getValue() / 100.0);
                        event.setZ(event.getZ() * (double) this.horizontal.getValue() / 100.0);
                    } else {
                        event.setX(mc.thePlayer.motionX);
                        event.setZ(mc.thePlayer.motionZ);
                    }
                    if (this.vertical.getValue() > 0) {
                        event.setY(event.getY() * (double) this.vertical.getValue() / 100.0);
                    } else {
                        event.setY(mc.thePlayer.motionY);
                    }
                }
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            this.pendingExplosion = false;
            this.allowNext = true;
            return;
        }

        if (this.isDelayMode()) {
            this.handleDelayPacket(event);
            this.pendingExplosion = false;
            this.allowNext = true;
            return;
        }

        if (event.getType() == EventType.RECEIVE) {
            if (event.getPacket() instanceof S27PacketExplosion) {
                S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
                if (packet.func_149149_c() != 0.0F || packet.func_149144_d() != 0.0F || packet.func_149147_e() != 0.0F) {
                    this.pendingExplosion = true;
                    if (this.explosionHorizontal.getValue() == 0 || this.explosionVertical.getValue() == 0) {
                        event.setCancelled(true);
                    }
                }
            } else if (event.getPacket() instanceof S19PacketEntityStatus) {
                S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
                net.minecraft.entity.Entity entity = packet.getEntity(mc.theWorld);
                if (entity != null && entity.equals(mc.thePlayer) && packet.getOpCode() == 2) {
                    this.allowNext = false;
                }
            }
        }
    }

    private void handleDelayPacket(PacketEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (event.getType() == EventType.RECEIVE && packet instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus status = (S19PacketEntityStatus) packet;
            if (this.attackCooldownTicks == 0 && status.getOpCode() == 2
                    && status.getEntity(mc.theWorld) == mc.thePlayer && this.isNearOpponent()) {
                this.queueDelayAction(() -> { }, true);
            }
            return;
        }

        if (event.getType() == EventType.RECEIVE && packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) packet;
            if (this.attackCooldownTicks == 0 && velocity.getEntityID() == mc.thePlayer.getEntityId()
                    && (this.delayActive || this.isNearOpponent())
                    && this.queueIncomingDelayPacket(packet, true)) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getType() == EventType.SEND && packet instanceof C02PacketUseEntity) {
            C02PacketUseEntity useEntity = (C02PacketUseEntity) packet;
            if (useEntity.getAction() == C02PacketUseEntity.Action.ATTACK) {
                this.attackCooldownTicks = ATTACK_COOLDOWN_TICKS;
                if (this.delayActive) {
                    this.flushDelayQueue();
                }
            }
            return;
        }

        if (event.getType() == EventType.SEND && packet instanceof C0FPacketConfirmTransaction && this.delayActive) {
            INetHandlerPlayClient handler = mc.getNetHandler();
            if (this.queueDelayAction(() -> {
                if (handler != null && mc.getNetHandler() == handler) {
                    crewx.util.PacketUtil.sendPacketNoEvent(packet);
                }
            }, false)) {
                event.setCancelled(true);
            }
        }
    }

    private boolean queueIncomingDelayPacket(Packet<?> packet, boolean startIfIdle) {
        INetHandlerPlayClient handler = mc.getNetHandler();
        if (handler == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Packet<INetHandlerPlayClient> incoming = (Packet<INetHandlerPlayClient>) packet;
        return this.queueDelayAction(() -> {
            if (mc.getNetHandler() == handler) {
                try {
                    incoming.processPacket(handler);
                } catch (Exception ignored) {
                }
            }
        }, startIfIdle);
    }

    private boolean queueDelayAction(Runnable action, boolean startIfIdle) {
        synchronized (this.delayPacketActions) {
            if (!this.delayActive && !startIfIdle) {
                return false;
            }
            this.delayPacketActions.add(action);
            if (!this.delayActive) {
                this.delayActive = true;
                this.delayTicks = 0;
            }
        }
        return true;
    }

    private void flushDelayQueue() {
        List<Runnable> actions;
        synchronized (this.delayPacketActions) {
            actions = new ArrayList<>(this.delayPacketActions);
            this.delayPacketActions.clear();
            this.delayActive = false;
            this.delayTicks = 0;
        }
        for (Runnable action : actions) {
            try {
                action.run();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private boolean isNearOpponent() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return false;
        }
        AntiBot antiBot = CrewX.moduleManager == null ? null
                : (AntiBot) CrewX.moduleManager.getModule(AntiBot.class);
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            boolean isBot = antiBot != null && antiBot.isEnabled() && antiBot.isBot(player);
            if (player != mc.thePlayer && !player.isDead && !isBot
                    && mc.thePlayer.getDistanceToEntity(player) <= 5.0F) {
                return true;
            }
        }
        return false;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST) {
            return;
        }

        if (this.attackCooldownTicks > 0) {
            this.attackCooldownTicks--;
        }
        boolean flushDelay = false;
        synchronized (this.delayPacketActions) {
            if (this.delayActive && ++this.delayTicks >= DELAY_TICKS) {
                flushDelay = true;
            }
        }
        if (flushDelay) {
            this.flushDelayQueue();
        }
        if (!this.isDelayMode()) {
            this.pendingExplosion = false;
            this.allowNext = true;
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.flushDelayQueue();
        this.onDisabled();
    }

    @Override
    public String[] getSuffix() {
        if (this.isDelayMode()) {
            return new String[]{"40 ticks"};
        }
        return new String[]{this.mode.getModeString()};
    }
}
