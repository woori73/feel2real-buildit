package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.dto;

import java.time.LocalDateTime;

/** 접속 회원 1행. nickname은 서버 resolve 값이며 식별자가 아니다. */
public record PresenceMemberView(
    Long userId,
    String displayNickname,
    LocalDateTime lastSeenAt) {
}
