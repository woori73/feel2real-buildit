package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto;

import java.util.List;

/**
 * 회원 상담 스레드 상세(§7 과거 대화·답변 시간순). messages는 MEMBER·OPERATOR만 포함 —
 * AI_RESEARCH(운영자 전용)는 서비스가 제외한다.
 *
 * @param atozStep 생성 시점 결정적 A to Z 단계(없으면 null). @param atozTitle/atozUrl 단계명과 이동 링크(없으면 null).
 */
public record ConsultationThreadView(
    Long consultationId,
    String status,
    String statusLabel,
    boolean linkDebt,
    Integer atozStep,
    String atozTitle,
    String atozUrl,
    List<ConsultationMessageView> messages) {}
