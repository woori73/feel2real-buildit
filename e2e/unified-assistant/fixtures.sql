-- ONLY: task-local unified-ai-mysql / f2r_unified_test + ac_unified_test.
USE ac_unified_test;
INSERT INTO rea_academies(id,name,active,created_at) VALUES(7,'새봄 영어학원',1,NOW()),(8,'다른 학원 격리검증',1,NOW()) ON DUPLICATE KEY UPDATE active=1;
INSERT INTO rea_members(id,academy_id,role,name,auth_key,active,failed_login_attempts,created_at) VALUES(101,7,'ACADEMY_OWNER','테스트 원장','local-only-owner-101',1,0,NOW()),(102,7,'TEACHER','테스트 강사','local-only-teacher-102',1,0,NOW()),(103,8,'ACADEMY_OWNER','다른 학원 원장','local-only-owner-103',1,0,NOW()) ON DUPLICATE KEY UPDATE active=1;
INSERT INTO rea_students(id,academy_id,name,grade,school_name,auth_key,active,created_at) VALUES(1,7,'김하늘','중2','새봄중','local-only-student-1',1,NOW()),(2,8,'다른학원 학생','중1','테스트중','local-only-student-2',1,NOW()) ON DUPLICATE KEY UPDATE active=1;
USE f2r_unified_test;
UPDATE users SET password=(SELECT pwd FROM (SELECT password AS pwd FROM users WHERE user_id=5) AS local_pwd) WHERE user_id IN(3,7);
INSERT INTO academy_easy_business_links(academy_id,business_id,created_at) VALUES(3,1,NOW()),(1,2,NOW()) ON DUPLICATE KEY UPDATE created_at=created_at;

UPDATE easy_businesses SET name='새봄 학원 장부 (테스트)' WHERE business_id=1;
UPDATE easy_businesses SET name='다른 사업체 격리검증' WHERE business_id=2;
