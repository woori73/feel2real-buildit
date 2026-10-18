package f2r.tutor_solution.private_tutor_solution_back.domain.examrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.dto.ExamRequestCreateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.dto.ExamRequestResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.entity.ExamRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.entity.ExamRequestStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.repository.ExamRequestRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.service.ExamRequestService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ExamRequestServiceTest {

  private final ExamRequestRepository repository = mock(ExamRequestRepository.class);
  private final ExamRequestService service = new ExamRequestService(repository);

  @Test
  @DisplayName("given 정상 폼 when register then 저장하고 응답을 돌려준다")
  void registersRequest() {
    when(repository.save(any(ExamRequest.class))).thenAnswer(i -> i.getArgument(0));
    ExamRequestResponse res = service.register(3L,
        new ExamRequestCreateRequest("신사중", "중3", "2학기 중간", "Unit 1~3", "010-1111-2222", null));
    assertThat(res.userId()).isEqualTo(3L);
    assertThat(res.status()).isEqualTo(ExamRequestStatus.RECEIVED);
    verify(repository).save(any(ExamRequest.class));
  }

  @Test
  @DisplayName("given 필수값 누락 when register then 400으로 거부하고 저장하지 않는다")
  void rejectsInvalidForm() {
    assertThatThrownBy(() -> service.register(3L,
        new ExamRequestCreateRequest("", "중3", "중간", "범위", "연락처", null)))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  @DisplayName("given 요청 본문 없음 when register then 400")
  void rejectsNullBody() {
    assertThatThrownBy(() -> service.register(3L, null))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  @DisplayName("given 내 의뢰 when listMine then 내 userId로 조회한다")
  void listsMine() {
    when(repository.findTop50ByUserIdOrderByCreatedAtDesc(3L)).thenReturn(
        List.of(ExamRequest.create(3L, "학교", "중3", "중간", "범위", "연락처", null)));
    assertThat(service.listMine(3L)).hasSize(1);
  }

  @Test
  @DisplayName("given 없는 id when updateStatus then 404")
  void updateStatusNotFound() {
    when(repository.findById(9L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.updateStatus(9L, ExamRequestStatus.DONE))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  @DisplayName("given 접수 건 when updateStatus then 상태를 바꾼다")
  void updatesStatus() {
    ExamRequest r = ExamRequest.create(3L, "학교", "중3", "중간", "범위", "연락처", null);
    when(repository.findById(1L)).thenReturn(Optional.of(r));
    assertThat(service.updateStatus(1L, ExamRequestStatus.DONE).status()).isEqualTo(ExamRequestStatus.DONE);
  }
}
