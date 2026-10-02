package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAtozCaseService.AtozCase;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberDebtStatusService.DebtStatusView;
import java.util.List;

/**
 * 운영자 상담 상세 — 한 화면에 §3의 6가지를 모은다(회원 닉네임/연결 허용한 채무상황/현재 A to Z 단계/
 * 이번 질문/이전 대화/관련 지피지기 사례). 운영자가 회원 개인정보를 찾아다니지 않게 상담에 필요한 것만 담는다.
 *
 * <p>{@code messages}는 AI_RESEARCH를 포함(운영자만 본다). {@code debt}는 회원이 linkDebt로 연결을
 * 허용했을 때만 채워지고, 아니면 null이다(§1·§8: 최소 정보만).
 */
public record AdminConsultationThreadView(
    Long consultationId,
    String memberNickname,
    String status,
    String statusLabel,
    Integer atozStep,
    String atozTitle,
    String atozUrl,
    DebtStatusView debt,
    List<AtozCase> relatedCases,
    List<ConsultationMessageView> messages) {}
