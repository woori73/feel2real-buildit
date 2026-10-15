package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto;

import java.time.LocalDateTime;

/**
 * 운영자 상담함 목록 한 행(§3). 회원 닉네임만 표시(실명·연락처 노출 없음).
 *
 * @param memberQuestionUnread 회원이 보낸 메시지 중 운영자가 아직 못 본 건수(처리 우선순위).
 */
public record AdminConsultationSummaryView(
    Long consultationId,
    String memberNickname,
    String status,
    String statusLabel,
    String preview,
    LocalDateTime lastMessageAt,
    long memberQuestionUnread,
    boolean linkDebt) {}
