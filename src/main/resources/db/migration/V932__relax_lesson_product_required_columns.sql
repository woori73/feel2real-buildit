-- 수업상품 전용 편집기가 예시 HTML 기준 필드만 받도록 바뀌면서
-- 가격·정원·일정·수업 유형은 더 이상 입력받지 않는다. 값 없이도 수업이 생성되도록
-- 해당 컬럼의 NOT NULL을 해제한다.
--
-- target_group은 V927이 NULL로 추가한 뒤 마지막 줄에서 다시
-- NOT NULL DEFAULT 'HIGH'로 되돌려 놓았으므로 여기서 함께 푼다.
-- DEFAULT도 제거해야 지정하지 않은 대상이 'HIGH'로 저장되지 않는다.
--
-- 대상이 아닌 것:
--   · name           예시 HTML의 '수업상품명'이라 편집기가 계속 받는다
--   · price_amount, weekly_count, start_date, end_date, duration_weeks, duration_months
--                    V3부터 NULL 허용
--   · status         편집기가 항상 PUBLISHED를 넣으므로 NOT NULL 유지
--   · academy_id, current_count, is_visible
--                    구조·내부 카운터
--
-- 기존 행의 값은 변경하지 않는다(제약만 완화).
ALTER TABLE lesson_products
  MODIFY COLUMN lesson_type ENUM('SUBSCRIPTION', 'SHORT_TERM') NULL,
  MODIFY COLUMN target_group VARCHAR(20) NULL DEFAULT NULL,
  MODIFY COLUMN price_unit ENUM('MONTH', 'COURSE', 'NEGOTIABLE') NULL,
  MODIFY COLUMN price_summary VARCHAR(100) NULL,
  MODIFY COLUMN max_capacity INT NULL,
  MODIFY COLUMN schedule_type ENUM('WEEKLY', 'PERIOD', 'CONSULTATION') NULL,
  MODIFY COLUMN schedule_summary VARCHAR(200) NULL;
