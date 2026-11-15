package omok3d;

import java.net.URL;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * Optional image assets. Drop PNG files into src/main/resources/images/ with the names below
 * and they are used automatically; if a file is missing, a built-in drawing is used instead.
 * See src/main/resources/images/README.md for the full list and recommended sizes.
 */
final class Assets {
    private Assets() {}

    static final String BACKGROUND = "background.png";
    static final String BOARD_TOP = "board_top.png";
    static final String BOARD_SIDE = "board_side.png";
    static final String STONE_BLACK = "stone_black.png";
    static final String STONE_WHITE = "stone_white.png";

    /** Loads images/<name> from the resources folder, or returns null if it does not exist. */
    static Image image(String name) {
        URL url = Assets.class.getResource("/images/" + name);
        return url == null ? null : new Image(url.toExternalForm());
    }

    /** URL string of images/<name> for use in CSS, or null if it does not exist. */
    static String url(String name) {
        URL url = Assets.class.getResource("/images/" + name);
        return url == null ? null : url.toExternalForm();
    }

    /** Built-in fallback: a subtle horizontal wood grain around the given base color. */
    static Image woodTexture(Color base, int size) {
        WritableImage img = new WritableImage(size, size);
        PixelWriter pw = img.getPixelWriter();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double wave = Math.sin(y * 0.11 + Math.sin(x * 0.013) * 3.0);
                double fine = Math.sin(y * 0.9 + Math.sin(x * 0.05) * 2.0);
                double k = 1.0 + 0.025 * wave + 0.01 * fine;
                pw.setColor(x, y, Color.color(clamp(base.getRed() * k), clamp(base.getGreen() * k), clamp(base.getBlue() * k)));
            }
        }
        return img;
    }

    /** Built-in: a square soft shadow (solid core, blurred edge) used under the board. */
    static Image softShadow(int size) {
        WritableImage img = new WritableImage(size, size);
        PixelWriter pw = img.getPixelWriter();
        double center = size / 2.0, core = size * 0.34, sigma = size * 0.08;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = Math.max(Math.abs(x - center) - core, 0);
                double dy = Math.max(Math.abs(y - center) - core, 0);
                double alpha = 0.30 * Math.exp(-(dx * dx + dy * dy) / (2 * sigma * sigma));
                pw.setColor(x, y, Color.color(0.50, 0.38, 0.26, alpha));
            }
        }
        return img;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
