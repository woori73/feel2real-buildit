package f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.entity.WikiLiveMessage;
import java.time.LocalDateTime;

/**
 * WIKI LIVE 한 줄 메시지(공개). 개인정보 없음 — 마스킹 표시명 · 본문(escape 완료) · 작성 시각만.
 * user id / role / loginId 는 담지 않는다.
 */
public record WikiLiveMessageResponse(
    Long id,
    String displayName,
    String message,
    LocalDateTime createdAt
) {
  public static WikiLiveMessageResponse of(WikiLiveMessage m) {
    return new WikiLiveMessageResponse(
        m.getWikiLiveMessageId(), m.getDisplayNameSnapshot(), m.getMessage(), m.getCreatedAt());
  }
}
