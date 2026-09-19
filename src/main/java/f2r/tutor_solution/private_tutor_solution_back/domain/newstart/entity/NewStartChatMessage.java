package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * NewStart 회원 채팅 메시지 (공용방 1개, 텍스트 전용 — {@code newstart_chat_messages}).
 *
 * <p>이 데이터는 회원 ↔ 회원 정보교류 전용이다. NewStart AI(질문/답변)와 무관하며,
 * AI 학습·RAG·embedding·요약에 사용하지 않는다(별도 정책/동의 결정 전까지 회원 대화로만 취급).
 *
 * <p>작성자 userId는 절대 브라우저 요청값으로 결정하지 않는다 — WS handshake principal(서버가
 * JWT에서 검증한 값)에서만 채워진다. 표시 이름은 컬럼에 저장하지 않고 조회 시점에
 * {@code User.getDisplayName()}(nickname 우선 → loginId, ADMIN=관리자)으로 resolve한다.
 *
 * <p>삭제는 soft delete({@link #markDeleted()}): 본문을 비우고 flag만 세워 pagination
 * id 안정성을 유지한다.
 */
@Entity
@Table(name = "newstart_chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class NewStartChatMessage extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "message_id")
  private Long messageId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 500)
  private String message;

  @Column(nullable = false)
  private boolean deleted;

  @Builder
  public NewStartChatMessage(Long userId, String message) {
    this.userId = userId;
    this.message = message;
    this.deleted = false;
  }

  /** 본인(또는 ADMIN) 삭제 — 본문은 비운다(삭제 의도 존중), row는 pagination 안정성을 위해 유지. */
  public void markDeleted() {
    this.deleted = true;
    this.message = "";
  }
}
