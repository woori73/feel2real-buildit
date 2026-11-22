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
A. At the front desk, please.
B. They left around noon.
C. Yes, I visited last week.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-의문사(Where)] 정답 (A). Where로 둘 장소를 물었으므로 ''At the front desk''라는 장소 응답이 맞다. 오답: (B) leave-left 소리를 이용한 시간 응답으로 주어 They가 가리킬 대상도 없다. (C) 의문사 의문문에 Yes로 답할 수 없고 visitor-visited 연상 오답이다.', 7),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'When does the shipment from Weston Ridge arrive?
A. By truck, I believe.
B. At the loading dock.
C. Not until Thursday morning.', 'TTS', '["", "", ""]', 'C', 1, '[Part2-의문사(When)] 정답 (C). When으로 도착 시점을 물었으므로 ''Not until Thursday morning''이 맞다. 오답: (A) 운송 수단(How)에 대한 응답이다. (B) 장소(Where)에 대한 응답이다.', 8),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Who''s leading the orientation for the new hires?
A. In the training room.
B. Yes, they were hired in March.
C. Ms. Okafor from human resources.', 'TTS', '["", "", ""]', 'C', 1, '[Part2-의문사(Who)] 정답 (C). Who로 진행자를 물었으므로 사람 이름과 부서를 말한 응답이 맞다. 오답: (A) 장소(Where) 응답이다. (B) 의문사 의문문에 Yes로 답할 수 없고 hires-hired 반복 오답이다.', 9),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Have you booked a room for the client meeting?
A. The book is on my desk.
B. Yes, the one on the third floor.
C. He''s a longtime client.', 'TTS', '["", "", ""]', 'B', 1, '[Part2-일반 의문문] 정답 (B). 회의실을 예약했느냐는 질문에 Yes로 답하고 어느 방인지 덧붙였다. 오답: (A) booked(예약하다)와 book(책)의 소리를 이용한 오답이다. (C) client를 반복했을 뿐 예약 여부와 관계가 없다.', 10),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Why is the parking garage closed today?
A. About ten dollars a day.
B. Close to the main entrance.
C. Didn''t you see the e-mail from building management?', 'TTS', '["", "", ""]', 'C', 1, '[Part2-의문사(Why)·간접 응답] 정답 (C). 이유를 직접 말하지 않고 ''건물 관리팀 이메일 못 봤어요?''라고 되물어 이유가 그 이메일에 나와 있음을 알리는 간접 응답이다. 오답: (A) 주차 요금(How much) 응답이다. (B) closed-close 소리를 이용한 위치 응답이다.', 11),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Could you send me the revised budget by this afternoon?
A. At the afternoon session.
B. Sure, I''m just finishing it now.
C. He advised me to wait.', 'TTS', '["", "", ""]', 'B', 1, '[Part2-제안·요청] 정답 (B). 수정 예산안을 보내 달라는 요청에 Sure로 수락하고 지금 마무리 중이라고 답했다. 오답: (A) afternoon을 반복한 장소·시간 응답으로 요청에 대한 답이 아니다. (C) revised-advised 소리를 이용한 오답이고 He가 가리킬 대상이 없다.', 12),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'How do I reset the password for the scheduling system?
A. Carla in technical support can walk you through it.
B. The schedule was posted on Monday.
C. No, I passed by it this morning.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-의문사(How)·간접 응답] 정답 (A). 방법을 직접 설명하는 대신 기술 지원팀의 Carla가 안내해 줄 수 있다고 알려 주는 간접 응답이다. 오답: (B) scheduling-schedule 반복 오답으로 방법과 무관하다. (C) 의문사 의문문에 No로 답할 수 없고 password-passed 소리 오답이다.', 13),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'The printer on the second floor has been repaired, hasn''t it?
A. Two pairs, please.
B. The technician is still working on it.
C. I''ll print twenty copies.', 'TTS', '["", "", ""]', 'B', 1, '[Part2-부가 의문문·간접 응답] 정답 (B). 수리가 끝났느냐는 확인에 ''기사가 아직 작업 중''이라고 답해 아직 안 끝났음을 알리는 간접 응답이다. 오답: (A) repaired-pairs 소리를 이용한 오답이다. (C) printer-print 연상 오답으로 수리 여부에 대한 답이 아니다.', 14),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'What time does the pharmacy on Marlow Street open?
A. Yes, it''s open to the public.
B. A farm outside the city.
C. At eight thirty on weekdays.', 'TTS', '["", "", ""]', 'C', 1, '[Part2-의문사(What time)] 정답 (C). What time으로 여는 시각을 물었으므로 ''At eight thirty''가 맞다. 오답: (A) 의문사 의문문에 Yes로 답할 수 없고 open을 반복했다. (B) pharmacy-farm 소리를 이용한 오답이다.', 15),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Would you rather fly to Harlow Bay or take the train?
A. The train is much more relaxing.
B. Yes, I''d like that.
C. The training starts at nine.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-선택 의문문] 정답 (A). 비행기와 기차 중 기차를 고르고 이유를 덧붙였다. 오답: (B) 둘 중 하나를 고르라는 질문에 Yes로 답하면 무엇을 골랐는지 알 수 없다. (C) train-training 소리를 이용한 오답이다.', 16),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'I can''t find the invoice for the catering order.
A. A table for twelve.
B. Yes, the food was excellent.
C. Have you checked the shared folder?', 'TTS', '["", "", ""]', 'C', 1, '[Part2-평서문·간접 응답] 정답 (C). 송장을 못 찾겠다는 말에 ''공유 폴더는 확인해 봤어요?''라고 찾을 곳을 제안하는 간접 응답이다. 오답: (A) catering에서 연상되는 식당 예약 표현이다. (B) catering-food 연상 오답으로 송장을 찾는 문제와 관계가 없다.', 17),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Where is the retirement dinner for Mr. Halvorsen being held?
A. He retired after thirty years.
B. At seven on Friday evening.
C. The venue hasn''t been decided yet.', 'TTS', '["", "", ""]', 'C', 1, '[Part2-의문사(Where)·간접 응답] 정답 (C). 장소를 묻는 말에 ''장소가 아직 정해지지 않았다''고 답하는 간접 응답이다. 오답: (A) retirement-retired 반복 오답으로 장소가 아니다. (B) 시간(When)에 대한 응답이다.', 18),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Aren''t you supposed to be at the dentist this afternoon?
A. A new toothbrush.
B. My appointment was moved to Friday.
C. I suppose it''s on the left.', 'TTS', '["", "", ""]', 'B', 1, '[Part2-부정 의문문·간접 응답] 정답 (B). 오늘 오후 치과에 가야 하지 않느냐는 말에 ''예약이 금요일로 옮겨졌다''고 답해 오늘은 가지 않음을 알리는 간접 응답이다. 오답: (A) dentist에서 연상되는 단어일 뿐이다. (C) supposed-suppose 소리를 이용한 위치 응답이다.', 19),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'How long will the elevator be out of service?
A. Until the end of the week, I heard.
B. On the fourth floor.
C. The service was very friendly.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-의문사(How long)] 정답 (A). How long으로 기간을 물었으므로 ''이번 주말까지''라는 기간 응답이 맞다. 오답: (B) 위치(Where) 응답이다. (C) service를 다른 뜻(접객)으로 반복한 오답이다.', 20),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Why don''t we hold the workshop in the larger conference room?
A. Because it started late.
B. I bought it at a hardware shop.
C. That room doesn''t have a projector.', 'TTS', '["", "", ""]', 'C', 1, '[Part2-제안·요청·간접 응답] 정답 (C). 큰 회의실에서 하자는 제안에 ''그 방에는 프로젝터가 없다''며 문제점을 들어 완곡하게 반대하는 간접 응답이다. 오답: (A) Why don''t we는 제안인데 이유를 묻는 Why로 착각하게 만든 오답이다. (B) workshop-shop 소리를 이용한 오답이다.', 21),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Did the supplier confirm the delivery date?
A. I''m still waiting to hear back.
B. A box of office supplies.
C. The expiration date is on the label.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-일반 의문문·간접 응답] 정답 (A). 납품일을 확인받았느냐는 질문에 ''아직 답을 기다리는 중''이라고 답해 아직 확인되지 않았음을 알리는 간접 응답이다. 오답: (B) supplier-supplies 소리를 이용한 오답이다. (C) date를 반복했지만 유통 기한 이야기라 질문과 무관하다.', 22),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Who''s going to pick up Ms. Tanaka from the airport?
A. Her flight has been canceled.
B. At terminal two.
C. I picked a window seat.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-의문사(Who)·간접 응답] 정답 (A). 누가 마중 나가느냐는 질문에 ''항공편이 취소됐다''고 답해 마중이 필요 없음을 알리는 간접 응답이다. 오답: (B) 장소(Where) 응답이다. (C) pick up-picked 소리와 airport-window seat 연상을 이용한 오답이다.', 23),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'The new accounting software is harder to use than I expected.
A. There''s a training session next Tuesday.
B. Yes, he''s a certified accountant.
C. The hardware store closes at six.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-평서문·간접 응답] 정답 (A). 새 프로그램이 쓰기 어렵다는 말에 ''다음 주 화요일에 교육이 있다''며 해결 방법을 알려 주는 간접 응답이다. 오답: (B) accounting-accountant 소리 오답이고 he가 가리킬 대상이 없다. (C) harder-hardware, software-hardware 소리·연상 오답이다.', 24),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'When will the job candidates be interviewed?
A. In the second-floor meeting room.
B. You''d have to ask Mr. Brandt.
C. Yes, I saw the view.', 'TTS', '["", "", ""]', 'B', 1, '[Part2-의문사(When)·간접 응답] 정답 (B). 면접 시기를 묻는 말에 ''Brandt 씨에게 물어봐야 한다''며 자신은 모르고 아는 사람을 알려 주는 간접 응답이다. 오답: (A) 장소(Where) 응답이다. (C) 의문사 의문문에 Yes로 답할 수 없고 interviewed-view 소리 오답이다.', 25),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Did the customer say anything about the damaged package?
A. A packing list is enclosed.
B. She''d like a replacement sent right away.
C. The manager''s office is upstairs.', 'TTS', '["", "", ""]', 'B', 1, '[Part2-일반 의문문] 정답 (B). 고객이 뭐라고 했느냐는 질문에 ''교체품을 바로 보내 달라고 한다''며 고객의 요구를 전했다. 오답: (A) package-packing 소리를 이용한 오답이다. (C) 고객 불만에서 연상되는 manager를 썼지만 위치 안내라 질문과 무관하다.', 26);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Is the cafeteria still serving lunch?
A. Only until one thirty.
B. They launched it last month.
C. Next to the service entrance.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-일반 의문문] 정답 (A). 아직 점심을 파느냐는 질문에 ''1시 30분까지만''이라고 답해 제공 시간을 알려 준다. 오답: (B) lunch-launched 소리를 이용한 오답이다. (C) serving-service 소리를 이용한 위치 응답이다.', 27),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Should I e-mail the contract or send it by courier?
A. Yes, he signed it.
B. A three-year agreement.
C. Either is fine with me.', 'TTS', '["", "", ""]', 'C', 1, '[Part2-선택 의문문] 정답 (C). 이메일과 택배 중 어느 쪽이냐는 선택 의문문에 ''어느 쪽이든 괜찮다''고 답했다. 오답: (A) 선택 의문문에 Yes로 답할 수 없고 he가 가리킬 대상이 없다. (B) contract-agreement 연상 오답으로 보내는 방법에 대한 답이 아니다.', 28),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Would you like me to reserve a table at Bellamy''s for Thursday?
A. It''s a wooden table.
B. That would be great, thanks.
C. They served us quickly.', 'TTS', '["", "", ""]', 'B', 1, '[Part2-제안·요청] 정답 (B). 식당 자리를 예약해 주겠다는 제안에 ''그래 주면 좋겠다''고 수락했다. 오답: (A) table을 가구의 뜻으로 반복한 오답이다. (C) reserve-served 소리를 이용한 과거 경험 진술로 제안에 대한 답이 아니다.', 29),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'Our flight to Weston Ridge has been delayed by two hours.
A. A window seat, please.
B. Then we have time to get something to eat.
C. Yes, I''ve lived there for two years.', 'TTS', '["", "", ""]', 'B', 1, '[Part2-평서문·간접 응답] 정답 (B). 비행기가 두 시간 지연됐다는 말에 ''그럼 뭘 좀 먹을 시간이 있겠다''고 그 결과를 받아 말하는 간접 응답이다. 오답: (A) flight에서 연상되는 좌석 요청이다. (C) two hours-two years 소리를 이용한 오답으로 지연과 무관하다.', 30),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P2', 'Listen to the question or statement and the three responses. Choose the best response.', 'How much does it cost to rent the community hall for an evening?
A. The rates are listed on the city''s website.
B. For about three hours.
C. On the corner of Pine Street.', 'TTS', '["", "", ""]', 'A', 1, '[Part2-의문사(How much)·간접 응답] 정답 (A). 대관료를 묻는 말에 금액 대신 ''요금은 시 웹사이트에 나와 있다''고 확인할 곳을 알려 주는 간접 응답이다. 오답: (B) 기간(How long) 응답이다. (C) 위치(Where) 응답이다.', 31),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Where does the conversation most likely take place?', 'Questions 32 through 34 refer to the following conversation.

Woman: Good evening. I have a reservation under Priya Nair, for three nights.
Man: Welcome to the Linden Court, Ms. Nair. Let me see. I''m sorry, but the room you booked isn''t ready yet. The air conditioner in that room is being repaired, and the technician needs about another hour.
Woman: Oh, that''s unfortunate. I have a video call with a client at six, and I was hoping to set up my laptop first.
Man: I understand. You''re welcome to use our business center on the second floor in the meantime. It''s quiet, and the internet connection is very fast. I can also keep your luggage here at the desk.
Woman: That works. Could you call my cell phone when the room is ready?
Man: Of course. I''ll do that.', 'TTS', '["At an airport", "At an electronics store", "At a repair shop", "At a hotel"]', 'D', 1, '[Part3-장소] 정답 (D). ''I have a reservation … for three nights'', ''the room you booked isn''t ready yet''에서 호텔 접수대임을 알 수 있다. 오답: (A) 공항을 나타내는 말이 없다. (B) laptop이 나오지만 매장이 아니다. (C) 에어컨 수리는 객실에서 이루어지는 일일 뿐 대화 장소가 수리점은 아니다.', 32),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Why is the room unavailable?', 'Questions 32 through 34 refer to the following conversation.

Woman: Good evening. I have a reservation under Priya Nair, for three nights.
Man: Welcome to the Linden Court, Ms. Nair. Let me see. I''m sorry, but the room you booked isn''t ready yet. The air conditioner in that room is being repaired, and the technician needs about another hour.
Woman: Oh, that''s unfortunate. I have a video call with a client at six, and I was hoping to set up my laptop first.
Man: I understand. You''re welcome to use our business center on the second floor in the meantime. It''s quiet, and the internet connection is very fast. I can also keep your luggage here at the desk.
Woman: That works. Could you call my cell phone when the room is ready?
Man: Of course. I''ll do that.', 'TTS', '["Some equipment is being fixed.", "It has not been cleaned.", "Another guest is staying late.", "The reservation was lost."]', 'A', 1, '[Part3-세부 사항(이유)] 정답 (A). ''The air conditioner in that room is being repaired''를 Some equipment is being fixed로 바꿔 말했다. 오답: (B) 청소 이야기는 없다. (C) 다른 투숙객 언급이 없다. (D) 예약은 정상적으로 확인되었다.', 33),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man suggest the woman do?', 'Questions 32 through 34 refer to the following conversation.

Woman: Good evening. I have a reservation under Priya Nair, for three nights.
Man: Welcome to the Linden Court, Ms. Nair. Let me see. I''m sorry, but the room you booked isn''t ready yet. The air conditioner in that room is being repaired, and the technician needs about another hour.
Woman: Oh, that''s unfortunate. I have a video call with a client at six, and I was hoping to set up my laptop first.
Man: I understand. You''re welcome to use our business center on the second floor in the meantime. It''s quiet, and the internet connection is very fast. I can also keep your luggage here at the desk.
Woman: That works. Could you call my cell phone when the room is ready?
Man: Of course. I''ll do that.', 'TTS', '["Change to a larger room", "Come back the next day", "Use a business center", "Call a technician"]', 'C', 1, '[Part3-제안] 정답 (C). ''You''re welcome to use our business center on the second floor in the meantime''에서 비즈니스 센터 이용을 권한다. 오답: (A) 방을 바꾸자는 말은 없다. (B) 한 시간 뒤면 준비되므로 다음 날 올 필요가 없다. (D) 기사는 이미 수리 중이며 여자가 부를 일이 아니다.', 34),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What are the speakers mainly discussing?', 'Questions 35 through 37 refer to the following conversation.

Man: Hi, Dana. I just noticed that the quarterly sales meeting is on the calendar for Wednesday at two. I''m afraid I''ll be visiting the Weston Ridge warehouse all that day.
Woman: Oh, I didn''t realize that. We really need you there, since you''re presenting the regional figures. Could we move it to Thursday morning?
Man: Thursday works for me. But isn''t the large conference room being used for the training workshop that morning?
Woman: You''re right. Well, the room on the fifth floor is smaller, but it should hold twelve people.
Man: That''ll be fine. I''ll send everyone an updated invitation right now so they can adjust their schedules.
Woman: Thanks. And I''ll ask the cafeteria to deliver coffee.', 'TTS', '["Hiring a sales manager", "Touring a warehouse", "Rescheduling a meeting", "Ordering refreshments"]', 'C', 1, '[Part3-주제] 정답 (C). 수요일 회의에 남자가 참석할 수 없어 ''Could we move it to Thursday morning?''이라며 회의 일정을 옮기는 대화다. 오답: (A) 채용 이야기는 없다. (B) 창고 방문은 불참 이유일 뿐 주제가 아니다. (D) 커피 주문은 마지막에 덧붙인 말일 뿐이다.', 35),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Why is the man unable to attend on Wednesday?', 'Questions 35 through 37 refer to the following conversation.

Man: Hi, Dana. I just noticed that the quarterly sales meeting is on the calendar for Wednesday at two. I''m afraid I''ll be visiting the Weston Ridge warehouse all that day.
Woman: Oh, I didn''t realize that. We really need you there, since you''re presenting the regional figures. Could we move it to Thursday morning?
Man: Thursday works for me. But isn''t the large conference room being used for the training workshop that morning?
Woman: You''re right. Well, the room on the fifth floor is smaller, but it should hold twelve people.
Man: That''ll be fine. I''ll send everyone an updated invitation right now so they can adjust their schedules.
Woman: Thanks. And I''ll ask the cafeteria to deliver coffee.', 'TTS', '["He is leading a workshop.", "He will be at another site.", "He has a medical appointment.", "He is meeting a client."]', 'B', 1, '[Part3-세부 사항(이유)] 정답 (B). ''I''ll be visiting the Weston Ridge warehouse all that day''를 He will be at another site로 바꿔 말했다. 오답: (A) 교육 워크숍은 목요일 오전에 큰 회의실을 쓰는 다른 행사다. (C) 병원 예약 언급이 없다. (D) 고객을 만난다는 말이 없다.', 36),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man say he will do next?', 'Questions 35 through 37 refer to the following conversation.

Man: Hi, Dana. I just noticed that the quarterly sales meeting is on the calendar for Wednesday at two. I''m afraid I''ll be visiting the Weston Ridge warehouse all that day.
Woman: Oh, I didn''t realize that. We really need you there, since you''re presenting the regional figures. Could we move it to Thursday morning?
Man: Thursday works for me. But isn''t the large conference room being used for the training workshop that morning?
Woman: You''re right. Well, the room on the fifth floor is smaller, but it should hold twelve people.
Man: That''ll be fine. I''ll send everyone an updated invitation right now so they can adjust their schedules.
Woman: Thanks. And I''ll ask the cafeteria to deliver coffee.', 'TTS', '["Send a revised invitation", "Reserve a larger room", "Call the cafeteria", "Print some figures"]', 'A', 1, '[Part3-다음 행동] 정답 (A). ''I''ll send everyone an updated invitation right now''에서 updated를 revised로 바꿔 말했다. 오답: (B) 오히려 더 작은 5층 방을 쓰기로 했다. (C) 구내식당에 연락하는 사람은 여자다. (D) 수치 자료를 인쇄한다는 말은 없다.', 37),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Why is the man calling?', 'Questions 38 through 40 refer to the following conversation.

Woman: Thanks for calling Corbin Office Furniture. How can I help you?
Man: Hi, I ordered six desk chairs last week for my accounting firm, and they were supposed to arrive yesterday. Nothing has come yet.
Woman: I''m sorry about that. May I have your order number?
Man: Sure. It''s four four seven one nine.
Woman: Thank you. I see what happened. The gray model you chose was out of stock at our warehouse, so the order was held. We do have the same chair in black, and those could go out today.
Man: Black is fine. But since the order is late, could you waive the delivery charge?
Woman: Certainly. I''ll remove that fee and send you a new confirmation by e-mail.', 'TTS', '["To return damaged items", "To ask about a late delivery", "To change a billing address", "To request a catalog"]', 'B', 1, '[Part3-목적] 정답 (B). ''they were supposed to arrive yesterday. Nothing has come yet''에서 배송 지연을 문의하는 전화임을 알 수 있다. 오답: (A) 물건이 아직 오지 않았으므로 파손·반품이 아니다. (C) 청구지 주소 이야기는 없다. (D) 카탈로그 요청은 없다.', 38),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What caused the problem?', 'Questions 38 through 40 refer to the following conversation.

Woman: Thanks for calling Corbin Office Furniture. How can I help you?
Man: Hi, I ordered six desk chairs last week for my accounting firm, and they were supposed to arrive yesterday. Nothing has come yet.
Woman: I''m sorry about that. May I have your order number?
Man: Sure. It''s four four seven one nine.
Woman: Thank you. I see what happened. The gray model you chose was out of stock at our warehouse, so the order was held. We do have the same chair in black, and those could go out today.
Man: Black is fine. But since the order is late, could you waive the delivery charge?
Woman: Certainly. I''ll remove that fee and send you a new confirmation by e-mail.', 'TTS', '["An item was out of stock.", "A payment was not processed.", "A truck broke down.", "An address was entered incorrectly."]', 'A', 1, '[Part3-세부 사항(문제 원인)] 정답 (A). ''The gray model you chose was out of stock at our warehouse, so the order was held''가 근거다. 오답: (B) 결제 문제는 언급되지 않았다. (C) 차량 고장 이야기는 없다. (D) 주소 오류 이야기는 없다.', 39),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the woman agree to do?', 'Questions 38 through 40 refer to the following conversation.

Woman: Thanks for calling Corbin Office Furniture. How can I help you?
Man: Hi, I ordered six desk chairs last week for my accounting firm, and they were supposed to arrive yesterday. Nothing has come yet.
Woman: I''m sorry about that. May I have your order number?
Man: Sure. It''s four four seven one nine.
Woman: Thank you. I see what happened. The gray model you chose was out of stock at our warehouse, so the order was held. We do have the same chair in black, and those could go out today.
Man: Black is fine. But since the order is late, could you waive the delivery charge?
Woman: Certainly. I''ll remove that fee and send you a new confirmation by e-mail.', 'TTS', '["Send a technician", "Provide extra chairs", "Remove a shipping fee", "Refund the full amount"]', 'C', 1, '[Part3-세부 사항(수락 내용)] 정답 (C). 남자가 ''could you waive the delivery charge?''라고 묻자 여자가 ''I''ll remove that fee''라고 답했다. delivery charge를 shipping fee로 바꿔 말했다. 오답: (A) 기사 파견은 없다. (B) 의자 수량은 그대로다. (D) 전액 환불이 아니라 배송비만 면제한다.', 40),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Who most likely is the man?', 'Questions 41 through 43 refer to the following conversation.

Man: Hi, Marisol. It''s Greg from building maintenance. You reported a leak in the ceiling of the break room?
Woman: Yes, thanks for coming so quickly. Water has been dripping near the refrigerator since this morning. We put a bucket under it.
Man: I see it. It looks like a pipe on the floor above is loose. I can fix that, but I''ll have to shut off the water on this side of the building for about forty minutes.
Woman: Hmm. We have clients arriving at eleven.
Man: Oh, in that case I''ll come back during the lunch hour, when fewer people are around.
Woman: I''d appreciate that. Meanwhile, I''ll put up a sign telling the staff to use the kitchen on the third floor.', 'TTS', '["A delivery driver", "A maintenance worker", "An appliance salesperson", "A safety inspector"]', 'B', 1, '[Part3-직업] 정답 (B). ''It''s Greg from building maintenance''라고 밝히고 새는 배관을 고치겠다고 하므로 건물 관리 직원이다. 오답: (A) 배달 이야기는 없다. (C) 냉장고가 나오지만 판매와 무관하다. (D) 점검이 아니라 신고받은 누수를 수리하러 왔다.', 41),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Why does the woman say, "We have clients arriving at eleven"?', 'Questions 41 through 43 refer to the following conversation.

Man: Hi, Marisol. It''s Greg from building maintenance. You reported a leak in the ceiling of the break room?
Woman: Yes, thanks for coming so quickly. Water has been dripping near the refrigerator since this morning. We put a bucket under it.
Man: I see it. It looks like a pipe on the floor above is loose. I can fix that, but I''ll have to shut off the water on this side of the building for about forty minutes.
Woman: Hmm. We have clients arriving at eleven.
Man: Oh, in that case I''ll come back during the lunch hour, when fewer people are around.
Woman: I''d appreciate that. Meanwhile, I''ll put up a sign telling the staff to use the kitchen on the third floor.', 'TTS', '["To indicate that the timing is inconvenient", "To ask the man to greet some visitors", "To explain why a room is reserved", "To suggest that she must leave soon"]', 'A', 1, '[Part3-화자 의도] 정답 (A). 남자가 40분 동안 물을 잠가야 한다고 하자 한 말이고, 남자가 ''in that case I''ll come back during the lunch hour''라고 답하므로 지금은 시간이 곤란하다는 뜻이다. 오답: (B) 손님 응대를 부탁하지 않았다. (C) 방 예약 이야기는 없다. (D) 여자가 자리를 떠난다는 말은 없다.', 42),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the woman say she will do?', 'Questions 41 through 43 refer to the following conversation.

Man: Hi, Marisol. It''s Greg from building maintenance. You reported a leak in the ceiling of the break room?
Woman: Yes, thanks for coming so quickly. Water has been dripping near the refrigerator since this morning. We put a bucket under it.
Man: I see it. It looks like a pipe on the floor above is loose. I can fix that, but I''ll have to shut off the water on this side of the building for about forty minutes.
Woman: Hmm. We have clients arriving at eleven.
Man: Oh, in that case I''ll come back during the lunch hour, when fewer people are around.
Woman: I''d appreciate that. Meanwhile, I''ll put up a sign telling the staff to use the kitchen on the third floor.', 'TTS', '["Empty a bucket", "Call a plumber", "Post a notice", "Move a refrigerator"]', 'C', 1, '[Part3-다음 행동] 정답 (C). ''I''ll put up a sign telling the staff to use the kitchen on the third floor''를 Post a notice로 바꿔 말했다. 오답: (A) 양동이는 이미 받쳐 두었다고만 했다. (B) 수리는 남자가 직접 한다. (D) 냉장고를 옮긴다는 말은 없다.', 43),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What are the speakers discussing?', 'Questions 44 through 46 refer to the following conversation with three speakers.

Woman: Thanks for joining me, both of you. We need to decide between the candidates for the marketing coordinator position. Oliver, what did you think?
Man 1: I liked Ms. Petrova. She has five years of experience at an advertising agency, and her writing samples were excellent.
Man 2: I agree, although Mr. Adeyemi was strong too. He speaks three languages, which would help with our overseas clients.
Woman: True. But the job posting said we need someone who can start by the first of next month, and Mr. Adeyemi isn''t available until the summer.
Man 1: Then Ms. Petrova is the better choice.
Man 2: Fair enough. Should we check her references first?
Woman: Yes. I''ll call her former manager this afternoon, and then we can make an offer.', 'TTS', '["Planning an advertising campaign", "Opening an overseas office", "Selecting a job candidate", "Revising a job description"]', 'C', 1, '[Part3-주제] 정답 (C). ''We need to decide between the candidates for the marketing coordinator position''에서 지원자 선정이 주제임을 알 수 있다. 오답: (A) 광고 대행사는 지원자의 경력일 뿐이다. (B) 해외 고객이 언급될 뿐 지사 개설 이야기는 없다. (D) 채용 공고의 조건을 인용할 뿐 고치자는 말은 없다.', 44),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What is mentioned about Mr. Adeyemi?', 'Questions 44 through 46 refer to the following conversation with three speakers.

Woman: Thanks for joining me, both of you. We need to decide between the candidates for the marketing coordinator position. Oliver, what did you think?
Man 1: I liked Ms. Petrova. She has five years of experience at an advertising agency, and her writing samples were excellent.
Man 2: I agree, although Mr. Adeyemi was strong too. He speaks three languages, which would help with our overseas clients.
Woman: True. But the job posting said we need someone who can start by the first of next month, and Mr. Adeyemi isn''t available until the summer.
Man 1: Then Ms. Petrova is the better choice.
Man 2: Fair enough. Should we check her references first?
Woman: Yes. I''ll call her former manager this afternoon, and then we can make an offer.', 'TTS', '["He cannot begin work soon enough.", "He lacks writing experience.", "He lives overseas.", "He asked for a higher salary."]', 'A', 1, '[Part3-세부 사항] 정답 (A). ''we need someone who can start by the first of next month, and Mr. Adeyemi isn''t available until the summer''가 근거다. 오답: (B) 글쓰기 견본은 Petrova 씨에 대한 이야기이고 Adeyemi 씨의 부족함은 언급되지 않았다. (C) 3개 국어를 한다고 했을 뿐 해외 거주는 아니다. (D) 급여 이야기는 없다.', 45),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What will the woman do this afternoon?', 'Questions 44 through 46 refer to the following conversation with three speakers.

Woman: Thanks for joining me, both of you. We need to decide between the candidates for the marketing coordinator position. Oliver, what did you think?
Man 1: I liked Ms. Petrova. She has five years of experience at an advertising agency, and her writing samples were excellent.
Man 2: I agree, although Mr. Adeyemi was strong too. He speaks three languages, which would help with our overseas clients.
Woman: True. But the job posting said we need someone who can start by the first of next month, and Mr. Adeyemi isn''t available until the summer.
Man 1: Then Ms. Petrova is the better choice.
Man 2: Fair enough. Should we check her references first?
Woman: Yes. I''ll call her former manager this afternoon, and then we can make an offer.', 'TTS', '["Post a job opening", "Interview a candidate", "Prepare a contract", "Contact a reference"]', 'D', 1, '[Part3-다음 행동] 정답 (D). ''I''ll call her former manager this afternoon''에서 추천인인 전 상사에게 연락함을 알 수 있다. 오답: (A) 공고는 이미 냈다. (B) 면접은 이미 끝난 상태다. (C) 제안은 추천인 확인 뒤의 일이고 계약서 준비는 언급되지 않았다.', 46);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Why is the woman calling?', 'Questions 47 through 49 refer to the following conversation.

Man: Good afternoon, Saffron Garden. How may I help you?
Woman: Hello. I''d like to reserve a table for a business dinner this Friday at seven. There will be ten of us.
Man: Let me check. For a group that size, I''d recommend our private dining room. It''s separate from the main floor, so it''s much quieter.
Woman: That sounds ideal. Is there an extra charge?
Man: No, but we do ask large parties to choose from a set menu in advance. I can e-mail you the options.
Woman: Please do. Oh, and two of my colleagues don''t eat meat.
Man: That''s no problem. Every set menu has a vegetarian main course. May I have your e-mail address?', 'TTS', '["To make a dinner reservation", "To cancel a catering order", "To ask about business hours", "To complain about a meal"]', 'A', 1, '[Part3-목적] 정답 (A). ''I''d like to reserve a table for a business dinner this Friday at seven''이 근거다. 오답: (B) 출장 요리 주문이나 취소 이야기는 없다. (C) business dinner의 business를 이용한 오답으로 영업시간은 묻지 않았다. (D) 불만 제기가 아니다.', 47),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man recommend?', 'Questions 47 through 49 refer to the following conversation.

Man: Good afternoon, Saffron Garden. How may I help you?
Woman: Hello. I''d like to reserve a table for a business dinner this Friday at seven. There will be ten of us.
Man: Let me check. For a group that size, I''d recommend our private dining room. It''s separate from the main floor, so it''s much quieter.
Woman: That sounds ideal. Is there an extra charge?
Man: No, but we do ask large parties to choose from a set menu in advance. I can e-mail you the options.
Woman: Please do. Oh, and two of my colleagues don''t eat meat.
Man: That''s no problem. Every set menu has a vegetarian main course. May I have your e-mail address?', 'TTS', '["Arriving early", "Using a private room", "Ordering dessert", "Choosing a different day"]', 'B', 1, '[Part3-제안] 정답 (B). ''For a group that size, I''d recommend our private dining room''이 근거다. 오답: (A) 일찍 오라는 말은 없다. (C) 후식 언급이 없다. (D) 금요일 예약을 그대로 진행한다.', 48),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the woman mention about her colleagues?', 'Questions 47 through 49 refer to the following conversation.

Man: Good afternoon, Saffron Garden. How may I help you?
Woman: Hello. I''d like to reserve a table for a business dinner this Friday at seven. There will be ten of us.
Man: Let me check. For a group that size, I''d recommend our private dining room. It''s separate from the main floor, so it''s much quieter.
Woman: That sounds ideal. Is there an extra charge?
Man: No, but we do ask large parties to choose from a set menu in advance. I can e-mail you the options.
Woman: Please do. Oh, and two of my colleagues don''t eat meat.
Man: That''s no problem. Every set menu has a vegetarian main course. May I have your e-mail address?', 'TTS', '["They are visiting from abroad.", "Some of them have dietary restrictions.", "They will arrive separately.", "Some of them have been there before."]', 'B', 1, '[Part3-세부 사항] 정답 (B). ''two of my colleagues don''t eat meat''를 have dietary restrictions로 바꿔 말했다. 오답: (A) 해외에서 온다는 말은 없다. (C) 따로 도착한다는 말은 없다. (D) 이전 방문 경험은 언급되지 않았다.', 49),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man want to do?', 'Questions 50 through 52 refer to the following conversation.

Woman: Welcome to Harlow Bay Savings Bank. What can I do for you today?
Man: Hi. I recently opened a bakery on Cedar Avenue, and I''d like to apply for a small business loan to buy a second oven.
Woman: Congratulations on the new shop. Our loan officer, Mr. Whitaker, handles those applications, but he''s out of the office until Monday. I can make an appointment for you.
Man: Monday afternoon would be good. What do I need to bring?
Woman: You''ll need your business license and your financial statements for the past six months.
Man: I have the license, but my accountant keeps the statements. I''ll ask her to send me copies today.
Woman: Perfect. I''ve put you down for Monday at two o''clock.', 'TTS', '["Open a savings account", "Rent a commercial space", "Hire an accountant", "Apply for a loan"]', 'D', 1, '[Part3-목적] 정답 (D). ''I''d like to apply for a small business loan to buy a second oven''이 근거다. 오답: (A) 은행 이름의 Savings를 이용한 오답으로 계좌 개설 이야기는 없다. (B) 가게는 이미 열었다. (C) 회계사는 이미 있다.', 50),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Why is the man unable to meet Mr. Whitaker today?', 'Questions 50 through 52 refer to the following conversation.

Woman: Welcome to Harlow Bay Savings Bank. What can I do for you today?
Man: Hi. I recently opened a bakery on Cedar Avenue, and I''d like to apply for a small business loan to buy a second oven.
Woman: Congratulations on the new shop. Our loan officer, Mr. Whitaker, handles those applications, but he''s out of the office until Monday. I can make an appointment for you.
Man: Monday afternoon would be good. What do I need to bring?
Woman: You''ll need your business license and your financial statements for the past six months.
Man: I have the license, but my accountant keeps the statements. I''ll ask her to send me copies today.
Woman: Perfect. I''ve put you down for Monday at two o''clock.', 'TTS', '["The bank is about to close.", "An application is incomplete.", "Mr. Whitaker is with another customer.", "Mr. Whitaker is away from the office."]', 'D', 1, '[Part3-세부 사항(이유)] 정답 (D). ''he''s out of the office until Monday''를 away from the office로 바꿔 말했다. 오답: (A) 폐점 시간 언급이 없다. (B) 신청서는 아직 작성하지도 않았다. (C) 다른 고객을 만나는 중이라는 말은 없다.', 51),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man say he will do today?', 'Questions 50 through 52 refer to the following conversation.

Woman: Welcome to Harlow Bay Savings Bank. What can I do for you today?
Man: Hi. I recently opened a bakery on Cedar Avenue, and I''d like to apply for a small business loan to buy a second oven.
Woman: Congratulations on the new shop. Our loan officer, Mr. Whitaker, handles those applications, but he''s out of the office until Monday. I can make an appointment for you.
Man: Monday afternoon would be good. What do I need to bring?
Woman: You''ll need your business license and your financial statements for the past six months.
Man: I have the license, but my accountant keeps the statements. I''ll ask her to send me copies today.
Woman: Perfect. I''ve put you down for Monday at two o''clock.', 'TTS', '["Renew a license", "Purchase an oven", "Visit another branch", "Request some documents"]', 'D', 1, '[Part3-다음 행동] 정답 (D). ''my accountant keeps the statements. I''ll ask her to send me copies today''에서 재무제표 사본을 요청함을 알 수 있다. 오답: (A) 사업자 등록증은 이미 가지고 있다. (B) 오븐 구입은 대출을 받은 뒤의 일이다. (C) 다른 지점 이야기는 없다.', 52),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Where most likely are the speakers?', 'Questions 53 through 55 refer to the following conversation.

Man: Excuse me. I was on the flight from Weston Ridge that just landed, and my suitcase never came out at baggage claim.
Woman: I''m sorry to hear that, sir. May I see your baggage tag? Thank you. It looks like your bag was loaded onto a later flight. It should arrive here at about nine tonight.
Man: Nine? I''m giving a presentation at a conference first thing tomorrow, and my suit is in that bag.
Woman: We''ll deliver it straight to your hotel at no cost as soon as it comes in. Where are you staying?
Man: At the Bayview Grand, downtown.
Woman: All right. Please write your phone number on this form, and our driver will call you before he arrives.', 'TTS', '["At a hotel", "At an airport", "At a clothing store", "At a conference center"]', 'B', 1, '[Part3-장소] 정답 (B). ''the flight … that just landed'', ''my suitcase never came out at baggage claim''에서 공항 수하물 창구임을 알 수 있다. 오답: (A) 호텔은 가방을 배달받을 곳이다. (C) suit가 나오지만 옷 가게가 아니다. (D) 학회는 내일 발표할 곳이다.', 53),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man say he will do tomorrow?', 'Questions 53 through 55 refer to the following conversation.

Man: Excuse me. I was on the flight from Weston Ridge that just landed, and my suitcase never came out at baggage claim.
Woman: I''m sorry to hear that, sir. May I see your baggage tag? Thank you. It looks like your bag was loaded onto a later flight. It should arrive here at about nine tonight.
Man: Nine? I''m giving a presentation at a conference first thing tomorrow, and my suit is in that bag.
Woman: We''ll deliver it straight to your hotel at no cost as soon as it comes in. Where are you staying?
Man: At the Bayview Grand, downtown.
Woman: All right. Please write your phone number on this form, and our driver will call you before he arrives.', 'TTS', '["Return home", "Give a presentation", "Buy a suit", "Meet a driver"]', 'B', 1, '[Part3-세부 사항] 정답 (B). ''I''m giving a presentation at a conference first thing tomorrow''가 근거다. 오답: (A) 귀가 일정은 언급되지 않았다. (C) 정장은 가방 안에 있다고 했을 뿐 산다는 말은 없다. (D) 기사는 오늘 밤 가방을 배달하러 온다.', 54),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the woman ask the man to do?', 'Questions 53 through 55 refer to the following conversation.

Man: Excuse me. I was on the flight from Weston Ridge that just landed, and my suitcase never came out at baggage claim.
Woman: I''m sorry to hear that, sir. May I see your baggage tag? Thank you. It looks like your bag was loaded onto a later flight. It should arrive here at about nine tonight.
Man: Nine? I''m giving a presentation at a conference first thing tomorrow, and my suit is in that bag.
Woman: We''ll deliver it straight to your hotel at no cost as soon as it comes in. Where are you staying?
Man: At the Bayview Grand, downtown.
Woman: All right. Please write your phone number on this form, and our driver will call you before he arrives.', 'TTS', '["Wait at a counter", "Show a boarding pass", "Fill out a form", "Pay a delivery fee"]', 'C', 1, '[Part3-요청] 정답 (C). ''Please write your phone number on this form''을 Fill out a form으로 바꿔 말했다. 오답: (A) 기다리라는 말은 없고 호텔로 배달해 준다. (B) 보여 달라고 한 것은 탑승권이 아니라 수하물 꼬리표다. (D) ''at no cost''라고 했으므로 배달비가 없다.', 55),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Who most likely is the man?', 'Questions 56 through 58 refer to the following conversation.

Woman: Hi, Tomas. How are you settling in at the store? Is your first week going well?
Man: It is, thanks. Everyone has been helpful. I still need to learn the inventory system, though. I haven''t been trained on it yet.
Woman: That''s why I stopped by. I''m running a training session on it tomorrow at nine in the back office. It takes about two hours.
Man: Great. But I''m scheduled to work at the register tomorrow morning.
Woman: Don''t worry. I''ve already asked Renee to cover your shift until you''re finished.
Man: Thank you. Should I bring anything?
Woman: Just your employee number. You''ll need it to log in. And take a look at the user guide I e-mailed you, if you have time tonight.', 'TTS', '["A new employee", "A software developer", "A store customer", "A delivery person"]', 'A', 1, '[Part3-직업] 정답 (A). ''How are you settling in at the store? Is your first week going well?''에서 남자가 입사 첫 주의 신입 직원임을 알 수 있다. 오답: (B) 재고 시스템을 배우는 쪽이지 만드는 사람이 아니다. (C) 계산대 근무가 잡혀 있는 직원이다. (D) 배달 이야기는 없다.', 56),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What will happen tomorrow morning?', 'Questions 56 through 58 refer to the following conversation.

Woman: Hi, Tomas. How are you settling in at the store? Is your first week going well?
Man: It is, thanks. Everyone has been helpful. I still need to learn the inventory system, though. I haven''t been trained on it yet.
Woman: That''s why I stopped by. I''m running a training session on it tomorrow at nine in the back office. It takes about two hours.
Man: Great. But I''m scheduled to work at the register tomorrow morning.
Woman: Don''t worry. I''ve already asked Renee to cover your shift until you''re finished.
Man: Thank you. Should I bring anything?
Woman: Just your employee number. You''ll need it to log in. And take a look at the user guide I e-mailed you, if you have time tonight.', 'TTS', '["A store will open late.", "A shipment will arrive.", "A register will be replaced.", "A training session will be held."]', 'D', 1, '[Part3-세부 사항(예정)] 정답 (D). ''I''m running a training session on it tomorrow at nine in the back office''가 근거다. 오답: (A) 개점 시간 변경은 없다. (B) 입고 이야기는 없다. (C) 계산대는 남자의 근무 위치로 언급될 뿐 교체되지 않는다.', 57),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the woman suggest the man do?', 'Questions 56 through 58 refer to the following conversation.

Woman: Hi, Tomas. How are you settling in at the store? Is your first week going well?
Man: It is, thanks. Everyone has been helpful. I still need to learn the inventory system, though. I haven''t been trained on it yet.
Woman: That''s why I stopped by. I''m running a training session on it tomorrow at nine in the back office. It takes about two hours.
Man: Great. But I''m scheduled to work at the register tomorrow morning.
Woman: Don''t worry. I''ve already asked Renee to cover your shift until you''re finished.
Man: Thank you. Should I bring anything?
Woman: Just your employee number. You''ll need it to log in. And take a look at the user guide I e-mailed you, if you have time tonight.', 'TTS', '["Review a user guide", "Trade shifts with a coworker", "Arrive an hour early", "Update a password"]', 'A', 1, '[Part3-제안] 정답 (A). ''take a look at the user guide I e-mailed you, if you have time tonight''를 Review a user guide로 바꿔 말했다. 오답: (B) 근무 대체는 여자가 이미 Renee에게 부탁해 두었다. (C) 일찍 오라는 말은 없다. (D) 로그인에 사번이 필요하다고 했을 뿐 비밀번호 변경은 없다.', 58),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What event are the speakers discussing?', 'Questions 59 through 61 refer to the following conversation with three speakers.

Man 1: Thanks for meeting with us, Ms. Lindqvist. We''re organizing the Harlow Bay Summer Food Fair, and we''d love your cafe to take part.
Woman: I''d be interested. When and where is it?
Man 2: It''s on the second Saturday of July, in Riverside Park. We expect around three thousand visitors.
Woman: That''s a big crowd. How much does a booth cost?
Man 2: A hundred and fifty dollars, and that includes a tent and two tables.
Man 1: Vendors do have to bring their own cooking equipment, though.
Woman: That''s fine. We have a portable grill. Is there a deadline to sign up?
Man 1: Yes, registration closes this Friday. I''ll leave an application form with you now.', 'TTS', '["A cafe opening", "A cooking class", "A food fair", "A park cleanup"]', 'C', 1, '[Part3-주제] 정답 (C). ''We''re organizing the Harlow Bay Summer Food Fair''가 근거다. 오답: (A) 카페는 여자가 운영하는 가게일 뿐 개업 행사가 아니다. (B) 조리 기구가 나오지만 수업이 아니다. (D) 공원은 행사 장소일 뿐이다.', 59),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What is included in the booth fee?', 'Questions 59 through 61 refer to the following conversation with three speakers.

Man 1: Thanks for meeting with us, Ms. Lindqvist. We''re organizing the Harlow Bay Summer Food Fair, and we''d love your cafe to take part.
Woman: I''d be interested. When and where is it?
Man 2: It''s on the second Saturday of July, in Riverside Park. We expect around three thousand visitors.
Woman: That''s a big crowd. How much does a booth cost?
Man 2: A hundred and fifty dollars, and that includes a tent and two tables.
Man 1: Vendors do have to bring their own cooking equipment, though.
Woman: That''s fine. We have a portable grill. Is there a deadline to sign up?
Man 1: Yes, registration closes this Friday. I''ll leave an application form with you now.', 'TTS', '["Cooking equipment", "Advertising", "A tent and tables", "Parking passes"]', 'C', 1, '[Part3-세부 사항] 정답 (C). ''A hundred and fifty dollars, and that includes a tent and two tables''가 근거다. 오답: (A) ''Vendors do have to bring their own cooking equipment''라고 했으므로 포함되지 않는다. (B) 광고 언급이 없다. (D) 주차권 언급이 없다.', 60),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What must the woman do by Friday?', 'Questions 59 through 61 refer to the following conversation with three speakers.

Man 1: Thanks for meeting with us, Ms. Lindqvist. We''re organizing the Harlow Bay Summer Food Fair, and we''d love your cafe to take part.
Woman: I''d be interested. When and where is it?
Man 2: It''s on the second Saturday of July, in Riverside Park. We expect around three thousand visitors.
Woman: That''s a big crowd. How much does a booth cost?
Man 2: A hundred and fifty dollars, and that includes a tent and two tables.
Man 1: Vendors do have to bring their own cooking equipment, though.
Woman: That''s fine. We have a portable grill. Is there a deadline to sign up?
Man 1: Yes, registration closes this Friday. I''ll leave an application form with you now.', 'TTS', '["Reserve a parking space", "Submit a menu", "Buy a grill", "Register for the event"]', 'D', 1, '[Part3-세부 사항(기한)] 정답 (D). ''Is there a deadline to sign up?''에 ''registration closes this Friday''라고 답했으므로 금요일까지 참가 등록을 해야 한다. 오답: (A) 주차 이야기는 없다. (B) 메뉴 제출은 요구되지 않았다. (C) 휴대용 그릴은 이미 가지고 있다.', 61),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Why is the woman calling?', 'Questions 62 through 64 refer to the following conversation.

Man: Weston Ridge Family Clinic. This is Andre.
Woman: Hi, Andre. This is Helen Park. I have an appointment with Doctor Simmons tomorrow at ten for my annual checkup, but I''ve just been asked to attend a meeting at that time. Is there anything later in the day?
Man: Let me look. Hmm. Doctor Simmons is fully booked tomorrow afternoon. His next opening is on the nineteenth.
Woman: That''s almost two weeks away.
Man: Well, Doctor Osei has an opening tomorrow at four. She''s new to our clinic, but patients speak very highly of her.
Woman: All right, I''ll take that one.
Man: Great. Since you''ll be seeing a different doctor, please come fifteen minutes early to update your patient information.', 'TTS', '["To ask for test results", "To get directions to a clinic", "To change an appointment time", "To refill a prescription"]', 'C', 1, '[Part3-목적] 정답 (C). 내일 10시 예약이 회의와 겹쳐 ''Is there anything later in the day?''라고 묻고 있으므로 예약 시간 변경이 목적이다. 오답: (A) 검사 결과 이야기는 없다. (B) 길 안내를 묻지 않았다. (D) 처방전 이야기는 없다.', 62),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the woman imply when she says, "That''s almost two weeks away"?', 'Questions 62 through 64 refer to the following conversation.

Man: Weston Ridge Family Clinic. This is Andre.
Woman: Hi, Andre. This is Helen Park. I have an appointment with Doctor Simmons tomorrow at ten for my annual checkup, but I''ve just been asked to attend a meeting at that time. Is there anything later in the day?
Man: Let me look. Hmm. Doctor Simmons is fully booked tomorrow afternoon. His next opening is on the nineteenth.
Woman: That''s almost two weeks away.
Man: Well, Doctor Osei has an opening tomorrow at four. She''s new to our clinic, but patients speak very highly of her.
Woman: All right, I''ll take that one.
Man: Great. Since you''ll be seeing a different doctor, please come fifteen minutes early to update your patient information.', 'TTS', '["She does not want to wait that long.", "She will be out of town then.", "She needs more time to prepare.", "She thinks the date is wrong."]', 'A', 1, '[Part3-화자 의도] 정답 (A). 다음 빈 시간이 19일이라는 말에 대한 반응이고, 이어서 남자가 내일 4시의 다른 의사를 권하자 ''I''ll take that one''이라고 하므로 그렇게 오래 기다리고 싶지 않다는 뜻이다. 오답: (B) 출장·여행 언급이 없다. (C) 준비 시간이 필요하다는 말이 아니다. (D) 날짜가 틀렸다고 지적하는 것이 아니다.', 63),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man ask the woman to do?', 'Questions 62 through 64 refer to the following conversation.

Man: Weston Ridge Family Clinic. This is Andre.
Woman: Hi, Andre. This is Helen Park. I have an appointment with Doctor Simmons tomorrow at ten for my annual checkup, but I''ve just been asked to attend a meeting at that time. Is there anything later in the day?
Man: Let me look. Hmm. Doctor Simmons is fully booked tomorrow afternoon. His next opening is on the nineteenth.
Woman: That''s almost two weeks away.
Man: Well, Doctor Osei has an opening tomorrow at four. She''s new to our clinic, but patients speak very highly of her.
Woman: All right, I''ll take that one.
Man: Great. Since you''ll be seeing a different doctor, please come fifteen minutes early to update your patient information.', 'TTS', '["Bring an insurance card", "Arrive early", "Call back tomorrow", "Pay in advance"]', 'B', 1, '[Part3-요청] 정답 (B). ''please come fifteen minutes early to update your patient information''이 근거다. 오답: (A) 보험증을 가져오라는 말은 없다. (C) 예약은 이미 확정되어 다시 전화할 필요가 없다. (D) 선결제 이야기는 없다.', 64),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Where most likely does the man work?', 'Questions 65 through 67 refer to the following conversation.

Woman: Hi. I''m here to pick up five hundred brochures for Tillman Realty.
Man: Yes, they''re right here. Would you like to look at one before I pack them up?
Woman: Sure. Oh, no. The phone number on the back is wrong. The last digit should be a seven, not a one.
Man: Let me pull up the file you sent. Hmm. I''m afraid the number in your original file ends in a one.
Woman: Then it was our mistake. We need these for an open house on Saturday. How soon could you reprint them?
Man: I can have them ready by noon tomorrow. And since you''re a regular customer, I''ll give you half off the reprint.
Woman: I appreciate that. I''ll e-mail you the corrected file as soon as I''m back at the office.', 'TTS', '["At a real estate agency", "At a post office", "At a print shop", "At a phone store"]', 'C', 1, '[Part3-장소·직업] 정답 (C). 남자가 주문받은 안내 책자를 건네고 ''How soon could you reprint them?''에 ''I can have them ready by noon tomorrow''라고 답하므로 인쇄소 직원이다. 오답: (A) 부동산 회사는 여자의 직장이다. (B) pack them up이 나오지만 우체국이 아니다. (D) phone number가 나오지만 휴대폰 매장이 아니다.', 65),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What problem does the woman notice?', 'Questions 65 through 67 refer to the following conversation.

Woman: Hi. I''m here to pick up five hundred brochures for Tillman Realty.
Man: Yes, they''re right here. Would you like to look at one before I pack them up?
Woman: Sure. Oh, no. The phone number on the back is wrong. The last digit should be a seven, not a one.
Man: Let me pull up the file you sent. Hmm. I''m afraid the number in your original file ends in a one.
Woman: Then it was our mistake. We need these for an open house on Saturday. How soon could you reprint them?
Man: I can have them ready by noon tomorrow. And since you''re a regular customer, I''ll give you half off the reprint.
Woman: I appreciate that. I''ll e-mail you the corrected file as soon as I''m back at the office.', 'TTS', '["An order is too small.", "Some colors are too dark.", "A logo is missing.", "Some contact information is incorrect."]', 'D', 1, '[Part3-세부 사항(문제점)] 정답 (D). ''The phone number on the back is wrong''을 Some contact information is incorrect로 바꿔 말했다. 오답: (A) 수량 문제는 없다. (B) 색상 이야기는 없다. (C) 로고 이야기는 없다.', 66);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man offer the woman?', 'Questions 65 through 67 refer to the following conversation.

Woman: Hi. I''m here to pick up five hundred brochures for Tillman Realty.
Man: Yes, they''re right here. Would you like to look at one before I pack them up?
Woman: Sure. Oh, no. The phone number on the back is wrong. The last digit should be a seven, not a one.
Man: Let me pull up the file you sent. Hmm. I''m afraid the number in your original file ends in a one.
Woman: Then it was our mistake. We need these for an open house on Saturday. How soon could you reprint them?
Man: I can have them ready by noon tomorrow. And since you''re a regular customer, I''ll give you half off the reprint.
Woman: I appreciate that. I''ll e-mail you the corrected file as soon as I''m back at the office.', 'TTS', '["Free delivery", "A larger paper size", "A full refund", "A reduced price"]', 'D', 1, '[Part3-제안(제공)] 정답 (D). ''since you''re a regular customer, I''ll give you half off the reprint''에서 재인쇄 비용을 절반으로 깎아 준다. 오답: (A) 배달 이야기는 없다. (B) 용지 크기 이야기는 없다. (C) 실수는 여자 회사 쪽에 있었고 환불이 아니라 할인이다.', 67),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'According to the man, what will end tomorrow?', 'Questions 68 through 70 refer to the following conversation.

Man: Sofia, have you registered for the logistics conference in Weston Ridge yet? The early rate ends tomorrow.
Woman: I did it this morning. I''m still deciding how to get there, though. Are you driving?
Man: No, I''m taking the express train. The station is right across from the convention center, so I won''t need a taxi.
Woman: That''s convenient. Parking downtown is expensive anyway. Which train are you on?
Man: The seven fifteen on Monday morning. It gets in well before the opening speech.
Woman: I''ll book the same one. Do we pay for the tickets ourselves?
Man: Yes, but keep your receipt. The finance department will pay you back within thirty days.
Woman: Good to know. I''ll buy my ticket online right now.', 'TTS', '["A logistics conference", "A discounted registration rate", "A parking promotion", "A temporary train schedule"]', 'B', 1, '[Part3-세부 사항] 정답 (B). ''have you registered for the logistics conference … The early rate ends tomorrow''에서 조기 등록 할인 요금이 내일 끝남을 알 수 있다. 오답: (A) 학회는 월요일에 시작한다. (C) 주차는 비싸다고만 했다. (D) 열차 시간표 변경 이야기는 없다.', 68),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'Why does the man prefer to take the train?', 'Questions 68 through 70 refer to the following conversation.

Man: Sofia, have you registered for the logistics conference in Weston Ridge yet? The early rate ends tomorrow.
Woman: I did it this morning. I''m still deciding how to get there, though. Are you driving?
Man: No, I''m taking the express train. The station is right across from the convention center, so I won''t need a taxi.
Woman: That''s convenient. Parking downtown is expensive anyway. Which train are you on?
Man: The seven fifteen on Monday morning. It gets in well before the opening speech.
Woman: I''ll book the same one. Do we pay for the tickets ourselves?
Man: Yes, but keep your receipt. The finance department will pay you back within thirty days.
Woman: Good to know. I''ll buy my ticket online right now.', 'TTS', '["The tickets are refundable.", "The station is near the venue.", "His car is being repaired.", "The seats are comfortable."]', 'B', 1, '[Part3-세부 사항(이유)] 정답 (B). ''The station is right across from the convention center, so I won''t need a taxi''를 The station is near the venue로 바꿔 말했다. 오답: (A) 표 환불이 아니라 회사가 비용을 돌려준다는 이야기다. (C) 차 수리 언급이 없다. (D) 좌석 이야기는 없다.', 69),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P3', 'What does the man remind the woman to do?', 'Questions 68 through 70 refer to the following conversation.

Man: Sofia, have you registered for the logistics conference in Weston Ridge yet? The early rate ends tomorrow.
Woman: I did it this morning. I''m still deciding how to get there, though. Are you driving?
Man: No, I''m taking the express train. The station is right across from the convention center, so I won''t need a taxi.
Woman: That''s convenient. Parking downtown is expensive anyway. Which train are you on?
Man: The seven fifteen on Monday morning. It gets in well before the opening speech.
Woman: I''ll book the same one. Do we pay for the tickets ourselves?
Man: Yes, but keep your receipt. The finance department will pay you back within thirty days.
Woman: Good to know. I''ll buy my ticket online right now.', 'TTS', '["Save a receipt", "Reserve a hotel room", "Print a conference badge", "Review a speech"]', 'A', 1, '[Part3-요청·당부] 정답 (A). ''Yes, but keep your receipt. The finance department will pay you back''에서 영수증을 보관하라고 당부한다. 오답: (B) 숙박 이야기는 없다. (C) 명찰 인쇄 이야기는 없다. (D) 개회사는 열차 도착 시각과 관련해 언급될 뿐이다.', 70),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'Why is the speaker calling?', 'Questions 71 through 73 refer to the following telephone message.

Hello, Ms. Castellano. This is Rob from Brightway Flooring, calling about the carpet installation at your law office. We were scheduled to begin tomorrow morning, but the carpet you selected has not arrived from the manufacturer. They told me it was shipped late and should reach us on Thursday. That means the earliest we can start is Friday. I know your staff has already moved the furniture out, so I apologize for the trouble. To make up for it, we''ll clean the carpets in your reception area at no charge. Please call me back at five five five, zero one four two to let me know whether Friday works for you.', 'TTS', '["To report a delay", "To confirm a payment", "To recommend a product", "To request a measurement"]', 'A', 1, '[Part4-목적] 정답 (A). ''We were scheduled to begin tomorrow morning, but the carpet you selected has not arrived''에서 공사가 늦어짐을 알리는 전화임을 알 수 있다. 오답: (B) 결제 이야기는 없다. (C) 카펫은 이미 고객이 골랐다. (D) 치수 측정 요청은 없다.', 71),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'When can the work begin at the earliest?', 'Questions 71 through 73 refer to the following telephone message.

Hello, Ms. Castellano. This is Rob from Brightway Flooring, calling about the carpet installation at your law office. We were scheduled to begin tomorrow morning, but the carpet you selected has not arrived from the manufacturer. They told me it was shipped late and should reach us on Thursday. That means the earliest we can start is Friday. I know your staff has already moved the furniture out, so I apologize for the trouble. To make up for it, we''ll clean the carpets in your reception area at no charge. Please call me back at five five five, zero one four two to let me know whether Friday works for you.', 'TTS', '["Today", "Tomorrow", "On Thursday", "On Friday"]', 'D', 1, '[Part4-세부 사항(시점)] 정답 (D). ''That means the earliest we can start is Friday''가 근거다. 오답: (A) 오늘 시작한다는 말은 없다. (B) 내일은 원래 예정일이었으나 미뤄졌다. (C) 목요일은 카펫이 업체에 도착하는 날이다.', 72),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What does the speaker offer?', 'Questions 71 through 73 refer to the following telephone message.

Hello, Ms. Castellano. This is Rob from Brightway Flooring, calling about the carpet installation at your law office. We were scheduled to begin tomorrow morning, but the carpet you selected has not arrived from the manufacturer. They told me it was shipped late and should reach us on Thursday. That means the earliest we can start is Friday. I know your staff has already moved the furniture out, so I apologize for the trouble. To make up for it, we''ll clean the carpets in your reception area at no charge. Please call me back at five five five, zero one four two to let me know whether Friday works for you.', 'TTS', '["A discount on materials", "A free cleaning service", "A faster shipping method", "Help with moving furniture"]', 'B', 1, '[Part4-제안(제공)] 정답 (B). ''we''ll clean the carpets in your reception area at no charge''를 A free cleaning service로 바꿔 말했다. 오답: (A) 자재 할인은 언급되지 않았다. (C) 배송 방법을 바꾼다는 말은 없다. (D) 가구는 직원들이 이미 옮겼다고 했을 뿐 도와주겠다는 말은 없다.', 73),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What is the announcement mainly about?', 'Questions 74 through 76 refer to the following announcement.

Attention, all employees. This is a reminder from the facilities department. Starting next Monday, the main entrance on Dover Street will be closed for three weeks while the lobby is renovated. During that time, please enter the building through the side door next to the parking garage. You will need your employee badge to open that door, so be sure to carry it with you at all times. If you have lost your badge, visit the security office on the first floor before Friday to get a new one. Also, deliveries will be accepted only at the loading dock behind the building, so please let your suppliers know in advance. Thank you for your patience.', 'TTS', '["A new parking policy", "A security training session", "A change in office hours", "A temporary entrance closure"]', 'D', 1, '[Part4-주제] 정답 (D). ''the main entrance on Dover Street will be closed for three weeks while the lobby is renovated''가 근거다. 오답: (A) 주차장은 옆문 위치를 설명할 때 나올 뿐이다. (B) 보안실은 사원증 재발급 장소일 뿐 교육 이야기는 없다. (C) 근무 시간 변경은 없다.', 74),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What are the listeners reminded to carry?', 'Questions 74 through 76 refer to the following announcement.

Attention, all employees. This is a reminder from the facilities department. Starting next Monday, the main entrance on Dover Street will be closed for three weeks while the lobby is renovated. During that time, please enter the building through the side door next to the parking garage. You will need your employee badge to open that door, so be sure to carry it with you at all times. If you have lost your badge, visit the security office on the first floor before Friday to get a new one. Also, deliveries will be accepted only at the loading dock behind the building, so please let your suppliers know in advance. Thank you for your patience.', 'TTS', '["An identification badge", "A parking permit", "A door key", "A visitor pass"]', 'A', 1, '[Part4-세부 사항] 정답 (A). ''You will need your employee badge to open that door, so be sure to carry it with you at all times''가 근거다. 오답: (B) 주차 허가증 이야기는 없다. (C) 문은 열쇠가 아니라 사원증으로 연다. (D) 청자는 방문객이 아니라 직원이다.', 75),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What should the listeners tell their suppliers?', 'Questions 74 through 76 refer to the following announcement.

Attention, all employees. This is a reminder from the facilities department. Starting next Monday, the main entrance on Dover Street will be closed for three weeks while the lobby is renovated. During that time, please enter the building through the side door next to the parking garage. You will need your employee badge to open that door, so be sure to carry it with you at all times. If you have lost your badge, visit the security office on the first floor before Friday to get a new one. Also, deliveries will be accepted only at the loading dock behind the building, so please let your suppliers know in advance. Thank you for your patience.', 'TTS', '["When the lobby will reopen", "Where deliveries will be accepted", "How to obtain a badge", "Who manages the facilities department"]', 'B', 1, '[Part4-요청] 정답 (B). ''deliveries will be accepted only at the loading dock behind the building, so please let your suppliers know in advance''에서 납품 받는 장소를 알리라고 한다. 오답: (A) 재개방 시점을 알리라는 말은 없다. (C) 사원증 발급은 직원에게 해당한다. (D) 부서 책임자는 언급되지 않았다.', 76),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What type of business is being advertised?', 'Questions 77 through 79 refer to the following advertisement.

Is your company planning to relocate? Let Summit Peak handle it. For more than twenty years, we have helped businesses in Harlow Bay change offices quickly and safely. Our trained crews pack your computers, files, and furniture with care and carry everything to your new address. We also work on evenings and weekends, so your employees never lose a day of work. This month only, customers who book with us receive free packing boxes, as many as they need. Not sure how much it will cost? Visit our website and fill out the short form, and one of our consultants will come to your office to give you an estimate. Summit Peak. We carry the load so you don''t have to.', 'TTS', '["A furniture store", "A computer repair service", "A storage facility", "A moving company"]', 'D', 1, '[Part4-업종] 정답 (D). ''Is your company planning to relocate?'', ''Our trained crews pack your computers, files, and furniture … and carry everything to your new address''에서 이사 업체임을 알 수 있다. 오답: (A) 가구는 옮기는 물건일 뿐이다. (B) 컴퓨터도 옮기는 물건일 뿐 수리하지 않는다. (C) 보관 서비스는 언급되지 않았다.', 77),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'According to the speaker, what is an advantage of the business?', 'Questions 77 through 79 refer to the following advertisement.

Is your company planning to relocate? Let Summit Peak handle it. For more than twenty years, we have helped businesses in Harlow Bay change offices quickly and safely. Our trained crews pack your computers, files, and furniture with care and carry everything to your new address. We also work on evenings and weekends, so your employees never lose a day of work. This month only, customers who book with us receive free packing boxes, as many as they need. Not sure how much it will cost? Visit our website and fill out the short form, and one of our consultants will come to your office to give you an estimate. Summit Peak. We carry the load so you don''t have to.', 'TTS', '["It has several locations.", "It works outside regular business hours.", "It guarantees the lowest prices.", "It hires only local staff."]', 'B', 1, '[Part4-세부 사항(장점)] 정답 (B). ''We also work on evenings and weekends, so your employees never lose a day of work''를 works outside regular business hours로 바꿔 말했다. 오답: (A) 지점 수 언급이 없다. (C) 최저가 보장 이야기는 없다. (D) 직원 채용 기준은 언급되지 않았다.', 78),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What can customers receive this month?', 'Questions 77 through 79 refer to the following advertisement.

Is your company planning to relocate? Let Summit Peak handle it. For more than twenty years, we have helped businesses in Harlow Bay change offices quickly and safely. Our trained crews pack your computers, files, and furniture with care and carry everything to your new address. We also work on evenings and weekends, so your employees never lose a day of work. This month only, customers who book with us receive free packing boxes, as many as they need. Not sure how much it will cost? Visit our website and fill out the short form, and one of our consultants will come to your office to give you an estimate. Summit Peak. We carry the load so you don''t have to.', 'TTS', '["A discounted rate", "Free packing supplies", "A gift card", "Extra insurance coverage"]', 'B', 1, '[Part4-세부 사항(혜택)] 정답 (B). ''This month only, customers who book with us receive free packing boxes''에서 packing boxes를 packing supplies로 바꿔 말했다. 오답: (A) 요금 할인은 언급되지 않았다. (C) 상품권 이야기는 없다. (D) 보험 이야기는 없다.', 79),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What is the broadcast mainly about?', 'Questions 80 through 82 refer to the following radio broadcast.

Good morning, and welcome back to Harlow Bay Community Radio. In local news, the city council has approved plans to build a new public library on Grant Avenue. The current library, which opened sixty years ago, has become too small for the city''s growing population. The new building will have twice as much space, including a children''s reading room and a computer lab. Construction is expected to begin in March and take about eighteen months. The council would like to hear from residents about the design, so a public meeting will be held at city hall this Thursday evening at seven. Right now, I''m joined in the studio by the head librarian, Diane Mercer, who will tell us more about the project. Welcome, Diane.', 'TTS', '["A plan for a new library", "A city council election", "A road construction project", "A children''s reading contest"]', 'A', 1, '[Part4-주제] 정답 (A). ''the city council has approved plans to build a new public library on Grant Avenue''가 근거다. 오답: (B) 시의회는 계획을 승인한 주체일 뿐 선거 이야기는 없다. (C) 공사는 도로가 아니라 도서관 건물이다. (D) 어린이 열람실이 생긴다고 했을 뿐 대회 이야기는 없다.', 80),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'According to the speaker, why is a new building needed?', 'Questions 80 through 82 refer to the following radio broadcast.

Good morning, and welcome back to Harlow Bay Community Radio. In local news, the city council has approved plans to build a new public library on Grant Avenue. The current library, which opened sixty years ago, has become too small for the city''s growing population. The new building will have twice as much space, including a children''s reading room and a computer lab. Construction is expected to begin in March and take about eighteen months. The council would like to hear from residents about the design, so a public meeting will be held at city hall this Thursday evening at seven. Right now, I''m joined in the studio by the head librarian, Diane Mercer, who will tell us more about the project. Welcome, Diane.', 'TTS', '["The current facility is too small.", "The current facility was damaged.", "A lease is about to end.", "A donation was received."]', 'A', 1, '[Part4-세부 사항(이유)] 정답 (A). ''The current library … has become too small for the city''s growing population''이 근거다. 오답: (B) 건물 파손 이야기는 없다. (C) 임대 계약 이야기는 없다. (D) 기부금 이야기는 없다.', 81),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What will the listeners most likely hear next?', 'Questions 80 through 82 refer to the following radio broadcast.

Good morning, and welcome back to Harlow Bay Community Radio. In local news, the city council has approved plans to build a new public library on Grant Avenue. The current library, which opened sixty years ago, has become too small for the city''s growing population. The new building will have twice as much space, including a children''s reading room and a computer lab. Construction is expected to begin in March and take about eighteen months. The council would like to hear from residents about the design, so a public meeting will be held at city hall this Thursday evening at seven. Right now, I''m joined in the studio by the head librarian, Diane Mercer, who will tell us more about the project. Welcome, Diane.', 'TTS', '["A weather report", "A traffic update", "An interview", "A commercial"]', 'C', 1, '[Part4-다음에 들을 내용] 정답 (C). ''Right now, I''m joined in the studio by the head librarian, Diane Mercer, who will tell us more about the project. Welcome, Diane''에서 바로 이어 도서관장과의 대담이 나옴을 알 수 있다. 오답: (A) 날씨 예보 예고는 없다. (B) 교통 정보 예고는 없다. (D) 광고가 이어진다는 말은 없다.', 82),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'Where is the tour taking place?', 'Questions 83 through 85 refer to the following tour information.

Welcome to the Marrow Creek Chocolate Factory. My name is Luis, and I''ll be your guide this morning. Our tour will last about forty-five minutes. We''ll start in the roasting room, where the cocoa beans are prepared, and then move on to the production floor to watch the chocolate being poured into molds. Please remember, this is a working factory. Everyone must wear a hair net and a protective coat, which you''ll find on the table by the door. Also, if you have questions, feel free to ask me at any time. At the end of the tour, we''ll stop in the tasting room, where you can sample our newest flavors. Now, please put on your safety gear, and we''ll get started.', 'TTS', '["At a coffee roaster", "At a bakery", "At a chocolate factory", "At a clothing factory"]', 'C', 1, '[Part4-장소] 정답 (C). ''Welcome to the Marrow Creek Chocolate Factory''가 근거다. 오답: (A) roasting room에서 볶는 것은 커피가 아니라 카카오 콩이다. (B) 제과점이 아니다. (D) protective coat는 위생복일 뿐 의류 공장이 아니다.', 83),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'Why does the speaker say, "this is a working factory"?', 'Questions 83 through 85 refer to the following tour information.

Welcome to the Marrow Creek Chocolate Factory. My name is Luis, and I''ll be your guide this morning. Our tour will last about forty-five minutes. We''ll start in the roasting room, where the cocoa beans are prepared, and then move on to the production floor to watch the chocolate being poured into molds. Please remember, this is a working factory. Everyone must wear a hair net and a protective coat, which you''ll find on the table by the door. Also, if you have questions, feel free to ask me at any time. At the end of the tour, we''ll stop in the tasting room, where you can sample our newest flavors. Now, please put on your safety gear, and we''ll get started.', 'TTS', '["To apologize for the noise", "To explain why the tour is short", "To give the reason for a safety rule", "To encourage the listeners to apply for jobs"]', 'C', 1, '[Part4-화자 의도] 정답 (C). 이 말 바로 뒤에 ''Everyone must wear a hair net and a protective coat''가 이어지므로, 실제 가동 중인 공장이라 위생·안전 복장을 갖춰야 한다는 이유를 대는 말이다. 오답: (A) 소음 이야기는 없다. (B) 견학 시간이 짧다고 설명하지 않았다. (D) 채용 이야기는 없다.', 84),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What will the listeners do at the end of the tour?', 'Questions 83 through 85 refer to the following tour information.

Welcome to the Marrow Creek Chocolate Factory. My name is Luis, and I''ll be your guide this morning. Our tour will last about forty-five minutes. We''ll start in the roasting room, where the cocoa beans are prepared, and then move on to the production floor to watch the chocolate being poured into molds. Please remember, this is a working factory. Everyone must wear a hair net and a protective coat, which you''ll find on the table by the door. Also, if you have questions, feel free to ask me at any time. At the end of the tour, we''ll stop in the tasting room, where you can sample our newest flavors. Now, please put on your safety gear, and we''ll get started.', 'TTS', '["Watch a video", "Meet the owner", "Receive a discount coupon", "Try some products"]', 'D', 1, '[Part4-세부 사항(마지막 일정)] 정답 (D). ''At the end of the tour, we''ll stop in the tasting room, where you can sample our newest flavors''에서 sample을 Try some products로 바꿔 말했다. 오답: (A) 영상 시청은 없다. (B) 사장을 만난다는 말은 없다. (C) 할인 쿠폰 이야기는 없다.', 85),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What did customers complain about?', 'Questions 86 through 88 refer to the following excerpt from a meeting.

Before we finish, I''d like to talk about the customer survey results from last quarter. Overall, shoppers are pleased with our products and our prices. However, many of them said they had to wait too long at the checkout counters, especially on weekends. We added two more registers last year. Clearly, that wasn''t enough. So, starting next month, we''re going to install four self-checkout machines near the front entrance. Customers with only a few items can pay there by themselves. I''ll need some of you to stand by the machines and help shoppers during the first few weeks. If you''re willing to do that, please write your name on the sheet by the door before you leave today.', 'TTS', '["Long waits to pay", "High prices", "A limited product selection", "Unfriendly employees"]', 'A', 1, '[Part4-세부 사항(문제점)] 정답 (A). ''many of them said they had to wait too long at the checkout counters''가 근거다. 오답: (B), (C) ''shoppers are pleased with our products and our prices''라고 했으므로 가격과 상품에는 만족한다. (D) 직원 태도 이야기는 없다.', 86);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What does the speaker imply by saying, "Clearly, that wasn''t enough"?', 'Questions 86 through 88 refer to the following excerpt from a meeting.

Before we finish, I''d like to talk about the customer survey results from last quarter. Overall, shoppers are pleased with our products and our prices. However, many of them said they had to wait too long at the checkout counters, especially on weekends. We added two more registers last year. Clearly, that wasn''t enough. So, starting next month, we''re going to install four self-checkout machines near the front entrance. Customers with only a few items can pay there by themselves. I''ll need some of you to stand by the machines and help shoppers during the first few weeks. If you''re willing to do that, please write your name on the sheet by the door before you leave today.', 'TTS', '["An earlier measure did not solve a problem.", "A survey did not include enough customers.", "A budget needs to be increased.", "Some machines were delivered late."]', 'A', 1, '[Part4-화자 의도] 정답 (A). 바로 앞의 ''We added two more registers last year''를 가리키는 말로, 작년에 계산대를 늘렸는데도 대기 시간 문제가 해결되지 않았다는 뜻이다. 오답: (B) 설문 참여 인원 이야기가 아니다. (C) 예산 이야기는 없다. (D) 기계는 다음 달에 설치될 예정이며 배송 지연 이야기는 없다.', 87),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What are the listeners asked to do before they leave?', 'Questions 86 through 88 refer to the following excerpt from a meeting.

Before we finish, I''d like to talk about the customer survey results from last quarter. Overall, shoppers are pleased with our products and our prices. However, many of them said they had to wait too long at the checkout counters, especially on weekends. We added two more registers last year. Clearly, that wasn''t enough. So, starting next month, we''re going to install four self-checkout machines near the front entrance. Customers with only a few items can pay there by themselves. I''ll need some of you to stand by the machines and help shoppers during the first few weeks. If you''re willing to do that, please write your name on the sheet by the door before you leave today.', 'TTS', '["Complete a survey", "Sign up to assist customers", "Test a new machine", "Pick up a work schedule"]', 'B', 1, '[Part4-요청] 정답 (B). ''I''ll need some of you to stand by the machines and help shoppers … please write your name on the sheet by the door before you leave today''에서 고객을 도울 사람은 이름을 적으라고 한다. 오답: (A) 설문은 고객이 이미 한 것이다. (C) 기계는 다음 달에 설치된다. (D) 근무표를 가져가라는 말은 없다.', 88),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What is the cause of the delay?', 'Questions 89 through 91 refer to the following public announcement.

Attention, passengers waiting for the ten forty express train to Weston Ridge. Because of signal repairs near Corbin Station, this train will depart approximately thirty minutes late. We apologize for the inconvenience. Please also note that the train will now leave from platform six instead of platform two. Passengers who need to reach Weston Ridge sooner may take the local bus, which leaves from the front of the station every fifteen minutes. While you wait, the snack bar next to the ticket office is offering a free cup of coffee or tea to delayed passengers. Simply show your train ticket at the counter. Thank you for traveling with Coastline Rail.', 'TTS', '["Bad weather", "Repair work on the line", "A shortage of staff", "A late connecting train"]', 'B', 1, '[Part4-세부 사항(원인)] 정답 (B). ''Because of signal repairs near Corbin Station, this train will depart approximately thirty minutes late''가 근거다. 오답: (A) 날씨 이야기는 없다. (C) 인력 부족 이야기는 없다. (D) 다른 열차의 연착 이야기는 없다.', 89),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'According to the speaker, what has changed besides the departure time?', 'Questions 89 through 91 refer to the following public announcement.

Attention, passengers waiting for the ten forty express train to Weston Ridge. Because of signal repairs near Corbin Station, this train will depart approximately thirty minutes late. We apologize for the inconvenience. Please also note that the train will now leave from platform six instead of platform two. Passengers who need to reach Weston Ridge sooner may take the local bus, which leaves from the front of the station every fifteen minutes. While you wait, the snack bar next to the ticket office is offering a free cup of coffee or tea to delayed passengers. Simply show your train ticket at the counter. Thank you for traveling with Coastline Rail.', 'TTS', '["The ticket price", "The destination", "The number of stops", "The platform"]', 'D', 1, '[Part4-세부 사항(변경 사항)] 정답 (D). ''the train will now leave from platform six instead of platform two''가 근거다. 오답: (A) 요금 변경은 없다. (B) 목적지는 그대로 Weston Ridge다. (C) 정차역 수 이야기는 없다.', 90),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What should passengers show to get a free drink?', 'Questions 89 through 91 refer to the following public announcement.

Attention, passengers waiting for the ten forty express train to Weston Ridge. Because of signal repairs near Corbin Station, this train will depart approximately thirty minutes late. We apologize for the inconvenience. Please also note that the train will now leave from platform six instead of platform two. Passengers who need to reach Weston Ridge sooner may take the local bus, which leaves from the front of the station every fifteen minutes. While you wait, the snack bar next to the ticket office is offering a free cup of coffee or tea to delayed passengers. Simply show your train ticket at the counter. Thank you for traveling with Coastline Rail.', 'TTS', '["A membership card", "A train ticket", "A store receipt", "A printed coupon"]', 'B', 1, '[Part4-세부 사항] 정답 (B). ''offering a free cup of coffee or tea to delayed passengers. Simply show your train ticket at the counter''가 근거다. 오답: (A) 회원 카드 이야기는 없다. (C) 영수증 이야기는 없다. (D) 쿠폰 이야기는 없다.', 91),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'Where is the introduction most likely being given?', 'Questions 92 through 94 refer to the following introduction.

Good evening, everyone, and thank you for attending the annual dinner of the Harlow Bay Small Business Association. It''s my pleasure to introduce tonight''s speaker, Ms. Naomi Feldstein. Fifteen years ago, Ms. Feldstein started selling handmade soap at a weekend market. Today, her company has forty employees and ships its products to stores across the country. Tonight she''ll share how she found reliable suppliers as her business expanded. After her talk, there will be time for questions, so please write yours on the cards at your tables, and our volunteers will collect them. And don''t leave early, because copies of her new book will be given away at the door. Now, please join me in welcoming Ms. Feldstein.', 'TTS', '["At a bookstore", "At a weekend market", "At a company orientation", "At an association dinner"]', 'D', 1, '[Part4-장소] 정답 (D). ''thank you for attending the annual dinner of the Harlow Bay Small Business Association''이 근거다. 오답: (A) 책은 문 앞에서 나눠 줄 증정품일 뿐이다. (B) 주말 시장은 연사가 15년 전에 사업을 시작한 곳이다. (C) 신입 사원 교육이 아니다.', 92),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What will Ms. Feldstein talk about?', 'Questions 92 through 94 refer to the following introduction.

Good evening, everyone, and thank you for attending the annual dinner of the Harlow Bay Small Business Association. It''s my pleasure to introduce tonight''s speaker, Ms. Naomi Feldstein. Fifteen years ago, Ms. Feldstein started selling handmade soap at a weekend market. Today, her company has forty employees and ships its products to stores across the country. Tonight she''ll share how she found reliable suppliers as her business expanded. After her talk, there will be time for questions, so please write yours on the cards at your tables, and our volunteers will collect them. And don''t leave early, because copies of her new book will be given away at the door. Now, please join me in welcoming Ms. Feldstein.', 'TTS', '["Designing product packaging", "Recruiting employees", "Finding dependable suppliers", "Opening retail stores"]', 'C', 1, '[Part4-세부 사항(강연 주제)] 정답 (C). ''Tonight she''ll share how she found reliable suppliers as her business expanded''에서 reliable을 dependable로 바꿔 말했다. 오답: (A) 포장 디자인 이야기는 없다. (B) 직원이 40명이라고 했을 뿐 채용이 강연 주제는 아니다. (D) 전국 매장에 납품한다고 했을 뿐 직접 매장을 연다는 말은 없다.', 93),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What are the listeners asked to do?', 'Questions 92 through 94 refer to the following introduction.

Good evening, everyone, and thank you for attending the annual dinner of the Harlow Bay Small Business Association. It''s my pleasure to introduce tonight''s speaker, Ms. Naomi Feldstein. Fifteen years ago, Ms. Feldstein started selling handmade soap at a weekend market. Today, her company has forty employees and ships its products to stores across the country. Tonight she''ll share how she found reliable suppliers as her business expanded. After her talk, there will be time for questions, so please write yours on the cards at your tables, and our volunteers will collect them. And don''t leave early, because copies of her new book will be given away at the door. Now, please join me in welcoming Ms. Feldstein.', 'TTS', '["Purchase a book", "Turn off their phones", "Write down their questions", "Move to another table"]', 'C', 1, '[Part4-요청] 정답 (C). ''please write yours on the cards at your tables''에서 질문을 카드에 적어 달라고 한다. 오답: (A) 책은 ''given away''이므로 사는 것이 아니라 무료로 받는다. (B) 휴대폰 이야기는 없다. (D) 자리 이동 요청은 없다.', 94),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What kind of organization recorded the message?', 'Questions 95 through 97 refer to the following recorded message.

Thank you for calling the Weston Ridge Museum of Science. The museum is open Tuesday through Sunday, from ten in the morning until five in the afternoon. We are closed on Mondays. Please note that our planetarium is closed for an upgrade to its sound system and will reopen on the first of May. All other exhibits are open as usual. This month, do not miss our special exhibit on ocean life, which is included in the regular admission price. Groups of fifteen or more qualify for a reduced rate, but they must book at least one week in advance. To make a group reservation, press one now. To hear this message again, press two.', 'TTS', '["A museum", "A concert hall", "An aquarium", "A travel agency"]', 'A', 1, '[Part4-화자·업종] 정답 (A). ''Thank you for calling the Weston Ridge Museum of Science''가 근거다. 오답: (B) sound system이 나오지만 공연장이 아니다. (C) ocean life는 박물관의 특별 전시 주제일 뿐이다. (D) 단체 예약이 나오지만 여행사가 아니다.', 95),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'Why is the planetarium closed?', 'Questions 95 through 97 refer to the following recorded message.

Thank you for calling the Weston Ridge Museum of Science. The museum is open Tuesday through Sunday, from ten in the morning until five in the afternoon. We are closed on Mondays. Please note that our planetarium is closed for an upgrade to its sound system and will reopen on the first of May. All other exhibits are open as usual. This month, do not miss our special exhibit on ocean life, which is included in the regular admission price. Groups of fifteen or more qualify for a reduced rate, but they must book at least one week in advance. To make a group reservation, press one now. To hear this message again, press two.', 'TTS', '["A private event is being held.", "Some equipment is being improved.", "Staff members are being trained.", "Attendance has been low."]', 'B', 1, '[Part4-세부 사항(이유)] 정답 (B). ''our planetarium is closed for an upgrade to its sound system''을 Some equipment is being improved로 바꿔 말했다. 오답: (A) 대관 행사 이야기는 없다. (C) 직원 교육 이야기는 없다. (D) 관람객 수 이야기는 없다.', 96),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'According to the message, what must groups do to get a reduced rate?', 'Questions 95 through 97 refer to the following recorded message.

Thank you for calling the Weston Ridge Museum of Science. The museum is open Tuesday through Sunday, from ten in the morning until five in the afternoon. We are closed on Mondays. Please note that our planetarium is closed for an upgrade to its sound system and will reopen on the first of May. All other exhibits are open as usual. This month, do not miss our special exhibit on ocean life, which is included in the regular admission price. Groups of fifteen or more qualify for a reduced rate, but they must book at least one week in advance. To make a group reservation, press one now. To hear this message again, press two.', 'TTS', '["Make a reservation in advance", "Show a membership card", "Pay by credit card", "Visit on a weekday"]', 'A', 1, '[Part4-세부 사항(조건)] 정답 (A). ''Groups of fifteen or more qualify for a reduced rate, but they must book at least one week in advance''가 근거다. 오답: (B) 회원 카드 이야기는 없다. (C) 결제 수단 이야기는 없다. (D) 평일 방문 조건은 없다.', 97),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'Who most likely are the listeners?', 'Questions 98 through 100 refer to the following talk.

Good morning, and welcome to your first day of customer service training here at Delmar Communications. Over the next three days, you''ll learn how to answer calls, look up account information, and solve the most common billing problems. This morning, I''ll explain our telephone system. Then, after lunch, each of you will sit with an experienced representative and listen to real customer calls. Tomorrow you''ll begin answering calls yourselves, with a supervisor nearby to help. Before we start, please open the folder in front of you. Inside, there''s a form asking for your bank details, so that we can set up your salary payments. Please complete it and hand it to me by the end of the day.', 'TTS', '["Bank tellers", "Telephone repair technicians", "Newly hired representatives", "Longtime customers"]', 'C', 1, '[Part4-청자] 정답 (C). ''welcome to your first day of customer service training'', ''set up your salary payments''에서 새로 입사한 고객 상담 직원들임을 알 수 있다. 오답: (A) bank details는 급여 이체용 계좌 정보일 뿐이다. (B) 전화 시스템 사용법을 배울 뿐 수리 기사가 아니다. (D) 급여를 받는 직원이지 고객이 아니다.', 98),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What will the listeners do after lunch?', 'Questions 98 through 100 refer to the following talk.

Good morning, and welcome to your first day of customer service training here at Delmar Communications. Over the next three days, you''ll learn how to answer calls, look up account information, and solve the most common billing problems. This morning, I''ll explain our telephone system. Then, after lunch, each of you will sit with an experienced representative and listen to real customer calls. Tomorrow you''ll begin answering calls yourselves, with a supervisor nearby to help. Before we start, please open the folder in front of you. Inside, there''s a form asking for your bank details, so that we can set up your salary payments. Please complete it and hand it to me by the end of the day.', 'TTS', '["Take a written test", "Answer calls on their own", "Tour the building", "Observe experienced coworkers"]', 'D', 1, '[Part4-세부 사항(일정)] 정답 (D). ''after lunch, each of you will sit with an experienced representative and listen to real customer calls''를 Observe experienced coworkers로 바꿔 말했다. 오답: (A) 시험 이야기는 없다. (B) 직접 전화를 받는 것은 내일부터다. (C) 건물 견학 이야기는 없다.', 99),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P4', 'What are the listeners asked to hand in by the end of the day?', 'Questions 98 through 100 refer to the following talk.

Good morning, and welcome to your first day of customer service training here at Delmar Communications. Over the next three days, you''ll learn how to answer calls, look up account information, and solve the most common billing problems. This morning, I''ll explain our telephone system. Then, after lunch, each of you will sit with an experienced representative and listen to real customer calls. Tomorrow you''ll begin answering calls yourselves, with a supervisor nearby to help. Before we start, please open the folder in front of you. Inside, there''s a form asking for your bank details, so that we can set up your salary payments. Please complete it and hand it to me by the end of the day.', 'TTS', '["A training evaluation", "A photo for a badge", "A completed form", "A signed contract"]', 'C', 1, '[Part4-요청] 정답 (C). ''there''s a form asking for your bank details … Please complete it and hand it to me by the end of the day''가 근거다. 오답: (A) 교육 평가서 이야기는 없다. (B) 사원증 사진 이야기는 없다. (D) 서류는 계약서가 아니라 계좌 정보 양식이다.', 100),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'The front-desk staff at Hotel Marivelle responded ------- to every guest request during the busy holiday weekend.', NULL, 'NONE', '["prompt", "promptly", "promptness", "prompted"]', 'B', 1, '[문법-품사(부사 자리)] 정답 (B). 자동사 responded와 전치사구 to every guest request 사이에서 동사를 꾸미는 부사 자리이므로 promptly가 맞다. 오답: (A) 형용사는 동사를 꾸미지 못한다. (C) 명사는 respond의 목적어가 될 수 없다(respond는 to와 함께 쓰는 자동사). (D) 과거형·과거분사로 동사 뒤 수식어 자리에 올 수 없다.', 101),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Ms. Okafor asked the interns to bring any scheduling conflicts to ------- attention before the weekly planning meeting on Monday.', NULL, 'NONE', '["her", "hers", "herself", "she"]', 'A', 1, '[문법-대명사(소유격)] 정답 (A). 명사 attention 앞에서 한정사 역할을 하는 소유격 자리이며 bring A to one''s attention(A를 ~에게 알리다) 결합이다. 오답: (B) 소유대명사는 뒤에 명사를 두지 못한다. (C) 재귀대명사는 명사 앞 한정사로 쓰지 못한다. 주어 Ms. Okafor와 같은 인물이라는 이유만으로 고르면 안 된다. (D) 주격은 전치사 to 뒤 명사구 안에 올 수 없다.', 102),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'The Harlow Bay Public Library will remain closed ------- the renovation of its second-floor reading room is complete.', NULL, 'NONE', '["until", "during", "within", "upon"]', 'A', 1, '[접속사(절·구 구별, 시간)] 정답 (A). 빈칸 뒤에 주어(the renovation)와 동사(is)를 갖춘 절이 오므로 접속사로 쓸 수 있는 until이 필요하고, ''공사가 끝날 때까지 휴관''이라는 뜻도 맞다. 오답: (B) during, (C) within, (D) upon은 모두 전치사라서 뒤에 절을 이끌지 못한다.', 103),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Trellick Freight will ------- a temporary gate pass to each driver who registers at the security office.', NULL, 'NONE', '["resolve", "issue", "attend", "consume"]', 'B', 1, '[어휘-동사(문맥별 뜻 issue)] 정답 (B). a temporary gate pass(임시 출입증)를 목적어로 하고 to each driver로 받는 사람을 나타내므로 ''발급하다''의 issue가 맞다. 오답: (A) resolve는 문제·분쟁을 해결하다, (C) attend는 행사에 참석하다, (D) consume은 소비하다라는 뜻이어서 출입증을 운전자에게 내주는 행동과 맞지 않는다.', 104),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'All dental instruments at Brindle Dental Care ------- after each appointment in accordance with regional health regulations.', NULL, 'NONE', '["are sterilized", "sterilize", "are sterilizing", "have sterilized"]', 'A', 1, '[문법-태(수동)] 정답 (A). 주어 instruments는 소독을 받는 대상이고 빈칸 뒤에 목적어가 없으므로 수동태 are sterilized가 맞다. 오답: (B) sterilize, (C) are sterilizing, (D) have sterilized는 모두 능동형이라 타동사 sterilize의 목적어가 필요한데 문장에 목적어가 없다.', 105),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Refunds will be processed within five business days upon ------- of the returned merchandise at our Weston Ridge warehouse.', NULL, 'NONE', '["permission", "attendance", "estimate", "receipt"]', 'D', 1, '[어휘-명사(문맥별 뜻 receipt)] 정답 (D). upon receipt of는 ''~을 수령하는 즉시''라는 뜻으로, 반품된 상품이 창고에 도착하면 환불을 처리한다는 문맥에 맞다. 여기서 receipt는 ''영수증''이 아니라 ''수령''이다. 오답: (A) permission 허가, (B) attendance 참석, (C) estimate 견적은 반품 상품을 받는 시점을 나타내지 못한다.', 106);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Customer feedback suggests that the new scheduling software is highly ------- to changes in staff availability.', NULL, 'NONE', '["responsive", "responsively", "response", "respond"]', 'A', 1, '[문법-품사(형용사 보어)] 정답 (A). be동사 is 뒤에서 주어 software의 상태를 설명하는 보어 자리이고 부사 highly의 수식을 받으므로 형용사 responsive가 맞다(responsive to ~에 잘 반응하는). 오답: (B) 부사는 보어가 될 수 없다. highly 뒤라고 부사를 고르면 안 된다. (C) 명사는 highly의 수식을 받지 못한다. (D) 동사원형은 is 뒤에 올 수 없다.', 107),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Mr. Delacroix is ------- for coordinating shipments between the Harlow Bay port and the inland distribution center.', NULL, 'NONE', '["capable", "responsible", "accustomed", "familiar"]', 'B', 1, '[어휘-형용사(전치사 결합)] 정답 (B). 빈칸 뒤의 for + 동명사와 결합해 ''~을 담당하다''라는 뜻을 만드는 것은 responsible for이다. 오답: (A) capable은 of, (C) accustomed는 to, (D) familiar는 with와 결합하므로 for coordinating 앞에 올 수 없다.', 108),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'The number of patrons borrowing electronic books from the Weston Ridge branch ------- steadily since the mobile application was introduced.', NULL, 'NONE', '["have risen", "are rising", "rise", "has risen"]', 'D', 1, '[문법-수일치(the number of + 현재완료)] 정답 (D). 주어의 중심은 단수 The number이고, since + 과거 시점이 있으므로 단수 현재완료 has risen이 맞다. 오답: (A) have risen, (B) are rising, (C) rise는 모두 복수 동사로, 가까이 있는 patrons·books에 잘못 맞춘 형태다.', 109),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Please ------- all guests of the pool closure scheduled for next Thursday morning by placing a notice in each room.', NULL, 'NONE', '["announce", "explain", "notify", "mention"]', 'C', 1, '[어휘-동사(문형: 사람 of 내용)] 정답 (C). 사람(all guests)을 목적어로 두고 of + 내용을 잇는 문형은 notify 사람 of 내용이다. 오답: (A) announce, (B) explain, (D) mention은 내용을 목적어로 두고 사람은 to로 연결하는 동사(announce/explain/mention 내용 to 사람)라서 all guests of the pool closure 구조에 맞지 않는다.', 110),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'The brochure ------- the hotel''s newly renovated conference facilities will be mailed to corporate clients next week.', NULL, 'NONE', '["describe", "described", "describes", "describing"]', 'D', 1, '[문법-분사(능동·수동)] 정답 (D). 본동사는 will be mailed이므로 빈칸은 The brochure를 뒤에서 꾸미는 수식어 자리다. 안내 책자가 시설을 ''설명하는'' 능동 관계이고 뒤에 목적어(the hotel''s ... facilities)가 있으므로 현재분사 describing이 맞다. 오답: (A)·(C)는 정형동사라 본동사가 둘이 된다. (B) 과거분사는 뒤에 목적어를 둘 수 없다.', 111),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', '------- the new oven may reduce baking time, Dovecote Bakery has decided to keep its current production schedule for now.', NULL, 'NONE', '["So that", "Because", "As soon as", "Although"]', 'D', 1, '[접속사(양보·목적 구별)] 정답 (D). ''새 오븐이 굽는 시간을 줄일 수도 있지만, 당분간 현재 생산 일정을 유지하기로 했다''는 양보 관계이므로 Although가 맞다. 오답: (A) So that은 목적을 나타내는데 may가 보인다고 고르면 안 된다. 뒤 절이 그 목적의 수단이 아니다. (B) Because는 원인·결과가 되어 내용이 모순된다. (C) As soon as는 시간 관계로 may reduce와 어울리지 않는다.', 112),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Aldermoor Design Studio has requested the city''s ------- to add a rooftop terrace to the Marlowe Street office building.', NULL, 'NONE', '["permission", "permissive", "permissibly", "permitted"]', 'A', 1, '[문법-품사(명사 자리)] 정답 (A). 소유격 the city''s 뒤이자 has requested의 목적어 자리이므로 명사 permission이 맞다(permission to do ~할 허가). 오답: (B) 형용사는 뒤에 꾸밀 명사가 없다. (C) 부사는 소유격 뒤 목적어 자리에 올 수 없다. (D) 과거분사·과거형은 명사구의 중심이 될 수 없다.', 113),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', '------- at the annual publishing conference in Harlow Bay has risen by 20 percent compared with last year.', NULL, 'NONE', '["Attendance", "Attendant", "Attendees", "Attendants"]', 'A', 1, '[어휘-명사(같은 어근의 행위·사람 명사)] 정답 (A). 20퍼센트 늘어난 것은 ''참석자 수·참석률''이므로 행위·수치를 나타내는 불가산명사 Attendance가 맞고 단수 동사 has와도 일치한다. 오답: (B) Attendant(안내원)는 가산명사라 관사 없이 단수로 쓸 수 없고 뜻도 맞지 않는다. (C) Attendees(참석자들), (D) Attendants(안내원들)는 복수라서 has와 수가 맞지 않는다.', 114),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'The building inspector has requested that the revised floor plans ------- to her office no later than Friday.', NULL, 'NONE', '["to be sent", "be sent", "were sent", "sending"]', 'B', 1, '[문법-제안절(that절 동사원형)] 정답 (B). 요구를 나타내는 request 뒤의 that절은 (should) + 동사원형을 쓰며, 도면은 보내지는 대상이므로 수동의 원형 be sent가 맞다. 오답: (A) to부정사와 (D) -ing는 that절의 동사가 될 수 없다. (C) were sent는 과거 사실을 말하는 형태로 ''금요일까지 보내 달라''는 요구의 내용과 맞지 않는다.', 115),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Dovecote Bakery''s downtown location has been ------- since two of its bakers transferred to the new Harlow Bay store.', NULL, 'NONE', '["outdated", "overpriced", "understaffed", "widespread"]', 'C', 1, '[어휘-형용사(understaffed)] 정답 (C). 제빵사 두 명이 다른 매장으로 옮긴 뒤의 상태이므로 ''인력이 부족한''이라는 understaffed가 맞다. 오답: (A) outdated 구식인, (B) overpriced 값이 너무 비싼, (D) widespread 널리 퍼진은 직원이 빠져나간 결과를 나타내지 못한다.', 116),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Architects ------- designs are selected for the waterfront project will be invited to present them to the city council in May.', NULL, 'NONE', '["who", "which", "whose", "their"]', 'C', 1, '[문법-관계사(소유격 whose)] 정답 (C). 관계절 안에 주어 designs와 동사 are selected가 모두 있고, 선행사 Architects와 designs가 ''건축가들의 설계안''이라는 소유 관계이므로 whose가 맞다. 오답: (A) who, (B) which는 관계절 안에서 주어나 목적어 역할을 해야 하는데 빈 자리가 없다. (D) their는 대명사라 두 절을 연결하지 못한다.', 117),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Rarely ------- Trellick Freight miss a guaranteed delivery deadline, even during the busiest weeks of the holiday shipping season.', NULL, 'NONE', '["has", "is", "do", "does"]', 'D', 1, '[문법-도치(부정어 문두)] 정답 (D). 부정 부사 Rarely가 문두에 오면 조동사 + 주어 + 동사 순서로 도치된다. 주어 Trellick Freight는 단수이고 뒤에 동사원형 miss가 있으므로 does가 맞다. 오답: (A) has 뒤에는 p.p.(missed)가 와야 한다. (B) is 뒤에는 원형이 올 수 없다. (C) do는 단수 주어와 수가 맞지 않는다.', 118),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Before returning the lease agreement, please ------- the bottom of each page to show that you have read it.', NULL, 'NONE', '["notify", "waive", "initial", "enclose"]', 'C', 1, '[어휘-동사(동사로 쓰는 initial)] 정답 (C). 각 쪽 아래에 표시를 해서 읽었음을 확인하는 행동이므로 ''머리글자로 서명하다''라는 동사 initial이 맞다. 형용사 ''최초의''로만 알고 있으면 놓치기 쉽다. 오답: (A) notify는 사람을 목적어로 한다. (B) waive는 권리·요금을 포기·면제하다, (D) enclose는 동봉하다라는 뜻이어서 the bottom of each page와 맞지 않는다.', 119),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Room rates listed on the hotel''s Web site are ------- to change without notice during the summer festival period.', NULL, 'NONE', '["capable", "subject", "dependent", "exempt"]', 'B', 1, '[어휘-형용사(subject to)] 정답 (B). be subject to change는 ''변경될 수 있다''라는 뜻으로, 요금이 예고 없이 바뀔 수 있다는 문맥과 전치사 to에 모두 맞다. 오답: (A) capable은 of, (C) dependent는 on, (D) exempt는 from과 결합하므로 to change 앞에 올 수 없다.', 120),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Ms. Lindqvist, who joined Corvanta Software last spring, now ------- the team that develops its mobile banking application.', NULL, 'NONE', '["led", "was leading", "had led", "leads"]', 'D', 1, '[문법-시제(주절과 관계절의 시간 단서)] 정답 (D). last spring은 관계절(who joined ...)의 시간 표현일 뿐이고, 주절에는 now가 있으며 뒤의 관계절도 현재(develops)이므로 주절 동사는 현재형 leads가 맞다. 오답: (A) led, (B) was leading, (C) had led는 모두 과거 계열로, 관계절의 last spring을 주절에까지 적용한 형태다.', 121),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Ms. Tanaka was praised for ------- a five-year supply contract between Dovecote Bakery and a regional flour mill.', NULL, 'NONE', '["brokering", "inhabiting", "resembling", "commuting"]', 'A', 1, '[어휘-동사(동사로 쓰는 broker)] 정답 (A). 두 회사 사이의 공급 계약을 성사시킨 것이므로 ''(계약·합의를) 중개하다''라는 동사 broker의 -ing형이 맞다. 오답: (B) inhabit 거주하다, (C) resemble 닮다는 contract를 목적어로 하면 뜻이 통하지 않는다. (D) commute 통근하다는 자동사라 목적어를 취하지 않는다.', 122),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', '------- designing residential buildings, Aldermoor Design Studio provides consulting services for historic restoration projects.', NULL, 'NONE', '["In addition to", "Even though", "In order that", "As long as"]', 'A', 1, '[전치사(구 전치사와 접속사 구별)] 정답 (A). 빈칸 뒤에 주어 없는 동명사구(designing residential buildings)가 오므로 전치사 역할을 하는 In addition to(~ 외에도)가 맞다. 오답: (B) Even though, (C) In order that, (D) As long as는 접속사라서 뒤에 주어와 동사를 갖춘 절이 와야 한다.', 123),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'The training module was designed ------- for dental assistants who have less than one year of clinical experience.', NULL, 'NONE', '["specific", "specifically", "specify", "specification"]', 'B', 1, '[문법-품사(부사 자리)] 정답 (B). 수동태 was designed로 문장이 이미 완성되어 있고 빈칸은 동사(구)를 꾸미는 자리이므로 부사 specifically(특별히 ~을 위해)가 맞다. 오답: (A) 형용사는 꾸밀 명사가 없다. (C) 동사는 was designed 뒤에 또 올 수 없다. (D) 명사는 수동태 뒤에서 목적어가 될 수 없다.', 124),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', '------- the structural survey, the engineers began drafting recommendations for reinforcing the library''s east wing.', NULL, 'NONE', '["Complete", "Completed", "To be completed", "Having completed"]', 'D', 1, '[문법-분사(완료 분사구문)] 정답 (D). 주절의 주어 the engineers가 조사를 ''끝낸'' 능동 관계이고, 조사를 끝낸 일이 권고안 작성(began)보다 먼저이므로 완료 분사구문 Having completed가 맞다. 뒤에 목적어 the structural survey도 있다. 오답: (A) 동사원형·형용사로는 분사구문을 만들 수 없다. (B) 과거분사와 (C) 수동 부정사는 뒤에 목적어를 둘 수 없다.', 125),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'The two versions of the scheduling software are ------- identical, differing only in the layout of the settings menu.', NULL, 'NONE', '["eagerly", "tightly", "nearly", "punctually"]', 'C', 1, '[어휘-부사] 정답 (C). 설정 메뉴의 배치만 다르다고 했으므로 두 버전은 ''거의'' 같다는 nearly identical이 맞다. 오답: (A) eagerly 열심히, (B) tightly 단단히, (D) punctually 시간을 지켜는 형용사 identical의 정도를 나타내지 못한다.', 126);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', '------- the terms of the contract, Halvorsen Printing must deliver finished copies within thirty days of final approval.', NULL, 'NONE', '["Among", "Along", "Under", "Onto"]', 'C', 1, '[전치사] 정답 (C). under the terms of the contract는 ''계약 조건에 따라''라는 뜻의 굳어진 결합이다. 오답: (A) Among은 셋 이상의 대상 ''사이에서'', (B) Along은 길·선을 ''따라서''(공간), (D) Onto는 ''~ 위로''라는 이동을 나타내어 규정·조건에 따른다는 뜻을 만들지 못한다.', 127),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Because ------- of the equipment in the clinic''s laboratory is more than ten years old, the director has requested funding for replacements.', NULL, 'NONE', '["many", "few", "much", "several"]', 'C', 1, '[문법-대명사(수량 표현·가산성)] 정답 (C). equipment는 불가산명사이고 동사도 단수 is이므로 불가산명사와 쓰는 수량 표현 much가 맞다. 오답: (A) many, (B) few, (D) several은 모두 복수 가산명사와 쓰고 복수 동사를 취하므로 of the equipment ... is와 맞지 않는다.', 128),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Quillhaven Press reported ------- higher revenue this year, largely because of strong sales of its digital textbooks.', NULL, 'NONE', '["substantial", "substantially", "substance", "substantiate"]', 'B', 1, '[문법-품사(비교급을 꾸미는 부사)] 정답 (B). 빈칸은 비교급 형용사 higher를 꾸며 정도를 강조하는 자리이므로 부사 substantially(상당히)가 맞다. 오답: (A) 형용사는 다른 형용사 higher를 꾸미지 못한다. (C) 명사와 (D) 동사는 reported와 higher revenue 사이에 올 수 없다.', 129),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P5', 'Staff at the Harlow Bay office may work remotely on Fridays ------- they obtain written approval from their department manager in advance.', NULL, 'NONE', '["whereas", "provided that", "in case of", "so as to"]', 'B', 1, '[접속사(조건)] 정답 (B). 뒤에 절(they obtain ...)이 오고 ''사전에 서면 승인을 받는다는 조건으로 재택근무가 가능하다''는 뜻이므로 조건 접속사 provided that이 맞다. 오답: (A) whereas는 대조를 나타내어 뜻이 통하지 않는다. (C) in case of는 전치사구라 뒤에 명사가 와야 한다. (D) so as to 뒤에는 동사원형이 와야 한다.', 130),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (131).', 'Questions 131-134 refer to the following e-mail.

To: Priya Raman
From: Corvanta Software Client Services
Date: March 5
Subject: Your license renewal

Dear Ms. Raman,

Thank you for renewing your firm''s Corvanta Planner license for another year. Your payment (131) ------- on March 3. A receipt is attached to this message.

As a returning client, you are entitled to free (132) ------- training for up to ten employees. Sessions can be booked through the Learning Center page of our Web site, and your staff can join from any computer with an Internet connection.

We would also like to remind you that version 8.0 of the program will be released next month. (133) -------. Simply open the program and select "Update" when prompted.

If you have any questions, please contact (134) ------- support team at any time.

Sincerely,

Corvanta Software Client Services', 'NONE', '["is receiving", "was received", "will be received", "has received"]', 'B', 1, '[Part6-문법(시제·태·문맥)] 정답 (B). 앞 문장에서 갱신에 감사하고 바로 뒤 문장에서 영수증을 첨부했다(A receipt is attached)고 하므로 결제는 이미 끝난 일이고, payment는 받아지는 대상이므로 과거 수동 was received가 맞다. 오답: (A) is receiving, (D) has received는 능동이라 목적어가 필요하다. (C) will be received는 미래라서 영수증이 이미 발행된 상황과 맞지 않는다.', 131),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (132).', 'Questions 131-134 refer to the following e-mail.

To: Priya Raman
From: Corvanta Software Client Services
Date: March 5
Subject: Your license renewal

Dear Ms. Raman,

Thank you for renewing your firm''s Corvanta Planner license for another year. Your payment (131) ------- on March 3. A receipt is attached to this message.

As a returning client, you are entitled to free (132) ------- training for up to ten employees. Sessions can be booked through the Learning Center page of our Web site, and your staff can join from any computer with an Internet connection.

We would also like to remind you that version 8.0 of the program will be released next month. (133) -------. Simply open the program and select "Update" when prompted.

If you have any questions, please contact (134) ------- support team at any time.

Sincerely,

Corvanta Software Client Services', 'NONE', '["online", "on-site", "classroom", "telephone"]', 'A', 1, '[Part6-어휘(문맥, 뒤 문장 단서)] 정답 (A). 바로 뒤 문장에서 웹사이트의 Learning Center에서 예약하고 ''인터넷에 연결된 컴퓨터 어디서나 참여할 수 있다''고 했으므로 online training이 맞다. 오답: (B) on-site(현장), (C) classroom(강의실)은 특정 장소에 모여야 하므로 any computer와 맞지 않는다. (D) telephone(전화)은 컴퓨터와 인터넷 연결로 참여한다는 설명과 다르다.', 132),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (133).', 'Questions 131-134 refer to the following e-mail.

To: Priya Raman
From: Corvanta Software Client Services
Date: March 5
Subject: Your license renewal

Dear Ms. Raman,

Thank you for renewing your firm''s Corvanta Planner license for another year. Your payment (131) ------- on March 3. A receipt is attached to this message.

As a returning client, you are entitled to free (132) ------- training for up to ten employees. Sessions can be booked through the Learning Center page of our Web site, and your staff can join from any computer with an Internet connection.

We would also like to remind you that version 8.0 of the program will be released next month. (133) -------. Simply open the program and select "Update" when prompted.

If you have any questions, please contact (134) ------- support team at any time.

Sincerely,

Corvanta Software Client Services', 'NONE', '["Current license holders can install it at no additional cost.", "Unfortunately, the release has been canceled.", "Our offices will be closed during that period.", "Your license expired at the end of last year."]', 'A', 1, '[Part6-문장삽입] 정답 (A). 앞 문장의 version 8.0을 it으로 받고, 뒤 문장 ''Simply open the program and select "Update"''가 그 설치 방법을 설명하므로 ''현재 사용권 보유자는 추가 비용 없이 설치할 수 있다''가 들어가야 한다. 오답: (B) 출시가 취소되었다면 뒤의 업데이트 방법 안내와 모순된다. (C) 사무실 휴무는 뒤의 설치 방법과 이어지지 않는다. (D) 첫 문단에서 사용권을 갱신했다고 했으므로 만료되었다는 말은 맞지 않는다.', 133),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (134).', 'Questions 131-134 refer to the following e-mail.

To: Priya Raman
From: Corvanta Software Client Services
Date: March 5
Subject: Your license renewal

Dear Ms. Raman,

Thank you for renewing your firm''s Corvanta Planner license for another year. Your payment (131) ------- on March 3. A receipt is attached to this message.

As a returning client, you are entitled to free (132) ------- training for up to ten employees. Sessions can be booked through the Learning Center page of our Web site, and your staff can join from any computer with an Internet connection.

We would also like to remind you that version 8.0 of the program will be released next month. (133) -------. Simply open the program and select "Update" when prompted.

If you have any questions, please contact (134) ------- support team at any time.

Sincerely,

Corvanta Software Client Services', 'NONE', '["your", "its", "their", "our"]', 'D', 1, '[Part6-대명사(지시 대상)] 정답 (D). 이 이메일의 발신자는 Corvanta Software Client Services이고 지원팀은 발신 회사에 속하므로, 발신자 쪽을 가리키는 our가 맞다(앞의 our Web site와 같은 관계). 오답: (A) your는 수신자인 Ms. Raman 회사의 팀이 되어 문의처가 될 수 없다. (B) its, (C) their는 가리킬 3인칭 대상이 앞 문장에 없다.', 134),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (135).', 'Questions 135-138 refer to the following notice.

Harlow Bay Public Library
Notice to Patrons

Beginning June 1, a valid library card (135) ------- to enter the second-floor study rooms. Patrons should scan their card at the reader beside each door. This change is intended to keep the rooms available for the people who have reserved them.

Reservations can be made up to seven days in advance at the information desk or on the library''s Web site. (136) -------, each patron may book no more than two hours per day. We ask that you leave promptly when your session ends so that the next group can begin on time.

Patrons who have lost their card can obtain a (137) ------- for a three-dollar fee. (138) -------. The new card will be ready within a few minutes.', 'NONE', '["will be required", "will require", "requiring", "has required"]', 'A', 1, '[Part6-문법(동사 자리·태)] 정답 (A). 문장에 본동사가 없으므로 정형동사가 필요하고, 주어 a valid library card는 ''요구되는'' 대상이며 뒤에 목적어가 없으므로 수동태 will be required가 맞다. Beginning June 1이라는 앞으로의 시점과도 맞는다. 오답: (B) will require, (D) has required는 능동이라 목적어가 필요하다. (C) requiring은 분사라서 본동사가 될 수 없다.', 135),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (136).', 'Questions 135-138 refer to the following notice.

Harlow Bay Public Library
Notice to Patrons

Beginning June 1, a valid library card (135) ------- to enter the second-floor study rooms. Patrons should scan their card at the reader beside each door. This change is intended to keep the rooms available for the people who have reserved them.

Reservations can be made up to seven days in advance at the information desk or on the library''s Web site. (136) -------, each patron may book no more than two hours per day. We ask that you leave promptly when your session ends so that the next group can begin on time.

Patrons who have lost their card can obtain a (137) ------- for a three-dollar fee. (138) -------. The new card will be ready within a few minutes.', 'NONE', '["Therefore", "For example", "Otherwise", "However"]', 'D', 1, '[Part6-연결어] 정답 (D). 앞 문장은 7일 전부터 예약할 수 있다는 허용 내용이고, 빈칸 문장은 하루 두 시간까지만 예약할 수 있다는 제한이므로 대조의 However가 맞다. 오답: (A) Therefore는 앞 문장이 제한의 이유가 아니므로 맞지 않는다. (B) For example은 뒤 문장이 앞 문장의 예시가 아니다. (C) Otherwise는 ''그렇지 않으면''이라는 가정으로 앞뒤 관계와 다르다.', 136),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (137).', 'Questions 135-138 refer to the following notice.

Harlow Bay Public Library
Notice to Patrons

Beginning June 1, a valid library card (135) ------- to enter the second-floor study rooms. Patrons should scan their card at the reader beside each door. This change is intended to keep the rooms available for the people who have reserved them.

Reservations can be made up to seven days in advance at the information desk or on the library''s Web site. (136) -------, each patron may book no more than two hours per day. We ask that you leave promptly when your session ends so that the next group can begin on time.

Patrons who have lost their card can obtain a (137) ------- for a three-dollar fee. (138) -------. The new card will be ready within a few minutes.', 'NONE', '["refund", "replacement", "reservation", "reminder"]', 'B', 1, '[Part6-어휘(문맥)] 정답 (B). 카드를 분실한 이용자가 수수료를 내고 받는 것이고, 뒤에 ''The new card will be ready''라고 했으므로 ''재발급 카드(대체품)''인 replacement가 맞다. 오답: (A) refund 환불은 수수료를 내고 받는 것이 아니다. (C) reservation 예약은 앞 문단의 내용이며 카드 분실과 관계없다. (D) reminder 알림은 뒤의 the new card와 이어지지 않는다.', 137),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (138).', 'Questions 135-138 refer to the following notice.

Harlow Bay Public Library
Notice to Patrons

Beginning June 1, a valid library card (135) ------- to enter the second-floor study rooms. Patrons should scan their card at the reader beside each door. This change is intended to keep the rooms available for the people who have reserved them.

Reservations can be made up to seven days in advance at the information desk or on the library''s Web site. (136) -------, each patron may book no more than two hours per day. We ask that you leave promptly when your session ends so that the next group can begin on time.

Patrons who have lost their card can obtain a (137) ------- for a three-dollar fee. (138) -------. The new card will be ready within a few minutes.', 'NONE', '["Simply bring photo identification to the circulation desk.", "The study rooms are located on the second floor.", "The library will be closed to the public on June 1.", "Overdue books may be returned at any branch."]', 'A', 1, '[Part6-문장삽입] 정답 (A). 앞 문장이 분실 카드의 재발급을 말하고 뒤 문장이 ''새 카드는 몇 분 안에 준비된다''고 하므로, 그 사이에는 재발급 방법(사진이 있는 신분증을 대출 창구로 가져오기)이 와야 한다. 오답: (B) 학습실 위치는 첫 문단의 내용으로 카드 재발급과 이어지지 않는다. (C) 6월 1일은 새 규정이 시작되는 날이므로 휴관은 글의 내용과 맞지 않는다. (D) 연체 도서 반납은 글에서 다루지 않는 소재다.', 138),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (139).', 'Questions 139-142 refer to the following article.

WESTON RIDGE (May 4) — Dovecote Bakery, the regional chain known for its sourdough loaves, will open its twelfth store next month on Canal Street. The company (139) ------- the site of a former bookstore. Renovation of the building began last November and is now nearly complete.

According to operations director Hugo Marchetti, the new store will be (140) ------- larger than the chain''s other locations. It will include a forty-seat café and a glass-walled kitchen where customers can watch the bakers at work.

Dovecote plans to hire about twenty-five employees for the Canal Street store. (141) -------. Interested candidates may apply in person at any Dovecote store or through the company''s Web site.

Mr. Marchetti said the chain is also (142) ------- sites in Harlow Bay for a possible thirteenth store.', 'NONE', '["will purchase", "is purchasing", "purchases", "purchased"]', 'D', 1, '[Part6-문법(시제·문맥)] 정답 (D). 바로 뒤 문장에서 건물 개조 공사가 지난 11월에 시작되어 거의 끝났다고 했으므로 부지 매입은 그보다 앞선 과거의 일이다. 따라서 과거형 purchased가 맞다. 오답: (A) will purchase는 미래, (B) is purchasing은 진행 중, (C) purchases는 현재의 반복을 나타내어 이미 공사가 거의 끝난 상황과 맞지 않는다.', 139),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (140).', 'Questions 139-142 refer to the following article.

WESTON RIDGE (May 4) — Dovecote Bakery, the regional chain known for its sourdough loaves, will open its twelfth store next month on Canal Street. The company (139) ------- the site of a former bookstore. Renovation of the building began last November and is now nearly complete.

According to operations director Hugo Marchetti, the new store will be (140) ------- larger than the chain''s other locations. It will include a forty-seat café and a glass-walled kitchen where customers can watch the bakers at work.

Dovecote plans to hire about twenty-five employees for the Canal Street store. (141) -------. Interested candidates may apply in person at any Dovecote store or through the company''s Web site.

Mr. Marchetti said the chain is also (142) ------- sites in Harlow Bay for a possible thirteenth store.', 'NONE', '["consider", "considerable", "considerably", "consideration"]', 'C', 1, '[Part6-문법(품사·비교급 강조)] 정답 (C). 빈칸은 비교급 larger를 꾸며 정도를 나타내는 자리이므로 부사 considerably(상당히)가 맞다. 오답: (A) 동사는 will be 뒤 비교급 앞에 올 수 없다. (B) 형용사는 다른 형용사 larger를 꾸미지 못한다. (D) 명사는 larger의 수식어가 될 수 없다.', 140),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (141).', 'Questions 139-142 refer to the following article.

WESTON RIDGE (May 4) — Dovecote Bakery, the regional chain known for its sourdough loaves, will open its twelfth store next month on Canal Street. The company (139) ------- the site of a former bookstore. Renovation of the building began last November and is now nearly complete.

According to operations director Hugo Marchetti, the new store will be (140) ------- larger than the chain''s other locations. It will include a forty-seat café and a glass-walled kitchen where customers can watch the bakers at work.

Dovecote plans to hire about twenty-five employees for the Canal Street store. (141) -------. Interested candidates may apply in person at any Dovecote store or through the company''s Web site.

Mr. Marchetti said the chain is also (142) ------- sites in Harlow Bay for a possible thirteenth store.', 'NONE', '["The bookstore closed after thirty years in business.", "Prices at the café have not yet been announced.", "Most of the openings are for bakers and counter staff.", "The store''s opening has been postponed until next year."]', 'C', 1, '[Part6-문장삽입] 정답 (C). 앞 문장이 약 25명을 채용할 계획이라고 하고 뒤 문장이 지원 방법(Interested candidates may apply ...)을 안내하므로, 그 사이에는 채용 직종을 말하는 문장이 와야 한다. openings가 앞의 hire, 뒤의 candidates와 이어진다. 오답: (A) 서점의 폐업과 (B) 카페 가격은 채용 이야기 사이에 끼어들 수 없다. (D) 첫 문장에서 다음 달 개점한다고 했으므로 내년으로 연기되었다는 말은 모순이다.', 141),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (142).', 'Questions 139-142 refer to the following article.

WESTON RIDGE (May 4) — Dovecote Bakery, the regional chain known for its sourdough loaves, will open its twelfth store next month on Canal Street. The company (139) ------- the site of a former bookstore. Renovation of the building began last November and is now nearly complete.

According to operations director Hugo Marchetti, the new store will be (140) ------- larger than the chain''s other locations. It will include a forty-seat café and a glass-walled kitchen where customers can watch the bakers at work.

Dovecote plans to hire about twenty-five employees for the Canal Street store. (141) -------. Interested candidates may apply in person at any Dovecote store or through the company''s Web site.

Mr. Marchetti said the chain is also (142) ------- sites in Harlow Bay for a possible thirteenth store.', 'NONE', '["vacating", "evaluating", "advertising", "inheriting"]', 'B', 1, '[Part6-어휘(문맥)] 정답 (B). a possible thirteenth store(열 수도 있는 열세 번째 매장)를 위해 Harlow Bay의 부지들을 ''검토하고 있다''는 뜻이므로 evaluating이 맞다. 오답: (A) vacating(비우다)은 새 매장을 열려는 계획과 반대다. (C) advertising(광고하다)은 아직 정해지지 않은 매장 부지에 맞지 않는다. (D) inheriting(물려받다)은 부지를 찾는 상황과 관계없다.', 142),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (143).', 'Questions 143-146 refer to the following memo.

To: All Warehouse Staff
From: Renata Osei, Operations Manager
Date: September 9
Subject: New loading dock schedule

Starting September 16, the Weston Ridge depot will assign each carrier a fixed thirty-minute window for pickups. Over the past few months, trucks arriving at the same time have caused long lines at the loading docks. (143) -------.

Under the new system, carriers must reserve a window at least one day in advance. Drivers who present a reservation code at the gate will be directed to a dock immediately. (144) -------, those without a code will be asked to wait until a window opens. We expect the change to (145) ------- waiting times considerably.

A training session for dock supervisors (146) ------- on September 12 at 2:00 P.M. in the break room. Attendance is mandatory, so please plan accordingly.', 'NONE', '["The depot will be closed for the rest of the month.", "Several carriers have praised our fast loading times.", "The new schedule is intended to prevent such delays.", "All trucks must be inspected once a year."]', 'C', 1, '[Part6-문장삽입] 정답 (C). 앞 문장이 트럭이 한꺼번에 도착해 하역장에 긴 줄이 생겼다는 문제를 말하므로, such delays로 그 문제를 받아 새 일정의 목적을 밝히는 문장이 이어져야 한다. 오답: (A) 창고가 문을 닫는다면 9월 16일부터 새 제도를 시행한다는 내용과 모순된다. (B) 빠른 하역을 칭찬받았다는 말은 긴 줄이 생겼다는 앞 문장과 반대다. (D) 트럭 정기 점검은 글의 소재가 아니다.', 143),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (144).', 'Questions 143-146 refer to the following memo.

To: All Warehouse Staff
From: Renata Osei, Operations Manager
Date: September 9
Subject: New loading dock schedule

Starting September 16, the Weston Ridge depot will assign each carrier a fixed thirty-minute window for pickups. Over the past few months, trucks arriving at the same time have caused long lines at the loading docks. (143) -------.

Under the new system, carriers must reserve a window at least one day in advance. Drivers who present a reservation code at the gate will be directed to a dock immediately. (144) -------, those without a code will be asked to wait until a window opens. We expect the change to (145) ------- waiting times considerably.

A training session for dock supervisors (146) ------- on September 12 at 2:00 P.M. in the break room. Attendance is mandatory, so please plan accordingly.', 'NONE', '["Similarly", "In contrast", "For instance", "As a result"]', 'B', 1, '[Part6-연결어] 정답 (B). 앞 문장은 예약 코드를 제시한 운전자는 곧바로 하역장으로 안내된다는 내용이고, 빈칸 문장은 코드가 없는 운전자는 기다려야 한다는 내용이므로 두 경우를 대조하는 In contrast가 맞다. 오답: (A) Similarly는 비슷한 내용을 덧붙일 때 쓴다. (C) For instance는 뒤 문장이 앞 문장의 예시일 때 쓴다. (D) As a result는 코드가 없는 운전자의 대기가 앞 문장의 결과가 아니므로 맞지 않는다.', 144),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (145).', 'Questions 143-146 refer to the following memo.

To: All Warehouse Staff
From: Renata Osei, Operations Manager
Date: September 9
Subject: New loading dock schedule

Starting September 16, the Weston Ridge depot will assign each carrier a fixed thirty-minute window for pickups. Over the past few months, trucks arriving at the same time have caused long lines at the loading docks. (143) -------.

Under the new system, carriers must reserve a window at least one day in advance. Drivers who present a reservation code at the gate will be directed to a dock immediately. (144) -------, those without a code will be asked to wait until a window opens. We expect the change to (145) ------- waiting times considerably.

A training session for dock supervisors (146) ------- on September 12 at 2:00 P.M. in the break room. Attendance is mandatory, so please plan accordingly.', 'NONE', '["extend", "measure", "record", "shorten"]', 'D', 1, '[Part6-어휘(문맥)] 정답 (D). 첫 문단에서 긴 줄이 문제였고 새 제도는 그것을 해결하려는 것이므로, 이 변화로 대기 시간이 크게 ''줄어들'' 것으로 기대한다는 shorten이 맞다. 오답: (A) extend(늘리다)는 제도의 목적과 반대다. (B) measure(측정하다), (C) record(기록하다)는 정도 부사 considerably와 어울리지 않고 제도의 기대 효과도 아니다.', 145),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P6', 'Choose the best answer for blank (146).', 'Questions 143-146 refer to the following memo.

To: All Warehouse Staff
From: Renata Osei, Operations Manager
Date: September 9
Subject: New loading dock schedule

Starting September 16, the Weston Ridge depot will assign each carrier a fixed thirty-minute window for pickups. Over the past few months, trucks arriving at the same time have caused long lines at the loading docks. (143) -------.

Under the new system, carriers must reserve a window at least one day in advance. Drivers who present a reservation code at the gate will be directed to a dock immediately. (144) -------, those without a code will be asked to wait until a window opens. We expect the change to (145) ------- waiting times considerably.

A training session for dock supervisors (146) ------- on September 12 at 2:00 P.M. in the break room. Attendance is mandatory, so please plan accordingly.', 'NONE', '["held", "was held", "will be held", "is holding"]', 'C', 1, '[Part6-문법(시제·태·문맥)] 정답 (C). 메모 작성일이 9월 9일이고 교육은 9월 12일이며, 뒤 문장에서 참석이 필수이니 일정을 조정하라고 하므로 앞으로 열릴 일이다. 교육은 ''열리는'' 대상이므로 미래 수동 will be held가 맞다. 오답: (A) held, (D) is holding은 능동이라 목적어가 필요하다. (B) was held는 과거라서 작성일보다 뒤인 날짜와 맞지 않는다.', 146);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is being offered for a limited time?', 'Questions 147-148 refer to the following advertisement.

Bayline Print & Sign
14 Quay Street, Harlow Bay

Getting ready for the Harbor Lights Festival? Let us help your booth stand out!

Through August 31, order any vinyl banner and receive a matching tabletop sign at no charge. Our banners are printed on weather-resistant material and are ready for pickup within two business days. Need yours sooner? Same-day service is available for an additional $25.

Not sure about your design? Our in-house designers will always review your artwork free of charge and suggest improvements before printing.

To place an order, upload your file at www.baylineprint.com or stop by our shop. Please note that orders cannot be accepted by phone.', 'NONE', '["A lower price on same-day printing", "Free delivery to festival booths", "A reduced fee for design work", "A complimentary item with a purchase"]', 'D', 1, '[세부 사항] 정답 (D). ''Through August 31, order any vinyl banner and receive a matching tabletop sign at no charge''에서 8월 31일까지 현수막을 주문하면 탁상용 표지판을 무료로 준다고 했다. at no charge가 complimentary로, tabletop sign이 item으로 바뀌었다. 오답: (A) 당일 인쇄는 25달러 추가 요금이 붙는다. (B) 배송 이야기는 없고 직접 찾아가는(pickup) 방식이다. (C) 디자인 검토는 기한 없이 언제나(always) 무료다.', 147),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about Bayline Print & Sign?', 'Questions 147-148 refer to the following advertisement.

Bayline Print & Sign
14 Quay Street, Harlow Bay

Getting ready for the Harbor Lights Festival? Let us help your booth stand out!

Through August 31, order any vinyl banner and receive a matching tabletop sign at no charge. Our banners are printed on weather-resistant material and are ready for pickup within two business days. Need yours sooner? Same-day service is available for an additional $25.

Not sure about your design? Our in-house designers will always review your artwork free of charge and suggest improvements before printing.

To place an order, upload your file at www.baylineprint.com or stop by our shop. Please note that orders cannot be accepted by phone.', 'NONE', '["It has opened a second shop in Harlow Bay.", "It takes banner orders over the telephone.", "It charges customers for reviewing artwork.", "It can finish some orders on the day they are placed."]', 'D', 1, '[사실 확인] 정답 (D). ''Same-day service is available for an additional $25''에서 추가 요금을 내면 주문 당일에 완성해 준다는 것을 알 수 있다. 오답: (A) 매장은 Quay Street 한 곳만 나온다. (B) ''orders cannot be accepted by phone''과 반대다. (C) 디자인 검토는 ''free of charge''라고 했다.', 148),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'Why did Mr. Oyelaran contact Ms. Venkataraman?', 'Questions 149-150 refer to the following text-message chain.

Marcus Oyelaran (7:52 A.M.)
Priya, my 8:40 flight to Weston Ridge has been pushed back to 11:15 because of fog. I won''t make the 10:30 walk-through at the Delmont Foods plant.

Priya Venkataraman (7:55 A.M.)
Shall I ask Ms. Delmont''s office to reschedule?

Marcus Oyelaran (7:57 A.M.)
Yes, please. Any time after 2:00 would be fine. Could you also tell the car service that I now land at 12:25?

Priya Venkataraman (8:06 A.M.)
Done. Ms. Delmont''s assistant says 3:00 is open, but the plant manager leaves at 4:00, so the walk-through can''t run long.

Marcus Oyelaran (8:08 A.M.)
That works. I only need about 45 minutes on the production floor. I''ll go over the contract terms with her by phone next week instead.

Priya Venkataraman (8:09 A.M.)
I''ll send you an updated itinerary.', 'NONE', '["To cancel a visit to a client''s plant", "To ask her to book a different flight", "To report that a contract has been signed", "To get help changing his arrangements"]', 'D', 1, '[주제·목적] 정답 (D). 안개로 비행기가 늦어져 10시 30분 일정에 갈 수 없게 되자 ''Yes, please''로 일정 재조정을 부탁하고 ''Could you also tell the car service …''라고 차량 시간 변경도 부탁한다. 즉 바뀐 일정에 맞춰 준비를 고쳐 달라는 연락이다. 오답: (A) 방문은 취소가 아니라 3시로 옮겨진다. (B) 다른 항공편 예약을 부탁하지 않았다. (C) 계약 조건은 다음 주에 전화로 논의할 예정이다.', 149),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'At 8:08 A.M., what does Mr. Oyelaran most likely mean when he writes, "That works"?', 'Questions 149-150 refer to the following text-message chain.

Marcus Oyelaran (7:52 A.M.)
Priya, my 8:40 flight to Weston Ridge has been pushed back to 11:15 because of fog. I won''t make the 10:30 walk-through at the Delmont Foods plant.

Priya Venkataraman (7:55 A.M.)
Shall I ask Ms. Delmont''s office to reschedule?

Marcus Oyelaran (7:57 A.M.)
Yes, please. Any time after 2:00 would be fine. Could you also tell the car service that I now land at 12:25?

Priya Venkataraman (8:06 A.M.)
Done. Ms. Delmont''s assistant says 3:00 is open, but the plant manager leaves at 4:00, so the walk-through can''t run long.

Marcus Oyelaran (8:08 A.M.)
That works. I only need about 45 minutes on the production floor. I''ll go over the contract terms with her by phone next week instead.

Priya Venkataraman (8:09 A.M.)
I''ll send you an updated itinerary.', 'NONE', '["He expects the fog to clear before noon.", "He wants the plant manager to stay later.", "He can finish the visit in the time available.", "He will discuss the contract during the visit."]', 'C', 1, '[의도 파악] 정답 (C). 바로 앞에서 3시에 시작할 수 있지만 공장장이 4시에 떠난다는 말을 듣고 ''That works''라고 한 뒤 ''I only need about 45 minutes''라고 덧붙인다. 주어진 한 시간 안에 견학을 끝낼 수 있다는 뜻이다. 오답: (A) 날씨 전망은 언급이 없다. (B) 공장장에게 더 있어 달라고 하지 않는다. (D) 계약 조건은 ''by phone next week instead''라고 했다.', 150),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is the purpose of the e-mail?', 'Questions 151-152 refer to the following e-mail.

To: Elena Kovacs
From: Tidewater Accounting Cloud <billing@tidewatercloud.com>
Date: March 3
Subject: Your plan renews soon

Dear Ms. Kovacs,

Your Tidewater Standard plan will renew automatically on March 17, and $228 will be charged to the card on file for the coming year. No action is needed if you wish to continue.

Since you joined, we have added receipt scanning and multi-currency invoicing to the Standard plan at no extra cost. If your business has grown, you may want to consider the Team plan, which supports up to ten users.

To change or cancel your plan, visit the Billing page of your account before the renewal date. A renewal charge can be refunded in full only within 14 days of the renewal date.

Tidewater Billing Team', 'NONE', '["To confirm that a payment has been received", "To notify a customer of an upcoming charge", "To announce an increase in a yearly fee", "To request updated credit card details"]', 'B', 1, '[주제·목적] 정답 (B). ''will renew automatically on March 17, and $228 will be charged to the card on file''에서 곧 있을 자동 갱신 결제를 미리 알리는 메일임을 알 수 있다. 오답: (A) 결제는 아직 이루어지지 않았다. (C) 요금이 오른다는 말은 없다. (D) 등록된 카드로 결제된다고 했을 뿐 카드 정보를 새로 요구하지 않는다.', 151),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'According to the e-mail, what has changed about the Standard plan?', 'Questions 151-152 refer to the following e-mail.

To: Elena Kovacs
From: Tidewater Accounting Cloud <billing@tidewatercloud.com>
Date: March 3
Subject: Your plan renews soon

Dear Ms. Kovacs,

Your Tidewater Standard plan will renew automatically on March 17, and $228 will be charged to the card on file for the coming year. No action is needed if you wish to continue.

Since you joined, we have added receipt scanning and multi-currency invoicing to the Standard plan at no extra cost. If your business has grown, you may want to consider the Team plan, which supports up to ten users.

To change or cancel your plan, visit the Billing page of your account before the renewal date. A renewal charge can be refunded in full only within 14 days of the renewal date.

Tidewater Billing Team', 'NONE', '["Its annual price has gone up.", "It now supports up to ten users.", "Additional features have been included.", "Its refund period has been shortened."]', 'C', 1, '[세부 사항] 정답 (C). ''we have added receipt scanning and multi-currency invoicing to the Standard plan at no extra cost''에서 기능이 추가되었음을 알 수 있다. added … to the plan이 Additional features have been included로 바뀌었다. 오답: (A) 추가 비용이 없다고 했다. (B) 열 명까지 쓰는 것은 Team plan이다. (D) 환불 기간 14일이 줄었다는 말은 없다.', 152),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is the notice mainly about?', 'Questions 153-154 refer to the following notice.

Notice to Tenants of Kestrel Tower

The building''s two passenger elevators will be modernized one at a time beginning October 6. Elevator A will be out of service from October 6 to October 17, and Elevator B from October 20 to October 31. One elevator will remain in operation at all times, but please expect longer waits, particularly between 8:00 and 9:00 A.M.

The freight elevator at the rear of the building may be used for deliveries only. To reserve it for furniture or other large equipment, contact the management office at least two days ahead.

Work will take place between 7:00 P.M. and 5:00 A.M. to limit noise during business hours. We appreciate your patience.

Kestrel Tower Management Office', 'NONE', '["A change in the ownership of a building", "New hours for a management office", "Scheduled work on building equipment", "The opening of a new rear entrance"]', 'C', 1, '[주제·목적] 정답 (C). ''The building''s two passenger elevators will be modernized one at a time beginning October 6''에서 엘리베이터 교체 공사 일정을 알리는 글임을 알 수 있다. elevators가 building equipment로, modernized가 work로 바뀌었다. 오답: (A) 소유주 변경, (B) 관리사무소 운영 시간, (D) 새 출입구 이야기는 없다.', 153),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'According to the notice, what should tenants do before moving large equipment?', 'Questions 153-154 refer to the following notice.

Notice to Tenants of Kestrel Tower

The building''s two passenger elevators will be modernized one at a time beginning October 6. Elevator A will be out of service from October 6 to October 17, and Elevator B from October 20 to October 31. One elevator will remain in operation at all times, but please expect longer waits, particularly between 8:00 and 9:00 A.M.

The freight elevator at the rear of the building may be used for deliveries only. To reserve it for furniture or other large equipment, contact the management office at least two days ahead.

Work will take place between 7:00 P.M. and 5:00 A.M. to limit noise during business hours. We appreciate your patience.

Kestrel Tower Management Office', 'NONE', '["Reserve Elevator B for the evening", "Wait until the work ends on October 31", "Hire an approved moving company", "Contact the management office in advance"]', 'D', 1, '[세부 사항] 정답 (D). ''To reserve it for furniture or other large equipment, contact the management office at least two days ahead''에서 큰 물건을 옮기려면 이틀 전까지 관리사무소에 연락해야 한다. at least two days ahead가 in advance로 바뀌었다. 오답: (A) 예약 대상은 화물용 엘리베이터다. (B) 공사가 끝날 때까지 기다리라는 말은 없다. (C) 이사 업체 이야기는 없다.', 154),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is the purpose of the memo?', 'Questions 155-157 refer to the following memo.

To: All Harlow Bay staff
From: Ingrid Solheim, Facilities Manager
Date: May 6
Subject: Move to Pier Nine Plaza

As announced last month, our Harlow Bay office will relocate from Dockside Road to the fourth floor of Pier Nine Plaza. — [1] —. The Dockside Road office will close at noon on May 22, and we will open at the new address on May 26.

Packing crates will be delivered to each department on May 15. Please label every crate with your name and your new desk number, which you can find on the floor plan attached to this memo. — [2] —. Computers and monitors will be packed and reconnected by the IT team, so there is no need to unplug anything yourself.

Pier Nine Plaza has a limited number of parking spaces, and these will be assigned to employees who carpool. — [3] —. The Route 12 bus stops directly in front of the building, and covered bicycle racks are available by the east entrance.

New access cards will be handed out on the morning of May 26. — [4] —. If you have any questions, contact me at extension 418.', 'NONE', '["To give details about an upcoming office move", "To introduce a newly hired facilities manager", "To invite employees to tour a new building", "To ask staff to vote on a possible location"]', 'A', 1, '[주제·목적] 정답 (A). ''our Harlow Bay office will relocate from Dockside Road to … Pier Nine Plaza''로 시작해 폐쇄일·상자 배부·주차·출입 카드 등 이전 관련 세부 사항을 안내한다. 오답: (B) Solheim 씨는 발신자일 뿐 새로 온 사람이라는 말이 없다. (C) 견학 초대가 아니다. (D) 장소는 이미 정해졌다(As announced last month).', 155),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What are employees asked to do?', 'Questions 155-157 refer to the following memo.

To: All Harlow Bay staff
From: Ingrid Solheim, Facilities Manager
Date: May 6
Subject: Move to Pier Nine Plaza

As announced last month, our Harlow Bay office will relocate from Dockside Road to the fourth floor of Pier Nine Plaza. — [1] —. The Dockside Road office will close at noon on May 22, and we will open at the new address on May 26.

Packing crates will be delivered to each department on May 15. Please label every crate with your name and your new desk number, which you can find on the floor plan attached to this memo. — [2] —. Computers and monitors will be packed and reconnected by the IT team, so there is no need to unplug anything yourself.

Pier Nine Plaza has a limited number of parking spaces, and these will be assigned to employees who carpool. — [3] —. The Route 12 bus stops directly in front of the building, and covered bicycle racks are available by the east entrance.

New access cards will be handed out on the morning of May 26. — [4] —. If you have any questions, contact me at extension 418.', 'NONE', '["Mark containers with their seating information", "Unplug their computers before May 22", "Collect packing crates from the fourth floor", "Return their old access cards to Ms. Solheim"]', 'A', 1, '[세부 사항] 정답 (A). ''Please label every crate with your name and your new desk number''에서 상자에 이름과 새 자리 번호를 적어 달라고 한다. label이 Mark로, crate가 containers로, desk number가 seating information으로 바뀌었다. 오답: (B) ''no need to unplug anything yourself''와 반대다. (C) 상자는 각 부서로 배달된다. (D) 기존 카드 반납 이야기는 없다.', 156),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'In which of the positions marked [1], [2], [3], and [4] does the following sentence best belong? "Everyone else is encouraged to use public transportation."', 'Questions 155-157 refer to the following memo.

To: All Harlow Bay staff
From: Ingrid Solheim, Facilities Manager
Date: May 6
Subject: Move to Pier Nine Plaza

As announced last month, our Harlow Bay office will relocate from Dockside Road to the fourth floor of Pier Nine Plaza. — [1] —. The Dockside Road office will close at noon on May 22, and we will open at the new address on May 26.

Packing crates will be delivered to each department on May 15. Please label every crate with your name and your new desk number, which you can find on the floor plan attached to this memo. — [2] —. Computers and monitors will be packed and reconnected by the IT team, so there is no need to unplug anything yourself.

Pier Nine Plaza has a limited number of parking spaces, and these will be assigned to employees who carpool. — [3] —. The Route 12 bus stops directly in front of the building, and covered bicycle racks are available by the east entrance.

New access cards will be handed out on the morning of May 26. — [4] —. If you have any questions, contact me at extension 418.', 'NONE', '["[1]", "[2]", "[3]", "[4]"]', 'C', 1, '[문장 삽입] 정답 (C). ''Everyone else''는 앞 문장의 ''employees who carpool''을 뺀 나머지 직원을 가리키고, 바로 뒤의 ''The Route 12 bus stops directly in front of the building''이 대중교통의 예를 이어 준다. 오답: (A) [1] 앞은 이전 사실, (B) [2] 앞은 상자 표시, (D) [4] 앞은 출입 카드 이야기라서 ''Everyone else''가 가리킬 대조 대상이 없다.', 157),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is the purpose of the information?', 'Questions 158-160 refer to the following information.

Travel Guidelines for Employees of Pelham Instruments
(effective July 1)

Booking. All flights, rail tickets, and hotel rooms must be reserved through TripLedger, the company''s travel portal. Reservations made on other Web sites will not be reimbursed unless a department head has given written approval beforehand.

Meals. Employees traveling overnight receive a daily meal allowance of $55. Receipts for meals are not required. The allowance does not apply on days when meals are provided by a client or a conference organizer.

Ground transportation. Taxi and ride-share fares are reimbursed for travel between airports, hotels, and client sites. Employees who drive their own vehicles are reimbursed at $0.60 per kilometer.

Expense reports. Submit your report, together with receipts for lodging and transportation, within ten business days of your return. Reports submitted late must be signed by your department head and may take up to six weeks to process. Approved amounts are normally paid with the next monthly salary.

Questions should be directed to the Finance Office at extension 220.', 'NONE', '["To advertise a new travel booking Web site", "To announce an upcoming industry conference", "To explain rules for business travel expenses", "To introduce the staff of the Finance Office"]', 'C', 1, '[주제·목적] 정답 (C). 제목 ''Travel Guidelines for Employees''와 예약·식비·교통·경비 보고서 항목에서 출장 경비 처리 규정을 설명하는 글임을 알 수 있다. Guidelines가 rules로 바뀌었다. 오답: (A) TripLedger는 사내 예약 창구로 언급될 뿐 광고가 아니다. (B) 학회 개최 안내가 아니다. (D) 재무팀은 문의처로만 나온다.', 158),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'For which expense are receipts NOT required?', 'Questions 158-160 refer to the following information.

Travel Guidelines for Employees of Pelham Instruments
(effective July 1)

Booking. All flights, rail tickets, and hotel rooms must be reserved through TripLedger, the company''s travel portal. Reservations made on other Web sites will not be reimbursed unless a department head has given written approval beforehand.

Meals. Employees traveling overnight receive a daily meal allowance of $55. Receipts for meals are not required. The allowance does not apply on days when meals are provided by a client or a conference organizer.

Ground transportation. Taxi and ride-share fares are reimbursed for travel between airports, hotels, and client sites. Employees who drive their own vehicles are reimbursed at $0.60 per kilometer.

Expense reports. Submit your report, together with receipts for lodging and transportation, within ten business days of your return. Reports submitted late must be signed by your department head and may take up to six weeks to process. Approved amounts are normally paid with the next monthly salary.

Questions should be directed to the Finance Office at extension 220.', 'NONE', '["Hotel stays", "Taxi rides", "Meals", "Rail tickets"]', 'C', 1, '[사실 확인(NOT)] 정답 (C). ''Receipts for meals are not required''라고 했다. 식비는 하루 55달러 정액 수당이기 때문이다. 오답: (A) 숙박(lodging)과 (B)·(D) 교통(transportation)은 ''together with receipts for lodging and transportation''에서 영수증을 내야 하는 항목이다.', 159),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about expense reports that are submitted late?', 'Questions 158-160 refer to the following information.

Travel Guidelines for Employees of Pelham Instruments
(effective July 1)

Booking. All flights, rail tickets, and hotel rooms must be reserved through TripLedger, the company''s travel portal. Reservations made on other Web sites will not be reimbursed unless a department head has given written approval beforehand.

Meals. Employees traveling overnight receive a daily meal allowance of $55. Receipts for meals are not required. The allowance does not apply on days when meals are provided by a client or a conference organizer.

Ground transportation. Taxi and ride-share fares are reimbursed for travel between airports, hotels, and client sites. Employees who drive their own vehicles are reimbursed at $0.60 per kilometer.

Expense reports. Submit your report, together with receipts for lodging and transportation, within ten business days of your return. Reports submitted late must be signed by your department head and may take up to six weeks to process. Approved amounts are normally paid with the next monthly salary.

Questions should be directed to the Finance Office at extension 220.', 'NONE', '["They need a manager''s signature.", "They will not be accepted.", "They must be sent through TripLedger.", "They are paid within ten business days."]', 'A', 1, '[사실 확인] 정답 (A). ''Reports submitted late must be signed by your department head''에서 늦게 낸 보고서는 부서장 서명이 필요하다. department head가 manager로 바뀌었다. 오답: (B) 처리에 최대 6주가 걸릴 뿐 거부되지는 않는다. (C) TripLedger는 예약용이다. (D) ten business days는 제출 기한이지 지급 기간이 아니다.', 160),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'Why was the memo written?', 'Questions 161-163 refer to the following memo.

To: Customer Support Department
From: Tomás Rivera, Training Coordinator
Date: January 9
Subject: RelayDesk training

Our new call-handling software, RelayDesk, goes live on February 2. To prepare, every member of the department must complete one two-hour training session before that date. The session dates are listed at the bottom of this memo.

Each session is limited to twelve people. Sign up on the department intranet page by January 15. Team leaders, please make sure that no more than half of your team attends the same session so that the phones remain staffed.

The sessions will be led by Ms. Hana Brandt of RelayDesk, who will guide participants through practice calls. Employees at the Weston Ridge office may join the January 27 session by video link; no other session will be broadcast.

A short online quiz will follow each session. Your results will not affect your performance review; they simply help us identify topics that need further explanation.

Sessions (Training Room B)
January 20 — 9:00 to 11:00 A.M.
January 22 — 2:00 to 4:00 P.M.
January 27 — 9:00 to 11:00 A.M.
January 29 — 2:00 to 4:00 P.M.', 'NONE', '["To introduce a new member of the department", "To report the results of an online quiz", "To request opinions about a software product", "To announce required training on a new system"]', 'D', 1, '[주제·목적] 정답 (D). ''every member of the department must complete one two-hour training session''에서 새 소프트웨어 RelayDesk에 대한 필수 교육을 알리는 글임을 알 수 있다. must complete가 required로, software가 system으로 바뀌었다. 오답: (A) 새 직원 소개가 아니다. (B) 퀴즈는 교육 뒤에 치를 예정이다. (C) 의견을 묻지 않는다.', 161),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is different about the January 27 session?', 'Questions 161-163 refer to the following memo.

To: Customer Support Department
From: Tomás Rivera, Training Coordinator
Date: January 9
Subject: RelayDesk training

Our new call-handling software, RelayDesk, goes live on February 2. To prepare, every member of the department must complete one two-hour training session before that date. The session dates are listed at the bottom of this memo.

Each session is limited to twelve people. Sign up on the department intranet page by January 15. Team leaders, please make sure that no more than half of your team attends the same session so that the phones remain staffed.

The sessions will be led by Ms. Hana Brandt of RelayDesk, who will guide participants through practice calls. Employees at the Weston Ridge office may join the January 27 session by video link; no other session will be broadcast.

A short online quiz will follow each session. Your results will not affect your performance review; they simply help us identify topics that need further explanation.

Sessions (Training Room B)
January 20 — 9:00 to 11:00 A.M.
January 22 — 2:00 to 4:00 P.M.
January 27 — 9:00 to 11:00 A.M.
January 29 — 2:00 to 4:00 P.M.', 'NONE', '["It will be led by a different instructor.", "It can be joined from another location.", "It is reserved for team leaders only.", "It will be held in a larger room."]', 'B', 1, '[세부 사항] 정답 (B). ''Employees at the Weston Ridge office may join the January 27 session by video link; no other session will be broadcast''에서 이 회차만 다른 사무실에서 화상으로 참여할 수 있다. by video link가 from another location으로 바뀌었다. 오답: (A) 모든 회차를 Brandt 씨가 진행한다. (C) 팀장 전용이라는 말은 없다. (D) 장소는 모두 Training Room B다.', 162),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'The word "follow" in paragraph 4, line 1, is closest in meaning to', 'Questions 161-163 refer to the following memo.

To: Customer Support Department
From: Tomás Rivera, Training Coordinator
Date: January 9
Subject: RelayDesk training

Our new call-handling software, RelayDesk, goes live on February 2. To prepare, every member of the department must complete one two-hour training session before that date. The session dates are listed at the bottom of this memo.

Each session is limited to twelve people. Sign up on the department intranet page by January 15. Team leaders, please make sure that no more than half of your team attends the same session so that the phones remain staffed.

The sessions will be led by Ms. Hana Brandt of RelayDesk, who will guide participants through practice calls. Employees at the Weston Ridge office may join the January 27 session by video link; no other session will be broadcast.

A short online quiz will follow each session. Your results will not affect your performance review; they simply help us identify topics that need further explanation.

Sessions (Training Room B)
January 20 — 9:00 to 11:00 A.M.
January 22 — 2:00 to 4:00 P.M.
January 27 — 9:00 to 11:00 A.M.
January 29 — 2:00 to 4:00 P.M.', 'NONE', '["obey", "pursue", "come after", "understand"]', 'C', 1, '[동의어] 정답 (C). ''A short online quiz will follow each session''은 교육이 끝난 뒤에 퀴즈가 이어진다는 뜻이므로 come after(뒤에 오다)가 맞다. 오답: (A) obey는 규칙·지시를 따르다, (B) pursue는 뒤쫓다·추구하다, (D) understand는 이해하다로, follow의 다른 뜻이지만 이 문장에는 맞지 않는다.', 163),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'Why does Ms. Farouk start the discussion?', 'Questions 164-167 refer to the following online chat discussion.

Leila Farouk (10:02 A.M.)
Hi, both. Facilities just told me the ceiling repairs in the main conference room won''t be finished until the 14th. Our quarterly sales meeting is on the 12th.

Dominic Achterberg (10:04 A.M.)
That''s a problem. We have 40 people coming, including the regional managers from Weston Ridge.

Leila Farouk (10:05 A.M.)
The training room on the second floor is free that day, but it seats only 28.

Yuki Tanabe (10:07 A.M.)
What about the Seabrook Hotel across the street? We held the awards dinner there last spring. Their Harbor Room holds 60.

Dominic Achterberg (10:08 A.M.)
Can we afford it? The budget for this meeting covers lunch and printing, nothing more.

Yuki Tanabe (10:11 A.M.)
I just called. If we order lunch through their catering department, they''ll waive the room charge. Their lunch is $22 a person; our usual caterer charges $20.

Dominic Achterberg (10:12 A.M.)
I can live with that. Leila, can you cancel our regular caterer?

Leila Farouk (10:13 A.M.)
Will do. I''ll also send everyone an update with the new location.

Yuki Tanabe (10:14 A.M.)
And I''ll ask the hotel to hold the room and e-mail us an agreement to sign. They need it back by the 5th.

Dominic Achterberg (10:15 A.M.)
Perfect. Forward it to me when it arrives, and I''ll sign it today.', 'NONE', '["To announce that a meeting has been postponed", "To request more money for a quarterly event", "To report that a room will not be available", "To invite regional managers to a sales meeting"]', 'C', 1, '[주제·목적] 정답 (C). ''the ceiling repairs in the main conference room won''t be finished until the 14th. Our quarterly sales meeting is on the 12th''에서 회의 날에 회의실을 쓸 수 없다는 사실을 알리려고 대화를 시작했다. 오답: (A) 회의 날짜는 그대로이고 장소만 바뀐다. (B) 예산 증액을 요청하지 않는다. (D) 지역 관리자들은 이미 참석 예정이다.', 164),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the Seabrook Hotel?', 'Questions 164-167 refer to the following online chat discussion.

Leila Farouk (10:02 A.M.)
Hi, both. Facilities just told me the ceiling repairs in the main conference room won''t be finished until the 14th. Our quarterly sales meeting is on the 12th.

Dominic Achterberg (10:04 A.M.)
That''s a problem. We have 40 people coming, including the regional managers from Weston Ridge.

Leila Farouk (10:05 A.M.)
The training room on the second floor is free that day, but it seats only 28.

Yuki Tanabe (10:07 A.M.)
What about the Seabrook Hotel across the street? We held the awards dinner there last spring. Their Harbor Room holds 60.

Dominic Achterberg (10:08 A.M.)
Can we afford it? The budget for this meeting covers lunch and printing, nothing more.

Yuki Tanabe (10:11 A.M.)
I just called. If we order lunch through their catering department, they''ll waive the room charge. Their lunch is $22 a person; our usual caterer charges $20.

Dominic Achterberg (10:12 A.M.)
I can live with that. Leila, can you cancel our regular caterer?

Leila Farouk (10:13 A.M.)
Will do. I''ll also send everyone an update with the new location.

Yuki Tanabe (10:14 A.M.)
And I''ll ask the hotel to hold the room and e-mail us an agreement to sign. They need it back by the 5th.

Dominic Achterberg (10:15 A.M.)
Perfect. Forward it to me when it arrives, and I''ll sign it today.', 'NONE', '["It has hosted a company event before.", "It recently renovated its Harbor Room.", "It is located in Weston Ridge.", "It charges less for lunch than other caterers."]', 'A', 1, '[추론] 정답 (A). ''We held the awards dinner there last spring''에서 회사가 지난봄 그 호텔에서 시상식 만찬을 열었음을 알 수 있다. 오답: (B) 보수 공사 중인 곳은 회사 회의실이다. (C) 호텔은 사무실 길 건너에 있고, Weston Ridge는 관리자들이 오는 곳이다. (D) 호텔 점심은 22달러로 기존 업체(20달러)보다 비싸다.', 165),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'At 10:12 A.M., what does Mr. Achterberg most likely mean when he writes, "I can live with that"?', 'Questions 164-167 refer to the following online chat discussion.

Leila Farouk (10:02 A.M.)
Hi, both. Facilities just told me the ceiling repairs in the main conference room won''t be finished until the 14th. Our quarterly sales meeting is on the 12th.

Dominic Achterberg (10:04 A.M.)
That''s a problem. We have 40 people coming, including the regional managers from Weston Ridge.

Leila Farouk (10:05 A.M.)
The training room on the second floor is free that day, but it seats only 28.

Yuki Tanabe (10:07 A.M.)
What about the Seabrook Hotel across the street? We held the awards dinner there last spring. Their Harbor Room holds 60.

Dominic Achterberg (10:08 A.M.)
Can we afford it? The budget for this meeting covers lunch and printing, nothing more.

Yuki Tanabe (10:11 A.M.)
I just called. If we order lunch through their catering department, they''ll waive the room charge. Their lunch is $22 a person; our usual caterer charges $20.

Dominic Achterberg (10:12 A.M.)
I can live with that. Leila, can you cancel our regular caterer?

Leila Farouk (10:13 A.M.)
Will do. I''ll also send everyone an update with the new location.

Yuki Tanabe (10:14 A.M.)
And I''ll ask the hotel to hold the room and e-mail us an agreement to sign. They need it back by the 5th.

Dominic Achterberg (10:15 A.M.)
Perfect. Forward it to me when it arrives, and I''ll sign it today.', 'NONE', '["He lives a short distance from the hotel.", "He would rather keep the usual caterer.", "He will pay the room charge himself.", "He considers the extra cost acceptable."]', 'D', 1, '[의도 파악] 정답 (D). 예산을 걱정하던 Achterberg 씨가 ''대관료 면제, 점심은 1인당 22달러(기존 20달러)''라는 말을 듣고 한 말이며, 곧바로 기존 업체 취소를 지시한다. 조금 더 드는 비용은 받아들일 만하다는 뜻이다. 오답: (A) live를 ''살다''로 옮긴 함정이다. (B) 기존 업체를 취소하라고 한다. (C) 대관료는 면제된다.', 166);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What will Ms. Tanabe most likely do next?', 'Questions 164-167 refer to the following online chat discussion.

Leila Farouk (10:02 A.M.)
Hi, both. Facilities just told me the ceiling repairs in the main conference room won''t be finished until the 14th. Our quarterly sales meeting is on the 12th.

Dominic Achterberg (10:04 A.M.)
That''s a problem. We have 40 people coming, including the regional managers from Weston Ridge.

Leila Farouk (10:05 A.M.)
The training room on the second floor is free that day, but it seats only 28.

Yuki Tanabe (10:07 A.M.)
What about the Seabrook Hotel across the street? We held the awards dinner there last spring. Their Harbor Room holds 60.

Dominic Achterberg (10:08 A.M.)
Can we afford it? The budget for this meeting covers lunch and printing, nothing more.

Yuki Tanabe (10:11 A.M.)
I just called. If we order lunch through their catering department, they''ll waive the room charge. Their lunch is $22 a person; our usual caterer charges $20.

Dominic Achterberg (10:12 A.M.)
I can live with that. Leila, can you cancel our regular caterer?

Leila Farouk (10:13 A.M.)
Will do. I''ll also send everyone an update with the new location.

Yuki Tanabe (10:14 A.M.)
And I''ll ask the hotel to hold the room and e-mail us an agreement to sign. They need it back by the 5th.

Dominic Achterberg (10:15 A.M.)
Perfect. Forward it to me when it arrives, and I''ll sign it today.', 'NONE', '["Cancel a catering order", "Notify the meeting attendees", "Request a document from the hotel", "Sign a rental agreement"]', 'C', 1, '[추론] 정답 (C). Tanabe 씨는 ''I''ll ask the hotel to hold the room and e-mail us an agreement to sign''이라고 했다. agreement가 document로 바뀌었다. 오답: (A) 기존 업체 취소와 (B) 참석자 공지는 Farouk 씨가 맡았다. (D) 서명은 Achterberg 씨가 하겠다고 했다.', 167),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'Why did Mr. Lindqvist most likely contact FreshCrate?', 'Questions 168-171 refer to the following e-mail.

To: Gareth Lindqvist
From: Naomi Estrada, FreshCrate Customer Care
Date: June 9
Subject: Re: Refund request

Dear Mr. Lindqvist,

Thank you for contacting us about the charge of $64.90 that appeared on your card on June 2. — [1] —. Because the subscription was not paused or canceled before the weekly cutoff of May 28, our system processed your order as usual, and the box was delivered on June 4.

I understand from your message that the box sat outside your door for three days before you returned from your trip. — [2] —. Although our policy does not normally allow refunds once a box has shipped, I see that you have subscribed for more than two years and have never made a request of this kind. I have therefore refunded the full amount. Please allow three to five business days for it to appear on your statement.

To avoid similar problems, you can skip deliveries up to six weeks ahead by using the calendar in the FreshCrate app. — [3] —. You can also turn on reminders, which will alert you two days before each cutoff.

I have added a $10 credit to your account as well. — [4] —. It will be applied automatically to your next box.

Sincerely,
Naomi Estrada', 'NONE', '["He received a box containing the wrong items.", "He wanted to change his delivery address.", "He was billed for a delivery he did not want.", "He was unable to log in to a mobile app."]', 'C', 1, '[추론] 정답 (C). 제목이 ''Refund request''이고, 여행 중이었는데 구독을 멈추지 않아 ''our system processed your order as usual''로 64.90달러가 결제되었다. 원하지 않던 배송에 대한 요금을 돌려 달라고 연락한 것이다. 오답: (A) 내용물이 잘못됐다는 말은 없다. (B) 주소 변경 요청이 아니다. (D) 앱은 Estrada 씨가 권하는 해결책일 뿐이다.', 168),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about Mr. Lindqvist?', 'Questions 168-171 refer to the following e-mail.

To: Gareth Lindqvist
From: Naomi Estrada, FreshCrate Customer Care
Date: June 9
Subject: Re: Refund request

Dear Mr. Lindqvist,

Thank you for contacting us about the charge of $64.90 that appeared on your card on June 2. — [1] —. Because the subscription was not paused or canceled before the weekly cutoff of May 28, our system processed your order as usual, and the box was delivered on June 4.

I understand from your message that the box sat outside your door for three days before you returned from your trip. — [2] —. Although our policy does not normally allow refunds once a box has shipped, I see that you have subscribed for more than two years and have never made a request of this kind. I have therefore refunded the full amount. Please allow three to five business days for it to appear on your statement.

To avoid similar problems, you can skip deliveries up to six weeks ahead by using the calendar in the FreshCrate app. — [3] —. You can also turn on reminders, which will alert you two days before each cutoff.

I have added a $10 credit to your account as well. — [4] —. It will be applied automatically to your next box.

Sincerely,
Naomi Estrada', 'NONE', '["He was away from home on June 4.", "He has asked FreshCrate for refunds before.", "He canceled his subscription in May.", "He recently changed his payment card."]', 'A', 1, '[추론] 정답 (A). 상자는 6월 4일에 배달되었고 ''the box sat outside your door for three days before you returned from your trip''이라고 했으므로 배달일에 그는 집에 없었다. 오답: (B) ''have never made a request of this kind''와 반대다. (C) 5월 28일 마감 전에 취소하지 않았다. (D) 카드 변경 이야기는 없다.', 169),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What does Ms. Estrada recommend that Mr. Lindqvist do?', 'Questions 168-171 refer to the following e-mail.

To: Gareth Lindqvist
From: Naomi Estrada, FreshCrate Customer Care
Date: June 9
Subject: Re: Refund request

Dear Mr. Lindqvist,

Thank you for contacting us about the charge of $64.90 that appeared on your card on June 2. — [1] —. Because the subscription was not paused or canceled before the weekly cutoff of May 28, our system processed your order as usual, and the box was delivered on June 4.

I understand from your message that the box sat outside your door for three days before you returned from your trip. — [2] —. Although our policy does not normally allow refunds once a box has shipped, I see that you have subscribed for more than two years and have never made a request of this kind. I have therefore refunded the full amount. Please allow three to five business days for it to appear on your statement.

To avoid similar problems, you can skip deliveries up to six weeks ahead by using the calendar in the FreshCrate app. — [3] —. You can also turn on reminders, which will alert you two days before each cutoff.

I have added a $10 credit to your account as well. — [4] —. It will be applied automatically to your next box.

Sincerely,
Naomi Estrada', 'NONE', '["Call Customer Care before each cutoff", "Use a scheduling feature in an app", "Switch to a smaller weekly box", "Ask a neighbor to collect deliveries"]', 'B', 1, '[세부 사항] 정답 (B). ''you can skip deliveries up to six weeks ahead by using the calendar in the FreshCrate app''에서 앱의 달력 기능으로 배송을 미리 건너뛰라고 권한다. calendar가 scheduling feature로 바뀌었다. 오답: (A) 전화하라는 말은 없다. (C) 상자 크기 변경, (D) 이웃에게 부탁하라는 말은 없다.', 170),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'In which of the positions marked [1], [2], [3], and [4] does the following sentence best belong? "This amount was for your regular weekly meal box."', 'Questions 168-171 refer to the following e-mail.

To: Gareth Lindqvist
From: Naomi Estrada, FreshCrate Customer Care
Date: June 9
Subject: Re: Refund request

Dear Mr. Lindqvist,

Thank you for contacting us about the charge of $64.90 that appeared on your card on June 2. — [1] —. Because the subscription was not paused or canceled before the weekly cutoff of May 28, our system processed your order as usual, and the box was delivered on June 4.

I understand from your message that the box sat outside your door for three days before you returned from your trip. — [2] —. Although our policy does not normally allow refunds once a box has shipped, I see that you have subscribed for more than two years and have never made a request of this kind. I have therefore refunded the full amount. Please allow three to five business days for it to appear on your statement.

To avoid similar problems, you can skip deliveries up to six weeks ahead by using the calendar in the FreshCrate app. — [3] —. You can also turn on reminders, which will alert you two days before each cutoff.

I have added a $10 credit to your account as well. — [4] —. It will be applied automatically to your next box.

Sincerely,
Naomi Estrada', 'NONE', '["[1]", "[2]", "[3]", "[4]"]', 'A', 1, '[문장 삽입] 정답 (A). ''This amount''는 바로 앞 문장의 ''the charge of $64.90''를 가리키고, 뒤 문장이 그 주문이 왜 처리되었는지(마감 전에 멈추지 않음)를 설명하므로 [1]이 맞다. 오답: (B) [2] 앞에는 금액이 없다. (C) [3] 앞은 앱 달력 이야기다. (D) [4] 앞의 10달러는 식단 상자 요금이 아니라 적립금(credit)이고 뒤의 It이 credit을 받아야 한다.', 171),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is the article mainly about?', 'Questions 172-175 refer to the following article.

Corvane Logistics to Open Weston Ridge Center

WESTON RIDGE (April 14) — Corvane Logistics announced yesterday that it will open a distribution center on Millbrook Road in Weston Ridge this autumn. The 18,000-square-meter building, formerly a furniture warehouse, is being fitted with automated sorting equipment and is expected to begin operations on October 1.

Corvane currently handles all of its regional shipments from a single site near the port in Harlow Bay, where the company is headquartered. "That building has served us well for fifteen years, but we have simply outgrown it," said Chief Operating Officer Renata Kowalczyk. "The Weston Ridge center will let us reach customers in the northern counties a full day sooner."

The company plans to fill about 120 positions at the new center, including forklift operators, inventory clerks, and shift supervisors. Previous warehouse experience is not required for most roles, as new hires will receive three weeks of paid training. A job fair will be held at the Weston Ridge Community Hall on June 20.

Local officials welcomed the news. Mayor Desmond Achebe noted that the project qualified for the city''s tax incentive for companies that reuse vacant commercial buildings. The Harlow Bay site will remain open, and no jobs there will be affected, the company said.', 'NONE', '["The merger of two shipping companies", "The closing of a furniture warehouse", "A company''s expansion into another city", "A change in a firm''s senior management"]', 'C', 1, '[주제·목적] 정답 (C). Harlow Bay에 본사가 있는 Corvane이 ''will open a distribution center … in Weston Ridge''라고 했으므로 다른 도시로 사업을 넓힌다는 기사다. 오답: (A) 합병 이야기는 없다. (B) 가구 창고는 건물의 이전 용도일 뿐이다. (D) Kowalczyk 씨는 발언자로 나올 뿐 경영진 교체가 아니다.', 172),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'The word "handles" in paragraph 2, line 1, is closest in meaning to', 'Questions 172-175 refer to the following article.

Corvane Logistics to Open Weston Ridge Center

WESTON RIDGE (April 14) — Corvane Logistics announced yesterday that it will open a distribution center on Millbrook Road in Weston Ridge this autumn. The 18,000-square-meter building, formerly a furniture warehouse, is being fitted with automated sorting equipment and is expected to begin operations on October 1.

Corvane currently handles all of its regional shipments from a single site near the port in Harlow Bay, where the company is headquartered. "That building has served us well for fifteen years, but we have simply outgrown it," said Chief Operating Officer Renata Kowalczyk. "The Weston Ridge center will let us reach customers in the northern counties a full day sooner."

The company plans to fill about 120 positions at the new center, including forklift operators, inventory clerks, and shift supervisors. Previous warehouse experience is not required for most roles, as new hires will receive three weeks of paid training. A job fair will be held at the Weston Ridge Community Hall on June 20.

Local officials welcomed the news. Mayor Desmond Achebe noted that the project qualified for the city''s tax incentive for companies that reuse vacant commercial buildings. The Harlow Bay site will remain open, and no jobs there will be affected, the company said.', 'NONE', '["touches", "manages", "sells", "delays"]', 'B', 1, '[동의어] 정답 (B). ''handles all of its regional shipments from a single site''는 한 곳에서 지역 배송 물량을 모두 처리·관리한다는 뜻이므로 manages가 맞다. 오답: (A) touches는 손으로 만지다라는 handle의 다른 뜻이다. (C) sells는 판매하다, (D) delays는 지연시키다로 문맥과 맞지 않는다.', 173),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is NOT mentioned about the positions at the new center?', 'Questions 172-175 refer to the following article.

Corvane Logistics to Open Weston Ridge Center

WESTON RIDGE (April 14) — Corvane Logistics announced yesterday that it will open a distribution center on Millbrook Road in Weston Ridge this autumn. The 18,000-square-meter building, formerly a furniture warehouse, is being fitted with automated sorting equipment and is expected to begin operations on October 1.

Corvane currently handles all of its regional shipments from a single site near the port in Harlow Bay, where the company is headquartered. "That building has served us well for fifteen years, but we have simply outgrown it," said Chief Operating Officer Renata Kowalczyk. "The Weston Ridge center will let us reach customers in the northern counties a full day sooner."

The company plans to fill about 120 positions at the new center, including forklift operators, inventory clerks, and shift supervisors. Previous warehouse experience is not required for most roles, as new hires will receive three weeks of paid training. A job fair will be held at the Weston Ridge Community Hall on June 20.

Local officials welcomed the news. Mayor Desmond Achebe noted that the project qualified for the city''s tax incentive for companies that reuse vacant commercial buildings. The Harlow Bay site will remain open, and no jobs there will be affected, the company said.', 'NONE', '["The approximate number of openings", "The kinds of jobs available", "The training new employees will receive", "The wages that will be offered"]', 'D', 1, '[사실 확인(NOT)] 정답 (D). 급여 수준은 기사에 나오지 않는다(교육이 유급이라는 말만 있다). 오답: (A) ''about 120 positions'', (B) ''forklift operators, inventory clerks, and shift supervisors'', (C) ''three weeks of paid training''으로 각각 언급되어 있다.', 174),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the building on Millbrook Road?', 'Questions 172-175 refer to the following article.

Corvane Logistics to Open Weston Ridge Center

WESTON RIDGE (April 14) — Corvane Logistics announced yesterday that it will open a distribution center on Millbrook Road in Weston Ridge this autumn. The 18,000-square-meter building, formerly a furniture warehouse, is being fitted with automated sorting equipment and is expected to begin operations on October 1.

Corvane currently handles all of its regional shipments from a single site near the port in Harlow Bay, where the company is headquartered. "That building has served us well for fifteen years, but we have simply outgrown it," said Chief Operating Officer Renata Kowalczyk. "The Weston Ridge center will let us reach customers in the northern counties a full day sooner."

The company plans to fill about 120 positions at the new center, including forklift operators, inventory clerks, and shift supervisors. Previous warehouse experience is not required for most roles, as new hires will receive three weeks of paid training. A job fair will be held at the Weston Ridge Community Hall on June 20.

Local officials welcomed the news. Mayor Desmond Achebe noted that the project qualified for the city''s tax incentive for companies that reuse vacant commercial buildings. The Harlow Bay site will remain open, and no jobs there will be affected, the company said.', 'NONE', '["It was not in use before Corvane''s project.", "It was constructed by the city government.", "It will replace the company''s Harlow Bay site.", "It is located next to a shipping port."]', 'A', 1, '[추론] 정답 (A). 이 사업이 ''the city''s tax incentive for companies that reuse vacant commercial buildings''의 대상이 되었다고 했으므로, 예전 가구 창고였던 그 건물은 비어 있었다고 볼 수 있다. vacant가 not in use로 바뀌었다. 오답: (B) 시가 지었다는 말은 없다. (C) ''The Harlow Bay site will remain open''과 반대다. (D) 항구 근처에 있는 것은 Harlow Bay 시설이다.', 175),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'Why did Ms. Whitlock write to Mr. Mbeki?', 'Questions 176-180 refer to the following e-mail and invoice.

[E-mail]
To: Oliver Mbeki <ombeki@brightleafcafe.com>
From: Sandra Whitlock <swhitlock@harlowpackaging.com>
Date: November 4
Subject: Order HP-2291

Dear Mr. Mbeki,

I am writing about the order you placed on October 28. The paper cups and the lids were shipped yesterday and should reach you by November 6. The tracking number for that shipment appears at the bottom of this message. Unfortunately, the printed paper bags have been delayed because the machine that applies custom logos is undergoing repairs. We now expect to ship the bags on November 12 rather than November 5.

I apologize for the inconvenience. As we discussed by phone this morning, I am sending you one pack of plain bags at no charge so that you have something to use in the meantime. To make up for the delay, I have also taken 10 percent off the price of the printed bags.

While reviewing your account, I noticed that the invoice I sent on October 29 listed five cases of lids rather than the four you ordered. A corrected invoice is attached. Please disregard the earlier one.

Sincerely,
Sandra Whitlock
Harlow Packaging Supply

────────────────────

[Invoice]
Harlow Packaging Supply
310 Cannery Road, Harlow Bay

Invoice No.: 2291-B (replaces 2291-A)
Date issued: November 4
Bill to: Brightleaf Café, 80 Orchard Lane, Weston Ridge

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
Note: Payments received within 10 days of the issue date qualify for an additional 2 percent discount.', 'NONE', '["To inform him that part of an order will arrive late", "To ask him to approve a new logo design", "To request payment of an overdue invoice", "To recommend a different type of paper cup"]', 'A', 1, '[주제·목적] 정답 (A). ''the printed paper bags have been delayed … We now expect to ship the bags on November 12 rather than November 5''에서 주문품 중 일부(인쇄 봉투)가 늦어진다는 것을 알리는 메일이다. 오답: (B) 로고 디자인 승인을 요청하지 않는다. (C) 납부 기한은 12월 4일로 연체가 아니다. (D) 다른 컵을 권하지 않는다.', 176),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'According to the e-mail, what caused the delay?', 'Questions 176-180 refer to the following e-mail and invoice.

[E-mail]
To: Oliver Mbeki <ombeki@brightleafcafe.com>
From: Sandra Whitlock <swhitlock@harlowpackaging.com>
Date: November 4
Subject: Order HP-2291

Dear Mr. Mbeki,

I am writing about the order you placed on October 28. The paper cups and the lids were shipped yesterday and should reach you by November 6. The tracking number for that shipment appears at the bottom of this message. Unfortunately, the printed paper bags have been delayed because the machine that applies custom logos is undergoing repairs. We now expect to ship the bags on November 12 rather than November 5.

I apologize for the inconvenience. As we discussed by phone this morning, I am sending you one pack of plain bags at no charge so that you have something to use in the meantime. To make up for the delay, I have also taken 10 percent off the price of the printed bags.

While reviewing your account, I noticed that the invoice I sent on October 29 listed five cases of lids rather than the four you ordered. A corrected invoice is attached. Please disregard the earlier one.

Sincerely,
Sandra Whitlock
Harlow Packaging Supply

────────────────────

[Invoice]
Harlow Packaging Supply
310 Cannery Road, Harlow Bay

Invoice No.: 2291-B (replaces 2291-A)
Date issued: November 4
Bill to: Brightleaf Café, 80 Orchard Lane, Weston Ridge

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
Note: Payments received within 10 days of the issue date qualify for an additional 2 percent discount.', 'NONE', '["A piece of equipment is being fixed.", "A supply of paper has run out.", "A courier went to the wrong address.", "A payment was processed late."]', 'A', 1, '[세부 사항] 정답 (A). ''because the machine that applies custom logos is undergoing repairs''가 지연 이유다. machine이 a piece of equipment로, undergoing repairs가 being fixed로 바뀌었다. 오답: (B) 종이 재고 부족, (C) 배송 주소 오류, (D) 결제 지연은 언급이 없다.', 177),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'Which amount on the invoice reflects the correction of an error mentioned in the e-mail?', 'Questions 176-180 refer to the following e-mail and invoice.

[E-mail]
To: Oliver Mbeki <ombeki@brightleafcafe.com>
From: Sandra Whitlock <swhitlock@harlowpackaging.com>
Date: November 4
Subject: Order HP-2291

Dear Mr. Mbeki,

I am writing about the order you placed on October 28. The paper cups and the lids were shipped yesterday and should reach you by November 6. The tracking number for that shipment appears at the bottom of this message. Unfortunately, the printed paper bags have been delayed because the machine that applies custom logos is undergoing repairs. We now expect to ship the bags on November 12 rather than November 5.

I apologize for the inconvenience. As we discussed by phone this morning, I am sending you one pack of plain bags at no charge so that you have something to use in the meantime. To make up for the delay, I have also taken 10 percent off the price of the printed bags.

While reviewing your account, I noticed that the invoice I sent on October 29 listed five cases of lids rather than the four you ordered. A corrected invoice is attached. Please disregard the earlier one.

Sincerely,
Sandra Whitlock
Harlow Packaging Supply

────────────────────

[Invoice]
Harlow Packaging Supply
310 Cannery Road, Harlow Bay

Invoice No.: 2291-B (replaces 2291-A)
Date issued: November 4
Bill to: Brightleaf Café, 80 Orchard Lane, Weston Ridge

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
Note: Payments received within 10 days of the issue date qualify for an additional 2 percent discount.', 'NONE', '["$152.00", "$84.00", "$180.00", "$24.00"]', 'B', 1, '[연계(문서1+2)] 정답 (B). 이메일의 ''listed five cases of lids rather than the four you ordered''에서 잘못된 항목은 뚜껑 수량이고, 송장에서 뚜껑은 4상자 × 21달러 = 84달러로 바로잡혀 있다. 오답: (A) 컵 금액, (C) 인쇄 봉투 금액, (D) 배송비는 이메일에서 오류로 언급되지 않았다.', 178),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is the listed amount, before any discount, for the items that will be shipped on November 12?', 'Questions 176-180 refer to the following e-mail and invoice.

[E-mail]
To: Oliver Mbeki <ombeki@brightleafcafe.com>
From: Sandra Whitlock <swhitlock@harlowpackaging.com>
Date: November 4
Subject: Order HP-2291

Dear Mr. Mbeki,

I am writing about the order you placed on October 28. The paper cups and the lids were shipped yesterday and should reach you by November 6. The tracking number for that shipment appears at the bottom of this message. Unfortunately, the printed paper bags have been delayed because the machine that applies custom logos is undergoing repairs. We now expect to ship the bags on November 12 rather than November 5.

I apologize for the inconvenience. As we discussed by phone this morning, I am sending you one pack of plain bags at no charge so that you have something to use in the meantime. To make up for the delay, I have also taken 10 percent off the price of the printed bags.

While reviewing your account, I noticed that the invoice I sent on October 29 listed five cases of lids rather than the four you ordered. A corrected invoice is attached. Please disregard the earlier one.

Sincerely,
Sandra Whitlock
Harlow Packaging Supply

────────────────────

[Invoice]
Harlow Packaging Supply
310 Cannery Road, Harlow Bay

Invoice No.: 2291-B (replaces 2291-A)
Date issued: November 4
Bill to: Brightleaf Café, 80 Orchard Lane, Weston Ridge

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
Note: Payments received within 10 days of the issue date qualify for an additional 2 percent discount.', 'NONE', '["$84.00", "$152.00", "$162.00", "$180.00"]', 'D', 1, '[연계(문서1+2)] 정답 (D). 이메일에서 11월 12일에 발송되는 것은 인쇄 봉투(''ship the bags on November 12'')이고, 송장에서 Printed paper bags는 6묶음 × 30달러 = 180달러다. 오답: (A) 뚜껑, (B) 컵 금액이다. (C) 162달러는 10% 할인(18달러)을 뺀 금액이므로 ''할인 전''이라는 조건에 맞지 않는다.', 179),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'By what date must Brightleaf Café pay in order to receive an extra discount?', 'Questions 176-180 refer to the following e-mail and invoice.

[E-mail]
To: Oliver Mbeki <ombeki@brightleafcafe.com>
From: Sandra Whitlock <swhitlock@harlowpackaging.com>
Date: November 4
Subject: Order HP-2291

Dear Mr. Mbeki,

I am writing about the order you placed on October 28. The paper cups and the lids were shipped yesterday and should reach you by November 6. The tracking number for that shipment appears at the bottom of this message. Unfortunately, the printed paper bags have been delayed because the machine that applies custom logos is undergoing repairs. We now expect to ship the bags on November 12 rather than November 5.

I apologize for the inconvenience. As we discussed by phone this morning, I am sending you one pack of plain bags at no charge so that you have something to use in the meantime. To make up for the delay, I have also taken 10 percent off the price of the printed bags.

While reviewing your account, I noticed that the invoice I sent on October 29 listed five cases of lids rather than the four you ordered. A corrected invoice is attached. Please disregard the earlier one.

Sincerely,
Sandra Whitlock
Harlow Packaging Supply

────────────────────

[Invoice]
Harlow Packaging Supply
310 Cannery Road, Harlow Bay

Invoice No.: 2291-B (replaces 2291-A)
Date issued: November 4
Bill to: Brightleaf Café, 80 Orchard Lane, Weston Ridge

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
Note: Payments received within 10 days of the issue date qualify for an additional 2 percent discount.', 'NONE', '["November 6", "November 14", "November 24", "December 4"]', 'B', 1, '[세부 사항] 정답 (B). 송장의 ''Payments received within 10 days of the issue date qualify for an additional 2 percent discount''에서 발급일(11월 4일)로부터 10일 이내, 즉 11월 14일까지 내야 추가 할인을 받는다. 오답: (A) 컵과 뚜껑의 도착 예정일이다. (C) 근거 없는 날짜다. (D) 일반 납부 기한이다.', 180),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about Marlowe & Finch Architects?', 'Questions 181-185 refer to the following job posting and e-mail.

[Job posting]
Marlowe & Finch Architects
Office Coordinator (Weston Ridge)

Marlowe & Finch Architects, a 35-person firm specializing in the design of schools and public libraries, seeks an office coordinator for its studio in Weston Ridge.

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

This is a full-time position. Benefits include health insurance, 15 days of paid leave, and a monthly transit pass. To apply, send a résumé and a brief cover letter to careers@marlowefinch.com by March 14. Interviews will be held at our studio during the week of March 23. The successful candidate will be expected to start on April 13.

────────────────────

[E-mail]
To: Beatrice Okonkwo <b.okonkwo@marlowefinch.com>
From: Julian Castellanos <jcastellanos@inbox-mail.com>
Date: March 18
Subject: Re: Interview invitation

Dear Ms. Okonkwo,

Thank you for inviting me to interview for the office coordinator position. I would be glad to meet you and Mr. Finch. However, I will be attending a trade conference in Harlow Bay on behalf of my employer from March 23 to 25, so the March 24 appointment you proposed is not possible for me. Could we meet on March 26 or 27 instead? Any time on either day would suit me.

You also asked when I would be available. My employer, Dunmore Engineering, requires four weeks'' notice, so the earliest I could begin is April 27. I hope this will not be an obstacle.

Finally, as requested, I have attached two letters of reference. One is from my supervisor at Dunmore, where I have run the front office for the past three years.

I look forward to hearing from you.

Sincerely,
Julian Castellanos', 'NONE', '["It was established 35 years ago.", "It is moving to a larger studio.", "It plans to hire two new partners.", "It designs buildings for educational use."]', 'D', 1, '[사실 확인] 정답 (D). ''specializing in the design of schools and public libraries''에서 학교 등 교육 시설을 설계하는 회사임을 알 수 있다. schools가 buildings for educational use로 바뀌었다. 오답: (A) 35는 직원 수(35-person firm)다. (B) 이전 계획은 없다. (C) two partners는 이미 있는 대표 두 명이다.', 181),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is NOT a stated duty of the office coordinator?', 'Questions 181-185 refer to the following job posting and e-mail.

[Job posting]
Marlowe & Finch Architects
Office Coordinator (Weston Ridge)

Marlowe & Finch Architects, a 35-person firm specializing in the design of schools and public libraries, seeks an office coordinator for its studio in Weston Ridge.

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

This is a full-time position. Benefits include health insurance, 15 days of paid leave, and a monthly transit pass. To apply, send a résumé and a brief cover letter to careers@marlowefinch.com by March 14. Interviews will be held at our studio during the week of March 23. The successful candidate will be expected to start on April 13.

────────────────────

[E-mail]
To: Beatrice Okonkwo <b.okonkwo@marlowefinch.com>
From: Julian Castellanos <jcastellanos@inbox-mail.com>
Date: March 18
Subject: Re: Interview invitation

Dear Ms. Okonkwo,

Thank you for inviting me to interview for the office coordinator position. I would be glad to meet you and Mr. Finch. However, I will be attending a trade conference in Harlow Bay on behalf of my employer from March 23 to 25, so the March 24 appointment you proposed is not possible for me. Could we meet on March 26 or 27 instead? Any time on either day would suit me.

You also asked when I would be available. My employer, Dunmore Engineering, requires four weeks'' notice, so the earliest I could begin is April 27. I hope this will not be an obstacle.

Finally, as requested, I have attached two letters of reference. One is from my supervisor at Dunmore, where I have run the front office for the past three years.

I look forward to hearing from you.

Sincerely,
Julian Castellanos', 'NONE', '["Training newly hired employees", "Welcoming visitors to the studio", "Purchasing materials for the office", "Making travel arrangements"]', 'A', 1, '[사실 확인(NOT)] 정답 (A). 신입 직원 교육은 Responsibilities에 없다. 오답: (B) ''Greeting clients and managing the reception desk'', (C) ''Ordering supplies'', (D) ''booking travel for the firm''s two partners''가 각각 바꿔 표현된 것이다.', 182),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'Why is Mr. Castellanos unable to meet on March 24?', 'Questions 181-185 refer to the following job posting and e-mail.

[Job posting]
Marlowe & Finch Architects
Office Coordinator (Weston Ridge)

Marlowe & Finch Architects, a 35-person firm specializing in the design of schools and public libraries, seeks an office coordinator for its studio in Weston Ridge.

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

This is a full-time position. Benefits include health insurance, 15 days of paid leave, and a monthly transit pass. To apply, send a résumé and a brief cover letter to careers@marlowefinch.com by March 14. Interviews will be held at our studio during the week of March 23. The successful candidate will be expected to start on April 13.

────────────────────

[E-mail]
To: Beatrice Okonkwo <b.okonkwo@marlowefinch.com>
From: Julian Castellanos <jcastellanos@inbox-mail.com>
Date: March 18
Subject: Re: Interview invitation

Dear Ms. Okonkwo,

Thank you for inviting me to interview for the office coordinator position. I would be glad to meet you and Mr. Finch. However, I will be attending a trade conference in Harlow Bay on behalf of my employer from March 23 to 25, so the March 24 appointment you proposed is not possible for me. Could we meet on March 26 or 27 instead? Any time on either day would suit me.

You also asked when I would be available. My employer, Dunmore Engineering, requires four weeks'' notice, so the earliest I could begin is April 27. I hope this will not be an obstacle.

Finally, as requested, I have attached two letters of reference. One is from my supervisor at Dunmore, where I have run the front office for the past three years.

I look forward to hearing from you.

Sincerely,
Julian Castellanos', 'NONE', '["He has an interview at another company.", "He has a work-related event to attend.", "He will be away on a family vacation.", "He needs more time to collect references."]', 'B', 1, '[세부 사항] 정답 (B). ''I will be attending a trade conference in Harlow Bay on behalf of my employer from March 23 to 25''에서 회사 일로 학회에 참석하기 때문이다. trade conference가 work-related event로 바뀌었다. 오답: (A) 다른 면접, (C) 휴가 이야기는 없다. (D) 추천서는 이미 첨부했다.', 183),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about Mr. Castellanos?', 'Questions 181-185 refer to the following job posting and e-mail.

[Job posting]
Marlowe & Finch Architects
Office Coordinator (Weston Ridge)

Marlowe & Finch Architects, a 35-person firm specializing in the design of schools and public libraries, seeks an office coordinator for its studio in Weston Ridge.

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

This is a full-time position. Benefits include health insurance, 15 days of paid leave, and a monthly transit pass. To apply, send a résumé and a brief cover letter to careers@marlowefinch.com by March 14. Interviews will be held at our studio during the week of March 23. The successful candidate will be expected to start on April 13.

────────────────────

[E-mail]
To: Beatrice Okonkwo <b.okonkwo@marlowefinch.com>
From: Julian Castellanos <jcastellanos@inbox-mail.com>
Date: March 18
Subject: Re: Interview invitation

Dear Ms. Okonkwo,

Thank you for inviting me to interview for the office coordinator position. I would be glad to meet you and Mr. Finch. However, I will be attending a trade conference in Harlow Bay on behalf of my employer from March 23 to 25, so the March 24 appointment you proposed is not possible for me. Could we meet on March 26 or 27 instead? Any time on either day would suit me.

You also asked when I would be available. My employer, Dunmore Engineering, requires four weeks'' notice, so the earliest I could begin is April 27. I hope this will not be an obstacle.

Finally, as requested, I have attached two letters of reference. One is from my supervisor at Dunmore, where I have run the front office for the past three years.

I look forward to hearing from you.

Sincerely,
Julian Castellanos', 'NONE', '["He has worked with Mr. Finch on a past project.", "He has less office experience than the firm requires.", "He has a background the firm prefers but does not require.", "He submitted his application after the stated deadline."]', 'C', 1, '[연계(문서1+2)·추론] 정답 (C). 공고의 ''Experience at an architecture or engineering firm is desirable but not essential''과 이메일의 ''Dunmore Engineering, where I have run the front office for the past three years''를 연결하면, 그는 필수는 아니지만 우대되는 엔지니어링 회사 경력을 갖고 있다. 오답: (A) Finch 씨와 일한 적이 있다는 말은 없다. (B) 3년 경력은 최소 2년 조건을 넘는다. (D) 면접에 초대되었고 지원 시점은 알 수 없다.', 184),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'Which detail in the job posting is Mr. Castellanos unable to meet?', 'Questions 181-185 refer to the following job posting and e-mail.

[Job posting]
Marlowe & Finch Architects
Office Coordinator (Weston Ridge)

Marlowe & Finch Architects, a 35-person firm specializing in the design of schools and public libraries, seeks an office coordinator for its studio in Weston Ridge.

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

This is a full-time position. Benefits include health insurance, 15 days of paid leave, and a monthly transit pass. To apply, send a résumé and a brief cover letter to careers@marlowefinch.com by March 14. Interviews will be held at our studio during the week of March 23. The successful candidate will be expected to start on April 13.

────────────────────

[E-mail]
To: Beatrice Okonkwo <b.okonkwo@marlowefinch.com>
From: Julian Castellanos <jcastellanos@inbox-mail.com>
Date: March 18
Subject: Re: Interview invitation

Dear Ms. Okonkwo,

Thank you for inviting me to interview for the office coordinator position. I would be glad to meet you and Mr. Finch. However, I will be attending a trade conference in Harlow Bay on behalf of my employer from March 23 to 25, so the March 24 appointment you proposed is not possible for me. Could we meet on March 26 or 27 instead? Any time on either day would suit me.

You also asked when I would be available. My employer, Dunmore Engineering, requires four weeks'' notice, so the earliest I could begin is April 27. I hope this will not be an obstacle.

Finally, as requested, I have attached two letters of reference. One is from my supervisor at Dunmore, where I have run the front office for the past three years.

I look forward to hearing from you.

Sincerely,
Julian Castellanos', 'NONE', '["The application deadline", "The expected start date", "The experience requirement", "The interview week"]', 'B', 1, '[연계(문서1+2)] 정답 (B). 공고는 ''expected to start on April 13''이라고 했지만 그는 ''the earliest I could begin is April 27''이라고 썼다. 오답: (A) 지원 마감을 어겼다는 근거는 없다. (C) 3년 경력으로 조건을 충족한다. (D) 그가 제안한 3월 26·27일은 면접 주간(the week of March 23) 안에 든다.', 185),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What feature of the Caldera Mini is emphasized in the advertisement?', 'Questions 186-190 refer to the following advertisement, online review, and e-mail.

[Advertisement]
Introducing the Caldera Mini
Café-quality espresso from a machine no wider than a paperback book

New from Ridgeline Home of Weston Ridge, the Caldera Mini heats up in just 40 seconds and brews with 15 bars of pressure. Its removable 0.8-liter water tank and dishwasher-safe drip tray make cleanup simple. The machine is available in matte black, cream, and slate blue.

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
Rahim Chaudhry', 'NONE', '["Its compact size", "Its quiet operation", "Its built-in grinder", "Its low price"]', 'A', 1, '[세부 사항] 정답 (A). ''a machine no wider than a paperback book''에서 작은 크기를 내세운다. no wider than a paperback book이 compact size로 바뀌었다. 오답: (B) 소음 이야기는 광고에 없다. (C) 그라인더는 Complete 버전에만 있다. (D) 가격이 싸다고 강조하지 않는다.', 186);

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What does Ms. Vos say about the milk frother?', 'Questions 186-190 refer to the following advertisement, online review, and e-mail.

[Advertisement]
Introducing the Caldera Mini
Café-quality espresso from a machine no wider than a paperback book

New from Ridgeline Home of Weston Ridge, the Caldera Mini heats up in just 40 seconds and brews with 15 bars of pressure. Its removable 0.8-liter water tank and dishwasher-safe drip tray make cleanup simple. The machine is available in matte black, cream, and slate blue.

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
Rahim Chaudhry', 'NONE', '["It arrived with a crack in it.", "It makes more noise than she anticipated.", "It is difficult to keep clean.", "It takes a long time to heat up."]', 'B', 1, '[세부 사항] 정답 (B). ''The frother works well too, although it is louder than I expected''에서 생각보다 시끄럽다고 했다. louder than I expected가 more noise than she anticipated로 바뀌었다. 오답: (A) 금이 간 것은 물통(water tank)이다. (C) 세척이 어렵다는 말은 없다. (D) 예열 40초는 칭찬한 점이다.', 187),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about Ms. Vos''s order?', 'Questions 186-190 refer to the following advertisement, online review, and e-mail.

[Advertisement]
Introducing the Caldera Mini
Café-quality espresso from a machine no wider than a paperback book

New from Ridgeline Home of Weston Ridge, the Caldera Mini heats up in just 40 seconds and brews with 15 bars of pressure. Its removable 0.8-liter water tank and dishwasher-safe drip tray make cleanup simple. The machine is available in matte black, cream, and slate blue.

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
Rahim Chaudhry', 'NONE', '["It qualified for free shipping.", "It included a coffee grinder.", "It was placed after the launch offer ended.", "It was the lowest-priced version."]', 'A', 1, '[연계(문서1+2)·추론] 정답 (A). 후기에서 그녀가 산 것은 Caldera Mini Plus이고, 광고에서 Plus는 189달러, ''Shipping is free on orders over $175''이므로 무료 배송 대상이다. 오답: (B) 그라인더는 Complete에만 있다. (C) 5월 20일 주문은 5월 31일 기한 이전이다. (D) 가장 싼 것은 149달러짜리 기본형이다.', 188),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'According to the e-mail, why was Ridgeline Home slow to reply?', 'Questions 186-190 refer to the following advertisement, online review, and e-mail.

[Advertisement]
Introducing the Caldera Mini
Café-quality espresso from a machine no wider than a paperback book

New from Ridgeline Home of Weston Ridge, the Caldera Mini heats up in just 40 seconds and brews with 15 bars of pressure. Its removable 0.8-liter water tank and dishwasher-safe drip tray make cleanup simple. The machine is available in matte black, cream, and slate blue.

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
Rahim Chaudhry', 'NONE', '["It had an unexpectedly high number of orders.", "It closed its support office for a holiday.", "It had a technical problem with its Web site.", "It lost the message Ms. Vos sent on June 2."]', 'A', 1, '[세부 사항] 정답 (A). ''we received far more orders than we had anticipated, and our support team fell behind''에서 예상보다 주문이 많아 응대가 밀렸다고 했다. far more … than we had anticipated가 unexpectedly high number로 바뀌었다. 오답: (B) 휴무, (C) 웹사이트 장애, (D) 메일 분실은 언급이 없다.', 189),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What issue raised by Ms. Vos does Mr. Chaudhry NOT address in his e-mail?', 'Questions 186-190 refer to the following advertisement, online review, and e-mail.

[Advertisement]
Introducing the Caldera Mini
Café-quality espresso from a machine no wider than a paperback book

New from Ridgeline Home of Weston Ridge, the Caldera Mini heats up in just 40 seconds and brews with 15 bars of pressure. Its removable 0.8-liter water tank and dishwasher-safe drip tray make cleanup simple. The machine is available in matte black, cream, and slate blue.

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
Rahim Chaudhry', 'NONE', '["The damaged part", "The loud frother", "The missing gift", "The delayed reply"]', 'B', 1, '[연계(문서2+3)·사실 확인(NOT)] 정답 (B). 후기의 불만은 물통 균열, 답변 지연, 컵 누락, 거품기 소음인데, 이메일은 거품기 소음에 대해서는 아무 말도 하지 않는다. 오답: (A) ''A replacement water tank was shipped'', (C) ''The cups … yours are in the same package'', (D) ''apologies for our slow response''로 각각 다루었다.', 190),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'According to the Web page, what is included with every booth?', 'Questions 191-195 refer to the following Web page, form, and e-mail.

[Web page]
Harbor Lights Festival — Vendor Booths

The Harlow Bay Tourism Office invites businesses to take part in the 12th annual Harbor Lights Festival, to be held September 18-20 on the Marina Promenade.

Booth type | Location | Size | Fee (three days)
Promenade | Along the waterfront walk | 3 m x 3 m | $240
Plaza | Fountain Plaza, near the main stage | 3 m x 3 m | $300
Market Tent | Inside the shared tent on Pier 2 | 2 m x 2 m | $150
Food Court | Parking area on Quay Street | 4 m x 6 m | $360

Every booth comes with one table and two chairs. Electricity is available at Plaza and Food Court booths only. Businesses registered in Harlow Bay receive 10 percent off the booth fee. Applications are due July 31, and vendors will be notified of their booth assignments by e-mail in early August. Fees are fully refundable until August 31.

────────────────────

[Form]
Harbor Lights Festival — Booth Application

Business name: Saltgrass Candle Company
Contact person: Mireille Fontaine
Business address: 27 Lantern Row, Harlow Bay
Products: Hand-poured candles and soaps
Booth type requested: Promenade
Second choice: Market Tent
Electricity needed: No
Number of staff at booth: 2
Participated in a previous year: Yes (last year)
Comments: If possible, please place us away from the food vendors. Cooking smells make it hard for customers to sample our scented products.
Date submitted: July 12

────────────────────

[E-mail]
To: Mireille Fontaine
From: Callum Reyes, Harlow Bay Tourism Office
Date: August 4
Subject: Your booth assignment

Dear Ms. Fontaine,

I am pleased to confirm that Saltgrass Candle Company has been accepted as a vendor at this year''s festival. Unfortunately, the booth type you requested first filled up quickly, so we have assigned you your second choice. Your space, number 14, is nowhere near the Food Court, as you asked.

Your fee, less the discount for local businesses, is payable by August 15 through the link below. Setup begins at 7:00 A.M. on September 18, and all vendors must be ready by 10:00 A.M., when the festival opens. Because of roadwork on Quay Street, vendors unloading goods this year should enter from Marina Drive instead. Please bring a printed copy of this message, which will serve as your vendor pass, on the first morning.

An online vendor orientation will be held on September 10 at 6:00 P.M. Attendance is optional for returning vendors.

Best regards,
Callum Reyes', 'NONE', '["A canopy for shade", "An electrical outlet", "A lockable storage box", "A table and seating"]', 'D', 1, '[세부 사항] 정답 (D). ''Every booth comes with one table and two chairs''에서 모든 부스에 탁자와 의자가 제공된다. two chairs가 seating으로 바뀌었다. 오답: (A) 차양막 이야기는 없다. (B) 전기는 Plaza와 Food Court 부스에서만 쓸 수 있다. (C) 보관함 이야기는 없다.', 191),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What does Ms. Fontaine request on the form?', 'Questions 191-195 refer to the following Web page, form, and e-mail.

[Web page]
Harbor Lights Festival — Vendor Booths

The Harlow Bay Tourism Office invites businesses to take part in the 12th annual Harbor Lights Festival, to be held September 18-20 on the Marina Promenade.

Booth type | Location | Size | Fee (three days)
Promenade | Along the waterfront walk | 3 m x 3 m | $240
Plaza | Fountain Plaza, near the main stage | 3 m x 3 m | $300
Market Tent | Inside the shared tent on Pier 2 | 2 m x 2 m | $150
Food Court | Parking area on Quay Street | 4 m x 6 m | $360

Every booth comes with one table and two chairs. Electricity is available at Plaza and Food Court booths only. Businesses registered in Harlow Bay receive 10 percent off the booth fee. Applications are due July 31, and vendors will be notified of their booth assignments by e-mail in early August. Fees are fully refundable until August 31.

────────────────────

[Form]
Harbor Lights Festival — Booth Application

Business name: Saltgrass Candle Company
Contact person: Mireille Fontaine
Business address: 27 Lantern Row, Harlow Bay
Products: Hand-poured candles and soaps
Booth type requested: Promenade
Second choice: Market Tent
Electricity needed: No
Number of staff at booth: 2
Participated in a previous year: Yes (last year)
Comments: If possible, please place us away from the food vendors. Cooking smells make it hard for customers to sample our scented products.
Date submitted: July 12

────────────────────

[E-mail]
To: Mireille Fontaine
From: Callum Reyes, Harlow Bay Tourism Office
Date: August 4
Subject: Your booth assignment

Dear Ms. Fontaine,

I am pleased to confirm that Saltgrass Candle Company has been accepted as a vendor at this year''s festival. Unfortunately, the booth type you requested first filled up quickly, so we have assigned you your second choice. Your space, number 14, is nowhere near the Food Court, as you asked.

Your fee, less the discount for local businesses, is payable by August 15 through the link below. Setup begins at 7:00 A.M. on September 18, and all vendors must be ready by 10:00 A.M., when the festival opens. Because of roadwork on Quay Street, vendors unloading goods this year should enter from Marina Drive instead. Please bring a printed copy of this message, which will serve as your vendor pass, on the first morning.

An online vendor orientation will be held on September 10 at 6:00 P.M. Attendance is optional for returning vendors.

Best regards,
Callum Reyes', 'NONE', '["A booth close to the main stage", "A connection to electricity", "An extra table for product samples", "A space that is not near food sellers"]', 'D', 1, '[세부 사항] 정답 (D). Comments의 ''please place us away from the food vendors''에서 음식 판매자와 떨어진 자리를 요청한다. away from the food vendors가 not near food sellers로 바뀌었다. 오답: (A) 무대 근처인 Plaza는 신청하지 않았다. (B) ''Electricity needed: No''다. (C) 탁자 추가 요청은 없다.', 192),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'How much will Ms. Fontaine most likely be charged for her booth?', 'Questions 191-195 refer to the following Web page, form, and e-mail.

[Web page]
Harbor Lights Festival — Vendor Booths

The Harlow Bay Tourism Office invites businesses to take part in the 12th annual Harbor Lights Festival, to be held September 18-20 on the Marina Promenade.

Booth type | Location | Size | Fee (three days)
Promenade | Along the waterfront walk | 3 m x 3 m | $240
Plaza | Fountain Plaza, near the main stage | 3 m x 3 m | $300
Market Tent | Inside the shared tent on Pier 2 | 2 m x 2 m | $150
Food Court | Parking area on Quay Street | 4 m x 6 m | $360

Every booth comes with one table and two chairs. Electricity is available at Plaza and Food Court booths only. Businesses registered in Harlow Bay receive 10 percent off the booth fee. Applications are due July 31, and vendors will be notified of their booth assignments by e-mail in early August. Fees are fully refundable until August 31.

────────────────────

[Form]
Harbor Lights Festival — Booth Application

Business name: Saltgrass Candle Company
Contact person: Mireille Fontaine
Business address: 27 Lantern Row, Harlow Bay
Products: Hand-poured candles and soaps
Booth type requested: Promenade
Second choice: Market Tent
Electricity needed: No
Number of staff at booth: 2
Participated in a previous year: Yes (last year)
Comments: If possible, please place us away from the food vendors. Cooking smells make it hard for customers to sample our scented products.
Date submitted: July 12

────────────────────

[E-mail]
To: Mireille Fontaine
From: Callum Reyes, Harlow Bay Tourism Office
Date: August 4
Subject: Your booth assignment

Dear Ms. Fontaine,

I am pleased to confirm that Saltgrass Candle Company has been accepted as a vendor at this year''s festival. Unfortunately, the booth type you requested first filled up quickly, so we have assigned you your second choice. Your space, number 14, is nowhere near the Food Court, as you asked.

Your fee, less the discount for local businesses, is payable by August 15 through the link below. Setup begins at 7:00 A.M. on September 18, and all vendors must be ready by 10:00 A.M., when the festival opens. Because of roadwork on Quay Street, vendors unloading goods this year should enter from Marina Drive instead. Please bring a printed copy of this message, which will serve as your vendor pass, on the first morning.

An online vendor orientation will be held on September 10 at 6:00 P.M. Attendance is optional for returning vendors.

Best regards,
Callum Reyes', 'NONE', '["$240", "$216", "$150", "$135"]', 'D', 1, '[연계(문서1+2+3)] 정답 (D). 이메일에서 2지망으로 배정되었고(''assigned you your second choice''), 신청서의 2지망은 Market Tent, 웹 페이지에서 Market Tent는 150달러다. 여기에 Harlow Bay 업체 10% 할인(''less the discount for local businesses'')을 적용하면 135달러다. 오답: (A) Promenade 정가, (B) Promenade 할인가, (C) Market Tent 정가다.', 193),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'According to the e-mail, what is different for vendors this year?', 'Questions 191-195 refer to the following Web page, form, and e-mail.

[Web page]
Harbor Lights Festival — Vendor Booths

The Harlow Bay Tourism Office invites businesses to take part in the 12th annual Harbor Lights Festival, to be held September 18-20 on the Marina Promenade.

Booth type | Location | Size | Fee (three days)
Promenade | Along the waterfront walk | 3 m x 3 m | $240
Plaza | Fountain Plaza, near the main stage | 3 m x 3 m | $300
Market Tent | Inside the shared tent on Pier 2 | 2 m x 2 m | $150
Food Court | Parking area on Quay Street | 4 m x 6 m | $360

Every booth comes with one table and two chairs. Electricity is available at Plaza and Food Court booths only. Businesses registered in Harlow Bay receive 10 percent off the booth fee. Applications are due July 31, and vendors will be notified of their booth assignments by e-mail in early August. Fees are fully refundable until August 31.

────────────────────

[Form]
Harbor Lights Festival — Booth Application

Business name: Saltgrass Candle Company
Contact person: Mireille Fontaine
Business address: 27 Lantern Row, Harlow Bay
Products: Hand-poured candles and soaps
Booth type requested: Promenade
Second choice: Market Tent
Electricity needed: No
Number of staff at booth: 2
Participated in a previous year: Yes (last year)
Comments: If possible, please place us away from the food vendors. Cooking smells make it hard for customers to sample our scented products.
Date submitted: July 12

────────────────────

[E-mail]
To: Mireille Fontaine
From: Callum Reyes, Harlow Bay Tourism Office
Date: August 4
Subject: Your booth assignment

Dear Ms. Fontaine,

I am pleased to confirm that Saltgrass Candle Company has been accepted as a vendor at this year''s festival. Unfortunately, the booth type you requested first filled up quickly, so we have assigned you your second choice. Your space, number 14, is nowhere near the Food Court, as you asked.

Your fee, less the discount for local businesses, is payable by August 15 through the link below. Setup begins at 7:00 A.M. on September 18, and all vendors must be ready by 10:00 A.M., when the festival opens. Because of roadwork on Quay Street, vendors unloading goods this year should enter from Marina Drive instead. Please bring a printed copy of this message, which will serve as your vendor pass, on the first morning.

An online vendor orientation will be held on September 10 at 6:00 P.M. Attendance is optional for returning vendors.

Best regards,
Callum Reyes', 'NONE', '["The opening time of the festival", "The deadline for paying fees", "The route for bringing in goods", "The format of the orientation"]', 'C', 1, '[세부 사항] 정답 (C). ''Because of roadwork on Quay Street, vendors unloading goods this year should enter from Marina Drive instead''에서 올해는 물품 반입 경로가 달라진다. 오답: (A) 개장 시각, (B) 납부 기한, (D) 오리엔테이션 방식이 올해 바뀌었다는 말은 없다.', 194),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is suggested about the September 10 orientation?', 'Questions 191-195 refer to the following Web page, form, and e-mail.

[Web page]
Harbor Lights Festival — Vendor Booths

The Harlow Bay Tourism Office invites businesses to take part in the 12th annual Harbor Lights Festival, to be held September 18-20 on the Marina Promenade.

Booth type | Location | Size | Fee (three days)
Promenade | Along the waterfront walk | 3 m x 3 m | $240
Plaza | Fountain Plaza, near the main stage | 3 m x 3 m | $300
Market Tent | Inside the shared tent on Pier 2 | 2 m x 2 m | $150
Food Court | Parking area on Quay Street | 4 m x 6 m | $360

Every booth comes with one table and two chairs. Electricity is available at Plaza and Food Court booths only. Businesses registered in Harlow Bay receive 10 percent off the booth fee. Applications are due July 31, and vendors will be notified of their booth assignments by e-mail in early August. Fees are fully refundable until August 31.

────────────────────

[Form]
Harbor Lights Festival — Booth Application

Business name: Saltgrass Candle Company
Contact person: Mireille Fontaine
Business address: 27 Lantern Row, Harlow Bay
Products: Hand-poured candles and soaps
Booth type requested: Promenade
Second choice: Market Tent
Electricity needed: No
Number of staff at booth: 2
Participated in a previous year: Yes (last year)
Comments: If possible, please place us away from the food vendors. Cooking smells make it hard for customers to sample our scented products.
Date submitted: July 12

────────────────────

[E-mail]
To: Mireille Fontaine
From: Callum Reyes, Harlow Bay Tourism Office
Date: August 4
Subject: Your booth assignment

Dear Ms. Fontaine,

I am pleased to confirm that Saltgrass Candle Company has been accepted as a vendor at this year''s festival. Unfortunately, the booth type you requested first filled up quickly, so we have assigned you your second choice. Your space, number 14, is nowhere near the Food Court, as you asked.

Your fee, less the discount for local businesses, is payable by August 15 through the link below. Setup begins at 7:00 A.M. on September 18, and all vendors must be ready by 10:00 A.M., when the festival opens. Because of roadwork on Quay Street, vendors unloading goods this year should enter from Marina Drive instead. Please bring a printed copy of this message, which will serve as your vendor pass, on the first morning.

An online vendor orientation will be held on September 10 at 6:00 P.M. Attendance is optional for returning vendors.

Best regards,
Callum Reyes', 'NONE', '["It will take place on the Marina Promenade.", "It will be led by Ms. Fontaine.", "It has been moved to a later time.", "It is not mandatory for Ms. Fontaine."]', 'D', 1, '[연계(문서2+3)·추론] 정답 (D). 이메일의 ''Attendance is optional for returning vendors''와 신청서의 ''Participated in a previous year: Yes (last year)''를 연결하면 Fontaine 씨는 재참가 업체이므로 참석이 의무가 아니다. optional이 not mandatory로 바뀌었다. 오답: (A) 온라인으로 열린다. (B) 진행자 언급은 없다. (C) 시간이 바뀌었다는 말은 없다.', 195),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is the purpose of the notice?', 'Questions 196-200 refer to the following notice, e-mail, and form.

[Notice]
Halvorsen Biotech — Weston Ridge Campus
Parking Structure Repairs

From March 2 through May 29, levels 2 and 3 of the employee parking structure will be closed for resurfacing, and about 180 of our 400 spaces will be unavailable. To reduce demand during this period, the company is offering the options below. Each employee may choose one.

Option A (Shuttle): Park free of charge at the Fairgrounds lot on Cutler Avenue. Shuttles run every 15 minutes from 6:30 to 9:30 A.M. and from 4:00 to 7:00 P.M.
Option B (Transit): Receive a regional bus and rail pass. The company will pay the full monthly cost.
Option C (Carpool): Groups of three or more employees receive a reserved space on level 1.
Option D (Regular parking): Continue to park in the structure. Open spaces are first come, first served.

Submit the Commuter Option Form to Facilities by February 13. Employees who do not submit a form will be assigned Option D. We apologize for the inconvenience and thank you for your cooperation.

────────────────────

[E-mail]
To: Facilities <facilities@halvorsenbio.com>
From: Soo-jin Hwang
Date: February 5
Subject: Question about commuter options

Hello,

I work in the Quality Lab, and my shift ends at 7:30 P.M. three days a week. I was planning to park at the Fairgrounds, but it looks as though I would miss the last shuttle on those evenings. Will the shuttle run any later? I checked the notice but could not find an answer. If the hours stay the same, I will have to choose something else. Two colleagues who live near me have suggested that we drive in together.

Also, may the form be submitted by e-mail? I will be at a training course in Harlow Bay from February 9 to 13 and will not be on campus.

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
Other members of group (Option C only): Paolo Greco, Ifeoma Nwosu
Start date: March 2
Vehicle license plates: WR 5521 (Hwang), WR 9048 (Nwosu)
Notes: Mr. Greco does not own a car. Ms. Nwosu and I will take turns driving.

For Facilities use
Received: February 10, by e-mail
Approved by: Lars Edstrom', 'NONE', '["To announce a new fee for employee parking", "To describe commuting choices during repair work", "To report the opening of an additional parking lot", "To recruit drivers for a company shuttle service"]', 'B', 1, '[주제·목적] 정답 (B). 주차장 2·3층이 보수로 닫히는 동안 ''the company is offering the options below''라며 셔틀·대중교통·카풀·기존 주차의 네 가지 통근 방법을 설명한다. resurfacing이 repair work로, options가 choices로 바뀌었다. 오답: (A) 요금 부과는 없다. (C) Fairgrounds는 임시로 쓰는 곳이지 새 주차장 개장 소식이 아니다. (D) 운전자 모집이 아니다.', 196),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'According to the notice, what will happen to employees who do not submit a form?', 'Questions 196-200 refer to the following notice, e-mail, and form.

[Notice]
Halvorsen Biotech — Weston Ridge Campus
Parking Structure Repairs

From March 2 through May 29, levels 2 and 3 of the employee parking structure will be closed for resurfacing, and about 180 of our 400 spaces will be unavailable. To reduce demand during this period, the company is offering the options below. Each employee may choose one.

Option A (Shuttle): Park free of charge at the Fairgrounds lot on Cutler Avenue. Shuttles run every 15 minutes from 6:30 to 9:30 A.M. and from 4:00 to 7:00 P.M.
Option B (Transit): Receive a regional bus and rail pass. The company will pay the full monthly cost.
Option C (Carpool): Groups of three or more employees receive a reserved space on level 1.
Option D (Regular parking): Continue to park in the structure. Open spaces are first come, first served.

Submit the Commuter Option Form to Facilities by February 13. Employees who do not submit a form will be assigned Option D. We apologize for the inconvenience and thank you for your cooperation.

────────────────────

[E-mail]
To: Facilities <facilities@halvorsenbio.com>
From: Soo-jin Hwang
Date: February 5
Subject: Question about commuter options

Hello,

I work in the Quality Lab, and my shift ends at 7:30 P.M. three days a week. I was planning to park at the Fairgrounds, but it looks as though I would miss the last shuttle on those evenings. Will the shuttle run any later? I checked the notice but could not find an answer. If the hours stay the same, I will have to choose something else. Two colleagues who live near me have suggested that we drive in together.

Also, may the form be submitted by e-mail? I will be at a training course in Harlow Bay from February 9 to 13 and will not be on campus.

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
Other members of group (Option C only): Paolo Greco, Ifeoma Nwosu
Start date: March 2
Vehicle license plates: WR 5521 (Hwang), WR 9048 (Nwosu)
Notes: Mr. Greco does not own a car. Ms. Nwosu and I will take turns driving.

For Facilities use
Received: February 10, by e-mail
Approved by: Lars Edstrom', 'NONE', '["They will lose their parking privileges.", "They will keep parking in the structure.", "They will be issued a transit pass.", "They will be placed in a carpool group."]', 'B', 1, '[세부 사항] 정답 (B). ''Employees who do not submit a form will be assigned Option D''이고 Option D는 ''Continue to park in the structure''다. 오답: (A) 주차 자격을 잃는다는 말은 없다. (C) 교통 패스는 Option B, (D) 카풀은 Option C를 고른 경우다.', 197),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What does Ms. Hwang ask about the shuttle?', 'Questions 196-200 refer to the following notice, e-mail, and form.

[Notice]
Halvorsen Biotech — Weston Ridge Campus
Parking Structure Repairs

From March 2 through May 29, levels 2 and 3 of the employee parking structure will be closed for resurfacing, and about 180 of our 400 spaces will be unavailable. To reduce demand during this period, the company is offering the options below. Each employee may choose one.

Option A (Shuttle): Park free of charge at the Fairgrounds lot on Cutler Avenue. Shuttles run every 15 minutes from 6:30 to 9:30 A.M. and from 4:00 to 7:00 P.M.
Option B (Transit): Receive a regional bus and rail pass. The company will pay the full monthly cost.
Option C (Carpool): Groups of three or more employees receive a reserved space on level 1.
Option D (Regular parking): Continue to park in the structure. Open spaces are first come, first served.

Submit the Commuter Option Form to Facilities by February 13. Employees who do not submit a form will be assigned Option D. We apologize for the inconvenience and thank you for your cooperation.

────────────────────

[E-mail]
To: Facilities <facilities@halvorsenbio.com>
From: Soo-jin Hwang
Date: February 5
Subject: Question about commuter options

Hello,

I work in the Quality Lab, and my shift ends at 7:30 P.M. three days a week. I was planning to park at the Fairgrounds, but it looks as though I would miss the last shuttle on those evenings. Will the shuttle run any later? I checked the notice but could not find an answer. If the hours stay the same, I will have to choose something else. Two colleagues who live near me have suggested that we drive in together.

Also, may the form be submitted by e-mail? I will be at a training course in Harlow Bay from February 9 to 13 and will not be on campus.

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
Other members of group (Option C only): Paolo Greco, Ifeoma Nwosu
Start date: March 2
Vehicle license plates: WR 5521 (Hwang), WR 9048 (Nwosu)
Notes: Mr. Greco does not own a car. Ms. Nwosu and I will take turns driving.

For Facilities use
Received: February 10, by e-mail
Approved by: Lars Edstrom', 'NONE', '["Whether its evening hours will be extended", "Where it will drop off passengers on campus", "How much employees will pay to ride it", "How often it will run in the morning"]', 'A', 1, '[세부 사항] 정답 (A). 근무가 저녁 7시 30분에 끝나 마지막 셔틀을 놓친다고 하면서 ''Will the shuttle run any later?''라고 묻는다. run any later가 evening hours will be extended로 바뀌었다. 오답: (B) 하차 장소, (C) 요금, (D) 아침 운행 간격은 묻지 않았다.', 198),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is indicated about the option Ms. Hwang selected?', 'Questions 196-200 refer to the following notice, e-mail, and form.

[Notice]
Halvorsen Biotech — Weston Ridge Campus
Parking Structure Repairs

From March 2 through May 29, levels 2 and 3 of the employee parking structure will be closed for resurfacing, and about 180 of our 400 spaces will be unavailable. To reduce demand during this period, the company is offering the options below. Each employee may choose one.

Option A (Shuttle): Park free of charge at the Fairgrounds lot on Cutler Avenue. Shuttles run every 15 minutes from 6:30 to 9:30 A.M. and from 4:00 to 7:00 P.M.
Option B (Transit): Receive a regional bus and rail pass. The company will pay the full monthly cost.
Option C (Carpool): Groups of three or more employees receive a reserved space on level 1.
Option D (Regular parking): Continue to park in the structure. Open spaces are first come, first served.

Submit the Commuter Option Form to Facilities by February 13. Employees who do not submit a form will be assigned Option D. We apologize for the inconvenience and thank you for your cooperation.

────────────────────

[E-mail]
To: Facilities <facilities@halvorsenbio.com>
From: Soo-jin Hwang
Date: February 5
Subject: Question about commuter options

Hello,

I work in the Quality Lab, and my shift ends at 7:30 P.M. three days a week. I was planning to park at the Fairgrounds, but it looks as though I would miss the last shuttle on those evenings. Will the shuttle run any later? I checked the notice but could not find an answer. If the hours stay the same, I will have to choose something else. Two colleagues who live near me have suggested that we drive in together.

Also, may the form be submitted by e-mail? I will be at a training course in Harlow Bay from February 9 to 13 and will not be on campus.

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
Other members of group (Option C only): Paolo Greco, Ifeoma Nwosu
Start date: March 2
Vehicle license plates: WR 5521 (Hwang), WR 9048 (Nwosu)
Notes: Mr. Greco does not own a car. Ms. Nwosu and I will take turns driving.

For Facilities use
Received: February 10, by e-mail
Approved by: Lars Edstrom', 'NONE', '["It requires her to pay a monthly fee.", "It gives her group a designated parking space.", "It involves parking at the Fairgrounds lot.", "It was submitted after the deadline."]', 'B', 1, '[연계(문서1+3)] 정답 (B). 신청서의 ''Option selected: C''를 공지의 ''Option C (Carpool): Groups of three or more employees receive a reserved space on level 1''과 연결하면, 그녀의 카풀 모임은 지정 주차 자리를 받는다. reserved가 designated로 바뀌었다. 오답: (A) 요금 이야기는 없다. (C) Fairgrounds 주차는 Option A다. (D) 2월 10일 접수는 기한(2월 13일) 이전이다.', 199),
(@toeic_full01_exam_id, 'READING', 'TOEIC_P7', 'What is most likely true about Ms. Hwang on February 10?', 'Questions 196-200 refer to the following notice, e-mail, and form.

[Notice]
Halvorsen Biotech — Weston Ridge Campus
Parking Structure Repairs

From March 2 through May 29, levels 2 and 3 of the employee parking structure will be closed for resurfacing, and about 180 of our 400 spaces will be unavailable. To reduce demand during this period, the company is offering the options below. Each employee may choose one.

Option A (Shuttle): Park free of charge at the Fairgrounds lot on Cutler Avenue. Shuttles run every 15 minutes from 6:30 to 9:30 A.M. and from 4:00 to 7:00 P.M.
Option B (Transit): Receive a regional bus and rail pass. The company will pay the full monthly cost.
Option C (Carpool): Groups of three or more employees receive a reserved space on level 1.
Option D (Regular parking): Continue to park in the structure. Open spaces are first come, first served.

Submit the Commuter Option Form to Facilities by February 13. Employees who do not submit a form will be assigned Option D. We apologize for the inconvenience and thank you for your cooperation.

────────────────────

[E-mail]
To: Facilities <facilities@halvorsenbio.com>
From: Soo-jin Hwang
Date: February 5
Subject: Question about commuter options

Hello,

I work in the Quality Lab, and my shift ends at 7:30 P.M. three days a week. I was planning to park at the Fairgrounds, but it looks as though I would miss the last shuttle on those evenings. Will the shuttle run any later? I checked the notice but could not find an answer. If the hours stay the same, I will have to choose something else. Two colleagues who live near me have suggested that we drive in together.

Also, may the form be submitted by e-mail? I will be at a training course in Harlow Bay from February 9 to 13 and will not be on campus.

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
Other members of group (Option C only): Paolo Greco, Ifeoma Nwosu
Start date: March 2
Vehicle license plates: WR 5521 (Hwang), WR 9048 (Nwosu)
Notes: Mr. Greco does not own a car. Ms. Nwosu and I will take turns driving.

For Facilities use
Received: February 10, by e-mail
Approved by: Lars Edstrom', 'NONE', '["She was working an evening shift in the Quality Lab.", "She was attending a training course in another city.", "She met with Mr. Edstrom in the Facilities office.", "She rode the shuttle from the Fairgrounds lot."]', 'B', 1, '[연계(문서2+3)·추론] 정답 (B). 이메일에서 ''I will be at a training course in Harlow Bay from February 9 to 13 and will not be on campus''라고 했고, 신청서는 그 기간인 2월 10일에 이메일로 접수되었다(''Received: February 10, by e-mail''). 따라서 그날 그녀는 Harlow Bay에서 교육을 받고 있었을 것이다. 오답: (A)·(C) 캠퍼스에 없었다. (D) 셔틀은 3월 2일부터 운행하는 선택지다.', 200);
