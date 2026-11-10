package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnectionStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRunStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionCheckpointRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionConnectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionSourceRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** 은행 연결 등록·설정 수정·재인증·해제와 실제 연동 전 표시(readiness). */
class EasyCollectionBankConnectionTest {

  private final EasyCollectionConnectionRepository connectionRepository = lenient(EasyCollectionConnectionRepository.class);
  private final EasyCollectionRunRepository runRepository = lenient(EasyCollectionRunRepository.class);
  private final EasyCollectionCheckpointRepository checkpointRepository = lenient(EasyCollectionCheckpointRepository.class);
  private final EasyCollectionSourceRepository sourceRepository = lenient(EasyCollectionSourceRepository.class);
  private final EasyBusinessRepository businessRepository = lenient(EasyBusinessRepository.class);
  private final Map<Long, EasyCollectionConnection> connections = new LinkedHashMap<>();
  private final AtomicLong ids = new AtomicLong(100L);
  private EasyCollectionSource bankSource;

  private static <T> T lenient(Class<T> type) {
    return mock(type, withSettings().strictness(Strictness.LENIENT));
  }

  @BeforeEach
  void setUp() {
    bankSource = EasyCollectionSource.builder().code("CODEF_BANK").name("은행 입출금")
        .adapterType("RELAY_API").active(true).build();
    ReflectionTestUtils.setField(bankSource, "sourceId", 9L);
    when(sourceRepository.findByCode("CODEF_BANK")).thenReturn(Optional.of(bankSource));
    for (long id : new long[] {1L, 2L}) {
      EasyBusiness business = EasyBusiness.builder().code("B" + id).name("사업체" + id)
          .active(true).sortOrder((int) id).build();
      ReflectionTestUtils.setField(business, "businessId", id);
      when(businessRepository.findById(id)).thenReturn(Optional.of(business));
    }
    when(connectionRepository.save(any(EasyCollectionConnection.class))).thenAnswer(inv -> {
      EasyCollectionConnection c = inv.getArgument(0);
      if (c.getConnectionId() == null) {
        ReflectionTestUtils.setField(c, "connectionId", ids.incrementAndGet());
      }
      connections.put(c.getConnectionId(), c);
      return c;
    });
    when(connectionRepository.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(connections.get(inv.getArgument(0))));
    when(connectionRepository.findByBusiness_BusinessIdAndSource_SourceIdAndLabel(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(runRepository.findFirstByConnection_ConnectionIdOrderByRunIdDesc(any()))
        .thenReturn(Optional.empty());
  }

  private EasyCollectionManagementService service(String verifiedSources) {
    EasyCollectionManagementService service = new EasyCollectionManagementService(
        connectionRepository, runRepository, checkpointRepository,
        new EasyCollectionProviderReadinessPolicy(verifiedSources),
        mock(EasyCollectionSchedulerService.class));
    ReflectionTestUtils.setField(service, "sourceRepository", sourceRepository);
    ReflectionTestUtils.setField(service, "businessRepository", businessRepository);
    return service;
  }

  @Test
  @DisplayName("given 계약·검증 전 when 계좌 등록 then 자동수집 꺼짐·연동 준비 중(UNVERIFIED)으로 보이고 계좌는 가려진다")
  void registrationBeforeVerificationIsNotShownAsConnected() {
    // given
    var service = service("");

    // when
    var saved = service.saveBankConnection(1L, null, "운영계좌", "0004", "B",
        "123-456-789012", "cid-synthetic");

    // then
    assertThat(saved.readiness()).isEqualTo(EasyCollectionProviderReadiness.DISABLED);
    assertThat(saved.enabled()).isFalse();
    assertThat(saved.accountMasked()).isEqualTo("********9012");
    assertThat(saved.authStatus()).isEqualTo("REGISTERED");
    assertThatThrownBy(() -> service.enable(1L, saved.connectionId()))
        .isInstanceOf(EasyCollectionException.class)
        .hasMessageContaining("UNVERIFIED");
  }

  @Test
  @DisplayName("given 검증 완료로 허용 목록에 등록 when 켜기 then READY")
  void verifiedSourceCanBeEnabled() {
    // given
    var service = service("CODEF_BANK");
    var saved = service.saveBankConnection(1L, null, "운영계좌", "0004", "P",
        "123456789012", "cid-synthetic");

    // when
    var enabled = service.enable(1L, saved.connectionId());

    // then
    assertThat(enabled.readiness()).isEqualTo(EasyCollectionProviderReadiness.READY);
    assertThat(enabled.clientType()).isEqualTo("P");
  }

  @Test
  @DisplayName("given 연결 when 해제 then 조회 중단·인증 참조 제거, 다시 쓰려면 재인증 등록이 필요하다")
  void disconnectStopsAndClearsReference() {
    // given
    var service = service("CODEF_BANK");
    var saved = service.saveBankConnection(1L, null, "운영계좌", "0004", "B",
        "123456789012", "cid-synthetic");

    // when
    var disconnected = service.disconnect(1L, saved.connectionId());

    // then
    EasyCollectionConnection entity = connections.get(saved.connectionId());
    assertThat(entity.getStatus()).isEqualTo(EasyCollectionConnectionStatus.DISCONNECTED);
    assertThat(entity.getConnectionRef()).isNull();
    assertThat(entity.getDisconnectedAt()).isNotNull();
    assertThat(disconnected.authStatus()).isEqualTo("DISCONNECTED");
    assertThatThrownBy(() -> service.enable(1L, saved.connectionId()))
        .isInstanceOf(EasyCollectionException.class);

    // when — 재인증 등록
    var reauth = service.updateAuthReference(1L, saved.connectionId(), "cid-new");

    // then — 꺼진 상태로 돌아오고, 켜기는 따로 한다
    assertThat(reauth.connectionStatus()).isEqualTo("DISABLED");
    assertThat(service.enable(1L, saved.connectionId()).readiness())
        .isEqualTo(EasyCollectionProviderReadiness.READY);
  }

  @Test
  @DisplayName("given 수집 진행 중 when 해제 then 거부한다")
  void disconnectRefusedWhileRunning() {
    var service = service("");
    var saved = service.saveBankConnection(1L, null, null, "0004", "B", "123456789012", null);
    when(runRepository.existsByConnection_ConnectionIdAndStatus(saved.connectionId(),
        EasyCollectionRunStatus.RUNNING)).thenReturn(true);

    assertThatThrownBy(() -> service.disconnect(1L, saved.connectionId()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("given 다른 사업체의 연결 when 수정·해제·이력 조회 then 모두 거부한다")
  void otherBusinessCannotTouchConnection() {
    var service = service("");
    var saved = service.saveBankConnection(1L, null, "운영계좌", "0004", "B", "123456789012", null);

    assertThatThrownBy(() -> service.saveBankConnection(2L, saved.connectionId(), "x", "0004",
        "B", "123456789012", null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.disconnect(2L, saved.connectionId()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.listRuns(2L, saved.connectionId()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.updateAuthReference(2L, saved.connectionId(), "cid"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 허용 목록 문자열 when 정책 생성 then 대소문자·공백을 무시하고 목록에 있는 수집원만 검증됨")
  void readinessPolicyAllowList() {
    var policy = new EasyCollectionProviderReadinessPolicy(" codef_bank , CARDSALES ");
    assertThat(policy.isVerified(bankSource)).isTrue();
    assertThat(new EasyCollectionProviderReadinessPolicy("").isVerified(bankSource)).isFalse();
    assertThat(new EasyCollectionProviderReadinessPolicy().isVerified(bankSource)).isFalse();
  }

  @Test
  @DisplayName("given 개인 이름 거래처 when AI 추천 프롬프트 then 이름 대신 (개인)으로 보낸다")
  void recommendationMasksPersonNames() {
    assertThat(EasyCollectionAccountRecommendationService.safeCounterparty("김학부")).isEqualTo("(개인)");
    assertThat(EasyCollectionAccountRecommendationService.safeCounterparty("(주)교보문고"))
        .isEqualTo("(주)교보문고");
    assertThat(EasyCollectionAccountRecommendationService.safeCounterparty(null)).isEqualTo("(없음)");
  }
}
