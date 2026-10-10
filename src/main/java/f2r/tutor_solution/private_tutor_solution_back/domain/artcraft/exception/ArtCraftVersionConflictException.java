package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception;

import lombok.Getter;

/**
 * 낙관적 잠금 충돌: 편집 요청의 {@code expectedVersion}이 서버의 현재 버전과 다를 때 발생.
 * 409 Conflict + 현재 버전으로 변환된다. 호출자는 최신 프로젝트를 다시 읽고 재시도하거나
 * 사용자에게 확인해야 한다.
 */
@Getter
public class ArtCraftVersionConflictException extends RuntimeException {

  /** 충돌 시점의 서버 프로젝트 버전. */
  private final int currentVersion;

  public ArtCraftVersionConflictException(int expectedVersion, int currentVersion, String lastEditor) {
    super("버전 충돌: 요청 v" + expectedVersion + ", 현재 v" + currentVersion
        + " (마지막 편집: " + lastEditor + "). 최신 버전을 다시 읽고 재시도하세요.");
    this.currentVersion = currentVersion;
  }
}
