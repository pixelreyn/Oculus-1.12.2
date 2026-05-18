package net.coderbot.iris.pipeline;

import net.coderbot.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Loader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Static class that deals with printing the patched_shader folder.
 * Adapted for 1.12.2 Forge.
 */
public class PatchedShaderPrinter {
    // In 1.12.2, check if we're in a dev environment differently
    public static final boolean prettyPrintShaders = true; // Force-enabled for debugging
    private static boolean outputLocationCleared = false;
    private static int programCounter = 0;

    private static boolean isProduction() {
        // In 1.12.2, check if we're in a dev environment by checking for deobfuscated names
        try {
            // In production, Minecraft class is obfuscated
            // In dev environment, the source class exists
            return !net.minecraftforge.fml.relauncher.FMLLaunchHandler.isDeobfuscatedEnvironment();
        } catch (Throwable e) {
            return true;
        }
    }

    private static Path getGameDir() {
        return Minecraft.getMinecraft().gameDir.toPath();
    }

    public static void resetPrintState() {
        outputLocationCleared = false;
        programCounter = 0;
    }

    public static void debugPatchedShaders(String name, String vertex, String geometry, String fragment) {
        if (prettyPrintShaders) {
            final Path debugOutDir = getGameDir().resolve("patched_shaders");
            if (!outputLocationCleared) {
                try {
                    if (Files.exists(debugOutDir)) {
                        try (Stream<Path> stream = Files.list(debugOutDir)) {
                            stream.forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        }
                    }

                    Files.createDirectories(debugOutDir);
                } catch (IOException e) {
                    Iris.logger.warn("Failed to initialize debug patched shader source location", e);
                }
                outputLocationCleared = true;
            }

            try {
                programCounter++;
                String prefix = String.format("%03d_", programCounter);
                if (vertex != null) {
                    Files.write(debugOutDir.resolve(prefix + name + ".vsh"), vertex.getBytes(StandardCharsets.UTF_8));
                }
                if (geometry != null) {
                    Files.write(debugOutDir.resolve(prefix + name + ".gsh"), geometry.getBytes(StandardCharsets.UTF_8));
                }
                if (fragment != null) {
                    Files.write(debugOutDir.resolve(prefix + name + ".fsh"), fragment.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                Iris.logger.warn("Failed to write debug patched shader source", e);
            }
        }
    }
}
