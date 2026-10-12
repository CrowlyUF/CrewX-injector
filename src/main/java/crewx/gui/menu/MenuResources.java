package crewx.gui.menu;

import crewx.event.EventTarget;
import crewx.events.Render2DEvent;





public final class MenuResources {
    private static final long IDLE_MS = 15000L;
    private long lastCheck;

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        long now = System.currentTimeMillis();
        if (now - this.lastCheck < 2000L) return;
        this.lastCheck = now;
        MenuTextures.releaseIdle(IDLE_MS);
    }
}
