-- ============================================================
-- V1047: newstart_official_updates 5건의 published_at 보정 (2026-10-07)
--   · V1046의 ON DUPLICATE KEY UPDATE가 published_at을 갱신하지 않아,
--     auto-discovery 선등록 레코드의 오래된 published_at이 유지됨.
--   · 정렬(findByReviewStatusOrderByFeaturedDescPublishedAtDesc)은
--     published_at DESC이므로 2026년 신규 항목이 상단에 오지 못했음.
--   · 5건의 dedup_key에 대해 published_at을 명시적으로 갱신한다.
--   · 4번 항목은 REVIEW 유지 (공식 URL 미확보).
--   · 5번 항목(상시 안내, published_at NULL)은 featured=TRUE로 상단 고정.
--   · V1045/V1046은 수정하지 않는다. UPDATE만 하며 INSERT 없음.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1) 2026-02-26 금융위원회 새출발기금 추진사항
UPDATE `newstart_official_updates`
SET `published_at` = '2026-02-26 16:00:00',
    `review_status` = 'PUBLISHED',
    `updated_at` = NOW(6)
WHERE `dedup_key` = 'DOC:FSC:fsc-86357';

-- 2) 2026-06-25 금융위원회·캠코 심사체계 개선
UPDATE `newstart_official_updates`
SET `published_at` = '2026-06-25 00:00:00',
    `review_status` = 'PUBLISHED',
    `updated_at` = NOW(6)
WHERE `dedup_key` = 'DOC:FSC:fsc-87188';

-- 3) 2026-08-11 신용정보법 개정 시행
UPDATE `newstart_official_updates`
SET `published_at` = '2026-08-11 00:00:00',
    `review_status` = 'PUBLISHED',
    `updated_at` = NOW(6)
WHERE `dedup_key` = 'DOC:PUBLIC_AGENCY:credit-info-enf-decree-appx2-4-20260811';

-- 4) 2026-09-18 8월말 신청 현황 (REVIEW 유지)
UPDATE `newstart_official_updates`
SET `published_at` = '2026-09-18 00:00:00',
    `updated_at` = NOW(6)
WHERE `dedup_key` = 'TITLE_DATE:NEWSTART_FUND:새출발기금 채무조정 신청 현황(26.8월말 기준):2026-09-18';

-- 5) 캠코 매입형/중개형 구조 안내 (상시, featured로 상단 고정)
UPDATE `newstart_official_updates`
SET `review_status` = 'PUBLISHED',
    `featured` = TRUE,
    `updated_at` = NOW(6)
WHERE `dedup_key` = 'URL:https://www.kamco.or.kr/portal/contents.do?mId=0203030000';
