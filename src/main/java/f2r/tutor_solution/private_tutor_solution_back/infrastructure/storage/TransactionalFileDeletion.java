package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 파일 삭제를 DB 트랜잭션 결과에 묶는다.
 *
 * <p>파일 저장소는 트랜잭션 밖이라, 트랜잭션 안에서 즉시 지우면 이후 DB 저장 실패·롤백 시
 * DB가 가리키는 파일만 사라지는 불일치가 생긴다. 그래서 삭제는 커밋 이후로 미루고,
 * 파일 교체는 롤백 시 새로 올린 파일을 정리해 고아 파일도 남기지 않는다.
 * 트랜잭션 동기화가 없으면(단위 테스트 등) 기존처럼 즉시 실행한다.
 */
public final class TransactionalFileDeletion {

  private TransactionalFileDeletion() {}

  /** 트랜잭션이 커밋된 뒤에만 실행한다. 롤백되면 실행하지 않는다. */
  public static void afterCommit(Runnable deletion) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          deletion.run();
        }
      });
    } else {
      deletion.run();
    }
  }

  /** 파일 교체 보상 — 커밋되면 기존 파일 삭제, 롤백되면 새 파일 삭제. */
  public static void onReplace(Runnable deleteOldFile, Runnable deleteNewFile) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          if (status == TransactionSynchronization.STATUS_COMMITTED) {
            deleteOldFile.run();
          } else {
            deleteNewFile.run();
          }
        }
      });
    } else {
      deleteOldFile.run();
    }
  }
}
