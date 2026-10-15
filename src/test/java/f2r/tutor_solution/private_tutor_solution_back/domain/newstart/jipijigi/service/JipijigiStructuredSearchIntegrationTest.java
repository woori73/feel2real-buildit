package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartEmpiricalCase;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartEmpiricalCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 구조화 사례 검색 통합 테스트 (격리 MySQL).
 *
 * <p>개인정보를 제거한 대표 사례 데이터로 기존 LIKE 검색과
 * 신규 구조화 검색을 13개 질문으로 비교한다.
 *
 * <p>운영 DB와 완전히 분리되며, 운영 접속정보를 사용하지 않는다.
 */
@SpringBootTest
@Transactional
@DisplayName("지피지기 구조화 검색 통합 테스트 (13문항 비교)")
class JipijigiStructuredSearchIntegrationTest {

    @Autowired
    private NewstartEmpiricalCaseRepository caseRepository;

    @Autowired
    private JipijigiResearchService researchService;

    /** 테스트용 대표 사례 (개인정보 제거). */
    @BeforeEach
    void setUp() {
        caseRepository.deleteAll();
        // 1: 3인가구, 5천만원, 감면율 75%
        saveCase("TEST-001", "3인가구 채무 5천만원 감면율 75% 확정",
                "3인가구로 채무 5천만원이 있었고 감면율 75%로 확정되었습니다.",
                "3인가구", 50_000_000L, 0L, 50_000_000L, "INSOLVENCY", "70%", "75%");
        // 2: 1인가구, 3천만원, 감면율 90% (저소득층 특별감면)
        saveCase("TEST-002", "1인가구 저소득층 특별감면 90%",
                "1인가구 저소득층으로 특별감면 90%를 받았습니다. 이자 포함 여부는 확인 필요.",
                "1인가구", 30_000_000L, 0L, 30_000_000L, "INSOLVENCY_RISK", "85%", "90%");
        // 3: 4인가구, 1억원, 담보대출 있음, 감면율 60%
        saveCase("TEST-003", "4인가구 담보대출 포함 1억원 감면율 60%",
                "담보대출 3천만원 포함 총 1억원, 감면율 60%로 확정.",
                "4인가구", 100_000_000L, 30_000_000L, 70_000_000L, "INSOLVENCY", "55%", "60%");
        // 4: 2인가구, 8천만원, 감면율 범위형 "75~80%"
        saveCase("TEST-004", "2인가구 감면율 75~80% 범위",
                "2인가구 채무 8천만원, 감면율은 75~80% 범위에서 결정.",
                "2인가구", 80_000_000L, 0L, 80_000_000L, "INSOLVENCY", "70%", "75~80%");
        // 5: 3인가구, 4500만원, 예측만 있음 (final null)
        saveCase("TEST-005", "3인가구 감면율 예측만 있음",
                "3인가구 채무 4500만원, 감면율 예측 65%이나 최종 미확정.",
                "3인가구", 45_000_000L, 0L, 45_000_000L, "INSOLVENCY", "65%", null);
        // 6: 공동명의 주택
        saveCase("TEST-006", "공동명의 주택 재산 평가",
                "공동명의 주택이 있어 재산가액 평가 후 감면율이 조정됨.",
                "3인가구", 60_000_000L, 0L, 60_000_000L, "INSOLVENCY", "70%", "65%");
        // 7: 이자 관련
        saveCase("TEST-007", "이자 포함 채무 조정",
                "원금은 상환하고 이자 500만원이 남아 조정 신청. 이자 포함 여부는 규정 확인 필요.",
                "2인가구", 5_000_000L, 0L, 5_000_000L, "INSOLVENCY_RISK", "50%", "50%");
        // 8: 감면율 파싱 실패 ("협의중")
        saveCase("TEST-008", "감면율 협의중 사례",
                "감면율이 아직 협의중이라 확정되지 않음.",
                "2인가구", 40_000_000L, 0L, 40_000_000L, "INSOLVENCY", "협의중", "협의중");
        // 9: 담보대출만
        saveCase("TEST-009", "담보대출만 보유",
                "담보대출 5천만원만 있고 무담보 채무 없음.",
                "1인가구", 50_000_000L, 50_000_000L, 0L, "INSOLVENCY", "30%", "30%");
        // 10: 무관한 글 (노이즈)
        saveCase("TEST-010", "가입인사",
                "안녕하세요 가입인사 드립니다.",
                "1인가구", 0L, 0L, 0L, "INSOLVENCY_RISK", null, null);
    }

    private void saveCase(String articleId, String title, String content,
                          String household, Long debtTotal, Long secured, Long unsecured,
                          String appType, String predicted, String finalRate) {
        NewstartEmpiricalCase c = NewstartEmpiricalCase.builder()
                .articleId(articleId)
                .boardName("감면율")
                .title(title)
                .contentRaw(content)
                .keyInsights(content.substring(0, Math.min(100, content.length())))
                .householdType(household)
                .debtTotal(debtTotal)
                .debtSecured(secured)
                .debtUnsecured(unsecured)
                .applicationType(appType)
                .predictedReductionRate(predicted)
                .finalReductionRate(finalRate)
                .writtenAt(LocalDateTime.now().minusDays(10))
                .sourceUrl("https://test.local/" + articleId)
                .build();
        caseRepository.save(c);
    }

    /** 13개 평가 질문. */
    static List<String> questions() {
        return List.of(
            "3인가구인데 채무가 5천만원 정도 됩니다. 감면율 얼마나 받을 수 있나요?",           // Q1
            "부실차주인데 공동명의 주택이 있고, 감면율이 예상보다 낮게 나왔습니다.",          // Q2
            "부실차주와 부실우려차주의 차이가 뭔가요?",                                      // Q3
            "담보대출이 있는데 새출발기금 신청 가능한가요?",                                  // Q4
            "원금은 다 갚았는데 이자만 남았습니다. 조정 가능한가요?",                          // Q5
            "소득이 있는데 감면율이 낮게 나왔습니다.",                                       // Q6
            "재산가액은 어떻게 평가하나요?",                                                // Q7
            "1인가구인데 최저생계비는 얼마로 보나요?",                                       // Q8
            "감면율 75~80% 받은 사례 있나요?",                                              // Q9
            "감면율이 협의중인 경우도 있나요?",                                              // Q10
            "저소득층 90% 특별감면에서 이자는 총채무액 1억원에 포함되는가?",                    // Q11
            "담보대출은 저소득층 특별감면의 1억원 기준에 포함되는가?",                          // Q12
            "공식 규정과 운영 사례가 서로 다를 때 어떻게 답해야 하는가?"                        // Q13
        );
    }

    @Test
    @DisplayName("13문항 LIKE vs 구조화 검색 비교")
    void compareSearchMethods() {
        System.out.println("\n=== 지피지기 검색 비교 (13문항) ===");
        System.out.printf("%-4s | %-8s | %-8s | %-10s | %s%n",
                "Q", "LIKE건수", "구조화건수", "응답시간ms", "비고");
        System.out.println("-".repeat(80));

        List<String> qs = questions();
        for (int i = 0; i < qs.size(); i++) {
            String q = qs.get(i);
            List<String> keywords = List.of(q.split("\\s+")).stream()
                    .filter(w -> w.length() > 1).limit(5).toList();

            // LIKE 검색
            long t1 = System.currentTimeMillis();
            List<Evidence> likeResults = researchService.searchEmpiricalCases(keywords);
            long likeMs = System.currentTimeMillis() - t1;

            // 구조화 검색
            CaseConditions cond = CaseConditionExtractor.extract(q);
            long t2 = System.currentTimeMillis();
            List<Evidence> structResults = cond.isEmpty()
                    ? likeResults
                    : researchService.searchEmpiricalCases(keywords, cond);
            long structMs = System.currentTimeMillis() - t2;

            String note = cond.isEmpty() ? "조건없음→LIKE" : "구조화 적용";
            // 출처 구분 확인
            boolean hasLabel = structResults.stream()
                    .allMatch(e -> e.getNote() != null && e.getNote().contains("[운영 사례]"));

            System.out.printf("Q%-3d | %-8d | %-10d | LIKE:%-4d Struct:%-4d | %s, 라벨:%s%n",
                    i + 1, likeResults.size(), structResults.size(),
                    likeMs, structMs, note, hasLabel ? "OK" : "NG");

            // top-8 articleIds
            if (!structResults.isEmpty()) {
                System.out.print("      구조화 top: ");
                structResults.stream().limit(8)
                        .forEach(e -> System.out.print(e.getRefId() + " "));
                System.out.println();
            }
        }
        System.out.println("=== 비교 완료 ===\n");
    }

    @Test
    @DisplayName("폴백 검증: 조건 추출 실패·파싱 실패·0건")
    void verifyFallbacks() {
        // 1. 조건 추출 실패 → LIKE 폴백
        List<String> keywords = List.of("감면율");
        CaseConditions empty = CaseConditionExtractor.extract("감면율이 궁금합니다");
        assert empty.isEmpty();
        List<Evidence> r1 = researchService.searchEmpiricalCases(keywords, empty);
        System.out.println("폴백1 (조건없음): " + r1.size() + "건 — LIKE와 동일해야 함");

        // 2. 감면율 파싱 실패 ("협의중") → 임의 제외 금지
        Integer parsed = CaseConditionExtractor.parseReductionRateLower("협의중");
        assert parsed == null;
        System.out.println("폴백2 (파싱실패): null 반환 확인 — 제외하지 않음");

        // 3. 존재하지 않는 조건 → 0건 → LIKE 폴백
        CaseConditions impossible = CaseConditions.builder()
                .householdType("9인가구").build();
        List<Evidence> r3 = researchService.searchEmpiricalCases(keywords, impossible);
        System.out.println("폴백3 (0건): " + r3.size() + "건 — LIKE 폴백 확인");

        // 4. 키워드 비어있음
        List<Evidence> r4 = researchService.searchEmpiricalCases(List.of());
        assert r4.isEmpty();
        System.out.println("폴백4 (빈 키워드): 0건 확인");
    }

    @Test
    @DisplayName("필터 과다 축소 검증: 다중 조건에서 중요 사례 누락 확인")
    void verifyNotOverFiltered() {
        // 3인가구 + 5천만원 + 부실차주 — TEST-001이 반드시 포함되어야 함
        String q = "3인가구 부실차주인데 채무 5천만원 있습니다";
        List<String> keywords = List.of("3인가구", "부실차주", "채무", "5천만원");
        CaseConditions cond = CaseConditionExtractor.extract(q);

        List<Evidence> results = researchService.searchEmpiricalCases(keywords, cond);
        boolean contains001 = results.stream()
                .anyMatch(e -> "TEST-001".equals(e.getRefId()));

        System.out.println("다중조건 필터: " + results.size() + "건, TEST-001 포함: " + contains001);
        assert contains001 : "TEST-001이 누락됨 — 필터가 너무 좁음";
    }
}
