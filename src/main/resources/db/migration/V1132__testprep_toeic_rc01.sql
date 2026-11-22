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
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'The Fenmore Herald is the most ------- circulated daily newspaper in the entire coastal region.', NULL, 'NONE', '["widely", "wide", "widest", "widening"]', 'A', 1, '[문법-품사(부사: 과거분사 수식)] 정답 (A). 빈칸은 뒤의 과거분사 circulated(배포되는)를 꾸미는 자리이므로 부사 widely가 필요하다. the most widely circulated newspaper는 ''가장 널리 배포되는 신문''이다. 오답: (B) wide는 형용사로 분사를 꾸미지 못한다. (C) widest는 최상급이라 앞의 the most와 겹친다. (D) widening은 분사라 circulated를 꾸미지 못한다.
포인트: 과거분사를 꾸미는 부사(widely circulated)', 101),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'City officials chose Voltway chargers because the units have proved ------- even in extremely cold weather.', NULL, 'NONE', '["spacious", "voluntary", "reliable", "absent"]', 'C', 1, '[어휘-형용사(상황 어휘)] 정답 (C). 혹한에서도 문제없이 작동했다는 것이 선택의 이유이므로 ''믿을 만한, 안정적으로 작동하는''이라는 뜻의 reliable이 맞다. 오답: (A) spacious는 공간이 넓다는 뜻이라 충전기의 작동과 무관하다. (B) voluntary는 ''자발적인'', (D) absent는 ''없는''이라는 뜻으로 선택한 이유가 되지 못한다.
포인트: 형용사 어휘 reliable(같은 품사는 뜻으로 구별)', 102),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Any contractor that fails to comply ------- the terms of the agreement may be removed from the project.', NULL, 'NONE', '["to", "by", "under", "with"]', 'D', 1, '[전치사(동사+전치사 결합)] 정답 (D). ''규칙·조건을 따르다''는 comply with로 쓴다. 동사와 전치사를 한 묶음으로 외워야 하는 문제이다. 오답: (A) to는 conform to, (B) by는 abide by와 헷갈리게 한 것이고, (C) under도 comply 뒤에서 따라야 할 대상을 연결하지 못한다.
포인트: 연어 comply with', 103),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'A description of the allergens in our boxed lunches ------- printed on every container.', NULL, 'NONE', '["were", "is", "be", "being"]', 'B', 1, '[문법-수일치(주어의 중심 명사)] 정답 (B). 주어의 중심 명사는 단수 A description이고, of the allergens in our boxed lunches는 수식어이다. 따라서 단수동사 is가 맞다. 오답: (A) were는 복수동사라 가까운 복수명사 lunches에 속은 답이다. (C) be는 동사원형이라 이 문장의 단수 주어 뒤에서 동사가 될 수 없다. (D) being은 혼자서 문장의 동사가 될 수 없다.
포인트: 수일치 — 긴 주어의 중심 명사', 104),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Furniture designs cannot be sent to the factory floor without the ------- of the lead engineer.', NULL, 'NONE', '["approval", "approves", "approved", "approvable"]', 'A', 1, '[문법-품사(명사 자리)] 정답 (A). 관사 the와 전치사 of 사이는 명사 자리이므로 approval(승인)이 맞다. without the approval of는 ''~의 승인 없이''이다. 오답: (B) approves는 동사, (C) approved는 동사의 과거형·과거분사, (D) approvable은 형용사라서 the 뒤에서 명사구의 중심이 될 수 없다.
포인트: 명사 자리(the + 명사 + of) — approval', 105),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'To thank longtime members, Brackwell Athletic Club will ------- the annual fee for anyone who renews by March 31.', NULL, 'NONE', '["waive", "retain", "estimate", "impose"]', 'A', 1, '[어휘-동사(상황 어휘)] 정답 (A). 오래된 회원에게 감사하는 뜻으로 하는 일이므로 요금을 ''면제하다''라는 waive가 맞다. waive a fee로 함께 외운다. 오답: (B) retain은 ''계속 보유하다'', (C) estimate는 ''추산하다'', (D) impose는 ''부과하다''라는 뜻이라 감사의 표시가 되지 못한다.
포인트: 동사 어휘 waive(요금을 면제하다)', 106),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Once an expense claim is processed, a detailed statement ------- to the employee by the accounting office.', NULL, 'NONE', '["sends", "will send", "is sent", "sending"]', 'C', 1, '[문법-태(수동태)] 정답 (C). 주어 a detailed statement는 보내는 주체가 아니라 보내지는 대상이고, 뒤에 목적어가 없으며 by the accounting office가 행위자를 나타낸다. 따라서 수동태 is sent가 맞다. 오답: (A) sends와 (B) will send는 능동형이라 목적어가 필요하다. (D) sending은 혼자서 문장의 동사가 될 수 없다.
포인트: 능동태와 수동태(by + 행위자)', 107),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Requests to change a catering order must be submitted ------- writing at least 72 hours in advance.', NULL, 'NONE', '["in", "as", "to", "of"]', 'A', 1, '[전치사(고정 표현)] 정답 (A). ''서면으로''라는 방식은 in writing으로 표현한다. 오답: (B) as writing, (C) to writing, (D) of writing은 ''서면으로''라는 뜻의 표현이 아니며 submitted 뒤에서 방식을 나타내지 못한다.
포인트: 고정 표현 in writing', 108),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Please request an ------- from the printer so that we know roughly what 500 brochures will cost.', NULL, 'NONE', '["apology", "extension", "estimate", "itinerary"]', 'C', 1, '[어휘-명사(상황 어휘)] 정답 (C). 인쇄 비용이 대략 얼마인지 미리 알기 위해 업체에 요청하는 것은 견적(estimate)이다. 오답: (A) apology는 ''사과'', (B) extension은 ''기한 연장'', (D) itinerary는 ''여행 일정표''라서 비용을 미리 알려 주는 것이 아니다.
포인트: 명사 어휘 estimate(견적)', 109),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'The brochure ------- to new patients explains how Medvane Pharmacy stores and delivers prescription medications.', NULL, 'NONE', '["distributing", "distributed", "distributes", "distribute"]', 'B', 1, '[문법-분사(능동·수동)] 정답 (B). 문장의 동사는 explains이므로 빈칸은 The brochure를 뒤에서 꾸미는 분사 자리이다. 안내 책자는 배포되는 대상이므로 과거분사 distributed가 맞다. 오답: (A) distributing은 책자가 무언가를 배포한다는 능동 관계가 되고 목적어도 없다. (C) distributes와 (D) distribute는 동사를 하나 더 만든다.
포인트: 명사를 뒤에서 꾸미는 분사(능동·수동)', 110),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'A shuttle bus departs for the convention center every 15 ------- from the hotel''s main entrance.', NULL, 'NONE', '["minute", "minutely", "minutes", "minute''s"]', 'C', 1, '[문법-수일치(명사의 단수·복수: every + 수사)] 정답 (C). every 뒤에는 보통 단수명사가 오지만, ''~마다''라는 간격을 나타낼 때는 every + 수사 + 복수명사로 쓴다. 15 뒤이므로 복수 minutes가 맞다. 오답: (A) minute는 단수라 15와 수가 맞지 않는다. (B) minutely는 부사, (D) minute''s는 소유격이라 수사 뒤 명사 자리에 올 수 없다.
포인트: every + 수사 + 복수명사(명사의 단수·복수)', 111),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Employees with at least one year of service are ------- for the company''s tuition assistance program.', NULL, 'NONE', '["capable", "valid", "eligible", "familiar"]', 'C', 1, '[어휘-형용사(형용사+전치사 결합)] 정답 (C). 1년 이상 근무라는 조건을 채운 직원이 지원 제도의 ''대상 자격이 된다''는 뜻이므로 eligible for가 맞다. 오답: (A) capable은 of와 쓰고 능력을 뜻한다. (B) valid는 쿠폰·서류 등이 유효하다는 뜻이라 사람을 주어로 쓰지 않는다. (D) familiar는 with와 쓰고 ''잘 아는''이라는 뜻이다.
포인트: eligible for + 명사', 112),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'The new scheduling software has made the transition between day and night shifts remarkably ------- for warehouse supervisors.', NULL, 'NONE', '["smoothly", "smooth", "smooths", "smoothest"]', 'B', 1, '[문법-품사(목적격 보어 형용사)] 정답 (B). make + 목적어(the transition ...) + 목적격 보어 구문이다. 목적어가 어떤 상태가 되는지 설명하는 형용사 smooth가 필요하고, 부사 remarkably가 그 형용사를 꾸민다. 오답: (A) smoothly는 부사라 보어가 될 수 없다. (C) smooths는 동사이다. (D) smoothest는 최상급인데 비교 범위가 없어 remarkably 뒤 보어로 맞지 않는다.
포인트: make + 목적어 + 형용사(목적격 보어)', 113),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Under its travel policy, the firm will ------- staff for parking fees if receipts are submitted within 30 days.', NULL, 'NONE', '["spend", "deposit", "afford", "reimburse"]', 'D', 1, '[어휘-동사(동사 문형)] 정답 (D). ''사람 + for + 비용''이 이어지는 문형이고 영수증을 내면 쓴 돈을 돌려준다는 뜻이므로 reimburse(reimburse 사람 for 비용)가 맞다. 오답: (A) spend는 돈·시간을 목적어로 하고 on과 쓴다. (B) deposit은 돈을 맡기거나 입금한다는 뜻, (C) afford는 ''~할 여유가 있다''는 뜻이라 사람을 목적어로 하는 이 문형에 맞지 않는다.
포인트: reimburse 사람 for 비용', 114),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', '------- interested in joining the weekend cycling class should sign up at the reception desk by Friday.', NULL, 'NONE', '["Those", "They", "Whoever", "Them"]', 'A', 1, '[문법-대명사(those + 분사)] 정답 (A). 문장의 동사는 should sign up이고, 빈칸은 뒤의 분사구 interested in ...의 수식을 받는 주어 자리이다. ''~하는 사람들''은 those + 분사로 쓴다. 오답: (B) They는 뒤에서 분사구의 수식을 받지 못한다. (C) Whoever는 절을 이끌어야 하는데 뒤에 동사(is)가 없다. (D) Them은 목적격이라 주어가 될 수 없다.
포인트: those + 분사(~하는 사람들)', 115),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Residents remain ------- divided over the proposal to build a parking garage beside the park.', NULL, 'NONE', '["wisely", "sharply", "promptly", "fluently"]', 'B', 1, '[어휘-부사(연어)] 정답 (B). 의견이 ''첨예하게 갈리다''는 be sharply divided로 쓴다. 오답: (A) wisely는 행동을 현명하게 했다는 뜻, (C) promptly는 ''지체 없이'', (D) fluently는 말을 ''유창하게''라는 뜻이라 의견이 갈린 정도를 나타내지 못한다.
포인트: 연어 sharply divided', 116),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'The finance team ------- all of the quarterly records by the time the auditors arrived on Monday.', NULL, 'NONE', '["organizes", "will organize", "had organized", "has organized"]', 'C', 1, '[문법-시제(과거완료)] 정답 (C). by the time 절의 동사가 과거 arrived이므로, 그 과거 시점보다 먼저 끝난 일은 과거완료 had organized로 쓴다. 오답: (A) organizes는 현재, (B) will organize는 미래라 과거의 기준 시점과 맞지 않는다. (D) has organized는 현재를 기준으로 하는 현재완료라 arrived와 함께 쓰지 않는다.
포인트: by the time + 과거 → 주절 과거완료', 117),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', '------- the lack of seating in the lobby, the reception will be held in the rooftop garden instead.', NULL, 'NONE', '["Given", "Because", "Moreover", "Unless"]', 'A', 1, '[전치사(given과 접속사·접속부사 구별)] 정답 (A). 빈칸 뒤는 명사구(the lack of seating in the lobby)이고 그것이 장소를 바꾼 근거이므로 ''~을 고려할 때''라는 전치사 Given이 맞다. 오답: (B) Because와 (D) Unless는 접속사라 뒤에 주어와 동사가 있는 절이 와야 한다. (C) Moreover는 접속부사라 명사구를 이끌지 못한다.
포인트: 전치사 given + 명사구(접속사·접속부사와 구별)', 118),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'The resort hires extra staff each summer to handle the ------- increase in demand for guided tours.', NULL, 'NONE', '["season", "seasonal", "seasonally", "seasonality"]', 'B', 1, '[문법-품사(명사를 꾸미는 형용사)] 정답 (B). 관사 the와 명사 increase(증가) 사이에서 명사를 꾸미는 형용사 자리이므로 seasonal(계절에 따른)이 맞다. 여기서 increase는 뒤에 in demand가 이어지는 명사이다. 오답: (A) season과 (D) seasonality는 명사라 increase를 꾸미는 말로 쓰지 않는다. (C) seasonally는 부사라 명사를 꾸미지 못한다.
포인트: 명사 앞 형용사 자리(seasonal increase)', 119),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'The restaurant''s owners plan to have the main kitchen ------- before the wedding season begins in May.', NULL, 'NONE', '["renovated", "renovating", "renovate", "renovates"]', 'A', 1, '[문법-준동사(have + 목적어 + 과거분사)] 정답 (A). have + 사물 + 과거분사는 ''다른 사람에게 맡겨 ~되게 하다''라는 구문이다. 주방은 개조되는 대상이므로 renovated가 맞다. 오답: (B) renovating과 (C) renovate는 주방이 스스로 개조한다는 능동 관계가 되고 목적어도 없다. (D) renovates는 have 뒤 목적격 보어 자리에 올 수 없는 정동사 형태이다.
포인트: have + 사물 + 과거분사', 120);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Please note that the list of speakers posted online is ------- and may change before the conference opens.', NULL, 'NONE', '["punctual", "mutual", "durable", "tentative"]', 'D', 1, '[어휘-형용사(상황 어휘)] 정답 (D). 뒤에서 행사 전에 바뀔 수 있다고 했으므로 연사 명단은 ''잠정적인'' 상태이다. tentative가 맞다. 오답: (A) punctual은 사람·교통편이 시간을 잘 지킨다는 뜻, (B) mutual은 ''상호의'', (C) durable은 물건이 ''내구성이 좋은''이라는 뜻이라 아직 확정되지 않은 명단을 설명하지 못한다.
포인트: 형용사 어휘 tentative(잠정적인)', 121),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'This insurance plan provides coverage ------- the loss or theft of personal belongings during a trip.', NULL, 'NONE', '["through", "among", "onto", "against"]', 'D', 1, '[전치사(명사+전치사 결합)] 정답 (D). 보험이 분실·도난에 ''대비한'' 보장을 제공한다는 뜻으로 coverage against + 위험을 쓴다. 오답: (A) through는 수단·경로, (B) among은 집단의 내부, (C) onto는 표면 위로의 이동을 나타내어 보장하려는 위험을 연결하지 못한다.
포인트: coverage against + 위험', 122),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'The conference includes a two-hour lunch break, ------- which attendees may visit the exhibit hall.', NULL, 'NONE', '["into", "during", "between", "toward"]', 'B', 1, '[문법-관계사(전치사 + which)] 정답 (B). which의 선행사는 a two-hour lunch break이고, ''그 시간 동안에'' 전시장을 둘러볼 수 있다는 뜻이므로 during which가 맞다. 오답: (A) into와 (D) toward는 방향을 나타내고, (C) between은 둘 사이를 나타내어 하나의 기간 안에서 일어나는 일을 연결하지 못한다.
포인트: 전치사 + 관계대명사(during which)', 123),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Reporters must send finished articles to the copy desk ------- 6:00 P.M. for printing overnight.', NULL, 'NONE', '["until", "by", "between", "onto"]', 'B', 1, '[전치사(by와 until)] 정답 (B). 기사를 보내는 일은 한 번에 끝나는 동작이고 6시는 그 일을 마쳐야 하는 기한이므로 by가 맞다. 오답: (A) until은 어떤 상태가 그때까지 계속된다는 뜻이라 send와 맞지 않는다. (C) between은 두 시점이 함께 나와야 한다. (D) onto는 표면 위로의 이동을 나타내어 시각 앞에 쓰지 않는다.
포인트: by(완료 기한)와 until(계속)의 구별', 124),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'The hotel''s banquet hall can ------- up to 250 guests, making it suitable for large corporate receptions.', NULL, 'NONE', '["accommodate", "attend", "reserve", "expand"]', 'A', 1, '[어휘-동사(상황 어휘)] 정답 (A). 주어가 연회장이고 목적어가 인원수이므로 ''수용하다''라는 accommodate가 맞다. 오답: (B) attend는 사람이 행사에 참석한다는 뜻이고, (C) reserve는 사람이 자리·방을 예약한다는 뜻이라 연회장이 주어가 될 수 없다. (D) expand는 ''확장하다''라는 뜻으로 손님을 목적어로 하지 않는다.
포인트: 동사 어휘 accommodate(인원을 수용하다)', 125),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', '------- the shipment arrived a day earlier, the pharmacy would not have run out of the allergy medication.', NULL, 'NONE', '["Should", "Were", "Did", "Had"]', 'D', 1, '[문법-도치(가정법 과거완료의 if 생략)] 정답 (D). 주절이 would not have run out이므로 과거 사실의 반대를 가정하는 가정법 과거완료이다. If the shipment had arrived에서 if를 생략하면 Had the shipment arrived로 도치된다. 오답: (A) Should와 (B) Were는 이 문장에서 뒤의 과거분사 arrived와 이어지지 않고, 주절 would not have run out과도 호응하지 않는다. (C) Did 뒤에는 동사원형이 와야 하므로 arrived와 이어지지 않는다.
포인트: 가정법 과거완료의 도치(Had + 주어 + p.p.)', 126),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Halberg Motors will ------- open 12 fast-charging stations along the coastal highway by the end of next year.', NULL, 'NONE', '["report", "reportedly", "reportable", "reporter"]', 'B', 1, '[문법-품사(조동사와 동사 사이의 부사)] 정답 (B). 조동사 will과 본동사 open 사이는 부사 자리이다. reportedly는 ''전해진 바에 따르면''이라는 부사이다. 오답: (A) report를 넣으면 will 뒤에 동사원형이 둘이 된다. (C) reportable은 형용사, (D) reporter는 명사라 조동사와 본동사 사이에서 동사를 꾸미지 못한다.
포인트: 조동사와 본동사 사이의 부사(reportedly)', 127),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Tests showed the new coating''s effect on shelf life to be -------, so the production process was left unchanged.', NULL, 'NONE', '["deliberate", "generous", "attentive", "negligible"]', 'D', 1, '[어휘-형용사] 정답 (D). 효과가 ''무시할 만큼 작아서'' 공정을 바꾸지 않았다는 흐름이므로 negligible이 맞다. 오답: (A) deliberate는 ''의도적인'', (B) generous는 사람·금액이 ''후한'', (C) attentive는 사람이 ''주의 깊은''이라는 뜻이라 공정을 그대로 둔 이유가 되지 못한다.
포인트: 형용사 어휘 negligible(무시해도 될 만큼 작은)', 128),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Since the merger, the newspaper has offered subscribers a ------- variety of local sports coverage than ever before.', NULL, 'NONE', '["greatly", "great", "greater", "greatness"]', 'C', 1, '[문법-비교(비교급 형용사 + 명사)] 정답 (C). 뒤의 than ever before가 비교의 단서이므로 명사 variety를 꾸미는 비교급 형용사 greater가 맞다(a greater variety of). 오답: (A) greatly는 부사라 명사를 꾸미지 못한다. (B) great는 원급이라 than과 호응하지 않는다. (D) greatness는 명사라 variety를 꾸미지 못한다.
포인트: 비교급 + than(a greater variety of)', 129),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P5', 'Customers enrolled in the loyalty program receive ------- rates at all of the company''s charging stations.', NULL, 'NONE', '["preferable", "apparent", "cautious", "preferential"]', 'D', 1, '[어휘-형용사(형태가 비슷한 형용사)] 정답 (D). 회원에게만 주는 ''우대 요금''은 preferential rates이다. preferential은 특정 대상에게 우선권·혜택을 준다는 뜻이다. 오답: (A) preferable은 둘을 비교했을 때 ''더 바람직한''이라는 뜻으로 보통 보어 자리에 쓰며 우대의 뜻이 없다. (B) apparent는 ''명백한'', (C) cautious는 ''조심스러운''이라 요금을 꾸미지 못한다.
포인트: preferential과 preferable의 구별', 130),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (131).', 'Questions 131-134 refer to the following letter.

Corrindale Small Business Association
18 Milldam Lane, Corrindale

April 9

Dr. Samira Nassar
Pinecrest Tax Advisers

Dear Dr. Nassar,

On behalf of our members, I would like to express our (131) ------- for the talk you gave at last week''s spring meeting. More than 90 business owners attended. (132) -------. In fact, the turnout was (133) ------- higher than for any other event we have held this year.

Several members told me afterward that you (134) ------- the new tax-filing rules to them more clearly than anyone had before. Many have asked whether you could return in the fall to lead a half-day workshop. I will call your office next week to discuss possible dates.

Sincerely,

Henrik Reinholt
Events Coordinator', 'NONE', '["concern", "regret", "gratitude", "curiosity"]', 'C', 1, '[Part6-어휘(문맥)] 정답 (C). 뒤에서 참석자가 예상보다 많았고 회원들이 강연을 칭찬하며 다시 와 달라고 한다고 했으므로, 이 편지는 강연에 ''감사''를 전하는 글이다. gratitude가 맞다. 오답: (A) concern(우려)과 (B) regret(유감)은 강연이 좋았다는 뒤 내용과 반대이다. (D) curiosity(호기심)는 이미 끝난 강연에 대해 전할 말이 아니다.
포인트: 명사 어휘 gratitude(감사) — 뒤 문장이 단서', 131),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (132).', 'Questions 131-134 refer to the following letter.

Corrindale Small Business Association
18 Milldam Lane, Corrindale

April 9

Dr. Samira Nassar
Pinecrest Tax Advisers

Dear Dr. Nassar,

On behalf of our members, I would like to express our (131) ------- for the talk you gave at last week''s spring meeting. More than 90 business owners attended. (132) -------. In fact, the turnout was (133) ------- higher than for any other event we have held this year.

Several members told me afterward that you (134) ------- the new tax-filing rules to them more clearly than anyone had before. Many have asked whether you could return in the fall to lead a half-day workshop. I will call your office next week to discuss possible dates.

Sincerely,

Henrik Reinholt
Events Coordinator', 'NONE', '["We hope that all of them will be able to attend.", "Both of them said they would gladly come again.", "As a result, the meeting had to be canceled.", "We had set out chairs for only 60 people."]', 'D', 1, '[Part6-문장삽입] 정답 (D). 앞 문장은 90명 넘게 왔다는 내용이고 뒤 문장은 In fact로 참석자가 어느 행사보다 많았다고 덧붙이므로, 60명분 의자만 준비했다(예상을 넘었다)는 (D)가 둘을 잇는다. 오답: (A) 이미 참석했다고 했는데 앞으로 참석하기를 바란다는 것은 시점이 맞지 않는다. (B) Both of them은 둘을 가리키는데 앞에는 90명이 넘는 사람들뿐이다. (C) 회의는 열렸고 강연도 했으므로 취소되었다는 말은 모순이다.
포인트: 문장 삽입 — 시점·지시어·앞뒤 논리', 132),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (133).', 'Questions 131-134 refer to the following letter.

Corrindale Small Business Association
18 Milldam Lane, Corrindale

April 9

Dr. Samira Nassar
Pinecrest Tax Advisers

Dear Dr. Nassar,

On behalf of our members, I would like to express our (131) ------- for the talk you gave at last week''s spring meeting. More than 90 business owners attended. (132) -------. In fact, the turnout was (133) ------- higher than for any other event we have held this year.

Several members told me afterward that you (134) ------- the new tax-filing rules to them more clearly than anyone had before. Many have asked whether you could return in the fall to lead a half-day workshop. I will call your office next week to discuss possible dates.

Sincerely,

Henrik Reinholt
Events Coordinator', 'NONE', '["very", "so", "far", "such"]', 'C', 1, '[Part6-문법(비교급 강조 부사)] 정답 (C). 빈칸은 비교급 higher를 강조하는 자리이다. 비교급은 far, much 등으로 강조하므로 far가 맞다(far higher than). 오답: (A) very와 (B) so는 원급 형용사·부사를 꾸미고 비교급 앞에는 쓰지 않는다. (D) such는 명사구 앞에 쓴다.
포인트: 비교급 강조 부사 far(very 불가)', 133),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (134).', 'Questions 131-134 refer to the following letter.

Corrindale Small Business Association
18 Milldam Lane, Corrindale

April 9

Dr. Samira Nassar
Pinecrest Tax Advisers

Dear Dr. Nassar,

On behalf of our members, I would like to express our (131) ------- for the talk you gave at last week''s spring meeting. More than 90 business owners attended. (132) -------. In fact, the turnout was (133) ------- higher than for any other event we have held this year.

Several members told me afterward that you (134) ------- the new tax-filing rules to them more clearly than anyone had before. Many have asked whether you could return in the fall to lead a half-day workshop. I will call your office next week to discuss possible dates.

Sincerely,

Henrik Reinholt
Events Coordinator', 'NONE', '["advised", "assured", "instructed", "explained"]', 'D', 1, '[Part6-문법(동사 문형)] 정답 (D). 빈칸 뒤에 내용(the new tax-filing rules)이 목적어로 오고 사람이 to them으로 이어지므로 explain 내용 to 사람 문형의 explained가 맞다. 오답: (A) advised, (B) assured, (C) instructed는 모두 사람을 바로 목적어로 하는 동사라(advise 사람 of 내용 등) 이 문장의 ''내용 + to 사람'' 자리에 맞지 않는다.
포인트: explain 내용 to 사람', 134),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (135).', 'Questions 135-138 refer to the following advertisement.

Rindahl Home Services
Fast, Dependable Repairs

Has your refrigerator stopped cooling? Is your washing machine making a strange noise? Our certified technicians repair all major brands of household (135) -------.

Repairing a modern machine (136) ------- takes both training and the right parts. That is why each of our vans carries more than 400 common components. (137) -------.

Most repair companies charge a fee simply for coming to your home. (138) -------, at Rindahl the visit costs nothing if you go ahead with the repair. In addition, all of our work is guaranteed for one full year.

Call 555-0142 or book a visit at www.rindahlhome.com.', 'NONE', '["applicants", "appliances", "applications", "appointments"]', 'B', 1, '[Part6-어휘(문맥)] 정답 (B). 앞의 두 문장이 냉장고(refrigerator)와 세탁기(washing machine)를 말하므로 기사들이 고치는 것은 ''가전제품''인 appliances이다. 오답: (A) applicants는 ''지원자'', (C) applications는 ''신청(서)'', (D) appointments는 ''예약·약속''이라 고장 나서 수리하는 대상이 아니다.
포인트: 형태가 비슷한 명사의 뜻 구별(appliance / applicant / application)', 135),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (136).', 'Questions 135-138 refer to the following advertisement.

Rindahl Home Services
Fast, Dependable Repairs

Has your refrigerator stopped cooling? Is your washing machine making a strange noise? Our certified technicians repair all major brands of household (135) -------.

Repairing a modern machine (136) ------- takes both training and the right parts. That is why each of our vans carries more than 400 common components. (137) -------.

Most repair companies charge a fee simply for coming to your home. (138) -------, at Rindahl the visit costs nothing if you go ahead with the repair. In addition, all of our work is guaranteed for one full year.

Call 555-0142 or book a visit at www.rindahlhome.com.', 'NONE', '["correct", "correctly", "correction", "corrects"]', 'B', 1, '[Part6-문법(품사: 동명사구를 꾸미는 부사)] 정답 (B). Repairing은 a modern machine을 목적어로 하는 동명사이고 문장의 동사는 takes이다. 목적어 뒤에서 ''수리하는'' 동작을 꾸미는 말은 부사 correctly이다. 오답: (A) correct는 형용사·동사라 동작을 꾸미지 못하고, 넣으면 동사 takes와 겹친다. (C) correction은 명사, (D) corrects는 동사라 이 자리에 올 수 없다.
포인트: 목적어를 취하는 -ing + 부사', 136),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (137).', 'Questions 135-138 refer to the following advertisement.

Rindahl Home Services
Fast, Dependable Repairs

Has your refrigerator stopped cooling? Is your washing machine making a strange noise? Our certified technicians repair all major brands of household (135) -------.

Repairing a modern machine (136) ------- takes both training and the right parts. That is why each of our vans carries more than 400 common components. (137) -------.

Most repair companies charge a fee simply for coming to your home. (138) -------, at Rindahl the visit costs nothing if you go ahead with the repair. In addition, all of our work is guaranteed for one full year.

Call 555-0142 or book a visit at www.rindahlhome.com.', 'NONE', '["As a result, most repairs are completed in a single visit.", "They were hired after your machine had been repaired.", "Otherwise, the parts would have arrived much sooner.", "This is the reason we no longer visit customers'' homes."]', 'A', 1, '[Part6-문장삽입] 정답 (A). 앞 문장에서 차량마다 부품을 400가지 넘게 싣고 다닌다고 했으므로, 그 결과 대부분 한 번의 방문으로 수리가 끝난다는 (A)가 이어진다. 오답: (B) They가 가리킬 사람이 바로 앞에 없고, 수리가 끝난 뒤에 고용했다는 것은 순서가 맞지 않는다. (C) Otherwise(그렇지 않았다면)로 이을 앞 내용이 없다. (D) 뒤 단락에서 고객의 집을 방문한다고 하므로 모순이다.
포인트: 문장 삽입 — 원인과 결과, 뒤 단락과의 모순 확인', 137),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (138).', 'Questions 135-138 refer to the following advertisement.

Rindahl Home Services
Fast, Dependable Repairs

Has your refrigerator stopped cooling? Is your washing machine making a strange noise? Our certified technicians repair all major brands of household (135) -------.

Repairing a modern machine (136) ------- takes both training and the right parts. That is why each of our vans carries more than 400 common components. (137) -------.

Most repair companies charge a fee simply for coming to your home. (138) -------, at Rindahl the visit costs nothing if you go ahead with the repair. In addition, all of our work is guaranteed for one full year.

Call 555-0142 or book a visit at www.rindahlhome.com.', 'NONE', '["Similarly", "However", "Otherwise", "For this reason"]', 'B', 1, '[Part6-연결어] 정답 (B). 앞 문장은 대부분의 업체가 방문만 해도 요금을 받는다는 내용이고, 뒤 문장은 이 업체는 수리를 맡기면 방문비가 없다는 내용이므로 대조의 However가 맞다. 오답: (A) Similarly는 비슷한 내용을 덧붙일 때, (C) Otherwise는 ''그렇지 않으면'', (D) For this reason은 결과를 말할 때 쓰는데, 뒤 문장은 앞 문장과 반대되는 내용이다.
포인트: 연결어 however(대조)', 138),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (139).', 'Questions 139-142 refer to the following instructions.

Trestlewood Shelving Unit
Assembly Instructions

Your Trestlewood unit (139) ------- of two side panels, five shelves, and a bag of 20 screws. Before you begin, please check that nothing is missing. If a part is missing or damaged, call our helpline at 555-0176, and a replacement will be sent to you in a timely (140) -------.

To assemble the unit, lay the side panels on the floor with the drilled holes facing up. Attach the top and bottom shelves first, but do not tighten the screws completely. (141) -------. Leaving the screws slightly loose will enable you (142) ------- the frame before the final step. Once all five shelves are in place, tighten every screw firmly.', 'NONE', '["consisting", "is consisted", "consists", "consist"]', 'C', 1, '[Part6-문법(동사 형태: 자동사 consist of)] 정답 (C). 문장에 동사가 없으므로 빈칸은 동사 자리이고, 주어 Your Trestlewood unit이 단수이므로 consists가 맞다. consist of(~로 이루어지다)는 수동태로 쓰지 않는다. 오답: (A) consisting은 혼자서 문장의 동사가 될 수 없다. (B) is consisted는 수동태라 쓸 수 없다. (D) consist는 단수 주어와 수가 맞지 않는다.
포인트: consist of — 능동으로만 쓰는 동사, 수일치', 139),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (140).', 'Questions 139-142 refer to the following instructions.

Trestlewood Shelving Unit
Assembly Instructions

Your Trestlewood unit (139) ------- of two side panels, five shelves, and a bag of 20 screws. Before you begin, please check that nothing is missing. If a part is missing or damaged, call our helpline at 555-0176, and a replacement will be sent to you in a timely (140) -------.

To assemble the unit, lay the side panels on the floor with the drilled holes facing up. Attach the top and bottom shelves first, but do not tighten the screws completely. (141) -------. Leaving the screws slightly loose will enable you (142) ------- the frame before the final step. Once all five shelves are in place, tighten every screw firmly.', 'NONE', '["basis", "period", "method", "manner"]', 'D', 1, '[Part6-어휘(고정 표현)] 정답 (D). ''늦지 않게, 제때에''는 in a timely manner로 쓴다. 부품이 없으면 전화하라는 앞 내용과 이어져 교체 부품을 제때 보내 준다는 뜻이 된다. 오답: (A) basis는 on a regular basis처럼 on과 쓴다. (B) period와 (C) method는 in a timely 뒤에서 이 뜻의 표현을 만들지 못한다.
포인트: 고정 표현 in a timely manner', 140);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (141).', 'Questions 139-142 refer to the following instructions.

Trestlewood Shelving Unit
Assembly Instructions

Your Trestlewood unit (139) ------- of two side panels, five shelves, and a bag of 20 screws. Before you begin, please check that nothing is missing. If a part is missing or damaged, call our helpline at 555-0176, and a replacement will be sent to you in a timely (140) -------.

To assemble the unit, lay the side panels on the floor with the drilled holes facing up. Attach the top and bottom shelves first, but do not tighten the screws completely. (141) -------. Leaving the screws slightly loose will enable you (142) ------- the frame before the final step. Once all five shelves are in place, tighten every screw firmly.', 'NONE', '["These were tightened firmly in the previous step.", "Two or three turns of each one are enough at this stage.", "Otherwise, the missing part cannot be replaced.", "After that, return the damaged panels to the store."]', 'B', 1, '[Part6-문장삽입] 정답 (B). 앞 문장이 나사를 끝까지 조이지 말라고 했으므로, each one(나사 하나하나)을 두세 바퀴만 돌리면 된다는 (B)가 그 정도를 설명하고, 뒤의 ''나사를 조금 느슨하게 두면''으로 이어진다. 오답: (A) 방금 끝까지 조이지 말라고 했는데 앞 단계에서 단단히 조였다는 것은 모순이다. (C) 빠진 부품 이야기는 첫 단락에서 끝났고 Otherwise로 이을 내용이 아니다. (D) 파손된 부품은 전화하면 새로 보내 준다고 했으므로 매장에 반품하라는 말은 맞지 않는다.
포인트: 문장 삽입 — 지시어(each one)와 단계의 순서', 141),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (142).', 'Questions 139-142 refer to the following instructions.

Trestlewood Shelving Unit
Assembly Instructions

Your Trestlewood unit (139) ------- of two side panels, five shelves, and a bag of 20 screws. Before you begin, please check that nothing is missing. If a part is missing or damaged, call our helpline at 555-0176, and a replacement will be sent to you in a timely (140) -------.

To assemble the unit, lay the side panels on the floor with the drilled holes facing up. Attach the top and bottom shelves first, but do not tighten the screws completely. (141) -------. Leaving the screws slightly loose will enable you (142) ------- the frame before the final step. Once all five shelves are in place, tighten every screw firmly.', 'NONE', '["to straighten", "straighten", "straightening", "straightened"]', 'A', 1, '[Part6-문법(준동사: enable + 목적어 + to부정사)] 정답 (A). enable은 목적어 뒤에 to부정사를 써서 ''~가 …할 수 있게 하다''라는 뜻을 만든다(enable you to straighten). 오답: (B) straighten은 동사원형, (C) straightening은 -ing, (D) straightened는 과거분사로, 이 문장의 enable you 뒤에는 올 수 없는 형태이다.
포인트: enable + 목적어 + to부정사', 142),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (143).', 'Questions 143-146 refer to the following press release.

FOR IMMEDIATE RELEASE

OAKHAVEN (June 3) — Quillon Mobility announced today that it will (143) ------- the release of its Veloq S2 electric scooter until September. The scooter had been scheduled to go on sale on June 20. According to the company, a supplier was unable to deliver a key battery component on time. Neither the scooter''s design nor its price (144) ------- by the delay.

Customers who have already placed orders may keep them or request a full refund. Those who decide to wait (145) ------- a $50 store credit when their scooters are shipped. (146) -------.

"We apologize to everyone who has been waiting for the S2," said company president Farida Haidari.', 'NONE', '["defer", "cancel", "advance", "celebrate"]', 'A', 1, '[Part6-어휘(문맥)] 정답 (A). 6월 20일로 잡혀 있던 출시가 9월로 늦어지고 뒤에서도 the delay라고 하므로 ''미루다''라는 defer가 맞다(defer A until B). 오답: (B) cancel은 아예 취소한다는 뜻인데 9월에 출시하고 기다리는 고객에게 배송한다고 했다. (C) advance는 ''앞당기다''라 반대이다. (D) celebrate(축하하다)는 until September, the delay와 이어지지 않는다.
포인트: 동사 어휘 defer(미루다 — 취소와 다름)', 143),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (144).', 'Questions 143-146 refer to the following press release.

FOR IMMEDIATE RELEASE

OAKHAVEN (June 3) — Quillon Mobility announced today that it will (143) ------- the release of its Veloq S2 electric scooter until September. The scooter had been scheduled to go on sale on June 20. According to the company, a supplier was unable to deliver a key battery component on time. Neither the scooter''s design nor its price (144) ------- by the delay.

Customers who have already placed orders may keep them or request a full refund. Those who decide to wait (145) ------- a $50 store credit when their scooters are shipped. (146) -------.

"We apologize to everyone who has been waiting for the S2," said company president Farida Haidari.', 'NONE', '["are affected", "have been affected", "is affected", "affecting"]', 'C', 1, '[Part6-문법(수일치: neither A nor B)] 정답 (C). neither A nor B가 주어이면 동사는 가까운 B에 수를 맞춘다. B가 단수 its price이므로 단수동사 is affected가 맞다. 오답: (A) are affected와 (B) have been affected는 복수동사라 its price와 수가 맞지 않는다. (D) affecting은 혼자서 문장의 동사가 될 수 없다.
포인트: neither A nor B의 수일치(가까운 B에 맞춘다)', 144),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (145).', 'Questions 143-146 refer to the following press release.

FOR IMMEDIATE RELEASE

OAKHAVEN (June 3) — Quillon Mobility announced today that it will (143) ------- the release of its Veloq S2 electric scooter until September. The scooter had been scheduled to go on sale on June 20. According to the company, a supplier was unable to deliver a key battery component on time. Neither the scooter''s design nor its price (144) ------- by the delay.

Customers who have already placed orders may keep them or request a full refund. Those who decide to wait (145) ------- a $50 store credit when their scooters are shipped. (146) -------.

"We apologize to everyone who has been waiting for the S2," said company president Farida Haidari.', 'NONE', '["received", "will receive", "have received", "receiving"]', 'B', 1, '[Part6-문법(시제·문맥)] 정답 (B). 스쿠터는 9월에 출시되므로 배송(when their scooters are shipped)은 앞으로의 일이다. 그때 적립금을 받는다는 뜻이므로 미래 will receive가 맞다. 오답: (A) received와 (C) have received는 이미 받았다는 뜻이라 아직 배송 전이라는 내용과 맞지 않는다. (D) receiving은 혼자서 문장의 동사가 될 수 없다.
포인트: 시제 — 시간 부사절(when + 현재)과 주절의 미래', 145),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (146).', 'Questions 143-146 refer to the following press release.

FOR IMMEDIATE RELEASE

OAKHAVEN (June 3) — Quillon Mobility announced today that it will (143) ------- the release of its Veloq S2 electric scooter until September. The scooter had been scheduled to go on sale on June 20. According to the company, a supplier was unable to deliver a key battery component on time. Neither the scooter''s design nor its price (144) ------- by the delay.

Customers who have already placed orders may keep them or request a full refund. Those who decide to wait (145) ------- a $50 store credit when their scooters are shipped. (146) -------.

"We apologize to everyone who has been waiting for the S2," said company president Farida Haidari.', 'NONE', '["The refund was issued before the order had been placed.", "Both of these components are now sold in stores.", "For this reason, the scooter went on sale in June as planned.", "This credit can be applied to any Quillon product."]', 'D', 1, '[Part6-문장삽입] 정답 (D). This credit이 앞 문장의 a $50 store credit을 받아 그 쓰임을 설명하므로 자연스럽게 이어진다. 오답: (A) 주문하기도 전에 환불했다는 것은 순서가 맞지 않는다. (B) 글에 나온 부품은 배터리 부품 하나뿐이어서 Both of these components가 가리킬 대상이 없다. (C) 출시가 9월로 미뤄졌다는 첫 단락과 반대이다.
포인트: 문장 삽입 — 지시어(this + 명사)가 받는 앞 내용', 146),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'According to the web page, what must a guide whose license expired a year ago do?', 'Questions 147-148 refer to the following Web page.

Wexmoor Visitor Bureau
Licensed City Guides: Renewals and New Applications

A city guide license is valid for two years. Guides whose licenses are still current may renew them online during the 60 days before the expiration date. The renewal fee is $35, and no examination is required. A license that expired within the past six months may also be renewed, but a $20 late fee applies.

A license that expired more than six months ago cannot be renewed. Its former holder must submit a new application, pay the $90 application fee, and pass the local history examination again, just as a first-time applicant would.

Please note that the fall examination dates listed on this page are tentative; the final schedule will be posted on August 1. Applicants who cannot take the examination on their assigned date may defer it once, to the next session, at no additional cost.', 'NONE', '["Start the application process over", "Renew the license online for $35", "Pay a $20 late fee to renew", "Wait another six months before reapplying"]', 'A', 1, '[세부 사항] 정답 (A). "A license that expired more than six months ago cannot be renewed. Its former holder must submit a new application"이므로 만료된 지 1년이 된 면허는 갱신이 아니라 처음부터 다시 신청해야 한다. submit a new application → start the application process over. 오답: (B) $35 온라인 갱신은 면허가 아직 유효한 안내원에게 해당한다. (C) $20 연체료를 내고 갱신하는 것은 만료 후 6개월 이내일 때다. (D) 6개월은 갱신 가능 여부를 가르는 기준일 뿐 다시 신청하기 전에 기다리라는 말은 없다.
포인트: 세부 사항 — 웹 페이지 둘째 문단(갱신과 신규 신청 구별)', 147),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the fall examination dates?', 'Questions 147-148 refer to the following Web page.

Wexmoor Visitor Bureau
Licensed City Guides: Renewals and New Applications

A city guide license is valid for two years. Guides whose licenses are still current may renew them online during the 60 days before the expiration date. The renewal fee is $35, and no examination is required. A license that expired within the past six months may also be renewed, but a $20 late fee applies.

A license that expired more than six months ago cannot be renewed. Its former holder must submit a new application, pay the $90 application fee, and pass the local history examination again, just as a first-time applicant would.

Please note that the fall examination dates listed on this page are tentative; the final schedule will be posted on August 1. Applicants who cannot take the examination on their assigned date may defer it once, to the next session, at no additional cost.', 'NONE', '["They were deferred from the summer.", "They apply only to first-time applicants.", "They can be changed for a $90 fee.", "They have not been finalized yet."]', 'D', 1, '[추론] 정답 (D). "the fall examination dates listed on this page are tentative; the final schedule will be posted on August 1"에서 가을 시험 날짜가 아직 확정되지 않았음을 알 수 있다. tentative → not finalized. 오답: (A) defer는 응시자가 자기 시험을 다음 회차로 미루는 것이지 일정이 여름에서 밀렸다는 말이 아니다. (B) 면허가 오래전에 만료된 사람도 시험을 본다. (C) $90은 신청 수수료이고 시험을 미루는 데는 추가 비용이 없다.
포인트: 추론 — 웹 페이지 셋째 문단(tentative = 미확정)', 148),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'Why did Ms. Deshmukh contact Mr. Bellini?', 'Questions 149-150 refer to the following text-message chain.

Anjali Deshmukh (10:12 A.M.)
Hi, Marco. This is Anjali from Ellery & Voss. I need to change our lunch order for the 14th. Six more people have signed up for the training, so we now need 30 boxed lunches instead of 24.

Marco Bellini (10:15 A.M.)
No problem. Same mix as before, half chicken and half vegetable?

Anjali Deshmukh (10:17 A.M.)
Actually, could you make 12 of them vegetable and the rest chicken? Also, the session has moved to our Fernhollow office at 80 Wharf Road. Is that outside your delivery area?

Marco Bellini (10:20 A.M.)
We''re there every week. There''s no extra charge, but I''ll need someone to meet our driver at the loading dock at 11:30 A.M.

Anjali Deshmukh (10:22 A.M.)
I''ll be there myself. Can you send a revised invoice?

Marco Bellini (10:23 A.M.)
You''ll have it within the hour.', 'NONE', '["To cancel a training session", "To modify an existing order", "To ask about a late delivery", "To request a refund for a meal"]', 'B', 1, '[주제·목적] 정답 (B). "I need to change our lunch order for the 14th"와 24개 → 30개 수량 변경이 근거다. change our lunch order → modify an existing order. 오답: (A) 교육은 인원이 늘고 장소만 바뀌었을 뿐 취소되지 않았다. (C) 배송 지역을 물었을 뿐 늦은 배송 이야기는 없다. (D) 환불 요청은 없고 수정된 청구서를 요청했다.
포인트: 주제·목적 — 문자 첫 메시지', 149),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'At 10:20 A.M., what does Mr. Bellini most likely mean when he writes, "We''re there every week"?', 'Questions 149-150 refer to the following text-message chain.

Anjali Deshmukh (10:12 A.M.)
Hi, Marco. This is Anjali from Ellery & Voss. I need to change our lunch order for the 14th. Six more people have signed up for the training, so we now need 30 boxed lunches instead of 24.

Marco Bellini (10:15 A.M.)
No problem. Same mix as before, half chicken and half vegetable?

Anjali Deshmukh (10:17 A.M.)
Actually, could you make 12 of them vegetable and the rest chicken? Also, the session has moved to our Fernhollow office at 80 Wharf Road. Is that outside your delivery area?

Marco Bellini (10:20 A.M.)
We''re there every week. There''s no extra charge, but I''ll need someone to meet our driver at the loading dock at 11:30 A.M.

Anjali Deshmukh (10:22 A.M.)
I''ll be there myself. Can you send a revised invoice?

Marco Bellini (10:23 A.M.)
You''ll have it within the hour.', 'NONE', '["He attends meetings at that office often.", "The order will take a week to prepare.", "A weekly discount will be applied.", "His company delivers to that area regularly."]', 'D', 1, '[의도 파악] 정답 (D). 바로 앞의 질문 "Is that outside your delivery area?"에 대한 답이고, 이어서 "There''s no extra charge"라고 하므로 그 지역에 정기적으로 배송을 간다는 뜻이다. 오답: (A) 회의 참석 이야기는 없다. (B) every week는 준비 기간이 아니다. (C) 추가 요금이 없다는 것이지 할인을 말한 것이 아니다.
포인트: 의도 파악 — 바로 앞 질문과 뒤따르는 말', 150),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about Ms. Strand''s trip?', 'Questions 151-152 refer to the following receipt.

Tamsin Valley Rail
Booking Change Receipt

Booking reference: TV-48217
Passenger: Helena Strand
Date issued: June 2

Original journey: June 9 — Stonebury Central to Lindenmere — Departs 8:05 A.M. — Standard class
New journey: June 9 — Stonebury Central to Lindenmere — Departs 6:40 A.M. — Standard class

Fare already paid: $42.00
Fare for new journey: $36.00
Fare difference: -$6.00
Change fee: $5.00
Amount refunded to card ending 3310: $1.00

Notes
Seat 14A, Car 3. Your original seat reservation has been released.
A ticket that has been changed once cannot be changed again, but it may be canceled for travel credit no later than two hours before departure.
Show this receipt and photo identification to the conductor on board. No paper ticket will be issued.', 'NONE', '["It was moved to a different date.", "It will begin earlier than originally planned.", "It was upgraded to a higher class.", "It will end at a different station."]', 'B', 1, '[사실 확인] 정답 (B). Original journey는 "Departs 8:05 A.M.", New journey는 "Departs 6:40 A.M."이므로 출발이 앞당겨졌다. 오답: (A) 두 여정 모두 June 9이다. (C) 둘 다 Standard class다. (D) 도착역은 둘 다 Lindenmere다.
포인트: 사실 확인 — 영수증의 변경 전·후 여정 비교', 151),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'According to the receipt, what is Ms. Strand unable to do?', 'Questions 151-152 refer to the following receipt.

Tamsin Valley Rail
Booking Change Receipt

Booking reference: TV-48217
Passenger: Helena Strand
Date issued: June 2

Original journey: June 9 — Stonebury Central to Lindenmere — Departs 8:05 A.M. — Standard class
New journey: June 9 — Stonebury Central to Lindenmere — Departs 6:40 A.M. — Standard class

Fare already paid: $42.00
Fare for new journey: $36.00
Fare difference: -$6.00
Change fee: $5.00
Amount refunded to card ending 3310: $1.00

Notes
Seat 14A, Car 3. Your original seat reservation has been released.
A ticket that has been changed once cannot be changed again, but it may be canceled for travel credit no later than two hours before departure.
Show this receipt and photo identification to the conductor on board. No paper ticket will be issued.', 'NONE', '["Travel without a printed ticket", "Cancel her trip in exchange for credit", "Change her departure time again", "Sit in a reserved seat"]', 'C', 1, '[세부 사항] 정답 (C). "A ticket that has been changed once cannot be changed again"가 근거다. 오답: (A) "No paper ticket will be issued"이므로 영수증과 신분증으로 탑승한다. (B) 출발 두 시간 전까지 travel credit으로 취소할 수 있다. (D) Seat 14A가 배정되어 있다.
포인트: 세부 사항 — 영수증 Notes(변경과 취소 구별)', 152),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is the notice mainly about?', 'Questions 153-154 refer to the following notice.

Meridian Heights Bank
Main Street Branch

To our customers:

Beginning September 1, our Main Street branch will operate from 215 Cobalt Avenue, two blocks north of the current location. The Main Street office will close permanently at 3:00 P.M. on August 28. Account numbers, debit cards, and automatic payments will not be affected.

The Cobalt Avenue branch offers extended hours until 7:00 P.M. on weekdays, a drive-up teller window, and four private rooms for loan and mortgage consultations. Safe-deposit box holders will receive a separate letter explaining how their boxes will be transferred; no action is needed until then.

To mark the opening, the monthly maintenance fee on any savings account opened at the Cobalt Avenue branch in September will be waived for one year.', 'NONE', '["A change in account numbers", "An increase in service fees", "A merger of two banks", "The relocation of a branch"]', 'D', 1, '[주제·목적] 정답 (D). "our Main Street branch will operate from 215 Cobalt Avenue"와 기존 사무소의 영구 폐쇄 안내가 중심 내용이므로 지점 이전 공지다. 오답: (A) 계좌번호는 "will not be affected"라고 했다. (B) 수수료는 인상이 아니라 면제(waived) 행사다. (C) 다른 은행과의 합병 이야기는 없다.
포인트: 주제·목적 — 공지 첫 문단', 153),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is NOT mentioned as a feature of the Cobalt Avenue branch?', 'Questions 153-154 refer to the following notice.

Meridian Heights Bank
Main Street Branch

To our customers:

Beginning September 1, our Main Street branch will operate from 215 Cobalt Avenue, two blocks north of the current location. The Main Street office will close permanently at 3:00 P.M. on August 28. Account numbers, debit cards, and automatic payments will not be affected.

The Cobalt Avenue branch offers extended hours until 7:00 P.M. on weekdays, a drive-up teller window, and four private rooms for loan and mortgage consultations. Safe-deposit box holders will receive a separate letter explaining how their boxes will be transferred; no action is needed until then.

To mark the opening, the monthly maintenance fee on any savings account opened at the Cobalt Avenue branch in September will be waived for one year.', 'NONE', '["Free parking for customers", "Longer hours on weekdays", "A drive-up teller window", "Private rooms for consultations"]', 'A', 1, '[사실 확인(NOT)] 정답 (A). 주차에 관한 언급은 지문에 없다. 오답: (B) "extended hours until 7:00 P.M. on weekdays". (C) "a drive-up teller window". (D) "four private rooms for loan and mortgage consultations".
포인트: 사실 확인(NOT) — 공지 둘째 문단', 154),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'Why was the letter sent?', 'Questions 155-157 refer to the following letter.

Fairholm Mutual Insurance
40 Granary Street, Ostwick

October 6

Mr. Anselm Ruiz
17 Pike Lane, Ostwick

Policy number: HM-209-5531

Dear Mr. Ruiz:

Your homeowner''s policy is due for renewal on November 15. Enclosed is a statement showing your coverage for the coming year. Your annual premium will be $1,140, an increase of $60 over the current term. This adjustment reflects higher rebuilding costs in the Ostwick area and is not related to your claims history.

You may lower your premium in two ways. Customers who install a monitored smoke-alarm system are eligible for a 5 percent discount once they send us proof of installation. In addition, policyholders who pay the full annual premium in a single payment, rather than in monthly installments, avoid the $3 monthly processing charge.

No action is required to renew; your coverage will continue automatically. However, if you wish to change your coverage limits, please contact your agent, Lorna Petrakis, at 555-0151 by November 1.

Sincerely,

Gideon Abara
Customer Accounts Manager

Enclosure', 'NONE', '["To request an overdue payment", "To notify a customer of renewal terms", "To confirm that a claim has been received", "To acknowledge an application for a new policy"]', 'B', 1, '[주제·목적] 정답 (B). "Your homeowner''s policy is due for renewal on November 15"에 이어 다음 해 보험료와 할인 방법을 알리므로 갱신 조건 통지다. 오답: (A) 연체 독촉이 아니다. (C) 보험금 청구 접수 이야기가 아니다(claims history는 인상 사유가 아니라는 설명). (D) 새 보험 신청이 아니라 기존 보험의 갱신이다.
포인트: 주제·목적 — 편지 첫 문단(갱신과 신규 신청 구별)', 155),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about Mr. Ruiz''s premium?', 'Questions 155-157 refer to the following letter.

Fairholm Mutual Insurance
40 Granary Street, Ostwick

October 6

Mr. Anselm Ruiz
17 Pike Lane, Ostwick

Policy number: HM-209-5531

Dear Mr. Ruiz:

Your homeowner''s policy is due for renewal on November 15. Enclosed is a statement showing your coverage for the coming year. Your annual premium will be $1,140, an increase of $60 over the current term. This adjustment reflects higher rebuilding costs in the Ostwick area and is not related to your claims history.

You may lower your premium in two ways. Customers who install a monitored smoke-alarm system are eligible for a 5 percent discount once they send us proof of installation. In addition, policyholders who pay the full annual premium in a single payment, rather than in monthly installments, avoid the $3 monthly processing charge.

No action is required to renew; your coverage will continue automatically. However, if you wish to change your coverage limits, please contact your agent, Lorna Petrakis, at 555-0151 by November 1.

Sincerely,

Gideon Abara
Customer Accounts Manager

Enclosure', 'NONE', '["It will be higher than it is now.", "It rose because of a recent claim.", "It must be paid in a single payment.", "It already includes a 5 percent discount."]', 'A', 1, '[사실 확인] 정답 (A). "an increase of $60 over the current term"이 근거다. 오답: (B) "is not related to your claims history"라고 했다. (C) 일시불은 수수료를 피하는 선택 사항일 뿐 의무가 아니다. (D) 5% 할인은 경보 장치를 설치하고 증빙을 보낸 뒤에 받는 것이다.
포인트: 사실 확인 — 편지 첫째·둘째 문단', 156),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'According to the letter, what should Mr. Ruiz do if he wants to adjust his coverage?', 'Questions 155-157 refer to the following letter.

Fairholm Mutual Insurance
40 Granary Street, Ostwick

October 6

Mr. Anselm Ruiz
17 Pike Lane, Ostwick

Policy number: HM-209-5531

Dear Mr. Ruiz:

Your homeowner''s policy is due for renewal on November 15. Enclosed is a statement showing your coverage for the coming year. Your annual premium will be $1,140, an increase of $60 over the current term. This adjustment reflects higher rebuilding costs in the Ostwick area and is not related to your claims history.

You may lower your premium in two ways. Customers who install a monitored smoke-alarm system are eligible for a 5 percent discount once they send us proof of installation. In addition, policyholders who pay the full annual premium in a single payment, rather than in monthly installments, avoid the $3 monthly processing charge.

No action is required to renew; your coverage will continue automatically. However, if you wish to change your coverage limits, please contact your agent, Lorna Petrakis, at 555-0151 by November 1.

Sincerely,

Gideon Abara
Customer Accounts Manager

Enclosure', 'NONE', '["Return the enclosed statement", "Visit the Granary Street office", "Send proof of installation", "Call a representative by a certain date"]', 'D', 1, '[세부 사항] 정답 (D). "if you wish to change your coverage limits, please contact your agent, Lorna Petrakis, at 555-0151 by November 1"가 근거다. contact your agent → call a representative, by November 1 → by a certain date. 오답: (A) 동봉 명세서를 돌려보내라는 말은 없다. (B) 방문 요청이 없다. (C) 설치 증빙은 할인 조건이다.
포인트: 세부 사항 — 편지 셋째 문단(요청과 기한 by)', 157),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is the problem with the product?', 'Questions 158-160 refer to the following notice.

PRODUCT SAFETY RECALL
Varnum Home Appliances

Varnum Home Appliances is voluntarily recalling the Varnum BrewPoint 12-cup coffee maker, model BP-300, sold between January and June of this year. — [1] — In a small number of units, the heating plate may fail to switch off automatically, which can cause the glass carafe to overheat and crack. No injuries have been reported.

To find out whether your unit is affected, check the serial number printed on the base. Only units whose serial numbers begin with the letters KT are included in the recall. — [2] —

If you own an affected unit, stop using it immediately. You may either return it to the store where it was purchased for a full refund or request a free replacement heating plate, which our technicians will install at any authorized Varnum service center. — [3] — A receipt is not required for either option.

For a list of participating stores and authorized service centers, visit www.varnumhome.com/recall or call 1-800-555-0119. — [4] —', 'NONE', '["Its glass container is too small.", "Its serial number was printed incorrectly.", "Its power cord may overheat.", "A part may not shut off as designed."]', 'D', 1, '[세부 사항] 정답 (D). "the heating plate may fail to switch off automatically"가 근거다. fail to switch off automatically → not shut off as designed. 오답: (A) 유리 용기의 크기 문제가 아니라 과열로 금이 갈 수 있다는 것이다. (B) 일련번호는 대상 확인 수단이다. (C) 과열되는 것은 전원 코드가 아니라 carafe다.
포인트: 세부 사항 — 공지 첫 문단', 158),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about the recall?', 'Questions 158-160 refer to the following notice.

PRODUCT SAFETY RECALL
Varnum Home Appliances

Varnum Home Appliances is voluntarily recalling the Varnum BrewPoint 12-cup coffee maker, model BP-300, sold between January and June of this year. — [1] — In a small number of units, the heating plate may fail to switch off automatically, which can cause the glass carafe to overheat and crack. No injuries have been reported.

To find out whether your unit is affected, check the serial number printed on the base. Only units whose serial numbers begin with the letters KT are included in the recall. — [2] —

If you own an affected unit, stop using it immediately. You may either return it to the store where it was purchased for a full refund or request a free replacement heating plate, which our technicians will install at any authorized Varnum service center. — [3] — A receipt is not required for either option.

For a list of participating stores and authorized service centers, visit www.varnumhome.com/recall or call 1-800-555-0119. — [4] —', 'NONE', '["It was ordered by a government agency.", "It applies only to some BP-300 units.", "It follows several reports of injuries.", "It covers units sold over two years."]', 'B', 1, '[사실 확인] 정답 (B). "Only units whose serial numbers begin with the letters KT are included in the recall"이므로 BP-300 중 일부만 대상이다. 오답: (A) voluntarily recalling이므로 자발적 리콜이다. (C) "No injuries have been reported". (D) 올해 1~6월 판매분이다.
포인트: 사실 확인 — 공지 둘째 문단', 159),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'In which of the positions marked [1], [2], [3], and [4] does the following sentence best belong? "This repair takes about 20 minutes and is done while you wait."', 'Questions 158-160 refer to the following notice.

PRODUCT SAFETY RECALL
Varnum Home Appliances

Varnum Home Appliances is voluntarily recalling the Varnum BrewPoint 12-cup coffee maker, model BP-300, sold between January and June of this year. — [1] — In a small number of units, the heating plate may fail to switch off automatically, which can cause the glass carafe to overheat and crack. No injuries have been reported.

To find out whether your unit is affected, check the serial number printed on the base. Only units whose serial numbers begin with the letters KT are included in the recall. — [2] —

If you own an affected unit, stop using it immediately. You may either return it to the store where it was purchased for a full refund or request a free replacement heating plate, which our technicians will install at any authorized Varnum service center. — [3] — A receipt is not required for either option.

For a list of participating stores and authorized service centers, visit www.varnumhome.com/recall or call 1-800-555-0119. — [4] —', 'NONE', '["[1]", "[2]", "[3]", "[4]"]', 'C', 1, '[문장 삽입] 정답 (C). This repair는 바로 앞 문장의 "a free replacement heating plate, which our technicians will install"을 가리키므로 [3]이 알맞다. 오답: (A) [1] 앞은 리콜 대상 제품 소개로 수리 언급이 없다. (B) [2] 앞은 일련번호 확인 방법이다. (D) [4] 앞은 매장·서비스 센터 목록 안내라 This repair가 가리킬 말이 멀다.
포인트: 문장 삽입 — 지시어 This repair의 선행사', 160);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'For whom is the pottery course intended?', 'Questions 161-163 refer to the following information.

Rosewick Community Arts Center
Winter Term for Adults: January 12 - March 6

Registration opens on December 1 for members and on December 8 for the general public. All courses meet once a week for eight weeks.

Pottery on the Wheel — Tuesday evenings — $160
An entry-level course; no experience is needed. The fee covers clay and glazes. Limited to 10 students.

Watercolor Landscapes — Wednesday mornings — $135
For students who have completed Watercolor Basics or have equivalent experience. Students supply their own paints and brushes; a list is sent upon registration.

Digital Photography — Thursday evenings — $150
Bring your own camera. The final session is an outdoor shoot at Rosewick Lighthouse.

Members receive 15 percent off all course fees. A full refund is available for withdrawals made before the second class meeting; after that, fees cannot be refunded. Courses with fewer than five registrants by January 5 will be canceled, and those who signed up will be notified by e-mail.

Register at the front desk or at www.rosewickarts.org/courses.', 'NONE', '["People who are new to the craft", "Students who finished a basics class", "Members who registered on December 1", "Artists who own their own equipment"]', 'A', 1, '[세부 사항] 정답 (A). "An entry-level course; no experience is needed"가 근거다. entry-level → new to the craft. 오답: (B) 기초반 수료 조건은 Watercolor Landscapes다. (C) 12월 1일은 회원 등록 시작일일 뿐 수강 대상 조건이 아니다. (D) 재료비가 포함되어 있고 장비 소지 조건은 없다.
포인트: 세부 사항 — 강좌 안내의 Pottery 항목(entry-level = 초보자 대상)', 161),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about Watercolor Landscapes?', 'Questions 161-163 refer to the following information.

Rosewick Community Arts Center
Winter Term for Adults: January 12 - March 6

Registration opens on December 1 for members and on December 8 for the general public. All courses meet once a week for eight weeks.

Pottery on the Wheel — Tuesday evenings — $160
An entry-level course; no experience is needed. The fee covers clay and glazes. Limited to 10 students.

Watercolor Landscapes — Wednesday mornings — $135
For students who have completed Watercolor Basics or have equivalent experience. Students supply their own paints and brushes; a list is sent upon registration.

Digital Photography — Thursday evenings — $150
Bring your own camera. The final session is an outdoor shoot at Rosewick Lighthouse.

Members receive 15 percent off all course fees. A full refund is available for withdrawals made before the second class meeting; after that, fees cannot be refunded. Courses with fewer than five registrants by January 5 will be canceled, and those who signed up will be notified by e-mail.

Register at the front desk or at www.rosewickarts.org/courses.', 'NONE', '["It has the highest fee of the three courses.", "It is not intended for first-time painters.", "It includes all necessary supplies.", "It ends with an outdoor session."]', 'B', 1, '[추론] 정답 (B). "For students who have completed Watercolor Basics or have equivalent experience"이므로 처음 그리는 사람을 위한 강좌가 아니다. 오답: (A) $135로 셋 중 가장 싸다. (C) "Students supply their own paints and brushes". (D) 야외 수업은 Digital Photography의 마지막 시간이다.
포인트: 추론 — 강좌 안내의 Watercolor 항목', 162),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the Digital Photography course?', 'Questions 161-163 refer to the following information.

Rosewick Community Arts Center
Winter Term for Adults: January 12 - March 6

Registration opens on December 1 for members and on December 8 for the general public. All courses meet once a week for eight weeks.

Pottery on the Wheel — Tuesday evenings — $160
An entry-level course; no experience is needed. The fee covers clay and glazes. Limited to 10 students.

Watercolor Landscapes — Wednesday mornings — $135
For students who have completed Watercolor Basics or have equivalent experience. Students supply their own paints and brushes; a list is sent upon registration.

Digital Photography — Thursday evenings — $150
Bring your own camera. The final session is an outdoor shoot at Rosewick Lighthouse.

Members receive 15 percent off all course fees. A full refund is available for withdrawals made before the second class meeting; after that, fees cannot be refunded. Courses with fewer than five registrants by January 5 will be canceled, and those who signed up will be notified by e-mail.

Register at the front desk or at www.rosewickarts.org/courses.', 'NONE', '["It is limited to 10 students.", "It provides cameras for students to borrow.", "One of its meetings is held away from the center.", "It will be canceled on December 8."]', 'C', 1, '[추론] 정답 (C). "The final session is an outdoor shoot at Rosewick Lighthouse"에서 마지막 수업이 센터 밖 등대에서 열림을 알 수 있다. 오답: (A) 10명 제한은 Pottery 강좌다. (B) "Bring your own camera". (D) 12월 8일은 일반 등록 시작일이고, 폐강 기준일은 1월 5일이다.
포인트: 추론 — 강좌 안내의 Photography 항목', 163),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about the Tri-County Freight Forum?', 'Questions 164-167 refer to the following online chat discussion.

Yolanda Carvalho (1:02 P.M.)
Quick update on the Tri-County Freight Forum. Early registration closed last night with 212 sign-ups, about 40 more than at this point last year. Where do we stand on the program?

Tariq Mansour (1:04 P.M.)
All speakers are confirmed except Dr. Amara Diallo. She has a prior engagement in Marlby on the morning of the 18th and has asked whether her talk could be deferred until the afternoon.

Signe Holm (1:06 P.M.)
The 2:30 P.M. slot in Hall B is open now that the customs panel has been dropped. But Hall B can accommodate only 80 people, and her session was the most requested on the sign-up form.

Yolanda Carvalho (1:07 P.M.)
Then Hall B won''t do. Li-Wei, could the 2:30 P.M. workshop in the Main Auditorium be reassigned to Hall B so that she can have the auditorium?

Li-Wei Zhao (1:10 P.M.)
I just spoke to the workshop leader. He''s fine with Hall B as long as he gets a projector and movable tables. He expects only about 30 people.

Signe Holm (1:11 P.M.)
Hall B has both.

Yolanda Carvalho (1:12 P.M.)
That settles it. Tariq, please let Dr. Diallo know she''s in the Main Auditorium at 2:30 P.M. Signe, can you update the online program today? The printed version goes to the print shop on the 9th, so we have a little time there.

Signe Holm (1:13 P.M.)
I''ll have the website updated by 5:00 P.M.

Tariq Mansour (1:14 P.M.)
Writing to her now.', 'NONE', '["It will be held in Marlby.", "Its registration period has been extended.", "It has more early registrants than last year.", "Its printed program has already been distributed."]', 'C', 1, '[사실 확인] 정답 (C). "Early registration closed last night with 212 sign-ups, about 40 more than at this point last year"가 근거다. 오답: (A) Marlby는 Diallo 박사의 다른 선약 장소일 뿐 포럼 개최지라는 말이 없다. (B) 조기 등록은 마감되었고 연장 언급이 없다. (D) 인쇄본은 9일에 인쇄소로 넘어갈 예정이다.
포인트: 사실 확인 — 채팅 첫 메시지(행사장과 다른 일정 장소 구별)', 164),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'Why does Dr. Diallo want her talk to be rescheduled?', 'Questions 164-167 refer to the following online chat discussion.

Yolanda Carvalho (1:02 P.M.)
Quick update on the Tri-County Freight Forum. Early registration closed last night with 212 sign-ups, about 40 more than at this point last year. Where do we stand on the program?

Tariq Mansour (1:04 P.M.)
All speakers are confirmed except Dr. Amara Diallo. She has a prior engagement in Marlby on the morning of the 18th and has asked whether her talk could be deferred until the afternoon.

Signe Holm (1:06 P.M.)
The 2:30 P.M. slot in Hall B is open now that the customs panel has been dropped. But Hall B can accommodate only 80 people, and her session was the most requested on the sign-up form.

Yolanda Carvalho (1:07 P.M.)
Then Hall B won''t do. Li-Wei, could the 2:30 P.M. workshop in the Main Auditorium be reassigned to Hall B so that she can have the auditorium?

Li-Wei Zhao (1:10 P.M.)
I just spoke to the workshop leader. He''s fine with Hall B as long as he gets a projector and movable tables. He expects only about 30 people.

Signe Holm (1:11 P.M.)
Hall B has both.

Yolanda Carvalho (1:12 P.M.)
That settles it. Tariq, please let Dr. Diallo know she''s in the Main Auditorium at 2:30 P.M. Signe, can you update the online program today? The printed version goes to the print shop on the 9th, so we have a little time there.

Signe Holm (1:13 P.M.)
I''ll have the website updated by 5:00 P.M.

Tariq Mansour (1:14 P.M.)
Writing to her now.', 'NONE', '["Her flight will arrive late in the day.", "She has another commitment that morning.", "She would prefer a larger room.", "She needs additional equipment."]', 'B', 1, '[세부 사항] 정답 (B). "She has a prior engagement in Marlby on the morning of the 18th"가 근거다. prior engagement → another commitment, deferred → rescheduled. 오답: (A) 항공편 이야기는 없다. (C) 더 큰 방은 주최 측이 판단한 것이지 박사의 요청이 아니다. (D) 장비를 요구한 사람은 워크숍 진행자다.
포인트: 세부 사항 — 1:04 P.M. 메시지(engagement = 선약, defer = 미루다)', 165),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'At 1:07 P.M., what does Ms. Carvalho most likely mean when she writes, "Then Hall B won''t do"?', 'Questions 164-167 refer to the following online chat discussion.

Yolanda Carvalho (1:02 P.M.)
Quick update on the Tri-County Freight Forum. Early registration closed last night with 212 sign-ups, about 40 more than at this point last year. Where do we stand on the program?

Tariq Mansour (1:04 P.M.)
All speakers are confirmed except Dr. Amara Diallo. She has a prior engagement in Marlby on the morning of the 18th and has asked whether her talk could be deferred until the afternoon.

Signe Holm (1:06 P.M.)
The 2:30 P.M. slot in Hall B is open now that the customs panel has been dropped. But Hall B can accommodate only 80 people, and her session was the most requested on the sign-up form.

Yolanda Carvalho (1:07 P.M.)
Then Hall B won''t do. Li-Wei, could the 2:30 P.M. workshop in the Main Auditorium be reassigned to Hall B so that she can have the auditorium?

Li-Wei Zhao (1:10 P.M.)
I just spoke to the workshop leader. He''s fine with Hall B as long as he gets a projector and movable tables. He expects only about 30 people.

Signe Holm (1:11 P.M.)
Hall B has both.

Yolanda Carvalho (1:12 P.M.)
That settles it. Tariq, please let Dr. Diallo know she''s in the Main Auditorium at 2:30 P.M. Signe, can you update the online program today? The printed version goes to the print shop on the 9th, so we have a little time there.

Signe Holm (1:13 P.M.)
I''ll have the website updated by 5:00 P.M.

Tariq Mansour (1:14 P.M.)
Writing to her now.', 'NONE', '["The room has already been reserved.", "The room does not have enough seats.", "The room lacks the equipment a speaker needs.", "The room is too far from the auditorium."]', 'B', 1, '[의도 파악] 정답 (B). 바로 앞에서 Holm이 "Hall B can accommodate only 80 people, and her session was the most requested"라고 했으므로 청중에 비해 좌석이 부족해 적합하지 않다는 뜻이다. 오답: (A) 2:30 시간대는 비어 있다(open). (C) Hall B에는 프로젝터와 이동식 탁자가 있다. (D) 거리 이야기는 없다.
포인트: 의도 파악 — 바로 앞 메시지(accommodate = 수용하다)', 166),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What will Mr. Mansour most likely do next?', 'Questions 164-167 refer to the following online chat discussion.

Yolanda Carvalho (1:02 P.M.)
Quick update on the Tri-County Freight Forum. Early registration closed last night with 212 sign-ups, about 40 more than at this point last year. Where do we stand on the program?

Tariq Mansour (1:04 P.M.)
All speakers are confirmed except Dr. Amara Diallo. She has a prior engagement in Marlby on the morning of the 18th and has asked whether her talk could be deferred until the afternoon.

Signe Holm (1:06 P.M.)
The 2:30 P.M. slot in Hall B is open now that the customs panel has been dropped. But Hall B can accommodate only 80 people, and her session was the most requested on the sign-up form.

Yolanda Carvalho (1:07 P.M.)
Then Hall B won''t do. Li-Wei, could the 2:30 P.M. workshop in the Main Auditorium be reassigned to Hall B so that she can have the auditorium?

Li-Wei Zhao (1:10 P.M.)
I just spoke to the workshop leader. He''s fine with Hall B as long as he gets a projector and movable tables. He expects only about 30 people.

Signe Holm (1:11 P.M.)
Hall B has both.

Yolanda Carvalho (1:12 P.M.)
That settles it. Tariq, please let Dr. Diallo know she''s in the Main Auditorium at 2:30 P.M. Signe, can you update the online program today? The printed version goes to the print shop on the 9th, so we have a little time there.

Signe Holm (1:13 P.M.)
I''ll have the website updated by 5:00 P.M.

Tariq Mansour (1:14 P.M.)
Writing to her now.', 'NONE', '["Write to a speaker", "Update a website", "Contact a print shop", "Reserve a projector"]', 'A', 1, '[추론] 정답 (A). Carvalho가 "Tariq, please let Dr. Diallo know"라고 요청했고 Mansour가 "Writing to her now"라고 답했으므로 발표자에게 메시지를 보낼 것이다. 오답: (B) 웹사이트 수정은 Holm의 일이다. (C) 인쇄소에는 9일에 넘기며 Mansour의 일로 언급되지 않았다. (D) 프로젝터는 Hall B에 이미 있다.
포인트: 추론(다음 행동) — 채팅 마지막 메시지', 167),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is NOT offered to Ms. Ferrand as part of the renewal?', 'Questions 168-171 refer to the following e-mail.

To: Odile Ferrand
From: Barrow & Lynd Property Management <leasing@barrowlynd.com>
Date: April 4
Subject: Lease renewal for Unit 6C, Millrace Lofts

Dear Ms. Ferrand,

Your lease for Unit 6C will expire on June 30. — [1] — We are pleased to offer you two renewal options. Under a 12-month lease, your monthly rent would rise from $1,450 to $1,495. Under a 24-month lease, the rent would remain at the current rate for the entire term.

Any outstanding balance on your account must be paid before a new lease takes effect. Whichever option you choose, your current security deposit will carry over, and no additional deposit will be required. — [2] — As a renewing tenant, you are also entitled to one complimentary service: professional carpet cleaning or repainting of one room. Please indicate your choice on the renewal form.

To renew, sign and return the attached form by May 15. — [3] — If we do not hear from you by that date, we will assume that you intend to move out, and we will begin showing the unit to prospective tenants in June. In that case, we would give you at least 24 hours'' notice before each viewing.

Finally, please note that as of May 1, rent can no longer be paid by check at our office. — [4] — Payments must be made through the online tenant portal or by automatic bank transfer.

Sincerely,

Felix Arroyo
Leasing Coordinator', 'NONE', '["A choice between two lease lengths", "A lower monthly rent", "A service at no extra charge", "The transfer of her existing deposit"]', 'B', 1, '[사실 확인(NOT)] 정답 (B). 12개월 계약은 $1,495로 오르고 24개월 계약은 "remain at the current rate"이므로 임대료 인하는 제안되지 않았다. 오답: (A) "two renewal options"(12개월·24개월). (C) "one complimentary service: professional carpet cleaning or repainting of one room". (D) "your current security deposit will carry over".
포인트: 사실 확인(NOT) — 이메일 첫째·둘째 문단', 168),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'The word "outstanding" in paragraph 2, line 1, is closest in meaning to', 'Questions 168-171 refer to the following e-mail.

To: Odile Ferrand
From: Barrow & Lynd Property Management <leasing@barrowlynd.com>
Date: April 4
Subject: Lease renewal for Unit 6C, Millrace Lofts

Dear Ms. Ferrand,

Your lease for Unit 6C will expire on June 30. — [1] — We are pleased to offer you two renewal options. Under a 12-month lease, your monthly rent would rise from $1,450 to $1,495. Under a 24-month lease, the rent would remain at the current rate for the entire term.

Any outstanding balance on your account must be paid before a new lease takes effect. Whichever option you choose, your current security deposit will carry over, and no additional deposit will be required. — [2] — As a renewing tenant, you are also entitled to one complimentary service: professional carpet cleaning or repainting of one room. Please indicate your choice on the renewal form.

To renew, sign and return the attached form by May 15. — [3] — If we do not hear from you by that date, we will assume that you intend to move out, and we will begin showing the unit to prospective tenants in June. In that case, we would give you at least 24 hours'' notice before each viewing.

Finally, please note that as of May 1, rent can no longer be paid by check at our office. — [4] — Payments must be made through the online tenant portal or by automatic bank transfer.

Sincerely,

Felix Arroyo
Leasing Coordinator', 'NONE', '["exceptional", "noticeable", "unpaid", "lasting"]', 'C', 1, '[동의어] 정답 (C). "Any outstanding balance on your account must be paid"에서 outstanding은 아직 내지 않은 금액을 뜻하므로 unpaid가 가장 가깝다. 오답: (A) exceptional과 (B) noticeable은 사람·성과가 ''뛰어난, 눈에 띄는''이라는 뜻의 outstanding이라 balance와 맞지 않는다. (D) lasting은 ''오래가는''이라는 뜻으로 관계없다.
포인트: 동의어 — 이메일 둘째 문단(같은 형용사 보기는 꾸미는 명사 balance로 구별)', 169),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What will happen if Ms. Ferrand does not respond by May 15?', 'Questions 168-171 refer to the following e-mail.

To: Odile Ferrand
From: Barrow & Lynd Property Management <leasing@barrowlynd.com>
Date: April 4
Subject: Lease renewal for Unit 6C, Millrace Lofts

Dear Ms. Ferrand,

Your lease for Unit 6C will expire on June 30. — [1] — We are pleased to offer you two renewal options. Under a 12-month lease, your monthly rent would rise from $1,450 to $1,495. Under a 24-month lease, the rent would remain at the current rate for the entire term.

Any outstanding balance on your account must be paid before a new lease takes effect. Whichever option you choose, your current security deposit will carry over, and no additional deposit will be required. — [2] — As a renewing tenant, you are also entitled to one complimentary service: professional carpet cleaning or repainting of one room. Please indicate your choice on the renewal form.

To renew, sign and return the attached form by May 15. — [3] — If we do not hear from you by that date, we will assume that you intend to move out, and we will begin showing the unit to prospective tenants in June. In that case, we would give you at least 24 hours'' notice before each viewing.

Finally, please note that as of May 1, rent can no longer be paid by check at our office. — [4] — Payments must be made through the online tenant portal or by automatic bank transfer.

Sincerely,

Felix Arroyo
Leasing Coordinator', 'NONE', '["Her rent will increase to $1,495.", "Her lease will be renewed automatically.", "Her deposit will be kept by the company.", "Her unit will be shown to potential renters."]', 'D', 1, '[세부 사항] 정답 (D). "If we do not hear from you by that date, ... we will begin showing the unit to prospective tenants in June"이 근거다. prospective tenants → potential renters. 오답: (A) $1,495는 12개월 계약을 택했을 때의 임대료다. (B) 답이 없으면 이사하는 것으로 간주한다. (C) 보증금 몰수 이야기는 없다.
포인트: 세부 사항 — 이메일 셋째 문단(기한 by)', 170),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'In which of the positions marked [1], [2], [3], and [4] does the following sentence best belong? "With the end of your tenancy approaching, we are writing to ask whether you plan to stay."', 'Questions 168-171 refer to the following e-mail.

To: Odile Ferrand
From: Barrow & Lynd Property Management <leasing@barrowlynd.com>
Date: April 4
Subject: Lease renewal for Unit 6C, Millrace Lofts

Dear Ms. Ferrand,

Your lease for Unit 6C will expire on June 30. — [1] — We are pleased to offer you two renewal options. Under a 12-month lease, your monthly rent would rise from $1,450 to $1,495. Under a 24-month lease, the rent would remain at the current rate for the entire term.

Any outstanding balance on your account must be paid before a new lease takes effect. Whichever option you choose, your current security deposit will carry over, and no additional deposit will be required. — [2] — As a renewing tenant, you are also entitled to one complimentary service: professional carpet cleaning or repainting of one room. Please indicate your choice on the renewal form.

To renew, sign and return the attached form by May 15. — [3] — If we do not hear from you by that date, we will assume that you intend to move out, and we will begin showing the unit to prospective tenants in June. In that case, we would give you at least 24 hours'' notice before each viewing.

Finally, please note that as of May 1, rent can no longer be paid by check at our office. — [4] — Payments must be made through the online tenant portal or by automatic bank transfer.

Sincerely,

Felix Arroyo
Leasing Coordinator', 'NONE', '["[1]", "[2]", "[3]", "[4]"]', 'A', 1, '[문장 삽입] 정답 (A). 삽입 문장은 편지를 쓴 목적을 밝히는 도입 문장이고, the end of your tenancy가 바로 앞의 "will expire on June 30"을 받으며 뒤의 갱신 선택지 제안으로 자연스럽게 이어지므로 [1]이 알맞다. 오답: (B) [2]는 이미 선택지를 설명한 뒤 보증금 이야기를 하는 자리다. (C) [3]은 제출 방법과 기한 사이다. (D) [4]는 임대료 납부 방법 변경 안내 중간이다.
포인트: 문장 삽입 — 글의 도입 기능과 앞뒤 연결', 171),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is the article mainly about?', 'Questions 172-175 refer to the following article.

Penmarrow Courier: Business Spotlight

PENMARROW (May 20) — When Marit Eklund opened Lanternlight Bindery in a former bakery on Bramble Street 12 years ago, she had one secondhand press and a single client, a local poet. Today the company employs 26 people and repairs and rebinds more than 9,000 books a year for libraries, law firms, and private collectors across the region.

"We have never run an advertisement," says Ms. Eklund, who trained as a paper conservator at the Penmarrow Maritime Museum. "The business grew by word of mouth. Librarians talk to one another."

The firm''s steady growth has not been without setbacks. A burst pipe two winters ago destroyed much of its leather stock and forced a six-week closure. Rather than lay off staff, Ms. Eklund used the time to train employees in digital scanning, a service that now accounts for nearly a third of revenue.

Last month, Lanternlight secured a five-year contract to preserve the Penmarrow County Archive''s collection of 19th-century shipping records. To handle the work, the company will lease the vacant unit next door and plans to hire four additional technicians by fall.

Ms. Eklund has no plans to leave Bramble Street. "The ovens are long gone," she said, "but people still walk in asking for bread. We send them out with a bookmark instead."', 'NONE', '["The reopening of a historic bakery", "A new exhibit at a maritime museum", "Job openings at a county archive", "The growth of a local business"]', 'D', 1, '[주제·목적] 정답 (D). 12년 전 창업부터 현재 직원 26명, 최근 계약과 확장 계획까지 Lanternlight Bindery의 성장 과정을 다루므로 지역 업체의 성장이 주제다. 오답: (A) 제과점은 건물의 옛 용도일 뿐이다. (B) 박물관은 Eklund가 교육받은 곳이다. (C) 채용은 기록 보관소가 아니라 제본 회사가 한다.
포인트: 주제·목적 — 기사 전체 흐름', 172),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'The word "run" in paragraph 2, line 1, is closest in meaning to', 'Questions 172-175 refer to the following article.

Penmarrow Courier: Business Spotlight

PENMARROW (May 20) — When Marit Eklund opened Lanternlight Bindery in a former bakery on Bramble Street 12 years ago, she had one secondhand press and a single client, a local poet. Today the company employs 26 people and repairs and rebinds more than 9,000 books a year for libraries, law firms, and private collectors across the region.

"We have never run an advertisement," says Ms. Eklund, who trained as a paper conservator at the Penmarrow Maritime Museum. "The business grew by word of mouth. Librarians talk to one another."

The firm''s steady growth has not been without setbacks. A burst pipe two winters ago destroyed much of its leather stock and forced a six-week closure. Rather than lay off staff, Ms. Eklund used the time to train employees in digital scanning, a service that now accounts for nearly a third of revenue.

Last month, Lanternlight secured a five-year contract to preserve the Penmarrow County Archive''s collection of 19th-century shipping records. To handle the work, the company will lease the vacant unit next door and plans to hire four additional technicians by fall.

Ms. Eklund has no plans to leave Bramble Street. "The ovens are long gone," she said, "but people still walk in asking for bread. We send them out with a bookmark instead."', 'NONE', '["published", "managed", "conducted", "raced"]', 'A', 1, '[동의어] 정답 (A). "We have never run an advertisement"는 광고를 한 번도 ''낸'' 적이 없다는 뜻이므로 published(게재했다)가 가장 가깝다. 오답: (B) managed는 회사를 ''운영하다'', (C) conducted는 조사·시험을 ''실시하다'', (D) raced는 ''달리다''는 뜻의 run이라 목적어 an advertisement와 맞지 않는다.
포인트: 동의어 — 기사 둘째 문단(목적어 an advertisement로 뜻 결정)', 173),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What happened during the company''s six-week closure?', 'Questions 172-175 refer to the following article.

Penmarrow Courier: Business Spotlight

PENMARROW (May 20) — When Marit Eklund opened Lanternlight Bindery in a former bakery on Bramble Street 12 years ago, she had one secondhand press and a single client, a local poet. Today the company employs 26 people and repairs and rebinds more than 9,000 books a year for libraries, law firms, and private collectors across the region.

"We have never run an advertisement," says Ms. Eklund, who trained as a paper conservator at the Penmarrow Maritime Museum. "The business grew by word of mouth. Librarians talk to one another."

The firm''s steady growth has not been without setbacks. A burst pipe two winters ago destroyed much of its leather stock and forced a six-week closure. Rather than lay off staff, Ms. Eklund used the time to train employees in digital scanning, a service that now accounts for nearly a third of revenue.

Last month, Lanternlight secured a five-year contract to preserve the Penmarrow County Archive''s collection of 19th-century shipping records. To handle the work, the company will lease the vacant unit next door and plans to hire four additional technicians by fall.

Ms. Eklund has no plans to leave Bramble Street. "The ovens are long gone," she said, "but people still walk in asking for bread. We send them out with a bookmark instead."', 'NONE', '["Several workers were laid off.", "Employees learned a new skill.", "The building was sold.", "A leather supplier was replaced."]', 'B', 1, '[세부 사항] 정답 (B). "Ms. Eklund used the time to train employees in digital scanning"이 근거다. train employees in digital scanning → learned a new skill. 오답: (A) "Rather than lay off staff"이므로 해고하지 않았다. (C) 건물 매각 이야기는 없다. (D) 가죽 재고가 손상되었을 뿐 공급업체 교체는 언급되지 않았다.
포인트: 세부 사항 — 기사 셋째 문단', 174),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the contract with the Penmarrow County Archive?', 'Questions 172-175 refer to the following article.

Penmarrow Courier: Business Spotlight

PENMARROW (May 20) — When Marit Eklund opened Lanternlight Bindery in a former bakery on Bramble Street 12 years ago, she had one secondhand press and a single client, a local poet. Today the company employs 26 people and repairs and rebinds more than 9,000 books a year for libraries, law firms, and private collectors across the region.

"We have never run an advertisement," says Ms. Eklund, who trained as a paper conservator at the Penmarrow Maritime Museum. "The business grew by word of mouth. Librarians talk to one another."

The firm''s steady growth has not been without setbacks. A burst pipe two winters ago destroyed much of its leather stock and forced a six-week closure. Rather than lay off staff, Ms. Eklund used the time to train employees in digital scanning, a service that now accounts for nearly a third of revenue.

Last month, Lanternlight secured a five-year contract to preserve the Penmarrow County Archive''s collection of 19th-century shipping records. To handle the work, the company will lease the vacant unit next door and plans to hire four additional technicians by fall.

Ms. Eklund has no plans to leave Bramble Street. "The ovens are long gone," she said, "but people still walk in asking for bread. We send them out with a bookmark instead."', 'NONE', '["It will require the company to add employees.", "It was signed during the winter two years ago.", "It involves repairing a law firm''s books.", "It will be completed by this coming fall."]', 'A', 1, '[추론] 정답 (A). "To handle the work, the company ... plans to hire four additional technicians"에서 계약 업무를 처리하려면 인력을 늘려야 함을 알 수 있다. 오답: (B) 계약은 지난달에 따냈고, 두 해 전 겨울은 배관 파열 사고다. (C) 대상은 19세기 해운 기록이다. (D) 가을은 채용 목표 시점이고 계약 기간은 5년이다.
포인트: 추론 — 기사 넷째 문단(근거가 있는 만큼만)', 175),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'According to the web page, what is included in every registration fee?', 'Questions 176-180 refer to the following Web page and e-mail.

[Web page]
Halstrom Institute of Urban Planning
9th Annual Conference on Sustainable Transit
November 12-13, Lakemont Convention Center

Registration fees
Type | By October 10 | After October 10
Full conference (both days) | $280 | $340
Single day | $160 | $190
Student (both days, valid ID required) | $120 | $150

All fees include lunch and admission to the exhibit hall. The conference dinner on November 12 is an additional $55. Presenters must register for the full conference by October 10 in order to be listed in the printed program.

Schedule of paper sessions
November 12, 10:00 A.M. — Session 1: Bus Rapid Transit — Room 210
November 12, 2:00 P.M. — Session 2: Fare Policy and Equity — Room 210
November 13, 9:30 A.M. — Session 3: Rail Station Design — Garnet Room
November 13, 1:30 P.M. — Session 4: Cycling Networks — Room 305

Each presenter is allotted 20 minutes, followed by five minutes for questions. Cancellations received by October 31 will be refunded less a $40 handling fee.

────────────────────

[E-mail]
To: Dr. Olumide Bankole
From: Paloma Vega <registration@halstrominstitute.org>
Date: October 14
Subject: Your registration and presentation

Dear Dr. Bankole,

Thank you for registering for the full conference on October 8. Your payment has been received, and a receipt is attached. You also asked to add one ticket to the conference dinner; that charge will appear separately on your card.

Your paper, "Platform Crowding at Mid-Sized Rail Stations," has been accepted and placed in Session 3. Please note that this session has been moved to Room 118 because the room listed on our website is being renovated; the day and time are unchanged. You will be the second of four speakers.

Please upload your slides to the presenter portal by November 5. Our technicians will load them onto the room computer in advance, so personal laptops cannot be connected to the projector.

Finally, the session chair, Dr. Mei-Ling Hsu, would like to hold a brief meeting with all Session 3 speakers at 9:00 A.M. on the day of the session, at the door of the presentation room.

Best regards,

Paloma Vega
Conference Registrar', 'NONE', '["A ticket to the conference dinner", "A parking permit", "Access to the presenter portal", "Entry to an exhibition area"]', 'D', 1, '[세부 사항] 정답 (D). "All fees include lunch and admission to the exhibit hall"이 근거다. admission to the exhibit hall → entry to an exhibition area. 오답: (A) 만찬은 "an additional $55"다. (B) 주차 언급이 없다. (C) 발표자 포털은 이메일에 나오는 슬라이드 제출처로 등록비 포함 항목이 아니다.
포인트: 세부 사항 — 웹 페이지 요금표 아래 문단', 176),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about a presenter who registers on October 20?', 'Questions 176-180 refer to the following Web page and e-mail.

[Web page]
Halstrom Institute of Urban Planning
9th Annual Conference on Sustainable Transit
November 12-13, Lakemont Convention Center

Registration fees
Type | By October 10 | After October 10
Full conference (both days) | $280 | $340
Single day | $160 | $190
Student (both days, valid ID required) | $120 | $150

All fees include lunch and admission to the exhibit hall. The conference dinner on November 12 is an additional $55. Presenters must register for the full conference by October 10 in order to be listed in the printed program.

Schedule of paper sessions
November 12, 10:00 A.M. — Session 1: Bus Rapid Transit — Room 210
November 12, 2:00 P.M. — Session 2: Fare Policy and Equity — Room 210
November 13, 9:30 A.M. — Session 3: Rail Station Design — Garnet Room
November 13, 1:30 P.M. — Session 4: Cycling Networks — Room 305

Each presenter is allotted 20 minutes, followed by five minutes for questions. Cancellations received by October 31 will be refunded less a $40 handling fee.

────────────────────

[E-mail]
To: Dr. Olumide Bankole
From: Paloma Vega <registration@halstrominstitute.org>
Date: October 14
Subject: Your registration and presentation

Dear Dr. Bankole,

Thank you for registering for the full conference on October 8. Your payment has been received, and a receipt is attached. You also asked to add one ticket to the conference dinner; that charge will appear separately on your card.

Your paper, "Platform Crowding at Mid-Sized Rail Stations," has been accepted and placed in Session 3. Please note that this session has been moved to Room 118 because the room listed on our website is being renovated; the day and time are unchanged. You will be the second of four speakers.

Please upload your slides to the presenter portal by November 5. Our technicians will load them onto the room computer in advance, so personal laptops cannot be connected to the projector.

Finally, the session chair, Dr. Mei-Ling Hsu, would like to hold a brief meeting with all Session 3 speakers at 9:00 A.M. on the day of the session, at the door of the presentation room.

Best regards,

Paloma Vega
Conference Registrar', 'NONE', '["The presenter will pay $280 for both days.", "The presenter will not be in the printed program.", "The presenter will have only five minutes to speak.", "The presenter will not be charged for dinner."]', 'B', 1, '[추론] 정답 (B). "Presenters must register for the full conference by October 10 in order to be listed in the printed program"이므로 10월 20일에 등록하면 인쇄 프로그램에 실리지 않는다. 오답: (A) 10월 10일 이후 전체 등록은 $340이다. (C) 발표 20분, 질문 5분이다. (D) 만찬은 누구에게나 추가 $55다.
포인트: 추론 — 웹 페이지의 등록 기한(by October 10)', 177),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'How much was Dr. Bankole most likely charged for the conference registration?', 'Questions 176-180 refer to the following Web page and e-mail.

[Web page]
Halstrom Institute of Urban Planning
9th Annual Conference on Sustainable Transit
November 12-13, Lakemont Convention Center

Registration fees
Type | By October 10 | After October 10
Full conference (both days) | $280 | $340
Single day | $160 | $190
Student (both days, valid ID required) | $120 | $150

All fees include lunch and admission to the exhibit hall. The conference dinner on November 12 is an additional $55. Presenters must register for the full conference by October 10 in order to be listed in the printed program.

Schedule of paper sessions
November 12, 10:00 A.M. — Session 1: Bus Rapid Transit — Room 210
November 12, 2:00 P.M. — Session 2: Fare Policy and Equity — Room 210
November 13, 9:30 A.M. — Session 3: Rail Station Design — Garnet Room
November 13, 1:30 P.M. — Session 4: Cycling Networks — Room 305

Each presenter is allotted 20 minutes, followed by five minutes for questions. Cancellations received by October 31 will be refunded less a $40 handling fee.

────────────────────

[E-mail]
To: Dr. Olumide Bankole
From: Paloma Vega <registration@halstrominstitute.org>
Date: October 14
Subject: Your registration and presentation

Dear Dr. Bankole,

Thank you for registering for the full conference on October 8. Your payment has been received, and a receipt is attached. You also asked to add one ticket to the conference dinner; that charge will appear separately on your card.

Your paper, "Platform Crowding at Mid-Sized Rail Stations," has been accepted and placed in Session 3. Please note that this session has been moved to Room 118 because the room listed on our website is being renovated; the day and time are unchanged. You will be the second of four speakers.

Please upload your slides to the presenter portal by November 5. Our technicians will load them onto the room computer in advance, so personal laptops cannot be connected to the projector.

Finally, the session chair, Dr. Mei-Ling Hsu, would like to hold a brief meeting with all Session 3 speakers at 9:00 A.M. on the day of the session, at the door of the presentation room.

Best regards,

Paloma Vega
Conference Registrar', 'NONE', '["$160", "$190", "$280", "$340"]', 'C', 1, '[연계(문서1+2)] 정답 (C). 이메일의 "registering for the full conference on October 8"과 웹 페이지 요금표의 Full conference·By October 10 칸($280)을 연결한다. 만찬 요금은 "appear separately"라 등록비에 넣지 않는다. 오답: (A) 하루 조기 요금. (B) 하루 일반 요금. (D) 10월 10일 이후의 전체 등록 요금.
포인트: 연계 문항 — 웹 페이지 요금표 + 이메일 등록일', 178),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What aspect of Session 3 has been changed?', 'Questions 176-180 refer to the following Web page and e-mail.

[Web page]
Halstrom Institute of Urban Planning
9th Annual Conference on Sustainable Transit
November 12-13, Lakemont Convention Center

Registration fees
Type | By October 10 | After October 10
Full conference (both days) | $280 | $340
Single day | $160 | $190
Student (both days, valid ID required) | $120 | $150

All fees include lunch and admission to the exhibit hall. The conference dinner on November 12 is an additional $55. Presenters must register for the full conference by October 10 in order to be listed in the printed program.

Schedule of paper sessions
November 12, 10:00 A.M. — Session 1: Bus Rapid Transit — Room 210
November 12, 2:00 P.M. — Session 2: Fare Policy and Equity — Room 210
November 13, 9:30 A.M. — Session 3: Rail Station Design — Garnet Room
November 13, 1:30 P.M. — Session 4: Cycling Networks — Room 305

Each presenter is allotted 20 minutes, followed by five minutes for questions. Cancellations received by October 31 will be refunded less a $40 handling fee.

────────────────────

[E-mail]
To: Dr. Olumide Bankole
From: Paloma Vega <registration@halstrominstitute.org>
Date: October 14
Subject: Your registration and presentation

Dear Dr. Bankole,

Thank you for registering for the full conference on October 8. Your payment has been received, and a receipt is attached. You also asked to add one ticket to the conference dinner; that charge will appear separately on your card.

Your paper, "Platform Crowding at Mid-Sized Rail Stations," has been accepted and placed in Session 3. Please note that this session has been moved to Room 118 because the room listed on our website is being renovated; the day and time are unchanged. You will be the second of four speakers.

Please upload your slides to the presenter portal by November 5. Our technicians will load them onto the room computer in advance, so personal laptops cannot be connected to the projector.

Finally, the session chair, Dr. Mei-Ling Hsu, would like to hold a brief meeting with all Session 3 speakers at 9:00 A.M. on the day of the session, at the door of the presentation room.

Best regards,

Paloma Vega
Conference Registrar', 'NONE', '["Its starting time", "Its date", "Its location", "Its topic"]', 'C', 1, '[세부 사항] 정답 (C). "this session has been moved to Room 118 ... the day and time are unchanged"이므로 바뀐 것은 장소뿐이다. 오답: (A)·(B) 날짜와 시각은 그대로라고 명시했다. (D) 세션 주제 변경은 언급되지 않았다.
포인트: 세부 사항 — 이메일 둘째 문단(바뀐 것과 유지된 것)', 179),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'When will Dr. Bankole most likely meet with Dr. Hsu?', 'Questions 176-180 refer to the following Web page and e-mail.

[Web page]
Halstrom Institute of Urban Planning
9th Annual Conference on Sustainable Transit
November 12-13, Lakemont Convention Center

Registration fees
Type | By October 10 | After October 10
Full conference (both days) | $280 | $340
Single day | $160 | $190
Student (both days, valid ID required) | $120 | $150

All fees include lunch and admission to the exhibit hall. The conference dinner on November 12 is an additional $55. Presenters must register for the full conference by October 10 in order to be listed in the printed program.

Schedule of paper sessions
November 12, 10:00 A.M. — Session 1: Bus Rapid Transit — Room 210
November 12, 2:00 P.M. — Session 2: Fare Policy and Equity — Room 210
November 13, 9:30 A.M. — Session 3: Rail Station Design — Garnet Room
November 13, 1:30 P.M. — Session 4: Cycling Networks — Room 305

Each presenter is allotted 20 minutes, followed by five minutes for questions. Cancellations received by October 31 will be refunded less a $40 handling fee.

────────────────────

[E-mail]
To: Dr. Olumide Bankole
From: Paloma Vega <registration@halstrominstitute.org>
Date: October 14
Subject: Your registration and presentation

Dear Dr. Bankole,

Thank you for registering for the full conference on October 8. Your payment has been received, and a receipt is attached. You also asked to add one ticket to the conference dinner; that charge will appear separately on your card.

Your paper, "Platform Crowding at Mid-Sized Rail Stations," has been accepted and placed in Session 3. Please note that this session has been moved to Room 118 because the room listed on our website is being renovated; the day and time are unchanged. You will be the second of four speakers.

Please upload your slides to the presenter portal by November 5. Our technicians will load them onto the room computer in advance, so personal laptops cannot be connected to the projector.

Finally, the session chair, Dr. Mei-Ling Hsu, would like to hold a brief meeting with all Session 3 speakers at 9:00 A.M. on the day of the session, at the door of the presentation room.

Best regards,

Paloma Vega
Conference Registrar', 'NONE', '["On November 12 at 9:00 A.M.", "On November 13 at 9:00 A.M.", "On November 13 at 9:30 A.M.", "On November 13 at 1:30 P.M."]', 'B', 1, '[연계(문서1+2)] 정답 (B). 이메일의 "at 9:00 A.M. on the day of the session"과 웹 페이지 일정의 "November 13, 9:30 A.M. — Session 3"을 연결하면 11월 13일 오전 9시다(날짜·시각은 변경 없음). 오답: (A) 11월 12일은 Session 1·2의 날이다. (C) 9시 30분은 세션 시작 시각이다. (D) 1시 30분은 Session 4다.
포인트: 연계 문항 — 웹 페이지 세션 일정 + 이메일 마지막 문단', 180);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'Why did Mr. Zamora write the e-mail?', 'Questions 181-185 refer to the following e-mail and service record.

[E-mail]
To: Service Department <service@orrinaudio.com>
From: Jorge Zamora
Date: February 3
Subject: Warranty repair for Orrin S40 speaker

Hello,

I bought an Orrin S40 wireless speaker from your online store on March 18 of last year. For the past two weeks, the unit has shut itself off after about 10 minutes of use, even when fully charged. I have already reset it as described in the user manual, but the problem continues.

According to the warranty card, defects are covered for one year from the date of purchase, so I believe the speaker should be repaired at no cost. I have attached a copy of my order confirmation.

I should also mention that the carrying strap tore some months ago. I realize that this may not be covered, but if a replacement strap is available, please let me know the price so that I can decide whether to add it.

I will be away from February 20 to March 2, so I would appreciate it if the speaker could be returned before I leave. Please tell me where to send it.

Thank you,

Jorge Zamora

────────────────────

[Service record]
Orrin Audio Service Record

Service order: SR-77105
Customer: Jorge Zamora
Product: S40 wireless speaker | Serial no.: S40-118824
Received: February 9 | Completed: February 12 | Shipped: February 13 (two-day courier)

Work performed
Replaced battery module — Parts: $38.00 | Labor: $25.00 — Covered under warranty
Updated system software — No charge
Replaced carrying strap (accessory; not covered) — $9.00 — Price approved by customer on February 10

Return shipping: No charge
Amount due: $9.00 (charged to card on file)

Technician''s notes: Fault traced to a defective battery module. Unit tested for four hours with no shutdown. Replacement parts are guaranteed for 90 days from the completion date or until the end of the original warranty, whichever is later.

Technician: A. Novak', 'NONE', '["To request service on a product", "To order a new speaker", "To ask for a copy of a manual", "To complain about a late shipment"]', 'A', 1, '[주제·목적] 정답 (A). 스피커가 저절로 꺼지는 증상을 설명하고 "I believe the speaker should be repaired at no cost", "Please tell me where to send it"이라고 하므로 수리 요청이다. 오답: (B) 새 스피커가 아니라 끈의 가격만 물었다. (C) 설명서는 이미 보고 초기화까지 했다. (D) 배송 지연 불만이 아니다.
포인트: 주제·목적 — 이메일 첫째·둘째 문단', 181),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about Mr. Zamora?', 'Questions 181-185 refer to the following e-mail and service record.

[E-mail]
To: Service Department <service@orrinaudio.com>
From: Jorge Zamora
Date: February 3
Subject: Warranty repair for Orrin S40 speaker

Hello,

I bought an Orrin S40 wireless speaker from your online store on March 18 of last year. For the past two weeks, the unit has shut itself off after about 10 minutes of use, even when fully charged. I have already reset it as described in the user manual, but the problem continues.

According to the warranty card, defects are covered for one year from the date of purchase, so I believe the speaker should be repaired at no cost. I have attached a copy of my order confirmation.

I should also mention that the carrying strap tore some months ago. I realize that this may not be covered, but if a replacement strap is available, please let me know the price so that I can decide whether to add it.

I will be away from February 20 to March 2, so I would appreciate it if the speaker could be returned before I leave. Please tell me where to send it.

Thank you,

Jorge Zamora

────────────────────

[Service record]
Orrin Audio Service Record

Service order: SR-77105
Customer: Jorge Zamora
Product: S40 wireless speaker | Serial no.: S40-118824
Received: February 9 | Completed: February 12 | Shipped: February 13 (two-day courier)

Work performed
Replaced battery module — Parts: $38.00 | Labor: $25.00 — Covered under warranty
Updated system software — No charge
Replaced carrying strap (accessory; not covered) — $9.00 — Price approved by customer on February 10

Return shipping: No charge
Amount due: $9.00 (charged to card on file)

Technician''s notes: Fault traced to a defective battery module. Unit tested for four hours with no shutdown. Replacement parts are guaranteed for 90 days from the completion date or until the end of the original warranty, whichever is later.

Technician: A. Novak', 'NONE', '["He purchased the speaker at a retail store.", "He has misplaced his proof of purchase.", "He has had the speaker repaired by Orrin before.", "He attempted to solve the problem on his own."]', 'D', 1, '[추론] 정답 (D). "I have already reset it as described in the user manual, but the problem continues"에서 스스로 문제를 해결해 보려 했음을 알 수 있다. 오답: (A) "from your online store"에서 샀다. (B) 주문 확인서 사본을 첨부했다. (C) 이전 수리 이력은 언급되지 않았다.
포인트: 추론 — 이메일 첫 문단', 182),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is most likely true about the speaker?', 'Questions 181-185 refer to the following e-mail and service record.

[E-mail]
To: Service Department <service@orrinaudio.com>
From: Jorge Zamora
Date: February 3
Subject: Warranty repair for Orrin S40 speaker

Hello,

I bought an Orrin S40 wireless speaker from your online store on March 18 of last year. For the past two weeks, the unit has shut itself off after about 10 minutes of use, even when fully charged. I have already reset it as described in the user manual, but the problem continues.

According to the warranty card, defects are covered for one year from the date of purchase, so I believe the speaker should be repaired at no cost. I have attached a copy of my order confirmation.

I should also mention that the carrying strap tore some months ago. I realize that this may not be covered, but if a replacement strap is available, please let me know the price so that I can decide whether to add it.

I will be away from February 20 to March 2, so I would appreciate it if the speaker could be returned before I leave. Please tell me where to send it.

Thank you,

Jorge Zamora

────────────────────

[Service record]
Orrin Audio Service Record

Service order: SR-77105
Customer: Jorge Zamora
Product: S40 wireless speaker | Serial no.: S40-118824
Received: February 9 | Completed: February 12 | Shipped: February 13 (two-day courier)

Work performed
Replaced battery module — Parts: $38.00 | Labor: $25.00 — Covered under warranty
Updated system software — No charge
Replaced carrying strap (accessory; not covered) — $9.00 — Price approved by customer on February 10

Return shipping: No charge
Amount due: $9.00 (charged to card on file)

Technician''s notes: Fault traced to a defective battery module. Unit tested for four hours with no shutdown. Replacement parts are guaranteed for 90 days from the completion date or until the end of the original warranty, whichever is later.

Technician: A. Novak', 'NONE', '["It was repaired after its warranty had expired.", "It was sent back before Mr. Zamora''s departure.", "It was replaced with a new unit.", "It was shipped on the day it was received."]', 'B', 1, '[연계(문서1+2)] 정답 (B). 이메일의 "I will be away from February 20"과 수리 내역서의 "Shipped: February 13 (two-day courier)"를 연결하면 출발 전에 발송되었다. 오답: (A) 구매일이 작년 3월 18일이라 2월 수리는 보증 기간 안이고 "Covered under warranty"라고 적혀 있다. (C) 배터리 모듈만 교체했다. (D) 2월 9일에 접수하고 13일에 발송했다.
포인트: 연계 문항 — 이메일의 출발일 + 수리 내역서의 발송일', 183),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'According to the service record, what caused the speaker to shut off?', 'Questions 181-185 refer to the following e-mail and service record.

[E-mail]
To: Service Department <service@orrinaudio.com>
From: Jorge Zamora
Date: February 3
Subject: Warranty repair for Orrin S40 speaker

Hello,

I bought an Orrin S40 wireless speaker from your online store on March 18 of last year. For the past two weeks, the unit has shut itself off after about 10 minutes of use, even when fully charged. I have already reset it as described in the user manual, but the problem continues.

According to the warranty card, defects are covered for one year from the date of purchase, so I believe the speaker should be repaired at no cost. I have attached a copy of my order confirmation.

I should also mention that the carrying strap tore some months ago. I realize that this may not be covered, but if a replacement strap is available, please let me know the price so that I can decide whether to add it.

I will be away from February 20 to March 2, so I would appreciate it if the speaker could be returned before I leave. Please tell me where to send it.

Thank you,

Jorge Zamora

────────────────────

[Service record]
Orrin Audio Service Record

Service order: SR-77105
Customer: Jorge Zamora
Product: S40 wireless speaker | Serial no.: S40-118824
Received: February 9 | Completed: February 12 | Shipped: February 13 (two-day courier)

Work performed
Replaced battery module — Parts: $38.00 | Labor: $25.00 — Covered under warranty
Updated system software — No charge
Replaced carrying strap (accessory; not covered) — $9.00 — Price approved by customer on February 10

Return shipping: No charge
Amount due: $9.00 (charged to card on file)

Technician''s notes: Fault traced to a defective battery module. Unit tested for four hours with no shutdown. Replacement parts are guaranteed for 90 days from the completion date or until the end of the original warranty, whichever is later.

Technician: A. Novak', 'NONE', '["Outdated system software", "A torn carrying strap", "A faulty battery component", "An error made during a reset"]', 'C', 1, '[세부 사항] 정답 (C). Technician''s notes의 "Fault traced to a defective battery module"이 근거다. defective battery module → a faulty battery component. 오답: (A) 소프트웨어는 업데이트만 했을 뿐 원인으로 적혀 있지 않다. (B) 끈은 별도의 부속품 교체다. (D) 초기화는 고객이 이메일에서 이미 해 보았다고 한 조치일 뿐 원인이 아니다.
포인트: 세부 사항 — 수리 내역서 Technician''s notes', 184),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the guarantee on the replacement parts?', 'Questions 181-185 refer to the following e-mail and service record.

[E-mail]
To: Service Department <service@orrinaudio.com>
From: Jorge Zamora
Date: February 3
Subject: Warranty repair for Orrin S40 speaker

Hello,

I bought an Orrin S40 wireless speaker from your online store on March 18 of last year. For the past two weeks, the unit has shut itself off after about 10 minutes of use, even when fully charged. I have already reset it as described in the user manual, but the problem continues.

According to the warranty card, defects are covered for one year from the date of purchase, so I believe the speaker should be repaired at no cost. I have attached a copy of my order confirmation.

I should also mention that the carrying strap tore some months ago. I realize that this may not be covered, but if a replacement strap is available, please let me know the price so that I can decide whether to add it.

I will be away from February 20 to March 2, so I would appreciate it if the speaker could be returned before I leave. Please tell me where to send it.

Thank you,

Jorge Zamora

────────────────────

[Service record]
Orrin Audio Service Record

Service order: SR-77105
Customer: Jorge Zamora
Product: S40 wireless speaker | Serial no.: S40-118824
Received: February 9 | Completed: February 12 | Shipped: February 13 (two-day courier)

Work performed
Replaced battery module — Parts: $38.00 | Labor: $25.00 — Covered under warranty
Updated system software — No charge
Replaced carrying strap (accessory; not covered) — $9.00 — Price approved by customer on February 10

Return shipping: No charge
Amount due: $9.00 (charged to card on file)

Technician''s notes: Fault traced to a defective battery module. Unit tested for four hours with no shutdown. Replacement parts are guaranteed for 90 days from the completion date or until the end of the original warranty, whichever is later.

Technician: A. Novak', 'NONE', '["It lasts for one year from the completion date.", "It will outlast the original warranty.", "It applies only to the software update.", "It ends on the day Mr. Zamora returns from his trip."]', 'B', 1, '[연계(문서1+2)] 정답 (B). 수리 내역서의 "guaranteed for 90 days from the completion date or until the end of the original warranty, whichever is later"와 "Completed: February 12", 이메일의 구매일(작년 March 18, 보증 1년)을 연결하면, 원래 보증은 3월 18일에 끝나고 수리 완료일로부터 90일은 5월 중순이므로 부품 보증이 원래 보증보다 늦게 끝난다. 오답: (A) 1년이 아니라 90일이다. (C) 교체한 부품에 적용된다. (D) 3월 2일은 보증과 관계없는 날짜다.
포인트: 연계 문항 — 수리 내역서의 보증 조건(until) + 이메일의 구매일', 185),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'According to the advertisement, what is available to all members?', 'Questions 186-190 refer to the following advertisement, form, and e-mail.

[Advertisement]
Stridewell Fitness & Aquatics
Now open at 300 Foundry Road, Hollin Springs

Choose the membership that fits your routine:

Basic — $39 per month — Gym floor and locker rooms, 5:00 A.M. to 11:00 P.M.
Plus — $55 per month — Everything in Basic, plus unlimited group classes (yoga, cycling, strength circuits)
Premier — $72 per month — Everything in Plus, plus the 25-meter pool, the sauna, and two guest passes each month

All memberships require a one-time enrollment fee of $50. Sign up by April 30, and the enrollment fee will be waived. Members who commit to 12 months receive a free fitness assessment with a certified trainer.

Personal training is available to all members at $45 per session. Towel service is included for Premier members and costs $5 per month for all others.

Tour the facility any day between 9:00 A.M. and 8:00 P.M. No appointment is needed.

────────────────────

[Form]
Stridewell Fitness & Aquatics
Membership Application

Date: April 22
Name: Beatrix Nakamura
Address: 58 Sorrel Court, Hollin Springs
E-mail: bnakamura@quillmail.com
Membership type: [ ] Basic  [X] Plus  [ ] Premier
Term: [X] 12 months  [ ] Month to month
Add-ons: [X] Towel service  [ ] Personal training
Start date: May 1
Payment method: Automatic monthly withdrawal from bank account ending 4471
Referred by: Mateo Ortiz (coworker; current member)
Signature: Beatrix Nakamura

────────────────────

[E-mail]
To: Beatrix Nakamura
From: Stridewell Member Services <members@stridewellfitness.com>
Date: June 10
Subject: Re: Membership change request

Dear Ms. Nakamura,

Thank you for your message. We are glad to hear that you have been enjoying the cycling classes. I have processed your request to upgrade your membership so that you can use the pool. The new rate will take effect on July 1, and your 12-month term will continue from your original start date; the upgrade does not extend your commitment.

Because towel service is part of your new membership, I have removed that add-on charge from your account as of July 1.

You also asked about the fitness assessment. Our records show that you have not yet scheduled it. Appointments are available with our trainer Tobias Engel on most weekday evenings; you can book one at the front desk or through the member app.

Finally, since Mr. Ortiz referred you, he was sent a one-month credit in May under our referral program. Should you refer a friend yourself, the same benefit will apply to you.

Sincerely,

Dalia Haddad
Member Services', 'NONE', '["Personal training for an added fee", "Two guest passes every month", "Complimentary daily towel service", "Unlimited group exercise classes"]', 'A', 1, '[세부 사항] 정답 (A). "Personal training is available to all members at $45 per session"이 근거다. 오답: (B) 게스트 이용권은 Premier에만 있다. (C) 수건 서비스는 Premier만 포함이고 나머지는 월 $5다. (D) 그룹 수업은 Plus 이상이다.
포인트: 세부 사항 — 광고의 등급별 혜택', 186),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about Ms. Nakamura?', 'Questions 186-190 refer to the following advertisement, form, and e-mail.

[Advertisement]
Stridewell Fitness & Aquatics
Now open at 300 Foundry Road, Hollin Springs

Choose the membership that fits your routine:

Basic — $39 per month — Gym floor and locker rooms, 5:00 A.M. to 11:00 P.M.
Plus — $55 per month — Everything in Basic, plus unlimited group classes (yoga, cycling, strength circuits)
Premier — $72 per month — Everything in Plus, plus the 25-meter pool, the sauna, and two guest passes each month

All memberships require a one-time enrollment fee of $50. Sign up by April 30, and the enrollment fee will be waived. Members who commit to 12 months receive a free fitness assessment with a certified trainer.

Personal training is available to all members at $45 per session. Towel service is included for Premier members and costs $5 per month for all others.

Tour the facility any day between 9:00 A.M. and 8:00 P.M. No appointment is needed.

────────────────────

[Form]
Stridewell Fitness & Aquatics
Membership Application

Date: April 22
Name: Beatrix Nakamura
Address: 58 Sorrel Court, Hollin Springs
E-mail: bnakamura@quillmail.com
Membership type: [ ] Basic  [X] Plus  [ ] Premier
Term: [X] 12 months  [ ] Month to month
Add-ons: [X] Towel service  [ ] Personal training
Start date: May 1
Payment method: Automatic monthly withdrawal from bank account ending 4471
Referred by: Mateo Ortiz (coworker; current member)
Signature: Beatrix Nakamura

────────────────────

[E-mail]
To: Beatrix Nakamura
From: Stridewell Member Services <members@stridewellfitness.com>
Date: June 10
Subject: Re: Membership change request

Dear Ms. Nakamura,

Thank you for your message. We are glad to hear that you have been enjoying the cycling classes. I have processed your request to upgrade your membership so that you can use the pool. The new rate will take effect on July 1, and your 12-month term will continue from your original start date; the upgrade does not extend your commitment.

Because towel service is part of your new membership, I have removed that add-on charge from your account as of July 1.

You also asked about the fitness assessment. Our records show that you have not yet scheduled it. Appointments are available with our trainer Tobias Engel on most weekday evenings; you can book one at the front desk or through the member app.

Finally, since Mr. Ortiz referred you, he was sent a one-month credit in May under our referral program. Should you refer a friend yourself, the same benefit will apply to you.

Sincerely,

Dalia Haddad
Member Services', 'NONE', '["She toured the facility with Mr. Ortiz.", "She signed up for personal training.", "She selected the lowest-priced membership.", "She did not pay an enrollment fee."]', 'D', 1, '[연계(문서1+2)] 정답 (D). 광고의 "Sign up by April 30, and the enrollment fee will be waived"와 신청서의 "Date: April 22"를 연결하면 가입비가 면제되었다. 오답: (A) Ortiz는 소개한 동료일 뿐 함께 견학했다는 말이 없다. (B) Personal training 칸은 비어 있다. (C) 가장 싼 Basic이 아니라 Plus를 골랐다.
포인트: 연계 문항 — 광고의 면제 조건(waive) + 신청서 날짜', 187),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'How much will Ms. Nakamura most likely pay each month for her membership as of July 1?', 'Questions 186-190 refer to the following advertisement, form, and e-mail.

[Advertisement]
Stridewell Fitness & Aquatics
Now open at 300 Foundry Road, Hollin Springs

Choose the membership that fits your routine:

Basic — $39 per month — Gym floor and locker rooms, 5:00 A.M. to 11:00 P.M.
Plus — $55 per month — Everything in Basic, plus unlimited group classes (yoga, cycling, strength circuits)
Premier — $72 per month — Everything in Plus, plus the 25-meter pool, the sauna, and two guest passes each month

All memberships require a one-time enrollment fee of $50. Sign up by April 30, and the enrollment fee will be waived. Members who commit to 12 months receive a free fitness assessment with a certified trainer.

Personal training is available to all members at $45 per session. Towel service is included for Premier members and costs $5 per month for all others.

Tour the facility any day between 9:00 A.M. and 8:00 P.M. No appointment is needed.

────────────────────

[Form]
Stridewell Fitness & Aquatics
Membership Application

Date: April 22
Name: Beatrix Nakamura
Address: 58 Sorrel Court, Hollin Springs
E-mail: bnakamura@quillmail.com
Membership type: [ ] Basic  [X] Plus  [ ] Premier
Term: [X] 12 months  [ ] Month to month
Add-ons: [X] Towel service  [ ] Personal training
Start date: May 1
Payment method: Automatic monthly withdrawal from bank account ending 4471
Referred by: Mateo Ortiz (coworker; current member)
Signature: Beatrix Nakamura

────────────────────

[E-mail]
To: Beatrix Nakamura
From: Stridewell Member Services <members@stridewellfitness.com>
Date: June 10
Subject: Re: Membership change request

Dear Ms. Nakamura,

Thank you for your message. We are glad to hear that you have been enjoying the cycling classes. I have processed your request to upgrade your membership so that you can use the pool. The new rate will take effect on July 1, and your 12-month term will continue from your original start date; the upgrade does not extend your commitment.

Because towel service is part of your new membership, I have removed that add-on charge from your account as of July 1.

You also asked about the fitness assessment. Our records show that you have not yet scheduled it. Appointments are available with our trainer Tobias Engel on most weekday evenings; you can book one at the front desk or through the member app.

Finally, since Mr. Ortiz referred you, he was sent a one-month credit in May under our referral program. Should you refer a friend yourself, the same benefit will apply to you.

Sincerely,

Dalia Haddad
Member Services', 'NONE', '["$72", "$77", "$117", "$122"]', 'A', 1, '[연계(문서1+3)] 정답 (A). 이메일의 "upgrade your membership so that you can use the pool"과 광고에서 수영장이 포함된 등급이 Premier($72)뿐이라는 점을 연결한다. 수건 서비스는 Premier에 포함되어 추가 요금이 없어진다. 오답: (B) $72에 수건 $5를 또 더한 값. (C) 개인 지도 1회 $45를 더한 값. (D) 면제받은 가입비 $50을 더한 값.
포인트: 연계 문항 — 광고의 등급별 요금 + 이메일의 등급 변경', 188),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'Why is Ms. Nakamura entitled to a fitness assessment?', 'Questions 186-190 refer to the following advertisement, form, and e-mail.

[Advertisement]
Stridewell Fitness & Aquatics
Now open at 300 Foundry Road, Hollin Springs

Choose the membership that fits your routine:

Basic — $39 per month — Gym floor and locker rooms, 5:00 A.M. to 11:00 P.M.
Plus — $55 per month — Everything in Basic, plus unlimited group classes (yoga, cycling, strength circuits)
Premier — $72 per month — Everything in Plus, plus the 25-meter pool, the sauna, and two guest passes each month

All memberships require a one-time enrollment fee of $50. Sign up by April 30, and the enrollment fee will be waived. Members who commit to 12 months receive a free fitness assessment with a certified trainer.

Personal training is available to all members at $45 per session. Towel service is included for Premier members and costs $5 per month for all others.

Tour the facility any day between 9:00 A.M. and 8:00 P.M. No appointment is needed.

────────────────────

[Form]
Stridewell Fitness & Aquatics
Membership Application

Date: April 22
Name: Beatrix Nakamura
Address: 58 Sorrel Court, Hollin Springs
E-mail: bnakamura@quillmail.com
Membership type: [ ] Basic  [X] Plus  [ ] Premier
Term: [X] 12 months  [ ] Month to month
Add-ons: [X] Towel service  [ ] Personal training
Start date: May 1
Payment method: Automatic monthly withdrawal from bank account ending 4471
Referred by: Mateo Ortiz (coworker; current member)
Signature: Beatrix Nakamura

────────────────────

[E-mail]
To: Beatrix Nakamura
From: Stridewell Member Services <members@stridewellfitness.com>
Date: June 10
Subject: Re: Membership change request

Dear Ms. Nakamura,

Thank you for your message. We are glad to hear that you have been enjoying the cycling classes. I have processed your request to upgrade your membership so that you can use the pool. The new rate will take effect on July 1, and your 12-month term will continue from your original start date; the upgrade does not extend your commitment.

Because towel service is part of your new membership, I have removed that add-on charge from your account as of July 1.

You also asked about the fitness assessment. Our records show that you have not yet scheduled it. Appointments are available with our trainer Tobias Engel on most weekday evenings; you can book one at the front desk or through the member app.

Finally, since Mr. Ortiz referred you, he was sent a one-month credit in May under our referral program. Should you refer a friend yourself, the same benefit will apply to you.

Sincerely,

Dalia Haddad
Member Services', 'NONE', '["She upgraded her membership.", "She was referred by a member.", "She signed up for a one-year term.", "She paid for personal training."]', 'C', 1, '[연계(문서1+2)] 정답 (C). 광고의 "Members who commit to 12 months receive a free fitness assessment"와 신청서의 "Term: [X] 12 months"를 연결한다. 12 months → a one-year term. 오답: (A) 등급 변경은 평가와 관계없다. (B) 추천 혜택은 추천한 Ortiz가 받은 한 달 크레디트다. (D) 개인 지도는 신청하지 않았다.
포인트: 연계 문항 — 광고의 12개월 조건 + 신청서의 Term', 189),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'Why did Mr. Ortiz receive a credit?', 'Questions 186-190 refer to the following advertisement, form, and e-mail.

[Advertisement]
Stridewell Fitness & Aquatics
Now open at 300 Foundry Road, Hollin Springs

Choose the membership that fits your routine:

Basic — $39 per month — Gym floor and locker rooms, 5:00 A.M. to 11:00 P.M.
Plus — $55 per month — Everything in Basic, plus unlimited group classes (yoga, cycling, strength circuits)
Premier — $72 per month — Everything in Plus, plus the 25-meter pool, the sauna, and two guest passes each month

All memberships require a one-time enrollment fee of $50. Sign up by April 30, and the enrollment fee will be waived. Members who commit to 12 months receive a free fitness assessment with a certified trainer.

Personal training is available to all members at $45 per session. Towel service is included for Premier members and costs $5 per month for all others.

Tour the facility any day between 9:00 A.M. and 8:00 P.M. No appointment is needed.

────────────────────

[Form]
Stridewell Fitness & Aquatics
Membership Application

Date: April 22
Name: Beatrix Nakamura
Address: 58 Sorrel Court, Hollin Springs
E-mail: bnakamura@quillmail.com
Membership type: [ ] Basic  [X] Plus  [ ] Premier
Term: [X] 12 months  [ ] Month to month
Add-ons: [X] Towel service  [ ] Personal training
Start date: May 1
Payment method: Automatic monthly withdrawal from bank account ending 4471
Referred by: Mateo Ortiz (coworker; current member)
Signature: Beatrix Nakamura

────────────────────

[E-mail]
To: Beatrix Nakamura
From: Stridewell Member Services <members@stridewellfitness.com>
Date: June 10
Subject: Re: Membership change request

Dear Ms. Nakamura,

Thank you for your message. We are glad to hear that you have been enjoying the cycling classes. I have processed your request to upgrade your membership so that you can use the pool. The new rate will take effect on July 1, and your 12-month term will continue from your original start date; the upgrade does not extend your commitment.

Because towel service is part of your new membership, I have removed that add-on charge from your account as of July 1.

You also asked about the fitness assessment. Our records show that you have not yet scheduled it. Appointments are available with our trainer Tobias Engel on most weekday evenings; you can book one at the front desk or through the member app.

Finally, since Mr. Ortiz referred you, he was sent a one-month credit in May under our referral program. Should you refer a friend yourself, the same benefit will apply to you.

Sincerely,

Dalia Haddad
Member Services', 'NONE', '["He was billed incorrectly in May.", "He upgraded to a Premier membership.", "He completed a fitness assessment.", "He brought in a new member."]', 'D', 1, '[세부 사항] 정답 (D). 이메일의 "since Mr. Ortiz referred you, he was sent a one-month credit in May under our referral program"이 근거다. referred you → brought in a new member. 오답: (A) 청구 오류 이야기는 없다. (B) 등급을 올린 사람은 Nakamura다. (C) 체력 평가는 Nakamura가 아직 예약하지 않은 항목이다.
포인트: 세부 사항 — 이메일 마지막 문단', 190),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'According to the itinerary, what is included in Mr. Daoud''s fare?', 'Questions 191-195 refer to the following itinerary, e-mail, and receipt.

[Itinerary]
Aurelia Air Itinerary

Confirmation code: QX7R2M
Passenger: Mr. Bashir Daoud
Fare type: Flex Economy
Issued: August 4

Flight AU 318 — September 15 — Depart Eldridge (ELD) 7:10 A.M. — Arrive Halden Bay (HDB) 9:25 A.M. — Seat 22C
Flight AU 327 — September 18 — Depart Halden Bay (HDB) 6:45 P.M. — Arrive Eldridge (ELD) 9:00 P.M. — Seat 19D

Baggage: One carry-on bag and one checked bag (up to 23 kg) are included.

Fare rules
Saver Economy: Changes are not permitted.
Flex Economy: Date or time changes are permitted up to three hours before departure for a $30 fee per flight, plus any difference in fare.
Business: Changes are permitted free of charge.

Changes can be made at www.aureliaair.com or by calling 555-0188. Online check-in opens 24 hours before departure and closes one hour before it.

────────────────────

[E-mail]
To: Carmen Iglesias
From: Bashir Daoud
Date: September 9
Subject: Halden Bay trip

Hi Carmen,

The client in Halden Bay has just added a site visit to our agenda. It will take place at their distribution center on the morning of September 19, so I will not be able to fly home on the 18th as planned. Could you move my return to the 19th? Any flight leaving after 3:00 P.M. would work, since the visit should wrap up by noon and the center is about an hour from the airport.

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

Original flight: AU 327 — September 18 — Halden Bay to Eldridge — Depart 6:45 P.M.
New flight: AU 325 — September 19 — Halden Bay to Eldridge — Depart 4:20 P.M. — Arrive 6:35 P.M. — Seat 8A

Change fee: $30.00
Fare difference: $46.00
Total charged: $76.00
Payment: Corporate card ending 9042

Your seat on the new flight is in the Extra Legroom section. It has been provided at no charge because no standard seats were available. All other details of your itinerary remain unchanged.', 'NONE', '["A hot meal on each flight", "One piece of checked luggage", "Unlimited free changes", "A seat in the Extra Legroom section"]', 'B', 1, '[세부 사항] 정답 (B). "One carry-on bag and one checked bag (up to 23 kg) are included"가 근거다. one checked bag → one piece of checked luggage. 오답: (A) 기내식 언급이 없다. (C) Flex Economy는 편당 $30 수수료와 운임 차액을 낸다. (D) 추가 공간 좌석은 변경 뒤 영수증에 나오는 내용으로 운임 포함 항목이 아니다.
포인트: 세부 사항 — 여정표의 Baggage 줄', 191),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'Why does Mr. Daoud want to change his return flight?', 'Questions 191-195 refer to the following itinerary, e-mail, and receipt.

[Itinerary]
Aurelia Air Itinerary

Confirmation code: QX7R2M
Passenger: Mr. Bashir Daoud
Fare type: Flex Economy
Issued: August 4

Flight AU 318 — September 15 — Depart Eldridge (ELD) 7:10 A.M. — Arrive Halden Bay (HDB) 9:25 A.M. — Seat 22C
Flight AU 327 — September 18 — Depart Halden Bay (HDB) 6:45 P.M. — Arrive Eldridge (ELD) 9:00 P.M. — Seat 19D

Baggage: One carry-on bag and one checked bag (up to 23 kg) are included.

Fare rules
Saver Economy: Changes are not permitted.
Flex Economy: Date or time changes are permitted up to three hours before departure for a $30 fee per flight, plus any difference in fare.
Business: Changes are permitted free of charge.

Changes can be made at www.aureliaair.com or by calling 555-0188. Online check-in opens 24 hours before departure and closes one hour before it.

────────────────────

[E-mail]
To: Carmen Iglesias
From: Bashir Daoud
Date: September 9
Subject: Halden Bay trip

Hi Carmen,

The client in Halden Bay has just added a site visit to our agenda. It will take place at their distribution center on the morning of September 19, so I will not be able to fly home on the 18th as planned. Could you move my return to the 19th? Any flight leaving after 3:00 P.M. would work, since the visit should wrap up by noon and the center is about an hour from the airport.

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

Original flight: AU 327 — September 18 — Halden Bay to Eldridge — Depart 6:45 P.M.
New flight: AU 325 — September 19 — Halden Bay to Eldridge — Depart 4:20 P.M. — Arrive 6:35 P.M. — Seat 8A

Change fee: $30.00
Fare difference: $46.00
Total charged: $76.00
Payment: Corporate card ending 9042

Your seat on the new flight is in the Extra Legroom section. It has been provided at no charge because no standard seats were available. All other details of your itinerary remain unchanged.', 'NONE', '["His client canceled a meeting.", "His hotel has no rooms available.", "An activity was added to his schedule.", "His original flight was delayed."]', 'C', 1, '[세부 사항] 정답 (C). "The client in Halden Bay has just added a site visit to our agenda ... so I will not be able to fly home on the 18th"가 근거다. added a site visit → an activity was added. 오답: (A) 회의가 취소된 것이 아니라 일정이 추가되었다. (B) 호텔은 숙박을 연장해 주었다. (D) 항공편 지연 이야기는 없다.
포인트: 세부 사항 — 이메일 첫 문단', 192),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the Larkmoor Hotel?', 'Questions 191-195 refer to the following itinerary, e-mail, and receipt.

[Itinerary]
Aurelia Air Itinerary

Confirmation code: QX7R2M
Passenger: Mr. Bashir Daoud
Fare type: Flex Economy
Issued: August 4

Flight AU 318 — September 15 — Depart Eldridge (ELD) 7:10 A.M. — Arrive Halden Bay (HDB) 9:25 A.M. — Seat 22C
Flight AU 327 — September 18 — Depart Halden Bay (HDB) 6:45 P.M. — Arrive Eldridge (ELD) 9:00 P.M. — Seat 19D

Baggage: One carry-on bag and one checked bag (up to 23 kg) are included.

Fare rules
Saver Economy: Changes are not permitted.
Flex Economy: Date or time changes are permitted up to three hours before departure for a $30 fee per flight, plus any difference in fare.
Business: Changes are permitted free of charge.

Changes can be made at www.aureliaair.com or by calling 555-0188. Online check-in opens 24 hours before departure and closes one hour before it.

────────────────────

[E-mail]
To: Carmen Iglesias
From: Bashir Daoud
Date: September 9
Subject: Halden Bay trip

Hi Carmen,

The client in Halden Bay has just added a site visit to our agenda. It will take place at their distribution center on the morning of September 19, so I will not be able to fly home on the 18th as planned. Could you move my return to the 19th? Any flight leaving after 3:00 P.M. would work, since the visit should wrap up by noon and the center is about an hour from the airport.

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

Original flight: AU 327 — September 18 — Halden Bay to Eldridge — Depart 6:45 P.M.
New flight: AU 325 — September 19 — Halden Bay to Eldridge — Depart 4:20 P.M. — Arrive 6:35 P.M. — Seat 8A

Change fee: $30.00
Fare difference: $46.00
Total charged: $76.00
Payment: Corporate card ending 9042

Your seat on the new flight is in the Extra Legroom section. It has been provided at no charge because no standard seats were available. All other details of your itinerary remain unchanged.', 'NONE', '["It is about an hour from the airport.", "It raised its rate for the extra night.", "It was originally booked by Ms. Iglesias.", "It had a room free for one more night."]', 'D', 1, '[추론] 정답 (D). "I have already called them, and they extended my reservation at the same rate"에서 하루 더 묵을 방이 있었음을 알 수 있다. 오답: (A) 공항에서 한 시간 거리인 곳은 고객사의 물류센터다. (B) at the same rate이므로 요금은 같다. (C) 처음 예약을 누가 했는지는 나와 있지 않다.
포인트: 추론 — 이메일 둘째 문단(호텔과 물류센터 구별)', 193),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about flight AU 325?', 'Questions 191-195 refer to the following itinerary, e-mail, and receipt.

[Itinerary]
Aurelia Air Itinerary

Confirmation code: QX7R2M
Passenger: Mr. Bashir Daoud
Fare type: Flex Economy
Issued: August 4

Flight AU 318 — September 15 — Depart Eldridge (ELD) 7:10 A.M. — Arrive Halden Bay (HDB) 9:25 A.M. — Seat 22C
Flight AU 327 — September 18 — Depart Halden Bay (HDB) 6:45 P.M. — Arrive Eldridge (ELD) 9:00 P.M. — Seat 19D

Baggage: One carry-on bag and one checked bag (up to 23 kg) are included.

Fare rules
Saver Economy: Changes are not permitted.
Flex Economy: Date or time changes are permitted up to three hours before departure for a $30 fee per flight, plus any difference in fare.
Business: Changes are permitted free of charge.

Changes can be made at www.aureliaair.com or by calling 555-0188. Online check-in opens 24 hours before departure and closes one hour before it.

────────────────────

[E-mail]
To: Carmen Iglesias
From: Bashir Daoud
Date: September 9
Subject: Halden Bay trip

Hi Carmen,

The client in Halden Bay has just added a site visit to our agenda. It will take place at their distribution center on the morning of September 19, so I will not be able to fly home on the 18th as planned. Could you move my return to the 19th? Any flight leaving after 3:00 P.M. would work, since the visit should wrap up by noon and the center is about an hour from the airport.

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

Original flight: AU 327 — September 18 — Halden Bay to Eldridge — Depart 6:45 P.M.
New flight: AU 325 — September 19 — Halden Bay to Eldridge — Depart 4:20 P.M. — Arrive 6:35 P.M. — Seat 8A

Change fee: $30.00
Fare difference: $46.00
Total charged: $76.00
Payment: Corporate card ending 9042

Your seat on the new flight is in the Extra Legroom section. It has been provided at no charge because no standard seats were available. All other details of your itinerary remain unchanged.', 'NONE', '["It lands in Halden Bay at 6:35 P.M.", "It is operated by a different airline.", "It leaves after the time Mr. Daoud specified.", "It costs less than his original return flight."]', 'C', 1, '[연계(문서2+3)] 정답 (C). 이메일의 "Any flight leaving after 3:00 P.M. would work"와 영수증의 "AU 325 ... Depart 4:20 P.M."을 연결하면 요청한 시각 이후에 출발한다. 오답: (A) 6시 35분에 도착하는 곳은 Eldridge다. (B) 같은 Aurelia Air 항공편이다. (D) Fare difference $46.00을 더 냈으므로 더 비싸다.
포인트: 연계 문항 — 이메일의 요청 시각 + 영수증의 새 항공편', 194),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What is most likely true about Mr. Daoud''s trip?', 'Questions 191-195 refer to the following itinerary, e-mail, and receipt.

[Itinerary]
Aurelia Air Itinerary

Confirmation code: QX7R2M
Passenger: Mr. Bashir Daoud
Fare type: Flex Economy
Issued: August 4

Flight AU 318 — September 15 — Depart Eldridge (ELD) 7:10 A.M. — Arrive Halden Bay (HDB) 9:25 A.M. — Seat 22C
Flight AU 327 — September 18 — Depart Halden Bay (HDB) 6:45 P.M. — Arrive Eldridge (ELD) 9:00 P.M. — Seat 19D

Baggage: One carry-on bag and one checked bag (up to 23 kg) are included.

Fare rules
Saver Economy: Changes are not permitted.
Flex Economy: Date or time changes are permitted up to three hours before departure for a $30 fee per flight, plus any difference in fare.
Business: Changes are permitted free of charge.

Changes can be made at www.aureliaair.com or by calling 555-0188. Online check-in opens 24 hours before departure and closes one hour before it.

────────────────────

[E-mail]
To: Carmen Iglesias
From: Bashir Daoud
Date: September 9
Subject: Halden Bay trip

Hi Carmen,

The client in Halden Bay has just added a site visit to our agenda. It will take place at their distribution center on the morning of September 19, so I will not be able to fly home on the 18th as planned. Could you move my return to the 19th? Any flight leaving after 3:00 P.M. would work, since the visit should wrap up by noon and the center is about an hour from the airport.

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

Original flight: AU 327 — September 18 — Halden Bay to Eldridge — Depart 6:45 P.M.
New flight: AU 325 — September 19 — Halden Bay to Eldridge — Depart 4:20 P.M. — Arrive 6:35 P.M. — Seat 8A

Change fee: $30.00
Fare difference: $46.00
Total charged: $76.00
Payment: Corporate card ending 9042

Your seat on the new flight is in the Extra Legroom section. It has been provided at no charge because no standard seats were available. All other details of your itinerary remain unchanged.', 'NONE', '["He will arrive in Eldridge at 9:00 P.M. on September 19.", "He will leave Eldridge on a 7:10 A.M. flight.", "He will sit in seat 19D on his way home.", "He will pay extra for additional legroom."]', 'B', 1, '[연계(문서1+3)] 정답 (B). 영수증의 "All other details of your itinerary remain unchanged"와 여정표의 "AU 318 — September 15 — Depart Eldridge (ELD) 7:10 A.M."을 연결하면 가는 편은 그대로다. 오답: (A) 새 귀국편은 6시 35분에 도착한다. (C) 19D는 변경 전 AU 327의 좌석이고 새 좌석은 8A다. (D) 추가 공간 좌석은 "at no charge"다.
포인트: 연계 문항 — 여정표의 가는 편 + 영수증의 변경 범위', 195),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'According to the web page, what is true about all of the listed properties?', 'Questions 196-200 refer to the following Web page and e-mails.

[Web page]
Thornwick Commercial Realty
Retail and Office Space for Lease in Sablefield

Unit 2, 14 Fenwick Street — 60 square meters — $1,900 per month
First-floor storefront with a large display window. Suited to retail. No kitchen facilities.

Unit 5, 14 Fenwick Street — 85 square meters — $2,300 per month
Second-floor office suite with two private rooms and a reception area. Elevator access.

9 Quarrymill Lane — 110 square meters — $3,100 per month
Former café with a fully equipped commercial kitchen and seating for 40. Outdoor terrace.

31 Saltmarsh Road — 140 square meters — $2,800 per month
Spacious open-plan studio in a converted warehouse. Loading door at the rear; on-site parking for six vehicles.

All leases run for a minimum of two years. A security deposit equal to two months'' rent is due at signing. Tenants are responsible for electricity and water; building insurance and exterior maintenance are covered by the owner. To arrange a viewing, contact Viktor Rasmussen at vrasmussen@thornwickrealty.com.

────────────────────

[E-mail]
To: Viktor Rasmussen
From: Soraya Kimathi
Date: July 7
Subject: Space for a cooking school

Dear Mr. Rasmussen,

I am the owner of Sage & Ember, a catering company based in Ravensholt, and I am planning to open a small cooking school in Sablefield this fall. I saw your listings and would like to view the property that already has a professional kitchen, since installing one elsewhere would be beyond my budget.

The lease term is my main concern. Your listing mentions a minimum of two years, but because this is a new venture, I would prefer one year with an option to renew. Is the owner open to that? I would also need to put up a sign facing the street. Is that permitted?

I am available for a viewing on July 10 or July 11 at any time after 2:00 P.M.

Kind regards,

Soraya Kimathi

────────────────────

[E-mail]
To: Soraya Kimathi
From: Viktor Rasmussen
Date: July 8
Subject: Re: Space for a cooking school

Dear Ms. Kimathi,

Thank you for your interest. I have spoken with the owner, Mr. Trevelyan. He is not willing to shorten the lease, but he has offered to reduce the rent by $200 per month for the first six months to help you get established. He is glad to have a sign installed, provided that its design is submitted to him for approval beforehand.

I can show you the property on July 11 at 3:30 P.M. Please note that the terrace is being retiled until the end of the month, so you will not be able to walk on it, but you will be able to see it from the dining room.

Should you decide to proceed, a signed lease and the deposit would be needed by August 1 for an occupancy date of September 1.

Regards,

Viktor Rasmussen
Leasing Agent', 'NONE', '["Their tenants pay their own utility bills.", "They include on-site parking.", "They are located on the same street.", "They can be leased for one year."]', 'A', 1, '[사실 확인] 정답 (A). 공통 조건의 "Tenants are responsible for electricity and water"가 근거다. electricity and water → utility bills. 오답: (B) 주차장은 31 Saltmarsh Road에만 있다. (C) Fenwick Street, Quarrymill Lane, Saltmarsh Road로 서로 다르다. (D) "a minimum of two years"다.
포인트: 사실 확인 — 웹 페이지 마지막 문단(공통 조건)', 196),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'Which property does Ms. Kimathi most likely want to view?', 'Questions 196-200 refer to the following Web page and e-mails.

[Web page]
Thornwick Commercial Realty
Retail and Office Space for Lease in Sablefield

Unit 2, 14 Fenwick Street — 60 square meters — $1,900 per month
First-floor storefront with a large display window. Suited to retail. No kitchen facilities.

Unit 5, 14 Fenwick Street — 85 square meters — $2,300 per month
Second-floor office suite with two private rooms and a reception area. Elevator access.

9 Quarrymill Lane — 110 square meters — $3,100 per month
Former café with a fully equipped commercial kitchen and seating for 40. Outdoor terrace.

31 Saltmarsh Road — 140 square meters — $2,800 per month
Spacious open-plan studio in a converted warehouse. Loading door at the rear; on-site parking for six vehicles.

All leases run for a minimum of two years. A security deposit equal to two months'' rent is due at signing. Tenants are responsible for electricity and water; building insurance and exterior maintenance are covered by the owner. To arrange a viewing, contact Viktor Rasmussen at vrasmussen@thornwickrealty.com.

────────────────────

[E-mail]
To: Viktor Rasmussen
From: Soraya Kimathi
Date: July 7
Subject: Space for a cooking school

Dear Mr. Rasmussen,

I am the owner of Sage & Ember, a catering company based in Ravensholt, and I am planning to open a small cooking school in Sablefield this fall. I saw your listings and would like to view the property that already has a professional kitchen, since installing one elsewhere would be beyond my budget.

The lease term is my main concern. Your listing mentions a minimum of two years, but because this is a new venture, I would prefer one year with an option to renew. Is the owner open to that? I would also need to put up a sign facing the street. Is that permitted?

I am available for a viewing on July 10 or July 11 at any time after 2:00 P.M.

Kind regards,

Soraya Kimathi

────────────────────

[E-mail]
To: Soraya Kimathi
From: Viktor Rasmussen
Date: July 8
Subject: Re: Space for a cooking school

Dear Ms. Kimathi,

Thank you for your interest. I have spoken with the owner, Mr. Trevelyan. He is not willing to shorten the lease, but he has offered to reduce the rent by $200 per month for the first six months to help you get established. He is glad to have a sign installed, provided that its design is submitted to him for approval beforehand.

I can show you the property on July 11 at 3:30 P.M. Please note that the terrace is being retiled until the end of the month, so you will not be able to walk on it, but you will be able to see it from the dining room.

Should you decide to proceed, a signed lease and the deposit would be needed by August 1 for an occupancy date of September 1.

Regards,

Viktor Rasmussen
Leasing Agent', 'NONE', '["Unit 2, 14 Fenwick Street", "Unit 5, 14 Fenwick Street", "9 Quarrymill Lane", "31 Saltmarsh Road"]', 'C', 1, '[연계(문서1+2)] 정답 (C). 첫 이메일의 "the property that already has a professional kitchen"과 웹 페이지의 "9 Quarrymill Lane ... a fully equipped commercial kitchen"을 연결한다. 오답: (A) "No kitchen facilities"라고 적혀 있다. (B) 사무실용 공간이다. (D) 창고를 개조한 스튜디오로 주방 언급이 없다.
포인트: 연계 문항 — 웹 페이지 매물 목록 + 첫 이메일의 조건', 197),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'In the first e-mail, the word "term" in paragraph 2, line 1, is closest in meaning to', 'Questions 196-200 refer to the following Web page and e-mails.

[Web page]
Thornwick Commercial Realty
Retail and Office Space for Lease in Sablefield

Unit 2, 14 Fenwick Street — 60 square meters — $1,900 per month
First-floor storefront with a large display window. Suited to retail. No kitchen facilities.

Unit 5, 14 Fenwick Street — 85 square meters — $2,300 per month
Second-floor office suite with two private rooms and a reception area. Elevator access.

9 Quarrymill Lane — 110 square meters — $3,100 per month
Former café with a fully equipped commercial kitchen and seating for 40. Outdoor terrace.

31 Saltmarsh Road — 140 square meters — $2,800 per month
Spacious open-plan studio in a converted warehouse. Loading door at the rear; on-site parking for six vehicles.

All leases run for a minimum of two years. A security deposit equal to two months'' rent is due at signing. Tenants are responsible for electricity and water; building insurance and exterior maintenance are covered by the owner. To arrange a viewing, contact Viktor Rasmussen at vrasmussen@thornwickrealty.com.

────────────────────

[E-mail]
To: Viktor Rasmussen
From: Soraya Kimathi
Date: July 7
Subject: Space for a cooking school

Dear Mr. Rasmussen,

I am the owner of Sage & Ember, a catering company based in Ravensholt, and I am planning to open a small cooking school in Sablefield this fall. I saw your listings and would like to view the property that already has a professional kitchen, since installing one elsewhere would be beyond my budget.

The lease term is my main concern. Your listing mentions a minimum of two years, but because this is a new venture, I would prefer one year with an option to renew. Is the owner open to that? I would also need to put up a sign facing the street. Is that permitted?

I am available for a viewing on July 10 or July 11 at any time after 2:00 P.M.

Kind regards,

Soraya Kimathi

────────────────────

[E-mail]
To: Soraya Kimathi
From: Viktor Rasmussen
Date: July 8
Subject: Re: Space for a cooking school

Dear Ms. Kimathi,

Thank you for your interest. I have spoken with the owner, Mr. Trevelyan. He is not willing to shorten the lease, but he has offered to reduce the rent by $200 per month for the first six months to help you get established. He is glad to have a sign installed, provided that its design is submitted to him for approval beforehand.

I can show you the property on July 11 at 3:30 P.M. Please note that the terrace is being retiled until the end of the month, so you will not be able to walk on it, but you will be able to see it from the dining room.

Should you decide to proceed, a signed lease and the deposit would be needed by August 1 for an occupancy date of September 1.

Regards,

Viktor Rasmussen
Leasing Agent', 'NONE', '["duration", "condition", "expression", "semester"]', 'A', 1, '[동의어] 정답 (A). "The lease term is my main concern" 뒤에 2년과 1년을 비교하므로 term은 계약 ''기간''을 뜻하고 duration이 가장 가깝다. 오답: (B) condition은 terms(조건)의 뜻이다. (C) expression은 ''용어''라는 뜻의 term이다. (D) semester는 ''학기''라는 뜻의 term이라 임대 계약 문맥과 맞지 않는다.
포인트: 동의어 — 첫 이메일 둘째 문단(lease term = 계약 기간)', 198),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'What does Mr. Rasmussen indicate about the owner of the property?', 'Questions 196-200 refer to the following Web page and e-mails.

[Web page]
Thornwick Commercial Realty
Retail and Office Space for Lease in Sablefield

Unit 2, 14 Fenwick Street — 60 square meters — $1,900 per month
First-floor storefront with a large display window. Suited to retail. No kitchen facilities.

Unit 5, 14 Fenwick Street — 85 square meters — $2,300 per month
Second-floor office suite with two private rooms and a reception area. Elevator access.

9 Quarrymill Lane — 110 square meters — $3,100 per month
Former café with a fully equipped commercial kitchen and seating for 40. Outdoor terrace.

31 Saltmarsh Road — 140 square meters — $2,800 per month
Spacious open-plan studio in a converted warehouse. Loading door at the rear; on-site parking for six vehicles.

All leases run for a minimum of two years. A security deposit equal to two months'' rent is due at signing. Tenants are responsible for electricity and water; building insurance and exterior maintenance are covered by the owner. To arrange a viewing, contact Viktor Rasmussen at vrasmussen@thornwickrealty.com.

────────────────────

[E-mail]
To: Viktor Rasmussen
From: Soraya Kimathi
Date: July 7
Subject: Space for a cooking school

Dear Mr. Rasmussen,

I am the owner of Sage & Ember, a catering company based in Ravensholt, and I am planning to open a small cooking school in Sablefield this fall. I saw your listings and would like to view the property that already has a professional kitchen, since installing one elsewhere would be beyond my budget.

The lease term is my main concern. Your listing mentions a minimum of two years, but because this is a new venture, I would prefer one year with an option to renew. Is the owner open to that? I would also need to put up a sign facing the street. Is that permitted?

I am available for a viewing on July 10 or July 11 at any time after 2:00 P.M.

Kind regards,

Soraya Kimathi

────────────────────

[E-mail]
To: Soraya Kimathi
From: Viktor Rasmussen
Date: July 8
Subject: Re: Space for a cooking school

Dear Ms. Kimathi,

Thank you for your interest. I have spoken with the owner, Mr. Trevelyan. He is not willing to shorten the lease, but he has offered to reduce the rent by $200 per month for the first six months to help you get established. He is glad to have a sign installed, provided that its design is submitted to him for approval beforehand.

I can show you the property on July 11 at 3:30 P.M. Please note that the terrace is being retiled until the end of the month, so you will not be able to walk on it, but you will be able to see it from the dining room.

Should you decide to proceed, a signed lease and the deposit would be needed by August 1 for an occupancy date of September 1.

Regards,

Viktor Rasmussen
Leasing Agent', 'NONE', '["He has agreed to a one-year lease.", "He will lower the rent permanently.", "He must approve the design of a sign.", "He plans to attend the viewing."]', 'C', 1, '[세부 사항] 정답 (C). "He is glad to have a sign installed, provided that its design is submitted to him for approval beforehand"가 근거다. 오답: (A) "He is not willing to shorten the lease". (B) 임대료 인하는 처음 6개월뿐이다. (D) 매물을 보여 주는 사람은 Rasmussen이고 주인의 참석 언급은 없다.
포인트: 세부 사항 — 둘째 이메일 첫 문단(approval = 승인)', 199),
(@toeic_rc01_exam_id, 'READING', 'TOEIC_P7', 'How much rent would Ms. Kimathi most likely pay per month during the first six months of her lease?', 'Questions 196-200 refer to the following Web page and e-mails.

[Web page]
Thornwick Commercial Realty
Retail and Office Space for Lease in Sablefield

Unit 2, 14 Fenwick Street — 60 square meters — $1,900 per month
First-floor storefront with a large display window. Suited to retail. No kitchen facilities.

Unit 5, 14 Fenwick Street — 85 square meters — $2,300 per month
Second-floor office suite with two private rooms and a reception area. Elevator access.

9 Quarrymill Lane — 110 square meters — $3,100 per month
Former café with a fully equipped commercial kitchen and seating for 40. Outdoor terrace.

31 Saltmarsh Road — 140 square meters — $2,800 per month
Spacious open-plan studio in a converted warehouse. Loading door at the rear; on-site parking for six vehicles.

All leases run for a minimum of two years. A security deposit equal to two months'' rent is due at signing. Tenants are responsible for electricity and water; building insurance and exterior maintenance are covered by the owner. To arrange a viewing, contact Viktor Rasmussen at vrasmussen@thornwickrealty.com.

────────────────────

[E-mail]
To: Viktor Rasmussen
From: Soraya Kimathi
Date: July 7
Subject: Space for a cooking school

Dear Mr. Rasmussen,

I am the owner of Sage & Ember, a catering company based in Ravensholt, and I am planning to open a small cooking school in Sablefield this fall. I saw your listings and would like to view the property that already has a professional kitchen, since installing one elsewhere would be beyond my budget.

The lease term is my main concern. Your listing mentions a minimum of two years, but because this is a new venture, I would prefer one year with an option to renew. Is the owner open to that? I would also need to put up a sign facing the street. Is that permitted?

I am available for a viewing on July 10 or July 11 at any time after 2:00 P.M.

Kind regards,

Soraya Kimathi

────────────────────

[E-mail]
To: Soraya Kimathi
From: Viktor Rasmussen
Date: July 8
Subject: Re: Space for a cooking school

Dear Ms. Kimathi,

Thank you for your interest. I have spoken with the owner, Mr. Trevelyan. He is not willing to shorten the lease, but he has offered to reduce the rent by $200 per month for the first six months to help you get established. He is glad to have a sign installed, provided that its design is submitted to him for approval beforehand.

I can show you the property on July 11 at 3:30 P.M. Please note that the terrace is being retiled until the end of the month, so you will not be able to walk on it, but you will be able to see it from the dining room.

Should you decide to proceed, a signed lease and the deposit would be needed by August 1 for an occupancy date of September 1.

Regards,

Viktor Rasmussen
Leasing Agent', 'NONE', '["$2,100", "$2,600", "$2,800", "$2,900"]', 'D', 1, '[연계(문서1+2+3)] 정답 (D). 첫 이메일에서 원하는 매물은 주방이 있는 9 Quarrymill Lane이고 웹 페이지에서 그 임대료는 월 $3,100이다. 답장의 "reduce the rent by $200 per month for the first six months"를 적용하면 $2,900이다. 오답: (A) Unit 5에서 $200을 뺀 값. (B) 31 Saltmarsh Road에서 $200을 뺀 값. (C) 31 Saltmarsh Road의 정가.
포인트: 연계 문항 — 웹 페이지 임대료 + 첫 이메일의 매물 조건 + 답장의 인하액', 200);
