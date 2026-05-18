package net.coderbot.iris.pipeline;

import net.coderbot.batchedentityrendering.impl.FullyBufferedMultiBufferSource;
import net.coderbot.iris.compat.Camera;
import net.coderbot.iris.vendored.joml.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.util.EnumHand;

/**
 * Handles rendering the player's hand with shader effects.
 * In 1.12.2, this wraps vanilla's hand rendering to inject shader phases.
 */
public class HandRenderer {
    public static final HandRenderer INSTANCE = new HandRenderer();
    public static final float DEPTH = 0.125F;
    private final FullyBufferedMultiBufferSource bufferSource = new FullyBufferedMultiBufferSource();
    private boolean ACTIVE;
    private boolean renderingSolid;

    /**
     * Check if we can render the hand.
     */
    private boolean canRender(Camera camera, EntityRenderer renderer) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.gameSettings.thirdPersonView == 0
                && !mc.gameSettings.hideGUI
                && mc.player != null
                && !mc.player.isPlayerSleeping()
                && !mc.playerController.isSpectator();
    }

    /**
     * Check if the item in the given hand is translucent.
     */
    public boolean isHandTranslucent(EnumHand hand) {
        // In 1.12.2, most items are rendered opaquely
        // This could be refined to check for specific translucent items
        return false;
    }

    /**
     * Check if any hand has a translucent item.
     */
    public boolean isAnyHandTranslucent() {
        return isHandTranslucent(EnumHand.MAIN_HAND) || isHandTranslucent(EnumHand.OFF_HAND);
    }

    /**
     * Render solid hand pass.
     * This sets the rendering phase and invokes vanilla's hand rendering.
     */
    public void renderSolid(Matrix4f modelView, float tickDelta, Camera camera, EntityRenderer renderer, WorldRenderingPipeline pipeline) {
        if (!canRender(camera, renderer)) {
            return;
        }

        ACTIVE = true;
        renderingSolid = true;
        pipeline.setPhase(WorldRenderingPhase.HAND_SOLID);

        // Sync the program for hand rendering
        pipeline.syncProgram();

        // In 1.12.2, hand rendering is handled by vanilla's EntityRenderer during the main
        // render pass. We set the phase here so that if/when a mixin hooks into the hand
        // rendering call, it can apply the correct shader program.
        //
        // Actual hand rendering invocation will be handled by mixin hooks into
        // EntityRenderer.renderHand() or ItemRenderer.renderItemInFirstPerson().
        // For now, the phase is set so syncProgram() activates the correct shader.

        renderingSolid = false;
        pipeline.setPhase(WorldRenderingPhase.NONE);
        pipeline.syncProgram();
        ACTIVE = false;
    }

    /**
     * Render translucent hand pass.
     * This handles translucent items held in hand.
     */
    public void renderTranslucent(Matrix4f modelView, float tickDelta, Camera camera, EntityRenderer renderer, WorldRenderingPipeline pipeline) {
        if (!canRender(camera, renderer) || !isAnyHandTranslucent()) {
            return;
        }

        ACTIVE = true;
        pipeline.setPhase(WorldRenderingPhase.HAND_TRANSLUCENT);
        pipeline.syncProgram();

        // Translucent hand rendering would go here
        // In 1.12.2, most hand items are opaque so this is rarely invoked

        pipeline.setPhase(WorldRenderingPhase.NONE);
        pipeline.syncProgram();
        ACTIVE = false;
    }

    public boolean isActive() {
        return ACTIVE;
    }

    public boolean isRenderingSolid() {
        return renderingSolid;
    }

    public FullyBufferedMultiBufferSource getBufferSource() {
        return bufferSource;
    }
}
