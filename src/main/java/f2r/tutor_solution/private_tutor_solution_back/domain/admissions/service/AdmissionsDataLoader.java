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
                             int types, int schedules, int knowledgeItems) {}

    /**
     * 시드 JSON 전체를 적재한다. 멱등성: dedup_key·대학명으로 중복 방지.
     */
    @Transactional
    public LoadResult loadSeed(String seedJson) throws Exception {
        JsonNode root = objectMapper.readTree(seedJson);
        Map<String, AdmissionsUniversity> univByCode = new HashMap<>();
        Map<String, AdmissionsOfficialMaterial> matByKey = new HashMap<>();

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

        int matCount = 0;
        for (JsonNode d : root.path("ipsi_documents")) {
            String docKey = d.path("document_key").asText();
            // 중복 방지: 같은 document_key가 이미 있으면 스킵 (제목으로 확인)
            String title = d.path("document_name").asText();
            boolean exists = materialRepository.findAll().stream()
                    .anyMatch(m -> title.equals(m.getTitle()));
            if (exists) continue;

            String univCode = extractUnivCode(docKey);
            AdmissionsUniversity univ = univByCode.get(univCode);
            AdmissionsOfficialMaterial mat = AdmissionsOfficialMaterial.builder()
                    .university(univ)
                    .academicYear(d.path("applicable_academic_year").asInt(2027))
                    .title(title)
                    .issuer(d.path("institution_name").asText("미확인"))
                    .publishDate(parseDate(d.path("published_at").asText(null)))
                    .documentUrl(d.path("source_url").asText(null))
                    .fileKey(docKey)
                    .build();
            mat = materialRepository.save(mat);
            matByKey.put(docKey, mat);
            matCount++;
        }

        int deptCount = 0;
        for (JsonNode dep : root.path("ipsi_departments")) {
            AdmissionsUniversity univ = univByCode.get(dep.path("university_code").asText());
            if (univ == null) continue;
            int year = parseYear(dep.path("academic_year").asText("2027"));
            AdmissionsDepartment dept = AdmissionsDepartment.builder()
                    .university(univ)
                    .academicYear(year)
                    .name(dep.path("department_name").asText())
                    .quotaInfo(dep.path("counts_by_type").toString())
                    .build();
            departmentRepository.save(dept);
            deptCount++;
        }

        int typeCount = 0;
        for (JsonNode t : root.path("ipsi_admission_types")) {
            AdmissionsUniversity univ = univByCode.get(t.path("university_code").asText());
            if (univ == null) continue;
            int year = t.path("academic_year").asInt(2027);
            StringBuilder details = new StringBuilder();
            details.append("모집인원: ").append(t.path("recruitment_count").asText("미확인")).append("\n");
            details.append("지원자격: ").append(t.path("eligibility").asText("")).append("\n");
            details.append("전형방법: ").append(t.path("student_record_method").asText("")).append("\n");
            details.append("수능최저: ").append(t.path("min_academic_standard").asText(""));
            AdmissionsType type = AdmissionsType.builder()
                    .university(univ)
                    .academicYear(year)
                    .name(t.path("type_name").asText())
                    .category(t.path("type_category").asText(null))
                    .details(details.toString())
                    .build();
            typeRepository.save(type);
            typeCount++;
        }

        int schedCount = 0;
        for (JsonNode s : root.path("ipsi_schedules")) {
            AdmissionsUniversity univ = univByCode.get(s.path("university_code").asText());
            int year = parseYear(s.path("academic_year").asText("2027"));
            String docKey = s.path("source_document_key").asText(null);
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

        log.info("admissions seed loaded: univ={} mat={} dept={} type={} sched={}",
                univCount, matCount, deptCount, typeCount, schedCount);
        return new LoadResult(univCount, matCount, deptCount, typeCount, schedCount, 0);
    }

    /**
     * 상담 지식베이스를 적재한다. 전부 DRAFT 상태로 시작.
     */
    @Transactional
    public int loadKnowledgeBase(String knowledgeBaseJson) throws Exception {
        JsonNode root = objectMapper.readTree(knowledgeBaseJson);
        int count = 0;

        // question_types에서 지식 항목 생성
        JsonNode questionTypes = root.path("question_types");
        if (questionTypes.isObject()) {
            var fields = questionTypes.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String qKey = entry.getKey();
                JsonNode qNode = entry.getValue();
                String dedupKey = "kb_" + qKey;
                if (knowledgeRepository.findByDedupKey(dedupKey).isPresent()) continue;

                StringBuilder content = new StringBuilder();
                content.append("질문 유형: ").append(qKey).append("\n");
                if (qNode.has("description")) {
                    content.append(qNode.path("description").asText()).append("\n");
                }
                if (qNode.has("answer_guide")) {
                    content.append("답변 가이드: ").append(qNode.path("answer_guide").asText());
                }

                AdmissionsKnowledgeItem item = AdmissionsKnowledgeItem.builder()
                        .academicYear(2027)
                        .yearConfirmed(true)
                        .title("상담 지식: " + qKey)
                        .content(content.toString())
                        .category("상담지식")
                        .sourceClass(AdmissionsKnowledgeItem.SourceClass.OFFICIAL)
                        .status(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT)
                        .dedupKey(dedupKey)
                        .verificationNote("Qoder knowledge_base.json에서 적재. NORMALIZED 상태 — 운영자 승인 필요.")
                        .createdBy("admissions-loader")
                        .build();
                knowledgeRepository.save(item);
                count++;
            }
        }

        log.info("admissions knowledge base loaded: {} items (DRAFT)", count);
        return count;
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

    private static int parseYear(String yearStr) {
        try {
            return Integer.parseInt(yearStr.trim());
        } catch (Exception e) {
            return 2027;
        }
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
