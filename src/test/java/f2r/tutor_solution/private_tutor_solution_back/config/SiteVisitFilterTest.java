package f2r.tutor_solution.private_tutor_solution_back.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.SiteVisitRecordingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("SiteVisitFilter — 실제 HTML 페이지 응답에서만 방문을 기록한다")
class SiteVisitFilterTest {

  private SiteVisitRecordingService recordingService;
  private SiteVisitFilter filter;

  @BeforeEach
  void setUp() {
    recordingService = mock(SiteVisitRecordingService.class);
    ObjectProvider<SiteVisitRecordingService> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(recordingService);
    filter = new SiteVisitFilter(provider);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("GET /wiki 200 text/html → 기록(비로그인, wiki=true)")
  void wikiHtmlPage_guest_recorded() throws Exception {
    MockHttpServletRequest request = htmlGet("/wiki");
    MockHttpServletResponse response = htmlResponse();

    filter.doFilter(request, response, chainSettingHtml(response));

    ArgumentCaptor<Boolean> wikiCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(recordingService).record(eq("/wiki"), wikiCaptor.capture(), anyString(), isNull(), isNull());
    assertThat(wikiCaptor.getValue()).isTrue();
  }

  @Test
  @DisplayName("GET /community 200 text/html → 기록(wiki=false)")
  void nonWikiHtmlPage_recorded() throws Exception {
    MockHttpServletRequest request = htmlGet("/community");
    MockHttpServletResponse response = htmlResponse();

    filter.doFilter(request, response, chainSettingHtml(response));

    verify(recordingService).record(eq("/community"), eq(false), anyString(), isNull(), isNull());
  }

  @Test
  @DisplayName("로그인 사용자는 visitorKey=U:{userId}, role/userId 전달")
  void loggedInUser_usesUserIdVisitorKey() throws Exception {
    JwtTokenProvider.AuthenticatedPrincipal principal =
        new JwtTokenProvider.AuthenticatedPrincipal(7L, "tutor01", "TUTOR");
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        principal, null, List.of(new SimpleGrantedAuthority("ROLE_TUTOR"))));

    MockHttpServletRequest request = htmlGet("/wiki/analysis");
    MockHttpServletResponse response = htmlResponse();

    filter.doFilter(request, response, chainSettingHtml(response));

    verify(recordingService).record("/wiki/analysis", true, "U:7", 7L, "TUTOR");
  }

  @Test
  @DisplayName("정적 리소스(/css/**)는 기록하지 않는다")
  void staticAsset_notRecorded() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/css/app.css");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setContentType("text/css");
    response.setStatus(HttpServletResponse.SC_OK);

    filter.doFilter(request, response, (req, res) -> {});

    verify(recordingService, never()).record(anyString(), anyBoolean(), anyString(), any(), any());
  }

  @Test
  @DisplayName("/api/** 는 기록하지 않는다")
  void apiCall_notRecorded() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/wiki/originals");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setContentType("application/json");
    response.setStatus(HttpServletResponse.SC_OK);

    filter.doFilter(request, response, (req, res) -> {});

    verify(recordingService, never()).record(anyString(), anyBoolean(), anyString(), any(), any());
  }

  @Test
  @DisplayName("POST 요청은 기록하지 않는다")
  void postRequest_notRecorded() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/wiki");
    MockHttpServletResponse response = htmlResponse();

    filter.doFilter(request, response, chainSettingHtml(response));

    verify(recordingService, never()).record(anyString(), anyBoolean(), anyString(), any(), any());
  }

  @Test
  @DisplayName("응답이 text/html이 아니면(예: 리다이렉트, JSON) 기록하지 않는다")
  void nonHtmlResponse_notRecorded() throws Exception {
    MockHttpServletRequest request = htmlGet("/wiki");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {
      ((HttpServletResponse) res).setStatus(HttpServletResponse.SC_FOUND);
    });

    verify(recordingService, never()).record(anyString(), anyBoolean(), anyString(), any(), any());
  }

  private MockHttpServletRequest htmlGet(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.setRequestURI(uri);
    return request;
  }

  private MockHttpServletResponse htmlResponse() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    return response;
  }

  private FilterChain chainSettingHtml(MockHttpServletResponse response) {
    return (req, res) -> {
      response.setContentType("text/html;charset=UTF-8");
      response.setStatus(HttpServletResponse.SC_OK);
    };
  }
}
