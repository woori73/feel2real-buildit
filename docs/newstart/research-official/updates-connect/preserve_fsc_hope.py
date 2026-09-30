# -*- coding: utf-8 -*-
"""FSC/HOPE collection preservation records (verified 2026-10-01, no re-crawl)."""
import io
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))

FSC = {
    'collector': 'opencode', 'collected_at': '2026-10-01', 'verify': 'live page fetch',
    'items': [
        {'title': '새출발기금 지원체계를 보완하여 필요한 분들께 채무조정 혜택이 더욱 집중되도록 하겠습니다',
         'url': 'https://www.fsc.go.kr/no010101/87188', 'date': '2026-06-25',
         'note': '재산심사·감면기준 보완(가상자산·비상장주식, 최소감면율 60→30%, 사해행위 조사)'},
        {'title': '소상공인·자영업자의 채무상환에서 재기까지 새출발기금이 함께 합니다',
         'url': 'https://www.fsc.go.kr/no010101/86357', 'date': '2026-03-03',
         'note': '2026 추진사항(조기상환 인센티브, 상환유예 확대, 재기지원 연계)'},
        {'title': '새출발기금 감사원 지적사항에 대한 대응 방향',
         'url': 'https://www.fsc.go.kr/no010101/85859', 'date': '2025-12-16',
         'note': '도덕적해이 심사 강화, 가상자산 은닉 대응'},
    ],
}
HOPE = {
    'collector': 'opencode', 'collected_at': '2026-10-01', 'verify': 'live page fetch (bizinfo.go.kr mirror)',
    'dropped': [{'title': '2026년 희망리턴패키지 원스톱폐업지원 수정 공고(2026-04-17)',
                 'url': 'https://www.sbiz.or.kr/nhrp/pblanc/pblancView.do?cMenuNo=100102&directCall=Y&pMenuNo=100100&selPblancClCode=BCC008',
                 'reason': '원문 직접확인 실패(transport error) - unverified URL 적재 금지'}],
    'items': [
        {'title': '2026년 희망리턴패키지 재기사업화(재창업) 새출발기금 연계지원 소상공인 모집',
         'url': 'https://www.bizinfo.go.kr/sii/siia/selectSIIA200Detail.do?pblancId=PBLN_000000000117933',
         'date': '2026-01-28', 'note': '새출발기금 약정자 연계, 공공정보 해제 지원'},
        {'title': '2026년 희망리턴패키지 재기사업화(재창업) 소상공인 모집',
         'url': 'https://www.bizinfo.go.kr/sii/siia/selectSIIA200Detail.do?pblancId=PBLN_000000000117934',
         'date': '2026-01-28', 'note': '재창업진단·사업화자금 최대 2천만원'},
        {'title': '2026년 희망리턴패키지 재기사업화(경영개선) 위기 소상공인 진단·멘토링 지원 모집',
         'url': 'https://www.bizinfo.go.kr/sii/siia/selectSIIA200Detail.do?pblancId=PBLN_000000000121182',
         'date': '2026-04-23', 'note': '위기 소상공인 진단·멘토링, 예산 소진시까지'},
    ],
}

os.makedirs(os.path.join(HERE, '..', 'fsc'), exist_ok=True)
os.makedirs(os.path.join(HERE, '..', 'hope'), exist_ok=True)
with io.open(os.path.join(HERE, '..', 'fsc', 'fsc_connect_2026-10-01.json'), 'w', encoding='utf-8') as f:
    json.dump(FSC, f, ensure_ascii=False, indent=1)
with io.open(os.path.join(HERE, '..', 'hope', 'hope_connect_2026-10-01.json'), 'w', encoding='utf-8') as f:
    json.dump(HOPE, f, ensure_ascii=False, indent=1)
print('fsc:', len(FSC['items']), 'hope:', len(HOPE['items']), 'dropped:', len(HOPE['dropped']))
