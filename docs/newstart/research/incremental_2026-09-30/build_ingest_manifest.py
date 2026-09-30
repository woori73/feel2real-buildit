#!/usr/bin/env python3
"""A/B INGEST READY manifest 생성기 (READ ONLY — 운영 쓰기/수집 없음).

입력: raw/article_*_raw.txt (기존 확보 원문), enrich_798001_usercapture.sql (K1~K7)
출력: INGEST_READY_MANIFEST_AB_2026-09-30.json
- 요약(question/answer/case)은 원문 근거 사실만 옮긴 사람 검수용 요약이며 원문 밖 내용을 만들지 않는다.
- evidence 라벨: CONFIRMED / CLAIM / EXPERIENCE / INFERENCE (카페 발언은 어떤 경우에도 OFFICIAL 승격 없음)
- dedup_key / fingerprint 는 manifest 전용 (POST /api/admin/newstart/wiki DTO 에는 dedup_key 필드가 없다)
"""
import hashlib, json, re, os

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")

def raw(aid):
    return open(os.path.join(RAW, f"article_{aid}_raw.txt"), encoding="utf-8").read()

def norm(t):
    t = re.sub(r"COLLECTED_AT:.*|EXTRACTION_METHOD:.*", "", t)
    return re.sub(r"\s+", " ", t).strip()

def fp(t):
    return hashlib.sha256(norm(t).encode("utf-8")).hexdigest()[:32]

def redact_4451(t):
    """4451: SAFETY_FLAG(재산심사 회피성 권고) Q&A 블록 제거본. 원문은 raw/ 에 그대로 보존."""
    head, _, body = t.partition("--- BEGIN BODY ---")
    parts = re.split(r"(?m)^(?=Q\d+\.)", body)
    out, removed = [], []
    for p in parts:
        m = re.match(r"Q(\d+)\.", p)
        if m and ("[SAFETY_FLAG]" in p or int(m.group(1)) in (8, 21, 22, 27, 28, 35)):
            removed.append(int(m.group(1)))
            out.append(f"Q{m.group(1)}. [삭제 — 재산심사 회피성 권고(SAFETY_FLAG). 원문은 raw 보존, 공식 안내로 일반화 금지]\n")
        else:
            out.append(p)
    return head + "--- BEGIN BODY ---" + "".join(out), sorted(removed)

CAFE = {
    "31312978": ("NEWSTART_BORROWER_CAFE", "newhopero"),
    "27453843": ("CCRS_CAFE", "ccrs5500"),
}

def src(aid, club, board, written, _unused=None):
    comm, slug = CAFE[club]
    return {"community": comm, "clubId": club, "board": board, "articleId": str(aid),
            "url": f"https://cafe.naver.com/{slug}/{aid}", "writtenAt": written}

def item(aid, bucket, club, board, written, title, category, evidence, seg, relations,
         ready=True, flags=None, dup=None, reco=None, body=None, content_override=None):
    t = raw(aid) if aid != "798001-K" else ""
    return {
        "manifestId": f"AB-{aid}",
        "bucket": bucket,
        "source": src(aid, club, board, written, None),
        "title": title,
        "category": category,
        "sourceTypeProposed": "CAFE_ARTICLE",
        "evidenceType": evidence,
        "segments": seg,
        "dedup_key": f"cafe:{club}:{aid}",
        "fingerprint": fp(body if body is not None else t),
        "relations": relations,
        "relatedCaseIds": [r["ref"] for r in relations if r["kind"] == "TEXT_REF_CASE"],
        "targetStatus": "REVIEW",
        "payloadReady": ready,
        "flags": flags or [],
        "duplicateCheck": dup or {},
        "approvalRecommendation": reco or "REVIEW_THEN_ADMIN_DECIDE",
        "contentSource": "raw/article_%s_raw.txt%s" % (aid, " (SAFETY redacted)" if content_override else ""),
    }

DUP_CASE_NEW = {"empirical_cases": "NEW (운영 cases 107건=article_id 5855~6010 범위, 미포함)",
                "knowledge": "PROD_TITLE_CHECK_REQUIRED (ADMIN GET /source-type/CAFE_ARTICLE, dedup_key/제목 대조)"}

items = []

# ── A: newhopero 09/25~09/30 ─────────────────────────────────────────────
A = "A"
items += [
 item("6037", A, "31312978", "자유게시판", "2026-09-28 12:50", "사업자 후순위 담보대출 문의드립니다.", "담보대출·기한이익상실",
  "OPERATOR_CLAIM + COMMUNITY_OPINION",
  [{"type": "QUESTION", "speaker": "감사멤버(작성자)", "text": "부실차주 접수·임시안 대기 중. 저축은행 후순위 담보대출에 기한이익상실 예정 통지(9/30 상실예정), 이번 달 이자를 내지 않고 있음. 금감원 민원은 제출. 따로 변제해야 하는지."},
   {"type": "COUNSELOR_CLAIM", "speaker": "지피지기자산(운영자)", "label": "CLAIM", "text": "'이번 달 이자를 안 내도 된다'고 단정 불가. 담보대출은 일반 신용채무와 처리방식이 다르고(부실차주 담보채무는 중개형 취급이라고 운영자가 공식 자료를 인용), 개인 사례(5862·5903)가 있으나 일반화 불가. 9/30 전에 새출발기금/신복위에 해당 채권이 조정대상에 포함됐는지, 이번 회차 이자 납부 필요 여부를 확인할 것. 금감원 민원 접수와 납부의무 정지는 별개."},
   {"type": "COMMUNITY_OPINION", "speaker": "회원", "label": "CLAIM", "text": "저축은행에 접수증·확인서를 제시하면 상실 처리를 유예해주는 경우가 있다(근거 미확인)."}],
  [{"kind": "TEXT_REF_CASE", "ref": "5862", "note": "운영자 인용 사례(운영 cases에 존재)"}, {"kind": "TEXT_REF_CASE", "ref": "5903", "note": "운영자 인용 사례(운영 cases에 존재)"}],
  flags=["운영자가 '공식 자료'를 인용하나 원 링크 미제시 → CONFIRMED 아님(CLAIM)", "9/30 기한 경과 — 시의성 자료, 결과 미추적(UNRESOLVED)"], dup=DUP_CASE_NEW),
 item("6038", A, "31312978", "질문게시판", "2026-09-28 15:20", "은행에서 계속 전화오면", "추심·연락 대응",
  "OPERATOR_CLAIM",
  [{"type": "QUESTION", "speaker": "감사멤버(작성자)", "text": "부실우려차주 접수 후 심사 대기. 우리은행 마이너스통장 2,400만원을 포함했는데 전화·문자가 계속 옴. 받아야 하는지."},
   {"type": "COUNSELOR_CLAIM", "speaker": "지피지기자산(운영자)", "label": "CLAIM", "text": "한 번 받아서 '새출발기금 부실우려차주로 신청해 신복위 심사 중'이라고 안내. 신청채권에 포함됐는지 확인. 계속되면 기록을 남기고 신복위 1600-5500으로 진행상태·추심중단 여부 확인. 전화가 온다고 심사에 문제가 있다는 뜻은 아님."},
   {"type": "MEMBER_EXPERIENCE", "speaker": "작성자", "text": "감사 표시(결과 후속 없음)."}],
  [], flags=["결과 후속 없음"], dup=DUP_CASE_NEW),
 item("6040", A, "31312978", "자유게시판", "2026-09-28 20:45", "드디어 부실차주, 새출발 기금 신청 완료!", "신청 후기(정보 낮음)",
  "MEMBER_EXPERIENCE",
  [{"type": "MEMBER_EXPERIENCE", "speaker": "감사멤버(작성자)", "label": "EXPERIENCE", "text": "전자책 보고 온라인으로 신청 완료. 세부 사실 없음."}],
  [], ready=False, flags=["LOW_INFO — 구조화할 사실 없음, REVIEW payload 생략 권고(원문은 raw 보존)"], dup=DUP_CASE_NEW, reco="SKIP_LOW_INFO"),
 item("6043", A, "31312978", "질문게시판", "2026-09-29 11:20", "전자책구매", "신청 절차(운영자 안내)",
  "OPERATOR_GUIDE(비공식)",
  [{"type": "QUESTION", "speaker": "감사멤버(작성자)", "text": "전자책 구매 인사(질문 없음)."},
   {"type": "OPERATOR_GUIDE", "speaker": "지피지기자산(운영자)", "label": "CLAIM", "text": "운영 경험 기반 절차 안내(공식 아님): ①전자책의 원장초기화 안내대로 새출발기금에 원장초기화 메일 → 보통 3일 내 초기화 ②3~7일 사이 신용정보원에서 장기연체등록 여부 확인 ③등록 확인 후 새출발기금 사이트 또는 캠코 방문으로 부실차주 신청."}],
  [{"kind": "SIMILAR_CANDIDATE", "ref": "5568-Q5", "note": "부실우려→부실차주 전환 절차와 부분 겹침(운영 knowledge 존재 여부 확인 필요)"}],
  flags=["전자책 판매 맥락 — 홍보성 문구 제외하고 절차만 구조화", "운영자 절차 안내는 공식 규정 아님"], dup=DUP_CASE_NEW),
 item("6044", A, "31312978", "자유게시판", "2026-09-29 12:31", "안녕하세요 부실차주 준비중입니다", "준비 상태(정보 낮음)",
  "MEMBER_EXPERIENCE",
  [{"type": "MEMBER_EXPERIENCE", "speaker": "일반멤버(작성자)", "label": "EXPERIENCE", "text": "연체 60일차, 부실차주 신청 준비 중. 세부 사실 없음."}],
  [], ready=False, flags=["LOW_INFO — REVIEW payload 생략 권고"], dup=DUP_CASE_NEW, reco="SKIP_LOW_INFO"),
 item("6046", A, "31312978", "질문게시판", "2026-09-29 15:36", "부실우려 신청 후", "부실우려→부실차주 전환(미납)",
  "OPERATOR_CLAIM + REPEATED_OBSERVATION",
  [{"type": "QUESTION", "speaker": "일반멤버(작성자)", "text": "부실우려 신청 후 10/10부터 납입하라는 메시지를 받았는데 미납하면 되는지."},
   {"type": "COUNSELOR_CLAIM", "speaker": "지피지기자산(운영자)", "label": "CLAIM", "text": "부실우려→부실차주로 넘어간 사례에서는 납입 시작일이 와도 미납을 이어갔고, 순서는 신청→합의서 체결→미납→최종 실효→장기연체 등록 확인→부실차주 신청. 5회차 미납 때 실효 연락이 옴. 부실우려를 유지하려는 경우가 아니라 실효 후 부실차주로 가는 경우에 한한 안내."},
   {"type": "MEMBER_EXPERIENCE", "speaker": "작성자", "text": "감사, 불안하지만 진행하겠다(후속 결과 없음)."}],
  [{"kind": "SIMILAR_CANDIDATE", "ref": "5568-Q5", "note": "동일 전환 경로(5회 미납 실효)"}, {"kind": "SIMILAR_CANDIDATE", "ref": "4451-Q10", "note": "동일 전환 경로"}],
  flags=["'미납' 권고는 실효 후 부실차주 전환 경로에 한정 — 일반 권고로 승격 금지"], dup=DUP_CASE_NEW),
 item("6047", A, "31312978", "질문게시판", "2026-09-29 16:58", "안녕하세요 보증재단 가압류 질문좀 드립니다..", "가압류·보증재단",
  "OPERATOR_CLAIM (운영 관찰) + 일반 법률 설명(미검증)",
  [{"type": "QUESTION", "speaker": "감사멤버(작성자)", "text": "연체 70일차, 부실차주 신청 예정. 보증재단이 10/5까지 변제 못하면 아파트 가압류 예고, 10/19에 90일 도래. 가압류 후 지급명령 가능성, 3년 후 가압류 해제 가능 여부."},
   {"type": "COUNSELOR_CLAIM", "speaker": "지피지기자산(운영자)", "label": "CLAIM", "text": "최근 사례에서 보증재단이 신청 전 가압류하면 새출발기금 이관 후에도 가압류가 유지되는 경우가 있음. 회피책으로 보증재단 채무를 신청 직전에 연체시키는 대응도 있으나 본 질문자는 신청 전 가압류가 먼저 들어올 수 있음. 3년 경과 시 자동해제가 아니라 본안소송 미제기 등 요건에서 가압류 취소를 신청할 수 있음. 가압류와 지급명령은 별개 절차. 사건번호·집행일자 보관."},
   {"type": "MEMBER_EXPERIENCE", "speaker": "작성자", "text": "메모해 두고 대응(후속 결과 없음)."}],
  [{"kind": "SIMILAR_CANDIDATE", "ref": "5568-Q16", "note": "가압류 여부"}, {"kind": "SIMILAR_CANDIDATE", "ref": "5568-Q19", "note": "가압류 후 조치(3년 경과)"}],
  flags=["가압류 취소 요건 설명은 운영자의 일반 법률 설명(미검증) — 법률 자문 아님", "신청 직전 연체 대응은 개인·운영 관찰 사례, 일반화 금지"], dup=DUP_CASE_NEW),
 item("6050", A, "31312978", "질문게시판", "2026-09-29 19:45", "폐업 후 보증금은 어떻게 되나요?", "재산심사·사업장 보증금",
  "OPERATOR_OBSERVATION + MEMBER_EXPERIENCE(상반)",
  [{"type": "QUESTION", "speaker": "일반멤버(작성자)", "text": "폐업 후 받은 보증금을 부실차주 신청 시 통장거래내역에서 문제 삼는지. 7월 이후 강화됐다는데."},
   {"type": "COUNSELOR_CLAIM", "speaker": "지피지기자산(운영자)", "label": "CLAIM", "text": "최근 사례에서 폐업 후 받은 보증금에 대해 사용처 소명을 요구한다고 함."},
   {"type": "MEMBER_EXPERIENCE", "speaker": "회원 후추", "label": "EXPERIENCE", "text": "'소명 없었다'(상반된 경험)."},
   {"type": "OPERATOR_GUIDE", "speaker": "지피지기자산(운영자)", "label": "CLAIM", "text": "2024년 운영자 사례(6월 폐업→8월 신청, 폐업사실증명 제출)에서는 접수 후 임대차서류·보증금 반환내역을 별도 요구받지 않음. 2026년 8월 이후에는 접수 후 심사 중 폐업 사업장의 임대차서류·보증금 반환 서류/현황을 개별 추가 요구하는 사례가 관찰됨('심사 변화 의심')."}],
  [{"kind": "TOPIC_LINK", "ref": "CCRS-798001-K1", "note": "K1(금융마이데이터 9/7)과 재산심사 강화 흐름 토픽 연결 — 인과 아님"}],
  flags=["운영자 관찰과 회원 경험이 상반 → CONFLICT_REVIEW", "'심사 변화 의심'은 공식 확인 아님"], dup=DUP_CASE_NEW),
 item("6055", A, "31312978", "부실차주 신청", "2026-09-30 07:53", "추심전화 열번도 안받았어요", "추심 대응·신청 후기",
  "MEMBER_EXPERIENCE",
  [{"type": "ACTUAL_CASE", "speaker": "인기멤버(작성자)", "label": "EXPERIENCE", "text": "채권 6곳. 추심 문자·전화가 오면 바로 금감원에 신고했더니 취하 요청 전화 후 문자만 간간히 옴. 연체 105일 경과 후 부실차주 신청, 1년 유예 없이 상환 계획."},
   {"type": "COMMUNITY_OPINION", "speaker": "작성자", "label": "CLAIM", "text": "부실우려를 거치지 말고 바로 부실차주로 가길 추천, '90일 아무것도 아니다'(개인 의견)."}],
  [], flags=["'바로 부실차주' 추천은 개인 의견 — 다른 운영자 안내(부실우려→실효 경로)와 병존, 우열 판단 금지", "댓글 0건, 결과 후속 없음"], dup=DUP_CASE_NEW),
]

# ── B: CCRS 09/24~09/30 ──────────────────────────────────────────────────
B = "B"
t798 = raw("798001")
items += [
 item("798001", B, "27453843", "연체전(신속),이자율(프리),채무조정(개인) (menuId 87)", "2026-09-24 13:13",
  "[금융정책] 신복위에서 9월7일부터 심사시 금융자산을 보기 시작했습니다", "재산심사·금융자산(신복위)",
  "MIXED — 원문 미러는 CONFIRMED/CLAIM/INFERENCE 혼재",
  [{"type": "RAW_MIRROR", "label": "MIXED", "text": "원문 전체 미러(확정 사실+제보+추정 혼재). RAG 혼합 위험 → 이 미러 자체보다 K1~K7 분리 항목을 승인 대상으로 권고."}],
  [{"kind": "SPLIT_INTO", "ref": f"CCRS-798001-K{i}", "note": "분리 저장"} for i in range(1, 8)],
  flags=["기관 분리: 신복위 자료 — 새출발기금 자동 적용 규칙 생성 금지", "20M/35M 제보·감면율 추정을 확정 사실과 섞지 않음"],
  dup=DUP_CASE_NEW, reco="HOLD_RAW_MIRROR__APPROVE_K1_K7_INSTEAD"),
 item("799029", B, "27453843", "연체전(신속),이자율(프리),채무조정(개인) (menuId 87)", "2026-09-28 16:19",
  "[개인] 개인워크체결 후기 긴글주의(고소득자에서 다자녀취약계층 체결 과정)", "개인워크아웃 사례",
  "MEMBER_EXPERIENCE (단일 사례)",
  [{"type": "ACTUAL_CASE", "speaker": "일반회원(작성자)", "label": "EXPERIENCE", "text": "30대 중반 여성, 채무 5천만원 초과. 2022 프리워크아웃(월 77만원×48개월 안내→연체 후 실효), 2024 개인워크아웃(고소득으로 감면 거의 불가, 24개월 월 200만원+ →소득 감소로 유예 후 연체·실효), 2026-07 3번째 신청(전화상담, 다자녀취약계층 접수비·예납금 면제 안내, 부동의 카톡 1회 후 9/28 합의서 체결). 결과: 원금+이자 7,700만원+ → 2,800만원 감면, 상환기간 최장(96개월 목표)."},
   {"type": "MEMBER_EXPERIENCE", "speaker": "댓글 회원들", "label": "EXPERIENCE", "text": "실효 후 추심 심하지 않았음, 지급명령·법원 방문 있었으나 90일 견딜 만함, 통장압류 없었음, 전화 접수 가능(예약 한 달 대기)."}],
  [], flags=["개인워크아웃(신복위) 사례 — 새출발기금 부실차주 규칙과 혼용 금지", "단일 사례 — 감면 결과 일반화 금지"], dup=DUP_CASE_NEW),
]

# K1~K7 (798001 분리 항목)
K = [
 ("K1", "신복위 금융자산 심사: 금융마이데이터 시범 2026-08-31~09-04(서울중앙지부), 정식 2026-09-07(전 지부)", "CONFIRMED", "USER_PROVIDED_CAPTURE 대조(신복위 공지). 원 URL 직접 재확인은 하지 않음 → 승인 시 공식 링크 대조 권장"),
 ("K2", "신복위 금융자산 조회 항목: 은행·보험·금융투자·휴면예금/보험", "CONFIRMED", "USER_PROVIDED_CAPTURE 대조. 각 자산의 반영 방식은 미확인"),
 ("K3", "게시자 설명: 감사원 지적을 배경으로 신복위가 금융자산을 반영하기 시작", "CLAIM", "게시자 2차 해석, 공식 인과 확정 금지"),
 ("K4", "상담 제보: 금융자산 3,500만원 초과 시 접수 불가 안내(9/25 업데이트)", "CLAIM", "단일 상담 제보 — 일반 cutoff 변환 금지"),
 ("K5", "상담 제보: 금융자산 2,000만원 초과 시 불가 안내(9/25 업데이트)", "CLAIM", "K4와 수치 상충 — MERGE 금지"),
 ("K6", "커뮤니티 추정: 기존 재산+금융자산이 총채무를 넘지 않는 범위로 심사", "INFERENCE", "게시자 '확실치 않음' 명시 — RULE 생성 금지"),
 ("K7", "커뮤니티 추정: 금융자산이 채무과중도·감면율 산정에 반영될 수 있다", "INFERENCE", "감면율 산식 변환 금지"),
]
for kid, ttl, label, note in K:
    items.append({
        "manifestId": f"AB-798001-{kid}",
        "bucket": "B",
        "source": src("798001", "27453843", "menuId 87", "2026-09-24 13:13"),
        "title": f"[CCRS-798001-{kid}] {ttl}",
        "category": "재산심사",
        "sourceTypeProposed": "CASE" if kid in ("K4", "K5", "K6", "K7") else "CAFE_ARTICLE",
        "evidenceType": label,
        "segments": [{"type": {"CONFIRMED": "OPERATOR_GUIDE", "CLAIM": "COUNSELOR_CLAIM", "INFERENCE": "INFERENCE"}[label], "label": label, "text": ttl, "note": note}],
        "dedup_key": f"cafe:27453843:798001:{kid}",
        "fingerprint": fp(ttl + note),
        "relations": [{"kind": "SIMILAR", "ref": "798001 RAW_MIRROR", "note": "원본 출처 보존 연결"}] + ([{"kind": "TOPIC_LINK", "ref": "6050", "note": "폐업 보증금 서류 요구(토픽 연결, 인과 아님)"}] if kid == "K1" else []),
        "relatedCaseIds": [],
        "targetStatus": "REVIEW",
        "payloadReady": True,
        "flags": ["기관 분리: 신복위", "OFFICIAL 승격 금지" if label != "CONFIRMED" else "CONFIRMED=사용자 캡처 대조 기준, 공식 링크 대조 후 ACTIVE 권장"],
        "duplicateCheck": {"knowledge": "NEW 예상 (제목 [CCRS-798001-Kn] 고유). 승인 전 PROD 제목 대조"},
        "approvalRecommendation": "REVIEW_THEN_ADMIN_DECIDE",
        "contentSource": "enrich_798001_usercapture.sql",
    })

# ── DESIGNATED 5 (phase1 지정수집) ───────────────────────────────────────
D = "DESIGNATED"
t4451 = raw("4451")
red4451, removed4451 = redact_4451(t4451)
assert "[SAFETY_FLAG]" not in red4451.replace("SAFETY_FLAG(재산심사", "")
items += [
 item("3401", D, "31312978", "초보가이드", "2026-01-04 11:57", "부동산 후순위 담보대출 처리법", "담보대출·중개형",
  "OPERATOR_GUIDE(비공식)",
  [{"type": "OPERATOR_GUIDE", "speaker": "지피지기자산(카페매니저)", "label": "CLAIM", "text": "①부실우려 때 후순위 담보대출을 중개형 채무로 두면 실효 후 부실차주로 자동 이관 ②90일 연체 후 바로 부실차주로 온 경우 신청 후 신복위 방문해 중개형 조정 ③우려 때 미포함이면 주담대는 자동 포함되지 않아 새출발 콜센터에 직접 접수 의사 표시 필요, 부실차주 이후 후순위 캠코 담보 접수는 원채권사 부동의 시 중단되어 정상 상환."}],
  [{"kind": "SIMILAR_CANDIDATE", "ref": "6037", "note": "후순위 담보대출 사례"}],
  flags=["운영자 안내, 공식 규정 아님"], dup={"empirical_cases": "NEW", "knowledge": "PROD_TITLE_CHECK_REQUIRED"}),
 item("3438", D, "31312978", "초보가이드", "2026-01-05 21:13", "10프로 추가감면 교육", "추가감면·국민취업지원제도",
  "OPERATOR_GUIDE(비공식) + REPEATED_OBSERVATION",
  [{"type": "OPERATOR_GUIDE", "speaker": "지피지기자산(카페매니저)", "label": "CLAIM", "text": "10% 추가감면 교육은 폐업증명서가 필요. 확실한 루트: 국민취업제도+희망리턴패키지 재창업(이러닝·비대면) 또는 재취업(기초+심화). 확정안 담당자들이 작년에 위 두 경우 외에는 높은 감면을 주지 않았다고 서술(전언). 국민취업지원제도 유형·신청 방법·절차 일반 정보 포함."},
   {"type": "MEMBER_EXPERIENCE", "speaker": "회원", "label": "EXPERIENCE", "text": "1유형 구직촉진수당이 월 50만원→60만원×6개월로 변경됐다는 회원 보고."}],
  [], flags=["'담당자 전언' 부분은 CLAIM(공식 아님)", "국민취업지원제도 수치는 고용24 공식 대조 필요"], dup={"empirical_cases": "NEW", "knowledge": "PROD_TITLE_CHECK_REQUIRED"}),
 item("4078", D, "31312978", "초보가이드", "2026-02-07 18:26", "부실우려 신청 후기 (감사회원 땜통욱님)", "신청 후기(이미지 본문)",
  "MEMBER_EXPERIENCE",
  [{"type": "MEMBER_EXPERIENCE", "label": "EXPERIENCE", "text": "본문 전체가 이미지 4장(OCR 미수행). 댓글은 응원·지점 문의 수준."}],
  [], ready=False, flags=["IMAGE_ONLY — 텍스트 본문 없음, OCR/수기 구조화 전 REVIEW payload 금지"], dup={"empirical_cases": "NEW", "knowledge": "n/a"}, reco="NEEDS_ORIGINAL_TEXT"),
 item("4451", D, "31312978", "초보가이드", "2026-03-06 19:37", "감사회원 FAQ (자주하는 질문들) 정리집", "운영자 FAQ(50문답)",
  "OPERATOR_GUIDE(비공식) — SAFETY_FLAG 포함",
  [{"type": "OPERATOR_GUIDE", "speaker": "지피지기자산(카페매니저, 단톡방 방장 정리본)", "label": "CLAIM",
    "text": f"운영자 FAQ 50문답 중 재산심사 회피성 권고 Q{removed4451} 삭제본. 원문 전체는 raw 보존."}],
  [{"kind": "OVERLAP_CANDIDATE", "ref": "5568", "note": "5568과 부분 겹침(이전 curation 문서 NEWSTART_OPERATOR_FAQ_5568_4451_CURATION 참조)"}],
  ready=True, flags=[f"SAFETY_FLAG: 타인 계좌 이체·현금화 등 재산심사 회피성 권고 Q{removed4451} — payload 는 삭제본만 사용", "이 삭제본 기준으로만 REVIEW 생성 가능. phase1 SQL 미러(원문 그대로)는 승인 금지"],
  dup={"empirical_cases": "NEW", "knowledge": "DUPLICATE_CHECK_REQUIRED — 운영 OPERATOR_KNOWLEDGE 1/CAFE_ARTICLE 1건과 충돌 가능(이전 4451 후보화 이력)"},
  reco="REVIEW_REDACTED_ONLY__NEVER_APPROVE_PHASE1_MIRROR", body=red4451, content_override=True),
 item("5568", D, "31312978", "초보가이드", "2026-06-29 10:27", "부실차주 전자책 상담방 - 30개 Q&A 정리 (실제 33문답)", "운영자 Q&A(33문답)",
  "OPERATOR_GUIDE(비공식)",
  [{"type": "OPERATOR_GUIDE", "speaker": "지피지기자산(카페매니저)", "label": "CLAIM", "text": "2026-03~06 상담방 대화와 6/25 금융위 개선안을 바탕으로 정리한 33문답(자격·부실우려→부실차주 전환·재산/감면율·추심/법적대응·서류·진행·6/25 개선안). 운영자 안내이며 공식 규정 아님."}],
  [{"kind": "OVERLAP_CANDIDATE", "ref": "4451", "note": "부분 겹침"}],
  ready=True, flags=["Q14(통장잔고 정리 유리)·Q15(보험 계약자·수익자 가족 변경 시 제외 가능) — 재산 정리 관련, 승인 전 SAFETY 재검수 필요", "Q28은 부동산 매도·명의변경 금지를 명시(안전)"],
  dup={"empirical_cases": "NEW", "knowledge": "DUPLICATE_CHECK_REQUIRED — 운영 OPERATOR_KNOWLEDGE 1건과 충돌 가능(이전 5568 후보화 이력)"},
  reco="REVIEW_THEN_SAFETY_RECHECK"),
]

counts = {
    "total": len(items),
    "payloadReady": sum(1 for i in items if i["payloadReady"]),
    "notReady": [(i["manifestId"], i["approvalRecommendation"]) for i in items if not i["payloadReady"]],
    "byBucket": {b: sum(1 for i in items if i["bucket"] == b) for b in ("A", "B", "DESIGNATED")},
    "dupCheckRequired": [i["manifestId"] for i in items if "DUPLICATE_CHECK_REQUIRED" in json.dumps(i["duplicateCheck"], ensure_ascii=False)],
    "relationsListed": sum(len(i["relations"]) for i in items),
}
out = {"generatedAt": "2026-09-30", "readOnly": True, "productionWrite": "NONE", "counts": counts, "items": items}
p = os.path.join(HERE, "INGEST_READY_MANIFEST_AB_2026-09-30.json")
json.dump(out, open(p, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print(json.dumps(counts, ensure_ascii=False, indent=2))
