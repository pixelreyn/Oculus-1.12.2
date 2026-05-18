package net.coderbot.iris;

import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Arrays;
import java.util.List;

/**
 * Registers Oculus mixin configurations with MixinBooter.
 * Uses ILateMixinLoader - only mixins targeting classes not yet loaded can be included.
 */
public class OculusMixinLoader implements ILateMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        return Arrays.asList(
                "mixins.oculus.json",
                "oculus-batched-entity-rendering.mixins.json",
                "mixins.oculus.compat.sodium.json"
        );
    }
}
