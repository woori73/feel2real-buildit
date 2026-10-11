package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiIdempotency;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAiIdempotencyRepository;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BuildIt AI 생성 멱등성 서비스 (P1).
 *
 * <p>{@code POST /api/tutor/academy/ai/chat/create/apply}의 중복 적용을 방지한다.
 * {@code (tutorId, idempotencyKey)} UNIQUE 제약을 이용해 동시 요청 경쟁도 DB 레벨에서 차단한다.
 *
 * <p>단순한 409 변환이 아니라 아래를 보장한다:
 * <ul>
 *   <li>동일 키 재호출 → 기존 결과와 함께 409 (COMPLETED), 처리 중이면 409 (PROCESSING)
 *   <li>이전 실패(FAILED) 키는 재시도 허용 (레코드 교체 후 진행)
 *   <li>경쟁에서 진 요청도 UNIQUE 제약 위반으로 감지되어 409로 변환된다
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AcademyAiIdempotencyService {

  private final AcademyAiIdempotencyRepository repository;

  /**
   * 동일 키로 이미 처리된(또는 처리 중인) 요청이 있을 때 던진다.
   * 컨트롤러에서 HTTP 409로 변환한다.
   */
  @Getter
  public static class DuplicateIdempotencyKeyException extends RuntimeException {
    private final AcademyAiIdempotency record;

    public DuplicateIdempotencyKeyException(AcademyAiIdempotency record) {
      super("이미 처리된 요청입니다: " + record.getIdempotencyKey());
      this.record = record;
    }
  }

  @Transactional(readOnly = true)
  public Optional<AcademyAiIdempotency> find(String tutorId, String idempotencyKey) {
    return repository.findByTutorIdAndIdempotencyKey(tutorId, idempotencyKey);
  }

  @Transactional
  public AcademyAiIdempotency insertFresh(String tutorId, String idempotencyKey) {
    return repository.saveAndFlush(new AcademyAiIdempotency(tutorId, idempotencyKey));
  }

  @Transactional
  public void deleteById(Long id) {
    repository.deleteById(id);
  }

  @Transactional
  public void markCompleted(Long id, Long academyId) {
    repository.findById(id).ifPresent(rec -> rec.markCompleted(academyId));
  }

  @Transactional
  public void markFailed(Long id) {
    repository.findById(id).ifPresent(AcademyAiIdempotency::markFailed);
  }

  /**
   * 멱등성 키를 선점한다.
   *
   * @return 새로 선점한 레코드
   * @throws DuplicateIdempotencyKeyException 이미 처리된/처리 중인 키일 때
   */
  public AcademyAiIdempotency claim(String tutorId, String idempotencyKey) {
    Optional<AcademyAiIdempotency> existing = find(tutorId, idempotencyKey);
    if (existing.isPresent()) {
      AcademyAiIdempotency rec = existing.get();
      if (rec.isFailed()) {
        // 이전 시도 실패 → 재시도 허용: 기존 레코드를 교체한다
        deleteById(rec.getId());
      } else if (rec.isProcessingExpired()) {
        // PROCESSING 만료 (30분 초과) → 고착이 아니라 재시도 허용
        // 기존 레코드를 삭제하고 새로 선점한다
        deleteById(rec.getId());
      } else {
        throw new DuplicateIdempotencyKeyException(rec);
      }
    }
    try {
      return insertFresh(tutorId, idempotencyKey);
    } catch (DataIntegrityViolationException e) {
      // 조회와 삽입 사이 경쟁에서 짐 → UNIQUE 제약이 잡아냄
      AcademyAiIdempotency raced = find(tutorId, idempotencyKey)
          .orElseThrow(() -> e);
      throw new DuplicateIdempotencyKeyException(raced);
    }
  }
}
