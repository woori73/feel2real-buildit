package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto;

/**
 * {@code GET /api/artcraft/projects/{id}/snapshots} 응답 원소.
 *
 * @param version 스냅샷이 캡처한 프로젝트 버전
 *        ({@code snapshots/v{n}.json} = 버전 n 상태의 프로젝트 JSON)
 * @param by 스냅샷 생성 주체
 * @param desc 스냅샷 설명 (편집 desc 또는 "v{n}으로 되돌리기")
 * @param at 스냅샷 생성 시각 (ISO-8601)
 */
public record SnapshotResponse(
    int version,
    String by,
    String desc,
    String at) {
}
