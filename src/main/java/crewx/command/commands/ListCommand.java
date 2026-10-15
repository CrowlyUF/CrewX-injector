package crewx.command.commands;

import crewx.CrewX;
import crewx.command.Command;
import crewx.module.Module;
import crewx.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;

public class ListCommand extends Command {
    public ListCommand() {
        super(new ArrayList<>(Arrays.asList("list", "l", "modules", "crewx", "crewx")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (!CrewX.moduleManager.modules.isEmpty()) {
            ChatUtil.sendFormatted(String.format("%sModules:&r", CrewX.clientName));
            for (Module module : CrewX.moduleManager.modules.values()) {
                ChatUtil.sendFormatted(String.format("%s»&r %s&r", module.isHidden() ? "&8" : "&7", module.formatModule()));
            }
        }
    }
}