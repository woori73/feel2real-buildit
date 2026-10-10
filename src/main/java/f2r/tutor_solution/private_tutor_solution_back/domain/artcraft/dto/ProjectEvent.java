package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto;

/**
 * {@code GET /api/artcraft/projects/{id}/events} (SSE)로 전달되는 변경 알림.
 *
 * @param type 이벤트 종류: {@code created} / {@code edited} / {@code reverted} /
 *        {@code subscribed}(구독 직후 현재 버전 동기화용) /
 *        {@code source_uploaded}(편집 소스 등록, P0-④)
 * @param projectId 프로젝트 ID
 * @param version 이벤트 시점의 프로젝트 버전
 * @param at 이벤트 발생 시각 (ISO-8601)
 * @param by 변경 주체
 * @param desc 변경 설명
 */
public record ProjectEvent(
    String type,
    String projectId,
    int version,
    String at,
    String by,
    String desc) {
}

