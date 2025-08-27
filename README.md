# Perlin Noise Visualization

This project is a JavaFX application that visualizes 1D and 2D noise generation algorithms. Currently in a preliminary state, it demonstrates static noise generation which is useful for understanding how value noise works.

![Project Status](https://img.shields.io/badge/status-preliminary-orange)
![Language](https://img.shields.io/badge/language-Java-blue)
![License](https://img.shields.io/badge/license-MIT-green)

## Overview

This application visualizes procedural noise patterns using value noise techniques. While commonly referred to as "Perlin Noise":
The project demonstrates how multiple octaves of noise with different frequencies and amplitudes can be combined to create complex, natural-looking patterns.

## Features

- **1D Noise Visualization**: Generates and displays 1D noise patterns
- **2D Noise Visualization**: Generates and displays 2D noise patterns with terrain-like color mapping


## Current State

This project is in a preliminary state with the following limitations:

- Basic visualization without animation

## Future Enhancements

Planned improvements include:

- Animated visualization of noise evolution

## Technical Details

### Noise Generation

The noise is generated using a multi-octave approach:

1. **Octave Parameters**:
   - Octave 1: frequency=2, amplitude=max/2
   - Octave 2: frequency=4, amplitude=max/4
   - Octave 3: frequency=8, amplitude=max/8
   - Octave 4: frequency=16, amplitude=max/16 ...

2. **Interpolation Methods**:
   - **Linear**: Simple straight-line interpolation
   - **Fade**: Smoothstep function (6t^5 - 15t^4 + 10t^3) for smooth transitions


### Color Mapping

- **1D Mode**: Grayscale visualization
- **2D Mode**: Terrain-like color mapping:
  - Deep water (dark blue) → Water (blue)
  - Coast (yellow) → Land (brown)
  - Higher terrain (green) → Mountains (grey)
  - Snow caps (white)

## Getting Started

### Prerequisites

- Java 8 or higher
- JavaFX SDK

### Running the Application

```bash
# Compile
javac -cp "lib/*" src/principal/*.java -d out

# Run
java -cp "out;lib/*" principal.PerlingNoise
```

Or with custom canvas size:
```bash
java -cp "out;lib/*" principal.PerlingNoise --width=1024 --height=768
```

## Code Structure

- `PerlingNoise.java`: Main application class with UI controls
- `Renderer.java`: Handles visualization and color mapping
- `Generator.java`: Core noise generation algorithms

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Based on Ken Perlin's original noise algorithm concepts
- Inspired by various procedural generation tutorials and resources
