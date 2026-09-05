package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 실제 페이지 진입 이벤트 로그 — 1건 = 1 PV.
 * 정적 리소스/API 호출은 저장 대상에서 제외한다({@code SiteVisitFilter}가 응답 Content-Type으로 판별).
 * UV(순 방문자)는 {@code visitorKey} distinct count로 계산한다:
 * 로그인 "U:{userId}", 비로그인 "V:{visitor UUID}". 기존 "S:{session id}" 행도 유지한다.
 * IP 등 개인정보는 저장하지 않는다.
 */
@Entity
@Table(name = "site_page_visits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SitePageVisit extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "site_page_visit_id")
  private Long sitePageVisitId;

  @Column(name = "visit_date", nullable = false)
  private LocalDate visitDate;

  @Column(name = "visited_at", nullable = false)
  private LocalDateTime visitedAt;

  @Column(name = "is_wiki", nullable = false)
  private boolean wiki;

  @Column(name = "path", nullable = false, length = 255)
  private String path;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "role", nullable = false, length = 20)
  private String role;

  @Column(name = "display_name", nullable = false, length = 60)
  private String displayName;

  @Column(name = "visitor_key", nullable = false, length = 64)
  private String visitorKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "service_area", nullable = false, length = 16)
  private AnalyticsServiceArea serviceArea;

  @Enumerated(EnumType.STRING)
  @Column(name = "traffic_kind", nullable = false, length = 10)
  private AnalyticsTrafficKind trafficKind;

  private SitePageVisit(LocalDate visitDate, LocalDateTime visitedAt, boolean wiki, String path,
      Long userId, String role, String displayName, String visitorKey,
      AnalyticsServiceArea serviceArea, AnalyticsTrafficKind trafficKind) {
    this.visitDate = visitDate;
    this.visitedAt = visitedAt;
    this.wiki = wiki;
    this.path = path;
    this.userId = userId;
    this.role = role;
    this.displayName = displayName;
    this.visitorKey = visitorKey;
    this.serviceArea = serviceArea;
    this.trafficKind = trafficKind;
  }

  public static SitePageVisit of(LocalDate visitDate, LocalDateTime visitedAt, boolean wiki, String path,
      Long userId, String role, String displayName, String visitorKey) {
    return of(visitDate, visitedAt, wiki, path, userId, role, displayName, visitorKey,
        wiki ? AnalyticsServiceArea.WIKI : AnalyticsServiceArea.OTHER, AnalyticsTrafficKind.HUMAN);
  }

  public static SitePageVisit of(LocalDate visitDate, LocalDateTime visitedAt, boolean wiki, String path,
      Long userId, String role, String displayName, String visitorKey,
      AnalyticsServiceArea serviceArea, AnalyticsTrafficKind trafficKind) {
    return new SitePageVisit(visitDate, visitedAt, wiki, path, userId, role, displayName, visitorKey,
        serviceArea, trafficKind);
  }
}
