-- TestPrep TOEIC 연습 세트 TP-TOEIC-RC-02 — TOEIC Reading Practice 02 — Part 5·6·7 (독해 추가 세트 2)
-- 문항 100개, F2R 자체 제작. ETS 공식 문항·지문과 후기 속 복원 문항은 쓰지 않았다(출제 포인트·유형·길이·상황만 참고).
-- 스키마는 바꾸지 않는다: 기존 tp_exams / tp_questions 에 kind='TOEIC' 행만 추가한다.
-- section: LISTENING / READING, task_type: TOEIC_P2 ~ TOEIC_P7, order_no = 실제 TOEIC 문항 번호(7~200).
-- 듣기(TOEIC_P2~P4)는 audio_mode='TTS' — 화면이 body_text(대본)를 브라우저 음성으로 읽는다. Part 1(사진)은 없다.
-- 문항 원본·생성기: naesin-workspace/testprep/toeic (tools/build_sql.py). 설계: docs/design/testprep-toeic.md
-- exam_id는 AUTO_INCREMENT에 의존하지 않고 exam_code로 회수한다.

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES
('TP-TOEIC-RC-02', 'TOEIC', 'TOEIC Reading Practice 02 — Part 5·6·7 (독해 추가 세트 2)', 'Part 5 (30) · Part 6 (16) · Part 7 (54) · 100 questions · 75 minutes', 'A full-length TOEIC Reading section written by F2R, built around points test takers often miss (confusable words, verb patterns, conjunction vs. preposition, Part 6 context). Scores are F2R internal estimates, not official ETS TOEIC scores. 수험생이 자주 틀리는 포인트 중심의 독해 세트입니다.', NULL, 4500, 'PUBLISHED', 'system');

SET @toeic_rc02_exam_id = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-TOEIC-RC-02');

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The marketing team''s ------- for the spring advertising campaign was approved by the board without any changes.', NULL, 'NONE', '["propose", "proposes", "proposed", "proposal"]', 'D', 1, '[문법-품사(명사 자리)] 정답 (D). 소유격 team''s 뒤이면서 동사 was approved의 주어가 되는 명사 자리다. 오답: (A)·(B) 동사는 소유격 뒤에 올 수 없다. (C) proposed는 과거형·과거분사여서 이 문장의 주어가 되지 못한다.
포인트: 품사 — 소유격 뒤 명사 자리', 101),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Assembly instructions for the Tolvane exercise bike are ------- written, so most customers finish in under an hour.', NULL, 'NONE', '["clear", "clarity", "clearly", "clears"]', 'C', 1, '[문법-품사(부사 자리)] 정답 (C). be동사 are와 과거분사 written 사이에서 분사를 꾸미는 부사 자리다. 오답: (A) 형용사 clear를 넣으면 뒤의 written과 이어지지 않는다. (B) 명사, (D) 동사는 are와 written 사이에 올 수 없다.
포인트: 품사 — be동사와 과거분사 사이의 부사', 102),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The Pellwick History Museum will keep its ticket counter open ------- 9:00 P.M. on the opening night of the new exhibit.', NULL, 'NONE', '["by", "toward", "among", "until"]', 'D', 1, '[전치사(until / by)] 정답 (D). keep ... open은 상태가 이어지는 표현이므로 그 상태가 끝나는 시점을 나타내는 until과 어울린다. 오답: (A) by는 submit, arrive처럼 한 번에 끝나는 동작의 완료 기한에 쓴다. (B) toward는 ''~ 무렵, ~을 향하여''라는 뜻이어서 상태가 끝나는 시점을 정하지 못한다. (C) among은 여러 대상 ''사이에''라는 뜻이다.
포인트: 전치사 until / by(계속되는 상태 vs 완료 기한)', 103),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Photo identification ------- of all contractors entering the server rooms at Varnell Telecom''s Eastgate facility.', NULL, 'NONE', '["requires", "is required", "to require", "has required"]', 'B', 1, '[문법-태(수동)] 정답 (B). 신분증은 요구하는 주체가 아니라 요구되는 대상이고 빈칸 뒤에 목적어가 없으므로 수동태 본동사가 필요하다(be required of + 사람). 오답: (A)·(D) 능동형은 무엇을 요구하는지 목적어가 있어야 한다. (C) to부정사만으로는 문장의 본동사가 될 수 없다.
포인트: 태 — 수동태 본동사(be required of)', 104),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Due to a shortage of glass jars, Greshby Foods has ------- the launch of its new pasta sauce until early October.', NULL, 'NONE', '["postponed", "canceled", "declined", "expired"]', 'A', 1, '[어휘-동사] 정답 (A). until early October가 새로 잡힌 시점을 알려 주므로 ''~까지 미루다''인 postpone A until B가 맞다. 오답: (B) cancel은 일정 자체를 없애는 것이어서 until과 맞지 않는다. (C) decline은 제안·초대를 거절하는 동사다. (D) expire는 목적어를 취하지 않는 자동사다.
포인트: 어휘 postpone A until B(cancel과 구별)', 105),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Mossgrove Landscaping offers a free ------- consultation to homeowners who are planning a new garden design.', NULL, 'NONE', '["initial", "initially", "initials", "initialize"]', 'A', 1, '[문법-품사(형용사 자리)] 정답 (A). 관사 a와 명사 consultation 사이에서 free와 함께 명사를 꾸미는 형용사 자리다. 관사 바로 뒤가 명사인 것이 아니라 명사구 끝의 consultation이 중심 명사다. 오답: (B) 부사는 명사를 꾸미지 못한다. (C) 복수 명사 ''머리글자''는 뜻과 수가 맞지 않는다. (D) 동사가 올 자리가 아니다.
포인트: 품사 — 명사 앞 형용사(형용사 두 개 연속)', 106),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Yarrowby Dental''s new scheduling software saves a great deal of time for ------- patients and front-desk employees.', NULL, 'NONE', '["either", "between", "both", "whether"]', 'C', 1, '[접속사(상관접속사)] 정답 (C). 뒤의 and와 짝을 이루어 patients와 front-desk employees를 묶는 both A and B다. 오답: (A) either는 or와 짝을 이룬다. (B) between은 전치사여서 전치사 for 바로 뒤에 다시 올 수 없다. (D) whether는 or와 함께 쓰거나 절을 이끈다.
포인트: 상관접속사 both A and B', 107),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The help page on Quindale Mobile''s website answers the most common ------- about roaming charges and data plans.', NULL, 'NONE', '["inquiries", "suppliers", "vacancies", "receipts"]', 'A', 1, '[어휘-명사] 정답 (A). answers의 목적어이면서 about roaming charges and data plans의 수식을 받으므로 ''문의 사항''인 inquiries가 맞다. 오답: (B) 공급업체, (C) 공석, (D) 영수증은 답변의 대상이 될 수 없다.
포인트: 어휘 answer + inquiries(문의)', 108),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'A number of tenants in the Rosgrave Building ------- additional bicycle racks near the main entrance.', NULL, 'NONE', '["have requested", "has requested", "is requesting", "was requested"]', 'A', 1, '[문법-수일치(a number of)] 정답 (A). a number of + 복수명사는 ''여러 ~''라는 뜻의 복수 주어이므로 복수 동사 have requested가 맞다(the number of ~는 단수 취급). 오답: (B)·(C) 단수 동사여서 주어 tenants와 맞지 않는다. (D) 단수인 데다 수동이어서 뒤의 목적어 additional bicycle racks를 받지 못한다.
포인트: 수일치 — a number of + 복수동사', 109),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The engineers at Strellan Dynamics look forward to ------- their prototype to investors at next month''s technology expo.', NULL, 'NONE', '["present", "presented", "presenting", "be presented"]', 'C', 1, '[문법-준동사(전치사 to + 동명사)] 정답 (C). look forward to의 to는 전치사이므로 뒤에 동명사가 오고, presenting이 their prototype을 목적어로 받는다. 오답: (A) to를 부정사의 to로 보고 동사원형을 고르면 안 된다. (B) presented는 이 문장에서 전치사 to의 목적어가 되지 못한다. (D) 원형인 데다 수동이어서 their prototype을 목적어로 받지 못한다.
포인트: 준동사 — look forward to + -ing', 110),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Tests showed that the Zephra water filter is ------- to competing models at removing lead from tap water.', NULL, 'NONE', '["better", "higher", "superior", "greater"]', 'C', 1, '[어휘-형용사(형용사 + 전치사)] 정답 (C). 빈칸 뒤에 than이 아니라 to가 있으므로 superior to(~보다 뛰어난)가 맞다. 오답: (A) better, (B) higher, (D) greater는 모두 비교급이어서 비교 대상 앞에 than을 쓴다.
포인트: 연어 superior to', 111),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Although the new packaging costs slightly more, Dorrance Tea ------- expects its profits to grow this year.', NULL, 'NONE', '["yet", "ever", "very", "still"]', 'D', 1, '[어휘-부사(양보절과 호응하는 still)] 정답 (D). Although절(비용 증가)과 주절(이익 증가 예상)이 양보 관계이고, 주어와 동사 사이에서 ''그런데도 여전히''를 나타내는 부사는 still이다. 오답: (A) yet은 부정문·의문문 끝이나 절을 잇는 자리에 쓴다. (B) ever는 긍정 평서문의 동사 앞에 쓰지 않는다. (C) very는 동사를 꾸미지 못한다.
포인트: although … still(양보절과 호응하는 부사)', 112),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Visitors ------- to tour the processing plant must sign in at the security desk and wear a hairnet at all times.', NULL, 'NONE', '["wish", "wished", "wishing", "to be wished"]', 'C', 1, '[문법-분사(능동·수동)] 정답 (C). 본동사는 must sign in이므로 빈칸은 Visitors를 뒤에서 꾸미는 수식어다. 방문객이 견학을 ''원하는'' 주체이므로 능동의 현재분사 wishing이 맞다(who wish to tour). 오답: (A) wish를 넣으면 한 문장에 본동사가 둘이 된다. (B) wished는 과거형이면 본동사가 둘이 되고, 과거분사이면 이 문장에서 방문객이 바람의 대상이 되어 뜻이 맞지 않는다. (D)도 수동이어서 같은 이유로 맞지 않는다.
포인트: 분사 — 명사 뒤 수식어의 능동·수동', 113),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Mr. Dvorak asked his assistant to ------- to the caterer''s e-mail about the menu change before noon.', NULL, 'NONE', '["answer", "reply", "contact", "inform"]', 'B', 1, '[어휘-동사(동사 + 전치사)] 정답 (B). 빈칸 뒤에 전치사 to가 있으므로 reply to(~에 답하다)가 맞다. 오답: (A) answer는 뜻은 같지만 타동사여서 to 없이 the e-mail을 바로 목적어로 취한다. (C) contact, (D) inform도 타동사이고 사람을 목적어로 취한다.
포인트: 동사 문형 — reply to / answer', 114),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The new irrigation system has kept the lawns at Thistlewick Park ------- throughout the unusually dry summer.', NULL, 'NONE', '["health", "healthily", "healthiness", "healthy"]', 'D', 1, '[문법-품사(목적격 보어)] 정답 (D). keep + 목적어 + 목적격 보어 구조에서 목적어 the lawns의 상태를 설명하는 형용사가 필요하다. at Thistlewick Park는 목적어를 꾸미는 전치사구일 뿐이다. 오답: (A)·(C) 명사는 잔디와 동격이 되지 않는다. (B) 부사는 목적어의 상태를 나타내는 보어가 될 수 없다.
포인트: 품사 — keep + 목적어 + 형용사 보어', 115),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The next issue of the staff newsletter will ------- employees'' concerns about the shortage of parking spaces.', NULL, 'NONE', '["attend", "address", "respond", "proceed"]', 'B', 1, '[어휘-동사] 정답 (B). concerns를 목적어로 취해 ''(문제·우려를) 다루다''라는 뜻이 되는 동사는 address다. 오답: (A) attend는 ''참석하다''이고 ''처리하다''의 뜻일 때는 to가 필요하다. (C) respond도 to가 있어야 대상을 취한다. (D) proceed는 목적어를 취하지 않는 자동사다.
포인트: 어휘 address(문제·우려를 다루다)', 116),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The tour includes an afternoon stop in Sablecourt, ------- travelers may explore the old market on their own.', NULL, 'NONE', '["which", "of whom", "during which", "what"]', 'C', 1, '[문법-관계사(전치사+관계대명사)] 정답 (C). 빈칸 뒤는 주어·동사·목적어가 모두 갖춰진 완전한 절이다. 선행사 an afternoon stop은 기간을 지닌 명사이므로 during the stop → during which가 된다. 오답: (A) which는 뒤 절에 주어나 목적어가 빠져 있어야 한다. (B) of whom은 사람 선행사를 받는다. (D) what은 선행사를 취하지 않는다.
포인트: 관계사 — 전치사 + which(뒤가 완전한 절)', 117),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', '------- all of the seats on Oskerry Travel''s harbor cruise were sold within an hour of going on sale.', NULL, 'NONE', '["Most", "Almost", "Every", "Very"]', 'B', 1, '[어휘-부사(almost / most)] 정답 (B). all을 앞에서 꾸며 ''거의 전부''를 만드는 부사는 Almost다. 오답: (A) Most는 most of the seats처럼 all 없이 쓴다. (C) Every는 뒤에 단수 명사가 바로 와야 한다. (D) Very는 all을 수식하지 못한다.
포인트: 어휘 almost all / most', 118),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The replacement parts ------- at the Dunhallow repair shop two days earlier than the supplier had promised.', NULL, 'NONE', '["were arrived", "arriving", "to arrive", "arrived"]', 'D', 1, '[문법-태(자동사는 능동)] 정답 (D). 주어가 사물이어도 arrive는 목적어를 취하지 않는 자동사여서 수동태를 만들지 않으므로, 능동 과거 arrived가 본동사로 맞다. 오답: (A) 자동사 arrive는 be + p.p. 꼴로 쓰지 않는다. (B) 현재분사, (C) to부정사만으로는 문장의 본동사가 될 수 없다.
포인트: 태 — 자동사 arrive는 능동', 119),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Brambleton Dairy prints the expiration date in large type ------- shoppers can read it without picking up the package.', NULL, 'NONE', '["in order to", "so that", "even though", "as well as"]', 'B', 1, '[접속사(목적 so that)] 정답 (B). 빈칸 뒤에 shoppers can read라는 절이 오고, 큰 글씨로 인쇄하는 목적을 나타내므로 so that이 맞다. 오답: (A) in order to 뒤에는 동사원형이 온다. (C) even though는 절을 이끌지만 양보 관계여서 뜻이 맞지 않는다. (D) as well as는 절과 절을 연결하지 못한다.
포인트: 접속사 so that(목적) — 절 연결과 의미 관계', 120);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Questions ------- to the revised vacation policy should be sent to the human resources office by Friday.', NULL, 'NONE', '["regarding", "pertaining", "concerning", "involving"]', 'B', 1, '[어휘-형용사(분사 + 전치사)] 정답 (B). 빈칸 뒤에 to가 있으므로 pertain to(~와 관련되다)의 분사형 pertaining이 맞다(Questions that pertain to ~). 오답: (A) regarding, (C) concerning은 그 자체가 전치사여서 뒤에 to 없이 명사가 바로 온다. (D) involving도 to 없이 목적어를 취한다.
포인트: 연어 pertaining to', 121),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Extended evening hours have made ------- possible for Corvell Pharmacy to serve customers who work during the day.', NULL, 'NONE', '["itself", "its own", "theirs", "it"]', 'D', 1, '[문법-대명사(가목적어 it)] 정답 (D). make + 가목적어 it + 형용사 + (for 사람) + to부정사 구문으로, it은 뒤의 to serve customers ~를 가리킨다. 오답: (A) itself, (B) its own, (C) theirs는 앞에 가리킬 대상이 있어야 하고 뒤의 to부정사를 대신하는 가목적어로 쓰지 못한다.
포인트: 가목적어 — make it + 형용사 + to부정사', 122),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', '------- the rising cost of imported olive oil, Palmeroy Foods has decided to source more ingredients from local farms.', NULL, 'NONE', '["Nevertheless", "Even though", "In light of", "In case"]', 'C', 1, '[전치사(판단 근거 in light of)] 정답 (C). 빈칸 뒤 the rising cost of imported olive oil은 명사구이고 결정의 근거이므로 전치사 표현 In light of(~을 고려하여)가 맞다. 오답: (A) Nevertheless는 접속부사여서 명사구를 이끌지 못한다. (B) Even though, (D) In case는 접속사여서 뒤에 주어와 동사가 필요하다.
포인트: 전치사 in light of(판단 근거) — 뒤에 명사구', 123),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Refunds for canceled tours are issued only ------- the customer has returned all printed tickets to our office.', NULL, 'NONE', '["after", "during", "despite", "whether"]', 'A', 1, '[접속사(only after + 절)] 정답 (A). 빈칸 뒤에 주어와 동사가 있는 절이 오고, 표를 반납한 ''뒤에야'' 환불된다는 뜻이므로 only after가 맞다. 오답: (B) during, (C) despite는 전치사여서 절을 이끌지 못한다. (D) whether는 절을 이끌지만 ''~인지''라는 뜻이어서 only 뒤에서 시점을 나타내지 못한다.
포인트: 접속사 — only after + 절', 124),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Never before has the Ondrevale Film Festival ------- as many visitors as it did over the past weekend.', NULL, 'NONE', '["attract", "attracts", "attracting", "attracted"]', 'D', 1, '[문법-도치(Never before has + p.p.)] 정답 (D). 부정어구 Never before가 문두에 와서 has + 주어로 도치된 문장이다. 조동사 has와 짝을 이루어 현재완료를 만드는 과거분사 attracted가 맞다. 오답: (A) 원형은 does·did 도치 뒤에 온다. (B) has 뒤에 정형동사를 또 쓸 수 없다. (C) 현재분사는 has와 완료형을 이루지 못한다.
포인트: 도치 — Never before has + 주어 + p.p.', 125),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The proposed delivery date ------- with the warehouse''s annual inventory count, so another day must be chosen.', NULL, 'NONE', '["conflicts", "interrupts", "prevents", "opposes"]', 'A', 1, '[어휘-동사(동사 + 전치사)] 정답 (A). 빈칸 뒤에 with가 있고 두 일정이 겹친다는 뜻이므로 conflict with(~와 겹치다·충돌하다)가 맞다. 오답: (B) interrupts, (C) prevents, (D) opposes는 모두 타동사여서 with 없이 목적어를 바로 취한다.
포인트: 연어 conflict with', 126),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The company shuttle is available to ------- working the late shift at the Jorvane assembly plant.', NULL, 'NONE', '["anyone", "whoever", "which", "every"]', 'A', 1, '[문법-대명사(anyone + 분사 / whoever)] 정답 (A). 전치사 to의 목적어가 될 대명사가 필요하고, working the late shift가 그 대명사를 뒤에서 꾸민다(anyone who is working). 오답: (B) whoever는 whoever works처럼 시제가 있는 동사가 따라야 한다. (C) which는 선행사와 절이 있어야 한다. (D) every는 뒤에 명사가 필요한 한정사다.
포인트: 대명사 — anyone + 분사(whoever + 절과 구별)', 127),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Thanks to the ------- processing of visa applications, Kestrow Travel''s clients rarely experience delays before departure.', NULL, 'NONE', '["prompts", "promptly", "prompt", "prompting"]', 'C', 1, '[문법-품사(명사구 안의 형용사 / 부사)] 정답 (C). the ------- processing of ...에서 processing은 관사와 of 사이에 놓인 명사이므로 그 앞에는 형용사 prompt가 온다. 오답: (B) promptly는 processing applications promptly처럼 -ing가 목적어를 직접 취하는 동사적 구조에서 쓴다. (A) prompts는 동사·복수 명사여서 processing을 꾸미지 못한다. (D) prompting은 ''재촉하는''이라는 뜻이어서 맞지 않는다.
포인트: 품사 — 명사형 -ing(+ of) 앞 형용사', 128),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'When its Corrimore branch closes in June, the bank will ------- all six tellers to nearby offices.', NULL, 'NONE', '["resign", "reassign", "remind", "replace"]', 'B', 1, '[어휘-동사] 정답 (B). 지점이 문을 닫아 직원을 다른 곳으로 옮기는 상황이고 뒤에 to + 장소가 있으므로 reassign A to B(A를 B로 다시 배정하다)가 맞다. 오답: (A) resign은 ''사임하다''로 사람을 목적어로 취하지 않는다. (C) remind는 사람 + to부정사·of와 쓴다. (D) replace는 A with B와 결합한다.
포인트: 어휘 reassign A to B', 129),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Although the opening speech ran 10 minutes over, the shareholders'' meeting ------- proceeded exactly as scheduled.', NULL, 'NONE', '["otherwise", "likewise", "instead", "moreover"]', 'A', 1, '[어휘-부사] 정답 (A). 개회사가 길어진 한 가지 예외를 빼면 나머지는 예정대로였다는 뜻이므로 ''그 밖의 점에서는''인 otherwise가 맞다. 오답: (B) likewise는 ''마찬가지로''여서 예외와 나머지의 대비를 나타내지 못한다. (C) instead는 대체, (D) moreover는 추가를 나타내어 Although절과 맞지 않는다.
포인트: 어휘 otherwise(그 밖의 점에서는)', 130),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (131).', 'Questions 131-134 refer to the following information.

Vessant Ferry Lines
Baggage Information

Each passenger may bring two pieces of baggage on board at no charge. Larger items, such as bicycles and surfboards, must be checked at the terminal office so that they can be loaded before passengers board. The office stops accepting these items 30 minutes before (131) -------. A fee of $8 applies to each checked item.

Baggage that (132) ------- unattended in the terminal will be removed by our staff and held at the lost-and-found desk.

Some items may not be brought onto the ferry at all. (133) -------, fuel containers and fireworks are prohibited on every route. (134) -------.

We thank you for your cooperation.', 'NONE', '["departure", "arrival", "delivery", "purchase"]', 'A', 1, '[Part6-어휘(문맥)] 정답 (A). 앞 문장에서 큰 짐은 승객이 타기 전에 실어야 한다(loaded before passengers board)고 했으므로, 접수를 마감하는 기준 시점은 배의 출항(departure)이다. 오답: (B) arrival은 목적지에 도착하는 때여서 탑승 전 접수와 맞지 않는다. (C) delivery, (D) purchase는 접수 마감의 기준이 될 수 없다.
포인트: Part 6 어휘 — 앞 문장의 단서(탑승 전 → departure)', 131),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (132).', 'Questions 131-134 refer to the following information.

Vessant Ferry Lines
Baggage Information

Each passenger may bring two pieces of baggage on board at no charge. Larger items, such as bicycles and surfboards, must be checked at the terminal office so that they can be loaded before passengers board. The office stops accepting these items 30 minutes before (131) -------. A fee of $8 applies to each checked item.

Baggage that (132) ------- unattended in the terminal will be removed by our staff and held at the lost-and-found desk.

Some items may not be brought onto the ferry at all. (133) -------, fuel containers and fireworks are prohibited on every route. (134) -------.

We thank you for your cooperation.', 'NONE', '["leaves", "is left", "leaving", "has left"]', 'B', 1, '[Part6-문법(태)] 정답 (B). 관계절의 주어 that(= Baggage)은 놓아두는 주체가 아니라 놓인 대상이고 뒤에 목적어 없이 보어 unattended만 있으므로 수동태 is left가 맞다. 오답: (A)·(D) 능동형은 짐이 무언가를 두고 가는 주체가 된다. (C) 분사만으로는 관계절의 동사가 될 수 없다.
포인트: 태 — 수동태(be left unattended)', 132),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (133).', 'Questions 131-134 refer to the following information.

Vessant Ferry Lines
Baggage Information

Each passenger may bring two pieces of baggage on board at no charge. Larger items, such as bicycles and surfboards, must be checked at the terminal office so that they can be loaded before passengers board. The office stops accepting these items 30 minutes before (131) -------. A fee of $8 applies to each checked item.

Baggage that (132) ------- unattended in the terminal will be removed by our staff and held at the lost-and-found desk.

Some items may not be brought onto the ferry at all. (133) -------, fuel containers and fireworks are prohibited on every route. (134) -------.

We thank you for your cooperation.', 'NONE', '["Instead", "Similarly", "In short", "For example"]', 'D', 1, '[Part6-연결어] 정답 (D). 앞 문장은 아예 반입할 수 없는 물품이 있다는 일반 진술이고, 뒤 문장은 그 구체적인 사례(fuel containers and fireworks)이므로 예시를 이끄는 For example이 맞다. 오답: (A) Instead는 대체, (B) Similarly는 비슷한 내용의 추가, (C) In short는 앞 내용의 요약을 이끌어 두 문장의 관계와 맞지 않는다.
포인트: 연결어 — 예시(For example)', 133),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (134).', 'Questions 131-134 refer to the following information.

Vessant Ferry Lines
Baggage Information

Each passenger may bring two pieces of baggage on board at no charge. Larger items, such as bicycles and surfboards, must be checked at the terminal office so that they can be loaded before passengers board. The office stops accepting these items 30 minutes before (131) -------. A fee of $8 applies to each checked item.

Baggage that (132) ------- unattended in the terminal will be removed by our staff and held at the lost-and-found desk.

Some items may not be brought onto the ferry at all. (133) -------, fuel containers and fireworks are prohibited on every route. (134) -------.

We thank you for your cooperation.', 'NONE', '["A complete list of these items is posted at every ticket window.", "They may be carried on board if they are checked 30 minutes early.", "This fee will be refunded when the ferry reaches its destination.", "For this reason, unattended bags are never removed from the terminal."]', 'A', 1, '[Part6-문장삽입] 정답 (A). these items가 바로 앞의 반입 금지 물품을 받아, 전체 목록을 어디서 볼 수 있는지 덧붙인다. 오답: (B) 금지 물품은 아예 실을 수 없다(at all)고 했으므로 모순이다. (C) This fee가 받을 요금이 바로 앞에 없고 환불도 언급되지 않았다. (D) 방치된 짐은 직원이 치운다고 한 앞 문단과 반대다.
포인트: 문장 삽입 — 지시어 these items의 대상', 134),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (135).', 'Questions 135-138 refer to the following e-mail.

To: Daniela Farrow
From: Kenji Morimoto
Date: April 8
Subject: Staff photographs

Dear Ms. Farrow,

As you know, a photographer will visit our office next month to take portraits for the new staff directory. We (135) ------- a schedule to every department head on April 1. Since then, two departments have asked for different time slots. (136) -------, we have had to make a few changes.

Your department''s session was originally planned for the morning of May 14. It has now been (137) ------- for 2:00 P.M. on the same day. The room and the length of the session are unchanged. (138) -------. If so, please let me know by April 15 so that we can find another solution.

Sincerely,

Kenji Morimoto
Human Resources', 'NONE', '["are sending", "will send", "sent", "were sent"]', 'C', 1, '[Part6-문법(시제·문맥)] 정답 (C). 이메일 작성일이 April 8이고 일정표를 보낸 날은 April 1이며, 다음 문장도 Since then으로 그 뒤의 일을 말하므로 과거 시제 sent가 맞다. 오답: (A) 현재진행, (B) 미래는 이미 지난 날짜와 맞지 않는다. (D) 수동태는 뒤의 목적어 a schedule을 받지 못한다.
포인트: 시제 — 작성일과 다음 문장으로 판단하는 과거', 135),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (136).', 'Questions 135-138 refer to the following e-mail.

To: Daniela Farrow
From: Kenji Morimoto
Date: April 8
Subject: Staff photographs

Dear Ms. Farrow,

As you know, a photographer will visit our office next month to take portraits for the new staff directory. We (135) ------- a schedule to every department head on April 1. Since then, two departments have asked for different time slots. (136) -------, we have had to make a few changes.

Your department''s session was originally planned for the morning of May 14. It has now been (137) ------- for 2:00 P.M. on the same day. The room and the length of the session are unchanged. (138) -------. If so, please let me know by April 15 so that we can find another solution.

Sincerely,

Kenji Morimoto
Human Resources', 'NONE', '["Thus", "Likewise", "Otherwise", "In contrast"]', 'A', 1, '[Part6-연결어] 정답 (A). 앞 문장(두 부서가 다른 시간대를 요청함)이 원인이고 뒤 문장(일정을 몇 군데 바꿈)이 그 결과이므로 Thus(따라서)가 맞다. 오답: (B) Likewise는 비슷한 내용을 덧붙인다. (C) Otherwise는 ''그렇지 않으면''이다. (D) In contrast는 대조되는 내용을 이끈다.
포인트: 연결어 — 원인·결과(thus)', 136),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (137).', 'Questions 135-138 refer to the following e-mail.

To: Daniela Farrow
From: Kenji Morimoto
Date: April 8
Subject: Staff photographs

Dear Ms. Farrow,

As you know, a photographer will visit our office next month to take portraits for the new staff directory. We (135) ------- a schedule to every department head on April 1. Since then, two departments have asked for different time slots. (136) -------, we have had to make a few changes.

Your department''s session was originally planned for the morning of May 14. It has now been (137) ------- for 2:00 P.M. on the same day. The room and the length of the session are unchanged. (138) -------. If so, please let me know by April 15 so that we can find another solution.

Sincerely,

Kenji Morimoto
Human Resources', 'NONE', '["canceled", "shortened", "rescheduled", "relocated"]', 'C', 1, '[Part6-어휘(문맥)] 정답 (C). 오전으로 잡혀 있던 촬영이 for 2:00 P.M. on the same day로 옮겨졌으므로 ''일정을 다시 잡다''인 rescheduled가 맞다. 오답: (A) canceled는 새 시각이 주어진 것과 맞지 않는다. (B) shortened, (D) relocated는 다음 문장의 The room and the length of the session are unchanged와 어긋난다.
포인트: Part 6 어휘 — rescheduled for + 새 시각(뒤 문장 단서)', 137),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (138).', 'Questions 135-138 refer to the following e-mail.

To: Daniela Farrow
From: Kenji Morimoto
Date: April 8
Subject: Staff photographs

Dear Ms. Farrow,

As you know, a photographer will visit our office next month to take portraits for the new staff directory. We (135) ------- a schedule to every department head on April 1. Since then, two departments have asked for different time slots. (136) -------, we have had to make a few changes.

Your department''s session was originally planned for the morning of May 14. It has now been (137) ------- for 2:00 P.M. on the same day. The room and the length of the session are unchanged. (138) -------. If so, please let me know by April 15 so that we can find another solution.

Sincerely,

Kenji Morimoto
Human Resources', 'NONE', '["The photographer will therefore use a different room that day.", "We realize that the new time may not suit everyone on your team.", "We are sorry that your department has decided not to take part.", "The first schedule will be sent to department heads next week."]', 'B', 1, '[Part6-문장삽입] 정답 (B). 뒤 문장의 If so가 ''새 시간이 맞지 않는 사람이 있다면''을 받아 다른 방법을 찾겠다는 내용으로 이어진다. 오답: (A) 촬영 장소는 바뀌지 않았다(The room ... unchanged)고 했으므로 모순이다. (C) 부서가 불참하기로 했다는 내용은 없다. (D) 일정표는 이미 April 1에 보냈으므로 시점이 맞지 않는다.
포인트: 문장 삽입 — 뒤 문장 If so가 받는 내용', 138),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (139).', 'Questions 139-142 refer to the following Web page.

Tallis & Halvard Translations
Home | Services | About Us | Contact

About Us

Tallis & Halvard provides written translation for companies that sell their products abroad. Every document is first translated by a specialist (139) ------- knows the client''s industry, whether it is medicine, law, or engineering. A second translator then checks the text for (140) -------. Because of this two-step process, mistakes are found and corrected before the document reaches you.

We opened our first office 15 years ago. Since then, we (141) ------- more than 40,000 documents for clients in 30 countries. Most of our work comes from customers who return to us year after year. (142) -------.

To request a price quote, please visit our Contact page.', 'NONE', '["whose", "what", "who", "whoever"]', 'C', 1, '[Part6-문법(관계대명사)] 정답 (C). 선행사가 사람 a specialist이고 빈칸 뒤에 동사 knows가 바로 오므로 주격 관계대명사 who가 맞다. 오답: (A) whose 뒤에는 명사가 와야 한다. (B) what, (D) whoever는 선행사 없이 쓴다.
포인트: 관계대명사 — 사람 선행사 + 주격 who', 139),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (140).', 'Questions 139-142 refer to the following Web page.

Tallis & Halvard Translations
Home | Services | About Us | Contact

About Us

Tallis & Halvard provides written translation for companies that sell their products abroad. Every document is first translated by a specialist (139) ------- knows the client''s industry, whether it is medicine, law, or engineering. A second translator then checks the text for (140) -------. Because of this two-step process, mistakes are found and corrected before the document reaches you.

We opened our first office 15 years ago. Since then, we (141) ------- more than 40,000 documents for clients in 30 countries. Most of our work comes from customers who return to us year after year. (142) -------.

To request a price quote, please visit our Contact page.', 'NONE', '["accuracy", "payment", "delivery", "permission"]', 'A', 1, '[Part6-어휘(문맥)] 정답 (A). 다음 문장에서 이 두 단계 과정 덕분에 mistakes are found and corrected라고 했으므로, 두 번째 번역가가 확인하는 것은 번역의 정확성(accuracy)이다. 오답: (B) payment, (C) delivery, (D) permission은 오류를 찾아 고친다는 뒤 문장과 이어지지 않는다.
포인트: Part 6 어휘 — 뒤 문장의 단서(mistakes → accuracy)', 140);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (141).', 'Questions 139-142 refer to the following Web page.

Tallis & Halvard Translations
Home | Services | About Us | Contact

About Us

Tallis & Halvard provides written translation for companies that sell their products abroad. Every document is first translated by a specialist (139) ------- knows the client''s industry, whether it is medicine, law, or engineering. A second translator then checks the text for (140) -------. Because of this two-step process, mistakes are found and corrected before the document reaches you.

We opened our first office 15 years ago. Since then, we (141) ------- more than 40,000 documents for clients in 30 countries. Most of our work comes from customers who return to us year after year. (142) -------.

To request a price quote, please visit our Contact page.', 'NONE', '["translate", "have translated", "will translate", "were translated"]', 'B', 1, '[Part6-문법(시제·문맥)] 정답 (B). Since then은 앞 문장의 15 years ago부터 지금까지를 가리키므로 그동안의 실적을 나타내는 현재완료 have translated가 맞다. 오답: (A) 현재, (C) 미래는 과거부터 이어진 누적 실적을 나타내지 못한다. (D) 수동태는 주어 we가 번역되는 대상이 되고 뒤의 목적어도 받지 못한다.
포인트: 시제 — Since then + 현재완료', 141),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (142).', 'Questions 139-142 refer to the following Web page.

Tallis & Halvard Translations
Home | Services | About Us | Contact

About Us

Tallis & Halvard provides written translation for companies that sell their products abroad. Every document is first translated by a specialist (139) ------- knows the client''s industry, whether it is medicine, law, or engineering. A second translator then checks the text for (140) -------. Because of this two-step process, mistakes are found and corrected before the document reaches you.

We opened our first office 15 years ago. Since then, we (141) ------- more than 40,000 documents for clients in 30 countries. Most of our work comes from customers who return to us year after year. (142) -------.

To request a price quote, please visit our Contact page.', 'NONE', '["As a result, we no longer accept documents on legal subjects.", "These customers will be contacted when our first office opens.", "However, the second check is usually skipped to save time.", "We believe that this loyalty reflects the quality of our work."]', 'D', 1, '[Part6-문장삽입] 정답 (D). this loyalty가 바로 앞의 customers who return to us year after year를 받아, 그 이유를 번역 품질로 설명하며 문단을 맺는다. 오답: (A) 첫 문단에서 law 분야도 다룬다고 했으므로 모순이다. (B) 첫 사무소는 15 years ago에 이미 열었다. (C) Every document가 두 단계를 거친다는 설명과 어긋난다.
포인트: 문장 삽입 — 지시어 this loyalty의 대상', 142),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (143).', 'Questions 143-146 refer to the following article.

STANNICK (October 3) — The Owl Line, the city''s late-night bus service, carried more than 210,000 passengers in its first year, according to figures released on Monday. City officials had (143) ------- only about 120,000.

The service has been especially popular with people who work at night. (144) -------. Many of these riders say that the buses save them the cost of a taxi home.

Encouraged by the results, the transit department plans to add two routes next spring. Both will serve neighborhoods east of the river, where (145) ------- few buses run after dark.

The department also considered lowering the fare. (146) -------, it decided to keep the price at $2 for another year.', 'NONE', '["refused", "predicted", "restricted", "reminded"]', 'B', 1, '[Part6-어휘(문맥)] 정답 (B). 앞 문장의 실제 이용객 more than 210,000과 대비되는 only about 120,000은 시 당국이 미리 내다본 수치이므로 predicted(예측했다)가 맞다. 오답: (A) refused는 거절했다, (C) restricted는 제한했다, (D) reminded는 사람을 목적어로 취해 ''상기시켰다''여서 수치와 이어지지 않는다.
포인트: Part 6 어휘 — 앞 문장 수치와의 대비(predicted)', 143),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (144).', 'Questions 143-146 refer to the following article.

STANNICK (October 3) — The Owl Line, the city''s late-night bus service, carried more than 210,000 passengers in its first year, according to figures released on Monday. City officials had (143) ------- only about 120,000.

The service has been especially popular with people who work at night. (144) -------. Many of these riders say that the buses save them the cost of a taxi home.

Encouraged by the results, the transit department plans to add two routes next spring. Both will serve neighborhoods east of the river, where (145) ------- few buses run after dark.

The department also considered lowering the fare. (146) -------, it decided to keep the price at $2 for another year.', 'NONE', '["Most of them said that they had never heard of the service.", "The buses will therefore stop running at 9:00 P.M. next month.", "Nearly half of all passengers boarded or left a bus near Stannick General Hospital.", "Officials will release the first-year figures later this month."]', 'C', 1, '[Part6-문장삽입] 정답 (C). 밤에 일하는 사람들에게 인기라는 앞 문장을 병원 근처 승하차 비율로 뒷받침하고, 뒤 문장의 these riders가 이 승객들을 받는다. 오답: (A) 서비스를 들어 본 적이 없다는 것은 인기가 많다는 앞 문장과 모순이다. (B) 운행 중단은 인기·노선 추가 계획과 맞지 않는다. (D) 수치는 이미 released on Monday라고 했으므로 시점이 어긋난다.
포인트: 문장 삽입 — 앞 진술의 근거 + 뒤 문장 these riders', 144),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (145).', 'Questions 143-146 refer to the following article.

STANNICK (October 3) — The Owl Line, the city''s late-night bus service, carried more than 210,000 passengers in its first year, according to figures released on Monday. City officials had (143) ------- only about 120,000.

The service has been especially popular with people who work at night. (144) -------. Many of these riders say that the buses save them the cost of a taxi home.

Encouraged by the results, the transit department plans to add two routes next spring. Both will serve neighborhoods east of the river, where (145) ------- few buses run after dark.

The department also considered lowering the fare. (146) -------, it decided to keep the price at $2 for another year.', 'NONE', '["relative", "relation", "relate", "relatively"]', 'D', 1, '[Part6-문법(품사)] 정답 (D). 주어 buses를 꾸미는 수량 형용사 few를 앞에서 수식하는 부사 자리이므로 relatively(비교적)가 맞다. 오답: (A) 형용사 relative는 few를 꾸미지 못한다. (B) 명사, (C) 동사는 where와 few buses 사이에 올 수 없다.
포인트: 품사 — 형용사 few 앞 부사', 145),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (146).', 'Questions 143-146 refer to the following article.

STANNICK (October 3) — The Owl Line, the city''s late-night bus service, carried more than 210,000 passengers in its first year, according to figures released on Monday. City officials had (143) ------- only about 120,000.

The service has been especially popular with people who work at night. (144) -------. Many of these riders say that the buses save them the cost of a taxi home.

Encouraged by the results, the transit department plans to add two routes next spring. Both will serve neighborhoods east of the river, where (145) ------- few buses run after dark.

The department also considered lowering the fare. (146) -------, it decided to keep the price at $2 for another year.', 'NONE', '["For instance", "In addition", "As a result", "In the end"]', 'D', 1, '[Part6-연결어] 정답 (D). 앞 문장은 요금 인하를 검토했다는 내용이고 뒤 문장은 검토 끝에 요금을 그대로 두기로 했다는 최종 결정이므로 In the end(결국)가 맞다. 오답: (A) For instance는 예시, (B) In addition은 추가를 이끈다. (C) As a result를 쓰면 인하를 검토한 결과로 요금을 유지했다는 뜻이 되어 맞지 않는다.
포인트: 연결어 — 검토 뒤의 최종 결정(In the end)', 146),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is the purpose of the notice?', 'Questions 147-148 refer to the following notice.

Ostrander Plaza — Notice to Tenants

The lower level of the parking garage will be closed from March 9 through March 13 while its concrete floor is repaired. During this period, vehicles with a lower-level permit may park on the rooftop level, which will be reserved for permit holders only. You do not need to apply for anything; your current access card will open the rooftop gate automatically.

Please remove all vehicles from the lower level by 8:00 P.M. on March 8. Any vehicle left there after that time will be towed at the owner''s expense.

Visitor parking on the street level is not affected. Questions may be directed to the building management office at 555-0119.', 'NONE', '["To announce a temporary change in parking", "To introduce a new fee for parking permits", "To explain how to replace a lost access card", "To report that a repair project has ended"]', 'A', 1, '[주제·목적] 정답 (A). "The lower level of the parking garage will be closed from March 9 through March 13"과 그 기간에 옥상층을 쓰라는 안내가 핵심이므로, 일시적인 주차 변경을 알리는 글이다. 오답: (B) 요금 이야기는 없다. (C) 기존 출입 카드가 그대로 작동한다고 했을 뿐 재발급 안내가 아니다. (D) 공사는 앞으로 할 일이지 끝난 일이 아니다.
포인트: 주제·목적 — 공지 첫 문단', 147),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What are lower-level permit holders asked to do?', 'Questions 147-148 refer to the following notice.

Ostrander Plaza — Notice to Tenants

The lower level of the parking garage will be closed from March 9 through March 13 while its concrete floor is repaired. During this period, vehicles with a lower-level permit may park on the rooftop level, which will be reserved for permit holders only. You do not need to apply for anything; your current access card will open the rooftop gate automatically.

Please remove all vehicles from the lower level by 8:00 P.M. on March 8. Any vehicle left there after that time will be towed at the owner''s expense.

Visitor parking on the street level is not affected. Questions may be directed to the building management office at 555-0119.', 'NONE', '["Apply for a rooftop parking permit", "Show their access cards to a guard", "Move their cars by a stated deadline", "Park in the street-level visitor spaces"]', 'C', 1, '[세부 사항] 정답 (C). "Please remove all vehicles from the lower level by 8:00 P.M. on March 8"를 ''정해진 기한까지 차를 옮기다''로 바꿔 말했다. 오답: (A) "You do not need to apply for anything"이라고 했다. (B) 카드가 문을 자동으로 연다고 했고 경비원 언급은 없다. (D) 거리층은 방문객용이고 허가증 소지자는 옥상층에 주차한다.
포인트: 세부 사항 — 공지 둘째 문단(by = 완료 기한)', 148),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What problem does Ms. Karimi mention?', 'Questions 149-150 refer to the following text-message chain.

Yasmin Karimi (8:52 A.M.)
Joel, I''m in Room 112 setting up for the 10:00 A.M. budgeting workshop. The projector won''t turn on.

Joel Becker (8:54 A.M.)
Did you try the spare cable in the cabinet?

Yasmin Karimi (8:57 A.M.)
Yes. Still nothing. And 26 people have signed up, so I can''t just pass my laptop around.

Joel Becker (8:59 A.M.)
Room 325 is free until noon, and its screen was replaced last week. Want me to book it?

Yasmin Karimi (9:00 A.M.)
That would save the day. I''ll carry the handouts upstairs now.

Joel Becker (9:03 A.M.)
Done. I''ll also put a sign on the door of 112 so nobody waits in the wrong place.', 'NONE', '["A room has been reserved by two groups.", "Some handouts have not been printed.", "Too few people have signed up.", "A piece of equipment is not working."]', 'D', 1, '[세부 사항] 정답 (D). "The projector won''t turn on"을 ''장비 하나가 작동하지 않는다''로 바꿔 말했다. 오답: (A) 중복 예약 이야기는 없다. (B) 유인물은 이미 있어서 위층으로 옮긴다고 했다. (C) 26명이 신청했다고 했으므로 인원 부족이 아니다.
포인트: 세부 사항 — 첫 문자(문제 상황)', 149),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'At 9:00 A.M., what does Ms. Karimi most likely mean when she writes, "That would save the day"?', 'Questions 149-150 refer to the following text-message chain.

Yasmin Karimi (8:52 A.M.)
Joel, I''m in Room 112 setting up for the 10:00 A.M. budgeting workshop. The projector won''t turn on.

Joel Becker (8:54 A.M.)
Did you try the spare cable in the cabinet?

Yasmin Karimi (8:57 A.M.)
Yes. Still nothing. And 26 people have signed up, so I can''t just pass my laptop around.

Joel Becker (8:59 A.M.)
Room 325 is free until noon, and its screen was replaced last week. Want me to book it?

Yasmin Karimi (9:00 A.M.)
That would save the day. I''ll carry the handouts upstairs now.

Joel Becker (9:03 A.M.)
Done. I''ll also put a sign on the door of 112 so nobody waits in the wrong place.', 'NONE', '["She wants to move the workshop to the afternoon.", "She thinks the screen in Room 112 should be replaced.", "She needs more time to finish the handouts.", "She gladly accepts the offer to reserve another room."]', 'D', 1, '[의도 파악] 정답 (D). 바로 앞에서 Mr. Becker가 "Room 325 is free until noon ... Want me to book it?"이라고 제안했고, Ms. Karimi는 곧바로 유인물을 위층으로 옮기겠다고 했으므로 다른 방을 예약해 주겠다는 제안을 반긴다는 뜻이다. 오답: (A) 시간을 바꾸자는 말은 없다. (B) 교체된 것은 325호 화면이다. (C) 유인물 준비 시간이 아니라 방 문제를 말하고 있다.
포인트: 의도 파악 — 바로 앞 제안과 뒤따르는 행동', 150),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about Vessington Airport Shuttle?', 'Questions 151-152 refer to the following advertisement.

Vessington Airport Shuttle — Reliable Rides, Day or Night

Serving Vessington Regional Airport from downtown Vessington and Petrie Falls since 2009.

• Departures every 30 minutes from 4:00 A.M. to 11:30 P.M.
• Spacious coaches with free Wi-Fi and room for two suitcases per passenger
• One-way fare: $18 from Vessington, $26 from Petrie Falls

Book online at least 24 hours before departure and save 10 percent. Groups of eight or more are eligible for a private van at the regular fare; call 555-0158 to arrange one.

Tickets are valid only for the date and time printed on them. Changes can be made free of charge until two hours before the scheduled departure.', 'NONE', '["It operates around the clock every day.", "It has more than one pickup location.", "It charges a fee for each suitcase carried.", "It recently added new coaches to its fleet."]', 'B', 1, '[사실 확인] 정답 (B). "from downtown Vessington and Petrie Falls"에서 승객을 태우는 곳이 한 곳이 아님을 알 수 있다. 오답: (A) 운행은 오전 4시부터 밤 11시 30분까지다. (C) 승객 1인당 가방 두 개 자리가 있다고 했을 뿐 요금은 없다. (D) 새 차량 언급은 없다.
포인트: 사실 확인 — 광고 첫 줄(출발 지점)', 151),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'How can customers receive a discount?', 'Questions 151-152 refer to the following advertisement.

Vessington Airport Shuttle — Reliable Rides, Day or Night

Serving Vessington Regional Airport from downtown Vessington and Petrie Falls since 2009.

• Departures every 30 minutes from 4:00 A.M. to 11:30 P.M.
• Spacious coaches with free Wi-Fi and room for two suitcases per passenger
• One-way fare: $18 from Vessington, $26 from Petrie Falls

Book online at least 24 hours before departure and save 10 percent. Groups of eight or more are eligible for a private van at the regular fare; call 555-0158 to arrange one.

Tickets are valid only for the date and time printed on them. Changes can be made free of charge until two hours before the scheduled departure.', 'NONE', '["By traveling with seven other people", "By reserving a seat a day or more ahead", "By buying tickets for a round trip", "By changing a ticket two hours early"]', 'B', 1, '[세부 사항] 정답 (B). "Book online at least 24 hours before departure and save 10 percent"를 ''하루 이상 미리 예약''으로 바꿔 말했다. 오답: (A) 8명 이상은 전용 밴을 ''정상 요금으로'' 이용할 수 있을 뿐 할인이 아니다. (C) 왕복 할인은 없다. (D) 두 시간 전까지는 변경이 무료라는 내용이지 할인이 아니다.
포인트: 세부 사항 — 광고의 할인 조건(자격 eligible과 구별)', 152),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'Who most likely is Mr. Dragomir?', 'Questions 153-154 refer to the following e-mail.

To: Zoran Dragomir
From: Front Desk, Halvern Tower
Date: June 3
Subject: Your building access

Dear Mr. Dragomir,

Ashgrove Engineering has informed us that you will be working on its ninth-floor network upgrade from June 10 to June 28. A temporary security badge has been issued in your name. Please collect it from the lobby front desk on your first morning and bring a government-issued photo ID.

The badge opens the main entrance and the elevators from 7:00 A.M. to 7:00 P.M. If you expect to work outside those hours, Ashgrove''s office manager must send us written approval at least one day beforehand.

The badge must be returned to the front desk on your final day.

Sincerely,
Ottilie Vasquez
Front Desk Supervisor', 'NONE', '["A technician on a short-term assignment", "A new member of the front desk staff", "The office manager at Ashgrove Engineering", "A tenant moving into Halvern Tower"]', 'A', 1, '[추론] 정답 (A). "you will be working on its ninth-floor network upgrade from June 10 to June 28"와 "A temporary security badge"에서 약 3주 동안 네트워크 작업을 하러 오는 외부 기술자임을 알 수 있다. 오답: (B) 프런트 데스크는 메일을 보낸 쪽이다. (C) 사무실 관리자는 승인서를 보내는 다른 사람이다. (D) 입주가 아니라 기간이 정해진 작업이다.
포인트: 추론 — 이메일 첫 문단(작업 기간·임시 출입증)', 153),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'According to the e-mail, what requires approval in advance?', 'Questions 153-154 refer to the following e-mail.

To: Zoran Dragomir
From: Front Desk, Halvern Tower
Date: June 3
Subject: Your building access

Dear Mr. Dragomir,

Ashgrove Engineering has informed us that you will be working on its ninth-floor network upgrade from June 10 to June 28. A temporary security badge has been issued in your name. Please collect it from the lobby front desk on your first morning and bring a government-issued photo ID.

The badge opens the main entrance and the elevators from 7:00 A.M. to 7:00 P.M. If you expect to work outside those hours, Ashgrove''s office manager must send us written approval at least one day beforehand.

The badge must be returned to the front desk on your final day.

Sincerely,
Ottilie Vasquez
Front Desk Supervisor', 'NONE', '["Picking up a badge on the first morning", "Using the elevators to reach the ninth floor", "Returning a badge after the final day", "Working in the building outside set hours"]', 'D', 1, '[세부 사항] 정답 (D). "If you expect to work outside those hours, Ashgrove''s office manager must send us written approval at least one day beforehand"가 근거다. 오답: (A) 수령에는 사진이 있는 신분증만 필요하다. (B) 엘리베이터는 출입증으로 정해진 시간에 그냥 이용한다. (C) 출입증은 마지막 날 반납해야 한다고 했을 뿐 승인 이야기가 아니다.
포인트: 세부 사항 — 이메일 둘째 문단(written approval)', 154),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is the main purpose of the memo?', 'Questions 155-157 refer to the following memo.

MEMO

To: All Staff, Birchall & Stroud Consulting
From: Chiara Lombardi, Finance Director
Date: September 1
Subject: Changes to expense reimbursement

Beginning October 1, all expense claims must be submitted through the Claimwise mobile application. Paper claim forms will no longer be accepted after September 30.

With Claimwise, you simply photograph each receipt and select the related client project. Claims submitted by the 20th of a month will be reimbursed with that month''s salary payment; under the current system, employees often wait six weeks or longer.

Two rules are also changing on October 1. First, receipts will be required for every expense of $15 or more, down from $25. Second, any meal with clients that costs more than $200 must be approved by a department head before the meal takes place. The daily meal allowance for business trips remains $60.

Training sessions on the application will be held in Conference Room B on September 12 and 19. To sign up, contact Hyun-woo Baek at extension 263. Attendance is not mandatory, but it is strongly encouraged.', 'NONE', '["To remind staff of a deadline for a training course", "To announce an increase in a travel allowance", "To describe a new procedure for claiming expenses", "To ask managers to approve overdue payments"]', 'C', 1, '[주제·목적] 정답 (C). 제목 "Changes to expense reimbursement"와 "all expense claims must be submitted through the Claimwise mobile application"에서 경비 청구 절차가 새로 바뀐다는 것을 알리는 글임을 알 수 있다. 오답: (A) 교육은 선택 사항으로 덧붙인 안내일 뿐이다. (B) 식비 한도는 60달러 그대로다. (D) 밀린 지급을 승인해 달라는 요청은 없다.
포인트: 주제·목적 — 메모 제목과 첫 문단', 155),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the current reimbursement system?', 'Questions 155-157 refer to the following memo.

MEMO

To: All Staff, Birchall & Stroud Consulting
From: Chiara Lombardi, Finance Director
Date: September 1
Subject: Changes to expense reimbursement

Beginning October 1, all expense claims must be submitted through the Claimwise mobile application. Paper claim forms will no longer be accepted after September 30.

With Claimwise, you simply photograph each receipt and select the related client project. Claims submitted by the 20th of a month will be reimbursed with that month''s salary payment; under the current system, employees often wait six weeks or longer.

Two rules are also changing on October 1. First, receipts will be required for every expense of $15 or more, down from $25. Second, any meal with clients that costs more than $200 must be approved by a department head before the meal takes place. The daily meal allowance for business trips remains $60.

Training sessions on the application will be held in Conference Room B on September 12 and 19. To sign up, contact Hyun-woo Baek at extension 263. Attendance is not mandatory, but it is strongly encouraged.', 'NONE', '["It requires a receipt for every expense.", "It is slower to repay employees.", "It was introduced on September 1.", "It can be used only on a mobile phone."]', 'B', 1, '[추론] 정답 (B). 새 방식은 20일까지 내면 그달 급여와 함께 돌려받는데 "under the current system, employees often wait six weeks or longer"라고 했으므로 지금 방식이 더 느리다. 오답: (A) 지금은 25달러 이상일 때만 영수증이 필요하다. (C) 9월 1일은 메모 날짜다. (D) 휴대전화 앱은 새 방식이고 지금은 종이 양식이다.
포인트: 추론 — 메모 둘째 문단(새 방식과 현재 방식 비교)', 156),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is NOT mentioned as changing on October 1?', 'Questions 155-157 refer to the following memo.

MEMO

To: All Staff, Birchall & Stroud Consulting
From: Chiara Lombardi, Finance Director
Date: September 1
Subject: Changes to expense reimbursement

Beginning October 1, all expense claims must be submitted through the Claimwise mobile application. Paper claim forms will no longer be accepted after September 30.

With Claimwise, you simply photograph each receipt and select the related client project. Claims submitted by the 20th of a month will be reimbursed with that month''s salary payment; under the current system, employees often wait six weeks or longer.

Two rules are also changing on October 1. First, receipts will be required for every expense of $15 or more, down from $25. Second, any meal with clients that costs more than $200 must be approved by a department head before the meal takes place. The daily meal allowance for business trips remains $60.

Training sessions on the application will be held in Conference Room B on September 12 and 19. To sign up, contact Hyun-woo Baek at extension 263. Attendance is not mandatory, but it is strongly encouraged.', 'NONE', '["The amount allowed for meals during travel", "The way in which claims are submitted", "The expense level that calls for a receipt", "The approval needed for costly client meals"]', 'A', 1, '[사실 확인(NOT)] 정답 (A). "The daily meal allowance for business trips remains $60"이므로 출장 식비 한도는 바뀌지 않는다. 오답: (B) 종이 양식에서 Claimwise 앱 제출로 바뀐다. (C) 영수증 기준이 25달러에서 15달러로 내려간다. (D) 200달러가 넘는 고객 식사는 부서장의 사전 승인이 필요해진다.
포인트: 사실 확인(NOT) — 메모 셋째 문단(remains = 바뀌지 않음)', 157),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about The Copper Ladle?', 'Questions 158-160 refer to the following letter.

Wickersham County Health Department
300 Juniper Court, Wickersham

April 14

Ms. Noriko Hayashida
The Copper Ladle
18 Saddler Street
Wickersham

Dear Ms. Hayashida,

Thank you for your cooperation during the routine inspection of The Copper Ladle on April 9. I am pleased to inform you that your restaurant was found to comply with the county''s food-safety regulations in every area but one.

The thermometer in the walk-in refrigerator was giving inaccurate readings. Although the food stored there was at a safe temperature when I tested it myself, the thermometer must be replaced by April 30. There is no need to schedule another visit. Simply e-mail my office a photograph of the new device together with a copy of the sales receipt.

Your inspection certificate for the coming year is enclosed. County rules require that it be posted where customers can easily see it, for example beside the main entrance. The certificate is valid until April 30 of next year, and a renewal notice will be mailed to you about six weeks before that date.

Sincerely,

Farid Nazari
Senior Inspector', 'NONE', '["It recently opened on Saddler Street.", "It failed an inspection last year.", "It met all but one safety requirement.", "It stores food at an unsafe temperature."]', 'C', 1, '[사실 확인] 정답 (C). "your restaurant was found to comply with the county''s food-safety regulations in every area but one"을 ''안전 요건을 하나만 빼고 모두 충족했다''로 바꿔 말했다. 오답: (A) 개업 시기는 나오지 않는다. (B) 이번은 정기(routine) 점검이며 지난 점검 결과는 언급되지 않았다. (D) 보관된 음식은 "at a safe temperature"였고 문제는 온도계였다.
포인트: 사실 확인 — 편지 첫 문단(comply with의 바꿔 말하기)', 158),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is Ms. Hayashida asked to do by April 30?', 'Questions 158-160 refer to the following letter.

Wickersham County Health Department
300 Juniper Court, Wickersham

April 14

Ms. Noriko Hayashida
The Copper Ladle
18 Saddler Street
Wickersham

Dear Ms. Hayashida,

Thank you for your cooperation during the routine inspection of The Copper Ladle on April 9. I am pleased to inform you that your restaurant was found to comply with the county''s food-safety regulations in every area but one.

The thermometer in the walk-in refrigerator was giving inaccurate readings. Although the food stored there was at a safe temperature when I tested it myself, the thermometer must be replaced by April 30. There is no need to schedule another visit. Simply e-mail my office a photograph of the new device together with a copy of the sales receipt.

Your inspection certificate for the coming year is enclosed. County rules require that it be posted where customers can easily see it, for example beside the main entrance. The certificate is valid until April 30 of next year, and a renewal notice will be mailed to you about six weeks before that date.

Sincerely,

Farid Nazari
Senior Inspector', 'NONE', '["Have a device replaced", "Arrange a second inspection", "Return a renewal notice", "Discard some stored food"]', 'A', 1, '[세부 사항] 정답 (A). "the thermometer must be replaced by April 30"에서 4월 30일까지 할 일은 온도계 교체다. 오답: (B) "There is no need to schedule another visit"라고 했다. (C) 갱신 안내문은 내년에 보건과가 보내 주는 것이다. (D) 음식은 안전한 온도였으므로 버리라는 말이 없다.
포인트: 세부 사항 — 편지 둘째 문단(by = 완료 기한)', 159),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What was sent with the letter?', 'Questions 158-160 refer to the following letter.

Wickersham County Health Department
300 Juniper Court, Wickersham

April 14

Ms. Noriko Hayashida
The Copper Ladle
18 Saddler Street
Wickersham

Dear Ms. Hayashida,

Thank you for your cooperation during the routine inspection of The Copper Ladle on April 9. I am pleased to inform you that your restaurant was found to comply with the county''s food-safety regulations in every area but one.

The thermometer in the walk-in refrigerator was giving inaccurate readings. Although the food stored there was at a safe temperature when I tested it myself, the thermometer must be replaced by April 30. There is no need to schedule another visit. Simply e-mail my office a photograph of the new device together with a copy of the sales receipt.

Your inspection certificate for the coming year is enclosed. County rules require that it be posted where customers can easily see it, for example beside the main entrance. The certificate is valid until April 30 of next year, and a renewal notice will be mailed to you about six weeks before that date.

Sincerely,

Farid Nazari
Senior Inspector', 'NONE', '["A document to be displayed", "A copy of a sales receipt", "A photograph of a device", "A list of county regulations"]', 'A', 1, '[세부 사항] 정답 (A). "Your inspection certificate for the coming year is enclosed"와 "it be posted where customers can easily see it"에서 게시해야 하는 증명서가 동봉되었음을 알 수 있다. 오답: (B)·(C) 영수증 사본과 새 온도계 사진은 Ms. Hayashida가 이메일로 보낼 것이다. (D) 규정 목록을 동봉했다는 말은 없다.
포인트: 세부 사항 — 편지 셋째 문단(enclosed)', 160);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is the article mainly about?', 'Questions 161-163 refer to the following article.

HOLLINBROOK (May 6) — Fennimore Cycle Works, a family-run bicycle repair shop on Quarry Road, has been named Small Business of the Year by the Hollinbrook Chamber of Commerce. — [1] —.

Owner Marguerite Fennimore opened the shop 11 years ago with a single employee. Today it has a staff of 14 and services more than 6,000 bicycles a year. — [2] —. The judges praised the shop''s free monthly repair classes, which have taught basic maintenance to hundreds of residents.

"We never set out to win prizes," Ms. Fennimore said. "We simply wanted people to keep riding." — [3] —.

The award comes with a $5,000 grant, which Ms. Fennimore plans to spend on a mobile repair van. The van will visit neighborhoods on the east side of the city, where there is currently no bicycle shop. — [4] —.

The award will be presented at the chamber''s annual dinner on May 30 at the Granite Hall Hotel.', 'NONE', '["The opening of a new bicycle shop", "The sale of a business to a new owner", "A local company receiving an honor", "A hotel that will host a cycling event"]', 'C', 1, '[주제·목적] 정답 (C). 첫 문장 "has been named Small Business of the Year"가 글의 중심이며 ''지역 업체가 상을 받는다''로 요약된다. 오답: (A) 가게는 11년 전에 열었다. (B) 주인이 바뀐다는 내용은 없다. (D) 호텔은 시상식 저녁 행사 장소일 뿐이다.
포인트: 주제·목적 — 기사 첫 문장', 161),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about Fennimore Cycle Works?', 'Questions 161-163 refer to the following article.

HOLLINBROOK (May 6) — Fennimore Cycle Works, a family-run bicycle repair shop on Quarry Road, has been named Small Business of the Year by the Hollinbrook Chamber of Commerce. — [1] —.

Owner Marguerite Fennimore opened the shop 11 years ago with a single employee. Today it has a staff of 14 and services more than 6,000 bicycles a year. — [2] —. The judges praised the shop''s free monthly repair classes, which have taught basic maintenance to hundreds of residents.

"We never set out to win prizes," Ms. Fennimore said. "We simply wanted people to keep riding." — [3] —.

The award comes with a $5,000 grant, which Ms. Fennimore plans to spend on a mobile repair van. The van will visit neighborhoods on the east side of the city, where there is currently no bicycle shop. — [4] —.

The award will be presented at the chamber''s annual dinner on May 30 at the Granite Hall Hotel.', 'NONE', '["It has been in business for over two decades.", "It sells more than 6,000 bicycles each year.", "It is managed by the Chamber of Commerce.", "It offers instruction to residents at no cost."]', 'D', 1, '[사실 확인] 정답 (D). "the shop''s free monthly repair classes, which have taught basic maintenance to hundreds of residents"를 ''주민에게 무료로 교육을 제공한다''로 바꿔 말했다. 오답: (A) 문을 연 지 11년이다. (B) 6,000대는 판매가 아니라 수리(services)한 수다. (C) 상공회의소는 상을 주는 기관이다.
포인트: 사실 확인 — 기사 둘째 문단(free classes의 바꿔 말하기)', 162),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'In which of the positions marked [1], [2], [3], and [4] does the following sentence best belong? "She expects it to be on the road by the end of the summer."', 'Questions 161-163 refer to the following article.

HOLLINBROOK (May 6) — Fennimore Cycle Works, a family-run bicycle repair shop on Quarry Road, has been named Small Business of the Year by the Hollinbrook Chamber of Commerce. — [1] —.

Owner Marguerite Fennimore opened the shop 11 years ago with a single employee. Today it has a staff of 14 and services more than 6,000 bicycles a year. — [2] —. The judges praised the shop''s free monthly repair classes, which have taught basic maintenance to hundreds of residents.

"We never set out to win prizes," Ms. Fennimore said. "We simply wanted people to keep riding." — [3] —.

The award comes with a $5,000 grant, which Ms. Fennimore plans to spend on a mobile repair van. The van will visit neighborhoods on the east side of the city, where there is currently no bicycle shop. — [4] —.

The award will be presented at the chamber''s annual dinner on May 30 at the Granite Hall Hotel.', 'NONE', '["[1]", "[2]", "[3]", "[4]"]', 'D', 1, '[문장 삽입] 정답 (D). 삽입 문장의 it은 바로 앞의 "a mobile repair van"을 가리키고, on the road(운행에 들어가다)는 밴이 동쪽 동네를 돌 것이라는 설명 뒤에 이어져야 자연스럽다. 오답: (A) [1] 앞에는 it이 가리킬 차량이 없다. (B) [2] 앞은 직원 수와 수리 대수 이야기다. (C) [3] 앞은 수상 소감 인용이라 it이 가리킬 대상이 없다.
포인트: 문장 삽입 — 지시어 it과 앞 문장의 명사', 163),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What led to the changes in the policy?', 'Questions 164-167 refer to the following e-mail.

To: All Employees
From: Declan Murtagh, Human Resources
Date: January 8
Subject: Updated remote work policy

Dear Colleagues,

After reviewing the results of last fall''s staff survey, the management team has approved several changes to our remote work policy. They take effect on February 1.

Employees may now work from home up to three days per week instead of two. However, every team must be in the office on its designated "anchor day" so that meetings can be held in person. Department heads will announce their anchor days by January 20.

Because fewer people will be in the building on any given day, individual desks on the fourth floor will be converted to shared workstations. These must be reserved through the DeskPoint system no later than 5:00 P.M. on the previous business day. Lockers will be provided for personal items.

The company will continue to cover the cost of a monitor and keyboard for home use. In addition, employees who work remotely at least two days a week will be eligible for a monthly internet allowance of $40. To claim it, submit a copy of your internet bill with your regular expense report.

Please note that requests to work remotely full time will still be handled case by case and require the approval of a vice president.

Questions can be addressed to me directly.

Declan Murtagh', 'NONE', '["A decision to move to a smaller building", "Feedback collected from employees", "A request made by department heads", "An increase in the cost of internet service"]', 'B', 1, '[세부 사항] 정답 (B). "After reviewing the results of last fall''s staff survey"를 ''직원에게서 모은 의견''으로 바꿔 말했다. 오답: (A) 건물 이전 이야기는 없고 4층 좌석을 공유석으로 바꿀 뿐이다. (C) 부서장은 출근일을 알리는 역할이다. (D) 인터넷 보조금은 새 혜택이지 변경의 원인이 아니다.
포인트: 세부 사항 — 이메일 첫 문단(survey → feedback)', 164),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What will department heads do by January 20?', 'Questions 164-167 refer to the following e-mail.

To: All Employees
From: Declan Murtagh, Human Resources
Date: January 8
Subject: Updated remote work policy

Dear Colleagues,

After reviewing the results of last fall''s staff survey, the management team has approved several changes to our remote work policy. They take effect on February 1.

Employees may now work from home up to three days per week instead of two. However, every team must be in the office on its designated "anchor day" so that meetings can be held in person. Department heads will announce their anchor days by January 20.

Because fewer people will be in the building on any given day, individual desks on the fourth floor will be converted to shared workstations. These must be reserved through the DeskPoint system no later than 5:00 P.M. on the previous business day. Lockers will be provided for personal items.

The company will continue to cover the cost of a monitor and keyboard for home use. In addition, employees who work remotely at least two days a week will be eligible for a monthly internet allowance of $40. To claim it, submit a copy of your internet bill with your regular expense report.

Please note that requests to work remotely full time will still be handled case by case and require the approval of a vice president.

Questions can be addressed to me directly.

Declan Murtagh', 'NONE', '["Approve requests for lockers", "Reserve workstations for their teams", "Tell staff which day they must come in", "Submit survey results to management"]', 'C', 1, '[세부 사항] 정답 (C). "Department heads will announce their anchor days by January 20"이고 anchor day는 팀 전원이 사무실에 나와야 하는 날이다. 오답: (A) 사물함은 회사가 제공한다고만 했다. (B) 좌석 예약은 직원이 DeskPoint로 직접 한다. (D) 설문은 지난가을에 이미 끝났다.
포인트: 세부 사항 — 이메일 둘째 문단(by = 기한)', 165),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is NOT mentioned as something the company will provide?', 'Questions 164-167 refer to the following e-mail.

To: All Employees
From: Declan Murtagh, Human Resources
Date: January 8
Subject: Updated remote work policy

Dear Colleagues,

After reviewing the results of last fall''s staff survey, the management team has approved several changes to our remote work policy. They take effect on February 1.

Employees may now work from home up to three days per week instead of two. However, every team must be in the office on its designated "anchor day" so that meetings can be held in person. Department heads will announce their anchor days by January 20.

Because fewer people will be in the building on any given day, individual desks on the fourth floor will be converted to shared workstations. These must be reserved through the DeskPoint system no later than 5:00 P.M. on the previous business day. Lockers will be provided for personal items.

The company will continue to cover the cost of a monitor and keyboard for home use. In addition, employees who work remotely at least two days a week will be eligible for a monthly internet allowance of $40. To claim it, submit a copy of your internet bill with your regular expense report.

Please note that requests to work remotely full time will still be handled case by case and require the approval of a vice president.

Questions can be addressed to me directly.

Declan Murtagh', 'NONE', '["Storage space for belongings", "Equipment for a home office", "Money toward internet service", "Transportation to the office"]', 'D', 1, '[사실 확인(NOT)] 정답 (D). 출퇴근 교통편은 어디에도 나오지 않는다. 오답: (A) "Lockers will be provided for personal items". (B) "a monitor and keyboard for home use". (C) "a monthly internet allowance of $40".
포인트: 사실 확인(NOT) — 이메일 셋째·넷째 문단', 166),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'The word "cover" in paragraph 4, line 1, is closest in meaning to', 'Questions 164-167 refer to the following e-mail.

To: All Employees
From: Declan Murtagh, Human Resources
Date: January 8
Subject: Updated remote work policy

Dear Colleagues,

After reviewing the results of last fall''s staff survey, the management team has approved several changes to our remote work policy. They take effect on February 1.

Employees may now work from home up to three days per week instead of two. However, every team must be in the office on its designated "anchor day" so that meetings can be held in person. Department heads will announce their anchor days by January 20.

Because fewer people will be in the building on any given day, individual desks on the fourth floor will be converted to shared workstations. These must be reserved through the DeskPoint system no later than 5:00 P.M. on the previous business day. Lockers will be provided for personal items.

The company will continue to cover the cost of a monitor and keyboard for home use. In addition, employees who work remotely at least two days a week will be eligible for a monthly internet allowance of $40. To claim it, submit a copy of your internet bill with your regular expense report.

Please note that requests to work remotely full time will still be handled case by case and require the approval of a vice president.

Questions can be addressed to me directly.

Declan Murtagh', 'NONE', '["protect", "pay", "report", "hide"]', 'B', 1, '[동의어] 정답 (B). "cover the cost of a monitor and keyboard"는 비용을 회사가 부담한다는 뜻이므로 pay가 가장 가깝다. 오답: (A) 보호하다, (D) 가리다는 cover의 다른 뜻이고, (C) 보도·보고하다는 뜻도 the cost를 목적어로 한 이 문장에는 맞지 않는다.
포인트: 동의어 — 이메일 넷째 문단(cover의 문맥상 뜻)', 167),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is the purpose of the Web page?', 'Questions 168-171 refer to the following Web page.

Town of Yarrowfield — Parks Department
Community Garden Plots

Every spring, the Parks Department rents 120 garden plots at three sites to residents who would like to grow their own vegetables, herbs, and flowers. — [1] —. Each plot measures 3 meters by 6 meters and comes with access to water, a shared tool shed, and free compost.

Who may apply
Any Yarrowfield resident aged 18 or older may rent one plot per household. Gardeners who rented a plot last season have until February 15 to renew it. — [2] —. Plots that have not been renewed by that date are offered to new applicants, in the order in which their applications were received, starting on March 1. New applicants who are offered a plot must confirm by e-mail within five days that they will take it.

Fees
The seasonal fee is $60 and is payable when a plot is assigned. A deposit of $25 is also collected; it is refunded in November once the plot has been cleared of plants, stakes, and fencing. The seasonal fee is waived for gardeners who agree to lead two of our Saturday workshops for beginners. — [3] —.

Rules
Plots must be planted by May 20 and kept free of weeds until the season ends on October 31. A plot that is still empty after May 20 will be given to the next person on the waiting list, and the seasonal fee will not be returned. — [4] —.

To apply, complete the online form on this page or visit the Parks Department office in the Town Hall.', 'NONE', '["To request feedback on a town service", "To invite residents to a gardening workshop", "To describe a program for local gardeners", "To report a change in the town''s rental fees"]', 'C', 1, '[주제·목적] 정답 (C). 구획 수, 신청 자격, 요금, 규칙, 신청 방법을 차례로 안내하므로 주민 텃밭 대여 제도를 설명하는 글이다. 오답: (A) 의견을 구하는 내용은 없다. (B) 워크숍은 이용료 면제 조건으로 나올 뿐이다. (D) 요금이 바뀌었다는 내용은 없다.
포인트: 주제·목적 — 웹 페이지 전체 구성', 168),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about gardeners who rented a plot last season?', 'Questions 168-171 refer to the following Web page.

Town of Yarrowfield — Parks Department
Community Garden Plots

Every spring, the Parks Department rents 120 garden plots at three sites to residents who would like to grow their own vegetables, herbs, and flowers. — [1] —. Each plot measures 3 meters by 6 meters and comes with access to water, a shared tool shed, and free compost.

Who may apply
Any Yarrowfield resident aged 18 or older may rent one plot per household. Gardeners who rented a plot last season have until February 15 to renew it. — [2] —. Plots that have not been renewed by that date are offered to new applicants, in the order in which their applications were received, starting on March 1. New applicants who are offered a plot must confirm by e-mail within five days that they will take it.

Fees
The seasonal fee is $60 and is payable when a plot is assigned. A deposit of $25 is also collected; it is refunded in November once the plot has been cleared of plants, stakes, and fencing. The seasonal fee is waived for gardeners who agree to lead two of our Saturday workshops for beginners. — [3] —.

Rules
Plots must be planted by May 20 and kept free of weeds until the season ends on October 31. A plot that is still empty after May 20 will be given to the next person on the waiting list, and the seasonal fee will not be returned. — [4] —.

To apply, complete the online form on this page or visit the Parks Department office in the Town Hall.', 'NONE', '["They must pay a larger deposit this year.", "They are given priority over new applicants.", "They may rent a second plot for their household.", "They are required to lead a Saturday workshop."]', 'B', 1, '[추론] 정답 (B). "Gardeners who rented a plot last season have until February 15 to renew it"이고 갱신되지 않은 구획만 3월 1일부터 "offered to new applicants"이므로 기존 이용자가 먼저 자리를 지킬 수 있다. 오답: (A) 보증금은 25달러로 같다. (C) "one plot per household"다. (D) 워크숍 진행은 이용료를 면제받으려는 사람만 한다.
포인트: 추론 — ''Who may apply'' 문단(갱신과 신규 신청 구별)', 169),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What are new applicants who are offered a plot required to do?', 'Questions 168-171 refer to the following Web page.

Town of Yarrowfield — Parks Department
Community Garden Plots

Every spring, the Parks Department rents 120 garden plots at three sites to residents who would like to grow their own vegetables, herbs, and flowers. — [1] —. Each plot measures 3 meters by 6 meters and comes with access to water, a shared tool shed, and free compost.

Who may apply
Any Yarrowfield resident aged 18 or older may rent one plot per household. Gardeners who rented a plot last season have until February 15 to renew it. — [2] —. Plots that have not been renewed by that date are offered to new applicants, in the order in which their applications were received, starting on March 1. New applicants who are offered a plot must confirm by e-mail within five days that they will take it.

Fees
The seasonal fee is $60 and is payable when a plot is assigned. A deposit of $25 is also collected; it is refunded in November once the plot has been cleared of plants, stakes, and fencing. The seasonal fee is waived for gardeners who agree to lead two of our Saturday workshops for beginners. — [3] —.

Rules
Plots must be planted by May 20 and kept free of weeds until the season ends on October 31. A plot that is still empty after May 20 will be given to the next person on the waiting list, and the seasonal fee will not be returned. — [4] —.

To apply, complete the online form on this page or visit the Parks Department office in the Town Hall.', 'NONE', '["Accept the plot within a set period", "Lead two workshops for beginning gardeners", "Clear their plot of stakes and fencing", "Renew their plot before February 15"]', 'A', 1, '[세부 사항] 정답 (A). "New applicants who are offered a plot must confirm by e-mail within five days that they will take it"를 ''정해진 기간 안에 구획을 받겠다고 확정하다''로 바꿔 말했다. 오답: (B) 워크숍 두 번 진행은 이용료를 면제받으려는 사람이 고르는 일이다. (C) 말뚝·울타리 정리는 11월에 보증금을 돌려받는 조건이다. (D) 2월 15일까지의 갱신은 지난 시즌 이용자가 하는 일이다.
포인트: 세부 사항 — ''Who may apply'' 문단(confirm 배정 확정과 renew 갱신 구별)', 170),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'In which of the positions marked [1], [2], [3], and [4] does the following sentence best belong? "Each of these lasts about an hour, and no teaching experience is required."', 'Questions 168-171 refer to the following Web page.

Town of Yarrowfield — Parks Department
Community Garden Plots

Every spring, the Parks Department rents 120 garden plots at three sites to residents who would like to grow their own vegetables, herbs, and flowers. — [1] —. Each plot measures 3 meters by 6 meters and comes with access to water, a shared tool shed, and free compost.

Who may apply
Any Yarrowfield resident aged 18 or older may rent one plot per household. Gardeners who rented a plot last season have until February 15 to renew it. — [2] —. Plots that have not been renewed by that date are offered to new applicants, in the order in which their applications were received, starting on March 1. New applicants who are offered a plot must confirm by e-mail within five days that they will take it.

Fees
The seasonal fee is $60 and is payable when a plot is assigned. A deposit of $25 is also collected; it is refunded in November once the plot has been cleared of plants, stakes, and fencing. The seasonal fee is waived for gardeners who agree to lead two of our Saturday workshops for beginners. — [3] —.

Rules
Plots must be planted by May 20 and kept free of weeds until the season ends on October 31. A plot that is still empty after May 20 will be given to the next person on the waiting list, and the seasonal fee will not be returned. — [4] —.

To apply, complete the online form on this page or visit the Parks Department office in the Town Hall.', 'NONE', '["[1]", "[2]", "[3]", "[4]"]', 'C', 1, '[문장 삽입] 정답 (C). Each of these가 바로 앞 문장의 "Saturday workshops for beginners"를 가리키고 그 시간과 자격을 덧붙이므로 [3]에 들어가야 한다. 오답: (A) [1] 앞의 plots와 sites는 ''한 시간쯤 걸린다''의 주어가 될 수 없다. (B) [2] 앞은 갱신 기한 이야기다. (D) [4] 앞은 빈 구획 처리 규칙이라 가리킬 대상이 없다.
포인트: 문장 삽입 — 지시어 these와 앞 문장의 명사', 171),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'Why did Ms. Takemoto contact the company?', 'Questions 172-175 refer to the following online chat discussion.

Sayuri Takemoto (2:04 P.M.)
Hello. I ordered a Trailmate rain jacket in size medium, order number 58117. It arrived yesterday, but it''s too tight in the shoulders. Can I exchange it for a large?

Rafael Duarte (2:06 P.M.)
Hello, Ms. Takemoto. I''d be happy to help. Exchanges are free within 30 days as long as the tags are still attached. Is that the case?

Sayuri Takemoto (2:07 P.M.)
Yes, I only tried it on once.

Rafael Duarte (2:09 P.M.)
Good. Let me bring in a colleague from our warehouse to check our stock. Linh, do we have the Trailmate in large, forest green?

Linh Chow (2:12 P.M.)
Not in forest green, I''m afraid. The next shipment is due on October 21. We do have large in navy and in gray right now.

Sayuri Takemoto (2:14 P.M.)
I''m leaving for a hiking trip on October 16, so I can''t wait that long. Navy will do.

Rafael Duarte (2:16 P.M.)
Understood. I''ve just e-mailed you a prepaid return label. Drop the medium jacket off at any Parcelway location, and we''ll ship the navy one as soon as the package is scanned there.

Sayuri Takemoto (2:17 P.M.)
Will it get here in time?

Linh Chow (2:19 P.M.)
Standard delivery takes three to five business days. If you drop the jacket off by tomorrow, October 8, you should be fine. I can also upgrade you to express shipping at no charge, just to be safe.

Sayuri Takemoto (2:20 P.M.)
Please do. Thank you both.', 'NONE', '["To report that an item arrived damaged", "To request a different size of a product", "To ask when a shipment is due to arrive", "To check whether a color is available"]', 'B', 1, '[주제·목적] 정답 (B). "it''s too tight in the shoulders. Can I exchange it for a large?"에서 다른 치수로 바꾸려는 것임을 알 수 있다. 오답: (A) 파손이 아니라 치수 문제다. (C) 입고일은 직원이 나중에 알려 준 것이고 재킷은 어제 이미 도착했다. (D) 색상 재고는 직원이 창고에 확인한 것이지 연락한 이유가 아니다.
포인트: 주제·목적 — 채팅 첫 메시지', 172),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What does Ms. Chow say about the forest green jacket in large?', 'Questions 172-175 refer to the following online chat discussion.

Sayuri Takemoto (2:04 P.M.)
Hello. I ordered a Trailmate rain jacket in size medium, order number 58117. It arrived yesterday, but it''s too tight in the shoulders. Can I exchange it for a large?

Rafael Duarte (2:06 P.M.)
Hello, Ms. Takemoto. I''d be happy to help. Exchanges are free within 30 days as long as the tags are still attached. Is that the case?

Sayuri Takemoto (2:07 P.M.)
Yes, I only tried it on once.

Rafael Duarte (2:09 P.M.)
Good. Let me bring in a colleague from our warehouse to check our stock. Linh, do we have the Trailmate in large, forest green?

Linh Chow (2:12 P.M.)
Not in forest green, I''m afraid. The next shipment is due on October 21. We do have large in navy and in gray right now.

Sayuri Takemoto (2:14 P.M.)
I''m leaving for a hiking trip on October 16, so I can''t wait that long. Navy will do.

Rafael Duarte (2:16 P.M.)
Understood. I''ve just e-mailed you a prepaid return label. Drop the medium jacket off at any Parcelway location, and we''ll ship the navy one as soon as the package is scanned there.

Sayuri Takemoto (2:17 P.M.)
Will it get here in time?

Linh Chow (2:19 P.M.)
Standard delivery takes three to five business days. If you drop the jacket off by tomorrow, October 8, you should be fine. I can also upgrade you to express shipping at no charge, just to be safe.

Sayuri Takemoto (2:20 P.M.)
Please do. Thank you both.', 'NONE', '["It has been discontinued.", "It costs more than the navy one.", "It is not currently in stock.", "It will arrive on October 16."]', 'C', 1, '[세부 사항] 정답 (C). "Not in forest green, I''m afraid. The next shipment is due on October 21"에서 지금은 재고가 없다는 것을 알 수 있다. 오답: (A) 다음 입고가 예정되어 있으므로 단종이 아니다. (B) 가격 차이 언급은 없다. (D) 10월 16일은 Ms. Takemoto의 출발일이고 입고는 21일이다.
포인트: 세부 사항 — 2:12 P.M. 메시지(재고와 입고일)', 173),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'At 2:14 P.M., what does Ms. Takemoto most likely mean when she writes, "Navy will do"?', 'Questions 172-175 refer to the following online chat discussion.

Sayuri Takemoto (2:04 P.M.)
Hello. I ordered a Trailmate rain jacket in size medium, order number 58117. It arrived yesterday, but it''s too tight in the shoulders. Can I exchange it for a large?

Rafael Duarte (2:06 P.M.)
Hello, Ms. Takemoto. I''d be happy to help. Exchanges are free within 30 days as long as the tags are still attached. Is that the case?

Sayuri Takemoto (2:07 P.M.)
Yes, I only tried it on once.

Rafael Duarte (2:09 P.M.)
Good. Let me bring in a colleague from our warehouse to check our stock. Linh, do we have the Trailmate in large, forest green?

Linh Chow (2:12 P.M.)
Not in forest green, I''m afraid. The next shipment is due on October 21. We do have large in navy and in gray right now.

Sayuri Takemoto (2:14 P.M.)
I''m leaving for a hiking trip on October 16, so I can''t wait that long. Navy will do.

Rafael Duarte (2:16 P.M.)
Understood. I''ve just e-mailed you a prepaid return label. Drop the medium jacket off at any Parcelway location, and we''ll ship the navy one as soon as the package is scanned there.

Sayuri Takemoto (2:17 P.M.)
Will it get here in time?

Linh Chow (2:19 P.M.)
Standard delivery takes three to five business days. If you drop the jacket off by tomorrow, October 8, you should be fine. I can also upgrade you to express shipping at no charge, just to be safe.

Sayuri Takemoto (2:20 P.M.)
Please do. Thank you both.', 'NONE', '["She wants to wait for the October 21 shipment.", "She would rather receive a refund.", "She believes navy is the most popular color.", "She is willing to accept a different color."]', 'D', 1, '[의도 파악] 정답 (D). 원하던 forest green은 21일에야 들어오는데 16일에 떠나야 해서 기다릴 수 없다고 한 뒤 한 말이므로, 남색이면 괜찮다 곧 다른 색을 받겠다는 뜻이다. 오답: (A) "I can''t wait that long"이라고 했다. (B) 환불이 아니라 교환을 계속 진행한다. (C) 인기 여부는 언급되지 않았다.
포인트: 의도 파악 — 앞 메시지의 입고일과 출발일', 174),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What will Ms. Takemoto most likely do next?', 'Questions 172-175 refer to the following online chat discussion.

Sayuri Takemoto (2:04 P.M.)
Hello. I ordered a Trailmate rain jacket in size medium, order number 58117. It arrived yesterday, but it''s too tight in the shoulders. Can I exchange it for a large?

Rafael Duarte (2:06 P.M.)
Hello, Ms. Takemoto. I''d be happy to help. Exchanges are free within 30 days as long as the tags are still attached. Is that the case?

Sayuri Takemoto (2:07 P.M.)
Yes, I only tried it on once.

Rafael Duarte (2:09 P.M.)
Good. Let me bring in a colleague from our warehouse to check our stock. Linh, do we have the Trailmate in large, forest green?

Linh Chow (2:12 P.M.)
Not in forest green, I''m afraid. The next shipment is due on October 21. We do have large in navy and in gray right now.

Sayuri Takemoto (2:14 P.M.)
I''m leaving for a hiking trip on October 16, so I can''t wait that long. Navy will do.

Rafael Duarte (2:16 P.M.)
Understood. I''ve just e-mailed you a prepaid return label. Drop the medium jacket off at any Parcelway location, and we''ll ship the navy one as soon as the package is scanned there.

Sayuri Takemoto (2:17 P.M.)
Will it get here in time?

Linh Chow (2:19 P.M.)
Standard delivery takes three to five business days. If you drop the jacket off by tomorrow, October 8, you should be fine. I can also upgrade you to express shipping at no charge, just to be safe.

Sayuri Takemoto (2:20 P.M.)
Please do. Thank you both.', 'NONE', '["Take a package to a drop-off location", "Pay an extra fee for faster shipping", "Place a new order on the company''s website", "Pick up a jacket at a warehouse"]', 'A', 1, '[추론] 정답 (A). "Drop the medium jacket off at any Parcelway location"과 "If you drop the jacket off by tomorrow"에서 반품 상자를 접수처에 맡기는 것이 다음 할 일임을 알 수 있다. 오답: (B) 빠른 배송은 무료로 올려 준다. (C) 새 주문 없이 반품이 스캔되면 남색 재킷을 보내 준다. (D) 창고는 직원이 재고를 확인한 곳일 뿐 방문 수령 이야기는 없다.
포인트: 추론(다음 행동) — 2:16 P.M.·2:19 P.M. 메시지', 175),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'According to the itinerary, what is included in the price of the tour?', 'Questions 176-180 refer to the following itinerary and e-mail.

[Itinerary]
Skyline & Shore Travel — Carrowmere
Coastal Heritage Tour: 4 days / 3 nights

A deposit of $150 per person secures your place on any departure. The balance is due 21 days before departure. Cancellations made after the balance due date are subject to a $150 fee.

Departure dates: May 14, June 11, July 9
Price: $780 per person (double room); single-room supplement $120

Day 1 — 8:00 A.M. coach departs from Carrowmere Central Station; afternoon guided walk in the old town of Lowick; dinner at the hotel
Day 2 — Morning ferry to Sable Island; lighthouse tour; free afternoon
Day 3 — Visit to the Wrenfield glassworks; vineyard tour; evening concert (optional, $35)
Day 4 — Morning at the Lowick harbor market; coach returns to Carrowmere by 5:00 P.M.

Included: coach travel, three nights at the Harbor Crest Hotel, breakfast on Days 2-4, dinner on Day 1, all entrance fees
Not included: lunches, travel insurance

────────────────────

[E-mail]
To: Nora Fitzwilliam
From: Tiago Ibarra, Skyline & Shore Travel
Date: May 29
Subject: Changes to your June 11 tour

Dear Ms. Fitzwilliam,

Thank you for booking the Coastal Heritage Tour for two people. I am writing to let you know about two changes.

First, the ferry operator has suspended service to Sable Island until the end of June while its dock is repaired. In place of the island visit, your group will take a guided boat cruise along the Lowick cliffs, followed by the free afternoon as planned. To make up for the change, we will provide the optional evening concert to all travelers at no charge.

Second, because of a road race in the city center, the coach will leave from the north entrance of Bellmead Park rather than from Central Station. The departure time has not changed.

If these changes do not suit you, you may transfer to the July 9 departure or cancel for a full refund, with no fee, provided that you notify us by June 4. Otherwise, no action is required.

Sincerely,
Tiago Ibarra', 'NONE', '["Insurance for travelers", "A midday meal each day", "Admission to the sites visited", "A private room at the hotel"]', 'C', 1, '[세부 사항] 정답 (C). Included 항목의 "all entrance fees"를 ''방문지 입장료''로 바꿔 말했다. 오답: (A) 여행 보험과 (B) 점심은 Not included에 있다. (D) 가격은 2인실 기준이고 1인실은 120달러를 더 낸다.
포인트: 세부 사항 — 일정표 Included 항목', 176),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'In the itinerary, the word "secures" in paragraph 1, line 1, is closest in meaning to', 'Questions 176-180 refer to the following itinerary and e-mail.

[Itinerary]
Skyline & Shore Travel — Carrowmere
Coastal Heritage Tour: 4 days / 3 nights

A deposit of $150 per person secures your place on any departure. The balance is due 21 days before departure. Cancellations made after the balance due date are subject to a $150 fee.

Departure dates: May 14, June 11, July 9
Price: $780 per person (double room); single-room supplement $120

Day 1 — 8:00 A.M. coach departs from Carrowmere Central Station; afternoon guided walk in the old town of Lowick; dinner at the hotel
Day 2 — Morning ferry to Sable Island; lighthouse tour; free afternoon
Day 3 — Visit to the Wrenfield glassworks; vineyard tour; evening concert (optional, $35)
Day 4 — Morning at the Lowick harbor market; coach returns to Carrowmere by 5:00 P.M.

Included: coach travel, three nights at the Harbor Crest Hotel, breakfast on Days 2-4, dinner on Day 1, all entrance fees
Not included: lunches, travel insurance

────────────────────

[E-mail]
To: Nora Fitzwilliam
From: Tiago Ibarra, Skyline & Shore Travel
Date: May 29
Subject: Changes to your June 11 tour

Dear Ms. Fitzwilliam,

Thank you for booking the Coastal Heritage Tour for two people. I am writing to let you know about two changes.

First, the ferry operator has suspended service to Sable Island until the end of June while its dock is repaired. In place of the island visit, your group will take a guided boat cruise along the Lowick cliffs, followed by the free afternoon as planned. To make up for the change, we will provide the optional evening concert to all travelers at no charge.

Second, because of a road race in the city center, the coach will leave from the north entrance of Bellmead Park rather than from Central Station. The departure time has not changed.

If these changes do not suit you, you may transfer to the July 9 departure or cancel for a full refund, with no fee, provided that you notify us by June 4. Otherwise, no action is required.

Sincerely,
Tiago Ibarra', 'NONE', '["guarantees", "strengthens", "defends", "attaches"]', 'A', 1, '[동의어] 정답 (A). "A deposit of $150 per person secures your place"는 예약금을 내면 자리가 확실히 잡힌다는 뜻이므로 guarantees가 가장 가깝다. 오답: (B) 튼튼하게 하다, (C) 지키다·방어하다, (D) 붙이다는 your place를 목적어로 한 이 문맥에 맞지 않는다.
포인트: 동의어 — 일정표 첫 문단(secure의 문맥상 뜻)', 177),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'According to the e-mail, why is one activity being replaced?', 'Questions 176-180 refer to the following itinerary and e-mail.

[Itinerary]
Skyline & Shore Travel — Carrowmere
Coastal Heritage Tour: 4 days / 3 nights

A deposit of $150 per person secures your place on any departure. The balance is due 21 days before departure. Cancellations made after the balance due date are subject to a $150 fee.

Departure dates: May 14, June 11, July 9
Price: $780 per person (double room); single-room supplement $120

Day 1 — 8:00 A.M. coach departs from Carrowmere Central Station; afternoon guided walk in the old town of Lowick; dinner at the hotel
Day 2 — Morning ferry to Sable Island; lighthouse tour; free afternoon
Day 3 — Visit to the Wrenfield glassworks; vineyard tour; evening concert (optional, $35)
Day 4 — Morning at the Lowick harbor market; coach returns to Carrowmere by 5:00 P.M.

Included: coach travel, three nights at the Harbor Crest Hotel, breakfast on Days 2-4, dinner on Day 1, all entrance fees
Not included: lunches, travel insurance

────────────────────

[E-mail]
To: Nora Fitzwilliam
From: Tiago Ibarra, Skyline & Shore Travel
Date: May 29
Subject: Changes to your June 11 tour

Dear Ms. Fitzwilliam,

Thank you for booking the Coastal Heritage Tour for two people. I am writing to let you know about two changes.

First, the ferry operator has suspended service to Sable Island until the end of June while its dock is repaired. In place of the island visit, your group will take a guided boat cruise along the Lowick cliffs, followed by the free afternoon as planned. To make up for the change, we will provide the optional evening concert to all travelers at no charge.

Second, because of a road race in the city center, the coach will leave from the north entrance of Bellmead Park rather than from Central Station. The departure time has not changed.

If these changes do not suit you, you may transfer to the July 9 departure or cancel for a full refund, with no fee, provided that you notify us by June 4. Otherwise, no action is required.

Sincerely,
Tiago Ibarra', 'NONE', '["Too few travelers signed up for it.", "A lighthouse has been closed to visitors.", "A road race will block access to the harbor.", "A ferry is temporarily out of service."]', 'D', 1, '[세부 사항] 정답 (D). "the ferry operator has suspended service to Sable Island until the end of June"을 ''여객선이 일시적으로 운항하지 않는다''로 바꿔 말했다. 오답: (A) 신청 인원 이야기는 없다. (B) 수리하는 것은 등대가 아니라 선착장이다. (C) 도로 경주는 버스 출발 장소가 바뀐 이유다.
포인트: 세부 사항 — 이메일 둘째 문단', 178),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is the regular price of the activity that will now be offered free of charge?', 'Questions 176-180 refer to the following itinerary and e-mail.

[Itinerary]
Skyline & Shore Travel — Carrowmere
Coastal Heritage Tour: 4 days / 3 nights

A deposit of $150 per person secures your place on any departure. The balance is due 21 days before departure. Cancellations made after the balance due date are subject to a $150 fee.

Departure dates: May 14, June 11, July 9
Price: $780 per person (double room); single-room supplement $120

Day 1 — 8:00 A.M. coach departs from Carrowmere Central Station; afternoon guided walk in the old town of Lowick; dinner at the hotel
Day 2 — Morning ferry to Sable Island; lighthouse tour; free afternoon
Day 3 — Visit to the Wrenfield glassworks; vineyard tour; evening concert (optional, $35)
Day 4 — Morning at the Lowick harbor market; coach returns to Carrowmere by 5:00 P.M.

Included: coach travel, three nights at the Harbor Crest Hotel, breakfast on Days 2-4, dinner on Day 1, all entrance fees
Not included: lunches, travel insurance

────────────────────

[E-mail]
To: Nora Fitzwilliam
From: Tiago Ibarra, Skyline & Shore Travel
Date: May 29
Subject: Changes to your June 11 tour

Dear Ms. Fitzwilliam,

Thank you for booking the Coastal Heritage Tour for two people. I am writing to let you know about two changes.

First, the ferry operator has suspended service to Sable Island until the end of June while its dock is repaired. In place of the island visit, your group will take a guided boat cruise along the Lowick cliffs, followed by the free afternoon as planned. To make up for the change, we will provide the optional evening concert to all travelers at no charge.

Second, because of a road race in the city center, the coach will leave from the north entrance of Bellmead Park rather than from Central Station. The departure time has not changed.

If these changes do not suit you, you may transfer to the July 9 departure or cancel for a full refund, with no fee, provided that you notify us by June 4. Otherwise, no action is required.

Sincerely,
Tiago Ibarra', 'NONE', '["$35", "$120", "$150", "$780"]', 'A', 1, '[연계(문서1+2)] 정답 (A). 이메일의 "we will provide the optional evening concert to all travelers at no charge"와 일정표 Day 3의 "evening concert (optional, $35)"를 연결하면 35달러다. 오답: (B) 1인실 추가 요금, (C) 예약금·취소 수수료, (D) 1인 여행 요금이다.
포인트: 연계 문항 — 일정표 Day 3 + 이메일 둘째 문단', 179),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What option is available to Ms. Fitzwilliam if she contacts the agency by June 4?', 'Questions 176-180 refer to the following itinerary and e-mail.

[Itinerary]
Skyline & Shore Travel — Carrowmere
Coastal Heritage Tour: 4 days / 3 nights

A deposit of $150 per person secures your place on any departure. The balance is due 21 days before departure. Cancellations made after the balance due date are subject to a $150 fee.

Departure dates: May 14, June 11, July 9
Price: $780 per person (double room); single-room supplement $120

Day 1 — 8:00 A.M. coach departs from Carrowmere Central Station; afternoon guided walk in the old town of Lowick; dinner at the hotel
Day 2 — Morning ferry to Sable Island; lighthouse tour; free afternoon
Day 3 — Visit to the Wrenfield glassworks; vineyard tour; evening concert (optional, $35)
Day 4 — Morning at the Lowick harbor market; coach returns to Carrowmere by 5:00 P.M.

Included: coach travel, three nights at the Harbor Crest Hotel, breakfast on Days 2-4, dinner on Day 1, all entrance fees
Not included: lunches, travel insurance

────────────────────

[E-mail]
To: Nora Fitzwilliam
From: Tiago Ibarra, Skyline & Shore Travel
Date: May 29
Subject: Changes to your June 11 tour

Dear Ms. Fitzwilliam,

Thank you for booking the Coastal Heritage Tour for two people. I am writing to let you know about two changes.

First, the ferry operator has suspended service to Sable Island until the end of June while its dock is repaired. In place of the island visit, your group will take a guided boat cruise along the Lowick cliffs, followed by the free afternoon as planned. To make up for the change, we will provide the optional evening concert to all travelers at no charge.

Second, because of a road race in the city center, the coach will leave from the north entrance of Bellmead Park rather than from Central Station. The departure time has not changed.

If these changes do not suit you, you may transfer to the July 9 departure or cancel for a full refund, with no fee, provided that you notify us by June 4. Otherwise, no action is required.

Sincerely,
Tiago Ibarra', 'NONE', '["Pay the remaining balance for her tour", "Request a seat on an earlier coach", "Take the same tour on a later date", "Add another traveler to her booking"]', 'C', 1, '[세부 사항] 정답 (C). "you may transfer to the July 9 departure ... provided that you notify us by June 4"를 ''같은 투어를 더 늦은 날짜에 가다''로 바꿔 말했다. 오답: (A) 잔금 이야기는 이메일에 없다. (B) 출발 시각은 그대로이며 더 이른 버스는 없다. (D) 인원 추가는 언급되지 않았다.
포인트: 세부 사항 — 이메일 넷째 문단(by = 통보 기한)', 180);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What does Mr. Okonjo mention about himself in the survey?', 'Questions 181-185 refer to the following survey and e-mail.

[Survey]
Stanmoor Business Development Center
Workshop Feedback Survey

Workshop: Writing Effective Grant Proposals
Date: March 6
Location: Birch Room (seats 24)
Instructor: Dr. Leona Szabo
Participant name (optional): Chidi Okonjo
Organization: Dunlavin Youth Sports Association

Please rate each item from 1 (poor) to 5 (excellent).
Content of the workshop: 5
Instructor''s knowledge: 5
Printed materials: 4
Room and equipment: 2
Length of the workshop: 3

What did you find most useful?
The sample budgets. I had never prepared one before, and Dr. Szabo explained each line clearly.

What could be improved?
The room was crowded, and people at the back could not see the screen. Also, three hours was not enough. We ran out of time before we could practice writing the project summary, which was the part I most wanted to work on.

Would you like to be notified of future workshops? Yes

────────────────────

[E-mail]
To: Chidi Okonjo
From: Bridget Amadi, Program Coordinator
Date: March 13
Subject: Follow-up session on April 10

Dear Mr. Okonjo,

Thank you for completing our survey after last week''s workshop. Several participants made comments similar to yours, and we have acted on them.

Dr. Szabo has agreed to lead a follow-up session on April 10 from 1:00 to 3:00 P.M. It will be devoted entirely to the topic that had to be left out on March 6. Because it is being offered to make up for that omission, there is no fee for anyone who attended the original workshop. Others may register for $30.

The session will be held in the Dalton Room on the second floor, which seats 60, rather than in the Birch Room. Please bring a draft of a proposal you are working on, even a rough one, as participants will exchange drafts and comment on them.

To reserve a seat, simply reply to this message by April 3.

Best regards,
Bridget Amadi', 'NONE', '["He lacked experience in preparing budgets.", "He attended the workshop with several coworkers.", "He had taken Dr. Szabo''s class before.", "He helped Dr. Szabo prepare the materials."]', 'A', 1, '[세부 사항] 정답 (A). "The sample budgets. I had never prepared one before"에서 예산서를 만들어 본 적이 없었음을 알 수 있다. 오답: (B) 동료 동행 언급은 없다. (C) 이전 수강 이야기는 없다. (D) 인쇄 자료는 평가 항목일 뿐 준비를 도왔다는 말은 없다.
포인트: 세부 사항 — 설문 서술 답변', 181),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'To what aspect of the workshop did Mr. Okonjo give the lowest rating?', 'Questions 181-185 refer to the following survey and e-mail.

[Survey]
Stanmoor Business Development Center
Workshop Feedback Survey

Workshop: Writing Effective Grant Proposals
Date: March 6
Location: Birch Room (seats 24)
Instructor: Dr. Leona Szabo
Participant name (optional): Chidi Okonjo
Organization: Dunlavin Youth Sports Association

Please rate each item from 1 (poor) to 5 (excellent).
Content of the workshop: 5
Instructor''s knowledge: 5
Printed materials: 4
Room and equipment: 2
Length of the workshop: 3

What did you find most useful?
The sample budgets. I had never prepared one before, and Dr. Szabo explained each line clearly.

What could be improved?
The room was crowded, and people at the back could not see the screen. Also, three hours was not enough. We ran out of time before we could practice writing the project summary, which was the part I most wanted to work on.

Would you like to be notified of future workshops? Yes

────────────────────

[E-mail]
To: Chidi Okonjo
From: Bridget Amadi, Program Coordinator
Date: March 13
Subject: Follow-up session on April 10

Dear Mr. Okonjo,

Thank you for completing our survey after last week''s workshop. Several participants made comments similar to yours, and we have acted on them.

Dr. Szabo has agreed to lead a follow-up session on April 10 from 1:00 to 3:00 P.M. It will be devoted entirely to the topic that had to be left out on March 6. Because it is being offered to make up for that omission, there is no fee for anyone who attended the original workshop. Others may register for $30.

The session will be held in the Dalton Room on the second floor, which seats 60, rather than in the Birch Room. Please bring a draft of a proposal you are working on, even a rough one, as participants will exchange drafts and comment on them.

To reserve a seat, simply reply to this message by April 3.

Best regards,
Bridget Amadi', 'NONE', '["The subject matter", "The duration", "The handouts", "The facilities"]', 'D', 1, '[세부 사항] 정답 (D). 가장 낮은 점수는 "Room and equipment: 2"이고 이를 the facilities로 바꿔 말했다. 오답: (A) 내용은 5점, (B) 길이는 3점, (C) 인쇄 자료는 4점이다.
포인트: 세부 사항 — 설문 평점(room and equipment → facilities)', 182),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What will most likely be the focus of the April 10 session?', 'Questions 181-185 refer to the following survey and e-mail.

[Survey]
Stanmoor Business Development Center
Workshop Feedback Survey

Workshop: Writing Effective Grant Proposals
Date: March 6
Location: Birch Room (seats 24)
Instructor: Dr. Leona Szabo
Participant name (optional): Chidi Okonjo
Organization: Dunlavin Youth Sports Association

Please rate each item from 1 (poor) to 5 (excellent).
Content of the workshop: 5
Instructor''s knowledge: 5
Printed materials: 4
Room and equipment: 2
Length of the workshop: 3

What did you find most useful?
The sample budgets. I had never prepared one before, and Dr. Szabo explained each line clearly.

What could be improved?
The room was crowded, and people at the back could not see the screen. Also, three hours was not enough. We ran out of time before we could practice writing the project summary, which was the part I most wanted to work on.

Would you like to be notified of future workshops? Yes

────────────────────

[E-mail]
To: Chidi Okonjo
From: Bridget Amadi, Program Coordinator
Date: March 13
Subject: Follow-up session on April 10

Dear Mr. Okonjo,

Thank you for completing our survey after last week''s workshop. Several participants made comments similar to yours, and we have acted on them.

Dr. Szabo has agreed to lead a follow-up session on April 10 from 1:00 to 3:00 P.M. It will be devoted entirely to the topic that had to be left out on March 6. Because it is being offered to make up for that omission, there is no fee for anyone who attended the original workshop. Others may register for $30.

The session will be held in the Dalton Room on the second floor, which seats 60, rather than in the Birch Room. Please bring a draft of a proposal you are working on, even a rough one, as participants will exchange drafts and comment on them.

To reserve a seat, simply reply to this message by April 3.

Best regards,
Bridget Amadi', 'NONE', '["Preparing sample budgets", "Writing a project summary", "Finding organizations that offer grants", "Using presentation equipment"]', 'B', 1, '[연계(문서1+2)] 정답 (B). 이메일은 4월 10일 수업이 "the topic that had to be left out on March 6"만 다룬다고 했고, 설문에서 시간이 모자라 하지 못한 것은 "practice writing the project summary"였다. 오답: (A) 예산 예시는 3월 6일에 이미 다뤘다. (C) 보조금 기관 찾기는 어디에도 없다. (D) 장비는 불만 사항이었을 뿐 수업 주제가 아니다.
포인트: 연계 문항 — 설문 개선점 + 이메일 둘째 문단', 183),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the Dalton Room?', 'Questions 181-185 refer to the following survey and e-mail.

[Survey]
Stanmoor Business Development Center
Workshop Feedback Survey

Workshop: Writing Effective Grant Proposals
Date: March 6
Location: Birch Room (seats 24)
Instructor: Dr. Leona Szabo
Participant name (optional): Chidi Okonjo
Organization: Dunlavin Youth Sports Association

Please rate each item from 1 (poor) to 5 (excellent).
Content of the workshop: 5
Instructor''s knowledge: 5
Printed materials: 4
Room and equipment: 2
Length of the workshop: 3

What did you find most useful?
The sample budgets. I had never prepared one before, and Dr. Szabo explained each line clearly.

What could be improved?
The room was crowded, and people at the back could not see the screen. Also, three hours was not enough. We ran out of time before we could practice writing the project summary, which was the part I most wanted to work on.

Would you like to be notified of future workshops? Yes

────────────────────

[E-mail]
To: Chidi Okonjo
From: Bridget Amadi, Program Coordinator
Date: March 13
Subject: Follow-up session on April 10

Dear Mr. Okonjo,

Thank you for completing our survey after last week''s workshop. Several participants made comments similar to yours, and we have acted on them.

Dr. Szabo has agreed to lead a follow-up session on April 10 from 1:00 to 3:00 P.M. It will be devoted entirely to the topic that had to be left out on March 6. Because it is being offered to make up for that omission, there is no fee for anyone who attended the original workshop. Others may register for $30.

The session will be held in the Dalton Room on the second floor, which seats 60, rather than in the Birch Room. Please bring a draft of a proposal you are working on, even a rough one, as participants will exchange drafts and comment on them.

To reserve a seat, simply reply to this message by April 3.

Best regards,
Bridget Amadi', 'NONE', '["It was recently fitted with a larger screen.", "It is where Dr. Szabo taught on March 6.", "It has more seats than the March 6 venue.", "It is located next to the Birch Room."]', 'C', 1, '[연계(문서1+2)] 정답 (C). 설문 머리의 "Location: Birch Room (seats 24)"와 이메일의 "the Dalton Room on the second floor, which seats 60"을 함께 보면 Dalton Room의 좌석이 더 많다. 오답: (A) 화면 교체 언급은 없다. (B) 3월 6일 장소는 Birch Room이다. (D) 두 방의 위치 관계는 나오지 않는다.
포인트: 연계 문항 — 설문 머리(장소) + 이메일 셋째 문단', 184),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is Mr. Okonjo asked to bring to the session?', 'Questions 181-185 refer to the following survey and e-mail.

[Survey]
Stanmoor Business Development Center
Workshop Feedback Survey

Workshop: Writing Effective Grant Proposals
Date: March 6
Location: Birch Room (seats 24)
Instructor: Dr. Leona Szabo
Participant name (optional): Chidi Okonjo
Organization: Dunlavin Youth Sports Association

Please rate each item from 1 (poor) to 5 (excellent).
Content of the workshop: 5
Instructor''s knowledge: 5
Printed materials: 4
Room and equipment: 2
Length of the workshop: 3

What did you find most useful?
The sample budgets. I had never prepared one before, and Dr. Szabo explained each line clearly.

What could be improved?
The room was crowded, and people at the back could not see the screen. Also, three hours was not enough. We ran out of time before we could practice writing the project summary, which was the part I most wanted to work on.

Would you like to be notified of future workshops? Yes

────────────────────

[E-mail]
To: Chidi Okonjo
From: Bridget Amadi, Program Coordinator
Date: March 13
Subject: Follow-up session on April 10

Dear Mr. Okonjo,

Thank you for completing our survey after last week''s workshop. Several participants made comments similar to yours, and we have acted on them.

Dr. Szabo has agreed to lead a follow-up session on April 10 from 1:00 to 3:00 P.M. It will be devoted entirely to the topic that had to be left out on March 6. Because it is being offered to make up for that omission, there is no fee for anyone who attended the original workshop. Others may register for $30.

The session will be held in the Dalton Room on the second floor, which seats 60, rather than in the Birch Room. Please bring a draft of a proposal you are working on, even a rough one, as participants will exchange drafts and comment on them.

To reserve a seat, simply reply to this message by April 3.

Best regards,
Bridget Amadi', 'NONE', '["A registration fee of $30", "A document he has started writing", "A copy of his completed survey", "A list of questions for Dr. Szabo"]', 'B', 1, '[세부 사항] 정답 (B). "Please bring a draft of a proposal you are working on, even a rough one"을 ''쓰기 시작한 문서''로 바꿔 말했다. 오답: (A) 3월 6일 참석자는 무료다. (C) 설문지를 가져오라는 말은 없다. (D) 질문 목록은 언급되지 않았다.
포인트: 세부 사항 — 이메일 셋째 문단', 185),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'Why does Foxglove Inn need new uniforms?', 'Questions 186-190 refer to the following e-mails and table.

[E-mail]
To: Northgate Uniforms; Pierce Linen Supply; Radley Workwear
From: Vivian Mokoena, Purchasing Manager, Foxglove Inn
Date: February 3
Subject: Request for quotation

Dear Sales Representatives,

Foxglove Inn in Brockway will reopen its restaurant on April 1 after a three-month renovation, and we need new uniforms for the dining room staff. Please send a quotation for 80 long-sleeved shirts and 80 aprons, each embroidered with our logo. A copy of the logo is attached. Sizes will be confirmed when the order is placed.

Our requirements are as follows. All items must be delivered no later than March 20 so that staff can be fitted before the reopening. The total cost, including embroidery and delivery, must not exceed $4,000. We would also like to see fabric samples before placing an order.

Quotations received by February 10 will be considered. We expect to make a decision within a few days of that date.

Vivian Mokoena

────────────────────

[Table]
Foxglove Inn — Uniform Quotations
Summary prepared on February 11 by V. Mokoena

Supplier | Total price | Delivery date | Fabric samples | Notes
Northgate Uniforms | $3,650 | March 27 | Free | 5% discount on repeat orders
Pierce Linen Supply | $3,880 | March 14 | Free | Embroidery done at its own workshop
Radley Workwear | $4,150 | March 10 | $20, refunded with order | Faulty items replaced free for one year

All prices include embroidery and delivery.
Payment terms (all three suppliers): 30 percent deposit with the order, balance on delivery

────────────────────

[E-mail]
To: Edwin Pierce, Pierce Linen Supply
From: Vivian Mokoena
Date: February 12
Subject: Uniform order

Dear Mr. Pierce,

Thank you for your quotation. I am pleased to tell you that we would like to proceed with your company. The samples you sent arrived this morning, and our general manager has chosen the charcoal fabric for the aprons.

Before I send the purchase order, I have one request. Our general manager has decided to hire additional servers, so we now need 90 shirts and 90 aprons instead of 80, and he has raised our budget to cover them. Could you send a revised quotation and confirm that the larger order will not affect your delivery date?

Also, the logo file I sent on February 3 was an outdated version. The current one is attached; please use it for the embroidery.

As stated in your terms, we will pay a 30 percent deposit when the order is placed and the balance on delivery.

Best regards,
Vivian Mokoena', 'NONE', '["It is about to reopen a dining facility.", "It has hired a new general manager.", "It is opening a second restaurant.", "It is replacing uniforms that were faulty."]', 'A', 1, '[세부 사항] 정답 (A). "will reopen its restaurant on April 1 after a three-month renovation, and we need new uniforms for the dining room staff"가 근거이며 restaurant를 a dining facility로 바꿔 말했다. 오답: (B) 총지배인은 결정을 내리는 사람으로 나올 뿐이다. (C) 2호점이 아니라 기존 식당을 다시 연다. (D) faulty items는 Radley Workwear의 보증 조건이다.
포인트: 세부 사항 — 첫 이메일 첫 문단', 186),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is NOT a requirement stated in the first e-mail?', 'Questions 186-190 refer to the following e-mails and table.

[E-mail]
To: Northgate Uniforms; Pierce Linen Supply; Radley Workwear
From: Vivian Mokoena, Purchasing Manager, Foxglove Inn
Date: February 3
Subject: Request for quotation

Dear Sales Representatives,

Foxglove Inn in Brockway will reopen its restaurant on April 1 after a three-month renovation, and we need new uniforms for the dining room staff. Please send a quotation for 80 long-sleeved shirts and 80 aprons, each embroidered with our logo. A copy of the logo is attached. Sizes will be confirmed when the order is placed.

Our requirements are as follows. All items must be delivered no later than March 20 so that staff can be fitted before the reopening. The total cost, including embroidery and delivery, must not exceed $4,000. We would also like to see fabric samples before placing an order.

Quotations received by February 10 will be considered. We expect to make a decision within a few days of that date.

Vivian Mokoena

────────────────────

[Table]
Foxglove Inn — Uniform Quotations
Summary prepared on February 11 by V. Mokoena

Supplier | Total price | Delivery date | Fabric samples | Notes
Northgate Uniforms | $3,650 | March 27 | Free | 5% discount on repeat orders
Pierce Linen Supply | $3,880 | March 14 | Free | Embroidery done at its own workshop
Radley Workwear | $4,150 | March 10 | $20, refunded with order | Faulty items replaced free for one year

All prices include embroidery and delivery.
Payment terms (all three suppliers): 30 percent deposit with the order, balance on delivery

────────────────────

[E-mail]
To: Edwin Pierce, Pierce Linen Supply
From: Vivian Mokoena
Date: February 12
Subject: Uniform order

Dear Mr. Pierce,

Thank you for your quotation. I am pleased to tell you that we would like to proceed with your company. The samples you sent arrived this morning, and our general manager has chosen the charcoal fabric for the aprons.

Before I send the purchase order, I have one request. Our general manager has decided to hire additional servers, so we now need 90 shirts and 90 aprons instead of 80, and he has raised our budget to cover them. Could you send a revised quotation and confirm that the larger order will not affect your delivery date?

Also, the logo file I sent on February 3 was an outdated version. The current one is attached; please use it for the embroidery.

As stated in your terms, we will pay a 30 percent deposit when the order is placed and the balance on delivery.

Best regards,
Vivian Mokoena', 'NONE', '["A discount on repeat orders", "A limit on the total cost", "A deadline for delivery", "A logo added to each item"]', 'A', 1, '[사실 확인(NOT)] 정답 (A). 재주문 할인은 표에서 Northgate가 내건 조건일 뿐 첫 이메일의 요구 사항이 아니다. 오답: (B) "must not exceed $4,000". (C) "delivered no later than March 20". (D) "each embroidered with our logo".
포인트: 사실 확인(NOT) — 첫 이메일 둘째 문단(요구 조건)', 187),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'Why was Northgate Uniforms most likely not selected?', 'Questions 186-190 refer to the following e-mails and table.

[E-mail]
To: Northgate Uniforms; Pierce Linen Supply; Radley Workwear
From: Vivian Mokoena, Purchasing Manager, Foxglove Inn
Date: February 3
Subject: Request for quotation

Dear Sales Representatives,

Foxglove Inn in Brockway will reopen its restaurant on April 1 after a three-month renovation, and we need new uniforms for the dining room staff. Please send a quotation for 80 long-sleeved shirts and 80 aprons, each embroidered with our logo. A copy of the logo is attached. Sizes will be confirmed when the order is placed.

Our requirements are as follows. All items must be delivered no later than March 20 so that staff can be fitted before the reopening. The total cost, including embroidery and delivery, must not exceed $4,000. We would also like to see fabric samples before placing an order.

Quotations received by February 10 will be considered. We expect to make a decision within a few days of that date.

Vivian Mokoena

────────────────────

[Table]
Foxglove Inn — Uniform Quotations
Summary prepared on February 11 by V. Mokoena

Supplier | Total price | Delivery date | Fabric samples | Notes
Northgate Uniforms | $3,650 | March 27 | Free | 5% discount on repeat orders
Pierce Linen Supply | $3,880 | March 14 | Free | Embroidery done at its own workshop
Radley Workwear | $4,150 | March 10 | $20, refunded with order | Faulty items replaced free for one year

All prices include embroidery and delivery.
Payment terms (all three suppliers): 30 percent deposit with the order, balance on delivery

────────────────────

[E-mail]
To: Edwin Pierce, Pierce Linen Supply
From: Vivian Mokoena
Date: February 12
Subject: Uniform order

Dear Mr. Pierce,

Thank you for your quotation. I am pleased to tell you that we would like to proceed with your company. The samples you sent arrived this morning, and our general manager has chosen the charcoal fabric for the aprons.

Before I send the purchase order, I have one request. Our general manager has decided to hire additional servers, so we now need 90 shirts and 90 aprons instead of 80, and he has raised our budget to cover them. Could you send a revised quotation and confirm that the larger order will not affect your delivery date?

Also, the logo file I sent on February 3 was an outdated version. The current one is attached; please use it for the embroidery.

As stated in your terms, we will pay a 30 percent deposit when the order is placed and the balance on delivery.

Best regards,
Vivian Mokoena', 'NONE', '["Its price was over the stated budget.", "It charges a fee for fabric samples.", "It does not offer embroidery.", "It could not deliver the items early enough."]', 'D', 1, '[연계(문서1+2)] 정답 (D). 첫 이메일의 조건은 "no later than March 20"인데 표에서 Northgate의 납품일은 March 27이다. 오답: (A) 3,650달러로 예산 4,000달러 안이다. (B) 견본은 무료다. (C) 모든 가격에 자수가 포함되어 있다.
포인트: 연계 문항 — 첫 이메일(납품 기한) + 표(납품일)', 188),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about the supplier that Ms. Mokoena chose?', 'Questions 186-190 refer to the following e-mails and table.

[E-mail]
To: Northgate Uniforms; Pierce Linen Supply; Radley Workwear
From: Vivian Mokoena, Purchasing Manager, Foxglove Inn
Date: February 3
Subject: Request for quotation

Dear Sales Representatives,

Foxglove Inn in Brockway will reopen its restaurant on April 1 after a three-month renovation, and we need new uniforms for the dining room staff. Please send a quotation for 80 long-sleeved shirts and 80 aprons, each embroidered with our logo. A copy of the logo is attached. Sizes will be confirmed when the order is placed.

Our requirements are as follows. All items must be delivered no later than March 20 so that staff can be fitted before the reopening. The total cost, including embroidery and delivery, must not exceed $4,000. We would also like to see fabric samples before placing an order.

Quotations received by February 10 will be considered. We expect to make a decision within a few days of that date.

Vivian Mokoena

────────────────────

[Table]
Foxglove Inn — Uniform Quotations
Summary prepared on February 11 by V. Mokoena

Supplier | Total price | Delivery date | Fabric samples | Notes
Northgate Uniforms | $3,650 | March 27 | Free | 5% discount on repeat orders
Pierce Linen Supply | $3,880 | March 14 | Free | Embroidery done at its own workshop
Radley Workwear | $4,150 | March 10 | $20, refunded with order | Faulty items replaced free for one year

All prices include embroidery and delivery.
Payment terms (all three suppliers): 30 percent deposit with the order, balance on delivery

────────────────────

[E-mail]
To: Edwin Pierce, Pierce Linen Supply
From: Vivian Mokoena
Date: February 12
Subject: Uniform order

Dear Mr. Pierce,

Thank you for your quotation. I am pleased to tell you that we would like to proceed with your company. The samples you sent arrived this morning, and our general manager has chosen the charcoal fabric for the aprons.

Before I send the purchase order, I have one request. Our general manager has decided to hire additional servers, so we now need 90 shirts and 90 aprons instead of 80, and he has raised our budget to cover them. Could you send a revised quotation and confirm that the larger order will not affect your delivery date?

Also, the logo file I sent on February 3 was an outdated version. The current one is attached; please use it for the embroidery.

As stated in your terms, we will pay a 30 percent deposit when the order is placed and the balance on delivery.

Best regards,
Vivian Mokoena', 'NONE', '["It charges customers for fabric samples.", "It does embroidery on its own premises.", "It submitted the lowest quotation.", "It replaces faulty items for one year."]', 'B', 1, '[연계(문서2+3)] 정답 (B). 두 번째 이메일에서 선택한 업체는 Mr. Pierce의 Pierce Linen Supply이고, 표의 Notes에 "Embroidery done at its own workshop"이라고 되어 있다. 오답: (A)·(D) 견본 비용과 1년 무상 교체는 Radley Workwear의 조건이다. (C) 가장 낮은 견적은 Northgate의 3,650달러다.
포인트: 연계 문항 — 표 Notes + 둘째 이메일(선택한 업체)', 189),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What does Ms. Mokoena ask Mr. Pierce to do?', 'Questions 186-190 refer to the following e-mails and table.

[E-mail]
To: Northgate Uniforms; Pierce Linen Supply; Radley Workwear
From: Vivian Mokoena, Purchasing Manager, Foxglove Inn
Date: February 3
Subject: Request for quotation

Dear Sales Representatives,

Foxglove Inn in Brockway will reopen its restaurant on April 1 after a three-month renovation, and we need new uniforms for the dining room staff. Please send a quotation for 80 long-sleeved shirts and 80 aprons, each embroidered with our logo. A copy of the logo is attached. Sizes will be confirmed when the order is placed.

Our requirements are as follows. All items must be delivered no later than March 20 so that staff can be fitted before the reopening. The total cost, including embroidery and delivery, must not exceed $4,000. We would also like to see fabric samples before placing an order.

Quotations received by February 10 will be considered. We expect to make a decision within a few days of that date.

Vivian Mokoena

────────────────────

[Table]
Foxglove Inn — Uniform Quotations
Summary prepared on February 11 by V. Mokoena

Supplier | Total price | Delivery date | Fabric samples | Notes
Northgate Uniforms | $3,650 | March 27 | Free | 5% discount on repeat orders
Pierce Linen Supply | $3,880 | March 14 | Free | Embroidery done at its own workshop
Radley Workwear | $4,150 | March 10 | $20, refunded with order | Faulty items replaced free for one year

All prices include embroidery and delivery.
Payment terms (all three suppliers): 30 percent deposit with the order, balance on delivery

────────────────────

[E-mail]
To: Edwin Pierce, Pierce Linen Supply
From: Vivian Mokoena
Date: February 12
Subject: Uniform order

Dear Mr. Pierce,

Thank you for your quotation. I am pleased to tell you that we would like to proceed with your company. The samples you sent arrived this morning, and our general manager has chosen the charcoal fabric for the aprons.

Before I send the purchase order, I have one request. Our general manager has decided to hire additional servers, so we now need 90 shirts and 90 aprons instead of 80, and he has raised our budget to cover them. Could you send a revised quotation and confirm that the larger order will not affect your delivery date?

Also, the logo file I sent on February 3 was an outdated version. The current one is attached; please use it for the embroidery.

As stated in your terms, we will pay a 30 percent deposit when the order is placed and the balance on delivery.

Best regards,
Vivian Mokoena', 'NONE', '["Provide an updated price", "Send more fabric samples", "Deliver the order earlier", "Design a new logo"]', 'A', 1, '[세부 사항] 정답 (A). "Could you send a revised quotation"을 ''수정된 가격을 알려 주다''로 바꿔 말했다. 오답: (B) 견본은 이미 받아서 천을 골랐다. (C) 납품일이 늦어지지 않는지 확인해 달라는 것이지 앞당겨 달라는 것이 아니다. (D) 로고는 새 파일을 첨부했을 뿐 디자인을 맡기지 않았다.
포인트: 세부 사항 — 둘째 이메일 둘째 문단', 190),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about the courses at Marrable Career Institute?', 'Questions 191-195 refer to the following schedule and e-mails.

[Schedule]
Marrable Career Institute
Spring Evening Courses in Office Finance
All classes meet from 6:30 P.M. to 8:30 P.M.

Course | Code | Sessions | First session | Room | Instructor | Fee
Bookkeeping Basics | BK-1 | 6 | April 7 | Room 3 | Ignacio Belmonte | $180
Bookkeeping Basics | BK-2 | 6 | April 9 | Room 3 | Ignacio Belmonte | $180
Payroll Essentials | PR-1 | 9 | April 8 | Room 5 | Pavel Novotny | $270
Small-Business Tax Returns | TX-1 | 12 | April 10 | Room 5 | Pavel Novotny | $360
Invoicing with Spreadsheets | SP-1 | 3 | April 14 | Computer Lab | Ignacio Belmonte | $90

Payroll Essentials is open only to those who have completed Bookkeeping Basics or have at least one year of bookkeeping experience.
Each class is limited to 16 participants. Registration closes three business days before the first session.
Fees are refunded in full to participants who withdraw before the second session.

────────────────────

[E-mail]
To: Registration Office, Marrable Career Institute
From: Delphine Marchand
Date: March 30
Subject: My course confirmation

Dear Registration Office,

The confirmation I received this morning shows that I have been enrolled in Payroll Essentials. I believe there has been a mix-up. On your online form, I selected Bookkeeping Basics, specifically the section that starts on April 9, because I will be away on business until April 8.

I opened a flower shop two months ago and have never kept accounts before, so a beginner''s course is what I need. If that section is already full, please enroll me in Invoicing with Spreadsheets instead, and I will take Bookkeeping Basics in the summer.

My card was charged for the payroll course, so I would also appreciate a refund of the difference.

Thank you,
Delphine Marchand

────────────────────

[E-mail]
To: Kofi Asante, Program Coordinator
From: Ignacio Belmonte
Date: April 1
Subject: Class lists

Dear Mr. Asante,

Thank you for sending the revised class lists. I see that you have added Delphine Marchand to the section she originally asked for. With her, that section has 15 participants, so it still complies with the institute''s limit.

I do have a concern about Invoicing with Spreadsheets. In your message, you said that you are thinking of reassigning that course to Mr. Novotny because I am already teaching two other classes this term. I would much rather keep it. I designed the course materials myself, and the extra evenings will not be a burden. Could you let me know your decision before the lists are posted on April 3?

Finally, when will the new projector be installed in Room 3? If the work will not be finished by April 7, I will need another room for my first class.

Best regards,
Ignacio Belmonte', 'NONE', '["They are all held in the same room.", "They are taught by three instructors.", "They each accept up to 16 participants.", "They all have the same number of sessions."]', 'C', 1, '[사실 확인] 정답 (C). 강좌표 아래의 "Each class is limited to 16 participants"가 근거다. 오답: (A) Room 3, Room 5, Computer Lab으로 나뉜다. (B) 강사는 Ignacio Belmonte와 Pavel Novotny 두 명이다. (D) 회차는 3·6·9·12회로 서로 다르다.
포인트: 사실 확인 — 강좌표와 아래 안내문', 191),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'Why did Ms. Marchand write to the registration office?', 'Questions 191-195 refer to the following schedule and e-mails.

[Schedule]
Marrable Career Institute
Spring Evening Courses in Office Finance
All classes meet from 6:30 P.M. to 8:30 P.M.

Course | Code | Sessions | First session | Room | Instructor | Fee
Bookkeeping Basics | BK-1 | 6 | April 7 | Room 3 | Ignacio Belmonte | $180
Bookkeeping Basics | BK-2 | 6 | April 9 | Room 3 | Ignacio Belmonte | $180
Payroll Essentials | PR-1 | 9 | April 8 | Room 5 | Pavel Novotny | $270
Small-Business Tax Returns | TX-1 | 12 | April 10 | Room 5 | Pavel Novotny | $360
Invoicing with Spreadsheets | SP-1 | 3 | April 14 | Computer Lab | Ignacio Belmonte | $90

Payroll Essentials is open only to those who have completed Bookkeeping Basics or have at least one year of bookkeeping experience.
Each class is limited to 16 participants. Registration closes three business days before the first session.
Fees are refunded in full to participants who withdraw before the second session.

────────────────────

[E-mail]
To: Registration Office, Marrable Career Institute
From: Delphine Marchand
Date: March 30
Subject: My course confirmation

Dear Registration Office,

The confirmation I received this morning shows that I have been enrolled in Payroll Essentials. I believe there has been a mix-up. On your online form, I selected Bookkeeping Basics, specifically the section that starts on April 9, because I will be away on business until April 8.

I opened a flower shop two months ago and have never kept accounts before, so a beginner''s course is what I need. If that section is already full, please enroll me in Invoicing with Spreadsheets instead, and I will take Bookkeeping Basics in the summer.

My card was charged for the payroll course, so I would also appreciate a refund of the difference.

Thank you,
Delphine Marchand

────────────────────

[E-mail]
To: Kofi Asante, Program Coordinator
From: Ignacio Belmonte
Date: April 1
Subject: Class lists

Dear Mr. Asante,

Thank you for sending the revised class lists. I see that you have added Delphine Marchand to the section she originally asked for. With her, that section has 15 participants, so it still complies with the institute''s limit.

I do have a concern about Invoicing with Spreadsheets. In your message, you said that you are thinking of reassigning that course to Mr. Novotny because I am already teaching two other classes this term. I would much rather keep it. I designed the course materials myself, and the extra evenings will not be a burden. Could you let me know your decision before the lists are posted on April 3?

Finally, when will the new projector be installed in Room 3? If the work will not be finished by April 7, I will need another room for my first class.

Best regards,
Ignacio Belmonte', 'NONE', '["To withdraw from a course before it begins", "To have a registration mistake corrected", "To ask whether a course still has openings", "To request a later starting date for a course"]', 'B', 1, '[주제·목적] 정답 (B). 확인서에는 Payroll Essentials로 되어 있지만 "I selected Bookkeeping Basics"라며 "I believe there has been a mix-up"이라고 했으므로 등록 오류를 바로잡아 달라는 글이다. 오답: (A) 수강을 그만두려는 것이 아니다. (C) 자리가 없을 때의 대안을 말했을 뿐 빈자리 문의가 목적은 아니다. (D) 4월 9일 반은 처음부터 고른 것이다.
포인트: 주제·목적 — 첫 이메일 첫 문단', 192),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about Ms. Marchand?', 'Questions 191-195 refer to the following schedule and e-mails.

[Schedule]
Marrable Career Institute
Spring Evening Courses in Office Finance
All classes meet from 6:30 P.M. to 8:30 P.M.

Course | Code | Sessions | First session | Room | Instructor | Fee
Bookkeeping Basics | BK-1 | 6 | April 7 | Room 3 | Ignacio Belmonte | $180
Bookkeeping Basics | BK-2 | 6 | April 9 | Room 3 | Ignacio Belmonte | $180
Payroll Essentials | PR-1 | 9 | April 8 | Room 5 | Pavel Novotny | $270
Small-Business Tax Returns | TX-1 | 12 | April 10 | Room 5 | Pavel Novotny | $360
Invoicing with Spreadsheets | SP-1 | 3 | April 14 | Computer Lab | Ignacio Belmonte | $90

Payroll Essentials is open only to those who have completed Bookkeeping Basics or have at least one year of bookkeeping experience.
Each class is limited to 16 participants. Registration closes three business days before the first session.
Fees are refunded in full to participants who withdraw before the second session.

────────────────────

[E-mail]
To: Registration Office, Marrable Career Institute
From: Delphine Marchand
Date: March 30
Subject: My course confirmation

Dear Registration Office,

The confirmation I received this morning shows that I have been enrolled in Payroll Essentials. I believe there has been a mix-up. On your online form, I selected Bookkeeping Basics, specifically the section that starts on April 9, because I will be away on business until April 8.

I opened a flower shop two months ago and have never kept accounts before, so a beginner''s course is what I need. If that section is already full, please enroll me in Invoicing with Spreadsheets instead, and I will take Bookkeeping Basics in the summer.

My card was charged for the payroll course, so I would also appreciate a refund of the difference.

Thank you,
Delphine Marchand

────────────────────

[E-mail]
To: Kofi Asante, Program Coordinator
From: Ignacio Belmonte
Date: April 1
Subject: Class lists

Dear Mr. Asante,

Thank you for sending the revised class lists. I see that you have added Delphine Marchand to the section she originally asked for. With her, that section has 15 participants, so it still complies with the institute''s limit.

I do have a concern about Invoicing with Spreadsheets. In your message, you said that you are thinking of reassigning that course to Mr. Novotny because I am already teaching two other classes this term. I would much rather keep it. I designed the course materials myself, and the extra evenings will not be a burden. Could you let me know your decision before the lists are posted on April 3?

Finally, when will the new projector be installed in Room 3? If the work will not be finished by April 7, I will need another room for my first class.

Best regards,
Ignacio Belmonte', 'NONE', '["She has taken a course at the institute before.", "She will miss the first session of her course.", "She works as a bookkeeper at a flower shop.", "She is not eligible for Payroll Essentials."]', 'D', 1, '[연계(문서1+2)] 정답 (D). 강좌표에 Payroll Essentials는 "open only to those who have completed Bookkeeping Basics or have at least one year of bookkeeping experience"라고 되어 있는데, Ms. Marchand는 "have never kept accounts before"라고 했으므로 수강 자격이 없다. 오답: (A) 이전 수강 이야기는 없다. (B) 4월 8일까지 출장이고 고른 반은 4월 9일에 시작한다. (C) 꽃집을 연 주인이며 장부를 써 본 적이 없다.
포인트: 연계 문항 — 강좌표 안내문 + 첫 이메일(수강 대상)', 193),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'According to the second e-mail, what has Mr. Asante already done?', 'Questions 191-195 refer to the following schedule and e-mails.

[Schedule]
Marrable Career Institute
Spring Evening Courses in Office Finance
All classes meet from 6:30 P.M. to 8:30 P.M.

Course | Code | Sessions | First session | Room | Instructor | Fee
Bookkeeping Basics | BK-1 | 6 | April 7 | Room 3 | Ignacio Belmonte | $180
Bookkeeping Basics | BK-2 | 6 | April 9 | Room 3 | Ignacio Belmonte | $180
Payroll Essentials | PR-1 | 9 | April 8 | Room 5 | Pavel Novotny | $270
Small-Business Tax Returns | TX-1 | 12 | April 10 | Room 5 | Pavel Novotny | $360
Invoicing with Spreadsheets | SP-1 | 3 | April 14 | Computer Lab | Ignacio Belmonte | $90

Payroll Essentials is open only to those who have completed Bookkeeping Basics or have at least one year of bookkeeping experience.
Each class is limited to 16 participants. Registration closes three business days before the first session.
Fees are refunded in full to participants who withdraw before the second session.

────────────────────

[E-mail]
To: Registration Office, Marrable Career Institute
From: Delphine Marchand
Date: March 30
Subject: My course confirmation

Dear Registration Office,

The confirmation I received this morning shows that I have been enrolled in Payroll Essentials. I believe there has been a mix-up. On your online form, I selected Bookkeeping Basics, specifically the section that starts on April 9, because I will be away on business until April 8.

I opened a flower shop two months ago and have never kept accounts before, so a beginner''s course is what I need. If that section is already full, please enroll me in Invoicing with Spreadsheets instead, and I will take Bookkeeping Basics in the summer.

My card was charged for the payroll course, so I would also appreciate a refund of the difference.

Thank you,
Delphine Marchand

────────────────────

[E-mail]
To: Kofi Asante, Program Coordinator
From: Ignacio Belmonte
Date: April 1
Subject: Class lists

Dear Mr. Asante,

Thank you for sending the revised class lists. I see that you have added Delphine Marchand to the section she originally asked for. With her, that section has 15 participants, so it still complies with the institute''s limit.

I do have a concern about Invoicing with Spreadsheets. In your message, you said that you are thinking of reassigning that course to Mr. Novotny because I am already teaching two other classes this term. I would much rather keep it. I designed the course materials myself, and the extra evenings will not be a burden. Could you let me know your decision before the lists are posted on April 3?

Finally, when will the new projector be installed in Room 3? If the work will not be finished by April 7, I will need another room for my first class.

Best regards,
Ignacio Belmonte', 'NONE', '["Reassigned a course to another instructor", "Added a participant to a class list", "Installed a projector in a classroom", "Posted the class lists for the term"]', 'B', 1, '[세부 사항] 정답 (B). "I see that you have added Delphine Marchand to the section she originally asked for"에서 이미 수강생 한 명을 명단에 넣었음을 알 수 있다. 오답: (A) 강좌를 Mr. Novotny에게 다시 맡기는 것은 "you are thinking of reassigning"으로 아직 검토 중이다. (C) 프로젝터는 언제 설치되는지 묻고 있다. (D) 명단은 4월 3일에 게시될 예정이다.
포인트: 세부 사항 — 둘째 이메일(add 추가와 reassign 재배정 구별)', 194),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'How many sessions are there in the course that Ms. Marchand will most likely take?', 'Questions 191-195 refer to the following schedule and e-mails.

[Schedule]
Marrable Career Institute
Spring Evening Courses in Office Finance
All classes meet from 6:30 P.M. to 8:30 P.M.

Course | Code | Sessions | First session | Room | Instructor | Fee
Bookkeeping Basics | BK-1 | 6 | April 7 | Room 3 | Ignacio Belmonte | $180
Bookkeeping Basics | BK-2 | 6 | April 9 | Room 3 | Ignacio Belmonte | $180
Payroll Essentials | PR-1 | 9 | April 8 | Room 5 | Pavel Novotny | $270
Small-Business Tax Returns | TX-1 | 12 | April 10 | Room 5 | Pavel Novotny | $360
Invoicing with Spreadsheets | SP-1 | 3 | April 14 | Computer Lab | Ignacio Belmonte | $90

Payroll Essentials is open only to those who have completed Bookkeeping Basics or have at least one year of bookkeeping experience.
Each class is limited to 16 participants. Registration closes three business days before the first session.
Fees are refunded in full to participants who withdraw before the second session.

────────────────────

[E-mail]
To: Registration Office, Marrable Career Institute
From: Delphine Marchand
Date: March 30
Subject: My course confirmation

Dear Registration Office,

The confirmation I received this morning shows that I have been enrolled in Payroll Essentials. I believe there has been a mix-up. On your online form, I selected Bookkeeping Basics, specifically the section that starts on April 9, because I will be away on business until April 8.

I opened a flower shop two months ago and have never kept accounts before, so a beginner''s course is what I need. If that section is already full, please enroll me in Invoicing with Spreadsheets instead, and I will take Bookkeeping Basics in the summer.

My card was charged for the payroll course, so I would also appreciate a refund of the difference.

Thank you,
Delphine Marchand

────────────────────

[E-mail]
To: Kofi Asante, Program Coordinator
From: Ignacio Belmonte
Date: April 1
Subject: Class lists

Dear Mr. Asante,

Thank you for sending the revised class lists. I see that you have added Delphine Marchand to the section she originally asked for. With her, that section has 15 participants, so it still complies with the institute''s limit.

I do have a concern about Invoicing with Spreadsheets. In your message, you said that you are thinking of reassigning that course to Mr. Novotny because I am already teaching two other classes this term. I would much rather keep it. I designed the course materials myself, and the extra evenings will not be a burden. Could you let me know your decision before the lists are posted on April 3?

Finally, when will the new projector be installed in Room 3? If the work will not be finished by April 7, I will need another room for my first class.

Best regards,
Ignacio Belmonte', 'NONE', '["3", "6", "9", "12"]', 'B', 1, '[연계(문서1+2+3)] 정답 (B). 첫 이메일에서 Ms. Marchand가 처음 고른 반은 4월 9일에 시작하는 Bookkeeping Basics이고 자리가 없을 때만 Invoicing with Spreadsheets를 듣겠다고 했다. 둘째 이메일에 "you have added Delphine Marchand to the section she originally asked for"와 "that section has 15 participants"라고 했으므로 처음 고른 반에 들어갔다. 강좌표에서 4월 9일에 시작하는 BK-2는 6회다. 오답: (A) 대안으로 든 Invoicing with Spreadsheets, (C) 잘못 등록된 Payroll Essentials, (D) Small-Business Tax Returns의 회차다.
포인트: 연계 문항 — 강좌표 + 이메일 두 통(강좌·날짜·회차 연결)', 195),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'According to the Web page, what do all members receive?', 'Questions 196-200 refer to the following Web page, form, and e-mail.

[Web page]
Penhallow Museum of Natural History
Become a Member

Membership supports our collections and education programs and gives you a full year of benefits.

Individual — $55: unlimited general admission for one adult
Dual — $85: unlimited general admission for two adults
Family — $110: unlimited general admission for two adults and up to four children
Supporter — $250: all Family benefits, plus four guest passes and an invitation to the annual Curators'' Dinner

All members receive 10 percent off in the museum shop and café, the quarterly magazine Strata, and early booking for special exhibitions. Tickets for special exhibitions are not included in general admission, but members pay half the regular price. Members are also invited to two evening receptions each year.

Memberships purchased in November are valid for 13 months instead of 12. Apply online or at the visitor services desk.

────────────────────

[Form]
Penhallow Museum of Natural History
Membership Application

Date: November 18
Name: Ilse Meier
Address: 77 Fernhill Road, Asterbrook
Telephone: 555-0163
Membership level: Dual
Second cardholder: Anton Meier
Payment: $85, credit card
Additional donation (optional): $40 to the Education Fund
Where did you learn about the museum? From a coworker who volunteers there
Comments: If tickets are still available, we hope to attend the opening day of the dinosaur exhibition.

────────────────────

[E-mail]
To: Ilse Meier
From: Visitor Services, Penhallow Museum of Natural History
Date: November 20
Subject: Welcome to the museum

Dear Ms. Meier,

Welcome, and thank you for your additional gift to the Education Fund. Your membership cards will be mailed within 10 days. Until they arrive, please show this e-mail at the entrance, and you will be admitted without delay.

In reply to your comment, Giants of the Cretaceous opens on December 6 and will run until the end of April. I am sorry to say that tickets for opening day are sold out, but plenty remain for December 7 onward. The regular price is $24 per adult. Members may book now at the member rate on our website; booking opens to the general public on November 27.

You may also be interested to know that members at your level can upgrade to a Family membership at any time by paying only the difference in price. Several members do so each year when relatives come to visit.

Warm regards,
Jerome Baptiste
Visitor Services', 'NONE', '["A publication issued four times a year", "Passes for guests who are not members", "Free entry to special exhibitions", "An invitation to an annual dinner"]', 'A', 1, '[세부 사항] 정답 (A). "All members receive ... the quarterly magazine Strata"에서 quarterly magazine을 ''1년에 네 번 나오는 간행물''로 바꿔 말했다. 오답: (B) 손님용 입장권과 (D) 연례 만찬 초대는 Supporter 등급 혜택이다. (C) 특별전은 무료가 아니라 반값이다.
포인트: 세부 사항 — 웹 페이지 회원 공통 혜택', 196),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about Ms. Meier''s membership?', 'Questions 196-200 refer to the following Web page, form, and e-mail.

[Web page]
Penhallow Museum of Natural History
Become a Member

Membership supports our collections and education programs and gives you a full year of benefits.

Individual — $55: unlimited general admission for one adult
Dual — $85: unlimited general admission for two adults
Family — $110: unlimited general admission for two adults and up to four children
Supporter — $250: all Family benefits, plus four guest passes and an invitation to the annual Curators'' Dinner

All members receive 10 percent off in the museum shop and café, the quarterly magazine Strata, and early booking for special exhibitions. Tickets for special exhibitions are not included in general admission, but members pay half the regular price. Members are also invited to two evening receptions each year.

Memberships purchased in November are valid for 13 months instead of 12. Apply online or at the visitor services desk.

────────────────────

[Form]
Penhallow Museum of Natural History
Membership Application

Date: November 18
Name: Ilse Meier
Address: 77 Fernhill Road, Asterbrook
Telephone: 555-0163
Membership level: Dual
Second cardholder: Anton Meier
Payment: $85, credit card
Additional donation (optional): $40 to the Education Fund
Where did you learn about the museum? From a coworker who volunteers there
Comments: If tickets are still available, we hope to attend the opening day of the dinosaur exhibition.

────────────────────

[E-mail]
To: Ilse Meier
From: Visitor Services, Penhallow Museum of Natural History
Date: November 20
Subject: Welcome to the museum

Dear Ms. Meier,

Welcome, and thank you for your additional gift to the Education Fund. Your membership cards will be mailed within 10 days. Until they arrive, please show this e-mail at the entrance, and you will be admitted without delay.

In reply to your comment, Giants of the Cretaceous opens on December 6 and will run until the end of April. I am sorry to say that tickets for opening day are sold out, but plenty remain for December 7 onward. The regular price is $24 per adult. Members may book now at the member rate on our website; booking opens to the general public on November 27.

You may also be interested to know that members at your level can upgrade to a Family membership at any time by paying only the difference in price. Several members do so each year when relatives come to visit.

Warm regards,
Jerome Baptiste
Visitor Services', 'NONE', '["It includes admission for four children.", "It will remain valid for more than a year.", "It was paid for by one of her coworkers.", "It comes with four passes for guests."]', 'B', 1, '[연계(문서1+2)] 정답 (B). 신청서 날짜가 November 18이고 웹 페이지에 "Memberships purchased in November are valid for 13 months instead of 12"라고 되어 있다. 오답: (A) 자녀 입장은 Family 등급이고 Ms. Meier는 Dual이다. (C) 동료는 박물관을 알려 준 사람일 뿐 본인이 카드로 결제했다. (D) 손님용 입장권은 Supporter 등급이다.
포인트: 연계 문항 — 웹 페이지(11월 가입 조건) + 신청서 날짜', 197),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What does Ms. Meier indicate on the form?', 'Questions 196-200 refer to the following Web page, form, and e-mail.

[Web page]
Penhallow Museum of Natural History
Become a Member

Membership supports our collections and education programs and gives you a full year of benefits.

Individual — $55: unlimited general admission for one adult
Dual — $85: unlimited general admission for two adults
Family — $110: unlimited general admission for two adults and up to four children
Supporter — $250: all Family benefits, plus four guest passes and an invitation to the annual Curators'' Dinner

All members receive 10 percent off in the museum shop and café, the quarterly magazine Strata, and early booking for special exhibitions. Tickets for special exhibitions are not included in general admission, but members pay half the regular price. Members are also invited to two evening receptions each year.

Memberships purchased in November are valid for 13 months instead of 12. Apply online or at the visitor services desk.

────────────────────

[Form]
Penhallow Museum of Natural History
Membership Application

Date: November 18
Name: Ilse Meier
Address: 77 Fernhill Road, Asterbrook
Telephone: 555-0163
Membership level: Dual
Second cardholder: Anton Meier
Payment: $85, credit card
Additional donation (optional): $40 to the Education Fund
Where did you learn about the museum? From a coworker who volunteers there
Comments: If tickets are still available, we hope to attend the opening day of the dinosaur exhibition.

────────────────────

[E-mail]
To: Ilse Meier
From: Visitor Services, Penhallow Museum of Natural History
Date: November 20
Subject: Welcome to the museum

Dear Ms. Meier,

Welcome, and thank you for your additional gift to the Education Fund. Your membership cards will be mailed within 10 days. Until they arrive, please show this e-mail at the entrance, and you will be admitted without delay.

In reply to your comment, Giants of the Cretaceous opens on December 6 and will run until the end of April. I am sorry to say that tickets for opening day are sold out, but plenty remain for December 7 onward. The regular price is $24 per adult. Members may book now at the member rate on our website; booking opens to the general public on November 27.

You may also be interested to know that members at your level can upgrade to a Family membership at any time by paying only the difference in price. Several members do so each year when relatives come to visit.

Warm regards,
Jerome Baptiste
Visitor Services', 'NONE', '["She works as a volunteer at the museum.", "She previously held a Family membership.", "She learned of the museum from a colleague.", "She wishes to cancel an optional donation."]', 'C', 1, '[세부 사항] 정답 (C). "Where did you learn about the museum? From a coworker who volunteers there"에서 직장 동료에게 들었음을 알 수 있다. 오답: (A) 자원봉사를 하는 사람은 동료다. (B) 이전 회원 이력은 없다. (D) 40달러를 추가로 기부했지 취소하려는 것이 아니다.
포인트: 세부 사항 — 신청서 항목', 198),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What does Mr. Baptiste state about Giants of the Cretaceous?', 'Questions 196-200 refer to the following Web page, form, and e-mail.

[Web page]
Penhallow Museum of Natural History
Become a Member

Membership supports our collections and education programs and gives you a full year of benefits.

Individual — $55: unlimited general admission for one adult
Dual — $85: unlimited general admission for two adults
Family — $110: unlimited general admission for two adults and up to four children
Supporter — $250: all Family benefits, plus four guest passes and an invitation to the annual Curators'' Dinner

All members receive 10 percent off in the museum shop and café, the quarterly magazine Strata, and early booking for special exhibitions. Tickets for special exhibitions are not included in general admission, but members pay half the regular price. Members are also invited to two evening receptions each year.

Memberships purchased in November are valid for 13 months instead of 12. Apply online or at the visitor services desk.

────────────────────

[Form]
Penhallow Museum of Natural History
Membership Application

Date: November 18
Name: Ilse Meier
Address: 77 Fernhill Road, Asterbrook
Telephone: 555-0163
Membership level: Dual
Second cardholder: Anton Meier
Payment: $85, credit card
Additional donation (optional): $40 to the Education Fund
Where did you learn about the museum? From a coworker who volunteers there
Comments: If tickets are still available, we hope to attend the opening day of the dinosaur exhibition.

────────────────────

[E-mail]
To: Ilse Meier
From: Visitor Services, Penhallow Museum of Natural History
Date: November 20
Subject: Welcome to the museum

Dear Ms. Meier,

Welcome, and thank you for your additional gift to the Education Fund. Your membership cards will be mailed within 10 days. Until they arrive, please show this e-mail at the entrance, and you will be admitted without delay.

In reply to your comment, Giants of the Cretaceous opens on December 6 and will run until the end of April. I am sorry to say that tickets for opening day are sold out, but plenty remain for December 7 onward. The regular price is $24 per adult. Members may book now at the member rate on our website; booking opens to the general public on November 27.

You may also be interested to know that members at your level can upgrade to a Family membership at any time by paying only the difference in price. Several members do so each year when relatives come to visit.

Warm regards,
Jerome Baptiste
Visitor Services', 'NONE', '["It will open to visitors on November 27.", "It is free of charge for museum members.", "Its tickets are sold only at the entrance.", "Its first day is already fully booked."]', 'D', 1, '[세부 사항] 정답 (D). "tickets for opening day are sold out"을 ''첫날은 이미 예약이 다 찼다''로 바꿔 말했다. 오답: (A) 11월 27일은 일반 예매 시작일이고 개막은 12월 6일이다. (B) 회원도 회원가(반값)를 낸다. (C) 웹사이트에서 예매할 수 있다고 했다.
포인트: 세부 사항 — 이메일 둘째 문단', 199),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'How much would Ms. Meier pay to change her membership as Mr. Baptiste describes?', 'Questions 196-200 refer to the following Web page, form, and e-mail.

[Web page]
Penhallow Museum of Natural History
Become a Member

Membership supports our collections and education programs and gives you a full year of benefits.

Individual — $55: unlimited general admission for one adult
Dual — $85: unlimited general admission for two adults
Family — $110: unlimited general admission for two adults and up to four children
Supporter — $250: all Family benefits, plus four guest passes and an invitation to the annual Curators'' Dinner

All members receive 10 percent off in the museum shop and café, the quarterly magazine Strata, and early booking for special exhibitions. Tickets for special exhibitions are not included in general admission, but members pay half the regular price. Members are also invited to two evening receptions each year.

Memberships purchased in November are valid for 13 months instead of 12. Apply online or at the visitor services desk.

────────────────────

[Form]
Penhallow Museum of Natural History
Membership Application

Date: November 18
Name: Ilse Meier
Address: 77 Fernhill Road, Asterbrook
Telephone: 555-0163
Membership level: Dual
Second cardholder: Anton Meier
Payment: $85, credit card
Additional donation (optional): $40 to the Education Fund
Where did you learn about the museum? From a coworker who volunteers there
Comments: If tickets are still available, we hope to attend the opening day of the dinosaur exhibition.

────────────────────

[E-mail]
To: Ilse Meier
From: Visitor Services, Penhallow Museum of Natural History
Date: November 20
Subject: Welcome to the museum

Dear Ms. Meier,

Welcome, and thank you for your additional gift to the Education Fund. Your membership cards will be mailed within 10 days. Until they arrive, please show this e-mail at the entrance, and you will be admitted without delay.

In reply to your comment, Giants of the Cretaceous opens on December 6 and will run until the end of April. I am sorry to say that tickets for opening day are sold out, but plenty remain for December 7 onward. The regular price is $24 per adult. Members may book now at the member rate on our website; booking opens to the general public on November 27.

You may also be interested to know that members at your level can upgrade to a Family membership at any time by paying only the difference in price. Several members do so each year when relatives come to visit.

Warm regards,
Jerome Baptiste
Visitor Services', 'NONE', '["$25", "$40", "$85", "$110"]', 'A', 1, '[연계(문서1+2+3)] 정답 (A). 이메일은 Family로 올릴 때 "paying only the difference in price"라고 했고, 신청서의 등급은 Dual($85), 웹 페이지의 Family는 $110이므로 110 − 85 = 25달러다. 오답: (B) 신청서의 추가 기부액, (C) 이미 낸 Dual 요금, (D) Family 전체 요금이다.
포인트: 연계 문항 — 웹 페이지 요금 + 신청서 등급 + 이메일 셋째 문단', 200);
