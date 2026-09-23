package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.controller.NewStartOfficialUpdateAdminController;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery.CcrsDiscoveryAdapter;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateDiscoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Production incident regression (2026-09-22): {@code CcrsDiscoveryAdapter} was
 * constructor-injected with the Jackson 2 {@code com.fasterxml.jackson.databind.ObjectMapper},
 * but Spring Boot 4 auto-configures a Jackson 3 {@code tools.jackson.databind.ObjectMapper}
 * bean. No qualifying bean existed, so the real application context failed to start with
 * {@code UnsatisfiedDependencyException} on {@code CcrsDiscoveryAdapter}.
 *
 * <p>{@code CcrsDiscoveryAdapterTest} manually constructed the adapter with {@code new
 * ObjectMapper()} and so never exercised Spring's dependency injection — it could not have
 * caught this. This test boots the real application context (no manually-built collaborators)
 * and proves Spring can wire the full chain that actually failed in production:
 * {@code NewStartOfficialUpdateAdminController -> NewStartOfficialUpdateDiscoveryService ->
 * CcrsDiscoveryAdapter -> ObjectMapper}.
 */
@SpringBootTest
class NewStartOfficialUpdateSpringContextRegressionTest {

  @Autowired
  private CcrsDiscoveryAdapter ccrsDiscoveryAdapter;

  @Autowired
  private NewStartOfficialUpdateDiscoveryService discoveryService;

  @Autowired
  private NewStartOfficialUpdateAdminController adminController;

  @Test
  void springWiresTheRealDiscoveryChainWithoutManualObjectMapperConstruction() {
    assertThat(ccrsDiscoveryAdapter).isNotNull();
    assertThat(discoveryService).isNotNull();
    assertThat(adminController).isNotNull();
  }
}
