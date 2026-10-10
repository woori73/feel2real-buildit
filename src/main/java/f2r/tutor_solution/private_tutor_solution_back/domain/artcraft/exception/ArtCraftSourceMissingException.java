package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception;

/**
 * 편집 선행조건 위반 — 프로젝트에 편집 소스({@code source.pdf})가 등록되지 않았다.
 *
 * <p>409 Conflict로 매핑된다. 호출자는
 * {@code POST /api/artcraft/projects/{id}/source}로 HTML 또는 PDF 소스를
 * 먼저 업로드해야 한다.
 */
public class ArtCraftSourceMissingException extends RuntimeException {

  public ArtCraftSourceMissingException(String projectId) {
    super("소스를 먼저 업로드하세요: 프로젝트 '" + projectId
        + "'에 편집 소스(source.pdf)가 없습니다. "
        + "POST /api/artcraft/projects/{projectId}/source 로 HTML 또는 PDF를 등록하세요.");
  }
}
