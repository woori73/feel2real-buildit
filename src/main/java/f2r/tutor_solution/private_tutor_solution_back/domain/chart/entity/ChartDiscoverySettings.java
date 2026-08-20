package f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chart_discovery_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChartDiscoverySettings extends BaseEntity {

  public static final String SINGLETON_ID = "default";

  @Id
  @Column(name = "id", length = 40)
  private String id;

  @Column(name = "search_zone", nullable = false, length = 60)
  private String searchZone;

  @Lob
  @Column(name = "include_activities", nullable = false, columnDefinition = "TEXT")
  private String includeActivities;

  @Lob
  @Column(name = "excluded_targets", nullable = false, columnDefinition = "TEXT")
  private String excludedTargets;

  @Column(name = "candidate_limit", nullable = false)
  private int candidateLimit;

  @Column(name = "deep_search_enabled", nullable = false)
  private boolean deepSearchEnabled;

  @Lob
  @Column(name = "extra_instruction", nullable = false, columnDefinition = "TEXT")
  private String extraInstruction;

  private ChartDiscoverySettings(String id) {
    this.id = id;
    restoreDefaults();
  }

  public static ChartDiscoverySettings defaults() {
    return new ChartDiscoverySettings(SINGLETON_ID);
  }

  public void update(
      String searchZone,
      String includeActivities,
      String excludedTargets,
      int candidateLimit,
      boolean deepSearchEnabled,
      String extraInstruction) {
    this.searchZone = requireText(searchZone, "검색권역은 필수입니다.");
    ChartDiscoveryZone.fromId(this.searchZone);
    this.includeActivities = requireText(includeActivities, "포함할 활동은 필수입니다.");
    this.excludedTargets = requireText(excludedTargets, "제외대상은 필수입니다.");
    this.candidateLimit = Math.max(1, Math.min(candidateLimit, 10));
    this.deepSearchEnabled = deepSearchEnabled;
    this.extraInstruction = extraInstruction == null ? "" : extraInstruction.trim();
  }

  public void restoreDefaults() {
    this.searchZone = ChartDiscoveryZone.APGUJEONG_SINSA.getId();
    this.includeActivities = "수업, 내신, 시험분석, 교육칼럼, YouTube, Shorts";
    this.excludedTargets = "대형학원, 전국 프랜차이즈, 체인 학원, 인강 중심 강사, 스타강사, 채용공고, 단순 광고/순위글";
    this.candidateLimit = 10;
    this.deepSearchEnabled = true;
    this.extraInstruction = "지역에서 실제 수업활동을 공개하는 원장/강사를 우선 발굴하세요. 검색 품질 확인 전 강남구 전체로 확대하지 마세요.";
  }

  private String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }
}
