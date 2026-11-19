-- 내신 실전 연습(/naesintest) — 신구중 2학년 2026학년도 2학기 기말 대비 F2R 예상문제 11회(Lesson 7·8 종합 Final).
-- 원본: F2R이 만든 예상문제 문제지 PDF(7쪽)와 정답·해설. V1120(1~10회)과 같은 방식으로 문항 그림(static/naesin/singu-m2-2026-2/r11)을 쓴다.
-- 출제 범위: 2학기말 학습지 필기본 Lesson 7·8 전 범위. 40문항, 배점 합계 100점(5점×3, 4점×5, 3점×1, 2점×31).
-- 한 개 고르기 NAESIN_MC5(A~E), "모두 고르면" NAESIN_MC5_MULTI(answer_key 'B,D' 형식).

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-SGM2-2602-R11', 'NAESIN', '신구중 2학년 2학기 기말 11회 · Lesson 7·8 종합 Final 예상문제 1회', 'Lesson 7·8 학습지 필기본 전 범위 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_sg_r11 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-SGM2-2602-R11');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q01.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화 내용 일치 (2점)
What a surprise!(뜻밖의 만남)를 바꿔 씀. ② Don’t forget Korea. ③ 7 p.m. ④ boat ride가 더 재미있다고 함 ⑤ let’s go(함께)
근거: 필기본 3쪽 B', 1, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q02.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영어 발문 이유 (2점)
I think it’ll be more fun.을 바꿔 씀
근거: 필기본 3쪽 B', 2, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q03.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p03-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ와 같은 의미 (2점)
ⓐ·① 동사 ‘좋아하다’. ②③④⑤ 전치사 ‘~처럼, ~ 같은’
근거: 필기본 4·11쪽 (③④⑤는 새 예문)', 3, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q04.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p03-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영어 발문 이유 (2점)
yellow is my favorite color를 바꿔 씀
근거: 필기본 3쪽 A-4', 4, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 짝지은 대화 어색 (2점)
winter/summer를 묻는데 They are cute.(필기본 Speaking Practice의 오답 선택지)
근거: 필기본 3·4쪽', 5, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q06.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p06-07.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ와 같은 의미 (2점)
ⓐ·② ~해 보이다(+형용사). ①④ 보다 ③ look for 찾다 ⑤ look after 돌보다
근거: 필기본 3쪽 A-2 · 13쪽 confused 예문 (①③⑤는 새 예문)', 6, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q07.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p06-07.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 대화 내용 일치 (2점)
I like tteokbokki much better.를 바꿔 씀
근거: 필기본 3쪽 A-2', 7, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸 어디에도 없는 단어 (2점)
like / play / wash / go
근거: 필기본 3쪽 A-1', 8, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 그림+지각동사 (2점)
어법 오류: ① to decorate ④ called / 내용 오류: ② 부른 사람은 Hana ⑤ Daho는 불꽃놀이를 봄
근거: 필기본 7쪽 see/hear + 목적어 + 동사원형/-ing', 9, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r11/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,D', 1, '정답 ②, ④ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ② getting → to get(진주어는 to부정사) ④ of you → for you(easy는 성격 형용사가 아님)
근거: 필기본 5·6·8쪽 가주어 It', 10, 4),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ~ⓒ 짝짓기 표 (2점)
make·have + 목적어 + 동사원형, want + 목적어 + to부정사
근거: 필기본 7쪽 7번 (1)(3)(6)', 11, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q12.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p12-14.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · Ⓐ 어법상 쓰임 (2점)
Ⓐ·② 주어 역할의 동명사(단수 취급 → symbolizes / takes). ①④ 목적격 보어(현재분사) ③⑤ 진행형
근거: 필기본 9쪽 ⑦ Grammar Point · 5·7·12·13쪽 예문', 12, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q13.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p12-14.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영영 풀이 찾을 수 없는 것 (4점)
① celebrate ② fireworks ④ statue ⑤ symbolize ③ decorate(윗글에 없음)
근거: 필기본 1~2쪽 Word List', 13, 4),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q14.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p12-14.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓔ 활용 의미 어색 (5점)
giant(very large)한 손인데 작은 장난감도 못 쥔다는 것은 뜻이 맞지 않음. 낱말 뜻(거대한)을 알아야 어색함이 보이게 함
근거: 필기본 1~2쪽 Word List 뜻풀이 (선택지는 기출 12처럼 새 예문)', 14, 5),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q15.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p15-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓔ 내용·어법 짝 (4점)
내용 오류: ⓐ short and bright(→long and dark) / 어법 오류: ⓓ end(→ends, starts와 병렬·주어 The festival) ⓔ set(→sets, 주어 the sun)
근거: 필기본 10쪽 ⑧~⑬', 15, 4),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q16.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p15-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ~ 이유로 가장 적절한 것 (2점)
the sun hardly sets를 바꿔 씀(오답은 ⓐ~ⓔ의 오류 부분을 드러내지 않게 구성)
근거: 필기본 10쪽 ⑬ More Questions 2', 16, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q17.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p15-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · Which is true (2점)
people decorate a tall pole with flowers and green leaves를 바꿔 씀
근거: 필기본 10쪽 ⑫', 17, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 전환 후 n번째 단어 (5점)
It(1) is(2) important(3) for(4) us(5) to(6) wear(7) a(8) helmet(9)
근거: 필기본 6쪽 It is important for us to wear a helmet.', 18, 5),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r11/q19.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p19-21.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,C', 1, '정답 ①, ③ · 의미와 쓰임이 ⓐ, ⓑ와 같은 것 모두 (5점)
ⓐ hold = (행사를) 열다, ⓑ set up = 차리다·설치하다. ② hold 잡다 ④ hold 안다·set up 설립하다 ⑤ hold 잡다
근거: 필기본 11쪽 ⑯⑱ (선택지는 새 예문)', 19, 5),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r11/q20.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p19-21.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,E', 1, '정답 ②, ⑤ · 글 내용 일치 모두 (4점)
② ⑭, ⑤ ⑰을 바꿔 씀. ① November 1 and 2 ③ 제단은 집에(in their homes) ④ In some areas
근거: 필기본 11쪽 ⑭~⑲', 20, 4),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q21.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p19-21.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 답할 수 없는 것 (2점)
② the Day of the Dead ③ November 1 and 2 ④ in cemeteries ⑤ skeletons
근거: 필기본 11쪽', 21, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q22.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p22-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓔ 의미 (2점)
ⓐ ~의 끝에(말에) ⓒ ~와 같은 ⓓ 달리려고 애쓰다(try + 동명사는 시험 삼아 ~해 보다) ⓔ 문제가 되지 않는다
근거: 필기본 12쪽 Grammar Point', 22, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q23.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p22-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · Ⓐ 어법상 쓰임 (2점)
Ⓐ·① 가주어(진주어 to do so / to learn a new language). ③⑤ 지시대명사 ②④ 비인칭주어
근거: 필기본 6·8쪽 It의 쓰임', 23, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q24.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p22-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글 내용 일치 (2점)
wear fun costumes를 바꿔 씀. ① At the end of ② traditional games도 ③ just having fun ④ Not really
근거: 필기본 12쪽 ㉓', 24, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 그림 <보기> 빈칸 어디에도 없는 것 (2점)
toward / stared, for / whispered
근거: 필기본 14쪽 toward·stare·whisper 예문', 25, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q26.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p26-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글 내용 일치 (2점)
best known for his science fiction and fantasy stories를 바꿔 씀
근거: 필기본 21쪽 1문단', 26, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q27.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p26-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영어 발문 (2점)
he educated himself through books를 바꿔 씀
근거: 필기본 21쪽 1문단', 27, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r11/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,E', 1, '정답 ②, ⑤ · 문장 전환 옳지 않은 것 모두 (4점)
어법 오류: ② has been → has gone(가서 지금 없음: 결과) ⑤ have learned → learned(3 years ago는 정해진 과거 시점)
근거: 필기본 17~19쪽 현재완료', 28, 4),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ~ⓒ 짝짓기 표 (2점)
since + 시작 시점(then), for + 기간, 정해진 과거 시점(two hours ago)에는 단순과거
근거: 필기본 18쪽·20쪽 2·5·7번', 29, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r11/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,E', 1, '정답 ②, ⑤ · 바꿔 쓸 단어 모두 (3점)
scared: feeling afraid or worried / terrified: very frightened(very afraid)
근거: 필기본 14쪽 23 scared, 28 terrified', 30, 3),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q31.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p31-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · Which is true (2점)
books are banned를 바꿔 씀. ① 1953 ③ Fahrenheit 451 ④ 2007 ⑤ begins to question
근거: 필기본 21쪽 2·3문단', 31, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q32.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p31-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓑ의 예 (2점)
인간의 선택이 미래를 만드는 이야기 — 22쪽 A Sound of Thunder
근거: 필기본 21·22쪽', 32, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q33.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p31-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ~ⓔ 의미 (2점)
ⓐ 가장 유명한 ⓑ 의문을 품기 시작한다 ⓒ 주제 ⓓ 잘 알려진 ⓔ 영감을 준다
근거: 필기본 21쪽 2·3문단', 33, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q34.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p34-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ와 같은 의미 (2점)
ⓐ·⑤ 명사 ‘변화’. ② 명사 ‘거스름돈’ ①③④ 동사 ‘바꾸다, 바뀌다’
근거: 필기본 22쪽 (③은 14쪽 예문, 나머지는 새 예문)', 34, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q35.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p34-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영어 발문 (2점)
tiny differences in starting conditions can lead to very different results over time를 바꿔 씀
근거: 필기본 22쪽 3번 (2)', 35, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q36.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p34-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글 내용 일치 (2점)
meteorologist(기상학자)를 바꿔 씀. ① small → bigger ② part of ③ very sensitive to small changes ⑤ 1960s
근거: 필기본 22쪽 1문단', 36, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q37.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p37-39.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓒ 전치사 짝 (2점)
lead to + -ing(meeting/missing), step on, think about
근거: 필기본 22쪽 2·3문단 · 시험 대비(lead to의 to는 전치사)', 37, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q38.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p37-39.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·④ 결과(그 결과 지금 ~하다). ①⑤ 계속 ②③ 경험 (완료 용법 선택지는 넣지 않음)
근거: 필기본 18쪽 현재완료 4가지 의미', 38, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q39.webp]]', '[[img:/naesin/singu-m2-2026-2/r11/p37-39.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ~ 이유로 가장 적절한 것 (2점)
Small actions … can shape the future in surprising ways를 바꿔 씀
근거: 필기본 22쪽 3문단', 39, 2),
(@naesin_sg_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r11/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸 어디에도 없는 단어 (2점)
stare / except / fog / million — 정확한 수(two) 뒤 million에는 -s를 붙이지 않음
근거: 필기본 15쪽 C', 40, 2);
