"""시뮬레이터 스크린샷(1320x2868)에 위 문구를 얹은 App Store용 이미지를 만든다.
같은 크기(1320x2868)로 만들어 그대로 올릴 수 있다. 사진 후보 미리보기(contact sheet)도 만든다.

사용: python3 frame.py <raw 폴더> <framed 폴더> <글꼴 폴더> [후보 사진 폴더]
글꼴 폴더에는 고운바탕 전체판 GowunBatang-Bold.ttf, GowunBatang-Regular.ttf 가 있어야 한다
(앱에 넣은 것은 필요한 글자만 담은 축소판이라 문구용으로 쓸 수 없다).
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

raw, out, fonts = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
out.mkdir(parents=True, exist_ok=True)
W, H = 1320, 2868

# 파일 이름(번호) → (위 문구, 아래 작은 문구, 다크 여부)
CAPTIONS = {
    "1-grid": ("날짜별로 모아 보는\n사진 기록", "연 · 월 · 일로 펼쳐 보는 나의 하루들", False),
    "2-detail": ("사진 한 장,\n기록 한 줄", "필름카메라 날짜와 촬영 장소가 함께 남습니다", False),
    "3-card": ("그대로 보내기 좋은\n사진 + 기록 카드", "비율을 고르고, 보낼 부분을 맞추세요", False),
    "4-story": ("인스타그램 스토리에\n바로", "9:16 비율, 사진 위에 기록을 얹어서", False),
    "5-dark": ("흑과 백,\n그리고 날짜의 주황", "모든 사진과 기록은 내 폰 안에만", True),
}

title = ImageFont.truetype(str(fonts / "GowunBatang-Bold.ttf"), 92)
sub = ImageFont.truetype(str(fonts / "GowunBatang-Regular.ttf"), 46)
ORANGE = (232, 117, 42)


def rounded(img, r):
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, img.size[0] - 1, img.size[1] - 1), r, fill=255)
    return m


for p in sorted(raw.glob("*.png")):
    key = p.stem
    if key not in CAPTIONS:
        continue
    head, small, dark = CAPTIONS[key]
    bg = (0, 0, 0) if dark else (247, 247, 245)
    fg = (245, 245, 245) if dark else (17, 17, 17)
    subc = (154, 154, 154) if dark else (110, 110, 110)

    canvas = Image.new("RGB", (W, H), bg)
    d = ImageDraw.Draw(canvas)
    y = 190
    for line in head.split("\n"):
        w = d.textlength(line, font=title)
        d.text(((W - w) / 2, y), line, font=title, fill=fg)
        y += 128
    # 날짜 주황 짧은 선
    d.rounded_rectangle((W / 2 - 36, y + 18, W / 2 + 36, y + 26), 4, fill=ORANGE)
    y += 64
    w = d.textlength(small, font=sub)
    d.text(((W - w) / 2, y), small, font=sub, fill=subc)
    y += 120

    shot = Image.open(p).convert("RGB")
    scale = (H - y + 140) / shot.height  # 아래로 조금 넘치게 해서 폰 화면이 화면 밖으로 이어지는 느낌
    sw, sh = int(shot.width * scale), int(shot.height * scale)
    shot = shot.resize((sw, sh), Image.LANCZOS)
    x = (W - sw) // 2

    # 그림자 + 테두리
    shadow = Image.new("L", (sw + 120, sh + 120), 0)
    ImageDraw.Draw(shadow).rounded_rectangle((60, 60, sw + 60, sh + 60), 70, fill=90 if not dark else 0)
    shadow = shadow.filter(ImageFilter.GaussianBlur(28))
    canvas.paste((0, 0, 0), (x - 60, y - 50), shadow)
    border = Image.new("RGB", (sw + 16, sh + 16), (40, 40, 40) if dark else (17, 17, 17))
    canvas.paste(border, (x - 8, y - 8), rounded(border, 78))
    canvas.paste(shot, (x, y), rounded(shot, 70))
    canvas.save(out / f"{key}.png")
    print("만듦:", out / f"{key}.png")

# 예시 사진 후보 미리보기
if len(sys.argv) > 4:
    cand = sorted(Path(sys.argv[4]).glob("*.jpg"))
    if cand:
        cols, cell = 6, 300
        rows = (len(cand) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * cell, rows * (cell + 40)), (255, 255, 255))
        d = ImageDraw.Draw(sheet)
        for i, f in enumerate(cand):
            im = Image.open(f).convert("RGB")
            im.thumbnail((cell - 10, cell - 10))
            cx, cy = (i % cols) * cell, (i // cols) * (cell + 40)
            sheet.paste(im, (cx + 5, cy + 5))
            d.text((cx + 8, cy + cell + 4), f.stem, font=ImageFont.truetype(str(fonts / "GowunBatang-Regular.ttf"), 26), fill=(0, 0, 0))
        sheet.save(out.parent / "photos-contact-sheet.jpg", quality=85)
