#!/usr/bin/env python3
import math
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
VELOCITY_DIR = ROOT / "runs" / "fixed-full"
FRAME_DIR = ROOT / "artifacts" / "curl-flow-frames"

GRID_W = 15
GRID_H = 7
OUT_W = 1024
OUT_H = 512


def read_velocity_frame(path):
    data = np.loadtxt(path, dtype=np.float64)
    return data[:, 0].reshape(GRID_H, GRID_W), data[:, 1].reshape(GRID_H, GRID_W)


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


def curl_color(curl, speed, curl_scale, speed_scale):
    c = np.clip(curl / curl_scale, -1.0, 1.0)
    s = np.clip(speed / speed_scale, 0.0, 1.0)
    base = 26.0 + 72.0 * s

    img = np.empty((curl.shape[0], curl.shape[1], 3), dtype=np.float64)
    pos = np.maximum(c, 0.0)
    neg = np.maximum(-c, 0.0)

    img[..., 0] = base + 150.0 * pos + 18.0 * neg
    img[..., 1] = base + 60.0 * (1.0 - np.abs(c))
    img[..., 2] = base + 165.0 * neg + 18.0 * pos

    return np.clip(img, 0, 255).astype(np.uint8)


def draw_line(img, x0, y0, x1, y1, color):
    x0 = int(round(x0))
    y0 = int(round(y0))
    x1 = int(round(x1))
    y1 = int(round(y1))

    dx = abs(x1 - x0)
    dy = -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx + dy

    while True:
      if 0 <= x0 < img.shape[1] and 0 <= y0 < img.shape[0]:
          img[max(0, y0-1):min(img.shape[0], y0+2), max(0, x0-1):min(img.shape[1], x0+2)] = color
      if x0 == x1 and y0 == y1:
          break
      e2 = 2 * err
      if e2 >= dy:
          err += dy
          x0 += sx
      if e2 <= dx:
          err += dx
          y0 += sy


def draw_arrow(img, x, y, vx, vy, speed_scale):
    speed = math.hypot(vx, vy)
    if speed < 1e-9:
        return

    length = min(42.0, 8.0 + 34.0 * math.sqrt(min(speed / speed_scale, 1.0)))
    ux = vx / speed
    uy = vy / speed
    x1 = x + ux * length
    y1 = y + uy * length
    color = np.array([245, 245, 235], dtype=np.uint8)

    draw_line(img, x, y, x1, y1, color)

    angle = math.atan2(uy, ux)
    for delta in (2.55, -2.55):
        hx = x1 + math.cos(angle + delta) * 9.0
        hy = y1 + math.sin(angle + delta) * 9.0
        draw_line(img, x1, y1, hx, hy, color)


def draw_circle(img):
    cx = OUT_W * 0.25
    cy = OUT_H * 0.5
    radius = OUT_H * 0.1
    color = np.array([12, 12, 12], dtype=np.uint8)

    for a in np.linspace(0, 2 * math.pi, 900):
        x = int(round(cx + math.cos(a) * radius))
        y = int(round(cy + math.sin(a) * radius))
        if 0 <= x < OUT_W and 0 <= y < OUT_H:
            img[max(0, y-2):min(OUT_H, y+3), max(0, x-2):min(OUT_W, x+3)] = color


def write_ppm(path, img):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as out:
        out.write(f"P6\n{img.shape[1]} {img.shape[0]}\n255\n".encode("ascii"))
        out.write(img.tobytes())


def main():
    velocity_files = sorted(VELOCITY_DIR.glob("test-vel-*"))
    fields = [read_velocity_frame(path) for path in velocity_files]

    curls = []
    speeds = []
    for vx, vy in fields:
        curl = np.gradient(vy, axis=1) - np.gradient(vx, axis=0)
        curls.append(curl)
        speeds.append(np.hypot(vx, vy))

    curl_scale = max(1.0, np.percentile(np.abs(np.concatenate([c.ravel() for c in curls])), 97))
    speed_scale = max(1.0, np.percentile(np.concatenate([s.ravel() for s in speeds]), 97))

    FRAME_DIR.mkdir(parents=True, exist_ok=True)
    for old_frame in FRAME_DIR.glob("curl-flow-*.ppm"):
        old_frame.unlink()

    for index, ((vx, vy), curl, speed) in enumerate(zip(fields, curls, speeds)):
        img = curl_color(
            upsample(curl),
            upsample(speed),
            curl_scale,
            speed_scale,
        )

        for row in range(GRID_H):
            for col in range(GRID_W):
                x = (col + 0.5) * OUT_W / GRID_W
                y = (row + 0.5) * OUT_H / GRID_H
                draw_arrow(img, x, y, vx[row, col], vy[row, col], speed_scale)

        draw_circle(img)
        write_ppm(FRAME_DIR / f"curl-flow-{index:03d}.ppm", img)

    print(f"wrote {len(fields)} frames to {FRAME_DIR}")
    print(f"curl_scale={curl_scale:.3f} speed_scale={speed_scale:.3f}")


if __name__ == "__main__":
    main()
