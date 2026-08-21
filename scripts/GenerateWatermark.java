// Regenerates src/main/resources/report-assets/ushirika-logo-watermark.png -- the pre-faded,
// pre-scaled XLSX letterhead watermark that XlsxBuilder reads as raw bytes at runtime (no AWT
// involved in the request path; see XlsxBuilder.WATERMARK_RESOURCE for why).
//
// Run only when the source logo changes. Requires a local JDK (not the app's own build):
//   javac scripts/GenerateWatermark.java -d scripts
//   java -cp scripts GenerateWatermark src/main/resources/report-assets/ushirika-logo.png src/main/resources/report-assets/ushirika-logo-watermark.png

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

public class GenerateWatermark {
    private static final float WATERMARK_OPACITY = 0.10f;
    private static final int WATERMARK_TARGET_PX = 260;

    public static void main(String[] args) throws Exception {
        BufferedImage src = ImageIO.read(new File(args[0]));

        double scale = Math.min(
                (double) WATERMARK_TARGET_PX / src.getWidth(),
                (double) WATERMARK_TARGET_PX / src.getHeight());
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));

        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        BufferedImage faded = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = scaled.getRGB(x, y);
                int alpha = argb >>> 24;
                int fadedAlpha = (int) Math.round(alpha * WATERMARK_OPACITY);
                faded.setRGB(x, y, (fadedAlpha << 24) | (argb & 0x00FFFFFF));
            }
        }

        ImageIO.write(faded, "png", new File(args[1]));
        System.out.println("Wrote " + args[1] + " (" + w + "x" + h + ")");
    }
}
