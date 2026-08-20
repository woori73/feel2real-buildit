package f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chart_candidates", uniqueConstraints = {
    @UniqueConstraint(name = "uk_chart_candidate_zone_person_source", columnNames = {"zone_id", "person_key", "source_url_hash"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChartCandidate extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "candidate_id")
  private Long candidateId;

  @Column(name = "zone_id", nullable = false, length = 60)
  private String zoneId;

  @Column(name = "person_key", nullable = false, length = 180)
  private String personKey;

  @Column(name = "name", nullable = false, length = 80)
  private String name;

  @Column(name = "role", nullable = false, length = 40)
  private String role;

  @Column(name = "academy_name", length = 160)
  private String academyName;

  @Column(name = "activity_region", nullable = false, length = 120)
  private String activityRegion;

  @Column(name = "content_type", nullable = false, length = 80)
  private String contentType;

  @Lob
  @Column(name = "content_summary", nullable = false, columnDefinition = "TEXT")
  private String contentSummary;

  @Column(name = "source_url", nullable = false, length = 1000)
  private String sourceUrl;

  @Column(name = "source_url_hash", nullable = false, length = 64)
  private String sourceUrlHash;

  @Column(name = "source_title", length = 500)
  private String sourceTitle;

  @Lob
  @Column(name = "discovery_reason", nullable = false, columnDefinition = "TEXT")
  private String discoveryReason;

  @Lob
  @Column(name = "deep_search_result", nullable = false, columnDefinition = "TEXT")
  private String deepSearchResult;

  @Lob
  @Column(name = "exclusion_check", nullable = false, columnDefinition = "TEXT")
  private String exclusionCheck;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_status", nullable = false, length = 30)
  private ChartVerificationStatus verificationStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private ChartCandidateStatus status = ChartCandidateStatus.DISCOVERED;

  @Column(name = "google_query", nullable = false, length = 500)
  private String googleQuery;

  @Column(name = "discovered_at", nullable = false)
  private LocalDateTime discoveredAt;

  private ChartCandidate(
      String zoneId, String personKey, String name, String role, String academyName,
      String activityRegion, String contentType, String contentSummary, String sourceUrl,
      String sourceTitle, String discoveryReason, String deepSearchResult, String exclusionCheck,
      ChartVerificationStatus verificationStatus, String googleQuery, LocalDateTime discoveredAt) {
    this.zoneId = zoneId;
    this.personKey = personKey;
    this.name = name;
    this.role = role;
    this.academyName = blankToNull(academyName);
    this.activityRegion = activityRegion;
    this.contentType = contentType;
    this.contentSummary = contentSummary;
    this.sourceUrl = sourceUrl;
    this.sourceUrlHash = sha256(sourceUrl);
    this.sourceUrlHash = sha256(this.sourceUrl);
    this.sourceTitle = blankToNull(sourceTitle);
    this.discoveryReason = discoveryReason;
    this.deepSearchResult = deepSearchResult == null ? "" : deepSearchResult;
    this.exclusionCheck = exclusionCheck;
    this.verificationStatus = verificationStatus;
    this.googleQuery = googleQuery;
    this.discoveredAt = discoveredAt;
  }

  public static ChartCandidate discovered(
      String zoneId, String personKey, String name, String role, String academyName,
      String activityRegion, String contentType, String contentSummary, String sourceUrl,
      String sourceTitle, String discoveryReason, String deepSearchResult, String exclusionCheck,
      ChartVerificationStatus verificationStatus, String googleQuery, LocalDateTime discoveredAt) {
    return new ChartCandidate(zoneId, personKey, name, role, academyName, activityRegion, contentType,
        contentSummary, sourceUrl, sourceTitle, discoveryReason, deepSearchResult, exclusionCheck,
        verificationStatus, googleQuery, discoveredAt);
  }

  public void refresh(
      String role, String academyName, String contentType, String contentSummary, String sourceTitle,
      String discoveryReason, String deepSearchResult, String exclusionCheck,
      ChartVerificationStatus verificationStatus, String googleQuery, LocalDateTime discoveredAt) {
    if (role != null && !role.isBlank()) this.role = role.trim();
    if (academyName != null && !academyName.isBlank()) this.academyName = academyName.trim();
    this.contentType = contentType;
    this.contentSummary = contentSummary;
    this.sourceUrlHash = sha256(this.sourceUrl);
    this.sourceTitle = blankToNull(sourceTitle);
    this.discoveryReason = discoveryReason;
    this.deepSearchResult = deepSearchResult == null ? "" : deepSearchResult;
    this.exclusionCheck = exclusionCheck;
    this.verificationStatus = verificationStatus;
    this.googleQuery = googleQuery;
    this.discoveredAt = discoveredAt;
  }

  public void approve() {
    this.status = ChartCandidateStatus.APPROVED;
  }

  public void reject() {
    this.status = ChartCandidateStatus.REJECTED;
  }

  private static String sha256(String value) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest((value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder();
      for (byte b : bytes) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
