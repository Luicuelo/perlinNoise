package principal;

import java.util.Random;

/**
 * Generator class for creating procedural noise patterns using value noise technique.
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
 *   <li>Uses multiple octaves with frequencies that double each time (2, 4, 8, 16, 32, 64, 128)</li>
 *   <li>Each octave has half the amplitude of the previous one</li>
 *   <li>Random values are assigned to grid points in each octave</li>
 *   <li>Values are interpolated between grid points using various interpolation methods</li>
 *   <li>Octaves are summed to create the final complex noise pattern</li>
 * </ul>
 * </p>
 */
public class Generator {

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
     * Inner class that handles the core value noise generation logic.
     * <p>
     * This class generates individual octaves of value noise with specified
     * parameters. An octave is a layer of noise with a specific frequency and amplitude.
     * Multiple octaves are combined to create the final complex noise pattern.
     * </p>
     * <p>
     *
     * For 1D noise:
     * <ol>
     *   <li>Create control points based on the frequency parameter</li>
     *   <li>Assign random values to each control point within the amplitude range</li>
     *   <li>Interpolate values between consecutive control points</li>
     *   <li>Return the complete array of interpolated noise values</li>
     * </ol>
     * </p>
     * <p>
     * For 2D noise:
     * <ol>
     *   <li>Create a grid based on frequency (grid spacing = width/frequency, height/frequency)</li>
     *   <li>Assign random values to each grid point</li>
     *   <li>For each pixel in the output, determine which grid cell it belongs to</li>
     *   <li>Use 2D interpolation to compute the value based on the four corners of that cell</li>
     * </ol>
     * </p>
     */
    private static class Perlin {
        private final Random rnd;

        /**
         * Constructor that initializes the random number generator with a specific
         * seed.
         * <p>
         * Using a seed ensures that the same noise pattern can be reproduced.
         * </p>
         *
         * @param seed the seed value for the random number generator
         */
        public Perlin(long seed) {
            this.rnd = new Random(seed);
        }

        public double[][] octave2D(int frequency, double amplitude, int width, int height,
                Interpolate2D.CalculateIntermediatePoints2D interpolator) {

            int stepy = height / frequency;
            int stepx = width / frequency;

            double[][] wave = new double[height][width];

            double[][] noise = new double[(height / stepy) + 2][(width / stepx) + 2];

            /*
            System.out.println("--------------------------------------------");
            System.out.printf("Ancho, Alto  %d,%d \r\n", width, height);
            System.out.printf("Frequency: %d \r\n", frequency);
            System.out.printf("Step x , step y : %d,%d \r\n", stepx, stepy);
            System.out.printf("Noise x , Noise  y  %d,%d \r\n", (width / stepx), (height / stepy));
            System.out.printf("");
             */

            for (int i = 0; i < (height / stepy) + 2; i++) {
                for (int j = 0; j < (width / stepx) + 2; j++) {
                    noise[i][j] = (amplitude / 2) - (rnd.nextDouble() * amplitude);
                }
            }

            int x1;
            int y1;
            int rx;
            int ry;
            double v1;
            double v2;
            double v3;
            double v4;
            int i1;
            int j1;
            for (int i = 0; i < height; i++) {
                i1 = (i / stepy);
                y1 = (i1) * stepy;
                ry = i - (y1);
                for (int j = 0; j < width; j++) {
                    j1 = j / stepx;
                    x1 = (j1) * stepx;
                    rx = j - (x1);
                    v1 = noise[i1][j1];
                    v2 = noise[i1][j1 + 1];
                    v3 = noise[i1 + 1][j1];
                    v4 = noise[i1 + 1][j1 + 1];

                    // check if is a node
                    if (ry == 0 && rx == 0)
                        wave[i][j] = v1;
                    else
                        wave[i][j] = interpolator.calculate(0, 0, stepx, stepy, v1, v2, v3, v4, rx, ry);

                }
            }

            return wave;

        }

        /**
         * Generates a single octave of value noise.
         * <p>
         * An octave is a layer of noise with a specific frequency and amplitude.
         * The method creates control points based on the frequency, assigns random
         * values within the amplitude range to these points, and then interpolates values
         * between these points using the specified interpolation method.
         * </p>
         * <h3>Process Details:</h3>
         * <ol>
         *   <li>Calculate step length: length / frequency</li>
         *   <li>Assign a random value to the first point</li>
         *   <li>For each subsequent grid point at step intervals:
         *     <ul>
         *       <li>Assign a random value within the amplitude range</li>
         *       <li>Interpolate all points between the previous and current grid points</li>
         *     </ul>
         *   </li>
         * </ol>
         * </p>
         *
         * @param frequency    the number of control points in the noise wave (higher = more detail)
         * @param amplitude    the maximum deviation from zero (controls the "height" of the noise)
         * @param length       the total number of samples in the output array
         * @param interpolator the interpolation method to use between control points
         * @return an array of noise values with length [length]
         */
        public double[] octave(int frequency, double amplitude, int length,
                Interpolate.CalculateIntermediatePoints interpolator) {
            double[] wave = new double[length];
            if (length <= 0 || frequency <= 0)
                return wave;
            int stepLength = length / frequency;
            if (stepLength <= 0)
                stepLength = 1;

            double lastNode = (amplitude / 2) - (rnd.nextDouble() * amplitude);
            wave[0] = lastNode;
            // Interpolate between nodes
            // We calculate the end position as (stepLength+1)*(frequency)-1 to ensure we
            // generate values for all the required wave values
            // ,but we also check wave array position < length in the loop body to prevent
            // array overflows.
            for (int i = stepLength; i < (stepLength + 1) * (frequency) - 1; i += stepLength) {
                double node = (amplitude / 2) - (rnd.nextDouble() * amplitude);
                if (i < length)
                    wave[i] = node;
                int start = i - stepLength + 1;
                for (int j = start; j < i && j < length; j++) {
                    wave[j] = interpolator.calculate(
                            start, lastNode,
                            i, node,
                            j);
                }
                lastNode = node;
            }

            return wave;
        }

    }

    /**
     * Generates a complete value noise pattern by combining multiple octaves.
     * <p>
     * This is the main entry point for generating 1D value noise. It creates multiple
     * octaves with different frequencies and amplitudes, then combines them to
     * produce a complex, natural-looking noise pattern.
     * </p>
     * <h3>Fractal Noise Generation:</h3>
     * <p>
     * The method uses 7 octaves, with each successive octave having double the
     * frequency and half the amplitude of the previous one. This follows the
     * standard approach for fractal noise generation, also known as "fractional Brownian motion" (fBm).
     * </p>
     * <p>
     * Octave parameters:
     * <ul>
     *   <li>Octave 1: frequency=2, amplitude=length/2</li>
     *   <li>Octave 2: frequency=4, amplitude=length/4</li>
     *   <li>Octave 3: frequency=8, amplitude=length/8</li>
     *   <li>Octave 4: frequency=16, amplitude=length/16</li>
     *   <li>Octave 5: frequency=32, amplitude=length/32</li>
     *   <li>Octave 6: frequency=64, amplitude=length/64</li>
     *   <li>Octave 7: frequency=128, amplitude=length/128</li>
     * </ul>
     * </p>
     * <p>
     * Perlin's fade interpolation is used to ensure smooth transitions.
     * </p>
     *
     * @param length the length of the output noise array
     * @param seed   the seed for the random number generator (for reproducible results)
     * @return an array of value noise values
     */
    public static double[] generate(int length, int maxAmplitude,long seed) {

        int nOctaves = 7;
        double[] perlinWave = new double[length];
        Perlin perlin = new Perlin(seed);

        // Sum each octave
        for (int octaveIndex = 1; octaveIndex <= nOctaves; octaveIndex++) {
            int frequency = (int) Math.pow(2, octaveIndex);
            double amplitude = (double) maxAmplitude / frequency;
            double[] octaveArr = perlin.octave(frequency, amplitude, length, Interpolate.FADE);

            for (int j = 0; j < length; j++) {
                perlinWave[j] += octaveArr[j];
            }
        }

        return perlinWave;
    }

    /**
     * Generates a complete 2D value noise pattern by combining multiple octaves.
     * <p>
     * This is the main entry point for generating 2D value noise. It creates multiple
     * octaves with different frequencies and amplitudes, then combines them to
     * produce a complex, natural-looking 2D noise pattern.
     * </p>
     * <h3>Fractal Noise Generation:</h3>
     * <p>
     * The method uses 7 octaves, with each successive octave having double the
     * frequency and half the amplitude of the previous one. This follows the
     * standard approach for fractal noise generation, also known as "fractional Brownian motion" (fBm).
     * </p>
     * <p>
     * Octave parameters:
     * <ul>
     *   <li>Octave 1: frequency=2, amplitude=maxAmplitude/2</li>
     *   <li>Octave 2: frequency=4, amplitude=maxAmplitude/4</li>
     *   <li>Octave 3: frequency=8, amplitude=maxAmplitude/8</li>
     *   <li>Octave 4: frequency=16, amplitude=maxAmplitude/16</li>
     *   <li>Octave 5: frequency=32, amplitude=maxAmplitude/32</li>
     *   <li>Octave 6: frequency=64, amplitude=maxAmplitude/64</li>
     *   <li>Octave 7: frequency=128, amplitude=maxAmplitude/128</li>
     * </ul>
     * </p>
     * <p>
     * Bilinear interpolation is used to ensure smooth transitions in 2D space.
     * </p>
     *
     * @param with          the width of the output noise array
     * @param height        the height of the output noise array
     * @param maxAmplitude  the maximum amplitude for the first octave
     * @param seed          the seed for the random number generator (for reproducible results)
     * @return a 2D array of value noise values
     */
    public static double[][] generate2D(int with, int height, int maxAmplitude, long seed) {

        int nOctaves = 7;
        double[][] perlinWave = new double[height][with];
        Perlin perlin = new Perlin(seed);

        // Sum each octave
        for (int octaveIndex = 1; octaveIndex <= nOctaves; octaveIndex++) {
            int frequency = (int) Math.pow(2, octaveIndex);
            double amplitude = (double) maxAmplitude / frequency;

            double[][] octaveArr = perlin.octave2D(frequency, amplitude, with, height,
                    Interpolate2D.BILINEAR);

            for (int i = 0; i < height; i++) {
                for (int j = 0; j < with; j++) {
                    perlinWave[i][j] += octaveArr[i][j];
                }
            }
        }

        return perlinWave;
    }
}