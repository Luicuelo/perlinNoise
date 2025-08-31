package principal;

import java.util.Random;

/**
 * Generator class for creating 2D procedural noise patterns using value noise technique.
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
 *   <li>Each octave has half the amplitude of the previous one</li>
 *   <li>Random values are assigned to grid points in each octave</li>
 *   <li>Values are interpolated between grid points using various interpolation methods</li>
 *   <li>Octaves are summed to create the final complex noise pattern</li>
 * </ul>
 * </p>
 */
public class Generate2D {

    protected static class Rnd2D {
        private static final int numberOfOctaves = 8;
        private static final long seed = 201012;

        private static int distanceBetweenNodes(int octaveId) {
            return (int) Math.pow(2, (numberOfOctaves - 1 - octaveId));
        }

        public static double[] createValues(int totalSize) {
            Random random = new Random(seed);
            int totalValues = totalSize * 4; // Extra space for offsets
            
            double[] returnValues = new double[totalValues];
            for (int i = 0; i < totalValues; i++) {
                returnValues[i] = random.nextDouble() * 2.0 - 1.0; // Range [-1, 1]
            }
            return returnValues;
        }
    }

    /**
     * Nested class containing different 2D interpolation algorithms used in noise
     * generation.
     * <p>
     * Interpolation is used to create smooth transitions between discrete random
     * values assigned to grid points. Instead of having abrupt changes at each node,
     * interpolation calculates intermediate values that create a continuous, smooth surface.
     * </p>
     * <p>
     * This class provides two implementations of bilinear interpolation:
     * <ul>
     *   <li>{@link #BILINEAR}: Uses area-based weighting to compute interpolated values</li>
     *   <li>{@link #BILINEAR2}: Uses standard parametric bilinear interpolation</li>
     * </ul>
     * Both methods produce similar results but use different mathematical approaches.
     * </p>
     * <h3>How 2D Interpolation Works:</h3>
     * <p>
     * Given four corner points of a grid cell:
     * <pre>
     * P1: (x1, y1, v1)    P2: (x2, y1, v2)
     * 
     * P3: (x1, y2, v3)    P4: (x2, y2, v4)
     * </pre>
     * And a target point (tx, ty) inside that cell, the interpolation methods calculate
     * a weighted average of the four corner values based on the target point's position.
     * </p>
     */
    private static class Interpolate2D {

        // P1: (x1, y1, v1)
        // P2: (x2, y1, v2)
        // P3: (x1, y2, v3)
        // P4: (x2, y2, v4)
        // Target: (tx, ty)
        @FunctionalInterface
        interface CalculateIntermediatePoints2D {
            double calculate(
                    double x1, double y1, double x2, double y2,
                    double v1, double v2, double v3, double v4,
                    double tx, double ty);
        }

        /**
         * Bilinear interpolation using area-based weighting.
         * <p>
         * This method calculates the interpolated value by computing the areas of rectangles
         * formed between the target point and each corner, then using those areas as weights
         * for the corner values. The closer the target point is to a corner, the more that
         * corner's value contributes to the result.
         * </p>
         * <p>
         * Mathematical approach:
         * <ol>
         *   <li>Calculate rectangular areas between the target point and each corner</li>
         *   <li>Multiply each corner value by its corresponding area</li>
         *   <li>Sum the weighted values and divide by the total area</li>
         * </ol>
         * </p>
         */
        public static final CalculateIntermediatePoints2D BILINEAR = (
                double x1, double y1, double x2, double y2,
                double v1, double v2, double v3, double v4,
                double tx, double ty) -> {

            double area_v1 = Math.abs((tx - x1) * (ty - y1)) * v4;
            double area_v2 = Math.abs((tx - x2) * (ty - y1)) * v3;
            double area_v3 = Math.abs((tx - x1) * (ty - y2)) * v2;
            double area_v4 = Math.abs((tx - x2) * (ty - y2)) * v1;

            double area_total = (x2 - x1) * (y2 - y1);

            return (area_v1 + area_v2 + area_v3 + area_v4) / area_total;
        };

        /**
         * Standard bilinear interpolation using parametric approach.
         * <p>
         * This method calculates the interpolated value using standard parametric bilinear
         * interpolation. It first interpolates values along the top and bottom edges of the
         * grid cell, then interpolates between those results in the vertical direction.
         * </p>
         * <p>
         * Mathematical approach:
         * <ol>
         *   <li>Calculate normalized coordinates dx = (tx-x1)/(x2-x1) and dy = (ty-y1)/(y2-y1)</li>
         *   <li>Interpolate along top edge: top = v1 * (1 - dx) + v2 * dx</li>
         *   <li>Interpolate along bottom edge: bottom = v3 * (1 - dx) + v4 * dx</li>
         *   <li>Interpolate vertically: result = top * (1 - dy) + bottom * dy</li>
         * </ol>
         * </p>
         */
        public static final CalculateIntermediatePoints2D BILINEAR2 = (
                double x1, double y1, double x2, double y2,
                double v1, double v2, double v3, double v4,
                double tx, double ty) -> {

            double dx = (tx - x1) / (x2 - x1);
            double dy = (ty - y1) / (y2 - y1);

            double top = v1 * (1 - dx) + v2 * dx;
            double bottom = v3 * (1 - dx) + v4 * dx;

            return top * (1 - dy) + bottom * dy;
        };

    }

    /** Width of the generated pattern, in cells (one value per column). */
    private final int widthInCells;
    /** Height of the generated pattern, in cells (one value per row). */
    private final int heightInCells;
    /** Amplitude of the pattern, in cells. */
    private final int amplitudeInCells;
    /**
     * Random values for every octave, built once from the fixed seed.
     * <p>
     * The pool depends only on the grid size, so it is created in the constructor:
     * {@link #generate(int, int)} reuses it instead of generating the same numbers again
     * on every frame.
     * </p>
     */
    private final double[] randomValues;

    /**
     * Creates a generator for one grid size.
     * <p>
     * The generator holds no view state: it only depends on the grid size and its
     * amplitude, so it can be built once and asked for as many frames as needed just by
     * changing the world offsets.
     * </p>
     *
     * @param widthInCells     number of columns to generate, in cells
     * @param heightInCells    number of rows to generate, in cells
     * @param amplitudeInCells amplitude of the resulting pattern, in cells
     */
    public Generate2D(int widthInCells, int heightInCells, int amplitudeInCells) {
        this.widthInCells = widthInCells;
        this.heightInCells = heightInCells;
        this.amplitudeInCells = amplitudeInCells;
        this.randomValues = Rnd2D.createValues(widthInCells * heightInCells);
    }

    /**
     * Maps a world cell to an index of the random value pool.
     * <p>
     * Neighbouring cells are spread over the pool with prime number multiplication,
     * and {@link Math#floorMod(long, long)} keeps the result valid for negative
     * coordinates instead of relying on {@code Math.abs}, which can overflow.
     * </p>
     *
     * @param gridX    horizontal world cell
     * @param gridY    vertical world cell
     * @param poolSize size of the random value pool
     * @return an index in the range {@code [0, poolSize)}
     */
    private static int hashIndex(long gridX, long gridY, int poolSize) {
        long hash = (gridY * 31L + gridX) * 17L;
        return (int) Math.floorMod(hash, (long) poolSize);
    }

    /**
     * Generates a single octave of 2D value noise with infinite world navigation support.
     * <p>
     * This method extends the 1D noise concept to 2D space, enabling seamless navigation
     * in both X and Y directions. It uses coordinate hashing to map 2D world positions
     * to deterministic random values from a pre-generated array.
     * </p>
     * <h3>2D Navigation Algorithm:</h3>
     * <ol>
     *   <li>Convert screen cells to world cells using offsets</li>
     *   <li>Calculate grid cell position and relative coordinates within cell</li>
     *   <li>Hash 2D coordinates to array indices using prime number multiplication</li>
     *   <li>Perform bilinear interpolation between four corner values</li>
     * </ol>
     * <p>
     * The octave is accumulated into {@code target} instead of being returned in a new
     * matrix, so generating a frame only allocates the final pattern.
     * </p>
     * <p>
     * <strong>Hash Function:</strong> Uses {@code (gridY * 31 + gridX) * 17} with prime
     * numbers to distribute coordinates evenly across the random value array and
     * minimize hash collisions. Both the grid and the offset are handled as
     * {@code long} values and resolved with {@link Math#floorMod(long, long)}, so
     * negative world coordinates stay in range.
     * </p>
     *
     * @param target       matrix that receives the octave, accumulated in place
     * @param octaveIndex  the octave number (affects grid spacing)
     * @param offsetX      world X coordinate offset for navigation
     * @param offsetY      world Y coordinate offset for navigation
     * @param interpolator the 2D interpolation method to use
     */
    private void octave2D(double[][] target, int octaveIndex, int offsetX, int offsetY,
            Interpolate2D.CalculateIntermediatePoints2D interpolator) {

        int stepLength = Rnd2D.distanceBetweenNodes(octaveIndex);
        double octaveAmplitude = amplitudeInCells / Math.pow(2, octaveIndex + 1);

        for (int i = 0; i < heightInCells; i++) {
            for (int j = 0; j < widthInCells; j++) {
                long worldX = (long) j + offsetX;
                long worldY = (long) i + offsetY;

                long gridX = Math.floorDiv(worldX, stepLength);
                long gridY = Math.floorDiv(worldY, stepLength);

                int relativeX = Math.floorMod(worldX, stepLength);
                int relativeY = Math.floorMod(worldY, stepLength);

                // Hash 2D coordinates to array indices using prime number multiplication
                // This ensures deterministic, well-distributed access to random values
                int idx1 = hashIndex(gridX, gridY, randomValues.length);         // Top-left
                int idx2 = hashIndex(gridX + 1, gridY, randomValues.length);     // Top-right
                int idx3 = hashIndex(gridX, gridY + 1, randomValues.length);     // Bottom-left
                int idx4 = hashIndex(gridX + 1, gridY + 1, randomValues.length); // Bottom-right
                    
                double nodeV1 = randomValues[idx1] * octaveAmplitude;
                double nodeV2 = randomValues[idx2] * octaveAmplitude;
                double nodeV3 = randomValues[idx3] * octaveAmplitude;
                double nodeV4 = randomValues[idx4] * octaveAmplitude;

                if (relativeY == 0 && relativeX == 0)
                    target[i][j] += nodeV1;
                else
                    target[i][j] += interpolator.calculate(0, 0, stepLength, stepLength, nodeV1, nodeV2, nodeV3, nodeV4, relativeX, relativeY);
            }
        }
    }


    /**
     * Generates a complete 2D value noise pattern by combining multiple octaves.
     * <p>
     * Every cell receives the sum of the eight octaves evaluated at the same world
     * position, so the coarse octave shapes the pattern and the finer ones add detail
     * on top of it.
     * </p>
     * <p>
     * Bilinear interpolation is used to ensure smooth transitions in 2D space.
     * </p>
     * <p>
     * Every octave is accumulated directly into the result matrix, so a frame allocates
     * a single matrix instead of one per octave.
     * </p>
     *
     * @param offsetX world X coordinate of the first generated column, in cells
     * @param offsetY world Y coordinate of the first generated row, in cells
     * @return one value per cell, centred on zero
     */
    public double[][] generate(int offsetX, int offsetY) {
        double[][] wave = new double[heightInCells][widthInCells];

        for (int octaveIndex = 0; octaveIndex < Rnd2D.numberOfOctaves; octaveIndex++) {
            octave2D(wave, octaveIndex, offsetX, offsetY, Interpolate2D.BILINEAR);
        }

        return wave;
    }
}