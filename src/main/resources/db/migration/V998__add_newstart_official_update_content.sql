-- NewStart 내부 상세보기: 공식 원문 본문 저장 컬럼
-- discovery 의 detailHtml(공식 원문)을 요약(300자)과 별개로 보존하기 위함.
-- 기존 행은 NULL → 상세화면에서 summary 기반으로 FULL/PARTIAL/CONTENT MISSING 을 판정한다.
ALTER TABLE newstart_official_updates
    ADD COLUMN content TEXT NULL AFTER summary;
