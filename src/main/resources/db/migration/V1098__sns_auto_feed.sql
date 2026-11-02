-- 인스타 자동 피드(docs/handoff/instagram-auto-feed/HANDOFF.md). 새 테이블 6개만 추가한다(기존 테이블 변경 없음).
-- 되돌리기: db/rollback/V1098__sns_auto_feed_down.sql (Flyway 경로 밖, 수동 실행용).
-- 테이블 이름은 다른 기능과 겹치지 않게 sns_ 접두어를 붙인다.

CREATE TABLE sns_accounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  platform VARCHAR(20) NOT NULL,
  ig_user_id VARCHAR(64) NULL,
  username VARCHAR(100) NULL,
  access_token_enc TEXT NULL,
  token_expires_at DATETIME NULL,
  token_refreshed_at DATETIME NULL,
  status VARCHAR(20) NOT NULL,
  last_error VARCHAR(500) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);

CREATE TABLE sns_brand_presets (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  industry TEXT NULL,
  audience TEXT NULL,
  purpose TEXT NULL,
  instructions TEXT NULL,
  fixed_text TEXT NULL,
  fixed_text_position VARCHAR(10) NOT NULL,
  banned_phrases TEXT NULL,
  language VARCHAR(10) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);

CREATE TABLE sns_reference_images (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  label VARCHAR(100) NOT NULL,
  brand VARCHAR(100) NULL,
  file_path VARCHAR(500) NOT NULL,
  content_type VARCHAR(50) NOT NULL,
  approved BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);

CREATE TABLE sns_automation_rules (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(150) NOT NULL,
  account_id BIGINT NULL,
  brand_id BIGINT NOT NULL,
  reference_image_id BIGINT NULL,
  image_prompt TEXT NOT NULL,
  image_style VARCHAR(30) NOT NULL,
  aspect_ratio VARCHAR(10) NOT NULL,
  image_count INT NOT NULL DEFAULT 1,
  fixed_hashtags VARCHAR(300) NULL,
  ai_hashtags BOOLEAN NOT NULL DEFAULT TRUE,
  schedule_type VARCHAR(20) NOT NULL,
  schedule_days VARCHAR(40) NULL,
  run_time TIME NOT NULL,
  start_date DATE NOT NULL,
  save_mode VARCHAR(10) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  monthly_budget_krw INT NULL,
  last_run_on DATE NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  CONSTRAINT fk_sns_rule_account FOREIGN KEY (account_id) REFERENCES sns_accounts(id),
  CONSTRAINT fk_sns_rule_brand FOREIGN KEY (brand_id) REFERENCES sns_brand_presets(id),
  CONSTRAINT fk_sns_rule_reference FOREIGN KEY (reference_image_id) REFERENCES sns_reference_images(id)
);

CREATE TABLE sns_posts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_id BIGINT NOT NULL,
  trial BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(20) NOT NULL,
  topic VARCHAR(200) NULL,
  caption TEXT NULL,
  hashtags VARCHAR(1000) NULL,
  caption_status VARCHAR(20) NOT NULL,
  caption_attempts INT NOT NULL DEFAULT 0,
  image_paths TEXT NULL,
  image_prompt TEXT NULL,
  image_status VARCHAR(20) NOT NULL,
  image_attempts INT NOT NULL DEFAULT 0,
  image_claimed_at DATETIME NULL,
  media_token VARCHAR(64) NULL,
  scheduled_at DATETIME NULL,
  publish_attempts INT NOT NULL DEFAULT 0,
  published_at DATETIME NULL,
  ig_media_id VARCHAR(64) NULL,
  fail_reason VARCHAR(500) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  CONSTRAINT fk_sns_post_rule FOREIGN KEY (rule_id) REFERENCES sns_automation_rules(id),
  CONSTRAINT uk_sns_post_media_token UNIQUE (media_token)
);
CREATE INDEX ix_sns_posts_status ON sns_posts(status, created_at);
CREATE INDEX ix_sns_posts_image_status ON sns_posts(image_status, created_at);

CREATE TABLE sns_generation_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_id BIGINT NULL,
  post_id BIGINT NULL,
  kind VARCHAR(20) NOT NULL,
  model VARCHAR(100) NULL,
  prompt TEXT NULL,
  cost_krw INT NOT NULL DEFAULT 0,
  duration_ms BIGINT NOT NULL DEFAULT 0,
  success BOOLEAN NOT NULL,
  error_message VARCHAR(500) NULL,
  created_at DATETIME NOT NULL
);
CREATE INDEX ix_sns_logs_created ON sns_generation_logs(created_at);
CREATE INDEX ix_sns_logs_rule_created ON sns_generation_logs(rule_id, created_at);

-- 로보MD에서 쓰던 3개 규칙을 옮긴다(HANDOFF §9). 계정은 Meta 연결 후 화면에서 고른다(account_id NULL).
-- 규칙은 꺼진 상태(enabled=FALSE)로 넣고, 관리자가 시험 생성으로 얼굴을 확인한 뒤 켠다.
INSERT INTO sns_reference_images (id, label, brand, file_path, content_type, approved, created_at, updated_at) VALUES
 (1, '세븐(F2R 영어학습)', 'F2R 영어학습', 'classpath:sns/reference/f2r-seven-square.jpg', 'image/jpeg', TRUE, NOW(), NOW()),
 (2, '새출발 여성 인물(전자책)', '새출발기금 전자책·카페', 'classpath:sns/reference/saechulbal-woman-book.jpg', 'image/jpeg', TRUE, NOW(), NOW()),
 (3, '서수니·세븐(AI AGENT)', 'AI AGENT 드라마 × F2R', 'classpath:sns/reference/f2r-seven-suni-wide.jpg', 'image/jpeg', TRUE, NOW(), NOW());

INSERT INTO sns_brand_presets (id, name, industry, audience, purpose, instructions, fixed_text, fixed_text_position, banned_phrases, language, created_at, updated_at) VALUES
 (1, 'F2R 영어학습',
  '서울 압구정 영어학원에서 만든 중고등 영어 학습 서비스 Feel to Real(F2R). 내신·수능 영어 맞춤 문제, 문법 교재, 복습 자료를 제공한다.',
  '중학생·고등학생과 학부모. 내신과 수능 영어를 준비하며 문법·어휘·구문을 정리하고 싶어 한다.',
  '정보 전달과 서비스 홍보. 매일 영어 학습 팁 하나(문법 포인트, 헷갈리는 어휘, 구문 해석, 내신 빈출 유형)를 알려 주고, F2R 시험기간 맞춤 문제 이용권(2주 160문항)을 꾸준히 안내한다.',
  '- 글 하나에 학습 포인트는 하나만 다룬다.\n- 예문은 짧고 정확한 영어 문장으로, 해석을 함께 적는다.\n- 마지막에 한두 줄로, 이런 유형을 내 학교 내신에 맞춘 문제로 연습할 수 있다는 점을 자연스럽게 연결한다.\n- 가격·문항 수·기간은 하단 고정 문구에 들어가므로 본문에는 새 숫자를 만들어 쓰지 않는다. 할인·이벤트도 지어내지 않는다.\n- 쉬운 말로 차분하게 설명하고, 과장되거나 자극적인 표현(최강, 필살, 무조건 등)은 쓰지 않는다.\n- 이모지는 최대 2개. 6~10줄 정도로 짧게 쓴다.',
  '📌 F2R 시험기간 맞춤 문제 이용권\n2주 160문항 · 20,000원\n자세한 내용은 feel2real.co.kr',
  'BOTTOM', '최강\n필살\n무조건', 'ko', NOW(), NOW()),
 (2, '새출발기금 전자책·카페',
  '새출발기금(자영업자·소상공인 채무조정) 정보를 나누는 네이버 카페(2024년 10월 개설)와, 운영자의 실제 경험을 담은 전자책 《새출발 2억 2천》(네이버 스마트스토어 판매, epub·휴대폰 PDF 제공).',
  '빚 문제로 힘든 자영업자·소상공인, 새출발기금 신청을 고민하거나 준비 중인 사람, 이미 진행 중이라 정보가 필요한 사람.',
  '정보 전달과 홍보. 새출발기금을 준비하는 사람에게 도움이 되는 준비 팁과 경험을 하나씩 전하고, 네이버 카페 가입과 전자책 《새출발 2억 2천》을 꾸준히 안내한다.',
  '- 글 하나에 주제는 하나만 다룬다(예: 서류 준비, 신청 전 확인할 것, 진행 중 마음가짐, 완제한 회원 이야기).\n- 감면율, 승인 가능성, 신청 자격 기준 같은 구체적인 수치나 조건은 지어내지 않는다. 제도 내용은 일반적인 설명만 하고, 최신 기준은 공식 창구에서 확인하라고 안내한다.\n- ''무조건 탕감'', ''100% 승인'' 처럼 결과를 약속하는 표현은 쓰지 않는다.\n- 힘든 상황의 사람을 배려해 따뜻하고 차분하게 쓴다. 과장되거나 자극적인 표현은 쓰지 않는다.\n- 마지막에 한두 줄로 카페에서 같은 상황의 사람들과 정보를 나눌 수 있고, 전자책에 실제 진행 과정이 정리돼 있다는 점을 자연스럽게 연결한다.\n- 가격이나 할인은 쓰지 않는다.\n- 이모지는 최대 2개, 6~10줄 정도.',
  '📌 새출발기금 정보 나눔 네이버 카페\n📘 전자책 《새출발 2억 2천》\n카페와 전자책은 프로필 링크에서 확인하세요.',
  'BOTTOM', '무조건 탕감\n100% 승인\n무조건', 'ko', NOW(), NOW()),
 (3, 'AI AGENT 드라마 × F2R',
  'Feel to Real(F2R)이 만드는 SF 액션 AI 드라마 《AI AGENT》. 주인공은 여전사 서수니와 전략가 바이오 AI 에이전트 세븐. 두 캐릭터는 F2R 홈페이지(feel2real.co.kr)의 AI 비서로도 나온다. F2R은 중고등 영어 내신·수능 맞춤 문제 서비스다.',
  'SF·액션·AI 이야기를 좋아하는 10대~30대, 중고등학생과 학부모.',
  '홍보. 드라마 《AI AGENT》의 세계관과 캐릭터(서수니, 세븐)를 조금씩 소개해 관심을 모으고, 이 드라마를 만드는 Feel to Real과 홈페이지를 자연스럽게 알린다.',
  '- 글 하나에 한 가지만 소개한다(서수니의 성격, 세븐의 역할, 두 사람의 관계, AI와 인간에 대한 질문 등).\n- 서수니: 평소에는 귀엽고 친근하지만 작전에서는 강렬한 여전사. 무적이 아니라 위기를 이겨내는 사람.\n- 세븐: 차분하고 지적인 바이오 AI 에이전트. 길 안내자가 아니라 계획과 재계획을 세우는 전략가.\n- 결말, 악역의 정체·이름, 반전은 절대 쓰지 않는다. 작품에 없는 설정·인물·공개 날짜를 지어내지 않는다.\n- 실존 기업·국가 이름을 악역과 연결하지 않는다.\n- 과장되거나 가벼운 광고 말투는 피하고, 짧고 여운 있는 문장으로 쓴다.\n- 마지막에 한 줄로, F2R 홈페이지에서 세븐·서수니를 AI 비서로 만날 수 있다는 점을 연결한다.\n- 이모지는 최대 2개, 5~9줄 정도.',
  '🎬 SF 액션 드라마 《AI AGENT》\nmade by Feel to Real · feel2real.co.kr',
  'BOTTOM', NULL, 'ko', NOW(), NOW());

INSERT INTO sns_automation_rules (id, title, account_id, brand_id, reference_image_id, image_prompt, image_style, aspect_ratio, image_count, fixed_hashtags, ai_hashtags, schedule_type, schedule_days, run_time, start_date, save_mode, enabled, monthly_budget_krw, last_run_on, created_at, updated_at) VALUES
 (1, 'F2R 영어학습 매일 피드', NULL, 1, 1,
  '첨부한 이미지와 같은 인물(검은 머리의 한국인 남자, AI 캐릭터 세븐)과 같은 분위기로 만든다. 얼굴 생김새는 첨부 이미지와 똑같이 유지한다. 짙은 남색 배경에 은은한 디지털 격자 무늬와 푸른 조명. 인물은 오른쪽에 두고 왼쪽은 비워 둔다. 매번 옷(검은 니트, 셔츠, 수트 등), 표정, 자세, 영어 교재·태블릿 같은 소품만 조금씩 다르게 한다. 이미지 안에 글자나 로고는 넣지 않는다.',
  'PHOTO', '1:1', 1, '#Feel2Real #영어내신', TRUE, 'DAILY', NULL, '19:00:00', '2026-11-02', 'LIST', FALSE, NULL, NULL, NOW(), NOW()),
 (2, '새출발기금 전자책·카페 매일 피드', NULL, 2, 2,
  '첨부한 이미지와 같은 여성 인물과 같은 분위기로 만든다. 얼굴 생김새는 첨부 이미지와 똑같이 유지한다. 밝은 크림색 배경에 남색 곡선 포인트, 따뜻하고 차분한 조명. 인물은 남색 재킷과 흰 셔츠 차림으로 신뢰감 있고 다정한 인상. 매번 표정, 자세, 장면(책상에서 서류를 정리하는 모습, 창가에서 책을 읽는 모습, 휴대폰을 보는 모습 등)만 조금씩 다르게 한다. 하늘색·남색 표지의 책이 함께 보여도 좋지만, 이미지 안에 글자나 로고는 넣지 않는다.',
  'PHOTO', '1:1', 1, '#새출발기금 #채무조정', TRUE, 'DAILY', NULL, '19:00:00', '2026-11-02', 'LIST', FALSE, NULL, NULL, NOW(), NOW()),
 (3, 'AI AGENT 드라마 × F2R 홍보', NULL, 3, 3,
  '첨부한 이미지의 두 인물(여성 서수니, 남성 AI 에이전트 세븐)을 같은 얼굴 그대로 유지해 SF 액션 드라마의 한 장면처럼 만든다. 어두운 남색 도시의 밤, 푸른 홀로그램 화면과 디지털 격자 빛, 영화 같은 조명. 세븐은 차분하고 지적인 표정으로 홀로그램 상태창을 띄우고, 서수니는 작전을 앞둔 단호한 눈빛의 여전사. 매번 장면(작전 회의, 옥상, 휴대폰 속 홀로그램, 도시 야경 등)과 구도만 다르게 한다. 이미지 안에 글자나 로고는 넣지 않는다.',
  'PHOTO', '1:1', 1, '#AIAGENT #Feel2Real', TRUE, 'DAILY', NULL, '19:00:00', '2026-11-02', 'LIST', FALSE, NULL, NULL, NOW(), NOW());
