package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto;

import java.time.LocalDateTime;

/**
 * 상담 메시지 표시 뷰. {@code authorRole}은 MEMBER|OPERATOR|AI_RESEARCH의 enum명.
 *
 * <p>회원 스레드에는 서비스가 AI_RESEARCH를 원천 제외하므로 이 뷰에 AI_RESEARCH가 실려도
 * 회원 응답에는 나타나지 않는다(AI_RESEARCH는 운영자 스레드에만 포함, §5).
 */
public record ConsultationMessageView(
    Long messageId, String authorRole, String content, LocalDateTime createdAt) {}
