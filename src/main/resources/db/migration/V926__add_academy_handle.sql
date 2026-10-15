-- 학원 공개페이지 서브도메인 URL(#172) 1단계.
-- 영문 handle 컬럼 추가. {handle}.feel2real.co.kr 서브도메인 라우팅 식별자.
-- nullable + UNIQUE: 기존 Academy는 handle 없이 경로형(/academies/{slug})으로 유지(무영향).
-- 전 Academy handle 백필은 별도 마이그레이션(retire 전제조건).
ALTER TABLE academies ADD COLUMN handle VARCHAR(30) NULL UNIQUE;
