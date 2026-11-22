"""문항 그림(webp)을 Tesseract(kor+eng)로 글자화해 캐시에 둔다. 캐시는 training/data/ocr/ (커밋하지 않음).

python3 -m training.export.ocr_images --root src/main/resources/static/naesin --root src/main/resources/prehigh-part2
"""
import argparse
import subprocess
import tempfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image

CACHE = Path(__file__).resolve().parents[1] / "data" / "ocr"


def cache_path(image, repo):
    return CACHE / (str(Path(image).resolve().relative_to(repo)) + ".txt")


def ocr(image, repo):
    out = cache_path(image, repo)
    if out.exists():
        return out
    im = Image.open(image).convert("L")
    im = im.resize((im.width * 3, im.height * 3), Image.LANCZOS)
    im = im.point(lambda v: 255 if v > 170 else 0)  # 흑백 처리: 한글 인식이 나아짐
    with tempfile.NamedTemporaryFile(suffix=".png") as tmp:
        im.save(tmp.name)
        text = subprocess.run(["tesseract", tmp.name, "-", "-l", "kor+eng", "--psm", "6"],
                              capture_output=True, text=True, check=True).stdout
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text, encoding="utf-8")
    return out


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--root", action="append", required=True)
    p.add_argument("--workers", type=int, default=2)
    a = p.parse_args()
    repo = Path(__file__).resolve().parents[2]
    images = sorted(i for r in a.root for i in Path(r).resolve().rglob("*.webp"))
    with ThreadPoolExecutor(a.workers) as pool:
        for n, _ in enumerate(pool.map(lambda i: ocr(i, repo), images), 1):
            if n % 200 == 0:
                print(n, "/", len(images), flush=True)
    print("done", len(images))


if __name__ == "__main__":
    main()
