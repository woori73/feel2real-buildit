package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 대화 수정 턴이 바꿀 수 있는 디자인 요소의 허용 목록과 사람이 읽는 이름.
 *
 * <p>AI(LLM)도 규칙 해석기도 이 목록 밖의 값은 만들 수 없다 — 서버 검증({@link AssistantPatchApplier})이 같은 목록을 쓴다.
 * 값의 의미는 프런트 {@code academy_brand.js}(DESIGN_PRESETS/PRESETS)와 같다. 순수 정적 상수라 컨테이너 없이 테스트한다.
 */
final class AssistantDesignCatalog {

  private AssistantDesignCatalog() {}

  /** 디자인 프리셋 id → {이름, 분위기 설명}. id는 {@link AcademyAiCreateService#VALID_PRESETS}와 같다. */
  static final Map<String, String[]> PRESETS = new LinkedHashMap<>();

  static {
    PRESETS.put("EXAM", new String[] {"입시학원형", "신뢰와 실적을 강조하는 네이비·골드의 전문적인 분위기"});
    PRESETS.put("ENGLISH", new String[] {"영어회화형", "밝고 친근한 블루·오렌지의 회화·어학 분위기"});
    PRESETS.put("ARTS", new String[] {"예체능형", "다크 갤러리 무드, 바이올렛 포인트"});
    PRESETS.put("STUDY", new String[] {"공부방형", "따뜻한 베이지·브라운의 소규모 공부방 분위기"});
    PRESETS.put("ONLINE", new String[] {"온라인학원형", "모던한 네이비·시안의 테크 분위기"});
    PRESETS.put("MINIMAL", new String[] {"미니멀형", "여백과 타이포그래피 중심의 절제된 흑백 디자인"});
    PRESETS.put("DARK", new String[] {"다크형", "다크 모드 기반의 시크하고 모던한 디자인"});
    PRESETS.put("WARM", new String[] {"감성형", "따뜻하고 스토리텔링 중심의 앰버 톤 감성 디자인"});
    PRESETS.put("PHOTO", new String[] {"포토형", "사진·이미지 중심의 시각적인 디자인"});
    PRESETS.put("DYNAMIC", new String[] {"다이나믹형", "보라·핑크·오렌지의 역동적이고 활기찬 디자인"});
  }

  /** 타이포그래피 프리셋 id → 이름. */
  static final Map<String, String> TYPOGRAPHY = Map.of(
      "MODERN", "모던(젊고 현대적)", "PREMIUM", "프리미엄(고급·신뢰, 명조 제목)", "CLEAN", "클린(정돈·전문)",
      "FRIENDLY", "프렌들리(부드럽고 편안)", "ACADEMIC", "아카데믹(학구적·진지)");

  static final Set<String> FONTS = Set.of("PRETENDARD", "SUIT", "NOTO_SANS_KR", "NOTO_SERIF_KR");
  static final Set<String> HERO_LAYOUTS = Set.of("center", "split", "visual", "gradient");
  static final Set<String> CARD_STYLES = Set.of("bordered", "rounded", "media", "minimal");

  /** 색상 키 → 역할 설명(프롬프트용). */
  static final Map<String, String> COLOR_KEYS = new LinkedHashMap<>();

  static {
    COLOR_KEYS.put("colorPrimary", "주색(버튼·강조·제목 포인트)");
    COLOR_KEYS.put("colorSecondary", "보조색");
    COLOR_KEYS.put("colorAccent", "강조색");
    COLOR_KEYS.put("colorBg", "페이지 배경");
    COLOR_KEYS.put("colorBgAlt", "보조 배경(카드·구획)");
    COLOR_KEYS.put("colorText", "본문 글자색");
    COLOR_KEYS.put("colorTextLight", "보조 글자색");
    COLOR_KEYS.put("colorBorder", "테두리");
    COLOR_KEYS.put("colorHeroBg", "상단 히어로 배경");
    COLOR_KEYS.put("colorHeroText", "상단 히어로 글자색");
  }

  /** 프리셋을 바꿀 때 지우는 사용자 지정 값 — 새 프리셋 고유의 색·레이아웃·글꼴이 보이게 한다. */
  static final List<String> STYLE_OVERRIDE_KEYS = List.of(
      "colorPrimary", "colorSecondary", "colorAccent", "colorBg", "colorBgAlt", "colorText", "colorTextLight",
      "colorBorder", "colorHeroBg", "colorHeroText", "heroLayout", "cardStyle", "typographyPreset",
      "headingFont", "bodyFont");

  /** 순서·표시를 바꿀 수 있는 섹션(히어로는 항상 맨 위 고정, 준비 중 섹션은 제외). */
  static final Map<String, String> SECTIONS = new LinkedHashMap<>();

  static {
    SECTIONS.put("sec-featured", "시즌 프로그램(Featured)");
    SECTIONS.put("sec-intro", "학원 소개");
    SECTIONS.put("sec-principal", "원장 소개");
    SECTIONS.put("sec-teachers", "강사진");
    SECTIONS.put("sec-philosophy", "학습 철학");
    SECTIONS.put("sec-videos", "수업 영상");
    SECTIONS.put("sec-minilectures", "미니 강의");
    SECTIONS.put("sec-lessons", "수업·커리큘럼");
    SECTIONS.put("sec-digitaltwin", "AI 교육 과정");
    SECTIONS.put("sec-cta", "상담 신청");
    SECTIONS.put("sec-social", "블로그·지도 연동");
  }

  /** 새로 켜려면 명시적 true가 필요한 섹션(기존 학원 보호용 opt-in). */
  static final Set<String> OPT_IN_SECTIONS = Set.of("sec-featured", "sec-digitaltwin", "sec-minilectures", "sec-cta");

  /** 문구 키 → 사람이 읽는 이름과 소속 섹션. */
  static final Map<String, String[]> TEXT_KEYS = new LinkedHashMap<>();

  static {
    TEXT_KEYS.put("introHeading", new String[] {"소개 제목", "sec-intro"});
    TEXT_KEYS.put("introMotto", new String[] {"소개 한마디", "sec-intro"});
    TEXT_KEYS.put("introBodySecondary", new String[] {"소개 보조 문단", "sec-intro"});
    TEXT_KEYS.put("principalDesc", new String[] {"원장 소개 문구", "sec-principal"});
    TEXT_KEYS.put("principalQuote", new String[] {"원장 인용문", "sec-principal"});
    TEXT_KEYS.put("philosophyTitle", new String[] {"학습 철학 제목", "sec-philosophy"});
    TEXT_KEYS.put("philosophyKeyword", new String[] {"학습 철학 키워드", "sec-philosophy"});
    TEXT_KEYS.put("philosophyItem1", new String[] {"학습 철학 1", "sec-philosophy"});
    TEXT_KEYS.put("philosophyItem2", new String[] {"학습 철학 2", "sec-philosophy"});
    TEXT_KEYS.put("philosophyItem3", new String[] {"학습 철학 3", "sec-philosophy"});
    TEXT_KEYS.put("philosophyItem4", new String[] {"학습 철학 4", "sec-philosophy"});
    TEXT_KEYS.put("featuredEyebrow", new String[] {"시즌 프로그램 소제목", "sec-featured"});
    TEXT_KEYS.put("featuredTitle", new String[] {"시즌 프로그램 제목", "sec-featured"});
    TEXT_KEYS.put("featuredDescription", new String[] {"시즌 프로그램 설명", "sec-featured"});
    TEXT_KEYS.put("featuredCtaLabel", new String[] {"시즌 프로그램 버튼 문구", "sec-featured"});
    TEXT_KEYS.put("videosSectionTitle", new String[] {"수업 영상 제목", "sec-videos"});
    TEXT_KEYS.put("socialSectionTitle", new String[] {"블로그·지도 제목", "sec-social"});
    TEXT_KEYS.put("miniLecturesSectionTitle", new String[] {"미니 강의 제목", "sec-minilectures"});
    TEXT_KEYS.put("ctaTitle", new String[] {"상담 신청 제목", "sec-cta"});
    TEXT_KEYS.put("ctaSubtitle", new String[] {"상담 신청 설명", "sec-cta"});
  }

  static String sectionOfTextKey(String key) {
    String[] v = TEXT_KEYS.get(key);
    return v == null ? null : v[1];
  }

  static String presetLabel(String id) {
    String[] v = PRESETS.get(id);
    return v == null ? id : v[0];
  }
}
