"""
Renders the CrewX menu assets offline (run once; the results are committed as resources):

  space.jpg          far layer: deep space + sparse stars                      (1920x1080)
  earth.jpg          planet + atmosphere colour, premultiplied over black      (2304x1296)
  earth_alpha.png    coverage of the planet layer (grayscale)                  (2304x1296)
  glare.jpg          the sun and its glow, drawn with additive blending        (1280x720)
  vignette.png       soft dark corners (alpha)                                 (256x144)
  clock.png/.txt     glyph atlas for the clock digits (Inter Display Bold)
  icon_*.png         menu icons (white on transparent)

Earth surface, clouds and city lights: NASA Visible Earth / Black Marble (public domain)
  https://eoimages.gsfc.nasa.gov/images/imagerecords/57000/57752/land_shallow_topo_8192.tif
  https://eoimages.gsfc.nasa.gov/images/imagerecords/57000/57747/cloud_combined_8192.tif
  https://eoimages.gsfc.nasa.gov/images/imagerecords/79000/79765/dnb_land_ocean_ice.2012.3600x1800.jpg
Clock font: Inter Display Bold (SIL Open Font License 1.1) - only the glyphs 0-9 and ':' are rendered.

usage: python3 render_menu_assets.py <nasa-texture-dir> <inter-display-bold.otf> <out-dir>
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

Image.MAX_IMAGE_PIXELS = None
RNG = np.random.default_rng(7)

# ---------------------------------------------------------------- composition (shared by all layers)
# normalised canvas (16:9): horizon top at 47 % of the height, the sun rising on the limb left of centre
HORIZON_TOP = 0.47
PLANET_RADIUS = 1.55          # in canvas heights
SUN_X = 0.385                 # in canvas widths


def planet_geometry(w, h):
    r = PLANET_RADIUS * h
    cx, cy = 0.5 * w, HORIZON_TOP * h + r
    return cx, cy, r


def limb_y(x, w, h):
    cx, cy, r = planet_geometry(w, h)
    return cy - math.sqrt(max(0.0, r * r - (x - cx) ** 2))


def sun_screen(w, h):
    x = SUN_X * w
    return x, limb_y(x, w, h) - 0.012 * h


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def to_srgb8(linear):
    linear = np.clip(linear, 0.0, 1.0)
    srgb = np.where(linear <= 0.0031308, linear * 12.92, 1.055 * np.power(linear, 1 / 2.4) - 0.055)
    # triangular dither: smooth glows (sun, atmosphere) would otherwise show 8-bit banding
    noise = (RNG.random(srgb.shape, dtype=np.float32) - RNG.random(srgb.shape, dtype=np.float32))
    return np.clip(srgb * 255 + 0.5 + noise, 0, 255).astype(np.uint8)


def to_linear(img8):
    c = img8.astype(np.float32) / 255.0
    return np.where(c <= 0.04045, c / 12.92, np.power((c + 0.055) / 1.055, 2.4))


def tonemap(x, exposure=1.0):
    return 1.0 - np.exp(-x * exposure)


def sample(tex, lon, lat):
    """Bilinear sample of an equirectangular texture (H, W, C) at lon/lat in radians."""
    th, tw = tex.shape[0], tex.shape[1]
    u = (lon / (2 * math.pi) + 0.5) % 1.0 * tw - 0.5
    v = (0.5 - lat / math.pi) * th - 0.5
    x0 = np.floor(u).astype(np.int64)
    y0 = np.clip(np.floor(v).astype(np.int64), 0, th - 1)
    fx = (u - x0)[..., None]
    fy = (v - np.floor(v))[..., None]
    x0 %= tw
    x1 = (x0 + 1) % tw
    y1 = np.clip(y0 + 1, 0, th - 1)
    a = tex[y0, x0].astype(np.float32)
    b = tex[y0, x1].astype(np.float32)
    c = tex[y1, x0].astype(np.float32)
    d = tex[y1, x1].astype(np.float32)
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


# ---------------------------------------------------------------- planet layer
def render_earth(nasa, out, w=2304, h=1296):
    land = np.asarray(Image.open(os.path.join(nasa, "land_shallow_topo_8192.tif")).convert("RGB"))
    clouds = np.asarray(Image.open(os.path.join(nasa, "cloud_combined_8192.tif")).convert("L"))
    lights = np.asarray(Image.open(os.path.join(nasa, "dnb_land_ocean_ice.2012.3600x1800.jpg")).convert("L"))

    cx, cy, r = planet_geometry(w, h)
    py, px = np.mgrid[0:h, 0:w].astype(np.float32)
    dx = (px + 0.5 - cx) / r
    dy = (cy - (py + 0.5)) / r
    r2 = dx * dx + dy * dy
    dist = np.sqrt(r2)
    inside = r2 < 1.0
    nz = np.sqrt(np.clip(1.0 - r2, 0.0, 1.0))

    # sun: just above the limb, slightly behind the planet (we look at the night side with a bright rim)
    sx, sy = sun_screen(w, h)
    u = np.array([(sx - cx) / r, (cy - sy) / r])
    u /= np.linalg.norm(u)
    sun = np.array([u[0] * 0.45, u[1] * 0.45, -0.90])
    sun /= np.linalg.norm(sun)

    # orientation of the globe: Atlantic / West Africa / Europe under the visible limb
    yaw, tilt = math.radians(-18.0), math.radians(-52.0)
    nx, ny = dx, dy
    # rotate around X (tilt) then Y (yaw)
    y1 = ny * math.cos(tilt) - nz * math.sin(tilt)
    z1 = ny * math.sin(tilt) + nz * math.cos(tilt)
    x2 = nx * math.cos(yaw) + z1 * math.sin(yaw)
    z2 = -nx * math.sin(yaw) + z1 * math.cos(yaw)
    lat = np.arcsin(np.clip(y1, -1, 1))
    lon = np.arctan2(x2, z2)

    idx = np.nonzero(inside)
    lon_i, lat_i = lon[idx], lat[idx]
    albedo = to_linear(np.clip(sample(land, lon_i, lat_i), 0, 255)) * 0.92
    cloud = (sample(clouds[..., None], lon_i, lat_i)[:, 0] / 255.0) ** 1.15
    city = (sample(lights[..., None], lon_i, lat_i)[:, 0] / 255.0)
    city = np.clip((city - 0.10) / 0.9, 0, 1) ** 2.2

    n = np.stack([dx[idx], dy[idx], nz[idx]], axis=1)
    ndl = n @ sun
    mu = nz[idx]
    day = smoothstep(-0.03, 0.22, ndl)
    surface = albedo * (1 - cloud[:, None] * 0.92) + cloud[:, None] * np.array([0.93, 0.95, 0.98])
    warm = smoothstep(0.0, 0.45, ndl)[:, None]
    sun_col = np.array([1.0, 0.62, 0.36]) * (1 - warm) + np.array([1.0, 0.97, 0.93]) * warm
    lit = surface * day[:, None] * sun_col * 1.9
    earthshine = surface * np.array([0.55, 0.66, 0.88]) * 0.075
    night = city[:, None] * np.array([1.0, 0.72, 0.42]) * (1 - day[:, None]) * (1 - cloud[:, None] * 0.75) * 0.55
    fres = (1.0 - mu) ** 2.6
    toward_sun = np.clip(ndl + 0.62, 0, 1) ** 1.8
    atm_col = np.array([0.22, 0.48, 1.0])
    haze = atm_col * fres[:, None] * (0.10 + 1.25 * toward_sun[:, None])
    color_in = (lit + earthshine + night) * (1 - fres[:, None] * 0.55) + haze

    rgb = np.zeros((h, w, 3), np.float32)
    rgb[idx] = color_in

    # atmosphere outside the limb: thin bright rim + wide soft halo, brighter and warmer towards the sun
    d = np.clip(dist - 1.0, 0, None)
    lx, ly = dx / np.maximum(dist, 1e-6), dy / np.maximum(dist, 1e-6)
    k = np.clip(lx * u[0] + ly * u[1], 0, 1)
    strength = 0.38 + 0.45 * k ** 2 + 1.5 * k ** 16
    # only the thin, bright part of the atmosphere lives in this (alpha) layer; the wide faint halo is
    # additive light in glare.jpg, where it cannot band through 8-bit alpha steps
    rim = np.exp(-d / 0.0040) * 0.95 + np.exp(-d / 0.016) * 0.22
    warmth = (k ** 24)[..., None]
    glow_col = atm_col * (1 - warmth) + np.array([1.0, 0.55, 0.25]) * warmth
    glow = glow_col * (rim * strength)[..., None]
    outside = ~inside
    rgb[outside] = glow[outside]
    # a little of the rim also sits on the planet's edge
    edge = np.exp(-np.clip(1.0 - dist, 0, None) / 0.004) * inside
    rgb += (glow_col * (0.45 * strength)[..., None]) * edge[..., None]

    rgb = tonemap(rgb, 1.15)
    # coverage: opaque planet (anti-aliased edge), glow alpha from its brightness
    aa = np.clip((1.0 - dist) * r + 0.5, 0, 1)
    glow_a = np.clip(rgb.max(axis=2) * 1.15, 0, 1)
    alpha = np.maximum(aa, glow_a * (1 - aa))
    # store colour premultiplied over black (the game multiplies by alpha again -> undo here)
    color = np.where(alpha[..., None] > 1e-4, rgb / np.maximum(alpha[..., None], 1e-4), 0)
    color = np.clip(color, 0, 1)
    Image.fromarray(to_srgb8(color)).save(os.path.join(out, "earth.jpg"), quality=90, subsampling=0, optimize=True)
    alpha8 = np.clip(alpha * 255 + 0.5 + (RNG.random(alpha.shape) - RNG.random(alpha.shape)), 0, 255)
    alpha8[alpha < 0.004] = 0   # nothing left far from the limb: no stray specks in space
    Image.fromarray(alpha8.astype(np.uint8), "L").save(os.path.join(out, "earth_alpha.png"), optimize=True)
    # preview only
    prev = to_srgb8(rgb)
    Image.fromarray(prev).save(os.path.join(out, "_preview_earth.png"))


# ---------------------------------------------------------------- far layer: space
def render_space(out, w=1920, h=1080):
    yy = np.linspace(0, 1, h, dtype=np.float32)[:, None, None]
    base = np.zeros((h, w, 3), np.float32)
    base += np.array([0.006, 0.009, 0.018]) * (1 - yy) + np.array([0.002, 0.003, 0.006]) * yy
    # very faint large-scale haze (not a busy nebula)
    noise = RNG.random((h // 16, w // 16)).astype(np.float32)
    haze = np.asarray(Image.fromarray((noise * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
                      .filter(ImageFilter.GaussianBlur(60)), np.float32) / 255.0
    base += (haze[..., None] - 0.5) * np.array([0.010, 0.012, 0.022])
    # stars: mostly tiny and dim, a few brighter ones
    count = 1100
    xs = RNG.random(count) * w
    ys = RNG.random(count) * h
    mag = RNG.pareto(2.6, count)
    bright = np.clip(0.035 + mag * 0.05, 0, 1.6)
    temp = RNG.random(count)
    tint = np.stack([0.85 + 0.15 * temp, 0.88 + 0.08 * temp, 1.0 - 0.15 * temp], axis=1)
    img = base
    for x, y, b, t in zip(xs, ys, bright, tint):
        size = 0.55 + min(1.4, b * 0.9)
        r = int(math.ceil(size * 3))
        x0, x1 = max(0, int(x) - r), min(w, int(x) + r + 1)
        y0, y1 = max(0, int(y) - r), min(h, int(y) + r + 1)
        gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5 - x, np.arange(y0, y1) + 0.5 - y)
        g = np.exp(-(gx * gx + gy * gy) / (2 * size * size * 0.35))
        img[y0:y1, x0:x1] += g[..., None] * t * b * 0.55
    img = tonemap(img, 1.0)
    Image.fromarray(to_srgb8(img)).save(os.path.join(out, "space.jpg"), quality=90, optimize=True)


# ---------------------------------------------------------------- the sun (additive layer)
def render_glare(out, w=1280, h=720):
    sx, sy = sun_screen(w, h)
    py, px = np.mgrid[0:h, 0:w].astype(np.float32)
    dx, dy = (px - sx) / h, (py - sy) / h
    d = np.sqrt(dx * dx + dy * dy)
    ang = np.arctan2(dy, dx)
    core = np.exp(-(d / 0.0085) ** 2) * 4.0
    inner = np.exp(-d / 0.022) * 1.1
    mid = np.exp(-d / 0.075) * 0.35
    wide = np.exp(-d / 0.30) * 0.10
    rays = np.zeros_like(d)
    for i, (a, length, width) in enumerate([(0.0, 0.55, 0.020), (0.52, 0.32, 0.028), (1.15, 0.42, 0.022),
                                            (1.95, 0.30, 0.03), (2.55, 0.50, 0.018), (3.14, 0.60, 0.016),
                                            (3.75, 0.36, 0.026), (4.35, 0.28, 0.03), (5.05, 0.40, 0.022),
                                            (5.65, 0.34, 0.025)]):
        delta = np.angle(np.exp(1j * (ang - a)))
        rays += np.exp(-(delta / (width * 1.8)) ** 2) * np.exp(-d / (length * 0.28)) * 0.10
    streak = np.exp(-(dy / 0.0035) ** 2) * np.exp(-np.abs(dx) / 0.28) * 0.08
    warm = np.array([1.0, 0.62, 0.30])
    white = np.array([1.0, 0.95, 0.88])
    col = (core[..., None] * white + inner[..., None] * np.array([1.0, 0.80, 0.55])
           + (mid + wide)[..., None] * warm + rays[..., None] * np.array([1.0, 0.78, 0.50])
           + streak[..., None] * np.array([0.55, 0.70, 1.0]))
    # wide atmospheric halo around the planet (outside the disc only), brighter towards the sun
    pcx, pcy, pr = planet_geometry(w, h)
    pdx, pdy = (px + 0.5 - pcx) / pr, (pcy - (py + 0.5)) / pr
    pdist = np.sqrt(pdx * pdx + pdy * pdy)
    ux, uy = (sx - pcx) / pr, (pcy - sy) / pr
    un = math.hypot(ux, uy)
    k = np.clip((pdx * ux + pdy * uy) / (np.maximum(pdist, 1e-6) * un), 0, 1)
    dd = np.clip(pdist - 1.0, 0, None)
    halo = (np.exp(-dd / 0.05) * 0.035 + np.exp(-dd / 0.14) * 0.010) * (0.38 + 0.45 * k ** 2 + 1.5 * k ** 16)
    halo = np.where(pdist >= 1.0, halo, 0.0)
    warmth = (k ** 24)[..., None]
    halo_col = np.array([0.22, 0.48, 1.0]) * (1 - warmth) + np.array([1.0, 0.55, 0.25]) * warmth
    col = col + halo[..., None] * halo_col
    col = tonemap(col, 0.9)
    Image.fromarray(to_srgb8(col)).save(os.path.join(out, "glare.jpg"), quality=92, optimize=True)


def render_vignette(out, w=256, h=144):
    py, px = np.mgrid[0:h, 0:w].astype(np.float32)
    nx, ny = (px + 0.5) / w * 2 - 1, (py + 0.5) / h * 2 - 1
    e = np.sqrt(nx * nx * 0.85 + ny * ny * 1.1)
    a = smoothstep(0.55, 1.45, e) * 0.62
    Image.fromarray((a * 255 + 0.5).astype(np.uint8), "L").save(os.path.join(out, "vignette.png"), optimize=True)


# ---------------------------------------------------------------- clock glyphs
def render_clock(font_path, out, size=180):
    font = ImageFont.truetype(font_path, size)
    chars = "0123456789:"
    ascent, descent = font.getmetrics()
    cell_h = ascent + descent + 8
    pad = 6
    widths = [int(math.ceil(font.getlength(c))) + pad * 2 for c in chars]
    atlas = Image.new("L", (sum(widths), cell_h), 0)
    draw = ImageDraw.Draw(atlas)
    x = 0
    lines = ["# char x width advance (pixels); cell height, ascent, cap-top, cap-bottom", ]
    cap_box = font.getbbox("0")
    for c, cw in zip(chars, widths):
        draw.text((x + pad, 4), c, font=font, fill=255)
        lines.append("%s %d %d %.2f" % (c, x, cw, font.getlength(c)))
        x += cw
    lines.append("height %d" % cell_h)
    lines.append("ascent %d" % (ascent + 4))
    lines.append("captop %d" % (cap_box[1] + 4))
    lines.append("capbottom %d" % (cap_box[3] + 4))
    lines.append("pad %d" % pad)
    rgba = Image.merge("RGBA", [Image.new("L", atlas.size, 255)] * 3 + [atlas])
    rgba.save(os.path.join(out, "clock.png"), optimize=True)
    with open(os.path.join(out, "clock.txt"), "w") as f:
        f.write("\n".join(lines) + "\n")


# ---------------------------------------------------------------- icons
def icon_canvas(scale=4, size=96):
    s = size * scale
    return Image.new("L", (s, s), 0), s


def finish_icon(img, out, name, size=96):
    img = img.resize((size, size), Image.LANCZOS)
    rgba = Image.merge("RGBA", [Image.new("L", img.size, 255)] * 3 + [img])
    rgba.save(os.path.join(out, "icon_%s.png" % name), optimize=True)


def person(draw, cx, cy, s, fill=255):
    hr = s * 0.17
    draw.ellipse([cx - hr, cy - s * 0.36 - hr, cx + hr, cy - s * 0.36 + hr], fill=fill)
    bw, bh = s * 0.34, s * 0.30
    draw.rounded_rectangle([cx - bw, cy - s * 0.10, cx + bw, cy - s * 0.10 + bh * 1.35], radius=bw * 0.9, fill=fill)
    draw.rectangle([cx - bw * 1.2, cy + s * 0.215, cx + bw * 1.2, cy + s * 0.6], fill=0)


def render_icons(out):
    img, s = icon_canvas()
    person(ImageDraw.Draw(img), s / 2, s * 0.52, s * 0.95)
    finish_icon(img, out, "singleplayer")

    img, s = icon_canvas()
    d = ImageDraw.Draw(img)
    person(d, s * 0.64, s * 0.52, s * 0.72, fill=150)
    back = img.copy()
    img2, _ = icon_canvas()
    d2 = ImageDraw.Draw(img2)
    person(d2, s * 0.40, s * 0.55, s * 0.82)
    # cut a gap around the front person so both silhouettes read clearly
    gap = img2.filter(ImageFilter.MaxFilter(25))
    merged = np.maximum(np.asarray(back) * (1 - np.asarray(gap) / 255.0), np.asarray(img2)).astype(np.uint8)
    finish_icon(Image.fromarray(merged), out, "multiplayer")

    # alts: person + circular "switch" arrows
    img, s = icon_canvas()
    d = ImageDraw.Draw(img)
    person(d, s * 0.40, s * 0.50, s * 0.78)
    ring = Image.new("L", img.size, 0)
    rd = ImageDraw.Draw(ring)
    cx, cy, rr = s * 0.74, s * 0.70, s * 0.19
    t = s * 0.055
    rd.arc([cx - rr, cy - rr, cx + rr, cy + rr], 200, 340, fill=255, width=int(t))
    rd.arc([cx - rr, cy - rr, cx + rr, cy + rr], 20, 160, fill=255, width=int(t))
    for ang, direction in ((340, 1), (160, 1)):
        a = math.radians(ang)
        tipx, tipy = cx + math.cos(a) * rr, cy + math.sin(a) * rr
        tangent = a + math.pi / 2 * direction
        hs = s * 0.075
        p1 = (tipx + math.cos(tangent) * hs, tipy + math.sin(tangent) * hs)
        p2 = (tipx + math.cos(a) * hs * 0.9 - math.cos(tangent) * hs * 0.3, tipy + math.sin(a) * hs * 0.9 - math.sin(tangent) * hs * 0.3)
        p3 = (tipx - math.cos(a) * hs * 0.9 - math.cos(tangent) * hs * 0.3, tipy - math.sin(a) * hs * 0.9 - math.sin(tangent) * hs * 0.3)
        rd.polygon([p1, p2, p3], fill=255)
    clear = ring.filter(ImageFilter.MaxFilter(31))
    merged = np.maximum(np.asarray(img) * (1 - np.asarray(clear) / 255.0), np.asarray(ring)).astype(np.uint8)
    finish_icon(Image.fromarray(merged), out, "alts")

    # options: gear
    img, s = icon_canvas()
    d = ImageDraw.Draw(img)
    cx = cy = s / 2
    teeth = 8
    outer, inner = s * 0.40, s * 0.30
    pts = []
    for i in range(teeth * 4):
        a = i / (teeth * 4) * 2 * math.pi
        rad = outer if (i % 4) in (1, 2) else inner
        pts.append((cx + math.cos(a) * rad, cy + math.sin(a) * rad))
    d.polygon(pts, fill=255)
    d.ellipse([cx - s * 0.13, cy - s * 0.13, cx + s * 0.13, cy + s * 0.13], fill=0)
    finish_icon(img, out, "options")

    # quit: power symbol
    img, s = icon_canvas()
    d = ImageDraw.Draw(img)
    cx = cy = s / 2
    rr, t = s * 0.30, int(s * 0.075)
    d.arc([cx - rr, cy - rr + s * 0.04, cx + rr, cy + rr + s * 0.04], 300, 240, fill=255, width=t)
    d.rounded_rectangle([cx - t / 2, cy - s * 0.36, cx + t / 2, cy + s * 0.02], radius=t / 2, fill=255)
    finish_icon(img, out, "quit")


if __name__ == "__main__":
    nasa_dir, font_file, out_dir = sys.argv[1:4]
    os.makedirs(out_dir, exist_ok=True)
    which = set(sys.argv[4:]) or {"earth", "space", "glare", "vignette", "clock", "icons"}
    if "earth" in which: render_earth(nasa_dir, out_dir)
    if "space" in which: render_space(out_dir)
    if "glare" in which: render_glare(out_dir)
    if "vignette" in which: render_vignette(out_dir)
    if "clock" in which: render_clock(font_file, out_dir)
    if "icons" in which: render_icons(out_dir)
