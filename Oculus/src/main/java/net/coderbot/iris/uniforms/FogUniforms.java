package net.coderbot.iris.uniforms;

import net.minecraft.client.renderer.GlStateManager;
import net.coderbot.iris.gl.state.GlStateManagerHelper;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.gl.uniform.DynamicUniformHolder;

public class FogUniforms {
    private FogUniforms() {
        // no construction
    }

    public static void addFogUniforms(DynamicUniformHolder uniforms) {
        uniforms.uniform1i("fogMode", () -> {
            GlStateManager.FogState fog = GlStateManagerHelper.getFogState();

            if (!GlStateManagerHelper.isBooleanStateEnabled(fog.fog)) {
                return 0;
            }

            return fog.mode;
        }, listener -> {
            StateUpdateNotifiers.fogToggleNotifier.setListener(listener);
            StateUpdateNotifiers.fogModeNotifier.setListener(listener);
        });

        uniforms.uniform1f("fogDensity", () -> GlStateManagerHelper.getFogState().density, listener -> {
            StateUpdateNotifiers.fogToggleNotifier.setListener(listener);
            StateUpdateNotifiers.fogDensityNotifier.setListener(listener);
        });

        uniforms.uniform1f("fogStart", () -> GlStateManagerHelper.getFogState().start, listener -> {
            StateUpdateNotifiers.fogToggleNotifier.setListener(listener);
            StateUpdateNotifiers.fogStartNotifier.setListener(listener);
        });

        uniforms.uniform1f("fogEnd", () -> GlStateManagerHelper.getFogState().end, listener -> {
            StateUpdateNotifiers.fogToggleNotifier.setListener(listener);
            StateUpdateNotifiers.fogEndNotifier.setListener(listener);
        });
    }
}
