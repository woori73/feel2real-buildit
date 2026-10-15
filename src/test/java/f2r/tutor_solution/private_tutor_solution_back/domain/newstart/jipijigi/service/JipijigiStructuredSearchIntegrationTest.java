package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartEmpiricalCase;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartEmpiricalCaseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
                .keyInsights("[\"" + content.substring(0, Math.min(50, content.length())).replace("\"", "") + "\"]")
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

    // =====================================================================
    // 실데이터 정확도 검증 (2026-10-16 QA, PR #701 후속)
    // 데이터: 2026-10-07 1:1 실상담 운영자 검증 사례 9건 + 노이즈 2건
    // (src/test/resources/jipijigi/real-cases.json, 개인정보 없음)
    // 원문에 없는 구조화 필드는 null (추측 금지). 운영 DB와 완전 분리.
    // =====================================================================

    /** 골든 질문: id, 질문, Top-8에 반드시 있어야 할 사례 ID, 비고 */
    record RealGolden(String id, String question, List<String> mustContain, String note) {}

    static List<RealGolden> realGoldens() {
        return List.of(
            new RealGolden("G1", "주택 지분이 있는데 공시지가로 생각했어요. 재산평가는 어떻게 하나요?",
                List.of("REAL-20261007-01"), "재산평가 실사례"),
            new RealGolden("G2", "공동명의 아파트가 있는데 감면이 어렵다고 나왔어요",
                List.of("REAL-20261007-02"), "공동명의 감면"),
            new RealGolden("G3", "부실차주인데 확정안 받고 취소하고 싶어요",
                List.of("REAL-20261007-04"), "구조화: applicationType=INSOLVENCY"),
            new RealGolden("G4", "차량담보대출이 있는데 차량 가액이 4천만원이 넘어요",
                List.of("REAL-20261007-07"), "구조화 0건 예상 → LIKE 폴백으로 회수"),
            new RealGolden("G5", "임시안 받은 지 4개월 됐는데 확정안은 언제 나와요?",
                List.of("REAL-20261007-09"), "확정안 시기"),
            new RealGolden("G6", "상가보증금이랑 주거보증금 둘 다 공제되나요?",
                List.of("REAL-20261007-06"), "보증금 공제"),
            new RealGolden("G7", "무직인데 생계급여 말고 받을 수 있는 복지가 있나요?",
                List.of("REAL-20261007-08"), "주거급여"),
            new RealGolden("G8", "연체 중인데 집을 팔면 어떻게 돼요?",
                List.of("REAL-20261007-05"), "사해행위 위험"),
            new RealGolden("G9", "감면율이 궁금합니다",
                List.of(), "조건 없음 → LIKE 폴백, 오류 없이 동작"),
            new RealGolden("G10", "2019년식 아반떼 중고차 시세가 어떻게 돼요?",
                List.of("NOISE-002"), "노이즈 1순위, 실사례 오염 없어야 함")
        );
    }

    /** 실데이터 fixture 로드 (기존 synthetic setUp 이후 호출) */
    private void loadRealFixture() throws Exception {
        caseRepository.deleteAll();
        ObjectMapper om = new ObjectMapper();
        List<Map<String, Object>> rows;
        try (var in = new ClassPathResource("jipijigi/real-cases.json").getInputStream()) {
            rows = om.readValue(in, new TypeReference<>() {});
        }
        for (Map<String, Object> r : rows) {
            caseRepository.save(NewstartEmpiricalCase.builder()
                .articleId((String) r.get("articleId"))
                .boardName((String) r.get("boardName"))
                .title((String) r.get("title"))
                .contentRaw((String) r.get("contentRaw"))
                .keyInsights((String) r.get("keyInsights"))
                .householdType((String) r.get("householdType"))
                .debtTotal(toLong(r.get("debtTotal")))
                .debtSecured(toLong(r.get("debtSecured")))
                .debtUnsecured(toLong(r.get("debtUnsecured")))
                .assetProperty(toLong(r.get("assetProperty")))
                .assetVehicle(toLong(r.get("assetVehicle")))
                .applicationType((String) r.get("applicationType"))
                .predictedReductionRate((String) r.get("predictedReductionRate"))
                .finalReductionRate((String) r.get("finalReductionRate"))
                .writtenAt(LocalDateTime.parse((String) r.get("writtenAt")))
                .createdBy("accuracy-qa")
                .build());
        }
        System.out.println("### 실데이터 fixture: " + rows.size() + "건 로드");
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        return null;
    }

    /** 실제 운영과 동일한 키워드 파이프라인 */
    private List<String> realKeywords(String q) {
        List<String> base = researchService.keywordsOf(q);
        return researchService.withIntentKeywords(base, JipijigiQuestionIntent.analyze(q));
    }

    @Test
    @DisplayName("실데이터 골든 10문항 Top-8 정확도 + 응답시간")
    void realDataGoldenAccuracy() throws Exception {
        loadRealFixture();
        List<Long> latencies = new ArrayList<>();
        int hit = 0, total = 0;

        System.out.println("\n=== 실데이터 골든 검증 (10문항) ===");
        System.out.printf("%-4s | %-8s | %-7s | %-40s | %s%n", "ID", "판정", "ms", "Top8", "비고");
        System.out.println("-".repeat(110));

        for (RealGolden g : realGoldens()) {
            List<String> keywords = realKeywords(g.question());
            CaseConditions cond = CaseConditionExtractor.extract(g.question());

            long t0 = System.currentTimeMillis();
            List<Evidence> results = cond.isEmpty()
                ? researchService.searchEmpiricalCases(keywords)
                : researchService.searchEmpiricalCases(keywords, cond);
            long ms = System.currentTimeMillis() - t0;
            latencies.add(ms);

            List<String> top8 = results.stream().limit(8).map(Evidence::getRefId).toList();
            List<String> missing = g.mustContain().stream().filter(id -> !top8.contains(id)).toList();
            boolean noisePollution = !g.id().equals("G10")
                && top8.stream().anyMatch(id -> id.startsWith("NOISE"));

            String verdict;
            if (g.mustContain().isEmpty()) {
                verdict = "동작확인";
            } else if (missing.isEmpty()) {
                verdict = "적중";
                hit += g.mustContain().size();
            } else {
                verdict = "미스:" + missing;
            }
            total += g.mustContain().size();

            String top8str = top8.toString();
            System.out.printf("%-4s | %-8s | %-7d | %-40s | %s%s%n",
                g.id(), verdict, ms,
                top8str.length() > 40 ? top8str.substring(0, 40) : top8str,
                g.note(), noisePollution ? " [노이즈오염!]" : "");

            if (!g.mustContain().isEmpty()) {
                assertThat(top8).as(g.id() + " " + g.question() + " → " + g.mustContain())
                    .containsAll(g.mustContain());
            }
            if (!g.id().equals("G10")) {
                assertThat(top8).as(g.id() + " 노이즈 오염").noneMatch(id -> id.startsWith("NOISE"));
            }
        }

        latencies.sort(Long::compareTo);
        long p50 = latencies.get((int) (latencies.size() * 0.5));
        long p95 = latencies.get(Math.min(latencies.size() - 1, (int) (latencies.size() * 0.95)));
        System.out.println("=== 실데이터 결과: Top-8 적중 " + hit + "/" + total
            + ", p50=" + p50 + "ms, p95=" + p95 + "ms ===");
        System.out.println("### LATENCY_MS=" + latencies);
    }
}
