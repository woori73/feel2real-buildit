package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공개 BuildIt Wiki 의 문항(Question Wiki). 하나의 모의고사 문항을 장기적으로 진화하는
 * 하나의 Knowledge Unit 으로 취급한다.
 *
 * <p>모든 하위 데이터(THREAD·변형문제·기출·자료 링크·AI 요약)는 이 엔티티의 안정적
 * {@code question_wiki_id} 로 연결된다 — 향후 AI 가 문항 전체 Context 를 한 번에 읽을 수 있게.
 */
@Entity
@Table(name = "question_wikis")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionWiki extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "question_wiki_id")
  private Long questionWikiId;

  @Column(name = "subject", nullable = false, length = 20)
  private String subject;

  @Column(name = "grade", nullable = false)
  private int grade;

  @Column(name = "exam_year", nullable = false)
  private int examYear;

  @Column(name = "exam_label", nullable = false, length = 100)
  private String examLabel;

  @Column(name = "question_number", nullable = false)
  private int questionNumber;

  /** 공동지문 묶음 키. NULL 이면 단독 지문. 같은 값이면 같은 지문을 공유하는 문항 세트. */
  @Column(name = "passage_group_key", length = 60)
  private String passageGroupKey;

  @Column(name = "title", length = 200)
  private String title;

  @Column(name = "is_visible", nullable = false)
  private boolean visible = true;

  @Builder
  public QuestionWiki(String subject, int grade, int examYear, String examLabel, int questionNumber,
      String passageGroupKey, String title, boolean visible) {
    this.subject = subject == null || subject.isBlank() ? "ENGLISH" : subject;
    this.grade = grade;
    this.examYear = examYear;
    this.examLabel = examLabel;
    this.questionNumber = questionNumber;
    this.passageGroupKey = passageGroupKey;
    this.title = title;
    this.visible = visible;
  }

  public void updateTitle(String title) {
    this.title = title;
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  /** "고2 · 2026년 · 9월 전국연합학력평가 · 21번" 형태의 표시용 문맥. */
  public String displayContext() {
    return "고" + grade + " · " + examYear + "년 · " + examLabel + " · " + questionNumber + "번";
  }
}
