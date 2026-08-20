package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import java.io.IOException;

/**
 * 저장 객체가 호출자가 정한 메모리 조회 상한을 초과할 때 발생한다.
 *
 * <p>메시지에는 storage key나 로컬 절대경로를 포함하지 않는다.
 */
public class StorageReadLimitExceededException extends IOException {

  public StorageReadLimitExceededException(long maxBytes) {
    super("저장 객체가 조회 상한(" + maxBytes + " bytes)을 초과했습니다.");
  }
}
