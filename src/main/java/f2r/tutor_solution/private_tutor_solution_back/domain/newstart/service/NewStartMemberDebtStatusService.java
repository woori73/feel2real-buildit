package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartMemberDebtStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberDebtStatusRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원별 "본인채무 확인 상태창" 저장/조회 — {@code /newstart#ns-status-panel}의 서버 정본.
 *
 * <p>저장 대상은 확인 플래그 6종 + 사용자 정리 메모 + 진행 단계뿐이다. Credit4U ID/PW·주민번호·
 * 인증서 비밀번호·OTP 등 인증 정보는 절대 받지 않고, 채무 원본 데이터도 수신하지 않는다.
 * 대상 회원은 항상 서버 principal의 userId로만 결정된다(controller가 요청값으로 userId를 받지 않는다).
 *
 * <p>파생 상태(status)는 새 저장 체계가 아니라 기존 플래그에서 계산한 표시값이다:
 * 체크 0개 → {@code NOT_STARTED}(미확인), 일부 → {@code IN_PROGRESS}(확인 중),
 * 6개 전부 + 메모 또는 단계 작성 → {@code COMPLETED}(정리 완료).
 */
@Service
public class NewStartMemberDebtStatusService {

  /** 화면 select 옵션과 동일한 allowlist. 위반값은 400으로 거부한다. */
  public static final Set<String> ALLOWED_STAGES = Set.of(
      "정보 확인 중", "신청 준비 중", "신청 완료", "심사 진행 중", "협약·약정 단계", "기타");

  public static final int SUMMARY_MAX_LENGTH = 1000;

  private final NewStartMemberDebtStatusRepository repository;

  public NewStartMemberDebtStatusService(NewStartMemberDebtStatusRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public DebtStatusView findMine(Long userId) {
    return repository.findById(userId).map(NewStartMemberDebtStatusService::toView)
        .orElseGet(DebtStatusView::empty);
  }

  /** upsert 전용 — 1:1 상태 row라 별도 delete/create 경로는 없다(초기화는 값 비운 저장). */
  @Transactional
  public DebtStatusView save(Long userId, DebtStatusRequest request) {
    String summary = normalizeSummary(request.debtSummary());
    String stage = normalizeStage(request.stage());
    NewStartMemberDebtStatus status = repository.findById(userId)
        .orElseGet(() -> NewStartMemberDebtStatus.builder().userId(userId).build());
    status.updateProgress(
        request.credit4uChecked(),
        request.creditorConfirmed(),
        request.personalLoanConfirmed(),
        request.overdueRegisteredConfirmed(),
        request.debtGuaranteeConfirmed(),
        request.creditorChangeConfirmed(),
        summary,
        stage);
    repository.save(status);
    return toView(status);
  }

  private String normalizeSummary(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.length() > SUMMARY_MAX_LENGTH) {
      throw new IllegalArgumentException(
          "채무 메모는 " + SUMMARY_MAX_LENGTH + "자 이하로 입력해 주세요.");
    }
    return trimmed;
  }

  private String normalizeStage(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (!ALLOWED_STAGES.contains(trimmed)) {
      throw new IllegalArgumentException("선택할 수 없는 진행 단계입니다.");
    }
    return trimmed;
  }

  private static DebtStatusView toView(NewStartMemberDebtStatus status) {
    return new DebtStatusView(
        status.isCredit4uChecked(),
        status.isCreditorConfirmed(),
        status.isPersonalLoanConfirmed(),
        status.isOverdueRegisteredConfirmed(),
        status.isDebtGuaranteeConfirmed(),
        status.isCreditorChangeConfirmed(),
        status.getDebtSummary(),
        status.getStage(),
        deriveStatus(status));
  }

  private static String deriveStatus(NewStartMemberDebtStatus status) {
    if (status.checkedCount() == 0) {
      return "NOT_STARTED";
    }
    if (status.allChecked() && (status.getDebtSummary() != null || status.getStage() != null)) {
      return "COMPLETED";
    }
    return "IN_PROGRESS";
  }

  /** 요청 바디(서버는 이 필드 외의 어떤 인증·개인정보 필드도 받지 않는다). */
  public record DebtStatusRequest(
      boolean credit4uChecked,
      boolean creditorConfirmed,
      boolean personalLoanConfirmed,
      boolean overdueRegisteredConfirmed,
      boolean debtGuaranteeConfirmed,
      boolean creditorChangeConfirmed,
      String debtSummary,
      String stage) {}

  /** 응답 뷰 — 파생 상태 포함. userId는 되돌려주지 않는다(본인 row이므로 불필요). */
  public record DebtStatusView(
      boolean credit4uChecked,
      boolean creditorConfirmed,
      boolean personalLoanConfirmed,
      boolean overdueRegisteredConfirmed,
      boolean debtGuaranteeConfirmed,
      boolean creditorChangeConfirmed,
      String debtSummary,
      String stage,
      String status) {

    public static DebtStatusView empty() {
      return new DebtStatusView(false, false, false, false, false, false, null, null, "NOT_STARTED");
    }
  }
}
