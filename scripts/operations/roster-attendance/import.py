"""운영 출결부: 학생명단(시간표) 진행표 → 출결부 가져오기 (미리보기 / 승인 후 적용).

실명·연락처는 출력하지 않는다. 건수·연도·반 번호만 출력한다.
- 이름 처리 규칙(자동): 같은 이름 학생 1명 = 그 학생에 연결, 없음 = 새로 등록, 여러 명 = 사람이 골라야 하므로 적용 중단.
- 적용은 APPLY=true 이고 CONFIRM == IMPORT-ROSTER-<연도> 일 때만 한다.
환경변수: BASE_URL, COOKIE_FILE(f2r-auth.json), YEAR, CLASS_NAME, APPLY, CONFIRM
"""
import collections
import json
import os
import sys
import urllib.error
import urllib.request


class Stop(Exception):
    pass


def auto_mappings(names):
    """미리보기 이름 목록 → 적용 요청 mappings. 여러 후보(CHOOSE)가 있으면 Stop."""
    choose = [n for n in names if n.get('suggestedAction') not in ('MATCH', 'CREATE')]
    if choose:
        raise Stop('같은 이름 학생이 여러 명인 이름 %d개는 사람이 골라야 합니다. 화면에서 진행해 주세요.' % len(choose))
    return [{'name': n['name'], 'action': n['suggestedAction'],
             'studentId': n.get('suggestedStudentId') if n['suggestedAction'] == 'MATCH' else None} for n in names]


def summarize_preview(p):
    actions = collections.Counter(n.get('suggestedAction') for n in p.get('names', []))
    return {'year': p.get('year'), 'names': len(p.get('names', [])), 'lessonDays': p.get('lessonDays'),
            'futureLessonDaysExcluded': p.get('futureLessonDays'), 'suggested': dict(sorted(actions.items()))}


def pick_business(businesses):
    for b in businesses:
        if b.get('code') == 'ACADEMY':
            return b['businessId']
    if not businesses:
        raise Stop('사업체가 없습니다.')
    return businesses[0]['businessId']


class Client:
    def __init__(self, base, cookie):
        self.base, self.cookie = base, cookie

    def call(self, method, path, body=None):
        data = None if body is None else json.dumps(body).encode('utf-8')
        req = urllib.request.Request(self.base + path, data=data, method=method, headers={
            'Cookie': self.cookie, 'Origin': self.base, 'Content-Type': 'application/json', 'Accept': 'application/json'})
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                text = r.read().decode('utf-8')
                return json.loads(text) if text else None
        except urllib.error.HTTPError as e:
            # 응답 본문은 안내 문장만 담는다(실명 없음). 길이를 줄여 출력한다.
            raise Stop('%s %s → HTTP %d %s' % (method, path.split('?')[0], e.code, e.read().decode('utf-8', 'replace')[:200]))


def run(client, year, class_name, apply, confirm):
    biz = pick_business(client.call('GET', '/api/admin/easy/businesses'))
    years = sorted(e['year'] for e in client.call('GET', '/api/admin/student-roster/entries'))
    print('archive_years', years)
    if year not in years:
        raise Stop('학생명단 자료실에 %d년 자료가 없습니다. 먼저 화면에서 올려 주세요.' % year)
    preview = client.call('GET', '/api/admin/easy/attendance/roster-import/preview?businessId=%d&year=%d' % (biz, year))
    print('preview', json.dumps(summarize_preview(preview), ensure_ascii=False))
    mappings = auto_mappings(preview['names'])
    if not apply:
        print('mode=preview (저장 안 함)')
        return
    if confirm != 'IMPORT-ROSTER-%d' % year:
        raise Stop('확인 문자열이 맞지 않습니다(IMPORT-ROSTER-%d).' % year)
    classes = client.call('GET', '/api/admin/easy/attendance/classes?businessId=%d' % biz)
    found = [c for c in classes if c['name'] == class_name]
    class_id = found[0]['classId'] if found else client.call(
        'POST', '/api/admin/easy/attendance/classes?businessId=%d' % biz, {'name': class_name})['classId']
    print('class', 'reused' if found else 'created', 'classId=%d' % class_id)
    result = client.call('POST', '/api/admin/easy/attendance/roster-import/apply', {
        'businessId': biz, 'year': year, 'previewHash': preview['previewHash'], 'classId': class_id, 'mappings': mappings})
    print('apply', json.dumps(result, ensure_ascii=False))
    months = {}
    for m in range(1, 13):
        data = client.call('GET', '/api/admin/easy/attendance/monthly?businessId=%d&classId=%d&month=%d-%02d' % (biz, class_id, year, m))
        months['%02d' % m] = sum(sum(s['counts'].values()) for s in data['students'])
    print('monthly_events', json.dumps(months))


def main():
    cookie = json.load(open(os.environ['COOKIE_FILE']))['adminCookie']
    try:
        run(Client(os.environ['BASE_URL'], cookie), int(os.environ['YEAR']), os.environ['CLASS_NAME'].strip(),
            os.environ.get('APPLY') == 'true', os.environ.get('CONFIRM', ''))
    except Stop as e:
        print('::error::' + str(e))
        sys.exit(1)


if __name__ == '__main__':
    main()
