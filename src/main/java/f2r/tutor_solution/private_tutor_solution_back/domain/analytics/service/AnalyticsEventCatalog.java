package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 서버가 받아들이는 이벤트의 허용 목록(단일 기준). 클라이언트가 보낸 이벤트명·대상·페이지는 여기 있는 것만 저장하며,
 * 페이지 경로·섹션은 클라이언트 값이 아니라 이 카탈로그가 정한다. 새 섹션·버튼은 여기에 추가해야 집계된다.
 */
public final class AnalyticsEventCatalog {

  public enum Type { SECTION_VIEW, CLICK, SERVICE_MOVE, FLOW_START }

  /** page 키 → 서버가 저장하는 경로 표기. */
  public static final Map<String, String> PAGES = Map.of("home", "/", "academy_public", "/academies/*");

  public record Def(Type type, String target, String page, String section, String label) {}

  private static final Map<String, Def> DEFS = new LinkedHashMap<>();

  private static void add(Type type, String target, String page, String section, String label) {
    DEFS.put(key(type, page, target), new Def(type, target, page, section, label));
  }

  static {
    // ── 홈페이지(landing) 실제 섹션(landing.html의 id) ──
    add(Type.SECTION_VIEW, "agentHero", "home", null, "메인 배너(Agent)");
    add(Type.SECTION_VIEW, "homeTop", "home", null, "홈페이지 제작 입력(한 문장 시작)");
    add(Type.SECTION_VIEW, "svcTiles", "home", null, "서비스 카드(8개)");
    add(Type.SECTION_VIEW, "nation", "home", null, "전국 무대 확장 소개");
    add(Type.SECTION_VIEW, "hero", "home", null, "간편 제작 소개");
    add(Type.SECTION_VIEW, "wiki", "home", null, "내신 예상문제 20,000원 안내");
    add(Type.SECTION_VIEW, "services", "home", null, "현재 오픈한 서비스·기능 소개");
    add(Type.SECTION_VIEW, "soon", "home", null, "출시 예정 서비스");
    add(Type.SECTION_VIEW, "chart-showcase", "home", null, "BUILDIT CHART 소개");
    add(Type.SECTION_VIEW, "faq", "home", null, "자주 묻는 질문");
    add(Type.SECTION_VIEW, "cta", "home", null, "하단 만들기 CTA");

    // ── 홈페이지 개편(2026-10-27): 기말고사 이용권·과정·적중 사례·고객 요청·브랜드 ──
    add(Type.SECTION_VIEW, "passHero", "home", null, "기말고사 이용권 히어로");
    add(Type.SECTION_VIEW, "passProcess", "home", null, "제출→제작→공급→피드백 과정");
    add(Type.SECTION_VIEW, "exam-hit", "home", null, "압구정중 적중 사례");
    add(Type.SECTION_VIEW, "agentRequest", "home", null, "서수니·세븐 고객 요청");
    add(Type.SECTION_VIEW, "brand", "home", null, "AI AGENT 브랜드 영역");

    // ── 버튼 클릭(클릭률 = 같은 방문에서 이 섹션을 본 뒤 클릭한 방문 ÷ 섹션 노출 방문) ──
    add(Type.CLICK, "nav_contact", "home", null, "상단 '문의하기'");
    add(Type.CLICK, "home_start", "home", "homeTop", "한 문장 시작(제작 입력)");
    add(Type.CLICK, "wiki_kakao", "home", "wiki", "카톡 문의(예상문제)");
    add(Type.CLICK, "wiki_mail", "home", "wiki", "이메일 문의(예상문제)");
    add(Type.CLICK, "cta_start", "home", "cta", "하단 '만들기'");
    add(Type.CLICK, "pass_kakao", "home", "passHero", "이용권 카톡 신청 안내");
    add(Type.CLICK, "pass_mail", "home", "passHero", "이용권 이메일 신청 안내");
    add(Type.CLICK, "dashboard_cta", "home", "services", "무료 대시보드 바로가기");
    add(Type.CLICK, "hit_blog", "home", "exam-hit", "적중 사례 블로그");

    // ── 서비스 이동(대상=서비스 키) ──
    for (String[] s : new String[][] {
        {"builder", "홈페이지 빌더", "svcTiles"}, {"exam_analysis", "기출분석", "svcTiles"}, {"wiki", "모의고사위키", "svcTiles"},
        {"chart", "CHART", "svcTiles"}, {"testprep", "TESTPREP", "svcTiles"}, {"ledger", "이지장부", "svcTiles"},
        {"designcraft", "DesignCraft", "svcTiles"}, {"admissions", "입시상담", "svcTiles"}}) {
      add(Type.SERVICE_MOVE, s[0], "home", s[2], s[1] + " 이동");
    }

    // ── 흐름 시작(상담신청 완료는 클라이언트 이벤트가 아니라 academy_inquiries에서 센다) ──
    add(Type.FLOW_START, "consult_contact", "home", null, "문의하기 시작(홈 모달)");
    add(Type.FLOW_START, "exam_request", "home", "wiki", "예상문제 제작 문의 시작");
    add(Type.FLOW_START, "home_builder", "home", "homeTop", "홈페이지 제작 시작");
    add(Type.FLOW_START, "pass_apply", "home", "passHero", "이용권 신청 안내 시작");
    add(Type.FLOW_START, "consult_academy", "academy_public", null, "학원 상담신청 시작");
  }

  private AnalyticsEventCatalog() {}

  private static String key(Type type, String page, String target) {
    return type.name() + "|" + page + "|" + target;
  }

  public static Optional<Def> find(String type, String page, String target) {
    if (type == null || page == null || target == null) return Optional.empty();
    try {
      return Optional.ofNullable(DEFS.get(key(Type.valueOf(type), page, target)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public static boolean isKnownPage(String page) {
    return page != null && PAGES.containsKey(page);
  }

  public static List<Def> all(Type type) {
    return DEFS.values().stream().filter(d -> d.type() == type).toList();
  }

  public static Set<String> pageKeys() {
    return PAGES.keySet();
  }
}
