package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * NewStart 질문요청 게시판 요청 한 건 ({@code newstart_requests}).
 *
 * <p>[질문·조사 요청]과 [사이트 개선 요청]은 검토 주체와 상태 흐름이 같으므로 한 테이블에
 * {@link NewStartRequestKind}로 담는다. 개선 요청 전용 칼럼({@link #screenLocation},
 * {@link #aToZStep})은 질문 요청에서는 null이다.
 *
 * <p>작성자 userId는 요청 body가 아니라 서버 principal에서만 채워진다(클라이언트 userId 전송 없음).
 * 표시 이름은 저장하지 않고 조회 시점에 User에서 resolve한다.
 *
 * <p>삭제는 soft delete다({@link #markDeleted()}): problem/answer에 채무·기관 정보가 들어올 수 있어
 * Withdraw 요청 시 원문을 물우고 row만 남겨 이력 연결(request_id)을 유지한다.
 */
@Entity
@Table(name = "newstart_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewStartRequest extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "request_id")
  private Long requestId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NewStartRequestKind kind;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(name = "screen_location", length = 120)
  private String screenLocation;

  @Column(name = "a_to_z_step", length = 20)
  private String aToZStep;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String problem;

  @Column(columnDefinition = "TEXT")
  private String proposal;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NewStartRequestStatus status;

  @Column(columnDefinition = "TEXT")
  private String answer;

  @Column(name = "answered_by", length = 50)
  private String answeredBy;

  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  @Column(name = "github_ref", length = 120)
  private String githubRef;

  @Column(name = "resolved_version", length = 40)
  private String resolvedVersion;

  @Column(nullable = false)
  private boolean deleted;

  @Builder
  public NewStartRequest(Long userId, NewStartRequestKind kind, String title, String screenLocation,
      String aToZStep, String problem, String proposal) {
    this.userId = userId;
    this.kind = kind;
    this.title = title;
    this.screenLocation = kind == NewStartRequestKind.SITE_IMPROVEMENT ? screenLocation : null;
    this.aToZStep = kind == NewStartRequestKind.SITE_IMPROVEMENT ? aToZStep : null;
    this.problem = problem;
    this.proposal = proposal;
    this.status = NewStartRequestStatus.RECEIVED;
    this.deleted = false;
  }

  /** 회원의 Withdraw(취소): 원문을 비우고 flag만 세운다. 이력 row는 유지된다. */
  public void markDeleted() {
    this.deleted = true;
    this.title = "";
    this.problem = "";
    this.proposal = null;
  }

  /**
   * 운영자 검토 반영. status가 ANSWERED면 답변 본문·답변자를 기록하고, RESOLVED/REJECTED/DUPLICATE로
   * 끝나면 해결 시각을 기록한다. 상태 이동은 호출자(service)가 검증한다.
   */
  public void review(NewStartRequestStatus status, String answer, String answeredBy) {
    this.status = status;
    if (answer != null && !answer.isBlank()) {
      this.answer = answer;
      this.answeredBy = answeredBy;
    }
    if (status == NewStartRequestStatus.RESOLVED || status == NewStartRequestStatus.REJECTED
        || status == NewStartRequestStatus.DUPLICATE) {
      this.resolvedAt = LocalDateTime.now();
    }
  }

  /** RESOLVED에 한해 반영 정보(GitHub issue/PR 수동 기입 값, 릴리즈 표식)를 적는다. */
  public void markResolved(String githubRef, String resolvedVersion) {
    this.githubRef = githubRef;
    this.resolvedVersion = resolvedVersion;
  }
}
