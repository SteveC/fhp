# fhp

Archival copy of an old Java FHP lattice gas simulator.

## Contents

- `src/fhp.java` - runnable Java source, with the lookup table repaired.
- `build.xml` - original Ant build file copied from the archived project.
- `dist/fhp.jar` - current compiled jar built from `src/fhp.java`.
- `legacy/original-source/` - exact source/build-file copies from the old disk path.
- `legacy/original-dist/` - exact jar copy from the old disk path.
- `legacy/sample-output/` - archived `test-gas-###.ppm` and `test-vel-###` output.
- `artifacts/` - rendered video/GIF captures.

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

The jar has no `Main-Class`, so run it with:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk
export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"
java -cp dist/fhp.jar fhp
```

The built-in test writes `test-gas-###.ppm` and `test-vel-###` files into the current working directory. A full run is computationally large: it uses a `2048x1024` grid, writes 100 frames, and performs 1000 simulation iterations between frames.

## Lookup Table Fix

The archived source had a hand-written 64-entry collision table with missing `break` statements in the 3-particle and 4-particle rules. Those cases fell through to the default continuation rule, so most of the intended FHP collisions never happened.

The current source generates the table from particle-count and momentum equivalence classes, then verifies every entry at startup. Each table output must conserve particle count and momentum after converting from the stored reverse-direction encoding used by the movement step.

## Render Existing Frames

```sh
ffmpeg -y -framerate 12 -i legacy/sample-output/test-gas-%03d.ppm \
  -vf "scale=1024:-2:flags=neighbor,format=yuv420p" artifacts/fhp-gas-flow.mp4

ffmpeg -y -framerate 12 -i legacy/sample-output/test-gas-%03d.ppm \
  -vf "fps=12,scale=768:-1:flags=neighbor" artifacts/fhp-gas-flow.gif
```

The repaired-code render is:

```text
artifacts/fhp-gas-flow-fixed.mp4
artifacts/fhp-gas-flow-fixed.gif
```

## Render Velocity Curl

The simulator also writes coarse velocity fields as `test-vel-###` files. To render curl/vorticity-colored flow from a completed run in `runs/fixed-full`:

```sh
python3 tools/render_velocity_curl.py

ffmpeg -y -framerate 12 -i artifacts/curl-flow-frames/curl-flow-%03d.ppm \
  -vf "format=yuv420p" artifacts/fhp-curl-flow-fixed.mp4

ffmpeg -y -framerate 12 -i artifacts/curl-flow-frames/curl-flow-%03d.ppm \
  -vf "fps=12,scale=768:-1:flags=neighbor" artifacts/fhp-curl-flow-fixed.gif
```

The curl render is:

```text
artifacts/fhp-curl-flow-fixed.mp4
artifacts/fhp-curl-flow-fixed.gif
```
