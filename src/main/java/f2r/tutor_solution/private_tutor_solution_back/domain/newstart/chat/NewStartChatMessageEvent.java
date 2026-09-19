package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartChatMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 회원 채팅 WebSocket 이벤트 payload(브라우저로 내려가는 JSON).
 *
 * <ul>
 *   <li>{@code type="new"} — 새 메시지. 방에 접속한 모든 세션에 broadcast.</li>
 *   <li>{@code type="deleted"} — 삭제 이벤트. 클라이언트는 해당 id 메시지를
 *       "삭제된 메시지입니다."로 표시 교체한다.</li>
 * </ul>
 *
 * <p>{@code mine} 같은 수신자별 값을 만들지 않는다 — 클라이언트는 {@code senderUserId}를
 * 자기 userId(/api/auth/me 응답)와 비교해 내 메시지를 판단한다. 서버는 각 사용자의
 * 표시 이름을 서버에서만 결정해 내려주며, 클라이언트가 보낸 닉네임은 절대 신뢰하지 않는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewStartChatMessageEvent(
    String type,
    Long id,
    Long senderUserId,
    String displayName,
    String message,
    boolean deleted,
    String createdAt) {

  public static NewStartChatMessageEvent created(NewStartChatMessage saved, String displayName) {
    return new NewStartChatMessageEvent(
        "new",
        saved.getMessageId(),
        saved.getUserId(),
        displayName,
        saved.getMessage(),
        false,
        iso(saved.getCreatedAt()));
  }

  public static NewStartChatMessageEvent deleted(Long messageId) {
    return new NewStartChatMessageEvent("deleted", messageId, null, null, "", true, null);
  }

  public static NewStartChatMessageEvent error(String errorMessage) {
    return new NewStartChatMessageEvent("error", null, null, null, errorMessage, false, null);
  }

  private static String iso(LocalDateTime time) {
    return time == null ? null : time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }
}
