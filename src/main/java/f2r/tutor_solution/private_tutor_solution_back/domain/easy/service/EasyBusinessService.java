package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyBusinessResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy 사업체 조회 — 이름 하드코딩 없이 code/name 목록을 내려준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyBusinessService {

  private final EasyBusinessRepository businessRepository;

  public List<EasyBusinessResponse> listActive() {
    return businessRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
        .map(EasyBusinessResponse::from)
        .toList();
  }
}
