package net.coderbot.iris.pipeline;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.compat.Camera;
import net.coderbot.iris.compat.MainRenderTarget;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gbuffer_overrides.matching.RenderCondition;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.gbuffer_overrides.state.RenderTargetStateListener;
import net.coderbot.iris.gl.blending.BlendModeOverride;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.gl.program.ProgramBuilder;
import net.coderbot.iris.gl.program.ProgramImages;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.gl.sampler.SamplerLimits;
import net.coderbot.iris.gl.texture.DepthBufferFormat;
import net.coderbot.iris.mixin.LevelRendererAccessor;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.TransformPatcher;
import net.coderbot.iris.postprocess.BufferFlipper;
import net.coderbot.iris.postprocess.CenterDepthSampler;
import net.coderbot.iris.postprocess.CompositeRenderer;
import net.coderbot.iris.postprocess.FinalPassRenderer;
import net.coderbot.iris.rendertarget.RenderTargets;
import net.coderbot.iris.samplers.IrisImages;
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.shaderpack.*;
import net.coderbot.iris.shaderpack.loading.ProgramId;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import net.coderbot.iris.shadows.ShadowRenderTargets;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.CommonUniforms;
import net.coderbot.iris.uniforms.FrameUpdateNotifier;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.coderbot.iris.gl.state.StateTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.IResourceManager;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Full implementation of DeferredWorldRenderingPipeline for 1.12.2.
 *
 * This creates and manages the entire shader rendering pipeline:
 * - G-buffer render targets for deferred rendering
 * - Shadow map rendering
 * - Gbuffer shader programs for terrain, entities, etc.
 * - Composite and deferred post-processing passes
 * - Final pass compositing to screen
 */
public class DeferredWorldRenderingPipeline implements WorldRenderingPipeline, RenderTargetStateListener {
    // ===== Pack Configuration =====
    private final PackDirectives packDirectives;
    private final float sunPathRotation;
    private final CloudSetting cloudSetting;
    private final boolean shouldRenderUnderwaterOverlay;
    private final boolean shouldRenderVignette;
    private final boolean shouldRenderSun;
    private final boolean shouldRenderMoon;
    private final boolean shouldWriteRainAndSnowToDepthBuffer;
    private final boolean shouldRenderParticlesBeforeDeferred;
    private final boolean allowConcurrentCompute;
    private final OptionalInt forcedShadowRenderDistanceChunks;

    // ===== Rendering Infrastructure =====
    private final FrameUpdateNotifier updateNotifier;
    private final RenderTargets renderTargets;
    private final int ownDepthTextureId;
    private final AbstractTexture whitePixel;
    private final CustomTextureManager customTextureManager;
    private final CenterDepthSampler centerDepthSampler;

    @Nullable
    private final ShadowRenderTargets shadowRenderTargets;
    @Nullable
    private final ShadowRenderer shadowRenderer;
    @Nullable
    private final CompositeRenderer prepareRenderer;
    @Nullable
    private final CompositeRenderer deferredRenderer;
    @Nullable
    private final CompositeRenderer compositeRenderer;
    private final FinalPassRenderer finalPassRenderer;

    // ===== Gbuffer Programs =====
    private final EnumMap<RenderCondition, GbufferPass> gbufferPasses;
    private final GlFramebuffer baseline; // default gbuffer FBO (colortex0 + depth)

    // ===== Buffer Flipping State =====
    private final ImmutableSet<Integer> flippedAfterPrepare;
    private final ImmutableSet<Integer> flippedAfterTranslucent;

    // ===== Runtime State =====
    private WorldRenderingPhase phase = WorldRenderingPhase.NONE;
    private WorldRenderingPhase overridePhase = null;
    private InputAvailability inputs = new InputAvailability(false, false, false);
    private SpecialCondition special = null;
    private int currentNormalTexture = 0;
    private int currentSpecularTexture = 0;
    @Nullable
    private GbufferPass currentPass = null;
    private boolean isMainBound = true;
    @Nullable
    private SodiumTerrainPipeline sodiumTerrainPipeline;
    private boolean isBeforeTranslucent = true;
    private int cachedWidth;
    private int cachedHeight;

    public DeferredWorldRenderingPipeline(ProgramSet programs) {
        Objects.requireNonNull(programs);

        this.packDirectives = programs.getPackDirectives();
        this.updateNotifier = new FrameUpdateNotifier();

        // Extract pack directives
        this.cloudSetting = packDirectives.getCloudSetting();
        this.shouldRenderUnderwaterOverlay = packDirectives.underwaterOverlay();
        this.shouldRenderVignette = packDirectives.vignette();
        this.shouldRenderSun = packDirectives.shouldRenderSun();
        this.shouldRenderMoon = packDirectives.shouldRenderMoon();
        this.shouldWriteRainAndSnowToDepthBuffer = packDirectives.rainDepth();
        this.shouldRenderParticlesBeforeDeferred = packDirectives.areParticlesBeforeDeferred();
        this.allowConcurrentCompute = packDirectives.getConcurrentCompute();
        this.sunPathRotation = packDirectives.getSunPathRotation();

        // Calculate forced shadow render distance
        PackShadowDirectives shadowDirectives = packDirectives.getShadowDirectives();
        if (shadowDirectives.isDistanceRenderMulExplicit()) {
            if (shadowDirectives.getDistanceRenderMul() >= 0.0) {
                forcedShadowRenderDistanceChunks =
                        OptionalInt.of(((int) (shadowDirectives.getDistance() * shadowDirectives.getDistanceRenderMul()) + 15) / 16);
            } else {
                forcedShadowRenderDistanceChunks = OptionalInt.of(-1);
            }
        } else {
            forcedShadowRenderDistanceChunks = OptionalInt.empty();
        }

        // Create white pixel texture for fallback sampling
        this.whitePixel = new WhitePixelTexture();

        // Get screen dimensions
        this.cachedWidth = MainRenderTarget.getWidth();
        this.cachedHeight = MainRenderTarget.getHeight();

        // Create our own depth texture since MC 1.12.2 uses a renderbuffer for depth
        this.ownDepthTextureId = createDepthTexture(cachedWidth, cachedHeight);

        // Create G-buffer render targets
        Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargetSettings =
                packDirectives.getRenderTargetDirectives().getRenderTargetSettings();
        this.renderTargets = new RenderTargets(cachedWidth, cachedHeight,
                ownDepthTextureId, 0, DepthBufferFormat.DEPTH24,
                renderTargetSettings, packDirectives);

        // Create custom textures and noise
        this.customTextureManager = new CustomTextureManager(packDirectives,
                programs.getPack().getCustomTextureDataMap(), programs.getPack().getCustomNoiseTexture());
        IntSupplier noiseTexture = customTextureManager.getNoiseTexture();

        // Create center depth sampler
        this.centerDepthSampler = new CenterDepthSampler(renderTargets::getDepthTexture,
                packDirectives.getCenterDepthHalfLife());

        // Create shadow infrastructure if the pack has shadow programs
        Supplier<ShadowRenderTargets> shadowTargetsSupplier;
        if (programs.getShadow().isPresent()) {
            PackShadowDirectives packShadowDirectives = packDirectives.getShadowDirectives();
            this.shadowRenderTargets = new ShadowRenderTargets(packShadowDirectives.getResolution(), packShadowDirectives);
            this.shadowRenderer = new ShadowRenderer(programs.getShadow().orElse(null),
                    packDirectives, shadowRenderTargets);
            shadowTargetsSupplier = () -> shadowRenderTargets;

            Iris.logger.info("Shadow maps enabled: {}x{}", packShadowDirectives.getResolution(), packShadowDirectives.getResolution());
        } else {
            this.shadowRenderTargets = null;
            this.shadowRenderer = null;
            shadowTargetsSupplier = () -> {
                throw new IllegalStateException("Shadow targets not available");
            };
        }

        // ===== Create Composite Renderers =====
        BufferFlipper bufferFlipper = new BufferFlipper();

        Object2ObjectMap<String, IntSupplier> gbufferCustomTextures =
                customTextureManager.getCustomTextureIdMap(TextureStage.GBUFFERS_AND_SHADOW);
        Object2ObjectMap<String, IntSupplier> compositeCustomTextures =
                customTextureManager.getCustomTextureIdMap(TextureStage.COMPOSITE_AND_FINAL);

        // Prepare passes
        ProgramSource[] prepareSources = programs.getPrepare();
        ComputeSource[][] prepareComputes = programs.getPrepareCompute();
        if (hasValidSource(prepareSources)) {
            this.prepareRenderer = new CompositeRenderer(packDirectives, prepareSources, prepareComputes,
                    renderTargets, noiseTexture, updateNotifier, centerDepthSampler, bufferFlipper,
                    shadowTargetsSupplier, compositeCustomTextures, ImmutableMap.of());
            Iris.logger.info("Prepare passes enabled");
        } else {
            this.prepareRenderer = null;
        }

        this.flippedAfterPrepare = bufferFlipper.snapshot();

        // Create sampler/image factories for Sodium terrain pipeline.
        // The flip supplier is dynamic: during opaque terrain it uses flippedAfterPrepare,
        // during translucent terrain it uses flippedAfterTranslucent (post-deferred).
        // This ensures samplers like gaux1/gaux2/depthtex1 read from the correct textures.
        // 'this' reference needed to access final fields from lambda during construction
        DeferredWorldRenderingPipeline self = this;
        Supplier<ImmutableSet<Integer>> flippedForTerrain = () -> {
            WorldRenderingPhase p = self.getPhase();
            if (p == WorldRenderingPhase.TERRAIN_TRANSLUCENT || p == WorldRenderingPhase.TRIPWIRE
                    || p == WorldRenderingPhase.HAND_TRANSLUCENT) {
                return self.flippedAfterTranslucent;
            }
            return self.flippedAfterPrepare;
        };

        java.util.function.IntFunction<ProgramSamplers> createTerrainSamplers = (programId) -> {
            ProgramSamplers.Builder builder = ProgramSamplers.builder(programId, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
            ProgramSamplers.CustomTextureSamplerInterceptor interceptor =
                    ProgramSamplers.customTextureSamplerInterceptor(builder, gbufferCustomTextures, flippedAfterPrepare);

            IrisSamplers.addRenderTargetSamplers(interceptor, flippedForTerrain, renderTargets, false);
            IrisSamplers.addLevelSamplers(interceptor, this, whitePixel, new InputAvailability(true, true, false));
            IrisSamplers.addWorldDepthSamplers(interceptor, renderTargets);
            IrisSamplers.addNoiseSampler(interceptor, customTextureManager.getNoiseTexture());

            if (IrisSamplers.hasShadowSamplers(interceptor) && shadowRenderTargets != null) {
                IrisSamplers.addShadowSamplers(interceptor, shadowRenderTargets);
            }

            return builder.build();
        };

        java.util.function.IntFunction<ProgramImages> createTerrainImages = (programId) -> {
            ProgramImages.Builder builder = ProgramImages.builder(programId);
            IrisImages.addRenderTargetImages(builder, flippedForTerrain, renderTargets);
            if (IrisImages.hasShadowImages(builder) && shadowRenderTargets != null) {
                IrisImages.addShadowColorImages(builder, shadowRenderTargets);
            }
            return builder.build();
        };

        java.util.function.IntFunction<ProgramSamplers> createShadowTerrainSamplers = shadowRenderer != null ? (programId) -> {
            ProgramSamplers.Builder builder = ProgramSamplers.builder(programId, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
            ProgramSamplers.CustomTextureSamplerInterceptor interceptor =
                    ProgramSamplers.customTextureSamplerInterceptor(builder, gbufferCustomTextures, flippedAfterPrepare);

            IrisSamplers.addRenderTargetSamplers(interceptor, flippedForTerrain, renderTargets, false);
            IrisSamplers.addLevelSamplers(interceptor, this, whitePixel, new InputAvailability(true, true, false));
            IrisSamplers.addNoiseSampler(interceptor, customTextureManager.getNoiseTexture());

            if (IrisSamplers.hasShadowSamplers(interceptor) && shadowRenderTargets != null) {
                IrisSamplers.addShadowSamplers(interceptor, shadowRenderTargets);
            }

            return builder.build();
        } : null;

        java.util.function.IntFunction<ProgramImages> createShadowTerrainImages = shadowRenderer != null ? (programId) -> {
            ProgramImages.Builder builder = ProgramImages.builder(programId);
            IrisImages.addRenderTargetImages(builder, flippedForTerrain, renderTargets);
            if (IrisImages.hasShadowImages(builder) && shadowRenderTargets != null) {
                IrisImages.addShadowColorImages(builder, shadowRenderTargets);
            }
            return builder.build();
        } : null;

        // Create Sodium terrain pipeline with patched gbuffer shaders
        this.sodiumTerrainPipeline = new SodiumTerrainPipeline(this, programs,
                createTerrainSamplers, createShadowTerrainSamplers,
                createTerrainImages, createShadowTerrainImages);

        // Deferred passes
        ProgramSource[] deferredSources = programs.getDeferred();
        ComputeSource[][] deferredComputes = programs.getDeferredCompute();
        if (hasValidSource(deferredSources)) {
            this.deferredRenderer = new CompositeRenderer(packDirectives, deferredSources, deferredComputes,
                    renderTargets, noiseTexture, updateNotifier, centerDepthSampler, bufferFlipper,
                    shadowTargetsSupplier, compositeCustomTextures, ImmutableMap.of());
            Iris.logger.info("Deferred passes enabled");
        } else {
            this.deferredRenderer = null;
        }

        this.flippedAfterTranslucent = bufferFlipper.snapshot();

        // Create gbuffer programs (after both flip states are known)
        this.gbufferPasses = createGbufferPrograms(programs, flippedAfterPrepare, noiseTexture,
                shadowTargetsSupplier, gbufferCustomTextures);

        // Create the baseline gbuffer framebuffer (colortex0 + depth)
        this.baseline = renderTargets.createGbufferFramebuffer(flippedAfterPrepare, new int[]{0});

        // Composite passes
        ProgramSource[] compositeSources = programs.getComposite();
        ComputeSource[][] compositeComputes = programs.getCompositeCompute();
        if (hasValidSource(compositeSources)) {
            this.compositeRenderer = new CompositeRenderer(packDirectives, compositeSources, compositeComputes,
                    renderTargets, noiseTexture, updateNotifier, centerDepthSampler, bufferFlipper,
                    shadowTargetsSupplier, compositeCustomTextures, ImmutableMap.of());
            Iris.logger.info("Composite passes enabled");
        } else {
            this.compositeRenderer = null;
        }

        // Final pass
        ImmutableSet<Integer> flippedAfterComposite = bufferFlipper.snapshot();
        this.finalPassRenderer = new FinalPassRenderer(programs, renderTargets, noiseTexture,
                updateNotifier, flippedAfterComposite, centerDepthSampler,
                shadowTargetsSupplier, compositeCustomTextures,
                compositeRenderer != null ? compositeRenderer.getFlippedAtLeastOnceFinal() : ImmutableSet.of());

        // Configure block rendering settings
        // Populate block state ID map from shader pack's block.properties
        BlockRenderingSettings.INSTANCE.setBlockStateIds(
                net.coderbot.iris.block_rendering.BlockMaterialMapping.createBlockStateIdMap(
                        programs.getPack().getIdMap().getBlockProperties()));
        BlockRenderingSettings.INSTANCE.setBlockTypeIds(
                net.coderbot.iris.block_rendering.BlockMaterialMapping.createBlockTypeMap(
                        programs.getPack().getIdMap().getBlockRenderTypeMap()));
        BlockRenderingSettings.INSTANCE.setEntityIds(programs.getPack().getIdMap().getEntityIdMap());
        BlockRenderingSettings.INSTANCE.setDisableDirectionalShading(shouldDisableDirectionalShading());
        BlockRenderingSettings.INSTANCE.setUseSeparateAo(packDirectives.shouldUseSeparateAo());
        BlockRenderingSettings.INSTANCE.setAmbientOcclusionLevel(packDirectives.getAmbientOcclusionLevel());
        BlockRenderingSettings.INSTANCE.setUseExtendedVertexFormat(true);

        // Initialize state tracker for fog/blend/texture polling
        StateTracker.INSTANCE.init();

        Iris.logger.info("Deferred rendering pipeline initialized successfully.");
    }

    // ===== Gbuffer Program Creation =====

    private EnumMap<RenderCondition, GbufferPass> createGbufferPrograms(ProgramSet programs,
            ImmutableSet<Integer> flipped, IntSupplier noiseTexture,
            Supplier<ShadowRenderTargets> shadowTargetsSupplier,
            Object2ObjectMap<String, IntSupplier> customTextureIds) {

        EnumMap<RenderCondition, GbufferPass> passes = new EnumMap<>(RenderCondition.class);
        ProgramFallbackResolver resolver = new ProgramFallbackResolver(programs);

        // Cache to avoid compiling the same shader source multiple times
        Map<ProgramSource, GbufferPass> cache = new IdentityHashMap<>();

        for (RenderCondition condition : RenderCondition.values()) {
            if (condition == RenderCondition.SHADOW) {
                // Shadow uses a different rendering path
                continue;
            }

            ProgramId id = conditionToProgramId(condition);
            ProgramSource source = resolver.resolveNullable(id);
            if (source == null) {
                continue;
            }

            GbufferPass pass = cache.get(source);
            if (pass == null) {
                try {
                    pass = createGbufferPass(source, flipped, noiseTexture,
                            shadowTargetsSupplier, customTextureIds);
                    cache.put(source, pass);
                } catch (Exception e) {
                    Iris.logger.error("Failed to create gbuffer program for {} (id={}): {}", condition, id, e.getMessage(), e);
                    continue;
                }
            }

            passes.put(condition, pass);
        }

        Iris.logger.info("Created {} unique gbuffer programs for {} render conditions",
                cache.size(), passes.size());
        return passes;
    }

    private GbufferPass createGbufferPass(ProgramSource source, ImmutableSet<Integer> flipped,
            IntSupplier noiseTexture, Supplier<ShadowRenderTargets> shadowTargetsSupplier,
            Object2ObjectMap<String, IntSupplier> customTextureIds) {
        // The most common input availability in 1.12.2: texture + lightmap, no overlay
        InputAvailability availability = new InputAvailability(true, true, false);

        // Patch the shader source for attribute handling
        Map<PatchShaderType, String> transformed = TransformPatcher.patchAttributes(
                source.getVertexSource().orElseThrow(NullPointerException::new),
                source.getGeometrySource().orElse(null),
                source.getFragmentSource().orElseThrow(NullPointerException::new),
                availability);

        String vertex = transformed.get(PatchShaderType.VERTEX);
        String geometry = transformed.get(PatchShaderType.GEOMETRY);
        String fragment = transformed.get(PatchShaderType.FRAGMENT);
        PatchedShaderPrinter.debugPatchedShaders(source.getName(), vertex, geometry, fragment);

        ProgramBuilder builder;
        try {
            builder = ProgramBuilder.begin(source.getName(), vertex, geometry, fragment,
                    IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
        } catch (RuntimeException e) {
            throw new RuntimeException("Shader compilation failed for " + source.getName(), e);
        }

        // Add uniforms
        CommonUniforms.addCommonUniforms(builder, source.getParent().getPack().getIdMap(),
                source.getParent().getPackDirectives(), updateNotifier);

        // Sampler interceptor for custom texture overrides
        ProgramSamplers.CustomTextureSamplerInterceptor interceptor =
                ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flipped);

        // Add level samplers (texture, lightmap, overlay with fallbacks)
        IrisSamplers.addLevelSamplers(interceptor, this, whitePixel, availability);

        // Add render target samplers (colortex4-15 only for gbuffer programs)
        IrisSamplers.addRenderTargetSamplers(interceptor, () -> flipped, renderTargets, false);

        // Add depth samplers
        IrisSamplers.addWorldDepthSamplers(interceptor, renderTargets);

        // Add noise sampler
        IrisSamplers.addNoiseSampler(interceptor, noiseTexture);

        // Add shadow samplers if needed
        if (IrisSamplers.hasShadowSamplers(interceptor)) {
            IrisSamplers.addShadowSamplers(interceptor, shadowTargetsSupplier.get());
            IrisImages.addShadowColorImages(builder, shadowTargetsSupplier.get());
        }

        // Add render target images
        IrisImages.addRenderTargetImages(builder, () -> flipped, renderTargets);

        Program program = builder.build();

        // Create framebuffer for this program's draw buffers
        int[] drawBuffers = source.getDirectives().getDrawBuffers();
        // Create TWO framebuffers: one for before deferred (opaque), one for after (translucent).
        // This matches the original Iris 1.16 architecture where each Pass has both FBOs.
        GlFramebuffer fboBefore = renderTargets.createGbufferFramebuffer(flippedAfterPrepare, drawBuffers);
        GlFramebuffer fboAfter = renderTargets.createGbufferFramebuffer(flippedAfterTranslucent, drawBuffers);

        // Get blend mode override
        BlendModeOverride blendOverride = source.getDirectives().getBlendModeOverride().orElse(null);

        return new GbufferPass(program, fboBefore, fboAfter, drawBuffers, blendOverride);
    }

    // ===== WorldRenderingPipeline Implementation =====

    @Override
    public void beginLevelRendering() {
        // Check for resize
        int newWidth = MainRenderTarget.getWidth();
        int newHeight = MainRenderTarget.getHeight();
        if (newWidth != cachedWidth || newHeight != cachedHeight) {
            cachedWidth = newWidth;
            cachedHeight = newHeight;
            resizeDepthTexture(ownDepthTextureId, newWidth, newHeight);
            renderTargets.resizeIfNeeded(MainRenderTarget.getDepthBufferVersion(),
                    ownDepthTextureId, newWidth, newHeight, DepthBufferFormat.DEPTH24, packDirectives);
        }

        // Bind the baseline gbuffer framebuffer for rendering
        baseline.bind();
        GlStateManager.viewport(0, 0, cachedWidth, cachedHeight);

        // Clear the G-buffer targets if needed
        if (renderTargets.isFullClearRequired()) {
            clearGbufferTargets();
            renderTargets.onFullClear();
        }

        // Clear depth buffer
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        GL20.glUseProgram(0);

        // Advance frame counters for time-based shader uniforms
        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(System.nanoTime());

        updateNotifier.onNewFrame();
        phase = WorldRenderingPhase.NONE;
        overridePhase = null;
        isBeforeTranslucent = true;
        isMainBound = true;
    }

    @Override
    public void renderShadows(LevelRendererAccessor levelRenderer, Camera camera) {
        if (shadowRenderer != null) {
            shadowRenderer.renderShadows(levelRenderer, camera);
        }
    }

    @Override
    public void addDebugText(List<String> messages) {
        messages.add("");
        messages.add("[Iris] Deferred rendering pipeline (1.12.2)");
        if (shadowRenderer != null) {
            messages.add("[Iris] Shadow Maps: " + shadowRenderer.getDebugStringOverall());
            messages.add("[Iris] Shadow Terrain: " + shadowRenderer.getDebugStringTerrain());
            messages.add("[Iris] Shadow Entities: " + shadowRenderer.getRenderedShadowEntities());
            messages.add("[Iris] Shadow Block Entities: " + shadowRenderer.getRenderedShadowBlockEntities());
        } else {
            messages.add("[Iris] Shadow Maps: disabled");
        }
        messages.add("[Iris] Gbuffer programs: " + gbufferPasses.size());
    }

    @Override
    public OptionalInt getForcedShadowRenderDistanceChunksForDisplay() {
        return forcedShadowRenderDistanceChunks;
    }

    @Override
    public WorldRenderingPhase getPhase() {
        if (overridePhase != null) {
            return overridePhase;
        }
        return phase;
    }

    @Override
    public void setPhase(WorldRenderingPhase phase) {
        this.phase = phase;
    }

    @Override
    public void beginSodiumTerrainRendering() {
        // Sodium/Vintagium terrain hooks - not yet implemented
    }

    @Override
    public void endSodiumTerrainRendering() {
        // Sodium/Vintagium terrain hooks - not yet implemented
    }

    @Override
    public void setOverridePhase(WorldRenderingPhase phase) {
        this.overridePhase = phase;
    }

    @Override
    public void setInputs(InputAvailability availability) {
        this.inputs = availability;
    }

    @Override
    public void setSpecialCondition(SpecialCondition special) {
        this.special = special;
    }

    @Override
    public void syncProgram() {
        // Poll GL state for fog/blend/texture changes (replaces mixin-based notifications)
        StateTracker.INSTANCE.poll();

        // NOTE: do NOT capture GL matrices here. syncProgram() runs for every
        // pass including composite/deferred/final (fullscreen ortho) and the
        // shadow pass (shadow ortho). Capturing here clobbered the frame's
        // CAMERA gbufferModelView/gbufferProjection with those wrong matrices,
        // breaking BSL's depth->world reconstruction in composite (only a
        // player-centred blob reconstructs, everything else goes black).
        // gbuffer* must stay the camera matrices captured once per frame by
        // MixinEntityRenderer.iris$captureMatrices (after setupCameraTransform),
        // which is the Iris-correct source of truth.

        RenderCondition condition = getCurrentCondition();
        GbufferPass pass = gbufferPasses.get(condition);

        if (pass == currentPass) {
            // Already bound
            return;
        }

        // Unbind old program
        if (currentPass != null) {
            currentPass.unbind();
        }

        currentPass = pass;

        if (pass != null) {
            pass.bind(isBeforeTranslucent);
            isMainBound = false;
        } else {
            // No shader program for this condition - use fixed function
            GL20.glUseProgram(0);
            baseline.bind();
            isMainBound = true;
        }
    }

    @Override
    public RenderTargetStateListener getRenderTargetStateListener() {
        return this;
    }

    @Override
    public int getCurrentNormalTexture() {
        return currentNormalTexture;
    }

    @Override
    public int getCurrentSpecularTexture() {
        return currentSpecularTexture;
    }

    @Override
    public void onBindTexture(int id) {
        // Track PBR texture bindings (normal and specular maps)
        if (id == 0) {
            currentNormalTexture = 0;
            currentSpecularTexture = 0;
            return;
        }

        try {
            net.coderbot.iris.texture.pbr.PBRTextureHolder holder = PBRTextureManager.INSTANCE.getHolder(id);
            if (holder != null) {
                currentNormalTexture = holder.getNormalTexture().getGlTextureId();
                currentSpecularTexture = holder.getSpecularTexture().getGlTextureId();
            } else {
                currentNormalTexture = 0;
                currentSpecularTexture = 0;
            }
        } catch (Exception e) {
            currentNormalTexture = 0;
            currentSpecularTexture = 0;
        }
    }

    @Override
    public void beginHand() {
        // Copy the depth buffer before hand rendering for depthtex2
        renderTargets.copyPreHandDepth();

        // Set hand phase so syncProgram() binds gbuffers_hand
        phase = WorldRenderingPhase.HAND_SOLID;
        syncProgram();
    }

    @Override
    public void beginTranslucents() {
        // Switch from pre-translucent to post-translucent state.
        // This controls which FBO each GbufferPass uses and which
        // flip state the samplers return.
        isBeforeTranslucent = false;

        // Unbind current gbuffer program
        if (currentPass != null) {
            currentPass.unbind();
            currentPass = null;
        }

        // Copy pre-translucent depth for depthtex1
        renderTargets.copyPreTranslucentDepth();

        // Run deferred passes (lighting from gbuffer data)
        if (deferredRenderer != null) {
            deferredRenderer.renderAll();
        }

        // Note: colortex4 (gaux1) contains cloud distance data written by deferred1.
        // Do NOT clear it here - the water shader needs this data.

        // Restore GL state after deferred passes for block rendering.
        // syncProgram() will be called by the mixin after this returns,
        // binding the correct post-deferred FBO + Water shader.
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();

        isMainBound = false;
    }

    private void runCompositeFinal() {
        if (compositeRenderer != null) {
            compositeRenderer.renderAll();
        }
        centerDepthSampler.sampleCenterDepth();
        finalPassRenderer.renderFinalPass();
    }

    @Override
    public void finalizeLevelRendering() {
        // Unbind current gbuffer program
        if (currentPass != null) {
            currentPass.unbind();
            currentPass = null;
        }

        // Run composite + final passes. These always run here (after all gbuffer
        // rendering including translucent), matching the original Iris 1.16 architecture.
        runCompositeFinal();

        // Reset state
        phase = WorldRenderingPhase.NONE;
        overridePhase = null;
        special = null;

        // Bind MC's framebuffer
        MainRenderTarget.bindWrite(true);
        GL20.glUseProgram(0);
        isMainBound = true;

        // Force-sync GlStateManager with actual GL state.
        // Iris uses raw GL calls that bypass GlStateManager's cache,
        // leaving it stale. MC's subsequent fixed-function rendering
        // (GUI, framebufferRender) relies on the cache being accurate.
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);

        // Clear any GL errors that accumulated during pipeline rendering
        // so MC's "Post render" check doesn't report stale errors
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}

    }

    @Override
    public void destroy() {
        Iris.logger.info("Destroying deferred rendering pipeline");

        // Destroy gbuffer programs
        Set<GbufferPass> destroyed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (GbufferPass pass : gbufferPasses.values()) {
            if (destroyed.add(pass)) {
                pass.destroy();
            }
        }

        // Destroy composite renderers
        if (prepareRenderer != null) prepareRenderer.destroy();
        if (deferredRenderer != null) deferredRenderer.destroy();
        if (compositeRenderer != null) compositeRenderer.destroy();
        finalPassRenderer.destroy();

        // Destroy center depth sampler
        centerDepthSampler.destroy();

        // Destroy shadow infrastructure
        if (shadowRenderer != null) shadowRenderer.destroy();
        if (shadowRenderTargets != null) shadowRenderTargets.destroy();

        // Destroy render targets
        renderTargets.destroy();

        // Destroy custom textures
        customTextureManager.destroy();

        // Destroy own depth texture
        GlStateManager.deleteTexture(ownDepthTextureId);

        // Destroy white pixel texture
        whitePixel.deleteGlTexture();
    }

    @Override
    public SodiumTerrainPipeline getSodiumTerrainPipeline() {
        return sodiumTerrainPipeline;
    }

    @Override
    public FrameUpdateNotifier getFrameUpdateNotifier() {
        return updateNotifier;
    }

    @Override
    public boolean shouldDisableVanillaEntityShadows() {
        return shadowRenderer != null || packDirectives.shouldDisableVanillaEntityShadows();
    }

    @Override
    public boolean shouldDisableDirectionalShading() {
        return packDirectives.shouldDisableDirectionalShading();
    }

    @Override
    public CloudSetting getCloudSetting() {
        return cloudSetting;
    }

    @Override
    public boolean shouldRenderUnderwaterOverlay() {
        return shouldRenderUnderwaterOverlay;
    }

    @Override
    public boolean shouldRenderVignette() {
        return shouldRenderVignette;
    }

    @Override
    public boolean shouldRenderSun() {
        return shouldRenderSun;
    }

    @Override
    public boolean shouldRenderMoon() {
        return shouldRenderMoon;
    }

    @Override
    public boolean shouldWriteRainAndSnowToDepthBuffer() {
        return shouldWriteRainAndSnowToDepthBuffer;
    }

    @Override
    public boolean shouldRenderParticlesBeforeDeferred() {
        return shouldRenderParticlesBeforeDeferred;
    }

    @Override
    public boolean allowConcurrentCompute() {
        return allowConcurrentCompute;
    }

    @Override
    public float getSunPathRotation() {
        return sunPathRotation;
    }

    // ===== RenderTargetStateListener =====

    @Override
    public void beginPostChain() {
        isMainBound = false;
    }

    @Override
    public void endPostChain() {
        isMainBound = true;
    }

    @Override
    public void setIsMainBound(boolean bound) {
        isMainBound = bound;
    }

    // ===== Helper Methods =====

    private RenderCondition getCurrentCondition() {
        if (special != null) {
            switch (special) {
                case ENTITY_EYES: return RenderCondition.ENTITY_EYES;
                case BEACON_BEAM: return RenderCondition.BEACON_BEAM;
                case GLINT: return RenderCondition.GLINT;
            }
        }

        WorldRenderingPhase p = getPhase();
        switch (p) {
            case SKY:
            case SUNSET:
            case SUN:
            case MOON:
            case STARS:
            case VOID:
                return RenderCondition.SKY;
            case TERRAIN_SOLID:
            case TERRAIN_CUTOUT_MIPPED:
            case TERRAIN_CUTOUT:
                return RenderCondition.TERRAIN_OPAQUE;
            case TERRAIN_TRANSLUCENT:
            case TRIPWIRE:
                return RenderCondition.TERRAIN_TRANSLUCENT;
            case CLOUDS:
                return RenderCondition.CLOUDS;
            case RAIN_SNOW:
                return RenderCondition.RAIN_SNOW;
            case ENTITIES:
                return RenderCondition.ENTITIES;
            case BLOCK_ENTITIES:
                return RenderCondition.BLOCK_ENTITIES;
            case DESTROY:
                return RenderCondition.DESTROY;
            case HAND_SOLID:
                return RenderCondition.HAND_OPAQUE;
            case HAND_TRANSLUCENT:
                return RenderCondition.HAND_TRANSLUCENT;
            case WORLD_BORDER:
                return RenderCondition.WORLD_BORDER;
            case PARTICLES:
                // Particles are textured + lit, same as world border
                return RenderCondition.WORLD_BORDER;
            default:
                return RenderCondition.DEFAULT;
        }
    }

    private static ProgramId conditionToProgramId(RenderCondition condition) {
        switch (condition) {
            case DEFAULT: return ProgramId.Basic;
            case SKY: return ProgramId.SkyBasic;
            case TERRAIN_OPAQUE: return ProgramId.Terrain;
            case TERRAIN_TRANSLUCENT: return ProgramId.Water;
            case CLOUDS: return ProgramId.Clouds;
            case DESTROY: return ProgramId.DamagedBlock;
            case BLOCK_ENTITIES: return ProgramId.Block;
            case BEACON_BEAM: return ProgramId.BeaconBeam;
            case ENTITIES: return ProgramId.Entities;
            case ENTITIES_TRANSLUCENT: return ProgramId.EntitiesTrans;
            case GLINT: return ProgramId.ArmorGlint;
            case ENTITY_EYES: return ProgramId.SpiderEyes;
            case HAND_OPAQUE: return ProgramId.Hand;
            case HAND_TRANSLUCENT: return ProgramId.HandWater;
            case RAIN_SNOW: return ProgramId.Weather;
            case WORLD_BORDER: return ProgramId.TexturedLit;
            case SHADOW: return ProgramId.Shadow;
            default: return ProgramId.Basic;
        }
    }

    private static boolean hasValidSource(ProgramSource[] sources) {
        for (ProgramSource source : sources) {
            if (source != null && source.isValid()) {
                return true;
            }
        }
        return false;
    }

    private void clearGbufferTargets() {
        // Clear all render target color buffers to their default values
        for (int i = 0; i < renderTargets.getRenderTargetCount(); i++) {
            int texture = renderTargets.get(i).getMainTexture();
            // Clear by binding a temporary FBO - simplified for now
        }
        // For now, just clear the main color buffer and depth
        float[] clearColor = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    private static int createDepthTexture(int width, int height) {
        int textureId = GlStateManager.generateTexture();
        GlStateManager.bindTexture(textureId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24,
                width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager.bindTexture(0);
        return textureId;
    }

    private static void resizeDepthTexture(int textureId, int width, int height) {
        GlStateManager.bindTexture(textureId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24,
                width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        GlStateManager.bindTexture(0);
    }

    // ===== Inner Classes =====

    /**
     * Holds a compiled gbuffer shader program and its associated framebuffer.
     */
    private static class GbufferPass {
        final Program program;
        final GlFramebuffer framebufferBeforeTranslucents;
        final GlFramebuffer framebufferAfterTranslucents;
        final int[] drawBuffers;
        @Nullable
        final BlendModeOverride blendModeOverride;

        GbufferPass(Program program, GlFramebuffer fboBefore, GlFramebuffer fboAfter,
                    int[] drawBuffers, @Nullable BlendModeOverride blendModeOverride) {
            this.program = program;
            this.framebufferBeforeTranslucents = fboBefore;
            this.framebufferAfterTranslucents = fboAfter;
            this.drawBuffers = drawBuffers;
            this.blendModeOverride = blendModeOverride;
        }

        void bind(boolean isBeforeTranslucent) {
            if (isBeforeTranslucent) {
                framebufferBeforeTranslucents.bind();
            } else {
                framebufferAfterTranslucents.bind();
            }
            program.use();
            if (blendModeOverride != null) {
                blendModeOverride.apply();
            }
        }

        void unbind() {
            Program.unbind();
            if (blendModeOverride != null) {
                BlendModeOverride.restore();
            }
        }

        void destroy() {
            program.destroy();
        }
    }

    /**
     * Simple 1x1 white pixel texture for fallback sampling.
     */
    private static class WhitePixelTexture extends AbstractTexture {
        WhitePixelTexture() {
            int id = getGlTextureId();
            GlStateManager.bindTexture(id);
            ByteBuffer pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
            pixel.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF);
            pixel.flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 1, 1, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GlStateManager.bindTexture(0);
        }

        @Override
        public void loadTexture(IResourceManager resourceManager) {
            // Already loaded in constructor
        }
    }
}
