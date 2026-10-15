package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import java.io.IOException;

/**
 * 저장소의 단일 객체 조회가 운영상 실패했음을 나타내는 key-free checked exception.
 *
 * <p>SDK 예외의 message/cause에는 버킷이나 객체 key가 포함될 수 있으므로 저장소 경계 밖으로 전달하지
 * 않는다.
 */
public class StorageReadException extends IOException {

  public StorageReadException() {
    super("저장소 객체를 읽을 수 없습니다.");
  }
}
