# 인쇄 PDF 기하 QA: 여백 침범 / 잘림 / 거터 위반 / 한글 음절 절단 / 문항 누락 점검
# 사용법: python qa_pdf.py out/qa-paper.pdf out/qa-answers.pdf
import pymupdf, re, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
PT = 72 / 25.4
LC0, LC1 = 11 * PT, 101.5 * PT
RC0, RC1 = 108.5 * PT, 199 * PT
G1 = (LC1 + RC0) / 2
CUT = (297 - 10) * PT          # .page 하단 padding 경계
COL_END = (297 - 14) * PT      # .cols padding-bottom(4mm)을 뺀 컬럼 하한
CIRC = "①②③④⑤"
FOOT = re.compile(r"^\d{1,2} / \d{1,2}$")
# 개행으로 갈라지면 안 되는 단어·구(한글 음절 절단 회귀 감지용)
INTACT = ["선행사", "생략 가능", "제시된 형태", "뒤에 오는 형태", "오답 포인트", "핵심 근거", "지문 근거"]
seen = set()

def lines(p):
    out = []
    for b in p.get_text("dict")["blocks"]:
        if b["type"] != 0: continue
        for l in b["lines"]:
            t = "".join(s["text"] for s in l["spans"]).strip()
            if t: out.append((l["bbox"], t, l["spans"][0]["size"]))
    return out

def inside(bb):
    x0, x1 = bb[0], bb[2]
    return (x0 >= LC0 - 1 and x1 <= LC1 + 1) or (x0 >= RC0 - 1 and x1 <= RC1 + 1) or (x0 >= LC0 - 1 and x1 <= RC1 + 1)

fail = 0
for f in sys.argv[1:]:
    d = pymupdf.open(f)
    print("=" * 16, f, "pages", d.page_count)
    nums = []
    for i, p in enumerate(d):
        ls = [(bb, t, sz) for bb, t, sz in lines(p) if not FOOT.match(t)]
        gut = [t for bb, t, _ in ls if not inside(bb)]
        over = [t for bb, t, _ in ls if bb[3] > CUT + 0.8 or bb[1] < 11.5 * PT]
        col_over = [t for bb, t, _ in ls if bb[3] > COL_END + 1.5]
        lb = [bb[3] for bb, t, _ in ls if bb[2] <= G1] or [0]
        rb = [bb[3] for bb, t, _ in ls if bb[0] >= G1] or [0]
        # 해설 1페이지의 정답표 셀은 한 글자씩만 담도록 설계되어 있다 → 마커 검사에서 제외
        mk = [t for bb, t, _ in ls if t in CIRC and not ("answers" in f and i == 0 and bb[1] < 140 * PT)]
        txt = p.get_text()
        broken = [w for w in INTACT if w in txt.replace("\n", "") and w not in txt]
        seen.update(w for w in INTACT if w in txt.replace("\n", ""))
        nums += [int(m.group(1)) for bb, t, _ in ls if (m := re.match(r"^(\d{1,2})\.\s", t))]
        bad = bool(gut or over or col_over or mk or broken)
        fail += bad
        print(f"{'!!' if bad else '  '}p{i+1}: Lbot={max(lb)/PT:.1f} Rbot={max(rb)/PT:.1f} "
              f"gut={len(gut)} over={len(over)} colOver={len(col_over)} markerFrag={len(mk)} brokenWord={len(broken)}")
        for tag, arr in (("GUT", gut), ("OVER", over), ("COLOVER", col_over), ("MK", mk), ("BROKEN", broken)):
            for x in arr[:4]: print(f"     {tag}: {x[:44]!r}")
    ok_seq = nums == sorted(nums) and len(set(nums)) == len(nums) and not (set(range(1, 71)) ^ set(nums))
    print("   문항번호 1~70 연속/무결:", "PASS" if ok_seq else f"FAIL({len(nums)})")
    fail += 0 if ok_seq else 1
    d.close()
print("   절단 검사 단어를 찾지 못한 경우:", [w for w in INTACT if w not in seen] or "없음(전수 확인)")
print("\nRESULT:", "FAIL" if fail else "PASS", f"({fail}개 문제 페이지)")
