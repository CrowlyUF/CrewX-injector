package crewx.inject;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;


public final class ForgeCompat {
    private static final boolean PRESENT = classPresent("net.minecraftforge.common.MinecraftForge");
    private static volatile Method attackHook;
    private static volatile Field lightPipeline;

    private ForgeCompat() {}

    private static boolean classPresent(String name) {
        try {
            Class.forName(name, false, ForgeCompat.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isPresent() {
        return PRESENT;
    }

    public static boolean allowPlayerAttack(EntityPlayer player, Entity target) {
        if (!PRESENT) return true;
        try {
            Method method = attackHook;
            if (method == null) {
                method = Class.forName("net.minecraftforge.common.ForgeHooks", false,
                        ForgeCompat.class.getClassLoader())
                        .getMethod("onPlayerAttackTarget", EntityPlayer.class, Entity.class);
                attackHook = method;
            }
            return (Boolean) method.invoke(null, player, target);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setLightPipeline(boolean enabled) {
        if (!PRESENT) return;
        try {
            Field field = lightPipeline;
            if (field == null) {
                field = Class.forName("net.minecraftforge.common.ForgeModContainer", false,
                        ForgeCompat.class.getClassLoader())
                        .getField("forgeLightPipelineEnabled");
                lightPipeline = field;
            }
            field.setBoolean(null, enabled);
        } catch (Throwable ignored) {

        }
    }
}
