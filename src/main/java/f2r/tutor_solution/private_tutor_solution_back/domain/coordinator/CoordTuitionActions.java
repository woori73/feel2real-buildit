package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyPaymentRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyPaymentResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyPaymentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyFinancialStatementService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTuitionService;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * AI 코디네이터 — 수강료 수납·장부 말 명령. 기존 {@link EasyTuitionService}(수납 원장)만 부른다.
 *
 * <ul>
 *   <li>조회: 월별 수납 현황(가린 이름·청구·납부·잔액), 손익 요약(기존 손익계산서).
 *   <li>초안 → 대화창 확인 → 실행: 청구 등록(BILL), 납부 기록(PAY = 이번에 받은 금액을 더함).
 *   <li>되돌리기(10분 안, 그사이 바뀌지 않았을 때만): 청구 → 그 청구 삭제, 납부 → 이전 납부액으로.
 *   <li>하지 않는 것: 실제 결제·송금·카드 승인, 수납 삭제(화면에서), 분개·전표 직접 작성.
 * </ul>
 */
@Component
public class CoordTuitionActions implements CoordActions {

  public static final String TUITION_BILL = "TUITION_BILL";
  public static final String TUITION_PAY = "TUITION_PAY";
  static final Set<String> KINDS = Set.of(TUITION_BILL, TUITION_PAY);
  static final long MAX_AMOUNT = 100_000_000L;
  static final Map<String, String> STATUS_LABEL = Map.of("UNPAID", "미납", "PARTIAL", "일부 납부", "PAID", "완납");

  private final EasyTuitionService tuition;
  private final EasyTuitionPaymentRepository payments;
  private final EasyStudentRepository students;
  private final EasyFinancialStatementService financial;
  private final Clock clock;

  @Autowired
  public CoordTuitionActions(EasyTuitionService tuition, EasyTuitionPaymentRepository payments,
      EasyStudentRepository students, EasyFinancialStatementService financial) {
    this(tuition, payments, students, financial, Clock.system(KstTime.ZONE));
  }

  CoordTuitionActions(EasyTuitionService tuition, EasyTuitionPaymentRepository payments, EasyStudentRepository students,
      EasyFinancialStatementService financial, Clock clock) {
    this.tuition = tuition;
    this.payments = payments;
    this.students = students;
    this.financial = financial;
    this.clock = clock;
  }

  @Override
  public Set<String> areas() {
    return Set.of("TUITION");
  }

  @Override
  public Set<String> kinds() {
    return KINDS;
  }

  /* ---------------- 조회 ---------------- */

  /** 월별 수납 현황. 학생 이름은 가린다. month 없으면 이번 달. status: UNPAID·PARTIAL·PAID(없으면 전체). */
  public Map<String, Object> status(Long bid, String month, String status) {
    String m = month == null || month.isBlank() ? YearMonth.now(clock).toString() : month.strip();
    EasyPaymentStatus st = status == null || status.isBlank() ? null : parseStatus(status);
    List<Map<String, Object>> rows = new ArrayList<>();
    long billed = 0, paid = 0, balance = 0;
    int unpaid = 0;
    for (EasyPaymentResponse r : tuition.list(null, m, st).stream()
        .filter(r -> Objects.equals(r.businessId(), bid))
        .sorted(Comparator.comparing(EasyPaymentResponse::studentName, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList()) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("paymentId", r.paymentId());
      row.put("studentId", r.studentId());
      row.put("displayName", CoordinatorService.maskName(r.studentName()));
      row.put("billedAmount", r.billedAmount());
      row.put("paidAmount", r.paidAmount());
      row.put("balance", r.balance());
      row.put("status", r.status());
      row.put("paidOn", r.paidOn() == null ? null : r.paidOn().toString());
      rows.add(row);
      billed += r.billedAmount();
      paid += r.paidAmount();
      balance += r.balance();
      if (!"PAID".equals(r.status())) unpaid++;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("businessId", bid);
    out.put("month", m);
    out.put("count", rows.size());
    out.put("notFullyPaidCount", unpaid);
    out.put("totalBilled", billed);
    out.put("totalPaid", paid);
    out.put("totalBalance", balance);
    out.put("payments", rows);
    out.put("note", "금액 단위는 원. 이름은 가려서 보여 줍니다. 실제 결제·송금은 하지 않습니다.");
    return out;
  }

  /** 손익 요약(기존 손익계산서). from·to 없으면 이번 달 1일~오늘. 금액이 큰 항목 5개씩. */
  public Map<String, Object> ledgerSummary(Long bid, String from, String to) {
    LocalDate today = LocalDate.now(clock);
    LocalDate f = from == null || from.isBlank() ? today.withDayOfMonth(1) : LocalDate.parse(from.strip());
    LocalDate t = to == null || to.isBlank() ? today : LocalDate.parse(to.strip());
    var is = financial.incomeStatement(bid, f, t);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("businessId", bid);
    out.put("from", f.toString());
    out.put("to", t.toString());
    out.put("revenue", is.revenue());
    out.put("expense", is.expense());
    out.put("netIncome", is.netIncome());
    out.put("trialBalanced", is.trialBalanced());
    out.put("topRevenue", top(is.revenueLines()));
    out.put("topExpense", top(is.expenseLines()));
    out.put("note", "장부(분개) 기준 손익입니다. 수강료 수납 원장과 시점이 다를 수 있습니다.");
    return out;
  }

  /* ---------------- 초안 ---------------- */

  @Override
  public Draft prepare(String area, Long bid, String action, JsonNode a) {
    String act = action == null ? "" : action.strip().toUpperCase();
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put("businessId", bid);
    if ("BILL".equals(act)) {
      EasyStudent s = requireStudent(a);
      String month = requireMonth(a);
      long billed = amount(a, "billedAmount", true);
      long paidNow = has(a, "paidAmount") ? amount(a, "paidAmount", false) : 0L;
      if (payments.existsByStudent_StudentIdAndBusiness_BusinessIdAndTargetMonth(s.getStudentId(), bid, month)) {
        throw new IllegalArgumentException(month + " 수납이 이미 있습니다. 납부 기록은 action=PAY로 하세요.");
      }
      plan.put("studentId", s.getStudentId());
      plan.put("month", month);
      plan.put("billedAmount", billed);
      plan.put("paidAmount", paidNow);
      plan.put("paidOn", paidNow > 0 ? date(a, "paidOn") : null);
      plan.put("payMethod", text(a, "payMethod"));
      plan.put("memo", text(a, "memo"));
      String tail = " · " + month + " 수강료 청구 " + won(billed) + (paidNow > 0 ? " · 납부 " + won(paidNow) : "");
      return new Draft(TUITION_BILL, plan, s.getName() + tail, CoordinatorService.maskName(s.getName()) + tail);
    }
    if ("PAY".equals(act)) {
      EasyTuitionPayment p = requirePayment(bid, a);
      long add = amount(a, "amount", true);
      if (add == 0) throw new IllegalArgumentException("이번에 받은 금액(amount)은 0보다 커야 합니다.");
      long total = p.getPaidAmount() + add;
      plan.put("paymentId", p.getPaymentId());
      plan.put("beforePaid", p.getPaidAmount());
      plan.put("amount", add);
      plan.put("paidOn", date(a, "paidOn"));
      plan.put("payMethod", text(a, "payMethod"));
      String result = total >= p.getBilledAmount() ? (total > p.getBilledAmount() ? "청구액보다 " + won(total - p.getBilledAmount()) + " 많음" : "완납")
          : "잔액 " + won(p.getBilledAmount() - total);
      String tail = " · " + p.getTargetMonth() + " 수강료 납부 " + won(add) + " (누적 " + won(total) + " / 청구 "
          + won(p.getBilledAmount()) + " → " + result + ")";
      String name = p.getStudent().getName();
      return new Draft(TUITION_PAY, plan, name + tail, CoordinatorService.maskName(name) + tail);
    }
    throw new IllegalArgumentException("action은 BILL(청구 등록)·PAY(납부 기록) 중 하나입니다. 수납 삭제는 화면에서 합니다.");
  }

  /* ---------------- 실행 ---------------- */

  @Override
  public Map<String, Object> execute(String kind, JsonNode plan) {
    Map<String, Object> r = new LinkedHashMap<>();
    Long bid = plan.get("businessId").asLong();
    if (TUITION_BILL.equals(kind)) {
      var created = tuition.create(new EasyPaymentRequest(plan.get("studentId").asLong(), bid, plan.get("month").asText(),
          plan.get("billedAmount").asLong(), plan.get("paidAmount").asLong(), localDate(plan, "paidOn"),
          str(plan, "payMethod"), str(plan, "memo")));
      r.put("paymentId", created.paymentId());
      r.put("afterPaid", created.paidAmount());
      r.put("label", "수강료 청구 등록");
      return r;
    }
    if (TUITION_PAY.equals(kind)) {
      EasyTuitionPayment p = payments.findById(plan.get("paymentId").asLong())
          .orElseThrow(() -> new IllegalArgumentException("수납을 찾을 수 없습니다."));
      if (p.getPaidAmount() != plan.get("beforePaid").asLong()) {
        throw new IllegalStateException("그사이 납부액이 바뀌어 기록하지 않았습니다. 다시 확인해 주세요.");
      }
      r.put("paymentId", p.getPaymentId());
      r.put("beforePaid", p.getPaidAmount());
      r.put("beforePaidOn", p.getPaidOn() == null ? null : p.getPaidOn().toString());
      r.put("beforePayMethod", p.getPayMethod());
      long after = p.getPaidAmount() + plan.get("amount").asLong();
      LocalDate paidOn = localDate(plan, "paidOn") == null ? LocalDate.now(clock) : localDate(plan, "paidOn");
      String method = plan.hasNonNull("payMethod") ? plan.get("payMethod").asText() : p.getPayMethod();
      tuition.update(p.getPaymentId(), new EasyPaymentRequest(p.getStudent().getStudentId(), p.getBusiness().getBusinessId(),
          p.getTargetMonth(), p.getBilledAmount(), after, paidOn, method, p.getMemo()));
      r.put("afterPaid", after);
      r.put("label", "수강료 납부 기록");
      return r;
    }
    throw new IllegalArgumentException("알 수 없는 작업입니다: " + kind);
  }

  /* ---------------- 되돌리기 ---------------- */

  @Override
  public void undo(String kind, JsonNode plan, JsonNode result) {
    EasyTuitionPayment p = payments.findById(result.get("paymentId").asLong())
        .orElseThrow(() -> new IllegalStateException("수납을 찾을 수 없어 되돌릴 수 없습니다."));
    if (p.getPaidAmount() != result.get("afterPaid").asLong()) {
      throw new IllegalStateException("그사이 납부액이 바뀌어 되돌릴 수 없습니다. 수납 화면에서 고쳐 주세요.");
    }
    if (TUITION_BILL.equals(kind)) {
      tuition.delete(p.getPaymentId());
      return;
    }
    if (TUITION_PAY.equals(kind)) {
      tuition.update(p.getPaymentId(), new EasyPaymentRequest(p.getStudent().getStudentId(), p.getBusiness().getBusinessId(),
          p.getTargetMonth(), p.getBilledAmount(), result.get("beforePaid").asLong(), localDate(result, "beforePaidOn"),
          str(result, "beforePayMethod"), p.getMemo()));
      return;
    }
    throw new IllegalArgumentException("알 수 없는 작업입니다: " + kind);
  }

  /* ---------------- 내부 ---------------- */

  static String won(long v) {
    return NumberFormat.getNumberInstance(Locale.KOREA).format(v) + "원";
  }

  private static List<Map<String, Object>> top(List<EasyFinancialStatementService.ProfitLine> lines) {
    return lines.stream().sorted(Comparator.comparingLong(EasyFinancialStatementService.ProfitLine::amount).reversed())
        .limit(5).map(l -> Map.<String, Object>of("account", l.accountName(), "amount", l.amount())).toList();
  }

  private EasyStudent requireStudent(JsonNode a) {
    if (!has(a, "studentId")) throw new IllegalArgumentException("studentId가 필요합니다. 먼저 f2r_student_search로 찾으세요.");
    return students.findById(a.get("studentId").asLong()).orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
  }

  /** paymentId, 또는 studentId+month로 이 사업체의 수납 한 건을 찾는다. */
  private EasyTuitionPayment requirePayment(Long bid, JsonNode a) {
    EasyTuitionPayment p;
    if (has(a, "paymentId")) {
      p = payments.findById(a.get("paymentId").asLong()).orElseThrow(() -> new IllegalArgumentException("수납을 찾을 수 없습니다."));
    } else {
      EasyStudent s = requireStudent(a);
      String month = requireMonth(a);
      p = payments.findByStudent_StudentIdOrderByTargetMonthDesc(s.getStudentId()).stream()
          .filter(x -> month.equals(x.getTargetMonth()) && Objects.equals(x.getBusiness().getBusinessId(), bid))
          .findFirst().orElseThrow(() -> new IllegalArgumentException(month + " 청구가 없습니다. 먼저 action=BILL로 청구를 등록하세요."));
    }
    if (!Objects.equals(p.getBusiness().getBusinessId(), bid)) throw new IllegalArgumentException("다른 사업체의 수납입니다.");
    return p;
  }

  private String requireMonth(JsonNode a) {
    String m = text(a, "month");
    if (m == null) return YearMonth.now(clock).toString();
    if (!m.matches("\\d{4}-(0[1-9]|1[0-2])")) throw new IllegalArgumentException("month는 YYYY-MM 형식입니다.");
    return m;
  }

  private static long amount(JsonNode a, String f, boolean required) {
    if (!has(a, f)) {
      if (required) throw new IllegalArgumentException(f + "(원)이 필요합니다.");
      return 0L;
    }
    long v = a.get(f).asLong();
    if (v < 0 || v > MAX_AMOUNT) throw new IllegalArgumentException(f + "는 0원 이상 1억 원 이하로 주세요.");
    return v;
  }

  private static EasyPaymentStatus parseStatus(String s) {
    try {
      return EasyPaymentStatus.valueOf(s.strip().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("status는 UNPAID(미납)·PARTIAL(일부)·PAID(완납) 중 하나입니다.");
    }
  }

  private static String date(JsonNode a, String f) {
    return has(a, f) ? LocalDate.parse(a.get(f).asText().strip()).toString() : null;
  }

  private static LocalDate localDate(JsonNode n, String f) {
    return n.hasNonNull(f) ? LocalDate.parse(n.get(f).asText()) : null;
  }

  private static boolean has(JsonNode a, String f) {
    return a != null && a.hasNonNull(f) && !(a.get(f).isTextual() && a.get(f).asText().isBlank());
  }

  private static String text(JsonNode a, String f) {
    return has(a, f) ? a.get(f).asText().strip() : null;
  }

  private static String str(JsonNode a, String f) {
    return a.hasNonNull(f) ? a.get(f).asText() : null;
  }
}
