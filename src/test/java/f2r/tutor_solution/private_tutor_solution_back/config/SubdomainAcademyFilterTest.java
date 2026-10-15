package f2r.tutor_solution.private_tutor_solution_back.config;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubdomainAcademyFilterTest {

  private final AcademyRepository academyRepository = mock(AcademyRepository.class);

  private MockMvc mockMvcWithBaseDomain(String baseDomain) {
    SubdomainAcademyFilter filter =
        new SubdomainAcademyFilter(providerOf(academyRepository), new PublicSiteProperties(baseDomain));
    return MockMvcBuilders.standaloneSetup(new RootController()).addFilter(filter).build();
  }

  @Test
  void subdomainRoot_forwardsToSubdomainHomeWhenHandleResolves() throws Exception {
    // given: minji.feel2real.co.kr → handle=minji → academy(slug=minji-eng)
    when(academyRepository.findByHandle("minji"))
        .thenReturn(Optional.of(academy("minji-eng", "minji")));
    MockMvc mockMvc = mockMvcWithBaseDomain("feel2real.co.kr");

    // when & then: root('/')를 공개 렌더 지점으로 forward
    mockMvc.perform(get("/").with(withServerName("minji.feel2real.co.kr")))
        .andExpect(forwardedUrl("/subdomain-home"));
  }

  @Test
  void subdomainRoot_returns404WhenHandleUnknown() throws Exception {
    // given: 존재하지 않는 handle
    when(academyRepository.findByHandle("ghost")).thenReturn(Optional.empty());
    MockMvc mockMvc = mockMvcWithBaseDomain("feel2real.co.kr");

    // when & then
    mockMvc.perform(get("/").with(withServerName("ghost.feel2real.co.kr")))
        .andExpect(status().isNotFound());
  }

  @Test
  void apexHost_passesThroughWithoutForward() throws Exception {
    // given: apex(base-domain 자체) → 서브도메인 아님
    MockMvc mockMvc = mockMvcWithBaseDomain("feel2real.co.kr");

    // when & then: 필터가 관여하지 않고 landing 컨트롤러로 통과
    mockMvc.perform(get("/").with(withServerName("feel2real.co.kr")))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl(null));
  }

  @Test
  void commonPathOnSubdomain_redirectsToApexPermanently() throws Exception {
    // given: 공통 페이지 경로는 학원 서브도메인에 속하지 않는다
    MockMvc mockMvc = mockMvcWithBaseDomain("feel2real.co.kr");

    // when & then: apex 동일 path로 301
    mockMvc.perform(get("/home").with(withServerName("minji.feel2real.co.kr")))
        .andExpect(status().isMovedPermanently())
        .andExpect(header().string("Location", "https://feel2real.co.kr/home"));
  }

  @Test
  void commonPathOnSubdomain_preservesQueryStringOnRedirect() throws Exception {
    // given: query string이 붙은 공통 경로
    MockMvc mockMvc = mockMvcWithBaseDomain("feel2real.co.kr");

    // when & then: query가 보존된 채 apex로 301
    mockMvc.perform(get("/community").queryParam("tab", "free")
            .with(withServerName("minji.feel2real.co.kr")))
        .andExpect(status().isMovedPermanently())
        .andExpect(header().string("Location", "https://feel2real.co.kr/community?tab=free"));
  }

  @Test
  void archivedPathOnSubdomain_redirectsToApexWithoutLosingAlias() throws Exception {
    // given: host canonicalization owns the first decision for archived paths
    MockMvc mockMvc = mockMvcWithBaseDomain("feel2real.co.kr");

    // when & then: alias path and query remain intact for the subsequent apex request
    mockMvc.perform(get("/archived/api/admin/probe").queryParam("mode", "kept")
            .with(withServerName("minji.feel2real.co.kr")))
        .andExpect(status().isMovedPermanently())
        .andExpect(header().string(
            "Location", "https://feel2real.co.kr/archived/api/admin/probe?mode=kept"));
  }

  @Test
  void staticAssetOnSubdomain_passesThroughWithoutRedirect() throws Exception {
    // given: 학원 페이지가 같은 호스트에서 로드하는 정적 자산
    MockMvc mockMvc = mockMvcWithBaseDomain("feel2real.co.kr");

    // when & then: 리다이렉트하면 페이지가 깨지므로 통과
    mockMvc.perform(get("/css/academy_public.css").with(withServerName("minji.feel2real.co.kr")))
        .andExpect(status().isOk());
  }

  @Test
  void apiPathOnSubdomain_passesThroughWithoutRedirect() throws Exception {
    // given: 학원 페이지가 호출하는 API
    MockMvc mockMvc = mockMvcWithBaseDomain("feel2real.co.kr");

    // when & then: 통과
    mockMvc.perform(get("/api/academies/x").with(withServerName("minji.feel2real.co.kr")))
        .andExpect(status().isOk());
  }

  @Test
  void lmsPathOnSubdomain_isNormalizedToLocalFormWithoutRedirect() throws Exception {
    // given: 서브도메인의 짧은 LMS 경로. 301하면 apex에 없는 주소로 보내 화면이 통째로 깨진다.
    MockMvc mockMvc = mockMvcWithBaseDomain("feel2real.co.kr");

    // when & then: /lms/my → /lms/minji/my 로 정규화돼 컨트롤러에 도달한다
    mockMvc.perform(get("/lms/my").with(withServerName("minji.feel2real.co.kr")))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl(null));
  }

  @Test
  void lmsRootOnSubdomain_isNormalizedToHandleOnlyPath() throws Exception {
    // given: feature 없는 LMS 루트
    MockMvc mockMvc = mockMvcWithBaseDomain("feel2real.co.kr");

    // when & then: /lms → /lms/minji
    mockMvc.perform(get("/lms").with(withServerName("minji.feel2real.co.kr")))
        .andExpect(status().isOk());
  }

  @Test
  void lmsPathOnApex_isNotNormalized() throws Exception {
    // given: apex는 경로에 handle이 이미 들어 있다 — 또 끼워 넣으면 안 된다
    MockMvc mockMvc = mockMvcWithBaseDomain("feel2real.co.kr");

    // when & then: 그대로 통과
    mockMvc.perform(get("/lms/minji/my").with(withServerName("feel2real.co.kr")))
        .andExpect(status().isOk());
  }

  @Test
  void nonLmsPathStartingWithLms_isNotTreatedAsLmsPath() throws Exception {
    // given: /lmsfoo는 LMS 경로가 아니다(prefix 매칭이 세그먼트 경계를 지켜야 한다)
    MockMvc mockMvc = mockMvcWithBaseDomain("feel2real.co.kr");

    // when & then: 공통 경로 취급 → apex로 301
    mockMvc.perform(get("/lmsfoo").with(withServerName("minji.feel2real.co.kr")))
        .andExpect(status().isMovedPermanently())
        .andExpect(header().string("Location", "https://feel2real.co.kr/lmsfoo"));
  }

  @Test
  void apexCommonPath_isNotRedirected() throws Exception {
    // given: apex의 공통 경로는 정상 서빙 대상(기존 랜딩/홈 보존)
    MockMvc mockMvc = mockMvcWithBaseDomain("feel2real.co.kr");

    // when & then: 필터가 관여하지 않는다
    mockMvc.perform(get("/home").with(withServerName("feel2real.co.kr")))
        .andExpect(status().isOk());
  }

  @Test
  void baseDomainBlank_doesNotRedirectCommonPath() throws Exception {
    // given: base-domain 미설정(local/test)
    MockMvc mockMvc = mockMvcWithBaseDomain("");

    // when & then: 서브도메인처럼 보여도 301하지 않고 통과
    mockMvc.perform(get("/home").with(withServerName("minji.feel2real.co.kr")))
        .andExpect(status().isOk());
  }

  @Test
  void baseDomainBlank_disablesFilterEntirely() throws Exception {
    // given: base-domain 미설정(local/test) → 완전 no-op
    MockMvc mockMvc = mockMvcWithBaseDomain("");

    // when & then: 서브도메인처럼 보여도 통과(forward 없음)
    mockMvc.perform(get("/").with(withServerName("minji.feel2real.co.kr")))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl(null));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor withServerName(String name) {
    return request -> {
      request.setServerName(name);
      return request;
    };
  }

  private Academy academy(String slug, String handle) {
    return Academy.builder()
        .slug(slug)
        .handle(handle)
        .name("n")
        .logoStorageKey("k")
        .intro("i")
        .build();
  }

  @RestController
  static class RootController {
    @GetMapping("/")
    @ResponseBody
    String root() {
      return "landing";
    }

    @GetMapping("/home")
    @ResponseBody
    String home() {
      return "home";
    }

    @GetMapping("/community")
    @ResponseBody
    String community() {
      return "community";
    }

    @GetMapping("/css/academy_public.css")
    @ResponseBody
    String css() {
      return "css";
    }

    @GetMapping("/api/academies/{id}")
    @ResponseBody
    String api() {
      return "api";
    }

    // LMS는 정규화된 로컬 형태(/lms/{handle}/...) 한 벌만 매핑한다.
    @GetMapping("/lms/{handle}")
    @ResponseBody
    String lmsRoot(@org.springframework.web.bind.annotation.PathVariable String handle) {
      return "lms:" + handle;
    }

    @GetMapping("/lms/{handle}/my")
    @ResponseBody
    String lmsMy(@org.springframework.web.bind.annotation.PathVariable String handle) {
      return "lms-my:" + handle;
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
