package crewx.module.modules.player;

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.events.LoadWorldEvent;
import crewx.events.TickEvent;
import crewx.module.Module;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.IntProperty;
import crewx.property.properties.ModeProperty;
import crewx.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.lwjgl.input.Keyboard;

public class AutoSoup extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long SOUP_USE_TIMEOUT_MS = 1200L;

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Automatic", "Manual"});
    public final IntProperty manualBind = new IntProperty("manual-bind", 0, 0, 255, () -> this.mode.getValue() != 0);
    public final IntProperty health = new IntProperty("health", 10, 1, 20);
    public final IntProperty soupsPerTrigger = new IntProperty("soups-per-trigger", 2, 1, 9);
    public final BooleanProperty dropSoup = new BooleanProperty("drop-soup", true);
    public final IntProperty healDelay = new IntProperty("heal-delay", 50, 1, 400);
    public final IntProperty dropDelay = new IntProperty("drop-delay", 50, 1, 400);
    public final IntProperty switchDelay = new IntProperty("switch-delay", 50, 1, 400);

    private long lastActionMs;
    private long useAttemptStartedAt;
    private int soupIndex = Integer.MIN_VALUE;
    private int originalIndex = Integer.MIN_VALUE;
    private int soupStackBeforeUse;
    private int soupsRemaining;
    private int step = 1;
    private boolean start;
    private boolean auraWasEnabled;
    private boolean useKeyPressedBySoup;

    public AutoSoup() {
        super("AutoSoup", false);
    }

    public boolean isProcessingSoup() {
        return this.start;
    }

    private int getSoupInHotbar() {
        if (mc.thePlayer == null) return Integer.MIN_VALUE;
        for (int slot = 36; slot < 45; slot++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(slot).getStack();
            if (stack != null && stack.getItem() instanceof ItemSoup) {
                return slot - 36;
            }
        }
        return Integer.MIN_VALUE;
    }

    private int getSoupStackSize(int hotbarIndex) {
        if (mc.thePlayer == null || hotbarIndex < 0 || hotbarIndex > 8) return 0;
        ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(36 + hotbarIndex).getStack();
        return stack != null && stack.getItem() instanceof ItemSoup ? stack.stackSize : 0;
    }

    private boolean currentSoupWasConsumed() {
        return this.soupStackBeforeUse > 0 && this.getSoupStackSize(this.soupIndex) < this.soupStackBeforeUse;
    }

    private boolean timeElapsed(int ms) {
        return System.currentTimeMillis() - this.lastActionMs >= ms;
    }

    private void resetTimer() {
        this.lastActionMs = System.currentTimeMillis();
    }

    @EventTarget
    public void onWorld(LoadWorldEvent event) {
        this.finishSequence();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null) {
            if (this.start) this.finishSequence();
            return;
        }
        if (mc.currentScreen != null) {
            if (this.start) this.finishSequence();
            return;
        }

        if (this.start) {
            Module killAura = CrewX.moduleManager == null ? null : CrewX.moduleManager.getModule("KillAura");
            if (this.step == 1 && killAura != null && killAura.isEnabled()) {
                this.auraWasEnabled = true;
                killAura.setEnabled(false);
            }

            if (this.step >= 2 && this.step <= 3 && mc.thePlayer.inventory.currentItem != this.soupIndex) {
                mc.thePlayer.inventory.currentItem = this.soupIndex;
            }

            switch (this.step) {
                case 1:
                    if (this.timeElapsed(this.switchDelay.getValue())) {
                        mc.thePlayer.inventory.currentItem = this.soupIndex;
                        this.resetTimer();
                        this.step++;
                    }
                    break;
                case 2:
                    if (!this.useKeyPressedBySoup) {
                        if (this.timeElapsed(this.healDelay.getValue())) {
                            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                            this.useKeyPressedBySoup = true;
                            this.useAttemptStartedAt = System.currentTimeMillis();
                        }
                    } else if (mc.gameSettings.keyBindUseItem.isKeyDown()) {
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                        this.useKeyPressedBySoup = false;
                        this.resetTimer();
                        this.step++;
                    } else {
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                        if (System.currentTimeMillis() - this.useAttemptStartedAt >= SOUP_USE_TIMEOUT_MS) {
                            this.finishSequence();
                        }
                    }
                    break;
                case 3:
                    if (!this.currentSoupWasConsumed()) {
                        if (this.timeElapsed((int) SOUP_USE_TIMEOUT_MS)) this.finishSequence();
                        break;
                    }
                    if (this.dropSoup.getValue()) {
                        if (this.timeElapsed(this.dropDelay.getValue())) {
                            C07PacketPlayerDigging.Action action = GuiScreen.isCtrlKeyDown()
                                    ? C07PacketPlayerDigging.Action.DROP_ALL_ITEMS
                                    : C07PacketPlayerDigging.Action.DROP_ITEM;
                            if (mc.getNetHandler() == null) {
                                this.finishSequence();
                                break;
                            }
                            mc.getNetHandler().getNetworkManager().sendPacket(
                                    new C07PacketPlayerDigging(action, BlockPos.ORIGIN, EnumFacing.DOWN));
                            this.resetTimer();
                            this.step++;
                        }
                    } else {
                        this.resetTimer();
                        this.step++;
                    }
                    break;
                case 4:
                    if (this.timeElapsed(this.switchDelay.getValue())) {
                        this.restoreOriginalSlot();
                        this.resetTimer();
                        this.step++;
                    }
                    break;
                case 5:
                    if (this.soupsRemaining > 1) {
                        int nextSoup = this.getSoupInHotbar();
                        if (nextSoup != Integer.MIN_VALUE) {
                            this.soupsRemaining--;
                            this.soupIndex = nextSoup;
                            this.soupStackBeforeUse = this.getSoupStackSize(nextSoup);
                            this.useKeyPressedBySoup = false;
                            this.step = 1;
                            this.resetTimer();
                            break;
                        }
                    }
                    this.finishSequence();
                    break;
                default:
                    this.finishSequence();
                    break;
            }
        } else {
            this.soupIndex = this.getSoupInHotbar();
            if (this.soupIndex != Integer.MIN_VALUE) {
                boolean auto = this.mode.getValue() == 0 && mc.thePlayer.getHealth() <= this.health.getValue();
                boolean manual = this.mode.getValue() == 1 && this.manualBind.getValue() > 0
                        && Keyboard.isKeyDown(this.manualBind.getValue());
                if (auto || manual) {
                    this.originalIndex = mc.thePlayer.inventory.currentItem;
                    this.soupsRemaining = this.soupsPerTrigger.getValue();
                    this.soupStackBeforeUse = this.getSoupStackSize(this.soupIndex);
                    this.start = true;
                    this.auraWasEnabled = false;
                    this.useKeyPressedBySoup = false;
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                    this.resetTimer();
                }
            }
        }
    }

    private void restoreOriginalSlot() {
        if (mc.thePlayer != null && this.originalIndex >= 0 && this.originalIndex < 9) {
            mc.thePlayer.inventory.currentItem = this.originalIndex;
        }
    }

    private void finishSequence() {
        if (mc.gameSettings != null) {
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        }
        this.restoreOriginalSlot();
        Module killAura = CrewX.moduleManager == null ? null : CrewX.moduleManager.getModule("KillAura");
        boolean restoreAura = this.auraWasEnabled;
        this.reset();
        if (restoreAura && killAura != null) killAura.setEnabled(true);
    }

    private void reset() {
        this.originalIndex = Integer.MIN_VALUE;
        this.soupIndex = Integer.MIN_VALUE;
        this.soupStackBeforeUse = 0;
        this.soupsRemaining = 0;
        this.start = false;
        this.auraWasEnabled = false;
        this.useKeyPressedBySoup = false;
        this.useAttemptStartedAt = 0L;
        this.step = 1;
        this.lastActionMs = 0L;
    }

    @Override
    public void onDisabled() {
        this.finishSequence();
    }
}
