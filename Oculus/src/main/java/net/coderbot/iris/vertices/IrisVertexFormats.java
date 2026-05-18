package net.coderbot.iris.vertices;

import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;

/**
 * Custom vertex formats for Iris shader support.
 * Adapted for 1.12.2 where DefaultVertexFormats has different element naming.
 */
public class IrisVertexFormats {
    // Custom elements for shader data
    public static final VertexFormatElement ENTITY_ELEMENT;
    public static final VertexFormatElement MID_TEXTURE_ELEMENT;
    public static final VertexFormatElement TANGENT_ELEMENT;
    public static final VertexFormatElement MID_BLOCK_ELEMENT;

    // Standard elements from 1.12.2 DefaultVertexFormats
    // Position: 3 floats (12 bytes)
    private static final VertexFormatElement POSITION_3F = new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.POSITION, 3);
    // Color: 4 unsigned bytes (4 bytes)
    private static final VertexFormatElement COLOR_4UB = new VertexFormatElement(0, VertexFormatElement.EnumType.UBYTE, VertexFormatElement.EnumUsage.COLOR, 4);
    // Texture UV: 2 floats (8 bytes)
    private static final VertexFormatElement TEX_2F = new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.UV, 2);
    // Lightmap UV: 2 shorts (4 bytes)
    private static final VertexFormatElement TEX_2S = new VertexFormatElement(1, VertexFormatElement.EnumType.SHORT, VertexFormatElement.EnumUsage.UV, 2);
    // Normal: 3 bytes + 1 padding (4 bytes)
    private static final VertexFormatElement NORMAL_3B = new VertexFormatElement(0, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.NORMAL, 3);
    // Padding element: 1 byte
    private static final VertexFormatElement PADDING_1B = new VertexFormatElement(0, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.PADDING, 1);

    public static final VertexFormat TERRAIN;
    public static final VertexFormat ENTITY;

    static {
        // Custom Iris shader elements
        ENTITY_ELEMENT = new VertexFormatElement(11, VertexFormatElement.EnumType.SHORT, VertexFormatElement.EnumUsage.GENERIC, 2);
        MID_TEXTURE_ELEMENT = new VertexFormatElement(12, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.GENERIC, 2);
        TANGENT_ELEMENT = new VertexFormatElement(13, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.GENERIC, 4);
        MID_BLOCK_ELEMENT = new VertexFormatElement(14, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.GENERIC, 3);

        // Build TERRAIN format manually
        VertexFormat terrainFormat = new VertexFormat();
        terrainFormat.addElement(POSITION_3F);    // 12 bytes
        terrainFormat.addElement(COLOR_4UB);      // 16 bytes
        terrainFormat.addElement(TEX_2F);         // 24 bytes
        terrainFormat.addElement(TEX_2S);         // 28 bytes (lightmap)
        terrainFormat.addElement(NORMAL_3B);      // 31 bytes
        terrainFormat.addElement(PADDING_1B);     // 32 bytes
        terrainFormat.addElement(ENTITY_ELEMENT); // 36 bytes
        terrainFormat.addElement(MID_TEXTURE_ELEMENT); // 44 bytes
        terrainFormat.addElement(TANGENT_ELEMENT);     // 48 bytes
        terrainFormat.addElement(MID_BLOCK_ELEMENT);   // 51 bytes
        terrainFormat.addElement(PADDING_1B);          // 52 bytes
        TERRAIN = terrainFormat;

        // Build ENTITY format manually
        VertexFormat entityFormat = new VertexFormat();
        entityFormat.addElement(POSITION_3F);    // 12 bytes
        entityFormat.addElement(COLOR_4UB);      // 16 bytes
        entityFormat.addElement(TEX_2F);         // 24 bytes
        entityFormat.addElement(new VertexFormatElement(1, VertexFormatElement.EnumType.SHORT, VertexFormatElement.EnumUsage.UV, 2)); // 28 bytes (overlay)
        entityFormat.addElement(TEX_2S);         // 32 bytes (lightmap)
        entityFormat.addElement(NORMAL_3B);      // 35 bytes
        entityFormat.addElement(PADDING_1B);     // 36 bytes
        entityFormat.addElement(MID_TEXTURE_ELEMENT); // 44 bytes
        entityFormat.addElement(TANGENT_ELEMENT);     // 48 bytes
        ENTITY = entityFormat;
    }
}
