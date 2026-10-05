-- V1022: 위메이드 새 대주주 지배구조 (NeoPulse 컨소시엄)
-- NeoPulse -> Neosphere -> Nexpulse -> Mok, Pui Ming 지배체인 + 인물 프로필.
-- 원칙: wemade_* 신규 테이블만 추가. 기존 테이블 변경 없음. destructive 변경 없음.
-- 중복 방지: node_key/person_key/content_hash UNIQUE.

CREATE TABLE IF NOT EXISTS wemade_ownership_nodes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  node_key VARCHAR(100) NOT NULL COMMENT '고정 키: mok_pui_ming | nexpulse | neosphere | cypress | kingnet | shengsong | neopulse | wemade',
  name VARCHAR(200) NOT NULL,
  name_zh VARCHAR(200) NULL COMMENT '중국어 표기 (미확인 시 NULL)',
  node_type VARCHAR(20) NOT NULL COMMENT 'PERSON|COMPANY',
  country VARCHAR(10) NULL COMMENT 'HK|KY|CN|KR',
  stake_pct DECIMAL(10,4) NULL COMMENT '직속 하위 노드 보유 지분율(%)',
  stake_note VARCHAR(1000) NULL,
  role_desc VARCHAR(1000) NULL,
  grade CHAR(1) NOT NULL DEFAULT 'U' COMMENT 'A=공식공시, B=복수신뢰매체, C=단일매체, D=계산값, U=미확인',
  source_name VARCHAR(200) NULL,
  source_url VARCHAR(1000) NULL,
  as_of_date DATE NULL,
  content_hash CHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_won_key (node_key),
  UNIQUE KEY uq_won_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='지배구조 노드 (인물/법인)';

CREATE TABLE IF NOT EXISTS wemade_persons (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  person_key VARCHAR(100) NOT NULL,
  name VARCHAR(200) NOT NULL,
  name_zh VARCHAR(200) NULL COMMENT '중국어 성명 표기 (미확인 시 NULL)',
  bio_ko VARCHAR(3000) NULL,
  education VARCHAR(500) NULL,
  affiliations VARCHAR(1000) NULL,
  past_roles VARCHAR(2000) NULL,
  relation_kingnet VARCHAR(2000) NULL COMMENT '킹넷 및 관계자와의 과거 관계',
  relation_park VARCHAR(2000) NULL COMMENT '박관호 의장과의 기존 관계',
  relation_mir_ip VARCHAR(2000) NULL COMMENT '미르/传奇 IP 관련 과거 이해관계',
  verification_note VARCHAR(2000) NULL COMMENT '교차검증 결과 및 한계',
  grade CHAR(1) NOT NULL DEFAULT 'U' COMMENT 'A=공식공시, B=복수신뢰매체, C=단일매체, D=계산값, U=미확인',
  source_name VARCHAR(200) NULL,
  source_url VARCHAR(1000) NULL,
  as_of_date DATE NULL,
  content_hash CHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_wp_key (person_key),
  UNIQUE KEY uq_wp_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='핵심 인물 심층 프로필 (사실/미확인 분리)';

CREATE TABLE IF NOT EXISTS wemade_ownership_edges (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_key VARCHAR(100) NOT NULL COMMENT '지배 측 노드 키',
  child_key VARCHAR(100) NOT NULL COMMENT '피지배 측 노드 키',
  stake_pct DECIMAL(10,4) NULL COMMENT '보유 지분율(%)',
  note VARCHAR(1000) NULL,
  grade CHAR(1) NOT NULL DEFAULT 'U' COMMENT 'A=공식공시, B=복수신뢰매체, C=단일매체, D=계산값, U=미확인',
  source_name VARCHAR(200) NULL,
  source_url VARCHAR(1000) NULL,
  as_of_date DATE NULL,
  content_hash CHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_woe_hash (content_hash),
  INDEX idx_woe_parent (parent_key),
  INDEX idx_woe_child (child_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='지배관계 간선 (parent -> child)';
