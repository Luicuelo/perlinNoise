package principal;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Perlin Noise Visualization Application
 * <p>
 * A JavaFX application that visualizes 1D and 2D noise algorithms in real-time.
 * Features:
 * - Interactive noise visualization with graphical output
 * - Switch between 1D and 2D noise generation
 * - Configurable canvas size via command line arguments
 * - Real-time visualization of noise operations
 * </p>
 */
public class PerlinNoise extends Application {

    private enum graphicTypeEnum {
        Noise_1D,
        Noise_2D
    }

    private graphicTypeEnum actualGraphicValue = graphicTypeEnum.Noise_1D;
    /**
     * Size of the logical grid in cells. It is the unit of every noise calculation:
     * the generators never see pixels. The cell -> pixel conversion lives only in
     * {@link Renderer}, which exposes its size in cells.
     */
    private static int widthInCells;
    private static int heightInCells;
    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    private static final int BUTTON_HEIGHT = 25;
    private static final Color BACKGROUND_COLOR = Color.LIGHTGRAY;
    private static final int FRAME_SKIP = 4; // Update every  FRAME_SKIP frames for performance
    /** World cells the view advances on every animated frame. */
    private static final int SCROLL_STEP = 4;
    /** Amplitude of the 2D pattern, in cells. */
    private static final int NOISE_2D_AMPLITUDE = 200;
    /** Palette index of the lightest terrain grey (the surface row). */
    private static final int TERRAIN_TOP_SHADE = 150;
    /** How many palette indices the terrain darkens from the surface to the bottom. */
    private static final int TERRAIN_SHADE_SPAN = 105;
    /** Logical grid handed to the renderer: one palette index per cell. */
    private static byte[][] cellGrid;
    private int canvasWidth;
    private int canvasHeight;
    private Canvas canvas;
    private Renderer renderer;
    /** Reusable 1D generator: its random pool is built once per grid size. */
    private Generate1D generator1D;
    /** Reusable 2D generator: its random pool is built once per grid size. */
    private Generate2D generator2D;
    private AnimationTimer animationTimer;
    private boolean finished = false;
    /**
     * Frame counter of the animation loop.
     * <p>
     * It is used to throttle rendering through {@link #FRAME_SKIP} and reset before
     * it loses precision. It is not related to audio: the sound feedback this field
     * was originally documented for is no longer part of the application.
     * </p>
     */
    private double frameCounter = 0;
    /** Horizontal world offset, in cells, applied when sampling the noise. */
    private int offsetX = 0;
    /** Vertical world offset, in cells. Only visible in the 2D view. */
    private int offsetY = 0;

    /**
     * Main method to launch the JavaFX application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Starts the JavaFX application and initializes the user interface.
     * Creates the main window with canvas for visualization and control buttons.
     *
     * @param primaryStage the primary stage for this application
     */
    @Override
    public void start(Stage primaryStage) {
        // Parse command line arguments for size
        Parameters params = getParameters();
        parseSize(params);

        primaryStage.setTitle("Perlin Noise");

        // Create and initialize
        initialize();

        // Create the restart button
        Button restartButton = new Button("Restart");
        restartButton.setOnAction(e -> restart());
        restartButton.setPrefHeight(BUTTON_HEIGHT);
        restartButton.setFocusTraversable(false);

        // Create the Iterate button for continuous animation
        Button iterateButton = new Button("Iterate");
        iterateButton.setOnAction(e -> iterate());
        iterateButton.setPrefHeight(BUTTON_HEIGHT);
        iterateButton.setFocusTraversable(false);

        // Create the Stop button to pause animation
        Button stopButton = new Button("Stop");
        stopButton.setOnAction(e -> stopIteration());
        stopButton.setPrefHeight(BUTTON_HEIGHT);
        stopButton.setFocusTraversable(false);

        // Create the Step button for single-step execution
        Button stepButton = new Button("Step");
        stepButton.setOnAction(e -> step());
        stepButton.setPrefHeight(BUTTON_HEIGHT);
        stepButton.setFocusTraversable(false);

        // Controls are non-focusable so they never capture the arrow keys
        Button button1D = new Button("1D");
        button1D.setPrefHeight(BUTTON_HEIGHT);
        button1D.setFocusTraversable(false);
        button1D.setOnAction(e -> {
            actualGraphicValue = graphicTypeEnum.Noise_1D;
            restart();
        });
        
        Button button2D = new Button("2D");
        button2D.setPrefHeight(BUTTON_HEIGHT);
        button2D.setFocusTraversable(false);
        button2D.setOnAction(e -> {
            actualGraphicValue = graphicTypeEnum.Noise_2D;
            restart();
        });

        // Layout
        BorderPane root = new BorderPane();
        root.setCenter(canvas);

        HBox buttons = new HBox();
        root.setBottom(buttons);
        buttons.setPadding(new Insets(0, 10, 0, 0));
        buttons.setSpacing(10);
        buttons.getChildren().addAll(button1D, button2D, restartButton, iterateButton, stopButton, stepButton);

        // Create scene and show
        Scene scene = new Scene(root, canvasWidth, canvasHeight + BUTTON_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
        
        // Make canvas focusable and request focus
        canvas.setFocusTraversable(true);
        canvas.requestFocus();

        // Add key press handling for scrolling
        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case RIGHT:
                    offsetX += 15;
                    drawImage();
                    break;
                case LEFT:
                    offsetX -= 15;
                    drawImage();
                    break;
                case UP:
                    offsetY -= 15;
                    drawImage();
                    break;
                case DOWN:
                    offsetY += 15;
                    drawImage();
                    break;
            }
        });

    }

    /**
     * Parses command line arguments for canvas size.
     *
     * @param params the parameters to parse
     */
    private void parseSize(Parameters params) {
        try {
            if (params.getNamed().containsKey("width")) {
                canvasWidth = Integer.parseInt(params.getNamed().get("width"));
            } else {
                canvasWidth = DEFAULT_WIDTH;
            }

            if (params.getNamed().containsKey("height")) {
                canvasHeight = Integer.parseInt(params.getNamed().get("height"));
            } else {
                canvasHeight = DEFAULT_HEIGHT;
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid size parameters, using defaults");
            canvasWidth = DEFAULT_WIDTH;
            canvasHeight = DEFAULT_HEIGHT;
        }
    }

    /**
     * Initializes the canvas and starts the animation loop.
     * Sets up the graphics context, creates the renderer, and begins the animation
     * timer for real-time visualization updates.
     */
    private void initialize() {
        canvas = new Canvas(canvasWidth, canvasHeight);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvasWidth, canvasHeight);
        gc.setFill(BACKGROUND_COLOR);
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        renderer = new Renderer(gc, canvasWidth, canvasHeight);
        // The renderer delegates every animated frame to this application, which owns
        // the world offsets and the noise generators.
        renderer.setIterationAction(this::animate);
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!finished && renderer != null) {
                    frameCounter++;
                    if (frameCounter >= Double.MAX_VALUE)
                        frameCounter = 0;
                    if (frameCounter % FRAME_SKIP == 0) {
                        try {
                            renderer.updateRegion();
                        } catch (Exception e) {
                            System.err.println("Error updating : " + e.getMessage());
                            stopAnimation();
                        }
                    }
                }
            }
        };

        restart();
        animationTimer.start();

    }

    /**
     * Paints one frame of the 1D curve for the current world offset.
     * <p>
     * The noise value of each column sets its surface row: everything above is sky
     * (palette index 0) and everything below is the terrain gradient. It runs for every
     * frame and every key press, not only at start-up.
     * </p>
     */
    private void drawTerrain1D() {

        // renderer.initializePaletteColor();
        renderer.initializePaletteGray();

        // The generator works in cells and is reused between frames: only the world
        // offset changes, so the random pool is not rebuilt on every frame.
        double[] noise = generator1D.generate(offsetX);

        for (int column = 0; column < widthInCells; column++) {
            int surface = firstTerrainRow(noise[column]);
            for (int row = 0; row < heightInCells; row++) {
                cellGrid[row][column] = row < surface ? 0 : terrainShade(row);
            }
        }

        renderer.drawCells(cellGrid);
    }

    /**
     * Computes the first terrain row of a column.
     * <p>
     * Noise is centred on zero, so half the grid height is added to obtain the column
     * height in cells; the axis is then flipped because rows count from the top while
     * height counts from the bottom.
     * </p>
     *
     * @param noiseValue noise value of the column, already in cells
     * @return first row occupied by the terrain, in cells
     */
    private static int firstTerrainRow(double noiseValue) {
        int columnHeight = (int) (noiseValue + heightInCells / 2);
        return heightInCells - columnHeight;
    }

    /**
     * Palette index of the terrain fill for a given row.
     * <p>
     * It is a fixed gradient: light at the surface and darker downwards, unrelated to
     * the column height.
     * </p>
     *
     * @param row grid row, in cells
     * @return index of the grey palette
     */
    private static byte terrainShade(int row) {
        return (byte) (TERRAIN_TOP_SHADE - row * TERRAIN_SHADE_SPAN / heightInCells);
    }

    /**
     * Paints one frame of the 2D noise for the current world offset.
     * <p>
     * Here the noise value is the palette index itself, so there is no surface row to
     * compute: every cell is painted directly.
     * </p>
     */
    private void drawNoise2D() {

        // Initialize palette first
        renderer.initializePaletteColor();
        double[][] noise = generator2D.generate(offsetX, offsetY);

        // Centring uses the same offset as before. The 2D octaves add up to about
        // +-amplitude, so the lowest cells still wrap through the byte cast; that is
        // deliberately left untouched because changing it would change the picture.
        for (int row = 0; row < heightInCells; row++) {
            for (int column = 0; column < widthInCells; column++) {
                cellGrid[row][column] = (byte) (noise[row][column] + NOISE_2D_AMPLITUDE / 2);
            }
        }

        renderer.drawCells(cellGrid);
    }

    /**
     * Restarts the visualization from the world origin.
     * <p>
     * The noise itself is deterministic, so restarting does not produce a different
     * world: it only resets the navigation offsets and stops any running animation.
     * </p>
     */
    private void restart() {

        if (renderer == null)
            return;
        System.out.println("Restarting  ...");

        finished = false;
        offsetX = 0;
        offsetY = 0;
        renderer.resetIteration();
        renderer.clearImage();

        // The renderer is the only one that knows the cell -> pixel conversion.
        widthInCells = renderer.widthInCells();
        heightInCells = renderer.heightInCells();
        cellGrid = new byte[heightInCells][widthInCells];
        // The random pools depend only on the grid size, so one generator per size is
        // enough: every frame reuses them instead of rebuilding the pools.
        generator1D = new Generate1D(widthInCells, heightInCells);
        generator2D = new Generate2D(widthInCells, heightInCells, NOISE_2D_AMPLITUDE);
        drawImage();

    }

    private void drawImage() {

        if (renderer == null)
            return;

        if (actualGraphicValue == graphicTypeEnum.Noise_1D)
            drawTerrain1D();
        else
            drawNoise2D();
    }

    /**
     * Advances the world one step and repaints the current view.
     * <p>
     * This is the action executed on every frame while the Iterate mode is running
     * and also on each press of the Step button. Only the horizontal offset moves,
     * so vertical movement stays under the user's control through the arrow keys.
     * </p>
     */
    private void animate() {
        if (renderer == null)
            return;

        offsetX += SCROLL_STEP;
        drawImage();
    }

    /**
     * Starts continuous iteration mode for the visualization.
     * The animation will run automatically until stopped.
     */
    private void iterate() {
        System.out.println("Iterating ...");
        if (renderer != null) {
            renderer.iterate();
        }
    }

    /**
     * Stops the continuous iteration mode of the visualization.
     */
    private void stopIteration() {
        System.out.println("stop ...");
        if (renderer != null) {
            renderer.stopIteration();
        }
    }

    /**
     * Performs a single step in the visualization.
     * Useful for debugging or educational purposes to see each operation.
     */
    private void step() {
        System.out.println("step ...");
        if (renderer != null) {
            renderer.step();
        }
    }

    /**
     * Stops the animation timer and cleans up resources.
     */
    private void stopAnimation() {
        finished = true;
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }

    /**
     * Cleanup method called when the application is closing.
     * Stops the animation timer and releases resources.
     */
    @Override
    public void stop() {
        stopAnimation();
        renderer = null;
        System.out.println(" destroyed");
    }
}