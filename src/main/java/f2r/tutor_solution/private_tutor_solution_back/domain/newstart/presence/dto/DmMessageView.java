package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.dto;

import java.time.LocalDateTime;

/** 쪽지 1건. 송수신자 표시는 서버 resolve 별명이며 식별자가 아니다. */
public record DmMessageView(
    Long messageId,
    Long senderUserId,
    String senderNickname,
    Long receiverUserId,
    String receiverNickname,
    String message,
    boolean read,
    LocalDateTime createdAt) {
}
