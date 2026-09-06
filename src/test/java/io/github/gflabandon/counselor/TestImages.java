package io.github.gflabandon.counselor;
public final class TestImages {
    public static byte[] png() {
        try { var output = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
            return output.toByteArray();
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
