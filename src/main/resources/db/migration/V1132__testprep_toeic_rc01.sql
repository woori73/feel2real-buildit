-- TestPrep TOEIC 연습 세트 TP-TOEIC-RC-01 — TOEIC Reading Practice 01 — Part 5·6·7 (독해 추가 세트 1)
-- 문항 100개, F2R 자체 제작. ETS 공식 문항·지문과 후기 속 복원 문항은 쓰지 않았다(출제 포인트·유형·길이·상황만 참고).
-- 스키마는 바꾸지 않는다: 기존 tp_exams / tp_questions 에 kind='TOEIC' 행만 추가한다.
-- section: LISTENING / READING, task_type: TOEIC_P2 ~ TOEIC_P7, order_no = 실제 TOEIC 문항 번호(7~200).
-- 듣기(TOEIC_P2~P4)는 audio_mode='TTS' — 화면이 body_text(대본)를 브라우저 음성으로 읽는다. Part 1(사진)은 없다.
-- 문항 원본·생성기: naesin-workspace/testprep/toeic (tools/build_sql.py). 설계: docs/design/testprep-toeic.md
-- exam_id는 AUTO_INCREMENT에 의존하지 않고 exam_code로 회수한다.

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES
('TP-TOEIC-RC-01', 'TOEIC', 'TOEIC Reading Practice 01 — Part 5·6·7 (독해 추가 세트 1)', 'Part 5 (30) · Part 6 (16) · Part 7 (54) · 100 questions · 75 minutes', 'A full-length TOEIC Reading section written by F2R. Grammar points, vocabulary and Part 7 situations follow recent test-taker reports. Scores are F2R internal estimates, not official ETS TOEIC scores. 최근 시험 후기의 문법·어휘·Part 7 상황을 반영한 독해 세트입니다.', NULL, 4500, 'PUBLISHED', 'system');

SET @toeic_rc01_exam_id = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-TOEIC-RC-01');

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'The Brixfield Ledger is the most ------- circulated daily newspaper in the region, reaching more than 80,000 households each morning.', NULL, 'NONE', '["widely", "wide", "widen", "width"]', 'A', 1, '[문법-품사(부사: 과거분사 수식)] 정답 (A). 빈칸은 뒤의 과거분사 circulated(배포되는)를 꾸미는 자리이므로 부사 widely가 필요하다. the most widely circulated newspaper는 ''가장 널리 배포되는 신문''이다. 오답: (B) wide는 형용사로 분사를 꾸미지 못하고 물리적 폭을 뜻한다. (C) widen은 동사, (D) width는 명사라 이 자리에 올 수 없다.', 101),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Calder Point officials chose Voltway chargers for the new parking garage because the units have proved ------- even in extremely cold weather.', NULL, 'NONE', '["spacious", "voluntary", "reliable", "absent"]', 'C', 1, '[어휘-형용사(상황 어휘)] 정답 (C). 혹한에서도 문제없이 작동했다는 것이 선택의 이유이므로 ''믿을 만한, 안정적으로 작동하는''이라는 뜻의 reliable이 맞다. 오답: (A) spacious는 공간이 넓다는 뜻이라 충전기의 작동과 무관하다. (B) voluntary는 ''자발적인'', (D) absent는 ''없는''이라는 뜻으로 선택한 이유가 되지 못한다.', 102),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'All cargo handlers at Calder Point Air Freight must comply ------- the revised labeling rules for temperature-sensitive shipments starting next month.', NULL, 'NONE', '["to", "for", "on", "with"]', 'D', 1, '[전치사(동사+전치사 결합)] 정답 (D). ''규칙·요구를 따르다''는 comply with로 쓴다. 동사와 전치사를 한 묶음으로 외워야 하는 문제이다. 오답: (A) to, (B) for, (C) on은 comply 뒤에서 따라야 할 규칙을 연결하는 전치사로 쓰지 않는다.', 103),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'A description of the allergens found in Harlow Catering''s boxed lunches ------- printed on the bottom of every container.', NULL, 'NONE', '["are", "is", "have been", "being"]', 'B', 1, '[문법-수일치(주어의 중심 명사)] 정답 (B). 주어의 중심 명사는 단수 A description이고, of the allergens found in ... boxed lunches는 모두 수식어이다. 따라서 단수동사 is가 맞다. 오답: (A) are와 (C) have been은 복수동사라 가까운 복수명사 lunches에 속은 답이다. (D) being은 혼자서 문장의 동사가 될 수 없다.', 104),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Furniture designs cannot be sent to the Brixfield factory floor without the ------- of the lead engineer, Ms. Okafor.', NULL, 'NONE', '["approval", "approve", "approved", "approvingly"]', 'A', 1, '[문법-품사(명사 자리)] 정답 (A). 관사 the와 전치사 of 사이는 명사 자리이므로 approval(승인)이 맞다. without the approval of는 ''~의 승인 없이''이다. 오답: (B) approve는 동사, (C) approved는 동사의 과거형·과거분사, (D) approvingly는 부사라서 the 뒤에서 명사구의 중심이 될 수 없다.', 105),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'To thank its longtime members, Corefit Gym will ------- the annual maintenance fee for anyone who renews before March 31.', NULL, 'NONE', '["waive", "retain", "estimate", "impose"]', 'A', 1, '[어휘-동사(상황 어휘)] 정답 (A). 오래된 회원에게 감사하는 뜻으로 하는 일이므로 요금을 ''면제하다''라는 waive가 맞다. waive a fee로 함께 외운다. 오답: (B) retain은 ''계속 보유하다'', (C) estimate는 ''추산하다'', (D) impose는 ''부과하다''라는 뜻이라 감사의 표시가 되지 못한다.', 106),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Once an expense claim has been processed, a detailed statement ------- to the employee by the accounting office within five business days.', NULL, 'NONE', '["sends", "will send", "is sent", "sending"]', 'C', 1, '[문법-태(수동태)] 정답 (C). 주어 a detailed statement는 보내는 주체가 아니라 보내지는 대상이고, 뒤에 목적어가 없으며 by the accounting office가 행위자를 나타낸다. 따라서 수동태 is sent가 맞다. 오답: (A) sends와 (B) will send는 능동형이라 목적어가 필요하다. (D) sending은 혼자서 문장의 동사가 될 수 없다.', 107),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Requests to change a catering order must be submitted ------- writing at least 72 hours before the scheduled event.', NULL, 'NONE', '["in", "as", "to", "of"]', 'A', 1, '[전치사(고정 표현)] 정답 (A). ''서면으로''라는 방식은 in writing으로 표현한다. 오답: (B) as writing, (C) to writing, (D) of writing은 ''서면으로''라는 뜻의 표현이 아니며 submitted 뒤에서 방식을 나타내지 못한다.', 108),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Before approving the kitchen renovation, Ms. Varga asked three contractors to submit a written ------- of the total cost of labor and materials.', NULL, 'NONE', '["invoice", "receipt", "estimate", "refund"]', 'C', 1, '[어휘-명사(상황 어휘)] 정답 (C). 공사를 승인하기 전에 세 업체에서 받는 것은 예상 비용을 알려 주는 견적(estimate)이다. 오답: (A) invoice는 일을 한 뒤 지급할 금액을 청구하는 서류, (B) receipt는 지급한 뒤 받는 영수증, (D) refund는 낸 돈을 돌려주는 환불이라 승인 전 단계와 맞지 않는다.', 109),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'The brochure ------- to new patients explains how Medvane Pharmacy stores and delivers prescription medications.', NULL, 'NONE', '["distributing", "distributed", "distributes", "distribute"]', 'B', 1, '[문법-분사(능동·수동)] 정답 (B). 문장의 동사는 explains이므로 빈칸은 The brochure를 뒤에서 꾸미는 분사 자리이다. 안내 책자는 배포되는 대상이므로 과거분사 distributed가 맞다. 오답: (A) distributing은 책자가 무언가를 배포한다는 능동 관계가 되고 목적어도 없다. (C) distributes와 (D) distribute는 동사를 하나 더 만든다.', 110),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Imported cargo will not be released from the Calder Point terminal ------- all customs forms have been verified by an inspector.', NULL, 'NONE', '["whereas", "so that", "until", "as if"]', 'C', 1, '[접속사(시간·부정 표현과의 결합)] 정답 (C). 앞 절의 will not be released와 결합해 ''서류가 확인될 때까지는 내보내지 않는다''는 시간 관계가 되므로 until이 맞다(not ... until). 오답: (A) whereas는 대조, (B) so that은 목적, (D) as if는 ''마치 ~인 것처럼''이라 두 절의 관계와 맞지 않는다.', 111),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Employees who have worked at Tarrow Furniture for at least one year are ------- for the company''s tuition assistance program.', NULL, 'NONE', '["capable", "valid", "eligible", "familiar"]', 'C', 1, '[어휘-형용사(형용사+전치사 결합)] 정답 (C). 1년 이상 근무라는 조건을 채운 직원이 지원 제도의 ''대상 자격이 된다''는 뜻이므로 eligible for가 맞다. 오답: (A) capable은 of와 쓰고 능력을 뜻한다. (B) valid는 쿠폰·서류 등이 유효하다는 뜻이라 사람을 주어로 쓰지 않는다. (D) familiar는 with와 쓰고 ''잘 아는''이라는 뜻이다.', 112),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'The new scheduling software has made the transition between day and night shifts remarkably ------- for warehouse supervisors.', NULL, 'NONE', '["smoothly", "smooth", "smoothness", "smooths"]', 'B', 1, '[문법-품사(목적격 보어 형용사)] 정답 (B). make + 목적어(the transition ...) + 목적격 보어 구문이다. 목적어가 어떤 상태가 되는지 설명하는 형용사 smooth가 필요하고, 부사 remarkably가 그 형용사를 꾸민다. 오답: (A) smoothly는 부사라 보어가 될 수 없다. (C) smoothness는 명사인데 부사 remarkably의 수식을 받지 못한다. (D) smooths는 동사이다.', 113),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'As stated in the travel policy, Dunmore & Pryce will ------- staff for parking fees, provided that original receipts are submitted within 30 days.', NULL, 'NONE', '["spend", "deposit", "afford", "reimburse"]', 'D', 1, '[어휘-동사(동사 문형)] 정답 (D). ''사람 + for + 비용''이 이어지는 문형이고 영수증을 내면 쓴 돈을 돌려준다는 뜻이므로 reimburse(reimburse 사람 for 비용)가 맞다. 오답: (A) spend는 돈·시간을 목적어로 하고 on과 쓴다. (B) deposit은 돈을 맡기거나 입금한다는 뜻, (C) afford는 ''~할 여유가 있다''는 뜻이라 사람을 목적어로 하는 이 문형에 맞지 않는다.', 114),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', '------- interested in joining the weekend cycling class should sign up at the Corefit reception desk by Friday.', NULL, 'NONE', '["Those", "They", "Whoever", "Them"]', 'A', 1, '[문법-대명사(those + 분사)] 정답 (A). 문장의 동사는 should sign up이고, 빈칸은 뒤의 분사구 interested in ...의 수식을 받는 주어 자리이다. ''~하는 사람들''은 those + 분사로 쓴다. 오답: (B) They는 뒤에서 분사구의 수식을 받지 못한다. (C) Whoever는 절을 이끌어야 하는데 뒤에 동사(is)가 없다. (D) Them은 목적격이라 주어가 될 수 없다.', 115),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Opinions among the Brixfield city council members were ------- divided over the proposal to build charging stations in the historic district.', NULL, 'NONE', '["wisely", "sharply", "promptly", "fluently"]', 'B', 1, '[어휘-부사(연어)] 정답 (B). 의견이 ''첨예하게 갈리다''는 be sharply divided로 쓴다. 오답: (A) wisely는 행동을 현명하게 했다는 뜻, (C) promptly는 ''지체 없이'', (D) fluently는 말을 ''유창하게''라는 뜻이라 의견이 갈린 정도를 나타내지 못한다.', 116),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'At present, Ms. Lindqvist ------- every expense report that was filed before the accounting software was replaced last spring.', NULL, 'NONE', '["reexamined", "had reexamined", "is reexamining", "reexamine"]', 'C', 1, '[문법-시제(주절과 관계절의 시간 표현)] 정답 (C). 주절의 시간 표현은 At present이므로 현재진행형 is reexamining이 맞다. last spring과 과거형 was filed·was replaced는 관계절 안의 일(보고서가 제출된 때)을 말할 뿐이다. 오답: (A) reexamined와 (B) had reexamined는 last spring만 보고 고른 답으로 At present와 맞지 않는다. (D) reexamine은 3인칭 단수 주어와 수가 맞지 않는다.', 117),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', '------- the sharp rise in fuel prices, Calder Point Air Freight has decided to add a temporary surcharge to all overnight deliveries.', NULL, 'NONE', '["Given", "Nevertheless", "Although", "Provided"]', 'A', 1, '[전치사(given과 접속사·접속부사 구별)] 정답 (A). 빈칸 뒤는 명사구(the sharp rise in fuel prices)이고 그것이 결정의 근거이므로 ''~을 고려할 때''라는 전치사 Given이 맞다. 오답: (B) Nevertheless는 접속부사라 명사구를 이끌지 못한다. (C) Although와 (D) Provided는 접속사라 뒤에 주어와 동사가 있는 절이 와야 한다.', 118),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Corefit Gym hires additional instructors each summer to handle the ------- increase in demand for outdoor fitness classes.', NULL, 'NONE', '["season", "seasonal", "seasonally", "seasons"]', 'B', 1, '[문법-품사(명사를 꾸미는 형용사)] 정답 (B). 관사 the와 명사 increase(증가) 사이에서 명사를 꾸미는 형용사 자리이므로 seasonal(계절에 따른)이 맞다. 여기서 increase는 뒤에 in demand가 이어지는 명사이다. 오답: (A) season과 (D) seasons는 명사라 increase를 꾸미는 말로 쓰지 않는다. (C) seasonally는 부사라 명사를 꾸미지 못한다.', 119),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'The owners of Saffron Table Catering plan to have their main kitchen ------- before the busy wedding season begins in May.', NULL, 'NONE', '["renovated", "renovating", "renovate", "renovates"]', 'A', 1, '[문법-준동사(have + 목적어 + 과거분사)] 정답 (A). have + 사물 + 과거분사는 ''다른 사람에게 맡겨 ~되게 하다''라는 구문이다. 주방은 개조되는 대상이므로 renovated가 맞다. 오답: (B) renovating과 (C) renovate는 주방이 스스로 개조한다는 능동 관계가 되고 목적어도 없다. (D) renovates는 have 뒤 목적격 보어 자리에 올 수 없는 정동사 형태이다.', 120);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'The delivery date for the conference tables is still -------, as Tarrow Furniture is waiting for its supplier to confirm the shipment of oak panels.', NULL, 'NONE', '["punctual", "mutual", "durable", "tentative"]', 'D', 1, '[어휘-형용사(상황 어휘)] 정답 (D). 공급업체의 확인을 아직 기다리고 있으므로 납품일은 ''잠정적인'' 상태이다. tentative가 맞다. 오답: (A) punctual은 사람·교통편이 시간을 잘 지킨다는 뜻, (B) mutual은 ''상호의'', (C) durable은 물건이 ''내구성이 좋은''이라는 뜻이라 아직 확정되지 않은 날짜를 설명하지 못한다.', 121),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'The warranty on Voltway home chargers provides coverage ------- defects in materials and workmanship for a period of three years.', NULL, 'NONE', '["through", "among", "onto", "against"]', 'D', 1, '[전치사(명사+전치사 결합)] 정답 (D). 보증이 결함에 ''대비한'' 보호를 제공한다는 뜻으로 coverage against + 위험을 쓴다. 오답: (A) through는 수단·경로, (B) among은 집단의 내부, (C) onto는 표면 위로의 이동을 나타내어 보호하려는 위험을 연결하지 못한다.', 122),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Corefit Gym will close for a two-week period in August, ------- which the swimming pool and locker rooms will be completely renovated.', NULL, 'NONE', '["into", "during", "between", "toward"]', 'B', 1, '[문법-관계사(전치사 + which)] 정답 (B). which의 선행사는 a two-week period이고, ''그 기간 동안에'' 개조된다는 뜻이므로 during which가 맞다. 오답: (A) into와 (D) toward는 방향을 나타내고, (C) between은 둘 사이를 나타내어 하나의 기간 안에서 일어나는 일을 연결하지 못한다.', 123),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Reporters at the Brixfield Ledger must send their finished articles to the copy desk ------- 6:00 P.M. so that the pages can be printed overnight.', NULL, 'NONE', '["until", "by", "within", "since"]', 'B', 1, '[전치사(by와 until)] 정답 (B). 기사를 보내는 일은 한 번에 끝나는 동작이고 6시는 그 일을 마쳐야 하는 기한이므로 by가 맞다. 오답: (A) until은 어떤 상태가 그때까지 계속된다는 뜻이라 send와 맞지 않는다. (C) within은 뒤에 기간(예: two hours)이 온다. (D) since는 시작 시점을 나타내며 완료시제와 쓴다.', 124),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'The banquet hall at the Calder Point Marina can ------- up to 250 guests, making it suitable for large corporate receptions.', NULL, 'NONE', '["accommodate", "attend", "reserve", "expand"]', 'A', 1, '[어휘-동사(상황 어휘)] 정답 (A). 주어가 연회장이고 목적어가 인원수이므로 ''수용하다''라는 accommodate가 맞다. 오답: (B) attend는 사람이 행사에 참석한다는 뜻이고, (C) reserve는 사람이 자리·방을 예약한다는 뜻이라 연회장이 주어가 될 수 없다. (D) expand는 ''확장하다''라는 뜻으로 손님을 목적어로 하지 않는다.', 125),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', '------- the shipment arrived a day earlier, the pharmacy would not have run out of the allergy medication.', NULL, 'NONE', '["Should", "Were", "Has", "Had"]', 'D', 1, '[문법-도치(가정법 과거완료의 if 생략)] 정답 (D). 주절이 would not have run out이므로 과거 사실의 반대를 가정하는 가정법 과거완료이다. If the shipment had arrived에서 if를 생략하면 Had the shipment arrived로 도치된다. 오답: (A) Should와 (B) Were 뒤에는 과거분사 arrived가 이어질 수 없다(Should + 주어 + 동사원형, Were + 주어 + to부정사). (C) Has는 가정법 도치에 쓰지 않는다.', 126),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Halberg Motors will ------- open twelve fast-charging stations along the coastal highway by the end of next year.', NULL, 'NONE', '["report", "reportedly", "reported", "reporting"]', 'B', 1, '[문법-품사(조동사와 동사 사이의 부사)] 정답 (B). 조동사 will과 본동사 open 사이는 부사 자리이다. reportedly는 ''전해진 바에 따르면''이라는 부사이다. 오답: (A) report를 넣으면 will 뒤에 동사원형이 둘이 된다. (C) reported와 (D) reporting은 분사라 조동사와 본동사 사이에서 동사를 꾸미지 못한다.', 127),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Laboratory tests showed that the effect of the new coating on the tablets'' shelf life was -------, so Medvane decided not to change its production process.', NULL, 'NONE', '["deliberate", "generous", "attentive", "negligible"]', 'D', 1, '[어휘-형용사] 정답 (D). 효과가 ''무시할 만큼 작아서'' 공정을 바꾸지 않기로 했다는 흐름이므로 negligible이 맞다. 오답: (A) deliberate는 ''의도적인'', (B) generous는 사람·금액이 ''후한'', (C) attentive는 사람이 ''주의 깊은''이라는 뜻이라 공정을 바꾸지 않기로 한 이유가 되지 못한다.', 128),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Since merging with a regional rival, the Brixfield Ledger has offered its subscribers a ------- variety of local sports coverage than ever before.', NULL, 'NONE', '["greatly", "great", "greater", "greatest"]', 'C', 1, '[문법-비교(비교급 형용사 + 명사)] 정답 (C). 뒤의 than ever before가 비교의 단서이므로 명사 variety를 꾸미는 비교급 형용사 greater가 맞다(a greater variety of). 오답: (A) greatly는 부사라 명사를 꾸미지 못한다. (B) great는 원급이라 than과 호응하지 않는다. (D) greatest는 최상급이라 관사 a, than과 함께 쓰지 않는다.', 129),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Customers enrolled in the Halberg Motors loyalty program receive ------- rates at all of the company''s fast-charging stations.', NULL, 'NONE', '["preferable", "apparent", "cautious", "preferential"]', 'D', 1, '[어휘-형용사(형태가 비슷한 형용사)] 정답 (D). 회원에게만 주는 ''우대 요금''은 preferential rates이다. preferential은 특정 대상에게 우선권·혜택을 준다는 뜻이다. 오답: (A) preferable은 둘을 비교했을 때 ''더 바람직한''이라는 뜻으로 보통 보어 자리에 쓰며 우대의 뜻이 없다. (B) apparent는 ''명백한'', (C) cautious는 ''조심스러운''이라 요금을 꾸미지 못한다.', 130),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (131).', 'Questions 131-134 refer to the following e-mail.

To: Renata Solberg
From: Idris Kamau
Date: May 6
Subject: Your June 14 luncheon

Dear Ms. Solberg,

Thank you for choosing Saffron Table Catering for your firm''s client luncheon on June 14. I am writing to confirm the (131) ------- we discussed by phone yesterday. Our staff will arrive at your office at 10:30 A.M. to set up the buffet for 60 guests. (132) -------. A revised menu is attached for your review.

As you requested, we have added a dessert course to the menu. (133) -------, the price per guest remains the same as in the original quote.

Please sign the attached agreement and return it to (134) ------- by May 20. We will then send your final invoice.

Sincerely,
Idris Kamau', 'NONE', '["complaints", "vacancies", "arrangements", "discounts"]', 'C', 1, '[Part6-어휘(문맥)] 정답 (C). 빈칸 뒤 문장들이 도착 시각, 뷔페 준비, 인원 60명 같은 행사 준비 사항을 하나씩 확인하고 있으므로 ''준비·합의 사항''이라는 arrangements가 맞다. 오답: (A) complaints(불만)와 (B) vacancies(공석)는 뒤의 내용과 무관하다. (D) discounts(할인)는 뒤에서 1인당 가격이 처음 견적과 같다고 했으므로 맞지 않는다.', 131),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (132).', 'Questions 131-134 refer to the following e-mail.

To: Renata Solberg
From: Idris Kamau
Date: May 6
Subject: Your June 14 luncheon

Dear Ms. Solberg,

Thank you for choosing Saffron Table Catering for your firm''s client luncheon on June 14. I am writing to confirm the (131) ------- we discussed by phone yesterday. Our staff will arrive at your office at 10:30 A.M. to set up the buffet for 60 guests. (132) -------. A revised menu is attached for your review.

As you requested, we have added a dessert course to the menu. (133) -------, the price per guest remains the same as in the original quote.

Please sign the attached agreement and return it to (134) ------- by May 20. We will then send your final invoice.

Sincerely,
Idris Kamau', 'NONE', '["We are sorry that you were not satisfied with the meal.", "The position requires two years of kitchen experience.", "Your payment was received on the day of the event.", "Lunch service will begin at noon and end at 2:00 P.M."]', 'D', 1, '[Part6-문장삽입] 정답 (D). 앞 문장이 직원 도착 시각과 뷔페 준비를 말하므로, 이어서 식사 제공 시간을 알리는 (D)가 행사 당일 일정의 흐름을 잇는다. 오답: (A) 행사는 아직 열리지 않았으므로 식사에 대한 사과는 맞지 않는다. (B) 채용 조건은 이 글의 소재가 아니다. (C) 행사일은 6월 14일인데 글은 5월 6일에 쓰였고, 끝에서 청구서를 앞으로 보내겠다고 했으므로 이미 대금을 받았다는 말은 모순이다.', 132),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (133).', 'Questions 131-134 refer to the following e-mail.

To: Renata Solberg
From: Idris Kamau
Date: May 6
Subject: Your June 14 luncheon

Dear Ms. Solberg,

Thank you for choosing Saffron Table Catering for your firm''s client luncheon on June 14. I am writing to confirm the (131) ------- we discussed by phone yesterday. Our staff will arrive at your office at 10:30 A.M. to set up the buffet for 60 guests. (132) -------. A revised menu is attached for your review.

As you requested, we have added a dessert course to the menu. (133) -------, the price per guest remains the same as in the original quote.

Please sign the attached agreement and return it to (134) ------- by May 20. We will then send your final invoice.

Sincerely,
Idris Kamau', 'NONE', '["For example", "Otherwise", "Even so", "As a result"]', 'C', 1, '[Part6-연결어] 정답 (C). 앞 문장은 디저트 코스를 추가했다는 내용이고 뒤 문장은 그런데도 1인당 가격이 그대로라는 내용이므로 ''그렇더라도''라는 양보의 Even so가 맞다. 오답: (A) For example은 예시, (B) Otherwise는 ''그렇지 않으면'', (D) As a result는 결과를 나타내는데, 메뉴를 늘린 결과로 가격이 그대로라는 인과 관계는 성립하지 않는다.', 133),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (134).', 'Questions 131-134 refer to the following e-mail.

To: Renata Solberg
From: Idris Kamau
Date: May 6
Subject: Your June 14 luncheon

Dear Ms. Solberg,

Thank you for choosing Saffron Table Catering for your firm''s client luncheon on June 14. I am writing to confirm the (131) ------- we discussed by phone yesterday. Our staff will arrive at your office at 10:30 A.M. to set up the buffet for 60 guests. (132) -------. A revised menu is attached for your review.

As you requested, we have added a dessert course to the menu. (133) -------, the price per guest remains the same as in the original quote.

Please sign the attached agreement and return it to (134) ------- by May 20. We will then send your final invoice.

Sincerely,
Idris Kamau', 'NONE', '["it", "him", "yourself", "me"]', 'D', 1, '[Part6-대명사(지시 대상)] 정답 (D). 이 이메일을 쓴 사람은 I am writing이라고 한 Idris Kamau이고, 서명한 계약서를 돌려받을 사람도 발신자 자신이므로 me가 맞다. 오답: (A) it은 계약서 자체를 가리키게 되어 뜻이 통하지 않는다. (B) him이 가리킬 남성 제3자가 글에 없다. (C) yourself는 받는 사람이 자기 자신에게 돌려보낸다는 뜻이 되어 맞지 않는다.', 134),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (135).', 'Questions 135-138 refer to the following notice.

Notice to Corefit Members

The swimming pool at Corefit Brixfield (135) ------- for maintenance from August 3 to August 16. During this period, the pool''s filtration system will be replaced and the adjoining locker rooms will be repainted. All other areas of the facility, including the weight room and the group exercise studios, will remain open.

We understand that this (136) ------- is inconvenient for members who swim regularly. (137) -------. Simply show your membership card at the front desk of that location.

Membership fees for August will not be reduced. (138) -------, members on the swim-only plan may suspend their memberships for the month at no charge. To do so, please speak with a staff member by July 31.', 'NONE', '["was closed", "will be closed", "has been closing", "to be closed"]', 'B', 1, '[Part6-문법(시제·문맥)] 정답 (B). 다음 문장에서 이 기간에 여과 장치가 교체될 것이고(will be replaced) 다른 구역은 계속 열려 있을 것(will remain open)이라고 하며, 끝에서 7월 31일까지 직원에게 말하라고 하므로 휴장은 앞으로의 일이다. 미래 수동형 will be closed가 맞다. 오답: (A) was closed는 과거라 뒤 문장들의 미래 시제와 맞지 않는다. (C) has been closing은 능동 진행형이라 수영장이 무언가를 닫는다는 뜻이 된다. (D) to be closed는 문장의 동사가 될 수 없다.', 135),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (136).', 'Questions 135-138 refer to the following notice.

Notice to Corefit Members

The swimming pool at Corefit Brixfield (135) ------- for maintenance from August 3 to August 16. During this period, the pool''s filtration system will be replaced and the adjoining locker rooms will be repainted. All other areas of the facility, including the weight room and the group exercise studios, will remain open.

We understand that this (136) ------- is inconvenient for members who swim regularly. (137) -------. Simply show your membership card at the front desk of that location.

Membership fees for August will not be reduced. (138) -------, members on the swim-only plan may suspend their memberships for the month at no charge. To do so, please speak with a staff member by July 31.', 'NONE', '["increase", "closure", "contest", "relocation"]', 'B', 1, '[Part6-어휘(문맥)] 정답 (B). this가 가리키는 것은 첫 단락에서 알린 수영장 휴장이므로 closure(폐쇄, 휴장)가 맞다. 오답: (A) increase(인상)는 뒤에서 회비가 달라지지 않는다고 했으므로 맞지 않는다. (C) contest(대회)는 글에 나오지 않는다. (D) relocation(이전)은 수영장을 옮기는 것이 아니라 보수하는 것이므로 맞지 않는다.', 136),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (137).', 'Questions 135-138 refer to the following notice.

Notice to Corefit Members

The swimming pool at Corefit Brixfield (135) ------- for maintenance from August 3 to August 16. During this period, the pool''s filtration system will be replaced and the adjoining locker rooms will be repainted. All other areas of the facility, including the weight room and the group exercise studios, will remain open.

We understand that this (136) ------- is inconvenient for members who swim regularly. (137) -------. Simply show your membership card at the front desk of that location.

Membership fees for August will not be reduced. (138) -------, members on the swim-only plan may suspend their memberships for the month at no charge. To do so, please speak with a staff member by July 31.', 'NONE', '["Members may use the pool at our Calder Point branch free of charge during these two weeks.", "The new filtration system was installed in the pool at the beginning of last year.", "Swimming lessons for children are held in the main pool every Saturday morning.", "Please remember to bring your own towel whenever you use the weight room."]', 'A', 1, '[Part6-문장삽입] 정답 (A). 앞 문장이 불편을 이해한다고 했으므로 대안을 제시하는 문장이 이어져야 하고, 뒤 문장의 that location이 가리킬 장소(Calder Point 지점)가 빈칸에 나와야 한다. 오답: (B) 여과 장치는 이번에 교체될 예정이므로 작년에 설치했다는 말은 흐름에 맞지 않고 장소도 나오지 않는다. (C) 어린이 강습, (D) 수건 안내는 that location이 가리킬 장소를 주지 못한다.', 137),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (138).', 'Questions 135-138 refer to the following notice.

Notice to Corefit Members

The swimming pool at Corefit Brixfield (135) ------- for maintenance from August 3 to August 16. During this period, the pool''s filtration system will be replaced and the adjoining locker rooms will be repainted. All other areas of the facility, including the weight room and the group exercise studios, will remain open.

We understand that this (136) ------- is inconvenient for members who swim regularly. (137) -------. Simply show your membership card at the front desk of that location.

Membership fees for August will not be reduced. (138) -------, members on the swim-only plan may suspend their memberships for the month at no charge. To do so, please speak with a staff member by July 31.', 'NONE', '["Therefore", "However", "In other words", "For instance"]', 'B', 1, '[Part6-연결어] 정답 (B). 앞 문장은 8월 회비를 깎아 주지 않는다는 내용이고, 뒤 문장은 수영 전용 회원은 무료로 회원권을 정지할 수 있다는 예외이므로 대조의 However가 맞다. 오답: (A) Therefore는 결과, (C) In other words는 바꿔 말하기, (D) For instance는 예시를 나타내는데, 뒤 문장은 앞 문장의 결과·풀이·예시가 아니다.', 138),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (139).', 'Questions 139-142 refer to the following article.

BRIXFIELD (April 2)—Voltway Energy announced yesterday that it will install 40 fast-charging stations in Brixfield and Calder Point by the end of the year. The company (139) ------- only six stations in the region, all of them near the airport. The expansion will bring its total to 46.

According to spokesperson Lena Achterberg, the new stations will be built in the parking areas of supermarkets and shopping centers. "(140) ------- can charge their vehicles while they shop," she said. "In this way, charging becomes part of a normal errand."

The project is expected to cost $9 million. (141) -------. Voltway will pay the remainder itself.

Mayor Paul Eze said that the lack of public chargers has (142) ------- many residents from buying electric cars.', 'NONE', '["operated", "will operate", "operates", "operating"]', 'C', 1, '[Part6-문법(시제·문맥)] 정답 (C). 앞 문장은 앞으로 40곳을 설치한다는 계획이고 뒤 문장은 그 결과 합계가 46곳이 된다고 하므로, 6곳은 지금 운영 중인 수이다. 현재형 operates가 맞다. 오답: (A) operated는 지금은 운영하지 않는다는 뜻이 되어 합계 46과 맞지 않는다. (B) will operate는 40곳을 새로 짓는다는 계획과 모순된다. (D) operating은 문장의 동사가 될 수 없다.', 139),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (140).', 'Questions 139-142 refer to the following article.

BRIXFIELD (April 2)—Voltway Energy announced yesterday that it will install 40 fast-charging stations in Brixfield and Calder Point by the end of the year. The company (139) ------- only six stations in the region, all of them near the airport. The expansion will bring its total to 46.

According to spokesperson Lena Achterberg, the new stations will be built in the parking areas of supermarkets and shopping centers. "(140) ------- can charge their vehicles while they shop," she said. "In this way, charging becomes part of a normal errand."

The project is expected to cost $9 million. (141) -------. Voltway will pay the remainder itself.

Mayor Paul Eze said that the lack of public chargers has (142) ------- many residents from buying electric cars.', 'NONE', '["Employers", "Passengers", "Suppliers", "Drivers"]', 'D', 1, '[Part6-어휘(문맥)] 정답 (D). 앞 문장에서 충전소가 슈퍼마켓과 쇼핑센터 주차장에 들어선다고 했고, 빈칸의 사람들은 장을 보는 동안 자기 차를 충전하므로 Drivers(운전자)가 맞다. 오답: (A) Employers(고용주), (C) Suppliers(납품업체)는 글에 나오지 않는다. (B) Passengers(승객)는 자기 차량을 충전하는 사람이 아니다.', 140);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (141).', 'Questions 139-142 refer to the following article.

BRIXFIELD (April 2)—Voltway Energy announced yesterday that it will install 40 fast-charging stations in Brixfield and Calder Point by the end of the year. The company (139) ------- only six stations in the region, all of them near the airport. The expansion will bring its total to 46.

According to spokesperson Lena Achterberg, the new stations will be built in the parking areas of supermarkets and shopping centers. "(140) ------- can charge their vehicles while they shop," she said. "In this way, charging becomes part of a normal errand."

The project is expected to cost $9 million. (141) -------. Voltway will pay the remainder itself.

Mayor Paul Eze said that the lack of public chargers has (142) ------- many residents from buying electric cars.', 'NONE', '["About one-third of that amount will come from a regional transportation grant.", "Electric cars were first sold in the Brixfield area more than a decade ago.", "The supermarket chain plans to open two more stores in Calder Point soon.", "Ms. Achterberg joined the company as a spokesperson at the start of March."]', 'A', 1, '[Part6-문장삽입] 정답 (A). that amount가 앞 문장의 $9 million을 가리키고, 뒤 문장의 the remainder(나머지)는 보조금으로 충당되는 부분을 뺀 금액을 가리키므로 (A)가 앞뒤 모두와 이어진다. 오답: (B) 전기차 판매의 역사, (C) 슈퍼마켓의 출점 계획, (D) 대변인의 입사 시기는 비용 이야기와 이어지지 않고 the remainder가 가리킬 대상도 주지 못한다.', 141),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (142).', 'Questions 139-142 refer to the following article.

BRIXFIELD (April 2)—Voltway Energy announced yesterday that it will install 40 fast-charging stations in Brixfield and Calder Point by the end of the year. The company (139) ------- only six stations in the region, all of them near the airport. The expansion will bring its total to 46.

According to spokesperson Lena Achterberg, the new stations will be built in the parking areas of supermarkets and shopping centers. "(140) ------- can charge their vehicles while they shop," she said. "In this way, charging becomes part of a normal errand."

The project is expected to cost $9 million. (141) -------. Voltway will pay the remainder itself.

Mayor Paul Eze said that the lack of public chargers has (142) ------- many residents from buying electric cars.', 'NONE', '["discouraged", "discouraging", "discourage", "discouragement"]', 'A', 1, '[Part6-문법(동사 형태)] 정답 (A). has 뒤에서 현재완료를 만드는 과거분사가 필요하고, 뒤에 목적어 many residents와 from buying이 이어진다(discourage 사람 from -ing). 오답: (B) discouraging은 has 바로 뒤에서 완료형을 만들지 못한다. (C) discourage는 동사원형이라 has 뒤에 올 수 없다. (D) discouragement는 명사라 뒤의 many residents from buying과 이어지지 않는다.', 142),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (143).', 'Questions 143-146 refer to the following memo.

To: All Production Staff
From: Mirela Okafor, Plant Manager
Date: September 9
Subject: New inventory scanners

Beginning October 1, Tarrow Furniture (143) ------- handheld scanners to track materials in the Brixfield plant. Each time wood or hardware is removed from the stockroom, employees will scan the label on the item instead of filling out a paper form. (144) -------, the purchasing team will know immediately when supplies are running low.

All production staff must attend one training (145) ------- before the scanners are introduced. These will be held in the break room on September 22, 23, and 24 at 3:00 P.M. (146) -------. Please sign up on the sheet outside my office by September 16.

Thank you for your cooperation.', 'NONE', '["will use", "used", "has used", "was using"]', 'A', 1, '[Part6-문법(시제·문맥)] 정답 (A). 메모의 작성일은 9월 9일이고 스캐너 사용은 10월 1일부터이며, 다음 문장도 employees will scan으로 앞으로의 일을 말한다. 따라서 미래형 will use가 맞다. 오답: (B) used와 (D) was using은 과거라 작성일보다 뒤인 10월 1일과 맞지 않는다. (C) has used는 과거부터 지금까지를 나타내므로 아직 도입 전(before the scanners are introduced)이라는 내용과 모순된다.', 143),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (144).', 'Questions 143-146 refer to the following memo.

To: All Production Staff
From: Mirela Okafor, Plant Manager
Date: September 9
Subject: New inventory scanners

Beginning October 1, Tarrow Furniture (143) ------- handheld scanners to track materials in the Brixfield plant. Each time wood or hardware is removed from the stockroom, employees will scan the label on the item instead of filling out a paper form. (144) -------, the purchasing team will know immediately when supplies are running low.

All production staff must attend one training (145) ------- before the scanners are introduced. These will be held in the break room on September 22, 23, and 24 at 3:00 P.M. (146) -------. Please sign up on the sheet outside my office by September 16.

Thank you for your cooperation.', 'NONE', '["For example", "On the contrary", "In this way", "Nevertheless"]', 'C', 1, '[Part6-연결어] 정답 (C). 앞 문장은 물품을 꺼낼 때마다 라벨을 스캔한다는 방법이고, 뒤 문장은 그 방법 덕분에 구매팀이 재고 부족을 바로 알게 된다는 결과이다. ''이런 방식으로''라는 In this way가 맞다. 오답: (A) For example은 앞 내용의 사례를 들 때 쓰는데 뒤 문장은 사례가 아니다. (B) On the contrary는 반대, (D) Nevertheless는 양보를 나타내어 방법과 결과의 관계와 맞지 않는다.', 144),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (145).', 'Questions 143-146 refer to the following memo.

To: All Production Staff
From: Mirela Okafor, Plant Manager
Date: September 9
Subject: New inventory scanners

Beginning October 1, Tarrow Furniture (143) ------- handheld scanners to track materials in the Brixfield plant. Each time wood or hardware is removed from the stockroom, employees will scan the label on the item instead of filling out a paper form. (144) -------, the purchasing team will know immediately when supplies are running low.

All production staff must attend one training (145) ------- before the scanners are introduced. These will be held in the break room on September 22, 23, and 24 at 3:00 P.M. (146) -------. Please sign up on the sheet outside my office by September 16.

Thank you for your cooperation.', 'NONE', '["manual", "session", "budget", "facility"]', 'B', 1, '[Part6-어휘(문맥)] 정답 (B). 다음 문장의 These가 빈칸의 명사를 받아 9월 22·23·24일 오후 3시에 휴게실에서 열린다고 하므로, 직원이 참석(attend)하는 교육 ''시간''인 session이 맞다. 오답: (A) manual(설명서)과 (C) budget(예산)은 참석하거나 날짜를 정해 여는 대상이 아니다. (D) facility(시설)는 휴게실에서 ''열리는'' 것이 아니다.', 145),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (146).', 'Questions 143-146 refer to the following memo.

To: All Production Staff
From: Mirela Okafor, Plant Manager
Date: September 9
Subject: New inventory scanners

Beginning October 1, Tarrow Furniture (143) ------- handheld scanners to track materials in the Brixfield plant. Each time wood or hardware is removed from the stockroom, employees will scan the label on the item instead of filling out a paper form. (144) -------, the purchasing team will know immediately when supplies are running low.

All production staff must attend one training (145) ------- before the scanners are introduced. These will be held in the break room on September 22, 23, and 24 at 3:00 P.M. (146) -------. Please sign up on the sheet outside my office by September 16.

Thank you for your cooperation.', 'NONE', '["The stockroom was reorganized about two years ago.", "Paper forms must still be submitted every Friday.", "Our customers have praised the new sofa designs.", "Each one will last 40 minutes and cover the same material."]', 'D', 1, '[Part6-문장삽입] 정답 (D). Each one이 앞 문장의 교육 시간(세 날짜) 하나하나를 가리키고, 내용이 같으므로 한 번만 참석하면 된다는 앞 단락의 안내와 이어지며 뒤의 신청 안내로 자연스럽게 넘어간다. 오답: (A) 자재실 정리는 교육 일정과 무관하다. (B) 종이 양식 대신 스캔한다는 첫 단락과 모순된다. (C) 고객의 제품 평가는 이 메모의 소재가 아니다.', 146),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is the purpose of the notice?', 'Questions 147-148 refer to the following notice.

Harlow Court Apartments
Management Office Notice
Posted: March 3

The passenger elevator in Building B will be out of service from March 10 through March 12 while a new control panel is installed. During this period, residents of Building B may use the freight elevator at the rear of the building between 7:00 A.M. and 10:00 P.M. Outside those hours, please use the stairs.

Residents who expect furniture or appliance deliveries on those dates should notify the management office at least 48 hours in advance so that a staff member can be on hand to assist the delivery crew. If you require help carrying groceries or other items, call the front desk at 555-0142.

We apologize for the inconvenience and appreciate your patience.

Dmitri Okafor
Property Manager', 'NONE', '["To introduce a new property manager", "To request payment of a maintenance fee", "To advertise a delivery service for residents", "To inform residents of a temporary disruption"]', 'D', 1, '[주제·목적] 정답 (D). 첫 문장 "will be out of service from March 10 through March 12"에서 승객용 엘리베이터가 사흘간 멈춘다고 알리고 대체 이용 방법을 안내한다. out of service → a temporary disruption으로 바꿔 말했다. 오답: (A) Okafor는 서명한 관리자일 뿐 새로 부임했다는 말이 없다. (B) 비용 청구 내용이 없다. (C) 배송은 입주민이 받을 예정인 배송을 미리 알리라는 내용이지 서비스 광고가 아니다.', 147),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the freight elevator?', 'Questions 147-148 refer to the following notice.

Harlow Court Apartments
Management Office Notice
Posted: March 3

The passenger elevator in Building B will be out of service from March 10 through March 12 while a new control panel is installed. During this period, residents of Building B may use the freight elevator at the rear of the building between 7:00 A.M. and 10:00 P.M. Outside those hours, please use the stairs.

Residents who expect furniture or appliance deliveries on those dates should notify the management office at least 48 hours in advance so that a staff member can be on hand to assist the delivery crew. If you require help carrying groceries or other items, call the front desk at 555-0142.

We apologize for the inconvenience and appreciate your patience.

Dmitri Okafor
Property Manager', 'NONE', '["It is located at the front of Building B.", "It is being fitted with a new control panel.", "It may be used only for furniture deliveries.", "It is not available to residents overnight."]', 'D', 1, '[추론] 정답 (D). "between 7:00 A.M. and 10:00 P.M. Outside those hours, please use the stairs"에서 밤 10시 이후~아침 7시 이전에는 화물용 엘리베이터를 쓸 수 없음을 알 수 있다. 오답: (A) at the rear of the building이므로 뒤쪽이다. (B) 제어판 교체는 승객용 엘리베이터 이야기다. (C) Building B 입주민이 일반적으로 이용할 수 있다.', 148),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'Why did Ms. Raman contact Mr. Bellini?', 'Questions 149-150 refer to the following text-message chain.

Priya Raman (10:12 A.M.)
Hi, Marco. This is Priya from Ellery & Voss. I need to change our lunch order for the 14th. Six more people have signed up for the training, so we now need 30 boxed lunches instead of 24.

Marco Bellini (10:15 A.M.)
No problem. Same mix as before, half chicken and half vegetable?

Priya Raman (10:17 A.M.)
Actually, could you make 12 of them vegetable and the rest chicken? Also, the session has moved to our Calder Point office at 80 Wharf Road. Is that outside your delivery area?

Marco Bellini (10:20 A.M.)
We''re there every week. There''s no extra charge, but I''ll need someone to meet our driver at the loading dock at 11:30.

Priya Raman (10:22 A.M.)
I''ll be there myself. Can you send a revised invoice?

Marco Bellini (10:23 A.M.)
You''ll have it within the hour.', 'NONE', '["To cancel a training session", "To modify an existing order", "To ask about a late delivery", "To request a refund for a meal"]', 'B', 1, '[주제·목적] 정답 (B). "I need to change our lunch order for the 14th"와 24개 → 30개 수량 변경이 근거다. change our lunch order → modify an existing order. 오답: (A) 교육은 인원이 늘고 장소만 바뀌었을 뿐 취소되지 않았다. (C) 배송 지역을 물었을 뿐 늦은 배송 이야기는 없다. (D) 환불 요청은 없고 수정된 청구서를 요청했다.', 149),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'At 10:20 A.M., what does Mr. Bellini most likely mean when he writes, "We''re there every week"?', 'Questions 149-150 refer to the following text-message chain.

Priya Raman (10:12 A.M.)
Hi, Marco. This is Priya from Ellery & Voss. I need to change our lunch order for the 14th. Six more people have signed up for the training, so we now need 30 boxed lunches instead of 24.

Marco Bellini (10:15 A.M.)
No problem. Same mix as before, half chicken and half vegetable?

Priya Raman (10:17 A.M.)
Actually, could you make 12 of them vegetable and the rest chicken? Also, the session has moved to our Calder Point office at 80 Wharf Road. Is that outside your delivery area?

Marco Bellini (10:20 A.M.)
We''re there every week. There''s no extra charge, but I''ll need someone to meet our driver at the loading dock at 11:30.

Priya Raman (10:22 A.M.)
I''ll be there myself. Can you send a revised invoice?

Marco Bellini (10:23 A.M.)
You''ll have it within the hour.', 'NONE', '["He attends meetings at that office often.", "The order will take a week to prepare.", "A weekly discount will be applied.", "His company delivers to that area regularly."]', 'D', 1, '[의도 파악] 정답 (D). 바로 앞의 질문 "Is that outside your delivery area?"에 대한 답이고, 이어서 "There''s no extra charge"라고 하므로 그 지역에 정기적으로 배송을 간다는 뜻이다. 오답: (A) 회의 참석 이야기는 없다. (B) every week는 준비 기간이 아니다. (C) 추가 요금이 없다는 것이지 할인을 말한 것이 아니다.', 150),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about Ms. Strand''s trip?', 'Questions 151-152 refer to the following receipt.

Brixfield Regional Rail
Booking Change Receipt

Booking reference: BR-48217
Passenger: Helena Strand
Date issued: June 2

Original journey: June 9 — Brixfield Central to Calder Point — Departs 8:05 A.M. — Standard class
New journey: June 9 — Brixfield Central to Calder Point — Departs 6:40 A.M. — Standard class

Fare already paid: $42.00
Fare for new journey: $36.00
Fare difference: -$6.00
Change fee: $5.00
Amount refunded to card ending 3310: $1.00

Notes
Seat 14A, Car 3. Your original seat reservation has been released.
A ticket that has been changed once cannot be changed again, but it may be canceled for travel credit up to two hours before departure.
Show this receipt and photo identification to the conductor on board. No paper ticket will be issued.', 'NONE', '["It was moved to a different date.", "It will begin earlier than originally planned.", "It was upgraded to a higher class.", "It will end at a different station."]', 'B', 1, '[사실 확인] 정답 (B). Original journey는 "Departs 8:05 A.M.", New journey는 "Departs 6:40 A.M."이므로 출발이 앞당겨졌다. 오답: (A) 두 여정 모두 June 9이다. (C) 둘 다 Standard class다. (D) 도착역은 둘 다 Calder Point다.', 151),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'According to the receipt, what is Ms. Strand unable to do?', 'Questions 151-152 refer to the following receipt.

Brixfield Regional Rail
Booking Change Receipt

Booking reference: BR-48217
Passenger: Helena Strand
Date issued: June 2

Original journey: June 9 — Brixfield Central to Calder Point — Departs 8:05 A.M. — Standard class
New journey: June 9 — Brixfield Central to Calder Point — Departs 6:40 A.M. — Standard class

Fare already paid: $42.00
Fare for new journey: $36.00
Fare difference: -$6.00
Change fee: $5.00
Amount refunded to card ending 3310: $1.00

Notes
Seat 14A, Car 3. Your original seat reservation has been released.
A ticket that has been changed once cannot be changed again, but it may be canceled for travel credit up to two hours before departure.
Show this receipt and photo identification to the conductor on board. No paper ticket will be issued.', 'NONE', '["Travel without a printed ticket", "Cancel her trip in exchange for credit", "Change her departure time again", "Sit in a reserved seat"]', 'C', 1, '[세부 사항] 정답 (C). "A ticket that has been changed once cannot be changed again"가 근거다. 오답: (A) "No paper ticket will be issued"이므로 영수증과 신분증으로 탑승한다. (B) 출발 두 시간 전까지 travel credit으로 취소할 수 있다. (D) Seat 14A가 배정되어 있다.', 152),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is the notice mainly about?', 'Questions 153-154 refer to the following notice.

Meridian Crest Bank
Brixfield Main Street Branch

To our customers:

Beginning September 1, our Main Street branch will operate from 215 Alder Avenue, two blocks north of the current location. The Main Street office will close permanently at 3:00 P.M. on August 28. Account numbers, debit cards, and automatic payments will not be affected.

The Alder Avenue branch offers extended hours until 7:00 P.M. on weekdays, a drive-up teller window, and four private rooms for loan and mortgage consultations. Safe-deposit box holders will receive a separate letter explaining how their boxes will be transferred; no action is needed until then.

To mark the opening, the monthly maintenance fee on any savings account opened at the Alder Avenue branch in September will be waived for one year.', 'NONE', '["A change in account numbers", "An increase in service fees", "A merger of two banks", "The relocation of a branch"]', 'D', 1, '[주제·목적] 정답 (D). "our Main Street branch will operate from 215 Alder Avenue"와 기존 사무소의 영구 폐쇄 안내가 중심 내용이므로 지점 이전 공지다. 오답: (A) 계좌번호는 "will not be affected"라고 했다. (B) 수수료는 인상이 아니라 면제(waived) 행사다. (C) 다른 은행과의 합병 이야기는 없다.', 153),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is NOT mentioned as a feature of the Alder Avenue branch?', 'Questions 153-154 refer to the following notice.

Meridian Crest Bank
Brixfield Main Street Branch

To our customers:

Beginning September 1, our Main Street branch will operate from 215 Alder Avenue, two blocks north of the current location. The Main Street office will close permanently at 3:00 P.M. on August 28. Account numbers, debit cards, and automatic payments will not be affected.

The Alder Avenue branch offers extended hours until 7:00 P.M. on weekdays, a drive-up teller window, and four private rooms for loan and mortgage consultations. Safe-deposit box holders will receive a separate letter explaining how their boxes will be transferred; no action is needed until then.

To mark the opening, the monthly maintenance fee on any savings account opened at the Alder Avenue branch in September will be waived for one year.', 'NONE', '["Free parking for customers", "Longer weekday hours", "A drive-up window", "Private meeting rooms"]', 'A', 1, '[사실 확인(NOT)] 정답 (A). 주차에 관한 언급은 지문에 없다. 오답: (B) "extended hours until 7:00 P.M. on weekdays". (C) "a drive-up teller window". (D) "four private rooms for loan and mortgage consultations" → private meeting rooms로 바꿔 말했다.', 154),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'Why was the letter sent?', 'Questions 155-157 refer to the following letter.

Tidewell Mutual Insurance
40 Granary Street, Brixfield

October 6

Mr. Anselm Ruiz
17 Pike Lane, Calder Point

Policy number: HM-209-5531

Dear Mr. Ruiz:

Your homeowner''s policy is due for renewal on November 15. Enclosed is a statement showing your coverage for the coming year. Your annual premium will be $1,140, an increase of $60 over the current term. This adjustment reflects higher rebuilding costs in the Calder Point area and is not related to your claims history.

You may lower your premium in two ways. Customers who install a monitored smoke-alarm system qualify for a 5 percent discount once they send us proof of installation. In addition, policyholders who pay the full annual premium in a single payment, rather than in monthly installments, avoid the $3 monthly processing charge.

No action is required to renew; your coverage will continue automatically. However, if you wish to change your coverage limits, please contact your agent, Lorna Petrakis, at 555-0177 by November 1.

Sincerely,

Gideon Marsh
Customer Accounts Manager

Enclosure', 'NONE', '["To confirm that a claim has been received", "To request an overdue payment", "To introduce a new insurance agent", "To notify a customer of renewal terms"]', 'D', 1, '[주제·목적] 정답 (D). "Your homeowner''s policy is due for renewal on November 15"에 이어 다음 해 보험료와 할인 방법을 알리므로 갱신 조건 통지다. 오답: (A) 보험금 청구 접수 이야기가 아니다(claims history는 인상 사유가 아니라는 설명). (B) 연체 독촉이 아니다. (C) Petrakis는 기존 담당 agent로 소개 대상이 아니다.', 155),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about Mr. Ruiz''s premium?', 'Questions 155-157 refer to the following letter.

Tidewell Mutual Insurance
40 Granary Street, Brixfield

October 6

Mr. Anselm Ruiz
17 Pike Lane, Calder Point

Policy number: HM-209-5531

Dear Mr. Ruiz:

Your homeowner''s policy is due for renewal on November 15. Enclosed is a statement showing your coverage for the coming year. Your annual premium will be $1,140, an increase of $60 over the current term. This adjustment reflects higher rebuilding costs in the Calder Point area and is not related to your claims history.

You may lower your premium in two ways. Customers who install a monitored smoke-alarm system qualify for a 5 percent discount once they send us proof of installation. In addition, policyholders who pay the full annual premium in a single payment, rather than in monthly installments, avoid the $3 monthly processing charge.

No action is required to renew; your coverage will continue automatically. However, if you wish to change your coverage limits, please contact your agent, Lorna Petrakis, at 555-0177 by November 1.

Sincerely,

Gideon Marsh
Customer Accounts Manager

Enclosure', 'NONE', '["It will be higher than it is now.", "It rose because of a recent claim.", "It must be paid in a single payment.", "It already includes a 5 percent discount."]', 'A', 1, '[사실 확인] 정답 (A). "an increase of $60 over the current term"이 근거다. 오답: (B) "is not related to your claims history"라고 했다. (C) 일시불은 수수료를 피하는 선택 사항일 뿐 의무가 아니다. (D) 5% 할인은 경보 장치를 설치하고 증빙을 보낸 뒤에 받는 것이다.', 156),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'According to the letter, what should Mr. Ruiz do if he wants to adjust his coverage?', 'Questions 155-157 refer to the following letter.

Tidewell Mutual Insurance
40 Granary Street, Brixfield

October 6

Mr. Anselm Ruiz
17 Pike Lane, Calder Point

Policy number: HM-209-5531

Dear Mr. Ruiz:

Your homeowner''s policy is due for renewal on November 15. Enclosed is a statement showing your coverage for the coming year. Your annual premium will be $1,140, an increase of $60 over the current term. This adjustment reflects higher rebuilding costs in the Calder Point area and is not related to your claims history.

You may lower your premium in two ways. Customers who install a monitored smoke-alarm system qualify for a 5 percent discount once they send us proof of installation. In addition, policyholders who pay the full annual premium in a single payment, rather than in monthly installments, avoid the $3 monthly processing charge.

No action is required to renew; your coverage will continue automatically. However, if you wish to change your coverage limits, please contact your agent, Lorna Petrakis, at 555-0177 by November 1.

Sincerely,

Gideon Marsh
Customer Accounts Manager

Enclosure', 'NONE', '["Return the enclosed statement", "Visit the Granary Street office", "Send proof of installation", "Call a representative by a certain date"]', 'D', 1, '[세부 사항] 정답 (D). "if you wish to change your coverage limits, please contact your agent, Lorna Petrakis, at 555-0177 by November 1"가 근거다. contact your agent → call a representative, by November 1 → by a certain date. 오답: (A) 동봉 명세서를 돌려보내라는 말은 없다. (B) 방문 요청이 없다. (C) 설치 증빙은 할인 조건이다.', 157),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is the problem with the product?', 'Questions 158-160 refer to the following notice.

PRODUCT SAFETY RECALL
Varnum Home Appliances

Varnum Home Appliances is voluntarily recalling the Varnum BrewPoint 12-cup coffee maker, model BP-300, sold between January and June of this year. — [1] — In a small number of units, the heating plate may fail to switch off automatically, which can cause the glass carafe to overheat and crack. No injuries have been reported.

To find out whether your unit is affected, check the serial number printed on the base. Only units whose serial numbers begin with the letters KT are included in the recall. — [2] —

If you own an affected unit, stop using it immediately. You may either return it to the store where it was purchased for a full refund or request a free replacement heating plate, which our technicians will install at any authorized Varnum service center. — [3] — A receipt is not required for either option.

For a list of service centers or to obtain a prepaid shipping label, visit www.varnumhome.com/recall or call 1-800-555-0119. — [4] —', 'NONE', '["Its glass container is too small.", "Its serial number was printed incorrectly.", "Its power cord may overheat.", "A part may stay on when it should shut off."]', 'D', 1, '[세부 사항] 정답 (D). "the heating plate may fail to switch off automatically"가 근거다. fail to switch off → stay on when it should shut off. 오답: (A) 유리 용기의 크기 문제가 아니라 과열로 금이 갈 수 있다는 것이다. (B) 일련번호는 대상 확인 수단이다. (C) 과열되는 것은 전원 코드가 아니라 carafe다.', 158),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about the recall?', 'Questions 158-160 refer to the following notice.

PRODUCT SAFETY RECALL
Varnum Home Appliances

Varnum Home Appliances is voluntarily recalling the Varnum BrewPoint 12-cup coffee maker, model BP-300, sold between January and June of this year. — [1] — In a small number of units, the heating plate may fail to switch off automatically, which can cause the glass carafe to overheat and crack. No injuries have been reported.

To find out whether your unit is affected, check the serial number printed on the base. Only units whose serial numbers begin with the letters KT are included in the recall. — [2] —

If you own an affected unit, stop using it immediately. You may either return it to the store where it was purchased for a full refund or request a free replacement heating plate, which our technicians will install at any authorized Varnum service center. — [3] — A receipt is not required for either option.

For a list of service centers or to obtain a prepaid shipping label, visit www.varnumhome.com/recall or call 1-800-555-0119. — [4] —', 'NONE', '["It was ordered by a government agency.", "It applies only to some BP-300 units.", "It follows several reports of injuries.", "It covers units sold over two years."]', 'B', 1, '[사실 확인] 정답 (B). "Only units whose serial numbers begin with the letters KT are included in the recall"이므로 BP-300 중 일부만 대상이다. 오답: (A) voluntarily recalling이므로 자발적 리콜이다. (C) "No injuries have been reported". (D) 올해 1~6월 판매분이다.', 159),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'In which of the positions marked [1], [2], [3], and [4] does the following sentence best belong? "This repair takes about twenty minutes and is done while you wait."', 'Questions 158-160 refer to the following notice.

PRODUCT SAFETY RECALL
Varnum Home Appliances

Varnum Home Appliances is voluntarily recalling the Varnum BrewPoint 12-cup coffee maker, model BP-300, sold between January and June of this year. — [1] — In a small number of units, the heating plate may fail to switch off automatically, which can cause the glass carafe to overheat and crack. No injuries have been reported.

To find out whether your unit is affected, check the serial number printed on the base. Only units whose serial numbers begin with the letters KT are included in the recall. — [2] —

If you own an affected unit, stop using it immediately. You may either return it to the store where it was purchased for a full refund or request a free replacement heating plate, which our technicians will install at any authorized Varnum service center. — [3] — A receipt is not required for either option.

For a list of service centers or to obtain a prepaid shipping label, visit www.varnumhome.com/recall or call 1-800-555-0119. — [4] —', 'NONE', '["[1]", "[2]", "[3]", "[4]"]', 'C', 1, '[문장 삽입] 정답 (C). This repair는 바로 앞 문장의 "a free replacement heating plate, which our technicians will install"을 가리키므로 [3]이 알맞다. 오답: (A) [1] 앞은 리콜 대상 제품 소개로 수리 언급이 없다. (B) [2] 앞은 일련번호 확인 방법이다. (D) [4] 앞은 서비스 센터 목록과 배송 라벨 안내라 This repair가 가리킬 말이 멀다.', 160);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'For whom is the pottery course intended?', 'Questions 161-163 refer to the following information.

Calder Point Community Arts Center
Winter Term for Adults: January 12 - March 6

Registration opens on December 1 for members and on December 8 for the general public. All courses meet once a week for eight weeks.

Pottery on the Wheel — Tuesday evenings — $160
An entry-level course; no experience is needed. The fee covers clay and glazes. Limited to 10 students.

Watercolor Landscapes — Wednesday mornings — $135
For students who have completed Watercolor Basics or have equivalent experience. Students supply their own paints and brushes; a list is sent upon registration.

Digital Photography — Thursday evenings — $150
Bring your own camera. The final session is an outdoor shoot at Calder Point Lighthouse.

Members receive 15 percent off all course fees. A full refund is available for withdrawals made before the second class meeting; after that, fees cannot be refunded. Courses with fewer than five registrants by January 5 will be canceled, and those who signed up will be notified by e-mail.

Register at the front desk or at www.cpcac.org/courses.', 'NONE', '["People who are new to the craft", "Students who finished a basics class", "Members who registered on December 1", "Artists who own their own equipment"]', 'A', 1, '[세부 사항] 정답 (A). "An entry-level course; no experience is needed"가 근거다. entry-level → new to the craft. 오답: (B) 기초반 수료 조건은 Watercolor Landscapes다. (C) 12월 1일은 회원 등록 시작일일 뿐 수강 대상 조건이 아니다. (D) 재료비가 포함되어 있고 장비 소지 조건은 없다.', 161),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about Watercolor Landscapes?', 'Questions 161-163 refer to the following information.

Calder Point Community Arts Center
Winter Term for Adults: January 12 - March 6

Registration opens on December 1 for members and on December 8 for the general public. All courses meet once a week for eight weeks.

Pottery on the Wheel — Tuesday evenings — $160
An entry-level course; no experience is needed. The fee covers clay and glazes. Limited to 10 students.

Watercolor Landscapes — Wednesday mornings — $135
For students who have completed Watercolor Basics or have equivalent experience. Students supply their own paints and brushes; a list is sent upon registration.

Digital Photography — Thursday evenings — $150
Bring your own camera. The final session is an outdoor shoot at Calder Point Lighthouse.

Members receive 15 percent off all course fees. A full refund is available for withdrawals made before the second class meeting; after that, fees cannot be refunded. Courses with fewer than five registrants by January 5 will be canceled, and those who signed up will be notified by e-mail.

Register at the front desk or at www.cpcac.org/courses.', 'NONE', '["It has the highest fee of the three courses.", "It is not intended for first-time painters.", "It includes all necessary supplies.", "It ends with an outdoor session."]', 'B', 1, '[추론] 정답 (B). "For students who have completed Watercolor Basics or have equivalent experience"이므로 처음 그리는 사람을 위한 강좌가 아니다. 오답: (A) $135로 셋 중 가장 싸다. (C) "Students supply their own paints and brushes". (D) 야외 수업은 Digital Photography의 마지막 시간이다.', 162),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the Digital Photography course?', 'Questions 161-163 refer to the following information.

Calder Point Community Arts Center
Winter Term for Adults: January 12 - March 6

Registration opens on December 1 for members and on December 8 for the general public. All courses meet once a week for eight weeks.

Pottery on the Wheel — Tuesday evenings — $160
An entry-level course; no experience is needed. The fee covers clay and glazes. Limited to 10 students.

Watercolor Landscapes — Wednesday mornings — $135
For students who have completed Watercolor Basics or have equivalent experience. Students supply their own paints and brushes; a list is sent upon registration.

Digital Photography — Thursday evenings — $150
Bring your own camera. The final session is an outdoor shoot at Calder Point Lighthouse.

Members receive 15 percent off all course fees. A full refund is available for withdrawals made before the second class meeting; after that, fees cannot be refunded. Courses with fewer than five registrants by January 5 will be canceled, and those who signed up will be notified by e-mail.

Register at the front desk or at www.cpcac.org/courses.', 'NONE', '["It is limited to ten students.", "It provides cameras for students to borrow.", "One of its meetings is held away from the center.", "It will be canceled on December 8."]', 'C', 1, '[추론] 정답 (C). "The final session is an outdoor shoot at Calder Point Lighthouse"에서 마지막 수업이 센터 밖 등대에서 열림을 알 수 있다. 오답: (A) 10명 제한은 Pottery 강좌다. (B) "Bring your own camera". (D) 12월 8일은 일반 등록 시작일이고, 폐강 기준일은 1월 5일이다.', 163),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about the Coastal Logistics Forum?', 'Questions 164-167 refer to the following online chat discussion.

Naomi Feld (1:02 P.M.)
Quick update on the Coastal Logistics Forum. Early registration closed last night with 212 sign-ups, about 40 more than at this point last year. Where do we stand on the program?

Tariq Osei (1:04 P.M.)
All speakers are confirmed except Dr. Amara Nwosu. She has a prior engagement in Brixfield on the morning of the 18th and has asked whether her talk could be moved to the afternoon.

Lucia Brandt (1:06 P.M.)
The 2:30 slot in Hall B is open now that the customs panel has been dropped. But Hall B seats only 80, and her session was the most requested on the sign-up form.

Naomi Feld (1:07 P.M.)
Then Hall B won''t do. Wen, could the 2:30 workshop in the Main Auditorium trade places with her?

Wen Zhao (1:10 P.M.)
I just spoke to the workshop leader. He''s fine with Hall B as long as he gets a projector and movable tables. He expects only about 30 people.

Lucia Brandt (1:11 P.M.)
Hall B has both.

Naomi Feld (1:12 P.M.)
That settles it. Tariq, please let Dr. Nwosu know she''s in the Main Auditorium at 2:30. Lucia, can you update the online program today? The printed version goes to the print shop on the 9th, so we have a little time there.

Lucia Brandt (1:13 P.M.)
I''ll have the Web page done by 5:00.

Tariq Osei (1:14 P.M.)
Writing to her now.', 'NONE', '["It will be held in Brixfield.", "Its registration period has been extended.", "It has drawn more early registrants than last year.", "Its printed program has already been distributed."]', 'C', 1, '[사실 확인] 정답 (C). "Early registration closed last night with 212 sign-ups, about 40 more than at this point last year"가 근거다. 오답: (A) Brixfield는 Nwosu 박사의 다른 선약 장소일 뿐 포럼 개최지라는 말이 없다. (B) 조기 등록은 마감되었고 연장 언급이 없다. (D) 인쇄본은 9일에 인쇄소로 넘어갈 예정이다.', 164),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'Why does Dr. Nwosu want her talk to be rescheduled?', 'Questions 164-167 refer to the following online chat discussion.

Naomi Feld (1:02 P.M.)
Quick update on the Coastal Logistics Forum. Early registration closed last night with 212 sign-ups, about 40 more than at this point last year. Where do we stand on the program?

Tariq Osei (1:04 P.M.)
All speakers are confirmed except Dr. Amara Nwosu. She has a prior engagement in Brixfield on the morning of the 18th and has asked whether her talk could be moved to the afternoon.

Lucia Brandt (1:06 P.M.)
The 2:30 slot in Hall B is open now that the customs panel has been dropped. But Hall B seats only 80, and her session was the most requested on the sign-up form.

Naomi Feld (1:07 P.M.)
Then Hall B won''t do. Wen, could the 2:30 workshop in the Main Auditorium trade places with her?

Wen Zhao (1:10 P.M.)
I just spoke to the workshop leader. He''s fine with Hall B as long as he gets a projector and movable tables. He expects only about 30 people.

Lucia Brandt (1:11 P.M.)
Hall B has both.

Naomi Feld (1:12 P.M.)
That settles it. Tariq, please let Dr. Nwosu know she''s in the Main Auditorium at 2:30. Lucia, can you update the online program today? The printed version goes to the print shop on the 9th, so we have a little time there.

Lucia Brandt (1:13 P.M.)
I''ll have the Web page done by 5:00.

Tariq Osei (1:14 P.M.)
Writing to her now.', 'NONE', '["Her flight will arrive late in the day.", "She has another commitment that morning.", "She would prefer a larger room.", "She needs additional equipment."]', 'B', 1, '[세부 사항] 정답 (B). "She has a prior engagement in Brixfield on the morning of the 18th"가 근거다. prior engagement → another commitment. 오답: (A) 항공편 이야기는 없다. (C) 더 큰 방은 주최 측이 판단한 것이지 박사의 요청이 아니다. (D) 장비를 요구한 사람은 워크숍 진행자다.', 165),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'At 1:07 P.M., what does Ms. Feld most likely mean when she writes, "Then Hall B won''t do"?', 'Questions 164-167 refer to the following online chat discussion.

Naomi Feld (1:02 P.M.)
Quick update on the Coastal Logistics Forum. Early registration closed last night with 212 sign-ups, about 40 more than at this point last year. Where do we stand on the program?

Tariq Osei (1:04 P.M.)
All speakers are confirmed except Dr. Amara Nwosu. She has a prior engagement in Brixfield on the morning of the 18th and has asked whether her talk could be moved to the afternoon.

Lucia Brandt (1:06 P.M.)
The 2:30 slot in Hall B is open now that the customs panel has been dropped. But Hall B seats only 80, and her session was the most requested on the sign-up form.

Naomi Feld (1:07 P.M.)
Then Hall B won''t do. Wen, could the 2:30 workshop in the Main Auditorium trade places with her?

Wen Zhao (1:10 P.M.)
I just spoke to the workshop leader. He''s fine with Hall B as long as he gets a projector and movable tables. He expects only about 30 people.

Lucia Brandt (1:11 P.M.)
Hall B has both.

Naomi Feld (1:12 P.M.)
That settles it. Tariq, please let Dr. Nwosu know she''s in the Main Auditorium at 2:30. Lucia, can you update the online program today? The printed version goes to the print shop on the 9th, so we have a little time there.

Lucia Brandt (1:13 P.M.)
I''ll have the Web page done by 5:00.

Tariq Osei (1:14 P.M.)
Writing to her now.', 'NONE', '["The room has already been reserved.", "The room is too small for the expected audience.", "The room lacks the necessary equipment.", "The room is too far from the auditorium."]', 'B', 1, '[의도 파악] 정답 (B). 바로 앞에서 Brandt가 "Hall B seats only 80, and her session was the most requested"라고 했으므로 청중에 비해 좌석이 부족해 적합하지 않다는 뜻이다. 오답: (A) 2:30 시간대는 비어 있다(open). (C) Hall B에는 프로젝터와 이동식 탁자가 있다. (D) 거리 이야기는 없다.', 166),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What will Mr. Osei most likely do next?', 'Questions 164-167 refer to the following online chat discussion.

Naomi Feld (1:02 P.M.)
Quick update on the Coastal Logistics Forum. Early registration closed last night with 212 sign-ups, about 40 more than at this point last year. Where do we stand on the program?

Tariq Osei (1:04 P.M.)
All speakers are confirmed except Dr. Amara Nwosu. She has a prior engagement in Brixfield on the morning of the 18th and has asked whether her talk could be moved to the afternoon.

Lucia Brandt (1:06 P.M.)
The 2:30 slot in Hall B is open now that the customs panel has been dropped. But Hall B seats only 80, and her session was the most requested on the sign-up form.

Naomi Feld (1:07 P.M.)
Then Hall B won''t do. Wen, could the 2:30 workshop in the Main Auditorium trade places with her?

Wen Zhao (1:10 P.M.)
I just spoke to the workshop leader. He''s fine with Hall B as long as he gets a projector and movable tables. He expects only about 30 people.

Lucia Brandt (1:11 P.M.)
Hall B has both.

Naomi Feld (1:12 P.M.)
That settles it. Tariq, please let Dr. Nwosu know she''s in the Main Auditorium at 2:30. Lucia, can you update the online program today? The printed version goes to the print shop on the 9th, so we have a little time there.

Lucia Brandt (1:13 P.M.)
I''ll have the Web page done by 5:00.

Tariq Osei (1:14 P.M.)
Writing to her now.', 'NONE', '["Send a message to a speaker", "Update a Web page", "Contact a print shop", "Reserve a projector"]', 'A', 1, '[추론] 정답 (A). Feld가 "Tariq, please let Dr. Nwosu know"라고 요청했고 Osei가 "Writing to her now"라고 답했으므로 발표자에게 메시지를 보낼 것이다. 오답: (B) 웹 페이지 수정은 Brandt의 일이다. (C) 인쇄소에는 9일에 넘기며 Osei의 일로 언급되지 않았다. (D) 프로젝터는 Hall B에 이미 있다.', 167),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is NOT offered to Ms. Fontaine as part of the renewal?', 'Questions 168-171 refer to the following e-mail.

To: Odile Fontaine
From: Barrow & Lynd Property Management <leasing@barrowlynd.com>
Date: April 4
Subject: Lease renewal for Unit 6C, Quayside Lofts

Dear Ms. Fontaine,

Your lease for Unit 6C will expire on June 30. — [1] — We are pleased to offer you two renewal options. Under a 12-month lease, your monthly rent would rise from $1,450 to $1,495. Under a 24-month lease, the rent would remain at the current rate for the entire term.

Whichever option you choose, the security deposit we currently hold will carry over, and no additional deposit will be required. — [2] — As a renewing tenant, you are also entitled to one complimentary service: professional carpet cleaning or repainting of one room. Please indicate your choice on the renewal form.

To renew, sign and return the attached form by May 15. — [3] — If we do not hear from you by that date, we will assume that you intend to move out, and we will begin showing the unit to prospective tenants in June. In that case, we would give you at least 24 hours'' notice before each viewing.

Finally, please note that as of May 1, rent can no longer be paid by check at our office. — [4] — Payments must be made through the online tenant portal or by automatic bank transfer.

Sincerely,

Felix Arroyo
Leasing Coordinator', 'NONE', '["A choice between two lease lengths", "A lower monthly rent", "A free home-improvement service", "The transfer of her existing deposit"]', 'B', 1, '[사실 확인(NOT)] 정답 (B). 12개월 계약은 $1,495로 오르고 24개월 계약은 "remain at the current rate"이므로 임대료 인하는 제안되지 않았다. 오답: (A) "two renewal options"(12개월·24개월). (C) "one complimentary service: professional carpet cleaning or repainting of one room". (D) "the security deposit we currently hold will carry over".', 168),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'The word "hold" in paragraph 2, line 1, is closest in meaning to', 'Questions 168-171 refer to the following e-mail.

To: Odile Fontaine
From: Barrow & Lynd Property Management <leasing@barrowlynd.com>
Date: April 4
Subject: Lease renewal for Unit 6C, Quayside Lofts

Dear Ms. Fontaine,

Your lease for Unit 6C will expire on June 30. — [1] — We are pleased to offer you two renewal options. Under a 12-month lease, your monthly rent would rise from $1,450 to $1,495. Under a 24-month lease, the rent would remain at the current rate for the entire term.

Whichever option you choose, the security deposit we currently hold will carry over, and no additional deposit will be required. — [2] — As a renewing tenant, you are also entitled to one complimentary service: professional carpet cleaning or repainting of one room. Please indicate your choice on the renewal form.

To renew, sign and return the attached form by May 15. — [3] — If we do not hear from you by that date, we will assume that you intend to move out, and we will begin showing the unit to prospective tenants in June. In that case, we would give you at least 24 hours'' notice before each viewing.

Finally, please note that as of May 1, rent can no longer be paid by check at our office. — [4] — Payments must be made through the online tenant portal or by automatic bank transfer.

Sincerely,

Felix Arroyo
Leasing Coordinator', 'NONE', '["conduct", "delay", "retain", "support"]', 'C', 1, '[동의어] 정답 (C). "the security deposit we currently hold"는 관리 회사가 현재 보관하고 있는 보증금이라는 뜻이므로 retain(보유하다)이 가장 가깝다. 오답: (A) conduct는 행사를 ''열다''는 뜻의 hold다. (B) delay는 ''보류하다''의 뜻으로 보증금과 맞지 않는다. (D) support는 무게를 ''지탱하다''는 뜻의 hold다.', 169),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What will happen if Ms. Fontaine does not respond by May 15?', 'Questions 168-171 refer to the following e-mail.

To: Odile Fontaine
From: Barrow & Lynd Property Management <leasing@barrowlynd.com>
Date: April 4
Subject: Lease renewal for Unit 6C, Quayside Lofts

Dear Ms. Fontaine,

Your lease for Unit 6C will expire on June 30. — [1] — We are pleased to offer you two renewal options. Under a 12-month lease, your monthly rent would rise from $1,450 to $1,495. Under a 24-month lease, the rent would remain at the current rate for the entire term.

Whichever option you choose, the security deposit we currently hold will carry over, and no additional deposit will be required. — [2] — As a renewing tenant, you are also entitled to one complimentary service: professional carpet cleaning or repainting of one room. Please indicate your choice on the renewal form.

To renew, sign and return the attached form by May 15. — [3] — If we do not hear from you by that date, we will assume that you intend to move out, and we will begin showing the unit to prospective tenants in June. In that case, we would give you at least 24 hours'' notice before each viewing.

Finally, please note that as of May 1, rent can no longer be paid by check at our office. — [4] — Payments must be made through the online tenant portal or by automatic bank transfer.

Sincerely,

Felix Arroyo
Leasing Coordinator', 'NONE', '["Her rent will increase to $1,495.", "Her lease will be renewed automatically.", "Her deposit will be kept by the company.", "Her unit will be shown to potential renters."]', 'D', 1, '[세부 사항] 정답 (D). "If we do not hear from you by that date, ... we will begin showing the unit to prospective tenants in June"이 근거다. prospective tenants → potential renters. 오답: (A) $1,495는 12개월 계약을 택했을 때의 임대료다. (B) 답이 없으면 이사하는 것으로 간주한다. (C) 보증금 몰수 이야기는 없다.', 170),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'In which of the positions marked [1], [2], [3], and [4] does the following sentence best belong? "With the end of your tenancy approaching, we are writing to ask whether you plan to stay."', 'Questions 168-171 refer to the following e-mail.

To: Odile Fontaine
From: Barrow & Lynd Property Management <leasing@barrowlynd.com>
Date: April 4
Subject: Lease renewal for Unit 6C, Quayside Lofts

Dear Ms. Fontaine,

Your lease for Unit 6C will expire on June 30. — [1] — We are pleased to offer you two renewal options. Under a 12-month lease, your monthly rent would rise from $1,450 to $1,495. Under a 24-month lease, the rent would remain at the current rate for the entire term.

Whichever option you choose, the security deposit we currently hold will carry over, and no additional deposit will be required. — [2] — As a renewing tenant, you are also entitled to one complimentary service: professional carpet cleaning or repainting of one room. Please indicate your choice on the renewal form.

To renew, sign and return the attached form by May 15. — [3] — If we do not hear from you by that date, we will assume that you intend to move out, and we will begin showing the unit to prospective tenants in June. In that case, we would give you at least 24 hours'' notice before each viewing.

Finally, please note that as of May 1, rent can no longer be paid by check at our office. — [4] — Payments must be made through the online tenant portal or by automatic bank transfer.

Sincerely,

Felix Arroyo
Leasing Coordinator', 'NONE', '["[1]", "[2]", "[3]", "[4]"]', 'A', 1, '[문장 삽입] 정답 (A). 삽입 문장은 편지를 쓴 목적을 밝히는 도입 문장이고, the end of your tenancy가 바로 앞의 "will expire on June 30"을 받으며 뒤의 갱신 선택지 제안으로 자연스럽게 이어지므로 [1]이 알맞다. 오답: (B) [2]는 이미 선택지를 설명한 뒤 보증금 이야기를 하는 자리다. (C) [3]은 제출 방법과 기한 사이다. (D) [4]는 임대료 납부 방법 변경 안내 중간이다.', 171),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is the article mainly about?', 'Questions 172-175 refer to the following article.

Brixfield Courier: Business Spotlight

BRIXFIELD (May 20) — When Ingrid Solberg opened Lanternlight Bindery in a former bakery on Corliss Street twelve years ago, she had one secondhand press and a single client, a local poet. Today the company employs 26 people and repairs and rebinds more than 9,000 books a year for libraries, law firms, and private collectors across the region.

Ms. Solberg, who trained as a paper conservator at the Calder Point Maritime Museum, says the business grew by word of mouth. "We have never run an advertisement," she said. "Librarians talk to one another."

The firm''s steady growth has not been without setbacks. A burst pipe two winters ago destroyed much of its leather stock and forced a six-week closure. Rather than lay off staff, Ms. Solberg used the time to train employees in digital scanning, a service that now accounts for nearly a third of revenue.

Last month, Lanternlight secured a five-year contract to preserve the Brixfield County Archive''s collection of nineteenth-century shipping records. To handle the work, the company will lease the vacant unit next door and plans to hire four additional technicians by autumn.

Ms. Solberg has no plans to leave Corliss Street. "The ovens are long gone," she said, "but people still walk in asking for bread. We send them out with a bookmark instead."', 'NONE', '["The development of a local business", "The reopening of a historic bakery", "A new exhibit at a maritime museum", "Job openings at a county archive"]', 'A', 1, '[주제·목적] 정답 (A). 12년 전 창업부터 현재 직원 26명, 최근 계약과 확장 계획까지 Lanternlight Bindery의 성장 과정을 다루므로 지역 업체의 발전이 주제다. 오답: (B) 제과점은 건물의 옛 용도일 뿐이다. (C) 박물관은 Solberg가 교육받은 곳이다. (D) 채용은 기록 보관소가 아니라 제본 회사가 한다.', 172),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'The word "run" in paragraph 2, line 2, is closest in meaning to', 'Questions 172-175 refer to the following article.

Brixfield Courier: Business Spotlight

BRIXFIELD (May 20) — When Ingrid Solberg opened Lanternlight Bindery in a former bakery on Corliss Street twelve years ago, she had one secondhand press and a single client, a local poet. Today the company employs 26 people and repairs and rebinds more than 9,000 books a year for libraries, law firms, and private collectors across the region.

Ms. Solberg, who trained as a paper conservator at the Calder Point Maritime Museum, says the business grew by word of mouth. "We have never run an advertisement," she said. "Librarians talk to one another."

The firm''s steady growth has not been without setbacks. A burst pipe two winters ago destroyed much of its leather stock and forced a six-week closure. Rather than lay off staff, Ms. Solberg used the time to train employees in digital scanning, a service that now accounts for nearly a third of revenue.

Last month, Lanternlight secured a five-year contract to preserve the Brixfield County Archive''s collection of nineteenth-century shipping records. To handle the work, the company will lease the vacant unit next door and plans to hire four additional technicians by autumn.

Ms. Solberg has no plans to leave Corliss Street. "The ovens are long gone," she said, "but people still walk in asking for bread. We send them out with a bookmark instead."', 'NONE', '["published", "managed", "operated", "raced"]', 'A', 1, '[동의어] 정답 (A). "We have never run an advertisement"는 광고를 한 번도 ''낸'' 적이 없다는 뜻이므로 published(게재했다)가 가장 가깝다. 오답: (B) managed와 (C) operated는 회사·기계를 ''운영하다''는 뜻의 run이고, (D) raced는 ''달리다''는 뜻의 run이라 목적어 an advertisement와 맞지 않는다.', 173),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What happened during the company''s six-week closure?', 'Questions 172-175 refer to the following article.

Brixfield Courier: Business Spotlight

BRIXFIELD (May 20) — When Ingrid Solberg opened Lanternlight Bindery in a former bakery on Corliss Street twelve years ago, she had one secondhand press and a single client, a local poet. Today the company employs 26 people and repairs and rebinds more than 9,000 books a year for libraries, law firms, and private collectors across the region.

Ms. Solberg, who trained as a paper conservator at the Calder Point Maritime Museum, says the business grew by word of mouth. "We have never run an advertisement," she said. "Librarians talk to one another."

The firm''s steady growth has not been without setbacks. A burst pipe two winters ago destroyed much of its leather stock and forced a six-week closure. Rather than lay off staff, Ms. Solberg used the time to train employees in digital scanning, a service that now accounts for nearly a third of revenue.

Last month, Lanternlight secured a five-year contract to preserve the Brixfield County Archive''s collection of nineteenth-century shipping records. To handle the work, the company will lease the vacant unit next door and plans to hire four additional technicians by autumn.

Ms. Solberg has no plans to leave Corliss Street. "The ovens are long gone," she said, "but people still walk in asking for bread. We send them out with a bookmark instead."', 'NONE', '["Several workers were laid off.", "Employees learned a new skill.", "The building was sold.", "A leather supplier was replaced."]', 'B', 1, '[세부 사항] 정답 (B). "Ms. Solberg used the time to train employees in digital scanning"이 근거다. train employees in digital scanning → learned a new skill. 오답: (A) "Rather than lay off staff"이므로 해고하지 않았다. (C) 건물 매각 이야기는 없다. (D) 가죽 재고가 손상되었을 뿐 공급업체 교체는 언급되지 않았다.', 174),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the contract with the Brixfield County Archive?', 'Questions 172-175 refer to the following article.

Brixfield Courier: Business Spotlight

BRIXFIELD (May 20) — When Ingrid Solberg opened Lanternlight Bindery in a former bakery on Corliss Street twelve years ago, she had one secondhand press and a single client, a local poet. Today the company employs 26 people and repairs and rebinds more than 9,000 books a year for libraries, law firms, and private collectors across the region.

Ms. Solberg, who trained as a paper conservator at the Calder Point Maritime Museum, says the business grew by word of mouth. "We have never run an advertisement," she said. "Librarians talk to one another."

The firm''s steady growth has not been without setbacks. A burst pipe two winters ago destroyed much of its leather stock and forced a six-week closure. Rather than lay off staff, Ms. Solberg used the time to train employees in digital scanning, a service that now accounts for nearly a third of revenue.

Last month, Lanternlight secured a five-year contract to preserve the Brixfield County Archive''s collection of nineteenth-century shipping records. To handle the work, the company will lease the vacant unit next door and plans to hire four additional technicians by autumn.

Ms. Solberg has no plans to leave Corliss Street. "The ovens are long gone," she said, "but people still walk in asking for bread. We send them out with a bookmark instead."', 'NONE', '["It will require the company to add employees.", "It was signed during the winter two years ago.", "It involves repairing a law firm''s books.", "It will be completed by this coming autumn."]', 'A', 1, '[추론] 정답 (A). "To handle the work, the company ... plans to hire four additional technicians"에서 계약 업무를 처리하려면 인력을 늘려야 함을 알 수 있다. 오답: (B) 계약은 지난달에 따냈고, 두 해 전 겨울은 배관 파열 사고다. (C) 대상은 19세기 해운 기록이다. (D) 가을은 채용 목표 시점이고 계약 기간은 5년이다.', 175),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'According to the Web page, what is included in every registration fee?', 'Questions 176-180 refer to the following Web page and e-mail.

[Web page]
Brixfield Institute of Urban Planning
9th Annual Conference on Sustainable Transit
November 12-13, Calder Point Convention Center

Registration fees
Type | By October 10 | After October 10
Full conference (both days) | $280 | $340
Single day | $160 | $190
Student (both days, valid ID required) | $120 | $150

All fees include lunch and admission to the exhibit hall. The conference dinner on November 12 is an additional $55. Presenters must register for the full conference by October 10 in order to be listed in the printed program.

Schedule of paper sessions
November 12, 10:00 A.M. — Session 1: Bus Rapid Transit — Room 204
November 12, 2:00 P.M. — Session 2: Fare Policy and Equity — Room 204
November 13, 9:30 A.M. — Session 3: Rail Station Design — Harbor Room
November 13, 1:30 P.M. — Session 4: Cycling Networks — Harbor Room

Each presenter is allotted 20 minutes, followed by 5 minutes for questions. Cancellations received by October 31 will be refunded less a $40 handling fee.

────────────────────

[E-mail]
To: Dr. Kwame Adeyemi
From: Rosalind Vega <registration@biup.org>
Date: October 14
Subject: Your registration and presentation

Dear Dr. Adeyemi,

Thank you for registering for the full conference on October 8. Your payment has been received, and a receipt is attached. You also asked to add one ticket to the conference dinner; that charge will appear separately on your card.

Your paper, "Platform Crowding at Mid-Sized Rail Stations," has been accepted and placed in Session 3. Please note that this session has been moved to Room 118 because the room listed on our Web site is being renovated; the day and time are unchanged. You will be the second of four speakers.

Please upload your slides to the presenter portal by November 5. Our technicians will load them onto the room computer in advance, so personal laptops cannot be connected to the projector.

Finally, the session chair, Dr. Mei-Ling Hsu, would like to hold a brief meeting with all Session 3 speakers at 9:00 A.M. on the day of the session, at the door of the presentation room.

Best regards,

Rosalind Vega
Conference Registrar', 'NONE', '["A ticket to the conference dinner", "A parking permit", "Access to the presenter portal", "Entry to an exhibition area"]', 'D', 1, '[세부 사항] 정답 (D). "All fees include lunch and admission to the exhibit hall"이 근거다. admission to the exhibit hall → entry to an exhibition area. 오답: (A) 만찬은 "an additional $55"다. (B) 주차 언급이 없다. (C) 발표자 포털은 이메일에 나오는 슬라이드 제출처로 등록비 포함 항목이 아니다.', 176),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about a presenter who registers on October 20?', 'Questions 176-180 refer to the following Web page and e-mail.

[Web page]
Brixfield Institute of Urban Planning
9th Annual Conference on Sustainable Transit
November 12-13, Calder Point Convention Center

Registration fees
Type | By October 10 | After October 10
Full conference (both days) | $280 | $340
Single day | $160 | $190
Student (both days, valid ID required) | $120 | $150

All fees include lunch and admission to the exhibit hall. The conference dinner on November 12 is an additional $55. Presenters must register for the full conference by October 10 in order to be listed in the printed program.

Schedule of paper sessions
November 12, 10:00 A.M. — Session 1: Bus Rapid Transit — Room 204
November 12, 2:00 P.M. — Session 2: Fare Policy and Equity — Room 204
November 13, 9:30 A.M. — Session 3: Rail Station Design — Harbor Room
November 13, 1:30 P.M. — Session 4: Cycling Networks — Harbor Room

Each presenter is allotted 20 minutes, followed by 5 minutes for questions. Cancellations received by October 31 will be refunded less a $40 handling fee.

────────────────────

[E-mail]
To: Dr. Kwame Adeyemi
From: Rosalind Vega <registration@biup.org>
Date: October 14
Subject: Your registration and presentation

Dear Dr. Adeyemi,

Thank you for registering for the full conference on October 8. Your payment has been received, and a receipt is attached. You also asked to add one ticket to the conference dinner; that charge will appear separately on your card.

Your paper, "Platform Crowding at Mid-Sized Rail Stations," has been accepted and placed in Session 3. Please note that this session has been moved to Room 118 because the room listed on our Web site is being renovated; the day and time are unchanged. You will be the second of four speakers.

Please upload your slides to the presenter portal by November 5. Our technicians will load them onto the room computer in advance, so personal laptops cannot be connected to the projector.

Finally, the session chair, Dr. Mei-Ling Hsu, would like to hold a brief meeting with all Session 3 speakers at 9:00 A.M. on the day of the session, at the door of the presentation room.

Best regards,

Rosalind Vega
Conference Registrar', 'NONE', '["The presenter will pay $280 for both days.", "The presenter will be left out of the printed program.", "The presenter will have only five minutes to speak.", "The presenter will not be charged for dinner."]', 'B', 1, '[추론] 정답 (B). "Presenters must register for the full conference by October 10 in order to be listed in the printed program"이므로 10월 20일에 등록하면 인쇄 프로그램에 실리지 않는다. 오답: (A) 10월 10일 이후 전체 등록은 $340이다. (C) 발표 20분, 질문 5분이다. (D) 만찬은 누구에게나 추가 $55다.', 177),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'How much was Dr. Adeyemi most likely charged for his conference registration?', 'Questions 176-180 refer to the following Web page and e-mail.

[Web page]
Brixfield Institute of Urban Planning
9th Annual Conference on Sustainable Transit
November 12-13, Calder Point Convention Center

Registration fees
Type | By October 10 | After October 10
Full conference (both days) | $280 | $340
Single day | $160 | $190
Student (both days, valid ID required) | $120 | $150

All fees include lunch and admission to the exhibit hall. The conference dinner on November 12 is an additional $55. Presenters must register for the full conference by October 10 in order to be listed in the printed program.

Schedule of paper sessions
November 12, 10:00 A.M. — Session 1: Bus Rapid Transit — Room 204
November 12, 2:00 P.M. — Session 2: Fare Policy and Equity — Room 204
November 13, 9:30 A.M. — Session 3: Rail Station Design — Harbor Room
November 13, 1:30 P.M. — Session 4: Cycling Networks — Harbor Room

Each presenter is allotted 20 minutes, followed by 5 minutes for questions. Cancellations received by October 31 will be refunded less a $40 handling fee.

────────────────────

[E-mail]
To: Dr. Kwame Adeyemi
From: Rosalind Vega <registration@biup.org>
Date: October 14
Subject: Your registration and presentation

Dear Dr. Adeyemi,

Thank you for registering for the full conference on October 8. Your payment has been received, and a receipt is attached. You also asked to add one ticket to the conference dinner; that charge will appear separately on your card.

Your paper, "Platform Crowding at Mid-Sized Rail Stations," has been accepted and placed in Session 3. Please note that this session has been moved to Room 118 because the room listed on our Web site is being renovated; the day and time are unchanged. You will be the second of four speakers.

Please upload your slides to the presenter portal by November 5. Our technicians will load them onto the room computer in advance, so personal laptops cannot be connected to the projector.

Finally, the session chair, Dr. Mei-Ling Hsu, would like to hold a brief meeting with all Session 3 speakers at 9:00 A.M. on the day of the session, at the door of the presentation room.

Best regards,

Rosalind Vega
Conference Registrar', 'NONE', '["$160", "$190", "$280", "$340"]', 'C', 1, '[연계(문서1+2)] 정답 (C). 이메일의 "registering for the full conference on October 8"과 웹 페이지 요금표의 Full conference·By October 10 칸($280)을 연결한다. 만찬 요금은 "appear separately"라 등록비에 넣지 않는다. 오답: (A) 하루 조기 요금. (B) 하루 일반 요금. (D) 10월 10일 이후의 전체 등록 요금.', 178),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'According to the e-mail, what has changed about Session 3?', 'Questions 176-180 refer to the following Web page and e-mail.

[Web page]
Brixfield Institute of Urban Planning
9th Annual Conference on Sustainable Transit
November 12-13, Calder Point Convention Center

Registration fees
Type | By October 10 | After October 10
Full conference (both days) | $280 | $340
Single day | $160 | $190
Student (both days, valid ID required) | $120 | $150

All fees include lunch and admission to the exhibit hall. The conference dinner on November 12 is an additional $55. Presenters must register for the full conference by October 10 in order to be listed in the printed program.

Schedule of paper sessions
November 12, 10:00 A.M. — Session 1: Bus Rapid Transit — Room 204
November 12, 2:00 P.M. — Session 2: Fare Policy and Equity — Room 204
November 13, 9:30 A.M. — Session 3: Rail Station Design — Harbor Room
November 13, 1:30 P.M. — Session 4: Cycling Networks — Harbor Room

Each presenter is allotted 20 minutes, followed by 5 minutes for questions. Cancellations received by October 31 will be refunded less a $40 handling fee.

────────────────────

[E-mail]
To: Dr. Kwame Adeyemi
From: Rosalind Vega <registration@biup.org>
Date: October 14
Subject: Your registration and presentation

Dear Dr. Adeyemi,

Thank you for registering for the full conference on October 8. Your payment has been received, and a receipt is attached. You also asked to add one ticket to the conference dinner; that charge will appear separately on your card.

Your paper, "Platform Crowding at Mid-Sized Rail Stations," has been accepted and placed in Session 3. Please note that this session has been moved to Room 118 because the room listed on our Web site is being renovated; the day and time are unchanged. You will be the second of four speakers.

Please upload your slides to the presenter portal by November 5. Our technicians will load them onto the room computer in advance, so personal laptops cannot be connected to the projector.

Finally, the session chair, Dr. Mei-Ling Hsu, would like to hold a brief meeting with all Session 3 speakers at 9:00 A.M. on the day of the session, at the door of the presentation room.

Best regards,

Rosalind Vega
Conference Registrar', 'NONE', '["Its starting time", "Its date", "Its location", "Its topic"]', 'C', 1, '[세부 사항] 정답 (C). "this session has been moved to Room 118 ... the day and time are unchanged"이므로 바뀐 것은 장소뿐이다. 오답: (A)·(B) 날짜와 시각은 그대로라고 명시했다. (D) 세션 주제 변경은 언급되지 않았다.', 179),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'When will Dr. Adeyemi most likely meet with Dr. Hsu?', 'Questions 176-180 refer to the following Web page and e-mail.

[Web page]
Brixfield Institute of Urban Planning
9th Annual Conference on Sustainable Transit
November 12-13, Calder Point Convention Center

Registration fees
Type | By October 10 | After October 10
Full conference (both days) | $280 | $340
Single day | $160 | $190
Student (both days, valid ID required) | $120 | $150

All fees include lunch and admission to the exhibit hall. The conference dinner on November 12 is an additional $55. Presenters must register for the full conference by October 10 in order to be listed in the printed program.

Schedule of paper sessions
November 12, 10:00 A.M. — Session 1: Bus Rapid Transit — Room 204
November 12, 2:00 P.M. — Session 2: Fare Policy and Equity — Room 204
November 13, 9:30 A.M. — Session 3: Rail Station Design — Harbor Room
November 13, 1:30 P.M. — Session 4: Cycling Networks — Harbor Room

Each presenter is allotted 20 minutes, followed by 5 minutes for questions. Cancellations received by October 31 will be refunded less a $40 handling fee.

────────────────────

[E-mail]
To: Dr. Kwame Adeyemi
From: Rosalind Vega <registration@biup.org>
Date: October 14
Subject: Your registration and presentation

Dear Dr. Adeyemi,

Thank you for registering for the full conference on October 8. Your payment has been received, and a receipt is attached. You also asked to add one ticket to the conference dinner; that charge will appear separately on your card.

Your paper, "Platform Crowding at Mid-Sized Rail Stations," has been accepted and placed in Session 3. Please note that this session has been moved to Room 118 because the room listed on our Web site is being renovated; the day and time are unchanged. You will be the second of four speakers.

Please upload your slides to the presenter portal by November 5. Our technicians will load them onto the room computer in advance, so personal laptops cannot be connected to the projector.

Finally, the session chair, Dr. Mei-Ling Hsu, would like to hold a brief meeting with all Session 3 speakers at 9:00 A.M. on the day of the session, at the door of the presentation room.

Best regards,

Rosalind Vega
Conference Registrar', 'NONE', '["On November 12 at 9:00 A.M.", "On November 13 at 9:00 A.M.", "On November 13 at 9:30 A.M.", "On November 13 at 1:30 P.M."]', 'B', 1, '[연계(문서1+2)] 정답 (B). 이메일의 "at 9:00 A.M. on the day of the session"과 웹 페이지 일정의 "November 13, 9:30 A.M. — Session 3"을 연결하면 11월 13일 오전 9시다(날짜·시각은 변경 없음). 오답: (A) 11월 12일은 Session 1·2의 날이다. (C) 9시 30분은 세션 시작 시각이다. (D) 1시 30분은 Session 4다.', 180);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'Why did Mr. Castellanos write the e-mail?', 'Questions 181-185 refer to the following e-mail and service record.

[E-mail]
To: Service Department <service@pellamaudio.com>
From: Jorge Castellanos
Date: February 3
Subject: Warranty repair for Pellam S40 speaker

Hello,

I bought a Pellam S40 wireless speaker from your online store on March 18 of last year. For the past two weeks, the unit has shut itself off after about ten minutes of use, even when fully charged. I have already reset it as described in the user manual, but the problem continues.

According to the warranty card, defects are covered for one year from the date of purchase, so I believe the speaker should be repaired at no cost. I have attached a copy of my order confirmation.

I should also mention that the carrying strap tore some months ago. I realize that this may not be covered, but if a replacement strap is available, please add it and let me know the price.

I will be away from February 20 to March 2, so I would appreciate it if the speaker could be returned before I leave. Please tell me where to send it.

Thank you,

Jorge Castellanos

────────────────────

[Service record]
Pellam Audio Service Record

Service order: SR-77105
Customer: Jorge Castellanos
Product: S40 wireless speaker | Serial no.: S40-118824
Received: February 9 | Completed: February 12 | Shipped: February 13 (two-day courier)

Work performed
Replaced battery module — Parts: $38.00 | Labor: $25.00 — Covered under warranty
Updated system software — No charge
Replaced carrying strap (accessory; not covered) — $9.00

Return shipping: No charge
Amount due: $9.00 (charged to card on file)

Technician''s notes: Fault traced to a defective battery module. Unit tested for four hours with no shutdown. Repaired parts are guaranteed for 90 days from the completion date or until the end of the original warranty, whichever is later.

Technician: A. Lindqvist', 'NONE', '["To request service on a product", "To order a new speaker", "To ask for a copy of a manual", "To complain about a late shipment"]', 'A', 1, '[주제·목적] 정답 (A). 스피커가 저절로 꺼지는 증상을 설명하고 "I believe the speaker should be repaired at no cost", "Please tell me where to send it"이라고 하므로 수리 요청이다. 오답: (B) 새 스피커가 아니라 끈만 추가로 요청했다. (C) 설명서는 이미 보고 초기화까지 했다. (D) 배송 지연 불만이 아니다.', 181),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about Mr. Castellanos?', 'Questions 181-185 refer to the following e-mail and service record.

[E-mail]
To: Service Department <service@pellamaudio.com>
From: Jorge Castellanos
Date: February 3
Subject: Warranty repair for Pellam S40 speaker

Hello,

I bought a Pellam S40 wireless speaker from your online store on March 18 of last year. For the past two weeks, the unit has shut itself off after about ten minutes of use, even when fully charged. I have already reset it as described in the user manual, but the problem continues.

According to the warranty card, defects are covered for one year from the date of purchase, so I believe the speaker should be repaired at no cost. I have attached a copy of my order confirmation.

I should also mention that the carrying strap tore some months ago. I realize that this may not be covered, but if a replacement strap is available, please add it and let me know the price.

I will be away from February 20 to March 2, so I would appreciate it if the speaker could be returned before I leave. Please tell me where to send it.

Thank you,

Jorge Castellanos

────────────────────

[Service record]
Pellam Audio Service Record

Service order: SR-77105
Customer: Jorge Castellanos
Product: S40 wireless speaker | Serial no.: S40-118824
Received: February 9 | Completed: February 12 | Shipped: February 13 (two-day courier)

Work performed
Replaced battery module — Parts: $38.00 | Labor: $25.00 — Covered under warranty
Updated system software — No charge
Replaced carrying strap (accessory; not covered) — $9.00

Return shipping: No charge
Amount due: $9.00 (charged to card on file)

Technician''s notes: Fault traced to a defective battery module. Unit tested for four hours with no shutdown. Repaired parts are guaranteed for 90 days from the completion date or until the end of the original warranty, whichever is later.

Technician: A. Lindqvist', 'NONE', '["He purchased the speaker at a retail store.", "He has misplaced his proof of purchase.", "He has had the speaker repaired by Pellam before.", "He attempted to solve the problem on his own."]', 'D', 1, '[추론] 정답 (D). "I have already reset it as described in the user manual, but the problem continues"에서 스스로 문제를 해결해 보려 했음을 알 수 있다. 오답: (A) "from your online store"에서 샀다. (B) 주문 확인서 사본을 첨부했다. (C) 이전 수리 이력은 언급되지 않았다.', 182),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is most likely true about the speaker?', 'Questions 181-185 refer to the following e-mail and service record.

[E-mail]
To: Service Department <service@pellamaudio.com>
From: Jorge Castellanos
Date: February 3
Subject: Warranty repair for Pellam S40 speaker

Hello,

I bought a Pellam S40 wireless speaker from your online store on March 18 of last year. For the past two weeks, the unit has shut itself off after about ten minutes of use, even when fully charged. I have already reset it as described in the user manual, but the problem continues.

According to the warranty card, defects are covered for one year from the date of purchase, so I believe the speaker should be repaired at no cost. I have attached a copy of my order confirmation.

I should also mention that the carrying strap tore some months ago. I realize that this may not be covered, but if a replacement strap is available, please add it and let me know the price.

I will be away from February 20 to March 2, so I would appreciate it if the speaker could be returned before I leave. Please tell me where to send it.

Thank you,

Jorge Castellanos

────────────────────

[Service record]
Pellam Audio Service Record

Service order: SR-77105
Customer: Jorge Castellanos
Product: S40 wireless speaker | Serial no.: S40-118824
Received: February 9 | Completed: February 12 | Shipped: February 13 (two-day courier)

Work performed
Replaced battery module — Parts: $38.00 | Labor: $25.00 — Covered under warranty
Updated system software — No charge
Replaced carrying strap (accessory; not covered) — $9.00

Return shipping: No charge
Amount due: $9.00 (charged to card on file)

Technician''s notes: Fault traced to a defective battery module. Unit tested for four hours with no shutdown. Repaired parts are guaranteed for 90 days from the completion date or until the end of the original warranty, whichever is later.

Technician: A. Lindqvist', 'NONE', '["It was repaired after its warranty had expired.", "It was sent back before Mr. Castellanos''s departure.", "It was replaced with a new unit.", "It was shipped on the day it was received."]', 'B', 1, '[연계(문서1+2)] 정답 (B). 이메일의 "I will be away from February 20"과 수리 내역서의 "Shipped: February 13 (two-day courier)"를 연결하면 출발 전에 발송되었다. 오답: (A) 구매일이 작년 3월 18일이라 2월 수리는 보증 기간 안이고 "Covered under warranty"라고 적혀 있다. (C) 배터리 모듈만 교체했다. (D) 2월 9일에 접수하고 13일에 발송했다.', 183),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'According to the service record, what caused the speaker to shut off?', 'Questions 181-185 refer to the following e-mail and service record.

[E-mail]
To: Service Department <service@pellamaudio.com>
From: Jorge Castellanos
Date: February 3
Subject: Warranty repair for Pellam S40 speaker

Hello,

I bought a Pellam S40 wireless speaker from your online store on March 18 of last year. For the past two weeks, the unit has shut itself off after about ten minutes of use, even when fully charged. I have already reset it as described in the user manual, but the problem continues.

According to the warranty card, defects are covered for one year from the date of purchase, so I believe the speaker should be repaired at no cost. I have attached a copy of my order confirmation.

I should also mention that the carrying strap tore some months ago. I realize that this may not be covered, but if a replacement strap is available, please add it and let me know the price.

I will be away from February 20 to March 2, so I would appreciate it if the speaker could be returned before I leave. Please tell me where to send it.

Thank you,

Jorge Castellanos

────────────────────

[Service record]
Pellam Audio Service Record

Service order: SR-77105
Customer: Jorge Castellanos
Product: S40 wireless speaker | Serial no.: S40-118824
Received: February 9 | Completed: February 12 | Shipped: February 13 (two-day courier)

Work performed
Replaced battery module — Parts: $38.00 | Labor: $25.00 — Covered under warranty
Updated system software — No charge
Replaced carrying strap (accessory; not covered) — $9.00

Return shipping: No charge
Amount due: $9.00 (charged to card on file)

Technician''s notes: Fault traced to a defective battery module. Unit tested for four hours with no shutdown. Repaired parts are guaranteed for 90 days from the completion date or until the end of the original warranty, whichever is later.

Technician: A. Lindqvist', 'NONE', '["Outdated system software", "A torn carrying strap", "A faulty battery component", "A damaged charging cable"]', 'C', 1, '[세부 사항] 정답 (C). Technician''s notes의 "Fault traced to a defective battery module"이 근거다. defective battery module → a faulty battery component. 오답: (A) 소프트웨어는 업데이트만 했을 뿐 원인으로 적혀 있지 않다. (B) 끈은 별도의 부속품 교체다. (D) 충전 케이블은 언급되지 않았다.', 184),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the guarantee on the repaired parts?', 'Questions 181-185 refer to the following e-mail and service record.

[E-mail]
To: Service Department <service@pellamaudio.com>
From: Jorge Castellanos
Date: February 3
Subject: Warranty repair for Pellam S40 speaker

Hello,

I bought a Pellam S40 wireless speaker from your online store on March 18 of last year. For the past two weeks, the unit has shut itself off after about ten minutes of use, even when fully charged. I have already reset it as described in the user manual, but the problem continues.

According to the warranty card, defects are covered for one year from the date of purchase, so I believe the speaker should be repaired at no cost. I have attached a copy of my order confirmation.

I should also mention that the carrying strap tore some months ago. I realize that this may not be covered, but if a replacement strap is available, please add it and let me know the price.

I will be away from February 20 to March 2, so I would appreciate it if the speaker could be returned before I leave. Please tell me where to send it.

Thank you,

Jorge Castellanos

────────────────────

[Service record]
Pellam Audio Service Record

Service order: SR-77105
Customer: Jorge Castellanos
Product: S40 wireless speaker | Serial no.: S40-118824
Received: February 9 | Completed: February 12 | Shipped: February 13 (two-day courier)

Work performed
Replaced battery module — Parts: $38.00 | Labor: $25.00 — Covered under warranty
Updated system software — No charge
Replaced carrying strap (accessory; not covered) — $9.00

Return shipping: No charge
Amount due: $9.00 (charged to card on file)

Technician''s notes: Fault traced to a defective battery module. Unit tested for four hours with no shutdown. Repaired parts are guaranteed for 90 days from the completion date or until the end of the original warranty, whichever is later.

Technician: A. Lindqvist', 'NONE', '["It lasts for one year from the completion date.", "It will remain in effect after the original warranty ends.", "It applies only to the software update.", "It ends on the day Mr. Castellanos returns from his trip."]', 'B', 1, '[연계(문서1+2)] 정답 (B). 수리 내역서의 "90 days from the completion date(February 12) or until the end of the original warranty, whichever is later"와 이메일의 구매일(작년 March 18, 보증 1년)을 연결하면, 원래 보증은 3월 18일에 끝나고 수리일로부터 90일은 5월 중순이므로 보증이 원래 보증 종료 뒤에도 이어진다. 오답: (A) 1년이 아니라 90일이다. (C) 수리한 부품에 적용된다. (D) 3월 2일은 보증과 관계없는 날짜다.', 185),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'According to the advertisement, what is available to all members?', 'Questions 186-190 refer to the following advertisement, form, and e-mail.

[Advertisement]
Tidemark Fitness & Aquatics
Now open at 300 Foundry Road, Brixfield

Choose the membership that fits your routine:

Basic — $39 per month — Gym floor and locker rooms, 5:00 A.M. to 11:00 P.M.
Plus — $55 per month — Everything in Basic, plus unlimited group classes (yoga, cycling, strength circuits)
Premier — $72 per month — Everything in Plus, plus the 25-meter pool, the sauna, and two guest passes each month

All memberships require a one-time enrollment fee of $50. Join by April 30 and we will waive the enrollment fee. Members who commit to 12 months receive a free fitness assessment with a certified trainer.

Personal training is available to all members at $45 per session. Towel service is included for Premier members and costs $5 per month for all others.

Tour the facility any day between 9:00 A.M. and 8:00 P.M. No appointment is needed.

────────────────────

[Form]
Tidemark Fitness & Aquatics
Membership Application

Date: April 22
Name: Beatrix Nakamura
Address: 58 Sorrel Court, Brixfield
E-mail: bnakamura@quillmail.com
Membership type: [ ] Basic  [X] Plus  [ ] Premier
Term: [X] 12 months  [ ] Month to month
Add-ons: [X] Towel service  [ ] Personal training
Start date: May 1
Payment method: Automatic monthly withdrawal from bank account ending 4471
How did you hear about us? Coworker (Samuel Ortiz, member)
Signature: Beatrix Nakamura

────────────────────

[E-mail]
To: Beatrix Nakamura
From: Tidemark Member Services <members@tidemarkfitness.com>
Date: June 10
Subject: Re: Membership change request

Dear Ms. Nakamura,

Thank you for your message. We are glad to hear that you have been enjoying the cycling classes. I have processed your request to upgrade your membership so that you can use the pool. The new rate will take effect on July 1, and your 12-month term will continue from your original start date; the upgrade does not extend your commitment.

Because towel service is part of your new membership, I have removed that add-on charge from your account as of July 1.

You also asked about the fitness assessment. Our records show that you have not yet scheduled it. Appointments are available with our trainer Callum Reyes on most weekday evenings; you can book one at the front desk or through the member app.

Finally, since Mr. Ortiz referred you, he was sent a one-month credit in May under our referral program. Should you refer a friend yourself, the same benefit will apply to you.

Sincerely,

Dalia Haddad
Member Services', 'NONE', '["Personal training for an added fee", "Two guest passes every month", "Complimentary daily towel service", "Unlimited group exercise classes"]', 'A', 1, '[세부 사항] 정답 (A). "Personal training is available to all members at $45 per session"이 근거다. 오답: (B) 게스트 이용권은 Premier에만 있다. (C) 수건 서비스는 Premier만 포함이고 나머지는 월 $5다. (D) 그룹 수업은 Plus 이상이다.', 186),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about Ms. Nakamura?', 'Questions 186-190 refer to the following advertisement, form, and e-mail.

[Advertisement]
Tidemark Fitness & Aquatics
Now open at 300 Foundry Road, Brixfield

Choose the membership that fits your routine:

Basic — $39 per month — Gym floor and locker rooms, 5:00 A.M. to 11:00 P.M.
Plus — $55 per month — Everything in Basic, plus unlimited group classes (yoga, cycling, strength circuits)
Premier — $72 per month — Everything in Plus, plus the 25-meter pool, the sauna, and two guest passes each month

All memberships require a one-time enrollment fee of $50. Join by April 30 and we will waive the enrollment fee. Members who commit to 12 months receive a free fitness assessment with a certified trainer.

Personal training is available to all members at $45 per session. Towel service is included for Premier members and costs $5 per month for all others.

Tour the facility any day between 9:00 A.M. and 8:00 P.M. No appointment is needed.

────────────────────

[Form]
Tidemark Fitness & Aquatics
Membership Application

Date: April 22
Name: Beatrix Nakamura
Address: 58 Sorrel Court, Brixfield
E-mail: bnakamura@quillmail.com
Membership type: [ ] Basic  [X] Plus  [ ] Premier
Term: [X] 12 months  [ ] Month to month
Add-ons: [X] Towel service  [ ] Personal training
Start date: May 1
Payment method: Automatic monthly withdrawal from bank account ending 4471
How did you hear about us? Coworker (Samuel Ortiz, member)
Signature: Beatrix Nakamura

────────────────────

[E-mail]
To: Beatrix Nakamura
From: Tidemark Member Services <members@tidemarkfitness.com>
Date: June 10
Subject: Re: Membership change request

Dear Ms. Nakamura,

Thank you for your message. We are glad to hear that you have been enjoying the cycling classes. I have processed your request to upgrade your membership so that you can use the pool. The new rate will take effect on July 1, and your 12-month term will continue from your original start date; the upgrade does not extend your commitment.

Because towel service is part of your new membership, I have removed that add-on charge from your account as of July 1.

You also asked about the fitness assessment. Our records show that you have not yet scheduled it. Appointments are available with our trainer Callum Reyes on most weekday evenings; you can book one at the front desk or through the member app.

Finally, since Mr. Ortiz referred you, he was sent a one-month credit in May under our referral program. Should you refer a friend yourself, the same benefit will apply to you.

Sincerely,

Dalia Haddad
Member Services', 'NONE', '["She toured the facility with Mr. Ortiz.", "She signed up for personal training.", "She selected the lowest-priced membership.", "She did not pay an enrollment fee."]', 'D', 1, '[연계(문서1+2)] 정답 (D). 광고의 "Join by April 30 and we will waive the enrollment fee"와 신청서의 "Date: April 22"를 연결하면 가입비가 면제되었다. 오답: (A) Ortiz는 소개한 동료일 뿐 함께 견학했다는 말이 없다. (B) Personal training 칸은 비어 있다. (C) 가장 싼 Basic이 아니라 Plus를 골랐다.', 187),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'How much will Ms. Nakamura most likely pay each month for her membership as of July 1?', 'Questions 186-190 refer to the following advertisement, form, and e-mail.

[Advertisement]
Tidemark Fitness & Aquatics
Now open at 300 Foundry Road, Brixfield

Choose the membership that fits your routine:

Basic — $39 per month — Gym floor and locker rooms, 5:00 A.M. to 11:00 P.M.
Plus — $55 per month — Everything in Basic, plus unlimited group classes (yoga, cycling, strength circuits)
Premier — $72 per month — Everything in Plus, plus the 25-meter pool, the sauna, and two guest passes each month

All memberships require a one-time enrollment fee of $50. Join by April 30 and we will waive the enrollment fee. Members who commit to 12 months receive a free fitness assessment with a certified trainer.

Personal training is available to all members at $45 per session. Towel service is included for Premier members and costs $5 per month for all others.

Tour the facility any day between 9:00 A.M. and 8:00 P.M. No appointment is needed.

────────────────────

[Form]
Tidemark Fitness & Aquatics
Membership Application

Date: April 22
Name: Beatrix Nakamura
Address: 58 Sorrel Court, Brixfield
E-mail: bnakamura@quillmail.com
Membership type: [ ] Basic  [X] Plus  [ ] Premier
Term: [X] 12 months  [ ] Month to month
Add-ons: [X] Towel service  [ ] Personal training
Start date: May 1
Payment method: Automatic monthly withdrawal from bank account ending 4471
How did you hear about us? Coworker (Samuel Ortiz, member)
Signature: Beatrix Nakamura

────────────────────

[E-mail]
To: Beatrix Nakamura
From: Tidemark Member Services <members@tidemarkfitness.com>
Date: June 10
Subject: Re: Membership change request

Dear Ms. Nakamura,

Thank you for your message. We are glad to hear that you have been enjoying the cycling classes. I have processed your request to upgrade your membership so that you can use the pool. The new rate will take effect on July 1, and your 12-month term will continue from your original start date; the upgrade does not extend your commitment.

Because towel service is part of your new membership, I have removed that add-on charge from your account as of July 1.

You also asked about the fitness assessment. Our records show that you have not yet scheduled it. Appointments are available with our trainer Callum Reyes on most weekday evenings; you can book one at the front desk or through the member app.

Finally, since Mr. Ortiz referred you, he was sent a one-month credit in May under our referral program. Should you refer a friend yourself, the same benefit will apply to you.

Sincerely,

Dalia Haddad
Member Services', 'NONE', '["$72", "$60", "$55", "$39"]', 'A', 1, '[연계(문서1+3)] 정답 (A). 이메일의 "upgrade your membership so that you can use the pool"과 광고에서 수영장이 포함된 등급이 Premier($72)뿐이라는 점을 연결한다. 수건 서비스는 Premier에 포함되어 추가 요금이 없어진다. 오답: (B) $60은 지금의 Plus $55 + 수건 $5다. (C) Plus 요금. (D) Basic 요금.', 188),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'Why is Ms. Nakamura entitled to a fitness assessment?', 'Questions 186-190 refer to the following advertisement, form, and e-mail.

[Advertisement]
Tidemark Fitness & Aquatics
Now open at 300 Foundry Road, Brixfield

Choose the membership that fits your routine:

Basic — $39 per month — Gym floor and locker rooms, 5:00 A.M. to 11:00 P.M.
Plus — $55 per month — Everything in Basic, plus unlimited group classes (yoga, cycling, strength circuits)
Premier — $72 per month — Everything in Plus, plus the 25-meter pool, the sauna, and two guest passes each month

All memberships require a one-time enrollment fee of $50. Join by April 30 and we will waive the enrollment fee. Members who commit to 12 months receive a free fitness assessment with a certified trainer.

Personal training is available to all members at $45 per session. Towel service is included for Premier members and costs $5 per month for all others.

Tour the facility any day between 9:00 A.M. and 8:00 P.M. No appointment is needed.

────────────────────

[Form]
Tidemark Fitness & Aquatics
Membership Application

Date: April 22
Name: Beatrix Nakamura
Address: 58 Sorrel Court, Brixfield
E-mail: bnakamura@quillmail.com
Membership type: [ ] Basic  [X] Plus  [ ] Premier
Term: [X] 12 months  [ ] Month to month
Add-ons: [X] Towel service  [ ] Personal training
Start date: May 1
Payment method: Automatic monthly withdrawal from bank account ending 4471
How did you hear about us? Coworker (Samuel Ortiz, member)
Signature: Beatrix Nakamura

────────────────────

[E-mail]
To: Beatrix Nakamura
From: Tidemark Member Services <members@tidemarkfitness.com>
Date: June 10
Subject: Re: Membership change request

Dear Ms. Nakamura,

Thank you for your message. We are glad to hear that you have been enjoying the cycling classes. I have processed your request to upgrade your membership so that you can use the pool. The new rate will take effect on July 1, and your 12-month term will continue from your original start date; the upgrade does not extend your commitment.

Because towel service is part of your new membership, I have removed that add-on charge from your account as of July 1.

You also asked about the fitness assessment. Our records show that you have not yet scheduled it. Appointments are available with our trainer Callum Reyes on most weekday evenings; you can book one at the front desk or through the member app.

Finally, since Mr. Ortiz referred you, he was sent a one-month credit in May under our referral program. Should you refer a friend yourself, the same benefit will apply to you.

Sincerely,

Dalia Haddad
Member Services', 'NONE', '["She upgraded her membership.", "She was referred by a member.", "She signed up for a one-year term.", "She paid for personal training."]', 'C', 1, '[연계(문서1+2)] 정답 (C). 광고의 "Members who commit to 12 months receive a free fitness assessment"와 신청서의 "Term: [X] 12 months"를 연결한다. 12 months → a one-year term. 오답: (A) 등급 변경은 평가와 관계없다. (B) 추천 혜택은 추천한 Ortiz가 받은 한 달 크레디트다. (D) 개인 지도는 신청하지 않았다.', 189),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'Why did Mr. Ortiz receive a credit?', 'Questions 186-190 refer to the following advertisement, form, and e-mail.

[Advertisement]
Tidemark Fitness & Aquatics
Now open at 300 Foundry Road, Brixfield

Choose the membership that fits your routine:

Basic — $39 per month — Gym floor and locker rooms, 5:00 A.M. to 11:00 P.M.
Plus — $55 per month — Everything in Basic, plus unlimited group classes (yoga, cycling, strength circuits)
Premier — $72 per month — Everything in Plus, plus the 25-meter pool, the sauna, and two guest passes each month

All memberships require a one-time enrollment fee of $50. Join by April 30 and we will waive the enrollment fee. Members who commit to 12 months receive a free fitness assessment with a certified trainer.

Personal training is available to all members at $45 per session. Towel service is included for Premier members and costs $5 per month for all others.

Tour the facility any day between 9:00 A.M. and 8:00 P.M. No appointment is needed.

────────────────────

[Form]
Tidemark Fitness & Aquatics
Membership Application

Date: April 22
Name: Beatrix Nakamura
Address: 58 Sorrel Court, Brixfield
E-mail: bnakamura@quillmail.com
Membership type: [ ] Basic  [X] Plus  [ ] Premier
Term: [X] 12 months  [ ] Month to month
Add-ons: [X] Towel service  [ ] Personal training
Start date: May 1
Payment method: Automatic monthly withdrawal from bank account ending 4471
How did you hear about us? Coworker (Samuel Ortiz, member)
Signature: Beatrix Nakamura

────────────────────

[E-mail]
To: Beatrix Nakamura
From: Tidemark Member Services <members@tidemarkfitness.com>
Date: June 10
Subject: Re: Membership change request

Dear Ms. Nakamura,

Thank you for your message. We are glad to hear that you have been enjoying the cycling classes. I have processed your request to upgrade your membership so that you can use the pool. The new rate will take effect on July 1, and your 12-month term will continue from your original start date; the upgrade does not extend your commitment.

Because towel service is part of your new membership, I have removed that add-on charge from your account as of July 1.

You also asked about the fitness assessment. Our records show that you have not yet scheduled it. Appointments are available with our trainer Callum Reyes on most weekday evenings; you can book one at the front desk or through the member app.

Finally, since Mr. Ortiz referred you, he was sent a one-month credit in May under our referral program. Should you refer a friend yourself, the same benefit will apply to you.

Sincerely,

Dalia Haddad
Member Services', 'NONE', '["He was billed incorrectly in May.", "He upgraded to a Premier membership.", "He completed a fitness assessment.", "He recommended Tidemark to a new member."]', 'D', 1, '[세부 사항] 정답 (D). 이메일의 "since Mr. Ortiz referred you, he was sent a one-month credit in May under our referral program"이 근거다. referred you → recommended Tidemark to a new member. 오답: (A) 청구 오류 이야기는 없다. (B) 등급을 올린 사람은 Nakamura다. (C) 체력 평가는 Nakamura가 아직 예약하지 않은 항목이다.', 190),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'According to the itinerary, what is included in Mr. Daoud''s fare?', 'Questions 191-195 refer to the following itinerary, e-mail, and receipt.

[Itinerary]
Aurelia Air Itinerary

Confirmation code: QX7R2M
Passenger: Mr. Bashir Daoud
Fare type: Flex Economy
Issued: August 4

Flight AU 318 — September 15 — Depart Brixfield (BXF) 7:10 A.M. — Arrive Port Halden (PHD) 9:25 A.M. — Seat 22C
Flight AU 327 — September 18 — Depart Port Halden (PHD) 6:45 P.M. — Arrive Brixfield (BXF) 9:00 P.M. — Seat 19D

Baggage: One carry-on bag and one checked bag (up to 23 kg) are included.

Fare rules
Saver Economy: Changes are not permitted.
Flex Economy: Date or time changes are permitted up to 3 hours before departure for a $30 fee per flight, plus any difference in fare.
Business: Changes are permitted free of charge.

Changes can be made at www.aureliaair.com or by calling 555-0188. Online check-in opens 24 hours before departure.

────────────────────

[E-mail]
To: Carmen Iglesias
From: Bashir Daoud
Date: September 9
Subject: Port Halden trip

Hi Carmen,

The client in Port Halden has just added a site visit to our agenda. It will take place at their distribution center on the morning of September 19, so I will not be able to fly home on the 18th as planned. Could you move my return to the 19th? Any flight leaving after 3:00 P.M. would work, since the visit should wrap up by noon and the center is about an hour from the airport.

My outbound flight can stay as it is. I will also need one more night at the Larkmoor Hotel; I have already called them, and they extended my reservation at the same rate, so there is nothing for you to do on that front.

Please charge any airline fees to the department''s travel card and forward the receipt to me for my expense report.

Thanks,
Bashir

────────────────────

[Receipt]
Aurelia Air Change Receipt

Confirmation code: QX7R2M
Passenger: Mr. Bashir Daoud
Date of change: September 10

Canceled flight: AU 327 — September 18 — Port Halden to Brixfield — Depart 6:45 P.M.
New flight: AU 325 — September 19 — Port Halden to Brixfield — Depart 4:20 P.M. — Arrive 6:35 P.M. — Seat 8A

Change fee: $30.00
Fare difference: $46.00
Total charged: $76.00
Payment: Corporate card ending 9042

Your seat on the new flight is in the Extra Legroom section. It has been provided at no charge because no standard seats were available. All other details of your itinerary remain unchanged.', 'NONE', '["A meal on each flight", "One piece of checked luggage", "Unlimited free changes", "A seat with extra legroom"]', 'B', 1, '[세부 사항] 정답 (B). "One carry-on bag and one checked bag (up to 23 kg) are included"가 근거다. one checked bag → one piece of checked luggage. 오답: (A) 기내식 언급이 없다. (C) Flex Economy는 편당 $30 수수료와 운임 차액을 낸다. (D) 추가 공간 좌석은 변경 뒤 영수증에 나오는 내용으로 운임 포함 항목이 아니다.', 191),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'Why does Mr. Daoud want to change his return flight?', 'Questions 191-195 refer to the following itinerary, e-mail, and receipt.

[Itinerary]
Aurelia Air Itinerary

Confirmation code: QX7R2M
Passenger: Mr. Bashir Daoud
Fare type: Flex Economy
Issued: August 4

Flight AU 318 — September 15 — Depart Brixfield (BXF) 7:10 A.M. — Arrive Port Halden (PHD) 9:25 A.M. — Seat 22C
Flight AU 327 — September 18 — Depart Port Halden (PHD) 6:45 P.M. — Arrive Brixfield (BXF) 9:00 P.M. — Seat 19D

Baggage: One carry-on bag and one checked bag (up to 23 kg) are included.

Fare rules
Saver Economy: Changes are not permitted.
Flex Economy: Date or time changes are permitted up to 3 hours before departure for a $30 fee per flight, plus any difference in fare.
Business: Changes are permitted free of charge.

Changes can be made at www.aureliaair.com or by calling 555-0188. Online check-in opens 24 hours before departure.

────────────────────

[E-mail]
To: Carmen Iglesias
From: Bashir Daoud
Date: September 9
Subject: Port Halden trip

Hi Carmen,

The client in Port Halden has just added a site visit to our agenda. It will take place at their distribution center on the morning of September 19, so I will not be able to fly home on the 18th as planned. Could you move my return to the 19th? Any flight leaving after 3:00 P.M. would work, since the visit should wrap up by noon and the center is about an hour from the airport.

My outbound flight can stay as it is. I will also need one more night at the Larkmoor Hotel; I have already called them, and they extended my reservation at the same rate, so there is nothing for you to do on that front.

Please charge any airline fees to the department''s travel card and forward the receipt to me for my expense report.

Thanks,
Bashir

────────────────────

[Receipt]
Aurelia Air Change Receipt

Confirmation code: QX7R2M
Passenger: Mr. Bashir Daoud
Date of change: September 10

Canceled flight: AU 327 — September 18 — Port Halden to Brixfield — Depart 6:45 P.M.
New flight: AU 325 — September 19 — Port Halden to Brixfield — Depart 4:20 P.M. — Arrive 6:35 P.M. — Seat 8A

Change fee: $30.00
Fare difference: $46.00
Total charged: $76.00
Payment: Corporate card ending 9042

Your seat on the new flight is in the Extra Legroom section. It has been provided at no charge because no standard seats were available. All other details of your itinerary remain unchanged.', 'NONE', '["His client canceled a meeting.", "His hotel has no rooms available.", "An activity was added to his schedule.", "His original flight was delayed."]', 'C', 1, '[세부 사항] 정답 (C). "The client in Port Halden has just added a site visit to our agenda ... so I will not be able to fly home on the 18th"가 근거다. added a site visit → an activity was added. 오답: (A) 회의가 취소된 것이 아니라 일정이 추가되었다. (B) 호텔은 숙박을 연장해 주었다. (D) 항공편 지연 이야기는 없다.', 192),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the Larkmoor Hotel?', 'Questions 191-195 refer to the following itinerary, e-mail, and receipt.

[Itinerary]
Aurelia Air Itinerary

Confirmation code: QX7R2M
Passenger: Mr. Bashir Daoud
Fare type: Flex Economy
Issued: August 4

Flight AU 318 — September 15 — Depart Brixfield (BXF) 7:10 A.M. — Arrive Port Halden (PHD) 9:25 A.M. — Seat 22C
Flight AU 327 — September 18 — Depart Port Halden (PHD) 6:45 P.M. — Arrive Brixfield (BXF) 9:00 P.M. — Seat 19D

Baggage: One carry-on bag and one checked bag (up to 23 kg) are included.

Fare rules
Saver Economy: Changes are not permitted.
Flex Economy: Date or time changes are permitted up to 3 hours before departure for a $30 fee per flight, plus any difference in fare.
Business: Changes are permitted free of charge.

Changes can be made at www.aureliaair.com or by calling 555-0188. Online check-in opens 24 hours before departure.

────────────────────

[E-mail]
To: Carmen Iglesias
From: Bashir Daoud
Date: September 9
Subject: Port Halden trip

Hi Carmen,

The client in Port Halden has just added a site visit to our agenda. It will take place at their distribution center on the morning of September 19, so I will not be able to fly home on the 18th as planned. Could you move my return to the 19th? Any flight leaving after 3:00 P.M. would work, since the visit should wrap up by noon and the center is about an hour from the airport.

My outbound flight can stay as it is. I will also need one more night at the Larkmoor Hotel; I have already called them, and they extended my reservation at the same rate, so there is nothing for you to do on that front.

Please charge any airline fees to the department''s travel card and forward the receipt to me for my expense report.

Thanks,
Bashir

────────────────────

[Receipt]
Aurelia Air Change Receipt

Confirmation code: QX7R2M
Passenger: Mr. Bashir Daoud
Date of change: September 10

Canceled flight: AU 327 — September 18 — Port Halden to Brixfield — Depart 6:45 P.M.
New flight: AU 325 — September 19 — Port Halden to Brixfield — Depart 4:20 P.M. — Arrive 6:35 P.M. — Seat 8A

Change fee: $30.00
Fare difference: $46.00
Total charged: $76.00
Payment: Corporate card ending 9042

Your seat on the new flight is in the Extra Legroom section. It has been provided at no charge because no standard seats were available. All other details of your itinerary remain unchanged.', 'NONE', '["It is about an hour from the airport.", "It raised its rate for the extra night.", "It was originally booked by Ms. Iglesias.", "It had a room available for an additional night."]', 'D', 1, '[추론] 정답 (D). "I have already called them, and they extended my reservation at the same rate"에서 하루 더 묵을 방이 있었음을 알 수 있다. 오답: (A) 공항에서 한 시간 거리인 곳은 고객사의 물류센터다. (B) at the same rate이므로 요금은 같다. (C) 처음 예약을 누가 했는지는 나와 있지 않다.', 193),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about flight AU 325?', 'Questions 191-195 refer to the following itinerary, e-mail, and receipt.

[Itinerary]
Aurelia Air Itinerary

Confirmation code: QX7R2M
Passenger: Mr. Bashir Daoud
Fare type: Flex Economy
Issued: August 4

Flight AU 318 — September 15 — Depart Brixfield (BXF) 7:10 A.M. — Arrive Port Halden (PHD) 9:25 A.M. — Seat 22C
Flight AU 327 — September 18 — Depart Port Halden (PHD) 6:45 P.M. — Arrive Brixfield (BXF) 9:00 P.M. — Seat 19D

Baggage: One carry-on bag and one checked bag (up to 23 kg) are included.

Fare rules
Saver Economy: Changes are not permitted.
Flex Economy: Date or time changes are permitted up to 3 hours before departure for a $30 fee per flight, plus any difference in fare.
Business: Changes are permitted free of charge.

Changes can be made at www.aureliaair.com or by calling 555-0188. Online check-in opens 24 hours before departure.

────────────────────

[E-mail]
To: Carmen Iglesias
From: Bashir Daoud
Date: September 9
Subject: Port Halden trip

Hi Carmen,

The client in Port Halden has just added a site visit to our agenda. It will take place at their distribution center on the morning of September 19, so I will not be able to fly home on the 18th as planned. Could you move my return to the 19th? Any flight leaving after 3:00 P.M. would work, since the visit should wrap up by noon and the center is about an hour from the airport.

My outbound flight can stay as it is. I will also need one more night at the Larkmoor Hotel; I have already called them, and they extended my reservation at the same rate, so there is nothing for you to do on that front.

Please charge any airline fees to the department''s travel card and forward the receipt to me for my expense report.

Thanks,
Bashir

────────────────────

[Receipt]
Aurelia Air Change Receipt

Confirmation code: QX7R2M
Passenger: Mr. Bashir Daoud
Date of change: September 10

Canceled flight: AU 327 — September 18 — Port Halden to Brixfield — Depart 6:45 P.M.
New flight: AU 325 — September 19 — Port Halden to Brixfield — Depart 4:20 P.M. — Arrive 6:35 P.M. — Seat 8A

Change fee: $30.00
Fare difference: $46.00
Total charged: $76.00
Payment: Corporate card ending 9042

Your seat on the new flight is in the Extra Legroom section. It has been provided at no charge because no standard seats were available. All other details of your itinerary remain unchanged.', 'NONE', '["It lands in Port Halden at 6:35 P.M.", "It is operated by a different airline.", "It leaves after the time Mr. Daoud specified.", "It costs less than his original return flight."]', 'C', 1, '[연계(문서2+3)] 정답 (C). 이메일의 "Any flight leaving after 3:00 P.M. would work"와 영수증의 "AU 325 ... Depart 4:20 P.M."을 연결하면 요청한 시각 이후에 출발한다. 오답: (A) 6시 35분에 도착하는 곳은 Brixfield다. (B) 같은 Aurelia Air 항공편이다. (D) Fare difference $46.00을 더 냈으므로 더 비싸다.', 194),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is most likely true about Mr. Daoud''s trip?', 'Questions 191-195 refer to the following itinerary, e-mail, and receipt.

[Itinerary]
Aurelia Air Itinerary

Confirmation code: QX7R2M
Passenger: Mr. Bashir Daoud
Fare type: Flex Economy
Issued: August 4

Flight AU 318 — September 15 — Depart Brixfield (BXF) 7:10 A.M. — Arrive Port Halden (PHD) 9:25 A.M. — Seat 22C
Flight AU 327 — September 18 — Depart Port Halden (PHD) 6:45 P.M. — Arrive Brixfield (BXF) 9:00 P.M. — Seat 19D

Baggage: One carry-on bag and one checked bag (up to 23 kg) are included.

Fare rules
Saver Economy: Changes are not permitted.
Flex Economy: Date or time changes are permitted up to 3 hours before departure for a $30 fee per flight, plus any difference in fare.
Business: Changes are permitted free of charge.

Changes can be made at www.aureliaair.com or by calling 555-0188. Online check-in opens 24 hours before departure.

────────────────────

[E-mail]
To: Carmen Iglesias
From: Bashir Daoud
Date: September 9
Subject: Port Halden trip

Hi Carmen,

The client in Port Halden has just added a site visit to our agenda. It will take place at their distribution center on the morning of September 19, so I will not be able to fly home on the 18th as planned. Could you move my return to the 19th? Any flight leaving after 3:00 P.M. would work, since the visit should wrap up by noon and the center is about an hour from the airport.

My outbound flight can stay as it is. I will also need one more night at the Larkmoor Hotel; I have already called them, and they extended my reservation at the same rate, so there is nothing for you to do on that front.

Please charge any airline fees to the department''s travel card and forward the receipt to me for my expense report.

Thanks,
Bashir

────────────────────

[Receipt]
Aurelia Air Change Receipt

Confirmation code: QX7R2M
Passenger: Mr. Bashir Daoud
Date of change: September 10

Canceled flight: AU 327 — September 18 — Port Halden to Brixfield — Depart 6:45 P.M.
New flight: AU 325 — September 19 — Port Halden to Brixfield — Depart 4:20 P.M. — Arrive 6:35 P.M. — Seat 8A

Change fee: $30.00
Fare difference: $46.00
Total charged: $76.00
Payment: Corporate card ending 9042

Your seat on the new flight is in the Extra Legroom section. It has been provided at no charge because no standard seats were available. All other details of your itinerary remain unchanged.', 'NONE', '["He will arrive in Brixfield at 9:00 P.M. on September 19.", "He will leave Brixfield on a 7:10 A.M. flight.", "He will sit in seat 19D on his way home.", "He will pay extra for additional legroom."]', 'B', 1, '[연계(문서1+3)] 정답 (B). 영수증의 "All other details of your itinerary remain unchanged"와 여정표의 "AU 318 — September 15 — Depart Brixfield (BXF) 7:10 A.M."을 연결하면 가는 편은 그대로다. 오답: (A) 새 귀국편은 6시 35분에 도착한다. (C) 19D는 취소된 AU 327의 좌석이고 새 좌석은 8A다. (D) 추가 공간 좌석은 "at no charge"다.', 195),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'According to the Web page, what is true about all of the listed properties?', 'Questions 196-200 refer to the following Web page and e-mails.

[Web page]
Calloway Commercial Realty
Retail and Office Space for Lease in Calder Point

Unit 2, 14 Fenwick Street — 60 square meters — $1,900 per month
Ground-floor shop front with a large display window. Suited to retail. No kitchen facilities.

Unit 5, 14 Fenwick Street — 85 square meters — $2,300 per month
Second-floor office suite with two private rooms and a reception area. Elevator access.

9 Marlowe Lane — 110 square meters — $3,100 per month
Former café with a fully equipped commercial kitchen and seating for 40. Outdoor terrace.

31 Saltmarsh Road — 140 square meters — $2,800 per month
Spacious open-plan studio in a converted warehouse. Loading door at the rear; on-site parking for six vehicles.

All leases run for a minimum of two years. A security deposit equal to two months'' rent is due at signing. Tenants are responsible for electricity and water; building insurance and exterior maintenance are covered by the owner. To arrange a viewing, contact Hugo Marchetti at hmarchetti@callowayrealty.com.

────────────────────

[E-mail]
To: Hugo Marchetti
From: Soraya Mbeki
Date: July 7
Subject: Space for a cooking school

Dear Mr. Marchetti,

I am the owner of Sage & Ember, a catering company based in Brixfield, and I am planning to open a small cooking school in Calder Point this autumn. I saw your listings and would like to view the property that already has a professional kitchen, since installing one elsewhere would be beyond my budget.

Before we meet, I have two questions. First, your listing mentions a minimum two-year lease. Because this is a new venture, I would prefer a one-year term with an option to renew; is the owner open to that? Second, I would need to put up a sign facing the street. Is that permitted?

I am available for a viewing on July 10 or July 11 at any time after 2:00 P.M.

Kind regards,

Soraya Mbeki

────────────────────

[E-mail]
To: Soraya Mbeki
From: Hugo Marchetti
Date: July 8
Subject: Re: Space for a cooking school

Dear Ms. Mbeki,

Thank you for your interest. I have spoken with the owner, Mr. Pryce. He is not willing to shorten the lease, but he has offered to reduce the rent by $200 per month for the first six months to help you get established. He is glad to have a sign installed, provided that its design is submitted to him for approval beforehand.

I can show you the property on July 11 at 3:30 P.M. Please note that the terrace is being resurfaced until the end of the month, so you will not be able to walk on it, but you will be able to see it from the dining room.

Should you decide to proceed, a signed lease and the deposit would be needed by August 1 for an occupancy date of September 1.

Regards,

Hugo Marchetti
Leasing Agent', 'NONE', '["Their tenants pay their own utility bills.", "They include on-site parking.", "They are located on the same street.", "They can be leased for one year."]', 'A', 1, '[사실 확인] 정답 (A). 공통 조건의 "Tenants are responsible for electricity and water"가 근거다. electricity and water → utility bills. 오답: (B) 주차장은 31 Saltmarsh Road에만 있다. (C) Fenwick Street, Marlowe Lane, Saltmarsh Road로 서로 다르다. (D) "a minimum of two years"다.', 196),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'Which property does Ms. Mbeki most likely want to view?', 'Questions 196-200 refer to the following Web page and e-mails.

[Web page]
Calloway Commercial Realty
Retail and Office Space for Lease in Calder Point

Unit 2, 14 Fenwick Street — 60 square meters — $1,900 per month
Ground-floor shop front with a large display window. Suited to retail. No kitchen facilities.

Unit 5, 14 Fenwick Street — 85 square meters — $2,300 per month
Second-floor office suite with two private rooms and a reception area. Elevator access.

9 Marlowe Lane — 110 square meters — $3,100 per month
Former café with a fully equipped commercial kitchen and seating for 40. Outdoor terrace.

31 Saltmarsh Road — 140 square meters — $2,800 per month
Spacious open-plan studio in a converted warehouse. Loading door at the rear; on-site parking for six vehicles.

All leases run for a minimum of two years. A security deposit equal to two months'' rent is due at signing. Tenants are responsible for electricity and water; building insurance and exterior maintenance are covered by the owner. To arrange a viewing, contact Hugo Marchetti at hmarchetti@callowayrealty.com.

────────────────────

[E-mail]
To: Hugo Marchetti
From: Soraya Mbeki
Date: July 7
Subject: Space for a cooking school

Dear Mr. Marchetti,

I am the owner of Sage & Ember, a catering company based in Brixfield, and I am planning to open a small cooking school in Calder Point this autumn. I saw your listings and would like to view the property that already has a professional kitchen, since installing one elsewhere would be beyond my budget.

Before we meet, I have two questions. First, your listing mentions a minimum two-year lease. Because this is a new venture, I would prefer a one-year term with an option to renew; is the owner open to that? Second, I would need to put up a sign facing the street. Is that permitted?

I am available for a viewing on July 10 or July 11 at any time after 2:00 P.M.

Kind regards,

Soraya Mbeki

────────────────────

[E-mail]
To: Soraya Mbeki
From: Hugo Marchetti
Date: July 8
Subject: Re: Space for a cooking school

Dear Ms. Mbeki,

Thank you for your interest. I have spoken with the owner, Mr. Pryce. He is not willing to shorten the lease, but he has offered to reduce the rent by $200 per month for the first six months to help you get established. He is glad to have a sign installed, provided that its design is submitted to him for approval beforehand.

I can show you the property on July 11 at 3:30 P.M. Please note that the terrace is being resurfaced until the end of the month, so you will not be able to walk on it, but you will be able to see it from the dining room.

Should you decide to proceed, a signed lease and the deposit would be needed by August 1 for an occupancy date of September 1.

Regards,

Hugo Marchetti
Leasing Agent', 'NONE', '["Unit 2, 14 Fenwick Street", "Unit 5, 14 Fenwick Street", "9 Marlowe Lane", "31 Saltmarsh Road"]', 'C', 1, '[연계(문서1+2)] 정답 (C). 첫 이메일의 "the property that already has a professional kitchen"과 웹 페이지의 "9 Marlowe Lane ... a fully equipped commercial kitchen"을 연결한다. 오답: (A) "No kitchen facilities"라고 적혀 있다. (B) 사무실용 공간이다. (D) 창고를 개조한 스튜디오로 주방 언급이 없다.', 197),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'In the first e-mail, the word "term" in paragraph 2, line 3, is closest in meaning to', 'Questions 196-200 refer to the following Web page and e-mails.

[Web page]
Calloway Commercial Realty
Retail and Office Space for Lease in Calder Point

Unit 2, 14 Fenwick Street — 60 square meters — $1,900 per month
Ground-floor shop front with a large display window. Suited to retail. No kitchen facilities.

Unit 5, 14 Fenwick Street — 85 square meters — $2,300 per month
Second-floor office suite with two private rooms and a reception area. Elevator access.

9 Marlowe Lane — 110 square meters — $3,100 per month
Former café with a fully equipped commercial kitchen and seating for 40. Outdoor terrace.

31 Saltmarsh Road — 140 square meters — $2,800 per month
Spacious open-plan studio in a converted warehouse. Loading door at the rear; on-site parking for six vehicles.

All leases run for a minimum of two years. A security deposit equal to two months'' rent is due at signing. Tenants are responsible for electricity and water; building insurance and exterior maintenance are covered by the owner. To arrange a viewing, contact Hugo Marchetti at hmarchetti@callowayrealty.com.

────────────────────

[E-mail]
To: Hugo Marchetti
From: Soraya Mbeki
Date: July 7
Subject: Space for a cooking school

Dear Mr. Marchetti,

I am the owner of Sage & Ember, a catering company based in Brixfield, and I am planning to open a small cooking school in Calder Point this autumn. I saw your listings and would like to view the property that already has a professional kitchen, since installing one elsewhere would be beyond my budget.

Before we meet, I have two questions. First, your listing mentions a minimum two-year lease. Because this is a new venture, I would prefer a one-year term with an option to renew; is the owner open to that? Second, I would need to put up a sign facing the street. Is that permitted?

I am available for a viewing on July 10 or July 11 at any time after 2:00 P.M.

Kind regards,

Soraya Mbeki

────────────────────

[E-mail]
To: Soraya Mbeki
From: Hugo Marchetti
Date: July 8
Subject: Re: Space for a cooking school

Dear Ms. Mbeki,

Thank you for your interest. I have spoken with the owner, Mr. Pryce. He is not willing to shorten the lease, but he has offered to reduce the rent by $200 per month for the first six months to help you get established. He is glad to have a sign installed, provided that its design is submitted to him for approval beforehand.

I can show you the property on July 11 at 3:30 P.M. Please note that the terrace is being resurfaced until the end of the month, so you will not be able to walk on it, but you will be able to see it from the dining room.

Should you decide to proceed, a signed lease and the deposit would be needed by August 1 for an occupancy date of September 1.

Regards,

Hugo Marchetti
Leasing Agent', 'NONE', '["duration", "condition", "expression", "semester"]', 'A', 1, '[동의어] 정답 (A). "a one-year term with an option to renew"는 1년의 계약 ''기간''을 뜻하므로 duration이 가장 가깝다. 오답: (B) condition은 terms(조건)의 뜻이다. (C) expression은 ''용어''라는 뜻의 term이다. (D) semester는 ''학기''라는 뜻의 term이라 임대 계약 문맥과 맞지 않는다.', 198),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What does Mr. Marchetti indicate about the owner of the property?', 'Questions 196-200 refer to the following Web page and e-mails.

[Web page]
Calloway Commercial Realty
Retail and Office Space for Lease in Calder Point

Unit 2, 14 Fenwick Street — 60 square meters — $1,900 per month
Ground-floor shop front with a large display window. Suited to retail. No kitchen facilities.

Unit 5, 14 Fenwick Street — 85 square meters — $2,300 per month
Second-floor office suite with two private rooms and a reception area. Elevator access.

9 Marlowe Lane — 110 square meters — $3,100 per month
Former café with a fully equipped commercial kitchen and seating for 40. Outdoor terrace.

31 Saltmarsh Road — 140 square meters — $2,800 per month
Spacious open-plan studio in a converted warehouse. Loading door at the rear; on-site parking for six vehicles.

All leases run for a minimum of two years. A security deposit equal to two months'' rent is due at signing. Tenants are responsible for electricity and water; building insurance and exterior maintenance are covered by the owner. To arrange a viewing, contact Hugo Marchetti at hmarchetti@callowayrealty.com.

────────────────────

[E-mail]
To: Hugo Marchetti
From: Soraya Mbeki
Date: July 7
Subject: Space for a cooking school

Dear Mr. Marchetti,

I am the owner of Sage & Ember, a catering company based in Brixfield, and I am planning to open a small cooking school in Calder Point this autumn. I saw your listings and would like to view the property that already has a professional kitchen, since installing one elsewhere would be beyond my budget.

Before we meet, I have two questions. First, your listing mentions a minimum two-year lease. Because this is a new venture, I would prefer a one-year term with an option to renew; is the owner open to that? Second, I would need to put up a sign facing the street. Is that permitted?

I am available for a viewing on July 10 or July 11 at any time after 2:00 P.M.

Kind regards,

Soraya Mbeki

────────────────────

[E-mail]
To: Soraya Mbeki
From: Hugo Marchetti
Date: July 8
Subject: Re: Space for a cooking school

Dear Ms. Mbeki,

Thank you for your interest. I have spoken with the owner, Mr. Pryce. He is not willing to shorten the lease, but he has offered to reduce the rent by $200 per month for the first six months to help you get established. He is glad to have a sign installed, provided that its design is submitted to him for approval beforehand.

I can show you the property on July 11 at 3:30 P.M. Please note that the terrace is being resurfaced until the end of the month, so you will not be able to walk on it, but you will be able to see it from the dining room.

Should you decide to proceed, a signed lease and the deposit would be needed by August 1 for an occupancy date of September 1.

Regards,

Hugo Marchetti
Leasing Agent', 'NONE', '["He has agreed to a one-year lease.", "He will lower the rent permanently.", "He must approve the design of a sign.", "He plans to attend the viewing."]', 'C', 1, '[세부 사항] 정답 (C). "He is glad to have a sign installed, provided that its design is submitted to him for approval beforehand"가 근거다. 오답: (A) "He is not willing to shorten the lease". (B) 임대료 인하는 처음 6개월뿐이다. (D) 매물을 보여 주는 사람은 Marchetti이고 주인의 참석 언급은 없다.', 199),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'How much rent would Ms. Mbeki most likely pay per month during the first six months of her lease?', 'Questions 196-200 refer to the following Web page and e-mails.

[Web page]
Calloway Commercial Realty
Retail and Office Space for Lease in Calder Point

Unit 2, 14 Fenwick Street — 60 square meters — $1,900 per month
Ground-floor shop front with a large display window. Suited to retail. No kitchen facilities.

Unit 5, 14 Fenwick Street — 85 square meters — $2,300 per month
Second-floor office suite with two private rooms and a reception area. Elevator access.

9 Marlowe Lane — 110 square meters — $3,100 per month
Former café with a fully equipped commercial kitchen and seating for 40. Outdoor terrace.

31 Saltmarsh Road — 140 square meters — $2,800 per month
Spacious open-plan studio in a converted warehouse. Loading door at the rear; on-site parking for six vehicles.

All leases run for a minimum of two years. A security deposit equal to two months'' rent is due at signing. Tenants are responsible for electricity and water; building insurance and exterior maintenance are covered by the owner. To arrange a viewing, contact Hugo Marchetti at hmarchetti@callowayrealty.com.

────────────────────

[E-mail]
To: Hugo Marchetti
From: Soraya Mbeki
Date: July 7
Subject: Space for a cooking school

Dear Mr. Marchetti,

I am the owner of Sage & Ember, a catering company based in Brixfield, and I am planning to open a small cooking school in Calder Point this autumn. I saw your listings and would like to view the property that already has a professional kitchen, since installing one elsewhere would be beyond my budget.

Before we meet, I have two questions. First, your listing mentions a minimum two-year lease. Because this is a new venture, I would prefer a one-year term with an option to renew; is the owner open to that? Second, I would need to put up a sign facing the street. Is that permitted?

I am available for a viewing on July 10 or July 11 at any time after 2:00 P.M.

Kind regards,

Soraya Mbeki

────────────────────

[E-mail]
To: Soraya Mbeki
From: Hugo Marchetti
Date: July 8
Subject: Re: Space for a cooking school

Dear Ms. Mbeki,

Thank you for your interest. I have spoken with the owner, Mr. Pryce. He is not willing to shorten the lease, but he has offered to reduce the rent by $200 per month for the first six months to help you get established. He is glad to have a sign installed, provided that its design is submitted to him for approval beforehand.

I can show you the property on July 11 at 3:30 P.M. Please note that the terrace is being resurfaced until the end of the month, so you will not be able to walk on it, but you will be able to see it from the dining room.

Should you decide to proceed, a signed lease and the deposit would be needed by August 1 for an occupancy date of September 1.

Regards,

Hugo Marchetti
Leasing Agent', 'NONE', '["$2,900", "$2,800", "$2,600", "$2,100"]', 'A', 1, '[연계(문서1+3)] 정답 (A). 웹 페이지에서 9 Marlowe Lane의 임대료는 월 $3,100이고, 답장의 "reduce the rent by $200 per month for the first six months"를 적용하면 $2,900이다. 오답: (B) 31 Saltmarsh Road의 정가. (C) Saltmarsh Road에서 $200을 뺀 값. (D) Unit 5에서 $200을 뺀 값.', 200);
