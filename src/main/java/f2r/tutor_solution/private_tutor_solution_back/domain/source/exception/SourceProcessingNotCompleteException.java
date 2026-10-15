package f2r.tutor_solution.private_tutor_solution_back.domain.source.exception;

import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ProcessingStatus;

import java.util.List;

/**
 * 대화에 첨부된 source 중 하나 이상이 {@link ProcessingStatus#COMPLETED} 가 아닐 때 발생.
 *
 * <p>채팅(LLM 호출)은 모든 첨부 source의 분석이 끝난 뒤에만 허용된다 (MVP §5.0).
 * 컨트롤러 어드바이스는 이 예외를 409 Conflict로 변환한다.
 */
public class SourceProcessingNotCompleteException extends RuntimeException {

  public record PendingSource(Long sourceId, String fileName, ProcessingStatus status) {}

  private final List<PendingSource> pendingSources;

  public SourceProcessingNotCompleteException(String message, List<PendingSource> pendingSources) {
    super(message);
    this.pendingSources = pendingSources;
  }

  public List<PendingSource> getPendingSources() {
    return pendingSources;
  }
}
