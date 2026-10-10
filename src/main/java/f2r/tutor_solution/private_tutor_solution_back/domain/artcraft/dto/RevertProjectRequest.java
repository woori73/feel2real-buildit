package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto;

/**
 * {@code POST /api/artcraft/projects/{id}/revert} 요청 본문.
 *
 * @param targetVersion 되돌릴 대상 버전 (필수). {@code snapshots/v{targetVersion}.json}이
 *        존재해야 한다.
 * @param actor 되돌리기 주체 식별자 (선택, 기본값: 요청자의 loginId)
 * @param desc 되돌리기 설명 (선택, 히스토리에 기록)
 */
public record RevertProjectRequest(
    Integer targetVersion,
    String actor,
    String desc) {
}
