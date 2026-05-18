package net.coderbot.iris.texture.pbr.loader;

import net.coderbot.iris.Iris;
import net.coderbot.iris.texture.pbr.PBRType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

/**
 * Stub implementation of PBR texture loading for simple textures.
 * Adapted for 1.12.2.
 */
public class SimplePBRLoader implements PBRTextureLoader<SimpleTexture> {

    @Override
    public void load(SimpleTexture texture, IResourceManager resourceManager, PBRTextureConsumer pbrTextureConsumer) {
        // Stub: Simple PBR loading not yet fully implemented for 1.12.2
        Iris.logger.debug("SimplePBRLoader: Simple PBR loading not yet implemented for 1.12.2");
    }

    protected AbstractTexture createPBRTexture(ResourceLocation imageLocation, IResourceManager resourceManager, PBRType pbrType) {
        // Stub implementation
        return null;
    }
}
