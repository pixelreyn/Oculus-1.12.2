package net.coderbot.iris.apiimpl;

import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.FixedFunctionWorldRenderingPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.coderbot.iris.vertices.IrisTextVertexSinkImpl;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisApiConfig;
import net.irisshaders.iris.api.v0.IrisTextVertexSink;

import java.nio.ByteBuffer;
import java.util.function.IntFunction;

public class IrisApiV0Impl implements IrisApi {
    public static final IrisApiV0Impl INSTANCE = new IrisApiV0Impl();
    private static final IrisApiV0ConfigImpl CONFIG = new IrisApiV0ConfigImpl();

    @Override
    public int getMinorApiRevision() {
        return 1;
    }

    @Override
    public boolean isShaderPackInUse() {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

        if (pipeline == null) {
            return false;
        }

        return !(pipeline instanceof FixedFunctionWorldRenderingPipeline);
    }

    @Override
    public boolean isRenderingShadowPass() {
        return ShadowRenderingState.areShadowsCurrentlyBeingRendered();
    }

    @Override
    public Object openMainIrisScreenObj(Object parent) {
        // GUI not available in 1.12.2 port yet
        return null;
    }

    @Override
    public String getMainScreenLanguageKey() {
        return "options.iris.shaderPackSelection";
    }

    @Override
    public IrisApiConfig getConfig() {
        return CONFIG;
    }

    @Override
    public IrisTextVertexSink createTextVertexSink(int maxQuadCount, IntFunction<ByteBuffer> bufferProvider) {
        return new IrisTextVertexSinkImpl(maxQuadCount, bufferProvider);
    }
}
