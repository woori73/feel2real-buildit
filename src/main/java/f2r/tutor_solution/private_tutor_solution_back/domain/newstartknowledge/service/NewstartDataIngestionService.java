package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartEmpiricalCase;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartEmpiricalCaseRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NewstartDataIngestionService {

    private final NewstartEmpiricalCaseRepository empiricalCaseRepository;
    private final NewstartKnowledgeItemRepository knowledgeRepository;

    /**
     * 수집된 JSON 파일들로부터 실증 사례를 DB에 적재
     * data/newstart-cafe/gamian/articles/ 디렉토리의 JSON 파일들을 읽어옴
     */
    @Transactional
    public IngestionResult ingestEmpiricalCases(String articlesDirPath) {
        Path dir = Paths.get(articlesDirPath);
        if (!Files.exists(dir)) {
            return IngestionResult.failure("Directory not found: " + articlesDirPath);
        }

        int success = 0;
        int skipped = 0;
        int failed = 0;
        List<String> errors = new java.util.ArrayList<>();

        try (var stream = Files.list(dir)) {
            List<Path> jsonFiles = stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .collect(Collectors.toList());

            for (Path file : jsonFiles) {
                try {
                    String content = Files.readString(file);
                    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    var node = mapper.readTree(content);

                    // 이미 존재하는지 확인
                    String articleId = node.get("articleId").asText();
                    Optional<NewstartEmpiricalCase> existing = empiricalCaseRepository.findByArticleId(articleId);
                    if (existing.isPresent()) {
                        skipped++;
                        continue;
                    }

                    NewstartEmpiricalCase caseEntity = buildCaseEntity(node);
                    empiricalCaseRepository.save(caseEntity);
                    success++;

                } catch (Exception e) {
                    failed++;
                    errors.add(file.getFileName() + ": " + e.getMessage());
                }
            }

        } catch (IOException e) {
            return IngestionResult.failure("IO Error: " + e.getMessage());
        }

        return IngestionResult.builder()
                .success(success)
                .skipped(skipped)
                .failed(failed)
                .errors(errors)
                .build();
    }

    /**
     * 수집된 사례들로부터 Knowledge Item 후보 생성 (감면율 규칙 등)
     */
    @Transactional
    public int generateKnowledgeFromCases() {
        List<NewstartEmpiricalCase> cases = empiricalCaseRepository.findWithActualReductionRate();
        int created = 0;

        for (NewstartEmpiricalCase c : cases) {
            // 실제 감면율이 있는 사례에서 파생 규칙 지식 생성
            if (c.getFinalReductionRate() != null && !c.getFinalReductionRate().isBlank()) {
                String title = String.format("[감면율 사례] %s - %s%% 확정", c.getTitle(), c.getFinalReductionRate());
                Optional<NewstartKnowledgeItem> existing = knowledgeRepository.findByTitle(title);
                if (existing.isPresent()) continue;

                NewstartKnowledgeItem rule = NewstartKnowledgeItem.builder()
                        .sourceType(NewstartKnowledgeItem.SourceType.DERIVED_RULE)
                        .category("감면율")
                        .title(title)
                        .summary(buildRuleSummary(c))
                        .content(buildRuleContent(c))
                        .authority("부실차주 Wiki 파생 규칙")
                        .tags("감면율,실제사례,파생규칙")
                        .sourceUrl(c.getSourceUrl())
                        .sourceDate(c.getWrittenAt() != null ? c.getWrittenAt().toLocalDate() : null)
                        .effectiveFrom(c.getWrittenAt() != null ? c.getWrittenAt().toLocalDate() : null)
                        .version(1)
                        .status(NewstartKnowledgeItem.KnowledgeStatus.REVIEW)
                        .confidence("MEDIUM")
                        .relatedCaseIds(c.getArticleId())
                        .createdBy("INGESTION_BOT")
                        .updatedBy("INGESTION_BOT")
                        .build();

                knowledgeRepository.save(rule);
                created++;
            }
        }
        return created;
    }

    private String buildRuleSummary(NewstartEmpiricalCase c) {
        return String.format(
                "채무 %s만원, 소득 %s만원, %s, %s, 재산 %s만원 조건에서 감면율 %s%% 확정. %s",
                c.getDebtTotal() != null ? c.getDebtTotal() / 10000 : "미상",
                c.getIncome2025() != null ? c.getIncome2025() / 10000 : "미상",
                c.getHouseholdType() != null ? c.getHouseholdType() : "미상",
                c.getAge() != null ? c.getAge() + "세" : "미상",
                c.getAssetProperty() != null ? c.getAssetProperty() / 10000 : "미상",
                c.getFinalReductionRate(),
                c.getSpecialCircumstances() != null ? c.getSpecialCircumstances() : ""
        );
    }

    private String buildRuleContent(NewstartEmpiricalCase c) {
        return String.format("""
                # 감면율 실제 사례 기반 파생 규칙
                
                ## 사례 개요
                - 게시글: %s (ID: %s)
                - 작성자: %s (%s)
                - 작성일: %s
                - 실제 확정 감면율: %s
                
                ## 신청인 조건
                - 채무 총액: %s 원
                - 2025년 소득: %s 원
                - 가구 유형: %s
                - 연령: %s
                - 부동산 자산: %s 원
                - 차량 자산: %s 원
                - 금융 자산: %s 원
                - 고용 상태: %s
                - 사업 상태: %s
                
                ## 실제 결과
                - 확정 감면율: %s
                - 최종 상환금액: %s 원
                - 상환 기간: %s 개월
                - 거치 기간: %s 개월
                - 월 상환액: %s 원
                
                ## 특수 상황
                %s
                
                ## 핵심 인사이트
                %s
                
                ---
                *출처: 네이버 카페 '새출발기금 부실차주 라이브러리' 감면율 게시판
                *원문: %s
                *수집일: %s
                """,
                c.getTitle(), c.getArticleId(),
                c.getAuthorNickname(), c.getAuthorGrade(),
                c.getWrittenAt(),
                c.getFinalReductionRate(),
                c.getDebtTotal(),
                c.getIncome2025(),
                c.getHouseholdType(),
                c.getAge(),
                c.getAssetProperty(),
                c.getAssetVehicle(),
                c.getAssetFinancial(),
                c.getEmploymentStatus(),
                c.getBusinessStatus(),
                c.getFinalReductionRate(),
                c.getFinalRepaymentAmount(),
                c.getRepaymentPeriodMonths(),
                c.getGracePeriodMonths(),
                c.getMonthlyPayment(),
                c.getSpecialCircumstances(),
                c.getKeyInsights(),
                c.getSourceUrl(),
                c.getCollectedAt()
        );
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IngestionResult {
        private int success;
        private int skipped;
        private int failed;
        private List<String> errors;

        public static IngestionResult failure(String error) {
            return IngestionResult.builder()
                    .success(0)
                    .skipped(0)
                    .failed(1)
                    .errors(List.of(error))
                    .build();
        }
    }
}