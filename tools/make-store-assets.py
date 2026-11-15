"""Builds Play Store assets from the verification screenshots.

Outputs (all PNG):
  store/screenshots/01..08-*.png   1080x2160 (1:2)
  store/feature-graphic-1024x500.png
  store/icon-512.png

Requires Pillow and the Windows Malgun Gothic fonts.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SHOTS = ROOT / "docs" / "screenshots"
OUT = ROOT / "store"
OUT.mkdir(exist_ok=True)
(OUT / "screenshots").mkdir(exist_ok=True)

W, H = 1080, 2160
PAPER = (247, 245, 240)
INK = (27, 28, 25)
BLUE = (30, 53, 80)
BLUE_TOP = (37, 66, 98)
BLUE_BOTTOM = (22, 40, 62)
PAPER_TOP = (253, 251, 247)
PAPER_BOTTOM = (239, 235, 227)
ORANGE = (194, 112, 61)
LIGHT = (247, 245, 240)
MUTED_LIGHT = (196, 202, 208)
MUTED_DARK = (118, 114, 104)

BOLD = "C:/Windows/Fonts/malgunbd.ttf"
REGULAR = "C:/Windows/Fonts/malgun.ttf"


def font(size: int, bold: bool = True) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(BOLD if bold else REGULAR, size)


def gradient(size, top, bottom) -> Image.Image:
    width, height = size
    image = Image.new("RGB", (1, height))
    pixels = image.load()
    for y in range(height):
        t = y / max(1, height - 1)
        pixels[0, y] = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    return image.resize((width, height), Image.BILINEAR).convert("RGBA")


def add_glow(canvas: Image.Image, center, radius, color, alpha=34):
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    x, y = center
    draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=(*color, alpha))
    layer = layer.filter(ImageFilter.GaussianBlur(radius // 3))
    canvas.alpha_composite(layer)


def rounded(image: Image.Image, radius: int) -> Image.Image:
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, image.width - 1, image.height - 1), radius=radius, fill=255
    )
    result = Image.new("RGBA", image.size, (0, 0, 0, 0))
    result.paste(image, (0, 0), mask)
    return result


def drop_shadow(canvas: Image.Image, box, radius=30, offset=(0, 16), blur=28, opacity=70):
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    x0, y0, x1, y1 = box
    ImageDraw.Draw(layer).rounded_rectangle(
        (x0 + offset[0], y0 + offset[1], x1 + offset[0], y1 + offset[1]),
        radius=radius,
        fill=(0, 0, 0, opacity),
    )
    canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(blur)))


def screenshot_asset(filename, source, eyebrow, lines, dark=False):
    canvas = gradient(
        (W, H),
        BLUE_TOP if dark else PAPER_TOP,
        BLUE_BOTTOM if dark else PAPER_BOTTOM,
    )
    add_glow(canvas, (W - 40, 160), 330, (255, 255, 255) if dark else (194, 112, 61), 24 if dark else 16)
    add_glow(canvas, (40, H - 120), 300, (194, 112, 61), 18 if dark else 10)

    draw = ImageDraw.Draw(canvas)
    text = LIGHT if dark else INK
    muted = MUTED_LIGHT if dark else MUTED_DARK

    draw.text((72, 52), eyebrow, font=font(26), fill=ORANGE if not dark else (224, 175, 139))
    draw.text((W - 72, 52), "My PDF Writer", font=font(24), fill=muted, anchor="ra")

    y = 118
    for line in lines:
        draw.text((72, y), line, font=font(66), fill=text)
        y += 82
    draw.rounded_rectangle((72, y + 12, 152, y + 20), radius=4, fill=ORANGE)

    shot = Image.open(source).convert("RGB")
    target_w = 810
    target_h = round(shot.height * target_w / shot.width)
    shot = rounded(shot.resize((target_w, target_h), Image.LANCZOS), 30)
    x = (W - target_w) // 2
    top = H - target_h - 30
    drop_shadow(canvas, (x, top, x + target_w, top + target_h))
    canvas.alpha_composite(shot, (x, top))

    canvas.convert("RGB").save(OUT / "screenshots" / filename, optimize=True)


def feature_graphic():
    fw, fh = 1024, 500
    canvas = gradient((fw, fh), (32, 62, 96), (20, 37, 58))
    add_glow(canvas, (900, 80), 260, (194, 112, 61), 38)
    add_glow(canvas, (700, 470), 260, (31, 78, 121), 60)

    draw = ImageDraw.Draw(canvas)
    draw.text((64, 108), "My PDF Writer", font=font(78), fill=LIGHT)
    draw.text(
        (64, 208),
        "전공 공부를 위한 오프라인 PDF 작업대",
        font=font(34, False),
        fill=(214, 224, 234),
    )
    draw.rounded_rectangle((64, 270, 108, 274), radius=2, fill=ORANGE)
    draw.text(
        (64, 302),
        "광고 없음  ·  인터넷 권한 없음  ·  완전 오프라인",
        font=font(26, False),
        fill=(224, 175, 139),
    )

    # Stylised page with a highlight and a pen.
    page = Image.new("RGBA", (250, 340), (0, 0, 0, 0))
    pd = ImageDraw.Draw(page)
    pd.rounded_rectangle((8, 8, 242, 332), radius=14, fill=(247, 245, 240, 255))
    pd.polygon([(190, 8), (242, 60), (190, 60)], fill=(199, 212, 226, 255))
    pd.rounded_rectangle((36, 78, 214, 84), radius=3, fill=(31, 78, 121, 255))
    for i, width in enumerate([178, 168, 150, 176]):
        pd.rounded_rectangle((36, 112 + i * 30, 36 + width, 118 + i * 30), radius=3, fill=(60, 68, 78, 255))
    pd.rounded_rectangle((30, 238, 220, 264), radius=6, fill=(255, 211, 110, 255))
    pd.rounded_rectangle((36, 280, 160, 286), radius=3, fill=(60, 68, 78, 255))
    pd.rounded_rectangle((36, 300, 140, 306), radius=3, fill=(60, 68, 78, 255))
    pd.polygon([(170, 220), (198, 248), (118, 328), (90, 336), (98, 308)], fill=(194, 112, 61, 255))
    page = page.rotate(-8, expand=True, resample=Image.BICUBIC)
    drop_shadow(canvas, (690, 70, 690 + page.width, 70 + page.height), radius=18, blur=24, opacity=90)
    canvas.alpha_composite(page, (690, 70))

    canvas.convert("RGB").save(OUT / "feature-graphic-1024x500.png", optimize=True)


def app_icon():
    size = 512
    scale = 4.35
    ox = (size - 108 * scale) / 2
    oy = (size - 108 * scale) / 2

    def sx(x):
        return ox + x * scale

    def sy(y):
        return oy + y * scale

    image = gradient((size, size), (238, 68, 64), (196, 24, 24))
    draw = ImageDraw.Draw(image, "RGBA")
    draw.rounded_rectangle(
        (sx(33), sy(27), sx(81), sy(89)),
        radius=round(6 * scale),
        fill=(122, 14, 14, 102),
    )
    draw.rounded_rectangle(
        (sx(30), sy(23), sx(78), sy(85)),
        radius=round(5 * scale),
        fill=(255, 251, 245),
    )
    draw.polygon(
        [(sx(67), sy(23)), (sx(78), sy(34)), (sx(67), sy(34))],
        fill=(246, 213, 213),
    )

    wordmark = font(74)
    box = draw.textbbox((0, 0), "PDF", font=wordmark)
    word_w = box[2] - box[0]
    word_h = box[3] - box[1]
    draw.text(
        (sx(54) - word_w / 2 - box[0], sy(42.5) - word_h / 2 - box[1]),
        "PDF",
        font=wordmark,
        fill=(198, 40, 40),
    )

    for y, width in ((55, 28), (62, 23)):
        draw.rounded_rectangle(
            (sx(54 - width / 2), sy(y), sx(54 + width / 2), sy(y + 2.2)),
            radius=round(1 * scale),
            fill=(240, 205, 205),
        )

    def line(x1, y1, x2, y2, width, color):
        draw.line((sx(x1), sy(y1), sx(x2), sy(y2)), fill=color, width=round(width * scale))
        r = width * scale / 2
        for x, y in ((x1, y1), (x2, y2)):
            draw.ellipse((sx(x) - r, sy(y) - r, sx(x) + r, sy(y) + r), fill=color)

    line(57, 79, 75, 61, 9, (255, 255, 255, 64))
    line(58, 78, 76, 60, 5.5, (188, 30, 30))
    line(59.5, 76.5, 72, 64, 1.6, (232, 70, 65))
    draw.polygon(
        [(sx(76), sy(60)), (sx(72.5), sy(71)), (sx(65), sy(63.5))],
        fill=(92, 10, 10),
    )
    image.convert("RGB").save(OUT / "icon-512.png", optimize=True)

def main():
    shots = [
        (
            "01-home.png",
            "home.png",
            "STUDY WORKBENCH",
            ["광고 없이,", "완전 오프라인"],
            True,
        ),
        (
            "02-annotate.png",
            "annotation.png",
            "PEN · HIGHLIGHT · TEXT",
            ["강의자료 위에", "바로 필기"],
            False,
        ),
        (
            "03-pages.png",
            "page-sheet.png",
            "PAGE WORKBENCH",
            ["페이지를", "한 번에 정리"],
            False,
        ),
        (
            "04-rotate.png",
            "page-rotated.png",
            "ROTATION-AWARE",
            ["회전해도", "필기는 그대로"],
            True,
        ),
        (
            "05-search.png",
            "search.png",
            "FULL-TEXT SEARCH",
            ["찾고, 바로", "해당 페이지로"],
            False,
        ),
        (
            "06-notes.png",
            "notes.png",
            "PAGE NOTES",
            ["페이지 메모로", "복습까지"],
            True,
        ),
        (
            "07-export.png",
            "export-opened.png",
            "FLATTENED PDF",
            ["주석이 새겨진", "PDF로 내보내기"],
            False,
        ),
    ]
    for filename, source, eyebrow, lines, dark in shots:
        screenshot_asset(filename, SHOTS / source, eyebrow, lines, dark)
        print("wrote", OUT / "screenshots" / filename)

    feature_graphic()
    print("wrote", OUT / "feature-graphic-1024x500.png")
    app_icon()
    print("wrote", OUT / "icon-512.png")


if __name__ == "__main__":
    main()
