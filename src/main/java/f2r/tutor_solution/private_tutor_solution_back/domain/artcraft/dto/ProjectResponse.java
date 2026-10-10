package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto;

/**
 * 프로젝트 조회/생성/되돌리기 응답.
 *
 * @param projectId 프로젝트 ID
 * @param academyId 소속 학원 ID (학원별 격리의 기준)
 * @param name 프로젝트 표시 이름
 * @param version 현재 버전 (낙관적 잠금 카운터)
 * @param createdBy 생성자 loginId
 * @param createdAt 생성 시각 (ISO-8601)
 * @param updatedAt 최종 수정 시각 (ISO-8601)
 * @param lastEditor 마지막 편집 주체 ({@code ai-agent} / {@code user} / loginId)
 * @param itemCount items 배열 원소 수
 * @param pdfPath 프로젝트 디렉토리 기준 상대 경로의 최신 PDF (없으면 null)
 */
public record ProjectResponse(
    String projectId,
    Long academyId,
    String name,
    int version,
    String createdBy,
    String createdAt,
    String updatedAt,
    String lastEditor,
    int itemCount,
    String pdfPath) {
}
