package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCorrectionLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAnnualClosingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCorrectionLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalEntryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalLineRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 운영 DELETE /api/admin/easy/transactions/1 HTTP 500 회귀 테스트.
 *
 * <p>ROOT: {@code org.springframework.dao.InvalidDataAccessApiUsageException} ←
 * {@code org.hibernate.TransientPropertyValueException}. {@code EasyLedgerService.deleteTxn}이
 * 삭제 대상 txn을 참조하는 {@code EasyCorrectionLog}를 같은 persistence context에서 저장한 뒤
 * {@code txnRepository.delete(txn)}을 수행하면, flush 시점에 REMOVED transaction을 참조하는
 * transient log 때문에 실패한다 (DB FK 문제가 아님).
 *
 * <p>운영 조건 그대로: accountId=null, journal 없음인 거래 1건 삭제.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:easydeletetxn;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DisplayName("EasyLedgerService.deleteTxn 삭제 500 회귀 테스트")
class EasyLedgerDeleteTxnRegressionTest {

  @Autowired EasyBusinessRepository businessRepository;
  @Autowired EasyTransactionRepository txnRepository;
  @Autowired EasyAccountRepository accountRepository;
  @Autowired EasyTuitionPaymentRepository paymentRepository;
  @Autowired EasyStudentRepository studentRepository;
  @Autowired EasyJournalEntryRepository entryRepository;
  @Autowired EasyJournalLineRepository lineRepository;
  @Autowired EasyCorrectionLogRepository correctionLogRepository;
  @Autowired EasyAnnualClosingRepository closingRepository;
  @Autowired EntityManager entityManager;

  private EasyLedgerService ledgerService;

  @BeforeEach
  void setUp() {
    EasyCorrectionService correctionService =
        new EasyCorrectionService(correctionLogRepository);
    EasyJournalService journalService =
        new EasyJournalService(entryRepository, lineRepository, accountRepository,
            closingRepository);
    ledgerService = new EasyLedgerService(
        txnRepository, businessRepository, accountRepository, paymentRepository,
        studentRepository, journalService, correctionService);
  }

  @Test
  @DisplayName("계정 미지정·분개 없는 거래 삭제 — 500 없이 삭제되고 삭제 감사 스냅샷이 남는다")
  void deleteTxn_withoutAccountOrJournal_keepsAuditSnapshot() {
    EasyBusiness academy = businessRepository.saveAndFlush(EasyBusiness.builder()
        .code("ACADEMY").name("학원").active(true).sortOrder(1).build());
    // 운영 txnId=1 조건: 2026-01-03, 540,000원, accountId=null, journal 없음.
    EasyTransaction txn = txnRepository.saveAndFlush(EasyTransaction.builder()
        .business(academy).occurredOn(LocalDate.of(2026, 1, 3))
        .direction(EasyTxnDirection.INCOME).amount(540000L)
        .category("수강료").build());
    Long txnId = txn.getTxnId();

    // 운영 장애 지점: 같은 persistence context에서 correction log 저장 + txn 삭제 후 flush.
    ledgerService.deleteTxn(txnId);
    entityManager.flush();

    assertThat(txnRepository.findById(txnId)).isEmpty();
    List<EasyCorrectionLog> logs = correctionLogRepository.findAllByOrderByLogIdDesc();
    assertThat(logs).hasSize(1);
    EasyCorrectionLog log = logs.get(0);
    assertThat(log.getAction()).isEqualTo("DELETE");
    // 삭제된 transaction을 참조하면 안 된다 — 참조가 남으면 flush 시점에
    // TransientPropertyValueException(운영 DELETE 500)이 발생한다.
    assertThat(log.getTxn()).isNull();
    // 감사 추적: 삭제 거래의 주요 정보는 snapshot에 보존된다.
    assertThat(log.getBeforeJson()).contains("\"txnId\":" + txnId);
    assertThat(log.getBeforeJson()).contains("540000");
    assertThat(log.getAfterJson()).isEqualTo("{}");
    // 사업체 스코프 조회에서 삭제 로그가 보인다.
    assertThat(correctionLogRepository
        .findByBusiness_BusinessIdOrderByLogIdDesc(academy.getBusinessId())).hasSize(1);
  }
}
