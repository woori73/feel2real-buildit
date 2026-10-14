package f2r.tutor_solution.private_tutor_solution_back.domain.easy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRowStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportRowRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorImportService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EasyTutorCsvWorkflowMySqlTest {

  @Autowired EasyTutorImportService tutorImportService;
  @Autowired EasyBusinessRepository businessRepository;
  @Autowired EasyAccountRepository accountRepository;
  @Autowired EasyImportRowRepository rowRepository;
  @Autowired EasyLedgerInquiryService inquiryService;
  @MockitoBean EasyTutorAccessService accessService;

  @Test
  void csvUploadRuleRecommendationCorrectionConfirmAndLedgerAreOneIsolatedFlow()
      throws Exception {
    EasyBusiness business = businessRepository.findAll().stream()
        .filter(EasyBusiness::isActive).findFirst().orElseThrow();
    EasyAccount expense = accountRepository
        .findAllByActiveTrueAndDirectionOrderBySortOrderAsc(EasyAccountDirection.EXPENSE)
        .stream().findFirst().orElseThrow();
    JwtTokenProvider.AuthenticatedPrincipal principal =
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "owner-a", "TUTOR");
    when(accessService.requireAllowedBusiness(any(), eq(business.getBusinessId())))
        .thenReturn(business);

    String csv = "거래일,구분,금액,거래처,적요,결제수단,원본계정\n"
        + "2099-10-14,지출,350000,KT,전화요금,계좌이체,전화/휴대폰/인터넷\n";
    EasyImportBatchResponse staged = tutorImportService.uploadCsv(principal,
        business.getBusinessId(), "academy-ledger.csv",
        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    assertThat(staged.rows()).hasSize(1);
    assertThat(staged.rows().getFirst().status()).isEqualTo("READY");
    assertThat(staged.rows().getFirst().confidence()).isNotNull();
    assertThat(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(staged.batchId()).getFirst()
        .getRawData()).contains("KT", "전화요금", "350000", "[srow:1]");

    EasyImportBatchResponse corrected = tutorImportService.selectAccount(principal,
        business.getBusinessId(), staged.batchId(), staged.rows().getFirst().rowId(),
        expense.getAccountId());
    assertThat(corrected.rows().getFirst().matchedAccountId()).isEqualTo(expense.getAccountId());

    EasyImportBatchResponse confirmed = tutorImportService.confirm(principal,
        business.getBusinessId(), staged.batchId(), null);
    assertThat(confirmed.confirmedRows()).isEqualTo(1);
    assertThat(confirmed.rows().getFirst().createdTxnId()).isNotNull();

    EasyLedgerInquiryService.TrialBalanceReport trial = inquiryService.trialBalance(
        business.getBusinessId(), LocalDate.of(2099, 10, 1), LocalDate.of(2099, 10, 31));
    assertThat(trial.balanced()).isTrue();
    assertThat(trial.totalDebit()).isEqualTo(350000L);
    assertThat(trial.totalCredit()).isEqualTo(350000L);
    assertThat(inquiryService.generalLedger(business.getBusinessId(), expense.getAccountId(),
        LocalDate.of(2099, 10, 1), LocalDate.of(2099, 10, 31)).rows()).hasSize(1);

    EasyImportBatchResponse repeated = tutorImportService.uploadCsv(principal,
        business.getBusinessId(), "academy-ledger-copy.csv",
        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    tutorImportService.selectAccount(principal, business.getBusinessId(), repeated.batchId(),
        repeated.rows().getFirst().rowId(), expense.getAccountId());
    EasyImportBatchResponse duplicate = tutorImportService.confirm(principal,
        business.getBusinessId(), repeated.batchId(), null);
    assertThat(duplicate.rows().getFirst().status())
        .isEqualTo(EasyImportRowStatus.DUPLICATE.name());
    assertThat(duplicate.confirmedRows()).isZero();
  }
}
