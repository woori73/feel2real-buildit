SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 대표 원장 소개 영상과 수업 영상이 academy_videos(학원당 공용 풀)를 함께 쓰다 보니 업로드
-- 시점에 용도가 저장되지 않아 원장 영상이 수업 영상 선택 목록에도 나타나는 문제가 있었다.
--
-- NULL 허용, 기존 행은 백필하지 않는다: 파일명만으로 과거 업로드의 실제 용도를 추측해
-- 일괄 마킹하지 않는다(운영 데이터 임의 변경 금지). NULL은 애플리케이션에서 "용도 미상"으로
-- 취급해 두 선택 목록 모두에 계속 노출한다 — 과거 업로드를 목록에서 잃어버리지 않는다.
-- 새 업로드부터는 Builder/영상 관리 화면에서 고른 값(DIRECTOR|LESSON)이 저장된다.
ALTER TABLE academy_videos
  ADD COLUMN purpose VARCHAR(20) NULL COMMENT 'DIRECTOR|LESSON. NULL=용도 미상(레거시, 마이그레이션 안 함)';
