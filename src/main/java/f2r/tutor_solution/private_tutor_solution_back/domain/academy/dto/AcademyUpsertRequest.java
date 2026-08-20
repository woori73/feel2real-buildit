package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;

import java.util.List;
import java.util.Set;

public record AcademyUpsertRequest(
    String name,
    String handle,
    String intro,
    Set<Integer> subjectIds,
    Set<TargetGroup> targetGroups,
    List<AcademyRegionRequest> regions,
    AcademyPageSettings pageSettings
) {
}
