package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 학습지(V1137 easy_worksheets). 지우면 휴지통(deletedAt)으로 가고, 휴지통에서 복구하거나 영구 삭제한다. */
@Entity
@Table(name = "easy_worksheets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Worksheet {

  /** 자료 상태: 작성 중 / 사용 중 / 숨김(만든 사람과 관리자만 봄). */
  public enum Status { DRAFT, ACTIVE, HIDDEN }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "worksheet_id")
  private Long worksheetId;

  @Column(name = "business_id", nullable = false)
  private Long businessId;

  @Column(name = "owner_user_id", nullable = false)
  private Long ownerUserId;

  @Column(name = "owner_name", nullable = false, length = 50)
  private String ownerName;

  @Column(name = "title", nullable = false, length = 120)
  private String title;

  @Column(name = "description", length = 500)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private Status status;

  @Column(name = "question_count", nullable = false)
  private int questionCount;

  @Column(name = "kind_label", nullable = false, length = 20)
  private String kindLabel = "";

  @Column(name = "grade_label", nullable = false, length = 40)
  private String gradeLabel = "";

  @Column(name = "scope_summary", nullable = false, length = 255)
  private String scopeSummary = "";

  /** 인쇄 단 수(1 또는 2). */
  @Column(name = "layout_columns", nullable = false)
  private int layoutColumns = 2;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Column(name = "deleted_by")
  private Long deletedBy;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public Worksheet(Long businessId, Long ownerUserId, String ownerName, String title, String description,
      Status status, int questionCount, LocalDateTime now) {
    this.businessId = businessId;
    this.ownerUserId = ownerUserId;
    this.ownerName = ownerName == null ? "" : ownerName;
    this.title = title;
    this.description = description;
    this.status = status;
    this.questionCount = questionCount;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** 목록에 보일 타입·학년·요약과 인쇄 단 수. 저장할 때마다 문항에서 다시 계산해 넣는다. */
  public void describe(String kindLabel, String gradeLabel, String scopeSummary, int layoutColumns) {
    this.kindLabel = kindLabel;
    this.gradeLabel = gradeLabel;
    this.scopeSummary = scopeSummary;
    this.layoutColumns = layoutColumns == 1 ? 1 : 2;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void edit(String title, String description, Status status, int questionCount, LocalDateTime now) {
    this.title = title;
    this.description = description;
    this.status = status;
    this.questionCount = questionCount;
    this.updatedAt = now;
  }

  public void moveToTrash(Long by, LocalDateTime now) {
    if (deletedAt == null) {
      this.deletedAt = now;
      this.deletedBy = by;
    }
  }

  public void restore(LocalDateTime now) {
    this.deletedAt = null;
    this.deletedBy = null;
    this.updatedAt = now;
  }
}
