package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiIdempotency;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAiIdempotencyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiIdempotencyService.DuplicateIdempotencyKeyException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * AcademyAiIdempotencyService 테스트.
 *
 * <p>멱등성은 단순한 409 변환이 아니라 아래를 검증한다:
 * <ul>
 *   <li>동일 키 재호출 → DuplicateIdempotencyKeyException (→ HTTP 409)
 *   <li>이전 FAILED 키 → 재시도 허용
 *   <li>조회-삽입 경쟁 → UNIQUE 제약 위반을 409로 변환
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AcademyAiIdempotencyService — 멱등성 키 선점")
class AcademyAiIdempotencyServiceTest {

  @Mock
  private AcademyAiIdempotencyRepository repository;

  private AcademyAiIdempotencyService service;

  @BeforeEach
  void setUp() {
    service = new AcademyAiIdempotencyService(repository);
  }

  @Test
  @DisplayName("given 새 키 when 선점 then PROCESSING 레코드가 반환된다")
  void claim_newKey_returnsProcessingRecord() {
    when(repository.findByTutorIdAndIdempotencyKey("t1", "k1"))
        .thenReturn(Optional.empty());
    AcademyAiIdempotency saved = new AcademyAiIdempotency("t1", "k1");
    when(repository.saveAndFlush(any(AcademyAiIdempotency.class))).thenReturn(saved);

    AcademyAiIdempotency result = service.claim("t1", "k1");

    assertThat(result.isProcessing()).isTrue();
    assertThat(result.getIdempotencyKey()).isEqualTo("k1");
  }

  @Test
  @DisplayName("given 완료된 키 when 재선점 then 409용 예외가 던져진다")
  void claim_completedKey_throwsDuplicate() {
    AcademyAiIdempotency completed = new AcademyAiIdempotency("t1", "k1");
    completed.markCompleted(42L);
    when(repository.findByTutorIdAndIdempotencyKey("t1", "k1"))
        .thenReturn(Optional.of(completed));

    assertThatThrownBy(() -> service.claim("t1", "k1"))
        .isInstanceOf(DuplicateIdempotencyKeyException.class)
        .satisfies(e -> assertThat(
            ((DuplicateIdempotencyKeyException) e).getRecord().getAcademyId())
            .isEqualTo(42L));
  }

  @Test
  @DisplayName("given 처리 중인 키 when 재선점 then 409용 예외가 던져진다")
  void claim_processingKey_throwsDuplicate() {
    AcademyAiIdempotency processing = new AcademyAiIdempotency("t1", "k1");
    when(repository.findByTutorIdAndIdempotencyKey("t1", "k1"))
        .thenReturn(Optional.of(processing));

    assertThatThrownBy(() -> service.claim("t1", "k1"))
        .isInstanceOf(DuplicateIdempotencyKeyException.class);
  }

  @Test
  @DisplayName("given 실패한 키 when 재선점 then 재시도가 허용된다")
  void claim_failedKey_allowsRetry() {
    AcademyAiIdempotency failed = new AcademyAiIdempotency("t1", "k1");
    failed.markFailed();
    when(repository.findByTutorIdAndIdempotencyKey("t1", "k1"))
        .thenReturn(Optional.of(failed))
        .thenReturn(Optional.empty());
    AcademyAiIdempotency fresh = new AcademyAiIdempotency("t1", "k1");
    when(repository.saveAndFlush(any(AcademyAiIdempotency.class))).thenReturn(fresh);

    AcademyAiIdempotency result = service.claim("t1", "k1");

    assertThat(result.isProcessing()).isTrue();
  }

  @Test
  @DisplayName("given 조회-삽입 경쟁 when UNIQUE 위반 then 409용 예외가 던져진다")
  void claim_raceOnInsert_throwsDuplicate() {
    when(repository.findByTutorIdAndIdempotencyKey("t1", "k1"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(new AcademyAiIdempotency("t1", "k1")));
    when(repository.saveAndFlush(any(AcademyAiIdempotency.class)))
        .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

    assertThatThrownBy(() -> service.claim("t1", "k1"))
        .isInstanceOf(DuplicateIdempotencyKeyException.class);
  }
}
