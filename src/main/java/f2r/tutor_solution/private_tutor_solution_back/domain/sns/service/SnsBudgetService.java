package f2r.tutor_solution.private_tutor_solution_back.domain.sns.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAutomationRule;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsTime;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsGenerationLogRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.springframework.stereotype.Service;

/** 월 상한(전체·규칙별). 이번 달(서울 기준) 생성 기록 비용 합 + 이번 작업 예상 비용이 상한을 넘으면 막는다. */
@Service
public class SnsBudgetService {

  public record Decision(boolean allowed, String reason, long spentKrw, Integer limitKrw) {}

  private final SnsProperties properties;
  private final SnsGenerationLogRepository logs;

  public SnsBudgetService(SnsProperties properties, SnsGenerationLogRepository logs) {
    this.properties = properties;
    this.logs = logs;
  }

  public LocalDateTime monthStart() {
    return YearMonth.now(SnsTime.ZONE).atDay(1).atStartOfDay();
  }

  public long spentThisMonth() {
    return logs.sumCostSince(monthStart());
  }

  /** 문구 1회 + 이미지 N장 생성 예상 비용(원). */
  public int estimateRunCost(SnsAutomationRule rule) {
    int images = Math.max(1, rule.getImageCount());
    return properties.getTextCostKrwPerCall() + images * imageCostFor(properties.getGpuEstimatedSecondsPerImage());
  }

  public int imageCostFor(long gpuSeconds) {
    return (int) Math.ceil(gpuSeconds * (double) properties.getGpuKrwPerHour() / 3600.0);
  }

  public Decision check(SnsAutomationRule rule, int estimatedKrw) {
    if (!properties.budgetConfigured()) {
      return new Decision(false, "월 상한(SNS_MONTHLY_BUDGET_KRW)이 설정되지 않아 생성하지 않습니다.", spentThisMonth(), null);
    }
    long spent = spentThisMonth();
    int limit = properties.getMonthlyBudgetKrw();
    if (spent + estimatedKrw > limit) {
      return new Decision(false, String.format("이번 달 상한 %,d원에 닿아 생성을 멈췄습니다(사용 %,d원).", limit, spent), spent, limit);
    }
    if (rule != null && rule.getMonthlyBudgetKrw() != null && rule.getMonthlyBudgetKrw() > 0) {
      long ruleSpent = logs.sumCostSinceForRule(rule.getId(), monthStart());
      if (ruleSpent + estimatedKrw > rule.getMonthlyBudgetKrw()) {
        return new Decision(false, String.format("이 규칙의 월 상한 %,d원에 닿아 생성을 멈췄습니다(사용 %,d원).",
            rule.getMonthlyBudgetKrw(), ruleSpent), spent, limit);
      }
    }
    return new Decision(true, null, spent, limit);
  }
}
