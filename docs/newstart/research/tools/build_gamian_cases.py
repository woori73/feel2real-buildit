# -*- coding: utf-8 -*-
"""gamian empirical-case JSON 생성기 (Agent 3 큐레이션 결과물).

입력: docs/newstart/research/article_json/article_XXXX.json (원문 provenance)
출력: data/newstart-cafe/gamian/articles/article_XXXX.json
      (NewstartDataIngestionService.buildCaseEntity camelCase 계약 준수)

규칙:
- 값이 미확인인 필드는 null (추측 금지). 금액은 원 단위 숫자(longVal 계약).
- writtenAt/collectedAt: LocalDateTime.parse 가능 형식(오프셋 제거, 초 보정).
- JSON 컬럼(debtBreakdown/managerPrediction/specialCircumstances/keyInsights/
  predictionVsActual)은 JSON-인코딩 문자열.
- PII 정책: authorNickname/authorGrade null.
"""
import json
import os
import re
import sys

SRC_DIR = "docs/newstart/research/article_json"
OUT_DIR = "data/newstart-cafe/gamian/articles"


def load(aid):
    with open(os.path.join(SRC_DIR, f"article_{aid}.json"), encoding="utf-8") as f:
        return json.load(f)


def iso(dt):
    """'2026.08.21. 10:42' 또는 ISO+오프셋 -> LocalDateTime 파싱 가능 형식."""
    if not dt:
        return None
    m = re.match(r"(\d{4})\.(\d{1,2})\.(\d{1,2})\.?\s+(\d{1,2}):(\d{2})", dt)
    if m:
        y, mo, d, h, mi = m.groups()
        return f"{y}-{int(mo):02d}-{int(d):02d}T{int(h):02d}:{mi}:00"
    return dt.replace("+09:00", "").replace("+00:00", "")


def jcols(obj):
    return json.dumps(obj, ensure_ascii=False)


def comments_of(d):
    return [(c.get("text") or "").strip() for c in (d.get("comments") or [])]


def build(aid, meta):
    d = load(aid)
    case = {
        "articleId": str(aid),
        "boardName": d.get("boardName"),
        "title": d.get("title"),
        "authorNickname": None,
        "authorGrade": None,
        "writtenAt": iso(d.get("writtenAt")),
        "viewCount": d.get("viewCount"),
        "sourceUrl": d.get("sourceUrl") or f"https://cafe.naver.com/newhopero/{aid}",
        "collectedAt": iso(d.get("collectedAt")),
        "contentRaw": d.get("bodyNormalized") or d.get("bodyRaw"),
    }
    numeric = [
        "debtTotal", "debtSecured", "debtUnsecured", "debtAcquiredByFund",
        "assetProperty", "assetVehicle", "assetFinancial", "income2025",
        "incomeCurrent", "predictedRepaymentAmount", "finalRepaymentAmount",
        "monthlyPayment",
    ]
    ints = ["dependents", "age", "repaymentPeriodMonths", "gracePeriodMonths"]
    texts = [
        "householdType", "employmentStatus", "businessStatus", "applicationType",
        "predictedReductionRate", "finalReductionRate", "tempReductionRate",
        "authorFollowupResult",
    ]
    jsons = ["debtBreakdown", "managerPrediction", "predictionVsActual",
             "specialCircumstances", "keyInsights"]
    for k in numeric + ints:
        case[k] = meta.get(k)  # None 허용, 숫자만 넣는다
    for k in texts:
        case[k] = meta.get(k)
    for k in jsons:
        v = meta.get(k)
        case[k] = jcols(v) if v is not None else None
    return case


CASES = {}

# ---------------------------------------------------------------- 결과확인

CASES[5866] = dict(
    applicationType="부실차주(90일 연체 후 직접 신청)",
    finalReductionRate="90",
    authorFollowupResult="2026-03-25 부실차주 신청 → 2026-08-21 새출발기금 전화로 채무조정 확정, 90% 탕감 통보 (신청~확정 약 5개월)",
    specialCircumstances={
        "연체": "90일 연체 후 부실차주로 바로 진행",
        "대기": "임시안 통지 후 확정 연락까지 약 70일 (신청자 증가로 심사 강화·지연)",
        "연락": "02-3420 발신번호로 확정 전화, 사전 날짜 고지 없음",
    },
    keyInsights=[
        "부실차주 직행으로 90% 탕감 확정된 결과확인 사례",
        "신청→확정 약 5개월이 실제 소요시간 기준값",
        "임시안 이후 확정 통지에 70일 내외 추가 대기 — 일희일비 금지 근거(제4장 멘탈 원칙의 실증)",
        "댓글 제보: 양도예정통지서 발송이 임박 신호이나 기금에서 날짜를 알려주지는 않음",
    ],
)

CASES[5952] = dict(
    applicationType="부실차주",
    finalReductionRate="90",
    monthlyPayment=19000,
    authorFollowupResult="2026-09 월 게시: 직전 달 체결완료, 90% 탕감, 사업소득 0원으로 예상보다 빠른 체결, 월 납부 19,000원",
    specialCircumstances={
        "소득": "사업소득 0원 — 체결 속도에 긍정적 영향(본인 진단)",
        "추가질문": "채무조정 상환 중 부모 결혼자금 계좌 입금 시 문제 여부 — 원문 내 미답",
    },
    keyInsights=[
        "사업소득 0 + 90% 탕감 + 월 19,000원 — 저소득·저상환력 조건에서 감면 상한 사례",
        "체결 후 신규 자금 유입(증여)이 상환계획에 미치는 영향은 미해결 질문으로 보존",
    ],
)

CASES[5988] = dict(
    debtTotal=63000000,
    debtSecured=184000000,
    debtUnsecured=63000000,
    assetProperty=100000000,
    income2025=0,
    householdType="본인+노부모(기초수급) 부양, 딸별거",
    applicationType="부실우려(2개월 납부 후 2025-10-말 실효) → 부실차주 접수",
    finalReductionRate="0",
    businessStatus="코로나 전후 매출 급감, 5년치 소득금액증명 0원",
    authorFollowupResult="확정안에서 채무보다 재산가액이 많아 감면 0% 판정 → 2026-09-08 국민신문고 민원, 캠코 담당자 재연락 예고 (2차 도전 진행 중, 결과 미확정)",
    debtBreakdown={
        "새출발기금 대상 채무": 63000000,
        "아파트 잔금대출(별도)": 184000000,
    },
    specialCircumstances={
        "부동산": "시가 3.2억 아파트, 대출 1.84억 남음, 기금 산정 재산가액 1억",
        "대납": "딸이 대출 원리금·관리비·소송비 등 총 8천만원 대납 — 명의만 본인, 실질 권리는 딸이라는 공동지분 차감 요청이 거부됨",
        "신용회복위원회": "이행 시 50% 감면 안내를 받았으나 새출발 부동의·체결 전 단계",
        "형평성불만": "카페 제보 대비 — 고소득·다재산자도 70% 감면받은 사례가 있다며 형평성 문제 제기",
        "행정구제": "국민신문고 민원이 캠코 담당자 재연락을 이끌어냄",
    },
    keyInsights=[
        "재산가액(1억) > 조정대상 채무(6,300만) 구조 → 감면 0% — 재산 산정이 감면의 지배 요인임을 실증(제7장 공식의 역방향 사례)",
        "타인(자녀) 대납분의 지분 인정은 거부되는 것으로 관찰됨 — 미확인 쟁점이므로 CONFLICT_REVIEW 대상",
        "확정안 불복 경로: 국민신문고 민원이 실무적으로 담당자 재접점을 만듦",
        "부실우려 납부 2개월 후 실효 → 부실차주 전환 루트 실사용 사례",
    ],
    predictionVsActual={"신복위 안내": "50% 감면", "새출발기금 확정": "감면 0%", "차이": "-50%p (기관별 재산·공제 산정 차이 추정, 확정 사유는 미확인)"},
)

CASES[6010] = dict(
    debtUnsecured=70000000,
    assetProperty=None,
    finalReductionRate="0",
    householdType="차상위계층(행정복지센터 지원 수급)",
    applicationType="부실우려 → 부실차주 전환",
    authorFollowupResult="확정 전 판정 단계에서 재산가액 높아 감면 0% 통보. 체결 전, 개인워크아웃 전환 검토 중 (결과 미확정)",
    specialCircumstances={
        "부동산": "자가 시세(부동산테크) 2억 vs 실거래가 1.3억 — 아파트는 부동산테크 시세 기준 산정",
        "공시가적용요청": "빌라는 공시지가 적용 사례가 있다며 요청했으나 거절",
        "근저당": "무담보 7천만원, 시세 상승으로 근저당 9천 증액 요구 상황",
    },
    keyInsights=[
        "아파트 재산가액 = 부동산테크 시세 기준(실거래가·공시지가 아님) — 운영자/선배 답변으로 확인",
        "차상위계층·행정복지센터 지원 여부는 감면율에 영향 없음",
        "'이미 결정난 방식은 변경 불가, 안 되면 버티다 개인워크아웃' — 실무자급 대응 원칙 제보",
        "감면 0% 판정이라도 체결 전이면 대안(워크아웃) 검토 여지 존재",
    ],
)

CASES[5867] = dict(
    applicationType="부실차주",
    finalReductionRate="30",
    authorFollowupResult="2025-08 부실차주 신청 → 2026-08-21 전화 결과(30%로 바뀌었다는 본인 서술), 신청~통화 약 1년",
    specialCircumstances={
        "지연": "1년간 회신 없음 — 그 사이 어카운트인포 유효기간(1년) 만료로 서류 재제출",
        "지역": "경기 지역",
        "기준": "이전 수치가 무엇이었는지 원문 미기재 — 30%가 최초안인지 변경안인지 불명",
    },
    keyInsights=[
        "신청→확정 통보 최장지연 사례(약 1년) — 평균 5개월(5866)과의 편차 기록",
        "장기 대기 시 어카운트인포 발급일 기준으로 재제출 요구 발생",
        "결과값 30%의 비교 기준(임시안 수치)이 없어 predictionVsActual은 UNKNOWN으로 보존",
    ],
)

CASES[5862] = dict(
    debtTotal=45000000,
    debtSecured=25000000,
    applicationType="부실우려 실효 → 부실차주 접수",
    authorFollowupResult="체결 서명 직전 단계. 저축은행 대출 4,500만 중 잔액 2,500만이 '차량담보'로 분류되어 원금 감면 제외·이자 별도 상환 대상(2026-08-20 기준)",
    specialCircumstances={
        "경위": "사업자대출로 알았으나 실행 시 '차량번호 있으면 승인율 높다'며 차량담보화 — 저당 설정되어 매도·폐차 불가",
        "차량": "2012년식 스파크, 차량가액 약 250만",
        "압류": "연금공단 압류 진행 중",
    },
    keyInsights=[
        "대출 실행 단계에서 차량번호 요구로 사업자신용대출이 차량담보대출로 전환되는 함정 — 제9장 '차량 담보는 원금 감면 없음'의 실사례",
        "차량가액(250만)보다 채무(2,500만)가 커도 담보 분류만 되면 별도 상환 유지",
        "부실우려 실효 후 부실차주 접수 루트 사용 사례",
    ],
)

CASES[5884] = dict(
    applicationType="부실우려 실효 → 부실차주 전환",
    businessStatus="정상 진행(임시안 대기)",
    authorFollowupResult="2026-08-25 신복위 앱에서 실효 확정 확인 → 실효 후 장기연체등록·부실차주 접수까지 약 5일(본인 보고, 타회원 약 1주) → 원장초기화 대상 확인·캠코 서류 제출 → 임시안 대기 중(지연 안내 수신)",
    specialCircumstances={
        "원장초기화": "문자로 대상 통지 → 캠코 전화 확인 → 이메일 제출. 새출발기금 홈페이지 진행상태에서 대상 여부 확인 가능",
        "절차": "캠코 요구 서류를 하나씩 준비·제출, 예상보다 빨리 진행",
        "지연": "2026-09 기준 신청자 폭주로 임시안 지연 안내",
    },
    keyInsights=[
        "부실우려 실효 → 장기연체 등록 → 부실차주 접수 실측 5일~1주일 (제8장 전환전략의 시간 검증)",
        "90일 버티면 부실차주 신청 가능 — 실효 즉시 연체등록되는 것은 아님",
        "원장초기화 대상 여부 확인 3경로: 홈페이지 진행상태 / 문자 / 캠코 전화",
    ],
)

# ---------------------------------------------------------------- 예측 제공(운영자)

CASES[5904] = dict(
    age=None,
    householdType="4인(미성년 자녀 포함)",
    income2025=80000000,
    debtTotal=330000000,
    debtBreakdown={
        "소상공인 정책자금": 40000000,
        "중소기업진흥공단": 200000000,
        "1금융권 신용대출": 40000000,
        "카드(할부·일시불·카드론)": 50000000,
    },
    assetProperty=60000000,
    assetVehicle=8000000,
    assetFinancial=10000000,
    predictedReductionRate="40",
    managerPrediction={
        "1차(소득 8천만원 반영)": "감면율 40% 예측 — 4인가구·자녀부양 고려, 소득 상위",
        "재산가액 산정": "전세 6,000만 − 지역공제 2,500~3,000만 + 사업자계좌 1,000만 + 자동차 0(4천만 이하) = 4,000만~4,500만",
        "2차(소득금액증명 3천만원 적용 시)": "67~70% 예측",
        "적용 시점": "2027-07 이후 체결 시 2026 소득 반영 가정",
    },
    specialCircumstances={
        "소득 기준": "제시된 합산 8천(근로 2천+사업 6천)과 소득금액증명상 3천이 병존 — 어느 해 소득이 체결 시점에 반영되느냐가 감면율 40 vs 67~70을 결정",
        "자동차": "시세 800만 — 계산기준 4천만원 이하 차량은 재산가액 0원 처리",
    },
    keyInsights=[
        "운영자 예측이 소득 기준 연(年) 하나 차이로 40% ↔ 67~70%로 요동 — 감면율의 소득 지배 원리(제7장 *)의 수치 실증",
        "차량 시세 4천만원 이하는 재산가액 0 처리 기준 운영",
        "최종 상환원금 = 재산가액 + (조정대상 부채 − 재산가액)×(1−감면율) 계산식 사용",
    ],
    predictionVsActual=None,
)

CASES[5947] = dict(
    age=29,
    householdType="단독세대(본인) · 부모+장애 동생 아파트 부양",
    incomeCurrent=None,
    debtTotal=330000000,
    debtBreakdown={
        "기술보증기금": 150000000,
        "소상공인진흥공단": 80000000,
        "국민은행 사업자대출": 100000000,
    },
    predictedReductionRate=None,
    managerPrediction={
        "감면예측 회피 사유": "신청 전 부동산 매매 계획이 사해행위(재산은닉·축소)로 간주되어 신청 직후 채무조정 불가 판정 확률이 큼 — 감면율 계산보다 먼저 정리할 문제",
        "선례": "신청 3년 내 부동산 판매 시 채무조정 불가 안내가 현재 기준",
        "권고": "새출발기금에 유선·직접 상의 후 매매 여부 확답 받을 것",
        "보완 제보": "처분 후 수익의 사용처(월세 보증금 등)는 무관, 처분 자체만 문제. 주식·코인 재산도 디테일하게 보는 강화 추세",
    },
    specialCircumstances={
        "매매계획": "부모 거주 아파트(3.6억, 담보 2.2억) 매각 → 1.4억 중 서울 월세 보증금 5천·부모 주거 3천·생활비 2천, 4천만 채무변제 계획",
        "상속리스크": "지적장애 3급 여동생·장기요양 3등급 부친 부양",
        "은행 동일성": "신용대출·주담대 모두 국민은행 — 상계 우려",
        "소명요청": "신청 후 예상 소득(서울 이주, 월 250만 세전)",
    },
    keyInsights=[
        "신청 전 재산 처분 = 사해행위 리스크 1순위 — 운영자가 감면율 예측을 거부한 첫 사례",
        "처분 '사용처'는 무관하고 '처분 자체'가 심사 대상이라는 선배 제보 (전자책 사해행위 경고의 구체화)",
        "감면율 산정 전에 자격(사해행위) 관문이 선행한다는 심사 순서 시사",
    ],
)

CASES[5948] = dict(
    age=30,
    householdType="1인(원룸)",
    income2025=40096990,
    debtTotal=59813649,
    debtBreakdown={
        "부실우려 채무(별도 계산)": 46745101,
        "흥국생명": 10780548,
        "전북은행": 460000,
        "NH저축은행": 1828000,
    },
    assetProperty=2000000,
    assetFinancial=1500000,
    predictedReductionRate="61",
    predictedRepaymentAmount=23327323,
    managerPrediction={
        "감면율": "60~62% (대표값 61%)",
        "재산": "보증금 200만+적금 150만=350만 명목자산, 지역공제 반영 후 최종 재산가액 0원 가정",
        "상환액": "60%→23,925,460 / 61%→23,327,323 / 62%→22,729,187 (10년 상환 기준 언급)",
        "구조": "부실우려 채무 46,745,101은 기타 금융사 채무와 별도 합산",
    },
    applicationType="부실우려 미납 중(2026-10-20 실효 예정) → 부실차주 신청 예정",
    businessStatus="2024-01-01 폐업, 2024-03~ 재직중",
    specialCircumstances={
        "추가질문": "월급통장 농축협과 NH저축은행 채무의 상계 관련 문의 — 답변 원문 없음",
    },
    keyInsights=[
        "30세·소득 4천만·재산사실상 0 → 60~62%대 예측 — '재산 0'이어도 소득 중위면 감면 상한(90%)이 아니라 중후반이라는 기준점",
        "부실우려 채무는 부실차주 합산에서 별도 항목으로 취급 (전환 시 이중계산 방지)",
    ],
)

CASES[5957] = dict(
    age=61,
    householdType="2인(배우자)",
    income2025=18000000,
    debtTotal=348700000,
    debtBreakdown={
        "신용채무(기금 매입 1.6억+미매입)": 174700000,
        "주담보 농협": 77000000,
        "2차담보 SC": 97000000,
    },
    debtUnsecured=174700000,
    debtSecured=174000000,
    assetProperty=None,
    assetVehicle=None,
    assetFinancial=500000,
    predictedReductionRate="75~77",
    managerPrediction={
        "예상 감면율": "75~77% 잠정 (실제 심사 전)",
        "재산가액": "아파트 인정가액 − 담보 1.74억 − 지역공제 약 2,500만 (2.6억→6,100만 / 2.7억→7,100만 / 2.8억→8,100만 / 2.9억→9,100만)",
        "공식": "최종 상환원금 = 재산가액 + (신용채무 1.747억 − 재산가액)×(1−감면율)",
        "예상 상환": "2.6억→약 8,715만~8,943만 … 2.8억→약 1억243만~1억443만",
        "배제": "금융자산 50만, 2009년식 차량은 재산 제외",
        "방법론": "인공지능으로 전자책 내용과 사례를 바탕으로 만든 예측 — 참고용",
    },
    applicationType="부실우려 2026-09-01 실효 후 부실차주 신청 예정",
    businessStatus="2007~2025-10 사업, 폐업 후 2025-11~ 근로 재직(기본급 270만+연장)",
    specialCircumstances={
        "배우자": "2025년 암 수술 후 치료 중, 아파트 배우자와 공동명의(저층)",
        "연금": "조기국민연금 수령 중(생활비 부족, 2025 기준 약 950만)",
        "소득 산정": "사업 100만 + 근로 760만(11~12월) + 연금 950만 ≈ 1,800만 잠정",
        "공동명의 질의": "시세 절반 − 담보 절반 계산, 지역공제는 유지 / 본인 명의 담보는 전체 공제 가능성 — 기금 적용 결과 미확인",
        "교육": "희망리턴·특화취업 기초교육 2025년 수료(심화 미이수)",
    },
    keyInsights=[
        "운영자 예측이 '전자책+사례 기반 AI 생성'임을 공개 — EBOOK↔CASE 파이프라인의 존재 자체를 증빙하는 1차 사료",
        "61세·1,800만 소득·재산가액 6,100만~9,100만 → 75~77% — 고연령·저소득이 감면율 상방 요인",
        "공동명의 재산 산정(절반 차감)과 지역공제 적용 순서는 결과 확인 전 — CONFLICT_REVIEW 후보",
        "저배기량/노후(2009년식) 차량은 재산 제외 — 전자책 제9장 기준과 부합",
    ],
)

# ---------------------------------------------------------------- 생성

def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for aid, meta in CASES.items():
        case = build(aid, meta)
        out = os.path.join(OUT_DIR, f"article_{aid}.json")
        with open(out, "w", encoding="utf-8") as f:
            json.dump(case, f, ensure_ascii=False, indent=2)
        print("wrote", out)
    print("total", len(CASES))


if __name__ == "__main__":
    main()
