# fhp

Archival copy of an old Java FHP lattice gas simulator.

## Contents

- `src/fhp.java` - runnable Java source copied from the archived project.
- `build.xml` - original Ant build file copied from the archived project.
- `dist/fhp.jar` - original compiled jar copied from the archived project.
- `legacy/original-source/` - exact source/build-file copies from the old disk path.
- `legacy/original-dist/` - exact jar copy from the old disk path.
- `legacy/sample-output/` - archived `test-gas-###.ppm` and `test-vel-###` output.
- `artifacts/` - rendered video/GIF made from the archived gas-density frames.

The original files were copied from:

```text
/Users/steve/Library/CloudStorage/OneDrive-CoastHeavyIndustries,LLC/to sort/old disks/backups/linux-computer/mnt/sda/steve/alexandria/fhp
```

## Build

Install Java and Ant, then run:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk
export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"
ant dist
```

The source was verified to compile with Homebrew OpenJDK 26.0.1 and Ant 1.10.17.

## Run

The original jar has no `Main-Class`, so run it with:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk
export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"
java -cp dist/fhp.jar fhp
```

The built-in test writes `test-gas-###.ppm` and `test-vel-###` files into the current working directory. A full run is computationally large: it uses a `2048x1024` grid, writes 100 frames, and performs 1000 simulation iterations between frames.

## Render Existing Frames

```sh
ffmpeg -y -framerate 12 -i legacy/sample-output/test-gas-%03d.ppm \
  -vf "scale=1024:-2:flags=neighbor,format=yuv420p" artifacts/fhp-gas-flow.mp4

ffmpeg -y -framerate 12 -i legacy/sample-output/test-gas-%03d.ppm \
  -vf "fps=12,scale=768:-1:flags=neighbor" artifacts/fhp-gas-flow.gif
```
