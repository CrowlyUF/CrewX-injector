package crewx.accountmanager;

import crewx.accountmanager.utils.Nan0EventRegister;
import net.minecraftforge.common.MinecraftForge;


final class ForgeBinding {
    private ForgeBinding() {}

    static void register() {
        Nan0EventRegister.register(MinecraftForge.EVENT_BUS, new Events());
    }
}
