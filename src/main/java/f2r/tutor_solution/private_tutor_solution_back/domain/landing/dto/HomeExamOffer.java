package f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto;

import java.util.Locale;

/** Current public offer only. Orders must snapshot their terms; never recalculate historical orders. */
public record HomeExamOffer(String version, int durationDays, int deliveriesPerWeek,
                            int totalDeliveries, int questionsPerDelivery, int priceWon) {
  public static final HomeExamOffer CURRENT =
      new HomeExamOffer("final-exam-20261028-v2", 14, 2, 4, 40, 20000);

  public int getWeeks() { return durationDays / 7; }
  public int getTotalQuestions() { return totalDeliveries * questionsPerDelivery; }
  public String getPriceLabel() { return String.format(Locale.KOREA, "%,d원", priceWon); }
  public String getHeadline() { return getWeeks() + "주 동안 " + getTotalQuestions() + "문항"; }
  public String getSummary() {
    return getWeeks() + "주 · 주 " + deliveriesPerWeek + "회 · 총 " + totalDeliveries
        + "회 · " + getTotalQuestions() + "문항";
  }
  public String getAssistantSummary() {
    return "기말고사 맞춤 이용권은 " + getPriceLabel() + "으로 " + getWeeks()
        + "주 동안 매주 " + deliveriesPerWeek + "회, 총 " + totalDeliveries
        + "회(회당 " + questionsPerDelivery + "문항, 총 " + getTotalQuestions() + "문항)를 안내합니다.";
  }
  public String getDescription() {
    return "기출과 아주 유사한 영어 기말 대비 문제! " + getSummary() + ", "
        + getPriceLabel() + ". 학교별 맞춤 제작 · 요구사항에 따라 수정.";
  }
}
