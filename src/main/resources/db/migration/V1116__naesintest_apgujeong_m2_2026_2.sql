-- 내신 실전 연습(/naesintest) — 압구정중 2학년 2026학년도 2학기 기말 대비 F2R 예상문제 16회분.
-- 원본: F2R이 만든 예상문제 문제지 PDF(117쪽)와 정답·해설 PDF(81쪽). 문항은 원본 PDF를 문항 단위로 잘라 그림으로 보여 준다
--   (밑줄·<보기> 상자·Ⓐ~Ⓒ 표를 원본 그대로 보이게 하려는 것). 그림: static/naesin/apgujeong-m2-2026-2/rNN/qNN.webp,
--   여러 문항이 같이 쓰는 지문은 pNN-NN.webp(body_text).
-- 정답 표와 문항별 해설의 정답이 730문항 모두 일치하고, 회차별 배점 합계가 문제지 표지 총점과 같음을 확인했다.
-- 1~10회·14~16회는 40문항, 11~13회(종합)는 원본대로 70문항이다.
-- kind='NAESIN', section='EN', task_type='NAESIN_MC5'(①~⑤ → answer_key A~E), points=문항 배점(V1115에서 추가한 칸).
-- 시험 화면은 풀이 중 해설을 보여 주지 않고(실제 시험 환경), 제출 후 복습 화면에서 explanation을 보여 준다.
-- 시간은 화면이 문항 수로 정한다(40문항 20분, 70문항 35분). 서버 마감은 7200초. exam_id는 exam_code로 회수한다.


INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM2-2602-R01', 'NAESIN', '압구정중 2학년 2학기 기말 1회 · Lesson 7 예상문제 1회', 'Lesson 7 학습지 필기본 1~11쪽 · 40문항 · 20분 · 134점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_r01 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM2-2602-R01');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 발음부호 (3점)
preserve의 발음부호는 [prɪˈzɜːrv]로 둘째 음절에 주강세가 있다.
근거: 필기본 01쪽 Lesson 7 단어 · preserve [prɪˈzɜːrv]', 1, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 발음부호 (3점)
glacier의 발음부호는 [ˈɡleɪʃər]로 첫째 음절에 주강세가 있다.
근거: 필기본 01쪽 Lesson 7 단어 · glacier [ˈɡleɪʃər]', 2, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이) (3점)
a group of animals or plants whose members are similar and can breed together는 species((생물분류상 의) 종)의 영어 뜻풀이이다.
근거: 필기본 01쪽 species 영어 뜻풀이', 3, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(숙어) (3점)
make an effort / make efforts: 노력하다. 환경을 보호하기 위해 노력하자.
근거: 필기본 01쪽 effort 예문·관련 표현(make an effort)', 4, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(빈칸) (3점)
고대 역사에 대해 배우기 위해 지역 박물관을 ''탐험하다 (explore)''가 자연스럽다.
근거: 필기본 02쪽 단어 연습 18번', 5, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(빈칸) (3점)
차례대로 wonderland, dynamic, product, control이 들 어간다. leopard(표범)는 어느 빈칸에도 들어갈 수 없다.
근거: 필기본 02쪽 wonderland 예문·단어 연습 8·16번, 01쪽 control 예문', 6, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(숙어) (3점)
agree with + 명사: ~에 동의하다. 우리는 사냥에 동의하지 않는다.
근거: 필기본 01쪽 hunting 예문·하단 필기(agree with + 명사)', 7, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(사역·준사역) (4점)
사역동사 make는 목적격 보어로 동사원형을 쓴다. makes us to run → makes us run. ⑤ help는 동사원형과 to부정 사 모두 가능하다.
어법 포인트: ④ to run (사역동사 make + 목적어 + 동사원형 → run)
근거: 필기본 03쪽 1·6·11·16번, 04쪽 41번', 8, 4),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(사역·준사역) (4점)
help + 목적어 + 동사원형/to부정사(to understand), get + 목적어 + 과거분사(cut, 수동 관계), let + 목적어 + 동사원형 (enter, ruin). understanding은 어디에도 들어갈 수 없다.
어법 포인트: help+목적어+동사원형/to부정사, get+목적어+과거 분사, let+목적어+동사원형 (동명사 understanding 불가)
근거: 필기본 04쪽 21·26·31·36번', 9, 4),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영작(사역동사) (4점)
He had the gardener plant new flowers in the garden. 사역동사 have + 목적어 + 동사원형이므로 다섯 번째 단어는 plant이다.
어법 포인트: have + 목적어 + 동사원형
근거: 필기본 03쪽 사역동사 예문(He had the gardener plant …)', 10, 4),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(가주어) (3점)
to부정사구 주어는 가주어 It을 앞에 두고 진주어 to부정사구 를 뒤로 보낸다. To cook spaghetti is easy. = It is easy to cook spaghetti.
어법 포인트: 가주어 It + be동사 + 형용사 + 진주어 to부정사
근거: 필기본 05쪽 가주어 It 예문', 11, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(가주어·의미상 주어) (4점)
Ⓐ 진주어가 to부정사구(to master …)이므로 to. Ⓑ 뒤에 주 어와 동사가 갖춰진 절(money cannot buy …)이 오므로 that. Ⓒ essential은 성격 형용사가 아니므로 의미상 주어는 for students.
어법 포인트: 진주어 to부정사 vs that절, 의미상 주어 for+목적 격
근거: 필기본 06쪽 16·31·36번', 12, 4),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(가주어) (4점)
옳은 문장은 첫째, 셋째, 넷째 문장으로 3개이다. 둘째 skip → to skip, 다섯째 live → to live로 고쳐야 한다.
어법 포인트: 둘째 skip → to skip, 다섯째 live → to live (가주 어 It – 진주어 to부정사)
근거: 필기본 05쪽 1·11번, 05쪽 예문(It is impossible to live without water), 06쪽 6·21번', 13, 4),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(사역·준사역) (4점)
made/had/let/helped 뒤에는 목적어 + 동사원형(finish)이 올 수 있다. get은 목적어 뒤에 to부정사를 써야 하므로(got the students to finish) 들어갈 수 없다.
어법 포인트: get + 목적어 + to부정사 (동사원형 불가)
근거: 필기본 03쪽 사역동사 예문(The teacher made the students finish …), 04쪽 준사역 help·get', 14, 4),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(종합) (4점)
옳은 것은 ⓐ, ⓒ, ⓖ이다. ⓑ to go → go, ⓓ hunt → to hunt, ⓔ for you → of you(foolish는 성격 형용사), ⓕ to hunting → to hunt.
어법 포인트: ⓑ to go → go(사역 make), ⓓ hunt → to hunt(진주어), ⓔ for you → of you(의미상 주어), ⓕ to hunting → to hunt(to부정사)
근거: 필기본 03쪽 예문, 05쪽 예문, 01쪽 illegal·monitor 예문, 02쪽 단어 연습 4번', 15, 4),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영작(가주어·의미상 주어) (4점)
It will be easy for her to solve the quiz. easy는 성격 형 용사가 아니므로 의미상 주어는 for her이고 다섯 번째 단어 는 for이다.
어법 포인트: 의미상 주어 for + 목적격
근거: 필기본 05쪽 의미상 주어 예문(It will be easy for her to solve the quiz.)', 16, 4),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q17.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(빈칸) (4점)
전치사 for 뒤에는 동명사가 온다. 국립 공원은 자연을 ''지키 는'' 장소이므로 preserving이 알맞다. hunting은 문맥에 맞 지 않는다.
어법 포인트: 전치사 for + 동명사(preserving)
근거: 필기본 07쪽 본문 A ① · 하단 필기(for preserving: 전치사 for 뒤 동명사)', 17, 4),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q18.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(표현) (3점)
''동시에''는 At the same time이다. At that time(그때), All the time(항상), For the first time(처음으로), In time(제 때에).
근거: 필기본 07쪽 본문 A ③ 해석', 18, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q19.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문) (4점)
Ⓒ 사역동사 let + 목적어 + 동사원형(enjoy). Ⓓ Let’s 뒤에 는 동사원형(take)이 온다.
어법 포인트: let+목적어+동사원형(enjoy), Let’s+동사원형 (take)
근거: 필기본 07쪽 본문 A ③④ · Grammar ③', 19, 4),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q20.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 일치 (3점)
공원에서 산, 호수, 심지어 커다란 빙원도 볼 수 있다고 했다.
① 칠레에 있다. ② 가장 멋진 빙하들 ''중 하나''이다. ③ 하루에 맑거나 비가 오거나 눈이 올 수 있다. ⑤ 커다란 빙원도 있다.
근거: 필기본 07쪽 본문 A ⑦⑨⑩⑪', 20, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q21.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(빈칸) (3점)
하루에 맑거나, 비가 오거나, 눈이 올 수 있다고 했으므로 날 씨가 ''역동적인(dynamic)'' 것이 알맞다.
근거: 필기본 07쪽 본문 A ⑧⑨ · 추가 질문(It is famous for its dynamic weather.)', 21, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q22.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영작(가주어) (4점)
It is exciting to walk on the glacier and enjoy the view. (12단어) 가주어 It, 진주어 to walk … and enjoy … 이고 열 번째 단어는 enjoy이다.
어법 포인트: 가주어 It – 진주어 to부정사구(to walk … and enjoy …)
근거: 필기본 07쪽 본문 A ⑫ · Grammar ⑫ 가주어 It', 22, 4),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q23.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p23-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 문장 넣기 (3점)
First, Second 다음에 Third가 이어지고, 마지막 노력은 There’s one more thing으로 덧붙이므로 Second 문장 뒤 ⓓ에 들어간다.
근거: 필기본 08쪽 본문 B ②③④⑤', 23, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q24.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p23-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(빈칸) (3점)
호텔들이 플라스틱을 쓰지 않고 ''친환경(eco-friendly)'' 제품 만 사용한다는 흐름이다.
근거: 필기본 08쪽 본문 B ⑤', 24, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q25.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p23-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(영영풀이) (3점)
①effort(efforts) ②control(controls) ③book ④preserve 는 글에 있다. ⑤ hunting(사냥)은 글에 없다.
근거: 필기본 01쪽 영어 뜻풀이(effort, control, book, preserve, hunting), 08쪽 본문 B', 25, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q26.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p23-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 일치 (3점)
공원이 방문객 수를 통제하므로 방문객들은 공원에 입장하려 면 예약해야 한다(Q3). ② 통제하는 것은 방문객 수이다. ③ 하이킹을 위해 개방된다. ④ 플라스틱을 쓰지 않는 것은 호텔 들이다. ⑤ 예약하는 것은 공원 입장이다.
근거: 필기본 08쪽 본문 B ②③⑤ · Q3', 26, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q27.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p27-29.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문) (4점)
주어 Many wild animals가 복수이므로 moves → move. ⓐ 주어 They는 복수, ⓑ 조동사 can + 동사원형, ⓓ 선행사 rhinos를 꾸미는 관계대명사 which, ⓔ 선행사 rhinos가 복 수이므로 are.
어법 포인트: ⓒ moves → move (복수 주어 Many wild animals와 수일치)
근거: 필기본 08쪽 본문 B ⑧~⑪ · Grammar ⑪', 27, 4),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q28.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p27-29.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(표현) (3점)
동사 move를 꾸미는 부사 freely(자유롭게)가 알맞다. for free는 ''무료로'', freedom은 명사이다.
근거: 필기본 08쪽 본문 B ⑩', 28, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q29.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p27-29.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 추론 (3점)
Many wild animals move around this park freely.라고 했으므로 야생 동물이 몇 마리밖에 없다는 ④는 유추할 수 없다.
근거: 필기본 08쪽 본문 B ⑧~⑪ · 추가 질문(It is in South Africa.)', 29, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q30.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p30-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 파악 (3점)
Big Five가 얼마나 오랫동안 위험에 처해 있었는지는 나오지 않는다. ① 과도한 사냥 때문에 ② Big Five를 보호하기 위해
③ 사냥 활동을 감시하기 위해 ④ 많은 자연 자원봉사 프로그 램.
근거: 필기본 09쪽 본문 C ①~④ · Q5', 30, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q31.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p30-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 연결어·표현 (3점)
Ⓐ 뒤에 명사구(too much hunting)가 오므로 because of.
Ⓑ 현재완료(has been)와 어울리는 ''처음부터'' from the start. Ⓒ 보호 노력을 하나 더 덧붙이므로 Also.
어법 포인트: because of + 명사(구)
근거: 필기본 09쪽 본문 C ①②④ · Grammar ①② · 하단 필기', 31, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q32.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 지칭·의미 (3점)
ⓒ present는 과거, 미래와 나란히 쓰여 ''현재''라는 뜻이다. ''선물''이 아니다.
근거: 필기본 09쪽 본문 C ⑤⑥⑦ 해석 · Vocabulary(present 현 재, monitor 감시하다), 01쪽 present', 32, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q33.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 연결어·표현 (3점)
국립 공원이 지구의 과거, 현재, 미래를 보존하므로 ''그것들 덕 분에'' 미래 세대도 자연의 경이로움을 경험할 수 있다. Because는 뒤에 절이 와야 한다.
근거: 필기본 09쪽 본문 C ⑦ · 하단 필기(Thanks to: ~덕분에)', 33, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q34.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 파악 (3점)
자원봉사자들은 불법 사냥 활동의 징후를 감시하고(살피고) 동물 덫을 제거한다. ② 이 일을 하는 것은 자원봉사자들이다.
③ 덫을 제거한다. ④ 과거·현재·미래를 보존한다. ⑤ 보존하 는 것은 국립 공원이다.
근거: 필기본 09쪽 본문 C ⑤⑥⑦ · Q6', 34, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q35.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 연결어·표현 (3점)
약 15,000년 전에 형성되었고 ''그 이후로'' 독특한 생태계를 형 성해 왔다(현재완료)는 흐름이므로 Since then이 알맞다.
근거: 필기본 10쪽 More Reading 1 · 하단 필기(has formed: 형 성해 왔다)', 35, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q36.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(지문) (4점)
뒤에 명사구(its history)가 오므로 because → because of. ⓑ 수동태(형성되었다), ⓒ 현재완료(형성해 왔다), ⓓ 단수 주어 this park에 맞춘 attracts, ⓔ 조동사 may + 동사원형.
어법 포인트: ⓐ because → because of (뒤에 명사구 its history)
근거: 필기본 10쪽 More Reading 1 · 하단 필기', 36, 4),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q37.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 불일치 (3점)
보호·관리 목적으로 방문이 닫힐 수 있다(it may close for visits for conservation purposes)고 했으므로 일 년 내내 열려 있다는 ②는 일치하지 않는다.
근거: 필기본 10쪽 More Reading 1 · T/F 2번 문제', 37, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q38.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 파악 (3점)
공원에 사는 거대한 육지거북의 수는 나오지 않는다. ① Ecuador ② giant tortoises, colorful birds, and clear blue waters ③ in the 1830s ④ 13 large islands, 17 small islands, and 40 rocks.
근거: 필기본 11쪽 More Reading 2', 38, 3),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q39.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법·어휘(지문) (4점)
ⓒ observed(관찰했다)는 visited와 나란히 쓰여 옳다. ⓐ one of the+최상급+복수명사 → treasures, ⓑ 다른 어디에 서도 찾을 수 없는 것이므로 common → special, ⓓ help+목적어+동사원형 → create, ⓔ 공원이 관리되는 것이 므로 수동태 monitored.
어법 포인트: ⓐ treasure → treasures(one of the+최상급+복 수명사), ⓓ creating → create(help+목적어+동사원형), ⓔ monitoring → monitored(수동태), ⓑ common → special(문맥)
근거: 필기본 11쪽 More Reading 2 · 하단 필기', 39, 4),
(@naesin_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r01/q40.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r01/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 일치 (3점)
갈라파고스 국립 공원은 에콰도르의 첫 번째 국립 공원이자 유네스코 세계 유산이다. ① first ② 13 large, 17 small ③ 1830s ⑤ 오늘날 공원은 관리되고 있으며 다윈이 관리하는 것 이 아니다.
근거: 필기본 11쪽 More Reading 2', 40, 3);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM2-2602-R02', 'NAESIN', '압구정중 2학년 2학기 기말 2회 · Lesson 7 예상문제 2회', 'Lesson 7 학습지 필기본 1~11쪽 · 40문항 · 20분 · 134점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_r02 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM2-2602-R02');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 발음부호 (3점)
dynamic의 발음부호는 [daɪˈnæmɪk]로 둘째 음절에 주강세 가 있다.
근거: 필기본 01쪽 Lesson 7 단어 · dynamic [daɪˈnæmɪk]', 1, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 발음부호 (3점)
present는 명사(현재, 선물)일 때 [ˈpreznt], 동사(제시하다) 일 때 [prɪˈzent]이다. 동사는 둘째 음절에 주강세가 있다.
근거: 필기본 01쪽 present [ˈpreznt] / 동사 [prɪˈzent], 02쪽 하단 필기(present: 명사/동사 강세)', 2, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(영영풀이) (3점)
to arrange to do or use something at a particular time in the future는 book(예약하다)의 영어 뜻풀이이다.
근거: 필기본 01쪽 book 영어 뜻풀이', 3, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(빈칸) (3점)
천천히 계곡을 따라 내려가는 것은 빙하(glacier)이다.
근거: 필기본 01쪽 glacier 예문', 4, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(빈칸) (3점)
수조를 깨끗하게 유지하도록 ''감시한다(monitors)''가 알맞다.
근거: 필기본 01쪽 monitor 예문', 5, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(빈칸) (3점)
차례대로 wonderland, icy, preserve, species가 들어간다. trap은 어느 빈칸에도 들어갈 수 없다.
근거: 필기본 02쪽 단어 연습 3·9·10·19번', 6, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(숙어) (3점)
under control: 통제되는, 통제하에. 경찰은 군중을 통제하 려고 했다.
근거: 필기본 01쪽 control 관련 표현(under control), 02쪽 단어 연습 20번', 7, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(사역·준사역) (4점)
사역동사 make + 목적어 + 동사원형(cancel)이므로 옳다.
① to go → go ③ knowing → know ④ fix → to fix ⑤ clean → to clean.
어법 포인트: ① to go → go(let), ③ knowing → know(let),
④ fix → to fix(get), ⑤ clean → to clean(get)
근거: 필기본 03쪽 2·7·17번, 04쪽 32·42번', 8, 4),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(종합) (4점)
틀린 문장은 둘째(to finish → finish), 셋째(installing → install / to install), 다섯째(watch → to watch)로 3개이다.
어법 포인트: 둘째 to finish → finish(사역 make), 셋째 installing → install/to install(준사역 help), 다섯째 watch → to watch(진주어 to부정사)
근거: 필기본 01쪽 illegal 예문, 04쪽 22·27·37번, 05쪽 2·12번', 9, 4),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(의미상 주어) (4점)
Ⓐ careless는 사람의 성격·태도를 나타내므로 of him. Ⓑ crucial은 성격 형용사가 아니므로 for us. Ⓒ 진주어 to부정 사구 to play with fire.
어법 포인트: 의미상 주어 of(성격 형용사)/for, 진주어 to부정사
근거: 필기본 06쪽 22·27·32번', 10, 4),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영작(사역동사) (4점)
My mom made me go to bed early. 사역동사 make + 목 적어 + 동사원형이므로 다섯 번째 단어는 go이다.
어법 포인트: make + 목적어 + 동사원형
근거: 필기본 03쪽 사역동사 예문(My mom made me go to bed early.)', 11, 4),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(의미상 주어) (4점)
의미상 주어 of + 목적격은 사람의 성격·태도를 나타내는 형용 사(kind, nice, generous, polite 등) 뒤에 쓴다. important 는 성격 형용사가 아니므로 for you를 써야 한다.
어법 포인트: 의미상 주어 of + 목적격 (성격 형용사 뒤)
근거: 필기본 05쪽 의미상 주어 설명(nice, kind, foolish, careful, wise, honest, rude), 06쪽 17·23번, 05~06쪽 14번 (polite)', 12, 4),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(가주어) (3점)
to부정사구 주어는 가주어 It을 앞에 두고 진주어 to부정사구 를 뒤로 보낸다. It is hard to wake up early on Sunday mornings.
어법 포인트: 가주어 It + be동사 + 형용사 + 진주어 to부정사
근거: 필기본 05쪽 7번 · 가주어 It 설명', 13, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(that 쓰임) (4점)
<보기>의 that은 가주어 It에 대한 진주어 that절을 이끈다. ④도 진주어 that절이다. ①③⑤는 관계대명사, ②는 ''그''라는 뜻의 지시형용사이다.
어법 포인트: It ~ that절(진주어)
근거: 필기본 06쪽 21·37번, 01쪽 product·baobab tree 뜻풀이, 02쪽 wonderland 뜻풀이, 10쪽 More Reading 1', 14, 4),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영작(가주어) (4점)
It is impossible to live without water. (7단어) 다섯 번째 단어는 live이다.
어법 포인트: 가주어 It – 진주어 to부정사
근거: 필기본 05쪽 가주어 예문(It is impossible to live without water.)', 15, 4),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(사역·가주어) (4점)
사역동사 make의 목적격 보어는 동사원형이다. finishing → finish.
어법 포인트: ③ finishing → finish (사역동사 make + 목적어 + 동사원형)
근거: 필기본 03쪽 사역동사 예문, 05쪽 가주어·의미상 주어 예문', 16, 4),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q17.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(문맥) (3점)
국립 공원은 자연을 지키는 장소이므로 동식물 종들을 ''안전하 게(safe)'' 지켜 준다. in danger(위험에 처한)는 문맥에 맞지 않는다.
근거: 필기본 07쪽 본문 A ① ② · Q1(They keep many animal and plant species safe.)', 17, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q18.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(표현) (3점)
take a look at: ~을 살펴보다. take care of·look after(돌 보다), look forward to(고대하다), look like(~처럼 보이다) 는 뜻이 다르다.
근거: 필기본 07쪽 본문 A ④ · Vocabulary(take a look at ~을 살 펴보다)', 18, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q19.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(문장 결합) (4점)
Can you believe you can experience four seasons in one day? believe 뒤 명사절 접속사 that을 생략하면 여섯 번째 단어는 experience이다.
어법 포인트: 명사절 접속사 that 생략(believe + (that) 주어 + 동사)
근거: 필기본 07쪽 본문 A ⑥ · Grammar ⑥(believe 다음 접속사 that 생략)', 19, 4),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q20.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p20-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 파악 (3점)
공원 입장 방법은 나오지 않는다. ② Chile ③ mountains, lakes, and even a big ice field ④ sunny, rainy, or snowy in a single day ⑤ in the park(칠레).
근거: 필기본 07쪽 본문 A ⑦~⑪ · 08쪽 Q3', 20, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q21.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p20-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문) (4점)
진주어 to walk … and enjoy …를 받는 가주어는 It이다. That → It. ⓐ one of the+최상급+복수명사, ⓑ 단수 주어 Grey Glacier에 맞춘 welcomes, ⓓ 진주어 to부정사, ⓔ to walk와 병렬인 (to) enjoy.
어법 포인트: ⓒ That → It (가주어 It – 진주어 to부정사)
근거: 필기본 07쪽 본문 A ⑪⑫ · Grammar ⑫ · 하단 필기(one of the+최상급+복수명사)', 21, 4),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q22.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p20-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 연결어·표현 (3점)
Ⓐ be famous for: ~로 유명하다. Ⓑ 볼 수 있는 것을 덧붙이 므로 Also. Ⓒ 빙원의 예로 Grey Glacier를 드므로 For example.
근거: 필기본 07쪽 본문 A ⑧⑩⑪ · 추가 질문(What is Torres del Paine National Park famous for?)', 22, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q23.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p20-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 불일치 (3점)
Grey Glacier는 칠레의 토레스 델 파이네 국립 공원에 있다. South Africa에 있는 것은 Kruger National Park이다. ④ 빙하 위를 걷는 것은 신난다고 했으므로 Grey Glacier 위를 걸을 수 있다.
근거: 필기본 07쪽 본문 A ⑦⑨⑩⑪⑫', 23, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q24.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p24-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 불일치 (3점)
공원은 방문객들이 떠날 때 쓰레기를 가져가도록 한다 (makes visitors take their trash with them). 쓰레기를 공원에 두고 갈 수 있다는 ④는 일치하지 않는다.
근거: 필기본 08쪽 본문 B ①~⑤ · Q3', 24, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q25.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p24-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(수동태 전환) (4점)
Visitors are made to take their trash with them when they leave. 사역동사 make의 수동태에서는 동사원형이 to 부정사로 바뀐다. 일곱 번째 단어는 trash이다.
어법 포인트: 사역동사 make의 수동태: be made + to부정사 (are made to take)
근거: 필기본 08쪽 본문 B ④ · Grammar ④', 25, 4),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q26.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p24-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 연결어·표현 (3점)
공원이 방문객 수를 통제하고, ''그래서'' 방문객들은 입장하려 면 예약해야 한다는 결과의 흐름이므로 so가 알맞다.
근거: 필기본 08쪽 본문 B ② 해석', 26, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q27.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p27-29.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(문맥) (3점)
크루거 국립 공원의 코뿔소들은 바오밥나무(baobab trees) 아래에서 잔다. 빙하(glaciers)는 문맥에 맞지 않는다.
근거: 필기본 08쪽 본문 B ⑪', 27, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q28.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p27-29.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(표현) (3점)
''운이 좋다면''은 If you are lucky이다. be동사 뒤에는 형용 사 lucky가 온다.
근거: 필기본 08쪽 본문 B ⑪ 해석', 28, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q29.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p27-29.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 주제 (3점)
Big Five가 무엇인지와 그것들을 모두 볼 수 있는 크루거 국 립 공원을 소개하는 글이다.
근거: 필기본 08쪽 본문 B ⑥~⑪ · 중심 내용 요약', 29, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q30.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p30-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(지문) (4점)
ⓐ because of 뒤에 명사구(too much hunting)가 와서 옳다. ⓑ Protect → To protect(목적), ⓒ have been → has been(주어 the park), ⓓ are use → are used(수동태), ⓔ to monitoring → to monitor(목적의 to부정사).
어법 포인트: ⓑ To protect(목적의 to부정사), ⓒ has been(현 재완료·단수 주어), ⓓ are used(수동태), ⓔ to monitor(목적의 to부정사)
근거: 필기본 09쪽 본문 C ①②③ · Grammar ①②③', 30, 4),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q31.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p30-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(표현) (3점)
''요즘에는''은 These days이다. In those days(그 당시에는), One day(어느 날), Some day(언젠가), The other day(며 칠 전에).
근거: 필기본 09쪽 본문 C ③ 해석', 31, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q32.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(영영풀이) (3점)
①present ②preserve ③monitor ④trap(traps)은 글에 있다. ⑤ explore(탐험하다)는 글에 없다.
근거: 필기본 01쪽 영어 뜻풀이(present, preserve, monitor, trap, explore), 09쪽 본문 C', 32, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q33.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 파악 (3점)
자원봉사자의 수는 나오지 않는다. ① many nature volunteer programs ③ Q6 ④ the Earth’s past, present, and future ⑤ animal traps.
근거: 필기본 09쪽 본문 C ④~⑥ · Q6 · 추가 질문', 33, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q34.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(지문) (4점)
옳은 것은 ⓑ, ⓓ, ⓔ이다. ⓐ 단수 주어 the park → runs, ⓒ monitor와 병렬이고 주어가 Volunteers → remove, ⓕ will 뒤에 can을 쓸 수 없으므로 be able to, ⓖ many + 복 수명사 → wonders.
어법 포인트: ⓐ run → runs(단수 주어), ⓒ removes → remove(복수 주어·병렬), ⓕ can → be able to(will 뒤 조동사 불가), ⓖ wonder → wonders(many+복수명사)
근거: 필기본 09쪽 본문 C ④~⑦ · Grammar ⑦(will be able to)', 34, 4),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q35.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 지칭 (3점)
ⓐ This는 앞의 ''땅과 바다를 모두 가진 공원!''이라는 말을 가 리키며, 그것이 태안해안 국립 공원을 묘사한다는 뜻이다. 신 두리 해안 사구가 아니다.
근거: 필기본 10쪽 More Reading 1 · 하단 필기', 35, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q36.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 일치 (3점)
보호·관리 목적으로 방문이 닫힐 수 있으므로(it may close for visits) 어떤 기간에는 방문할 수 없다. ① Korea ② 바다 의 모든 생물 ④ 약 15,000년 전 ⑤ beautiful islands.
근거: 필기본 10쪽 More Reading 1 · 표 Conservation Note', 36, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q37.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 연결어·표현 (3점)
보호·관리 목적으로 방문이 닫힐 수 있으므로 ''그래서'' 방문을 계획한다면 그 기간을 피하라는 흐름이다.
근거: 필기본 10쪽 More Reading 1', 37, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q38.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문) (4점)
선행사 special plants and animals가 있으므로 what이 아 니라 관계대명사 that(which)을 써야 한다. ⓐ be made up of(~로 구성되다), ⓒ 과거 시제, ⓓ 단수 주어 It, ⓔ 선행사 visitors가 복수이므로 who want.
어법 포인트: ⓑ what → that (선행사가 있으면 관계대명사 that/which)
근거: 필기본 11쪽 More Reading 2 · 하단 필기(be made up of)', 38, 4),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q39.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 파악 (3점)
공원은 거대한 육지거북과 색색의 새들로 유명하므로 방문객 들은 그것들을 볼 수 있다. ① Ecuador ② 1830s ③ 진화론 을 만든 것은 다윈 ⑤ 방문객이 관리하는 것이 아니다.
근거: 필기본 11쪽 More Reading 2 · 1번 문제 ②', 39, 3),
(@naesin_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r02/q40.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r02/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 주제 (3점)
갈라파고스 국립 공원의 구성, 특별한 동식물, 다윈의 연구, 오늘날의 관리와 방문객을 소개하는 글이다.
근거: 필기본 11쪽 More Reading 2', 40, 3);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM2-2602-R03', 'NAESIN', '압구정중 2학년 2학기 기말 3회 · Lesson 7 예상문제 3회', 'Lesson 7 학습지 필기본 1~11쪽 · 40문항 · 20분 · 135점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_r03 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM2-2602-R03');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 발음부호 (3점)
illegal의 발음부호는 [ɪˈliːɡəl]로 둘째 음절에 주강세가 있다. 나머지는 필기본 표기와 같다.
근거: 필기본 01쪽 Lesson 7 단어 · illegal [ɪˈliːɡəl]', 1, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 발음부호 (3점)
species의 발음부호는 [ˈspiːʃiːz]로 첫째 음절에 주강세가 있다. 나머지는 필기본 표기와 같다.
근거: 필기본 01쪽 Lesson 7 단어 · species [ˈspiːʃiːz]', 2, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이) (3점)
wonderland(아주 멋진 곳)의 영어 뜻풀이이다.
근거: 필기본 02쪽 wonderland 뜻풀이', 3, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(빈칸) (3점)
present(동사): 제시하다. ‘그 팀은 내일 아침 이사회에 제안 서를 제시할 것이다.’
근거: 필기본 02쪽 단어 연습 11번(present: 제시하다)', 4, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(빈칸) (3점)
차례대로 explore(탐험하다), icy(얼음에 뒤덮인), product(제품), species(종)가 들어간다. hunting(사냥)은 어느 빈칸에도 알맞지 않다.
근거: 필기본 01쪽 explore·icy·product·species 예문', 5, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(빈칸) (3점)
control: 통제하다. ‘너는 지출을 통제해야 한다.’
근거: 필기본 01쪽 control 예문', 6, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(숙어) (3점)
make an effort: 노력하다. ‘그 과학자는 마감 전에 연구를 끝내기 위해 끊임없이 노력했다.’
근거: 필기본 01쪽 effort 관련 표현(make an effort) · 02쪽 단어 연습 1번', 7, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(사역·준사역) (4점)
let+목적어+동사원형이므로 to go를 go로 고쳐야 한다. get+목적어+과거분사(repaired)는 목적어와 보어가 수동 관 계일 때 쓴다.
어법 포인트: 사역동사 let+목적어+동사원형 (to go → go)
근거: 필기본 03쪽 e.g.·Step 1 3번·8번·Step 2 13번, 04쪽 43번', 8, 4),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(사역·준사역) (4점)
help+목적어+동사원형/to부정사(prepare), have+목적어 +동사원형(wash, plant), get+목적어+to부정사(to stop)이다. washed는 어느 빈칸에도 들어갈 수 없다.
어법 포인트: have+목적어+동사원형 / help+목적어+동사원형·to 부정사 / get+목적어+to부정사
근거: 필기본 03쪽 e.g., 04쪽 23번·33번·38번', 9, 4),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(사역동사 수동태) (4점)
사역동사 make가 쓰인 문장을 수동태로 바꾸면 목적격 보어 인 동사원형이 to부정사가 된다. The students were made to finish their homework on time (by the teacher). 네 번째 단어는 made이다.
어법 포인트: 사역동사 make의 수동태: be made + to부정사
근거: 필기본 03쪽 e.g. The teacher made the students finish their homework on time.', 10, 4),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(가주어 It) (4점)
가주어 It과 진주어 to부정사가 바르게 쓰인 문장은 It is necessary to wear ~이다. 나머지는 impossible to live, generous of her, obvious that she, It is important로 고 쳐야 한다.
어법 포인트: 가주어 It+진주어 to부정사 / of+목적격(성격 형용 사) / It ~ that절
근거: 필기본 05쪽 e.g.·1번·3번, 06쪽 18번·23번', 11, 4),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(의미상 주어) (4점)
사람의 성격·특징을 나타내는 형용사(foolish, kind, rude, wise) 뒤에는 의미상 주어로 of+목적격을 쓴다. necessary는 for+목적격을 쓰므로 들어갈 수 없다.
어법 포인트: to부정사의 의미상 주어: 성격·특징 형용사+of+목적 격 / 그 밖의 형용사+for+목적격
근거: 필기본 05쪽 의미상 주어 설명과 e.g. It was foolish of you to say so.', 12, 4),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영작(가주어 It) (4점)
It is illegal to hunt these animals. (총 7단어) 다섯 번째 단어는 hunt이다.
어법 포인트: 가주어 It+진주어 to부정사
근거: 필기본 01쪽 illegal 예문 It is illegal to hunt these animals.', 13, 4),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(종합) (4점)
틀린 문장은 to crossing(→ to cross), to he(→ that he), to carry(→ carry)가 쓰인 3개이다.
어법 포인트: 진주어 to+동사원형 / It ~ that절 / make+목적어 +동사원형
근거: 필기본 03쪽 e.g.·18번, 04쪽 28번, 05쪽 e.g.·13번, 06쪽 28 번·33번·38번', 14, 4),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(가주어 It) (3점)
to부정사구 주어를 뒤로 보내고 가주어 It을 쓰면 It is healthy to eat fresh fruits and vegetables.가 된다.
어법 포인트: 가주어 It+진주어 to부정사
근거: 필기본 05쪽 8번 It is healthy to eat fresh fruits and vegetables. · 가주어–진주어 설명', 15, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(to부정사·의미상 주어) (3점)
Ⓐ ‘~하기 위해’라는 목적의 to부정사 to see Ⓑ 가주어 It의 진주어 to부정사 to cook Ⓒ to부정사의 의미상 주어는 for+목적격(easy는 성격 형용사가 아님)
어법 포인트: Ⓐ 목적의 to부정사 / Ⓑ 진주어 to부정사 / Ⓒ 의미 상 주어 for+목적격
근거: 필기본 02쪽 단어 연습 13번 · 05쪽 e.g.', 16, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q17.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(지문) (4점)
사역동사 let+목적어+동사원형이므로 ⓓ to enjoy는 enjoy 가 되어야 한다. ⓐ 전치사 for 뒤 동명사, ⓑ 주어 They(National parks)에 맞는 동사, ⓒ keep+목적어+형용 사(safe), ⓔ two ~ parks는 옳다.
어법 포인트: ⓓ 사역동사 let+목적어+동사원형 (to enjoy → enjoy)
근거: 필기본 07쪽 본문 A ①②③④ · Grammar ③ · 필기(for preserving)', 17, 4),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q18.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 연결어 (3점)
종들을 안전하게 지켜 주는 일과 사람들이 자연의 아름다움을 즐기게 해 주는 일이 함께 이루어지므로 At the same time(동시에)이 알맞다.
근거: 필기본 07쪽 본문 A ③', 18, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q19.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 일치 (3점)
국립 공원은 많은 동물과 식물 종들을 안전하게 지켜 준다. 국 립 공원은 자연을 지키는 곳이고, 살펴볼 두 공원은 각각 남아 메리카와 아프리카에 있다.
근거: 필기본 07쪽 본문 A ①②③④ · Comprehension Check Q1', 19, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q20.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p20-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 주제 (3점)
칠레 토레스 델 파이네 국립 공원의 역동적인 날씨와 산·호수· 빙원·빙하 같은 자연을 소개하는 글이다. Grey Glacier는 그 예로 든 것이다.
근거: 필기본 07쪽 중심 내용 요약(토레스 델 파이네 국립 공원과 역동적인 날씨·자연경관 소개)', 20, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q21.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p20-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 지칭 (3점)
ⓒ this park는 커다란 빙원이 아니라 토레스 델 파이네 국립 공원을 가리킨다. ⓐ this는 하루에 사계절을 경험하는 것(필 기), ⓔ the glacier는 앞 문장의 Grey Glacier를 가리킨다.
근거: 필기본 07쪽 필기(this(7번): 하루에 사계절을 경험하는 것) · 본문 A ⑧⑩⑪⑫', 21, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q22.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p20-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 추론 (3점)
하루에 맑거나, 비가 오거나, 눈이 올 수 있다고 했으므로 항 상 맑다는 내용은 유추할 수 없다. Grey Glacier는 칠레에서 가장 멋진 빙하들 중 하나이므로 다른 빙하들도 있다.
근거: 필기본 07쪽 본문 A ⑨⑩⑪⑫', 22, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q23.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p20-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(최상급) (4점)
‘가장 ~한 것들 중 하나’는 one of the+최상급+복수명사로 쓴다.
어법 포인트: one of the+최상급+복수명사 (one of the most wonderful glaciers)
근거: 필기본 07쪽 본문 A ⑪ · 필기(one of the+최상급+복수명사)', 23, 4),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q24.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p24-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(지문 ⓐ~ⓖ) (4점)
옳은 것은 ⓑ controls(주어 it), ⓒ book(조동사 should 뒤 동사원형), ⓕ leave이다. ⓐ to preserve(목적의 to부정사), ⓓ are(주어 trails가 복수), ⓔ take(make+목적어+동사원 형), ⓖ use(주어 the hotels가 복수)로 고쳐야 한다.
어법 포인트: ⓐ 목적의 to부정사 / ⓓ 주어·동사 수일치 / ⓔ 사역 동사 make+목적어+동사원형 / ⓖ 주어·동사 수일치
근거: 필기본 08쪽 본문 B ①~⑤ · Grammar ①④', 24, 4),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q25.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p24-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 파악 (3점)
방문객들은 떠날 때 쓰레기를 가져가야 하므로 공원에 쓰레기 를 두고 가면 안 된다. 예약해야 입장할 수 있고, 일부 산책로 만 열려 있으며, 호텔은 플라스틱을 쓰지 않고, 방문객 수를 통제하는 것은 공원이다.
근거: 필기본 08쪽 본문 B ②③④⑤', 25, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q26.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p24-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이) (3점)
effort(efforts), control(controls), book, product(products)는 글에 있다. ‘covered with or having ice’는 icy의 뜻풀이로 글에 없다.
근거: 필기본 01쪽 영어 뜻풀이 · 08쪽 본문 B ①②⑤', 26, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q27.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p27-29.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 일치 (3점)
크루거 국립 공원에서 Big Five(사자, 표범, 코끼리, 코뿔소, 버팔로)를 모두 볼 수 있다. 곰이 아니라 버팔로이고, 바오밥 나무 아래에서 자는 것은 코뿔소이며, 동물들은 공원을 자유 롭게 돌아다닌다.
근거: 필기본 08쪽 본문 B ⑧⑨⑩⑪', 27, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q28.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p27-29.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(지문 짝짓기) (4점)
Ⓑ 주어 Many wild animals가 복수 → move Ⓒ 선행사 rhinos가 동물 → which Ⓓ 선행사 rhinos가 복수이므로 관 계대명사절 동사도 복수 → are
어법 포인트: Ⓑ 주어·동사 수일치 / Ⓒ 선행사가 동물일 때 관계대 명사 which / Ⓓ 관계대명사절 동사의 수일치
근거: 필기본 08쪽 본문 B ⑩⑪ · Grammar ⑪', 28, 4),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q29.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p27-29.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(표현) (3점)
경험을 묻는 현재완료 의문문은 Have+주어+과거분사 ~?이다. Have you heard about the Big Five?
근거: 필기본 08쪽 본문 B ⑦ · Grammar ⑦(현재완료 의문문, 경 험)', 29, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q30.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p30-32.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 일치 (3점)
Big Five를 보호하기 위해 공원은 처음부터 사냥 금지 지역이 었다. Big Five는 과도한 사냥 때문에 위험하고, 공원은 많은 자연 자원봉사 프로그램을 운영한다.
근거: 필기본 09쪽 본문 C ①②④', 30, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q31.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p30-32.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영작(수동태·to부정사) (4점)
These days, drones and helicopters are used to monitor hunting activities. (총 11단어) 여덟 번째 단어는 to이다.
어법 포인트: 수동태 are used + 목적의 to부정사 to monitor
근거: 필기본 09쪽 본문 C ③ · Grammar ③', 31, 4),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q32.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p30-32.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 파악 (3점)
공원이 헬리콥터를 몇 대 쓰는지는 나오지 않는다. 나머지는 too much hunting, To protect the Big Five, nature volunteer programs, from the start로 답할 수 있다.
근거: 필기본 09쪽 본문 C ①②③④ · Comprehension Check Q5', 32, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q33.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p33-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문 짝짓기) (4점)
Ⓑ 주어 Volunteers가 복수이고 monitor와 병렬 → remove Ⓒ 주어 National parks가 복수 → preserve Ⓓ will 뒤에는 조동사 can을 쓸 수 없으므로 be able to
어법 포인트: Ⓑ Ⓒ 주어·동사 수일치 / Ⓓ will be able to (조동 사 연속 불가)
근거: 필기본 09쪽 본문 C ⑤⑥⑦ · Grammar ⑦', 33, 4),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q34.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p33-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(표현) (3점)
‘불법 사냥 활동의 징후’는 signs of illegal hunting activities이다. 형용사 illegal이 hunting activities를 꾸민다.
근거: 필기본 09쪽 본문 C ⑤ · Vocabulary illegal(불법적인) · Comprehension Check Q6', 34, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q35.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 파악 (3점)
섬이 몇 개인지는 나오지 않는다. 해안 국립 공원(땅과 바다가 함께 있는 공원), 갯벌·모래 언덕·아름다운 섬들, 약 15,000년 전 형성, 보호·관리 목적으로 답할 수 있다.
근거: 필기본 10쪽 More Reading 1 · 표 완성 문제', 35, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q36.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 연결어 (3점)
지금은 많은 방문객을 끌어들이지만 보호·관리 목적으로 방문 을 막을 수도 있다는 상반된 내용이 이어지므로 But이 알맞다.
근거: 필기본 10쪽 More Reading 1 끝의 세 문장', 36, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q37.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문) (4점)
ⓑ offer는 주어 They(the mudflats)에 맞는 동사로 옳다. ⓐ are(주어 The mudflats가 복수), ⓒ interesting(흥미로 운), ⓓ formed(현재완료 has+과거분사), ⓔ attracts(문장 의 동사)로 고쳐야 한다.
어법 포인트: ⓐ 주어·동사 수일치 / ⓒ interesting(가주어 It is interesting to visit) / ⓓ 현재완료 has formed / ⓔ 문장의 동 사(attracts)
근거: 필기본 10쪽 More Reading 1 · 필기(it is interesting to visit: 가주어–진주어, has formed: 현재완료)', 37, 4),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q38.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 일치 (3점)
다윈의 연구는 그가 진화론을 만드는 데 도움이 되었다. 진화 론은 동물들이 변하는 방식을 설명하고, 공원은 거대한 육지 거북과 다채로운 새들로 유명하며, 다윈은 1830년대에 방문했 고, 섬과 바위의 수가 다르다.
근거: 필기본 11쪽 More Reading 2', 38, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q39.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(문맥) (3점)
방문객들은 공원의 자연의 아름다움을 탐험하기(explore) 위 해 오는 것이므로 ⓔ avoid(피하다)는 문맥에 맞지 않다.
근거: 필기본 11쪽 More Reading 2 (visitors who want to explore its natural beauty)', 39, 3),
(@naesin_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r03/q40.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r03/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(지문 짝짓기) (4점)
Ⓐ be made up of: ~로 구성되다 → made Ⓑ help+목적어 +동사원형 → create Ⓒ 공원이 감시·관리되는 것이므로 수동 태 → monitored
어법 포인트: Ⓐ 수동태 be made up of / Ⓑ help+목적어+동사 원형 / Ⓒ 수동태 is monitored
근거: 필기본 11쪽 More Reading 2 · 필기(be made up of, help+목적어+동사원형)', 40, 4);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM2-2602-R04', 'NAESIN', '압구정중 2학년 2학기 기말 4회 · Lesson 7 예상문제 4회', 'Lesson 7 학습지 필기본 1~11쪽 · 40문항 · 20분 · 136점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_r04 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM2-2602-R04');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 발음부호 (3점)
explore의 발음부호는 [ɪkˈsplɔːr]로 둘째 음절에 주강세가 있다. 나머지는 필기본 표기와 같다.
근거: 필기본 01쪽 Lesson 7 단어 · explore [ɪkˈsplɔːr]', 1, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 발음부호 (3점)
leopard의 발음부호는 [ˈlepərd]로 철자 o는 소리 나지 않는다. 나머지는 필기본 표기와 같다.
근거: 필기본 01쪽 Lesson 7 단어 · leopard [ˈlepərd] (사진 강조 단어)', 2, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(영영풀이) (3점)
icy(얼음에 뒤덮인)의 영어 뜻풀이이다. glacier는 a slowly moving mass or river of ice ~이다.
근거: 필기본 01쪽 icy 뜻풀이', 3, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(빈칸) (3점)
preserve: 보존하다. ‘전통 지역 공동체들은 그들의 문화유산 을 보존하기 위해 열심히 노력한다.’
근거: 필기본 02쪽 단어 연습 19번 · 01쪽 preserve 관련 표현 (preserve nature/culture)', 4, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(빈칸) (3점)
차례대로 glacier(빙하), leopard(표범), baobab tree(바오 밥 나무), species(종)가 들어간다. dynamic(역동적인)은 형 용사로 어느 빈칸에도 들어갈 수 없다.
근거: 필기본 01쪽 glacier·leopard·baobab tree·species 예문', 5, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(표현) (3점)
at risk of: ~의 위험에 처한. ‘수천 종의 식물과 동물이 현재 멸종 위기에 처해 있다.’
근거: 필기본 02쪽 단어 연습 9번', 6, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(표현) (3점)
take strong measures: 강력한 조치를 취하다.
근거: 필기본 02쪽 단어 연습 5번 · 필기(take strong measures: 강력한 조치를 취하다)', 7, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(사역동사) (4점)
make+목적어+동사원형이므로 to study를 study로 고쳐야 한다.
어법 포인트: 사역동사 make+목적어+동사원형 (to study → study)
근거: 필기본 03쪽 4번·9번·19번, 04쪽 24번·29번', 8, 4),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(사역·준사역) (4점)
get+목적어+to부정사(to open), get+목적어+과거분사 (stolen, 수동 관계), help+목적어+동사원형/to부정사 (carry), let+목적어+동사원형(use)이다. using은 어느 빈칸 에도 들어갈 수 없다.
어법 포인트: get+목적어+to부정사/과거분사 · help+목적어+동 사원형 · let+목적어+동사원형
근거: 필기본 03쪽 19번, 04쪽 34번·39번·44번', 9, 4),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영작(사역동사) (4점)
She let her son eat ice cream. (총 7단어) 사역동사 let 뒤 에 목적어+동사원형이 온다. 다섯 번째 단어는 eat이다.
어법 포인트: 사역동사 let+목적어+동사원형
근거: 필기본 03쪽 e.g. She let her son eat ice cream.', 10, 4),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(가주어 It) (4점)
가주어 It, 의미상 주어 for applicants, 진주어 to submit가 바르게 쓰였다. 나머지는 fun to play, difficult to find, turns out that, of you to say로 고쳐야 한다.
어법 포인트: 가주어 It+(for 목적격)+to부정사 / It turns out that절
근거: 필기본 05쪽 4번·9번·e.g., 06쪽 24번·29번', 11, 4),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(가주어·의미상 주어) (4점)
차례대로 to(진주어 to부정사), that(진주어 that절), of(성격 형용사+of+목적격), for(의미상 주어)가 들어간다. with는 들 어갈 수 없다.
어법 포인트: 진주어 to부정사·that절 / 의미상 주어 for·of
근거: 필기본 06쪽 14번·19번·34번·39번', 12, 4),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(사역동사 수동태) (4점)
사역동사 make의 수동태는 be made+to부정사이다. I was made to cry by the sad music. 네 번째 단어는 to이다.
어법 포인트: 사역동사 make의 수동태: be made + to부정사
근거: 필기본 03쪽 Step 2 14번 The sad music made me cry.', 13, 4),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(종합) (4점)
옳은 문장은 made me go, made the students finish, It is impossible to live, It is strictly illegal to hunt의 4개 이다. let anyone enter, dangerous to swim, easy for her로 고쳐야 한다.
어법 포인트: 사역동사 make/let+목적어+동사원형 / 진주어 to부 정사 / 의미상 주어 for
근거: 필기본 03쪽 e.g., 04쪽 21번, 05쪽 e.g., 02쪽 단어 연습 4번', 14, 4),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(가주어 It) (3점)
가주어 It 대신 진주어 to부정사구를 문장 앞에 쓰면 To hunt these animals is illegal.이 된다. to부정사구 주어는 단수 취급한다.
어법 포인트: 가주어 It+진주어 to부정사 ↔ to부정사구 주어
근거: 필기본 01쪽 illegal 예문 · 05쪽 가주어–진주어 설명', 15, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(to부정사·사역동사) (3점)
Ⓐ Ⓑ ‘~하기 위해’라는 목적의 to부정사 to keep, to catch
Ⓒ 사역동사 have+목적어+동사원형이므로 explain
어법 포인트: Ⓐ Ⓑ 목적의 to부정사 / Ⓒ 사역동사 have+목적어 +동사원형
근거: 필기본 01쪽 monitor·trap 예문 · 04쪽 25번', 16, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q17.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문) (4점)
ⓑ keep+목적어+형용사(safe)는 옳다. ⓐ 전치사 for 뒤에는 동명사 preserving, ⓒ let+목적어+동사원형 enjoy, ⓓ Let’s+동사원형 take, ⓔ 하루에 경험하는 것은 사계절 (seasons)이다.
어법 포인트: ⓐ 전치사+동명사 / ⓒ 사역동사 let+목적어+동사원 형 / ⓓ Let’s+동사원형 / ⓔ 문맥(seasons)
근거: 필기본 07쪽 본문 A ①②③④⑥ · Grammar ③ · 필기(for preserving)', 17, 4),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q18.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(영영풀이) (3점)
preserve, species, explore(Exploring), dynamic(Dynamic)은 글에 있다. ‘to limit something or make it happen in a particular way’는 control의 뜻풀이 로 글에 없다.
근거: 필기본 01쪽 영어 뜻풀이 · 07쪽 본문 A ①②⑤', 18, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q19.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(표현) (3점)
‘자연의 아름다움’은 명사 beauty와 nature를 써서 the beauty of nature로 쓴다.
근거: 필기본 07쪽 본문 A ③ · Vocabulary beauty(아름다움)', 19, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q20.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p20-21.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 연결어 (3점)
역동적인 날씨에 더해 산, 호수, 빙원도 볼 수 있다는 내용을 덧붙이므로 Also(또한)가 알맞다.
근거: 필기본 07쪽 본문 A ⑩', 20, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q21.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p20-21.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문) (4점)
ⓒ one of the+최상급+복수명사이므로 glaciers는 옳다. ⓐ is(주어 Torres del Paine National Park 단수), ⓑ be(조동 사 can+동사원형), ⓓ welcomes(주어 Grey Glacier 단수), ⓔ It(가주어)으로 고쳐야 한다.
어법 포인트: ⓐⓓ 주어·동사 수일치 / ⓑ 조동사+동사원형 / ⓔ 가주어 It
근거: 필기본 07쪽 본문 A ⑦⑨⑪⑫ · Grammar ⑫ · 필기(one of the+최상급+복수명사)', 21, 4),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q22.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p22-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 지칭·의미 (3점)
ⓒ them은 쓰레기가 아니라 방문객들(visitors)을 가리킨다. ‘방문객들이 떠날 때 쓰레기를 (그들이) 가지고 가도록 한다.’
근거: 필기본 08쪽 본문 B ②④⑤ 해석', 22, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q23.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p22-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 연결어 (3점)
공원이 자연의 아름다움을 보존하기 위해 하는 노력을 차례대 로 나열하므로 First, Second, Third가 알맞다.
근거: 필기본 08쪽 본문 B ②③④', 23, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q24.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p22-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 주제 (3점)
방문객 수 통제, 일부 산책로 개방, 쓰레기 가져가기, 친환경 제품 사용 등 공원이 자연의 아름다움을 보존하기 위해 하는 노력을 소개하는 글이다.
근거: 필기본 08쪽 본문 B ①~⑤ · 중심 내용 요약', 24, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q25.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p25-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(지문) (4점)
현재완료 의문문은 Have+주어+과거분사이므로 ⓐ hear는 heard가 되어야 한다. ⓑ They(the Big Five) are, ⓒ them(the Big Five), ⓓ 주어 Many wild animals(복수)에 맞는 move, ⓔ 동사를 꾸미는 부사 freely는 옳다.
어법 포인트: ⓐ 현재완료 Have+주어+과거분사 (hear → heard)
근거: 필기본 08쪽 본문 B ⑦⑧⑨⑩ · Grammar ⑦', 25, 4),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q26.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p25-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 파악 (3점)
크루거 국립 공원에 사는 코뿔소의 수는 나오지 않는다. 나머 지는 lions, leopards, elephants, rhinos, and buffaloes / in South Africa / freely / Yes로 답할 수 있다.
근거: 필기본 08쪽 본문 B ⑧⑨⑩ · Comprehension Check Q4 · 추가 질문', 26, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q27.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p25-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(관계대명사) (4점)
선행사 rhinos가 동물이므로 관계대명사 which를 쓰고, 복 수 선행사에 맞게 are sleeping으로 쓴다.
어법 포인트: 선행사가 동물일 때 관계대명사 which / 관계사절 동사 수일치(are)
근거: 필기본 08쪽 본문 B ⑪ · Grammar ⑪', 27, 4),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q28.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 불일치 (3점)
공원은 Big Five를 보호하기 위해 처음부터 사냥 금지(no- hunting) 지역이었다.
근거: 필기본 09쪽 본문 C ①②③④', 28, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q29.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(지문 짝짓기) (4점)
Ⓐ 뒤에 명사구 too much hunting이 오므로 because of
Ⓑ ‘~을 보호하기 위해’라는 목적의 to부정사 To protect Ⓒ 주어 the park가 단수이고 처음부터 지금까지 계속되므로 현 재완료 has been
어법 포인트: Ⓐ because of+명사(구) / Ⓑ 목적의 to부정사 / Ⓒ 현재완료 has been(계속)·수일치
근거: 필기본 09쪽 본문 C ①② · Grammar ①②', 29, 4),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q30.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(목적의 to부정사) (4점)
‘~하기 위해’는 목적의 to부정사로 쓴다. are used to monitor: 감시하기 위해 사용된다. be used to+-ing(~에 익 숙하다)와 구별한다.
어법 포인트: 수동태 are used + 목적의 to부정사 (to monitor)
근거: 필기본 09쪽 본문 C ③ · Grammar ③', 30, 4),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q31.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p31-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 추론 (3점)
자원봉사자들은 동물 덫을 제거하므로 덫을 공원에 남겨 둔다 는 것은 유추할 수 없다.
근거: 필기본 09쪽 본문 C ⑤⑦ · Comprehension Check Q6', 31, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q32.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p31-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(지문) (4점)
주어 National parks가 복수이므로 ⓓ preserves는 preserve가 되어야 한다. ⓔ will 뒤에는 can 대신 be able to를 쓴다.
어법 포인트: ⓓ 주어·동사 수일치 (preserves → preserve)
근거: 필기본 09쪽 본문 C ⑤⑥⑦ · Grammar ⑦', 32, 4),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q33.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p31-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(표현) (3점)
present(명사): 현재. 이 문장에서는 ‘선물’이 아니라 ‘현재’의 뜻으로 쓰였다.
근거: 필기본 09쪽 본문 C ⑥ · Vocabulary present(현재) · 01쪽 present 뜻', 33, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q34.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p31-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 파악 (3점)
국립 공원 덕분에 미래 세대도 많은 자연의 경이로움을 경험 할 수 있다. 자원봉사자들은 불법 사냥 활동의 징후를 감시하 고(징후를 제거하는 것이 아님) 동물 덫을 제거하며, 국립 공 원은 지구의 과거·현재·미래를 보존한다.
근거: 필기본 09쪽 본문 C ⑤⑥⑦', 34, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q35.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 주제 (3점)
땅과 바다가 함께 있는 한국의 해안 국립 공원인 태안해안 국 립 공원의 갯벌, 사구, 방문 시 유의점을 소개하는 글이다.
근거: 필기본 10쪽 More Reading 1 · 표 완성 문제(Features, Sinduri Coastal Sand Dune, Conservation Note)', 35, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q36.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 문장 넣기 (3점)
They는 앞 문장의 The mudflats를 가리킨다. 갯벌이 자연의 보물과 같다고 한 뒤, 바다 생물들에게 먹이와 살 곳을 준다는 내용이 이어져야 한다.
근거: 필기본 10쪽 More Reading 1 · 필기(food / places: 먹이 / 장소)', 36, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q37.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(지문 짝짓기) (4점)
Ⓐ 가주어 It의 진주어 to부정사 to visit Ⓑ 뒤에 명사구 its history가 오므로 because of Ⓒ Since then(그때 이후로) 과 함께 쓰는 현재완료 has formed
어법 포인트: Ⓐ 가주어–진주어 to부정사 / Ⓑ because of+명사 (구) / Ⓒ 현재완료(since then)
근거: 필기본 10쪽 More Reading 1 · 필기(it is interesting to visit: 가주어–진주어, has formed: 현재완료)', 37, 4),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q38.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 파악 (3점)
다윈이 갈라파고스에 얼마나 머물렀는지는 나오지 않는다. 나 머지는 13개, Charles Darwin, 동물들이 시간이 지나며 환 경에 맞게 변하는 방식, 놀라운 야생 동물로 답할 수 있다.
근거: 필기본 11쪽 More Reading 2', 38, 3),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q39.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(지문) (4점)
one of the+최상급+복수명사이므로 ⓐ treasure는 treasures가 되어야 한다. ⓑ be made up of, ⓒ 목적격 관 계대명사 that, ⓓ help+목적어+동사원형, ⓔ 수동태 is monitored는 옳다.
어법 포인트: ⓐ one of the+최상급+복수명사 (treasure → treasures)
근거: 필기본 11쪽 More Reading 2 · 필기(one of the+최상급+복 수명사, be made up of, help+목적어+동사원형)', 39, 4),
(@naesin_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r04/q40.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r04/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 불일치 (3점)
공원은 13개의 큰 섬, 17개의 작은 섬, 40개의 바위로 이루어 져 있다. 큰 섬과 작은 섬의 수가 바뀌었다.
근거: 필기본 11쪽 More Reading 2', 40, 3);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM2-2602-R05', 'NAESIN', '압구정중 2학년 2학기 기말 5회 · Lesson 7 예상문제 5회', 'Lesson 7 학습지 필기본 1~11쪽 · 40문항 · 20분 · 135점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_r05 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM2-2602-R05');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 발음부호 (3점)
monitor의 발음부호는 [ˈmɑːnɪtər]로 첫째 음절에 주강세가 있다. ②는 강세 위치를 둘째 음절로 옮겨 틀리다.
근거: 필기본 01쪽 Lesson 7 단어 · monitor [ˈmɑːnɪtər], effort [ˈefərt], explore [ɪkˈsplɔːr], illegal [ɪˈliːɡəl], product [ˈprɑːdʌkt]', 1, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 발음부호 (3점)
control의 발음부호는 [kənˈtroʊl]로 둘째 음절에 주강세가 있다. ④는 주강세를 첫째 음절로 옮겨 틀리다.
근거: 필기본 01쪽 Lesson 7 단어 · control [kənˈtroʊl], hunting [ˈhʌntɪŋ], leopard [ˈlepərd], species [ˈspiːʃiːz], glacier [ˈɡleɪʃər]', 2, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이) (3점)
not allowed by the law(법으로 허용되지 않는)는 illegal(불법적인)의 영어 뜻풀이이다.
근거: 필기본 01쪽 illegal 영어 뜻풀이', 3, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(빈칸) (3점)
회사가 다음 달에 출시할 새 기술 ‘제품’이므로 product가 알 맞다.
근거: 필기본 02쪽 단어 연습 8번 · product(제품, 상품)', 4, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(숙어) (3점)
make an effort / make efforts: 노력하다. ‘환경을 보호하 기 위해 노력하자.’
근거: 필기본 01쪽 effort 예문 Let''s make efforts to protect the environment. · 관련 표현 make an effort', 5, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(빈칸 불가) (3점)
차례대로 species(종), book(예약하다), icy(얼음에 뒤덮인), wonderland(아주 멋진 곳)가 들어간다. effort(노력)는 어느 빈칸에도 들어가지 않는다.
근거: 필기본 02쪽 단어 연습 9·15·10·3번', 6, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(숙어) (3점)
lose control (of): (~에 대한) 통제력을 잃다. ‘과속 운전자들 은 미끄러운 도로에서 종종 차량의 통제력을 잃는다.’
근거: 필기본 01쪽 control 관련 표현 gain/lose control · 02쪽 단 어 연습 2번', 7, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(사역동사) (4점)
사역동사 have는 목적격 보어로 동사원형을 쓴다. to explain → explain. ①②⑤ make/have/let + 목적어 + 동 사원형, ③ get + 목적어 + to부정사로 옳다.
어법 포인트: 사역동사 have + 목적어 + 동사원형(④ to explain → explain)
근거: 필기본 03쪽 Step 1 5·10번, e.g. · 04쪽 25·34번', 8, 4),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(사역·준사역) (3점)
Ⓐ make + 목적어 + 동사원형 → water Ⓑ help + 목적어 + 동사원형/to부정사 → expand Ⓒ get + 목적어 + to부정사 → to submit
어법 포인트: make + 목적어 + 동사원형 / help + 목적어 + 동사 원형 / get + 목적어 + to부정사
근거: 필기본 04쪽 30·35·45번', 9, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(사역·준사역) (4점)
목적격 보어가 동사원형 make이므로 help, make, let은 들 어갈 수 있다. get은 목적격 보어로 to부정사를 쓰므로(get me to make) got은 들어갈 수 없다.
어법 포인트: get + 목적어 + to부정사(동사원형 불가) / help·make·let + 목적어 + 동사원형
근거: 필기본 04쪽 40번 · get + 목적어 + to부정사', 10, 4),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영작(사역동사) (4점)
The teacher made the students finish their homework on time. 여섯 번째 단어는 finish이다. 사역동사 made 뒤 목적격 보어는 동사원형이므로 to는 쓰지 않는다.
어법 포인트: make + 목적어 + 동사원형
근거: 필기본 03쪽 e.g. The teacher made the students finish their homework on time. (선생님은 학생들이 그들의 숙제를 제 시간에 끝내게 했다.)', 11, 4),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(가주어·의미상 주어) (4점)
② 가주어 It + 진주어 to부정사로 옳다. ① be → to be ③ flying → to fly ④ 가주어는 It을 쓴다(This → It) ⑤ that say → to say
어법 포인트: 가주어 It + 진주어 to부정사(① be → to be, ③ flying → to fly, ④ This → It, ⑤ that say → to say)
근거: 필기본 06쪽 15·25·30·20번, 06쪽 40번', 12, 4),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(가주어) (4점)
틀린 문장은 4개이다. It is necessary wearing → to wear, It was generous for her → of her(성격 형용사), That is safe → It is safe, It was difficult find → to find. 나머지 3문장은 옳다.
어법 포인트: 진주어 to + 동사원형, 성격 형용사(generous) + of + 목적격, 가주어 It
근거: 필기본 05쪽 1·3·9·10·13번, e.g. · 06쪽 23번', 13, 4),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(가주어) (3점)
that절 주어를 뒤로 보내고 가주어 It을 앞에 쓴다: It is surprising that she won the first prize without any training. to 뒤에는 절이 올 수 없고, 가주어로 That은 쓰지 않으며, what은 완전한 절 앞에 쓰지 않는다.
어법 포인트: 가주어 It + 진주어 that절
근거: 필기본 06쪽 35번 · 05쪽 It ~ that절 진주어', 14, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영작(가주어) (4점)
It is dangerous to swim in the sea. 진주어 to부정사는 to + 동사원형이므로 swimming은 쓰지 않는다. 다섯 번째 단어 는 swim이다.
어법 포인트: 가주어 It + 진주어 to + 동사원형
근거: 필기본 05쪽 e.g. It is dangerous to swim in the sea. (바 다에서 수영하는 것은 위험하다.)', 15, 4),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(가주어·의미상 주어) (4점)
차례대로 for(의미상 주어), that(진주어 that절), of(성격 형 용사 careless), to(진주어 to부정사)가 들어간다. about은 어디에도 들어갈 수 없다.
어법 포인트: 의미상 주어 for/of, 진주어 that절, 진주어 to부정 사
근거: 필기본 06쪽 20·27·37번, 05쪽 e.g.', 16, 4),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q17.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p17-20.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 문장 넣기 (3점)
주어진 문장의 It과 its는 앞 문장의 Torres del Paine National Park를 가리키고, ⓓ 뒤 ‘하루에 맑거나 비가 오거 나 눈이 올 수 있다’가 그 역동적인 날씨의 예가 된다. ⓐⓑⓒ 에 넣으면 It이 가리킬 공원이 아직 나오지 않고, ⓔ에 넣으면 빙하 이야기 사이에 날씨 이야기가 끼어든다.
근거: 필기본 07쪽 본문 A ⑦⑧⑨', 17, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q18.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p17-20.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 알 수 있는 내용 (3점)
하루에 사계절을 경험할 수 있고 역동적인 날씨로 유명하므로 날씨가 많이 변한다는 것을 알 수 있다. ② Chile ③ dynamic weather ④ Chile ⑤ It is exciting to walk on the glacier.
근거: 필기본 07쪽 본문 A ⑥⑦⑨⑫ (⑧은 17번 문장)', 18, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q19.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p17-20.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영작(조건) (4점)
For example, Grey Glacier, one of the most wonderful glaciers in Chile, welcomes you in the park. (17단어) 주어 Grey Glacier가 단수이므로 welcomes이고, 열세 번째 단어이다.
어법 포인트: 주어(Grey Glacier, 단수)–동사 수일치 welcomes, one of the + 최상급 + 복수명사
근거: 필기본 07쪽 본문 A ⑪ 및 해석', 19, 4),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q20.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p17-20.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 불일치 (3점)
공원에서는 산, 호수, 커다란 빙원을 볼 수 있다고 했다. 사막 (deserts)은 나오지 않는다.
근거: 필기본 07쪽 본문 A ⑥⑦⑨⑩⑫', 20, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q21.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p21-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(지문) (4점)
ⓓ keep + 목적어 + 형용사(목적격 보어)이므로 safely → safe. ⓐ 주어 National parks(복수) + are ⓑ 전치사 for 뒤 동명사 ⓒ keep ⓔ 사역동사 let + 목적어 + 동사원형
어법 포인트: ⓓ keep + 목적어 + 형용사 보어(safely → safe)
근거: 필기본 07쪽 본문 A ①~③ · Grammar ③ · 필기 for preserving', 21, 4),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q22.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p21-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 유추할 수 없는 것 (3점)
국립 공원이 자연의 아름다움을 즐기게 해 주는 대상은 동물 이 아니라 사람(people)이다. ②~⑤는 국립 공원이 자연과 동 식물을 지키고 사람들이 자연을 즐기게 한다는 내용에서 알 수 있다.
근거: 필기본 07쪽 본문 A ①~③', 22, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q23.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p23-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영작(조건) (4점)
If you are lucky, you can see rhinos which are sleeping under baobab trees. (14단어) 아홉 번째 단어는 which이다.
어법 포인트: 선행사 rhinos + 주격 관계대명사 which + are
근거: 필기본 08쪽 본문 B ⑪ 및 해석 · Grammar ⑪ 주격 관계대 명사 which', 23, 4),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q24.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p23-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 답할 수 없는 질문 (3점)
공원이 처음 문을 연 때는 나오지 않는다. ① lions, leopards, elephants, rhinos, and buffaloes ② South Africa ③ Kruger National Park ④ the Big Five, many wild animals
근거: 필기본 08쪽 본문 B ⑦~⑩', 24, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q25.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p23-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 불일치 (3점)
Big Five는 lions, leopards, elephants, rhinos, buffaloes이므로 사자도 Big Five에 속한다.
근거: 필기본 08쪽 본문 B ⑧⑨⑩', 25, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q26.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p26-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법·의미(지문) (4점)
ⓒ 목적을 나타내는 to부정사 to enter로 옳다. ⓐ preserving → to preserve(목적의 to부정사) ⓑ 공원은 방 문객 수를 통제하므로 increases → controls ⓓ 사역동사 makes + 목적어 + 동사원형이므로 to take → take ⓔ 플라 스틱을 쓰지 않고 친환경 제품만 쓰므로 plastic → eco- friendly
어법 포인트: ⓐ 목적의 to부정사(preserving → to preserve) · ⓑ 의미(increases → controls) · ⓓ 사역동사 make + 동사원형 (to take → take) · ⓔ 의미(plastic → eco-friendly)
근거: 필기본 08쪽 본문 B ①~⑤ · Grammar ①④', 26, 4),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q27.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p26-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(영영풀이) (3점)
④는 glacier의 뜻풀이로 윗글에 없다. ① effort ② preserve ③ book ⑤ product는 윗글에 있다.
근거: 필기본 01쪽 영어 뜻풀이 · 08쪽 본문 B ①~⑤', 27, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q28.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p26-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 일치 (3점)
방문객들은 공원에 입장하려면 예약해야 한다. ② 일부 산책 로만 열려 있다 ③ 쓰레기를 가지고 떠나게 한다 ④ 쓰레기를 가져가게 하는 것은 공원이다 ⑤ 일부만 열려 있는 것은 산책 로이다
근거: 필기본 08쪽 본문 B ①~④', 28, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q29.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p29-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(문맥) (3점)
Big Five를 ‘보호하기 위해’ 공원이 처음부터 사냥 금지 지역 이었으므로 hunt가 아니라 protect가 알맞다.
근거: 필기본 09쪽 본문 C ① ② · Grammar ②', 29, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q30.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p29-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 답할 수 없는 질문 (3점)
드론의 수는 나오지 않는다. ① the Big Five ② because of too much hunting ③ from the start ④ drones and helicopters
근거: 필기본 09쪽 본문 C ①~③ · Comprehension Check Q5', 30, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q31.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p31-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 주제 (3점)
자원봉사자들이 불법 사냥을 감시하고 덫을 제거하며, 국립 공원이 지구의 과거·현재·미래를 보존해 미래 세대도 자연을 경험할 수 있다는 내용이다.
근거: 필기본 09쪽 본문 C ④~⑦ · 중심 내용(국립 공원의 야생 동 물 보호 활동과 보존의 가치)', 31, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q32.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p31-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 알 수 있는 내용 (3점)
자원봉사자들은 불법 사냥의 징후를 감시하고 동물 덫을 제거 하여 공원을 돕는다. ② 덫을 제거하는 것은 자원봉사자 ③ 자 연 자원봉사 프로그램이다 ④ 덫을 놓는 것이 아니라 제거한 다 ⑤ 프로그램을 운영하는 것은 공원이다
근거: 필기본 09쪽 본문 C ⑤⑥⑦', 32, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q33.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p31-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(지문 ⓐ~ⓖ) (4점)
옳은 것: ⓐ runs(주어 the park, 단수) ⓒ remove(주어 Volunteers) ⓕ be able to. 틀린 것: ⓑ monitors → monitor ⓓ preserves → preserve ⓔ Thanks for → Thanks to ⓖ experiencing → experience
어법 포인트: ⓑⓓ 주어·동사 수일치(복수 주어 → monitor, preserve) · ⓔ Thanks to(~덕분에) · ⓖ be able to + 동사원형
근거: 필기본 09쪽 본문 C ④~⑦ · Grammar ⑦', 33, 4),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q34.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p31-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(수동태) (4점)
Many nature volunteer programs are run by the park. run(운영하다)의 과거분사는 run이다(run–ran–run). 여섯 번째 단어는 run이다.
어법 포인트: 수동태 be + 과거분사(run–ran–run)
근거: 필기본 09쪽 본문 C ④ · Vocabulary run 운영하다 · Grammar ③ 수동태', 34, 4),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q35.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 주제 (3점)
땅과 바다가 함께 있는 태안해안 국립 공원의 갯벌, 사구, 섬 과 신두리 해안 사구의 생태계를 소개하는 글이다.
근거: 필기본 10쪽 More Reading 1', 35, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q36.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 알 수 있는 내용 (3점)
갯벌은 바다의 모든 생물에게 먹이와 살 곳을 제공하므로 바 다 동물의 집이라고 할 수 있다. ① coastal park ② in the sea ③ beautiful islands ④ 보호·관리 목적(conservation purposes)
근거: 필기본 10쪽 More Reading 1 · T/F 2번', 36, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q37.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법·의미(지문) (4점)
ⓓ Since then과 함께 쓰인 현재완료 has formed로 옳다. ⓐ 가주어 it의 진주어는 to부정사 → to visit ⓑ 뒤에 명사구 가 오므로 because of ⓒ 15,000 years ago(과거 시점)와 함 께 쓰이고 ‘형성되었다’는 수동이므로 was formed ⓔ 주어 this park가 단수이므로 attracts
어법 포인트: ⓐ 진주어 to부정사(visit → to visit) · ⓑ because of + 명사구 · ⓒ 수동태 was formed(has formed → was formed) · ⓔ 수일치(attract → attracts)
근거: 필기본 10쪽 More Reading 1 필기(가주어–진주어, was formed 수동태, has formed 현재완료)', 37, 4),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q38.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 불일치 (3점)
공원은 큰 섬 13개, 작은 섬 17개, 바위 40개로 이루어져 있다. 큰 섬이 40개인 것이 아니다.
근거: 필기본 11쪽 More Reading 2', 38, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q39.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 일치 (3점)
다윈은 1830년대에 갈라파고스를 방문해 놀라운 생물들을 관 찰했다. ① first ② 1830s ④ animals ⑤ Darwin’s discoveries
근거: 필기본 11쪽 More Reading 2', 39, 3),
(@naesin_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r05/q40.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r05/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(지문) (4점)
ⓓ 주어 It(the idea of evolution)이 단수이므로 explain → explains. ⓐ one of the + 최상급 + 복수명사 ⓑ be made up of ⓒ 목적격 관계대명사 that ⓔ 수동태 is monitored
어법 포인트: ⓓ 주어(It, 단수)–동사 수일치(explain → explains)
근거: 필기본 11쪽 More Reading 2 필기(one of the+최상급+복 수명사, be made up of) · 2번 정답 ④ It = the idea of evolution', 40, 4);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM2-2602-R06', 'NAESIN', '압구정중 2학년 2학기 기말 6회 · Lesson 8 예상문제 1회', 'Lesson 8 학습지 필기본 12~23쪽 · 40문항 · 20분 · 134점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_r06 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM2-2602-R06');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 발음부호 (3점)
honestly는 h가 소리 나지 않으므로 [ˈɑːnɪstli]가 바른 발음 부호이다. 나머지는 필기본 표기와 같다.
근거: 필기본 12쪽 Word List · honestly [ˈɑːnɪstli] (h 묵음 주의)', 1, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 발음부호 (3점)
customer는 [ˈkʌstəmər]로 첫째 음절에 주강세가 있다. 나머 지는 필기본 표기와 같다.
근거: 필기본 12~13쪽 Word List · customer [ˈkʌstəmər]', 2, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이) (3점)
‘어떤 일이 일어난 직후에; 즉시’는 as soon as(~하자마자)의 영어 뜻풀이이다.
근거: 필기본 12쪽 Word List · as soon as', 3, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(빈칸) (3점)
막차를 놓치지 않으려고 역으로 ‘서둘러 가야’ 했다는 내용이 므로 rush가 알맞다.
근거: 필기본 13쪽 Word List 연습 17번', 4, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(빈칸) (3점)
차례대로 customer, return, silently, sock이 들어간다. wool(양모, 털실)은 어느 빈 칸에도 알맞지 않다.
근거: 필기본 12~13쪽 Word List 예문(silently) · 연습 10, 16, 20 번', 5, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(빈칸) (3점)
‘솔직히 말해서’라는 뜻의 Honestly speaking이 알맞다.
근거: 필기본 13쪽 Word List 연습 12번', 6, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이) (3점)
‘다른 색깔의 긴 선이나 띠’는 stripe(줄무늬)이다.
근거: 필기본 13쪽 Word List · stripe', 7, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(-thing+형용사) (3점)
차례대로 anything special, something cold to drink, anything else, something warm to wear가 들어간다. interesting something to read는 형용사가 -thing 앞에 온 틀린 어순이다(something interesting to read).
어법 포인트: -thing+형용사+to부정사 어순(interesting something → something interesting)
근거: 필기본 17쪽 e.g.·기초 6번·보충 1번, 18쪽 보충 6번', 8, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(목적격 관계대명사) (4점)
선행사 the gift는 사물이므로 who가 아니라 which나 that 을 써야 한다.
어법 포인트: 사물 선행사(the gift) 뒤 who → which/that
근거: 필기본 14쪽 1·11번·e.g., 15쪽 16번, 17쪽 e.g.', 9, 4),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(종합) (4점)
옳은 문장은 The bicycle ~, Is that the girl ~, I have something new., This is the plant ~, The movie that ~ 의 5개이다. The singer whom I like him best와 This is the house which we built it은 목적어(him, it)를 중복해 서 썼고, cold anything은 anything cold로 써야 한다.
어법 포인트: 목적격 관계대명사절의 목적어 중복(him, it) · - thing+형용사 어순
근거: 필기본 15쪽 21번, 14쪽 e.g., 17쪽 기초 1번·보충 1번', 10, 4),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(관계대명사 that) (4점)
<보기>의 that은 선행사 The movie를 꾸미는 목적격 관계대 명사이다. ②의 that도 선행사 The cookies를 꾸미는 목적격 관계대명사이다. 나머지는 명사절 접속사(I know that), 지 시형용사(that building), 동격절 접속사(The news that), 진주어 that절을 이끄는 접속사(It is clear that)이다.
어법 포인트: 목적격 관계대명사 that과 주격 관계대명사·접속사· 지시형용사 that 구별
근거: 필기본 16쪽 30·31번', 11, 4),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(-thing+형용사) (4점)
-thing으로 끝나는 대명사는 형용사가 뒤에서 꾸민다. I want something hot to eat.만 옳다. 나머지는 something new, something cold, anything strange, something interesting으로 고쳐야 한다.
어법 포인트: -thing+형용사(+to부정사) 어순
근거: 필기본 17쪽 기초 5·6번·보충 1번, 18쪽 보충 11번', 12, 4),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영작(목적격 관계대명사) (4점)
전치사를 관계대명사 앞에 쓰면 This is the girl about whom I told you.가 된다. 다섯 번째 단어는 about이다. 전 치사 뒤에는 that을 쓸 수 없고, her는 관계대명사로 바뀌었 으므로 다시 쓰지 않는다.
어법 포인트: 전치사+목적격 관계대명사(about whom), 목적어 her 중복 금지
근거: 필기본 16쪽 36번', 13, 4),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(목적격 관계대명사) (4점)
목적격 관계대명사 whom이 met의 목적어 역할을 하므로 him을 다시 쓰면 안 된다(The man whom I met yesterday was kind).
어법 포인트: 관계대명사절 안 목적어(him) 중복
근거: 필기본 14쪽 e.g., 15쪽 17번, 16쪽 36번, 17쪽 e.g.', 14, 4),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(목적격 관계대명사) (4점)
전치사 바로 뒤에는 that을 쓸 수 없다(in which we live). which/that we live in과 목적격 관계대명사를 생략한 we live in은 모두 가능하다.
어법 포인트: 전치사+that 불가(전치사+which)
근거: 필기본 15쪽 26번, 16쪽 27·28번', 15, 4),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(종합) (4점)
Ⓐ 선행사 The movie가 있으므로 what은 쓸 수 없고 which. Ⓑ 선행사 the singer가 사람이므로 whom. Ⓒ anything을 뒤에서 꾸미는 형용사 important가 알맞다.
어법 포인트: 선행사 뒤 what 불가, 사람 선행사 whom, - thing+형용사
근거: 필기본 14쪽 6번·e.g., 18쪽 보충 4번', 16, 4),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q17.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 일치 (3점)
When they played together, Bruce flew into Angus’s pocket.과 일치한다. ① Scotland, ③ Angus의 친구, ④ 최 근 손님을 많이 잃었다, ⑤ 큰 백화점에서 샀다.
근거: 필기본 19쪽 본문 A ①~⑥', 17, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q18.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 연결어 (3점)
예전에는 손님이 많았지만 최근에는 많이 잃었다는 대조의 내 용이므로 but이 알맞다.
근거: 필기본 19쪽 본문 A ⑤', 18, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q19.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(지문) (4점)
used to 뒤에는 동사원형이 오므로 used to have로 고쳐야 한다. ⓐ there lived(유도부사 구문), ⓑ flew(fly의 과거형), ⓒ 주격 관계대명사 that, ⓔ start+동명사 buying은 옳다.
어법 포인트: used to+동사원형(used to having → used to have)
근거: 필기본 19쪽 본문 A ①③④⑤⑥', 19, 4),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q20.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 조건 영작 (4점)
Angus made more and more socks because he knew other sparrows were shaking in the cold weather, too.(18단어) 열 번째 단어는 other이다.
어법 포인트: because+주어+동사, 과거진행형 were shaking
근거: 필기본 19쪽 본문 A ⑭', 20, 4),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q21.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 불일치 (3점)
Angus는 가게가 밤에 문을 닫은 후(After the store closed for the night) 양말 기계 앞에 앉았다.
근거: 필기본 19쪽 본문 A ⑦⑪⑫⑬⑭', 21, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q22.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(표현) (4점)
‘다 써 버렸다’는 과거의 일이므로 used up이 알맞다. used to는 ‘~하곤 했다’, was used up은 수동태라 주어 he와 맞지 않는다.
어법 포인트: use up의 과거형 used up(수동태·used to와 구별)
근거: 필기본 19쪽 본문 A ⑮, 13쪽 Word List · use up', 22, 4),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q23.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p23-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 문장 넣기 (3점)
참새들의 양말을 본 사람들이 백화점으로 서둘러 간 뒤 “우리 는 참새들이 신고 있는 양말을 원해요.”라고 물어야 한다. 이 문장이 없으면 They asked의 They가 가리킬 대상(people) 이 없다.
근거: 필기본 20쪽 본문 B ③', 23, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q24.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p23-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 일치 (3점)
다음 날 참새들은 빨간 줄무늬가 있는 따뜻한 양말을 신고 있 었다. ① red stripes, ② 다음 날(The next day), ③ 어디에 서도 찾을 수 없었다, ⑤ 양말 가게(in the sock store).
근거: 필기본 20쪽 본문 B ①②⑤⑥', 24, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q25.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p25-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(영영풀이) (3점)
차례대로 wool, shake(shaking), sparrow(sparrows), sock(socks)은 글에 있다. customer(a person who buys things from a store)는 글에 없다.
근거: 필기본 12쪽 Word List 영어 뜻풀이, 20쪽 본문 B ⑥~⑨', 25, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q26.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p25-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(지문) (4점)
-thing으로 끝나는 대명사는 형용사가 뒤에서 꾸미므로 something strange로 고쳐야 한다. ⓑ took, ⓒ make socks for ~, ⓓ·ⓔ 과거진행형 were shaking, was listening은 옳다.
어법 포인트: -thing+형용사(strange something → something strange)
근거: 필기본 20쪽 본문 B ⑦⑧⑨ · Grammar ①⑦', 26, 4),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q27.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p25-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 추론 (3점)
Bruce는 Angus의 주머니 속에서 듣고 있었을 뿐이다. 털실 에 대해 사실대로 말한 것은 Angus이다.
근거: 필기본 20쪽 본문 B ⑦~⑨', 27, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q28.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p25-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(문맥) (3점)
Ⓐ Angus는 사실대로 털어놓았으므로 ‘정직하게’ honestly.
Ⓑ Bruce는 주머니 속에서 ‘조용히’ 듣고 있었으므로 silently.
근거: 필기본 20쪽 본문 B ⑧⑨', 28, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q29.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p29-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 지칭·의미 (3점)
ⓓ him은 양말 때문에 곤경에 빠진 Angus를 가리킨다. ⓑ는 have to 뒤에 return these socks가 생략된 말이다.
근거: 필기본 21쪽 본문 C ①②③④ · Grammar ②', 29, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q30.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p29-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 알 수 있는 내용 (3점)
다른 참새들은 처음에 “Do we have to? We like these socks.”라고 했으므로 양말을 갖고 싶어 했다. ①·② 돌려주자 고 한 것은 Bruce, ④ 양말 가게, ⑤ 탁자 위에 쌓았다.
근거: 필기본 21쪽 본문 C ①②⑤', 30, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q31.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p29-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 답할 수 없는 질문 (3점)
참새들이 돌려준 양말의 수는 나와 있지 않다. ② Bruce, ③ because of our socks, ④ piled their socks on the table, ⑤ We like these socks.
근거: 필기본 21쪽 본문 C ①②③⑤', 31, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q32.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 연결어·표현 (3점)
노크 소리가 난 ‘바로 그때’ 사람들이 가게로 몰려왔으므로 Just then이 알맞다.
근거: 필기본 21쪽 본문 C ⑥', 32, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q33.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문) (4점)
ⓐ rushed, ⓔ said(도치), ⓕ are all wearing은 옳다. ⓑ 선행사 the socks가 있으므로 that, ⓒ be crowded with, ⓓ 현재완료 has come, ⓖ 주어 dogs and cats가 복수이므 로 are로 고쳐야 한다.
어법 포인트: 선행사 뒤 what 불가(that), be crowded with, 현 재완료 has+p.p., 주어 수일치(are)
근거: 필기본 21쪽 본문 C ⑥~⑪ · Grammar ⑦⑨', 33, 4),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q34.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 속담 (3점)
Angus의 친절한 마음이 손님이라는 큰 선물로 돌아왔으므로 ‘가는 것은 돌아온다(What goes around comes around.)’가 어울린다.
근거: 필기본 21쪽 본문 C 정리(친절한 행동은 자신에게도 돌아온 다), 22쪽 속담 필기', 34, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q35.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 불일치 (3점)
진정한 친구는 조건 없이 베푼다(Gives unconditionally).
근거: 필기본 22쪽 More Reading 1', 35, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q36.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문) (4점)
‘~하기 위해’라는 목적의 to부정사이므로 to say로 고쳐야 한다. 주어 A true friend가 3인칭 단수이므로 Accepts, Forgives, wants는 옳다.
어법 포인트: 목적의 to부정사(to saying → to say)
근거: 필기본 22쪽 More Reading 1', 36, 4),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q37.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 주제 (3점)
A부터 L까지 진정한 친구가 하는 일을 나열한 글이다(The ABCs of Friendship).
근거: 필기본 22쪽 More Reading 1 · 1번(Friendship)', 37, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q38.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 일치 (3점)
The mouse chewed through the ropes of the net with his sharp teeth.와 일치한다. ② tail, ③ a few days, ④ no one could hear him, ⑤ 고마워한 것은 사자이다.
근거: 필기본 23쪽 More Reading 2', 38, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q39.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 문맥상 쓰임 (3점)
작은 쥐가 사자를 구했으므로 “가장 작은 친구들도 큰 도움이 될 수 있다(Even the smallest friends ~)”가 되어야 한다.
근거: 필기본 23쪽 More Reading 2', 39, 3),
(@naesin_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r06/q40.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r06/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 답할 수 없는 질문 (3점)
사자가 그물에 얼마나 오래 있었는지는 나와 있지 않다. ① in the forest, ③ to repay his kindness, ④ chewed through the ropes, ⑤ Even the smallest friends can be a great help.
근거: 필기본 23쪽 More Reading 2', 40, 3);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM2-2602-R07', 'NAESIN', '압구정중 2학년 2학기 기말 7회 · Lesson 8 예상문제 2회', 'Lesson 8 학습지 필기본 12~23쪽 · 40문항 · 20분 · 136점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_r07 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM2-2602-R07');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 발음부호 (3점)
knock의 k는 소리 나지 않으므로 [nɑːk]가 바른 발음부호이다. 나머지는 필기본 표기와 같다.
근거: 필기본 12쪽 Word List · knock [nɑːk] (k 묵음 주의)', 1, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 발음부호 (3점)
department store는 [dɪˈpɑːrtmənt stɔːr]로 depart-의 둘 째 음절에 주강세가 있다. 나머지는 필기본 표기와 같다.
근거: 필기본 12쪽 Word List · department store [dɪˈpɑːrtmənt stɔːr]', 2, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(영영풀이) (3점)
‘어떤 것이 위에 온통 덮여 있다’는 be covered with(~로 덮 여 있다)의 영어 뜻풀이이다.
근거: 필기본 12쪽 Word List · be covered with', 3, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(빈칸) (3점)
실수로 교실 유리창을 깨서 ‘곤란(문제)’에 빠졌다는 뜻이므로 trouble이 알맞다(get into trouble).
근거: 필기본 13쪽 Word List 연습 7번', 4, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(빈칸) (3점)
차례대로 silently, pair, pop out, villager가 들어간다. anywhere(어디에도)는 어느 빈 칸에도 알맞지 않다.
근거: 필기본 13쪽 Word List 연습 3, 14, 15, 19번', 5, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(빈칸) (3점)
‘문을 똑똑 두드리는 소리’를 들었다는 뜻이므로 knock이 알 맞다.
근거: 필기본 12쪽 Word List · knock 예문', 6, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(영영풀이) (3점)
‘강한 감정이나 추위 때문에 몸이 저절로 움직이다’는 shake(떨다, 흔들리다)이다.
근거: 필기본 12쪽 Word List · shake', 7, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(목적격 관계대명사) (3점)
선행사 The teacher가 사람이므로 which는 쓸 수 없다. who(m)/that을 쓰거나 목적격 관계대명사를 생략할 수 있다.
어법 포인트: 사람 선행사 뒤 which 불가
근거: 필기본 14쪽 2번', 8, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(목적격 관계대명사) (4점)
which가 take의 목적어 역할을 하므로 it을 다시 쓰면 안 된 다(the bus which I usually take).
어법 포인트: 관계대명사절 안 목적어(it) 중복
근거: 필기본 15쪽 22번, 16쪽 32번', 9, 4),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(-thing+형용사) (4점)
He wants to eat something delicious.만 옳다. 나머지는 to solve 뒤 목적어 중복(this math problem 삭제), something delicious, There is something(단수 취급), someone special로 고쳐야 한다.
어법 포인트: -thing/-one+형용사 어순, -thing 단수 취급, to부 정사의 목적어 중복
근거: 필기본 17쪽 보충 2번, 18쪽 보충 7·12번', 10, 4),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(종합) (4점)
Ⓐ 전치사 about 바로 뒤에는 that을 쓸 수 없으므로 whom.
Ⓑ 선행사 the man이 있으므로 what은 쓸 수 없고 whom.
Ⓒ anything을 뒤에서 꾸미는 형용사 wrong이 알맞다.
어법 포인트: 전치사+whom(that 불가), 선행사 뒤 what 불가, - thing+형용사
근거: 필기본 16쪽 27번, 14쪽 12번, 17쪽 보충 2번', 11, 4),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(종합) (4점)
틀린 문장은 The man whom I met him ~(him 삭제), The girl with that ~(with whom), She needs warm something ~(something warm)의 3개이다.
어법 포인트: 목적어 중복(him), 전치사+that 불가, -thing+형용 사 어순
근거: 필기본 15쪽 17·22번, 16쪽 32번, 14쪽 e.g., 17쪽 기초 2번· 보충 2번, 18쪽 보충 7번', 12, 4),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영작(-thing+형용사) (4점)
Is there something interesting to read?(6단어) 네 번째 단어는 interesting이다. 「-thing+형용사+to부정사」 어순 이다.
어법 포인트: -thing+형용사+to부정사 어순
근거: 필기본 17쪽 기초 7번', 13, 4),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(목적격 관계대명사) (4점)
The boy that you saw in the library is my brother.만 옳다. 나머지는 with whom, him 삭제, it 삭제, that(which)으로 고쳐야 한다.
어법 포인트: 전치사+whom, 목적어 중복 금지, 선행사 뒤 what 불가
근거: 필기본 14쪽 7번, 15쪽 22번, 16쪽 37번', 14, 4),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(-thing+형용사) (4점)
didn’t find anything useful은 found nothing useful과 같은 의미이다. useful nothing, useful anything은 어순이 틀렸고, something useful은 의미가 반대이다.
어법 포인트: not ~ anything = nothing, -thing+형용사 어순
근거: 필기본 18쪽 보충 17번', 15, 4),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(종합) (4점)
ⓐ 목적격 관계대명사 which, ⓒ nothing unusual, ⓕ anything special은 옳다. ⓑ 사람 선행사이므로 who(m)/that, ⓓ -thing은 단수 취급이므로 Is, ⓔ it 삭제, ⓖ anybody famous로 고쳐야 한다.
어법 포인트: 사람 선행사 뒤 which 불가, 목적어 중복 금지, - thing 단수 취급, -thing/-body+형용사
근거: 필기본 14쪽 e.g., 17쪽 e.g.·기초 9번, 18쪽 보충 5번', 16, 4),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q17.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 문맥상 쓰임 (3점)
마을 사람들이 큰 백화점에서 양말을 사기 시작했으므로 가게 는 최근에 손님을 많이 ‘잃었다(lost)’가 되어야 한다.
근거: 필기본 19쪽 본문 A ②③④⑤⑥', 17, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q18.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 답할 수 없는 질문 (3점)
가게의 손님 수는 나와 있지 않다(many customers). ① a small village in Scotland, ② a sparrow, ③ socks, ⑤ a big department store.
근거: 필기본 19쪽 본문 A ①~⑥', 18, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q19.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(표현) (4점)
과거의 상태(예전에 ~하곤 했다)는 「used to+동사원형」으 로 나타낸다. be used to는 ‘~에 익숙하다’ 또는 수동태이다.
어법 포인트: used to+동사원형(be used to와 구별)
근거: 필기본 19쪽 본문 A ⑤ · used to 필기', 19, 4),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q20.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문) (4점)
ⓓ help, ⓔ popped, ⓖ used up은 옳다. ⓐ 주어가 Bruce and the other sparrows(복수)이므로 were, ⓑ 수동태 was covered with, ⓒ 지각동사 saw+목적어+raising, ⓕ 진행형 were shaking으로 고쳐야 한다.
어법 포인트: 주어 수일치(were), 수동태 be covered with, 지각 동사+현재분사, 과거진행형 were shaking
근거: 필기본 19쪽 본문 A ⑦⑧⑨⑩⑫⑭⑮ · Grammar ⑨', 20, 4),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q21.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 연결어 (3점)
양말을 점점 더 많이 만들었지만 기계 안의 털실을 모두 써 버 렸다는 것은 깨닫지 못했다는 대조의 흐름이므로 But이 알맞다.
근거: 필기본 19쪽 본문 A ⑭⑮', 21, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q22.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 추론 (3점)
Angus는 다른 참새들도 추위에 떨고 있다는 것을 알고 양말 을 점점 더 많이 만들었으므로 Bruce만을 위해 만든 것이 아 니다.
근거: 필기본 19쪽 본문 A ⑧⑩⑪⑬⑭', 22, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q23.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p23-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 불일치 (3점)
백화점으로 서둘러 간 것은 참새들이 아니라 양말을 본 사람 들(people)이다.
근거: 필기본 20쪽 본문 B ①②③④', 23, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q24.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p23-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문) (4점)
ⓑ that은 선행사 the socks를 꾸미는 목적격 관계대명사로 옳다. ⓐ 양말을 ‘보자마자’이므로 As soon as, ⓒ 진행형 are wearing, ⓓ 가리키는 대상이 the socks(복수)이므로 them, ⓔ 부정어 couldn’t가 있으므로 anywhere로 고쳐야 한다.
어법 포인트: 목적격 관계대명사 that, 진행형(능동) are wearing, 대명사 수일치 them
근거: 필기본 20쪽 본문 B ③④⑤', 24, 4),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q25.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p25-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(표현) (4점)
-thing으로 끝나는 대명사는 형용사가 뒤에서 꾸미므로 something strange가 알맞다. 긍정문에서 ‘뭔가’는 something이고, nothing strange는 ‘이상한 일이 아무것도 없다’는 반대 의미이다.
어법 포인트: -thing+형용사(something strange)
근거: 필기본 20쪽 본문 B ⑦ · Grammar ①⑦', 25, 4),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q26.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p25-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(수동태) (4점)
Angus was called by his(Angus’s) father. 네 번째 단어 는 by이다.
어법 포인트: 수동태 be+p.p.+by+행위자
근거: 필기본 20쪽 본문 B ⑥', 26, 4),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q27.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p25-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 일치 (3점)
Angus는 참새들이 추운 날씨에 떨고 있어서 양말을 만들었다 고 이유를 말했다. ① 털실을 쓴 것은 Angus, ② 참새들을 위 해, ④ Angus의 주머니 속.
근거: 필기본 20쪽 본문 B ⑦⑧⑨', 27, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q28.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p25-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 문맥상 쓰임 (3점)
털실이 모두 없어진 상황이므로 “누군가 우리 털실을 모두 가 져갔다(Someone took all of our wool.)”가 되어야 한다.
근거: 필기본 20쪽 본문 B ⑦', 28, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q29.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p29-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문) (4점)
ⓑ was crowded with는 옳다. ⓐ 선행사 the socks가 있으 므로 that, ⓒ 주어 your kind heart가 단수이므로 현재완료 has come, ⓓ 현재진행형 are all wearing, ⓔ 주어가 dogs and cats(복수)이므로 are로 고쳐야 한다.
어법 포인트: 선행사 뒤 what 불가(that), 현재완료 has come, 진행형, 주어 수일치
근거: 필기본 21쪽 본문 C ⑦~⑪ · Grammar ⑦⑨', 29, 4),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q30.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p29-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(영영풀이) (3점)
차례대로 stripe(stripes), rush(rushed), villager(villagers), customer(customers)는 글에 있다. return(to give something back or go back to a place) 은 글에 없다.
근거: 필기본 12~13쪽 Word List 영어 뜻풀이, 21쪽 본문 C ⑥~⑪', 30, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q31.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p29-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 답할 수 없는 질문 (3점)
가게가 번 돈의 액수는 나와 있지 않다. ② people, ③ dogs and cats, ④ your kind heart has come back to us as a great gift, ⑤ These are the socks that I want.
근거: 필기본 21쪽 본문 C ⑥~⑪', 31, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q32.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 문장 넣기 (3점)
Bruce가 양말을 돌려주자고 다른 참새들을 설득했고, 참새들 이 “Alright then.”이라고 동의한 뒤, 노크 소리(Knock, knock, knock.)가 나기 전에 가게로 날아가 양말을 쌓아 두 어야 한다.
근거: 필기본 21쪽 본문 C ⑤', 32, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q33.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(지문) (4점)
뒤에 주어와 동사가 없는 명사구 our socks가 오므로 전치사 역할을 하는 because of가 알맞다.
어법 포인트: because of+명사구(because+주어+동사와 구별)
근거: 필기본 21쪽 본문 C ③', 33, 4),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q34.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 알 수 있는 내용 (3점)
Bruce는 “Angus is in trouble because of our socks. We should help him.”이라고 했다. ① We like these socks, ③ 돌려주자고 한 것은 Bruce, ④ 양말 가게, ⑤ 처음 에는 “Do we have to?”라며 망설였다.
근거: 필기본 21쪽 본문 C ①②③', 34, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q35.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 일치 (3점)
Forgives your mistakes.와 일치한다. ① “you”를 믿는다,
② 있는 그대로의 너(who you are)를 사랑한다, ③ 너를 집 으로 초대한다(Invites you over), ④ 있는 그대로의 너를 받 아들인다.
근거: 필기본 22쪽 More Reading 1', 35, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q36.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 문맥상 쓰임 (3점)
진정한 친구는 조건 없이 베푼다(Gives unconditionally).
근거: 필기본 22쪽 More Reading 1', 36, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q37.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 속담 (3점)
진정한 친구가 하는 일(믿어 주고, 돕고, 포기하지 않는 것)을 나열한 글이므로 ‘어려울 때 돕는 친구가 진정한 친구이다’가 어울린다.
근거: 필기본 22쪽 More Reading 1, 22쪽 속담 필기, 23쪽 1번 선 택지', 37, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q38.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 불일치 (3점)
사자의 소리를 듣고 찾아간 것은 사냥꾼이 아니라 작은 쥐 (The little mouse)이다.
근거: 필기본 23쪽 More Reading 2', 38, 3),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q39.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문) (4점)
사역동사 let은 「let+목적어+동사원형」으로 쓰므로 let the mouse go가 되어야 한다. ⓒ struggle to escape, ⓓ 목적 의 to부정사 to find, ⓔ 전치사 for 뒤 동명사 saving은 옳다.
어법 포인트: 사역동사 let+목적어+동사원형(to go → go)
근거: 필기본 23쪽 More Reading 2 · 문법 필기(let+목적어+동사 원형)', 39, 4),
(@naesin_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r07/q40.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r07/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 주제 (3점)
작은 쥐가 그물에 걸린 사자를 구한 이야기이다. 작은 친구도 곤경에 빠졌을 때 큰 도움이 될 수 있다는 내용이 주제이다 (Even the smallest friends can be a great help.).
근거: 필기본 23쪽 More Reading 2', 40, 3);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM2-2602-R08', 'NAESIN', '압구정중 2학년 2학기 기말 8회 · Lesson 8 예상문제 3회', 'Lesson 8 학습지 필기본 12~23쪽 · 40문항 · 20분 · 134점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_r08 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM2-2602-R08');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 발음부호 (3점)
use up의 use는 동사이므로 [juːz]로 발음한다. 바른 발음부 호는 [ˌjuːz ˈʌp]이다.
근거: 필기본 12~13쪽 Lesson 8 Word List · use up [ˌjuːz ˈʌp] (13쪽 주의: use up의 use는 [juːz])', 1, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 발음부호 (3점)
villager는 첫 음절에 주강세가 있다. 바른 발음부호는 [ˈvɪlɪdʒər]이다. honestly의 h, knock의 k는 소리 나지 않으 므로 ③④는 바르다.
근거: 필기본 12~13쪽 Word List · villager [ˈvɪlɪdʒər], return, honestly, knock, shake', 2, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(영영풀이) (3점)
‘양말이나 신발처럼 함께 쓰이는 두 개’는 pair(짝)의 영어 뜻 풀이이다.
근거: 필기본 12쪽 Word List · pair 영어 뜻풀이', 3, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(숙어) (3점)
bring … together는 ‘~을 모으다’라는 뜻이다. ‘모두를 모아 서 소풍 가자.’
근거: 필기본 12쪽 Word List · bring … together 예문', 4, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(문장 빈칸) (3점)
시험을 방해하지 않으려고 교실에 ‘조용히’ 걸어 들어간 것이 므로 silently가 알맞다.
근거: 필기본 13쪽 Word List 연습 19번', 5, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(빈칸 불가) (3점)
차례대로 covered with, popped out, rush, return이 들 어간다. used up(다 써 버렸다)은 어느 빈 칸에도 들어갈 수 없다.
근거: 필기본 12~13쪽 Word List 예문(pop out, rush), 연습 1·16 번', 6, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(문장 빈칸) (3점)
실수로 교실 창문을 깨서 곤란해진 것이므로 by accident(실 수로)가 알맞다.
근거: 필기본 13쪽 Word List 연습 7번 · 필기 by accident: 실수 로', 7, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(목적격 관계대명사) (3점)
사물 선행사에는 which/that, 사람 선행사에는 who/whom/that을 쓴다. 선행사가 있으므로 what은 어느 빈 칸에도 쓸 수 없다.
어법 포인트: 선행사가 있을 때 what 불가(목적격 관계대명사 선 택)
근거: 필기본 14쪽 표 예문, [A] 8번 · 15쪽 23번① · 16쪽 33번', 8, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(목적격 관계대명사) (4점)
관계대명사 which가 bought의 목적어 역할을 하므로 it을 다시 쓰면 안 된다. → which my father bought for me
어법 포인트: 목적격 관계대명사절의 목적어 중복(it 삭제)
근거: 필기본 14쪽 [A] 3번, 표 예문 · 15쪽 23번 ②③④', 9, 4),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(-thing+형용사) (4점)
-thing으로 끝나는 대명사는 형용사가 뒤에서 꾸민다. ① are → is(단수 취급) ② something cold ④ something important ⑤ anything interesting
어법 포인트: -thing+형용사 어순, -thing 단수 취급
근거: 필기본 17쪽 예문·기초 3·8번 · 18쪽 연습 13·18번', 10, 4),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(종합) (4점)
틀린 문장은 3개이다. which she cooked it → it 삭제, new nothing → nothing new, the cat what → the cat that.
어법 포인트: 목적어 중복, -thing+형용사 어순, 선행사 뒤 what 불가
근거: 필기본 14쪽 표 예문 · 15쪽 18번 ①~④, 21번 (c) · 17쪽 예문', 11, 4),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영작(목적격 관계대명사) (4점)
The singer whom I met yesterday sings very well. 다섯 번째 단어는 met이다.
어법 포인트: 목적격 관계대명사+주어+동사 어순
근거: 필기본 16쪽 38번', 12, 4),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영작(두 문장 연결) (4점)
I like the puppy which my uncle gave to me. it은 which가 대신하므로 쓰지 않는다. 여섯 번째 단어는 my이다.
어법 포인트: 두 문장 연결 시 목적어 삭제
근거: 필기본 14쪽 [B] 13번', 13, 4),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(-thing+형용사) (4점)
Ⓐ -thing 뒤에는 형용사 interesting Ⓑ -thing+형용사+to 부정사이므로 to wear Ⓒ nothing은 단수 취급하므로 is.
어법 포인트: -thing+형용사(+to부정사), 단수 취급
근거: 필기본 17쪽 예문(something interesting, something warm to wear) · 기초 10번', 14, 4),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(종합) (4점)
ⓑ 전치사 뒤에는 that을 쓸 수 없다(about whom). ⓓ whom이 met의 목적어이므로 him 삭제. ⓔ 선행사가 있으 므로 what 대신 which/that. ⓖ 목적어 it 삭제. 옳은 것은 ⓐ, ⓒ, ⓕ이다.
어법 포인트: 전치사+that 불가, 목적어 중복, 선행사 뒤 what 불 가
근거: 필기본 14쪽 표 예문·문법 1 e.g. · 15쪽 18번① · 16쪽 28·33 번', 15, 4),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영작(-thing+형용사) (4점)
I have something interesting to share with you. - thing+형용사+to부정사 순서이므로 네 번째 단어는 interesting이다.
어법 포인트: -thing+형용사+to부정사 어순
근거: 필기본 18쪽 연습 3번', 16, 4),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q17.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p17-18.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 지칭(한글 선택지) (3점)
ⓒ they는 함께 놀던 Angus와 Bruce를 가리킨다.
근거: 필기본 19쪽 본문 A 1~5', 17, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q18.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p17-18.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 불일치 (3점)
가게를 도운 사람은 Angus이다(Angus sometimes helped him). 인물이 바뀌었다. ① a small village in Scotland ③ owned a small store that made socks ④ used to have many customers
근거: 필기본 19쪽 본문 A 1·3·4·5', 18, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q19.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p19-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 주제 (3점)
추위에 발을 떠는 참새들을 보고 Angus가 밤에 양말 기계로 양말을 만드는 내용이다(필기본 요약: Angus가 추위에 떠는 참새들을 위해 양말을 만들다가 털실을 모두 써 버린다).
근거: 필기본 19쪽 본문 A 중심 내용 요약', 19, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q20.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p19-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(지문) (4점)
use up은 ‘다 써 버리다’라는 뜻의 타동사구이고 주어 he가 털실을 쓴 것이므로 능동태 used up을 써야 한다. was used up all the wool은 쓸 수 없다. ⓑ는 지각동사+목적어+현재 분사로 옳다.
어법 포인트: ⓔ 능동 used up → 수동태 오용(was used up)
근거: 필기본 19쪽 본문 A 8·9·11·14·15 · Grammar ⑨', 20, 4),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q21.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p19-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 일치 (3점)
가게가 밤에 문을 닫은 뒤 Angus는 양말 기계 앞에 앉아 양말 을 만들었다. ① 나무 위 ③ red stripes ④ Your feet are cold! ⑤ 다른 참새들을 위해서도 만들었다.
근거: 필기본 19쪽 본문 A 7·10·11·13·14', 21, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q22.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p19-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(문맥 표현) (3점)
작은 양말 한 켤레가 ‘튀어나왔다’는 popped out이다(pop out: 튀어나오다).
근거: 필기본 19쪽 본문 A 12 · Word List pop out', 22, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q23.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p23-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(지문 ⓐ~ⓖ) (4점)
ⓐ warm something → something warm ⓑ were worn → were wearing(능동) ⓓ 선행사 the socks가 있으므로 what → that. 옳은 것은 ⓒ, ⓔ, ⓕ, ⓖ이다.
어법 포인트: ⓐ -thing+형용사 어순, ⓑ 수동태 오용(were wearing → were worn), ⓓ 목적격 관계대명사 that → what
근거: 필기본 20쪽 본문 B 1~5 · Grammar ①④', 23, 4),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q24.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p23-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 알 수 있는 내용 (3점)
참새들의 양말에는 빨간 줄무늬가 있었다. ① 파란 줄무늬가 아니다 ② 사람들은 백화점으로 갔다 ③ 백화점에 간 것은 사 람들 ⑤ 같은 양말을 찾을 수 없었다.
근거: 필기본 20쪽 본문 B 2·3·5', 24, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q25.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p23-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영영풀이(지문에 없는 것) (3점)
difficulties or problems는 trouble의 뜻풀이로 윗글에 없다. ① as soon as ③ stripe ④ department store ⑤ sparrow는 윗글에 있다.
근거: 필기본 12~13쪽 Word List 영어 뜻풀이 · 20쪽 본문 B 1~5', 25, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q26.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p26-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 문맥상 쓰임 (3점)
참새들이 추운 날씨에 ‘떨고 있어서’ 양말을 만든 것이므로 원 문은 They were shaking in the cold weather.이다. warm은 문맥에 맞지 않다.
근거: 필기본 20쪽 본문 B 8', 26, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q27.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p26-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(문맥 표현) (3점)
‘정직하게’는 honestly이다. Angus honestly said, “Sorry, ...”
근거: 필기본 20쪽 본문 B 8 · Word List honestly', 27, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q28.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p26-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 일치 (3점)
Bruce는 Angus의 주머니 속에서 조용히 듣고 있었다. ② 양 말 가게 ③ Someone took ④ 참새들을 위한 양말 ⑤ 사과한 사람은 Angus.
근거: 필기본 20쪽 본문 B 6~9 · Q5', 28, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q29.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p29-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문) (4점)
명사구 our socks 앞에는 전치사 역할의 because of를 쓴다. because 뒤에는 주어+동사가 온다.
어법 포인트: ⓒ because of(+명사) → because로 바꿈
근거: 필기본 21쪽 본문 C 3 · Q(Why was Angus in trouble?)', 29, 4),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q30.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p29-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 일치 (3점)
Bruce는 “We should help him.”이라고 말했다. ① 참새들 을 모은 것은 Bruce ③ Bruce가 한 말 ④ 다른 참새들이 한 말 ⑤ 참새들은 동의했다.
근거: 필기본 21쪽 본문 C 1~4', 30, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q31.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p31-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(that의 쓰임) (4점)
윗글의 that은 선행사 the socks를 꾸미는 목적격 관계대명 사이다. ③도 선행사 The book을 꾸미는 목적격 관계대명사 이다. ①⑤ 진주어 명사절 접속사 ② 지시대명사 ④ 동격 접속 사.
어법 포인트: 목적격 관계대명사 that 구별
근거: 필기본 21쪽 본문 C 7 Grammar · 15쪽 21번(c)·22번① · 16 쪽 31번③ · 06쪽 35·39번(Lesson 7)', 31, 4),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q32.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p31-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 조건영작 (4점)
your kind heart has come back to us as a great gift. 현 재완료 has+과거분사(come)이므로 다섯 번째 단어는 come 이다.
어법 포인트: 현재완료 has+p.p.(has come)
근거: 필기본 21쪽 본문 C 9 · Grammar ⑨', 32, 4),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q33.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p31-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 답할 수 없는 질문 (3점)
가게에 온 손님의 수는 나오지 않는다. ② on the table ③ customers ④ warm socks with red stripes ⑤ dogs and cats
근거: 필기본 21쪽 본문 C 5·8·10·11 · Q6·Q7', 33, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q34.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p31-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 문장 넣기 (3점)
참새들이 탁자 위에 양말을 쌓은 바로 그때 사람들이 가게로 몰려왔고, 그 뒤에 누군가 “드디어!”라고 말한다. 따라서 ⓐ가 알맞다.
근거: 필기본 21쪽 본문 C 5~7', 34, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q35.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 제목 빈칸 (3점)
진정한 친구(A true friend)가 하는 일을 A~L로 정리한 글이 므로 제목은 The ABCs of Friendship이다.
근거: 필기본 22쪽 More Reading 1 · 1번', 35, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q36.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 문맥상 쓰임 (3점)
진정한 친구는 너를 ‘포기하지 않는다’. 원문은 Doesn’t give up on you.이다. Doesn’t believe in you는 ⓑ Believes in “you.”와도 맞지 않다.
근거: 필기본 22쪽 More Reading 1 (D 행)', 36, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q37.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 불일치 (3점)
진정한 친구는 그저 “안녕”이라고 말하려고 전화한다(Calls you just to say “Hi.”). 도움이 필요할 때만 전화한다는 것은 전화하는 목적이 바뀌었다. ① Embraces you as you are.
③ Invites you over. ④ Just wants to “be” with you. ⑤ Loves you for who you are.
근거: 필기본 22쪽 More Reading 1 (C·E·I·J·L 행) 및 해석', 37, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q38.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문) (4점)
사자가 사냥꾼의 그물에 ‘잡힌’ 것이므로 수동태 be+과거분사 를 써서 was caught로 고친다. ⓐ는 let+목적어+동사원형, ⓑ는 분사구문, ⓓ는 struggle+to부정사, ⓔ는 전치사 for+동 명사로 옳다.
어법 포인트: ⓒ 수동태 was caught → was catching으로 바꿈
근거: 필기본 23쪽 More Reading 2 둘째 단락 · 문법 필기', 38, 4),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q39.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(문맥 표현) (3점)
‘우연히’는 동사 ran을 꾸미는 부사 accidentally이다(필기 accidentally: 우연히, 뜻하지 않게).
근거: 필기본 23쪽 More Reading 2 첫 문장 · 어휘 필기 accidentally', 39, 3),
(@naesin_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r08/q40.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r08/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 일치 (3점)
쥐는 “I will repay your kindness someday.”라고 약속했다. ① 쥐가 꼬리 위를 지나가서 깼다 ③ 날카로운 이빨로 끊 었다 ④ 쥐가 듣고 달려왔다 ⑤ 고마워한 것은 사자.
근거: 필기본 23쪽 More Reading 2 · 2번', 40, 3);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM2-2602-R09', 'NAESIN', '압구정중 2학년 2학기 기말 9회 · Lesson 8 예상문제 4회', 'Lesson 8 학습지 필기본 12~23쪽 · 40문항 · 20분 · 135점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_r09 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM2-2602-R09');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 발음부호 (3점)
wool은 짧은 모음 [ʊ]로 발음한다. 바른 발음부호는 [wʊl]이다.
근거: 필기본 12~13쪽 Lesson 8 Word List · wool [wʊl] (13쪽 주 의: wool은 [wʊl])', 1, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 발음부호 (3점)
silently는 첫 음절에 주강세가 있다. 바른 발음부호는 [ˈsaɪləntli]이다.
근거: 필기본 12쪽 Word List · silently [ˈsaɪləntli], customer, department store, pop out, return', 2, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(영영풀이) (3점)
‘작은 마을에 사는 사람’은 villager(마을 사람)의 영어 뜻풀이 이다.
근거: 필기본 13쪽 Word List · villager 영어 뜻풀이', 3, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(문장 빈칸) (3점)
토스트가 다 되면 토스터에서 ‘튀어나온다’는 뜻이므로 pop out이 알맞다.
근거: 필기본 13쪽 Word List 연습 15번', 4, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(문장 빈칸) (3점)
제일 좋아하는 한 켤레 중 ‘양말’ 한 짝을 잃어버린 것이므로 sock이 알맞다.
근거: 필기본 12쪽 Word List · sock 예문', 5, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(빈칸 불가) (3점)
차례대로 sparrow, stripes, knock, wool이 들어간다. 네 빈 칸은 모두 명사 자리이므로 부사 recently(최근에)는 어느 빈 칸에도 들어갈 수 없다.
근거: 필기본 12~13쪽 Word List 예문(sparrow, stripe, knock, wool) · 19쪽 본문 A Vocabulary recently', 6, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(문장 빈칸) (3점)
새 드레스에 만족한 사람은 손님(customer)이다.
근거: 필기본 12쪽 Word List · customer 예문', 7, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(-thing+형용사) (3점)
nothing 뒤에서 꾸미는 말은 형용사여야 한다. 부사 interestingly는 쓸 수 없다.
어법 포인트: -thing+형용사(부사 불가)
근거: 필기본 17쪽 기초 4번', 8, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(목적격 관계대명사) (4점)
전치사 바로 뒤에는 who를 쓰지 않고 whom을 쓴다. → The woman with whom he works is friendly.
어법 포인트: 전치사+whom(전치사 뒤 who 불가)
근거: 필기본 14쪽 표 예문, [B] 9번 · 15쪽 19번② · 16쪽 34번③', 9, 4),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(종합) (4점)
옳은 문장은 1·3·5·6·8번째 5개이다. made it → it 삭제, in which I was born in → 뒤의 in 삭제, warm something → something warm, something to do important → something important to do.
어법 포인트: 목적어 중복, 전치사 중복, -thing+형용사(+to부정 사) 어순
근거: 필기본 15쪽 19번 ①③④ · 16쪽 34번 ②④ · 18쪽 연습 9번', 10, 4),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(-thing+형용사) (4점)
Ⓐ·Ⓒ -thing 뒤에서 꾸미는 말은 형용사(important, special). Ⓑ anything은 단수 취급하므로 is.
어법 포인트: -thing+형용사, -thing 단수 취급
근거: 필기본 17쪽 예문 · 18쪽 연습 4·14번', 11, 4),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영작(목적격 관계대명사 생략) (4점)
The phone I bought last week is great.(8단어) 관계대명 사를 생략하면 다섯 번째 단어는 last이다.
어법 포인트: 목적격 관계대명사 생략
근거: 필기본 16쪽 29번', 12, 4),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영작(두 문장 연결) (4점)
The artist whom we visited last month is famous. her 는 whom이 대신하므로 쓰지 않는다. 다섯 번째 단어는 visited이다.
어법 포인트: 목적격 관계대명사절 위치(선행사 바로 뒤)와 목적어 삭제
근거: 필기본 14쪽 [B] 14번', 13, 4),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(-thing+형용사) (4점)
someone, something처럼 -one, -thing으로 끝나는 대명사 는 형용사가 뒤에서 꾸민다. ① Are → Is ② something interesting ③ nothing new ④ something new
어법 포인트: -thing+형용사 어순, 단수 취급
근거: 필기본 17쪽 예문·기초 9번·보충 1번③·보충 2번①', 14, 4),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영작(-thing+형용사+to부정사) (4점)
I want to buy something new to read this weekend. - thing+형용사+to부정사 순서이므로 여섯 번째 단어는 new 이다.
어법 포인트: -thing+형용사+to부정사 어순
근거: 필기본 18쪽 연습 19번', 15, 4),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(전치사+관계대명사) (4점)
Ⓐ 전치사 on 바로 뒤, 사물 선행사이므로 which(that 불가)
Ⓑ 전치사가 뒤에 있으므로 which/that 모두 가능(사물이므 로 whom 불가) Ⓒ 전치사 about 바로 뒤, 사람 선행사이므 로 whom(who·that 불가).
어법 포인트: 전치사+which/whom(전치사 뒤 that·who 불가), 사물 선행사 which/that
근거: 필기본 15쪽 24·26번 · 16쪽 27번', 16, 4),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q17.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 의미·어법(지문) (4점)
ⓐ there lived a boy는 원문 그대로이다. ⓑ used to+동사 원형(have) ⓒ 손님을 많이 잃었으므로 원래 손님이 many였 다 ⓓ 백화점에서 양말을 사기 started(시작했다) ⓔ 나무 위 에 있던 것은 다른 sparrows이다.
어법 포인트: ⓑ used to+동사원형(have) → having으로 바꿈 (ⓒⓓⓔ는 의미 오류)
근거: 필기본 19쪽 본문 A 1·5·6·7 · used to 필기', 17, 4),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q18.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영영풀이(지문에 없는 것) (3점)
the sound of hitting a door to get someone’s attention 은 knock의 뜻풀이로 윗글에 없다. ① sparrow ② customer ④ villager ⑤ be covered with는 윗글에 있다.
근거: 필기본 12~13쪽 Word List 영어 뜻풀이 · 19쪽 본문 A 1~8', 18, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q19.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(that의 쓰임) (4점)
윗글의 that은 선행사 a small store를 꾸미는 주격 관계대명 사이다. ⑤도 선행사 the cat을 꾸미는 주격 관계대명사이다.
① 진주어 접속사 ② 지시형용사 ③ 동격 접속사 ④ 목적어절 접속사.
어법 포인트: 관계대명사 that 구별(주격)
근거: 필기본 19쪽 본문 A 4 Grammar ④ · 15쪽 20번④ · 16쪽 31 번①②③ · 06쪽 31번(Lesson 7)', 19, 4),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q20.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p20-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(지문) (4점)
pop out(튀어나오다)은 목적어가 없는 자동사구이므로 수동 태로 쓰지 않는다. a tiny pair of socks popped out으로 고 친다. ⓐ 지각동사+현재분사 ⓑ 복수 주어 feet+are ⓒ 접속 사 After ⓔ 접속사 because+주어+동사는 옳다.
어법 포인트: ⓓ 능동 popped out → 수동태 오용(were popped out)
근거: 필기본 19쪽 본문 A 9~14 · Grammar ⑨', 20, 4),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q21.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p20-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 연결어 (3점)
양말 기계 앞에 앉은 뒤 ‘곧’ 작은 양말 한 켤레가 튀어나왔다. 원문은 Soon이다.
근거: 필기본 19쪽 본문 A 11·12', 21, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q22.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p20-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(문맥 표현) (3점)
‘다 써 버렸다’는 used up이다(use up: 다 써 버리다).
근거: 필기본 19쪽 본문 A 15 · Word List use up', 22, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q23.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p20-23.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 불일치 (3점)
Angus는 추위에 떠는 참새들을 위해 양말을 만들었다. 마을 사람들로 대상이 바뀌었다.
근거: 필기본 19쪽 본문 A 10~14 · Q2', 23, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q24.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p24-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 지칭·의미(한글 선택지) (3점)
ⓓ them은 사람들이 사고 싶어 하는, 참새들이 신고 있는 양 말(the socks)을 가리킨다.
근거: 필기본 20쪽 본문 B 1~5 · 어휘 anywhere', 24, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q25.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p24-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 연결어 (3점)
사람들이 양말을 사고 싶어 했지만 어디에서도 찾을 수 없었 으므로 역접의 But이 알맞다.
근거: 필기본 20쪽 본문 B 4·5', 25, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q26.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p24-26.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 일치 (3점)
사람들은 어디에서도 참새들의 것과 같은 양말을 찾을 수 없 었다. ① something warm ② red stripes ③ 물은 사람은 사람들 ④ 양말을 보자마자 갔다.
근거: 필기본 20쪽 본문 B 1~5', 26, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q27.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p27-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 의미·어법(지문) (4점)
ⓔ silently는 원문 그대로이다. ⓐ 아버지가 “Son”이라고 부 른 사람은 Angus ⓑ -thing 뒤에는 형용사 strange ⓒ took(능동) ⓓ make socks for+대상.
어법 포인트: ⓑ something+형용사(strange) → 부사로 바꿈, ⓒ 능동 took → 수동태 오용, ⓓ made socks for → to (ⓐ는 의 미 오류)
근거: 필기본 20쪽 본문 B 6~9 · Grammar ①⑦ · 필기(make socks for sparrows)', 27, 4),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q28.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p27-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영영풀이(지문에 없는 것) (3점)
to give something back or go back to a place는 return 의 뜻풀이로 윗글에 없다. ② honestly ③ silently ④ wool
⑤ shake는 윗글에 있다.
근거: 필기본 12~13쪽 Word List 영어 뜻풀이 · 20쪽 본문 B 6~9', 28, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q29.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p29-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(빈칸 짝짓기) (4점)
Ⓑ need+to부정사 Ⓒ 명사구 our socks 앞이므로 because of Ⓓ agree는 수동태로 쓰지 않으므로 agreed.
어법 포인트: Ⓑ need+to부정사, Ⓒ because of+명사, Ⓓ 자동 사 agree 수동태 불가
근거: 필기본 21쪽 본문 C 1~4 · Grammar ②', 29, 4),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q30.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p29-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(문맥 표현) (3점)
‘잠시 후’는 After a while이다. For a while 잠시 동안, A while ago 조금 전에, After all 결국, All the while 그동안 내내.
근거: 필기본 21쪽 본문 C 1 및 해석', 30, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q31.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p29-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 알 수 있는 내용 (3점)
다른 참새들은 “We like these socks.”라고 말했다. ① Bruce는 돌려줘야 한다고 했다 ② 모은 것은 Bruce ③ 곤경 에 빠진 것은 Angus ④ 참새들은 동의했다.
근거: 필기본 21쪽 본문 C 1~4', 31, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q32.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문 ⓐ~ⓖ) (4점)
ⓐ fly into는 수동태로 쓰지 않는다(flew) ⓓ 선행사 the socks가 있으므로 what → that ⓕ 현재완료는 has+과거분 사 come. 옳은 것은 ⓑ, ⓒ, ⓔ, ⓖ이다.
어법 포인트: ⓐ 능동 flew → 수동태 오용, ⓓ 목적격 관계대명사 that → what, ⓕ 현재완료 has come → has came
근거: 필기본 21쪽 본문 C 5~11 · Grammar ⑦⑨', 32, 4),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q33.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 불일치 (3점)
좋은 선물로 돌아왔다고 말한 사람은 Angus의 아버지이다 (said his father). 인물이 바뀌었다.
근거: 필기본 21쪽 본문 C 5~11', 33, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q34.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 유추할 수 없는 것 (3점)
참새들은 양말을 가게 탁자 위에 쌓아 두었다. 양말을 나무로 다시 가져갔다고 볼 수 없다. 나머지는 가게가 손님들로 붐비 고 아버지가 Angus의 마음을 칭찬한 데서 유추할 수 있다.
근거: 필기본 21쪽 본문 C 5~11 · 중심 내용 요약', 34, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q35.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문) (4점)
주어 A true friend는 3인칭 단수이므로 Doesn’t give up on you.로 써야 한다. ⓑ는 목적의 to부정사, ⓔ는 want의 목적어 to부정사이다.
어법 포인트: ⓒ 주어(A true friend)·동사 수일치 Doesn’t → Don’t로 바꿈
근거: 필기본 22쪽 More Reading 1 (D 행)', 35, 4),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q36.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(문맥 표현) (3점)
‘조건 없이 베푼다’는 뜻이므로 unconditionally이다. Gives unconditionally.
근거: 필기본 22쪽 More Reading 1 (G 행) 및 해석 ‘조건 없이 베 푼다’', 36, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q37.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 알 수 있는 내용 (3점)
Just wants to “be” with you.(그저 너와 함께 있고 싶어 한다.)를 바꿔 쓴 ④가 알맞다. ① 있는 그대로 받아들인다 (Accepts you as you are.) ② 조건 없이 베푼다 ③ 그저 “안 녕”이라고 말하려고 전화한다 ⑤ 실수를 잊는 것이 아니라 용 서한다(Forgives).
근거: 필기본 22쪽 More Reading 1 (A·C·F·G·J 행) 및 해석', 37, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q38.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 지칭(한글 선택지) (3점)
ⓑ me는 놓아 달라고 애원하는 쥐(the mouse)를 가리킨다.
근거: 필기본 23쪽 More Reading 2', 38, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q39.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 불일치 (3점)
사자는 쥐가 꼬리 위를 달려 지나가서 깨어났다. 배가 고파서 깬 것이 아니다.
근거: 필기본 23쪽 More Reading 2 · 2번', 39, 3),
(@naesin_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r09/q40.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r09/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 연결어 (3점)
쥐가 너무 작아 도움이 안 된다고 말했지만 ‘그러나’ 놓아주었 으므로 However가 알맞다.
근거: 필기본 23쪽 More Reading 2 · 어휘 필기(however)', 40, 3);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM2-2602-R10', 'NAESIN', '압구정중 2학년 2학기 기말 10회 · Lesson 8 예상문제 5회', 'Lesson 8 학습지 필기본 12~23쪽 · 40문항 · 20분 · 134점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_r10 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM2-2602-R10');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 발음부호 (3점)
sparrow의 발음부호는 [ˈspæroʊ]로 첫째 음절에 주강세가 있다. ④는 주강세를 둘째 음절로 옮겨 틀리다.
근거: 필기본 12~13쪽 Word List · sparrow [ˈspæroʊ], stripe [straɪp], sock [sɑːk], shake [ʃeɪk], customer [ˈkʌstəmər]', 1, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 발음부호 (3점)
return의 발음부호는 [rɪˈtɜːrn]으로 둘째 음절에 주강세가 있다. ①은 주강세를 첫째 음절로 옮겨 틀리다.
근거: 필기본 12~13쪽 Word List · return [rɪˈtɜːrn], trouble [ˈtrʌbəl], villager [ˈvɪlɪdʒər], silently [ˈsaɪləntli], pair [per]', 2, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(영영풀이) (3점)
무언가를 돌려주거나 어떤 장소로 돌아가는 것은 return(돌 려주다, 돌아오다)이다.
근거: 필기본 12쪽 return 영어 뜻풀이', 3, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(빈칸) (3점)
‘양모로 만든 따뜻한 스웨터’이므로 wool(양모, 털실)이 알맞다.
근거: 필기본 13쪽 wool 예문 She wore a warm sweater made of wool.', 4, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(빈칸) (3점)
나무 위에 내려앉은 것은 작은 참새(sparrow)이다.
근거: 필기본 12쪽 sparrow 예문 A little sparrow landed on a tree.', 5, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(빈칸 불가) (3점)
차례대로 rush, knock, return, shake가 들어간다. 모두 to 뒤에 동사원형이 와야 하므로 부사 quietly는 어느 빈칸에도 들어가지 않는다.
근거: 필기본 12쪽 rush·return 예문 · 13쪽 연습 13·18번 · silently 유의어 quietly', 6, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(숙어) (3점)
be in trouble: 곤란에 처하다. ‘너에게 문제가 있으면 내가 너를 도울게.’
근거: 필기본 13쪽 trouble 예문 · 21쪽 본문 C ③ Angus is in trouble', 7, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(목적격 관계대명사) (4점)
목적격 관계대명사(생략)가 이끄는 절에는 목적어를 다시 쓰 지 않는다. borrowed it → borrowed.
어법 포인트: 목적격 관계대명사절의 목적어 중복 금지(③ it 삭 제)
근거: 필기본 14쪽 e.g. · 15쪽 20번 ①③④ · 16쪽 35번', 8, 4),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(관계대명사) (4점)
Ⓐ 선행사 the cat(동물) → which Ⓑ 선행사가 사람+동물 (the boy and his dog) → that Ⓒ 선행사 The boy(사람) → whom
어법 포인트: 선행사에 따른 관계대명사(동물 which, 사람+동물 that, 사람 whom)
근거: 필기본 14쪽 5·10번, 16쪽 30번', 9, 4),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(관계대명사) (4점)
The boy with whom you were playing is my cousin. 전 치사 바로 뒤에는 that이나 who를 쓰지 않고 whom을 쓴다. 네 번째 단어는 whom이다.
어법 포인트: 전치사 + 목적격 관계대명사 whom
근거: 필기본 15쪽 25번', 10, 4),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(관계대명사 that) (4점)
<보기>의 that은 선행사 the girl을 꾸미는 절의 목적격 관계 대명사이다. ⑤도 선행사 The song을 꾸미는 목적격 관계대 명사이다. ① 진주어 that절 ② 지시형용사 ③ 동격 접속사 ④ 명사절 접속사
어법 포인트: 목적격 관계대명사 that
근거: 필기본 14쪽 e.g. Emma is the girl that I told you about. · 15쪽 18번 ③ · 16쪽 31번 · 06쪽 35번', 11, 4),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(-thing+형용사) (4점)
-thing으로 끝나는 대명사는 형용사가 뒤에서 꾸민다. ① anything strange ③ Nothing is(단수 취급) ④ nothing new ⑤ something new
어법 포인트: -thing + 형용사 어순, -thing 단수 취급
근거: 필기본 17쪽 기초 1·5·10번, 보충 5번 ① · 18쪽 15번', 12, 4),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(-thing+형용사) (4점)
틀린 문장은 4개이다. Something special are → is, wrong anything → anything wrong, new something → something new, delicious something → something delicious.
어법 포인트: -thing + 형용사(+to부정사) 어순, -thing 단수 취 급
근거: 필기본 17쪽 기초 8번, 보충 2④·5②⑤, e.g. · 18쪽 7③·15·17·19번', 13, 4),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영작(-thing+형용사) (4점)
There was nothing strange in the room. 형용사 strange는 nothing 뒤에 온다. 네 번째 단어는 strange이다.
어법 포인트: nothing + 형용사
근거: 필기본 18쪽 20번 방 안에는 이상한 것이 전혀 없었다. There was nothing strange in the room.', 14, 4),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(목적격 관계대명사) (4점)
옳은 문장은 5개이다(movie that, American boy whom, singer whom, cat that, friend whom … to). 틀린 문장: which → who/whom/that(사람), with that → with whom, in that → in which, with who → with whom.
어법 포인트: 선행사(사람)에 맞는 관계대명사, 전치사 + whom/which(that·who 불가)
근거: 필기본 14쪽 e.g. · 15쪽 15·20·25번 · 16쪽 28·34번', 15, 4),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(-thing+형용사) (3점)
-thing + 형용사 + to부정사 어순이므로 interesting anything → anything interesting.
어법 포인트: -thing + 형용사 + to부정사 어순(⑤ anything interesting)
근거: 필기본 18쪽 4·6·11·12·18번', 16, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q17.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 문장 넣기 (3점)
가게가 최근 손님을 많이 잃었다는 문장 뒤(ⓔ)에, 그 이유로 마을 사람들이 큰 백화점에서 양말을 사기 시작했다는 문장이 와야 한다.
근거: 필기본 19쪽 본문 A ⑤⑥⑦', 17, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q18.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 알 수 있는 내용 (3점)
가게는 예전에 손님이 많았지만 최근에 많은 손님을 잃었으므 로 지금은 손님이 예전보다 적다. ② small village ③ father
④ Angus가 아버지를 도왔다 ⑤ Angus의 친한 친구
근거: 필기본 19쪽 본문 A ①②④⑤', 18, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q19.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p17-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 연결어 (3점)
‘그들이 함께 놀 때’ Bruce가 Angus의 주머니로 날아들었다 는 흐름이므로 When이 알맞다.
근거: 필기본 19쪽 본문 A ③', 19, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q20.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문) (4점)
ⓑ 지각동사 saw + 목적어 + 현재분사(동사원형)이므로 raised → raising. ⓐ 수동태 be covered with ⓒ because + 절 ⓓ 명사절 접속사 that ⓔ used up(다 써 버렸다)
어법 포인트: ⓑ 지각동사 see + 목적어 + 현재분사(raised → raising)
근거: 필기본 19쪽 본문 A ⑧⑨⑭⑮ · Grammar ⑨', 20, 4),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q21.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(표현) (3점)
in front of: ~앞에. Angus sat in front of the sock machine.
근거: 필기본 19쪽 본문 A ⑪ After the store closed for the night, Angus sat in front of the sock machine.', 21, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q22.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 불일치 (3점)
나무는 눈(snow)으로 덮여 있었다.
근거: 필기본 19쪽 본문 A ⑧⑩⑪⑬⑮', 22, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q23.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p23-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영작(관계대명사) (4점)
We want the socks that the sparrows are wearing. (9 단어) 다섯 번째 단어는 목적격 관계대명사 that이다. 조건에 따라 which는 쓰지 않고, 관계대명사가 목적어를 대신하므로 them을 붙이지 않는다.
어법 포인트: 선행사 the socks + 목적격 관계대명사 that + 주어 + 동사
근거: 필기본 20쪽 본문 B ④ 및 해석 · Grammar ④', 23, 4),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q24.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p23-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 답할 수 없는 질문 (3점)
양말의 가격은 나오지 않는다(백화점에서는 같은 양말을 찾을 수 없었다). ① cute socks with red stripes ② hurried to the department store ③ the department store ④ the socks that the sparrows were wearing
근거: 필기본 20쪽 본문 B ①~⑤ · Comprehension Check Q4', 24, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q25.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p23-25.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 일치 (3점)
사람들은 양말을 보자마자 백화점으로 서둘러 갔다. ① red stripes ② department store ③ 사람들이 물었다 ⑤ 어디 에서도 찾을 수 없었다
근거: 필기본 20쪽 본문 B ②③④⑤', 25, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q26.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p26-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법·의미(지문) (4점)
ⓑ Someone took all of our wool. 은 문맥과 어법 모두 옳다. ⓐ -thing + 형용사이므로 something strange ⓒ 사실 대로 말했으므로 honestly ⓓ 참새들이 추위에 떨고 있었으 므로 cold ⓔ 듣고 있었으므로 진행형 was listening
어법 포인트: ⓐ -thing + 형용사(strange something → something strange) · ⓒ 의미(dishonestly → honestly) · ⓓ 의미(hot → cold) · ⓔ 진행형(was listened → was listening)
근거: 필기본 20쪽 본문 B ⑥~⑨ · Grammar ⑦', 26, 4),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q27.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p26-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(영영풀이) (3점)
③은 villager의 뜻풀이로 윗글에 없다. ① sock ② wool ④ shake ⑤ silently는 윗글에 있다.
근거: 필기본 12~13쪽 영어 뜻풀이 · 20쪽 본문 B ⑥~⑨', 27, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q28.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p26-28.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 유추할 수 없는 것 (3점)
아버지는 ‘뭔가 이상한 일이 일어났다, 누군가 털실을 모두 가 져갔다’고 했으므로 털실이 없어져서 기뻐했다고 볼 수 없다.
근거: 필기본 20쪽 본문 B ⑥~⑨', 28, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q29.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p29-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(문맥) (3점)
참새들은 양말을 돌려주려고 양말 가게 ‘안으로’ 날아 들어가 탁자 위에 양말을 쌓았으므로 out of가 아니라 into가 알맞다.
근거: 필기본 21쪽 본문 C ⑤ · Comprehension Check Q6', 29, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q30.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p29-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(수동태) (4점)
The other sparrows were brought together by Bruce. bring의 과거분사는 brought이다(bring–brought– brought). 다섯 번째 단어는 brought이다.
어법 포인트: 수동태 be + 과거분사(복수 주어 → were, bring– brought–brought)
근거: 필기본 21쪽 본문 C ① · 필기 brought는 bring의 과거형', 30, 4),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q31.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p29-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 답할 수 없는 질문 (3점)
참새들이 양말을 얼마나 오래 신었는지는 나오지 않는다. ① return the socks ② They liked the socks. ③ help him
⑤ Alright then.
근거: 필기본 21쪽 본문 C ①~④', 31, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q32.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(지문 ⓐ~ⓖ) (4점)
옳은 것: ⓐ rushed ⓒ 목적격 관계대명사 that ⓕ are all wearing(진행형). 틀린 것: ⓑ These is → are ⓓ crowded → was crowded ⓔ has came → has come ⓖ dogs and cats is → are
어법 포인트: ⓑⓖ 주어·동사 수일치(is → are) · ⓓ be crowded with · ⓔ 현재완료 has + 과거분사(came → come)
근거: 필기본 21쪽 본문 C ⑥~⑪ · Grammar ⑦⑨', 32, 4),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q33.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 주제 (3점)
Angus의 친절한 마음이 큰 선물로 돌아왔다는 내용이다. 필 기: 친절한 행동은 자신에게도 돌아온다.
근거: 필기본 21쪽 본문 C ⑨ · 필기(친절한 행동은 자신에게도 돌 아온다)', 33, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q34.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p32-34.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 알 수 있는 내용 (3점)
사람들이 가게로 서둘러 들어와 ‘드디어! 이게 내가 원하는 양 말이야!’라고 했고 가게는 손님들로 붐볐다. ① 사람들이 들어 왔다 ② 마을 사람들도 신고 있다 ③ 원하는 양말이라고 했다
⑤ 빨간 줄무늬
근거: 필기본 21쪽 본문 C ⑥⑦⑧⑩⑪', 34, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q35.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 문장 넣기 (3점)
각 줄이 A, B, C … 순서의 글자로 시작한다. Forgives는 F로 시작하므로 E(Embraces)와 G(Gives) 사이, 곧 ⓔ에 들어간다.
근거: 필기본 22쪽 More Reading 1 The ABCs of Friendship', 35, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q36.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 답할 수 없는 질문 (3점)
우정이 얼마나 오래가는지는 나오지 않는다. ① just to say “Hi.” ② unconditionally ④ “you” ⑤ close at heart
근거: 필기본 22쪽 More Reading 1', 36, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q37.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p35-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 일치 (3점)
Loves you for who you are.(있는 그대로의 너를 사랑한다.)와 일치한다. ① “you”를 믿는다 ② 있는 그대로의 너를 받아들인다 ④ Invites you over에 생일이라는 조건은 없다
⑤ Gives unconditionally.
근거: 필기본 22쪽 More Reading 1 해석', 37, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q38.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(지문 ⓐ~ⓖ) (4점)
옳은 것: ⓑ let + 목적어 + 동사원형 ⓒ 명사절 접속사 that ⓔ to escape(원문) ⓕ 목적의 to부정사. 틀린 것: ⓐ to go → go ⓓ caught → was caught ⓖ save → saving. 선택지 중 옳은 것만으로 이루어진 것은 ①뿐이다.
어법 포인트: ⓐ 사역동사 let + 동사원형 · ⓓ 수동태 was caught · ⓖ 전치사 for + 동명사
근거: 필기본 23쪽 More Reading 2 · 필기(let + 목적어 + 동사원 형)', 38, 4),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q39.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 알 수 있는 내용 (3점)
쥐는 언젠가 은혜를 갚겠다고 했고, 그물에 걸린 사자를 구해 주었다. ① forest ② accidentally ③ 쥐가 밧줄을 끊어 주었 다 ④ You are so tiny.
근거: 필기본 23쪽 More Reading 2', 39, 3),
(@naesin_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r10/q40.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r10/p38-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 속담 (3점)
사자가 쥐를 놓아준 선행이 쥐가 사자를 구해 주는 선행으로 돌아왔으므로 ‘하나의 선행은 또 다른 선행으로 보답받는다’가 어울린다.
근거: 필기본 23쪽 1번 · 22쪽 속담 필기 One good turn deserves another.', 40, 3);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM2-2602-R11', 'NAESIN', '압구정중 2학년 2학기 기말 11회 · Lesson 7·8 종합 예상문제 1회', 'Lesson 7·8 학습지 필기본 1~23쪽 · 70문항 · 35분 · 237점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_r11 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM2-2602-R11');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 발음부호(7과) (3점)
baobab tree의 발음부호는 [ˈbeɪoʊbæb triː]로 첫째 음절에 주강세가 있다. ②는 강세 위치를 둘째 음절로 옮긴 것이다.
근거: 필기본 01쪽 Lesson 7 단어 · baobab tree [ˈbeɪoʊbæb triː], effort, dynamic, icy, hunting', 1, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 발음부호(8과) (3점)
bring … together의 발음부호는 [brɪŋ … təˈɡeðər]로 together의 둘째 음절에 주강세가 있다. ④는 강세 위치를 첫 째 음절로 옮긴 것이다.
근거: 필기본 12~13쪽 Lesson 8 Word List · bring … together [brɪŋ … təˈɡeðər], customer, silently, villager, sparrow', 2, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(문장 빈칸·7과) (3점)
놀라운 속도와 아름다운 점박이 털로 알려진 동물은 leopard(표범)이다.
근거: 필기본 02쪽 단어 연습 14번 · 01쪽 leopard', 3, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(문장 빈칸·7과) (3점)
‘불법’ 사냥 때문에 호랑이 개체 수가 급격히 줄었다는 뜻이므 로 Illegal이 알맞다.
근거: 필기본 02쪽 단어 연습 17번 · 01쪽 illegal(illegal hunting)', 4, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(문장 빈칸·7과) (3점)
‘얼음에 뒤덮인’ 도로에서 더 조심히 운전했다는 뜻이므로 icy 가 알맞다.
근거: 필기본 01쪽 icy 예문', 5, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(빈칸 공통·7과) (3점)
차례대로 trap, hunting, product, present가 들어간다. explore(탐험하다)는 동사로 어느 빈 칸에도 들어갈 수 없다.
근거: 필기본 01쪽 trap·hunting·product·present 예문', 6, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이·8과) (3점)
‘빨리 움직이거나 서두르다’는 rush(급히 움직이다, 서두르 다)의 영어 뜻풀이이다.
근거: 필기본 12쪽 rush 영어 뜻풀이', 7, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(문장 빈칸·8과) (3점)
a pair of shoes: 신발 한 켤레. 딸의 생일 선물로 새 신발 한 켤레를 샀다는 뜻이다.
근거: 필기본 13쪽 Word List 연습 14번 · 12쪽 pair', 8, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(문장 빈칸·8과) (3점)
도서관에 책을 ‘돌려주어야’ 한다는 뜻이므로 return이 알맞다.
근거: 필기본 12쪽 return 예문', 9, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(빈칸 공통·8과) (3점)
차례대로 knock, customer, stripes, villager가 들어간다. honestly(정직하게)는 부사로 어느 빈 칸에도 들어갈 수 없다.
근거: 필기본 12~13쪽 knock·customer·stripe·villager 예문', 10, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(사역·준사역) (4점)
help+목적어+동사원형/to부정사이므로 ①은 preparing을 prepare 또는 to prepare로 고쳐야 한다. ②③④⑤는 make/have/let+목적어+동사원형으로 옳다.
어법 포인트: ① preparing → prepare/to prepare (help+목적 어+동사원형/to부정사)
근거: 필기본 03쪽 2·12·13번, 04쪽 24·33번', 11, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(사역·준사역) (4점)
had his assistant print / let anyone enter / let anger ruin / get my hair cut. 머리카락은 ‘잘리는’ 것이므로 get+목적어+과거분사 cut을 쓰고, to cut은 어디에도 들어갈 수 없다.
어법 포인트: have/let+목적어+동사원형, get+목적어+과거분사 (수동 관계) — to cut 불가
근거: 필기본 03쪽 6번, 04쪽 21·26·36번', 12, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(가주어·의미상 주어) (4점)
틀린 문장은 3개이다. It is not good to skip ~(to 빠짐), It is essential for students ~(성격 형용사가 아니므로 for), It is impossible to live ~(to 빠짐).
어법 포인트: skip → to skip, of students → for students, live → to live (진주어 to부정사, 의미상 주어 for+목적격)
근거: 필기본 05쪽 1·11번, 예문 · 06쪽 16·21·36번', 13, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(의미상 주어) (3점)
careless(부주의한)는 사람의 성격·태도를 나타내는 형용사이 므로 to부정사의 의미상 주어는 of+목적격을 쓴다. It was careless of him to forget his passport at home.
어법 포인트: 성격 형용사 + of + 목적격 + to부정사
근거: 필기본 06쪽 27번 · 05쪽 의미상 주어(성격 형용사 + of + 목 적격)', 14, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(가주어·that절) (3점)
Ⓐ easy는 성격 형용사가 아니므로 의미상 주어는 for her.
Ⓑ 뒤에 주어+동사가 있는 절이 오므로 that. Ⓒ 진주어는 to 부정사이므로 to despise.
어법 포인트: 의미상 주어 for/of, It ~ that절, 진주어 to부정사
근거: 필기본 05쪽 예문 ④, 06쪽 31번, 22쪽 필기(The Fox and the Grapes)', 15, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(사역동사 수동태) (4점)
The team was made to finish the project by Friday. 사 역동사 make를 수동태로 쓰면 동사원형이 to부정사로 바뀐 다(be made to+동사원형). 다섯 번째 단어는 to이다.
어법 포인트: 사역동사 make의 수동태: be made + to부정사
근거: 필기본 04쪽 27번 · 08쪽 본문 B Grammar ④(make+목적 어+동사원형)', 16, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q17.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(사역·준사역) (4점)
help+목적어+동사원형/to부정사이므로 ⑤가 옳다. ① to doing → do/to do, ② to know → know, ③ stop → to stop, ④ to cut → cut.
어법 포인트: ① to doing, ② to know(let+원형), ③ stop(get+목적어+to부정사), ④ to cut(have+원형)
근거: 필기본 03쪽 7·15번, 04쪽 31·38·41번', 17, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영작(의미상 주어) (4점)
It was foolish of you to say so. (8단어) 사람의 성격을 나 타내는 foolish 뒤에는 of+목적격을 쓰므로 네 번째 단어는 of이다.
어법 포인트: 성격 형용사 + of + 목적격 + to부정사
근거: 필기본 05쪽 의미상 주어 예문 ⑤', 18, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(목적격 관계대명사) (3점)
선행사 The artist는 사람이므로 which가 아니라 who(m) 나 that을 쓴다. ④는 목적격 관계대명사가 생략된 문장이다.
어법 포인트: ① which → who/whom/that (사람 선행사)
근거: 필기본 14쪽 5·14번, 15쪽 25번, 16쪽 35·38번', 19, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(목적격 관계대명사) (4점)
옳은 문장은 2·3·5·8번째 4개이다. 1번째 him, 4번째 it, 7번 째 him은 관계대명사가 목적어를 대신하므로 지워야 하고, 6 번째는 전치사 about 뒤에 that을 쓸 수 없으므로 whom으 로 고친다.
어법 포인트: 목적어 중복(him, it, him), 전치사+that 불가 (about that → about whom)
근거: 필기본 14쪽 표 예문·e.g., 15쪽 21번, 16쪽 32번', 20, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(관계대명사 that) (3점)
<보기>의 that은 선행사 All을 수식하는 관계대명사이다. ③ 도 선행사 the girl and the dog를 수식하는 관계대명사이다. ①⑤ 명사절 접속사, ② 지시형용사, ④ 동격절 접속사.
어법 포인트: 관계대명사 that 구별
근거: 필기본 16쪽 31·32번, 06쪽 18번', 21, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(-thing+형용사) (3점)
-thing+형용사+to부정사 어순인 ⑤가 옳다. ① nothing이 solve의 목적어이므로 this math problem 삭제, ② something hot to eat, ③ 단수 취급 is, ④ something important to do.
어법 포인트: ① 목적어 중복(this math problem), ② hot something(어순), ③ are → is(단수 취급), ④ something to do important(형용사+to부정사 어순)
근거: 필기본 17쪽 기초문제 6·8번, 18쪽 7·9·11번', 22, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영작(-thing+형용사) (4점)
I have nothing important to do today. (7단어) - thing+형용사+to부정사 어순이므로 네 번째 단어는 important이다.
어법 포인트: -thing+형용사+to부정사
근거: 필기본 18쪽 16번', 23, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(전치사+관계대명사) (3점)
Ⓐ 전치사 in 바로 뒤에는 that을 쓰지 않으므로 which. Ⓑ 전치사 about 바로 뒤에는 whom. Ⓒ 선행사 The movie가 있으므로 what은 쓸 수 없고 which.
어법 포인트: 전치사+whom/which, 선행사가 있을 때 what 불 가
근거: 필기본 14쪽 6번, 15쪽 26번, 16쪽 36번', 24, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(문장 결합) (4점)
This is the pizza which we ordered an hour ago. 관계 대명사가 목적어 it을 대신하므로 it을 쓰지 않는다. 일곱 번째 단어는 ordered이다.
어법 포인트: 목적격 관계대명사절에서 목적어 it 생략
근거: 필기본 14쪽 [B] 11번', 25, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(-thing+형용사) (4점)
틀린 문장은 4개이다. special anything → anything special, something to wear warm → something warm to wear, Nothing are → Nothing is, special something → something special.
어법 포인트: special anything, to wear warm, Nothing are, special something (-thing+형용사+to부정사, 단수 취급)
근거: 필기본 17쪽 예문·기초문제 2·10번·보충문제 1번, 18쪽 6번', 26, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q27.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p27-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 문장 넣기 (3점)
주어진 문장의 this는 앞 문장의 ‘하루에 사계절을 경험하는 것’을 가리키고, 뒤 문장 It is famous ~ its dynamic weather.의 It은 Torres del Paine National Park를 가리 키므로 ⓑ에 들어가야 한다.
근거: 필기본 07쪽 본문 A ⑥⑦⑧ · 필기 ‘this(7번): 하루에 사계절 을 경험하는 것’', 27, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q28.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p27-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 알 수 있는 내용 (3점)
It is famous for its dynamic weather.와 It can be sunny, rainy, or snowy in a single day!를 합친 내용이다. ① one of the most wonderful glaciers(가장 멋진 빙 하들 중 하나), ② animal and plant species, ③ in one day, ④ Grey Glacier가 공원에 있다.
근거: 필기본 07쪽 본문 A ②⑥⑧⑨⑩⑪', 28, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q29.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p27-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문 빈칸) (4점)
Ⓐ be famous for: ~로 유명하다. Ⓑ one of the+최상급+복 수명사이므로 glaciers. Ⓒ 진주어 to walk ~를 받는 가주어 는 It이다.
어법 포인트: be famous for, one of the+최상급+복수명사, 가 주어 It
근거: 필기본 07쪽 본문 A ⑧⑪⑫ · 필기 ‘one of the+최상급+복수 명사’ · Grammar ⑫', 29, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q30.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p27-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이) (3점)
①은 icy(얼음에 뒤덮인)의 뜻풀이로 윗글에 없다(ice field의 ice와 구별). ② preserve, ③ species, ④ dynamic, ⑤ glacier는 윗글에 있다.
근거: 필기본 01쪽 icy·preserve·species·dynamic·glacier 영어 뜻풀이', 30, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q31.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p31-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영작(조건) (4점)
The park is making efforts to preserve its natural beauty. (10단어) 일곱 번째 단어는 preserve이다. to preserve는 목적을 나타내는 to부정사이다.
어법 포인트: 현재진행형 is making, 목적의 to부정사 to preserve
근거: 필기본 08쪽 본문 B ① 해석·Grammar ①', 31, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q32.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p31-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 연결어 (3점)
공원의 노력을 First, ( Ⓑ ), Third 순서로 나열하고 있으므 로 Second가 알맞다.
근거: 필기본 08쪽 본문 B ②③④', 32, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q33.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p31-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문) (4점)
사역동사 make+목적어+동사원형이므로 ⓒ to take를 take 로 고쳐야 한다. ⓐ controls, ⓑ 목적의 to부정사 to enter, ⓓ leave, ⓔ use는 옳다.
어법 포인트: ⓒ to take → take(사역동사 make+목적어+동사원 형)
근거: 필기본 08쪽 본문 B Grammar ①④', 33, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q34.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p34-35.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 지칭(한글 선택지) (3점)
ⓔ which는 선행사 rhinos를 수식하는 주격 관계대명사이다. 바오밥나무 아래에서 자고 있는 것은 코뿔소들이다.
근거: 필기본 08쪽 본문 B ⑧⑨⑩⑪ · 필기 ‘them: Big Five’ · Grammar ⑪', 34, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q35.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p34-35.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 알 수 있는 내용 (3점)
They are lions, ~ buffaloes.와 You can see them all in Kruger National Park in South Africa.를 합친 내용이다.
② South Africa, ③ tigers가 아니라 elephants, ④ Many wild animals, ⑤ 바오밥나무 아래에서 자고 있는 것은 코뿔 소(rhinos)이다.
근거: 필기본 08쪽 본문 B ⑧~⑪', 35, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q36.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p36-38.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 유추할 수 없는 것 (3점)
자원봉사자들은 불법 사냥의 징후를 감시하고 덫을 치우는 일 을 한다는 내용만 있고, 윗글에는 드론이 나오지 않는다(본문 C ③에서도 드론을 쓰는 주체는 자원봉사자가 아니다). ①~④ 는 윗글에서 알 수 있다.
근거: 필기본 09쪽 본문 C ③~⑦', 36, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q37.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p36-38.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(문맥) (3점)
자원봉사자들은 불법 사냥을 감시하는 사람들이므로 동물 덫 을 ‘놓는’ 것이 아니라 ‘제거한다’. ⓓ set → remove.
근거: 필기본 09쪽 본문 C ⑤ · Comprehension Check Q6', 37, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q38.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p36-38.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 주제 (3점)
공원의 자원봉사 프로그램에서 자원봉사자들이 하는 일과 국 립 공원이 지구의 과거·현재·미래를 보존한다는 가치를 다룬 글이다.
근거: 필기본 09쪽 중심 내용 ‘국립 공원의 야생 동물 보호 활동과 보존의 가치’', 38, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q39.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p39-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영작(조건) (4점)
However, the Big Five are in danger because of too much hunting. (12단어) 뒤에 명사구 too much hunting 이 오므로 because of를 쓰고, 아홉 번째 단어는 of이다.
어법 포인트: because of + 명사(구)
근거: 필기본 09쪽 본문 C ① 해석·Grammar ①', 39, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q40.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p39-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(지문) (4점)
‘Big Five를 보호하기 위해’라는 목적을 나타내야 하므로 ⓐ Protect를 To protect로 고쳐야 한다. ⓑ 현재완료(계속), ⓓ 수동태, ⓔ 목적의 to부정사는 옳다.
어법 포인트: ⓐ Protect → To protect(목적의 to부정사)
근거: 필기본 09쪽 본문 C Grammar ②③', 40, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q41.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p41-43.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 문장 넣기 (3점)
Angus가 양말 기계 앞에 앉은 뒤 작은 양말 한 켤레가 튀어나 오고, 이어서 그 양말을 묘사하는 Warm and soft winter socks with red stripes!가 와야 하므로 ⓓ가 알맞다.
근거: 필기본 19쪽 본문 A ⑪⑫⑬', 41, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q42.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p41-43.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 불일치 (3점)
But he didn’t realize that he used up all the wool in the machine.이므로 Angus는 털실을 다 써 버린 것을 알지 못했다.
근거: 필기본 19쪽 본문 A ⑦⑨⑬⑭⑮ · Comprehension Check Q3(F)', 42, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q43.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p41-43.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(영영풀이) (3점)
②는 return(돌려주다)의 뜻풀이로 윗글에 없다. ① sparrow, ③ stripe, ④ use up, ⑤ shake는 윗글에 있다.
근거: 필기본 12~13쪽 sparrow·return·stripe·use up·shake 영 어 뜻풀이', 43, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q44.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p44-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법·의미(지문) (4점)
ⓔ department store(백화점)가 옳다. ⓐ 함께 노는 ‘친한 친 구’이므로 friend, ⓑ 주머니 ‘안으로’ 날아들었으므로 into, ⓒ 선행사 a small store가 있으므로 that, ⓓ 마을 사람들은 백화점에서 양말을 ‘사기’ 시작했으므로 buying.
어법 포인트: ⓒ what → that(선행사 a small store가 있으면 what 불가), ⓐ enemy → friend, ⓑ out of → into, ⓓ selling → buying(문맥)
근거: 필기본 19쪽 본문 A ②③④⑥ · Grammar ④', 44, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q45.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p44-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영작(조건) (4점)
The store used to have many customers, but recently it lost a lot of them. (15단어) 열한 번째 단어는 lost이다. used to+동사원형: 예전에 ~했다.
어법 포인트: used to+동사원형
근거: 필기본 19쪽 본문 A ⑤ 해석 · 필기 ‘used to+동사원형’', 45, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q46.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p46-47.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 답할 수 없는 질문 (3점)
Angus가 만든 양말의 개수는 나와 있지 않다. ① something warm ② the department store ③ the socks that the sparrows are wearing ⑤ No, they couldn’t.
근거: 필기본 20쪽 본문 B ①③④⑤', 46, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q47.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p46-47.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 문장 넣기 (3점)
참새들이 신고 있던 something warm이 무엇인지(빨간 줄 무늬가 있는 귀여운 양말) 구체적으로 밝히는 문장이므로 ⓐ 에 들어가야 한다. 그다음에 사람들이 the socks를 보고 백화 점으로 간다.
근거: 필기본 20쪽 본문 B ①②③', 47, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q48.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p48-49.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문) (4점)
누군가가 털실을 ‘가져간’ 것이므로 능동태 took을 써야 한다. ⓐ -thing+형용사, ⓑ happened, ⓓ 부사 honestly, ⓔ make socks for ~(3형식)는 옳다.
어법 포인트: ⓒ was taken → took(능동/수동)
근거: 필기본 20쪽 본문 B ⑦⑧ · Grammar ①⑦ · 필기 ‘make socks for sparrows: 3형식’', 48, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q49.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p48-49.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 유추할 수 없는 것 (3점)
Angus honestly said ~이므로 Angus는 아버지에게 정직하 게 말했다. ③은 유추할 수 없다.
근거: 필기본 20쪽 본문 B ⑦⑧⑨', 49, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q50.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p50-52.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 일치 (3점)
The sparrows flew into the sock store and piled their socks on the table.과 일치한다. ① 참새들을 모은 것은 Bruce, ② 곤경에 빠진 것은 Angus, ③ 다른 참새들은 양말 을 좋아했다, ⑤ 양말 가게로 날아들었다.
근거: 필기본 21쪽 본문 C ①②③⑤', 50, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q51.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p50-52.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(문맥) (3점)
다른 참새들이 “Do we have to? We like these socks”라고 답하고 결국 양말을 가게에 가져다 놓으므로, Bruce는 양말 을 ‘돌려줘야’ 한다고 말한 것이다. ⓑ keep → return.
근거: 필기본 21쪽 본문 C ①②⑤ · Vocabulary return(동) 돌려 주다', 51, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q52.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p50-52.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 답할 수 없는 질문 (3점)
양말을 신은 참새의 수는 나와 있지 않다. ① Bruce ② We like these socks. ③ because of the sparrows’ socks ④ on the table in the sock store.
근거: 필기본 21쪽 본문 C ①~⑤', 52, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q53.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p53-54.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(지문 ⓐ~ⓖ) (4점)
옳은 것은 ⓐ rushed, ⓑ are, ⓒ that(목적격 관계대명사), ⓕ with이고, 이 셋만으로 된 것은 ①이다. ⓓ crowded(be crowded with), ⓔ wearing(현재진행형), ⓖ are(dogs and cats는 복수)로 고쳐야 한다.
어법 포인트: ⓓ crowding → crowded(be crowded with), ⓔ wear → wearing, ⓖ is → are
근거: 필기본 21쪽 본문 C Grammar ⑦ · 필기 ‘be crowded with’', 53, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q54.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p53-54.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영작(조건) (4점)
your kind heart has come back to us as a great gift (12단어) 여섯 번째 단어는 back이다. has come: 현재완료.
어법 포인트: 현재완료 has come
근거: 필기본 21쪽 본문 C ⑨ 해석·Grammar ⑨', 54, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q55.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p55-56.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 답할 수 없는 질문 (3점)
방문객이 많다는 말은 있지만 한 해 방문객 수는 나와 있지 않다. ① mudflats, sand dunes, beautiful islands ② food and places to live ③ about 15,000 years ago ⑤ for conservation purposes.
근거: 필기본 10쪽 More Reading 1', 55, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q56.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p55-56.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 연결어 (3점)
공원이 많은 방문객을 끌어들이지만, 보호·관리 목적으로 방 문을 막을 수도 있다는 대조의 흐름이므로 But이 알맞다.
근거: 필기본 10쪽 More Reading 1 · T/F 3번(보호·관리 목적으로 문을 닫을 수 있다)', 56, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q57.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p57-58.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 문장 넣기 (3점)
주어진 문장의 It은 the idea of evolution(진화론)을 가리키 므로 His studies helped him create the idea of evolution. 바로 뒤인 ⓓ에 들어가야 한다.
근거: 필기본 11쪽 More Reading 2 · 2번 정답 ‘④ — the idea of evolution’', 57, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q58.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p57-58.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 알 수 있는 내용 (3점)
It has special plants and animals that you can’t find anywhere else.와 같은 내용이다. ① Ecuador’s first national park ② 큰 섬 13개, 작은 섬 17개 ③ 1830s ④ colorful birds.
근거: 필기본 11쪽 More Reading 2', 58, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q59.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p59-60.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 문장 넣기 (3점)
진정한 친구가 하는 일을 A부터 L까지 알파벳 순서로 나열한 글이다. C로 시작하는 주어진 문장은 B(Believes)와 D(Doesn’t) 사이인 ⓐ에 들어가야 한다.
근거: 필기본 22쪽 More Reading 1 The ABCs of Friendship', 59, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q60.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p59-60.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 불일치 (3점)
진정한 친구는 너를 포기하지 않는다(Doesn’t give up on you).
근거: 필기본 22쪽 More Reading 1 B·D·F·I·K', 60, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q61.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p61-62.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문) (4점)
사자가 사냥꾼의 그물에 ‘잡힌’ 것이므로 수동태 was caught 를 써야 한다. ⓐ let+목적어+동사원형, ⓒ struggle to+동사 원형, ⓓ 목적의 to부정사, ⓔ 전치사 for 뒤 동명사는 옳다.
어법 포인트: ⓑ caught → was caught(수동태)
근거: 필기본 23쪽 More Reading 2 · 필기 ‘let me go’', 61, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q62.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r11/p61-62.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 일치 (3점)
사자는 쥐에게 Thank you for saving me.라고 말했다. ① 사자가 쥐를 잡았다, ② 쥐가 해칠 뜻이 없다고 생각해서 놓아 주었다, ③ 사자의 소리를 듣고 달려간 것은 쥐, ④ 쥐는 칼이 아니라 날카로운 이빨로 밧줄을 끊었다.
근거: 필기본 23쪽 More Reading 2', 62, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q63.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(종합) (4점)
-thing으로 끝나는 대명사는 형용사가 뒤에서 수식하므로 interesting nothing을 nothing interesting으로 고쳐야 한다.
어법 포인트: ④ interesting nothing → nothing interesting(- thing+형용사)
근거: 필기본 06쪽 14·30번, 18쪽 14·19번, 17쪽 기초문제 4번', 63, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q64.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(종합) (4점)
①은 nothing+형용사 어순으로 옳다. ② him 삭제, ③ somewhere new, ④ I have something interesting to share with you, ⑤ 선행사 sand hill이 있으므로 what이 아니라 that.
어법 포인트: ② him 삭제(목적어 중복), ③ new somewhere → somewhere new, ④ interesting something → something interesting(어순), ⑤ what → that(선행사 있음)
근거: 필기본 18쪽 15번, 16쪽 34번, 18쪽 3·5번, 10쪽 필기(추가 영 작)', 64, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q65.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(종합) (4점)
틀린 문장은 3개이다. anything what → anything (that) (선행사가 있으면 what 불가), strange nothing → nothing strange, get my brother fix → get my brother to fix.
어법 포인트: what → that(또는 생략), strange nothing → nothing strange, fix → to fix(get+목적어+to부정사)
근거: 필기본 03쪽 예문, 05쪽 예문, 16쪽 33번, 17쪽 보충문제 2 번, 18쪽 20번, 04쪽 32·40번', 65, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q66.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(종합) (4점)
옳은 문장은 1·4·5·6·8번째 5개이다. natural은 성격 형용사 가 아니므로 for teenagers, The cake which my mom made it에서 it 삭제, helped us install/to install.
어법 포인트: of teenagers → for teenagers, it 삭제, installing → install/to install
근거: 필기본 06쪽 17·20번, 15쪽 19·22번, 04쪽 37·39번, 18쪽 7번', 66, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q67.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(종합 ⓐ~ⓖ) (4점)
옳은 것은 ⓐ, ⓒ, ⓔ이다. ⓑ made me cry, ⓓ It is dangerous to play, ⓕ with whom, ⓖ something important.
어법 포인트: ⓑ to cry → cry, ⓓ play → to play, ⓕ who → whom(전치사 뒤), ⓖ important something → something important
근거: 필기본 03쪽 14번, 04쪽 28번, 06쪽 32·37번, 14쪽 10번, 16 쪽 34번, 18쪽 13번', 67, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q68.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(종합 ⓐ~ⓖ) (4점)
옳은 것은 ⓐ, ⓓ, ⓔ이다. ⓑ had the waiter bring, ⓒ It was difficult to find, ⓕ in which I was born(뒤의 in 삭 제), ⓖ anything important.
어법 포인트: ⓑ to bring → bring(have+목적어+동사원형), ⓒ find → to find, ⓕ 뒤의 in 삭제, ⓖ importantly → important
근거: 필기본 04쪽 30번, 03쪽 10번, 05쪽 9번, 06쪽 29번, 14쪽 4 번, 16쪽 34번, 18쪽 4번', 68, 4),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q69.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(종합) (3점)
전치사 in 바로 뒤에는 that을 쓸 수 없으므로 in which로 고 친다.
어법 포인트: ② in that → in which(전치사 뒤 that 불가)
근거: 필기본 04쪽 35번, 16쪽 28·29번, 14쪽 2번, 18쪽 18번', 69, 3),
(@naesin_r11, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r11/q70.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(종합) (3점)
①은 전치사 to가 관계절 끝에 있어 옳다. ② made us cancel, ③ not to waste, ④ it 삭제, ⑤ There is something.
어법 포인트: ② to cancel → cancel, ③ not wasting → not to waste, ④ it 삭제, ⑤ are → is(-thing 단수 취급)
근거: 필기본 15쪽 18·20번, 03쪽 17번, 06쪽 22번, 18쪽 12번', 70, 3);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM2-2602-R12', 'NAESIN', '압구정중 2학년 2학기 기말 12회 · Lesson 7·8 종합 예상문제 2회', 'Lesson 7·8 학습지 필기본 1~23쪽 · 70문항 · 35분 · 236점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_r12 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM2-2602-R12');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 발음부호 (3점)
hunting의 발음부호는 [ˈhʌntɪŋ]으로 첫째 음절에 주강세가 있다. ②는 강세 위치를 둘째 음절로 옮겨 틀리다. 나머지는 필기본 표기와 같다.
근거: 필기본 01쪽 Lesson 7 단어 · hunting [ˈhʌntɪŋ], glacier, leopard, monitor, species', 1, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 발음부호 (3점)
trouble의 발음부호는 [ˈtrʌbəl]이다. ④는 철자 ou를 [aʊ]로 읽어 틀리다. honestly의 h, knock의 k는 소리 나지 않으므 로 ①②는 바르다.
근거: 필기본 12~13쪽 Lesson 8 Word List · trouble [ˈtrʌbəl], honestly, knock, wool, villager', 2, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이) (3점)
‘어떤 지역이나 나라에 대해 알기 위해 그곳을 여행하거나 돌 아다니다’는 explore(탐험하다)의 영어 뜻풀이이다.
근거: 필기본 01쪽 explore 영어 뜻풀이', 3, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(문장 빈칸) (3점)
지구 온난화로 북극에서 빠르게 녹고 있는 것은 거대한 빙하 (glacier)이다.
근거: 필기본 02쪽 단어 연습 7번', 4, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(문장 빈칸) (3점)
위성 추적기를 이용해 야생 동물의 이동을 ‘관찰하기로’ 결정 한 것이므로 monitor(감시하다, 관찰하다)가 알맞다.
근거: 필기본 02쪽 단어 연습 6번 · 01쪽 monitor', 5, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(빈칸 불가) (3점)
순서대로 illegal, icy, dynamic, species가 들어간다. trap(덫)은 어느 빈칸에도 들어가지 않는다.
근거: 필기본 02쪽 단어 연습 4·10·16·9번', 6, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(문장 빈칸) (3점)
버스를 타기 위해 ‘서둘러야’ 했다는 뜻이므로 rush(급히 움직 이다, 서두르다)가 알맞다.
근거: 필기본 12쪽 rush 예문', 7, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(문장 빈칸) (3점)
교장실에 들어가기 전에 문을 ‘두드리는’ 것을 잊지 말라는 뜻 이므로 knock (on the door)가 알맞다.
근거: 필기본 13쪽 Word List 연습 13번 · 12쪽 knock', 8, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(영영풀이) (3점)
‘무언가를 완전히 끝내거나 소비하다’는 use up(다 써 버리 다)의 영어 뜻풀이이다.
근거: 필기본 13쪽 use up 영어 뜻풀이', 9, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(빈칸 불가) (3점)
순서대로 customer, pair, sparrow, department store가 들어간다. stripe(줄무늬)는 어느 빈칸에도 들어가지 않는다.
근거: 필기본 13쪽 Word List 연습 10·14·6·11번', 10, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(사역·준사역) (4점)
let/have/make+목적어+동사원형이므로 go, write, cancel 이 들어가고, get+목적어+to부정사이므로 to fix가 들어간다. to wait는 어느 빈칸에도 들어갈 수 없다.
어법 포인트: 사역동사(let/have/make)+목적어+동사원형 · get+목적어+to부정사
근거: 필기본 03쪽 2·12·17번, 04쪽 32번', 11, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(사역·준사역) (4점)
Ⓐ make+목적어+동사원형 → wait, Ⓑ get+목적어+to부정 사 → to clean, Ⓒ let+목적어+동사원형 → know.
어법 포인트: make/let+목적어+동사원형, get+목적어+to부정사
근거: 필기본 04쪽 22·42번, 03쪽 7번', 12, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(준사역) (4점)
get+목적어+to부정사이므로 stop을 to stop으로 고쳐야 한다. ①은 help+목적어+to부정사, ②④는 목적어와 보어가 수 동 관계인 get+목적어+과거분사, ⑤는 make+목적어+동사원 형으로 옳다.
어법 포인트: ③ get my dog stop → to stop (get+목적어+to부 정사)
근거: 필기본 04쪽 38번(정답 to stop), 31·39·43·27번', 13, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(가주어·의미상 주어) (4점)
틀린 문장은 2개이다. It was careless for him → of him(성격 형용사 careless는 of+목적격), It is easy learn → to learn(진주어 to부정사). 나머지는 옳다.
어법 포인트: 의미상 주어 of(성격 형용사) · 가주어 It+진주어 to 부정사
근거: 필기본 06쪽 17·27·22번, 05쪽 2·12·7번, 06쪽 37·32번', 14, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(의미상 주어) (3점)
의미상 주어가 for a human being이므로 성격·특징을 나타 내지 않는 형용사(impossible, hard, dangerous, difficult)가 온다. kind는 사람의 성격을 나타내므로 of+목 적격과 쓰여 들어갈 수 없다.
어법 포인트: 의미상 주어 for/of를 결정하는 형용사
근거: 필기본 06쪽 30번, 05쪽 의미상 주어 설명(nice, kind, foolish … → of+목적격)', 15, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(가주어·의미상 주어) (4점)
④는 It ~ that절(that 뒤에 주어+동사)로 옳다. ① submit → to submit, ② for him → of him(foolish는 성격 형용 사), ③ of teenagers → for teenagers, ⑤ of a doctor → for a doctor(important는 성격 형용사가 아니다).
어법 포인트: ① 진주어 to부정사 누락 · ② 성격 형용사 → of · ③⑤ 성격 형용사가 아니므로 for+목적격
근거: 필기본 06쪽 24·29·19·20·25번', 16, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q17.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(사역·준사역) (3점)
목적격 보어가 동사원형 carry이므로 make, have, let, help는 쓸 수 있다. get은 목적격 보어로 to부정사를 쓰므로 (got her younger brother to carry) 들어갈 수 없다.
어법 포인트: get+목적어+to부정사(동사원형 불가)
근거: 필기본 03쪽 18번 · 04쪽 준사역동사 help, get 설명', 17, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(사역·준사역·가주어) (4점)
옳은 것은 ⓑ(have+목적어+동사원형), ⓒ(get+목적어+to부 정사), ⓕ(It ~ that절)이다. ⓐ watching → watch, ⓓ carrying → carry/to carry, ⓔ wear → to wear, ⓖ to cry → cry.
어법 포인트: ⓐⓖ 사역동사 let/make+목적어+동사원형 · ⓓ help+목적어+동사원형/to부정사 · ⓔ 진주어 to부정사
근거: 필기본 03쪽 4·6·14번, 04쪽 44·45번, 05쪽 3번, 06쪽 33번', 18, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(목적격 관계대명사) (4점)
전치사 바로 뒤에는 that을 쓸 수 없으므로 with that을 with whom으로 고쳐야 한다.
어법 포인트: ① 전치사(with)+that 불가 → with whom
근거: 필기본 15쪽 22번(정답 ③ that → whom), 14쪽 2·12·7·8번', 19, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(관계대명사 that) (3점)
<보기>의 that은 선행사 the cat을 수식하며 saw의 목적어 역할을 하는 목적격 관계대명사이다. ④도 the dress를 수식 하며 wearing의 목적어 역할을 하는 목적격 관계대명사이다.
① 명사절 접속사, ② 진주어 that절의 접속사, ③ 지시형용 사, ⑤ 동격절 접속사.
어법 포인트: 목적격 관계대명사 that 구별
근거: 필기본 14쪽 표 예문, 15쪽 23번 ④, 16쪽 31번, 06쪽 18번', 20, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(목적격 관계대명사) (4점)
옳은 문장은 2·3·5·6·7번째 5개이다. 1번째는 him, 4번째는 it을 삭제해야 하고(목적어 중복), 8번째는 전치사 뒤이므로 who를 whom으로 고쳐야 한다.
어법 포인트: 목적격 관계대명사절의 목적어 중복 금지 · 전치사 +whom
근거: 필기본 15쪽 17·16·22·20·26번, 16쪽 37·29·34번', 21, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(-thing+형용사) (3점)
-thing+형용사+to부정사 어순의 ②가 옳다. ① something special, ③ something interesting, ④ There is(단수 취 급), ⑤ nothing useful.
어법 포인트: -thing+형용사 어순 · -thing 단수 취급
근거: 필기본 18쪽 16·12·17번, 17쪽 기초 2·7번', 22, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영작(-thing+형용사) (4점)
Will you cook anything special for the party? 다섯 번째 단어는 special이다(-thing+형용사).
어법 포인트: -thing+형용사(anything special)
근거: 필기본 17쪽 -thing+형용사 예문', 23, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(관계대명사) (3점)
순서대로 who(whom/that), whom, which, which/that, that/which가 들어간다. 선행사가 있으므로 선행사를 포함하 는 what은 어느 빈칸에도 쓸 수 없다.
어법 포인트: 전치사+whom/which · 선행사가 있을 때 what 불 가
근거: 필기본 14쪽 4·6번, 15쪽 25·24번, 16쪽 33번', 24, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(-thing+형용사) (3점)
Ⓐ -thing은 단수 취급 → is, Ⓑ anything을 꾸미는 형용사 → important, Ⓒ -thing+형용사 → something cold.
어법 포인트: -thing 단수 취급 · -thing+형용사 어순
근거: 필기본 17쪽 기초 8·6번, 18쪽 4번', 25, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영작(관계대명사) (4점)
Do you know the man to whom Kelly is talking? 전치 사 to 뒤에는 that을 쓸 수 없고 whom을 쓴다. 일곱 번째 단 어는 whom이다.
어법 포인트: 전치사+whom(that 불가)
근거: 필기본 14쪽 12번, 15쪽 전치사+관계대명사 필기', 26, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q27.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p27-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 일치 (3점)
하루에 맑거나, 비가 오거나, 눈이 올 수 있다고 했다. ① 남아 메리카와 아프리카의 두 공원, ② 가장 멋진 빙하들 중 하나,
③ 커다란 빙원도 볼 수 있다, ⑤ 사람들이 자연의 아름다움을 즐기도록 해 준다.
근거: 필기본 07쪽 본문 A ②③④⑨⑩⑪', 27, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q28.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p27-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 파악(답할 수 없는 질문) (3점)
칠레에 있는 빙하의 수는 나오지 않는다. ② many animal and plant species, ③ mountains, lakes, and a big ice field, ④ Grey Glacier, ⑤ in Chile.
근거: 필기본 07쪽 Comprehension Check Q1, 본문 A ②⑦⑩⑪', 28, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q29.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p27-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(영영풀이) (3점)
②는 leopard(표범)의 뜻풀이로 본문 A에 없다. ① dynamic, ③ preserve(preserving), ④ species, ⑤ glacier는 윗글에 있다.
근거: 필기본 01쪽 영어 뜻풀이(dynamic, leopard, preserve, species, glacier)', 29, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q30.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p27-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문) (4점)
ⓑ the perfect place for this는 옳다. ⓐ can 뒤 동사원형 experience, ⓒ be famous for, ⓓ one of the+최상급+복 수명사 glaciers, ⓔ 진주어 to walk로 고쳐야 한다.
어법 포인트: ⓐ 조동사+동사원형 · ⓒ be famous for · ⓓ one of the+최상급+복수명사 · ⓔ 진주어 to부정사
근거: 필기본 07쪽 본문 A ⑥⑦⑧⑪⑫, 필기(one of the+최상급 +복수명사, ⑫ 가주어)', 30, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q31.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p27-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 연결어 (3점)
앞 문장에서 공원에서 산, 호수, 빙원을 볼 수 있다고 하고 그 예로 Grey Glacier를 소개하므로 For example이 알맞다.
근거: 필기본 07쪽 본문 A ⑪', 31, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q32.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p32-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 불일치 (3점)
운이 좋으면 바오밥나무 아래에서 자고 있는 것을 볼 수 있는 동물은 사자가 아니라 코뿔소(rhinos)이다.
근거: 필기본 08쪽 본문 B ③⑤⑨⑩⑪', 32, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q33.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p32-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문) (4점)
사역동사 make+목적어+동사원형이므로 ⓒ to take를 take 로 고쳐야 한다. ⓐ 단수 주어 it의 현재 동사, ⓑ 목적의 to부 정사, ⓓ 현재완료 의문문의 과거분사, ⓔ 선행사 rhinos를 수식하는 주격 관계대명사로 옳다.
어법 포인트: ⓒ makes visitors to take → take (사역동사 make+목적어+동사원형)
근거: 필기본 08쪽 본문 B Grammar ①④⑦⑪', 33, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q34.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p32-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영작(본문) (4점)
The park is making efforts to preserve its natural beauty. 여섯 번째 단어는 목적을 나타내는 to부정사의 to이다.
어법 포인트: 목적을 나타내는 to부정사(to preserve)
근거: 필기본 08쪽 본문 B ① 해석, Grammar ① 목적의 to부정사', 34, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q35.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p32-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 파악(답할 수 없는 질문) (3점)
크루거 국립 공원에 사는 표범의 수는 나오지 않는다. ① lions, leopards, elephants, rhinos, and buffaloes, ② in South Africa, ③ plastic, ⑤ rhinos.
근거: 필기본 08쪽 Comprehension Check Q4, 추가 질문, 본문 B ⑤⑧⑨⑪', 35, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q36.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p32-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 추론 (3점)
Big Five(버팔로 포함)를 크루거 국립 공원에서 모두 볼 수 있다고 했으므로 ①은 유추할 수 없다. ③ 입장하려면 예약해야 한다, ④ 일부 산책로만 개방된다, ⑤ 친환경 제품만 사용한다.
근거: 필기본 08쪽 본문 B ①②③⑤⑧⑨', 36, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q37.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p37-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 추론 (3점)
Big Five는 과도한 사냥 때문에 위험에 처해 있다고 했으므로 ⑤는 유추할 수 없다. ②는 자원봉사자들이 불법 사냥의 징후 를 감시하고 덫을 제거한다는 내용에서 알 수 있다.
근거: 필기본 09쪽 본문 C ①②⑤⑦', 37, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q38.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p37-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 지칭·의미 (3점)
ⓓ traps는 동물을 잡기 위한 ‘덫’이다(a piece of equipment for catching animals). ⓑ runs는 ‘운영하다’, ⓔ them은 앞 문장의 National parks를 가리킨다.
근거: 필기본 09쪽 본문 C, Vocabulary(run 운영하다, monitor 감시하다, trap 덫), 01쪽 trap 뜻풀이', 38, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q39.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p37-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문) (4점)
Ⓐ 뒤에 명사구 too much hunting이 오므로 because of,
Ⓑ 드론과 헬리콥터가 ‘사용되는’ 것이므로 수동태 are used,
Ⓒ will 뒤에 조동사 can을 쓸 수 없으므로 be able to.
어법 포인트: because of+명사구 · 수동태 are used · will be able to
근거: 필기본 09쪽 본문 C Grammar ①③⑦', 39, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q40.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p37-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 불일치 (3점)
동물 덫을 제거하는 것은 헬리콥터가 아니라 자원봉사자들 (Volunteers)이다. 헬리콥터는 사냥 활동을 감시하는 데 쓰인다.
근거: 필기본 09쪽 본문 C ②③④⑤⑥', 40, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q41.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p41-43.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 불일치 (3점)
함께 놀 때 Bruce가 날아든 곳은 Angus의 아버지가 아니라 Angus의 주머니이다.
근거: 필기본 19쪽 본문 A ①③④⑥⑦', 41, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q42.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p41-43.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문) (4점)
나무가 눈으로 ‘덮여 있었다’는 수동의 의미이므로 ⓒ covered를 was covered로 고쳐야 한다. ⓐ 주격 관계대명 사, ⓑ used to+동사원형, ⓓ 지각동사 see+목적어+현재분 사, ⓔ one by one(하나씩)은 옳다.
어법 포인트: ⓒ The tree covered → was covered with snow (be covered with 수동태)
근거: 필기본 19쪽 본문 A ④⑤⑧⑨, Grammar ④⑨, 12쪽 be covered with', 42, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q43.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p41-43.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 파악(답할 수 없는 질문) (3점)
나무 위에 있던 참새의 수는 나오지 않는다. ① a small store that made socks, ② Bruce, ③ into Angus’s pocket, ④ Bruce가 발을 하나씩 들어 올리는 것.
근거: 필기본 19쪽 Comprehension Check Q1, 추가 질문, 본문 A ③⑦⑨', 43, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q44.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p44-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 문장 넣기 (3점)
<보기>는 기계에서 튀어나온 양말을 묘사하는 문장이므로 a tiny pair of socks popped out 바로 뒤, ⓓ 자리에 와야 한다.
근거: 필기본 19쪽 본문 A ⑫⑬', 44, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q45.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p44-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(표현) (3점)
‘점점 더 많은’은 more and more이다. one by one(하나 씩), little by little(조금씩), one more thing(한 가지 더), at the same time(동시에)은 뜻이 다르다.
근거: 필기본 19쪽 본문 A ⑭⑨, 22쪽 속담 필기, 08쪽 본문 B ⑤, 07쪽 본문 A ③', 45, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q46.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p46-50.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 일치 (3점)
참새들은 빨간 줄무늬가 있는 귀여운 양말을 신고 있었다. ① 어디에서도 찾을 수 없었다, ② 양말 가게, ④ 아버지에게 사 과했다, ⑤ Angus의 주머니.
근거: 필기본 20쪽 본문 B ②⑤⑥⑧⑨', 46, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q47.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p46-50.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(지문) (4점)
ⓐ As soon as(~하자마자)는 옳다. ⓑ 선행사 the socks가 있으므로 that, ⓒ something strange(-thing+형용사), ⓓ 털실을 모두 ‘가져갔다(took)’, ⓔ 참새들이 추위에 ‘떨고 있었 다(were shaking)’로 고쳐야 한다.
어법 포인트: ⓑ 선행사 뒤 what 불가 → that · ⓒ -thing+형용 사 어순 · ⓔ 수동태 오용 were shaken → were shaking
근거: 필기본 20쪽 본문 B ③④⑦⑧, Grammar ①⑦④', 47, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q48.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p46-50.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영작(본문) (4점)
The next day, the sparrows were wearing something warm. 여덟 번째 단어는 something이다(-thing+형용사).
어법 포인트: -thing+형용사(something warm)
근거: 필기본 20쪽 본문 B ① 해석, Grammar ①', 48, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q49.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p46-50.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 추론 (3점)
Bruce는 Angus의 주머니 속에서 조용히 듣고 있었으므로 ⑤ 는 유추할 수 없다. ② 사람들은 어디에서도 같은 양말을 찾을 수 없었다, ③④ Angus가 정직하게 “Sorry, I made socks for sparrows.”라고 말했다.
근거: 필기본 20쪽 본문 B ③④⑤⑧⑨', 49, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q50.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p46-50.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이) (3점)
①은 knock의 뜻풀이로 윗글에 없다. ② as soon as, ③ silently, ④ stripe(stripes), ⑤ department store는 윗글 에 있다.
근거: 필기본 12~13쪽 영어 뜻풀이(knock, as soon as, silently, stripe, department store)', 50, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q51.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p51-54.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(표현) (3점)
‘드디어’는 Finally이다. 원하던 양말을 마침내 찾았다는 뜻이다. Suddenly(갑자기), Lately(최근에), Instead(대신에), At first(처음에는)는 뜻이 다르다.
근거: 필기본 21쪽 본문 C ⑦ 해석(누군가 “드디어! 이것들이 내가 원하는 양말이야!”라고 말했다)', 51, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q52.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p51-54.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 파악(답할 수 없는 질문) (3점)
가게에 온 손님의 수는 나오지 않는다. ① 참새들의 양말 때문 에, ② on the table, ④ “Finally! These are the socks that I want!”, ⑤ warm socks with red stripes.
근거: 필기본 21쪽 Comprehension Check Q6·Q7, 추가 질문, 본 문 C ⑦', 52, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q53.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p51-54.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문) (4점)
목적격 관계대명사 that이 want의 목적어 역할을 하므로 관 계대명사절에 목적어 them을 다시 쓰지 않는다. ⓐ need+to 부정사, ⓑ because of+명사구, ⓓ be crowded with, ⓔ 현재완료 has come은 옳다.
어법 포인트: ⓒ that I want them → that I want (목적격 관계 대명사절 목적어 중복 금지)
근거: 필기본 21쪽 본문 C Grammar ⑦⑨, 14쪽 목적어 중복 금지', 53, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q54.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p51-54.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 불일치 (3점)
참새들이 양말을 쌓아 둔 뒤 사람들이 가게로 서둘러 들어왔 다(people rushed into the store). ② 다른 참새들은 처음 에 “Do we have to? We like these socks.”라고 했다.
근거: 필기본 21쪽 본문 C ①②⑤⑥⑩', 54, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q55.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p55-56.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 파악(알 수 있는 내용) (3점)
신두리 해안 사구는 독특한 식물과 동물이 있는 독특한 생태 계를 형성해 왔다. ① 해안 공원, ② 바다의 모든 생물, ③ 약 15,000년 전, ⑤ 보호·관리 목적.
근거: 필기본 10쪽 More Reading 1, T/F 3번', 55, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q56.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p55-56.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(지문) (4점)
가주어 it에 대한 진주어는 to부정사이므로 ⓐ visit을 to visit으로 고쳐야 한다. ⓑ because of+명사구, ⓒ 수동태 was formed, ⓓ 현재완료 has formed, ⓔ make sure+to 부정사는 옳다.
어법 포인트: ⓐ it is interesting visit → to visit (가주어 It+진 주어 to부정사)
근거: 필기본 10쪽 More Reading 1 필기(it is interesting to visit: 가주어–진주어, was formed, has formed)', 56, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q57.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p57-58.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 파악(알 수 있는 내용) (3점)
다윈은 1830년대에 갈라파고스를 방문해 놀라운 생물들을 관 찰했다. ① Ecuador, ② 큰 섬은 13개(작은 섬 17개), ④ 1830s, ⑤ 거대한 것은 거북(giant tortoises)이고 새는 colorful birds이다.
근거: 필기본 11쪽 More Reading 2', 57, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q58.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p57-58.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(지문) (4점)
ⓐ one of the+최상급+복수명사(treasures)는 옳다. ⓑ is made up of(수동태), ⓒ 목적격 관계대명사절의 목적어 중 복 → find, ⓓ help+목적어+동사원형/to부정사 → create(to create), ⓔ 선행사가 사람(visitors)이므로 who 로 고쳐야 한다.
어법 포인트: ⓑ be made up of 수동태 · ⓒ 관계대명사절 목적 어 중복 · ⓓ help+목적어+동사원형 · ⓔ 사람 선행사 → who
근거: 필기본 11쪽 More Reading 2 필기(one of the+최상급+복 수명사, be made up of, help+목적어+동사원형)', 58, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q59.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p59-60.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 불일치 (3점)
진정한 친구는 네가 가진 것이 아니라 있는 그대로의 너(who you are)를 사랑한다.
근거: 필기본 22쪽 More Reading 1 A·E·H·J·L', 59, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q60.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p59-60.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 파악(답할 수 없는 질문) (3점)
진정한 친구가 얼마나 자주 전화하는지는 나오지 않는다. ② 용서한다, ③ 그저 “안녕”이라고 말하려고, ④ 포기하지 않는 다, ⑤ 조건 없이.
근거: 필기본 22쪽 More Reading 1 C·D·F·G', 60, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q61.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p61-62.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 불일치 (3점)
날카로운 이빨로 그물의 밧줄을 끊은 것은 사자가 아니라 쥐 이다.
근거: 필기본 23쪽 More Reading 2, 내용 일치 문항 해설', 61, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q62.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r12/p61-62.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문) (4점)
ⓑ 사역동사 let+목적어+동사원형(go)은 옳다. ⓐ let me go, ⓒ 사자가 그물에 ‘잡혔다’ → was caught, ⓓ 목적의 to부정 사 → to find, ⓔ 전치사 for 뒤 동명사 → saving.
어법 포인트: ⓐ let+목적어+동사원형 · ⓒ 수동태 was caught · ⓓ 목적의 to부정사 · ⓔ 전치사+동명사
근거: 필기본 23쪽 More Reading 2 필기(let+목적어+동사원형), 본문 원문', 62, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q63.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(종합) (3점)
목적격 관계대명사 which가 made의 목적어이므로 it을 삭 제해야 한다. ① let+목적어+동사원형, ② 의미상 주어 for,
③ help+목적어+동사원형, ④ -thing+형용사+to부정사.
어법 포인트: ⑤ which my mom made it → it 삭제(목적어 중 복)
근거: 필기본 15쪽 19번 ③, 03쪽 13번, 06쪽 34번, 04쪽 33번, 17 쪽 예문', 63, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q64.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(종합) (3점)
⑤는 anything+형용사+to부정사로 옳다. ① to run → run,
② for her → of her, ③ in that → in which, ④ brought → bring.
어법 포인트: ① make+목적어+동사원형 · ② 성격 형용사+of ·
③ 전치사+that 불가 · ④ have+목적어+동사원형
근거: 필기본 18쪽 18번, 03쪽 11·10번, 06쪽 23번, 16쪽 28번', 64, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q65.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(종합) (4점)
틀린 문장은 4개이다. have the guide to explain → explain, helps you expanding → expand/to expand, about that → about whom, Nothing are → Nothing is.
어법 포인트: have+목적어+동사원형 · help+목적어+동사원형/to 부정사 · 전치사+whom · -thing 단수 취급
근거: 필기본 03쪽 9번, 04쪽 25·35번, 06쪽 38·31번, 16쪽 27번, 15쪽 20번 ④, 17쪽 10번, 18쪽 6번', 65, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q66.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(종합) (4점)
옳은 것은 ⓐ, ⓒ, ⓔ이다. ⓑ to cut → cut, ⓓ who → which/that, ⓕ cross → to cross, ⓖ cold anything → anything cold.
어법 포인트: have+목적어+동사원형 · 사물 선행사 which · 진주 어 to부정사 · -thing+형용사
근거: 필기본 03쪽 8·15번, 05쪽 13번, 06쪽 15번, 14쪽 3번, 18쪽 9번 ㄴ, 17쪽 보충 1번 ④', 66, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q67.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(종합) (4점)
옳은 문장은 1·2·3·7·8·9번째 6개이다. foolish for you → of you, whom I spoke to him → him 삭제, in which I was born in → 뒤의 in 삭제.
어법 포인트: 성격 형용사+of · 관계대명사절 목적어 중복 금지 · 전치사+관계대명사 뒤 전치사 중복 금지
근거: 필기본 04쪽 36·34번, 05쪽 예문 2개, 16쪽 34번 ①②, 17쪽 보충 2번 ③, 18쪽 9번 ㄷ·11번', 67, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q68.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(종합) (4점)
순서대로 let, who/whom(사람 선행사 the man, 목적격), whom(who), make가 들어간다. for는 어느 빈칸에도 들어 갈 수 없다.
어법 포인트: 사역동사+목적어+동사원형 · 목적격 관계대명사 who/whom
근거: 필기본 04쪽 26·30번, 16쪽 32번 ③, 14쪽 10번', 68, 4),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q69.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(종합) (3점)
Ⓐ let+목적어+동사원형 → leave, Ⓑ essential은 성격 형용 사가 아니므로 for students, Ⓒ -thing+형용사+to부정사 → something important.
어법 포인트: let+목적어+동사원형 · 의미상 주어 for · -thing+형 용사
근거: 필기본 04쪽 29번, 06쪽 16번, 18쪽 13번', 69, 3),
(@naesin_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r12/q70.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(종합) (4점)
help+목적어+동사원형/to부정사이므로 to doing을 do 또는 to do로 고쳐야 한다. ② 가주어–진주어, ③ 목적격 관계대명 사 which, ④ -thing+형용사, ⑤ let+목적어+동사원형.
어법 포인트: ① helped her younger brother to doing → do / to do (help+목적어+동사원형/to부정사)
근거: 필기본 04쪽 41번(→ do / to do), 06쪽 36번, 14쪽 13번, 18 쪽 7번 ④, 04쪽 21번', 70, 4);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM2-2602-R13', 'NAESIN', '압구정중 2학년 2학기 기말 13회 · Lesson 7·8 종합 예상문제 3회', 'Lesson 7·8 학습지 필기본 1~23쪽 · 70문항 · 35분 · 237점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_r13 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM2-2602-R13');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 발음부호(7과) (3점)
product의 발음부호는 [ˈprɑːdʌkt]로 첫째 음절에 강세가 있다. 나머지는 필기본 표기와 같다.
근거: 필기본 01쪽 Lesson 7 단어 · product [ˈprɑːdʌkt]', 1, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 발음부호(8과) (3점)
pop out의 발음부호는 [ˌpɑːp ˈaʊt]로 out에 주강세가 있다. 나머지는 필기본 표기와 같다.
근거: 필기본 12쪽 Lesson 8 Word List · pop out [ˌpɑːp ˈaʊt]', 2, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(7과 영영풀이) (3점)
the period of time that is happening now는 present(현 재)의 영어 뜻풀이이다.
근거: 필기본 01쪽 present 영어 뜻풀이', 3, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(7과 예문) (3점)
눈 덮인 산봉우리가 마법 같은 겨울의 아주 멋진 곳(winter wonderland)처럼 보였다는 뜻이다.
근거: 필기본 02쪽 단어 연습 3번 · wonderland(natural/winter wonderland)', 4, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(7과 예문) (3점)
make efforts: 노력하다. ‘환경을 보호하기 위해 노력하 자.’라는 뜻이다.
근거: 필기본 01쪽 effort 예문 · 관련 표현 make an effort', 5, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(7과 예문) (3점)
차례대로 book(예약하다), dynamic(역동적인), explore(탐 험하다), species(종)가 들어간다. leopard는 들어갈 곳이 없다.
근거: 필기본 01쪽 book·dynamic·explore·species 예문', 6, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(8과 영영풀이) (3점)
to cause people or things to come to the same place or to join은 bring … together(~을/를 모으다)의 영어 뜻풀 이이다.
근거: 필기본 12쪽 bring … together 영어 뜻풀이', 7, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(8과 숙어) (3점)
use up: 다 써 버리다. ‘그는 돈을 다 써 버렸다.’라는 뜻이다.
근거: 필기본 13쪽 use up 예문', 8, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(8과 예문) (3점)
가게에 들어오는 모든 손님(customer)에게 점원이 인사했다 는 뜻이다.
근거: 필기본 13쪽 Word List 연습 10번 · 12쪽 customer', 9, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(8과 예문) (3점)
차례대로 pair, sock, wool, Honestly가 들어간다. silently 는 들어갈 곳이 없다.
근거: 필기본 13쪽 Word List 연습 14·20·2·12번', 10, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(사역·준사역) (3점)
차례대로 let anyone enter, got the patient to open(get+목적어+to부정사), had the car washer wash, have the guide explain이 들어간다. have는 사역동사이므 로 to explain은 들어갈 곳이 없다.
어법 포인트: 사역동사 let/have+동사원형 / 준사역 get+목적어 +to부정사
근거: 필기본 04쪽 21·34·23·25번', 11, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(준사역 get) (4점)
get + 목적어 + to부정사(목적어가 동작을 함): to fix, to stop. 목적어와 보어가 수동 관계이면 get + 목적어 + 과거분 사: my hair cut(cut–cut–cut).
어법 포인트: get+목적어+to부정사(능동) / get+목적어+과거분사 (수동)
근거: 필기본 04쪽 32·36·38번', 12, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(사역·준사역) (4점)
help + 목적어 + 동사원형/to부정사이므로 carrying을 carry 또는 to carry로 고친다.
어법 포인트: help+목적어+동사원형/to부정사(carrying → carry/to carry)
근거: 필기본 04쪽 44번(틀린 곳 고치기) · 31·39·26번 · 03쪽 11번', 13, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(사역동사 수동태) (4점)
The students were made to rewrite their essays (by the teacher). 사역동사 make의 수동태에서는 동사원형이 to부정사로 바뀐다. 네 번째 단어는 made이다.
어법 포인트: 사역동사 make의 수동태: be made + to부정사
근거: 필기본 03쪽 Step 1 8번', 14, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(의미상 주어) (3점)
빈칸 뒤에 for us가 있으므로 for+목적격과 쓰는 형용사가 와 야 한다. wise는 사람의 성격·특징을 나타내는 형용사라 of+목적격을 쓰므로 들어갈 수 없다.
어법 포인트: to부정사의 의미상 주어 for/of(성격 형용사 wise → of)
근거: 필기본 06쪽 22번 · 05쪽 의미상 주어 설명(wise는 of+목적 격)', 15, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(의미상 주어) (3점)
careless, generous처럼 사람의 성격·특징을 나타내는 형용 사 뒤에는 of+목적격, necessary 뒤에는 for+목적격을 쓴다.
어법 포인트: to부정사의 의미상 주어 for/of
근거: 필기본 06쪽 27·29·23번', 16, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q17.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(가주어 to/that) (4점)
that 뒤에 주어+동사를 갖춘 절이 왔으므로 옳다. ① to → that ③ that → to ④ sleep → to sleep ⑤ to → that.
어법 포인트: 진주어 to부정사(+동사원형) / 진주어 that절(+주어 +동사)
근거: 필기본 06쪽 31~35번(to부정사 vs that절)', 17, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(가주어 It) (4점)
It is fun to play soccer with friends after school. 가주 어 It을 앞에 쓰고 진주어 to부정사구를 뒤로 보낸다. 다섯 번 째 단어는 play이다.
어법 포인트: 가주어 It + 진주어 to부정사
근거: 필기본 05쪽 4번 · 05쪽 문법 2 가주어–진주어 설명', 18, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영작(목적격 관계대명사) (4점)
The artist whom we visited last month is famous. 관계 절에서 목적어 her를 쓰지 않는다. 다섯 번째 단어는 visited 이다.
어법 포인트: 목적격 관계대명사 whom, 관계절 안 목적어 중복 금지
근거: 필기본 14쪽 [B] 14번', 19, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(목적격 관계대명사) (4점)
write a letter to+사람이므로 whom I wrote a letter to로 고쳐야 한다.
어법 포인트: 관계절 끝 전치사 to 누락(whom I wrote a letter to)
근거: 필기본 15쪽 19·20번', 20, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(관계대명사) (3점)
선행사 The boy가 사람이고 관계절에서 saw의 목적어이므 로 who/whom/that을 쓰거나 생략할 수 있다. 사람 선행사 에는 which를 쓰지 않는다.
어법 포인트: 사람 선행사의 목적격 관계대명사 who/whom/that(생략 가능), which 불가
근거: 필기본 14쪽 7번 · 16쪽 33번(선택지 방식)', 21, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(목적격 관계대명사) (4점)
옳은 문장: bicycle, girl who you invited, house in which we live, phone I bought, song which we listened to (5개). 틀린 문장: him 삭제, it 삭제, with that → with whom, in which I was born in → 뒤의 in 삭제.
어법 포인트: 목적어 중복 금지, 전치사 뒤 that 불가, 전치사 중복 금지
근거: 필기본 15쪽 21·22·26번 · 16쪽 29·34번', 22, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영작(-thing+형용사) (4점)
I heard something interesting from my friend. - thing+형용사 어순이다. 네 번째 단어는 interesting이다.
어법 포인트: -thing+형용사
근거: 필기본 17쪽 -thing+형용사 예문', 23, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(-thing+형용사) (3점)
-thing으로 끝나는 대명사는 형용사가 뒤에서 꾸민다. strange nothing → nothing strange.
어법 포인트: -thing+형용사 어순
근거: 필기본 18쪽 20번 · 17쪽 -thing+형용사', 24, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(-thing+형용사) (3점)
Ⓐ -thing은 단수 취급 → is Ⓑ something을 뒤에서 꾸미는 것은 형용사 delicious Ⓒ -thing+형용사+to부정사 → to tell.
어법 포인트: -thing 단수 취급, -thing+형용사(+to부정사)
근거: 필기본 18쪽 14·7·18번', 25, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(-thing+형용사) (4점)
틀린 문장: new somewhere → somewhere new, famous anybody → anybody famous, something to do important → something important to do, There are → There is, warm something → something warm (5개).
어법 포인트: -thing+형용사(+to부정사) 어순, -thing 단수 취급
근거: 필기본 17~18쪽 보충 2번, 5·9·12번', 26, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q27.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p27-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영작(사역동사) (4점)
At the same time, they let people enjoy the beauty of nature. 일곱 번째 단어는 people이다.
어법 포인트: 사역동사 let+목적어+동사원형
근거: 필기본 07쪽 본문 A ③', 27, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q28.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p27-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 문맥상 쓰임 (3점)
국립 공원은 자연을 지키는 곳이므로 동식물 종을 안전하게 (safe) 지켜 준다고 해야 한다. ⓐ in danger → safe.
근거: 필기본 07쪽 본문 A ② They keep many animal and plant species safe.', 28, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q29.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p27-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 일치 (3점)
Torres del Paine National Park in Chile라고 했다. ① 역 동적인 날씨로 유명하다 ② in Chile ③ 남아메리카와 아프리 카의 두 공원 ④ 산, 호수, 빙원을 모두 볼 수 있다.
근거: 필기본 07쪽 본문 A ④⑦⑧⑩⑪', 29, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q30.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p27-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 유추할 수 없는 것 (3점)
공원에서 커다란 빙원(ice field)과 Grey Glacier를 볼 수 있다. ① mountains, lakes ② 하루에 맑음·비·눈 ④ one of the most wonderful glaciers in Chile ⑤ It is exciting to walk on the glacier.
근거: 필기본 07쪽 본문 A ⑨⑩⑪⑫', 30, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q31.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p27-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 답할 수 없는 질문 (3점)
매년 Grey Glacier를 걷는 방문객 수는 나오지 않는다. ① Chile ② its dynamic weather ③ Grey Glacier ⑤ sunny, rainy, or snowy.
근거: 필기본 07쪽 본문 A ⑦⑧⑨⑪ · Comprehension Check Q2', 31, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q32.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p32-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문 ⓐ~ⓖ) (4점)
옳은 것: ⓐ 목적의 to부정사 ⓑ 주어 it에 맞는 controls ⓕ 동사를 꾸미는 부사 freely. 틀린 것: ⓒ entering → to enter ⓓ There are → There’s(There is) ⓔ hear → heard ⓖ who → which.
어법 포인트: ⓒ 목적의 to부정사(to enter) / ⓓ There is+단수 명사 / ⓔ 현재완료 Have you heard / ⓖ 동물 선행사 → which
근거: 필기본 08쪽 본문 B Grammar ①④⑦⑪', 32, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q33.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p32-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 불일치 (3점)
공원은 방문객들이 떠날 때 쓰레기를 가져가도록 한다(Ⓐ).
근거: 필기본 08쪽 본문 B ③④⑤⑨⑪', 33, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q34.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p32-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이) (3점)
① trap(덫)은 윗글에 없다. ② efforts ③ book ④ products
⑤ leopards는 윗글에 있다.
근거: 필기본 01쪽 trap·effort·book·product·leopard 영어 뜻풀 이', 34, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q35.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p32-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 유추할 수 없는 것 (3점)
공원 안의 호텔들은 플라스틱을 사용하지 않고 친환경 제품만 사용한다. ⑤는 If you are lucky에서 알 수 있다.
근거: 필기본 08쪽 본문 B ①②⑤⑩⑪', 35, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q36.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p32-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 표현(사역동사) (3점)
makes visitors 뒤에는 동사원형이 오고, ‘가져가다’이므로 take their trash with them이다.
어법 포인트: 사역동사 make+목적어+동사원형
근거: 필기본 08쪽 본문 B ④ · Grammar ④', 36, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q37.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p37-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영작(수동태) (4점)
These days, drones and helicopters are used to monitor hunting activities. 수동태 be동사 are가 여섯 번 째 단어이다.
어법 포인트: 수동태 are used + 목적의 to부정사
근거: 필기본 09쪽 본문 C ③ · Grammar ③', 37, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q38.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p37-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(지문 ⓐ~ⓔ) (4점)
will 뒤에 조동사 can을 연달아 쓸 수 없으므로 will be able to로 고친다. ⓐ because of+명사구 ⓑ 목적의 to부정사 ⓒ 현재완료(계속) ⓓ monitor와 병렬.
어법 포인트: ⓔ will can → will be able to(조동사 연속 불가)
근거: 필기본 09쪽 본문 C Grammar ①②⑦', 38, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q39.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p37-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 일치 (3점)
자원봉사자들은 불법 사냥 활동의 징후를 감시한다. ② from the start ③ 과도한 사냥으로 위험에 처해 있다 ④ 덫을 제거 한다 ⑤ 사냥(hunting) 때문이다.
근거: 필기본 09쪽 본문 C ①②④⑤⑥', 39, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q40.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p37-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 연결어 (3점)
국립 공원 덕분에 미래 세대도 자연의 경이로움을 경험할 수 있다는 흐름이므로 Thanks to(~덕분에)가 알맞다.
근거: 필기본 09쪽 본문 C ⑦ Thanks to: ~덕분에', 40, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q41.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p41-43.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영작(지각동사) (4점)
Angus saw Bruce raising his feet one by one. 지각동사 saw+목적어+현재분사. 네 번째 단어는 raising이다.
어법 포인트: 지각동사 see+목적어+현재분사
근거: 필기본 19쪽 본문 A ⑨ · Grammar ⑨', 41, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q42.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p41-43.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문 ⓐ~ⓔ) (4점)
used to+동사원형(예전에 ~하곤 했다)이므로 used to have 로 고친다. ⓐ 선행사 a small store를 꾸미는 관계대명사 ⓒ start+동명사 ⓓ be covered with ⓔ 조동사 can+동사원형.
어법 포인트: ⓑ used to having → used to have(used to+동 사원형)
근거: 필기본 19쪽 본문 A ④⑤⑥⑧⑩ · used to+동사원형 필기', 42, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q43.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p41-43.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 답할 수 없는 질문 (3점)
Angus의 나이는 나오지 않는다. ① Bruce ② a small store that made socks ③ a big department store ④ in a tree.
근거: 필기본 19쪽 본문 A ①~⑦ · Comprehension Check Q1', 43, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q44.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p44-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 문맥상 쓰임 (3점)
참새들이 떨고 있어 따뜻한 겨울 양말을 만들어 준 것이므로 추운(cold) 날씨라고 해야 한다. ⓒ warm → cold.
근거: 필기본 19쪽 본문 A ⑭ shaking in the cold weather', 44, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q45.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p44-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 알 수 있는 내용 (3점)
Angus는 양말 기계 앞에 앉았고 양말이 튀어나왔다. ② 다른 참새들을 위해서도 점점 더 많이 만들었다 ③ 가게가 문을 닫 은 후 ④ red stripes ⑤ 털실을 모두 써 버렸다.
근거: 필기본 19쪽 본문 A ⑪~⑮', 45, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q46.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p46-50.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영작(-thing+형용사) (4점)
The next day, the sparrows were wearing something warm. -thing+형용사 어순이므로 아홉 번째 단어는 warm 이다.
어법 포인트: -thing+형용사(something warm)
근거: 필기본 20쪽 본문 B ① · Grammar ①', 46, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q47.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p46-50.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법·의미(지문 ⓐ~ⓔ) (4점)
ⓑ that은 선행사 the socks를 꾸미는 목적격 관계대명사로 옳다. ⓐ Before → As soon as(양말을 보자마자 백화점으로 갔다, 의미) ⓒ strange something → something strange ⓓ honest → honestly ⓔ listen → listening(과거진행형).
어법 포인트: ⓐ As soon as(의미) / ⓒ -thing+형용사 / ⓓ 동사 수식 부사 honestly / ⓔ was listening(진행형)
근거: 필기본 20쪽 본문 B ③④⑦⑧⑨ · Grammar ①④⑦', 47, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q48.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p46-50.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 일치 (3점)
Angus는 아버지에게 참새들이 추운 날씨에 떨고 있었다고 말 했다. ① 같은 양말을 찾을 수 없었다 ② the department store ③ 양말을 만든 것은 Angus ④ Angus’s pocket.
근거: 필기본 20쪽 본문 B ③⑤⑧⑨', 48, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q49.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p46-50.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(영영풀이) (3점)
③ pop out은 윗글에 없다. ① department store ② stripes ④ shaking(shake) ⑤ wool은 윗글에 있다.
근거: 필기본 12~13쪽 department store·stripe·pop out·shake·wool 영어 뜻풀이', 49, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q50.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p46-50.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 표현 (3점)
부정문(couldn’t)에서 ‘어디에서도’는 anywhere로 쓴다. nowhere는 부정어와 함께 쓰지 않는다.
근거: 필기본 20쪽 본문 B ⑤ · Vocabulary anywhere: (부정문) 어디에도', 50, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q51.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p51-54.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(영영풀이) (3점)
④ honestly는 윗글에 없다. ① trouble ② Knock ③ rushed(rush) ⑤ customers는 윗글에 있다.
근거: 필기본 12~13쪽 trouble·knock·rush·honestly·customer 영어 뜻풀이', 51, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q52.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p51-54.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 관계대명사 that (3점)
윗글의 that은 선행사 the socks를 꾸미는 목적격 관계대명 사이다. ① 명사절 접속사 ③ 지시형용사 ④ 동격절 접속사 ⑤ 진주어 that절.
어법 포인트: 목적격 관계대명사 that
근거: 필기본 16쪽 31번 · 06쪽 31번 · 21쪽 본문 C Grammar ⑦', 52, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q53.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p51-54.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(지문 ⓐ~ⓖ) (4점)
옳은 것: ⓐ brought(bring의 과거형) ⓓ flew(fly의 과거형) ⓖ are wearing(현재진행형). 틀린 것: ⓑ return → to return ⓒ because → because of ⓔ of → with ⓕ has came → has come.
어법 포인트: ⓑ need+to부정사 / ⓒ because of+명사구 / ⓔ be crowded with / ⓕ 현재완료 has come
근거: 필기본 21쪽 본문 C ①③⑤⑧⑨⑩ · Grammar ⑨', 53, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q54.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p51-54.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 일치 (3점)
참새들은 “Alright then.”이라고 Bruce의 말에 동의했다. ① We like these socks ② 돌려줘야 한다고 했다 ④ 가게로 달 려온 것은 사람들 ⑤ 개와 고양이들도 신고 있다.
근거: 필기본 21쪽 본문 C ①②④⑥⑩⑪', 54, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q55.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p55-56.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 일치 (3점)
갯벌은 바다의 모든 생물에게 먹이와 살 곳을 제공한다. ② coastal park ③ about 15,000 years ago ④ 신두리 해안사 구는 역사 때문에 방문하기 흥미롭다 ⑤ 그 기간은 피해야 한다.
근거: 필기본 10쪽 More Reading 1', 55, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q56.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p55-56.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문 ⓐ~ⓔ) (4점)
about 15,000 years ago라는 과거 시점이 있으므로 과거 수 동태 was formed로 고친다. ⓐ 진주어 to부정사 ⓒ 현재완 료(형성해 왔다) ⓓ 단수 주어 this park에 맞는 attracts ⓔ make sure+to부정사.
어법 포인트: ⓑ is formed → was formed(과거 시점 + 수동태)
근거: 필기본 10쪽 More Reading 1 필기(was formed 수동태, has formed 현재완료)', 56, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q57.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p57-58.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 문장 넣기 (3점)
these amazing creatures는 앞의 특별한 동식물을 가리키 고, 뒤의 His studies의 His는 Charles Darwin을 가리키므 로 ⓓ에 들어간다.
근거: 필기본 11쪽 More Reading 2', 57, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q58.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p57-58.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 알 수 있는 내용 (3점)
It has special plants and animals that you can’t find anywhere else라고 했다. ① first national park ② 큰 섬 13개, 작은 섬 17개 ④ 방문객을 끌어들인다 ⑤ 동물이 환경에 맞게 변하는 방식.
근거: 필기본 11쪽 More Reading 2', 58, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q59.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p59-60.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 일치 (3점)
Keeps you close at heart라고 했다. ① just to say “Hi.”
② Forgives your mistakes ③ Gives unconditionally(조 건 없이) ④ Doesn’t give up on you.
근거: 필기본 22쪽 More Reading 1', 59, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q60.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p59-60.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 문맥상 쓰임 (3점)
진정한 친구는 있는 그대로의 너를 받아들인다(Accepts). 뒤 의 Embraces you as you are, Loves you for who you are와도 맞지 않는다. ⓐ Rejects → Accepts.
근거: 필기본 22쪽 More Reading 1 Accepts you as you are.', 60, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q61.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p61-62.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문 ⓐ~ⓔ) (4점)
사자가 그물에 ‘잡힌’ 것이므로 수동태 was caught로 고친다. ⓐⓑ 사역동사 let+목적어+동사원형 ⓓ struggle to escape ⓔ 전치사 for+동명사.
어법 포인트: ⓒ caught → was caught(수동태)
근거: 필기본 23쪽 More Reading 2 · 필기(let+목적어+동사원형)', 61, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q62.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r13/p61-62.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 일치 (3점)
사자는 “How can you help me? You are so tiny.”라고 했다. ① 꼬리 위를 달려 지나간 것은 쥐 ② 날카로운 이빨로 ③ tail ⑤ 사자를 구한 것은 쥐.
근거: 필기본 23쪽 More Reading 2 · 2번', 62, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q63.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(종합) (4점)
get+목적어+to부정사이므로 clean을 to clean으로 고친다.
어법 포인트: get+목적어+to부정사(clean → to clean)
근거: 필기본 04쪽 42번(틀린 곳 고치기) · 03쪽 20번 · 05쪽 6번 · 16쪽 30번 · 18쪽 11번', 63, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q64.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(종합) (3점)
nothing은 단수 취급하므로 is가 옳다. ① to drive → drive
② to water → water ③ him 삭제 ④ solve → to solve.
어법 포인트: -thing 단수 취급 / let·make+원형 / 목적어 중복 / 진주어 to부정사
근거: 필기본 17쪽 10번 · 03쪽 9번 · 04쪽 30번 · 15쪽 17번 · 05쪽 10번', 64, 3),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q65.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(종합) (4점)
틀린 문장: let her going → go, the gift who → which/that, in that → in which, new something → something new (4개).
어법 포인트: let+동사원형 / 사물 선행사 which / 전치사 뒤 that 불가 / -thing+형용사
근거: 필기본 03쪽 13·17번 · 04쪽 35번 · 05쪽 3번 · 06쪽 24번 · 15 쪽 16번 · 16쪽 28번 · 17쪽 보충 1번 · 18쪽 13번', 65, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q66.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(종합) (4점)
틀린 문장: made the team to finish → finish, natural of teenagers → for teenagers, cold anything → anything cold. 나머지 7개는 옳다.
어법 포인트: make+동사원형 / 의미상 주어 for(natural) / - thing+형용사
근거: 필기본 03쪽 15번 · 04쪽 27·33번 · 06쪽 17·20번 · 14쪽 10번 · 16쪽 27번 · 17쪽 9번·보충 1번·6번', 66, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q67.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(종합) (4점)
옳은 것: ⓐ let+동사원형 ⓓ It ~ that절 ⓕ 전치사+which. 틀린 것: ⓑ to cry → cry ⓒ say → to say ⓔ it 삭제 ⓖ wear → to wear.
어법 포인트: ⓑ make+동사원형 / ⓒ 진주어 to부정사 / ⓔ 목적 어 중복 금지 / ⓖ -thing+형용사+to부정사
근거: 필기본 03쪽 7·14번 · 06쪽 14·21번 · 15쪽 19·24번 · 17쪽 - thing+형용사+to부정사', 67, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q68.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(종합) (4점)
관계대명사 which가 take의 목적어 역할을 하므로 it을 쓰지 않는다.
어법 포인트: 목적격 관계대명사절 안 목적어(it) 중복 금지
근거: 필기본 15쪽 22번 ④ · 04쪽 45번 · 06쪽 15번 · 15쪽 18번 ③ · 18쪽 17번', 68, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q69.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(종합) (4점)
옳은 것: ⓑ get+목적어+과거분사(수동) ⓒ It ~ that절 ⓖ anything+형용사. 틀린 것: ⓐ making → make/to make ⓓ be → to be ⓔ with who → with whom ⓕ are → is.
어법 포인트: ⓐ help+동사원형/to부정사 / ⓓ 진주어 to부정사 / ⓔ 전치사+whom / ⓕ -thing 단수 취급
근거: 필기본 04쪽 40·43번 · 06쪽 25·37번 · 16쪽 34번 · 17쪽 기초 5·8번', 69, 4),
(@naesin_r13, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r13/q70.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(종합) (3점)
have+목적어+동사원형이므로 옳다. ① finishing → finish
③ read → to read ④ it 삭제 ⑤ this math problem 삭제 (nothing이 solve의 목적어).
어법 포인트: have+동사원형 / 진주어 to부정사 / 목적어 중복 금 지
근거: 필기본 03쪽 6번·예문② · 06쪽 16번 · 14쪽 11번 · 18쪽 7번', 70, 3);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM2-2602-R14', 'NAESIN', '압구정중 2학년 2학기 기말 14회 · Final 예상문제 1회', 'Lesson 7·8 학습지 필기본 전 범위 · 40문항 · 20분 · 137점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_r14 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM2-2602-R14');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 발음부호 (3점)
wonderland의 발음부호는 [ˈwʌndərlænd]로 첫째 음절에 주강세가 있다.
근거: 필기본 02쪽 Lesson 7 단어 · wonderland [ˈwʌndərlænd]', 1, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 발음부호 (3점)
be covered with의 발음부호는 [bi ˈkʌvərd wɪð]로 covered의 첫째 음절에 주강세가 있다.
근거: 필기본 12쪽 Lesson 8 Word List · be covered with [bi ˈkʌvərd wɪð]', 2, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이) (3점)
continuously moving or changing(계속 움직이거나 변하 는) = dynamic(역동적인). icy 얼음에 뒤덮인, illegal 불법 적인, single 단 하나의, present 현재(의).
근거: L7 Word List (dynamic) · 본문 A ‘dynamic weather’', 3, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(빈칸) (3점)
book(예약하다), controls(통제하다), monitor(감시하다), preserve(보존하다)가 차례로 들어가고 explore는 들어갈 곳 이 없다.
근거: L7 Word List 1~2쪽 · 기출 1번 유형', 4, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(빈칸) (3점)
‘음악은 전 세계 사람들을 한데 모으는 힘이 있다’ → bring together(모으다). shake 떨다, return 돌려주다/돌아가다, pop out 튀어나오다, rush 서두르다.
근거: L8 Word List (bring together) · 연습 9', 5, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이) (3점)
검은 점무늬가 있는 고양잇과 동물은 표범(leopard)이다.
근거: L7 Word List (leopard) · 기출 3번 유형', 6, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(빈칸) (3점)
as soon as, used up, popped out, bring이 차례로 들어간다.
근거: L8 Word List · 기출 1번 유형', 7, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(사역동사) (3점)
let+목적어+동사원형.
근거: L7 Grammar 1 · 기출 17번 유형', 8, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영작(사역동사) (4점)
The park makes visitors take their trash with them when they leave. → The(1) park(2) makes(3) visitors(4) take(5) their(6) trash(7) with(8) them(9) when(10) they(11) leave(12).
근거: L7 본문 B ④', 9, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(-thing+형용사) (4점)
-thing으로 끝나는 대명사는 형용사가 뒤에서 수식: ① something special(옳음). ② something interesting, ③ something은 단수 → There is, ④ anything cold, ⑤ something delicious.
근거: L8 Grammar 2 -thing+형용사 · 보충 1·2 · 연습 12', 10, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(본문 문장) (4점)
start 뒤에는 동명사나 to부정사 → started buying(본문 A⑥). ① keep+목적어+형용사, ② 복수 주어+monitor, ③ used to+원형, ④ realize that ~.
근거: L7 본문 A②·C⑤, L8 본문 A⑤⑥⑮', 11, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(관계대명사) (4점)
전치사+whom은 옳다. to that 불가, him·it 중복, lived in 필요.
근거: L8 Grammar 1 · 기출 28번 유형', 12, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(가주어·의미상 주어) (4점)
옳은 것: 1·2·4·5·7·9번째 = 6개. 틀린 것: 3번째 foolish(성 격) → of him, 6번째 → to swim, 8번째 → to submit, 10 번째 가주어 → It.
근거: L7 Grammar 2 가주어 It · 연습 16~30', 13, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(사역·준사역) (4점)
ⓑ had us clean, ⓒ let me feed, ⓔ got a vet to check, ⓕ made the place look. 옳은 것은 ⓐ, ⓓ, ⓖ.
근거: L7 Grammar 1 · 기출 12번 유형', 14, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(사역·준사역) (3점)
make/let/have+목적어+동사원형, get+목적어+to부정사. laughed는 어느 빈칸에도 맞지 않는다(made everyone laugh).
근거: L7 Grammar 1 사역동사 · 기출 6번 유형', 15, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(종합) (4점)
틀린 문장 3개: The house that she lived in (it 삭제), She made us sit, It was kind of her. 나머지 다섯 문장은 옳다.
근거: L7 Grammar 1·2 · L8 Grammar 1·2', 16, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q17.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(관계대명사) (4점)
전치사 바로 뒤에는 that을 쓰지 않고, 목적어 her를 다시 쓰 지 않는다. 옳은 것 4개.
근거: L8 Grammar 1 · 기출 14번 유형', 17, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영작(관계대명사) (4점)
The socks which the sparrows are wearing are cute. → The(1) socks(2) which(3) the(4) sparrows(5) are(6) wearing(7) are(8) cute(9).
근거: L8 Grammar 1 [B] · 본문 B ④', 18, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(사역·준사역) (4점)
get+목적어+to부정사이므로 open → to open. ① let+원형
② have+원형 ④ help+원형 ⑤ make+원형.
근거: L7 Grammar 1 연습 21·28·30·34·35', 19, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영작(-thing+형용사) (4점)
Do you have anything[something] interesting to tell me? → Do(1) you(2) have(3) anything(4) interesting(5) to(6) tell(7) me(8).
근거: L8 Grammar 2 연습 18', 20, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(사역·준사역) (4점)
help+목적어+to부정사/동사원형 모두 가능. 나머지: made us write, let me drive, get him to fix, makes visitors take.
근거: L7 Grammar 1 준사역동사 help · 기출 28번 유형', 21, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(준사역 get) (3점)
get+목적어+to부정사.
근거: L7 Grammar 1 준사역동사 get · 기출 17번 유형', 22, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q23.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r14/p23-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(지문) (4점)
ⓓ 「one of the+최상급+복수명사」이므로 glacier → glaciers. ⓐ 전치사 for 뒤 동명사, ⓑ 사역동사 let+목적어 +동사원형, ⓒ be famous for, ⓔ 가주어 It–진주어 to부정 사.
근거: L7 본문 A ①③⑧⑪⑫', 23, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q24.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r14/p23-24.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 일치 (3점)
‘It can be sunny, rainy, or snowy in a single day!’ → ③. ① animal and plant species, ② 칠레(남아메리카), ④ 칠레의 가장 멋진 빙하 중 하나, ⑤ 빙하 위를 걷는 것은 신난다고 했다.
근거: L7 본문 A ②⑦⑨⑪⑫', 24, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q25.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r14/p25-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(문맥) (3점)
방문객 수를 통제하므로 공원에 들어가려면 ‘예약해야’ 한다 → book(예약하다).
근거: L7 본문 B ② · Word List (book)', 25, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q26.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r14/p25-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 파악 (3점)
방문객 수를 통제한다는 말은 있지만 하루 입장 인원은 나오 지 않는다. ① book ② plastic ③ lions 등 다섯 동물 ⑤ under baobab trees.
근거: L7 본문 B ②⑤⑧⑪', 26, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q27.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r14/p25-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(설명) (4점)
ⓒ Have you heard about ~?는 현재완료 ‘경험’의 의미이다 (필기본 ⑦). 나머지는 필기본 설명과 같다.
근거: L7 본문 B Grammar ①④⑦⑪ (필기본)', 27, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 지칭 (3점)
ⓓ them은 Big Five(사자, 표범, 코끼리, 코뿔소, 버팔로)를 가리킨다.
근거: L7 본문 B · 기출 18번 유형', 28, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(지문) (4점)
드론과 헬리콥터가 “사용되는” 것이므로 수동태 are used to monitor.
근거: L7 본문 C ③ · 기출 4번 유형', 29, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q30.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r14/p30-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 추론 (3점)
모래를 기념품으로 가져갈 수 있다는 내용은 없다.
근거: L7 More Reading 1 · 기출 9번 유형', 30, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q31.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r14/p30-31.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 불일치 (3점)
약 15,000년 전에 형성되었다.
근거: L7 More Reading 1 · 기출 5번 유형(수치·시점)', 31, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(지문) (4점)
공원이 감시·관리되는 것이므로 is carefully monitored. help him to create는 가능하다.
근거: L7 More Reading 2 · 기출 4번 유형', 32, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q33.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r14/p33-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 연결어 (3점)
다른 참새들도 떨고 있다는 것을 알았기 때문에(because).
근거: L8 본문 A ⑭ · 기출 2번 유형', 33, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q34.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r14/p33-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(우리말 표현) (3점)
one by one: 하나씩.
근거: L8 본문 A ⑨ · 기출 15번 유형', 34, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q35.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r14/p33-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 일치 (3점)
Angus sometimes helped him(his father).
근거: L8 본문 A ④ · 기출 22번 유형', 35, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q36.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r14/p33-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이) (3점)
rush는 본문 A에 없다. customer, stripe, pop out, use up은 있다.
근거: L8 Word List 영영풀이 · 기출 11번 유형', 36, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q37.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r14/p33-37.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 파악 (3점)
‘After the store closed for the night, Angus sat in front of the sock machine.’ → ②. ① 최근 손님을 많이 잃 음, ③ 양말을 만든 것은 Angus, ④ 털실을 다 써 버림, ⑤ 빨 간 줄무늬.
근거: L8 본문 A ⑤⑪⑬⑮', 37, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지문) (4점)
옳은 것: ⓐ something warm, ⓓ anywhere, ⓖ silently. ⓑ As soon as people saw, ⓒ that the sparrows are wearing(them 삭제), ⓔ something strange, ⓕ honestly.
근거: L8 본문 B · Grammar 1·2', 38, 4),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 불일치 (3점)
참새들은 “We like these socks.”라고 했지만 Angus가 곤 경에 빠졌기 때문에 양말을 돌려주었다. 양말이 싫어서 돌려 주었다는 ⑤는 일치하지 않는다.
근거: 필기본 21쪽 본문 C ②③⑤⑧', 39, 3),
(@naesin_r14, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r14/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 파악 (3점)
Accepts you as you are(①), Calls you just to say “Hi.”(②), Forgives your mistakes(③), Invites you over(⑤)는 글에 있다. 친구를 더 나은 사람으로 바꾼다는 내 용은 없다.
근거: 필기본 22쪽 More Reading 1 · 학습지 2번(언급되지 않은 것: 친구를 더 낫게 변화시킨다)', 40, 3);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM2-2602-R15', 'NAESIN', '압구정중 2학년 2학기 기말 15회 · Final 예상문제 2회', 'Lesson 7·8 학습지 필기본 전 범위 · 40문항 · 20분 · 138점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_r15 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM2-2602-R15');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 발음부호 (3점)
effort의 발음부호는 [ˈefərt]로 첫째 음절에 주강세가 있다.
근거: 필기본 01쪽 Lesson 7 단어 · effort [ˈefərt]', 1, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 발음부호 (3점)
use up의 use는 동사이므로 [juːz]로 발음한다. 바른 발음부 호는 [ˌjuːz ˈʌp]이다.
근거: 필기본 13쪽 Lesson 8 Word List · use up [ˌjuːz ˈʌp] (필 기: use는 동사이므로 [juːz])', 2, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(영영풀이) (3점)
양모, 털실(wool).
근거: L8 Word List (wool) · 기출 3번 유형', 3, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘 (3점)
trap(덫)·effort(노력)·glacier(빙하)·monitor(감시하다)가 차례로 한 칸씩 들어간다. species(종)는 어디에도 맞지 않는다.
근거: L7 Word List · 단어 연습 1·6·7·12', 4, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(빈칸) (3점)
pop out: 튀어나오다.
근거: L8 본문 A ⑫ · 기출 4번 유형', 5, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(빈칸) (3점)
사냥꾼들이 경찰에 잡혔으므로 사냥은 불법(illegal)이다.
근거: L7 Word List (illegal) 예문 · 기출 2번 유형', 6, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이) (3점)
빙하(glacier)의 영영풀이이다.
근거: L7 Word List (glacier) · 기출 3번 유형', 7, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(사역·준사역) (4점)
옳은 것: made the baby cry, had my brother carry, helped us solve, Let me introduce — 4개. 틀린 것: let me stay, got her friend to lend, made us feel, had his car washed.
근거: L7 Grammar 1 · 기출 14번 유형', 8, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(가주어) (3점)
가주어 It + 진주어 to부정사구.
근거: L7 Grammar 2 · 기출 24번 유형', 9, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(준사역 help) (4점)
help+목적어+동사원형(또는 to부정사). 선택지 중에서는 install만 가능하다.
근거: L7 Grammar 1 준사역동사 help · 연습 37', 10, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(사역동사) (4점)
허락 → let, 돈을 주고 시킴(의뢰) → had, 감정을 불러일으 킴 → made.
근거: L7 Grammar 1 Step 2 (13·14·15)', 11, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(목적격 관계대명사) (4점)
④만 옳다. ① 선행사가 사람 → who(m)/that, ② 관계사절 에 목적어 him 중복 → him 삭제, ③ 전치사 뒤 that 불가 → in which, ⑤ 전치사 뒤 → about whom.
근거: L8 Grammar 1 연습 8·15·17·27·28', 12, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영작(가주어) (4점)
It is not easy to see rhinos in the park. 일곱 번째는 rhinos.
근거: L7 Grammar 2 · 기출 23·24번 유형', 13, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(사역·준사역) (4점)
make+목적어+동사원형(clean), have+사물 목적어+과거분 사(repaired), get+목적어+to부정사(to submit), help+목 적어+동사원형(understand). to cleaning은 어디에도 들어 갈 수 없다.
근거: L7 Grammar 1 사역동사 · 준사역동사 help, get', 14, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(사역·준사역) (4점)
had a vet check, made my brother keep, makes everyone smile — 3개.
근거: L7 Grammar 1 · 기출 20번 유형', 15, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영작(-thing+형용사) (4점)
-thing+형용사+to부정사 순서.
근거: L8 Grammar 2 · 기출 12번 유형', 16, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q17.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(관계대명사) (4점)
사물 선행사 + 목적격 which, 목적어 it은 쓰지 않는다.
근거: L8 Grammar 1 · 기출 27번 유형', 17, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(관계대명사 생략) (3점)
who는 주격 관계대명사라 생략할 수 없다.
근거: L8 Grammar 1 · 기출 11번 유형', 18, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(지각동사 수동태) (4점)
Bruce was seen raising his feet one by one by Angus. 현재분사는 그대로 남는다. 네 번째는 raising.
근거: L8 본문 A ⑨ · 기출 7번 유형', 19, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 영작(관계대명사) (4점)
The socks that Angus made for the sparrows were warm. 여섯 번째는 for.
근거: L8 Grammar 1 · 기출 23번 유형', 20, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영작(가주어) (4점)
It is exciting to walk on the glacier and enjoy the view. → It(1) is(2) exciting(3) to(4) walk(5) on(6) the(7) glacier(8) and(9) enjoy(10) the(11) view(12).
근거: L7 본문 A ⑫ · Grammar 2', 21, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법·어휘(지문) (4점)
keep+목적어+형용사 보어이므로 safely → safe.
근거: L7 본문 A ② · 기출 4번 유형', 22, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법·어휘(지문) (4점)
ⓐ to preserve(목적), ⓒ take, ⓓ heard(현재완료), ⓔ which are(선행사 rhinos 복수). ⓑ만 옳다.
근거: L7 본문 B · 기출 21번 유형', 23, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q24.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r15/p24-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 연결어 (3점)
Ⓐ 앞뒤가 대조 → However, Ⓑ 국립 공원 덕분에 → Thanks to.
근거: L7 본문 C ①⑦ · 기출 2번 유형', 24, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q25.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r15/p24-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이) (3점)
dynamic(역동적인)은 본문 C에 없다. hunting, monitor, illegal, trap은 모두 있다.
근거: L7 본문 C · Word List 영영풀이', 25, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q26.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r15/p24-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 일치 (3점)
Volunteers ... remove animal traps.
근거: L7 본문 C ⑤ · 기출 22번 유형', 26, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q27.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r15/p24-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 파악 (3점)
… future generations will also be able to experience many natural wonders.
근거: L7 본문 C ⑦ · 기출 16번 유형', 27, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q28.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r15/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘(문맥) (3점)
보호·관리(conservation)를 위해 방문을 ‘막는’ 기간이 있으 므로 그 기간을 피하라고 했다. ⓔ open → close.
근거: L7 More Reading 1', 28, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q29.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r15/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 일치 (3점)
‘They offer food and places to live for all living things in the sea.’ → ②. ① 땅과 바다 모두, ③ 15,000년 전, ④ 보 호를 위해 방문을 막는 기간이 있음, ⑤ 많은 방문객을 끌어들 임.
근거: L7 More Reading 1 · 학습지 T/F', 29, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q30.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r15/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(가주어) (4점)
밑줄 친 it은 진주어 to visit ~를 대신하는 가주어. ③도 가주 어(진주어 to drink ~). ①④ 날씨·시간의 비인칭 주어, ②⑤ 대명사.
근거: L7 Grammar 2 가주어 It · 연습 1', 30, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q31.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r15/p31-32.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 불일치 (3점)
다윈은 갈라파고스를 방문해 생물을 관찰했고, 그 연구가 진 화 개념을 만드는 데 도움을 주었다. 방문 ‘전에’ 만들었다는 ⑤는 틀리다. ② 13+17=30개의 섬과 40개의 바위로 일치.
근거: L7 More Reading 2', 31, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q32.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r15/p31-32.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(영영풀이) (3점)
① monitored ② explore ④ visited ⑤ famous는 글에 있다. ③ preserve(보존하다)는 이 글에 나오지 않는다.
근거: L7 Word List (monitor·explore·preserve)', 32, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법·어휘(지문) (4점)
과거의 상태는 used to+동사원형 → used to have.
근거: L8 본문 A ⑤ · 기출 4번 유형', 33, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q34.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r15/p34-38.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(-thing+형용사) (3점)
-thing 뒤에는 형용사: something warm, something strange.
근거: L8 본문 B ①⑥ · Grammar 2', 34, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q35.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r15/p34-38.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 연결어 (3점)
양말을 ‘보자마자’ 백화점으로 서둘러 갔다 → As soon as. Because of 뒤에는 절이 올 수 없다.
근거: L8 본문 B ③ · Word List (as soon as)', 35, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q36.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r15/p34-38.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(관계대명사) (4점)
밑줄 친 that은 선행사 the socks를 수식하며 wearing의 목 적어 역할을 하는 목적격 관계대명사. ④도 used의 목적어 역 할을 하는 목적격 관계대명사. ①③ 명사절 접속사, ② 지시형 용사, ⑤ 동격의 접속사.
근거: L8 본문 B ④ · Grammar 1 연습 31', 36, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q37.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r15/p34-38.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(우리말 표현) (3점)
동사 said를 꾸미는 부사 honestly.
근거: L8 본문 B ⑧ · 기출 15번 유형', 37, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q38.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r15/p34-38.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 파악 (3점)
Angus는 아버지에게 참새들에게 양말을 만들어 주었다고 정 직하게 말했다 → ⑤. ② 어디에서도 찾지 못함, ③ ‘누군가’ 가져갔다고 함, ④ 조용히 듣고 있었다.
근거: L8 본문 B ⑤⑦⑧⑨', 38, 3),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(지문) (4점)
현재완료는 「have/has+p.p.」이므로 has came → has come. ⓐ bring의 과거형 brought, ⓑ need+to부정사, ⓒ fly의 과거형 flew, ⓓ 선행사 the socks를 수식하는 목적격 관계대명사 that.
근거: 필기본 21쪽 본문 C Grammar ②⑦⑨ · 필기(brought, flew)', 39, 4),
(@naesin_r15, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r15/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 내용 불일치 (3점)
사자는 “How can you help me? You are so tiny.”라고 했 고, 나중에 “I didn’t think you could help me”라고 말했다. 쥐가 자신을 도울 수 있다고 생각했다는 ③은 일치하지 않 는다.
근거: 필기본 23쪽 More Reading 2 · 학습지 2번 ③ 참고', 40, 3);

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-APM2-2602-R16', 'NAESIN', '압구정중 2학년 2학기 기말 16회 · Final 예상문제 3회', 'Lesson 7·8 학습지 필기본 전 범위 · 40문항 · 20분 · 135점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_r16 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-APM2-2602-R16');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q01.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 발음부호 (3점)
present는 명사일 때 [ˈpreznt], 동사일 때 [prɪˈzent]이다. 명사 present에 동사의 발음부호를 붙였다.
근거: 필기본 01쪽 Lesson 7 단어 · present 명사 [ˈpreznt] / 동사 [prɪˈzent]', 1, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q02.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 발음부호 (3점)
wool의 발음부호는 [wʊl]이다(필기: wool은 [wʊl]).
근거: 필기본 13쪽 Lesson 8 Word List · wool [wʊl]', 2, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(빈칸) (3점)
‘저축한 돈을 지금 다 써 버리면 여행에 쓸 돈이 남지 않는다’ 이므로 use up(다 써 버리다)이 알맞다.
근거: L8 Word List (use up) · 연습 4', 3, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘(빈칸) (3점)
run: 운영하다.
근거: L7 본문 C ④ · 기출 2번 유형', 4, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 전치사 (3점)
be famous for(~로 유명하다), be open to(~에게 개방되 다), in danger(위험에 처한), be made up of(~로 구성되 다), in front of(~의 앞에) → for / to / in / of / of.
근거: L7 본문 A⑧·B③·C①, More Reading 2, L8 본문 A⑪', 5, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘(숙어) (3점)
take a look at: ~을 살펴보다.
근거: L7 본문 A ④ · 기출 4번 유형', 6, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(영영풀이) (3점)
without making any noise; quietly = silently(조용히). honestly 정직하게, recently 최근에, freely 자유롭게, carefully 주의 깊게.
근거: L8 Word List (silently) · 본문 B ⑨', 7, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘(빈칸) (3점)
추운 바람에 떨고(shaking) 있었다.
근거: L8 Word List (shake) · 기출 2번 유형', 8, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(사역동사 수동태) (4점)
Visitors are made to take their trash with them. 사역 동사 make의 수동태에서 동사원형은 to부정사가 된다. 네 번 째 단어는 to.
근거: L7 본문 B ④ · 기출 7번 유형', 9, 4),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(-thing+형용사) (3점)
-thing으로 끝나는 대명사는 형용사가 뒤에서 꾸민다.
근거: L8 Grammar 2 · 기출 28번 유형', 10, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(관계대명사) (4점)
관계대명사가 목적어를 대신하므로 it을 쓰지 않는다.
근거: L8 Grammar 1 · 기출 19번 유형', 11, 4),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q12.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 영작(관계대명사) (4점)
We want the socks that the sparrows are wearing. 일 곱 번째는 sparrows.
근거: L8 본문 B ④ · 기출 27번 유형', 12, 4),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q13.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(가주어·의미상 주어) (4점)
important는 성격 형용사가 아니므로 for us.
근거: L7 Grammar 2 · 기출 19번 유형', 13, 4),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q14.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(사역·준사역) (4점)
have+목적어+동사원형이므로 to stretch → stretch.
근거: L7 Grammar 1 · 기출 11·19번 유형', 14, 4),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q15.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(종합) (4점)
ⓓ removed만 옳다. ⓐ has him check(have+목적어+동사 원형), ⓑ important → for volunteers, ⓒ something strange, ⓔ which he found(it 삭제).
근거: L7 Grammar 1·2 · L8 Grammar 1·2', 15, 4),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q16.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 영작(사역동사) (3점)
have+목적어+동사원형.
근거: L7 Grammar 1 · 기출 12번 유형', 16, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q17.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(-thing+형용사) (4점)
「-thing+형용사+to부정사」 순서이므로 ① → something cold to drink. ②③④⑤는 모두 가능하다.
근거: L8 Grammar 2 「-thing+형용사+to부정사」', 17, 4),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 요약 (3점)
참새들이 추위에 떨고 있었기 ‘때문에’(because) 양말을 만들 었고, 아버지에게 ‘정직하게’(honestly) 말했다(Angus honestly said, …).
근거: L8 본문 A⑭ · 본문 B⑧', 18, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(가주어·의미상 주어) (4점)
Ⓐ 뒤에 절 → that, Ⓑ 동사원형 → to, Ⓒ careless(성격) → of.
근거: L7 Grammar 2 · 기출 10번 유형', 19, 4),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법(관계대명사 생략) (4점)
목적격 관계대명사는 생략할 수 있지만 주격 관계대명사는 생 략할 수 없다. ⑤ who는 came의 주어 역할 → 생략 불가.
근거: L8 Grammar 1 연습 18·23', 20, 4),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(종합) (4점)
틀린 문장 4개: let me choose, made us take, The deer that I saw (it 삭제), It was kind of the guide. 나머지 다 섯 문장은 옳다.
근거: L7 Grammar 1·2 · L8 Grammar 1·2', 21, 4),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법(-thing+형용사) (4점)
There is something, something warm to wear, something cold to drink — 3개.
근거: L8 Grammar 2 · 기출 20번 유형', 22, 4),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영작(가주어) (4점)
It is exciting to walk on the glacier and enjoy the view. → It(1) is(2) exciting(3) to(4) walk(5). 다섯 번째는 walk.
근거: L7 본문 A ⑫ · 기출 23·24번 유형', 23, 4),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법(의미상 주어) (3점)
사람의 성격을 나타내는 형용사(kind) 뒤 의미상 주어는 of+목적격.
근거: L7 Grammar 2 · 기출 17번 유형', 24, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q25.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r16/p25-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 지칭 (3점)
앞 문장의 experience four seasons in one day를 가리킨다.
근거: L7 본문 A ⑦ · 기출 18번 유형(한글 선택지)', 25, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q26.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r16/p25-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 주제 (3점)
토레스 델 파이네 국립 공원의 역동적인 날씨와 자연을 소개 하는 글이다.
근거: L7 본문 A · 기출 21번 유형', 26, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q27.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r16/p25-27.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 불일치 (3점)
Grey Glacier는 칠레에서 가장 멋진 빙하들 중 하나이다.
근거: L7 본문 A ⑪ · 기출 22번 유형', 27, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q28.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r16/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 문장 넣기 (3점)
Big Five를 묻는 질문(Have you heard about the Big Five?) 바로 뒤에 Big Five의 구성이 와야 한다. 다음 문장의 them all도 이 다섯 동물을 가리킨다.
근거: L7 본문 B ⑦⑧ · 기출 8번 유형', 28, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q29.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r16/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 파악 (3점)
호텔을 더 짓는다는 내용은 없다.
근거: L7 본문 B ①~⑤ · 기출 29번 유형', 29, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q30.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r16/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 일치 (3점)
You can see them all in Kruger National Park.
근거: L7 본문 B ⑨ · 기출 9·22번 유형', 30, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q31.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r16/p31-32.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문) (4점)
ⓑ has been(처음부터 지금까지 계속)만 옳다. ⓐ 뒤에 명사 구 → because of, ⓒ 드론이 ‘사용되는’ 것 → are used, ⓓ 주어 Volunteers(복수) → monitor, ⓔ 조동사 연속 불가 → be able to.
근거: L7 본문 C ①~⑦ Grammar', 31, 4),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q32.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r16/p31-32.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 불일치 (3점)
자원봉사자들은 동물 덫을 ‘제거한다(remove animal traps)’. 덫을 놓는다는 ④는 일치하지 않는다.
근거: L7 본문 C ①②③⑤⑦', 32, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법(지문) (4점)
ⓐ describes, ⓑ to visit(진주어), ⓒ was formed, ⓔ to avoid. ⓓ만 옳다.
근거: L7 More Reading 1 · 기출 25번 유형', 33, 4),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q34.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r16/p34-35.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 지칭 (3점)
ⓔ They는 공원을 찾는 방문객들(visitors)이다.
근거: L7 More Reading 2 · 기출 18번 유형', 34, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q35.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r16/p34-35.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 일치 (3점)
Charles Darwin visited the Galápagos in the 1830s.
근거: L7 More Reading 2 · 기출 22번 유형', 35, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q36.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r16/p36-38.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법(지문) (4점)
틀린 것: ⓑ 선행사가 사물(a small store) → that/which, ⓒ used to+동사원형 → used to have, ⓕ 주어 other sparrows(복수) → were. 옳은 것: ⓐ ⓓ ⓔ ⓖ. 보기 중 옳 은 것만으로 된 것은 ②.
근거: L8 본문 A ①④⑤⑥⑨⑭⑮ · Grammar', 36, 4),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q37.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r16/p36-38.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 일치 (3점)
‘After the store closed for the night, Angus sat in front of the sock machine.’ → ⑤. ① 작은 마을, ② 최근 손님을 많이 잃음, ③ 큰 백화점에서 사기 시작, ④ 깨닫지 못 했다(didn’t realize).
근거: L8 본문 A ①⑤⑥⑪⑮ · Comprehension Check', 37, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q38.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r16/p36-38.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘(영영풀이) (3점)
② customers ③ Villagers ④ popped out ⑤ wool은 글 에 있다. ① honestly(정직하게)는 본문 B에 나오는 단어로 이 글에는 없다.
근거: L8 Word List 영영풀이', 38, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q39.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r16/p39-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 불일치 (3점)
사람들은 어디에서도 같은 양말을 찾을 수 없었다(they couldn’t find the same socks anywhere).
근거: L8 본문 B ⑤', 39, 3),
(@naesin_r16, 'EN', 'NAESIN_MC5', '[[img:/naesin/apgujeong-m2-2026-2/r16/q40.webp]]', '[[img:/naesin/apgujeong-m2-2026-2/r16/p39-40.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 지칭 (3점)
ⓔ They는 추위에 떨고 있던 참새들(sparrows)을 가리킨다.
근거: L8 본문 B ⑨ F2R Feel to Real 2 0 2 6 압구정중학교 2학년 기말고사 총정리 영어 · Lesson 7 – 8 교사용 · 정답 PART 01 Word List · 발음부호 · Lesson 7·8 PART 02 문법 정리 · 사역동사 · 가주어 It PART 03 문법 정리 · 목적격 관계대명사 · -thing+형용사 PART 04 본문 빈칸 · Lesson 7 Wild Wonderlands PART 05 본문 빈칸 · Lesson 8 Sparrow Socks PART 06 추가 지문 · Lesson 7 More Reading 1·2 PART 07 추가 지문 · Lesson 8 More Reading 1·2 이름 F2R 영어 압구정에듀홀릭 · feel2real.co.kr Word List PART 01 Lesson 7 · Natural Wonders No. 영어 뜻풀이 / 관련 표현 a short thick tree, found especially in Africa and Australia, that lives for 1 many years to arrange to do or use something at a particular time in the future 2 관련 표현: reserve book in advance to limit something or make it happen in a particular way 3 관련 표현: gain/lose control under control continuously moving or changing 4 관련 표현: dynamic atmosphere an attempt to do something especially when it is difficult to do 5 관련 표현: make an effort, put effort into to travel to or around an area or a country in order to learn about it 6 관련 표현: explore new options/possibilities a slowly moving mass or river of ice, formed from snow on mountains or 7 near the poles 관련 표현: melting glacier alpine glacier going after and killing animals for food or sport 8 관련 표현: go hunting covered with or having ice 9 관련 표현: frozen icy road icy stare not allowed by the law 10 관련 표현: unlawful, banned, prohibited illegal activity illegal hunting a large animal of the cat family, usually with yellow-brown fur with black 11 spots to watch or check for a special purpose 12 관련 표현: monitor the situation closely monitor the period of time that is happening now 13 관련 표현: introduce, gift, current present an opportunity at the present time to make sure that something is kept 14 관련 표현: protect, conserve preserve nature/culture a thing that is produced or created, usually for sale 15 관련 표현: merchandise, goods product development a group of animals or plants whose members are similar and can breed 16 together 관련 표현: breed, type, category endangered species a piece of equipment for catching animals 17 관련 표현: set a trap fall into a trap be trapped inside a place that is exciting and full of beautiful and interesting things 18 관련 표현: paradise, utopia natural/winter wonderland Lesson 7 · 8 단어 우리말 뜻 바오밥 나무 baobab tree 예약하다, 책 book 통제하다, 규제하 control 다 역동적인 dynamic 노력 effort 탐험하다 explore 빙하 glacier 사냥 hunting 얼음에 뒤덮인 icy 불법적인 illegal 표범 leopard 감시하다, 관찰하 monitor 다 현재, 선물 present 보존하다, 보호하 preserve 다 제품, 상품 product (생물분류상의) species 종 덫 trap 아주 멋진 곳 wonderland Lesson 8 · The Magic of Giving No. 영어 뜻풀이 / 관련 표현 right after something happens; immediately 1 관련 표현: the moment, the minute to have something on top and all over 2 관련 표현: be filled with, be full of to cause people or things to come to the same place or to join 3 관련 표현: unite, gather a person who buys things from a store 4 관련 표현: client, buyer, shopper a large store that sells many different types of products, like clothes, food, 5 etc. 관련 표현: mall, shopping center in a truthful way; without lying or hiding the truth 6 관련 표현: frankly the sound of hitting a door to get someone’s attention 7 관련 표현: tap, bang, hit 8 two things that go together, like socks or shoes to suddenly appear or come out 9 관련 표현: jump out, burst out to give something back or go back to a place 10 관련 표현: give back to move quickly or hurry 11 관련 표현: hurry, dash to move uncontrollably, caused by a strong emotion or cold 12 관련 표현: tremble, quiver without making any noise; quietly 13 관련 표현: quietly 14 a piece of clothing you wear on your foot 15 a small bird that 16 a long line or band of a different color difficulties or problems 17 관련 표현: difficulty, issue to finish or consume something completely 18 관련 표현: consume, run out of 19 a person who lives in a small town a soft, warm material made from the fur of sheep 20 관련 표현: fleece 단어 우리말 뜻 ~하자마자 as soon as ~로 덮여 있다 be covered with ~을/를 모으다 bring … together 손님, 고객 customer department 백화점 store 정직하게 honestly 똑똑 두드리는 소 knock 리 짝 pair 튀어나오다 pop out 돌려주다, 돌아오 return 다 급히 움직이다, rush 서두르다 떨다, 흔들리다 shake 조용히 silently 양말 sock 참새 sparrow 줄무늬 stripe 문제, 곤란 trouble 다 써 버리다 use up 마을 사람 villager 양모, 털실 wool 발음부호 · 강세 정리 기말시험 출제: 7과 · 8과 단어의 발음부호 단어 철자·발음부호·강세를 함께 암기하세요. [ ] 안은 발음부호, ˈ는 주강세, ˌ는 부강세입니다. • 7과 사진 강조 단어: glacier [ˈɡleɪʃər] · leopard [ˈlepərd] • present: 명사 [ˈpreznt] / 동사 [prɪˈzent] — 품사에 따라 강세가 달라집니다. • honestly의 h, knock의 k는 소리 나지 않습니다. 철자와 실제 발음을 구별하세요. • use up의 use는 동사이므로 [juːz]로 발음합니다. wool은 [wʊl]입니다. ★ 표시: 위 주의 사항에 나온 단어 Lesson 7 · Natural Wonders No. 단어 발음부호 뜻 바오밥 나무 1 baobab tree [ˈbeɪoʊbæb triː] 예약하다, 책 2 book [bʊk] 통제하다, 규제하다 3 control [kənˈtroʊl] 4 dynamic [daɪˈnæmɪk] 역동적인 5 [ˈefərt] 노력 effort 6 [ɪkˈsplɔːr] 탐험하다 explore 7 glacier ★ [ˈɡleɪʃər] 빙하 8 [ˈhʌntɪŋ] hunting 사냥 9 icy [ˈaɪsi] 얼음에 뒤덮인 10 illegal [ɪˈliːɡəl] 불법적인 leopard ★ 11 [ˈlepərd] 표범 12 monitor [ˈmɑːnɪtər] 감시하다, 관찰하다 명사 [ˈpreznt] present ★ 13 현재, 선물 동사 [prɪˈzent] [prɪˈzɜːrv] 14 preserve 보존하다, 보호하다 15 product [ˈprɑːdʌkt] 제품, 상품 16 species [ˈspiːʃiːz] (생물분류상의) 종 17 trap [træp] 덫 18 wonderland [ˈwʌndərlænd] 아주 멋진 곳 발음부호: 미국식 대표 발음 기준(구는 낱말별 발음을 연결하여 표기). 참고: Oxford Learner’s Dictionaries · Cambridge Dictionary. glacier의 영국식 대표 발음은 [ˈɡlæsiə(r)]입니다. Lesson 8 · The Magic of Giving No. 단어 발음부호 뜻 ~하자마자 1 as soon as [əz ˈsuːn əz] ~로 덮여 있다 2 be covered with [bi ˈkʌvərd wɪð] ~을/를 모으다 3 bring … together [brɪŋ … təˈɡeðər] 4 customer [ˈkʌstəmər] 손님, 고객 department [dɪˈpɑːrtmənt stɔːr] 백화점 5 store honestly ★ 6 [ˈɑːnɪstli] 정직하게 knock ★ 똑똑 두드리는 소리 7 [nɑːk] 짝 8 pair [per] 튀어나오다 9 pop out [ˌpɑːp ˈaʊt] [rɪˈtɜːrn] 10 return 돌려주다, 돌아오다 급히 움직이다, 서두 11 rush [rʌʃ] 르다 12 shake [ʃeɪk] 떨다, 흔들리다 13 silently [ˈsaɪləntli] 조용히 양말 14 sock [sɑːk] 참새 15 sparrow [ˈspæroʊ] 줄무늬 16 stripe [straɪp] 17 trouble [ˈtrʌbəl] 문제, 곤란 use up ★ 다 써 버리다 18 [ˌjuːz ˈʌp] 마을 사람 19 villager [ˈvɪlɪdʒər] wool ★ 양모, 털실 20 [wʊl] 문법 정리 PART 02 Lesson 7 · 문법 1 · 사역동사 1. make/have/let + 목적어 + 동사원형 • 사역동사: ‘다른 사람이 무엇을 하게 하다’라는 의미를 나타내는 동사로 make, have, let 등이 있다. 형태 의미 make+목적어+동사원형 (목적어가) ~하게(하도록) 하다(시키다) 〈have보다 직접적이고 강제성이 있음〉 have+목적어+① 동사원형 (목적어가) ~하게(하도록) 하다 〈요청, 부탁의 의미를 내포〉 let+목적어+동사원형 (목적어가) ~하게(하도록) (허락)하다 〈허락의 의미를 내포〉 • 형태: 5형식 문장에서 주어 다음에 「make/have/let+목적어+동사원형」 형태로 오고, 이때 동사원형은 목적어의 동작이나 상 태를 설명해 주는 목적격 보어이다. e.g. My mom made me go to bed early. (엄마는 내가 일찍 자도록 하셨다.) The teacher made the students ② finish their homework on time. (선생님은 학생들이 그들의 숙제를 제시간에 끝내게 했다.) He had the gardener plant new flowers in the garden. (그는 정원사가 정원에 새 꽃들을 심도록 했다.) She ③ let her son eat ice cream. (그녀는 그녀의 아들이 아이스크림을 먹게 해 주었다.) Step 1. 괄호 안의 동사를 알맞은 형태로 바꿔 빈칸에 쓰세요. 1. My mother made me clean (clean) my room. 2. The teacher let us go (go) home early today. 3. I had the mechanic check (check) my car. 4. She didn’t let her child watch (watch) TV late at night. 5. The funny movie made everyone laugh (laugh). 6. He had his assistant print (print) the documents. 7. Please let me know (know) if you need any help. 8. The teacher made the students rewrite (rewrite) their essays. 9. My dad let me drive (drive) his car on the weekend. 10. She had the waiter bring (bring) some more water. Step 2. make / have / let 중 알맞은 동사를 알맞은 시제(현재/과거 등)로 넣으세요. 11. The strict coach makes us run five miles every morning. (강제로 시킴) 12. My boss had me write a summary of the meeting. (지시/부탁함) 13. Her parents let her go to the party with her friends. (허락함) 14. The sad music made me cry. (감정을 불러일으킴/하게 만듦) 15. I had the barber cut my hair yesterday. (돈을 주고 시킴/의뢰함) 16. Will you let me use your smartphone for a minute? (허락을 구함) 17. The heavy rain made us cancel our picnic plans. (어쩔 수 없이 하게 만듦) 18. She made / had her younger brother carry her heavy bag. (시킴) 19. My teacher doesn’t let us use calculators during the exam. (허용하지 않음) Lesson 7 20. He had the doctor examine his eyes last week. (진료/점검을 맡김) Lesson 7 · 문법 1 · 사역동사 연습 Step 3. [21–25] 보기에서 알맞은 동사를 골라 문장을 완성하세요. (보기: study, wash, explain, enter, wait) 21. The guard didn’t let anyone enter the building without an ID card. 22. The teacher made the whole class wait for another 10 minutes. 23. I had the car washer wash my car thoroughly. 24. Parents often make their children study hard before exams. 25. Can you have the guide explain the rule once more? [26–30] (A)에는 사역동사, (B)에는 알맞은 동사원형을 적으세요. 26. Don’t (A) let anger (B) ruin (ruin) your day. 27. The manager (A) made the team (B) finish (finish) the project by Friday. (강력히 요구함) 28. I will (A) have my secretary (B) call (call) you back in ten minutes. 29. Why did you (A) let him (B) leave (leave) without saying goodbye? (막지 않고 놔둠/허락함) 30. Onion slices always (A) make my eyes (B) water (water). (생리적 반응을 유발함) 준사역동사 help, get [31–35] 괄호 안의 동사를 알맞은 형태로 바꾸세요. (가능한 형태가 2개이면 모두 쓰세요.) 31. My teacher helped me understand / to understand (understand) the difficult math problem. 32. I will get my brother to fix (fix) my bicycle tomorrow. 33. She helped her mother prepare / to prepare (prepare) dinner in the kitchen. 34. The dentist got the patient to open (open) his mouth wider. 35. Reading books helps you expand / to expand (expand) your vocabulary. [36–40] 문맥과 어법상 가장 알맞은 표현을 고르세요. 36. I need to get my hair cut before the wedding.
① cut ② to cut ③ cutting ④ cuts 37. The technician helped us install the new software on our computers.
① installing ② installed ③ install ④ to installing 38. How can I get my dog to stop barking at night?
① stop ② to stop ③ stopped ④ stopping 39. She got her phone stolen while she was riding the subway.
① steal ② to steal ③ stolen ④ stealing 40. His advice helped me make a better decision.
① make ② to making ③ made ④ making [41–45] 어법상 틀린 부분을 찾아 고치세요. (없으면 “정답 없음”) 41. She (A) helped her younger brother (B) to doing his homework (C) last night. → (B) do / to do 42. I finally (A) got my roommate (B) clean the kitchen (C) this morning. → (B) to clean 43. He (A) got his car (B) repaired at the auto shop (C) yesterday. → 정답 없음 44. Can you (A) help me (B) carrying these heavy boxes (C) upstairs? → (B) carry / to carry 45. The teacher (A) got all the students (B) to submit their projects (C) in time. → 정답 없음 Lesson 7 · 문법 2 · 가주어 It 2. It … to부정사 • 가주어–진주어: to부정사구가 문장의 주어일 때 문장 앞부분이 길어지는 것을 막기 위해 진주어인 to부정사구문을 뒤로 보내고 형식적인 주어인 가주어 It을 문장의 앞에 쓴다. 이때 가주어 It은 해석하지 않는다. e.g. To cook spaghetti is easy. = ① It is easy to cook spaghetti. (스파게티를 요리하는 것은 쉽다.) • 형태와 의미: 「It(가주어)+be동사+형용사+to부정사구(진주어) ~.」 형태로 쓰고, ‘~하는 것은 …하다’라고 해석한다. e.g. It is dangerous to swim in the sea. (바다에서 수영하는 것은 위험하다.)
② It is impossible to live without water. (물 없이 사는 것은 불가능하다.) • to부정사의 의미상의 주어: 보통 to부정사 앞에 「for+목적격」을 쓰고, 사람의 성격이나 특징을 나타내는 형용사(nice, kind, foolish, careful, wise, honest, rude 등)일 때는 「of+③ 목적격」을 쓴다. e.g. It will be easy ④ for her to solve the quiz. (그녀가 그 퀴즈를 푸는 것은 쉬울 것이다.) It was foolish ⑤ of you to say so. (네가 그렇게 말한 것은 어리석었다.) [1–10] 빈칸에 가장 알맞은 단어 하나를 적으세요. 1. It is important to drink enough water every day. 2. It is easy to learn basic math rules. 3. It is necessary to wear a seatbelt while driving. 4. It is fun to play soccer with friends after school. 5. It is dangerous to swim in this deep river. 6. It is not good to skip breakfast every morning. 7. It is hard to wake up early on Sunday mornings. 8. It is healthy to eat fresh fruits and vegetables. 9. It was difficult to find the hidden key in the dark. 10. It is impossible to solve this puzzle in one minute. [11–15] 괄호 안 단어들을 바르게 배열하세요. 11. It is nice to meet (meet / to) you today. 12. It was exciting to watch (watch / to) the live match. 13. It is safe to cross (cross / to) the street here. Lesson 7 · 문법 2 · 가주어 It 연습 14. It is polite to say (say / to) thank you. 15. It was surprising to see (see / to) him at the airport. [16–25] 알맞은 단어를 채워 문장을 완성하세요. 의미상 주어(for/of+목적격), that절 진주어, 시제 및 부정 구문 응용 포함 16. It is essential for students to read books regularly. 17. It was very kind of you to lend me your umbrella. 18. It is obvious that she has prepared a lot for this presentation. 19. It is foolish of him to make the same mistake twice. 20. It is natural for teenagers to care about their appearance. 21. It surprised everyone that he passed the difficult examination easily. 22. It is crucial for us not to waste our limited natural resources. 23. It was generous of her to donate all her money to charity. 24. It turns out that the news was completely false. 25. It is important for a doctor to be patient with sick people. [26–30] 괄호 안 단어를 활용하여 빈칸을 채우세요. 26. It is irresponsible for you to leave (leave) the front door unlocked. 27. It was careless of him to forget (he / forget) his passport at home. 28. It appears that the meeting was canceled (that / the meeting / cancel) due to the bad weather. 29. It is necessary for applicants to submit (submit) their resumes by Friday. 30. It is impossible for a human being to fly (fly) without any tools. [1–10] 문맥과 어법상 알맞은 표현을 고르세요. to부정사 vs that절 31. It is true that money cannot buy happiness. ① to ② that 32. It is dangerous to play with fire. ① to ② that 33. It was obvious that he was lying to his parents. ① to ② that 34. It is essential for students to sleep at least seven hours. ① to ② that 35. It is surprising that she won the first prize without any training. ① to ② that 36. It is not easy to master a foreign language in a single year. ① to ② that 37. It is clear that global warming is affecting the world’s weather. ① to ② that 38. It is necessary to turn off the lights when you leave the room. ① to ② that 39. It turned out that the rumor was completely wrong. ① to ② that 40. It is polite to say “thank you” when someone helps you. ① to ② that 문법 정리 PART 03 Lesson 8 · 문법 1 · 목적격 관계대명사 목적격 관계대명사 • 정의와 종류: 목적격 관계대명사는 앞에 있는 명사(선행사)를 수식하는 관계대명사절 내에서 ① 목적어 역할을 하는 관계대명사 로 선행사의 종류에 따라 who, whom, which, that을 쓴다. 선행사 목적격 관계대명사 예문 사람 who / ② whom Jenny is the singer who(m) I like best. (Jenny는 내가 제일 좋아하는 가수이다.) 사물, 동물 which This is the plant which I grow. (이것은 내가 키우는 식물이다.) 사람, 사물, 동물 that Emma is the girl that I told you about. (Emma는 내가 너에게 말했던 소녀이다.) The movie that I watched yesterday was touching. (내가 어제 본 영화는 감동적이었다.) This is not the cat ③ that I saw in the park. (이것은 내가 공원에서 본 고양이가 아니다.) • 어순: 문장에서 목적격 관계대명사절은 선행사 뒤에 「목적격 관계대명사+④ 주어+동사」의 어순으로 오고, 관계대명사가 목적 어를 대신하므로 관계대명사절에 목적어를 쓰지 않는 것에 주의한다. e.g. Do you remember the American boy? + We met him at the party. → Do you remember the American boy who(m)[that] we met at the party? 목적격 관계대명사+주어+동사: 뒤 절에서 목적어 자리가 비어 있다. him 등 목적어를 중복해서 쓰지 않는다. 목적격 관계대명사는 보통 생 략 가능하다. [A] Fill in the blank with the appropriate relative pronoun. 1. This is the book which I read last week. ① who ② whom ③ which ④ what 2. The teacher whom we respect is very kind. ① which ② whom ③ whose ④ where 3. This is the camera which my father bought for me. ① who ② whom ③ which ④ whose 4. The girl who I met at the party was very friendly. ① which ② who ③ whose ④ where 5. This is the cat which my sister likes very much. ① who ② whom ③ which ④ whose 6. The movie which / that I watched yesterday was exciting. (사용 가능한 모든 관계대명사를 쓰시오.) 7. The boy who / whom / that you saw in the library is my brother. (사용 가능한 모든 관계대명사를 쓰시오.) 8. This is the song which / that we listened to in the car. (사용 가능한 모든 관계대명사를 쓰시오.) [B] 두 문장을 목적격 관계대명사를 사용하여 한 문장으로 연결하시오. 9. I lost the ring. + My mother gave it to me. → I lost the ring which my mother gave to me. 10. The boy is my classmate. + You met him yesterday. → The boy whom you met yesterday is my classmate. 11. This is the pizza. + We ordered it an hour ago. → This is the pizza which we ordered an hour ago. Lesson 8 12. Do you know the man? + Kelly is talking to him. → Do you know the man whom Kelly is talking to? 13. I like the puppy. + My uncle gave it to me. → I like the puppy which my uncle gave to me. 14. The artist is famous. + We visited her last month. → The artist whom we visited last month is famous. Lesson 8 · 문법 1 · 관계대명사 연습 (15–26) [C] 다음 문장에서 어색한 부분을 찾아 바르게 고쳐 쓰시오. 15. The boy which I like is very smart. → which → who / whom / that 16. This is the gift who my friend sent me. → who → which / that 17. The man whom I met him yesterday was kind. → him 삭제 18. 다음 중 밑줄 친 관계대명사의 생략이 불가능한 것은?
① The bicycle which I bought is blue.
② The man who came to see you is outside.
③ The song that we listened to was quiet.
④ The food which she cooked was delicious. 정답 · ② — 주격 관계대명사 who이므로 생략할 수 없다. [D] 어법상 틀린 문장 고르기 (시험 최다 빈출) 19. 다음 중 어법상 틀린 문장은?
① The house which I live in is very old.
② The student whom everybody likes is smart.
③ The cake which my mom made it was delicious.
④ This is the girl that I told you about. 정답 · ③ — it 삭제. 20. 다음 중 어법상 틀린 문장은?
① Is this the movie that you were talking about?
② The friend whom I wrote a letter is in London.
③ The present which he gave me was beautiful.
④ Look at the cat that is sleeping on the sofa. 정답 · ② — whom I wrote a letter to로 고친다. 21. 다음 중 어법상 올바른 문장의 개수는? (a) The singer whom I like him best is IU. (b) The bicycle which my dad bought for me was stolen. (c) Is that the girl who you invited to the party? (d) This is the house which we built it last year. 정답 · 2개: (b), (c). (a)는 him, (d)는 it 삭제. 22. 다음 중 어법상 틀린 문장을 모두 고르면?
① The book that I bought yesterday was expensive.
② The girl with whom I talked was very kind.
③ The girl with that I talked was very kind.
④ This is the bus which I usually take it.
⑤ The food which she made was delicious. 정답 · ③, ④ — ③ that → whom, ④ it 삭제. 23. 다음 중 관계대명사의 생략이 불가능한 문장은?
① The boy whom you met yesterday is my brother.
② The computer which I am using is fast.
③ This is the boy who saved the poor dog.
④ The dress that she is wearing looks nice. 정답 · ③ — who는 관계절의 주어이다. 24. 다음 빈칸에 들어갈 말이 바르게 짝지어진 것은? This is the chair which / that I sat on. This is the chair on which I sat.
① which - which ② that - that ③ which - that ④ that - which 정답 · ①, ④ — 첫 빈칸은 which/that, 전치사 on 바로 뒤는 which. 25. 두 문장을 한 문장으로 만들 때, 빈칸 (A), (B)에 들어갈 단어로 적절한 것은? The boy is my cousin. + You were playing with him. → The boy (A) who / whom / that you were playing with is my cousin. → The boy with (B) whom you were playing is my cousin. 26. 전치사가 관계대명사 앞으로 오도록 바꾸어 쓰시오. The house which we live in is not big. → The house in which we live is not big. Lesson 8 · 문법 1 · 관계대명사 연습 (27–38) 27. 다음 문장 중 어법상 틀린 것은?
① The girl about whom I told you is my sister.
② The girl about that I told you is my sister.
③ The girl who I told you about is my sister.
④ The girl that I told you about is my sister. 정답 · ② — 전치사 about 뒤에는 that을 쓸 수 없다. whom으로 고친다. 28. 잘못된 부분을 찾아 바르게 고치시오. The house in that he lives is very small. → that → which [E] 조건에 맞게 조건영작 및 통합 유형 29. 제시된 단어만을 모두 사용하여 문장을 완성하시오. (단, 목적격 관계대명사 생략할 것) 내가 지난주에 사간 그 휴대폰은 매우 좋다. (The phone / bought / last week / I / is / great / .) → The phone I bought last week is great. 30. 관계대명사 that만을 사용하여 두 문장을 한 문장으로 만드시오. Look at the boy and his dog. + They are running over there. → Look at the boy and his dog that are running over there. 31. 밑줄 친 that의 쓰임이 보기와 같은 것은? [보기] The movie that I watched yesterday was moving.
① I know that he is an honest boy.
② Look at that building over there.
③ The news that he failed the exam was sad.
④ The cookies that my sister baked were tasty. 정답 · ④ — 목적격 관계대명사. ① 명사절 접속사, ② 지시형용사, ③ 동격절 접속사. 32. 관계대명사 that만 써야 하는 문장이 아닌 것은?
① Look at the girl and the dog _____ are walking together.
② This is the best movie _____ I have ever seen.
③ The man _____ she visited yesterday was her teacher.
④ All _____ glitters is not gold. 정답 · ③ — who/whom/that 가능. 학교 문법의 출제 의도는 ① 사람+동물, ② 최상급, ④ all의 수식을 that으로 구분하는 것이다. 33. 다음 빈칸에 들어갈 수 없는 단어는? Is there anything _____ I can do for you?
① that ② which ③ (생략) ④ what 정답 · ④ — 선행사 anything이 있으므로 선행사를 포함하는 what을 쓸 수 없다. 34. 다음 중 어법상 올바른 문장을 고르시오.
① The student whom I spoke to him was very polite.
② This is the house in which I was born in.
③ The woman with who he works is friendly.
④ The song which we listened to yesterday was famous. 정답 · ④ — ① him 삭제, ② 뒤의 in 삭제, ③ who → whom. 35. 우리말 뜻과 일치하도록 괄호 안의 단어를 바르게 배열하시오. 내가 어제 도서관에서 빌린 그 책은 흥미롭다. (borrowed / yesterday / the library / the book / from / I) → The book I borrowed from the library yesterday is interesting. 36. 두 문장의 의미가 같도록 빈칸에 알맞은 말을 쓰시오. This is the girl. I told you about her. = This is the girl who / whom / that I told you about. = This is the girl about whom I told you. 37. 밑줄 친 부분 중 어법상 틀린 곳을 찾아 기호를 쓰고 바르게 고치시오. The boy ⓐ whom I ⓑ met him ⓒ at the party ⓓ is my cousin. 기호: ⓑ / 수정: met 38. 세 조건에 맞게 영작하시오. 한국어 의미: “내가 어제 만난 그 가수는 노래를 매우 잘 부른다.” 조건 1: 선행사 the singer로 시작할 것. 조건 2: 목적격 관계대명사 whom 또는 who 또는 that 중 하나를 포함할 것. 조건 3: 총 9단어로 완성할 것. → The singer whom I met yesterday sings very well. Lesson 8 · 문법 2 · -thing+형용사 1. -thing+형용사 • 형태와 의미: 보통 영어에서 명사를 꾸며 주는 형용사는 명사 ① 앞에 위치하지만, something, anything, nothing과 같이 - thing으로 끝나는 대명사는 형용사가 대명사 뒤에 위치하여 「-thing+② 형용사」의 순서로 쓰인다. e.g. I heard ③ something interesting from my friend. (나는 친구에게 뭔가 흥미로운 것을 들었다.) Will you cook anything special for the party? (너는 파티를 위해 뭔가 특별한 것을 요리할 거니?) There’s nothing new in this story. (이 이야기에는 새로운 것이 아무것도 없다.) 형태 의미 something+형용사 (뭔가) ~한 것 anything+형용사 (뭔가) ~한 것 〈의문문〉 / ~한 아무것(도 ~않다, 없다) 〈부정문〉
④ nothing+형용사 ~한 아무것(도 ~않다, 없다) • -thing+형용사+to부정사: 명사를 수식하는 to부정사의 형용사적 용법이 함께 쓰일 때는 「-thing+형용사+⑤ to부정사」 형태 로 쓴다. e.g. I want something warm to wear. (나는 ⑥ 입을 따뜻한 것이 필요하다.) something / anything / nothing + 형용사 + to부정사. 예: something hot to drink. something은 긍정문뿐 아니라 권유·요청·긍정 답변을 기대하는 의문문에서도 쓴다. anything은 부정·의문문에서 흔히 쓰이며 긍정문에서는 ‘무엇이든’의 뜻도 있다. [기초문제] 1. 나는 새로운 무언가를 가지고 있다. I have [new / something]. → something new 2. 그녀는 특별한 무언가를 찾고 있다. She is looking for [something / special]. → something special 3. 주머니 안에 차가운 무언가가 있다. There is [cold / something] in the pocket. → something cold 4. 그 영화에는 재미있는 것이 전혀 없다. There is [interesting / nothing] in the movie. → nothing interesting 5. 너 어제 이상한 것 봤니? Did you see [strange / anything] last night? → anything strange 6. 마실 차가운 것 좀 주세요. Give me (something / cold / to / drink). → something cold to drink 7. 읽을 흥미로운 책(무언가)이 있니? Is there (interesting / something / to / read)? → something interesting to read 8. Something special (is / are) waiting for you. 9. (Is / Are) anything wrong with your computer? 10. Nothing (is / are) more important than health. 정답 · 8) is　9) Is　10) is — 단수 취급. [보충문제] 1. 다음 중 어법상 올바른 문장을 고르시오.
① I have interesting something to tell you.
② Do you need anything else?
③ She wants to buy new something for her room.
④ There is cold anything in the fridge. 정답 · ② 2. 다음 중 어법상 틀린 문장을 고르시오.
① I met someone special at the party.
② Is there anything wrong with your phone?
③ He has nothing to do today.
④ We need delicious something for dinner. 정답 · ④ — something delicious로 고친다. Lesson 8 · 문법 2 · -thing+형용사 연습 3. 우리말을 영작한 것으로 알맞은 것은? “나에게는 너와 공유할 흥미로운 무언가가 있어.”
① I have something interesting to share with you.
② I have interesting something to share with you.
③ I have to share with you interesting something.
④ I something interesting have to share with you. 정답 · ① 4. 문장의 괄호 안에서 알맞은 것을 고르시오. “Is there anything (important / importantly) in the letter?” 정답 · important 5. 다음 문장 중 어법상 틀린 것을 모두 고르시오.
① She said nothing unusual.
② Give me something cold to drink.
③ He is looking for new somewhere to live.
④ Do you know famous anybody?
⑤ Everything is ready for the trip. 정답 · ③, ④ — somewhere new / anybody famous. 6. 단어들을 바르게 배열하시오. [to / read / something / interesting / find] → Find something interesting to read. 7. 밑줄 친 부분의 어법이 틀린 문장은?
① She needs something warm to wear.
② I have nothing difficult to solve this math problem.
③ Is there anything wrong with your computer?
④ He wants to eat something delicious. 정답 · ② — nothing이 solve의 목적어 역할도 하므로 뒤에 this math problem을 중복해서 붙이지 않는다. “I have nothing difficult to solve.” 등으로 고친다. 8. 다음 중 어순이 모두 올바른 것을 고르시오.
① something useful to buy　② anything to eat hot
③ cold something to drink　④ interesting anything to watch 정답 · ① 9. 어법상 올바른 것은? ㄱ. I have something to do important. ㄴ. She found nothing interesting in the book. ㄷ. Is there anything else I can do? ㄹ. He needs warm something for the winter. 정답 · ㄴ, ㄷ 10. 빈칸에 something이 들어갈 수 없는 질문(Question)은?
① Did you see _____ strange last night?
② Would you like _____ to drink? (권유의 의미)
③ Is there _____ I can help you with?
④ Can I get you _____ sweet? 정답 · 출제 의도상 ①로 추정. 다만 문맥에 따라 ①·③에도 something이 가능하므로 “들어갈 수 없는” 단 하나의 정답은 확정하기 어 렵다. 11. 주어진 단어를 알맞게 배열하여 문장을 완성하시오. (something / hot / want / I / to / eat) → I want something hot to eat. / I want to eat something hot. 12. 틀린 부분을 고치고 전체 문장을 쓰시오. “There are something wrong with my car.” → There is something wrong with my car. 13. 문장을 완성하시오. [보기: important / tell / I / something / you / to / have] → I have something important to tell you. 14. 알맞은 대명사를 쓰시오. Anything is possible if you try. 15. 같은 의미가 되도록 빈칸을 채우시오. “He didn’t say anything new.” = He said nothing new. 16. 나는 오늘 할 중요한 일(무언가)이 아무것도 없다. I have nothing important to do today. 17. 같은 의미가 되도록 빈칸을 채우시오. “He didn’t find anything useful in the library.” = He found nothing useful in the library. 18. 나에게 말해 줄 흥미로운 무언가가 있니? Do you have anything interesting to tell me? 19. 이번 주말에 읽을 새로운 무언가를 사고 싶다. I want to buy something new to read this weekend. 20. 방 안에는 이상한 것이 전혀 없었다. There was nothing strange in the room. 본문 빈칸 PART 04 Lesson 7 · Natural Wonders · Wild Wonderlands · 본문 A 1. National parks are special places for preserving nature. ➜ 국립 공원은 자연을 지키는 특별한 장소들입니다. 2. They keep many animal and plant species safe. ➜ 그것들은 많은 동물과 식물 종들을 안전하게 지켜 줍니다. 3. At the same time, they let people enjoy the beauty of nature. ➜ 동시에, 그것들은 사람들이 자연의 아름다움을 즐기도록 해 줍니다. 4. Let’s take a look at two famous national parks in South America and Africa. ➜ 남아메리카와 아프리카에 있는 두 유명한 국립 공원을 살펴봅시다. 5. Exploring the Dynamic Nature – Torres del Paine National Park ➜ 역동적인 자연 탐험하기 – 토레스 델 파이네 국립 공원 6. Can you believe you can experience four seasons in one day? ➜ 하루에 사계절을 경험할 수 있다는 것을 믿을 수 있나요? 7. Torres del Paine National Park in Chile is the perfect place for this. ➜ 칠레에 있는 토레스 델 파이네 국립 공원은 이것을 위한 완벽한 장소입니다. 8. It is famous for its dynamic weather. ➜ 그곳은 역동적인 날씨로 유명합니다. 9. It can be sunny, rainy, or snowy in a single day! ➜ 하루에 맑거나, 비가 오거나, 눈이 올 수 있습니다! 10. Also, you can see mountains, lakes, and even a big ice field in this park. ➜ 또한, 이 공원에서는 당신은 산, 호수, 심지어 커다란 빙원도 볼 수 있습니다. 11. For example, Grey Glacier, one of the most wonderful glaciers in Chile, welcomes you in the park. ➜ 예를 들어, 칠레에서 가장 멋진 빙하들 중 하나인 Grey Glacier가 공원에서 여러분을 환영합니다. 12. It is exciting to walk on the glacier and enjoy the view. ➜ 빙하 위를 걷고 경치를 즐기는 것은 신납니다. Lesson 7 Lesson 7 · Natural Wonders · Wild Wonderlands · 본문 B 1. The park is making efforts to preserve its natural beauty. ➜ 그 공원은 자연의 아름다움을 보존하기 위해 노력하고 있습니다. 2. First, it controls the number of visitors, so they should book to enter the park. ➜ 첫째, 그곳은 방문객 수를 통제하여 방문객들은 공원에 입장하려면 예약해야 합니다. 3. Second, only some park trails are open to visitors for hiking. ➜ 둘째, 공원 산책로 일부만 방문객들의 하이킹을 위해 개방되어 있습니다. 4. Third, the park makes visitors take their trash with them when they leave. ➜ 셋째, 그 공원은 방문객들이 떠날 때 쓰레기를 가져가도록 합니다. 5. There’s one more thing: the hotels in the park do not use plastic and only use eco-friendly products. ➜ 한 가지가 더 있습니다. 공원 안의 호텔들은 플라스틱을 사용하지 않고 친환경 제품만 사용합니다. 6. Discovering the Big Five – Kruger National Park ➜ Big Five 발견하기 – 크루거 국립 공원 7. Have you heard about the Big Five? ➜ Big Five에 대해 들어 본 적이 있나요? 8. They are lions, leopards, elephants, rhinos, and buffaloes. ➜ 그것들은 사자, 표범, 코끼리, 코뿔소, 그리고 버팔로입니다. 9. You can see them all in Kruger National Park in South Africa. ➜ 남아프리카 공화국에 있는 크루거 국립 공원에서 그것들을 모두 볼 수 있습니다. 10. Many wild animals move around this park freely. ➜ 많은 야생 동물들이 이 공원에서 자유롭게 돌아다닙니다. 11. If you are lucky, you can see rhinos which are sleeping under baobab trees. ➜ 만약 운이 좋으면, 바오밥나무 아래에서 자고 있는 코뿔소들을 볼 수 있습니다. Lesson 7 · Natural Wonders · Wild Wonderlands · 본문 C 1. However, the Big Five are in danger because of too much hunting. ➜ 하지만 Big Five는 과도한 사냥 때문에 위험에 처해 있습니다. 2. To protect the Big Five, the park has been a “no-hunting” area from the start. ➜ Big Five를 보호하기 위해, 그 공원은 처음부터 ‘사냥 금지’ 지역이었습니다. 3. These days, drones and helicopters are used to monitor hunting activities. ➜ 요즘에는 드론과 헬리콥터가 사냥 활동을 감시하기 위해 사용됩니다. 4. Also, the park runs many nature volunteer programs. ➜ 또한, 그 공원은 많은 자연 자원봉사 프로그램을 운영합니다. 5. Volunteers monitor signs of illegal hunting activities and remove animal traps. ➜ 자원봉사자들은 불법 사냥 활동의 징후를 감시하고 동물 덫을 제거합니다. 6. National parks preserve the Earth’s past, present, and future. ➜ 국립 공원은 지구의 과거, 현재, 그리고 미래를 보존합니다. 7. Thanks to them, future generations will also be able to experience many natural wonders. ➜ 그것들 덕분에, 미래 세대들도 많은 자연의 경이로움을 경험할 수 있을 것입니다. 본문 빈칸 PART 05 Lesson 8 · The Magic of Giving · Sparrow Socks · 본문 A 1. Years ago, there lived a boy in a small village in Scotland. His name was Angus. ➜ 여러 해 전, 스코틀랜드의 작은 마을에 한 소년이 살았다. 그의 이름은 Angus였다. 2. A sparrow, Bruce, was his close friend. ➜ 참새 Bruce는 그의 친한 친구였다. 3. When they played together, Bruce flew into Angus’s pocket. ➜ 그들이 함께 놀 때, Bruce는 Angus의 주머니로 날아들었다. 4. Angus’s father owned a small store that made socks, and Angus sometimes helped him. ➜ Angus의 아버지는 양말을 만드는 작은 가게를 소유했고, Angus는 가끔 그를 도왔다. 5. The store used to have many customers, but recently it lost a lot of them. ➜ 그 가게에는 예전에 손님이 많았지만, 최근에는 많은 손님을 잃었다. 6. Villagers started buying socks from a big department store. ➜ 마을 사람들은 큰 백화점에서 양말을 사기 시작했다. 7. One cold winter morning, Bruce and the other sparrows were in a tree. ➜ 어느 추운 겨울 아침, Bruce와 다른 참새들이 나무 위에 있었다. 8. The tree was covered with snow. ➜ 그 나무는 눈으로 덮여 있었다. 9. Angus saw Bruce raising his feet one by one. ➜ Angus는 Bruce가 발을 하나씩 들어 올리는 것을 보았다. 10. “Your feet are cold! Maybe I can help you,” said Angus. ➜ “너의 발이 차갑구나! 아마 내가 너를 도와줄 수 있을 거야.”라고 Angus가 말했다. 11. After the store closed for the night, Angus sat in front of the sock machine. ➜ 가게가 밤에 문을 닫은 후, Angus는 양말 기계 앞에 앉았다. 12. Soon, a tiny pair of socks popped out. ➜ 곧 작은 양말 한 켤레가 튀어나왔다. 13. Warm and soft winter socks with red stripes! ➜ 빨간 줄무늬가 있는 따뜻하고 부드러운 겨울 양말이었다! 14. Angus made more and more socks because he knew other sparrows were shaking in the cold weather, too. ➜ Angus는 다른 참새들도 추운 날씨에 떨고 있다는 것을 알았기 때문에 점점 더 많은 양말을 만들었다. 15. But he didn’t realize that he used up all the wool in the machine. ➜ 그러나 그는 기계 안의 털실을 모두 써 버렸다는 것을 깨닫지 못했다. Lesson 8 Lesson 8 · The Magic of Giving · Sparrow Socks · 본문 B 1. The next day, the sparrows were wearing something warm. ➜ 다음 날, 참새들은 뭔가 따뜻한 것을 신고 있었다. 2. They were wearing cute socks with red stripes! ➜ 그들은 빨간 줄무늬가 있는 귀여운 양말을 신고 있었다! 3. As soon as people saw the socks, they hurried to the department store. ➜ 사람들은 그 양말을 보자마자 백화점으로 서둘러 갔다. 4. They asked, “We want the socks that the sparrows are wearing. Where can we buy them?” ➜ 그들은 “우리는 참새들이 신고 있는 양말을 원해요. 그것을 어디에서 살 수 있나요?”라고 물었다. 5. But they couldn’t find the same socks anywhere. ➜ 하지만 그들은 어디에서도 같은 양말을 찾을 수 없었다. 6. At the same time in the sock store, Angus’s father called Angus. ➜ 같은 시간에 양말 가게에서 Angus의 아버지가 Angus를 불렀다. 7. “Son, something strange happened. Someone took all of our wool.” ➜ “아들아, 뭔가 이상한 일이 일어났어. 누군가 우리의 털실을 모두 가져갔어.” 8. Angus honestly said, “Sorry, I made socks for sparrows. They were shaking in the cold weather.” ➜ Angus는 정직하게 말했다. “죄송해요, 제가 참새들을 위한 양말을 만들었어요. 그들이 추운 날씨에 떨고 있었거든요.” 9. Bruce was listening silently in Angus’s pocket. ➜ Bruce는 Angus의 주머니 속에서 조용히 듣고 있었다. Lesson 8 · The Magic of Giving · Sparrow Socks · 본문 C 1. After a while, Bruce brought the other sparrows together and said, “We need to return the socks.” ➜ 잠시 후, Bruce는 다른 참새들을 모아 말했다. “우리는 양말을 돌려줘야 해.” 2. “Do we have to? We like these socks,” said the other sparrows. ➜ “우리가 그래야 해? 우리는 이 양말이 좋은데.”라고 다른 참새들이 말했다. 3. Bruce added, “Angus is in trouble because of our socks. We should help him.” ➜ Bruce는 “Angus가 우리의 양말 때문에 곤경에 빠졌어. 우리는 그를 도와야 해.”라고 덧붙였다. 4. The sparrows agreed, “Alright then.” ➜ 참새들은 “그럼 알겠어.”라고 동의했다. 5. The sparrows flew into the sock store and piled their socks on the table. ➜ 참새들은 양말 가게로 날아들어가 탁자 위에 양말을 쌓았다. 6. Knock, knock, knock. Just then, people rushed into the store. ➜ 똑똑똑. 바로 그때, 사람들이 가게로 서둘러 왔다. 7. Someone said, “Finally! These are the socks that I want!” ➜ 누군가 “드디어! 이것들이 내가 원하는 양말이야!”라고 말했다. 8. The store was crowded with customers. ➜ 가게는 손님들로 붐볐다. 9. “Angus, your kind heart has come back to us as a great gift!” said his father. ➜ “Angus, 너의 친절한 마음이 좋은 선물로 우리에게 돌아왔어!”라고 그의 아버지가 말했다. 10. Now the villagers are all wearing warm socks with red stripes. ➜ 이제 마을 사람들 모두 빨간 줄무늬가 있는 따뜻한 양말을 신고 있다. 11. Oh, dogs and cats are, too! ➜ 오, 개와 고양이들도 마찬가지다! 추가 지문 PART 06 Lesson 7 · More Reading 1 · 기본 소재: 생태전환 > 자연환경과 생태 윤리 · 사용 어휘 수: 108단어 A Window to the Past – Taeanhaean National Park A park with both land and sea! This describes Taeanhaean National Park, a coastal park in Korea. This park has mudflats, sand *dunes, and beautiful islands. The mudflats are like treasures from nature. They offer food and places to live for all living things in the sea. Also, it is interesting to visit Sinduri Coastal Sand Dune because of its history. It was formed about 15,000 years ago. Since then, it has formed a unique ecosystem with unique plants and animals. Now, this park attracts many visitors. But it may close for visits for **conservation purposes. So if you’re planning to go, make sure to avoid that period. *dune 모래 언덕　**conservation 보호, 관리 1　윗글의 내용과 일치하도록 표를 완성해 봅시다. Taeanhaean National Park Features Sinduri Coastal Sand Dune mudflats, sand dunes, • formed about 15,000 years ago beautiful islands • unique ecosystem with unique plants and animals 2　윗글의 내용과 일치하면 T에, 일치하지 않으면 F에 표시해 봅시다. 1. Taeanhaean National Park has parts of both land and sea.　[ T / F ] 2. The mudflats in Taeanhaean National Park are homes for sea animals.　[ T / F ] 3. Taeanhaean National Park is always open to visitors.　[ T / F ] 정답 · 1) T　2) T　3) F — 보호·관리 목적으로 문을 닫을 수 있다. Lesson 7 · More Reading 2 · 심화 소재: 생태전환 > 자연환경과 생태 윤리 · 사용 어휘 수: 129단어 Galápagos National Park Galápagos National Park is one of the greatest natural treasures in the world. ① It is Ecuador’s first national park and a UNESCO World Heritage Site. ② It is made up of 13 large islands, 17 small islands, and 40 rocks. The park is famous for its giant tortoises, colorful birds, and clear blue waters. ③ It has special plants and animals that you can’t find anywhere else. Charles Darwin visited the Galápagos in the 1830s and observed these amazing creatures. His studies helped him create the idea of evolution. ④ It explains how animals change to fit their surroundings over time. Lesson 7 More Reading Conservation Note • may close for visits for conservation purposes • check for closures before visiting Today, the park is carefully monitored and managed, and ⑤ it also attracts visitors who want to explore its natural beauty. They come to see amazing wildlife and learn about Darwin’s discoveries. 1　윗글을 읽고 답할 수 없는 질문을 골라 봅시다.
① Where is Galápagos National Park?
② What is Galápagos National Park famous for?
③ How many animal species are there in Galápagos National Park?
④ When did Charles Darwin visit the Galápagos?
⑤ Why do people visit Galápagos National Park today? 정답 · ③ — 동물 종의 수는 제시되지 않았다. ① Ecuador　② giant tortoises, colorful birds, and clear blue waters　④ in the 1830s　⑤ to see amazing wildlife and learn about Darwin’s discoveries. 2　밑줄 친 ①–⑤ 중 가리키는 대상이 나머지와 다른 것을 골라 봅시다. ①　　　　②　　　　③　　　　④　　　　⑤ 정답 · ④ — the idea of evolution. ①②③⑤는 Galápagos National Park를 가리킨다. 추가 지문 PART 07 Lesson 8 · More Reading 1 · 기본 소재: 학교생활 > 친구 · 사용 어휘 수: 54단어 The ABCs of Friendship A true friend ... Accepts you as you are. Believes in “you.” Calls you just to say “Hi.” Doesn’t give up on you. Embraces you as you are. Forgives your mistakes. 1　윗글의 빈칸에 가장 알맞은 것을 골라 제목을 완성해 봅시다.
① Family
② Success
③ Friendship
④ Health 정답 · ③ Friendship 2　윗글에서 진정한 친구에 관해 언급되지 않은 것을 골라 봅시다.
① 친구를 믿어 준다.
② 친구를 더 낫게 변화시킨다.
③ 친구의 실수를 용서한다.
④ 조건 없이 베푼다. 정답 · ② Lesson 8 More Reading Gives unconditionally. Helps you. Invites you over. Just wants to “be” with you. Keeps you close at heart. Loves you for who you are. Lesson 8 · More Reading 2 · 심화 소재: 이야기 > 우화 · 사용 어휘 수: 157단어 The Lion and the Mouse One day, a lion was sleeping in the forest, and a little mouse accidentally ran across his tail. The lion woke up, and he caught the mouse. The mouse begged the lion, “Please let me go! I will repay your kindness someday.” The lion said, “How can you help me? You are so tiny.” However, he let the mouse go, thinking that the mouse meant no harm. A few days later, the lion was caught in a hunter’s net. He struggled to escape, but no one could hear him. The little mouse heard the lion and ran to find him. The mouse chewed through the ropes of the net with his sharp teeth, and soon the lion was free. The lion was grateful and said to the mouse, “Thank you for saving me. I didn’t think you could help me, but you did.” The mouse smiled and replied, “Even the smallest friends can be a great help.” 1　윗글의 내용과 가장 어울리는 속담을 골라 봅시다.
① Walls have ears.
② Blood is thicker than water.
③ Don’t cry over spilt milk.
④ The early bird catches the worm.
⑤ One good turn deserves another. 정답 · ⑤ One good turn deserves another. — 선행은 선행으로 보답받는다. 2　윗글의 내용과 일치하지 않는 것을 2개 골라 봅시다.
① The lion woke up because he was hungry.
② The mouse promised to help the lion someday.
③ The lion thought the mouse was too small to help him.
④ The mouse broke the ropes of the net with a knife.
⑤ The lion thanked the mouse for saving him. 정답 · ①, ④
① 쥐가 사자의 꼬리 위를 달려 지나가자 사자가 깨어났다.
④ 쥐는 칼이 아니라 날카로운 이빨로 그물의 밧줄을 끊었다.', 40, 3);
