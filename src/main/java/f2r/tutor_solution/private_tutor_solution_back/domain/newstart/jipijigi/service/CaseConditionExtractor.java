package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 질문 텍스트에서 구조화 검색 조건을 추출한다.
 *
 * <p>추출 원칙:
 * <ul>
 *   <li>정규식으로 명시적 표현만 추출한다. 추측하지 않는다.</li>
 *   <li>추출 실패 시 해당 필드는 null — null인 조건은 검색에 사용하지 않는다.</li>
 *   <li>이자처럼 별도 컬럼이 없는 정보는 여기서 추출하지 않고 원문 LIKE 검색에 맡긴다.</li>
 * </ul>
 */
public final class CaseConditionExtractor {

    private CaseConditionExtractor() {}

    private static final Pattern HOUSEHOLD_PATTERN = Pattern.compile("(\\d)\\s*인가구");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d[\\d,]*)\\s*만원");
    private static final Pattern RATE_PATTERN = Pattern.compile("(\\d{1,3})\\s*%");
    private static final Pattern RATE_RANGE_PATTERN = Pattern.compile("(\\d{1,3})\\s*~\\s*(\\d{1,3})\\s*%");

    /**
     * 질문에서 구조화 조건을 추출한다. 조건이 하나도 없으면 빈 {@link CaseConditions}를 반환한다.
     * 절대 null을 반환하지 않는다.
     */
    public static CaseConditions extract(String questionText) {
        if (questionText == null || questionText.isBlank()) {
            return CaseConditions.builder().build();
        }
        String q = questionText;

        CaseConditions.CaseConditionsBuilder b = CaseConditions.builder();

        // 가구유형: "3인가구"
        Matcher hm = HOUSEHOLD_PATTERN.matcher(q);
        if (hm.find()) {
            b.householdType(hm.group(1) + "인가구");
        }

        // 채무액: "채무 5천만원", "총채무액 1억원" 등 — 문맥상 채무를 가리키는 금액
        // "만원" 단위를 원으로 변환. "천만원" → "1000만원" 형태도 처리
        Long debtAmount = extractDebtAmount(q);
        if (debtAmount != null) {
            // 단일 금액은 ±30% 범위로 해석 (정확한 일치가 아닐 수 있으므로)
            b.debtTotalMin((long) (debtAmount * 0.7));
            b.debtTotalMax((long) (debtAmount * 1.3));
        }

        // 감면율: "감면율 70%", "70~80%" 등
        Matcher rm = RATE_RANGE_PATTERN.matcher(q);
        if (rm.find()) {
            try {
                b.reductionRateMin(Integer.parseInt(rm.group(1)));
                b.reductionRateMax(Integer.parseInt(rm.group(2)));
            } catch (NumberFormatException ignored) {
                // 파싱 실패 시 조건 없음 — 임의 제외 금지
            }
        } else {
            Matcher sm = RATE_PATTERN.matcher(q);
            if (sm.find()) {
                try {
                    int rate = Integer.parseInt(sm.group(1));
                    if (rate <= 100) {  // 100% 초과는 감면율이 아닐 가능성
                        b.reductionRateMin(Math.max(0, rate - 10));
                        b.reductionRateMax(Math.min(100, rate + 10));
                    }
                } catch (NumberFormatException ignored) {
                    // 파싱 실패 시 조건 없음
                }
            }
        }

        // 담보 여부: "담보대출", "담보 있음" 등
        if (q.contains("담보대출") || q.contains("담보 대출") || q.contains("담보가 있")) {
            b.hasSecuredDebt(true);
        } else if (q.contains("무담보") || q.contains("담보가 없") || q.contains("담보없")) {
            b.hasSecuredDebt(false);
        }

        // 신청 유형
        if (q.contains("부실우려차주") || q.contains("부실 우려")) {
            b.applicationType("INSOLVENCY_RISK");
        } else if (q.contains("부실차주")) {
            b.applicationType("INSOLVENCY");
        }

        return b.build();
    }

    /**
     * 질문에서 채무액을 추출한다 (원 단위).
     * "5천만원" → 50,000,000 / "1억원" → 100,000,000 / "5000만원" → 50,000,000
     * 추출 실패 시 null.
     */
    static Long extractDebtAmount(String q) {
        // "N억원" 패턴
        Matcher eok = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*억원").matcher(q);
        if (eok.find()) {
            try {
                double v = Double.parseDouble(eok.group(1));
                return (long) (v * 100_000_000);
            } catch (NumberFormatException ignored) {}
        }
        // "N천만원" 패턴
        Matcher cheonman = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*천만원").matcher(q);
        if (cheonman.find()) {
            try {
                double v = Double.parseDouble(cheonman.group(1));
                return (long) (v * 10_000_000);
            } catch (NumberFormatException ignored) {}
        }
        // "N만원" 패턴 (채무 문맥)
        if (q.contains("채무") || q.contains("빚") || q.contains("대출")) {
            Matcher man = AMOUNT_PATTERN.matcher(q);
            if (man.find()) {
                try {
                    String num = man.group(1).replace(",", "");
                    return Long.parseLong(num) * 10_000;
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    /**
     * DB의 감면율 VARCHAR("75%", "75~80%")에서 하한값을 파싱한다.
     * 파싱 실패 시 null을 반환 — 호출자는 null인 사례를 임의로 제외하지 말아야 한다.
     */
    public static Integer parseReductionRateLower(String rateStr) {
        if (rateStr == null || rateStr.isBlank()) return null;
        Matcher m = Pattern.compile("(\\d{1,3})").matcher(rateStr);
        if (m.find()) {
            try {
                int v = Integer.parseInt(m.group(1));
                return v <= 100 ? v : null;
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
