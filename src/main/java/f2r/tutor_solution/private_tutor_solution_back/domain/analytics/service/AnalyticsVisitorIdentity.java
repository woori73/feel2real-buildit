package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 방문자 식별·Bot 판별의 단일 기준. 페이지 방문({@code SiteVisitFilter})과 이벤트 수집이 같은 규칙을 쓰도록 한 곳에 둔다.
 * 로그인 "U:{userId}", 비로그인 first-party 쿠키 "V:{uuid}". IP·fingerprint는 쓰지 않는다.
 */
public final class AnalyticsVisitorIdentity {

  public static final String VISITOR_COOKIE_NAME = "F2R_VISITOR";

  private static final Pattern BOT_USER_AGENT = Pattern.compile(
      "(?i)(?:bot|crawler|spider|googlebot|bingbot|yandexbot|duckduckbot|"
          + "facebookexternalhit|twitterbot|slackbot|discordbot|bytespider|gptbot|claudebot)");

  private AnalyticsVisitorIdentity() {}

  public static boolean isBot(String userAgent) {
    return userAgent != null && BOT_USER_AGENT.matcher(userAgent).find();
  }

  /** 요청 쿠키의 유효한 방문자 UUID. 없거나 형식이 틀리면 null. */
  public static String validVisitorId(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return null;
    for (Cookie c : cookies) {
      if (VISITOR_COOKIE_NAME.equals(c.getName()) && isUuid(c.getValue())) return c.getValue();
    }
    return null;
  }

  public static boolean isUuid(String value) {
    try {
      return value != null && UUID.fromString(value).toString().equalsIgnoreCase(value);
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }
}
