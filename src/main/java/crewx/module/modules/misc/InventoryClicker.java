package crewx.module.modules.misc;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.events.TickEvent;
import crewx.mixin.IAccessorGuiScreen;
import crewx.module.Module;
import crewx.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.input.Mouse;

public class InventoryClicker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty triggerTicks = new IntProperty("ticks", 2, 0, 20);
    public int ticks;

    public InventoryClicker() {
        super("InventoryClicker", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{triggerTicks.getValue().toString() + " ticks"};
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && mc.thePlayer != null && event.getType() == EventType.PRE) {
            if (mc.currentScreen instanceof GuiContainer) {
                GuiContainer screen = ((GuiContainer) mc.currentScreen);
                final int mouseX = Mouse.getEventX() * screen.width / mc.displayWidth;
                final int mouseY = screen.height - Mouse.getEventY() * screen.height / mc.displayHeight - 1;
                if (Mouse.isButtonDown(0)) {
                    ticks++;
                    if(ticks > triggerTicks.getValue())
                    {
                        ((IAccessorGuiScreen)screen).callMouseClicked(mouseX, mouseY, 0);
                    }
                }else {
                    ticks = 0;
                }
            }
        }
    }
}