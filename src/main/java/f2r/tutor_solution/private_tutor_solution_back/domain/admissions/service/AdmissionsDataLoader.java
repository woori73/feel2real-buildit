package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsDepartment;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsOfficialMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsSchedule;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsType;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsUniversity;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsDepartmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsOfficialMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsScheduleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsTypeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsUniversityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Qoder 입시 자료 적재기 (개발·검증 환경용).
 *
 * <p>입력: {@code woori73/buildit-admissions}의 {@code data/seed/ipsi_seed_univ_sample.json}
 * 및 {@code docs/ANALYSIS/03_counseling_qa/knowledge_base.json}.
 *
 * <p><b>적재된 모든 자료는 DRAFT 상태로 시작한다.</b>
 * REVIEW → ACTIVE 승격은 관리자 승인 API를 통해서만 가능하다 (자동 승격 없음).
 * 운영 DB 적재와 APPROVED 승격은 별도 승인 전까지 금지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionsDataLoader {

    private final ObjectMapper objectMapper;
    private final AdmissionsUniversityRepository universityRepository;
    private final AdmissionsOfficialMaterialRepository materialRepository;
    private final AdmissionsDepartmentRepository departmentRepository;
    private final AdmissionsTypeRepository typeRepository;
    private final AdmissionsScheduleRepository scheduleRepository;
    private final AdmissionsKnowledgeItemRepository knowledgeRepository;

    public record LoadResult(int universities, int materials, int departments,
                             int types, int schedules, int knowledgeItems, int skipped) {}

    /** 시드 구조/참조 오류. 던지면 트랜잭션이 롤백되어 부분 적재가 남지 않는다. */
    public static class SeedLoadException extends IllegalArgumentException {
        public SeedLoadException(String message) { super(message); }
    }

    /**
     * 시드 JSON 전체를 적재한다. 같은 시드를 다시 실행해도 중복되지 않는다
     * (대학명 / 자료 제목 / 대학+학년도+학과·전형명 / 대학+학년도+일정 제목+비고 기준).
     * 구조 오류(대학 목록 없음, 알 수 없는 대학 코드)는 {@link SeedLoadException}으로 전체 롤백한다.
     * 학년도 미확정·미지원 행만 건너뛰며 그 건수를 {@code skipped}로 돌려준다.
     */
    @Transactional
    public LoadResult loadSeed(String seedJson) throws Exception {
        JsonNode root = objectMapper.readTree(seedJson);
        if (!root.path("ipsi_universities").isArray() || root.path("ipsi_universities").isEmpty()) {
            throw new SeedLoadException("ipsi_universities가 없거나 비어 있습니다.");
        }
        Map<String, AdmissionsUniversity> univByCode = new HashMap<>();
        Map<String, AdmissionsOfficialMaterial> matByKey = new HashMap<>();
        // 재실행 중복 방지: 적재 전에 이미 있는 행의 자연키 스냅샷(같은 파일 안의 원본 중복행은 보존한다).
        java.util.Set<String> existingDept = new java.util.HashSet<>();
        departmentRepository.findAll().forEach(d -> existingDept.add(
                d.getUniversity().getUniversityId() + "|" + d.getAcademicYear() + "|" + d.getName()));
        java.util.Set<String> existingType = new java.util.HashSet<>();
        typeRepository.findAll().forEach(t -> existingType.add(
                t.getUniversity().getUniversityId() + "|" + t.getAcademicYear() + "|" + t.getName()));
        java.util.Set<String> existingSched = new java.util.HashSet<>();
        scheduleRepository.findAll().forEach(sc -> existingSched.add(
                sc.getUniversity().getUniversityId() + "|" + sc.getAcademicYear() + "|" + sc.getTitle() + "|" + sc.getNote()));
        Map<String, AdmissionsOfficialMaterial> materialByTitle = new HashMap<>();
        materialRepository.findAll().forEach(m -> materialByTitle.put(m.getTitle(), m));

        int univCount = 0;
        for (JsonNode u : root.path("ipsi_universities")) {
            String name = u.path("name").asText();
            if (universityRepository.findByName(name).isPresent()) {
                univByCode.put(u.path("university_code").asText(),
                        universityRepository.findByName(name).orElseThrow());
                continue;
            }
            AdmissionsUniversity univ = AdmissionsUniversity.builder()
                    .name(name)
                    .region(u.path("region").asText(null))
                    .type(u.path("establishment_type").asText(null))
                    .homepageUrl(u.path("admission_office_url").asText(null))
                    .build();
            univ = universityRepository.save(univ);
            univByCode.put(u.path("university_code").asText(), univ);
            univCount++;
        }

        int skipped = 0;
        int matCount = 0;
        for (JsonNode d : root.path("ipsi_documents")) {
            String docKey = d.path("document_key").asText();
            // 중복 방지: 같은 제목의 자료가 이미 있으면 새로 만들지 않고 출처 매핑만 연결한다.
            String title = d.path("document_name").asText();
            AdmissionsOfficialMaterial already = materialByTitle.get(title);
            if (already != null) {
                matByKey.put(docKey, already);
                continue;
            }

            Integer matYear = readSupportedYear(d.path("applicable_academic_year"));
            if (matYear == null) {
                log.warn("admissions seed: 적용 학년도 미확정/미지원 자료는 적재하지 않는다 title={} year={}",
                        title, d.path("applicable_academic_year"));
                skipped++;
                continue;
            }
            String univCode = extractUnivCode(docKey);
            AdmissionsUniversity univ = univByCode.get(univCode);
            AdmissionsOfficialMaterial mat = AdmissionsOfficialMaterial.builder()
                    .university(univ)
                    .academicYear(matYear)
                    .title(title)
                    .issuer(d.path("institution_name").asText("미확인"))
                    .publishDate(parseDate(d.path("published_at").asText(null)))
                    .documentUrl(d.path("source_url").asText(null))
                    .fileKey(docKey)
                    .build();
            mat = materialRepository.save(mat);
            matByKey.put(docKey, mat);
            materialByTitle.put(title, mat);
            matCount++;
        }

        int deptCount = 0;
        for (JsonNode dep : root.path("ipsi_departments")) {
            AdmissionsUniversity univ = requireUniversity(univByCode, dep.path("university_code").asText(), "ipsi_departments");
            Integer year = readSupportedYear(dep.path("academic_year"));
            if (year == null) { skipped++; continue; }
            if (existingDept.contains(univ.getUniversityId() + "|" + year + "|" + dep.path("department_name").asText())) continue;
            AdmissionsDepartment dept = AdmissionsDepartment.builder()
                    .university(univ)
                    .academicYear(year)
                    .name(dep.path("department_name").asText())
                    .quotaInfo(dep.path("counts_by_type").toString())
                    .source(matByKey.get(dep.path("source_document_key").asText(null)))
                    .build();
            departmentRepository.save(dept);
            deptCount++;
        }

        int typeCount = 0;
        for (JsonNode t : root.path("ipsi_admission_types")) {
            AdmissionsUniversity univ = requireUniversity(univByCode, t.path("university_code").asText(), "ipsi_admission_types");
            Integer year = readSupportedYear(t.path("academic_year"));
            if (year == null) { skipped++; continue; }
            if (existingType.contains(univ.getUniversityId() + "|" + year + "|" + t.path("type_name").asText())) continue;
            StringBuilder details = new StringBuilder();
            details.append("모집인원: ").append(t.path("recruitment_count").asText("미확인")).append("\n");
            details.append("지원자격: ").append(t.path("eligibility").asText("")).append("\n");
            details.append("전형방법: ").append(t.path("student_record_method").asText("")).append("\n");
            details.append("수능최저: ").append(t.path("min_academic_standard").asText(""));
            details.append("\n제출서류: ").append(submissionDocuments(t.path("submission_documents")));
            AdmissionsType type = AdmissionsType.builder()
                    .university(univ)
                    .academicYear(year)
                    .name(t.path("type_name").asText())
                    .category(t.path("type_category").asText(null))
                    .source(matByKey.get(t.path("source_document_key").asText(null)))
                    .details(details.toString())
                    .build();
            typeRepository.save(type);
            typeCount++;
        }

        int schedCount = 0;
        for (JsonNode s : root.path("ipsi_schedules")) {
            AdmissionsUniversity univ = requireUniversity(univByCode, s.path("university_code").asText(), "ipsi_schedules");
            Integer year = readSupportedYear(s.path("academic_year"));
            if (year == null) { skipped++; continue; }
            String docKey = s.path("source_document_key").asText(null);
            String schedTitle = s.path("item").asText() + " (" + s.path("type_name").asText("") + ")";
            if (existingSched.contains(univ.getUniversityId() + "|" + year + "|" + schedTitle + "|" + s.path("date").asText(null))) continue;
            AdmissionsSchedule sched = AdmissionsSchedule.builder()
                    .university(univ)
                    .academicYear(year)
                    .title(s.path("item").asText() + " (" + s.path("type_name").asText("") + ")")
                    .note(s.path("date").asText(null))
                    .source(matByKey.get(docKey))
                    .build();
            scheduleRepository.save(sched);
            schedCount++;
        }

        log.info("admissions seed loaded: univ={} mat={} dept={} type={} sched={} skippedUnknownYear={}",
                univCount, matCount, deptCount, typeCount, schedCount, skipped);
        return new LoadResult(univCount, matCount, deptCount, typeCount, schedCount, 0, skipped);
    }

    /**
     * 상담 지식베이스를 적재한다. 전부 DRAFT 상태로 시작.
     */
    @Transactional
    public int loadKnowledgeBase(String knowledgeBaseJson) throws Exception {
        JsonNode root = objectMapper.readTree(knowledgeBaseJson);
        int count = 0;

        // question_types에서 지식 항목 생성 (객체 {key: {...}} 또는 배열 [{id: "Q1", ...}] 모두 지원)
        java.util.List<Map.Entry<String, JsonNode>> entries = questionTypeEntries(root.path("question_types"));
        if (entries.isEmpty()) {
            throw new SeedLoadException("question_types에서 적재할 항목을 찾지 못했습니다(형식 오류 또는 빈 목록).");
        }
        for (Map.Entry<String, JsonNode> entry : entries) {
            String qKey = entry.getKey();
            JsonNode qNode = entry.getValue();
            String dedupKey = "kb_" + qKey;
            if (knowledgeRepository.findByDedupKey(dedupKey).isPresent()) continue;

            StringBuilder content = new StringBuilder();
            content.append("질문 유형: ").append(qKey).append("\n");
            for (String field : new String[]{"question", "description"}) {
                if (qNode.hasNonNull(field)) content.append(qNode.path(field).asText()).append("\n");
            }
            for (String field : new String[]{"answer_basis", "answer_guide"}) {
                if (qNode.hasNonNull(field)) content.append("답변 근거: ").append(qNode.path(field).asText()).append("\n");
            }

            AdmissionsKnowledgeItem item = AdmissionsKnowledgeItem.builder()
                    .academicYear(2027)
                    .yearConfirmed(true)
                    .title("상담 지식: " + qKey)
                    .content(content.toString().trim())
                    .category("상담지식")
                    .sourceClass(AdmissionsKnowledgeItem.SourceClass.OFFICIAL)
                    // Q1~Q9는 질문 유형별 답변 안내문이지 입시 사실이 아니다 → GUIDE (근거 개수에서 제외)
                    .role(AdmissionsKnowledgeItem.Role.GUIDE)
                    .status(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT)
                    .dedupKey(dedupKey)
                    .verificationNote("Qoder knowledge_base.json에서 적재. NORMALIZED 상태 — 운영자 승인 필요.")
                    .createdBy("admissions-loader")
                    .build();
            knowledgeRepository.save(item);
            count++;
        }

        log.info("admissions knowledge base loaded: {} items (DRAFT)", count);
        return count;
    }

    /** question_types를 (키, 노드) 목록으로 평탄화한다. 객체/배열 외 형태나 id 없는 배열 원소는 건너뛴다. */
    static java.util.List<Map.Entry<String, JsonNode>> questionTypeEntries(JsonNode questionTypes) {
        java.util.List<Map.Entry<String, JsonNode>> out = new java.util.ArrayList<>();
        if (questionTypes == null) return out;
        if (questionTypes.isObject()) {
            questionTypes.fields().forEachRemaining(e -> out.add(Map.entry(e.getKey(), e.getValue())));
        } else if (questionTypes.isArray()) {
            for (JsonNode n : questionTypes) {
                String id = n.path("id").asText("");
                if (!id.isBlank()) out.add(Map.entry(id, n));
            }
        }
        return out;
    }

    /**
     * 제출서류 원문을 한 줄로 합친다(항목 구분 " | "). 값이 없으면 빈 문자열이라 검색 단계에서 "확인되지 않음"으로 표기된다.
     * 문자열 하나 또는 문자열 배열을 받는다. 원문을 고치거나 보충하지 않는다.
     */
    static String submissionDocuments(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        if (node.isTextual()) return node.asText().strip();
        if (!node.isArray()) return "";
        java.util.List<String> items = new java.util.ArrayList<>();
        for (JsonNode n : node) {
            String v = n.asText("").strip();
            if (!v.isEmpty()) items.add(v);
        }
        return String.join(" | ", items);
    }

    private static String extractUnivCode(String docKey) {
        // doc_snu_2027_susi → SNU
        if (docKey == null) return null;
        String[] parts = docKey.split("_");
        if (parts.length < 2) return null;
        return switch (parts[1].toLowerCase()) {
            case "snu" -> "SNU";
            case "yon", "yonsei" -> "YON";
            case "korea" -> "KOREA";
            case "skku" -> "SKKU";
            case "hanyang" -> "HANYANG";
            case "khu" -> "KHU";
            case "inha" -> "INHA";
            default -> parts[1].toUpperCase();
        };
    }

    private static AdmissionsUniversity requireUniversity(Map<String, AdmissionsUniversity> byCode, String code, String section) {
        AdmissionsUniversity univ = byCode.get(code);
        if (univ == null) {
            throw new SeedLoadException(section + ": 알 수 없는 university_code=" + code);
        }
        return univ;
    }

    /** 상담이 다루는 학년도. 2029 등 그 밖의 연도는 섞이지 않도록 적재하지 않는다. */
    static final java.util.Set<Integer> SUPPORTED_YEARS = java.util.Set.of(2027, 2028);

    /**
     * 학년도를 읽는다. 값이 없거나 숫자가 아니거나 2027/2028이 아니면 null — 임의로 2027로 확정하지 않는다.
     */
    static Integer readSupportedYear(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        Integer year;
        if (node.isInt()) {
            year = node.asInt();
        } else {
            try {
                year = Integer.parseInt(node.asText("").trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return SUPPORTED_YEARS.contains(year) ? year : null;
    }

    private static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr.substring(0, 10), DateTimeFormatter.ISO_DATE);
        } catch (Exception e) {
            return null;
        }
    }
}
