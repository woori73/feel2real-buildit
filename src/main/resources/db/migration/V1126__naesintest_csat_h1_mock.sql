-- 내신 실전 연습(/naesintest) — 고1 수능 영어 독해 변형 모의고사 1~10회(과정 페이지 /naesintest/school/csat-h1).
-- 원본: 고1 사설 수능 영어 모의고사 10회(이투스·종로·더 모먼트) 18~45번. 지문과 선택지를 수능 유형 논리에 맞춰 바꾸고,
-- 해설은 유형 전략·풀이 과정·정답 근거·오답 분석·어휘로 새로 썼다. 두 차례 감수를 반영했다.
-- 회차마다 28문항, 배점 합계 62점(3점 6문항: 21·29·33·34·37·39번), 시험 시간 45분(NaesinTestPageController.CSAT_H1_MINUTES).
-- order_no는 시험지 번호(18~45)를 그대로 쓴다. 문항 그림: static/naesin/csat-h1-mock/rNN/. 41~42·43~45번 공통 지문은 body_text 그림.
-- 1회: 2022년 고1 윈터스쿨 이투스 원본(고1 이투스 22 윈터)
INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-CSH1-MOCK-R01', 'NAESIN', '고1 수능 영어 독해 모의고사 1회 · 18~45번 변형문제 1회', '2022년 고1 윈터스쿨 이투스 원본 · 28문항 · 45분 · 62점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 풀이 전략 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @csh1_r01 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-CSH1-MOCK-R01');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글의 목적 (2점)
[유형 전략] 글의 목적은 대개 글 후반부의 요청·안내 문장(please, we ask, I would like to, I am writing to)에 드러납니다. 앞부분의 인사·배경 설명에 끌려가지 말고, 필자가 독자에게 결국 무엇을 해 달라고 하는지 한 문장으로 정리한 뒤 선택지와 맞춰 봅니다.
[풀이 과정]
1) 인사(Welcome to the new school year)와 담임 소개는 배경입니다. 이어지는 We would like to let you know about one change에서 글의 목적이 ‘변경 사항 안내’임을 예고합니다.
2) 무엇이 바뀌는지 확인합니다: we will no longer send printed notices home → all notices will be delivered through the school''s mobile app. 종이 통신문 대신 앱으로 보낸다는 내용입니다.
3) 마지막 부분의 사과·협조 요청(we appreciate your patience)은 변경에 따른 인사말일 뿐 새로운 요청이 아닙니다.
[정답 근거] printed notices → the school''s mobile app으로 통신문을 보내는 방법이 바뀐다는 것을 알리는 글이므로 ①이 정답입니다.
[오답 분석]
② 상담 일정은 언급되지 않습니다.
③ 웹사이트에는 통신문 사본을 올린다고만 했을 뿐, 개편이나 의견 요청은 없습니다.
④ 종이 사용을 줄이는 정책(policy of reducing paper use)은 변경의 이유일 뿐, 캠페인 참여를 독려하는 글이 아닙니다. 지문 단어를 활용한 매력적 오답입니다.
⑤ 대화방은 등장하지 않으며, 앱 이용 규칙도 설명하지 않습니다.
[어휘] adopt a policy 정책을 채택하다 · deliver 전달하다 · get used to ~에 익숙해지다 · adjust 적응하다', 18, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 심경 변화 (2점)
[유형 전략] 심경 변화는 처음 상황과 전환 이후 상황을 나누어 읽습니다. 사건이 바뀌는 지점(when, suddenly, but, then)을 찾고, 그 앞뒤에서 인물의 감정을 드러내는 표현(형용사, 신체 반응, 혼잣말)을 각각 하나씩 골라 두 감정의 짝을 확인합니다.
[풀이 과정]
1) 전반부: the tension ... finally leaves my body, even better than the photos, I happily put on my swimsuit → 긴장이 풀리고 기분이 좋은 상태(delighted)입니다.
2) 전환점: my phone buzzes with a message from Rachel. 친구가 오늘 저녁에 온다는 메시지를 받습니다.
3) 후반부: my stomach drops, my hands start to shake, it completely slipped my mind, I haven''t booked a table → 약속을 잊어 당황하고 허둥대는 상태(panicked)입니다.
[정답 근거] 기쁨(delighted)에서 당황(panicked)으로 바뀌므로 ④가 정답입니다.
[오답 분석]
① 처음부터 지루한(bored) 모습은 없고, 나중에도 들뜬 것이 아니라 당황합니다.
② 순서가 반대입니다. 처음에 긴장이 풀리고(relieved에 가까움) 나중에 불안해집니다.
③ 처음 감정이 호기심이 아니며, 나중 감정도 실망보다는 당황입니다.
⑤ 감사나 질투를 드러내는 표현은 없습니다.
[어휘] tension 긴장 · stuff 쑤셔 넣다 · buzz (휴대 전화가) 진동하다 · one''s stomach drops 가슴이 철렁하다 · slip one''s mind 깜박 잊다', 19, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 필자의 주장 (2점)
[유형 전략] 필자의 주장은 should, must, need to, it is important to, 명령문처럼 당위를 나타내는 문장에 담깁니다. 예시나 일화는 주장을 뒷받침하는 재료이므로, 일화가 끝난 뒤 필자가 일반화하는 문장을 찾아 그 내용과 같은 선택지를 고릅니다.
[풀이 과정]
1) 도입: 사람들은 남이 도와주지 않거나 도울 능력이 없다고 지레짐작해 도움을 청하지 않는다(we assume that others are either unwilling or unable)는 문제 상황을 제시합니다.
2) 필자의 일화 속 조언이 곧 주장입니다: Don''t decide for them what they can or cannot do. Simply say what you need. 명령문 두 개가 핵심입니다.
3) 마지막 문장(seldom disappointed)은 그 조언을 따랐을 때의 좋은 결과로 주장을 뒷받침합니다.
[정답 근거] 상대의 능력을 미리 판단하지 말고 필요한 것을 요청하라는 명령문이 주장이므로 ①이 정답입니다.
[오답 분석]
② networking workshops는 필자가 경험한 장소일 뿐, 모임 기획 방법을 말하지 않습니다.
③ 감사 표현은 언급되지 않습니다.
④ 글의 주장과 반대 방향입니다.
⑤ 거절당할까 두려워한다(worry about being turned down)는 표현을 이용한 오답으로, 거절하는 방법은 다루지 않습니다.
[어휘] hold back from ~을 망설이다 · turn down 거절하다 · in advance 미리 · self-defeating 자멸적인, 스스로를 막는 · seldom 좀처럼 ~ 않는', 20, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 밑줄 함의 (3점)
[유형 전략] 밑줄 친 표현은 비유이므로 글자 그대로 해석하지 않습니다. 밑줄 앞뒤에서 같은 대상을 설명하는 문장을 찾아, 비유 속 단어(밑줄의 핵심 명사·동사)가 글에서 무엇에 대응하는지 짝지은 뒤, 그 의미를 풀어 쓴 선택지를 고릅니다.
[풀이 과정]
1) 비유어의 짝을 정합니다. leash(개 줄) = 이끌고 훈련하는 도구, sword(칼) = 싸우고 공격하는 도구입니다.
2) 바로 앞 문장 What it needs is patient guidance, not mockery or attack.이 밑줄을 그대로 풀어 쓴 문장입니다. leash ↔ patient guidance, sword ↔ mockery or attack.
3) 앞부분의 our ego is not a beast waiting to attack(자아는 공격하려는 짐승이 아니다)도 자아를 적으로 여기지 말라는 흐름을 뒷받침합니다.
[정답 근거] 자아를 적처럼 공격하지 말고 인내심 있게 이끌라는 뜻이므로 ③이 정답입니다.
[오답 분석]
① leash에서 ‘통제’를 떠올리게 하는 오답입니다. 글은 엄격한 통제가 아니라 인내심 있는 안내(patient guidance)를 말합니다.
② 다른 사람의 자아는 다루지 않습니다.
④ 무시하면 문제가 생긴다는 내용과 반대입니다.
⑤ Left alone, it will wander into dark corners(내버려 두면 어두운 곳을 헤맨다)와 반대입니다.
[어휘] any more than ~이 아닌 것과 마찬가지로 · take charge of ~을 책임지다 · twist 왜곡하다 · mockery 조롱 · guidance 안내, 지도', 21, 3),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글의 요지 (2점)
[유형 전략] 요지는 글 전체가 말하려는 한 가지 핵심입니다. 도입의 질문·통념, 대조(however, but), 결론(therefore, in short)을 확인하고, 세부 예시 하나만 다룬 선택지나 지문 단어를 섞어 만든 그럴듯한 선택지는 지웁니다.
[풀이 과정]
1) 첫 문장의 질문(언제 자신이 만든 것에 자부심을 느끼는가?)이 글의 화제입니다. 답은 글 끝에서 찾습니다.
2) 대조되는 두 예: 냉동 피자(no special skill and very little effort → 성취라 부르기 어렵다) vs 손으로 만든 라자냐(skill, time, and attention → truly yours).
3) 결론: the more of ourselves we put into them, the more justified our sense of ownership and pride becomes. ‘the 비교급, the 비교급’ 구문이 요지를 담고 있습니다.
[정답 근거] 들인 기술·노력이 많을수록 자부심이 커진다는 결론 문장과 같은 ③이 정답입니다.
[오답 분석]
① 반복 연습으로 실력이 는다는 내용은 없습니다.
② 간편식의 장점은 다루지 않습니다. 오히려 성취감을 주지 못하는 예로 쓰였습니다.
④ 친구에게 자랑하지 않는다(few people would boast)는 표현을 이용한 오답입니다. 가치를 남의 평가로 정한다는 말은 없습니다.
⑤ 가족, 맛과 정성의 비교는 언급되지 않습니다.
[어휘] earn the right to ~할 자격을 얻다 · boast 자랑하다 · extreme 극단 · ownership 소유 의식 · justified 정당한', 22, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글의 주제 (2점)
[유형 전략] 주제는 글의 핵심 소재와 그에 대한 필자의 관점을 함께 담은 영어 구로 나타납니다. 반복되는 핵심어를 찾고, 글이 그 소재의 무엇(원인, 효과, 과정, 중요성, 한계)을 다루는지 확인하여 범위가 너무 넓거나 좁은 선택지를 지웁니다.
[풀이 과정]
1) 첫 문장 Babies become interested in books far earlier than most people expect.이 소재(아기와 책)를 제시합니다.
2) 이후 시간 순서 표현이 이어집니다: Within days of being born → By the time they are three or four months old → As they gain more control → Over time. 아기가 책에 익숙해지는 ‘과정’을 단계별로 설명하는 글입니다.
3) 마지막 문장은 그 과정이 언어 발달로 이어진다는 결과입니다.
[정답 근거] 출생 직후부터 책을 장난감처럼 다루고 언어망을 형성하기까지의 과정을 다루므로 ⑤가 정답입니다.
[오답 분석]
① 아버지의 역할은 언급되지 않습니다.
② 대비가 강한 색(Bold, contrasting colors)이 언급되지만, 신생아용 책의 종류를 추천하는 글이 아닙니다.
③ 부모의 실수는 다루지 않습니다.
④ 책을 장난감처럼 다룬다(just as they handle toys)는 세부 내용을 비튼 오답입니다. 장난감을 더 좋아한다는 말은 없습니다.
[어휘] infant 유아 · grab 움켜잡다 · flip over 뒤집다 · gaze at ~을 응시하다 · contrasting 대비를 이루는', 23, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 제목 (2점)
[유형 전략] 제목은 주제를 압축하거나 비유·의문문으로 바꾼 표현입니다. 먼저 글의 주제를 우리말로 정리한 뒤, 그 핵심을 담은 선택지를 고릅니다. 지문에 나온 단어가 그대로 쓰였지만 글의 일부만 다루거나 반대 방향을 말하는 선택지에 주의합니다.
[풀이 과정]
1) 첫 문장은 버섯이 독립된 계(kingdom)에 속한다는 도입입니다. 핵심은 두 번째 문장 Many species of fungi form close partnerships with trees.입니다.
2) 이후 균류가 나무에 주는 도움이 차례로 나옵니다: 뿌리 표면적을 넓혀 물·양분 흡수를 돕고(expand the surface area), 중금속을 막고(keeping harmful heavy metals away), 공격적인 균으로부터 보호합니다(a protective wall).
3) 따라서 글의 중심은 ‘나무를 돕고 지켜 주는 동반자로서의 균류’입니다.
[정답 근거] 나무의 동반자이자 보호자로서 균류의 역할을 담은 ④가 정답입니다.
[오답 분석]
① 균류를 숲의 적으로 표현해 글과 반대입니다.
② 버섯의 독성은 다루지 않습니다.
③ 도움의 방향이 반대입니다. 나무가 균류를 돕는 것이 아니라 균류가 나무를 돕습니다.
⑤ 첫 문장의 분류 이야기만 다룬 오답이며, 논쟁이 계속된다는 말도 없습니다.
[어휘] organism 생물 · kingdom (생물 분류의) 계 · partnership 협력 관계 · surface area 표면적 · aggressive 공격적인', 24, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 도표 불일치 (2점)
[유형 전략] 도표 문항은 선택지 문장을 하나씩 도표 수치와 맞춰 봅니다. 비교 표현(more than, twice, the lowest), 순위, 차이 계산이 함정이 되는 경우가 많으므로, 문장에 나온 항목·연도·수치를 도표에서 손가락으로 짚어 가며 확인합니다.
[풀이 과정]
1) ① easy to use의 Completely Agree는 74%로 70%보다 높습니다. (일치)
2) ② on time: Completely 58%, Somewhat 27%. 27의 두 배는 54이므로 58은 두 배보다 큽니다. (일치)
3) ③ Completely Agree가 가장 낮은 항목은 refunds(47%)가 아니라 customer service(41%)입니다. (불일치)
4) ④ customer service: 41 + 33 = 74%로 75%보다 낮습니다. (일치)
5) ⑤ Somewhat Agree 수치 27·15·33·31 중 가장 낮은 것은 easy to use의 15%입니다. (일치)
[정답 근거] 최저 항목을 바꿔 쓴 ③이 도표와 일치하지 않습니다.
[오답 분석]
비교 표현(more than twice, the lowest)과 합산(combined)이 나오면 반드시 계산해서 확인합니다. 이 문항은 최저 항목을 다른 항목과 바꾸어 함정을 만들었습니다.
[어휘] respond to ~에 응답하다 · statement 진술 · combined 합한 · handle 처리하다', 25, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 불일치 (2점)
[유형 전략] 인물 내용 일치 문항은 선택지 순서가 대체로 글의 순서와 같습니다. 선택지의 핵심어(연도, 장소, 숫자, 고유명사)를 먼저 보고 지문에서 해당 부분을 찾아 정확히 대조합니다. 일부만 바꾼 진술(시기, 횟수, 주체)에 주의합니다.
[풀이 과정]
1) 선택지 순서와 지문 순서가 같습니다. ①은 When she was seven 문장, ②는 At sixteen 문장, ③은 Yale 문장, ④는 World War II 문장, ⑤는 마지막 문장과 대응합니다.
2) ②의 근거: At sixteen, she applied for early admission to Vassar College but was turned down; she was accepted the following year. 16세에는 거절당했고 이듬해에 입학했습니다.
[정답 근거] 16세의 조기 입학 신청은 거절당했으므로(was turned down) ‘조기 입학했다’는 ②가 일치하지 않습니다.
[오답 분석]
① took apart ... dismantled seven of them과 일치합니다.
③ a master''s degree in 1930 and a doctorate in 1934와 일치합니다.
④ she tried to join the Navy, but she was rejected at first와 일치합니다.
⑤ In 1949, she joined the team developing the UNIVAC과 일치합니다.
[어휘] take apart / dismantle 분해하다 · admission 입학 · turn down 거절하다 · commercially 상업적으로', 26, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 안내문 불일치 (2점)
[유형 전략] 안내문은 항목별로 정보가 정리되어 있으므로 선택지의 핵심어(날짜, 시간, 대상, 비용, 신청 방법)로 해당 항목을 바로 찾습니다. 주의 사항·예외 조건(only, not, must)이 함정으로 자주 쓰입니다.
[풀이 과정]
1) 선택지의 핵심어로 항목을 찾습니다: ① 대상 → 첫 줄, ② Coaches, ③ Schedule, ④ Fees, ⑤ Special Offer.
2) ① This program is open to both children and adults. 어린이와 성인 모두 참여할 수 있습니다.
[정답 근거] 성인도 참여할 수 있으므로 ①이 일치하지 않습니다.
[오답 분석]
② Former national team players와 일치합니다.
③ closed on Sundays와 일치합니다.
④ $330 for three months와 일치합니다.
⑤ Your first three days of practice are free.와 일치합니다.
[어휘] open to ~에게 개방된 · former 이전의 · footwork 발놀림 · analysis 분석', 27, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 안내문 일치 (2점)
[유형 전략] 안내문 일치 문항은 정답이 하나뿐이므로 선택지를 하나씩 대조하며 틀린 것을 지웁니다. 날짜·요일, 연령, 요금, 포함 여부, 사전 등록 여부처럼 숫자와 조건이 바뀐 부분을 꼼꼼히 확인합니다.
[풀이 과정]
1) 일치하는 것을 고르는 문항이므로 선택지를 하나씩 지워 갑니다.
2) ② What to Donate에 New or gently used books라고 했으므로 새 책뿐 아니라 깨끗하게 사용한 책도 받습니다.
[정답 근거] ②가 안내문과 일치합니다.
[오답 분석]
① Saturday, December 13 하루만 진행합니다.
③ readers aged 3 to 15용 책만 받습니다.
④ 경품 추첨은 다섯 권 이상(five or more books) 기부한 사람만 참여할 수 있습니다.
⑤ 단체는 미리 전화해야(call us in advance) 합니다.
[어휘] book drive 도서 기부 행사 · gently used 깨끗하게 사용한 · donor 기부자 · in advance 미리', 28, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법 (3점)
[유형 전략] 어법 문항은 밑줄마다 확인할 문법 포인트를 먼저 정합니다(동사 자리·수일치, 관계사와 접속사, 분사·to부정사, 병렬 구조, 대명사, 형용사·부사). 특히 문장의 주어와 본동사를 먼저 찾으면 수일치와 준동사 문제를 빠르게 판단할 수 있습니다.
[풀이 과정]
1) ② 관계대명사절의 동사는 선행사에 수를 맞춥니다. that의 선행사는 problems(복수)이므로 has가 아니라 have가 와야 합니다.
2) ① students sit ... facing the teacher: 주어 students가 선생님을 ‘향하는’ 능동 관계이므로 현재분사 facing이 맞습니다.
3) ③ end up + -ing(결국 ~하게 되다)이므로 answering이 맞습니다.
4) ④ 전치사 in의 목적어로 쓰인 의문사절(누가 질문을 했는지)이며, who가 절 안에서 주어 역할을 하므로 맞습니다.
5) ⑤ 교사는 84개를 물었다, 반면 학생은 2개만 물었다는 대조이므로 접속사 while이 맞습니다.
[정답 근거] 선행사 problems와 수가 맞지 않는 ② has가 틀렸습니다(→ have).
어법 포인트: 관계대명사절의 수일치 — 관계사 바로 앞의 명사(problems)를 찾아 동사의 수를 결정합니다. 중간에 수식어가 있어도 선행사를 끝까지 추적해야 합니다.
[어휘] neat 가지런한 · reasoning 추론 · turn out to be ~로 드러나다 · imbalance 불균형 · pose a question 질문을 하다', 29, 3),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어휘 (2점)
[유형 전략] 어휘 문항은 글의 흐름(원인과 결과, 대조, 예시)을 먼저 잡고, 밑줄 친 낱말이 그 흐름과 반대 방향인지 확인합니다. 밑줄 낱말을 반의어로 바꿔 넣었을 때 문맥이 자연스러워지면 그것이 정답입니다.
[풀이 과정]
1) 글의 흐름: 광고·영화·부모 모두 음악으로 감정을 움직인다(make use of music''s power to stir up emotions).
2) 마지막 문장은 부모가 노래로 아기를 진정시킨다는 예입니다. 아기를 달래려면 울게 만든 것에서 관심을 ‘돌려야’ 하므로, 그 대상에 관심을 ‘끌어당긴다(attract)’는 말은 흐름과 반대입니다.
3) ⑤ attract → distract (their attention from whatever has made them cry)로 바꾸면 자연스럽습니다.
[정답 근거] 진정시키기 위한 노래의 목적과 반대되는 ⑤ attract가 적절하지 않습니다.
[오답 분석]
① stir up emotions 감정을 불러일으키다 — 글 전체의 주제와 맞습니다.
② 광고는 제품을 경쟁 제품보다 더 현대적으로 보이게 합니다.
③ 음악이 없으면 ‘분명하지 않을’ 장면에 감정의 방향을 알려 준다는 뜻으로 맞습니다.
④ 우리는 음악의 힘을 저항 없이 받아들인다(without resisting)와 맞습니다.
[어휘] stir up 불러일으키다 · rival 경쟁자 · heighten 고조시키다 · resist 저항하다 · distract 주의를 딴 데로 돌리다', 30, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 첫 문장이 주제문입니다: 가장 중요한 일에서 오히려 실패한다(struggle or even fail at the very tasks that matter most).
2) 빈칸 문장: 연습 시험보다 실제 시험 점수가 낮은 이유 = ‘~하고 싶은 압박감’. 실제 시험은 결과가 중요한 순간이므로, 그 순간에 잘하고 싶은 압박이 점수를 떨어뜨린 것입니다.
3) 이어지는 발표 예시의 The strong desire to impress others can make us stumble.도 ‘잘 보이고 싶은 욕구가 실수를 부른다’는 같은 논리입니다.
[정답 근거] 중요한 순간에 잘하고 싶은 압박을 뜻하는 ③이 정답입니다.
[오답 분석]
① 시험을 일찍 끝내려는 압박은 근거가 없습니다.
② 연습은 혼자 할 때 오히려 잘한다는 내용과 관계없습니다.
④ 발표 예시에서 떠올리기 쉬운 오답이지만, 빈칸은 시험 점수에 관한 문장이고 남과의 비교도 없습니다.
⑤ 모든 질문에 대비하려는 압박은 언급되지 않습니다.
[어휘] matter 중요하다 · rehearse 예행연습을 하다 · fall apart 엉망이 되다 · stumble 실수하다, 휘청거리다 · rank alongside ~와 나란히 놓이다', 31, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) Yet이 흐름을 바꿉니다. 앞에서는 사람들이 부정적 감정을 피하거나 묻어 둔다고 했고, 빈칸 문장은 그 부정적 감정이 실제로는 ‘무엇’인지를 말합니다.
2) 빈칸 뒤 문장이 결정적 단서입니다: These unwelcome experiences often end up shaping some of the most meaningful and inspiring moments of our lives.
3) 마지막 문장 When we learn to use negative emotions ... we prepare ourselves for success.도 부정적 감정이 성장의 밑거름이라는 뜻입니다.
[정답 근거] 지혜와 성숙을 추구할 때 부정적 감정이 ‘꼭 필요한 성장의 원천’이 된다는 ⑤가 정답입니다.
[오답 분석]
① 오래 남지 않는다는 말은 shaping ... moments of our lives와 맞지 않습니다.
② 약점의 신호라는 것은 글의 관점과 반대입니다.
③ 완전히 없애야 할 장애물이라는 것도 반대입니다.
④ 잊어야 할 기억이라는 것은 사람들이 흔히 하는 회피(avoid or bury)와 같은 방향으로, 필자의 관점과 다릅니다.
[어휘] whole 온전한 · thrill 짜릿함 · triumph 승리 · bury 묻어 두다 · maturity 성숙 · unwelcome 반갑지 않은', 32, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 핵심 논리: 기술 부족이 원인이 아닌 문제에 교육을 하는 것은 낭비다(If a performance problem is not caused by a lack of skill ... training ... is a waste).
2) 대부분의 문제는 진짜 기술 격차(a genuine skill gap)가 아니며, 예외는 신입이거나 처음 하는 일을 맡은 사람입니다. 즉, 교육이 필요한 경우는 실제로 기술이 부족할 때뿐입니다.
3) 빈칸 문장의 only when은 ‘교육이 효과가 있는 유일한 조건’을 묻습니다. 앞의 논리를 뒤집지 않고 그대로 정리하면 됩니다.
[정답 근거] 실제로 존재하는 기술 격차를 메우는 데 맞춰질 때만 교육이 효과가 있다는 ④가 정답입니다.
[오답 분석]
① 이미 잘하는 직원에 대한 보상은 다루지 않습니다.
② 경력 목표 설정은 언급되지 않습니다.
③ 모두에게 같은 기술을 익히게 하는 것은 이미 아는 것을 가르치는 낭비가 될 수 있어 글의 논지와 반대입니다.
⑤ motivation, the way the work is organized를 떠올리게 하지만 성격 이해는 언급되지 않습니다.
[어휘] genuine 진짜의 · skill gap 기술 격차 · sign up for ~에 등록하다 · lie elsewhere 다른 데 있다 · pay off 성과를 거두다', 33, 3),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 빈칸 문장은 You might think that ~(~라고 생각할지도 모른다) 구조로, 필자가 반박하려는 ‘잘못된 생각’을 제시합니다. 따라서 빈칸에는 필자의 결론과 반대되는 내용이 들어갑니다.
2) 그런 생각을 하게 만드는 말: "I don''t believe the Earth is round; I know it is!" → 마치 ‘알면 믿는 것은 아니다’라는 뜻처럼 들립니다.
3) 필자의 반박: 이 말을 믿지 않는다는 증거로 보면 안 된다. 그 말은 단지 믿음에 더해 강한 근거가 있다는 뜻이다. 즉, 아는 것에는 믿는 것이 포함됩니다.
[정답 근거] 필자가 반박하는 오해는 ‘무언가를 아는 것은 그것을 믿는 것과 관계없다’이므로 ②가 정답입니다.
[오답 분석]
① 믿음이 지식보다 강하다는 비교는 없습니다.
③ 논쟁 상황이 나오지만 의견을 바꾸는지는 다루지 않습니다.
④ 행동(you act in exactly the same way)이 언급되지만, 행동이 말보다 믿음을 잘 드러낸다는 것은 근거의 일부일 뿐 빈칸의 오해가 아닙니다. 이 내용은 오히려 필자의 반박에 쓰인 근거입니다.
⑤ 과학적 사실의 증명 가능성은 다루지 않습니다.
[어휘] insist 주장하다 · exclaim 외치다 · remark 말, 발언 · stress 강조하다 · merely 단지 · signal 알리다', 34, 3),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 무관한 문장 (2점)
[유형 전략] 무관한 문장 문항은 첫 문장(주제문)이 제시한 소재와 방향을 확인한 뒤 각 문장이 그 방향을 이어 가는지 봅니다. 정답 문장은 지문의 핵심어를 일부 포함하지만 논점이 다른 쪽(다른 대상, 다른 측면)으로 벗어난 경우가 많습니다.
[풀이 과정]
1) 첫 문장의 화제: 아기가 언제 자기 감정과 자아를 인식하는지 확실히 알 수 없다.
2) ①은 그 이유(말을 못 해서 소통할 수 없다), ②는 동물의 경우와 같다는 비교, ④는 그래서 동물과 같은 연구 방법을 쓴다는 내용, ⑤는 그럼에도 아기에게는 감정을 인정한다는 내용으로 모두 ‘말 이전의 아기의 인식을 알아내는 문제’를 다룹니다.
3) ③은 animals라는 단어를 이어받았지만 동물 실험에 대한 비판이라는 전혀 다른 화제로 벗어났습니다.
[정답 근거] 동물 실험 비판을 다룬 ③이 글의 흐름과 관계없습니다.
[오답 분석]
무관한 문장은 앞 문장의 핵심어(animals)를 그대로 써서 자연스러워 보이게 만드는 경우가 많습니다. 단어가 아니라 ‘무엇에 대해 말하는가’를 기준으로 판단합니다.
[어휘] with certainty 확실하게 · in this respect 이런 점에서 · draw criticism 비판을 받다 · readily 쉽게 · assumption 가정', 35, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글의 순서 (2점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 사람들은 확실성을 원하지만 확실한 것은 죽음뿐이다.
2) (C)의 In response(이에 대응하여)는 ‘불확실함’에 대한 반응을 가리키므로 주어진 글 바로 뒤에 옵니다. 사람들은 근본주의로 확실성을 얻으려 했다.
3) (B)의 this same purpose(같은 목적)는 (C)의 ‘확실성을 얻으려는 목적’을 가리킵니다. 최근에는 과학에서 확실성을 찾다가 또 다른 근본주의가 생긴다.
4) (A)의 That은 (B)의 ‘과학에 절대적 확실성을 기대하는 것’을 가리키며, 그것이 과학에 대한 오해라고 결론짓습니다.
[정답 근거] 주어진 글 → (C) → (B) → (A)이므로 ⑤가 정답입니다.
[오답 분석]
(A)를 먼저 놓으면 That이 가리킬 대상(과학에 대한 기대)이 아직 나오지 않아 흐름이 끊깁니다.
(B)를 먼저 놓으면 this same purpose가 무엇과 ‘같은’ 목적인지 알 수 없습니다.
[어휘] long for ~을 갈망하다 · banish 몰아내다 · embrace 받아들이다 · absolute 절대적인 · confidence 신뢰', 36, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글의 순서 (3점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 역사가들은 과거를 현재의 시각으로 본다.
2) (A)의 This bias는 주어진 글의 ‘현재의 시각으로 과거를 보는 경향’을 가리키고, 그 예로 The Flintstones를 소개합니다.
3) (C)의 In this popular cartoon은 (A)에서 소개한 만화를 가리키며, 석기 시대 사람들이 현대인처럼 사는 모습을 구체적으로 설명합니다.
4) (B)의 this ... a single case는 (C)의 만화 사례를 받아, 이런 오류가 하나의 사례에 그치지 않는다고 일반화합니다.
[정답 근거] 주어진 글 → (A) → (C) → (B)이므로 ①이 정답입니다.
[오답 분석]
(C)가 (A)보다 앞서면 this popular cartoon이 무엇인지 알 수 없습니다.
(B)가 (C)보다 앞서면 ‘하나의 사례라면’이 가리키는 구체적 사례 설명이 빠지게 됩니다.
[어휘] tricky 까다로운 · intention 의도 · lens 렌즈, 관점 · bias 편향 · context 맥락', 37, 3),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 문장 삽입 (2점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장은 But으로 시작하며 ‘예측은 매우 어렵다’는 내용입니다. 따라서 앞에는 예측하려는 노력이나 기대가, 뒤에는 예측이 어렵다는 근거가 와야 합니다.
2) ④ 앞: 기업은 알고리즘을 만들고 트렌드 예측가는 다음을 추측하려 한다(예측하려는 노력).
3) ④ 뒤: Even experts struggle to spot hits ... rejected by twelve publishers(전문가도 실패한다는 근거).
4) ③에 넣으면 To grab those rewards의 those rewards가 바로 앞 문장의 rewards와 떨어져 연결이 끊깁니다.
[정답 근거] 노력(알고리즘·예측가)과 실패 사례(전문가도 실패) 사이인 ④가 정답입니다.
[오답 분석]
①②: 아직 질문을 던지며 화제를 소개하는 단계라 ‘But 어렵다’가 들어갈 자리가 아닙니다.
⑤: 앞 문장이 이미 어려움의 사례를 말하고 있어, 그 뒤에 But으로 다시 어렵다고 하면 대조가 성립하지 않습니다.
[어휘] notoriously 악명 높게 · catch on 인기를 얻다 · enormous 엄청난 · forecaster 예측가 · take off 급격히 인기를 얻다', 38, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 문장 삽입 (3점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장의 Even inside companies(회사 안에서조차)는 ‘회사 밖’의 신뢰 하락이 먼저 나온 뒤에 이어질 수 있다는 단서입니다.
2) ② 앞: trust in corporations, governments, and institutions keeps wearing away(외부의 신뢰 하락).
3) ② 뒤: With both the public and their own workers losing faith in them — their own workers(자기 직원들)가 신뢰를 잃었다는 정보는 주어진 문장에서만 나옵니다.
4) 따라서 주어진 문장은 외부 신뢰 하락과 ‘대중과 직원 모두’를 묶는 문장 사이에 들어가야 합니다.
[정답 근거] ②에 넣어야 Even inside companies와 both the public and their own workers가 자연스럽게 이어집니다.
[오답 분석]
①: 아직 회사 밖의 신뢰 하락이 나오지 않아 Even inside companies의 대비가 성립하지 않습니다.
③: 바로 앞 문장(With both the public and their own workers losing faith in them)이 이미 직원의 신뢰 하락을 전제로 정리하고 있으므로, 직원 이야기를 처음 꺼내는 주어진 문장은 그 앞에 와야 합니다.
④⑤: Yet 이후 리더십에 관한 내용으로 넘어가 흐름이 맞지 않습니다.
[어휘] loyalty 충성심 · wear away 닳아 없어지다 · lose faith in ~에 대한 신뢰를 잃다 · remarkable 놀라운 · rise to one''s best 최고의 역량을 발휘하다', 39, 3),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 요약문 완성 (2점)
[유형 전략] 요약문은 먼저 요약문을 읽고 (A), (B)가 각각 무엇을 묻는지 확인한 뒤 지문을 읽습니다. 지문의 실험 결과나 결론 문장을 요약문의 표현과 짝지어, 같은 뜻을 다른 말로 바꾼(paraphrase) 선택지를 고릅니다.
[풀이 과정]
1) 요약문 분석: 사람들은 (A)라고 여기는 가격 정책을 거부한다, 경제적으로는 (B)할지라도.
2) (A) 근거: People ... saw it as a shameless attempt to exploit them, Don''t profit at my expense!, sense of fairness → 사람들이 그 정책을 ‘불공정한(unjust)’ 것으로 보았습니다.
3) (B) 근거: In terms of supply and demand, the plan made perfect sense, sound economic reasons → 경제적으로는 ‘논리적인(logical)’ 정책이었습니다.
[정답 근거] (A) unjust, (B) logical인 ③이 정답입니다.
[오답 분석]
① harmful(해로운)은 불공정과 다르고, unpopular(인기 없는)는 경제적 관점의 평가가 아닙니다.
② complex(복잡한)라는 근거가 없습니다.
④ (A)는 맞지만 (B) risky(위험한)는 made perfect sense와 맞지 않습니다.
⑤ (B)는 맞지만 (A) outdated(시대에 뒤떨어진)는 근거가 없습니다.
[어휘] fitted with ~을 장착한 · revenue 수익 · fury 분노 · step down 물러나다 · shameless 뻔뻔한 · trigger 촉발하다', 40, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q41.webp]]', '[[img:/naesin/csat-h1-mock/r01/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 장문 제목 (2점)
[유형 전략] 장문 제목은 글 전체의 논지를 담아야 합니다. 도입의 일반적 진술과 후반부의 결론을 연결하여 주제를 정리하고, 글의 일부 사례만 다루는 선택지는 지웁니다.
[풀이 과정]
1) 도입: 사랑은 가장 깊고 달콤한 감정이다. → Yet 이후가 핵심: 사랑을 잃으면 가장 큰 고통의 원천이 된다.
2) 이유 설명: 사랑은 삶과 기쁨의 원천과의 유대이므로 그것이 끊기면 생명에 대한 위협처럼 느껴지고, 자아가 위축된다.
3) 결론: 이 고통(heartbreak)은 다시 마음을 열기 두려워해서 사랑의 기쁨보다 오래 지속된다.
[정답 근거] 사랑을 잃은 고통이 왜 그토록 큰지를 설명하는 글이므로 ①이 정답입니다.
[오답 분석]
② 거절에 대한 두려움(the fear of loss or rejection)이 언급되지만 극복 방법은 제시하지 않습니다.
③ 사랑하지 못하는 사람들의 이유를 다루는 글이 아닙니다.
④ the universe는 사랑의 원천의 예 중 하나일 뿐입니다.
⑤ 사랑의 기쁨이 영원하다는 것은 글과 반대입니다(heartbreak lasts longer than the joy).
[어휘] vital 필수적인 · bond 유대 · expand 확장하다 · shrink 줄어들다 · longing 갈망', 41, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q42.webp]]', '[[img:/naesin/csat-h1-mock/r01/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 장문 어휘 (2점)
[유형 전략] 장문 어휘 문항은 단락별 흐름을 잡은 뒤 밑줄 낱말을 하나씩 문맥에 넣어 봅니다. 앞뒤 문장과 인과·대조 관계가 맞지 않는 낱말, 반의어로 바꾸면 자연스러워지는 낱말이 정답입니다.
[풀이 과정]
1) (a)가 있는 문장은 Yet으로 시작해 앞 문장(사랑은 가장 달콤한 감정)과 대조를 이룹니다. 사랑이 거절되거나 사라지면 가장 강렬한 ‘고통’의 원천이 되어야 합니다.
2) 뒤 문장들도 threat to life, painful, heartbreak처럼 고통을 설명하므로 delight(기쁨)는 흐름에 맞지 않습니다. delight → pain
[정답 근거] (a) delight는 pain으로 바꾸어야 하므로 ①이 정답입니다.
[오답 분석]
(b) bond: 사랑은 삶과 기쁨의 원천에 대한 유대 — 다음 문장의 this bond is broken과 이어집니다.
(c) threat: 유대가 끊기면 생명에 대한 ‘위협’으로 느껴진다는 뜻으로 맞습니다.
(d) joyful: 사랑이 기뻤던 만큼 아프다(as painful as the love was joyful)로 맞습니다.
(e) longer: 다시 마음을 열기 두려워하므로 고통이 ‘더 오래’ 간다는 뜻으로 맞습니다.
[어휘] intense 강렬한 · threat 위협 · reach out (도움·관계를) 구하다', 42, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q43.webp]]', '[[img:/naesin/csat-h1-mock/r01/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 장문 순서 (2점)
[유형 전략] 장문 순서는 등장인물의 행동과 시간의 흐름을 따라갑니다. 각 단락의 첫 문장이 앞 단락의 마지막 장면과 이어지는지(장소 이동, 대화의 응답, 사건의 결과) 확인합니다.
[풀이 과정]
1) (A) 마지막: Lulu가 Harry Horse를 만나러 헛간(barn)으로 간다.
2) (D) 첫 문장은 Harry의 대답("I haven''t seen your bell," said Harry)이므로 (A) 바로 뒤에 옵니다. Harry → Kitty의 스카프 → Kitty가 Donny의 모자를 건넴 → Donny를 만나러 감.
3) (B) 첫 문장 Lulu handed Donny his straw hat.은 (D)의 끝(Donny를 만나러 감)과 이어집니다. Donny가 Gary의 안경을 건넴 → 목초지(meadow)로 감.
4) (C) 첫 문장 Gary was resting in the meadow ... bringing back his glasses는 (B)의 끝과 이어지고, Cora가 방울을 돌려주며 이야기가 끝납니다.
[정답 근거] (A) → (D) → (B) → (C)이므로 ④가 정답입니다.
[오답 분석]
물건의 주인이 바뀌는 순서(스카프 → 모자 → 안경 → 방울)와 장소 이동(barn → farmhouse → cabbage patch → meadow → cowshed)을 따라가면 순서를 확실히 정할 수 있습니다.
[어휘] collar 목걸이, (개 등의) 목줄 · barn 헛간 · light up (얼굴이) 환해지다 · meadow 목초지 · cowshed 외양간', 43, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q44.webp]]', '[[img:/naesin/csat-h1-mock/r01/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 지칭 대상 (2점)
[유형 전략] 지칭 대상 문항은 밑줄 친 대명사가 나오는 문장에서 그 행동을 하는 사람이 누구인지 앞뒤 문맥으로 확인합니다. 대화문 속 you/I는 말하는 사람과 듣는 사람을 기준으로 따집니다.
[풀이 과정]
1) (a) Peggy가 Lulu에게 묻는 말 속의 you → Lulu
2) (b) 방울을 찾을 희망을 잃기 시작한 사람 → Lulu
3) (c) Gary가 Lulu에게 “Cora가 너를 찾고 있었다”고 하는 말 속의 you → Lulu
4) (d) Cora가 Lulu에게 하는 말 속의 you → Lulu
5) (e) 스카프를 돌려받고 고마워한 사람 → Kitty
[정답 근거] 나머지는 모두 Lulu이고 (e)만 Kitty이므로 ⑤가 정답입니다.
[오답 분석]
(c)는 she와 you가 함께 있어 헷갈리기 쉽습니다. she는 Cora, 밑줄 친 you는 말을 듣는 Lulu입니다.', 44, 2),
(@csh1_r01, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r01/q45.webp]]', '[[img:/naesin/csat-h1-mock/r01/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 장문 내용 불일치 (2점)
[유형 전략] 장문 내용 불일치는 선택지 순서가 대체로 글의 사건 순서를 따릅니다. 선택지의 핵심어로 해당 단락을 찾아 인물·행동·시간을 정확히 대조합니다.
[풀이 과정]
1) 선택지를 단락과 대응시킵니다: ① (A), ② (D), ③ (B), ④·⑤ (C).
2) ⑤ (C)에서 Lulu found Cora in the cowshed.라고 했습니다. Lulu가 Cora를 만난 곳은 목초지(meadow)가 아니라 외양간입니다. 목초지는 Gary가 쉬던 곳입니다.
[정답 근거] Lulu는 외양간(cowshed)에서 Cora를 만났으므로 ⑤가 적절하지 않습니다.
[오답 분석]
① Lulu Lamb, who was usually cheerful과 일치합니다.
② Harry가 I found this blue scarf. It belongs to Kitty Cat.이라고 하며 건넸습니다.
③ (B)에서 Donny는 I''m afraid I haven''t seen your bell이라고 말했습니다.
④ Gary가 Now go and see Cora Cow.라고 했습니다.', 45, 2);

-- 2회: 2023년 5월 고1 이투스 원본(고1 이투스 23 5모)
INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-CSH1-MOCK-R02', 'NAESIN', '고1 수능 영어 독해 모의고사 2회 · 18~45번 변형문제 2회', '2023년 5월 고1 이투스 원본 · 28문항 · 45분 · 62점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 풀이 전략 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @csh1_r02 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-CSH1-MOCK-R02');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 목적 (2점)
[유형 전략] 글의 목적은 대개 글 후반부의 요청·안내 문장(please, we ask, I would like to, I am writing to)에 드러납니다. 앞부분의 인사·배경 설명에 끌려가지 말고, 필자가 독자에게 결국 무엇을 해 달라고 하는지 한 문장으로 정리한 뒤 선택지와 맞춰 봅니다.
[풀이 과정]
1) 도입: 3월 3일에 주문한 과학 도서 120권에 관해 쓴다(I am writing about the order).
2) 문제 상황: 10일 안에 온다고 했지만 한 달이 지나도록 책도, 지연 이유도 받지 못했다(we have received neither the books nor any explanation).
3) 요청: I must ask that you deliver the books within seven days. 7일 안에 오지 않으면 주문을 취소하겠다는 경고가 뒤따릅니다. must ask가 들어간 문장이 목적입니다.
[정답 근거] 지연된 책을 7일 안에 보내 달라고 촉구하는 글이므로 ④가 정답입니다.
[오답 분석]
① 주문 내역을 바꾸려는 것이 아닙니다.
② 책이 아직 도착하지 않았으므로 파손 항의는 성립하지 않습니다.
③ 할인 문의는 없습니다. 환불(full refund)은 취소할 경우의 조건입니다.
⑤ 전화에 응답이 없었다는 불만은 있지만, 담당자 교체를 요구하지 않습니다.
[어휘] to whom it may concern 관계자께 · assure 장담하다 · explanation 설명 · have no choice but to ~할 수밖에 없다 · refund 환불', 18, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 심경 변화 (2점)
[유형 전략] 심경 변화는 처음 상황과 전환 이후 상황을 나누어 읽습니다. 사건이 바뀌는 지점(when, suddenly, but, then)을 찾고, 그 앞뒤에서 인물의 감정을 드러내는 표현(형용사, 신체 반응, 혼잣말)을 각각 하나씩 골라 두 감정의 짝을 확인합니다.
[풀이 과정]
1) 전반부: 출근하는 Tom을 보며 I find myself wishing I could dress up, rush out the door ... earning a paycheck, I almost feel a pang → 직장 생활을 하는 남편을 부러워합니다(envious).
2) 전환점: Then Evie tugs at my sleeve. 딸과 정원으로 나갑니다.
3) 후반부: my heart fills with warmth, I would miss these moments far more → 아이와 보내는 지금에 만족합니다(content).
[정답 근거] 부러움(envious)에서 만족(content)으로 바뀌므로 ⑤가 정답입니다.
[오답 분석]
① 자랑스러움이나 부끄러움은 언급되지 않습니다.
② 지루함이나 초조함을 드러내는 표현은 없습니다.
③ 희망 → 실망은 글의 흐름과 반대입니다.
④ 처음에 편안한 상태가 아니며, 나중에 짜증이 나지도 않습니다.
[어휘] straighten 똑바로 하다 · lost in thought 생각에 잠긴 · paycheck 급료 · tug at ~을 잡아당기다 · giggle 킥킥 웃다', 19, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 필자의 주장 (2점)
[유형 전략] 필자의 주장은 should, must, need to, it is important to, 명령문처럼 당위를 나타내는 문장에 담깁니다. 예시나 일화는 주장을 뒷받침하는 재료이므로, 일화가 끝난 뒤 필자가 일반화하는 문장을 찾아 그 내용과 같은 선택지를 고릅니다.
[풀이 과정]
1) 첫 명령문 Don''t treat this Hyde as an enemy가 주장의 방향(충동적인 면을 적으로 보지 마라)을 알려 줍니다.
2) 근거: 자신과 싸우면 에너지가 낭비되고(drains energy), 억누르려 해도 효과가 없으며(Trying to crush ... will not work), 그 면은 재미와 창의성의 원천이기도 하다.
3) 결론 문장 So treat your Hyde as a part of yourself that you can win over through cooperation and compromise, not through combat.이 주장을 정리합니다.
[정답 근거] 충동적인 면을 억누르지 말고 협력과 타협으로 다루라는 ③이 정답입니다.
[오답 분석]
① 즉시 바로잡으라는 것은 ‘싸우지 마라’는 주장과 반대입니다.
② 창의성(creativity)이 언급되지만, 자기 통제를 먼저 갖추라는 말은 없습니다.
④ 다른 사람의 조언은 언급되지 않습니다.
⑤ 재미(sense of fun)를 줄이라는 것은 글의 관점과 반대입니다.
[어휘] drain 소모시키다 · restless 가만히 있지 못하는 · crush 짓밟다 · a bundle of ~의 덩어리 · win over ~을 설득하다, 자기편으로 만들다 · compromise 타협', 20, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 밑줄 함의 (3점)
[유형 전략] 밑줄 친 표현은 비유이므로 글자 그대로 해석하지 않습니다. 밑줄 앞뒤에서 같은 대상을 설명하는 문장을 찾아, 비유 속 단어(밑줄의 핵심 명사·동사)가 글에서 무엇에 대응하는지 짝지은 뒤, 그 의미를 풀어 쓴 선택지를 고릅니다.
[풀이 과정]
1) 비유의 짝을 정리합니다: 도시의 지리(geography) = 사람의 유전자(genetic code), 도시의 적응(adapt to it) = 사람이 타고난 조건에 적응하는 방식.
2) 바로 앞 문장 The way a city adjusts in order to survive and flourish is what keeps it alive and vigorous.를 ‘you’에게 적용한 것이 밑줄의 의미입니다.
3) 즉, 유전자는 바꿀 수 없지만 그에 어떻게 적응하느냐가 노화의 질을 결정한다는 뜻입니다.
[정답 근거] 타고난 것에 어떻게 적응하느냐에 따라 잘 늙는지가 결정된다는 ②가 정답입니다.
[오답 분석]
① 사는 곳이 건강을 결정한다는 것은 도시의 지리를 글자 그대로 사람에게 옮긴 오답입니다.
③ 유전자를 바꿀 수 없다는 앞부분만 반영하고, 적응할 수 있다는 핵심을 뒤집었습니다.
④ 다른 사람의 도움은 언급되지 않습니다.
⑤ 노력과 외모의 관계는 다루지 않습니다.
[어휘] graceful 우아한 · geography 지리, 지형 · earthquake-resistant 내진의 · commuter 통근자 · flourish 번창하다', 21, 3),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글의 요지 (2점)
[유형 전략] 요지는 글 전체가 말하려는 한 가지 핵심입니다. 도입의 질문·통념, 대조(however, but), 결론(therefore, in short)을 확인하고, 세부 예시 하나만 다룬 선택지나 지문 단어를 섞어 만든 그럴듯한 선택지는 지웁니다.
[풀이 과정]
1) 일화: 아이들은 눈 깜짝할 새 자라 버렸다(In what felt like the blink of an eye).
2) 핵심 문장: All we ever really have is "now" time, and whether or not it feels like "quality" time, we need to make the most of it.
3) 마지막 문장도 같은 내용을 반복합니다: the time in front of me matters more than waiting for some perfect moment.
[정답 근거] 특별한(완벽한) 시간을 기다리지 말고 지금의 시간을 소중히 하라는 ⑤가 요지입니다.
[오답 분석]
① 자녀가 성인이 된 뒤 관계가 더 깊어진다는 말은 없습니다.
② 필자가 집에서 일한다는 것은 사례일 뿐, 재택근무의 장점을 말하는 글이 아닙니다.
③ 일관된 태도는 언급되지 않습니다.
④ 흔히 하는 말(양보다 질)을 이용한 오답입니다. 필자는 오히려 quality time을 기다리기보다 now time을 잡으라고 합니다.
[어휘] before you know it 어느새 · blink of an eye 눈 깜짝할 사이 · make the most of ~을 최대한 활용하다 · a handful of 몇몇의', 22, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글의 주제 (2점)
[유형 전략] 주제는 글의 핵심 소재와 그에 대한 필자의 관점을 함께 담은 영어 구로 나타납니다. 반복되는 핵심어를 찾고, 글이 그 소재의 무엇(원인, 효과, 과정, 중요성, 한계)을 다루는지 확인하여 범위가 너무 넓거나 좁은 선택지를 지웁니다.
[풀이 과정]
1) 첫 문장이 주제문입니다: 소기업은 자기 몫 이상의 새 일자리를 만들어 왔다.
2) 이어서 근거가 나열됩니다: 일자리를 가장 많이 늘린 산업 10개 중 7개가 소기업 중심, 구직이 어려운 사람들을 고용, 첫 직장과 직무 교육 제공, 미국 전체 기업의 99.9%와 순 신규 일자리의 3분의 2.
3) 모든 근거가 ‘고용에서 소기업의 역할’이라는 한 방향을 가리킵니다.
[정답 근거] 일자리 창출에서 소기업이 하는 중요한 역할을 다룬 글이므로 ①이 정답입니다.
[오답 분석]
② 젊은 사람을 고용한다는 내용을 뒤집은 오답입니다. 소기업은 오히려 그들을 더 많이 고용합니다.
③ 대기업은 다루지 않습니다.
④ 시간제 근무자는 소기업이 고용하는 대상의 예일 뿐입니다.
⑤ Small Business Administration은 통계의 출처일 뿐, 정부 지원 방법은 다루지 않습니다.
[어휘] fair share 정당한 몫 · trade 직종 · on-the-job training 직무 교육 · workforce 노동력 · net 순(純)', 23, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글의 제목 (2점)
[유형 전략] 제목은 주제를 압축하거나 비유·의문문으로 바꾼 표현입니다. 먼저 글의 주제를 우리말로 정리한 뒤, 그 핵심을 담은 선택지를 고릅니다. 지문에 나온 단어가 그대로 쓰였지만 글의 일부만 다루거나 반대 방향을 말하는 선택지에 주의합니다.
[풀이 과정]
1) 첫 문장: 민족·인종 집단의 명칭이 피부색 중심 분류에서 벗어나는 흐름과 함께 바뀌었다.
2) 구체적 예: "Black" → "African American". 피부색에서 출신(origin)과 현재 국적(nationality)으로 초점이 이동했습니다.
3) 결론: 이제 피부색보다 의미 있는 정보(조상, 출신)를 알려 주는 말이 생겼다.
[정답 근거] 피부색에서 출신으로, 집단 명칭이 바뀐 과정을 담은 ③이 제목으로 적절합니다.
[오답 분석]
① 이민법은 다루지 않습니다.
② 언어가 문화를 가로막는다는 내용은 없습니다.
④ 국적과 조상을 비교해 우열을 가리지 않습니다. 오히려 조상(출신)이 피부색보다 의미 있다고 합니다.
⑤ 논쟁이 계속된다는 내용은 없으며, 명칭이 바뀐 흐름을 설명합니다.
[어휘] classification 분류 · justify 정당화하다 · recognition 인정 · racialized 인종적으로 구분된 · informative 유익한 정보를 주는', 24, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 도표 불일치 (2점)
[유형 전략] 도표 문항은 선택지 문장을 하나씩 도표 수치와 맞춰 봅니다. 비교 표현(more than, twice, the lowest), 순위, 차이 계산이 함정이 되는 경우가 많으므로, 문장에 나온 항목·연도·수치를 도표에서 손가락으로 짚어 가며 확인합니다.
[풀이 과정]
1) ① Driving 68%, 다음 Relaxing at home 61%. (일치)
2) ② Cooking & cleaning은 55%로 절반보다 많습니다. Less than half는 틀렸습니다. (불일치)
3) ③ Commuting 49% − Working & studying 37% = 12%p. (일치)
4) ④ Exercising은 42%로 열 명 중 네 명(40%)보다 많습니다. (일치)
5) ⑤ Going to sleep 17%로 가장 낮고 20% 미만입니다. (일치)
[정답 근거] 요리·청소 중 음악을 듣는 비율은 55%로 절반보다 많으므로 ②가 도표와 일치하지 않습니다.
[오답 분석]
‘열 명 중 네 명’처럼 분수·비율로 바꾼 표현은 백분율로 바꿔(40%) 비교합니다. percentage points는 두 백분율의 차이입니다.
[어휘] commute 통근하다 · percentage point 퍼센트포인트', 25, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 불일치 (2점)
[유형 전략] 인물 내용 일치 문항은 선택지 순서가 대체로 글의 순서와 같습니다. 선택지의 핵심어(연도, 장소, 숫자, 고유명사)를 먼저 보고 지문에서 해당 부분을 찾아 정확히 대조합니다. 일부만 바꾼 진술(시기, 횟수, 주체)에 주의합니다.
[풀이 과정]
1) 선택지 순서대로 지문을 따라갑니다.
2) ② In 1951, he entered Presidency College in Calcutta, where he earned a degree in economics. 철학이 아니라 경제학 학위입니다. 철학(philosophy)은 Harvard에서 가르친 과목에 나옵니다.
[정답 근거] Presidency College에서 받은 것은 경제학 학위이므로 ②가 일치하지 않습니다.
[오답 분석]
① his father was a professor of chemistry와 일치합니다.
③ During his time there, he was diagnosed with oral cancer와 일치합니다.
④ a professor of economics and philosophy와 일치합니다.
⑤ In 1998, he was awarded the Nobel Prize in Economics와 일치합니다.
[어휘] be diagnosed with ~ 진단을 받다 · oral 구강의 · radiation treatment 방사선 치료 · shortage 부족', 26, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 안내문 불일치 (2점)
[유형 전략] 안내문은 항목별로 정보가 정리되어 있으므로 선택지의 핵심어(날짜, 시간, 대상, 비용, 신청 방법)로 해당 항목을 바로 찾습니다. 주의 사항·예외 조건(only, not, must)이 함정으로 자주 쓰입니다.
[풀이 과정]
1) 선택지 핵심어: ① guide → Included Highlights, ② 8시 → Departure, ③ 65세 → Prices, ④ 점심 → 마지막 문단, ⑤ 예약 → 마지막 문장.
2) ⑤ book online at least 48 hours in advance: 최소 48시간 전에 예약해야 하므로 당일 예약은 안 됩니다.
[정답 근거] 출발 48시간 전까지 예약해야 하므로 ⑤가 일치하지 않습니다.
[오답 분석]
① A professional guide와 일치합니다.
② Departs ... at 8:00 a.m.과 일치합니다.
③ Seniors (65 and over): £75와 일치합니다.
④ Lunch is not included.와 일치합니다.
[어휘] spire 첨탑 · admission 입장 · accompany 동행하다 · secure 확보하다 · in advance 미리', 27, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 안내문 일치 (2점)
[유형 전략] 안내문 일치 문항은 정답이 하나뿐이므로 선택지를 하나씩 대조하며 틀린 것을 지웁니다. 날짜·요일, 연령, 요금, 포함 여부, 사전 등록 여부처럼 숫자와 조건이 바뀐 부분을 꼼꼼히 확인합니다.
[풀이 과정]
1) 일치하는 것 하나를 찾는 문항이므로 틀린 선택지를 지워 가며 확인합니다.
2) ③ All workshops will be held in Room 204 of the Eastside Community Center. 모두 같은 장소입니다.
[정답 근거] ③이 안내문과 일치합니다.
[오답 분석]
① free workshops라고 했으므로 무료입니다.
② 성인 워크숍은 수요일(Wednesday)입니다.
④ Participants must bring their own laptops. 노트북은 직접 가져와야 합니다.
⑤ must be completed online. 온라인으로만 등록합니다.
[어휘] registration 등록 · be limited to ~로 제한되다 · participant 참가자', 28, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어법 (3점)
[유형 전략] 어법 문항은 밑줄마다 확인할 문법 포인트를 먼저 정합니다(동사 자리·수일치, 관계사와 접속사, 분사·to부정사, 병렬 구조, 대명사, 형용사·부사). 특히 문장의 주어와 본동사를 먼저 찾으면 수일치와 준동사 문제를 빠르게 판단할 수 있습니다.
[풀이 과정]
1) ① 문장에 이미 본동사 looked after가 있고 접속사가 없으므로 apply는 동사 자리에 올 수 없습니다. 주어(Men and women)가 꿀과 동물성 지방을 ‘바르는’ 능동 관계의 분사구문이 되어야 하므로 applying이 맞습니다.
2) ② tubs (which were) filled with milk: 욕조가 우유로 ‘채워진’ 수동 관계이므로 과거분사 filled가 맞습니다.
3) ③ long hours (which were) spent working: 시간이 ‘보내진’ 수동 관계이므로 spent가 맞습니다.
4) ④ 콤마 뒤 계속적 용법의 관계대명사로 선행사 lead를 받으며, 절 안에서 주어 역할을 하므로 which가 맞습니다.
5) ⑤ so highly prized that ~: ‘매우 ~해서 …하다’의 so ~ that 구문이므로 that이 맞습니다.
[정답 근거] 본동사가 이미 있는 문장에서 동사원형 apply를 쓴 ①이 틀렸습니다(→ applying).
어법 포인트: 분사구문 — 한 문장에 접속사 없이 동사가 두 개 올 수 없습니다. 본동사를 먼저 찾고, 나머지 동사는 의미상 주어와의 관계(능동/수동)에 따라 현재분사나 과거분사로 바꿉니다.
[어휘] look after 돌보다 · enhance 향상시키다 · leisure 여가 · prized 소중히 여겨지는 · delicate 섬세한', 29, 3),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘 (2점)
[유형 전략] 어휘 문항은 글의 흐름(원인과 결과, 대조, 예시)을 먼저 잡고, 밑줄 친 낱말이 그 흐름과 반대 방향인지 확인합니다. 밑줄 낱말을 반의어로 바꿔 넣었을 때 문맥이 자연스러워지면 그것이 정답입니다.
[풀이 과정]
1) 글의 흐름: 상징적 의미를 담은 패션 상품 → 소비자와의 정서적 유대 → 소비자의 꾸준한 구매 → 기업의 매출·이익 증가 → 경쟁 우위.
2) ④ 앞 문장에서 소비자들은 새 상품을 ‘정기적으로’ 사고 싶어 한다고 했고, 뒤에서도 This desire also leads to higher sales and profits라고 했습니다. 이런 소비자는 기업의 재무 성과를 ‘약화시키는(weaken)’ 것이 아니라 ‘강화하는(strengthen, improve)’ 존재입니다.
[정답 근거] 흐름과 반대되는 ④ weaken이 적절하지 않습니다(→ strengthen).
[오답 분석]
① 자기 이미지를 표현하므로 상징적(symbolic) 의미가 맞습니다.
② 패션이 상품에 더하는 가치(value)로 맞습니다.
③ 유행을 유지하려면 정기적으로(regularly) 사야 하므로 맞습니다.
⑤ 매출과 이익이 ‘더 높아진다’로 결론과 맞습니다.
[어휘] identity 정체성 · attach (의미를) 부여하다 · financial performance 재무 성과 · competitive edge 경쟁 우위', 30, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 핵심 논리: 우리는 경험·지식이 많은 사람(people who have more experience and knowledge)을 따르도록 배웠고, 믿을 만한 출처라고 생각되는 사람을 자동으로 따른다.
2) 광고의 예: 인상적인 학위(degrees), 흰 실험실 가운(white lab coats), 존경받는 유명 인사, 치과 의사·과학자·유명 요리사의 추천 → 모두 전문성과 지위를 나타내는 ‘권위’의 상징입니다.
3) 빈칸 문장은 이 예들을 한 단어로 묶은 것입니다.
[정답 근거] 전문가·학위·가운은 권위(authority)의 상징이므로 ②가 정답입니다.
[오답 분석]
① 부(wealth)를 나타내는 예는 없습니다.
③ 친근함이 아니라 전문성을 내세웁니다.
④ 전통과 관련된 예는 없습니다.
⑤ 새로움(novelty)을 강조하는 광고 예가 아닙니다.
[어휘] sensible 현명한 · side effect 부작용 · built-in 타고난 · readily 쉽게 · trigger 촉발하다', 31, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 예시: 제트 엔진의 부품(압축기, 터빈, 연소 장치)은 이전 기술(발전소, 산업용 송풍기)에도 들어 있었다.
2) 일반화: Technologies inherit parts from the technologies that came before them, so the way these parts are put together ... how new technologies are born.
3) 빈칸은 이 일반화를 정리한 결론입니다. 새 기술은 기존 부품을 새롭게 조합한 결과로 생겨난다는 것입니다.
[정답 근거] 기존 부품의 새로운 조합이라는 ⑤가 정답입니다.
[오답 분석]
① 우연한 발견이라는 근거는 없습니다.
② 시장의 요구는 언급되지 않습니다.
③ 과학적 논쟁은 다루지 않습니다.
④ radically new technologies를 떠올리게 하지만, 글은 오히려 새 기술도 갑작스럽지 않다(far less sudden)고 말하므로 정반대입니다.
[어휘] take apart 분해하다 · component 부품 · radically 근본적으로 · come into being 생겨나다', 32, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 첫 문장이 원리를 제시합니다: 자기 정보가 부족하면 다른 사람의 말과 행동에 주의를 기울인다.
2) A → B → C → D로 갈수록 ‘다른 사람들이 그렇게 믿는다’는 사실만으로 믿음이 퍼지고, 반대하기 어려워집니다. C는 독립적인 정보가 없어서(no independent information) 따라갑니다.
3) 빈칸 앞 simply because(단지 ~이기 때문에)는 믿음의 근거가 증거가 아니라 남들의 믿음뿐이라는 뜻을 강조합니다.
[정답 근거] 주변 사람들이 그것을 믿는 것처럼 보이기 때문이라는 ④가 정답입니다.
[오답 분석]
① 증거를 꼼꼼히 검토한다는 것은 정보가 부족해 남을 따른다는 내용과 반대입니다.
② 전문가의 공식 확인은 언급되지 않습니다.
③ 군중과 달라 보이고 싶다는 것은 오히려 D처럼 반대하는 경우에 가깝습니다.
⑤ 이미 알던 것과 맞는다는 것은 자기 정보가 없다는 전제와 맞지 않습니다.
[어휘] private information 개인이 가진 정보 · alarm 불안, 경계 · justified 정당한 · independent 독립적인 · reject 거부하다', 33, 3),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 빈칸 문장은 This is why we ~로, 앞 내용 전체의 결론을 묻습니다.
2) 앞 내용: 하나를 선택하면 다른 것을 놓쳐 후회할까 걱정한다 → decide의 -cide는 ‘자르다’라는 뜻 → 선택은 자기 발전에 필요하지만 동시에 다른 가능성을 잘라 내는 ‘절단’이다.
3) 따라서 우리가 선택할 때 그토록 고통스러워하는 이유를 설명하는 글입니다. Feeling some pain when we make decisions is natural.이 중간에서 미리 단서를 줍니다.
[정답 근거] 선택이 그토록 고통스러운 이유를 정리하는 ①이 정답입니다.
[오답 분석]
② 다른 사람의 조언은 언급되지 않습니다.
③ 익숙한 것을 선호한다는 내용은 없습니다.
④ 후회를 두려워한다는 내용과 반대입니다.
⑤ But no one can be everything.과 정반대입니다.
[어휘] commit to ~에 전념하다 · storehouse 저장소 · alternative 대안의 · self-development 자기 발전 · give up 포기하다', 34, 3),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 무관한 문장 (2점)
[유형 전략] 무관한 문장 문항은 첫 문장(주제문)이 제시한 소재와 방향을 확인한 뒤 각 문장이 그 방향을 이어 가는지 봅니다. 정답 문장은 지문의 핵심어를 일부 포함하지만 논점이 다른 쪽(다른 대상, 다른 측면)으로 벗어난 경우가 많습니다.
[풀이 과정]
1) 첫 문장의 화제: 모든 지역 사회에는 사람들이 모여 이야기를 나누는 사교의 중심지가 있다.
2) ①은 그 장소가 미용실·이발소라고 소개하고, ②③⑤는 그곳이 대화에 알맞은 이유(앉아서 기다릴 곳, 알맞은 분위기, 기기에 방해받지 않음, 누구나 대화에 낄 수 있음)를 설명합니다.
3) ④는 온라인 예약으로 대기 시간이 줄어든다는 내용으로, 대화의 장소라는 화제에서 벗어납니다. 오히려 ‘앉아서 기다리는 공간’이라는 ②의 장점과도 어긋납니다.
[정답 근거] 이발소의 예약 방식과 대기 시간을 다룬 ④가 흐름과 관계없습니다.
[오답 분석]
④는 barbers, waiting 같은 지문 단어를 다시 써서 자연스러워 보이지만 ‘사교의 장소’라는 논점과 관계가 없습니다.
[어휘] catch up on (소식을) 듣다 · suited to ~에 적합한 · distraction 주의를 흐트러뜨리는 것 · booking 예약', 35, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글의 순서 (2점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글의 마지막: your parents are in charge(부모가 책임진다).
2) (B)의 They는 parents를 가리키며, 부모가 무엇을 정하는지 구체적으로 설명하고, 자라면서 스스로 결정하게 된다고 합니다.
3) (A)의 however는 (B)의 ‘스스로 결정하게 된다’와 대조됩니다. 그런데도 어릴 때의 길들임 때문에 남이 책임진다고 느낀다.
4) (C)의 What is more(게다가)는 (A)의 ‘남 탓’에 덧붙여, 남은 악당이고 자신은 피해자라는 생각과 변명으로 이어집니다.
[정답 근거] 주어진 글 → (B) → (A) → (C)이므로 ②가 정답입니다.
[오답 분석]
(A)를 먼저 놓으면 however가 대조할 내용(스스로 결정하게 됨)이 없습니다.
(C)를 먼저 놓으면 in this view가 가리킬 관점(남 탓)이 아직 나오지 않았습니다.
[어휘] in charge 책임지는 · programming 길들임, 프로그래밍 · fault 잘못 · victim 피해자 · make excuses 변명하다', 36, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글의 순서 (3점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 사회는 우유부단함을 부정적으로 본다.
2) (A)의 Such indecisiveness는 주어진 글의 indecisiveness를 받아 그 원인(완벽주의)을 설명하고, 완벽주의자의 두려움을 소개합니다.
3) (C)의 This fear는 (A)의 ‘잘못된 선택에 대한 두려움’을 가리키며, 그래서 결정을 꺼린다(reluctant to decide)고 합니다.
4) (B)의 this reluctance to commit은 (C)의 reluctant를 받고, however로 흐름을 바꾸어 우유부단함의 이점을 제시하며 마무리합니다.
[정답 근거] 주어진 글 → (A) → (C) → (B)이므로 ①이 정답입니다.
[오답 분석]
(B)를 주어진 글 바로 뒤에 놓으면 this reluctance to commit이 가리킬 대상이 없습니다.
(C)를 먼저 놓으면 This fear와 them이 무엇·누구인지 알 수 없습니다.
[어휘] in a positive light 긍정적으로 · trait 특성 · reluctant 꺼리는 · weigh 따져 보다 · rational 합리적인', 37, 3),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 문장 삽입 (2점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장의 this fear of the unfamiliar(낯선 것에 대한 이 두려움)는 바로 앞에 ‘두려움’이 나와야 한다는 단서입니다.
2) ③ 앞 문장: our fear of the unknown often keeps us from taking risks and exploring new ideas. 두려움이 처음이자 유일하게 나오는 곳입니다.
3) ③ 뒤: Another factor ~로 창의성의 두 번째 요소가 시작되므로, 첫 번째 요소(경험에 대한 개방성)에 대한 설명을 주어진 문장으로 마무리하는 것이 자연스럽습니다.
[정답 근거] 두려움을 언급한 문장 바로 뒤인 ③이 정답입니다.
[오답 분석]
①②: 아직 두려움이 언급되지 않아 this fear가 가리킬 대상이 없습니다.
④⑤: 두 번째 요소(경험을 성찰하기)의 문제는 경험을 그냥 흘려보내는 경향이지 낯선 것에 대한 두려움이 아닙니다.
[어휘] contrary to ~와 반대로 · rigid 엄격한 · openness 개방성 · reflect on ~을 되돌아보다 · enrich 풍요롭게 하다', 38, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 문장 삽입 (3점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장은 on the other hand로 질투(jealousy)와 대조하며 시기(envy)를 처음 정의합니다. 따라서 질투에 대한 설명이 끝나고 시기에 대한 설명이 시작되는 지점에 들어갑니다.
2) ① 앞: 아이들의 질투 예시로 jealousy 설명이 끝납니다.
3) ① 뒤: It arises from our desire for something that is not ours. 여기서 It은 ‘남의 것을 원하는’ 감정, 즉 envy를 가리켜야 합니다. 주어진 문장이 없으면 It이 jealousy(잃을까 두려워하는 감정)를 가리키게 되어 내용이 맞지 않습니다.
[정답 근거] 질투 설명과 시기 설명의 경계인 ①이 정답입니다.
[오답 분석]
②~⑤: 이미 It, this desire, envy가 시기를 설명하고 있으므로, 그 뒤에 시기를 처음 정의하는 문장이 오면 순서가 뒤바뀝니다.
[어휘] rage 격분 · threatened 위협을 느끼는 · bond 유대 · obtain 얻다 · positive force 긍정적인 힘', 39, 3),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 요약문 완성 (2점)
[유형 전략] 요약문은 먼저 요약문을 읽고 (A), (B)가 각각 무엇을 묻는지 확인한 뒤 지문을 읽습니다. 지문의 실험 결과나 결론 문장을 요약문의 표현과 짝지어, 같은 뜻을 다른 말로 바꾼(paraphrase) 선택지를 고릅니다.
[풀이 과정]
1) 요약문 분석: 비언어적 신호는 대부분 (A) 만들어지며, 말과 (B)할 때 특히 진짜 태도를 드러낸다.
2) (A) 근거: produced automatically, with little thought, some are produced entirely without our awareness → automatically(자동적으로).
3) (B) 근거: your body language can send a message quite different from your words, your body is telling a different story from your words → 말과 ‘일치하지 않을(inconsistent)’ 때입니다.
[정답 근거] (A) automatically, (B) inconsistent인 ④가 정답입니다.
[오답 분석]
①⑤ consciously(의식적으로)는 without our awareness와 반대입니다.
② (A)는 맞지만 (B) consistent(일치하는)는 반대입니다.
③ (B)는 맞지만 deliberately(의도적으로)는 with little thought와 반대입니다.
[어휘] unfiltered 걸러지지 않은 · give away 드러내다 · frown 얼굴을 찌푸리다 · consistent 일치하는', 40, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q41.webp]]', '[[img:/naesin/csat-h1-mock/r02/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 장문 제목 (2점)
[유형 전략] 장문 제목은 글 전체의 논지를 담아야 합니다. 도입의 일반적 진술과 후반부의 결론을 연결하여 주제를 정리하고, 글의 일부 사례만 다루는 선택지는 지웁니다.
[풀이 과정]
1) 도입: Bannister가 ‘1마일 4분’의 한계를 깼다는 사례.
2) 일반화: 피로를 이기는 열쇠는 스스로 느끼는 한계를 넘어서도록 두뇌를 훈련하는 것이고, 이는 자기 훈련(self-discipline)을 크게 강화한다.
3) 둘째 단락: 피로의 순간이 자기 훈련이 길러지는 결정적인 순간이며, 그때 조금 더 밀어붙이라고 합니다.
[정답 근거] 피로를 넘어서 자기 훈련을 기르라는 ④가 제목으로 적절합니다.
[오답 분석]
① 휴식을 강조하는 것은 글과 반대입니다.
② Bannister는 도입 사례일 뿐, 그의 훈련 방법은 다루지 않습니다.
③ 몸보다 정신(두뇌 훈련)을 강조하므로 반대입니다.
⑤ 현실적인 목표 설정이나 번아웃 예방은 언급되지 않습니다.
[어휘] barrier 장벽 · perceive 인식하다 · drained 기진맥진한 · crucial 결정적인 · burnout 극도의 피로', 41, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q42.webp]]', '[[img:/naesin/csat-h1-mock/r02/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 장문 어휘 (2점)
[유형 전략] 장문 어휘 문항은 단락별 흐름을 잡은 뒤 밑줄 낱말을 하나씩 문맥에 넣어 봅니다. 앞뒤 문장과 인과·대조 관계가 맞지 않는 낱말, 반의어로 바꾸면 자연스러워지는 낱말이 정답입니다.
[풀이 과정]
1) (e)가 있는 문장은 ‘피로의 순간에 조금 더 밀어붙여라’의 예입니다. 앞 문장도 at the end of a long, hard day(길고 힘든 하루의 끝)라는 지친 상황입니다.
2) 같은 문장 안의 longing for sleep(잠을 갈망하는)과도 fresh(상쾌한)는 모순됩니다. fresh → exhausted(지친)로 바꾸어야 합니다.
[정답 근거] (e) fresh가 문맥에 맞지 않으므로 ⑤가 정답입니다.
[오답 분석]
(a) wrong: 불가능하다는 세상의 믿음이 ‘틀렸음’을 증명했다는 뜻으로 맞습니다.
(b) perceive: 스스로 ‘인식하는’ 한계를 넘어서라는 뜻으로 맞습니다.
(c) drained: 정신적으로 ‘지쳐서’ 그만두려는 순간으로 맞습니다.
(d) Add: 힘든 하루 끝에 한 시간을 ‘더하라’는 것으로 push a little further와 맞습니다.
[어휘] on the edge of ~하기 직전에 · long for ~을 갈망하다 · exhausted 기진맥진한', 42, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q43.webp]]', '[[img:/naesin/csat-h1-mock/r02/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 장문 순서 (2점)
[유형 전략] 장문 순서는 등장인물의 행동과 시간의 흐름을 따라갑니다. 각 단락의 첫 문장이 앞 단락의 마지막 장면과 이어지는지(장소 이동, 대화의 응답, 사건의 결과) 확인합니다.
[풀이 과정]
1) (A) 끝: 작년에 크게 다툰 뒤 Nick이 집을 나갔다.
2) (C) 첫 문장 No one had seen Nick or heard from him since then.의 since then은 (A)의 ‘집을 나간 때’를 가리킵니다. Richard가 새벽에 언덕에 올라 해돋이를 봅니다.
3) (D) From the hilltop은 (C)에서 오른 언덕 위를 가리키며, 멀리서 걸어오는 사람이 Nick임을 알아봅니다.
4) (B) Richard jumped up and ran down the hill.은 (D)에서 Nick을 알아본 직후의 행동이며, 재회와 파티로 이야기가 끝납니다.
[정답 근거] (A) → (C) → (D) → (B)이므로 ③이 정답입니다.
[오답 분석]
장소의 이동(침대 → 언덕 → 언덕에서 내려다봄 → 언덕을 뛰어 내려감)을 따라가면 순서가 분명해집니다.
[어휘] toss and turn 뒤척이다 · settle down 진정하다 · stubborn 고집 센 · fetch 가져오다 · figure (멀리 보이는) 사람의 모습', 43, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q44.webp]]', '[[img:/naesin/csat-h1-mock/r02/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 지칭 대상 (2점)
[유형 전략] 지칭 대상 문항은 밑줄 친 대명사가 나오는 문장에서 그 행동을 하는 사람이 누구인지 앞뒤 문맥으로 확인합니다. 대화문 속 you/I는 말하는 사람과 듣는 사람을 기준으로 따집니다.
[풀이 과정]
1) (a) 이미 다 자란 어른인 사람 → 아들 Nick (even though he was a grown man now)
2) (b) Nick이 말하기도 전에 끌어안은 사람 → Richard
3) (c) 새벽에 일어나 언덕에 오른 사람 → Richard
4) (d) 언덕에 앉아 해돋이를 본 사람 → Richard
5) (e) 다가오는 사람을 보고 눈을 비빈 사람 → Richard
[정답 근거] (a)만 Nick이고 나머지는 Richard이므로 ①이 정답입니다.
[오답 분석]
(a)가 있는 문장은 주어가 He(Richard)로 시작하지만, even though절의 he는 ‘다 자란 어른’인 아들을 가리킵니다. 문장 안에서 누가 그 상태에 해당하는지로 판단합니다.', 44, 2),
(@csh1_r02, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r02/q45.webp]]', '[[img:/naesin/csat-h1-mock/r02/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 장문 내용 불일치 (2점)
[유형 전략] 장문 내용 불일치는 선택지 순서가 대체로 글의 사건 순서를 따릅니다. 선택지의 핵심어로 해당 단락을 찾아 인물·행동·시간을 정확히 대조합니다.
[풀이 과정]
1) 선택지와 단락을 대응시킵니다: ①② (A), ③ (C), ④ (D), ⑤ (B).
2) ② (A)에서 They had had a terrible argument the year before라고 했습니다. 작년에는 함께 여행을 간 것이 아니라 심하게 다투었고, 그 뒤 아들이 집을 떠났습니다.
[정답 근거] 작년에 아들과 심하게 다투었으므로 ②가 적절하지 않습니다.
[오답 분석]
① Richard lay awake again, unable to sleep because of his son과 일치합니다.
③ climbed the hill ... watched the sun rise와 일치합니다.
④ someone was coming slowly along the road ... The way the man walked looked very familiar와 일치합니다.
⑤ "Let''s have a party!"와 일치합니다.', 45, 2);

-- 3회: 2022년 5월 고1 이투스 원본(고1 이투스 22 5모)
INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-CSH1-MOCK-R03', 'NAESIN', '고1 수능 영어 독해 모의고사 3회 · 18~45번 변형문제 3회', '2022년 5월 고1 이투스 원본 · 28문항 · 45분 · 62점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 풀이 전략 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @csh1_r03 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-CSH1-MOCK-R03');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글의 목적 (2점)
[유형 전략] 글의 목적은 대개 글 후반부의 요청·안내 문장(please, we ask, I would like to, I am writing to)에 드러납니다. 앞부분의 인사·배경 설명에 끌려가지 말고, 필자가 독자에게 결국 무엇을 해 달라고 하는지 한 문장으로 정리한 뒤 선택지와 맞춰 봅니다.
[풀이 과정]
1) 배경: 시의 예산 삭감과 도서관 수입 감소(the city has cut our budget, the income ... fell).
2) 그 결과: book donations are more valuable to us now than ever before.
3) 요청: please bring them to the front desk at any time during our regular opening hours. please가 들어간 문장이 글의 목적입니다.
[정답 근거] 필요 없는 책을 안내 데스크에 가져다 달라는, 즉 도서 기증을 요청하는 글이므로 ⑤가 정답입니다.
[오답 분석]
① 이미 회원인 사람들에게 보내는 글입니다.
② regular opening hours는 기증할 수 있는 시간일 뿐, 변경 안내가 아닙니다.
③ 대출 규정은 언급되지 않습니다.
④ 예산 삭감은 기증이 필요한 이유일 뿐, 항의하는 글이 아닙니다. 배경 정보를 목적으로 착각하게 만드는 오답입니다.
[어휘] be committed to ~에 전념하다 · current 최신의 · cut a budget 예산을 삭감하다 · pass on 넘겨주다 · charity 자선 단체', 18, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 심경 변화 (2점)
[유형 전략] 심경 변화는 처음 상황과 전환 이후 상황을 나누어 읽습니다. 사건이 바뀌는 지점(when, suddenly, but, then)을 찾고, 그 앞뒤에서 인물의 감정을 드러내는 표현(형용사, 신체 반응, 혼잣말)을 각각 하나씩 골라 두 감정의 짝을 확인합니다.
[풀이 과정]
1) 전반부: Eleanor''s legs went weak, afraid of heights, made her whole body shake, clutching the rope with sweaty hands → 겁에 질린 상태(frightened)입니다.
2) 전환점: Finally, she pushed off and flew over the water.
3) 후반부: "That was amazing!", her face glowing with a huge grin, "Can I do it again?" → 짜릿하고 신이 난 상태(thrilled)입니다.
[정답 근거] 두려움(frightened)에서 짜릿함(thrilled)으로 바뀌므로 ③이 정답입니다.
[오답 분석]
① 처음부터 지루한 것이 아니라 무서워합니다.
② 처음에 침착하지 않았고, 나중에 짜증이 나지도 않습니다.
④ 희망 → 후회는 결과와 반대입니다.
⑤ 나중에 무관심해진 것이 아니라 다시 하고 싶어 합니다.
[어휘] obstacle 장애물 · stream 개울 · shriek 비명을 지르다 · chant 구호를 외치다 · breathtaking 숨이 멎을 듯한', 19, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 필자의 주장 (2점)
[유형 전략] 필자의 주장은 should, must, need to, it is important to, 명령문처럼 당위를 나타내는 문장에 담깁니다. 예시나 일화는 주장을 뒷받침하는 재료이므로, 일화가 끝난 뒤 필자가 일반화하는 문장을 찾아 그 내용과 같은 선택지를 고릅니다.
[풀이 과정]
1) 첫 문장: 무용수의 몸은 모두 다르다(No two dancing bodies are the same).
2) 당위 표현: you should still provide ways to adjust the work, instructors should offer more advanced exercises ... and easier alternatives.
3) 마지막 문장은 그렇게 했을 때의 효과(부상 위험 없이 체력 향상)입니다.
[정답 근거] 개인차에 맞게 더 어렵거나 쉬운 선택지를 제공하라는 ⑤가 주장입니다.
[오답 분석]
① 공연 전 체력 훈련은 주장의 초점이 아닙니다.
② 부상 위험은 결과에서 언급될 뿐, 훈련 강도를 낮추라는 것이 아닙니다. 오히려 준비된 사람에게는 더 어려운 운동을 주라고 합니다.
③ 집단 훈련 자체를 부정하지 않고, 집단 안에서 조정하라고 합니다.
④ 나이와 성별만의 문제가 아니라고(not just a matter of age or gender) 했습니다.
[어휘] demanding 힘든 · technique class 기술 수업 · alternative 대안 · appropriate 적절한 · expose 노출시키다', 20, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 밑줄 함의 (3점)
[유형 전략] 밑줄 친 표현은 비유이므로 글자 그대로 해석하지 않습니다. 밑줄 앞뒤에서 같은 대상을 설명하는 문장을 찾아, 비유 속 단어(밑줄의 핵심 명사·동사)가 글에서 무엇에 대응하는지 짝지은 뒤, 그 의미를 풀어 쓴 선택지를 고릅니다.
[풀이 과정]
1) 밑줄 바로 뒤 설명이 의미를 알려 줍니다: very hard to scratch anything on it(새기기 어렵다) = 배우는 데 오래 걸린다, almost impossible ... to rub it out(지우기 어렵다) = 한 번 익히면 잊지 않는다.
2) 밑줄 앞 문장 "I am slow to learn, and slow to forget what I have learned."가 같은 뜻입니다.
3) 뒤의 일화(판자에 베껴 쓰고, 다시 스크랩북에 옮겨 적음)는 노력으로 기억에 새기는 과정을 보여 줍니다.
[정답 근거] 힘들여 천천히 배운 것은 오래 남는다는 ④가 정답입니다.
[오답 분석]
① 후회는 다루지 않습니다.
② steel(강철)에서 ‘강인함’을 떠올리게 하는 오답입니다.
③ steel에서 ‘단단한 결심’을 떠올리게 하는 오답입니다. 이 글은 결정이 아니라 기억에 관한 글입니다.
⑤ 남의 의견에 흔들리지 않는다는 내용은 없습니다.
[어휘] be struck by ~에 깊은 인상을 받다 · impression 인상, 자국 · erase 지우다 · scratch 긁어 새기다 · carve 새기다', 21, 3),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글의 요지 (2점)
[유형 전략] 요지는 글 전체가 말하려는 한 가지 핵심입니다. 도입의 질문·통념, 대조(however, but), 결론(therefore, in short)을 확인하고, 세부 예시 하나만 다룬 선택지나 지문 단어를 섞어 만든 그럴듯한 선택지는 지웁니다.
[풀이 과정]
1) 출발점: 예술가는 끈질기지만 막연한 감정(persistent but still vague)으로 작품을 시작한다.
2) 과정: 여러 표현을 시도해 보고(trying out different ways of expressing it), 물러서서 감정에 맞는지 묻는다.
3) 결론: Through this back-and-forth process, the emotion becomes clearer to the artist. 감정과 표현 선택이 서로를 분명하게 합니다.
[정답 근거] 표현을 시험하는 과정을 통해 막연한 감정을 분명히 한다는 ②가 요지입니다.
[오답 분석]
① 감정은 작품을 만드는 과정 속에서 분명해지므로 순서가 맞지 않습니다.
③ 관객의 감정은 다루지 않습니다.
④ 처음에는 감정이 막연하다(vague)고 했으므로 반대입니다.
⑤ 안무가·화가·작곡가는 여러 분야의 예일 뿐, 여러 분야를 경험하라는 내용이 아닙니다.
[어휘] persistent 끈질긴 · vague 막연한 · bring ~ into focus ~을 분명히 하다 · chord 화음 · capture 포착하다', 22, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 주제 (2점)
[유형 전략] 주제는 글의 핵심 소재와 그에 대한 필자의 관점을 함께 담은 영어 구로 나타납니다. 반복되는 핵심어를 찾고, 글이 그 소재의 무엇(원인, 효과, 과정, 중요성, 한계)을 다루는지 확인하여 범위가 너무 넓거나 좁은 선택지를 지웁니다.
[풀이 과정]
1) 첫 문장: 과학의 진보는 혼란 속에서 질서를 찾는 것이다(finding order in confusion).
2) 과학의 예(공을 100번 떨어뜨림 → 중력의 법칙)와 언어의 예(뒤죽박죽인 생각 → 짧고 강렬한 명언)가 같은 구조로 나란히 제시됩니다.
3) 마지막 문장 A great quotation, like a scientific law, is clarity born out of confusion.이 두 예를 하나로 묶습니다.
[정답 근거] 혼란이 명료함으로 바뀌는 과정을 다룬 글이므로 ④가 정답입니다.
[오답 분석]
① 과학 자료를 단순화하는 방법은 예의 일부만 다룹니다.
② 언어가 과학 이론에 주는 영향은 다루지 않습니다. 과학과 언어는 비교 대상일 뿐입니다.
③ 명언이 기억하기 쉬운 이유는 다루지 않습니다.
⑤ 자료 수집의 어려움은 언급되지 않습니다.
[어휘] make sense of ~을 이해하다 · unite 통합하다 · fall into a pattern 하나의 형태를 이루다 · in the face of ~에 직면하여 · clarity 명료함', 23, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글의 제목 (2점)
[유형 전략] 제목은 주제를 압축하거나 비유·의문문으로 바꾼 표현입니다. 먼저 글의 주제를 우리말로 정리한 뒤, 그 핵심을 담은 선택지를 고릅니다. 지문에 나온 단어가 그대로 쓰였지만 글의 일부만 다루거나 반대 방향을 말하는 선택지에 주의합니다.
[풀이 과정]
1) 도입: 의사소통의 네 가지 도구(듣기·말하기·읽기·쓰기)를 알고 익히는 것이 중요하다.
2) 양보: 일상에서는 주로 듣기와 말하기만 쓴다.
3) 주장(Even so): we should keep working to improve all four. 결과: you will find yourself gradually gaining an edge.
[정답 근거] 네 가지 의사소통 기술을 모두 익혀 우위를 얻으라는 ①이 제목으로 적절합니다.
[오답 분석]
② 듣기 하나만 강조하지 않습니다.
③ 읽기와 말하기를 비교하지 않습니다.
④ 첫인상은 다루지 않습니다.
⑤ 까다로운 사람을 대하는 상황은 여러 예 가운데 하나일 뿐입니다.
[어휘] rely on ~에 의존하다 · pay off 성과를 내다 · countless 수많은 · gain an edge 우위를 얻다 · appreciation 인식, 이해', 24, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 도표 불일치 (2점)
[유형 전략] 도표 문항은 선택지 문장을 하나씩 도표 수치와 맞춰 봅니다. 비교 표현(more than, twice, the lowest), 순위, 차이 계산이 함정이 되는 경우가 많으므로, 문장에 나온 항목·연도·수치를 도표에서 손가락으로 짚어 가며 확인합니다.
[풀이 과정]
1) ① 2016년 TV 57%로 가장 높고 절반이 넘습니다. (일치)
2) ② 2016년 최저는 social media 18%입니다(print 20%). (일치)
3) ③ news websites는 두 해 모두 2위이지만, 2016년은 28%로 30%를 넘지 않습니다. (불일치)
4) ④ radio 25% → 26%로 약간 높아졌습니다. (일치)
5) ⑤ 감소한 것은 TV(57 → 49)와 print(20 → 16)뿐입니다. (일치)
[정답 근거] 2016년 뉴스 웹사이트 비율은 28%이므로 ③이 도표와 일치하지 않습니다.
[오답 분석]
In both years처럼 두 해를 묶은 진술은 두 해의 수치를 각각 확인해야 합니다. 한 해만 맞는 경우가 흔한 함정입니다.
[어휘] source 출처 · share 비율, 몫', 25, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 내용 불일치 (2점)
[유형 전략] 인물 내용 일치 문항은 선택지 순서가 대체로 글의 순서와 같습니다. 선택지의 핵심어(연도, 장소, 숫자, 고유명사)를 먼저 보고 지문에서 해당 부분을 찾아 정확히 대조합니다. 일부만 바꾼 진술(시기, 횟수, 주체)에 주의합니다.
[풀이 과정]
1) 선택지의 핵심어(나무, 낮, 두세 달, 먹이, 새끼)로 해당 문장을 찾습니다.
2) ④ Tent-building bats feed mainly on fruit; they chew it up and drink the juice. 주로 과일을 먹습니다.
[정답 근거] 곤충이 아니라 과일을 주로 먹으므로 ④가 일치하지 않습니다.
[오답 분석]
① live in areas with plenty of palm or banana trees와 일치합니다.
② protect the bats from the sun and wind while they sleep during the day와 일치합니다.
③ build new shelters every two or three months와 일치합니다.
⑤ mothers leave their young behind when they fly out to find food at night와 일치합니다.
[어휘] settle 자리 잡다 · fold down 접혀 내려오다 · shelter 거처 · breeding 번식 · young (동물의) 새끼', 26, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 안내문 불일치 (2점)
[유형 전략] 안내문은 항목별로 정보가 정리되어 있으므로 선택지의 핵심어(날짜, 시간, 대상, 비용, 신청 방법)로 해당 항목을 바로 찾습니다. 주의 사항·예외 조건(only, not, must)이 함정으로 자주 쓰입니다.
[풀이 과정]
1) ② Time: 10 a.m. – 12 p.m. 오전 10시에 시작합니다.
[정답 근거] 오전 10시에 시작하므로 ②가 일치하지 않습니다.
[오답 분석]
① local historian Andrew Johnson과 일치합니다.
③ covers a distance of about 4 km와 일치합니다.
④ Lemonade and cookies will be served at the end of the walk.와 일치합니다.
⑤ not suitable for children under 10과 일치합니다.
[어휘] cargo 화물 · lock (운하의) 갑문 · on a lead 목줄을 맨 채로 · booking 예약', 27, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 안내문 일치 (2점)
[유형 전략] 안내문 일치 문항은 정답이 하나뿐이므로 선택지를 하나씩 대조하며 틀린 것을 지웁니다. 날짜·요일, 연령, 요금, 포함 여부, 사전 등록 여부처럼 숫자와 조건이 바뀐 부분을 꼼꼼히 확인합니다.
[풀이 과정]
1) ⑤ The class will be rescheduled if the weather is bad. 날씨가 나쁘면 일정을 다시 잡습니다.
[정답 근거] ⑤가 안내문과 일치합니다.
[오답 분석]
① a 90-minute beginner kayaking class로 90분입니다.
② for 5th graders and up으로 5학년부터입니다.
③ Parking is available for $5 per car.로 유료입니다.
④ 참가비에 a waterproof bag이 포함됩니다.
[어휘] introduction 입문 · paddle 노 · waterproof 방수의 · reschedule 일정을 변경하다', 28, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법 (3점)
[유형 전략] 어법 문항은 밑줄마다 확인할 문법 포인트를 먼저 정합니다(동사 자리·수일치, 관계사와 접속사, 분사·to부정사, 병렬 구조, 대명사, 형용사·부사). 특히 문장의 주어와 본동사를 먼저 찾으면 수일치와 준동사 문제를 빠르게 판단할 수 있습니다.
[풀이 과정]
1) ③ so ill 뒤에 ‘너무 아파서 ~하다’라는 결과를 이끄는 so ~ that 구문이 와야 합니다. which 뒤의 절(he had been rushed to the hospital)은 빠진 성분이 없는 완전한 절이므로 관계대명사 which는 올 수 없고 접속사 that이 맞습니다.
2) ① lost, altered와 함께 are에 이어지는 과거분사로 병렬을 이루므로 exaggerated가 맞습니다.
3) ② ‘그날 늦게’라는 부사 later로 맞습니다.
4) ④ had passed through ... and changed: had에 이어지는 과거분사 passed와 병렬을 이루므로 changed가 맞습니다.
5) ⑤ as가 이끄는 부사절의 주어 it(a story)이 단수이므로 travels가 맞습니다.
[정답 근거] so ~ that 구문에 that 대신 which를 쓴 ③이 틀렸습니다(→ that).
어법 포인트: 관계대명사 vs 접속사 that — 관계대명사 뒤에는 주어나 목적어가 빠진 불완전한 절이, 접속사 that 뒤에는 완전한 절이 옵니다. so ~ that(너무 ~해서 …하다)의 that은 접속사입니다.
[어휘] pass on 전하다 · get ~ across ~을 이해시키다 · alter 바꾸다 · in common 공통으로 · specialist 전문의', 29, 3),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘 (2점)
[유형 전략] 어휘 문항은 글의 흐름(원인과 결과, 대조, 예시)을 먼저 잡고, 밑줄 친 낱말이 그 흐름과 반대 방향인지 확인합니다. 밑줄 낱말을 반의어로 바꿔 넣었을 때 문맥이 자연스러워지면 그것이 정답입니다.
[풀이 과정]
1) 글의 결론: relying on others to be there for you all the time is as risky as walking on thin ice. 남에게 계속 기대는 것은 위험하다.
2) 근거: 사람들은 같은 사람을 계속 돕는 데 지치고(grow tired), 도움이 필요한지 의심하기도 한다(compassion fatigue).
3) 따라서 첫 문장에서 남이 모든 것을 해 주길 기대하며 사는 사람은 ‘만족할(satisfied)’ 것이 아니라 ‘실망할(disappointed)’ 것입니다.
[정답 근거] ① satisfied는 글의 흐름과 반대이므로 적절하지 않습니다(→ disappointed).
[오답 분석]
② 위급 상황(emergencies)과 재난 때는 기꺼이 돕는다로 맞습니다.
③ 같은 사람을 계속 돕는 것에 지친다(tired)로 맞습니다.
④ 필요가 여전히 존재하는 것이 ‘분명할’ 때조차 의심한다는 뜻으로 맞습니다.
⑤ 얇은 얼음 위를 걷는 것처럼 ‘위험하다’로 맞습니다.
[어휘] be eager to ~하기를 열망하다 · life''s work 필생의 일 · in need 어려움에 처한 · rely on ~에 의존하다', 30, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 빈칸 문장이 주제문입니다: 상사에게 ‘~이 없으면’ 직원들이 문제를 일으킨다.
2) 첫 번째 연구: 차갑게(coldly) 알린 공장의 절도율이 직원을 배려하며(with obvious concern for the workers) 설명한 공장의 약 두 배였다.
3) 두 번째 연구: 상사가 공정하고 친절하게 대하도록 교육받자 절도가 절반으로 줄었다. 상사를 묘사한 heartless, insensitive, cold ↔ kind의 대비가 결정적입니다.
[정답 근거] 상사에게 부족했던 것은 직원에 대한 배려와 연민(compassion)이므로 ①이 정답입니다.
[오답 분석]
② 창의성은 관련이 없습니다.
③ 야망은 언급되지 않습니다.
④ 권위가 없어서가 아니라 차갑고 무정해서 문제가 생겼습니다.
⑤ 효율성이 부족해서 절도가 늘어난 것이 아닙니다.
[어휘] theft 절도 · partial 부분적인 · supervisor 관리자 · insensitive 무신경한 · roughly 대략', 31, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 대조 구조: 호흡이 잘 맞을 때는 음을 많이 연주하지 않아도 된다 ↔ 호흡이 무너지면 모두 과하게 연주하려 한다(overplay).
2) 농구 비유: 팀으로 움직이지 못하면 각자 제멋대로 움직인다(everyone starts doing their own thing).
3) 빈칸 문장 [ ] is no substitute for skilled execution.은 ‘~이 숙련된 실행을 대신할 수 없다’는 뜻이므로, 빈칸에는 호흡이 무너졌을 때 나타나는 ‘과하고 분주한 움직임’이 들어가야 합니다.
[정답 근거] 과하게 연주하고 제멋대로 움직이는 것을 가리키는 ② frantic activity(정신없이 많은 움직임)가 정답입니다.
[오답 분석]
① 타고난 재능은 언급되지 않습니다.
③ 신중한 계획은 오히려 연습한 플레이를 따르는 것과 가깝습니다.
④ 비싼 장비는 관련이 없습니다.
⑤ 팀 정신은 필자가 바람직하다고 보는 함께 움직이는 상태와 가까워, 이것이 숙련된 실행을 대신할 수 없다고 하면 글의 논지와 어긋납니다.
[어휘] toss off 쉽게 해내다 · hit the mark 적중하다 · tempted to ~하고 싶어지는 · instinctive 본능적인 · substitute 대체물', 32, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 원리: 위험에 주의가 쏠리면, 그 위험이 작다는 정보 때문이라도 두려움이 커진다.
2) 적용(The same may hold for corrections of false reports): 허위 보도를 정정하면 사람들의 주의가 다시 그 보도로 향한다.
3) 따라서 정정은 의도와 달리 ‘허위로 보도된 내용이 사실일지도 모른다’는 인상을 강하게 만듭니다.
[정답 근거] 정정 보도가 오히려 허위 내용이 사실일 수 있다는 인상을 강화한다는 ④가 정답입니다.
[오답 분석]
① 공식 발표를 무시한다는 내용은 없습니다.
② 원래 보도가 꼼꼼히 확인되었다는 것은 ‘허위 보도’라는 전제와 맞지 않습니다.
③ 새 정보가 더 믿을 만하다면 정정이 효과가 있어야 하므로 글의 논지와 반대입니다.
⑤ 위험이 사라졌다는 인상은 정정이 의도한 효과이지, 글이 말하는 역효과가 아닙니다.
[어휘] shrink 줄어들다 · puzzling 이해하기 어려운 · come true 실현되다 · hold for ~에도 적용되다 · correction 정정', 33, 3),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 앞 문장들의 흐름: 공공·민간 부문의 역할은 여전히 논란거리이며(remain controversial), 완전한 합의가 없다(no complete agreement).
2) 빈칸 문장은 이 일반론을 대중교통 분야에 적용한 것이며, despite(~에도 불구하고) 30년간의 개혁 노력이 이어집니다. 노력에도 ‘불구하고’ 남아 있는 것은 혼란과 의견 불일치입니다.
3) 빈칸 뒤 문장도 독점에서 완전한 규제 완화까지 온갖 방식을 시도했다고 하여, 하나의 답이 정해지지 않았음을 보여 줍니다.
[정답 근거] 혼란과 의견 불일치가 여전히 계속된다는 ②가 정답입니다.
[오답 분석]
① 경쟁이 서비스 질을 떨어뜨렸다는 근거는 없습니다.
③ 정부의 투자 중단은 언급되지 않습니다.
④ 민간 기업이 완전히 장악했다는 것은 여러 방식을 시도했다는 내용과 맞지 않습니다.
⑤ 승객의 요금 인하 요구는 언급되지 않습니다.
[어휘] sophisticated 정교한 · controversial 논란이 많은 · sector 부문 · monopoly 독점 · compromise 절충안', 34, 3),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 무관한 문장 (2점)
[유형 전략] 무관한 문장 문항은 첫 문장(주제문)이 제시한 소재와 방향을 확인한 뒤 각 문장이 그 방향을 이어 가는지 봅니다. 정답 문장은 지문의 핵심어를 일부 포함하지만 논점이 다른 쪽(다른 대상, 다른 측면)으로 벗어난 경우가 많습니다.
[풀이 과정]
1) 도입: 괴테의 통찰(남의 훌륭한 점 앞에서는 사랑만이 도움이 된다)을 활용해 시기심을 줄일 수 있다.
2) ②~⑤는 그 방법(진심 어린 칭찬)과 효과(시기심 → 칭찬과 인정 → 개인의 장점 → 존중받음)를 차례로 설명합니다.
3) ①은 시기심이 소셜 미디어 사용 시간과 관련 있다는 연구 결과로, ‘시기심을 줄이는 방법’이라는 흐름에서 벗어납니다.
[정답 근거] 시기심의 원인을 다룬 ①이 흐름과 관계없습니다.
[오답 분석]
정답이 첫 번째 위치에 오는 경우도 있습니다. 주제문 바로 뒤라도 주제문이 제시한 방향(방법)을 이어 가는지 확인합니다.
[어휘] remark 말하다 · insight 통찰 · sincere 진심 어린 · compliment 칭찬 · recognition 인정', 35, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글의 순서 (2점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 세균의 모양(막대, 원, 나선).
2) (B)의 And all of them은 앞에서 말한 여러 모양의 세균을 받아 크기에 대한 정보를 덧붙이고, 세균에 관한 나쁜 소식을 소개합니다.
3) (A)의 Stories like these는 (B)의 ‘나쁜 소식’을 가리키며, 그 결과 항생제·항균 비누 사용이 늘었다고 합니다.
4) (C)의 But은 (A)의 ‘세균은 나쁘다는 믿음’과 대조하여, 실제로는 대부분 이롭다는 결론을 제시합니다.
[정답 근거] 주어진 글 → (B) → (A) → (C)이므로 ②가 정답입니다.
[오답 분석]
(A)를 먼저 놓으면 Stories like these가 가리킬 이야기가 없습니다.
(C)를 (A)보다 앞에 놓으면 But이 대조할 ‘세균은 나쁘다’는 믿음이 충분히 나오지 않은 상태가 됩니다.
[어휘] rod 막대 · naked eye 육안 · in turn 결국, 이번에는 · overlook 간과하다 · villain 악당', 36, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글의 순서 (3점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 도형(diagram)이 그것이 나타내는 대상의 은유가 될 수 있는가?
2) (B)의 In my view, yes.는 주어진 글의 질문에 대한 대답이며, 분자 구조 그림을 예로 듭니다.
3) (C)의 The same is true of ~는 (B)의 주장(그림은 은유가 될 수 있다)을 인물 초상화에도 적용하면서 모나리자에 대한 질문을 던집니다.
4) (A)의 To answer this는 (C)의 질문에 답하며, 은유의 본질로 마무리합니다.
[정답 근거] 주어진 글 → (B) → (C) → (A)이므로 ③이 정답입니다.
[오답 분석]
(C)가 주어진 글 바로 뒤에 오면 The same is true가 받을 주장이 없습니다. 주어진 글은 질문일 뿐입니다.
(A)의 the realistic painting은 (C)의 모나리자가 나온 뒤에야 가리킬 대상이 생깁니다.
[어휘] count as ~로 여겨지다 · represent 나타내다 · molecule 분자 · reinterpret 재해석하다 · sit for (초상화의) 모델이 되다', 37, 3),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 문장 삽입 (2점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장: 다른 문화에서는 자아가 심장(heart)에 있다고 믿었다. In other cultures는 앞에서 특정 문화(이집트, 그리스)의 예가 나온 뒤에 이어진다는 단서입니다.
2) ④ 뒤: That is why we still learn things "by heart" and love someone "with all our heart." That이 가리키는 내용은 ‘심장에서 생각한다는 믿음’이어야 하는데, 이 내용은 주어진 문장에만 있습니다.
3) ④ 앞: 아리스토텔레스까지 ‘뇌를 낮게 본’ 예가 이어집니다.
[정답 근거] 뇌를 하찮게 본 예들 뒤, That is why ~ heart 문장 앞인 ④가 정답입니다.
[오답 분석]
①: 근대의 생각이 착각이라는 말 바로 뒤로, 아직 ‘다른 시대·문화’의 예가 하나도 나오지 않았습니다.
②: 이집트의 예(The ancient Egyptians, for instance)가 시작되기 전이라 In other cultures의 ‘다른’이 가리킬 대상이 없습니다.
③: 이집트와 그리스의 예 사이입니다. 여기에 넣으면 Aristotle shared this low opinion의 this가 이집트의 생각(뇌를 쓸모없다고 봄)을 가리키지 못하게 됩니다.
⑤: That is why ~ heart 문장 뒤입니다. 그러면 That이 아리스토텔레스의 생각(뇌는 피를 식힌다)을 가리키게 되어 by heart 표현과 이어지지 않습니다.
[어휘] associate A with B A를 B와 연관 짓다 · skull 두개골 · illusion 착각 · preserve 보존하다 · organ 장기', 38, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 문장 삽입 (3점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장의 In another study(또 다른 연구에서)와 students는 첫 번째 연구가 끝난 뒤, 학생들의 반응이 나오기 전에 들어가야 한다는 단서입니다.
2) ③ 앞: 수술 환자 연구의 결과(3일 일찍 퇴원)로 첫 번째 연구가 끝납니다.
3) ③ 뒤: Afterward, the students reported ~. the students가 처음 나오는데, 주어진 문장이 없으면 어떤 학생인지 알 수 없습니다.
[정답 근거] 첫 번째 연구가 끝나고 학생들의 반응이 시작되기 전인 ③이 정답입니다.
[오답 분석]
①②: 첫 번째 연구가 소개되기 전이거나 진행 중이므로 another study가 올 수 없습니다.
④⑤: the students가 이미 등장한 뒤이므로, 그 연구를 소개하는 문장이 뒤에 오면 순서가 맞지 않습니다.
[어휘] perception 지각 · surgery 수술 · medication 약물 · alert 기민한, 정신이 또렷한 · blood pressure 혈압', 39, 3),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 요약문 완성 (2점)
[유형 전략] 요약문은 먼저 요약문을 읽고 (A), (B)가 각각 무엇을 묻는지 확인한 뒤 지문을 읽습니다. 지문의 실험 결과나 결론 문장을 요약문의 표현과 짝지어, 같은 뜻을 다른 말로 바꾼(paraphrase) 선택지를 고릅니다.
[풀이 과정]
1) 요약문 분석: 조직이 배우려면 과거의 오류를 개선의 (A)로 다루어야 하며, (B)해야 할 것으로 여겨서는 안 된다.
2) (A) 근거: reports should be treated as information ... to make constructive changes, Errors should be regarded as signals that a change in practice is needed → 개선의 ‘기회(opportunity)’.
3) (B) 근거: not suppressed as unwelcome whistle-blowing, If they are hidden at all costs ... nothing will be learned → ‘숨길(conceal)’ 것이 아닙니다.
[정답 근거] (A) opportunity, (B) conceal인 ①이 정답입니다.
[오답 분석]
② (B) report(보고하다)는 글이 권하는 행동이므로 rather than 뒤에 올 수 없습니다.
③④ (A) threat(위협)는 오류를 변화의 신호로 보라는 내용과 반대입니다.
⑤ excuse(변명)는 근거가 없습니다.
[어휘] acknowledge 인정하다 · constructive 건설적인 · at all costs 무슨 수를 써서라도 · in practice 실제로', 40, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q41.webp]]', '[[img:/naesin/csat-h1-mock/r03/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 장문 제목 (2점)
[유형 전략] 장문 제목은 글 전체의 논지를 담아야 합니다. 도입의 일반적 진술과 후반부의 결론을 연결하여 주제를 정리하고, 글의 일부 사례만 다루는 선택지는 지웁니다.
[풀이 과정]
1) 첫 단락: 불의 발견 → 농업 → 도시화로 인간 활동이 야생 동물의 서식지를 파괴해 왔다(규모).
2) 둘째 단락: 서식지 변화는 규모만큼 형태(pattern)도 중요하다. 같은 면적이라도 작게 쪼개지면 피해가 훨씬 크다.
3) 두 단락을 묶으면 ‘인간 활동과 서식지의 변화(규모와 형태)’가 주제입니다.
[정답 근거] 인간 활동에 따른 서식지 변화를 다룬 ⑤가 제목으로 적절합니다.
[오답 분석]
① 농업은 서식지를 파괴한 주된 원인이므로 반대입니다.
② 동물원은 언급되지 않습니다.
③ 도시화는 위협으로 소개되므로 반대입니다.
④ 불은 도입부의 출발점일 뿐입니다.
[어휘] habitat 서식지 · hemisphere 반구 · urbanization 도시화 · predator 포식자 · territory 영역', 41, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q42.webp]]', '[[img:/naesin/csat-h1-mock/r03/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 장문 어휘 (2점)
[유형 전략] 장문 어휘 문항은 단락별 흐름을 잡은 뒤 밑줄 낱말을 하나씩 문맥에 넣어 봅니다. 앞뒤 문장과 인과·대조 관계가 맞지 않는 낱말, 반의어로 바꾸면 자연스러워지는 낱말이 정답입니다.
[풀이 과정]
1) (b)가 있는 문장은 In recent times로 시작해, 농업에 이어 도시화도 서식지에 대한 큰 위협이 되었다는 흐름입니다. almost equally(거의 똑같이)는 앞에서 말한 농업만큼이라는 뜻입니다.
2) 콜론(:) 뒤의 설명(도시가 공간을 차지하고, 도로는 더 많은 공간을 차지한다)도 위협이 크다는 근거입니다. 따라서 minor(사소한)가 아니라 serious(심각한)가 와야 합니다.
[정답 근거] (b) minor는 serious로 바꾸어야 하므로 ②가 정답입니다.
[오답 분석]
(a) continues: 과거에 북반구를 휩쓸었고 오늘날 열대 지방에서 ‘계속된다’로 맞습니다.
(c) kept: 넓은 지역이 온전하게 ‘유지되면’ 기능이 이어진다로 맞습니다.
(d) severe: 작게 쪼개지면 영향이 훨씬 ‘심각하다’로 맞습니다.
(e) harder: 방해를 많이 받아 먹고 번식하기가 ‘더 어렵다’로 맞습니다.
[어휘] speed up 가속하다 · scale 규모 · isolated 고립된 · disturbance 방해 · breed 번식하다', 42, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q43.webp]]', '[[img:/naesin/csat-h1-mock/r03/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 장문 순서 (2점)
[유형 전략] 장문 순서는 등장인물의 행동과 시간의 흐름을 따라갑니다. 각 단락의 첫 문장이 앞 단락의 마지막 장면과 이어지는지(장소 이동, 대화의 응답, 사건의 결과) 확인합니다.
[풀이 과정]
1) (A) 끝: Linda는 메달을 행운의 부적으로 여겼다.
2) (D) Whenever someone asked what the Chinese characters on it meant에서 it은 (A)의 메달이며, 사람들이 대화 중에 뜻을 묻는 장면으로 (A)의 interesting conversations와 이어집니다.
3) (C) Then came the night ~로 시간이 흐른 뒤 대사관 만찬 장면이 나오고, 대사의 아내에게 읽어 달라고 부탁합니다.
4) (B) The wife hesitated는 (C)의 부탁에 대한 반응이며, 메달이 개 이름표였다는 결말로 끝납니다.
[정답 근거] (A) → (D) → (C) → (B)이므로 ⑤가 정답입니다.
[오답 분석]
(B)의 The wife는 (C)에서 대사의 아내(his wife)가 등장한 뒤에만 쓸 수 있습니다.
(C)의 Then came the night는 메달에 얽힌 일상(D)이 먼저 나온 뒤 특별한 사건을 시작할 때 자연스럽습니다.
[어휘] attached to ~에 애착을 가진 · charm 부적 · embassy 대사관 · ambassador 대사 · reluctance 꺼림', 43, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q44.webp]]', '[[img:/naesin/csat-h1-mock/r03/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 지칭 대상 (2점)
[유형 전략] 지칭 대상 문항은 밑줄 친 대명사가 나오는 문장에서 그 행동을 하는 사람이 누구인지 앞뒤 문맥으로 확인합니다. 대화문 속 you/I는 말하는 사람과 듣는 사람을 기준으로 따집니다.
[풀이 과정]
1) (a) 메달을 행운의 부적으로 여기게 된 사람 → Linda
2) (b) 수년간 이름표를 자랑스럽게 보여 주던 사람 → Linda
3) (c) 조용히 듣다가 글자의 뜻을 다 아는 듯이 메달을 힐끗 본 사람 → 대사의 아내(His wife)
4) (d) 대사의 아내에게 고개를 돌린 사람 → Linda
5) (e) 글자의 뜻을 전혀 몰랐던 사람 → Linda
[정답 근거] (c)만 대사의 아내이고 나머지는 Linda이므로 ③이 정답입니다.
[오답 분석]
(d) 바로 뒤의 turned to the ambassador''s wife에서 (d)는 아내에게 말을 거는 Linda임을 알 수 있습니다.
다섯 개가 모두 she이므로 성별로는 가를 수 없습니다. 대명사 바로 앞의 주어(His wife, who had been listening quietly)를 확인해야 합니다.', 44, 2),
(@csh1_r03, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r03/q45.webp]]', '[[img:/naesin/csat-h1-mock/r03/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 장문 내용 불일치 (2점)
[유형 전략] 장문 내용 불일치는 선택지 순서가 대체로 글의 사건 순서를 따릅니다. 선택지의 핵심어로 해당 단락을 찾아 인물·행동·시간을 정확히 대조합니다.
[풀이 과정]
1) ④ (C) Linda was invited to a formal dinner at an embassy. During the meal, ~. 점심 모임이 아니라 저녁 만찬(formal dinner)이었습니다.
[정답 근거] ④가 글의 내용과 일치하지 않습니다.
[오답 분석]
① Linda bought an old bronze medallion at a street market과 일치합니다.
② her personal good luck charm과 일치합니다.
③ say that it was an ancient blessing과 일치합니다.
⑤ she turned to the ambassador''s wife. "Would you read it for me instead?"와 일치합니다.', 45, 2);

-- 4회: 2025년 8월 고1 이투스 원본(고1 이투스 25 8모)
INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-CSH1-MOCK-R04', 'NAESIN', '고1 수능 영어 독해 모의고사 4회 · 18~45번 변형문제 4회', '2025년 8월 고1 이투스 원본 · 28문항 · 45분 · 62점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 풀이 전략 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @csh1_r04 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-CSH1-MOCK-R04');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글의 목적 (2점)
[유형 전략] 글의 목적은 대개 글 후반부의 요청·안내 문장(please, we ask, I would like to, I am writing to)에 드러납니다. 앞부분의 인사·배경 설명에 끌려가지 말고, 필자가 독자에게 결국 무엇을 해 달라고 하는지 한 문장으로 정리한 뒤 선택지와 맞춰 봅니다.
[풀이 과정]
1) 도입: I am writing to share a concern about our school''s lunch period. 점심시간에 관한 문제 제기입니다.
2) 문제 상황: 30분 중 대부분을 줄 서는 데 써서 제대로 먹지 못하고, 아예 점심을 거르는 학생도 있다.
3) 제안: extending the lunch period by 15 minutes, to 45 minutes in total, would go a long way toward solving these problems. 이것이 글의 목적입니다.
[정답 근거] 점심시간을 45분으로 늘려 달라고 건의하는 글이므로 ③이 정답입니다.
[오답 분석]
① 메뉴는 언급되지 않습니다.
② 위생 문제는 다루지 않습니다.
④ 긴 대기 줄은 문제의 원인일 뿐, 인력 충원을 해결책으로 제시하지 않았습니다. 문제 상황을 목적으로 착각하게 만드는 오답입니다.
⑤ 점심을 거르는 학생은 문제의 한 예일 뿐입니다.
[어휘] concern 걱정, 우려 · cafeteria 구내식당 · skip 거르다 · extend 연장하다 · go a long way toward ~에 크게 도움이 되다', 18, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 심경 변화 (2점)
[유형 전략] 심경 변화는 처음 상황과 전환 이후 상황을 나누어 읽습니다. 사건이 바뀌는 지점(when, suddenly, but, then)을 찾고, 그 앞뒤에서 인물의 감정을 드러내는 표현(형용사, 신체 반응, 혼잣말)을 각각 하나씩 골라 두 감정의 짝을 확인합니다.
[풀이 과정]
1) 전반부: leaped out of bed, the best day of his life, could hardly wait, Humming to himself, dashed downstairs → 들뜨고 신난 상태(excited)입니다.
2) 전환점: But ~ the sky outside had turned dark gray. 그리고 엄마가 워터파크가 문을 닫았다고 알립니다.
3) 후반부: shoulders dropped, let out a deep sigh, trudged slowly back → 실망한 상태(disappointed)입니다.
[정답 근거] 들뜬 마음(excited)에서 실망(disappointed)으로 바뀌므로 ⑤가 정답입니다.
[오답 분석]
① 처음에 지루하지 않았고, 나중에 기뻐하지도 않습니다.
② 긴장 → 안도는 흐름과 반대입니다.
③ 화가 난 상태로 시작하지 않습니다.
④ 걱정 → 기쁨은 흐름과 정반대입니다.
[어휘] leap 뛰어오르다 · hum 콧노래를 부르다 · dash 서둘러 가다 · beat against ~에 세차게 부딪치다 · sigh 한숨', 19, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 필자의 주장 (2점)
[유형 전략] 필자의 주장은 should, must, need to, it is important to, 명령문처럼 당위를 나타내는 문장에 담깁니다. 예시나 일화는 주장을 뒷받침하는 재료이므로, 일화가 끝난 뒤 필자가 일반화하는 문장을 찾아 그 내용과 같은 선택지를 고릅니다.
[풀이 과정]
1) 통념 제시: 많은 학생이 늦게까지 공부하면 성적이 오른다고 믿는다.
2) 반박 근거: 수면 부족은 학습과 기억을 해치고, 7시간 미만으로 자면 집중력과 점수가 떨어진다.
3) 주장: Students should therefore make sleep a priority instead of trading it for late-night study sessions. therefore와 should가 함께 쓰인 문장이 주장입니다.
[정답 근거] 늦게까지 공부하기보다 충분한 수면을 우선하라는 ②가 정답입니다.
[오답 분석]
① 통념에 해당하는 내용으로 필자의 주장과 반대입니다.
③ 공부 장소는 언급되지 않습니다.
④ 등교 시간 조정은 학교 정책에 관한 것으로 글에 나오지 않습니다.
⑤ 낮잠은 다루지 않습니다.
[어휘] convinced 확신하는 · dedication 헌신 · a growing body of research 점점 늘어나는 연구 · absorb 흡수하다 · priority 우선 사항', 20, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 밑줄 함의 (3점)
[유형 전략] 밑줄 친 표현은 비유이므로 글자 그대로 해석하지 않습니다. 밑줄 앞뒤에서 같은 대상을 설명하는 문장을 찾아, 비유 속 단어(밑줄의 핵심 명사·동사)가 글에서 무엇에 대응하는지 짝지은 뒤, 그 의미를 풀어 쓴 선택지를 고릅니다.
[풀이 과정]
1) whispers(속삭임)는 크게 드러나지 않는 작은 신호를 비유합니다. symphony는 오케스트라 단원 전체를 가리킵니다.
2) 밑줄 뒤 설명: They need to notice fine details: the way a violinist''s bow movements reveal years of practice, or the way a flutist''s breathing shows a rare level of skill.
3) 마지막 문장의 such details of individual skill(개인 기량의 세부 사항)이 밑줄을 다시 풀어 쓴 표현입니다.
[정답 근거] 각 연주자의 기량을 드러내는 미묘한 신호를 뜻하므로 ①이 정답입니다.
[오답 분석]
② 템포에 대한 의견 차이는 언급되지 않습니다.
③ whispers를 ‘조용한 부분’으로 글자 그대로 해석한 오답입니다.
④ 오히려 과거의 엄격한 방식이 바뀌었다는 내용입니다.
⑤ 단원들의 불만은 다루지 않습니다.
[어휘] conductor 지휘자 · carry out 수행하다 · vision 구상 · bow (현악기의) 활 · overlook 간과하다', 21, 3),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 요지 (2점)
[유형 전략] 요지는 글 전체가 말하려는 한 가지 핵심입니다. 도입의 질문·통념, 대조(however, but), 결론(therefore, in short)을 확인하고, 세부 예시 하나만 다룬 선택지나 지문 단어를 섞어 만든 그럴듯한 선택지는 지웁니다.
[풀이 과정]
1) 대조: 혼자 운동하는 사람은 동기가 약하고 자주 빠진다 ↔ 함께 운동하는 사람은 더 꾸준하다.
2) 이유: 집단 속 관계가 책임감과 격려를 만들고, 정서적 지지와 즐거움을 준다.
3) 결론: this social side of exercise has a major influence on participation, 그리고 우정이 운동을 계속하는 주된 이유가 된다.
[정답 근거] 함께 운동하며 생긴 사회적 유대가 운동을 꾸준히 하게 한다는 ④가 요지입니다.
[오답 분석]
① 혼자 하는 운동은 오히려 동기가 약하다고 했습니다.
② 전문가의 지도는 언급되지 않습니다.
③ across all age groups and fitness levels(모든 연령과 체력 수준)를 이용한 오답입니다.
⑤ 경쟁심은 언급되지 않고, 격려와 책임감, 우정을 이유로 듭니다.
[어휘] revealing 의미 있는 사실을 드러내는 · stick with ~을 계속하다 · mutual 서로의 · participation 참여', 22, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글의 주제 (2점)
[유형 전략] 주제는 글의 핵심 소재와 그에 대한 필자의 관점을 함께 담은 영어 구로 나타납니다. 반복되는 핵심어를 찾고, 글이 그 소재의 무엇(원인, 효과, 과정, 중요성, 한계)을 다루는지 확인하여 범위가 너무 넓거나 좁은 선택지를 지웁니다.
[풀이 과정]
1) 첫 문장: 아이들이 읽기를 배우는 매우 다른 두 가지 방식이 있다.
2) 전통적 방식(phonics)의 특징과 장단점 → by contrast → 총체적 언어 접근법(whole language)의 특징과 장단점이 대등하게 제시됩니다.
3) 마지막 문장도 두 방법이 서로 다른 관점에 기반한다고 정리합니다.
[정답 근거] 대조되는 두 가지 읽기 교수법을 비교하는 글이므로 ③이 정답입니다.
[오답 분석]
① 두 방법을 섞으라는 결론은 없습니다. 비교 글에서 가장 흔한 함정입니다.
② 조기 교육의 중요성은 다루지 않습니다.
④ 교사가 겪는 문제는 각 방법의 단점으로 일부만 언급됩니다.
⑤ 흥미(interested)는 한 방법의 장점일 뿐입니다.
[어휘] blend 섞다, 합치다 · track 추적하다 · mechanical 기계적인 · in context 문맥 속에서 · systematic 체계적인', 23, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글의 제목 (2점)
[유형 전략] 제목은 주제를 압축하거나 비유·의문문으로 바꾼 표현입니다. 먼저 글의 주제를 우리말로 정리한 뒤, 그 핵심을 담은 선택지를 고릅니다. 지문에 나온 단어가 그대로 쓰였지만 글의 일부만 다루거나 반대 방향을 말하는 선택지에 주의합니다.
[풀이 과정]
1) 과거: 배터리 연구는 에너지 용량과 충전 시간에 집중했다.
2) 전환(however, Yet): 소비자는 기술 사양이 아니라 전체적인 소유 경험으로 EV를 평가한다.
3) 결론: 배터리 기술은 사람들이 차를 쓰고 즐기는 방식을 개선해야만 널리 받아들여진다.
[정답 근거] 수치를 넘어 운전 경험을 개선하는 배터리를 강조하는 ⑤가 제목으로 적절합니다.
[오답 분석]
① 배터리 화학의 혁신만으로 판매가 늘 것이라는 생각은 과거의 믿음으로, 글이 반박하는 내용입니다.
② 내연기관차와의 비교는 없습니다.
③ 초기 연구가 언급되지만 역사 개관이 글의 초점은 아닙니다.
④ 정부 정책은 언급되지 않습니다.
[어휘] breakthrough 획기적 발전 · persuade 설득하다 · ownership 소유 · priority 우선순위 · acceptance 수용', 24, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 도표 불일치 (2점)
[유형 전략] 도표 문항은 선택지 문장을 하나씩 도표 수치와 맞춰 봅니다. 비교 표현(more than, twice, the lowest), 순위, 차이 계산이 함정이 되는 경우가 많으므로, 문장에 나온 항목·연도·수치를 도표에서 손가락으로 짚어 가며 확인합니다.
[풀이 과정]
1) ① 미식축구는 나이에 따라 증가하고 55–64세가 최고이지만, 수치는 85%가 아니라 89%입니다. (불일치)
2) ② 농구: 64 → 70(증가) → 66 → 54 → 51(계속 감소). (일치)
3) ③ 18–24세 두 종목 모두 64%. (일치)
4) ④ 35–44세 80 − 66 = 14%p. (일치)
5) ⑤ 45–54세 54%, 55–64세 51%로 둘 다 55% 미만. (일치)
[정답 근거] 55–64세 미식축구 비율은 89%이므로 ①이 일치하지 않습니다.
[오답 분석]
한 문장에 경향(increased with age)과 수치(85 percent)가 함께 있으면 둘 다 확인해야 합니다. 경향이 맞아도 수치가 틀릴 수 있습니다.
[어휘] age group 연령대 · gap 격차', 25, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 불일치 (2점)
[유형 전략] 인물 내용 일치 문항은 선택지 순서가 대체로 글의 순서와 같습니다. 선택지의 핵심어(연도, 장소, 숫자, 고유명사)를 먼저 보고 지문에서 해당 부분을 찾아 정확히 대조합니다. 일부만 바꾼 진술(시기, 횟수, 주체)에 주의합니다.
[풀이 과정]
1) ② She studied at Barnard College, where she was the only Black student at the time. 당시 유일한 흑인 학생이었습니다.
[정답 근거] Barnard 대학에서 유일한 흑인 학생이었으므로 ②가 일치하지 않습니다.
[오답 분석]
① In the 1920s, she moved to New York City와 일치합니다.
③ traveled throughout the American South and the Caribbean, collecting folk tales와 일치합니다.
④ took jobs as a substitute teacher and a maid와 일치합니다.
⑤ In the 1970s, the writer Alice Walker helped bring Hurston''s work back to public attention과 일치합니다.
[어휘] flowering 개화, 융성 · folk tale 민담 · substitute teacher 대체 교사 · revival 부활', 26, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 안내문 불일치 (2점)
[유형 전략] 안내문은 항목별로 정보가 정리되어 있으므로 선택지의 핵심어(날짜, 시간, 대상, 비용, 신청 방법)로 해당 항목을 바로 찾습니다. 주의 사항·예외 조건(only, not, must)이 함정으로 자주 쓰입니다.
[풀이 과정]
1) ② Registration Deadline: March 11, 6 p.m.이고 행사는 Saturday, March 15입니다. 마감일은 행사 나흘 전입니다.
[정답 근거] 신청 마감일은 3월 11일로 행사 당일(3월 15일)이 아니므로 ②가 일치하지 않습니다.
[오답 분석]
① Saturday, March 15, 10 a.m.과 일치합니다.
③ No experience needed와 일치합니다.
④ What''s Included의 A glass container and small plants와 일치합니다.
⑤ Each participant will take home a care guide.와 일치합니다.
[어휘] brighten up 밝게 하다 · container 용기 · florist 플로리스트, 꽃집 주인 · provide 제공하다', 27, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 안내문 일치 (2점)
[유형 전략] 안내문 일치 문항은 정답이 하나뿐이므로 선택지를 하나씩 대조하며 틀린 것을 지웁니다. 날짜·요일, 연령, 요금, 포함 여부, 사전 등록 여부처럼 숫자와 조건이 바뀐 부분을 꼼꼼히 확인합니다.
[풀이 과정]
1) ① Students may choose only one class. 한 가지 수업만 고를 수 있습니다.
[정답 근거] ①이 안내문과 일치합니다.
[오답 분석]
② Tuesdays and Thursdays로 평일입니다.
③ Online only로 온라인 등록만 됩니다.
④ can be paid in two installments로 두 번에 나누어 낼 수 있습니다.
⑤ 전액 환불은 9월 25일까지이고, 10월 3일까지는 50%만 환불됩니다.
[어휘] pottery 도예 · tuition 수강료 · cancellation 취소 · refund 환불', 28, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어법 (3점)
[유형 전략] 어법 문항은 밑줄마다 확인할 문법 포인트를 먼저 정합니다(동사 자리·수일치, 관계사와 접속사, 분사·to부정사, 병렬 구조, 대명사, 형용사·부사). 특히 문장의 주어와 본동사를 먼저 찾으면 수일치와 준동사 문제를 빠르게 판단할 수 있습니다.
[풀이 과정]
1) ③ it is ~ that 강조 구문입니다. it is와 that 사이에 강조할 말(the most symmetrical two-legged and four-legged animals)을 넣고, 뒤에는 나머지 부분(can move the fastest)이 옵니다. 이 자리의 what은 that으로 바꾸어야 합니다.
2) ① 전치사 of의 목적어 자리이므로 동명사 being이 맞습니다. a shape는 동명사의 의미상 주어입니다.
3) ② rely on symmetry to propel ~: ‘~하기 위해’의 to부정사로 동사원형 propel이 맞습니다.
4) ④ 전치사 of 뒤에서 not becoming(되지 않는 것)으로 동명사가 맞습니다.
5) ⑤ 주어는 An animal(단수)이고 with one leg much longer than the others는 수식어이므로 단수 동사 is가 맞습니다.
[정답 근거] it is ~ that 강조 구문의 that 자리에 쓴 ③ what이 틀렸습니다(→ that).
어법 포인트: 강조 구문 it is A that ~ — A(주어·목적어·부사구)를 it is와 that 사이에 두어 강조합니다. that 자리에 what을 쓰지 않습니다. what은 선행사를 품은 관계대명사라 앞에 명사(animals)가 있으면 쓸 수 없습니다. it is와 that을 지우고 남은 문장(the most symmetrical ... animals can move the fastest)이 완전하면 강조 구문입니다.
[어휘] superior 우월한 · motor ability 운동 능력 · prey 먹이, 사냥감 · favor 선호하다 · fierce 치열한', 29, 3),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어휘 (2점)
[유형 전략] 어휘 문항은 글의 흐름(원인과 결과, 대조, 예시)을 먼저 잡고, 밑줄 친 낱말이 그 흐름과 반대 방향인지 확인합니다. 밑줄 낱말을 반의어로 바꿔 넣었을 때 문맥이 자연스러워지면 그것이 정답입니다.
[풀이 과정]
1) 핵심 개념: 돈의 시간 가치 — 돈이 묶여 있는 동안 다른 데 쓰거나 이자를 벌 기회를 잃는다.
2) ③ the 비교급, the 비교급 구문: 돈을 쓰고 수입을 얻기까지의 지연이 길수록, 잃어버린 수입(lost income) 면에서 투자는 더 ‘비싸진다(costly)’. 지연은 ‘문제’입니다.
3) ④ 다음 문장에서 영화는 보관할 수는 있지만 오래되면 가치가 떨어진다(do not age well)고 합니다. 보관이 어렵다는 점은 문제를 ‘쉽게(easier)’ 만드는 것이 아니라 ‘더 나쁘게(worse)’ 만듭니다.
[정답 근거] ④ easier는 worse로 바꾸어야 하므로 적절하지 않습니다.
[오답 분석]
① 티켓 판매 수입이 들어오기 ‘전에’ 돈을 다 쓴다로 맞습니다.
② 그 돈은 다른 일에 ‘쓰일’ 수 없다로 맞습니다.
③ 지연이 길수록 잃는 수입이 커지므로 투자가 더 ‘비싸진다’로 맞습니다.
⑤ 오래된 영화로 수입을 계속 올릴 기회가 ‘거의 없다’로 맞습니다.
[어휘] time value of money 화폐의 시간 가치 · pay off 갚다 · age well 세월이 지나도 가치를 유지하다 · outdated 구식인', 30, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 빈칸이 첫 문장에 있으므로 뒤에 이어지는 근거를 모아 판단합니다.
2) 근거 1: No book or expert can tell you exactly how to become more creative. / no book, course, or teacher can hand it to you.
3) 근거 2: 자신을 관찰하고(observing yourself), 자신의 패턴(your own patterns)에 맞춰 창작 활동을 계획하라. 남을 따라 하는 것(copying other people)은 소용없다.
4) 결국 창의성은 사람마다 다르고 스스로 찾아야 하는 ‘개인적인’ 것입니다.
[정답 근거] 창의성은 개인적인(personal) 것이므로 ①이 정답입니다.
[오답 분석]
② 책이나 전문가가 가르쳐 줄 수 없다고 했으므로 teachable은 반대입니다.
③ 남을 따라 하는 것은 소용없다고 했으므로 imitative도 반대입니다.
④ 믿을 수 없다는 내용은 없습니다.
⑤ 수익성과는 관련이 없습니다.
[어휘] arrange 계획하다, 정리하다 · self-knowledge 자기 이해 · self-defeating 자기 파괴적인, 역효과를 낳는', 31, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 필자의 관점: 예술 비평도 과학처럼 관찰과 증거에 기반한 추론이 필요하다.
2) 빈칸 앞: 비평가는 구도·기법·역사적 맥락 같은 구체적 요소를 고려해야 한다. 빈칸 문장은 ‘~하면 판단이 무게를 갖지 못한다’는 조건이므로, 이 요소들을 고려하지 않는 경우가 들어가야 합니다.
3) 빈칸 뒤: 감정도 구체적 특징(concrete qualities)과 다른 사람이 확인할 수 있는 세부 사항에 근거해야 한다.
[정답 근거] 작품의 관찰 가능한 특징을 무시한다는 ④가 정답입니다.
[오답 분석]
① 인기는 언급되지 않습니다.
② 전문 용어는 다루지 않습니다.
③ 감정 반응을 과소평가하는 것이 문제라는 것은 글의 방향과 반대입니다. 글은 감정만으로는 부족하다고 합니다.
⑤ 너무 객관적으로 판단하는 것이 문제라는 것도 글의 주장과 반대입니다.
[어휘] subjective 주관적인 · fall short 부족하다 · unwillingness 꺼림 · carry weight 영향력을 갖다 · concrete 구체적인', 32, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 빈칸 문장 바로 뒤 At first, this may sound surprising은 빈칸 내용이 ‘추상화의 목적(익숙함에서 벗어나기)과 반대처럼 보이는 긍정적 효과’임을 알려 줍니다.
2) 근거: 익숙한 요소는 관람자에게 기준점(anchor, a point of reference)이 되어 어려운 작품에 다가가게 한다. 달리의 시계 덕분에 관람자가 꿈같은 장면에 더 쉽게 들어간다(enter the dreamlike scene more easily).
3) 마지막 문장: 아는 것과 모르는 것 사이에 다리를 놓아 관람자를 초대한다.
[정답 근거] 익숙한 요소가 관람자의 몰입과 이해를 돕는다는 ②가 정답입니다.
[오답 분석]
① Such an approach does not reduce the creativity or power of the work.와 반대입니다.
③ 복잡한 개념을 지나치게 단순하게 만든다는 부정적 평가는 글의 관점과 맞지 않습니다.
④ 단순히 장식용이라는 내용은 없습니다.
⑤ 추상 미술의 규칙을 어긴다는 것은 사람들이 처음에 품는 의문(isn''t the whole point ~?)일 뿐, 필자의 주장이 아닙니다.
[어휘] abstract 추상적인 · recognizable 알아볼 수 있는 · twist out of shape 비틀어 변형하다 · territory 영역', 33, 3),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 핵심 개념: 생각과 감정에 끌려가는 것에서 그것을 관찰하는 것으로의 전환(a shift from being driven by ~ to observing them).
2) 부연: 판단하거나 매달리지 않고(without immediately judging them or clinging to them) 알아차리며, 마음이 만드는 이야기에 빠지는 경향(get lost in the stories)에 맞선다.
3) 빈칸 바로 앞: 감정을 억누르거나 생각을 멈추는 것이 아니다(not to suppress ~ or to stop thinking). 따라서 빈칸은 ‘생각을 관찰하되 휩쓸리지 않는 것’입니다.
[정답 근거] 생각에 휩쓸리지 않고 관찰하는 것이라는 ④가 정답입니다.
[오답 분석]
① 감정의 원인을 분석하는 것은 판단 없이 지켜보라는 내용과 다릅니다.
② not to suppress emotions와 정면으로 어긋납니다.
③ 다른 사람과의 거리는 다루지 않습니다.
⑤ 생각을 바꾸는 것이 아니라 있는 그대로 관찰하는 것이 핵심입니다.
[어휘] awareness 알아차림 · urgent 긴급한 · practitioner 실천하는 사람 · stimulus 자극 · suppress 억누르다', 34, 3),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 무관한 문장 (2점)
[유형 전략] 무관한 문장 문항은 첫 문장(주제문)이 제시한 소재와 방향을 확인한 뒤 각 문장이 그 방향을 이어 가는지 봅니다. 정답 문장은 지문의 핵심어를 일부 포함하지만 논점이 다른 쪽(다른 대상, 다른 측면)으로 벗어난 경우가 많습니다.
[풀이 과정]
1) 첫 문장의 화제: 냄새는 신생아와 양육자 사이의 가장 기본적인 유대이다.
2) ①②는 아기가 냄새로 엄마를 알아보는 내용, ④⑤는 엄마가 냄새로 자기 아기를 알아보는 내용으로 ‘서로를 알아보는 유대’를 양쪽에서 설명합니다.
3) ③은 냄새에 지나치게 민감한 엄마가 스트레스를 받는다는 내용으로, 유대라는 화제에서 벗어난 부정적 측면입니다.
[정답 근거] 엄마의 스트레스와 집안일을 다룬 ③이 흐름과 관계없습니다.
[오답 분석]
④의 in turn(이번에는)은 아기 쪽 이야기(①②) 다음에 엄마 쪽으로 넘어가는 표지입니다. ③을 빼야 ②와 ④가 자연스럽게 이어집니다.
[어휘] caregiver 양육자 · scent 냄새 · identify 알아보다 · identical 똑같은', 35, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글의 순서 (2점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 할아버지가 정원에서 예초기로 일하고 있었다.
2) (C)의 The machine은 주어진 글의 예초기이며, 할아버지가 날을 정리하다가 “빨간 버튼을 누르지 마라”고 경고합니다.
3) (B)의 the red button은 (C)에서 처음 언급된 버튼을 가리킵니다. 나는 버튼 옆에 앉아 누르지 말자고 되뇝니다.
4) (A) 결국 버튼을 눌러 버리는 결말입니다.
[정답 근거] 주어진 글 → (C) → (B) → (A)이므로 ⑤가 정답입니다.
[오답 분석]
(B)가 (C)보다 앞서면 the red button과 Don''t press it.이 왜 나오는지 알 수 없습니다. 정관사 the와 반복되는 금지의 말은 앞에서 경고를 들었다는 단서입니다.
[어휘] wander 거닐다 · upside down 거꾸로 · stare at ~을 응시하다 · roar to life 굉음을 내며 작동하다', 36, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 순서 (3점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 마시멜로 실험은 미래 성공의 강력한 예측 도구로 여겨졌다.
2) (C) In the experiment로 실험 방법과 원래의 결론(기다린 아이가 더 성공)을 설명합니다.
3) (A)의 however와 this conclusion은 (C)의 원래 결론을 받아, 가정 배경을 고려한 재연구가 이를 반박했다고 합니다.
4) (B)의 This suggests는 (A)의 재연구 결과(가난한 가정의 아이가 즉시 보상을 택한 이유)를 받아 결론을 내립니다.
[정답 근거] 주어진 글 → (C) → (A) → (B)이므로 ④가 정답입니다.
[오답 분석]
(A)가 주어진 글 바로 뒤에 오면 this conclusion이 가리킬 구체적 결론(기다린 아이가 더 성공)이 아직 나오지 않았습니다.
(B)가 (A)보다 앞서면 This suggests가 근거로 삼을 연구 결과가 없습니다.
[어휘] predictor 예측 변수 · take ~ into account ~을 고려하다 · unstable 불안정한 · delay 미루다 · trait 특성', 37, 3),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 문장 삽입 (2점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장의 both sides(양쪽)는 바로 앞에 서로 맞서는 두 주장이 나와야 한다는 단서이고, have always been flexible(항상 유연했다)은 뒤에 역사적 근거가 이어질 것을 예고합니다.
2) ① 앞: 비판하는 쪽(Critics claim)과 지지하는 쪽(supporters point to)의 주장이 나옵니다.
3) ① 뒤: The potter''s wheel and the electric kiln, for example, ... without giving up their traditions. for example은 바로 앞에 ‘예를 들 만한 주장’이 있어야 하는데, 찬반 주장 자체에는 이 예가 들어맞지 않습니다. 주어진 문장(장인은 늘 새 도구를 받아들이면서 핵심은 지켰다)의 예입니다.
4) ② 뒤의 This ongoing blend of tradition and technology는 ② 앞의 도자기 예(전통을 버리지 않고 새 기술을 받아들임)를 가리킵니다.
[정답 근거] 찬반 양쪽 주장 뒤, 역사적 근거 앞인 ①이 정답입니다.
[오답 분석]
②: 도자기 예와 This ongoing blend가 이미 이어져 있습니다. 여기에 넣으면 both sides가 가리킬 두 주장과 멀어집니다.
③④: 오늘날의 장인 사례가 이어지는 자리로, 찬반 양쪽(both sides)이 바로 앞에 없습니다.
⑤: 앞 문장은 한쪽의 우려(Some worry)일 뿐 양쪽 주장이 아니며, 뒤의 However와도 주어진 문장이 겹쳐 흐름이 어색해집니다.
[어휘] spark 촉발하다 · precision 정밀함 · tradition 전통 · blend 혼합 · uniform 획일적인 · thrive 번창하다', 38, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 문장 삽입 (3점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장의 This gap in our knowledge(우리 지식의 이 빈틈)는 바로 앞에 ‘아직 잘 모른다’는 내용이 나와야 한다는 단서입니다.
2) ⑤ 앞: many aspects of how it works are still not well understood. 지식의 빈틈이 처음 언급됩니다.
3) ⑤ 뒤: However, if scientists focus mainly on its practical uses ~. its practical uses는 주어진 문장의 new technologies, such as more efficient lighting과 이어집니다.
[정답 근거] ‘잘 알려지지 않았다’는 문장과 ‘실용적 활용에만 집중하면’ 문장 사이인 ⑤가 정답입니다.
[오답 분석]
①~④: 앞부분은 생물 발광의 특징과 쓰임새를 설명하는 단계로, 지식의 빈틈이 아직 언급되지 않았습니다.
[어휘] organism 생물 · firefly 반딧불이 · prey 먹이 · mate 짝 · widespread 널리 퍼진', 39, 3),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 요약문 완성 (2점)
[유형 전략] 요약문은 먼저 요약문을 읽고 (A), (B)가 각각 무엇을 묻는지 확인한 뒤 지문을 읽습니다. 지문의 실험 결과나 결론 문장을 요약문의 표현과 짝지어, 같은 뜻을 다른 말로 바꾼(paraphrase) 선택지를 고릅니다.
[풀이 과정]
1) 요약문 분석: 타르는 나무와 함께 움직일 만큼 유연해서, 온도와 움직임의 변화에도 방수 밀봉을 (B)하여 나무배를 더 (A)하게 만들었다.
2) (A) 근거: greatly extended the life of the boats(배의 수명을 크게 늘렸다) → durable(내구성 있는).
3) (B) 근거: keep its protective seal unbroken through constant movement and shifting temperatures → maintaining(유지하는).
[정답 근거] (A) durable, (B) maintaining인 ③이 정답입니다.
[오답 분석]
① stable은 그럴듯하지만 (B) breaking(깨뜨리는)은 unbroken과 반대입니다.
② beautiful(아름다운)은 근거가 없습니다.
④ (A)는 맞지만 (B) loosening(느슨하게 하는)은 반대입니다.
⑤ expensive는 근거가 없고, creating보다 기존의 밀봉을 ‘유지’하는 것이 핵심입니다.
[어휘] swell 부풀다 · shrink 줄어들다 · leak 새는 곳 · seep into ~으로 스며들다 · seal 밀봉', 40, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q41.webp]]', '[[img:/naesin/csat-h1-mock/r04/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 장문 제목 (2점)
[유형 전략] 장문 제목은 글 전체의 논지를 담아야 합니다. 도입의 일반적 진술과 후반부의 결론을 연결하여 주제를 정리하고, 글의 일부 사례만 다루는 선택지는 지웁니다.
[풀이 과정]
1) 첫 단락: 협력이 생존에 필수였던 사회에서, 남을 도우면 보상을 주는 심리 체계가 뇌에 생겼다.
2) 둘째 단락: 오늘날에는 이웃에 덜 의존하지만 뇌는 아직 적응하지 못해, 우리는 여전히 남을 도울 때 기쁨을 느낀다. 이것은 생존 본능이다.
3) 마지막 문장: why we often feel driven to help others — 글의 핵심 질문이자 답입니다.
[정답 근거] 우리가 본능적으로 남을 돕고 싶어 하는 이유를 다룬 ②가 제목으로 적절합니다.
[오답 분석]
① 이웃과 친해지는 방법은 다루지 않습니다.
③ 기술과 외로움의 관계를 주장하는 글이 아닙니다.
④ 정착지의 진화는 다루지 않습니다.
⑤ 독립적인 생활을 사회 발전의 증거로 평가하지 않습니다.
[어휘] mutual support 상호 지원 · generosity 관대함 · ancestor 조상 · lift 기분이 좋아짐 · be inclined to ~하는 경향이 있다', 41, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q42.webp]]', '[[img:/naesin/csat-h1-mock/r04/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 장문 어휘 (2점)
[유형 전략] 장문 어휘 문항은 단락별 흐름을 잡은 뒤 밑줄 낱말을 하나씩 문맥에 넣어 봅니다. 앞뒤 문장과 인과·대조 관계가 맞지 않는 낱말, 반의어로 바꾸면 자연스러워지는 낱말이 정답입니다.
[풀이 과정]
1) (d)가 있는 문장은 We still feel a strong emotional lift when we help others(남을 도우면 기분이 좋아진다)와 and로 이어지며 그 반대 상황을 말합니다.
2) 도우면 기분이 좋아지도록 진화한 뇌라면, 어려운 사람을 외면할 때는 ‘불편함(discomfort)’을 느껴야 합니다. comfortable → uncomfortable
[정답 근거] (d) comfortable은 uncomfortable로 바꾸어야 하므로 ④가 정답입니다.
[오답 분석]
(a) increasing: 도움을 받을 가능성이 높아져 생존 확률이 ‘높아진다’로 맞습니다.
(b) beneficial: 남을 돕는 ‘이로운’ 행동을 장려한다로 맞습니다.
(c) adjusted: 뇌가 새로운 생활 방식에 완전히 ‘적응하지’ 못했다로 맞습니다.
(e) driven: 이익이 없어도 남을 돕도록 ‘이끌린다’로 맞습니다.
[어휘] in need 어려움에 처한 · rooted 뿌리박힌 · obvious 분명한', 42, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q43.webp]]', '[[img:/naesin/csat-h1-mock/r04/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 장문 순서 (2점)
[유형 전략] 장문 순서는 등장인물의 행동과 시간의 흐름을 따라갑니다. 각 단락의 첫 문장이 앞 단락의 마지막 장면과 이어지는지(장소 이동, 대화의 응답, 사건의 결과) 확인합니다.
[풀이 과정]
1) (A) 끝: Daniel이 정원에서 수수께끼의 철 상자를 발견했다.
2) (B) When James saw the strange box는 (A)의 상자를 이어받습니다. James는 마을 어른에게 가자고 하지만 Daniel은 혼자 풀려고 한다.
3) (D) After many sleepless nights, Daniel finally managed to open the box. (B)에서 몇 시간씩 상징을 연구하던 일의 결과입니다. 두루마리로 물건을 만들수록 Daniel이 지쳐 갑니다.
4) (C) 두루마리의 해로운 영향을 걱정한 James의 설득과 상자를 봉인하는 결말입니다. the scrolls는 (D)에서 처음 등장하므로 (C)는 (D) 뒤에 옵니다.
[정답 근거] (A) → (B) → (D) → (C)이므로 ①이 정답입니다.
[오답 분석]
상자 발견 → 상자 연구 → 상자 열기(두루마리) → 두루마리의 해로움 → 봉인. 물건(box, scrolls)이 처음 언급되는 단락이 먼저 와야 합니다.
[어휘] uncover 파내다, 발견하다 · elder 어른 · obsession 집착 · drained 기진맥진한 · for good 영원히', 43, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q44.webp]]', '[[img:/naesin/csat-h1-mock/r04/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 지칭 대상 (2점)
[유형 전략] 지칭 대상 문항은 밑줄 친 대명사가 나오는 문장에서 그 행동을 하는 사람이 누구인지 앞뒤 문맥으로 확인합니다. 대화문 속 you/I는 말하는 사람과 듣는 사람을 기준으로 따집니다.
[풀이 과정]
1) (a) 상자를 발견하고 호기심에 찬 사람 → Daniel
2) (b) 상자 뚜껑의 상징을 몇 시간씩 연구한 사람 → Daniel
3) (c) 마법이 몸과 마음을 약하게 한다고 짚어 준 사람 → James(앞 문장의 Worried about Daniel''s health, James told him ~)
4) (d) 위험을 깨닫고 두루마리 사용을 그만두기로 동의한 사람 → Daniel
5) (e) 물건을 만들 때마다 더 지쳐 가던 사람 → Daniel
[정답 근거] (c)만 James이고 나머지는 Daniel이므로 ③이 정답입니다.
[오답 분석]
(c)는 James told him about the harmful effects 바로 다음 문장의 주어이므로 James입니다. 같은 문장의 weakening him(Daniel)과 구별합니다.
(d)는 위험을 깨닫고 동의하는 사람이므로 경고를 들은 Daniel입니다.', 44, 2),
(@csh1_r04, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r04/q45.webp]]', '[[img:/naesin/csat-h1-mock/r04/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 장문 내용 불일치 (2점)
[유형 전략] 장문 내용 불일치는 선택지 순서가 대체로 글의 사건 순서를 따릅니다. 선택지의 핵심어로 해당 단락을 찾아 인물·행동·시간을 정확히 대조합니다.
[풀이 과정]
1) ⑤ (C) Together, the brothers locked the box and its contents away for good. 형제는 상자와 두루마리를 영원히 잠가 두었습니다. 마을 어른에게 맡긴 것이 아닙니다.
[정답 근거] ⑤가 글의 내용과 일치하지 않습니다.
[오답 분석]
① lived in a small cottage deep in a thick forest와 일치합니다.
② while digging in the garden, Daniel uncovered a mysterious iron box와 일치합니다.
③ he suggested that they take it to the village elder for advice와 일치합니다.
④ to forge extraordinary tools and weapons와 일치합니다.', 45, 2);

-- 5회: 2024년 8월 고1 이투스 원본(고1 이투스 24 8모)
INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-CSH1-MOCK-R05', 'NAESIN', '고1 수능 영어 독해 모의고사 5회 · 18~45번 변형문제 5회', '2024년 8월 고1 이투스 원본 · 28문항 · 45분 · 62점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 풀이 전략 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @csh1_r05 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-CSH1-MOCK-R05');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글의 목적 (2점)
[유형 전략] 글의 목적은 대개 글 후반부의 요청·안내 문장(please, we ask, I would like to, I am writing to)에 드러납니다. 앞부분의 인사·배경 설명에 끌려가지 말고, 필자가 독자에게 결국 무엇을 해 달라고 하는지 한 문장으로 정리한 뒤 선택지와 맞춰 봅니다.
[풀이 과정]
1) 도입: 언니의 결혼 날짜가 정해져 기쁘다는 인사입니다.
2) 전환(Unfortunately): I will not be able to be there for this once-in-a-lifetime event. 입학시험 날과 겹쳐 참석할 수 없다.
3) 마무리: give her my sincere apologies for not being able to attend. 불참에 대한 사과와 축하를 전해 달라고 합니다.
[정답 근거] 결혼식에 참석하지 못하는 것을 사과하는 글이므로 ①이 정답입니다.
[오답 분석]
② the exam date cannot be changed라고 했으므로 일정 변경을 부탁하지 않습니다. 지문 내용을 비튼 오답입니다.
③ 준비를 돕겠다는 제안은 없습니다.
④ 기쁜 소식을 들은 반가움은 도입부의 인사일 뿐입니다.
⑤ 선물은 언급되지 않습니다.
[어휘] once-in-a-lifetime 일생에 한 번뿐인 · fall on (날짜가) ~에 해당하다 · a way around ~을 피할 방법 · regret 유감스럽게 생각하다 · attend 참석하다', 18, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 심경 변화 (2점)
[유형 전략] 심경 변화는 처음 상황과 전환 이후 상황을 나누어 읽습니다. 사건이 바뀌는 지점(when, suddenly, but, then)을 찾고, 그 앞뒤에서 인물의 감정을 드러내는 표현(형용사, 신체 반응, 혼잣말)을 각각 하나씩 골라 두 감정의 짝을 확인합니다.
[풀이 과정]
1) 전반부: my stomach began to turn, Don''t be afraid!, I wanted to scream, "No, no, no!", couldn''t bring myself to look down → 겁에 질린 상태(scared)입니다.
2) 전환점: And I jumped. ... Suddenly, a rush of joy swept through me.
3) 후반부: the most wonderful surprise of my life, This was fantastic! → 기쁨(joyful)입니다.
[정답 근거] 두려움(scared)에서 기쁨(joyful)으로 바뀌므로 ①이 정답입니다.
[오답 분석]
② 외로움은 언급되지 않습니다.
③ 처음 감정이 들뜸이 아니라 두려움이고, 나중에 걱정하지도 않습니다. 순서를 뒤집은 오답입니다.
④ 지루함은 드러나지 않습니다.
⑤ 화가 난 상태로 시작하지 않습니다.
[어휘] bring oneself to ~할 마음이 내키다 · squeeze 꽉 감다 · sweep through ~을 휩쓸다 · whisper 속삭임', 19, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 필자의 주장 (2점)
[유형 전략] 필자의 주장은 should, must, need to, it is important to, 명령문처럼 당위를 나타내는 문장에 담깁니다. 예시나 일화는 주장을 뒷받침하는 재료이므로, 일화가 끝난 뒤 필자가 일반화하는 문장을 찾아 그 내용과 같은 선택지를 고릅니다.
[풀이 과정]
1) 비유로 시작합니다: 몸을 먹일 단골 식당은 있지만 정신을 먹일 좋아하는 작가는 없는 사람.
2) 주장: I am asking you to nourish your mind just as faithfully as you nourish your body.
3) 방법: One of the best ways to do this ... is through the writings of influential people. 처칠과 아리스토텔레스의 책이 예입니다.
[정답 근거] 몸을 먹이듯 영향력 있는 사람들의 글로 정신을 길러야 한다는 ②가 주장입니다.
[오답 분석]
① 직장 성공이나 전문 서적에 한정하지 않습니다.
③ 시험이 없으면 책을 읽지 않는 태도를 비판하지만, 계획적인 공부를 말하는 글은 아닙니다.
④ You may never meet them in person, but you can read their books.와 맞지 않습니다.
⑤ 식사는 비유로 쓰였을 뿐 실제 식사 이야기가 아닙니다.
[어휘] land a job 일자리를 얻다 · faithfully 충실하게 · influential 영향력 있는 · feast 잔치, 즐거움', 20, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 밑줄 함의 (3점)
[유형 전략] 밑줄 친 표현은 비유이므로 글자 그대로 해석하지 않습니다. 밑줄 앞뒤에서 같은 대상을 설명하는 문장을 찾아, 비유 속 단어(밑줄의 핵심 명사·동사)가 글에서 무엇에 대응하는지 짝지은 뒤, 그 의미를 풀어 쓴 선택지를 고릅니다.
[풀이 과정]
1) 비유의 짝: 볼링에서 공을 굴리는 것(bowls carefully) = 차례가 된 사람이 말하는 것, 다른 사람들이 자리에서 지켜보는 것 = 듣는 사람들이 기다리는 것.
2) 따라서 공이 핀에 닿는다는 것은 굴린 공의 여정, 즉 지금 말하는 사람의 발언이 끝났다는 뜻입니다.
3) No one else speaks until ~: 그때까지 아무도 끼어들지 않는다는 일본식 대화의 차례 지키기를 보여 줍니다.
[정답 근거] 지금 말하는 사람이 말을 완전히 마칠 때까지라는 ⑤가 정답입니다.
[오답 분석]
① 동의 여부는 다루지 않습니다.
② 한꺼번에 말하기 시작하는 것은 차례를 지키는 문화와 반대입니다.
③ 화제 전환은 언급되지 않습니다.
④ 경쟁적인 게임으로 해석한 오답입니다. 볼링은 차례를 상징할 뿐입니다.
[어휘] remark 말하다 · come to a halt 멈추다 · stand in contrast to ~와 대조를 이루다 · appropriate 적절한', 21, 3),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글의 요지 (2점)
[유형 전략] 요지는 글 전체가 말하려는 한 가지 핵심입니다. 도입의 질문·통념, 대조(however, but), 결론(therefore, in short)을 확인하고, 세부 예시 하나만 다룬 선택지나 지문 단어를 섞어 만든 그럴듯한 선택지는 지웁니다.
[풀이 과정]
1) 도입: 지방 섭취는 줄었는데 과체중 인구는 늘었다는 역설적인 통계.
2) 통념 반박: 저지방 제품만 먹으면 살이 빠진다고 믿지만 그렇게 간단하지 않다(it is not that simple).
3) 이유: 빠진 맛을 보충하려고 설탕을 많이 넣어 칼로리가 높다(plenty of sugar means plenty of calories).
[정답 근거] 저지방 식품도 당분 때문에 체중 증가의 원인이 될 수 있다는 ③이 요지입니다.
[오답 분석]
① 글이 반박하는 통념입니다.
② 성분 표시 문제는 다루지 않습니다.
④ 설탕이 칼로리를 높인다고 했을 뿐, 설탕과 지방의 해로움을 비교하지 않습니다.
⑤ 운동은 언급되지 않습니다.
[어휘] overweight 과체중의 · label 표시하다 · version 형태, 종류 · make up for ~을 보충하다 · deliver 제공하다', 22, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 주제 (2점)
[유형 전략] 주제는 글의 핵심 소재와 그에 대한 필자의 관점을 함께 담은 영어 구로 나타납니다. 반복되는 핵심어를 찾고, 글이 그 소재의 무엇(원인, 효과, 과정, 중요성, 한계)을 다루는지 확인하여 범위가 너무 넓거나 좁은 선택지를 지웁니다.
[풀이 과정]
1) 도입: 친구에게 설명하면 스스로 더 잘 이해하게 된다. 설명하려면 더 깊이 생각해야 하기 때문이다.
2) 확장: The same principle makes study groups so valuable. 함께 생각을 말하고, 토론하고, 서로의 답을 비교하면서 혼자 할 때보다 더 많이 배운다.
3) 결론: 혼자서는 늘 같은 관점으로만 보지만, 스터디 그룹에서는 새로운 관점을 얻는다.
[정답 근거] 함께 공부하는 것이 이해를 깊게 하는 이유를 다루므로 ④가 정답입니다.
[오답 분석]
① 혼자 공부하는 것의 한계를 지적하므로 반대입니다.
② 구성원을 고르는 방법은 다루지 않습니다.
③ 작은 그룹(a small group)의 장점을 말하며, 큰 그룹의 단점은 언급되지 않습니다.
⑤ explain이 반복되지만 교사의 설명 방법은 다루지 않습니다.
[어휘] think out loud 생각을 소리 내어 말하다 · debate 토론하다 · work out 알아내다 · point of view 관점', 23, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 제목 (2점)
[유형 전략] 제목은 주제를 압축하거나 비유·의문문으로 바꾼 표현입니다. 먼저 글의 주제를 우리말로 정리한 뒤, 그 핵심을 담은 선택지를 고릅니다. 지문에 나온 단어가 그대로 쓰였지만 글의 일부만 다루거나 반대 방향을 말하는 선택지에 주의합니다.
[풀이 과정]
1) 원리: 자연은 모든 영양소를 한 가지 음식에 담지 않고 여러 음식에 흩어 놓았다.
2) 결론: it is important to eat a variety of foods rather than the same meals and snacks day after day.
3) 구체화: 색이 다양할수록 좋다 — 블루베리, 빨간 피망, 주황 당근, 초록 시금치처럼 여러 색의 음식을 먹어라.
[정답 근거] 다양한 색의 음식을 먹어 건강을 지키라는 ④가 제목으로 적절합니다.
[오답 분석]
① 보기 좋은 음식을 더 먹는다는 것은 다루지 않습니다.
② 간식을 줄이는 요령이 아닙니다.
③ 영양소가 부족한 음식의 위험은 다루지 않습니다.
⑤ 가뭄(droughts)을 이용한 오답으로, 가뭄은 한 가지 음식에 의존할 때의 위험을 보여 주는 예일 뿐입니다.
[어휘] nutrient 영양소 · drought 가뭄 · shortage 부족 · modest 그다지 많지 않은 · as a general rule 대체로', 24, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 도표 불일치 (2점)
[유형 전략] 도표 문항은 선택지 문장을 하나씩 도표 수치와 맞춰 봅니다. 비교 표현(more than, twice, the lowest), 순위, 차이 계산이 함정이 되는 경우가 많으므로, 문장에 나온 항목·연도·수치를 도표에서 손가락으로 짚어 가며 확인합니다.
[풀이 과정]
1) ① 게임(66 > 58)만 8–11세가 높고 나머지는 모두 12–15세가 높습니다. (일치)
2) ② 8–11세의 가장 흔한 용도는 Schoolwork(74%)이고 Playing games는 66%입니다. (불일치)
3) ③ 8–11세 TV websites 21%로 약 5분의 1입니다. (일치)
4) ④ 12–15세 46%, 8–11세 8%. (일치)
5) ⑤ Downloading music: 30 > 12 × 2 = 24. (일치)
[정답 근거] 8–11세가 인터넷을 가장 많이 쓴 용도는 학업이므로 ②가 일치하지 않습니다.
[오답 분석]
최상급(the most common) 진술은 해당 집단의 막대 전체를 비교해야 합니다. 한 항목만 보고 판단하면 함정에 빠집니다.
[어휘] instant messaging 메신저 · one-fifth 5분의 1', 25, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 불일치 (2점)
[유형 전략] 인물 내용 일치 문항은 선택지 순서가 대체로 글의 순서와 같습니다. 선택지의 핵심어(연도, 장소, 숫자, 고유명사)를 먼저 보고 지문에서 해당 부분을 찾아 정확히 대조합니다. 일부만 바꾼 진술(시기, 횟수, 주체)에 주의합니다.
[풀이 과정]
1) ⑤ most of them based on images he had seen only once in a book or on television. 책이나 텔레비전에서 한 번 본 이미지를 바탕으로 그렸습니다.
[정답 근거] 실제 풍경을 보며 그린 것이 아니므로 ⑤가 일치하지 않습니다.
[오답 분석]
① did not learn to speak until he was eleven과 일치합니다.
② At the age of three, he began drawing on a chalkboard와 일치합니다.
③ his first exhibition in Edinburgh at the age of seventeen과 일치합니다.
④ He never used models; ... pictures from memory와 일치합니다.
[어휘] exhibition 전시회 · astonishingly 놀라울 정도로 · accurate 정확한 · landscape 풍경화', 26, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 안내문 불일치 (2점)
[유형 전략] 안내문은 항목별로 정보가 정리되어 있으므로 선택지의 핵심어(날짜, 시간, 대상, 비용, 신청 방법)로 해당 항목을 바로 찾습니다. 주의 사항·예외 조건(only, not, must)이 함정으로 자주 쓰입니다.
[풀이 과정]
1) ③ Each person may submit up to two entries. 한 사람이 두 작품까지 낼 수 있습니다.
[정답 근거] 두 작품까지 제출할 수 있으므로 ③이 일치하지 않습니다.
[오답 분석]
① Students between the ages of 7 and 18 living anywhere in the world와 일치합니다.
② Participation is free.와 일치합니다.
④ Photographs ... are not allowed.와 일치합니다.
⑤ an art supply set and a certificate와 일치합니다.
[어휘] entry 출품작 · submit 제출하다 · up to ~까지 · certificate 증서', 27, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 안내문 일치 (2점)
[유형 전략] 안내문 일치 문항은 정답이 하나뿐이므로 선택지를 하나씩 대조하며 틀린 것을 지웁니다. 날짜·요일, 연령, 요금, 포함 여부, 사전 등록 여부처럼 숫자와 조건이 바뀐 부분을 꼼꼼히 확인합니다.
[풀이 과정]
1) ② Each group must include at least one adult for every four children. 어린이 네 명당 성인 한 명 이상이 있어야 합니다.
[정답 근거] ②가 안내문과 일치합니다.
[오답 분석]
① 11월 8일은 Sold out(매진)입니다.
③ Adults may not register without children.
④ No tickets will be sold at the door.
⑤ Bring your own sleeping bag and pillow. 침낭은 직접 가져와야 하고, 제공되는 것은 아침 식사입니다.
[어휘] accompanying 동반하는 · register 등록하다 · in advance 미리 · at the door 현장에서', 28, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법 (3점)
[유형 전략] 어법 문항은 밑줄마다 확인할 문법 포인트를 먼저 정합니다(동사 자리·수일치, 관계사와 접속사, 분사·to부정사, 병렬 구조, 대명사, 형용사·부사). 특히 문장의 주어와 본동사를 먼저 찾으면 수일치와 준동사 문제를 빠르게 판단할 수 있습니다.
[풀이 과정]
1) ④ so 뒤에는 새 절이 와야 하는데 주어가 없으므로 명령문이며, looking이 아니라 동사원형 look이 와야 합니다.
2) ① because절 안에서 is easy to prepare와 and로 병렬을 이루는 동사로, 주어 it(단수)에 맞춘 takes가 맞습니다.
3) ② Cereals (which contain) fruit and nuts: 시리얼이 과일과 견과를 ‘포함하는’ 능동 관계이므로 현재분사 containing이 맞습니다.
4) ③ 선행사 a cereal을 받는 주격 관계대명사 that이 맞습니다.
5) ⑤ both A and B 구조에서 형용사 delicious와 병렬을 이루는 형용사 healthy가 맞습니다.
[정답 근거] 명령문의 동사 자리에 -ing형을 쓴 ④가 틀렸습니다(→ look).
어법 포인트: 동사 자리 판단 — 접속사(so, and, but) 뒤에 새로운 절이 시작되면 그 절에는 반드시 본동사가 있어야 합니다. 주어가 없다면 명령문이므로 동사원형을 씁니다.
[어휘] convenient 편리한 · be loaded with ~으로 가득하다 · crunchy 아삭한 · toasty 구운 맛이 나는', 29, 3),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘 (2점)
[유형 전략] 어휘 문항은 글의 흐름(원인과 결과, 대조, 예시)을 먼저 잡고, 밑줄 친 낱말이 그 흐름과 반대 방향인지 확인합니다. 밑줄 낱말을 반의어로 바꿔 넣었을 때 문맥이 자연스러워지면 그것이 정답입니다.
[풀이 과정]
1) ① 문장의 결론은 the bucket of ice should be heavier(얼음 양동이가 더 무겁겠지?)입니다. 그런 추론이 나오려면 대부분의 고체가 액체 상태보다 ‘더 무겁다(heavier)’는 전제가 있어야 합니다.
2) 그러면 바로 뒤 Wrong. Water is unusual among liquids because it becomes lighter as it freezes.(물은 얼면 가벼워지는 예외다)와도 자연스럽게 이어집니다.
[정답 근거] ① lighter는 heavier로 바꾸어야 하므로 ①이 정답입니다.
[오답 분석]
② 얼면서 가벼워지는 물은 액체 중에서 ‘특이하다’로 맞습니다.
③ 바닥부터 얼면 얼음이 ‘올라와’ 물이 얕아진다로 맞습니다.
④ 물보다 가벼우니 ‘뜬다’로 맞습니다.
⑤ 표면의 얼음층이 아래의 물을 ‘보호한다’로 맞습니다.
[어휘] solid 고체 · for one 예를 들면 · trap 가두다 · anything but 결코 ~이 아닌 · layer 층', 30, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 실험: 스트레스가 전혀 없는 완벽한 환경(ideal temperature, perfect level of moisture, constant supply of food)에 둔 아메바가 죽었다.
2) 해석: 모든 생물은 도전을 필요로 한다(demands challenge). 우리는 음식과 공기처럼 변화와 적응, 고투가 필요하다.
3) 빈칸: 그렇다면 우리를 죽일 수 있는 것은 도전이 없는 상태, 즉 지나친 ‘편안함’입니다.
[정답 근거] 지나친 편안함(comfort)이 생물을 죽일 수 있다는 ③이 정답입니다.
[오답 분석]
① challenge, ④ change는 생물에게 ‘필요한’ 것으로 제시되었으므로 반대입니다.
② 갈등이 너무 많아 죽었다는 것은 실험 결과와 맞지 않습니다.
⑤ 인내는 관련이 없습니다.
[어휘] moisture 습기 · constant 끊임없는 · adjustment 적응, 조정 · apparently 보아하니 · struggle 고투', 31, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 핵심 태도: 설거지할 때 그릇·물·손의 움직임 하나하나를 온전히 의식한다(staying fully aware).
2) 대조: 디저트를 빨리 먹으려고 서두르면 설거지도, 디저트도 즐기지 못한다. 포크를 들고도 다음 할 일을 생각하기 때문이다.
3) 빈칸 문장은 이런 태도가 계속되면 ‘결코 ~할 수 없을 것’이라는 결론입니다. 늘 다음 일을 생각하면 지금 이 순간을 살 수 없습니다.
[정답 근거] 현재의 순간을 온전히 살 수 없다는 ⑤가 정답입니다.
[오답 분석]
① 미래 계획은 오히려 필자가 경계하는 ‘다음 할 일 생각하기’와 가깝습니다.
② 과거는 다루지 않습니다.
③ 일을 제시간에 끝내는 것은 서두르는 태도와 관련 있어 글의 방향과 다릅니다.
④ 다른 사람과의 나눔은 언급되지 않습니다.
[어휘] take one''s time 천천히 하다 · chore 하기 싫은 일 · miracle 기적 · slip away 사라지다', 32, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 빈칸 뒤 Here is how it works.는 빈칸 내용의 원리를 설명하겠다는 신호입니다.
2) 원리: 빛이 별을 떠나 우리 눈에 오기까지 시간이 걸리므로, we see the star or planet as it was when the light left it, not as it is now.
3) 예시: 태양빛은 8분 걸려 오므로, 태양이 사라져도 8분 동안은 여전히 보인다.
[정답 근거] 밤하늘을 볼 때 우리는 사실 과거를 보고 있다는 ②가 정답입니다.
[오답 분석]
① 별의 개수는 다루지 않습니다.
③ 별자리처럼 무리 짓는 현상은 언급되지 않습니다.
④ 밝기와 거리의 관계는 다루지 않습니다.
⑤ not as it is now와 정반대입니다.
[어휘] gaze up at ~을 올려다보다 · race 질주하다 · enormous 엄청난 · vanish 사라지다 · stream 흘러 들어오다', 33, 3),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 원리: 입자가 탈출 속도보다 빠르면 행성을 떠난다. 지구의 대기 입자는 그렇게 빠르지 않아서 Earth is able to keep its air.
2) 적용(Now consider): 달은 질량이 작아 중력이 약하고 탈출 속도도 훨씬 낮다.
3) 빈칸은 so ~ that 구문의 결과입니다. 지구가 공기를 지킬 수 있었던 것과 대조하면, 달은 공기를 붙잡아 두지 못했다는 결론이 나옵니다.
[정답 근거] 달이 대기를 붙잡아 두지 못했다는 ⑤가 정답입니다.
[오답 분석]
① 중력이 약하다는 내용과 반대입니다.
② 표면 온도는 다루지 않습니다.
③ 운석은 언급되지 않습니다.
④ 달의 공전 속도는 탈출 속도와 관계가 없습니다.
[어휘] atmosphere 대기 · exceed 넘다 · altogether 완전히 · mass 질량 · hold on to ~을 붙잡아 두다', 34, 3),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 무관한 문장 (2점)
[유형 전략] 무관한 문장 문항은 첫 문장(주제문)이 제시한 소재와 방향을 확인한 뒤 각 문장이 그 방향을 이어 가는지 봅니다. 정답 문장은 지문의 핵심어를 일부 포함하지만 논점이 다른 쪽(다른 대상, 다른 측면)으로 벗어난 경우가 많습니다.
[풀이 과정]
1) 첫 두 문장의 화제: 물은 몸을 깨끗이 하고 해로운 물질을 씻어 내는 자연의 약이다.
2) ②③④는 스트레스 때 생기는 노폐물 → 물이 그것을 몸 밖으로 내보냄 → 물이 부족하면 노폐물이 쌓임의 흐름이고, ⑤는 물의 또 다른 건강 효과입니다.
3) ①은 생수와 수돗물 중 무엇이 안전한지, 사기 전에 출처를 확인하라는 내용으로 ‘물이 몸을 정화하는 역할’에서 벗어납니다.
[정답 근거] 물의 구매 요령을 다룬 ①이 흐름과 관계없습니다.
[오답 분석]
③의 these substances는 ②의 chemicals and other waste를 가리킵니다. ①을 빼면 첫 문장들 → ② → ③이 자연스럽게 이어집니다.
[어휘] property 성질 · cleanse 깨끗이 하다 · kidney 신장 · build up 쌓이다', 35, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글의 순서 (2점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 마젤란이 태평양에 이름을 붙였다.
2) (B)의 He named it은 주어진 글의 마젤란과 태평양을 받아 이름의 뜻(평화로운)을 설명하고, 그가 틀렸다(he was wrong)고 합니다.
3) (C)의 In fact는 ‘틀렸다’의 근거로, 태평양이 가장 거친 바다이며 폭풍이 잦다고 합니다.
4) (A)의 also는 (C)의 폭풍에 더해 ‘불의 고리’와 쓰나미라는 또 다른 위험을 덧붙입니다.
[정답 근거] 주어진 글 → (B) → (C) → (A)이므로 ③이 정답입니다.
[오답 분석]
(A)를 (C)보다 앞에 놓으면 also가 덧붙일 앞의 위험(폭풍)이 없습니다.
(C)가 주어진 글 바로 뒤에 오면 In fact가 반박할 마젤란의 생각(평화로운 바다)이 아직 나오지 않았습니다.
[어휘] explorer 탐험가 · as it turned out 결국 알고 보니 · violent 거친 · fierce 사나운', 36, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글의 순서 (3점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: S. B.는 시각 장애가 있었지만 다시 보기를 꿈꿨다.
2) (A) At last로 오랜 꿈이 이루어지는 수술 장면이 나오고, 곧바로 사물과 글자를 알아봅니다.
3) (C)의 Yet과 his new visual world는 (A)의 성공 뒤에 나타난 이상한 빈틈을 말합니다. 만져 본 적 없는 사물은 보지 못했다.
4) (B)의 For example은 (C)의 빈틈을 구체적으로 보여 줍니다. 얼굴 표정이나 풍경 사진을 이해하지 못했다.
[정답 근거] 주어진 글 → (A) → (C) → (B)이므로 ①이 정답입니다.
[오답 분석]
(C)를 주어진 글 바로 뒤에 놓으면 his new visual world(새로운 시각 세계)가 수술 전이라 성립하지 않습니다.
(B)가 (C)보다 앞서면 For example이 예를 들 일반적 진술이 없습니다.
[어휘] adapt to ~에 적응하다 · undergo (수술을) 받다 · scenery 풍경 · perception 지각', 37, 3),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 문장 삽입 (2점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장: 결국(Finally) 꽃병을 깨는 수밖에 없었다. 앞에는 손을 빼려는 노력이, 뒤에는 꽃병이 깨진 뒤의 장면이 와야 합니다.
2) ② 앞: 부모와 이웃이 힘껏 팔을 잡아당겼다(빼려는 노력).
3) ② 뒤: the sad pile of broken pieces(깨진 조각 더미)가 처음 나옵니다. 꽃병을 깼다는 내용이 앞에 없으면 이 조각들이 갑자기 등장하게 됩니다.
[정답 근거] 노력이 실패한 뒤, 깨진 조각이 등장하기 전인 ②가 정답입니다.
[오답 분석]
①: 아직 손을 빼려는 시도가 나오기 전이라 Finally가 어색합니다.
③~⑤: 이미 꽃병이 깨진 뒤의 장면과 교훈이 이어지는 부분입니다.
[어휘] at the top of one''s lungs 목청껏 · clutch 꽉 쥐다 · ignorance 무지 · cling to ~에 매달리다 · sacrifice 희생하다', 38, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 문장 삽입 (3점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장: 소포(the package)를 열었을 때 그녀는 자기 눈을 믿을 수 없었다. the package는 앞에서 우편으로 무언가를 보냈다는 내용이 나온 뒤에 쓸 수 있습니다.
2) ④ 앞: he returned it to her by mail(우편으로 돌려보냈다).
3) ④ 뒤: Before her eyes lay the handkerchief ~. ‘그녀의 눈앞에 손수건이 놓여 있었다’는 소포를 열어 본 뒤의 장면이며, could hardly believe her eyes와 이어집니다.
[정답 근거] 우편으로 돌려받은 뒤, 손수건의 모습을 묘사하기 전인 ④가 정답입니다.
[오답 분석]
①~③: 아직 소포가 오기 전입니다.
⑤: Before her eyes lay ~가 이미 소포를 연 장면을 보여 주므로, 그 뒤에 소포를 연다는 문장이 오면 순서가 뒤바뀝니다.
[어휘] tragedy 비극 · in tears 울고 있는 · ruin 망치다 · inkblot 잉크 얼룩 · healing 치유', 39, 3),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 요약문 완성 (2점)
[유형 전략] 요약문은 먼저 요약문을 읽고 (A), (B)가 각각 무엇을 묻는지 확인한 뒤 지문을 읽습니다. 지문의 실험 결과나 결론 문장을 요약문의 표현과 짝지어, 같은 뜻을 다른 말로 바꾼(paraphrase) 선택지를 고릅니다.
[풀이 과정]
1) 요약문은 the 비교급, the 비교급 구조입니다: 기회비용이 (A)할수록 사람들은 그 활동을 (B)할 가능성이 낮아진다.
2) 근거: as the opportunity cost of attending class rises relative to the benefits of attending, Bill becomes less likely to go.
3) rises → (A) higher, go(수업에 가다) → (B) choose(선택하다)로 바꿔 쓴 것입니다.
[정답 근거] (A) higher, (B) choose인 ⑤가 정답입니다.
[오답 분석]
①② lower는 기회비용이 올라간다(rises)와 반대입니다.
③ clearer는 근거가 없습니다.
④ (A)는 맞지만 (B) avoid(피하다)로 하면 ‘피할 가능성이 낮아진다’가 되어 결론과 반대입니다.
[어휘] give up 포기하다 · skip 빼먹다 · predict 예측하다 · relative to ~에 비해', 40, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q41.webp]]', '[[img:/naesin/csat-h1-mock/r05/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 장문 제목 (2점)
[유형 전략] 장문 제목은 글 전체의 논지를 담아야 합니다. 도입의 일반적 진술과 후반부의 결론을 연결하여 주제를 정리하고, 글의 일부 사례만 다루는 선택지는 지웁니다.
[풀이 과정]
1) 플라밍고(함께 둥지를 짓고 새끼를 기름), 해마(수컷이 임신), 개구리(아빠가 올챙이를 데리고 다님), 황제펭귄(아빠가 65일 동안 알을 품음)의 예가 이어집니다.
2) 모든 예의 공통점은 새끼를 돌보는 데 적극적인 ‘수컷(아빠)’입니다.
[정답 근거] 동물 세계의 헌신적인 아빠들을 소개하는 글이므로 ③이 제목으로 적절합니다.
[오답 분석]
① 새끼를 버리는 동물은 반대 내용입니다.
② 짝을 고르는 방법은 다루지 않습니다.
④ 황제펭귄은 여러 예 중 하나일 뿐입니다.
⑤ 먹지 않는다는 내용이 나오지만 식습관을 다룬 글이 아닙니다.
[어휘] devoted 헌신적인 · nest site 둥지 터 · pouch (동물의) 주머니 · hatch 부화하다 · take over 넘겨받다', 41, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q42.webp]]', '[[img:/naesin/csat-h1-mock/r05/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 장문 어휘 (2점)
[유형 전략] 장문 어휘 문항은 단락별 흐름을 잡은 뒤 밑줄 낱말을 하나씩 문맥에 넣어 봅니다. 앞뒤 문장과 인과·대조 관계가 맞지 않는 낱말, 반의어로 바꾸면 자연스러워지는 낱말이 정답입니다.
[풀이 과정]
1) (d)가 있는 문장은 알을 낳는 것은 힘든 일(hard work)이라고 한 뒤, 수컷 황제펭귄이 그 점을 어떻게 대하는지 말합니다.
2) 뒤 문장에서 아빠 펭귄은 엄마가 바다에 나간 두 달 동안 아무것도 먹지 않고 알을 품습니다. 엄마의 수고를 ‘무시하는(ignores)’ 것이 아니라 ‘알아주는(appreciates)’ 것입니다.
[정답 근거] (d) ignores는 appreciates로 바꾸어야 하므로 ④가 정답입니다.
[오답 분석]
(a) equality: 암수가 모든 일을 함께하므로 성 ‘평등’이 맞습니다.
(b) natural: 해마에게 수컷 임신은 ‘자연스러운’ 일이라는 뜻으로 맞습니다.
(c) devoted: 새끼를 입에 넣어 다니며 먹지도 않는 아빠이므로 ‘헌신적인’이 맞습니다.
(e) shared: 암수가 번갈아 알을 돌보므로 ‘공유된’ 책임이 맞습니다.
[어휘] devoted 헌신적인 · appreciate 진가를 알아보다 · ignore 무시하다', 42, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q43.webp]]', '[[img:/naesin/csat-h1-mock/r05/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 장문 순서 (2점)
[유형 전략] 장문 순서는 등장인물의 행동과 시간의 흐름을 따라갑니다. 각 단락의 첫 문장이 앞 단락의 마지막 장면과 이어지는지(장소 이동, 대화의 응답, 사건의 결과) 확인합니다.
[풀이 과정]
1) (A) 끝: Monu가 빵 냄새를 좋아한다고 빵집 주인에게 말했다.
2) (C) The next morning: 다음 날 아침, 빵집 주인이 냄새를 훔친다며 돈을 내라고 하고, 경찰을 부르겠다고 위협합니다.
3) (B) Soon a policeman arrived는 (C)의 I''ll call a policeman!과 이어집니다. 경찰은 Monu에게 은화 다섯 개를 내라고 합니다.
4) (D) 경찰은 동전 소리로 냄새값을 치르게 하는 지혜로운 판결을 내립니다. (B)의 five silver coins가 있어야 Drop your coins가 자연스럽습니다.
[정답 근거] (A) → (C) → (B) → (D)이므로 ②가 정답입니다.
[오답 분석]
(B)를 (C)보다 앞에 놓으면 경찰이 왜 왔는지, the baker''s story가 무엇인지 알 수 없습니다.
[어휘] drift (냄새가) 퍼지다 · astonished 깜짝 놀란 · frown 얼굴을 찡그리다 · greedy 욕심 많은 · in shame 부끄러워서', 43, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q44.webp]]', '[[img:/naesin/csat-h1-mock/r05/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 지칭 대상 (2점)
[유형 전략] 지칭 대상 문항은 밑줄 친 대명사가 나오는 문장에서 그 행동을 하는 사람이 누구인지 앞뒤 문맥으로 확인합니다. 대화문 속 you/I는 말하는 사람과 듣는 사람을 기준으로 따집니다.
[풀이 과정]
1) (a) 냄새를 맡고 “멋진 냄새!”라고 말한 사람 → Monu
2) (b) 경찰이 “냄새를 즐기느냐”고 묻는 상대 → Monu
3) (c) “네”라고 대답한 사람 → Monu
4) (d) 돈이 거의 없던 사람 → Monu
5) (e) 경찰이 “동전 소리로 냄새값을 받았다”고 말하는 상대 → 빵집 주인(the baker)
[정답 근거] (e)만 빵집 주인이고 나머지는 Monu이므로 ⑤가 정답입니다.
[오답 분석]
대화문 속 you는 누가 누구에게 하는 말인지 확인해야 합니다. (e)는 the policeman asked the baker 바로 뒤의 말이므로 빵집 주인을 가리킵니다.', 44, 2),
(@csh1_r05, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r05/q45.webp]]', '[[img:/naesin/csat-h1-mock/r05/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 장문 내용 불일치 (2점)
[유형 전략] 장문 내용 불일치는 선택지 순서가 대체로 글의 사건 순서를 따릅니다. 선택지의 핵심어로 해당 단락을 찾아 인물·행동·시간을 정확히 대조합니다.
[풀이 과정]
1) ④ (D)에서 경찰은 Monu에게 동전을 그릇에 떨어뜨리게 한 뒤 Monu, take your money back.이라고 했습니다. 빵집 주인은 동전 소리만 들었을 뿐 돈을 받지 않았습니다.
[정답 근거] Monu는 돈을 돌려받았으므로 ④가 적절하지 않습니다.
[오답 분석]
① rented a small room above a baker''s shop과 일치합니다.
② bought a small biscuit과 일치합니다.
③ "You are stealing my smells!"와 일치합니다.
⑤ the greedy baker hung his head in shame과 일치합니다.', 45, 2);

-- 6회: 2023년 8월 고1 이투스 원본(고1 이투스 23 8모)
INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-CSH1-MOCK-R06', 'NAESIN', '고1 수능 영어 독해 모의고사 6회 · 18~45번 변형문제 6회', '2023년 8월 고1 이투스 원본 · 28문항 · 45분 · 62점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 풀이 전략 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @csh1_r06 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-CSH1-MOCK-R06');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 목적 (2점)
[유형 전략] 글의 목적은 대개 글 후반부의 요청·안내 문장(please, we ask, I would like to, I am writing to)에 드러납니다. 앞부분의 인사·배경 설명에 끌려가지 말고, 필자가 독자에게 결국 무엇을 해 달라고 하는지 한 문장으로 정리한 뒤 선택지와 맞춰 봅니다.
[풀이 과정]
1) 근거 제시: 2년 동안 충실하게 일했고 모든 업무를 제때 마쳤다.
2) 배경: 지난해 급여 인상을 요청했지만 답을 받지 못했고, 최근 더 높은 급여를 제안받았다.
3) 요청: However, because I remain committed to this company ..., I am writing once again to ask you to consider increasing my salary.
[정답 근거] 회사에 남고 싶다며 급여 인상을 다시 요청하는 글이므로 ④가 정답입니다.
[오답 분석]
① 이미 팀장으로 일하고 있으며 새 직책을 제안하지 않습니다.
② 업무 평가는 언급되지 않습니다.
③ ACE Technology의 제안은 배경일 뿐이고, 오히려 이 회사에 남고 싶다(would prefer to stay)고 했습니다. 가장 매력적인 오답입니다.
⑤ 업무량 조정은 요청하지 않습니다.
[어휘] dedication 헌신 · on schedule 예정대로 · raise 급여 인상 · committed to ~에 헌신하는 · consideration 고려', 18, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 심경 변화 (2점)
[유형 전략] 심경 변화는 처음 상황과 전환 이후 상황을 나누어 읽습니다. 사건이 바뀌는 지점(when, suddenly, but, then)을 찾고, 그 앞뒤에서 인물의 감정을 드러내는 표현(형용사, 신체 반응, 혼잣말)을 각각 하나씩 골라 두 감정의 짝을 확인합니다.
[풀이 과정]
1) 전반부: Her heart began to pound, rushed back, searched through every drawer, close to tears → 반지를 잃어버려 당황하고 겁에 질린 상태(panicked)입니다.
2) 전환점: Then her daughter, Jan, suggested checking a local online lost-and-found page.
3) 후반부: took a long, deep breath, smiled for the first time all day → 안도한 상태(relieved)입니다.
[정답 근거] 당황(panicked)에서 안도(relieved)로 바뀌므로 ⑤가 정답입니다.
[오답 분석]
① 처음부터 지루한 상황이 아닙니다.
② 침착한 상태에서 좌절로 가는 흐름과 반대입니다.
③ 희망 → 실망은 결말과 반대입니다.
④ 화를 내는 장면은 없습니다.
[어휘] late 고인이 된 · pound (심장이) 쿵쾅거리다 · scan 훑어보다 · sink onto ~에 털썩 주저앉다 · scroll through (화면을) 넘겨 보다', 19, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 필자의 주장 (2점)
[유형 전략] 필자의 주장은 should, must, need to, it is important to, 명령문처럼 당위를 나타내는 문장에 담깁니다. 예시나 일화는 주장을 뒷받침하는 재료이므로, 일화가 끝난 뒤 필자가 일반화하는 문장을 찾아 그 내용과 같은 선택지를 고릅니다.
[풀이 과정]
1) 문제 제기: 소셜 미디어 때문에 최신 물건을 가져야 한다는 압박이 커졌고, 젊은이의 절반 가까이가 버는 것보다 많이 쓰게 된다.
2) 주장(명령문): Don''t be afraid to tell them honestly that you are on a budget. / suggest cheaper alternatives.
3) 구체적 예: 영화관 대신 집에서 영화 보기, 외식 대신 각자 음식을 가져오는 모임.
[정답 근거] 친구에게 예산 사정을 솔직히 말하고 돈이 덜 드는 활동을 고르라는 ③이 주장입니다.
[오답 분석]
① 할인 구매는 언급되지 않습니다.
② 모든 비싼 모임에 응하지 말라(Instead of agreeing to every expensive outing)고 했으므로 반대입니다.
④ 소셜 미디어는 압박의 원인으로 언급될 뿐, 사용 시간을 줄이라는 주장은 없습니다.
⑤ 수입을 늘리는 방법은 다루지 않습니다.
[어휘] must-have 꼭 가져야 할 것 · gadget 기기 · on a budget 예산이 빠듯한 · outing 외출, 나들이 · alternative 대안', 20, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 밑줄 함의 (3점)
[유형 전략] 밑줄 친 표현은 비유이므로 글자 그대로 해석하지 않습니다. 밑줄 앞뒤에서 같은 대상을 설명하는 문장을 찾아, 비유 속 단어(밑줄의 핵심 명사·동사)가 글에서 무엇에 대응하는지 짝지은 뒤, 그 의미를 풀어 쓴 선택지를 고릅니다.
[풀이 과정]
1) 비유의 출발점: 테세우스의 배는 부품이 모두 바뀌어도 같은 배로 여겨진다. 스포츠 팀도 선수·감독·코치가 모두 바뀌어도 같은 이름이다.
2) the clothes(옷)는 팀 유니폼, 즉 사람이 바뀌어도 그대로 남는 ‘팀’ 자체를 상징합니다.
3) 밑줄 뒤 문장: 팬은 선수를 아끼다가도 그가 다른 팀으로 가면 야유한다 → 팬이 응원하는 것은 선수 개인이 아니라 팀입니다.
[정답 근거] 팬은 누가 입든 팀의 이름과 색(유니폼)에 충성한다는 ③이 정답입니다.
[오답 분석]
① clothes를 글자 그대로 유니폼으로 읽고, 선수의 마음으로 바꾼 오답입니다.
② 성적에 따라 팀을 고른다는 내용은 없습니다. 오히려 100년 전 일로 지금의 팀을 미워할 만큼 팀 자체에 매여 있습니다.
④ 1920년의 Yankees 때문에 지금 팀을 미워한다는 예와 반대입니다. 팀의 역사는 팬에게 큰 의미가 있습니다.
⑤ clothes를 패션으로 읽은 오답입니다. 옷 구매 이유는 다루지 않습니다.
[어휘] preserve 보존하다 · rot 썩다 · have a lot in common with ~와 공통점이 많다 · adore 몹시 좋아하다 · boo 야유하다', 21, 3),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 요지 (2점)
[유형 전략] 요지는 글 전체가 말하려는 한 가지 핵심입니다. 도입의 질문·통념, 대조(however, but), 결론(therefore, in short)을 확인하고, 세부 예시 하나만 다룬 선택지나 지문 단어를 섞어 만든 그럴듯한 선택지는 지웁니다.
[풀이 과정]
1) 통념: 우리 문화는 행동하지 않는 것을 가치 있게 여기지 않고, 행동하는 사람에게 보상한다.
2) 반박(however): 통제한다는 느낌은 착각인 경우가 많고, 행동하고 통제하려는 시도가 오히려 상황을 악화시킨다.
3) 결론: Consider waiting. ... things sometimes improve on their own and that unexpected good results emerge.
[정답 근거] 때로는 기다리는 것이 더 나은 결과를 낳는다는 ④가 요지입니다.
[오답 분석]
①②⑤ 글이 비판하는 통념(측정 가능한 성과, 행동에 대한 보상, 통제 = 강인함)을 그대로 옮긴 오답입니다.
③ 필자의 주장과 정반대입니다.
[어휘] place value on ~을 중시하다 · reward 보상하다 · concrete 구체적인 · illusion 착각 · emerge 나타나다', 22, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 주제 (2점)
[유형 전략] 주제는 글의 핵심 소재와 그에 대한 필자의 관점을 함께 담은 영어 구로 나타납니다. 반복되는 핵심어를 찾고, 글이 그 소재의 무엇(원인, 효과, 과정, 중요성, 한계)을 다루는지 확인하여 범위가 너무 넓거나 좁은 선택지를 지웁니다.
[풀이 과정]
1) 첫 두 문장: 다윈은 지렁이가 흙을 옮기는 데 하는 중요한 역할을 처음 알려 주었다.
2) 이어서 지렁이가 흙에 주는 효과가 나열됩니다: 고운 흙을 위로 올림, 유기물을 흙 속에 섞음, 10년에 1~1.5인치의 흙층, 공기와 배수, 소화 과정을 통한 흙의 비옥화.
3) And that is far from all they do.는 효과를 더 추가하겠다는 신호입니다.
[정답 근거] 지렁이가 흙을 어떻게 개선하는지를 다룬 글이므로 ④가 정답입니다.
[오답 분석]
① 비가 온 뒤 지렁이가 땅 위로 나오는 이유는 다루지 않습니다.
② Darwin은 등장하지만 비글호 항해는 언급되지 않습니다. 인물 이름만 보고 고르기 쉬운 오답입니다.
③ 지렁이를 기르는 방법은 다루지 않습니다.
⑤ 글은 지렁이가 흙을 이롭게 한다는 내용이므로, 지렁이를 해충처럼 막는다는 것은 방향이 반대입니다.
[어휘] organic matter 유기물 · calculation 계산 · drain 물이 빠지다 · enrich 비옥하게 하다', 23, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글의 제목 (2점)
[유형 전략] 제목은 주제를 압축하거나 비유·의문문으로 바꾼 표현입니다. 먼저 글의 주제를 우리말로 정리한 뒤, 그 핵심을 담은 선택지를 고릅니다. 지문에 나온 단어가 그대로 쓰였지만 글의 일부만 다루거나 반대 방향을 말하는 선택지에 주의합니다.
[풀이 과정]
1) 첫 문장: 혁신은 한 천재의 번뜩이는 순간에서 나온다는 통념(myth)을 깨려 한다.
2) 핵심: Big ideas ... grow out of many smaller ideas and improvements made over long periods of time.
3) 예시와 결론: 에디슨 이전에도 전구 특허가 있었고, 전구는 앞선 많은 사람의 작업 위에 세워진 결과였다.
[정답 근거] 큰 아이디어는 많은 작은 아이디어에서 자라난다는 ⑤가 제목으로 적절합니다.
[오답 분석]
① 에디슨이 등장하지만 그의 연구실을 소개하는 글이 아닙니다.
② 발명이 처음에 실패하는 이유는 다루지 않습니다. 앞선 작업이 쌓여 큰 발명이 된다는 것이 핵심입니다.
③ 전구가 도시 생활에 준 영향은 다루지 않습니다.
④ 특허(patents)와 lone genius라는 지문 낱말을 섞었지만, 글은 오히려 한 사람의 공으로 돌리는 생각을 반박합니다.
[어휘] myth 근거 없는 믿음 · insight 통찰 · illustrate 예를 들어 설명하다 · credit A to B A를 B의 공으로 돌리다', 24, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 도표 불일치 (2점)
[유형 전략] 도표 문항은 선택지 문장을 하나씩 도표 수치와 맞춰 봅니다. 비교 표현(more than, twice, the lowest), 순위, 차이 계산이 함정이 되는 경우가 많으므로, 문장에 나온 항목·연도·수치를 도표에서 손가락으로 짚어 가며 확인합니다.
[풀이 과정]
1) ① 양서류 41%가 가장 높고, 상어·가오리 37%가 그다음입니다. (일치)
2) ② 침엽수 34%, 산호 33%로 1%p 차이입니다. (일치)
3) ③ 상어·가오리 37%, 파충류 21%의 두 배는 42%입니다. 37%는 두 배보다 작습니다. (불일치)
4) ④ 조류 13%가 가장 낮습니다. (일치)
5) ⑤ 포유류 26%로 약 4분의 1입니다. (일치)
[정답 근거] 상어·가오리의 비율은 파충류의 두 배가 되지 않으므로 ③이 일치하지 않습니다.
[오답 분석]
배수 표현(twice, three times)은 작은 쪽 수치에 배수를 곱해 큰 쪽과 비교하면 빠르게 판단할 수 있습니다.
[어휘] assess 평가하다 · threatened 멸종 위기에 처한 · extinction 멸종 · a quarter 4분의 1', 25, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 불일치 (2점)
[유형 전략] 인물 내용 일치 문항은 선택지 순서가 대체로 글의 순서와 같습니다. 선택지의 핵심어(연도, 장소, 숫자, 고유명사)를 먼저 보고 지문에서 해당 부분을 찾아 정확히 대조합니다. 일부만 바꾼 진술(시기, 횟수, 주체)에 주의합니다.
[풀이 과정]
1) ① Charles Price, who happened to see her running to catch a train after school. 경기장이 아니라 방과 후 기차를 타려고 달리는 모습을 보고 재능을 발견했습니다.
[정답 근거] ①이 글의 내용과 일치하지 않습니다.
[오답 분석]
② the only American athlete to reach the final of the women''s 100 meters와 일치합니다.
③ In 1931, ... seriously injured in a plane crash와 일치합니다.
④ two years to walk normally와 일치합니다.
⑤ won her second Olympic gold medal과 일치합니다.
[어휘] equal (기록과) 타이를 이루다 · kneel 무릎을 꿇다 · relay 계주', 26, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 안내문 불일치 (2점)
[유형 전략] 안내문은 항목별로 정보가 정리되어 있으므로 선택지의 핵심어(날짜, 시간, 대상, 비용, 신청 방법)로 해당 항목을 바로 찾습니다. 주의 사항·예외 조건(only, not, must)이 함정으로 자주 쓰입니다.
[풀이 과정]
1) ⑤ Notes: Pets are not allowed in the fields. 반려동물은 들어갈 수 없습니다.
[정답 근거] ⑤가 일치하지 않습니다.
[오답 분석]
① You will have 90 minutes와 일치합니다.
② Free for children 4 years old or younger와 일치합니다.
③ Extra baskets cost $5 each.와 일치합니다.
④ Saturdays and Sundays: 7 a.m. – 2 p.m.과 일치합니다.
[어휘] organic 유기농의 · picker 따는 사람 · in case of ~의 경우에', 27, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 안내문 일치 (2점)
[유형 전략] 안내문 일치 문항은 정답이 하나뿐이므로 선택지를 하나씩 대조하며 틀린 것을 지웁니다. 날짜·요일, 연령, 요금, 포함 여부, 사전 등록 여부처럼 숫자와 조건이 바뀐 부분을 꼼꼼히 확인합니다.
[풀이 과정]
1) ④ Registration fee: $8 per person (Children under 6 participate for free.) 6세 미만은 무료입니다.
[정답 근거] ④가 안내문과 일치합니다.
[오답 분석]
① at 8:30 a.m.로 오전에 시작합니다.
② Registration opens at 7:30 a.m. on the day of the event. 당일에 등록합니다.
③ The course starts and ends at Maple Grove Park. 출발점과 도착점이 같습니다.
⑤ bicycles are not allowed on the course.
[어휘] pavilion 정자, 대형 천막 · registration 등록 · coupon 할인권', 28, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법 (3점)
[유형 전략] 어법 문항은 밑줄마다 확인할 문법 포인트를 먼저 정합니다(동사 자리·수일치, 관계사와 접속사, 분사·to부정사, 병렬 구조, 대명사, 형용사·부사). 특히 문장의 주어와 본동사를 먼저 찾으면 수일치와 준동사 문제를 빠르게 판단할 수 있습니다.
[풀이 과정]
1) ② there be 구문은 be동사 뒤에 오는 명사가 진짜 주어입니다. 진짜 주어는 a book(단수)이므로 were가 아니라 was가 되어야 합니다. 앞에 장소 부사구(On the library''s shelves)가 있어도 마찬가지입니다.
2) ① it seems likely that ~: ‘~인 것 같다’의 형용사 likely가 맞습니다.
3) ③ 선행사 the astronomer Aristarchus of Samos(사람)를 받는 계속적 용법의 관계대명사 who가 맞습니다.
4) ④ 형용사 far를 꾸미는 부사 enormously가 맞습니다.
5) ⑤ wait (for) ~ to do: ‘~하기 위해 기다리다’의 to부정사로 맞습니다.
[정답 근거] 진짜 주어 a book과 수가 맞지 않는 ② were가 틀렸습니다(→ was).
어법 포인트: there be 구문·도치 구문의 수일치 — 동사 뒤에 오는 명사(진짜 주어)를 찾아 수를 맞춥니다. 복수 명사(shelves)가 동사 앞에 있어도 그것은 주어가 아닙니다.
[어휘] estimate 추정하다 · collapse 붕괴하다 · deliberately 고의로 · fraction 일부 · tempting 마음을 끄는', 29, 3),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘 (2점)
[유형 전략] 어휘 문항은 글의 흐름(원인과 결과, 대조, 예시)을 먼저 잡고, 밑줄 친 낱말이 그 흐름과 반대 방향인지 확인합니다. 밑줄 낱말을 반의어로 바꿔 넣었을 때 문맥이 자연스러워지면 그것이 정답입니다.
[풀이 과정]
1) 글의 흐름: 인은 적으면 필수적이지만 많으면 독성이 있다 → 농장에서 흘러나온 인이 강과 바다를 오염시켜 해양 생물을 파괴한다.
2) ③ 문장의 thus(그래서)는 앞 문장의 결과를 말합니다. 아이오와의 옥수수 농부가 멀리 떨어진 멕시코만의 물고기를 ‘보호하는(protecting)’ 것이 아니라 ‘죽이고 있을(killing)’ 수 있다는 뜻이어야 합니다.
[정답 근거] ③ protecting은 killing으로 바꾸어야 하므로 적절하지 않습니다.
[오답 분석]
① 적은 양에서는 식물 성장에 ‘필수적’이다로 맞습니다.
② 유출수가 물을 ‘오염시킨다’로 맞습니다.
④ 서식지가 파괴되어 동식물이 ‘사라진다’로 맞습니다.
⑤ 인류 문명의 기반까지 ‘약화시킬’ 수 있다로 맞습니다.
[어휘] fertilizer 비료 · excessive 지나친 · toxic 독성이 있는 · devastate 황폐하게 하다 · wipe out 전멸시키다', 30, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 빈칸 문장: 거리감과 두려움은 질문받는 순간 당신의 ‘무엇’에 달려 있다.
2) 예시: 건물 아래에서 올려다볼 때와 위에서 내려다볼 때 같은 높이를 다르게 판단한다. depending on where you were(어디에 있느냐에 따라)가 결정적 단서입니다.
3) 연구 결과: 위에 있는 사람이 아래에 있는 사람보다 높이를 훨씬 크게 추정한다.
[정답 근거] 사람이 서 있는 위치(position)에 따라 거리 판단이 달라지므로 ⑤가 정답입니다.
[오답 분석]
① 두려움(fear)에서 감정을 떠올리게 하는 오답입니다. 예시는 기분이 아니라 서 있는 곳을 바꿉니다.
② 성격, ③ 나이, ④ 상상력은 예시에서 바뀌는 조건이 아닙니다.
[어휘] evolutionary 진화의 · ancestor 조상 · a body of research 일련의 연구 · estimate 추정하다 · drop 높이, 낙차', 31, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 인본주의의 핵심: 우리 자신이 의미의 궁극적 원천이며, 외부의 힘 대신 자신의 감정과 욕구에 의지할 수 있다.
2) 루소의 말 바로 앞: 그는 삶의 규칙을 자기 마음 깊은 곳(in the depths of his own heart)에서 찾았다.
3) 빈칸 뒤: what I feel to be good is good — 선악의 기준이 자기 느낌이므로, 무엇을 할지 정할 때도 자기 자신에게만 물으면 된다는 말이 들어가야 합니다.
[정답 근거] 자기 자신에게만 물어보면 된다는 ①이 정답입니다.
[오답 분석]
② 사회 규칙, ③ 타인의 조언은 외부 권위로, 인본주의가 대신하려는 대상입니다.
④ 확신할 수 없다는 말은 what I feel to be good is good(단언)과 맞지 않습니다.
⑤ do what feels good과 반대입니다.
[어휘] ultimate 궁극적인 · free will 자유 의지 · authority 권위 · urge 촉구하다 · sum up 요약하다', 32, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 첫 문장이 주제문입니다: 놀라운 일을 해낸 사람의 삶에도 실패담이 있다.
2) 필자는 화려한 소개를 받은 직후 자신의 ‘실패 이력서’를 말한다. 학생들은 처음엔 믿지 못하다가 안도(relief)한다.
3) 안도의 이유: 성공한 사람도 실패를 겪었다는 사실, 즉 실패하고도 큰일을 해낼 수 있다는 깨달음입니다.
[정답 근거] 실패하고도 결국 큰일을 이룰 수 있다는 ③이 정답입니다.
[오답 분석]
①② 실패를 피하거나 실수 없이 성공한다는 것은 모든 성공담에 실패가 있다는 주제와 반대입니다.
④ 실패를 숨기는 것은 필자가 비판하는 태도입니다(we do others ... a disservice).
⑤ 운은 언급되지 않습니다.
[어휘] accomplish 성취하다 · remarkable 놀라운 · edit out 삭제하다 · make a point of -ing 반드시 ~하다 · disbelief 믿기지 않음', 33, 3),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 경제학적 설명: 혼자 사업할 때의 비용(고객 찾기, 재무 관리, 고객과의 소통)이 높으면 사람들은 회사에 들어간다.
2) 변화: 인터넷 덕분에 이런 비용이 크게 줄거나 사라졌다 — 접수원, 장부 정리, 사무실 임대가 필요 없어짐.
3) In short로 시작하는 빈칸 문장은 이 내용을 요약합니다. 비용이 줄었다 = 예전만큼 큰 투자가 필요 없다.
[정답 근거] 전통적 직장 없이 돈을 버는 데 예전만큼 큰 투자가 들지 않는다는 ②가 정답입니다.
[오답 분석]
① 소득의 안정성은 다루지 않습니다.
③ 온라인으로 일한다는 내용과 반대입니다.
④ 전문 기술을 가진 밀레니얼이 예로 나오지만, ‘숙련자만 가능하다’는 제한은 글의 요점이 아닙니다.
⑤ 주말 여가는 언급되지 않습니다.
[어휘] long for ~을 갈망하다 · practice (의사·변호사의) 개업 사무실 · receptionist 접수원 · altogether 완전히 · earn a living 생계를 꾸리다', 34, 3),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 무관한 문장 (2점)
[유형 전략] 무관한 문장 문항은 첫 문장(주제문)이 제시한 소재와 방향을 확인한 뒤 각 문장이 그 방향을 이어 가는지 봅니다. 정답 문장은 지문의 핵심어를 일부 포함하지만 논점이 다른 쪽(다른 대상, 다른 측면)으로 벗어난 경우가 많습니다.
[풀이 과정]
1) 첫 문장의 화제: 비난 본능 — 나쁜 일에 대해 분명하고 단순한 이유(누군가의 탓)를 찾으려는 충동.
2) ①③은 이 본능이 생기는 이유, ④⑤는 이 본능이 배움과 문제 해결을 막는다는 부작용을 설명합니다.
3) ②는 자기 실수에 책임을 지는 것이 성숙한 리더의 첫걸음이라는 내용으로, ‘남을 탓하는 본능’의 원인과 부작용이라는 흐름에서 벗어난 교훈입니다.
[정답 근거] 리더십과 책임감을 다룬 ②가 흐름과 관계없습니다.
[오답 분석]
① 나쁜 일이 생기면 나쁜 의도를 가진 누군가가 있다고 가정한다 — 비난 본능의 모습입니다.
③ 누군가 원해서 일이 일어났다고 믿어야 세상이 덜 두렵다 — 비난 본능이 생기는 이유입니다. ②를 빼면 ①과 바로 이어집니다.
④ 탓할 사람을 정하면 다른 설명을 찾지 않는다 — 본능의 부작용(배움을 막음)입니다.
⑤ This는 ④의 내용을 받아 문제 해결 능력이 약해진다는 결과를 말합니다.
[어휘] instinct 본능 · urge 충동 · intention 의도 · point the finger at ~을 비난하다', 35, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글의 순서 (2점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 엄마는 태어나기 전부터 아이의 음식 선호에 영향을 줄 수 있다.
2) (A)의 for example은 주어진 글의 구체적 예(카레를 좋아하는 임신부)입니다.
3) (C)의 however는 출생 전의 영향(A)과 대조하여 대부분의 선호는 출생 후에 형성되며, 익숙함이 핵심이라고 합니다.
4) (B)의 this process는 (C)의 ‘여러 번 맛보며 익숙해지는 과정’을 가리키며, 그 과정을 서두르면 안 된다고 마무리합니다.
[정답 근거] 주어진 글 → (A) → (C) → (B)이므로 ①이 정답입니다.
[오답 분석]
(C)를 주어진 글 바로 뒤에 놓으면 however가 대조할 출생 전의 예(A)가 아직 나오지 않은 상태가 됩니다.
(B)의 this process는 (C)의 익숙해지는 과정이 나온 뒤에만 가리킬 대상이 생깁니다.
[어휘] inherit 물려받다 · familiarity 익숙함 · treat 간식, 보상 · distort 왜곡하다', 36, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글의 순서 (3점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 최초의 정기적 체중계는 동전을 넣는 앉은뱅이 저울(penny scale)이었다.
2) (B)의 These platform scales는 주어진 글의 저울을 받아, 그 저울이 놓인 장소와 ‘체중 수첩(weight booklet)’을 소개합니다.
3) (C)의 This booklet은 (B)의 수첩을 가리키며, 저울이 커서 가정용으로는 맞지 않았다고 합니다.
4) (A)는 (C)의 ‘가정용으로 부적합’을 받아, 욕실 체중계가 나온 뒤에야 집에서 잴 수 있게 되었다고 마무리합니다.
[정답 근거] 주어진 글 → (B) → (C) → (A)이므로 ③이 정답입니다.
[오답 분석]
(C)를 (B)보다 앞에 놓으면 This booklet이 가리킬 수첩이 없습니다.
(A)의 only after는 집에서 쓸 수 없었다는 (C)의 내용이 먼저 나와야 자연스럽습니다.
[어휘] weigh 무게를 재다 · booklet 소책자 · track 추적하다 · suitable 적합한', 37, 3),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 문장 삽입 (2점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장: 물론 실제로는(In practice) 둘 사이의 경계가 그렇게 분명하지 않다. 앞에는 두 분야의 ‘차이’가, 뒤에는 경계가 흐려지는 ‘실제 사례’가 와야 합니다.
2) ④ 앞: 정신 의학(약물·수술)과 심리학(치료 요법)의 차이, 그리고 그 차이가 각 분야의 훈련 방식을 만들어 왔다는 내용.
3) ④ 뒤: 많은 정신과 의사가 대화 치료를 배우고, 임상 심리학자도 의학적 치료를 잘 안다 — 경계가 흐려진 실제 모습입니다.
[정답 근거] 두 분야의 차이 설명이 끝나고 겹치는 사례가 시작되는 ④가 정답입니다.
[오답 분석]
①②: 아직 두 분야의 차이 설명이 진행 중입니다.
③: 뒤에 This difference in viewpoint has long shaped ~가 차이를 다시 강조하므로, 경계가 분명하지 않다는 말과 충돌합니다.
⑤: Likewise는 앞의 정신과 의사 사례에 같은 종류의 사례를 덧붙이는 말이라, 그 사이에 들어갈 수 없습니다.
[어휘] branch 분야 · disorder 장애 · call for ~을 필요로 하다 · clinical 임상의 · neuroscience 신경 과학', 38, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 문장 삽입 (3점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장: "have fed," "have maintained," "have modulated"(현재완료)가 더 나은 표현일 것이다 — 지구 온난화가 이 기능들을 위협하기 때문이다.
2) ② 앞: feed, maintain, modulate가 현재형으로 쓰인 문장들(바다는 우리를 먹여 살리고, 계절을 유지하고, 기온을 조절한다).
3) ② 뒤: Already, fish populations have moved ~. 온난화의 피해 사례가 시작됩니다. 주어진 문장이 ‘위협’을 처음 꺼내야 Already가 자연스럽습니다.
[정답 근거] 세 동사가 나온 직후, 피해 사례가 시작되기 전인 ②가 정답입니다.
[오답 분석]
①: 아직 feed, maintain, modulate가 나오지 않았습니다.
③~⑤: 이미 피해 사례가 이어지는 중이라, 동사의 시제를 바꾸자는 말이 들어가면 흐름이 끊깁니다.
[어휘] dominant 지배적인 · protein 단백질 · current 해류 · absorb 흡수하다 · sustain 지탱하다', 39, 3),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 요약문 완성 (2점)
[유형 전략] 요약문은 먼저 요약문을 읽고 (A), (B)가 각각 무엇을 묻는지 확인한 뒤 지문을 읽습니다. 지문의 실험 결과나 결론 문장을 요약문의 표현과 짝지어, 같은 뜻을 다른 말로 바꾼(paraphrase) 선택지를 고릅니다.
[풀이 과정]
1) 요약문 분석: 영화 속 인물들은 만화 속 인물들과 같은 단순한 방식으로 (A)되며, 이것이 영화를 (B)하게 만든다.
2) (A) 근거: Not only are the colors bold and simple, but so are the personalities of the characters. 인물들이 만화처럼 단순하게 ‘그려진다(drawn)’. just as he was ... drawn in the comic books도 같은 말을 씁니다.
3) (B) 근거: that is what makes it great entertainment → ‘재미있는(entertaining)’.
[정답 근거] (A) drawn, (B) entertaining인 ②가 정답입니다.
[오답 분석]
① (B) realistic은 more like cardboard cut-outs than real people과 반대입니다.
③ (A) praised는 인물이 칭찬받는다는 뜻이 되어 맞지 않고, (B) serious도 great entertainment와 반대입니다.
④ developed … complex는 neither the space nor the desire to develop characters in great depth와 반대입니다.
⑤ (A) changed는 만화와 같은 방식이라는 내용과 맞지 않고, disappointing은 결론과 반대입니다.
[어휘] star (배우가) 주연을 맡다 · bold 선명한 · at a glance 한눈에 · in depth 깊이 있게 · satisfying 만족스러운', 40, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q41.webp]]', '[[img:/naesin/csat-h1-mock/r06/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 장문 제목 (2점)
[유형 전략] 장문 제목은 글 전체의 논지를 담아야 합니다. 도입의 일반적 진술과 후반부의 결론을 연결하여 주제를 정리하고, 글의 일부 사례만 다루는 선택지는 지웁니다.
[풀이 과정]
1) 첫 단락: 성숙한 사람은 상황에 대한 책임을 전적으로 지며, 바꿀 수 없는 일에 화내지 않기로 미리 결심한다.
2) 둘째 단락: 특히 과거의 불행한 경험 때문에 지금 화내지 않는다. 과거를 놓아주고 목표와 미래를 생각하라.
3) 두 단락을 묶으면 ‘성숙한 사람은 책임을 지고 과거를 놓아준다’가 됩니다.
[정답 근거] ⑤가 제목으로 적절합니다.
[오답 분석]
① 인내(endure)가 언급되지만 성공의 열쇠를 다루는 글이 아닙니다.
② 문제를 즉시 해결하라는 것이 아니라, 통제할 수 없는 일에 화내지 말라는 것입니다.
③ 남의 실수에서 배우는 내용은 없습니다.
④ 분노를 자유롭게 표현하라는 것은 글과 반대입니다.
[어휘] mature 성숙한 · take charge of ~을 책임지다 · circumstance 상황 · limitless 무한한', 41, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q42.webp]]', '[[img:/naesin/csat-h1-mock/r06/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 장문 어휘 (2점)
[유형 전략] 장문 어휘 문항은 단락별 흐름을 잡은 뒤 밑줄 낱말을 하나씩 문맥에 넣어 봅니다. 앞뒤 문장과 인과·대조 관계가 맞지 않는 낱말, 반의어로 바꾸면 자연스러워지는 낱말이 정답입니다.
[풀이 과정]
1) (a)가 있는 문장의 such a person은 앞 문장의 ‘상황에 전적으로 책임을 지는 성숙한 사람’입니다.
2) 그런 사람이 문제가 생겼을 때 행동하지 않고 불평만 하는 모습은 상상하기 ‘어려워야(hard)’ 흐름이 맞습니다. easy → hard
[정답 근거] (a) easy는 hard로 바꾸어야 하므로 ①이 정답입니다.
[오답 분석]
(b) take: 미리 결심함으로써 삶을 ‘책임진다’로 맞습니다.
(c) allow: 과거 때문에 지금 화내도록 ‘허락하지’ 않는다로 맞습니다.
(d) stop: The good news 뒤이므로 과거를 곱씹는 것을 언제든 ‘멈출’ 수 있다로 맞습니다. 뒤의 let it go와도 이어집니다.
(e) start: 과거를 놓고 목표와 미래를 생각하기 ‘시작한다’로 맞습니다.
[어휘] recall 떠올리다 · let ~ go ~을 놓아주다', 42, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q43.webp]]', '[[img:/naesin/csat-h1-mock/r06/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 장문 순서 (2점)
[유형 전략] 장문 순서는 등장인물의 행동과 시간의 흐름을 따라갑니다. 각 단락의 첫 문장이 앞 단락의 마지막 장면과 이어지는지(장소 이동, 대화의 응답, 사건의 결과) 확인합니다.
[풀이 과정]
1) (A) 끝: 버스 터미널에서 주소록이 든 가방을 도둑맞았다.
2) (D) Andrew began to panic은 (A)의 상황에 대한 반응이며, 주소록이 없어 형에게 연락할 수 없다는 문제와 경찰서에 가는 장면이 이어집니다.
3) (C) After listening carefully는 (D)의 끝(Andrew explained what had happened)과 이어지고, 경찰관이 형에게 연락할 다른 방법을 묻습니다.
4) (B) Then Andrew suddenly remembered ~는 (C)의 질문에 대한 답이며, 전화 연결과 버스 정류장까지 안내로 이야기가 끝납니다.
[정답 근거] (A) → (D) → (C) → (B)이므로 ⑤가 정답입니다.
[오답 분석]
(C)의 the officer는 (D)에서 경찰관을 만난 뒤에만 쓸 수 있고, (B)는 (C)의 질문(Is there any other way ~?)에 대한 답입니다.
[어휘] head to ~로 향하다 · set down 내려놓다 · reach 연락하다', 43, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q44.webp]]', '[[img:/naesin/csat-h1-mock/r06/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 지칭 대상 (2점)
[유형 전략] 지칭 대상 문항은 밑줄 친 대명사가 나오는 문장에서 그 행동을 하는 사람이 누구인지 앞뒤 문맥으로 확인합니다. 대화문 속 you/I는 말하는 사람과 듣는 사람을 기준으로 따집니다.
[풀이 과정]
1) (a) 계산대에 가서 가방을 잠시 내려놓은 사람 → Andrew
2) (b) 경찰관에게 형이 일하는 식당 이름을 말한 사람 → Andrew
3) (c) 너무 안도하여 경찰관에게 거듭 감사한 사람 → Andrew
4) (d) 이야기를 듣고 자세한 내용을 받아 적은 사람 → 경찰관(the officer)
5) (e) 주소록이 없어 형에게 연락할 방법이 없던 사람 → Andrew
[정답 근거] (d)만 경찰관이고 나머지는 Andrew이므로 ④가 정답입니다.
[오답 분석]
(d)는 After listening carefully, the officer said ~ 바로 다음 문장의 주어로, 앞 문장의 주어 the officer를 받습니다.
(a)(b)(c)(e)는 가방을 잃어버리고 형을 찾는 Andrew의 행동입니다.', 44, 2),
(@csh1_r06, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r06/q45.webp]]', '[[img:/naesin/csat-h1-mock/r06/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 장문 내용 불일치 (2점)
[유형 전략] 장문 내용 불일치는 선택지 순서가 대체로 글의 사건 순서를 따릅니다. 선택지의 핵심어로 해당 단락을 찾아 인물·행동·시간을 정확히 대조합니다.
[풀이 과정]
1) ① (A) Andrew arrived in New York with a friend. 친구와 함께 도착했습니다.
[정답 근거] ①이 글의 내용과 일치하지 않습니다.
[오답 분석]
② his address book was inside와 일치합니다.
③ His friend suggested that they go to the police.와 일치합니다.
④ "Do you know your brother''s phone number?"와 일치합니다.
⑤ his brother worked at an Italian restaurant called Napoli와 일치합니다.', 45, 2);

-- 7회: 2019년 4월 고1 종로 원본(고1 종로 19 4모)
INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-CSH1-MOCK-R07', 'NAESIN', '고1 수능 영어 독해 모의고사 7회 · 18~45번 변형문제 7회', '2019년 4월 고1 종로 원본 · 28문항 · 45분 · 62점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 풀이 전략 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @csh1_r07 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-CSH1-MOCK-R07');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글의 목적 (2점)
[유형 전략] 글의 목적은 대개 글 후반부의 요청·안내 문장(please, we ask, I would like to, I am writing to)에 드러납니다. 앞부분의 인사·배경 설명에 끌려가지 말고, 필자가 독자에게 결국 무엇을 해 달라고 하는지 한 문장으로 정리한 뒤 선택지와 맞춰 봅니다.
[풀이 과정]
1) 도입: we would like to remind you of a benefit available to you and your family members. 직원과 가족이 누릴 수 있는 혜택을 알려 주겠다는 예고입니다.
2) 혜택의 내용: 직원과 부양가족에게 최대 3회의 상담을 무료로 제공하는 프로그램(Employee Assistance Program)에 자동 등록되어 있다.
3) 권유: We encourage you to take advantage of this opportunity. 예약 방법과 개인 정보 보호도 함께 안내합니다.
[정답 근거] 직원과 부양가족을 위한 무료 상담 프로그램을 알리고 이용을 권하는 글이므로 ⑤가 정답입니다.
[오답 분석]
① 건강 검진은 언급되지 않습니다.
② 상담 센터의 위치 변경은 없습니다.
③ You can make an appointment at any time은 예약 방법일 뿐, 운영 시간 변경이 아닙니다.
④ 만족도 조사는 언급되지 않습니다.
[어휘] enroll 등록하다 · free of charge 무료로 · voluntary 자발적인 · rest assured 안심하다 · take advantage of ~을 이용하다', 18, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 심경 (2점)
[유형 전략] 심경 문항은 인물이 처한 상황과 감정을 드러내는 표현(형용사, 표정, 행동, 속마음)을 모아 하나의 감정으로 정리합니다. 부정적 표현이 일부 섞여 있어도 글 전체의 분위기와 마지막 장면의 감정이 무엇인지 확인합니다.
[풀이 과정]
1) 감정 표현을 모읍니다: Karolina smiled to herself, could be this much fun, felt sure that moving ... had been the right decision, My sweet boy, another wonderful day.
2) 아이들이 시끄럽고 냄새가 난다는 부정적 표현이 있지만, 바로 But으로 이어져 아이들의 좋은 점을 강조합니다.
3) 전체적으로 지금의 상황과 이사 결정에 기뻐하고 만족하는 마음입니다.
[정답 근거] 기쁘고 만족스러운 심경이므로 ②가 정답입니다.
[오답 분석]
① 외로움은 드러나지 않습니다.
③ 걱정하는 표현은 없습니다.
④ 이사를 후회하지 않고 오히려 잘했다고 생각합니다.
⑤ noisy, did not always smell great에서 떠올리기 쉬운 오답이지만, But 뒤의 내용과 미소가 핵심입니다.
[어휘] burst into ~을 터뜨리다 · polite 예의 바른 · glance at ~을 힐끗 보다 · content 만족한', 19, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 필자의 주장 (2점)
[유형 전략] 필자의 주장은 should, must, need to, it is important to, 명령문처럼 당위를 나타내는 문장에 담깁니다. 예시나 일화는 주장을 뒷받침하는 재료이므로, 일화가 끝난 뒤 필자가 일반화하는 문장을 찾아 그 내용과 같은 선택지를 고릅니다.
[풀이 과정]
1) 근거 1: 핵무기는 모든 것을 파괴하며, 이미 두 번 사용되었다.
2) 근거 2: 인류를 여러 번 멸망시킬 수 있는 양의 핵탄두가 있고, 체르노빌 사고는 그 파괴력의 일부만 보여 준다.
3) 주장: As long as nuclear weapons remain on Earth, the survival of the human race will remain at risk. ... they must be eliminated.
[정답 근거] 인류의 생존을 위해 핵무기를 없애야 한다는 ⑤가 주장입니다.
[오답 분석]
① 국제기구는 언급되지 않습니다.
② 교육은 다루지 않습니다.
③ 체르노빌은 핵의 파괴력을 보여 주는 예일 뿐, 발전소 점검을 주장하지 않습니다.
④ 피해 복구는 주장의 초점이 아닙니다.
[어휘] spare 남겨 두다 · guarantee 보장하다 · wipe out 전멸시키다 · unlivable 살 수 없는 · eliminate 없애다', 20, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 밑줄 함의 (3점)
[유형 전략] 밑줄 친 표현은 비유이므로 글자 그대로 해석하지 않습니다. 밑줄 앞뒤에서 같은 대상을 설명하는 문장을 찾아, 비유 속 단어(밑줄의 핵심 명사·동사)가 글에서 무엇에 대응하는지 짝지은 뒤, 그 의미를 풀어 쓴 선택지를 고릅니다.
[풀이 과정]
1) keep one''s word는 ‘약속을 지키다’라는 뜻입니다. 따라서 선생님이 한 약속이 무엇인지 찾아야 합니다.
2) 약속: if you can find another hand in this classroom as dirty as that one, I will let you off.(이만큼 더러운 손을 찾으면 봐주겠다)
3) Webster가 더러운 왼손을 내밀어 조건을 충족했으므로, 선생님은 그를 벌하지 않고 보내 줄 수밖에 없었습니다.
[정답 근거] 벌을 주지 않고 보내 준다는 ③이 정답입니다.
[오답 분석]
① 약속과 반대입니다.
② 손이 더럽다는 상황에서 떠올리기 쉬운 오답이지만 약속의 내용이 아닙니다.
④ 다른 학생들의 판단은 언급되지 않습니다.
⑤ 변명할 기회는 약속의 내용이 아닙니다.
[어휘] stroke 한 번 때림 · palm 손바닥 · rub off 문질러 없애다 · sternly 엄하게 · have no choice but to ~할 수밖에 없다', 21, 3),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글의 요지 (2점)
[유형 전략] 요지는 글 전체가 말하려는 한 가지 핵심입니다. 도입의 질문·통념, 대조(however, but), 결론(therefore, in short)을 확인하고, 세부 예시 하나만 다룬 선택지나 지문 단어를 섞어 만든 그럴듯한 선택지는 지웁니다.
[풀이 과정]
1) 비유: 마음에 들지만 꽉 끼는 신발을 사서 계속 신다가 발만 아프고 반품도 못 하게 된다.
2) 적용: Don''t let your job turn out like those shoes. 신발 이야기는 직업에 대한 비유입니다.
3) 결론: Make sure the fit is a good one from the very beginning. 처음부터 자신에게 잘 맞는 일을 골라야 한다.
[정답 근거] 처음부터 자신에게 맞는 직업을 선택하라는 ①이 요지입니다.
[오답 분석]
② 열정을 키우는 방법이 아니라, 맞지 않는 일이 열정을 몰아낸다고 경고합니다.
③ 반품은 비유 속 세부 내용일 뿐입니다.
④ 시간이 지나면 괜찮아질 것이라는 생각은 비유 속 주인공의 착각으로, 글이 경계하는 태도입니다.
⑤ 판매원의 말은 일화의 일부일 뿐 요지가 아닙니다.
[어휘] stretch 늘이다 · swollen 부은 · sore 아픈 · drive out 몰아내다 · fit 맞음, 적합성', 22, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글의 주제 (2점)
[유형 전략] 주제는 글의 핵심 소재와 그에 대한 필자의 관점을 함께 담은 영어 구로 나타납니다. 반복되는 핵심어를 찾고, 글이 그 소재의 무엇(원인, 효과, 과정, 중요성, 한계)을 다루는지 확인하여 범위가 너무 넓거나 좁은 선택지를 지웁니다.
[풀이 과정]
1) 첫 문장: 빵은 흔히 삶의 기본 필수품과 생활 조건 전반을 상징한다(stands for).
2) 이어지는 예: breadwinner, putting bread on the table, the greatest thing since sliced bread, "Peace, Land, and Bread," 요정을 막는 힘, breadbasket, 빵과 소금으로 손님 맞이.
3) 모든 예가 빵이 실제 음식이 아니라 다른 의미를 나타내는 경우입니다.
[정답 근거] 여러 문화에서 빵이 갖는 상징적 의미를 다룬 ②가 주제입니다.
[오답 분석]
① 빵 만드는 역사는 다루지 않습니다.
③ 러시아 혁명의 구호는 여러 예 중 하나일 뿐입니다.
④ 건강한 섭취법은 언급되지 않습니다.
⑤ 종교 의식은 다루지 않습니다.
[어휘] necessity 필수품 · household 가정 · revolution 혁명 · slogan 구호 · offering 제공물', 23, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글의 제목 (2점)
[유형 전략] 제목은 주제를 압축하거나 비유·의문문으로 바꾼 표현입니다. 먼저 글의 주제를 우리말로 정리한 뒤, 그 핵심을 담은 선택지를 고릅니다. 지문에 나온 단어가 그대로 쓰였지만 글의 일부만 다루거나 반대 방향을 말하는 선택지에 주의합니다.
[풀이 과정]
1) 도입: 거절은 하기도 듣기도 어렵고, 이메일은 나쁜 소식을 미루게 만든다.
2) 핵심 1: When it comes to rejection, a quick response is almost always appreciated.(빨리)
3) 핵심 2: Leaving the door half open does not help either. 확고한 거절이 애매한 “maybe”보다 훨씬 친절하다.(분명히)
[정답 근거] 거절은 빠르고 분명하게 하라는 ②가 제목으로 적절합니다.
[오답 분석]
① 이메일은 오히려 나쁜 소식을 미루게 만드는 도구로 소개됩니다.
③ Jack Welch의 말과 정반대입니다.
④ 거절을 기회로 바꾸는 방법은 다루지 않습니다.
⑤ 정중한 이메일 작성법이 아니라 거절의 방식이 핵심입니다.
[어휘] instinct 본능 · shield 보호하다 · put off 미루다 · rejection 거절 · get ~ over with ~을 끝내다', 24, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 도표 불일치 (2점)
[유형 전략] 도표 문항은 선택지 문장을 하나씩 도표 수치와 맞춰 봅니다. 비교 표현(more than, twice, the lowest), 순위, 차이 계산이 함정이 되는 경우가 많으므로, 문장에 나온 항목·연도·수치를 도표에서 손가락으로 짚어 가며 확인합니다.
[풀이 과정]
1) ① Machine drives 49.5%로 거의 절반입니다. (일치)
2) ② 두 번째는 Process heating 11.2%입니다(HVAC 9.3%, Other 9.0%). (일치)
3) ③ HVAC 9.3% × 5 = 46.5% < 49.5%이므로 다섯 배보다 큽니다. (일치)
4) ④ Electrochemical 7.0% > Lighting 6.4%. (일치)
5) ⑤ Process cooling & refrigeration은 7.6%, Other process & facility uses는 9.0%로 같지 않습니다. (불일치)
[정답 근거] 두 항목의 비율이 다르므로 ⑤가 일치하지 않습니다.
[오답 분석]
원그래프는 항목이 많아 비슷한 크기의 조각을 헷갈리기 쉽습니다. 조각의 크기를 눈으로 비교하지 말고 표시된 수치로 확인합니다.
[어휘] end-use 최종 용도 · account for (비율을) 차지하다 · share 비율', 25, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 불일치 (2점)
[유형 전략] 인물 내용 일치 문항은 선택지 순서가 대체로 글의 순서와 같습니다. 선택지의 핵심어(연도, 장소, 숫자, 고유명사)를 먼저 보고 지문에서 해당 부분을 찾아 정확히 대조합니다. 일부만 바꾼 진술(시기, 횟수, 주체)에 주의합니다.
[풀이 과정]
1) ② Like most girls of her time, Christina was educated at home, since few educational opportunities were open to women. 학교가 아니라 집에서 교육을 받았습니다.
[정답 근거] ②가 글의 내용과 일치하지 않습니다.
[오답 분석]
① Her father ... had fled Italy as a political refugee와 일치합니다.
③ Her older brother, Dante Gabriel Rossetti, became a famous poet and painter와 일치합니다.
④ she turned down chances to marry men who did not share her deep faith와 일치합니다.
⑤ She died of cancer ... at the age of 64와 일치합니다.
[어휘] flee 달아나다 · turn down 거절하다 · faith 신앙 · adore 몹시 아끼다 · reflect 반영하다', 26, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 안내문 불일치 (2점)
[유형 전략] 안내문은 항목별로 정보가 정리되어 있으므로 선택지의 핵심어(날짜, 시간, 대상, 비용, 신청 방법)로 해당 항목을 바로 찾습니다. 주의 사항·예외 조건(only, not, must)이 함정으로 자주 쓰입니다.
[풀이 과정]
1) ① The challenge is open to children ages 6 to 12. 6세부터 참가할 수 있으므로 5세는 참가할 수 없습니다.
[정답 근거] ①이 안내문과 일치하지 않습니다.
[오답 분석]
② For every 15 minutes you read, color in one space와 일치합니다.
③ between December 1 and January 31과 일치합니다.
④ a free book of his or her choice와 일치합니다.
⑤ local businesses are supporting the challenge by donating prizes와 일치합니다.
[어휘] reading chart 독서 기록표 · color in 색칠하다 · completed 완성된 · donate 기부하다', 27, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 안내문 일치 (2점)
[유형 전략] 안내문 일치 문항은 정답이 하나뿐이므로 선택지를 하나씩 대조하며 틀린 것을 지웁니다. 날짜·요일, 연령, 요금, 포함 여부, 사전 등록 여부처럼 숫자와 조건이 바뀐 부분을 꼼꼼히 확인합니다.
[풀이 과정]
1) ⑤ Gown Purchase: $45. 가운을 사려면 45달러를 냅니다.
[정답 근거] ⑤가 안내문과 일치합니다.
[오답 분석]
① Time: 10:00 a.m.로 오전입니다.
② Gown Rental: $20로 대여도 유료입니다.
③ All fees must be paid online through the school website.
④ 리허설은 졸업식 전날(February 13) 오후 3시입니다.
[어휘] auditorium 강당 · rental 대여 · purchase 구매 · rehearsal 예행연습', 28, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 어법 (3점)
[유형 전략] 어법 문항은 밑줄마다 확인할 문법 포인트를 먼저 정합니다(동사 자리·수일치, 관계사와 접속사, 분사·to부정사, 병렬 구조, 대명사, 형용사·부사). 특히 문장의 주어와 본동사를 먼저 찾으면 수일치와 준동사 문제를 빠르게 판단할 수 있습니다.
[풀이 과정]
1) ④ 관계대명사 who의 선행사는 those(~한 사람들, 복수)입니다. 따라서 관계절의 동사도 복수형 have가 되어야 합니다.
2) ① because of 뒤에 명사구(language barriers)가 오므로 맞습니다. 절이 오면 because를 씁니다.
3) ② reported, experienced와 함께 found that절의 주어 deaf children에 이어지는 과거 동사로 병렬을 이루므로 had가 맞습니다.
4) ③ with + 목적어 + 분사 구문: 학자들이 ‘주장하는’ 능동 관계이므로 현재분사 arguing이 맞습니다.
5) ⑤ 셀 수 없는 명사 social anxiety를 꾸미는 비교급 less가 맞습니다.
[정답 근거] 선행사 those와 수가 맞지 않는 ④ who has가 틀렸습니다(→ who have).
어법 포인트: 관계대명사절의 수일치 — those who ~(~한 사람들)는 복수로 취급합니다. 관계사 앞의 선행사가 대명사여도 그 수를 따라 동사를 정합니다.
[어휘] disorder 장애 · barrier 장벽 · insecure 불안정한 · boarding school 기숙 학교 · peer 또래', 29, 3),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘 (2점)
[유형 전략] 어휘 문항은 글의 흐름(원인과 결과, 대조, 예시)을 먼저 잡고, 밑줄 친 낱말이 그 흐름과 반대 방향인지 확인합니다. 밑줄 낱말을 반의어로 바꿔 넣었을 때 문맥이 자연스러워지면 그것이 정답입니다.
[풀이 과정]
1) 글의 첫 문장: 인종 간 적대감은 공동의 목표를 위해 협력해야 하는 상황을 만들어 줄일 수 있다.
2) ② 문장은 그런 협력 상황의 예(for example)입니다. 같은 팀에서 뛰는 것과 나란히 학교·직장에서 일하는 것이 예가 되어야 하므로, 함께 일하기를 ‘피한다(avoid)’는 말은 흐름과 반대입니다. avoid → begin, 또는 avoid를 빼고 work side by side가 되어야 합니다.
[정답 근거] 협력 상황의 예와 맞지 않는 ② avoid가 적절하지 않습니다.
[오답 분석]
① 협력하여 이루는 ‘공동의(shared)’ 목표로 맞습니다.
③ 그런 우정만으로는 뿌리 깊은 편견을 ‘극복하기에’ 부족하다로 맞습니다.
④ 흑인을 두 범주로 ‘나눈다’로 맞습니다.
⑤ 유명인을 부정적 감정에서 ‘제외한다’로 맞습니다.
[어휘] side by side 나란히 · deep-rooted 뿌리 깊은 · category 범주 · exclude 제외하다 · individual 개별적인', 30, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 빈칸은 ‘무엇이 믿음 형성에 미치는 영향’인지를 묻습니다. 이어지는 예시를 모아 공통 요소를 찾습니다.
2) 예시: 행복한 사람 vs 슬픈 사람이 물건의 가치, 동음이의어(morning/mourning), 굶주리는 고아 비율을 다르게 판단한다.
3) 마지막 문장 people''s feelings at the moment shape what they believe to be true가 결정적 단서입니다.
[정답 근거] 그 순간의 기분(mood)이 믿음에 영향을 준다는 ③이 정답입니다.
[오답 분석]
② 기억, ① 교육, ④ 사회적 지위는 예시에서 비교되는 조건이 아닙니다.
⑤ morning/mourning이라는 단어 예에서 떠올리기 쉬운 오답이지만, 단어는 기분의 영향을 보여 주는 도구일 뿐입니다.
[어휘] document 기록하다, 입증하다 · evaluate 평가하다 · estimate 추정하다 · figure 수치', 31, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 빈칸 뒤에 반복되는 핵심어를 찾습니다: divide them evenly, share much more evenly, divide resources evenly.
2) ‘고르게 나누는 것’을 사회적 가치로 표현하면 공정함과 평등입니다.
3) 침팬지와의 비교도 인간이 협력 후 ‘공평하게’ 나누는 경향을 강조합니다.
[정답 근거] 공정함과 평등을 뜻하는 ③이 정답입니다.
[오답 분석]
① 자유와 선택은 언급되지 않습니다.
② 보상(rewards)이 나오지만 경쟁이 아니라 협력 후의 분배가 핵심입니다.
④ 효율성은 다루지 않습니다.
⑤ 권위에 대한 존중은 관련이 없습니다.
[어휘] resource 자원 · evenly 고르게 · cooperative 협력적인 · joint 공동의 · evolutionary 진화의', 32, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 빈칸 문장: 노력과 인내는 물질적인 것보다 ‘~’에 더 중요하다.
2) 이어지는 내용: 가까운 관계를 원한다 → 진정한 우정은 저절로 유지되지 않고 노력이 필요하다 → 결혼도 마찬가지다.
3) 모든 예가 오래가는 인간관계를 유지하는 데 노력과 인내가 필요하다는 것을 보여 줍니다.
[정답 근거] 오래가는 관계를 쌓는 것이라는 ⑤가 정답입니다.
[오답 분석]
① 저축은 오히려 첫 문장의 material things(물질적인 것)에 가깝습니다. 빈칸은 그보다 ‘더’ 노력이 필요한 것입니다.
② 건강 관리, ③ 외국어 공부, ④ 일자리 찾기도 노력이 필요하지만, 지문의 예(우정·결혼)는 모두 사람 사이의 관계입니다.
[어휘] material 물질적인 · depend on ~에 의지하다 · look out for ~을 보살피다 · commitment 헌신', 33, 3),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 첫 문장이 주제문입니다: 사람들은 아는 사람과 연관 짓는다는 이유만으로 선택하기도 한다(associate it with someone they know).
2) 예시: 친구들이 같은 와인 가게를 이용, 삼촌이 일하는 회사의 컴퓨터만 삼, 친척이 관련된 정유사에서 계속 주유.
3) 빈칸: 소셜 미디어는 친구·가족이 어떤 브랜드를 좋아하는지 보여 주므로, 이런 ‘개인적 연관’이 생길 기회를 훨씬 많이 준다.
[정답 근거] 구매 결정으로 이어지는 개인적 연관을 만든다는 ④가 정답입니다.
[오답 분석]
① 광고를 믿고 안 믿고는 다루지 않습니다. 글의 구매 이유는 광고가 아니라 아는 사람과의 연관입니다.
② 신제품을 남보다 먼저 아는 것은 소셜 미디어의 쓰임일 수 있지만, 아는 사람과 연관 짓는다는 글의 논점과 관련이 없습니다.
③ 소셜 미디어는 친구·가족의 선호를 ‘보여 주는’ 곳이라 했으므로 쇼핑 습관을 숨긴다는 것은 반대입니다.
⑤ 의견 공유의 자유는 구매 이유와 관련이 없습니다.
[어휘] associate A with B A를 B와 연관 짓다 · disastrous 처참한 · oil spill 기름 유출 · preference 선호 · interaction 상호 작용', 34, 3),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 무관한 문장 (2점)
[유형 전략] 무관한 문장 문항은 첫 문장(주제문)이 제시한 소재와 방향을 확인한 뒤 각 문장이 그 방향을 이어 가는지 봅니다. 정답 문장은 지문의 핵심어를 일부 포함하지만 논점이 다른 쪽(다른 대상, 다른 측면)으로 벗어난 경우가 많습니다.
[풀이 과정]
1) 첫 두 문장의 화제: TV의 중요성이 커지면서 선거 운동 비용이 크게 늘었다.
2) ①은 증가의 이유(예산 배분 방식의 변화), ②③은 그 변화의 구체적 수치(1956년 집회 중심 → 세 번의 선거 뒤 TV 광고 중심), ⑤는 그 결과(현장 활동 예산 감소)입니다.
3) ④는 TV 뉴스가 후보의 개인적 특성에 치중한다는 보도 비판으로, ‘선거 비용의 배분 변화’라는 흐름에서 벗어납니다.
[정답 근거] TV 보도 방식에 대한 비판을 다룬 ④가 흐름과 관계없습니다.
[오답 분석]
④를 빼면 ③(TV 광고 예산 증가) → ⑤(And the television budget kept climbing)로 자연스럽게 이어집니다.
[어휘] campaign spending 선거 운동 비용 · fourfold 네 배로 · field activity 현장 활동', 35, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글의 순서 (2점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 생후 5주쯤 미소가 특정 자극에 대한 반응이 된다.
2) (A)의 By this time은 주어진 글의 ‘5주쯤’을 받아, 이때 아기가 눈(검은 점 두 개)에 반응하고 점차 입도 필요해진다고 합니다.
3) (C)의 Now와 two black dots with a line for a mouth는 (A)의 마지막(a mouth also becomes necessary)을 이어받습니다.
4) (B)의 At the next stage는 앞 단계 다음인 3~4개월 무렵, 엄마의 얼굴에만 반응하는 단계로 마무리합니다.
[정답 근거] 주어진 글 → (A) → (C) → (B)이므로 ①이 정답입니다.
[오답 분석]
자극이 점점 구체화되는 순서(눈 → 눈 + 입 → 웃는 입 → 엄마의 얼굴)와 시간 표현(By this time → Now → At the next stage)을 함께 따라갑니다.
[어휘] aim at ~을 겨냥하다 · focus on ~에 초점을 맞추다 · essential 필수적인 · narrow 좁혀지다 · bond 유대', 36, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글의 순서 (3점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 스토아학파는 미덕만 지키면 행복(well-being)하다고 믿었다.
2) (B)의 in their view는 스토아학파의 관점을 이어서 설명합니다. 물질적 불행은 미덕을 빼앗을 수 없으므로 물질적 환경에 무관심하려 했다.
3) (C)의 by contrast는 스토아학파(B)와 대조되는 아리스토텔레스의 생각을 소개합니다. 가난은 좋은 삶에 필요한 것을 빼앗을 수 있다.
4) (A)의 In his view, then은 (C)의 아리스토텔레스(his)의 결론을 정리합니다.
[정답 근거] 주어진 글 → (B) → (C) → (A)이므로 ③이 정답입니다.
[오답 분석]
(A)의 his는 아리스토텔레스를 가리키므로 (C)에서 그가 등장한 뒤에 와야 합니다.
(C)가 주어진 글 바로 뒤에 오면 스토아학파의 생각이 충분히 설명되기 전에 대조가 나와 흐름이 어색합니다.
[어휘] principle 원칙 · misfortune 불운 · indifferent 무관심한 · rob A of B A에게서 B를 빼앗다 · sufficient 충분한', 37, 3),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 문장 삽입 (2점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장의 Instead(그 대신)는 앞에서 ‘하지 않은 것’이 나와야 한다는 단서이고, combining pictures(그림을 결합)는 뒤에 결합의 예가 와야 한다는 단서입니다.
2) ④ 앞: Traditional Chinese did not follow this path.(다른 문자들처럼 소리 문자로 옮겨 가지 않았다) → Instead와 연결됩니다.
3) ④ 뒤: Thus, combining the pictures for "sun" and "moon" ~. 그림 결합의 구체적 예가 Thus로 이어집니다.
[정답 근거] ‘소리 문자로 가지 않았다’와 ‘그림 결합의 예’ 사이인 ④가 정답입니다.
[오답 분석]
①~③: 아직 다른 언어들과의 비교(did not follow this path)가 나오기 전이라 Instead가 받을 내용이 없습니다.
⑤: 결합의 예(Thus ~) 뒤입니다. 주어진 문장이 없으면 did not follow this path 다음에 바로 Thus가 와서, Thus가 가리킬 ‘그림 결합’이라는 방식이 빠집니다.
[어휘] character 글자 · build up 만들어 내다 · date back to ~까지 거슬러 올라가다 · abstract 추상적인', 38, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 문장 삽입 (3점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장의 Its vision, in contrast는 앞에 대학의 ‘사명(mission)’이 먼저 나온 뒤 그것과 대조되는 ‘비전’을 소개한다는 단서입니다.
2) ② 앞: Your university probably has a mission to educate people(사명).
3) ② 뒤: Such a vision requires ~. Such a vision이 가리킬 구체적 비전(최고의 대학으로 인정받기)은 주어진 문장에만 있습니다.
[정답 근거] 대학의 사명 설명과 Such a vision 사이인 ②가 정답입니다.
[오답 분석]
①: 아직 대학의 사명이 나오기 전이라 in contrast가 대조할 대상이 없습니다.
③~⑤: Such a vision이 이미 나온 뒤이므로, 그 비전을 처음 소개하는 문장이 오기에 늦습니다.
[어휘] organizational 조직의 · mission statement 사명 선언문 · define 규정하다 · strategic 전략적인 · carry out 수행하다', 39, 3),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 요약문 완성 (2점)
[유형 전략] 요약문은 먼저 요약문을 읽고 (A), (B)가 각각 무엇을 묻는지 확인한 뒤 지문을 읽습니다. 지문의 실험 결과나 결론 문장을 요약문의 표현과 짝지어, 같은 뜻을 다른 말로 바꾼(paraphrase) 선택지를 고릅니다.
[풀이 과정]
1) 요약문 분석: 잘 지내는 사람들은 서로의 몸짓을 (A)하는 경향이 있지만, 그들 사이에 (B)가 있으면 몸짓이 더 이상 일치하지 않는다.
2) (A) 근거: your body language tends to look alike, if one of you leans forward ..., the other leans forward too → 서로 ‘따라 한다(imitate)’.
3) (B) 근거: When two people are out of harmony, reflecting the disagreement between them → ‘갈등(conflict)’.
[정답 근거] (A) imitate, (B) conflict인 ①이 정답입니다.
[오답 분석]
② (B) agreement(합의)는 반대입니다.
③⑤ (A) ignore(무시하다)는 몸짓이 비슷해진다는 내용과 반대입니다.
④ (B) harmony가 있으면 몸짓이 일치하므로 반대입니다.
[어휘] get along well 사이좋게 지내다 · lean forward 몸을 앞으로 기울이다 · out of harmony 조화를 이루지 못하는 · forced 억지의', 40, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q41.webp]]', '[[img:/naesin/csat-h1-mock/r07/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 장문 제목 (2점)
[유형 전략] 장문 제목은 글 전체의 논지를 담아야 합니다. 도입의 일반적 진술과 후반부의 결론을 연결하여 주제를 정리하고, 글의 일부 사례만 다루는 선택지는 지웁니다.
[풀이 과정]
1) 첫 단락: 분업은 문명의 혜택을 주었지만, 일상 행동과 그 결과 사이의 연결을 가려 버린다.
2) 둘째 단락: 이런 전문화를 처음부터 가능하게 한 것은 값싼 에너지였다. 값싼 에너지 덕분에 멀리 있는 사람들의 기술을 이용하며 지역 공동체를 건너뛸 수 있었다.
3) 글 전체를 관통하는 것은 ‘값싼 에너지가 전문화(분업)를 떠받치는 숨은 힘’이라는 점입니다.
[정답 근거] ③이 제목으로 적절합니다.
[오답 분석]
① 정전은 예시일 뿐, 대비 방법을 다루지 않습니다.
② 산 정상 파괴는 하나의 결과로 언급될 뿐입니다.
④ 값싼 에너지가 지역 공동체를 건너뛰게 했다는 내용과 반대입니다.
⑤ 기후 변화 연구의 기원은 다루지 않습니다.
[어휘] blessing 축복, 혜택 · specialization 전문화 · consequence 결과 · coal-fired 석탄을 때는 · blackout 정전', 41, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q42.webp]]', '[[img:/naesin/csat-h1-mock/r07/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 장문 어휘 (2점)
[유형 전략] 장문 어휘 문항은 단락별 흐름을 잡은 뒤 밑줄 낱말을 하나씩 문맥에 넣어 봅니다. 앞뒤 문장과 인과·대조 관계가 맞지 않는 낱말, 반의어로 바꾸면 자연스러워지는 낱말이 정답입니다.
[풀이 과정]
1) (a)가 있는 문장은 Yet으로 시작해 앞 문장(분업의 혜택)과 대조되는 부정적 측면을 말합니다.
2) 뒤 문장 This makes it easy for me to forget about the coal-fired power plant ~(발전소를 잊기 쉽게 만든다)를 보면, 분업은 행동과 결과의 연결을 ‘분명하게 하는(clarifies)’ 것이 아니라 ‘흐리게 하는(obscures)’ 것이어야 합니다.
[정답 근거] (a) clarifies는 obscures로 바꾸어야 하므로 ①이 정답입니다.
[오답 분석]
(b) easy: 연결이 흐려져 발전소를 잊기 ‘쉽다’로 맞습니다.
(c) possible: 값싼 에너지가 전문화를 ‘가능하게’ 했다로 맞습니다.
(d) larger: 정전이 되면 이웃(공동체)의 존재가 갑자기 ‘더 크게’ 느껴진다로 맞습니다.
(e) bypass: 값싼 에너지로 지역 공동체를 ‘건너뛰었다’로 맞습니다.
[어휘] clarify 분명히 하다 · obscure 흐리게 하다 · process 가공하다', 42, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q43.webp]]', '[[img:/naesin/csat-h1-mock/r07/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 장문 순서 (2점)
[유형 전략] 장문 순서는 등장인물의 행동과 시간의 흐름을 따라갑니다. 각 단락의 첫 문장이 앞 단락의 마지막 장면과 이어지는지(장소 이동, 대화의 응답, 사건의 결과) 확인합니다.
[풀이 과정]
1) (A) 끝: Grétry가 독일인 남작을 만나 함께 여행하기로 한다.
2) (D) As soon as the carriage set off: 출발하자마자 마차 안에서 남작이 “마차에서는 말하지 않는다”며 대화를 막는다.
3) (B) That night: 그날 밤 여관에서 Grétry가 “여관에서는 말하지 않는다”며 남작의 말을 그대로 돌려준다. (D)의 말을 흉내 낸 농담이므로 (D) 뒤에 옵니다.
4) (C) The next day: 다음 날 산을 오르며 안내인들에게 “조용히!”라는 말을 듣는다.
[정답 근거] (A) → (D) → (B) → (C)이므로 ④가 정답입니다.
[오답 분석]
(B)의 농담(I never talk in an inn)은 (D)의 남작의 말(I never talk in a carriage)을 따라 한 것이므로 (D)가 먼저 나와야 웃음의 의미가 생깁니다. 시간 표현(As soon as → That night → The next day)도 순서를 알려 줍니다.
[어휘] wit 재치 · interrupt 말을 끊다 · cut in 끼어들다 · chatterbox 수다쟁이 · loosen 느슨하게 하다', 43, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q44.webp]]', '[[img:/naesin/csat-h1-mock/r07/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 지칭 대상 (2점)
[유형 전략] 지칭 대상 문항은 밑줄 친 대명사가 나오는 문장에서 그 행동을 하는 사람이 누구인지 앞뒤 문맥으로 확인합니다. 대화문 속 you/I는 말하는 사람과 듣는 사람을 기준으로 따집니다.
[풀이 과정]
1) (a) 스위스를 여행하다 남작을 만난 사람 → Grétry
2) (b) 여관에서 “나는 여관에서는 말하지 않는다”고 말한 I → Grétry
3) (c) 작은 십자가의 의미를 물은 사람 → Grétry
4) (d) 소리가 눈사태를 일으킬 수 있다고 설명한 사람들 → 안내인들(the guides)
5) (e) “좋습니다”라고 대답하고 입을 다문 사람 → Grétry
[정답 근거] (d)만 안내인들이고 나머지는 Grétry이므로 ④가 정답입니다.
[오답 분석]
(b)처럼 인용문 속의 I는 그 말을 하는 사람을 가리킵니다. Grétry interrupted sharply 뒤의 말이므로 Grétry입니다.', 44, 2),
(@csh1_r07, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r07/q45.webp]]', '[[img:/naesin/csat-h1-mock/r07/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 장문 내용 불일치 (2점)
[유형 전략] 장문 내용 불일치는 선택지 순서가 대체로 글의 사건 순서를 따릅니다. 선택지의 핵심어로 해당 단락을 찾아 인물·행동·시간을 정확히 대조합니다.
[풀이 과정]
1) ③ (B) The baron saw the joke at once, and from then on the two enjoyed a friendly conversation. 남작은 농담을 바로 알아채고 둘은 즐겁게 대화했습니다.
[정답 근거] 남작은 화를 내지 않았으므로 ③이 적절하지 않습니다.
[오답 분석]
① while traveling through Switzerland, he met a German baron과 일치합니다.
② "I never talk in a carriage."와 일치합니다.
④ Grétry noticed a small cross planted in the ground와 일치합니다.
⑤ any talking or noise might ... cause an avalanche와 일치합니다.', 45, 2);

-- 8회: 2018년 5월 고1 종로 원본(고1 종로 18 5모)
INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-CSH1-MOCK-R08', 'NAESIN', '고1 수능 영어 독해 모의고사 8회 · 18~45번 변형문제 8회', '2018년 5월 고1 종로 원본 · 28문항 · 45분 · 62점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 풀이 전략 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @csh1_r08 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-CSH1-MOCK-R08');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글의 목적 (2점)
[유형 전략] 글의 목적은 대개 글 후반부의 요청·안내 문장(please, we ask, I would like to, I am writing to)에 드러납니다. 앞부분의 인사·배경 설명에 끌려가지 말고, 필자가 독자에게 결국 무엇을 해 달라고 하는지 한 문장으로 정리한 뒤 선택지와 맞춰 봅니다.
[풀이 과정]
1) 도입: only one week remains in our annual Spring Sale. 할인 행사가 일주일밖에 남지 않았다는 정보가 핵심 배경입니다.
2) 강조: during this final week, every item in the store is marked down by 50 percent.
3) 요청: Please stop by ..., and then come and visit us before the sale ends. 행사가 끝나기 전에 방문하라는 권유입니다.
[정답 근거] 곧 끝나는 할인 행사에 방문하라고 권유하는 글이므로 ①이 정답입니다.
[오답 분석]
② 대금 지불 요청은 없습니다.
③ 이미 참여한 것에 대한 감사가 아니라 아직 방문하지 않은 고객에게 쓴 글입니다.
④ 기간이 연장된 것이 아니라 마지막 주(final week)라고 했습니다.
⑤ 신제품은 언급되지 않습니다.
[어휘] take advantage of ~을 활용하다 · mark down 가격을 인하하다 · enclose 동봉하다 · flyer 전단', 18, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 심경 (2점)
[유형 전략] 심경 문항은 인물이 처한 상황과 감정을 드러내는 표현(형용사, 표정, 행동, 속마음)을 모아 하나의 감정으로 정리합니다. 부정적 표현이 일부 섞여 있어도 글 전체의 분위기와 마지막 장면의 감정이 무엇인지 확인합니다.
[풀이 과정]
1) 불안을 드러내는 표현: began to wonder whether I would make it, had to find a gas station as soon as possible, at any moment I might be stranded, glancing at the gauge and scanning the roadside.
2) 후회를 드러내는 표현: I had passed a couple of gas stations ... Now I wished I had pulled into one of them.(I wish + 과거완료 = 과거에 대한 후회)
[정답 근거] 불안하면서 후회하는 심경이므로 ③이 정답입니다.
[오답 분석]
① I did not panic at first는 처음에만 그랬을 뿐, 점점 불안해집니다.
② 자랑스럽거나 자신 있는 표현은 없습니다.
④ 무관심한 것이 아니라 계속 계기를 확인합니다.
⑤ 아직 주유소를 찾지 못해 안도할 상황이 아닙니다.
[어휘] destination 목적지 · creep 서서히 움직이다 · in sight 보이는 곳에 · premium 고급의 · pull into ~에 차를 대다', 19, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 필자의 주장 (2점)
[유형 전략] 필자의 주장은 should, must, need to, it is important to, 명령문처럼 당위를 나타내는 문장에 담깁니다. 예시나 일화는 주장을 뒷받침하는 재료이므로, 일화가 끝난 뒤 필자가 일반화하는 문장을 찾아 그 내용과 같은 선택지를 고릅니다.
[풀이 과정]
1) 첫 문장이 주장입니다: The time has come for Americans to end their love affair with the automobile.
2) 이유: 자동차 의존 → 석유 의존 → 현대 세계의 갈등.
3) 방법(We need to): 자동차와 트럭의 상당 부분을 대중교통, 자전거, 걷기로 대체해야 한다.
[정답 근거] 석유 의존을 줄이기 위해 자동차 이용을 크게 줄여야 한다는 ①이 주장입니다.
[오답 분석]
② 대중교통 요금은 언급되지 않습니다.
③ 친환경 차가 아니라 차 자체를 줄이자는 주장입니다.
④ 걷기와 자전거는 대안의 예일 뿐, 건강이 이유가 아닙니다.
⑤ 갈등(conflict)은 자동차 의존의 결과로 언급될 뿐, 에너지 협력을 주장하지 않습니다.
[어휘] love affair 애착 · dependence 의존 · root cause 근본 원인 · decisive 결단력 있는 · inconvenience 불편', 20, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 밑줄 함의 (3점)
[유형 전략] 밑줄 친 표현은 비유이므로 글자 그대로 해석하지 않습니다. 밑줄 앞뒤에서 같은 대상을 설명하는 문장을 찾아, 비유 속 단어(밑줄의 핵심 명사·동사)가 글에서 무엇에 대응하는지 짝지은 뒤, 그 의미를 풀어 쓴 선택지를 고릅니다.
[풀이 과정]
1) 비유의 출발점은 ‘벽에도 귀가 있다(Walls have ears)’라는 속담입니다. 식탁에도 귀가 있다는 것은 식탁에서 한 말을 누군가 듣고 퍼뜨릴 수 있다는 뜻입니다.
2) 일화: 집 식탁에서 무심코 한 말 → 딸이 친구들에게 전함 → 다음 날 학교 전체에 퍼짐 → 의뢰인 가족에게까지 알려짐.
3) 따라서 밑줄은 ‘집에서 한 말도 집 밖으로 멀리 퍼질 수 있다’는 교훈입니다.
[정답 근거] ②가 정답입니다.
[오답 분석]
① 식탁을 소식을 나누기 좋은 자리로 본 것은 교훈과 반대입니다.
③ 자녀에게 부모의 직업을 말하지 말라는 것이 아닙니다.
④ Susan은 일자리를 잃지 않았고, 일반화된 진술이라 맞지 않습니다.
⑤ 일화의 상황(변호사 사무실)에서 떠올리기 쉬운 오답이지만, 밑줄은 ‘말이 퍼지는 것’에 대한 교훈입니다.
[어휘] make it a rule to ~을 규칙으로 삼다 · casually 무심코 · confront A with B A에게 B를 들이대다 · the hard way 쓰라린 경험을 통해', 21, 3),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 요지 (2점)
[유형 전략] 요지는 글 전체가 말하려는 한 가지 핵심입니다. 도입의 질문·통념, 대조(however, but), 결론(therefore, in short)을 확인하고, 세부 예시 하나만 다룬 선택지나 지문 단어를 섞어 만든 그럴듯한 선택지는 지웁니다.
[풀이 과정]
1) 첫 문장: 대부분의 실패는 놀라울 만큼 오래가지 않는다(surprisingly short-lived).
2) 예시: 아무리 심한 연예인 스캔들도 일주일쯤 지나면 사라진다.
3) 이유: contrary to what we believe, other people are not thinking about us or watching us all the time. 사람들은 결국 자기 자신의 문제로 돌아간다.
[정답 근거] 사람들은 남의 실패에 오래 관심을 두지 않아 실패는 금방 잊힌다는 ④가 요지입니다.
[오답 분석]
① 실패가 성공의 조건이라는 내용은 없습니다.
② 유명인의 스캔들도 일주일이면 사라진다고 했으므로 반대입니다.
③ 습관은 다루지 않습니다.
⑤ 남의 사생활에 관심을 갖지 말라는 당위가 아니라, 사람들이 원래 오래 관심을 두지 않는다는 사실을 말합니다.
[어휘] get over 극복하다 · short-lived 오래가지 않는 · disappear from the radar 관심에서 사라지다 · contrary to ~와 반대로 · self-centered 자기중심적인', 22, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글의 주제 (2점)
[유형 전략] 주제는 글의 핵심 소재와 그에 대한 필자의 관점을 함께 담은 영어 구로 나타납니다. 반복되는 핵심어를 찾고, 글이 그 소재의 무엇(원인, 효과, 과정, 중요성, 한계)을 다루는지 확인하여 범위가 너무 넓거나 좁은 선택지를 지웁니다.
[풀이 과정]
1) 현상: 아이들은 과거 시제 규칙(-ed)을 배우면 모든 단어에 적용한다(I falled down, I hurted my knee).
2) 부모의 대응: There is no need to make a big deal out of correcting him. 대신 올바른 형태로 자연스럽게 대답해 주라고 한다.
3) 마무리: 아이가 언어 규칙을 배우고 있다는 것을 자랑스러워하라. 예외는 시간이 지나면 익힌다.
[정답 근거] 과거 시제를 배우는 아이를 어떻게 도울지를 다룬 글이므로 ⑤가 정답입니다.
[오답 분석]
① 아이가 교정을 싫어한다는 내용은 없습니다.
② be proud는 부모의 태도이지 자주 칭찬하라는 것이 아닙니다.
③ 부모의 실수를 나열하는 글이 아닙니다.
④ 창의성은 언급되지 않습니다.
[어휘] scrape 긁히다 · make a big deal out of ~을 크게 문제 삼다 · back-and-forth 주고받는 · exception 예외', 23, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글의 제목 (2점)
[유형 전략] 제목은 주제를 압축하거나 비유·의문문으로 바꾼 표현입니다. 먼저 글의 주제를 우리말로 정리한 뒤, 그 핵심을 담은 선택지를 고릅니다. 지문에 나온 단어가 그대로 쓰였지만 글의 일부만 다루거나 반대 방향을 말하는 선택지에 주의합니다.
[풀이 과정]
1) 카트리나 피해를 두 집단의 관점에서 봅니다.
2) 가난한 사람들: 저지대에 살아 피해에 가장 많이 노출됨, 차가 없어 탈출이 어려움, 저축이 없어 회복이 더딤.
3) 노인: Just as poor people were at a disadvantage, so were the elderly. 스트레스·추위·더위·피로를 견딜 힘이 부족했다.
[정답 근거] 재난이 가난한 사람과 노인에게 가장 큰 타격을 준다는 ③이 제목으로 적절합니다.
[오답 분석]
① 허리케인이 점점 강해진다는 내용은 없습니다.
② 이웃 간의 도움은 언급되지 않습니다.
④ 차는 탈출 수단으로 언급되며, 차를 두고 다니라는 내용과 관계가 없습니다.
⑤ 고지대 거주의 장점을 다루는 글이 아닙니다.
[어휘] resident 주민 · be exposed to ~에 노출되다 · favor 유리하게 하다 · paycheck to paycheck 근근이 · endure 견디다', 24, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 도표 불일치 (2점)
[유형 전략] 도표 문항은 선택지 문장을 하나씩 도표 수치와 맞춰 봅니다. 비교 표현(more than, twice, the lowest), 순위, 차이 계산이 함정이 되는 경우가 많으므로, 문장에 나온 항목·연도·수치를 도표에서 손가락으로 짚어 가며 확인합니다.
[풀이 과정]
1) ① 2015년 최고는 45–54세(1,110천 명)이고, 35–44세는 1,080천 명입니다. (불일치)
2) ② 모든 연령대에서 2015년 수치가 더 큽니다. (일치)
3) ③ 2005년 35–44세는 1,020천 명으로 100만 명을 조금 넘습니다. (일치)
4) ④ 2005년 1,020 ÷ 120 = 8.5배, 2015년 1,110 ÷ 150 = 7.4배로 모두 다섯 배 이상입니다. (일치)
5) ⑤ 증가량: 65+ 290, 55–64 260, 45–54 210 … 가장 큰 증가는 65세 이상입니다. (일치)
[정답 근거] 2015년의 최고 연령대는 45–54세이므로 ①이 일치하지 않습니다.
[오답 분석]
두 해를 비교하는 도표에서는 연도별 최고값이 서로 다른 항목일 수 있습니다. 2005년 최고(35–44세)를 2015년에도 그대로라고 착각하게 만드는 함정입니다.
[어휘] self-employed 자영업의 · age group 연령대 · increase 증가', 25, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 내용 불일치 (2점)
[유형 전략] 인물 내용 일치 문항은 선택지 순서가 대체로 글의 순서와 같습니다. 선택지의 핵심어(연도, 장소, 숫자, 고유명사)를 먼저 보고 지문에서 해당 부분을 찾아 정확히 대조합니다. 일부만 바꾼 진술(시기, 횟수, 주체)에 주의합니다.
[풀이 과정]
1) ⑤ she and her husband had four children, who were also enslaved. 자녀는 세 명이 아니라 네 명이었습니다.
[정답 근거] ⑤가 글의 내용과 일치하지 않습니다.
[오답 분석]
① the lifelong companion of Jane Long과 일치합니다.
② Jane and the thirteen-year-old Kian spent a bitterly cold winter alone on Bolivar Point와 일치합니다.
③ fired a cannon ... to make it seem that the fort was still guarded by soldiers와 일치합니다.
④ Kian helped Jane give birth to a baby in an ice-covered tent와 일치합니다.
[어휘] companion 동반자 · settle 정착하다 · bitterly 혹독하게 · inn 여관 · free 해방하다', 26, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 안내문 불일치 (2점)
[유형 전략] 안내문은 항목별로 정보가 정리되어 있으므로 선택지의 핵심어(날짜, 시간, 대상, 비용, 신청 방법)로 해당 항목을 바로 찾습니다. 주의 사항·예외 조건(only, not, must)이 함정으로 자주 쓰입니다.
[풀이 과정]
1) ② Schedule: 5K Run은 8:30, 3K Walk는 8:40에 출발합니다. 걷기가 달리기보다 10분 늦게 출발합니다.
[정답 근거] ②가 안내문과 일치하지 않습니다.
[오답 분석]
① 7:30 a.m. – 8:15 a.m.: Registration과 일치합니다.
③ Kids'' Dash starts (ages 8 and under)와 일치합니다.
④ 5K Run: $25 with a T-shirt와 일치합니다.
⑤ Register by September 1 to receive an early-bird discount와 일치합니다.
[어휘] registration 등록 · dash 단거리 달리기 · early-bird 조기의 · apply to ~에 적용되다', 27, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 안내문 일치 (2점)
[유형 전략] 안내문 일치 문항은 정답이 하나뿐이므로 선택지를 하나씩 대조하며 틀린 것을 지웁니다. 날짜·요일, 연령, 요금, 포함 여부, 사전 등록 여부처럼 숫자와 조건이 바뀐 부분을 꼼꼼히 확인합니다.
[풀이 과정]
1) ⑤ Register in advance by phone or email by March 1. 전화나 이메일로 등록할 수 있습니다.
[정답 근거] ⑤가 안내문과 일치합니다.
[오답 분석]
① No knowledge of Italian is required.
② For boys and girls로 남녀 모두 참여할 수 있습니다.
③ Practices: Tuesdays로 화요일에 연습합니다.
④ (March to June)로 3월부터 6월까지만 연습합니다.
[어휘] choir 합창단 · kick-off 시작 · knowledge 지식 · in advance 미리', 28, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법 (3점)
[유형 전략] 어법 문항은 밑줄마다 확인할 문법 포인트를 먼저 정합니다(동사 자리·수일치, 관계사와 접속사, 분사·to부정사, 병렬 구조, 대명사, 형용사·부사). 특히 문장의 주어와 본동사를 먼저 찾으면 수일치와 준동사 문제를 빠르게 판단할 수 있습니다.
[풀이 과정]
1) ⑤ 문장의 주어는 Something이고, that bothered me ... twenty-two는 주어를 꾸미는 관계절입니다. 따라서 이 문장에는 본동사가 있어야 하는데 bothering은 동사가 될 수 없습니다. 과거의 일을 말하므로 bothered로 고쳐야 합니다.
2) ① the 비교급 ~, the 비교급 …(~할수록 더 …하다) 구문의 the earlier가 맞습니다.
3) ② learn의 목적어가 되는 명사절을 이끄는 접속사 that이 맞습니다. 뒤에 완전한 절이 옵니다.
4) ③ 주어는 a certain immunity(단수)이므로 comes가 맞습니다. to minor setbacks는 수식어입니다.
5) ④ 주어는 the painful incidents(복수)이므로 have become이 맞습니다. 중간의 관계절과 such as구는 수식어입니다.
[정답 근거] 본동사 자리에 -ing형을 쓴 ⑤가 틀렸습니다(→ bothered).
어법 포인트: 본동사 확인 — 주어 뒤에 관계절이 길게 붙으면 관계절의 동사(bothered)를 본동사로 착각하기 쉽습니다. 관계절을 괄호로 묶은 뒤 남은 부분에 시제를 가진 동사가 있는지 확인합니다.
[어휘] misfortune 불운 · better off 더 나은 형편인 · thick skin 둔감함, 강한 정신력 · incident 사건 · self-confidence 자신감', 29, 3),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 어휘 (2점)
[유형 전략] 어휘 문항은 글의 흐름(원인과 결과, 대조, 예시)을 먼저 잡고, 밑줄 친 낱말이 그 흐름과 반대 방향인지 확인합니다. 밑줄 낱말을 반의어로 바꿔 넣었을 때 문맥이 자연스러워지면 그것이 정답입니다.
[풀이 과정]
1) 글의 흐름: 기기가 늘어나면서 사람들은 언제 어디서나 연락이 닿아야 했고, 일과 사생활의 경계가 사라졌다.
2) ① 문장의 nine-to-five workday(오전 9시~오후 5시의 정해진 근무일)는 이런 변화 속에서 ‘돌아오는(returning)’ 것이 아니라 조용히 ‘사라지고(disappearing)’ 있었다고 해야 합니다. 뒤의 at all hours, during what used to be our free time이 근거입니다.
[정답 근거] ① returning은 disappearing으로 바꾸어야 하므로 ①이 정답입니다.
[오답 분석]
② 고용주가 늘 연락하라고 압박하는 ‘긴장’이 쌓였다로 맞습니다.
③ 가족·친구와 연락하려는 욕구가 ‘커졌다’로 맞습니다.
④ 통신망을 차와 여행지에 ‘가지고 다녔다’로 맞습니다.
⑤ 기기를 끄는 것이 점점 ‘어려운’ 선택이 되었다로 맞습니다.
[어휘] reachable 연락이 닿는 · in touch 연락하는 · gadget 기기 · fear of missing out 소외에 대한 두려움', 30, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 실험의 핵심 차이: B는 음식이 어느 그릇에 들어가는지 볼 수 있었고, C는 방에 없어서 보지 못했다.
2) 결과: 개는 B가 가리킨 그릇으로 갔다.
3) 해석: 개는 ‘정확한(믿을 만한) 정보를 가졌다고 생각되는 사람’의 정보를 더 선호했다. 빈칸은 B가 가진 정보의 성격입니다.
[정답 근거] 믿을 만한(reliable) 정보라는 ④가 정답입니다.
[오답 분석]
① 많은 사람이 아는 정보라는 근거는 없습니다.
② limited(제한된)는 오히려 C의 정보에 가깝습니다.
③ 감정적인 정보는 관련이 없습니다.
⑤ 놀라운 정보라는 근거는 없습니다.
[어휘] feed 먹이를 주다 · covered 덮인 · tell apart 구별하다 · indicate 가리키다 · prefer A over B B보다 A를 선호하다', 31, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 연구 결과: 고양잇과 포식자가 있는 섬에서는 원숭이가 큰 무리로, 없는 섬에서는 작은 무리로 다녔다.
2) 일반화: The presence of predators ... pushes individuals together. / the more vulnerable a species is, the larger its groups.
3) 추가 예: 땅에 사는 개코원숭이는 큰 무리, 나무 위 원숭이와 덩치 큰 침팬지는 작은 무리. 모두 ‘안전의 필요’가 무리 크기를 정한다는 근거입니다.
[정답 근거] 안전의 필요(the need for safety)가 사회생활을 형성한다는 ②가 정답입니다.
[오답 분석]
① 먹이, ④ 물, ⑤ 둥지는 예시에서 비교되는 조건이 아닙니다.
③ 영역의 크기는 언급되지 않습니다. 비교되는 조건은 포식자의 유무와 취약성입니다.
[어휘] descend from ~의 후손이다 · predator 포식자 · vulnerable 취약한 · tree-dwelling 나무 위에 사는', 32, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 일화: 수다를 떠느라 주변을 살피지 않은 사람들은 자신이 어디에 있는지, 어디서 왔는지 몰랐다(They had already lost their way).
2) 적용: 우리도 일상에서 고개를 숙인 채 바쁘게 지내다 길가의 신호(the signs along the way)를 놓친다.
3) 빈칸: 그 신호들이 원래 해 줄 수 있었던 일 — 길을 잃지 않도록 우리의 길을 더 잘 안내해 주는 것입니다.
[정답 근거] 우리의 길을 더 현명하게 안내해 줄 신호라는 ③이 정답입니다.
[오답 분석]
① 리더십은 언급되지 않습니다.
② 꿈을 빨리 이루는 것은 관련이 없습니다.
④ 일을 덜 피곤하게 해 준다는 내용은 없습니다. 바쁜 일(busy with work)은 신호를 놓치는 원인일 뿐입니다.
⑤ new people enter our lives는 우리를 당황하게 하는 변화의 예일 뿐, 신호가 해 줄 일이 아닙니다.
[어휘] wilderness 황야 · take in 주의 깊게 보다 · throw ~ off balance ~을 당황하게 하다 · parenting 육아', 33, 3),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 대조: 부모는 나쁜 일(the bad things)에, 십 대는 좋고 신나는 일(the good and exciting things)에 집중한다.
2) 빈칸 앞: 나이가 들면 위험을 더 잘 판단하고 행동의 결과를 충분히 이해하게 된다.
3) 빈칸: 그 결과 한쪽만 보던 십 대가 좋은 점과 나쁜 점을 모두 따져 보기 시작한다는 결론이 자연스럽습니다.
[정답 근거] 좋은 점과 나쁜 점을 모두 따져 보기 시작한다는 ⑤가 정답입니다.
[오답 분석]
① 창의성은 관련이 없습니다.
② 밝은 면만 보는 것은 지금의 십 대의 모습이므로 반대입니다.
③ 위험을 더 잘 판단하게 된다는 내용과 맞지 않습니다.
④ 체력은 언급되지 않습니다.
[어휘] think ~ through ~을 충분히 생각하다 · convince oneself 스스로 확신하다 · judge 판단하다 · consequence 결과 · weigh 따져 보다', 34, 3),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 무관한 문장 (2점)
[유형 전략] 무관한 문장 문항은 첫 문장(주제문)이 제시한 소재와 방향을 확인한 뒤 각 문장이 그 방향을 이어 가는지 봅니다. 정답 문장은 지문의 핵심어를 일부 포함하지만 논점이 다른 쪽(다른 대상, 다른 측면)으로 벗어난 경우가 많습니다.
[풀이 과정]
1) 첫 문장의 화제: 학령기 아동을 환자 교육에 참여시키는 것이 중요하다.
2) ①③④⑤는 간호사가 아이에게 병과 치료를 어떻게 설명해야 하는지(쉽고 논리적으로, 구체적으로, 단계별로, 반응을 확인하며)를 다룹니다.
3) ②는 아이들의 뼈와 근육을 튼튼하게 하는 운동 이야기로, ‘환자 교육 방법’이라는 흐름에서 벗어납니다.
[정답 근거] 운동의 효과를 다룬 ②가 흐름과 관계없습니다.
[오답 분석]
④의 therefore는 ③(추상적 사고 능력이 아직 제한적이다)의 결과입니다. ②를 빼면 ① → ③ → ④가 자연스럽게 이어집니다.
[어휘] school-aged 학령기의 · patient education 환자 교육 · abstractly 추상적으로 · concrete 구체적인 · misunderstand 오해하다', 35, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 순서 (2점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 제목의 철자 오류는 큰 글자 때문에 실수가 더 두드러진다.
2) (C)의 It practically shouts는 주어진 글의 ‘큰 글자로 된 제목의 오류’를 가리킵니다. 신뢰를 떨어뜨리니 두 번 이상 확인하라고 합니다.
3) (A) A computer spellchecker is a good place to start는 (C)의 ‘확인하라’에 대한 구체적 방법입니다. 하지만 철자만 확인할 뿐 쓰임은 확인하지 못한다.
4) (B)의 It will not tell you는 (A)의 맞춤법 검사기를 가리키며 그 한계의 예(too/to, dairy/diary)를 듭니다.
[정답 근거] 주어진 글 → (C) → (A) → (B)이므로 ④가 정답입니다.
[오답 분석]
(B)의 It이 맞춤법 검사기를 가리키려면 (A)가 먼저 나와야 합니다.
(A)를 주어진 글 바로 뒤에 놓으면 ‘확인하라’는 당부(C) 없이 확인 방법이 먼저 나와 흐름이 어색하고, (C)의 It이 가리킬 대상도 사라집니다.
[어휘] misspell 철자를 틀리다 · magnify 확대하다 · credibility 신뢰성 · rely on ~에 의존하다', 36, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 순서 (3점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 가끔 거짓말하는 사람의 말은 그것만으로 믿을 수 없다.
2) (C)의 In the same way는 주어진 글의 논리를 감각에 적용합니다. 감각도 속일 수 있으므로 감각 증거만으로 믿음을 신뢰할 수 없다.
3) (A)의 After all(어쨌든, 왜냐하면)은 (C)의 이유를 설명합니다. 그 믿음의 증거를 준 감각이 하필 속이던 때였을 수 있다.
4) (B)의 Even so(그렇더라도)는 앞의 의심에 대한 양보로, 감각이 믿을 만한 때를 구별할 수 있다면 일부 믿음은 받아들일 수 있다고 마무리합니다.
[정답 근거] 주어진 글 → (C) → (A) → (B)이므로 ④가 정답입니다.
[오답 분석]
(A)의 that belief는 (C)의 a belief ... based on sensory evidence를 가리킵니다.
(B)의 Even so는 감각에 대한 의심이 충분히 제시된 뒤(A, C)에 와야 양보의 의미가 살아납니다.
[어휘] reasonable 합리적인 · merely 단지 · evidence 증거 · misleading 오해를 일으키는 · give up 포기하다', 37, 3),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 문장 삽입 (2점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장은 조상들이 자연 현상을 상상력으로 해석한 구체적 예입니다.
2) ③ 앞: 이런 경향은 실험실뿐 아니라 과거와 현재의 사회 전체에서 볼 수 있다(across entire societies, both past and present) → ‘과거 사회’의 예가 이어질 자리입니다.
3) ③ 뒤: Such imaginative interpretations found their way into science as well. Such imaginative interpretations가 가리킬 예(번개 = 신의 분노, 일식 = 해를 삼키는 용)는 주어진 문장에만 있고, as well은 ‘과학에도’라는 뜻이므로 앞에 과학 밖의 예가 와야 합니다.
[정답 근거] ③이 정답입니다.
[오답 분석]
①②: 아직 사회 전체에서 보이는 경향이라는 일반론이 나오기 전입니다.
④⑤: 이미 과학 속 해석(아리스토텔레스)으로 넘어간 뒤입니다.
[어휘] passive 수동적인 · revise 수정하다 · interpretation 해석 · mold 곰팡이 · give way to ~에 자리를 내주다', 38, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 문장 삽입 (3점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장의 A similar statue는 앞에서 동상 하나(Greyfriars Bobby)가 소개된 뒤 또 다른 동상을 소개한다는 단서입니다.
2) ⑤ 앞: Bobby의 이야기가 책과 영화로 알려졌다는 내용으로 Bobby 이야기가 끝납니다.
3) ⑤ 뒤: This dog became famous for keeping up this routine ~. this routine(이 습관 = 매일 역에 마중 나가기)과 the station은 주어진 문장에서만 나옵니다.
[정답 근거] Bobby 이야기가 끝나고 Hachiko의 습관을 설명하기 전인 ⑤가 정답입니다.
[오답 분석]
①~④: 아직 Bobby의 이야기가 진행 중이라 A similar statue가 들어가면 흐름이 끊깁니다.
[어휘] loyalty 충성 · grave 무덤 · keep watch over ~을 지키다 · headstone 묘비 · faithfulness 충실함', 39, 3),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 요약문 완성 (2점)
[유형 전략] 요약문은 먼저 요약문을 읽고 (A), (B)가 각각 무엇을 묻는지 확인한 뒤 지문을 읽습니다. 지문의 실험 결과나 결론 문장을 요약문의 표현과 짝지어, 같은 뜻을 다른 말로 바꾼(paraphrase) 선택지를 고릅니다.
[풀이 과정]
1) 요약문 분석: 작물 육종은 (A)가 적고 (B)가 넉넉한 환경을 주고, 마음에 드는 식물의 씨앗을 남기는 일이다.
2) (A) 근거: plant the seeds far enough apart that the plants do not compete much, pull some of them out, remove any weeds → 경쟁(competition)을 줄인다.
3) (B) 근거: has enough room to grow without being disturbed, too crowded → 공간(room)을 넉넉히 준다.
[정답 근거] (A) competition, (B) room인 ④가 정답입니다.
[오답 분석]
① shade(그늘)는 light를 충분히 받는다는 내용과 반대입니다.
② change는 근거가 없습니다.
③ (A) care(돌봄)가 적다는 것은 흙 고르기·솎아 내기·잡초 제거와 반대입니다.
⑤ (B) space는 맞지만 (A) variation(변이)은 근거가 없습니다.
[어휘] wag (꼬리를) 흔들다 · loosen 부드럽게 하다 · nutrient 영양분 · crowded 빽빽한 · weed 잡초', 40, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q41.webp]]', '[[img:/naesin/csat-h1-mock/r08/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 장문 제목 (2점)
[유형 전략] 장문 제목은 글 전체의 논지를 담아야 합니다. 도입의 일반적 진술과 후반부의 결론을 연결하여 주제를 정리하고, 글의 일부 사례만 다루는 선택지는 지웁니다.
[풀이 과정]
1) 첫 단락: 유령이 어둠 속에서만 나타나듯, 마음속 두려움도 우울할 때 나타난다. 더 밝은 관점으로 보면 두려움이 살아남기 어렵다.
2) 둘째 단락: 형의 생일 건배사 예시 — 망칠까 두려운 마음 대신 형의 기쁨, 좋은 추억, 끝난 뒤의 뿌듯함을 생각하면 두려움의 상당 부분이 사라진다.
3) 두 단락의 공통 핵심은 ‘두려움을 더 밝은 관점으로 보라’입니다.
[정답 근거] ①이 제목으로 적절합니다.
[오답 분석]
② 두려움에 실제 원인이 있다는 내용은 없습니다. 오히려 상상 속 괴물(imaginary monsters)이라고 합니다.
③ 조심하라는 교훈은 관련이 없습니다.
④ 연습은 언급되지 않습니다.
⑤ 주목받는 것을 피하라는 것은 건배사를 하라는 예시와 반대입니다.
[어휘] perspective 관점 · vanish 사라지다 · stumble over one''s words 말을 더듬다 · nerves 긴장, 초조 · adopt 취하다', 41, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q42.webp]]', '[[img:/naesin/csat-h1-mock/r08/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 장문 어휘 (2점)
[유형 전략] 장문 어휘 문항은 단락별 흐름을 잡은 뒤 밑줄 낱말을 하나씩 문맥에 넣어 봅니다. 앞뒤 문장과 인과·대조 관계가 맞지 않는 낱말, 반의어로 바꾸면 자연스러워지는 낱말이 정답입니다.
[풀이 과정]
1) (b)가 있는 문장은 In the morning light, the ghosts vanish(아침 햇살 속에서 유령이 사라진다)와 and ... as well로 이어집니다. as well(마찬가지로)이므로 마음속 괴물도 ‘사라져야(disappear)’ 합니다.
2) 다음 문장 Looking at a fear from a brighter perspective makes it far harder for that fear to survive.도 두려움이 줄어든다는 흐름입니다. multiply(늘어나다)는 반대 방향입니다.
[정답 근거] (b) multiply는 disappear로 바꾸어야 하므로 ②가 정답입니다.
[오답 분석]
(a) appear: 마음속 괴물은 우울할 때 주로 ‘나타난다’로 맞습니다.
(c) harder: 밝은 관점으로 보면 두려움이 살아남기 ‘더 어렵다’로 맞습니다.
(d) uneasy: 모두의 관심을 받는다는 생각에 ‘불안하다’로 맞습니다.
(e) remove: 밝은 관점이 두려움의 상당 부분을 ‘없앤다’로 맞습니다.
[어휘] multiply 늘어나다 · brighten 밝아지다 · survive 살아남다', 42, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q43.webp]]', '[[img:/naesin/csat-h1-mock/r08/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 장문 순서 (2점)
[유형 전략] 장문 순서는 등장인물의 행동과 시간의 흐름을 따라갑니다. 각 단락의 첫 문장이 앞 단락의 마지막 장면과 이어지는지(장소 이동, 대화의 응답, 사건의 결과) 확인합니다.
[풀이 과정]
1) (A) 끝: 아버지는 게으른 아들들이 땅을 돌보지 않을까 걱정했다.
2) (C) But the old man was wise.는 (A)의 걱정에 대한 해결책을 시작합니다. 땅속에 보물이 있다고 말하고, 아들들은 땅을 갈기 시작한다.
3) (D) At the end of every harvest: 해마다 수확으로 이익은 얻었지만 보물은 찾지 못하자, 큰형과 둘째는 아버지가 거짓말을 했다고 생각한다.
4) (B) 막내가 (D)의 의심에 답하며 ‘수확 자체가 보물’이라는 교훈으로 마무리합니다.
[정답 근거] (A) → (C) → (D) → (B)이므로 ③이 정답입니다.
[오답 분석]
(D)의 의심(father had lied)이 먼저 나와야 (B)의 Our father never lied to us가 반박으로 이어집니다.
[어휘] hardworking 근면한 · achieve 이루다 · abandon 버리다 · profit 이익 · fruits of one''s labor 노동의 결실', 43, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q44.webp]]', '[[img:/naesin/csat-h1-mock/r08/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 지칭 대상 (2점)
[유형 전략] 지칭 대상 문항은 밑줄 친 대명사가 나오는 문장에서 그 행동을 하는 사람이 누구인지 앞뒤 문맥으로 확인합니다. 대화문 속 you/I는 말하는 사람과 듣는 사람을 기준으로 따집니다.
[풀이 과정]
1) (a) 늘 땅을 돌보며 이룬 것을 자랑스러워한 부지런한 사람 → 아버지
2) (b) 아들들이 땅을 일구지 않을까 걱정한 사람 → 아버지
3) (c) “형들, 들어 봐”라고 말한 사람 → 막내아들(The youngest brother)
4) (d) 평생 이 땅을 일군 사람(막내의 말 속) → 아버지
5) (e) 목소리는 약했지만 말은 분명했던 사람 → 아버지
[정답 근거] (c)만 막내아들이고 나머지는 아버지이므로 ③이 정답입니다.
[오답 분석]
(c)와 (d)는 같은 단락에 있지만, (c)는 말하는 사람(막내), (d)는 막내가 이야기하는 대상(Our father)입니다.', 44, 2),
(@csh1_r08, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r08/q45.webp]]', '[[img:/naesin/csat-h1-mock/r08/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 장문 내용 불일치 (2점)
[유형 전략] 장문 내용 불일치는 선택지 순서가 대체로 글의 사건 순서를 따릅니다. 선택지의 핵심어로 해당 단락을 찾아 인물·행동·시간을 정확히 대조합니다.
[풀이 과정]
1) ② (C) I cannot tell you exactly where it is. 아버지는 보물의 위치를 알려 주지 않았습니다.
[정답 근거] ②가 글의 내용과 일치하지 않습니다.
[오답 분석]
① took pride in what he had achieved와 일치합니다.
③ pleased to have earned a good profit from their work와 일치합니다.
④ The middle brother agreed와 일치합니다.
⑤ "Our father never lied to us."와 일치합니다.', 45, 2);

-- 9회: 2026학년도 고1 더 모먼트 제1회 원본(고1 더 모먼트 1회)
INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-CSH1-MOCK-R09', 'NAESIN', '고1 수능 영어 독해 모의고사 9회 · 18~45번 변형문제 9회', '2026학년도 고1 더 모먼트 제1회 원본 · 28문항 · 45분 · 62점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 풀이 전략 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @csh1_r09 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-CSH1-MOCK-R09');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글의 목적 (2점)
[유형 전략] 글의 목적은 대개 글 후반부의 요청·안내 문장(please, we ask, I would like to, I am writing to)에 드러납니다. 앞부분의 인사·배경 설명에 끌려가지 말고, 필자가 독자에게 결국 무엇을 해 달라고 하는지 한 문장으로 정리한 뒤 선택지와 맞춰 봅니다.
[풀이 과정]
1) 감사 인사 뒤 Unfortunately로 흐름이 바뀝니다: I must decline. 해외 일정 때문에 기조연설 초청을 거절합니다.
2) 이어서 With this in mind, I would like to suggest Dr. Matthew Sanford as a speaker in my place. 대신할 사람을 추천합니다.
3) 추천 이유(20년 경력, 인지 행동 치료 전문가)와 연락 방법이 뒤따릅니다.
[정답 근거] 초청을 거절하면서 대신 발표할 사람을 추천하는 글이므로 ③이 정답입니다.
[오답 분석]
① 일정 변경을 요청하지 않고 거절합니다.
② cognitive behavioral therapy는 추천인의 전문 분야일 뿐입니다.
④ 회원 가입은 언급되지 않습니다.
⑤ 동료의 성과를 축하하는 글이 아닙니다.
[어휘] decline 거절하다 · commitment 약속, 할 일 · in one''s place ~ 대신에 · in advance 미리', 18, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 심경 변화 (2점)
[유형 전략] 심경 변화는 처음 상황과 전환 이후 상황을 나누어 읽습니다. 사건이 바뀌는 지점(when, suddenly, but, then)을 찾고, 그 앞뒤에서 인물의 감정을 드러내는 표현(형용사, 신체 반응, 혼잣말)을 각각 하나씩 골라 두 감정의 짝을 확인합니다.
[풀이 과정]
1) 전반부: felt fully ready, strode inside with my head held high, imagining myself as a great educator → 자신감(confident)이 넘칩니다.
2) 전환점: 교장이 특수 교육 학급이라고 알려 주고, 교실 문을 엽니다.
3) 후반부: felt my throat tighten, a wave of noise hit me, lesson plans suddenly seemed useless, my knees felt weak → 감당하기 힘들어 압도된 상태(overwhelmed)입니다.
[정답 근거] 자신감(confident)에서 압도됨(overwhelmed)으로 바뀌므로 ①이 정답입니다.
[오답 분석]
② 불안 → 안도는 흐름과 반대입니다.
③ 처음부터 지루하지 않았고, 나중에 매료되지도 않았습니다.
④ 감사의 감정은 드러나지 않습니다.
⑤ 마지막에 결심하는 장면이 없습니다.
[어휘] straighten 똑바로 하다 · echo 울리다 · behavioral 행동의 · scatter 흩뜨리다 · overwhelmed 압도된', 19, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 필자의 주장 (2점)
[유형 전략] 필자의 주장은 should, must, need to, it is important to, 명령문처럼 당위를 나타내는 문장에 담깁니다. 예시나 일화는 주장을 뒷받침하는 재료이므로, 일화가 끝난 뒤 필자가 일반화하는 문장을 찾아 그 내용과 같은 선택지를 고릅니다.
[풀이 과정]
1) 첫 문장: 지속가능성은 기업 운영에서 점점 더 필수적인 역할을 해야 한다.
2) must가 반복됩니다: leaders must adopt a sustainability-centered approach, Corporate leaders ... must take the lead.
3) 마지막 문장이 주장을 가장 분명하게 정리합니다: 경영진과 이사회가 지속가능성을 모든 사업 운영에 도입하는 데 앞장서야 한다.
[정답 근거] ④가 주장입니다.
[오답 분석]
① 단기 수익과 장기 투자의 비교는 없습니다.
② 변화의 주체는 소비자가 아니라 기업 리더입니다.
③ 정부 규제는 언급되지 않습니다.
⑤ measuring environmental impact는 리더가 다루는 영역의 하나일 뿐입니다.
[어휘] govern 운영하다 · sector 부문 · embrace 받아들이다 · take the lead 앞장서다 · operation 운영', 20, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 밑줄 함의 (3점)
[유형 전략] 밑줄 친 표현은 비유이므로 글자 그대로 해석하지 않습니다. 밑줄 앞뒤에서 같은 대상을 설명하는 문장을 찾아, 비유 속 단어(밑줄의 핵심 명사·동사)가 글에서 무엇에 대응하는지 짝지은 뒤, 그 의미를 풀어 쓴 선택지를 고릅니다.
[풀이 과정]
1) fog(안개)는 앞이 보이지 않는 상태, 즉 무언가가 분명하지 않은 상황을 비유합니다. 안개를 걷어 내는 것은 openness(투명성, 공개성)입니다.
2) 밑줄 앞에서 그 상황이 구체적으로 나옵니다: the unknown judgment hidden in the teacher''s grade book, the standards for evaluation are hidden or poorly explained, students tend to imagine the worst.
3) 따라서 안개는 ‘어떻게 평가받는지 몰라서 생기는 불안과 혼란’입니다.
[정답 근거] 평가 방식을 알지 못해 생기는 불안을 뜻하는 ⑤가 정답입니다.
[오답 분석]
① 학교 규칙의 복잡성은 다루지 않습니다.
② 세대 간 소통은 다루지 않습니다.
③ 학업 성취도 하락은 언급되지 않습니다.
④ 목표가 불분명하다는 것은 첫 부분의 행정 이야기와 관련 있지만, 밑줄은 학생이 느끼는 평가에 대한 불안입니다.
[어휘] inefficiency 비효율 · evaluation 평가 · take the place of ~을 대신하다 · openness 투명성 · regain 되찾다', 21, 3),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글의 요지 (2점)
[유형 전략] 요지는 글 전체가 말하려는 한 가지 핵심입니다. 도입의 질문·통념, 대조(however, but), 결론(therefore, in short)을 확인하고, 세부 예시 하나만 다룬 선택지나 지문 단어를 섞어 만든 그럴듯한 선택지는 지웁니다.
[풀이 과정]
1) 연구 결과 1: 일에 쓰는 시간의 양은 삶의 만족도와 관계없었다.
2) 연구 결과 2: What did predict satisfaction was the quality of their free time. 의미 있는 취미와 관계에 참여할수록 만족도가 높았다.
3) 결론: investing in meaningful leisure can be just as rewarding as moving up in your career.
[정답 근거] 의미 있는 여가가 삶의 만족도에 중요하다는 ③이 요지입니다.
[오답 분석]
① 여가의 ‘양’이 아니라 ‘질(quality)’이 핵심입니다. 가장 매력적인 오답입니다.
② 소득을 고려해도 관계가 유지되었다(even after ... took people''s incomes into account)고 했으므로 연봉의 효과가 아닙니다.
④ 직업적 성공도 만족도의 예측 변수였다(as powerful a predictor of satisfaction as career success)고 했으므로 지나친 진술입니다.
⑤ 균형의 어려움은 다루지 않습니다.
[어휘] waking hours 깨어 있는 시간 · devote 바치다 · predict 예측하다 · take ~ into account ~을 고려하다 · corporate ladder 회사 내 승진 사다리', 22, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글의 주제 (2점)
[유형 전략] 주제는 글의 핵심 소재와 그에 대한 필자의 관점을 함께 담은 영어 구로 나타납니다. 반복되는 핵심어를 찾고, 글이 그 소재의 무엇(원인, 효과, 과정, 중요성, 한계)을 다루는지 확인하여 범위가 너무 넓거나 좁은 선택지를 지웁니다.
[풀이 과정]
1) 어린아이 → 십 대 → 어른의 순서로 인내심의 변화를 설명합니다.
2) 어린아이와 십 대는 조급하고 좌절이 많다 → As we grow older, however, patience comes more naturally.
3) 마지막 문장: perhaps the ability to be patient is the true wisdom that experience brings.
[정답 근거] 경험이 쌓이면서 인내심이 길러지는 과정을 다룬 ⑤가 주제입니다.
[오답 분석]
① 아이에게 인내를 가르치는 방법은 다루지 않습니다.
② 십 대의 좌절이 언급되지만 어른과의 스트레스 비교가 주제가 아닙니다.
③ 좌절을 동기로 바꾸는 방법은 없습니다.
④ 인내가 성공에 미치는 영향은 다루지 않습니다.
[어휘] concrete 구체적인 · compromise 타협 · frustration 좌절 · timeline 일정 · circumstance 상황', 23, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글의 제목 (2점)
[유형 전략] 제목은 주제를 압축하거나 비유·의문문으로 바꾼 표현입니다. 먼저 글의 주제를 우리말로 정리한 뒤, 그 핵심을 담은 선택지를 고릅니다. 지문에 나온 단어가 그대로 쓰였지만 글의 일부만 다루거나 반대 방향을 말하는 선택지에 주의합니다.
[풀이 과정]
1) 핵심 문장: it becomes difficult to separate an aesthetic principle from a principle of nature.
2) 예시: 조화(harmony)는 자연의 일부인가, 예술가가 만든 것인가?
3) 결론: 이 미학 원리는 자연 속에 이미 들어 있을지도 모른다.
[정답 근거] 조화가 예술가의 발명인지 자연에서 발견된 것인지를 묻는 ①이 제목으로 적절합니다.
[오답 분석]
② 재능 있는 예술가의 조건은 다루지 않습니다.
③ 과학에서 논리의 한계를 다루는 글이 아닙니다.
④ 일상의 선택은 언급되지 않습니다.
⑤ 예술은 도구로 측정할 수 없다(cannot measure with instruments)고 했으므로 반대입니다.
[어휘] precision 정밀함 · interpret 해석하다 · harmony 조화 · abstract 추상적인 · faithfully 충실하게', 24, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 도표 불일치 (2점)
[유형 전략] 도표 문항은 선택지 문장을 하나씩 도표 수치와 맞춰 봅니다. 비교 표현(more than, twice, the lowest), 순위, 차이 계산이 함정이 되는 경우가 많으므로, 문장에 나온 항목·연도·수치를 도표에서 손가락으로 짚어 가며 확인합니다.
[풀이 과정]
1) ① 미국 6,100달러로 최고이며 6,000달러를 넘습니다. (일치)
2) ② 인도 1,000달러로 최저입니다. (일치)
3) ③ 영국 3,700 > 독일 3,400. (일치)
4) ④ 독일은 3,400달러로 3,000달러보다 많습니다. (불일치)
5) ⑤ 브라질 2,100 > 인도 1,000 × 2 = 2,000. (일치)
[정답 근거] 독일 프리랜서의 평균 수입은 3,000달러를 넘으므로 ④가 일치하지 않습니다.
[오답 분석]
less than, more than 같은 기준값 비교는 경계값(3,000달러, 2,000달러)을 정확히 따져야 합니다. ⑤처럼 두 배에 가까운 경우는 계산으로 확인합니다.
[어휘] freelance 프리랜서의 · average 평균의 · as of ~ 기준으로', 25, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 내용 불일치 (2점)
[유형 전략] 인물 내용 일치 문항은 선택지 순서가 대체로 글의 순서와 같습니다. 선택지의 핵심어(연도, 장소, 숫자, 고유명사)를 먼저 보고 지문에서 해당 부분을 찾아 정확히 대조합니다. 일부만 바꾼 진술(시기, 횟수, 주체)에 주의합니다.
[풀이 과정]
1) ② Because she never wrote full-time, she produced relatively few works. 전업 작가가 아니었고 작품 수도 비교적 적었습니다. 생계는 비서 일로 꾸렸습니다.
[정답 근거] ②가 글의 내용과 일치하지 않습니다.
[오답 분석]
① She wrote mainly out of a need to record her personal thoughts and experiences.와 일치합니다.
③ In 1999, a book review ... praised her mystery novels.와 일치합니다.
④ the other is kept in a library for retired British citizens in London과 일치합니다.
⑤ a creativity that made up for her lack of technical writing skill과 일치합니다.
[어휘] essentially 본질적으로 · support 부양하다 · feature (인물을) 다루다 · make up for ~을 보완하다', 26, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 안내문 불일치 (2점)
[유형 전략] 안내문은 항목별로 정보가 정리되어 있으므로 선택지의 핵심어(날짜, 시간, 대상, 비용, 신청 방법)로 해당 항목을 바로 찾습니다. 주의 사항·예외 조건(only, not, must)이 함정으로 자주 쓰입니다.
[풀이 과정]
1) ② Participation Fee: Adults (18 and older): $15, Students: $5. 15달러는 성인 참가비이고 학생은 5달러입니다. 비슷한 숫자를 다른 대상과 바꿔 놓은 선택지입니다.
[정답 근거] 학생 참가비는 5달러이므로 ②가 안내문과 일치하지 않습니다.
[오답 분석]
① Start time: 8 a.m.과 일치합니다.
③ Limited to 300 participants (first come, first served)와 일치합니다.
④ a certificate and a reusable water bottle과 일치합니다.
⑤ Gloves, trash bags, and grabbers will be provided free of charge.와 일치합니다.
[어휘] first come, first served 선착순 · certificate 인증서 · reusable 재사용할 수 있는 · free of charge 무료로', 27, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 안내문 일치 (2점)
[유형 전략] 안내문 일치 문항은 정답이 하나뿐이므로 선택지를 하나씩 대조하며 틀린 것을 지웁니다. 날짜·요일, 연령, 요금, 포함 여부, 사전 등록 여부처럼 숫자와 조건이 바뀐 부분을 꼼꼼히 확인합니다.
[풀이 과정]
1) ① Fee: $120 per participant per session (materials and snacks included). 재료와 간식이 포함되어 있습니다.
[정답 근거] ①이 안내문과 일치합니다.
[오답 분석]
② 할인 대상은 친구가 아니라 함께 등록하는 형제자매(siblings)입니다.
③ 12~14세는 오전 9시~12시 30분입니다.
④ 드론 비행은 Session B입니다.
⑤ No refunds after registration.
[어휘] hands-on 직접 해 보는 · sign up 등록하다 · inquiry 문의 · goggles 보안경', 28, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어법 (3점)
[유형 전략] 어법 문항은 밑줄마다 확인할 문법 포인트를 먼저 정합니다(동사 자리·수일치, 관계사와 접속사, 분사·to부정사, 병렬 구조, 대명사, 형용사·부사). 특히 문장의 주어와 본동사를 먼저 찾으면 수일치와 준동사 문제를 빠르게 판단할 수 있습니다.
[풀이 과정]
1) ② 뒤의 a university shares part of its research findings with a corporate partner는 주어·동사·목적어가 다 갖춰진 완전한 절입니다. 관계대명사 which 뒤에는 빠진 성분이 있는 불완전한 절이 와야 하므로, 완전한 절을 이끄는 ‘전치사 + 관계대명사’ in which가 되어야 합니다.
2) ① 주어 many universities(복수)에 맞춘 현재완료 have begun이 맞습니다. In recent years와도 어울립니다.
3) ③ encourage + 목적어 + to부정사(~가 …하도록 권장하다)이므로 to consider가 맞습니다.
4) ④ agreeing과 or로 병렬을 이루는 분사구문이므로 offering이 맞습니다.
5) ⑤ is의 보어가 되는 명사절을 이끄는 접속사 that이 맞습니다.
[정답 근거] 완전한 절 앞에 관계대명사 which를 쓴 ②가 틀렸습니다(→ in which).
어법 포인트: 관계대명사와 관계부사(전치사 + 관계대명사) — 뒤에 오는 절이 완전한지 먼저 봅니다. 주어나 목적어가 빠져 있으면 관계대명사(which, that), 다 갖춰져 있으면 in which, where 같은 관계부사 자리입니다. 원래 문장으로 바꾸면 a university shares part of its findings in the programs이므로 in이 필요합니다.
[어휘] collaboration 협력 · findings 연구 결과 · application 응용 · dedicate 바치다 · sponsor 후원하다', 29, 3),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 어휘 (2점)
[유형 전략] 어휘 문항은 글의 흐름(원인과 결과, 대조, 예시)을 먼저 잡고, 밑줄 친 낱말이 그 흐름과 반대 방향인지 확인합니다. 밑줄 낱말을 반의어로 바꿔 넣었을 때 문맥이 자연스러워지면 그것이 정답입니다.
[풀이 과정]
1) ② 문장은 Unfortunately(불행히도)로 시작하고, 세미콜론 뒤에서 ‘의욕 없고 성과가 낮은 직원이 가장 자주 꼬리표가 붙는다’고 합니다.
2) 불행한 일이려면 긍정적인 꼬리표는 ‘흔하지 않아야(rare)’ 합니다. common은 Unfortunately 및 뒤의 내용과 맞지 않습니다.
[정답 근거] ② common은 rare로 바꾸어야 하므로 ②가 정답입니다.
[오답 분석]
① 많은 범주가 ‘사용된다’로 맞습니다.
③ 비효율적이라는 꼬리표에 ‘맞춰’ 더 비효율적이 된다로 맞습니다.
④ 프로젝트를 모두에게 ‘더 어렵게’ 만든다로 맞습니다.
⑤ 기회 있을 때마다 그 꼬리표를 ‘강화하면’ 긍정적 영향을 줄 수 없다로 맞습니다.
[어휘] supervisor 관리자 · unmotivated 의욕이 없는 · inefficient 비효율적인 · precisely 바로 · reinforce 강화하다', 30, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 전반부: 디트로이트와 포드는 대량 생산으로 세계를 바꾼 최고의 산업 제국이었다.
2) 전환: 그 우위가 두 세대 만에 사라질 것이라고 하면 미쳤다고 했겠지만, And yet you would have been right.
3) 빈칸: 1973년 석유 위기 때 크게 약해졌고, 2010년에는 디트로이트가 ‘폐허’가 되었다는 흐름입니다.
[정답 근거] 쇠락한 도시의 모습을 나타내는 ruin(폐허)이 들어가야 하므로 ④가 정답입니다.
[오답 분석]
① leader, ③ model은 전성기의 모습으로 흐름과 반대입니다.
② 경쟁자로 남았다는 근거는 없습니다.
⑤ 중재자는 관련이 없습니다.
[어휘] mass production 대량 생산 · innovative 혁신적인 · empire 제국 · out of one''s mind 정신이 나간 · weaken 약화시키다', 31, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 사례: 어부들이 파괴적인 저인망 어업을 멈추고 해양 보호 구역을 만들었다.
2) 결과: 어족 자원이 회복되고, 지속 가능한 어획과 생태 관광이 가능해졌다.
3) 빈칸: 이 사례가 보여 주는 것은 지역의 행동과 적응이 ‘해양 생태계를 보호하는 데’ 필수적이라는 점입니다.
[정답 근거] ④가 정답입니다.
[오답 분석]
① 해운업 인력 양성은 언급되지 않습니다.
② 도시화는 관련이 없습니다.
③ 언어 장벽은 다루지 않습니다.
⑤ 디지털 연결은 언급되지 않습니다.
[어휘] make a living 생계를 꾸리다 · halt 멈추다 · sustainable 지속 가능한 · reserve 보호 구역 · conservation 보존', 32, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 연구 결과: environmental and behavioral factors clearly matter more than genes.
2) 근거: 같은 DNA를 가진 일란성 쌍둥이도 발암 물질 노출과 생활 습관에 따라 결과가 크게 다르다.
3) 빈칸 앞 문장: 부모가 모두 암을 앓았어도 생활 방식으로 암 없는 삶을 살 수 있다. In other words로 이어지는 빈칸은 이를 일반화한 말입니다.
[정답 근거] 유전자가 건강을 결정할 필요는 없다는 ③이 정답입니다.
[오답 분석]
① DNA가 바뀐다는 내용은 없습니다. 쌍둥이는 같은 DNA를 유지합니다.
② 모든 병을 막는다는 것은 지나친 일반화입니다.
④ 생활 습관의 선택이 결과를 바꾼다는 내용과 반대입니다.
⑤ 부모와 같은 환경이라는 것은 근거가 없습니다.
[어휘] inherit 물려받다 · outcome 결과 · exposure 노출 · substance 물질 · lifestyle 생활 방식', 33, 3),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 빈칸 문장은 ‘이상적인 시장이라면’ 고용주가 어떻게 할지를 말하고, 바로 뒤 But does this actually happen?으로 현실과 대조합니다.
2) 실험 결과(현실): 자격이 같은 이력서인데도 환경에 대한 태도(개인적 가치)에 따라 급여를 다르게 제시했다.
3) 따라서 이상적인 시장에서는 고용주가 개인적 가치가 아니라 능력으로 지원자를 평가할 것이라는 내용이 빈칸에 들어가야 합니다.
[정답 근거] 개인적 가치가 아니라 능력으로 판단한다는 ③이 정답입니다.
[오답 분석]
① 같은 급여로 더 오래 일하게 한다는 내용은 지문에 없습니다. 실험이 잰 것은 근무 시간이 아니라 제시한 급여였습니다.
② 실험의 이력서는 모두 같았고(identical résumés), 빈칸 문장도 자격이 비슷하다는 조건을 둡니다. 경력이 많은 사람을 뽑는다는 것은 이 조건과 맞지 않습니다.
④ 인맥은 언급되지 않습니다.
⑤ 실험에서 실제로 나타난 현실의 모습으로, 이상과 대조되는 내용입니다.
[어휘] qualification 자격 · résumé 이력서 · applicant 지원자 · conscious 의식하는 · commitment 헌신', 34, 3),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 무관한 문장 (2점)
[유형 전략] 무관한 문장 문항은 첫 문장(주제문)이 제시한 소재와 방향을 확인한 뒤 각 문장이 그 방향을 이어 가는지 봅니다. 정답 문장은 지문의 핵심어를 일부 포함하지만 논점이 다른 쪽(다른 대상, 다른 측면)으로 벗어난 경우가 많습니다.
[풀이 과정]
1) 화제: 미래의 도전에 미리 대비하는 ‘선행 학습(pre-learning)’과 그 실행 방법.
2) ①②③④는 방법을 순서대로 안내합니다: 어려운 개념 정하기 → 스터디 파트너에게 자료 구하기 → 모르는 것에 시간 쓰지 않기 → 대신 새 내용에 집중하기.
3) ⑤는 조용하고 밝은 곳에서 공부하면 눈의 피로가 줄어든다는 내용으로, 선행 학습 방법과 관계가 없습니다.
[정답 근거] 공부 환경과 눈의 피로를 다룬 ⑤가 흐름과 관계없습니다.
[오답 분석]
③과 ④는 avoid ~ / Focus instead on ~으로 짝을 이루는 문장입니다. 마지막 문장이라도 앞의 방법 설명과 같은 논점인지 확인해야 합니다.
[어휘] gap 빈틈 · in advance 미리 · master 숙달하다 · approach 다가가다 · strain 피로', 35, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글의 순서 (2점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 곧은 수로는 흐르는 물을 나르기에 좋지 않은 설계다.
2) (B)는 그 근거로 자연의 개울을 관찰하게 합니다. 물은 구불구불한 길을 내며, 물이 빠를수록 침식이 커진다.
3) (C)의 This is why는 (B)의 원리(빠른 물 = 큰 침식)를 받아, 현대 관개가 굽은 수로로 효율적이 되었다고 합니다.
4) (A)의 This change는 (C)의 ‘굽은 수로로 바꾼 것’을 가리키며 그 효과로 마무리합니다.
[정답 근거] 주어진 글 → (B) → (C) → (A)이므로 ③이 정답입니다.
[오답 분석]
(C)의 This is why는 이유가 되는 원리(B)가 먼저 나와야 하고, (A)의 This change는 변화의 내용(C)이 먼저 나와야 합니다.
[어휘] channel 수로 · winding 구불구불한 · bend 굽은 곳 · adopt 받아들이다', 36, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 순서 (3점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 훌륭한 상사에게는 직원이 리더 역할을 즐기는 주된 이유다.
2) (C)의 Such outstanding supervisors는 주어진 글의 훌륭한 상사를 받아, 그들이 팀원과의 관계에서 만족을 얻는다고 설명합니다.
3) (A)의 just as valuable when your supervisor is struggling은 (C)의 훌륭한 상사와 대비해 ‘힘든 상사’의 경우로 확장합니다.
4) (B)의 That discouragement는 (A)의 deeply discouraging(몹시 낙담하게 한다)을 받아, 그래서 힘든 상사도 열의 있는 직원을 소중히 여긴다고 이어 가고, 그런 직원이 받는 보상으로 마무리합니다.
[정답 근거] 주어진 글 → (C) → (A) → (B)이므로 ④가 정답입니다.
[오답 분석]
(B)의 even a struggling supervisor는 힘든 상사 이야기(A)가 먼저 나와야 자연스럽고, (A)의 just as valuable은 비교 대상인 훌륭한 상사 이야기(C)가 먼저 나와야 합니다.
[어휘] keep in mind 명심하다 · outstanding 뛰어난 · discouraging 낙담시키는 · favorable 유리한 · promotion 승진', 37, 3),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 문장 삽입 (2점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장은 For instance로 시작하는 예시이며, 해외 경험이 있는 요리사 Bottura를 소개합니다. 앞에는 ‘요리사의 이동’이라는 일반론이, 뒤에는 Bottura에 대한 부연이 와야 합니다.
2) ① 앞: the movement of chefs across continents and cultures, which has blended cooking techniques and traditions(요리사의 이동과 기술의 혼합).
3) ① 뒤: His signature dishes ~. His가 가리킬 사람(Bottura)이 주어진 문장에서 처음 등장합니다.
[정답 근거] 요리사 이동의 일반론과 His signature dishes 사이인 ①이 정답입니다.
[오답 분석]
②~⑤: 이미 His, he로 Bottura를 가리키는 문장이 나오거나 소셜 미디어로 화제가 바뀐 뒤라 예시를 처음 소개할 자리가 아닙니다.
[어휘] defining 결정적인 · driver 원동력 · blend 섞다 · signature dish 대표 요리 · appreciation 인식, 감상', 38, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 문장 삽입 (3점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장: 이런 변명은 해가 없지만, 지나치면 해롭다. Excuses like these는 앞에 가벼운 변명의 예가 나와야 하고, becomes harmful은 뒤에 해로운 합리화의 예가 올 것을 예고합니다.
2) ③ 앞: 시험 점수, 승진 탈락에 대한 가벼운 변명의 예.
3) ③ 뒤: 늘 남을 탓하거나 책임을 지지 않는 것은 아무에게도 도움이 되지 않는다 — 지나친 합리화의 예입니다.
[정답 근거] 가벼운 변명의 예와 해로운 합리화의 예 사이인 ③이 정답입니다.
[오답 분석]
①②: 아직 변명의 예가 끝나지 않았습니다.
④⑤: 이미 해로운 합리화의 예가 나온 뒤입니다.
[어휘] defense mechanism 방어 기제 · explain away 해명하다, 둘러대다 · pass over (승진에서) 제외하다 · unethical 비윤리적인', 39, 3),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 요약문 완성 (2점)
[유형 전략] 요약문은 먼저 요약문을 읽고 (A), (B)가 각각 무엇을 묻는지 확인한 뒤 지문을 읽습니다. 지문의 실험 결과나 결론 문장을 요약문의 표현과 짝지어, 같은 뜻을 다른 말로 바꾼(paraphrase) 선택지를 고릅니다.
[풀이 과정]
1) 요약문 분석: (A) 수행되는 숙련된 동작이 정신적 자원을 풀어 주어 우리가 (B)할 수 있게 한다.
2) (A) 근거: you do not consciously calculate, perform without thinking, Whatever we can do automatically → automatically(자동적으로).
3) (B) 근거: focus on something else, such as predicting an opponent''s strategy, the thinking part of the brain is free to plan → think strategically(전략적으로 생각하다).
[정답 근거] (A) automatically, (B) think strategically인 ⑤가 정답입니다.
[오답 분석]
①④ consciously(의식적으로)는 반대입니다.
② precisely, compete는 근거가 없습니다.
③ (A)는 맞지만 memorize(암기하다)는 풀려난 정신적 자원으로 하는 일(전략 세우기)과 다릅니다.
[어휘] path 경로 · awareness 인식 · coordinated 협응된 · exhaustion 고갈 · capacity 능력', 40, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q41.webp]]', '[[img:/naesin/csat-h1-mock/r09/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 장문 제목 (2점)
[유형 전략] 장문 제목은 글 전체의 논지를 담아야 합니다. 도입의 일반적 진술과 후반부의 결론을 연결하여 주제를 정리하고, 글의 일부 사례만 다루는 선택지는 지웁니다.
[풀이 과정]
1) 핵심: 새 기술을 익힐 때는 처음에 실력이 떨어지고(performance curve will drop) 집중은 더 많이 필요하다.
2) 그 구간에서는 연습해도 실력이 계속 나빠지는 것처럼 느껴져 많은 사람이 포기한다(give up, just before it would have started to pay off).
3) 즉, 새 기술을 배우는 것이 왜 뒷걸음질처럼 느껴지는지를 설명하는 글입니다.
[정답 근거] ④가 제목으로 적절합니다.
[오답 분석]
① 배울 가치가 있는 기술의 조건은 다루지 않습니다.
② 연습을 줄이라는 내용은 없습니다.
③ 재능과 노력을 비교하지 않습니다.
⑤ 휴식 시점은 언급되지 않습니다.
[어휘] set out to ~하기 시작하다 · concentration 집중 · automatic 자동적인 · pay off 성과를 내다', 41, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q42.webp]]', '[[img:/naesin/csat-h1-mock/r09/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 장문 어휘 (2점)
[유형 전략] 장문 어휘 문항은 단락별 흐름을 잡은 뒤 밑줄 낱말을 하나씩 문맥에 넣어 봅니다. 앞뒤 문장과 인과·대조 관계가 맞지 않는 낱말, 반의어로 바꾸면 자연스러워지는 낱말이 정답입니다.
[풀이 과정]
1) (b) 다음 문장이 So your performance curve will drop(그래서 실력 곡선이 떨어진다)입니다. So로 이어지려면 앞에서 새 기술로 ‘더 못하게(worse)’ 연주한다고 해야 합니다.
2) 둘째 단락의 your performance keeps getting worse도 처음에는 실력이 떨어진다는 같은 흐름입니다. better → worse
[정답 근거] (b) better는 worse로 바꾸어야 하므로 ②가 정답입니다.
[오답 분석]
(a) struggle: 이 문제를 모르면 진전 없이 계속 ‘애쓰게’ 된다로 맞습니다.
(c) drop: 실력 곡선이 ‘떨어진다’로 맞습니다.
(d) more: 익숙하지 않으니 집중이 ‘더’ 필요하다로 맞습니다.
(e) worse: 그 구간에서는 실력이 계속 ‘나빠진다’로 맞습니다.
[어휘] curve 곡선 · familiar 익숙한 · zone 구간', 42, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q43.webp]]', '[[img:/naesin/csat-h1-mock/r09/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 장문 순서 (2점)
[유형 전략] 장문 순서는 등장인물의 행동과 시간의 흐름을 따라갑니다. 각 단락의 첫 문장이 앞 단락의 마지막 장면과 이어지는지(장소 이동, 대화의 응답, 사건의 결과) 확인합니다.
[풀이 과정]
1) (A) 끝: Emily가 Sarah에게 기자가 되고 싶다는 꿈을 이야기한다.
2) (B) Sarah told her ~는 그 꿈에 대한 Sarah의 조언이며, 인턴십 기회로 이어집니다. 마지막에 Emily가 아직 경험이 적은 십 대였다고 합니다.
3) (D) That is why it was so difficult는 (B)의 ‘경험이 적었다’를 이유로 받아, 첫 과제가 어려웠던 장면을 이어 갑니다.
4) (C) Emily also discovered ~는 성장 과정의 마무리로, 졸업과 성공, 멘토에 대한 감사로 끝납니다.
[정답 근거] (A) → (B) → (D) → (C)이므로 ①이 정답입니다.
[오답 분석]
(D)의 That is why는 (B)의 마지막 문장(경험이 거의 없는 십 대)이 앞에 있어야 의미가 통합니다.
[어휘] tiresome 지루한, 귀찮은 · take an interest in ~에 관심을 갖다 · assignment 과제 · cover 취재하다 · credit A to B A를 B의 덕분으로 여기다', 43, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q44.webp]]', '[[img:/naesin/csat-h1-mock/r09/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 지칭 대상 (2점)
[유형 전략] 지칭 대상 문항은 밑줄 친 대명사가 나오는 문장에서 그 행동을 하는 사람이 누구인지 앞뒤 문맥으로 확인합니다. 대화문 속 you/I는 말하는 사람과 듣는 사람을 기준으로 따집니다.
[풀이 과정]
1) (a) Emily에게 관심을 갖고 사진을 가르친 사람 → Sarah
2) (b) 어느새 인턴십을 하게 된 사람 → Emily
3) (c) 우등으로 졸업한 사람 → Emily
4) (d) 성공을 공동체와 멘토 덕분으로 여긴 사람 → Emily
5) (e) 경험이 부족했지만 최선을 다해 첫 기사를 낸 사람 → Emily
[정답 근거] (a)만 Sarah이고 나머지는 Emily이므로 ①이 정답입니다.
[오답 분석]
(a)는 Sarah took an interest in Emily에 이어지는 절의 주어로, 사진을 가르치는 사람(Sarah)이고 her photography의 her가 Emily입니다.', 44, 2),
(@csh1_r09, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r09/q45.webp]]', '[[img:/naesin/csat-h1-mock/r09/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 장문 내용 불일치 (2점)
[유형 전략] 장문 내용 불일치는 선택지 순서가 대체로 글의 사건 순서를 따릅니다. 선택지의 핵심어로 해당 단락을 찾아 인물·행동·시간을 정확히 대조합니다.
[풀이 과정]
1) ⑤ (C) Emily also discovered that she had a natural talent for the social sciences and ethics. 사회과학과 윤리에 재능이 있었습니다.
[정답 근거] ⑤가 글의 내용과 일치하지 않습니다.
[오답 분석]
① When she was about fifteen, she met Sarah Thompson, a local journalist와 일치합니다.
② began teaching her photography와 일치합니다.
③ an internship at a local newspaper ... still a teenager와 일치합니다.
④ the article came back covered in comments와 일치합니다.', 45, 2);

-- 10회: 2026학년도 고1 더 모먼트 제2회 원본(고1 더 모먼트 2회)
INSERT INTO tp_exams (exam_code, kind, title, subtitle, description, difficulty, time_limit_sec, status, created_by) VALUES ('TP-NAESIN-CSH1-MOCK-R10', 'NAESIN', '고1 수능 영어 독해 모의고사 10회 · 18~45번 변형문제 10회', '2026학년도 고1 더 모먼트 제2회 원본 · 28문항 · 45분 · 62점', '실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 풀이 전략 해설을 볼 수 있습니다.', NULL, 7200, 'PUBLISHED', 'system');
SET @csh1_r10 = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-NAESIN-CSH1-MOCK-R10');
INSERT INTO tp_questions (exam_id, section, task_type, prompt, body_text, audio_mode, choices_json, answer_key, auto_score, explanation, order_no, points) VALUES
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q18.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글의 목적 (2점)
[유형 전략] 글의 목적은 대개 글 후반부의 요청·안내 문장(please, we ask, I would like to, I am writing to)에 드러납니다. 앞부분의 인사·배경 설명에 끌려가지 말고, 필자가 독자에게 결국 무엇을 해 달라고 하는지 한 문장으로 정리한 뒤 선택지와 맞춰 봅니다.
[풀이 과정]
1) 배경: 연구 센터가 다음 학기에 연구 보조원을 더 뽑을 계획이라는 소식을 들었다.
2) 근거: 인턴십 동안 분석 능력과 자료 수집 능력을 인정받았다.
3) 요청: I would very much like to rejoin your center, this time as a research assistant. 연구 보조원으로 지원하는 글입니다.
[정답 근거] ②가 정답입니다.
[오답 분석]
① 인턴 모집이 아니라 연구 보조원 채용에 관한 글입니다.
③ 인턴십은 이미 끝난 경력으로 언급될 뿐, 수료증 요청은 없습니다.
④ 센터를 홍보하는 글이 아닙니다.
⑤ 오히려 다시 참여하고 싶다는 글이므로 반대입니다.
[어휘] rewarding 보람 있는 · firsthand 직접 · analytical 분석적인 · efficiency 효율성 · application 지원(서)', 18, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q19.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 심경 (2점)
[유형 전략] 심경 문항은 인물이 처한 상황과 감정을 드러내는 표현(형용사, 표정, 행동, 속마음)을 모아 하나의 감정으로 정리합니다. 부정적 표현이 일부 섞여 있어도 글 전체의 분위기와 마지막 장면의 감정이 무엇인지 확인합니다.
[풀이 과정]
1) 성취를 드러내는 표현: we had found a wall of ancient rock carvings that no one on our team had ever recorded.
2) 기쁨·감동을 드러내는 표현: breathless in the best possible way, magnificent scenery, we grinned at each other, at the very heart of the earth.
3) trudging, Our legs ached는 몸이 지쳤다는 뜻이지만 바로 neither of us complained가 이어져 부정적 감정이 아님을 보여 줍니다.
[정답 근거] 큰 발견을 이루고 장관에 벅찬, 만족스럽고 신나는 심경이므로 ①이 정답입니다.
[오답 분석]
② 동료와 함께 있고 절망하지 않습니다.
③ 무관심과 지루함은 장관에 대한 감동과 반대입니다.
④ trudging, ached에서 떠올리기 쉬운 오답이지만, 실망한 것이 아니라 성과에 만족합니다.
⑤ 불안이나 죄책감은 없습니다.
[어휘] speck 작은 점 · expedition 탐험 · formation (암석의) 형성물 · rock carving 암각화 · magnificent 장엄한', 19, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q20.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 필자의 주장 (2점)
[유형 전략] 필자의 주장은 should, must, need to, it is important to, 명령문처럼 당위를 나타내는 문장에 담깁니다. 예시나 일화는 주장을 뒷받침하는 재료이므로, 일화가 끝난 뒤 필자가 일반화하는 문장을 찾아 그 내용과 같은 선택지를 고릅니다.
[풀이 과정]
1) 통념: 상업화는 자유로운 지식 공유라는 학문적 가치에 어긋난다.
2) 반박(This belief, however, is mistaken): 발견이 사회에 도움이 되려면 많은 사람이 써야 하고, 그러려면 시장 가치가 있는 제품이나 서비스로 바뀌어야 한다.
3) 결론(therefore): 상업화 노력 없이 학술 경로로만 공유하면 현실 문제를 해결하기 어렵다.
[정답 근거] 연구가 사회에 기여하려면 상업화가 필요하다는 ③이 주장입니다.
[오답 분석]
①② 필자가 반박하는 통념입니다.
④ 기업은 상업화 과정의 제3자로 언급될 뿐, 투자 확대를 주장하지 않습니다.
⑤ it seldom creates the competitive advantage needed to succeed in the market과 반대입니다.
[어휘] widespread 널리 퍼진 · third party 제3자 · competitive advantage 경쟁 우위 · channel 경로 · address (문제를) 다루다', 20, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q21.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 밑줄 함의 (3점)
[유형 전략] 밑줄 친 표현은 비유이므로 글자 그대로 해석하지 않습니다. 밑줄 앞뒤에서 같은 대상을 설명하는 문장을 찾아, 비유 속 단어(밑줄의 핵심 명사·동사)가 글에서 무엇에 대응하는지 짝지은 뒤, 그 의미를 풀어 쓴 선택지를 고릅니다.
[풀이 과정]
1) vulture(독수리)는 죽은 동물을 먹고 사는 새로, 남의 불행을 이용해 이익을 챙기는 사람을 비유합니다.
2) 글의 예시: 2달러짜리 얼음을 10달러에, 250달러짜리 발전기를 2,000달러에, 40달러짜리 모텔방을 160달러에 받았다.
3) 따라서 ‘폭풍 뒤에 독수리가 온다’는 재난 뒤에 피해자들의 어려움을 이용해 터무니없는 값을 받는 판매자들이 나타난다는 뜻입니다.
[정답 근거] ①이 정답입니다.
[오답 분석]
② 폭풍이 지나간 뒤의 자연적 피해를 말하는 것이 아닙니다.
③ vulture를 글자 그대로 해석한 오답입니다.
④ 신문 제목이 등장할 뿐, 언론의 과장을 다루지 않습니다.
⑤ 다른 주의 도움은 언급되지 않습니다.
[어휘] roar out of ~에서 맹렬히 몰아치다 · contractor 시공업자 · generator 발전기 · outraged 격분한 · inflated 부풀려진', 21, 3),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q22.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 글의 요지 (2점)
[유형 전략] 요지는 글 전체가 말하려는 한 가지 핵심입니다. 도입의 질문·통념, 대조(however, but), 결론(therefore, in short)을 확인하고, 세부 예시 하나만 다룬 선택지나 지문 단어를 섞어 만든 그럴듯한 선택지는 지웁니다.
[풀이 과정]
1) 연구 결과: 꿈의 내용 대부분은 평범한 일상에서 오며, 약간 뒤섞이고 재배열된 형태로 나타난다.
2) 예시: 사냥꾼은 숲을, 판사는 재판을, 회계사는 숫자를 꿈꾼다.
3) 결론: 이런 꿈은 일상의 경험을 조각내어 섞고 서서히 사라지게 하는 자연스러운 정신 과정이며, 의식적인 노력 없이 저절로 진행된다.
[정답 근거] ④가 요지입니다.
[오답 분석]
① 미래를 암시한다는 내용은 없습니다.
② 직업별 예시를 이용한 오답입니다. 예시는 꿈이 ‘일상에서 온다’는 근거일 뿐, 직업에 따른 차이가 요지가 아닙니다.
③ 꿈을 기록하는 방법은 다루지 않습니다.
⑤ Golding, Claudian은 같은 생각을 보여 주는 인용일 뿐입니다.
[어휘] spill out 쏟아져 나오다 · rearrange 재배열하다 · column (숫자의) 열 · break ~ into pieces ~을 조각내다 · fade away 사라지다', 22, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q23.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 글의 주제 (2점)
[유형 전략] 주제는 글의 핵심 소재와 그에 대한 필자의 관점을 함께 담은 영어 구로 나타납니다. 반복되는 핵심어를 찾고, 글이 그 소재의 무엇(원인, 효과, 과정, 중요성, 한계)을 다루는지 확인하여 범위가 너무 넓거나 좁은 선택지를 지웁니다.
[풀이 과정]
1) 첫 문장: 스타트업은 해적에게 특별한 동질감을 느껴 왔다.
2) 기원: 스티브 잡스의 구호와 해적 깃발 → 실리콘 밸리에 해적 이미지가 자리 잡았다.
3) 이유: 초기 스타트업은 불확실하고 적대적인 환경에서 빠르게 움직이고, 규칙을 깨고, 위험을 감수한다는 점에서 해적선과 닮았다.
[정답 근거] 스타트업이 해적선에 비유되는 이유를 다룬 ③이 주제입니다.
[오답 분석]
① 규칙을 깨는 것은 오히려 생존에 필요한 정신으로 소개됩니다.
② 매킨토시는 해적 이미지가 생긴 계기일 뿐입니다.
④ 위원회 회의를 열지 않는다는 것이 해적의 특징이므로 반대입니다.
⑤ 해적의 역사는 다루지 않습니다.
[어휘] embrace 받아들이다 · stick 자리 잡다 · charm 매료하다 · hostile 적대적인 · fierce 치열한', 23, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q24.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 글의 제목 (2점)
[유형 전략] 제목은 주제를 압축하거나 비유·의문문으로 바꾼 표현입니다. 먼저 글의 주제를 우리말로 정리한 뒤, 그 핵심을 담은 선택지를 고릅니다. 지문에 나온 단어가 그대로 쓰였지만 글의 일부만 다루거나 반대 방향을 말하는 선택지에 주의합니다.
[풀이 과정]
1) 첫 문장: 모래는 평범해 보이지만 현대 문명의 토대다.
2) 근거: 콘크리트(건물), 유리(창, 병, 화면), 실리콘(컴퓨터 칩)의 원료가 된다.
3) 반전(Ironically): 쓸모 있는 모래는 한정되어 있고 빠르게 고갈되어, 가장 귀하고 다툼이 많은 물질 중 하나가 되었다.
[정답 근거] 평범하지만 현대 생활을 떠받치는 모래를 다룬 ⑤가 제목으로 적절합니다.
[오답 분석]
① 사막 모래는 건설에 쓰기에 너무 매끄럽다고 했으므로 반대입니다.
② 유리는 모래의 쓰임새 중 하나일 뿐입니다.
③ 해변의 형성은 다루지 않습니다.
④ 컴퓨터 칩 산업은 모래의 역할 중 일부만 다룹니다.
[어휘] civilization 문명 · essential 필수적인 · transform 변형시키다 · riverbed 강바닥 · substance 물질', 24, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q25.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 도표 불일치 (2점)
[유형 전략] 도표 문항은 선택지 문장을 하나씩 도표 수치와 맞춰 봅니다. 비교 표현(more than, twice, the lowest), 순위, 차이 계산이 함정이 되는 경우가 많으므로, 문장에 나온 항목·연도·수치를 도표에서 손가락으로 짚어 가며 확인합니다.
[풀이 과정]
1) ① 발전 25%로 가장 크고 4분의 1입니다. (일치)
2) ② 두 번째는 삼림 벌채 17%입니다. (일치)
3) ③ 도로 운송 10% vs 석유·가스 생산 5% + 비료 4% = 9%. 도로 운송이 더 큽니다. (일치)
4) ④ 가축 6% > 시멘트 3%. 가축이 더 크므로 smaller는 틀렸습니다. (불일치)
5) ⑤ 항공, 철강, 폐기물 모두 2%. (일치)
[정답 근거] 가축(6%)의 비율이 시멘트(3%)보다 크므로 ④가 일치하지 않습니다.
[오답 분석]
combined(합친)가 나오면 두 수치를 더한 뒤 비교합니다. 차이가 1%p처럼 작게 설계되는 경우가 많아 꼭 계산해야 합니다.
[어휘] emission 배출 · deforestation 삼림 벌채 · transport 운송', 25, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q26.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 내용 불일치 (2점)
[유형 전략] 인물 내용 일치 문항은 선택지 순서가 대체로 글의 순서와 같습니다. 선택지의 핵심어(연도, 장소, 숫자, 고유명사)를 먼저 보고 지문에서 해당 부분을 찾아 정확히 대조합니다. 일부만 바꾼 진술(시기, 횟수, 주체)에 주의합니다.
[풀이 과정]
1) ① Marie Curie was born in Warsaw, Poland. 폴란드 바르샤바에서 태어났고, 파리는 공부하러 간 곳입니다.
[정답 근거] ①이 글의 내용과 일치하지 않습니다.
[오답 분석]
② In 1891, she moved to Paris to study science at the Sorbonne과 일치합니다.
③ After receiving her first Nobel Prize in 1903, she continued ~와 일치합니다.
④ none of them had managed to isolate pure radioactive elements(다른 과학자들은 해내지 못했다)와 일치합니다.
⑤ honored on banknotes in both France and Poland와 일치합니다.
[어휘] magnetic 자기의 · lay the foundations of ~의 기초를 놓다 · pioneering 선구적인 · legacy 유산 · banknote 지폐', 26, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q27.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 안내문 불일치 (2점)
[유형 전략] 안내문은 항목별로 정보가 정리되어 있으므로 선택지의 핵심어(날짜, 시간, 대상, 비용, 신청 방법)로 해당 항목을 바로 찾습니다. 주의 사항·예외 조건(only, not, must)이 함정으로 자주 쓰입니다.
[풀이 과정]
1) ④ Notes: The entry fee is $30 per player. 참가비는 선수 1인당 30달러입니다.
[정답 근거] ④가 안내문과 일치하지 않습니다.
[오답 분석]
① November 6–8로 3일입니다.
② Doubles matches: 2 p.m. – 4 p.m.과 일치합니다.
③ All participants will receive a T-shirt.와 일치합니다.
⑤ On-site registration is not available.과 일치합니다.
[어휘] tournament 대회 · award 수여하다 · entry fee 참가비 · on-site 현장의', 27, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q28.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 안내문 일치 (2점)
[유형 전략] 안내문 일치 문항은 정답이 하나뿐이므로 선택지를 하나씩 대조하며 틀린 것을 지웁니다. 날짜·요일, 연령, 요금, 포함 여부, 사전 등록 여부처럼 숫자와 조건이 바뀐 부분을 꼼꼼히 확인합니다.
[풀이 과정]
1) ② Open every day from 9 a.m. to 6 p.m. 매일 오전 9시부터 오후 6시까지 운영합니다.
[정답 근거] ②가 안내문과 일치합니다.
[오답 분석]
① Follow the clues on your smartphone으로 스마트폰을 사용합니다.
③ Forest Course는 12세 이상(ages 12 and over)입니다.
④ 30달러는 2~4명 팀 기준(per team)입니다.
⑤ 할인 코드는 10% 할인입니다.
[어휘] clue 단서 · checkpoint 확인 지점 · sign up 등록하다', 28, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q29.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 어법 (3점)
[유형 전략] 어법 문항은 밑줄마다 확인할 문법 포인트를 먼저 정합니다(동사 자리·수일치, 관계사와 접속사, 분사·to부정사, 병렬 구조, 대명사, 형용사·부사). 특히 문장의 주어와 본동사를 먼저 찾으면 수일치와 준동사 문제를 빠르게 판단할 수 있습니다.
[풀이 과정]
1) ⑤ stay는 ‘~한 상태로 있다’는 뜻의 2형식 동사로, 뒤에 보어로 형용사가 와야 합니다. 따라서 invisibly가 아니라 invisible이 맞습니다.
2) ① 주어는 the total effect(단수)이고 that they produce over several years는 이를 꾸미는 관계절입니다. 따라서 is가 맞습니다.
3) ② Only + 부사구(Only by looking back ~ later)가 문장 앞에 오면 주어와 조동사의 순서가 바뀝니다(도치). do we clearly see가 맞습니다.
4) ③ worth + 명사(the effort): ‘~의 가치가 있는’으로 맞습니다.
5) ④ makes it easy to ~: it은 가목적어, to ignore 이하가 진목적어이므로 맞습니다.
[정답 근거] stay의 보어 자리에 부사를 쓴 ⑤가 틀렸습니다(→ invisible).
어법 포인트: 2형식 동사의 보어 — remain, stay, become, seem, look, sound 등의 뒤에는 주어의 상태를 설명하는 형용사가 옵니다. ‘~하게’로 해석된다고 부사를 쓰면 안 됩니다.
[어휘] pile up 쌓이다 · drift back into ~로 되돌아가다 · routine 일상 · invisible 보이지 않는', 29, 3),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q30.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 어휘 (2점)
[유형 전략] 어휘 문항은 글의 흐름(원인과 결과, 대조, 예시)을 먼저 잡고, 밑줄 친 낱말이 그 흐름과 반대 방향인지 확인합니다. 밑줄 낱말을 반의어로 바꿔 넣었을 때 문맥이 자연스러워지면 그것이 정답입니다.
[풀이 과정]
1) 글의 흐름: 인공지능과 자동화, 클라우드 덕분에 업무가 놀랄 만큼 빨라지고 부서 간 연결이 쉬워졌다.
2) ③ 앞 문장은 복잡한 분석을 예전의 간단한 계산만큼 빠르게 끝낸다는 내용이고, 클라우드도 같은 ‘속도와 편리함’의 예입니다. 따라서 ‘지연된(delayed)’ 접근이 아니라 ‘즉각적인(instant)’ 접근이어야 합니다.
[정답 근거] ③ delayed는 instant로 바꾸어야 하므로 적절하지 않습니다.
[오답 분석]
① 변화가 ‘빠른’ 속도로 일어난다로 맞습니다.
② 기술 덕분에 일터가 ‘더 작게’ 느껴진다(가까워졌다)로 맞습니다.
④ 지능형 조직이라는 목표에 ‘더 가까워진다’로 맞습니다.
⑤ 다른 부서와 협업하기 쉬워져 부서 간 장벽이 생길 가능성이 ‘줄었다’로 맞습니다.
[어휘] breakthrough 획기적 발전 · automation 자동화 · statistics 통계 · integration 통합 · considerably 상당히', 30, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q31.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 칸트의 핵심: 행동이 진정한 도덕적 가치를 가지려면 의무(duty)에서 나와야 하며, 이익(self-interest)이나 자연스러운 동정심에서 나와서는 안 된다.
2) 예시: 장사에 이득이 되어서 정직한 상인은 이기적 동기(a selfish motive)에서 행동한 것이다.
3) 빈칸: 진정한 도덕적 가치는 의무만을 따를 때, 심지어 그것이 자기 자신의 ‘~’에 어긋날 때도 나타난다 → 자기 이익, 하고 싶은 마음 등 개인적 욕구(desires)입니다.
[정답 근거] ④가 정답입니다.
[오답 분석]
② 우정은 natural sympathy(자연스러운 동정심)에서 떠올릴 수 있지만, 빈칸은 의무와 맞서는 ‘자기 자신’의 것이어야 하고 글의 대립은 의무 대 이기적 동기입니다.
③ 교육, ① 기억, ⑤ 전통은 의무와 대립하는 개념으로 제시되지 않습니다.
[어휘] will 의지 · in itself 그 자체로 · duty 의무 · sympathy 동정심 · conduct 행동 · motive 동기', 31, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q32.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 빈칸 추론 (2점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 빈칸 바로 앞: AI는 자료 분석과 일상적 의사 결정을 자동화해 관리자가 운영 업무에 쓰는 시간을 줄인다.
2) 빈칸 문장의 As a result와 boosts managers'' creativity: 줄어든 시간을 창의적인 일에 쓸 수 있게 된다.
3) 빈칸 뒤 문장도 같은 내용입니다: freeing people for more imaginative work.
[정답 근거] 창의적인 해결책을 만드는 데 더 많은 시간을 쓰게 된다는 ④가 정답입니다.
[오답 분석]
① AI가 자료 분석을 자동화한다고 했으므로 분석가를 더 뽑는다는 것은 흐름과 반대입니다.
② 상품 보관 공간은 다루지 않습니다.
③ AI는 고객의 행동과 선호를 분석해 맞춤 상품을 제안하므로, 고객에게 관심을 덜 둔다는 것은 반대입니다.
⑤ AI가 일상적 결정을 자동화해 줄어드는 것이 바로 이런 반복 업무입니다.
[어휘] supply 공급 · demand 수요 · personalized 개인 맞춤형의 · automate 자동화하다 · devote A to B A를 B에 쏟다', 32, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q33.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 빈칸 앞 문장: Such dependence on recreation and tourism can also lead people to overlook ecological concerns.(관광 의존이 생태 문제를 외면하게 만든다)
2) 예시: 관광으로 돈을 벌지 않는 사람들이 관광에 생계를 의존하는 주민보다 생태 의식이 훨씬 높았다.
3) 빈칸: 이 차이의 원인은 관광에 의존하는 주민들의 ‘경제적 이해관계’입니다. 일반적인 환경 문제에 대한 견해 차이는 거의 없었다는 마지막 부분이 이를 뒷받침합니다.
[정답 근거] ⑤가 정답입니다.
[오답 분석]
② 일반적 환경 문제에 대한 견해 차이는 매우 작았다(differed very little)고 했으므로 원인이 될 수 없습니다. 가장 매력적인 오답입니다.
① 문화적 전통, ③ 거주 기간, ④ 교육 수준은 언급되지 않습니다. 두 집단을 가른 것은 관광으로 돈을 버느냐(rely on tourism for their income)였습니다.
[어휘] fragile 취약한 · concentrate 집중시키다 · overlook 간과하다 · livelihood 생계 · stem from ~에서 비롯되다', 33, 3),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q34.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 빈칸 추론 (3점)
[유형 전략] 빈칸 추론은 빈칸 문장이 글 전체의 주제문인 경우가 많습니다. 빈칸 앞뒤의 연결어와 반복되는 핵심 개념을 찾고, 예시·부연 설명을 근거로 빈칸에 들어갈 내용을 우리말로 먼저 정해 본 뒤 선택지와 비교합니다.
[풀이 과정]
1) 빈칸 앞: 인간다움이 합리성에 달려 있다는 생각은 고대 그리스로 거슬러 올라간다.
2) However: 하지만 15~16세기 유럽인에게 중요했던 합리성은 (고대 그리스의 것이 아니라) 다른 것이었다는 대조입니다.
3) 빈칸 바로 뒤: It was the kind of thinking that was then producing rapid advances in astronomy, physics, and the making of maps. 그 시기 천문학·물리학·지도 제작을 발전시키던 사고, 곧 과학에 바탕을 둔 새로운 추론 방식입니다.
4) 그 뒤: 원주민의 사고방식이 유럽인의 ‘보편적 이성(universal reason)’ 관념과 맞지 않아 고등 사고를 못 한다고 판단했다.
[정답 근거] 과학에 바탕을 둔 새로운 추론 방식이라는 ②가 정답입니다.
[오답 분석]
⑤ However가 고대 그리스와 대조하므로 반대입니다. 가장 매력적인 오답입니다.
① 라틴어 읽고 쓰기는 언급되지 않으며, 빈칸 뒤의 과학 발전과도 이어지지 않습니다.
③ 교회와 왕에 대한 충성은 ‘합리성’이 아닙니다.
④ 어른들에게 물려받은 지혜는 오히려 유럽인이 무시한 원주민의 전통에 가깝습니다.
[어휘] justify 정당화하다 · conquer 정복하다 · rationality 합리성 · astronomy 천문학 · look down on ~을 깔보다 · legacy 유산', 34, 3),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q35.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 무관한 문장 (2점)
[유형 전략] 무관한 문장 문항은 첫 문장(주제문)이 제시한 소재와 방향을 확인한 뒤 각 문장이 그 방향을 이어 가는지 봅니다. 정답 문장은 지문의 핵심어를 일부 포함하지만 논점이 다른 쪽(다른 대상, 다른 측면)으로 벗어난 경우가 많습니다.
[풀이 과정]
1) 첫 문장의 화제: 헤테로포니 — 같은 선율을 여러 연주자가 조금씩 다르게 연주하는 짜임새.
2) ①②는 다르게 연주하는 방법(꾸밈음, 리듬·템포의 차이), ④는 이 짜임새가 쓰이는 문화권, ⑤는 구체적인 곡의 예입니다.
3) ③은 백 명 이상이 필요한 서양 교향곡 이야기로, 헤테로포니라는 화제와 관계가 없습니다.
[정답 근거] 서양 교향곡의 규모를 다룬 ③이 흐름과 관계없습니다.
[오답 분석]
③은 바로 뒤 ④의 Western music과 단어가 겹쳐 자연스러워 보이지만, ④의 This texture는 헤테로포니를 가리켜야 하므로 ③을 빼야 ② → ④가 이어집니다.
[어휘] texture (음악의) 짜임새 · decoration 장식 · set apart 구별하다 · layered 겹겹의', 35, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q36.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 글의 순서 (2점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 버섯 외에도 곰팡이, 흰곰팡이, 깜부기, 녹병, 마름병, 효모가 모두 균류다.
2) (A)의 those unpleasant-sounding names는 주어진 글에 나열된 이름들을 가리킵니다. 그 이름들은 식물의 병을 가리키지만, 균류의 역할은 병을 넘어선다.
3) (C)의 also는 (A)의 ‘병을 일으키는 역할’에 더해 균류의 이로운 역할(식물과의 협력, 음식·약, 청소부)을 덧붙입니다.
4) (B)는 균류의 다양한 형태와 분류 방법으로 화제를 넓히며 마무리합니다.
[정답 근거] 주어진 글 → (A) → (C) → (B)이므로 ①이 정답입니다.
[오답 분석]
(A)의 those ... names는 이름이 나열된 주어진 글 바로 뒤에 와야 합니다. (B)가 끼어들면 those가 가리킬 대상이 멀어집니다.
[어휘] overlook 간과하다 · single-celled 단세포의 · classify 분류하다 · thrive 번성하다 · pile up 쌓이다', 36, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q37.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 글의 순서 (3점)
[유형 전략] 글의 순서는 각 단락의 첫 부분에 있는 연결어(However, For example, As a result), 지시어(this, these, such), 대명사(he, they)가 가리키는 대상을 단서로 삼습니다. 주어진 글의 마지막 문장과 자연스럽게 이어지는 단락부터 찾습니다.
[풀이 과정]
1) 주어진 글: 우리는 자신은 의도와 노력으로, 남은 결과로 평가한다.
2) (B) For example: 회의에서 동료를 돕지 못했을 때 우리는 ‘말하려 했는데 기회가 없었다’며 자신의 의도로 판단한다.
3) (A)의 however는 (B)와 반대 상황을 제시합니다. 우리가 비판받을 때 동료가 침묵하면 그들을 결과로만 판단해 비난한다.
4) (C)의 So the next time은 앞의 두 사례를 근거로, 동료도 노력으로 평가하라는 결론을 내립니다.
[정답 근거] 주어진 글 → (B) → (A) → (C)이므로 ②가 정답입니다.
[오답 분석]
(A)의 however는 대조할 앞 사례(B)가 있어야 하고, (C)의 So는 두 사례가 모두 제시된 뒤의 결론이므로 맨 끝에 옵니다.
[어휘] intention 의도 · speak up 의견을 말하다 · bother to 일부러 ~하다 · label A as B A에게 B라는 꼬리표를 붙이다 · disloyal 충실하지 않은', 37, 3),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q38.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'B', 1, '정답 ② · 문장 삽입 (2점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장의 this idea는 앞에 나온 생각을, take ~ even further는 그 생각을 더 밀고 나간 주장이 이어진다는 것을 알려 줍니다.
2) ② 앞: 안을 들여다보는 것(looking inward)만으로는 학습을 이해할 수 없고 외부 관찰이 필요하다는 생각(this idea).
3) ② 뒤: According to these psychologists, there is no need to explore inner mental states. these psychologists가 가리킬 행동주의 심리학자들은 주어진 문장에서 처음 나옵니다.
[정답 근거] ②가 정답입니다.
[오답 분석]
①: 아직 this idea(외부 관찰이 필요하다)가 나오기 전입니다.
③~⑤: these psychologists가 이미 나온 뒤이므로, 그들을 처음 소개하는 문장이 오기에 늦습니다.
[어휘] observable 관찰할 수 있는 · subjective 주관적인 · inward 안으로 · organism 유기체 · punishment 벌', 38, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q39.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 문장 삽입 (3점)
[유형 전략] 문장 삽입은 주어진 문장의 연결어·지시어·대명사를 먼저 분석해, 그 앞에 와야 할 내용과 뒤에 이어질 내용을 예측합니다. 지문에서 흐름이 끊기는 곳(갑자기 대상이 바뀌거나 지시어가 가리킬 대상이 없는 곳)을 찾으면 정답 위치가 드러납니다.
[풀이 과정]
1) 주어진 문장: 나중에 학생이 발전하면 악보 표기(the written notation)를 빼도 된다. 앞에는 악보를 쓰는 단계가, 뒤에는 악보 없이 신호에만 반응하는 단계가 와야 합니다.
2) ⑤ 앞: In the earliest stages ~ along with written notation과 At this stage, the notes on the page show ~. 처음 단계에서는 악보가 어떤 기술을 쓸지 알려 줍니다 — 아직 악보를 씁니다.
3) ⑤ 뒤: 아주 작은 시각 신호(고개 끄덕임, 지휘봉의 움직임)에도 반응하는 단계 — 악보 없이 신호만으로 연주합니다.
[정답 근거] ⑤가 정답입니다.
[오답 분석]
④: 주어진 문장을 넣으면 At this stage가 ‘악보를 뺀 단계’를 가리키게 되는데, 바로 그 문장이 악보(the notes on the page)가 기술을 알려 준다고 하므로 모순이 됩니다.
①~③: 아직 악보가 언급되지 않았거나 처음 소개되는 단계입니다.
[어휘] instruction 교육 · passage (악곡의) 악절 · cue 신호 · notation 악보 표기 · thoroughly 철저히', 39, 3),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q40.webp]]', NULL, 'NONE', '["①", "②", "③", "④", "⑤"]', 'C', 1, '정답 ③ · 요약문 완성 (2점)
[유형 전략] 요약문은 먼저 요약문을 읽고 (A), (B)가 각각 무엇을 묻는지 확인한 뒤 지문을 읽습니다. 지문의 실험 결과나 결론 문장을 요약문의 표현과 짝지어, 같은 뜻을 다른 말로 바꾼(paraphrase) 선택지를 고릅니다.
[풀이 과정]
1) 요약문 분석: 주거 선택의 변화 중 일부는 자발적이지만, 많은 경우 도시 현대화의 (A) 때문에 생기며, 주민들은 비싸고 (B)한 주택으로 내몰린다.
2) (A) 근거: factors over which local residents have little control(주민이 통제할 수 없는 요인) — 상업용 부동산을 짓기 위해 저렴한 주택에서 돈과 땅을 빼 가는 것 → pressures(압력).
3) (B) 근거: overpriced and too poorly built to properly shelter them → poor-quality(질 낮은).
[정답 근거] (A) pressures, (B) poor-quality인 ③이 정답입니다.
[오답 분석]
①④ benefits(혜택)는 주민이 겪는 불리한 결과와 반대입니다.
② luxurious(호화로운)는 too poorly built와 반대입니다.
⑤ (A)는 맞지만 traditional(전통적인) 주택은 오히려 줄어든 쪽입니다.
[어휘] voluntary 자발적인 · developer 개발업자 · status 지위 · affordable 저렴한 · shelter 보호하다', 40, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q41.webp]]', '[[img:/naesin/csat-h1-mock/r10/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 장문 제목 (2점)
[유형 전략] 장문 제목은 글 전체의 논지를 담아야 합니다. 도입의 일반적 진술과 후반부의 결론을 연결하여 주제를 정리하고, 글의 일부 사례만 다루는 선택지는 지웁니다.
[풀이 과정]
1) 첫 단락: 언어의 첫 번째 특징 — 개방성(open). 어휘와 문법으로 한 번도 말해진 적 없는 새 문장을 만들 수 있다.
2) 둘째 단락: 두 번째 특징(Another feature) — 전위성(displaced). 지금 여기에 없는 것에 대해 말할 수 있다. 벌의 춤은 이와 대조되는 예입니다.
3) 두 단락을 묶으면 ‘인간 언어의 두 가지 특징: 개방성과 전위성’입니다.
[정답 근거] ①이 제목으로 적절합니다.
[오답 분석]
② 벌은 대조를 위한 예일 뿐이며, 새끼에게 춤을 가르치는 내용은 없습니다.
③ 거짓말은 전위성으로 가능한 일의 하나일 뿐입니다.
④ 문법과 어휘의 기원은 다루지 않습니다.
⑤ 벌의 의사소통은 인간 언어와 대조되어 한계를 보여 주는 예입니다.
[어휘] feature 특징 · fixed 고정된 · resource 자원 · abstract 추상적인 · vehicle 수단', 41, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q42.webp]]', '[[img:/naesin/csat-h1-mock/r10/p41-42.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'D', 1, '정답 ④ · 장문 어휘 (2점)
[유형 전략] 장문 어휘 문항은 단락별 흐름을 잡은 뒤 밑줄 낱말을 하나씩 문맥에 넣어 봅니다. 앞뒤 문장과 인과·대조 관계가 맞지 않는 낱말, 반의어로 바꾸면 자연스러워지는 낱말이 정답입니다.
[풀이 과정]
1) (d)가 있는 문장은 벌의 의사소통이 ‘지금 여기’에 묶여 있다는 점을 보여 줍니다. 벌은 꿀이 있는 곳을 ‘방금 발견했을(found)’ 때만 춤으로 알린다는 뜻이어야 합니다.
2) 앞에서 벌의 춤은 동료들에게 먹이가 있는 곳을 알려 주는 것이라고 했으므로, ‘잊어버렸을(forgotten)’ 때 알린다는 것은 앞뒤가 맞지 않습니다.
[정답 근거] (d) forgotten은 found로 바꾸어야 하므로 ④가 정답입니다.
[오답 분석]
(a) create: 새로운 메시지를 ‘만들어 낸다’로 맞습니다.
(b) new: 개방적인 체계이므로 ‘새로운’ 것에 대해 말할 수 있다로 맞습니다.
(c) present: 지금 ‘존재하지 않는’ 것에 대해 말할 수 있다로 맞습니다.
(e) nothing: 주변 환경과 ‘아무 관련이 없는’ 목적으로도 언어를 쓴다로 맞습니다.
[어휘] hive 벌집 · celebrate 기념하다 · immediate 바로 가까이의', 42, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q43.webp]]', '[[img:/naesin/csat-h1-mock/r10/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 장문 순서 (2점)
[유형 전략] 장문 순서는 등장인물의 행동과 시간의 흐름을 따라갑니다. 각 단락의 첫 문장이 앞 단락의 마지막 장면과 이어지는지(장소 이동, 대화의 응답, 사건의 결과) 확인합니다.
[풀이 과정]
1) (A) 끝: 교사가 악기는 꾸준히 연습할 수 있는 학생을 위한 것이라고 말한다.
2) (D) The girl looked down for a moment.는 (A)의 교사 말에 대한 반응입니다. 소녀는 부모님의 허락서를 내밀고, 학생들이 음악실로 들어갑니다.
3) (C) As the other students gathered around the instruments는 (D)의 탁자 위 악기들을 이어받습니다. 소녀가 구석의 금 간 바이올린을 발견합니다.
4) (B) The teacher came over and said ~ that violin은 (C)에서 소녀가 고른 바이올린에 대한 교사의 말이며, 할머니 사진으로 이야기가 마무리됩니다.
[정답 근거] (A) → (D) → (C) → (B)이므로 ⑤가 정답입니다.
[오답 분석]
(B)의 reached into her backpack once again(다시 한 번)은 (D)에서 처음 가방에서 허락서를 꺼낸 뒤에 와야 합니다.
[어휘] give away 나누어 주다 · adjust 고쳐 쓰다 · commit to ~에 전념하다 · tuck away 숨겨 두다 · imperfection 결함', 43, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q44.webp]]', '[[img:/naesin/csat-h1-mock/r10/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'E', 1, '정답 ⑤ · 지칭 대상 (2점)
[유형 전략] 지칭 대상 문항은 밑줄 친 대명사가 나오는 문장에서 그 행동을 하는 사람이 누구인지 앞뒤 문맥으로 확인합니다. 대화문 속 you/I는 말하는 사람과 듣는 사람을 기준으로 따집니다.
[풀이 과정]
1) (a) 악기를 하나 갖고 싶다고 말한 사람 → 소녀
2) (b) 다시 가방에 손을 넣어 사진을 꺼낸 사람 → 소녀
3) (c) 할머니 이야기를 한 사람 → 소녀
4) (d) 금 간 바이올린을 가리키며 “저걸 원해요”라고 한 사람 → 소녀
5) (e) “물론이지”라고 대답하고 앞장선 사람 → 교사
[정답 근거] (e)만 교사이고 나머지는 소녀이므로 ⑤가 정답입니다.
[오답 분석]
(e)는 소녀의 질문(Is that enough to see them?)에 대한 대답이므로 말하는 사람은 교사입니다. 바로 뒤 the teacher led the way도 이를 확인해 줍니다.', 44, 2),
(@csh1_r10, 'EN', 'NAESIN_MC5', '[[img:/naesin/csat-h1-mock/r10/q45.webp]]', '[[img:/naesin/csat-h1-mock/r10/p43-45.webp]]', 'NONE', '["①", "②", "③", "④", "⑤"]', 'A', 1, '정답 ① · 장문 내용 불일치 (2점)
[유형 전략] 장문 내용 불일치는 선택지 순서가 대체로 글의 사건 순서를 따릅니다. 선택지의 핵심어로 해당 단락을 찾아 인물·행동·시간을 정확히 대조합니다.
[풀이 과정]
1) ① She wrote a notice announcing the instruments and began pinning it to the bulletin board in the hallway. 공고는 칠판에 적은 것이 아니라 복도 게시판에 붙였습니다.
[정답 근거] ①이 글의 내용과 일치하지 않습니다.
[오답 분석]
② "I have a permission slip from my parents."와 일치합니다.
③ (D) The instruments were laid out on tables: a trumpet, a flute, a clarinet, and a drum. 네 가지와 일치합니다.
④ a small case that was badly worn and scratched와 일치합니다.
⑤ pulled out a photograph, and showed it to the teacher와 일치합니다.', 45, 2);
