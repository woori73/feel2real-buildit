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
    private final f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsPriorResultRepository priorResults;

    public record ActivationResult(int academicYear, int materials, int departments, int types, int schedules,
                                   int excludedMaterials, int excludedChildren, int knowledgeItems, int priorResults) {
        public ActivationResult(int academicYear, int materials, int departments, int types, int schedules,
                                int excludedMaterials, int excludedChildren, int knowledgeItems) {
            this(academicYear, materials, departments, types, schedules, excludedMaterials, excludedChildren, knowledgeItems, 0);
        }
    }

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
        // 지난해 입시결과(상담 학년도 = academicYear)도 같은 규칙으로 승격한다. 출처 자료가 제외 대상이면 함께 제외.
        // 187개교면 수십만 행이라 엔티티로 읽지 않고 한 번의 UPDATE로 올린다.
        java.util.List<KnowledgeStatus> promotableStatuses = java.util.List.of(KnowledgeStatus.DRAFT, KnowledgeStatus.REVIEW);
        java.util.List<Long> excludedMaterialIds = new java.util.ArrayList<>();
        if (!excluded.isEmpty()) {
            for (var mat : materials.findAll()) {
                if (excluded.contains(mat.getFileKey())) excludedMaterialIds.add(mat.getMaterialId());
            }
        }
        if (excludedMaterialIds.isEmpty()) excludedMaterialIds.add(-1L); // 빈 IN 목록 방지
        exC += (int) priorResults.countExcluded(academicYear, promotableStatuses, excludedMaterialIds);
        int p = priorResults.promote(academicYear, promotableStatuses, excludedMaterialIds, KnowledgeStatus.ACTIVE);

        int k = 0;
        java.util.Set<Long> activeTypes = new java.util.HashSet<>();
        types.findAll().forEach(row -> { if (row.getStatus() == KnowledgeStatus.ACTIVE) activeTypes.add(row.getAdmissionTypeId()); });
        java.util.Set<Long> activeSchedules = new java.util.HashSet<>();
        schedules.findAll().forEach(row -> { if (row.getStatus() == KnowledgeStatus.ACTIVE) activeSchedules.add(row.getScheduleId()); });
        java.util.Set<Long> activePrior = new java.util.HashSet<>(priorResults.findIdsByStatus(KnowledgeStatus.ACTIVE));
        // 지식 항목도 본문을 읽지 않고 [id, dedupKey, sourceClass, createdBy]만 읽어 고른 뒤 id로 한꺼번에 올린다.
        java.util.List<Long> promote = new java.util.ArrayList<>();
        for (Object[] row : knowledgeItems.findPromotionCandidates(academicYear, promotableStatuses)) {
            Long id = (Long) row[0];
            String key = row[1] == null ? "" : (String) row[1];
            var sourceClass = (f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.SourceClass) row[2];
            String createdBy = (String) row[3];
            // 시드로 넣은 유튜브 해석 항목(원본 행 없음)도 함께 승격한다. 답변에서는 공식 근거와 분리해 참고로만 쓴다.
            boolean youtube = key.startsWith("youtube:")
                    && sourceClass == f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.SourceClass.YOUTUBE;
            if (!youtube) {
                if (!AdmissionsKnowledgeBuildService.CREATED_BY.equals(createdBy)) continue;
                boolean backed = (key.startsWith("type:") && activeTypes.contains(idOf(key)))
                        || (key.startsWith("schedule:") && activeSchedules.contains(idOf(key)))
                        || (key.startsWith("prior:") && activePrior.contains(idOf(key)));
                if (!backed) continue;
            }
            promote.add(id);
        }
        for (int i = 0; i < promote.size(); i += 1000) {
            k += knowledgeItems.updateStatusByIds(promote.subList(i, Math.min(promote.size(), i + 1000)), KnowledgeStatus.ACTIVE);
        }
        return new ActivationResult(academicYear, m, d, t, s, exM, exC, k, p);
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
