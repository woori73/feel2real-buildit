-- 카드 이벤트의 승인/거래일(occurred_at)과 카드사 확정(매입)일을 분리 보존한다.
-- 신한카드 "개인사업자 이용내역" 등 일부 소스는 거래일과 확정일이 다르며
-- (예: 09/12 거래 -> 09/13 확정), 이 둘을 합치면 boundary dedupe가 틀어질 수 있다.
-- NULL 허용 — 확정일 개념이 없는 소스는 채우지 않는다.
ALTER TABLE `easy_card_events`
    ADD COLUMN `confirmation_date` DATE NULL
        COMMENT '카드사 확정(매입)일 — occurred_at(거래/승인일)과 별개, 소스가 제공할 때만 저장';
