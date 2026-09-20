package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionIngestResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionRunResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionSourceResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest.EasyImportRowInput;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportRowResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRawRecord;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRawStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRunStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionConnectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRawRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PHASE 1 수집 파이프라인 오케스트레이션 — 파일/어댑터 → run → parse → 3단계 dedupe →
 * 기존 EasyImportService.stage() handoff → raw↔import row 링크.
 * 절대 easy_transactions를 직접 INSERT하지 않는다; 장부 확정(confirm)은 기존 경계의 관리자 권한이다.
 * 자동 collect ≠ 자동 confirm.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyCollectionIngestService {

  public static final String SOURCE_CODE = "EXCEL_FILE";
  public static final String IMPORT_SOURCE_KIND = "FILE_CSV";
  private static final int MAX_ACCEPTED_ROWS = 2000;

  private final EasyBusinessRepository businessRepository;
  private final EasyCollectionSourceRepository sourceRepository;
  private final EasyCollectionConnectionRepository connectionRepository;
  private final EasyCollectionRunRepository runRepository;
  private final EasyCollectionRawRecordRepository rawRepository;
  private final EasyTransactionRepository txnRepository;
  private final EasyCsvFileAdapter csvFileAdapter;
  private final EasyCardsalesAdapter cardsalesAdapter;
  private final EasyImportService importService;

  /** CSV 파일을 수집해 기존 import staging에 인계한다. 같은 파일 재업로드해도 중복 행이 생기지 않는다. */
  @Transactional
  public EasyCollectionIngestResponse ingestCsv(Long businessId, String label, String filename,
      byte[] content) {
    EasyBusiness business = requireActiveBusiness(businessId);
    EasyCollectionSource source = sourceRepository.findByCode(SOURCE_CODE)
        .orElseThrow(() -> new IllegalArgumentException("수집원을 찾을 수 없습니다: " + SOURCE_CODE));
    EasyCollectionConnection connection = findOrCreateConnection(business, source, label);
    String safeName = normalizeFilename(filename);
    EasyCollectionRun run = runRepository.save(EasyCollectionRun.builder()
        .connection(connection)
        .business(business)
        .triggerType("MANUAL")
        .sourceFilename(safeName)
        .adapterCode(EasyCsvFileAdapter.ADAPTER_CODE)
        .adapterVersion(EasyCsvFileAdapter.ADAPTER_VERSION)
        .build());

    List<CollectionParsedRow> parsed;
    try {
      parsed = csvFileAdapter.parse(content, safeName);
    } catch (IllegalArgumentException e) {
      run.fail(e.getMessage());
      return new EasyCollectionIngestResponse(EasyCollectionRunResponse.from(run),
          List.of(e.getMessage()));
    }
    return ingestParsedRows(run, connection, business, SOURCE_CODE, IMPORT_SOURCE_KIND, safeName,
        parsed);
  }

  /**
   * 어댑터 기반 수집 — Cardsales 등 외부 API 연동 어댑터를 통해 수집한다.
   * connectionRef는 {@link EasyCollectionConnection#connectionRef}에 저장된 opaque reference다.
   * since로 증분 수집 가능(초기 전체 수집 시 null).
   */
  @Transactional
  public EasyCollectionIngestResponse ingestByAdapter(Long businessId, String label,
      String adapterCode, LocalDateTime since, int limit) {
    EasyBusiness business = requireActiveBusiness(businessId);
    EasyCollectionAdapter adapter = resolveAdapter(adapterCode);
    EasyCollectionSource source = sourceRepository.findByCode(adapterCode)
        .orElseThrow(() -> new IllegalArgumentException("수집원을 찾을 수 없습니다: " + adapterCode));
    EasyCollectionConnection connection = findOrCreateConnection(business, source, label);
    String connectionRef = connection.getConnectionRef();
    if (connectionRef == null || connectionRef.isBlank()) {
      throw new IllegalStateException("연결 참조(connectionRef)가 설정되지 않았습니다. 관리자 콘솔에서 등록하세요.");
    }
    adapter.validateConnection(connectionRef);

    EasyCollectionRun run = runRepository.save(EasyCollectionRun.builder()
        .connection(connection)
        .business(business)
        .triggerType("SCHEDULED")
        .sourceFilename(adapterCode + "-" + LocalDateTime.now().toLocalDate())
        .adapterCode(adapter.adapterCode())
        .adapterVersion(adapter.adapterVersion())
        .build());

    List<CollectionParsedRow> parsed;
    try {
      parsed = adapter.fetch(connectionRef, since, limit);
    } catch (IllegalArgumentException | IllegalStateException e) {
      run.fail(e.getMessage());
      return new EasyCollectionIngestResponse(EasyCollectionRunResponse.from(run),
          List.of(e.getMessage()));
    }
    return ingestParsedRows(run, connection, business, adapterCode, adapterCode, adapterCode,
        parsed);
  }

  private EasyCollectionAdapter resolveAdapter(String adapterCode) {
    return switch (adapterCode) {
      case EasyCardsalesAdapter.ADAPTER_CODE -> cardsalesAdapter;
      default -> throw new IllegalArgumentException("지원하지 않는 어댑터: " + adapterCode);
    };
  }

  /**
   * 어댑터 공통 수집 코어 — parse된 행만 들어오면 소스와 무관하게 동일하게
   * 3단계 dedupe → raw 보존 → fingerprint 제외 → stage 인계 → run 완수를 수행한다.
   * 이후 어댑터(cardsales 등)는 이 메서드 reuse가 확장점이다(direct transaction write 없음).
   */
  private EasyCollectionIngestResponse ingestParsedRows(EasyCollectionRun run,
      EasyCollectionConnection connection, EasyBusiness business, String sourceCode,
      String importSourceKind, String displayName, List<CollectionParsedRow> parsed) {
    Long businessId = business.getBusinessId();
    Set<String> ledgerFingerprints = txnRepository
        .findByBusiness_BusinessIdAndFingerprintIsNotNull(businessId).stream()
        .map(EasyTransaction::getFingerprint)
        .collect(Collectors.toSet());

    Map<String, Integer> seqByTuple = new HashMap<>();
    Set<String> seenKeys = new HashSet<>();
    List<EasyCollectionRawRecord> accepted = new ArrayList<>();
    List<CollectionParsedRow> acceptedRows = new ArrayList<>();
    List<String> rowErrors = new ArrayList<>();
    int duplicate = 0;
    int error = 0;

    for (CollectionParsedRow row : parsed) {
      String tupleHash = EasyCollectionDedupeKeys.tupleHash(sourceCode, businessId,
          row.occurredOn(), row.direction(), row.amount(), row.counterparty(),
          row.description(), row.payMethod());
      int seq = seqByTuple.merge(tupleHash, 1, Integer::sum);
      Long connectionId = connection.getConnectionId();
      String dedupeKey = row.externalId() == null
          ? EasyCollectionDedupeKeys.tuple(sourceCode, businessId, tupleHash, seq)
          : EasyCollectionDedupeKeys.external(sourceCode, businessId, connectionId, row.externalId());

      if (!seenKeys.add(dedupeKey) || rawRepository.findByDedupeKey(dedupeKey).isPresent()) {
        duplicate++;
        continue;
      }
      EasyCollectionRawRecord raw = EasyCollectionRawRecord.builder()
          .run(run)
          .connection(connection)
          .business(business)
          .sourceCode(sourceCode)
          .externalRecordId(row.externalId())
          .tupleHash(tupleHash)
          .dedupeKey(dedupeKey)
          .rawPayload(row.rawJson())
          .rawHash(EasyCollectionDedupeKeys.rawHash(row.rawJson()))
          .sourceOccurredOn(row.occurredOn())
          .status(row.isValid() ? EasyCollectionRawStatus.STORED : EasyCollectionRawStatus.ERROR)
          .normalizeError(row.isValid() ? null : row.error())
          .build();
      raw = rawRepository.save(raw);
      if (!row.isValid()) {
        error++;
        rowErrors.add("행 " + row.rowNo() + ": " + row.error());
        continue;
      }
      String fingerprint = EasyFingerprint.of(businessId, row.occurredOn(), row.direction(),
          row.amount(), row.counterparty(), row.description(), row.payMethod());
      if (ledgerFingerprints.contains(fingerprint)) {
        raw.markLedgerDuplicate();
        duplicate++;
        continue;
      }
      accepted.add(raw);
      acceptedRows.add(row);
    }

    Long batchId = null;
    if (!accepted.isEmpty()) {
      if (accepted.size() > MAX_ACCEPTED_ROWS) {
        throw new IllegalArgumentException(
            "수집 1회당 import 인계는 " + MAX_ACCEPTED_ROWS + "행까지입니다. 파일분할 후 다시 업로드하세요.");
      }
      batchId = handoffToImport(run, businessId, displayName, importSourceKind, accepted,
          acceptedRows);
    }
    run.finish(parsed.size(), accepted.size(), duplicate, error, batchId, summarize(rowErrors));
    if (run.getStatus() != EasyCollectionRunStatus.FAILED) {
      connection.markSuccess(LocalDateTime.now());
    }
    return new EasyCollectionIngestResponse(EasyCollectionRunResponse.from(run), rowErrors);
  }

  public List<EasyCollectionRunResponse> listRuns(Long businessId) {
    if (businessId == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    return runRepository.findAllByBusiness_BusinessIdOrderByRunIdDesc(businessId).stream()
        .map(EasyCollectionRunResponse::from)
        .toList();
  }

  public EasyCollectionRunResponse getRun(Long runId) {
    return EasyCollectionRunResponse.from(runRepository.findById(runId)
        .orElseThrow(() -> new IllegalArgumentException("수집 실행을 찾을 수 없습니다: " + runId)));
  }

  public List<EasyCollectionSourceResponse> listSources() {
    return EasyCollectionSourceResponse.listOf(sourceRepository.findAll());
  }

  /**
   * 기존 import 경계 인계 — rowNo는 accepted 행 순서와 1:1이라 응답 rowNo로 raw 링크를 되매핑한다.
   * rawData 끝부분에 dedupeKey를 박아 원문 raw ↔ import row ↔ (나중의) transaction 역추적 체인을 만든다.
   */
  private Long handoffToImport(EasyCollectionRun run, Long businessId, String displayName,
      String importSourceKind, List<EasyCollectionRawRecord> accepted, List<CollectionParsedRow> rows) {
    List<EasyImportRowInput> inputs = new ArrayList<>();
    Map<Integer, EasyCollectionRawRecord> rawByRowNo = new HashMap<>();
    for (int i = 0; i < accepted.size(); i++) {
      EasyCollectionRawRecord raw = accepted.get(i);
      CollectionParsedRow row = rows.get(i);
      int rowNo = i + 1;
      rawByRowNo.put(rowNo, raw);
      inputs.add(new EasyImportRowInput(
          row.occurredOn().toString(),
          row.direction(),
          row.amount(),
          row.counterparty(),
          row.description(),
          row.payMethod(),
          row.supplyAmount(),
          row.vatAmount(),
          row.evidenceType(),
          raw.getRawPayload() + " [ext:" + raw.getDedupeKey() + "]",
          row.externalAccount()));
    }
    EasyImportBatchResponse batch = importService.stage(new EasyImportBatchRequest(
        businessId,
        displayName + "#run:" + run.getRunId(),
        importSourceKind,
        inputs));
    for (EasyImportRowResponse staged : batch.rows()) {
      EasyCollectionRawRecord raw = rawByRowNo.get(staged.rowNo());
      if (raw != null) {
        raw.linkImport(batch.batchId(), staged.rowId());
      }
    }
    return batch.batchId();
  }

  private EasyCollectionConnection findOrCreateConnection(EasyBusiness business,
      EasyCollectionSource source, String label) {
    String normalizedLabel = label == null || label.isBlank() ? "default" : label.trim();
    return connectionRepository.findByBusiness_BusinessIdAndSource_SourceIdAndLabel(
            business.getBusinessId(), source.getSourceId(), normalizedLabel)
        .orElseGet(() -> connectionRepository.save(EasyCollectionConnection.builder()
            .business(business)
            .source(source)
            .label(normalizedLabel)
            .build()));
  }

  private static String normalizeFilename(String filename) {
    String name = filename == null || filename.isBlank() ? "uploaded.csv"
        : filename.replace("\\", "/");
    name = name.substring(name.lastIndexOf('/') + 1);
    return name.length() <= 200 ? name : name.substring(name.length() - 200);
  }

  private static String summarize(List<String> rowErrors) {
    if (rowErrors.isEmpty()) {
      return null;
    }
    StringBuilder summary = new StringBuilder();
    for (String e : rowErrors) {
      if (summary.length() > 0) {
        summary.append(" / ");
      }
      summary.append(e);
      if (summary.length() > 400) {
        summary.append(" ...");
        break;
      }
    }
    return summary.toString();
  }

  private EasyBusiness requireActiveBusiness(Long businessId) {
    if (businessId == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    EasyBusiness business = businessRepository.findById(businessId)
        .orElseThrow(() -> new IllegalArgumentException("사업체를 찾을 수 없습니다: " + businessId));
    if (!business.isActive()) {
      throw new IllegalArgumentException("비활성 사업체에는 수집할 수 없습니다.");
    }
    return business;
  }
}
