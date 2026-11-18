-- 내신 실전 연습(/naesintest) — 신구중 2학년 2026학년도 2학기 기말 대비 F2R 예상문제 Lesson 7 1~5회, Lesson 8 6~10회.
-- 원본: F2R이 만든 예상문제 문제지 PDF(회당 6쪽)와 정답·해설. 문항은 원본 문제지를 문항 단위로 자른 그림으로 보여 준다
--   (밑줄·<보 기> 상자·ⓐ~ⓔ 표시를 원본 그대로 보이게 하려는 것). 그림: static/naesin/singu-m2-2026-2/rNN/qNN.webp,
--   여러 문항이 같이 쓰는 지문·대화는 pAA-BB.webp(body_text).
-- 출제 범위: 2학기말 학습지 필기본 Lesson 7(1~12쪽, 1~5회)·Lesson 8(13~22쪽, 6~10회). 발문·선택지 형식은 2학기 중간고사 기출을 따랐다.
-- 회당 40문항, 배점 합계 100점(5점×3, 4점×4, 3점×3, 2점×30). 정답지와 문항 그림의 번호가 같음을 확인했다.
-- kind='NAESIN', section='EN'. 한 개를 고르는 문항은 task_type='NAESIN_MC5'(①~⑤ → answer_key A~E),
-- "~을 모두 고르면?" 문항은 task_type='NAESIN_MC5_MULTI'(answer_key는 'B,D'처럼 쉼표로 잇고, 고른 묶음이 같아야 정답).
-- 시험 화면은 풀이 중 해설을 보여 주지 않고, 제출 후 복습 화면에서 explanation을 보여 준다. 서버 마감 7200초.

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-SGM2-2602-R01', 'NAESIN', '신구중 2학년 2학기 기말 1회 · Lesson 7 예상문제 1회', 'Lesson 7 학습지 필기본 1~12쪽 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_sg_r01 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-SGM2-2602-R01');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q01.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ와 같은 의미 (2점)
ⓐ hear = (소식을) 듣다·전해 듣다. 나머지는 소리를 듣다
근거: 필기본 3쪽 B (선택지는 새 예문)', 1, 2),
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
근거: 필기본 10쪽 Grammar Point · 3·5·6·7쪽', 17, 2),
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
ⓐ race = (명사) 경주. ②⑤ 서둘러 가다·달려가다 ③ 인종 ④ (심장이) 빨리 뛰다
근거: 필기본 12쪽 ㉓ (선택지는 새 예문)', 32, 2),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r01/q33.webp]]', '[[img:/naesin/singu-m2-2026-2/r01/p31-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영영 풀이 찾을 수 없는 것 (4점)
reindeer / possible / matter / costume은 윗글에 있음. ③ skeleton 없음
근거: 필기본 1~2쪽 Word List', 33, 4),
(@naesin_sg_r01, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r01/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,D', 1, '정답 ②, ④ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ② sang → sing/singing(hear + 목적어 + 동사원형/-ing) ④ to dance → dance/dancing(see + 목적어 + 동사원형/-ing)
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
어법 오류: ② make → to make ④ of them → for them(impossible은 성격 형용사가 아님)
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
근거: 필기본 5·6쪽 가주어 It', 32, 2),
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
have + 목적어 + 동사원형, want + 목적어 + to부정사, make + 목적어 + 형용사
근거: 필기본 7쪽 5형식 예1·예3·7번 (6)', 12, 2),
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
ⓐ run = 달리다. ① 경영하다 ③ 운행하다 ④ 흐르다 ⑤ 작동하다
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
근거: 필기본 5쪽 · 8쪽 연습문제 4·6번', 40, 2);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-SGM2-2602-R04', 'NAESIN', '신구중 2학년 2학기 기말 4회 · Lesson 7 예상문제 4회', 'Lesson 7 학습지 필기본 1~12쪽 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_sg_r04 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-SGM2-2602-R04');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q01.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ와 같은 의미 (2점)
ⓐ do = (활동을) 하다. ① 의문문 조동사 ③ 강조 ④ 충분하다 ⑤ 부정문 조동사
근거: 필기본 3쪽 A-1 (선택지는 새 예문)', 1, 2),
(@naesin_sg_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r04/q02.webp]]', '[[img:/naesin/singu-m2-2026-2/r04/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 대화 내용 일치 (2점)
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
ⓐ like = (전치사) ~처럼. 나머지는 동사 좋아하다·원하다
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
근거: 필기본 3·6·7·8쪽 (상자 문장·정답 선택지는 새 예문)', 38, 2),
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
(@naesin_sg_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r05/q02.webp]]', '[[img:/naesin/singu-m2-2026-2/r05/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화 내용 일치 (2점)
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

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-SGM2-2602-R06', 'NAESIN', '신구중 2학년 2학기 기말 6회 · Lesson 8 예상문제 6회', 'Lesson 8 학습지 필기본 13~22쪽 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_sg_r06 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-SGM2-2602-R06');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q01.webp]]', '[[img:/naesin/singu-m2-2026-2/r06/p01-03.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓔ 의미 (2점)
ⓐ 작가 ⓑ ~로 가장 잘 알려진 ⓒ 어린 시절 ⓓ 독학했다(스스로 공부했다) ⓔ 영향력 있는
근거: 필기본 21쪽 1문단', 1, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q02.webp]]', '[[img:/naesin/singu-m2-2026-2/r06/p01-03.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글 내용 일치 (2점)
he educated himself through books를 바꿔 씀. ⑤ 1920~2012(21세기까지 삶)
근거: 필기본 21쪽 1문단', 2, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q03.webp]]', '[[img:/naesin/singu-m2-2026-2/r06/p01-03.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영어 발문 (2점)
spent much of his childhood reading in libraries를 바꿔 씀
근거: 필기본 21쪽 1문단', 3, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q04.webp]]', '[[img:/naesin/singu-m2-2026-2/r06/p04-06.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · Which is true (2점)
“firemen” burn them을 바꿔 씀. ① 1953 ③ banned ④ begins to question ⑤ a future society
근거: 필기본 21쪽 2문단', 4, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q05.webp]]', '[[img:/naesin/singu-m2-2026-2/r06/p04-06.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영어 발문 (2점)
begins to question this system and seeks knowledge and freedom을 바꿔 씀
근거: 필기본 21쪽 2문단 · 2번(seeks = looks for)', 5, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q06.webp]]', '[[img:/naesin/singu-m2-2026-2/r06/p04-06.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ의 예 (3점)
검열(censorship) = 글·소식을 마음대로 내지 못하게 막는 것. 소설 속 책 금지와 같은 예
근거: 필기본 21쪽 2문단 *censorship: 검열', 6, 3),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r06/q07.webp]]', '[[img:/naesin/singu-m2-2026-2/r06/p07-09.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,D', 1, '정답 ②, ④ · 글 내용 일치 모두 (4점)
② 2004 National Medal of Arts ④ focus on how human choices shape the future를 바꿔 씀. ① 1951 > 1950 ③ 2007
근거: 필기본 21쪽 3문단', 7, 4),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q08.webp]]', '[[img:/naesin/singu-m2-2026-2/r06/p07-09.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ~ 이유로 가장 적절한 것 (2점)
still inspires readers with his imagination
근거: 필기본 21쪽 3문단', 8, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q09.webp]]', '[[img:/naesin/singu-m2-2026-2/r06/p07-09.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영어 발문 (2점)
is remembered as one of the greatest science fiction writers
근거: 필기본 21쪽 3문단', 9, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r06/q10.webp]]', '[[img:/naesin/singu-m2-2026-2/r06/p10-12.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,D', 1, '정답 ①, ④ · 의미와 쓰임이 ⓐ, ⓑ와 같은 것 모두 (5점)
ⓐ mean = 의미하다 ⓑ lead to = (결과로) ~로 이어지다. ② 진심이다·(길이) ~로 통하다 ③ 못된·이끌다 ⑤ (길이) ~로 통하다
근거: 필기본 22쪽 1문단 (선택지는 새 예문)', 10, 5),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q11.webp]]', '[[img:/naesin/singu-m2-2026-2/r06/p10-12.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글 내용 일치 (2점)
meteorologist(기상학자)를 바꿔 씀. ② 1960s ③ small → bigger ④⑤ weather or society are very sensitive
근거: 필기본 22쪽 1문단', 11, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q12.webp]]', '[[img:/naesin/singu-m2-2026-2/r06/p10-12.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 답할 수 없는 것 (4점)
① a small change can cause a bigger change ③ in the 1960s ④ chaos theory ⑤ weather or society
근거: 필기본 22쪽 1문단', 12, 4),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q13.webp]]', '[[img:/naesin/singu-m2-2026-2/r06/p13-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓔ 내용·어법 짝 (4점)
어법 오류: ⓑ lead to meet → lead to meeting(lead to의 to는 전치사) / 내용 오류: ⓒ future(→past) ⓔ 작은 행동도 미래를 만들 수 있다
근거: 필기본 22쪽 2·3문단', 13, 4),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q14.webp]]', '[[img:/naesin/singu-m2-2026-2/r06/p13-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영어 발문 (2점)
the world has changed completely를 바꿔 씀
근거: 필기본 22쪽 2문단', 14, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q15.webp]]', '[[img:/naesin/singu-m2-2026-2/r06/p13-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영어 발문 (2점)
reminds us to think carefully about our decisions
근거: 필기본 22쪽 3문단', 15, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r06/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,C', 1, '정답 ①, ③ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ① for 2010 → since 2010(시작 시점) ③ have seen → saw(last week는 정해진 과거 시점)
근거: 필기본 18·19쪽 현재완료', 16, 4),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q17.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 전환 후 n번째 단어 (5점)
I(1) have(2) studied(3) Economics(4) for(5) a(6) long(7) time(8) — 기간 앞에는 for
근거: 필기본 20쪽 3번 I have studied Economics for quite a long time.', 17, 5),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ~ⓒ 짝 (2점)
since + 시작 시점(then), for + 기간, 정해진 과거 시점(3 years ago)은 단순과거
근거: 필기본 19쪽 · 20쪽 2·7번', 18, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·④ 완료(already/just). ① 계속 ② 경험 ③⑤ 결과
근거: 필기본 18쪽 현재완료 4가지 의미 · 20쪽 4번', 19, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 짝지은 대화 어색 (2점)
Yes라고 답하고 아직 끝내지 않았다(haven’t finished)고 하면 맞지 않음
근거: 필기본 18·20쪽 현재완료', 20, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸 어디에도 없는 단어 (2점)
ever / yet / never / just — ago는 단순과거와 쓴다
근거: 필기본 18쪽 현재완료 같이 다니는 친구들', 21, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸 어디에도 없는 단어 (2점)
appear / breathe / instructions / founder
근거: 필기본 13쪽 Vocabulary 예문', 22, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸 어디에도 없는 단어 (2점)
except / mud / silence / toward
근거: 필기본 13·14쪽 Vocabulary 예문', 23, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸 어디에도 없는 단어 (2점)
unthinkable / dinosaur / cause / president
근거: 필기본 15쪽 Worksheet B', 24, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 어디에도 없는 단어 (2점)
shocked·confused / panicked / whispered (stare는 stare at으로 씀)
근거: 필기본 13·14쪽 Vocabulary 예문', 25, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓒ 짝 (2점)
except(~을 제외하고), stare at(빤히 쳐다보다), 정확한 수 뒤 million에는 -s를 붙이지 않음
근거: 필기본 15쪽 Worksheet C 1·2·4번', 26, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓒ 짝 (2점)
be covered in, at any time, put A in danger
근거: 필기본 13·14쪽 be covered in · at any time · put ~ in danger', 27, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r06/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,D', 1, '정답 ①, ④ · 바꿔 쓸 단어 모두 (3점)
foolish = silly = unwise
근거: 필기본 13쪽 foolish (silly) · 15쪽 A-4 (unwise)', 28, 3),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r06/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,E', 1, '정답 ②, ⑤ · 바꿔 쓸 단어 모두 (2점)
terrified = very frightened = very afraid
근거: 필기본 14쪽 terrified (very frightened) · 15쪽 A-1 (very afraid)', 29, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 그림 <보기> 빈칸 어디에도 없는 것 (3점)
fog / mud / roar, lion
근거: 필기본 14쪽 roar 예문 · 15쪽 C-3·C-5', 30, 3),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 그림 <보기> 빈칸 어디에도 없는 것 (2점)
scared, dog / narrow / dinosaur
근거: 필기본 13·14쪽 예문', 31, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓔ 활용 의미 어색 (5점)
우연히(by accident, in a way that is not planned) 만났는데 몇 주 동안 계획했다는 것은 맞지 않음
근거: 필기본 13·14쪽 뜻풀이 (선택지는 새 예문)', 32, 5),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ와 같은 의미 (2점)
ⓐ present = 현재. ②⑤ 선물 ③ 출석한 ④ 발표하다
근거: 필기본 14쪽 present 예문 (선택지는 새 예문)', 33, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ와 같은 의미 (2점)
ⓐ appear = 나타나다. ②~⑤ ~인 것 같다, ~처럼 보이다
근거: 필기본 13쪽 dinosaur 예문 (선택지는 새 예문)', 34, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ와 같은 의미 (2점)
ⓐ cause = (동사) 일으키다. ①②④ (명사) 원인 ③ (명사) 대의, 목적
근거: 필기본 13쪽 cause 예문 (선택지는 새 예문)', 35, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ와 같은 의미 (2점)
ⓐ spend = (시간을) 보내다. ①~④ (돈을) 쓰다
근거: 필기본 21쪽 1문단 (선택지는 새 예문)', 36, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ와 같은 의미 (2점)
ⓐ over = (수량이) ~ 이상. ① 위로 ② 끝난 ③ 넘어서 ④ 덮어서
근거: 필기본 13쪽 million 예문 (선택지는 새 예문)', 37, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ와 같은 의미 (2점)
ⓐ work = (명사) 연구, 업적. ① 일하다 ② 작동하다 ④ 효과가 있다 ⑤ 직장
근거: 필기본 22쪽 1문단 (선택지는 새 예문)', 38, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ와 같은 의미 (2점)
ⓐ change = (명사) 변화. ② (명사) 거스름돈 ①③⑤ (동사) 바꾸다, 바뀌다
근거: 필기본 22쪽 1문단 (⑤는 14쪽 예문, 나머지는 새 예문)', 39, 2),
(@naesin_sg_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r06/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · Ⓐ 어법상 쓰임 (2점)
Ⓐ·② 목적어절을 이끄는 접속사. ① 지시대명사 ③ 지시형용사 ④ 부사(그렇게) ⑤ 관계대명사
근거: 필기본 22쪽 1문단', 40, 2);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-SGM2-2602-R07', 'NAESIN', '신구중 2학년 2학기 기말 7회 · Lesson 8 예상문제 7회', 'Lesson 8 학습지 필기본 13~22쪽 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_sg_r07 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-SGM2-2602-R07');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q01.webp]]', '[[img:/naesin/singu-m2-2026-2/r07/p01-03.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 답할 수 없는 것 (4점)
① 1920 ② Waukegan, Illinois ③ in libraries ④ through books
근거: 필기본 21쪽 1문단', 1, 4),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q02.webp]]', '[[img:/naesin/singu-m2-2026-2/r07/p01-03.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영어 발문 (2점)
never went to college, but he educated himself through books를 바꿔 씀
근거: 필기본 21쪽 1문단', 2, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q03.webp]]', '[[img:/naesin/singu-m2-2026-2/r07/p01-03.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글 내용 일치 (2점)
① American ② science fiction and fantasy ③ reading in libraries ⑤ 1920~2012
근거: 필기본 21쪽 1문단', 3, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q04.webp]]', '[[img:/naesin/singu-m2-2026-2/r07/p04-06.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓔ 내용·어법 짝 (4점)
어법 오류: ⓓ begin → begins(주어 Guy Montag는 3인칭 단수) / 내용 오류: ⓑ past(→future) ⓔ money(→ideas)
근거: 필기본 21쪽 2문단', 4, 4),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q05.webp]]', '[[img:/naesin/singu-m2-2026-2/r07/p04-06.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영어 발문 (2점)
“firemen” burn them(books)
근거: 필기본 21쪽 2문단', 5, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q06.webp]]', '[[img:/naesin/singu-m2-2026-2/r07/p04-06.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영어 발문 (2점)
seeks knowledge and freedom
근거: 필기본 21쪽 2문단 · 2번(seeks = looks for)', 6, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 전환 후 n번째 단어 (5점)
I(1) have(2) lived(3) in(4) Seoul(5) for(6) five(7) years(8)
근거: 필기본 20쪽 6번 I have lived in Seoul for five years.', 7, 5),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q08.webp]]', '[[img:/naesin/singu-m2-2026-2/r07/p08-10.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ의 예 (3점)
사람의 선택(새 친구를 돕기로 함)이 뒷날(단짝이 됨)을 만든 예
근거: 필기본 21쪽 3문단', 8, 3),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q09.webp]]', '[[img:/naesin/singu-m2-2026-2/r07/p08-10.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · Which is true (2점)
① 1951 ③ 2004 ④ many awards ⑤ is remembered as one of the greatest
근거: 필기본 21쪽 3문단', 9, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q10.webp]]', '[[img:/naesin/singu-m2-2026-2/r07/p08-10.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영어 발문 (2점)
still inspires readers with his imagination
근거: 필기본 21쪽 3문단', 10, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r07/q11.webp]]', '[[img:/naesin/singu-m2-2026-2/r07/p11-13.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,E', 1, '정답 ①, ⑤ · 글 내용 일치 모두 (4점)
① became famous through the work of … Edward Lorenz ⑤ weather … very sensitive to small changes를 바꿔 씀
근거: 필기본 22쪽 1문단', 11, 4),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q12.webp]]', '[[img:/naesin/singu-m2-2026-2/r07/p11-13.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓔ 의미 (2점)
ⓐ 개념 ⓑ 아주 작은 ⓒ 조건 ⓓ 결과 ⓔ 민감한
근거: 필기본 22쪽 1문단', 12, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q13.webp]]', '[[img:/naesin/singu-m2-2026-2/r07/p11-13.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영어 발문 (2점)
tiny differences in starting conditions can lead to very different results over time를 바꿔 씀
근거: 필기본 22쪽 3번 (2)', 13, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r07/q14.webp]]', '[[img:/naesin/singu-m2-2026-2/r07/p14-16.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,D', 1, '정답 ①, ④ · 의미와 쓰임이 ⓐ, ⓑ와 같은 것 모두 (5점)
ⓐ step on = 밟다 ⓑ return = 돌아오다. ② step 명사·return 돌려주다 ③ step 명사(단계) ⑤ return 돌려주다
근거: 필기본 22쪽 2문단 (선택지는 새 예문)', 14, 5),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q15.webp]]', '[[img:/naesin/singu-m2-2026-2/r07/p14-16.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ~ 이유로 가장 적절한 것 (2점)
accidentally steps on a butterfly during a time trip to the past → the world has changed completely
근거: 필기본 22쪽 2문단', 15, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q16.webp]]', '[[img:/naesin/singu-m2-2026-2/r07/p14-16.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글 내용 일치 (2점)
②문장을 바꿔 씀
근거: 필기본 22쪽 2·3문단', 16, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r07/q17.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,E', 1, '정답 ②, ⑤ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ② has been → has gone(가서 지금 없음) ⑤ have finished → finished(two hours ago는 정해진 과거 시점)
근거: 필기본 18~20쪽 현재완료', 17, 4),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓒ 짝 (2점)
have been to(경험), 3인칭 단수 주어 + has p.p., I + have p.p.
근거: 필기본 18쪽 현재완료 형태·4가지 의미', 18, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ~ⓒ 짝 (2점)
last week·3 years ago는 정해진 과거 시점(단순과거), 부정문의 yet(아직)은 현재완료(완료)
근거: 필기본 18·19쪽', 19, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · Ⓐ 어법상 쓰임 (2점)
Ⓐ·② 경험(never, once). ① 계속 ③⑤ 완료 ④ 결과
근거: 필기본 18쪽 현재완료 4가지 의미 · 20쪽 8번', 20, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 짝지은 대화 어색 (2점)
Tom이 왔냐는 물음에 Yes라고 하고 Tom만 빼고 왔다(except Tom)고 하면 맞지 않음
근거: 필기본 13~15쪽 · 18쪽', 21, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 짝지은 대화 어색 (2점)
yesterday는 정해진 과거 시점이라 have lost가 아니라 lost
근거: 필기본 19·20쪽 과거와 현재완료', 22, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 어디에도 없는 단어 (2점)
worked / did / was / has eaten
근거: 필기본 20쪽 2·10·12·13번', 23, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸 어디에도 없는 단어 (2점)
by accident / at any time / million / foolish
근거: 필기본 13쪽 Vocabulary 예문', 24, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸 어디에도 없는 단어 (2점)
suddenly / unthinkable / present / president
근거: 필기본 14쪽 Vocabulary 예문', 25, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸 어디에도 없는 단어 (2점)
founder / roar / silence / shocked
근거: 필기본 13·14쪽 Vocabulary 예문', 26, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸 어디에도 없는 단어 (2점)
covered / stared / confused / toward
근거: 필기본 13·14쪽 Vocabulary 예문', 27, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓒ 짝 (2점)
panicked, breathe(동사), appeared
근거: 필기본 13·14쪽 · 시험 대비(panic → panicked, breathe 동사 / breath 명사)', 28, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ~ⓒ 짝 (2점)
stare at, cause problems for, focus on
근거: 필기본 13·14·15쪽 예문', 29, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r07/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,C', 1, '정답 ②, ③ · 바꿔 쓸 단어 모두 (3점)
appear = come into view = start to be seen
근거: 필기본 13쪽 appear (to start to be seen) · 15쪽 A-2 (to come into view)', 30, 3),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r07/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,D', 1, '정답 ①, ④ · 바꿔 쓸 단어 모두 (2점)
terrified = very frightened = very afraid
근거: 필기본 14쪽 terrified (very frightened) · 15쪽 A-1 (very afraid)', 31, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 그림 <보기> 빈칸 어디에도 없는 것 (3점)
fog / jungle / narrow, path
근거: 필기본 13·14쪽 예문 · 15쪽 C-3', 32, 3),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 그림 <보기> 빈칸 어디에도 없는 것 (2점)
mud / scared, dog / dinosaur
근거: 필기본 13·14쪽 예문 · 15쪽 B-2', 33, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ~ⓔ 활용 의미 어색 (5점)
안개(fog)는 앞을 보기 어렵게 하는데(difficult to see through) 짙어서 산이 잘 보였다는 것은 맞지 않음
근거: 필기본 13·14쪽 뜻풀이 (선택지는 새 예문)', 34, 5),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ와 같은 의미 (2점)
ⓐ character = 등장인물. ①④ 성격·인격 ② 글자 ⑤ 특색
근거: 필기본 22쪽 2문단 (선택지는 새 예문)', 35, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ와 같은 의미 (2점)
ⓐ miss = 놓치다. ②③⑤ 그리워하다 ④ ~ 양(호칭)
근거: 필기본 22쪽 2문단 (선택지는 새 예문)', 36, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ와 같은 의미 (2점)
ⓐ shape = (동사) 형성하다, 만들어 가다. ①②④ (명사) 모양 ③ (몸의) 상태
근거: 필기본 22쪽 3문단 (선택지는 새 예문)', 37, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ와 같은 의미 (2점)
ⓐ clear = 명확한. ① 맑은 ② 치우다 ④ 투명한 ⑤ 막힌 것이 없는
근거: 필기본 13쪽 instruction 예문 (선택지는 새 예문)', 38, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ와 같은 의미 (2점)
ⓐ type = (명사) 종류. 나머지는 (동사) 타자를 치다, 입력하다
근거: 필기본 13쪽 dinosaur 영어 뜻풀이 (선택지는 새 예문)', 39, 2),
(@naesin_sg_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r07/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·④ 목적어절을 이끄는 접속사. ① 지시대명사 ② 지시형용사 ③ 부사(그렇게) ⑤ 관계대명사
근거: 필기본 22쪽 1문단', 40, 2);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-SGM2-2602-R08', 'NAESIN', '신구중 2학년 2학기 기말 8회 · Lesson 8 예상문제 8회', 'Lesson 8 학습지 필기본 13~22쪽 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_sg_r08 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-SGM2-2602-R08');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_sg_r08, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r08/q01.webp]]', '[[img:/naesin/singu-m2-2026-2/r08/p01-03.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,C', 1, '정답 ①, ③ · 의미와 쓰임이 ⓐ, ⓑ와 같은 것 모두 (5점)
ⓐ spend = (시간을) 보내다 ⓑ become = ~이 되다. ② spend (돈을) 쓰다 ④ become 어울리다 ⑤ spend (돈을) 쓰다
근거: 필기본 21쪽 1문단 (선택지는 새 예문)', 1, 5),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q02.webp]]', '[[img:/naesin/singu-m2-2026-2/r08/p01-03.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영어 발문 (2점)
best known for his science fiction and fantasy stories
근거: 필기본 21쪽 1문단', 2, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q03.webp]]', '[[img:/naesin/singu-m2-2026-2/r08/p01-03.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글 내용 일치 (2점)
① 1920~2012 ③ never went to college ④ spent much of his childhood reading in libraries ⑤ one of the most influential writers
근거: 필기본 21쪽 1문단', 3, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r08/q04.webp]]', '[[img:/naesin/singu-m2-2026-2/r08/p04-06.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,E', 1, '정답 ②, ⑤ · 글 내용 일치 모두 (4점)
② books are banned ⑤ seeks knowledge and freedom(seeks = looks for)를 바꿔 씀. ① 1953 ③ burn them ④ ideas
근거: 필기본 21쪽 2문단 · 2번', 4, 4),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q05.webp]]', '[[img:/naesin/singu-m2-2026-2/r08/p04-06.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓔ 의미 (2점)
ⓐ 금지된 ⓑ 사회 ⓒ 주인공 ⓓ 의문을 품다 ⓔ 주제
근거: 필기본 21쪽 2문단', 5, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q06.webp]]', '[[img:/naesin/singu-m2-2026-2/r08/p04-06.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영어 발문 (2점)
The book shows themes of censorship, technology, and the importance of ideas.
근거: 필기본 21쪽 2문단', 6, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q07.webp]]', '[[img:/naesin/singu-m2-2026-2/r08/p07-08.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓔ 내용·어법 짝 (4점)
어법 오류: ⓑ focuses → focus(주어 stories는 복수) / 내용 오류: ⓐ 1955(→1950) ⓔ is remembered as one of the greatest science fiction writers
근거: 필기본 21쪽 3문단', 7, 4),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q08.webp]]', '[[img:/naesin/singu-m2-2026-2/r08/p07-08.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영어 발문 (2점)
the National Medal of Arts in 2004 and a Pulitzer Prize Special Citation in 2007
근거: 필기본 21쪽 3문단', 8, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q09.webp]]', '[[img:/naesin/singu-m2-2026-2/r08/p09-11.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 답할 수 없는 것 (4점)
① a small change can cause a much bigger change later ② Edward Lorenz ③ in the 1960s ④ chaos theory
근거: 필기본 22쪽 1문단', 9, 4),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q10.webp]]', '[[img:/naesin/singu-m2-2026-2/r08/p09-11.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글 내용 일치 (2점)
People often explain it with a butterfly.를 바꿔 씀. ① meteorologist ② 1960s
근거: 필기본 22쪽 1문단', 10, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q11.webp]]', '[[img:/naesin/singu-m2-2026-2/r08/p09-11.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ~ 이유로 가장 적절한 것 (2점)
some systems, such as weather or society, are very sensitive to small changes
근거: 필기본 22쪽 1문단', 11, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r08/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C,D', 1, '정답 ③, ④ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ③ has left → left(an hour ago는 정해진 과거 시점) ④ have visit → have visited(have + 과거분사)
근거: 필기본 17~20쪽 현재완료', 12, 4),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 전환 후 n번째 단어 (5점)
I(1) have(2) worked(3) at(4) this(5) company(6) since(7) 2020(8)
근거: 필기본 18쪽 계속 용법(since + 시작 시점)', 13, 5),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · Ⓐ 어법상 쓰임 (2점)
Ⓐ·① 결과(그 결과 지금 ~하다). ② 계속 ③⑤ 경험 ④ 완료
근거: 필기본 17·18쪽 현재완료 결과', 14, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q15.webp]]', '[[img:/naesin/singu-m2-2026-2/r08/p15-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영어 발문 (2점)
When he returns, the world has changed completely.
근거: 필기본 22쪽 2문단', 15, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q16.webp]]', '[[img:/naesin/singu-m2-2026-2/r08/p15-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영어 발문 (2점)
might lead to meeting a new friend or missing an important event
근거: 필기본 22쪽 2문단', 16, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q17.webp]]', '[[img:/naesin/singu-m2-2026-2/r08/p15-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글 내용 일치 (2점)
This shows how even a tiny action can have unthinkable consequences.를 바꿔 씀
근거: 필기본 22쪽 2문단', 17, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q18.webp]]', '[[img:/naesin/singu-m2-2026-2/r08/p18-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ의 예 (3점)
작은 행동(small actions)의 예. 나머지는 큰 일
근거: 필기본 22쪽 3문단', 18, 3),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q19.webp]]', '[[img:/naesin/singu-m2-2026-2/r08/p18-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글 내용 일치 (2점)
they can shape the future in surprising ways를 바꿔 씀
근거: 필기본 22쪽 3문단', 19, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ~ⓒ 짝 (2점)
3인칭 단수 주어 + has p.p., I + have p.p., 현재완료 의문문은 Have + 주어 + p.p.?
근거: 필기본 18쪽 현재완료 형태(긍정문·의문문)', 20, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓒ 짝 (2점)
for + 기간, since + 시작 시점, already(이미)는 완료
근거: 필기본 18쪽 · 20쪽 7번', 21, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓒ 짝 (2점)
정확한 수(two, a) 뒤에는 million, 막연한 수량은 millions of
근거: 필기본 13쪽 million · 15쪽 C-4 · 시험 대비', 22, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 짝지은 대화 어색 (2점)
얼마나 오래 살아 왔는지(계속) 묻는데 2년 전에 살았다고 답함
근거: 필기본 18~20쪽 현재완료', 23, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 짝지은 대화 어색 (2점)
속삭이는 것(whisper)은 가까운 사람만 듣게 아주 작게 말하는 것이라 모두가 듣기를 원했다는 이유와 맞지 않음
근거: 필기본 13·14쪽 뜻풀이', 24, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸 어디에도 없는 단어 (2점)
lost / have met / did / have lived
근거: 필기본 20쪽 1·6·9·12번', 25, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸 어디에도 없는 단어 (2점)
president / unthinkable / fog / mud
근거: 필기본 13·14쪽 Vocabulary 예문', 26, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 어디에도 없는 단어 (2점)
panicked / causing / toward / suddenly
근거: 필기본 13·14쪽 Vocabulary 예문', 27, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸 어디에도 없는 단어 (2점)
stare / except / shocked / terrified
근거: 필기본 14쪽 · 15쪽 Worksheet C', 28, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ~ⓒ 짝 (2점)
소유격(His, The … of) 뒤에는 명사: silence(침묵), instructions(지시), founder(설립자)
근거: 필기본 13·14쪽 Vocabulary 예문', 29, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓒ 짝 (2점)
be covered in, by accident, focus on
근거: 필기본 13·14쪽 be covered in · by accident · present 예문', 30, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r08/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,E', 1, '정답 ②, ⑤ · 바꿔 쓸 단어 모두 (3점)
discover = find (out)
근거: 필기본 22쪽 1문단 · 3번 (2) What did … find out? → He found that…', 31, 3),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r08/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,D', 1, '정답 ①, ④ · 바꿔 쓸 단어 모두 (2점)
accidentally = by accident = in a way that is not planned
근거: 필기본 13쪽 by accident (in a way that is not planned) · 22쪽 2문단', 32, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 그림 <보기> 빈칸 어디에도 없는 것 (3점)
roar, lion / jungle / scared
근거: 필기본 13·14쪽 예문', 33, 3),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 그림 <보기> 빈칸 어디에도 없는 것 (2점)
appear / dinosaur / narrow, path
근거: 필기본 13·14쪽 예문', 34, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ~ⓔ 활용 의미 어색 (5점)
stare는 오랫동안(for a long time) 빤히 보는 것이라 단 1초 동안이라는 것과 맞지 않음
근거: 필기본 13·14쪽 뜻풀이 (선택지는 새 예문)', 35, 5),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ와 같은 의미 (2점)
ⓐ state = (명사) 상태. ②③ 주 ④⑤ 말하다, 진술하다
근거: 필기본 14쪽 silence 영어 뜻풀이 (선택지는 새 예문)', 36, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ와 같은 의미 (2점)
ⓐ last = 마지막의. ①③ 계속되다 ②④ 지난
근거: 필기본 20쪽 13번 (선택지는 새 예문)', 37, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ와 같은 의미 (2점)
ⓐ play = 경기하다, 운동하다. ① 연주하다 ③ 연극 ④ 역을 맡다 ⑤ 틀다
근거: 필기본 15쪽 C-5 (선택지는 새 예문)', 38, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ와 같은 의미 (2점)
ⓐ novel = (명사) 소설. 나머지는 (형용사) 새로운
근거: 필기본 21쪽 2문단 (선택지는 새 예문)', 39, 2),
(@naesin_sg_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r08/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·③ 목적어절을 이끄는 접속사. ① 지시대명사 ② 지시형용사 ④ 부사(그렇게) ⑤ 관계대명사
근거: 필기본 22쪽 2문단', 40, 2);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-SGM2-2602-R09', 'NAESIN', '신구중 2학년 2학기 기말 9회 · Lesson 8 예상문제 9회', 'Lesson 8 학습지 필기본 13~22쪽 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_sg_r09 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-SGM2-2602-R09');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q01.webp]]', '[[img:/naesin/singu-m2-2026-2/r09/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓔ 내용·어법 짝 (4점)
어법 오류: ⓒ is born → was born(과거의 일) / 내용 오류: ⓐ British(→American) ⓓ playing sports(→reading in libraries)
근거: 필기본 21쪽 1문단', 1, 4),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q02.webp]]', '[[img:/naesin/singu-m2-2026-2/r09/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영어 발문 (2점)
he educated himself through books를 바꿔 씀
근거: 필기본 21쪽 1문단', 2, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q03.webp]]', '[[img:/naesin/singu-m2-2026-2/r09/p03-05.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 답할 수 없는 것 (4점)
① 1953 ② burn books ④ Guy Montag ⑤ knowledge and freedom
근거: 필기본 21쪽 2문단', 3, 4),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q04.webp]]', '[[img:/naesin/singu-m2-2026-2/r09/p03-05.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영어 발문 (2점)
begins to question this system
근거: 필기본 21쪽 2문단', 4, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q05.webp]]', '[[img:/naesin/singu-m2-2026-2/r09/p03-05.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · Which is true (2점)
books are banned를 바꿔 씀
근거: 필기본 21쪽 2문단', 5, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r09/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,D', 1, '정답 ②, ④ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ② have lost → lost(last Saturday는 정해진 과거 시점) ④ readed → read(read의 과거분사는 read)
근거: 필기본 17~20쪽 현재완료', 6, 4),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r09/q07.webp]]', '[[img:/naesin/singu-m2-2026-2/r09/p07-09.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,E', 1, '정답 ②, ⑤ · 의미와 쓰임이 ⓐ, ⓑ와 같은 것 모두 (5점)
ⓐ include = 포함하다 ⓑ be remembered as = ~로 기억되다(수동). ① remember to ~ 잊지 않고 ~하다 ③ remember 기억해 내다(능동) ④ include 넣다·remember 안부를 전하다
근거: 필기본 21쪽 3문단 (선택지는 새 예문)', 7, 5),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q08.webp]]', '[[img:/naesin/singu-m2-2026-2/r09/p07-09.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영어 발문 (2점)
focus on how human choices shape the future
근거: 필기본 21쪽 3문단', 8, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q09.webp]]', '[[img:/naesin/singu-m2-2026-2/r09/p07-09.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글 내용 일치 (2점)
1950 → 1951
근거: 필기본 21쪽 3문단', 9, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q10.webp]]', '[[img:/naesin/singu-m2-2026-2/r09/p10-12.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ~ⓔ 의미 (2점)
ⓐ 설명하다 ⓑ 유명한 ⓒ 발견했다 ⓓ 차이 ⓔ 시간이 지나면서
근거: 필기본 22쪽 1문단', 10, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q11.webp]]', '[[img:/naesin/singu-m2-2026-2/r09/p10-12.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영어 발문 (2점)
It means that a very small change can cause a much bigger change later.
근거: 필기본 22쪽 3번 (1)', 11, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q12.webp]]', '[[img:/naesin/singu-m2-2026-2/r09/p10-12.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · Which is true (2점)
some systems, such as weather or society, are very sensitive to small changes
근거: 필기본 22쪽 1문단', 12, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r09/q13.webp]]', '[[img:/naesin/singu-m2-2026-2/r09/p13-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C,D', 1, '정답 ③, ④ · 글 내용 일치 모두 (4점)
③ a time trip to the past ④ ②문장을 바꿔 씀. ① accidentally ② has changed completely ⑤ unthinkable consequences
근거: 필기본 22쪽 2문단', 13, 4),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q14.webp]]', '[[img:/naesin/singu-m2-2026-2/r09/p13-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ의 예 (3점)
하나의 작은 선택(one small choice)의 예. 나머지는 큰 일
근거: 필기본 22쪽 2문단', 14, 3),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q15.webp]]', '[[img:/naesin/singu-m2-2026-2/r09/p13-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영어 발문 (2점)
accidentally steps on a butterfly
근거: 필기본 22쪽 2문단', 15, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q16.webp]]', '[[img:/naesin/singu-m2-2026-2/r09/p16-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ~ 이유로 가장 적절한 것 (2점)
Small actions … can shape the future in surprising ways.
근거: 필기본 22쪽 3문단', 16, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q17.webp]]', '[[img:/naesin/singu-m2-2026-2/r09/p16-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글 내용 일치 (2점)
Small actions may seem unimportant
근거: 필기본 22쪽 3문단', 17, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 전환 후 n번째 단어 (5점)
My(1) little(2) brother(3) has(4) eaten(5) up(6) …
근거: 필기본 17쪽 예3 My little brother has eaten up the last piece of my favorite cake.', 18, 5),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓒ 짝 (2점)
When은 정해진 때를 묻는 말이라 단순과거(did), 경험·완료를 묻는 현재완료 의문문은 Have/Has + 주어 + p.p.?
근거: 필기본 18쪽 의문문 · 20쪽 12번', 19, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ~ⓒ 짝 (2점)
since + 시작 시점, for + 기간, 정해진 과거 시점 last week는 단순과거
근거: 필기본 18·19쪽', 20, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓒ 짝 (2점)
to·can 뒤에는 동사 breathe, a deep 뒤에는 명사 breath
근거: 필기본 13쪽 breathe · 15쪽 시험 대비(breathe 동사, breath 명사)', 21, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·④ 계속(for + 기간). ① 경험 ② 완료 ③⑤ 결과
근거: 필기본 18쪽 현재완료 계속', 22, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 짝지은 대화 어색 (2점)
가 본 적이 없다(No)고 하면서 세 번 가 보았다고 답함
근거: 필기본 18~20쪽 현재완료', 23, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 짝지은 대화 어색 (2점)
confused는 분명하게 이해하지 못하는(unable to understand) 상태라 다 분명히 이해한다는 이유와 맞지 않음
근거: 필기본 13·14쪽 뜻풀이', 24, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸 어디에도 없는 단어 (2점)
have met / worked / was / lost — in my late twenties는 끝난 과거라 have worked는 쓸 수 없음
근거: 필기본 20쪽 1·2·9·10번', 25, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸 어디에도 없는 단어 (2점)
path / founder / instructions / at any time
근거: 필기본 13·14쪽 Vocabulary 예문', 26, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸 어디에도 없는 단어 (2점)
dinosaur / mud / jungle / silence
근거: 필기본 13·14쪽 Vocabulary 예문', 27, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 어디에도 없는 단어 (2점)
shocked / panicked / confused / except
근거: 필기본 13·14쪽 Vocabulary 예문', 28, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸 어디에도 없는 단어 (2점)
president / cause / dinosaur / unthinkable
근거: 필기본 15쪽 Worksheet B', 29, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓒ 짝 (2점)
동사(changed)를 꾸미는 부사 suddenly, 상상도 할 수 없는 unthinkable, 겁이 난 terrified
근거: 필기본 14쪽 Vocabulary 예문', 30, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ~ⓒ 짝 (2점)
be covered in, focus on, stare at
근거: 필기본 13·14쪽 · 15쪽 C-1', 31, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r09/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,C', 1, '정답 ②, ③ · 바꿔 쓸 단어 모두 (3점)
foolish = silly = unwise
근거: 필기본 13쪽 foolish (silly) · 15쪽 A-4 (unwise)', 32, 3),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r09/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,E', 1, '정답 ①, ⑤ · 바꿔 쓸 단어 모두 (2점)
unthinkable = impossible to imagine or accept
근거: 필기본 14쪽 unthinkable (impossible to imagine or accept) · 22쪽 2문단', 33, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 그림 <보기> 빈칸 어디에도 없는 것 (3점)
fog / scared, dog / roar
근거: 필기본 14쪽 예문 · 15쪽 C-3', 34, 3),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 그림 <보기> 빈칸 어디에도 없는 것 (2점)
toward / stared, for / whispered
근거: 필기본 14쪽 toward·stare·whisper 예문', 35, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓔ 활용 의미 어색 (5점)
founder는 회사를 세운 사람(a person who starts an organization)이라 지난주에 새로 들어온 직원이라는 것과 맞지 않음
근거: 필기본 13·14쪽 뜻풀이 (선택지는 새 예문)', 36, 5),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ와 같은 의미 (2점)
ⓐ question = (동사) 의문을 품다. ①②③ (명사) 질문 ④ (명사) 문제
근거: 필기본 21쪽 2문단 (선택지는 새 예문)', 37, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ와 같은 의미 (2점)
ⓐ show = (동사) 보여 주다. ②~⑤ (명사) 쇼, 프로그램, 공연
근거: 필기본 21쪽 2문단 (선택지는 새 예문)', 38, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ와 같은 의미 (2점)
ⓐ close = (형용사) 가까운. 나머지는 (동사) 닫다, 문을 닫다
근거: 필기본 14쪽 whisper 영어 뜻풀이 (선택지는 새 예문)', 39, 2),
(@naesin_sg_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r09/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·⑤ 목적어절을 이끄는 접속사. ① 지시대명사 ② 지시형용사 ③ 부사(그렇게) ④ 관계대명사
근거: 필기본 22쪽 1문단', 40, 2);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-SGM2-2602-R10', 'NAESIN', '신구중 2학년 2학기 기말 10회 · Lesson 8 예상문제 10회', 'Lesson 8 학습지 필기본 13~22쪽 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_sg_r10 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-SGM2-2602-R10');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_sg_r10, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r10/q01.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p01-03.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,D', 1, '정답 ①, ④ · 글 내용 일치 모두 (4점)
① an American author ④ never went to college. ② Waukegan, Illinois ③ reading in libraries ⑤ 1920~2012
근거: 필기본 21쪽 1문단', 1, 4),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q02.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p01-03.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ의 예 (3점)
공상 과학 소설(science fiction)의 예: 미래·우주를 상상해 쓴 이야기
근거: 필기본 21쪽 1문단·3문단(The Martian Chronicles)', 2, 3),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q03.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p01-03.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ~ 이유로 가장 적절한 것 (2점)
never went to college, but he educated himself through books and became …
근거: 필기본 21쪽 1문단', 3, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q04.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p04-06.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · Which is true (2점)
begins to question this system을 바꿔 씀
근거: 필기본 21쪽 2문단', 4, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q05.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p04-06.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영어 발문 (2점)
books are banned in a future society
근거: 필기본 21쪽 2문단', 5, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q06.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p04-06.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영어 발문 (2점)
seeks knowledge and freedom
근거: 필기본 21쪽 2문단', 6, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q07.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p07-09.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 답할 수 없는 것 (4점)
① The Martian Chronicles, The Illustrated Man ② in 2004 ③ how human choices shape the future ⑤ one of the greatest science fiction writers (상 개수는 many로만 나옴)
근거: 필기본 21쪽 3문단', 7, 4),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q08.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p07-09.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글 내용 일치 (2점)
a Pulitzer Prize Special Citation in 2007
근거: 필기본 21쪽 3문단', 8, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q09.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p07-09.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영어 발문 (2점)
still inspires readers with his imagination
근거: 필기본 21쪽 3문단', 9, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q10.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p10-12.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓔ 내용·어법 짝 (4점)
어법 오류: ⓒ discover → discovered(과거의 일, 주어 He) / 내용 오류: ⓐ 작은 변화가 큰 변화를 일으킴 ⓔ very sensitive to small changes
근거: 필기본 22쪽 1문단', 10, 4),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q11.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p10-12.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영어 발문 (2점)
in the 1960s
근거: 필기본 22쪽 1문단', 11, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q12.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p10-12.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영어 발문 (2점)
meteorologist Edward Lorenz
근거: 필기본 22쪽 1문단 *meteorologist: 기상학자', 12, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 전환 후 n번째 단어 (5점)
I(1) have(2) known(3) her(4) since(5) 2010(6)
근거: 필기본 18쪽 I have known her since 2010.', 13, 5),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q14.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p14-16.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영어 발문 (2점)
the main character, Eckels
근거: 필기본 22쪽 2문단', 14, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q15.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p14-16.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영어 발문 (2점)
This shows how even a tiny action can have unthinkable consequences.
근거: 필기본 22쪽 2문단', 15, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q16.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p14-16.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글 내용 일치 (2점)
①문장 그대로의 뜻
근거: 필기본 22쪽 2문단', 16, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r10/q17.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p17-18.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,C', 1, '정답 ①, ③ · 의미와 쓰임이 ⓐ, ⓑ와 같은 것 모두 (5점)
ⓐ remind A to ~ = A에게 ~할 것을 일깨우다 ⓑ seem + 형용사 = ~처럼 보이다. ②④ remind A of B(생각나게 하다) ⑤ It seems that ~ 구문
근거: 필기본 22쪽 3문단 (선택지는 새 예문)', 17, 5),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q18.webp]]', '[[img:/naesin/singu-m2-2026-2/r10/p17-18.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영어 발문 (2점)
reminds us to think carefully about our decisions
근거: 필기본 22쪽 3문단', 18, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r10/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,E', 1, '정답 ①, ⑤ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ① have seen → saw(yesterday는 정해진 과거 시점) ⑤ for two years ago → for two years(현재완료는 ago와 쓰지 않음)
근거: 필기본 17~20쪽 현재완료', 19, 4),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓒ 짝 (2점)
have been to = 가 본 적이 있다(경험), have gone to = 가서 지금 없다(결과)
근거: 필기본 18쪽 시험 대비', 20, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓒ 짝 (2점)
two hours ago(단순과거), 부정문의 yet(아직), just(방금)는 현재완료(완료)
근거: 필기본 18쪽 · 20쪽 4·5번', 21, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · Ⓐ 어법상 쓰임 (2점)
Ⓐ·① 경험(once, twice). ② 계속 ③ 완료 ④⑤ 결과
근거: 필기본 18쪽 · 20쪽 1번', 22, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 짝지은 대화 어색 (2점)
벌써 끝냈냐는 물음에 Yes라고 하고 내일 끝내겠다고 답함
근거: 필기본 18~20쪽 현재완료', 23, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 짝지은 대화 어색 (2점)
무서워한다(scared, feeling afraid)고 하면서 함께 놀고 싶다고 답함
근거: 필기본 13·14쪽 뜻풀이', 24, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸 어디에도 없는 단어 (2점)
have studied / did / have lived / lost
근거: 필기본 20쪽 3·6·9·12번', 25, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸 어디에도 없는 단어 (2점)
appear / breathe / stare / panicked
근거: 필기본 13·14쪽 · 15쪽 C-1', 26, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸 어디에도 없는 단어 (2점)
jungle / million / president / founder
근거: 필기본 13·14쪽 Vocabulary 예문', 27, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸 어디에도 없는 단어 (2점)
toward / except / suddenly / unthinkable
근거: 필기본 13·14쪽 Vocabulary 예문', 28, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 어디에도 없는 단어 (2점)
shocked / scared / silence / causing
근거: 필기본 13·14쪽 Vocabulary 예문', 29, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ~ⓒ 짝 (2점)
be covered in(~로 덮이다), 사람이 혼란스러워하는 confused, 지시 instructions(명사)
근거: 필기본 13쪽 Vocabulary 예문', 30, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓒ 짝 (2점)
by accident, at any time, put A in danger
근거: 필기본 13·14쪽 by accident · at any time · put ~ in danger', 31, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r10/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,D', 1, '정답 ①, ④ · 바꿔 쓸 단어 모두 (3점)
terrified = very frightened = very afraid
근거: 필기본 14쪽 terrified (very frightened) · 15쪽 A-1 (very afraid)', 32, 3),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r10/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,E', 1, '정답 ②, ⑤ · 바꿔 쓸 단어 모두 (2점)
foolish = silly = unwise
근거: 필기본 13쪽 foolish (silly) · 15쪽 A-4 (unwise)', 33, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 그림 <보기> 빈칸 어디에도 없는 것 (3점)
narrow, path / mud / dinosaur
근거: 필기본 13·14쪽 예문', 34, 3),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ~ⓔ 활용 의미 어색 (5점)
whisper는 가까운 사람만 들을 수 있게 아주 작게(very quietly) 말하는 것이라 크게, 모두가 듣게 했다는 것과 맞지 않음
근거: 필기본 13·14쪽 뜻풀이 (선택지는 새 예문)', 35, 5),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ와 같은 의미 (2점)
ⓐ sound = (명사) 소리. 나머지는 (동사) ~하게 들리다
근거: 필기본 14쪽 roar 영어 뜻풀이 (선택지는 새 예문)', 36, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ와 같은 의미 (2점)
ⓐ land = (동사) 착륙하다. 나머지는 (명사) 땅, 나라
근거: 필기본 13쪽 fog 예문 (선택지는 새 예문)', 37, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ와 같은 의미 (2점)
ⓐ well = (부사) 잘. ①④ 건강한 ② 우물 ③ 글쎄
근거: 필기본 21쪽 3문단 (선택지는 새 예문)', 38, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ와 같은 의미 (2점)
ⓐ result = (명사) 결과. ②~⑤ (동사) ~의 결과로 생기다, ~로 끝나다
근거: 필기본 22쪽 3번 (2) (선택지는 새 예문)', 39, 2),
(@naesin_sg_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r10/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·③ 목적어절을 이끄는 접속사. ① 지시대명사 ② 지시형용사 ④ 부사(그렇게) ⑤ 관계대명사
근거: 필기본 22쪽 2문단', 40, 2);
