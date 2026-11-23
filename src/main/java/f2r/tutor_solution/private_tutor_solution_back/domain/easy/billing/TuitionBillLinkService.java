package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수강료 청구 링크 만들기·회수·조회. 실제 결제·송금은 하지 않는다 — 학부모에게 금액과 입금 계좌를 안내할 뿐이다.
 * 링크는 수정하지 않는다(금액은 수납에서 고치고, 계좌·기한을 바꾸려면 새로 만든다. 새로 만들면 이전 링크는 회수된다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TuitionBillLinkService {

  static final int DEFAULT_TTL_DAYS = 14;
  static final int MAX_TTL_DAYS = 60;

  private final EasyTuitionBillLinkRepository linkRepository;
  private final EasyTuitionPaymentRepository paymentRepository;

  public record CreateRequest(Long paymentId, String bankName, String bankAccountNo,
      String bankAccountHolder, Integer ttlDays) {}

  /** token은 이 응답에서만 볼 수 있다(DB에는 해시만 남는다). */
  public record CreatedLink(Long linkId, String token, String depositorCode,
      LocalDateTime expiresAt) {}

  /** 원장 화면용 한 줄(관리자 전용이라 실명을 담는다). */
  public record AdminRow(Long paymentId, String studentName, String businessName,
      String targetMonth, long billedAmount, long paidAmount, long balance, Long linkId,
      String linkState, String depositorCode, LocalDateTime expiresAt) {}

  /** 학부모 공개 화면용. 학생 실명·연락처를 담지 않는다. */
  public record PublicBill(String academyName, String maskedStudentName, String targetMonth,
      long billedAmount, long balance, boolean paid, String bankName, String bankAccountNo,
      String bankAccountHolder, String depositorCode, LocalDate expiresOn) {}

  @Transactional
  public CreatedLink create(CreateRequest request, LocalDateTime now) {
    if (request == null || request.paymentId() == null) {
      throw new IllegalArgumentException("청구할 수납을 선택해 주세요.");
    }
    EasyTuitionPayment payment = paymentRepository.findById(request.paymentId())
        .orElseThrow(() -> new IllegalArgumentException("수납을 찾을 수 없습니다."));
    if (payment.getBalance() <= 0) {
      throw new IllegalArgumentException("남은 청구 금액이 없어 링크를 만들 수 없습니다.");
    }
    String bankName = requireText(request.bankName(), "은행 이름", 40);
    String accountNo = requireText(request.bankAccountNo(), "계좌번호", 40);
    if (!accountNo.matches("[0-9-]{6,40}")) {
      throw new IllegalArgumentException("계좌번호는 숫자와 - 만 적어 주세요.");
    }
    String holder = requireText(request.bankAccountHolder(), "예금주", 40);
    int ttlDays = request.ttlDays() == null ? DEFAULT_TTL_DAYS : request.ttlDays();
    if (ttlDays < 1 || ttlDays > MAX_TTL_DAYS) {
      throw new IllegalArgumentException("링크 기한은 1~" + MAX_TTL_DAYS + "일 사이여야 합니다.");
    }
    // 수납 1건에 열려 있는 링크는 하나만 둔다.
    for (EasyTuitionBillLink old : linkRepository.findByPayment_PaymentIdAndStatus(
        payment.getPaymentId(), EasyTuitionBillLink.Status.ACTIVE)) {
      old.revoke(now);
    }
    String token = TuitionBillTokens.newToken();
    EasyTuitionBillLink link = linkRepository.save(EasyTuitionBillLink.builder()
        .payment(payment)
        .tokenHash(TuitionBillTokens.hash(token))
        .depositorCode(newUniqueDepositorCode())
        .bankName(bankName)
        .bankAccountNo(accountNo)
        .bankAccountHolder(holder)
        .expiresAt(now.plusDays(ttlDays))
        .build());
    return new CreatedLink(link.getLinkId(), token, link.getDepositorCode(), link.getExpiresAt());
  }

  /** 회수 — 행은 남기고 상태만 바꾼다(soft). 이미 회수된 링크면 그대로 둔다. */
  @Transactional
  public void revoke(Long linkId, LocalDateTime now) {
    EasyTuitionBillLink link = linkRepository.findById(linkId)
        .orElseThrow(() -> new IllegalArgumentException("청구 링크를 찾을 수 없습니다."));
    link.revoke(now);
  }

  /** 대상 월의 수납과 가장 최근 링크 상태. */
  public List<AdminRow> listForMonth(String targetMonth, LocalDateTime now) {
    if (targetMonth == null || !targetMonth.matches("\\d{4}-(0[1-9]|1[0-2])")) {
      throw new IllegalArgumentException("대상 월은 YYYY-MM 형식이어야 합니다.");
    }
    List<EasyTuitionPayment> payments =
        paymentRepository.findByTargetMonthOrderByStudent_NameAsc(targetMonth);
    Map<Long, EasyTuitionBillLink> latest = new HashMap<>();
    if (!payments.isEmpty()) {
      List<Long> ids = payments.stream().map(EasyTuitionPayment::getPaymentId).toList();
      for (EasyTuitionBillLink link : linkRepository.findByPayment_PaymentIdInOrderByLinkIdDesc(ids)) {
        latest.putIfAbsent(link.getPayment().getPaymentId(), link);
      }
    }
    List<AdminRow> rows = new ArrayList<>();
    for (EasyTuitionPayment payment : payments) {
      EasyTuitionBillLink link = latest.get(payment.getPaymentId());
      rows.add(new AdminRow(payment.getPaymentId(), payment.getStudent().getName(),
          payment.getBusiness().getName(), payment.getTargetMonth(), payment.getBilledAmount(),
          payment.getPaidAmount(), payment.getBalance(),
          link == null ? null : link.getLinkId(), linkState(link, now),
          link == null ? null : link.getDepositorCode(),
          link == null ? null : link.getExpiresAt()));
    }
    return rows;
  }

  /** 열 수 있는 링크면 공개 화면 내용을 돌려준다. 없는·회수된·만료된 토큰은 구분 없이 비어 있다. */
  public Optional<PublicBill> publicView(String token, LocalDateTime now) {
    return findOpen(token, now).map(link -> {
      EasyTuitionPayment payment = link.getPayment();
      return new PublicBill(payment.getBusiness().getName(),
          TuitionNameMasker.mask(payment.getStudent().getName()), payment.getTargetMonth(),
          payment.getBilledAmount(), payment.getBalance(), payment.getBalance() <= 0,
          link.getBankName(), link.getBankAccountNo(), link.getBankAccountHolder(),
          link.getDepositorCode(), link.getExpiresAt().toLocalDate());
    });
  }

  Optional<EasyTuitionBillLink> findOpen(String token, LocalDateTime now) {
    String hash = TuitionBillTokens.hash(token);
    if (hash == null) {
      return Optional.empty();
    }
    return linkRepository.findByTokenHash(hash).filter(link -> link.isOpen(now));
  }

  private String linkState(EasyTuitionBillLink link, LocalDateTime now) {
    if (link == null) {
      return "NONE";
    }
    if (link.getStatus() == EasyTuitionBillLink.Status.REVOKED) {
      return "REVOKED";
    }
    return link.isOpen(now) ? "ACTIVE" : "EXPIRED";
  }

  private String newUniqueDepositorCode() {
    for (int i = 0; i < 50; i++) {
      String code = TuitionBillTokens.newDepositorCode();
      if (!linkRepository.existsByDepositorCodeAndStatus(code, EasyTuitionBillLink.Status.ACTIVE)) {
        return code;
      }
    }
    throw new IllegalStateException("입금자 확인 번호를 만들지 못했습니다. 잠시 뒤 다시 시도해 주세요.");
  }

  private String requireText(String value, String label, int max) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + "을(를) 적어 주세요.");
    }
    String trimmed = value.trim();
    if (trimmed.length() > max) {
      throw new IllegalArgumentException(label + "은(는) " + max + "자 이내여야 합니다.");
    }
    return trimmed;
  }
}
