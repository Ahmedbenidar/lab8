import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
IMG_DIR = ROOT / "docs" / "images"
IMG_DIR.mkdir(parents=True, exist_ok=True)

FONT = ImageFont.truetype(r"C:\Windows\Fonts\consola.ttf", 15)
TITLE_FONT = ImageFont.truetype(r"C:\Windows\Fonts\consola.ttf", 13)

BG = (30, 30, 32)
TITLE_BG = (45, 45, 48)
FG = (204, 204, 204)
DIM = (128, 128, 132)
GREEN = (106, 190, 96)
RED = (232, 96, 96)
YELLOW = (222, 183, 92)
CYAN = (110, 190, 214)

PAD = 26
LH = 23
TOP = 46
TITLE_BAR = 34


def render(lines, title, out_path):
    width = max(FONT.getlength(t) for t, _ in lines) + PAD * 2
    height = TOP + LH * len(lines) + PAD - 8
    img = Image.new("RGB", (int(width), int(height)), BG)
    d = ImageDraw.Draw(img)

    d.rectangle([0, 0, img.width, TITLE_BAR], fill=TITLE_BG)
    for i, c in enumerate([(255, 95, 86), (255, 189, 46), (39, 201, 63)]):
        cx, cy = 20 + i * 20, 17
        d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], fill=c)
    tw = TITLE_FONT.getlength(title)
    d.text(((img.width - tw) / 2, 10), title, font=TITLE_FONT, fill=(160, 160, 165))

    y = TOP
    for text, color in lines:
        d.text((PAD, y), text, font=FONT, fill=color)
        y += LH

    img.save(out_path)
    print(f"{out_path} {img.size}")


APP_PREFIXES = (
    "Réservation initiale",
    "Version chargée",
    "Conflit détecté",
    "Modification enregistrée",
    "Transaction ",
    "État final",
)


def app_lines(path):
    """Ne garde que les lignes produites par l'application (pas les logs Hibernate/Maven)."""
    out = []
    for raw in Path(path).read_text(encoding="utf-8-sig").splitlines():
        line = raw.rstrip()
        if line.startswith(APP_PREFIXES):
            out.append(line)
    return out


def sql_update(path):
    lines = Path(path).read_text(encoding="utf-8-sig").splitlines()
    blocks = []
    for i, line in enumerate(lines):
        if line.strip() == "update":
            block, j = [], i
            while j < len(lines) and lines[j].strip() and lines[j].startswith(" "):
                block.append(lines[j].rstrip())
                j += 1
            blocks.append(block)
    return blocks[-1] if blocks else []


def colorize(line):
    low = line.lower()
    if "conflit détecté" in low and "tentative de retry" not in low:
        return line, RED
    if "tentative de retry" in low:
        return line, YELLOW
    if line.startswith("Transaction 1") or line.startswith("Transaction 2 : modification réussie"):
        return line, GREEN
    if line.startswith("Modification enregistrée"):
        return line, GREEN
    if line.startswith("État final"):
        return line, CYAN
    return line, FG


sim = app_lines(ROOT / "tools" / "outputs" / "sim.txt")
retry = app_lines(ROOT / "tools" / "outputs" / "retry.txt")
sql = sql_update(ROOT / "tools" / "outputs" / "sim.txt")

sim_lines = []
for line in sim:
    sim_lines.append(colorize(line))
    if line.startswith("Version chargée") and sql:
        sim_lines.append(("", FG))
        sim_lines.extend((s, DIM) for s in sql)
        sim_lines.append(("", FG))

render(sim_lines,
       "TP 6 — ConcurrentReservationSimulator : conflit non géré",
       IMG_DIR / "01-conflit-detecte.png")

render([colorize(l) for l in retry],
       "TP 6 — ConcurrentReservationWithRetry : conflit résolu",
       IMG_DIR / "02-retry-resolu.png")
