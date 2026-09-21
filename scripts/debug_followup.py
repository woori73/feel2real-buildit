import json

# Check articles with known followup results
articles_to_check = ["5731", "5700", "5727", "5672"]

for aid in articles_to_check:
    with open(f'C:/feel2real-buildit/data/newstart-cafe/gamian/articles/{aid}.json', 'r', encoding='utf-8') as f:
        a = json.load(f)
    print(f"\n=== Article {aid} ===")
    for c in a.get('comments', []):
        author = c.get('author', {}).get('nickname', '')
        content = c.get('content', '')[:100]
        print(f"  {author}: {content}")
        for r in c.get('replies', []):
            rauthor = r.get('author', {}).get('nickname', '')
            rcontent = r.get('content', '')[:80]
            print(f"    REPLY {rauthor}: {rcontent}")