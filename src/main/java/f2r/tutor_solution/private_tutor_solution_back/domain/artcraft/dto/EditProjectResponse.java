package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto;

import java.util.List;

/**
 * {@code POST /api/artcraft/projects/{id}/edit} 응답.
 *
 * @param projectId 프로젝트 ID
 * @param version 편집 후 버전 ({@code expectedVersion + 1})
 * @param updatedAt 편집 시각 (ISO-8601)
 * @param cliOutput CLI 표준 출력 요약 (디버깅용, 최대 4KB로 잘림)
 * @param warnings 비차단 경고 목록 (예: 사용자 수정 후 AI 덮어쓰기 주의)
 */
public record EditProjectResponse(
    String projectId,
    int version,
    String updatedAt,
    String cliOutput,
    List<String> warnings) {
}
