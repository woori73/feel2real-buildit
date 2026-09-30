# -*- coding: utf-8 -*-
"""Build official_updates connect payloads from k13~k42 sources + new FSC/HOPE collection.

Reads k-item JSONs (title/summary/url/date) — no re-crawl. New FSC/HOPE items are
hand-verified (live page fetch 2026-10-01) and preserved under research-official/.
Output: docs/newstart/research-official/updates-connect/official_updates_connect_2026-10-01.json
Row shape matches POST /api/admin/newstart/updates (admin create -> REVIEW).
"""
import io
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))  # updates-connect/
OFFICIAL = os.path.dirname(HERE)
CCRS_DIR = os.path.join(OFFICIAL, 'ccrs')

CONNECT = [
    # (kfile, sourceType, sourceName, officialDocumentId, category, publishedAt, skip_reason)
    ('k13', 'CCRS', '신용회복위원회', 'CCRS-K13', 'DEBT_ADJUSTMENT', '2026-09-30T00:00:00', None),
    ('k14', 'CCRS', '신용회복위원회', 'CCRS-K14', 'DEBT_ADJUSTMENT', '2026-09-30T00:00:00', None),
    ('k15', 'CCRS', '신용회복위원회', 'CCRS-K15', 'APPLICATION', '2026-09-30T00:00:00', None),
    ('k16', None, None, None, None, None, 'kamco.or.kr host - KAMCO channel 없음'),
    ('k17', None, None, None, None, None, 'sinbo.or.kr 지역신보 - channel 없음'),
    ('k18', None, None, None, None, None, 'gcgf.or.kr 경기신보 - channel 없음'),
    ('k19', None, None, None, None, None, 'gcgf.or.kr 경기신보 - channel 없음'),
    ('k20', None, None, None, None, None, 'gcgf.or.kr 경기신보 - channel 없음'),
    ('k21', 'NEWSTART_FUND', '새출발기금', 'NSF-K21', 'ELIGIBILITY', '2026-09-30T00:00:00', None),
    ('k22', 'CREDIT4U', 'Credit4U', 'CREDIT4U-K22', 'OTHER', '2026-09-30T00:00:00', None),
    ('k23', 'FSC', '금융위원회', 'FSC-K23', 'ELIGIBILITY', '2026-09-30T00:00:00', None),
    ('k24', 'FSC', '금융위원회', 'FSC-K24', 'ELIGIBILITY', '2026-09-30T00:00:00', None),
    ('k25', None, None, None, None, None, 'kcredit.or.kr 올크레딧 - Credit4U와 다른 기관'),
    ('k26', None, None, None, None, None, 'easylaw.go.kr host - Credit4U 귀속 부적합'),
    ('k27', 'CREDIT4U', 'Credit4U', 'CREDIT4U-K27', 'OTHER', '2026-09-30T00:00:00', None),
    ('k28', 'NEWSTART_FUND', '새출발기금', 'NSF-K28', 'ELIGIBILITY', '2026-09-30T00:00:00', None),
    ('k29', 'FSS', '금융감독원', 'FSS-K29', 'NOTICE', '2026-09-30T00:00:00', None),
    ('k30', 'FSS', '금융감독원', 'FSS-K30', 'NOTICE', '2026-09-30T00:00:00', None),
    ('k31', 'FSS', '금융감독원', 'FSS-K31', 'NOTICE', '2026-09-30T00:00:00', None),
    ('k32', None, None, None, None, None, 'law.go.kr 법령원문 - RAG 지식으로 충분'),
    ('k33', None, None, None, None, None, 'law.go.kr 법령원문 - RAG 지식으로 충분'),
    ('k34', 'FSS', '금융감독원', 'FSS-K34', 'NOTICE', '2026-09-30T00:00:00', None),
    ('k35', None, None, None, None, None, 'easylaw.go.kr host - 법원 귀속 부적합'),
    ('k36', 'COURT', '법원 전자소송', 'COURT-K36', 'NOTICE', '2026-09-30T00:00:00', None),
    ('k37', 'COURT', '법원 전자소송', 'COURT-K37', 'NOTICE', '2026-09-30T00:00:00', None),
    ('k38', 'COURT', '법원 전자소송', 'COURT-K38', 'NOTICE', '2026-09-30T00:00:00', None),
    ('k39', None, None, None, None, None, 'easylaw.go.kr host - 법원 귀속 부적합'),
    ('k40', 'NEWSTART_FUND', '새출발기금', 'NSF-K40', 'POLICY', '2026-09-30T00:00:00', None),
    ('k41', None, None, None, None, None, 'klac.or.kr 법률구조공단 - channel 없음'),
    ('k42', 'COURT', '법원 전자소송', 'COURT-K42', 'NOTICE', '2026-09-30T00:00:00', None),
]


def load_k(prefix):
    import glob
    matches = sorted(glob.glob(os.path.join(CCRS_DIR, prefix + '_*.json')))
    assert len(matches) == 1, (prefix, matches)
    with io.open(matches[0], encoding='utf-8') as f:
        return json.load(f)


def short_summary(text, limit=270):
    t = ' '.join((text or '').split())
    if len(t) <= limit:
        return t
    cut = t[:limit].rsplit(' ', 1)[0]
    return cut


NEW_ITEMS = [
    {
        'sourceType': 'FSC', 'sourceName': '금융위원회',
        'title': '새출발기금 지원체계를 보완하여 필요한 분들께 채무조정 혜택이 더욱 집중되도록 하겠습니다',
        'summary': '금융위원회·캠코 업무현황 점검회의(2026-06-25). 가상자산·비상장주식 재산심사 반영, 변제가능률 100% 초과 시 최소감면율 60%에서 30%로 차등, 사해행위 조사 강화 등 지원체계 보완.',
        'originalUrl': 'https://www.fsc.go.kr/no010101/87188',
        'officialDocumentId': 'FSC-87188', 'publishedAt': '2026-06-25T00:00:00',
        'category': 'POLICY',
    },
    {
        'sourceType': 'FSC', 'sourceName': '금융위원회',
        'title': '소상공인·자영업자의 채무상환에서 재기까지 새출발기금이 함께 합니다',
        'summary': '2026년 새출발기금 점검회의. 조기상환 추가감면 5~10%, 성실상환 금리인하, 상환유예 사유 확대(출산·육아휴직 등), 취·창업 연계 및 재기지원 지역연계 확대.',
        'originalUrl': 'https://www.fsc.go.kr/no010101/86357',
        'officialDocumentId': 'FSC-86357', 'publishedAt': '2026-03-03T00:00:00',
        'category': 'POLICY',
    },
    {
        'sourceType': 'FSC', 'sourceName': '금융위원회',
        'title': '새출발기금 감사원 지적사항에 대한 대응 방향',
        'summary': '고소득자 지원대상 제외 등 도덕적해이 심사 강화, 가상자산 은닉 사해행위 의심사례 대응, 재산심사 절차 보완. 새도약기금과의 제도 비교 포함.',
        'originalUrl': 'https://www.fsc.go.kr/no010101/85859',
        'officialDocumentId': 'FSC-85859', 'publishedAt': '2025-12-16T00:00:00',
        'category': 'POLICY',
    },
    {
        'sourceType': 'HOPE_RETURN_PACKAGE', 'sourceName': '희망리턴패키지',
        'title': '2026년 희망리턴패키지 재기사업화(재창업) 새출발기금 연계지원 소상공인 모집',
        'summary': '새출발기금 채무조정 약정 체결 폐업 소상공인 대상 재기사업화 연계지원. 공공정보 해제 지원, 재창업진단·사전교육·밀착멘토링·사업화자금 최대 1천만원.',
        'originalUrl': 'https://www.bizinfo.go.kr/sii/siia/selectSIIA200Detail.do?pblancId=PBLN_000000000117933',
        'officialDocumentId': 'HOPE-117933', 'publishedAt': '2026-01-28T00:00:00',
        'category': 'APPLICATION',
    },
    {
        'sourceType': 'HOPE_RETURN_PACKAGE', 'sourceName': '희망리턴패키지',
        'title': '2026년 희망리턴패키지 재기사업화(재창업) 소상공인 모집',
        'summary': '폐업(예정) 소상공인 대상 재창업 지원. 재창업진단·사전교육·밀착멘토링·사업화자금 최대 2천만원. 업종전환·창업도약 유형 포함.',
        'originalUrl': 'https://www.bizinfo.go.kr/sii/siia/selectSIIA200Detail.do?pblancId=PBLN_000000000117934',
        'officialDocumentId': 'HOPE-117934', 'publishedAt': '2026-01-28T00:00:00',
        'category': 'APPLICATION',
    },
    {
        'sourceType': 'HOPE_RETURN_PACKAGE', 'sourceName': '희망리턴패키지',
        'title': '2026년 희망리턴패키지 재기사업화(경영개선) 위기 소상공인 진단·멘토링 지원 모집',
        'summary': '위기 소상공인 대상 경영진단·밀착멘토링. 빅데이터 표준진단지 방문진단, 전담 PM 이행관리. 예산 소진시까지 접수.',
        'originalUrl': 'https://www.bizinfo.go.kr/sii/siia/selectSIIA200Detail.do?pblancId=PBLN_000000000121182',
        'officialDocumentId': 'HOPE-121182', 'publishedAt': '2026-04-23T00:00:00',
        'category': 'APPLICATION',
    },
]


def main():
    rows = []
    skipped = []
    for prefix, stype, sname, doc_id, cat, pub_at, reason in CONNECT:
        if reason is not None:
            skipped.append({'k': prefix, 'reason': reason})
            continue
        d = load_k(prefix)
        title = (d.get('title') or '').strip()
        assert title and len(title) <= 200, (prefix, len(title))
        summary = short_summary(d.get('normalized_content'))
        assert summary and summary != title, prefix
        assert not summary.endswith('...'), prefix
        url = (d.get('source_url') or '').strip()
        assert url.startswith('https://'), (prefix, url)
        rows.append({
            'sourceType': stype, 'sourceName': sname, 'title': title,
            'summary': summary, 'content': None, 'originalUrl': url,
            'officialDocumentId': doc_id, 'publishedAt': pub_at,
            'effectiveFrom': None, 'effectiveTo': None, 'category': cat,
        })
    for item in NEW_ITEMS:
        rows.append({**item, 'content': None, 'effectiveFrom': None, 'effectiveTo': None})
    out = os.path.join(HERE, 'official_updates_connect_2026-10-01.json')
    with io.open(out, 'w', encoding='utf-8') as f:
        json.dump(rows, f, ensure_ascii=False, indent=1)
    print('rows:', len(rows), 'skipped:', len(skipped))
    for s in skipped:
        print('SKIP', s['k'], s['reason'])
    by_type = {}
    for r in rows:
        by_type[r['sourceType']] = by_type.get(r['sourceType'], 0) + 1
    print(by_type)


if __name__ == '__main__':
    main()
