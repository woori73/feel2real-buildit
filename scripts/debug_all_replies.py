import json
import re

# Check 5700 in full detail
with open('C:/feel2real-buildit/data/newstart-cafe/gamian/articles/5700.json', 'r', encoding='utf-8') as f:
    a = json.load(f)

author = a.get('author', {}).get('nickname', '')
print(f"Article author: '{author}'")

for i, c in enumerate(a.get('comments', [])):
    c_author = c.get('author', {}).get('nickname', '')
    print(f"\nComment {i}: author='{c_author}' (match: {c_author == author})")
    print(f"  Content: {c.get('content', '')[:120]}")
    for j, r in enumerate(c.get('replies', [])):
        r_author = r.get('author', {}).get('nickname', '')
        r_content = r.get('content', '')
        print(f"  Reply {j}: author='{r_author}' (match: {r_author == author})")
        print(f"    Content: {r_content[:200]}")