package crewx.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;


public final class DynamicTextures {
    private DynamicTextures() {
    }






    public static void release(ResourceLocation location) {
        if (location == null) return;
        TextureManager manager = Minecraft.getMinecraft().getTextureManager();
        ITextureObject texture = manager.getTexture(location);
        if (texture == null || texture == TextureUtil.missingTexture) return;
        if (texture instanceof AbstractTexture) {
            ((AbstractTexture) texture).deleteGlTexture();
        } else {
            TextureUtil.deleteTexture(texture.getGlTextureId());
        }
        manager.loadTexture(location, TextureUtil.missingTexture);
    }
}
