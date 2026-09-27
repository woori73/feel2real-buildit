"""수능영어 Wiki DAY 1 — 평가원 기출 등록 + 출제 DNA API 테스트.

given/when/then 구조. 커버 범위 (§22): exam 등록, 문항 등록/정답 연결, DNA CRUD,
DNA evidence 연결, 6월/9월 분리, 교차 비교 계약, 관리자 수정(AI_PROPOSED→TEACHER_CONFIRMED),
학생 오답 향후 연결(DAY 2) 구조, ADMIN 전용 쓰기(403).
"""

import hashlib
import hmac
import json
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


def signed_headers(role, user_id, login_id):
    timestamp = str(int(time.time()))
    identity = main.wiki_workspace.WikiIdentity(user_id, login_id, role)
    payload = main.wiki_workspace.signature_payload(identity, timestamp)
    signature = hmac.new(os.environ['WIKI_IDENTITY_SECRET'].encode(), payload, hashlib.sha256).hexdigest()
    return {'x-buildit-user-id': user_id, 'x-buildit-login-id': login_id, 'x-buildit-role': role,
            'x-buildit-timestamp': timestamp, 'x-buildit-signature': signature}


def sample_questions(*numbers):
    return [{'question_no': n, 'prompt': f'{n}번 문제', 'passage': f'Passage {n}',
             'choices': ['1', '2', '3', '4', '5'], 'answer': str((n % 5) + 1), 'solution': f'{n}번 해설'}
            for n in numbers]


class CsatEnglishDnaTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        os.environ['BUILDIT_WIKI_DB_PATH'] = tmp.name
        main.DB_PATH = Path(tmp.name)
        main.init_db()
        main.migrate_schema()
        cls.client = TestClient(main.app)

    def setUp(self):
        # 이 모듈 전용 DB로 격리: 다른 테스트 모듈과 순서 무관하게 동작해야 한다.
        with main.get_db() as conn:
            for table in ('csat_question_dna', 'csat_dna_claims', 'csat_dna_comparisons',
                          'csat_exam_resources', 'csat_wrong_answers', 'csat_questions',
                          'csat_question_sources', 'csat_exams'):
                conn.execute(f'DELETE FROM {table}')
        june = self.client.post('/api/csat/exams', json={
            'exam_year': 2027, 'exam_type': 'JUNE_MOCK', 'exam_date': '2026-06-04', 'label': '2027학년도 6월 모의평가'})
        self.assertEqual(201, june.status_code)
        self.june_id = june.json()['id']
        sept = self.client.post('/api/csat/exams', json={
            'exam_year': 2027, 'exam_type': 'SEPTEMBER_MOCK', 'exam_date': '2026-09-02', 'label': '2027학년도 9월 모의평가'})
        self.assertEqual(201, sept.status_code)
        self.sept_id = sept.json()['id']

    def import_questions(self, exam_id, *numbers):
        response = self.client.post(f'/api/csat/exams/{exam_id}/questions',
                                    json={'questions': sample_questions(*numbers), 'source_filename': 'fixture.pdf'})
        self.assertEqual(201, response.status_code, response.text)
        return response.json()

    # ── exam 등록 ──────────────────────────────────────────────────────────────

    def test_exam_registration_roundtrip(self):
        # given: 6월/9월 모평이 setUp에서 등록되어 있을 때
        # when: 시험 목록/상세를 조회하면
        listed = self.client.get('/api/csat/exams').json()
        detail = self.client.get(f'/api/csat/exams/{self.june_id}').json()
        # then: 학년도/유형/시행일이 그대로 읽고 집계 카운트가 0으로 시작한다
        self.assertEqual({2027}, {e['exam_year'] for e in listed})
        self.assertEqual({'JUNE_MOCK', 'SEPTEMBER_MOCK'}, {e['exam_type'] for e in listed})
        self.assertEqual('2026-06-04', detail['exam_date'])
        self.assertEqual(0, detail['question_count'])
        self.assertEqual(0, detail['dna_count'])

    def test_duplicate_exam_registration_is_rejected(self):
        # given: 2027 JUNE_MOCK이 이미 있고
        # when: 같은 연도+유형+과목을 다시 등록하면
        response = self.client.post('/api/csat/exams', json={'exam_year': 2027, 'exam_type': 'JUNE_MOCK'})
        # then: 409로 거부된다
        self.assertEqual(409, response.status_code)

    def test_exam_registration_validates_type_and_year(self):
        self.assertEqual(400, self.client.post('/api/csat/exams', json={'exam_year': 2027, 'exam_type': 'MOCK'}).status_code)
        self.assertEqual(400, self.client.post('/api/csat/exams', json={'exam_year': 1999, 'exam_type': 'CSAT'}).status_code)

    def test_taxonomy_exposes_dna_vocabulary(self):
        # given/when: 분류 체계 엔드포인트를 조회하면
        taxonomy = self.client.get('/api/csat/dna/taxonomy').json()
        # then: 16개 DNA 차원·함정 태그·provenance·판정 열거가 UI에 노출된다
        self.assertEqual(16, len(taxonomy['dimensions']))
        self.assertIn('WORD_ECHO', taxonomy['traps'])
        self.assertIn('ACTUAL_EXAM', taxonomy['provenance'])
        self.assertIn('STRENGTHENED', taxonomy['verdicts'])
        self.assertEqual({'ANSWER', 'ANALYSIS', 'QUESTION_PAPER', 'TEACHER_NOTE'}, set(taxonomy['resource_types']))

    # ── 원본 자료(SOURCE) 업로드 ───────────────────────────────────────────────

    def test_source_resource_upload_and_original_roundtrip(self):
        # given: 6월 모평 문제지 PDF 원본 바이트가 있고
        blob = b'%PDF-1.4 fake june question paper bytes'
        upload = self.client.post(
            f'/api/csat/exams/{self.june_id}/resources',
            data={'resource_type': 'QUESTION_PAPER'},
            files={'file': ('2027_6월_문제지.pdf', blob, 'application/pdf')})
        self.assertEqual(201, upload.status_code)
        resource_id = upload.json()['id']
        # when: 원본 열기로 다운로드하고 시험 상세를 보면
        fetched = self.client.get(f'/api/csat/exam-resources/{resource_id}/original')
        detail = self.client.get(f'/api/csat/exams/{self.june_id}').json()
        # then:SOURCE 원본 바이트가 무손실 반환되고资料 목록에 연결된다
        self.assertEqual(200, fetched.status_code)
        self.assertEqual(blob, fetched.content)
        self.assertIn('inline', fetched.headers['content-disposition'])
        self.assertEqual(1, detail['resource_count'])
        self.assertEqual('QUESTION_PAPER', detail['resources'][0]['resource_type'])
        # and: 삭제하면 사라진다
        self.assertEqual(200, self.client.delete(f'/api/csat/exam-resources/{resource_id}').status_code)
        self.assertEqual(0, self.client.get(f'/api/csat/exams/{self.june_id}').json()['resource_count'])

    def test_source_resource_rejects_unknown_type_and_empty_file(self):
        self.assertEqual(400, self.client.post(
            f'/api/csat/exams/{self.june_id}/resources', data={'resource_type': 'RUMOR'},
            files={'file': ('x.txt', b'data', 'text/plain')}).status_code)
        self.assertEqual(400, self.client.post(
            f'/api/csat/exams/{self.june_id}/resources', data={'resource_type': 'ANSWER'},
            files={'file': ('empty.pdf', b'', 'application/pdf')}).status_code)

    # ── 문항 등록 + 정답 연결 ──────────────────────────────────────────────────

    def test_question_registration_maps_answers_and_detects_types(self):
        # given: 6월 모평에 20~24, 29번 문제 등록 요청이 있고
        result = self.import_questions(self.june_id, 20, 21, 22, 23, 24, 29)
        # when: 시험 문항 목록을 조회하면
        rows = self.client.get(f'/api/csat/exams/{self.june_id}/questions').json()
        # then: 문항번호순 정렬, 정답 연결, 표준 유형 자동 매핑(21→implication, 29→grammar)
        self.assertEqual([20, 21, 22, 23, 24, 29], [r['question_no'] for r in rows])
        by_no = {r['question_no']: r for r in rows}
        self.assertEqual('1', by_no[20]['answer'])
        self.assertEqual('implication', by_no[21]['csat_type_id'])
        self.assertEqual('grammar', by_no[29]['csat_type_id'])
        # and: 등록 응답은 exam/source/question id를 모두 반환한다
        self.assertEqual(self.june_id, result['exam_id'])
        self.assertEqual(6, len(result['question_ids']))

    def test_question_registration_is_isolated_per_exam(self):
        # given: 6월에는 20~24, 9월에는 29~32가 각각 등록되어 있을 때
        self.import_questions(self.june_id, 20, 21, 22, 23, 24)
        self.import_questions(self.sept_id, 29, 30, 31, 32)
        # when: 각 시험 문항을 조회하면
        june = self.client.get(f'/api/csat/exams/{self.june_id}/questions').json()
        sept = self.client.get(f'/api/csat/exams/{self.sept_id}/questions').json()
        # then: 6월/9월 데이터가 섞이지 않는다
        self.assertEqual([20, 21, 22, 23, 24], [r['question_no'] for r in june])
        self.assertEqual([29, 30, 31, 32], [r['question_no'] for r in sept])

    def test_question_registration_rejects_duplicate_payload_and_reimport(self):
        # given: 6월에 20번이 등록되어 있고
        self.import_questions(self.june_id, 20)
        # when/then: 요청 내부 중복 번호는 400, 이미 문항 있는 회차의 재등록은 409
        self.assertEqual(400, self.client.post(f'/api/csat/exams/{self.june_id}/questions',
                     json={'questions': sample_questions(21, 21)}).status_code)
        self.assertEqual(409, self.client.post(f'/api/csat/exams/{self.june_id}/questions',
                     json={'questions': sample_questions(30)}).status_code)

    def test_exam_question_range_filter(self):
        # given: 20~24, 29~33 등록 후
        self.import_questions(self.june_id, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33)
        # when: 분석 범위(from_no/to_no)로 조회하면
        rows = self.client.get(f'/api/csat/exams/{self.june_id}/questions?from_no=29&to_no=32').json()
        # then: 해당 구간만 반환
        self.assertEqual([29, 30, 31, 32], [r['question_no'] for r in rows])

    # ── DNA CRUD ──────────────────────────────────────────────────────────────

    def seed_dna_question(self):
        self.import_questions(self.june_id, 22, 31)
        rows = self.client.get(f'/api/csat/exams/{self.june_id}/questions').json()
        return {r['question_no']: r['id'] for r in rows}

    def create_dna(self, question_id, payload):
        response = self.client.post(f'/api/csat/questions/{question_id}/dna', json=payload)
        self.assertEqual(201, response.status_code, response.text)
        return response.json()

    def test_dna_multitag_creation_and_evidence_link(self):
        # given: 6월 31번 문항이 있고
        ids = self.seed_dna_question()
        # when: 실제 문항 근거와 함께 DNA 태그 2개를 붙이면
        evidence = [{'exam_id': self.june_id, 'question_no': 31}]
        vocab = self.create_dna(ids[31], {'entry_type': 'DNA', 'tag': 'VOCABULARY', 'note': '빈칸 어휘 수준 높음', 'evidence': evidence})
        logic = self.create_dna(ids[31], {'entry_type': 'DNA', 'tag': 'LOGIC', 'importance': 3, 'evidence': evidence})
        # then: 한 문항에 다중 태그, AI_PROPOSED 상태, 근거가 JSON으로 저장된다
        self.assertEqual('AI_PROPOSED', vocab['status'])
        self.assertEqual('DNA', vocab['entry_type'])
        self.assertEqual(evidence, logic['evidence'])
        listed = self.client.get(f"/api/csat/questions/{ids[31]}/dna").json()
        self.assertEqual({'LOGIC', 'VOCABULARY'}, {r['tag'] for r in listed})

    def test_trap_requires_choice_no_and_valid_tag(self):
        ids = self.seed_dna_question()
        # given/when/then: 함정은 오답 선지 번호 필수, 함정 태그 집합 검증
        self.assertEqual(400, self.client.post(f'/api/csat/questions/{ids[22]}/dna',
                     json={'entry_type': 'TRAP', 'tag': 'WORD_ECHO'}).status_code)
        self.assertEqual(400, self.client.post(f'/api/csat/questions/{ids[22]}/dna',
                     json={'entry_type': 'TRAP', 'tag': 'VOCABULARY', 'choice_no': 3}).status_code)
        trap = self.create_dna(ids[22], {'entry_type': 'TRAP', 'tag': 'WORD_ECHO', 'choice_no': 4,
                                         'note': '지선 핵심어 그대로 재등장 함정',
                                         'evidence': [{'exam_id': self.june_id, 'question_no': 22}]})
        self.assertEqual(4, trap['choice_no'])

    def test_dna_rejects_unknown_provenance_and_duplicate_tag(self):
        ids = self.seed_dna_question()
        self.assertEqual(400, self.client.post(f'/api/csat/questions/{ids[22]}/dna',
                     json={'entry_type': 'DNA', 'tag': 'DETAIL', 'provenance': 'BLOG'}).status_code)
        payload = {'entry_type': 'DNA', 'tag': 'DETAIL', 'evidence': [{'exam_id': self.june_id, 'question_no': 22}]}
        self.create_dna(ids[22], payload)
        self.assertEqual(409, self.client.post(f'/api/csat/questions/{ids[22]}/dna', json=payload).status_code)
        # 다른 문항의 같은 태그는 중복이 아니다
        self.assertEqual(201, self.client.post(f'/api/csat/questions/{ids[31]}/dna',
                     json={'entry_type': 'DNA', 'tag': 'DETAIL',
                           'evidence': [{'exam_id': self.june_id, 'question_no': 31}]}).status_code)

    def test_dna_actualexam_requires_evidence_and_expert_analysis_requires_expert(self):
        ids = self.seed_dna_question()
        # given/when/then: LEVEL 1(실제 문항)은 evidence 필수,
        # LEVEL 2(강사 분석)은 expert 기록 필수 — 출처 없는 단정 방지 계약
        self.assertEqual(400, self.client.post(f'/api/csat/questions/{ids[22]}/dna',
                     json={'entry_type': 'DNA', 'tag': 'MAIN_IDEA', 'provenance': 'ACTUAL_EXAM'}).status_code)
        self.assertEqual(400, self.client.post(f'/api/csat/questions/{ids[22]}/dna',
                     json={'entry_type': 'DNA', 'tag': 'MAIN_IDEA', 'provenance': 'EXPERT_ANALYSIS'}).status_code)
        expert = self.create_dna(ids[22], {'entry_type': 'DNA', 'tag': 'INFERENCE', 'provenance': 'EXPERT_ANALYSIS',
                                           'expert': '조정식', 'note': '추론 요구 문항 비중 증가 분석'})
        self.assertEqual('조정식', expert['expert'])
        # evidence 항목은 question_no와 exam 특정(exam_id 또는 exam_year+exam_type)이 모두 필요하다
        self.assertEqual(400, self.client.post(f'/api/csat/questions/{ids[22]}/dna',
                     json={'entry_type': 'DNA', 'tag': 'SYNTAX', 'provenance': 'ACTUAL_EXAM',
                           'evidence': [{'question_no': 29}]}).status_code)
        self.assertEqual(400, self.client.post(f'/api/csat/questions/{ids[22]}/dna',
                     json={'entry_type': 'DNA', 'tag': 'SYNTAX', 'provenance': 'ACTUAL_EXAM',
                           'evidence': [{'exam_id': self.june_id, 'question_no': 999}]}).status_code)

    def test_teacher_correction_flow_ai_proposed_to_confirmed(self):
        # given: AI가 제안한 DNA가 있고
        ids = self.seed_dna_question()
        dna = self.create_dna(ids[31], {'entry_type': 'TRAP', 'tag': 'SCOPE_EXPANSION', 'choice_no': 2,
                                        'note': '범위 확대 함정', 'evidence': [{'exam_id': self.june_id, 'question_no': 31}]})
        # when: 교수가 태그·설명·중요도를 수정하고 TEACHER_CONFIRMED로 승인하면
        corrected = self.client.put(f"/api/csat/dna/{dna['id']}", json={
            'tag': 'PARTIAL_TRUE', 'note': '일부 참 선지 함정으로 정정', 'importance': 3, 'status': 'TEACHER_CONFIRMED'}).json()
        # then: 상태/필드가 갱신되고 수정자가 기록된다
        self.assertEqual('TEACHER_CONFIRMED', corrected['status'])
        self.assertEqual('PARTIAL_TRUE', corrected['tag'])
        self.assertEqual(3, corrected['importance'])
        self.assertEqual('admin', corrected['updated_by'])
        # and: DNA 태그 체계(TRAP→DNA)를 위반하는 수정은 거부된다
        self.assertEqual(400, self.client.put(f"/api/csat/dna/{dna['id']}", json={'tag': 'MAIN_IDEA'}).status_code)
        self.assertEqual(400, self.client.put(f"/api/csat/dna/{dna['id']}", json={'status': 'PUBLISHED'}).status_code)

    def test_rejected_dna_hidden_from_question_and_exam_views(self):
        # given: DNA 2건 중 1건이 거절 처리되고
        ids = self.seed_dna_question()
        keep = self.create_dna(ids[22], {'entry_type': 'DNA', 'tag': 'COHESION', 'evidence': [{'exam_id': self.june_id, 'question_no': 22}]})
        drop = self.create_dna(ids[22], {'entry_type': 'DNA', 'tag': 'REFERENCE', 'evidence': [{'exam_id': self.june_id, 'question_no': 22}]})
        self.client.put(f"/api/csat/dna/{drop['id']}", json={'status': 'REJECTED'})
        # when: 문항 목록과 시험 DNA 집계를 조회하면
        row = self.client.get(f'/api/csat/exams/{self.june_id}/questions').json()[0]
        exam_dna = self.client.get(f'/api/csat/exams/{self.june_id}/dna').json()
        # then: 승인 대기/확정 태그만 노출되고 거절은 제외된다
        self.assertEqual(['COHESION'], [e['tag'] for e in row['dna']])
        self.assertEqual(['COHESION'], [t['tag'] for t in exam_dna['tags']])
        self.assertEqual([22], exam_dna['tags'][0]['question_nos'])
        # and: DELETE로 완전 삭제 가능
        self.assertEqual(200, self.client.delete(f"/api/csat/dna/{keep['id']}").status_code)
        self.assertEqual([], self.client.get(f"/api/csat/questions/{ids[22]}/dna").json())

    def test_exam_dna_aggregation_groups_tags_and_traps(self):
        # given: 6월 22·31번에 추론 DNA, 22·31번에 워드에코 함정이 있고
        ids = self.seed_dna_question()
        evidence_22 = [{'exam_id': self.june_id, 'question_no': 22}]
        evidence_31 = [{'exam_id': self.june_id, 'question_no': 31}]
        self.create_dna(ids[22], {'entry_type': 'DNA', 'tag': 'INFERENCE', 'note': '함의 추론', 'evidence': evidence_22})
        self.create_dna(ids[31], {'entry_type': 'DNA', 'tag': 'INFERENCE', 'note': '간접 추론', 'evidence': evidence_31})
        self.create_dna(ids[22], {'entry_type': 'TRAP', 'tag': 'WORD_ECHO', 'choice_no': 3, 'evidence': evidence_22})
        self.create_dna(ids[31], {'entry_type': 'TRAP', 'tag': 'WORD_ECHO', 'choice_no': 1, 'evidence': evidence_31})
        # when: 시험 단위 DNA 집계를 조회하면
        exam_dna = self.client.get(f'/api/csat/exams/{self.june_id}/dna').json()
        # then: 태그→근거 문항 목록으로 그룹핑되고 함정이 별도 정렬되어 노출된다
        inference = next(t for t in exam_dna['tags'] if t['tag'] == 'INFERENCE')
        self.assertEqual([22, 31], sorted(set(inference['question_nos'])))
        self.assertEqual(2, len(inference['notes']))
        self.assertEqual('TRAP', exam_dna['traps'][0]['entry_type'])
        self.assertEqual('WORD_ECHO', exam_dna['traps'][0]['tag'])

    # ── CLAIM+EVIDENCE 집계 ───────────────────────────────────────────────────

    def test_exam_claims_require_evidence_for_synthesis(self):
        # given: 6월 모평이 있고
        # when: Wiki 종합 CLAIM을 근거 없이 등록하면
        response = self.client.post(f'/api/csat/exams/{self.june_id}/dna-claims', json={
            'claim_type': 'CORE_TRAIT', 'tag': 'INFERENCE', 'claim_text': '추론 문항이 핵심이다', 'provenance': 'WIKI_SYNTHESIS'})
        # then: "DNA는 CLAIM+EVIDENCE" 계약으로 400
        self.assertEqual(400, response.status_code)
        self.assertIn('CLAIM+EVIDENCE', response.json()['detail'])
        # and: 근거 문항을 붙이면 등록되고 집계에서 REJECTED만 제외된다
        created = self.client.post(f'/api/csat/exams/{self.june_id}/dna-claims', json={
            'claim_type': 'CORE_TRAIT', 'tag': 'INFERENCE', 'claim_text': '추론 요구가 반복된다',
            'evidence': [{'exam_id': self.june_id, 'question_no': 21}]}).json()
        self.assertEqual('AI_PROPOSED', created['status'])
        confirmed = self.client.put(f"/api/csat/dna-claims/{created['id']}", json={'status': 'TEACHER_CONFIRMED'}).json()
        self.assertEqual('TEACHER_CONFIRMED', confirmed['status'])
        claims = self.client.get(f'/api/csat/exams/{self.june_id}/dna').json()['claims']
        self.assertEqual([created['id']], [c['id'] for c in claims])
        self.client.put(f"/api/csat/dna-claims/{created['id']}", json={'status': 'REJECTED'})
        self.assertEqual([], self.client.get(f'/api/csat/exams/{self.june_id}/dna').json()['claims'])

    def test_expert_claims_record_instructor_and_agreement(self):
        # given/when: 강사 분석 CLAIM(조정식·이명학 합의)을 agreement와 함께 등록하면
        claim = self.client.post(f'/api/csat/exams/{self.june_id}/dna-claims', json={
            'claim_type': 'MAJOR_TRAP', 'tag': 'WORD_ECHO', 'claim_text': '지선 워드에코가 대표 함정',
            'provenance': 'EXPERT_ANALYSIS', 'expert': '조정식', 'agreement': True,
            'evidence': [{'exam_id': self.june_id, 'question_no': 31}]}).json()
        # then: 출처·합의 여부가 보존된다 (강사 의견 ≠ 평가원 공식 의도)
        self.assertEqual('EXPERT_ANALYSIS', claim['provenance'])
        self.assertEqual('조정식', claim['expert'])
        self.assertTrue(claim['agreement'])

    # ── 6월↔9월 교차 비교 계약 ─────────────────────────────────────────────────

    def test_cross_exam_comparison_requires_evidence_and_distinct_exams(self):
        # given: 두 시험 문항이 각각 등록되어 있고
        self.import_questions(self.june_id, 24)
        self.import_questions(self.sept_id, 24)
        # when/then: 자기자신 비교·근거 없음·미등록 판정은 400
        base = {'exam_a_id': self.june_id, 'exam_b_id': self.june_id, 'tag': 'MAIN_IDEA', 'verdict': 'REPEATED', 'claim_text': 'x',
                'evidence': [{'exam_id': self.june_id, 'question_no': 24}]}
        self.assertEqual(400, self.client.post('/api/csat/dna-comparisons', json=base).status_code)
        self.assertEqual(400, self.client.post('/api/csat/dna-comparisons', json={**base, 'exam_b_id': self.sept_id, 'evidence': []}).status_code)
        self.assertEqual(400, self.client.post('/api/csat/dna-comparisons', json={**base, 'exam_b_id': self.sept_id, 'verdict': 'GUARANTEED'}).status_code)
        self.assertEqual(400, self.client.post('/api/csat/dna-comparisons', json={**base, 'exam_b_id': self.sept_id, 'tag': 'TITLE'}).status_code)
        # and: 근거 있는 비교는 등록되고 (a,b,tag,verdict) 유니크
        self.client.post('/api/csat/dna-comparisons', json={
            'exam_a_id': self.june_id, 'exam_b_id': self.sept_id, 'tag': 'MAIN_IDEA', 'verdict': 'REPEATED',
            'claim_text': 'main idea 요구가 6월·9월 모두 출제', 'evidence': [
                {'exam_id': self.june_id, 'question_no': 24}, {'exam_id': self.sept_id, 'question_no': 24}]})
        self.assertEqual(409, self.client.post('/api/csat/dna-comparisons', json={
            'exam_a_id': self.june_id, 'exam_b_id': self.sept_id, 'tag': 'MAIN_IDEA', 'verdict': 'REPEATED',
            'claim_text': 'dup', 'evidence': [{'exam_id': self.june_id, 'question_no': 24}]}).status_code)
        listed = self.client.get(f'/api/csat/dna-comparisons?exam_a_id={self.june_id}&exam_b_id={self.sept_id}').json()
        self.assertEqual(1, len(listed))
        created = listed[0]
        # and: 교수가 TEACHER_CONFIRMED로 승격, 거절은 목록에서 빠진다
        self.client.put(f"/api/csat/dna-comparisons/{created['id']}", json={'status': 'TEACHER_CONFIRMED'})
        self.assertEqual('TEACHER_CONFIRMED', self.client.get(f'/api/csat/dna-comparisons?exam_a_id={self.june_id}&exam_b_id={self.sept_id}').json()[0]['status'])
        self.client.put(f"/api/csat/dna-comparisons/{created['id']}", json={'status': 'REJECTED'})
        self.assertEqual([], self.client.get(f'/api/csat/dna-comparisons?exam_a_id={self.june_id}&exam_b_id={self.sept_id}').json())

    # ── DAY 2 대비: 학생 오답 ↔ 문항 DNA 연결 구조 ─────────────────────────────

    def test_student_wrong_answer_links_to_question_dna(self):
        # given: 6월 31번에 DNA가 걸려 있고 LOCAL fixture 학생 오답이 문항 id로 연결될 때
        ids = self.seed_dna_question()
        dna = self.create_dna(ids[31], {'entry_type': 'TRAP', 'tag': 'NO_SUPPORT', 'choice_no': 3,
                                        'note': '본문 근거 없는 선지', 'evidence': [{'exam_id': self.june_id, 'question_no': 31}]})
        self.assertEqual('AI_PROPOSED', dna['status'])
        with main.get_db() as conn:
            row = conn.execute('SELECT question_no, answer FROM csat_questions WHERE id=?', (ids[31],)).fetchone()
            conn.execute('''INSERT INTO csat_wrong_answers
                (question_id,student_name,exam_name,question_no,csat_type_id,csat_type_name,wrong_answer,correct_answer,created_at)
                VALUES(?,?,?,?,?,?,?,?,?)''',
                (ids[31], '최승원', '2027학년도 6월 모의평가', row['question_no'], 'blank', '빈칸추론', '3', row['answer'], 'fixture'))
        # when: 오답 → 문항 → DNA 조인을 재현하면 (DAY 2 약점 DNA의 구조 검증)
        with main.get_db() as conn:
            joined = conn.execute('''SELECT w.student_name, w.wrong_answer, d.tag, d.choice_no, d.status
                FROM csat_wrong_answers w
                JOIN csat_question_dna d ON d.question_id = w.question_id
                WHERE w.question_id=? AND d.status<>'REJECTED' ''', (ids[31],)).fetchall()
        # then: 선택 오답 선지(3)와 같은 번호의 함정 DNA가 연결된다
        self.assertEqual(1, len(joined))
        self.assertEqual('최승원', joined[0]['student_name'])
        self.assertEqual('NO_SUPPORT', joined[0]['tag'])
        self.assertEqual(int(joined[0]['wrong_answer']), joined[0]['choice_no'])
        # and: 오답 행은 문항의 실제 정답을 함께 보존한다
        with main.get_db() as conn:
            wrong = conn.execute('SELECT correct_answer FROM csat_wrong_answers WHERE question_id=?', (ids[31],)).fetchone()
        self.assertEqual(sample_questions(31)[0]['answer'], wrong['correct_answer'])

    # ── 권한·회귀 ─────────────────────────────────────────────────────────────

    def test_write_routes_are_admin_only(self):
        # given: 서명된 TUTOR 신원이 있을 때 (?role=ADMIN 표시값이 아니라 서명 역할이 기준)
        tutor = signed_headers('TUTOR', '9', 'tutor-local')
        # when/then: DNA 경로 쓰기는 모두 403
        self.assertEqual(403, self.client.post('/api/csat/exams', json={'exam_year': 2026, 'exam_type': 'CSAT'}, headers=tutor).status_code)
        self.assertEqual(403, self.client.post(f'/api/csat/exams/{self.june_id}/questions',
                     json={'questions': sample_questions(20)}, headers=tutor).status_code)
        self.assertEqual(403, self.client.post(f'/api/csat/exams/{self.june_id}/dna-claims', json={
            'claim_type': 'CORE_TRAIT', 'claim_text': 'x', 'provenance': 'OFFICIAL_SOURCE'}, headers=tutor).status_code)
        self.assertEqual(403, self.client.post('/api/csat/dna-comparisons', json={
            'exam_a_id': self.june_id, 'exam_b_id': self.sept_id, 'tag': '', 'verdict': 'NEW',
            'claim_text': 'x', 'evidence': [{'exam_id': self.june_id, 'question_no': 20}]}, headers=tutor).status_code)
        # and:읽기는 403이 아니면서, 레거시 workspace의 기출 데이터가 개인 workspace로 새지 않는다(격리)
        self.assertEqual([], self.client.get('/api/csat/exams', headers=tutor).json())
        self.assertEqual(404, self.client.get(f'/api/csat/exams/{self.june_id}/dna', headers=tutor).status_code)

    def test_existing_csat_library_routes_still_work(self):
        # given: 회차 없는 기존 라이브러리 문항이 direct insert로 있고
        with main.get_db() as conn:
            source = conn.execute("""INSERT INTO csat_question_sources(exam_kind,source_filename,exam_metadata_json,parser,raw_text,created_at)
                VALUES('half','regression.json','{}','test','', 'now')""")
            csat_type = main.csat_type_for_number(43)
            conn.execute('''INSERT INTO csat_questions
                (source_id,exam_kind,question_no,csat_type_id,csat_type_name,prompt,choices_json,answer,created_at)
                VALUES(?,?,?,?,?,?,?,?,?)''', (source.lastrowid, 'half', 43, csat_type['id'], csat_type['name'],
                '회귀 테스트', '["1","2","3","4","5"]', '5', 'now'))
        # when: 기존 목록 API와 신규 시험 API를 함께 조회하면
        legacy = self.client.get('/api/csat/questions?exam_kind=half').json()
        exam_rows = self.client.get(f'/api/csat/exams/{self.june_id}/questions').json()
        # then: exam_id 없는 기존 문항은 건재하고 시험 문항 목록(빈 상태)과 충돌하지 않는다
        self.assertTrue(any(q['question_no'] == 43 for q in (legacy.get('questions') if isinstance(legacy, dict) else legacy)))
        self.assertEqual([], exam_rows)


if __name__ == '__main__':
    unittest.main()
