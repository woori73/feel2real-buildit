package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeRelation;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeRelationRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PHASE 2 curated REVIEW ingest — Agent3 구조화 산출물(EBOOK 19 section + gamian CASE 11건)을
 * PR #361 승인 계약(REVIEW→ACTIVE는 ADMIN 명시 승인)에 맞게 REVIEW로만 적재한다.
 *
 * <p>불변식:
 * <ul>
 *   <li>생성되는 모든 item의 status는 REVIEW 강제. ACTIVE 생성 경로는 없다.</li>
 *   <li>멱등성: EBOOK은 "[전자책] {sectionId}" title, CASE는 "[사례] {articleId}" title,
 *       empirical case 행은 기존 {@link NewstartDataIngestionService#ingestEmpiricalCases}
 *       의 articleId skip으로 중복 생성 불가.</li>
 *   <li>prediction → confirmed 변환 금지. operator-prediction 게시판 출처는 OPERATOR_PREDICTION
 *       태그로 분리 보존하고 finalReductionRate를 never 복사한다.</li>
 *   <li>UNKNOWN/null은 추론으로 채우지 않고 "UNKNOWN" 문자열/미기재로 보존.</li>
 *   <li>충돌(EV-NS-PROP-2026 vs 전자책 60~80% vs 90% 확정 사례, k02 10일 vs 31일)은
 *       CONFLICT_REVIEW 태그 + (양쪽 item이 실재할 때만) CONTRADICTS relation row로 보존하며
 *       원문 수치를 수정하지 않는다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class NewstartCuratedReviewIngestService {

    static final String EBOOK_AUTHORITY = "부실차주 전자책 REVIEW ingest";
    static final String CASE_AUTHORITY = "부실차주 카페사례 REVIEW ingest";
    static final String PREDICTION_BOARD = "감면율과 확정금액 예측 게시판";
    static final String EV_NS_PROP_2026_TITLE = "[Evidence] [2026 새출발기금 재산심사·감면차등화 evidence]";
    static final String K02_TITLE = "[공식] [부실우려차주 연체기준]";

    private final NewstartKnowledgeItemRepository knowledgeRepository;
    private final NewstartKnowledgeRelationRepository relationRepository;
    private final NewstartDataIngestionService dataIngestionService;
    private final ObjectMapper mapper = new ObjectMapper();

    public record CuratedIngestReport(
            int ebookCreated, int ebookSkipped,
            int caseCreated, int caseSkipped,
            int caseRowsCreated, int caseRowsSkipped, int caseRowsFailed,
            int relationsCreated,
            List<String> errors) {}

    @Transactional
    public CuratedIngestReport ingestCuratedReview(Path ebookSectionsJson, Path gamianArticlesDir) {
        List<String> errors = new ArrayList<>();
        Map<String, Long> savedIds = new HashMap<>();

        int[] ebook = ingestEbook(ebookSectionsJson, savedIds, errors);
        NewstartDataIngestionService.IngestionResult rows =
                dataIngestionService.ingestEmpiricalCases(gamianArticlesDir.toString());

        int[] cases = ingestCaseKnowledge(gamianArticlesDir, savedIds, errors);

        int relations = createConflictRelations(savedIds);

        return new CuratedIngestReport(ebook[0], ebook[1], cases[0], cases[1],
                rows.getSuccess(), rows.getSkipped(), rows.getFailed(), relations, errors);
    }

    // ---------------- EBOOK ----------------

    private int[] ingestEbook(Path sectionsJson, Map<String, Long> savedIds, List<String> errors) {
        int created = 0;
        int skipped = 0;
        JsonNode root;
        try {
            root = mapper.readTree(Files.readString(sectionsJson));
        } catch (IOException e) {
            errors.add("ebook_sections: " + e.getMessage());
            return new int[]{0, 0};
        }
        String ragImportance = optText(root, "rag_importance");
        for (JsonNode s : root.path("sections")) {
            String sectionId = optText(s, "id");
            String title = "[전자책] " + sectionId + " " + optText(s, "chapter") + " :: " + optText(s, "title");
            Optional<NewstartKnowledgeItem> existing = knowledgeRepository.findByTitle(title);
            if (existing.isPresent()) {
                skipped++;
                savedIds.put(sectionId, existing.get().getKnowledgeId());
                continue;
            }
            boolean hasConflict = hasRelationType(s, "CONFLICT_REVIEW");
            NewstartKnowledgeItem saved = knowledgeRepository.save(NewstartKnowledgeItem.builder()
                    .sourceType(NewstartKnowledgeItem.SourceType.EBOOK)
                    .category(orElse(optText(s, "category"), "부실차주 일반"))
                    .title(title)
                    .summary(orElse(optText(s, "topic"), "") + " — " + firstOrEmpty(s.path("key_claims")))
                    .content(buildEbookContent(s, ragImportance))
                    .authority(EBOOK_AUTHORITY)
                    .tags(buildEbookTags(s, sectionId, ragImportance, hasConflict))
                    .sourceUrl("docs/newstart/ebook/ebook_sections.json")
                    .version(1)
                    .status(NewstartKnowledgeItem.KnowledgeStatus.REVIEW)
                    .relatedRuleIds(joinRelationTargets(s))
                    .relatedCaseIds(joinList(s.path("related_case_articles")))
                    .createdBy("INGESTION_BOT")
                    .updatedBy("INGESTION_BOT")
                    .build());
            savedIds.put(sectionId, saved.getKnowledgeId());
            created++;
        }
        return new int[]{created, skipped};
    }

    private String buildEbookContent(JsonNode s, String ragImportance) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 메타\n");
        sb.append("- section: ").append(optText(s, "id")).append("\n");
        sb.append("- chapter: ").append(optText(s, "chapter")).append("\n");
        sb.append("- source_lines: ").append(orElse(optText(s, "source_lines"), "UNKNOWN")).append("\n");
        sb.append("- topic: ").append(orElse(optText(s, "topic"), "UNKNOWN")).append("\n");
        sb.append("- rag_importance: ").append(orElse(ragImportance, "UNKNOWN")).append("\n");
        appendList(sb, "## 질문 의도", s.path("question_intents"));
        appendList(sb, "## 핵심 주장", s.path("key_claims"));
        appendList(sb, "## 실무 판단", s.path("practical_guidance"));
        appendList(sb, "## 주의", s.path("cautions"));
        sb.append("## 크로스소스 관계 (보존 — 자동 해결 금지)\n");
        for (JsonNode r : s.path("relations")) {
            sb.append("- ").append(optText(r, "to")).append(" | ").append(optText(r, "type"))
              .append(" | ").append(orElse(optText(r, "note"), "UNKNOWN")).append("\n");
        }
        appendList(sb, "## 관련 사례 article", s.path("related_case_articles"));
        return sb.toString();
    }

    private String buildEbookTags(JsonNode s, String sectionId, String ragImportance, boolean hasConflict) {
        StringBuilder tags = new StringBuilder("EBOOK,REVIEW,section:")
                .append(sectionId)
                .append(",chapter:").append(optText(s, "chapter"))
                .append(",rag_importance:").append(orElse(ragImportance, "UNKNOWN"));
        if (hasConflict) {
            tags.append(",CONFLICT_REVIEW");
        }
        return tags.toString();
    }

    // ---------------- CASE knowledge ----------------

    private int[] ingestCaseKnowledge(Path dir, Map<String, Long> savedIds, List<String> errors) {
        int created = 0;
        int skipped = 0;
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().startsWith("article_"))
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            errors.add("list gamian dir: " + e.getMessage());
            return new int[]{0, 0};
        }
        for (Path p : files) {
            try {
                JsonNode n = mapper.readTree(Files.readString(p));
                String articleId = optText(n, "articleId");
                String title = "[사례] " + articleId + " " + optText(n, "title");
                Optional<NewstartKnowledgeItem> existing = knowledgeRepository.findByTitle(title);
                if (existing.isPresent()) {
                    skipped++;
                    savedIds.put("CASE:" + articleId, existing.get().getKnowledgeId());
                    continue;
                }
                String classification = classify(n);
                boolean conflictWithOfficialCap = "90".equals(optText(n, "finalReductionRate"));
                NewstartKnowledgeItem saved = knowledgeRepository.save(NewstartKnowledgeItem.builder()
                        .sourceType(NewstartKnowledgeItem.SourceType.CASE)
                        .category("부실차주 사례")
                        .title(title)
                        .summary(orElse(optText(n, "authorFollowupResult"), optText(n, "title")))
                        .content(buildCaseContent(n, classification))
                        .authority(CASE_AUTHORITY)
                        .tags("CASE,REVIEW," + classification + ",board:" + optText(n, "boardName")
                                + (conflictWithOfficialCap ? ",CONFLICT_REVIEW" : ""))
                        .sourceUrl(optText(n, "sourceUrl"))
                        .version(1)
                        .status(NewstartKnowledgeItem.KnowledgeStatus.REVIEW)
                        .relatedCaseIds(articleId)
                        .createdBy("INGESTION_BOT")
                        .updatedBy("INGESTION_BOT")
                        .build());
                savedIds.put("CASE:" + articleId, saved.getKnowledgeId());
                created++;
            } catch (IOException e) {
                errors.add(p.getFileName() + ": " + e.getMessage());
            }
        }
        return new int[]{created, skipped};
    }

    /** prediction을 confirmed로 변환하지 않는다: classification은 출처 게시판과 확정 필드 유무로만 결정. */
    private String classify(JsonNode n) {
        String finalRate = optText(n, "finalReductionRate");
        if (!finalRate.isEmpty() && !"null".equals(finalRate)) {
            return "RESULT_CONFIRMED";
        }
        if (PREDICTION_BOARD.equals(optText(n, "boardName"))) {
            return "OPERATOR_PREDICTION";
        }
        return "RESULT_PARTIAL";
    }

    private String buildCaseContent(JsonNode n, String classification) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 분류\n- ").append(classification)
          .append(" (OPERATOR_PREDICTION은 운영자 예측이며 확정 결과가 아니다)\n");
        sb.append("## 결과/상태\n");
        sb.append("- 확정 감면율: ").append(label(optText(n, "finalReductionRate"))).append("\n");
        sb.append("- 임시 감면율: ").append(label(optText(n, "tempReductionRate"))).append("\n");
        sb.append("- 예측 감면율(운영자): ").append(label(optText(n, "predictedReductionRate"))).append("\n");
        sb.append("- 결과 추적(author followup): ").append(label(optText(n, "authorFollowupResult"))).append("\n");
        sb.append("- 예측 vs 실제: ").append(label(optText(n, "predictionVsActual"))).append("\n");
        sb.append("## 상황\n");
        sb.append("- 채무합계: ").append(label(n.path("debtTotal"))).append("\n");
        sb.append("- 채무분해: ").append(label(optText(n, "debtBreakdown"))).append("\n");
        sb.append("- 재산(부동산/차량/금융): ")
          .append(label(n.path("assetProperty"))).append(" / ")
          .append(label(n.path("assetVehicle"))).append(" / ")
          .append(label(n.path("assetFinancial"))).append("\n");
        sb.append("- 소득(2025/현재): ").append(label(n.path("income2025"))).append(" / ")
          .append(label(n.path("incomeCurrent"))).append("\n");
        sb.append("- 가구/나이/직업상태: ").append(label(optText(n, "householdType"))).append(" / ")
          .append(label(n.path("age"))).append(" / ").append(label(optText(n, "businessStatus"))).append("\n");
        sb.append("- 신청 유형: ").append(label(optText(n, "applicationType"))).append("\n");
        sb.append("- 운영자 예측 전문: ").append(label(optText(n, "managerPrediction"))).append("\n");
        sb.append("- 특수상황: ").append(label(optText(n, "specialCircumstances"))).append("\n");
        sb.append("- 핵심 인사이트: ").append(label(optText(n, "keyInsights"))).append("\n");
        sb.append("## 원문 본문\n").append(optText(n, "contentRaw")).append("\n");
        return sb.toString();
    }

    // ---------------- relations (충돌 보존) ----------------

    private int createConflictRelations(Map<String, Long> savedIds) {
        int created = 0;
        Long evNsProp = resolve(EV_NS_PROP_2026_TITLE);
        if (evNsProp != null) {
            for (String sectionId : List.of("EBK-2-1", "EBK-2-2", "EBK-7-1")) {
                if (saveContradicts(savedIds.get(sectionId), evNsProp)) {
                    created++;
                }
            }
            for (String caseId : List.of("5866", "5952")) {
                if (saveContradicts(savedIds.get("CASE:" + caseId), evNsProp)) {
                    created++;
                }
            }
        }
        Long k02 = resolve(K02_TITLE);
        if (k02 != null) {
            for (String sectionId : List.of("EBK-5-1", "EBK-6-2")) {
                if (saveContradicts(savedIds.get(sectionId), k02)) {
                    created++;
                }
            }
        }
        return created;
    }

    private Long resolve(String officialTitle) {
        return knowledgeRepository.findByTitle(officialTitle)
                .map(NewstartKnowledgeItem::getKnowledgeId)
                .orElse(null);
    }

    private boolean saveContradicts(Long from, Long to) {
        if (from == null || to == null) {
            return false;
        }
        boolean dup = relationRepository.findAll().stream().anyMatch(r ->
                r.getFromKnowledgeId().equals(from) && r.getToKnowledgeId().equals(to)
                        && r.getRelationType() == NewstartKnowledgeRelation.RelationType.CONTRADICTS);
        if (dup) {
            return false;
        }
        relationRepository.save(NewstartKnowledgeRelation.builder()
                .fromKnowledgeId(from)
                .toKnowledgeId(to)
                .relationType(NewstartKnowledgeRelation.RelationType.CONTRADICTS)
                .createdBy("INGESTION_BOT")
                .build());
        return true;
    }

    // ---------------- helpers ----------------

    private void appendList(StringBuilder sb, String header, JsonNode arr) {
        if (arr.isArray() && !arr.isEmpty()) {
            sb.append(header).append("\n");
            arr.forEach(v -> sb.append("- ").append(v.asText()).append("\n"));
        }
    }

    private boolean hasRelationType(JsonNode s, String type) {
        for (JsonNode r : s.path("relations")) {
            if (type.equals(optText(r, "type"))) {
                return true;
            }
        }
        return false;
    }

    private String joinRelationTargets(JsonNode s) {
        List<String> ids = new ArrayList<>();
        for (JsonNode r : s.path("relations")) {
            String to = optText(r, "to");
            if (!to.isEmpty()) {
                ids.add(to);
            }
        }
        return ids.isEmpty() ? null : String.join(",", ids);
    }

    private String joinList(JsonNode arr) {
        if (!arr.isArray() || arr.isEmpty()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        arr.forEach(v -> out.add(v.asText()));
        return String.join(",", out);
    }

    private String firstOrEmpty(JsonNode arr) {
        return arr.isArray() && !arr.isEmpty() ? arr.get(0).asText() : "";
    }

    private String optText(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    private String orElse(String v, String fallback) {
        return v == null || v.isEmpty() ? fallback : v;
    }

    private String label(JsonNode v) {
        return v == null || v.isNull() ? "UNKNOWN" : v.asText();
    }

    private String label(String v) {
        return v == null || v.isEmpty() ? "UNKNOWN" : v;
    }
}
