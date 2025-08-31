# Perlin Noise Visualization

A JavaFX application that visualizes 1D and 2D procedural noise generation algorithms with real-time navigation capabilities.

![Project Status](https://img.shields.io/badge/status-stable-green)
![Language](https://img.shields.io/badge/language-Java-blue)
![License](https://img.shields.io/badge/license-MIT-green)

## Overview

This application demonstrates **value noise** generation (commonly called "Perlin noise") using a multi-octave fractal approach. The implementation provides:

- **1D Noise Visualization**: Generates terrain-like height maps with smooth scrolling
- **2D Noise Visualization**: Creates terrain maps with color-coded elevation
- **Infinite World Navigation**: Seamless exploration using arrow keys
- **Real-time Generation**: Smooth navigation without loading delays

## Key Features

### Navigation System
- **Arrow Keys**: Navigate through infinite procedural worlds
- **Four-Direction Movement**: World coordinates go negative as well as positive, so ←/↑ explore the same lattice as →/↓
- **Seamless Continuity**: Return to previous locations with identical results
- **Memory Efficient**: Generates content on-demand without storing large maps

### Playback Controls
- **Iterate**: Advances the world 4 cells per rendered frame (`SCROLL_STEP`) using the JavaFX animation timer, which repaints one out of every 4 ticks (`FRAME_SKIP`)
- **Stop**: Pauses the animation and leaves the last generated frame on screen
- **Step**: Advances exactly one animated frame, which is 4 cells, useful to inspect the noise frame by frame
- **Restart**: Returns to the world origin and cancels any running animation

### Noise Generation
- **Multi-Octave Approach**: Combines 8 octaves for natural-looking patterns
- **Consistent Seeding**: Same coordinates always produce the same values
- **Smooth Interpolation**: Uses fade interpolation for seamless transitions

## Technical Implementation

### Architecture Overview

```
PerlinNoise.java     - Main application, UI controller and frame loop
├── Generate1D.java  - 1D noise generation with offset support
├── Generate2D.java  - 2D noise generation with X/Y offset support
└── Renderer.java    - Visualization and color mapping
```

The frame loop lives in `PerlinNoise`: the `AnimationTimer` ticks on every frame and calls
`Renderer.updateRegion()`, which delegates each animated frame to a callback registered with
`Renderer.setIterationAction(...)`. The renderer stays a pure view; the world offsets and the
noise generators remain in the application.

### Cells vs Pixels

All noise and drawing logic works in **cells**. Pixels exist only inside `Renderer`:

```java
PIXELS_PER_CELL = 4          // one cell covers 4 x 4 real pixels
widthInCells  = width  / 4   // 800 px -> 200 cells
heightInCells = height / 4   // 600 px -> 150 cells
```

`--width` and `--height` are in **pixels**, and `Renderer` is the only class that converts
them to cells (`widthInCells()`, `heightInCells()`): the generators never see a pixel. The
cell buffer reused while painting is `PIXELS_PER_CELL * PIXELS_PER_CELL * 4 = 64` bytes
(`CELL_BUFFER_SIZE`), filled once per cell and blitted with a single `setPixels` call.

### Critical Design Decisions

#### 1. Offset-Based World Generation
**Problem**: How to generate infinite worlds without storing massive arrays?

**Solution**: Use coordinate offsets to simulate infinite space:
```java
// World coordinates = screen coordinates + offset
long worldX = j + offsetX;
long worldY = i + offsetY;
```

Offsets are plain signed integers, so the world extends in every direction. The grid is
resolved with `Math.floorDiv` / `Math.floorMod` over `long` world positions, which keeps
negative coordinates on the same lattice as positive ones.

#### 2. Deterministic Random Value Access
**Problem**: Need consistent values when revisiting locations.

**Solution**: Pre-generate one pool of random values (fixed seed `201012`) and index it
deterministically. The two generators do it differently:

```java
// 1D: every octave owns a slice of the pool and walks it linearly
int index = (int) Math.floorMod((long) randomOffsetForOctave + nodeIndex, pool.length);

// 2D: both world coordinates are hashed into a single index
long hash = (gridY * 31L + gridX) * 17L;
return (int) Math.floorMod(hash, poolSize);
```

`Math.floorMod` is used instead of `Math.abs(...) % n`, which overflows on
`Integer.MIN_VALUE` and would return a negative index on the left side of the origin.

#### 3. Continuous Navigation
**Problem**: Avoid discontinuities when moving between "screens".

**Solution**: 
- Generate values once with fixed seed
- Use modulo arithmetic for array bounds
- Apply offsets during interpolation, not generation

#### 4. Focus Management
**Problem**: UI controls capturing arrow key events.

**Solution**: Make all controls non-focusable:
```java
button.setFocusTraversable(false);
```

#### 5. Animation Delegation
**Problem**: The renderer cannot generate noise, but it owns the frame loop state.

**Solution**: The application registers a per-frame action on the renderer:
```java
// In PerlinNoise.initialize()
renderer.setIterationAction(this::animate);
animationTimer.start();

// In Renderer
public void updateRegion() {
    if (!runningIteration && !runningStep) return;
    iteration();          // runs the registered action
    runningStep = false;  // Step mode lasts exactly one frame
}
```

### Noise Generation Algorithm

#### 1D Implementation
```java
// For each octave
int stepLength = distanceBetweenNodes(octaveIndex);   // 128, 64, ... down to 1 cell
long worldPos = (long) pos + offset;
long nodeIndex = Math.floorDiv(worldPos, stepLength);
int relativePos = Math.floorMod(worldPos, stepLength);

// Interpolate between the two neighbouring nodes and accumulate the octave
value = interpolator.calculate(0, node, stepLength, nextNode, relativePos);
target[pos] += value * octaveAmplitude;
```

#### 2D Implementation
```java
// Convert screen cells to world cells
long worldX = (long) j + offsetX;
long worldY = (long) i + offsetY;

// Find the grid cell and the position inside it
long gridX = Math.floorDiv(worldX, stepLength);
long gridY = Math.floorDiv(worldY, stepLength);
int relativeX = Math.floorMod(worldX, stepLength);
int relativeY = Math.floorMod(worldY, stepLength);

// Bilinear interpolation between the 4 corners, accumulated into the frame
target[i][j] += interpolator.calculate(0, 0, stepLength, stepLength,
        v1, v2, v3, v4, relativeX, relativeY);
```

### Memory Management

- **One pool per generator**: built once in the constructor with the fixed seed, then
  reused for every frame
- **1D**: 412 doubles for a 200-cell wide grid, split into per-octave slices of 3, 5, 8,
  15, 27, 52, 102 and 200 values
- **2D**: `widthInCells * heightInCells * 4` doubles: 120,000 values (about 940 KB) for
  800x600
- **No per-frame pool**: a frame allocates only the final pattern (`double[200]` in 1D,
  `double[150][200]` in 2D), because each octave is accumulated in place
- **Modulo wrapping**: `Math.floorMod` keeps every index in range, on both sides of the
  origin

## Getting Started

### Prerequisites
- JDK 17 or higher
- JavaFX SDK 21 (the project is configured against `javafx-sdk-21.0.8`)

### Building and Running

Set `JAVAFX_HOME` to the JavaFX `lib` folder, for example
`C:\Program Files\Java\javafx-sdk-21.0.8\lib`.

```bash
# Compile
javac -d out --module-path "%JAVAFX_HOME%" --add-modules javafx.controls,javafx.graphics src/principal/*.java

# Run with default size (800x600)
java --module-path "%JAVAFX_HOME%" --add-modules javafx.controls,javafx.graphics -cp out principal.PerlinNoise

# Run with custom canvas size (1024x768 pixels, that is a 256x192 cell grid)
java --module-path "%JAVAFX_HOME%" --add-modules javafx.controls,javafx.graphics -cp out principal.PerlinNoise --width=1024 --height=768
```

### Controls

| Key | Action |
|-----|--------|
| ← → | Navigate horizontally, 15 cells per press (both 1D and 2D) |
| ↑ ↓ | Move the vertical offset, 15 cells per press (only the 2D view uses it) |
| 1D Button | Switch to 1D terrain view |
| 2D Button | Switch to 2D map view |
| Restart Button | Reset the world offsets to the origin and stop the animation |
| Iterate Button | Scroll the world continuously, 4 cells per rendered frame |
| Stop Button | Pause the animation |
| Step Button | Advance a single rendered frame (4 cells) |

All buttons are non-focusable, so they never steal the arrow keys from the canvas.

## Technical Challenges Solved

### 1. Infinite World Illusion
**Challenge**: Create the appearance of infinite worlds without infinite memory.

**Solution**: 
- Use mathematical functions instead of stored data
- Coordinate transformation: `world_pos = screen_pos + offset`
- Deterministic value lookup based on world coordinates

### 2. Seamless Boundaries
**Challenge**: Avoid visible seams when scrolling between regions.

**Solution**:
- Single random value array for entire world
- Consistent hash function for coordinate-to-index mapping
- Interpolation uses world coordinates, not screen coordinates
- `floorDiv`/`floorMod` keep the grid and pool indices valid on both sides of the origin

### 3. Performance Optimization
**Challenge**: Real-time generation without frame drops.

**Solution**:
- Pre-compute random values once
- Simple hash function for fast lookups
- Minimal object allocation during rendering

### 4. UI Focus Issues
**Challenge**: Arrow keys controlling UI elements instead of navigation.

**Solution**:
- Replace ComboBox with non-focusable buttons
- Set `setFocusTraversable(false)` on all controls, action buttons included
- Handle key events at scene level

## Color Mapping

### 1D Mode (Grayscale)
- The **shape** encodes the height: the surface row of each column comes from its noise value
- The fill is a fixed vertical gradient, palette index 150 at the surface down to 45 at the
  bottom row, so it is unrelated to the height itself
- Palette indices 0-255 are a black to white ramp; the sky uses index 0

### 2D Mode (Terrain Colors)
Palette ranges: deep blue below 30 %, blue 30-40 %, yellow 40-50 %, brown 50-60 %,
green 60-70 %, grey 70-80 % and white above 80 %.

- **Deep Blue**: Water/ocean depths
- **Blue**: Shallow water
- **Yellow**: Beaches/coastline  
- **Brown**: Land and hills
- **Green**: Higher terrain and forests
- **Gray**: High peaks
- **White**: Snow-capped summits

## Mathematical Foundation

### Value Noise vs Perlin Noise
This implementation uses **value noise**, not true Perlin noise:

- **Value Noise**: Assigns random scalar values to grid points, interpolates between them
- **Perlin Noise**: Assigns gradient vectors to grid points, interpolates dot products

Value noise is simpler to implement and produces visually similar results for most applications.

### Octave Combination

The frequency doubles on every octave: `stepLength = 2^(7 - octave)`, so octave 0 places a
node every 128 cells and octave 7 every cell. Going down the list is going towards detail.

The amplitude halves on every octave, but the two generators start from a different value:

| Octave | 1D amplitude (`A = heightInCells`) | 2D amplitude (`A = NOISE_2D_AMPLITUDE`) |
|---|---|---|
| 0 | A/4 | A/2 |
| 1 | A/8 | A/4 |
| 2 | A/16 | A/8 |
| 3 | A/32 | A/16 |
| 4 | A/128 | A/32 |
| 5 | A/256 | A/64 |
| 6 | A/512 | A/128 |
| 7 | A/1024 | A/256 |

The 1D generator adds one extra halving to the octaves with `stepLength <= 8` (octaves 4
to 7, the rows from A/128 down): at that level of detail a half-step amplitude makes the
silhouette too jagged.

With the default sizes (A = 150 in 1D, 200 in 2D) the eight amplitudes add up to 72.5 and
199.2 cells, so both patterns fit inside the grid.

Final value = sum of all octaves

### Interpolation Methods
- **Linear**: Fast but can show discontinuities
- **Fade**: Smooth transitions using `6t^5 - 15t^4 + 10t^3`
- **Bilinear**: 2D interpolation between four corner values. The code ships two flavours:
  `BILINEAR` (area-based weighting, the one in use) and `BILINEAR2` (parametric, kept as a
  reference)

## Known Limitations

1. **Pattern Repetition (1D)**: the pool is walked with the node number, so the curve repeats exactly every `412 * 128 = 52,736` cells
2. **Diagonal Aliasing (2D)**: the hash is `(gridY * 31 + gridX) * 17`, so the cells `(x + 31, y)` and `(x, y - 1)` always read the same pool entry; neighbouring diagonals are correlated
3. **Byte Wrap (2D)**: the eight octaves add up to about `±NOISE_2D_AMPLITUDE` while the palette index is a `byte`, so the highest peaks wrap from 255 to 0 and show up as water (around 1.5 % of the cells). Normalizing the sum would change the picture, so it is left as it is
4. **Memory Grows With The Window**: the pools are built once per grid size and then reused, but a bigger window means a bigger pool (2D: 4 values per cell)
5. **Animation Cost**: Iterate advances 4 cells per rendered frame and recomputes all 8 octaves, so the 2D view is much heavier than the 1D one
6. **Offset Range**: Offsets are `int`, so navigation beyond `Integer.MAX_VALUE` cells overflows; world positions are computed as `long` but start from an `int` offset

## Future Enhancements

- [ ] True Perlin noise implementation with gradient vectors
- [ ] Configurable octave parameters via UI
- [ ] Export functionality for generated maps
- [ ] 3D noise visualization
- [ ] Custom color palette editor
- [ ] Animation/time-based noise evolution

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Based on Ken Perlin's original noise algorithm concepts
- Inspired by various procedural generation tutorials and resources
- JavaFX community for UI implementation guidance

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Technical Notes for Developers

### Key Classes Explained

#### `PerlinNoise`
- Owns the `offsetX`/`offsetY` world position and the cell grid handed to the renderer
- Starts the `AnimationTimer` and supplies `animate()` as the per-frame action
- `restart()` resets the offsets to the origin and cancels Iterate and Step modes

#### `Renderer`
- Pure view: palettes, pixel buffers and the `WritableImage`
- `drawCells(byte[][])` paints a whole grid: for every cell it fills the 64-byte buffer
  (`fillCellBuffer`) and blits it to the screen (`drawCell`), which is also where the
  `setPixels` call and the edge clipping live
- `setIterationAction(Runnable)` receives the per-frame action from the application
- `updateRegion()` runs that action only while Iterate or Step is active
- `resetIteration()` cancels both animation modes

#### `Generate1D` / `Generate2D`
- Stateful generators: the random pool is built in the constructor, so a frame only pays for
  the octave loop
- `generate(offset)` / `generate(offsetX, offsetY)` return one value per cell, in cells and
  centred on zero
- Every octave is accumulated into the target, so a frame allocates a single array or matrix
- The 1D amplitude is `heightInCells`; the 2D amplitude is `NOISE_2D_AMPLITUDE` (200),
  independent of the grid height

#### `Generate1D.Rnd`
- Builds the 1D pool once, with the fixed seed
- Slices it per octave (`offsetForOctave`): the coarse octaves need far fewer values than
  the fine ones, so they read overlapping windows of the same array

#### `Generate2D.Rnd2D`  
- Builds the 2D pool once, with the same fixed seed
- Both coordinates are hashed together with prime multiplication (31 and 17)
- That linear combination is not injective against the grid, which is the origin of the
  diagonal aliasing listed under Known Limitations

#### `Generate2D.hashIndex`
- Maps a world grid cell to an index of the random pool
- Uses `long` arithmetic and `Math.floorMod`, so cells on the negative side of the origin never produce a negative index

#### Interpolation Classes
- Functional interfaces for pluggable interpolation methods
- Separate 1D and 2D implementations
- Easy to extend with new interpolation algorithms

### Debugging Tips

1. **Repetition Issues**: Check random array size calculation
2. **Discontinuities**: Verify offset application in interpolation
3. **Performance**: Profile the hash function and interpolation calls
4. **UI Focus**: Ensure all interactive elements have `setFocusTraversable(false)`
5. **Animation Not Running**: Confirm `animationTimer.start()` is reached and `setIterationAction(...)` was called before pressing Iterate
6. **Negative Navigation**: Offsets below zero must go through `floorDiv`/`floorMod`; plain `/` and `%` throw `ArrayIndexOutOfBoundsException`

### Extension Points

- **New Interpolation Methods**: Implement the functional interfaces
- **Different Noise Types**: Extend the generator classes
- **Custom Rendering**: Modify the `Renderer` class
- **Additional Controls**: Add to the button panel with proper focus management