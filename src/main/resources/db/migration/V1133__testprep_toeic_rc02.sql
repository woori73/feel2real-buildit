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
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Ms. Okafor asked the paralegals to bring any errors in the contract drafts to ------- attention before the filing deadline.', NULL, 'NONE', '["she", "herself", "hers", "her"]', 'D', 1, '[문법-대명사(소유격)] 정답 (D). 명사 attention 앞에서 소유 관계를 나타내는 소유격 자리다. bring A to one''s attention(A를 ~에게 알리다)의 결합이다. 오답: (A) 주격은 명사 앞에 올 수 없다. (B) 재귀대명사는 명사를 꾸미지 못하며, 주어와 같은 인물이라는 사실만으로 고르지 않는다. (C) 소유대명사는 뒤에 명사를 다시 쓰지 않는다.', 101),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Verdane Cosmetics'' fragrance-free moisturizer is ------- recommended by dermatologists for customers with sensitive skin.', NULL, 'NONE', '["wide", "widen", "widely", "width"]', 'C', 1, '[문법-품사(부사 자리)] 정답 (C). be동사와 과거분사 recommended 사이에서 분사를 꾸미는 부사 자리이고, 추천되는 범위가 넓다는 뜻이므로 widely가 맞다. 오답: (A) wide는 형용사이며 부사로 쓰여도 open wide처럼 물리적으로 크게 벌어지는 뜻이다. (B) 동사는 is와 recommended 사이에 올 수 없다. (D) 명사가 들어갈 자리가 아니다.', 102),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The Marlowe Springs History Museum will keep its ticket counter open ------- 9:00 P.M. on the opening night of the new exhibit.', NULL, 'NONE', '["by", "within", "among", "until"]', 'D', 1, '[전치사(until / by)] 정답 (D). keep ... open은 상태가 이어지는 표현이므로 그 상태가 끝나는 시점을 나타내는 until과 어울린다. 오답: (A) by는 submit, arrive처럼 한 번에 끝나는 동작의 완료 기한에 쓴다. (B) within 뒤에는 시각이 아니라 기간(within two hours)이 온다. (C) among은 여러 대상 ''사이에''라는 뜻이다.', 103),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Photo identification ------- of all contractors entering the server rooms at Brightwater Telecom''s Tennby facility.', NULL, 'NONE', '["requires", "is required", "requiring", "has required"]', 'B', 1, '[문법-태(수동)] 정답 (B). 신분증은 요구하는 주체가 아니라 요구되는 대상이고 빈칸 뒤에 목적어가 없으므로 수동태 본동사가 필요하다(be required of + 사람). 오답: (A)·(D) 능동형은 무엇을 요구하는지 목적어가 있어야 한다. (C) 분사만으로는 문장의 본동사가 될 수 없다.', 104),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Due to a shortage of glass jars, Hartwell Foods has ------- the launch of its new pasta sauce until early October.', NULL, 'NONE', '["postponed", "canceled", "declined", "expired"]', 'A', 1, '[어휘-동사] 정답 (A). until early October가 새로 잡힌 시점을 알려 주므로 ''~까지 미루다''인 postpone A until B가 맞다. 오답: (B) cancel은 일정 자체를 없애는 것이어서 until과 맞지 않는다. (C) decline은 제안·초대를 거절하는 동사다. (D) expire는 목적어를 취하지 않는 자동사다.', 105),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Greenleaf Landscaping offers a free ------- consultation to homeowners who are planning a new garden design.', NULL, 'NONE', '["initial", "initially", "initials", "initialize"]', 'A', 1, '[문법-품사(형용사 자리)] 정답 (A). 관사 a와 명사 consultation 사이에서 free와 함께 명사를 꾸미는 형용사 자리다. 관사 바로 뒤가 명사인 것이 아니라 명사구 끝의 consultation이 중심 명사다. 오답: (B) 부사는 명사를 꾸미지 못한다. (C) 복수 명사 ''머리글자''는 뜻과 수가 맞지 않는다. (D) 동사가 올 자리가 아니다.', 106),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The warranty for Solterra home batteries covers both replacement parts ------- the cost of labor for five years.', NULL, 'NONE', '["or", "but", "and", "nor"]', 'C', 1, '[접속사(상관접속사)] 정답 (C). 앞의 both와 짝을 이루어 두 명사구 replacement parts와 the cost of labor를 연결하는 both A and B다. 오답: (A) or는 either와, (D) nor는 neither와 짝을 이룬다. (B) but은 not only A but also B에서 쓰인다.', 107),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The help page on Brightwater Telecom''s Web site answers the most common ------- about billing and data plans.', NULL, 'NONE', '["inquiries", "suppliers", "vacancies", "receipts"]', 'A', 1, '[어휘-명사] 정답 (A). answers의 목적어이면서 about billing and data plans의 수식을 받으므로 ''문의 사항''인 inquiries가 맞다. 오답: (B) 공급업체, (C) 공석, (D) 영수증은 답변의 대상이 될 수 없다.', 108),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The number of households in Tennby that generate their own electricity ------- risen steadily over the past decade.', NULL, 'NONE', '["has", "have", "are", "were"]', 'A', 1, '[문법-수일치] 정답 (A). 주어의 중심은 households가 아니라 단수 The number이고, over the past decade와 함께 현재완료 has risen을 이룬다. 오답: (B) 가까운 복수명사 households에 끌린 형태다. (C)·(D) rise는 자동사여서 be + risen의 수동형을 만들지 않으며 수도 맞지 않는다.', 109),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'To reduce packaging waste, Hartwell Foods is considering ------- its plastic trays with ones made from recycled paper.', NULL, 'NONE', '["replace", "to replace", "replacing", "replaced"]', 'C', 1, '[문법-준동사(동명사)] 정답 (C). consider는 ''~하는 것을 검토하다''일 때 동명사를 목적어로 취하고, replacing이 다시 its plastic trays를 목적어로 받는다. 오답: (A) 동사원형은 is considering 뒤에 바로 올 수 없다. (B) consider는 to부정사를 목적어로 취하지 않는다. (D) 과거분사는 뒤의 목적어를 받지 못한다.', 110),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Customers who have held a Brightwater mobile plan for at least two years are ------- for a free handset upgrade.', NULL, 'NONE', '["valid", "capable", "eligible", "accessible"]', 'C', 1, '[어휘-형용사] 정답 (C). 사람이 조건을 충족해 혜택을 받을 자격이 있을 때 be eligible for를 쓴다. 오답: (A) valid는 표·쿠폰 등이 유효하다는 뜻으로 사람 주어와 맞지 않는다. (B) capable은 of와 결합한다. (D) accessible은 장소·정보에 접근할 수 있다는 뜻이다.', 111),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', '------- demand for home charging stations rose sharply last year, Solterra Energy did not hire any additional technicians.', NULL, 'NONE', '["Despite", "During", "Owing to", "Although"]', 'D', 1, '[접속사(양보, 절 연결)] 정답 (D). 빈칸 뒤에 주어 demand와 동사 rose가 있는 절이 오고, 수요 증가와 인력 미충원이 양보 관계이므로 접속사 Although가 맞다. 오답: (A) Despite, (B) During, (C) Owing to는 모두 전치사여서 뒤에 절이 올 수 없다.', 112),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Visitors ------- to tour the processing plant must sign in at the security desk and wear a hairnet at all times.', NULL, 'NONE', '["wish", "wished", "wishing", "to be wished"]', 'C', 1, '[문법-분사(능동·수동)] 정답 (C). 본동사는 must sign in이므로 빈칸은 Visitors를 뒤에서 꾸미는 수식어다. 방문객이 견학을 ''원하는'' 주체이므로 능동의 현재분사 wishing이 맞다(who wish to tour). 오답: (A)·(B) 정형동사를 넣으면 한 문장에 본동사가 둘이 된다. (D) 수동 부정사는 뜻이 통하지 않는다.', 113),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'All food processors operating in Marlowe Springs must ------- with the region''s updated hygiene regulations.', NULL, 'NONE', '["obey", "comply", "observe", "follow"]', 'B', 1, '[어휘-동사(동사+전치사)] 정답 (B). 빈칸 뒤에 전치사 with가 있으므로 comply with(~을 준수하다)가 맞다. 오답: (A) obey, (C) observe, (D) follow는 뜻은 비슷하지만 모두 타동사여서 with 없이 the regulations를 바로 목적어로 취한다.', 114),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The new irrigation system has kept the lawns at Tennby Central Park ------- throughout the unusually dry summer.', NULL, 'NONE', '["health", "healthily", "healthiness", "healthy"]', 'D', 1, '[문법-품사(목적격 보어)] 정답 (D). keep + 목적어 + 목적격 보어 구조에서 목적어 the lawns의 상태를 설명하는 형용사가 필요하다. at Tennby Central Park는 목적어를 꾸미는 전치사구일 뿐이다. 오답: (A)·(C) 명사는 잔디와 동격이 되지 않는다. (B) 부사는 목적어의 상태를 나타내는 보어가 될 수 없다.', 115),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Before any work begins, Greenleaf Landscaping provides each client with a written ------- of the expected cost.', NULL, 'NONE', '["invoice", "estimate", "receipt", "refund"]', 'B', 1, '[어휘-명사] 정답 (B). 작업 시작 전(Before any work begins)에 주는 ''예상 비용(expected cost)''에 대한 문서는 견적서 estimate다. 오답: (A) invoice는 작업 뒤 대금을 청구하는 문서다. (C) receipt는 지불을 증명하는 영수증이다. (D) refund는 이미 낸 돈을 돌려주는 것이다.', 116),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The tour includes a two-hour stop in Marlowe Springs, ------- travelers may explore the old market on their own.', NULL, 'NONE', '["which", "during which", "whom", "what"]', 'B', 1, '[문법-관계사(전치사+관계대명사)] 정답 (B). 빈칸 뒤는 주어·동사·목적어가 모두 갖춰진 완전한 절이다. 선행사 a two-hour stop은 기간을 지닌 명사이므로 during the stop → during which가 된다. 오답: (A) which는 뒤 절에 주어나 목적어가 빠져 있어야 한다. (C) whom은 사람 선행사를 받는다. (D) what은 선행사를 취하지 않는다.', 117),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', '------- all of the seats on Wayfarer Travel''s harbor cruise were sold within an hour of going on sale.', NULL, 'NONE', '["Most", "Almost", "Many", "Much"]', 'B', 1, '[어휘-부사(almost / most)] 정답 (B). all을 앞에서 꾸며 ''거의 전부''를 만드는 부사는 Almost다. 오답: (A) Most는 most of the seats처럼 all 없이 쓴다. (C) Many, (D) Much는 all을 수식하지 못하고 many of the seats처럼 쓴다.', 118),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'From a single control room, Solterra Energy now ------- all of the turbines that it installed along the Tennby coast three years ago.', NULL, 'NONE', '["monitored", "monitoring", "has been monitored", "monitors"]', 'D', 1, '[문법-시제(주절과 관계절의 시간 단서)] 정답 (D). three years ago는 관계절의 installed에 걸리고, 주절에는 now가 있으므로 현재 시제 본동사 monitors가 맞다. 오답: (A) 과거 표현이 보인다고 주절까지 과거로 쓰면 now와 충돌한다. (B) 분사만으로는 본동사가 될 수 없다. (C) 수동태는 뒤의 목적어 all of the turbines를 받지 못한다.', 119),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Hartwell Foods prints the expiration date in large type ------- shoppers can read it without picking up the package.', NULL, 'NONE', '["in order to", "so that", "even though", "as well as"]', 'B', 1, '[접속사(목적 so that)] 정답 (B). 빈칸 뒤에 shoppers can read라는 절이 오고, 큰 글씨로 인쇄하는 목적을 나타내므로 so that이 맞다. 오답: (A) in order to 뒤에는 동사원형이 온다. (C) even though는 절을 이끌지만 양보 관계여서 뜻이 맞지 않는다. (D) as well as는 절과 절을 연결하지 못한다.', 120);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Customers describe Brightwater Telecom''s support staff as highly ------- to service complaints, noting that most problems are fixed within a day.', NULL, 'NONE', '["responsible", "responsive", "respective", "resistant"]', 'B', 1, '[어휘-형용사(responsive / responsible)] 정답 (B). within a day가 대응 속도를 보여 주므로 ''~에 신속히 대응하는''인 responsive to가 맞다. 오답: (A) responsible은 담당 업무를 나타낼 때 for와 결합한다. (C) respective는 ''각각의''라는 뜻으로 명사 앞에만 쓴다. (D) resistant to는 ''~에 저항하는''이어서 하루 안에 해결한다는 내용과 반대다.', 121),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The safety consultant recommended that every forklift at the plant ------- by a certified mechanic at least twice a year.', NULL, 'NONE', '["inspects", "is inspecting", "inspected", "be inspected"]', 'D', 1, '[문법-제안절(동사원형 수동)] 정답 (D). recommend 뒤의 that절은 앞으로 할 일을 권고하므로 동사원형을 쓰고, 지게차는 점검받는 대상(by a certified mechanic)이므로 수동 원형 be inspected가 맞다. 오답: (A)·(B)·(C) 모두 능동형이어서 지게차가 점검하는 주체가 되고, 뒤에 목적어도 없다.', 122),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', '------- the rising cost of imported olive oil, Hartwell Foods has decided to source more ingredients from local farms.', NULL, 'NONE', '["Nevertheless", "Even though", "In light of", "In case"]', 'C', 1, '[전치사(판단 근거 in light of)] 정답 (C). 빈칸 뒤 the rising cost of imported olive oil은 명사구이고 결정의 근거이므로 전치사 표현 In light of(~을 고려하여)가 맞다. 오답: (A) Nevertheless는 접속부사여서 명사구를 이끌지 못한다. (B) Even though, (D) In case는 접속사여서 뒤에 주어와 동사가 필요하다.', 123),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The 30-day return period for Verdane Cosmetics products begins upon ------- of the order at the customer''s address.', NULL, 'NONE', '["receipt", "recipient", "reception", "receiver"]', 'A', 1, '[어휘-명사(같은 어근 명사의 의미)] 정답 (A). upon receipt of는 ''~을 받는 즉시''라는 표현이며, 여기서 receipt는 영수증이 아니라 ''수령''이라는 행위다. 오답: (B) recipient는 받는 사람, (D) receiver는 수신자·수화기로 모두 사람·도구 명사다. (C) reception은 접수처·환영 행사·수신 상태를 뜻한다.', 124),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Not until the soil tests were completed last spring ------- Greenleaf Landscaping begin planting trees along the Tennby riverfront.', NULL, 'NONE', '["has", "was", "does", "did"]', 'D', 1, '[문법-도치(부정어구 강조)] 정답 (D). 부정어구 Not until ...이 문두에 오면 주절이 조동사 + 주어 + 동사원형으로 도치된다. 뒤에 원형 begin이 있고 last spring이 과거이므로 did가 맞다. 오답: (A) has 뒤에는 begun이 필요하다. (B) was 뒤에는 원형이 올 수 없다. (C) does는 과거 시점 last spring과 맞지 않는다.', 125),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Members of the Marlowe Springs Museum Society receive ------- treatment when booking tickets for special lectures and guided tours.', NULL, 'NONE', '["preferential", "preferable", "prospective", "proficient"]', 'A', 1, '[어휘-형용사(preferential / preferable)] 정답 (A). 특정 집단에 예약 우선권을 주는 것은 preferential treatment(우대)다. 오답: (B) preferable은 ''더 바람직한''이라는 판단이어서 treatment와 결합해 우대의 뜻을 만들지 못한다. (C) prospective는 ''장래의, 예비의'', (D) proficient는 ''능숙한''으로 사람의 능력을 설명한다.', 126),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The company shuttle is available to ------- working the late shift at the Solterra assembly plant.', NULL, 'NONE', '["anyone", "whoever", "whichever", "every"]', 'A', 1, '[문법-대명사(anyone + 분사 / whoever)] 정답 (A). 전치사 to의 목적어가 될 대명사가 필요하고, working the late shift가 그 대명사를 뒤에서 꾸민다(anyone who is working). 오답: (B) whoever는 whoever works처럼 시제가 있는 동사가 따라야 한다. (C) whichever도 절을 이끌어야 한다. (D) every는 뒤에 명사가 필요한 한정사다.', 127),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Thanks to the ------- processing of visa applications, Wayfarer Travel''s clients rarely experience delays before departure.', NULL, 'NONE', '["promptness", "promptly", "prompt", "prompted"]', 'C', 1, '[문법-품사(명사구 안의 형용사 / 부사)] 정답 (C). the ------- processing of ...에서 processing은 관사와 of 사이에 놓인 명사이므로 그 앞에는 형용사 prompt가 온다. 오답: (B) promptly는 processing applications promptly처럼 -ing가 목적어를 직접 취하는 동사적 구조에서 쓴다. (A) 명사는 processing을 꾸미지 못한다. (D) prompted는 ''촉발된''이라는 뜻이어서 맞지 않는다.', 128),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'The project manager will ------- residents of the revised schedule for the installation of underground cables on Pine Street.', NULL, 'NONE', '["explain", "notify", "announce", "mention"]', 'B', 1, '[어휘-동사(동사 + 사람 + of)] 정답 (B). 사람(residents)을 바로 목적어로 취하고 of + 내용이 이어지는 문형은 notify A of B다. 오답: (A) explain, (C) announce, (D) mention은 내용을 목적어로 취하고 듣는 사람은 to 뒤에 쓴다(explain the schedule to residents).', 129),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P5', 'Although the opening speech ran ten minutes over, the shareholders'' meeting ------- proceeded exactly as scheduled.', NULL, 'NONE', '["otherwise", "likewise", "instead", "moreover"]', 'A', 1, '[어휘-부사] 정답 (A). 개회사가 길어진 한 가지 예외를 빼면 나머지는 예정대로였다는 뜻이므로 ''그 밖의 점에서는''인 otherwise가 맞다. 오답: (B) likewise는 ''마찬가지로''여서 예외와 나머지의 대비를 나타내지 못한다. (C) instead는 대체, (D) moreover는 추가를 나타내어 Although절과 맞지 않는다.', 130),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (131).', 'Questions 131-134 refer to the following e-mail.

To: Nadia Ferreira
From: Brightwater Telecom Customer Care
Date: November 4
Subject: Your October statement

Dear Ms. Ferreira,

Thank you for contacting us about your October statement. We have reviewed your account and confirmed that the monthly service fee was charged to your credit card twice. We will (131) ------- the second payment to the same card within five business days.

We apologize for this mistake. It occurred while (132) ------- billing system was being updated last month. The update has since been completed. (133) -------, your future statements should be accurate.

To thank you for your patience, we have added two gigabytes of data to your plan for November at no charge. (134) -------.

Sincerely,

Owen Brannigan
Brightwater Telecom Customer Care', 'NONE', '["refund", "waive", "collect", "estimate"]', 'A', 1, '[Part6-어휘(문맥)] 정답 (A). 앞 문장에서 요금이 카드로 두 번 결제되었다(was charged ... twice)고 했으므로, 이미 받은 두 번째 결제액을 같은 카드로 돌려주는 refund가 맞다. 오답: (B) waive는 아직 받지 않은 요금을 면제할 때 쓴다. (C) collect는 돈을 더 걷는 것이다. (D) estimate는 금액을 어림하는 것이다.', 131),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (132).', 'Questions 131-134 refer to the following e-mail.

To: Nadia Ferreira
From: Brightwater Telecom Customer Care
Date: November 4
Subject: Your October statement

Dear Ms. Ferreira,

Thank you for contacting us about your October statement. We have reviewed your account and confirmed that the monthly service fee was charged to your credit card twice. We will (131) ------- the second payment to the same card within five business days.

We apologize for this mistake. It occurred while (132) ------- billing system was being updated last month. The update has since been completed. (133) -------, your future statements should be accurate.

To thank you for your patience, we have added two gigabytes of data to your plan for November at no charge. (134) -------.

Sincerely,

Owen Brannigan
Brightwater Telecom Customer Care', 'NONE', '["your", "our", "its", "their"]', 'B', 1, '[Part6-대명사(지시 대상)] 정답 (B). 청구 시스템은 편지를 보낸 통신사의 것이다. 글 전체에서 발신자는 We로 표현되므로 our가 맞다. 오답: (A) your는 수신자인 고객을 가리킨다. (C) its가 받을 단수 사물, (D) their가 받을 복수 명사가 앞에 없다.', 132),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (133).', 'Questions 131-134 refer to the following e-mail.

To: Nadia Ferreira
From: Brightwater Telecom Customer Care
Date: November 4
Subject: Your October statement

Dear Ms. Ferreira,

Thank you for contacting us about your October statement. We have reviewed your account and confirmed that the monthly service fee was charged to your credit card twice. We will (131) ------- the second payment to the same card within five business days.

We apologize for this mistake. It occurred while (132) ------- billing system was being updated last month. The update has since been completed. (133) -------, your future statements should be accurate.

To thank you for your patience, we have added two gigabytes of data to your plan for November at no charge. (134) -------.

Sincerely,

Owen Brannigan
Brightwater Telecom Customer Care', 'NONE', '["Otherwise", "For instance", "Even so", "As a result"]', 'D', 1, '[Part6-연결어] 정답 (D). 앞 문장은 원인(시스템 업데이트가 끝남), 빈칸 뒤는 그 결과(앞으로의 청구서는 정확함)이므로 As a result가 맞다. 오답: (A) Otherwise는 ''그렇지 않으면''이다. (B) For instance 뒤에는 앞 내용의 구체적 사례가 와야 한다. (C) Even so는 앞 내용과 반대되는 내용을 이끈다.', 133),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (134).', 'Questions 131-134 refer to the following e-mail.

To: Nadia Ferreira
From: Brightwater Telecom Customer Care
Date: November 4
Subject: Your October statement

Dear Ms. Ferreira,

Thank you for contacting us about your October statement. We have reviewed your account and confirmed that the monthly service fee was charged to your credit card twice. We will (131) ------- the second payment to the same card within five business days.

We apologize for this mistake. It occurred while (132) ------- billing system was being updated last month. The update has since been completed. (133) -------, your future statements should be accurate.

To thank you for your patience, we have added two gigabytes of data to your plan for November at no charge. (134) -------.

Sincerely,

Owen Brannigan
Brightwater Telecom Customer Care', 'NONE', '["You do not need to take any action to receive it.", "Please return the device in its original packaging.", "Your contract will therefore end on October 31.", "The second payment is due by the end of this week."]', 'A', 1, '[Part6-문장삽입] 정답 (A). 바로 앞 문장의 무료 데이터 2기가바이트를 it으로 받아, 별도 신청 없이 제공된다고 덧붙이는 문장이다. 오답: (B) 기기 반품은 글에 나온 적이 없다. (C) 계약 종료는 사과·보상 내용과 연결되지 않는다. (D) 두 번째 결제액은 환불 대상이어서 납부 기한을 말하면 앞 내용과 모순된다.', 134),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (135).', 'Questions 135-138 refer to the following notice.

MARLOWE SPRINGS MUSEUM OF NATURAL HISTORY
Notice to Visitors

The Fossil Gallery on the second floor (135) ------- from January 6 through February 28. During this period, new lighting and climate-control equipment will be installed to better protect the specimens on display.

Several of the gallery''s most popular fossils will not be placed in storage. (136) -------, they will be moved to the main lobby, where they can be viewed free of charge. All other galleries will remain open during regular hours.

The work is expected to take about eight weeks, but this schedule is (137) -------. A firm reopening date will be posted on our Web site once the new equipment has been tested. (138) -------.', 'NONE', '["closed", "has closed", "will be closed", "was closing"]', 'C', 1, '[Part6-문법(시제·문맥)] 정답 (C). 다음 문장이 During this period, ... will be installed라고 하여 휴관 기간이 앞으로의 일임을 알려 주므로 미래 시제 will be closed가 맞다. 오답: (A) closed, (D) was closing은 이미 지난 일로 서술하게 된다. (B) has closed는 from ... through ...로 끝 날짜가 정해진 미래 기간과 함께 쓸 수 없다.', 135),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (136).', 'Questions 135-138 refer to the following notice.

MARLOWE SPRINGS MUSEUM OF NATURAL HISTORY
Notice to Visitors

The Fossil Gallery on the second floor (135) ------- from January 6 through February 28. During this period, new lighting and climate-control equipment will be installed to better protect the specimens on display.

Several of the gallery''s most popular fossils will not be placed in storage. (136) -------, they will be moved to the main lobby, where they can be viewed free of charge. All other galleries will remain open during regular hours.

The work is expected to take about eight weeks, but this schedule is (137) -------. A firm reopening date will be posted on our Web site once the new equipment has been tested. (138) -------.', 'NONE', '["Similarly", "As a result", "Instead", "For example"]', 'C', 1, '[Part6-연결어] 정답 (C). 앞 문장은 화석을 보관 창고에 넣지 않는다는 내용이고, 뒤 문장은 그 대신 로비로 옮긴다는 내용이므로 대체를 나타내는 Instead가 맞다. 오답: (A) Similarly는 비슷한 내용을 덧붙인다. (B) As a result는 원인과 결과를 연결한다. (D) For example 뒤에는 앞 진술의 사례가 와야 한다.', 136),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (137).', 'Questions 135-138 refer to the following notice.

MARLOWE SPRINGS MUSEUM OF NATURAL HISTORY
Notice to Visitors

The Fossil Gallery on the second floor (135) ------- from January 6 through February 28. During this period, new lighting and climate-control equipment will be installed to better protect the specimens on display.

Several of the gallery''s most popular fossils will not be placed in storage. (136) -------, they will be moved to the main lobby, where they can be viewed free of charge. All other galleries will remain open during regular hours.

The work is expected to take about eight weeks, but this schedule is (137) -------. A firm reopening date will be posted on our Web site once the new equipment has been tested. (138) -------.', 'NONE', '["permanent", "overdue", "tentative", "punctual"]', 'C', 1, '[Part6-어휘(문맥)] 정답 (C). 다음 문장에서 확정된(firm) 재개관일은 장비 시험이 끝난 뒤 게시한다고 했으므로 지금의 일정은 바뀔 수 있는 tentative(잠정적인) 상태다. 오답: (A) permanent는 ''영구적인''이다. (B) overdue는 기한이 지났다는 뜻이다. (D) punctual은 사람이나 교통편이 시간을 잘 지킨다는 뜻이다.', 137),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (138).', 'Questions 135-138 refer to the following notice.

MARLOWE SPRINGS MUSEUM OF NATURAL HISTORY
Notice to Visitors

The Fossil Gallery on the second floor (135) ------- from January 6 through February 28. During this period, new lighting and climate-control equipment will be installed to better protect the specimens on display.

Several of the gallery''s most popular fossils will not be placed in storage. (136) -------, they will be moved to the main lobby, where they can be viewed free of charge. All other galleries will remain open during regular hours.

The work is expected to take about eight weeks, but this schedule is (137) -------. A firm reopening date will be posted on our Web site once the new equipment has been tested. (138) -------.', 'NONE', '["The fossils were donated by a local collector in 1987.", "We apologize for any inconvenience this work may cause.", "Tickets for the lobby display must be purchased in advance.", "Applications for the position are due on January 6."]', 'B', 1, '[Part6-문장삽입] 정답 (B). 공사 일정 안내를 마친 뒤 this work로 앞의 공사를 받아 불편에 대해 사과하며 공지를 끝맺는 문장이다. 오답: (A) 화석의 기증 내력은 일정 안내의 마무리와 이어지지 않는다. (C) 로비 전시는 무료(free of charge)라고 했으므로 모순이다. (D) 채용 지원은 글에 나온 적이 없다.', 138),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (139).', 'Questions 139-142 refer to the following article.

TENNBY (May 14) — Solterra Energy announced yesterday that it has received approval to build a solar farm on the site of the former Tennby glassworks. The facility, (139) ------- to begin operating next autumn, will supply electricity to roughly 4,000 homes.

The project has drawn strong (140) ------- from local residents. In a survey conducted by the city council last month, 78 percent of respondents said that they were in favor of the plan.

Construction is scheduled to start in July. According to Solterra, most of the construction workers (141) ------- from the Tennby area. The company plans to hold a job fair at the Tennby Community Center on June 2. (142) -------.', 'NONE', '["expects", "expecting", "expected", "expectation"]', 'C', 1, '[Part6-문법(분사·동사의 자리)] 정답 (C). 문장의 본동사는 will supply이고 쉼표 사이는 The facility를 꾸미는 삽입구다. 시설은 가동될 것으로 ''예상되는'' 대상이므로 과거분사 expected가 맞다(which is expected to begin). 오답: (A) 정형동사를 넣으면 본동사가 둘이 된다. (B) 현재분사는 시설이 예상하는 주체가 된다. (D) 명사는 to begin과 이어지지 않는다.', 139),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (140).', 'Questions 139-142 refer to the following article.

TENNBY (May 14) — Solterra Energy announced yesterday that it has received approval to build a solar farm on the site of the former Tennby glassworks. The facility, (139) ------- to begin operating next autumn, will supply electricity to roughly 4,000 homes.

The project has drawn strong (140) ------- from local residents. In a survey conducted by the city council last month, 78 percent of respondents said that they were in favor of the plan.

Construction is scheduled to start in July. According to Solterra, most of the construction workers (141) ------- from the Tennby area. The company plans to hold a job fair at the Tennby Community Center on June 2. (142) -------.', 'NONE', '["support", "criticism", "competition", "hesitation"]', 'A', 1, '[Part6-어휘(문맥)] 정답 (A). 다음 문장에서 응답자의 78퍼센트가 계획에 찬성했다(in favor of the plan)고 했으므로 주민들의 강한 지지(support)가 맞다. 오답: (B) criticism은 찬성 비율과 반대되는 내용이다. (C) competition은 경쟁, (D) hesitation은 망설임으로 설문 결과와 맞지 않는다.', 140);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (141).', 'Questions 139-142 refer to the following article.

TENNBY (May 14) — Solterra Energy announced yesterday that it has received approval to build a solar farm on the site of the former Tennby glassworks. The facility, (139) ------- to begin operating next autumn, will supply electricity to roughly 4,000 homes.

The project has drawn strong (140) ------- from local residents. In a survey conducted by the city council last month, 78 percent of respondents said that they were in favor of the plan.

Construction is scheduled to start in July. According to Solterra, most of the construction workers (141) ------- from the Tennby area. The company plans to hold a job fair at the Tennby Community Center on June 2. (142) -------.', 'NONE', '["were hired", "will be hired", "have hired", "hiring"]', 'B', 1, '[Part6-문법(시제·태·문맥)] 정답 (B). 공사는 7월에 시작할 예정이고 채용 박람회도 6월 2일로 아직 열리지 않았으므로 채용은 앞으로의 일이다. 인부들은 고용되는 대상이므로 미래 수동 will be hired가 맞다. 오답: (A) 과거형은 뒤 문장의 박람회 계획과 맞지 않는다. (C) 능동형은 목적어가 없고 인부가 고용하는 주체가 된다. (D) 분사만으로는 본동사가 될 수 없다.', 141),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (142).', 'Questions 139-142 refer to the following article.

TENNBY (May 14) — Solterra Energy announced yesterday that it has received approval to build a solar farm on the site of the former Tennby glassworks. The facility, (139) ------- to begin operating next autumn, will supply electricity to roughly 4,000 homes.

The project has drawn strong (140) ------- from local residents. In a survey conducted by the city council last month, 78 percent of respondents said that they were in favor of the plan.

Construction is scheduled to start in July. According to Solterra, most of the construction workers (141) ------- from the Tennby area. The company plans to hold a job fair at the Tennby Community Center on June 2. (142) -------.', 'NONE', '["Residents can therefore expect higher electricity bills.", "However, the council rejected the proposal last week.", "Solar panels should be cleaned at least twice a year.", "Details will be posted on the company''s Web site next week."]', 'D', 1, '[Part6-문장삽입] 정답 (D). 바로 앞 문장의 채용 박람회에 대한 세부 사항(Details)을 어디서 볼 수 있는지 덧붙이며, the company가 Solterra를 받는다. 오답: (A) 전기 요금 인상은 근거가 없고 therefore로 이어질 원인도 없다. (B) 첫 문장에서 승인을 받았다고 했으므로 모순이다. (C) 패널 관리 요령은 기사 내용과 무관하다.', 142),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (143).', 'Questions 143-146 refer to the following memo.

To: All production staff
From: Priya Raman, Plant Manager
Date: March 3
Subject: New labeling machine

Starting March 10, Line 2 will use a new labeling machine. The current machine requires operators to check every label by hand. The new (143) ------- reads each barcode automatically and stops the line whenever it detects an error. (144) -------.

All Line 2 operators must complete a two-hour training session before using the machine. Sessions (145) ------- in the staff room on March 6 and 7. Please sign up with your shift supervisor by Thursday.

Operators on other lines are not required to attend. (146) -------, they are welcome to join if space allows.', 'NONE', '["schedule", "model", "policy", "operator"]', 'B', 1, '[Part6-어휘(문맥)] 정답 (B). 앞 문장의 The current machine과 대비되는 새 기계를 가리켜야 하고, 바코드를 자동으로 읽는 주체이므로 기계의 ''기종''인 model이 맞다. 오답: (A) schedule, (C) policy는 바코드를 읽을 수 없다. (D) operator는 사람이어서 automatically reads, 뒤의 it과 맞지 않고 앞 문장의 기계 비교와도 어긋난다.', 143),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (144).', 'Questions 143-146 refer to the following memo.

To: All production staff
From: Priya Raman, Plant Manager
Date: March 3
Subject: New labeling machine

Starting March 10, Line 2 will use a new labeling machine. The current machine requires operators to check every label by hand. The new (143) ------- reads each barcode automatically and stops the line whenever it detects an error. (144) -------.

All Line 2 operators must complete a two-hour training session before using the machine. Sessions (145) ------- in the staff room on March 6 and 7. Please sign up with your shift supervisor by Thursday.

Operators on other lines are not required to attend. (146) -------, they are welcome to join if space allows.', 'NONE', '["This should reduce the number of mislabeled packages considerably.", "The old labels may be ordered from the supply office.", "Line 2 has been closed permanently as a result.", "Customers have praised the taste of the new sauce."]', 'A', 1, '[Part6-문장삽입] 정답 (A). 앞 문장이 오류를 자동으로 감지해 라인을 멈추는 ''방법''을 설명했고, This가 그 기능을 받아 라벨 오류 감소라는 ''결과''를 말한다. 오답: (B) 기존 라벨 주문은 언급된 적이 없다. (C) Line 2는 3월 10일부터 새 기계를 쓰므로 폐쇄와 모순된다. (D) 제품 맛에 대한 평가는 기계 안내와 무관하다.', 144),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (145).', 'Questions 143-146 refer to the following memo.

To: All production staff
From: Priya Raman, Plant Manager
Date: March 3
Subject: New labeling machine

Starting March 10, Line 2 will use a new labeling machine. The current machine requires operators to check every label by hand. The new (143) ------- reads each barcode automatically and stops the line whenever it detects an error. (144) -------.

All Line 2 operators must complete a two-hour training session before using the machine. Sessions (145) ------- in the staff room on March 6 and 7. Please sign up with your shift supervisor by Thursday.

Operators on other lines are not required to attend. (146) -------, they are welcome to join if space allows.', 'NONE', '["held", "were held", "are holding", "will be held"]', 'D', 1, '[Part6-문법(시제·태·문맥)] 정답 (D). 메모 작성일이 3월 3일이므로 3월 6일과 7일의 교육은 앞으로의 일이고, 교육은 열리는 대상이므로 미래 수동 will be held가 맞다. 오답: (A) held만 쓰면 본동사가 없는 문장이 되거나 능동 과거가 되어 목적어가 없다. (B) 과거 수동은 작성일과 맞지 않는다. (C) 능동 진행형은 목적어가 필요하다.', 145),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (146).', 'Questions 143-146 refer to the following memo.

To: All production staff
From: Priya Raman, Plant Manager
Date: March 3
Subject: New labeling machine

Starting March 10, Line 2 will use a new labeling machine. The current machine requires operators to check every label by hand. The new (143) ------- reads each barcode automatically and stops the line whenever it detects an error. (144) -------.

All Line 2 operators must complete a two-hour training session before using the machine. Sessions (145) ------- in the staff room on March 6 and 7. Please sign up with your shift supervisor by Thursday.

Operators on other lines are not required to attend. (146) -------, they are welcome to join if space allows.', 'NONE', '["Therefore", "In other words", "For example", "However"]', 'D', 1, '[Part6-연결어] 정답 (D). 앞 문장은 다른 라인 작업자는 참석 의무가 없다는 내용이고, 뒤 문장은 그래도 자리가 있으면 참석해도 된다는 내용이므로 대조를 나타내는 However가 맞다. 오답: (A) Therefore는 결과, (B) In other words는 바꿔 말하기, (C) For example은 예시를 이끌어 두 문장의 관계와 맞지 않는다.', 146),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is the purpose of the notice?', 'Questions 147-148 refer to the following notice.

Corliss Plaza — Notice to Tenants

The lower level of the parking garage will be closed from March 9 through March 13 while the floor is resurfaced. During this period, vehicles with a lower-level permit may park on the rooftop level, which will be reserved for permit holders only. You do not need to apply for anything; your current access card will open the rooftop gate automatically.

Please remove all vehicles from the lower level by 8:00 P.M. on March 8. Any vehicle left there after that time will be towed at the owner''s expense.

Visitor parking on the street level is not affected. Questions may be directed to the building management office at 555-0142.', 'NONE', '["To introduce a new fee for parking permits", "To explain how to replace a lost access card", "To report that a repair project has ended", "To announce a temporary change in parking"]', 'D', 1, '[주제·목적] 정답 (D). "The lower level of the parking garage will be closed from March 9 through March 13"과 그 기간에 옥상층을 쓰라는 안내가 핵심이므로, 일시적인 주차 변경을 알리는 글이다. 오답: (A) 요금 이야기는 없다. (B) 기존 출입 카드가 그대로 작동한다고 했을 뿐 재발급 안내가 아니다. (C) 공사는 앞으로 할 일이지 끝난 일이 아니다.', 147),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What are lower-level permit holders asked to do?', 'Questions 147-148 refer to the following notice.

Corliss Plaza — Notice to Tenants

The lower level of the parking garage will be closed from March 9 through March 13 while the floor is resurfaced. During this period, vehicles with a lower-level permit may park on the rooftop level, which will be reserved for permit holders only. You do not need to apply for anything; your current access card will open the rooftop gate automatically.

Please remove all vehicles from the lower level by 8:00 P.M. on March 8. Any vehicle left there after that time will be towed at the owner''s expense.

Visitor parking on the street level is not affected. Questions may be directed to the building management office at 555-0142.', 'NONE', '["Apply for a rooftop parking permit", "Show their access cards to a guard", "Take their cars out of an area by a set time", "Park in the street-level visitor spaces"]', 'C', 1, '[세부 사항] 정답 (C). "Please remove all vehicles from the lower level by 8:00 P.M. on March 8"를 ''정해진 시각까지 한 구역에서 차를 빼다''로 바꿔 말했다. 오답: (A) "You do not need to apply for anything"이라고 했다. (B) 카드가 문을 자동으로 연다고 했고 경비원 언급은 없다. (D) 거리층은 방문객용이고 허가증 소지자는 옥상층에 주차한다.', 148),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What problem does Ms. Raman mention?', 'Questions 149-150 refer to the following text-message chain.

Priya Raman (8:52 A.M.)
Joel, I''m in Room 204 setting up for the 10:00 budgeting workshop. The projector won''t turn on.

Joel Becker (8:54 A.M.)
Did you try the spare cable in the cabinet?

Priya Raman (8:57 A.M.)
Yes. Still nothing. And 26 people have signed up, so I can''t just pass my laptop around.

Joel Becker (8:59 A.M.)
Room 310 is free until noon, and its screen was replaced last week. Want me to book it?

Priya Raman (9:00 A.M.)
That would save the day. I''ll carry the handouts upstairs now.

Joel Becker (9:03 A.M.)
Done. I''ll also put a sign on the door of 204 so nobody waits in the wrong place.', 'NONE', '["A room has been reserved by two groups.", "Some handouts have not been printed.", "Too few people have signed up.", "A piece of equipment is not working."]', 'D', 1, '[세부 사항] 정답 (D). "The projector won''t turn on"을 ''장비 하나가 작동하지 않는다''로 바꿔 말했다. 오답: (A) 중복 예약 이야기는 없다. (B) 유인물은 이미 있어서 위층으로 옮긴다고 했다. (C) 26명이 신청했다고 했으므로 인원 부족이 아니다.', 149),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'At 9:00 A.M., what does Ms. Raman most likely mean when she writes, "That would save the day"?', 'Questions 149-150 refer to the following text-message chain.

Priya Raman (8:52 A.M.)
Joel, I''m in Room 204 setting up for the 10:00 budgeting workshop. The projector won''t turn on.

Joel Becker (8:54 A.M.)
Did you try the spare cable in the cabinet?

Priya Raman (8:57 A.M.)
Yes. Still nothing. And 26 people have signed up, so I can''t just pass my laptop around.

Joel Becker (8:59 A.M.)
Room 310 is free until noon, and its screen was replaced last week. Want me to book it?

Priya Raman (9:00 A.M.)
That would save the day. I''ll carry the handouts upstairs now.

Joel Becker (9:03 A.M.)
Done. I''ll also put a sign on the door of 204 so nobody waits in the wrong place.', 'NONE', '["She wants to move the workshop to the afternoon.", "She thinks the screen in Room 204 should be replaced.", "She needs more time to finish the handouts.", "She gladly accepts the offer to reserve another room."]', 'D', 1, '[의도 파악] 정답 (D). 바로 앞에서 Mr. Becker가 "Room 310 is free until noon ... Want me to book it?"이라고 제안했고, Ms. Raman은 곧바로 유인물을 위층으로 옮기겠다고 했으므로 다른 방을 예약해 주겠다는 제안을 반긴다는 뜻이다. 오답: (A) 시간을 바꾸자는 말은 없다. (B) 교체된 것은 310호 화면이다. (C) 유인물 준비 시간이 아니라 방 문제를 말하고 있다.', 150),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about Tennby Airport Shuttle?', 'Questions 151-152 refer to the following advertisement.

Tennby Airport Shuttle — Reliable Rides, Day or Night

Serving Tennby Regional Airport from downtown Tennby and Marlowe Springs since 2009.

• Departures every 30 minutes from 4:00 A.M. to 11:30 P.M.
• Spacious coaches with free Wi-Fi and room for two suitcases per passenger
• One-way fare: $18 from Tennby, $26 from Marlowe Springs

Book online at least 24 hours before departure and save 10 percent. Groups of eight or more are eligible for a private van at the regular fare; call 555-0177 to arrange one.

Tickets are valid only for the date and time printed on them. Changes can be made free of charge up to two hours before departure.', 'NONE', '["It runs 24 hours a day.", "It picks up passengers in two cities.", "It charges a fee for each suitcase.", "It recently bought new coaches."]', 'B', 1, '[사실 확인] 정답 (B). "from downtown Tennby and Marlowe Springs"에서 두 도시에서 승객을 태운다는 것을 알 수 있다. 오답: (A) 운행은 오전 4시부터 밤 11시 30분까지다. (C) 승객 1인당 가방 두 개 자리가 있다고 했을 뿐 요금은 없다. (D) 새 차량 구입 언급은 없다.', 151),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'How can customers receive a discount?', 'Questions 151-152 refer to the following advertisement.

Tennby Airport Shuttle — Reliable Rides, Day or Night

Serving Tennby Regional Airport from downtown Tennby and Marlowe Springs since 2009.

• Departures every 30 minutes from 4:00 A.M. to 11:30 P.M.
• Spacious coaches with free Wi-Fi and room for two suitcases per passenger
• One-way fare: $18 from Tennby, $26 from Marlowe Springs

Book online at least 24 hours before departure and save 10 percent. Groups of eight or more are eligible for a private van at the regular fare; call 555-0177 to arrange one.

Tickets are valid only for the date and time printed on them. Changes can be made free of charge up to two hours before departure.', 'NONE', '["By traveling with seven other people", "By reserving a seat a day or more ahead", "By buying tickets for a round trip", "By changing a ticket two hours early"]', 'B', 1, '[세부 사항] 정답 (B). "Book online at least 24 hours before departure and save 10 percent"를 ''하루 이상 미리 예약''으로 바꿔 말했다. 오답: (A) 8명 이상은 전용 밴을 ''정상 요금으로'' 이용할 수 있을 뿐 할인이 아니다. (C) 왕복 할인은 없다. (D) 두 시간 전까지는 변경이 무료라는 내용이지 할인이 아니다.', 152),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'Who most likely is Mr. Oyelaran?', 'Questions 153-154 refer to the following e-mail.

To: Marcus Oyelaran
From: Front Desk, Halvern Tower
Date: June 3
Subject: Your building access

Dear Mr. Oyelaran,

Dunmore Engineering has informed us that you will be working on its ninth-floor network upgrade from June 10 to June 28. A temporary security badge has been issued in your name. Please collect it from the lobby front desk on your first morning and bring a government-issued photo ID.

The badge opens the main entrance and the elevators between 7:00 A.M. and 7:00 P.M. If you expect to work outside those hours, Dunmore''s office manager must send us written approval at least one day beforehand.

The badge must be returned to the front desk on your final day.

Sincerely,
Elena Marsh
Front Desk Supervisor', 'NONE', '["A technician on a short-term assignment", "A new member of the front desk staff", "The office manager at Dunmore Engineering", "A tenant moving into Halvern Tower"]', 'A', 1, '[추론] 정답 (A). "you will be working on its ninth-floor network upgrade from June 10 to June 28"와 "A temporary security badge"에서 약 3주 동안 네트워크 작업을 하러 오는 외부 기술자임을 알 수 있다. 오답: (B) 프런트 데스크는 메일을 보낸 쪽이다. (C) 사무실 관리자는 승인서를 보내는 다른 사람이다. (D) 입주가 아니라 기간이 정해진 작업이다.', 153),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'According to the e-mail, what requires approval in advance?', 'Questions 153-154 refer to the following e-mail.

To: Marcus Oyelaran
From: Front Desk, Halvern Tower
Date: June 3
Subject: Your building access

Dear Mr. Oyelaran,

Dunmore Engineering has informed us that you will be working on its ninth-floor network upgrade from June 10 to June 28. A temporary security badge has been issued in your name. Please collect it from the lobby front desk on your first morning and bring a government-issued photo ID.

The badge opens the main entrance and the elevators between 7:00 A.M. and 7:00 P.M. If you expect to work outside those hours, Dunmore''s office manager must send us written approval at least one day beforehand.

The badge must be returned to the front desk on your final day.

Sincerely,
Elena Marsh
Front Desk Supervisor', 'NONE', '["Picking up a badge on the first morning", "Using the elevators to reach the ninth floor", "Returning a badge after the final day", "Working in the building outside set hours"]', 'D', 1, '[세부 사항] 정답 (D). "If you expect to work outside those hours, Dunmore''s office manager must send us written approval at least one day beforehand"가 근거다. 오답: (A) 수령에는 사진이 있는 신분증만 필요하다. (B) 엘리베이터는 출입증으로 정해진 시간에 그냥 이용한다. (C) 출입증은 마지막 날 반납해야 한다고 했을 뿐 승인 이야기가 아니다.', 154),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is the main purpose of the memo?', 'Questions 155-157 refer to the following memo.

MEMO

To: All Staff, Pellam & Rowe Consulting
From: Anita Kowalczyk, Finance Director
Date: September 1
Subject: Changes to expense reimbursement

Beginning October 1, all expense claims must be submitted through the Claimwise mobile application. Paper claim forms will no longer be accepted after September 30.

With Claimwise, you simply photograph each receipt and select the related client project. Claims submitted by the 20th of a month will be reimbursed with that month''s salary payment; under the current system, employees often wait six weeks or longer.

Two rules are also changing on October 1. First, receipts will be required for every expense of $15 or more, down from $25. Second, any meal with clients that costs more than $200 must be approved by a department head before the meal takes place. The daily meal allowance for business trips remains $60.

Training sessions on the application will be held in Conference Room B on September 12 and 19. To sign up, contact Tomas Reyes at extension 418. Attendance is not mandatory, but it is strongly encouraged.', 'NONE', '["To remind staff of a deadline for a training course", "To announce an increase in a travel allowance", "To describe a new procedure for claiming expenses", "To ask managers to approve overdue payments"]', 'C', 1, '[주제·목적] 정답 (C). 제목 "Changes to expense reimbursement"와 "all expense claims must be submitted through the Claimwise mobile application"에서 경비 청구 절차가 새로 바뀐다는 것을 알리는 글임을 알 수 있다. 오답: (A) 교육은 선택 사항으로 덧붙인 안내일 뿐이다. (B) 식비 한도는 60달러 그대로다. (D) 밀린 지급을 승인해 달라는 요청은 없다.', 155),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the current reimbursement system?', 'Questions 155-157 refer to the following memo.

MEMO

To: All Staff, Pellam & Rowe Consulting
From: Anita Kowalczyk, Finance Director
Date: September 1
Subject: Changes to expense reimbursement

Beginning October 1, all expense claims must be submitted through the Claimwise mobile application. Paper claim forms will no longer be accepted after September 30.

With Claimwise, you simply photograph each receipt and select the related client project. Claims submitted by the 20th of a month will be reimbursed with that month''s salary payment; under the current system, employees often wait six weeks or longer.

Two rules are also changing on October 1. First, receipts will be required for every expense of $15 or more, down from $25. Second, any meal with clients that costs more than $200 must be approved by a department head before the meal takes place. The daily meal allowance for business trips remains $60.

Training sessions on the application will be held in Conference Room B on September 12 and 19. To sign up, contact Tomas Reyes at extension 418. Attendance is not mandatory, but it is strongly encouraged.', 'NONE', '["It requires a receipt for every expense.", "It is slower to repay employees.", "It was introduced on September 1.", "It can be used only on a mobile phone."]', 'B', 1, '[추론] 정답 (B). 새 방식은 20일까지 내면 그달 급여와 함께 돌려받는데 "under the current system, employees often wait six weeks or longer"라고 했으므로 지금 방식이 더 느리다. 오답: (A) 지금은 25달러 이상일 때만 영수증이 필요하다. (C) 9월 1일은 메모 날짜다. (D) 휴대전화 앱은 새 방식이고 지금은 종이 양식이다.', 156),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is NOT mentioned as changing on October 1?', 'Questions 155-157 refer to the following memo.

MEMO

To: All Staff, Pellam & Rowe Consulting
From: Anita Kowalczyk, Finance Director
Date: September 1
Subject: Changes to expense reimbursement

Beginning October 1, all expense claims must be submitted through the Claimwise mobile application. Paper claim forms will no longer be accepted after September 30.

With Claimwise, you simply photograph each receipt and select the related client project. Claims submitted by the 20th of a month will be reimbursed with that month''s salary payment; under the current system, employees often wait six weeks or longer.

Two rules are also changing on October 1. First, receipts will be required for every expense of $15 or more, down from $25. Second, any meal with clients that costs more than $200 must be approved by a department head before the meal takes place. The daily meal allowance for business trips remains $60.

Training sessions on the application will be held in Conference Room B on September 12 and 19. To sign up, contact Tomas Reyes at extension 418. Attendance is not mandatory, but it is strongly encouraged.', 'NONE', '["The amount allowed for meals during travel", "The way in which claims are submitted", "The expense level that calls for a receipt", "The approval needed for costly client meals"]', 'A', 1, '[사실 확인(NOT)] 정답 (A). "The daily meal allowance for business trips remains $60"이므로 출장 식비 한도는 바뀌지 않는다. 오답: (B) 종이 양식에서 Claimwise 앱 제출로 바뀐다. (C) 영수증 기준이 25달러에서 15달러로 내려간다. (D) 200달러가 넘는 고객 식사는 부서장의 사전 승인이 필요해진다.', 157),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'Why did Ms. Solheim write to Mr. Achterberg?', 'Questions 158-160 refer to the following letter.

Tennby Museum of Glass
40 Kiln Street, Tennby

April 14

Mr. Desmond Achterberg
212 Larch Avenue
Marlowe Springs

Dear Mr. Achterberg,

On behalf of the museum''s board, thank you for your generous gift of $500 to the Restoration Fund. Contributions like yours make it possible to repair the stained-glass panels that will be the centerpiece of our autumn exhibition, Light Through the Ages.

As a donor at the Patron level, you are entitled to an annual membership at no cost. Your membership card is enclosed. It provides unlimited admission for two adults, a 15 percent discount in the museum shop, and invitations to members-only previews. The first of these, a preview of Light Through the Ages, will take place on September 18, two days before the exhibition opens to the public.

We will mail a receipt for your donation in January for your tax records. If your employer matches charitable gifts, please let us know, and we will supply the necessary paperwork.

With gratitude,

Ingrid Solheim
Director of Development', 'NONE', '["To ask him to renew his membership", "To invite him to join the museum''s board", "To acknowledge a financial contribution", "To request documents for tax purposes"]', 'C', 1, '[주제·목적] 정답 (C). "thank you for your generous gift of $500 to the Restoration Fund"를 ''금전 기부에 감사를 표하다''로 바꿔 말했다. 오답: (A) 회원권은 기부자에게 새로 무료 제공되는 것이지 갱신 요청이 아니다. (B) 이사회를 대신해 쓴 편지일 뿐 이사회 가입 초대가 아니다. (D) 세금용 영수증은 박물관이 보내 주는 것이다.', 158),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'According to the letter, what will the Restoration Fund be used for?', 'Questions 158-160 refer to the following letter.

Tennby Museum of Glass
40 Kiln Street, Tennby

April 14

Mr. Desmond Achterberg
212 Larch Avenue
Marlowe Springs

Dear Mr. Achterberg,

On behalf of the museum''s board, thank you for your generous gift of $500 to the Restoration Fund. Contributions like yours make it possible to repair the stained-glass panels that will be the centerpiece of our autumn exhibition, Light Through the Ages.

As a donor at the Patron level, you are entitled to an annual membership at no cost. Your membership card is enclosed. It provides unlimited admission for two adults, a 15 percent discount in the museum shop, and invitations to members-only previews. The first of these, a preview of Light Through the Ages, will take place on September 18, two days before the exhibition opens to the public.

We will mail a receipt for your donation in January for your tax records. If your employer matches charitable gifts, please let us know, and we will supply the necessary paperwork.

With gratitude,

Ingrid Solheim
Director of Development', 'NONE', '["Repairing items to be shown in an exhibition", "Building a larger shop for the museum", "Printing cards for new members", "Holding previews for local residents"]', 'A', 1, '[세부 사항] 정답 (A). "make it possible to repair the stained-glass panels that will be the centerpiece of our autumn exhibition"이 근거다. 오답: (B) 상점은 할인 혜택으로만 언급된다. (C) 회원 카드는 동봉물이지 기금의 용도가 아니다. (D) 사전 관람 행사는 회원 혜택이지 기금 용도가 아니다.', 159),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What was sent with the letter?', 'Questions 158-160 refer to the following letter.

Tennby Museum of Glass
40 Kiln Street, Tennby

April 14

Mr. Desmond Achterberg
212 Larch Avenue
Marlowe Springs

Dear Mr. Achterberg,

On behalf of the museum''s board, thank you for your generous gift of $500 to the Restoration Fund. Contributions like yours make it possible to repair the stained-glass panels that will be the centerpiece of our autumn exhibition, Light Through the Ages.

As a donor at the Patron level, you are entitled to an annual membership at no cost. Your membership card is enclosed. It provides unlimited admission for two adults, a 15 percent discount in the museum shop, and invitations to members-only previews. The first of these, a preview of Light Through the Ages, will take place on September 18, two days before the exhibition opens to the public.

We will mail a receipt for your donation in January for your tax records. If your employer matches charitable gifts, please let us know, and we will supply the necessary paperwork.

With gratitude,

Ingrid Solheim
Director of Development', 'NONE', '["A card that allows free entry", "A receipt for a donation", "A form for matching gifts", "A ticket to an exhibition preview"]', 'A', 1, '[세부 사항] 정답 (A). "Your membership card is enclosed. It provides unlimited admission for two adults"에서 무료 입장이 되는 카드가 동봉되었음을 알 수 있다. 오답: (B) 영수증은 1월에 따로 우편으로 보낸다. (C) 매칭 기부 서류는 알려 주면 그때 제공한다. (D) 사전 관람은 초대장을 받게 된다는 혜택 설명일 뿐 표가 들어 있지는 않다.', 160);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is the article mainly about?', 'Questions 161-163 refer to the following article.

MARLOWE SPRINGS (May 6) — Fennimore Cycle Works, a family-run bicycle repair shop on Quarry Road, has been named Small Business of the Year by the Marlowe Springs Chamber of Commerce. — [1] —.

Owner Lucia Fennimore opened the shop eleven years ago with a single employee. Today it has a staff of fourteen and services more than 6,000 bicycles a year. — [2] —. The judges praised the shop''s free monthly repair classes, which have taught basic maintenance to hundreds of residents.

"We never set out to win prizes," Ms. Fennimore said. "We simply wanted people to keep riding." — [3] —.

The award comes with a $5,000 grant, which Ms. Fennimore plans to spend on a mobile repair van. The van will visit neighborhoods on the east side of the city, where there is currently no bicycle shop. — [4] —.

The award will be presented at the chamber''s annual dinner on May 30 at the Granite Hall Hotel.', 'NONE', '["The opening of a new bicycle shop", "A change in the ownership of a business", "A local company receiving an honor", "A hotel that will host a cycling event"]', 'C', 1, '[주제·목적] 정답 (C). 첫 문장 "has been named Small Business of the Year"가 글의 중심이며 ''지역 업체가 상을 받는다''로 요약된다. 오답: (A) 가게는 11년 전에 열었다. (B) 주인이 바뀐다는 내용은 없다. (D) 호텔은 시상식 저녁 행사 장소일 뿐이다.', 161),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about Fennimore Cycle Works?', 'Questions 161-163 refer to the following article.

MARLOWE SPRINGS (May 6) — Fennimore Cycle Works, a family-run bicycle repair shop on Quarry Road, has been named Small Business of the Year by the Marlowe Springs Chamber of Commerce. — [1] —.

Owner Lucia Fennimore opened the shop eleven years ago with a single employee. Today it has a staff of fourteen and services more than 6,000 bicycles a year. — [2] —. The judges praised the shop''s free monthly repair classes, which have taught basic maintenance to hundreds of residents.

"We never set out to win prizes," Ms. Fennimore said. "We simply wanted people to keep riding." — [3] —.

The award comes with a $5,000 grant, which Ms. Fennimore plans to spend on a mobile repair van. The van will visit neighborhoods on the east side of the city, where there is currently no bicycle shop. — [4] —.

The award will be presented at the chamber''s annual dinner on May 30 at the Granite Hall Hotel.', 'NONE', '["It has been in business for over two decades.", "It sells more than 6,000 bicycles each year.", "It is managed by the Chamber of Commerce.", "It offers instruction to residents at no cost."]', 'D', 1, '[사실 확인] 정답 (D). "the shop''s free monthly repair classes, which have taught basic maintenance to hundreds of residents"를 ''주민에게 무료로 교육을 제공한다''로 바꿔 말했다. 오답: (A) 문을 연 지 11년이다. (B) 6,000대는 판매가 아니라 수리(services)한 수다. (C) 상공회의소는 상을 주는 기관이다.', 162),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'In which of the positions marked [1], [2], [3], and [4] does the following sentence best belong? "She expects it to be on the road by the end of the summer."', 'Questions 161-163 refer to the following article.

MARLOWE SPRINGS (May 6) — Fennimore Cycle Works, a family-run bicycle repair shop on Quarry Road, has been named Small Business of the Year by the Marlowe Springs Chamber of Commerce. — [1] —.

Owner Lucia Fennimore opened the shop eleven years ago with a single employee. Today it has a staff of fourteen and services more than 6,000 bicycles a year. — [2] —. The judges praised the shop''s free monthly repair classes, which have taught basic maintenance to hundreds of residents.

"We never set out to win prizes," Ms. Fennimore said. "We simply wanted people to keep riding." — [3] —.

The award comes with a $5,000 grant, which Ms. Fennimore plans to spend on a mobile repair van. The van will visit neighborhoods on the east side of the city, where there is currently no bicycle shop. — [4] —.

The award will be presented at the chamber''s annual dinner on May 30 at the Granite Hall Hotel.', 'NONE', '["[1]", "[2]", "[3]", "[4]"]', 'D', 1, '[문장 삽입] 정답 (D). 삽입 문장의 it은 바로 앞의 "a mobile repair van"을 가리키고, on the road(운행에 들어가다)는 밴이 동쪽 동네를 돌 것이라는 설명 뒤에 이어져야 자연스럽다. 오답: (A) [1] 앞에는 it이 가리킬 차량이 없다. (B) [2] 앞은 직원 수와 수리 대수 이야기다. (C) [3] 앞은 수상 소감 인용이라 it이 가리킬 대상이 없다.', 163),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What led to the changes in the policy?', 'Questions 164-167 refer to the following e-mail.

To: All Employees
From: Gareth Lindqvist, Human Resources
Date: January 8
Subject: Updated remote work policy

Dear colleagues,

After reviewing the results of last autumn''s staff survey, the management team has approved several changes to our remote work policy. They take effect on February 1.

Employees may now work from home up to three days per week instead of two. However, every team must be in the office on its designated "anchor day" so that meetings can be held in person. Department heads will announce their anchor days by January 20.

Because fewer people will be in the building on any given day, individual desks on the fourth floor will be converted to shared workstations. These must be reserved through the DeskPoint system no later than 5:00 P.M. on the previous business day. Lockers will be provided for personal items.

The company will continue to cover the cost of a monitor and keyboard for home use. In addition, employees who work remotely at least two days a week will be eligible for a monthly internet allowance of $40. To claim it, submit a copy of your internet bill with your regular expense report.

Please note that requests to work remotely full time will still be handled case by case and require the approval of a vice president.

Questions can be addressed to me directly.

Gareth Lindqvist', 'NONE', '["A decision to move to a smaller building", "Feedback collected from employees", "A request made by department heads", "An increase in the cost of internet service"]', 'B', 1, '[세부 사항] 정답 (B). "After reviewing the results of last autumn''s staff survey"를 ''직원에게서 모은 의견''으로 바꿔 말했다. 오답: (A) 건물 이전 이야기는 없고 4층 좌석을 공유석으로 바꿀 뿐이다. (C) 부서장은 출근일을 알리는 역할이다. (D) 인터넷 보조금은 새 혜택이지 변경의 원인이 아니다.', 164),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What will department heads do by January 20?', 'Questions 164-167 refer to the following e-mail.

To: All Employees
From: Gareth Lindqvist, Human Resources
Date: January 8
Subject: Updated remote work policy

Dear colleagues,

After reviewing the results of last autumn''s staff survey, the management team has approved several changes to our remote work policy. They take effect on February 1.

Employees may now work from home up to three days per week instead of two. However, every team must be in the office on its designated "anchor day" so that meetings can be held in person. Department heads will announce their anchor days by January 20.

Because fewer people will be in the building on any given day, individual desks on the fourth floor will be converted to shared workstations. These must be reserved through the DeskPoint system no later than 5:00 P.M. on the previous business day. Lockers will be provided for personal items.

The company will continue to cover the cost of a monitor and keyboard for home use. In addition, employees who work remotely at least two days a week will be eligible for a monthly internet allowance of $40. To claim it, submit a copy of your internet bill with your regular expense report.

Please note that requests to work remotely full time will still be handled case by case and require the approval of a vice president.

Questions can be addressed to me directly.

Gareth Lindqvist', 'NONE', '["Approve requests for lockers", "Reserve workstations for their teams", "Tell staff which day they must come in", "Submit survey results to management"]', 'C', 1, '[세부 사항] 정답 (C). "Department heads will announce their anchor days by January 20"이고 anchor day는 팀 전원이 사무실에 나와야 하는 날이다. 오답: (A) 사물함은 회사가 제공한다고만 했다. (B) 좌석 예약은 직원이 DeskPoint로 직접 한다. (D) 설문은 지난가을에 이미 끝났다.', 165),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is NOT mentioned as something the company will provide?', 'Questions 164-167 refer to the following e-mail.

To: All Employees
From: Gareth Lindqvist, Human Resources
Date: January 8
Subject: Updated remote work policy

Dear colleagues,

After reviewing the results of last autumn''s staff survey, the management team has approved several changes to our remote work policy. They take effect on February 1.

Employees may now work from home up to three days per week instead of two. However, every team must be in the office on its designated "anchor day" so that meetings can be held in person. Department heads will announce their anchor days by January 20.

Because fewer people will be in the building on any given day, individual desks on the fourth floor will be converted to shared workstations. These must be reserved through the DeskPoint system no later than 5:00 P.M. on the previous business day. Lockers will be provided for personal items.

The company will continue to cover the cost of a monitor and keyboard for home use. In addition, employees who work remotely at least two days a week will be eligible for a monthly internet allowance of $40. To claim it, submit a copy of your internet bill with your regular expense report.

Please note that requests to work remotely full time will still be handled case by case and require the approval of a vice president.

Questions can be addressed to me directly.

Gareth Lindqvist', 'NONE', '["Storage space for belongings", "Equipment for a home office", "Money toward internet service", "Transportation to the office"]', 'D', 1, '[사실 확인(NOT)] 정답 (D). 출퇴근 교통편은 어디에도 나오지 않는다. 오답: (A) "Lockers will be provided for personal items". (B) "a monitor and keyboard for home use". (C) "a monthly internet allowance of $40".', 166),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'The word "cover" in paragraph 4, line 1, is closest in meaning to', 'Questions 164-167 refer to the following e-mail.

To: All Employees
From: Gareth Lindqvist, Human Resources
Date: January 8
Subject: Updated remote work policy

Dear colleagues,

After reviewing the results of last autumn''s staff survey, the management team has approved several changes to our remote work policy. They take effect on February 1.

Employees may now work from home up to three days per week instead of two. However, every team must be in the office on its designated "anchor day" so that meetings can be held in person. Department heads will announce their anchor days by January 20.

Because fewer people will be in the building on any given day, individual desks on the fourth floor will be converted to shared workstations. These must be reserved through the DeskPoint system no later than 5:00 P.M. on the previous business day. Lockers will be provided for personal items.

The company will continue to cover the cost of a monitor and keyboard for home use. In addition, employees who work remotely at least two days a week will be eligible for a monthly internet allowance of $40. To claim it, submit a copy of your internet bill with your regular expense report.

Please note that requests to work remotely full time will still be handled case by case and require the approval of a vice president.

Questions can be addressed to me directly.

Gareth Lindqvist', 'NONE', '["protect", "pay", "report", "hide"]', 'B', 1, '[동의어] 정답 (B). "cover the cost of a monitor and keyboard"는 비용을 회사가 부담한다는 뜻이므로 pay가 가장 가깝다. 오답: (A) 보호하다, (D) 가리다는 cover의 다른 뜻이고, (C) 보도·보고하다는 뜻도 the cost를 목적어로 한 이 문장에는 맞지 않는다.', 167),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is the article mainly about?', 'Questions 168-171 refer to the following article.

Tennby Roaster to Open Second Site

TENNBY (August 22) — Harrowgate Coffee Roasters announced yesterday that it will open a production facility in the Dockside Business Park next spring. The 2,400-square-meter building, formerly a furniture warehouse, will triple the company''s roasting capacity. The company expects to spend about $1.8 million on equipment and renovations.

— [1] —. Founded in 2015 by former schoolteacher Owen Harrowgate, the company began by supplying beans to three cafés in Tennby. It now delivers to more than 200 restaurants, hotels, and grocery stores across the region. "Our Mill Lane site has been running seven days a week for two years, and we still have to turn down new orders," Mr. Harrowgate said. — [2] —.

The new facility will include a training room where café owners can attend classes on brewing techniques. — [3] —. The company also plans to hire about 25 workers, mostly for packaging and delivery roles, and will begin accepting applications in January.

The Mill Lane roastery will remain open and will focus on small-batch specialty coffees. — [4] —. City officials welcomed the announcement, noting that the Dockside Business Park has had several vacant buildings since 2021. "Seeing one of them come back to life is good news for the whole district," said Deputy Mayor Carla Winslow.', 'NONE', '["A change in the ownership of a coffee company", "The closing of a furniture warehouse", "A local business''s plan to grow", "A new café opening in a business park"]', 'C', 1, '[주제·목적] 정답 (C). "it will open a production facility ... will triple the company''s roasting capacity"에서 지역 업체의 확장 계획이 주제임을 알 수 있다. 오답: (A) 소유주 변경은 없다. (B) 가구 창고는 새 건물의 예전 용도일 뿐이다. (D) 새로 여는 것은 카페가 아니라 생산 시설이다.', 168),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the Mill Lane site?', 'Questions 168-171 refer to the following article.

Tennby Roaster to Open Second Site

TENNBY (August 22) — Harrowgate Coffee Roasters announced yesterday that it will open a production facility in the Dockside Business Park next spring. The 2,400-square-meter building, formerly a furniture warehouse, will triple the company''s roasting capacity. The company expects to spend about $1.8 million on equipment and renovations.

— [1] —. Founded in 2015 by former schoolteacher Owen Harrowgate, the company began by supplying beans to three cafés in Tennby. It now delivers to more than 200 restaurants, hotels, and grocery stores across the region. "Our Mill Lane site has been running seven days a week for two years, and we still have to turn down new orders," Mr. Harrowgate said. — [2] —.

The new facility will include a training room where café owners can attend classes on brewing techniques. — [3] —. The company also plans to hire about 25 workers, mostly for packaging and delivery roles, and will begin accepting applications in January.

The Mill Lane roastery will remain open and will focus on small-batch specialty coffees. — [4] —. City officials welcomed the announcement, noting that the Dockside Business Park has had several vacant buildings since 2021. "Seeing one of them come back to life is good news for the whole district," said Deputy Mayor Carla Winslow.', 'NONE', '["It will close when the new facility opens.", "It cannot produce enough to meet demand.", "It was formerly used to store furniture.", "It is located in the Dockside Business Park."]', 'B', 1, '[추론] 정답 (B). "running seven days a week for two years, and we still have to turn down new orders"에서 지금 시설로는 주문을 다 감당하지 못한다는 것을 알 수 있다. 오답: (A) "will remain open"이라고 했다. (C) 가구 창고였던 곳은 새 건물이다. (D) Dockside Business Park는 새 시설의 위치다.', 169),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'According to the article, what will happen in January?', 'Questions 168-171 refer to the following article.

Tennby Roaster to Open Second Site

TENNBY (August 22) — Harrowgate Coffee Roasters announced yesterday that it will open a production facility in the Dockside Business Park next spring. The 2,400-square-meter building, formerly a furniture warehouse, will triple the company''s roasting capacity. The company expects to spend about $1.8 million on equipment and renovations.

— [1] —. Founded in 2015 by former schoolteacher Owen Harrowgate, the company began by supplying beans to three cafés in Tennby. It now delivers to more than 200 restaurants, hotels, and grocery stores across the region. "Our Mill Lane site has been running seven days a week for two years, and we still have to turn down new orders," Mr. Harrowgate said. — [2] —.

The new facility will include a training room where café owners can attend classes on brewing techniques. — [3] —. The company also plans to hire about 25 workers, mostly for packaging and delivery roles, and will begin accepting applications in January.

The Mill Lane roastery will remain open and will focus on small-batch specialty coffees. — [4] —. City officials welcomed the announcement, noting that the Dockside Business Park has had several vacant buildings since 2021. "Seeing one of them come back to life is good news for the whole district," said Deputy Mayor Carla Winslow.', 'NONE', '["The company will start receiving job applications.", "The new facility will begin roasting coffee.", "Brewing classes will be opened to the public.", "City officials will inspect a vacant building."]', 'A', 1, '[세부 사항] 정답 (A). "will begin accepting applications in January"가 근거다. 오답: (B) 새 시설은 내년 봄에 문을 연다. (C) 수업은 카페 주인 대상이며 1월이라는 말도 없다. (D) 시 관계자는 발표를 환영했을 뿐 점검 이야기는 없다.', 170),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'In which of the positions marked [1], [2], [3], and [4] does the following sentence best belong? "These hands-on sessions will be offered free of charge to the company''s existing customers."', 'Questions 168-171 refer to the following article.

Tennby Roaster to Open Second Site

TENNBY (August 22) — Harrowgate Coffee Roasters announced yesterday that it will open a production facility in the Dockside Business Park next spring. The 2,400-square-meter building, formerly a furniture warehouse, will triple the company''s roasting capacity. The company expects to spend about $1.8 million on equipment and renovations.

— [1] —. Founded in 2015 by former schoolteacher Owen Harrowgate, the company began by supplying beans to three cafés in Tennby. It now delivers to more than 200 restaurants, hotels, and grocery stores across the region. "Our Mill Lane site has been running seven days a week for two years, and we still have to turn down new orders," Mr. Harrowgate said. — [2] —.

The new facility will include a training room where café owners can attend classes on brewing techniques. — [3] —. The company also plans to hire about 25 workers, mostly for packaging and delivery roles, and will begin accepting applications in January.

The Mill Lane roastery will remain open and will focus on small-batch specialty coffees. — [4] —. City officials welcomed the announcement, noting that the Dockside Business Park has had several vacant buildings since 2021. "Seeing one of them come back to life is good news for the whole district," said Deputy Mayor Carla Winslow.', 'NONE', '["[1]", "[2]", "[3]", "[4]"]', 'C', 1, '[문장 삽입] 정답 (C). These hands-on sessions가 바로 앞 문장의 "classes on brewing techniques"를 가리키므로 [3]에 들어가야 한다. 오답: (A) [1] 앞에는 건물 면적과 생산 능력 이야기만 있다. (B) [2] 앞은 주문을 거절한다는 인용문이라 무료로 제공할 대상이 없다. (D) [4] 앞은 기존 공장의 생산 품목 이야기라 sessions가 가리킬 수업이 없다.', 171),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'Why did Ms. Whitlock contact the company?', 'Questions 172-175 refer to the following online chat discussion.

Hana Whitlock (2:04 P.M.)
Hello. I ordered a Trailmate rain jacket in size medium, order number 58117. It arrived yesterday, but it''s too tight in the shoulders. Can I exchange it for a large?

Rafael Duarte (2:06 P.M.)
Hello, Ms. Whitlock. I''d be happy to help. Exchanges are free within 30 days as long as the tags are still attached. Is that the case?

Hana Whitlock (2:07 P.M.)
Yes, I only tried it on once.

Rafael Duarte (2:09 P.M.)
Good. Let me bring in a colleague from our warehouse to check our stock. Mei, do we have the Trailmate in large, forest green?

Mei Okafor (2:12 P.M.)
Not in forest green, I''m afraid. The next shipment is due on October 21. We do have large in navy and in gray right now.

Hana Whitlock (2:14 P.M.)
I''m leaving for a hiking trip on October 16, so I can''t wait that long. Navy will do.

Rafael Duarte (2:16 P.M.)
Understood. I''ve just e-mailed you a prepaid return label. Drop the medium jacket off at any Parcelway location, and we''ll ship the navy one as soon as the package is scanned there.

Hana Whitlock (2:17 P.M.)
Will it get here in time?

Mei Okafor (2:19 P.M.)
Standard delivery takes three to five business days. If you drop the jacket off by tomorrow, October 8, you should be fine. I can also upgrade you to express shipping at no charge, just to be safe.

Hana Whitlock (2:20 P.M.)
Please do. Thank you both.', 'NONE', '["To report that an item arrived damaged", "To request a different size of a product", "To ask why a delivery is late", "To cancel an order she placed"]', 'B', 1, '[주제·목적] 정답 (B). "it''s too tight in the shoulders. Can I exchange it for a large?"에서 다른 치수로 바꾸려는 것임을 알 수 있다. 오답: (A) 파손이 아니라 치수 문제다. (C) 재킷은 어제 이미 도착했다. (D) 취소가 아니라 교환을 원한다.', 172),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What does Ms. Okafor say about the forest green jacket in large?', 'Questions 172-175 refer to the following online chat discussion.

Hana Whitlock (2:04 P.M.)
Hello. I ordered a Trailmate rain jacket in size medium, order number 58117. It arrived yesterday, but it''s too tight in the shoulders. Can I exchange it for a large?

Rafael Duarte (2:06 P.M.)
Hello, Ms. Whitlock. I''d be happy to help. Exchanges are free within 30 days as long as the tags are still attached. Is that the case?

Hana Whitlock (2:07 P.M.)
Yes, I only tried it on once.

Rafael Duarte (2:09 P.M.)
Good. Let me bring in a colleague from our warehouse to check our stock. Mei, do we have the Trailmate in large, forest green?

Mei Okafor (2:12 P.M.)
Not in forest green, I''m afraid. The next shipment is due on October 21. We do have large in navy and in gray right now.

Hana Whitlock (2:14 P.M.)
I''m leaving for a hiking trip on October 16, so I can''t wait that long. Navy will do.

Rafael Duarte (2:16 P.M.)
Understood. I''ve just e-mailed you a prepaid return label. Drop the medium jacket off at any Parcelway location, and we''ll ship the navy one as soon as the package is scanned there.

Hana Whitlock (2:17 P.M.)
Will it get here in time?

Mei Okafor (2:19 P.M.)
Standard delivery takes three to five business days. If you drop the jacket off by tomorrow, October 8, you should be fine. I can also upgrade you to express shipping at no charge, just to be safe.

Hana Whitlock (2:20 P.M.)
Please do. Thank you both.', 'NONE', '["It has been discontinued.", "It costs more than the navy one.", "It is not currently in stock.", "It will arrive on October 16."]', 'C', 1, '[세부 사항] 정답 (C). "Not in forest green, I''m afraid. The next shipment is due on October 21"에서 지금은 재고가 없다는 것을 알 수 있다. 오답: (A) 다음 입고가 예정되어 있으므로 단종이 아니다. (B) 가격 차이 언급은 없다. (D) 10월 16일은 Ms. Whitlock의 출발일이고 입고는 21일이다.', 173),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'At 2:14 P.M., what does Ms. Whitlock most likely mean when she writes, "Navy will do"?', 'Questions 172-175 refer to the following online chat discussion.

Hana Whitlock (2:04 P.M.)
Hello. I ordered a Trailmate rain jacket in size medium, order number 58117. It arrived yesterday, but it''s too tight in the shoulders. Can I exchange it for a large?

Rafael Duarte (2:06 P.M.)
Hello, Ms. Whitlock. I''d be happy to help. Exchanges are free within 30 days as long as the tags are still attached. Is that the case?

Hana Whitlock (2:07 P.M.)
Yes, I only tried it on once.

Rafael Duarte (2:09 P.M.)
Good. Let me bring in a colleague from our warehouse to check our stock. Mei, do we have the Trailmate in large, forest green?

Mei Okafor (2:12 P.M.)
Not in forest green, I''m afraid. The next shipment is due on October 21. We do have large in navy and in gray right now.

Hana Whitlock (2:14 P.M.)
I''m leaving for a hiking trip on October 16, so I can''t wait that long. Navy will do.

Rafael Duarte (2:16 P.M.)
Understood. I''ve just e-mailed you a prepaid return label. Drop the medium jacket off at any Parcelway location, and we''ll ship the navy one as soon as the package is scanned there.

Hana Whitlock (2:17 P.M.)
Will it get here in time?

Mei Okafor (2:19 P.M.)
Standard delivery takes three to five business days. If you drop the jacket off by tomorrow, October 8, you should be fine. I can also upgrade you to express shipping at no charge, just to be safe.

Hana Whitlock (2:20 P.M.)
Please do. Thank you both.', 'NONE', '["She wants to see a photo of the navy jacket.", "She would rather receive a refund.", "She believes navy is the most popular color.", "She is willing to accept a different color."]', 'D', 1, '[의도 파악] 정답 (D). 원하던 forest green은 21일에야 들어오는데 16일에 떠나야 해서 기다릴 수 없다고 한 뒤 한 말이므로, 남색이면 괜찮다 곧 다른 색을 받겠다는 뜻이다. 오답: (A) 사진을 요청하지 않았다. (B) 환불이 아니라 교환을 계속 진행한다. (C) 인기 여부는 언급되지 않았다.', 174),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What will Ms. Whitlock most likely do next?', 'Questions 172-175 refer to the following online chat discussion.

Hana Whitlock (2:04 P.M.)
Hello. I ordered a Trailmate rain jacket in size medium, order number 58117. It arrived yesterday, but it''s too tight in the shoulders. Can I exchange it for a large?

Rafael Duarte (2:06 P.M.)
Hello, Ms. Whitlock. I''d be happy to help. Exchanges are free within 30 days as long as the tags are still attached. Is that the case?

Hana Whitlock (2:07 P.M.)
Yes, I only tried it on once.

Rafael Duarte (2:09 P.M.)
Good. Let me bring in a colleague from our warehouse to check our stock. Mei, do we have the Trailmate in large, forest green?

Mei Okafor (2:12 P.M.)
Not in forest green, I''m afraid. The next shipment is due on October 21. We do have large in navy and in gray right now.

Hana Whitlock (2:14 P.M.)
I''m leaving for a hiking trip on October 16, so I can''t wait that long. Navy will do.

Rafael Duarte (2:16 P.M.)
Understood. I''ve just e-mailed you a prepaid return label. Drop the medium jacket off at any Parcelway location, and we''ll ship the navy one as soon as the package is scanned there.

Hana Whitlock (2:17 P.M.)
Will it get here in time?

Mei Okafor (2:19 P.M.)
Standard delivery takes three to five business days. If you drop the jacket off by tomorrow, October 8, you should be fine. I can also upgrade you to express shipping at no charge, just to be safe.

Hana Whitlock (2:20 P.M.)
Please do. Thank you both.', 'NONE', '["Take a package to a drop-off location", "Pay a fee for faster shipping", "Place a new order on a Web site", "Pick up a jacket at a warehouse"]', 'A', 1, '[추론] 정답 (A). "Drop the medium jacket off at any Parcelway location"과 "If you drop the jacket off by tomorrow"에서 반품 상자를 접수처에 맡기는 것이 다음 할 일임을 알 수 있다. 오답: (B) 빠른 배송은 무료로 올려 준다. (C) 새 주문 없이 반품이 스캔되면 남색 재킷을 보내 준다. (D) 창고는 직원이 재고를 확인한 곳일 뿐 방문 수령 이야기는 없다.', 175),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'According to the itinerary, what is included in the price of the tour?', 'Questions 176-180 refer to the following itinerary and e-mail.

[Itinerary]
Skyline & Shore Travel — Marlowe Springs
Coastal Heritage Tour: 4 days / 3 nights

Departure dates: May 14, June 11, July 9
Price: $780 per person (double room); single-room supplement $120

Day 1 — 8:00 A.M. coach departs from Marlowe Springs Central Station; afternoon guided walk in the old town of Tennby; dinner at the hotel
Day 2 — Morning ferry to Sable Island; lighthouse tour; free afternoon
Day 3 — Visit to the Wrenfield glassworks; vineyard tour; evening concert (optional, $35)
Day 4 — Morning at the Tennby harbor market; coach returns to Marlowe Springs by 5:00 P.M.

Included: coach travel, three nights at the Harbor Crest Hotel, breakfast daily, dinner on Day 1, all entrance fees
Not included: lunches, travel insurance

A deposit of $150 per person holds your place. The balance is due 21 days before departure. Cancellations made after the balance due date are subject to a $150 fee.

────────────────────

[E-mail]
To: Nora Fitzwilliam
From: Callum Ibarra, Skyline & Shore Travel
Date: May 29
Subject: Changes to your June 11 tour

Dear Ms. Fitzwilliam,

Thank you for booking the Coastal Heritage Tour for two people. I am writing to let you know about two changes.

First, the ferry operator has suspended service to Sable Island until the end of June while its dock is repaired. In place of the island visit, your group will take a guided boat cruise along the Tennby cliffs, followed by the free afternoon as planned. To make up for the change, we will provide the optional evening concert to all travelers at no charge.

Second, because of a road race in the city center, the coach will leave from the north entrance of Riverside Park rather than from Central Station. The departure time has not changed.

If these changes do not suit you, you may transfer to the July 9 departure or cancel for a full refund, with no fee, provided that you notify us by June 4. Otherwise, no action is required.

Sincerely,
Callum Ibarra', 'NONE', '["Insurance for travelers", "A midday meal each day", "Admission to the sites visited", "A private room at the hotel"]', 'C', 1, '[세부 사항] 정답 (C). Included 항목의 "all entrance fees"를 ''방문지 입장료''로 바꿔 말했다. 오답: (A) 여행 보험과 (B) 점심은 Not included에 있다. (D) 가격은 2인실 기준이고 1인실은 120달러를 더 낸다.', 176),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'In the itinerary, the word "holds" in the last paragraph, line 1, is closest in meaning to', 'Questions 176-180 refer to the following itinerary and e-mail.

[Itinerary]
Skyline & Shore Travel — Marlowe Springs
Coastal Heritage Tour: 4 days / 3 nights

Departure dates: May 14, June 11, July 9
Price: $780 per person (double room); single-room supplement $120

Day 1 — 8:00 A.M. coach departs from Marlowe Springs Central Station; afternoon guided walk in the old town of Tennby; dinner at the hotel
Day 2 — Morning ferry to Sable Island; lighthouse tour; free afternoon
Day 3 — Visit to the Wrenfield glassworks; vineyard tour; evening concert (optional, $35)
Day 4 — Morning at the Tennby harbor market; coach returns to Marlowe Springs by 5:00 P.M.

Included: coach travel, three nights at the Harbor Crest Hotel, breakfast daily, dinner on Day 1, all entrance fees
Not included: lunches, travel insurance

A deposit of $150 per person holds your place. The balance is due 21 days before departure. Cancellations made after the balance due date are subject to a $150 fee.

────────────────────

[E-mail]
To: Nora Fitzwilliam
From: Callum Ibarra, Skyline & Shore Travel
Date: May 29
Subject: Changes to your June 11 tour

Dear Ms. Fitzwilliam,

Thank you for booking the Coastal Heritage Tour for two people. I am writing to let you know about two changes.

First, the ferry operator has suspended service to Sable Island until the end of June while its dock is repaired. In place of the island visit, your group will take a guided boat cruise along the Tennby cliffs, followed by the free afternoon as planned. To make up for the change, we will provide the optional evening concert to all travelers at no charge.

Second, because of a road race in the city center, the coach will leave from the north entrance of Riverside Park rather than from Central Station. The departure time has not changed.

If these changes do not suit you, you may transfer to the July 9 departure or cancel for a full refund, with no fee, provided that you notify us by June 4. Otherwise, no action is required.

Sincerely,
Callum Ibarra', 'NONE', '["reserves", "carries", "contains", "delays"]', 'A', 1, '[동의어] 정답 (A). "A deposit of $150 per person holds your place"는 예약금이 자리를 확보해 둔다는 뜻이므로 reserves가 가장 가깝다. 오답: (B) 들어 옮기다, (C) 담고 있다, (D) 늦추다는 your place를 목적어로 한 이 문맥에 맞지 않는다.', 177),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'According to the e-mail, why is one activity being replaced?', 'Questions 176-180 refer to the following itinerary and e-mail.

[Itinerary]
Skyline & Shore Travel — Marlowe Springs
Coastal Heritage Tour: 4 days / 3 nights

Departure dates: May 14, June 11, July 9
Price: $780 per person (double room); single-room supplement $120

Day 1 — 8:00 A.M. coach departs from Marlowe Springs Central Station; afternoon guided walk in the old town of Tennby; dinner at the hotel
Day 2 — Morning ferry to Sable Island; lighthouse tour; free afternoon
Day 3 — Visit to the Wrenfield glassworks; vineyard tour; evening concert (optional, $35)
Day 4 — Morning at the Tennby harbor market; coach returns to Marlowe Springs by 5:00 P.M.

Included: coach travel, three nights at the Harbor Crest Hotel, breakfast daily, dinner on Day 1, all entrance fees
Not included: lunches, travel insurance

A deposit of $150 per person holds your place. The balance is due 21 days before departure. Cancellations made after the balance due date are subject to a $150 fee.

────────────────────

[E-mail]
To: Nora Fitzwilliam
From: Callum Ibarra, Skyline & Shore Travel
Date: May 29
Subject: Changes to your June 11 tour

Dear Ms. Fitzwilliam,

Thank you for booking the Coastal Heritage Tour for two people. I am writing to let you know about two changes.

First, the ferry operator has suspended service to Sable Island until the end of June while its dock is repaired. In place of the island visit, your group will take a guided boat cruise along the Tennby cliffs, followed by the free afternoon as planned. To make up for the change, we will provide the optional evening concert to all travelers at no charge.

Second, because of a road race in the city center, the coach will leave from the north entrance of Riverside Park rather than from Central Station. The departure time has not changed.

If these changes do not suit you, you may transfer to the July 9 departure or cancel for a full refund, with no fee, provided that you notify us by June 4. Otherwise, no action is required.

Sincerely,
Callum Ibarra', 'NONE', '["Too few travelers signed up for it.", "A lighthouse has been closed to visitors.", "A road race will block access to the harbor.", "A ferry is temporarily out of service."]', 'D', 1, '[세부 사항] 정답 (D). "the ferry operator has suspended service to Sable Island until the end of June"을 ''여객선이 일시적으로 운항하지 않는다''로 바꿔 말했다. 오답: (A) 신청 인원 이야기는 없다. (B) 수리하는 것은 등대가 아니라 선착장이다. (C) 도로 경주는 버스 출발 장소가 바뀐 이유다.', 178),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is the regular price of the activity that will now be offered free of charge?', 'Questions 176-180 refer to the following itinerary and e-mail.

[Itinerary]
Skyline & Shore Travel — Marlowe Springs
Coastal Heritage Tour: 4 days / 3 nights

Departure dates: May 14, June 11, July 9
Price: $780 per person (double room); single-room supplement $120

Day 1 — 8:00 A.M. coach departs from Marlowe Springs Central Station; afternoon guided walk in the old town of Tennby; dinner at the hotel
Day 2 — Morning ferry to Sable Island; lighthouse tour; free afternoon
Day 3 — Visit to the Wrenfield glassworks; vineyard tour; evening concert (optional, $35)
Day 4 — Morning at the Tennby harbor market; coach returns to Marlowe Springs by 5:00 P.M.

Included: coach travel, three nights at the Harbor Crest Hotel, breakfast daily, dinner on Day 1, all entrance fees
Not included: lunches, travel insurance

A deposit of $150 per person holds your place. The balance is due 21 days before departure. Cancellations made after the balance due date are subject to a $150 fee.

────────────────────

[E-mail]
To: Nora Fitzwilliam
From: Callum Ibarra, Skyline & Shore Travel
Date: May 29
Subject: Changes to your June 11 tour

Dear Ms. Fitzwilliam,

Thank you for booking the Coastal Heritage Tour for two people. I am writing to let you know about two changes.

First, the ferry operator has suspended service to Sable Island until the end of June while its dock is repaired. In place of the island visit, your group will take a guided boat cruise along the Tennby cliffs, followed by the free afternoon as planned. To make up for the change, we will provide the optional evening concert to all travelers at no charge.

Second, because of a road race in the city center, the coach will leave from the north entrance of Riverside Park rather than from Central Station. The departure time has not changed.

If these changes do not suit you, you may transfer to the July 9 departure or cancel for a full refund, with no fee, provided that you notify us by June 4. Otherwise, no action is required.

Sincerely,
Callum Ibarra', 'NONE', '["$35", "$120", "$150", "$780"]', 'A', 1, '[연계(문서1+2)] 정답 (A). 이메일의 "we will provide the optional evening concert to all travelers at no charge"와 일정표 Day 3의 "evening concert (optional, $35)"를 연결하면 35달러다. 오답: (B) 1인실 추가 요금, (C) 예약금·취소 수수료, (D) 1인 여행 요금이다.', 179),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What may Ms. Fitzwilliam do until June 4?', 'Questions 176-180 refer to the following itinerary and e-mail.

[Itinerary]
Skyline & Shore Travel — Marlowe Springs
Coastal Heritage Tour: 4 days / 3 nights

Departure dates: May 14, June 11, July 9
Price: $780 per person (double room); single-room supplement $120

Day 1 — 8:00 A.M. coach departs from Marlowe Springs Central Station; afternoon guided walk in the old town of Tennby; dinner at the hotel
Day 2 — Morning ferry to Sable Island; lighthouse tour; free afternoon
Day 3 — Visit to the Wrenfield glassworks; vineyard tour; evening concert (optional, $35)
Day 4 — Morning at the Tennby harbor market; coach returns to Marlowe Springs by 5:00 P.M.

Included: coach travel, three nights at the Harbor Crest Hotel, breakfast daily, dinner on Day 1, all entrance fees
Not included: lunches, travel insurance

A deposit of $150 per person holds your place. The balance is due 21 days before departure. Cancellations made after the balance due date are subject to a $150 fee.

────────────────────

[E-mail]
To: Nora Fitzwilliam
From: Callum Ibarra, Skyline & Shore Travel
Date: May 29
Subject: Changes to your June 11 tour

Dear Ms. Fitzwilliam,

Thank you for booking the Coastal Heritage Tour for two people. I am writing to let you know about two changes.

First, the ferry operator has suspended service to Sable Island until the end of June while its dock is repaired. In place of the island visit, your group will take a guided boat cruise along the Tennby cliffs, followed by the free afternoon as planned. To make up for the change, we will provide the optional evening concert to all travelers at no charge.

Second, because of a road race in the city center, the coach will leave from the north entrance of Riverside Park rather than from Central Station. The departure time has not changed.

If these changes do not suit you, you may transfer to the July 9 departure or cancel for a full refund, with no fee, provided that you notify us by June 4. Otherwise, no action is required.

Sincerely,
Callum Ibarra', 'NONE', '["Pay the remaining balance for her tour", "Request a seat on an earlier coach", "Switch to a tour that leaves at a later date", "Add another traveler to her booking"]', 'C', 1, '[세부 사항] 정답 (C). "you may transfer to the July 9 departure ... provided that you notify us by June 4"를 ''더 늦게 출발하는 투어로 옮기다''로 바꿔 말했다. 오답: (A) 잔금 이야기는 이메일에 없다. (B) 출발 시각은 그대로이며 더 이른 버스는 없다. (D) 인원 추가는 언급되지 않았다.', 180);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What does Mr. Okonjo mention about himself in the survey?', 'Questions 181-185 refer to the following survey and e-mail.

[Survey]
Marlowe Springs Business Development Center
Workshop Feedback Survey

Workshop: Writing Effective Grant Proposals
Date: March 6
Instructor: Dr. Leona Varga
Participant name (optional): Samuel Okonjo
Organization: Tennby Youth Sports Association

Please rate each item from 1 (poor) to 5 (excellent).
Content of the workshop: 5
Instructor''s knowledge: 5
Printed materials: 4
Room and equipment: 2
Length of the workshop: 3

What did you find most useful?
The sample budgets. I had never prepared one before, and Dr. Varga explained each line clearly.

What could be improved?
The room was crowded, and people at the back could not see the screen. Also, three hours was not enough. We ran out of time before we could practice writing the project summary, which was the part I most wanted to work on.

Would you like to be notified of future workshops? Yes

────────────────────

[E-mail]
To: Samuel Okonjo
From: Bridget Amadi, Program Coordinator
Date: March 13
Subject: Thank you for your feedback

Dear Mr. Okonjo,

Thank you for completing our survey after last week''s workshop. Several participants made comments similar to yours, and we have acted on them.

Dr. Varga has agreed to lead a follow-up session on April 10 from 1:00 to 3:00 P.M. It will be devoted entirely to the topic that had to be left out on March 6. Because it is being offered to make up for that omission, there is no fee for anyone who attended the original workshop. Others may register for $30.

The session will be held in the Dalton Room on the second floor, which seats 60, rather than in the Birch Room. Please bring a draft of a proposal you are working on, even a rough one, as participants will exchange drafts and comment on them.

To reserve a seat, simply reply to this message by April 3.

Best regards,
Bridget Amadi', 'NONE', '["He lacked experience in preparing budgets.", "He attended with several coworkers.", "He had taken Dr. Varga''s class before.", "He could not see the screen from his seat."]', 'A', 1, '[세부 사항] 정답 (A). "The sample budgets. I had never prepared one before"에서 예산서를 만들어 본 적이 없었음을 알 수 있다. 오답: (B) 동료 동행 언급은 없다. (C) 이전 수강 이야기는 없다. (D) 화면이 안 보인 것은 "people at the back"이라고 했을 뿐 본인 자리라고 하지 않았다.', 181),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'To what aspect of the workshop did Mr. Okonjo give the lowest rating?', 'Questions 181-185 refer to the following survey and e-mail.

[Survey]
Marlowe Springs Business Development Center
Workshop Feedback Survey

Workshop: Writing Effective Grant Proposals
Date: March 6
Instructor: Dr. Leona Varga
Participant name (optional): Samuel Okonjo
Organization: Tennby Youth Sports Association

Please rate each item from 1 (poor) to 5 (excellent).
Content of the workshop: 5
Instructor''s knowledge: 5
Printed materials: 4
Room and equipment: 2
Length of the workshop: 3

What did you find most useful?
The sample budgets. I had never prepared one before, and Dr. Varga explained each line clearly.

What could be improved?
The room was crowded, and people at the back could not see the screen. Also, three hours was not enough. We ran out of time before we could practice writing the project summary, which was the part I most wanted to work on.

Would you like to be notified of future workshops? Yes

────────────────────

[E-mail]
To: Samuel Okonjo
From: Bridget Amadi, Program Coordinator
Date: March 13
Subject: Thank you for your feedback

Dear Mr. Okonjo,

Thank you for completing our survey after last week''s workshop. Several participants made comments similar to yours, and we have acted on them.

Dr. Varga has agreed to lead a follow-up session on April 10 from 1:00 to 3:00 P.M. It will be devoted entirely to the topic that had to be left out on March 6. Because it is being offered to make up for that omission, there is no fee for anyone who attended the original workshop. Others may register for $30.

The session will be held in the Dalton Room on the second floor, which seats 60, rather than in the Birch Room. Please bring a draft of a proposal you are working on, even a rough one, as participants will exchange drafts and comment on them.

To reserve a seat, simply reply to this message by April 3.

Best regards,
Bridget Amadi', 'NONE', '["The content", "The duration", "The handouts", "The facilities"]', 'D', 1, '[세부 사항] 정답 (D). 가장 낮은 점수는 "Room and equipment: 2"이고 이를 the facilities로 바꿔 말했다. 오답: (A) 내용은 5점, (B) 길이는 3점, (C) 인쇄 자료는 4점이다.', 182),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What will most likely be the focus of the April 10 session?', 'Questions 181-185 refer to the following survey and e-mail.

[Survey]
Marlowe Springs Business Development Center
Workshop Feedback Survey

Workshop: Writing Effective Grant Proposals
Date: March 6
Instructor: Dr. Leona Varga
Participant name (optional): Samuel Okonjo
Organization: Tennby Youth Sports Association

Please rate each item from 1 (poor) to 5 (excellent).
Content of the workshop: 5
Instructor''s knowledge: 5
Printed materials: 4
Room and equipment: 2
Length of the workshop: 3

What did you find most useful?
The sample budgets. I had never prepared one before, and Dr. Varga explained each line clearly.

What could be improved?
The room was crowded, and people at the back could not see the screen. Also, three hours was not enough. We ran out of time before we could practice writing the project summary, which was the part I most wanted to work on.

Would you like to be notified of future workshops? Yes

────────────────────

[E-mail]
To: Samuel Okonjo
From: Bridget Amadi, Program Coordinator
Date: March 13
Subject: Thank you for your feedback

Dear Mr. Okonjo,

Thank you for completing our survey after last week''s workshop. Several participants made comments similar to yours, and we have acted on them.

Dr. Varga has agreed to lead a follow-up session on April 10 from 1:00 to 3:00 P.M. It will be devoted entirely to the topic that had to be left out on March 6. Because it is being offered to make up for that omission, there is no fee for anyone who attended the original workshop. Others may register for $30.

The session will be held in the Dalton Room on the second floor, which seats 60, rather than in the Birch Room. Please bring a draft of a proposal you are working on, even a rough one, as participants will exchange drafts and comment on them.

To reserve a seat, simply reply to this message by April 3.

Best regards,
Bridget Amadi', 'NONE', '["Preparing sample budgets", "Writing a project summary", "Finding organizations that offer grants", "Using presentation equipment"]', 'B', 1, '[연계(문서1+2)] 정답 (B). 이메일은 4월 10일 수업이 "the topic that had to be left out on March 6"만 다룬다고 했고, 설문에서 시간이 모자라 하지 못한 것은 "practice writing the project summary"였다. 오답: (A) 예산 예시는 3월 6일에 이미 다뤘다. (C) 보조금 기관 찾기는 어디에도 없다. (D) 장비는 불만 사항이었을 뿐 수업 주제가 아니다.', 183),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the Birch Room?', 'Questions 181-185 refer to the following survey and e-mail.

[Survey]
Marlowe Springs Business Development Center
Workshop Feedback Survey

Workshop: Writing Effective Grant Proposals
Date: March 6
Instructor: Dr. Leona Varga
Participant name (optional): Samuel Okonjo
Organization: Tennby Youth Sports Association

Please rate each item from 1 (poor) to 5 (excellent).
Content of the workshop: 5
Instructor''s knowledge: 5
Printed materials: 4
Room and equipment: 2
Length of the workshop: 3

What did you find most useful?
The sample budgets. I had never prepared one before, and Dr. Varga explained each line clearly.

What could be improved?
The room was crowded, and people at the back could not see the screen. Also, three hours was not enough. We ran out of time before we could practice writing the project summary, which was the part I most wanted to work on.

Would you like to be notified of future workshops? Yes

────────────────────

[E-mail]
To: Samuel Okonjo
From: Bridget Amadi, Program Coordinator
Date: March 13
Subject: Thank you for your feedback

Dear Mr. Okonjo,

Thank you for completing our survey after last week''s workshop. Several participants made comments similar to yours, and we have acted on them.

Dr. Varga has agreed to lead a follow-up session on April 10 from 1:00 to 3:00 P.M. It will be devoted entirely to the topic that had to be left out on March 6. Because it is being offered to make up for that omission, there is no fee for anyone who attended the original workshop. Others may register for $30.

The session will be held in the Dalton Room on the second floor, which seats 60, rather than in the Birch Room. Please bring a draft of a proposal you are working on, even a rough one, as participants will exchange drafts and comment on them.

To reserve a seat, simply reply to this message by April 3.

Best regards,
Bridget Amadi', 'NONE', '["It is located on the second floor.", "It can seat sixty people.", "It was where the March 6 workshop took place.", "It has been fitted with a larger screen."]', 'C', 1, '[추론] 정답 (C). 설문에서 방이 비좁았다고 했고, 이메일은 그런 의견에 따라 조치했다며 다음 수업을 "rather than in the Birch Room" 60석짜리 Dalton Room에서 연다고 했으므로 3월 6일 장소가 Birch Room이었음을 알 수 있다. 오답: (A)·(B) 2층·60석은 Dalton Room의 설명이다. (D) 화면 교체 언급은 없다.', 184),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is Mr. Okonjo asked to bring to the session?', 'Questions 181-185 refer to the following survey and e-mail.

[Survey]
Marlowe Springs Business Development Center
Workshop Feedback Survey

Workshop: Writing Effective Grant Proposals
Date: March 6
Instructor: Dr. Leona Varga
Participant name (optional): Samuel Okonjo
Organization: Tennby Youth Sports Association

Please rate each item from 1 (poor) to 5 (excellent).
Content of the workshop: 5
Instructor''s knowledge: 5
Printed materials: 4
Room and equipment: 2
Length of the workshop: 3

What did you find most useful?
The sample budgets. I had never prepared one before, and Dr. Varga explained each line clearly.

What could be improved?
The room was crowded, and people at the back could not see the screen. Also, three hours was not enough. We ran out of time before we could practice writing the project summary, which was the part I most wanted to work on.

Would you like to be notified of future workshops? Yes

────────────────────

[E-mail]
To: Samuel Okonjo
From: Bridget Amadi, Program Coordinator
Date: March 13
Subject: Thank you for your feedback

Dear Mr. Okonjo,

Thank you for completing our survey after last week''s workshop. Several participants made comments similar to yours, and we have acted on them.

Dr. Varga has agreed to lead a follow-up session on April 10 from 1:00 to 3:00 P.M. It will be devoted entirely to the topic that had to be left out on March 6. Because it is being offered to make up for that omission, there is no fee for anyone who attended the original workshop. Others may register for $30.

The session will be held in the Dalton Room on the second floor, which seats 60, rather than in the Birch Room. Please bring a draft of a proposal you are working on, even a rough one, as participants will exchange drafts and comment on them.

To reserve a seat, simply reply to this message by April 3.

Best regards,
Bridget Amadi', 'NONE', '["A registration fee of $30", "A document he has started writing", "A copy of his completed survey", "A list of questions for Dr. Varga"]', 'B', 1, '[세부 사항] 정답 (B). "Please bring a draft of a proposal you are working on, even a rough one"을 ''쓰기 시작한 문서''로 바꿔 말했다. 오답: (A) 3월 6일 참석자는 무료다. (C) 설문지를 가져오라는 말은 없다. (D) 질문 목록은 언급되지 않았다.', 185),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'Why does Calloway Inn need new uniforms?', 'Questions 186-190 refer to the following e-mails and table.

[E-mail]
To: Northgate Uniforms; Pierce Linen Supply; Tennby Workwear
From: Vivian Castellano, Purchasing Manager, Calloway Inn
Date: February 3
Subject: Request for quotation

To whom it may concern:

Calloway Inn in Marlowe Springs will reopen its restaurant on April 1 after a three-month renovation, and we need new uniforms for the dining room staff. Please send a quotation for 80 long-sleeved shirts and 80 aprons, each embroidered with our logo. A copy of the logo is attached. Sizes will be confirmed when the order is placed.

Our requirements are as follows. All items must be delivered no later than March 20 so that staff can be fitted before the reopening. The total cost, including embroidery and delivery, must not exceed $4,000. We would also like to see fabric samples before placing an order.

Quotations received by February 10 will be considered. We expect to make a decision within a few days of that date.

Vivian Castellano

────────────────────

[Table]
Calloway Inn — Uniform Quotations
Summary prepared on February 11 by V. Castellano

Supplier | Total price | Delivery date | Fabric samples | Notes
Northgate Uniforms | $3,650 | March 27 | Free | 5% discount on repeat orders
Pierce Linen Supply | $3,880 | March 14 | Free | Embroidery done at its own workshop
Tennby Workwear | $4,150 | March 10 | $20, refunded with order | Faulty items replaced free for one year

All prices include embroidery and delivery.
Payment terms (all three suppliers): 30 percent deposit with the order, balance on delivery

────────────────────

[E-mail]
To: Edwin Pierce, Pierce Linen Supply
From: Vivian Castellano
Date: February 12
Subject: Uniform order

Dear Mr. Pierce,

Thank you for your quotation. I am pleased to tell you that we would like to proceed with your company. The samples you sent arrived this morning, and our general manager has chosen the charcoal fabric for the aprons.

Before I send the purchase order, I have one request. Our general manager has decided to hire additional servers, so we now need 90 shirts and 90 aprons instead of 80. Could you send a revised quotation and confirm that the larger order will not affect your delivery date?

Also, the logo file I sent on February 3 was an outdated version. The current one is attached; please use it for the embroidery.

As stated in your terms, we will pay a 30 percent deposit when the order is placed and the balance on delivery.

Best regards,
Vivian Castellano', 'NONE', '["It is about to reopen a dining facility.", "It has recently hired a new chef.", "It is changing the name of the inn.", "It is opening a second location."]', 'A', 1, '[세부 사항] 정답 (A). "will reopen its restaurant on April 1 after a three-month renovation, and we need new uniforms for the dining room staff"가 근거이며 restaurant를 a dining facility로 바꿔 말했다. 오답: (B) 주방장 채용, (C) 이름 변경, (D) 2호점 이야기는 어디에도 없다.', 186),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is NOT a requirement stated in the first e-mail?', 'Questions 186-190 refer to the following e-mails and table.

[E-mail]
To: Northgate Uniforms; Pierce Linen Supply; Tennby Workwear
From: Vivian Castellano, Purchasing Manager, Calloway Inn
Date: February 3
Subject: Request for quotation

To whom it may concern:

Calloway Inn in Marlowe Springs will reopen its restaurant on April 1 after a three-month renovation, and we need new uniforms for the dining room staff. Please send a quotation for 80 long-sleeved shirts and 80 aprons, each embroidered with our logo. A copy of the logo is attached. Sizes will be confirmed when the order is placed.

Our requirements are as follows. All items must be delivered no later than March 20 so that staff can be fitted before the reopening. The total cost, including embroidery and delivery, must not exceed $4,000. We would also like to see fabric samples before placing an order.

Quotations received by February 10 will be considered. We expect to make a decision within a few days of that date.

Vivian Castellano

────────────────────

[Table]
Calloway Inn — Uniform Quotations
Summary prepared on February 11 by V. Castellano

Supplier | Total price | Delivery date | Fabric samples | Notes
Northgate Uniforms | $3,650 | March 27 | Free | 5% discount on repeat orders
Pierce Linen Supply | $3,880 | March 14 | Free | Embroidery done at its own workshop
Tennby Workwear | $4,150 | March 10 | $20, refunded with order | Faulty items replaced free for one year

All prices include embroidery and delivery.
Payment terms (all three suppliers): 30 percent deposit with the order, balance on delivery

────────────────────

[E-mail]
To: Edwin Pierce, Pierce Linen Supply
From: Vivian Castellano
Date: February 12
Subject: Uniform order

Dear Mr. Pierce,

Thank you for your quotation. I am pleased to tell you that we would like to proceed with your company. The samples you sent arrived this morning, and our general manager has chosen the charcoal fabric for the aprons.

Before I send the purchase order, I have one request. Our general manager has decided to hire additional servers, so we now need 90 shirts and 90 aprons instead of 80. Could you send a revised quotation and confirm that the larger order will not affect your delivery date?

Also, the logo file I sent on February 3 was an outdated version. The current one is attached; please use it for the embroidery.

As stated in your terms, we will pay a 30 percent deposit when the order is placed and the balance on delivery.

Best regards,
Vivian Castellano', 'NONE', '["A discount on repeat orders", "A limit on the total cost", "A deadline for delivery", "A logo added to each item"]', 'A', 1, '[사실 확인(NOT)] 정답 (A). 재주문 할인은 표에서 Northgate가 내건 조건일 뿐 첫 이메일의 요구 사항이 아니다. 오답: (B) "must not exceed $4,000". (C) "delivered no later than March 20". (D) "each embroidered with our logo".', 187),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'Why was Northgate Uniforms most likely not selected?', 'Questions 186-190 refer to the following e-mails and table.

[E-mail]
To: Northgate Uniforms; Pierce Linen Supply; Tennby Workwear
From: Vivian Castellano, Purchasing Manager, Calloway Inn
Date: February 3
Subject: Request for quotation

To whom it may concern:

Calloway Inn in Marlowe Springs will reopen its restaurant on April 1 after a three-month renovation, and we need new uniforms for the dining room staff. Please send a quotation for 80 long-sleeved shirts and 80 aprons, each embroidered with our logo. A copy of the logo is attached. Sizes will be confirmed when the order is placed.

Our requirements are as follows. All items must be delivered no later than March 20 so that staff can be fitted before the reopening. The total cost, including embroidery and delivery, must not exceed $4,000. We would also like to see fabric samples before placing an order.

Quotations received by February 10 will be considered. We expect to make a decision within a few days of that date.

Vivian Castellano

────────────────────

[Table]
Calloway Inn — Uniform Quotations
Summary prepared on February 11 by V. Castellano

Supplier | Total price | Delivery date | Fabric samples | Notes
Northgate Uniforms | $3,650 | March 27 | Free | 5% discount on repeat orders
Pierce Linen Supply | $3,880 | March 14 | Free | Embroidery done at its own workshop
Tennby Workwear | $4,150 | March 10 | $20, refunded with order | Faulty items replaced free for one year

All prices include embroidery and delivery.
Payment terms (all three suppliers): 30 percent deposit with the order, balance on delivery

────────────────────

[E-mail]
To: Edwin Pierce, Pierce Linen Supply
From: Vivian Castellano
Date: February 12
Subject: Uniform order

Dear Mr. Pierce,

Thank you for your quotation. I am pleased to tell you that we would like to proceed with your company. The samples you sent arrived this morning, and our general manager has chosen the charcoal fabric for the aprons.

Before I send the purchase order, I have one request. Our general manager has decided to hire additional servers, so we now need 90 shirts and 90 aprons instead of 80. Could you send a revised quotation and confirm that the larger order will not affect your delivery date?

Also, the logo file I sent on February 3 was an outdated version. The current one is attached; please use it for the embroidery.

As stated in your terms, we will pay a 30 percent deposit when the order is placed and the balance on delivery.

Best regards,
Vivian Castellano', 'NONE', '["Its price was over the stated budget.", "It charges a fee for fabric samples.", "It does not offer embroidery.", "It could not deliver the items early enough."]', 'D', 1, '[연계(문서1+2)] 정답 (D). 첫 이메일의 조건은 "no later than March 20"인데 표에서 Northgate의 납품일은 March 27이다. 오답: (A) 3,650달러로 예산 4,000달러 안이다. (B) 견본은 무료다. (C) 모든 가격에 자수가 포함되어 있다.', 188),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about the supplier that Ms. Castellano chose?', 'Questions 186-190 refer to the following e-mails and table.

[E-mail]
To: Northgate Uniforms; Pierce Linen Supply; Tennby Workwear
From: Vivian Castellano, Purchasing Manager, Calloway Inn
Date: February 3
Subject: Request for quotation

To whom it may concern:

Calloway Inn in Marlowe Springs will reopen its restaurant on April 1 after a three-month renovation, and we need new uniforms for the dining room staff. Please send a quotation for 80 long-sleeved shirts and 80 aprons, each embroidered with our logo. A copy of the logo is attached. Sizes will be confirmed when the order is placed.

Our requirements are as follows. All items must be delivered no later than March 20 so that staff can be fitted before the reopening. The total cost, including embroidery and delivery, must not exceed $4,000. We would also like to see fabric samples before placing an order.

Quotations received by February 10 will be considered. We expect to make a decision within a few days of that date.

Vivian Castellano

────────────────────

[Table]
Calloway Inn — Uniform Quotations
Summary prepared on February 11 by V. Castellano

Supplier | Total price | Delivery date | Fabric samples | Notes
Northgate Uniforms | $3,650 | March 27 | Free | 5% discount on repeat orders
Pierce Linen Supply | $3,880 | March 14 | Free | Embroidery done at its own workshop
Tennby Workwear | $4,150 | March 10 | $20, refunded with order | Faulty items replaced free for one year

All prices include embroidery and delivery.
Payment terms (all three suppliers): 30 percent deposit with the order, balance on delivery

────────────────────

[E-mail]
To: Edwin Pierce, Pierce Linen Supply
From: Vivian Castellano
Date: February 12
Subject: Uniform order

Dear Mr. Pierce,

Thank you for your quotation. I am pleased to tell you that we would like to proceed with your company. The samples you sent arrived this morning, and our general manager has chosen the charcoal fabric for the aprons.

Before I send the purchase order, I have one request. Our general manager has decided to hire additional servers, so we now need 90 shirts and 90 aprons instead of 80. Could you send a revised quotation and confirm that the larger order will not affect your delivery date?

Also, the logo file I sent on February 3 was an outdated version. The current one is attached; please use it for the embroidery.

As stated in your terms, we will pay a 30 percent deposit when the order is placed and the balance on delivery.

Best regards,
Vivian Castellano', 'NONE', '["It charges customers for fabric samples.", "It adds logos to garments at its own facility.", "It submitted the lowest quotation.", "It replaces faulty items for one year."]', 'B', 1, '[연계(문서2+3)] 정답 (B). 두 번째 이메일에서 선택한 업체는 Mr. Pierce의 Pierce Linen Supply이고, 표의 Notes에 "Embroidery done at its own workshop"이라고 되어 있다. 오답: (A)·(D) 견본 비용과 1년 무상 교체는 Tennby Workwear의 조건이다. (C) 가장 낮은 견적은 Northgate의 3,650달러다.', 189),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What does Ms. Castellano ask Mr. Pierce to do?', 'Questions 186-190 refer to the following e-mails and table.

[E-mail]
To: Northgate Uniforms; Pierce Linen Supply; Tennby Workwear
From: Vivian Castellano, Purchasing Manager, Calloway Inn
Date: February 3
Subject: Request for quotation

To whom it may concern:

Calloway Inn in Marlowe Springs will reopen its restaurant on April 1 after a three-month renovation, and we need new uniforms for the dining room staff. Please send a quotation for 80 long-sleeved shirts and 80 aprons, each embroidered with our logo. A copy of the logo is attached. Sizes will be confirmed when the order is placed.

Our requirements are as follows. All items must be delivered no later than March 20 so that staff can be fitted before the reopening. The total cost, including embroidery and delivery, must not exceed $4,000. We would also like to see fabric samples before placing an order.

Quotations received by February 10 will be considered. We expect to make a decision within a few days of that date.

Vivian Castellano

────────────────────

[Table]
Calloway Inn — Uniform Quotations
Summary prepared on February 11 by V. Castellano

Supplier | Total price | Delivery date | Fabric samples | Notes
Northgate Uniforms | $3,650 | March 27 | Free | 5% discount on repeat orders
Pierce Linen Supply | $3,880 | March 14 | Free | Embroidery done at its own workshop
Tennby Workwear | $4,150 | March 10 | $20, refunded with order | Faulty items replaced free for one year

All prices include embroidery and delivery.
Payment terms (all three suppliers): 30 percent deposit with the order, balance on delivery

────────────────────

[E-mail]
To: Edwin Pierce, Pierce Linen Supply
From: Vivian Castellano
Date: February 12
Subject: Uniform order

Dear Mr. Pierce,

Thank you for your quotation. I am pleased to tell you that we would like to proceed with your company. The samples you sent arrived this morning, and our general manager has chosen the charcoal fabric for the aprons.

Before I send the purchase order, I have one request. Our general manager has decided to hire additional servers, so we now need 90 shirts and 90 aprons instead of 80. Could you send a revised quotation and confirm that the larger order will not affect your delivery date?

Also, the logo file I sent on February 3 was an outdated version. The current one is attached; please use it for the embroidery.

As stated in your terms, we will pay a 30 percent deposit when the order is placed and the balance on delivery.

Best regards,
Vivian Castellano', 'NONE', '["Provide an updated price", "Send more fabric samples", "Deliver the order earlier", "Design a new logo"]', 'A', 1, '[세부 사항] 정답 (A). "Could you send a revised quotation"을 ''수정된 가격을 알려 주다''로 바꿔 말했다. 오답: (B) 견본은 이미 받아서 천을 골랐다. (C) 납품일이 늦어지지 않는지 확인해 달라는 것이지 앞당겨 달라는 것이 아니다. (D) 로고는 새 파일을 첨부했을 뿐 디자인을 맡기지 않았다.', 190),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about The Loom?', 'Questions 191-195 refer to the following advertisement, online review, and e-mail.

[Advertisement]
The Loom — Shared Workspace in Downtown Tennby

Located in a restored textile mill at 9 Weaver''s Row, a five-minute walk from Tennby Station.

Membership plans (per month)
Day Pass Plan — $90: any open desk, five days per month
Flex Plan — $180: any open desk, unlimited days, weekdays 8 A.M. to 6 P.M.
Dedicated Plan — $290: your own desk and locker, 24-hour access
Studio Plan — $650: private lockable office for up to four people, 24-hour access

All plans include high-speed Wi-Fi, coffee and tea, and use of the printer. Meeting rooms may be booked for $15 per hour; Dedicated and Studio members receive six free hours each month.

Special offer: Sign up for any plan before July 31, and we will waive the $50 setup fee. Tours are available by appointment at 555-0126.

────────────────────

[Online review]
The Loom — reviewed by Teodoro Vance on August 19
Rating: 4 out of 5

I run a small translation business, and I joined The Loom at the beginning of July. The building is beautiful, the internet is reliable, and the staff are responsive to every request. Since I often work late into the evening to meet deadlines for overseas clients, I chose the plan that gives me my own desk and round-the-clock access, and I have not regretted it.

My only complaint concerns the meeting rooms. There are just two, and they are often fully booked days ahead. Last week I had to meet a client in a café across the street because neither room was free. For the price, I expected better availability.

────────────────────

[E-mail]
To: Teodoro Vance
From: Simone Achebe, Community Manager, The Loom
Date: August 21
Subject: Your review

Dear Mr. Vance,

Thank you for taking the time to review The Loom. We are glad that the space suits your business, and we take your comment about the meeting rooms seriously.

I am happy to report that a solution is on the way. The storage area on the second floor is being converted into two additional meeting rooms, which will open on September 15. One of them will be equipped for video conferences. Until then, members may use the lounge on the ground floor for client meetings without charge; simply let the front desk know an hour in advance.

As an apology for last week''s inconvenience, I have added three extra meeting-room hours to your account for September. They are in addition to the free hours that your plan already provides.

Sincerely,
Simone Achebe', 'NONE', '["It is located inside Tennby Station.", "It charges members a fee for printing.", "It occupies a building once used for manufacturing.", "It is closed to all members on weekends."]', 'C', 1, '[사실 확인] 정답 (C). "Located in a restored textile mill"을 ''예전에 제조에 쓰이던 건물''로 바꿔 말했다. 오답: (A) 역에서 걸어서 5분 거리다. (B) 모든 요금제에 프린터 사용이 포함된다. (D) 평일 제한은 Flex Plan뿐이고 Dedicated·Studio는 24시간 이용이다.', 191),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is included in every membership plan?', 'Questions 191-195 refer to the following advertisement, online review, and e-mail.

[Advertisement]
The Loom — Shared Workspace in Downtown Tennby

Located in a restored textile mill at 9 Weaver''s Row, a five-minute walk from Tennby Station.

Membership plans (per month)
Day Pass Plan — $90: any open desk, five days per month
Flex Plan — $180: any open desk, unlimited days, weekdays 8 A.M. to 6 P.M.
Dedicated Plan — $290: your own desk and locker, 24-hour access
Studio Plan — $650: private lockable office for up to four people, 24-hour access

All plans include high-speed Wi-Fi, coffee and tea, and use of the printer. Meeting rooms may be booked for $15 per hour; Dedicated and Studio members receive six free hours each month.

Special offer: Sign up for any plan before July 31, and we will waive the $50 setup fee. Tours are available by appointment at 555-0126.

────────────────────

[Online review]
The Loom — reviewed by Teodoro Vance on August 19
Rating: 4 out of 5

I run a small translation business, and I joined The Loom at the beginning of July. The building is beautiful, the internet is reliable, and the staff are responsive to every request. Since I often work late into the evening to meet deadlines for overseas clients, I chose the plan that gives me my own desk and round-the-clock access, and I have not regretted it.

My only complaint concerns the meeting rooms. There are just two, and they are often fully booked days ahead. Last week I had to meet a client in a café across the street because neither room was free. For the price, I expected better availability.

────────────────────

[E-mail]
To: Teodoro Vance
From: Simone Achebe, Community Manager, The Loom
Date: August 21
Subject: Your review

Dear Mr. Vance,

Thank you for taking the time to review The Loom. We are glad that the space suits your business, and we take your comment about the meeting rooms seriously.

I am happy to report that a solution is on the way. The storage area on the second floor is being converted into two additional meeting rooms, which will open on September 15. One of them will be equipped for video conferences. Until then, members may use the lounge on the ground floor for client meetings without charge; simply let the front desk know an hour in advance.

As an apology for last week''s inconvenience, I have added three extra meeting-room hours to your account for September. They are in addition to the free hours that your plan already provides.

Sincerely,
Simone Achebe', 'NONE', '["A personal locker", "Hot beverages", "Free meeting-room hours", "Access at any hour"]', 'B', 1, '[세부 사항] 정답 (B). "All plans include high-speed Wi-Fi, coffee and tea, and use of the printer"에서 coffee and tea를 hot beverages로 바꿔 말했다. 오답: (A) 사물함은 Dedicated Plan, (C) 무료 회의실 시간은 Dedicated·Studio, (D) 24시간 출입도 Dedicated·Studio에만 해당한다.', 192),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is most likely true about Mr. Vance?', 'Questions 191-195 refer to the following advertisement, online review, and e-mail.

[Advertisement]
The Loom — Shared Workspace in Downtown Tennby

Located in a restored textile mill at 9 Weaver''s Row, a five-minute walk from Tennby Station.

Membership plans (per month)
Day Pass Plan — $90: any open desk, five days per month
Flex Plan — $180: any open desk, unlimited days, weekdays 8 A.M. to 6 P.M.
Dedicated Plan — $290: your own desk and locker, 24-hour access
Studio Plan — $650: private lockable office for up to four people, 24-hour access

All plans include high-speed Wi-Fi, coffee and tea, and use of the printer. Meeting rooms may be booked for $15 per hour; Dedicated and Studio members receive six free hours each month.

Special offer: Sign up for any plan before July 31, and we will waive the $50 setup fee. Tours are available by appointment at 555-0126.

────────────────────

[Online review]
The Loom — reviewed by Teodoro Vance on August 19
Rating: 4 out of 5

I run a small translation business, and I joined The Loom at the beginning of July. The building is beautiful, the internet is reliable, and the staff are responsive to every request. Since I often work late into the evening to meet deadlines for overseas clients, I chose the plan that gives me my own desk and round-the-clock access, and I have not regretted it.

My only complaint concerns the meeting rooms. There are just two, and they are often fully booked days ahead. Last week I had to meet a client in a café across the street because neither room was free. For the price, I expected better availability.

────────────────────

[E-mail]
To: Teodoro Vance
From: Simone Achebe, Community Manager, The Loom
Date: August 21
Subject: Your review

Dear Mr. Vance,

Thank you for taking the time to review The Loom. We are glad that the space suits your business, and we take your comment about the meeting rooms seriously.

I am happy to report that a solution is on the way. The storage area on the second floor is being converted into two additional meeting rooms, which will open on September 15. One of them will be equipped for video conferences. Until then, members may use the lounge on the ground floor for client meetings without charge; simply let the front desk know an hour in advance.

As an apology for last week''s inconvenience, I have added three extra meeting-room hours to your account for September. They are in addition to the free hours that your plan already provides.

Sincerely,
Simone Achebe', 'NONE', '["He uses a different desk each day.", "He works at The Loom only until 6 P.M.", "He pays $180 per month for his plan.", "He was not charged a setup fee."]', 'D', 1, '[연계(문서1+2)] 정답 (D). 후기에서 "I joined The Loom at the beginning of July"라고 했고, 광고는 7월 31일 전에 가입하면 "we will waive the $50 setup fee"라고 했다. 오답: (A) "my own desk"를 쓴다. (B) 24시간 이용 요금제를 골랐고 저녁 늦게까지 일한다. (C) 전용 책상과 24시간 이용은 290달러짜리 Dedicated Plan이다.', 193),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'In the online review, the word "run" in paragraph 1, line 1, is closest in meaning to', 'Questions 191-195 refer to the following advertisement, online review, and e-mail.

[Advertisement]
The Loom — Shared Workspace in Downtown Tennby

Located in a restored textile mill at 9 Weaver''s Row, a five-minute walk from Tennby Station.

Membership plans (per month)
Day Pass Plan — $90: any open desk, five days per month
Flex Plan — $180: any open desk, unlimited days, weekdays 8 A.M. to 6 P.M.
Dedicated Plan — $290: your own desk and locker, 24-hour access
Studio Plan — $650: private lockable office for up to four people, 24-hour access

All plans include high-speed Wi-Fi, coffee and tea, and use of the printer. Meeting rooms may be booked for $15 per hour; Dedicated and Studio members receive six free hours each month.

Special offer: Sign up for any plan before July 31, and we will waive the $50 setup fee. Tours are available by appointment at 555-0126.

────────────────────

[Online review]
The Loom — reviewed by Teodoro Vance on August 19
Rating: 4 out of 5

I run a small translation business, and I joined The Loom at the beginning of July. The building is beautiful, the internet is reliable, and the staff are responsive to every request. Since I often work late into the evening to meet deadlines for overseas clients, I chose the plan that gives me my own desk and round-the-clock access, and I have not regretted it.

My only complaint concerns the meeting rooms. There are just two, and they are often fully booked days ahead. Last week I had to meet a client in a café across the street because neither room was free. For the price, I expected better availability.

────────────────────

[E-mail]
To: Teodoro Vance
From: Simone Achebe, Community Manager, The Loom
Date: August 21
Subject: Your review

Dear Mr. Vance,

Thank you for taking the time to review The Loom. We are glad that the space suits your business, and we take your comment about the meeting rooms seriously.

I am happy to report that a solution is on the way. The storage area on the second floor is being converted into two additional meeting rooms, which will open on September 15. One of them will be equipped for video conferences. Until then, members may use the lounge on the ground floor for client meetings without charge; simply let the front desk know an hour in advance.

As an apology for last week''s inconvenience, I have added three extra meeting-room hours to your account for September. They are in addition to the free hours that your plan already provides.

Sincerely,
Simone Achebe', 'NONE', '["race", "manage", "publish", "extend"]', 'B', 1, '[동의어] 정답 (B). "I run a small translation business"는 사업체를 운영한다는 뜻이므로 manage가 가장 가깝다. 오답: (A) 달리다·경주하다, (C) 싣다·게재하다, (D) 이어지다는 run의 다른 뜻으로 a business를 목적어로 한 이 문맥에 맞지 않는다.', 194),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'How many free meeting-room hours will Mr. Vance most likely have in September?', 'Questions 191-195 refer to the following advertisement, online review, and e-mail.

[Advertisement]
The Loom — Shared Workspace in Downtown Tennby

Located in a restored textile mill at 9 Weaver''s Row, a five-minute walk from Tennby Station.

Membership plans (per month)
Day Pass Plan — $90: any open desk, five days per month
Flex Plan — $180: any open desk, unlimited days, weekdays 8 A.M. to 6 P.M.
Dedicated Plan — $290: your own desk and locker, 24-hour access
Studio Plan — $650: private lockable office for up to four people, 24-hour access

All plans include high-speed Wi-Fi, coffee and tea, and use of the printer. Meeting rooms may be booked for $15 per hour; Dedicated and Studio members receive six free hours each month.

Special offer: Sign up for any plan before July 31, and we will waive the $50 setup fee. Tours are available by appointment at 555-0126.

────────────────────

[Online review]
The Loom — reviewed by Teodoro Vance on August 19
Rating: 4 out of 5

I run a small translation business, and I joined The Loom at the beginning of July. The building is beautiful, the internet is reliable, and the staff are responsive to every request. Since I often work late into the evening to meet deadlines for overseas clients, I chose the plan that gives me my own desk and round-the-clock access, and I have not regretted it.

My only complaint concerns the meeting rooms. There are just two, and they are often fully booked days ahead. Last week I had to meet a client in a café across the street because neither room was free. For the price, I expected better availability.

────────────────────

[E-mail]
To: Teodoro Vance
From: Simone Achebe, Community Manager, The Loom
Date: August 21
Subject: Your review

Dear Mr. Vance,

Thank you for taking the time to review The Loom. We are glad that the space suits your business, and we take your comment about the meeting rooms seriously.

I am happy to report that a solution is on the way. The storage area on the second floor is being converted into two additional meeting rooms, which will open on September 15. One of them will be equipped for video conferences. Until then, members may use the lounge on the ground floor for client meetings without charge; simply let the front desk know an hour in advance.

As an apology for last week''s inconvenience, I have added three extra meeting-room hours to your account for September. They are in addition to the free hours that your plan already provides.

Sincerely,
Simone Achebe', 'NONE', '["6", "9", "12", "15"]', 'B', 1, '[연계(문서1+2+3)] 정답 (B). 후기의 ''전용 책상 + 24시간 이용''은 Dedicated Plan이고 광고에 따르면 이 요금제는 매달 6시간이 무료다. 이메일에서 "three extra meeting-room hours"를 9월에 더해 주었으므로 6 + 3 = 9시간이다. 오답: (A) 추가분을 빼고 본 수, (C)·(D) 지문의 숫자로 나올 수 없는 값이다.', 195),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'According to the Web page, what do all members receive?', 'Questions 196-200 refer to the following Web page, form, and e-mail.

[Web page]
Marlowe Springs Museum of Natural History
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
Marlowe Springs Museum of Natural History
Membership Application

Date: November 18
Name: Rosalind Meier
Address: 77 Fernhill Road, Tennby
Telephone: 555-0163
Membership level: Dual
Second cardholder: Anton Meier
Payment: $85, credit card
Additional donation (optional): $25 to the Education Fund
How did you hear about us? From a coworker who volunteers at the museum
Comments: We would like to attend the opening day of the dinosaur exhibition if tickets are still available.

────────────────────

[E-mail]
To: Rosalind Meier
From: Visitor Services, Marlowe Springs Museum of Natural History
Date: November 20
Subject: Welcome to the museum

Dear Ms. Meier,

Welcome, and thank you for your additional gift to the Education Fund. Your membership cards will be mailed within ten days. Until they arrive, please show this e-mail at the entrance, and you will be admitted without delay.

In reply to your comment, Giants of the Cretaceous opens on December 6 and will run until the end of April. I am sorry to say that tickets for opening day are sold out, but plenty remain for December 7 onward. The regular price is $24 per adult. Members may book now at the member rate on our Web site; booking opens to the general public on November 27.

You may also be interested to know that members at your level can upgrade to a Family membership at any time by paying only the difference in price. Several members do so each year when relatives come to visit.

Warm regards,
Jerome Baptiste
Visitor Services', 'NONE', '["A publication issued four times a year", "Passes for guests who are not members", "Free entry to special exhibitions", "An invitation to an annual dinner"]', 'A', 1, '[세부 사항] 정답 (A). "All members receive ... the quarterly magazine Strata"에서 quarterly magazine을 ''1년에 네 번 나오는 간행물''로 바꿔 말했다. 오답: (B) 손님용 입장권과 (D) 연례 만찬 초대는 Supporter 등급 혜택이다. (C) 특별전은 무료가 아니라 반값이다.', 196),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about Ms. Meier''s membership?', 'Questions 196-200 refer to the following Web page, form, and e-mail.

[Web page]
Marlowe Springs Museum of Natural History
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
Marlowe Springs Museum of Natural History
Membership Application

Date: November 18
Name: Rosalind Meier
Address: 77 Fernhill Road, Tennby
Telephone: 555-0163
Membership level: Dual
Second cardholder: Anton Meier
Payment: $85, credit card
Additional donation (optional): $25 to the Education Fund
How did you hear about us? From a coworker who volunteers at the museum
Comments: We would like to attend the opening day of the dinosaur exhibition if tickets are still available.

────────────────────

[E-mail]
To: Rosalind Meier
From: Visitor Services, Marlowe Springs Museum of Natural History
Date: November 20
Subject: Welcome to the museum

Dear Ms. Meier,

Welcome, and thank you for your additional gift to the Education Fund. Your membership cards will be mailed within ten days. Until they arrive, please show this e-mail at the entrance, and you will be admitted without delay.

In reply to your comment, Giants of the Cretaceous opens on December 6 and will run until the end of April. I am sorry to say that tickets for opening day are sold out, but plenty remain for December 7 onward. The regular price is $24 per adult. Members may book now at the member rate on our Web site; booking opens to the general public on November 27.

You may also be interested to know that members at your level can upgrade to a Family membership at any time by paying only the difference in price. Several members do so each year when relatives come to visit.

Warm regards,
Jerome Baptiste
Visitor Services', 'NONE', '["It includes admission for four children.", "It will remain valid for more than a year.", "It was paid for by one of her coworkers.", "It comes with four passes for guests."]', 'B', 1, '[연계(문서1+2)] 정답 (B). 신청서 날짜가 November 18이고 웹 페이지에 "Memberships purchased in November are valid for 13 months instead of 12"라고 되어 있다. 오답: (A) 자녀 입장은 Family 등급이고 Ms. Meier는 Dual이다. (C) 동료는 박물관을 알려 준 사람일 뿐 본인이 카드로 결제했다. (D) 손님용 입장권은 Supporter 등급이다.', 197),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What does Ms. Meier indicate on the form?', 'Questions 196-200 refer to the following Web page, form, and e-mail.

[Web page]
Marlowe Springs Museum of Natural History
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
Marlowe Springs Museum of Natural History
Membership Application

Date: November 18
Name: Rosalind Meier
Address: 77 Fernhill Road, Tennby
Telephone: 555-0163
Membership level: Dual
Second cardholder: Anton Meier
Payment: $85, credit card
Additional donation (optional): $25 to the Education Fund
How did you hear about us? From a coworker who volunteers at the museum
Comments: We would like to attend the opening day of the dinosaur exhibition if tickets are still available.

────────────────────

[E-mail]
To: Rosalind Meier
From: Visitor Services, Marlowe Springs Museum of Natural History
Date: November 20
Subject: Welcome to the museum

Dear Ms. Meier,

Welcome, and thank you for your additional gift to the Education Fund. Your membership cards will be mailed within ten days. Until they arrive, please show this e-mail at the entrance, and you will be admitted without delay.

In reply to your comment, Giants of the Cretaceous opens on December 6 and will run until the end of April. I am sorry to say that tickets for opening day are sold out, but plenty remain for December 7 onward. The regular price is $24 per adult. Members may book now at the member rate on our Web site; booking opens to the general public on November 27.

You may also be interested to know that members at your level can upgrade to a Family membership at any time by paying only the difference in price. Several members do so each year when relatives come to visit.

Warm regards,
Jerome Baptiste
Visitor Services', 'NONE', '["She works as a volunteer at the museum.", "She previously held a Family membership.", "She learned of the museum from a colleague.", "She wishes to cancel an optional donation."]', 'C', 1, '[세부 사항] 정답 (C). "How did you hear about us? From a coworker who volunteers at the museum"에서 직장 동료에게 들었음을 알 수 있다. 오답: (A) 자원봉사를 하는 사람은 동료다. (B) 이전 회원 이력은 없다. (D) 25달러를 추가로 기부했지 취소하려는 것이 아니다.', 198),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'What does Mr. Baptiste state about Giants of the Cretaceous?', 'Questions 196-200 refer to the following Web page, form, and e-mail.

[Web page]
Marlowe Springs Museum of Natural History
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
Marlowe Springs Museum of Natural History
Membership Application

Date: November 18
Name: Rosalind Meier
Address: 77 Fernhill Road, Tennby
Telephone: 555-0163
Membership level: Dual
Second cardholder: Anton Meier
Payment: $85, credit card
Additional donation (optional): $25 to the Education Fund
How did you hear about us? From a coworker who volunteers at the museum
Comments: We would like to attend the opening day of the dinosaur exhibition if tickets are still available.

────────────────────

[E-mail]
To: Rosalind Meier
From: Visitor Services, Marlowe Springs Museum of Natural History
Date: November 20
Subject: Welcome to the museum

Dear Ms. Meier,

Welcome, and thank you for your additional gift to the Education Fund. Your membership cards will be mailed within ten days. Until they arrive, please show this e-mail at the entrance, and you will be admitted without delay.

In reply to your comment, Giants of the Cretaceous opens on December 6 and will run until the end of April. I am sorry to say that tickets for opening day are sold out, but plenty remain for December 7 onward. The regular price is $24 per adult. Members may book now at the member rate on our Web site; booking opens to the general public on November 27.

You may also be interested to know that members at your level can upgrade to a Family membership at any time by paying only the difference in price. Several members do so each year when relatives come to visit.

Warm regards,
Jerome Baptiste
Visitor Services', 'NONE', '["It will open to visitors on November 27.", "It is free of charge for museum members.", "Its tickets are sold only at the entrance.", "Its first day is already fully booked."]', 'D', 1, '[세부 사항] 정답 (D). "tickets for opening day are sold out"을 ''첫날은 이미 예약이 다 찼다''로 바꿔 말했다. 오답: (A) 11월 27일은 일반 예매 시작일이고 개막은 12월 6일이다. (B) 회원도 회원가(반값)를 낸다. (C) 웹 사이트에서 예매할 수 있다고 했다.', 199),
(@toeic_rc02_exam_id, 'READING', 'TOEIC_P7', 'How much would Ms. Meier pay to change her membership as Mr. Baptiste describes?', 'Questions 196-200 refer to the following Web page, form, and e-mail.

[Web page]
Marlowe Springs Museum of Natural History
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
Marlowe Springs Museum of Natural History
Membership Application

Date: November 18
Name: Rosalind Meier
Address: 77 Fernhill Road, Tennby
Telephone: 555-0163
Membership level: Dual
Second cardholder: Anton Meier
Payment: $85, credit card
Additional donation (optional): $25 to the Education Fund
How did you hear about us? From a coworker who volunteers at the museum
Comments: We would like to attend the opening day of the dinosaur exhibition if tickets are still available.

────────────────────

[E-mail]
To: Rosalind Meier
From: Visitor Services, Marlowe Springs Museum of Natural History
Date: November 20
Subject: Welcome to the museum

Dear Ms. Meier,

Welcome, and thank you for your additional gift to the Education Fund. Your membership cards will be mailed within ten days. Until they arrive, please show this e-mail at the entrance, and you will be admitted without delay.

In reply to your comment, Giants of the Cretaceous opens on December 6 and will run until the end of April. I am sorry to say that tickets for opening day are sold out, but plenty remain for December 7 onward. The regular price is $24 per adult. Members may book now at the member rate on our Web site; booking opens to the general public on November 27.

You may also be interested to know that members at your level can upgrade to a Family membership at any time by paying only the difference in price. Several members do so each year when relatives come to visit.

Warm regards,
Jerome Baptiste
Visitor Services', 'NONE', '["$25", "$55", "$85", "$110"]', 'A', 1, '[연계(문서1+2+3)] 정답 (A). 이메일은 Family로 올릴 때 "paying only the difference in price"라고 했고, 신청서의 등급은 Dual($85), 웹 페이지의 Family는 $110이므로 110 − 85 = 25달러다. 오답: (B) Individual 요금, (C) 이미 낸 Dual 요금, (D) Family 전체 요금이다.', 200);
