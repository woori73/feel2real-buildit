package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

class EasyCollectionManagementServiceTest {

  @Mock EasyCollectionConnectionRepository connectionRepository;
  @Mock EasyCollectionRunRepository runRepository;
  @Mock EasyCollectionCheckpointRepository checkpointRepository;
  @Mock EasyCollectionProviderReadinessPolicy readinessPolicy;
  @Mock EasyCollectionSchedulerService schedulerService;

  EasyCollectionManagementService service;
  EasyCollectionConnection connection;
  EasyCollectionSource source;
  EasyBusiness business;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new EasyCollectionManagementService(connectionRepository, runRepository,
        checkpointRepository, readinessPolicy, schedulerService);
    connection = mock(EasyCollectionConnection.class);
    source = mock(EasyCollectionSource.class);
    business = mock(EasyBusiness.class);
    when(connection.getConnectionId()).thenReturn(7L);
    when(connection.getBusiness()).thenReturn(business);
    when(business.getBusinessId()).thenReturn(1L);
    when(connection.getSource()).thenReturn(source);
    when(source.getCode()).thenReturn("CARDSALES");
    when(source.getName()).thenReturn("Card sales");
    when(source.isActive()).thenReturn(true);
    when(connection.getStatus()).thenReturn(EasyCollectionConnectionStatus.ACTIVE);
    when(connection.getConnectionRef()).thenReturn("write-only-reference");
    when(connectionRepository.findById(7L)).thenReturn(Optional.of(connection));
    when(runRepository.findFirstByConnection_ConnectionIdOrderByRunIdDesc(7L))
        .thenReturn(Optional.empty());
  }

  @Test
  void connectionListContainsReadinessButNeverConnectionReference() {
    when(connectionRepository.findAllByBusiness_BusinessIdOrderByConnectionIdAsc(1L))
        .thenReturn(List.of(connection));
    when(readinessPolicy.isVerified(source)).thenReturn(false);

    List<EasyCollectionManagementResponse> result = service.listConnections(1L);

    assertThat(result).singleElement().satisfies(item -> {
      assertThat(item.readiness()).isEqualTo(EasyCollectionProviderReadiness.UNVERIFIED);
      assertThat(item.hasConnectionRef()).isTrue();
    });
    assertThat(Arrays.stream(EasyCollectionManagementResponse.class.getRecordComponents())
        .map(component -> component.getName()))
        .doesNotContain("connectionRef", "credential", "token", "secret");
  }

  @Test
  @DisplayName("given 직접 로그인 수집(MCP_SITE) 최근 실행 when 연결 목록 then 기관 코드·계좌 참조·장부 저장 합계를 표시용으로 준다")
  void siteCollectionConnectionCarriesDisplayFields() {
    // given
    EasyCollectionRun run = mock(EasyCollectionRun.class);
    when(run.getRunId()).thenReturn(30L);
    when(run.getStatus()).thenReturn(EasyCollectionRunStatus.SUCCESS);
    when(run.getAdapterCode()).thenReturn(EasyCollectionIngestService.SITE_ADAPTER_CODE);
    when(runRepository.findFirstByConnection_ConnectionIdOrderByRunIdDesc(7L)).thenReturn(Optional.of(run));
    when(runRepository.sumLedgerSavedByConnection(7L)).thenReturn(12L);
    EasyCollectionRawRecordRepository rawRepository = mock(EasyCollectionRawRecordRepository.class);
    EasyCollectionRawRecord raw = mock(EasyCollectionRawRecord.class);
    when(raw.getRawPayload()).thenReturn("{\"provider\":\"MCP_SITE\",\"site\":\"KB_BANK\",\"account_ref\":\"운영통장\"}");
    when(rawRepository.findFirstByRun_RunIdOrderByRawIdAsc(30L)).thenReturn(Optional.of(raw));
    org.springframework.test.util.ReflectionTestUtils.setField(service, "rawRepository", rawRepository);
    when(connectionRepository.findAllByBusiness_BusinessIdOrderByConnectionIdAsc(1L)).thenReturn(List.of(connection));

    // when
    List<EasyCollectionManagementResponse> result = service.listConnections(1L);

    // then
    assertThat(result).singleElement().satisfies(item -> {
      assertThat(item.latestRunAdapterCode()).isEqualTo("MCP_SITE");
      assertThat(item.siteCode()).isEqualTo("KB_BANK");
      assertThat(item.siteAccountRef()).isEqualTo("운영통장");
      assertThat(item.ledgerSavedTotal()).isEqualTo(12L);
    });
  }

  @Test
  @DisplayName("given 파일 수집 최근 실행 when 연결 목록 then 기관 코드는 비어 있고 원본을 읽지 않는다")
  void fileConnectionDoesNotReadRaw() {
    // given
    EasyCollectionRun run = mock(EasyCollectionRun.class);
    when(run.getRunId()).thenReturn(31L);
    when(run.getStatus()).thenReturn(EasyCollectionRunStatus.SUCCESS);
    when(run.getAdapterCode()).thenReturn("FILE_CSV");
    when(runRepository.findFirstByConnection_ConnectionIdOrderByRunIdDesc(7L)).thenReturn(Optional.of(run));
    EasyCollectionRawRecordRepository rawRepository = mock(EasyCollectionRawRecordRepository.class);
    org.springframework.test.util.ReflectionTestUtils.setField(service, "rawRepository", rawRepository);
    when(connectionRepository.findAllByBusiness_BusinessIdOrderByConnectionIdAsc(1L)).thenReturn(List.of(connection));

    // when
    var item = service.listConnections(1L).get(0);

    // then
    assertThat(item.siteCode()).isNull();
    assertThat(item.latestRunAdapterCode()).isEqualTo("FILE_CSV");
    verifyNoInteractions(rawRepository);
  }

  @Test
  void disabledConnectionBlocksBeforeAdapterDelegate() {
    when(connection.getStatus()).thenReturn(EasyCollectionConnectionStatus.DISABLED);

    assertThatThrownBy(() -> service.triggerManual(1L, 7L))
        .isInstanceOf(EasyCollectionException.class)
        .hasMessageContaining("DISABLED");
    verifyNoInteractions(schedulerService);
  }

  @Test
  void needsReauthBlocksBeforeAdapterDelegate() {
    when(connection.getStatus()).thenReturn(EasyCollectionConnectionStatus.NEEDS_REAUTH);

    assertThatThrownBy(() -> service.triggerManual(1L, 7L))
        .isInstanceOf(EasyCollectionException.class)
        .hasMessageContaining("NEEDS_REAUTH");
    verifyNoInteractions(schedulerService);
  }

  @Test
  void unverifiedCrefiaBlocksBeforeAdapterDelegateAndNetwork() {
    when(readinessPolicy.isVerified(source)).thenReturn(false);

    assertThatThrownBy(() -> service.triggerManual(1L, 7L))
        .isInstanceOf(EasyCollectionException.class)
        .hasMessageContaining("UNVERIFIED");
    verifyNoInteractions(schedulerService);
  }

  @Test
  void readyProviderCreatesManualRunThroughExistingPipeline() {
    when(readinessPolicy.isVerified(source)).thenReturn(true);
    EasyCollectionRunResponse manual = new EasyCollectionRunResponse(
        9L, 7L, 1L, "TEST", "MANUAL", "SUCCESS", "", "TEST", "v1",
        1, 1, 0, 0, null, null, null, null, null, null, null, 0, 0, 0);
    when(schedulerService.triggerManualCollection(1L, 7L)).thenReturn(manual);

    EasyCollectionRunResponse result = service.triggerManual(1L, 7L);

    assertThat(result.triggerType()).isEqualTo("MANUAL");
    verify(schedulerService).triggerManualCollection(1L, 7L);
  }

  @Test
  void duplicateRunningBlocksSecondManualRun() {
    when(readinessPolicy.isVerified(source)).thenReturn(true);
    when(runRepository.existsByConnection_ConnectionIdAndStatus(
        7L, EasyCollectionRunStatus.RUNNING)).thenReturn(true);

    assertThatThrownBy(() -> service.triggerManual(1L, 7L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already");
    verifyNoInteractions(schedulerService);
  }

  @Test
  void checkpointViewContainsNoCursorValueOrSecret() {
    assertThat(Arrays.stream(EasyCollectionCheckpointResponse.class.getRecordComponents())
        .map(component -> component.getName()))
        .contains("hasCursor", "checkpointExists")
        .doesNotContain("cursorValue", "checkpointValue", "connectionRef", "secret");
  }

  @Test
  void activationRemainsFailClosedForUnverifiedProvider() {
    when(connection.getStatus()).thenReturn(EasyCollectionConnectionStatus.DISABLED);
    when(readinessPolicy.isVerified(source)).thenReturn(false);

    assertThatThrownBy(() -> service.enable(1L, 7L))
        .isInstanceOf(EasyCollectionException.class);
    verify(connection, never()).enable();
  }
}
