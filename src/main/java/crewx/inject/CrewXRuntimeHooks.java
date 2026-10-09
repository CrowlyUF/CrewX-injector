package crewx.inject;

import crewx.CrewX;
import crewx.event.EventManager;
import crewx.event.types.EventType;
import crewx.events.AttackEvent;
import crewx.events.CancelUseEvent;
import crewx.events.HitBlockEvent;
import crewx.events.KeyEvent;
import crewx.events.LeftClickMouseEvent;
import crewx.events.LivingUpdateEvent;
import crewx.events.LoadWorldEvent;
import crewx.events.MoveInputEvent;
import crewx.events.PacketEvent;
import crewx.events.PickEvent;
import crewx.events.PlayerUpdateEvent;
import crewx.events.RaytraceEvent;
import crewx.events.Render2DEvent;
import crewx.events.Render3DEvent;
import crewx.events.RenderLivingEvent;
import crewx.events.ResizeEvent;
import crewx.events.RightClickMouseEvent;
import crewx.events.SafeWalkEvent;
import crewx.events.StrafeEvent;
import crewx.events.KnockbackEvent;
import crewx.events.TickEvent;
import crewx.events.UpdateEvent;
import crewx.events.WindowClickEvent;
import crewx.management.RotationState;
import crewx.module.Module;
import crewx.module.modules.combat.KillAura;
import crewx.module.modules.player.AntiDebuff;
import crewx.module.modules.movement.KeepSprint;
import crewx.module.modules.movement.NoSlow;
import crewx.module.modules.render.Animations;
import crewx.module.modules.render.NoHurtCam;
import crewx.module.modules.render.Accessories;
import crewx.gui.ClickGui;
import crewx.module.modules.misc.AntiObfuscate;
import crewx.module.modules.misc.NickHider;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.potion.Potion;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;










public final class CrewXRuntimeHooks {
    private static long clickGuiDrawAtFrameStart;
    private static boolean clickGuiFallbackLogged;
    private static boolean clickGuiFallbackErrorLogged;
    private static boolean itemRenderStateChecked;
    private static String lastTitleScreenClass;

    public static void onClickGuiFrameBegin() {
        clickGuiDrawAtFrameStart = ClickGui.getDrawCount();
    }


    public static void onClickGuiRenderFallback(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof ClickGui)
                || ClickGui.getDrawCount() != clickGuiDrawAtFrameStart) return;
        try {
            ScaledResolution scale = new ScaledResolution(mc);
            int mouseX = Mouse.getX() * scale.getScaledWidth() / Math.max(1, Display.getWidth());
            int mouseY = scale.getScaledHeight()
                    - Mouse.getY() * scale.getScaledHeight() / Math.max(1, Display.getHeight()) - 1;
            mc.entityRenderer.setupOverlayRendering();
            mc.currentScreen.drawScreen(mouseX, mouseY, partialTicks);
            if (!clickGuiFallbackLogged) {
                clickGuiFallbackLogged = true;
                CrewXBootstrap.log("ClickGui rendered by Badlion frame fallback");
            }
        } catch (Throwable error) {
            if (!clickGuiFallbackErrorLogged) {
                clickGuiFallbackErrorLogged = true;
                CrewXBootstrap.log("ClickGui frame fallback FAILED: "
                        + CrewXBootstrap.describeFailure(error));
            }
        }
    }

    private static volatile boolean initialized;
    private static volatile boolean initializationFailed;
    private static volatile long tickCounter;
    private static volatile long tickPreCount;
    private static volatile long keyCount;
    private static volatile boolean firstTickSeen;
    private static volatile boolean firstFrameSeen;
    private static volatile boolean rshiftWasDown;
    private static volatile boolean rshiftDispatchedThisTick;
    private static volatile long render2dCount;
    private static volatile long attackCalls;
    private static volatile long c02Packets;
    private static volatile boolean firstClientPacketReceived;
    private static volatile boolean packetReceiveFailureLogged;
    private static volatile boolean safeWalkFailureLogged;
    private static volatile boolean firstClientPacketSent;
    private static volatile boolean firstClientPacketSent2;
    private static volatile boolean firstLagPacketQueued;
    private static volatile boolean packetSendFailureLogged;
    private static int lagSnapshots;
    private static int positionCorrectionSnapshots;








    private static final Map<EntityPlayerSP, float[]> ROTATION =
            new WeakHashMap<EntityPlayerSP, float[]>();

    private static volatile Field lastReportedYawField;
    private static volatile Field lastReportedPitchField;
    private static volatile Field leftClickCounterField;
    private static final boolean[] mouseButtons = new boolean[32];
    private static final boolean[] keyboardButtons = new boolean[256];
    private static final long[] keyboardDispatchTick = new long[256];
    private static volatile Field itemToRenderField;
    private static volatile Field equippedProgressField;
    private static volatile Field prevEquippedProgressField;
    private static volatile Method rotateAroundMethod;
    private static volatile Method lightMapMethod;
    private static volatile Method playerRotationsMethod;
    private static volatile Field buttonListField;

    private CrewXRuntimeHooks() {
    }

    private static Field gameField(Class<?> type, String namedOwner, String name)
            throws NoSuchFieldException {
        return type.getDeclaredField(NotchMappings.fieldName(namedOwner, name));
    }

    private static Method gameMethod(Class<?> type, String namedOwner, String name,
            String descriptor, Class<?>... parameters) throws NoSuchMethodException {
        return type.getDeclaredMethod(NotchMappings.methodName(namedOwner, name, descriptor),
                parameters);
    }












    private static void ensureInitialized() {
        if (initialized || initializationFailed) {
            return;
        }


        try {
            if (crewx.CrewX.initializationComplete) {
                initialized = true;
                return;
            }
        } catch (Throwable ignored) {

        }
        long tick = ++tickCounter;

        if (tick % 20 != 1) {
            return;
        }
        try {
            new crewx.CrewX();
            initialized = true;
            CrewXBootstrap.log("CrewX initialized on client tick " + tick);
            listEnabledModules();
        } catch (Throwable error) {
            CrewXBootstrap.log("deferred init attempt " + tick + " FAILED: " + error);
            java.io.StringWriter detail = new java.io.StringWriter();
            error.printStackTrace(new java.io.PrintWriter(detail));
            CrewXBootstrap.log(detail.toString());


            initializationFailed = crewx.CrewX.moduleManager != null;
        }
    }


    public static boolean initializeOnClientThread() {
        ensureInitialized();
        return initialized;
    }

    private static void listEnabledModules() {
        try {
            if (crewx.CrewX.moduleManager == null) {
                return;
            }
            StringBuilder enabled = new StringBuilder("enabled-modules:");
            for (Object module : crewx.CrewX.moduleManager.modules.values()) {
                if (module instanceof crewx.module.Module
                        && ((crewx.module.Module) module).isEnabled()) {
                    enabled.append(' ').append(((crewx.module.Module) module).getName());
                }
            }
            CrewXBootstrap.log(enabled.toString());
        } catch (Throwable ignored) {

        }
    }

    private static float[] rotationOf(EntityPlayerSP player) {
        synchronized (ROTATION) {
            float[] state = ROTATION.get(player);
            if (state == null) {
                state = new float[]{Float.NaN, Float.NaN, Float.NaN, Float.NaN};
                ROTATION.put(player, state);
            }
            return state;
        }
    }

    private static float getLastReportedYaw(EntityPlayerSP player) {
        try {
            if (lastReportedYawField == null) {
                lastReportedYawField = gameField(EntityPlayerSP.class,
                        "net.minecraft.client.entity.EntityPlayerSP", "lastReportedYaw");
                lastReportedYawField.setAccessible(true);
            }
            return lastReportedYawField.getFloat(player);
        } catch (Throwable ignored) {
            return player.rotationYaw;
        }
    }

    private static float getLastReportedPitch(EntityPlayerSP player) {
        try {
            if (lastReportedPitchField == null) {
                lastReportedPitchField = gameField(EntityPlayerSP.class,
                        "net.minecraft.client.entity.EntityPlayerSP", "lastReportedPitch");
                lastReportedPitchField.setAccessible(true);
            }
            return lastReportedPitchField.getFloat(player);
        } catch (Throwable ignored) {
            return player.rotationPitch;
        }
    }

    private static void setLastReported(EntityPlayerSP player, float yaw, float pitch) {
        try {
            if (lastReportedYawField == null) {
                lastReportedYawField = gameField(EntityPlayerSP.class,
                        "net.minecraft.client.entity.EntityPlayerSP", "lastReportedYaw");
                lastReportedYawField.setAccessible(true);
            }
            if (lastReportedPitchField == null) {
                lastReportedPitchField = gameField(EntityPlayerSP.class,
                        "net.minecraft.client.entity.EntityPlayerSP", "lastReportedPitch");
                lastReportedPitchField.setAccessible(true);
            }
            lastReportedYawField.setFloat(player, yaw);
            lastReportedPitchField.setFloat(player, pitch);
        } catch (Throwable ignored) {

        }
    }





    public static void onGameLoopFrame() {
        if (!firstFrameSeen) {
            firstFrameSeen = true;
            CrewXBootstrap.log("first Minecraft.runGameLoop callback reached CrewX");
        }
    }

    public static void onTickPre(Object minecraft) {
        try {
            if (!firstTickSeen) {
                firstTickSeen = true;
                CrewXBootstrap.log("first Minecraft.runTick callback reached CrewX");
            }
            rshiftDispatchedThisTick = false;
            ensureInitialized();
            crewx.config.Config.flushPending();
            if (!(minecraft instanceof Minecraft)) {
                return;
            }
            Minecraft mc = (Minecraft) minecraft;
            if (mc.theWorld == null) {
                String screenClass = mc.currentScreen == null
                        ? "<none>" : mc.currentScreen.getClass().getName();
                if (!screenClass.equals(lastTitleScreenClass)) {
                    lastTitleScreenClass = screenClass;
                    CrewXBootstrap.log("title screen class: " + screenClass);
                }
                if (mc.currentScreen != null
                        && !(mc.currentScreen instanceof crewx.gui.CrewXMainMenu)
                        && (screenClass.toLowerCase(java.util.Locale.ROOT).contains("mainmenu")
                                || "net.badlion.a.aPU".equals(screenClass))) {
                    mc.displayGuiScreen(new crewx.gui.CrewXMainMenu());
                    CrewXBootstrap.log("CrewX main menu replaced " + screenClass);
                }
            }
            pollMouseBinds(mc);
            if (mc.theWorld == null || mc.thePlayer == null) {
                return;
            }
            tickPreCount++;
            if (Boolean.getBoolean("crewx.audit") && tickPreCount % 600 == 1) {
                diagnose();
            }
            EventManager.call(new TickEvent(EventType.PRE));
        } catch (Throwable ignored) {

        }
    }

    public static void onTickPost(Object minecraft) {
        try {
            if (!(minecraft instanceof Minecraft)) {
                return;
            }
            Minecraft mc = (Minecraft) minecraft;
            boolean rshiftDown = org.lwjgl.input.Keyboard.isKeyDown(
                    org.lwjgl.input.Keyboard.KEY_RSHIFT);
            if (rshiftDown && !rshiftWasDown && !rshiftDispatchedThisTick
                    && mc.currentScreen == null) {
                CrewXBootstrap.log("RSHIFT physical fallback dispatched");
                onKey(org.lwjgl.input.Keyboard.KEY_RSHIFT, true);
            }
            rshiftWasDown = rshiftDown;
            pollKeyboardBinds(mc);
            if (mc.theWorld == null || mc.thePlayer == null) {
                return;
            }
            EventManager.call(new TickEvent(EventType.POST));
            if (tickPreCount > 0 && tickPreCount % 200 == 0 && lagSnapshots < 8
                    && CrewX.lagManager != null && CrewX.moduleManager != null) {
                lagSnapshots++;
                Module fakeLag = (Module) CrewX.moduleManager.modules.get(
                        crewx.module.modules.combat.Fakelag.class);
                Module lagRange = (Module) CrewX.moduleManager.modules.get(
                        crewx.module.modules.combat.LagRange.class);
                Module backtrack = (Module) CrewX.moduleManager.modules.get(
                        crewx.module.modules.combat.Backtrack.class);
                CrewXBootstrap.log("lag state: fake=" + (fakeLag != null && fakeLag.isEnabled())
                        + " range=" + (lagRange != null && lagRange.isEnabled())
                        + " backtrack=" + (backtrack != null && backtrack.isEnabled())
                        + " delayTicks=" + CrewX.lagManager.getTickDelay()
                        + " queued=" + CrewX.lagManager.packetQueue.size()
                        + " sentHook=" + firstClientPacketSent
                        + " sent2Hook=" + firstClientPacketSent2);
            }
        } catch (Throwable ignored) {

        }
    }

    public static void onKey(int keyCode, boolean pressed) {
        try {
            int mouseButton = keyCode + 100;
            if (keyCode < 0 && mouseButton >= 0 && mouseButton < mouseButtons.length) {
                if (!pressed) {
                    mouseButtons[mouseButton] = false;
                    return;
                }
                if (mouseButtons[mouseButton]) return;
                mouseButtons[mouseButton] = true;
            }
            if (keyCode > 0 && keyCode < keyboardButtons.length) {
                if (!pressed) {
                    keyboardButtons[keyCode] = false;
                    return;
                }
                if (keyboardButtons[keyCode]) {
                    return;
                }
                keyboardButtons[keyCode] = true;
                keyboardDispatchTick[keyCode] = tickPreCount;
            }
            if (keyCode == org.lwjgl.input.Keyboard.KEY_RSHIFT && pressed) {
                rshiftDispatchedThisTick = true;
                CrewXBootstrap.log("RSHIFT key event received; initialized=" + initialized);
            }
            if (!pressed) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.currentScreen != null) {
                return;
            }
            EventManager.call(new KeyEvent(keyCode));
            keyCount++;
        } catch (Throwable ignored) {

        }
    }







    public static void onKeyDispatch(int keyCode, boolean pressed) {
        try {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(keyCode, pressed);
        } catch (Throwable ignored) {

        }
        onKey(keyCode, pressed);
    }

    private static void pollMouseBinds(Minecraft mc) {
        try {
            int count = Math.min(Mouse.getButtonCount(), mouseButtons.length);
            for (int button = 0; button < count; button++) {
                boolean down = Mouse.isButtonDown(button);
                if (down && !mouseButtons[button] && mc.currentScreen == null) {
                    CrewXAudit.mark("mouse-bind-" + button);
                    onKey(button - 100, true);
                }
                mouseButtons[button] = down;
            }
        } catch (Throwable error) {
            CrewXBootstrap.log("mouse bind polling FAILED: " + CrewXBootstrap.describeFailure(error));
        }
    }


    private static void pollKeyboardBinds(Minecraft mc) {
        try {
            if (CrewX.moduleManager == null) return;
            for (Object value : CrewX.moduleManager.modules.values()) {
                if (!(value instanceof Module)) continue;
                int key = ((Module) value).getKey();
                if (key <= 0 || key >= keyboardButtons.length
                        || key == org.lwjgl.input.Keyboard.KEY_RSHIFT) continue;
                boolean down = org.lwjgl.input.Keyboard.isKeyDown(key);
                if (down && !keyboardButtons[key] && mc.currentScreen == null
                        && keyboardDispatchTick[key] != tickPreCount) {
                    CrewXBootstrap.log("keyboard bind fallback: " + key);
                    onKey(key, true);
                }
                keyboardButtons[key] = down;
            }
        } catch (Throwable error) {
            CrewXBootstrap.log("keyboard bind polling FAILED: "
                    + CrewXBootstrap.describeFailure(error));
        }
    }





    public static void onChangeCurrentItem(Object inventory, int slot) {
        try {
            if (!(inventory instanceof net.minecraft.entity.player.InventoryPlayer)) {
                return;
            }
            crewx.events.SwapItemEvent event =
                    new crewx.events.SwapItemEvent(-1, slot);
            EventManager.call(event);
            if (!event.isCancelled()) {
                ((net.minecraft.entity.player.InventoryPlayer) inventory).changeCurrentItem(slot);
            }
        } catch (Throwable ignored) {

        }
    }

    public static void onRenderOverlay(float partialTicks) {
        org.lwjgl.opengl.GL11.glPushMatrix();
        try {
            render2dCount++;
            EventManager.call(new Render2DEvent(partialTicks));
        } catch (Throwable ignored) {

        } finally {
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
    }

    public static void onBeginOverlayFrame() {
        try {
            crewx.gui.BackdropBlur.beginFrame();
        } catch (Throwable ignored) {

        }
    }

    public static net.minecraft.item.ItemStack onHudCurrentItem(
            net.minecraft.entity.player.InventoryPlayer inventory) {
        try {
            if (crewx.CrewX.moduleManager != null) {
                crewx.module.modules.player.Scaffold scaffold =
                        (crewx.module.modules.player.Scaffold) crewx.CrewX.moduleManager.modules.get(
                                crewx.module.modules.player.Scaffold.class);
                if (scaffold != null && scaffold.isEnabled() && scaffold.itemSpoof.getValue()) {
                    int slot = scaffold.getSlot();
                    if (slot >= 0) return inventory.getStackInSlot(slot);
                }
                crewx.module.modules.player.AutoBlockIn autoBlockIn =
                        (crewx.module.modules.player.AutoBlockIn) crewx.CrewX.moduleManager.modules.get(
                                crewx.module.modules.player.AutoBlockIn.class);
                if (autoBlockIn != null && autoBlockIn.isEnabled() && autoBlockIn.itemSpoof.getValue()) {
                    int slot = autoBlockIn.getSlot();
                    if (slot >= 0) return inventory.getStackInSlot(slot);
                }
            }
        } catch (Throwable ignored) {

        }
        return inventory.getCurrentItem();
    }

    private static final ThreadLocal<net.minecraft.scoreboard.ScoreObjective> SCORE_OBJECTIVE =
            new ThreadLocal<net.minecraft.scoreboard.ScoreObjective>();
    private static final ThreadLocal<Boolean> SCORE_PANEL_DRAWN = new ThreadLocal<Boolean>();

    public static void onScoreboardBegin(net.minecraft.scoreboard.ScoreObjective objective) {
        SCORE_OBJECTIVE.set(objective);
        SCORE_PANEL_DRAWN.set(Boolean.FALSE);
        crewx.gui.ScoreboardPosition.clearBounds();
    }

    public static int onScoreboardText(net.minecraft.client.gui.FontRenderer font, String text,
            int x, int y, int color) {
        return font.drawString(text, x + crewx.gui.ScoreboardPosition.getOffsetX(),
                y + crewx.gui.ScoreboardPosition.getOffsetY(), color);
    }

    public static void onScoreboardBackground(int left, int top, int right, int bottom, int color) {
        if (Boolean.TRUE.equals(SCORE_PANEL_DRAWN.get())) return;
        SCORE_PANEL_DRAWN.set(Boolean.TRUE);
        try {
            net.minecraft.scoreboard.ScoreObjective objective = SCORE_OBJECTIVE.get();
            if (objective == null) return;
            int rows = 0;
            for (net.minecraft.scoreboard.Score score :
                    objective.getScoreboard().getSortedScores(objective)) {
                if (score != null && score.getPlayerName() != null
                        && !score.getPlayerName().startsWith("#")) rows++;
            }
            rows = Math.min(rows, 15);
            if (rows <= 0) return;
            float panelY = top - rows * Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT - 1.0F;
            float panelWidth = right - left;
            float panelHeight = bottom - panelY;
            crewx.gui.ScoreboardPosition.updateBounds(left, panelY, panelWidth, panelHeight);
            crewx.gui.BackdropBlur.drawRoundedPanel(
                    left + crewx.gui.ScoreboardPosition.getOffsetX(),
                    panelY + crewx.gui.ScoreboardPosition.getOffsetY(),
                    panelWidth, panelHeight, 3.0F, 0x900D0E10);
        } catch (Throwable ignored) {

        }
    }

    public static float onHudExperience(net.minecraft.client.entity.EntityPlayerSP player) {
        return hideExperienceLevel() ? 0.0F : player.experience;
    }

    public static int onHudExperienceLevel(net.minecraft.client.entity.EntityPlayerSP player) {
        return hideExperienceLevel() ? 0 : player.experienceLevel;
    }

    private static boolean hideExperienceLevel() {
        try {
            if (crewx.CrewX.moduleManager == null) return false;
            NickHider module = (NickHider) crewx.CrewX.moduleManager.modules.get(NickHider.class);
            return module != null && module.isEnabled() && module.level.getValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean onChatMouseClicked(int mouseX, int mouseY, int button) {
        try {
            return crewx.gui.BindViewer.beginDrag(mouseX, mouseY, button)
                    || crewx.gui.ScoreboardPosition.beginDrag(mouseX, mouseY, button)
                    || crewx.gui.ChatPosition.beginDrag(mouseX, mouseY, button);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void onChatDrawScreen() {
        try {
            crewx.gui.ChatPosition.renderHandle();
            if (crewx.CrewX.moduleManager != null) {
                Object module = crewx.CrewX.moduleManager.modules.get(crewx.module.modules.render.HUD.class);
                if (module instanceof crewx.module.modules.render.HUD) {
                    crewx.gui.BindViewer.render((crewx.module.modules.render.HUD) module);
                }
            }
        } catch (Throwable ignored) {

        }
    }

    public static void onChatClosed() {
        crewx.gui.ChatPosition.endDrag();
        crewx.gui.ScoreboardPosition.endDrag();
        crewx.gui.BindViewer.endDrag();
    }

    public static boolean onChatMouseDragged(int mouseX, int mouseY) {
        try {
            if (!(Minecraft.getMinecraft().currentScreen instanceof net.minecraft.client.gui.GuiChat)) return false;
            if (crewx.gui.BindViewer.isDragging()) {
                crewx.gui.BindViewer.dragTo(mouseX, mouseY);
            } else if (crewx.gui.ScoreboardPosition.isDragging()) {
                crewx.gui.ScoreboardPosition.dragTo(mouseX, mouseY);
            } else if (crewx.gui.ChatPosition.isDragging()) {
                crewx.gui.ChatPosition.dragTo(mouseX, mouseY);
            } else {
                return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean onChatMouseReleased(int button) {
        if (!(Minecraft.getMinecraft().currentScreen instanceof net.minecraft.client.gui.GuiChat)) return false;
        if (button == 0 && crewx.gui.BindViewer.isDragging()) {
            crewx.gui.BindViewer.endDrag();
        } else if (button == 1 && crewx.gui.ScoreboardPosition.isDragging()) {
            crewx.gui.ScoreboardPosition.endDrag();
        } else if (button == 0 && crewx.gui.ChatPosition.isDragging()) {
            crewx.gui.ChatPosition.endDrag();
        } else {
            return false;
        }
        return true;
    }

    public static void onTextFieldDrawRect(int left, int top, int right, int bottom, int color) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen instanceof net.minecraft.client.gui.GuiChat
                    && right - left >= 100 && bottom - top >= 9) {
                int alpha = color == -16777216 ? 0xA6000000 : 0xB8141414;
                crewx.gui.BackdropBlur.drawRoundedPanel(left, top, right - left, bottom - top,
                        4.0F, alpha | (color & 0x00FFFFFF));
            } else {
                net.minecraft.client.gui.Gui.drawRect(left, top, right, bottom, color);
            }
        } catch (Throwable ignored) {
            net.minecraft.client.gui.Gui.drawRect(left, top, right, bottom, color);
        }
    }

    public static boolean onDefaultBackground(Object screenObject) {
        if (!(Minecraft.getMinecraft().currentScreen instanceof net.minecraft.client.gui.GuiMainMenu)
                || !(screenObject instanceof GuiScreen)) return false;
        GuiScreen screen = (GuiScreen) screenObject;
        crewx.gui.CrewXTheme.drawScreenBackground(screen.width, screen.height);
        return true;
    }

    private static volatile Field drawnChatLinesField;
    private static volatile Field chatScrollField;

    @SuppressWarnings("unchecked")
    private static java.util.List<net.minecraft.client.gui.ChatLine> chatLines(
            net.minecraft.client.gui.GuiNewChat chat) throws Exception {
        if (drawnChatLinesField == null) {
            try {
                drawnChatLinesField = gameField(net.minecraft.client.gui.GuiNewChat.class,
                        "net.minecraft.client.gui.GuiNewChat", "drawnChatLines");
            } catch (NoSuchFieldException missing) {
                drawnChatLinesField = net.minecraft.client.gui.GuiNewChat.class.getDeclaredField("field_146253_i");
            }
            drawnChatLinesField.setAccessible(true);
        }
        return (java.util.List<net.minecraft.client.gui.ChatLine>) drawnChatLinesField.get(chat);
    }

    private static int chatScroll(net.minecraft.client.gui.GuiNewChat chat) throws Exception {
        if (chatScrollField == null) {
            try {
                chatScrollField = gameField(net.minecraft.client.gui.GuiNewChat.class,
                        "net.minecraft.client.gui.GuiNewChat", "scrollPos");
            } catch (NoSuchFieldException missing) {
                chatScrollField = net.minecraft.client.gui.GuiNewChat.class.getDeclaredField("field_146250_j");
            }
            chatScrollField.setAccessible(true);
        }
        return chatScrollField.getInt(chat);
    }

    public static void onChatTranslate(float x, float y, float z) {
        try {
            net.minecraft.client.gui.GuiNewChat chat = Minecraft.getMinecraft().ingameGUI.getChatGUI();
            float scale = Math.max(0.01F, chat.getChatScale());
            GlStateManager.translate(x + crewx.gui.ChatPosition.getOffsetX() / scale,
                    y + crewx.gui.ChatPosition.getOffsetY() / scale, z);
        } catch (Throwable ignored) {
            GlStateManager.translate(x, y, z);
        }
    }

    public static void onChatLineBackground(int left, int top, int right, int bottom, int color) {

    }

    public static void onChatPanel(net.minecraft.client.gui.GuiNewChat chat, int updateCounter) {
        try {
            java.util.List<net.minecraft.client.gui.ChatLine> lines = chatLines(chat);
            if (lines == null || lines.isEmpty()) return;
            int start = Math.max(0, chatScroll(chat));
            int end = Math.min(lines.size(), start + Math.max(0, chat.getLineCount()));
            int visible = 0;
            for (int index = start; index < end; index++) {
                net.minecraft.client.gui.ChatLine line = lines.get(index);
                if (line == null) break;
                int age = updateCounter - line.getUpdatedCounter();
                if (chat.getChatOpen() || age >= 0 && age < 200) visible++;
                else break;
            }
            if (visible == 0) return;
            float scale = Math.max(0.01F, chat.getChatScale());
            float panelWidth = (float) Math.ceil(chat.getChatWidth() / scale) + 8.0F;
            float panelHeight = visible * 9.0F;
            int alpha;
            if (chat.getChatOpen()) {
                alpha = 174;
            } else {
                net.minecraft.client.gui.ChatLine newest = lines.get(Math.min(lines.size() - 1, start));
                if (newest == null) return;
                double fade = Math.max(0.0D, Math.min(1.0D,
                        1.0D - (updateCounter - newest.getUpdatedCounter()) / 200.0D));
                float opacity = Minecraft.getMinecraft().gameSettings.chatOpacity * 0.9F + 0.1F;
                alpha = Math.max(0, Math.min(220, (int) (174.0D * fade * fade * opacity)));
            }
            crewx.gui.BackdropBlur.drawRoundedPanel(-2.0F, -panelHeight - 3.0F,
                    panelWidth, panelHeight + 6.0F, 3.0F, alpha << 24 | 0x000C0D0F);
        } catch (Throwable ignored) {

        }
    }

    public static int onChatDrawString(net.minecraft.client.gui.FontRenderer font, String text,
            float x, float y, int color, net.minecraft.client.gui.GuiNewChat chat, int updateCounter) {
        try {
            java.util.List<net.minecraft.client.gui.ChatLine> lines = chatLines(chat);
            int start = Math.max(0, chatScroll(chat));
            int end = Math.min(lines.size(), start + chat.getLineCount());
            for (int index = start; index < end; index++) {
                net.minecraft.client.gui.ChatLine line = lines.get(index);
                if (line == null || text == null
                        || !text.equals(line.getChatComponent().getFormattedText())) continue;
                y += crewx.gui.ChatMessageAnimator.getRiseOffset(line, updateCounter);
                break;
            }
        } catch (Throwable ignored) {

        }
        return font.drawStringWithShadow(text, x, y, color);
    }

    public static boolean onSpawnMobPacket(Object packetObject) {
        try {
            if (!(packetObject instanceof net.minecraft.network.play.server.S0FPacketSpawnMob)) return true;
            if (Minecraft.getMinecraft().theWorld == null) return true;
            net.minecraft.network.play.server.S0FPacketSpawnMob packet =
                    (net.minecraft.network.play.server.S0FPacketSpawnMob) packetObject;
            int type = packet.getEntityType();
            return type < 0 || net.minecraft.entity.EntityList.getClassFromID(type) == null
                    || packet.getEntityID() == 0;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static boolean shouldOverrideChunkVisibility() {
        try {
            if (crewx.CrewX.moduleManager == null) return false;
            return crewx.CrewX.moduleManager.modules.get(crewx.module.modules.render.Chams.class).isEnabled()
                    || crewx.CrewX.moduleManager.modules.get(crewx.module.modules.render.ViewClip.class).isEnabled()
                    || crewx.CrewX.moduleManager.modules.get(crewx.module.modules.render.Xray.class).isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static net.minecraft.client.renderer.chunk.SetVisibility overrideChunkVisibility() {
        if (!shouldOverrideChunkVisibility()) return null;
        net.minecraft.client.renderer.chunk.SetVisibility result =
                new net.minecraft.client.renderer.chunk.SetVisibility();
        result.setAllVisible(true);
        return result;
    }

    public static int adjustWorldVertexColor(int color) {
        try {
            if (crewx.CrewX.moduleManager == null) return color;
            crewx.module.modules.render.Xray xray =
                    (crewx.module.modules.render.Xray) crewx.CrewX.moduleManager.modules.get(
                            crewx.module.modules.render.Xray.class);
            if (xray == null || !xray.isEnabled()) return color;
            int alpha = (int) ((float) xray.opacity.getValue().intValue() * 255.0F / 100.0F);
            return color & 0x00FFFFFF | alpha << 24;
        } catch (Throwable ignored) {
            return color;
        }
    }

    public static boolean onKeyBindingPressed(boolean pressed, Object bindingObject) {
        if (!pressed || !(bindingObject instanceof net.minecraft.client.settings.KeyBinding)) return pressed;
        try {
            String description = ((net.minecraft.client.settings.KeyBinding) bindingObject).getKeyDescription();
            net.minecraft.client.settings.KeyBinding[] hotbar = Minecraft.getMinecraft().gameSettings.keyBindsHotbar;
            for (int i = 0; i < hotbar.length; i++) {
                if (!hotbar[i].getKeyDescription().equals(description)) continue;
                crewx.events.SwapItemEvent event = new crewx.events.SwapItemEvent(i, 0);
                EventManager.call(event);
                if (event.isCancelled()) return false;
            }
        } catch (Throwable ignored) {

        }
        return true;
    }

    public static boolean suppressItemEffect() {
        try {
            if (crewx.CrewX.moduleManager == null) return false;
            crewx.module.modules.render.ESP esp =
                    (crewx.module.modules.render.ESP) crewx.CrewX.moduleManager.modules.get(
                            crewx.module.modules.render.ESP.class);
            return esp != null && esp.isEnabled() && !esp.isGlowEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Boolean onBlockRenderModel(Object rendererObject,
            net.minecraft.world.IBlockAccess access,
            net.minecraft.client.resources.model.IBakedModel model,
            net.minecraft.block.state.IBlockState state,
            net.minecraft.util.BlockPos position,
            net.minecraft.client.renderer.WorldRenderer worldRenderer,
            boolean checkSides) {
        try {
            if (crewx.CrewX.moduleManager == null) return null;
            crewx.module.modules.render.Xray xray =
                    (crewx.module.modules.render.Xray) crewx.CrewX.moduleManager.modules.get(
                            crewx.module.modules.render.Xray.class);
            if (xray == null || !xray.isEnabled()) return null;
            return ((net.minecraft.client.renderer.BlockModelRenderer) rendererObject)
                    .renderModelAmbientOcclusion(access, model, state.getBlock(), position,
                            worldRenderer, checkSides);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final ThreadLocal<float[]> RENDER_PLAYER_ROTATION = new ThreadLocal<float[]>();
    private static final ThreadLocal<net.minecraft.client.entity.EntityPlayerSP> RENDER_PLAYER =
            new ThreadLocal<net.minecraft.client.entity.EntityPlayerSP>();

    public static void onRenderManagerEntityPre(Object entityObject) {
        if (!(entityObject instanceof net.minecraft.client.entity.EntityPlayerSP)
                || !crewx.management.RotationState.isRotated(1)) return;
        net.minecraft.client.entity.EntityPlayerSP player =
                (net.minecraft.client.entity.EntityPlayerSP) entityObject;
        RENDER_PLAYER.set(player);
        float[] saved = RENDER_PLAYER_ROTATION.get();
        if (saved == null) {
            saved = new float[6];
            RENDER_PLAYER_ROTATION.set(saved);
        }
        saved[0] = player.prevRenderYawOffset;
        saved[1] = player.renderYawOffset;
        saved[2] = player.prevRotationYawHead;
        saved[3] = player.rotationYawHead;
        saved[4] = player.prevRotationPitch;
        saved[5] = player.rotationPitch;
        player.prevRenderYawOffset = crewx.management.RotationState.getPrevRenderYawOffset();
        player.renderYawOffset = crewx.management.RotationState.getRenderYawOffset();
        player.prevRotationYawHead = crewx.management.RotationState.getPrevRotationYawHead();
        player.rotationYawHead = crewx.management.RotationState.getRotationYawHead();
        player.prevRotationPitch = crewx.management.RotationState.getPrevRotationPitch();
        player.rotationPitch = crewx.management.RotationState.getRotationPitch();
    }

    public static void onRenderManagerEntityPost(Object entityObject) {
        if (entityObject != RENDER_PLAYER.get()) return;
        float[] saved = RENDER_PLAYER_ROTATION.get();
        RENDER_PLAYER.remove();
        if (saved == null) return;
        net.minecraft.client.entity.EntityPlayerSP player =
                (net.minecraft.client.entity.EntityPlayerSP) entityObject;
        player.prevRenderYawOffset = saved[0];
        player.renderYawOffset = saved[1];
        player.prevRotationYawHead = saved[2];
        player.rotationYawHead = saved[3];
        player.prevRotationPitch = saved[4];
        player.rotationPitch = saved[5];
    }

    private static final ThreadLocal<float[]> FREELOOK_CAMERA_SAVED = new ThreadLocal<float[]>();
    private static final ThreadLocal<EntityPlayerSP> FREELOOK_CAMERA_PLAYER =
            new ThreadLocal<EntityPlayerSP>();

    private static crewx.module.modules.render.FreeLook activeFreeLook() {
        if (CrewX.moduleManager == null) return null;
        crewx.module.modules.render.FreeLook module =
                (crewx.module.modules.render.FreeLook) CrewX.moduleManager.modules.get(
                        crewx.module.modules.render.FreeLook.class);
        return module != null && module.isEnabled() ? module : null;
    }

    public static void onFreeLookFramePrepare() {
        try {
            crewx.module.modules.render.FreeLook module = activeFreeLook();
            if (module != null) module.prepare(Minecraft.getMinecraft().thePlayer);
        } catch (Throwable ignored) {

        }
    }

    public static void onFreeLookMouse(EntityPlayerSP player, float yawDelta, float pitchDelta) {
        try {
            crewx.module.modules.render.FreeLook module = activeFreeLook();
            if (module != null && Minecraft.getMinecraft().gameSettings.thirdPersonView != 0) {
                module.applyMouseDelta(player, yawDelta, pitchDelta);
                return;
            }
        } catch (Throwable ignored) {

        }
        player.setAngles(yawDelta, pitchDelta);
    }

    public static void onFreeLookCameraPre() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayerSP player = mc.thePlayer;
            crewx.module.modules.render.FreeLook module = activeFreeLook();
            if (player == null || module == null || mc.gameSettings.thirdPersonView == 0
                    || FREELOOK_CAMERA_PLAYER.get() != null) return;
            module.prepare(player);
            float[] saved = FREELOOK_CAMERA_SAVED.get();
            if (saved == null) {
                saved = new float[4];
                FREELOOK_CAMERA_SAVED.set(saved);
            }
            saved[0] = player.rotationYaw;
            saved[1] = player.prevRotationYaw;
            saved[2] = player.rotationPitch;
            saved[3] = player.prevRotationPitch;
            FREELOOK_CAMERA_PLAYER.set(player);
            player.rotationYaw = module.getCameraYaw();
            player.prevRotationYaw = module.getCameraYaw();
            player.rotationPitch = module.getCameraPitch();
            player.prevRotationPitch = module.getCameraPitch();
        } catch (Throwable ignored) {

        }
    }

    public static void onFreeLookCameraPost() {
        EntityPlayerSP player = FREELOOK_CAMERA_PLAYER.get();
        float[] saved = FREELOOK_CAMERA_SAVED.get();
        FREELOOK_CAMERA_PLAYER.remove();
        if (player == null || saved == null) return;
        player.rotationYaw = saved[0];
        player.prevRotationYaw = saved[1];
        player.rotationPitch = saved[2];
        player.prevRotationPitch = saved[3];
    }





    public static void onRenderWorld(float partialTicks) {
        org.lwjgl.opengl.GL11.glPushMatrix();
        try {
            crewx.util.RenderUtil.updateFrustum();
            EventManager.call(new Render3DEvent(partialTicks));
        } catch (Throwable ignored) {

        } finally {
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
    }

    public static double onPickRange(double original) {
        try {
            crewx.events.PickEvent event = new crewx.events.PickEvent(original);
            EventManager.call(event);
            return event.getRange();
        } catch (Throwable ignored) {
            return original;
        }
    }

    public static double onRaytraceRange(double original) {
        try {
            crewx.events.RaytraceEvent event = new crewx.events.RaytraceEvent(original);
            EventManager.call(event);
            return event.getRange();
        } catch (Throwable ignored) {
            return original;
        }
    }

    public static void onRenderLivingPre(Object entity) {
        try {
            if (entity instanceof EntityLivingBase) {
                EventManager.call(new RenderLivingEvent(EventType.PRE,
                        (EntityLivingBase) entity));
                if (entity instanceof net.minecraft.entity.player.EntityPlayer) {
                    net.minecraft.client.renderer.entity.Render<?> renderer =
                            Minecraft.getMinecraft().getRenderManager().getEntityRenderObject((Entity) entity);
                    if (renderer instanceof net.minecraft.client.renderer.entity.RenderPlayer) {
                        crewx.cosmetics.CosmeticRenderHandler.INSTANCE.onPlayerRenderPre(
                                (net.minecraft.client.renderer.entity.RenderPlayer) renderer);
                    }
                }
            }
        } catch (Throwable ignored) {

        }
    }

    public static void onRenderLivingPost(Object entity) {
        try {
            if (entity instanceof EntityLivingBase) {
                EventManager.call(new RenderLivingEvent(EventType.POST,
                        (EntityLivingBase) entity));
            }
        } catch (Throwable ignored) {

        }
    }


    public static boolean suppressVanillaName(Object entity) {
        try {
            if (!(entity instanceof EntityLivingBase) || CrewX.moduleManager == null) return false;
            EntityLivingBase living = (EntityLivingBase) entity;
            crewx.module.modules.render.NameTags tags =
                    (crewx.module.modules.render.NameTags) CrewX.moduleManager.modules.get(
                            crewx.module.modules.render.NameTags.class);
            if (tags != null && tags.isEnabled() && tags.shouldRenderTags(living)) return true;
            crewx.module.modules.render.ESP esp =
                    (crewx.module.modules.render.ESP) CrewX.moduleManager.modules.get(
                            crewx.module.modules.render.ESP.class);
            return esp != null && esp.isEnabled() && !esp.isOutlineEnabled();
        } catch (Throwable error) {
            return false;
        }
    }





    public static boolean onSafeWalkCheck(Object entity, boolean flag) {
        try {
            if (!(entity instanceof EntityPlayerSP)) {
                return flag;
            }
            SafeWalkEvent event = new SafeWalkEvent(flag);
            EventManager.call(event);
            return event.isSafeWalk();
        } catch (Throwable ignored) {
            return flag;
        }
    }

    public static double[] onSafeWalkMotion(Object entity, double x, double y, double z) {
        double[] motion = new double[]{x, y, z};
        try {
            if (!(entity instanceof EntityPlayerSP)) {
                return motion;
            }
            EntityPlayerSP player = (EntityPlayerSP) entity;
            CrewXAudit.mark("safewalk-event");


            SafeWalkEvent event = new SafeWalkEvent(((Entity) player).onGround && player.isSneaking());
            EventManager.call(event);
            if (!event.isSafeWalk() || player.worldObj == null) {
                return motion;
            }
            CrewXAudit.mark("safewalk-active");
            final double step = 0.05D;
            while (x != 0.0D && player.worldObj.getCollidingBoundingBoxes(player,
                    player.getEntityBoundingBox().offset(x, -1.0D, 0.0D)).isEmpty()) {
                x = Math.abs(x) < step ? 0.0D : x + (x > 0.0D ? -step : step);
            }
            while (z != 0.0D && player.worldObj.getCollidingBoundingBoxes(player,
                    player.getEntityBoundingBox().offset(0.0D, -1.0D, z)).isEmpty()) {
                z = Math.abs(z) < step ? 0.0D : z + (z > 0.0D ? -step : step);
            }
            while (x != 0.0D && z != 0.0D && player.worldObj.getCollidingBoundingBoxes(player,
                    player.getEntityBoundingBox().offset(x, -1.0D, z)).isEmpty()) {
                x = Math.abs(x) < step ? 0.0D : x + (x > 0.0D ? -step : step);
                z = Math.abs(z) < step ? 0.0D : z + (z > 0.0D ? -step : step);
            }
            motion[0] = x;
            motion[2] = z;
        } catch (Throwable error) {
            if (!safeWalkFailureLogged) {
                safeWalkFailureLogged = true;
                CrewXBootstrap.log("SafeWalk runtime FAILED: " + CrewXBootstrap.describeFailure(error));
            }
        }
        return motion;
    }


    public static boolean forceChamsRenderRange(Object entity, double distanceSquared) {
        try {
            if (!(entity instanceof net.minecraft.entity.player.EntityPlayer)
                    || distanceSquared > 512.0D * 512.0D
                    || CrewX.moduleManager == null) {
                return false;
            }
            crewx.module.modules.render.Chams chams =
                    (crewx.module.modules.render.Chams) CrewX.moduleManager.modules.get(
                            crewx.module.modules.render.Chams.class);
            return chams != null && chams.isEnabled()
                    && chams.shouldRenderChams((EntityLivingBase) entity);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean onRenderItemFirstPerson(Object rendererObject, float partialTicks) {
        try {
            if (!itemRenderStateChecked) {
                itemRenderStateChecked = true;
                if (!org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_TEXTURE_2D)) {
                    CrewXBootstrap.log("first-person item: GL_TEXTURE_2D was disabled; restoring");
                }
                if (org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FILL)) {
                    CrewXBootstrap.log("first-person item: polygon offset was enabled; restoring");
                }
            }
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FILL);
            if (!(rendererObject instanceof ItemRenderer) || CrewX.moduleManager == null) {
                return false;
            }
            Animations animations = (Animations) CrewX.moduleManager.modules.get(Animations.class);
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayerSP player = mc == null ? null : mc.thePlayer;
            if (animations == null || !animations.isEnabled() || player == null) {
                return false;
            }
            CrewXAudit.mark("animations-render-attempt");
            ItemRenderer renderer = (ItemRenderer) rendererObject;
            Class<?> type = ItemRenderer.class;
            if (itemToRenderField == null) {
                itemToRenderField = gameField(type,
                        "net.minecraft.client.renderer.ItemRenderer", "itemToRender");
                equippedProgressField = gameField(type,
                        "net.minecraft.client.renderer.ItemRenderer", "equippedProgress");
                prevEquippedProgressField = gameField(type,
                        "net.minecraft.client.renderer.ItemRenderer", "prevEquippedProgress");
                itemToRenderField.setAccessible(true);
                equippedProgressField.setAccessible(true);
                prevEquippedProgressField.setAccessible(true);
                rotateAroundMethod = gameMethod(type,
                        "net.minecraft.client.renderer.ItemRenderer", "rotateArroundXAndY",
                        "(FF)V", float.class, float.class);
                lightMapMethod = gameMethod(type,
                        "net.minecraft.client.renderer.ItemRenderer", "setLightMapFromPlayer",
                        "(Lnet/minecraft/client/entity/AbstractClientPlayer;)V", AbstractClientPlayer.class);
                playerRotationsMethod = gameMethod(type,
                        "net.minecraft.client.renderer.ItemRenderer", "rotateWithPlayerRotations",
                        "(Lnet/minecraft/client/entity/EntityPlayerSP;F)V", EntityPlayerSP.class, float.class);
                rotateAroundMethod.setAccessible(true);
                lightMapMethod.setAccessible(true);
                playerRotationsMethod.setAccessible(true);
            }
            ItemStack item = (ItemStack) itemToRenderField.get(renderer);
            if (item == null || item.getItem() instanceof ItemMap) {
                return false;
            }
            float equipped = equippedProgressField.getFloat(renderer);
            float previous = prevEquippedProgressField.getFloat(renderer);
            boolean using = player.getItemInUseCount() > 0;
            boolean blocking = using && item.getItemUseAction() == EnumAction.BLOCK;
            KillAura aura = (KillAura) CrewX.moduleManager.modules.get(KillAura.class);
            if (aura != null && aura.isEnabled() && aura.isBlocking()) {
                CrewXAudit.mark("killaura-blocking-frame");
                blocking = true;
            }
            blocking = animations.shouldBlock(blocking, using);
            if (using && !blocking) {
                return false;
            }
            float equip = 1.0F - (previous + (equipped - previous) * partialTicks);
            float swing = player.getSwingProgress(partialTicks);
            float pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
            float yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;
            rotateAroundMethod.invoke(renderer, pitch, yaw);
            lightMapMethod.invoke(renderer, (AbstractClientPlayer) player);
            playerRotationsMethod.invoke(renderer, player, partialTicks);
            GlStateManager.enableRescaleNormal();
            GlStateManager.pushMatrix();
            try {
                animations.applyFirstPersonTransform(blocking, equip, swing, equipped, player.isSneaking());
                renderer.renderItem(player, item, ItemCameraTransforms.TransformType.FIRST_PERSON);
            } finally {
                GlStateManager.popMatrix();
                GlStateManager.disableRescaleNormal();
                RenderHelper.disableStandardItemLighting();
            }
            CrewXAudit.mark("animations-render-success");
            return true;
        } catch (Throwable error) {
            CrewXBootstrap.log("Animations runtime FAILED: " + CrewXBootstrap.describeFailure(error));
            return false;
        }
    }

    public static int adjustSwingDuration(Object entity, int vanillaDuration) {
        try {
            if (entity instanceof EntityPlayerSP && CrewX.moduleManager != null) {
                Animations animations = (Animations) CrewX.moduleManager.modules.get(Animations.class);
                if (animations != null && animations.isEnabled()) {
                    return animations.adjustSwingDuration(vanillaDuration);
                }
            }
        } catch (Throwable error) {
            CrewXBootstrap.log("Animations swing duration FAILED: " + CrewXBootstrap.describeFailure(error));
        }
        return vanillaDuration;
    }


    public static boolean isPotionActiveForRender(EntityLivingBase entity, Potion potion) {
        try {
            if (entity != null && potion != null && CrewX.moduleManager != null) {
                AntiDebuff module = (AntiDebuff) CrewX.moduleManager.modules.get(AntiDebuff.class);
                if (module != null && module.isEnabled()) {
                    if (potion == Potion.blindness
                            && ((Boolean) module.blindness.getValue()).booleanValue()) {
                        CrewXAudit.mark("antidebuff-blindness-blocked");
                        return false;
                    }
                    if (potion == Potion.confusion
                            && ((Boolean) module.nausea.getValue()).booleanValue()) {
                        CrewXAudit.mark("antidebuff-nausea-blocked");
                        return false;
                    }
                }
            }
        } catch (Throwable error) {
            CrewXBootstrap.log("AntiDebuff runtime FAILED: " + CrewXBootstrap.describeFailure(error));
        }
        return entity != null && entity.isPotionActive(potion);
    }

    public static boolean isUsingItemForNoSlow(EntityPlayerSP player) {
        try {
            NoSlow module = CrewX.moduleManager == null ? null
                    : (NoSlow) CrewX.moduleManager.modules.get(NoSlow.class);
            if (module != null && module.isEnabled() && module.isAnyActive()) {
                CrewXAudit.mark("noslow-slowdown-blocked");
                return false;
            }
        } catch (Throwable error) {
            CrewXBootstrap.log("NoSlow runtime FAILED: " + CrewXBootstrap.describeFailure(error));
        }
        return player != null && player.isUsingItem();
    }

    public static double adjustKeepSprintSlowdown(double vanilla) {
        try {
            KeepSprint module = CrewX.moduleManager == null ? null
                    : (KeepSprint) CrewX.moduleManager.modules.get(KeepSprint.class);
            if (module != null && module.isEnabled() && module.shouldKeepSprint()) {
                CrewXAudit.mark("keepsprint-slowdown-adjusted");
                return vanilla + (1.0D - vanilla)
                        * (1.0D - ((Integer) module.slowdown.getValue()).doubleValue() / 100.0D);
            }
        } catch (Throwable error) {
            CrewXBootstrap.log("KeepSprint slowdown FAILED: " + CrewXBootstrap.describeFailure(error));
        }
        return vanilla;
    }

    public static void setSprintingForKeepSprint(EntityPlayer player, boolean sprinting) {
        try {
            KeepSprint module = CrewX.moduleManager == null ? null
                    : (KeepSprint) CrewX.moduleManager.modules.get(KeepSprint.class);
            if (module != null && module.isEnabled() && module.shouldKeepSprint()) {
                CrewXAudit.mark("keepsprint-stop-blocked");
                return;
            }
        } catch (Throwable error) {
            CrewXBootstrap.log("KeepSprint state FAILED: " + CrewXBootstrap.describeFailure(error));
        }
        player.setSprinting(sprinting);
    }

    public static float adjustHurtCameraConstant(float vanilla) {
        try {
            NoHurtCam module = CrewX.moduleManager == null ? null
                    : (NoHurtCam) CrewX.moduleManager.modules.get(NoHurtCam.class);
            if (module != null && module.isEnabled()) {
                CrewXAudit.mark("nohurtcam-adjusted");
                return vanilla * ((Integer) module.multiplier.getValue()).floatValue() / 100.0F;
            }
        } catch (Throwable error) {
            CrewXBootstrap.log("NoHurtCam runtime FAILED: " + CrewXBootstrap.describeFailure(error));
        }
        return vanilla;
    }

    public static String processFontString(String text) {
        if (text == null || CrewX.moduleManager == null) return text;
        try {
            AntiObfuscate anti = (AntiObfuscate) CrewX.moduleManager.modules.get(AntiObfuscate.class);
            if (anti != null && anti.isEnabled()) {
                text = anti.stripObfuscated(text);
                CrewXAudit.mark("antiobfuscate-text");
            }
            NickHider nick = (NickHider) CrewX.moduleManager.modules.get(NickHider.class);
            if (nick != null && nick.isEnabled()) {
                text = nick.replaceNick(text);
                CrewXAudit.mark("nickhider-text");
            }



            text = text.replaceAll("(?i)(?:\\u00a7.)?\\[(?:crew|crewx)\\](?:\\u00a7r)?\\s*", "");
        } catch (Throwable error) {
            CrewXBootstrap.log("Font modules runtime FAILED: " + CrewXBootstrap.describeFailure(error));
        }
        return text;
    }

    public static ResourceLocation getCustomCape(Object playerObject) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || playerObject != mc.thePlayer || CrewX.moduleManager == null) return null;
            Accessories accessories = (Accessories) CrewX.moduleManager.modules.get(Accessories.class);
            if (accessories != null && accessories.isEnabled()) {
                ResourceLocation texture = accessories.getCapeTexture();
                if (texture != null) CrewXAudit.mark("cape-texture");
                return texture;
            }
        } catch (Throwable error) {
            CrewXBootstrap.log("Cape runtime FAILED: " + CrewXBootstrap.describeFailure(error));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static void onMainMenuInit(Object screenObject) {
        try {
            if (!(screenObject instanceof GuiScreen)) return;
            GuiScreen screen = (GuiScreen) screenObject;
            if (buttonListField == null) {
                buttonListField = gameField(GuiScreen.class,
                        "net.minecraft.client.gui.GuiScreen", "buttonList");
                buttonListField.setAccessible(true);
            }
            java.util.List<GuiButton> buttons =
                    (java.util.List<GuiButton>) buttonListField.get(screen);
            buttons.clear();
            int wide = Math.min(300, Math.max(220, screen.width / 5));
            int narrow = (wide - 12) / 2;
            int left = screen.width / 2 - wide / 2;
            int top = screen.height / 2 - 48;
            buttons.add(new GuiButton(1, left, top, wide, 28, "Singleplayer"));
            buttons.add(new GuiButton(2, left, top + 36, wide, 28, "Multiplayer"));
            buttons.add(new GuiButton(0, left, top + 72, narrow, 28, "Options"));
            buttons.add(new GuiButton(1337, left + narrow + 12, top + 72, narrow, 28, "Alts"));
            buttons.add(new GuiButton(1338, screen.width - 86, screen.height - 31, 74, 20, "Quit"));
            CrewXAudit.mark("altmanager-button-added");
        } catch (Throwable error) {
            CrewXBootstrap.log("Alt Manager button FAILED: " + CrewXBootstrap.describeFailure(error));
        }
    }

    public static boolean onMainMenuAction(Object screenObject, Object buttonObject) {
        try {
            if (!(screenObject instanceof GuiScreen) || !(buttonObject instanceof GuiButton)) return false;
            GuiButton button = (GuiButton) buttonObject;
            if (!button.enabled) return false;
            if (button.id == 1337) {
                Minecraft.getMinecraft().displayGuiScreen(
                        new crewx.accountmanager.gui.GuiAccountManager((GuiScreen) screenObject));
                CrewXAudit.mark("altmanager-opened");
                return true;
            }
            if (button.id == 0) {
                Minecraft mc = Minecraft.getMinecraft();
                mc.displayGuiScreen(new net.minecraft.client.gui.GuiOptions((GuiScreen) screenObject, mc.gameSettings));
                return true;
            }
            if (button.id == 1338) {
                Minecraft.getMinecraft().shutdown();
                return true;
            }
            return false;
        } catch (Throwable error) {
            CrewXBootstrap.log("Alt Manager open FAILED: " + CrewXBootstrap.describeFailure(error));
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean onMainMenuDraw(Object screenObject, int mouseX, int mouseY) {
        try {
            if (!(screenObject instanceof GuiScreen)) return false;
            GuiScreen screen = (GuiScreen) screenObject;
            crewx.gui.CrewXTheme.drawBackground(screen.width, screen.height);
            crewx.gui.CrewXTheme.drawMainTitle(screen.width, screen.height);
            if (buttonListField == null) {
                buttonListField = gameField(GuiScreen.class,
                        "net.minecraft.client.gui.GuiScreen", "buttonList");
                buttonListField.setAccessible(true);
            }
            for (GuiButton button : (java.util.List<GuiButton>) buttonListField.get(screen)) {
                button.drawButton(Minecraft.getMinecraft(), mouseX, mouseY);
            }
            return true;
        } catch (Throwable error) {
            CrewXBootstrap.log("Main menu draw FAILED: " + CrewXBootstrap.describeFailure(error));
            return false;
        }
    }

    public static boolean onDrawButton(Object buttonObject, int mouseX, int mouseY) {
        try {
            if (!(buttonObject instanceof GuiButton)) return false;
            crewx.gui.CrewXTheme.drawButton((GuiButton) buttonObject, mouseX, mouseY);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }





    public static boolean onKnockbackVelocity(Object entity, double x, double y, double z) {
        try {
            if (!(entity instanceof EntityPlayerSP)) {
                return false;
            }
            KnockbackEvent event = new KnockbackEvent(x, y, z);
            EventManager.call(event);
            if (!event.isCancelled()) {
                return false;
            }
            EntityPlayerSP player = (EntityPlayerSP) entity;
            player.motionX = event.getX();
            player.motionY = event.getY();
            player.motionZ = event.getZ();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void onPlayerUpdate() {
        try {
            EventManager.call(new PlayerUpdateEvent());
        } catch (Throwable ignored) {

        }
    }

    public static void onLivingUpdate() {
        try {
            EventManager.call(new LivingUpdateEvent());
        } catch (Throwable ignored) {

        }
    }

    public static void onMoveInput() {
        try {
            EventManager.call(new MoveInputEvent());
        } catch (Throwable ignored) {

        }
    }

    public static void onLoadWorld() {
        try {
            EventManager.call(new LoadWorldEvent());
        } catch (Throwable ignored) {

        }
    }

    public static void onResize() {
        try {
            EventManager.call(new ResizeEvent());
        } catch (Throwable ignored) {

        }
    }

    public static void onHitBlock() {
        try {
            HitBlockEvent event = new HitBlockEvent();
            EventManager.call(event);
        } catch (Throwable ignored) {

        }
    }

    public static void onAttack(Object target) {
        try {
            if (!(target instanceof Entity)) {
                return;
            }
            attackCalls++;
            EventManager.call(new AttackEvent((Entity) target));
        } catch (Throwable ignored) {

        }
    }





    public static boolean onLeftClick(Object minecraft) {
        try {
            if (CrewX.moduleManager != null && minecraft instanceof Minecraft) {
                Object raw = CrewX.moduleManager.modules.get(
                        crewx.module.modules.combat.NoHitDelay.class);
                if (raw instanceof Module && ((Module) raw).isEnabled()) {
                    try {
                        if (leftClickCounterField == null) {
                            leftClickCounterField =
                                    gameField(Minecraft.class,
                                            "net.minecraft.client.Minecraft", "leftClickCounter");
                            leftClickCounterField.setAccessible(true);
                        }
                        leftClickCounterField.setInt(minecraft, 0);
                    } catch (Throwable ignored) {

                    }
                }
            }
            LeftClickMouseEvent event = new LeftClickMouseEvent();
            EventManager.call(event);
            return event.isCancelled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean onRightClick() {
        try {
            RightClickMouseEvent event = new RightClickMouseEvent();
            EventManager.call(event);
            return event.isCancelled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean onWindowClick(int windowId, int slotId, int mouseButton, int mode) {
        try {
            WindowClickEvent event = new WindowClickEvent(windowId, slotId, mouseButton, mode);
            EventManager.call(event);
            return event.isCancelled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean onStoppedUsing() {
        try {
            CancelUseEvent event = new CancelUseEvent();
            EventManager.call(event);
            return event.isCancelled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isClientPlayConnection(Object manager) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.getNetHandler() != null
                && mc.getNetHandler().getNetworkManager() == manager;
    }

    public static boolean onPacketSend(Object manager, Object packet) {
        try {
            if (!isClientPlayConnection(manager) || !(packet instanceof Packet)) {
                return false;
            }
            if (!firstClientPacketSent) {
                firstClientPacketSent = true;
                CrewXBootstrap.log("client packet send hook active: "
                        + packet.getClass().getName());
            }
            if (packet.getClass().getName().contains("C02PacketUseEntity")) {
                c02Packets++;
            }
            String packetName = packet.getClass().getSimpleName();
            if (packetName.contains("C07PacketPlayerDigging")) CrewXAudit.mark("packet-C07");
            if (packetName.contains("C08PacketPlayerBlockPlacement")) CrewXAudit.mark("packet-C08");
            if (packet.getClass().getName().startsWith("net.minecraft.network.play.server")) {
                return false;
            }
            PacketEvent event = new PacketEvent(EventType.SEND, (Packet) packet);
            EventManager.call(event);
            if (event.isCancelled()) {
                return true;
            }
            if (CrewX.playerStateManager == null || CrewX.blinkManager == null
                    || CrewX.lagManager == null) {
                return false;
            }
            if (CrewX.lagManager.isFlushing()) {
                return false;
            }
            CrewX.playerStateManager.handlePacket((Packet) packet);
            if (CrewX.blinkManager.isBlinking()
                    && CrewX.blinkManager.offerPacket((Packet) packet)) {
                return true;
            }
            boolean queued = CrewX.lagManager.handlePacket((Packet) packet);
            if (queued && !firstLagPacketQueued) {
                firstLagPacketQueued = true;
                CrewXBootstrap.log("first lag packet queued: " + packet.getClass().getName());
            }
            return queued;
        } catch (Throwable error) {
            if (!packetSendFailureLogged) {
                packetSendFailureLogged = true;
                CrewXBootstrap.log("client packet send hook FAILED: "
                        + CrewXBootstrap.describeFailure(error));
            }
            return false;
        }
    }

    public static boolean onPacketSend2(Object manager, Object packet) {
        try {
            if (!isClientPlayConnection(manager) || !(packet instanceof Packet)) {
                return false;
            }
            if (!firstClientPacketSent2) {
                firstClientPacketSent2 = true;
                CrewXBootstrap.log("client packet send2 hook active: "
                        + packet.getClass().getName());
            }
            if (packet.getClass().getName().startsWith("net.minecraft.network.play.server")) {
                return false;
            }
            if (CrewX.playerStateManager == null || CrewX.blinkManager == null
                    || CrewX.lagManager == null) {
                return false;
            }
            if (CrewX.lagManager.isFlushing()) {
                return false;
            }
            CrewX.playerStateManager.handlePacket((Packet) packet);
            if (CrewX.blinkManager.isBlinking()
                    && CrewX.blinkManager.offerPacket((Packet) packet)) {
                return true;
            }
            boolean queued = CrewX.lagManager.handlePacket((Packet) packet);
            if (queued && !firstLagPacketQueued) {
                firstLagPacketQueued = true;
                CrewXBootstrap.log("first lag packet queued: " + packet.getClass().getName());
            }
            return queued;
        } catch (Throwable error) {
            if (!packetSendFailureLogged) {
                packetSendFailureLogged = true;
                CrewXBootstrap.log("client packet send hook FAILED: "
                        + CrewXBootstrap.describeFailure(error));
            }
            return false;
        }
    }

    public static boolean onPacketReceive(Object manager, Object packet) {
        try {
            if (!isClientPlayConnection(manager) || !(packet instanceof Packet)) {
                return false;
            }
            if (!firstClientPacketReceived) {
                firstClientPacketReceived = true;
                CrewXBootstrap.log("client packet receive hook active: "
                        + packet.getClass().getName());
            }
            if (packet.getClass().getName().startsWith("net.minecraft.network.play.client")) {
                return false;
            }
            if (packet.getClass().getSimpleName().contains("S08PacketPlayerPosLook")
                    && positionCorrectionSnapshots < 16
                    && CrewX.moduleManager != null && CrewX.lagManager != null) {
                Module lagRange = (Module) CrewX.moduleManager.modules.get(
                        crewx.module.modules.combat.LagRange.class);
                if (lagRange != null && lagRange.isEnabled()) {
                    positionCorrectionSnapshots++;
                    CrewXBootstrap.log("position correction with LagRange: delayTicks="
                            + CrewX.lagManager.getTickDelay()
                            + " queued=" + CrewX.lagManager.packetQueue.size());
                }
            }
            if (CrewX.delayManager != null
                    && CrewX.delayManager.shouldDelay((Packet) packet)) {
                return true;
            }
            PacketEvent event = new PacketEvent(EventType.RECEIVE, (Packet) packet);
            EventManager.call(event);
            return event.isCancelled();
        } catch (Throwable error) {
            if (!packetReceiveFailureLogged) {
                packetReceiveFailureLogged = true;
                CrewXBootstrap.log("client packet receive hook FAILED: "
                        + CrewXBootstrap.describeFailure(error));
            }
            return false;
        }
    }






    public static void onUpdatePre(Object playerObject) {
        try {
            if (!(playerObject instanceof EntityPlayerSP)) {
                return;
            }
            EntityPlayerSP player = (EntityPlayerSP) playerObject;
            if (player.worldObj == null
                    || !player.worldObj.isBlockLoaded(
                            new BlockPos(player.posX, 0.0D, player.posZ))) {
                return;
            }
            UpdateEvent event = new UpdateEvent(EventType.PRE,
                    getLastReportedYaw(player), getLastReportedPitch(player),
                    player.rotationYaw, player.rotationPitch);
            EventManager.call(event);
            boolean rotating = event.isRotated() && !player.isRiding();
            RotationState.applyState(rotating, event.getNewYaw(), event.getNewPitch(),
                    event.getPreYaw(), event.isRotating());
            float[] state = rotationOf(player);
            if (event.isRotated()) {
                state[0] = player.rotationYaw;
                state[1] = player.rotationPitch;
                state[2] = event.getNewYaw();
                state[3] = event.getNewPitch();
            } else {
                state[0] = Float.NaN;
                state[1] = Float.NaN;
                state[2] = Float.NaN;
                state[3] = Float.NaN;
            }
        } catch (Throwable ignored) {

        }
    }


    public static void onUpdateApply(Object playerObject) {
        if (!(playerObject instanceof EntityPlayerSP)) return;
        EntityPlayerSP player = (EntityPlayerSP) playerObject;
        float[] state = rotationOf(player);
        if (!Float.isNaN(state[2]) && !Float.isNaN(state[3])) {
            player.rotationYaw = state[2];
            player.rotationPitch = state[3];
        }
    }

    public static void onUpdatePost(Object playerObject) {
        try {
            if (!(playerObject instanceof EntityPlayerSP)) {
                return;
            }
            EntityPlayerSP player = (EntityPlayerSP) playerObject;
            if (player.worldObj == null
                    || !player.worldObj.isBlockLoaded(
                            new BlockPos(player.posX, 0.0D, player.posZ))) {
                return;
            }
            float[] state = rotationOf(player);
            if (!Float.isNaN(state[0]) && !Float.isNaN(state[1])) {
                setLastReported(player, player.rotationYaw, player.rotationPitch);
                player.rotationYaw = player.rotationYaw
                        + MathHelper.wrapAngleTo180_float(state[0] - player.rotationYaw);
                player.rotationPitch = state[1];
                player.prevRotationYaw = player.rotationYaw;
                player.prevRotationPitch = player.rotationPitch;
                player.prevRenderArmYaw = player.rotationYaw
                        - (player.renderArmYaw - player.prevRenderArmYaw) * 2.0F;
                player.renderArmYaw = player.rotationYaw;
            }
            EventManager.call(new UpdateEvent(EventType.POST,
                    getLastReportedYaw(player), getLastReportedPitch(player),
                    player.rotationYaw, player.rotationPitch));
        } catch (Throwable ignored) {

        }
    }





    private static void diagnose() {
        try {
            StringBuilder detail = new StringBuilder();
            detail.append("diag ticks=").append(tickPreCount)
                    .append(" keys=").append(keyCount)
                    .append(" render2d=").append(render2dCount)
                    .append(" attacks=").append(attackCalls)
                    .append(" c02=").append(c02Packets);
            try {
                Field registryField = crewx.event.EventManager.class
                        .getDeclaredField("REGISTRY_MAP");
                registryField.setAccessible(true);
                Object map = registryField.get(null);
                if (map instanceof java.util.Map) {
                    java.util.Map<?, ?> registry = (java.util.Map<?, ?>) map;
                    detail.append(" registryTypes=").append(registry.size());
                    for (Object key : new java.util.ArrayList<Object>(registry.keySet())) {
                        Object list = registry.get(key);
                        int size = list instanceof java.util.List
                                ? ((java.util.List<?>) list).size() : -1;
                        String name = key == null ? "null" : ((Class<?>) key).getSimpleName();
                        detail.append(' ').append(name).append('=').append(size);
                    }
                }
            } catch (Throwable registryError) {
                detail.append(" registry=? (").append(registryError).append(')');
            }
            try {
                if (crewx.CrewX.moduleManager != null) {
                    detail.append(" modules=").append(crewx.CrewX.moduleManager.modules.size());
                }
            } catch (Throwable ignored) {

            }
            CrewXAudit.snapshot();
            CrewXBootstrap.log(detail.toString());
        } catch (Throwable ignored) {

        }
    }





    private static void probeModule(StringBuilder detail, String label, String className) {
        try {
            if (crewx.CrewX.moduleManager == null) {
                return;
            }
            Object found = null;
            for (Object module : crewx.CrewX.moduleManager.modules.values()) {
                if (module != null && module.getClass().getName().equals(className)) {
                    found = module;
                    break;
                }
            }
            if (found == null) {
                detail.append(' ').append(label).append("=?");
                return;
            }
            boolean enabled = ((crewx.module.Module) found).isEnabled();
            detail.append(' ').append(label).append(enabled ? "=ON" : "=off");
            try {
                java.lang.reflect.Method onTick = found.getClass()
                        .getMethod("onTick", crewx.events.TickEvent.class);
                onTick.invoke(found, new crewx.events.TickEvent(
                        crewx.event.types.EventType.PRE));
                detail.append("(tick-ok)");
            } catch (Throwable error) {
                detail.append("(tick-FAIL ")
                        .append(CrewXBootstrap.describeFailure(error)).append(')');
            }
        } catch (Throwable error) {
            detail.append(' ').append(label).append("(probe-FAIL ").append(error).append(')');
        }
    }





    private static void probeTick(StringBuilder detail, String label, String className,
                                  String methodName, Object event) {
        try {
            if (crewx.CrewX.moduleManager == null) {
                return;
            }
            Object found = null;
            for (Object module : crewx.CrewX.moduleManager.modules.values()) {
                if (module != null && module.getClass().getName().equals(className)) {
                    found = module;
                    break;
                }
            }
            if (found == null) {
                detail.append(' ').append(label).append("=?");
                return;
            }
            boolean enabled = ((crewx.module.Module) found).isEnabled();
            detail.append(' ').append(label).append(enabled ? "=ON" : "=off");
            try {
                java.lang.reflect.Method handler = found.getClass()
                        .getMethod(methodName, event.getClass());
                handler.invoke(found, event);
                detail.append("(ok)");
            } catch (Throwable error) {
                detail.append("(FAIL ")
                        .append(CrewXBootstrap.describeFailure(error)).append(')');
            }
        } catch (Throwable error) {
            detail.append(' ').append(label).append("(probe-FAIL ").append(error).append(')');
        }
    }





    private static void probeKillAura(StringBuilder detail) {
        try {
            if (crewx.CrewX.moduleManager == null) {
                return;
            }
            Object found = null;
            for (Object module : crewx.CrewX.moduleManager.modules.values()) {
                if (module != null && module.getClass().getName()
                        .equals("crewx.module.modules.combat.KillAura")) {
                    found = module;
                    break;
                }
            }
            if (found == null) {
                detail.append(" KA=?");
                return;
            }
            boolean enabled = ((crewx.module.Module) found).isEnabled();
            Object target = null;
            try {
                java.lang.reflect.Field targetField = found.getClass().getDeclaredField("target");
                targetField.setAccessible(true);
                target = targetField.get(found);
            } catch (Throwable ignored) {

            }
            String canAttack = "?";
            try {
                java.lang.reflect.Method gate = found.getClass().getDeclaredMethod("canAttack");
                gate.setAccessible(true);
                canAttack = String.valueOf(gate.invoke(found));
            } catch (Throwable error) {
                canAttack = "ERR";
            }
            detail.append(" KA").append(enabled ? "=ON" : "=off")
                    .append(" target=").append(target == null ? "null" : target.getClass().getSimpleName())
                    .append(" canAttack=").append(canAttack);
        } catch (Throwable error) {
            detail.append(" KA(probe-FAIL ").append(error).append(')');
        }
    }






    public static void onStrafeMove(Object entityObject, float strafe, float forward,
                                    float friction) {
        try {
            if (!(entityObject instanceof Entity)) {
                return;
            }
            Entity entity = (Entity) entityObject;
            if (entity instanceof EntityPlayerSP) {
                StrafeEvent event = new StrafeEvent(strafe, forward, friction);
                EventManager.call(event);
                strafe = event.getStrafe();
                forward = event.getForward();
                friction = event.getFriction();
                boolean active = RotationState.isActived();
                float savedYaw = entity.rotationYaw;
                if (active) {
                    entity.rotationYaw = RotationState.getSmoothedYaw();
                }
                entity.moveFlying(strafe, forward, friction);
                if (active) {
                    entity.rotationYaw = savedYaw;
                }
                return;
            }
            entity.moveFlying(strafe, forward, friction);
        } catch (Throwable ignored) {
            try {
                if (entityObject instanceof Entity) {
                    ((Entity) entityObject).moveFlying(strafe, forward, friction);
                }
            } catch (Throwable alsoIgnored) {

            }
        }
    }

    public static boolean isPushedByWater(Object entityObject) {
        try {
            if (entityObject instanceof EntityPlayerSP && CrewX.moduleManager != null) {
                crewx.module.modules.movement.Jesus jesus =
                        (crewx.module.modules.movement.Jesus) CrewX.moduleManager.modules.get(
                                crewx.module.modules.movement.Jesus.class);
                if (jesus != null && jesus.isEnabled()
                        && ((Boolean) jesus.noPush.getValue()).booleanValue()) {
                    CrewXAudit.mark("jesus-no-water-push");
                    return false;
                }
            }
            return entityObject instanceof Entity && ((Entity) entityObject).isPushedByWater();
        } catch (Throwable ignored) {
            return entityObject instanceof Entity && ((Entity) entityObject).isPushedByWater();
        }
    }

    public static int adjustDepthStrider(int vanilla, Object entityObject) {
        try {
            if (entityObject instanceof EntityPlayerSP && CrewX.moduleManager != null) {
                crewx.module.modules.movement.Jesus jesus =
                        (crewx.module.modules.movement.Jesus) CrewX.moduleManager.modules.get(
                                crewx.module.modules.movement.Jesus.class);
                EntityPlayerSP player = (EntityPlayerSP) entityObject;
                if (jesus != null && jesus.isEnabled()
                        && (!((Boolean) jesus.groundOnly.getValue()).booleanValue() || player.onGround)) {
                    int configured = Math.round(((Float) jesus.speed.getValue()).floatValue());
                    int result = Math.max(vanilla, configured);
                    if (result != vanilla) CrewXAudit.mark("jesus-water-speed");
                    return result;
                }
            }
        } catch (Throwable ignored) { }
        return vanilla;
    }

    public static void onBlockRendered(Object stateObject, Object posObject) {
        try {
            if (!(stateObject instanceof IBlockState) || !(posObject instanceof BlockPos)
                    || CrewX.moduleManager == null) return;
            IBlockState state = (IBlockState) stateObject;
            BlockPos pos = (BlockPos) posObject;
            crewx.module.modules.render.BedESP bed =
                    (crewx.module.modules.render.BedESP) CrewX.moduleManager.modules.get(
                            crewx.module.modules.render.BedESP.class);
            if (bed != null && bed.isEnabled() && state.getBlock() instanceof BlockBed
                    && state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD) {
                bed.beds.add(new BlockPos(pos));
                CrewXAudit.mark("bedesp-bed-found");
            }
            crewx.module.modules.render.Xray xray =
                    (crewx.module.modules.render.Xray) CrewX.moduleManager.modules.get(
                            crewx.module.modules.render.Xray.class);
            if (xray != null && xray.isEnabled()
                    && xray.isXrayBlock(Block.getIdFromBlock(state.getBlock()))) {
                if (xray.checkBlock(pos)) xray.trackedBlocks.add(new BlockPos(pos));
                else xray.trackedBlocks.remove(pos);
                CrewXAudit.mark("xray-block-scanned");
            }
        } catch (Throwable error) {
            CrewXBootstrap.log("block scan FAILED: " + CrewXBootstrap.describeFailure(error));
        }
    }

    public static boolean xrayForceSide(Object blockObject, Object posObject, Object facingObject) {
        try {
            if (!(blockObject instanceof Block) || !(posObject instanceof BlockPos)
                    || !(facingObject instanceof EnumFacing) || CrewX.moduleManager == null) return false;
            crewx.module.modules.render.Xray xray =
                    (crewx.module.modules.render.Xray) CrewX.moduleManager.modules.get(
                            crewx.module.modules.render.Xray.class);
            if (xray == null || !xray.isEnabled() || ((Integer) xray.mode.getValue()).intValue() != 1
                    || !xray.shouldRenderSide(Block.getIdFromBlock((Block) blockObject))) return false;
            BlockPos pos = (BlockPos) posObject;
            net.minecraft.util.Vec3i vec = ((EnumFacing) facingObject).getDirectionVec();
            boolean result = xray.checkBlock(new BlockPos(pos.getX() - vec.getX(),
                    pos.getY() - vec.getY(), pos.getZ() - vec.getZ()));
            if (result) CrewXAudit.mark("xray-force-side");
            return result;
        } catch (Throwable ignored) { return false; }
    }

    public static Object xrayBlockLayer(Object blockObject) {
        try {
            if (!(blockObject instanceof Block) || CrewX.moduleManager == null) return null;
            crewx.module.modules.render.Xray xray =
                    (crewx.module.modules.render.Xray) CrewX.moduleManager.modules.get(
                            crewx.module.modules.render.Xray.class);
            if (xray == null || !xray.isEnabled()) return null;
            int id = Block.getIdFromBlock((Block) blockObject);
            if (!xray.shouldRenderSide(id)
                    || (((Integer) xray.mode.getValue()).intValue() == 0 && !xray.isXrayBlock(id))) {
                CrewXAudit.mark("xray-translucent-layer");
                return EnumWorldBlockLayer.TRANSLUCENT;
            }
        } catch (Throwable ignored) { }
        return null;
    }
}
