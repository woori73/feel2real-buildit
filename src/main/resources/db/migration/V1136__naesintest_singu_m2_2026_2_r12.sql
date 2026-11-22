-- 내신 실전 연습(/naesintest) — 신구중 2학년 2026학년도 2학기 기말 대비 F2R 예상문제 12회(Lesson 7·8 종합 Final 2회).
-- 원본: F2R이 만든 예상문제 문제지 PDF(9쪽)와 정답·해설(검수 3회·5차 수정본). V1121(11회)과 같은 방식으로 문항 그림(static/naesin/singu-m2-2026-2/r12)을 쓴다.
-- 출제 범위: 2학기말 학습지 필기본 Lesson 7·8 전 범위. 40문항, 배점 합계 100점(5점×3, 4점×4, 3점×3, 2점×30), 모두 고르기 4문항.
-- 한 개 고르기 NAESIN_MC5(A~E), "모두 고르면" NAESIN_MC5_MULTI(answer_key 'B,D' 형식).

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-SGM2-2602-R12', 'NAESIN', '신구중 2학년 2학기 기말 12회 · Lesson 7·8 종합 Final 예상문제 2회', 'Lesson 7·8 학습지 필기본 전 범위 · 40문항 · 20분 · 100점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 틀린 문제 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @naesin_sg_r12 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-SGM2-2602-R12');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q01.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 대화 내용 일치 (2점)
I like the T-shirt better. / There is a nice picture of the festival on it.(it = the T-shirt) 두 문장을 합친 ③. ① 먼저 제안한 사람은 G ② cap이 아니라 T-shirt ④ 소년은 자기 선호를 말하지 않음 ⑤ 이유를 말함
근거: 필기본 3쪽 A-3', 1, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q02.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p01-02.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ와 같은 의미 (2점)
ⓐ·⑤ 사다, 구하다. ① 도착하다(get to) ② ~해지다 ③ 이해하다 ④ 일어나다(get up)
근거: 필기본 3쪽 A-3', 2, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q03.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영어 발문 이유 (2점)
B: I don’t want to wash my face again.(→ would have to clean his face once more) + G: Playing VR games is OK, too.(→ doesn’t mind) 두 말을 합쳐야 한다. ① 소녀는 face painting을 더 좋아함 ③④⑤ 대화에 없는 내용.
근거: 필기본 3쪽 A-1', 3, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q04.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 짝지은 대화 어색 (2점)
① 해변을 더 좋아하는 이유를 묻는데 “바다를 좋아하지 않는다”고 답해 앞뒤가 맞지 않는다(필기본 4쪽: 알맞은 이유는 I don’t like walking.). ②③⑤ 선호와 이유가 맞는 짝 ④ 꽃을 보고 선호를 묻는 자연스러운 흐름
근거: 필기본 3쪽 A-2·A-4, 4쪽 Speaking Practice', 4, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q05.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸 어디에도 없는 단어 (2점)
차례로 hear / forget / start / ride. participate는 뒤에 in이 있어야 하고 뜻도 맞지 않아 어느 빈칸에도 들어갈 수 없다(지문의 participating에 끌리지 않기)
근거: 필기본 3쪽 B', 5, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q06.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 그림+지각동사 (2점)
④ hear + 목적어 + 동사원형(sing), 그림에서 Sujin은 마이크를 들고 노래한다. ① to fly → fly/flying ② barked → bark/barking. heard the dog barked는 ‘직접 들은’ 것을 나타내는 지각동사 구문(hear + 목적어 + 동사원형/-ing)이 아님 ③ Yuna는 책을 읽고 있다(내용 오류) ⑤ Jake는 공을 차고 있다(내용 오류)
근거: 필기본 7쪽 5형식 표 예4(heard/saw + 목적어 + 동사원형/-ing), 7번 (2)(5)', 6, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r12/q07.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A,D', 1, '정답 ①, ④ · 바꿔 쓸 단어 모두 (3점)
giant = very large. huge, large와 바꿔 쓸 수 있다. ② 아주 작은 ③ 좁은 ⑤ 유명한
근거: 필기본 1쪽 giant(a. very large), 13쪽 A huge dinosaur', 7, 3),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q08.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · ⓐ~ⓒ 전치사 짝 (2점)
from far away(멀리서), stare at(~을 빤히 보다), be famous for(~로 유명하다). ① ⓑ to가 틀림 ③ ⓐ in이 틀림 ④ ⓒ to가 틀림 ⑤ ⓐ in, ⓑ to, ⓒ of가 모두 틀림
근거: 필기본 14쪽 22번 roar 예문(from far away), 14쪽 26번 stare 예문, 1쪽 reindeer 예문', 8, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q09.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ~ⓒ 짝짓기 표 (2점)
tell + 목적어 + to부정사(to bring), let + 목적어 + 동사원형(play), want + 목적어 + to부정사(to run). ① ⓐ bring, ⓒ run이 틀림 ② ⓑ to play가 틀림 ④ ⓒ running이 틀림 ⑤ ⓐ bringing, ⓑ to play, ⓒ run이 틀림
근거: 필기본 7쪽 5형식 표 예2(make/have/let + 목적어 + 동사원형), 예3(wanted/told/asked + 목적어 + to부정사), 7번 (3)(4)', 9, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r12/q10.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C,E', 1, '정답 ③, ⑤ · 문장 전환 옳지 않은 것 모두 (4점)
③ 시제가 바뀜(was → is. It was not easy to …가 옳다) ⑤ 의미상의 주어는 for + 목적격이므로 for she → for her(문법 오류). ① 동명사 주어를 to부정사 진주어로 옮긴 옳은 전환 ② 의미상의 주어 for children을 to부정사 바로 앞에 둔 옳은 전환 ④ that절 진주어
근거: 필기본 5쪽(주어로 쓰인 to부정사·동명사, 가주어 It, 의미상의 주어 for + 목적격), 6쪽(It … to / It … that), 8쪽 4번(for them)', 10, 4),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q11.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 전환 후 n번째 단어 (5점)
It(1) was(2) careless(3) of(4) him(5) to(6) leave(7) the(8) window(9) open(10) all(11) night(12). → 네 번째 단어는 of. 주어인 that절(That he left the window open all night)을 뒤로 보내고 가주어 It을 쓴다. to부정사로 바꾸면 that절의 주어 he는 의미상의 주어가 되는데, careless는 사람의 성격을 나타내는 형용사이므로 for가 아니라 of + 목적격(of him)을 쓰고 to부정사 바로 앞에 둔다. ① for him으로 쓰면 for(성격 형용사에는 쓰지 않음) ③ him은 다섯 번째 ④ leave는 일곱 번째(의미상의 주어를 빼고 It was careless to leave …로 쓰면 누가 했는지가 빠져 원래 문장과 뜻이 달라지고, 그때의 네 번째 단어 to는 선택지에 없다) ⑤ It was careless that he left …는 to부정사가 없어 조건에 맞지 않음
근거: 필기본 5쪽 3. 문장이 주어로 들어간 경우(That she passed the exam surprised everyone.), [2단계] that절 주어(It isn’t easy that you exercise every day.) → [3단계] 의미상의 주어(It isn’t easy for you to exercise every day. / It is nice of you to help them.), ※ 성격 형용사 nice, kind, smart, wise, foolish, honest, careless + of + 목적격', 11, 5),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q12.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p12-14.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ⓐ~ⓔ 내용·어법 짝 (4점)
어법 오류: ⓑ is taken place(→ takes place, take place는 수동태로 쓰지 않음) ⓔ say(→ saying, symbolizes의 목적어 자리이므로 동명사) / 내용 오류: ⓓ 파야는 축제의 주인공(The stars of the festival)이고 마지막 날 그것들을 태운다고 했으므로 ‘도시에서 하나도 찾기 어렵다’는 말은 앞뒤와 맞지 않는다(본문: You can find hundreds of them all over the city.). 어법 오류 ⓑ·ⓔ를 지워도 ①(ⓐ, ⓒ)과 ⑤(ⓒ, ⓓ)가 남으므로 ⓓ의 내용을 앞뒤 문장과 맞춰 봐야 한다
근거: 필기본 9쪽 Reading 1 ①②④⑤⑦', 12, 4),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q13.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p12-14.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 영어 발문 이유 (2점)
Burning them symbolizes … goodbye to old things and hello to a new start. → 파야를 태우는 것은 오래된 것과 작별하고 새로 시작한다는 뜻이다(④). ①②③⑤ 글에 없는 내용(⑤는 Reading 3 죽은 자의 날의 내용)
근거: 필기본 9쪽 Reading 1 ⑦', 13, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q14.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p12-14.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글 내용 일치 (2점)
⑤ the fallas are burned on the last day of the festival을 바꿔 씀. ① the coming of spring(봄이 오는 것) ② March 15 to 19(5일) ③ parades and fireworks 둘 다 ④ giant wooden statues
근거: 필기본 9쪽 Reading 1 ①②③④⑥', 14, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q15.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p15-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·⑤ call A B의 B(목적격 보어): it = the Midsummer festival, our science teacher = Dr. Robot. ①③ 수여동사 뒤의 직접목적어(me ≠ a sandwich, us ≠ his new bike) ② call A B의 A(목적어) ④ make + 목적어 + 형용사의 목적어
근거: 필기본 7쪽 B. call (5형식) 표 예5·연습 1~3, 10쪽 ⑩ They call it the Midsummer festival.(문법 설명: call + 목적어 + 목적격 보어)', 15, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q16.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p15-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓔ 의미 (2점)
ⓐ 특히 ⓑ 일 년 중 낮이 가장 긴 날 ⓒ 6월 19일과 25일 사이의 ⓓ hardly: 거의 ~않다 ⓔ 밤새도록
근거: 필기본 10쪽 Reading 2 어휘·해석', 16, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q17.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p15-17.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글 내용 일치 (2점)
① starts on the Friday evening … and ends on the Sunday → 금요일 저녁에 시작해 이틀 뒤 일요일에 끝난다. ② the longest day ③ 6월 19일과 25일 사이의 금요일 ④ a tall pole(기둥) 주위에서 ⑤ 겨울이 길고 어두워 여름을 반긴다
근거: 필기본 10쪽 Reading 2 ⑨⑪⑫⑬', 17, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q18.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p18-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓔ 활용 의미 어색 (5점)
④ cemeteries는 ‘묘지’이다. 저녁거리로 신선한 과일과 채소를 사러 가는 곳은 묘지가 아니라 시장이므로 의미가 어색하다. ① 일 년에 한 번, 보통 8월 여름 방학에 ② 새 선생님을 맞이하다 ③ 체육관에 작은 무대를 설치하다 ⑤ 유령처럼 분장하고 집집마다 다닌다. 나머지는 자연스럽다
근거: 필기본 11쪽 Reading 3 어휘(once a year, set up 차리다·설치하다, cemetery 묘지, dress up)', 18, 5),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q19.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p18-19.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글 내용 일치 (2점)
⑤ In some areas, people dress up like skeletons and hold big parades.를 바꿔 씀. ① 제단은 집에 차린다(set up altars in their homes) ② 죽은 사람들이 집으로 돌아온다고 믿는다 ③ 죽은 사람들을 기억하는 것이 중요하다고 믿는다 ④ 해골처럼 분장하고(dress up like skeletons) 퍼레이드를 벌인다
근거: 필기본 11쪽 Reading 3 ⑭⑯⑰⑱⑲', 19, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q20.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 영영 풀이 찾을 수 없는 것 (4점)
① reindeer ③ participate ④ costume(s) ⑤ possible ② parade(윗글에 없음. Reading 1·3에 나옴)
근거: 필기본 12쪽 Reading 4, 1쪽 Word List 영어 뜻풀이', 20, 4),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r12/q21.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,E', 1, '정답 ②, ⑤ · 글 내용 일치 모두 (4점)
② At the end of February ⑤ many special events such as the Running of the Reindeer(여러 특별 행사 중 하나). ① one of the biggest(가장 큰 것들 중 하나) ③ fun costumes(재미있는 복장) ④ 순록을 타는 것이 아니라 순록보다 빨리 달리려고 한다
근거: 필기본 12쪽 Reading 4 ㉑㉔㉕', 21, 4),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q22.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p20-22.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ⓐ와 같은 의미 (2점)
ⓐ·③ 그저, 단지(only). ① 방금, 막 ② 딱, 정확히 ④ 꼭(~와 똑같이) ⑤ 바로, 딱. 선택지는 모두 부사
근거: 필기본 12쪽 ㉗ Everyone is just having fun!(모두가 그저 즐기고 있을 뿐이다), 18쪽 완료 just(막, 방금)', 22, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸 어디에도 없는 단어 (2점)
차례로 celebrate / symbolize / cause / breathe. stare(빤히 쳐다보다)는 어느 빈칸에도 뜻이 맞지 않는다
근거: 필기본 1쪽 celebrate 예문, 2쪽 symbolize 예문, 13쪽 6번 cause(v. to make something happen), 13쪽 4번 breathe(v. to take air into your body and let it out again), 14쪽 26번 stare', 23, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓒ 짝짓기 표 (2점)
경험을 나타내는 평서문에는 never(ever는 의문문에서), has 뒤에는 과거분사 written(wrote는 과거형). ⓒ when I was seven은 이미 끝난 과거의 한 시점이므로 현재완료(have moved)를 쓸 수 없고 단순과거 moved를 쓴다. ① ⓐ ever ② ⓑ wrote ③ ⓒ have moved ⑤ 셋 다 틀림
근거: 필기본 17쪽 과거 시제(특정 과거 시점을 나타내는 어구 ago, when, last month …와 함께), 18쪽 1-(2)(과거형 / 과거분사), 2. 현재완료의 형태 have/has + p.p., 3. 경험 never·ever 예문, 19쪽 복습 메모', 24, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 그림 <보기> 빈칸 어디에도 없는 것 (2점)
차례로 lost / finished / has, up. 첫째 그림의 사람은 빈 주머니를 뒤집어 보이며 지갑을 찾고 있으므로 지갑을 잃어버린 것이다(lost). found는 문법으로는 첫째 빈칸에 들어갈 수 있지만 그림과 맞지 않아 어느 빈칸에도 들어갈 수 없다
근거: 필기본 18쪽 결과 예문(I have lost my wallet.), 20쪽 4번·13번', 25, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · Ⓐ 어법상 쓰임 (2점)
Ⓐ·⑤ 완료(already, just). ①④ 계속(since, for) ②③ 경험(once, twice)
근거: 필기본 17쪽 예 2(He has already left.), 18쪽 현재완료의 4가지 의미', 26, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 짝지은 대화 어색 (2점)
④ How long …?은 알고 지낸 기간(계속)을 묻는데 만난 횟수(경험)로 답해 어색하다. I have known him for three years.처럼 답해야 한다. ① 경험 ② 완료(just) ③ 결과(has gone: 가서 지금 없음) ⑤ 경험(twice)
근거: 필기본 18쪽 현재완료의 4가지 의미(계속 / 경험 / 완료 / 결과)', 27, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q28.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · Ⓐ 어법상 쓰임 (2점)
Ⓐ·① 주격 관계대명사(뒤에 동사가 바로 온다: who was / who founded). ②③④⑤ 목적격 관계대명사(뒤에 주어 + 동사: who I met, who many readers love, who we respect, who I told you about)
근거: 필기본 21쪽 1문단, 12쪽 Grammar Point ㉓의 “(주격 / 목적격) 관계대명사절” 표기', 28, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q29.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글 내용 일치 (2점)
(1920–2012) → 90년 넘게 살았다. ① Waukegan에서 태어남(도서관은 어린 시절 책을 읽은 곳) ② educated himself through books(책으로 독학) ④ 20th century ⑤ science fiction and fantasy stories 둘 다
근거: 필기본 21쪽 1문단', 29, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q30.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p28-30.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · ⓐ~ⓔ 의미 (2점)
ⓐ ~에서 태어났다 ⓑ 어린 시절의 대부분을 책을 읽으며 보냈다 ⓒ 대학에 전혀 다니지 않았다 ⓓ educate oneself: 독학하다 ⓔ 가장 영향력 있는 작가들 중 한 명
근거: 필기본 21쪽 1문단', 30, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q31.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p31-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · Ⓐ의 예 (3점)
question this system: 정해진 제도를 그대로 따르지 않고 의문을 품다(question은 동사). ②가 그 예. ① 시험 문제에 답하는 것 ③ 의문 없이 따르는 것(반대) ④⑤ 제도에 의문을 품는 것과 관계없음
근거: 필기본 21쪽 2문단(begins to question this system)', 31, 3),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q32.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p31-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 답할 수 없는 것 (3점)
① The Illustrated Man (1951)이 Fahrenheit 451 (1953)보다 먼저 ② “firemen” ③ a future society(책이 금지된 미래 사회) ⑤ 2004 + 3 = 2007, a Pulitzer Prize Special Citation. ④ 금지된 이유는 글에 없음
근거: 필기본 21쪽 2~3문단', 32, 3),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q33.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p31-33.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ~ⓔ 의미 (2점)
ⓐ ~에 초점을 맞추다 ⓑ 인간의 선택이 미래를 어떻게 만드는지 ⓒ 많은 상을 받았다 ⓓ ~로 기억된다(수동태) ⓔ with: ~으로(수단)
근거: 필기본 21쪽 3문단', 33, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q34.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p34-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ~ⓒ 전치사 짝 (2점)
explain A with B(B로 A를 설명하다), through the work of(~의 연구를 통해), be sensitive to(~에 민감하다)
근거: 필기본 22쪽 1문단', 34, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q35.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p34-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · Which is true (2점)
① The butterfly effect is part of chaos theory.(나비 효과는 혼돈 이론의 일부 → 혼돈 이론이 나비 효과를 포함한다) ② a very small change → a much bigger change ③ meteorologist(기상학자) ④ very sensitive to small changes ⑤ in the 1960s
근거: 필기본 22쪽 1문단', 35, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q36.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p34-36.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · ~ 이유로 가장 적절한 것 (2점)
People often explain it … a butterfly. The butterfly moves its wings, and later the weather far away can change. → 나비의 작은 날갯짓(작은 변화)이 나중에 먼 곳의 날씨(큰 변화)를 바꿀 수 있다는 것으로 나비 효과를 보여 준다. ①②④⑤ 글에 없는 내용
근거: 필기본 22쪽 1문단, 1번 문장 삽입의 주어진 문장(The butterfly moves its wings, and later the weather far away can change. 정답 위치 ⓐ)', 36, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5_MULTI', '[[img:/naesin/singu-m2-2026-2/r12/q37.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p37-39.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B,D', 1, '정답 ②, ④ · 의미와 쓰임이 ⓐ, ⓑ와 같은 것 모두 (5점)
ⓐ late: 부사 ‘늦게’, ⓑ trip: 명사 ‘여행’. ②④ 부사 + 명사. ① late 형용사(늦은) ③ trip 동사(발이 걸려 넘어지다) ⑤ late 형용사
근거: 필기본 22쪽 2문단', 37, 5),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q38.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p37-39.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 답할 수 없는 것 (2점)
① meeting a new friend or missing an important event ③ Ray Bradbury ④ our decisions ⑤ the world has changed completely. ② Eckels가 시간 여행을 떠난 이유는 글에 없음
근거: 필기본 22쪽 2~3문단', 38, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q39.webp]]', '[[img:/naesin/singu-m2-2026-2/r12/p37-39.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · ~ 이유로 가장 적절한 것 (2점)
one small choice can change many things in the future. For example, arriving late to school might lead to meeting a new friend or missing an important event. → 학교에 늦는 일은 ‘작은 선택 하나가 미래의 많은 것을 바꿀 수 있다’는 앞 문장의 예이다. ② 늦지 말라는 충고가 아님 ③ 둘의 중요도를 비교하지 않음 ④ Eckels는 그 뒤에 나오는 다른 예 ⑤ 작은 선택이 큰 결과로 이어진다는 글의 내용과 반대
근거: 필기본 22쪽 2문단(In everyday life, … one small choice can change many things in the future. For example, arriving late to school …)', 39, 2),
(@naesin_sg_r12, 'EN', 'NAESIN_MC5', '[[img:/naesin/singu-m2-2026-2/r12/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · ⓐ와 같은 의미 (2점)
ⓐ·⑤ ~쪽으로, ~을 향하여(방향). ① ~에게(대하는 태도) ② ~ 무렵(시간) ③ ~을 위해(목적) ④ ~에 대한(감정의 대상). 선택지는 모두 전치사
근거: 필기본 14쪽 29번 toward(prep. in the direction of someone or something) 예문', 40, 2);
