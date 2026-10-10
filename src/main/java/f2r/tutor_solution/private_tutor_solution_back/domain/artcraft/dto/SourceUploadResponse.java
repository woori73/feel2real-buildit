package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto;

/**
 * {@code POST /api/artcraft/projects/{id}/source} 응답.
 *
 * @param projectId 프로젝트 ID
 * @param academyId 소속 학원 ID (학원별 격리의 기준)
 * @param version 소스 업로드 시점의 프로젝트 버전.
 *        소스 업로드로 버전이 증가하지는 않는다 (버전은 edit의 낙관적 잠금 카운터 전용).
 * @param sourceType 업로드된 소스의 종류 ("html" | "pdf")
 * @param sourcePath 프로젝트 디렉토리 기준 상대 경로 (항상 "source.pdf")
 * @param converted HTML→PDF 변환 수행 여부 (PDF 직접 업로드면 false)
 */
public record SourceUploadResponse(
    String projectId,
    Long academyId,
    int version,
    String sourceType,
    String sourcePath,
    boolean converted) {
}
