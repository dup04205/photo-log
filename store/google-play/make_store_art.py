"""Google Play store art for PHOTO-LOG: 512x512 icon and 1024x500 feature graphic."""
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

F = "/tmp/claude-0/-home-claude/9b13eec4-26bc-59e9-a46f-9df2d989da90/scratchpad/fonts"
OUT = sys.argv[1]
PLAYFAIR = f"{F}/out/playfair_display_bold_italic.ttf"
PLAYFAIR_SB = f"{F}/out/playfair_display_semibold_italic.ttf"
GOWUN = f"{F}/gowun_full_400.ttf"
DSEG = f"{F}/dseg/package/fonts/DSEG7-Classic/DSEG7Classic-Italic.ttf"
ORANGE = (255, 122, 26)
GLOW = (255, 90, 0)


def fit_font(path, text, width):
    size = 10
    while True:
        f = ImageFont.truetype(path, size + 2)
        if f.getbbox(text)[2] - f.getbbox(text)[0] > width:
            return ImageFont.truetype(path, size)
        size += 2


def centered(draw, text, font, cx, cy, fill):
    l, t, r, b = font.getbbox(text)
    draw.text((cx - (l + r) / 2, cy - (t + b) / 2), text, font=font, fill=fill)


def glow_text(img, xy, text, font, color, glow, radius):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.text(xy, text, font=font, fill=glow + (230,))
    blurred = layer.filter(ImageFilter.GaussianBlur(radius))
    img.alpha_composite(blurred)
    img.alpha_composite(blurred)
    d2 = ImageDraw.Draw(img)
    d2.text(xy, text, font=font, fill=color + (255,))


# ---------- 512 x 512 icon (light: white + black text, same as the launcher icon) ----------
icon = Image.new("RGBA", (512, 512), (255, 255, 255, 255))
d = ImageDraw.Draw(icon)
font = fit_font(PLAYFAIR, "PHOTO-LOG", 512 * 0.70)
centered(d, "PHOTO-LOG", font, 256, 256, (17, 17, 17))
icon.convert("RGB").save(f"{OUT}/play-icon-512.png")

# dark variant for reference (Play shows one icon; light is the submitted one)
icon_d = Image.new("RGBA", (512, 512), (0, 0, 0, 255))
d = ImageDraw.Draw(icon_d)
centered(d, "PHOTO-LOG", font, 256, 256, (245, 245, 245))
icon_d.convert("RGB").save(f"{OUT}/play-icon-512-dark.png")

# ---------- 1024 x 500 feature graphic ----------
W, H = 1024, 500
fg = Image.new("RGBA", (W, H), (0, 0, 0, 255))
d = ImageDraw.Draw(fg)

# left: title + tagline (kept inside the central safe area, away from edges)
title_font = ImageFont.truetype(PLAYFAIR, 76)
d.text((84, 168), "PHOTO-LOG", font=title_font, fill=(245, 245, 245))
tag_font = ImageFont.truetype(GOWUN, 30)
d.text((88, 272), "사진 한 장, 기록 한 줄", font=tag_font, fill=(190, 190, 190))
small = ImageFont.truetype(GOWUN, 20)
d.text((88, 322), "필름카메라처럼 날짜가 찍히는 사진 기록장", font=small, fill=(120, 120, 120))

# right: a print-style photo card with the film date stamp
card = Image.new("RGBA", (300, 360), (247, 247, 245, 255))
cd = ImageDraw.Draw(card)
# photo area: soft dusk gradient (abstract, not a real photo)
px0, py0, px1, py1 = 18, 18, 282, 282
top, mid, bot = (64, 92, 140), (196, 120, 110), (246, 180, 120)
for y in range(py0, py1):
    t = (y - py0) / (py1 - py0)
    if t < 0.6:
        k = t / 0.6
        c = tuple(int(top[i] + (mid[i] - top[i]) * k) for i in range(3))
    else:
        k = (t - 0.6) / 0.4
        c = tuple(int(mid[i] + (bot[i] - mid[i]) * k) for i in range(3))
    cd.line([(px0, y), (px1 - 1, y)], fill=c)
# low horizon silhouette
cd.polygon([(px0, 240), (90, 222), (150, 232), (220, 214), (px1, 228), (px1, py1), (px0, py1)], fill=(38, 34, 44))
# film date stamp in the photo's bottom-right
stamp_font = ImageFont.truetype(DSEG, 20)
tick_font = ImageFont.truetype(PLAYFAIR_SB, 22)   # DSEG에는 ' 글자가 없어 따로 그린다
parts = [("'", tick_font), ("26", stamp_font), ("11", stamp_font), ("05", stamp_font)]
gap = 8
widths = [f.getbbox(t)[2] - f.getbbox(t)[0] for t, f in parts]
total = sum(widths) + gap * (len(parts) - 1) - 4
x = px1 - 14 - total
digit_h = stamp_font.getbbox("26")[3]
y = py1 - 14 - digit_h
for (txt, f), w in zip(parts, widths):
    yy = y - 2 if txt == "'" else y
    glow_text(card, (x - f.getbbox(txt)[0], yy), txt, f, ORANGE, GLOW, 4)
    x += w + (gap if txt != "'" else gap - 4)
# note lines under the photo, like the card share format
nd = ImageDraw.Draw(card)
note_font = ImageFont.truetype(GOWUN, 17)
nd.text((22, 296), "노을이 길었던 퇴근길", font=note_font, fill=(40, 40, 40))
foot = ImageFont.truetype(PLAYFAIR_SB, 13)
nd.text((282 - foot.getbbox("PHOTO-LOG")[2], 334), "PHOTO-LOG", font=foot, fill=(60, 60, 60))

card = card.rotate(-6, resample=Image.BICUBIC, expand=True)
shadow = Image.new("RGBA", card.size, (0, 0, 0, 0))
shadow.paste((0, 0, 0, 150), mask=card.split()[3])
shadow = shadow.filter(ImageFilter.GaussianBlur(14))
cx, cy = 600, 78
fg.alpha_composite(shadow, (cx + 8, cy + 12))
fg.alpha_composite(card, (cx, cy))
fg.convert("RGB").save(f"{OUT}/play-feature-1024x500.png")
print("ok")
