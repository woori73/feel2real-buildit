-- TestPrep TOEIC 연습 세트 TP-TOEIC-FULL-01 — TOEIC Practice Test 01 — Listening & Reading (기출 유사 1회)
-- 문항 194개, F2R 자체 제작. ETS 공식 문항·지문과 후기 속 복원 문항은 쓰지 않았다(출제 포인트·유형·길이·상황만 참고).
-- 스키마는 바꾸지 않는다: 기존 tp_exams / tp_questions 에 kind='TOEIC' 행만 추가한다.
-- section: LISTENING / READING, task_type: TOEIC_P2 ~ TOEIC_P7, order_no = 실제 TOEIC 문항 번호(7~200).
-- 듣기(TOEIC_P2~P4)는 audio_mode='TTS' — 화면이 body_text(대본)를 브라우저 음성으로 읽는다. Part 1(사진)은 없다.
-- 문항 원본·생성기: naesin-workspace/testprep/toeic (tools/build_sql.py). 설계: docs/design/testprep-toeic.md
-- exam_id는 AUTO_INCREMENT에 의존하지 않고 exam_code로 회수한다.

INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES
('TP-TOEIC-FULL-01', 'TOEIC', 'TOEIC Practice Test 01 — Listening & Reading (기출 유사 1회)', 'Listening Part 2–4 (94 questions) · Reading Part 5–7 (100 questions) · 120 minutes', 'A full-length TOEIC Listening & Reading practice test written by F2R: Part 2 (25), Part 3 (39), Part 4 (30), Part 5 (30), Part 6 (16) and Part 7 (54). Part 1 (photographs) is not included. Listening scripts are read aloud by the browser in practice mode. Scores are F2R internal estimates, not official ETS TOEIC scores. 듣기는 브라우저 음성으로 읽어 주며 Part 1(사진 묘사)은 없습니다.', NULL, 7200, 'PUBLISHED', 'system');

SET @toeic_full01_exam_id = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-TOEIC-FULL-01');

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Where should I leave the visitor badges?
A. At the front desk.
B. They left around noon, I think.
C. Yes, I visited last week.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-의문사(Where)] 정답 (A). Where로 둘 장소를 물었으므로 ''At the front desk''라는 장소 응답이 맞다. 오답: (B) leave-left 소리를 이용한 시간 응답으로 주어 They가 가리킬 대상도 없다. (C) 의문사 의문문에 Yes로 답할 수 없고 visitor-visited 연상 오답이다.
포인트: Where 의문문 — 장소 응답', 7),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'When does the paper shipment arrive?
A. It''s coming by truck, I believe.
B. At the rear loading dock.
C. Not until Thursday.', 'TTS', '["", "", ""]', 'C', 1, '[Part2-의문사(When)] 정답 (C). When으로 도착 시점을 물었으므로 ''Not until Thursday(목요일은 되어야)''가 맞다. 오답: (A) 운송 수단(How)에 대한 응답이다. (B) 장소(Where)에 대한 응답이다.
포인트: When 의문문 — 시점 응답(Not until ~)', 8),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Who''s leading the new-hire orientation?
A. In the training room upstairs.
B. Yes, they were hired in March.
C. Ms. Dlamini from human resources.', 'TTS', '["", "", ""]', 'C', 1, '[Part2-의문사(Who)] 정답 (C). Who로 진행자를 물었으므로 사람 이름과 부서를 말한 응답이 맞다. 오답: (A) 장소(Where) 응답이다. (B) 의문사 의문문에 Yes로 답할 수 없고 hire-hired 반복 오답이다.
포인트: Who 의문문 — 이름·부서 응답', 9),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Have you booked a room for the meeting?
A. The book is on my desk.
B. Yes, the one on the third floor.
C. He''s been a client of ours for years.', 'TTS', '["", "", ""]', 'B', 1, '[Part2-일반 의문문] 정답 (B). 회의실을 예약했느냐는 질문에 Yes로 답하고 어느 방인지 덧붙였다. 오답: (A) booked(예약하다)와 book(책)의 소리를 이용한 오답이다. (C) meeting에서 연상되는 client를 썼을 뿐 예약 여부와 관계가 없고 He가 가리킬 대상도 없다.
포인트: 일반 의문문 — Yes + 덧붙이는 정보', 10),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Why is the parking garage closed today?
A. It''s about ten dollars a day.
B. Close to the main entrance.
C. Didn''t you see the e-mail?', 'TTS', '["", "", ""]', 'C', 1, '[Part2-의문사(Why)·간접 응답] 정답 (C). 이유를 직접 말하지 않고 ''이메일 못 봤어요?''라고 되물어 이유가 그 이메일에 나와 있음을 알리는 간접 응답이다. 오답: (A) 주차 요금(How much) 응답이다. (B) closed-close 소리를 이용한 위치 응답이다.
포인트: 간접 응답 — 되묻기', 11),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Could you send me the revised budget this afternoon?
A. At the afternoon session.
B. Sure, I''m just finishing it now.
C. He advised me to wait.', 'TTS', '["", "", ""]', 'B', 1, '[Part2-제안·요청] 정답 (B). 수정 예산안을 보내 달라는 요청에 Sure로 수락하고 지금 마무리 중이라고 답했다. 오답: (A) afternoon을 반복한 응답으로 요청에 대한 답이 아니다. (C) revised-advised 소리를 이용한 오답이고 He가 가리킬 대상이 없다.
포인트: 요청 — 수락(Sure)', 12),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'How do I reset my system password?
A. Ask Yusuf in tech support.
B. I set it on your desk earlier.
C. No, I passed by it this morning.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-의문사(How)·간접 응답] 정답 (A). 방법을 직접 설명하는 대신 기술 지원팀의 Yusuf에게 물어보라고 알려 주는 간접 응답이다. 오답: (B) reset-set 소리를 이용한 오답으로 방법과 무관하다. (C) 의문사 의문문에 No로 답할 수 없고 password-passed 소리 오답이다.
포인트: 간접 응답 — 아는 사람을 알려 줌', 13),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'The second-floor printer has been repaired, hasn''t it?
A. Two pairs, please.
B. The technician''s still working on it.
C. I''ll print twenty copies for you.', 'TTS', '["", "", ""]', 'B', 1, '[Part2-부가 의문문·간접 응답] 정답 (B). 수리가 끝났느냐는 확인에 ''기사가 아직 작업 중''이라고 답해 아직 안 끝났음을 알리는 간접 응답이다. 오답: (A) repaired-pairs 소리를 이용한 오답이다. (C) printer-print 연상 오답으로 수리 여부에 대한 답이 아니다.
포인트: 부가 의문문 — 간접 부정', 14),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'What time does the Quince Street pharmacy open?
A. Yes, it''s open to the public.
B. On a farm outside the city.
C. At eight thirty.', 'TTS', '["", "", ""]', 'C', 1, '[Part2-의문사(What time)] 정답 (C). What time으로 여는 시각을 물었으므로 ''At eight thirty''가 맞다. 오답: (A) 의문사 의문문에 Yes로 답할 수 없고 open을 반복했다. (B) pharmacy-farm 소리를 이용한 오답이다.
포인트: What time 의문문 — 시각 응답', 15),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Should we fly or take the train?
A. Let''s take the train.
B. Yes, I''d like that very much.
C. The training starts at nine o''clock.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-선택 의문문] 정답 (A). 비행기와 기차 중 기차를 골랐다. 오답: (B) 둘 중 하나를 고르라는 질문에 Yes로 답하면 무엇을 골랐는지 알 수 없다. (C) train-training 소리를 이용한 오답이다.
포인트: 선택 의문문 — 둘 중 하나 고르기', 16),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'I can''t find the catering invoice.
A. I''d like a table for twelve.
B. Yes, the food was excellent.
C. Have you checked the shared folder?', 'TTS', '["", "", ""]', 'C', 1, '[Part2-평서문·간접 응답] 정답 (C). 송장을 못 찾겠다는 말에 ''공유 폴더는 확인해 봤어요?''라고 찾을 곳을 제안하는 간접 응답이다. 오답: (A) catering에서 연상되는 식당 예약 표현이다. (B) catering-food 연상 오답으로 송장을 찾는 문제와 관계가 없다.
포인트: 평서문 — 해결책을 되묻는 간접 응답', 17),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Where''s Mr. Lachance''s retirement dinner being held?
A. He retired after thirty years here.
B. At seven on Friday evening.
C. That hasn''t been decided yet.', 'TTS', '["", "", ""]', 'C', 1, '[Part2-의문사(Where)·간접 응답] 정답 (C). 장소를 묻는 말에 ''그건 아직 정해지지 않았다''고 답하는 간접 응답이다. 오답: (A) retirement-retired 반복 오답으로 장소가 아니다. (B) 시간(When)에 대한 응답이다.
포인트: 간접 응답 — 아직 정해지지 않음', 18),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Aren''t you supposed to be at the dentist?
A. I just bought a new toothbrush.
B. That got moved to Friday.
C. I suppose it''s on the left side.', 'TTS', '["", "", ""]', 'B', 1, '[Part2-부정 의문문·간접 응답] 정답 (B). 지금 치과에 가 있어야 하지 않느냐는 말에 ''그건 금요일로 옮겨졌다''고 답해 오늘은 가지 않음을 알리는 간접 응답이다. 오답: (A) dentist에서 연상되는 toothbrush를 썼을 뿐이다. (C) supposed-suppose 소리를 이용한 위치 응답이다.
포인트: 부정 의문문 — 일정 변경을 알리는 간접 응답', 19),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'How long will the elevator be out of service?
A. Until the end of the week, I heard.
B. It only goes up to the fourth floor.
C. The service there was very friendly.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-의문사(How long)] 정답 (A). How long으로 기간을 물었으므로 ''이번 주가 끝날 때까지''라는 기간 응답이 맞다. 오답: (B) elevator에서 연상되는 floor를 쓴 응답으로 기간이 아니다. (C) service를 다른 뜻(접객)으로 반복한 오답이다.
포인트: How long 의문문 — 기간 응답(Until ~)', 20),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Why don''t we hold the workshop upstairs?
A. Because it started late.
B. I think the gift shop is downstairs.
C. There''s no projector up there.', 'TTS', '["", "", ""]', 'C', 1, '[Part2-제안·요청·간접 응답] 정답 (C). 위층에서 하자는 제안에 ''거기에는 프로젝터가 없다''며 문제점을 들어 완곡하게 반대하는 간접 응답이다. 오답: (A) Why don''t we는 제안인데 이유를 묻는 Why로 착각하게 만든 오답이다. (B) workshop-shop 소리와 upstairs-downstairs 연상을 이용한 오답이다.
포인트: 제안(Why don''t we) — 완곡한 반대', 21),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Did the supplier confirm the delivery date?
A. I''m still waiting to hear back.
B. Two boxes of office supplies.
C. The expiration date is on the label.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-일반 의문문·간접 응답] 정답 (A). 납품일을 확인받았느냐는 질문에 ''아직 답을 기다리는 중''이라고 답해 아직 확인되지 않았음을 알리는 간접 응답이다. 오답: (B) supplier-supplies 소리를 이용한 오답이다. (C) date를 반복했지만 유통 기한 이야기라 질문과 무관하다.
포인트: 일반 의문문 — 간접 부정', 22),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Who''s picking up Ms. Ferrante from the airport?
A. It''s at terminal two.
B. I picked a window seat this time.
C. Her flight''s been canceled.', 'TTS', '["", "", ""]', 'C', 1, '[Part2-의문사(Who)·간접 응답] 정답 (C). 누가 마중 나가느냐는 질문에 ''항공편이 취소됐다''고 답해 마중이 필요 없음을 알리는 간접 응답이다. 오답: (A) 장소(Where) 응답이다. (B) picking-picked 소리와 airport-window seat 연상을 이용한 오답이다.
포인트: 간접 응답 — 질문의 전제가 바뀜', 23),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'This new accounting software is hard to use.
A. There''s a training session on Tuesday.
B. Yes, he''s a certified accountant.
C. The hardware store closes at six.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-평서문·간접 응답] 정답 (A). 새 프로그램이 쓰기 어렵다는 말에 ''화요일에 교육이 있다''며 해결 방법을 알려 주는 간접 응답이다. 오답: (B) accounting-accountant 소리 오답이고 he가 가리킬 대상이 없다. (C) hard-hardware, software-hardware 소리·연상 오답이다.
포인트: 평서문 — 해결 방법을 알려 주는 간접 응답', 24),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'When will the job candidates be interviewed?
A. In the second-floor meeting room.
B. You''d have to ask Mr. Oduya.
C. Yes, I saw the view from there.', 'TTS', '["", "", ""]', 'B', 1, '[Part2-의문사(When)·간접 응답] 정답 (B). 면접 시기를 묻는 말에 ''Oduya 씨에게 물어봐야 한다''며 자신은 모르고 아는 사람을 알려 주는 간접 응답이다. 오답: (A) 장소(Where) 응답이다. (C) 의문사 의문문에 Yes로 답할 수 없고 interviewed-view 소리 오답이다.
포인트: 간접 응답 — 아는 사람을 알려 줌', 25),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Did the customer mention the damaged package?
A. The packing list is enclosed.
B. She''d like a replacement.
C. The manager''s office is upstairs.', 'TTS', '["", "", ""]', 'B', 1, '[Part2-일반 의문문] 정답 (B). 고객이 파손된 소포 이야기를 했느냐는 질문에 ''교체품을 원한다''며 고객이 한 말을 전했다. 오답: (A) package-packing 소리를 이용한 오답이다. (C) 고객 불만에서 연상되는 manager를 썼지만 위치 안내라 질문과 무관하다.
포인트: 일반 의문문 — Yes를 생략하고 내용으로 답함', 26);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Is the cafeteria still serving lunch?
A. Only until one thirty.
B. They launched it last month.
C. Next to the service entrance.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-일반 의문문] 정답 (A). 아직 점심을 파느냐는 질문에 ''1시 30분까지만''이라고 답해 제공 시간을 알려 준다. 오답: (B) lunch-launched 소리를 이용한 오답이다. (C) serving-service 소리를 이용한 위치 응답이다.
포인트: 일반 의문문 — 조건(Only until ~)으로 답함', 27),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Should I send the contract by e-mail or courier?
A. Yes, he signed it yesterday.
B. It''s a three-year agreement.
C. Either is fine with me.', 'TTS', '["", "", ""]', 'C', 1, '[Part2-선택 의문문] 정답 (C). 이메일과 택배 중 어느 쪽이냐는 선택 의문문에 ''어느 쪽이든 괜찮다''고 답했다. 오답: (A) 선택 의문문에 Yes로 답할 수 없고 he가 가리킬 대상이 없다. (B) contract-agreement 연상 오답으로 보내는 방법에 대한 답이 아니다.
포인트: 선택 의문문 — Either 응답', 28),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Would you like me to reserve a table?
A. It''s a round wooden table.
B. That would be great, thanks.
C. They served us quickly last time.', 'TTS', '["", "", ""]', 'B', 1, '[Part2-제안·요청] 정답 (B). 식당 자리를 예약해 주겠다는 제안에 ''그래 주면 좋겠다''고 수락했다. 오답: (A) table을 가구의 뜻으로 반복한 오답이다. (C) reserve-served 소리를 이용한 과거 경험 진술로 제안에 대한 답이 아니다.
포인트: 제안(Would you like me to) — 수락', 29),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Our flight''s been delayed by two hours.
A. I''d prefer a window seat, please.
B. Then we have time to get something to eat.
C. Yes, I''ve lived there for two years.', 'TTS', '["", "", ""]', 'B', 1, '[Part2-평서문·간접 응답] 정답 (B). 비행기가 두 시간 지연됐다는 말에 ''그럼 뭘 좀 먹을 시간이 있겠다''고 그 결과를 받아 말하는 간접 응답이다. 오답: (A) flight에서 연상되는 좌석 요청이다. (C) two hours-two years 소리를 이용한 오답으로 지연과 무관하다.
포인트: 평서문 — 결과를 받아 말하는 간접 응답', 30),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'How much is it to rent the community hall?
A. The rates are on the city''s website.
B. For about three hours.
C. On the corner, next to the bank.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-의문사(How much)·간접 응답] 정답 (A). 대관료를 묻는 말에 금액 대신 ''요금은 시 웹사이트에 나와 있다''고 확인할 곳을 알려 주는 간접 응답이다. 오답: (B) 기간(How long) 응답이다. (C) 위치(Where) 응답이다.
포인트: 간접 응답 — 확인할 곳을 알려 줌', 31),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Where does the conversation most likely take place?', 'Questions 32 through 34 refer to the following conversation.

Woman: Hi, I''m checking in. The name''s Defne Yilmaz, for three nights.
Man: Welcome to the Alderbrook Inn, Ms. Yilmaz. Let''s see. Oh, I''m sorry. Your room isn''t ready yet. The air conditioner''s being repaired, and the technician needs about another hour.
Woman: Oh, no. I''ve got a video call with a client at three, and I wanted to set up my laptop first.
Man: I understand. You''re welcome to use our business center on the second floor. It''s quiet, and the Wi-Fi''s fast.
Woman: That''d work. What about my bags?
Man: We can keep them here at the desk. And I''ll call your cell phone as soon as the room''s ready.
Woman: Great, thanks.', 'TTS', '["At an airport", "At an electronics store", "At a repair shop", "At a hotel"]', 'D', 1, '[Part3-장소] 정답 (D). ''I''m checking in'', ''Your room isn''t ready yet''에서 호텔 접수대임을 알 수 있다. 오답: (A) 공항을 나타내는 말이 없다. (B) laptop이 나오지만 매장이 아니다. (C) 에어컨 수리는 객실에서 이루어지는 일일 뿐 대화 장소가 수리점은 아니다.
포인트: 대화 장소 — 첫 두 말', 32),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Why is the room unavailable?', 'Questions 32 through 34 refer to the following conversation.

Woman: Hi, I''m checking in. The name''s Defne Yilmaz, for three nights.
Man: Welcome to the Alderbrook Inn, Ms. Yilmaz. Let''s see. Oh, I''m sorry. Your room isn''t ready yet. The air conditioner''s being repaired, and the technician needs about another hour.
Woman: Oh, no. I''ve got a video call with a client at three, and I wanted to set up my laptop first.
Man: I understand. You''re welcome to use our business center on the second floor. It''s quiet, and the Wi-Fi''s fast.
Woman: That''d work. What about my bags?
Man: We can keep them here at the desk. And I''ll call your cell phone as soon as the room''s ready.
Woman: Great, thanks.', 'TTS', '["Some equipment is being fixed.", "It has not been cleaned.", "Another guest is staying late.", "The reservation was lost."]', 'A', 1, '[Part3-세부 사항(이유)] 정답 (A). ''The air conditioner''s being repaired''를 Some equipment is being fixed로 바꿔 말했다. 오답: (B) 청소 이야기는 없다. (C) 다른 투숙객 언급이 없다. (D) 예약은 정상적으로 확인되었다.
포인트: 세부 사항(이유) — 바꿔 말하기(air conditioner → equipment)', 33),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man suggest the woman do?', 'Questions 32 through 34 refer to the following conversation.

Woman: Hi, I''m checking in. The name''s Defne Yilmaz, for three nights.
Man: Welcome to the Alderbrook Inn, Ms. Yilmaz. Let''s see. Oh, I''m sorry. Your room isn''t ready yet. The air conditioner''s being repaired, and the technician needs about another hour.
Woman: Oh, no. I''ve got a video call with a client at three, and I wanted to set up my laptop first.
Man: I understand. You''re welcome to use our business center on the second floor. It''s quiet, and the Wi-Fi''s fast.
Woman: That''d work. What about my bags?
Man: We can keep them here at the desk. And I''ll call your cell phone as soon as the room''s ready.
Woman: Great, thanks.', 'TTS', '["Change to a larger room", "Come back the next day", "Use a business center", "Call a technician"]', 'C', 1, '[Part3-제안] 정답 (C). ''You''re welcome to use our business center on the second floor''에서 비즈니스 센터 이용을 권한다. 오답: (A) 방을 바꾸자는 말은 없다. (B) 한 시간 뒤면 준비되므로 다음 날 올 필요가 없다. (D) 기사는 이미 수리 중이며 여자가 부를 일이 아니다.
포인트: 제안 — You''re welcome to …', 34),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What are the speakers mainly discussing?', 'Questions 35 through 37 refer to the following conversation.

Man: Hi, Yuki. I just saw that the budget review''s on the calendar for Wednesday at two. I''m afraid I''ll be out at the Pinehollow warehouse all day.
Woman: Oh, I didn''t know that. We really need you there. You''re presenting the regional figures. Could we move it to Thursday morning?
Man: Thursday works. But isn''t the big conference room booked for the training workshop then?
Woman: Oh, you''re right. Hmm. Well, there''s the room on the fifth floor. It''s smaller, but it holds twelve people.
Man: That''ll do. Tell you what, I''ll send everyone an updated invitation right now, so they can adjust their schedules.
Woman: Perfect. Thanks, Emeka.', 'TTS', '["Hiring a regional manager", "Touring a warehouse", "Rescheduling a meeting", "Planning a training workshop"]', 'C', 1, '[Part3-주제] 정답 (C). 수요일 회의에 남자가 참석할 수 없어 ''Could we move it to Thursday morning?''이라며 회의 일정을 옮기는 대화다. 오답: (A) regional figures가 나올 뿐 채용 이야기는 없다. (B) 창고 방문은 불참 이유일 뿐 주제가 아니다. (D) 교육 워크숍은 큰 회의실을 쓰는 다른 행사로 언급될 뿐이다.
포인트: 주제 — 대화 전체(move it to → reschedule)', 35),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Why is the man unable to attend on Wednesday?', 'Questions 35 through 37 refer to the following conversation.

Man: Hi, Yuki. I just saw that the budget review''s on the calendar for Wednesday at two. I''m afraid I''ll be out at the Pinehollow warehouse all day.
Woman: Oh, I didn''t know that. We really need you there. You''re presenting the regional figures. Could we move it to Thursday morning?
Man: Thursday works. But isn''t the big conference room booked for the training workshop then?
Woman: Oh, you''re right. Hmm. Well, there''s the room on the fifth floor. It''s smaller, but it holds twelve people.
Man: That''ll do. Tell you what, I''ll send everyone an updated invitation right now, so they can adjust their schedules.
Woman: Perfect. Thanks, Emeka.', 'TTS', '["He is leading a workshop.", "He will be at another site.", "He has a medical appointment.", "He is meeting a client."]', 'B', 1, '[Part3-세부 사항(이유)] 정답 (B). ''I''ll be out at the Pinehollow warehouse all day''를 He will be at another site로 바꿔 말했다. 오답: (A) 교육 워크숍은 목요일 오전에 큰 회의실을 쓰는 다른 행사다. (C) 병원 예약 언급이 없다. (D) 고객을 만난다는 말이 없다.
포인트: 세부 사항(이유) — 바꿔 말하기(warehouse → another site)', 36),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man offer to do?', 'Questions 35 through 37 refer to the following conversation.

Man: Hi, Yuki. I just saw that the budget review''s on the calendar for Wednesday at two. I''m afraid I''ll be out at the Pinehollow warehouse all day.
Woman: Oh, I didn''t know that. We really need you there. You''re presenting the regional figures. Could we move it to Thursday morning?
Man: Thursday works. But isn''t the big conference room booked for the training workshop then?
Woman: Oh, you''re right. Hmm. Well, there''s the room on the fifth floor. It''s smaller, but it holds twelve people.
Man: That''ll do. Tell you what, I''ll send everyone an updated invitation right now, so they can adjust their schedules.
Woman: Perfect. Thanks, Emeka.', 'TTS', '["Send a revised invitation", "Reserve a larger conference room", "Postpone a workshop", "Print some figures"]', 'A', 1, '[Part3-제안(제공)] 정답 (A). ''I''ll send everyone an updated invitation right now''에서 updated를 revised로 바꿔 말했다. 오답: (B) 오히려 더 작은 5층 방을 쓰기로 했다. (C) 워크숍은 그대로 두고 회의를 옮긴다. (D) 수치 자료를 인쇄한다는 말은 없다.
포인트: 제공(offer to do) — 마지막 말(updated → revised)', 37),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Why is the man calling?', 'Questions 38 through 40 refer to the following conversation.

Woman: Thanks for calling Brennick Office Furniture. How can I help?
Man: Hi. I ordered six desk chairs for my accounting firm last week. They were supposed to come yesterday, but nothing''s shown up.
Woman: I''m sorry about that. Can I get your order number?
Man: Sure. It''s four four seven one.
Woman: Thanks. OK, I see what happened. The gray model you picked ran out at our warehouse, so the order''s on hold. We do have the same chair in black, though. Those could go out today.
Man: Black''s fine. But since it''s late, could you waive the delivery charge?
Woman: Of course. I''ll take that off and e-mail you a new confirmation.', 'TTS', '["To return some damaged chairs", "To ask about a late delivery", "To update a billing address", "To request a catalog"]', 'B', 1, '[Part3-목적] 정답 (B). ''They were supposed to come yesterday, but nothing''s shown up''에서 배송 지연을 문의하는 전화임을 알 수 있다. 오답: (A) 물건이 아직 오지 않았으므로 파손·반품이 아니다. (C) 청구지 주소 이야기는 없다. (D) 카탈로그 요청은 없다.
포인트: 전화 목적 — 첫 말', 38),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What caused the problem?', 'Questions 38 through 40 refer to the following conversation.

Woman: Thanks for calling Brennick Office Furniture. How can I help?
Man: Hi. I ordered six desk chairs for my accounting firm last week. They were supposed to come yesterday, but nothing''s shown up.
Woman: I''m sorry about that. Can I get your order number?
Man: Sure. It''s four four seven one.
Woman: Thanks. OK, I see what happened. The gray model you picked ran out at our warehouse, so the order''s on hold. We do have the same chair in black, though. Those could go out today.
Man: Black''s fine. But since it''s late, could you waive the delivery charge?
Woman: Of course. I''ll take that off and e-mail you a new confirmation.', 'TTS', '["A product was sold out.", "A payment was declined.", "A truck broke down.", "An address was incorrect."]', 'A', 1, '[Part3-세부 사항(문제 원인)] 정답 (A). ''The gray model you picked ran out at our warehouse, so the order''s on hold''에서 ran out을 was sold out으로 바꿔 말했다. 오답: (B) 결제 문제는 언급되지 않았다. (C) 차량 고장 이야기는 없다. (D) 주소 오류 이야기는 없다.
포인트: 세부 사항(원인) — 바꿔 말하기(ran out → sold out)', 39),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the woman agree to do?', 'Questions 38 through 40 refer to the following conversation.

Woman: Thanks for calling Brennick Office Furniture. How can I help?
Man: Hi. I ordered six desk chairs for my accounting firm last week. They were supposed to come yesterday, but nothing''s shown up.
Woman: I''m sorry about that. Can I get your order number?
Man: Sure. It''s four four seven one.
Woman: Thanks. OK, I see what happened. The gray model you picked ran out at our warehouse, so the order''s on hold. We do have the same chair in black, though. Those could go out today.
Man: Black''s fine. But since it''s late, could you waive the delivery charge?
Woman: Of course. I''ll take that off and e-mail you a new confirmation.', 'TTS', '["Send a technician", "Provide extra chairs", "Remove a shipping fee", "Refund the full amount"]', 'C', 1, '[Part3-세부 사항(수락 내용)] 정답 (C). ''could you waive the delivery charge?''라는 부탁에 ''I''ll take that off''라고 답했다. delivery charge를 shipping fee로 바꿔 말했다. 오답: (A) 기사 파견은 없다. (B) 의자 수량은 그대로다. (D) 전액 환불이 아니라 배송비만 면제한다.
포인트: 요청과 수락 — 마지막 주고받기(delivery charge → shipping fee)', 40),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Who most likely is the man?', 'Questions 41 through 43 refer to the following conversation.

Man: Hi, I''m Greg from building maintenance. Did someone report a leak in the break room?
Woman: Yes, that was me. Thanks for coming so quickly. Water''s been dripping by the refrigerator since this morning, and we''ve got clients coming at eleven.
Man: Let''s see. Looks like a loose pipe on the floor above. I can fix it, but I''ll have to shut off the water on this floor for about forty minutes.
Woman: Hmm. It''s already ten thirty.
Man: Oh, right. In that case, I''ll come back at lunchtime, when fewer people are around.
Woman: I''d appreciate that. I''ll put up a sign telling everyone to use the kitchen on the third floor.', 'TTS', '["A delivery driver", "A maintenance worker", "An appliance salesperson", "A safety inspector"]', 'B', 1, '[Part3-직업] 정답 (B). ''I''m Greg from building maintenance''라고 밝히고 새는 배관을 고치겠다고 하므로 건물 관리 직원이다. 오답: (A) 배달 이야기는 없다. (C) 냉장고가 나오지만 판매와 무관하다. (D) 점검이 아니라 신고받은 누수를 수리하러 왔다.
포인트: 화자의 직업 — 첫 말', 41),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the woman mean when she says, "It''s already ten thirty"?', 'Questions 41 through 43 refer to the following conversation.

Man: Hi, I''m Greg from building maintenance. Did someone report a leak in the break room?
Woman: Yes, that was me. Thanks for coming so quickly. Water''s been dripping by the refrigerator since this morning, and we''ve got clients coming at eleven.
Man: Let''s see. Looks like a loose pipe on the floor above. I can fix it, but I''ll have to shut off the water on this floor for about forty minutes.
Woman: Hmm. It''s already ten thirty.
Man: Oh, right. In that case, I''ll come back at lunchtime, when fewer people are around.
Woman: I''d appreciate that. I''ll put up a sign telling everyone to use the kitchen on the third floor.', 'TTS', '["A repair should be done later.", "The man arrived later than expected.", "The break room is about to close.", "A lunch break is starting soon."]', 'A', 1, '[Part3-화자 의도] 정답 (A). 여자는 앞에서 ''we''ve got clients coming at eleven''이라고 했고, 남자가 40분 동안 물을 잠가야 한다고 하자 한 말이다. 지금 시작하면 11시를 넘기므로 지금은 곤란하다는 뜻이고, 남자도 ''In that case, I''ll come back at lunchtime''이라고 답한다. 오답: (B) ''Thanks for coming so quickly''라고 했으므로 늦게 온 것이 아니다. (C) 휴게실이 닫힌다는 말은 없다. (D) 점심시간은 남자가 다시 오겠다는 때일 뿐이다.
포인트: 화자 의도 — 앞말(11시 손님) + 뒷말(나중에 다시 옴)', 42),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the woman say she will do?', 'Questions 41 through 43 refer to the following conversation.

Man: Hi, I''m Greg from building maintenance. Did someone report a leak in the break room?
Woman: Yes, that was me. Thanks for coming so quickly. Water''s been dripping by the refrigerator since this morning, and we''ve got clients coming at eleven.
Man: Let''s see. Looks like a loose pipe on the floor above. I can fix it, but I''ll have to shut off the water on this floor for about forty minutes.
Woman: Hmm. It''s already ten thirty.
Man: Oh, right. In that case, I''ll come back at lunchtime, when fewer people are around.
Woman: I''d appreciate that. I''ll put up a sign telling everyone to use the kitchen on the third floor.', 'TTS', '["Empty a bucket", "Call a plumber", "Post a notice", "Move a refrigerator"]', 'C', 1, '[Part3-다음 행동] 정답 (C). ''I''ll put up a sign telling everyone to use the kitchen on the third floor''를 Post a notice로 바꿔 말했다. 오답: (A) 양동이 이야기는 없다. (B) 수리는 지금 온 남자가 한다. (D) 냉장고는 물이 새는 위치로 언급될 뿐이다.
포인트: 다음 행동 — 마지막 말(put up a sign → post a notice)', 43),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What are the speakers discussing?', 'Questions 44 through 46 refer to the following conversation with three speakers.

Woman: Thanks for joining me, both of you. So, we need to pick someone for the marketing coordinator job. Darnell, what did you think?
Man 1: I liked Ms. Petrova. She''s got five years at an advertising agency, and her writing samples were excellent.
Man 2: Agreed, though Mr. Fonseca was strong, too. He speaks three languages. That''d help with our overseas clients.
Woman: True. But we need someone who can start by the first of next month, and Mr. Fonseca isn''t free until summer.
Man 1: Well, then it''s Ms. Petrova.
Man 2: Fair enough. Shouldn''t we check her references first?
Woman: Right. I''ll call her former manager this afternoon. Then we can make an offer.', 'TTS', '["Planning an advertising campaign", "Opening an overseas office", "Selecting a job candidate", "Revising a job description"]', 'C', 1, '[Part3-주제] 정답 (C). ''we need to pick someone for the marketing coordinator job''에서 지원자 선정이 주제임을 알 수 있다. 오답: (A) 광고 대행사는 지원자의 경력일 뿐이다. (B) 해외 고객이 언급될 뿐 지사 개설 이야기는 없다. (D) 직무 내용을 고치자는 말은 없다.
포인트: 주제 — 첫 말(pick someone → select a candidate)', 44),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What is mentioned about Mr. Fonseca?', 'Questions 44 through 46 refer to the following conversation with three speakers.

Woman: Thanks for joining me, both of you. So, we need to pick someone for the marketing coordinator job. Darnell, what did you think?
Man 1: I liked Ms. Petrova. She''s got five years at an advertising agency, and her writing samples were excellent.
Man 2: Agreed, though Mr. Fonseca was strong, too. He speaks three languages. That''d help with our overseas clients.
Woman: True. But we need someone who can start by the first of next month, and Mr. Fonseca isn''t free until summer.
Man 1: Well, then it''s Ms. Petrova.
Man 2: Fair enough. Shouldn''t we check her references first?
Woman: Right. I''ll call her former manager this afternoon. Then we can make an offer.', 'TTS', '["He is not available right away.", "He lacks writing experience.", "He lives overseas.", "He requested a much higher salary."]', 'A', 1, '[Part3-세부 사항] 정답 (A). ''we need someone who can start by the first of next month, and Mr. Fonseca isn''t free until summer''가 근거다. 오답: (B) 글쓰기 견본은 Petrova 씨에 대한 이야기이고 Fonseca 씨의 부족함은 언급되지 않았다. (C) 3개 국어를 한다고 했을 뿐 해외 거주는 아니다. (D) 급여 이야기는 없다.
포인트: 세부 사항 — 3인 대화, 인물별 정보 구별', 45),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What will the woman do this afternoon?', 'Questions 44 through 46 refer to the following conversation with three speakers.

Woman: Thanks for joining me, both of you. So, we need to pick someone for the marketing coordinator job. Darnell, what did you think?
Man 1: I liked Ms. Petrova. She''s got five years at an advertising agency, and her writing samples were excellent.
Man 2: Agreed, though Mr. Fonseca was strong, too. He speaks three languages. That''d help with our overseas clients.
Woman: True. But we need someone who can start by the first of next month, and Mr. Fonseca isn''t free until summer.
Man 1: Well, then it''s Ms. Petrova.
Man 2: Fair enough. Shouldn''t we check her references first?
Woman: Right. I''ll call her former manager this afternoon. Then we can make an offer.', 'TTS', '["Post a job opening", "Interview a candidate", "Prepare a contract", "Contact a reference"]', 'D', 1, '[Part3-다음 행동] 정답 (D). ''I''ll call her former manager this afternoon''에서 추천인인 전 상사에게 연락함을 알 수 있다. 오답: (A) 공고 이야기는 없다. (B) 면접은 이미 끝난 상태다. (C) 제안은 추천인 확인 뒤의 일이고 계약서 준비는 언급되지 않았다.
포인트: 다음 행동 — 마지막 말(former manager → reference)', 46);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Why is the woman calling?', 'Questions 47 through 49 refer to the following conversation.

Man: Good afternoon, Tamarind House. How can I help?
Woman: Hi. I''d like to reserve a table for a business dinner this Friday at seven. There''ll be ten of us.
Man: Let me check. OK, for a group that size, I''d suggest our private dining room. It''s away from the main floor, so it''s a lot quieter.
Woman: That sounds perfect. Is there an extra charge?
Man: No, but we do ask large parties to pick a set menu in advance. I can e-mail you the options.
Woman: Please do. Oh, and two of my colleagues don''t eat meat.
Man: No problem. Every set menu comes with a vegetarian main course. What''s your e-mail address?', 'TTS', '["To book a table for a group", "To cancel a catering order", "To ask about business hours", "To complain about a meal"]', 'A', 1, '[Part3-목적] 정답 (A). ''I''d like to reserve a table for a business dinner this Friday at seven. There''ll be ten of us''가 근거다. 오답: (B) 출장 요리 주문이나 취소 이야기는 없다. (C) business dinner의 business를 이용한 오답으로 영업시간은 묻지 않았다. (D) 불만 제기가 아니다.
포인트: 전화 목적 — 첫 말(reserve → book)', 47),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man recommend?', 'Questions 47 through 49 refer to the following conversation.

Man: Good afternoon, Tamarind House. How can I help?
Woman: Hi. I''d like to reserve a table for a business dinner this Friday at seven. There''ll be ten of us.
Man: Let me check. OK, for a group that size, I''d suggest our private dining room. It''s away from the main floor, so it''s a lot quieter.
Woman: That sounds perfect. Is there an extra charge?
Man: No, but we do ask large parties to pick a set menu in advance. I can e-mail you the options.
Woman: Please do. Oh, and two of my colleagues don''t eat meat.
Man: No problem. Every set menu comes with a vegetarian main course. What''s your e-mail address?', 'TTS', '["Arriving early", "Using a separate area", "Ordering dessert", "Choosing a different day"]', 'B', 1, '[Part3-제안] 정답 (B). ''I''d suggest our private dining room. It''s away from the main floor''에서 따로 떨어진 방을 권한다. private dining room을 a separate area로 바꿔 말했다. 오답: (A) 일찍 오라는 말은 없다. (C) 후식 언급이 없다. (D) 금요일 예약을 그대로 진행한다.
포인트: 제안 — 바꿔 말하기(private dining room → separate area)', 48),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the woman mention about her colleagues?', 'Questions 47 through 49 refer to the following conversation.

Man: Good afternoon, Tamarind House. How can I help?
Woman: Hi. I''d like to reserve a table for a business dinner this Friday at seven. There''ll be ten of us.
Man: Let me check. OK, for a group that size, I''d suggest our private dining room. It''s away from the main floor, so it''s a lot quieter.
Woman: That sounds perfect. Is there an extra charge?
Man: No, but we do ask large parties to pick a set menu in advance. I can e-mail you the options.
Woman: Please do. Oh, and two of my colleagues don''t eat meat.
Man: No problem. Every set menu comes with a vegetarian main course. What''s your e-mail address?', 'TTS', '["They are visiting from abroad.", "Some of them avoid certain foods.", "They will arrive separately.", "Some of them have been there before."]', 'B', 1, '[Part3-세부 사항] 정답 (B). ''two of my colleagues don''t eat meat''를 Some of them avoid certain foods로 바꿔 말했다. 오답: (A) 해외에서 온다는 말은 없다. (C) 따로 도착한다는 말은 없다. (D) 이전 방문 경험은 언급되지 않았다.
포인트: 세부 사항 — 바꿔 말하기(don''t eat meat → avoid certain foods)', 49),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man want to do?', 'Questions 50 through 52 refer to the following conversation.

Woman: Welcome to Stonegate Community Bank. What can I do for you?
Man: Hi. I run a bakery on Pruitt Street. We''ve been open about two years, and I''d like to apply for a small business loan. I need a second oven.
Woman: Sure. Our loan officer, Mr. Whitaker, handles those, but he''s away on a business trip until Monday. I can set up an appointment, though.
Man: Monday afternoon''s good. What should I bring?
Woman: Your business license and your financial statements for the past six months.
Man: I''ve got the license, but my accountant keeps the statements. I''ll ask her for copies today.
Woman: Perfect. I''ve got you down for Monday at two.', 'TTS', '["Open a savings account", "Rent a commercial space", "Hire an accountant", "Borrow some money"]', 'D', 1, '[Part3-목적] 정답 (D). ''I''d like to apply for a small business loan''을 Borrow some money로 바꿔 말했다. 오답: (A) 은행이 배경일 뿐 계좌 개설 이야기는 없다. (B) 가게는 이미 2년째 운영 중이다. (C) 회계사는 이미 있다.
포인트: 방문 목적 — 바꿔 말하기(apply for a loan → borrow money)', 50),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Why is the man unable to meet Mr. Whitaker today?', 'Questions 50 through 52 refer to the following conversation.

Woman: Welcome to Stonegate Community Bank. What can I do for you?
Man: Hi. I run a bakery on Pruitt Street. We''ve been open about two years, and I''d like to apply for a small business loan. I need a second oven.
Woman: Sure. Our loan officer, Mr. Whitaker, handles those, but he''s away on a business trip until Monday. I can set up an appointment, though.
Man: Monday afternoon''s good. What should I bring?
Woman: Your business license and your financial statements for the past six months.
Man: I''ve got the license, but my accountant keeps the statements. I''ll ask her for copies today.
Woman: Perfect. I''ve got you down for Monday at two.', 'TTS', '["He is traveling for work.", "He is with another customer.", "He has moved to another branch.", "He works only on Mondays."]', 'A', 1, '[Part3-세부 사항(이유)] 정답 (A). ''he''s away on a business trip until Monday''를 He is traveling for work로 바꿔 말했다. 오답: (B) 다른 고객을 만나는 중이라는 말은 없다. (C) 다른 지점 이야기는 없다. (D) 월요일은 돌아오는 날일 뿐 월요일에만 근무한다는 뜻이 아니다.
포인트: 세부 사항(이유) — 바꿔 말하기(business trip → traveling for work)', 51),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man say he will do today?', 'Questions 50 through 52 refer to the following conversation.

Woman: Welcome to Stonegate Community Bank. What can I do for you?
Man: Hi. I run a bakery on Pruitt Street. We''ve been open about two years, and I''d like to apply for a small business loan. I need a second oven.
Woman: Sure. Our loan officer, Mr. Whitaker, handles those, but he''s away on a business trip until Monday. I can set up an appointment, though.
Man: Monday afternoon''s good. What should I bring?
Woman: Your business license and your financial statements for the past six months.
Man: I''ve got the license, but my accountant keeps the statements. I''ll ask her for copies today.
Woman: Perfect. I''ve got you down for Monday at two.', 'TTS', '["Renew a license", "Purchase an oven", "Visit another branch", "Request some documents"]', 'D', 1, '[Part3-다음 행동] 정답 (D). ''my accountant keeps the statements. I''ll ask her for copies today''에서 재무제표 사본을 요청함을 알 수 있다. 오답: (A) 사업자 등록증은 이미 가지고 있다. (B) 오븐 구입은 대출을 받은 뒤의 일이다. (C) 다른 지점 이야기는 없다.
포인트: 다음 행동 — 바꿔 말하기(ask for copies → request documents)', 52),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Where most likely are the speakers?', 'Questions 53 through 55 refer to the following conversation.

Man: Excuse me. I was on the flight from Eastmere that just landed, and my suitcase never came out at baggage claim.
Woman: Oh, I''m sorry. Can I see your baggage tag? Thanks. Looks like your bag got put on a later flight. It should be here around nine tonight.
Man: Nine? But I''m giving a presentation at a conference first thing tomorrow, and my suit''s in that bag!
Woman: We''ll deliver it straight to your hotel, free of charge, as soon as it comes in. Where are you staying?
Man: At the Ormond Hotel.
Woman: All right. Just write your phone number on this form, and our driver will call before he arrives.', 'TTS', '["At a hotel", "At an airport", "At a clothing store", "At a conference center"]', 'B', 1, '[Part3-장소] 정답 (B). ''the flight from Eastmere that just landed'', ''my suitcase never came out at baggage claim''에서 공항 수하물 창구임을 알 수 있다. 오답: (A) 호텔은 가방을 배달받을 곳이다. (C) suit가 나오지만 옷 가게가 아니다. (D) 학회는 내일 발표할 곳이다.
포인트: 대화 장소 — 첫 말', 53),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man say he will do tomorrow?', 'Questions 53 through 55 refer to the following conversation.

Man: Excuse me. I was on the flight from Eastmere that just landed, and my suitcase never came out at baggage claim.
Woman: Oh, I''m sorry. Can I see your baggage tag? Thanks. Looks like your bag got put on a later flight. It should be here around nine tonight.
Man: Nine? But I''m giving a presentation at a conference first thing tomorrow, and my suit''s in that bag!
Woman: We''ll deliver it straight to your hotel, free of charge, as soon as it comes in. Where are you staying?
Man: At the Ormond Hotel.
Woman: All right. Just write your phone number on this form, and our driver will call before he arrives.', 'TTS', '["Return home", "Speak at an event", "Buy a suit", "Meet a driver"]', 'B', 1, '[Part3-세부 사항] 정답 (B). ''I''m giving a presentation at a conference first thing tomorrow''를 Speak at an event로 바꿔 말했다. 오답: (A) 귀가 일정은 언급되지 않았다. (C) 정장은 가방 안에 있다고 했을 뿐 산다는 말은 없다. (D) 기사는 오늘 밤 가방을 배달하러 온다.
포인트: 세부 사항 — 바꿔 말하기(give a presentation → speak at an event)', 54),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the woman tell the man to do?', 'Questions 53 through 55 refer to the following conversation.

Man: Excuse me. I was on the flight from Eastmere that just landed, and my suitcase never came out at baggage claim.
Woman: Oh, I''m sorry. Can I see your baggage tag? Thanks. Looks like your bag got put on a later flight. It should be here around nine tonight.
Man: Nine? But I''m giving a presentation at a conference first thing tomorrow, and my suit''s in that bag!
Woman: We''ll deliver it straight to your hotel, free of charge, as soon as it comes in. Where are you staying?
Man: At the Ormond Hotel.
Woman: All right. Just write your phone number on this form, and our driver will call before he arrives.', 'TTS', '["Wait at a counter", "Show a boarding pass", "Fill out a form", "Pay a delivery fee"]', 'C', 1, '[Part3-요청] 정답 (C). ''Just write your phone number on this form''을 Fill out a form으로 바꿔 말했다. 오답: (A) 기다리라는 말은 없고 호텔로 배달해 준다. (B) 보여 달라고 한 것은 탑승권이 아니라 수하물 꼬리표다. (D) ''free of charge''라고 했으므로 배달비가 없다.
포인트: 요청 — 마지막 말(write … on this form → fill out a form)', 55),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Who most likely is the man?', 'Questions 56 through 58 refer to the following conversation.

Woman: Hi, Mateus. How''s your first week at the store going?
Man: Pretty well, thanks. Everyone''s been helpful. I still don''t know the inventory system, though. Nobody''s shown me yet.
Woman: Actually, that''s why I stopped by. I''m running a training session on it tomorrow at nine, in the back office. It takes about two hours.
Man: Great. But I''m supposed to be on the register tomorrow morning.
Woman: Don''t worry. I''ve already asked Renee to cover for you until you''re done.
Man: Thanks. Should I bring anything?
Woman: Just your employee number. You''ll need it to log in. Oh, and take a look at the user guide I e-mailed you, if you''ve got time tonight.', 'TTS', '["A new employee", "A software developer", "A store customer", "A delivery person"]', 'A', 1, '[Part3-직업] 정답 (A). ''How''s your first week at the store going?''에서 남자가 입사 첫 주의 신입 직원임을 알 수 있다. 오답: (B) 재고 시스템을 배우는 쪽이지 만드는 사람이 아니다. (C) 계산대 근무가 잡혀 있는 직원이다. (D) 배달 이야기는 없다.
포인트: 화자의 신분 — 첫 말(first week)', 56),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What will happen tomorrow morning?', 'Questions 56 through 58 refer to the following conversation.

Woman: Hi, Mateus. How''s your first week at the store going?
Man: Pretty well, thanks. Everyone''s been helpful. I still don''t know the inventory system, though. Nobody''s shown me yet.
Woman: Actually, that''s why I stopped by. I''m running a training session on it tomorrow at nine, in the back office. It takes about two hours.
Man: Great. But I''m supposed to be on the register tomorrow morning.
Woman: Don''t worry. I''ve already asked Renee to cover for you until you''re done.
Man: Thanks. Should I bring anything?
Woman: Just your employee number. You''ll need it to log in. Oh, and take a look at the user guide I e-mailed you, if you''ve got time tonight.', 'TTS', '["A store will open late.", "A shipment will arrive.", "A register will be replaced.", "Some staff will be trained."]', 'D', 1, '[Part3-세부 사항(예정)] 정답 (D). ''I''m running a training session on it tomorrow at nine, in the back office''가 근거다. 오답: (A) 개점 시간 변경은 없다. (B) 입고 이야기는 없다. (C) 계산대는 남자의 근무 위치로 언급될 뿐 교체되지 않는다.
포인트: 세부 사항(예정) — tomorrow at nine', 57),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the woman suggest the man do?', 'Questions 56 through 58 refer to the following conversation.

Woman: Hi, Mateus. How''s your first week at the store going?
Man: Pretty well, thanks. Everyone''s been helpful. I still don''t know the inventory system, though. Nobody''s shown me yet.
Woman: Actually, that''s why I stopped by. I''m running a training session on it tomorrow at nine, in the back office. It takes about two hours.
Man: Great. But I''m supposed to be on the register tomorrow morning.
Woman: Don''t worry. I''ve already asked Renee to cover for you until you''re done.
Man: Thanks. Should I bring anything?
Woman: Just your employee number. You''ll need it to log in. Oh, and take a look at the user guide I e-mailed you, if you''ve got time tonight.', 'TTS', '["Review a user guide", "Print a work schedule", "Arrive an hour early", "Update a password"]', 'A', 1, '[Part3-제안] 정답 (A). ''take a look at the user guide I e-mailed you, if you''ve got time tonight''를 Review a user guide로 바꿔 말했다. 오답: (B) 근무표를 인쇄하라는 말은 없다. (C) 일찍 오라는 말은 없다. (D) 로그인에 사번이 필요하다고 했을 뿐 비밀번호 변경은 없다.
포인트: 제안 — 마지막 말(take a look at → review)', 58),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What event are the speakers discussing?', 'Questions 59 through 61 refer to the following conversation with three speakers.

Man 1: Thanks for meeting with us, Ms. Venter. We''re organizing the Summer Food Fair this year, and we''d love for your cafe to take part.
Woman: Oh, I''m interested. When is it?
Man 2: The second Saturday in July, at Millpond Park. We''re expecting around three thousand visitors.
Woman: Wow, that''s a big crowd. And how much is a booth?
Man 2: A hundred and fifty dollars. That covers a tent and two tables.
Man 1: Vendors do have to bring their own cooking gear, though.
Woman: That''s fine. We''ve got a portable grill. Is there a deadline?
Man 1: Yes, sign-ups close this Friday. Here, I''ll leave an application form with you.', 'TTS', '["A cafe opening", "A cooking class", "A food festival", "A park cleanup"]', 'C', 1, '[Part3-주제] 정답 (C). ''We''re organizing the Summer Food Fair this year''가 근거다. fair를 festival로 바꿔 말했다. 오답: (A) 카페는 여자가 운영하는 가게일 뿐 개업 행사가 아니다. (B) 조리 기구가 나오지만 수업이 아니다. (D) 공원은 행사 장소일 뿐이다.
포인트: 주제(행사 종류) — 첫 말(fair → festival)', 59),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What are vendors required to do?', 'Questions 59 through 61 refer to the following conversation with three speakers.

Man 1: Thanks for meeting with us, Ms. Venter. We''re organizing the Summer Food Fair this year, and we''d love for your cafe to take part.
Woman: Oh, I''m interested. When is it?
Man 2: The second Saturday in July, at Millpond Park. We''re expecting around three thousand visitors.
Woman: Wow, that''s a big crowd. And how much is a booth?
Man 2: A hundred and fifty dollars. That covers a tent and two tables.
Man 1: Vendors do have to bring their own cooking gear, though.
Woman: That''s fine. We''ve got a portable grill. Is there a deadline?
Man 1: Yes, sign-ups close this Friday. Here, I''ll leave an application form with you.', 'TTS', '["Wear a uniform", "Attend a safety meeting", "Supply their own equipment", "Share a booth"]', 'C', 1, '[Part3-세부 사항] 정답 (C). ''Vendors do have to bring their own cooking gear''를 Supply their own equipment로 바꿔 말했다. 오답: (A) 복장 이야기는 없다. (B) 안전 회의 언급이 없다. (D) 부스 요금에 천막과 탁자가 포함된다고 했을 뿐 함께 쓴다는 말은 없다.
포인트: 세부 사항 — 바꿔 말하기(bring their own cooking gear → supply their own equipment)', 60),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What must the woman do by Friday?', 'Questions 59 through 61 refer to the following conversation with three speakers.

Man 1: Thanks for meeting with us, Ms. Venter. We''re organizing the Summer Food Fair this year, and we''d love for your cafe to take part.
Woman: Oh, I''m interested. When is it?
Man 2: The second Saturday in July, at Millpond Park. We''re expecting around three thousand visitors.
Woman: Wow, that''s a big crowd. And how much is a booth?
Man 2: A hundred and fifty dollars. That covers a tent and two tables.
Man 1: Vendors do have to bring their own cooking gear, though.
Woman: That''s fine. We''ve got a portable grill. Is there a deadline?
Man 1: Yes, sign-ups close this Friday. Here, I''ll leave an application form with you.', 'TTS', '["Reserve a parking space", "Send a menu", "Buy a grill", "Submit an application"]', 'D', 1, '[Part3-세부 사항(기한)] 정답 (D). ''Is there a deadline?''에 ''sign-ups close this Friday. Here, I''ll leave an application form with you''라고 답했으므로 금요일까지 신청서를 내야 한다. 오답: (A) 주차 이야기는 없다. (B) 메뉴 제출은 요구되지 않았다. (C) 휴대용 그릴은 이미 가지고 있다.
포인트: 세부 사항(기한) — 마지막 말(sign-ups close → submit an application)', 61),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Why is the woman calling?', 'Questions 62 through 64 refer to the following conversation.

Man: Brookvale Family Clinic. This is Stefan.
Woman: Hi, Stefan. It''s Rosa Delgado. I''ve got my annual checkup with Doctor Simmons tomorrow at ten, but I was just told I have a meeting then. Is there anything later in the day?
Man: Let me look. Hmm. He''s fully booked tomorrow afternoon. His next opening''s on the nineteenth.
Woman: Oh. Today''s only the fifth.
Man: I know. Well, Doctor Hadley has an opening tomorrow at four. She''s new here, but patients speak very highly of her.
Woman: All right. I''ll take that.
Man: Great. Since you''re seeing a different doctor, please come fifteen minutes early so we can update your patient information.', 'TTS', '["To ask for test results", "To get directions to a clinic", "To change an appointment time", "To refill a prescription"]', 'C', 1, '[Part3-목적] 정답 (C). 내일 10시 검진이 회의와 겹쳐 ''Is there anything later in the day?''라고 묻고 있으므로 예약 시간 변경이 목적이다. 오답: (A) 검사 결과 이야기는 없다. (B) 길 안내를 묻지 않았다. (D) 처방전 이야기는 없다.
포인트: 전화 목적 — 첫 말', 62),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the woman imply when she says, "Today''s only the fifth"?', 'Questions 62 through 64 refer to the following conversation.

Man: Brookvale Family Clinic. This is Stefan.
Woman: Hi, Stefan. It''s Rosa Delgado. I''ve got my annual checkup with Doctor Simmons tomorrow at ten, but I was just told I have a meeting then. Is there anything later in the day?
Man: Let me look. Hmm. He''s fully booked tomorrow afternoon. His next opening''s on the nineteenth.
Woman: Oh. Today''s only the fifth.
Man: I know. Well, Doctor Hadley has an opening tomorrow at four. She''s new here, but patients speak very highly of her.
Woman: All right. I''ll take that.
Man: Great. Since you''re seeing a different doctor, please come fifteen minutes early so we can update your patient information.', 'TTS', '["A wait would be too long.", "A form has the wrong date.", "A payment is not due yet.", "A meeting was moved up."]', 'A', 1, '[Part3-화자 의도] 정답 (A). ''His next opening''s on the nineteenth''라는 말에 대한 반응이다. 오늘이 5일이니 2주나 기다려야 한다는 뜻이고, 이어서 남자가 내일 4시의 다른 의사를 권하자 ''I''ll take that''이라고 한다. 오답: (B) 서류의 날짜를 지적하는 것이 아니다. (C) 납부 기한 이야기는 없다. (D) 회의는 예약을 바꾸는 이유로 앞에서 언급되었을 뿐이다.
포인트: 화자 의도 — 앞말(19일) + 뒷말(내일 다른 의사)', 63),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man ask the woman to do?', 'Questions 62 through 64 refer to the following conversation.

Man: Brookvale Family Clinic. This is Stefan.
Woman: Hi, Stefan. It''s Rosa Delgado. I''ve got my annual checkup with Doctor Simmons tomorrow at ten, but I was just told I have a meeting then. Is there anything later in the day?
Man: Let me look. Hmm. He''s fully booked tomorrow afternoon. His next opening''s on the nineteenth.
Woman: Oh. Today''s only the fifth.
Man: I know. Well, Doctor Hadley has an opening tomorrow at four. She''s new here, but patients speak very highly of her.
Woman: All right. I''ll take that.
Man: Great. Since you''re seeing a different doctor, please come fifteen minutes early so we can update your patient information.', 'TTS', '["Bring an insurance card", "Arrive early", "Call back tomorrow", "Pay in advance"]', 'B', 1, '[Part3-요청] 정답 (B). ''please come fifteen minutes early so we can update your patient information''이 근거다. 오답: (A) 보험증을 가져오라는 말은 없다. (C) 예약은 이미 확정되어 다시 전화할 필요가 없다. (D) 선결제 이야기는 없다.
포인트: 요청 — 마지막 말(please …)', 64),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Where does the conversation most likely take place?', 'Questions 65 through 67 refer to the following conversation.

Woman: Hi. I''m here to pick up some brochures for Tillman Realty.
Man: Sure, they''re right here. Want to look at one before I box them up?
Woman: Oh, no. The phone number on the back is wrong. The last digit should be a seven, not a one.
Man: Let me pull up your file. Hmm. I''m afraid the number here ends in a one.
Woman: So it''s our mistake. We need these for an open house on Saturday. How soon can you reprint them?
Man: By noon tomorrow. And since you''re a regular, I''ll do the reprint at half price.
Woman: Thank you. I''ll e-mail you the corrected file once I''m back at the office.', 'TTS', '["At a real estate agency", "At a post office", "At a print shop", "At a phone store"]', 'C', 1, '[Part3-장소] 정답 (C). 주문한 안내 책자를 찾으러 온 곳이고 ''How soon can you reprint them?''에 ''By noon tomorrow''라고 답하므로 인쇄소다. 오답: (A) 부동산 회사는 책자를 주문한 쪽이며 ''once I''m back at the office''라고 했으므로 대화 장소가 아니다. (B) box them up이 나오지만 우체국이 아니다. (D) phone number가 나오지만 휴대폰 매장이 아니다.
포인트: 대화 장소 — brochures, reprint', 65),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What problem does the woman notice?', 'Questions 65 through 67 refer to the following conversation.

Woman: Hi. I''m here to pick up some brochures for Tillman Realty.
Man: Sure, they''re right here. Want to look at one before I box them up?
Woman: Oh, no. The phone number on the back is wrong. The last digit should be a seven, not a one.
Man: Let me pull up your file. Hmm. I''m afraid the number here ends in a one.
Woman: So it''s our mistake. We need these for an open house on Saturday. How soon can you reprint them?
Man: By noon tomorrow. And since you''re a regular, I''ll do the reprint at half price.
Woman: Thank you. I''ll e-mail you the corrected file once I''m back at the office.', 'TTS', '["An order arrived a day late.", "Some pictures are too dark.", "A company logo is missing.", "Some information is wrong."]', 'D', 1, '[Part3-세부 사항(문제점)] 정답 (D). ''The phone number on the back is wrong''의 phone number를 Some information으로 바꿔 말했다. 오답: (A) 주문품은 제때 준비되어 있었다. (B) 사진 이야기는 없다. (C) 로고 이야기는 없다.
포인트: 세부 사항(문제점) — 바꿔 말하기(phone number → information)', 66);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man offer to do?', 'Questions 65 through 67 refer to the following conversation.

Woman: Hi. I''m here to pick up some brochures for Tillman Realty.
Man: Sure, they''re right here. Want to look at one before I box them up?
Woman: Oh, no. The phone number on the back is wrong. The last digit should be a seven, not a one.
Man: Let me pull up your file. Hmm. I''m afraid the number here ends in a one.
Woman: So it''s our mistake. We need these for an open house on Saturday. How soon can you reprint them?
Man: By noon tomorrow. And since you''re a regular, I''ll do the reprint at half price.
Woman: Thank you. I''ll e-mail you the corrected file once I''m back at the office.', 'TTS', '["Deliver an order", "Use thicker paper", "Refund a payment", "Lower a price"]', 'D', 1, '[Part3-제안(제공)] 정답 (D). ''since you''re a regular, I''ll do the reprint at half price''에서 재인쇄 비용을 절반으로 깎아 준다. 오답: (A) 배달 이야기는 없다. (B) 용지 이야기는 없다. (C) 실수는 주문한 회사 쪽에 있었고 환불이 아니라 할인이다.
포인트: 제공(offer to do) — 바꿔 말하기(at half price → lower a price)', 67),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'According to the man, what will end tomorrow?', 'Questions 68 through 70 refer to the following conversation.

Man: Sofia, have you signed up for the logistics conference in Northwick? The early registration rate ends tomorrow.
Woman: Yep, did it this morning. I''m still figuring out how to get there, though. Are you driving?
Man: No, I''m taking the express train. The station''s right across from the convention center, so I won''t need a taxi.
Woman: That''s handy. And parking downtown costs a fortune. Which train are you on?
Man: The seven fifteen, Monday morning. It gets in well before the opening speech.
Woman: I''ll book the same one. Do we pay for tickets ourselves?
Man: We do, but hang on to your receipt. Finance will reimburse you within thirty days.
Woman: Good to know.', 'TTS', '["A logistics conference", "A lower registration fee", "A parking promotion", "A temporary train schedule"]', 'B', 1, '[Part3-세부 사항] 정답 (B). ''The early registration rate ends tomorrow''에서 조기 등록 요금이 내일 끝남을 알 수 있다. early registration rate를 A lower registration fee로 바꿔 말했다. 오답: (A) 학회는 월요일에 시작한다. (C) 주차는 비싸다고만 했다. (D) 열차 시간표 변경 이야기는 없다.
포인트: 세부 사항 — 첫 말(early registration rate → lower fee)', 68),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Why does the man prefer to take the train?', 'Questions 68 through 70 refer to the following conversation.

Man: Sofia, have you signed up for the logistics conference in Northwick? The early registration rate ends tomorrow.
Woman: Yep, did it this morning. I''m still figuring out how to get there, though. Are you driving?
Man: No, I''m taking the express train. The station''s right across from the convention center, so I won''t need a taxi.
Woman: That''s handy. And parking downtown costs a fortune. Which train are you on?
Man: The seven fifteen, Monday morning. It gets in well before the opening speech.
Woman: I''ll book the same one. Do we pay for tickets ourselves?
Man: We do, but hang on to your receipt. Finance will reimburse you within thirty days.
Woman: Good to know.', 'TTS', '["The tickets are refundable.", "It stops near the venue.", "His car is being repaired.", "The seats are comfortable."]', 'B', 1, '[Part3-세부 사항(이유)] 정답 (B). ''The station''s right across from the convention center, so I won''t need a taxi''를 It stops near the venue로 바꿔 말했다. 오답: (A) 표 환불이 아니라 회사가 비용을 돌려준다는 이야기다. (C) 차 수리 언급이 없다. (D) 좌석 이야기는 없다.
포인트: 세부 사항(이유) — 바꿔 말하기(convention center → venue)', 69),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man remind the woman to do?', 'Questions 68 through 70 refer to the following conversation.

Man: Sofia, have you signed up for the logistics conference in Northwick? The early registration rate ends tomorrow.
Woman: Yep, did it this morning. I''m still figuring out how to get there, though. Are you driving?
Man: No, I''m taking the express train. The station''s right across from the convention center, so I won''t need a taxi.
Woman: That''s handy. And parking downtown costs a fortune. Which train are you on?
Man: The seven fifteen, Monday morning. It gets in well before the opening speech.
Woman: I''ll book the same one. Do we pay for tickets ourselves?
Man: We do, but hang on to your receipt. Finance will reimburse you within thirty days.
Woman: Good to know.', 'TTS', '["Save a receipt", "Reserve a hotel room", "Print a conference badge", "Review a speech"]', 'A', 1, '[Part3-요청·당부] 정답 (A). ''hang on to your receipt. Finance will reimburse you within thirty days''에서 영수증을 보관하라고 당부한다. 오답: (B) 숙박 이야기는 없다. (C) 명찰 인쇄 이야기는 없다. (D) 개회사는 열차 도착 시각과 관련해 언급될 뿐이다.
포인트: 당부 — 마지막 말(hang on to → save)', 70),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'Why is the speaker calling?', 'Questions 71 through 73 refer to the following telephone message.

Hi, Ms. Petrosyan. It''s Rob from Brightway Flooring, calling about the new carpet for your law office. So, we were supposed to start tomorrow morning. Unfortunately, the carpet you picked hasn''t come in yet. The manufacturer shipped it late, and they say it''ll get here Thursday. That means we can''t start until Friday. I know your staff has already moved the furniture out, and I''m really sorry about that. To make up for it, we''ll clean the carpets in your reception area at no charge. Could you call me back at five five five, zero one four two? Just let me know if Friday works for you.', 'TTS', '["To report a delay", "To confirm a payment", "To recommend a product", "To request a measurement"]', 'A', 1, '[Part4-목적] 정답 (A). ''we were supposed to start tomorrow morning. Unfortunately, the carpet you picked hasn''t come in yet''에서 공사가 늦어짐을 알리는 전화임을 알 수 있다. 오답: (B) 결제 이야기는 없다. (C) 카펫은 이미 고객이 골랐다. (D) 치수 측정 요청은 없다.
포인트: 전화 목적 — 첫 부분', 71),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'When does the speaker expect to begin the work?', 'Questions 71 through 73 refer to the following telephone message.

Hi, Ms. Petrosyan. It''s Rob from Brightway Flooring, calling about the new carpet for your law office. So, we were supposed to start tomorrow morning. Unfortunately, the carpet you picked hasn''t come in yet. The manufacturer shipped it late, and they say it''ll get here Thursday. That means we can''t start until Friday. I know your staff has already moved the furniture out, and I''m really sorry about that. To make up for it, we''ll clean the carpets in your reception area at no charge. Could you call me back at five five five, zero one four two? Just let me know if Friday works for you.', 'TTS', '["Today", "Tomorrow", "On Thursday", "On Friday"]', 'D', 1, '[Part4-세부 사항(시점)] 정답 (D). ''That means we can''t start until Friday''가 근거다. 오답: (A) 오늘 시작한다는 말은 없다. (B) 내일은 원래 예정일이었으나 미뤄졌다. (C) 목요일은 카펫이 업체에 도착하는 날이다.
포인트: 시점 — 여러 요일 가운데 가려 듣기', 72),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What does the speaker offer to do?', 'Questions 71 through 73 refer to the following telephone message.

Hi, Ms. Petrosyan. It''s Rob from Brightway Flooring, calling about the new carpet for your law office. So, we were supposed to start tomorrow morning. Unfortunately, the carpet you picked hasn''t come in yet. The manufacturer shipped it late, and they say it''ll get here Thursday. That means we can''t start until Friday. I know your staff has already moved the furniture out, and I''m really sorry about that. To make up for it, we''ll clean the carpets in your reception area at no charge. Could you call me back at five five five, zero one four two? Just let me know if Friday works for you.', 'TTS', '["Lower a price", "Provide a free service", "Pay for faster shipping", "Move some furniture"]', 'B', 1, '[Part4-제안(제공)] 정답 (B). ''we''ll clean the carpets in your reception area at no charge''를 Provide a free service로 바꿔 말했다. 오답: (A) 가격 할인은 언급되지 않았다. (C) 배송 방법을 바꾼다는 말은 없다. (D) 가구는 직원들이 이미 옮겼다고 했을 뿐 옮겨 주겠다는 말은 없다.
포인트: 제안(offer to do) — 바꿔 말하기', 73),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What is the announcement mainly about?', 'Questions 74 through 76 refer to the following announcement.

Attention, all employees. This is a reminder from the facilities department. Starting next Monday, the main entrance on Dover Street will be closed for three weeks while the lobby''s being renovated. During that time, please use the side door next to the parking garage. Now, you''ll need your employee badge to open that door, so be sure to carry it at all times. Lost your badge? Just stop by the security office before Friday for a new one. Also, deliveries will be accepted only at the loading dock behind the building. So please let your suppliers know ahead of time. Thanks for your patience.', 'TTS', '["A new parking policy", "A security training session", "A change in office hours", "A temporary entrance closure"]', 'D', 1, '[Part4-주제] 정답 (D). ''the main entrance on Dover Street will be closed for three weeks while the lobby''s being renovated''가 근거다. 오답: (A) 주차장은 옆문 위치를 설명할 때 나올 뿐이다. (B) 보안실은 사원증 재발급 장소일 뿐 교육 이야기는 없다. (C) 근무 시간 변경은 없다.
포인트: 주제 — 첫 부분', 74),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What are the listeners reminded to carry?', 'Questions 74 through 76 refer to the following announcement.

Attention, all employees. This is a reminder from the facilities department. Starting next Monday, the main entrance on Dover Street will be closed for three weeks while the lobby''s being renovated. During that time, please use the side door next to the parking garage. Now, you''ll need your employee badge to open that door, so be sure to carry it at all times. Lost your badge? Just stop by the security office before Friday for a new one. Also, deliveries will be accepted only at the loading dock behind the building. So please let your suppliers know ahead of time. Thanks for your patience.', 'TTS', '["An identification badge", "A parking permit", "A door key", "A visitor pass"]', 'A', 1, '[Part4-세부 사항] 정답 (A). ''you''ll need your employee badge to open that door, so be sure to carry it at all times''가 근거다. 오답: (B) 주차 허가증 이야기는 없다. (C) 문은 열쇠가 아니라 사원증으로 연다. (D) 청자는 방문객이 아니라 직원이다.
포인트: 세부 사항 — 당부(be sure to)', 75),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What should the listeners tell their suppliers?', 'Questions 74 through 76 refer to the following announcement.

Attention, all employees. This is a reminder from the facilities department. Starting next Monday, the main entrance on Dover Street will be closed for three weeks while the lobby''s being renovated. During that time, please use the side door next to the parking garage. Now, you''ll need your employee badge to open that door, so be sure to carry it at all times. Lost your badge? Just stop by the security office before Friday for a new one. Also, deliveries will be accepted only at the loading dock behind the building. So please let your suppliers know ahead of time. Thanks for your patience.', 'TTS', '["When the lobby will reopen", "Where deliveries will be accepted", "How to obtain a badge", "Who manages the facilities department"]', 'B', 1, '[Part4-요청] 정답 (B). ''deliveries will be accepted only at the loading dock behind the building. So please let your suppliers know ahead of time''에서 납품 받는 장소를 알리라고 한다. 오답: (A) 재개방 시점을 알리라는 말은 없다. (C) 사원증 발급은 직원에게 해당한다. (D) 부서 책임자는 언급되지 않았다.
포인트: 요청 — 마지막 부분', 76),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What type of business is being advertised?', 'Questions 77 through 79 refer to the following advertisement.

Is your company planning to relocate? Let Summit Peak handle it. For more than twenty years, we''ve helped businesses in the Glenhaven area move offices quickly and safely. Our trained crews pack up your computers, files, and furniture and deliver everything to your new address. We work evenings and weekends, so your employees never lose a day of work. This month only, customers who book with us get free packing boxes. Not sure what it''ll cost? Just fill out the short form on our website, and a consultant will come by to give you an estimate. Summit Peak. We carry the load so you don''t have to.', 'TTS', '["A furniture store", "A computer repair service", "A storage facility", "A moving company"]', 'D', 1, '[Part4-업종] 정답 (D). ''Is your company planning to relocate?'', ''Our trained crews pack up your computers, files, and furniture and deliver everything to your new address''에서 이사 업체임을 알 수 있다. 오답: (A) 가구는 옮기는 물건일 뿐이다. (B) 컴퓨터도 옮기는 물건일 뿐 수리하지 않는다. (C) 보관 서비스는 언급되지 않았다.
포인트: 업종 — 단서 종합', 77),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'According to the speaker, what is an advantage of the business?', 'Questions 77 through 79 refer to the following advertisement.

Is your company planning to relocate? Let Summit Peak handle it. For more than twenty years, we''ve helped businesses in the Glenhaven area move offices quickly and safely. Our trained crews pack up your computers, files, and furniture and deliver everything to your new address. We work evenings and weekends, so your employees never lose a day of work. This month only, customers who book with us get free packing boxes. Not sure what it''ll cost? Just fill out the short form on our website, and a consultant will come by to give you an estimate. Summit Peak. We carry the load so you don''t have to.', 'TTS', '["It has several locations.", "It operates outside regular hours.", "It guarantees the lowest prices in the area.", "It hires only local staff."]', 'B', 1, '[Part4-세부 사항(장점)] 정답 (B). ''We work evenings and weekends, so your employees never lose a day of work''를 operates outside regular hours로 바꿔 말했다. 오답: (A) 지점 수 언급이 없다. (C) 최저가 보장 이야기는 없다. (D) 직원 채용 기준은 언급되지 않았다.
포인트: 세부 사항 — 바꿔 말하기(evenings and weekends)', 78),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What can customers receive this month?', 'Questions 77 through 79 refer to the following advertisement.

Is your company planning to relocate? Let Summit Peak handle it. For more than twenty years, we''ve helped businesses in the Glenhaven area move offices quickly and safely. Our trained crews pack up your computers, files, and furniture and deliver everything to your new address. We work evenings and weekends, so your employees never lose a day of work. This month only, customers who book with us get free packing boxes. Not sure what it''ll cost? Just fill out the short form on our website, and a consultant will come by to give you an estimate. Summit Peak. We carry the load so you don''t have to.', 'TTS', '["A discounted rate", "Free packing supplies", "A gift card", "Extra insurance coverage"]', 'B', 1, '[Part4-세부 사항(혜택)] 정답 (B). ''This month only, customers who book with us get free packing boxes''에서 packing boxes를 packing supplies로 바꿔 말했다. 오답: (A) 요금 할인은 언급되지 않았다. (C) 상품권 이야기는 없다. (D) 보험 이야기는 없다.
포인트: 세부 사항 — 기간 한정 혜택(This month only)', 79),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What is the broadcast mainly about?', 'Questions 80 through 82 refer to the following radio broadcast.

Good morning, and welcome back to Tolliver Community Radio. In local news, the city council has approved plans for a new public library on Larkspur Avenue. The current one opened sixty years ago, and, well, there are a lot more people living here now. It simply can''t serve everyone. The new building will have twice the space, plus a children''s reading room. The council wants to hear what residents think of the design, so there''ll be a public meeting at city hall this Thursday at seven. Right now, I''m joined in the studio by head librarian Diane Mercer, who''ll tell us more. Welcome, Diane.', 'TTS', '["A plan for a new library", "A city council election", "A road construction project", "A children''s reading contest"]', 'A', 1, '[Part4-주제] 정답 (A). ''the city council has approved plans for a new public library on Larkspur Avenue''가 근거다. 오답: (B) 시의회는 계획을 승인한 주체일 뿐 선거 이야기는 없다. (C) 공사는 도로가 아니라 도서관 건물이다. (D) 어린이 열람실이 생긴다고 했을 뿐 대회 이야기는 없다.
포인트: 주제 — 첫 부분', 80),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'According to the speaker, what has caused a problem?', 'Questions 80 through 82 refer to the following radio broadcast.

Good morning, and welcome back to Tolliver Community Radio. In local news, the city council has approved plans for a new public library on Larkspur Avenue. The current one opened sixty years ago, and, well, there are a lot more people living here now. It simply can''t serve everyone. The new building will have twice the space, plus a children''s reading room. The council wants to hear what residents think of the design, so there''ll be a public meeting at city hall this Thursday at seven. Right now, I''m joined in the studio by head librarian Diane Mercer, who''ll tell us more. Welcome, Diane.', 'TTS', '["An increase in residents", "Damage to a building", "The end of a lease", "A reduction in city funding"]', 'A', 1, '[Part4-세부 사항(원인)] 정답 (A). ''there are a lot more people living here now. It simply can''t serve everyone''를 An increase in residents로 바꿔 말했다. 오답: (B) 건물 파손 이야기는 없다. (C) 임대 계약 이야기는 없다. (D) 예산 삭감 이야기는 없다.
포인트: 세부 사항 — 바꿔 말하기(a lot more people living here)', 81),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What will the listeners most likely hear next?', 'Questions 80 through 82 refer to the following radio broadcast.

Good morning, and welcome back to Tolliver Community Radio. In local news, the city council has approved plans for a new public library on Larkspur Avenue. The current one opened sixty years ago, and, well, there are a lot more people living here now. It simply can''t serve everyone. The new building will have twice the space, plus a children''s reading room. The council wants to hear what residents think of the design, so there''ll be a public meeting at city hall this Thursday at seven. Right now, I''m joined in the studio by head librarian Diane Mercer, who''ll tell us more. Welcome, Diane.', 'TTS', '["A weather report", "A traffic update", "An interview", "A commercial"]', 'C', 1, '[Part4-다음에 들을 내용] 정답 (C). ''Right now, I''m joined in the studio by head librarian Diane Mercer, who''ll tell us more. Welcome, Diane''에서 바로 이어 도서관장과의 대담이 나옴을 알 수 있다. 오답: (A) 날씨 예보 예고는 없다. (B) 교통 정보 예고는 없다. (D) 광고가 이어진다는 말은 없다.
포인트: 다음에 들을 내용 — 마지막 말', 82),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'Where is the tour taking place?', 'Questions 83 through 85 refer to the following tour information.

Welcome to Brenmoor''s. My name''s Luis, and I''ll be your guide this morning. We''ve been making chocolate in this building for over forty years. We''ll start in the roasting room, where the cocoa beans are prepared. Then we''ll head to the production floor. Now, normally you''d watch the wrapping machines there. It''s a holiday week, though. But don''t worry, there''s still plenty to see. Oh, and everyone needs to wear a hairnet and a lab coat. They''re by the door. At the end, we''ll stop in the tasting room, where you can sample our newest flavors. OK, please put on your gear, and we''ll get started.', 'TTS', '["At a coffee roaster", "At a commercial bakery", "At a cooking school", "At a candy factory"]', 'D', 1, '[Part4-장소] 정답 (D). ''We''ve been making chocolate in this building for over forty years'', ''the production floor''에서 초콜릿을 만드는 공장임을 알 수 있고, 이를 a candy factory로 바꿔 말했다. 오답: (A) roasting room에서 볶는 것은 커피가 아니라 카카오 콩이다. (B) 빵을 만드는 곳이 아니다. (C) tasting room이 있을 뿐 요리를 가르치는 곳이 아니다.
포인트: 장소 — 단서 종합·바꿔 말하기', 83),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What does the speaker mean when he says, "It''s a holiday week"?', 'Questions 83 through 85 refer to the following tour information.

Welcome to Brenmoor''s. My name''s Luis, and I''ll be your guide this morning. We''ve been making chocolate in this building for over forty years. We''ll start in the roasting room, where the cocoa beans are prepared. Then we''ll head to the production floor. Now, normally you''d watch the wrapping machines there. It''s a holiday week, though. But don''t worry, there''s still plenty to see. Oh, and everyone needs to wear a hairnet and a lab coat. They''re by the door. At the end, we''ll stop in the tasting room, where you can sample our newest flavors. OK, please put on your gear, and we''ll get started.', 'TTS', '["The tour will be shorter than usual.", "The gift shop is closed today.", "Some equipment is not operating.", "Admission is free for visitors."]', 'C', 1, '[Part4-화자 의도] 정답 (C). 바로 앞의 ''normally you''d watch the wrapping machines there''와 뒤의 ''there''s still plenty to see''로 보아, 연휴 주간이라 평소 보던 포장 기계가 돌아가지 않는다는 뜻이다. 오답: (A) 볼 것이 여전히 많다고 했을 뿐 견학이 짧아진다는 말은 없다. (B) 기념품 가게 이야기는 없다. (D) 입장료 이야기는 없다.
포인트: 화자 의도 — 인용문 앞뒤 문맥', 84),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What will the listeners do at the end of the tour?', 'Questions 83 through 85 refer to the following tour information.

Welcome to Brenmoor''s. My name''s Luis, and I''ll be your guide this morning. We''ve been making chocolate in this building for over forty years. We''ll start in the roasting room, where the cocoa beans are prepared. Then we''ll head to the production floor. Now, normally you''d watch the wrapping machines there. It''s a holiday week, though. But don''t worry, there''s still plenty to see. Oh, and everyone needs to wear a hairnet and a lab coat. They''re by the door. At the end, we''ll stop in the tasting room, where you can sample our newest flavors. OK, please put on your gear, and we''ll get started.', 'TTS', '["Watch a video", "Meet the owner", "Receive a discount coupon", "Try some products"]', 'D', 1, '[Part4-세부 사항(마지막 일정)] 정답 (D). ''At the end, we''ll stop in the tasting room, where you can sample our newest flavors''에서 sample을 Try some products로 바꿔 말했다. 오답: (A) 영상 시청은 없다. (B) 사장을 만난다는 말은 없다. (C) 할인 쿠폰 이야기는 없다.
포인트: 세부 사항 — 마지막 일정(sample → try)', 85),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What did customers complain about?', 'Questions 86 through 88 refer to the following excerpt from a meeting.

Before we finish, let''s talk about last quarter''s customer survey. Overall, shoppers are happy with our products and our prices. But a lot of them said they had to wait too long at the checkout, especially on weekends. Now, we did add two registers last year. Well, you all worked last Saturday. So, starting next month, we''re putting in four self-checkout machines. Customers with just a few items can pay there on their own. I''ll need some of you to stand by the machines and help shoppers at first. If you''re willing, please write your name on the sheet by the door before you leave today.', 'TTS', '["Long waits to pay", "High prices", "A limited product selection", "Unfriendly employees"]', 'A', 1, '[Part4-세부 사항(문제점)] 정답 (A). ''a lot of them said they had to wait too long at the checkout''이 근거다. 오답: (B), (C) ''shoppers are happy with our products and our prices''라고 했으므로 가격과 상품에는 만족한다. (D) 직원 태도 이야기는 없다.
포인트: 세부 사항 — 문제점(But 뒤)', 86);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'Why does the speaker say, "you all worked last Saturday"?', 'Questions 86 through 88 refer to the following excerpt from a meeting.

Before we finish, let''s talk about last quarter''s customer survey. Overall, shoppers are happy with our products and our prices. But a lot of them said they had to wait too long at the checkout, especially on weekends. Now, we did add two registers last year. Well, you all worked last Saturday. So, starting next month, we''re putting in four self-checkout machines. Customers with just a few items can pay there on their own. I''ll need some of you to stand by the machines and help shoppers at first. If you''re willing, please write your name on the sheet by the door before you leave today.', 'TTS', '["To show that a problem still exists", "To thank the listeners for extra effort", "To explain a change in the schedule", "To remind the listeners about a payment"]', 'A', 1, '[Part4-화자 의도] 정답 (A). 바로 앞의 ''we did add two registers last year''에 이어진 말로, 지난 토요일에 일한 청자들도 보았듯이 계산대를 늘렸는데도 주말 대기 문제가 그대로라는 뜻이다. 오답: (B) 수고에 감사하는 말이 아니다. (C) 근무 일정 변경 이야기는 없다. (D) 급여 이야기는 없다.
포인트: 화자 의도 — 인용문 앞뒤 문맥', 87),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What are the listeners asked to do before they leave?', 'Questions 86 through 88 refer to the following excerpt from a meeting.

Before we finish, let''s talk about last quarter''s customer survey. Overall, shoppers are happy with our products and our prices. But a lot of them said they had to wait too long at the checkout, especially on weekends. Now, we did add two registers last year. Well, you all worked last Saturday. So, starting next month, we''re putting in four self-checkout machines. Customers with just a few items can pay there on their own. I''ll need some of you to stand by the machines and help shoppers at first. If you''re willing, please write your name on the sheet by the door before you leave today.', 'TTS', '["Complete a survey", "Sign up to assist customers", "Test a new machine", "Pick up a work schedule"]', 'B', 1, '[Part4-요청] 정답 (B). ''I''ll need some of you to stand by the machines and help shoppers … please write your name on the sheet by the door before you leave today''에서 고객을 도울 사람은 이름을 적으라고 한다. 오답: (A) 설문은 고객이 이미 한 것이다. (C) 기계는 다음 달에 설치된다. (D) 근무표를 가져가라는 말은 없다.
포인트: 요청 — 마지막 부분(write your name → sign up)', 88),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What is the cause of the delay?', 'Questions 89 through 91 refer to the following public announcement.

Attention, passengers on the ten forty express to Pellston. Because of signal repairs near Ashby Station, this train will leave about thirty minutes late. Please note, too, that it''ll now depart from platform six, not platform two. If you''d rather not wait, your ticket is also good on the ten fifty local train from platform three. Just keep in mind that it stops at every station. In the meantime, the snack bar next to the ticket office is offering a free cup of coffee or tea to anyone affected by the delay. Simply show your train ticket at the counter. Thank you for traveling with Coastline Rail.', 'TTS', '["Bad weather", "Repair work on the line", "A shortage of staff", "A late connecting train"]', 'B', 1, '[Part4-세부 사항(원인)] 정답 (B). ''Because of signal repairs near Ashby Station, this train will leave about thirty minutes late''가 근거다. 오답: (A) 날씨 이야기는 없다. (C) 인력 부족 이야기는 없다. (D) 다른 열차의 연착 이야기는 없다.
포인트: 세부 사항 — 원인(Because of)', 89),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'According to the speaker, what has changed besides the departure time?', 'Questions 89 through 91 refer to the following public announcement.

Attention, passengers on the ten forty express to Pellston. Because of signal repairs near Ashby Station, this train will leave about thirty minutes late. Please note, too, that it''ll now depart from platform six, not platform two. If you''d rather not wait, your ticket is also good on the ten fifty local train from platform three. Just keep in mind that it stops at every station. In the meantime, the snack bar next to the ticket office is offering a free cup of coffee or tea to anyone affected by the delay. Simply show your train ticket at the counter. Thank you for traveling with Coastline Rail.', 'TTS', '["The ticket price", "The destination", "The number of stops", "The platform"]', 'D', 1, '[Part4-세부 사항(변경 사항)] 정답 (D). ''it''ll now depart from platform six, not platform two''가 근거다. 오답: (A) 요금 변경은 없다. (B) 목적지는 그대로 Pellston이다. (C) 모든 역에 서는 것은 다른 열차(완행)이고 이 급행의 정차역이 바뀐 것은 아니다.
포인트: 세부 사항 — 변경 사항(now … not …)', 90),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What should passengers show to get a free drink?', 'Questions 89 through 91 refer to the following public announcement.

Attention, passengers on the ten forty express to Pellston. Because of signal repairs near Ashby Station, this train will leave about thirty minutes late. Please note, too, that it''ll now depart from platform six, not platform two. If you''d rather not wait, your ticket is also good on the ten fifty local train from platform three. Just keep in mind that it stops at every station. In the meantime, the snack bar next to the ticket office is offering a free cup of coffee or tea to anyone affected by the delay. Simply show your train ticket at the counter. Thank you for traveling with Coastline Rail.', 'TTS', '["A membership card", "A train ticket", "A store receipt", "A printed coupon"]', 'B', 1, '[Part4-세부 사항] 정답 (B). ''offering a free cup of coffee or tea to anyone affected by the delay. Simply show your train ticket at the counter''가 근거다. 오답: (A) 회원 카드 이야기는 없다. (C) 영수증 이야기는 없다. (D) 쿠폰 이야기는 없다.
포인트: 세부 사항 — 마지막 부분', 91),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'Where is the introduction most likely being given?', 'Questions 92 through 94 refer to the following introduction.

Good evening, everyone, and thanks for coming to the annual dinner of the Lindale Small Business Association. It''s my pleasure to introduce tonight''s speaker, Ms. Ines Quintero. Fifteen years ago, Ms. Quintero started out selling handmade soap at a weekend market. Today, her company has forty employees and ships to stores across the country. Tonight, she''ll share how she found reliable suppliers as her business grew. There''ll be time for questions afterward. So please write yours on the cards at your tables, and our volunteers will collect them. And we''re giving away copies of her new book at the door. Now, please join me in welcoming Ms. Quintero.', 'TTS', '["At a bookstore", "At a weekend market", "At a company orientation", "At an association dinner"]', 'D', 1, '[Part4-장소] 정답 (D). ''thanks for coming to the annual dinner of the Lindale Small Business Association''이 근거다. 오답: (A) 책은 문 앞에서 나눠 줄 증정품일 뿐이다. (B) 주말 시장은 연사가 15년 전에 사업을 시작한 곳이다. (C) 신입 사원 교육이 아니다.
포인트: 장소 — 첫 부분', 92),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What will Ms. Quintero talk about?', 'Questions 92 through 94 refer to the following introduction.

Good evening, everyone, and thanks for coming to the annual dinner of the Lindale Small Business Association. It''s my pleasure to introduce tonight''s speaker, Ms. Ines Quintero. Fifteen years ago, Ms. Quintero started out selling handmade soap at a weekend market. Today, her company has forty employees and ships to stores across the country. Tonight, she''ll share how she found reliable suppliers as her business grew. There''ll be time for questions afterward. So please write yours on the cards at your tables, and our volunteers will collect them. And we''re giving away copies of her new book at the door. Now, please join me in welcoming Ms. Quintero.', 'TTS', '["Designing product packaging", "Recruiting employees", "Finding dependable suppliers", "Opening retail stores"]', 'C', 1, '[Part4-세부 사항(강연 주제)] 정답 (C). ''Tonight, she''ll share how she found reliable suppliers as her business grew''에서 reliable을 dependable로 바꿔 말했다. 오답: (A) 포장 디자인 이야기는 없다. (B) 직원이 40명이라고 했을 뿐 채용이 강연 주제는 아니다. (D) 전국 매장에 납품한다고 했을 뿐 직접 매장을 연다는 말은 없다.
포인트: 세부 사항 — 바꿔 말하기(reliable → dependable)', 93),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What are the listeners asked to do?', 'Questions 92 through 94 refer to the following introduction.

Good evening, everyone, and thanks for coming to the annual dinner of the Lindale Small Business Association. It''s my pleasure to introduce tonight''s speaker, Ms. Ines Quintero. Fifteen years ago, Ms. Quintero started out selling handmade soap at a weekend market. Today, her company has forty employees and ships to stores across the country. Tonight, she''ll share how she found reliable suppliers as her business grew. There''ll be time for questions afterward. So please write yours on the cards at your tables, and our volunteers will collect them. And we''re giving away copies of her new book at the door. Now, please join me in welcoming Ms. Quintero.', 'TTS', '["Purchase a book", "Turn off their phones", "Write down their questions", "Move to another table"]', 'C', 1, '[Part4-요청] 정답 (C). ''please write yours on the cards at your tables''에서 질문을 카드에 적어 달라고 한다. 오답: (A) 책은 ''giving away''이므로 사는 것이 아니라 무료로 받는다. (B) 휴대폰 이야기는 없다. (D) 자리 이동 요청은 없다.
포인트: 요청 — please 뒤', 94),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What type of organization recorded the message?', 'Questions 95 through 97 refer to the following recorded message.

Thank you for calling the Wexley Science Center. We''re open Tuesday through Sunday, from ten in the morning until five. We''re closed on Mondays. Please note that our planetarium is currently closed while its sound system is being upgraded. It''ll reopen on the first of May. All of our other exhibits are open as usual. And this month, don''t miss our special exhibit on ocean life. It''s included in the regular admission price. Groups of fifteen or more qualify for a reduced rate, but they must book at least one week ahead. To make a group reservation, press one now. To hear this message again, press two.', 'TTS', '["A museum", "A movie theater", "An aquarium", "A public library"]', 'A', 1, '[Part4-화자·업종] 정답 (A). ''the Wexley Science Center'', ''All of our other exhibits are open as usual'', ''the regular admission price''에서 전시물을 보여 주고 입장료를 받는 과학관, 곧 박물관임을 알 수 있다. 오답: (B) sound system이 나오지만 영화관이 아니다. (C) ocean life는 이달의 특별 전시 주제일 뿐이다. (D) 입장료와 전시가 있으므로 도서관이 아니다.
포인트: 업종 — 단서 종합(exhibits, admission)', 95),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'Why is one area temporarily closed?', 'Questions 95 through 97 refer to the following recorded message.

Thank you for calling the Wexley Science Center. We''re open Tuesday through Sunday, from ten in the morning until five. We''re closed on Mondays. Please note that our planetarium is currently closed while its sound system is being upgraded. It''ll reopen on the first of May. All of our other exhibits are open as usual. And this month, don''t miss our special exhibit on ocean life. It''s included in the regular admission price. Groups of fifteen or more qualify for a reduced rate, but they must book at least one week ahead. To make a group reservation, press one now. To hear this message again, press two.', 'TTS', '["A private event is being held there.", "Some equipment is being improved.", "Staff members are being trained.", "Attendance has been low."]', 'B', 1, '[Part4-세부 사항(이유)] 정답 (B). ''our planetarium is currently closed while its sound system is being upgraded''를 Some equipment is being improved로 바꿔 말했다. 오답: (A) 대관 행사 이야기는 없다. (C) 직원 교육 이야기는 없다. (D) 관람객 수 이야기는 없다.
포인트: 세부 사항 — 바꿔 말하기(sound system → equipment)', 96),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'According to the message, what must groups do to get a reduced rate?', 'Questions 95 through 97 refer to the following recorded message.

Thank you for calling the Wexley Science Center. We''re open Tuesday through Sunday, from ten in the morning until five. We''re closed on Mondays. Please note that our planetarium is currently closed while its sound system is being upgraded. It''ll reopen on the first of May. All of our other exhibits are open as usual. And this month, don''t miss our special exhibit on ocean life. It''s included in the regular admission price. Groups of fifteen or more qualify for a reduced rate, but they must book at least one week ahead. To make a group reservation, press one now. To hear this message again, press two.', 'TTS', '["Reserve ahead of time", "Show a membership card", "Pay by credit card", "Visit on a weekday"]', 'A', 1, '[Part4-세부 사항(조건)] 정답 (A). ''Groups of fifteen or more qualify for a reduced rate, but they must book at least one week ahead''에서 book을 Reserve로 바꿔 말했다. 오답: (B) 회원 카드 이야기는 없다. (C) 결제 수단 이야기는 없다. (D) 평일 방문 조건은 없다.
포인트: 세부 사항 — 조건(must)', 97),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'Who most likely are the listeners?', 'Questions 98 through 100 refer to the following talk.

Good morning, and welcome to your first day of customer service training here at Ferncliff Communications. Over the next three days, you''ll learn to answer calls and solve common billing problems. So, this morning, I''ll walk you through our phone system. Then, after lunch, each of you will sit with an experienced representative and listen in on real customer calls. Tomorrow, you''ll start taking calls yourselves. OK, before we begin, please open your folder. Inside, there''s a form asking for your bank details. We need that to set up your salary payments. Please fill it out and hand it to me by the end of the day.', 'TTS', '["Bank tellers", "Telephone repair technicians", "Newly hired representatives", "Longtime customers"]', 'C', 1, '[Part4-청자] 정답 (C). ''welcome to your first day of customer service training'', ''set up your salary payments''에서 새로 입사한 고객 상담 직원들임을 알 수 있다. 오답: (A) bank details는 급여 이체용 계좌 정보일 뿐이다. (B) 전화 시스템 사용법을 배울 뿐 수리 기사가 아니다. (D) 급여를 받는 직원이지 고객이 아니다.
포인트: 청자 — 단서 종합', 98),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What will the listeners do after lunch?', 'Questions 98 through 100 refer to the following talk.

Good morning, and welcome to your first day of customer service training here at Ferncliff Communications. Over the next three days, you''ll learn to answer calls and solve common billing problems. So, this morning, I''ll walk you through our phone system. Then, after lunch, each of you will sit with an experienced representative and listen in on real customer calls. Tomorrow, you''ll start taking calls yourselves. OK, before we begin, please open your folder. Inside, there''s a form asking for your bank details. We need that to set up your salary payments. Please fill it out and hand it to me by the end of the day.', 'TTS', '["Take a written test", "Answer calls on their own", "Tour the building", "Observe experienced coworkers"]', 'D', 1, '[Part4-세부 사항(일정)] 정답 (D). ''after lunch, each of you will sit with an experienced representative and listen in on real customer calls''를 Observe experienced coworkers로 바꿔 말했다. 오답: (A) 시험 이야기는 없다. (B) 직접 전화를 받는 것은 내일부터다. (C) 건물 견학 이야기는 없다.
포인트: 세부 사항 — 일정 순서(after lunch / Tomorrow)', 99),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What are the listeners asked to hand in by the end of the day?', 'Questions 98 through 100 refer to the following talk.

Good morning, and welcome to your first day of customer service training here at Ferncliff Communications. Over the next three days, you''ll learn to answer calls and solve common billing problems. So, this morning, I''ll walk you through our phone system. Then, after lunch, each of you will sit with an experienced representative and listen in on real customer calls. Tomorrow, you''ll start taking calls yourselves. OK, before we begin, please open your folder. Inside, there''s a form asking for your bank details. We need that to set up your salary payments. Please fill it out and hand it to me by the end of the day.', 'TTS', '["A training evaluation", "A photo for a badge", "A completed form", "A signed contract"]', 'C', 1, '[Part4-요청] 정답 (C). ''there''s a form asking for your bank details'', ''Please fill it out and hand it to me by the end of the day''가 근거다. 오답: (A) 교육 평가서 이야기는 없다. (B) 사원증 사진 이야기는 없다. (D) 서류는 계약서가 아니라 계좌 정보 양식이다.
포인트: 요청 — 마지막 부분(fill it out → completed)', 100),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'The front-desk staff at Hotel Marivelle responded ------- to every guest request during the busy holiday weekend.', NULL, 'NONE', '["prompt", "promptly", "promptness", "prompted"]', 'B', 1, '[문법-품사(부사 자리)] 정답 (B). 자동사 responded와 전치사구 to every guest request 사이에서 동사를 꾸미는 부사 자리이므로 promptly가 맞다. 오답: (A) 형용사는 동사를 꾸미지 못한다. (C) 명사는 respond의 목적어가 될 수 없다(respond는 to와 함께 쓰는 자동사). (D) 과거형·과거분사로 동사 뒤 수식어 자리에 올 수 없다.
포인트: 품사 — 동사를 꾸미는 부사 자리', 101),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Because of last week''s heavy rain, resurfacing the hotel parking area will take ------- three weeks to finish.', NULL, 'NONE', '["another", "other", "every", "one another"]', 'A', 1, '[문법-대명사(한정사 another + 수사 + 복수명사)] 정답 (A). 빈칸 뒤의 three weeks를 한 덩어리의 기간으로 보고 ''3주 더''라는 추가를 나타내는 another + 수사 + 복수명사 자리다. 오답: (B) other에는 ''추가로 더''라는 뜻이 없고, 수사 앞에 쓰려면 the other three weeks처럼 한정사가 필요하다. (C) every three weeks는 ''3주마다''라는 빈도라서 공사에 걸리는 기간(take)이 되지 못한다. (D) one another는 ''서로''라는 뜻의 대명사다.
포인트: another + 수사 + 복수명사(추가)', 102),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'The Osgood Public Library will remain closed ------- the renovation of its second-floor reading room is complete.', NULL, 'NONE', '["until", "despite", "along", "prior to"]', 'A', 1, '[접속사(절·구 구별, 시간)] 정답 (A). 빈칸 뒤에 주어(the renovation)와 동사(is)를 갖춘 절이 오므로 접속사로 쓸 수 있는 until이 필요하고, ''공사가 끝날 때까지 휴관''이라는 뜻도 맞다. 오답: (B) despite, (C) along, (D) prior to는 모두 전치사라서 뒤에 절을 이끌지 못한다.
포인트: 접속사 vs 전치사(until + 절)', 103),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'After the final inspection, the city will ------- a certificate allowing the restaurant to use its patio.', NULL, 'NONE', '["resolve", "issue", "attend", "consume"]', 'B', 1, '[어휘-동사(문맥별 뜻 issue)] 정답 (B). a certificate(증명서)를 목적어로 하고, 점검이 끝난 뒤 시가 식당에 내주는 것이므로 ''발급하다''의 issue가 맞다. 오답: (A) resolve는 문제·분쟁을 해결하다, (C) attend는 행사에 참석하다, (D) consume은 소비하다라는 뜻이어서 증명서를 목적어로 하면 뜻이 통하지 않는다.
포인트: 어휘 issue(발급하다)', 104),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'All dental instruments at Brindle Dental Care ------- after each appointment in accordance with regional health regulations.', NULL, 'NONE', '["are sterilized", "sterilize", "are sterilizing", "have sterilized"]', 'A', 1, '[문법-태(수동)] 정답 (A). 주어 instruments는 소독을 받는 대상이고 빈칸 뒤에 목적어가 없으므로 수동태 are sterilized가 맞다. 오답: (B) sterilize, (C) are sterilizing, (D) have sterilized는 모두 능동형이라 타동사 sterilize의 목적어가 필요한데 문장에 목적어가 없다.
포인트: 태 — 수동태(주어가 동작의 대상, 목적어 없음)', 105),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Please confirm ------- of the attached contract by replying to this message before the end of the week.', NULL, 'NONE', '["permission", "admission", "reference", "receipt"]', 'D', 1, '[어휘-명사(문맥별 뜻 receipt)] 정답 (D). confirm receipt of는 ''~을 받았음을 확인하다''라는 결합으로, 첨부한 계약서를 받았다고 답장해 달라는 문맥에 맞다. 여기서 receipt는 ''영수증''이 아니라 ''수령''이다. 오답: (A) permission 허가, (B) admission 입장·입학, (C) reference 참조·추천서는 of the attached contract와 이어져 답장으로 확인할 내용을 만들지 못한다.
포인트: 어휘 receipt(수령) — confirm receipt of', 106);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Reviewers report that the Solvane 5 tablet''s battery remains fully ------- even after two years of daily use.', NULL, 'NONE', '["functional", "function", "functionally", "functions"]', 'A', 1, '[문법-품사(형용사 보어)] 정답 (A). remains 뒤에서 주어 battery의 상태를 설명하는 보어 자리이고 부사 fully의 수식을 받으므로 형용사 functional(제대로 작동하는)이 맞다. 오답: (B) function과 (D) functions는 명사·동사 형태라 fully의 수식을 받는 보어가 되지 못한다. (C) 부사는 보어가 될 수 없다. fully 뒤라고 부사를 고르면 안 된다.
포인트: 품사 — 보어 자리 형용사(부사 + 형용사)', 107),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Mr. Delacroix is ------- for coordinating shipments between the Pennock port and the inland distribution center.', NULL, 'NONE', '["capable", "responsible", "accustomed", "familiar"]', 'B', 1, '[어휘-형용사(전치사 결합)] 정답 (B). 빈칸 뒤의 for + 동명사와 결합해 ''~을 담당하다''라는 뜻을 만드는 것은 responsible for이다. 오답: (A) capable은 of, (C) accustomed는 to, (D) familiar는 with와 결합하므로 for coordinating 앞에 올 수 없다.
포인트: 연어 responsible for', 108),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'The number of patrons borrowing electronic books from the Thornbury branch ------- steadily since the mobile application was introduced.', NULL, 'NONE', '["have risen", "are rising", "rise", "has risen"]', 'D', 1, '[문법-수일치(the number of + 현재완료)] 정답 (D). 주어의 중심은 단수 The number이고, since + 과거 시점이 있으므로 단수 현재완료 has risen이 맞다. 오답: (A) have risen, (B) are rising, (C) rise는 모두 복수 동사로, 가까이 있는 patrons·books에 잘못 맞춘 형태다.
포인트: 수일치 — The number of + 단수 동사', 109),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Please ------- all guests of the pool closure scheduled for next Thursday morning by placing a notice in each room.', NULL, 'NONE', '["announce", "explain", "notify", "mention"]', 'C', 1, '[어휘-동사(문형: 사람 of 내용)] 정답 (C). 사람(all guests)을 목적어로 두고 of + 내용을 잇는 문형은 notify 사람 of 내용이다. 오답: (A) announce, (B) explain, (D) mention은 내용을 목적어로 두고 사람은 to로 연결하는 동사(announce/explain/mention 내용 to 사람)라서 all guests of the pool closure 구조에 맞지 않는다.
포인트: 동사 문형 — notify 사람 of 내용', 110),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'The brochure ------- the hotel''s newly renovated conference facilities will be mailed to corporate clients next week.', NULL, 'NONE', '["describe", "described", "describes", "describing"]', 'D', 1, '[문법-분사(능동·수동)] 정답 (D). 본동사는 will be mailed이므로 빈칸은 The brochure를 뒤에서 꾸미는 수식어 자리다. 안내 책자가 시설을 ''설명하는'' 능동 관계이고 뒤에 목적어(the hotel''s … facilities)가 있으므로 현재분사 describing이 맞다. 오답: (A)·(C)는 정형동사라 본동사가 둘이 된다. (B) 이 문장에서 described를 과거분사로 보면 ''설명되는 책자''가 되어 뒤의 the hotel''s … facilities를 목적어로 받지 못한다.
포인트: 분사 — 명사를 뒤에서 꾸미는 현재분사(능동)', 111),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', '------- the new oven may reduce baking time, Pelletier Bakery has decided to keep its current production schedule for now.', NULL, 'NONE', '["So that", "Now that", "As soon as", "Although"]', 'D', 1, '[접속사(양보·목적 구별)] 정답 (D). ''새 오븐이 굽는 시간을 줄일 수도 있지만, 당분간 현재 생산 일정을 유지하기로 했다''는 양보 관계이므로 Although가 맞다. 오답: (A) So that은 목적을 나타내는데 may가 보인다고 고르면 안 된다. 뒤 절이 그 목적의 수단이 아니다. (B) Now that은 ''~이므로''라는 원인이 되어 내용이 모순된다. (C) As soon as는 시간 관계로 may reduce와 어울리지 않는다.
포인트: 접속사 의미 관계(양보 Although / 목적 So that)', 112),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Aldermoor Design Studio has requested the city''s ------- to add a rooftop terrace to the Tiverton Street office building.', NULL, 'NONE', '["permission", "permissive", "permissibly", "permitted"]', 'A', 1, '[문법-품사(명사 자리)] 정답 (A). 소유격 the city''s 뒤이자 has requested의 목적어 자리이므로 명사 permission이 맞다(permission to do ~할 허가). 오답: (B) 형용사는 뒤에 꾸밀 명사가 없다. (C) 부사는 소유격 뒤 목적어 자리에 올 수 없다. (D) 과거분사·과거형은 명사구의 중심이 될 수 없다.
포인트: 품사 — 소유격 뒤 명사 자리', 113),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', '------- at the annual publishing conference in Whitcombe has risen by 20 percent compared with last year.', NULL, 'NONE', '["Attendance", "Attendant", "Attendees", "Attendants"]', 'A', 1, '[어휘-명사(같은 어근의 행위·사람 명사)] 정답 (A). 20퍼센트 늘어난 것은 ''참석자 수·참석률''이므로 행위·수치를 나타내는 불가산명사 Attendance가 맞고 단수 동사 has와도 일치한다. 오답: (B) Attendant(안내원)는 가산명사라 관사 없이 단수로 쓸 수 없고 뜻도 맞지 않는다. (C) Attendees(참석자들), (D) Attendants(안내원들)는 복수라서 has와 수가 맞지 않는다.
포인트: 명사 구별 — 행위 명사 attendance / 사람 명사 attendee·attendant', 114),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'The building inspector has requested that the revised floor plans ------- to her office no later than Friday.', NULL, 'NONE', '["to be sent", "be sent", "were sent", "sending"]', 'B', 1, '[문법-제안절(that절 동사원형)] 정답 (B). 요구를 나타내는 request 뒤의 that절은 (should) + 동사원형을 쓰며, 도면은 보내지는 대상이므로 수동의 원형 be sent가 맞다. 오답: (A) to부정사와 (D) -ing는 that절의 동사가 될 수 없다. (C) were sent는 과거 사실을 말하는 형태로 ''금요일까지 보내 달라''는 요구의 내용과 맞지 않는다.
포인트: 요구 동사 request + that절 동사원형', 115),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'The catering manager admitted that Saturday''s banquet was -------, which is why the meal was served so slowly.', NULL, 'NONE', '["outdated", "overpriced", "understaffed", "widespread"]', 'C', 1, '[어휘-형용사(understaffed)] 정답 (C). 식사가 아주 늦게 나온 까닭이 되는 상태이므로 ''인력이 부족한''이라는 understaffed가 맞다. 오답: (A) outdated 구식인, (B) overpriced 값이 너무 비싼, (D) widespread 널리 퍼진은 서빙이 늦어진 이유를 설명하지 못한다.
포인트: 어휘 understaffed(인력이 부족한)', 116),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Brannock Tools has signed a five-year agreement with a distributor ------- network covers more than 40 countries.', NULL, 'NONE', '["that", "whoever", "whose", "its"]', 'C', 1, '[문법-관계사(소유격 whose)] 정답 (C). 빈칸 뒤에 주어 network와 동사 covers, 목적어가 모두 있고, 선행사 a distributor와 network가 ''그 유통업체의 판매망''이라는 소유 관계이므로 whose가 맞다. 오답: (A) that은 관계절 안에서 주어나 목적어 역할을 해야 하는데 빈 자리가 없다. (B) whoever는 선행사 뒤에 쓰지 못한다. (D) its는 대명사라 두 절을 연결하지 못한다.
포인트: 관계대명사 소유격 whose + 명사', 117),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Seldom does the Varley Museum ------- open past 6:00 P.M., even during the busy summer season.', NULL, 'NONE', '["stays", "stayed", "staying", "stay"]', 'D', 1, '[문법-도치(부정어 문두)] 정답 (D). 부정 부사 Seldom이 문두에 오면 조동사 + 주어 + 동사 순서로 도치된다. 조동사 does가 이미 주어 앞에 나와 있으므로 주어 뒤에는 동사원형 stay가 와야 한다. 오답: (A) stays는 주어가 단수라는 이유로 고르기 쉽지만 does 뒤에는 쓸 수 없다. (B) stayed, (C) staying도 does와 결합하지 못한다.
포인트: 부정어 도치 — Seldom does + 주어 + 동사원형', 118),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'During yesterday''s conference call, Mr. Aldana and the suppliers ------- several ways to reduce packaging costs.', NULL, 'NONE', '["talked", "replied", "discussed", "commented"]', 'C', 1, '[어휘-동사(문형: discuss + 목적어)] 정답 (C). 빈칸 뒤에 전치사 없이 목적어 several ways가 바로 오므로 타동사 discuss가 맞다(discuss + 목적어. discuss about은 틀린 형태). 오답: (A) talked는 about, (B) replied는 to, (D) commented는 on이 있어야 뒤에 명사를 둘 수 있다.
포인트: 동사 문형 — discuss + 목적어(전치사 없이)', 119),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Room rates listed on the hotel''s website are ------- to change without notice during the summer festival period.', NULL, 'NONE', '["capable", "subject", "dependent", "exempt"]', 'B', 1, '[어휘-형용사(subject to)] 정답 (B). be subject to change는 ''변경될 수 있다''라는 뜻으로, 요금이 예고 없이 바뀔 수 있다는 문맥과 전치사 to에 모두 맞다. 오답: (A) capable은 of, (C) dependent는 on, (D) exempt는 from과 결합하므로 to change 앞에 올 수 없다.
포인트: 연어 be subject to change', 120),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Ms. Abernathy, who joined Nimbelle Software last spring, now ------- the team that develops its mobile banking application.', NULL, 'NONE', '["led", "was leading", "had led", "leads"]', 'D', 1, '[문법-시제(주절과 관계절의 시간 단서)] 정답 (D). last spring은 관계절(who joined ...)의 시간 표현일 뿐이고, 주절에는 now가 있으며 뒤의 관계절도 현재(develops)이므로 주절 동사는 현재형 leads가 맞다. 오답: (A) led, (B) was leading, (C) had led는 모두 과거 계열로, 관계절의 last spring을 주절에까지 적용한 형태다.
포인트: 시제 — 주절의 now와 관계절의 과거 표현 구별', 121),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Ms. Okada ------- the sale of the Pell Theater to a group of local investors earlier this year.', NULL, 'NONE', '["brokered", "inhabited", "resembled", "commuted"]', 'A', 1, '[어휘-동사(동사로 쓰는 broker)] 정답 (A). 극장을 투자자들에게 파는 거래를 성사시킨 것이므로 ''(거래·계약을) 중개하다''라는 동사 broker의 과거형이 맞다. 명사 ''중개인''으로만 알고 있으면 놓치기 쉽다. 오답: (B) inhabit 거주하다, (C) resemble 닮다는 the sale을 목적어로 하면 뜻이 통하지 않는다. (D) commute 통근하다는 자동사라 목적어를 취하지 않는다.
포인트: 어휘 broker(동사: 중개하다)', 122),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', '------- designing residential buildings, Greaves Design Group provides consulting services for historic restoration projects.', NULL, 'NONE', '["In addition to", "Even though", "In order that", "As long as"]', 'A', 1, '[전치사(구 전치사와 접속사 구별)] 정답 (A). 빈칸 뒤에 주어 없는 동명사구(designing residential buildings)가 오므로 전치사 역할을 하는 In addition to(~ 외에도)가 맞다. 오답: (B) Even though, (C) In order that, (D) As long as는 접속사라서 뒤에 주어와 동사를 갖춘 절이 와야 한다.
포인트: 구 전치사 In addition to + -ing vs 접속사', 123),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Saffira Bistro has ------- received high ratings from food critics since it opened three years ago.', NULL, 'NONE', '["consistent", "consistently", "consistency", "consist"]', 'B', 1, '[문법-품사(부사 자리)] 정답 (B). has와 과거분사 received 사이는 동사를 꾸미는 부사 자리이므로 consistently(꾸준히)가 맞고, since it opened와도 뜻이 통한다. 오답: (A) 형용사는 동사를 꾸미지 못한다. (C) 명사와 (D) 동사원형은 has와 received 사이에 올 수 없다.
포인트: 품사 — has + 부사 + 과거분사(consistently)', 124),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', '------- the structural survey, the engineers began drafting recommendations for reinforcing the library''s east wing.', NULL, 'NONE', '["Complete", "Completed", "To be completed", "Having completed"]', 'D', 1, '[문법-분사(완료 분사구문)] 정답 (D). 주절의 주어 the engineers가 조사를 ''끝낸'' 능동 관계이고, 조사를 끝낸 일이 권고안 작성(began)보다 먼저이므로 완료 분사구문 Having completed가 맞다. 뒤에 목적어 the structural survey도 있다. 오답: (A) 동사원형·형용사로는 분사구문을 만들 수 없다. (B) Completed와 (C) To be completed는 수동의 뜻이라 이 문장에서는 뒤의 the structural survey를 목적어로 받지 못한다.
포인트: 완료 분사구문 Having + 과거분사', 125),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'The two versions of the scheduling software are ------- identical, differing only in the layout of the settings menu.', NULL, 'NONE', '["eagerly", "tightly", "nearly", "punctually"]', 'C', 1, '[어휘-부사] 정답 (C). 설정 메뉴의 배치만 다르다고 했으므로 두 버전은 ''거의'' 같다는 nearly identical이 맞다. 오답: (A) eagerly 열심히, (B) tightly 단단히, (D) punctually 시간을 지켜는 형용사 identical의 정도를 나타내지 못한다.
포인트: 부사 어휘 — nearly identical', 126);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Employees who have questions ------- the revised travel reimbursement policy should contact the payroll office before May 1.', NULL, 'NONE', '["regarding", "throughout", "toward", "except"]', 'A', 1, '[전치사(regarding)] 정답 (A). questions와 the revised … policy를 ''~에 관한''이라는 뜻으로 잇는 전치사 regarding이 맞다. 오답: (B) throughout은 기간·장소 ''내내·전체에 걸쳐'', (C) toward는 방향 ''~ 쪽으로'', (D) except는 ''~을 제외하고''라는 뜻이어서 질문의 주제를 나타내지 못한다.
포인트: 전치사 regarding(~에 관한)', 127),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Because ------- of the equipment in the clinic''s laboratory is more than 10 years old, the director has requested funding for replacements.', NULL, 'NONE', '["many", "both", "much", "a few"]', 'C', 1, '[문법-대명사(수량 표현·가산성)] 정답 (C). equipment는 불가산명사이고 동사도 단수 is이므로 불가산명사와 쓰는 수량 표현 much가 맞다. 오답: (A) many, (B) both, (D) a few는 모두 복수 가산명사와 쓰고 복수 동사를 취하므로 of the equipment ... is와 맞지 않는다.
포인트: 수량 표현 — much of + 불가산명사', 128),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Quillhaven Press reported ------- higher revenue this year, largely because of strong sales of its digital textbooks.', NULL, 'NONE', '["substantial", "substantially", "substance", "substantiate"]', 'B', 1, '[문법-품사(비교급을 꾸미는 부사)] 정답 (B). 빈칸은 비교급 형용사 higher를 꾸며 정도를 강조하는 자리이므로 부사 substantially(상당히)가 맞다. 오답: (A) 형용사는 다른 형용사 higher를 꾸미지 못한다. (C) 명사와 (D) 동사는 reported와 higher revenue 사이에 올 수 없다.
포인트: 품사 — 비교급 형용사 앞 부사 자리', 129),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Staff at the head office may work remotely on Fridays ------- they obtain written approval from their department manager in advance.', NULL, 'NONE', '["whereas", "in case of", "provided that", "so as to"]', 'C', 1, '[접속사(조건)] 정답 (C). 뒤에 절(they obtain ...)이 오고 ''사전에 서면 승인을 받는다는 조건으로 재택근무가 가능하다''는 뜻이므로 조건 접속사 provided that이 맞다. 오답: (A) whereas는 대조를 나타내어 뜻이 통하지 않는다. (B) in case of는 전치사구라 뒤에 명사가 와야 한다. (D) so as to 뒤에는 동사원형이 와야 한다.
포인트: 조건 접속사 provided that + 절', 130),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (131).', 'Questions 131-134 refer to the following e-mail.

To: Imani Hollis
From: Corvanta Software Client Services
Date: March 5
Subject: Your license renewal

Dear Ms. Hollis,

Thank you for renewing your firm''s Corvanta Planner license for another year. Your payment (131) ------- on March 3. A receipt is attached to this message.

As a returning client, you are entitled to free (132) ------- training for up to 10 employees. Sessions can be booked through the Learning Center page of our website, and your staff can join from any computer with an Internet connection.

We would also like to remind you that version 8.0 of the program will be released next month. (133) -------. Simply open the program and select "Update" when prompted.

If you have any questions, please contact (134) ------- support team at any time.

Sincerely,

Corvanta Software Client Services', 'NONE', '["is receiving", "was received", "will be received", "has received"]', 'B', 1, '[Part6-문법(시제·태·문맥)] 정답 (B). 앞 문장에서 갱신에 감사하고 바로 뒤 문장에서 영수증을 첨부했다(A receipt is attached)고 하므로 결제는 이미 끝난 일이고, payment는 받아지는 대상이므로 과거 수동 was received가 맞다. 오답: (A) is receiving, (D) has received는 능동이라 목적어가 필요하다. (C) will be received는 미래라서 영수증이 이미 발행된 상황과 맞지 않는다.
포인트: 시제·태 — 과거 수동(뒤 문장의 영수증 단서)', 131),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (132).', 'Questions 131-134 refer to the following e-mail.

To: Imani Hollis
From: Corvanta Software Client Services
Date: March 5
Subject: Your license renewal

Dear Ms. Hollis,

Thank you for renewing your firm''s Corvanta Planner license for another year. Your payment (131) ------- on March 3. A receipt is attached to this message.

As a returning client, you are entitled to free (132) ------- training for up to 10 employees. Sessions can be booked through the Learning Center page of our website, and your staff can join from any computer with an Internet connection.

We would also like to remind you that version 8.0 of the program will be released next month. (133) -------. Simply open the program and select "Update" when prompted.

If you have any questions, please contact (134) ------- support team at any time.

Sincerely,

Corvanta Software Client Services', 'NONE', '["online", "on-site", "classroom", "telephone"]', 'A', 1, '[Part6-어휘(문맥, 뒤 문장 단서)] 정답 (A). 바로 뒤 문장에서 웹사이트의 Learning Center에서 예약하고 ''인터넷에 연결된 컴퓨터 어디서나 참여할 수 있다''고 했으므로 online training이 맞다. 오답: (B) on-site(현장), (C) classroom(강의실)은 특정 장소에 모여야 하므로 any computer와 맞지 않는다. (D) telephone(전화)은 컴퓨터와 인터넷 연결로 참여한다는 설명과 다르다.
포인트: 문맥 어휘 — 뒤 문장 단서(online)', 132),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (133).', 'Questions 131-134 refer to the following e-mail.

To: Imani Hollis
From: Corvanta Software Client Services
Date: March 5
Subject: Your license renewal

Dear Ms. Hollis,

Thank you for renewing your firm''s Corvanta Planner license for another year. Your payment (131) ------- on March 3. A receipt is attached to this message.

As a returning client, you are entitled to free (132) ------- training for up to 10 employees. Sessions can be booked through the Learning Center page of our website, and your staff can join from any computer with an Internet connection.

We would also like to remind you that version 8.0 of the program will be released next month. (133) -------. Simply open the program and select "Update" when prompted.

If you have any questions, please contact (134) ------- support team at any time.

Sincerely,

Corvanta Software Client Services', 'NONE', '["Current license holders can install it at no additional cost.", "Those who installed it last year have reported no problems.", "Both of them will be mailed to your office on a disc.", "However, a release date has not yet been decided."]', 'A', 1, '[Part6-문장삽입] 정답 (A). 앞 문장의 version 8.0을 it으로 받고, 뒤 문장 ''Simply open the program and select "Update" when prompted.''가 그 설치 방법이므로 ''현재 사용권 보유자는 추가 비용 없이 설치할 수 있다''가 들어가야 한다. 오답: (B) 다음 달에 나올 판을 작년에 설치했다는 것은 시점이 맞지 않는다. (C) Both of them이 가리킬 두 대상이 앞에 없고, 디스크 우편 발송은 뒤의 설치 방법과 어긋난다. (D) 앞 문장에서 next month라고 밝혔으므로 출시일이 정해지지 않았다는 말은 모순이다.
포인트: 문장 삽입 — 지시어 it과 뒤 문장의 방법 안내', 133),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (134).', 'Questions 131-134 refer to the following e-mail.

To: Imani Hollis
From: Corvanta Software Client Services
Date: March 5
Subject: Your license renewal

Dear Ms. Hollis,

Thank you for renewing your firm''s Corvanta Planner license for another year. Your payment (131) ------- on March 3. A receipt is attached to this message.

As a returning client, you are entitled to free (132) ------- training for up to 10 employees. Sessions can be booked through the Learning Center page of our website, and your staff can join from any computer with an Internet connection.

We would also like to remind you that version 8.0 of the program will be released next month. (133) -------. Simply open the program and select "Update" when prompted.

If you have any questions, please contact (134) ------- support team at any time.

Sincerely,

Corvanta Software Client Services', 'NONE', '["your", "its", "their", "our"]', 'D', 1, '[Part6-대명사(지시 대상)] 정답 (D). 이 이메일의 발신자는 Corvanta Software Client Services이고 지원팀은 발신 회사에 속하므로, 발신자 쪽을 가리키는 our가 맞다(앞의 our website와 같은 관계). 오답: (A) your는 수신자인 Ms. Hollis 회사의 팀이 되어 문의처가 될 수 없다. (B) its는 ''그것의'', (C) their는 ''그들의'' 지원팀이라는 뜻이 되어, 문의를 받는 발신 회사 자신의 팀을 가리키지 못한다.
포인트: 소유격 — 발신자 쪽을 가리키는 our', 134),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (135).', 'Questions 135-138 refer to the following notice.

Cedar Hollow Public Library
Notice to Patrons

Beginning June 1, a valid library card (135) ------- to enter the second-floor study rooms. Patrons should scan their card at the reader beside each door. This change is intended to keep the rooms available for the people who have reserved them.

Reservations can be made up to seven days in advance at the information desk or on the library''s website. (136) -------, each patron may book no more than two hours per day. We ask that you leave promptly when your session ends so that the next group can begin on time.

Patrons who have lost their card can obtain a (137) ------- for a $3 fee. (138) -------. The new card will be ready within a few minutes.', 'NONE', '["will be required", "will require", "requiring", "has required"]', 'A', 1, '[Part6-문법(동사 자리·태)] 정답 (A). 문장에 본동사가 없으므로 정형동사가 필요하고, 주어 a valid library card는 ''요구되는'' 대상이며 뒤에 목적어가 없으므로 수동태 will be required가 맞다. Beginning June 1이라는 앞으로의 시점과도 맞는다. 오답: (B) will require, (D) has required는 능동이라 목적어가 필요하다. (C) requiring은 분사라서 본동사가 될 수 없다.
포인트: 동사 자리·태 — 미래 수동', 135),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (136).', 'Questions 135-138 refer to the following notice.

Cedar Hollow Public Library
Notice to Patrons

Beginning June 1, a valid library card (135) ------- to enter the second-floor study rooms. Patrons should scan their card at the reader beside each door. This change is intended to keep the rooms available for the people who have reserved them.

Reservations can be made up to seven days in advance at the information desk or on the library''s website. (136) -------, each patron may book no more than two hours per day. We ask that you leave promptly when your session ends so that the next group can begin on time.

Patrons who have lost their card can obtain a (137) ------- for a $3 fee. (138) -------. The new card will be ready within a few minutes.', 'NONE', '["As a result", "For example", "Otherwise", "However"]', 'D', 1, '[Part6-연결어] 정답 (D). 앞 문장은 7일 전부터 예약할 수 있다는 허용 내용이고, 빈칸 문장은 하루 두 시간까지만 예약할 수 있다는 제한이므로 대조의 However가 맞다. 오답: (A) As a result는 앞 문장이 제한의 원인이 아니므로 맞지 않는다. (B) For example은 뒤 문장이 앞 문장의 예시가 아니다. (C) Otherwise는 ''그렇지 않으면''이라는 가정으로 앞뒤 관계와 다르다.
포인트: 연결어 — 대조 However', 136),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (137).', 'Questions 135-138 refer to the following notice.

Cedar Hollow Public Library
Notice to Patrons

Beginning June 1, a valid library card (135) ------- to enter the second-floor study rooms. Patrons should scan their card at the reader beside each door. This change is intended to keep the rooms available for the people who have reserved them.

Reservations can be made up to seven days in advance at the information desk or on the library''s website. (136) -------, each patron may book no more than two hours per day. We ask that you leave promptly when your session ends so that the next group can begin on time.

Patrons who have lost their card can obtain a (137) ------- for a $3 fee. (138) -------. The new card will be ready within a few minutes.', 'NONE', '["refund", "replacement", "reservation", "reminder"]', 'B', 1, '[Part6-어휘(문맥)] 정답 (B). 카드를 분실한 이용자가 수수료를 내고 받는 것이고, 뒤에 ''The new card will be ready''라고 했으므로 ''재발급 카드(대체품)''인 replacement가 맞다. 오답: (A) refund 환불은 수수료를 내고 받는 것이 아니다. (C) reservation 예약은 앞 문단의 내용이며 카드 분실과 관계없다. (D) reminder 알림은 뒤의 the new card와 이어지지 않는다.
포인트: 문맥 어휘 — 뒤 문장 단서(replacement)', 137),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (138).', 'Questions 135-138 refer to the following notice.

Cedar Hollow Public Library
Notice to Patrons

Beginning June 1, a valid library card (135) ------- to enter the second-floor study rooms. Patrons should scan their card at the reader beside each door. This change is intended to keep the rooms available for the people who have reserved them.

Reservations can be made up to seven days in advance at the information desk or on the library''s website. (136) -------, each patron may book no more than two hours per day. We ask that you leave promptly when your session ends so that the next group can begin on time.

Patrons who have lost their card can obtain a (137) ------- for a $3 fee. (138) -------. The new card will be ready within a few minutes.', 'NONE', '["It was mailed to your home address last week.", "Simply bring photo identification to the circulation desk.", "Those who do so will no longer be able to reserve a room.", "In that case, the study rooms may be entered without one."]', 'B', 1, '[Part6-문장삽입] 정답 (B). 앞 문장이 분실 카드의 재발급을 말하고 뒤 문장이 ''The new card will be ready within a few minutes.''라고 하므로, 그 사이에는 재발급 방법(사진이 있는 신분증을 대출 창구로 가져오기)이 와야 한다. 오답: (A) 새 카드가 지난주에 이미 우송되었다면 몇 분 안에 준비된다는 뒤 문장과 시점이 어긋난다. (C) 재발급받은 사람이 예약을 못 하게 된다는 것은 논리가 맞지 않는다. (D) 첫 문단에서 카드가 있어야 들어갈 수 있다고 했으므로 카드 없이 들어간다는 말은 모순이다.
포인트: 문장 삽입 — 절차 안내와 뒤 문장(The new card) 연결', 138),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (139).', 'Questions 139-142 refer to the following article.

DUNMERE (May 4) — Hearthway Bakery, the regional chain known for its sourdough loaves, will open its 12th store next month on Tanner Street. The company (139) ------- the site of a former bookstore. Renovation of the building began last November and is now nearly complete.

According to operations director Obinna Udoka, the new store will be (140) ------- larger than the chain''s other locations. It will include a 40-seat café and a glass-walled kitchen where customers can watch the bakers at work.

Hearthway plans to hire about 25 employees for the Tanner Street store. (141) -------. Interested candidates may apply in person at any Hearthway store or through the company''s website.

Mr. Udoka said the chain is also (142) ------- sites in Starling for a possible 13th store.', 'NONE', '["will purchase", "is purchasing", "purchases", "purchased"]', 'D', 1, '[Part6-문법(시제·문맥)] 정답 (D). 바로 뒤 문장에서 건물 개조 공사가 지난 11월에 시작되어 거의 끝났다고 했으므로 부지 매입은 그보다 앞선 과거의 일이다. 따라서 과거형 purchased가 맞다. 오답: (A) will purchase는 미래, (B) is purchasing은 진행 중, (C) purchases는 현재의 반복을 나타내어 이미 공사가 거의 끝난 상황과 맞지 않는다.
포인트: 시제 — 뒤 문장으로 정하는 과거', 139),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (140).', 'Questions 139-142 refer to the following article.

DUNMERE (May 4) — Hearthway Bakery, the regional chain known for its sourdough loaves, will open its 12th store next month on Tanner Street. The company (139) ------- the site of a former bookstore. Renovation of the building began last November and is now nearly complete.

According to operations director Obinna Udoka, the new store will be (140) ------- larger than the chain''s other locations. It will include a 40-seat café and a glass-walled kitchen where customers can watch the bakers at work.

Hearthway plans to hire about 25 employees for the Tanner Street store. (141) -------. Interested candidates may apply in person at any Hearthway store or through the company''s website.

Mr. Udoka said the chain is also (142) ------- sites in Starling for a possible 13th store.', 'NONE', '["consider", "considerable", "considerably", "consideration"]', 'C', 1, '[Part6-문법(품사·비교급 앞 부사)] 정답 (C). 빈칸은 비교급 larger를 꾸며 정도를 나타내는 자리이므로 부사 considerably(상당히)가 맞다. 오답: (A) 동사는 will be 뒤 비교급 앞에 올 수 없다. (B) 형용사는 다른 형용사 larger를 꾸미지 못한다. (D) 명사는 larger의 수식어가 될 수 없다.
포인트: 품사 — 비교급 형용사 앞 부사 자리', 140),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (141).', 'Questions 139-142 refer to the following article.

DUNMERE (May 4) — Hearthway Bakery, the regional chain known for its sourdough loaves, will open its 12th store next month on Tanner Street. The company (139) ------- the site of a former bookstore. Renovation of the building began last November and is now nearly complete.

According to operations director Obinna Udoka, the new store will be (140) ------- larger than the chain''s other locations. It will include a 40-seat café and a glass-walled kitchen where customers can watch the bakers at work.

Hearthway plans to hire about 25 employees for the Tanner Street store. (141) -------. Interested candidates may apply in person at any Hearthway store or through the company''s website.

Mr. Udoka said the chain is also (142) ------- sites in Starling for a possible 13th store.', 'NONE', '["They were trained at the chain''s main bakery last year.", "All of these positions have already been filled.", "Most of the openings are for bakers and counter staff.", "That store has kept the same staff since it opened."]', 'C', 1, '[Part6-문장삽입] 정답 (C). 앞 문장이 약 25명을 채용할 계획이라고 하고 뒤 문장이 지원 방법(Interested candidates may apply …)을 안내하므로, 그 사이에는 채용 직종을 말하는 문장이 와야 한다. openings가 앞의 hire, 뒤의 candidates와 이어진다. 오답: (A) 아직 뽑지 않은 직원들이 작년에 교육받았다는 것은 시점이 맞지 않는다. (B) 자리가 모두 찼다면 뒤의 지원 안내와 모순된다. (D) 다음 달에 여는 매장이 개점 이후 같은 직원을 유지했다는 말은 성립하지 않는다.
포인트: 문장 삽입 — 채용 계획과 지원 안내 사이', 141),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (142).', 'Questions 139-142 refer to the following article.

DUNMERE (May 4) — Hearthway Bakery, the regional chain known for its sourdough loaves, will open its 12th store next month on Tanner Street. The company (139) ------- the site of a former bookstore. Renovation of the building began last November and is now nearly complete.

According to operations director Obinna Udoka, the new store will be (140) ------- larger than the chain''s other locations. It will include a 40-seat café and a glass-walled kitchen where customers can watch the bakers at work.

Hearthway plans to hire about 25 employees for the Tanner Street store. (141) -------. Interested candidates may apply in person at any Hearthway store or through the company''s website.

Mr. Udoka said the chain is also (142) ------- sites in Starling for a possible 13th store.', 'NONE', '["vacating", "evaluating", "advertising", "inheriting"]', 'B', 1, '[Part6-어휘(문맥)] 정답 (B). a possible 13th store(열 수도 있는 열세 번째 매장)를 위해 Starling의 부지들을 ''검토하고 있다''는 뜻이므로 evaluating이 맞다. 오답: (A) vacating(비우다)은 새 매장을 열려는 계획과 반대다. (C) advertising(광고하다)은 아직 정해지지 않은 매장 부지에 맞지 않는다. (D) inheriting(물려받다)은 부지를 찾는 상황과 관계없다.
포인트: 문맥 어휘 — evaluate(후보를 검토하다)', 142),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (143).', 'Questions 143-146 refer to the following memo.

To: All Warehouse Staff
From: Greta Sandoval, Operations Manager
Date: November 2
Subject: New loading dock schedule

Starting November 9, the Wexcombe depot will assign each carrier a fixed 30-minute window for pickups. Over the past few months, trucks arriving at the same time have caused long lines at the loading docks. (143) -------.

Under the new system, carriers must reserve a window at least one day in advance. Drivers who present a reservation code at the gate will be directed to a dock immediately. (144) -------, those without a code will be asked to wait until a window opens. We expect the change to (145) ------- waiting times considerably.

A training session for dock supervisors (146) ------- on November 5 at 2:00 P.M. in the break room. Attendance is mandatory, so please plan accordingly.', 'NONE', '["He has asked the carriers to send larger trucks instead.", "These lines disappeared once the windows were introduced.", "The new schedule is intended to prevent such delays.", "Therefore, carriers may now arrive whenever they wish."]', 'C', 1, '[Part6-문장삽입] 정답 (C). 앞 문장이 트럭이 한꺼번에 도착해 하역장에 긴 줄이 생겼다는 문제를 말하므로, such delays로 그 문제를 받아 새 일정의 목적을 밝히는 문장이 이어져야 한다. 오답: (A) He가 가리킬 사람이 앞에 없다. (B) 시간대 배정은 11월 9일에 시작되므로 줄이 이미 사라졌다는 과거 서술은 시점이 맞지 않는다. (D) 아무 때나 도착해도 된다는 것은 정해진 시간대를 배정한다는 내용과 반대다.
포인트: 문장 삽입 — 지시어 such delays와 새 제도의 목적', 143),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (144).', 'Questions 143-146 refer to the following memo.

To: All Warehouse Staff
From: Greta Sandoval, Operations Manager
Date: November 2
Subject: New loading dock schedule

Starting November 9, the Wexcombe depot will assign each carrier a fixed 30-minute window for pickups. Over the past few months, trucks arriving at the same time have caused long lines at the loading docks. (143) -------.

Under the new system, carriers must reserve a window at least one day in advance. Drivers who present a reservation code at the gate will be directed to a dock immediately. (144) -------, those without a code will be asked to wait until a window opens. We expect the change to (145) ------- waiting times considerably.

A training session for dock supervisors (146) ------- on November 5 at 2:00 P.M. in the break room. Attendance is mandatory, so please plan accordingly.', 'NONE', '["Similarly", "In contrast", "In particular", "As a result"]', 'B', 1, '[Part6-연결어] 정답 (B). 앞 문장은 예약 코드를 제시한 운전자는 곧바로 하역장으로 안내된다는 내용이고, 빈칸 문장은 코드가 없는 운전자는 기다려야 한다는 내용이므로 두 경우를 대조하는 In contrast가 맞다. 오답: (A) Similarly는 비슷한 내용을 덧붙일 때 쓴다. (C) In particular는 앞 내용 가운데 한 가지를 강조할 때 쓴다. (D) As a result는 코드가 없는 운전자의 대기가 앞 문장의 결과가 아니므로 맞지 않는다.
포인트: 연결어 — 대조 In contrast', 144),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (145).', 'Questions 143-146 refer to the following memo.

To: All Warehouse Staff
From: Greta Sandoval, Operations Manager
Date: November 2
Subject: New loading dock schedule

Starting November 9, the Wexcombe depot will assign each carrier a fixed 30-minute window for pickups. Over the past few months, trucks arriving at the same time have caused long lines at the loading docks. (143) -------.

Under the new system, carriers must reserve a window at least one day in advance. Drivers who present a reservation code at the gate will be directed to a dock immediately. (144) -------, those without a code will be asked to wait until a window opens. We expect the change to (145) ------- waiting times considerably.

A training session for dock supervisors (146) ------- on November 5 at 2:00 P.M. in the break room. Attendance is mandatory, so please plan accordingly.', 'NONE', '["extend", "measure", "record", "shorten"]', 'D', 1, '[Part6-어휘(문맥)] 정답 (D). 첫 문단에서 긴 줄이 문제였고 새 제도는 그것을 해결하려는 것이므로, 이 변화로 대기 시간이 크게 ''줄어들'' 것으로 기대한다는 shorten이 맞다. 오답: (A) extend(늘리다)는 제도의 목적과 반대다. (B) measure(측정하다), (C) record(기록하다)는 정도 부사 considerably와 어울리지 않고 제도의 기대 효과도 아니다.
포인트: 문맥 어휘 — 앞 문단 단서(shorten)', 145),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (146).', 'Questions 143-146 refer to the following memo.

To: All Warehouse Staff
From: Greta Sandoval, Operations Manager
Date: November 2
Subject: New loading dock schedule

Starting November 9, the Wexcombe depot will assign each carrier a fixed 30-minute window for pickups. Over the past few months, trucks arriving at the same time have caused long lines at the loading docks. (143) -------.

Under the new system, carriers must reserve a window at least one day in advance. Drivers who present a reservation code at the gate will be directed to a dock immediately. (144) -------, those without a code will be asked to wait until a window opens. We expect the change to (145) ------- waiting times considerably.

A training session for dock supervisors (146) ------- on November 5 at 2:00 P.M. in the break room. Attendance is mandatory, so please plan accordingly.', 'NONE', '["held", "was held", "will be held", "is holding"]', 'C', 1, '[Part6-문법(시제·태·문맥)] 정답 (C). 메모 작성일이 11월 2일이고 교육은 11월 5일이며, 뒤 문장에서 참석이 필수이니 일정을 조정하라고 하므로 앞으로 열릴 일이다. 교육은 ''열리는'' 대상이므로 미래 수동 will be held가 맞다. 오답: (A) held, (D) is holding은 능동이라 목적어가 필요하다. (B) was held는 과거라서 작성일보다 뒤인 날짜와 맞지 않는다.
포인트: 시제·태 — 미래 수동(작성일과 행사일 비교)', 146);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is being offered for a limited time?', 'Questions 147-148 refer to the following advertisement.

Bayline Print & Sign
14 Chandler Street, Oakhollow

Getting ready for the Founders'' Day Fair? Let us help your booth stand out!

Through August 31, order any vinyl banner and receive a matching tabletop sign at no charge. Our banners are printed on weather-resistant material and are ready for pickup within two business days. Need yours sooner? Same-day service is available for an additional $25.

Not sure about your design? Our in-house designers will always review your artwork free of charge and suggest improvements before printing.

To place an order, upload your file at www.baylineprint.com or stop by our shop. Please note that orders cannot be accepted by phone.', 'NONE', '["A lower price on same-day printing", "Free delivery to booths at the fair", "A reduced fee for design work", "A complimentary item with a purchase"]', 'D', 1, '[세부 사항] 정답 (D). ''Through August 31, order any vinyl banner and receive a matching tabletop sign at no charge''에서 8월 31일까지 현수막을 주문하면 탁상용 표지판을 무료로 준다고 했다. at no charge가 complimentary로, tabletop sign이 item으로 바뀌었다. 오답: (A) 당일 인쇄는 25달러 추가 요금이 붙는다. (B) 배송 이야기는 없고 직접 찾아가는(pickup) 방식이다. (C) 디자인 검토는 기한 없이 언제나(always) 무료다.
포인트: 세부 사항 — 광고 둘째 문단(한정 혜택과 상시 서비스 구별)', 147),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about Bayline Print & Sign?', 'Questions 147-148 refer to the following advertisement.

Bayline Print & Sign
14 Chandler Street, Oakhollow

Getting ready for the Founders'' Day Fair? Let us help your booth stand out!

Through August 31, order any vinyl banner and receive a matching tabletop sign at no charge. Our banners are printed on weather-resistant material and are ready for pickup within two business days. Need yours sooner? Same-day service is available for an additional $25.

Not sure about your design? Our in-house designers will always review your artwork free of charge and suggest improvements before printing.

To place an order, upload your file at www.baylineprint.com or stop by our shop. Please note that orders cannot be accepted by phone.', 'NONE', '["It has recently opened a second location.", "It can finish some orders the day they are placed.", "It charges customers a fee for reviewing artwork.", "It accepts banner orders placed over the telephone."]', 'B', 1, '[사실 확인] 정답 (B). ''Same-day service is available for an additional $25''에서 추가 요금을 내면 주문 당일에 완성해 준다는 것을 알 수 있다. 오답: (A) 매장은 Chandler Street 한 곳만 나온다. (C) 디자인 검토는 ''free of charge''라고 했다. (D) ''orders cannot be accepted by phone''과 반대다.
포인트: 사실 확인 — 광고 전체와 보기 대조', 148),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'Why did Mr. Lesko contact Ms. Menon?', 'Questions 149-150 refer to the following text-message chain.

Teodor Lesko (7:52 A.M.)
Divya, my 8:40 flight to Varden has been pushed back to 11:15 because of fog. I won''t make the 10:30 walk-through at the Kessick Foods plant. Can you help me sort things out?

Divya Menon (7:55 A.M.)
Of course. Should I ask Ms. Kessick''s office to reschedule?

Teodor Lesko (7:57 A.M.)
Yes, please. Any time after 2:00 would be fine. Could you also tell the car service that I now land at 12:25?

Divya Menon (8:06 A.M.)
Done. Ms. Kessick''s assistant says 3:00 is open, but the plant manager leaves at 4:00, so the walk-through can''t run long.

Teodor Lesko (8:08 A.M.)
That works. I only need about 45 minutes on the production floor. I''ll go over the contract terms with her by phone next week instead.

Divya Menon (8:09 A.M.)
I''ll send you an updated itinerary.', 'NONE', '["To cancel a visit to a client''s plant", "To ask her to book a different flight", "To report that a contract has been signed", "To get help changing his arrangements"]', 'D', 1, '[주제·목적] 정답 (D). 안개로 비행기가 늦어져 10시 30분 일정에 갈 수 없게 되자 ''Can you help me sort things out?''이라고 도움을 청하고, 일정 재조정과 차량 시간 변경(''Could you also tell the car service …'')을 부탁한다. 바뀐 일정에 맞춰 준비를 고쳐 달라는 연락이다. 오답: (A) 방문은 취소가 아니라 3시로 옮겨진다. (B) 다른 항공편 예약을 부탁하지 않았다. (C) 계약 조건은 다음 주에 전화로 논의할 예정이다.
포인트: 주제·목적 — 첫 문자의 문제 상황과 요청', 149),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'At 8:08 A.M., what does Mr. Lesko most likely mean when he writes, "That works"?', 'Questions 149-150 refer to the following text-message chain.

Teodor Lesko (7:52 A.M.)
Divya, my 8:40 flight to Varden has been pushed back to 11:15 because of fog. I won''t make the 10:30 walk-through at the Kessick Foods plant. Can you help me sort things out?

Divya Menon (7:55 A.M.)
Of course. Should I ask Ms. Kessick''s office to reschedule?

Teodor Lesko (7:57 A.M.)
Yes, please. Any time after 2:00 would be fine. Could you also tell the car service that I now land at 12:25?

Divya Menon (8:06 A.M.)
Done. Ms. Kessick''s assistant says 3:00 is open, but the plant manager leaves at 4:00, so the walk-through can''t run long.

Teodor Lesko (8:08 A.M.)
That works. I only need about 45 minutes on the production floor. I''ll go over the contract terms with her by phone next week instead.

Divya Menon (8:09 A.M.)
I''ll send you an updated itinerary.', 'NONE', '["He expects the fog to clear before noon.", "He wants the plant manager to stay later.", "He can finish the visit in the time available.", "He will discuss the contract during the visit."]', 'C', 1, '[의도 파악] 정답 (C). 바로 앞에서 3시에 시작할 수 있지만 공장장이 4시에 떠난다는 말을 듣고 ''That works''라고 한 뒤 ''I only need about 45 minutes''라고 덧붙인다. 주어진 한 시간 안에 견학을 끝낼 수 있다는 뜻이다. 오답: (A) 날씨 전망은 언급이 없다. (B) 공장장에게 더 있어 달라고 하지 않는다. (D) 계약 조건은 ''by phone next week instead''라고 했다.
포인트: 의도 파악 — 바로 앞 문자(3시 시작·4시 퇴근)와 뒤 문장', 150),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is the purpose of the e-mail?', 'Questions 151-152 refer to the following e-mail.

To: Elena Kovacs
From: Quillbook Accounting Cloud <billing@quillbookcloud.com>
Date: March 3
Subject: Your plan renews soon

Dear Ms. Kovacs,

Your Quillbook Standard plan will renew automatically on March 17, and $228 will be charged to the card on file for the coming year. No action is needed if you wish to continue.

Since you joined, we have added receipt scanning and multi-currency invoicing to the Standard plan at no extra cost. If your business has grown, you may want to consider the Team plan, which supports up to 10 users.

To change or cancel your plan, visit the Billing page of your account before the renewal date. A renewal charge can be refunded in full only within 14 days of the renewal date.

Quillbook Billing Team', 'NONE', '["To confirm that a payment has been received", "To notify a customer of an upcoming charge", "To announce an increase in a yearly fee", "To request updated credit card details"]', 'B', 1, '[주제·목적] 정답 (B). ''will renew automatically on March 17, and $228 will be charged to the card on file''에서 곧 있을 자동 갱신 결제를 미리 알리는 메일임을 알 수 있다. 오답: (A) 결제는 아직 이루어지지 않았다. (C) 요금이 오른다는 말은 없다. (D) 등록된 카드로 결제된다고 했을 뿐 카드 정보를 새로 요구하지 않는다.
포인트: 주제·목적 — 이메일 첫 문단(자동 갱신과 청구 예정)', 151),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'According to the e-mail, what has changed about the Standard plan?', 'Questions 151-152 refer to the following e-mail.

To: Elena Kovacs
From: Quillbook Accounting Cloud <billing@quillbookcloud.com>
Date: March 3
Subject: Your plan renews soon

Dear Ms. Kovacs,

Your Quillbook Standard plan will renew automatically on March 17, and $228 will be charged to the card on file for the coming year. No action is needed if you wish to continue.

Since you joined, we have added receipt scanning and multi-currency invoicing to the Standard plan at no extra cost. If your business has grown, you may want to consider the Team plan, which supports up to 10 users.

To change or cancel your plan, visit the Billing page of your account before the renewal date. A renewal charge can be refunded in full only within 14 days of the renewal date.

Quillbook Billing Team', 'NONE', '["Its annual price has gone up.", "It now supports up to 10 users.", "Additional features have been included.", "Its refund period has been shortened."]', 'C', 1, '[세부 사항] 정답 (C). ''we have added receipt scanning and multi-currency invoicing to the Standard plan at no extra cost''에서 기능이 추가되었음을 알 수 있다. added … to the plan이 Additional features have been included로 바뀌었다. 오답: (A) 연 228달러가 청구된다고 했을 뿐 요금이 올랐다는 말은 없다. (B) 10명까지 쓰는 것은 Team plan이다. (D) 환불 기간 14일이 줄었다는 말은 없다.
포인트: 세부 사항 — 이메일 둘째 문단(added → included)', 152),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is the notice mainly about?', 'Questions 153-154 refer to the following notice.

Notice to Tenants of Kestrel Tower

The building''s two passenger elevators will be modernized one at a time beginning October 6. Elevator A will be out of service from October 6 to October 17, and Elevator B from October 20 to October 31. One elevator will remain in operation at all times, but please expect longer waits, particularly between 8:00 and 9:00 A.M.

The freight elevator at the rear of the building may be used for deliveries and for moving bulky items. To reserve it for furniture or other large equipment, contact the management office at least two days ahead.

Work will take place between 7:00 P.M. and 5:00 A.M. to limit noise during business hours. We appreciate your patience.

Kestrel Tower Management Office', 'NONE', '["A change in the ownership of a building", "New hours for a management office", "Scheduled work on building equipment", "The opening of a new rear entrance"]', 'C', 1, '[주제·목적] 정답 (C). ''The building''s two passenger elevators will be modernized one at a time beginning October 6''에서 엘리베이터를 한 대씩 새 설비로 개량하는(modernize) 공사 일정을 알리는 글임을 알 수 있다. elevators가 building equipment로, modernized가 work로 바뀌었다. 오답: (A) 소유주 변경, (B) 관리사무소 운영 시간, (D) 새 출입구 이야기는 없다.
포인트: 주제·목적 — 공지 첫 문장(elevators → building equipment)', 153),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'According to the notice, what should tenants do before moving large equipment?', 'Questions 153-154 refer to the following notice.

Notice to Tenants of Kestrel Tower

The building''s two passenger elevators will be modernized one at a time beginning October 6. Elevator A will be out of service from October 6 to October 17, and Elevator B from October 20 to October 31. One elevator will remain in operation at all times, but please expect longer waits, particularly between 8:00 and 9:00 A.M.

The freight elevator at the rear of the building may be used for deliveries and for moving bulky items. To reserve it for furniture or other large equipment, contact the management office at least two days ahead.

Work will take place between 7:00 P.M. and 5:00 A.M. to limit noise during business hours. We appreciate your patience.

Kestrel Tower Management Office', 'NONE', '["Reserve Elevator B for the evening", "Wait until the work ends on October 31", "Hire an approved moving company", "Contact the management office in advance"]', 'D', 1, '[세부 사항] 정답 (D). ''To reserve it for furniture or other large equipment, contact the management office at least two days ahead''에서 큰 물건을 옮기려면 이틀 전까지 관리사무소에 연락해야 한다. at least two days ahead가 in advance로 바뀌었다. 오답: (A) 예약 대상은 화물용 엘리베이터다. (B) 공사가 끝날 때까지 기다리라는 말은 없다. (C) 이사 업체 이야기는 없다.
포인트: 세부 사항 — 공지 둘째 문단(at least two days ahead → in advance)', 154),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is the purpose of the memo?', 'Questions 155-157 refer to the following memo.

To: All Elmstead staff
From: Helga Nystrom, Facilities Manager
Date: May 6
Subject: Move to Beacon House

As announced last month, our Elmstead office will relocate from Corbel Lane to the fourth floor of Beacon House. — [1] —. The Corbel Lane office will close at noon on May 22, and we will open at the new address on May 26.

Packing crates will be delivered to each department on May 15. Please label every crate with your name and your new desk number, which you can find on the floor plan attached to this memo. — [2] —. Computers and monitors will be packed and reconnected by the IT team, so there is no need to unplug anything yourself.

Beacon House has a limited number of parking spaces, and these will be assigned to employees who carpool. — [3] —. The Route 12 bus stops directly in front of the building, and covered bicycle racks are available by the east entrance.

New access cards will be handed out on the morning of May 26. — [4] —. If you have any questions, contact me at extension 263.', 'NONE', '["To give details about an upcoming office move", "To introduce a newly hired facilities manager", "To invite employees to tour a new building", "To ask staff to vote on a possible location"]', 'A', 1, '[주제·목적] 정답 (A). ''our Elmstead office will relocate from Corbel Lane to … Beacon House''로 시작해 폐쇄일·상자 배부·주차·출입 카드 등 이전 관련 세부 사항을 안내한다. 오답: (B) Nystrom 씨는 발신자일 뿐 새로 온 사람이라는 말이 없다. (C) 견학 초대가 아니다. (D) 장소는 이미 정해졌다(As announced last month).
포인트: 주제·목적 — 메모 제목과 첫 문단', 155),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What are employees asked to do?', 'Questions 155-157 refer to the following memo.

To: All Elmstead staff
From: Helga Nystrom, Facilities Manager
Date: May 6
Subject: Move to Beacon House

As announced last month, our Elmstead office will relocate from Corbel Lane to the fourth floor of Beacon House. — [1] —. The Corbel Lane office will close at noon on May 22, and we will open at the new address on May 26.

Packing crates will be delivered to each department on May 15. Please label every crate with your name and your new desk number, which you can find on the floor plan attached to this memo. — [2] —. Computers and monitors will be packed and reconnected by the IT team, so there is no need to unplug anything yourself.

Beacon House has a limited number of parking spaces, and these will be assigned to employees who carpool. — [3] —. The Route 12 bus stops directly in front of the building, and covered bicycle racks are available by the east entrance.

New access cards will be handed out on the morning of May 26. — [4] —. If you have any questions, contact me at extension 263.', 'NONE', '["Mark containers with their seating information", "Unplug their computers before May 22", "Collect packing crates from the fourth floor", "Return their old access cards to Ms. Nystrom"]', 'A', 1, '[세부 사항] 정답 (A). ''Please label every crate with your name and your new desk number''에서 상자에 이름과 새 자리 번호를 적어 달라고 한다. label이 Mark로, crate가 containers로, desk number가 seating information으로 바뀌었다. 오답: (B) ''no need to unplug anything yourself''와 반대다. (C) 상자는 각 부서로 배달된다. (D) 기존 카드 반납 이야기는 없다.
포인트: 세부 사항 — 메모 둘째 문단(label → mark, crate → container)', 156),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'In which of the positions marked [1], [2], [3], and [4] does the following sentence best belong? "Everyone else is encouraged to use public transportation."', 'Questions 155-157 refer to the following memo.

To: All Elmstead staff
From: Helga Nystrom, Facilities Manager
Date: May 6
Subject: Move to Beacon House

As announced last month, our Elmstead office will relocate from Corbel Lane to the fourth floor of Beacon House. — [1] —. The Corbel Lane office will close at noon on May 22, and we will open at the new address on May 26.

Packing crates will be delivered to each department on May 15. Please label every crate with your name and your new desk number, which you can find on the floor plan attached to this memo. — [2] —. Computers and monitors will be packed and reconnected by the IT team, so there is no need to unplug anything yourself.

Beacon House has a limited number of parking spaces, and these will be assigned to employees who carpool. — [3] —. The Route 12 bus stops directly in front of the building, and covered bicycle racks are available by the east entrance.

New access cards will be handed out on the morning of May 26. — [4] —. If you have any questions, contact me at extension 263.', 'NONE', '["[1]", "[2]", "[3]", "[4]"]', 'C', 1, '[문장 삽입] 정답 (C). ''Everyone else''는 앞 문장의 ''employees who carpool''을 뺀 나머지 직원을 가리키고, 바로 뒤의 ''The Route 12 bus stops directly in front of the building''이 대중교통의 예를 이어 준다. 오답: (A) [1] 앞은 이전 사실, (B) [2] 앞은 상자 표시, (D) [4] 앞은 출입 카드 이야기라서 ''Everyone else''가 가리킬 대조 대상이 없다.
포인트: 문장 삽입 — Everyone else의 대조 대상과 뒤 문장', 157),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is the purpose of the information?', 'Questions 158-160 refer to the following information.

Travel Guidelines for Employees of Sabel Instruments
(effective July 1)

Booking. All flights, rail tickets, and hotel rooms must be reserved through TripLedger, the company''s travel portal. Reservations made on other websites will not be reimbursed unless a department head has given written approval beforehand.

Meals. Employees traveling overnight receive a daily meal allowance of $55. Receipts for meals are not required. The allowance does not apply on days when meals are provided by a client or a conference organizer.

Ground transportation. Taxi and ride-share fares are reimbursed for travel between airports, hotels, and client sites. Employees who drive their own vehicles are reimbursed at $0.60 per kilometer.

Expense reports. Submit your report, together with receipts for lodging and transportation, within 10 business days of your return. Reports submitted late must be signed by your department head and may take up to six weeks to process. Approved amounts are normally included in your next paycheck.

Questions should be directed to the Finance Office at extension 220.', 'NONE', '["To advertise a new travel booking website", "To announce an upcoming industry conference", "To introduce the staff of the Finance Office", "To explain rules for business travel costs"]', 'D', 1, '[주제·목적] 정답 (D). 제목 ''Travel Guidelines for Employees''와 예약·식비·교통·경비 보고서 항목에서 출장 경비 처리 규정을 설명하는 글임을 알 수 있다. Guidelines가 rules로 바뀌었다. 오답: (A) TripLedger는 사내 예약 창구로 언급될 뿐 광고가 아니다. (B) conference는 식사를 제공하는 주최 측(conference organizer)으로만 나오며 행사 개최 안내가 아니다. (C) 재무팀은 문의처로만 나온다.
포인트: 주제·목적 — 안내문 제목과 소제목', 158),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'For which expense are receipts NOT required?', 'Questions 158-160 refer to the following information.

Travel Guidelines for Employees of Sabel Instruments
(effective July 1)

Booking. All flights, rail tickets, and hotel rooms must be reserved through TripLedger, the company''s travel portal. Reservations made on other websites will not be reimbursed unless a department head has given written approval beforehand.

Meals. Employees traveling overnight receive a daily meal allowance of $55. Receipts for meals are not required. The allowance does not apply on days when meals are provided by a client or a conference organizer.

Ground transportation. Taxi and ride-share fares are reimbursed for travel between airports, hotels, and client sites. Employees who drive their own vehicles are reimbursed at $0.60 per kilometer.

Expense reports. Submit your report, together with receipts for lodging and transportation, within 10 business days of your return. Reports submitted late must be signed by your department head and may take up to six weeks to process. Approved amounts are normally included in your next paycheck.

Questions should be directed to the Finance Office at extension 220.', 'NONE', '["Hotel stays", "Taxi rides", "Meals", "Rail tickets"]', 'C', 1, '[사실 확인(NOT)] 정답 (C). ''Receipts for meals are not required''라고 했다. 식비는 하루 55달러 정액 수당이기 때문이다. 오답: (A) 숙박(lodging)과 (B)·(D) 교통(transportation)은 ''together with receipts for lodging and transportation''에서 영수증을 내야 하는 항목이다.
포인트: 사실 확인(NOT) — Meals 항목과 Expense reports 항목 대조', 159),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about expense reports that are submitted late?', 'Questions 158-160 refer to the following information.

Travel Guidelines for Employees of Sabel Instruments
(effective July 1)

Booking. All flights, rail tickets, and hotel rooms must be reserved through TripLedger, the company''s travel portal. Reservations made on other websites will not be reimbursed unless a department head has given written approval beforehand.

Meals. Employees traveling overnight receive a daily meal allowance of $55. Receipts for meals are not required. The allowance does not apply on days when meals are provided by a client or a conference organizer.

Ground transportation. Taxi and ride-share fares are reimbursed for travel between airports, hotels, and client sites. Employees who drive their own vehicles are reimbursed at $0.60 per kilometer.

Expense reports. Submit your report, together with receipts for lodging and transportation, within 10 business days of your return. Reports submitted late must be signed by your department head and may take up to six weeks to process. Approved amounts are normally included in your next paycheck.

Questions should be directed to the Finance Office at extension 220.', 'NONE', '["They need a manager''s signature.", "They will not be accepted.", "They must be sent through TripLedger.", "They are paid within 10 business days."]', 'A', 1, '[사실 확인] 정답 (A). ''Reports submitted late must be signed by your department head''에서 늦게 낸 보고서는 부서장 서명이 필요하다. department head가 manager로 바뀌었다. 오답: (B) 처리에 최대 6주가 걸릴 뿐 거부되지는 않는다. (C) TripLedger는 예약용이다. (D) 10 business days는 제출 기한이지 지급 기간이 아니다.
포인트: 사실 확인 — Expense reports 항목(department head → manager)', 160),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'Why was the memo written?', 'Questions 161-163 refer to the following memo.

To: Customer Support Department
From: Rafaela Quiroga, Training Coordinator
Date: January 9
Subject: RelayDesk training

Our new call-handling software, RelayDesk, goes live on February 2. To prepare, every member of the department must complete one two-hour training session before that date. The session dates are listed at the bottom of this memo.

Each session is limited to 12 people. Sign up on the department intranet page by January 15. Team leaders, please make sure that no more than half of your team attends the same session so that the phones remain staffed.

The sessions will be led by Ms. Sabine Kraus of RelayDesk, who will guide participants through practice calls. Employees at the Kingsmoor office may join the January 27 session by video link; no other session will be broadcast.

A short online quiz will follow each session. Your results will not affect your performance review; they simply help us identify topics that need further explanation.

Sessions (Training Room B)
January 20 — 9:00 to 11:00 A.M.
January 22 — 2:00 to 4:00 P.M.
January 27 — 9:00 to 11:00 A.M.
January 29 — 2:00 to 4:00 P.M.', 'NONE', '["To introduce a new member of the department", "To report the results of an online quiz", "To request opinions about a software product", "To announce required training on a new system"]', 'D', 1, '[주제·목적] 정답 (D). ''every member of the department must complete one two-hour training session''에서 새 소프트웨어 RelayDesk에 대한 필수 교육을 알리는 글임을 알 수 있다. must complete가 required로, software가 system으로 바뀌었다. 오답: (A) 새 직원 소개가 아니다. (B) 퀴즈는 교육 뒤에 치를 예정이다. (C) 의견을 묻지 않는다.
포인트: 주제·목적 — 메모 첫 문단(must complete → required)', 161),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is different about the January 27 session?', 'Questions 161-163 refer to the following memo.

To: Customer Support Department
From: Rafaela Quiroga, Training Coordinator
Date: January 9
Subject: RelayDesk training

Our new call-handling software, RelayDesk, goes live on February 2. To prepare, every member of the department must complete one two-hour training session before that date. The session dates are listed at the bottom of this memo.

Each session is limited to 12 people. Sign up on the department intranet page by January 15. Team leaders, please make sure that no more than half of your team attends the same session so that the phones remain staffed.

The sessions will be led by Ms. Sabine Kraus of RelayDesk, who will guide participants through practice calls. Employees at the Kingsmoor office may join the January 27 session by video link; no other session will be broadcast.

A short online quiz will follow each session. Your results will not affect your performance review; they simply help us identify topics that need further explanation.

Sessions (Training Room B)
January 20 — 9:00 to 11:00 A.M.
January 22 — 2:00 to 4:00 P.M.
January 27 — 9:00 to 11:00 A.M.
January 29 — 2:00 to 4:00 P.M.', 'NONE', '["It will be led by a different instructor.", "It can be joined from another location.", "It is reserved for team leaders only.", "It will be held in a larger room."]', 'B', 1, '[세부 사항] 정답 (B). ''Employees at the Kingsmoor office may join the January 27 session by video link; no other session will be broadcast''에서 이 회차만 다른 사무실에서 화상으로 참여할 수 있다. by video link가 from another location으로 바뀌었다. 오답: (A) 모든 회차를 Kraus 씨가 진행한다. (C) 팀장 전용이라는 말은 없다. (D) 장소는 모두 Training Room B다.
포인트: 세부 사항 — 메모 셋째 문단(by video link → from another location)', 162),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'The word "follow" in paragraph 4, line 1, is closest in meaning to', 'Questions 161-163 refer to the following memo.

To: Customer Support Department
From: Rafaela Quiroga, Training Coordinator
Date: January 9
Subject: RelayDesk training

Our new call-handling software, RelayDesk, goes live on February 2. To prepare, every member of the department must complete one two-hour training session before that date. The session dates are listed at the bottom of this memo.

Each session is limited to 12 people. Sign up on the department intranet page by January 15. Team leaders, please make sure that no more than half of your team attends the same session so that the phones remain staffed.

The sessions will be led by Ms. Sabine Kraus of RelayDesk, who will guide participants through practice calls. Employees at the Kingsmoor office may join the January 27 session by video link; no other session will be broadcast.

A short online quiz will follow each session. Your results will not affect your performance review; they simply help us identify topics that need further explanation.

Sessions (Training Room B)
January 20 — 9:00 to 11:00 A.M.
January 22 — 2:00 to 4:00 P.M.
January 27 — 9:00 to 11:00 A.M.
January 29 — 2:00 to 4:00 P.M.', 'NONE', '["obey", "pursue", "come after", "understand"]', 'C', 1, '[동의어] 정답 (C). ''A short online quiz will follow each session''은 교육이 끝난 뒤에 퀴즈가 이어진다는 뜻이므로 come after(뒤에 오다)가 맞다. 오답: (A) obey는 규칙·지시를 따르다, (B) pursue는 뒤쫓다·추구하다, (D) understand는 이해하다로, follow의 다른 뜻이지만 이 문장에는 맞지 않는다.
포인트: 동의어 — follow(뒤에 오다)의 문맥상 뜻', 163),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'Why does Ms. Farouk start the discussion?', 'Questions 164-167 refer to the following online chat discussion.

Leila Farouk (10:02 A.M.)
Hi, Dominic and Emi. Facilities just told me the ceiling repairs in the main conference room won''t be finished until the 14th. Our quarterly sales meeting is on the 12th.

Dominic Vandersteen (10:04 A.M.)
That''s a problem. We have 40 people coming, including the regional managers from Pembry.

Leila Farouk (10:05 A.M.)
The training room on the second floor is free that day, but it seats only 28.

Emi Tanabe (10:07 A.M.)
What about the Seabrook Hotel across the street? We held the awards dinner there last spring. Their Lakeview Room holds 60.

Dominic Vandersteen (10:08 A.M.)
Can we afford it? The budget for this meeting covers lunch and printing, nothing more.

Emi Tanabe (10:11 A.M.)
I just called. If we order lunch through their catering department, they''ll waive the room charge. Their lunch is $22 a person; our usual caterer charges $20.

Dominic Vandersteen (10:12 A.M.)
I can live with that. Leila, can you cancel our regular caterer?

Leila Farouk (10:13 A.M.)
Will do. I''ll also send everyone an update with the new location.

Emi Tanabe (10:14 A.M.)
And I''ll ask the hotel to hold the room and e-mail us an agreement to sign. They need it back by the 5th.

Dominic Vandersteen (10:15 A.M.)
Perfect. Forward it to me when it arrives, and I''ll sign it today.', 'NONE', '["To announce that a meeting has been postponed", "To request more money for a quarterly event", "To report that a room will not be available", "To invite regional managers to a sales meeting"]', 'C', 1, '[주제·목적] 정답 (C). ''the ceiling repairs in the main conference room won''t be finished until the 14th. Our quarterly sales meeting is on the 12th''에서 회의 날에 회의실을 쓸 수 없다는 사실을 알리려고 대화를 시작했다. 오답: (A) 회의 날짜는 그대로이고 장소만 바뀐다. (B) 예산 증액을 요청하지 않는다. (D) 지역 관리자들은 이미 참석 예정이다.
포인트: 주제·목적 — 첫 메시지의 문제 상황', 164),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the Seabrook Hotel?', 'Questions 164-167 refer to the following online chat discussion.

Leila Farouk (10:02 A.M.)
Hi, Dominic and Emi. Facilities just told me the ceiling repairs in the main conference room won''t be finished until the 14th. Our quarterly sales meeting is on the 12th.

Dominic Vandersteen (10:04 A.M.)
That''s a problem. We have 40 people coming, including the regional managers from Pembry.

Leila Farouk (10:05 A.M.)
The training room on the second floor is free that day, but it seats only 28.

Emi Tanabe (10:07 A.M.)
What about the Seabrook Hotel across the street? We held the awards dinner there last spring. Their Lakeview Room holds 60.

Dominic Vandersteen (10:08 A.M.)
Can we afford it? The budget for this meeting covers lunch and printing, nothing more.

Emi Tanabe (10:11 A.M.)
I just called. If we order lunch through their catering department, they''ll waive the room charge. Their lunch is $22 a person; our usual caterer charges $20.

Dominic Vandersteen (10:12 A.M.)
I can live with that. Leila, can you cancel our regular caterer?

Leila Farouk (10:13 A.M.)
Will do. I''ll also send everyone an update with the new location.

Emi Tanabe (10:14 A.M.)
And I''ll ask the hotel to hold the room and e-mail us an agreement to sign. They need it back by the 5th.

Dominic Vandersteen (10:15 A.M.)
Perfect. Forward it to me when it arrives, and I''ll sign it today.', 'NONE', '["It has hosted a company event before.", "It recently renovated its Lakeview Room.", "It is located in Pembry.", "It charges less for lunch than other caterers."]', 'A', 1, '[추론] 정답 (A). ''We held the awards dinner there last spring''에서 회사가 지난봄 그 호텔에서 시상식 만찬을 열었음을 알 수 있다. 오답: (B) 보수 공사 중인 곳은 회사 회의실이다. (C) 호텔은 사무실 길 건너에 있고, Pembry는 관리자들이 오는 곳이다. (D) 호텔 점심은 22달러로 기존 업체(20달러)보다 비싸다.
포인트: 추론 — 10:07 A.M. 메시지(지난봄 행사 개최)', 165),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'At 10:12 A.M., what does Mr. Vandersteen most likely mean when he writes, "I can live with that"?', 'Questions 164-167 refer to the following online chat discussion.

Leila Farouk (10:02 A.M.)
Hi, Dominic and Emi. Facilities just told me the ceiling repairs in the main conference room won''t be finished until the 14th. Our quarterly sales meeting is on the 12th.

Dominic Vandersteen (10:04 A.M.)
That''s a problem. We have 40 people coming, including the regional managers from Pembry.

Leila Farouk (10:05 A.M.)
The training room on the second floor is free that day, but it seats only 28.

Emi Tanabe (10:07 A.M.)
What about the Seabrook Hotel across the street? We held the awards dinner there last spring. Their Lakeview Room holds 60.

Dominic Vandersteen (10:08 A.M.)
Can we afford it? The budget for this meeting covers lunch and printing, nothing more.

Emi Tanabe (10:11 A.M.)
I just called. If we order lunch through their catering department, they''ll waive the room charge. Their lunch is $22 a person; our usual caterer charges $20.

Dominic Vandersteen (10:12 A.M.)
I can live with that. Leila, can you cancel our regular caterer?

Leila Farouk (10:13 A.M.)
Will do. I''ll also send everyone an update with the new location.

Emi Tanabe (10:14 A.M.)
And I''ll ask the hotel to hold the room and e-mail us an agreement to sign. They need it back by the 5th.

Dominic Vandersteen (10:15 A.M.)
Perfect. Forward it to me when it arrives, and I''ll sign it today.', 'NONE', '["He lives a short distance from the hotel.", "He would rather keep the usual caterer.", "He will pay the room charge himself.", "He considers the extra cost acceptable."]', 'D', 1, '[의도 파악] 정답 (D). 예산을 걱정하던 Vandersteen 씨가 ''대관료 면제, 점심은 1인당 22달러(기존 20달러)''라는 말을 듣고 한 말이며, 곧바로 기존 업체 취소를 지시한다. 조금 더 드는 비용은 받아들일 만하다는 뜻이다. 오답: (A) live를 ''살다''로 옮긴 함정이다. (B) 기존 업체를 취소하라고 한다. (C) 대관료는 면제된다.
포인트: 의도 파악 — 바로 앞 메시지(대관료 면제·점심값 차이)와 뒤 지시', 166);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What will Ms. Tanabe most likely do next?', 'Questions 164-167 refer to the following online chat discussion.

Leila Farouk (10:02 A.M.)
Hi, Dominic and Emi. Facilities just told me the ceiling repairs in the main conference room won''t be finished until the 14th. Our quarterly sales meeting is on the 12th.

Dominic Vandersteen (10:04 A.M.)
That''s a problem. We have 40 people coming, including the regional managers from Pembry.

Leila Farouk (10:05 A.M.)
The training room on the second floor is free that day, but it seats only 28.

Emi Tanabe (10:07 A.M.)
What about the Seabrook Hotel across the street? We held the awards dinner there last spring. Their Lakeview Room holds 60.

Dominic Vandersteen (10:08 A.M.)
Can we afford it? The budget for this meeting covers lunch and printing, nothing more.

Emi Tanabe (10:11 A.M.)
I just called. If we order lunch through their catering department, they''ll waive the room charge. Their lunch is $22 a person; our usual caterer charges $20.

Dominic Vandersteen (10:12 A.M.)
I can live with that. Leila, can you cancel our regular caterer?

Leila Farouk (10:13 A.M.)
Will do. I''ll also send everyone an update with the new location.

Emi Tanabe (10:14 A.M.)
And I''ll ask the hotel to hold the room and e-mail us an agreement to sign. They need it back by the 5th.

Dominic Vandersteen (10:15 A.M.)
Perfect. Forward it to me when it arrives, and I''ll sign it today.', 'NONE', '["Cancel a catering order", "Notify the meeting attendees", "Request a document from the hotel", "Sign a rental agreement"]', 'C', 1, '[추론] 정답 (C). Tanabe 씨는 ''I''ll ask the hotel to hold the room and e-mail us an agreement to sign''이라고 했다. agreement가 document로 바뀌었다. 오답: (A) 기존 업체 취소와 (B) 참석자 공지는 Farouk 씨가 맡았다. (D) 서명은 Vandersteen 씨가 하겠다고 했다.
포인트: 추론(다음 행동) — 화자별로 맡은 일 구별', 167),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'Where should a staff member meet Mr. Boateng?', 'Questions 168-171 refer to the following letter.

Clearwell Hearing Services
58 Tannery Row

May 6

Ms. Adaeze Obi
Safety Coordinator, Carrow Bottling Company
210 Hazelmere Avenue

Dear Ms. Obi,

Thank you for choosing Clearwell to carry out this year''s hearing tests for the production staff of Carrow Bottling Company at your plant on May 27. — [1] —. I am writing to confirm the arrangements we discussed by phone.

Testing will run from 8:30 A.M. to 12:30 P.M., which allows about 10 minutes for each of your 22 employees. Please send me a list of their names and job titles by May 20 so that we can prepare a schedule. — [2] —.

My assistant, Jonas Boateng, will arrive at 7:45 A.M. to set up. Because our portable testing booth is heavy, he will need to use the freight elevator. Please have someone meet him at the service entrance on Quenby Lane rather than in the main lobby. — [3] —.

Your deposit of $150 was received on May 2. Should you need to cancel, it can be refunded in full until May 13. Also, because your plant is within the city limits, we will waive our usual $60 travel fee.

Within a week of the visit, I will send you a link to a secure website where the test results will be posted. The results will remain available there until June 30, and each employee may download a copy of his or her own report at no extra cost. — [4] —.

Sincerely,

Corinne Field
Clearwell Hearing Services', 'NONE', '["In the main lobby", "By the freight elevator", "At an entrance on Quenby Lane", "At the Clearwell office on Tannery Row"]', 'C', 1, '[세부 사항] 정답 (C). ''Please have someone meet him at the service entrance on Quenby Lane rather than in the main lobby''에서 조수를 만날 곳은 Quenby Lane 쪽 업무용 출입구다. 오답: (A) main lobby는 rather than으로 배제된 곳이다. (B) 화물용 엘리베이터는 만난 뒤 장비를 옮길 때 쓴다. (D) Tannery Row는 검사 업체의 주소이고 검사는 고객의 공장(at your plant)에서 한다.
포인트: 세부 사항(만날 장소) — 편지 셋째 문단, 업체 주소·로비와 구별', 168),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What does Ms. Field indicate about the travel fee?', 'Questions 168-171 refer to the following letter.

Clearwell Hearing Services
58 Tannery Row

May 6

Ms. Adaeze Obi
Safety Coordinator, Carrow Bottling Company
210 Hazelmere Avenue

Dear Ms. Obi,

Thank you for choosing Clearwell to carry out this year''s hearing tests for the production staff of Carrow Bottling Company at your plant on May 27. — [1] —. I am writing to confirm the arrangements we discussed by phone.

Testing will run from 8:30 A.M. to 12:30 P.M., which allows about 10 minutes for each of your 22 employees. Please send me a list of their names and job titles by May 20 so that we can prepare a schedule. — [2] —.

My assistant, Jonas Boateng, will arrive at 7:45 A.M. to set up. Because our portable testing booth is heavy, he will need to use the freight elevator. Please have someone meet him at the service entrance on Quenby Lane rather than in the main lobby. — [3] —.

Your deposit of $150 was received on May 2. Should you need to cancel, it can be refunded in full until May 13. Also, because your plant is within the city limits, we will waive our usual $60 travel fee.

Within a week of the visit, I will send you a link to a secure website where the test results will be posted. The results will remain available there until June 30, and each employee may download a copy of his or her own report at no extra cost. — [4] —.

Sincerely,

Corinne Field
Clearwell Hearing Services', 'NONE', '["Ms. Obi''s company will not be charged for it.", "It will be returned if the visit is canceled.", "It was paid together with the deposit on May 2.", "It applies to clients within the city limits."]', 'A', 1, '[세부 사항] 정답 (A). ''we will waive our usual $60 travel fee''의 waive는 요금을 처음부터 받지 않는다(면제)는 뜻이다. 오답: (B) 취소하면 돌려받는(refunded) 것은 이미 낸 계약금 150달러다. 면제와 환불을 구별한다. (C) 5월 2일에 받은 것은 계약금뿐이다. (D) 공장이 시 경계 안에 있어서 출장비를 받지 않는 것이므로 반대다.
포인트: 세부 사항 — 편지 넷째 문단, waive(면제)와 refund(환불) 구별', 169),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about the test results?', 'Questions 168-171 refer to the following letter.

Clearwell Hearing Services
58 Tannery Row

May 6

Ms. Adaeze Obi
Safety Coordinator, Carrow Bottling Company
210 Hazelmere Avenue

Dear Ms. Obi,

Thank you for choosing Clearwell to carry out this year''s hearing tests for the production staff of Carrow Bottling Company at your plant on May 27. — [1] —. I am writing to confirm the arrangements we discussed by phone.

Testing will run from 8:30 A.M. to 12:30 P.M., which allows about 10 minutes for each of your 22 employees. Please send me a list of their names and job titles by May 20 so that we can prepare a schedule. — [2] —.

My assistant, Jonas Boateng, will arrive at 7:45 A.M. to set up. Because our portable testing booth is heavy, he will need to use the freight elevator. Please have someone meet him at the service entrance on Quenby Lane rather than in the main lobby. — [3] —.

Your deposit of $150 was received on May 2. Should you need to cancel, it can be refunded in full until May 13. Also, because your plant is within the city limits, we will waive our usual $60 travel fee.

Within a week of the visit, I will send you a link to a secure website where the test results will be posted. The results will remain available there until June 30, and each employee may download a copy of his or her own report at no extra cost. — [4] —.

Sincerely,

Corinne Field
Clearwell Hearing Services', 'NONE', '["They will become available on June 30.", "They can be viewed through the end of June.", "They may be downloaded for a small fee.", "They will be ready one week before the visit."]', 'B', 1, '[사실 확인] 정답 (B). ''The results will remain available there until June 30''의 until은 그날까지 상태가 이어진다는 뜻이므로 6월 말까지 볼 수 있다. 오답: (A) June 30은 올라오는 날이 아니라 열람이 끝나는 날이다(완료 기한 by와 구별). (C) 내려받는 데 추가 비용이 없다(''at no extra cost''). (D) 링크는 방문 뒤 일주일 안에(''Within a week of the visit'') 보낸다.
포인트: 사실 확인 — 편지 마지막 문단, until(지속의 끝)과 by(완료 기한) 구별', 170),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'In which of the positions marked [1], [2], [3], and [4] does the following sentence best belong? "We have set aside the entire morning of that day for your group."', 'Questions 168-171 refer to the following letter.

Clearwell Hearing Services
58 Tannery Row

May 6

Ms. Adaeze Obi
Safety Coordinator, Carrow Bottling Company
210 Hazelmere Avenue

Dear Ms. Obi,

Thank you for choosing Clearwell to carry out this year''s hearing tests for the production staff of Carrow Bottling Company at your plant on May 27. — [1] —. I am writing to confirm the arrangements we discussed by phone.

Testing will run from 8:30 A.M. to 12:30 P.M., which allows about 10 minutes for each of your 22 employees. Please send me a list of their names and job titles by May 20 so that we can prepare a schedule. — [2] —.

My assistant, Jonas Boateng, will arrive at 7:45 A.M. to set up. Because our portable testing booth is heavy, he will need to use the freight elevator. Please have someone meet him at the service entrance on Quenby Lane rather than in the main lobby. — [3] —.

Your deposit of $150 was received on May 2. Should you need to cancel, it can be refunded in full until May 13. Also, because your plant is within the city limits, we will waive our usual $60 travel fee.

Within a week of the visit, I will send you a link to a secure website where the test results will be posted. The results will remain available there until June 30, and each employee may download a copy of his or her own report at no extra cost. — [4] —.

Sincerely,

Corinne Field
Clearwell Hearing Services', 'NONE', '["[1]", "[2]", "[3]", "[4]"]', 'A', 1, '[문장 삽입] 정답 (A). ''that day''는 바로 앞 문장의 ''May 27''을 가리키고, 다음 문단이 그날 오전의 구체적인 시간(''8:30 A.M. to 12:30 P.M.'')을 이어서 알려 준다. 오답: (B) [2] 앞의 날짜는 명단 제출 기한(May 20)이다. (C) [3] 앞은 만날 장소 이야기다. (D) [4] 앞의 June 30은 결과 열람이 끝나는 날이다.
포인트: 문장 삽입 — 지시어 that day가 가리키는 날짜', 171),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is the article mainly about?', 'Questions 172-175 refer to the following article.

Corvane Logistics to Open Alderpoint Center

ALDERPOINT (April 14) — Corvane Logistics announced yesterday that it will open a distribution center on Millbrook Road in Alderpoint this fall. The 18,000-square-meter building, formerly a furniture warehouse, is being fitted with automated sorting equipment and is expected to begin operations on October 1.

Corvane currently handles all of its regional shipments from a single site near the port in Port Ansel, where the company is headquartered. "That building has served us well for 15 years, but we have simply outgrown it," said Chief Operating Officer Hedda Brenner. "The Alderpoint center will let us reach customers in the northern counties a full day sooner."

The company plans to fill about 120 positions at the new center, including forklift operators, inventory clerks, and shift supervisors. Previous warehouse experience is not required for most roles, as new hires will receive three weeks of paid training. A job fair will be held at the Alderpoint Community Hall on June 20.

Local officials welcomed the news. Mayor Wren Thackery noted that the project qualified for the city''s tax incentive for companies that reuse vacant commercial buildings. The Port Ansel site will remain open, and no jobs there will be affected, the company said.', 'NONE', '["The merger of two shipping companies", "The closing of a furniture warehouse", "A company''s expansion into another city", "A change in a firm''s senior management"]', 'C', 1, '[주제·목적] 정답 (C). Port Ansel에 본사가 있는 Corvane이 ''will open a distribution center … in Alderpoint''라고 했으므로 다른 도시로 사업을 넓힌다는 기사다. 오답: (A) 합병 이야기는 없다. (B) 가구 창고는 건물의 이전 용도일 뿐이다. (D) Brenner 씨는 발언자로 나올 뿐 경영진 교체가 아니다.
포인트: 주제·목적 — 기사 제목과 첫 문단', 172),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'The word "handles" in paragraph 2, line 1, is closest in meaning to', 'Questions 172-175 refer to the following article.

Corvane Logistics to Open Alderpoint Center

ALDERPOINT (April 14) — Corvane Logistics announced yesterday that it will open a distribution center on Millbrook Road in Alderpoint this fall. The 18,000-square-meter building, formerly a furniture warehouse, is being fitted with automated sorting equipment and is expected to begin operations on October 1.

Corvane currently handles all of its regional shipments from a single site near the port in Port Ansel, where the company is headquartered. "That building has served us well for 15 years, but we have simply outgrown it," said Chief Operating Officer Hedda Brenner. "The Alderpoint center will let us reach customers in the northern counties a full day sooner."

The company plans to fill about 120 positions at the new center, including forklift operators, inventory clerks, and shift supervisors. Previous warehouse experience is not required for most roles, as new hires will receive three weeks of paid training. A job fair will be held at the Alderpoint Community Hall on June 20.

Local officials welcomed the news. Mayor Wren Thackery noted that the project qualified for the city''s tax incentive for companies that reuse vacant commercial buildings. The Port Ansel site will remain open, and no jobs there will be affected, the company said.', 'NONE', '["touches", "manages", "sells", "delays"]', 'B', 1, '[동의어] 정답 (B). ''handles all of its regional shipments from a single site''는 한 곳에서 지역 배송 물량을 모두 처리·관리한다는 뜻이므로 manages가 맞다. 오답: (A) touches는 손으로 만지다라는 handle의 다른 뜻이다. (C) sells는 판매하다, (D) delays는 지연시키다로 문맥과 맞지 않는다.
포인트: 동의어 — handle(처리·관리하다)의 문맥상 뜻', 173),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is NOT mentioned about the positions at the new center?', 'Questions 172-175 refer to the following article.

Corvane Logistics to Open Alderpoint Center

ALDERPOINT (April 14) — Corvane Logistics announced yesterday that it will open a distribution center on Millbrook Road in Alderpoint this fall. The 18,000-square-meter building, formerly a furniture warehouse, is being fitted with automated sorting equipment and is expected to begin operations on October 1.

Corvane currently handles all of its regional shipments from a single site near the port in Port Ansel, where the company is headquartered. "That building has served us well for 15 years, but we have simply outgrown it," said Chief Operating Officer Hedda Brenner. "The Alderpoint center will let us reach customers in the northern counties a full day sooner."

The company plans to fill about 120 positions at the new center, including forklift operators, inventory clerks, and shift supervisors. Previous warehouse experience is not required for most roles, as new hires will receive three weeks of paid training. A job fair will be held at the Alderpoint Community Hall on June 20.

Local officials welcomed the news. Mayor Wren Thackery noted that the project qualified for the city''s tax incentive for companies that reuse vacant commercial buildings. The Port Ansel site will remain open, and no jobs there will be affected, the company said.', 'NONE', '["The approximate number of openings", "The kinds of jobs available", "The training new employees will receive", "The wages that will be offered"]', 'D', 1, '[사실 확인(NOT)] 정답 (D). 급여 수준은 기사에 나오지 않는다(교육이 유급이라는 말만 있다). 오답: (A) ''about 120 positions'', (B) ''forklift operators, inventory clerks, and shift supervisors'', (C) ''three weeks of paid training''으로 각각 언급되어 있다.
포인트: 사실 확인(NOT) — 기사 셋째 문단과 보기 대조', 174),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the building on Millbrook Road?', 'Questions 172-175 refer to the following article.

Corvane Logistics to Open Alderpoint Center

ALDERPOINT (April 14) — Corvane Logistics announced yesterday that it will open a distribution center on Millbrook Road in Alderpoint this fall. The 18,000-square-meter building, formerly a furniture warehouse, is being fitted with automated sorting equipment and is expected to begin operations on October 1.

Corvane currently handles all of its regional shipments from a single site near the port in Port Ansel, where the company is headquartered. "That building has served us well for 15 years, but we have simply outgrown it," said Chief Operating Officer Hedda Brenner. "The Alderpoint center will let us reach customers in the northern counties a full day sooner."

The company plans to fill about 120 positions at the new center, including forklift operators, inventory clerks, and shift supervisors. Previous warehouse experience is not required for most roles, as new hires will receive three weeks of paid training. A job fair will be held at the Alderpoint Community Hall on June 20.

Local officials welcomed the news. Mayor Wren Thackery noted that the project qualified for the city''s tax incentive for companies that reuse vacant commercial buildings. The Port Ansel site will remain open, and no jobs there will be affected, the company said.', 'NONE', '["It was not in use before Corvane''s project.", "It was constructed by the city government.", "It will replace the company''s Port Ansel site.", "It is located next to a shipping port."]', 'A', 1, '[추론] 정답 (A). 이 사업이 ''the city''s tax incentive for companies that reuse vacant commercial buildings''의 대상이 되었다고 했으므로, 예전 가구 창고였던 그 건물은 비어 있었다고 볼 수 있다. vacant가 not in use로 바뀌었다. 오답: (B) 시가 지었다는 말은 없다. (C) ''The Port Ansel site will remain open''과 반대다. (D) 항구 근처에 있는 것은 Port Ansel 시설이다.
포인트: 추론 — 기사 마지막 문단(vacant → not in use)', 175),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'Why did Ms. Garrity write to Mr. Tembo?', 'Questions 176-180 refer to the following e-mail and invoice.

[E-mail]
To: Oliver Tembo <otembo@brightleafcafe.com>
From: Sandra Garrity <sgarrity@kellanpackaging.com>
Date: November 4
Subject: Order KP-2291

Dear Mr. Tembo,

I am writing about the order you placed on October 28. The paper cups and the lids were shipped yesterday and should reach you by November 6. The tracking number for that shipment appears at the bottom of this message. Unfortunately, the printed paper bags have been delayed because the machine that applies custom logos is undergoing repairs. We now expect to ship the bags on November 12 rather than November 5.

I apologize for the inconvenience. As we discussed by phone this morning, I am sending you one pack of plain bags at no charge so that you have something to use in the meantime. To make up for the delay, I have also taken 10 percent off the price of the printed bags.

While reviewing your account, I noticed that the invoice I sent on October 29 listed five cases of lids rather than the four you ordered. A corrected invoice is attached. Please disregard the earlier one.

Sincerely,
Sandra Garrity
Kellan Packaging Supply

Tracking number (paper cups and lids): KP-7730-2291-04

────────────────────

[Invoice]
Kellan Packaging Supply
310 Cannery Road, Marrowby

Invoice No.: 2291-B (replaces 2291-A)
Date issued: November 4
Bill to: Brightleaf Café, 80 Orchard Lane, Lowden

Item | Quantity | Unit price | Amount
12-oz. paper cups (case of 500) | 4 | $38.00 | $152.00
Cup lids (case of 500) | 4 | $21.00 | $84.00
Printed paper bags (pack of 250) | 6 | $30.00 | $180.00
Plain paper bags (pack of 500) | 1 | $0.00 | $0.00

Subtotal: $416.00
Discount (10% on printed bags): -$18.00
Shipping: $24.00
Total due: $422.00

Payment due: December 4
Note: Payments received within 10 days of the issue date qualify for an additional 2 percent discount.', 'NONE', '["To ask him to approve a new logo design", "To request payment of an overdue invoice", "To tell him that part of an order will be late", "To recommend a different type of paper cup and lid"]', 'C', 1, '[주제·목적] 정답 (C). ''the printed paper bags have been delayed … We now expect to ship the bags on November 12 rather than November 5''에서 주문품 중 일부(인쇄 봉투)가 늦어진다는 것을 알리는 메일이다. 오답: (A) 로고 디자인 승인을 요청하지 않는다. (B) 납부 기한은 12월 4일로 연체가 아니다. (D) 다른 컵이나 뚜껑을 권하지 않는다.
포인트: 주제·목적 — 이메일 첫 문단', 176),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'According to the e-mail, what caused the delay?', 'Questions 176-180 refer to the following e-mail and invoice.

[E-mail]
To: Oliver Tembo <otembo@brightleafcafe.com>
From: Sandra Garrity <sgarrity@kellanpackaging.com>
Date: November 4
Subject: Order KP-2291

Dear Mr. Tembo,

I am writing about the order you placed on October 28. The paper cups and the lids were shipped yesterday and should reach you by November 6. The tracking number for that shipment appears at the bottom of this message. Unfortunately, the printed paper bags have been delayed because the machine that applies custom logos is undergoing repairs. We now expect to ship the bags on November 12 rather than November 5.

I apologize for the inconvenience. As we discussed by phone this morning, I am sending you one pack of plain bags at no charge so that you have something to use in the meantime. To make up for the delay, I have also taken 10 percent off the price of the printed bags.

While reviewing your account, I noticed that the invoice I sent on October 29 listed five cases of lids rather than the four you ordered. A corrected invoice is attached. Please disregard the earlier one.

Sincerely,
Sandra Garrity
Kellan Packaging Supply

Tracking number (paper cups and lids): KP-7730-2291-04

────────────────────

[Invoice]
Kellan Packaging Supply
310 Cannery Road, Marrowby

Invoice No.: 2291-B (replaces 2291-A)
Date issued: November 4
Bill to: Brightleaf Café, 80 Orchard Lane, Lowden

Item | Quantity | Unit price | Amount
12-oz. paper cups (case of 500) | 4 | $38.00 | $152.00
Cup lids (case of 500) | 4 | $21.00 | $84.00
Printed paper bags (pack of 250) | 6 | $30.00 | $180.00
Plain paper bags (pack of 500) | 1 | $0.00 | $0.00

Subtotal: $416.00
Discount (10% on printed bags): -$18.00
Shipping: $24.00
Total due: $422.00

Payment due: December 4
Note: Payments received within 10 days of the issue date qualify for an additional 2 percent discount.', 'NONE', '["A piece of equipment is being fixed.", "A shipment of cups arrived damaged.", "An invoice listed the wrong quantity.", "A supply of plain bags ran out."]', 'A', 1, '[세부 사항] 정답 (A). ''because the machine that applies custom logos is undergoing repairs''가 지연 이유다. machine이 a piece of equipment로, undergoing repairs가 being fixed로 바뀌었다. 오답: (B) 컵은 이미 발송되었고 파손 이야기는 없다. (C) 송장의 수량 오류는 지연과 별개의 일이다. (D) 무지 봉투는 그동안 쓰라고 보내 주는 것이다.
포인트: 세부 사항 — 이메일 첫 문단(undergoing repairs → being fixed)', 177),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about invoice 2291-A?', 'Questions 176-180 refer to the following e-mail and invoice.

[E-mail]
To: Oliver Tembo <otembo@brightleafcafe.com>
From: Sandra Garrity <sgarrity@kellanpackaging.com>
Date: November 4
Subject: Order KP-2291

Dear Mr. Tembo,

I am writing about the order you placed on October 28. The paper cups and the lids were shipped yesterday and should reach you by November 6. The tracking number for that shipment appears at the bottom of this message. Unfortunately, the printed paper bags have been delayed because the machine that applies custom logos is undergoing repairs. We now expect to ship the bags on November 12 rather than November 5.

I apologize for the inconvenience. As we discussed by phone this morning, I am sending you one pack of plain bags at no charge so that you have something to use in the meantime. To make up for the delay, I have also taken 10 percent off the price of the printed bags.

While reviewing your account, I noticed that the invoice I sent on October 29 listed five cases of lids rather than the four you ordered. A corrected invoice is attached. Please disregard the earlier one.

Sincerely,
Sandra Garrity
Kellan Packaging Supply

Tracking number (paper cups and lids): KP-7730-2291-04

────────────────────

[Invoice]
Kellan Packaging Supply
310 Cannery Road, Marrowby

Invoice No.: 2291-B (replaces 2291-A)
Date issued: November 4
Bill to: Brightleaf Café, 80 Orchard Lane, Lowden

Item | Quantity | Unit price | Amount
12-oz. paper cups (case of 500) | 4 | $38.00 | $152.00
Cup lids (case of 500) | 4 | $21.00 | $84.00
Printed paper bags (pack of 250) | 6 | $30.00 | $180.00
Plain paper bags (pack of 500) | 1 | $0.00 | $0.00

Subtotal: $416.00
Discount (10% on printed bags): -$18.00
Shipping: $24.00
Total due: $422.00

Payment due: December 4
Note: Payments received within 10 days of the issue date qualify for an additional 2 percent discount.', 'NONE', '["It was issued on November 4.", "It listed too many cases of cup lids.", "It included a charge for a pack of plain bags.", "It has already been paid by Brightleaf Café."]', 'B', 1, '[연계(문서1+2)·추론] 정답 (B). 송장의 ''Invoice No.: 2291-B (replaces 2291-A)''에서 2291-A는 먼저 보낸 송장이고, 이메일의 ''the invoice I sent on October 29 listed five cases of lids rather than the four you ordered''에서 그 송장에 뚜껑이 한 상자 많게 적혔음을 알 수 있다. 오답: (A) 11월 4일은 정정 송장 2291-B의 발급일이다. (C) 무지 봉투는 오늘 아침 통화 뒤에 무료로 보내기로 한 것이다. (D) ''Please disregard the earlier one''이라고 했을 뿐 결제했다는 말은 없다.
포인트: 연계 문항 — 이메일 셋째 문단 + 송장 번호(replaces 2291-A)', 178),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is the listed amount, before any discount, for the items that will be shipped on November 12?', 'Questions 176-180 refer to the following e-mail and invoice.

[E-mail]
To: Oliver Tembo <otembo@brightleafcafe.com>
From: Sandra Garrity <sgarrity@kellanpackaging.com>
Date: November 4
Subject: Order KP-2291

Dear Mr. Tembo,

I am writing about the order you placed on October 28. The paper cups and the lids were shipped yesterday and should reach you by November 6. The tracking number for that shipment appears at the bottom of this message. Unfortunately, the printed paper bags have been delayed because the machine that applies custom logos is undergoing repairs. We now expect to ship the bags on November 12 rather than November 5.

I apologize for the inconvenience. As we discussed by phone this morning, I am sending you one pack of plain bags at no charge so that you have something to use in the meantime. To make up for the delay, I have also taken 10 percent off the price of the printed bags.

While reviewing your account, I noticed that the invoice I sent on October 29 listed five cases of lids rather than the four you ordered. A corrected invoice is attached. Please disregard the earlier one.

Sincerely,
Sandra Garrity
Kellan Packaging Supply

Tracking number (paper cups and lids): KP-7730-2291-04

────────────────────

[Invoice]
Kellan Packaging Supply
310 Cannery Road, Marrowby

Invoice No.: 2291-B (replaces 2291-A)
Date issued: November 4
Bill to: Brightleaf Café, 80 Orchard Lane, Lowden

Item | Quantity | Unit price | Amount
12-oz. paper cups (case of 500) | 4 | $38.00 | $152.00
Cup lids (case of 500) | 4 | $21.00 | $84.00
Printed paper bags (pack of 250) | 6 | $30.00 | $180.00
Plain paper bags (pack of 500) | 1 | $0.00 | $0.00

Subtotal: $416.00
Discount (10% on printed bags): -$18.00
Shipping: $24.00
Total due: $422.00

Payment due: December 4
Note: Payments received within 10 days of the issue date qualify for an additional 2 percent discount.', 'NONE', '["$84.00", "$152.00", "$162.00", "$180.00"]', 'D', 1, '[연계(문서1+2)] 정답 (D). 이메일에서 11월 12일에 발송되는 것은 인쇄 봉투(''ship the bags on November 12'')이고, 송장에서 Printed paper bags는 6묶음 × 30달러 = 180달러다. 오답: (A) 뚜껑, (B) 컵 금액이다. (C) 162달러는 10% 할인(18달러)을 뺀 금액이므로 ''할인 전''이라는 조건에 맞지 않는다.
포인트: 연계 문항 — 이메일의 발송일 + 송장의 품목 금액', 179),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'By what date must Brightleaf Café pay in order to receive an extra discount?', 'Questions 176-180 refer to the following e-mail and invoice.

[E-mail]
To: Oliver Tembo <otembo@brightleafcafe.com>
From: Sandra Garrity <sgarrity@kellanpackaging.com>
Date: November 4
Subject: Order KP-2291

Dear Mr. Tembo,

I am writing about the order you placed on October 28. The paper cups and the lids were shipped yesterday and should reach you by November 6. The tracking number for that shipment appears at the bottom of this message. Unfortunately, the printed paper bags have been delayed because the machine that applies custom logos is undergoing repairs. We now expect to ship the bags on November 12 rather than November 5.

I apologize for the inconvenience. As we discussed by phone this morning, I am sending you one pack of plain bags at no charge so that you have something to use in the meantime. To make up for the delay, I have also taken 10 percent off the price of the printed bags.

While reviewing your account, I noticed that the invoice I sent on October 29 listed five cases of lids rather than the four you ordered. A corrected invoice is attached. Please disregard the earlier one.

Sincerely,
Sandra Garrity
Kellan Packaging Supply

Tracking number (paper cups and lids): KP-7730-2291-04

────────────────────

[Invoice]
Kellan Packaging Supply
310 Cannery Road, Marrowby

Invoice No.: 2291-B (replaces 2291-A)
Date issued: November 4
Bill to: Brightleaf Café, 80 Orchard Lane, Lowden

Item | Quantity | Unit price | Amount
12-oz. paper cups (case of 500) | 4 | $38.00 | $152.00
Cup lids (case of 500) | 4 | $21.00 | $84.00
Printed paper bags (pack of 250) | 6 | $30.00 | $180.00
Plain paper bags (pack of 500) | 1 | $0.00 | $0.00

Subtotal: $416.00
Discount (10% on printed bags): -$18.00
Shipping: $24.00
Total due: $422.00

Payment due: December 4
Note: Payments received within 10 days of the issue date qualify for an additional 2 percent discount.', 'NONE', '["November 6", "November 14", "November 24", "December 4"]', 'B', 1, '[세부 사항] 정답 (B). 송장의 ''Payments received within 10 days of the issue date qualify for an additional 2 percent discount''에서 발급일(11월 4일)로부터 10일 이내, 즉 11월 14일까지 내야 추가 할인을 받는다. 오답: (A) 컵과 뚜껑의 도착 예정일이다. (C) 근거 없는 날짜다. (D) 일반 납부 기한이다.
포인트: 세부 사항(날짜 계산) — 송장 Note와 발급일', 180),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about Ellwood & Finch Architects?', 'Questions 181-185 refer to the following job posting and e-mail.

[Job posting]
Ellwood & Finch Architects
Office Coordinator (Cedarholm)

Ellwood & Finch Architects, a 35-person firm specializing in the design of schools and public libraries, seeks an office coordinator for its studio in Cedarholm.

Responsibilities
- Greeting clients and managing the reception desk
- Ordering supplies and keeping in contact with vendors
- Scheduling meetings and booking travel for the firm''s two partners
- Assisting the bookkeeper with monthly expense reports

Requirements
- At least two years of experience in an office setting
- Proficiency with spreadsheet and scheduling software
- Excellent written communication skills
Experience at an architecture or engineering firm is desirable but not essential.

This is a full-time position. Benefits include health insurance, 15 days of paid leave, and a monthly transit pass. To apply, send a résumé and a brief cover letter to careers@ellwoodfinch.com by March 14. Interviews will be held at our studio during the week of March 23. The successful candidate will be expected to start on April 13.

────────────────────

[E-mail]
To: Beatrice Chukwu <b.chukwu@ellwoodfinch.com>
From: Julian Ostlund <jostlund@inbox-mail.com>
Date: March 18
Subject: Re: Interview invitation

Dear Ms. Chukwu,

Thank you for inviting me to interview for the office coordinator position. I would be glad to meet you and Mr. Finch. However, I will be attending a trade conference in Northmere on behalf of my employer from March 23 to 25, so the March 24 appointment you proposed is not possible for me. Could we meet on March 26 or 27 instead? Any time on either day would suit me.

I hope the firm can accommodate a request for a later start date. My employer, Brackley Engineering, requires four weeks'' notice, so the earliest I could begin is April 27.

Finally, as requested, I have attached two letters of reference. One is from my supervisor at Brackley, where I have run the front office for the past three years.

I look forward to hearing from you.

Sincerely,
Julian Ostlund', 'NONE', '["It was established 35 years ago.", "It is moving to a larger studio.", "It plans to hire two new partners.", "It designs buildings for educational use."]', 'D', 1, '[사실 확인] 정답 (D). ''specializing in the design of schools and public libraries''에서 학교 등 교육 시설을 설계하는 회사임을 알 수 있다. schools가 buildings for educational use로 바뀌었다. 오답: (A) 35는 직원 수(35-person firm)다. (B) 이전 계획은 없다. (C) two partners는 이미 있는 대표 두 명이다.
포인트: 사실 확인 — 채용 공고 첫 문단(schools → educational use)', 181),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is NOT a stated duty of the office coordinator?', 'Questions 181-185 refer to the following job posting and e-mail.

[Job posting]
Ellwood & Finch Architects
Office Coordinator (Cedarholm)

Ellwood & Finch Architects, a 35-person firm specializing in the design of schools and public libraries, seeks an office coordinator for its studio in Cedarholm.

Responsibilities
- Greeting clients and managing the reception desk
- Ordering supplies and keeping in contact with vendors
- Scheduling meetings and booking travel for the firm''s two partners
- Assisting the bookkeeper with monthly expense reports

Requirements
- At least two years of experience in an office setting
- Proficiency with spreadsheet and scheduling software
- Excellent written communication skills
Experience at an architecture or engineering firm is desirable but not essential.

This is a full-time position. Benefits include health insurance, 15 days of paid leave, and a monthly transit pass. To apply, send a résumé and a brief cover letter to careers@ellwoodfinch.com by March 14. Interviews will be held at our studio during the week of March 23. The successful candidate will be expected to start on April 13.

────────────────────

[E-mail]
To: Beatrice Chukwu <b.chukwu@ellwoodfinch.com>
From: Julian Ostlund <jostlund@inbox-mail.com>
Date: March 18
Subject: Re: Interview invitation

Dear Ms. Chukwu,

Thank you for inviting me to interview for the office coordinator position. I would be glad to meet you and Mr. Finch. However, I will be attending a trade conference in Northmere on behalf of my employer from March 23 to 25, so the March 24 appointment you proposed is not possible for me. Could we meet on March 26 or 27 instead? Any time on either day would suit me.

I hope the firm can accommodate a request for a later start date. My employer, Brackley Engineering, requires four weeks'' notice, so the earliest I could begin is April 27.

Finally, as requested, I have attached two letters of reference. One is from my supervisor at Brackley, where I have run the front office for the past three years.

I look forward to hearing from you.

Sincerely,
Julian Ostlund', 'NONE', '["Training newly hired employees", "Welcoming visitors to the studio", "Purchasing materials for the office", "Making travel arrangements"]', 'A', 1, '[사실 확인(NOT)] 정답 (A). 신입 직원 교육은 Responsibilities에 없다. 오답: (B) ''Greeting clients and managing the reception desk'', (C) ''Ordering supplies'', (D) ''booking travel for the firm''s two partners''가 각각 바꿔 표현된 것이다.
포인트: 사실 확인(NOT) — 공고 Responsibilities 항목 대조', 182),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'Why is Mr. Ostlund unable to meet on March 24?', 'Questions 181-185 refer to the following job posting and e-mail.

[Job posting]
Ellwood & Finch Architects
Office Coordinator (Cedarholm)

Ellwood & Finch Architects, a 35-person firm specializing in the design of schools and public libraries, seeks an office coordinator for its studio in Cedarholm.

Responsibilities
- Greeting clients and managing the reception desk
- Ordering supplies and keeping in contact with vendors
- Scheduling meetings and booking travel for the firm''s two partners
- Assisting the bookkeeper with monthly expense reports

Requirements
- At least two years of experience in an office setting
- Proficiency with spreadsheet and scheduling software
- Excellent written communication skills
Experience at an architecture or engineering firm is desirable but not essential.

This is a full-time position. Benefits include health insurance, 15 days of paid leave, and a monthly transit pass. To apply, send a résumé and a brief cover letter to careers@ellwoodfinch.com by March 14. Interviews will be held at our studio during the week of March 23. The successful candidate will be expected to start on April 13.

────────────────────

[E-mail]
To: Beatrice Chukwu <b.chukwu@ellwoodfinch.com>
From: Julian Ostlund <jostlund@inbox-mail.com>
Date: March 18
Subject: Re: Interview invitation

Dear Ms. Chukwu,

Thank you for inviting me to interview for the office coordinator position. I would be glad to meet you and Mr. Finch. However, I will be attending a trade conference in Northmere on behalf of my employer from March 23 to 25, so the March 24 appointment you proposed is not possible for me. Could we meet on March 26 or 27 instead? Any time on either day would suit me.

I hope the firm can accommodate a request for a later start date. My employer, Brackley Engineering, requires four weeks'' notice, so the earliest I could begin is April 27.

Finally, as requested, I have attached two letters of reference. One is from my supervisor at Brackley, where I have run the front office for the past three years.

I look forward to hearing from you.

Sincerely,
Julian Ostlund', 'NONE', '["He has an interview at another company.", "He has a work-related event to attend.", "He will be away on a family vacation.", "He needs more time to collect references."]', 'B', 1, '[세부 사항] 정답 (B). ''I will be attending a trade conference in Northmere on behalf of my employer from March 23 to 25''에서 회사를 대표해 업계 회의(trade conference)에 참석하기 때문이다. trade conference가 work-related event로 바뀌었다. 오답: (A) 다른 면접, (C) 휴가 이야기는 없다. (D) 추천서는 이미 첨부했다.
포인트: 세부 사항 — 이메일 첫 문단(trade conference → work-related event)', 183),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about Mr. Ostlund?', 'Questions 181-185 refer to the following job posting and e-mail.

[Job posting]
Ellwood & Finch Architects
Office Coordinator (Cedarholm)

Ellwood & Finch Architects, a 35-person firm specializing in the design of schools and public libraries, seeks an office coordinator for its studio in Cedarholm.

Responsibilities
- Greeting clients and managing the reception desk
- Ordering supplies and keeping in contact with vendors
- Scheduling meetings and booking travel for the firm''s two partners
- Assisting the bookkeeper with monthly expense reports

Requirements
- At least two years of experience in an office setting
- Proficiency with spreadsheet and scheduling software
- Excellent written communication skills
Experience at an architecture or engineering firm is desirable but not essential.

This is a full-time position. Benefits include health insurance, 15 days of paid leave, and a monthly transit pass. To apply, send a résumé and a brief cover letter to careers@ellwoodfinch.com by March 14. Interviews will be held at our studio during the week of March 23. The successful candidate will be expected to start on April 13.

────────────────────

[E-mail]
To: Beatrice Chukwu <b.chukwu@ellwoodfinch.com>
From: Julian Ostlund <jostlund@inbox-mail.com>
Date: March 18
Subject: Re: Interview invitation

Dear Ms. Chukwu,

Thank you for inviting me to interview for the office coordinator position. I would be glad to meet you and Mr. Finch. However, I will be attending a trade conference in Northmere on behalf of my employer from March 23 to 25, so the March 24 appointment you proposed is not possible for me. Could we meet on March 26 or 27 instead? Any time on either day would suit me.

I hope the firm can accommodate a request for a later start date. My employer, Brackley Engineering, requires four weeks'' notice, so the earliest I could begin is April 27.

Finally, as requested, I have attached two letters of reference. One is from my supervisor at Brackley, where I have run the front office for the past three years.

I look forward to hearing from you.

Sincerely,
Julian Ostlund', 'NONE', '["He has worked with Mr. Finch on a past project.", "He has less office experience than the firm requires.", "He has a background the firm prefers but does not require.", "He can start work on the date mentioned in the job posting."]', 'C', 1, '[연계(문서1+2)·추론] 정답 (C). 공고의 ''Experience at an architecture or engineering firm is desirable but not essential''과 이메일의 ''My employer, Brackley Engineering'', ''where I have run the front office for the past three years''를 연결하면, 그는 필수는 아니지만 우대되는 엔지니어링 회사 경력을 갖고 있다. 오답: (A) Finch 씨와 일한 적이 있다는 말은 없다. (B) 3년 경력은 최소 2년 조건을 넘는다. (D) 공고의 근무 시작일은 4월 13일인데 그는 4월 27일부터 가능하다고 했다.
포인트: 연계 문항 — 공고의 우대 조건 + 이메일의 경력', 184),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'In the e-mail, the word "accommodate" in paragraph 2, line 1, is closest in meaning to', 'Questions 181-185 refer to the following job posting and e-mail.

[Job posting]
Ellwood & Finch Architects
Office Coordinator (Cedarholm)

Ellwood & Finch Architects, a 35-person firm specializing in the design of schools and public libraries, seeks an office coordinator for its studio in Cedarholm.

Responsibilities
- Greeting clients and managing the reception desk
- Ordering supplies and keeping in contact with vendors
- Scheduling meetings and booking travel for the firm''s two partners
- Assisting the bookkeeper with monthly expense reports

Requirements
- At least two years of experience in an office setting
- Proficiency with spreadsheet and scheduling software
- Excellent written communication skills
Experience at an architecture or engineering firm is desirable but not essential.

This is a full-time position. Benefits include health insurance, 15 days of paid leave, and a monthly transit pass. To apply, send a résumé and a brief cover letter to careers@ellwoodfinch.com by March 14. Interviews will be held at our studio during the week of March 23. The successful candidate will be expected to start on April 13.

────────────────────

[E-mail]
To: Beatrice Chukwu <b.chukwu@ellwoodfinch.com>
From: Julian Ostlund <jostlund@inbox-mail.com>
Date: March 18
Subject: Re: Interview invitation

Dear Ms. Chukwu,

Thank you for inviting me to interview for the office coordinator position. I would be glad to meet you and Mr. Finch. However, I will be attending a trade conference in Northmere on behalf of my employer from March 23 to 25, so the March 24 appointment you proposed is not possible for me. Could we meet on March 26 or 27 instead? Any time on either day would suit me.

I hope the firm can accommodate a request for a later start date. My employer, Brackley Engineering, requires four weeks'' notice, so the earliest I could begin is April 27.

Finally, as requested, I have attached two letters of reference. One is from my supervisor at Brackley, where I have run the front office for the past three years.

I look forward to hearing from you.

Sincerely,
Julian Ostlund', 'NONE', '["lodge", "satisfy", "seat", "adapt"]', 'B', 1, '[동의어] 정답 (B). ''accommodate a request for a later start date''는 근무 시작일을 늦춰 달라는 요청을 들어준다는 뜻이므로 satisfy(충족시키다)가 맞다. 오답: (A) lodge(숙박시키다)와 (C) seat(인원을 앉히다)은 사람·인원이 목적어일 때의 accommodate 뜻이다. (D) adapt(적응시키다)는 request를 목적어로 하는 이 문장에 맞지 않는다.
포인트: 동의어 — accommodate(요청을 들어주다), 목적어 확인', 185),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What feature of the Caldera Mini is emphasized in the advertisement?', 'Questions 186-190 refer to the following advertisement, online review, and e-mail.

[Advertisement]
Introducing the Caldera Mini
Café-quality espresso from a machine no wider than a paperback book

New from Ridgeline Home of Castlebrook, the Caldera Mini heats up in just 40 seconds and brews with 15 bars of pressure. Its removable 0.8-liter water tank and dishwasher-safe drip tray make cleanup simple. The machine is available in matte black, cream, and slate blue.

Choose from three versions:
Caldera Mini (machine only) — $149
Caldera Mini Plus (with milk frother) — $189
Caldera Mini Complete (with milk frother and grinder) — $259

Launch offer: Order at www.ridgelinehome.com by May 31 and receive a free set of two espresso cups. Every machine comes with a two-year warranty and a 30-day money-back guarantee. Shipping is free on orders over $175.

────────────────────

[Online review]
Caldera Mini Plus — 4 out of 5 stars
Posted by Anneke Vos on June 8

I ordered this machine on May 20 to replace a much larger one that took up half of my kitchen counter. The espresso is excellent, and the 40-second heat-up time is no exaggeration. The frother works well too, although it is louder than I expected.

My only real complaint concerns the water tank. Mine arrived with a small crack near the handle, and it leaks slowly. I e-mailed the company about it on June 2 and am still waiting for an answer. Also, the free cups mentioned in the advertisement were not in the box. If Ridgeline sorts all this out, I will happily change my rating to five stars.

────────────────────

[E-mail]
To: Anneke Vos
From: Rahim Chaudhry, Ridgeline Home Customer Support
Date: June 9
Subject: Your Caldera Mini Plus (Order 60417)

Dear Ms. Vos,

Thank you for your review, and please accept my apologies for our slow response. After the launch, we received far more orders than we had anticipated, and our support team fell behind.

A replacement water tank was shipped to you this morning by express mail. There is no need to return the damaged one. The cups were left out of some early shipments by mistake, and yours are in the same package. Please let me know if it has not arrived by June 12.

If you would prefer, we can exchange the entire machine under the warranty. Simply reply to this message, and I will arrange it. In addition, I have issued a $15 refund to your original payment method as a gesture of goodwill.

Sincerely,
Rahim Chaudhry', 'NONE', '["Its compact size", "Its quiet operation", "Its built-in grinder", "Its low price"]', 'A', 1, '[세부 사항] 정답 (A). ''a machine no wider than a paperback book''에서 작은 크기를 내세운다. no wider than a paperback book이 compact size로 바뀌었다. 오답: (B) 소음 이야기는 광고에 없다. (C) 그라인더는 Complete 버전에만 있다. (D) 가격이 싸다고 강조하지 않는다.
포인트: 세부 사항 — 광고 부제(no wider than a paperback book → compact)', 186);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What does Ms. Vos say about the milk frother?', 'Questions 186-190 refer to the following advertisement, online review, and e-mail.

[Advertisement]
Introducing the Caldera Mini
Café-quality espresso from a machine no wider than a paperback book

New from Ridgeline Home of Castlebrook, the Caldera Mini heats up in just 40 seconds and brews with 15 bars of pressure. Its removable 0.8-liter water tank and dishwasher-safe drip tray make cleanup simple. The machine is available in matte black, cream, and slate blue.

Choose from three versions:
Caldera Mini (machine only) — $149
Caldera Mini Plus (with milk frother) — $189
Caldera Mini Complete (with milk frother and grinder) — $259

Launch offer: Order at www.ridgelinehome.com by May 31 and receive a free set of two espresso cups. Every machine comes with a two-year warranty and a 30-day money-back guarantee. Shipping is free on orders over $175.

────────────────────

[Online review]
Caldera Mini Plus — 4 out of 5 stars
Posted by Anneke Vos on June 8

I ordered this machine on May 20 to replace a much larger one that took up half of my kitchen counter. The espresso is excellent, and the 40-second heat-up time is no exaggeration. The frother works well too, although it is louder than I expected.

My only real complaint concerns the water tank. Mine arrived with a small crack near the handle, and it leaks slowly. I e-mailed the company about it on June 2 and am still waiting for an answer. Also, the free cups mentioned in the advertisement were not in the box. If Ridgeline sorts all this out, I will happily change my rating to five stars.

────────────────────

[E-mail]
To: Anneke Vos
From: Rahim Chaudhry, Ridgeline Home Customer Support
Date: June 9
Subject: Your Caldera Mini Plus (Order 60417)

Dear Ms. Vos,

Thank you for your review, and please accept my apologies for our slow response. After the launch, we received far more orders than we had anticipated, and our support team fell behind.

A replacement water tank was shipped to you this morning by express mail. There is no need to return the damaged one. The cups were left out of some early shipments by mistake, and yours are in the same package. Please let me know if it has not arrived by June 12.

If you would prefer, we can exchange the entire machine under the warranty. Simply reply to this message, and I will arrange it. In addition, I have issued a $15 refund to your original payment method as a gesture of goodwill.

Sincerely,
Rahim Chaudhry', 'NONE', '["It arrived with a crack in it.", "It makes more noise than she anticipated.", "It is difficult to keep clean.", "It takes a long time to heat up."]', 'B', 1, '[세부 사항] 정답 (B). ''The frother works well too, although it is louder than I expected''에서 생각보다 시끄럽다고 했다. louder than I expected가 more noise than she anticipated로 바뀌었다. 오답: (A) 금이 간 것은 물통(water tank)이다. (C) 세척이 어렵다는 말은 없다. (D) 예열 40초는 칭찬한 점이다.
포인트: 세부 사항 — 후기 첫 문단', 187),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about Ms. Vos''s order?', 'Questions 186-190 refer to the following advertisement, online review, and e-mail.

[Advertisement]
Introducing the Caldera Mini
Café-quality espresso from a machine no wider than a paperback book

New from Ridgeline Home of Castlebrook, the Caldera Mini heats up in just 40 seconds and brews with 15 bars of pressure. Its removable 0.8-liter water tank and dishwasher-safe drip tray make cleanup simple. The machine is available in matte black, cream, and slate blue.

Choose from three versions:
Caldera Mini (machine only) — $149
Caldera Mini Plus (with milk frother) — $189
Caldera Mini Complete (with milk frother and grinder) — $259

Launch offer: Order at www.ridgelinehome.com by May 31 and receive a free set of two espresso cups. Every machine comes with a two-year warranty and a 30-day money-back guarantee. Shipping is free on orders over $175.

────────────────────

[Online review]
Caldera Mini Plus — 4 out of 5 stars
Posted by Anneke Vos on June 8

I ordered this machine on May 20 to replace a much larger one that took up half of my kitchen counter. The espresso is excellent, and the 40-second heat-up time is no exaggeration. The frother works well too, although it is louder than I expected.

My only real complaint concerns the water tank. Mine arrived with a small crack near the handle, and it leaks slowly. I e-mailed the company about it on June 2 and am still waiting for an answer. Also, the free cups mentioned in the advertisement were not in the box. If Ridgeline sorts all this out, I will happily change my rating to five stars.

────────────────────

[E-mail]
To: Anneke Vos
From: Rahim Chaudhry, Ridgeline Home Customer Support
Date: June 9
Subject: Your Caldera Mini Plus (Order 60417)

Dear Ms. Vos,

Thank you for your review, and please accept my apologies for our slow response. After the launch, we received far more orders than we had anticipated, and our support team fell behind.

A replacement water tank was shipped to you this morning by express mail. There is no need to return the damaged one. The cups were left out of some early shipments by mistake, and yours are in the same package. Please let me know if it has not arrived by June 12.

If you would prefer, we can exchange the entire machine under the warranty. Simply reply to this message, and I will arrange it. In addition, I have issued a $15 refund to your original payment method as a gesture of goodwill.

Sincerely,
Rahim Chaudhry', 'NONE', '["It qualified for free shipping.", "It included a coffee grinder.", "It was placed after the launch offer ended.", "It was the lowest-priced version."]', 'A', 1, '[연계(문서1+2)·추론] 정답 (A). 후기에서 그녀가 산 것은 Caldera Mini Plus이고, 광고에서 Plus는 189달러, ''Shipping is free on orders over $175''이므로 무료 배송 대상이다. 오답: (B) 그라인더는 Complete에만 있다. (C) 5월 20일 주문은 5월 31일 기한 이전이다. (D) 가장 싼 것은 149달러짜리 기본형이다.
포인트: 연계 문항 — 후기의 구매 모델 + 광고의 가격·무료 배송 조건', 188),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'According to the e-mail, why was Ridgeline Home slow to reply?', 'Questions 186-190 refer to the following advertisement, online review, and e-mail.

[Advertisement]
Introducing the Caldera Mini
Café-quality espresso from a machine no wider than a paperback book

New from Ridgeline Home of Castlebrook, the Caldera Mini heats up in just 40 seconds and brews with 15 bars of pressure. Its removable 0.8-liter water tank and dishwasher-safe drip tray make cleanup simple. The machine is available in matte black, cream, and slate blue.

Choose from three versions:
Caldera Mini (machine only) — $149
Caldera Mini Plus (with milk frother) — $189
Caldera Mini Complete (with milk frother and grinder) — $259

Launch offer: Order at www.ridgelinehome.com by May 31 and receive a free set of two espresso cups. Every machine comes with a two-year warranty and a 30-day money-back guarantee. Shipping is free on orders over $175.

────────────────────

[Online review]
Caldera Mini Plus — 4 out of 5 stars
Posted by Anneke Vos on June 8

I ordered this machine on May 20 to replace a much larger one that took up half of my kitchen counter. The espresso is excellent, and the 40-second heat-up time is no exaggeration. The frother works well too, although it is louder than I expected.

My only real complaint concerns the water tank. Mine arrived with a small crack near the handle, and it leaks slowly. I e-mailed the company about it on June 2 and am still waiting for an answer. Also, the free cups mentioned in the advertisement were not in the box. If Ridgeline sorts all this out, I will happily change my rating to five stars.

────────────────────

[E-mail]
To: Anneke Vos
From: Rahim Chaudhry, Ridgeline Home Customer Support
Date: June 9
Subject: Your Caldera Mini Plus (Order 60417)

Dear Ms. Vos,

Thank you for your review, and please accept my apologies for our slow response. After the launch, we received far more orders than we had anticipated, and our support team fell behind.

A replacement water tank was shipped to you this morning by express mail. There is no need to return the damaged one. The cups were left out of some early shipments by mistake, and yours are in the same package. Please let me know if it has not arrived by June 12.

If you would prefer, we can exchange the entire machine under the warranty. Simply reply to this message, and I will arrange it. In addition, I have issued a $15 refund to your original payment method as a gesture of goodwill.

Sincerely,
Rahim Chaudhry', 'NONE', '["It had an unexpectedly high number of orders.", "It closed its support office for a holiday.", "It had a technical problem with its website.", "It lost the message Ms. Vos sent on June 2."]', 'A', 1, '[세부 사항] 정답 (A). ''we received far more orders than we had anticipated, and our support team fell behind''에서 예상보다 주문이 많아 응대가 밀렸다고 했다. far more … than we had anticipated가 unexpectedly high number로 바뀌었다. 오답: (B) 휴무, (C) 웹사이트 장애, (D) 메일 분실은 언급이 없다.
포인트: 세부 사항 — 이메일 첫 문단', 189),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What issue raised by Ms. Vos does Mr. Chaudhry NOT address in his e-mail?', 'Questions 186-190 refer to the following advertisement, online review, and e-mail.

[Advertisement]
Introducing the Caldera Mini
Café-quality espresso from a machine no wider than a paperback book

New from Ridgeline Home of Castlebrook, the Caldera Mini heats up in just 40 seconds and brews with 15 bars of pressure. Its removable 0.8-liter water tank and dishwasher-safe drip tray make cleanup simple. The machine is available in matte black, cream, and slate blue.

Choose from three versions:
Caldera Mini (machine only) — $149
Caldera Mini Plus (with milk frother) — $189
Caldera Mini Complete (with milk frother and grinder) — $259

Launch offer: Order at www.ridgelinehome.com by May 31 and receive a free set of two espresso cups. Every machine comes with a two-year warranty and a 30-day money-back guarantee. Shipping is free on orders over $175.

────────────────────

[Online review]
Caldera Mini Plus — 4 out of 5 stars
Posted by Anneke Vos on June 8

I ordered this machine on May 20 to replace a much larger one that took up half of my kitchen counter. The espresso is excellent, and the 40-second heat-up time is no exaggeration. The frother works well too, although it is louder than I expected.

My only real complaint concerns the water tank. Mine arrived with a small crack near the handle, and it leaks slowly. I e-mailed the company about it on June 2 and am still waiting for an answer. Also, the free cups mentioned in the advertisement were not in the box. If Ridgeline sorts all this out, I will happily change my rating to five stars.

────────────────────

[E-mail]
To: Anneke Vos
From: Rahim Chaudhry, Ridgeline Home Customer Support
Date: June 9
Subject: Your Caldera Mini Plus (Order 60417)

Dear Ms. Vos,

Thank you for your review, and please accept my apologies for our slow response. After the launch, we received far more orders than we had anticipated, and our support team fell behind.

A replacement water tank was shipped to you this morning by express mail. There is no need to return the damaged one. The cups were left out of some early shipments by mistake, and yours are in the same package. Please let me know if it has not arrived by June 12.

If you would prefer, we can exchange the entire machine under the warranty. Simply reply to this message, and I will arrange it. In addition, I have issued a $15 refund to your original payment method as a gesture of goodwill.

Sincerely,
Rahim Chaudhry', 'NONE', '["The damaged part", "The loud frother", "The missing gift", "The delayed reply"]', 'B', 1, '[연계(문서2+3)·사실 확인(NOT)] 정답 (B). 후기의 불만은 물통 균열, 답변 지연, 컵 누락, 거품기 소음인데, 이메일은 거품기 소음에 대해서는 아무 말도 하지 않는다. 오답: (A) ''A replacement water tank was shipped'', (C) ''The cups … yours are in the same package'', (D) ''apologies for our slow response''로 각각 다루었다.
포인트: 연계 문항(NOT) — 후기의 불만 네 가지 + 이메일의 조치', 190),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'According to the Web page, what is included with every booth?', 'Questions 191-195 refer to the following Web page, form, and e-mail.

[Web page]
Harvest Moon Festival — Vendor Booths

The Senna Valley Tourism Office invites businesses to take part in the 12th annual Harvest Moon Festival, to be held September 18-20 on the Lakeshore Promenade.

Booth type | Location | Size | Fee (three days)
Promenade | Along the waterfront walk | 3 m x 3 m | $240
Plaza | Fountain Plaza, near the main stage | 3 m x 3 m | $300
Market Tent | Inside the shared tent on Pier 2 | 2 m x 2 m | $150
Food Court | Parking area on Greymoor Street | 4 m x 6 m | $360

Every booth comes with one table and two chairs. Electricity is available at Plaza and Food Court booths only. Businesses registered in Senna Valley receive 10 percent off the booth fee. Applications are due July 31, and vendors will be notified of their booth assignments by e-mail in early August. Fees are fully refundable until August 31.

────────────────────

[Form]
Harvest Moon Festival — Booth Application

Business name: Saltgrass Candle Company
Contact person: Mireille Aubert
Business address: 27 Thimble Row, Senna Valley
Products: Hand-poured candles and soaps
Booth type requested: Promenade
Second choice: Plaza
Electricity needed: No
Number of staff at booth: 2
Participated in a previous year: Yes (last year)
Comments: If possible, please place us away from the food vendors. Cooking smells make it hard for customers to sample our scented products.
Date submitted: July 12

────────────────────

[E-mail]
To: Mireille Aubert
From: Fergus Doyle, Senna Valley Tourism Office
Date: August 4
Subject: Your booth assignment

Dear Ms. Aubert,

I am pleased to confirm that Saltgrass Candle Company has been accepted as a vendor at this year''s festival. Unfortunately, the booth type you requested first filled up quickly, so we have assigned you your second choice. Your space, number 14, is nowhere near the Food Court, as you asked.

Your fee, less the discount for local businesses, is payable by August 15 through the link below. Setup begins at 7:00 A.M. on September 18, and all vendors must be ready by 10:00 A.M., when the festival opens. Because of roadwork on Greymoor Street, vendors unloading goods this year should enter from Boathouse Drive instead. Please bring a printed copy of this message, which will serve as your vendor pass, on the first morning.

An online vendor orientation will be held on September 10 at 6:00 P.M. Attendance is optional for returning vendors.

Best regards,
Fergus Doyle', 'NONE', '["A canopy for shade", "An electrical outlet", "A lockable storage box", "A table and seating"]', 'D', 1, '[세부 사항] 정답 (D). ''Every booth comes with one table and two chairs''에서 모든 부스에 탁자와 의자가 제공된다. two chairs가 seating으로 바뀌었다. 오답: (A) 차양막 이야기는 없다. (B) 전기는 Plaza와 Food Court 부스에서만 쓸 수 있다. (C) 보관함 이야기는 없다.
포인트: 세부 사항 — 웹 페이지 표 아래 문단(two chairs → seating)', 191),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What does Ms. Aubert request on the form?', 'Questions 191-195 refer to the following Web page, form, and e-mail.

[Web page]
Harvest Moon Festival — Vendor Booths

The Senna Valley Tourism Office invites businesses to take part in the 12th annual Harvest Moon Festival, to be held September 18-20 on the Lakeshore Promenade.

Booth type | Location | Size | Fee (three days)
Promenade | Along the waterfront walk | 3 m x 3 m | $240
Plaza | Fountain Plaza, near the main stage | 3 m x 3 m | $300
Market Tent | Inside the shared tent on Pier 2 | 2 m x 2 m | $150
Food Court | Parking area on Greymoor Street | 4 m x 6 m | $360

Every booth comes with one table and two chairs. Electricity is available at Plaza and Food Court booths only. Businesses registered in Senna Valley receive 10 percent off the booth fee. Applications are due July 31, and vendors will be notified of their booth assignments by e-mail in early August. Fees are fully refundable until August 31.

────────────────────

[Form]
Harvest Moon Festival — Booth Application

Business name: Saltgrass Candle Company
Contact person: Mireille Aubert
Business address: 27 Thimble Row, Senna Valley
Products: Hand-poured candles and soaps
Booth type requested: Promenade
Second choice: Plaza
Electricity needed: No
Number of staff at booth: 2
Participated in a previous year: Yes (last year)
Comments: If possible, please place us away from the food vendors. Cooking smells make it hard for customers to sample our scented products.
Date submitted: July 12

────────────────────

[E-mail]
To: Mireille Aubert
From: Fergus Doyle, Senna Valley Tourism Office
Date: August 4
Subject: Your booth assignment

Dear Ms. Aubert,

I am pleased to confirm that Saltgrass Candle Company has been accepted as a vendor at this year''s festival. Unfortunately, the booth type you requested first filled up quickly, so we have assigned you your second choice. Your space, number 14, is nowhere near the Food Court, as you asked.

Your fee, less the discount for local businesses, is payable by August 15 through the link below. Setup begins at 7:00 A.M. on September 18, and all vendors must be ready by 10:00 A.M., when the festival opens. Because of roadwork on Greymoor Street, vendors unloading goods this year should enter from Boathouse Drive instead. Please bring a printed copy of this message, which will serve as your vendor pass, on the first morning.

An online vendor orientation will be held on September 10 at 6:00 P.M. Attendance is optional for returning vendors.

Best regards,
Fergus Doyle', 'NONE', '["A space away from food sellers", "A booth inside the shared tent", "A connection to electricity", "An extra table for product samples"]', 'A', 1, '[세부 사항] 정답 (A). Comments의 ''please place us away from the food vendors''에서 음식 판매자와 떨어진 자리를 요청한다. food vendors가 food sellers로 바뀌었다. 오답: (B) 공동 천막 안의 Market Tent는 신청하지 않았다. (C) ''Electricity needed: No''다. (D) 탁자 추가 요청은 없다.
포인트: 세부 사항 — 신청서 Comments 항목', 192),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'How much will Ms. Aubert most likely be charged for her booth?', 'Questions 191-195 refer to the following Web page, form, and e-mail.

[Web page]
Harvest Moon Festival — Vendor Booths

The Senna Valley Tourism Office invites businesses to take part in the 12th annual Harvest Moon Festival, to be held September 18-20 on the Lakeshore Promenade.

Booth type | Location | Size | Fee (three days)
Promenade | Along the waterfront walk | 3 m x 3 m | $240
Plaza | Fountain Plaza, near the main stage | 3 m x 3 m | $300
Market Tent | Inside the shared tent on Pier 2 | 2 m x 2 m | $150
Food Court | Parking area on Greymoor Street | 4 m x 6 m | $360

Every booth comes with one table and two chairs. Electricity is available at Plaza and Food Court booths only. Businesses registered in Senna Valley receive 10 percent off the booth fee. Applications are due July 31, and vendors will be notified of their booth assignments by e-mail in early August. Fees are fully refundable until August 31.

────────────────────

[Form]
Harvest Moon Festival — Booth Application

Business name: Saltgrass Candle Company
Contact person: Mireille Aubert
Business address: 27 Thimble Row, Senna Valley
Products: Hand-poured candles and soaps
Booth type requested: Promenade
Second choice: Plaza
Electricity needed: No
Number of staff at booth: 2
Participated in a previous year: Yes (last year)
Comments: If possible, please place us away from the food vendors. Cooking smells make it hard for customers to sample our scented products.
Date submitted: July 12

────────────────────

[E-mail]
To: Mireille Aubert
From: Fergus Doyle, Senna Valley Tourism Office
Date: August 4
Subject: Your booth assignment

Dear Ms. Aubert,

I am pleased to confirm that Saltgrass Candle Company has been accepted as a vendor at this year''s festival. Unfortunately, the booth type you requested first filled up quickly, so we have assigned you your second choice. Your space, number 14, is nowhere near the Food Court, as you asked.

Your fee, less the discount for local businesses, is payable by August 15 through the link below. Setup begins at 7:00 A.M. on September 18, and all vendors must be ready by 10:00 A.M., when the festival opens. Because of roadwork on Greymoor Street, vendors unloading goods this year should enter from Boathouse Drive instead. Please bring a printed copy of this message, which will serve as your vendor pass, on the first morning.

An online vendor orientation will be held on September 10 at 6:00 P.M. Attendance is optional for returning vendors.

Best regards,
Fergus Doyle', 'NONE', '["$216", "$270", "$300", "$360"]', 'B', 1, '[연계(문서1+2+3)] 정답 (B). 이메일에서 2지망으로 배정되었고(''we have assigned you your second choice''), 신청서의 2지망은 Plaza, 웹 페이지에서 Plaza는 300달러다. 여기에 Senna Valley 업체 10% 할인(''less the discount for local businesses'')을 적용하면 270달러다. 오답: (A) 1지망 Promenade의 할인가, (C) Plaza 정가, (D) Food Court 정가다.
포인트: 연계 문항 — 이메일(2지망 배정·할인) + 신청서(2지망) + 웹 페이지(요금표)', 193),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'According to the e-mail, what is different for vendors this year?', 'Questions 191-195 refer to the following Web page, form, and e-mail.

[Web page]
Harvest Moon Festival — Vendor Booths

The Senna Valley Tourism Office invites businesses to take part in the 12th annual Harvest Moon Festival, to be held September 18-20 on the Lakeshore Promenade.

Booth type | Location | Size | Fee (three days)
Promenade | Along the waterfront walk | 3 m x 3 m | $240
Plaza | Fountain Plaza, near the main stage | 3 m x 3 m | $300
Market Tent | Inside the shared tent on Pier 2 | 2 m x 2 m | $150
Food Court | Parking area on Greymoor Street | 4 m x 6 m | $360

Every booth comes with one table and two chairs. Electricity is available at Plaza and Food Court booths only. Businesses registered in Senna Valley receive 10 percent off the booth fee. Applications are due July 31, and vendors will be notified of their booth assignments by e-mail in early August. Fees are fully refundable until August 31.

────────────────────

[Form]
Harvest Moon Festival — Booth Application

Business name: Saltgrass Candle Company
Contact person: Mireille Aubert
Business address: 27 Thimble Row, Senna Valley
Products: Hand-poured candles and soaps
Booth type requested: Promenade
Second choice: Plaza
Electricity needed: No
Number of staff at booth: 2
Participated in a previous year: Yes (last year)
Comments: If possible, please place us away from the food vendors. Cooking smells make it hard for customers to sample our scented products.
Date submitted: July 12

────────────────────

[E-mail]
To: Mireille Aubert
From: Fergus Doyle, Senna Valley Tourism Office
Date: August 4
Subject: Your booth assignment

Dear Ms. Aubert,

I am pleased to confirm that Saltgrass Candle Company has been accepted as a vendor at this year''s festival. Unfortunately, the booth type you requested first filled up quickly, so we have assigned you your second choice. Your space, number 14, is nowhere near the Food Court, as you asked.

Your fee, less the discount for local businesses, is payable by August 15 through the link below. Setup begins at 7:00 A.M. on September 18, and all vendors must be ready by 10:00 A.M., when the festival opens. Because of roadwork on Greymoor Street, vendors unloading goods this year should enter from Boathouse Drive instead. Please bring a printed copy of this message, which will serve as your vendor pass, on the first morning.

An online vendor orientation will be held on September 10 at 6:00 P.M. Attendance is optional for returning vendors.

Best regards,
Fergus Doyle', 'NONE', '["The opening time of the festival", "The deadline for paying fees", "The route for bringing in goods", "The format of the orientation"]', 'C', 1, '[세부 사항] 정답 (C). ''Because of roadwork on Greymoor Street, vendors unloading goods this year should enter from Boathouse Drive instead''에서 올해는 물품 반입 경로가 달라진다. 오답: (A) 개장 시각, (B) 납부 기한, (D) 오리엔테이션 방식이 올해 바뀌었다는 말은 없다.
포인트: 세부 사항 — 이메일 둘째 문단(this year … instead)', 194),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the September 10 orientation?', 'Questions 191-195 refer to the following Web page, form, and e-mail.

[Web page]
Harvest Moon Festival — Vendor Booths

The Senna Valley Tourism Office invites businesses to take part in the 12th annual Harvest Moon Festival, to be held September 18-20 on the Lakeshore Promenade.

Booth type | Location | Size | Fee (three days)
Promenade | Along the waterfront walk | 3 m x 3 m | $240
Plaza | Fountain Plaza, near the main stage | 3 m x 3 m | $300
Market Tent | Inside the shared tent on Pier 2 | 2 m x 2 m | $150
Food Court | Parking area on Greymoor Street | 4 m x 6 m | $360

Every booth comes with one table and two chairs. Electricity is available at Plaza and Food Court booths only. Businesses registered in Senna Valley receive 10 percent off the booth fee. Applications are due July 31, and vendors will be notified of their booth assignments by e-mail in early August. Fees are fully refundable until August 31.

────────────────────

[Form]
Harvest Moon Festival — Booth Application

Business name: Saltgrass Candle Company
Contact person: Mireille Aubert
Business address: 27 Thimble Row, Senna Valley
Products: Hand-poured candles and soaps
Booth type requested: Promenade
Second choice: Plaza
Electricity needed: No
Number of staff at booth: 2
Participated in a previous year: Yes (last year)
Comments: If possible, please place us away from the food vendors. Cooking smells make it hard for customers to sample our scented products.
Date submitted: July 12

────────────────────

[E-mail]
To: Mireille Aubert
From: Fergus Doyle, Senna Valley Tourism Office
Date: August 4
Subject: Your booth assignment

Dear Ms. Aubert,

I am pleased to confirm that Saltgrass Candle Company has been accepted as a vendor at this year''s festival. Unfortunately, the booth type you requested first filled up quickly, so we have assigned you your second choice. Your space, number 14, is nowhere near the Food Court, as you asked.

Your fee, less the discount for local businesses, is payable by August 15 through the link below. Setup begins at 7:00 A.M. on September 18, and all vendors must be ready by 10:00 A.M., when the festival opens. Because of roadwork on Greymoor Street, vendors unloading goods this year should enter from Boathouse Drive instead. Please bring a printed copy of this message, which will serve as your vendor pass, on the first morning.

An online vendor orientation will be held on September 10 at 6:00 P.M. Attendance is optional for returning vendors.

Best regards,
Fergus Doyle', 'NONE', '["It will take place on the Lakeshore Promenade.", "It will be led by Ms. Aubert.", "It has been moved to a later time.", "It is not mandatory for Ms. Aubert."]', 'D', 1, '[연계(문서2+3)·추론] 정답 (D). 이메일의 ''Attendance is optional for returning vendors''와 신청서의 ''Participated in a previous year: Yes (last year)''를 연결하면 Aubert 씨는 재참가 업체이므로 참석이 의무가 아니다. optional이 not mandatory로 바뀌었다. 오답: (A) 온라인으로 열린다. (B) 진행자 언급은 없다. (C) 시간이 바뀌었다는 말은 없다.
포인트: 연계 문항 — 이메일(returning vendors) + 신청서(지난해 참가)', 195),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is the purpose of the notice?', 'Questions 196-200 refer to the following notice, e-mail, and form.

[Notice]
Kelvane Biotech — Briar Glen Campus
Parking Structure Repairs

From March 2 through May 29, levels 2 and 3 of the employee parking structure will be closed for resurfacing, and about 180 of our 400 spaces will be unavailable. To reduce demand during this period, the company is offering the options below. Each employee may choose one.

Option A (Shuttle): Park free of charge at the Fairgrounds lot on Cutler Avenue. Shuttles run every 15 minutes from 6:30 to 9:30 A.M. and from 4:00 to 7:00 P.M.
Option B (Transit): Receive a regional bus and rail pass. The company will pay the full monthly cost.
Option C (Carpool): Groups of three or more employees receive a reserved space on level 1.
Option D (Regular parking): Continue to park in the structure. Open spaces there are first come, first served each morning.

Submit the Commuter Option Form to Facilities by February 13. Employees who do not submit a form will be assigned Option D. We apologize for the inconvenience and thank you for your cooperation.

────────────────────

[E-mail]
To: Facilities <facilities@kelvanebio.com>
From: Soo-jin Hwang
Date: February 5
Subject: Question about commuter options

Hello,

I work in the Quality Lab, and my shift ends at 7:30 P.M. three days a week. I was planning to park at the Fairgrounds, but it looks as though I would miss the last shuttle on those evenings. Will the shuttle run any later? I checked the notice but could not find an answer. If the hours stay the same, I will have to choose something else. Two colleagues who live near me have suggested that we drive in together.

Also, may the form be submitted by e-mail? I will be at a training course in Haverly from February 9 to 13 and will not be on campus.

Thank you,
Soo-jin Hwang

────────────────────

[Form]
Commuter Option Form

Name: Soo-jin Hwang
Department: Quality Lab
Employee number: 30792
Usual working hours: 11:00 A.M. to 7:30 P.M. (three days a week)
Option selected: C
Other members of group (Option C only): Paolo Greco, Ifeoma Okoye
Start date: March 2
Vehicle license plates: BG 5521 (Hwang), BG 9048 (Okoye)
Notes: Mr. Greco does not own a car. Ms. Okoye and I will take turns driving.

For Facilities use
Received: February 10, by e-mail
Approved by: Lars Edstrom', 'NONE', '["To announce a new fee for employee parking", "To describe commuting choices during repair work", "To report the opening of an additional parking lot", "To recruit drivers for a company shuttle service"]', 'B', 1, '[주제·목적] 정답 (B). 주차장 2·3층이 보수로 닫히는 동안 ''the company is offering the options below''라며 셔틀·대중교통·카풀·기존 주차의 네 가지 통근 방법을 설명한다. resurfacing이 repair work로, options가 choices로 바뀌었다. 오답: (A) 요금 부과는 없다. (C) Fairgrounds는 임시로 쓰는 곳이지 새 주차장 개장 소식이 아니다. (D) 운전자 모집이 아니다.
포인트: 주제·목적 — 공지 첫 문단(options → choices)', 196),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'According to the notice, what will happen to employees who do not submit a form?', 'Questions 196-200 refer to the following notice, e-mail, and form.

[Notice]
Kelvane Biotech — Briar Glen Campus
Parking Structure Repairs

From March 2 through May 29, levels 2 and 3 of the employee parking structure will be closed for resurfacing, and about 180 of our 400 spaces will be unavailable. To reduce demand during this period, the company is offering the options below. Each employee may choose one.

Option A (Shuttle): Park free of charge at the Fairgrounds lot on Cutler Avenue. Shuttles run every 15 minutes from 6:30 to 9:30 A.M. and from 4:00 to 7:00 P.M.
Option B (Transit): Receive a regional bus and rail pass. The company will pay the full monthly cost.
Option C (Carpool): Groups of three or more employees receive a reserved space on level 1.
Option D (Regular parking): Continue to park in the structure. Open spaces there are first come, first served each morning.

Submit the Commuter Option Form to Facilities by February 13. Employees who do not submit a form will be assigned Option D. We apologize for the inconvenience and thank you for your cooperation.

────────────────────

[E-mail]
To: Facilities <facilities@kelvanebio.com>
From: Soo-jin Hwang
Date: February 5
Subject: Question about commuter options

Hello,

I work in the Quality Lab, and my shift ends at 7:30 P.M. three days a week. I was planning to park at the Fairgrounds, but it looks as though I would miss the last shuttle on those evenings. Will the shuttle run any later? I checked the notice but could not find an answer. If the hours stay the same, I will have to choose something else. Two colleagues who live near me have suggested that we drive in together.

Also, may the form be submitted by e-mail? I will be at a training course in Haverly from February 9 to 13 and will not be on campus.

Thank you,
Soo-jin Hwang

────────────────────

[Form]
Commuter Option Form

Name: Soo-jin Hwang
Department: Quality Lab
Employee number: 30792
Usual working hours: 11:00 A.M. to 7:30 P.M. (three days a week)
Option selected: C
Other members of group (Option C only): Paolo Greco, Ifeoma Okoye
Start date: March 2
Vehicle license plates: BG 5521 (Hwang), BG 9048 (Okoye)
Notes: Mr. Greco does not own a car. Ms. Okoye and I will take turns driving.

For Facilities use
Received: February 10, by e-mail
Approved by: Lars Edstrom', 'NONE', '["They will lose their parking privileges.", "They will be issued a transit pass.", "They will keep parking in the structure.", "They will be assigned to a carpool group."]', 'C', 1, '[세부 사항] 정답 (C). ''Employees who do not submit a form will be assigned Option D''이고 Option D는 ''Continue to park in the structure''다. 오답: (A) 주차 자격을 잃는다는 말은 없다. (B) 교통 패스는 Option B, (D) 카풀은 Option C를 고른 경우다.
포인트: 세부 사항 — 공지 마지막 문단 + Option D 설명', 197),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What does Ms. Hwang ask about the shuttle?', 'Questions 196-200 refer to the following notice, e-mail, and form.

[Notice]
Kelvane Biotech — Briar Glen Campus
Parking Structure Repairs

From March 2 through May 29, levels 2 and 3 of the employee parking structure will be closed for resurfacing, and about 180 of our 400 spaces will be unavailable. To reduce demand during this period, the company is offering the options below. Each employee may choose one.

Option A (Shuttle): Park free of charge at the Fairgrounds lot on Cutler Avenue. Shuttles run every 15 minutes from 6:30 to 9:30 A.M. and from 4:00 to 7:00 P.M.
Option B (Transit): Receive a regional bus and rail pass. The company will pay the full monthly cost.
Option C (Carpool): Groups of three or more employees receive a reserved space on level 1.
Option D (Regular parking): Continue to park in the structure. Open spaces there are first come, first served each morning.

Submit the Commuter Option Form to Facilities by February 13. Employees who do not submit a form will be assigned Option D. We apologize for the inconvenience and thank you for your cooperation.

────────────────────

[E-mail]
To: Facilities <facilities@kelvanebio.com>
From: Soo-jin Hwang
Date: February 5
Subject: Question about commuter options

Hello,

I work in the Quality Lab, and my shift ends at 7:30 P.M. three days a week. I was planning to park at the Fairgrounds, but it looks as though I would miss the last shuttle on those evenings. Will the shuttle run any later? I checked the notice but could not find an answer. If the hours stay the same, I will have to choose something else. Two colleagues who live near me have suggested that we drive in together.

Also, may the form be submitted by e-mail? I will be at a training course in Haverly from February 9 to 13 and will not be on campus.

Thank you,
Soo-jin Hwang

────────────────────

[Form]
Commuter Option Form

Name: Soo-jin Hwang
Department: Quality Lab
Employee number: 30792
Usual working hours: 11:00 A.M. to 7:30 P.M. (three days a week)
Option selected: C
Other members of group (Option C only): Paolo Greco, Ifeoma Okoye
Start date: March 2
Vehicle license plates: BG 5521 (Hwang), BG 9048 (Okoye)
Notes: Mr. Greco does not own a car. Ms. Okoye and I will take turns driving.

For Facilities use
Received: February 10, by e-mail
Approved by: Lars Edstrom', 'NONE', '["Whether its evening hours will be extended", "Where it will drop off passengers on campus", "How much employees will pay to ride it", "How often it will run in the morning"]', 'A', 1, '[세부 사항] 정답 (A). 근무가 저녁 7시 30분에 끝나 마지막 셔틀을 놓친다고 하면서 ''Will the shuttle run any later?''라고 묻는다. run any later가 evening hours will be extended로 바뀌었다. 오답: (B) 하차 장소, (C) 요금, (D) 아침 운행 간격은 묻지 않았다.
포인트: 세부 사항 — 이메일 첫 문단(run any later → hours extended)', 198),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about the option Ms. Hwang selected?', 'Questions 196-200 refer to the following notice, e-mail, and form.

[Notice]
Kelvane Biotech — Briar Glen Campus
Parking Structure Repairs

From March 2 through May 29, levels 2 and 3 of the employee parking structure will be closed for resurfacing, and about 180 of our 400 spaces will be unavailable. To reduce demand during this period, the company is offering the options below. Each employee may choose one.

Option A (Shuttle): Park free of charge at the Fairgrounds lot on Cutler Avenue. Shuttles run every 15 minutes from 6:30 to 9:30 A.M. and from 4:00 to 7:00 P.M.
Option B (Transit): Receive a regional bus and rail pass. The company will pay the full monthly cost.
Option C (Carpool): Groups of three or more employees receive a reserved space on level 1.
Option D (Regular parking): Continue to park in the structure. Open spaces there are first come, first served each morning.

Submit the Commuter Option Form to Facilities by February 13. Employees who do not submit a form will be assigned Option D. We apologize for the inconvenience and thank you for your cooperation.

────────────────────

[E-mail]
To: Facilities <facilities@kelvanebio.com>
From: Soo-jin Hwang
Date: February 5
Subject: Question about commuter options

Hello,

I work in the Quality Lab, and my shift ends at 7:30 P.M. three days a week. I was planning to park at the Fairgrounds, but it looks as though I would miss the last shuttle on those evenings. Will the shuttle run any later? I checked the notice but could not find an answer. If the hours stay the same, I will have to choose something else. Two colleagues who live near me have suggested that we drive in together.

Also, may the form be submitted by e-mail? I will be at a training course in Haverly from February 9 to 13 and will not be on campus.

Thank you,
Soo-jin Hwang

────────────────────

[Form]
Commuter Option Form

Name: Soo-jin Hwang
Department: Quality Lab
Employee number: 30792
Usual working hours: 11:00 A.M. to 7:30 P.M. (three days a week)
Option selected: C
Other members of group (Option C only): Paolo Greco, Ifeoma Okoye
Start date: March 2
Vehicle license plates: BG 5521 (Hwang), BG 9048 (Okoye)
Notes: Mr. Greco does not own a car. Ms. Okoye and I will take turns driving.

For Facilities use
Received: February 10, by e-mail
Approved by: Lars Edstrom', 'NONE', '["It requires her to pay a monthly fee.", "It gives her group a designated parking space.", "It involves leaving her car at the Fairgrounds lot.", "It includes a regional bus and rail pass."]', 'B', 1, '[연계(문서1+3)] 정답 (B). 신청서의 ''Option selected: C''를 공지의 ''Option C (Carpool): Groups of three or more employees receive a reserved space on level 1''과 연결하면, 그녀의 카풀 모임은 지정 주차 자리를 받는다. reserved가 designated로 바뀌었다. 오답: (A) 요금 이야기는 없다. (C) Fairgrounds 주차는 Option A다. (D) 버스·철도 패스는 Option B다.
포인트: 연계 문항 — 신청서의 선택(Option C) + 공지의 Option C 설명', 199),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is most likely true about Ms. Hwang on February 10?', 'Questions 196-200 refer to the following notice, e-mail, and form.

[Notice]
Kelvane Biotech — Briar Glen Campus
Parking Structure Repairs

From March 2 through May 29, levels 2 and 3 of the employee parking structure will be closed for resurfacing, and about 180 of our 400 spaces will be unavailable. To reduce demand during this period, the company is offering the options below. Each employee may choose one.

Option A (Shuttle): Park free of charge at the Fairgrounds lot on Cutler Avenue. Shuttles run every 15 minutes from 6:30 to 9:30 A.M. and from 4:00 to 7:00 P.M.
Option B (Transit): Receive a regional bus and rail pass. The company will pay the full monthly cost.
Option C (Carpool): Groups of three or more employees receive a reserved space on level 1.
Option D (Regular parking): Continue to park in the structure. Open spaces there are first come, first served each morning.

Submit the Commuter Option Form to Facilities by February 13. Employees who do not submit a form will be assigned Option D. We apologize for the inconvenience and thank you for your cooperation.

────────────────────

[E-mail]
To: Facilities <facilities@kelvanebio.com>
From: Soo-jin Hwang
Date: February 5
Subject: Question about commuter options

Hello,

I work in the Quality Lab, and my shift ends at 7:30 P.M. three days a week. I was planning to park at the Fairgrounds, but it looks as though I would miss the last shuttle on those evenings. Will the shuttle run any later? I checked the notice but could not find an answer. If the hours stay the same, I will have to choose something else. Two colleagues who live near me have suggested that we drive in together.

Also, may the form be submitted by e-mail? I will be at a training course in Haverly from February 9 to 13 and will not be on campus.

Thank you,
Soo-jin Hwang

────────────────────

[Form]
Commuter Option Form

Name: Soo-jin Hwang
Department: Quality Lab
Employee number: 30792
Usual working hours: 11:00 A.M. to 7:30 P.M. (three days a week)
Option selected: C
Other members of group (Option C only): Paolo Greco, Ifeoma Okoye
Start date: March 2
Vehicle license plates: BG 5521 (Hwang), BG 9048 (Okoye)
Notes: Mr. Greco does not own a car. Ms. Okoye and I will take turns driving.

For Facilities use
Received: February 10, by e-mail
Approved by: Lars Edstrom', 'NONE', '["She was working an evening shift in the Quality Lab.", "She met with Mr. Edstrom in the Facilities office.", "She rode the shuttle from the Fairgrounds lot.", "She was taking a training course off campus."]', 'D', 1, '[연계(문서2+3)·추론] 정답 (D). 이메일에서 ''I will be at a training course in Haverly from February 9 to 13 and will not be on campus''라고 했고, 신청서는 그 기간인 2월 10일에 이메일로 접수되었다(''Received: February 10, by e-mail''). 따라서 그날 그녀는 캠퍼스 밖에서 교육을 받고 있었을 것이다. 오답: (A)·(B) 캠퍼스에 없었다. (C) 셔틀은 3월 2일부터 시작되는 선택지다.
포인트: 연계 문항 — 이메일의 교육 기간 + 신청서의 접수일', 200);
