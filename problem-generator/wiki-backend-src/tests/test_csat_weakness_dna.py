"""WIKI ④ 학생 약점 DNA — 오답 원문 × 평가원 출제 DNA 연결 계약 테스트.

given/when/then 구조. 커버 범위:
- 오답 원문 파싱(원문에 없는 학생 선택 답은 만들지 않음)
- 반복 규칙(서로 다른 시험 2개 이상 × 문항 2개 이상) → 약점 vs 관찰 분리
- 약점 카드의 근거 문항(시험·번호·정답·item_id)과 ③ 원문 연결
- 평가원 출제 DNA/CLAIM ↔ 학생 오답 유형 연결
- 분석 결과 저장(⑤용 구조)과 재분석 멱등성
- ADMIN 전용 쓰기(403), 오답 원문 없을 때 404
"""

import hashlib
import hmac
import os
import sys
import tempfile
import time
import unittest
from pathlib import Path
from fastapi.testclient import TestClient

BACKEND = Path(__file__).resolve().parents[1] / 'backend'
sys.path.insert(0, str(BACKEND))
tmp = tempfile.NamedTemporaryFile(suffix='.db', delete=False); tmp.close()
os.environ['BUILDIT_WIKI_DB_PATH'] = tmp.name
import main
import csat_weakness


def signed_headers(role, user_id, login_id):
    timestamp = str(int(time.time()))
    identity = main.wiki_workspace.WikiIdentity(user_id, login_id, role)
    payload = main.wiki_workspace.signature_payload(identity, timestamp)
    signature = hmac.new(os.environ['WIKI_IDENTITY_SECRET'].encode(), payload, hashlib.sha256).hexdigest()
    return {'x-buildit-user-id': user_id, 'x-buildit-login-id': login_id, 'x-buildit-role': role,
            'x-buildit-timestamp': timestamp, 'x-buildit-signature': signature}


ADMIN = signed_headers('ADMIN', '1', 'tester')


def wrong_article(item_id, source, number, type_label, answer, body='지문과 발문이 여기에 있습니다.'):
    return (f'<article class="wrong-answer" id="{item_id}" data-student="김테스트" data-source="{source}" '
            f'data-number="{number}" data-type="{type_label}" data-answer="{answer}">'
            f'<h2>{source} · {number}번</h2><pre>{body}</pre></article>')


# given: 글의 순서 2건(서로 다른 시험) + 어법 1건(단발) + 제목 2건(같은 시험)
WRONG_HTML = ('<!doctype html><html lang="ko"><body><section id="previous"><h1>기존 추출분</h1>'
              + wrong_article('item-1', '모의A', '36', '글의 순서', '3')
              + wrong_article('item-2', '모의B', '37', '글의 순서', '5')
              + wrong_article('item-3', '모의A', '29', '어법', '2')
              + wrong_article('item-4', '모의A', '24', '제목', '1')
              + wrong_article('item-5', '모의A', '23', '제목', '4')
              + '</section></body></html>').encode('utf-8')


class CsatWeaknessDnaTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        os.environ['BUILDIT_WIKI_DB_PATH'] = tmp.name
        main.DB_PATH = Path(tmp.name)
        main.init_db()
        main.migrate_schema()
        cls.client = TestClient(main.app)

    def setUp(self):
        with main.get_db() as conn:
            for table in ('csat_weakness_dna', 'csat_weakness_analysis', 'csat_question_dna',
                          'csat_dna_claims', 'csat_dna_comparisons', 'csat_exam_resources',
                          'csat_wrong_html', 'csat_questions', 'csat_question_sources',
                          'csat_exams', 'students'):
                conn.execute(f'DELETE FROM {table}')
        student = self.client.post('/api/students', json={'name': '김테스트'})
        self.assertIn(student.status_code, (200, 201))
        self.student_id = student.json()['id']

    def upload_wrong_html(self, data=WRONG_HTML):
        response = self.client.post('/api/csat/wrong-html', data={'student_id': str(self.student_id)},
                                    files={'file': ('wrong.html', data, 'text/html')}, headers=ADMIN)
        self.assertEqual(201, response.status_code, response.text)
        return response.json()

    def seed_exam_dna(self):
        """given: 평가원 6월 모평(36번 글의 순서)에 COHESION DNA와 같은 태그의 CLAIM을 등록."""
        exam = self.client.post('/api/csat/exams', json={
            'exam_year': 2027, 'exam_type': 'JUNE_MOCK', 'exam_date': '2026-06-04',
            'label': '2027학년도 6월 모의평가 영어'}, headers=ADMIN)
        self.assertEqual(201, exam.status_code, exam.text)
        exam_id = exam.json()['id']
        questions = self.client.post(f'/api/csat/exams/{exam_id}/questions', json={'questions': [
            {'question_no': 36, 'prompt': '제시문의 순서로 가장 적절한 것은?', 'passage': 'Passage',
             'choices': ['1', '2', '3', '4', '5'], 'answer': '3', 'solution': '해설'}]}, headers=ADMIN)
        self.assertEqual(201, questions.status_code, questions.text)
        question_id = questions.json()['question_ids'][0]
        dna = self.client.post(f'/api/csat/questions/{question_id}/dna', json={
            'entry_type': 'DNA', 'tag': 'COHESION', 'note': '제시문 연결: 재진술 어휘 짝짓기',
            'evidence': [{'question_no': 36, 'exam_id': exam_id}], 'provenance': 'ACTUAL_EXAM'}, headers=ADMIN)
        self.assertEqual(201, dna.status_code, dna.text)
        claim = self.client.post(f'/api/csat/exams/{exam_id}/dna-claims', json={
            'claim_type': 'REPEATED_SKILL', 'tag': 'COHESION',
            'claim_text': '36·37번 제시문 배열은 재진술 어휘 짝짓기 요구',
            'evidence': [{'question_no': 36, 'exam_id': exam_id}], 'provenance': 'ACTUAL_EXAM'}, headers=ADMIN)
        self.assertEqual(201, claim.status_code, claim.text)
        return exam_id, question_id

    # ── 원문 파싱 ──────────────────────────────────────────────────────────────

    def test_parse_reads_items_without_inventing_student_answer(self):
        # given: ADMIN이 등록한 오답 원문이 있을 때 (원문에는 학생 선택 답이 없다)
        # when: 원문을 파싱하면
        items = csat_weakness.parse_wrong_items(WRONG_HTML)
        # then: 5개 문항이 원문 순서·시험·번호·정답 그대로 읽히고 학생 선택 답은 None이다
        self.assertEqual(5, len(items))
        self.assertEqual(['item-1', 'item-2', 'item-3', 'item-4', 'item-5'], [item['item_id'] for item in items])
        first = items[0]
        self.assertEqual('모의A', first['source'])
        self.assertEqual('36', first['number'])
        self.assertEqual('글의 순서', first['type_label'])
        self.assertEqual('3', first['answer'])
        self.assertIsNone(first['student_answer'])
        self.assertTrue(first['excerpt'])

    # ── 약점 vs 관찰 분리 ──────────────────────────────────────────────────────

    def test_analyze_separates_repeated_dna_from_observations(self):
        # given: 오답 원문이 등록되어 있고
        self.upload_wrong_html()
        # when: ④ 약점 DNA 분석을 실행하면
        response = self.client.post(f'/api/csat/students/{self.student_id}/weakness-dna/analyze', headers=ADMIN)
        # then: 2개 시험에서 반복된 '글의 순서'만 약점으로 확정된다
        self.assertEqual(201, response.status_code, response.text)
        body = response.json()
        self.assertEqual(5, body['analysis']['wrong_total'])
        self.assertEqual(1, body['analysis']['dna_count'])
        card = body['dna'][0]
        self.assertEqual('order', card['key'])
        self.assertEqual('논리', card['category'])
        self.assertEqual(2, card['wrong_count'])
        self.assertEqual(['모의A', '모의B'], card['sources'])
        # then: 단발(어법)과 같은 시험 2건(제목)은 약점이 아니라 관찰로 남는다
        self.assertEqual(2, body['analysis']['observation_count'])
        observed = {row['key']: row for row in body['analysis']['observations']}
        self.assertEqual(1, observed['grammar']['wrong_count'])
        self.assertEqual(1, observed['grammar']['source_count'])
        self.assertEqual(2, observed['title']['wrong_count'])
        self.assertEqual(1, observed['title']['source_count'])

    def test_card_evidence_links_back_to_raw_wrong_html(self):
        # given: 분석이 완료된 상태에서
        wrong = self.upload_wrong_html()
        body = self.client.post(f'/api/csat/students/{self.student_id}/weakness-dna/analyze',
                                headers=ADMIN).json()
        # then: 약점 카드의 근거 문항은 원문 item_id·시험·번호·학생답/정답을 그대로 실는다
        evidence = body['dna'][0]['evidence']
        self.assertEqual(2, len(evidence))
        self.assertEqual({'item-1', 'item-2'}, {row['item_id'] for row in evidence})
        for row in evidence:
            self.assertIn(row['source'], ('모의A', '모의B'))
            self.assertIn(row['number'], ('36', '37'))
            self.assertIsNone(row['student_answer'])  # 원문 미표기 — 추정하지 않는다
            self.assertTrue(row['answer_known'])
        self.assertEqual(wrong['id'], body['analysis']['wrong_html_id'])
        # then: ③의 원문 보기 API로 같은 자료에 연결된다
        raw = self.client.get(f'/api/csat/wrong-html/{wrong["id"]}/raw')
        self.assertEqual(200, raw.status_code)
        self.assertIn('item-1', raw.text)

    # ── 평가원 출제 DNA 연결 ──────────────────────────────────────────────────

    def test_card_links_exam_dna_and_claim_by_type(self):
        # given: 오답 원문과 평가원 6월 모평 COHESION DNA·CLAIM이 등록되어 있고
        self.upload_wrong_html()
        exam_id, question_id = self.seed_exam_dna()
        # when: 분석하면
        body = self.client.post(f'/api/csat/students/{self.student_id}/weakness-dna/analyze',
                                headers=ADMIN).json()
        # then: 약점 카드에 같은 유형(글의 순서)의 평가원 DNA와 CLAIM이 연결된다
        card = body['dna'][0]
        self.assertEqual(1, len(card['exam_dna']))
        entry = card['exam_dna'][0]
        self.assertEqual('COHESION', entry['tag'])
        self.assertEqual(36, entry['question_no'])
        self.assertEqual('ACTUAL_EXAM', entry['provenance'])
        self.assertEqual([{'question_no': 36, 'exam_id': exam_id}], entry['evidence'])
        self.assertIn('2027학년도 6월', entry['exam_label'])
        self.assertEqual([{'entry_type': 'DNA', 'tag': 'COHESION', 'count': 1}], card['exam_dna_tags'])
        self.assertEqual(1, len(card['claims']))
        self.assertEqual('REPEATED_SKILL', card['claims'][0]['claim_type'])
        # then: 관찰(어법·제목)에는 약점 카드용 평가원 DNA 연결이 붙지 않는다
        observed = {row['key']: row for row in body['analysis']['observations']}
        self.assertNotIn('exam_dna', observed['grammar'])
        self.assertEqual(1, len(observed['grammar']['evidence']))

    # ── 저장(⑤용 구조)과 멱등성 ──────────────────────────────────────────────

    def test_analysis_is_persisted_and_reanalysis_replaces_rows(self):
        # given: 분석이 한 번 실행된 상태에서
        self.upload_wrong_html()
        self.seed_exam_dna()
        first = self.client.post(f'/api/csat/students/{self.student_id}/weakness-dna/analyze',
                                 headers=ADMIN).json()
        # when: 저장된 결과를 GET하고 재분석하면
        stored = self.client.get(f'/api/csat/students/{self.student_id}/weakness-dna').json()
        second = self.client.post(f'/api/csat/students/{self.student_id}/weakness-dna/analyze',
                                  headers=ADMIN).json()
        with main.get_db() as conn:
            rows = conn.execute('SELECT COUNT(*) c FROM csat_weakness_dna WHERE student_id=?',
                                (self.student_id,)).fetchone()['c']
        # then: GET은 같은 구조를 반환하고 재분석은 행을 중복 생성하지 않는다
        self.assertEqual(first['analysis']['wrong_total'], stored['analysis']['wrong_total'])
        self.assertEqual(1, stored['analysis']['dna_count'])
        self.assertEqual(1, len(stored['dna']))
        self.assertEqual(first['dna'][0]['key'], second['dna'][0]['key'])
        self.assertEqual(1, rows)
        # then: ⑤가 소비할 처방 구조(유형별 행동 단위)가 저장된다
        self.assertTrue(stored['analysis']['prescription'])
        prescription = stored['analysis']['prescription'][0]
        self.assertEqual('order', prescription['key'])
        self.assertGreaterEqual(len(prescription['steps']), 3)

    def test_get_without_analysis_returns_null_not_placeholder(self):
        # given: 분석 전 학생이 있을 때
        # when: ④ 데이터를 읽으면
        body = self.client.get(f'/api/csat/students/{self.student_id}/weakness-dna').json()
        # then: 가짜 데이터 없이 analysis=null, dna=[]이다
        self.assertIsNone(body['analysis'])
        self.assertEqual([], body['dna'])

    # ── 권한/입력 가드 ────────────────────────────────────────────────────────

    def test_analyze_requires_admin(self):
        # given: ADMIN이 아닌(TUTOR) 서명 identity 요청일 때
        self.upload_wrong_html()
        # when: 분석을 실행하면 → then: 403으로 거부된다
        response = self.client.post(f'/api/csat/students/{self.student_id}/weakness-dna/analyze',
                                    headers=signed_headers('TUTOR', '9', 'tutor-local'))
        self.assertEqual(403, response.status_code)

    def test_analyze_without_wrong_html_is_404(self):
        # given: 오답 원문이 아직 등록되지 않은 학생일 때
        # when: 분석을 실행하면 → then: 원문부터 등록하라는 404다
        response = self.client.post(f'/api/csat/students/{self.student_id}/weakness-dna/analyze', headers=ADMIN)
        self.assertEqual(404, response.status_code)


if __name__ == '__main__':
    unittest.main()
