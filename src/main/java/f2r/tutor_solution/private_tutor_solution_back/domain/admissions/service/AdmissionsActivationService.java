package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsDepartmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsOfficialMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsScheduleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 학년도의 공식자료·학과·전형·일정을 한 번에 ACTIVE로 승격한다 (운영 정책: 신규 입시자료는 전체 ACTIVE가 기본).
 *
 * <p>학과·전형·일정에는 개별 승인 API가 없어서 이 서비스가 유일한 승격 경로다. 호출은 ADMIN 전용
 * ({@code /api/admin/admissions/**})이고 확인 문자열이 맞아야 하며, 한 트랜잭션이라 중간 실패 시 되돌려진다.
 * ARCHIVED 행과 다른 학년도 행은 건드리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AdmissionsActivationService {

    private final AdmissionsOfficialMaterialRepository materials;
    private final AdmissionsDepartmentRepository departments;
    private final AdmissionsTypeRepository types;
    private final AdmissionsScheduleRepository schedules;

    public record ActivationResult(int academicYear, int materials, int departments, int types, int schedules) {}

    public static String confirmationFor(int academicYear) {
        return "ACTIVATE-ALL-" + academicYear;
    }

    @Transactional
    public ActivationResult activateAll(int academicYear, String confirm) {
        if (academicYear != 2027 && academicYear != 2028) {
            throw new IllegalArgumentException("학년도는 2027 또는 2028만 지정할 수 있습니다.");
        }
        if (!confirmationFor(academicYear).equals(confirm)) {
            throw new IllegalArgumentException("확인 문자열이 맞지 않습니다.");
        }
        int m = 0, d = 0, t = 0, s = 0;
        for (var row : materials.findAll()) {
            if (row.getAcademicYear() == academicYear && promotable(row.getStatus())) { row.setStatus(KnowledgeStatus.ACTIVE); m++; }
        }
        for (var row : departments.findAll()) {
            if (row.getAcademicYear() == academicYear && promotable(row.getStatus())) { row.setStatus(KnowledgeStatus.ACTIVE); d++; }
        }
        for (var row : types.findAll()) {
            if (row.getAcademicYear() == academicYear && promotable(row.getStatus())) { row.setStatus(KnowledgeStatus.ACTIVE); t++; }
        }
        for (var row : schedules.findAll()) {
            if (row.getAcademicYear() == academicYear && promotable(row.getStatus())) { row.setStatus(KnowledgeStatus.ACTIVE); s++; }
        }
        return new ActivationResult(academicYear, m, d, t, s);
    }

    private static boolean promotable(KnowledgeStatus status) {
        return status == KnowledgeStatus.DRAFT || status == KnowledgeStatus.REVIEW;
    }
}
