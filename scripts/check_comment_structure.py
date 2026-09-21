import json

with open('C:/feel2real-buildit/data/newstart-cafe/gamian/articles/5700.json', 'r', encoding='utf-8') as f:
    a = json.load(f)

print('Keys:', a.keys())
print('Comment count:', a.get('commentCount'))
print('Reply count:', a.get('replyCount'))
print('Num comments:', len(a.get('comments', [])))
for i, c in enumerate(a.get('comments', [])):
    print('Comment', i, 'keys:', c.keys())
    print('  Replies:', len(c.get('replies', [])))