package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Academy 한 곳당 1개의 페이지 레이아웃을 보존한다(academy_id UNIQUE).
 *
 * <p>{@code layoutJson} 은 raw HTML/CSS 가 아닌 allowlist 기반 semantic JSON 만 담는다.
 * Academy 삭제 시 DB FK {@code ON DELETE CASCADE} 로 함께 제거된다.
 */
@Entity
@Table(name = "academy_page_layouts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyPageLayout extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "layout_id")
  private Long layoutId;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "academy_id", nullable = false, unique = true)
  private Academy academy;

  @Column(name = "layout_version", nullable = false)
  private int layoutVersion = 1;

  @Lob
  @Column(name = "layout_json", nullable = false, columnDefinition = "LONGTEXT")
  private String layoutJson;

  @Builder
  public AcademyPageLayout(Academy academy, Integer layoutVersion, String layoutJson) {
    this.academy = academy;
    this.layoutVersion = layoutVersion != null ? layoutVersion : 1;
    this.layoutJson = layoutJson;
  }

  /** 기존 레이아웃을 새 schema 내용으로 덮어쓴다(upsert 수정 경로). academy 연결은 불변. */
  public void updateLayout(int layoutVersion, String layoutJson) {
    this.layoutVersion = layoutVersion;
    this.layoutJson = layoutJson;
  }
}
