package crewx.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.util.ResourceLocation;

public class SoundUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Object TOGGLE_LOCK = new Object();
    private static ISound activeToggleSound;

    public static void playSound(String soundName) {
        SoundHandler soundHandler = mc.getSoundHandler();
        if (soundHandler != null) {
            PositionedSoundRecord positionedSoundRecord = PositionedSoundRecord.create(new ResourceLocation(soundName));
            soundHandler.playSound(positionedSoundRecord);
        }
    }

    public static void playToggleSound(boolean enabled) {
        SoundHandler soundHandler = mc.getSoundHandler();
        if (soundHandler == null) return;
        synchronized (TOGGLE_LOCK) {
            if (activeToggleSound != null) {
                soundHandler.stopSound(activeToggleSound);
            }
            activeToggleSound = PositionedSoundRecord.create(
                    new ResourceLocation(enabled ? "gui.button.press" : "random.click"));
            soundHandler.playSound(activeToggleSound);
        }
    }
}
