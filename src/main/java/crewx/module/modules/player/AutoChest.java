package crewx.module.modules.player;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.events.UpdateEvent;
import crewx.events.WindowClickEvent;
import crewx.module.Module;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import java.util.ArrayList;
import java.util.List;


public class AutoChest extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final BooleanProperty ironIngot = new BooleanProperty("Iron Ingot", true);
    public final BooleanProperty goldIngot = new BooleanProperty("Gold Ingot", true);
    public final BooleanProperty diamond = new BooleanProperty("Diamond", true);
    public final BooleanProperty emerald = new BooleanProperty("Emerald", true);
    public final BooleanProperty diamondSword = new BooleanProperty("Diamond Sword", false);
    public final BooleanProperty ironSword = new BooleanProperty("Iron Sword", false);
    public final BooleanProperty bow = new BooleanProperty("Bow", false);
    public final BooleanProperty enderPearl = new BooleanProperty("Ender Pearl", false);
    public final BooleanProperty snowball = new BooleanProperty("Snowball", false);
    public final BooleanProperty fireball = new BooleanProperty("Fireball", false);
    public final BooleanProperty egg = new BooleanProperty("Egg", false);
    public final BooleanProperty jumpPotion = new BooleanProperty("Jump Potion", false);
    public final BooleanProperty speedPotion = new BooleanProperty("Speed Potion", false);
    public final BooleanProperty invisPotion = new BooleanProperty("Invisibility Potion", false);
    public final BooleanProperty debug = new BooleanProperty("Debug", false);
    public final IntProperty minDelay = new IntProperty("Min Delay", 50, 0, 500);
    public final IntProperty maxDelay = new IntProperty("Max Delay", 150, 0, 1000);
    private final List<Integer> queue = new ArrayList<>();
    private boolean wasOpen = false;
    private boolean wasInChest = false;
    private long lastClick = 0;
    private int delay = 100;

    public AutoChest() {
        super("AutoChest", false);
    }

    private int getRaw(int slot, int size) {
        return slot < 9 ? size + 27 + slot : size + slot - 9;
    }

    private boolean isValid(ItemStack item) {
        if (item == null) return false;
        if (item.getItem() == Items.iron_ingot) return ironIngot.getValue();
        if (item.getItem() == Items.gold_ingot) return goldIngot.getValue();
        if (item.getItem() == Items.diamond) return diamond.getValue();
        if (item.getItem() == Items.emerald) return emerald.getValue();
        if (item.getItem() == Items.diamond_sword) return diamondSword.getValue();
        if (item.getItem() == Items.iron_sword) return ironSword.getValue();
        if (item.getItem() == Items.bow) return bow.getValue();
        if (item.getItem() == Items.ender_pearl) return enderPearl.getValue();
        if (item.getItem() == Items.snowball) return snowball.getValue();
        if (item.getItem() == Items.fire_charge) return fireball.getValue();
        if (item.getItem() == Items.egg) return egg.getValue();
        if (item.getItem() instanceof ItemPotion) {
            List<PotionEffect> effects = ((ItemPotion) item.getItem()).getEffects(item);
            if (effects != null) {
                for (PotionEffect effect : effects) {
                    int id = effect.getPotionID();
                    if (id == Potion.jump.getId() && jumpPotion.getValue()) return true;
                    if (id == Potion.moveSpeed.getId() && speedPotion.getValue()) return true;
                    if (id == Potion.invisibility.getId() && invisPotion.getValue()) return true;
                }
            }
        }
        return false;
    }

    private boolean isRealChest() {
        if (!(mc.currentScreen instanceof GuiChest)) return false;
        try {
            IInventory lower = ((ContainerChest) ((GuiChest) mc.currentScreen).inventorySlots).getLowerChestInventory();
            if (lower == null) return false;
            String name = lower.getName();
            return "Chest".equals(name) || "Large Chest".equals(name) || "Ender Chest".equals(name) || "Báu do Fim".equals(name)
                    || "container.chest".equals(name) || "container.chestDouble".equals(name) || "container.enderchest".equals(name);
        } catch (Exception e) {
            return false;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE || !isEnabled()) return;
        boolean inChest = mc.currentScreen instanceof GuiChest;
        boolean open = inChest && isRealChest();
        if (inChest && !wasInChest && debug.getValue()) {
            String lowerName = ((ContainerChest) ((GuiChest) mc.currentScreen).inventorySlots).getLowerChestInventory().getName();
            System.out.println("autochest lower name: " + lowerName);
            wasInChest = true;
        } else if (!inChest) {
            wasInChest = false;
        }
        if (open && !wasOpen) {
            queue.clear();
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (isValid(stack)) queue.add(i);
            }
        } else if (!open && wasOpen) {
            queue.clear();
        }
        wasOpen = open;
        if (!open || queue.isEmpty()) return;
        if (System.currentTimeMillis() - lastClick < delay) return;
        int slot = queue.remove(0);
        ItemStack item = mc.thePlayer.inventory.getStackInSlot(slot);
        if (item != null && isValid(item)) {
            if (mc.currentScreen instanceof GuiChest) {
                int chestSize = ((ContainerChest) ((GuiChest) mc.currentScreen).inventorySlots).getLowerChestInventory().getSizeInventory();
                int min = minDelay.getValue();
                int max = maxDelay.getValue();
                if (max <= min) max = min + 10;
                delay = min + (int) (Math.random() * (max - min));
                lastClick = System.currentTimeMillis();
                mc.playerController.windowClick(((GuiChest) mc.currentScreen).inventorySlots.windowId, getRaw(slot, chestSize), 0, 1, mc.thePlayer);
            }
        }
    }
}