#!/usr/bin/env python3
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
RUN_DIR = ROOT / "runs" / "fixed-full"
FRAME_DIR = ROOT / "artifacts" / "flow-art-frames"
OUT_W = 1024
OUT_H = 512
VEL_W = 15
VEL_H = 7


def read_ppm(path):
    with path.open("rb") as f:
        assert f.readline().strip() == b"P3"
        line = f.readline().strip()
        while line.startswith(b"#"):
            line = f.readline().strip()
        width, height = map(int, line.split())
        max_value = int(f.readline().strip())
        data = np.fromstring(f.read().decode("ascii"), sep=" ", dtype=np.float64)
    return (data.reshape(height, width, 3)[:, :, 0] / max_value)


def read_velocity(path):
    data = np.loadtxt(path, dtype=np.float64)
    return data[:, 0].reshape(VEL_H, VEL_W), data[:, 1].reshape(VEL_H, VEL_W)


def upsample(field, width=OUT_W, height=OUT_H):
    src_h, src_w = field.shape
    x = np.linspace(0, src_w - 1, width)
    y = np.linspace(0, src_h - 1, height)
    x0 = np.floor(x).astype(np.int32)
    y0 = np.floor(y).astype(np.int32)
    x1 = np.minimum(x0 + 1, src_w - 1)
    y1 = np.minimum(y0 + 1, src_h - 1)
    wx = x - x0
    wy = y - y0

    top = field[y0[:, None], x0[None, :]] * (1.0 - wx)[None, :] + field[y0[:, None], x1[None, :]] * wx[None, :]
    bottom = field[y1[:, None], x0[None, :]] * (1.0 - wx)[None, :] + field[y1[:, None], x1[None, :]] * wx[None, :]
    return top * (1.0 - wy)[:, None] + bottom * wy[:, None]


def smooth(field, passes=2):
    out = field
    for _ in range(passes):
        out = (
            out * 4.0 +
            np.roll(out, 1, axis=0) +
            np.roll(out, -1, axis=0) +
            np.roll(out, 1, axis=1) +
            np.roll(out, -1, axis=1)
        ) / 8.0
    return out


def gradient_lighting(density):
    gx = np.gradient(density, axis=1)
    gy = np.gradient(density, axis=0)
    light = -0.7 * gx - 0.45 * gy
    scale = np.percentile(np.abs(light), 98)
    if scale < 1e-9:
        return np.zeros_like(light)
    return np.clip(light / scale, -1.0, 1.0)


def hsv_to_rgb(h, s, v):
    h = np.mod(h, 1.0) * 6.0
    i = np.floor(h).astype(np.int32)
    f = h - i
    p = v * (1.0 - s)
    q = v * (1.0 - s * f)
    t = v * (1.0 - s * (1.0 - f))

    rgb = np.zeros(h.shape + (3,), dtype=np.float64)
    masks = [i == n for n in range(6)]
    rgb[masks[0]] = np.stack([v, t, p], axis=-1)[masks[0]]
    rgb[masks[1]] = np.stack([q, v, p], axis=-1)[masks[1]]
    rgb[masks[2]] = np.stack([p, v, t], axis=-1)[masks[2]]
    rgb[masks[3]] = np.stack([p, q, v], axis=-1)[masks[3]]
    rgb[masks[4]] = np.stack([t, p, v], axis=-1)[masks[4]]
    rgb[masks[5]] = np.stack([v, p, q], axis=-1)[masks[5]]
    return rgb


def draw_filled_circle(img):
    yy, xx = np.mgrid[0:img.shape[0], 0:img.shape[1]]
    cx = img.shape[1] * 0.25
    cy = img.shape[0] * 0.5
    radius = img.shape[0] * 0.1
    dist = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    body = dist <= radius
    rim = (dist > radius) & (dist <= radius + 4.0)
    highlight = (dist <= radius * 0.62) & (xx < cx - radius * 0.15) & (yy < cy - radius * 0.2)

    img[body] = np.array([9, 12, 16], dtype=np.uint8)
    img[rim] = np.array([215, 224, 230], dtype=np.uint8)
    img[highlight] = (img[highlight].astype(np.float64) * 0.55 + np.array([58, 70, 82]) * 0.45).astype(np.uint8)


def write_ppm(path, img):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as out:
        out.write(f"P6\n{img.shape[1]} {img.shape[0]}\n255\n".encode("ascii"))
        out.write(img.tobytes())


def main():
    gas_files = sorted(RUN_DIR.glob("test-gas-*.ppm"))
    vel_files = sorted(RUN_DIR.glob("test-vel-*"))
    if not gas_files or len(gas_files) != len(vel_files):
        raise SystemExit("expected matching gas and velocity frames in runs/fixed-full")

    velocities = [read_velocity(path) for path in vel_files]
    speed_fields = [np.hypot(vx, vy) for vx, vy in velocities]
    curl_fields = [np.gradient(vy, axis=1) - np.gradient(vx, axis=0) for vx, vy in velocities]
    speed_scale = max(1.0, np.percentile(np.concatenate([s.ravel() for s in speed_fields]), 96))
    curl_scale = max(1.0, np.percentile(np.abs(np.concatenate([c.ravel() for c in curl_fields])), 96))

    FRAME_DIR.mkdir(parents=True, exist_ok=True)
    for old in FRAME_DIR.glob("flow-art-*.ppm"):
        old.unlink()

    for i, (gas_file, (vx, vy), speed, curl) in enumerate(zip(gas_files, velocities, speed_fields, curl_fields)):
        density = smooth(upsample(read_ppm(gas_file)), 1)
        vx_u = smooth(upsample(vx), 3)
        vy_u = smooth(upsample(vy), 3)
        speed_u = smooth(upsample(speed), 3)
        curl_u = smooth(upsample(curl), 3)

        density_n = np.clip((density - 0.12) / 0.88, 0.0, 1.0)
        speed_n = np.clip(speed_u / speed_scale, 0.0, 1.0)
        curl_n = np.clip(curl_u / curl_scale, -1.0, 1.0)

        angle = np.arctan2(vy_u, vx_u)
        direction_hue = (angle + np.pi) / (2.0 * np.pi)
        speed_rgb = hsv_to_rgb(direction_hue, 0.42 + 0.32 * speed_n, 0.38 + 0.46 * speed_n)

        base = np.zeros((OUT_H, OUT_W, 3), dtype=np.float64)
        base[..., 0] = 16 + 82 * density_n
        base[..., 1] = 20 + 96 * density_n
        base[..., 2] = 28 + 122 * density_n

        curl_red = np.maximum(curl_n, 0.0)
        curl_blue = np.maximum(-curl_n, 0.0)
        curl_rgb = np.stack([
            238 * curl_red + 28 * curl_blue,
            46 + 48 * (1.0 - np.abs(curl_n)),
            245 * curl_blue + 42 * curl_red,
        ], axis=-1)

        lighting = gradient_lighting(density_n)
        combined = (
            base * (0.58 + 0.20 * density_n[..., None]) +
            speed_rgb * 255.0 * (0.13 + 0.20 * speed_n[..., None]) +
            curl_rgb * (0.26 + 0.22 * np.abs(curl_n)[..., None])
        )
        combined *= (0.92 + 0.20 * lighting[..., None])

        # Slight rightward smoky shimmer from speed so the wake reads as motion.
        shifted = np.roll(combined, 3, axis=1)
        combined = combined * 0.88 + shifted * (0.05 + 0.10 * speed_n[..., None])

        img = np.clip(combined, 0, 255).astype(np.uint8)
        draw_filled_circle(img)
        write_ppm(FRAME_DIR / f"flow-art-{i:03d}.ppm", img)

    print(f"wrote {len(gas_files)} frames to {FRAME_DIR}")
    print(f"speed_scale={speed_scale:.3f} curl_scale={curl_scale:.3f}")


if __name__ == "__main__":
    main()
