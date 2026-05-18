package net.coderbot.iris.pipeline;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import net.coderbot.iris.Iris;
import net.coderbot.iris.compat.Camera;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.mixin.LevelRendererAccessor;
import net.coderbot.iris.shaderpack.OptionalBoolean;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.shaderpack.PackShadowDirectives;
import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.shadow.ShadowMatrices;
import net.coderbot.iris.shadows.ShadowRenderTargets;
import net.coderbot.iris.shadows.frustum.BoxCuller;
import net.coderbot.iris.shadows.frustum.FrustumHolder;
import net.coderbot.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.coderbot.iris.shadows.frustum.fallback.BoxCullingFrustum;
import net.coderbot.iris.shadows.frustum.fallback.NonCullingFrustum;
import net.coderbot.iris.uniforms.CameraUniforms;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.vendored.joml.Matrix4f;
import net.coderbot.iris.vendored.joml.Vector3d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Handles shadow map rendering for shader packs.
 * Adapted for 1.12.2 - this is a stub implementation that needs to be filled in.
 */
public class ShadowRenderer {

    public static Matrix4f MODELVIEW;
    public static Matrix4f PROJECTION;
    public static List<TileEntity> visibleBlockEntities;
    public static boolean ACTIVE = false;

    private final float halfPlaneLength;
    private final float renderDistanceMultiplier;
    private final float entityShadowDistanceMultiplier;
    private final int resolution;
    private final float intervalSize;
    private final Float fov;
    private final GlFramebuffer shadowFb;
    private final ShadowRenderTargets targets;
    private final OptionalBoolean packCullingState;
    private final boolean shouldRenderTerrain;
    private final boolean shouldRenderTranslucent;
    private final boolean shouldRenderEntities;
    private final boolean shouldRenderPlayer;
    private final boolean shouldRenderBlockEntities;
    private final float sunPathRotation;
    private final List<MipmapPass> mipmapPasses = new ArrayList<>();
    private final String debugStringOverall;
    private boolean packHasVoxelization;
    private FrustumHolder terrainFrustumHolder;
    private FrustumHolder entityFrustumHolder;
    private String debugStringTerrain = "(unavailable)";
    private int renderedShadowEntities = 0;
    private int renderedShadowBlockEntities = 0;
    private Profiler profiler;

    public ShadowRenderer(
        ProgramSource shadow,
        PackDirectives directives,
        ShadowRenderTargets shadowRenderTargets
    ) {
        this.profiler = Minecraft.getMinecraft().profiler;

        final PackShadowDirectives shadowDirectives =
            directives.getShadowDirectives();

        this.halfPlaneLength = shadowDirectives.getDistance();
        this.renderDistanceMultiplier = shadowDirectives.getDistanceRenderMul();
        this.entityShadowDistanceMultiplier =
            shadowDirectives.getEntityShadowDistanceMul();
        this.resolution = shadowDirectives.getResolution();
        this.intervalSize = shadowDirectives.getIntervalSize();
        this.shouldRenderTerrain = shadowDirectives.shouldRenderTerrain();
        this.shouldRenderTranslucent =
            shadowDirectives.shouldRenderTranslucent();
        this.shouldRenderEntities = shadowDirectives.shouldRenderEntities();
        this.shouldRenderPlayer = shadowDirectives.shouldRenderPlayer();
        this.shouldRenderBlockEntities =
            shadowDirectives.shouldRenderBlockEntities();

        debugStringOverall =
            "half plane = " +
            halfPlaneLength +
            " meters @ " +
            resolution +
            "x" +
            resolution;

        this.terrainFrustumHolder = new FrustumHolder();
        this.entityFrustumHolder = new FrustumHolder();

        this.fov = shadowDirectives.getFov();
        this.targets = shadowRenderTargets;
        this.shadowFb = shadowRenderTargets.createFramebufferWritingToMain(new int[]{0});

        if (shadow != null) {
            this.packHasVoxelization = shadow.getGeometrySource().isPresent();
            this.packCullingState = shadowDirectives.getCullingState();
        } else {
            this.packHasVoxelization = false;
            this.packCullingState = OptionalBoolean.DEFAULT;
        }

        this.sunPathRotation = directives.getSunPathRotation();

        configureSamplingSettings(shadowDirectives);
    }

    public static Matrix4f createShadowModelView(
        float sunPathRotation,
        float intervalSize
    ) {
        Vector3d cameraPos = CameraUniforms.getUnshiftedCameraPosition();

        double cameraX = cameraPos.x;
        double cameraY = cameraPos.y;
        double cameraZ = cameraPos.z;

        Matrix4f modelView = new Matrix4f();
        ShadowMatrices.createModelViewMatrix(
            modelView,
            getShadowAngle(),
            intervalSize,
            sunPathRotation,
            cameraX,
            cameraY,
            cameraZ
        );

        return modelView;
    }

    private static WorldClient getLevel() {
        return Minecraft.getMinecraft().world;
    }

    public static float getShadowAngle() {
        float skyAngle = getLevel().getCelestialAngle(
            CapturedRenderingState.INSTANCE.getTickDelta()
        );

        if (skyAngle < 0.75F) {
            return skyAngle + 0.25F;
        } else {
            return skyAngle - 0.75F;
        }
    }

    private void configureSamplingSettings(
        PackShadowDirectives shadowDirectives
    ) {
        // Configure hardware shadow filtering for depth textures
        for (
            int i = 0;
            i < shadowDirectives.getDepthSamplingSettings().size();
            i++
        ) {
            PackShadowDirectives.DepthSamplingSettings settings =
                shadowDirectives.getDepthSamplingSettings().get(i);
            int textureId;
            if (i == 0) {
                textureId = targets.getDepthTexture().getTextureId();
            } else if (i == 1) {
                textureId = targets
                    .getDepthTextureNoTranslucents()
                    .getTextureId();
            } else {
                continue;
            }

            if (settings.getHardwareFiltering()) {
                // Enable hardware shadow comparison
                GlStateManager.bindTexture(textureId);
                GL11.glTexParameteri(
                    GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER,
                    GL11.GL_LINEAR
                );
                GL11.glTexParameteri(
                    GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MAG_FILTER,
                    GL11.GL_LINEAR
                );
                GL11.glTexParameteri(
                    GL11.GL_TEXTURE_2D,
                    GL14.GL_TEXTURE_COMPARE_MODE,
                    GL14.GL_COMPARE_R_TO_TEXTURE
                );
                GlStateManager.bindTexture(0);
            }
        }
    }

    public void renderShadows(
        LevelRendererAccessor levelRenderer,
        Camera playerCamera
    ) {
        ACTIVE = true;

        Minecraft mc = Minecraft.getMinecraft();
        profiler = mc.profiler;

        profiler.startSection("shadow_setup");

        // ===== Step 1: Compute shadow matrices =====
        MODELVIEW = createShadowModelView(sunPathRotation, intervalSize);

        if (fov != null) {
            // Perspective shadow projection
            float[] projData = ShadowMatrices.createPerspectiveMatrix(fov);
            PROJECTION = new Matrix4f().set(projData);
        } else {
            // Orthographic shadow projection
            float[] projData = ShadowMatrices.createOrthoMatrix(
                halfPlaneLength
            );
            PROJECTION = new Matrix4f().set(projData);
        }

        profiler.endStartSection("shadow_bind");

        // ===== Step 2: Bind shadow framebuffer =====
        shadowFb.bind();

        // Set viewport to shadow map resolution
        GlStateManager.viewport(0, 0, resolution, resolution);

        // ===== Step 3: Clear shadow framebuffer =====
        GL11.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);

        // ===== Step 4: Set up GL state for shadow rendering =====
        // Enable depth testing
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);

        // Disable blending for shadow pass
        GlStateManager.disableBlend();

        // NOTE: Do NOT GL-polygon-offset the shadow map. Upstream Iris/Oculus
        // renders the shadow map UNBIASED and lets the shader pack apply its own
        // normal+depth shadow bias (BSL is tuned for an unbiased map). The
        // previous custom glPolygonOffset(1.1, 4.0) fought BSL's bias and pushed
        // large flat surfaces into self-shadow across the whole ortho footprint
        // (vertical geometry had enough depth separation to still look right,
        // which is why buildings shadowed correctly but open ground went dark).
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);

        // Use fixed function for shadow pass (Vintagium uses its own shader for terrain)
        GL20.glUseProgram(0);

        // Enable alpha test so leaves, fences, etc. have proper cutout shadows
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);

        // ===== Step 5: Set up shadow modelview and projection matrices =====
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        // Load shadow projection matrix
        loadJomlMatrix(PROJECTION);

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        // Load shadow modelview matrix
        loadJomlMatrix(MODELVIEW);

        // Update ActiveRenderInfo's cached matrices so that Vintagium's
        // GameRendererContext.getModelViewProjectionMatrix() returns the shadow MVP.
        // Without this, terrain renders from the camera's perspective, not the light's.
        net.minecraft.client.renderer.ActiveRenderInfo.updateRenderInfo(
            mc.getRenderViewEntity(),
            mc.gameSettings.thirdPersonView == 2
        );

        // Also disable backface culling for shadow rendering (prevents light leaking)
        GlStateManager.disableCull();

        // TODO: Entity shadow rendering produces shadows at wrong positions.
        // GL matrices are verified correct (loadJomlMatrix works), but entity
        // renderers appear to use a different transform pipeline. Needs deeper
        // investigation into MC 1.12.2 entity rendering matrix handling.

        // ===== Step 5.5: Rebuild Vintagium chunk visibility for the light =====
        // renderShadows runs at renderWorldPass HEAD, before Sodium's
        // setupTerrain. Without this, Step 6 would draw the *previous frame's
        // camera-frustum* chunk list, so shadows would only exist where the
        // camera recently looked. Rebuild with a shadow-distance box frustum
        // (360deg, occlusion off) so every potential shadow caster is included.
        boolean shadowChunksRebuilt = false;
        if (shouldRenderTerrain) {
            Vector3d shadowCam = CameraUniforms.getUnshiftedCameraPosition();
            float distMul =
                renderDistanceMultiplier > 0.0f ? renderDistanceMultiplier : 1.0f;
            double shadowDistance = Math.max(
                16.0,
                halfPlaneLength * distMul
            );
            shadowChunksRebuilt =
                net.coderbot.iris.compat.sodium.impl.ShadowChunkRenderer.rebuildForShadow(
                    shadowCam.x,
                    shadowCam.y,
                    shadowCam.z,
                    CapturedRenderingState.INSTANCE.getTickDelta(),
                    shadowDistance
                );
        }

        profiler.endStartSection("shadow_terrain");

        // The shadow pass is DEPTH-ONLY. shadowtex0 depth is all the pack needs
        // for basic shadows; writing opaque albedo into shadowcolor0 makes BSL's
        // colored-shadow path project a top-down image of the world as a bright
        // ortho-shaped overlay. Mask colour for both terrain (Step 6) and
        // entities (Step 7); restored before Step 8. (shadowcolor0 stays at the
        // white clear from Step 3 -> neutral tint. Colored translucent shadows
        // are a future feature once shadowtex1 separation exists.)
        GlStateManager.colorMask(false, false, false, false);

        // ===== Step 6: Render terrain into shadow map =====
        // invokeRenderBlockLayer is @Overwrite-redirected by Vintagium's
        // MixinWorldRenderer into SodiumWorldRenderer.drawChunkLayer, which calls
        // backend.begin(); MixinChunkRenderShaderBackend then sees
        // ShadowRenderer.ACTIVE and binds the shadow program override, so chunks
        // are drawn into the bound shadow FBO with the light-space MVP.
        if (shouldRenderTerrain) {
            Entity viewEntity = mc.getRenderViewEntity();
            if (viewEntity != null) {
                float partialTicks =
                    CapturedRenderingState.INSTANCE.getTickDelta();

                // Render solid terrain
                levelRenderer.invokeRenderBlockLayer(
                    BlockRenderLayer.SOLID,
                    partialTicks,
                    2,
                    viewEntity
                );
                levelRenderer.invokeRenderBlockLayer(
                    BlockRenderLayer.CUTOUT_MIPPED,
                    partialTicks,
                    2,
                    viewEntity
                );
                levelRenderer.invokeRenderBlockLayer(
                    BlockRenderLayer.CUTOUT,
                    partialTicks,
                    2,
                    viewEntity
                );

                if (shouldRenderTranslucent) {
                    levelRenderer.invokeRenderBlockLayer(
                        BlockRenderLayer.TRANSLUCENT,
                        partialTicks,
                        2,
                        viewEntity
                    );
                }
            }
            debugStringTerrain = "rendered";
        } else {
            debugStringTerrain = "disabled by shader pack";
        }

        profiler.endStartSection("shadow_entities");

        // ===== Step 7: Render entities into shadow map =====
        renderedShadowEntities = 0;
        renderedShadowBlockEntities = 0;

        if (shouldRenderEntities) {
            float partialTicks = CapturedRenderingState.INSTANCE.getTickDelta();
            WorldClient world = levelRenderer.getWorld();
            RenderManager renderManager = levelRenderer.getRenderManager();

            Entity viewEntity = mc.getRenderViewEntity();

            // The shadow MODELVIEW has NO camera translation baked in: it expects
            // geometry in CAMERA-RELATIVE coords (same basis Sodium feeds terrain).
            // Entities must therefore be rendered relative to the EXACT point
            // createShadowModelView() was built from - the unshifted camera pos -
            // not the view entity's interpolated feet position.
            Vector3d shadowOrigin = CameraUniforms.getUnshiftedCameraPosition();
            double ox = shadowOrigin.x;
            double oy = shadowOrigin.y;
            double oz = shadowOrigin.z;

            // Re-bind shadow FBO and restore shadow state after Vintagium terrain
            shadowFb.bind();
            GlStateManager.viewport(0, 0, resolution, resolution);
            // Entities are drawn fixed-function; bind a GLSL-110 program that
            // applies BSL's shadow-map distortion (same as the terrain fallback)
            // so entity geometry lands in the SAME distorted space BSL samples.
            // Without this, entities are written undistorted -> blobs that don't
            // sit under the entity and slide with the camera.
            int iris$entProg = ensureEntityShadowProgram();
            if (iris$entProg != 0) {
                GL20.glUseProgram(iris$entProg);
                GL20.glUniform1i(
                    GL20.glGetUniformLocation(iris$entProg, "tex"), 0);
            } else {
                GL20.glUseProgram(0);
            }

            // Re-load the proper shadow matrices rather than trying to manually rebuild them.
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            loadJomlMatrix(PROJECTION);

            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();
            loadJomlMatrix(MODELVIEW);

            GlStateManager.enableDepth();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.depthMask(true);
            GlStateManager.disableCull();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);

            // Entities must NOT inherit the terrain depth bias. Step 4 enabled
            // GL_POLYGON_OFFSET_FILL with glPolygonOffset(1.1, 4.0) to fight
            // terrain shadow acne; applied to entities it pushes their depth
            // *behind* the ground, so they never win the shadow occlusion test
            // and cast no shadow (only their shadowcolor0 albedo leaks through).
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GlStateManager.disableBlend();
            // Colour is already masked for the whole depth-only shadow pass
            // (set before Step 6, restored before Step 8).

            if (world != null && renderManager != null) {
                renderManager.setRenderPosition(ox, oy, oz);

                // Also set TileEntityRendererDispatcher position for block entities
                net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.staticPlayerX =
                    ox;
                net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.staticPlayerY =
                    oy;
                net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.staticPlayerZ =
                    oz;

                double shadowDist =
                    halfPlaneLength * entityShadowDistanceMultiplier;
                net.minecraft.client.renderer.RenderHelper.enableStandardItemLighting();

                for (Entity entity : world.loadedEntityList) {
                    if (
                        entity == viewEntity &&
                        mc.gameSettings.thirdPersonView == 0
                    ) continue;
                    if (
                        !shouldRenderPlayer &&
                        entity instanceof
                            net.minecraft.entity.player.EntityPlayer
                    ) continue;
                    if (!entity.shouldRenderInPass(0)) continue;

                    double dx = entity.posX - ox;
                    double dy = entity.posY - oy;
                    double dz = entity.posZ - oz;
                    if (
                        dx * dx + dy * dy + dz * dz > shadowDist * shadowDist
                    ) continue;

                    try {
                        // Call renderEntity directly with explicit camera-relative position
                        // to bypass any potential renderPosX issues
                        double ex =
                            entity.lastTickPosX +
                            (entity.posX - entity.lastTickPosX) * partialTicks;
                        double ey =
                            entity.lastTickPosY +
                            (entity.posY - entity.lastTickPosY) * partialTicks;
                        double ez =
                            entity.lastTickPosZ +
                            (entity.posZ - entity.lastTickPosZ) * partialTicks;
                        float eyaw =
                            entity.prevRotationYaw +
                            (entity.rotationYaw - entity.prevRotationYaw) *
                            partialTicks;
                        boolean prevShadow = renderManager.isRenderShadow();
                        renderManager.setRenderShadow(false);
                        // Re-assert depth write/test: some entity renderers
                        // (overlays, nameplates) toggle these and may not
                        // restore them, which would drop the next entity's
                        // depth from shadowtex0.
                        GlStateManager.enableDepth();
                        GlStateManager.depthMask(true);
                        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
                        renderManager.renderEntity(
                            entity,
                            ex - ox,
                            ey - oy,
                            ez - oz,
                            eyaw,
                            partialTicks,
                            false
                        );
                        renderManager.setRenderShadow(prevShadow);
                        renderedShadowEntities++;
                    } catch (Exception ignored) {}
                }

                net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();

                if (shouldRenderBlockEntities) {
                    net.minecraft.client.renderer.RenderHelper.enableStandardItemLighting();
                    net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher dispatcher =
                        net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance;

                    for (net.minecraft.tileentity.TileEntity te : world.loadedTileEntityList) {
                        net.minecraft.util.math.BlockPos pos = te.getPos();
                        double dx = pos.getX() + 0.5 - ox;
                        double dy = pos.getY() + 0.5 - oy;
                        double dz = pos.getZ() + 0.5 - oz;
                        if (
                            dx * dx + dy * dy + dz * dz >
                            shadowDist * shadowDist
                        ) continue;

                        try {
                            dispatcher.render(te, partialTicks, -1);
                            renderedShadowBlockEntities++;
                        } catch (Exception ignored) {}
                    }

                    net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
                }
            }
        }

        // Unbind the entity shadow distortion program.
        GL20.glUseProgram(0);

        // Re-enable color writes (entities were rendered depth-only).
        GlStateManager.colorMask(true, true, true, true);

        profiler.endStartSection("shadow_cleanup");

        // ===== Step 8: Restore GL state =====
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GlStateManager.enableCull();

        // Restore matrices
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();

        // Restore ActiveRenderInfo's cached matrices to camera matrices
        net.minecraft.client.renderer.ActiveRenderInfo.updateRenderInfo(
            mc.getRenderViewEntity(),
            mc.gameSettings.thirdPersonView == 2
        );

        // Force Sodium to rebuild the camera-frustum chunk list for the main
        // pass; setupTerrain (which runs after this) otherwise skips the
        // rebuild when the player is stationary, leaving our shadow list.
        if (shadowChunksRebuilt) {
            net.coderbot.iris.compat.sodium.impl.ShadowChunkRenderer.markCameraDirty();
        }

        // Generate mipmaps for shadow textures
        for (MipmapPass pass : mipmapPasses) {
            pass.generate();
        }

        // Restore viewport
        GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);

        // Unbind shadow framebuffer
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);

        GL20.glUseProgram(0);

        profiler.endSection();

        ACTIVE = false;
    }

    /**
     * Load a JOML Matrix4f into the current GL matrix.
     */
    private static final java.nio.FloatBuffer SHADOW_MATRIX_BUF =
        org.lwjgl.BufferUtils.createFloatBuffer(16);

    /**
     * Load a JOML Matrix4f directly into the current GL matrix using glLoadMatrix.
     * Uses a static buffer and glLoadMatrix (not glMultMatrix) for reliability.
     */
    private static void loadJomlMatrix(Matrix4f matrix) {
        SHADOW_MATRIX_BUF.clear();
        // Write column-major float array then load directly
        float[] data = new float[16];
        matrix.get(data);
        SHADOW_MATRIX_BUF.put(data);
        SHADOW_MATRIX_BUF.flip();
        GL11.glLoadMatrix(SHADOW_MATRIX_BUF);
    }

    /**
     * Lazily builds a GLSL-110 program (compatibility built-ins so it works with
     * fixed-function entity submission) that applies BSL's exact shadow-map
     * distortion - identical to the terrain fallback in IrisChunkProgramOverrides
     * and to BSL program/shadow.glsl. Bound during the Step 7 entity pass so
     * entities occupy the SAME distorted shadow space BSL samples.
     */
    private static int iris$entityShadowProgram = 0;

    private static int ensureEntityShadowProgram() {
        if (iris$entityShadowProgram != 0) {
            return iris$entityShadowProgram;
        }

        float halfPlaneLength = 256.0f; // BSL default if PROJECTION unset
        try {
            if (PROJECTION != null && Math.abs(PROJECTION.m00()) > 1.0e-6f) {
                halfPlaneLength = 1.0f / PROJECTION.m00();
            }
        } catch (Throwable ignored) {}
        String SMB = Float.toString(1.0f - 25.6f / halfPlaneLength);

        String vsh =
            "#version 110\n" +
            "varying vec2 vTex;\n" +
            "void main() {\n" +
            "    vec4 clip = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "    float d = sqrt(clip.x * clip.x + clip.y * clip.y);\n" +
            "    float f = d * " + SMB + " + (1.0 - " + SMB + ");\n" +
            "    clip.xy *= 1.0 / f;\n" +
            "    clip.z *= 0.2;\n" +
            "    gl_Position = clip;\n" +
            "    vTex = gl_MultiTexCoord0.xy;\n" +
            "    gl_FrontColor = gl_Color;\n" +
            "}\n";
        String fsh =
            "#version 110\n" +
            "uniform sampler2D tex;\n" +
            "varying vec2 vTex;\n" +
            "void main() {\n" +
            "    vec4 c = texture2D(tex, vTex) * gl_Color;\n" +
            "    if (c.a < 0.1) discard;\n" +
            "    gl_FragColor = c;\n" +
            "}\n";

        int v = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(v, vsh);
        GL20.glCompileShader(v);
        int fr = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fr, fsh);
        GL20.glCompileShader(fr);
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, v);
        GL20.glAttachShader(prog, fr);
        GL20.glLinkProgram(prog);

        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            Iris.logger.error(
                "[Shadow] entity shadow program link failed: {} | vsh: {} | fsh: {}",
                GL20.glGetProgramInfoLog(prog, 1024),
                GL20.glGetShaderInfoLog(v, 1024),
                GL20.glGetShaderInfoLog(fr, 1024));
            GL20.glDeleteProgram(prog);
            GL20.glDeleteShader(v);
            GL20.glDeleteShader(fr);
            return 0;
        }

        GL20.glDeleteShader(v);
        GL20.glDeleteShader(fr);
        iris$entityShadowProgram = prog;
        Iris.logger.info(
            "[Shadow] built entity shadow distortion program (bias src hpl={})",
            halfPlaneLength);
        return iris$entityShadowProgram;
    }

    public int getResolution() {
        return resolution;
    }

    public ShadowRenderTargets getRenderTargets() {
        return targets;
    }

    public String getDebugStringOverall() {
        return debugStringOverall;
    }

    public String getDebugStringTerrain() {
        return debugStringTerrain;
    }

    public int getRenderedShadowEntities() {
        return renderedShadowEntities;
    }

    public int getRenderedShadowBlockEntities() {
        return renderedShadowBlockEntities;
    }

    public float getSunPathRotation() {
        return sunPathRotation;
    }

    public float getIntervalSize() {
        return intervalSize;
    }

    public void addMipmapPass(MipmapPass pass) {
        mipmapPasses.add(pass);
    }

    public void destroy() {
        // Cleanup
    }

    public static class MipmapPass {

        private final int texture;
        private final int target;

        public MipmapPass(int texture, int target) {
            this.texture = texture;
            this.target = target;
        }

        public void generate() {
            IrisRenderSystem.generateMipmaps(texture, target);
        }
    }
}
