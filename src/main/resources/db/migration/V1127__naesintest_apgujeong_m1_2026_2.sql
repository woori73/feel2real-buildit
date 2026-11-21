-- 내신 실전 연습(/naesintest) — 압구정중 1학년 2026학년도 2학기 기말 대비 F2R 예상문제 1~7회.
-- 원본: F2R이 만든 예상문제 문제지 PDF(회당 6쪽)와 정답지. 문항은 원본 문제지를 문항 단위로 자른 그림으로 보여 준다
--   (밑줄·<보 기> 상자·(A)~(E)·ⓐ~ⓔ·Ⓐ~Ⓔ 표시를 원본 그대로 보이게 하려는 것). 그림: static/naesin/apgujeong-m1-2026-2/rNN/qNN.webp,
--   여러 문항이 같이 쓰는 지문·대화는 pAA-BB.webp(body_text).
-- 출제 범위: 교과서(천재 소영순 1학년) Lesson 3~5 본문·대화문과 2학기말 학습지 필기본. 발문·선택지·기호는 출제 교사의 2024 3학년 기말 기출을 따랐다.
-- 회당 40문항, 배점 합계 100점(2점×25, 3점×10, 4점×5). 정답지와 문항 그림의 번호가 같음을 확인했다. 정답 번호는 ①~⑤ 각 8개.
-- kind='NAESIN', section='EN', task_type='NAESIN_MC5'(①~⑤ → answer_key A~E). "있는 대로 고른 것" 문항도 선택지 하나를 고르는 형식이다.
-- 시험 화면은 풀이 중 해설을 보여 주지 않고, 제출 후 복습 화면에서 explanation을 보여 준다. 서버 마감 7200초.

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM1-2602-R01', 'NAESIN', '압구정중 1학년 2학기 기말 1회 · Lesson 3~5 예상문제 1회', 'Lesson 3~5 교과서·학습지 필기본 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_am1_r01 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM1-2602-R01');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q01.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · (A)~(F)중 어법상 올바른 문장을 있는 대로 고른 것은? (3점)
(C) raiseing → raising(-e 삭제) / (D) lieing → lying(-ie → y+ing) / (F) is it do → is it doing
근거: L3 본문 20~29', 1, 3),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q02.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 윗글에서 찾을 수 없는 단어의 뜻은?(단, 단어의 단·복수 및 동사의 변형은 상관없음.) (3점)
bow는 윗글에 없음 · comfortable, curious, straight, worried는 있음', 2, 3),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · (A)~(G)중 어법상 올바르지 않은 것을 있는 대로 고른 것은? (4점)
(B) does → do(주어 these cats) / (C) is show → is showing(현재진행형) / (F) show → shows(주어 This action) / (G) feel → feels(주어 The cat)
근거: L3 본문 01~07', 3, 4),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 대화의 흐름과 어법상 (A)~(C)에 들어갈 알맞은 것은? (2점)
be동사 의문문 → be동사로, Do 의문문 → do로 대답
근거: 필기본 10쪽 A', 4, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 대화의 흐름에 맞게 ⓐ에 들어갈 말로 올바르지 않은 것은? (2점)
[L3 Listen and Talk C] Can I help you?는 도움을 주겠다는 말이라 Sure. What is it?과 맞지 않음
근거: L3 핵심 대화 01 도움 요청하기', 5, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 다음 우리말을 바르게 영작한 것은? (2점)
stop + to부정사: ~하기 위해 멈추다 · stop + 동명사: ~하는 것을 멈추다
근거: 필기본 17쪽', 6, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · (A)~(E)중 어법상 올바르지 않은 것은? (2점)
(C) wishes being → wishes to be(wish는 to부정사를 목적어로)
근거: 필기본 10쪽 B', 7, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸에 공통으로 들어갈 것은? (2점)
be afraid of / be full of
근거: 필기본 17·20쪽', 8, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q09.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p09-10.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 밑줄 친 곳이 ⓐ와 같은 쓰임으로 사용된 것은? (3점)
ⓐ bowing은 현재진행형의 현재분사 · ①~④는 동명사(L4 본문, 필기본 16·17쪽) · ⑤ L3 Listen and Talk C
근거: L3 본문 12~19', 9, 3),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q10.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p09-10.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 윗글에서 찾을 수 있는 단어의 뜻은?(단, 단어의 단·복수 및 동사의 변형은 상관없음.) (2점)
excited는 윗글에 있음 · curious, comfortable, raise, introduce는 없음(영영풀이: 필기본 단어 정리 1)', 10, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 단어(구)의 뜻과 그 단어(구)가 들어갈 문장을 바르게 짝지은 것만으로 묶인 것은? (4점)
(A)upset-ⓔ (B)nervous-ⓑ (C)pot-ⓒ (D)heavy-ⓐ (E)delicious-ⓕ (F)practice-ⓓ · (G)suddenly는 들어갈 문장 없음
근거: 필기본 Lesson 5 단어 영영풀이 / L3 Listen and Talk A-1, L5 Listen and Talk B, L5 핵심 대화 1, 필기본 10쪽, L5 Listen and Talk A-1, L4 Enjoy the Clip A', 11, 4),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · (A)~(E)중 대화의 흐름과 어법상 올바르지 않은 것은? (2점)
(D) what do she → what does she
근거: L3 Review 1-1', 12, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · (A)~(E)중 어법상 올바른 것은? (2점)
(A) helps → help / (C) is having → has(가지다 뜻의 have는 진행형 불가) / (D) are → is / (E) needs → need
근거: L3 Enjoy the Clip A', 13, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 대화의 흐름과 어법상 (A)~(C)에 들어갈 알맞은 것은? (2점)
It was great / enjoyed and ate(병렬, 과거) / What did you eat?
근거: L4 Listen and Talk A-3', 14, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 두 사람의 대화 흐름에 맞게 (A)~(F)를 바르게 나열한 것은?(한 사람은 한 번에 한 번씩만 이야기함.) (2점)
(C) → (B) → (E) → (F) → (A) → (D)
근거: L4 Listen and Talk B', 15, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법상 올바른 문장은 모두 몇 개인가? (4점)
올바른 문장 4개(1·3·5·7번째) · 틀린 문장: breaked → broke / is → were / beautiful → more beautiful / closest → closer
근거: 필기본 17·20쪽 A', 16, 4),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q17.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p17-18.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · (A)~(E)중 어법상 올바른 것은? (3점)
(A) were → was(동명사 주어는 단수) / (B) eat → ate(found와 병렬) / (C) were → was(only rice) / (E) did not went → did not go
근거: L4 본문 10~19', 17, 3),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q18.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p17-18.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ~ⓒ에 들어갈 알맞은 것은? (2점)
by cable car / other types of gimbap / on the side', 18, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 대화의 흐름과 어법상 ⓐ에 들어갈 알맞은 것은? (2점)
What did you make?로 묻는 과거의 일 → cooked
근거: L4 Review 1-1', 19, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 밑줄 친 곳이 (A)와 같은 뜻으로 사용된 것은? (2점)
(A) like는 ‘~처럼’(전치사) · ①만 같은 뜻(L5 본문 14) · ②~⑤는 ‘좋아하다’(동사)
근거: L3 본문 09', 20, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸에 들어갈 것이 다른 것은? (2점)
④ does(주어 she, 3인칭 단수) · 나머지는 do
근거: L3 본문 12, 필기본 9·10쪽', 21, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q22.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p22-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · (A)~(E)중 글의 흐름에 맞게 <보기>의 문장이 들어갈 곳으로 가장 알맞은 것은? (2점)
First, ~ 다음, I had it ~ 앞
근거: L4 본문 20~28', 22, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q23.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p22-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · Ⓐ를 바르게 영작한 것은? (3점)
교과서 27번 문장 · 과거형 learned / the meaning of', 23, 3),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q24.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p22-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 지칭하는 대상이 다른 것은? (2점)
ⓐ~ⓓ는 dombegogi, ⓔ는 dombe', 24, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q25.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p25-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · (A)~(F)중 어법상 올바른 것을 있는 대로 고른 것은? (4점)
(B) goed → went / (C) eated → ate / (F) were → was(주어 Tteokgalbi)
근거: L4 본문 01~09', 25, 4),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q26.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p25-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 윗글에서 답을 찾을 수 없는 것은? (2점)
떡갈비의 가격은 나오지 않음', 26, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 흐름과 어법상 (A), (B)에 들어갈 알맞은 것은? (2점)
be ready to / will + 동사원형 have
근거: L4 Listen and Talk A-2', 27, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 대화의 흐름과 어법상 (A)~(C)에 들어갈 알맞은 것은? (2점)
I’m worried about / isn’t eating(현재진행형 부정) / I’m sorry to hear that
근거: L5 Listen and Talk A-2', 28, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q29.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p29-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · (A)~(E)중 어법상 올바른 것을 있는 대로 고른 것은? (4점)
(A) most small → smallest / (C) were → was(something은 단수) / (E) more small → smaller
근거: L5 본문 01~07', 29, 4),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q30.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p29-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 흐름에 맞게 ⓐ에 들어갈 가장 알맞은 것은? (2점)
혼자서는 화분에 닿을 수 없었기 때문에 → because(뒤에 주어+동사) · because of 뒤에는 명사(구)', 30, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q31.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p31-32.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · (A)~(E)중 글의 흐름과 어법상 올바르지 않은 것은? (3점)
(B) it’ll grows → it’ll grow(조동사 will + 동사원형)
근거: L5 본문 15~21', 31, 3),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q32.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p31-32.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · Ⓐ를 바르게 영작한 것은? (3점)
교과서 19번 문장 · I’ll + 동사원형 / 소유격 your / side by side', 32, 3),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 다음 글의 (A)~(E)중 글의 내용과 가장 관련이 없는 것은? (2점)
(B)는 L4 본문 07번 문장(담양 대통밥)으로, 글과 관련 없음
근거: L5 본문 22~27', 33, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 대화의 흐름에 맞게 ⓐ에 들어갈 가장 알맞은 것은? (2점)
I get too nervous in front of the class.
근거: L5 Listen and Talk C', 34, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q35.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p35-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 윗글의 내용과 일치하는 것은? (3점)
① 학기 마지막 날 / ② 반 친구들이 El 너머로 손을 뻗음 / ④ 마지막 화분 / ⑤ 꽃이 없는 가장 작은 식물
근거: L5 본문 08~13', 35, 3),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q36.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r01/p35-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 윗글에서 찾을 수 없는 단어의 뜻은?(단, 단어의 단·복수 및 동사의 변형은 상관없음.) (2점)
decide는 윗글에 없음 · tiny, care for, colorful, pot(pots)은 있음(영영풀이: 필기본 Lesson 5 단어)', 36, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법상 올바르지 않은 것은? (3점)
② enjoy to talk → enjoy talking(enjoy는 동명사를 목적어로)
근거: 필기본 10·17쪽', 37, 3),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 다음 글을 읽고 답을 찾을 수 없는 것은? (2점)
Maria의 나이는 나오지 않음
근거: L5 Enjoy the Clip A', 38, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 대화의 흐름에 맞게 ⓐ에 들어갈 가장 알맞은 것은? (2점)
during the last game
근거: L5 Listen and Talk B', 39, 2),
(@naesin_am1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r01/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 흐름과 어법상 가장 올바른 것은? (3점)
② have → has / ③ bring → brought / ④ play → plays / ⑤ dance → danced
근거: L3·L4·L5 대화', 40, 3);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM1-2602-R02', 'NAESIN', '압구정중 1학년 2학기 기말 2회 · Lesson 3~5 예상문제 2회', 'Lesson 3~5 교과서·학습지 필기본 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_am1_r02 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM1-2602-R02');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q01.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r02/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · (A)~(H)중 어법상 올바르지 않은 것을 있는 대로 고른 것은? (4점)
(A) does → do(주어 these dogs) / (C) want playing → want to play / (F) its ears is → its ears are / (D) feel → feels(주어 The dog)
근거: L3 본문 12~19', 1, 4),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q02.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r02/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 윗글에서 찾을 수 없는 단어의 뜻은?(단, 단어의 단·복수 및 동사의 변형은 상관없음.) (3점)
introduce는 윗글에 없음 · fear, alone, moment, body language는 있음', 2, 3),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · (A)~(C)에 들어갈 알맞은 것은? (2점)
What do they sell? / When is your birthday? / Why does she feel sad?
근거: 필기본 9쪽', 3, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · (A)를 (B)로 같은 의미를 가진 문장으로 바꾸었을 때 어법상 올바른 것은? (2점)
① like swimming / ③ do me / ④ ask you a favor / ⑤ enjoy talking
근거: L3 핵심 대화 1 도움 요청 표현, 필기본 16·17쪽', 4, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 대화의 흐름과 어법상 (A)~(C)에 들어갈 알맞은 것은? (2점)
can you help me / want to move / Let’s do
근거: L3 Listen and Talk A-1', 5, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 다음 우리말을 바르게 영작한 것은? (2점)
forget + 동명사: (이미) 한 일을 잊다 · forget + to부정사: (앞으로) 할 일을 잊다
근거: 필기본 17쪽', 6, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q07.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r02/p07-08.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · (A)~(E)중 글의 흐름에 맞게 <보기>의 문장이 들어갈 곳으로 가장 알맞은 것은? (3점)
누워 있는 두 번째 토끼가 다리를 쭉 뻗고 있음 → Why is it doing that? 앞
근거: L3 본문 20~29', 7, 3),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q08.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r02/p07-08.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 윗글을 읽고 답을 찾을 수 없는 것은? (2점)
첫 번째 토끼가 무엇을 궁금해하는지는 나오지 않음(something)', 8, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q09.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r02/p09-10.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 지칭하는 대상이 다른 것은? (2점)
ⓐ는 두 고양이 모두, ⓑ~ⓔ는 첫 번째 고양이
근거: L3 본문 03~07', 9, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q10.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r02/p09-10.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 윗글에서 찾을 수 있는 단어의 뜻은?(단, 단어의 단·복수 및 동사의 변형은 상관없음.) (2점)
trust는 윗글에 있음 · alone, moment, curious, stretch는 없음(영영풀이: 필기본 단어 정리 1)', 10, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · <보기>의 두 사람의 대화 다음에 올 부분을 올바르게 나열했을 때 ⓐ~ⓔ중 3번째에 오는 것은?(단, 한 사람은 한 번에 한 번씩만 이야기함.) (2점)
ⓒ → ⓔ → ⓓ → ⓑ → ⓐ · 3번째는 ⓓ
근거: L3 Listen and Talk B', 11, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 밑줄 친 부분의 쓰임이 ⓐ와 같은 것은? (3점)
ⓐ to have는 want의 목적어(명사적 용법) · ① 형용사적, ②③④ 부사적(필기본 10·17쪽, L5 Listen and Talk A-2)
근거: L3 본문 08~11', 12, 3),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · (A)~(E)중 대화의 흐름과 어법상 올바르지 않은 것은? (2점)
(B) are playing → is playing(주어 my dog, Cooper)
근거: L3 Listen and Talk A-2', 13, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 흐름에 맞게 ⓐ에 들어갈 가장 알맞은 것은? (2점)
다음 말 Strawberry ice cream, please.로 보아 맛을 알려 주는 말
근거: L4 Listen and Talk A-1', 14, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · (A)~(E)중 대화의 흐름과 어법상 올바르지 않은 것은? (3점)
(A) eat → ate(went와 병렬, 과거)
근거: L4 Listen and Talk C', 15, 3),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법상 올바른 문장은 모두 몇 개인가? (4점)
올바른 문장 4개(1·3·5·7번째) · 틀린 문장: swiming → swimming / Does she doing → Is she doing / is → are / don’t → doesn’t
근거: 필기본 3·6쪽', 16, 4),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q17.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 다음 글을 읽고 답을 찾을 수 없는 것은? (2점)
만드는 데 걸리는 시간은 나오지 않음
근거: L4 Enjoy the Clip A', 17, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q18.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r02/p18-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · (A)~(E)중 글의 흐름과 어법상 올바르지 않은 것은? (3점)
(E) was → were(주어 Kimchi and spicy squid, 복수)
근거: L4 본문 10~16', 18, 3),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q19.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r02/p18-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 윗글에서 찾을 수 없는 단어의 뜻은?(단, 단어의 단·복수 및 동사의 변형은 상관없음.) (2점)
plate는 윗글에 없음 · ocean, seaweed, squid, local은 있음(영영풀이: 필기본 단어 정리 2)', 19, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q20.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r02/p20-21.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · (A)~(F)중 글의 흐름과 어법상 올바른 것을 있는 대로 고른 것은? (4점)
(A) become → became / (D) were → was(주어 it) / (F) learn → learned
근거: L4 본문 20~28', 20, 4),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q21.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r02/p20-21.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글의 흐름에 맞게 ⓐ에 들어갈 가장 알맞은 것은? (2점)
I had it with a special fish sauce(~과 함께)', 21, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 흐름과 어법상 (A), (B)에 들어갈 알맞은 것은? (2점)
I’ll have ~ / Would you like ~?
근거: L4 Listen and Talk A-2', 22, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q23.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r02/p23-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · Ⓐ를 바르게 영작한 것은? (3점)
looked / ate / two local dishes
근거: L4 본문 05', 23, 3),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q24.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r02/p23-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ~ⓒ에 들어갈 알맞은 것은? (2점)
enjoyed walking(enjoy + 동명사) / Tteokgalbi was / went well together
근거: L4 본문 04·08·09', 24, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · <보기>에서 어법상 올바르지 않은 문장을 찾아 그 이유를 바르게 설명한 사람은? (2점)
(E) plans visiting → plans to visit
근거: 필기본 10·16·17쪽', 25, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 흐름과 어법상 (A)~(C)에 들어갈 알맞은 것은? (2점)
upset / broke(break의 과거) / can + 동사원형
근거: L5 Listen and Talk A-1', 26, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸에 공통으로 들어갈 것은? (2점)
learn about / be worried about / tell ~ about
근거: L3 본문 02, L5 Listen and Talk A-2, L5 Enjoy the Clip A', 27, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 대화의 흐름에 맞게 ⓐ에 들어갈 가장 알맞은 것은? (2점)
Why don’t you ~? — 제안하는 말
근거: L5 Listen and Talk A-3', 28, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · (A)~(G)중 어법상 올바르지 않은 것을 있는 대로 고른 것은? (4점)
(B) runned → ran / (E) taked → took / (F) have → had
근거: L5 본문 08~14', 29, 4),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q30.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r02/p30-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 지칭하는 대상이 다른 것은? (2점)
ⓐ~ⓓ는 El의 과꽃(aster plant), ⓔ는 과꽃과 백일홍 둘 다
근거: L5 본문 15~21', 30, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q31.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r02/p30-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · Ⓐ를 바르게 영작한 것은? (3점)
교과서 18번 문장 · 조동사 can + 동사원형 / 소유격 my', 31, 3),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 대화의 흐름과 어법상 (A), (B)에 들어갈 알맞은 것은? (2점)
She is sick / I heard the news(hear-heard-heard)
근거: L5 Review 1-1', 32, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 대화의 흐름에 맞게 ⓐ에 들어갈 가장 알맞은 것은? (2점)
「how+형용사/부사」 — 상영 시간은 How long
근거: 필기본 9쪽', 33, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · (A)~(E)중 대화의 흐름과 어법상 올바르지 않은 것은? (3점)
(C) practices → practiced(주어 we, 과거의 일)
근거: L5 Listen and Talk B', 34, 3),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸에 공통으로 들어갈 것은? (2점)
근거: 필기본 17쪽 D, L4 본문 18, L4 Review 1-1', 35, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 단어(구)의 뜻과 그 단어(구)가 들어갈 문장을 바르게 짝지은 것만으로 묶인 것은? (4점)
(A)scared-ⓕ (B)safe-ⓐ (C)presentation-ⓔ (E)volunteer-ⓓ (F)pillow-ⓒ (G)vegetable-ⓑ (H)enjoy-ⓖ · (D)describe는 들어갈 문장 없음
근거: 필기본 단어 정리 1·2, Lesson 5 단어 영영풀이 / L3 Enjoy the Clip A, L4 Listen and Talk A-3, L5 Enjoy the Clip A·Listen and Talk C', 36, 4),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · (A)~(E)중 어법상 올바르지 않은 것은? (3점)
(C) keep → keeps(동명사 주어는 단수)
근거: 필기본 17쪽 B', 37, 3),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · (A)~(E)중 글의 흐름과 어법상 올바른 것은? (3점)
(A) plant → planted / (B) were → was / (C) care → cared / (D) come → came
근거: L5 본문 22~26', 38, 3),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q39.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r02/p39-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓒ에서 내용과 어법상 올바른 것으로만 고른 것은? (2점)
the smallest(최상급) / taller than(비교급) / felt smaller(small-smaller)
근거: L5 본문 01~07', 39, 2),
(@naesin_am1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r02/q40.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r02/p39-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 윗글을 읽고 답을 찾을 수 없는 것은? (2점)
Jo의 키는 나오지 않음', 40, 2);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM1-2602-R03', 'NAESIN', '압구정중 1학년 2학기 기말 3회 · Lesson 3~5 예상문제 3회', 'Lesson 3~5 교과서·학습지 필기본 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_am1_r03 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM1-2602-R03');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 대화의 흐름과 어법상 (A)~(C)에 들어갈 알맞은 것은? (3점)
can + 동사원형 / want + to부정사 / Let’s + 동사원형
근거: L3 Listen and Talk A-1', 1, 3),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q02.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p02-03.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · (A)~(E)중 글의 흐름에 맞게 <보기>의 문장이 들어갈 곳으로 가장 알맞은 것은? (2점)
머리·귀·꼬리 모습(몸짓 언어) 다음, The dog is scared ~ 앞
근거: L3 본문 17~18', 2, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q03.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p02-03.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 밑줄 친 곳이 ⓐ와 같은 쓰임으로 사용된 것은? (4점)
ⓐ는 진행형의 현재분사 · 나머지는 동명사
근거: L3 본문 14, 필기본 16·17쪽 D', 3, 4),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · (A)~(E)중 대화의 흐름과 어법상 올바르지 않은 것은? (2점)
(D) have → has(주어 He, 3인칭 단수)
근거: L3 Play with Expressions', 4, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · <보기>에서 어법상 올바르지 않은 문장을 찾아 그 이유를 바르게 설명한 사람은? (3점)
(C) prettyer → prettier
근거: 필기본 20쪽', 5, 3),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸에 들어갈 것이 다른 것은? (2점)
My mother → is · 나머지는 are
근거: 필기본 01·04·06쪽', 6, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q07.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p07-08.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 대화의 흐름에 맞게 ⓐ에 들어갈 알맞은 것은? (2점)
생김새를 묻는 What does ~ look like?
근거: L3 Listen and Talk B', 7, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q08.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p07-08.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 대화의 내용과 일치하는 것은? (2점)
She has big ears.
근거: L3 Listen and Talk B', 8, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 흐름에 맞게 (A)~(E)에 들어갈 것 중 올바르지 않은 것은? (2점)
(D)에는 safe가 들어감(구해 준 뒤 Whew, it is safe now.)
근거: L3 Enjoy the Clip A', 9, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 밑줄 친 곳이 Ⓐ와 같은 쓰임으로 사용된 것은? (3점)
Ⓐ는 ‘~하기 위해’(부사적 용법) · He stopped to drink water.만 같은 쓰임, 나머지는 명사적 용법
근거: 필기본 10·17쪽', 10, 3),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q11.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p11-13.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · (A)~(F)중 어법상 올바른 것을 있는 대로 고른 것은? (4점)
(B) do → does / (D) feel → feels / (F) look → looks (주어 3인칭 단수)
근거: L3 본문 01~11', 11, 4),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q12.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p11-13.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 윗글에서 찾을 수 있는 단어의 뜻은?(단, 단어의 단·복수 및 동사의 변형은 상관없음.) (2점)
trust · alone, moment, raise, body language는 윗글에 없음
근거: 필기본 단어 정리 1', 12, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q13.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p11-13.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 윗글의 내용과 일치하는 것은? (2점)
근거: L3 본문 05~10', 13, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q14.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p14-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 윗글의 (A)~(E)중 내용과 가장 관련이 없는 것은? (2점)
(D)는 L5 본문 07번 문장으로, 토끼 이야기와 관련 없음
근거: L3 본문 20~29', 14, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q15.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p14-15.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · Ⓐ를 바르게 영작한 것은? (3점)
의문사 + be동사 + 주어 + -ing
근거: L3 본문 27', 15, 3),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q16.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p16-18.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · Ⓐ를 바르게 영작한 것은? (3점)
enjoy + 동명사, 과거형 enjoyed
근거: L4 본문 04', 16, 3),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q17.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p16-18.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 윗글을 읽고 답을 찾을 수 없는 것은? (2점)
담양에 머문 기간은 나오지 않음
근거: L4 본문 01~09', 17, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q18.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p16-18.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 윗글에서 찾을 수 있는 단어의 뜻을 <보기>에서 있는 대로 고른 것은?(단, 단어의 단·복수는 상관없음.) (4점)
(A) local (C) unique (E) dish(dishes) · ocean, seaweed, pork는 윗글에 없음
근거: 필기본 단어 정리 2', 18, 4),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸에 공통으로 들어갈 것은? (2점)
Thank you for / a name for / for two hours
근거: 필기본 9·17쪽, L4 본문 18', 19, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 다음 우리말을 바르게 영작한 것은? (3점)
remember + to부정사: (앞으로) 할 일을 기억하다
근거: 필기본 17쪽', 20, 3),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q21.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p21-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · (A)~(I)중 어법상 올바른 것은 모두 몇 개인가? (4점)
올바른 것 (B)(C)(E)(F)(H)(I) 6개 · (A) were → was(동명사 주어) / (D) were → was(rice) / (G) heared → heard
근거: L4 본문 10~19', 21, 4),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q22.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p21-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글의 흐름에 맞게 ⓐ에 들어갈 알맞은 것은? (2점)
by cable car
근거: L4 본문 10', 22, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 대화의 (A)와 (B)에 들어갈 것으로 알맞은 것은? (2점)
권유 수락 Yes, please. / 과거 sang(danced와 시제 일치)
근거: L4 Play with Expressions', 23, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · (A)~(E)중 대화의 흐름과 어법상 올바르지 않은 것은? (2점)
(A) cooks → cooked(주어 I, 과거)
근거: L4 Review 1-1', 24, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 대화의 흐름에 맞게 ⓐ에 들어갈 가장 알맞은 것은? (2점)
경험 묻기 What did you eat?
근거: L4 Listen and Talk A-3', 25, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q26.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p26-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 윗글의 제목으로 가장 알맞은 것은? (2점)
제주에서 먹은 돔베고기 이야기
근거: L4 본문 20~28', 26, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q27.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p26-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 윗글의 내용과 가장 일치하지 않는 것은? (3점)
접시가 아니라 도마 위에 나옴
근거: L4 본문 24', 27, 3),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · <보기>의 두 사람의 대화 다음에 올 부분을 올바르게 나열했을 때 ⓐ~ⓔ중 3번째에 오는 것은?(단, 한 사람은 한 번에 한 번씩만 이야기함.) (2점)
3번째는 Sounds delicious! · 순서 ⓔ → ⓒ → ⓑ → ⓓ → ⓐ
근거: L4 Listen and Talk B', 28, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q29.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p29-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · (A)~(G)중 어법상 올바르지 않은 것을 있는 대로 고른 것은? (4점)
(B) runned → ran / (D) takes → took / (F) the most small → the smallest
근거: L5 본문 08~14', 29, 4),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q30.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p29-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 윗글을 읽고 답을 찾을 수 없는 것은? (2점)
화분의 개수는 나오지 않음
근거: L5 본문 08~14', 30, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · (A)~(E)중 글의 흐름과 어법상 올바른 것은? (2점)
(A) has → have / (B) keeps → keep / (D) telling → tell / (E) takes → take
근거: L5 Enjoy the Clip A', 31, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q32.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p32-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓒ에서 내용과 어법상 올바른 것으로만 고른 것은? (3점)
El and Jo(복수) were / could + 동사원형 / than 앞 비교급
근거: L5 본문 01~06', 32, 3),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q33.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p32-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 윗글에서 찾을 수 있는 단어의 뜻은?(단, 단어의 단·복수 및 동사의 변형은 상관없음.) (2점)
reach · tiny, colorful, pillow, suddenly는 윗글에 없음
근거: 필기본 Lesson 5 단어', 33, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 동사의 변화형(원형 - 과거형 - 과거분사형)을 바르게 쓴 것은? (2점)
lost-lost / fed-fed / stole-stolen / dug-dug
근거: 필기본 13~15쪽', 34, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 대화의 흐름에 맞게 (A)~(C)에 들어갈 단어를 바르게 짝지은 것은? (2점)
be worried about / sorry to hear / go to
근거: L5 Listen and Talk A-2', 35, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 형용사·부사의 원급 - 비교급 - 최상급을 바르게 쓴 것은? (3점)
hotter-hottest / earlier-earliest / more famous-most famous / wiser-wisest
근거: 필기본 20쪽', 36, 3),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 흐름에 맞게 ⓐ에 들어갈 가장 알맞은 것은? (2점)
Don’t worry.로 위로할 수 있는 걱정의 말
근거: L5 Listen and Talk C', 37, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q38.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p38-39.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · Ⓐ를 바르게 영작한 것은? (3점)
will + 동사원형 grow and have · in time(시간이 지나면)
근거: L5 본문 15', 38, 3),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q39.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r03/p38-39.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 윗글의 내용과 일치하는 것은? (2점)
“You can have my zinnia plant,” Jo said to El
근거: L5 본문 18', 39, 2),
(@naesin_am1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r03/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 두 사람의 대화 흐름에 맞게 (A)~(F)를 바르게 나열한 것은?(한 사람은 한 번에 한 번씩만 이야기함.) (2점)
(B) → (D) → (F) → (A) → (E) → (C)
근거: L5 Listen and Talk B', 40, 2);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM1-2602-R04', 'NAESIN', '압구정중 1학년 2학기 기말 4회 · Lesson 3~5 예상문제 4회', 'Lesson 3~5 교과서·학습지 필기본 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_am1_r04 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM1-2602-R04');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 대화의 흐름과 어법상 (A)~(C)에 들어갈 알맞은 것은? (2점)
my dog(단수) is / does he / he has
근거: L3 Listen and Talk A-2', 1, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q02.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p02-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 지칭하는 대상이 다른 것은? (2점)
ⓔ는 두 번째 고양이 · 나머지는 첫 번째 고양이
근거: L3 본문 03~10', 2, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q03.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p02-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · Ⓐ를 바르게 영작한 것은? (3점)
주어 It(3인칭 단수) + feels + 형용사
근거: L3 본문 10', 3, 3),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q04.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p02-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 윗글에서 찾을 수 없는 단어의 뜻은?(단, 단어의 단·복수 및 동사의 변형은 상관없음.) (2점)
bow는 윗글에 없음 · forward, maybe, safe, trust는 있음
근거: 필기본 단어 정리 1', 4, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 흐름과 어법상 (A)~(C)에 들어갈 알맞은 것은? (2점)
How old / How long / It runs
근거: 필기본 9쪽', 5, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · (A)~(E)중 글의 흐름과 어법상 올바르지 않은 것을 있는 대로 고른 것은? (3점)
(C) have → has / (E) needs → need(주어 Many street animals)
근거: L3 Enjoy the Clip A', 6, 3),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 두 사람의 대화 흐름에 맞게 (A)~(E)를 바르게 나열한 것은?(한 사람은 한 번에 한 번씩만 이야기함.) (2점)
(B) → (D) → (E) → (C) → (A)
근거: L3 Listen and Talk C', 7, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법상 올바른 문장은 모두 몇 개인가? (4점)
올바른 문장 6개 · beautifuler → more beautiful / the expensivest → the most expensive
근거: 필기본 20쪽', 8, 4),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q09.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p09-10.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · (A)~(H)중 어법상 올바른 것은 모두 몇 개인가? (4점)
올바른 것 (B)(D)(E)(G) 4개 · (A) does → do / (C) playing → to play / (F) is → are(ears) / (H) being → to be
근거: L3 본문 12~19', 9, 4),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q10.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p09-10.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 윗글의 내용과 일치하는 것은? (3점)
근거: L3 본문 14~19', 10, 3),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q11.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p11-12.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · (A)~(E)중 글의 흐름에 맞게 <보기>의 문장이 들어갈 곳으로 가장 알맞은 것은? (2점)
Is it worried ~? Not really. 다음, 두 번째 토끼 이야기 앞
근거: L3 본문 24~26', 11, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q12.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p11-12.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 윗글의 제목으로 가장 알맞은 것은? (2점)
두 토끼의 몸짓이 나타내는 마음
근거: L3 본문 20~29', 12, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 내용과 일치하는 것은? (2점)
She is black, and she has a long tail.
근거: L3 Review 1-1', 13, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q14.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p14-16.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · Ⓐ를 바르게 영작한 것은? (4점)
start + to부정사 / did not + 동사원형 / because(이유)
근거: L4 본문 19', 14, 4),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q15.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p14-16.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 밑줄 친 곳이 ⓐ와 같은 뜻으로 사용된 것은? (2점)
ⓐ like는 ‘~와 같은, ~처럼’ · 나머지는 ‘좋아하다’
근거: L4 본문 13, L3 본문 09', 15, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q16.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p14-16.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 윗글을 읽고 답을 찾을 수 없는 것은? (2점)
충무김밥의 값은 나오지 않음
근거: L4 본문 10~19', 16, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q17.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 대화의 흐름과 어법상 (A), (B)에 들어갈 알맞은 것은? (2점)
과거형 ate / brought(bring-brought-brought)
근거: L4 Listen and Talk C', 17, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · <보기>에서 어법상 올바르지 않은 문장을 찾아 그 이유를 바르게 설명한 사람은? (3점)
(D) leaves → left
근거: 필기본 17쪽 A', 18, 3),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · (A)~(E)중 글의 흐름과 어법상 올바르지 않은 것은? (2점)
(E) look → looks
근거: L4 Enjoy the Clip A', 19, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q20.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p20-21.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · (A)~(G)중 글의 흐름과 어법상 올바르지 않은 것을 있는 대로 고른 것은? (4점)
(A) will travel → traveled / (C) to walk → walking / (G) were → was(Tteokgalbi)
근거: L4 본문 01~09', 20, 4),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q21.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p20-21.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 윗글에서 찾을 수 있는 단어의 뜻은?(단, 단어의 단·복수 및 동사의 변형은 상관없음.) (2점)
bamboo · squid, fisherman, cutting board, fantastic은 윗글에 없음
근거: 필기본 단어 정리 2', 21, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 대화의 흐름에 맞게 ⓐ에 들어갈 알맞은 것은? (2점)
음식 권하기 Would you like ~?
근거: L4 Listen and Talk A-2', 22, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 다음 우리말을 바르게 영작한 것은? (3점)
stop + 동명사: ~하는 것을 멈추다
근거: 필기본 17쪽', 23, 3),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸에 들어갈 것이 다른 것은? (2점)
Why does she ~ · 나머지는 do
근거: 필기본 9쪽, L3 본문 03·12', 24, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 다음 글의 (A)~(E)중 내용과 가장 관련이 없는 것은? (2점)
(D)는 L3 본문 15번 문장으로, 돔베고기 이야기와 관련 없음
근거: L4 본문 20~28', 25, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 밑줄 친 곳이 Ⓐ와 같은 쓰임으로 사용된 것은? (3점)
Ⓐ는 동명사 · 나머지는 진행형의 현재분사
근거: 필기본 6·16쪽', 26, 3),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · (A)~(F)중 어법상 올바른 것을 있는 대로 고른 것은? (4점)
(B) play → playing / (C) Do → Doing / (F) enjoy → enjoying(전치사 by 뒤 동명사)
근거: 필기본 17쪽 B', 27, 4),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸에 공통으로 들어갈 것은? (2점)
be worried about / learn about
근거: L5 Listen and Talk A-2·C, L3 본문 02', 28, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 동사의 변화형(원형 - 과거형 - 과거분사형)을 바르게 쓴 것은? (2점)
brought-brought / flew-flown / thought-thought / swam-swum
근거: 필기본 13~15쪽', 29, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 대화의 내용과 일치하지 않는 것은? (3점)
It was great.
근거: L4 Listen and Talk A-3', 30, 3),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 다음 글의 내용과 일치하는 것은? (2점)
keep her under my pillow
근거: L5 Enjoy the Clip A', 31, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q32.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p32-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · Ⓐ~Ⓔ 중 어법상 올바르지 않은 것은? (3점)
Ⓓ more tall → taller
근거: L5 본문 06', 32, 3),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q33.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p32-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 윗글의 내용과 일치하는 것은? (2점)
근거: L5 본문 04~06', 33, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · (A)~(E)중 대화의 흐름과 어법상 올바르지 않은 것은? (2점)
(B) play → plays
근거: L5 Listen and Talk A-3', 34, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 대화의 흐름에 맞게 ⓐ에 들어갈 가장 알맞은 것은? (2점)
Good idea.로 답할 수 있는 제안
근거: L5 Review 1-1', 35, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q36.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p36-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 흐름에 맞게 ⓐ~ⓒ에 들어갈 알맞은 것은? (3점)
ran to / reached for / pots with colorful flowers
근거: L5 본문 09~11', 36, 3),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q37.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p36-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 윗글의 내용과 가장 일치하지 않는 것은? (2점)
El은 꽃이 없는 마지막 화분을 가져감
근거: L5 본문 12~13', 37, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q38.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p38-39.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · (A)~(E)중 글의 흐름에 맞게 <보기>의 문장이 들어갈 곳으로 가장 알맞은 것은? (2점)
성장의 계절 다음, 여름 마지막 날 앞
근거: L5 본문 23~25', 38, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q39.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r04/p38-39.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 윗글의 내용과 일치하는 것은? (2점)
El grew TALLER, TOO!
근거: L5 본문 28', 39, 2),
(@naesin_am1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r04/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · <보기>의 두 사람의 대화 다음에 올 부분을 올바르게 나열했을 때 ⓐ~ⓔ중 4번째에 오는 것은?(단, 한 사람은 한 번에 한 번씩만 이야기함.) (3점)
4번째는 Don’t worry. We all learn from our mistakes. ~ · 순서 ⓔ → ⓒ → ⓓ → ⓑ → ⓐ
근거: L5 Listen and Talk B', 40, 3);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM1-2602-R05', 'NAESIN', '압구정중 1학년 2학기 기말 5회 · Lesson 3~5 예상문제 5회', 'Lesson 3~5 교과서·학습지 필기본 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_am1_r05 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM1-2602-R05');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · (A)~(E)중 대화의 흐름과 어법상 올바른 것은? (2점)
(A) helps → help / (B) finding → find / (D) are → is / (E) finds → find
근거: L3 Listen and Talk B', 1, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 다음 우리말을 바르게 영작한 것은? (3점)
decide + to부정사 / practice + 동명사
근거: 필기본 10쪽', 2, 3),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q03.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p03-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · (A)~(F)중 글의 흐름과 어법상 올바른 것을 있는 대로 고른 것은? (4점)
(B) raiseing → raising / (D) lieing → lying / (E) does it doing → is it doing
근거: L3 본문 22~29', 3, 4),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q04.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p03-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 윗글의 내용과 일치하는 것은? (2점)
its ears are straight
근거: L3 본문 23', 4, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q05.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p05-06.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 대화의 흐름에 맞게 ⓐ에 들어갈 알맞은 것은? (2점)
생김새를 묻는 What does she look like?
근거: L3 Listen and Talk A-3', 5, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q06.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p05-06.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 대화의 내용과 일치하지 않는 것은? (2점)
초록색 재킷을 입고 있음
근거: L3 Listen and Talk A-3', 6, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 흐름에 맞게 빈칸에 들어갈 알맞은 것은? (2점)
도와줘야 하는 상태 → scared
근거: L3 Enjoy the Clip A', 7, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · Ⓐ~Ⓔ 중 어법상 올바르지 않은 것은? (3점)
Ⓑ say → saying(mind + 동명사)
근거: 필기본 17쪽 C', 8, 3),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · (A)~(E)중 어법상 올바른 것은? (3점)
(A) cleaned / (C) did / (D) left / (E) wrote
근거: 필기본 17쪽 A', 9, 3),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 대화의 (A)와 (B)에 들어갈 것으로 알맞은 것은? (2점)
도움 요청 수락 Sure. / She’s playing(진행형)
근거: L3 핵심 대화 1, Review 1-1', 10, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q11.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p11-12.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 윗글에서 찾을 수 있는 단어의 뜻을 <보기>에서 있는 대로 고른 것은?(단, 단어의 단·복수는 상관없음.) (4점)
(B) excited (D) body language (F) moment · curious, comfortable, stretch는 윗글에 없음
근거: 필기본 단어 정리 1', 11, 4),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q12.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p11-12.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 윗글을 읽고 답을 찾을 수 없는 것은? (2점)
무엇을 무서워하는지는 나오지 않음(scared of something)
근거: L3 본문 12~19', 12, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q13.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p13-14.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · Ⓐ~Ⓔ 중 어법상 올바르지 않은 것은? (3점)
Ⓑ Let’s learning → Let’s learn
근거: L3 본문 02', 13, 3),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q14.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p13-14.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 윗글의 제목으로 가장 알맞은 것은? (2점)
두 고양이의 몸짓으로 알아보는 기분
근거: L3 본문 01~11', 14, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 다음 글의 내용과 일치하는 것은? (2점)
I’m making surichwitteok with my dad now.
근거: L4 Enjoy the Clip A', 15, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q16.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p16-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · (A)~(E)중 글의 흐름에 맞게 <보기>의 문장이 들어갈 곳으로 가장 알맞은 것은? (3점)
다른 김밥과 다른 점 다음, 곁들인 반찬 앞
근거: L4 본문 13~15', 16, 3),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q17.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p16-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 윗글에서 찾을 수 없는 단어의 뜻은?(단, 단어의 단·복수 및 동사의 변형은 상관없음.) (2점)
pork는 윗글에 없음 · ocean, fantastic, squid, fisherman은 있음
근거: 필기본 단어 정리 2', 17, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 대화의 흐름에 맞게 ⓐ에 들어갈 알맞은 것은? (2점)
권유 수락 Yes, please.
근거: L4 Listen and Talk A-1', 18, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · (A)~(E)중 대화의 흐름과 어법상 올바르지 않은 것은? (2점)
(C) eat → ate(enjoyed와 시제 일치)
근거: L4 Listen and Talk A-3', 19, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · (A)~(F)중 어법상 올바른 것을 있는 대로 고른 것은? (4점)
(B) siting → sitting / (D) is → are / (F) Does she doing → Is she doing
근거: 필기본 6쪽', 20, 4),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 대화의 흐름에 맞게 ⓐ에 들어갈 가장 알맞은 것은? (2점)
Sure.로 답할 수 있는 권유
근거: L4 Review 1-1', 21, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 흐름과 어법상 (A)~(C)에 들어갈 알맞은 것은? (2점)
과거 went / did + 동사원형 make / brought
근거: L4 Listen and Talk B', 22, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q23.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p23-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · Ⓐ를 바르게 영작한 것은? (4점)
목적격 me / 과거 surprised / in two ways
근거: L4 본문 23', 23, 4),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q24.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p23-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 윗글을 읽고 답을 찾을 수 없는 것은? (2점)
누가 요리했는지는 나오지 않음
근거: L4 본문 20~28', 24, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q25.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p25-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓒ에서 내용과 어법상 올바른 것으로만 고른 것은? (3점)
지난 여름방학 → traveled / enjoy + 동명사 / Tteokgalbi(단수) was
근거: L4 본문 01~08', 25, 3),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q26.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p25-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 윗글의 내용과 일치하는 것은? (2점)
근거: L4 본문 06', 26, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 밑줄 친 곳이 (A)와 같은 뜻으로 사용된 것은? (3점)
(A)는 ‘먹다’ · 나머지는 ‘가지다’
근거: 필기본 6쪽, L4 본문 26', 27, 3),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 형용사·부사의 원급 - 비교급 - 최상급을 바르게 쓴 것은? (2점)
happier-happiest / more useful-most useful / better-best / braver-bravest
근거: 필기본 20쪽', 28, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글의 흐름에 맞게 (A)~(D)를 가장 바르게 나열한 것은? (2점)
(B) → (D) → (A) → (C)
근거: L5 본문 09~13', 29, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q30.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p30-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · Ⓐ를 바르게 영작한 것은? (3점)
비교급 taller than / so(그래서) / could + 동사원형
근거: L5 본문 06', 30, 3),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q31.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p30-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 윗글의 내용과 일치하지 않는 것은? (2점)
could not reach the pots alone
근거: L5 본문 03', 31, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 내용과 일치하지 않는 것은? (3점)
I missed the ball many times during the last game.
근거: L5 Listen and Talk B', 32, 3),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸에 공통으로 들어갈 단어는? (2점)
be afraid of / a lot of
근거: 필기본 10·20쪽', 33, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 밑줄 친 곳이 Ⓐ와 같은 쓰임으로 쓰이지 않은 것은? (3점)
Ⓐ는 동사의 목적어(명사적 용법) · to buy는 ‘~하기 위해’(부사적 용법)
근거: 필기본 10쪽', 34, 3),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 대화의 흐름에 맞게 (A)~(C)에 들어갈 단어를 바르게 짝지은 것은? (2점)
worried about / play with / on weekends
근거: L5 Listen and Talk A-3', 35, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 (A)와 (B)에 들어갈 것으로 알맞은 것은? (2점)
감정·상태 묻기 / be worried about
근거: L5 핵심 대화 1·2', 36, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q37.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p37-38.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · (A)~(H)중 어법상 올바른 것은 모두 몇 개인가? (4점)
올바른 것 (A)(C)(F)(G) 4개 · (B) grows → grow / (D) did → was / (E) has → have / (H) we → us
근거: L5 본문 15~21', 37, 4),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q38.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r05/p37-38.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 윗글의 내용과 일치하는 것은? (2점)
근거: L5 본문 19', 38, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸에 들어갈 것이 다른 것은? (2점)
Does + 주어 + have · 나머지는 has
근거: 필기본 5쪽, L3 대화', 39, 2),
(@naesin_am1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r05/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글의 흐름에 맞게 ⓐ에 들어갈 가장 알맞은 것은? (2점)
take away my worries
근거: L5 Enjoy the Clip A', 40, 2);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM1-2602-R06', 'NAESIN', '압구정중 1학년 2학기 기말 6회 · Lesson 3~5 예상문제 6회', 'Lesson 3~5 교과서·학습지 필기본 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_am1_r06 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM1-2602-R06');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · (A)~(E)중 대화의 흐름과 어법상 올바르지 않은 것은? (2점)
(D) are → is(This table)
근거: L3 Listen and Talk A-1', 1, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q02.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r06/p02-03.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · Ⓐ~Ⓔ 중 어법상 올바르지 않은 것은? (3점)
Ⓓ are → is(주어 The rabbit)
근거: L3 본문 25', 2, 3),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q03.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r06/p02-03.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 윗글의 내용과 일치하지 않는 것은? (2점)
아픈 것이 아니라 편안함
근거: L3 본문 28~29', 3, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q04.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r06/p04-05.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 윗글의 (A)~(E)중 내용과 가장 관련이 없는 것은? (2점)
(C)는 L4 본문 07번 문장으로, 고양이 이야기와 관련 없음
근거: L3 본문 03~11', 4, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q05.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r06/p04-05.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 윗글에서 찾을 수 있는 단어의 뜻을 <보기>에서 있는 대로 고른 것은?(단, 단어의 단·복수는 상관없음.) (4점)
(B) forward (D) safe (E) maybe · raise, alone, describe는 윗글에 없음
근거: 필기본 단어 정리 1', 5, 4),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글의 흐름에 맞게 ⓐ에 들어갈 가장 알맞은 것은? (2점)
We need volunteers.
근거: L3 Enjoy the Clip A', 6, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 형용사·부사의 원급 - 비교급 - 최상급을 바르게 쓴 것은? (2점)
better / more / farther·further-farthest·furthest / earlier
근거: 필기본 20쪽', 7, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 내용과 일치하는 것은? (2점)
he has a short tail
근거: L3 Listen and Talk A-2', 8, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q09.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r06/p09-10.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · Ⓐ를 바르게 영작한 것은? (4점)
need + to부정사 / 주어 It + needs / for a moment
근거: L3 본문 19', 9, 4),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q10.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r06/p09-10.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 윗글의 제목으로 가장 알맞은 것은? (2점)
두 개의 몸짓이 전하는 말
근거: L3 본문 12~19', 10, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 흐름과 어법상 (A)~(C)에 들어갈 알맞은 것은? (2점)
want + to부정사 / does + 동사원형 look / He is wearing
근거: L3 Play with Expressions', 11, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q12.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r06/p12-13.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글의 흐름에 맞게 ⓐ, ⓑ에 들어갈 알맞은 것은? (3점)
thanks to / on a cutting board
근거: L4 본문 22·24', 12, 3),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q13.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r06/p12-13.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 윗글의 내용과 일치하는 것은? (2점)
근거: L4 본문 26', 13, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · (A)~(E)중 어법상 올바르지 않은 것은? (2점)
(D) they do → they are(be동사 의문문)
근거: 필기본 10쪽 A', 14, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 대화의 (A)와 (B)에 들어갈 것으로 알맞은 것은? (2점)
did + 동사원형 eat / sang and danced
근거: L4 핵심 대화 2, Play with Expressions', 15, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 두 사람의 대화 흐름에 맞게 (A)~(D)를 바르게 나열한 것은?(한 사람은 한 번에 한 번씩만 이야기함.) (2점)
(B) → (D) → (C) → (A)
근거: L4 Listen and Talk A-2', 16, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q17.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · (A)~(E)중 대화의 흐름과 어법상 올바른 것은? (3점)
(A) go → went(ate와 시제 일치) / (B) Sound → Sounds / (C) bringed → brought / (E) Thanks you → Thank you
근거: L4 Listen and Talk C', 17, 3),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · <보기>에서 어법상 올바르지 않은 문장을 찾아 그 이유를 바르게 설명한 사람은? (3점)
(E) to read → reading
근거: 필기본 10·16·17쪽', 18, 3),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 흐름에 맞게 (A), (B)에 들어갈 단어를 바르게 짝지은 것은? (3점)
trip to / a lot of
근거: L4 Listen and Talk A-3', 19, 3),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸에 공통으로 들어갈 단어는? (2점)
be good at / look at
근거: 필기본 16·17쪽, L5 Enjoy the Clip A', 20, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q21.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r06/p21-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · (A)~(E)중 글의 흐름에 맞게 <보기>의 문장이 들어갈 곳으로 가장 알맞은 것은? (3점)
두 가지 음식 소개 다음, its unique bamboo smell 앞
근거: L4 본문 05~07', 21, 3),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q22.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r06/p21-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 윗글의 제목으로 가장 알맞은 것은? (2점)
담양에서 먹은 대통밥과 떡갈비
근거: L4 본문 01~09', 22, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q23.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r06/p23-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · (A)~(G)중 어법상 올바르지 않은 것을 있는 대로 고른 것은? (4점)
(A) goes → went / (C) finded → found / (G) were → was(주어 It)
근거: L4 본문 10~16', 23, 4),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q24.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r06/p23-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글의 흐름에 맞게 ⓐ에 들어갈 알맞은 것은? (2점)
go bad(상하다)
근거: L4 본문 19', 24, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 다음 글의 제목으로 가장 알맞은 것은? (2점)
꽃과 El이 함께 자람
근거: L5 본문 22~28', 25, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · (A)를 (B)로 같은 의미를 가진 문장으로 바꾸었을 때 어법상 올바른 것은? (3점)
like는 to부정사·동명사 모두 가능(뜻 같음) · want·decide는 to부정사, enjoy는 동명사, 주어는 To play/Playing
근거: 필기본 10·16쪽', 26, 3),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 밑줄 친 곳이 Ⓐ와 같은 쓰임으로 사용된 것은? (3점)
Ⓐ는 동명사 · 나머지는 진행형의 현재분사
근거: 필기본 16쪽', 27, 3),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q28.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r06/p28-29.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓒ에서 내용과 어법상 올바른 것으로만 고른 것은? (3점)
will + 동사원형 / can + 동사원형 / 전치사 like 뒤 목적격 us
근거: L5 본문 15·18·20', 28, 3),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q29.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r06/p28-29.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 윗글의 내용과 일치하지 않는 것은? (2점)
여름 내내 할머니 댁에 있음
근거: L5 본문 18', 29, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸에 들어갈 것이 다른 것은? (2점)
My mother is · 나머지는 are
근거: 필기본 1·4쪽', 30, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q31.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r06/p31-32.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · (A)~(G)중 어법상 올바른 것은 모두 몇 개인가? (4점)
올바른 것 (A)(B)(C)(D)(F) 5개 · (E) taked → took / (G) say → said
근거: L5 본문 08~14', 31, 4),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q32.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r06/p31-32.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 윗글에서 찾을 수 있는 단어의 뜻은?(단, 단어의 단·복수 및 동사의 변형은 상관없음.) (2점)
tiny · side by side, suddenly, decide, grade는 윗글에 없음
근거: 필기본 Lesson 5 단어', 32, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 대화의 흐름에 맞게 ⓐ에 들어갈 가장 알맞은 것은? (2점)
So I’m worried about tomorrow’s game.
근거: L5 Listen and Talk B', 33, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 동사의 변화형(원형 - 과거형 - 과거분사형)을 바르게 쓴 것은? (2점)
drank-drunk / slept-slept / led-led / rang-rung
근거: 필기본 13~15쪽', 34, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 다음 우리말을 바르게 영작한 것은? (3점)
remember + 동명사: (이미) 한 일을 기억하다
근거: 필기본 17쪽', 35, 3),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · (A)~(E)중 대화의 흐름과 어법상 올바르지 않은 것은? (2점)
(B) breaked → broke
근거: L5 Listen and Talk A-1', 36, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 흐름에 맞게 (A)~(E)에 들어갈 것 중 올바르지 않은 것은? (2점)
(D)에는 alone이 들어감
근거: L5 본문 01~07', 37, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법상 올바른 문장은 모두 몇 개인가? (4점)
올바른 문장 5개 · lives → live / live → lives / flys → flies
근거: 필기본 2~5쪽', 38, 4),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 대화의 내용과 일치하는 것은? (2점)
근거: L5 Listen and Talk A-2', 39, 2),
(@naesin_am1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r06/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 두 사람의 대화 흐름에 맞게 (A)~(D)를 바르게 나열한 것은?(한 사람은 한 번에 한 번씩만 이야기함.) (2점)
(B) → (D) → (C) → (A)
근거: L5 Review 1-1', 40, 2);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM1-2602-R07', 'NAESIN', '압구정중 1학년 2학기 기말 7회 · Lesson 3~5 예상문제 7회', 'Lesson 3~5 교과서·학습지 필기본 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_am1_r07 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM1-2602-R07');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · (A)~(E)중 대화의 흐름과 어법상 올바르지 않은 것은? (2점)
(C) have → has
근거: L3 Listen and Talk A-3', 1, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · <보기>에서 어법상 올바르지 않은 문장을 찾아 그 이유를 바르게 설명한 사람은? (3점)
(D) swiming → swimming · have가 ‘먹다’일 때는 진행형 가능
근거: 필기본 6쪽', 2, 3),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q03.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r07/p03-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · Ⓐ~Ⓔ 중 어법상 올바르지 않은 것은? (2점)
Ⓑ feel → feels
근거: L3 본문 15', 3, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q04.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r07/p03-04.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 밑줄 친 곳이 ⓐ와 같은 쓰임으로 사용된 것은? (4점)
ⓐ는 진행형의 현재분사 · 나머지는 동명사
근거: L3 본문 14, 필기본 16·17쪽', 4, 4),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 다음 우리말을 바르게 영작한 것은? (3점)
try + 동명사: 시험 삼아 ~해 보다
근거: 필기본 17쪽', 5, 3),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 밑줄 친 곳이 (A)와 같은 뜻으로 사용된 것은? (3점)
(A) run은 ‘상영하다’ · 나머지는 ‘달리다’
근거: 필기본 9쪽', 6, 3),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글의 흐름에 맞게 (A)~(E)에 들어갈 것 중 올바르지 않은 것은? (2점)
(B)에는 mistakes가 들어감
근거: L5 Listen and Talk B·A-2, 본문 15·19', 7, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 흐름과 어법상 (A)~(C)에 들어갈 알맞은 것은? (2점)
your dog(단수) is / She’s playing(진행형) / she has
근거: L3 Review 1-1', 8, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 동사의 변화형(원형 - 과거형 - 과거분사형)을 바르게 쓴 것은? (2점)
kept-kept / began-begun / stole-stolen / drew-drawn
근거: 필기본 13~15쪽', 9, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q10.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r07/p10-11.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓒ에서 내용과 어법상 올바른 것으로만 고른 것은? (3점)
these cats(복수) do / This action shows / want + to부정사
근거: L3 본문 03·07·11', 10, 3),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q11.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r07/p10-11.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 윗글의 내용과 일치하지 않는 것은? (2점)
Not at all!
근거: L3 본문 06', 11, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 다음 글의 내용과 일치하지 않는 것은? (2점)
it has black ears
근거: L3 Enjoy the Clip A', 12, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q13.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r07/p13-14.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 윗글의 (A)~(E)중 내용과 가장 관련이 없는 것은? (2점)
(D)는 L4 본문 04번 문장으로, 토끼 이야기와 관련 없음
근거: L3 본문 20~29', 13, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q14.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r07/p13-14.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 윗글에서 찾을 수 있는 단어의 뜻은?(단, 단어의 단·복수 및 동사의 변형은 상관없음.) (2점)
stretch · bow, moment, fear, scared는 윗글에 없음
근거: 필기본 단어 정리 1', 14, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸에 공통으로 들어갈 단어는? (2점)
in the box / in the same class / in the Smith family
근거: 필기본 1·20쪽', 15, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · <보기>의 말 다음에 올 부분을 올바르게 나열했을 때 ⓐ~ⓕ중 5번째에 오는 것은?(단, 한 사람은 한 번에 한 번씩만 이야기함.) (3점)
5번째는 Okay. Let’s find her together. · 순서 ⓐ → ⓓ → ⓑ → ⓒ → ⓔ → ⓕ
근거: L3 Listen and Talk B', 16, 3),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q17.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 대화의 흐름과 어법상 (A)~(C)에 들어갈 알맞은 것은? (2점)
과거 cooked / did + 동사원형 / want + to부정사
근거: L4 Review 1-1', 17, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글의 흐름에 맞게 빈칸에 들어갈 알맞은 것은? (2점)
It was tasty.
근거: L4 Listen and Talk A-3', 18, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 내용과 일치하지 않는 것은? (3점)
딸기 아이스크림을 고름
근거: L4 Listen and Talk A-1', 19, 3),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · (A)~(E)중 어법상 올바른 것은? (3점)
(A) wiser / (B) prettier / (D) the most expensive / (E) closer
근거: 필기본 20쪽 A', 20, 3),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · (A)~(E)중 글의 흐름과 어법상 올바른 것을 있는 대로 고른 것은? (3점)
(B) make → making / (D) are → is
근거: L4 Enjoy the Clip A', 21, 3),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q22.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r07/p22-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · Ⓐ를 바르게 영작한 것은? (3점)
go well together, 과거형 went
근거: L4 본문 09', 22, 3),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q23.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r07/p22-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 윗글을 읽고 답을 찾을 수 없는 것은? (2점)
아침 식사는 나오지 않음
근거: L4 본문 01~09', 23, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q24.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r07/p24-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 윗글의 (A)~(E)중 내용과 가장 관련이 없는 것은? (2점)
(E)는 L3 본문 26번 문장으로, 충무김밥 이야기와 관련 없음
근거: L4 본문 10~19', 24, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q25.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r07/p24-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 윗글의 내용과 일치하는 것은? (2점)
근거: L4 본문 19', 25, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · (A)~(F)중 어법상 올바른 것을 있는 대로 고른 것은? (4점)
(A) are → is / (C) not is → is not / (E) Is → Are
근거: 필기본 4쪽', 26, 4),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 대화의 흐름과 어법상 (A)~(C)에 들어갈 알맞은 것은? (2점)
Do you ~? / Does she ~? → No, she doesn’t.
근거: 필기본 5·9쪽', 27, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · (A)~(F)중 어법상 올바르지 않은 것을 있는 대로 고른 것은? (4점)
(B) becomed → became / (D) were → was / (E) haved → had
근거: L4 본문 20~28', 28, 4),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 대화의 흐름에 맞게 ⓐ에 들어갈 가장 알맞은 것은? (2점)
Sounds delicious.
근거: L4 Listen and Talk C', 29, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 대화의 흐름에 맞게 ⓐ에 들어갈 가장 알맞은 것은? (2점)
제안 Why don’t you ~?
근거: L5 Listen and Talk A-3', 30, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · (A)~(E)중 글의 흐름에 맞게 <보기>의 문장이 들어갈 곳으로 가장 알맞은 것은? (2점)
걱정 인형을 보여 준 뒤, Like this. 앞
근거: L5 Enjoy the Clip A', 31, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 대화의 (A)와 (B)에 들어갈 것으로 알맞은 것은? (2점)
presentation / upset
근거: L5 Listen and Talk C·A-1', 32, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸에 들어갈 것이 다른 것은? (2점)
The bus doesn’t · 나머지는 don’t
근거: 필기본 3·17쪽', 33, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 다음 글의 Ⓐ~Ⓔ 중 어법상 올바르지 않은 것은? (2점)
Ⓓ comed → came
근거: L5 본문 25', 34, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · (A)~(E)중 대화의 흐름과 어법상 올바르지 않은 것은? (2점)
(E) visiting → visit(Let’s + 동사원형)
근거: L5 Review 1-1', 35, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q36.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r07/p36-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · Ⓐ를 바르게 영작한 것은? (3점)
take의 과거형 took / with colorful flowers
근거: L5 본문 11', 36, 3),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q37.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r07/p36-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 밑줄 친 곳이 ⓐ와 같은 뜻으로 사용된 것은? (2점)
ⓐ like는 ‘~와 같은, ~처럼’ · 나머지는 ‘좋아하다’
근거: L5 본문 14, L4 본문 13', 37, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q38.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r07/p38-39.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · (A)~(G)중 어법상 올바른 것을 있는 대로 고른 것은? (4점)
(B) was → were / (C) reached → reach / (G) feeled → felt
근거: L5 본문 01~07', 38, 4),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q39.webp]]', '[[img:/naesin/apgujeong-m1-2026-2/r07/p38-39.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 윗글의 내용과 일치하는 것은? (2점)
근거: L5 본문 03', 39, 2),
(@naesin_am1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m1-2026-2/r07/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법상 올바른 문장은 모두 몇 개인가? (4점)
올바른 문장 5개 · are → is / does → do / feels → feel
근거: 필기본 9쪽', 40, 4);
