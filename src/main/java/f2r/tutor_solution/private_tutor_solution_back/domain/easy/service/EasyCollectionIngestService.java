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
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionTriggerType;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionConnectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRawRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 수집 파이프라인 오케스트레이션 — 운영 원칙(EASY_LEDGER_OPERATING_PRINCIPLES) 순서 그대로:
 * 수집 → 원본 보존(raw) → 최소 중복 방지(dedupe_key·기존 장부 fingerprint) → 중앙 장부 저장 →
 * 자동분류(원본 계정 매핑 → 과거 장부 → 명시 규칙) → 예외 검토(REVIEW).
 *
 * <p>장부 저장은 사람 승인을 기다리지 않는다. 미분류 거래도 REVIEW_REQUIRED로 저장한다.
 * {@code easy_transactions}에는 직접 쓰지 않고 기존 import 경계({@code stage} → {@link
 * EasyImportService#saveCollectedBatch})를 그대로 통과한다. 날짜·금액을 해석하지 못한 자료는 raw ERROR로
 * 원문과 사유를 보존하고 장부에 임의 값으로 저장하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyCollectionIngestService {

  public static final String SOURCE_CODE = "EXCEL_FILE";
  public static final String IMPORT_SOURCE_KIND = "FILE_CSV";
  private static final int MAX_ACCEPTED_ROWS = 2000;
  private static final java.time.ZoneId KST = java.time.ZoneId.of("Asia/Seoul");

  private final EasyBusinessRepository businessRepository;
  private final EasyCollectionSourceRepository sourceRepository;
  private final EasyCollectionConnectionRepository connectionRepository;
  private final EasyCollectionRunRepository runRepository;
  private final EasyCollectionRawRecordRepository rawRepository;
  private final EasyTransactionRepository txnRepository;
  private final EasyCsvFileAdapter csvFileAdapter;
  private final EasyCardsalesAdapter cardsalesAdapter;
  private final CrefiaCardsalesAdapter crefiaCardsalesAdapter;
  private final EasyImportService importService;

  /**
   * Programmatic transactions make the fetch boundary explicit and avoid self-invocation.
   * Direct unit constructions may omit it; Spring always injects the application manager.
   */
  @Autowired
  private PlatformTransactionManager transactionManager;

  @Autowired
  private EasyCollectionRunExecutionGate executionGate;

  @Autowired
  private EasyCollectionExecutionRuntime executionRuntime;

  /** 기간 단위 기관 어댑터(은행 등). 없으면 비어 있다. */
  @Autowired(required = false)
  private List<EasyPeriodCollectionAdapter> periodAdapters = List.of();

  /** 기간 어댑터의 "장부 저장 완료일" 커서. */
  @Autowired(required = false)
  private f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionCheckpointRepository
      checkpointRepository;

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
   * Provider orchestration. The provider call is always executed with Spring transaction
   * propagation suspended. Only run creation, completion, and failure recording use short
   * transactions.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public EasyCollectionIngestResponse ingestByAdapter(Long businessId, String label,
      String adapterCode, EasyCollectionTriggerType triggerType, LocalDateTime since, int limit) {
    EasyCollectionAdapter adapter = resolveAdapter(adapterCode);
    EasyCollectionTriggerType actualTrigger = triggerType == null
        ? EasyCollectionTriggerType.MANUAL : triggerType;
    AdapterFetchContext context = inTransaction(
        () -> startAdapterRun(businessId, label, adapter, actualTrigger));
    if (adapter instanceof EasyPeriodCollectionAdapter periodAdapter) {
      return ingestByPeriod(context, periodAdapter, limit);
    }

    try {
      List<CollectionParsedRow> parsed;
      if (executionRuntime == null) {
        adapter.validateConnection(context.connectionRef());
        parsed = adapter.fetch(context.connectionRef(), since, limit);
      } else {
        parsed = executionRuntime.execute(() -> {
          adapter.validateConnection(context.connectionRef());
          return adapter.fetch(context.connectionRef(), since, limit);
        }, () -> inTransaction(() -> {
          executionGate.heartbeat(context.runId(), context.connectionId(), context.leaseToken());
          return Boolean.TRUE;
        }));
      }
      return inTransaction(() -> completeAdapterRun(context, parsed));
    } catch (EasyCollectionException e) {
      return inTransaction(() -> failAdapterRun(context, e));
    } catch (RuntimeException e) {
      EasyCollectionException safe = new EasyCollectionException(
          EasyCollectionErrorCode.PROVIDER_TEMPORARY,
          "Provider collection failed temporarily.", e);
      return inTransaction(() -> failAdapterRun(context, safe));
    }
  }

  private AdapterFetchContext startAdapterRun(Long businessId, String label,
      EasyCollectionAdapter adapter, EasyCollectionTriggerType triggerType) {
    EasyBusiness business = requireActiveBusiness(businessId);
    EasyCollectionSource source = sourceRepository.findByCode(adapter.adapterCode())
        .orElseThrow(() -> new IllegalArgumentException(
            "수집원을 찾을 수 없습니다: " + adapter.adapterCode()));
    EasyCollectionConnection connection = findOrCreateConnection(business, source, label);
    if (executionGate != null) {
      EasyCollectionRunExecutionGate.Claim claim = executionGate.claim(
          connection.getConnectionId(), triggerType, adapter.adapterCode(),
          adapter.adapterVersion());
      return new AdapterFetchContext(claim.runId(), claim.businessId(), claim.connectionId(),
          claim.connectionRef(), claim.sourceCode(), claim.adapterCode(), claim.leaseToken(),
          connection.getProviderConfig());
    }

    // Direct-construction fallback retained for legacy unit tests. Spring production always
    // supplies the DB-backed execution gate above.
    String connectionRef = connection.getConnectionRef();
    if (connectionRef == null || connectionRef.isBlank()) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "Collection connection is not configured.");
    }
    EasyCollectionConnection lockedConnection =
        connectionRepository.findByIdWithLock(connection.getConnectionId())
            .orElseThrow(() -> new IllegalStateException(
                "연결을 찾을 수 없습니다: " + connection.getConnectionId()));
    if (runRepository.existsByConnection_ConnectionIdAndStatus(
        lockedConnection.getConnectionId(), EasyCollectionRunStatus.RUNNING)) {
      throw new IllegalStateException("이미 실행 중인 수집이 있습니다. 완료 후 다시 시도하세요.");
    }
    EasyCollectionRun run = runRepository.save(EasyCollectionRun.builder()
        .connection(lockedConnection)
        .business(business)
        .triggerType(triggerType.name())
        .sourceFilename(adapter.adapterCode() + "-" + LocalDateTime.now().toLocalDate())
        .adapterCode(adapter.adapterCode())
        .adapterVersion(adapter.adapterVersion())
        .build());
    return new AdapterFetchContext(run.getRunId(), businessId,
        lockedConnection.getConnectionId(), lockedConnection.getConnectionRef(),
        source.getCode(), adapter.adapterCode(), null, lockedConnection.getProviderConfig());
  }

  private EasyCollectionIngestResponse completeAdapterRun(AdapterFetchContext context,
      List<CollectionParsedRow> parsed) {
    if (executionGate != null) {
      executionGate.requireOwnedRunning(context.runId(), context.connectionId(), context.leaseToken());
    }
    EasyCollectionRun run = runRepository.findById(context.runId())
        .orElseThrow(() -> new IllegalStateException(
            "수집 실행을 찾을 수 없습니다: " + context.runId()));
    EasyCollectionConnection connection = connectionRepository.findById(context.connectionId())
        .orElseThrow(() -> new IllegalStateException(
            "연결을 찾을 수 없습니다: " + context.connectionId()));
    EasyBusiness business = requireActiveBusiness(context.businessId());
    return ingestParsedRows(run, connection, business, context.sourceCode(),
        context.adapterCode(), context.adapterCode(), parsed);
  }

  private EasyCollectionIngestResponse failAdapterRun(AdapterFetchContext context,
      EasyCollectionException failure) {
    if (executionGate != null) {
      try {
        executionGate.requireOwnedRunning(context.runId(), context.connectionId(), context.leaseToken());
      } catch (IllegalStateException ownershipLost) {
        EasyCollectionRun terminal = runRepository.findById(context.runId())
            .orElseThrow(() -> ownershipLost);
        return new EasyCollectionIngestResponse(EasyCollectionRunResponse.from(terminal),
            List.of("Collection execution ownership was lost."));
      }
    }
    EasyCollectionRun run = runRepository.findById(context.runId())
        .orElseThrow(() -> new IllegalStateException(
            "수집 실행을 찾을 수 없습니다: " + context.runId()));
    EasyCollectionConnection connection = connectionRepository.findById(context.connectionId())
        .orElseThrow(() -> new IllegalStateException(
            "연결을 찾을 수 없습니다: " + context.connectionId()));
    run.fail(failure.safeSummary());
    connection.recordFailure(failure.errorCode().name());
    if (failure.errorCode() == EasyCollectionErrorCode.AUTH_REQUIRED) {
      // 인증 만료는 재인증 필요로 표시하고 자동 재시도하지 않는다(스케줄러가 NEEDS_REAUTH를 건너뜀).
      connection.markNeedsReauth();
    }
    connectionRepository.save(connection);
    runRepository.save(run);
    return new EasyCollectionIngestResponse(EasyCollectionRunResponse.from(run),
        List.of(failure.safeSummary()));
  }

  private <T> T inTransaction(Supplier<T> action) {
    if (transactionManager == null) {
      return action.get();
    }
    return new TransactionTemplate(transactionManager).execute(status -> action.get());
  }

  private record AdapterFetchContext(
      Long runId,
      Long businessId,
      Long connectionId,
      String connectionRef,
      String sourceCode,
      String adapterCode,
      String leaseToken,
      String providerConfig
  ) {}

  private EasyCollectionAdapter resolveAdapter(String adapterCode) {
    if (periodAdapters != null) {
      for (EasyPeriodCollectionAdapter candidate : periodAdapters) {
        if (candidate.adapterCode().equals(adapterCode)) {
          return candidate;
        }
      }
    }
    return switch (adapterCode) {
      case EasyCardsalesAdapter.ADAPTER_CODE -> cardsalesAdapter;
      case CrefiaCardsalesAdapter.ADAPTER_CODE -> crefiaCardsalesAdapter;
      default -> throw new IllegalArgumentException("지원하지 않는 어댑터: " + adapterCode);
    };
  }

  /**
   * 기간 어댑터 수집 — [커서일, 오늘]을 기관 1회 최대 조회 기간으로 나눠 구간마다
   * (1) 트랜잭션 밖에서 기관 조회 → (2) 짧은 트랜잭션에서 원본 보존·중복 방지·장부 저장·커서 전진.
   * 저장이 실패한 구간은 롤백되고 커서도 그대로라 다음 실행이 같은 구간을 다시 처리한다(원본 키로 멱등).
   * 커서일 당일은 다시 조회한다 — 그날 늦게 들어온 거래를 놓치지 않기 위해서이며 이미 저장된 거래는 중복으로 걸러진다.
   */
  private EasyCollectionIngestResponse ingestByPeriod(AdapterFetchContext context,
      EasyPeriodCollectionAdapter adapter, int limit) {
    LocalDate today = LocalDate.now(KST);
    LocalDate start = inTransaction(() -> savedThrough(context.connectionId())
        .orElse(today.minusDays(Math.max(0, adapter.initialLookbackDays()))));
    java.util.ArrayDeque<LocalDate[]> windows = new java.util.ArrayDeque<>(
        EasyCollectionPeriods.split(start, today, adapter.maxPeriodDays()));
    List<String> messages = new ArrayList<>();
    EasyCollectionException failure = null;
    LocalDateTime lastReceivedAt = null;
    while (!windows.isEmpty()) {
      LocalDate[] window = windows.pollFirst();
      List<CollectionParsedRow> fetched;
      try {
        fetched = fetchWindow(context, adapter, window[0], window[1], limit);
      } catch (EasyCollectionException e) {
        failure = e;
        break;
      } catch (RuntimeException e) {
        failure = new EasyCollectionException(EasyCollectionErrorCode.PROVIDER_TEMPORARY,
            "Provider collection failed temporarily.", e);
        break;
      }
      if (fetched.size() > limit) {
        if (window[0].isBefore(window[1])) {
          // 한 번에 받을 수 있는 건수(limit)를 넘으면 구간을 반으로 나눠 다시 조회한다(누락 방지).
          List<LocalDate[]> halves = EasyCollectionPeriods.halve(window[0], window[1]);
          windows.addFirst(halves.get(1));
          windows.addFirst(halves.get(0));
          continue;
        }
        failure = new EasyCollectionException(EasyCollectionErrorCode.INVALID_DATA,
            "A single day returned more rows than the collection limit.");
        break;
      }
      LocalDateTime receivedAt = LocalDateTime.now();
      lastReceivedAt = receivedAt;
      try {
        List<String> windowErrors = inTransaction(
            () -> saveWindow(context, fetched, window[1], receivedAt));
        messages.addAll(windowErrors);
      } catch (EasyCollectionException e) {
        failure = e;
        break;
      } catch (RuntimeException e) {
        failure = new EasyCollectionException(EasyCollectionErrorCode.LEDGER_SAVE_FAILED,
            "Ledger save failed after provider response; cursor was not advanced.", e);
        break;
      }
    }
    EasyCollectionException finalFailure = failure;
    LocalDateTime finalReceivedAt = lastReceivedAt;
    return inTransaction(() -> completePeriodRun(context, finalFailure, messages, finalReceivedAt));
  }

  private List<CollectionParsedRow> fetchWindow(AdapterFetchContext context,
      EasyPeriodCollectionAdapter adapter, LocalDate from, LocalDate to, int limit) {
    if (executionRuntime == null) {
      return adapter.fetchPeriod(context.connectionRef(), context.providerConfig(), from, to,
          limit);
    }
    return executionRuntime.execute(
        () -> adapter.fetchPeriod(context.connectionRef(), context.providerConfig(), from, to,
            limit),
        () -> inTransaction(() -> {
          executionGate.heartbeat(context.runId(), context.connectionId(), context.leaseToken());
          return Boolean.TRUE;
        }));
  }

  private List<String> saveWindow(AdapterFetchContext context, List<CollectionParsedRow> rows,
      LocalDate windowEnd, LocalDateTime receivedAt) {
    if (executionGate != null) {
      executionGate.requireOwnedRunning(context.runId(), context.connectionId(),
          context.leaseToken());
    }
    EasyCollectionRun run = runRepository.findById(context.runId())
        .orElseThrow(() -> new IllegalStateException("수집 실행을 찾을 수 없습니다."));
    EasyCollectionConnection connection = connectionRepository.findById(context.connectionId())
        .orElseThrow(() -> new IllegalStateException("연결을 찾을 수 없습니다."));
    EasyBusiness business = requireActiveBusiness(context.businessId());
    SaveOutcome outcome = saveParsedRows(run, connection, business, context.sourceCode(),
        context.adapterCode(), context.adapterCode(), rows);
    run.accumulate(rows.size(), outcome.accepted(), outcome.duplicate(), outcome.error(),
        outcome.lastBatchId());
    run.addLedgerResult(outcome.ledger().saved(), outcome.ledger().review(),
        outcome.ledger().classified(), windowEnd, LocalDateTime.now());
    run.appendRowErrors(summarize(outcome.rowErrors()));
    advanceSavedThrough(connection, windowEnd);
    runRepository.save(run);
    return outcome.rowErrors();
  }

  /**
   * 실행 종료 기록. 기관 응답 수신 시각은 저장 트랜잭션과 별개로 여기서 남긴다 — 저장이 롤백돼도
   * "응답은 받았지만 장부 저장은 실패"가 이력에 보이게 하기 위해서다.
   */
  private EasyCollectionIngestResponse completePeriodRun(AdapterFetchContext context,
      EasyCollectionException failure, List<String> messages, LocalDateTime receivedAt) {
    EasyCollectionRun run = runRepository.findById(context.runId())
        .orElseThrow(() -> new IllegalStateException("수집 실행을 찾을 수 없습니다."));
    EasyCollectionConnection connection = connectionRepository.findById(context.connectionId())
        .orElseThrow(() -> new IllegalStateException("연결을 찾을 수 없습니다."));
    if (executionGate != null) {
      try {
        executionGate.requireOwnedRunning(context.runId(), context.connectionId(),
            context.leaseToken());
      } catch (IllegalStateException ownershipLost) {
        return new EasyCollectionIngestResponse(EasyCollectionRunResponse.from(run),
            List.of("Collection execution ownership was lost."));
      }
    }
    if (receivedAt != null) {
      run.recordFetched(receivedAt);
    }
    run.complete(failure == null ? null : failure.safeSummary());
    List<String> out = new ArrayList<>(messages);
    if (failure != null) {
      connection.recordFailure(failure.errorCode().name());
      if (failure.errorCode() == EasyCollectionErrorCode.AUTH_REQUIRED) {
        connection.markNeedsReauth();
      }
      out.add(failure.safeSummary());
    } else {
      connection.markSuccess(LocalDateTime.now());
    }
    connectionRepository.save(connection);
    runRepository.save(run);
    return new EasyCollectionIngestResponse(EasyCollectionRunResponse.from(run), out);
  }

  private java.util.Optional<LocalDate> savedThrough(Long connectionId) {
    if (checkpointRepository == null) {
      return java.util.Optional.empty();
    }
    return checkpointRepository.findByConnection_ConnectionIdAndDatasetType(connectionId,
            f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionDatasetType.BANK_TRANSACTION)
        .map(cp -> cp.getCheckpointValue())
        .filter(v -> v != null && !v.isBlank())
        .map(LocalDate::parse);
  }

  private void advanceSavedThrough(EasyCollectionConnection connection, LocalDate through) {
    if (checkpointRepository == null) {
      return;
    }
    var dataset = f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionDatasetType.BANK_TRANSACTION;
    var checkpoint = checkpointRepository
        .findByConnection_ConnectionIdAndDatasetType(connection.getConnectionId(), dataset)
        .orElseGet(() -> f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionCheckpoint.builder()
            .connection(connection)
            .datasetType(dataset)
            .build());
    checkpoint.advance(null, through.toString(), LocalDateTime.now());
    checkpointRepository.save(checkpoint);
  }

  /**
   * 어댑터 공통 수집 코어 — 단발 수집(CSV·기존 어댑터·로컬 금융파일)용. 저장 결과로 run을 끝낸다.
   */
  private EasyCollectionIngestResponse ingestParsedRows(EasyCollectionRun run,
      EasyCollectionConnection connection, EasyBusiness business, String sourceCode,
      String importSourceKind, String displayName, List<CollectionParsedRow> parsed) {
    SaveOutcome outcome = saveParsedRows(run, connection, business, sourceCode, importSourceKind,
        displayName, parsed);
    run.finish(parsed.size(), outcome.accepted(), outcome.duplicate(), outcome.error(),
        outcome.lastBatchId(), summarize(outcome.rowErrors()));
    run.addLedgerResult(outcome.ledger().saved(), outcome.ledger().review(),
        outcome.ledger().classified(), null, LocalDateTime.now());
    if (run.getStatus() != EasyCollectionRunStatus.FAILED) {
      connection.markSuccess(LocalDateTime.now());
    }
    return new EasyCollectionIngestResponse(EasyCollectionRunResponse.from(run),
        outcome.rowErrors());
  }

  /** 한 저장 단위의 결과. */
  record SaveOutcome(int accepted, int duplicate, int error, Long lastBatchId,
      EasyImportService.CollectedLedgerResult ledger, List<String> rowErrors) {}

  /**
   * 원본 보존 → 최소 중복 방지 → 장부 저장(2,000행 단위 import 배치) → raw 상태 갱신. 호출자의 트랜잭션
   * 안에서 원자적으로 실행된다(중간 실패 시 raw·import·장부 모두 롤백).
   *
   * <p>중복 방지는 두 단계다. ① 원본 키(dedupe_key) — 같은 원본 재수집은 저장하지 않는다.
   * ② 기존 장부(수집 외 경로로 들어온 거래)와 내용 fingerprint가 같으면 그 건수만큼만 제외한다 —
   * 날짜·금액이 같은 서로 다른 실제 거래가 더 많이 들어오면 초과분은 저장한다(임의 병합 금지).
   */
  SaveOutcome saveParsedRows(EasyCollectionRun run, EasyCollectionConnection connection,
      EasyBusiness business, String sourceCode, String importSourceKind, String displayName,
      List<CollectionParsedRow> parsed) {
    Long businessId = business.getBusinessId();
    Map<String, Integer> ledgerFingerprints = new HashMap<>();
    for (EasyTransaction txn : txnRepository
        .findByBusiness_BusinessIdAndFingerprintIsNotNull(businessId)) {
      ledgerFingerprints.merge(txn.getFingerprint(), 1, Integer::sum);
    }

    Map<String, Integer> seqByTuple = new HashMap<>();
    Set<String> seenKeys = new HashSet<>();
    List<EasyCollectionRawRecord> accepted = new ArrayList<>();
    List<CollectionParsedRow> acceptedRows = new ArrayList<>();
    List<String> rowErrors = new ArrayList<>();
    int duplicate = 0;
    int error = 0;
    Long connectionId = connection.getConnectionId();

    for (CollectionParsedRow row : parsed) {
      String tupleHash = EasyCollectionDedupeKeys.tupleHash(sourceCode, businessId,
          row.occurredOn(), row.direction(), row.amount(), row.counterparty(),
          row.description(), row.payMethod());
      int seq = seqByTuple.merge(tupleHash, 1, Integer::sum);
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
        // 날짜·금액을 해석하지 못한 자료: 원문과 사유만 보존하고 장부에 임의 값으로 넣지 않는다.
        error++;
        rowErrors.add("행 " + row.rowNo() + ": " + row.error());
        continue;
      }
      String fingerprint = EasyFingerprint.of(businessId, row.occurredOn(), row.direction(),
          row.amount(), row.counterparty(), row.description(), row.payMethod());
      Integer remaining = ledgerFingerprints.get(fingerprint);
      if (remaining != null && remaining > 0) {
        ledgerFingerprints.put(fingerprint, remaining - 1);
        raw.markLedgerDuplicate();
        duplicate++;
        continue;
      }
      accepted.add(raw);
      acceptedRows.add(row);
    }

    Long lastBatchId = null;
    EasyImportService.CollectedLedgerResult ledger = EasyImportService.CollectedLedgerResult.empty();
    int parts = (accepted.size() + MAX_ACCEPTED_ROWS - 1) / MAX_ACCEPTED_ROWS;
    for (int part = 0; part < parts; part++) {
      int from = part * MAX_ACCEPTED_ROWS;
      int to = Math.min(accepted.size(), from + MAX_ACCEPTED_ROWS);
      String partName = parts > 1 ? displayName + "#p" + (part + 1) : displayName;
      Handoff handoff = handoffToImport(run, businessId, partName, importSourceKind,
          sourceCode, connectionId, accepted.subList(from, to), acceptedRows.subList(from, to));
      lastBatchId = handoff.batchId();
      EasyImportService.CollectedLedgerResult saved = importService.saveCollectedBatch(run,
          handoff.batchId(), handoff.identityByRowId(), handoff.originalKeyByRowId(),
          this::txnIdByCollectionKey);
      ledger = ledger.plus(saved);
      markLedgerOutcome(handoff);
    }
    return new SaveOutcome(accepted.size(), duplicate + ledger.duplicate(),
        error + ledger.error(), lastBatchId, ledger, rowErrors);
  }

  /** 수집 원본 키 → import 행 → 장부 거래 id (취소 원거래 연결용). */
  private Long txnIdByCollectionKey(String dedupeKey) {
    return rawRepository.findByDedupeKey(dedupeKey)
        .map(EasyCollectionRawRecord::getImportRowId)
        .flatMap(importService::createdTxnIdOfRow)
        .orElse(null);
  }

  private void markLedgerOutcome(Handoff handoff) {
    for (EasyImportRowResponse row : importService.getBatch(handoff.batchId()).rows()) {
      EasyCollectionRawRecord raw = handoff.rawByRowId().get(row.rowId());
      if (raw == null) {
        continue;
      }
      if ("IMPORTED".equals(String.valueOf(row.status()))) {
        raw.markLedgerSaved();
      } else if ("DUPLICATE".equals(String.valueOf(row.status()))) {
        raw.markLedgerDuplicate();
      }
    }
  }

  public List<EasyCollectionRunResponse> listRuns(Long businessId) {
    if (businessId == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    return runRepository.findAllByBusiness_BusinessIdOrderByRunIdDesc(businessId).stream()
        .map(EasyCollectionRunResponse::from)
        .toList();
  }

  /** Local financial prototype seam: immutable archive ID makes DB handoff retries recoverable. */
  @Transactional
  public EasyCollectionIngestResponse ingestFinancialDraft(Long businessId, String collectionId,
      String scopeCode, List<CollectionParsedRow> parsed) {
    requireActiveBusiness(businessId);
    String filename = "financial-" + java.util.UUID.fromString(collectionId);
    var prior = runRepository.findAllByBusiness_BusinessIdOrderByRunIdDesc(businessId).stream()
        .filter(run -> "FINANCIAL_FILE".equals(run.getAdapterCode())
            && filename.equals(run.getSourceFilename())).findFirst();
    if (prior.isPresent()) {
      return new EasyCollectionIngestResponse(EasyCollectionRunResponse.from(prior.get()), List.of());
    }
    if (scopeCode == null || !scopeCode.matches("FIN_[a-f0-9]{24}") || parsed.size() > MAX_ACCEPTED_ROWS) {
      throw new IllegalArgumentException("FINANCIAL_SCOPE_OR_ROW_LIMIT_INVALID");
    }
    EasyBusiness business = requireActiveBusiness(businessId);
    EasyCollectionSource source = sourceRepository.findByCode(SOURCE_CODE)
        .orElseThrow(() -> new IllegalArgumentException("파일 수집원이 없습니다."));
    EasyCollectionConnection connection = findOrCreateConnection(business, source, scopeCode);
    EasyCollectionRun run = runRepository.save(EasyCollectionRun.builder()
        .business(business).connection(connection).triggerType("MANUAL")
        .sourceFilename(filename).adapterCode("FINANCIAL_FILE").adapterVersion("v1").build());
    // scopeCode includes dataset + account reference, including tuple fallback keys.
    return ingestParsedRows(run, connection, business, scopeCode, "FINANCIAL_FILE", filename, parsed);
  }

  public EasyCollectionRunResponse getRun(Long runId) {
    return EasyCollectionRunResponse.from(runRepository.findById(runId)
        .orElseThrow(() -> new IllegalArgumentException("수집 실행을 찾을 수 없습니다: " + runId)));
  }

  public List<EasyCollectionSourceResponse> listSources() {
    return EasyCollectionSourceResponse.listOf(sourceRepository.findAll());
  }

  /**
   * connection_ref 업데이트 — 관리자 콘솔에서 가맹점 동의ID 등 참조자 등록/수정.
   * NEEDS_REAUTH 상태면 ACTIVE로 복구.
   */
  @Transactional
  public void updateConnectionRef(Long businessId, Long connectionId, String connectionRef) {
    EasyCollectionConnection conn = connectionRepository.findById(connectionId)
        .orElseThrow(() -> new IllegalArgumentException("연결을 찾을 수 없습니다: " + connectionId));
    if (!conn.getBusiness().getBusinessId().equals(businessId)) {
      throw new IllegalArgumentException("사업체 불일치");
    }
    conn.updateConnectionRef(connectionRef);
    connectionRepository.save(conn);
  }

  /**
   * 연결 생성 또는 조회 (connection_ref 포함) — 관리자 콘솔에서 초기 등록용.
   */
  @Transactional
  public EasyCollectionConnection createOrUpdateConnection(Long businessId, String sourceCode,
      String label, String connectionRef) {
    EasyBusiness business = requireActiveBusiness(businessId);
    EasyCollectionSource source = sourceRepository.findByCode(sourceCode)
        .orElseThrow(() -> new IllegalArgumentException("수집원을 찾을 수 없습니다: " + sourceCode));
    String normalizedLabel = label == null || label.isBlank() ? "default" : label.trim();
    EasyCollectionConnection conn = connectionRepository
        .findByBusiness_BusinessIdAndSource_SourceIdAndLabel(businessId, source.getSourceId(), normalizedLabel)
        .orElseGet(() -> EasyCollectionConnection.builder()
            .business(business)
            .source(source)
            .label(normalizedLabel)
            .build());
    if (connectionRef != null && !connectionRef.isBlank()) {
      conn.updateConnectionRef(connectionRef);
    }
    return connectionRepository.save(conn);
  }

  /**
   * 연결 단건 조회 — 상세 화면용
   */
  public EasyCollectionConnection getConnection(Long connectionId) {
    return connectionRepository.findById(connectionId)
        .orElseThrow(() -> new IllegalArgumentException("연결을 찾을 수 없습니다: " + connectionId));
  }

  /** import 인계 결과 — 행 id ↔ 원본 연결. */
  record Handoff(Long batchId, Map<Long, EasyCollectionRawRecord> rawByRowId,
      Map<Long, String> identityByRowId, Map<Long, String> originalKeyByRowId) {}

  /**
   * 기존 import 경계 인계 — rowNo는 accepted 행 순서와 1:1이라 응답 rowNo로 raw 링크를 되매핑한다.
   * rawData 끝부분에 dedupeKey를 박아 원문 raw ↔ import row ↔ 장부 거래 역추적 체인을 만든다.
   * 원문에 {@code original_external_id}가 있으면 취소·부분취소로 보고 역분개(reversal) 행으로 넘긴다.
   */
  private Handoff handoffToImport(EasyCollectionRun run, Long businessId, String displayName,
      String importSourceKind, String sourceCode, Long connectionId,
      List<EasyCollectionRawRecord> accepted, List<CollectionParsedRow> rows) {
    List<EasyImportRowInput> inputs = new ArrayList<>();
    Map<Integer, EasyCollectionRawRecord> rawByRowNo = new HashMap<>();
    Map<Integer, String> originalByRowNo = new HashMap<>();
    for (int i = 0; i < accepted.size(); i++) {
      EasyCollectionRawRecord raw = accepted.get(i);
      CollectionParsedRow row = rows.get(i);
      int rowNo = i + 1;
      rawByRowNo.put(rowNo, raw);
      String originalExternalId = EasyCollectionRawJson.text(row.rawJson(), "original_external_id");
      if (originalExternalId != null) {
        originalByRowNo.put(rowNo, EasyCollectionDedupeKeys.external(sourceCode, businessId,
            connectionId, originalExternalId));
      }
      inputs.add(new EasyImportRowInput(
          row.occurredOn().toString(),
          row.direction(),
          row.amount(),
          row.counterparty(),
          row.description(),
          row.payMethod(),
          row.supplyAmount(),
          row.vatAmount(),
          // 증빙 유형을 모르면 거래를 버리지 않고 증빙 미지정으로 저장한다(원문 값은 raw에 그대로 남는다).
          knownEvidenceType(row.evidenceType()),
          raw.getRawPayload() + " [ext:" + raw.getDedupeKey() + "]",
          row.externalAccount(),
          originalExternalId != null ? Boolean.TRUE : null));
    }
    EasyImportBatchResponse batch = importService.stage(new EasyImportBatchRequest(
        businessId,
        displayName + "#run:" + run.getRunId(),
        importSourceKind,
        inputs));
    Map<Long, EasyCollectionRawRecord> rawByRowId = new HashMap<>();
    Map<Long, String> identityByRowId = new HashMap<>();
    Map<Long, String> originalKeyByRowId = new HashMap<>();
    for (EasyImportRowResponse staged : batch.rows()) {
      EasyCollectionRawRecord raw = rawByRowNo.get(staged.rowNo());
      if (raw != null) {
        raw.linkImport(batch.batchId(), staged.rowId());
        rawByRowId.put(staged.rowId(), raw);
        identityByRowId.put(staged.rowId(), raw.getDedupeKey());
        String original = originalByRowNo.get(staged.rowNo());
        if (original != null) {
          originalKeyByRowId.put(staged.rowId(), original);
        }
      }
    }
    return new Handoff(batch.batchId(), rawByRowId, identityByRowId, originalKeyByRowId);
  }

  private static String knownEvidenceType(String evidenceType) {
    if (evidenceType == null || evidenceType.isBlank()) {
      return null;
    }
    try {
      f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyEvidenceType.parse(evidenceType);
      return evidenceType;
    } catch (IllegalArgumentException e) {
      return null;
    }
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
