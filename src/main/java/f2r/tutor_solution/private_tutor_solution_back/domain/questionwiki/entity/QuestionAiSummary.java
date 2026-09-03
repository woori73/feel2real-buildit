package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 가 문항 THREAD 전체를 종합한 요약. 버전형(v1 → v2 → v3).
 *
 * <p>spec §9·§10: AI 는 원문을 자동 수정하지 않는다. AI Suggestion → Human Approval → New Version.
 * 이번 PR 에는 write 경로가 없다(빈 테이블). Context 응답에 최신 {@code APPROVED} 요약을 포함만 한다.
 */
@Entity
@Table(name = "question_ai_summaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionAiSummary extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "summary_id")
  private Long summaryId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "question_wiki_id", nullable = false)
  private QuestionWiki questionWiki;

  @Column(name = "version", nullable = false)
  private int version;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private QuestionAiSummaryStatus status;

  @Lob
  @Column(name = "summary_text", nullable = false, columnDefinition = "MEDIUMTEXT")
  private String summaryText;

  @Column(name = "thread_post_count")
  private Integer threadPostCount;

  @Builder
  public QuestionAiSummary(QuestionWiki questionWiki, int version, QuestionAiSummaryStatus status,
      String summaryText, Integer threadPostCount) {
    this.questionWiki = questionWiki;
    this.version = version;
    this.status = status == null ? QuestionAiSummaryStatus.DRAFT : status;
    this.summaryText = summaryText;
    this.threadPostCount = threadPostCount;
  }
}
