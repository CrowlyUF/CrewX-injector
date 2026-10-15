package crewx.module.modules.combat;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.events.TickEvent;
import crewx.module.Module;
import crewx.property.properties.IntProperty;
import net.minecraft.client.Minecraft;

import java.util.Random;

public class Fakelag extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random RANDOM = new Random();

    public final IntProperty minDelay = new IntProperty("min-delay", 100, 0, 2000);
    public final IntProperty maxDelay = new IntProperty("max-delay", 200, 0, 2000);

    private int currentDelay = 0;
    private long lastRoll = 0L;

    public Fakelag() {
        super("Fakelag", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        long now = System.currentTimeMillis();
        if (now - lastRoll >= currentDelay || currentDelay == 0) {
            int min = Math.min(minDelay.getValue(), maxDelay.getValue());
            int max = Math.max(minDelay.getValue(), maxDelay.getValue());
            currentDelay = max <= min ? min : min + RANDOM.nextInt(max - min + 1);
            lastRoll = now;
        }
        CrewX.lagManager.setDelay(currentDelay / 50);
    }

    @Override
    public void onDisabled() {
        CrewX.lagManager.setDelay(0);
        currentDelay = 0;
        lastRoll = 0L;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{minDelay.getValue() + "-" + maxDelay.getValue() + "ms"};
    }
}