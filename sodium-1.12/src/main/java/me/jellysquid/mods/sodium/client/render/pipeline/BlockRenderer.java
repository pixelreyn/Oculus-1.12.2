package me.jellysquid.mods.sodium.client.render.pipeline;

import me.jellysquid.mods.sodium.client.model.light.LightMode;
import me.jellysquid.mods.sodium.client.model.light.LightPipeline;
import me.jellysquid.mods.sodium.client.model.light.LightPipelineProvider;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.quad.blender.BiomeColorBlender;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadOrientation;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import me.jellysquid.mods.sodium.client.util.rand.XoRoShiRoRandom;
import me.jellysquid.mods.sodium.client.world.biome.BlockColorsExtended;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Random;

public class BlockRenderer {
    private final Random random = new XoRoShiRoRandom();

    private final BlockColorsExtended blockColors;

    private final QuadLightData cachedQuadLightData = new QuadLightData();

    private final BiomeColorBlender biomeColorBlender;
    private final LightPipelineProvider lighters;

    private final boolean useAmbientOcclusion;

    public BlockRenderer(Minecraft client, LightPipelineProvider lighters, BiomeColorBlender biomeColorBlender) {
        this.blockColors = (BlockColorsExtended) client.getBlockColors();
        this.biomeColorBlender = biomeColorBlender;

        this.lighters = lighters;

        this.useAmbientOcclusion = Minecraft.isAmbientOcclusionEnabled();
    }

    public boolean renderModel(IBlockAccess world, IBlockState state, BlockPos pos, IBakedModel model, ChunkModelBuffers buffers, boolean cull, long seed) {
        LightMode mode = this.getLightingMode(state, model, world, pos);
        LightPipeline lighter = this.lighters.getLighter(mode);
        Vec3d offset = state.getOffset(world, pos);

        boolean rendered = false;

        // Use Sodium's default render path
        
        for (EnumFacing dir : DirectionUtil.ALL_DIRECTIONS) {
            this.random.setSeed(seed);

            List<BakedQuad> sided = model.getQuads(state, dir, this.random.nextLong());

            if (sided.isEmpty()) {
                continue;
            }

            if (!cull || state.shouldSideBeRendered(world, pos, dir)) {
                this.renderQuadList(world, state, pos, lighter, offset, buffers, sided, dir);

                rendered = true;
            }
        }

        this.random.setSeed(seed);

        List<BakedQuad> all = model.getQuads(state, null, this.random.nextLong());

        if (!all.isEmpty()) {
            this.renderQuadList(world, state, pos, lighter, offset, buffers, all, null);

            rendered = true;
        }

        return rendered;
    }

    private void renderQuadList(IBlockAccess world, IBlockState state, BlockPos pos, LightPipeline lighter, Vec3d offset,
                                ChunkModelBuffers buffers, List<BakedQuad> quads, EnumFacing cullFace) {
    	ModelQuadFacing facing = cullFace == null ? ModelQuadFacing.UNASSIGNED : ModelQuadFacing.fromDirection(cullFace);
        IBlockColor colorizer = null;

        ModelVertexSink sink = buffers.getSink(facing);
        sink.ensureCapacity(quads.size() * 4);

        ChunkRenderData.Builder renderData = buffers.getRenderData();

        // This is a very hot allocation, iterate over it manually
        // noinspection ForLoopReplaceableByForEach
        for (int i = 0, quadsSize = quads.size(); i < quadsSize; i++) {
            BakedQuad quad = quads.get(i);

            QuadLightData light = this.cachedQuadLightData;
            // TODO: Does null mean we should treat it as non-axis-aligned?
            EnumFacing quadFace = quad.getFace();
            if (quadFace == null) {
                quadFace = EnumFacing.DOWN;
            }
            lighter.calculate((ModelQuadView) quad, pos, light, cullFace, quadFace, quad.shouldApplyDiffuseLighting());

            if (quad.hasTintIndex() && colorizer == null) {
                if (this.blockColors.hasColorProvider(state)) {
                    colorizer = this.blockColors.getColorProvider(state);
                }
            }

            this.renderQuad(world, state, pos, sink, offset, colorizer, quad, light, renderData);
        }

        sink.flush();
    }

    private void renderQuad(IBlockAccess world, IBlockState state, BlockPos pos, ModelVertexSink sink, Vec3d offset,
                            IBlockColor colorProvider, BakedQuad bakedQuad, QuadLightData light, ChunkRenderData.Builder renderData) {
        ModelQuadView src = (ModelQuadView) bakedQuad;

        ModelQuadOrientation order = ModelQuadOrientation.orient(light.br);

        int[] colors = null;

        if (bakedQuad.hasTintIndex() && colorProvider != null) {
            colors = this.biomeColorBlender.getColors(colorProvider, world, state, pos, src);
        }

        // Compute extended vertex data for shader packs
        short entityId = getEntityId(state);
        float midU = (src.getTexU(0) + src.getTexU(1) + src.getTexU(2) + src.getTexU(3)) * 0.25f;
        float midV = (src.getTexV(0) + src.getTexV(1) + src.getTexV(2) + src.getTexV(3)) * 0.25f;
        int tangent = computeTangent(bakedQuad.getFace());

        for (int dstIndex = 0; dstIndex < 4; dstIndex++) {
            int srcIndex = order.getVertexIndex(dstIndex);

            float x = src.getX(srcIndex) + (float) offset.x;
            float y = src.getY(srcIndex) + (float) offset.y;
            float z = src.getZ(srcIndex) + (float) offset.z;

            int color = ColorABGR.mul(colors != null ? colors[srcIndex] : src.getColor(srcIndex), light.br[srcIndex]);

            float u = src.getTexU(srcIndex);
            float v = src.getTexV(srcIndex);

            int lm = light.lm[srcIndex];

            sink.writeQuadExtended(x, y, z, color, u, v, lm, entityId, midU, midV, tangent);
        }

        TextureAtlasSprite sprite = src.rubidium$getSprite();

        if (sprite != null) {
            renderData.addSprite(sprite);
        }
    }

    // Reflection-based access to Iris/Oculus BlockRenderingSettings to avoid compile-time dependency
    private static final Object BLOCK_RENDERING_SETTINGS_INSTANCE;
    private static final MethodHandle GET_BLOCK_STATE_IDS;

    static {
        Object instance = null;
        MethodHandle getBlockStateIds = null;
        try {
            Class<?> clazz = Class.forName("net.coderbot.iris.block_rendering.BlockRenderingSettings");
            instance = clazz.getField("INSTANCE").get(null);
            getBlockStateIds = MethodHandles.lookup().findVirtual(clazz, "getBlockStateIds",
                    MethodType.methodType(Object2IntMap.class));
        } catch (Throwable ignored) {
            // Iris/Oculus not present
        }
        BLOCK_RENDERING_SETTINGS_INSTANCE = instance;
        GET_BLOCK_STATE_IDS = getBlockStateIds;
    }

    @SuppressWarnings("unchecked")
    static short getEntityId(IBlockState state) {
        if (GET_BLOCK_STATE_IDS == null || BLOCK_RENDERING_SETTINGS_INSTANCE == null) {
            return 0;
        }
        try {
            Object2IntMap<IBlockState> blockStateIds = (Object2IntMap<IBlockState>) GET_BLOCK_STATE_IDS.invoke(BLOCK_RENDERING_SETTINGS_INSTANCE);
            if (blockStateIds == null) {
                return 0;
            }
            if (!blockStateIds.containsKey(state)) {
                return 0;
            }
            int rawId = blockStateIds.getInt(state);
            return (short) rawId;
        } catch (Throwable e) {
            return 0;
        }
    }

    /**
     * Computes a packed tangent vector for the given face direction.
     * The tangent is packed as 4 signed bytes (x, y, z, w) where w is the handedness (+1).
     */
    static int computeTangent(EnumFacing face) {
        // Default tangent vectors per face direction
        int tx, ty, tz;
        switch (face) {
            case UP:
            case DOWN:
                tx = 127; ty = 0; tz = 0;
                break;
            case NORTH:
            case SOUTH:
                tx = 127; ty = 0; tz = 0;
                break;
            case EAST:
            case WEST:
                tx = 0; ty = 0; tz = 127;
                break;
            default:
                tx = 127; ty = 0; tz = 0;
                break;
        }
        // Pack as 4 signed bytes: x, y, z, w (w=127 for +1 handedness)
        return (tx & 0xFF) | ((ty & 0xFF) << 8) | ((tz & 0xFF) << 16) | (127 << 24);
    }

    private LightMode getLightingMode(IBlockState state, IBakedModel model, IBlockAccess world, BlockPos pos) {
        if (this.useAmbientOcclusion && model.isAmbientOcclusion(state) && state.getLightValue(world, pos) == 0) {
            return LightMode.SMOOTH;
        } else {
            return LightMode.FLAT;
        }
    }
}
