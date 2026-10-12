-- BuildIt P1: 버전 무결성 보강 (B4)
-- academy_page_versions: (academy_id, version_no) UNIQUE로 동시 생성 중복 방지
-- P1 테이블에 academies FK 추가 (Academy 삭제 시 고아 데이터 방지)

ALTER TABLE academy_page_versions
    ADD UNIQUE KEY uq_academy_version_no (academy_id, version_no);

ALTER TABLE academy_page_versions
    ADD CONSTRAINT fk_apv_academy FOREIGN KEY (academy_id)
    REFERENCES academies (academy_id) ON DELETE CASCADE;

ALTER TABLE academy_ai_sessions
    ADD CONSTRAINT fk_aas_academy FOREIGN KEY (academy_id)
    REFERENCES academies (academy_id) ON DELETE CASCADE;

ALTER TABLE academy_ai_idempotency
    ADD CONSTRAINT fk_aai_academy FOREIGN KEY (academy_id)
    REFERENCES academies (academy_id) ON DELETE CASCADE;
