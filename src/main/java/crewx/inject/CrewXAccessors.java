package crewx.inject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
























public final class CrewXAccessors {

    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<String, Field>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<String, Method>();

    private CrewXAccessors() {
    }

    private static Class<?> gameClass(String name) throws ClassNotFoundException {
        ClassLoader loader = CrewXAccessors.class.getClassLoader();
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        return Class.forName(NotchMappings.className(name), false, loader);
    }

    private static Field field(String owner, String name) {
        String key = owner + "#" + name;
        Field cached = FIELDS.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> current = gameClass(owner);
            while (current != null) {
                try {
                    Field found = current.getDeclaredField(NotchMappings.fieldName(owner, name));
                    found.setAccessible(true);
                    FIELDS.put(key, found);
                    return found;
                } catch (NoSuchFieldException next) {
                    current = current.getSuperclass();
                }
            }
        } catch (Throwable error) {
            CrewXBootstrap.log("accessor field MISS " + key + ": " + error);
        }
        return null;
    }

    private static Method method(String owner, String name, Class<?>... parameters) {
        StringBuilder keyBuilder = new StringBuilder(owner).append('#').append(name);
        for (Class<?> parameter : parameters) {
            keyBuilder.append(':').append(parameter.getName());
        }
        String key = keyBuilder.toString();
        Method cached = METHODS.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> current = gameClass(owner);
            while (current != null) {
                try {
                    for (Method found : current.getDeclaredMethods()) {
                        if (!NotchMappings.matchesMethod(name, found)
                                || !java.util.Arrays.equals(found.getParameterTypes(), parameters)) {
                            continue;
                        }
                        found.setAccessible(true);
                        METHODS.put(key, found);
                        return found;
                    }
                    current = current.getSuperclass();
                } catch (SecurityException next) {
                    current = current.getSuperclass();
                }
            }
        } catch (Throwable error) {
            CrewXBootstrap.log("accessor method MISS " + key + ": " + error);
        }
        return null;
    }

    private static Object readField(String owner, String name, Object target) {
        try {
            Field found = field(owner, name);
            if (found == null) {
                return null;
            }
            return found.get(target);
        } catch (Throwable error) {
            CrewXBootstrap.log("accessor read FAIL " + owner + "#" + name + ": " + error);
            return null;
        }
    }

    private static void writeField(String owner, String name, Object target, Object value) {
        try {
            Field found = field(owner, name);
            if (found != null) {
                found.set(target, value);
            }
        } catch (Throwable error) {
            CrewXBootstrap.log("accessor write FAIL " + owner + "#" + name + ": " + error);
        }
    }

    private static Object invoke(String owner, String name, Object target, Object... arguments) {
        try {
            Class<?>[] parameters = new Class<?>[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                parameters[i] = toPrimitive(arguments[i]);
            }
            Method found = method(owner, name, parameters);
            if (found == null && arguments.length > 0) {
                found = method(owner, name, toObjects(arguments));
            }
            if (found == null) {
                return null;
            }
            return found.invoke(target, arguments);
        } catch (Throwable error) {
            CrewXBootstrap.log("accessor invoke FAIL " + owner + "#" + name + ": " + error);
            return null;
        }
    }

    private static Class<?> toPrimitive(Object value) {
        if (value instanceof Boolean) {
            return Boolean.TYPE;
        }
        if (value instanceof Integer) {
            return Integer.TYPE;
        }
        if (value instanceof Float) {
            return Float.TYPE;
        }
        if (value instanceof Double) {
            return Double.TYPE;
        }
        if (value instanceof Long) {
            return Long.TYPE;
        }
        if (value instanceof Short) {
            return Short.TYPE;
        }
        if (value instanceof Byte) {
            return Byte.TYPE;
        }
        if (value instanceof Character) {
            return Character.TYPE;
        }
        return value == null ? Object.class : value.getClass();
    }

    private static Class<?>[] toObjects(Object[] arguments) {
        Class<?>[] parameters = new Class<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            parameters[i] = arguments[i] == null ? Object.class : arguments[i].getClass();
        }
        return parameters;
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static float floatValue(Object value) {
        return value instanceof Number ? ((Number) value).floatValue() : 0.0F;
    }

    private static double doubleValue(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }





    public static void setOnGround(Object target, boolean onGround) {
        writeField("net.minecraft.network.play.client.C03PacketPlayer", "onGround", target, onGround);
    }





    public static int getWindowId(Object target) {
        return intValue(readField("net.minecraft.network.play.client.C0DPacketCloseWindow",
                "windowId", target));
    }





    public static Object callGetVectorForRotation(Object target, float yaw, float pitch) {
        return invoke("net.minecraft.entity.Entity", "getVectorForRotation", target, yaw, pitch);
    }

    public static boolean getIsInWeb(Object target) {
        return booleanValue(readField("net.minecraft.entity.Entity", "isInWeb", target));
    }


    public static Object getItemInUse(Object target) {
        return readField("net.minecraft.entity.player.EntityPlayer", "itemInUse", target);
    }

    public static void setItemInUse(Object target, Object item) {
        writeField("net.minecraft.entity.player.EntityPlayer", "itemInUse", target, item);
    }

    public static int getItemInUseCount(Object target) {
        return intValue(readField("net.minecraft.entity.player.EntityPlayer", "itemInUseCount", target));
    }

    public static void setItemInUseCount(Object target, int count) {
        writeField("net.minecraft.entity.player.EntityPlayer", "itemInUseCount", target, count);
    }





    public static Object getActivePotionsMap(Object target) {
        return readField("net.minecraft.entity.EntityLivingBase", "activePotionsMap", target);
    }

    public static Object getSprintingSpeedBoostModifier(Object target) {
        return readField("net.minecraft.entity.EntityLivingBase",
                "sprintingSpeedBoostModifier", target);
    }

    public static int getJumpTicks(Object target) {
        return intValue(readField("net.minecraft.entity.EntityLivingBase", "jumpTicks", target));
    }

    public static void setJumpTicks(Object target, int ticks) {
        writeField("net.minecraft.entity.EntityLivingBase", "jumpTicks", target, ticks);
    }





    public static void callSetupCameraTransform(Object target, float partialTicks, int pass) {
        invoke("net.minecraft.client.renderer.EntityRenderer", "setupCameraTransform",
                target, partialTicks, pass);
    }





    public static Object getInputField(Object target) {
        return readField("net.minecraft.client.gui.GuiChat", "inputField", target);
    }





    public static void callMouseClicked(Object target, int x, int y, int button) {
        invoke("net.minecraft.client.gui.GuiScreen", "mouseClicked", target, x, y, button);
    }





    public static Object getMaterial(Object target) {
        return readField("net.minecraft.item.ItemSword", "toolMaterial", target);
    }





    public static void setPressed(Object target, boolean pressed) {
        writeField("net.minecraft.client.settings.KeyBinding", "pressed", target, pressed);
    }





    public static Object getLogger(Object target) {
        Object logger = readField("net.minecraft.client.Minecraft", "logger", target);
        return logger != null ? logger : org.apache.logging.log4j.LogManager.getLogger("CrewX");
    }

    public static Object getTimer(Object target) {
        return readField("net.minecraft.client.Minecraft", "timer", target);
    }

    public static int getRightClickDelayTimer(Object target) {
        return intValue(readField("net.minecraft.client.Minecraft",
                "rightClickDelayTimer", target));
    }

    public static void setRightClickDelayTimer(Object target, int delay) {
        writeField("net.minecraft.client.Minecraft", "rightClickDelayTimer", target, delay);
    }

    public static void callRightClickMouse(Object target) {
        invoke("net.minecraft.client.Minecraft", "rightClickMouse", target);
    }





    public static float getCurBlockDamageMP(Object target) {
        return floatValue(readField("net.minecraft.client.multiplayer.PlayerControllerMP",
                "curBlockDamageMP", target));
    }

    public static void setCurBlockDamageMP(Object target, float damage) {
        writeField("net.minecraft.client.multiplayer.PlayerControllerMP",
                "curBlockDamageMP", target, damage);
        CrewXAudit.mark("speedmine-damage-write");
    }

    public static int getBlockHitDelay(Object target) {
        return intValue(readField("net.minecraft.client.multiplayer.PlayerControllerMP",
                "blockHitDelay", target));
    }

    public static void setBlockHitDelay(Object target, int delay) {
        writeField("net.minecraft.client.multiplayer.PlayerControllerMP",
                "blockHitDelay", target, delay);
        CrewXAudit.mark("speedmine-delay-write");
    }

    public static boolean getIsHittingBlock(Object target) {
        return booleanValue(readField("net.minecraft.client.multiplayer.PlayerControllerMP",
                "isHittingBlock", target));
    }

    public static int getCurrentPlayerItem(Object target) {
        return intValue(readField("net.minecraft.client.multiplayer.PlayerControllerMP",
                "currentPlayerItem", target));
    }

    public static void setCurrentPlayerItem(Object target, int slot) {
        writeField("net.minecraft.client.multiplayer.PlayerControllerMP",
                "currentPlayerItem", target, slot);
    }

    public static void callSyncCurrentPlayItem(Object target) {
        invoke("net.minecraft.client.multiplayer.PlayerControllerMP",
                "syncCurrentPlayItem", target);
    }





    public static double getRenderPosX(Object target) {
        return doubleValue(readField("net.minecraft.client.renderer.entity.RenderManager",
                "renderPosX", target));
    }

    public static double getRenderPosY(Object target) {
        return doubleValue(readField("net.minecraft.client.renderer.entity.RenderManager",
                "renderPosY", target));
    }

    public static double getRenderPosZ(Object target) {
        return doubleValue(readField("net.minecraft.client.renderer.entity.RenderManager",
                "renderPosZ", target));
    }
}
