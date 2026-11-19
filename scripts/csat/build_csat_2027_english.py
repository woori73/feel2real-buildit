#!/usr/bin/env python3
"""2027학년도 수능 영어(홀수형) 필기본 PDF → 모션 해설 페이지 자료(webp 이미지 + data.json).

사용: python3 -I scripts/csat/build_csat_2027_english.py <필기본.pdf> [출력 폴더]
기본 출력: src/main/resources/static/img/csat/2027-en/

- 왼쪽(문제) 칸은 평가원 문제지에서 잘라 붙인 그림이다. 그림 속 노란 형광(정답 근거)은 지우고(종이색으로 되돌림)
  형광 위치만 비율 좌표로 따로 저장한다 → 페이지에서 문제를 먼저 깨끗하게 보여 주고, 근거 단계에서 형광을 다시 그린다.
- 오른쪽(해설) 칸은 글자다. 굵은 소제목(주제·해설·선택지별 정답·오답 이유·풀이 전략·핵심 단어)으로 나눈다.
- 「문장별 해석과 어휘」 쪽은 문장마다 영어·해석·어휘로 나눈다.
- 1쪽 빠른 정답과 문항 쪽 「가답안 N번」이 하나라도 다르면 멈춘다(잘못된 정답 공개 방지).
확정 정답이 나오면 새 PDF로 다시 돌리면 된다.
"""
import io
import json
import re
import sys
from pathlib import Path

import pymupdf
from PIL import Image

CIRC = "①②③④⑤"
HEAD_COLOR = 0x22615A
BLUE = 0x064CA2
RED = 0xA31F2C
COL_SPLIT = 380  # pt: 왼쪽 문제 칸 / 오른쪽 해설 칸 경계


def is_yellow(r, g, b):
    return r > 200 and g > 170 and b < 190 and (r - b) > 45


def clean_image(img):
    """노란 형광을 지우고(회색조 R값으로) 형광 영역 사각형(비율)을 돌려준다."""
    img = img.convert("RGB")
    w, h = img.size
    px = img.load()
    rows = {}
    for y in range(h):
        xs = []
        for x in range(w):
            r, g, b = px[x, y]
            if (r - b) > 40 and r > 120:
                if is_yellow(r, g, b):
                    xs.append(x)
                px[x, y] = (r, r, r)
        if len(xs) > 8:
            rows[y] = (min(xs), max(xs))
    # 연속된 행을 한 줄 형광으로 묶는다
    boxes = []
    cur = None
    for y in sorted(rows):
        x0, x1 = rows[y]
        if cur and y - cur["y1"] <= 2 and not (x1 < cur["x0"] - 40 or x0 > cur["x1"] + 40):
            cur["y1"] = y
            cur["x0"] = min(cur["x0"], x0)
            cur["x1"] = max(cur["x1"], x1)
        else:
            if cur:
                boxes.append(cur)
            cur = {"x0": x0, "x1": x1, "y0": y, "y1": y}
    if cur:
        boxes.append(cur)
    out = []
    for bx in boxes:
        if bx["y1"] - bx["y0"] < 6:
            continue
        out.append({
            "x": round(bx["x0"] / w * 100, 2), "y": round(bx["y0"] / h * 100, 2),
            "w": round((bx["x1"] - bx["x0"] + 1) / w * 100, 2), "h": round((bx["y1"] - bx["y0"] + 1) / h * 100, 2),
        })
    return img, out


def lines_of(page):
    res = []
    for b in page.get_text("dict")["blocks"]:
        for l in b.get("lines", []):
            spans = [s for s in l["spans"] if s["text"].strip()]
            if not spans:
                continue
            text = "".join(s["text"] for s in l["spans"]).strip()
            res.append({"x": l["bbox"][0], "y": l["bbox"][1], "text": text, "size": spans[0]["size"],
                        "color": spans[0]["color"], "bold": "Bold" in spans[0]["font"], "font": spans[0]["font"]})
    res.sort(key=lambda r: (round(r["y"]), r["x"]))
    return res


def is_footer(l):
    return l["y"] > 800 or l["text"].startswith("원문:") or l["text"].startswith("출처:")


def parse_right(lines):
    """오른쪽 칸: 소제목 단위 sections. 선택지 이유는 choices로."""
    sections = []
    cur = None
    choice = None
    for l in lines:
        t = l["text"]
        if t.startswith("노란색") or t.startswith("파란색") or "빨간색" in t:
            continue
        if l["bold"] and l["color"] == HEAD_COLOR and l["size"] >= 9.9:
            if t == "주제 · 해설 · 풀이 전략":
                continue
            cur = {"title": t, "lines": [], "choices": []}
            sections.append(cur)
            choice = None
            continue
        if cur is None:
            continue
        m = re.match(r"^([①②③④⑤])\s*(정답|오답)\s*(.*)$", t)
        if m:
            choice = {"no": CIRC.index(m.group(1)) + 1, "correct": m.group(2) == "정답", "text": m.group(3).strip()}
            cur["choices"].append(choice)
            continue
        if choice is not None and l["x"] > 420:
            choice["text"] = join_ko(choice["text"], t)
            continue
        choice = None
        cur["lines"].append(t)
    for s in sections:
        s["body"] = merge_lines(s.pop("lines"), keep_breaks=s["title"] == "핵심 단어")
    return sections


def dedupe(sections):
    seen, out = set(), []
    for s in sections:
        k = (s["title"], tuple(s["body"]), len(s["choices"]))
        if k in seen:
            continue
        seen.add(k)
        out.append(s)
    return out


# 한 글자로 쓰이는 낱말(줄 끝에 홀로 있어도 띄어 쓰는 경우)
WORDS1 = set("수것때중후전및더잘못또곧각두세네그저새왜즉총약첫좀곳점줄뿐데바")
# 줄 첫머리가 동사 어미 덩어리면 낱말 중간에서 끊긴 것(예: 점|검했다, 분리|한다)
SUFFIX = re.compile(r"^(다|한다|하는|하다|했다|해도|해서|하고|하며|하면|되므로|되어|된다|진다|하여|시킨다|하지|하게|이다|된|되는|한)(?=[\s,.)’”]|$)")
# 줄 첫머리가 조사·어미 덩어리면 앞 낱말에 붙인다(예: 과거|의 대비)
PARTICLE = re.compile(r"^(의|을|를|은|는|이|가|에|와|과|로|으로|도|만|에서|에게|까지|부터|처럼|보다|이라는|이라고|라는|이다|이며|이고|인|이나|과의|와의|에는|에도|으로는|로는|이므로|이지만)(?=[\s,.)’”]|$)")
# 낱말 끝으로 거의 오지 않는 글자(줄이 낱말 중간에서 끊긴 표시)
STEM_END = set("하되시으어아여해")


# 문서 전체에서 줄 「가운데」에 있는 낱말(앞뒤가 공백이라 확실한 낱말) 모음. 줄 끝·첫 조각은 넣지 않는다.
TOKENS = set()
STRIP = "‘’“”'\"()[]{}.,;:!?·…「」『』"


def learn_tokens(doc):
    for page in doc:
        for l in lines_of(page):
            parts = l["text"].split()
            for w in parts[1:-1]:
                w = w.strip(STRIP)
                if re.fullmatch(r"[가-힣]+", w):
                    TOKENS.add(w)


def known(w):
    w = w.strip(STRIP)
    return w in TOKENS


def join_ko(a, b):
    """PDF 줄바꿈을 잇는다. 원문 공백 정보가 없어서(띄어쓰기 자리·낱말 중간 모두에서 끊김)
    ① 문서에서 배운 낱말로 판단하고 ② 모르면 우리말 규칙으로 판단한다."""
    if not a:
        return b
    if not b:
        return a
    if a[-1] in "‘“(「『":
        return a + b
    mx = re.search(r"[가-힣]+$", a)
    my = re.match(r"^[가-힣]+", b)
    if mx and my:
        x, y = mx.group(0), my.group(0)
        if SUFFIX.match(b):
            return a + b
        if known(x + y) or (len(x) <= 2 and any(w.startswith(x + y[:2]) for w in TOKENS)):
            return a + b
        if known(x) and known(y):
            return a + " " + b
    if re.match(r"^[.,;:!?)\]」』’”%·]", b):
        return a + b
    last = a.rsplit(" ", 1)[-1]
    if re.search(r"[A-Za-z0-9,.;:)]$", a) or re.match(r"^[A-Za-z0-9(]", b):
        return a + " " + b
    if re.search(r"[가-힣]$", a) and SUFFIX.match(b):
        return a + b
    if re.search(r"[가-힣’”)]$", a) and PARTICLE.match(b):
        return a + b
    if re.search(r"[가-힣]$", a) and re.match(r"^[가-힣]", b):
        tail = a[-1]
        last = re.search(r"[가-힣]+$", a).group(0)
        if len(last) == 1 and last not in WORDS1:
            return a + b
        if tail in STEM_END and len(last) >= 2:
            return a + b
        return a + " " + b
    return a + " " + b


def merge_lines(lines, keep_breaks=False):
    if keep_breaks:
        return [l for l in lines if l]
    out = ""
    for l in lines:
        out = join_ko(out, l)
    return [out] if out else []


def parse_sentences(lines):
    """문장별 해석과 어휘 쪽 → [{en, ko, vocab[]}]. 번호(①…) 줄의 y를 기준으로 그 아래 줄을 묶는다."""
    marks = [l for l in lines if l["x"] < 60 and re.fullmatch(r"[①-⑳]", l["text"]) and 110 < l["y"] < 800]
    marks.sort(key=lambda l: l["y"])
    items = [{"en": "", "ko": "", "vocab": [], "_mode": "en"} for _ in marks]
    for l in lines:
        t = l["text"]
        if is_footer(l) or l["y"] < 110 or l in marks or t.startswith("영어 문장"):
            continue
        idx = max((i for i, m in enumerate(marks) if m["y"] <= l["y"] + 6), default=None)
        if idx is None:
            continue
        cur = items[idx]
        if t.startswith("해석"):
            cur["ko"] = t[2:].strip()
            cur["_mode"] = "ko"
        elif t.startswith("어휘"):
            cur["vocab"].append(t[2:].strip())
            cur["_mode"] = "vocab"
        elif cur["_mode"] == "en":
            cur["en"] = (cur["en"] + " " + t).strip()
        elif cur["_mode"] == "ko":
            cur["ko"] = join_ko(cur["ko"], t.strip())
        else:
            cur["vocab"].append(t)
    for it in items:
        it.pop("_mode")
    return [it for it in items if it["en"]]


def save_webp(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "WEBP", quality=82, method=6)


def main():
    pdf = Path(sys.argv[1])
    out_dir = Path(sys.argv[2] if len(sys.argv) > 2 else "src/main/resources/static/img/csat/2027-en")
    doc = pymupdf.open(pdf)
    learn_tokens(doc)

    # 1쪽: 빠른 정답 + 주요 어휘
    p1 = doc[0].get_text("text")
    quick = {int(n): CIRC.index(c) + 1 for n, c in re.findall(r"(\d{2})\s+([①②③④⑤])", p1)}
    rows = {}
    for l in lines_of(doc[0]):
        if 360 < l["y"] < 680:
            rows.setdefault((l["x"] > 300, round(l["y"] / 8)), []).append(l)
    vocab = []
    for key in sorted(rows, key=lambda k: (k[0], k[1])):
        cells = sorted(rows[key], key=lambda l: l["x"])
        no = next((c["text"] for c in cells if re.fullmatch(r"\d{2}", c["text"].replace("□", "").strip())), None)
        word = next((c["text"] for c in cells if re.match(r"^[A-Za-z]", c["text"])), None)
        meaning = next((c["text"] for c in cells if re.match(r"^[가-힣]", c["text"])), None)
        if no and word and meaning:
            vocab.append({"no": int(no.replace("□", "").strip()), "word": word, "meaning": meaning})
    if len(quick) != 45:
        sys.exit(f"빠른 정답이 45개가 아닙니다: {len(quick)}")

    questions = {}
    order = []
    img_count = 0
    for pi in range(1, len(doc)):
        page = doc[pi]
        lines = lines_of(page)
        title_line = next((l for l in lines if l["size"] >= 19), None)
        if not title_line:
            continue
        title = title_line["text"]
        m = re.match(r"^(\d{2})(?:~(\d{2}))?번\s+(.+)$", title)
        if not m:
            continue
        q1 = int(m.group(1))
        q2 = int(m.group(2)) if m.group(2) else q1
        kind = m.group(3).strip()
        key = f"{q1:02d}" if q1 == q2 else f"{q1:02d}-{q2:02d}"
        is_sentences = kind.startswith("문장별 해석")
        if is_sentences and key not in questions and f"{q1:02d}" in questions:
            key = f"{q1:02d}"  # 41~42번 문장별 해석은 41번(공통 지문) 묶음에 붙인다
        if key not in questions:
            questions[key] = {"key": key, "nos": list(range(q1, q2 + 1)), "type": "", "section": "",
                              "answers": {}, "left": [], "right": [], "sentences": []}
            order.append(key)
        q = questions[key]
        header = next((l["text"] for l in lines if "가답안" in l["text"] and l["y"] < 90), "")
        if not is_sentences and not kind.endswith("(계속)"):
            q["type"] = kind
        if header:
            q["section"] = "듣기" if header.startswith("듣기") else "독해"
            for n, a in re.findall(r"(\d{2})번\s*([①②③④⑤])", header):
                q["answers"][int(n)] = CIRC.index(a) + 1
            mm = re.search(r"가답안\s*(\d)번", header)
            if mm and q1 == q2:
                q["answers"][q1] = int(mm.group(1))
        if is_sentences:
            q["sentences"].extend(parse_sentences(lines))
            continue
        # 왼쪽 칸: 글자 + 그림을 위에서 아래 순서로
        items = []
        for l in lines:
            if l["x"] < COL_SPLIT and 95 < l["y"] < 800 and l is not title_line:
                t = l["text"]
                if t in ("대본 · 선택지", "발문 · 지문 · 선택지", "선택지"):
                    continue
                items.append({"y": l["y"], "kind": "label" if l["color"] == HEAD_COLOR else "text", "text": t})
        for info in page.get_image_info(xrefs=True):
            x0, y0, x1, y1 = info["bbox"]
            if x0 >= COL_SPLIT:
                continue
            pix = pymupdf.Pixmap(doc, info["xref"])
            if pix.alpha or pix.n > 3:
                pix = pymupdf.Pixmap(pymupdf.csRGB, pix)
            img = Image.open(io.BytesIO(pix.tobytes("png")))
            clean, hl = clean_image(img)
            img_count += 1
            name = f"q{key}_{img_count:03d}.webp"
            save_webp(clean, out_dir / name)
            items.append({"y": y0, "kind": "img", "src": name, "w": clean.size[0], "h": clean.size[1],
                          "widthPt": round(x1 - x0, 1), "highlights": hl})
        items.sort(key=lambda it: it["y"])
        for it in items:
            it.pop("y")
        q["left"].extend(items)
        right = [l for l in lines if l["x"] >= COL_SPLIT and 95 < l["y"] < 800 and l is not title_line]
        q["right"].extend(parse_right(right))

    # 공통 지문(43~45번) 표시
    for key, q in questions.items():
        txt = " ".join(it.get("text", "") for it in q["left"])
        m = re.search(r"(\d{2})-(\d{2})번 공통 지문은 (\d{2})번", txt)
        if m:
            q["sharedFrom"] = f"{int(m.group(3)):02d}"

    # 정답 대조(빠른 정답 ↔ 문항 쪽)
    errors = []
    for q in questions.values():
        for n in q["nos"]:
            if q["answers"].get(n) != quick.get(n):
                errors.append(f"{n}번 빠른정답 {quick.get(n)} ≠ 문항쪽 {q['answers'].get(n)}")
    covered = sorted(n for q in questions.values() for n in q["nos"])
    if covered != list(range(1, 46)):
        errors.append(f"문항 번호 누락: {sorted(set(range(1, 46)) - set(covered))}")
    if errors:
        sys.exit("정답 대조 실패:\n" + "\n".join(errors))

    data = {
        "title": "2027학년도 대학수학능력시험 영어 영역 (홀수형)",
        "answerBasis": "2026년 11월 19일 한국교육과정평가원 공개 가답안 기준",
        "source": "문제 원문: 한국교육과정평가원 · 해설: Feel2Real(F2R)",
        "quick": [{"no": n, "answer": quick[n]} for n in range(1, 46)],
        "vocab": vocab,
        "questions": [questions[k] for k in order],
    }
    fixes_path = Path(__file__).with_name("spacing_fixes_2027_english.json")
    fixes = json.loads(fixes_path.read_text(encoding="utf-8")) if fixes_path.exists() else {}

    def fix(s):
        """사람이 검토한 띄어쓰기 고침(공백만 다른 경우에만 적용)."""
        v = fixes.get(s)
        return v if v and v.replace(" ", "") == s.replace(" ", "") else s

    for q in data["questions"]:
        q["right"] = dedupe(q["right"])
        for sec in q["right"]:
            sec["body"] = [fix(b) for b in sec["body"]]
            for c in sec["choices"]:
                c["text"] = fix(c["text"])
        for se in q["sentences"]:
            se["ko"] = fix(se["ko"])
        q["answers"] = {str(k): v for k, v in sorted(q["answers"].items())}
    (out_dir / "data.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"문항 묶음 {len(order)}개, 그림 {img_count}개, 어휘 {len(vocab)}개 → {out_dir}")


if __name__ == "__main__":
    main()
