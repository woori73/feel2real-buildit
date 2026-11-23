package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tp_questions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TpQuestion extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "question_id")
  private Long questionId;

  @Column(name = "exam_id", nullable = false)
  private Long examId;

  @Column(name = "section", nullable = false, length = 10)
  private String section;

  @Column(name = "task_type", nullable = false, length = 40)
  private String taskType;

  @Column(name = "prompt", nullable = false, columnDefinition = "TEXT")
  private String prompt;

  @Column(name = "body_text", columnDefinition = "MEDIUMTEXT")
  private String bodyText;

  @Column(name = "audio_mode", nullable = false, length = 10)
  private String audioMode;

  @Column(name = "choices_json", columnDefinition = "TEXT")
  private String choicesJson;

  @Column(name = "answer_key", length = 500)
  private String answerKey;

  @Column(name = "auto_score", nullable = false)
  private Boolean autoScore;

  @Column(name = "explanation", columnDefinition = "TEXT")
  private String explanation;

  @Column(name = "order_no", nullable = false)
  private Integer orderNo;

  /** 문항 배점(내신 실전 연습). SAT·TOEFL 문항은 null. */
  @Column(name = "points")
  private Integer points;

  /** 해설란 한글 도움 자료 JSON {translation, vocab[[영어, 뜻]], solution, strategy}. 없으면 null. */
  @Column(name = "ko_help_json", columnDefinition = "MEDIUMTEXT")
  private String koHelpJson;

  /** 제작 공장 게시(FactoryExamPublisher)가 쓰는 자동 채점 객관식 문항(내신 계약: 선택지 5개, 정답 A~E). */
  public static TpQuestion create(Long examId, String section, String taskType, String prompt, String bodyText,
      String choicesJson, String answerKey, String explanation, int orderNo, int points) {
    TpQuestion q = new TpQuestion();
    q.examId = examId;
    q.section = section;
    q.taskType = taskType;
    q.prompt = prompt;
    q.bodyText = bodyText;
    q.audioMode = "NONE";
    q.choicesJson = choicesJson;
    q.answerKey = answerKey;
    q.autoScore = true;
    q.explanation = explanation;
    q.orderNo = orderNo;
    q.points = points;
    return q;
  }
}
