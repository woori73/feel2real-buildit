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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PHASE 2.5 공식자료 REVIEW ingest — docs/newstart/research-official/ 전용.
 *
 * <p>불변식: 저장되는 모든 item의 status는 REVIEW로 강제된다. ACTIVE 생성 경로는 존재하지
 * 않는다. production retrieval(ACTIVE ONLY)은 건드리지 않는다. 동일 title 재ingest는
 * skip한다(멱등성). UNKNOWN/CONFLICT_REVIEW는 추론으로 채우지 않고 그대로 보존한다.
 *
 * <p>대상: comparison/*.json(비교·매트릭스·evidence) + ccrs/k*.json(공식 후보).
 * top10_questions.json·MANIFEST.md·source_registry.json은 ingest하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class NewstartOfficialReviewIngestService {

    private final NewstartKnowledgeItemRepository knowledgeRepository;
    private final NewstartKnowledgeRelationRepository relationRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public record IngestReport(int created, int skipped, int downgraded, int relations, List<String> errors) {}

    private record PendingItem(
            String title, String sourceType, String category, String dimension, String scheme,
            String summary, String content, String sourceUrl, String sourceDate,
            String effectiveFrom, String confidence, String tags, List<String> evidenceIds,
            String rawStatus) {}

    @Transactional
    public IngestReport ingestOfficialReview(Path researchOfficialDir) {
        List<String> errors = new ArrayList<>();
        List<PendingItem> pending = new ArrayList<>();
        collectComparison(researchOfficialDir.resolve("comparison"), pending, errors);
        collectCcrs(researchOfficialDir.resolve("ccrs"), pending, errors);

        int created = 0;
        int skipped = 0;
        int downgraded = 0;
        Map<String, Long> savedIds = new HashMap<>();
        for (PendingItem p : pending) {
            Optional<NewstartKnowledgeItem> existing = knowledgeRepository.findByTitle(p.title());
            if (existing.isPresent()) {
                skipped++;
                savedIds.put(p.title(), existing.get().getKnowledgeId());
                continue;
            }
            if (p.rawStatus() != null && !p.rawStatus().isEmpty() && !"REVIEW".equals(p.rawStatus())) {
                downgraded++;
            }
            NewstartKnowledgeItem saved = knowledgeRepository.save(toEntity(p));
            savedIds.put(p.title(), saved.getKnowledgeId());
            created++;
        }

        int relations = createConflictRelations(savedIds, errors);
        return new IngestReport(created, skipped, downgraded, relations, errors);
    }

    // ---- collectors ----

    private void collectComparison(Path dir, List<PendingItem> out, List<String> errors) {
        if (!Files.isDirectory(dir)) {
            errors.add("comparison dir missing: " + dir);
            return;
        }
        readRows(dir.resolve("comparison_matrix.json"), "rows", errors).forEach(row -> {
            String item = text(row, "item");
            String dim = dimensionOf(item);
            out.add(new PendingItem("[비교/새출발기금] " + item, "OFFICIAL_CURRENT", "부실차주 비교",
                    dim, "NEWSTART_FUND", text(row, "ns"), text(row, "ns"),
                    null, null, null, null,
                    "scheme:NEWSTART_FUND,dimension:" + dim + ",REVIEW,file:comparison_matrix.json",
                    List.of(text(row, "evidence_ns")), "REVIEW"));
            out.add(new PendingItem("[비교/개인워크아웃] " + item, "OFFICIAL_CURRENT", "부실차주 비교",
                    dim, "PERSONAL_WORKOUT", text(row, "pw"), text(row, "pw"),
                    null, null, null, null,
                    "scheme:PERSONAL_WORKOUT,dimension:" + dim + ",REVIEW,file:comparison_matrix.json",
                    List.of(text(row, "evidence_pw")), "REVIEW"));
        });
        readRows(dir.resolve("property_matrix.json"), "rows", errors).forEach(row -> {
            String item = text(row, "item");
            boolean unknown = "UNKNOWN".equals(text(row, "status"));
            out.add(new PendingItem((unknown ? "[재산/UNKNOWN] " : "[재산/비교] ") + item,
                    "OFFICIAL_CURRENT", "부실우려", "PROPERTY", "BOTH",
                    "새출발기금: " + text(row, "ns") + " / 개인워크아웃: " + text(row, "pw"),
                    "새출발기금: " + text(row, "ns") + "\n개인워크아웃: " + text(row, "pw"),
                    null, null, null, unknown ? "UNKNOWN" : null,
                    "dimension:PROPERTY,REVIEW" + (unknown ? ",UNKNOWN" : "") + ",file:property_matrix.json",
                    List.of("MX-PROPERTY"), "REVIEW"));
        });
        readRows(dir.resolve("debt_matrix.json"), "rows", errors).forEach(row -> {
            String debt = text(row, "debt");
            out.add(new PendingItem("[채무/" + text(row, "status") + "] " + debt,
                    "OFFICIAL_CURRENT", "부실차주 신청", "DEBT_SCOPE", "BOTH",
                    text(row, "note"), text(row, "note"),
                    null, null, null, "UNKNOWN".equals(text(row, "status")) ? "UNKNOWN" : null,
                    "dimension:DEBT_SCOPE,REVIEW,debt-status:" + text(row, "status") + ",file:debt_matrix.json",
                    List.of("MX-DEBT"), "REVIEW"));
        });
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().startsWith("evidence_"))
                    .sorted()
                    .forEach(p -> out.add(readEvidence(p, errors)));
        } catch (IOException e) {
            errors.add("list evidence: " + e.getMessage());
        }
        readConcepts(dir.resolve("concepts.json"), out, errors);
    }

    private void collectCcrs(Path dir, List<PendingItem> out, List<String> errors) {
        if (!Files.isDirectory(dir)) {
            errors.add("ccrs dir missing: " + dir);
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().startsWith("k"))
                    .sorted()
                    .forEach(p -> {
                        try {
                            JsonNode n = mapper.readTree(Files.readString(p));
                            if (!n.has("title")) {
                                return;
                            }
                            String reviewState = text(n, "review_state");
                            List<String> ev = new ArrayList<>();
                            if (n.has("evidence_ref") && !text(n, "evidence_ref").isEmpty()) {
                                ev.add(text(n, "evidence_ref"));
                            }
                            String tags = text(n, "tags") + ",REVIEW,file:" + p.getFileName();
                            if ("CONFLICT_REVIEW".equals(reviewState)) {
                                tags += ",CONFLICT_REVIEW";
                            }
                            List<String> cats = new ArrayList<>();
                            if (n.has("category") && n.get("category").isArray()) {
                                n.get("category").forEach(c -> cats.add(c.asText()));
                            }
                            String st = text(n, "source_type");
                            out.add(new PendingItem("[공식] " + text(n, "title"),
                                    st.isEmpty() ? "OFFICIAL_CURRENT" : st,
                                    cats.isEmpty() ? "부실우려" : cats.get(0),
                                    "OFFICIAL", text(n, "source_name"),
                                    text(n, "normalized_content"), text(n, "normalized_content"),
                                    text(n, "source_url"), text(n, "source_date"),
                                    text(n, "effective_from"), null, tags, ev,
                                    text(n, "status")));
                        } catch (IOException e) {
                            errors.add(p.getFileName() + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            errors.add("list ccrs: " + e.getMessage());
        }
    }

    // ---- helpers ----

    private PendingItem readEvidence(Path p, List<String> errors) {
        try {
            JsonNode n = mapper.readTree(Files.readString(p));
            StringBuilder sb = new StringBuilder();
            JsonNode findings = n.get("findings");
            if (findings != null && findings.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = findings.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    sb.append("## ").append(e.getKey()).append("\n");
                    JsonNode v = e.getValue();
                    if (v.isValueNode()) {
                        sb.append(v.asText()).append("\n");
                    } else if (v.has("content")) {
                        sb.append(v.get("content").asText()).append("\n");
                    } else {
                        sb.append(v.toString()).append("\n");
                    }
                }
            }
            String evId = text(n, "evidence_id");
            String title = text(n, "title");
            return new PendingItem("[Evidence] " + (title.isEmpty() ? evId : title),
                    "OFFICIAL_CURRENT", "부실차주 비교", "EVIDENCE", "BOTH",
                    "evidence " + evId, sb.toString(),
                    text(n, "source_url"), text(n, "source_date"), null, null,
                    "dimension:EVIDENCE,REVIEW,evidence:" + evId + ",file:" + p.getFileName(), List.of(evId), "REVIEW");
        } catch (IOException e) {
            errors.add(p.getFileName() + ": " + e.getMessage());
            return new PendingItem("[Evidence/ERROR] " + p.getFileName(), "OFFICIAL_CURRENT",
                    "부실차주 비교", "EVIDENCE", "BOTH", "read error", "",
                    null, null, null, "UNKNOWN", "dimension:EVIDENCE,REVIEW", List.of(), "REVIEW");
        }
    }

    private void readConcepts(Path p, List<PendingItem> out, List<String> errors) {
        try {
            if (!Files.exists(p)) {
                return;
            }
            JsonNode n = mapper.readTree(Files.readString(p));
            JsonNode concepts = n.get("concepts");
            if (concepts != null && concepts.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = concepts.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    out.add(new PendingItem("[개념] " + e.getKey(),
                            "OFFICIAL_CURRENT", "부실우려", "CONCEPT", "BOTH",
                            text(e.getValue(), "definition"), e.getValue().toString(),
                            null, null, null, null, "dimension:CONCEPT,REVIEW,file:concepts.json", List.of(), "REVIEW"));
                }
            }
        } catch (IOException e) {
            errors.add("concepts: " + e.getMessage());
        }
    }

    private List<JsonNode> readRows(Path p, String field, List<String> errors) {
        List<JsonNode> rows = new ArrayList<>();
        try {
            if (!Files.exists(p)) {
                errors.add("missing: " + p.getFileName());
                return rows;
            }
            JsonNode n = mapper.readTree(Files.readString(p));
            if (n.has(field) && n.get(field).isArray()) {
                n.get(field).forEach(rows::add);
            }
        } catch (IOException e) {
            errors.add(p.getFileName() + ": " + e.getMessage());
        }
        return rows;
    }

    private int createConflictRelations(Map<String, Long> savedIds, List<String> errors) {
        Long from = savedIds.get("[공식] [부실우려차주 연체기준]");
        Long to = savedIds.get("[공식] [부실우려 연체하한 evidence: 10일 vs 31일]");
        if (from == null || to == null) {
            return 0;
        }
        boolean dup = relationRepository.findAll().stream().anyMatch(r ->
                r.getFromKnowledgeId().equals(from) && r.getToKnowledgeId().equals(to)
                        && r.getRelationType() == NewstartKnowledgeRelation.RelationType.CONTRADICTS);
        if (dup) {
            return 0;
        }
        try {
            relationRepository.save(NewstartKnowledgeRelation.builder()
                    .fromKnowledgeId(from)
                    .toKnowledgeId(to)
                    .relationType(NewstartKnowledgeRelation.RelationType.CONTRADICTS)
                    .createdBy("INGESTION_BOT")
                    .build());
            return 1;
        } catch (Exception e) {
            errors.add("relation: " + e.getMessage());
            return 0;
        }
    }

    private NewstartKnowledgeItem toEntity(PendingItem p) {
        return NewstartKnowledgeItem.builder()
                .sourceType(NewstartKnowledgeItem.SourceType.valueOf(p.sourceType()))
                .category(p.category())
                .title(p.title())
                .summary(p.summary())
                .content(p.content())
                .authority("부실차주 Wiki REVIEW ingest")
                .tags(p.tags())
                .sourceUrl(p.sourceUrl())
                .sourceDate(parseDate(p.sourceDate()))
                .effectiveFrom(parseDate(p.effectiveFrom()))
                .version(1)
                .status(NewstartKnowledgeItem.KnowledgeStatus.REVIEW)
                .confidence(p.confidence())
                .relatedRuleIds(p.evidenceIds() == null ? null : String.join(",", p.evidenceIds()))
                .createdBy("INGESTION_BOT")
                .updatedBy("INGESTION_BOT")
                .build();
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }

    private String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return "";
        }
        return n.get(field).asText("");
    }

    private String dimensionOf(String itemName) {
        if (itemName == null) {
            return "GENERAL";
        }
        if (itemName.contains("대상자")) {
            return "ELIGIBILITY";
        }
        if (itemName.contains("연체")) {
            return "DELINQUENCY";
        }
        if (itemName.contains("사업자")) {
            return "BUSINESS_REQUIREMENT";
        }
        if (itemName.contains("대상 채무")) {
            return "DEBT_SCOPE";
        }
        if (itemName.contains("제외")) {
            return "DEBT_SCOPE";
        }
        if (itemName.contains("통신")) {
            return "TELECOM";
        }
        if (itemName.contains("재산")) {
            return "PROPERTY";
        }
        if (itemName.contains("소득")) {
            return "INCOME";
        }
        if (itemName.contains("원금")) {
            return "PRINCIPAL_REDUCTION";
        }
        if (itemName.contains("이자")) {
            return "INTEREST";
        }
        if (itemName.contains("상환기간")) {
            return "REPAYMENT_PERIOD";
        }
        if (itemName.contains("추심")) {
            return "COLLECTION_HALT";
        }
        if (itemName.contains("신용정보")) {
            return "CREDIT_INFORMATION";
        }
        if (itemName.contains("중복") || itemName.contains("병행")) {
            return "ROUTING";
        }
        if (itemName.contains("담보")) {
            return "SECURED_DEBT";
        }
        if (itemName.contains("신청") || itemName.contains("절차")) {
            return "APPLICATION";
        }
        return "GENERAL";
    }
}
