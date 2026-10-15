package f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공개 BuildIt Wiki(/wiki) 메인 "WIKI LIVE" 한 줄 활동 메시지.
 *
 * <p>작성은 로그인 회원만({@code userId} 는 서버 authenticated principal 로만 기록), 조회는 비회원 포함 공개.
 * {@code displayNameSnapshot} 은 작성 시점의 <b>마스킹된</b> 표시명(예: "김OO")이라 조회 시 {@code users}
 * 조인이 필요 없고, 실명/loginId 로 되돌아갈 위험도 없다. {@code message} 는 서버에서 HTML escape 후 저장한다.
 */
@Entity
@Table(name = "wiki_live_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WikiLiveMessage extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "wiki_live_message_id")
  private Long wikiLiveMessageId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "display_name_snapshot", nullable = false, length = 60)
  private String displayNameSnapshot;

  @Column(name = "message", nullable = false, length = 500)
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private WikiLiveMessageStatus status;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  private WikiLiveMessage(Long userId, String displayNameSnapshot, String message) {
    this.userId = userId;
    this.displayNameSnapshot = displayNameSnapshot;
    this.message = message;
    this.status = WikiLiveMessageStatus.VISIBLE;
  }

  public static WikiLiveMessage of(Long userId, String displayNameSnapshot, String message) {
    return new WikiLiveMessage(userId, displayNameSnapshot, message);
  }

  /** 관리자 숨김. 한 번 숨기면 다시 노출하지 않는다(재노출 경로 없음). */
  public void hide(LocalDateTime at) {
    this.status = WikiLiveMessageStatus.HIDDEN;
    this.deletedAt = at;
  }
}
