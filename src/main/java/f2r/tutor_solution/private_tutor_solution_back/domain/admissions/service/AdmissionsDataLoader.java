package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsDepartment;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsJeongsiFormula;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsSusiMinimum;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsOfficialMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsPriorResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsSchedule;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsType;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsUniversity;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsDepartmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsOfficialMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsPriorResultRepository;
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
    private final AdmissionsPriorResultRepository priorResultRepository;
    private final f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsJeongsiFormulaRepository jeongsiFormulaRepository;
    private final f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsSusiMinimumRepository susiMinimumRepository;
    private final f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsCsatStdMaxRepository csatStdMaxRepository;
    private final f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsConversionScoreRepository conversionScoreRepository;

    /**
     * 적재 결과. departmentsUpdated·typesUpdated는 이미 있던 행(같은 대학·학년도·이름) 중 내용이 바뀌어
     * 갱신하고 DRAFT로 되돌린 건수다. schedulesArchived는 시드의 archive_schedules로 보관 처리한 일정 건수다.
     * supersededArchived는 authoritative_universities 처리로 보관한 예전 행(학과·전형·일정·공식자료) 건수다.
     * schedulesSourced는 출처 없이 들어가 있던 기존 일정에 시드의 출처를 연결한(DRAFT로 되돌린) 건수다.
     */
    public record LoadResult(int universities, int materials, int departments,
                             int types, int schedules, int knowledgeItems, int skipped,
                             int departmentsUpdated, int typesUpdated, int schedulesArchived,
                             int supersededArchived, int schedulesSourced,
                             int priorResults, int priorResultsUpdated,
                             int jeongsiFormulas, int jeongsiFormulasUpdated,
                             int susiMinimums, int susiMinimumsUpdated,
                             int csatStdMax, int csatStdMaxUpdated, int conversionScores, int conversionScoresUpdated) {
        public LoadResult(int universities, int materials, int departments,
                          int types, int schedules, int knowledgeItems, int skipped,
                          int departmentsUpdated, int typesUpdated, int schedulesArchived,
                          int supersededArchived, int schedulesSourced, int priorResults, int priorResultsUpdated,
                          int jeongsiFormulas, int jeongsiFormulasUpdated, int susiMinimums, int susiMinimumsUpdated) {
            this(universities, materials, departments, types, schedules, knowledgeItems, skipped, departmentsUpdated, typesUpdated,
                    schedulesArchived, supersededArchived, schedulesSourced, priorResults, priorResultsUpdated,
                    jeongsiFormulas, jeongsiFormulasUpdated, susiMinimums, susiMinimumsUpdated, 0, 0, 0, 0);
        }

        public LoadResult(int universities, int materials, int departments,
                          int types, int schedules, int knowledgeItems, int skipped,
                          int departmentsUpdated, int typesUpdated, int schedulesArchived,
                          int supersededArchived, int schedulesSourced, int priorResults, int priorResultsUpdated,
                          int jeongsiFormulas, int jeongsiFormulasUpdated) {
            this(universities, materials, departments, types, schedules, knowledgeItems, skipped, departmentsUpdated, typesUpdated,
                    schedulesArchived, supersededArchived, schedulesSourced, priorResults, priorResultsUpdated,
                    jeongsiFormulas, jeongsiFormulasUpdated, 0, 0, 0, 0, 0, 0);
        }

        public LoadResult(int universities, int materials, int departments,
                          int types, int schedules, int knowledgeItems, int skipped) {
            this(universities, materials, departments, types, schedules, knowledgeItems, skipped, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public LoadResult(int universities, int materials, int departments,
                          int types, int schedules, int knowledgeItems, int skipped,
                          int departmentsUpdated, int typesUpdated, int schedulesArchived,
                          int supersededArchived, int schedulesSourced, int priorResults, int priorResultsUpdated) {
            this(universities, materials, departments, types, schedules, knowledgeItems, skipped, departmentsUpdated, typesUpdated,
                    schedulesArchived, supersededArchived, schedulesSourced, priorResults, priorResultsUpdated, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public LoadResult(int universities, int materials, int departments,
                          int types, int schedules, int knowledgeItems, int skipped,
                          int departmentsUpdated, int typesUpdated, int schedulesArchived,
                          int supersededArchived, int schedulesSourced) {
            this(universities, materials, departments, types, schedules, knowledgeItems, skipped,
                    departmentsUpdated, typesUpdated, schedulesArchived, supersededArchived, schedulesSourced, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    /** 시드 구조/참조 오류. 던지면 트랜잭션이 롤백되어 부분 적재가 남지 않는다. */
    public static class SeedLoadException extends IllegalArgumentException {
        public SeedLoadException(String message) { super(message); }
    }

    /**
     * 시드 JSON 전체를 적재한다. 같은 시드를 다시 실행해도 중복되지 않는다
     * (대학명 / 자료 제목 / 대학+학년도+학과·전형명 / 대학+학년도+일정 제목+비고 기준).
     * 이미 있는 학과·전형 행의 내용(학과 인원표, 전형 세부내용·분류)이 시드와 다르면 그 행을 갱신하고
     * DRAFT로 되돌린다(재검수 후 다시 승인해야 상담 검색에 노출된다). 내용이 같으면 그대로 둔다.
     * 시드 최상위 {@code archive_schedules}(대학 코드·학년도·일정 제목·비고)에 적힌 기존 일정은 원문과 달라
     * 폐기된 행이므로 ARCHIVED로 바꾸고, 그 일정에서 만든 지식 항목도 ARCHIVED로 바꾼다. 행을 지우지는 않는다.
     * 시드 최상위 {@code authoritative_universities}(대학 코드 목록)에 적힌 대학은 이 시드를 최신본으로 본다:
     * 시드가 다루는 학년도에서, 같은 대학(이름이 같거나 괄호 부기를 뺀 이름이 같은 기존 대학 포함)의 예전 학과·전형·일정 중
     * 시드에 없는 행과 그 지식 항목, 더 이상 근거로 쓰이지 않는 예전 공식자료를 ARCHIVED로 바꾼다.
     * 다른 캠퍼스(세종 등) 표기가 있는 행은 시드 범위 밖이므로 남긴다.
     * 구조 오류(대학 목록 없음, 알 수 없는 대학 코드)는 {@link SeedLoadException}으로 전체 롤백한다.
     * 학년도 미확정·미지원 행만 건너뛰며 그 건수를 {@code skipped}로 돌려준다.
     */
    @Transactional
    public LoadResult loadSeed(String seedJson) throws Exception {
        JsonNode root = objectMapper.readTree(seedJson);
        // 평가원 표준점수 최고점만 있는 시드는 대학이 없다(근거 자료도 대학 없는 평가원 자료).
        boolean stdMaxOnly = root.path("csat_std_max").isArray() && !root.path("csat_std_max").isEmpty();
        if (!stdMaxOnly && (!root.path("ipsi_universities").isArray() || root.path("ipsi_universities").isEmpty())) {
            throw new SeedLoadException("ipsi_universities가 없거나 비어 있습니다.");
        }
        Map<String, AdmissionsUniversity> univByCode = new HashMap<>();
        Map<String, AdmissionsOfficialMaterial> matByKey = new HashMap<>();
        // 재실행 중복 방지: 적재 전에 이미 있는 행의 자연키 스냅샷(같은 파일 안의 원본 중복행은 보존한다).
        Map<String, AdmissionsDepartment> existingDept = new HashMap<>();
        departmentRepository.findAll().forEach(d -> existingDept.putIfAbsent(
                d.getUniversity().getUniversityId() + "|" + d.getAcademicYear() + "|" + d.getName(), d));
        Map<String, AdmissionsType> existingType = new HashMap<>();
        typeRepository.findAll().forEach(t -> existingType.putIfAbsent(
                t.getUniversity().getUniversityId() + "|" + t.getAcademicYear() + "|" + t.getName(), t));
        Map<String, AdmissionsSchedule> existingSchedRows = new HashMap<>();
        scheduleRepository.findAll().forEach(sc -> existingSchedRows.putIfAbsent(
                sc.getUniversity().getUniversityId() + "|" + sc.getAcademicYear() + "|" + sc.getTitle() + "|" + sc.getNote(), sc));
        // 같은 자료 판단은 "대학 + 제목"으로 한다. 제목만 보면 "2027학년도 수시모집 모집요강"처럼 흔한 제목의
        // 다른 대학 자료에 행이 잘못 연결된다.
        Map<String, AdmissionsOfficialMaterial> materialByTitle = new HashMap<>();
        materialRepository.findAll().forEach(m -> materialByTitle.put(materialKey(m.getUniversity(), m.getTitle()), m));

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
            String univCode = extractUnivCode(docKey);
            AdmissionsUniversity univ = univByCode.get(univCode);
            // 연세대는 시드에 따라 코드가 YON(검증용 시드) 또는 YONSEI(buildit-admissions 시드)다. 둘 다 받는다.
            if (univ == null && "YON".equals(univCode)) univ = univByCode.get("YONSEI");
            AdmissionsOfficialMaterial already = materialByTitle.get(materialKey(univ, title));
            if (already != null) {
                // 예전에 보관(ARCHIVED)된 같은 자료를 시드가 다시 쓰면 되살려 재승인을 받게 한다.
                // 보관된 자료에 연결된 행은 검색에 쓰이지 않기 때문이다.
                if (already.getStatus() == AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED) {
                    already.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
                    materialRepository.save(already);
                    matCount++;
                }
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
            materialByTitle.put(materialKey(univ, title), mat);
            matCount++;
        }

        int deptCount = 0;
        int deptUpdated = 0;
        for (JsonNode dep : root.path("ipsi_departments")) {
            AdmissionsUniversity univ = requireUniversity(univByCode, dep.path("university_code").asText(), "ipsi_departments");
            Integer year = readSupportedYear(dep.path("academic_year"));
            if (year == null) { skipped++; continue; }
            AdmissionsDepartment old = existingDept.get(univ.getUniversityId() + "|" + year + "|" + dep.path("department_name").asText());
            if (old != null) {
                String quota = dep.path("counts_by_type").toString();
                AdmissionsOfficialMaterial src = matByKey.get(dep.path("source_document_key").asText(null));
                if (!quota.equals(old.getQuotaInfo()) || sourceChanged(old.getSource(), src)) {
                    old.setQuotaInfo(quota);
                    if (src != null) old.setSource(src);
                    old.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
                    departmentRepository.save(old);
                    deptUpdated++;
                }
                continue;
            }
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
        int typeUpdated = 0;
        for (JsonNode t : root.path("ipsi_admission_types")) {
            AdmissionsUniversity univ = requireUniversity(univByCode, t.path("university_code").asText(), "ipsi_admission_types");
            Integer year = readSupportedYear(t.path("academic_year"));
            if (year == null) { skipped++; continue; }
            AdmissionsType oldType = existingType.get(univ.getUniversityId() + "|" + year + "|" + t.path("type_name").asText());
            StringBuilder details = new StringBuilder();
            details.append("모집인원: ").append(t.path("recruitment_count").asText("미확인")).append("\n");
            details.append("지원자격: ").append(t.path("eligibility").asText("")).append("\n");
            details.append("전형방법: ").append(t.path("student_record_method").asText("")).append("\n");
            details.append("수능최저: ").append(t.path("min_academic_standard").asText(""));
            details.append("\n제출서류: ").append(submissionDocuments(t.path("submission_documents")));
            if (oldType != null) {
                String category = t.path("type_category").asText(null);
                AdmissionsOfficialMaterial src = matByKey.get(t.path("source_document_key").asText(null));
                boolean changed = !details.toString().equals(oldType.getDetails())
                        || (category != null && !category.equals(oldType.getCategory()))
                        || sourceChanged(oldType.getSource(), src);
                if (changed) {
                    oldType.setDetails(details.toString());
                    if (category != null) oldType.setCategory(category);
                    if (src != null) oldType.setSource(src);
                    oldType.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
                    typeRepository.save(oldType);
                    typeUpdated++;
                }
                continue;
            }
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
        int schedSourced = 0;
        for (JsonNode s : root.path("ipsi_schedules")) {
            AdmissionsUniversity univ = requireUniversity(univByCode, s.path("university_code").asText(), "ipsi_schedules");
            Integer year = readSupportedYear(s.path("academic_year"));
            if (year == null) { skipped++; continue; }
            String docKey = s.path("source_document_key").asText(null);
            String schedTitle = s.path("item").asText() + " (" + s.path("type_name").asText("") + ")";
            AdmissionsSchedule existing = existingSchedRows.get(univ.getUniversityId() + "|" + year + "|" + schedTitle + "|" + s.path("date").asText(null));
            if (existing != null) {
                // 예전에 출처 없이 들어간 일정(검색에 쓰이지 않음)은 시드의 출처를 연결하고 재승인을 받게 DRAFT로 둔다.
                AdmissionsOfficialMaterial src = matByKey.get(docKey);
                // 다른 자료(예: 같은 제목의 다른 대학 자료)에 잘못 연결된 일정도 시드의 출처로 다시 연결한다.
                if (sourceChanged(existing.getSource(), src) && existing.getStatus() != AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED) {
                    existing.setSource(src);
                    existing.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
                    scheduleRepository.save(existing);
                    schedSourced++;
                }
                continue;
            }
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

        int schedArchived = 0;
        for (JsonNode a : root.path("archive_schedules")) {
            AdmissionsUniversity univ = requireUniversity(univByCode, a.path("university_code").asText(), "archive_schedules");
            Integer year = readSupportedYear(a.path("academic_year"));
            if (year == null) { skipped++; continue; }
            AdmissionsSchedule old = existingSchedRows.get(univ.getUniversityId() + "|" + year + "|"
                    + a.path("title").asText() + "|" + a.path("note").asText(null));
            if (old == null || old.getStatus() == AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED) continue;
            old.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED);
            scheduleRepository.save(old);
            knowledgeRepository.findByDedupKey("schedule:" + univ.getUniversityId() + ":" + year + ":" + old.getScheduleId())
                    .ifPresent(k -> { k.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED); knowledgeRepository.save(k); });
            schedArchived++;
        }

        int youtube = loadYoutubeKnowledge(root.path("youtube_knowledge"));
        int[] prior = loadPriorResults(root.path("prior_results"), univByCode, matByKey);
        int[] jeongsi = loadJeongsiFormulas(root.path("jeongsi_formulas"), univByCode, matByKey);
        int[] susiMin = loadSusiMinimums(root.path("susi_minimums"), univByCode, matByKey);
        int[] stdMax = loadCsatStdMax(root.path("csat_std_max"), matByKey);
        int[] conv = loadConversionScores(root.path("conversion_scores"), univByCode, matByKey);

        int superseded = 0;
        for (JsonNode code : root.path("authoritative_universities")) {
            superseded += archiveSuperseded(root, code.asText(), univByCode);
        }

        log.info("admissions seed loaded: univ={} mat={} dept={} type={} sched={} skippedUnknownYear={} deptUpdated={} typeUpdated={} schedArchived={} superseded={} schedSourced={} prior={} priorUpdated={} jeongsi={} jeongsiUpdated={}",
                univCount, matCount, deptCount, typeCount, schedCount, skipped, deptUpdated, typeUpdated, schedArchived, superseded, schedSourced,
                prior[0], prior[1], jeongsi[0], jeongsi[1]);
        log.info("admissions seed susi minimums: created={} updated={}", susiMin[0], susiMin[1]);
        log.info("admissions seed jeongsi stats: stdMax created={} updated={} conversion created={} updated={}", stdMax[0], stdMax[1], conv[0], conv[1]);
        return new LoadResult(univCount, matCount, deptCount, typeCount, schedCount, youtube, skipped, deptUpdated, typeUpdated,
                schedArchived, superseded, schedSourced, prior[0], prior[1], jeongsi[0], jeongsi[1], susiMin[0], susiMin[1],
                stdMax[0], stdMax[1], conv[0], conv[1]);
    }

    /**
     * 시드의 jeongsi_formulas(정시 수능 환산 계산식)를 DRAFT로 넣는다. 대학·학년도·formula_key가 같으면 새로 만들지 않고,
     * 내용(계산식·등급표·적용 범위·원문·확인 필요 사유·출처)이 바뀐 것만 갱신해 DRAFT로 되돌린다(재승인 필요).
     * 계산식이 이 서버 계산기로 해석되지 않으면 {@link SeedLoadException}으로 전체 롤백한다.
     * @return [새로 넣은 수, 갱신한 수]
     */
    int[] loadJeongsiFormulas(JsonNode rows, Map<String, AdmissionsUniversity> univByCode,
                              Map<String, AdmissionsOfficialMaterial> matByKey) {
        if (rows == null || !rows.isArray() || rows.isEmpty()) return new int[] {0, 0};
        java.util.Set<Long> seedUnivIds = new java.util.HashSet<>();
        for (JsonNode r : rows) {
            AdmissionsUniversity u = univByCode.get(r.path("university_code").asText());
            if (u != null) seedUnivIds.add(u.getUniversityId());
        }
        Map<String, AdmissionsJeongsiFormula> existing = new HashMap<>();
        jeongsiFormulaRepository.findByUniversityUniversityIdIn(seedUnivIds).forEach(f ->
                existing.putIfAbsent(f.getUniversity().getUniversityId() + "|" + f.getAcademicYear() + "|" + f.getFormulaKey(), f));
        int created = 0;
        int updated = 0;
        for (JsonNode r : rows) {
            AdmissionsUniversity univ = requireUniversity(univByCode, r.path("university_code").asText(), "jeongsi_formulas");
            Integer year = readSupportedYear(r.path("academic_year"));
            String key = textOrNull(r.path("formula_key"));
            String expression = textOrNull(r.path("expression"));
            if (year == null || key == null || expression == null || !r.path("type_names").isArray() || r.path("type_names").isEmpty()) continue;
            try {
                JeongsiFormulaEvaluator.variables(expression);
            } catch (JeongsiFormulaEvaluator.FormulaException e) {
                throw new SeedLoadException("jeongsi_formulas " + key + ": 계산식을 해석할 수 없습니다 — " + e.getMessage());
            }
            AdmissionsOfficialMaterial src = matByKey.get(r.path("source_document_key").asText(null));
            String hash = sha256(r.toString());
            String mapKey = univ.getUniversityId() + "|" + year + "|" + key;
            AdmissionsJeongsiFormula row = existing.get(mapKey);
            if (row != null && hash.equals(row.getContentHash())
                    && (src == null || (row.getSource() != null && src.getMaterialId().equals(row.getSource().getMaterialId())))) {
                continue;
            }
            boolean isNew = row == null;
            if (isNew) {
                row = AdmissionsJeongsiFormula.builder().university(univ).academicYear(year).formulaKey(key).build();
            }
            row.setLabel(truncate(r.path("label").asText(key), 300));
            row.setTypeNames(r.path("type_names").toString());
            row.setDepartmentNames(r.path("department_names").isArray() ? r.path("department_names").toString() : "[]");
            row.setUnitScopeText(textOrNull(r.path("unit_scope_text")));
            row.setCsatPoints(decimalOrNull(r.path("csat_points")));
            row.setPerfectScoreExpected(decimalOrNull(r.path("perfect_score_expected")));
            row.setExpression(expression);
            row.setTablesJson(r.path("tables").isObject() ? r.path("tables").toString() : "{}");
            row.setRoundingText(truncate(textOrNull(r.path("rounding_text")), 500));
            row.setEligibilityText(textOrNull(r.path("eligibility_text")));
            row.setNotInCsatScoreText(textOrNull(r.path("not_in_csat_score_text")));
            row.setEvidencePages(truncate(r.path("evidence_pages").isArray() ? r.path("evidence_pages").toString() : null, 200));
            row.setRawText(textOrNull(r.path("raw_text")));
            row.setUncertainJson(r.path("uncertain").isArray() ? r.path("uncertain").toString() : "[]");
            row.setNeedsReviewReason(textOrNull(r.path("needs_review_reason")));
            row.setSourceUrl(truncate(textOrNull(r.path("source_url")), 1000));
            if (src != null) row.setSource(src);
            row.setContentHash(hash);
            row.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
            jeongsiFormulaRepository.save(row);
            if (isNew) {
                existing.put(mapKey, row);
                created++;
            } else {
                updated++;
            }
        }
        return new int[] {created, updated};
    }

    /**
     * 시드의 susi_minimums(수시 수능최저학력기준)를 DRAFT로 넣는다. 대학·학년도·rule_key가 같으면 새로 만들지 않고,
     * 내용·출처가 바뀐 것만 갱신해 DRAFT로 되돌린다. RULE의 조건식이 해석되지 않으면 {@link SeedLoadException}으로 전체 롤백한다.
     * @return [새로 넣은 수, 갱신한 수]
     */
    int[] loadSusiMinimums(JsonNode rows, Map<String, AdmissionsUniversity> univByCode,
                           Map<String, AdmissionsOfficialMaterial> matByKey) {
        if (rows == null || !rows.isArray() || rows.isEmpty()) return new int[] {0, 0};
        java.util.Set<Long> seedUnivIds = new java.util.HashSet<>();
        for (JsonNode r : rows) {
            AdmissionsUniversity u = univByCode.get(r.path("university_code").asText());
            if (u != null) seedUnivIds.add(u.getUniversityId());
        }
        Map<String, AdmissionsSusiMinimum> existing = new HashMap<>();
        susiMinimumRepository.findByUniversityUniversityIdIn(seedUnivIds).forEach(m ->
                existing.putIfAbsent(m.getUniversity().getUniversityId() + "|" + m.getAcademicYear() + "|" + m.getRuleKey(), m));
        int created = 0;
        int updated = 0;
        for (JsonNode r : rows) {
            AdmissionsUniversity univ = requireUniversity(univByCode, r.path("university_code").asText(), "susi_minimums");
            Integer year = readSupportedYear(r.path("academic_year"));
            String key = textOrNull(r.path("rule_key"));
            String kindText = r.path("rule_kind").asText("");
            if (year == null || key == null || !r.path("type_names").isArray() || r.path("type_names").isEmpty()) continue;
            AdmissionsSusiMinimum.RuleKind kind;
            try {
                kind = AdmissionsSusiMinimum.RuleKind.valueOf(kindText);
            } catch (IllegalArgumentException e) {
                throw new SeedLoadException("susi_minimums " + key + ": rule_kind는 RULE 또는 NO_MINIMUM — " + kindText);
            }
            String condition = textOrNull(r.path("condition"));
            if (kind == AdmissionsSusiMinimum.RuleKind.RULE) {
                if (condition == null) throw new SeedLoadException("susi_minimums " + key + ": RULE에 조건식이 없습니다.");
                try {
                    JeongsiFormulaEvaluator.variables(condition);
                } catch (JeongsiFormulaEvaluator.FormulaException e) {
                    throw new SeedLoadException("susi_minimums " + key + ": 조건식을 해석할 수 없습니다 — " + e.getMessage());
                }
            } else {
                condition = null;
            }
            AdmissionsOfficialMaterial src = matByKey.get(r.path("source_document_key").asText(null));
            String hash = sha256(r.toString());
            String mapKey = univ.getUniversityId() + "|" + year + "|" + key;
            AdmissionsSusiMinimum row = existing.get(mapKey);
            if (row != null && hash.equals(row.getContentHash())
                    && (src == null || (row.getSource() != null && src.getMaterialId().equals(row.getSource().getMaterialId())))) {
                continue;
            }
            boolean isNew = row == null;
            if (isNew) {
                row = AdmissionsSusiMinimum.builder().university(univ).academicYear(year).ruleKey(key).build();
            }
            row.setRuleKind(kind);
            row.setLabel(truncate(r.path("label").asText(key), 300));
            row.setTypeNames(r.path("type_names").toString());
            row.setDepartmentNames(r.path("department_names").isArray() ? r.path("department_names").toString() : "[]");
            row.setUnitScopeText(textOrNull(r.path("unit_scope_text")));
            row.setConditionExpr(condition);
            row.setRequiredAreasText(textOrNull(r.path("required_areas_text")));
            row.setEvidencePages(truncate(r.path("evidence_pages").isArray() ? r.path("evidence_pages").toString() : null, 200));
            row.setRawText(textOrNull(r.path("raw_text")));
            row.setUncertainJson(r.path("uncertain").isArray() ? r.path("uncertain").toString() : "[]");
            row.setNeedsReviewReason(textOrNull(r.path("needs_review_reason")));
            row.setSourceUrl(truncate(textOrNull(r.path("source_url")), 1000));
            if (src != null) row.setSource(src);
            row.setContentHash(hash);
            row.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
            susiMinimumRepository.save(row);
            if (isNew) {
                existing.put(mapKey, row);
                created++;
            } else {
                updated++;
            }
        }
        return new int[] {created, updated};
    }

    static final java.util.Set<String> INQUIRY_TRACKS = java.util.Set.of("SOC", "SCI", "VOC");

    /**
     * 시드의 csat_std_max(평가원 수능 채점 결과의 영역·탐구 과목별 표준점수 최고점)를 DRAFT로 넣는다. 학년도·subject_key가 같으면
     * 새로 만들지 않고 값·출처가 바뀐 것만 갱신해 DRAFT로 되돌린다. 값이 없거나 0~200 밖이면 전체 롤백한다.
     * @return [새로 넣은 수, 갱신한 수]
     */
    int[] loadCsatStdMax(JsonNode rows, Map<String, AdmissionsOfficialMaterial> matByKey) {
        if (rows == null || !rows.isArray() || rows.isEmpty()) return new int[] {0, 0};
        Map<String, f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsCsatStdMax> existing = new HashMap<>();
        csatStdMaxRepository.findAll().forEach(m -> existing.putIfAbsent(m.getAcademicYear() + "|" + m.getSubjectKey(), m));
        int created = 0;
        int updated = 0;
        for (JsonNode r : rows) {
            Integer year = readSupportedYear(r.path("academic_year"));
            String key = textOrNull(r.path("subject_key"));
            String area = r.path("area").asText("");
            if (year == null || key == null) continue;
            if (!java.util.Set.of("KOR", "MATH", "INQ").contains(area)) throw new SeedLoadException("csat_std_max " + key + ": area는 KOR/MATH/INQ — " + area);
            String track = textOrNull(r.path("track"));
            if ("INQ".equals(area) && (textOrNull(r.path("subject_name")) == null || !INQUIRY_TRACKS.contains(track))) {
                throw new SeedLoadException("csat_std_max " + key + ": 탐구는 subject_name과 track(SOC/SCI/VOC)이 필요합니다.");
            }
            if (!r.path("std_max").isNumber() || r.path("std_max").asDouble() <= 0 || r.path("std_max").asDouble() > 200) {
                throw new SeedLoadException("csat_std_max " + key + ": std_max가 없거나 0~200 밖입니다.");
            }
            AdmissionsOfficialMaterial src = matByKey.get(r.path("source_document_key").asText(null));
            String hash = sha256(r.toString());
            String mapKey = year + "|" + key;
            var row = existing.get(mapKey);
            if (row != null && hash.equals(row.getContentHash())
                    && (src == null || (row.getSource() != null && src.getMaterialId().equals(row.getSource().getMaterialId())))) {
                continue;
            }
            boolean isNew = row == null;
            if (isNew) {
                row = f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsCsatStdMax.builder()
                        .academicYear(year).subjectKey(key).build();
            }
            row.setArea(area);
            row.setSubjectName(textOrNull(r.path("subject_name")));
            row.setTrack(track);
            row.setStdMax(r.path("std_max").decimalValue());
            row.setEvidencePages(truncate(r.path("evidence_pages").isArray() ? r.path("evidence_pages").toString() : null, 200));
            row.setSourceUrl(truncate(textOrNull(r.path("source_url")), 1000));
            if (src != null) row.setSource(src);
            row.setContentHash(hash);
            row.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
            csatStdMaxRepository.save(row);
            if (isNew) { existing.put(mapKey, row); created++; } else { updated++; }
        }
        return new int[] {created, updated};
    }

    /**
     * 시드의 conversion_scores(대학 탐구 변환표준점수표, 백분위 0~100 → 점수)를 DRAFT로 넣는다. 대학·학년도·track이 같으면
     * 새로 만들지 않고 표·출처가 바뀐 것만 갱신해 DRAFT로 되돌린다. 백분위가 빠졌거나, 낮은 백분위 점수가 더 높으면 전체 롤백한다.
     * @return [새로 넣은 수, 갱신한 수]
     */
    int[] loadConversionScores(JsonNode rows, Map<String, AdmissionsUniversity> univByCode,
                               Map<String, AdmissionsOfficialMaterial> matByKey) {
        if (rows == null || !rows.isArray() || rows.isEmpty()) return new int[] {0, 0};
        Map<String, f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsConversionScore> existing = new HashMap<>();
        conversionScoreRepository.findAll().forEach(m ->
                existing.putIfAbsent(m.getUniversity().getUniversityId() + "|" + m.getAcademicYear() + "|" + m.getTrack(), m));
        int created = 0;
        int updated = 0;
        for (JsonNode r : rows) {
            AdmissionsUniversity univ = requireUniversity(univByCode, r.path("university_code").asText(), "conversion_scores");
            Integer year = readSupportedYear(r.path("academic_year"));
            String track = r.path("track").asText("");
            if (year == null) continue;
            if (!INQUIRY_TRACKS.contains(track)) throw new SeedLoadException("conversion_scores " + univ.getName() + ": track은 SOC/SCI/VOC — " + track);
            JsonNode scores = r.path("scores");
            Double prev = null;
            for (int pct = 100; pct >= 0; pct--) {
                JsonNode v = scores.path(String.valueOf(pct));
                if (!v.isNumber() || v.asDouble() < 0 || v.asDouble() > 200) {
                    throw new SeedLoadException("conversion_scores " + univ.getName() + " " + track + ": 백분위 " + pct + " 점수가 없거나 0~200 밖입니다.");
                }
                if (prev != null && v.asDouble() > prev) {
                    throw new SeedLoadException("conversion_scores " + univ.getName() + " " + track + ": 백분위 " + pct + " 점수가 위 백분위보다 큽니다.");
                }
                prev = v.asDouble();
            }
            AdmissionsOfficialMaterial src = matByKey.get(r.path("source_document_key").asText(null));
            String hash = sha256(r.toString());
            String mapKey = univ.getUniversityId() + "|" + year + "|" + track;
            var row = existing.get(mapKey);
            if (row != null && hash.equals(row.getContentHash())
                    && (src == null || (row.getSource() != null && src.getMaterialId().equals(row.getSource().getMaterialId())))) {
                continue;
            }
            boolean isNew = row == null;
            if (isNew) {
                row = f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsConversionScore.builder()
                        .university(univ).academicYear(year).track(track).build();
            }
            row.setLabel(truncate(r.path("label").asText(track), 100));
            row.setScoresJson(scores.toString());
            row.setNote(textOrNull(r.path("note")));
            row.setEvidencePages(truncate(r.path("evidence_pages").isArray() ? r.path("evidence_pages").toString() : null, 200));
            row.setSourceUrl(truncate(textOrNull(r.path("source_url")), 1000));
            if (src != null) row.setSource(src);
            row.setContentHash(hash);
            row.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
            conversionScoreRepository.save(row);
            if (isNew) { existing.put(mapKey, row); created++; } else { updated++; }
        }
        return new int[] {created, updated};
    }

    private static String sha256(String raw) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 시드의 prior_results(지난해 입시결과)를 DRAFT로 넣는다. 자연키(대학·결과학년도·트랙·군·전형·모집단위·지표명)가 같으면
     * 새로 만들지 않고, 값이나 출처가 바뀐 행만 갱신해 DRAFT로 되돌린다(재승인 필요). 같은 시드를 다시 적재해도 중복이 없다.
     * 상담 학년도(academic_year)가 2027·2028이 아니거나 결과 학년도·전형·모집단위가 비어 있는 행은 건너뛴다.
     * @return [새로 넣은 수, 갱신한 수]
     */
    int[] loadPriorResults(JsonNode rows, Map<String, AdmissionsUniversity> univByCode,
                           Map<String, AdmissionsOfficialMaterial> matByKey) {
        if (rows == null || !rows.isArray() || rows.isEmpty()) return new int[] {0, 0};
        Map<String, AdmissionsPriorResult> existing = new HashMap<>();
        // 이번 시드에 나온 대학의 기존 행만 읽는다(187개교면 전체가 수십만 행).
        java.util.Set<Long> seedUnivIds = new java.util.HashSet<>();
        for (JsonNode r : rows) {
            AdmissionsUniversity u = univByCode.get(r.path("university_code").asText());
            if (u != null) seedUnivIds.add(u.getUniversityId());
        }
        priorResultRepository.findByUniversityUniversityIdIn(seedUnivIds).forEach(r ->
                existing.putIfAbsent(r.getUniversity().getUniversityId() + "|" + r.getNaturalKey(), r));
        int created = 0;
        int updated = 0;
        for (JsonNode r : rows) {
            AdmissionsUniversity univ = requireUniversity(univByCode, r.path("university_code").asText(), "prior_results");
            Integer year = readSupportedYear(r.path("academic_year"));
            int resultYear = r.path("result_year").asInt(0);
            String round = r.path("round_type").asText("");
            String type = r.path("type_name").asText("").strip();
            String dept = r.path("department_name").asText("").strip();
            if (year == null || resultYear < 2020 || round.isBlank() || type.isEmpty() || dept.isEmpty()) continue;
            String group = textOrNull(r.path("group"));
            String label = r.path("metric_label").isNull() ? "" : r.path("metric_label").asText("").strip();
            String naturalKey = priorNaturalKey(resultYear, round, group, type, dept, label);
            AdmissionsOfficialMaterial src = matByKey.get(r.path("source_document_key").asText(null));
            AdmissionsPriorResult row = existing.get(univ.getUniversityId() + "|" + naturalKey);
            boolean isNew = row == null;
            if (isNew) {
                row = AdmissionsPriorResult.builder()
                        .university(univ).academicYear(year).resultYear(resultYear).roundType(round).groupName(group)
                        .typeName(type).departmentName(dept).metricLabel(label).naturalKey(naturalKey).build();
            }
            String before = isNew ? null : priorSignature(row);
            row.setAcademicYear(year);
            row.setMetricUnit(textOrNull(r.path("metric_unit")));
            row.setMetricValue(decimalOrNull(r.path("metric_value")));
            row.setRawValueText(truncate(textOrNull(r.path("raw_value_text")), 200));
            row.setRecruitCount(intOrNull(r.path("recruit_count")));
            row.setApplicantCount(intOrNull(r.path("applicant_count")));
            row.setCompetitionRate(decimalOrNull(r.path("competition_rate")));
            row.setExtraAdmittedCount(intOrNull(r.path("extra_admitted_count")));
            row.setLinkedTypeName(truncate(textOrNull(r.path("linked_2027_type_name")), 200));
            row.setLinkedDepartmentName(truncate(textOrNull(r.path("linked_2027_department_name")), 200));
            row.setSourceUrl(truncate(textOrNull(r.path("source_url")), 1000));
            row.setInstitutionName(truncate(textOrNull(r.path("institution_name")), 200));
            row.setPublishedAt(parseDate(textOrNull(r.path("published_at"))));
            row.setEvidencePages(truncate(r.path("evidence_pages").isArray() ? r.path("evidence_pages").toString() : null, 100));
            if (src != null) row.setSource(src);
            if (isNew) {
                priorResultRepository.save(row);
                existing.put(univ.getUniversityId() + "|" + naturalKey, row);
                created++;
            } else if (!before.equals(priorSignature(row))) {
                row.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
                priorResultRepository.save(row);
                updated++;
            }
        }
        return new int[] {created, updated};
    }

    static String priorNaturalKey(int resultYear, String round, String group, String type, String dept, String label) {
        String raw = resultYear + "|" + round + "|" + (group == null ? "" : group) + "|" + type + "|" + dept + "|" + label;
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 바뀌었는지 비교할 값 묶음(학년도·값·출처). 상태·시각은 넣지 않는다. */
    private static String priorSignature(AdmissionsPriorResult r) {
        return r.getAcademicYear() + "|" + r.getMetricUnit() + "|" + plain(r.getMetricValue()) + "|" + r.getRawValueText()
                + "|" + r.getRecruitCount() + "|" + r.getApplicantCount() + "|" + plain(r.getCompetitionRate()) + "|" + r.getExtraAdmittedCount()
                + "|" + r.getLinkedTypeName() + "|" + r.getLinkedDepartmentName() + "|" + r.getSourceUrl() + "|" + r.getInstitutionName()
                + "|" + r.getPublishedAt() + "|" + r.getEvidencePages() + "|" + (r.getSource() == null ? null : r.getSource().getMaterialId());
    }

    private static String plain(java.math.BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        String t = n.asText("").strip();
        return t.isEmpty() ? null : t;
    }

    private static Integer intOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        if (n.isIntegralNumber()) return n.asInt();
        try {
            return Integer.valueOf(n.asText("").replace(",", "").strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static java.math.BigDecimal decimalOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        try {
            return n.isNumber() ? n.decimalValue() : new java.math.BigDecimal(n.asText("").replace(",", "").strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * 시드의 youtube_knowledge(유튜브 영상 분석 = 전문가 해석·전략·전망)를 지식 항목으로 넣는다.
     * 공식 사실이 아니므로 sourceClass=YOUTUBE로 두어 공식 근거 검색(OFFICIAL만)에 섞이지 않게 한다.
     * 같은 id(dedupKey youtube:{id})가 있으면 제목·본문이 바뀐 경우에만 갱신하고 DRAFT로 되돌린다.
     * 지원하지 않는 학년도(2027·2028 외) 항목은 넣지 않는다. 넣거나 갱신한 건수를 돌려준다.
     */
    int loadYoutubeKnowledge(JsonNode items) {
        if (items == null || !items.isArray()) return 0;
        int n = 0;
        for (JsonNode it : items) {
            String id = it.path("id").asText("");
            Integer year = readSupportedYear(it.path("academic_year"));
            String title = it.path("title").asText("");
            String content = it.path("content").asText("");
            if (id.isBlank() || year == null || title.isBlank() || content.isBlank()) continue;
            String dedupKey = "youtube:" + id;
            java.util.Optional<AdmissionsKnowledgeItem> existing = knowledgeRepository.findByDedupKey(dedupKey);
            if (existing.isPresent()) {
                AdmissionsKnowledgeItem k = existing.get();
                if (title.equals(k.getTitle()) && content.equals(k.getContent())) continue;
                k.setTitle(title);
                k.setContent(content);
                k.setTags(it.path("tags").asText(null));
                k.setSourceUrl(it.path("source_url").asText(null));
                k.setAcademicYear(year);
                k.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT);
                knowledgeRepository.save(k);
                n++;
                continue;
            }
            knowledgeRepository.save(AdmissionsKnowledgeItem.builder()
                    .academicYear(year)
                    .yearConfirmed(false)
                    .title(title)
                    .content(content)
                    .category("유튜브 해석")
                    .tags(it.path("tags").asText(null))
                    .sourceClass(AdmissionsKnowledgeItem.SourceClass.YOUTUBE)
                    .sourceUrl(it.path("source_url").asText(null))
                    .role(AdmissionsKnowledgeItem.Role.FACT)
                    .status(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT)
                    .dedupKey(dedupKey)
                    .verificationNote("유튜브 영상 분석(buildit-admissions data/youtube). 공식 사실이 아닌 전문가 해석 — 답변에서는 별도 참고 단락으로만 쓴다.")
                    .createdBy("admissions-loader")
                    .build());
            n++;
        }
        return n;
    }

    /** authoritative_universities 한 대학 처리. 보관한 행 수를 돌려준다. */
    private int archiveSuperseded(JsonNode root, String code, Map<String, AdmissionsUniversity> univByCode) {
        AdmissionsUniversity seedUniv = requireUniversity(univByCode, code, "authoritative_universities");
        String base = baseName(seedUniv.getName());
        // 같은 캠퍼스끼리만 정리한다: 분교(ERICA 등) 시드가 본교 행을, 본교 시드가 분교 행을 보관하지 않게.
        String seedCampus = AdmissionsFactSearchService.campusMarker(seedUniv.getName());
        java.util.Set<Long> targets = new java.util.HashSet<>();
        for (AdmissionsUniversity u : universityRepository.findAll()) {
            if (u.getName().equals(seedUniv.getName())
                    || (base.equals(baseName(u.getName()))
                        && java.util.Objects.equals(seedCampus, AdmissionsFactSearchService.campusMarker(u.getName())))) {
                targets.add(u.getUniversityId());
            }
        }
        // 시드에 있는 이 대학 행의 자연키(학년도별). 시드가 다루는 학년도만 정리한다.
        java.util.Set<Integer> years = new java.util.HashSet<>();
        java.util.Set<String> keepType = new java.util.HashSet<>();
        java.util.Set<String> keepDept = new java.util.HashSet<>();
        java.util.Set<String> keepSched = new java.util.HashSet<>();
        java.util.Set<String> keepDocTitles = new java.util.HashSet<>();
        for (JsonNode t : root.path("ipsi_admission_types")) {
            if (!code.equals(t.path("university_code").asText())) continue;
            Integer y = readSupportedYear(t.path("academic_year"));
            if (y == null) continue;
            years.add(y);
            keepType.add(y + "|" + t.path("type_name").asText());
        }
        for (JsonNode d : root.path("ipsi_departments")) {
            if (!code.equals(d.path("university_code").asText())) continue;
            Integer y = readSupportedYear(d.path("academic_year"));
            if (y != null) keepDept.add(y + "|" + d.path("department_name").asText());
        }
        for (JsonNode sc : root.path("ipsi_schedules")) {
            if (!code.equals(sc.path("university_code").asText())) continue;
            Integer y = readSupportedYear(sc.path("academic_year"));
            if (y != null) keepSched.add(y + "|" + sc.path("item").asText() + " (" + sc.path("type_name").asText("") + ")"
                    + "|" + sc.path("date").asText(null));
        }
        for (JsonNode d : root.path("ipsi_documents")) keepDocTitles.add(d.path("document_name").asText());
        if (years.isEmpty()) return 0;

        Long seedId = seedUniv.getUniversityId();
        int archived = 0;
        java.util.Set<Long> stillUsedMaterials = new java.util.HashSet<>();
        for (AdmissionsType t : typeRepository.findAll()) {
            if (!inScope(t.getUniversity(), t.getAcademicYear(), targets, years)) continue;
            boolean keep = (seedId.equals(t.getUniversity().getUniversityId()) && keepType.contains(t.getAcademicYear() + "|" + t.getName()))
                    || otherCampus(sourceTitle(t.getSource()) + " " + t.getName(), seedCampus);
            if (keep || t.getStatus() == AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED) {
                if (t.getStatus() != AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED && t.getSource() != null) stillUsedMaterials.add(t.getSource().getMaterialId());
                continue;
            }
            t.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED);
            typeRepository.save(t);
            archiveKnowledge("type:" + t.getUniversity().getUniversityId() + ":" + t.getAcademicYear() + ":" + t.getAdmissionTypeId());
            archived++;
        }
        for (AdmissionsDepartment d : departmentRepository.findAll()) {
            if (!inScope(d.getUniversity(), d.getAcademicYear(), targets, years)) continue;
            boolean keep = (seedId.equals(d.getUniversity().getUniversityId()) && keepDept.contains(d.getAcademicYear() + "|" + d.getName()))
                    || otherCampus(sourceTitle(d.getSource()) + " " + d.getName(), seedCampus);
            if (keep || d.getStatus() == AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED) {
                if (d.getStatus() != AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED && d.getSource() != null) stillUsedMaterials.add(d.getSource().getMaterialId());
                continue;
            }
            d.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED);
            departmentRepository.save(d);
            archived++;
        }
        for (AdmissionsSchedule sc : scheduleRepository.findAll()) {
            if (!inScope(sc.getUniversity(), sc.getAcademicYear(), targets, years)) continue;
            boolean keep = (seedId.equals(sc.getUniversity().getUniversityId())
                    && keepSched.contains(sc.getAcademicYear() + "|" + sc.getTitle() + "|" + sc.getNote()))
                    || otherCampus(sourceTitle(sc.getSource()) + " " + sc.getTitle(), seedCampus);
            if (keep || sc.getStatus() == AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED) {
                if (sc.getStatus() != AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED && sc.getSource() != null) stillUsedMaterials.add(sc.getSource().getMaterialId());
                continue;
            }
            sc.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED);
            scheduleRepository.save(sc);
            archiveKnowledge("schedule:" + sc.getUniversity().getUniversityId() + ":" + sc.getAcademicYear() + ":" + sc.getScheduleId());
            archived++;
        }
        // 지난해 입시결과 행의 출처 자료는 모집요강 시드가 다루지 않는다(별도 시드로 들어올 수 있음) — 보관하지 않는다.
        stillUsedMaterials.addAll(priorResultRepository.findSourceMaterialIdsInUse(AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED));
        for (AdmissionsOfficialMaterial m : materialRepository.findAll()) {
            if (!inScope(m.getUniversity(), m.getAcademicYear(), targets, years)) continue;
            if (m.getStatus() == AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED) continue;
            if (keepDocTitles.contains(m.getTitle()) || stillUsedMaterials.contains(m.getMaterialId())) continue;
            if (otherCampus(m.getTitle(), seedCampus)) continue;
            m.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED);
            materialRepository.save(m);
            archived++;
        }
        return archived;
    }

    private static boolean inScope(AdmissionsUniversity u, Integer year, java.util.Set<Long> targets, java.util.Set<Integer> years) {
        return u != null && targets.contains(u.getUniversityId()) && years.contains(year);
    }

    /** 괄호·공백·캠퍼스 표시를 뺀 대학 이름. "한양대학교(ERICA)"와 "한양대학교 ERICA"는 같은 "한양대학교"다. */
    private static String baseName(String name) {
        if (name == null) return "";
        String t = name.replaceAll("\\(.*?\\)", "").replace(" ", "");
        String marker = AdmissionsFactSearchService.campusMarker(t);
        return marker == null ? t : t.replace(marker, "");
    }

    /** 시드 대학과 다른 캠퍼스 표시가 붙은 글인가. */
    private static boolean otherCampus(String text, String seedCampus) {
        String marker = AdmissionsFactSearchService.campusMarker(text);
        return marker != null && !marker.equals(seedCampus);
    }

    private static String materialKey(AdmissionsUniversity univ, String title) {
        return (univ == null ? "-" : String.valueOf(univ.getUniversityId())) + "|" + title;
    }

    /** 시드가 가리키는 자료가 있고, 행의 현재 자료와 다른가(없던 출처 포함). */
    private static boolean sourceChanged(AdmissionsOfficialMaterial current, AdmissionsOfficialMaterial seed) {
        return seed != null && (current == null || !java.util.Objects.equals(current.getMaterialId(), seed.getMaterialId()));
    }

    private static String sourceTitle(AdmissionsOfficialMaterial m) {
        return m == null || m.getTitle() == null ? "" : m.getTitle();
    }

    private void archiveKnowledge(String dedupKey) {
        knowledgeRepository.findByDedupKey(dedupKey).ifPresent(k -> {
            k.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED);
            knowledgeRepository.save(k);
        });
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
