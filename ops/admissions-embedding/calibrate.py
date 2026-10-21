"""실제 임베딩 서버로 라벨 질문셋을 돌려 유사도 하한을 제안한다. 질문별 '최고 유사도'가 앱이 보는 값이다."""
import json
import sys
import time
import urllib.request

import embed_core

URL = "http://127.0.0.1:8081/embed"
DOCS = [
    "서울대학교 2027학년도 수시 학생부종합전형(지역균형전형) 수능최저학력기준: 3개 영역 등급 합 7등급 이내",
    "고려대학교 2027학년도 정시 일반전형 모집인원과 전형방법: 수능 100%",
    "연세대학교 2027학년도 수시 논술전형 지원자격 및 제출서류",
    "경희대학교 2027학년도 정시 모집 일정: 원서접수 2027-01-04 ~ 2027-01-07",
    "서강대학교 2027학년도 정시 수능(일반) 모집인원 623명",
    "이화여자대학교 2027학년도 정시 전형방법 및 수능 반영 비율",
    "한양대학교 2027학년도 수시 학생부교과 전형 지원자격 제출서류",
    "성균관대학교 2027학년도 수시 합격자 발표 일정",
]
RELEVANT = ["서울대 지역균형 수능 최저가 어떻게 되나요", "고려대 정시 일반전형은 수능 몇 퍼센트 반영해요",
            "연세대 논술전형 제출서류 알려주세요", "경희대 정시 원서 접수 언제예요",
            "서강대 정시 정원이 몇 명이에요", "이화여대 정시는 어떤 방식으로 뽑나요",
            "한양대 교과전형 지원 자격이 궁금합니다", "성균관대 수시 합격 발표일이 언제인가요"]
IRRELEVANT = ["오늘 점심 뭐 먹을까요", "강아지 훈련 방법 알려줘", "영어 단어 외우는 요령", "주말 날씨가 어떨까요",
              "파이썬 리스트 정렬하는 법", "다이어트에 좋은 운동 추천", "비트코인 가격 전망", "서울에서 부산 가는 기차 시간"]


def embed(texts, task):
    t0 = time.time()
    try:
        return _embed(texts, task)
    finally:
        print("  embed %d texts (%s): %.2fs" % (len(texts), task, time.time() - t0), flush=True)


def _embed(texts, task):
    req = urllib.request.Request(URL, json.dumps({"texts": texts, "task": task}).encode(),
                                 {"Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(req, timeout=180))["embeddings"]


embed(["워밍업"], "document")
docs = embed(DOCS, "document")


def best(queries):
    out = []
    for q in queries:
        qv = embed([q], "query")[0]
        out.append(max(embed_core.cosine(qv, d) for d in docs))
    return out


rel, irr = best(RELEVANT), best(IRRELEVANT)
print("relevant best-sim  min=%.3f max=%.3f" % (min(rel), max(rel)))
print("irrelevant best-sim min=%.3f max=%.3f" % (min(irr), max(irr)))
floor = embed_core.suggest_floor(rel, irr)
print("SUGGESTED_FLOOR=%s" % floor)
sys.exit(0)
