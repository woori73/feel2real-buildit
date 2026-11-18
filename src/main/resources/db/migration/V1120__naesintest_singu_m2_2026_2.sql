-- 내신 실전 연습(/naesintest) — 신구중 2학년 2026학년도 2학기 기말 대비 F2R 예상문제 Lesson 7 1~5회.
-- 원본: F2R이 만든 예상문제 문제지 PDF(회당 6쪽)와 정답·해설. 문항은 원본 문제지를 문항 단위로 자른 그림으로 보여 준다
--   (밑줄·<보 기> 상자·ⓐ~ⓔ 표시를 원본 그대로 보이게 하려는 것). 그림: static/naesin/singu-m2-2026-2/rNN/qNN.webp,
--   여러 문항이 같이 쓰는 지문·대화는 pAA-BB.webp(body_text).
-- 출제 범위: 2학기말 학습지 필기본 Lesson 7. 발문·선택지 형식은 2학기 중간고사 기출을 따랐다.
-- 회당 40문항, 배점 합계 100점(5점×3, 4점×4, 3점×3, 2점×30). 정답지와 문항 그림의 번호가 같음을 확인했다.
-- kind='NAESIN', section='EN'. 한 개를 고르는 문항은 task_type='NAESIN_MC5'(①~⑤ → answer_key A~E),
-- "~을 모두 고르면?" 문항은 task_type='NAESIN_MC5_MULTI'(answer_key는 'B,D'처럼 쉼표로 잇고, 고른 묶음이 같아야 정답).
-- 시험 화면은 풀이 중 해설을 보여 주지 않고, 제출 후 복습 화면에서 explanation을 보여 준다. 서버 마감 7200초.

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-SGM2-2602-R01', 'NAESIN', '신구중 2학년 2학기 기말 1회 · Lesson 7 예상문제 1회', 'Lesson 7 학습지 필기본 1~12쪽 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_sg_r01 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-SGM2-2602-R01');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q01.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ와 같은 의미 (2점)
ⓐ hear = (소식을) 듣다·전해 듣다. 나머지는 소리를 듣다
근거: 필기본 3쪽 B · 7쪽 (정답·①③은 새 예문)', 1, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q02.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영어 발문 (2점)
Let’s do something together until then. … let’s go for a boat ride.
근거: 필기본 3쪽 B', 2, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q03.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p03-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ와 같은 의미 (2점)
ⓐ get = 사다, 구하다. ① 도착하다 ③ get up 일어나다 ④⑤ ~하게 되다
근거: 필기본 3쪽 A-3 · 6쪽 (①②③④는 새 예문)', 3, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q04.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p03-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 대화 내용 일치 (2점)
I like the T-shirt better.를 바꿔 씀
근거: 필기본 3쪽 A-3', 4, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 짝지은 대화 어색 (2점)
winter를 좋아하는 이유로 I don’t like the sea.(필기본 Speaking Practice의 오답 선택지)는 어색함
근거: 필기본 3·4쪽', 5, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q06.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p06-07.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ와 같은 의미 (2점)
ⓐ order = 주문하다. ① 순서 ② 명령하다 ④ 명령 ⑤ 순서
근거: 필기본 3쪽 A-2 (선택지는 새 예문)', 6, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q07.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p06-07.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영어 발문 이유 (2점)
I like spicy food.를 바꿔 씀
근거: 필기본 3쪽 A-2', 7, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 그림+지각동사 (2점)
어법 오류: ① to call ② rode / 내용 오류: ③ Daho는 불꽃놀이를 봄 ④ 부른 사람은 Hana
근거: 필기본 7쪽 see/hear + 목적어 + 동사원형/-ing', 8, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q09.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p09-11.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓒ 짝 (2점)
from A to B(기간), during + 명사(축제 기간 동안)
근거: 필기본 9쪽 ②③ Grammar Point', 9, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q10.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p09-11.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영영 풀이 찾을 수 없는 것 (4점)
take place / fireworks / celebrate / parade는 윗글에 있음. ③ statue는 없음(뒷부분에 나옴)
근거: 필기본 1~2쪽 Word List', 10, 4),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q11.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p09-11.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글 내용 일치 (2점)
celebrate the coming of spring을 바꿔 씀. ② 3월 15~19일
근거: 필기본 9쪽 ①', 11, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 전환 후 n번째 단어 (5점)
It(1) is(2) impossible(3) for(4) them(5) to(6) answer(7) the(8) question(9)
근거: 필기본 8쪽 4번 It is impossible for them to answer the question.', 12, 5),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q13.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p13-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ와 같은 의미 (2점)
ⓐ stars = 가장 주목받는 존재(주인공). ①② 별 ③ 등급 ④ 별 모양
근거: 필기본 9쪽 ④ (선택지는 새 예문)', 13, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q14.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p13-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · Which is true (2점)
burned on the last day of the festival을 바꿔 씀
근거: 필기본 9쪽 ⑥', 14, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q15.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p13-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 답할 수 없는 것 (3점)
① wooden ② hundreds ③ on the last day ④ saying goodbye to old things and hello to a new start
근거: 필기본 9쪽 ④~⑦', 15, 3),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r01/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,D', 1, '정답 ②, ④ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ② making → to make(진주어는 to부정사) ④ This → It(가주어는 It)
근거: 필기본 6·8쪽 가주어 It', 16, 4),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q17.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p17-18.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·③ 목적(~하기 위해)의 부사적 용법. ① 목적어 ② 진주어 ④ 명사 수식 ⑤ 목적격 보어
근거: 필기본 10쪽 Grammar Point · 1·3·5·7쪽', 17, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q18.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p17-18.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ~ 이유로 가장 적절한 것 (2점)
Winter in Sweden is long and dark를 바꿔 씀
근거: 필기본 10쪽 ⑧ More Questions 1', 18, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ~ⓒ 짝 (2점)
tell + 목적어 + to부정사, let + 목적어 + 동사원형, hear + 목적어 + 동사원형/-ing
근거: 필기본 7쪽 5형식 예2~4·7번 (4)', 19, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q20.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓒ 짝 (2점)
between A and B, decorate A with B
근거: 필기본 10쪽 ⑪⑫ Grammar Point', 20, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q21.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글 내용 일치 (2점)
⑫를 바꿔 씀
근거: 필기본 10쪽 ⑪~⑬', 21, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q22.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ와 같은 의미 (2점)
Ⓐ sets = (해가) 지다. ① 차리다 ③ 맞추다 ④ 세우다 ⑤ set up 차리다
근거: 필기본 10쪽 ⑬ · 11쪽 ⑱ (①~④ 새 예문)', 22, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q23.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p23-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓔ 내용·어법 짝 (4점)
내용 오류: ⓐ once a month(→once a year) / 어법 오류: ⓓ sets → set(주어 people) ⓔ holding → hold(dress와 병렬)
근거: 필기본 11쪽 ⑭~⑲', 23, 4),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q24.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p23-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 답할 수 없는 것 (3점)
① the Day of the Dead ③ to remember dead people ④ November 1 and 2 ⑤ skeletons (선택지는 오류 부분을 드러내지 않게 구성)
근거: 필기본 11쪽', 24, 3),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q25.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p23-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글 내용 일치 (2점)
They believe it is important to remember dead people.을 바꿔 씀(오답은 ⓐ~ⓔ 오류 부분을 드러내지 않게 구성)
근거: 필기본 11쪽 ⑰', 25, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓔ 활용 의미 어색 (5점)
불가능하다(not possible)고 하고 쉽게 할 수 있다고 하는 것은 맞지 않음
근거: 필기본 1~2쪽 Word List 뜻풀이 (선택지는 새 예문)', 26, 5),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r01/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,D', 1, '정답 ①, ④ · 바꿔 쓸 단어 모두 (3점)
participate in = take part in = become involved in (필기본 영어 뜻풀이)
근거: 필기본 1쪽 participate (to take part in)', 27, 3),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q28.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ~ⓔ 의미 (2점)
ⓐ 방문객들 ⓑ 다양한 ⓒ 전통의 ⓓ 특별한
근거: 필기본 12쪽 Vocabulary·Grammar Point', 28, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q29.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영어 발문 (2점)
At the end of February를 바꿔 씀
근거: 필기본 12쪽 ⑳', 29, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q30.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글 내용 일치 (2점)
㉒를 바꿔 씀
근거: 필기본 12쪽 ⑳~㉒', 30, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q31.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p31-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ~ 이유로 가장 적절한 것 (2점)
Everyone is just having fun!을 바꿔 씀
근거: 필기본 12쪽 ㉕㉗', 31, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q32.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p31-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ와 같은 의미 (2점)
ⓐ race = (명사) 경주. ②⑤ 경주하다 ③ 인종 ④ 빨리 뛰다
근거: 필기본 12쪽 ㉓ (선택지는 새 예문)', 32, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q33.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p31-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영영 풀이 찾을 수 없는 것 (4점)
reindeer / possible / matter / costume은 윗글에 있음. ③ skeleton 없음
근거: 필기본 1~2쪽 Word List', 33, 4),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r01/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,D', 1, '정답 ②, ④ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ② called → call/calling(hear + 목적어 + 동사원형/-ing) ④ to dance → dance/dancing(see + 목적어 + 동사원형/-ing)
근거: 필기본 7쪽 5형식 예4·7번', 34, 4),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ~ⓒ 짝 (2점)
성격 형용사 nice → of + 목적격, easy → for + 목적격, 진주어 that절
근거: 필기본 5·6쪽', 35, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸 어디에도 없는 단어 (2점)
altar / reindeer / puzzle / recipe
근거: 필기본 1쪽 Word List 예문', 36, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 그림 <보기> 빈칸 어디에도 없는 것 (3점)
fireworks / hands, parade / skeleton
근거: 필기본 1~2쪽 Word List 예문', 37, 3),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ와 같은 의미 (2점)
ⓐ matter = (동사) 중요하다. ①②⑤ 문제·일 ④ no matter ~ 이더라도
근거: 필기본 1쪽 matter (선택지는 새 예문)', 38, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·③ 가주어. ①② 비인칭주어 ④ 지시대명사 ⑤ 비인칭주어(거리)
근거: 필기본 6·8쪽 It의 쓰임', 39, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸 어디에도 없는 단어 (2점)
statue / symbolize / take / possible
근거: 필기본 1~2쪽 Word List 예문', 40, 2);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-SGM2-2602-R02', 'NAESIN', '신구중 2학년 2학기 기말 2회 · Lesson 7 예상문제 2회', 'Lesson 7 학습지 필기본 1~12쪽 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_sg_r02 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-SGM2-2602-R02');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q01.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 대화 내용 일치 (2점)
Don’t forget Korea.를 바꿔 씀. ④ 7 p.m.을 알고 있음
근거: 필기본 3쪽 B', 1, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q02.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ와 같은 의미 (2점)
ⓐ start = 시작되다. ① 세우다(설립) ② 시동을 걸다 ③④ 명사 시작·출발
근거: 필기본 3쪽 B (선택지는 새 예문)', 2, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q03.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p03-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 대화 내용 일치 (2점)
Let’s go play VR games together.를 바꿔 씀. ③ Playing VR games is OK, too.
근거: 필기본 3쪽 A-1', 3, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q04.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p03-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영어 발문 이유 (2점)
I don’t want to wash my face again.을 바꿔 씀
근거: 필기본 3쪽 A-1', 4, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 짝지은 대화 어색 (2점)
산과 바다 중 고르라는데 I like spicy food.(필기본 Speaking Practice의 오답 선택지)
근거: 필기본 3·4쪽', 5, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q06.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p06-07.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ와 같은 의미 (2점)
ⓐ pretty = (형용사) 예쁜. ①~④ (부사) 꽤
근거: 필기본 3쪽 A-4 (선택지는 새 예문)', 6, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q07.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p06-07.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 대화 내용 일치 (2점)
Why do you like them more?를 바꿔 씀
근거: 필기본 3쪽 A-4', 7, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 그림+지각동사 (2점)
어법 오류: ① to dance ③ calls / 내용 오류: ④ 자전거는 Yuna ⑤ 기둥 장식은 Mina
근거: 필기본 7쪽 see/hear + 목적어 + 동사원형/-ing', 8, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q09.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p09-11.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓔ 의미 (2점)
ⓐ 기념한다 ⓑ ~이 오는 것 ⓓ ~ 동안 ⓔ 즐긴다
근거: 필기본 9쪽 Vocabulary', 9, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q10.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p09-11.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영어 발문 (2점)
people enjoy parades and fireworks
근거: 필기본 9쪽 ③', 10, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q11.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p09-11.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글 내용 일치 (2점)
from March 15 to 19를 바꿔 씀
근거: 필기본 9쪽 ②', 11, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r02/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,D', 1, '정답 ②, ④ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ② learn → to learn ④ of them → for them(impossible은 성격 형용사가 아님)
근거: 필기본 5·6·8쪽 가주어 It', 12, 4),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q13.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p13-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · Ⓐ 어법상 쓰임 (2점)
Ⓐ·① 수동태(be + 과거분사). ② 과거시제 ③④ 진행형 ⑤ be + 형용사
근거: 필기본 9쪽 ⑥ Grammar Point · 1·3·12쪽', 13, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q14.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p13-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글 내용 일치 (2점)
You can find hundreds of them all over the city.를 바꿔 씀
근거: 필기본 9쪽 ⑤', 14, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q15.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p13-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영영 풀이 찾을 수 없는 것 (4점)
giant / statue / burn / symbolize는 윗글에 있음. ④ celebrate는 없음(앞부분에 나옴)
근거: 필기본 1~2쪽 Word List', 15, 4),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q16.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p16-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ와 같은 의미 (2점)
ⓐ welcome = (동사) 반가이 맞이하다. ①⑤ 감탄 인사 ② 천만에요 ③ 형용사 환영하는
근거: 필기본 10쪽 ⑧ (선택지는 새 예문)', 16, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q17.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p16-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영어 발문 이유 (2점)
To celebrate the longest day of the year를 바꿔 씀
근거: 필기본 10쪽 ⑨', 17, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q18.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p18-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글 내용 일치 (2점)
starts on the Friday evening … ends on the Sunday를 바꿔 씀
근거: 필기본 10쪽 ⑪', 18, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q19.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p18-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 답할 수 없는 것 (3점)
② Friday evening ③ flowers and green leaves ④ the sun hardly sets ⑤ on the Sunday
근거: 필기본 10쪽 ⑪~⑬', 19, 3),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓒ 짝 (2점)
ask + 목적어 + to부정사, see + 목적어 + -ing, make + 목적어 + 형용사
근거: 필기본 7쪽 5형식 예1·예3·7번 (2)', 20, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r02/q21.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p21-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,C', 1, '정답 ①, ③ · 의미와 쓰임이 ⓐ, ⓑ와 같은 것 모두 (5점)
ⓐ call A B = A를 B라고 부르다, ⓑ have (파티를) 열다. ② 전화하다·먹다 ④ 전화하다·먹다 ⑤ have 가지다
근거: 필기본 7·11쪽 (선택지는 새 예문)', 21, 5),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r02/q22.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p21-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,D', 1, '정답 ②, ④ · 글 내용 일치 모두 (4점)
② To welcome dead people … hold a festival ④ set up altars in their homes를 바꿔 씀
근거: 필기본 11쪽 ⑯⑱', 22, 4),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q23.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p21-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ~ 이유로 가장 적절한 것 (2점)
⑰ They believe it is important to remember dead people. → ⑱ So, …
근거: 필기본 11쪽 ⑰⑱', 23, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 전환 후 n번째 단어 (5점)
It(1) is(2) clear(3) that(4) she(5) knows(6) the(7) truth(8)
근거: 필기본 6쪽 7번 It is clear that she knows the truth.', 24, 5),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓔ 활용 의미 어색 (5점)
다른 사람 생각은 중요하지 않다(doesn’t matter)면서 늘 걱정한다는 것은 맞지 않음
근거: 필기본 1~2쪽 Word List 뜻풀이 (선택지는 새 예문)', 25, 5),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r02/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,D', 1, '정답 ②, ④ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ② sang → sing/singing(hear + 목적어 + 동사원형/-ing) ④ calls Mr. Fix-It him → calls him Mr. Fix-It(call + 목적어 + 목적격 보어)
근거: 필기본 7쪽 5형식 예4·배열 (1)', 26, 4),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q27.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p27-29.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ와 같은 의미 (2점)
ⓐ hold = (행사를) 열다. ① 들고 있다 ② 기다리다 ③ 안다 ④ 수용하다
근거: 필기본 12쪽 ⑳ (선택지는 새 예문)', 27, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q28.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p27-29.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영어 발문 (2점)
various winter sports and Alaskan traditional games를 바꿔 씀
근거: 필기본 12쪽 ㉑', 28, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q29.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p27-29.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · Which is true (2점)
many special events such as the Running of the Reindeer를 바꿔 씀
근거: 필기본 12쪽 ㉒', 29, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q30.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p30-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓔ 의미 (2점)
ⓐ ~에 참가하다 ⓑ 입다 ⓒ 달리려고 애쓰다 ⓔ 문제가 되지 않는다
근거: 필기본 12쪽 Vocabulary·Grammar Point', 30, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q31.webp]]', '[[img:/naesin/singu-m2-2026-2/r02/p30-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영어 발문 (2점)
try to run faster than reindeer를 바꿔 씀
근거: 필기본 12쪽 ㉓', 31, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ~ⓒ 짝 (2점)
가주어 It, 진주어 to부정사, 진주어 that절
근거: 필기본 6·8쪽 가주어 It', 32, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸 어디에도 없는 단어 (2점)
fireworks / skeleton / burned / participate
근거: 필기본 1~2쪽 Word List 예문', 33, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r02/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,D', 1, '정답 ①, ④ · 바꿔 쓸 단어 모두 (3점)
take place = happen = be held
근거: 필기본 2쪽 take place (to happen)', 34, 3),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 그림 <보기> 빈칸 어디에도 없는 것 (3점)
costume / pole, ball / reindeer
근거: 필기본 1~2쪽 Word List 예문', 35, 3),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ와 같은 의미 (2점)
ⓐ follow = (지시·조언 등을) 따르다. ①④ 뒤따라가다 ② 이해하다 ⑤ 뒤를 잇다
근거: 필기본 1쪽 recipe 예문 (선택지는 새 예문)', 36, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ와 같은 의미 (2점)
ⓐ wave = (손을) 흔들다. 나머지는 명사 파도·물결
근거: 필기본 1쪽 parade 예문 (선택지는 새 예문)', 37, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · Ⓐ 어법상 쓰임 (2점)
Ⓐ·① 비인칭주어(명암·계절). ②④ 지시대명사 ③⑤ 가주어
근거: 필기본 6쪽 It의 쓰임', 38, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·⑤ 목적어 역할(명사적 용법). ①③④ 목적(부사적) ② 명사 수식(형용사적)
근거: 필기본 1·3·5·11쪽', 39, 2),
(@naesin_sg_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r02/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 어디에도 없는 단어 (2점)
hard / nice / possible / exciting (excited는 사람의 감정)
근거: 필기본 5·6쪽 가주어 It 연습', 40, 2);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-SGM2-2602-R03', 'NAESIN', '신구중 2학년 2학기 기말 3회 · Lesson 7 예상문제 3회', 'Lesson 7 학습지 필기본 1~12쪽 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_sg_r03 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-SGM2-2602-R03');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q01.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영어 발문 (2점)
I hear China, Italy, and the USA are participating this year. / Don’t forget Korea.
근거: 필기본 3쪽 B', 1, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q02.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화 내용 일치 (2점)
I guess we can ride bikes or go for a boat ride.를 바꿔 씀
근거: 필기본 3쪽 B', 2, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q03.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p03-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ와 같은 의미 (2점)
ⓐ much = 비교급 앞에서 ‘훨씬’. ① 많은 ② 대단히 ④ 얼마 ⑤ 많이
근거: 필기본 3쪽 A-2 (선택지는 새 예문)', 3, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q04.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p03-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 대화 내용 일치 (2점)
I’m so hungry!를 바꿔 씀
근거: 필기본 3쪽 A-2', 4, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 짝지은 대화 어색 (2점)
해변을 좋아하는 이유로 I don’t like spicy food.(필기본 Speaking Practice의 오답 선택지)는 어색함
근거: 필기본 3·4쪽', 5, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q06.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p06-07.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영어 발문 이유 (2점)
There is a nice picture of the festival on it.을 바꿔 씀
근거: 필기본 3쪽 A-3', 6, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q07.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p06-07.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 대화 내용 일치 (2점)
Which do you like better, the T-shirt or the cap?을 바꿔 씀
근거: 필기본 3쪽 A-3', 7, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q08.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p08-09.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ와 같은 의미 (2점)
ⓐ more = (부사) 더(좋아하다). ②④⑤ 더 많은 ③ 더 많은 것
근거: 필기본 3쪽 A-4 (선택지는 새 예문)', 8, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q09.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p08-09.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 대화 내용 일치 (2점)
There are lots of pretty flowers here.를 바꿔 씀
근거: 필기본 3쪽 A-4', 9, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 그림+지각동사 (2점)
어법 오류: ① decorated ② to watch / 내용 오류: ③ 자전거는 Yuna ⑤ 부른 사람은 Hana
근거: 필기본 7쪽 see/hear + 목적어 + 동사원형/-ing', 10, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r03/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,E', 1, '정답 ②, ⑤ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ② meet → to meet ⑤ That → It(가주어는 It)
근거: 필기본 5·6·8쪽 가주어 It', 11, 4),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ~ⓒ 짝 (2점)
have + 목적어 + 동사원형, want + 목적어 + to부정사, hear + 목적어 + 동사원형
근거: 필기본 7쪽 5형식 예3·7번 (5)(6)', 12, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q13.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p13-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓔ 내용·어법 짝 (4점)
내용 오류: ⓑ May(→March) / 어법 오류: ⓓ are burn → are burned(수동태) ⓔ symbolize → symbolizes(동명사 주어는 단수)
근거: 필기본 9쪽 ①~⑦', 13, 4),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q14.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p13-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영영 풀이 찾을 수 없는 것 (3점)
celebrate / fireworks / statue / symbolize는 윗글에 있음. ③ decorate는 없음
근거: 필기본 1~2쪽 Word List', 14, 3),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q15.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p13-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 답할 수 없는 것 (3점)
① wooden ② hundreds ③ parades and fireworks ④ the coming of spring (선택지는 ⓐ~ⓔ 오류 부분을 드러내지 않게 구성)
근거: 필기본 9쪽', 15, 3),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q16.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p16-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · Ⓐ 어법상 쓰임 (2점)
Ⓐ(a special festival을 가리킴)·② 지시대명사. ①④⑤ 비인칭주어 ③ 가주어
근거: 필기본 6쪽 It의 쓰임 · 10쪽 ⑩', 16, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q17.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p16-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ~ 이유로 가장 적절한 것 (2점)
To celebrate the longest day of the year를 바꿔 씀
근거: 필기본 10쪽 ⑨', 17, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 전환 후 n번째 단어 (5점)
It(1) is(2) hard(3) to(4) keep(5) a(6) diary(7) every(8) day(9)
근거: 필기본 6쪽 5번 It is hard to keep a diary every day.', 18, 5),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ~ⓔ 활용 의미 어색 (5점)
parade는 공공장소를 행진하는 것(moving together through a public place)이라 작은 방 안에서 열렸다는 것은 맞지 않음
근거: 필기본 1~2쪽 Word List 뜻풀이 (선택지는 새 예문)', 19, 5),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q20.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓔ 의미 (2점)
ⓐ 시작한다 ⓑ ~ 사이 ⓓ 전통의 ⓔ 거의 ~않다(‘열심히’는 hard)
근거: 필기본 10쪽 Vocabulary', 20, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q21.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영어 발문 (2점)
starts on the Friday evening
근거: 필기본 10쪽 ⑪', 21, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q22.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · Which is true (2점)
ends on the Sunday를 바꿔 씀
근거: 필기본 10쪽 ⑪', 22, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q23.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p23-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ와 같은 의미 (2점)
ⓐ meet = (사람을) 만나다. ②④ 충족시키다 ③ 맞추다 ⑤ (명사) 대회
근거: 필기본 11쪽 ⑭ (선택지는 새 예문)', 23, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q24.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p23-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영어 발문 (2점)
once a year dead people come back home를 바꿔 씀
근거: 필기본 11쪽 ⑭', 24, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q25.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p23-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글 내용 일치 (2점)
on November 1 and 2를 바꿔 씀
근거: 필기본 11쪽 ⑯', 25, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r03/q26.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p26-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,C', 1, '정답 ①, ③ · 의미와 쓰임이 ⓐ, ⓑ와 같은 것 모두 (5점)
ⓐ set up = 차리다·설치하다, ⓑ have = (파티를) 열다. ② 설립하다·가지다 ④ 차리다(가게 개업)·먹다 ⑤ 설립하다·가지다
근거: 필기본 11쪽 ⑱ (선택지는 새 예문)', 26, 5),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q27.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p26-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · Which is true (2점)
In some areas, people dress up like skeletons and hold big parades.를 바꿔 씀
근거: 필기본 11쪽 ⑲', 27, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r03/q28.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,D', 1, '정답 ①, ④ · 글 내용 일치 모두 (4점)
① At the end of February ④ wear fun costumes를 바꿔 씀
근거: 필기본 12쪽 ⑳~㉗', 28, 4),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q29.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ와 같은 의미 (2점)
ⓐ run = 달리다. ① 경영하다 ③ 운행하다 ④ 흐르다 ⑤ 출마하다
근거: 필기본 12쪽 ㉓ (선택지는 새 예문)', 29, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q30.webp]]', '[[img:/naesin/singu-m2-2026-2/r03/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·③ 가주어(진주어 to부정사). ①⑤ 지시대명사 ②④ 비인칭주어
근거: 필기본 6쪽 It의 쓰임 · 12쪽 ㉔', 30, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r03/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,D', 1, '정답 ②, ④ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ② danced → dance/dancing(see + 목적어 + 동사원형/-ing) ④ to shout → shout/shouting(hear + 목적어 + 동사원형/-ing)
근거: 필기본 7쪽 5형식 예4·배열 (1)', 31, 4),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓒ 짝 (2점)
진주어 to부정사, 가주어 it, 성격 형용사 nice → of + 목적격
근거: 필기본 5·6쪽', 32, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸 어디에도 없는 단어 (2점)
possible / matters / symbolize / altar
근거: 필기본 1~2쪽 Word List 예문', 33, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r03/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,E', 1, '정답 ②, ⑤ · 바꿔 쓸 단어 모두 (3점)
take place = happen = be held(열리다)
근거: 필기본 2쪽 take place (to happen) · 11쪽 hold a festival', 34, 3),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 그림 <보기> 빈칸 어디에도 없는 것 (2점)
decorated, flowers / skeleton / costume
근거: 필기본 1~2쪽 Word List 예문', 35, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ와 같은 의미 (2점)
ⓐ spring = 봄. ①⑤ 용수철 ② 샘 ③ (동사) 뛰어오르다
근거: 필기본 9쪽 ① (선택지는 새 예문)', 36, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ와 같은 의미 (2점)
ⓐ right = 맞는, 옳은. ② 오른쪽으로 ③ 권리 ④ 오른쪽의 ⑤ 바로
근거: 필기본 3쪽 B (선택지는 새 예문)', 37, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·③ 목적격 보어 자리의 동사원형. ① to부정사의 일부 ② 조동사 뒤 ④ 명령문 ⑤ 의문문의 동사원형
근거: 필기본 7쪽 7번 (1) · 5형식 예2', 38, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·⑤ 앞의 명사를 꾸미는 형용사적 용법. ① 목적 ② 진주어 ③ 목적어 ④ 목적격 보어
근거: 필기본 5·8쪽', 39, 2),
(@naesin_sg_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r03/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 어디에도 없는 단어 (2점)
easy / boring / difficult / nice (bored는 사람의 감정)
근거: 필기본 8쪽 연습문제 4·5·6번', 40, 2);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-SGM2-2602-R04', 'NAESIN', '신구중 2학년 2학기 기말 4회 · Lesson 7 예상문제 4회', 'Lesson 7 학습지 필기본 1~12쪽 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_sg_r04 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-SGM2-2602-R04');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q01.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ와 같은 의미 (2점)
ⓐ do = (활동을) 하다. ① 의문문 조동사 ③ 강조 ④ 충분하다 ⑤ 부정문 조동사
근거: 필기본 3쪽 A-1 (선택지는 새 예문)', 1, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q02.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · Which is true (2점)
I like face painting more. / Playing VR games is OK, too.를 바꿔 씀
근거: 필기본 3쪽 A-1', 2, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q03.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p03-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영어 발문 (2점)
So, the fireworks start at 7 p.m. Right? — Yeah.
근거: 필기본 3쪽 B', 3, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q04.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p03-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 대화 내용 일치 (2점)
Going for a boat ride. I think it’ll be more fun.을 바꿔 씀
근거: 필기본 3쪽 B', 4, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q05.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p05-06.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영어 발문 (2점)
Then, let’s order tteokbokki.
근거: 필기본 3쪽 A-2', 5, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q06.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p05-06.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화 내용 일치 (2점)
It looks delicious.를 바꿔 씀
근거: 필기본 3쪽 A-2', 6, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q07.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p07-08.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영어 발문 이유 (2점)
Well, yellow is my favorite color.
근거: 필기본 3쪽 A-4', 7, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q08.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p07-08.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 대화 내용 일치 (2점)
Which do you like more, the red flowers or the yellow flowers?를 바꿔 씀
근거: 필기본 3쪽 A-4', 8, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 짝지은 대화 어색 (2점)
둘 중 하나를 고르라는 Which 의문문에 Yes, I do.로 답할 수 없음
근거: 필기본 3·4쪽', 9, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 그림+지각동사 (2점)
어법 오류: ① to decorate ② called / 내용 오류: ③ Daho는 불꽃놀이를 봄 ④ Eric과 Sora는 기둥 주위에서 춤추고 자전거는 Yuna만 탐
근거: 필기본 7쪽 see/hear + 목적어 + 동사원형/-ing', 10, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r04/q11.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p11-13.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,E', 1, '정답 ②, ⑤ · 의미와 쓰임이 ⓐ, ⓑ와 같은 것 모두 (5점)
ⓐ find = 찾다, 발견하다 ⓑ last = (형용사) 마지막의. ① find 생각하다(5형식) ③ last 동사 계속되다 ④ last 지난
근거: 필기본 9쪽 ⑤⑥ (선택지는 새 예문)', 11, 5),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q12.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p11-13.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글 내용 일치 (2점)
giant wooden statues를 바꿔 씀
근거: 필기본 9쪽 ④', 12, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q13.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p11-13.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영어 발문 이유 (2점)
Burning them symbolizes saying goodbye to old things and hello to a new start.
근거: 필기본 9쪽 ⑦', 13, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r04/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C,D', 1, '정답 ③, ④ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ③ for wear → to wear ④ This → It(가주어는 It)
근거: 필기본 5·6·8쪽 가주어 It', 14, 4),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ~ⓒ 짝 (2점)
사역동사 make/let + 목적어 + 동사원형, want + 목적어 + to부정사
근거: 필기본 7쪽 5형식 예2·7번', 15, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 전환 후 n번째 단어 (5점)
It(1) is(2) important(3) to(4) get(5) enough(6) sleep(7)
근거: 필기본 8쪽 연습문제 3번 It is important to get enough sleep.', 16, 5),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r04/q17.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,E', 1, '정답 ②, ⑤ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ② to run → run/running(see + 목적어 + 동사원형/-ing) ⑤ barked → bark/barking(hear + 목적어 + 동사원형/-ing)
근거: 필기본 7쪽 5형식 예4·배열 (1)', 17, 4),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q18.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p18-20.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓔ 내용·어법 짝 (4점)
어법 오류: ⓐ are → is(주어 Winter는 단수) / 내용 오류: ⓒ Winter(→Midsummer) ⓓ Monday(→Sunday)
근거: 필기본 10쪽 ⑧~⑬', 18, 4),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q19.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p18-20.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영어 발문 (2점)
enjoy traditional dances around it
근거: 필기본 10쪽 ⑫', 19, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q20.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p18-20.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 답할 수 없는 것 (3점)
② flowers and green leaves ③ the sun hardly sets ④ to celebrate the longest day ⑤ summer (선택지는 ⓐ~ⓔ 오류 부분을 묻지 않게 구성)
근거: 필기본 10쪽', 20, 3),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q21.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p21-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓔ 의미 (2점)
ⓐ 믿는다 ⓒ 돌아오다 ⓓ 맞이하다 ⓔ (행사를) 열다
근거: 필기본 11쪽 Vocabulary', 21, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q22.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p21-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영어 발문 (2점)
on November 1 and 2
근거: 필기본 11쪽 ⑯', 22, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q23.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p21-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글 내용 일치 (2점)
⑭를 바꿔 씀
근거: 필기본 11쪽 ⑭', 23, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓔ 활용 의미 어색 (5점)
다음 주 금요일에 열릴(take place) 축제를 지난주에 즐겼다는 것은 맞지 않음
근거: 필기본 1~2쪽 Word List 뜻풀이 (선택지는 새 예문)', 24, 5),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r04/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,C', 1, '정답 ①, ③ · 바꿔 쓸 단어 모두 (3점)
participate in = take part in = become involved in
근거: 필기본 1쪽 participate (to take part in or become involved in an activity) · 12쪽 ㉓', 25, 3),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q26.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p26-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · Ⓐ 어법상 쓰임 (2점)
Ⓐ·① 가주어(진주어 to부정사). ②④ 비인칭주어 ③⑤ 지시대명사
근거: 필기본 6쪽 It의 쓰임 · 11쪽 ⑰', 26, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q27.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p26-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ와 같은 의미 (2점)
Ⓑ like = (전치사) ~처럼. 나머지는 동사 좋아하다·원하다
근거: 필기본 11쪽 ⑲ (선택지는 새 예문)', 27, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q28.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p26-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · Which is true (2점)
⑱을 바꿔 씀
근거: 필기본 11쪽 ⑱', 28, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q29.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p29-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영영 풀이 찾을 수 없는 것 (4점)
possible / costume / matter / reindeer는 윗글에 있음. ⑤ statue는 없음
근거: 필기본 1~2쪽 Word List', 29, 4),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q30.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p29-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영어 발문 (2점)
Is it possible to do so? Not really
근거: 필기본 12쪽 ㉔㉕', 30, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q31.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p29-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글 내용 일치 (2점)
㉑을 바꿔 씀
근거: 필기본 12쪽 ㉑', 31, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓒ 짝 (2점)
의미상 주어 for + 목적격, 성격 형용사 nice → of + 목적격, 가주어 It + 진주어 that절
근거: 필기본 6·8쪽', 32, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 어디에도 없는 단어 (2점)
costume / altar / statue / celebrate
근거: 필기본 1~2쪽 Word List 예문', 33, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 그림 <보기> 빈칸 어디에도 없는 것 (3점)
pole, ball / fireworks / reindeer
근거: 필기본 1~2쪽 Word List 예문', 34, 3),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ와 같은 의미 (2점)
ⓐ giant = (형용사) 거대한. ①②③ (명사) 거인 ④ (명사) 거장
근거: 필기본 9쪽 ④ (선택지는 새 예문)', 35, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ와 같은 의미 (2점)
ⓐ free = 한가한, 자유 시간의. ①④ 무료의 ③ 자유로운 ⑤ ~이 없는
근거: 필기본 1쪽 puzzle 예문 (선택지는 새 예문)', 36, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ와 같은 의미 (2점)
ⓐ hard = 어려운. ② 열심히 ③ 딱딱한 ④ 세차게 ⑤ 열심히 하는
근거: 필기본 6쪽 5번 (선택지는 새 예문)', 37, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·④ 목적(~하기 위해서). ① 목적어 ② 진주어 ③ 명사 수식 ⑤ 목적격 보어
근거: 필기본 1·5·7쪽 (상자 문장·정답 선택지는 새 예문)', 38, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸 어디에도 없는 단어 (2점)
burned / decorated / recipe / possible
근거: 필기본 1~2쪽 Word List 예문', 39, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·③ 주어 역할 동명사. ① 진행형 ②⑤ 목적격 보어(현재분사) ④ 명사 수식(현재분사)
근거: 필기본 9쪽 ⑦ Grammar Point', 40, 2);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-SGM2-2602-R05', 'NAESIN', '신구중 2학년 2학기 기말 5회 · Lesson 7 예상문제 5회', 'Lesson 7 학습지 필기본 1~12쪽 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_sg_r05 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-SGM2-2602-R05');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q01.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ와 같은 의미 (2점)
ⓐ surprise = (명사) 뜻밖의 일. ①②③ (동사) 놀라게 하다 ⑤ 깜짝(예고 없는)
근거: 필기본 3쪽 B (선택지는 새 예문)', 1, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q02.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · Which is true (2점)
Hi, Jihan. What a surprise!를 바꿔 씀
근거: 필기본 3쪽 B', 2, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q03.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p03-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영어 발문 (2점)
Let’s get a souvenir.
근거: 필기본 3쪽 A-3', 3, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q04.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p03-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 대화 내용 일치 (2점)
I like the T-shirt better. / There is a nice picture of the festival on it.
근거: 필기본 3쪽 A-3', 4, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 짝지은 대화 어색 (2점)
티셔츠를 더 좋아하는 이유를 묻는데 모자가 더 좋다고 답함
근거: 필기본 3·4쪽', 5, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q06.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p06-07.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 대화 내용 일치 (2점)
I don’t want to wash my face again.
근거: 필기본 3쪽 A-1', 6, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q07.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p06-07.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영어 발문 (2점)
Let’s go play VR games together.
근거: 필기본 3쪽 A-1', 7, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q08.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p08-09.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영어 발문 (2점)
I like tteokbokki much better.
근거: 필기본 3쪽 A-2', 8, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q09.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p08-09.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 대화 내용 일치 (2점)
I like spicy food.
근거: 필기본 3쪽 A-2', 9, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q10.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p10-11.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영어 발문 (2점)
Well, yellow is my favorite color.
근거: 필기본 3쪽 A-4', 10, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q11.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p10-11.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 대화 내용 일치 (2점)
the red flowers or the yellow flowers
근거: 필기본 3쪽 A-4', 11, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 그림+지각동사 (2점)
어법 오류: ① to call ② watched / 내용 오류: ④ 기둥 장식은 Mina ⑤ Yuna는 자전거를 탐(기둥 주위에서 춤추는 것은 Eric과 Sora)
근거: 필기본 7쪽 see/hear + 목적어 + 동사원형/-ing', 12, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q13.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p13-14.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영어 발문 (2점)
celebrate the coming of spring
근거: 필기본 9쪽 ①', 13, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q14.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p13-14.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 답할 수 없는 것 (3점)
② Valencia ③ from March 15 to 19 ④ five days ⑤ parades and fireworks
근거: 필기본 9쪽 ①~③', 14, 3),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q15.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p15-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓔ 의미 (2점)
ⓐ 주인공 ⓑ 수백의 ⓒ ~ 곳곳에 ⓔ 상징한다
근거: 필기본 9쪽 Vocabulary', 15, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r05/q16.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p15-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,C', 1, '정답 ①, ③ · 글 내용 일치 모두 (4점)
① giant wooden statues ③ burned on the last day를 바꿔 씀
근거: 필기본 9쪽 ④~⑦', 16, 4),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q17.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p15-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ~ 이유로 가장 적절한 것 (3점)
Burning them symbolizes saying goodbye to old things and hello to a new start.를 바꿔 씀
근거: 필기본 9쪽 ⑦', 17, 3),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r05/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,D', 1, '정답 ①, ④ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ① learn → to learn ④ to playing → to play
근거: 필기본 5·6·8쪽 가주어 It', 18, 4),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q19.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p19-20.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ와 같은 의미 (2점)
ⓐ long = (형용사) 긴. ② (동사) 몹시 바라다 ③ 얼마나 오래 ④ 오래전에 ⑤ ~하는 한
근거: 필기본 10쪽 ⑧ (선택지는 새 예문)', 19, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q20.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p19-20.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · Which is true (2점)
⑨를 바꿔 씀
근거: 필기본 10쪽 ⑨', 20, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q21.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p21-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영어 발문 (2점)
with flowers and green leaves
근거: 필기본 10쪽 ⑫', 21, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q22.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p21-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글 내용 일치 (2점)
the sun hardly sets를 바꿔 씀
근거: 필기본 10쪽 ⑬', 22, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓒ 짝 (2점)
tell + 목적어 + to부정사, see + 목적어 + 동사원형/-ing, have + 목적어 + 동사원형
근거: 필기본 7쪽 7번 (2)(4)(6)', 23, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q24.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p24-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·⑤ 목적(~하기 위해서). ① 목적어 ② 명사 수식 ③ 진주어 ④ 목적격 보어
근거: 필기본 1·5·6·7쪽 · 11쪽 ⑯', 24, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q25.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p24-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영어 발문 (2점)
dress up like skeletons
근거: 필기본 11쪽 ⑲', 25, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q26.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p24-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · Which is true (2점)
⑰을 바꿔 씀
근거: 필기본 11쪽 ⑰', 26, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 전환 후 n번째 단어 (5점)
It(1) is(2) fun(3) to(4) solve(5) word(6) puzzles(7)
근거: 필기본 6쪽 2번 It is fun to solve word puzzles.', 27, 5),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r05/q28.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,D', 1, '정답 ②, ④ · 의미와 쓰임이 ⓐ, ⓑ와 같은 것 모두 (5점)
ⓐ hold = (행사를) 열다 ⓑ matter = (동사) 중요하다. ① 잡다·명사 문제 ③ 명사 문제 ⑤ 들다
근거: 필기본 12쪽 ⑳㉕ (선택지는 새 예문)', 28, 5),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q29.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영영 풀이 찾을 수 없는 것 (4점)
participate / costume / possible / reindeer는 윗글에 있음. ① altar는 없음
근거: 필기본 1~2쪽 Word List', 29, 4),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q30.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글 내용 일치 (2점)
㉓을 바꿔 씀
근거: 필기본 12쪽 ㉓', 30, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓔ 활용 의미 어색 (5점)
요리법(recipe)을 잃어버렸는데 그 요리법대로 정확히 만들었다는 것은 맞지 않음
근거: 필기본 1~2쪽 Word List 뜻풀이 (선택지는 새 예문)', 31, 5),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r05/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C,D', 1, '정답 ③, ④ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ③ to call → call/calling(hear + 목적어 + 동사원형/-ing) ④ rode → ride/riding(see + 목적어 + 동사원형/-ing)
근거: 필기본 7쪽 5형식 예4·배열 (1)', 32, 4),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ~ⓒ 짝 (2점)
가주어 It, 의미상 주어 for + 목적격, 진주어 that절
근거: 필기본 5·6·8쪽', 33, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 어디에도 없는 단어 (2점)
puzzle / altar / possible / take place
근거: 필기본 1~2쪽 Word List 예문', 34, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 그림 <보기> 빈칸 어디에도 없는 것 (3점)
parade / decorated, flowers / reindeer
근거: 필기본 1~2쪽 Word List 예문', 35, 3),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ와 같은 의미 (2점)
ⓐ letter = 편지. 나머지는 글자·문자
근거: 필기본 1쪽 burn 예문 (선택지는 새 예문)', 36, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ와 같은 의미 (2점)
ⓐ watch = 보다. ②⑤ (명사) 손목시계 ③ 조심하다 ④ 지켜 주다
근거: 필기본 6쪽 1번 (선택지는 새 예문)', 37, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · Ⓐ 어법상 쓰임 (2점)
Ⓐ·② 목적어절을 이끄는 접속사. ① 지시대명사 ③ 지시형용사 ④ 부사(그렇게) ⑤ 관계대명사
근거: 필기본 11쪽 ⑭', 38, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·④ 가주어(진주어 to부정사). ①③ 비인칭주어 ②⑤ 지시대명사
근거: 필기본 6쪽 It의 쓰임 · 12쪽 ㉔ (정답 선택지는 새 예문)', 39, 2),
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸 어디에도 없는 단어 (2점)
fireworks / giant / symbolize / statue
근거: 필기본 1~2쪽 Word List 예문', 40, 2);
