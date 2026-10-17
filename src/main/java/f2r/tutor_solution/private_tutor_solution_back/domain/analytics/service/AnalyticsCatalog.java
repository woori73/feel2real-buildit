package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Analytics 서비스·페이지 분류표. 저장된 방문 경로(path)만으로 분류하므로 과거 데이터까지 소급 적용된다(DB 변경 없음).
 * 같은 규칙에서 Java 분류({@link #serviceOf})와 SQL CASE({@link #serviceCaseSql})를 만들어 두 결과가 어긋나지 않게 한다.
 *
 * <p>UV는 서비스·페이지 그룹마다 따로 센다 — 한 방문자가 여러 서비스를 쓰면 각 서비스에 1명씩 들어가므로
 * 서비스별 UV를 더한 값은 전체 UV가 아니다(화면에 명시).
 */
public final class AnalyticsCatalog {

  public enum Service {
    HOME("메인 홈페이지"), BUILDIT("BuildIt 홈페이지 빌더"), EASY("EASY 장부"), WEMADE("위메이드"), CHOONGGI("충기"),
    NAESIN_WIKI("내신위키"), TESTPREP("TESTPREP"), ACADEMYCRAFT("AcademyCraft"),
    OTHER_SERVICE("기타 등록 서비스"), ADMIN_CONSOLE("관리자 화면(통계 제외)"), UNCLASSIFIED("미분류");

    private final String label;

    Service(String label) {
      this.label = label;
    }

    public String label() {
      return label;
    }
  }

  public enum PageGroup {
    HOME_MAIN("메인 홈페이지"), SERVICE_INTRO("서비스 소개·첫 화면"), EXAM_MAKING("시험 제작"), CONSULT("상담신청"),
    SERVICE_DETAIL("개별 서비스 페이지"), OTHER("기타(로그인·회원 등)");

    private final String label;

    PageGroup(String label) {
      this.label = label;
    }

    public String label() {
      return label;
    }
  }

  /** 가격·상품 안내 전용 페이지는 현재 없다(홈 '내신 예상문제 20,000원' 영역은 섹션 지표 대상). */
  public static final List<String> PAGES_WITHOUT_ROUTE = List.of("가격 및 상품 안내");

  private record Rule(Service service, String... prefixes) {}

  // 위에서부터 먼저 맞는 규칙. 접두는 "경로 == p 또는 p/로 시작"으로 비교한다.
  private static final List<Rule> RULES = List.of(
      new Rule(Service.ADMIN_CONSOLE, "/admin"),
      new Rule(Service.BUILDIT, "/builder", "/tutor/academy", "/academies", "/academy-posts", "/subdomain-home", "/lessons"),
      new Rule(Service.EASY, "/easy", "/ledger", "/tutor/easy"),
      new Rule(Service.WEMADE, "/wemade"),
      new Rule(Service.CHOONGGI, "/choonggi"),
      new Rule(Service.NAESIN_WIKI, "/problem-generator"),
      new Rule(Service.TESTPREP, "/testprep"),
      new Rule(Service.ACADEMYCRAFT, "/lms"),
      new Rule(Service.OTHER_SERVICE, "/designcraft", "/exam-analysis", "/chart", "/wiki", "/newstart", "/admissions"));

  /** 서비스의 첫 화면(루트) 경로. */
  private static final List<String> SERVICE_ROOTS = List.of(
      "/builder", "/designcraft", "/problem-generator", "/exam-analysis", "/ledger", "/easy", "/testprep", "/wemade",
      "/choonggi", "/chart", "/wiki", "/newstart", "/admissions/guide");

  private static final List<String> EXAM_MAKING_PREFIXES = List.of("/problem-generator", "/exam-analysis");

  private AnalyticsCatalog() {}

  public static Service serviceOf(String path) {
    if (path == null || path.isBlank()) return Service.UNCLASSIFIED;
    if (path.equals("/") || path.equals("/home")) return Service.HOME;
    for (Rule r : RULES) {
      for (String p : r.prefixes()) {
        if (matches(path, p)) return r.service();
      }
    }
    return Service.UNCLASSIFIED;
  }

  public static PageGroup pageGroupOf(String path) {
    if (path == null || path.isBlank()) return PageGroup.OTHER;
    if (path.equals("/") || path.equals("/home")) return PageGroup.HOME_MAIN;
    if (matches(path, "/admissions") || path.startsWith("/newstart/consult")) return PageGroup.CONSULT;
    for (String p : EXAM_MAKING_PREFIXES) {
      if (matches(path, p)) return PageGroup.EXAM_MAKING;
    }
    Service s = serviceOf(path);
    if (s == Service.ADMIN_CONSOLE || s == Service.UNCLASSIFIED) return PageGroup.OTHER;
    String clean = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    return SERVICE_ROOTS.contains(clean) ? PageGroup.SERVICE_INTRO : PageGroup.SERVICE_DETAIL;
  }

  private static boolean matches(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + "/");
  }

  // ───────────── SQL (같은 규칙에서 생성, 상수만 사용) ─────────────

  private static String cond(String col, String prefix, boolean wordMid) {
    return wordMid
        ? "(" + col + " = '" + prefix + "' OR " + col + " LIKE '" + prefix + "%')"
        : "(" + col + " = '" + prefix + "' OR " + col + " LIKE '" + prefix + "/%')";
  }

  /** path 컬럼을 서비스 이름(enum name)으로 바꾸는 CASE 식. */
  public static String serviceCaseSql(String col) {
    List<String> whens = new ArrayList<>();
    whens.add("WHEN " + col + " IN ('/', '/home') THEN '" + Service.HOME.name() + "'");
    for (Rule r : RULES) {
      List<String> conds = new ArrayList<>();
      for (String p : r.prefixes()) conds.add(cond(col, p, false));
      whens.add("WHEN " + String.join(" OR ", conds) + " THEN '" + r.service().name() + "'");
    }
    return "CASE " + String.join(" ", whens) + " ELSE '" + Service.UNCLASSIFIED.name() + "' END";
  }

  /** path 컬럼을 페이지 그룹 이름으로 바꾸는 CASE 식. */
  public static String pageGroupCaseSql(String col) {
    List<String> whens = new ArrayList<>();
    whens.add("WHEN " + col + " IN ('/', '/home') THEN '" + PageGroup.HOME_MAIN.name() + "'");
    // 상담신청: /admissions 이하 + /newstart/consult* (consultation 등 단어 중간까지의 접두)
    whens.add("WHEN " + cond(col, "/admissions", false) + " OR " + cond(col, "/newstart/consult", true)
        + " THEN '" + PageGroup.CONSULT.name() + "'");
    List<String> exam = new ArrayList<>();
    for (String p : EXAM_MAKING_PREFIXES) exam.add(cond(col, p, false));
    whens.add("WHEN " + String.join(" OR ", exam) + " THEN '" + PageGroup.EXAM_MAKING.name() + "'");
    whens.add("WHEN " + cond(col, "/admin", false) + " THEN '" + PageGroup.OTHER.name() + "'");
    List<String> roots = new ArrayList<>();
    for (String r : SERVICE_ROOTS) roots.add("'" + r + "'");
    whens.add("WHEN " + col + " IN (" + String.join(", ", roots) + ") THEN '" + PageGroup.SERVICE_INTRO.name() + "'");
    List<String> detail = new ArrayList<>();
    for (Rule r : RULES) {
      if (r.service() == Service.ADMIN_CONSOLE) continue;
      for (String p : r.prefixes()) detail.add(cond(col, p, false));
    }
    whens.add("WHEN " + String.join(" OR ", detail) + " THEN '" + PageGroup.SERVICE_DETAIL.name() + "'");
    return "CASE " + String.join(" ", whens) + " ELSE '" + PageGroup.OTHER.name() + "' END";
  }
}
