package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모의고사 위키 회차(연도·월) 메타 (V1093).
 *
 * <p>메인 회차(is_current)를 관리자가 지정하면 그 회차가 /wiki 메인이 되고, 나머지 공개 회차는
 * 아카이브에서 열람한다. 자료 자체는 {@link WikiExamOriginal}이 exam_year/exam_month로 회차에 속한다.
 * 배너는 정적 경로(/images/...) 또는 업로드 후 서빙 경로를 banner_path에 그대로 보관한다.
 */
@Entity
@Table(name = "wiki_exam_rounds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WikiExamRound extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "wiki_exam_round_id")
  private Long wikiExamRoundId;

  @Column(name = "exam_year", nullable = false)
  private Integer examYear;

  @Column(name = "exam_month", nullable = false)
  private Integer examMonth;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 300)
  private String subtitle;

  @Column(name = "banner_path", length = 500)
  private String bannerPath;

  @Column(name = "is_current", nullable = false)
  private boolean current;

  @Column(name = "is_published", nullable = false)
  private boolean published;

  @Column(name = "is_deleted", nullable = false)
  private boolean deleted;

  @Builder
  public WikiExamRound(Integer examYear, Integer examMonth, String title, String subtitle,
      String bannerPath, boolean current, boolean published) {
    this.examYear = examYear;
    this.examMonth = examMonth;
    this.title = title;
    this.subtitle = subtitle;
    this.bannerPath = bannerPath;
    this.current = current;
    this.published = published;
    this.deleted = false;
  }

  public void updateMetadata(Integer examYear, Integer examMonth, String title, String subtitle,
      String bannerPath, boolean published) {
    this.examYear = examYear;
    this.examMonth = examMonth;
    this.title = title;
    this.subtitle = subtitle;
    if (bannerPath != null) {
      this.bannerPath = bannerPath;
    }
    this.published = published;
  }

  public void setCurrent(boolean current) {
    this.current = current;
  }

  public void setPublished(boolean published) {
    this.published = published;
  }

  public void softDelete() {
    this.deleted = true;
    this.published = false;
    this.current = false;
  }
}
