package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsOfficialMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsDepartmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsOfficialMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsScheduleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 한 학년도의 공식자료·학과·전형·일정을 한 번에 ACTIVE로 승격한다 (운영 정책: 신규 입시자료는 전체 ACTIVE가 기본).
 *
 * <p>학과·전형·일정에는 개별 승인 API가 없어서 이 서비스가 유일한 승격 경로다. 호출은 ADMIN 전용
 * ({@code /api/admin/admissions/**})이고 확인 문자열이 맞아야 하며, 한 트랜잭션이라 중간 실패 시 되돌려진다.
 * ARCHIVED 행과 다른 학년도 행은 건드리지 않는다.
 *
 * <p>제외 옵션: {@code excludeFileKeys}에 공식자료의 fileKey(= 시드 document_key)를 지정하면 그 자료는
 * 승격 단계에서 원천 제외되고, 그 자료를 출처로 삼는 학과·전형·일정도 함께 제외된다.
 * (예: 공식 원본이 아닌 미러본 자료를 ACTIVE로 올리지 않기 위한 용도. 승격 후 비활성화 방식은 쓰지 않는다.)
 * 제외 건수는 승격 대상(해당 학년도·DRAFT/REVIEW)이었으나 제외된 행만 센다.
 * <p>구조화 행에서 만든 검색용 지식 항목({@code knowledge-build}가 만든 type/schedule 항목)도 함께 승격한다.
 * 단, 원본 행이 ACTIVE일 때만이다(원본이 ARCHIVED·DRAFT면 항목도 올리지 않는다). 항목을 만들기 전이면 0건이고,
 * 만든 뒤 다시 실행하면 항목만 올라간다(멱등).
 */
@Service
@RequiredArgsConstructor
public class AdmissionsActivationService {

    private final AdmissionsOfficialMaterialRepository materials;
    private final AdmissionsDepartmentRepository departments;
    private final AdmissionsTypeRepository types;
    private final AdmissionsScheduleRepository schedules;
    private final AdmissionsKnowledgeItemRepository knowledgeItems;

    public record ActivationResult(int academicYear, int materials, int departments, int types, int schedules,
                                   int excludedMaterials, int excludedChildren, int knowledgeItems) {}

    public static String confirmationFor(int academicYear) {
        return "ACTIVATE-ALL-" + academicYear;
    }

    @Transactional
    public ActivationResult activateAll(int academicYear, String confirm) {
        return activateAll(academicYear, confirm, Set.of());
    }

    @Transactional
    public ActivationResult activateAll(int academicYear, String confirm, Set<String> excludeFileKeys) {
        if (academicYear != 2027 && academicYear != 2028) {
            throw new IllegalArgumentException("학년도는 2027 또는 2028만 지정할 수 있습니다.");
        }
        if (!confirmationFor(academicYear).equals(confirm)) {
            throw new IllegalArgumentException("확인 문자열이 맞지 않습니다.");
        }
        Set<String> excluded = excludeFileKeys == null ? Set.of() : excludeFileKeys;
        int m = 0, d = 0, t = 0, s = 0, exM = 0, exC = 0;
        for (var row : materials.findAll()) {
            if (row.getAcademicYear() == academicYear && promotable(row.getStatus())) {
                if (excluded.contains(row.getFileKey())) { exM++; continue; }
                row.setStatus(KnowledgeStatus.ACTIVE); m++;
            }
        }
        for (var row : departments.findAll()) {
            if (row.getAcademicYear() == academicYear && promotable(row.getStatus())) {
                if (excludedBySource(row.getSource(), excluded)) { exC++; continue; }
                row.setStatus(KnowledgeStatus.ACTIVE); d++;
            }
        }
        for (var row : types.findAll()) {
            if (row.getAcademicYear() == academicYear && promotable(row.getStatus())) {
                if (excludedBySource(row.getSource(), excluded)) { exC++; continue; }
                row.setStatus(KnowledgeStatus.ACTIVE); t++;
            }
        }
        for (var row : schedules.findAll()) {
            if (row.getAcademicYear() == academicYear && promotable(row.getStatus())) {
                if (excludedBySource(row.getSource(), excluded)) { exC++; continue; }
                row.setStatus(KnowledgeStatus.ACTIVE); s++;
            }
        }
        int k = 0;
        java.util.Set<Long> activeTypes = new java.util.HashSet<>();
        types.findAll().forEach(row -> { if (row.getStatus() == KnowledgeStatus.ACTIVE) activeTypes.add(row.getAdmissionTypeId()); });
        java.util.Set<Long> activeSchedules = new java.util.HashSet<>();
        schedules.findAll().forEach(row -> { if (row.getStatus() == KnowledgeStatus.ACTIVE) activeSchedules.add(row.getScheduleId()); });
        for (var item : knowledgeItems.findAll()) {
            if (item.getAcademicYear() != academicYear || !promotable(item.getStatus())) continue;
            if (!AdmissionsKnowledgeBuildService.CREATED_BY.equals(item.getCreatedBy())) continue;
            String key = item.getDedupKey() == null ? "" : item.getDedupKey();
            boolean backed = (key.startsWith("type:") && activeTypes.contains(idOf(key)))
                    || (key.startsWith("schedule:") && activeSchedules.contains(idOf(key)));
            if (!backed) continue;
            item.setStatus(KnowledgeStatus.ACTIVE);
            k++;
        }
        return new ActivationResult(academicYear, m, d, t, s, exM, exC, k);
    }

    /** 출처 자료가 제외 대상이면 true. 출처가 없는 행은 제외할 근거가 없어 승격 대상에 남는다. */
    private static boolean excludedBySource(AdmissionsOfficialMaterial source, Set<String> excluded) {
        return source != null && excluded.contains(source.getFileKey());
    }

    private static long idOf(String dedupKey) {
        try {
            return Long.parseLong(dedupKey.substring(dedupKey.lastIndexOf(':') + 1));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static boolean promotable(KnowledgeStatus status) {
        return status == KnowledgeStatus.DRAFT || status == KnowledgeStatus.REVIEW;
    }
}
