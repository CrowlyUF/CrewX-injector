package crewx.inject;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.IResourcePack;

















public final class CrewXBootstrap {

    private static final Map<Class<?>, byte[]> ORIGINAL_HOOKS = new LinkedHashMap<>();
    private static volatile boolean unloadStarted;
    public static volatile boolean injectionActive;
    private static IResourcePack installedResourcePack;
    private static List<IResourcePack> installedResourcePacks;

    private static final String[] RUNTIME_HOOK_TARGETS = {
            "net.minecraft.client.Minecraft",
            "net.minecraft.client.gui.GuiIngame",
            "net.minecraftforge.client.GuiIngameForge",
            "net.minecraft.client.gui.GuiChat",
            "net.minecraft.client.gui.GuiScreen",
            "net.minecraft.client.gui.GuiTextField",
            "net.minecraft.client.gui.GuiNewChat",
            "net.minecraft.client.network.NetHandlerPlayClient",
            "net.minecraft.client.renderer.chunk.VisGraph",
            "net.minecraft.client.renderer.WorldRenderer",
            "net.minecraft.client.settings.KeyBinding",
            "net.minecraft.item.ItemStack",
            "net.minecraft.client.renderer.BlockModelRenderer",
            "net.minecraft.client.renderer.entity.RenderManager",
            "net.minecraft.client.gui.GuiMainMenu",
            "net.minecraft.client.gui.GuiButton",
            "net.minecraft.client.renderer.EntityRenderer",
            "net.minecraft.client.renderer.ItemRenderer",
            "net.minecraft.client.gui.FontRenderer",
            "net.minecraft.client.entity.AbstractClientPlayer",
            "net.minecraft.client.entity.EntityPlayerSP",
            "net.minecraft.entity.player.EntityPlayer",
            "net.minecraft.util.MovementInputFromOptions",
            "net.minecraft.entity.EntityLivingBase",
            "net.minecraft.client.multiplayer.PlayerControllerMP",
            "net.minecraft.network.NetworkManager",
            "net.minecraft.entity.Entity",
            "net.minecraft.client.renderer.entity.RendererLivingEntity",
            "net.minecraft.world.World",
            "net.minecraft.block.Block",
            "net.minecraft.block.BlockBush",
            "net.minecraft.block.BlockGrass",
            "net.minecraft.block.BlockLadder",
            "net.minecraft.block.BlockLeaves",
            "net.minecraft.block.BlockPane",
            "net.minecraft.block.BlockWeb",
            "net.minecraft.client.renderer.BlockRendererDispatcher",
            "crewx.module.modules.misc.BedNuker",
    };

    private CrewXBootstrap() {
    }

    public static void start() {
        if (unloadStarted) {
            CrewXRuntimeTransformer.resetAfterUnload();
            CrewXRuntimeHooks.resetAfterUnload();
            crewx.CrewX.unloaded = false;
            unloadStarted = false;
            log("reset runtime state for reinjection");
        }
        redirectConsole();
        log("CrewXBootstrap.start (loader=" + describeLoader(CrewXBootstrap.class.getClassLoader()) + ")");
        log("contextLoader=" + describeLoader(Thread.currentThread().getContextClassLoader()));
        log("weaveApiPresent=" + isPresent("net.weavemc.api.ModInitializer"));
        log("minecraftPresent=" + (isPresent(NotchMappings.className("net.minecraft.client.Minecraft"))
                || isPresent("net.minecraft.client.MinecraftClient")));

        if (!waitForMinecraft(90)) {
            log("WARN Minecraft instance not visible yet; continuing anyway");
        }





        tryInit("crewx.init.Initializer");

        if (!runOnClientThread()) {
            if (NotchMappings.isNotch()) {
                throw new IllegalStateException("Badlion CrewX client-thread initialization failed; see crewx-inject.log");
            }
            log("WARN client-thread handoff failed; falling back to tick-lazy init");
        } else {
            injectionActive = true;
            log("CrewX client-thread initialization completed");
        }
    }


    public static boolean unload() {
        if (unloadStarted) return false;
        unloadStarted = true;
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) loader = CrewXBootstrap.class.getClassLoader();
            Class<?> minecraftClass = Class.forName(
                    NotchMappings.className("net.minecraft.client.Minecraft"), false, loader);
            Object minecraft = minecraftClass.getMethod(NotchMappings.methodName(
                    "net.minecraft.client.Minecraft", "getMinecraft",
                    "()Lnet/minecraft/client/Minecraft;")).invoke(null);
            if (minecraft == null) return false;
            final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            final boolean[] success = new boolean[1];
            Runnable task = new Runnable() {
                @Override public void run() {
                    try {
                        success[0] = unloadOnClientThread();
                    } catch (Throwable error) {
                        log("unload FAILED: " + describeFailure(error));
                    } finally {
                        done.countDown();
                    }
                }
            };
            boolean scheduled = false;
            for (Method candidate : minecraftClass.getMethods()) {
                if (candidate.getName().equals(NotchMappings.methodName(
                        "net.minecraft.client.Minecraft", "addScheduledTask",
                        "(Ljava/lang/Runnable;)Lcom/google/common/util/concurrent/ListenableFuture;"))
                        && candidate.getParameterTypes().length == 1
                        && candidate.getParameterTypes()[0].isAssignableFrom(Runnable.class)) {
                    candidate.invoke(minecraft, task);
                    scheduled = true;
                    break;
                }
            }
            if (!scheduled || !done.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                log("unload FAILED: client-thread cleanup unavailable or timed out");
                return false;
            }
            return success[0];
        } catch (Throwable error) {
            log("unload FAILED: " + describeFailure(error));
            return false;
        }
    }

    private static boolean unloadOnClientThread() {
        Minecraft mc = Minecraft.getMinecraft();
        if (crewx.CrewX.moduleManager == null) {
            log("unload FAILED: CrewX was not initialized");
            return false;
        }


        new crewx.config.Config(crewx.config.Config.lastConfig, false).save(false);
        crewx.config.Config.suspendAutoSave();
        crewx.CrewX.unloaded = true;
        if (mc.currentScreen instanceof crewx.gui.ClickGui) mc.displayGuiScreen(null);
        for (crewx.module.Module module : crewx.CrewX.moduleManager.modules.values()) {
            if (!module.isEnabled()) continue;
            try { module.setEnabled(false); }
            catch (Throwable error) { log("unload module " + module.getName() + ": " + describeFailure(error)); }
        }
        try { if (crewx.CrewX.discordRPC != null) crewx.CrewX.discordRPC.shutdown(); }
        catch (Throwable error) { log("unload RPC: " + describeFailure(error)); }
        crewx.event.EventManager.cleanMap(false);

        boolean restored = true;
        List<Map.Entry<Class<?>, byte[]>> hooks = new ArrayList<>(ORIGINAL_HOOKS.entrySet());
        for (int i = hooks.size() - 1; i >= 0; --i) {
            Map.Entry<Class<?>, byte[]> hook = hooks.get(i);
            try {
                int result = CrewXNative.scb(hook.getKey(), hook.getValue());
                if (result != 0) {
                    restored = false;
                    log("unload restore FAILED " + hook.getKey().getName() + ": JVMTI=" + result);
                }
            } catch (Throwable error) {
                restored = false;
                log("unload restore FAILED " + hook.getKey().getName() + ": " + describeFailure(error));
            }
        }
        if (!restored) return false;
        ORIGINAL_HOOKS.clear();
        if (installedResourcePacks != null && installedResourcePack != null) {
            installedResourcePacks.remove(installedResourcePack);
            installedResourcePacks = null;
            installedResourcePack = null;
            try { mc.refreshResources(); } catch (Throwable ignored) {}
        }
        crewx.CrewX.initializationComplete = false;
        injectionActive = false;
        log("unload complete: modules stopped and runtime hooks restored");
        return true;
    }






    private static boolean runOnClientThread() {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) {
                loader = CrewXBootstrap.class.getClassLoader();
            }
            Class<?> minecraftClass = Class.forName(
                    NotchMappings.className("net.minecraft.client.Minecraft"), false, loader);
            Object minecraft = minecraftClass.getMethod(NotchMappings.methodName(
                    "net.minecraft.client.Minecraft", "getMinecraft",
                    "()Lnet/minecraft/client/Minecraft;")).invoke(null);
            if (minecraft == null) {
                return false;
            }
            final java.util.concurrent.CountDownLatch done =
                    new java.util.concurrent.CountDownLatch(1);
            final boolean[] ok = new boolean[1];
            Object task = (Runnable) new Runnable() {
                @Override
                public void run() {
                    try {




                        installResources();
                        verifyHookBridge();
                        applyRuntimeHooks();
                        if (!CrewXRuntimeHooks.initializeOnClientThread()) {
                            throw new IllegalStateException("CrewX did not initialize on the client thread");
                        }
                        ok[0] = true;
                    } catch (Throwable error) {
                        log("client-thread init FAILED: " + describeFailure(error));
                    } finally {
                        done.countDown();
                    }
                }
            };
            boolean scheduled = false;
            for (Method candidate : minecraftClass.getMethods()) {
                if (!candidate.getName().equals(NotchMappings.methodName(
                        "net.minecraft.client.Minecraft", "addScheduledTask",
                        "(Ljava/lang/Runnable;)Lcom/google/common/util/concurrent/ListenableFuture;"))
                        || candidate.getParameterTypes().length != 1
                        || !candidate.getParameterTypes()[0].isAssignableFrom(Runnable.class)) {
                    continue;
                }
                candidate.invoke(minecraft, task);
                scheduled = true;
                break;
            }
            if (!scheduled) {
                log("WARN addScheduledTask(Runnable) not found");
                return false;
            }
            if (!done.await(120, java.util.concurrent.TimeUnit.SECONDS)) {
                log("WARN client-thread init timed out");
                return false;
            }
            return ok[0];
        } catch (Throwable error) {
            log("WARN client-thread handoff FAILED: " + error);
            return false;
        }
    }

    private static boolean waitForMinecraft(int seconds) {
        for (int attempt = 0; attempt < seconds * 10; attempt++) {
            try {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                if (loader == null) {
                    loader = CrewXBootstrap.class.getClassLoader();
                }
                Class<?> minecraft = Class.forName(
                        NotchMappings.className("net.minecraft.client.Minecraft"), false, loader);
                Object instance = minecraft.getMethod(NotchMappings.methodName(
                        "net.minecraft.client.Minecraft", "getMinecraft",
                        "()Lnet/minecraft/client/Minecraft;")).invoke(null);
                if (instance != null) {
                    log("Minecraft instance visible");
                    return true;
                }
            } catch (Throwable ignored) {

            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static void applyRuntimeHooks() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = CrewXBootstrap.class.getClassLoader();
        }
        for (String className : RUNTIME_HOOK_TARGETS) {
            try {
                Class<?> target = Class.forName(NotchMappings.className(className), false, loader);
                byte[] original = CrewXNative.gcb(target);
                if (original == null || original.length < 4) {
                    log("hook SKIP " + className + ": no bytecode captured");
                    continue;
                }
                byte[] namedOriginal = NotchMappings.isNotch()
                        ? NotchMappings.toNamed(original)
                        : NotchMappings.isSrg() ? NotchMappings.srgToNamed(original) : original;
                byte[] patched = CrewXRuntimeTransformer.transform(
                        className.replace('.', '/'), namedOriginal);
                if (patched == null) {
                    log("hook SKIP " + className + ": no patch produced");
                    continue;
                }
                if (NotchMappings.isNotch()) patched = NotchMappings.toNotch(patched);
                else if (NotchMappings.isSrg()) patched = NotchMappings.namedToSrg(patched);
                int result = CrewXNative.scb(target, patched);
                if (result == 0) ORIGINAL_HOOKS.put(target, original);
                log("hook " + className + ": scb=" + result
                        + " (" + original.length + " -> " + patched.length + " bytes)");
                if (NotchMappings.isNotch()
                        && "net.minecraft.client.Minecraft".equals(className)) {
                    dumpDebugBytes(className, original, patched);
                    final Class<?> minecraftClass = target;
                    final String loop = NotchMappings.methodName(className,
                            "runGameLoop", "()V");
                    final String tickMethod = NotchMappings.methodName(className,
                            "runTick", "()V");
                    log("live method bytes after redefine: loop="
                            + CrewXNative.mbl(target, loop, "()V") + " tick="
                            + CrewXNative.mbl(target, tickMethod, "()V"));
                    Thread verify = new Thread(new Runnable() {
                        @Override public void run() {
                            try {
                                Thread.sleep(3000L);
                                log("live method bytes after 3s: loop="
                                        + CrewXNative.mbl(minecraftClass, loop, "()V")
                                        + " tick="
                                        + CrewXNative.mbl(minecraftClass, tickMethod, "()V"));
                                for (java.util.Map.Entry<Thread, StackTraceElement[]> entry
                                        : Thread.getAllStackTraces().entrySet()) {
                                    for (StackTraceElement frame : entry.getValue()) {
                                        if ("ave".equals(frame.getClassName())
                                                || "net.minecraft.client.Minecraft".equals(frame.getClassName())) {
                                            log("active Minecraft frame: thread="
                                                    + entry.getKey().getName() + " state="
                                                    + entry.getKey().getState() + " frame=" + frame
                                                    + " targetLoader=" + minecraftClass.getClassLoader()
                                                    + " instanceClass=" + Minecraft.getMinecraft().getClass()
                                                    + " sameClass=" + (Minecraft.getMinecraft().getClass()
                                                    == minecraftClass));
                                            break;
                                        }
                                    }
                                }
                            } catch (Throwable error) {
                                log("live method verification FAILED: " + describeFailure(error));
                            }
                        }
                    }, "CrewX Badlion Method Verification");
                    verify.setDaemon(true);
                    verify.start();
                }
                if (result != 0) {
                    dumpDebugBytes(className, original, patched);
                }
            } catch (Throwable error) {
                log("hook FAILED " + className + ": " + error);
            }
        }


        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof net.minecraft.client.gui.GuiMainMenu) {
            CrewXRuntimeHooks.onMainMenuInit(mc.currentScreen);
        }
    }

    private static void verifyHookBridge() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> bridge = Class.forName("crewx.inject.CrewXRuntimeHooks", true, loader);
        bridge.getMethod("onTickPre", Object.class);
        log("tick hook bridge visible to " + describeLoader(loader));
    }


    @SuppressWarnings("unchecked")
    private static void installResources() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            URL location = CrewXBootstrap.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            if ("jar".equals(location.getProtocol())) {
                location = ((java.net.JarURLConnection) location.openConnection()).getJarFileURL();
            }
            log("resource source: " + location);
            File jar = new File(location.toURI());
            if (!jar.isFile()) {
                log("resource pack SKIP: payload is not a JAR");
                return;
            }
            IResourcePack defaultPack = null;
            for (Field field : Minecraft.class.getDeclaredFields()) {
                if (!IResourcePack.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object value = field.get(mc);
                if (value instanceof IResourcePack) {
                    defaultPack = (IResourcePack) value;
                    break;
                }
            }
            if (defaultPack == null) {
                log("resource pack SKIP: default pack field not found");
                return;
            }
            for (Field field : Minecraft.class.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object value = field.get(mc);
                if (!(value instanceof List)) continue;
                List<?> candidate = (List<?>) value;
                if (!candidate.contains(defaultPack)) continue;
                List<IResourcePack> packs = (List<IResourcePack>) candidate;
                for (IResourcePack pack : packs) {
                    if (pack instanceof FileResourcePack && jar.getName().equals(pack.getPackName())) {
                        log("resource pack already present");
                        return;
                    }
                }
                FileResourcePack pack = new FileResourcePack(jar);
                packs.add(pack);
                installedResourcePack = pack;
                installedResourcePacks = packs;
                mc.refreshResources();
                log("registered CrewX resources: " + jar.getName());
                return;
            }
            log("resource pack FAILED: default resource-pack list not found");
        } catch (Throwable error) {
            log("resource pack FAILED: " + describeFailure(error));
        }
    }

    private static void dumpDebugBytes(String className, byte[] original, byte[] patched) {
        try {
            File directory = new File(System.getProperty("java.io.tmpdir"), "CrewX");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            String simple = className.substring(className.lastIndexOf('.') + 1);
            java.nio.file.Files.write(new File(directory, "debug-orig-" + simple + ".class").toPath(),
                    original);
            if (patched != null) {
                java.nio.file.Files.write(new File(directory, "debug-patched-" + simple + ".class").toPath(),
                        patched);
            }
            log("debug bytes dumped for " + simple);
        } catch (Throwable error) {
            log("debug dump FAILED: " + error);
        }
    }

    private static void tryInit(String className) {        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = CrewXBootstrap.class.getClassLoader();
        }
        try {
            Class<?> clazz = Class.forName(className, true, loader);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            log("instantiated " + className);


            invokeIfPresent(instance, "preInit", new Class<?>[]{Object.class}, new Object[]{null});
            invokeIfPresent(instance, "init", new Class<?>[0], new Object[0]);
            log("initialized " + className);
        } catch (Throwable error) {
            log("FAILED " + className + ": " + describeFailure(error));
        }
    }

    static String describeFailure(Throwable error) {
        StringBuilder detail = new StringBuilder();
        int depth = 0;
        for (Throwable current = error; current != null && depth < 4;
             current = current.getCause(), depth++) {
            if (depth > 0) {
                detail.append(" <- ");
            }
            detail.append(current.getClass().getName());
            if (current.getMessage() != null) {
                detail.append(": ").append(current.getMessage());
            }
            StackTraceElement[] frames = current.getStackTrace();
            int shown = 0;
            for (StackTraceElement frame : frames) {
                String location = frame.toString();
                if (location.startsWith("crewx.") || location.startsWith("java.lang.reflect.")) {
                    if (shown > 0) {
                        detail.append(" | ");
                    }
                    detail.append("at ").append(location);
                    if (++shown >= 6) {
                        break;
                    }
                }
            }
        }
        return detail.toString();
    }

    private static void invokeIfPresent(Object instance, String name,
                                        Class<?>[] parameterTypes, Object[] arguments) {
        try {
            Method method = instance.getClass().getMethod(name, parameterTypes);
            method.invoke(instance, arguments);
            log("invoked " + instance.getClass().getName() + "#" + name);
        } catch (NoSuchMethodException missing) {

        } catch (Throwable error) {
            log("FAILED " + instance.getClass().getName() + "#" + name + ": " + describeFailure(error));
        }
    }

    private static boolean isPresent(String className) {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) {
                loader = CrewXBootstrap.class.getClassLoader();
            }
            Class.forName(className, false, loader);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String describeLoader(ClassLoader loader) {
        if (loader == null) {
            return "<bootstrap>";
        }
        return loader.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(loader));
    }






    private static void redirectConsole() {
        try {
            File directory = new File(System.getProperty("java.io.tmpdir"), "CrewX");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            File console = new File(directory, "console.log");
            java.io.PrintStream fileOut = new java.io.PrintStream(
                    new java.io.FileOutputStream(console, true), true, "UTF-8");
            System.setOut(fileOut);
            System.setErr(fileOut);
            fileOut.println("[crewx-inject] console capturado");
        } catch (Throwable ignored) {

        }
    }

    public static void log(String message) {
        String line = "[crewx-inject] " + message;
        System.out.println(line);
        try {
            File directory = new File(System.getProperty("java.io.tmpdir"), "CrewX");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            File logFile = new File(directory, "crewx-inject.log");
            java.nio.file.Files.write(logFile.toPath(),
                    (line + System.lineSeparator()).getBytes("UTF-8"),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Throwable ignored) {

        }
    }
}
