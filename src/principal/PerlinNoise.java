package principal;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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
    protected static final int PIXEL_SIZE = 1;
    private static int   cellWidth;
    private static int   cellHeight;
    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    private static final int BUTTON_HEIGHT = 25;
    private static final Color BACKGROUND_COLOR = Color.LIGHTGRAY;
    private static final int FRAME_SKIP = 1; // Update every FRAME_SKIP frames for performance
    private static byte[][] CELLS_ARRAY;
    private int canvasWidth;
    private int canvasHeight;
    private Canvas canvas;
    private Renderer renderer;
    private AnimationTimer animationTimer;
    private boolean finished = false;
    /**
     * Flag to control whether sound is enabled during visualization.
     * When true, audio feedback is played during operations.
     * When false, all sound generation is skipped.
     */
    private double frameCounter = 0;

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

        // Create the Iterate button for continuous animation
        Button iterateButton = new Button("Iterate");
        iterateButton.setOnAction(e -> iterate());
        iterateButton.setPrefHeight(BUTTON_HEIGHT);

        // Create the Stop button to pause animation
        Button stopButton = new Button("Stop");
        stopButton.setOnAction(e -> stopIteration());
        stopButton.setPrefHeight(BUTTON_HEIGHT);

        // Create the Step button for single-step execution
        Button stepButton = new Button("Step");
        stepButton.setOnAction(e -> step());
        stepButton.setPrefHeight(BUTTON_HEIGHT);

        // Create the selector dropdown
        ComboBox<graphicTypeEnum> Selector_1D2D;
        Selector_1D2D = new ComboBox<>();
        Selector_1D2D.getItems().addAll(graphicTypeEnum.values());
        Selector_1D2D.setValue(actualGraphicValue);
        Selector_1D2D.setPrefHeight(BUTTON_HEIGHT);
        // Add a listener to update the actualGraphicValue field when selection changes
        Selector_1D2D.setOnAction(e -> {
            actualGraphicValue = Selector_1D2D.getValue();
            restart();
        });

        // Layout
        BorderPane root = new BorderPane();
        root.setCenter(canvas);

        HBox buttons = new HBox();
        root.setBottom(buttons);
        buttons.setPadding(new Insets(0, 10, 0, 0));
        buttons.setSpacing(10);
        buttons.getChildren().addAll(Selector_1D2D);

        // Create scene and show
        Scene scene = new Scene(root, canvasWidth, canvasHeight + BUTTON_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
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

        renderer = new Renderer(gc, canvasWidth, canvasHeight, PIXEL_SIZE, new Color(.3, .6, .9, 1), BACKGROUND_COLOR
                );
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


    }

    private void initial1D( ) {

        // Generate noise with cellWidth elements
        //renderer.initializePaletteColor();
        renderer.initializePaletteGray();
        
        int amplitude=200;
        double[] noise = Generator.generate(cellWidth,amplitude, 25083025);
        double[] noise2 = Generator.generate(cellWidth,(255-amplitude)*100, 25083025);



        for (int b = 0; b < cellWidth; b++) {
            int value=(int) (noise[b] + amplitude / 2);
            int point = cellHeight -value;
            for (int a = 0; a < cellHeight-point; a++) {

                CELLS_ARRAY[a+point][b] = (byte) (value -a +(255-amplitude)/2 +noise2[b]/100 );
            }
        }

        renderer.drawPixels(CELLS_ARRAY);
    }

    private void initial2D() {

        // Initialize palette first
        renderer.initializePaletteColor();
        int maxAmplitude=255;
        double[][] noise = Generator.generate2D(cellWidth, cellHeight, maxAmplitude, 27184235);


        for (int a = 0; a < cellHeight; a++) {
            for (int b = 0; b < cellWidth; b++) {
                CELLS_ARRAY[a][b] = (byte) ((noise[a][b]+maxAmplitude/2));
            }
        }
        

        renderer.drawPixels(CELLS_ARRAY);
    }

    /**
     * Restarts the noise visualization with a new randomly generated noise.
     * Clears the current visualization, generates new data, and displays it.
     */
    private void restart() {

        if (renderer == null)
            return;
        System.out.println("Restarting  ...");

                


        finished = false;
        renderer.clearImage();

        cellWidth = canvasWidth / PIXEL_SIZE;
        cellHeight = canvasHeight / PIXEL_SIZE;
        CELLS_ARRAY = new byte[cellHeight][cellWidth];
        // animationTimer.start();
        if (actualGraphicValue==graphicTypeEnum.Noise_1D) initial1D();
        else initial2D();

        if (renderer != null) {
            renderer.restart();
        }



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
        Platform.exit();
    }
}