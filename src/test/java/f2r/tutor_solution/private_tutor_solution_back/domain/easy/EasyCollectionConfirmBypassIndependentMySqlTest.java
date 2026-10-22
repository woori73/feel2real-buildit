package f2r.tutor_solution.private_tutor_solution_back.domain.easy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionIngestService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyImportService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorImportService;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * [검수 재현 테스트 — 수용 기준] 승인 전 금융 수집 배치는 일반 확정 API로 장부에 반영되면 안 된다.
 *
 * <p>현재(#853 head 60479f1) 구현에서는 이 테스트가 <b>실패</b>한다: 공용 {@code EasyImportService.confirm}이
 * 배치 출처·승인 상태를 보지 않아, 수집 승인(EasyCollectionApproval) 없이도 거래가 장부에 생긴다.
 * 수정 후에는 두 테스트가 통과해야 하며, 승인 경로({@code approveAndCommit})는 기존대로 동작해야 한다.
 */
@SpringBootTest
@Transactional
// Imported from Claude review branch cf3c12a5 / handoff commit 5bc0a3a.
// Only class name differs to coexist with Codex's broader boundary regression.
class EasyCollectionConfirmBypassIndependentMySqlTest {
  @Autowired EasyCollectionIngestService ingest;
  @Autowired EasyImportService importService;
  @Autowired EasyTutorImportService tutorImportService;
  @Autowired EasyBusinessRepository businesses;
  @Autowired EasyTransactionRepository transactions;
  @MockitoBean EasyTutorAccessService access;

  private EasyBusiness business() {
    return businesses.findAll().stream().filter(EasyBusiness::isActive).findFirst().orElseThrow();
  }

  private long ledgerCount(Long businessId) {
    return transactions.findAll().stream()
        .filter(t -> t.getBusiness().getBusinessId().equals(businessId))
        .count();
  }

  /** 승인 요청을 만들지 않고 수집 초안(FINANCIAL_FILE)만 stage한 배치 ID. */
  private Long stagedCollectionBatch(EasyBusiness business) {
    var row =
        new CollectionParsedRow(
            1, Map.of("note", "합성 검수 데이터"), "{\"note\":\"synthetic\"}", LocalDate.now(),
            "EXPENSE", 12_345L, "검수 합성 거래처", "승인 우회 재현", "TRANSFER", null, null,
            "NONE", "검수-합성-계좌", "ext-" + UUID.randomUUID(), null);
    var response =
        ingest.ingestFinancialDraft(
            business.getBusinessId(), UUID.randomUUID().toString(), "FIN_" + "a".repeat(24), java.util.List.of(row));
    Long batchId = response.run().importBatchId();
    assertThat(batchId).as("수집 초안은 import 배치에 연결된다").isNotNull();
    return batchId;
  }

  @Test
  void givenCollectionBatchWithoutApproval_whenConfirmedThroughPublicService_thenRejectedAndLedgerUnchanged() {
    // given
    EasyBusiness business = business();
    Long batchId = stagedCollectionBatch(business);
    long before = ledgerCount(business.getBusinessId());
    // when / then
    assertThatThrownBy(() -> importService.confirm(batchId, null))
        .as("승인 없이 수집 배치를 확정하면 거부되어야 한다")
        .isInstanceOf(RuntimeException.class);
    assertThat(ledgerCount(business.getBusinessId())).isEqualTo(before);
  }

  @Test
  void givenCollectionBatchWithoutApproval_whenConfirmedThroughTutorFacade_thenRejectedAndLedgerUnchanged() {
    // given
    EasyBusiness business = business();
    when(access.requireAllowedBusiness(any(), eq(business.getBusinessId()))).thenReturn(business);
    Long batchId = stagedCollectionBatch(business);
    long before = ledgerCount(business.getBusinessId());
    var tutor = new JwtTokenProvider.AuthenticatedPrincipal(10L, "owner-a", "TUTOR");
    // when / then
    assertThatThrownBy(() -> tutorImportService.confirm(tutor, business.getBusinessId(), batchId, null))
        .isInstanceOf(RuntimeException.class);
    assertThat(ledgerCount(business.getBusinessId())).isEqualTo(before);
  }
}
