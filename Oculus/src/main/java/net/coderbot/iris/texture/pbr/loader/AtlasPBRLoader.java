package net.coderbot.iris.texture.pbr.loader;

import net.coderbot.iris.Iris;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;

/**
 * Stub implementation of PBR texture loading for texture atlases.
 * Full PBR atlas support requires significant porting work for 1.12.2's
 * different texture atlas system.
 *
 * TODO: Implement actual PBR atlas loading for 1.12.2
 */
public class AtlasPBRLoader implements PBRTextureLoader<TextureMap> {

    @Override
    public void load(TextureMap atlas, IResourceManager resourceManager, PBRTextureConsumer pbrTextureConsumer) {
        // Stub: PBR atlas loading not yet implemented for 1.12.2
        // This would need to:
        // 1. Iterate through all sprites in the atlas
        // 2. Look for _n and _s suffixed textures
        // 3. Load and stitch them into a PBR atlas
        // 4. Call pbrTextureConsumer with the texture IDs

        Iris.logger.debug("AtlasPBRLoader: PBR atlas loading not yet implemented for 1.12.2");
    }
}
