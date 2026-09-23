package f2r.tutor_solution.private_tutor_solution_back.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyLocalCollector;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLocalCollectorService;
import jakarta.servlet.FilterChain;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * EasyCollectorAgentTokenFilter 단위 검증 (무DB — EasyLocalCollectorService는 Mockito mock).
 * given/when/then: 유효 토큰+allowlist → collector 인증 / 오토큰·revoked·비allowlist → 미인증 /
 * 기존 인증 보존.
 */
class EasyCollectorAgentTokenFilterTest {

  private static final String TOKEN = "test-collector-token-123";

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private MockHttpServletRequest request(String method, String uri) {
    MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
    req.setRequestURI(uri);
    return req;
  }

  private EasyLocalCollector collector(long id) {
    EasyBusiness business = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).build();
    ReflectionTestUtils.setField(business, "businessId", 1L);
    EasyLocalCollector collector = EasyLocalCollector.builder()
        .business(business).name("PC-1").tokenHash("a".repeat(64)).build();
    ReflectionTestUtils.setField(collector, "collectorId", id);
    return collector;
  }

  @SuppressWarnings("unchecked")
  private ObjectProvider<EasyLocalCollectorService> providerOf(EasyLocalCollectorService service) {
    ObjectProvider<EasyLocalCollectorService> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(service);
    return provider;
  }

  @Test
  void givenValidTokenOnAllowlistedPost_whenFilter_thenAuthenticatesAsCollector() throws Exception {
    EasyLocalCollectorService service = mock(EasyLocalCollectorService.class);
    when(service.authenticate(TOKEN)).thenReturn(Optional.of(collector(7L)));
    EasyCollectorAgentTokenFilter filter = new EasyCollectorAgentTokenFilter(providerOf(service));
    MockHttpServletRequest req = request("POST", "/api/admin/easy/collector-agent/heartbeat");
    req.addHeader("Authorization", "Bearer " + TOKEN);
    List<Object> seen = new ArrayList<>();
    FilterChain chain = (request, response) ->
        seen.add(SecurityContextHolder.getContext().getAuthentication());

    filter.doFilter(req, new MockHttpServletResponse(), chain);

    assertThat(seen).hasSize(1);
    assertThat(seen.get(0)).isNotNull();
    assertThat(((org.springframework.security.core.Authentication) seen.get(0)).getPrincipal())
        .isEqualTo(new EasyCollectorPrincipal(7L, 1L));
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void givenUnknownToken_whenFilter_thenNoAuthentication() throws Exception {
    EasyLocalCollectorService service = mock(EasyLocalCollectorService.class);
    when(service.authenticate(any())).thenReturn(Optional.empty());
    EasyCollectorAgentTokenFilter filter = new EasyCollectorAgentTokenFilter(providerOf(service));
    MockHttpServletRequest req = request("POST", "/api/admin/easy/collector-agent/heartbeat");
    req.addHeader("Authorization", "Bearer wrong-token");
    List<Object> seen = new ArrayList<>();
    FilterChain chain = (request, response) ->
        seen.add(SecurityContextHolder.getContext().getAuthentication());

    filter.doFilter(req, new MockHttpServletResponse(), chain);

    assertThat(seen).containsExactly((Object) null);
  }

  @Test
  void givenRevokedCollectorToken_whenFilter_thenNoAuthentication() throws Exception {
    // service.authenticate already filters REVOKED collectors out (empty) — verified at the
    // filter level: a revoked collector's token must behave exactly like an unknown one.
    EasyLocalCollectorService service = mock(EasyLocalCollectorService.class);
    when(service.authenticate(TOKEN)).thenReturn(Optional.empty());
    EasyCollectorAgentTokenFilter filter = new EasyCollectorAgentTokenFilter(providerOf(service));
    MockHttpServletRequest req = request("GET", "/api/admin/easy/collector-agent/next-task");
    req.addHeader("Authorization", "Bearer " + TOKEN);
    List<Object> seen = new ArrayList<>();
    FilterChain chain = (request, response) ->
        seen.add(SecurityContextHolder.getContext().getAuthentication());

    filter.doFilter(req, new MockHttpServletResponse(), chain);

    assertThat(seen).containsExactly((Object) null);
  }

  @Test
  void givenNoHeader_whenFilter_thenNoAuthentication() throws Exception {
    EasyLocalCollectorService service = mock(EasyLocalCollectorService.class);
    EasyCollectorAgentTokenFilter filter = new EasyCollectorAgentTokenFilter(providerOf(service));
    MockHttpServletRequest req = request("GET", "/api/admin/easy/collector-agent/next-task");
    List<Object> seen = new ArrayList<>();
    FilterChain chain = (request, response) ->
        seen.add(SecurityContextHolder.getContext().getAuthentication());

    filter.doFilter(req, new MockHttpServletResponse(), chain);

    assertThat(seen).containsExactly((Object) null);
  }

  @Test
  void givenValidTokenOnNonAllowlistedAdminPath_whenFilter_thenNoAuthentication() throws Exception {
    // register/start/cancel 등 admin 관리 endpoint는 collector 토큰으로 인증되면 안 된다.
    EasyLocalCollectorService service = mock(EasyLocalCollectorService.class);
    when(service.authenticate(TOKEN)).thenReturn(Optional.of(collector(7L)));
    EasyCollectorAgentTokenFilter filter = new EasyCollectorAgentTokenFilter(providerOf(service));
    String[] nonAllowlisted = {
        "/api/admin/easy/collector/register",
        "/api/admin/easy/collector/tasks",
        "/api/admin/easy/collector/tasks/1/cancel"
    };
    for (String uri : nonAllowlisted) {
      MockHttpServletRequest req = request("POST", uri);
      req.addHeader("Authorization", "Bearer " + TOKEN);
      List<Object> seen = new ArrayList<>();
      FilterChain chain = (request, response) ->
          seen.add(SecurityContextHolder.getContext().getAuthentication());

      filter.doFilter(req, new MockHttpServletResponse(), chain);

      assertThat(seen).as(uri).containsExactly((Object) null);
    }
  }

  @Test
  void givenExistingAuthentication_whenFilter_thenPreserved() throws Exception {
    EasyLocalCollectorService service = mock(EasyLocalCollectorService.class);
    when(service.authenticate(TOKEN)).thenReturn(Optional.of(collector(7L)));
    EasyCollectorAgentTokenFilter filter = new EasyCollectorAgentTokenFilter(providerOf(service));
    var existing = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
        "admin", null,
        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
    SecurityContextHolder.getContext().setAuthentication(existing);
    MockHttpServletRequest req = request("POST", "/api/admin/easy/collector-agent/heartbeat");
    req.addHeader("Authorization", "Bearer " + TOKEN);
    List<Object> seen = new ArrayList<>();
    FilterChain chain = (request, response) ->
        seen.add(SecurityContextHolder.getContext().getAuthentication());

    filter.doFilter(req, new MockHttpServletResponse(), chain);

    assertThat(seen).containsExactly(existing);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(existing);
  }

  @Test
  void givenNoServiceBean_whenFilter_thenNoAuthentication() throws Exception {
    // @WebMvcTest 슬라이스처럼 서비스 빈이 없는 컨텍스트 — fail-closed.
    EasyCollectorAgentTokenFilter filter = new EasyCollectorAgentTokenFilter(providerOf(null));
    MockHttpServletRequest req = request("POST", "/api/admin/easy/collector-agent/heartbeat");
    req.addHeader("Authorization", "Bearer " + TOKEN);
    List<Object> seen = new ArrayList<>();
    FilterChain chain = (request, response) ->
        seen.add(SecurityContextHolder.getContext().getAuthentication());

    filter.doFilter(req, new MockHttpServletResponse(), chain);

    assertThat(seen).containsExactly((Object) null);
  }
}
