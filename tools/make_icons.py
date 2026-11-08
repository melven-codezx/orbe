#!/usr/bin/env python3
"""Gera todos os ícones do Orbe (adaptive + legado + arte de divulgação)."""
import math, os
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app/src/main/res")
S = 1024  # resolução master

# ---------- utilidades ----------
def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def diagonal_gradient(size, c0, c1, c2):
    """Gradiente diagonal suave passando por 3 cores."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x / size * 0.62 + (1 - y / size) * 0.38)
            if t < 0.55:
                px[x, y] = lerp(c0, c1, t / 0.55)
            else:
                px[x, y] = lerp(c1, c2, (t - 0.55) / 0.45)
    return img

def radial_glow(size, cx, cy, radius, color, alpha=180):
    """Mancha radial com alfa, usada como camada de brilho."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    steps = 60
    for i in range(steps, 0, -1):
        r = radius * i / steps
        a = int(alpha * (1 - i / steps) ** 1.8)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color + (a,))
    return img.filter(ImageFilter.GaussianBlur(size * 0.02))

def glyph(size, scale=1.0, glow=True):
    """Símbolo do Orbe: anel orbital + núcleo + satélite."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = cy = size / 2
    R = size * 0.205 * scale          # raio do anel
    stroke = size * 0.062 * scale     # espessura do anel
    core = size * 0.052 * scale       # núcleo
    sat = size * 0.072 * scale        # satélite

    # halo interno
    if glow:
        img.alpha_composite(radial_glow(size, cx, cy, R * 2.1, (150, 130, 255), 120))

    # anel
    d.ellipse([cx - R, cy - R, cx + R, cy + R], outline=(255, 255, 255, 255), width=int(stroke))
    # núcleo
    d.ellipse([cx - core, cy - core, cx + core, cy + core], fill=(255, 255, 255, 255))
    # satélite (ponto ciano) a 45°
    ang = math.radians(-48)
    sx, sy = cx + R * math.cos(ang), cy + R * math.sin(ang)
    d.ellipse([sx - sat, sy - sat, sx + sat, sy + sat], fill=(86, 244, 255, 255))
    return img

def rounded_mask(size, radius_ratio=0.225):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size - 1, size - 1],
                                        radius=int(size * radius_ratio), fill=255)
    return m

def circle_mask(size):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size - 1, size - 1], fill=255)
    return m

# ---------- camadas ----------
bg = diagonal_gradient(S, (18, 12, 44), (76, 43, 224), (124, 77, 255)).convert("RGBA")
bg.alpha_composite(radial_glow(S, S * 0.18, S * 0.84, S * 0.55, (34, 211, 238), 150))
bg.alpha_composite(radial_glow(S, S * 0.86, S * 0.16, S * 0.42, (168, 85, 247), 150))

full = bg.copy()
full.alpha_composite(glyph(S, 1.0))

fg = glyph(S, 0.86, glow=False)   # foreground adaptativo (com zona segura)

def save(img, mask, path, size):
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    im.paste(img, (0, 0), mask)
    im = im.resize((size, size), Image.LANCZOS)
    im.save(path)

DENS = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FG_DENS = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

for d, sz in DENS.items():
    p = os.path.join(OUT, "mipmap-" + d)
    os.makedirs(p, exist_ok=True)
    save(full, rounded_mask(S), os.path.join(p, "ic_launcher.png"), sz)
    save(full, circle_mask(S), os.path.join(p, "ic_launcher_round.png"), sz)

for d, sz in FG_DENS.items():
    p = os.path.join(OUT, "drawable-" + d)
    os.makedirs(p, exist_ok=True)
    # fundo adaptativo (gradiente sangrando) + foreground (símbolo)
    bg_img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bg_img.paste(bg, (0, 0))
    bg_img.resize((sz, sz), Image.LANCZOS).save(os.path.join(p, "ic_launcher_background.png"))
    fg.resize((sz, sz), Image.LANCZOS).save(os.path.join(p, "ic_launcher_foreground.png"))

# arte de divulgação
os.makedirs("/home/user/orbe/art", exist_ok=True)
full.resize((512, 512), Image.LANCZOS).save("/home/user/orbe/art/icon_512.png")
save(full, rounded_mask(S), "/home/user/orbe/art/icon_rounded_512.png", 512)
print("ícones gerados")
