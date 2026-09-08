package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 내신 LIVE FRAME 의 (학교, 학년, 역할) scene 에 연결된 기존 {@code academy_videos} 참조(Phase 4c).
 *
 * <p>영상 본체·metadata 의 Source of Truth 는 {@code academy_videos} 다. 이 행은 "어느 캠페인의
 * 어느 학교/학년/역할 scene 에 그 영상을 쓸지" 와 공개 여부/정렬만 담는다(V969 wiki source
 * exposure 와 같은 참조형 계층). campaign_id · academy_video_id 둘 다 FK ON DELETE CASCADE.
 */
@Entity
@Table(name = "midterm_live_video_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MidtermLiveVideoLink extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "link_id")
  private Long linkId;

  @Column(name = "campaign_id", nullable = false)
  private Long campaignId;

  @Column(name = "academy_video_id", nullable = false)
  private Long academyVideoId;

  @Column(name = "school_name", nullable = false, length = 100)
  private String schoolName;

  @Column(name = "grade", nullable = false)
  private int grade;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private MidtermLiveVideoRole role;

  @Column(name = "visible", nullable = false)
  private boolean visible;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Builder
  private MidtermLiveVideoLink(Long campaignId, Long academyVideoId, String schoolName,
      int grade, MidtermLiveVideoRole role, boolean visible, int sortOrder) {
    this.campaignId = campaignId;
    this.academyVideoId = academyVideoId;
    this.schoolName = normalizeSchool(schoolName);
    this.grade = grade;
    this.role = role;
    this.visible = visible;
    this.sortOrder = sortOrder;
    validate();
  }

  /** 대상(학교/학년/역할) 재지정 + 공개/정렬. school/grade 는 FRAME 과 exact match 되어야 한다. */
  public void update(String schoolName, int grade, MidtermLiveVideoRole role,
      boolean visible, int sortOrder) {
    this.schoolName = normalizeSchool(schoolName);
    this.grade = grade;
    this.role = role;
    this.visible = visible;
    this.sortOrder = sortOrder;
    validate();
  }

  /** 공개 여부 / 정렬만 변경. */
  public void updateExposure(boolean visible, int sortOrder) {
    this.visible = visible;
    this.sortOrder = sortOrder;
  }

  private void validate() {
    if (campaignId == null || academyVideoId == null) {
      throw new IllegalArgumentException("campaign / academy video 참조가 필요합니다.");
    }
    if (schoolName == null || schoolName.isBlank()) {
      throw new IllegalArgumentException("학교명을 입력해 주세요.");
    }
    if (grade < 1 || grade > 9) {
      throw new IllegalArgumentException("학년은 1~9 사이여야 합니다.");
    }
    if (role == null) {
      throw new IllegalArgumentException("역할을 선택해 주세요.");
    }
  }

  /** 새 normalization layer 를 만들지 않는다(§7) — 앞뒤 공백만 제거하고 그대로 저장한다. */
  private static String normalizeSchool(String value) {
    return value == null ? null : value.trim();
  }
}
