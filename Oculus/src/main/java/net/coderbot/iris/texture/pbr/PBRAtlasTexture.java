package net.coderbot.iris.texture.pbr;

import net.coderbot.iris.Iris;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;

/**
 * Stub implementation of PBR atlas texture for 1.12.2.
 * Full implementation requires porting the complex texture stitching logic.
 */
public class PBRAtlasTexture extends AbstractTexture {
    private final TextureMap atlasTexture;
    private final PBRType type;

    public PBRAtlasTexture(TextureMap atlasTexture, PBRType type) {
        this.atlasTexture = atlasTexture;
        this.type = type;
    }

    @Override
    public void loadTexture(IResourceManager resourceManager) {
        // Stub: PBR atlas texture loading not yet implemented
        Iris.logger.debug("PBRAtlasTexture: PBR atlas texture loading not yet implemented for 1.12.2");
    }

    public PBRType getType() {
        return type;
    }

    public TextureMap getAtlasTexture() {
        return atlasTexture;
    }

    /**
     * Updates animation frames for PBR textures.
     * In 1.12.2, this is a stub that does nothing since we don't have full PBR support.
     */
    public void cycleAnimationFrames() {
        // Stub: PBR animation cycling not yet implemented for 1.12.2
    }
}
