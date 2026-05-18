package net.coderbot.iris.texture.pbr.loader;

import net.minecraft.client.resources.IResourceManager;

/**
 * Interface for loading PBR textures.
 * Adapted for 1.12.2 resource system.
 *
 * @param <T> The texture type to load PBR data for
 */
public interface PBRTextureLoader<T> {
    /**
     * Loads PBR textures for the given texture object.
     *
     * @param texture The texture to load PBR data for
     * @param resourceManager The resource manager to load resources from
     * @param pbrTextureConsumer Consumer to receive loaded PBR textures
     */
    void load(T texture, IResourceManager resourceManager, PBRTextureConsumer pbrTextureConsumer);

    /**
     * Consumer interface for receiving PBR texture data.
     */
    interface PBRTextureConsumer {
        void acceptNormalTexture(net.minecraft.client.renderer.texture.AbstractTexture texture);
        void acceptSpecularTexture(net.minecraft.client.renderer.texture.AbstractTexture texture);
    }
}
