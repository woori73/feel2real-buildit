import json

for aid in ["5731", "5700", "5727", "5672", "5758", "5904", "5695", "5690"]:
    with open(f'C:/feel2real-buildit/data/newstart-cafe/gamian/articles/{aid}.json', 'r', encoding='utf-8') as f:
        a = json.load(f)
    author = a.get('author', {}).get('nickname', '')
    print(f"\n=== {aid} ({author}) ===")
    for c in a.get('comments', []):
        for r in c.get('replies', []):
            if r.get('author', {}).get('nickname') == author:
                content = r.get('content', '')
                keywords = ["나왔", "확정", "체결", "임시안", "프로", "나왔어유", "프로나왔"]
                if any(kw in content for kw in keywords):
                    print(f"  RESULT REPLY: {content[:150]}")