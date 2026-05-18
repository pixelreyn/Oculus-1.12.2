package net.coderbot.iris;

import com.google.common.collect.ImmutableList;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Registers Oculus mixin configurations with MixinBooter as an EARLY mixin loader.
 *
 * <p>Oculus mixins target core vanilla render classes (RenderGlobal, EntityRenderer,
 * TileEntityRendererDispatcher, RenderManager, Render, BufferBuilder) as well as Sodium's
 * classes. In heavy coremod environments (e.g. RLCraft) other coremod transformers
 * classload these targets during the coremod phase, long before FML mod construction.
 * Loading these configs late (the old {@code ILateMixinLoader} approach) therefore threw
 * {@code MixinTargetAlreadyLoadedException}. Early loading prepares the configs during the
 * coremod phase, before those targets are loaded. This mirrors Vintagium's
 * {@code SodiumMixinTweaker}.
 */
@IFMLLoadingPlugin.Name("Oculus")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class OculusMixinTweaker implements IFMLLoadingPlugin, IEarlyMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        return ImmutableList.of(
                "mixins.oculus.json",
                "mixins.oculus.batched-entity-rendering.json",
                "mixins.oculus.compat.sodium.json"
        );
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {

    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
