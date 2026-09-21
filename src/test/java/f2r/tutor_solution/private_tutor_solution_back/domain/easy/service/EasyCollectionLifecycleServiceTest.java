package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

class EasyCollectionLifecycleServiceTest {

  @Mock EasyCollectionConnectionRepository connectionRepository;
  @Mock EasyEconomicSaleRepository saleRepository;
  @Mock EasyCollectionEventRepository eventRepository;
  @Mock EasyCollectionCheckpointRepository checkpointRepository;

  EasyCollectionLifecycleService service;
  EasyCollectionConnection connection;
  EasyBusiness business;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new EasyCollectionLifecycleService(connectionRepository, saleRepository,
        eventRepository, checkpointRepository);
    connection = mock(EasyCollectionConnection.class);
    business = mock(EasyBusiness.class);
    when(connection.getBusiness()).thenReturn(business);
    when(connectionRepository.findById(1L)).thenReturn(Optional.of(connection));
    when(eventRepository.findByConnection_ConnectionIdAndEventKey(anyLong(), anyString()))
        .thenReturn(Optional.empty());
    when(saleRepository.findByConnection_ConnectionIdAndEconomicKey(anyLong(), anyString()))
        .thenReturn(Optional.empty());
    when(saleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void approvalRetryIsIdempotentAndCheckpointAdvancesOnlyOnSuccess() {
    EasyCollectionEventObservation approval = observation("A-1", "SALE-1", null);
    assertThat(service.recordSuccessfulBatch(1L, EasyCollectionDatasetType.APPROVAL,
        List.of(approval), "cursor-2", "page-2")).isEqualTo(1);
    verify(eventRepository).save(any(EasyCollectionEvent.class));
    verify(checkpointRepository).save(argThat(cp ->
        "cursor-2".equals(cp.getCursorValue()) && cp.getLastSuccessAt() != null));

    reset(eventRepository, checkpointRepository);
    when(eventRepository.findByConnection_ConnectionIdAndEventKey(1L, "APPROVAL:EXT:A-1"))
        .thenReturn(Optional.of(mock(EasyCollectionEvent.class)));
    when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    assertThat(service.recordSuccessfulBatch(1L, EasyCollectionDatasetType.APPROVAL,
        List.of(approval), "cursor-2", "page-2")).isZero();
    verify(eventRepository, never()).save(any());
  }

  @Test
  void approvalPurchaseDepositRemainThreeEventsForOneSaleAndCreateNoAccounting() {
    EasyEconomicSale sale = mock(EasyEconomicSale.class);
    when(saleRepository.findByConnection_ConnectionIdAndEconomicKey(1L, "SALE-1"))
        .thenReturn(Optional.of(sale));

    service.recordSuccessfulBatch(1L, EasyCollectionDatasetType.APPROVAL,
        List.of(observation("A-1", "SALE-1", null)), "a", "1");
    service.recordSuccessfulBatch(1L, EasyCollectionDatasetType.PURCHASE,
        List.of(observation("P-1", "SALE-1", null)), "p", "1");
    service.recordSuccessfulBatch(1L, EasyCollectionDatasetType.DEPOSIT,
        List.of(observation("D-1", "SALE-1", null)), "d", "1");

    verify(eventRepository, times(3)).save(any(EasyCollectionEvent.class));
    verify(saleRepository, never()).save(any());
    // This service intentionally has no EasyImportService/EasyTransactionRepository dependency.
  }

  @Test
  void cancellationReferencesAndReversesOriginalWithoutCreatingNormalIncome() {
    EasyEconomicSale sale = mock(EasyEconomicSale.class);
    EasyCollectionEvent original = mock(EasyCollectionEvent.class);
    when(original.getEconomicSale()).thenReturn(sale);
    when(saleRepository.findByConnection_ConnectionIdAndEconomicKey(1L, "SALE-1"))
        .thenReturn(Optional.of(sale));
    when(eventRepository
        .findFirstByConnection_ConnectionIdAndExternalEventIdOrderByEventIdAsc(1L, "A-1"))
        .thenReturn(Optional.of(original));

    service.recordSuccessfulBatch(1L, EasyCollectionDatasetType.CANCELLATION,
        List.of(observation("C-1", "SALE-1", "A-1")), "c", "1");

    verify(original).markReversed();
    verify(sale).markReversed();
    verify(eventRepository).save(argThat(e ->
        e.getDatasetType() == EasyCollectionDatasetType.CANCELLATION
            && e.getOriginalEvent() == original));
  }

  @Test
  void failedPersistenceDoesNotAttemptCheckpointAdvance() {
    when(eventRepository.save(any())).thenThrow(new RuntimeException("test persistence failure"));

    assertThatThrownBy(() -> service.recordSuccessfulBatch(1L,
        EasyCollectionDatasetType.APPROVAL,
        List.of(observation("A-1", "SALE-1", null)), "must-not-advance", "x"))
        .isInstanceOf(RuntimeException.class);

    verify(checkpointRepository, never()).save(any());
  }

  @Test
  void cancellationWithoutOriginalFailsAndDoesNotAdvanceCheckpoint() {
    assertThatThrownBy(() -> service.recordSuccessfulBatch(1L,
        EasyCollectionDatasetType.CANCELLATION,
        List.of(observation("C-1", "SALE-1", null)), "must-not-advance", "x"))
        .isInstanceOf(EasyCollectionException.class)
        .hasMessageContaining("Cancellation");

    verify(checkpointRepository, never()).save(any());
  }

  private static EasyCollectionEventObservation observation(
      String externalId, String economicKey, String originalId) {
    return new EasyCollectionEventObservation(externalId, economicKey, originalId,
        LocalDateTime.of(2026, 9, 21, 12, 0), "provider-cursor", "v1", null);
  }
}
