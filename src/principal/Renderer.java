package principal;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.*;
import javafx.scene.paint.Color;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Renderer class for noise visualization.
 * Handles drawing of 1D and 2D noise with color palettes.
 */
class Renderer {

    /** Size of each cell in pixels */
    protected int PIXEL_SIZE;

    /** Default color for drawing */
    private final Color defaultColor;
    /** Background color for clearing areas */
    private final Color backGroundColor;
    /** JavaFX graphics context for drawing */
    private final GraphicsContext gc;
    /** Canvas width in pixels */
    private final int width;
    /** Canvas height in pixels */
    private final int height;
    /** Number of cells horizontally */
    private final int cellWidth;
    /** Number of cells vertically */
    private final int cellHeight;
    /** Writable image for efficient pixel manipulation */
    private final WritableImage estadosImage;
    /** Pixel writer for direct pixel access */
    private final PixelWriter pixelWriter;


    /** Flag indicating if continuous iteration is active */
    private boolean runningIteration = false;
    /** Flag indicating if single step mode is active */
    private boolean runningStep = false;
    /** Reusable buffer for pixel data to avoid allocations */
    private byte[] blockBuffer = new byte[PIXEL_SIZE * PIXEL_SIZE * 4];

    private final Color[] palette = new Color[256];

    /**
     * Creates a new Renderer for noise visualization.
     * 
     * @param gc              the graphics context to draw on (must not be null)
     * @param w               canvas width in pixels (must be positive)
     * @param h               canvas height in pixels (must be positive)
     * @param pPIXEL_SIZE     pixel size for each cell
     * @param barsColor       color for drawing
     * @param backGroundColor color for the background
     * @throws IllegalArgumentException if gc is null or dimensions are invalid
     */
    public Renderer(GraphicsContext gc, int w, int h, int pPIXEL_SIZE, Color barsColor, Color backGroundColor) {
        PIXEL_SIZE = pPIXEL_SIZE;
        blockBuffer = new byte[PIXEL_SIZE * PIXEL_SIZE * 4];
        if (gc == null) {
            throw new IllegalArgumentException("GraphicsContext cannot be null");
        }
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }

        this.defaultColor = barsColor;
        this.backGroundColor = backGroundColor;
        this.gc = gc;
        this.width = w;
        this.height = h;
        this.cellWidth = width / PIXEL_SIZE;
        this.cellHeight = height / PIXEL_SIZE;

        // Create writable image and pixel writer for efficient drawing
        estadosImage = new WritableImage(width, height);
        pixelWriter = estadosImage.getPixelWriter();

    }

    public void initializePaletteGray() {
        for (int i = 0; i < palette.length; i++) {
            double gray = (double) i / (double) palette.length;
            Color color = new Color(gray, gray, gray, 1);
            palette[i] = color;
        }
    }

    /**
     * Initializes the palette with terrain-like colors.
     * <p>
     * Creates a color gradient that represents different terrain types based on
     * elevation:
     * <ul>
     * <li>Low values: Deep blue to blue (water/ocean)</li>
     * <li>Medium-low values: Yellow (coast/beach)</li>
     * <li>Medium values: Brown (land/forest)</li>
     * <li>Medium-high values: Yellow/brown (higher terrain)</li>
     * <li>High values: Grey (rock/mountain)</li>
     * <li>Very high values: White (snow caps)</li>
     * </ul>
     * </p>
     */
    public void initializePaletteColor() {
          for (int i = 0; i < palette.length; i++) {
            double value = (double) i / (palette.length - 1);

            final float DARK_BLUE = 0.3F;
            final float BLUE = 0.4F;
            final float YELLOW = 0.5F;
            final float BROWN = 0.6F;
            final float GREEN = 0.7F;
            final float GREY = 0.8F;

            // Define color components for different terrain types
            if (value < DARK_BLUE) {
                // Deep water (dark blue)
                double factor = value / DARK_BLUE;
                palette[i] = new Color(0, 0, Math.max(0, Math.min(1, 0.5 + 0.5 * factor)), 1);
            } else if (value < BLUE) {
                // Water (blue)
                double factor = (value - DARK_BLUE) / 0.1;
                palette[i] = new Color(0, Math.max(0, Math.min(1, 0.2 * factor)), Math.max(0, Math.min(1, 0.8 + 0.2 * (1 - factor))), 1);
            } else if (value < YELLOW) {
                // Coast (yellow)
                double factor = (value - BLUE) / 0.1;
                palette[i] = new Color(Math.max(0, Math.min(1, 0.8 + 0.2 * factor)), Math.max(0, Math.min(1, 0.8 + 0.2 * factor)), Math.max(0, Math.min(1, 0.2 * (1 - factor))), 1);
            } else if (value < BROWN) {
                // Land (brown)
                double factor = (value - YELLOW) / 0.2;
                palette[i] = new Color(Math.max(0, Math.min(1, 0.5 + 0.3 * factor)), Math.max(0, Math.min(1, 0.4 + 0.2 * factor)), Math.max(0, Math.min(1, 0.2 * (1 - factor))), 1);
            } else if (value < GREEN) {
                // Higher terrain (green)
                double factor = (value - BROWN) / 0.2;
                palette[i] = new Color(Math.max(0, Math.min(1, 0.2 + 0.2 * factor)), Math.max(0, Math.min(1, 0.8 + 0.2 * factor)), Math.max(0, Math.min(1, 0.1 * (1 - factor))), 1);
            } else if (value < GREY) {
                // Mountains (grey)
                double factor = (value - GREEN) / 0.2;
                palette[i] = new Color(Math.max(0, Math.min(1, 0.4 + 0.4 * factor)), Math.max(0, Math.min(1, 0.4 + 0.4 * factor)), Math.max(0, Math.min(1, 0.4 + 0.4 * factor)), 1);
            } else {
                // Snow (white)
                double factor = (value - GREY) / 0.1;
                palette[i] = new Color(Math.max(0, Math.min(1, 0.7 + 0.3 * factor)), Math.max(0, Math.min(1, 0.7 + 0.3 * factor)), Math.max(0, Math.min(1, 0.7 + 0.3 * factor)), 1);
            }
        }
    }

    public void drawPixels(byte[][] cells) {
        for (int a = 0; a < cellHeight; a++)
            for (int b = 0; b < cellWidth; b++) {
                // Convert byte to unsigned int (0-255 range) to use as palette index
                int paletteIndex = cells[a][b] & 0xFF;
                pixelToScreen(b, a, palette[paletteIndex]);
            }
        gc.drawImage(estadosImage, 0, 0);
    }

    /**
     * Clears the entire canvas and resets the image.
     * Sets all pixels to transparent and fills the background.
     */
    public void clearImage() {
        pixelWriter.setPixels(0, 0, width, height, PixelFormat.getByteBgraInstance(), new byte[width * height * 4], 0,
                width * 4);
        gc.fillRect(0, 0, width, height);
        gc.drawImage(estadosImage, 0, 0);
        System.out.println("Image Drawn");
    }

    /**
     * Updates the visual representation by processing one animation frame.
     * Only processes if iteration or step mode is active.
     */
    public void updateRegion() {
        if (!runningIteration && !runningStep)
            return;
        iteration();
        runningStep = false;
        gc.drawImage(estadosImage, 0, 0);
    }

    /**
     * Processes one iteration of the animation queue.
     * Executes the current action and removes it when complete.
     */
    public void iteration() {

    }

    /**
     * Clears a rectangular area by filling it with the background color.
     * 
     * @param x       horizontal position in cells
     * @param y       vertical position in cells
     * @param rwidth  width in cells
     * @param rheight height in cells
     */
    protected void clearRectangle(int x, int y, int rwidth, int rheight) {

        WritablePixelFormat<ByteBuffer> pixelFormat = PixelFormat.getByteBgraInstance();
        for (int cy = Math.max(0, y); cy < Math.min(cellHeight, y + rheight); cy++) {
            for (int cx = Math.max(0, x); cx < Math.min(cellWidth, x + rwidth); cx++) {
                int screenX = cx * PIXEL_SIZE;
                int screenY = cy * PIXEL_SIZE;

                for (int py = 0; py < PIXEL_SIZE; py++) {
                    for (int px = 0; px < PIXEL_SIZE; px++) {
                        int pos = (py * PIXEL_SIZE + px) * 4;

                        blockBuffer[pos] = (byte) (backGroundColor.getBlue() * 255); // Blue - inner color
                        blockBuffer[pos + 1] = (byte) (backGroundColor.getGreen() * 255); // Green
                        blockBuffer[pos + 2] = (byte) (backGroundColor.getRed() * 255); // Red
                        blockBuffer[pos + 3] = (byte) 255;
                    }
                }

                if (screenX + PIXEL_SIZE <= width && screenY + PIXEL_SIZE <= height) {
                    pixelWriter.setPixels(screenX, screenY, PIXEL_SIZE, PIXEL_SIZE, pixelFormat, blockBuffer, 0,
                            PIXEL_SIZE * 4);
                }
            }
        }
    }

    protected void pixelToScreen(int x, int y, Color color) {
        rectangleToScreen(x, y, 1, 1, false, false, color);
    }

    protected void rectangleToScreen(int x, int y, int rwidth, int rheight, boolean hasFrame, boolean hasSpace) {
        rectangleToScreen(x, y, rwidth, rheight, hasFrame, hasSpace, defaultColor);
    }

    /**
     * Draws a rectangle to the screen with specified color and styling options.
     * 
     * @param x        horizontal position in cells
     * @param y        vertical position in cells
     * @param rwidth   width in cells
     * @param rheight  height in cells
     * @param hasFrame whether to draw a frame around the rectangle
     * @param hasSpace whether to leave spacing around the rectangle
     * @param color    the color to use for drawing
     */
    protected void rectangleToScreen(int x, int y, int rwidth, int rheight, boolean hasFrame, boolean hasSpace,
            Color color) {
        WritablePixelFormat<ByteBuffer> pixelFormat = PixelFormat.getByteBgraInstance();

        for (int cy = Math.max(0, y); cy < Math.min(cellHeight, y + rheight); cy++) {
            for (int cx = Math.max(0, x); cx < Math.min(cellWidth, x + rwidth); cx++) {
                int screenX = cx * PIXEL_SIZE;
                int screenY = cy * PIXEL_SIZE;

                boolean leftBorder = (cx == x);
                boolean rightBorder = (cx == x + rwidth - 1);
                boolean topBorder = (cy == y);
                boolean bottomBorder = (cy == y + rheight - 1);

                // Clear the entire block first
                // The Alpha is 0 is not going to draw
                Arrays.fill(blockBuffer, (byte) 0);

                int startPx = (hasSpace && leftBorder) ? 1 : 0;
                // int endPx = (hasSpace && rightBorder) ? PIXEL_SIZE - 1 : PIXEL_SIZE;
                int endPx = PIXEL_SIZE;
                int startPy = 0;
                int endPy = (hasSpace && bottomBorder) ? PIXEL_SIZE - 1 : PIXEL_SIZE;

                for (int py = startPy; py < endPy; py++) {
                    for (int px = startPx; px < endPx; px++) {
                        int pos = (py * PIXEL_SIZE + px) * 4;

                        if (!(hasFrame &&
                                ((leftBorder && px == startPx) ||
                                        (rightBorder && px == endPx - 1) ||
                                        (topBorder && py == startPy) ||
                                        (bottomBorder && py == endPy - 1)))) {
                            blockBuffer[pos] = (byte) (color.getBlue() * 255); // Blue - inner color
                            blockBuffer[pos + 1] = (byte) (color.getGreen() * 255); // Green
                            blockBuffer[pos + 2] = (byte) (color.getRed() * 255); // Red
                        }
                        blockBuffer[pos + 3] = (byte) 255;
                    }
                }

                if (screenX + PIXEL_SIZE <= width && screenY + PIXEL_SIZE <= height) {
                    pixelWriter.setPixels(screenX, screenY, PIXEL_SIZE, PIXEL_SIZE,
                            pixelFormat, blockBuffer, 0, PIXEL_SIZE * 4);
                }
            }
        }
    }

    /**
     * Restarts the visualization by stopping any ongoing animation.
     * Resets the animation state to allow for a fresh start.
     */
    public void restart() {

        runningIteration = false;
    }

    /**
     * Activates continuous iteration mode.
     * When active, the visualization will continuously process animation actions.
     */
    public void iterate() {
        runningIteration = true;
    }

    /**
     * Stops the continuous iteration mode.
     * When stopped, the visualization will pause and wait for manual control.
     */
    public void stopIteration() {
        runningIteration = false;
    }

    /**
     * Performs a single animation step.
     * Useful for debugging or educational purposes to observe each operation
     * individually.
     */
    public void step() {
        runningStep = true;
    }

    /**
     * Creates an inverted version of the given color.
     * 
     * @param color the original color
     * @return a new color with inverted RGB values
     */
    private Color invertColor(Color color) {
        return new Color(1 - color.getRed(), 1 - color.getGreen(), 1 - color.getBlue(), color.getOpacity());
    }

}