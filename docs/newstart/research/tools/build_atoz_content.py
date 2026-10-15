#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PHASE1 STEP5 — A_TO_Z_CONTENT_READY synthesizer.
Reads qa_units.json (+ cafe_qa_db_ready.json) and maps the 부실우려 (board6) materials into
the EXISTING A to Z STEP 7 structure (부실우려 → 부실차주). Does NOT modify a-to-z.html
(other agent is editing it). Output is a READY artifact + a paste-ready HTML fragment that
a human can merge when the UI file is free.

Human reading order (rule #11): ①실제로 많이 묻는 질문 ②실제 사례/경험 ③실제 대응·주의 ④현재 공식기준
No raw enums are shown to users (rule #12); cafe answers are NOT promoted to official (rule #3/#7):
unconfirmed guidance is presented as "전해지는 내용 / 현재 재확인 필요", never "공식·현재" unless OFFICIAL_CONFIRMED
(which none are here).
"""
import json, os, re, collections

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.normpath(os.path.join(HERE, "..", "cafe-qa-phase1-b6"))
Q = json.load(open(os.path.join(DATA, "qa_units.json"), encoding="utf-8"))
U = Q["units"]

# topic buckets -> display order (human Korean labels)
BUCKETS = [
    ("실효", "미납·실효", re.compile(r"실효|미납|5 ?회|3 ?개월|연체")),
    ("전환", "부실우려→부실차주 전환", re.compile(r"전환|부실차주|차주 ?(넘|전|갈)|새출발")),
    ("재산", "재산·차량 반영", re.compile(r"재산|차량|자동차|중고차|명의|홈텍스")),
    ("압류", "통장·계좌·압류", re.compile(r"압류|통장|계좌|사고통장|상계|자동이체")),
    ("수정조정", "수정조정·채권 추가", re.compile(r"수정조정|채권(을)? ?추가|대위변제|매입|환매|포함")),
    ("서류", "신청 서류·거주 확인", re.compile(r"서류|임차|무상 ?거주|주민등록|따로서류|어카운트")),
    ("신용", "신용점수·장기연체·원장", re.compile(r"신용점수|장기연체|원장 ?초기화|연체 ?등록|신용정보")),
    ("심사", "심사·동의/부동의", re.compile(r"심사|동의|부동의|기각|인용|합의|배정")),
]

def bucket_of(text):
    hits = [name for _k, name, rx in BUCKETS if rx.search(text or "")]
    return hits or ["기타 문의"]

# ---- ① 실제로 많이 묻는 질문 (frequency over QUESTION units, by bucket) ----
q_units = [u for u in U if u["type"] == "QUESTION"]
bucket_q = collections.Counter()
q_examples = collections.defaultdict(list)
for u in q_units:
    for b in bucket_of(u["evidenceText"]):
        bucket_q[b] += 1
        if len(q_examples[b]) < 3 and len(u["evidenceText"]) >= 15:
            q_examples[b].append(u)

# ---- ② 사례/경험: ACTUAL_CASE with date-timeline signals + MEMBER_EXPERIENCE ----
cases = [u for u in U if u["type"] == "ACTUAL_CASE"]
cases_sorted = sorted(cases, key=lambda u: -len(u["evidenceText"]))
date_rx = re.compile(r"(\d{1,2})\s*월\s*(\d{1,2})\s*일|(\d{1,2})/(\d{1,2})")
timeline_cases = [u for u in cases if date_rx.search(u["evidenceText"])]
exps = [u for u in U if u["type"] == "MEMBER_EXPERIENCE"]

# ---- ③ 대응·주의: STAFF_ANSWER + OPERATOR_GUIDE (operator guidance) ----
staff = [u for u in U if u["type"] in ("STAFF_ANSWER", "OPERATOR_GUIDE")]
claims = [u for u in U if u["type"] == "COUNSELOR_CLAIM"]
caution = [u for u in staff if re.search(r"규칙|반드시|모든 사건|아닙니다|주의|~라고 단정| 케이스", u["evidenceText"])]

def clean(s):
    return re.sub(r"\s+", " ", (s or "")).strip()

# operator-answered article count (P1) for provenance note
p1_articles = sorted({u["articleId"] for u in U if u.get("tier") == 1})

lines = []
A = lines.append
A("# A_TO_Z_CONTENT_READY — 부실우려(menuId=6) 실전 콘텐츠")
A("")
A("- 상태: **READY (미적용)**. `templates/newstart/a-to-z.html`은 다른 Agent 편집 중 → 본 파일만 수정 가능.",
)
A("- 대상 단계: **STEP 7. 부실우려 → 부실차주** (`#step-7`), 기존 소스 클래스(`az-source-official`/`az-source-member`/`az-source-recheck`) 재사용.")
A("- 원천: 부실차주 카페 '부실우려' 게시판 RAW 248건 → qa_units 1,000개 (P1 운영자 답변 포함 글 %d건)." % len(p1_articles))
A("- 원칙: 카페 답변·주장은 **공식 규정으로 승격하지 않음**. 미확인 안내는 `현재 재확인 필요`로 표시. 내부 enum 미노출.")
A("")
A("## 매핑 요약 (사람이 읽는 순서)")
A("| 순서 | A to Z 섹션 제안 | 소스 unit | 근거 article 수 |")
A("|---|---|---|---|")
A("| ① | 7-A 실제로 많이 묻는 질문 | QUESTION %d | %d |" % (len(q_units), len({u['articleId'] for u in q_units})))
A("| ② | 7-B 실제 사례·경험 | ACTUAL_CASE %d / MEMBER_EXPERIENCE %d | %d |" % (len(cases), len(exps), len({u['articleId'] for u in cases+exps})))
A("| ③ | 7-C 실제 대응·주의할 점 | STAFF/OPERATOR %d | %d |" % (len(staff), len({u['articleId'] for u in staff})))
A("| ④ | 7-D 현재 공식기준(재확인) | 미확인 COUNSELOR_CLAIM %d | %d |" % (len(claims), len({u['articleId'] for u in claims})))
A("")
A("## ① 실제로 많이 묻는 질문 (주제 빈도순)")
for b, cnt in bucket_q.most_common():
    A("### %s — 질문 %d건" % (b, cnt))
    for u in q_examples.get(b, [])[:2]:
        A("- “%s” (출처 article %s)" % (clean(u["evidenceText"])[:120], u["articleId"]))
    A("")

A("## ② 실제 사례·경험")
A("### 대표 타임라인 사례 (본문 내 날짜 서술)")
for u in timeline_cases[:4]:
    A("- article %s: %s" % (u["articleId"], clean(u["evidenceText"])[:200]))
A("")
A("### 회원 경험(운영자 답변 포함 thread)")
for u in exps[:6]:
    A("- article %s(%s): %s" % (u["articleId"], u["role"], clean(u["evidenceText"])[:120]))
A("")

A("## ③ 실제 대응·주의할 점 (운영자 안내)")
for u in staff[:8]:
    A("- [%s·article %s] %s" % (u["role"], u["articleId"], clean(u["evidenceText"])[:150]))
A("")
if caution:
    A("### 주의 (단정 금지)")
    for u in caution[:5]:
        A("- article %s: %s" % (u["articleId"], clean(u["evidenceText"])[:140]))
    A("")

A("## ④ 현재 공식기준 — 미확인 안내(재확인 필요)")
A("아래는 카페에서 오간 안내로, 공식자료에서 개별 확인 전에는 `현재 재확인 필요`로만 노출한다.")
for u in claims[:10]:
    A("- [%s·article %s] %s" % (u["role"], u["articleId"], clean(u["evidenceText"])[:140]))
A("")

A("---")
A("## paste-ready HTML fragment (STEP 7 하단 삽입용 — 적용은 UI 파일 해제 후)")
A("```html")
A('<!-- 7-A. 실제로 많이 묻는 질문 -->')
A('<h3 class="az-h3">7-A. 실제로 많이 묻는 질문</h3>')
A('<div class="az-source az-source-member">')
top3 = [u for u in q_units]
A('  <p><span class="az-tag az-tag-member">회원 질문</span> 부실우려 게시판에서 자주 올라오는 질문:</p>')
A('  <ul>')
for b, cnt in bucket_q.most_common(6):
    ex = clean(q_examples[b][0]["evidenceText"])[:60] if q_examples[b] else b
    A('    <li>%s <span class="az-muted">(%d건)</span></li>' % (b, cnt))
A('  </ul>')
A('</div>')
A('<!-- 7-B. 실제 사례·경험 -->')
A('<h3 class="az-h3">7-B. 실제 사례·경험</h3>')
A('<div class="az-source az-source-member">')
A('  <p><span class="az-tag az-tag-member">회원 사례</span> 부실우려 약정 실효 후 부실차주 전환 실사례(본문 날짜 기반):</p>')
A('  <ul>')
for u in timeline_cases[:3]:
    A('    <li>article %s — %s</li>' % (u["articleId"], clean(u["evidenceText"])[:90]))
A('  </ul>')
A('</div>')
A('<!-- 7-C. 실제 대응·주의할 점 -->')
A('<h3 class="az-h3">7-C. 실제 대응·주의할 점</h3>')
A('<div class="az-block az-block-caution">')
A('  <p class="az-block-title">[주의할 것]</p>')
A('  <ul>')
for u in staff[:4]:
    A('    <li><span class="az-tag az-tag-member">운영자 안내</span> %s</li>' % clean(u["evidenceText"])[:90])
A('    <li>“~회 미납하면 반드시 실효” 등은 모든 사건에 적용되는 규칙이 아닙니다.</li>')
A('  </ul>')
A('</div>')
A('<!-- 7-D. 현재 공식기준(재확인) -->')
A('<h3 class="az-h3">7-D. 현재 공식기준</h3>')
A('<div class="az-source az-source-recheck">')
A('  <p><span class="az-tag az-tag-recheck">현재 재확인 필요</span> 카페에서 안내·전해지는 내용은 신복위 1600-7000/새출발기금 1660-1378 및 공식 FAQ에서 개별 확인이 필요합니다.</p>')
A('</div>')
A("```")
A("")
A("NOTE: 7-A~7-D는 제안 heading 앵커/번호입니다. 실제 병합 시 기존 '7-1 차이/7-2 실효 후 부실차주 신청 사례'의")
A("번호 체계·class를 해치지 않도록 adjacent placement 하십시오. PHASE 2(담보·menuId=7)는 PHASE 1 PASS 전 매핑하지 않음.")

OUTP = os.path.join(DATA, "A_TO_Z_CONTENT_READY.md")
open(OUTP, "w", encoding="utf-8").write("\n".join(lines))
print("WROTE", OUTP)
print("buckets:", json.dumps(dict(bucket_q.most_common()), ensure_ascii=False))
print("timeline_cases", len(timeline_cases), "cases", len(cases), "exps", len(exps),
      "staff", len(staff), "claims", len(claims), "caution", len(caution))
print("p1_operator_answered_articles", len(p1_articles))
