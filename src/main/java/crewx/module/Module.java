package crewx.module;

import crewx.CrewX;
import crewx.module.modules.render.HUD;
import crewx.module.modules.render.GuiModule;
import crewx.module.modules.render.Notifications;
import crewx.util.KeyBindUtil;
import crewx.util.SoundUtil;

public abstract class Module {
    protected final String name;
    protected final boolean defaultEnabled;
    protected final int defaultKey;
    protected final boolean defaultHidden;
    protected boolean enabled;
    protected int key;
    protected boolean hidden;

    public Module(String name, boolean enabled) {
        this(name, enabled, false);
    }

    public Module(String name, boolean enabled, boolean hidden) {
        this.name = name;
        this.enabled = this.defaultEnabled = enabled;
        this.key = this.defaultKey = 0;
        this.hidden = this.defaultHidden = hidden;
    }

    public String getName() {
        return this.name;
    }

    public ModuleCategory getCategory() {
        return ModuleCategory.fromPackage(this.getClass().getPackage().getName());
    }

    public String formatModule() {
        return String.format(
                "%s%s &r(%s&r)",
                this.key == 0 ? "" : String.format("&l[%s] &r", KeyBindUtil.getKeyName(this.key)),
                this.name,
                this.enabled ? "&a&lON" : "&c&lOFF"
        );
    }

    public String[] getSuffix() {
        return new String[0];
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                this.onEnabled();
            } else {
                this.onDisabled();
            }
            try {
                if (!(this instanceof Notifications) && !(this instanceof GuiModule)) {
                    Notifications.push(enabled ? "Enabled" : "Disabled",
                            this.name + (enabled ? " Toggled" : " Toggled Off"), enabled);
                }
            } catch (Throwable ignored) {}
        }
    }

    public boolean toggle() {
        boolean enabled = !this.enabled;
        this.setEnabled(enabled);
        if (this.enabled == enabled) {
            HUD hud = (HUD) CrewX.moduleManager.modules.get(HUD.class);
            if (hud != null && hud.toggleSound.getValue()) {
                SoundUtil.playToggleSound(this.enabled);
            }
            return true;
        } else {
            return false;
        }
    }

    public int getKey() {
        return this.key;
    }

    public void setKey(int integer) {
        this.key = integer;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public void setHidden(boolean boolean1) {
        this.hidden = boolean1;
    }

    public void onEnabled() {
    }

    public void onDisabled() {
    }

    public void verifyValue(String string) {
    }
}
