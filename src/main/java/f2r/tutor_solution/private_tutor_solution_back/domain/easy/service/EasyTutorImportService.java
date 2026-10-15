package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest.EasyImportRowInput;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRowStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportBatchRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportRowRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tutor-facing import facade. Every batch/row operation is scoped through the server-side link. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyTutorImportService {

  private final EasyTutorAccessService accessService;
  private final EasyCsvFileAdapter csvFileAdapter;
  private final EasyImportService importService;
  private final EasyImportBatchRepository batchRepository;
  private final EasyImportRowRepository rowRepository;
  private final EasyAccountRepository accountRepository;

  public List<EasyImportBatchResponse> list(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long businessId) {
    EasyBusiness business = accessService.requireAllowedBusiness(principal, businessId);
    return importService.listBatches(business.getBusinessId());
  }

  public EasyImportBatchResponse detail(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long businessId, Long batchId) {
    requireOwnedBatch(principal, businessId, batchId);
    return importService.getBatch(batchId);
  }

  /** Parse the uploaded bytes, preserve every original row as raw JSON, then run existing rules. */
  @Transactional
  public EasyImportBatchResponse uploadCsv(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long businessId,
      String filename,
      InputStream input) throws IOException {
    EasyBusiness business = accessService.requireAllowedBusiness(principal, businessId);
    String safeName = safeFilename(filename);
    List<CollectionParsedRow> parsed = csvFileAdapter.parse(input, safeName);
    List<EasyImportRowInput> rows = parsed.stream().map(row -> new EasyImportRowInput(
        row.occurredOn() == null ? null : row.occurredOn().toString(),
        row.direction(), row.amount(), row.counterparty(), row.description(), row.payMethod(),
        row.supplyAmount(), row.vatAmount(), row.evidenceType(),
        row.rawJson() + " [srow:" + row.rowNo() + "]", row.externalAccount(), null)).toList();
    return importService.stage(new EasyImportBatchRequest(
        business.getBusinessId(), safeName, "CSV", rows));
  }

  /** User correction of a recommendation. Imported rows are immutable through this workflow. */
  @Transactional
  public EasyImportBatchResponse selectAccount(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long businessId,
      Long batchId,
      Long rowId,
      Long accountId) {
    EasyImportBatch batch = requireOwnedBatch(principal, businessId, batchId);
    EasyImportRow row = rowRepository.findByBatch_BatchIdAndRowIdIn(batchId, List.of(rowId))
        .stream().findFirst()
        .orElseThrow(() -> new IllegalArgumentException("행을 찾을 수 없습니다."));
    if (row.getStatus() != EasyImportRowStatus.READY || row.getCreatedTxn() != null) {
      throw new IllegalArgumentException("확정 전 READY 행만 수정할 수 있습니다.");
    }
    EasyAccount account = accountRepository.findById(accountId)
        .filter(EasyAccount::isActive)
        .orElseThrow(() -> new IllegalArgumentException("활성 계정을 찾을 수 없습니다."));
    row.markReady(account, 1.0);
    return EasyImportBatchResponse.of(batch, rows(batchId));
  }

  @Transactional
  public EasyImportBatchResponse reclassify(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long businessId,
      Long batchId, List<Long> rowIds) {
    requireOwnedBatch(principal, businessId, batchId);
    return importService.reclassify(batchId, rowIds);
  }

  @Transactional
  public EasyImportBatchResponse confirm(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long businessId,
      Long batchId, List<Long> rowIds) {
    requireOwnedBatch(principal, businessId, batchId);
    return importService.confirm(batchId, rowIds);
  }

  @Transactional
  public void discard(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long businessId, Long batchId) {
    requireOwnedBatch(principal, businessId, batchId);
    importService.discard(batchId);
  }

  private EasyImportBatch requireOwnedBatch(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long businessId, Long batchId) {
    EasyBusiness business = accessService.requireAllowedBusiness(principal, businessId);
    return batchRepository.findByBatchIdAndBusiness_BusinessId(batchId, business.getBusinessId())
        .orElseThrow(() -> new IllegalArgumentException("사업체에 속한 배치를 찾을 수 없습니다."));
  }

  private List<f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportRowResponse>
      rows(Long batchId) {
    return rowRepository.findByBatch_BatchIdOrderByRowNoAsc(batchId).stream()
        .map(f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportRowResponse::from)
        .toList();
  }

  private String safeFilename(String filename) {
    String name = filename == null || filename.isBlank() ? "upload.csv" : filename;
    name = Path.of(name).getFileName().toString();
    if (!name.toLowerCase().endsWith(".csv")) {
      throw new IllegalArgumentException("CSV 파일만 업로드할 수 있습니다.");
    }
    return name.length() > 200 ? name.substring(name.length() - 200) : name;
  }
}
