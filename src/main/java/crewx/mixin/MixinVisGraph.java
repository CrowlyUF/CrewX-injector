package crewx.mixin;

import crewx.CrewX;
import crewx.module.modules.render.Chams;
import crewx.module.modules.render.ViewClip;
import crewx.module.modules.render.Xray;
import net.minecraft.client.renderer.chunk.SetVisibility;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = {VisGraph.class}, priority = 9999)
public abstract class MixinVisGraph {

    @Unique
    private static boolean shouldOverrideVisibility() {
        if (CrewX.moduleManager == null) return false;
        return CrewX.moduleManager.modules.get(Chams.class).isEnabled()
                || CrewX.moduleManager.modules.get(ViewClip.class).isEnabled()
                || CrewX.moduleManager.modules.get(Xray.class).isEnabled();
    }

    @Inject(
            method = {"func_178606_a"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void func_178606_a(CallbackInfo callbackInfo) {
        if (shouldOverrideVisibility()) {
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = {"computeVisibility"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void computeVisibility(CallbackInfoReturnable<SetVisibility> callbackInfoReturnable) {
        if (shouldOverrideVisibility()) {
            SetVisibility setVisibility = new SetVisibility();
            setVisibility.setAllVisible(true);
            callbackInfoReturnable.setReturnValue(setVisibility);
        }
    }
}