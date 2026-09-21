import json
import re

def extract_actual_rate_from_followup(followup_text: str):
    if not followup_text:
        return None
    
    patterns = [
        r'임시안\s*(?:에|에서)?\s*(\d+)\s*프로',
        r'임시안\s*(\d+)프로',
        r'임시안\s*(\d+)\s*%',
        r'(\d+)\s*프로\s*나왔',
        r'(\d+)%\s*나왔',
        r'확정\s*감면율\s*(\d+)',
        r'최종\s*감면율\s*(\d+)',
        r'감면율\s*(\d+)\s*확정',
    ]
    
    for pattern in patterns:
        match = re.search(pattern, followup_text)
        if match:
            try:
                return f"{match.group(1)}%"
            except:
                pass
    return None

# Test with known followup texts
test_texts = [
    "방장님 말씀해주신대로 정확히 임시안에75프로나왔습니다. 소름돋았습니다. 감사합니다.",
    "하디임시안 오늘 74나왔어유!!",
    "지피지기자산네감사합니다 잘 준비해봐야겠습니다 ^^",
    "감사드림니다 덕분에 고비를 또 넘겻네요",
    "감사합니다!! 임시안 나오게되면 글작성하겠습니다!!",
    "정말 감사드립니다\n혹시 담당자 배정 후에\n확정안 나오는 과정이나 1차 확정안 나온 후 재산가액이 공시지가나\n다른 방향으로 바뀔 가능성도 있을까요?",
]

for text in test_texts:
    result = extract_actual_rate_from_followup(text)
    print(f'Text: {text[:60]}... -> Rate: {result}')