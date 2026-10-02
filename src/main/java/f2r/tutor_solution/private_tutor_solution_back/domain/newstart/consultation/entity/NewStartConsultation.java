package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 감사회원 1:1 비공개 상담 "건"(스레드). 기존 쪽지({@code newstart_direct_messages})와 별개이며
 * 쪽지는 그대로 둔다 — 쪽지는 단발·단일복사본·회원 상호 DM이라 상담 스레드로 재사용하지 않는다.
 *
 * <p>소유 회원은 {@code member_user_id}로만 저장한다(닉네임·loginId·이메일 저장 금지, 표시명은
 * 조회 시 {@code NewStartMemberProfileService}로 resolve). 접근 권한은 회원 본인 + ADMIN뿐이며
 * 서비스/리포지토리에서 강제한다(HARD GATE, 설계 §8).
 *
 * <p>{@code linkDebt}가 true일 때만 운영자 화면에 회원이 저장한 채무상황을 붙인다(§1·§3).
 * 채무 원본은 이 테이블에 복사하지 않고 조회 시 {@code NewStartMemberDebtStatusService}로 읽는다.
 */
@Entity
@Table(name = "newstart_consultations",
    indexes = {
        @Index(name = "idx_newstart_consult_member", columnList = "member_user_id, consultation_id"),
        @Index(name = "idx_newstart_consult_status", columnList = "status, last_message_at")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewStartConsultation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "consultation_id")
  private Long consultationId;

  @Column(name = "member_user_id", nullable = false)
  private Long memberUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 30, nullable = false)
  @Builder.Default
  private ConsultationStatus status = ConsultationStatus.NEW;

  @Column(name = "link_debt", nullable = false)
  @Builder.Default
  private boolean linkDebt = false;

  /** 생성 시점 결정적 A to Z 단계(0..13). 신호가 없으면 null(§7 — 단계를 임의로 만들지 않는다). */
  @Column(name = "atoz_step")
  private Integer atozStep;

  @Column(name = "member_last_seen_message_id")
  private Long memberLastSeenMessageId;

  @Column(name = "operator_last_seen_message_id")
  private Long operatorLastSeenMessageId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "last_message_at", nullable = false)
  private LocalDateTime lastMessageAt;

  /** 메시지가 추가될 때마다 정렬 시각을 갱신한다. */
  public void touch(LocalDateTime now) {
    this.lastMessageAt = now;
  }

  /**
   * 회원 메시지 추가 시 전이. 운영자 답변(OPERATOR_ANSWERED) 뒤 회원이 다시 쓰면 MEMBER_FOLLOWUP,
   * 그 외(NEW/AWAITING_ANSWER/MEMBER_FOLLOWUP)에는 상태를 유지한다(아직 운영자 답변 대기).
   */
  public void onMemberMessage() {
    if (this.status == ConsultationStatus.OPERATOR_ANSWERED) {
      this.status = ConsultationStatus.MEMBER_FOLLOWUP;
    }
  }

  /** 운영자가 상담을 열었을 때 전이. 새 상담(NEW)이면 답변 대기(AWAITING_ANSWER)로, 나머지는 유지. */
  public void onOperatorView() {
    if (this.status == ConsultationStatus.NEW) {
      this.status = ConsultationStatus.AWAITING_ANSWER;
    }
  }

  /** 운영자 답변 전송 시 전이 — 항상 답변 완료. */
  public void onOperatorAnswer() {
    this.status = ConsultationStatus.OPERATOR_ANSWERED;
  }

  public void markMemberSeen(Long messageId) {
    this.memberLastSeenMessageId = messageId;
  }

  public void markOperatorSeen(Long messageId) {
    this.operatorLastSeenMessageId = messageId;
  }
}
