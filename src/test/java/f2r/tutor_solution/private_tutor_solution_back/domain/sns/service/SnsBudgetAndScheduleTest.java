package f2r.tutor_solution.private_tutor_solution_back.domain.sns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAutomationRule;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsGenerationLogRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SnsBudgetAndScheduleTest {

  SnsProperties props = new SnsProperties();
  SnsGenerationLogRepository logs = mock(SnsGenerationLogRepository.class);
  SnsBudgetService budget = new SnsBudgetService(props, logs);

  @Test
  @DisplayName("given 월 상한 미설정 when check then 생성 막음(이유 포함)")
  void noBudget_blocks() {
    // given
    props.setMonthlyBudgetKrw(0);
    // when
    var d = budget.check(rule(), 10);
    // then
    assertThat(d.allowed()).isFalse();
    assertThat(d.reason()).contains("SNS_MONTHLY_BUDGET_KRW");
  }

  @Test
  @DisplayName("given 3만원 상한·2만9천9백원 사용 when 이미지 1장 예상 then 막음, 여유 있으면 허용")
  void budget_limit() {
    // given
    props.setMonthlyBudgetKrw(30000);
    when(logs.sumCostSince(any())).thenReturn(29_990L);
    // when
    var blocked = budget.check(rule(), budget.estimateRunCost(rule()));
    when(logs.sumCostSince(any())).thenReturn(1_000L);
    var allowed = budget.check(rule(), budget.estimateRunCost(rule()));
    // then
    assertThat(blocked.allowed()).isFalse();
    assertThat(blocked.reason()).contains("30,000원");
    assertThat(allowed.allowed()).isTrue();
  }

  @Test
  @DisplayName("given 규칙 상한 100원·사용 90원 when check then 규칙 상한으로 막음")
  void ruleBudget() {
    // given
    props.setMonthlyBudgetKrw(30000);
    SnsAutomationRule r = rule();
    r.setMonthlyBudgetKrw(100);
    when(logs.sumCostSince(any())).thenReturn(90L);
    when(logs.sumCostSinceForRule(eq(7L), any())).thenReturn(90L);
    // when
    var d = budget.check(r, 43);
    // then
    assertThat(d.allowed()).isFalse();
    assertThat(d.reason()).contains("이 규칙");
  }

  @Test
  @DisplayName("given GPU 1,261원/시간 when 120초 then 43원(올림)")
  void imageCost() {
    // given / when / then
    assertThat(budget.imageCostFor(120)).isEqualTo(43);
    assertThat(budget.imageCostFor(1)).isEqualTo(1);
  }

  @Test
  @DisplayName("given 격일·매주 규칙 when isScheduledOn then 시작일 기준 격일, 지정 요일만")
  void scheduleDays() {
    // given
    SnsAutomationRule every = rule();
    every.setScheduleType(SnsAutomationRule.ScheduleType.EVERY_OTHER_DAY);
    SnsAutomationRule weekly = rule();
    weekly.setScheduleType(SnsAutomationRule.ScheduleType.WEEKLY);
    weekly.setScheduleDays("MON,wed");
    LocalDate start = LocalDate.of(2026, 11, 2); // 월요일
    // when / then
    assertThat(every.isScheduledOn(start)).isTrue();
    assertThat(every.isScheduledOn(start.plusDays(1))).isFalse();
    assertThat(every.isScheduledOn(start.plusDays(2))).isTrue();
    assertThat(every.isScheduledOn(start.minusDays(2))).isFalse();
    assertThat(weekly.weeklyDays()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
    assertThat(weekly.isScheduledOn(start.plusDays(2))).isTrue();
    assertThat(weekly.isScheduledOn(start.plusDays(1))).isFalse();
  }

  @Test
  @DisplayName("given 19:00 매일 규칙 when 시각별 isDue then 19:00~22:00 사이 하루 한 번, 멈춤·꺼짐은 안 돎")
  void isDue_window() {
    // given
    SnsAutomationRule r = rule();
    LocalDate day = LocalDate.of(2026, 11, 3);
    // when / then
    assertThat(SnsScheduler.isDue(r, LocalDateTime.of(day, LocalTime.of(18, 59)))).isFalse();
    assertThat(SnsScheduler.isDue(r, LocalDateTime.of(day, LocalTime.of(19, 0)))).isTrue();
    assertThat(SnsScheduler.isDue(r, LocalDateTime.of(day, LocalTime.of(21, 59)))).isTrue();
    assertThat(SnsScheduler.isDue(r, LocalDateTime.of(day, LocalTime.of(22, 0)))).isFalse();
    r.setLastRunOn(day);
    assertThat(SnsScheduler.isDue(r, LocalDateTime.of(day, LocalTime.of(19, 5)))).isFalse();
    r.setLastRunOn(null);
    r.setSaveMode(SnsAutomationRule.SaveMode.PAUSED);
    assertThat(SnsScheduler.isDue(r, LocalDateTime.of(day, LocalTime.of(19, 5)))).isFalse();
  }

  @Test
  @DisplayName("given 고정 해시태그 7개(#없는 것 포함) when fixedHashtagList then #붙여 5개까지")
  void fixedHashtags() {
    // given
    SnsAutomationRule r = rule();
    r.setFixedHashtags("a #b c d e f g");
    // when / then
    assertThat(r.fixedHashtagList()).containsExactly("#a", "#b", "#c", "#d", "#e");
  }

  static SnsAutomationRule rule() {
    SnsAutomationRule r = new SnsAutomationRule();
    r.setId(7L);
    r.setTitle("규칙");
    r.setEnabled(true);
    r.setStartDate(LocalDate.of(2026, 11, 2));
    r.setRunTime(LocalTime.of(19, 0));
    return r;
  }
}
