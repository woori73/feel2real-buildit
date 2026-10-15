-- initial seed 107건 실측 오버플로: application_type 최장 40자(5948), business_status 최장 49자(5957)
-- varchar(30) 유지 시 INSERT 실패(Data too long) → 전체 트랜잭션 롤백. 두 컬럼만 VARCHAR(255)로 확장.
ALTER TABLE newstart_empirical_cases
    MODIFY COLUMN application_type VARCHAR(255),
    MODIFY COLUMN business_status  VARCHAR(255);
