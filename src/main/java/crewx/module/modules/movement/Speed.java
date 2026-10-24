package crewx.module.modules.movement;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.event.types.Priority;
import crewx.events.LivingUpdateEvent;
import crewx.events.StrafeEvent;
import crewx.mixin.IAccessorEntity;
import crewx.module.Module;
import crewx.util.MoveUtil;
import crewx.property.properties.FloatProperty;
import crewx.property.properties.ModeProperty;
import crewx.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;

public class Speed extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private boolean cheatBreakerInputLogged;
    public final FloatProperty multiplier = new FloatProperty("multiplier", 1.0F, 0.0F, 10.0F);
    public final FloatProperty friction = new FloatProperty("friction", 1.0F, 0.0F, 10.0F);
    public final PercentProperty strafe = new PercentProperty("strafe", 0);
    public final ModeProperty speedMode = new ModeProperty("mode", 0, new String[]{"Normal", "Legit"});

    private boolean canBoost() {
        Scaffold scaffold = (Scaffold) CrewX.moduleManager.modules.get(Scaffold.class);
        boolean moving = MoveUtil.isForwardPressed();
        if (!moving && crewx.inject.NotchMappings.isCheatBreaker()) {
            moving = crewx.util.KeyBindUtil.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())
                    || crewx.util.KeyBindUtil.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode())
                    || crewx.util.KeyBindUtil.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode())
                    || crewx.util.KeyBindUtil.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode());
        }
        return !scaffold.isEnabled() && moving
                && mc.thePlayer.getFoodStats().getFoodLevel() > 6
                && !mc.thePlayer.isSneaking()
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isInLava()
                && !((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    public Speed() {
        super("Speed", false);
    }

    @EventTarget(Priority.LOW)
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled() && this.canBoost()) {
            if (speedMode.getValue() == 1) {
                return;
            }

            if (mc.thePlayer.onGround) {
                mc.thePlayer.motionY = 0.42F;
                MoveUtil.setSpeed(
                        MoveUtil.getJumpMotion() * (double) this.multiplier.getValue().floatValue(),
                        MoveUtil.getMoveYaw()
                );
            } else {
                if (this.friction.getValue() != 1.0F) {
                    event.setFriction(event.getFriction() * this.friction.getValue());
                }
                if (this.strafe.getValue() > 0) {
                    double speed = MoveUtil.getSpeed();
                    MoveUtil.setSpeed(speed * (double) ((float) (100 - this.strafe.getValue()) / 100.0F), MoveUtil.getDirectionYaw());
                    MoveUtil.addSpeed(
                            speed * (double) ((float) this.strafe.getValue().intValue() / 100.0F), MoveUtil.getMoveYaw()
                    );
                }
            }
        }
    }

    @EventTarget(Priority.LOW)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && crewx.inject.NotchMappings.isCheatBreaker()
                && !this.cheatBreakerInputLogged && mc.thePlayer != null
                && crewx.util.KeyBindUtil.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())) {
            this.cheatBreakerInputLogged = true;
            crewx.inject.CrewXBootstrap.log("CheatBreaker Speed input: mode=" + speedMode.getValue()
                    + " key=" + mc.gameSettings.keyBindForward.isKeyDown()
                    + " input=" + mc.thePlayer.movementInput.moveForward
                    + " canBoost=" + this.canBoost());
        }
        if (this.isEnabled() && this.canBoost()) {
            if (speedMode.getValue() == 1) {
                mc.thePlayer.movementInput.jump = true;
            } else {
                mc.thePlayer.movementInput.jump = false;
            }
        }
    }
}
