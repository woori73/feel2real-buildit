package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsServiceArea;

/** 실제 MVC 공개/운영 화면 route를 Analytics 서비스 영역으로 분류한다. */
public final class AnalyticsServiceAreaClassifier {

  private AnalyticsServiceAreaClassifier() {}

  public static AnalyticsServiceArea classify(String path) {
    if (path == null || path.isBlank()) return AnalyticsServiceArea.OTHER;
    if (path.equals("/") || path.equals("/home")) return AnalyticsServiceArea.HOME;
    if (path.equals("/chart") || path.startsWith("/chart/")) return AnalyticsServiceArea.CHART;
    if (path.equals("/wiki") || path.startsWith("/wiki/")) return AnalyticsServiceArea.WIKI;
    if (isNewStart(path)) return AnalyticsServiceArea.NEWSTART;
    if (isWeb(path)) return AnalyticsServiceArea.WEB;
    if (isAcademy(path)) return AnalyticsServiceArea.ACADEMY;
    return AnalyticsServiceArea.OTHER;
  }

  private static boolean isNewStart(String path) {
    // /admin/newstart(운영자 콘솔)는 회원/방문 트래픽이 아니라 관리 화면이므로 제외한다.
    return path.equals("/newstart") || path.startsWith("/newstart/");
  }

  private static boolean isWeb(String path) {
    return path.equals("/academies")
        || path.startsWith("/academies/")
        || path.equals("/academy-posts")
        || path.startsWith("/academy-posts/")
        || path.equals("/subdomain-home");
  }

  private static boolean isAcademy(String path) {
    return path.equals("/builder")
        || path.equals("/lessons")
        || path.startsWith("/lessons/")
        || path.equals("/tutor/academy")
        || path.startsWith("/tutor/academy/")
        || path.equals("/lms")
        || path.startsWith("/lms/");
  }
}
