package crewx.module.modules.player;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.events.LoadWorldEvent;
import crewx.events.TickEvent;
import crewx.module.Module;
import crewx.property.properties.ModeProperty;
import crewx.property.properties.TextProperty;
import crewx.util.ChatUtil;
import net.minecraft.client.Minecraft;

import java.security.SecureRandom;

public class AutoRegister extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Normal", "Custom"});
    public final TextProperty customPassword = new TextProperty("custom-password", "", () -> mode.getValue() == 1);

    private String password;
    private boolean sendRegister, sendLogin;
    private int tickDelay;

    public AutoRegister() {
        super("AutoRegister", false);
    }

    private void trigger() {
        if (mode.getValue() == 1 && (customPassword.getValue() == null || customPassword.getValue().isEmpty())) {
            ChatUtil.sendFormatted(CrewX.clientName + "&cNo custom password set!");
            return;
        }

        password = mode.getValue() == 1 ? customPassword.getValue() : generatePassword(12);
        sendRegister = true;
        sendLogin = false;
        tickDelay = 0;

        ChatUtil.sendFormatted(CrewX.clientName + "&aPassword: &f" + password);
    }

    @Override
    public void onEnabled() {
        trigger();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        if (!isEnabled()) return;
        trigger();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;

        tickDelay++;

        if (sendRegister && tickDelay >= 2) {
            sendRegister = false;
            mc.thePlayer.sendChatMessage("/register " + password + " " + password);
            sendLogin = true;
            tickDelay = 0;
            return;
        }

        if (sendLogin && tickDelay >= 2) {
            sendLogin = false;
            mc.thePlayer.sendChatMessage("/login " + password);
        }
    }

    private String generatePassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    @Override
    public void onDisabled() {
        password = null;
        sendRegister = false;
        sendLogin = false;
        tickDelay = 0;
    }
}