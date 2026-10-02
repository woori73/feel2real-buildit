package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto;

import java.time.LocalDateTime;

/**
 * 회원의 상담 목록 한 행(§2 상태 구분 + §7 "새 답변 N건" 표시).
 *
 * @param status enum명(NEW|AWAITING_ANSWER|OPERATOR_ANSWERED|MEMBER_FOLLOWUP).
 * @param statusLabel 사용자용 한글 라벨(새 상담/답변 대기/운영자 답변 완료/회원 추가 질문).
 * @param preview 마지막 메시지 미리보기(짧게 자름).
 * @param newAnswerCount 운영자 답변 중 회원이 아직 못 본 건수.
 */
public record ConsultationSummaryView(
    Long consultationId,
    String status,
    String statusLabel,
    String preview,
    LocalDateTime lastMessageAt,
    long newAnswerCount,
    boolean linkDebt) {}
