package crewx.mixin;

import crewx.gui.CrewXMainMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;





@SideOnly(Side.CLIENT)
@Mixin(GuiMainMenu.class)
public class MixinGuiMainMenu extends GuiScreen {
    @Inject(method = "initGui", at = @At("RETURN"))
    private void crewx$initGui(CallbackInfo callbackInfo) {

        buttonList.clear();
    }

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
    private void crewx$drawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo callbackInfo) {
        callbackInfo.cancel();
        crewx.gui.CrewXTheme.drawBackground(width, height);
        if (mc.currentScreen == (Object) this) {
            mc.displayGuiScreen(new CrewXMainMenu());
        }
    }
}
