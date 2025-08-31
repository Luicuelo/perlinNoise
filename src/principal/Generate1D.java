package principal;

import java.util.Random;

/**
 * Generator class for creating 1D procedural noise patterns using value noise technique.
 * <p>
 * This class implements a form of value noise generation through multiple octaves of
 * interpolated random values. While commonly referred to as "Perlin noise" in the 
 * documentation, this implementation actually uses value noise rather than true Perlin noise.
 * </p>
 * <p>
 * The key difference between value noise and true Perlin noise:
 * <ul>
 *   <li><b>Value Noise</b>: Assigns random scalar values to grid points and interpolates between them</li>
 *   <li><b>Perlin Noise</b>: Assigns gradient vectors to grid points and interpolates dot products</li>
 * </ul>
 * </p>
 * <p>
 * The noise generation works by combining multiple "octaves" of noise with different 
 * frequencies and amplitudes. Each octave contributes to the overall complexity of the
 * resulting noise pattern. This approach is mathematically sound and produces high-quality
 * procedural noise, just using a different technique than the original Perlin algorithm.
 * </p>
 * <p>
 * Implementation details:
 * <ul>
 *   <li>Uses 8 octaves whose node spacing halves each time (128, 64, ..., 1 cells)</li>
 *   <li>Each octave has half the amplitude of the previous one, reduced further for the
 *       finest ones so the summed pattern stays inside half the grid height</li>
 *   <li>Random values are assigned to grid points in each octave</li>
 *   <li>Values are interpolated between grid points using various interpolation methods</li>
 *   <li>Octaves are summed to create the final complex noise pattern</li>
 * </ul>
 * </p>
 */
public class Generate1D {

    //this class returns the random numbers needed for a screen
    //Return numbers for octave 1 separated by 1024 pixel , octave 2 separated by 512 pixel... and so on
    //So we need for each frame  1 value vor octave 1, 2 for octave 2, 4 for octave 3... until 1024 to octave 11 2^12-1 values
    //first , this implementation is for one dimension case.
    //We assume that a screen with is less than 1024 pixels
    //So to draw actual screen we need
    protected static class Rnd{

        private static final int numberOfOctaves=8;
        private static final long seed=201012;


       
        private static int distanceBetweenNodes(int octaveId){
            return (int) Math.pow(2, (numberOfOctaves-1-octaveId));
        }

        // Calculate the starting index for an octave's random values
        private static int offsetForOctave(int octaveId, int widthInCells) {
            return octaveId == 0 ? 0 : widthInCells * ((1 << octaveId) - 1) / (1 << (numberOfOctaves - 1)) + 2 * octaveId;
        }

        /**
         * Creates a deterministic array of random values for noise generation.
         * <p>
         * This method is crucial for achieving consistent, repeatable noise patterns.
         * It generates a large array of random values using a fixed seed, ensuring
         * that the same coordinates always produce the same noise values.
         * </p>
         * <p>
         * <strong>Design Rationale:</strong> Instead of generating random values on-demand
         * (which would be inconsistent), we pre-generate a large pool of values that
         * can be accessed deterministically using coordinate-based indexing. The pool only
         * depends on the width, so it is built once per generator instance.
         * </p>
         *
         * @param widthInCells number of columns the pool has to cover, in cells
         * @return array of random values in the range [-1, 1]
         */
        public static double[] createValues(int widthInCells){
            Random random = new Random(seed);
            // Total values = (2^numberOfOctaves + numberOfOctaves) * width / 2^(numberOfOctaves-1)
            int totalValues = ((1 << numberOfOctaves) + numberOfOctaves) * widthInCells / (1 << (numberOfOctaves - 1));
            
            double[] returnValues = new double[totalValues];
            for (int i = 0; i < totalValues; i++) {
                returnValues[i] = random.nextDouble()* 2.0 - 1.0;
            }
            return returnValues;
        }

    }
    
    private static class Interpolate {

        /**
         * Functional interface for calculating intermediate points between two known
         * points.
         * <p>
         * This interface defines the contract for all interpolation methods used in the
         * noise generation. Given two points (x1,y1) and (x2,y2), and a target x value,
         * the method calculates the corresponding y value using a specific interpolation
         * algorithm.
         * </p>
         */
        @FunctionalInterface
        interface CalculateIntermediatePoints {
            /**
             * Calculates the y-value at position x using interpolation between two points.
             *
             * @param x1 the x-coordinate of the first point
             * @param y1 the y-coordinate of the first point
             * @param x2 the x-coordinate of the second point
             * @param y2 the y-coordinate of the second point
             * @param x  the x-coordinate where we want to interpolate the y-value
             * @return the interpolated y-value at position x
             */
            double calculate(double x1, double y1, double x2, double y2, double x);
        }

        /**
         * Linear interpolation method.
         * <p>
         * Creates a straight line between two points. This is the simplest form of
         * interpolation but can result in noticeable "corners" at the control points.
         * </p>
         * <p>
         * Formula: y = ((x - x1) * (y2 - y1) / (x2 - x1)) + y1
         * </p>
         * <p>
         * This method provides fast computation but produces less smooth transitions
         * compared to other methods. It's useful when performance is more important
         * than visual smoothness.
         * </p>
         */
        public static final CalculateIntermediatePoints LINEAR = (x1, y1, x2, y2,
                x) -> ((x - x1) * (y2 - y1) / (x2 - x1))
                        + y1;

        /**
         * Perlin's fade interpolation method.
         * <p>
         * Uses a smoothstep function (6t^5 - 15t^4 + 10t^3) to create smoother
         * transitions. This is the interpolation function specifically designed by
         * Ken Perlin for his noise algorithm. It eliminates the discontinuities at
         * the integer points that can be noticeable with linear interpolation.
         * </p>
         * <p>
         * Formula: f(t) = 6t^5 - 15t^4 + 10t^3 where t = (x - x1) / (x2 - x1)
         * </p>
         * <p>
         * This method produces the smoothest transitions and is the preferred choice
         * for high-quality noise generation. The smoothstep function ensures that
         * both the function and its first derivative are continuous at the endpoints,
         * resulting in visually smooth noise patterns.
         * </p>
         */
        public static final CalculateIntermediatePoints FADE = (x1, y1, x2, y2, x) -> {
            double t = (x - x1) / (x2 - x1);
            double ft = t * t * t * (t * (t * 6 - 15) + 10);
            return y1 + ft * (y2 - y1);
        };

    }

    /**
     * Node spacing, in cells, at or below which an octave is damped to a quarter of the
     * previous amplitude instead of a half.
     * <p>
     * That covers the last four octaves (8, 4, 2 and 1 cell between nodes): at that level
     * of detail a half-step amplitude adds too much jaggedness to the silhouette.
     * </p>
     */
    private static final int DAMPED_STEP = 8;

    /** Width of the generated pattern, in cells (one value per column). */
    private final int widthInCells;
    /** Amplitude of the pattern, in cells. */
    private final int amplitudeInCells;
    /**
     * Random values for every octave, built once from the fixed seed.
     * <p>
     * The pool depends only on {@link #widthInCells}, so it is created in the constructor:
     * {@link #generate(int)} reuses it instead of generating the same numbers again on
     * every frame.
     * </p>
     */
    private final double[] randomValues;

    /**
     * Creates a generator for one grid size.
     * <p>
     * The generator holds no view state: it only depends on the grid size and its
     * amplitude, so it can be built once and asked for as many frames as needed just by
     * changing the world offset.
     * </p>
     *
     * @param widthInCells     number of columns to generate, in cells
     * @param amplitudeInCells amplitude of the resulting pattern, in cells
     */
    public Generate1D(int widthInCells, int amplitudeInCells) {
        this.widthInCells = widthInCells;
        this.amplitudeInCells = amplitudeInCells;
        this.randomValues = Rnd.createValues(widthInCells);
    }

    /**
     * Generates one window of 1D value noise by summing all octaves.
     * <p>
     * Every column receives the sum of the eight octaves evaluated at the same world
     * position, so the coarse octave shapes the silhouette and the finer ones add detail
     * on top of it. Every octave is accumulated directly into the result, so a call
     * allocates a single array instead of one per octave.
     * </p>
     *
     * @param offset world position, in cells, of the first generated column
     * @return one value per column, in cells and centred on zero
     */
    public double[] generate(int offset) {
        double[] wave = new double[widthInCells];

        for (int octaveIndex = 0; octaveIndex < Rnd.numberOfOctaves; octaveIndex++) {
            octave(wave, octaveIndex, Interpolate.FADE, offset);
        }

        return wave;
    }
    /**
     * Generates a single octave of value noise using offset-based world coordinates.
     * <p>
     * This method implements the core noise generation algorithm that enables infinite
     * world navigation. It uses a pre-generated array of random values and applies
     * coordinate offsets to simulate seamless movement through an infinite noise field.
     * </p>
     * <h3>Key Algorithm Steps:</h3>
     * <ol>
     *   <li>Calculate step length based on octave index (higher octaves = smaller steps)</li>
     *   <li>For each screen position, convert to world coordinates using offset</li>
     *   <li>Find grid nodes and relative position within the grid cell</li>
     *   <li>Interpolate between adjacent nodes using the specified method</li>
     * </ol>
     * <p>
     * <strong>Critical Design:</strong> The offset parameter enables infinite navigation
     * by shifting the "window" of values read from the random array without changing
     * the underlying noise function. This ensures returning to the same world coordinates
     * always produces identical results.
     * </p>
     * <p>
     * Offsets are accepted in both directions. The grid is computed with
     * {@link Math#floorDiv(long, int)} and {@link Math#floorMod(long, int)} over a
     * {@code long} world position, so negative coordinates map to the same lattice
     * and never produce a negative array index.
     * </p>
     *
     * @param target       array that receives the octave, accumulated in place
     * @param octaveIndex  the octave number (0-7, affects frequency)
     * @param interpolator the interpolation method (LINEAR or FADE)
     * @param offset       the world coordinate offset for infinite navigation
     */
    private void octave(double[] target, int octaveIndex, Interpolate.CalculateIntermediatePoints interpolator, int offset) {

        int stepLength = Rnd.distanceBetweenNodes(octaveIndex);
        // The extra division by two keeps the sum of the eight octaves just under half
        // of amplitudeInCells, so the silhouette always fits in the grid height.
        double octaveAmplitude = amplitudeInCells / Math.pow(2, octaveIndex + 2);
            
        if (stepLength <= DAMPED_STEP) {
            octaveAmplitude *= 0.5;
        }

        int randomOffset = Rnd.offsetForOctave(octaveIndex, widthInCells);

        // Generate interpolated values for the entire width.
        // floorDiv/floorMod keep the node grid consistent for negative world
        // coordinates, so navigating left uses the same lattice as navigating right.
        for (int pos = 0; pos < widthInCells; pos++) {
            long worldPos = (long) pos + offset;
            long nodeIndex = Math.floorDiv(worldPos, stepLength);
            int relativePos = Math.floorMod(worldPos, stepLength);

            int index = (int) Math.floorMod((long) randomOffset + nodeIndex, (long) randomValues.length);
            int nextIndex = (int) Math.floorMod((long) randomOffset + nodeIndex + 1L, (long) randomValues.length);

            double node = randomValues[index];
            double nextNode = randomValues[nextIndex];

            target[pos] += interpolator.calculate(
                    0, node,
                    stepLength, nextNode,
                    relativePos) * octaveAmplitude;
        }
    }

}