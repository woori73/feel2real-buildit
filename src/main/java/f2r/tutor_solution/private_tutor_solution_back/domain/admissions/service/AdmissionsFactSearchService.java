package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsDepartment;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsOfficialMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsSchedule;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsType;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsUniversity;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsDepartmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsScheduleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsTypeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsUniversityRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsResearchService.SourceClass;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 학과(모집단위)·전형·일정 실제 DB 데이터를 상담 근거로 만든다.
 *
 * <p>규칙:
 * <ul>
 *   <li>승인(ACTIVE)된 행만 사용한다. DRAFT/REVIEW 행은 검색되지 않는다.</li>
 *   <li>학년도로 먼저 거른다. 질문에 학년도가 없으면 2027(확정)만 본다. 2027과 2028을 한 근거 목록에 섞는 것은
 *       질문이 둘 다 명시한 경우에만 한다.</li>
 *   <li>값이 비어 있는 필드는 "확인되지 않음"으로 표기한다(추정 금지).</li>
 * </ul>
 * 데이터 규모(수천 행 이하)를 전제로 메모리에서 거른다. 커지면 대학·학년도 인덱스 쿼리로 바꾼다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdmissionsFactSearchService {

    static final int STEP_FACT = 0;
    static final int MAX_DEPARTMENTS = 10;
    static final int MAX_TYPES = 8;
    static final int MAX_SCHEDULES = 8;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AdmissionsUniversityRepository universityRepository;
    private final AdmissionsDepartmentRepository departmentRepository;
    private final AdmissionsTypeRepository typeRepository;
    private final AdmissionsScheduleRepository scheduleRepository;

    /**
     * @param evidence 근거(없으면 빈 목록)
     * @param parsed 질문 분석 결과
     * @param searchedYears 실제로 조회한 학년도
     * @param unpublished 2028처럼 아직 발표 자료가 없는 학년도를 물었고 근거가 하나도 없는 경우
     */
    public record FactResult(List<Evidence> evidence, AdmissionsQueryParser.Parsed parsed,
                             Set<Integer> searchedYears, boolean unpublished) {}

    public FactResult search(String question, Integer yearOverride) {
        List<String> univNames = universityRepository.findAll().stream()
                .map(AdmissionsUniversity::getName).toList();
        AdmissionsQueryParser.Parsed parsed = AdmissionsQueryParser.parse(question, univNames);

        Set<Integer> years = new LinkedHashSet<>();
        if (yearOverride != null) years.add(yearOverride);
        else if (!parsed.years().isEmpty()) years.addAll(parsed.years());
        else years.add(2027); // 학년도 미지정: 확정 자료(2027)만. 답변에 기준 학년도를 명시한다.

        List<Evidence> out = new ArrayList<>();
        out.addAll(searchDepartments(question, parsed, years));
        out.addAll(searchTypes(question, parsed, years));
        out.addAll(searchSchedules(question, parsed, years));

        boolean askedUnpublished = years.contains(2028) && !years.contains(2027);
        return new FactResult(out, parsed, years, askedUnpublished && out.isEmpty());
    }

    // ── 모집단위(학과) ───────────────────────────────────────────────

    private List<Evidence> searchDepartments(String question, AdmissionsQueryParser.Parsed p, Set<Integer> years) {
        List<AdmissionsDepartment> candidates = new ArrayList<>();
        for (AdmissionsDepartment d : departmentRepository.findAll()) {
            if (d.getStatus() != KnowledgeStatus.ACTIVE || !years.contains(d.getAcademicYear())) continue;
            if (!univSelected(p, d.getUniversity())) continue;
            candidates.add(d);
        }
        // 1차: 학과명(접미 제거 포함) 매칭. 하나도 없으면 2차: 핵심어 부분 일치(예: "반도체").
        List<AdmissionsDepartment> matched = candidates.stream()
                .filter(d -> AdmissionsQueryParser.mentionsDepartment(question, d.getName())).toList();
        if (matched.isEmpty()) {
            matched = candidates.stream()
                    .filter(d -> AdmissionsQueryParser.mentionsDepartmentByKeyword(question, d.getName())).toList();
        }
        List<Evidence> out = new ArrayList<>();
        for (AdmissionsDepartment d : matched) {
            out.add(evidence("dept", d.getDepartmentId(), d.getAcademicYear(),
                    d.getUniversity().getName() + " " + d.getAcademicYear() + "학년도 " + d.getName() + " 모집인원",
                    "전형별 모집인원: " + formatQuota(d.getQuotaInfo()), d.getSource()));
            if (out.size() >= MAX_DEPARTMENTS) break;
        }
        return out;
    }

    // ── 전형 ─────────────────────────────────────────────────────────

    private List<Evidence> searchTypes(String question, AdmissionsQueryParser.Parsed p, Set<Integer> years) {
        if (p.universities().isEmpty()) return List.of();
        List<AdmissionsType> candidates = new ArrayList<>();
        for (AdmissionsType t : typeRepository.findAll()) {
            if (t.getStatus() != KnowledgeStatus.ACTIVE || !years.contains(t.getAcademicYear())) continue;
            if (!univSelected(p, t.getUniversity())) continue;
            candidates.add(t);
        }
        List<AdmissionsType> matched = candidates.stream()
                .filter(t -> AdmissionsQueryParser.typePieces(t.getName()).stream().anyMatch(question::contains))
                .toList();
        List<AdmissionsType> chosen = !matched.isEmpty() ? matched : (p.wantsType() ? candidates : List.of());
        List<Evidence> out = new ArrayList<>();
        for (AdmissionsType t : chosen) {
            out.add(evidence("type", t.getAdmissionTypeId(), t.getAcademicYear(),
                    t.getUniversity().getName() + " " + t.getAcademicYear() + "학년도 " + t.getName(),
                    cleanDetails(t.getDetails()), t.getSource()));
            if (out.size() >= MAX_TYPES) break;
        }
        return out;
    }

    // ── 일정 ─────────────────────────────────────────────────────────

    private List<Evidence> searchSchedules(String question, AdmissionsQueryParser.Parsed p, Set<Integer> years) {
        if (p.universities().isEmpty() || !p.wantsSchedule()) return List.of();
        List<Evidence> out = new ArrayList<>();
        for (AdmissionsSchedule s : scheduleRepository.findAll()) {
            if (s.getStatus() != KnowledgeStatus.ACTIVE || !years.contains(s.getAcademicYear())) continue;
            if (s.getUniversity() == null || !univSelected(p, s.getUniversity())) continue;
            if (!scheduleMatches(question, s.getTitle())) continue;
            out.add(evidence("sched", s.getScheduleId(), s.getAcademicYear(),
                    s.getUniversity().getName() + " " + s.getAcademicYear() + "학년도 " + s.getTitle(),
                    "일정 원문: " + (s.getNote() == null || s.getNote().isBlank() ? "확인되지 않음" : s.getNote()),
                    s.getSource()));
            if (out.size() >= MAX_SCHEDULES) break;
        }
        return out;
    }

    static boolean scheduleMatches(String question, String title) {
        for (String term : AdmissionsQueryParser.SCHEDULE_TERMS) {
            if (question.contains(term) && title.contains(term)) return true;
        }
        return AdmissionsQueryParser.typePieces(title).stream()
                .filter(piece -> piece.length() >= 3)
                .anyMatch(question::contains);
    }

    // ── helpers ──────────────────────────────────────────────────────

    private static boolean univSelected(AdmissionsQueryParser.Parsed p, AdmissionsUniversity u) {
        return p.universities().isEmpty() || (u != null && p.universities().contains(u.getName()));
    }

    private static Evidence evidence(String kind, Long id, Integer year, String title, String excerpt,
                                     AdmissionsOfficialMaterial source) {
        boolean confirmed = year != null && year == 2027;
        String sourceTitle = source == null ? "출처 자료 연결 없음" : source.getTitle();
        return Evidence.builder()
                .stepNo(STEP_FACT)
                .sourceClass(confirmed ? SourceClass.OFFICIAL_CONFIRMED : SourceClass.OFFICIAL_PLANNED)
                .refId(kind + ":" + id)
                .refUrl(source == null ? null : source.getDocumentUrl())
                .title(title)
                .excerpt(excerpt)
                .note("공식자료 DB(" + kind + ") / 출처: " + sourceTitle)
                .academicYear(year)
                .yearConfirmed(confirmed)
                .build();
    }

    /** {"학교추천":19,"논술":0,"합계":81} → "학교추천 19명, 합계 81명" (0명은 생략). */
    static String formatQuota(String quotaJson) {
        if (quotaJson == null || quotaJson.isBlank()) return "확인되지 않음";
        try {
            JsonNode n = MAPPER.readTree(quotaJson);
            List<String> parts = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> it = n.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getValue().isNumber() && e.getValue().asInt() > 0) {
                    parts.add(e.getKey() + " " + e.getValue().asInt() + "명");
                }
            }
            return parts.isEmpty() ? "확인되지 않음" : String.join(", ", parts);
        } catch (Exception e) {
            return "확인되지 않음";
        }
    }

    /** 비어 있는 항목("수능최저: ")은 "확인되지 않음"으로 바꿔 LLM이 추정하지 않게 한다. */
    static String cleanDetails(String details) {
        if (details == null || details.isBlank()) return "확인되지 않음";
        StringBuilder sb = new StringBuilder();
        for (String line : details.split("\n")) {
            String trimmed = line.stripTrailing();
            if (trimmed.endsWith(":") || trimmed.endsWith(": ")) {
                sb.append(trimmed.stripTrailing()).append(" 확인되지 않음(추정 금지)\n");
            } else {
                sb.append(trimmed).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
