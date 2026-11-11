-- ONLY: task-local test DB(ac_unified_test). 모든 이름·연락처는 합성 테스트 값이다. 운영 DB에 쓰지 않는다.
-- e2e/unified-assistant/fixtures.sql 적용 뒤 실행한다. 수업 요일은 실행 날짜와 무관하게 모든 요일에 둬 오늘 수업이 항상 보이게 한다.
USE ac_unified_test;
INSERT INTO rea_members(id,academy_id,role,name,auth_key,active,failed_login_attempts,created_at) VALUES(104,8,'ACADEMY_OWNER','다른 학원 관리자','local-only-owner-104',1,0,NOW()) ON DUPLICATE KEY UPDATE active=1;
INSERT INTO rea_students(id,academy_id,name,grade,school_name,parent_name,parent_phone,auth_key,active,created_at) VALUES
 (11,7,'테스트가','중1','가상중','테스트보호자가','010-0000-0011','local-only-student-11',1,NOW()),
 (12,7,'테스트나','중1','가상중',NULL,NULL,'local-only-student-12',1,NOW()),
 (13,7,'테스트다','중2','가상중',NULL,NULL,'local-only-student-13',1,NOW()),
 (14,7,'아주긴이름을가진테스트학생이름표시확인용','고1','이름이아주긴가상고등학교',NULL,NULL,'local-only-student-14',1,NOW()),
 (15,7,'테스트마','고1','가상고',NULL,NULL,'local-only-student-15',0,NOW()),
 (16,7,'동명나','중3','가상중',NULL,NULL,'local-only-student-16',1,NOW()),
 (17,7,'동명나','고2','가상고',NULL,NULL,'local-only-student-17',1,NOW())
 ON DUPLICATE KEY UPDATE active=VALUES(active);
INSERT INTO rea_classes(id,academy_id,name,description,teacher_id,weekdays,start_time,end_time,room,capacity,active,created_at) VALUES
 (201,7,'중1 문법반','문법 3단원 · 관계대명사',102,'MON,TUE,WED,THU,FRI,SAT,SUN','09:00:00','10:00:00','A강의실',10,1,NOW()),
 (202,7,'중2 독해반','지문 구조 읽기 · 긴 수업 설명이 두 줄을 넘을 때 줄임표로 잘리는지 확인하기 위한 테스트용 문장입니다',102,'MON,TUE,WED,THU,FRI,SAT,SUN','00:00:00','23:59:00','B강의실',12,1,NOW()),
 (203,7,'고1 내신반',NULL,101,'MON,TUE,WED,THU,FRI,SAT,SUN','23:58:00','23:59:00','C강의실',8,1,NOW()),
 (204,7,'시간 미정반',NULL,101,NULL,NULL,NULL,NULL,NULL,1,NOW())
 ON DUPLICATE KEY UPDATE active=1;
INSERT INTO rea_class_enrollments(id,academy_id,class_id,student_id,enrolled_at,active,created_at) VALUES
 (301,7,201,11,NOW(),1,NOW()),(302,7,201,12,NOW(),1,NOW()),(303,7,202,11,NOW(),1,NOW()),(304,7,202,13,NOW(),1,NOW()),(305,7,202,14,NOW(),1,NOW()),(306,7,203,1,NOW(),1,NOW()),(307,7,202,15,NOW(),1,NOW()),(308,7,201,16,NOW(),1,NOW()),(309,7,202,16,NOW(),1,NOW())
 ON DUPLICATE KEY UPDATE active=1;
INSERT INTO rea_consultations(id,academy_id,student_id,student_name,type,status,title,active,created_at) VALUES
 (401,7,12,'테스트나','INQUIRY','OPEN','보강 일정 문의(테스트)',1,NOW() - INTERVAL 2 DAY),
 (402,7,13,'테스트다','NEWS','OPEN','아이소식(테스트) — 홈 후속 상담에서 제외돼야 함',1,NOW() - INTERVAL 5 DAY),
 (403,7,11,'테스트가','INQUIRY','ANSWERED','답변 완료 문의(테스트)',1,NOW() - INTERVAL 1 DAY)
 ON DUPLICATE KEY UPDATE active=1;
INSERT INTO rea_consultation_messages(id,academy_id,consultation_id,sender_student_id,sender_name,content,created_at) VALUES
 (501,7,401,12,'테스트보호자','다음 주 보강 가능한가요? (테스트 문장)',NOW() - INTERVAL 2 DAY)
 ON DUPLICATE KEY UPDATE content=content;
