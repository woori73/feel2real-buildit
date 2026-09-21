import json
import re

# Check 5700 content
with open('C:/feel2real-buildit/data/newstart-cafe/gamian/articles/5700.json', 'r', encoding='utf-8') as f:
    a = json.load(f)

content = a.get('content', '')
author = a.get('author', {}).get('nickname', '')

print(f"Author: '{author}'")
print(f"Content length: {len(content)}")
print(f"Author in content: {author in content}")

# Search for author followup pattern
lines = content.split('\n')
for i, line in enumerate(lines):
    if '임시안' in line and ('75' in line or '74' in line or '나왔' in line):
        print(f"Line {i}: {line[:200]}")
    if '이치깡' in line:
        print(f"Author line {i}: {line[:200]}")

# Check for author followup keywords
for i, line in enumerate(lines):
    check_text = '\n'.join(lines[i:min(i+5, len(lines))])
    if any(kw in check_text for kw in ["나왔습니다", "나왔어", "확정", "체결", "받았습니다", "결과", "최종", "임시안", "프로 나왔", "나왔어유", "프로나왔"]):
        if '이치깡' in check_text or '75' in check_text or '74' in check_text:
            print(f"Match at line {i}: {check_text[:300]}")