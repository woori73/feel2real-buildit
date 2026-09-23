package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyLocalCollector;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyLocalCollectorRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EasyLocalCollectorServiceTest {
  private final EasyBusinessRepository businesses = mock(EasyBusinessRepository.class);
  private final EasyLocalCollectorRepository collectors = mock(EasyLocalCollectorRepository.class);
  private EasyLocalCollectorService service;
  private EasyBusiness business;

  @BeforeEach
  void setUp() {
    service = new EasyLocalCollectorService(businesses, collectors);
    business = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).build();
    ReflectionTestUtils.setField(business, "businessId", 1L);
    when(businesses.findById(1L)).thenReturn(Optional.of(business));
    when(collectors.save(any())).thenAnswer(invocation -> {
      EasyLocalCollector value = invocation.getArgument(0);
      ReflectionTestUtils.setField(value, "collectorId", 100L);
      return value;
    });
  }

  @Test
  void registerReturnsRawTokenOnceAndStoresOnlyItsHash() {
    var result = service.register(1L, "에듀홀릭-원장PC");

    assertThat(result.rawToken()).matches("[0-9a-f]{64}");
    assertThat(result.collector().getTokenHash()).matches("[0-9a-f]{64}");
    assertThat(result.collector().getTokenHash()).isNotEqualTo(result.rawToken());
    assertThat(result.collector().getName()).isEqualTo("에듀홀릭-원장PC");
    assertThat(result.collector().getBusiness()).isSameAs(business);
  }

  @Test
  void registerRejectsUnknownBusiness() {
    when(businesses.findById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.register(99L, "PC-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void authenticateSucceedsForActiveCollectorWithMatchingToken() {
    var result = service.register(1L, "PC-1");
    String rawToken = result.rawToken();
    when(collectors.findByTokenHash(result.collector().getTokenHash()))
        .thenReturn(Optional.of(result.collector()));

    var authenticated = service.authenticate(rawToken);

    assertThat(authenticated).isPresent();
    assertThat(authenticated.get().getCollectorId()).isEqualTo(100L);
  }

  @Test
  void authenticateFailsForUnknownToken() {
    when(collectors.findByTokenHash(any())).thenReturn(Optional.empty());
    assertThat(service.authenticate("does-not-exist")).isEmpty();
  }

  @Test
  void authenticateFailsForBlankOrNullToken() {
    assertThat(service.authenticate(null)).isEmpty();
    assertThat(service.authenticate("  ")).isEmpty();
    verifyNoInteractions(collectors);
  }

  @Test
  void authenticateFailsForRevokedCollectorEvenWithCorrectToken() {
    var result = service.register(1L, "PC-1");
    result.collector().revoke();
    when(collectors.findByTokenHash(result.collector().getTokenHash()))
        .thenReturn(Optional.of(result.collector()));

    assertThat(service.authenticate(result.rawToken())).isEmpty();
  }

  @Test
  void heartbeatUpdatesLastHeartbeatAt() {
    EasyLocalCollector collector = EasyLocalCollector.builder()
        .business(business).name("PC-1").tokenHash("a".repeat(64)).build();
    ReflectionTestUtils.setField(collector, "collectorId", 100L);
    when(collectors.findById(100L)).thenReturn(Optional.of(collector));

    service.heartbeat(100L);

    assertThat(collector.getLastHeartbeatAt()).isNotNull();
  }

  @Test
  void heartbeatRejectsUnknownCollector() {
    when(collectors.findById(999L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.heartbeat(999L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
