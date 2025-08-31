package principal;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.*;

import java.nio.ByteBuffer;

/**
 * Renderer class for noise visualization.
 * Handles drawing of 1D and 2D noise with color palettes.
 */
class Renderer {

    /**
     * Real pixels occupied by each cell. It is the only conversion between the logical
     * grid, where the noise is calculated, and the screen.
     */
    static final int PIXELS_PER_CELL = 4;

    /** Colours a palette can hold: one per unsigned byte value. */
    private static final int PALETTE_SIZE = 256;

    /** Bytes needed by one cell: its pixels, four bytes each. */
    private static final int CELL_BUFFER_SIZE = PIXELS_PER_CELL * PIXELS_PER_CELL * 4;

    /** JavaFX graphics context for drawing */
    private final GraphicsContext gc;
    /** Canvas width in real pixels. */
    private final int width;
    /** Canvas height in real pixels. */
    private final int height;
    /** Width of the logical grid, in cells (columns). */
    private final int widthInCells;
    /** Height of the logical grid, in cells (rows). */
    private final int heightInCells;
    /** Writable image for efficient pixel manipulation */
    private final WritableImage estadosImage;
    /** Pixel writer for direct pixel access */
    private final PixelWriter pixelWriter;

    /** Shared pixel format instance to avoid repeated allocation */
    private static final WritablePixelFormat<ByteBuffer> PIXEL_FORMAT = PixelFormat.getByteBgraInstance();

    /** Flag indicating if continuous iteration is active */
    private boolean runningIteration = false;
    /** Flag indicating if single step mode is active */
    private boolean runningStep = false;
    /**
     * Action executed once per animated frame. It is provided by the application
     * because advancing the world requires the noise generators and the offsets,
     * which do not belong to the renderer.
     */
    private Runnable iterationAction;
    /** Reusable buffer holding the pixels of one cell, filled by {@link #fillCellBuffer(byte[])} */
    private final byte[] cellBuffer;
    /** Palette as byte arrays (4 bytes each) for direct buffer writes */
    private final byte[][] paletteBytes;

    /**
     * Creates a new Renderer for noise visualization.
     * 
     * @param gc the graphics context to draw on (must not be null)
     * @param w  canvas width in real pixels (must be positive)
     * @param h  canvas height in real pixels (must be positive)
     * @throws IllegalArgumentException if gc is null or dimensions are invalid
     */
    public Renderer(GraphicsContext gc, int w, int h) {
        if (gc == null) {
            throw new IllegalArgumentException("GraphicsContext cannot be null");
        }
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }

        this.gc = gc;
        this.width = w;
        this.height = h;
        this.widthInCells = width / PIXELS_PER_CELL;
        this.heightInCells = height / PIXELS_PER_CELL;

        this.cellBuffer = new byte[CELL_BUFFER_SIZE];
        this.paletteBytes = new byte[PALETTE_SIZE][4];

        // Create writable image and pixel writer for efficient drawing
        this.estadosImage = new WritableImage(width, height);
        this.pixelWriter = estadosImage.getPixelWriter();
    }

    /**
     * Width of the logical grid in cells.
     *
     * @return number of columns, in cells
     */
    public int widthInCells() {
        return widthInCells;
    }

    /**
     * Height of the logical grid in cells.
     *
     * @return number of rows, in cells
     */
    public int heightInCells() {
        return heightInCells;
    }

    /**
     * Scales a 0..1 colour component to the 0..255 range, clamping it.
     *
     * @param component colour component, normally in the range 0..1
     * @return the component scaled to 0..255
     */
    private static int toByte(double component) {
        return (int) Math.max(0, Math.min(255, component * 255));
    }

    /**
     * Writes one opaque colour, given as red/green/blue, into a BGRA buffer.
     *
     * @param target buffer that receives the colour
     * @param offset byte offset of the pixel
     * @param red    red component, 0..255
     * @param green  green component, 0..255
     * @param blue   blue component, 0..255
     */
    private static void putRgb(byte[] target, int offset, int red, int green, int blue) {
        target[offset] = (byte) blue;
        target[offset + 1] = (byte) green;
        target[offset + 2] = (byte) red;
        target[offset + 3] = (byte) 255;
    }

    /**
     * Copies one colour, already in B, G, R, A order, into a BGRA buffer.
     *
     * @param target buffer that receives the colour
     * @param offset byte offset of the pixel
     * @param bgra   colour to copy
     */
    private static void putBgra(byte[] target, int offset, byte[] bgra) {
        target[offset] = bgra[0];
        target[offset + 1] = bgra[1];
        target[offset + 2] = bgra[2];
        target[offset + 3] = bgra[3];
    }

    /**
     * Registers the action executed once per animated frame.
     * <p>
     * Without an action the Iterate and Step modes are no-ops, so the owner of the
     * noise generation is expected to provide one right after construction.
     * </p>
     *
     * @param action the per-frame action, may be null to disable animation
     */
    public void setIterationAction(Runnable action) {
        this.iterationAction = action;
    }

    /**
     * Initializes the palette with a black to white ramp.
     */
    public void initializePaletteGray() {
        for (int i = 0; i < PALETTE_SIZE; i++) {
            int gray = (int) ((double) i / (PALETTE_SIZE - 1) * 255);
            putRgb(paletteBytes[i], 0, gray, gray, gray);
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
        final float DARK_BLUE = 0.3F;
        final float BLUE = 0.4F;
        final float YELLOW = 0.5F;
        final float BROWN = 0.6F;
        final float GREEN = 0.7F;
        final float GREY = 0.8F;

        for (int i = 0; i < PALETTE_SIZE; i++) {
            double value = (double) i / (PALETTE_SIZE - 1);

            int r, g, b;

            // Define color components for different terrain types
            if (value < DARK_BLUE) {
                // Deep water (dark blue)
                double factor = value / DARK_BLUE;
                b = toByte(0.5 + 0.5 * factor);
                r = 0;
                g = 0;
            } else if (value < BLUE) {
                // Water (blue)
                double factor = (value - DARK_BLUE) / 0.1;
                r = 0;
                g = toByte(0.2 * factor);
                b = toByte(0.8 + 0.2 * (1 - factor));
            } else if (value < YELLOW) {
                // Coast (yellow)
                double factor = (value - BLUE) / 0.1;
                r = toByte(0.8 + 0.2 * factor);
                g = toByte(0.8 + 0.2 * factor);
                b = toByte(0.2 * (1 - factor));
            } else if (value < BROWN) {
                // Land (brown)
                double factor = (value - YELLOW) / 0.2;
                r = toByte(0.5 + 0.3 * factor);
                g = toByte(0.4 + 0.2 * factor);
                b = toByte(0.2 * (1 - factor));
            } else if (value < GREEN) {
                // Higher terrain (green)
                double factor = (value - BROWN) / 0.2;
                r = toByte(0.2 + 0.2 * factor);
                g = toByte(0.8 + 0.2 * factor);
                b = toByte(0.1 * (1 - factor));
            } else if (value < GREY) {
                // Mountains (grey)
                double factor = (value - GREEN) / 0.2;
                r = toByte(0.4 + 0.4 * factor);
                g = toByte(0.4 + 0.4 * factor);
                b = toByte(0.4 + 0.4 * factor);
            } else {
                // Snow (white)
                double factor = (value - GREY) / 0.1;
                r = toByte(0.7 + 0.3 * factor);
                g = toByte(0.7 + 0.3 * factor);
                b = toByte(0.7 + 0.3 * factor);
            }

            putRgb(paletteBytes[i], 0, r, g, b);
        }
    }

    /**
     * Paints a whole grid of palette indexes, one cell per entry.
     *
     * @param cells palette index per cell, addressed as {@code cells[row][column]}
     */
    public void drawCells(byte[][] cells) {
        for (int row = 0; row < heightInCells; row++) {
            for (int column = 0; column < widthInCells; column++) {
                // Bytes are signed: mask them to use the value as a palette index.
                fillCellBuffer(paletteBytes[Byte.toUnsignedInt(cells[row][column])]);
                drawCell(column, row);
            }
        }
        gc.drawImage(estadosImage, 0, 0);
    }

    /**
     * Copies the reusable cell buffer to the screen at the given cell position.
     *
     * @param column cell column
     * @param row    cell row
     */
    private void drawCell(int column, int row) {
        int screenX = column * PIXELS_PER_CELL;
        int screenY = row * PIXELS_PER_CELL;
        if (screenX + PIXELS_PER_CELL <= width && screenY + PIXELS_PER_CELL <= height) {
            pixelWriter.setPixels(screenX, screenY, PIXELS_PER_CELL, PIXELS_PER_CELL,
                    PIXEL_FORMAT, cellBuffer, 0, PIXELS_PER_CELL * 4);
        }
    }

    /**
     * Clears the entire canvas and resets the image.
     * Sets all pixels to transparent and fills the background.
     */
    public void clearImage() {
        // A fresh byte array is already all zeros, that is, fully transparent.
        byte[] transparentRow = new byte[width * 4];
        for (int y = 0; y < height; y++) {
            pixelWriter.setPixels(0, y, width, 1, PIXEL_FORMAT, transparentRow, 0, width * 4);
        }
        gc.fillRect(0, 0, width, height);
        gc.drawImage(estadosImage, 0, 0);
        System.out.println("Image Drawn");
    }

    /**
     * Updates the visual representation by processing one animation frame.
     * Only processes if iteration or step mode is active. The drawing itself is
     * done by the iteration action, so no extra repaint is needed here.
     */
    public void updateRegion() {
        if (!runningIteration && !runningStep)
            return;
        iteration();
        runningStep = false;
    }

    /**
     * Processes one iteration of the animation queue by delegating to the action
     * registered with {@link #setIterationAction(Runnable)}.
     */
    public void iteration() {
        if (iterationAction != null) {
            iterationAction.run();
        }
    }

    /**
     * Fills the whole cell buffer with one colour, ready to be copied to the screen by
     * {@link #drawCell(int, int)}.
     *
     * @param colorBytes colour in B, G, R, A order
     */
    private void fillCellBuffer(byte[] colorBytes) {
        for (int offset = 0; offset < CELL_BUFFER_SIZE; offset += 4) {
            putBgra(cellBuffer, offset, colorBytes);
        }
    }

    /**
     * Resets the animation state, cancelling both Iterate and Step modes.
     * Called when the view is restarted or the noise mode changes.
     */
    public void resetIteration() {

        runningIteration = false;
        runningStep = false;
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

}