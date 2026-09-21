import json
import re

def extract_author_followup_result(comments, author):
    for comment in comments:
        if comment.get("author", {}).get("nickname") == author:
            content = comment.get("content", "")
            result_keywords = ["나왔습니다", "나왔어", "확정", "체결", "받았습니다", "결과", "최종", "임시안", "프로 나왔", "나왔어유", "프로나왔"]
            if any(kw in content for kw in result_keywords):
                return content
        for reply in comment.get("replies", []):
            if reply.get("author", {}).get("nickname") == author:
                content = reply.get("content", "")
                result_keywords = ["나왔습니다", "나왔어", "확정", "체결", "받았습니다", "결과", "최종", "임시안", "프로 나왔", "나왔어유", "프로나왔"]
                if any(kw in content for kw in result_keywords):
                    return content
    return None

def extract_actual_rate_from_followup(followup_text):
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
        r'임시안\s*(?:오늘\s*)?(\d+)\s*나왔어유',
        r'임시안\s*(\d+)\s*나왔',
    ]
    for pattern in patterns:
        match = re.search(pattern, followup_text)
        if match:
            try:
                return f"{match.group(1)}%"
            except:
                pass
    return None

# Test with 5700
with open('C:/feel2real-buildit/data/newstart-cafe/gamian/articles/5700.json', 'r', encoding='utf-8') as f:
    a = json.load(f)

author = a.get('author', {}).get('nickname', '')
afr = extract_author_followup_result(a.get('comments', []), author)
print(f"Author: {author}")
print(f"AFR: {afr}")

if afr:
    arrf = extract_actual_rate_from_followup(afr)
    print(f"ARRF: {arrf}")

# Test 5727
with open('C:/feel2real-buildit/data/newstart-cafe/gamian/articles/5727.json', 'r', encoding='utf-8') as f:
    a = json.load(f)

author = a.get('author', {}).get('nickname', '')
afr = extract_author_followup_result(a.get('comments', []), author)
print(f"\nAuthor: {author}")
print(f"AFR: {afr}")

if afr:
    arrf = extract_actual_rate_from_followup(afr)
    print(f"ARRF: {arrf}")

# Test 5731
with open('C:/feel2real-buildit/data/newstart-cafe/gamian/articles/5731.json', 'r', encoding='utf-8') as f:
    a = json.load(f)

author = a.get('author', {}).get('nickname', '')
afr = extract_author_followup_result(a.get('comments', []), author)
print(f"\nAuthor: {author}")
print(f"AFR: {afr}")

if afr:
    arrf = extract_actual_rate_from_followup(afr)
    print(f"ARRF: {arrf}")