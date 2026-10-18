package f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 내신 영어 예상문제 의뢰(1회 20,000원, 40문항). 홈페이지 '예상문제 의뢰하기'가 만드는 접수 건이다.
 *
 * <p>길이·필수 검증은 {@link #create}가 단일 지점으로 담당한다. 결제는 접수 후 운영자가 안내한다.
 */
@Entity
@Table(name = "exam_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExamRequest extends BaseEntity {

  public static final int QUESTION_COUNT = 40;
  public static final int PRICE_KRW = 20_000;
  public static final int SCHOOL_MAX = 60;
  public static final int GRADE_MAX = 20;
  public static final int ROUND_MAX = 40;
  public static final int SCOPE_MAX = 1000;
  public static final int CONTACT_MAX = 60;
  public static final int MEMO_MAX = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "school_name", nullable = false, length = SCHOOL_MAX)
  private String schoolName;

  @Column(name = "grade", nullable = false, length = GRADE_MAX)
  private String grade;

  @Column(name = "exam_round", nullable = false, length = ROUND_MAX)
  private String examRound;

  @Column(name = "exam_scope", nullable = false, length = SCOPE_MAX)
  private String examScope;

  @Column(name = "contact", nullable = false, length = CONTACT_MAX)
  private String contact;

  @Column(name = "memo", length = MEMO_MAX)
  private String memo;

  @Column(name = "question_count", nullable = false)
  private int questionCount = QUESTION_COUNT;

  @Column(name = "price_krw", nullable = false)
  private int priceKrw = PRICE_KRW;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ExamRequestStatus status = ExamRequestStatus.RECEIVED;

  public static ExamRequest create(
      Long userId, String schoolName, String grade, String examRound,
      String examScope, String contact, String memo) {
    if (userId == null) {
      throw new IllegalArgumentException("로그인이 필요합니다.");
    }
    ExamRequest r = new ExamRequest();
    r.userId = userId;
    r.schoolName = required(schoolName, "학교명", SCHOOL_MAX);
    r.grade = required(grade, "학년", GRADE_MAX);
    r.examRound = required(examRound, "시험 회차", ROUND_MAX);
    r.examScope = required(examScope, "시험 범위", SCOPE_MAX);
    r.contact = required(contact, "연락처", CONTACT_MAX);
    r.memo = optional(memo, "요청 사항", MEMO_MAX);
    return r;
  }

  public void changeStatus(ExamRequestStatus next) {
    if (next == null) {
      throw new IllegalArgumentException("상태가 필요합니다.");
    }
    this.status = next;
  }

  private static String required(String value, String label, int max) {
    String v = value == null ? "" : value.trim();
    if (v.isEmpty()) {
      throw new IllegalArgumentException(label + "을(를) 입력해 주세요.");
    }
    if (v.length() > max) {
      throw new IllegalArgumentException(label + "은(는) " + max + "자 이내로 입력해 주세요.");
    }
    return v;
  }

  private static String optional(String value, String label, int max) {
    String v = value == null ? "" : value.trim();
    if (v.length() > max) {
      throw new IllegalArgumentException(label + "은(는) " + max + "자 이내로 입력해 주세요.");
    }
    return v.isEmpty() ? null : v;
  }
}
