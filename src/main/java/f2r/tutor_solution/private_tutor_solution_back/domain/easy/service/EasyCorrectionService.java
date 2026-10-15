package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCorrectionLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCorrectionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy 수정 감사 — append-only 기록. 향후 AI 수정
 * (요청→확인→수정→재분개) 추적 기반이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyCorrectionService {

  private final EasyCorrectionLogRepository logRepository;

  @Transactional
  public void record(EasyBusiness business, EasyTransaction txn, String action,
      String beforeJson, String afterJson, String actor) {
    logRepository.save(EasyCorrectionLog.builder()
        .business(business)
        .txn(txn)
        .action(action)
        .beforeJson(beforeJson)
        .afterJson(afterJson)
        .requestedBy(actor)
        .confirmedBy(actor)
        .build());
  }
}
