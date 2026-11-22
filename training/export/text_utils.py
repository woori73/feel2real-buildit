"""OCR 글자 정리와 문항 그림 OCR 결과를 발문·지문·선택지로 나누는 도구."""
import hashlib
import re
import unicodedata

CIRCLED = "①②③④⑤"
ORDER_CHOICES = ("(A) - (C) - (B)", "(B) - (A) - (C)", "(B) - (C) - (A)", "(C) - (A) - (B)", "(C) - (B) - (A)")
# Tesseract가 원문자 ①~⑤를 잘못 읽은 모양: @) (2) 66) © ® 0) 등
_MARK = r"(?:[①②③④⑤]|\(?0[1-5]\)|[@©®][D)]|\(\s*[S9]\s*\)|Q\)|\(\s*[1-5]\s*\)|\(\s*\(\s*[1-5]\s*\)\s*\)|[1-5]\)|@\)?|©|®|\(\s*[@©®]\s*\)|6{1,2}\)|0{1,2}\)|O\))"
_LINE_MARK = re.compile(r"^\s*" + _MARK + r"\s*")
_INLINE_MARK = re.compile(r"(?:(?<=\s)|^)" + _MARK + r"\s+")
_INSERT_POS = re.compile(r"\(\s*(?:" + _MARK + r"|[@©®]|\(\s*[1-5]\s*\))\s*\)")
_LOOSE = re.compile(r"^\s*(?:\(?[0-9@©®OoDSsQq]{1,2}\)?|[①②③④⑤@©®]|\(\s*\S\s*\)?)\s+")
_SCORE = re.compile(r"[\(\[]\s*(\d)\s*[점섬젬]\s*[\)\]]|\(\s*[2-5][0-9]{1,2}\s*\)\s*$")


def nfkc(text):
    return unicodedata.normalize("NFKC", text or "")


def norm_space(text):
    return " ".join((text or "").split())


def digest(text):
    return hashlib.sha256(norm_space(text).lower().encode("utf-8")).hexdigest()[:16]


def strip_score(text):
    return _SCORE.sub("", text).strip()


def fix_insertion_marks(text):
    """지문 안의 ( ① ) 자리 표시를 순서대로 ①~⑤로 다시 쓴다. 정확히 5개일 때만 바꾼다."""
    found = list(_INSERT_POS.finditer(text))
    if len(found) != 5:
        return text, False
    out, last = [], 0
    for n, m in enumerate(found):
        out.append(text[last:m.start()])
        out.append(f"( {CIRCLED[n]} )")
        last = m.end()
    out.append(text[last:])
    return "".join(out), True


def split_question(ocr_text):
    """문항 그림 OCR → (번호, 발문, 본문, 선택지 5개 또는 None).

    발문은 첫 줄부터 '?'·'.'·'[N점]'으로 끝나는 줄까지. 선택지는 뒤에서부터 원문자 모양 5개를 찾는다.
    """
    lines = [l.rstrip() for l in (ocr_text or "").splitlines() if l.strip()]
    if not lines:
        return None, "", "", None
    number = None
    m = re.match(r"^\s*(\d{1,2})\s*[\.\,]\s*", lines[0])
    if m:
        number = int(m.group(1))
        lines[0] = lines[0][m.end():]
    stem_end = 0
    for i, line in enumerate(lines[:4]):
        if re.search(r"(\?|\.|[\)\]])\s*$", line) and (re.search(r"[가-힣]", line) or "?" in line):
            stem_end = i
            if "?" in line or _SCORE.search(line) or re.search(r"(시오|은|는|것)[\.\?]?\s*$", line):
                break
    stem = " ".join(lines[:stem_end + 1])
    if stem_end + 1 < len(lines) and _SCORE.fullmatch(lines[stem_end + 1].strip()):
        stem_end += 1
    rest = lines[stem_end + 1:]
    # 선택지: 줄 머리 원문자, 한 줄에 여러 개 있으면 줄 안에서 나눈다.
    starts = []
    for i, line in enumerate(rest):
        for mm in _INLINE_MARK.finditer(line):
            if mm.start() == 0 or line[:mm.start()].endswith("  ") or re.search(r"\s{2,}$", line[:mm.start()]):
                starts.append((i, mm.start(), mm.end()))
    options = None
    body_lines = rest
    if len(starts) >= 5:
        sel = starts[-5:]
        first_line = sel[0][0]
        # 선택지 구간은 첫 선택지 줄부터 끝까지여야 한다.
        if all(sel[k][0] >= first_line for k in range(5)):
            tail = rest[first_line:]
            flat, offsets, pos = "", {}, 0
            for j, line in enumerate(tail):
                offsets[first_line + j] = len(flat)
                flat += line + "\n"
            cuts = [(offsets[i] + s, offsets[i] + e) for i, s, e in sel]
            options = []
            for k, (s, e) in enumerate(cuts):
                end = cuts[k + 1][0] if k + 1 < 5 else len(flat)
                options.append(norm_space(flat[e:end]))
            body_lines = rest[:first_line] + ([rest[first_line][:sel[0][1]]] if sel[0][1] else [])
            if any(not o for o in options):
                options = None
                body_lines = rest
    if options is None:
        # 느슨한 규칙: 마지막 5줄이 모두 짧은 기호(0, D, 8, (9), 01), (S) 등)로 시작하면 선택지로 본다.
        tail = rest[-5:]
        if len(rest) >= 5 and all(_LOOSE.match(l) for l in tail):
            options = [norm_space(_LOOSE.sub("", l, count=1)) for l in tail]
            body_lines = rest[:-5]
            if any(not o for o in options):
                options, body_lines = None, rest
    body = "\n".join(l for l in body_lines if l.strip())
    if options is None and fix_insertion_marks(body)[1]:
        options = list(CIRCLED)  # 지문 속 ( ① )~( ⑤ ) 자리를 고르는 문항(문장 삽입 등)
    stem = re.sub(r"(?<=[가-힣]) (?=[오은는을를이가요][\.\?\)]?(\s|$))", "", norm_space(strip_score(stem)))
    if options and all(re.fullmatch(r"\([A-C0-9]\)\s*-\s*\([A-C0-9]\)\s*-\s*\([A-C0-9]\)", o) for o in options):
        options = list(ORDER_CHOICES)  # 글의 순서 선택지는 수능 고정 5개
    return number, stem, body.strip(), options


def ngrams(text, n=8):
    toks = re.findall(r"[A-Za-z가-힣0-9']+", (text or "").lower())
    return {tuple(toks[i:i + n]) for i in range(len(toks) - n + 1)}


def overlap(candidate, reference, n=8):
    """candidate의 n-gram 중 reference에 있는 비율(0~1). 짧아 n-gram이 없으면 None."""
    c = ngrams(candidate, n)
    if not c:
        return None
    return len(c & ngrams(reference, n)) / len(c)


_NOISE = re.compile(r"(?<![A-Za-z])[A-Z0-9@©®\]\[|]{2,}(?![a-z])")


def ocr_noise(*texts):
    """한글이 있는 줄에 섞인 OCR 잡음 토큰 수(예: 'SA]', 'AAS', '37H'). 영어만 있는 줄은 세지 않는다."""
    n = 0
    for text in texts:
        for line in (text or "").splitlines() or [text or ""]:
            if re.search(r"[가-힣]", line):
                n += len(_NOISE.findall(line))
    return n
