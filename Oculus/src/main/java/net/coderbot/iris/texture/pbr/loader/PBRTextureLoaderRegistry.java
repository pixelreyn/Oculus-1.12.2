package net.coderbot.iris.texture.pbr.loader;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Registry for PBR texture loaders.
 * Adapted for 1.12.2 texture classes.
 */
public class PBRTextureLoaderRegistry {
    public static final PBRTextureLoaderRegistry INSTANCE = new PBRTextureLoaderRegistry();

    private final Map<Class<? extends AbstractTexture>, PBRTextureLoader<?>> loaders = new IdentityHashMap<>();

    private PBRTextureLoaderRegistry() {
        // Register default loaders
        register(TextureMap.class, new AtlasPBRLoader());
        register(SimpleTexture.class, new SimplePBRLoader());
    }

    public <T extends AbstractTexture> void register(Class<T> clazz, PBRTextureLoader<T> loader) {
        loaders.put(clazz, loader);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T extends AbstractTexture> PBRTextureLoader<T> getLoader(Class<T> clazz) {
        return (PBRTextureLoader<T>) loaders.get(clazz);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T extends AbstractTexture> PBRTextureLoader<T> getLoader(T texture) {
        // First try exact class match
        PBRTextureLoader<?> loader = loaders.get(texture.getClass());
        if (loader != null) {
            return (PBRTextureLoader<T>) loader;
        }

        // Then try superclass matches
        for (Map.Entry<Class<? extends AbstractTexture>, PBRTextureLoader<?>> entry : loaders.entrySet()) {
            if (entry.getKey().isInstance(texture)) {
                return (PBRTextureLoader<T>) entry.getValue();
            }
        }

        return null;
    }
}
