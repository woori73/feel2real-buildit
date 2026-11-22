-- TestPrep 그림·표 문항 추가: TOEIC FULL01에 Part 1(1~6번)과 그래픽 문항 5개, SAT 연습 세트 01에 그림 문항 4개.
-- 스키마는 바꾸지 않는다. 기존 세트(V1131 TP-TOEIC-FULL-01, V1129 TP-SAT-PRAC-01)의 행을 더하고 고친다.
-- 1) TP-TOEIC-FULL-01: Part 1 사진 묘사 6문항 INSERT(order_no 1~6, TOEIC_P1). 사진이 아니라 F2R이 그린 선 그림(SVG)이다.
--    Part 3의 62-64·65-67·68-70번, Part 4의 95-97·98-100번 묶음에 시각자료([[show]] 뒤의 표·그림)를 붙이고
--    묶음마다 한 문항(64·66·69·97·98번)을 "Look at the graphic." 문항으로 바꿨다. 정답 글자는 그대로다.
--    묶음 세 문항의 body_text는 글자까지 같다(화면이 한 묶음으로 보여 준다). 194문항 → 200문항.
-- 2) TP-SAT-PRAC-01: 12번(읽기·쓰기 근거형: 표 → 막대그래프), 29번(막대그래프의 평균), 33번(삼각형 그림), 39번(좌표평면의 직선).
--    문항 수(27 + 22)·영역 순서·정답 글자는 그대로다.
-- 그림 파일: src/main/resources/static/testprep/img/ (toeic_full01_p1_01~06.svg, toeic_full01_g_95.svg, sat_prac01_q12·q29·q33·q39.svg).
-- 표기(show 줄, img 그림 줄, | 표):docs/design/testprep-toeic.md 4장, docs/design/testprep-digital-sat.md §5.
-- ETS·College Board의 문항·지문·수치·그림은 쓰지 않았다(형식만 참고). 생성기: naesin-workspace/testprep/toeic/graphics/build_v1135.py

SET @toeic_full01_exam_id = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-TOEIC-FULL-01');

UPDATE tp_exams SET subtitle = 'Listening Part 1–4 (100 questions) · Reading Part 5–7 (100 questions) · 120 minutes',
  description = 'A full-length TOEIC Listening & Reading practice test written by F2R: Part 1 (6), Part 2 (25), Part 3 (39), Part 4 (30), Part 5 (30), Part 6 (16) and Part 7 (54) — 200 questions. Part 1 uses simple line drawings instead of photographs, and five Part 3–4 sets include a graphic (list, schedule or floor plan). Listening scripts are read aloud by the browser in practice mode. Scores are F2R internal estimates, not official ETS TOEIC scores. 듣기는 브라우저 음성으로 읽어 줍니다. Part 1의 그림은 사진이 아니라 F2R이 그린 선 그림입니다.'
WHERE exam_code = 'TP-TOEIC-FULL-01';

INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no) VALUES
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P1', 'Look at the picture and listen to the four statements. Choose the statement that best describes the picture.', 'A. He''s opening a door.
B. He''s carrying a box.
C. He''s climbing a ladder.
D. He''s taping a box shut.
[[show]]
[[img:/testprep/img/toeic_full01_p1_01.svg]]', 'TTS', '["", "", "", ""]', 'B', 1, '[Part1-인물(동작)] 정답 (B). 남자가 상자를 받쳐 들고 걸어가고 있다. 오답: (A) 문은 남자 뒤쪽에 닫혀 있고 남자는 문을 잡고 있지 않다. (C) 사다리는 오른쪽에 세워져 있을 뿐 남자는 바닥을 걷고 있다. (D) 상자를 들고 있을 뿐 테이프를 붙이는 동작이 아니다.
포인트: 인물 사진 — 동작 동사 구별(carry / open / climb / tape)', 1),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P1', 'Look at the picture and listen to the four statements. Choose the statement that best describes the picture.', 'A. She''s drinking from a cup.
B. She''s reaching for a lamp.
C. She''s closing a window.
D. She''s typing on a laptop.
[[show]]
[[img:/testprep/img/toeic_full01_p1_02.svg]]', 'TTS', '["", "", "", ""]', 'D', 1, '[Part1-인물(동작)] 정답 (D). 여자가 책상 앞에 앉아 노트북 자판에 손을 올리고 있다. 오답: (A) 컵은 책상 위에 놓여 있을 뿐 마시고 있지 않다. (B) 손은 노트북에 있고 스탠드(lamp)는 책상 끝에 떨어져 있다. laptop과 소리가 비슷한 lamp를 쓴 함정이다. (C) 창문은 여자 뒤쪽 벽에 닫혀 있고 여자는 창문을 만지고 있지 않다.
포인트: 인물 사진 — 사진 속 사물(cup, lamp, window)을 넣은 오답, 유사 발음(laptop / lamp)', 2),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P1', 'Look at the picture and listen to the four statements. Choose the statement that best describes the picture.', 'A. They''re shaking hands.
B. They''re moving a table.
C. They''re pulling out some chairs.
D. They''re writing on a board.
[[show]]
[[img:/testprep/img/toeic_full01_p1_03.svg]]', 'TTS', '["", "", "", ""]', 'A', 1, '[Part1-인물(2인 동작)] 정답 (A). 두 사람이 마주 서서 손을 맞잡고 있다. 오답: (B) 탁자는 뒤쪽에 놓여 있을 뿐 옮기고 있지 않다. (C) 의자 두 개는 탁자 옆에 비어 있고 두 사람은 의자를 만지고 있지 않다. (D) 벽의 칠판은 비어 있고 쓰는 사람이 없다.
포인트: 인물 사진(2인) — 공통 동작(They''re -ing), 사진 속 사물(table, chairs, board)을 넣은 오답', 3),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P1', 'Look at the picture and listen to the four statements. Choose the statement that best describes the picture.', 'A. A woman is wiping a window.
B. A woman is filling a bucket.
C. A chair is being moved.
D. The floor is being swept.
[[show]]
[[img:/testprep/img/toeic_full01_p1_04.svg]]', 'TTS', '["", "", "", ""]', 'D', 1, '[Part1-인물(동작·수동 진행)] 정답 (D). 여자가 빗자루로 바닥을 쓸고 있다. 사람이 하고 있는 동작을 사물을 주어로 한 수동 진행형(is being swept)으로 말했다. 오답: (A) 창문은 오른쪽 벽에 있고 여자는 창문을 닦고 있지 않다. (B) 양동이는 여자 뒤쪽 바닥에 놓여 있을 뿐이다. (C) 의자는 창문 아래에 놓여 있고 옮기는 사람이 없다.
포인트: 수동 진행형(is being p.p.) — 누군가 그 동작을 하고 있어야 정답', 4),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P1', 'Look at the picture and listen to the four statements. Choose the statement that best describes the picture.', 'A. A man is repairing a bicycle near a tree.
B. Some people are resting on a bench.
C. Bicycles have been parked near a bench.
D. A tree is being planted.
[[show]]
[[img:/testprep/img/toeic_full01_p1_05.svg]]', 'TTS', '["", "", "", ""]', 'C', 1, '[Part1-사물·풍경(상태)] 정답 (C). 벤치 바로 옆에 자전거 두 대가 세워져 있다. 이미 끝난 동작의 결과(상태)를 현재완료 수동(have been parked)으로 말했다. 오답: (A)·(B) 그림에 사람이 없다. (D) 나무는 이미 서 있고 심는 사람이 없으므로 수동 진행형(is being planted)은 맞지 않는다.
포인트: 사물·풍경 사진 — 사람이 없으면 사람 주어·수동 진행형은 오답, 현재완료 수동(have been p.p.)은 상태', 5),
(@toeic_full01_exam_id, 'LISTENING', 'TOEIC_P1', 'Look at the picture and listen to the four statements. Choose the statement that best describes the picture.', 'A. A clock has been set on a desk.
B. Some books are piled on a chair.
C. A chair is being pushed under a desk.
D. There''s a potted plant beside a desk.
[[show]]
[[img:/testprep/img/toeic_full01_p1_06.svg]]', 'TTS', '["", "", "", ""]', 'D', 1, '[Part1-사물(위치)] 정답 (D). 책상 오른쪽 바닥에 화분이 놓여 있다. 오답: (A) 시계는 책상 위가 아니라 벽에 걸려 있다. (B) 책은 의자가 아니라 벽 선반에 꽂혀 있다. (C) 의자는 책상 앞에 놓여 있을 뿐 미는 사람이 없으므로 수동 진행형은 맞지 않는다.
포인트: 사물 사진 — 위치 전치사(on / beside / under), There is 구문, 사람 없는 사진의 수동 진행형 함정', 6);

UPDATE tp_questions SET body_text = 'Questions 62 through 64 refer to the following conversation and list.

Man: Brookvale Family Clinic. This is Stefan.
Woman: Hi, Stefan. It''s Rosa Delgado. I''ve got my annual checkup with Doctor Simmons tomorrow at ten, but I was just told I have a meeting then. Is there anything later in the day?
Man: Let me look. Hmm. He''s fully booked tomorrow afternoon. His next opening''s on the nineteenth.
Woman: Oh. Today''s only the fifth.
Man: I know. Well, one of our other doctors has an opening tomorrow at four. She''s new here, but patients speak very highly of her.
Woman: All right. I''ll take that.
Man: Great. Since you''re seeing a different doctor, please come fifteen minutes early so we can update your patient information.
[[show]]
Open appointments — June 6
| Doctor | Time
| Dr. Castell | 8:30 A.M.
| Dr. Hadley | 4:00 P.M.
| Dr. Lindahl | 9:45 A.M.
| Dr. Varga | 9:15 A.M.'
WHERE exam_id = @toeic_full01_exam_id AND order_no = 62;

UPDATE tp_questions SET body_text = 'Questions 62 through 64 refer to the following conversation and list.

Man: Brookvale Family Clinic. This is Stefan.
Woman: Hi, Stefan. It''s Rosa Delgado. I''ve got my annual checkup with Doctor Simmons tomorrow at ten, but I was just told I have a meeting then. Is there anything later in the day?
Man: Let me look. Hmm. He''s fully booked tomorrow afternoon. His next opening''s on the nineteenth.
Woman: Oh. Today''s only the fifth.
Man: I know. Well, one of our other doctors has an opening tomorrow at four. She''s new here, but patients speak very highly of her.
Woman: All right. I''ll take that.
Man: Great. Since you''re seeing a different doctor, please come fifteen minutes early so we can update your patient information.
[[show]]
Open appointments — June 6
| Doctor | Time
| Dr. Castell | 8:30 A.M.
| Dr. Hadley | 4:00 P.M.
| Dr. Lindahl | 9:45 A.M.
| Dr. Varga | 9:15 A.M.'
WHERE exam_id = @toeic_full01_exam_id AND order_no = 63;

UPDATE tp_questions SET prompt = 'Look at the graphic. Which doctor will the woman see tomorrow?',
  body_text = 'Questions 62 through 64 refer to the following conversation and list.

Man: Brookvale Family Clinic. This is Stefan.
Woman: Hi, Stefan. It''s Rosa Delgado. I''ve got my annual checkup with Doctor Simmons tomorrow at ten, but I was just told I have a meeting then. Is there anything later in the day?
Man: Let me look. Hmm. He''s fully booked tomorrow afternoon. His next opening''s on the nineteenth.
Woman: Oh. Today''s only the fifth.
Man: I know. Well, one of our other doctors has an opening tomorrow at four. She''s new here, but patients speak very highly of her.
Woman: All right. I''ll take that.
Man: Great. Since you''re seeing a different doctor, please come fifteen minutes early so we can update your patient information.
[[show]]
Open appointments — June 6
| Doctor | Time
| Dr. Castell | 8:30 A.M.
| Dr. Hadley | 4:00 P.M.
| Dr. Lindahl | 9:45 A.M.
| Dr. Varga | 9:15 A.M.',
  choices_json = '["Dr. Castell", "Dr. Hadley", "Dr. Lindahl", "Dr. Varga"]',
  explanation = '[Part3-시각 정보(목록)] 정답 (B). 남자가 ''one of our other doctors has an opening tomorrow at four''라고 하자 여자가 ''I''ll take that''이라고 답한다. 목록에서 4:00 P.M.에 비어 있는 의사는 Dr. Hadley다. 대화에는 새 의사의 이름이 나오지 않으므로 목록을 함께 봐야 한다. 오답: (A) 8:30 A.M., (C) 9:45 A.M., (D) 9:15 A.M.는 남자가 말한 시각이 아니고, 모두 오전이어서 여자가 물은 ''later in the day''에도 맞지 않는다.
포인트: 시각 정보 — 대화의 시각(at four) + 목록'
WHERE exam_id = @toeic_full01_exam_id AND order_no = 64;

UPDATE tp_questions SET body_text = 'Questions 65 through 67 refer to the following conversation and list.

Woman: Hi. I''m here to pick up some brochures for Tillman Realty.
Man: Sure, they''re right here. Want to look at one before I box them up?
Woman: Oh, no. The phone number on the back is wrong. The last digit should be a seven, not a one.
Man: Let me pull up your file. Hmm. I''m afraid the number here ends in a one.
Woman: So it''s our mistake. We need these for an open house on Saturday. How soon can you reprint them?
Man: By noon tomorrow. And since you''re a regular, I''ll do the reprint at half price.
Woman: Thank you. I''ll e-mail you the corrected file once I''m back at the office.
[[show]]
Printing services
| Service | Ready for pickup
| Economy | In 5 business days
| Standard | In 3 business days
| Rush | Next day by 5:00 P.M.
| Priority | Next day by noon'
WHERE exam_id = @toeic_full01_exam_id AND order_no = 65;

UPDATE tp_questions SET prompt = 'Look at the graphic. Which service will the woman most likely use?',
  body_text = 'Questions 65 through 67 refer to the following conversation and list.

Woman: Hi. I''m here to pick up some brochures for Tillman Realty.
Man: Sure, they''re right here. Want to look at one before I box them up?
Woman: Oh, no. The phone number on the back is wrong. The last digit should be a seven, not a one.
Man: Let me pull up your file. Hmm. I''m afraid the number here ends in a one.
Woman: So it''s our mistake. We need these for an open house on Saturday. How soon can you reprint them?
Man: By noon tomorrow. And since you''re a regular, I''ll do the reprint at half price.
Woman: Thank you. I''ll e-mail you the corrected file once I''m back at the office.
[[show]]
Printing services
| Service | Ready for pickup
| Economy | In 5 business days
| Standard | In 3 business days
| Rush | Next day by 5:00 P.M.
| Priority | Next day by noon',
  choices_json = '["Economy", "Standard", "Rush", "Priority"]',
  explanation = '[Part3-시각 정보(목록)] 정답 (D). 여자가 ''How soon can you reprint them?''이라고 묻자 남자가 ''By noon tomorrow''라고 답한다. 목록에서 다음 날 정오까지 되는 서비스는 Priority다. 오답: (A) 5영업일, (B) 3영업일이 걸려 내일 받을 수 없다. (C) Rush도 다음 날이지만 오후 5시까지여서 남자가 말한 정오와 다르다.
포인트: 시각 정보 — 대화의 시각(by noon tomorrow) + 목록'
WHERE exam_id = @toeic_full01_exam_id AND order_no = 66;

UPDATE tp_questions SET body_text = 'Questions 65 through 67 refer to the following conversation and list.

Woman: Hi. I''m here to pick up some brochures for Tillman Realty.
Man: Sure, they''re right here. Want to look at one before I box them up?
Woman: Oh, no. The phone number on the back is wrong. The last digit should be a seven, not a one.
Man: Let me pull up your file. Hmm. I''m afraid the number here ends in a one.
Woman: So it''s our mistake. We need these for an open house on Saturday. How soon can you reprint them?
Man: By noon tomorrow. And since you''re a regular, I''ll do the reprint at half price.
Woman: Thank you. I''ll e-mail you the corrected file once I''m back at the office.
[[show]]
Printing services
| Service | Ready for pickup
| Economy | In 5 business days
| Standard | In 3 business days
| Rush | Next day by 5:00 P.M.
| Priority | Next day by noon'
WHERE exam_id = @toeic_full01_exam_id AND order_no = 67;

UPDATE tp_questions SET body_text = 'Questions 68 through 70 refer to the following conversation and schedule.

Man: Sofia, have you signed up for the logistics conference in Northwick? The early registration rate ends tomorrow.
Woman: Yep, did it this morning. I''m still figuring out how to get there, though. Are you driving?
Man: No, I''m taking the express train. The station''s right across from the convention center, so I won''t need a taxi.
Woman: That''s handy. And parking downtown costs a fortune. Which train are you on?
Man: The seven fifteen, Monday morning. It gets in well before the opening speech.
Woman: I''ll book the same one. Do we pay for tickets ourselves?
Man: We do, but hang on to your receipt. Finance will reimburse you within thirty days.
Woman: Good to know.
[[show]]
Monday morning express trains to Northwick
| Departs | Arrives in Northwick
| 6:30 A.M. | 7:50 A.M.
| 7:15 A.M. | 8:35 A.M.
| 8:00 A.M. | 9:20 A.M.
| 8:45 A.M. | 10:05 A.M.'
WHERE exam_id = @toeic_full01_exam_id AND order_no = 68;

UPDATE tp_questions SET prompt = 'Look at the graphic. What time will the man arrive in Northwick?',
  body_text = 'Questions 68 through 70 refer to the following conversation and schedule.

Man: Sofia, have you signed up for the logistics conference in Northwick? The early registration rate ends tomorrow.
Woman: Yep, did it this morning. I''m still figuring out how to get there, though. Are you driving?
Man: No, I''m taking the express train. The station''s right across from the convention center, so I won''t need a taxi.
Woman: That''s handy. And parking downtown costs a fortune. Which train are you on?
Man: The seven fifteen, Monday morning. It gets in well before the opening speech.
Woman: I''ll book the same one. Do we pay for tickets ourselves?
Man: We do, but hang on to your receipt. Finance will reimburse you within thirty days.
Woman: Good to know.
[[show]]
Monday morning express trains to Northwick
| Departs | Arrives in Northwick
| 6:30 A.M. | 7:50 A.M.
| 7:15 A.M. | 8:35 A.M.
| 8:00 A.M. | 9:20 A.M.
| 8:45 A.M. | 10:05 A.M.',
  choices_json = '["7:50 A.M.", "8:35 A.M.", "9:20 A.M.", "10:05 A.M."]',
  explanation = '[Part3-시각 정보(시간표)] 정답 (B). ''Which train are you on?''에 남자가 ''The seven fifteen, Monday morning''이라고 답하므로 7:15 A.M.에 떠나는 열차를 탄다. 시간표에서 이 열차가 Northwick에 닿는 시각은 8:35 A.M.이다. 오답: (A) 6:30 열차, (C) 8:00 열차, (D) 8:45 열차의 도착 시각이다.
포인트: 시각 정보 — 대화의 출발 시각(seven fifteen) + 시간표의 도착 시각'
WHERE exam_id = @toeic_full01_exam_id AND order_no = 69;

UPDATE tp_questions SET body_text = 'Questions 68 through 70 refer to the following conversation and schedule.

Man: Sofia, have you signed up for the logistics conference in Northwick? The early registration rate ends tomorrow.
Woman: Yep, did it this morning. I''m still figuring out how to get there, though. Are you driving?
Man: No, I''m taking the express train. The station''s right across from the convention center, so I won''t need a taxi.
Woman: That''s handy. And parking downtown costs a fortune. Which train are you on?
Man: The seven fifteen, Monday morning. It gets in well before the opening speech.
Woman: I''ll book the same one. Do we pay for tickets ourselves?
Man: We do, but hang on to your receipt. Finance will reimburse you within thirty days.
Woman: Good to know.
[[show]]
Monday morning express trains to Northwick
| Departs | Arrives in Northwick
| 6:30 A.M. | 7:50 A.M.
| 7:15 A.M. | 8:35 A.M.
| 8:00 A.M. | 9:20 A.M.
| 8:45 A.M. | 10:05 A.M.'
WHERE exam_id = @toeic_full01_exam_id AND order_no = 70;

UPDATE tp_questions SET body_text = 'Questions 95 through 97 refer to the following recorded message and floor plan.

Thank you for calling the Wexley Science Center. We''re open Tuesday through Sunday, from ten in the morning until five. We''re closed on Mondays. Please note that our planetarium is currently closed while its sound system is being upgraded. It''ll reopen on the first of May. All of our other exhibits are open as usual. And this month, don''t miss our special exhibit on ocean life. You''ll find it in the hall directly across from the gift shop. It''s included in the regular admission price. To make a group reservation, press one now. To hear this message again, press two.
[[show]]
Wexley Science Center
[[img:/testprep/img/toeic_full01_g_95.svg]]'
WHERE exam_id = @toeic_full01_exam_id AND order_no = 95;

UPDATE tp_questions SET body_text = 'Questions 95 through 97 refer to the following recorded message and floor plan.

Thank you for calling the Wexley Science Center. We''re open Tuesday through Sunday, from ten in the morning until five. We''re closed on Mondays. Please note that our planetarium is currently closed while its sound system is being upgraded. It''ll reopen on the first of May. All of our other exhibits are open as usual. And this month, don''t miss our special exhibit on ocean life. You''ll find it in the hall directly across from the gift shop. It''s included in the regular admission price. To make a group reservation, press one now. To hear this message again, press two.
[[show]]
Wexley Science Center
[[img:/testprep/img/toeic_full01_g_95.svg]]'
WHERE exam_id = @toeic_full01_exam_id AND order_no = 96;

UPDATE tp_questions SET prompt = 'Look at the graphic. Where is the special exhibit being held?',
  body_text = 'Questions 95 through 97 refer to the following recorded message and floor plan.

Thank you for calling the Wexley Science Center. We''re open Tuesday through Sunday, from ten in the morning until five. We''re closed on Mondays. Please note that our planetarium is currently closed while its sound system is being upgraded. It''ll reopen on the first of May. All of our other exhibits are open as usual. And this month, don''t miss our special exhibit on ocean life. You''ll find it in the hall directly across from the gift shop. It''s included in the regular admission price. To make a group reservation, press one now. To hear this message again, press two.
[[show]]
Wexley Science Center
[[img:/testprep/img/toeic_full01_g_95.svg]]',
  choices_json = '["Hall A", "Hall B", "Hall C", "Hall D"]',
  explanation = '[Part4-시각 정보(평면도)] 정답 (A). 특별 전시에 대해 ''You''ll find it in the hall directly across from the gift shop''이라고 했다. 평면도에서 복도를 사이에 두고 Gift Shop 바로 맞은편에 있는 전시실은 Hall A다. 오답: (B) Hall B는 로비 맞은편, (C) Hall C는 카페 맞은편이다. (D) Hall D는 카페 옆이고 맞은편은 Planetarium이다.
포인트: 시각 정보 — 담화의 위치 표현(directly across from) + 평면도'
WHERE exam_id = @toeic_full01_exam_id AND order_no = 97;

UPDATE tp_questions SET prompt = 'Look at the graphic. Who most likely is the speaker?',
  body_text = 'Questions 98 through 100 refer to the following talk and list.

Good morning, and welcome to your first day of customer service training here at Ferncliff Communications. Over the next three days, you''ll learn to answer calls and solve common billing problems. So, this morning, I''ll walk you through our phone system. Then, after lunch, each of you will sit with an experienced representative and listen in on real customer calls. Tomorrow, you''ll start taking calls yourselves. OK, before we begin, please open your folder. Inside, there''s a form asking for your bank details. We need that to set up your salary payments. Please fill it out and hand it to me by the end of the day.
[[show]]
Training sessions
| Trainer | Session
| Ms. Brandt | Billing software
| Mr. Corbin | Company policies
| Ms. Ishida | Phone system
| Mr. Okoro | Handling complaints',
  choices_json = '["Ms. Brandt", "Mr. Corbin", "Ms. Ishida", "Mr. Okoro"]',
  explanation = '[Part4-시각 정보(목록)] 정답 (C). 화자가 ''this morning, I''ll walk you through our phone system''이라고 했으므로 화자는 전화 시스템 교육을 맡은 강사다. 목록에서 Phone system을 맡은 사람은 Ms. Ishida다. 오답: (A) Billing software 담당이다. billing problems는 사흘 동안 배울 내용으로 언급될 뿐이고 화자가 직접 진행한다고 말한 것은 전화 시스템이다. (B) Company policies, (D) Handling complaints는 담화에 나오지 않는다.
포인트: 시각 정보 — 담화의 단서(I''ll walk you through our phone system) + 목록'
WHERE exam_id = @toeic_full01_exam_id AND order_no = 98;

UPDATE tp_questions SET body_text = 'Questions 98 through 100 refer to the following talk and list.

Good morning, and welcome to your first day of customer service training here at Ferncliff Communications. Over the next three days, you''ll learn to answer calls and solve common billing problems. So, this morning, I''ll walk you through our phone system. Then, after lunch, each of you will sit with an experienced representative and listen in on real customer calls. Tomorrow, you''ll start taking calls yourselves. OK, before we begin, please open your folder. Inside, there''s a form asking for your bank details. We need that to set up your salary payments. Please fill it out and hand it to me by the end of the day.
[[show]]
Training sessions
| Trainer | Session
| Ms. Brandt | Billing software
| Mr. Corbin | Company policies
| Ms. Ishida | Phone system
| Mr. Okoro | Handling complaints'
WHERE exam_id = @toeic_full01_exam_id AND order_no = 99;

UPDATE tp_questions SET body_text = 'Questions 98 through 100 refer to the following talk and list.

Good morning, and welcome to your first day of customer service training here at Ferncliff Communications. Over the next three days, you''ll learn to answer calls and solve common billing problems. So, this morning, I''ll walk you through our phone system. Then, after lunch, each of you will sit with an experienced representative and listen in on real customer calls. Tomorrow, you''ll start taking calls yourselves. OK, before we begin, please open your folder. Inside, there''s a form asking for your bank details. We need that to set up your salary payments. Please fill it out and hand it to me by the end of the day.
[[show]]
Training sessions
| Trainer | Session
| Ms. Brandt | Billing software
| Mr. Corbin | Company policies
| Ms. Ishida | Phone system
| Mr. Okoro | Handling complaints'
WHERE exam_id = @toeic_full01_exam_id AND order_no = 100;

SET @sat_prac_01_exam_id = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-SAT-PRAC-01');

UPDATE tp_questions SET prompt = 'Which choice best describes data from the graph that support the team''s conclusion?',
  body_text = '**Bean Seedlings Surviving After Eight Weeks**

[[img:/testprep/img/sat_prac01_q12.svg]]

Biochar, a charcoal-like material made by heating plant waste in the absence of oxygen, is sometimes mixed into farm soil because its porous structure can hold water near plant roots during dry spells. To find out when this property matters most, a team of agronomists grew bean seedlings in pots of soil with and without biochar under two watering schedules, regular and reduced, and recorded the percentage of seedlings that survived for eight weeks. The team concluded that biochar made little difference when water was plentiful but substantially improved survival when water was scarce.',
  explanation = '[Information and Ideas · Command of Evidence (Quantitative)] Choice C is the best answer because the conclusion compares soil with and without biochar under each watering schedule, and this choice makes both comparisons accurately: 91% versus 92% (little difference) and 48% versus 77% (a large improvement). Choice A is incorrect because it compares the two watering schedules within biochar soil only, so it cannot show what biochar changed. Choice B is incorrect because it describes only soil without biochar. Choice D is incorrect because it reverses the two values for reduced watering; the graph shows 48% without biochar and 77% with it. — 물 조건별로 ‘바이오차 유무’를 비교해야 결론이 뒷받침됩니다.',
  ko_help_json = '{"translation": "[막대그래프] 8주 뒤 살아남은 콩 모종(%) — 물주기 방식별, 옅은 막대 = 바이오차 없음, 짙은 막대 = 바이오차 넣음\\n정상 물주기: 바이오차 없음 91% / 넣음 92%\\n물 줄이기: 바이오차 없음 48% / 넣음 77%\\n\\n바이오차는 식물성 폐기물을 산소 없이 가열해 만드는, 숯과 비슷한 물질이다. 구멍이 많은 구조가 가문 동안 식물 뿌리 근처에 물을 붙잡아 둘 수 있어서 농지 흙에 섞기도 한다. 이 성질이 언제 가장 중요한지 알아보려고 한 농학 연구팀은 바이오차를 넣은 흙과 넣지 않은 흙을 담은 화분에서 정상 물주기와 물 줄이기 두 가지 방식으로 콩 모종을 키우고, 8주 동안 살아남은 모종의 비율을 기록했다. 연구팀은 물이 넉넉할 때는 바이오차가 별 차이를 만들지 않았지만 물이 부족할 때는 생존율을 크게 높였다고 결론지었다.\\n\\n질문: 연구팀의 결론을 뒷받침하는 그래프의 자료를 가장 잘 설명한 것은?\\n(A) 바이오차를 넣었을 때 생존율은 정상 물주기에서 92%, 물 줄이기에서 77%로, 두 방식 사이에 15퍼센트포인트 차이가 났다.\\n(B) 바이오차가 없을 때 생존율은 정상 물주기 91%에서 물 줄이기 48%로 떨어졌는데, 이 실험의 두 토양 처리 가운데 가장 큰 하락이었다.\\n(C) 정상 물주기에서는 바이오차 없이 91%, 넣으면 92%였고, 물 줄이기에서는 바이오차 없이 48%, 넣으면 77%였다.\\n(D) 물 줄이기에서 생존율은 바이오차 없이 77%, 넣으면 48%였고, 정상 물주기에서는 두 토양 처리의 생존율이 거의 같았다.", "vocab": [["seedling", "모종, 어린 식물"], ["biochar", "바이오차(숯과 비슷한 토양 개량재)"], ["porous", "구멍이 많은"], ["agronomist", "농학자"], ["watering schedule", "물주기 방식"], ["plentiful", "넉넉한"], ["scarce", "부족한"], ["substantially", "크게, 상당히"], ["percentage point", "퍼센트포인트"]], "solution": "정답 (C). 결론은 두 부분으로 나뉩니다. ① 물이 넉넉할 때는 차이가 거의 없다 → 정상 물주기의 두 막대 91% 대 92%. ② 물이 부족할 때는 크게 높인다 → 물 줄이기의 두 막대 48% 대 77%. 두 비교를 그래프의 숫자 그대로 담은 것은 (C)뿐입니다. (A)와 (B)는 그래프의 숫자는 맞지만 같은 토양 처리 안에서 물주기 방식끼리 비교한 것이라 바이오차의 효과를 보여 주지 못합니다. (D)는 비교 방향은 맞지만 물 줄이기의 두 숫자를 서로 바꿔 썼습니다.", "strategy": "【근거 고르기 — 자료(Command of Evidence: Quantitative)】\\n1. 그래프는 제목 → 가로축·세로축 → 범례(막대 색이 무엇인지) 순서로 먼저 읽으세요.\\n2. 결론에서 ‘무엇과 무엇을 비교하는지’를 찾으세요. 그래프에서는 같은 조건 안에 나란히 선 두 막대의 비교입니다.\\n3. 결론에 조건이 둘이면 두 조건을 모두 다룬 선택지만 남기고, 숫자가 맞더라도 비교 방향이 결론과 다른 선택지는 지웁니다."}'
WHERE exam_id = @sat_prac_01_exam_id AND order_no = 12;

UPDATE tp_questions SET prompt = 'The bar graph shows the number of trees that volunteers planted in a park on each of 5 days.

[[img:/testprep/img/sat_prac01_q29.svg]]

What is the mean number of trees planted per day for these 5 days?',
  choices_json = '["36", "40", "45", "50"]',
  explanation = '[Problem-Solving and Data Analysis · One-variable data: distributions and measures of center and spread] Choice A is the best answer because the heights of the five bars are 30, 40, 20, 50, and 40, so the total is 180 trees, and 180 ÷ 5 days = 36. Choice B is incorrect because 40 is the median (and the most frequent value) of the five numbers, not the mean. Choice C is incorrect because 45 results from dividing the total of 180 by 4 rather than by the 5 days. Choice D is incorrect because 50 is the greatest number of trees planted on a single day. — 30 + 40 + 20 + 50 + 40 = 180, 180 ÷ 5 = 36.',
  ko_help_json = '{"translation": "막대그래프는 자원봉사자들이 5일 동안 날마다 공원에 심은 나무 수를 나타낸 것이다.\\n[막대그래프] 가로축: 날(Day 1~5), 세로축: 심은 나무 수(0~60, 눈금 10)\\n\\n이 5일 동안 하루에 심은 나무 수의 평균은?", "vocab": [["bar graph", "막대그래프"], ["mean", "평균"], ["per day", "하루당"], ["volunteer", "자원봉사자"], ["plant (v.)", "심다"]], "solution": "정답 (A) 36. 막대의 높이를 눈금선에서 읽으면 1일 30, 2일 40, 3일 20, 4일 50, 5일 40입니다. 합은 30 + 40 + 20 + 50 + 40 = 180이고 날 수는 5이므로 평균은 180 ÷ 5 = 36입니다. (B) 40은 크기순으로 늘어놓았을 때 가운데 값(중앙값)이자 가장 자주 나온 값, (C) 45는 180을 4로 나눈 값, (D) 50은 가장 큰 값입니다.", "strategy": "【막대그래프에서 평균 구하기】\\n1. 세로축 눈금 한 칸이 얼마인지 먼저 확인하고, 막대마다 값을 적어 두세요.\\n2. 평균 = 값의 합 ÷ 값의 개수입니다. 나누는 수는 막대의 개수입니다.\\n3. 평균(mean)·중앙값(median)·최댓값을 구별하세요. 선택지에는 이 값들이 함께 나옵니다."}'
WHERE exam_id = @sat_prac_01_exam_id AND order_no = 29;

UPDATE tp_questions SET prompt = '[[img:/testprep/img/sat_prac01_q33.svg]]

Note: Figure not drawn to scale.

In the figure shown, point *C* lies on line segment *AD*. What is the value of *x*?',
  choices_json = '["67", "115", "132", "163"]',
  explanation = '[Geometry and Trigonometry · Lines, angles, and triangles] Choice A is the best answer because angles *ACB* and *BCD* lie along a straight line, so angle *ACB* measures 180° − 115° = 65°, and the angles of triangle *ABC* add up to 180°, so *x* = 180 − 48 − 65 = 67. Choice B is incorrect because 115 is the measure of angle *BCD*, which lies outside the triangle. Choice C is incorrect because 132 is 180 − 48, which subtracts only the angle at *A*. Choice D is incorrect because 163 is the sum of the two labeled angles, 48 + 115. — 각 ACB = 180 − 115 = 65, x = 180 − 48 − 65 = 67.',
  ko_help_json = '{"translation": "[그림] 삼각형 ABC. 각 A = 48°, 각 B = x°. 변 AC를 C 너머로 늘인 선 위에 점 D가 있고 각 BCD = 115°.\\n주의: 그림은 실제 비율과 다를 수 있다.\\n\\n그림에서 점 C는 선분 AD 위에 있다. x의 값은?", "vocab": [["line segment", "선분"], ["lie on", "~ 위에 있다"], ["Figure not drawn to scale", "그림은 실제 비율대로 그린 것이 아님"], ["measure of an angle", "각의 크기"], ["exterior angle", "외각"]], "solution": "정답 (A) 67. 점 C가 선분 AD 위에 있으므로 각 ACB와 각 BCD는 일직선(180°)을 이룹니다. 각 ACB = 180° − 115° = 65°. 삼각형 세 내각의 합이 180°이므로 x = 180 − 48 − 65 = 67입니다. (외각의 성질로 한 번에: 외각 115° = 이웃하지 않은 두 내각의 합 48° + x°, x = 67.) (B) 115는 삼각형 바깥 각의 크기, (C) 132는 180 − 48, (D) 163은 48 + 115입니다.", "strategy": "【삼각형과 일직선의 각】\\n1. 그림에 적힌 각이 삼각형 안의 각인지 바깥의 각인지 먼저 구별하세요.\\n2. 일직선 위의 두 각은 합이 180°, 삼각형 세 내각의 합도 180°입니다. 이 두 가지로 모르는 각을 차례로 구합니다.\\n3. ‘Figure not drawn to scale’이면 눈대중으로 재지 말고 적힌 숫자만 쓰세요."}'
WHERE exam_id = @sat_prac_01_exam_id AND order_no = 33;

UPDATE tp_questions SET prompt = '[[img:/testprep/img/sat_prac01_q39.svg]]

The graph of line *k* is shown in the *xy*-plane. Which equation represents line *k*?',
  choices_json = '["3*x* − 2*y* = 2", "2*x* − 3*y* = 3", "3*x* + 2*y* = −2", "3*x* − 2*y* = −2"]',
  explanation = '[Algebra · Linear equations in two variables] Choice A is the best answer because the line passes through (0, −1) and (2, 2), so its slope is (2 − (−1)) ÷ (2 − 0) = \\frac{3}{2} and its *y*-intercept is −1. The equation *y* = \\frac{3}{2}*x* − 1 can be rewritten as 3*x* − 2*y* = 2. Choice B is incorrect because it is equivalent to *y* = \\frac{2}{3}*x* − 1, which has the reciprocal slope; that line passes through (0, −1) but not (2, 2). Choice C is incorrect because it is equivalent to *y* = −\\frac{3}{2}*x* − 1, whose slope has the wrong sign. Choice D is incorrect because it is equivalent to *y* = \\frac{3}{2}*x* + 1, which crosses the *y*-axis at 1 rather than −1. — (0, −1)과 (2, 2)를 지남: 기울기 3/2, y절편 −1 → 3x − 2y = 2.',
  ko_help_json = '{"translation": "[그래프] xy평면(가로·세로 −6~6, 눈금 1)에 오른쪽 위로 올라가는 직선 k가 그려져 있다.\\n\\n직선 k의 그래프가 xy평면에 그려져 있다. 직선 k를 나타내는 식은?", "vocab": [["graph", "그래프"], ["xy-plane", "xy평면(좌표평면)"], ["represent", "나타내다"], ["slope", "기울기"], ["y-intercept", "y절편"]], "solution": "정답 (A) 3x − 2y = 2. 직선이 격자점 (0, −1)과 (2, 2)를 지납니다. 기울기 = (2 − (−1)) ÷ (2 − 0) = 3/2, y절편 = −1이므로 y = (3/2)x − 1입니다. 양변에 2를 곱해 옮기면 3x − 2y = 2입니다. 확인: (2, 2)를 넣으면 6 − 4 = 2. (B)는 y = (2/3)x − 1(기울기를 뒤집음), (C)는 y = −(3/2)x − 1(기울기 부호가 반대), (D)는 y = (3/2)x + 1(y절편 부호가 반대)입니다.", "strategy": "【그래프에서 직선의 식 찾기】\\n1. 직선이 정확히 지나는 격자점 두 개를 고르세요. y축과 만나는 점이 격자점이면 그것이 y절편입니다.\\n2. 두 점으로 기울기(세로 변화 ÷ 가로 변화)를 구하고, 직선이 오른쪽 위로 가는지 아래로 가는지로 부호를 확인합니다.\\n3. 선택지가 ax + by = c 꼴이면 고른 두 점을 넣어 ‘둘 다’ 성립하는 식을 찾는 것이 가장 확실합니다."}'
WHERE exam_id = @sat_prac_01_exam_id AND order_no = 39;
