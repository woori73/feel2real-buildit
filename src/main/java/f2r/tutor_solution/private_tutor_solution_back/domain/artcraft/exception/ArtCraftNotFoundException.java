package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception;

/**
 * 프로젝트 또는 스냅샷을 찾을 수 없을 때 발생. 404로 변환된다.
 */
public class ArtCraftNotFoundException extends RuntimeException {

  public ArtCraftNotFoundException(String message) {
    super(message);
  }
}
