-- 내신 실전 연습(V1116) 발음부호 문항 수정: 정답 보기가 '강세 위치만 틀린' 형태였던 24문항을
-- '강세는 맞고 발음부호 하나가 틀린' 형태로 바꾸고 해설 첫 단락을 고친다. 정답 번호(answer_key)와 배점은 그대로다.
-- 문항 그림(static/naesin/apgujeong-m2-2026-2/rNN/qNN.webp)은 같은 이름으로 다시 만들었다.
-- 이미 발음부호가 틀린 형태였던 8문항(4회 2번, 6회 1번, 7회 1번, 8회 1번, 9회 1번, 12회 2번, 15회 2번, 16회 2번)은 바꾸지 않는다.

UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ② · 발음부호 (3점)
preserve의 s는 [z]로 소리 나므로 바른 발음부호는 [prɪˈzɜːrv]이다. ②는 [s]로 적어 틀리다.
근거: 필기본 01쪽 Lesson 7 단어 · preserve [prɪˈzɜːrv]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R01' AND q.order_no = 1;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ④ · 발음부호 (3점)
glacier의 ci는 [ʃ]로 소리 나므로 바른 발음부호는 [ˈɡleɪʃər]이다. ④는 [s]로 적어 틀리다.
근거: 필기본 01쪽 Lesson 7 단어 · glacier [ˈɡleɪʃər]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R01' AND q.order_no = 2;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ③ · 발음부호 (3점)
dynamic의 y는 [aɪ]로 소리 나므로 바른 발음부호는 [daɪˈnæmɪk]이다. ③은 [ɪ]로 적어 틀리다.
근거: 필기본 01쪽 Lesson 7 단어 · dynamic [daɪˈnæmɪk]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R02' AND q.order_no = 1;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ⑤ · 발음부호 (3점)
동사 present의 바른 발음부호는 [prɪˈzent]이다(명사는 [ˈpreznt]). ⑤는 s를 [s]로 적어 틀리다.
근거: 필기본 01쪽 present [ˈpreznt] / 동사 [prɪˈzent], 02쪽 하단 필기(present: 명사/동사 강세)'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R02' AND q.order_no = 2;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ① · 발음부호 (3점)
illegal의 g는 [ɡ]로 소리 나므로 바른 발음부호는 [ɪˈliːɡəl]이다. ①은 [dʒ]로 적어 틀리다.
근거: 필기본 01쪽 Lesson 7 단어 · illegal [ɪˈliːɡəl]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R03' AND q.order_no = 1;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ② · 발음부호 (3점)
species의 c는 [ʃ]로 소리 나므로 바른 발음부호는 [ˈspiːʃiːz]이다. ②는 [s]로 적어 틀리다.
근거: 필기본 01쪽 Lesson 7 단어 · species [ˈspiːʃiːz]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R03' AND q.order_no = 2;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ⑤ · 발음부호 (3점)
explore의 바른 발음부호는 [ɪkˈsplɔːr]이다. ⑤는 l을 [r]로 적어 틀리다.
근거: 필기본 01쪽 Lesson 7 단어 · explore [ɪkˈsplɔːr]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R04' AND q.order_no = 1;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ② · 발음부호 (3점)
monitor의 o는 [ɑː]로 소리 나므로 바른 발음부호는 [ˈmɑːnɪtər]이다. ②는 [oʊ]로 적어 틀리다.
근거: 필기본 01쪽 Lesson 7 단어 · monitor [ˈmɑːnɪtər], effort [ˈefərt], explore [ɪkˈsplɔːr], illegal [ɪˈliːɡəl], product [ˈprɑːdʌkt]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R05' AND q.order_no = 1;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ④ · 발음부호 (3점)
control의 둘째 o는 [oʊ]로 소리 나므로 바른 발음부호는 [kənˈtroʊl]이다. ④는 [ɑː]로 적어 틀리다.
근거: 필기본 01쪽 Lesson 7 단어 · control [kənˈtroʊl], hunting [ˈhʌntɪŋ], leopard [ˈlepərd], species [ˈspiːʃiːz], glacier [ˈɡleɪʃər]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R05' AND q.order_no = 2;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ④ · 발음부호 (3점)
customer의 u는 [ʌ]로 소리 나므로 바른 발음부호는 [ˈkʌstəmər]이다. ④는 [uː]로 적어 틀리다.
근거: 필기본 12~13쪽 Word List · customer [ˈkʌstəmər]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R06' AND q.order_no = 2;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ① · 발음부호 (3점)
department의 a는 [ɑː]로 소리 나므로 바른 발음부호는 [dɪˈpɑːrtmənt stɔːr]이다. ①은 [ɔː]로 적어 틀리다.
근거: 필기본 12쪽 Word List · department store [dɪˈpɑːrtmənt stɔːr]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R07' AND q.order_no = 2;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ① · 발음부호 (3점)
villager의 a는 [ɪ]로 약하게 소리 나므로 바른 발음부호는 [ˈvɪlɪdʒər]이다. ①은 [eɪ]로 적어 틀리다.
근거: 필기본 12~13쪽 Word List · villager [ˈvɪlɪdʒər], return, honestly, knock, shake'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R08' AND q.order_no = 2;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ⑤ · 발음부호 (3점)
silently의 i는 [aɪ]로 소리 나므로 바른 발음부호는 [ˈsaɪləntli]이다. ⑤는 [ɪ]로 적어 틀리다.
근거: 필기본 12쪽 Word List · silently [ˈsaɪləntli], customer, department store, pop out, return'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R09' AND q.order_no = 2;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ④ · 발음부호 (3점)
sparrow의 a는 [æ]로 소리 나므로 바른 발음부호는 [ˈspæroʊ]이다. ④는 [eɪ]로 적어 틀리다.
근거: 필기본 12~13쪽 Word List · sparrow [ˈspæroʊ], stripe [straɪp], sock [sɑːk], shake [ʃeɪk], customer [ˈkʌstəmər]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R10' AND q.order_no = 1;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ① · 발음부호 (3점)
return의 ur은 [ɜːr]로 소리 나므로 바른 발음부호는 [rɪˈtɜːrn]이다. ①은 [uːr]로 적어 틀리다.
근거: 필기본 12~13쪽 Word List · return [rɪˈtɜːrn], trouble [ˈtrʌbəl], villager [ˈvɪlɪdʒər], silently [ˈsaɪləntli], pair [per]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R10' AND q.order_no = 2;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ② · 발음부호(7과) (3점)
tree의 ee는 [iː]로 소리 나므로 바른 발음부호는 [ˈbeɪoʊbæb triː]이다. ②는 [eɪ]로 적어 틀리다.
근거: 필기본 01쪽 Lesson 7 단어 · baobab tree [ˈbeɪoʊbæb triː], effort, dynamic, icy, hunting'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R11' AND q.order_no = 1;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ④ · 발음부호(8과) (3점)
together의 th는 [ð]로 소리 나므로 바른 발음부호는 [brɪŋ … təˈɡeðər]이다. ④는 [θ]로 적어 틀리다.
근거: 필기본 12~13쪽 Lesson 8 Word List · bring … together [brɪŋ … təˈɡeðər], customer, silently, villager, sparrow'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R11' AND q.order_no = 2;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ② · 발음부호 (3점)
hunting의 u는 [ʌ]로 소리 나므로 바른 발음부호는 [ˈhʌntɪŋ]이다. ②는 [uː]로 적어 틀리다.
근거: 필기본 01쪽 Lesson 7 단어 · hunting [ˈhʌntɪŋ], glacier, leopard, monitor, species'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R12' AND q.order_no = 1;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ② · 발음부호(7과) (3점)
product의 o는 [ɑː]로 소리 나므로 바른 발음부호는 [ˈprɑːdʌkt]이다. ②는 [oʊ]로 적어 틀리다.
근거: 필기본 01쪽 Lesson 7 단어 · product [ˈprɑːdʌkt]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R13' AND q.order_no = 1;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ④ · 발음부호(8과) (3점)
pop의 o는 [ɑː]로 소리 나므로 바른 발음부호는 [ˌpɑːp ˈaʊt]이다. ④는 [oʊ]로 적어 틀리다.
근거: 필기본 12쪽 Lesson 8 Word List · pop out [ˌpɑːp ˈaʊt]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R13' AND q.order_no = 2;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ② · 발음부호 (3점)
wonderland의 o는 [ʌ]로 소리 나므로 바른 발음부호는 [ˈwʌndərlænd]이다. ②는 [ɑː]로 적어 틀리다.
근거: 필기본 02쪽 Lesson 7 단어 · wonderland [ˈwʌndərlænd]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R14' AND q.order_no = 1;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ② · 발음부호 (3점)
covered의 o는 [ʌ]로 소리 나므로 바른 발음부호는 [bi ˈkʌvərd wɪð]이다. ②는 [oʊ]로 적어 틀리다.
근거: 필기본 12쪽 Lesson 8 Word List · be covered with [bi ˈkʌvərd wɪð]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R14' AND q.order_no = 2;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ① · 발음부호 (3점)
바른 발음부호는 [ˈefərt]이다. ①은 e를 [iː]로 적어 틀리다.
근거: 필기본 01쪽 Lesson 7 단어 · effort [ˈefərt]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R15' AND q.order_no = 1;
UPDATE tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id SET q.explanation = '정답 ① · 발음부호 (3점)
명사 present의 바른 발음부호는 [ˈpreznt]이다(동사는 [prɪˈzent]). ①은 s를 [s]로 적어 틀리다.
근거: 필기본 01쪽 Lesson 7 단어 · present 명사 [ˈpreznt] / 동사 [prɪˈzent]'
  WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R16' AND q.order_no = 1;
