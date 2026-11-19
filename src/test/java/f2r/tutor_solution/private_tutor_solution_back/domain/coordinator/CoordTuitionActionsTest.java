package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyPaymentRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyPaymentResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyFinancialStatementService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTuitionService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayName("CoordTuitionActions — 수강료 말 명령 조회·청구·납부·되돌리기")
class CoordTuitionActionsTest {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
  EasyTuitionService tuition;
  EasyTuitionPaymentRepository payments;
  EasyStudentRepository students;
  EasyFinancialStatementService financial;
  CoordTuitionActions actions;
  EasyStudent minsu;
  EasyBusiness biz;
  EasyTuitionPayment nov;

  @BeforeEach
  void setUp() {
    tuition = mock(EasyTuitionService.class);
    payments = mock(EasyTuitionPaymentRepository.class);
    students = mock(EasyStudentRepository.class);
    financial = mock(EasyFinancialStatementService.class);
    Clock clock = Clock.fixed(LocalDateTime.of(TODAY, java.time.LocalTime.NOON).atZone(KstTime.ZONE).toInstant(), KstTime.ZONE);
    actions = new CoordTuitionActions(tuition, payments, students, financial, clock);
    minsu = EasyStudent.builder().name("김민수").grade((byte) 2).status(EasyStudentStatus.ENROLLED).build();
    ReflectionTestUtils.setField(minsu, "studentId", 11L);
    biz = EasyBusiness.builder().code("A").name("학원").active(true).build();
    ReflectionTestUtils.setField(biz, "businessId", 3L);
    nov = EasyTuitionPayment.builder().student(minsu).business(biz).targetMonth("2026-11").billedAmount(300000)
        .paidAmount(100000).payMethod("계좌").build();
    ReflectionTestUtils.setField(nov, "paymentId", 70L);
    when(students.findById(11L)).thenReturn(Optional.of(minsu));
    when(payments.findById(70L)).thenReturn(Optional.of(nov));
    when(payments.findByStudent_StudentIdOrderByTargetMonthDesc(11L)).thenReturn(List.of(nov));
  }

  ObjectNode args() {
    return JSON.createObjectNode();
  }

  static Map<String, Object> plan(CoordActions.Draft d) {
    return d.plan();
  }

  @Test
  @DisplayName("given 이번 달 수납 when 현황 조회 then 이 사업체만, 가린 이름·합계·미완납 수")
  void status_masksAndTotals() {
    // given
    when(tuition.list(null, "2026-11", null)).thenReturn(List.of(
        new EasyPaymentResponse(70L, 11L, "김민수", 3L, "학원", "2026-11", 300000, 100000, null, "", null, "PARTIAL", 200000),
        new EasyPaymentResponse(71L, 12L, "이수", 9L, "다른곳", "2026-11", 100000, 100000, null, "", null, "PAID", 0)));

    // when
    Map<String, Object> out = actions.status(3L, null, null);

    // then
    assertThat(out).containsEntry("month", "2026-11").containsEntry("count", 1).containsEntry("totalBalance", 200000L)
        .containsEntry("notFullyPaidCount", 1);
    assertThat(out.toString()).contains("김○수").doesNotContain("김민수", "이○");
  }

  @Test
  @DisplayName("given 11월 청구 있음 when 30만원 중 20만원 납부 초안 then 누적·완납 문구, 실행은 누적에 더해 저장")
  void pay_addsToPaidAmount() {
    // given
    var d = actions.prepare("TUITION", 3L, "PAY", args().put("studentId", 11).put("month", "2026-11").put("amount", 200000));

    // when
    Map<String, Object> r = actions.execute(d.kind(), JSON.valueToTree(plan(d)));

    // then
    assertThat(d.aiTitle()).isEqualTo("김○수 · 2026-11 수강료 납부 200,000원 (누적 300,000원 / 청구 300,000원 → 완납)");
    verify(tuition).update(eq(70L), eq(new EasyPaymentRequest(11L, 3L, "2026-11", 300000L, 300000L, TODAY, "계좌", null)));
    assertThat(r).containsEntry("beforePaid", 100000L).containsEntry("afterPaid", 300000L);
  }

  @Test
  @DisplayName("given 납부 기록 when 그사이 납부액이 바뀜 then 실행·되돌리기 모두 거절")
  void pay_driftRejected() {
    // given
    var d = actions.prepare("TUITION", 3L, "PAY", args().put("paymentId", 70).put("amount", 50000));
    JsonNode p = JSON.valueToTree(plan(d));
    ReflectionTestUtils.setField(nov, "paidAmount", 120000L);

    // when & then
    assertThatThrownBy(() -> actions.execute(d.kind(), p)).isInstanceOf(IllegalStateException.class);
    JsonNode result = JSON.createObjectNode().put("paymentId", 70).put("afterPaid", 150000).put("beforePaid", 100000);
    assertThatThrownBy(() -> actions.undo(d.kind(), p, result)).isInstanceOf(IllegalStateException.class);
    verify(tuition, never()).update(anyLong(), any());
  }

  @Test
  @DisplayName("given 납부 기록 후 when 되돌리기 then 이전 납부액·납부일·방법으로")
  void pay_undoRestores() {
    // given
    ReflectionTestUtils.setField(nov, "paidAmount", 300000L);
    JsonNode result = JSON.createObjectNode().put("paymentId", 70).put("afterPaid", 300000).put("beforePaid", 100000)
        .putNull("beforePaidOn").put("beforePayMethod", "계좌");

    // when
    actions.undo(CoordTuitionActions.TUITION_PAY, JSON.createObjectNode(), result);

    // then
    verify(tuition).update(eq(70L), eq(new EasyPaymentRequest(11L, 3L, "2026-11", 300000L, 100000L, null, "계좌", null)));
  }

  @Test
  @DisplayName("given 청구 등록 when 실행 후 되돌리기 then 생성 후 그 청구 삭제, 이미 있는 달은 초안 거절")
  void bill_createAndUndo() {
    // given
    when(payments.existsByStudent_StudentIdAndBusiness_BusinessIdAndTargetMonth(11L, 3L, "2026-12")).thenReturn(false);
    when(payments.existsByStudent_StudentIdAndBusiness_BusinessIdAndTargetMonth(11L, 3L, "2026-11")).thenReturn(true);
    var d = actions.prepare("TUITION", 3L, "BILL", args().put("studentId", 11).put("month", "2026-12").put("billedAmount", 300000));
    when(tuition.create(any())).thenReturn(
        new EasyPaymentResponse(80L, 11L, "김민수", 3L, "학원", "2026-12", 300000, 0, null, "", null, "UNPAID", 300000));
    EasyTuitionPayment dec = EasyTuitionPayment.builder().student(minsu).business(biz).targetMonth("2026-12")
        .billedAmount(300000).paidAmount(0).build();
    when(payments.findById(80L)).thenReturn(Optional.of(dec));

    // when
    Map<String, Object> r = actions.execute(d.kind(), JSON.valueToTree(plan(d)));
    ReflectionTestUtils.setField(dec, "paymentId", 80L);
    actions.undo(d.kind(), JSON.valueToTree(plan(d)), JSON.valueToTree(r));

    // then
    assertThat(d.aiTitle()).isEqualTo("김○수 · 2026-12 수강료 청구 300,000원");
    verify(tuition).delete(80L);
    assertThatThrownBy(() -> actions.prepare("TUITION", 3L, "BILL",
        args().put("studentId", 11).put("month", "2026-11").put("billedAmount", 1))).hasMessageContaining("이미 있습니다");
  }

  @Test
  @DisplayName("given 이상한 값 when 초안 then 거절(없는 action·금액 범위·잘못된 월·다른 사업체·0원)")
  void prepare_rejectsInvalid() {
    // given & when & then
    assertThatThrownBy(() -> actions.prepare("TUITION", 3L, "DELETE", args())).hasMessageContaining("삭제는 화면");
    assertThatThrownBy(() -> actions.prepare("TUITION", 3L, "BILL",
        args().put("studentId", 11).put("month", "2026-12").put("billedAmount", 200_000_000L))).hasMessageContaining("1억");
    assertThatThrownBy(() -> actions.prepare("TUITION", 3L, "BILL",
        args().put("studentId", 11).put("month", "2026/12").put("billedAmount", 1))).hasMessageContaining("YYYY-MM");
    assertThatThrownBy(() -> actions.prepare("TUITION", 9L, "PAY", args().put("paymentId", 70).put("amount", 1)))
        .hasMessageContaining("다른 사업체");
    assertThatThrownBy(() -> actions.prepare("TUITION", 3L, "PAY", args().put("paymentId", 70).put("amount", 0)))
        .hasMessageContaining("0보다");
  }

  @Test
  @DisplayName("given 손익계산서 when 요약 then 이번 달 범위·큰 항목 순")
  void ledgerSummary() {
    // given
    when(financial.incomeStatement(3L, TODAY.withDayOfMonth(1), TODAY)).thenReturn(new EasyFinancialStatementService.IncomeStatement(
        List.of(new EasyFinancialStatementService.ProfitLine("4100", "수강료수입", 900000)),
        List.of(new EasyFinancialStatementService.ProfitLine("5100", "임차료", 100000),
            new EasyFinancialStatementService.ProfitLine("5200", "급여", 500000)),
        900000, 600000, 300000, true));

    // when
    Map<String, Object> out = actions.ledgerSummary(3L, null, null);

    // then
    assertThat(out).containsEntry("from", "2026-11-01").containsEntry("netIncome", 300000L);
    assertThat(out.get("topExpense").toString()).startsWith("[{account=급여");
  }
}
