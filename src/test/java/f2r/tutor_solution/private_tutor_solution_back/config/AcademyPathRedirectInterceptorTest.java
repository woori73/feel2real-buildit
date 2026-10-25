package f2r.tutor_solution.private_tutor_solution_back.config;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AcademyPathRedirectInterceptorTest {

  private final AcademyRepository academyRepository = mock(AcademyRepository.class);

  private MockMvc mockMvcWithBaseDomain(String baseDomain) {
    AcademyPathRedirectInterceptor interceptor =
        new AcademyPathRedirectInterceptor(providerOf(academyRepository), new PublicSiteProperties(baseDomain));
    return MockMvcBuilders.standaloneSetup(new AcademyPageController())
        .addInterceptors(interceptor)
        .build();
  }

  @Test
  void pathWithHandle_redirects301ToSubdomain() throws Exception {
    // given: handle 보유 academy
    when(academyRepository.findBySlug("minji-eng")).thenReturn(Optional.of(academy("minji")));
    MockMvc mockMvc = mockMvcWithBaseDomain("feel2real.co.kr");

    // when & then
    mockMvc.perform(get("/academies/minji-eng"))
        .andExpect(status().isMovedPermanently())
        .andExpect(header().string("Location", "https://minji.feel2real.co.kr/"));
  }

  @Test
  void hangulSlugPercentEncoded_redirects301() throws Exception {
    // given: 한글 slug — 요청 URI는 percent-encoded로 도착, 인터셉터가 디코드해 조회해야 한다
    when(academyRepository.findBySlug("압구정에듀홀릭")).thenReturn(Optional.of(academy("eduholic")));
    MockMvc mockMvc = mockMvcWithBaseDomain("feel2real.co.kr");

    // when & then: URI 객체로 전달해야 %인코딩이 그대로 유지된다(get(String)은 재인코딩함)
    String encoded = java.net.URLEncoder.encode("압구정에듀홀릭", java.nio.charset.StandardCharsets.UTF_8);
    mockMvc.perform(get(java.net.URI.create("/academies/" + encoded)))
        .andExpect(status().isMovedPermanently())
        .andExpect(header().string("Location", "https://eduholic.feel2real.co.kr/"));
  }

  @Test
  void pathWithoutHandle_passesThrough200() throws Exception {
    // given: handle 없는 전환기 academy → 경로형 유지
    when(academyRepository.findBySlug("legacy")).thenReturn(Optional.of(academy(null)));
    MockMvc mockMvc = mockMvcWithBaseDomain("feel2real.co.kr");

    // when & then
    mockMvc.perform(get("/academies/legacy")).andExpect(status().isOk());
  }

  @Test
  void baseDomainBlank_neverRedirects() throws Exception {
    // given: local/test → base-domain 공란 → 리다이렉트 안 함(repo 조회도 안 함)
    MockMvc mockMvc = mockMvcWithBaseDomain("");

    // when & then
    mockMvc.perform(get("/academies/minji-eng")).andExpect(status().isOk());
  }

  @Test
  void givenMissingOrPrivateAcademy_whenRequested_then404WithoutRedirect() throws Exception {
    MockMvc mvc = mockMvcWithBaseDomain("feel2real.co.kr");
    mvc.perform(get("/academies/deleted")).andExpect(status().isNotFound())
        .andExpect(header().doesNotExist("Location"));
    Academy hidden = academy("hidden"); hidden.updateVisibility(false);
    when(academyRepository.findBySlug("hidden")).thenReturn(Optional.of(hidden));
    mvc.perform(get("/academies/hidden")).andExpect(status().isNotFound());
    Academy draft = Academy.builder().status(f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus.DRAFT).slug("draft").name("draft").build();
    when(academyRepository.findBySlug("draft")).thenReturn(Optional.of(draft));
    mvc.perform(get("/academies/draft")).andExpect(status().isNotFound());
  }

  private Academy academy(String handle) {
    return Academy.builder()
        .status(f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus.PUBLISHED)
        .slug("minji-eng")
        .handle(handle)
        .name("n")
        .logoStorageKey("k")
        .intro("i")
        .build();
  }

  @RestController
  static class AcademyPageController {
    @GetMapping("/academies/{slug}")
    @ResponseBody
    String page(@PathVariable String slug) {
      return slug;
    }
  }

  @SuppressWarnings("unchecked")
  private static org.springframework.beans.factory.ObjectProvider<AcademyRepository> providerOf(
      AcademyRepository repo) {
    org.springframework.beans.factory.ObjectProvider<AcademyRepository> provider =
        mock(org.springframework.beans.factory.ObjectProvider.class);
    org.mockito.Mockito.when(provider.getObject()).thenReturn(repo);
    return provider;
  }

}
