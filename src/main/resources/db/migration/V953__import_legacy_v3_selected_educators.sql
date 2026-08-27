-- V953: Legacy V3 VERIFIED 43명 → chart_candidates import (PR-B)
--   · 최초 BuildIt Chart(서울 auto 조사 + 강남/부산 수동)에서 선정된 43명을 초기 공개 데이터로 연결.
--   · 전화·이메일·상세주소 등 개인정보는 제외한다(스캔·치환).
--   · Legacy placeholder Score(대부분 2.0)는 저장하지 않는다. 현행 BuildIt Score 는 서버가 재계산한다.
--   · legacy_source 로 표시해 현행 강화 Hard Gate(교육철학·커리큘럼)는 grandfather 처리하되,
--     상세 카드는 확보된 정보(선정 이유·활동 근거)까지만 노출한다(없는 내용 생성 금지).
--   · 재실행 안전: person_key 중복 시 IGNORE. 이미 조사된 zone 도 그대로 승계(지역 상태는 V952).

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-seocho', '문준영|문준영&커넥트영어학원|서울특별시|서초구', '문준영', '원장', '문준영&커넥트영어학원', '서울 서초구 방배동', '서울특별시', '서초구', '글', '"2026년 상문고 1학년 1학기 중간고사 압도적 적중률!!!" 글을 통해 상문고 영어 내신의 출제 경향(객관식 및 서술형)을 분석하고, 자체 제작한 분석 교재와 실제 시험지의 일치율을 비교하며 내신 대비 전략을 설명함.', '방배동 지역에서 서울고, 상문고 등 인근 고등학교 영어 내신을 전문으로 직접 지도하며, 블로그를 통해 구체적인 시험 분석 및 적중 사례를 공유하는 교육자임.', '방배동 지역에서 서울고, 상문고 등 인근 고등학교 영어 내신을 전문으로 직접 지도하며, 블로그를 통해 구체적인 시험 분석 및 적중 사례를 공유하는 교육자임.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 4, "v3CandidateId": 2, "v3RegionKey": "seoul-seocho", "v3Region": "서울 서초구 방배동", "v3Score": 23.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHnICs8VJwQZ4uxFKb8Pab0_B7hus5ueJWu4LM0gSFft86ao1Jk1EaYpUNXujKaYz9Uy5iUhf6-q3mBCBYXLqQakNH1K6bGDM7BHdQ5sTn7elQ24aPHuEhUeDHppufk0_xtYH0RyYy9L7NVdyLI4qsdxjb2dXRvtoctJ5dvl2gThrz3ZZ3B8FT_398gjNIpcw1wb9cubmBBVCEl17lLJ0NIZ7q9vKzdSCM4rg1Z3elecw_C1NKBNUluJHIxDpHbJFUTjbJRhYRXUA==', '0f55bb14192748f159d5c5ee1109ed13b8f85241be80b61a25512522e31fca23', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '"2026년 상문고 1학년 1학기 중간고사 압도적 적중률!!!" 글을 통해 상문고 영어 내신의 출제 경향(객관식 및 서술형)을 분석하고, 자체 제작한 분석 교재와 실제 시험지의 일치율을 비교하며 내신 대비 전략을 설명함.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHnICs8VJwQZ4uxFKb8Pab0_B7hus5ueJWu4LM0gSFft86ao1Jk1EaYpUNXujKaYz9Uy5iUhf6-q3mBCBYXLqQakNH1K6bGDM7BHdQ5sTn7elQ24aPHuEhUeDHppufk0_xtYH0RyYy9L7NVdyLI4qsdxjb2dXRvtoctJ5dvl2gThrz3ZZ3B8FT_398gjNIpcw1wb9cubmBBVCEl17lLJ0NIZ7q9vKzdSCM4rg1Z3elecw_C1NKBNUluJHIxDpHbJFUTjbJRhYRXUA==', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '문준영|문준영&커넥트영어학원|서울특별시|서초구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-seocho', '이광노|케이앤(kn)영어학원|서울특별시|서초구', '이광노', '대표', '케이앤(KN)영어학원', '서울 서초구 반포동', '서울특별시', '서초구', '글', '"2026 고1 영어, 설계가 결과를 만듭니다" 글을 통해 반포고, 상문고, 서초고 등 서초 지역 고등학교의 내신 시험 특징(지문 변형 및 서술형 대비)을 분석하고, 내신과 수능을 유기적으로 연결하는 ''수업+활동+코칭'' 학습법을 제시함.', '반포동에서 소수정예로 학생들을 직접 지도하며, 블로그를 통해 서초 지역 고교 영어 내신 분석 및 메타인지 학습 활동의 중요성을 담은 교육 콘텐츠를 작성함.', '반포동에서 소수정예로 학생들을 직접 지도하며, 블로그를 통해 서초 지역 고교 영어 내신 분석 및 메타인지 학습 활동의 중요성을 담은 교육 콘텐츠를 작성함.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 3, "v3CandidateId": 3, "v3RegionKey": "seoul-seocho", "v3Region": "서울 서초구 반포동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQF2FzkYMUEZ-iiFYUDo89WEH_nRUcsOdGadE6QeV6GpimX8-0i4a8b3TlwishliMtbJAYWtQnBah-mdz0U3f8f__Acj5Lp8jJJ49XWaOTHlhKDscXsFowVYiTA4gJtyTOcRnB_uVtvrvwbr', 'd47f8ae535a8a4763901e59ef49081ed81058fe79eb9ebd7e35e7d8db8546ab1', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '"2026 고1 영어, 설계가 결과를 만듭니다" 글을 통해 반포고, 상문고, 서초고 등 서초 지역 고등학교의 내신 시험 특징(지문 변형 및 서술형 대비)을 분석하고, 내신과 수능을 유기적으로 연결하는 ''수업+활동+코칭'' 학습법을 제시함.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQF2FzkYMUEZ-iiFYUDo89WEH_nRUcsOdGadE6QeV6GpimX8-0i4a8b3TlwishliMtbJAYWtQnBah-mdz0U3f8f__Acj5Lp8jJJ49XWaOTHlhKDscXsFowVYiTA4gJtyTOcRnB_uVtvrvwbr', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '이광노|케이앤(kn)영어학원|서울특별시|서초구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-seocho', '박정인|박정인영어교습소|서울특별시|서초구', '박정인', '원장', '박정인영어교습소', '서울 서초구 잠원동', '서울특별시', '서초구', '글', '"[세화여고1] 26년 1학기 중간고사 분석" 글을 통해 세화여고 영어 시험의 부교재 출제 비중(54% 이상)과 서술형 및 어휘의 까다로움을 분석하고, 단순 암기가 아닌 지문 포인트 중심 정리의 중요성을 강조함.', '잠원동에서 영어 교습소를 운영하며 세화여고, 반포고 등 인근 학교의 영어 내신을 직접 분석하고 지도하는 개인 교육자임.', '잠원동에서 영어 교습소를 운영하며 세화여고, 반포고 등 인근 학교의 영어 내신을 직접 분석하고 지도하는 개인 교육자임.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 2, "v3CandidateId": 4, "v3RegionKey": "seoul-seocho", "v3Region": "서울 서초구 잠원동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEWBscw-JCyMkM8Qo753yXRI9TNh7vwYHdxJ_Rouq7wXHYVCZTKMQj1vLkRFbfUTiL-DeX_9HddR7_ez9IbbbtiJaEqFKyAmHkJGKeW8wqglaGuc0bJrFfDYv2yc7rN4cZGny4ECgdoX5_wvkl6zCCOoBuo5sizTSrdKsob7MF3o5bHAbJWGSCgUAVNGsroRFumeKnoLIzH9LLodRw3xDPnHdQ2nzq72iyBvfbpYJckfsNw4ZQ=', '33966b0ef49ca7f990197559f3932ee2d47a6e807b0f69e6442e2171b9a8ca64', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '"[세화여고1] 26년 1학기 중간고사 분석" 글을 통해 세화여고 영어 시험의 부교재 출제 비중(54% 이상)과 서술형 및 어휘의 까다로움을 분석하고, 단순 암기가 아닌 지문 포인트 중심 정리의 중요성을 강조함.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEWBscw-JCyMkM8Qo753yXRI9TNh7vwYHdxJ_Rouq7wXHYVCZTKMQj1vLkRFbfUTiL-DeX_9HddR7_ez9IbbbtiJaEqFKyAmHkJGKeW8wqglaGuc0bJrFfDYv2yc7rN4cZGny4ECgdoX5_wvkl6zCCOoBuo5sizTSrdKsob7MF3o5bHAbJWGSCgUAVNGsroRFumeKnoLIzH9LLodRw3xDPnHdQ2nzq72iyBvfbpYJckfsNw4ZQ=', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '박정인|박정인영어교습소|서울특별시|서초구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-seocho', '오션|퍼스트영어학원|서울특별시|서초구', '오션', '대표', '퍼스트영어학원', '서울 서초구 방배동', '서울특별시', '서초구', 'YouTube', '"초중고 학부모님 꼭 보세요 | 카페 블로그 글 댓글의 심각성" 유튜브 영상을 통해 학부모들에게 올바른 교육 정보 선별법을 조언하고, 서울고, 상문고, 서초고 등 방배동 인근 고교의 수능 및 내신 영어 대비 전략을 제시함.', '방배동에서 수능 영어 직강 및 서울고, 상문고, 서초고 내신 대비를 직접 지도하며, 유튜브를 통해 교육 관련 조언을 제공하는 교육자임.', '방배동에서 수능 영어 직강 및 서울고, 상문고, 서초고 내신 대비를 직접 지도하며, 유튜브를 통해 교육 관련 조언을 제공하는 교육자임.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 1, "v3CandidateId": 6, "v3RegionKey": "seoul-seocho", "v3Region": "서울 서초구 방배동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQG4uyLlMekKI0KTqMVAb_EeNr0peZjFKgKblh8_M8hjjbq3y6oPDYasu7YTjNojF5MnjqvARLbHPTtYiEYvJZNmTMA4bVw6-FNdSz5LBYBn3mD_acW24ZEItOrZgwSwFjqX', '54c8718c8493d7450fd0189a3b4f292a009740c1e92b4a7c72807ab50b8a2d80', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, 'YouTube', '"초중고 학부모님 꼭 보세요 | 카페 블로그 글 댓글의 심각성" 유튜브 영상을 통해 학부모들에게 올바른 교육 정보 선별법을 조언하고, 서울고, 상문고, 서초고 등 방배동 인근 고교의 수능 및 내신 영어 대비 전략을 제시함.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQG4uyLlMekKI0KTqMVAb_EeNr0peZjFKgKblh8_M8hjjbq3y6oPDYasu7YTjNojF5MnjqvARLbHPTtYiEYvJZNmTMA4bVw6-FNdSz5LBYBn3mD_acW24ZEItOrZgwSwFjqX', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '오션|퍼스트영어학원|서울특별시|서초구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-songpa', '정수정|정수정영어학원|서울특별시|송파구', '정수정', '원장', '정수정영어학원', '서울 송파구 삼전동', '서울특별시', '송파구', '글', '선화예중, 정신여고 등 송파 및 인근 지역 학교의 영어 내신 시험 및 단어 교재 변화에 맞춘 학습 전략을 제시하고, 상위권 학생들을 위한 원장 직강 수업을 운영함.', '송파구 삼전동에서 초6부터 고3까지의 학생들을 대상으로 내신 및 수능 대비 원장 직강 수업을 직접 진행하는 교육자임.', '송파구 삼전동에서 초6부터 고3까지의 학생들을 대상으로 내신 및 수능 대비 원장 직강 수업을 직접 진행하는 교육자임.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 5, "v3CandidateId": 17, "v3RegionKey": "seoul-songpa", "v3Region": "서울 송파구 삼전동", "v3Score": 27.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFs3aLfbtlozg60sQ9_V9oTEF1T-ApqjbZYt8lQT_FmBJyWtJ37RV-onOJqVvMLRZEtX51ZVBVqJfB4-K01SXgjrXB2-8qpc8WxZqBGhxsE52ywvimfl_EO9aKqaEx1WPPsPKoWjfWK0NfihbmFAkiGxgEX3rEPIGcUDAOcFeQyBvYYb1PG1QVNMjxsFNv370Imvo1TcqIap859alY46CYD45WmGnY=', '19edf8aea76c5f69f4be8076b56b88a536492240086bdb3eac9eb7170005884f', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '선화예중, 정신여고 등 송파 및 인근 지역 학교의 영어 내신 시험 및 단어 교재 변화에 맞춘 학습 전략을 제시하고, 상위권 학생들을 위한 원장 직강 수업을 운영함.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFs3aLfbtlozg60sQ9_V9oTEF1T-ApqjbZYt8lQT_FmBJyWtJ37RV-onOJqVvMLRZEtX51ZVBVqJfB4-K01SXgjrXB2-8qpc8WxZqBGhxsE52ywvimfl_EO9aKqaEx1WPPsPKoWjfWK0NfihbmFAkiGxgEX3rEPIGcUDAOcFeQyBvYYb1PG1QVNMjxsFNv370Imvo1TcqIap859alY46CYD45WmGnY=', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '정수정|정수정영어학원|서울특별시|송파구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-gangdong', '김성진|피케이리더스영어학원|서울특별시|강동구', '김성진', '원장', '피케이리더스영어학원', '서울 강동구', '서울특별시', '강동구', '글', '강동구 고등학교들의 방대한 영어 내신 범위와 높은 난이도에 대처하기 위해, 중학교 때부터 서술형 영작을 완벽하게 대비할 수 있는 능력을 키워주는 고등 내신 전문 지도를 강조합니다.', '강동구 지역 고등학교의 영어 내신 출제 경향과 서술형 대비의 중요성을 분석하고, 이를 바탕으로 학생들을 지도하는 원장으로 확인되었습니다.', '강동구 지역 고등학교의 영어 내신 출제 경향과 서술형 대비의 중요성을 분석하고, 이를 바탕으로 학생들을 지도하는 원장으로 확인되었습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 7, "v3CandidateId": 36, "v3RegionKey": "seoul-gangdong", "v3Region": "서울 강동구", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEEioQR9CHVTrPX6YPoaxuDdfP4ODbzY-FpE3Ib8HgDVvTXUxH-VmPdMtO-HkOSw2xGWtMnEsUk0essMZl2a2ePIrQmENkUgKfVf7A6MrlJ_BfFV9uLPuwg9F-pPyZUOF0r4jl4TxXHW9KLBHmA1iVkV3lkfiwbZ4GvYuKBWD51TzGpoAS2ZiEIP4MdpErG1KG8Ecg=', 'a7cbcaccd7c1222c3f66ea1d5edd843261ae7b7dfc2bce4ea51e59f8bb24dbd0', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '강동구 고등학교들의 방대한 영어 내신 범위와 높은 난이도에 대처하기 위해, 중학교 때부터 서술형 영작을 완벽하게 대비할 수 있는 능력을 키워주는 고등 내신 전문 지도를 강조합니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEEioQR9CHVTrPX6YPoaxuDdfP4ODbzY-FpE3Ib8HgDVvTXUxH-VmPdMtO-HkOSw2xGWtMnEsUk0essMZl2a2ePIrQmENkUgKfVf7A6MrlJ_BfFV9uLPuwg9F-pPyZUOF0r4jl4TxXHW9KLBHmA1iVkV3lkfiwbZ4GvYuKBWD51TzGpoAS2ZiEIP4MdpErG1KG8Ecg=', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '김성진|피케이리더스영어학원|서울특별시|강동구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-gangdong', '권수진|뮤엠영어고덕점학원|서울특별시|강동구', '권수진', '원장', '뮤엠영어고덕점학원', '서울 강동구 고덕동', '서울특별시', '강동구', '글', '8년간의 초·중등 영어 강의 및 관리 노하우를 바탕으로, 초등 파닉스부터 중학 내신 대비까지 ''영어는 영어답게'' 가르치는 밀착 관리를 제공합니다.', '강동구 고덕동에서 초등 파닉스 및 중학 내신 대비를 직접 지도하는 원장으로 확인되었습니다.', '강동구 고덕동에서 초등 파닉스 및 중학 내신 대비를 직접 지도하는 원장으로 확인되었습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 6, "v3CandidateId": 37, "v3RegionKey": "seoul-gangdong", "v3Region": "서울 강동구 고덕동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEuotEm6URa60xGnvltBOs8KTrDzQY4tBflkF_FmrZ0eKS3fWUXVKrYJI8kttav_CPVwOcV-LxWZcQ3xIM55U1-TpKeMi62bmhHlduLyHGjXx4GMySZOJUV6LupA5WEYLfe8NZt0G-_cq5LSt7Kikd2OcKbzaHipAIG7q1Pv3jMZJztjm8KZmcTd-cqMt_GuZduLG2VXi1rT7gljh3jJqsS7PiDDzrcrM4AoB0f5kIfsUu-WvekIAygrCriVRM=', '2659c3b7b4cda44c07435e30b8328e0e32a043fb4b86af7e4ec6a80e969caeb1', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '8년간의 초·중등 영어 강의 및 관리 노하우를 바탕으로, 초등 파닉스부터 중학 내신 대비까지 ''영어는 영어답게'' 가르치는 밀착 관리를 제공합니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEuotEm6URa60xGnvltBOs8KTrDzQY4tBflkF_FmrZ0eKS3fWUXVKrYJI8kttav_CPVwOcV-LxWZcQ3xIM55U1-TpKeMi62bmhHlduLyHGjXx4GMySZOJUV6LupA5WEYLfe8NZt0G-_cq5LSt7Kikd2OcKbzaHipAIG7q1Pv3jMZJztjm8KZmcTd-cqMt_GuZduLG2VXi1rT7gljh3jJqsS7PiDDzrcrM4AoB0f5kIfsUu-WvekIAygrCriVRM=', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '권수진|뮤엠영어고덕점학원|서울특별시|강동구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-mapo', '손은일|애슐리영어학원|서울특별시|마포구', '손은일', '원장', '애슐리영어학원', '서울 마포구 신수동', '서울특별시', '마포구', '글', '기존 애슐리영어교습소에서 애슐리영어학원으로 확장 개원하며, 중등부 1학기 정규수업 시간표 및 문법/독해 중심의 체계적인 내신 및 수능 대비 커리큘럼을 운영함.', '마포구 신수동에서 중고등 영어 내신 및 수능을 전문으로 지도하며, 교습소에서 학원으로 확장 개원한 소식과 중등부 정규수업 커리큘럼을 직접 작성하여 발굴됨.', '마포구 신수동에서 중고등 영어 내신 및 수능을 전문으로 지도하며, 교습소에서 학원으로 확장 개원한 소식과 중등부 정규수업 커리큘럼을 직접 작성하여 발굴됨.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 8, "v3CandidateId": 41, "v3RegionKey": "seoul-mapo", "v3Region": "서울 마포구 신수동", "v3Score": 26.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHD-cpEXa1vyoBkM7zcwermaLEUFsV0w_D3c-1JpbGGPtV40RFYpSJZncA5pDeozPGicusaNUVE6xeNDFrYhPf4XQYJhFXuCkA5N3K0fGgdkExmwGXS1veP7pZEq_vtS6HpKJS81dPfh2V55lMrIpdj06Z0da54y6WbvkIMVCMDekiMp0hkOwFIBOAHgcY=', '53b29ffb0021b5f9c20266f2a987b3567459001fc6c381cd214ab9b87ad157ec', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '기존 애슐리영어교습소에서 애슐리영어학원으로 확장 개원하며, 중등부 1학기 정규수업 시간표 및 문법/독해 중심의 체계적인 내신 및 수능 대비 커리큘럼을 운영함.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHD-cpEXa1vyoBkM7zcwermaLEUFsV0w_D3c-1JpbGGPtV40RFYpSJZncA5pDeozPGicusaNUVE6xeNDFrYhPf4XQYJhFXuCkA5N3K0fGgdkExmwGXS1veP7pZEq_vtS6HpKJS81dPfh2V55lMrIpdj06Z0da54y6WbvkIMVCMDekiMp0hkOwFIBOAHgcY=', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '손은일|애슐리영어학원|서울특별시|마포구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-jongno', '김성연|키위북웜스|서울특별시|종로구', '김성연', '원장', '키위북웜스', '서울 종로구 신교동', '서울특별시', '종로구', '글', '고1 영어 내신 대비를 위해 직접 제작한 어법 변형 문제를 블로그에 공유하고, 소규모 멘토링형 수업 방식을 소개했습니다.', '종로구 신교동에서 소규모 영어 공부방을 운영하며, 블로그와 카카오톡 채널을 통해 직접 제작한 내신 대비 학습 자료를 배포하고 있어 후보로 선정했습니다.', '종로구 신교동에서 소규모 영어 공부방을 운영하며, 블로그와 카카오톡 채널을 통해 직접 제작한 내신 대비 학습 자료를 배포하고 있어 후보로 선정했습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 9, "v3CandidateId": 45, "v3RegionKey": "seoul-jongno", "v3Region": "서울 종로구 신교동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQG6UO11GAoNHMGWHudqT2bFclhwmHqgtd4HqDkmaxBaMcgN3lC54P_R8mSKePaB0kfnzfAvvMOwGm7bx40cLk6mq1tRem0d0ScZELaVJYgwPAI=', 'd466c5ebf26ddaf1dae8dbc80769d4c6adda300c37316c637f3d7efaf45c1873', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '고1 영어 내신 대비를 위해 직접 제작한 어법 변형 문제를 블로그에 공유하고, 소규모 멘토링형 수업 방식을 소개했습니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQG6UO11GAoNHMGWHudqT2bFclhwmHqgtd4HqDkmaxBaMcgN3lC54P_R8mSKePaB0kfnzfAvvMOwGm7bx40cLk6mq1tRem0d0ScZELaVJYgwPAI=', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '김성연|키위북웜스|서울특별시|종로구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-seongdong', '김정아|경일해법영어교습소|서울특별시|성동구', '김정아', '원장', '경일해법영어교습소', '서울 성동구 성수동1가', '서울특별시', '성동구', '글', '연세대학교 영어영문학과 학사, 석사, 박사 학위를 소지하고 있으며, 성수동에서 초등 및 중등(현 초6~중2) 학생들을 대상으로 겨울방학 소수정예 집중반 및 영어 수업을 직강으로 지도하고 있습니다.', '성수동 경일초 옆에서 연세대 영문학 박사 학위를 바탕으로 직접 초중등 영어를 지도하는 원장으로 확인되었습니다.', '성수동 경일초 옆에서 연세대 영문학 박사 학위를 바탕으로 직접 초중등 영어를 지도하는 원장으로 확인되었습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 10, "v3CandidateId": 52, "v3RegionKey": "seoul-seongdong", "v3Region": "서울 성동구 성수동1가", "v3Score": 17.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEvdtWkEXsvci9x4i9IwnLqSmXLXg-gk6wQC1kkACnaawbeoRnlc5IfPN67XQi4aIRKuO6IvpqxodnbcGYdpkX5EumdVTvM_-WdgUG97D7msZ6YT7-Yu2v6Yt9rC9qBeR9zVFWlDHqxMii-rbRxHnSY_Rg4w1wdy_EzMNvPK9JYr6m5btUVSnGVxFnrir5qSEIsUhxtB9pEVSOeV_Lh4u9zYocz-WmfSe5bSZT-VQyjJDHvAWO0IzX17SFOCVM=', 'd1b38c087597b91ad90cae090f2a221f9922097bb88733e8c6e0b178b132f617', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '연세대학교 영어영문학과 학사, 석사, 박사 학위를 소지하고 있으며, 성수동에서 초등 및 중등(현 초6~중2) 학생들을 대상으로 겨울방학 소수정예 집중반 및 영어 수업을 직강으로 지도하고 있습니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEvdtWkEXsvci9x4i9IwnLqSmXLXg-gk6wQC1kkACnaawbeoRnlc5IfPN67XQi4aIRKuO6IvpqxodnbcGYdpkX5EumdVTvM_-WdgUG97D7msZ6YT7-Yu2v6Yt9rC9qBeR9zVFWlDHqxMii-rbRxHnSY_Rg4w1wdy_EzMNvPK9JYr6m5btUVSnGVxFnrir5qSEIsUhxtB9pEVSOeV_Lh4u9zYocz-WmfSe5bSZT-VQyjJDHvAWO0IzX17SFOCVM=', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '김정아|경일해법영어교습소|서울특별시|성동구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-dongdaemun', '권아영|오케이영어교습소|서울특별시|동대문구', '권아영', '원장', '오케이영어교습소', '서울 동대문구 전농동', '서울특별시', '동대문구', '글', '동대문구 전농동 일대 주요 중학교(동대문중, 숭인중, 전농중, 전일중) 및 고등학교(휘경여고)의 영어 내신 출제 경향을 완벽 분석하고, 4단계 빌드업을 통한 내신 대비반을 운영하는 교육 활동을 소개함.', '동대문구 전농동 지역의 중고등 영어 내신 분석 글을 직접 작성하고 소수정예로 직강하는 원장으로 확인됨.', '동대문구 전농동 지역의 중고등 영어 내신 분석 글을 직접 작성하고 소수정예로 직강하는 원장으로 확인됨.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 14, "v3CandidateId": 53, "v3RegionKey": "seoul-dongdaemun", "v3Region": "서울 동대문구 전농동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://blog.naver.com/okkoenglish/224210344643', '64ca2f9f6cb194070e849bf603bd2bf8118e39101516fd65d4e9c21c4e040060', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '동대문구 전농동 일대 주요 중학교(동대문중, 숭인중, 전농중, 전일중) 및 고등학교(휘경여고)의 영어 내신 출제 경향을 완벽 분석하고, 4단계 빌드업을 통한 내신 대비반을 운영하는 교육 활동을 소개함.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://blog.naver.com/okkoenglish/224210344643', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '권아영|오케이영어교습소|서울특별시|동대문구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-dongdaemun', '손형주|hj영어학원|서울특별시|동대문구', '손형주', '원장', 'HJ영어학원', '서울 동대문구 전농동', '서울특별시', '동대문구', '글', '동대문구 6개교(휘경여고, 해성여고, 경희여고, 청량고, 혜원여고, 동대부고 등)의 영어 내신 1등급을 목표로 하는 ''HJ-LOGIC 심화논리'' 분석과 변형 문제 대응 전략을 제시함.', '동대문구 지역 고등학교의 영어 내신 기출 경향을 분석하고 1등급 설계관을 직접 운영하며 강의하는 원장으로 확인됨.', '동대문구 지역 고등학교의 영어 내신 기출 경향을 분석하고 1등급 설계관을 직접 운영하며 강의하는 원장으로 확인됨.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 13, "v3CandidateId": 54, "v3RegionKey": "seoul-dongdaemun", "v3Region": "서울 동대문구 전농동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://m.blog.naver.com/hyungju19/223989319884', '0c0efca012df87438320d95ea9fd40d6bb5a09022f72ce2165c9431748600b35', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '동대문구 6개교(휘경여고, 해성여고, 경희여고, 청량고, 혜원여고, 동대부고 등)의 영어 내신 1등급을 목표로 하는 ''HJ-LOGIC 심화논리'' 분석과 변형 문제 대응 전략을 제시함.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://m.blog.naver.com/hyungju19/223989319884', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '손형주|hj영어학원|서울특별시|동대문구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-dongdaemun', '김현진|주노영어학원|서울특별시|동대문구', '김현진', '원장', '주노영어학원', '서울 동대문구 장안동', '서울특별시', '동대문구', '글', '동대부중, 장평중, 전동중 등 동대문구 지역 중학교의 영어 기출문제 유형별 출제 빈도수와 난이도를 분석하고, 1:1 밀착 관리 시스템을 통한 내신 및 수능 대비법을 공유함.', '장안동에서 14년간 중고등 영어 내신 및 수능 대비를 위해 직접 기출 분석 글을 작성하고 지도하는 원장으로 확인됨.', '장안동에서 14년간 중고등 영어 내신 및 수능 대비를 위해 직접 기출 분석 글을 작성하고 지도하는 원장으로 확인됨.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 12, "v3CandidateId": 55, "v3RegionKey": "seoul-dongdaemun", "v3Region": "서울 동대문구 장안동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://blog.naver.com/junoenglish', 'a94612ddd5b784174ad2b39acfc533606efcf32ad82c2fee7e6bdb7a50720734', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '동대부중, 장평중, 전동중 등 동대문구 지역 중학교의 영어 기출문제 유형별 출제 빈도수와 난이도를 분석하고, 1:1 밀착 관리 시스템을 통한 내신 및 수능 대비법을 공유함.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://blog.naver.com/junoenglish', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '김현진|주노영어학원|서울특별시|동대문구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-dongdaemun', '전수진|절대영어학원|서울특별시|동대문구', '전수진', '원장', '절대영어학원', '서울 동대문구 장안동', '서울특별시', '동대문구', '글', '동대부중, 청량고, 해성여고 등 동대문구 지역 학교의 영어 기출문제를 직접 분석하여 유형별 출제 비중(어법, 내용 일치 등)을 정리하고, 1:1 피드백 기반의 코칭 수업을 소개함.', '유웨이 수능 분석 위원 출신으로, 동대문구 장안동에서 중고등 내신 및 수능 영어 기출 분석 글을 직접 작성하고 강의하는 원장으로 확인됨.', '유웨이 수능 분석 위원 출신으로, 동대문구 장안동에서 중고등 내신 및 수능 영어 기출 분석 글을 직접 작성하고 강의하는 원장으로 확인됨.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 11, "v3CandidateId": 56, "v3RegionKey": "seoul-dongdaemun", "v3Region": "서울 동대문구 장안동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://blog.naver.com/jd___english', 'd202562a87c75bfada5d7c6ae16507e9e7d262d318c6710d909c3e3b52bb9f15', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '동대부중, 청량고, 해성여고 등 동대문구 지역 학교의 영어 기출문제를 직접 분석하여 유형별 출제 비중(어법, 내용 일치 등)을 정리하고, 1:1 피드백 기반의 코칭 수업을 소개함.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://blog.naver.com/jd___english', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '전수진|절대영어학원|서울특별시|동대문구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-seongbuk', '민정빈|jbm영어학원|서울특별시|성북구', '민정빈', '원장', 'JBM영어학원', '서울 성북구 아리랑로', '서울특별시', '성북구', '글', '단순히 문제풀이에 집중하는 기존 방식에서 벗어나 아이들이 스스로 영어를 즐기고 삶에 연결시키도록 이끄는 교육 철학을 소개하는 인터뷰 기사입니다 [4.1.2].', '성북구에서 직접 학생들을 지도하며 영어를 통한 성장과 자존감 회복을 돕는 교육자로서 인터뷰를 통해 발굴되었습니다.', '성북구에서 직접 학생들을 지도하며 영어를 통한 성장과 자존감 회복을 돕는 교육자로서 인터뷰를 통해 발굴되었습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 17, "v3CandidateId": 58, "v3RegionKey": "seoul-seongbuk", "v3Region": "서울 성북구 아리랑로", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGN9boZ6tHhhjEcZH_27pHdFFqi-TxerbqtA3I_iraCaB1vTXQUqiqFoeRTtdMeqjhB3VMZvY5CFus_PEKiQXh2I3b0N9dVe2XykawDoMUP7A==', 'a3b985b076cc44c52a84973880e5184f9b86997b6be284b2861b3ec575443695', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '단순히 문제풀이에 집중하는 기존 방식에서 벗어나 아이들이 스스로 영어를 즐기고 삶에 연결시키도록 이끄는 교육 철학을 소개하는 인터뷰 기사입니다 [4.1.2].', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGN9boZ6tHhhjEcZH_27pHdFFqi-TxerbqtA3I_iraCaB1vTXQUqiqFoeRTtdMeqjhB3VMZvY5CFus_PEKiQXh2I3b0N9dVe2XykawDoMUP7A==', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '민정빈|jbm영어학원|서울특별시|성북구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-seongbuk', '이윤정|윤정영어학원|서울특별시|성북구', '이윤정', '원장', '윤정영어학원', '서울 성북구 동소문동', '서울특별시', '성북구', '글', '성북구에서 12년째 학생들을 지도하며 초6부터 고3까지 내신 관리와 수능 대비를 원장 직강으로 진행하는 체계적인 커리큘럼을 소개합니다.', '성북구 지역에서 오랫동안 직접 고등부 내신(한성여고, 동성고, 경동고, 홍익고, 용문고 등)을 밀착 지도해 온 교육자로 확인되었습니다.', '성북구 지역에서 오랫동안 직접 고등부 내신(한성여고, 동성고, 경동고, 홍익고, 용문고 등)을 밀착 지도해 온 교육자로 확인되었습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 16, "v3CandidateId": 59, "v3RegionKey": "seoul-seongbuk", "v3Region": "서울 성북구 동소문동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHH2c4ss1TyiHZ7pFBSeSELpjpuIBh-YNPWD42ctaT_aOPEkPKEknvnSzOgcnnpx_40TOx_YPTl0MuwgsbdsyCoq7_pzxi1GDApngKxZUbgJAu2NHRj3jM8h_5AVQOLYGbVSl9w3gc36JxJlMgZKGgp24LFG0dCw6ZSQK_cRAUPwaIbRrfb2Fr8X41lGeUeKCODLvTFgt2BL7E3TaZx-FBYPw==', '95941e3cae6d435f275d7bc15c08d95d41dc8d36ad6c0d7c0aff1776bdd49b12', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '성북구에서 12년째 학생들을 지도하며 초6부터 고3까지 내신 관리와 수능 대비를 원장 직강으로 진행하는 체계적인 커리큘럼을 소개합니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHH2c4ss1TyiHZ7pFBSeSELpjpuIBh-YNPWD42ctaT_aOPEkPKEknvnSzOgcnnpx_40TOx_YPTl0MuwgsbdsyCoq7_pzxi1GDApngKxZUbgJAu2NHRj3jM8h_5AVQOLYGbVSl9w3gc36JxJlMgZKGgp24LFG0dCw6ZSQK_cRAUPwaIbRrfb2Fr8X41lGeUeKCODLvTFgt2BL7E3TaZx-FBYPw==', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '이윤정|윤정영어학원|서울특별시|성북구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-seongbuk', '신민정|라이크영어학원|서울특별시|성북구', '신민정', '대표', '라이크영어학원', '서울 성북구 종암동', '서울특별시', '성북구', '글', '언어로서의 영어 접근법과 초·중등 대상 파닉스, 필수 어휘 암기, 문법 개념 완성 등 맞춤형 커리큘럼을 소개하는 인터뷰 기사입니다.', '성북구에서 초·중등 영어 전문 학원을 직접 운영하며 교육 철학과 커리큘럼을 밝힌 실제 교육자로 확인되었습니다.', '성북구에서 초·중등 영어 전문 학원을 직접 운영하며 교육 철학과 커리큘럼을 밝힌 실제 교육자로 확인되었습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 15, "v3CandidateId": 61, "v3RegionKey": "seoul-seongbuk", "v3Region": "서울 성북구 종암동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFvRHgO12gXvknQeF-QjoS-xd7fZ1mt2-rHO_XmndSDTdyyxbTtPvPpPXcplW7dCOqsCPOnl_ZV_THe3sF-T1OFdmMhBl17uF6yDboR-PzxrEeob487IFg=', '7bbb717e57a98ae1068216952cfa969130a79cb718d4e62979cd33f95347a88f', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '언어로서의 영어 접근법과 초·중등 대상 파닉스, 필수 어휘 암기, 문법 개념 완성 등 맞춤형 커리큘럼을 소개하는 인터뷰 기사입니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFvRHgO12gXvknQeF-QjoS-xd7fZ1mt2-rHO_XmndSDTdyyxbTtPvPpPXcplW7dCOqsCPOnl_ZV_THe3sF-T1OFdmMhBl17uF6yDboR-PzxrEeob487IFg=', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '신민정|라이크영어학원|서울특별시|성북구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-gangbuk', '박종훈|블랙레벨영어교습소|서울특별시|강북구', '박종훈', '대표', '블랙레벨영어교습소', '서울 강북구 미아동', '서울특별시', '강북구', '글', '목동 학원가 출신의 원장으로, ''영어식 사고를 기르면 원어민처럼 생각하고 말할 수 있다''는 철학 하에 영작 특화 수업 및 능동형 발표 수업을 직접 지도합니다.', '강북구 미아동에서 소수정예 과외식 수업을 운영하며, 영작 특화 수업과 영어식 사고 학습법을 강조하는 인터뷰 기사를 통해 발굴했습니다.', '강북구 미아동에서 소수정예 과외식 수업을 운영하며, 영작 특화 수업과 영어식 사고 학습법을 강조하는 인터뷰 기사를 통해 발굴했습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 18, "v3CandidateId": 67, "v3RegionKey": "seoul-gangbuk", "v3Region": "서울 강북구 미아동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQE5AdDxvCZ4-G0HH37VkLcmcOgajEd1vUhhqO0-BPgavOzm0qqNNPIPmCkDRN7JOy6CdgfBALbC59LtsXHNyd2qG-rEGFnmnMGAmls4SK0CjqVeO-32SsM=', '34f96df731de54d53c7cfb1680ac903ce848aec1573659e1872cedcc7c82e393', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '목동 학원가 출신의 원장으로, ''영어식 사고를 기르면 원어민처럼 생각하고 말할 수 있다''는 철학 하에 영작 특화 수업 및 능동형 발표 수업을 직접 지도합니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQE5AdDxvCZ4-G0HH37VkLcmcOgajEd1vUhhqO0-BPgavOzm0qqNNPIPmCkDRN7JOy6CdgfBALbC59LtsXHNyd2qG-rEGFnmnMGAmls4SK0CjqVeO-32SsM=', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '박종훈|블랙레벨영어교습소|서울특별시|강북구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-dobong', '황진하|강한영어아는영어황쌤영어학원|서울특별시|도봉구', '황진하', '대표', '강한영어아는영어황쌤영어학원', '서울 도봉구 방학동', '서울특별시', '도봉구', '글', '창동 및 방학동 지역에서 초등부 기초 영어부터 중등부 학교별 내신 영어 대비, 고등부 수능 영어까지 체계적으로 지도하는 맞춤형 교육 프로그램을 소개했습니다.', '도봉구 방학동 및 창동 지역에서 내신과 수능 대비를 위한 체계적인 영어 교육 프로그램을 운영하는 대표자로 확인되어 후보로 선정했습니다.', '도봉구 방학동 및 창동 지역에서 내신과 수능 대비를 위한 체계적인 영어 교육 프로그램을 운영하는 대표자로 확인되어 후보로 선정했습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 21, "v3CandidateId": 76, "v3RegionKey": "seoul-dobong", "v3Region": "서울 도봉구 방학동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFBFFngzYe958qc7bDPHYIKL8hlT_D99bNvIRYU730wOQTOzmQXmvT-iT4s4A4B4rhkL0U8Gsq_oMYDeezsKl8EqU46aDkUTXBaDeElf5sXzKkQS9eu5L8Ob5AmCzgJO09Rk71bnmA0eqwGTgnPOuL8IVTahFwxkEWK4zTavd78m1U7_MlHzLUrepHDt2YNOgsTSQZjl0sGn1z0HSonx4hFUEk8yjUGiXKZmf0H9aEB5CrNrTW_XO7sWrs_7QTHaPUsotd53e4qmWJol5HVexT4dYdTbT-IN-QWJAmi1IBNdm5eeH8_K48=', '2e0adeb1492e656b160ed79e29713ae447d307a4766fc7f890da08de451b8f24', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '창동 및 방학동 지역에서 초등부 기초 영어부터 중등부 학교별 내신 영어 대비, 고등부 수능 영어까지 체계적으로 지도하는 맞춤형 교육 프로그램을 소개했습니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFBFFngzYe958qc7bDPHYIKL8hlT_D99bNvIRYU730wOQTOzmQXmvT-iT4s4A4B4rhkL0U8Gsq_oMYDeezsKl8EqU46aDkUTXBaDeElf5sXzKkQS9eu5L8Ob5AmCzgJO09Rk71bnmA0eqwGTgnPOuL8IVTahFwxkEWK4zTavd78m1U7_MlHzLUrepHDt2YNOgsTSQZjl0sGn1z0HSonx4hFUEk8yjUGiXKZmf0H9aEB5CrNrTW_XO7sWrs_7QTHaPUsotd53e4qmWJol5HVexT4dYdTbT-IN-QWJAmi1IBNdm5eeH8_K48=', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '황진하|강한영어아는영어황쌤영어학원|서울특별시|도봉구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-dobong', '이지현|일리영어학원|서울특별시|도봉구', '이지현', '원장', '일리영어학원', '서울 도봉구 쌍문동', '서울특별시', '도봉구', '글', '선덕고, 창동고, 효문고, 정의여고 등 도봉구 지역 고등학교 학생들을 대상으로 한 예비고1반 내신 성과와 유기적인 학습 관리 커리큘럼을 소개했습니다.', '도봉구 쌍문동 지역 고등학교들의 영어 내신 성과 및 유기적 학습 관리 콘텐츠를 작성한 학원의 대표자로 확인되어 후보로 선정했습니다.', '도봉구 쌍문동 지역 고등학교들의 영어 내신 성과 및 유기적 학습 관리 콘텐츠를 작성한 학원의 대표자로 확인되어 후보로 선정했습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 20, "v3CandidateId": 77, "v3RegionKey": "seoul-dobong", "v3Region": "서울 도봉구 쌍문동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://blog.naver.com/ili_english', '96efb7b8cb3862be7476f1842d41ee1055558dadc6df07f6e50d9a659dc3037c', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '선덕고, 창동고, 효문고, 정의여고 등 도봉구 지역 고등학교 학생들을 대상으로 한 예비고1반 내신 성과와 유기적인 학습 관리 커리큘럼을 소개했습니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://blog.naver.com/ili_english', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '이지현|일리영어학원|서울특별시|도봉구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-dobong', '서쌤|서쌤의영어독서교실영어교습소|서울특별시|도봉구', '서쌤', '원장', '서쌤의영어독서교실영어교습소', '서울 도봉구 쌍문동', '서울특별시', '도봉구', '글', '백운초등학교 학생들을 대상으로 넬트(NELT) 영어 테스트 결과를 분석하고, 반반노트와 연상력 그림장을 활용한 낭독 및 영어 원서 읽기 지도법을 공유했습니다.', '도봉구 쌍문동에서 초등학생 대상 영어 원서 읽기 및 넬트 테스트 분석 등 구체적인 교육활동 콘텐츠를 작성한 원장이나, 실명이 명확히 확인되지 않아 모호함(isAmbiguous: true)으로 표시하여 후보로 선정했습니다.', '도봉구 쌍문동에서 초등학생 대상 영어 원서 읽기 및 넬트 테스트 분석 등 구체적인 교육활동 콘텐츠를 작성한 원장이나, 실명이 명확히 확인되지 않아 모호함(isAmbiguous: true)으로 표시하여 후보로 선정했습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 19, "v3CandidateId": 78, "v3RegionKey": "seoul-dobong", "v3Region": "서울 도봉구 쌍문동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://blog.naver.com/symk79', 'b84fe8771b5881c005a2a24ebc614108b708f933d8322047b3bd528f3713ef69', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '백운초등학교 학생들을 대상으로 넬트(NELT) 영어 테스트 결과를 분석하고, 반반노트와 연상력 그림장을 활용한 낭독 및 영어 원서 읽기 지도법을 공유했습니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://blog.naver.com/symk79', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '서쌤|서쌤의영어독서교실영어교습소|서울특별시|도봉구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-nowon', '김지민|김지민영어학원|서울특별시|노원구', '김지민', '원장', '김지민영어학원', '서울 노원구 중계동', '서울특별시', '노원구', '글', '중계중, 상명중 등 노원구 중계동 지역 중학교의 영어 내신 시험을 심층 분석하고, 1:1 허점보완코칭 및 내신 분석 설명회를 직접 진행합니다.', '노원구 중계동 지역 중학교의 영어 내신 시험을 심층 분석하고, 1:1 허점보완코칭 및 내신 분석 설명회를 직접 진행하는 지역 영어 교육자입니다.', '노원구 중계동 지역 중학교의 영어 내신 시험을 심층 분석하고, 1:1 허점보완코칭 및 내신 분석 설명회를 직접 진행하는 지역 영어 교육자입니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 24, "v3CandidateId": 79, "v3RegionKey": "seoul-nowon", "v3Region": "서울 노원구 중계동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://blog.naver.com/kimjiminenglish/223849189138', 'ed6d55798a8b50bc16ab04756411b2e329400d8127f9738008c8193b3db9d81b', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '중계중, 상명중 등 노원구 중계동 지역 중학교의 영어 내신 시험을 심층 분석하고, 1:1 허점보완코칭 및 내신 분석 설명회를 직접 진행합니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://blog.naver.com/kimjiminenglish/223849189138', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '김지민|김지민영어학원|서울특별시|노원구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-nowon', '김동길|김동길영어학원|서울특별시|노원구', '김동길', '원장', '김동길영어학원', '서울 노원구 중계동', '서울특별시', '노원구', '글', '혜성여고 1학년 영어 내신 기출 및 적중 문제를 분석하고, 중계동 지역 고등부 영어 내신 분석 및 기고 활동을 진행합니다.', '혜성여고 등 노원구 지역 고등학교의 영어 내신 기출 및 적중 문제를 분석하고, 블로그를 통해 내신 분석 자료를 제공하는 지역 영어 교육자입니다.', '혜성여고 등 노원구 지역 고등학교의 영어 내신 기출 및 적중 문제를 분석하고, 블로그를 통해 내신 분석 자료를 제공하는 지역 영어 교육자입니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 23, "v3CandidateId": 80, "v3RegionKey": "seoul-nowon", "v3Region": "서울 노원구 중계동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://blog.naver.com/kdgfull', 'c9afe95c0bbf79209c7f5de08780cd2eb5325aaf0301774c6f9871c045cd9ee9', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '혜성여고 1학년 영어 내신 기출 및 적중 문제를 분석하고, 중계동 지역 고등부 영어 내신 분석 및 기고 활동을 진행합니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://blog.naver.com/kdgfull', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '김동길|김동길영어학원|서울특별시|노원구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-nowon', '전종삼|js뉴욕어학원|서울특별시|노원구', '전종삼', '원장', 'JS뉴욕어학원', '서울 노원구 중계동', '서울특별시', '노원구', '글', '불암중 등 근처 중학교별 시험유형 분석과 대책을 제시하고, 영문법 특강 및 토요 코칭수업을 통해 학생별 맞춤 관리를 진행합니다.', '노원구 중계동에서 중학교별 시험유형 분석과 대책을 제시하며, 영문법 특강 및 토요 코칭수업을 통해 학생별 맞춤 관리를 직접 진행하는 지역 영어 교육자입니다.', '노원구 중계동에서 중학교별 시험유형 분석과 대책을 제시하며, 영문법 특강 및 토요 코칭수업을 통해 학생별 맞춤 관리를 직접 진행하는 지역 영어 교육자입니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 22, "v3CandidateId": 81, "v3RegionKey": "seoul-nowon", "v3Region": "서울 노원구 중계동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://blog.naver.com/bell325/224262861822', 'a1fe3d3912eab134cbc7627f74bff45d19fbdd1d67e392632d7a11b81ea3968e', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '불암중 등 근처 중학교별 시험유형 분석과 대책을 제시하고, 영문법 특강 및 토요 코칭수업을 통해 학생별 맞춤 관리를 진행합니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://blog.naver.com/bell325/224262861822', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '전종삼|js뉴욕어학원|서울특별시|노원구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-eunpyeong', '이수연|더퍼스트펭귄영어교습소|서울특별시|은평구', '이수연', '원장', '더퍼스트펭귄 영어교습소', '서울 은평구 진관동', '서울특별시', '은평구', '인터뷰', '중등 문법을 고등 내신 독해의 도구로 전환하고, 고등부 내신 지문 7단계 분석법을 통해 학생이 출제자의 시각에서 지문을 재조립하도록 훈련하며, 공부 자립을 돕는 교육 철학을 실천함.', '은평구 구파발 지역에서 ''''공부 자립''''을 돕는 영어 교육 철학과 중고등 내신 지문 분석법을 인터뷰를 통해 상세히 밝히고 직접 지도하는 원장으로 확인됨.', '은평구 구파발 지역에서 ''공부 자립''을 돕는 영어 교육 철학과 중고등 내신 지문 분석법을 인터뷰를 통해 상세히 밝히고 직접 지도하는 원장으로 확인됨.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 27, "v3CandidateId": 86, "v3RegionKey": "seoul-eunpyeong", "v3Region": "서울 은평구 진관동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHbCIlqweIrYglrP1JGlSe5PYGr6RaT0VAjRX5SZrE_GDxPDez_Q3NbFf3QiSGdz5xJ2L6J-aC4z6dZuwmYvfzkeYEj6XLZrnaVRk3izf5i6f8=', '3dbf54a792a13e488519fe0823c935ecea10b9f4928b867f02ecd2db9de23bdc', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '인터뷰', '중등 문법을 고등 내신 독해의 도구로 전환하고, 고등부 내신 지문 7단계 분석법을 통해 학생이 출제자의 시각에서 지문을 재조립하도록 훈련하며, 공부 자립을 돕는 교육 철학을 실천함.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHbCIlqweIrYglrP1JGlSe5PYGr6RaT0VAjRX5SZrE_GDxPDez_Q3NbFf3QiSGdz5xJ2L6J-aC4z6dZuwmYvfzkeYEj6XLZrnaVRk3izf5i6f8=', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '이수연|더퍼스트펭귄영어교습소|서울특별시|은평구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-eunpyeong', '이영인|프라임온영어교습소|서울특별시|은평구', '이영인', '원장', '프라임온 영어교습소', '서울 은평구 갈현동', '서울특별시', '은평구', '글', '초·중·고를 연결하는 1:1 개별진도 수업을 진행하며, 선일여중, 불광중, 은평중, 대성중 등 인근 학교들의 시험 직전 요점 정리 자료를 직접 제작하여 높은 적중률을 보임.', '은평구 연신내 지역에서 1:1 개별진도 원장 직강을 강조하며, 인근 중학교들의 시험 직전 요점 정리 자료 제작 및 교재 분석 글을 작성한 교육자로 확인됨.', '은평구 연신내 지역에서 1:1 개별진도 원장 직강을 강조하며, 인근 중학교들의 시험 직전 요점 정리 자료 제작 및 교재 분석 글을 작성한 교육자로 확인됨.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 26, "v3CandidateId": 87, "v3RegionKey": "seoul-eunpyeong", "v3Region": "서울 은평구 갈현동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHSXrTDZEx4AHsUnepHp6-u-o5NUignukLSj-h_jAbTYA8QdBR2O_LVdSouSaPyLVLa0WS4ouiNd4Jh0AZd0r2tOv5eAoFd3ao--nseYbI7-w==', 'd1501a4aa64d3c5b00dfee42d527ae30b6d52073a588de05a50a6f1afe5a20dc', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '초·중·고를 연결하는 1:1 개별진도 수업을 진행하며, 선일여중, 불광중, 은평중, 대성중 등 인근 학교들의 시험 직전 요점 정리 자료를 직접 제작하여 높은 적중률을 보임.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHSXrTDZEx4AHsUnepHp6-u-o5NUignukLSj-h_jAbTYA8QdBR2O_LVdSouSaPyLVLa0WS4ouiNd4Jh0AZd0r2tOv5eAoFd3ao--nseYbI7-w==', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '이영인|프라임온영어교습소|서울특별시|은평구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-eunpyeong', '제프|솔리드영어교습소|서울특별시|은평구', '제프', '선생님', '솔리드영어교습소', '서울 은평구 역촌동', '서울특별시', '은평구', '글', '초등부에서 중등부로 진학하는 학생들을 대상으로 한 영어 학습 습관 형성과 2026학년도 수능 영어 분석 등 입시 영어의 흐름을 짚어주는 1:1 밀착 지도를 진행함.', '은평구 역촌동에서 초·중·고 대상 영어 전문 교습소를 운영하며, 수능 영어 분석 및 초등 신규생 맞춤형 학습법에 관한 글을 직접 작성하고 지도하는 교육자로 확인됨. 실명이 명확하지 않아 모호성(isAmbiguous)을 true로 표시함.', '은평구 역촌동에서 초·중·고 대상 영어 전문 교습소를 운영하며, 수능 영어 분석 및 초등 신규생 맞춤형 학습법에 관한 글을 직접 작성하고 지도하는 교육자로 확인됨. 실명이 명확하지 않아 모호성(isAmbiguous)을 true로 표시함.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 25, "v3CandidateId": 91, "v3RegionKey": "seoul-eunpyeong", "v3Region": "서울 은평구 역촌동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQG7dg0Ff4rnRRRWfFJ5RVZuvV3_TWelGEEao6dJH-mZw8QupOYczKQ649A_vHjvDeKI_ePJf1aRh-wArAVt6hLSf6SG7wRNrx58dvI0kMiRTaIXAxwK0-FzWPu_-A63XzlETAu0WuJiwsfRBANcYuh64VdtCknsZqODxlNEtGJVRViybzk0YTn2WGPJezfpnDbHIUcd96AdzdqavErHMcFkW_TskBqFK1hAr1Sdo-im', '0525b0dd31febe71c963d9819fb83c2cb20bf6fc756460b1a779a7d8e9ae3648', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '초등부에서 중등부로 진학하는 학생들을 대상으로 한 영어 학습 습관 형성과 2026학년도 수능 영어 분석 등 입시 영어의 흐름을 짚어주는 1:1 밀착 지도를 진행함.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQG7dg0Ff4rnRRRWfFJ5RVZuvV3_TWelGEEao6dJH-mZw8QupOYczKQ649A_vHjvDeKI_ePJf1aRh-wArAVt6hLSf6SG7wRNrx58dvI0kMiRTaIXAxwK0-FzWPu_-A63XzlETAu0WuJiwsfRBANcYuh64VdtCknsZqODxlNEtGJVRViybzk0YTn2WGPJezfpnDbHIUcd96AdzdqavErHMcFkW_TskBqFK1hAr1Sdo-im', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '제프|솔리드영어교습소|서울특별시|은평구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-yangcheon', '이승현|cs영어학원|서울특별시|양천구', '이승현', '원장', 'CS영어학원', '서울 양천구 목동', '서울특별시', '양천구', '인터뷰', '고등학생 중심의 영어 전문 학원을 운영하며, 학생 개개인의 성향과 가능성을 분석하고 입시와 학습 방향까지 설계하는 개별 맞춤형 교육을 제공함.', '목동에서 고등 영어 전문 학원을 운영하며, 학생 맞춤형 입시 전략과 교육 철학을 바탕으로 직접 학생들을 지도하는 원장으로 확인됨.', '목동에서 고등 영어 전문 학원을 운영하며, 학생 맞춤형 입시 전략과 교육 철학을 바탕으로 직접 학생들을 지도하는 원장으로 확인됨.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 29, "v3CandidateId": 92, "v3RegionKey": "seoul-yangcheon", "v3Region": "서울 양천구 목동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGndD7jOrRuHyvZWGChlVn2-xkoPfcPoEg9PAKGSQxp5Wqv5O4tg_qx5ajy7Gi92JVYJIzQIDrfRijnxjsU7CWj4JKIregDlSvCOrfy3VVOto8=', '3b04124b5c811680d3ba775110d675775c34db43249ba482186bc8744f91c3e3', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '인터뷰', '고등학생 중심의 영어 전문 학원을 운영하며, 학생 개개인의 성향과 가능성을 분석하고 입시와 학습 방향까지 설계하는 개별 맞춤형 교육을 제공함.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGndD7jOrRuHyvZWGChlVn2-xkoPfcPoEg9PAKGSQxp5Wqv5O4tg_qx5ajy7Gi92JVYJIzQIDrfRijnxjsU7CWj4JKIregDlSvCOrfy3VVOto8=', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '이승현|cs영어학원|서울특별시|양천구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-yangcheon', '황수미|영어잡는영잡|서울특별시|양천구', '황수미', '원장', '영어 잡는 영잡', '서울 양천구 신월동', '서울특별시', '양천구', '글', '''5문장 암기 시스템''과 자체 제작한 Daily Syntax 분석 노트를 통해 학생들의 영어 학습 습관과 사고력을 기르고, 데이터 기반으로 출결 및 학습을 관리함.', '양천구 신월동에서 소규모 공부방으로 시작해 독자적인 영어 학습 시스템과 브랜딩을 구축하고 직접 학생들을 지도하는 원장임.', '양천구 신월동에서 소규모 공부방으로 시작해 독자적인 영어 학습 시스템과 브랜딩을 구축하고 직접 학생들을 지도하는 원장임.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 28, "v3CandidateId": 94, "v3RegionKey": "seoul-yangcheon", "v3Region": "서울 양천구 신월동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHCfC2xewQTjUyQr9iwszJ70HbluY_GuP1nCmOroHuNvcFivRqPnKvXMCMWF3rxNn3wU8xlaVcwaCrC8N2YKXgpVifqe-Yi-1qw2Eav4VVJuIwz7FjWGz0bUXk=', '60b6b8f5a088a3a6916c3ee13346a800923701a4955dd2480f9a96d53acf9321', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '''5문장 암기 시스템''과 자체 제작한 Daily Syntax 분석 노트를 통해 학생들의 영어 학습 습관과 사고력을 기르고, 데이터 기반으로 출결 및 학습을 관리함.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHCfC2xewQTjUyQr9iwszJ70HbluY_GuP1nCmOroHuNvcFivRqPnKvXMCMWF3rxNn3wU8xlaVcwaCrC8N2YKXgpVifqe-Yi-1qw2Eav4VVJuIwz7FjWGz0bUXk=', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '황수미|영어잡는영잡|서울특별시|양천구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-gangseo', '박선경|씨투엠플러스학원|서울특별시|강서구', '박선경', '원장', '씨투엠플러스학원', '서울 강서구 마곡동', '서울특별시', '강서구', '칼럼', '초등부터 고등까지 국영수과 올케어 시스템을 운영하며, 특히 영어 점수 향상 경험을 바탕으로 학생들의 자기주도 학습 능력과 입시 컨설팅을 직접 지도합니다.', '마곡지구에서 단순 지식 전달을 넘어 학생들의 전인적 성장과 입시 컨설팅, 자기주도 학습을 직접 코칭하는 원장입니다.', '마곡지구에서 단순 지식 전달을 넘어 학생들의 전인적 성장과 입시 컨설팅, 자기주도 학습을 직접 코칭하는 원장입니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 30, "v3CandidateId": 103, "v3RegionKey": "seoul-gangseo", "v3Region": "서울 강서구 마곡동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFTMuStdB5m1Tghx3AaCyeY6ivgFZQ6ah4BpvSCJswl9vcN0kzI2IdRr7edPd-uLPFmbcWDV9xuFfbTWkqF0TGgETx4odU3-FBcrx8AUvuzBw==', 'c752050a8ab04cc84b46711f166e0be9e5f4edb41f1295f9add22c66501d657f', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '칼럼', '초등부터 고등까지 국영수과 올케어 시스템을 운영하며, 특히 영어 점수 향상 경험을 바탕으로 학생들의 자기주도 학습 능력과 입시 컨설팅을 직접 지도합니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFTMuStdB5m1Tghx3AaCyeY6ivgFZQ6ah4BpvSCJswl9vcN0kzI2IdRr7edPd-uLPFmbcWDV9xuFfbTWkqF0TGgETx4odU3-FBcrx8AUvuzBw==', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '박선경|씨투엠플러스학원|서울특별시|강서구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-yeongdeungpo', '제니쌤|제니쌤잉글리시영어교습소|서울특별시|영등포구', '제니쌤', '원장', '제니쌤잉글리시영어교습소', '서울 영등포구 신길동', '서울특별시', '영등포구', '글', '초등학생들을 위해 ''Pete the Cat Saves Up'' 원서를 활용하여 돈을 모으고 사용하는 방법에 대해 영어로 읽고 발표하는 낭독 및 원서 수업을 지도함.', '신길동에서 원서를 활용한 초등 영어 낭독 및 발표 수업 콘텐츠를 직접 작성하여 지도하는 개인 교육자이나, 실명이 명확히 확인되지 않아 모호함으로 표시함.', '신길동에서 원서를 활용한 초등 영어 낭독 및 발표 수업 콘텐츠를 직접 작성하여 지도하는 개인 교육자이나, 실명이 명확히 확인되지 않아 모호함으로 표시함.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 31, "v3CandidateId": 107, "v3RegionKey": "seoul-yeongdeungpo", "v3Region": "서울 영등포구 신길동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://blog.naver.com/jennyenglish77', 'ba157a965a4d7c03655c1a865f8f837c14c305662ece8a310f2d5ad8622fb343', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '초등학생들을 위해 ''Pete the Cat Saves Up'' 원서를 활용하여 돈을 모으고 사용하는 방법에 대해 영어로 읽고 발표하는 낭독 및 원서 수업을 지도함.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://blog.naver.com/jennyenglish77', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '제니쌤|제니쌤잉글리시영어교습소|서울특별시|영등포구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-dongjak', '김혜경|뮤엠영어학원|서울특별시|동작구', '김혜경', '원장', '뮤엠영어학원', '서울 동작구 사당동', '서울특별시', '동작구', '칼럼', '디지털 시대에 영어 쓰기를 통해 아이들의 유추, 추론 능력 및 논리적 사고력을 확장하는 영어 교육의 중요성을 강조합니다.', '동작구 사당동에서 20년 경력의 유아 영어 교육 전문가이자 뮤엠영어학원 원장으로 활동하며, 영어 쓰기를 통한 사고력 확장에 관한 교육 칼럼을 기고한 내용을 통해 발굴했습니다.', '동작구 사당동에서 20년 경력의 유아 영어 교육 전문가이자 뮤엠영어학원 원장으로 활동하며, 영어 쓰기를 통한 사고력 확장에 관한 교육 칼럼을 기고한 내용을 통해 발굴했습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 35, "v3CandidateId": 109, "v3RegionKey": "seoul-dongjak", "v3Region": "서울 동작구 사당동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFmTO22HxLbniZLBgvV6R8FAVB_Eu6MePpwp2W3W_evMD4jKj5_YNGBhhqa2jj6p1pH3yKSPjGHUCTUFp3lwGEwr5ZmgF3D7yLyM8BxfZK85doSAIbdIaL5MZKA8CwMRBK3suc3sr69VR1aHrj2Ru2snw==', 'b4e83f3f6c4e46afd40fdb1a1b3ee19e23820155eccda64ddd94f327efae03bd', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '칼럼', '디지털 시대에 영어 쓰기를 통해 아이들의 유추, 추론 능력 및 논리적 사고력을 확장하는 영어 교육의 중요성을 강조합니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFmTO22HxLbniZLBgvV6R8FAVB_Eu6MePpwp2W3W_evMD4jKj5_YNGBhhqa2jj6p1pH3yKSPjGHUCTUFp3lwGEwr5ZmgF3D7yLyM8BxfZK85doSAIbdIaL5MZKA8CwMRBK3suc3sr69VR1aHrj2Ru2snw==', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '김혜경|뮤엠영어학원|서울특별시|동작구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-dongjak', '류제시|제시영어학원|서울특별시|동작구', '류제시', '대표', '제시영어학원', '서울 동작구', '서울특별시', '동작구', '글', '영어를 배우며 사고력을 키우고 자신감을 높이는 교육 철학을 바탕으로, 20년 후에도 기억에 남는 리더를 양성하는 학원을 지향합니다.', '동작구에서 실제로 학생들을 가르치는 개인 영어교육자로, 영어를 통한 사고력 확장과 자신감 향상을 강조하는 교육 철학 인터뷰를 통해 발굴했습니다.', '동작구에서 실제로 학생들을 가르치는 개인 영어교육자로, 영어를 통한 사고력 확장과 자신감 향상을 강조하는 교육 철학 인터뷰를 통해 발굴했습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 34, "v3CandidateId": 110, "v3RegionKey": "seoul-dongjak", "v3Region": "서울 동작구", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEsY6zSlEQ1tW8cTKrxu-U41oXPBQ-45r15NpMEbKoNH4KaAYVSu6KU-o5whjQdHqS-uWwKH0wiMz11QebrSDdyM2u-AiQBkQ7mjEBnAMblMfMCmoQTR0TY', 'e80778150260cd2bc11a6093a36879b3d8db9da9d9dec23a26ecc8cda36dd92c', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '영어를 배우며 사고력을 키우고 자신감을 높이는 교육 철학을 바탕으로, 20년 후에도 기억에 남는 리더를 양성하는 학원을 지향합니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEsY6zSlEQ1tW8cTKrxu-U41oXPBQ-45r15NpMEbKoNH4KaAYVSu6KU-o5whjQdHqS-uWwKH0wiMz11QebrSDdyM2u-AiQBkQ7mjEBnAMblMfMCmoQTR0TY', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '류제시|제시영어학원|서울특별시|동작구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-dongjak', '이지혜|마스터즈영어|서울특별시|동작구', '이지혜', '원장', '마스터즈영어', '서울 동작구', '서울특별시', '동작구', '글', '시험을 위한 영어가 아닌, 미래를 위한 영어라는 슬로건 아래, 실질적인 영어 실력 향상을 목표로 하는 영어 교육을 실천합니다.', '동작구에서 영어 교습소를 운영하며 시험 위주가 아닌 미래 지향적 영어 교육을 제공하는 교육자로서, 관련 인터뷰 기사를 통해 발굴했습니다.', '동작구에서 영어 교습소를 운영하며 시험 위주가 아닌 미래 지향적 영어 교육을 제공하는 교육자로서, 관련 인터뷰 기사를 통해 발굴했습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 33, "v3CandidateId": 112, "v3RegionKey": "seoul-dongjak", "v3Region": "서울 동작구", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEYXfvF8N59P6BBoAwOmR2CdJnP-DFIEmkseMfoIypKuXjBJwuchWkjbo3yOe35UT7xTKjvqS2UO1h8ehz8BCYzYushhVhH-p58qqPDhW1iIRoLop0QUCkD6A==', 'baebbecd3f884bc675c497a04ef2e034c595afdb7bba319acf09e95571b5353b', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '시험을 위한 영어가 아닌, 미래를 위한 영어라는 슬로건 아래, 실질적인 영어 실력 향상을 목표로 하는 영어 교육을 실천합니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEYXfvF8N59P6BBoAwOmR2CdJnP-DFIEmkseMfoIypKuXjBJwuchWkjbo3yOe35UT7xTKjvqS2UO1h8ehz8BCYzYushhVhH-p58qqPDhW1iIRoLop0QUCkD6A==', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '이지혜|마스터즈영어|서울특별시|동작구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-dongjak', '성지윤|아스터영어학원|서울특별시|동작구', '성지윤', '원장', '아스터영어학원', '서울 동작구 사당동', '서울특별시', '동작구', '글', '사당동에서 16년 이상 영어 강의를 하며 800명 이상의 학생들을 지도해 온 경험을 바탕으로, 단순한 자습실 이용이 아닌 원장이 직접 밀착 관리하는 여름방학 프로젝트의 중요성을 강조합니다.', '동작구 사당동에서 실제로 학생들을 가르치는 개인 영어교육자로, 원장의 직접 관리와 학습법에 관한 소식글을 통해 발굴했습니다.', '동작구 사당동에서 실제로 학생들을 가르치는 개인 영어교육자로, 원장의 직접 관리와 학습법에 관한 소식글을 통해 발굴했습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 32, "v3CandidateId": 113, "v3RegionKey": "seoul-dongjak", "v3Region": "서울 동작구 사당동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEeAc0qisiQqrylSg47iDBRcxbp0k4BmuAfudoQjQevFaQovibKhd04108R6Ou7mGc1yf30xmbYpbGjbrK_cjAs-zZVQUxl01aRnJmaj8OO_ZLhCWM2MRSuNsSh_QOXyw5dtN4ffz0970Gtk91c9Pgnin-CW488JGZCg67ldOcJKjQ3E0jKyxw9-l5QgrLzqPIE00r0MAPj3Cz_brYwfe70Oh5ljwijVnkAxg==', '7633d2e98a0586de05597d8494a79a241229af267843db0476d6665d8adc8bad', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '사당동에서 16년 이상 영어 강의를 하며 800명 이상의 학생들을 지도해 온 경험을 바탕으로, 단순한 자습실 이용이 아닌 원장이 직접 밀착 관리하는 여름방학 프로젝트의 중요성을 강조합니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEeAc0qisiQqrylSg47iDBRcxbp0k4BmuAfudoQjQevFaQovibKhd04108R6Ou7mGc1yf30xmbYpbGjbrK_cjAs-zZVQUxl01aRnJmaj8OO_ZLhCWM2MRSuNsSh_QOXyw5dtN4ffz0970Gtk91c9Pgnin-CW488JGZCg67ldOcJKjQ3E0jKyxw9-l5QgrLzqPIE00r0MAPj3Cz_brYwfe70Oh5ljwijVnkAxg==', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '성지윤|아스터영어학원|서울특별시|동작구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-geumcheon', '김시훈|시은영어학원|서울특별시|금천구', '김시훈', '원장', '시은영어학원', '서울 금천구 시흥동', '서울특별시', '금천구', '글', '문일고 1학년 영어 내신 시험 4주 전 대비 전략 분석 및 초등 고학년을 위한 단어 암기 공부법, 오답 관리에 대한 교육 칼럼을 작성했습니다.', '금천구 시흥동에서 시은영어학원을 운영하며 문일고, 금천고, 동일여고 등 지역 학교들의 영어 내신 분석 및 공부법 칼럼을 직접 작성하여 학생들을 지도하고 있습니다.', '금천구 시흥동에서 시은영어학원을 운영하며 문일고, 금천고, 동일여고 등 지역 학교들의 영어 내신 분석 및 공부법 칼럼을 직접 작성하여 학생들을 지도하고 있습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 37, "v3CandidateId": 114, "v3RegionKey": "seoul-geumcheon", "v3Region": "서울 금천구 시흥동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://blog.naver.com/sieuneng', '30c2907dad1e68f7e7027e23e05bf324b3e2c2f32c8548b4ad753b0dfe40b22a', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '문일고 1학년 영어 내신 시험 4주 전 대비 전략 분석 및 초등 고학년을 위한 단어 암기 공부법, 오답 관리에 대한 교육 칼럼을 작성했습니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://blog.naver.com/sieuneng', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '김시훈|시은영어학원|서울특별시|금천구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-geumcheon', '신정화|치즈영어교습소|서울특별시|금천구', '신정화', '대표', '치즈영어교습소', '서울 금천구 독산동', '서울특별시', '금천구', '글', '5명 정원제 소수정예 클래스를 통해 숙제 없이 학원 내에서 집중적으로 문법과 다독을 지도하는 초등 전문 영어 교육법을 소개했습니다.', '금천구 독산동에서 치즈영어교습소를 직접 운영하며, 다독과 언어적 소양을 강조하는 1:1 밀착형 초등 영어 교육을 실천하고 있습니다.', '금천구 독산동에서 치즈영어교습소를 직접 운영하며, 다독과 언어적 소양을 강조하는 1:1 밀착형 초등 영어 교육을 실천하고 있습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_AUTO', '{"v3EducatorId": 36, "v3CandidateId": 118, "v3RegionKey": "seoul-geumcheon", "v3Region": "서울 금천구 독산동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_AUTO"}',
        b'1', b'1', b'1', b'1',
        'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQG10U4P2IuUIraaCIMOSaNLyNc1tZy3ZhZtjcPAkHIeBQsMK_O17FEwZAq5KsT9OpQDbXECCIkOpXYVBWqqEXkg2WW9VdFrEAvMPC9RuuOg-D9FRrrx2hkh', '9489d501ceba91560b1c5122271186c079d19360e2f7f7ec7663b295795a013f', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '글', '5명 정원제 소수정예 클래스를 통해 숙제 없이 학원 내에서 집중적으로 문법과 다독을 지도하는 초등 전문 영어 교육법을 소개했습니다.', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQG10U4P2IuUIraaCIMOSaNLyNc1tZy3ZhZtjcPAkHIeBQsMK_O17FEwZAq5KsT9OpQDbXECCIkOpXYVBWqqEXkg2WW9VdFrEAvMPC9RuuOg-D9FRrrx2hkh', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '신정화|치즈영어교습소|서울특별시|금천구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-gangnam', '조민규|조민규영어학원|서울특별시|강남구', '조민규', '원장', '조민규영어학원', '강남구 대치동', '서울특별시', '강남구', '수동 발굴', '운영자 수동 발굴 등록', '서울 강남구 기준으로는 조민규영어의 조민규 원장과 지피에이영어학원의 정승주 원장이 지역 기반 소규모 영어교육자 후보로 확인됩니다. 조민규 원장은 대치동 중등 영어의 개별 맞춤·내신·정규 과정 전환을 강조하고, 정승주 원장은 도곡동에서 11년째 초·중·고 영어와 국내외 입시, 말하기·쓰기·토론형 수업을 운영하는 점이 공개 근거로 확인돼 BuildIt 후보 검토 대상으로 볼 만해요.', '서울 강남구 기준으로는 조민규영어의 조민규 원장과 지피에이영어학원의 정승주 원장이 지역 기반 소규모 영어교육자 후보로 확인됩니다. 조민규 원장은 대치동 중등 영어의 개별 맞춤·내신·정규 과정 전환을 강조하고, 정승주 원장은 도곡동에서 11년째 초·중·고 영어와 국내외 입시, 말하기·쓰기·토론형 수업을 운영하는 점이 공개 근거로 확인돼 BuildIt 후보 검토 대상으로 볼 만해요.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_MANUAL', '{"v3EducatorId": 43, "v3CandidateId": 120, "v3RegionKey": "__manual__", "v3Region": "강남구 대치동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_MANUAL"}',
        b'1', b'1', b'1', b'1',
        'https://blog.naver.com/chotcher', '4ddd657ed25418d7fdc40368a5f6de7c3d39e21d4c58382dcc65d82104984dcb', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '수동 발굴', '운영자 수동 발굴 등록', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://blog.naver.com/chotcher', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '조민규|조민규영어학원|서울특별시|강남구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-gangnam', '정승주원장|지피에이영어학원|서울특별시|강남구', '정승주 원장', '원장', '지피에이영어학원', '강남구 도곡동', '서울특별시', '강남구', '수동 발굴', '운영자 수동 발굴 등록', '정승주 원장은 블로그에서 지피에이영어학원 원장으로 자신을 소개하며, 도곡동에서 11년째 학생들의 영어를 지도하고 있다고 밝혔습니다. 공개 글에서는 초등·중등·고등 내신 및 수능뿐 아니라 국제학교와 해외 입시까지 고려한 학습 방향 설정을 언급합니다. Sunny T +2 교육 철학 및 수업 방식 수준 맞춤 교재 선택: 유명 교재나 높은 난도의 교재가 항상 효과적인 것은 아니며, 학생의 학습 수준과 교재 레벨이 맞아야 한다는 관점을 제시합니다. Sunny T Speaking·Writing 대회 운영:', '정승주 원장은 블로그에서 지피에이영어학원 원장으로 자신을 소개하며, 도곡동에서 11년째 학생들의 영어를 지도하고 있다고 밝혔습니다. 공개 글에서는 초등·중등·고등 내신 및 수능뿐 아니라 국제학교와 해외 입시까지 고려한 학습 방향 설정을 언급합니다. Sunny T +2 교육 철학 및 수업 방식 수준 맞춤 교재 선택: 유명 교재나 높은 난도의 교재가 항상 효과적인 것은 아니며, 학생의 학습 수준과 교재 레벨이 맞아야 한다는 관점을 제시합니다. Sunny T Speaking·Writing 대회 운영: 초등 정규반 학생들이 매년 두 차례 Speech Contest와 Writing Contest에 참여한다고 설명합니다. 상위권 학생만이 아닌 모든 학생이 준비 과정부터 참여하는 방식으로 소개됩니다. Sunny T 첨삭 기반 글쓰기 지도: 완성한 글을 원장과 강사진이 직접 읽고 첨삭하며, 주제 이해, 독창성, 글의 흐름, 단어와 문법 사용 등 네 가지 기준으로 점검한다고 설명합니다. Sunny T 토론형 라이팅 수업: Foundation Writing, Core Writing, Advanced Writing 단계에서 기본 문장·문단 구성, 의견과 이유 설명, 토론 후 에세이 작성으로 이어지는 흐름을 제시합니다. Sunny T 6개월 담임제: 학생 한 명을 가까이 살피기 위해 담임 선생님이 표정 변화까지 체크할 수 있는 6개월 담임제를 선택했다고 설명합니다',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_MANUAL', '{"v3EducatorId": 42, "v3CandidateId": 121, "v3RegionKey": "__manual__", "v3Region": "강남구 도곡동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_MANUAL"}',
        b'1', b'1', b'1', b'1',
        'https://blog.naver.com/globalprep-', 'ed46f636a88ff67f5c1fe66f4b451e5186200d656fef9b511aa5782e2a218951', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '수동 발굴', '운영자 수동 발굴 등록', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://blog.naver.com/globalprep-', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '정승주원장|지피에이영어학원|서울특별시|강남구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-gangnam', '박정민|압구정에듀홀릭학원|서울특별시|강남구', '박정민', '원장', '압구정에듀홀릭학원', '강남구 압구정', '서울특별시', '강남구', '수동 발굴', '운영자 수동 발굴 등록', '박정민 대표원장의 공개 프로필에서는 미국 샌프란시스코 주립대학교 TESOL 석사 학위와 국내 여러 대학교 영어 교수법 강사 출강 이력이 확인됩니다. 또한 과천정부청사 중앙공무원교육원 5급 공무원 영어교육 20년 경력이 소개되어 있어, 일반 학생 대상 영어지도뿐 아니라 성인·공무원 영어교육 경험까지 함께 보유한 교육자로 볼 수 있어요. 압구정에듀홀릭학원 경력의 핵심은 단순히 오래 가르쳤다는 점보다, 영어 교수법과 실제 적용 가능한 영어 능력을 함께 강조한다는 데 있습니다. 학원 소개 글에서도 체계적', '박정민 대표원장의 공개 프로필에서는 미국 샌프란시스코 주립대학교 TESOL 석사 학위와 국내 여러 대학교 영어 교수법 강사 출강 이력이 확인됩니다. 또한 과천정부청사 중앙공무원교육원 5급 공무원 영어교육 20년 경력이 소개되어 있어, 일반 학생 대상 영어지도뿐 아니라 성인·공무원 영어교육 경험까지 함께 보유한 교육자로 볼 수 있어요. 압구정에듀홀릭학원 경력의 핵심은 단순히 오래 가르쳤다는 점보다, 영어 교수법과 실제 적용 가능한 영어 능력을 함께 강조한다는 데 있습니다. 학원 소개 글에서도 체계적인 영어 교수법과 학생별 분석을 바탕으로 한 수업 방식을 내세우고 있어, 이론적 배경과 현장 지도 경험을 연결하는 형태의 교육자로 정리됩니다. 압구정에듀홀릭학원 교육 철학 및 수업 방식 박정민 원장의 교육 철학은 학생 개별 분석 기반의 맞춤형 지도에 맞춰져 있습니다. 학원 소개 글에서는 2008년부터 압구정에서 영어 교육을 이어 왔고, 초·중·고 모든 학습 단계의 학생에게 문법·독해·내신·수능 영어를 지도해 온 영어 전문학원이라고 설명합니다. 압구정에듀홀릭학원 수업 방식은 1:1 맞춤식 개별 지도 시스템으로 소개됩니다. 학생마다 실력, 목표, 학습 패턴이 다르기 때문에 이를 분석해 가장 빠르고 정확하게 성장하도록 설계한다는 방향이에요. 따라서 대형 강의식 수업보다는 학생별 취약점 진단과 보완에 초점을 둔 소규모·개별 관리형 수업에 가깝습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_MANUAL', '{"v3EducatorId": 41, "v3CandidateId": 122, "v3RegionKey": "__manual__", "v3Region": "강남구 압구정", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_MANUAL"}',
        b'1', b'1', b'1', b'1',
        'https://buildit.example/legacy-v3/122', '8461746c17c72a130463136bd02ab93e07265b6ec3e5b1a5c4a8af4880aa96d4', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '수동 발굴', '운영자 수동 발굴 등록', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://buildit.example/legacy-v3/122', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '박정민|압구정에듀홀릭학원|서울특별시|강남구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('seoul-gangnam', '김주경|김주경영어교습소|서울특별시|강남구', '김주경', '원장', '김주경 영어교습소', '도곡동', '서울특별시', '강남구', '수동 발굴', '운영자 수동 발굴 등록', '김주경 영어교습소는 당근 로컬 프로필에서 대형학원 노하우를 소수정예로 전달한다는 설명과 함께, 모든 수업 원장 직강을 내세웁니다. 대형학원에서의 20년 강의 노하우를 소수정예 학습으로 전달한다고 소개되어 있고, 예비 중1부터 고3까지를 대상으로 한 콘텐츠가 확인됩니다. 당근 대표 수업이나 직접 제작 콘텐츠의 구체적 명칭은 출처에 충분히 나오지 않습니다. 하지만 “원장 직강”, “소수정예”, “20년 강의 노하우”, “예비 중1~고3 대상”이라는 공개 문구가 확인되므로, 개인 교육자 후보로서 검토', '김주경 영어교습소는 당근 로컬 프로필에서 대형학원 노하우를 소수정예로 전달한다는 설명과 함께, 모든 수업 원장 직강을 내세웁니다. 대형학원에서의 20년 강의 노하우를 소수정예 학습으로 전달한다고 소개되어 있고, 예비 중1부터 고3까지를 대상으로 한 콘텐츠가 확인됩니다. 당근 대표 수업이나 직접 제작 콘텐츠의 구체적 명칭은 출처에 충분히 나오지 않습니다. 하지만 “원장 직강”, “소수정예”, “20년 강의 노하우”, “예비 중1~고3 대상”이라는 공개 문구가 확인되므로, 개인 교육자 후보로서 검토 가치가 있습니다.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_MANUAL', '{"v3EducatorId": 40, "v3CandidateId": 123, "v3RegionKey": "__manual__", "v3Region": "도곡동", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_MANUAL"}',
        b'1', b'1', b'1', b'1',
        'https://blog.naver.com/jk1468', 'e36a88c72ff6db9dd00f18ebee9747e4bd0f75b7e89206dbd0cf7d69ad8da534', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '수동 발굴', '운영자 수동 발굴 등록', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://blog.naver.com/jk1468', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '김주경|김주경영어교습소|서울특별시|강남구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('busan-haeundae', '정종욱|영어종결센터반여학원|부산광역시|해운대구', '정종욱', '원장', '영어종결센터 반여학원', '부산 해운대구', '부산광역시', '해운대구', '수동 발굴', '운영자 수동 발굴 등록', '정종욱 원장은 영어종결센터 반여학원 대표 원장으로 소개되며, 반여동 영어 수업 10년 이상 경력과 학교별 전담 수업을 내세웁니다. 반여고, 충렬고, 장산중, 인지중 등 주변 학교 재원생의 진학 사례가 공개되어 있어 지역 내신 연계성이 확인됩니다. 영어종결센터반여학원 관리 방식은 테스트 기반 성적 추적, 숙제·오답·출결 관리, 수업 데일리 리포트 발송으로 설명됩니다. 학원임에도 과외 이상의  1 : 1 1:1 피드백과 학습 전략 수립을 제공한다고 밝히고 있어, 대형 강의형보다 관리형 내신 대비 후보', '정종욱 원장은 영어종결센터 반여학원 대표 원장으로 소개되며, 반여동 영어 수업 10년 이상 경력과 학교별 전담 수업을 내세웁니다. 반여고, 충렬고, 장산중, 인지중 등 주변 학교 재원생의 진학 사례가 공개되어 있어 지역 내신 연계성이 확인됩니다. 영어종결센터반여학원 관리 방식은 테스트 기반 성적 추적, 숙제·오답·출결 관리, 수업 데일리 리포트 발송으로 설명됩니다. 학원임에도 과외 이상의  1 : 1 1:1 피드백과 학습 전략 수립을 제공한다고 밝히고 있어, 대형 강의형보다 관리형 내신 대비 후보로 보기 좋아요. 영어종결센터반여학원 직접 제작 콘텐츠 및 최근 활동 최근 교육 활동은 예비고1 특강, 중학 핵심 영문법, 중등 서술형, 고등 내신 어법, 구문독해, 고등 문법 문제 풀이 등으로 확인됩니다. 특히 예비고1 특강은 12월~2월 3개월 완성 과정으로 안내되어, 고교 입학 전 전환기에 맞춘 프로그램을 운영한 기록이 있습니다. 영어종결센터반여학원 +1 또한 인지중학교를 포함한 반여동 인근 중·고등학교의 기출 문제 데이터를 수집·분석하고, 이를 바탕으로  1 : 1 1:1 피드백과 학습 전략을 제시한다고 설명합니다. 이는 단순 문제풀이보다 학교별 시험 유형을 분석해 학생별 약점을 보완하는 방식에 가깝습니다. 영어종결센터반여학원',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_MANUAL', '{"v3EducatorId": 39, "v3CandidateId": 124, "v3RegionKey": "__manual__", "v3Region": "부산 해운대구", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_MANUAL"}',
        b'1', b'1', b'1', b'1',
        'https://blog.naver.com/tysg3825', '2bd7e3bc7424a95536d0f46b63aaa0379a1c8fd4762be53bdd6904262e85a291', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '수동 발굴', '운영자 수동 발굴 등록', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://blog.naver.com/tysg3825', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '정종욱|영어종결센터반여학원|부산광역시|해운대구' AND c.legacy_source IS NOT NULL LIMIT 1;

INSERT IGNORE INTO chart_candidates
  (zone_id, person_key, name, role, academy_name, activity_region, activity_province, activity_city_district, content_type, content_summary, identity_summary, discovery_reason, deep_search_result, exclusion_check, verification_status, status, google_query, eligibility_status, legacy_source, legacy_metadata_json, offline_activity_confirmed, current_year_activity_confirmed, educator_identity_confirmed, exclusion_free, source_url, source_url_hash, source_title, criteria_version, research_source, discovered_at, created_at, updated_at)
VALUES ('busan-nam', '미키쌤|국제어학당대연캠퍼스|부산광역시|부산시', '미키쌤', '원장', '국제어학당 대연캠퍼스', '부산시', '부산광역시', '부산시', '수동 발굴', '운영자 수동 발굴 등록', '미키쌤은 영어강사로 18년 일했으며, YBM 서면을 비롯한 대형 어학원에서 영어회화 라인을 담당했던 이력이 확인됩니다. 과거 회화 수업에서 만든 수작업 자료를 현재 부산시 청년취업수업이나 회화수업에도 활용한다고 설명해, 오랜 기간 축적한 수업 자료와 강의 경험이 강점으로 보입니다. 미키쌤영어 커리큘럼 방향은 초등영어 원서, 영어회화, 토익, 내신 교재 세팅으로 제시됩니다. 특히 “일주일에 1번, 길면 3달 코스”가 아니라 한 아이를 꾸준히 가르치고 싶다는 취지로 대연동 영어학원 운영 계획을 밝혔기', '미키쌤은 영어강사로 18년 일했으며, YBM 서면을 비롯한 대형 어학원에서 영어회화 라인을 담당했던 이력이 확인됩니다. 과거 회화 수업에서 만든 수작업 자료를 현재 부산시 청년취업수업이나 회화수업에도 활용한다고 설명해, 오랜 기간 축적한 수업 자료와 강의 경험이 강점으로 보입니다. 미키쌤영어 커리큘럼 방향은 초등영어 원서, 영어회화, 토익, 내신 교재 세팅으로 제시됩니다. 특히 “일주일에 1번, 길면 3달 코스”가 아니라 한 아이를 꾸준히 가르치고 싶다는 취지로 대연동 영어학원 운영 계획을 밝혔기 때문에, 단기 특강형보다 지속 지도형 교육자에 가까워요.',
        'Legacy V3 선정 이력 승계 (당시 조사)', '', 'VERIFIED', 'DISCOVERED', 'legacy-v3-import',
        'ELIGIBLE', 'V3_MANUAL', '{"v3EducatorId": 38, "v3CandidateId": 125, "v3RegionKey": "__manual__", "v3Region": "부산시", "v3Score": 2.0, "v3ScoreIsCurrent": false, "source": "V3_MANUAL"}',
        b'1', b'1', b'1', b'1',
        'https://blog.naver.com/mingkyworld/', '6a50adf8930026aa5db728c83ded9134e7e66d4624419fc48e4a07de7ed1f238', 'Legacy V3 조사 근거', 'buildit-chart-v1', 'LEGACY_V3', NOW(6), NOW(6), NOW(6));
INSERT IGNORE INTO chart_candidate_activities
  (candidate_id, content_type, content_summary, evidence_kind, educationally_informative,
   researched_at, source_url, discovered_at, created_at, updated_at)
SELECT c.candidate_id, '수동 발굴', '운영자 수동 발굴 등록', 'EDUCATIONAL_CONTENT', b'1', NOW(6), 'https://blog.naver.com/mingkyworld/', NOW(6), NOW(6), NOW(6)
  FROM chart_candidates c WHERE c.person_key = '미키쌤|국제어학당대연캠퍼스|부산광역시|부산시' AND c.legacy_source IS NOT NULL LIMIT 1;
