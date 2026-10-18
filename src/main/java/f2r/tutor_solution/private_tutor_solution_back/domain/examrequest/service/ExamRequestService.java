package f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.dto.ExamRequestCreateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.dto.ExamRequestResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.entity.ExamRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.entity.ExamRequestStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.repository.ExamRequestRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ExamRequestService {

  private final ExamRequestRepository repository;

  @Transactional
  public ExamRequestResponse register(Long userId, ExamRequestCreateRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 내용이 없습니다.");
    }
    try {
      ExamRequest saved = repository.save(ExamRequest.create(
          userId, request.schoolName(), request.grade(), request.examRound(),
          request.examScope(), request.contact(), request.memo()));
      return ExamRequestResponse.from(saved);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @Transactional(readOnly = true)
  public List<ExamRequestResponse> listMine(Long userId) {
    return repository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(ExamRequestResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<ExamRequestResponse> listAll() {
    return repository.findTop200ByOrderByCreatedAtDesc().stream()
        .map(ExamRequestResponse::from).toList();
  }

  @Transactional
  public ExamRequestResponse updateStatus(Long id, ExamRequestStatus status) {
    ExamRequest r = repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "의뢰를 찾을 수 없습니다."));
    try {
      r.changeStatus(status);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
    return ExamRequestResponse.from(r);
  }
}
