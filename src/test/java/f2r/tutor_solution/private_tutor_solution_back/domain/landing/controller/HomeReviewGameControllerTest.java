package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("HomeReviewGameController — 고1 10월 복습 게임 사이트 안 실행")
class HomeReviewGameControllerTest {

  static final String BASE = HomeReviewGameController.PAGE_PATH;
  MockMvc mvc;

  @BeforeEach void setup() {
    mvc = MockMvcBuilders.standaloneSetup(new HomeReviewGameController()).build();
  }

  @Test
  @DisplayName("given 게임 주소 when 열면 then 같은 출처만 허용하는 CSP와 함께 시작 페이지를 준다")
  void page_hasSameOriginCsp() throws Exception {
    // given / when / then
    mvc.perform(get(BASE)).andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/html"))
        .andExpect(header().string("Content-Security-Policy", HomeReviewGameController.PAGE_CSP))
        .andExpect(header().string("Cache-Control", "no-cache"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("unity-canvas")));
    assertThat(HomeReviewGameController.PAGE_CSP).contains("default-src 'self'").contains("frame-ancestors 'self'")
        .doesNotContain("*").doesNotContain("https:");
  }

  @Test
  @DisplayName("given 끝 슬래시 없는 주소 when 열면 then 상대 경로가 맞는 슬래시 주소로 보낸다")
  void pageWithoutSlash_redirects() throws Exception {
    // given / when / then
    mvc.perform(get("/games/g1-oct-review/v1")).andExpect(status().isMovedPermanently())
        .andExpect(header().string("Location", BASE));
  }

  @Test
  @DisplayName("given br 지원 브라우저 when 빌드 파일을 받으면 then 미리 압축한 원본을 형식·압축·긴 캐시 헤더와 함께 준다")
  void build_servesPrecompressedFiles() throws Exception {
    for (var entry : HomeReviewGameController.BUILD_FILES.entrySet()) {
      // given
      byte[] expected = new ClassPathResource("games/g1-oct-review/v1/Build/" + entry.getKey() + ".br")
          .getContentAsByteArray();
      // when / then
      mvc.perform(get(BASE + "Build/" + entry.getKey()).header("Accept-Encoding", "gzip, deflate, br"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(entry.getValue()))
          .andExpect(header().string("Content-Encoding", "br"))
          .andExpect(header().string("Vary", "Accept-Encoding"))
          .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
          .andExpect(content().bytes(expected));
    }
  }

  @Test
  @DisplayName("given 목록에 없는 파일 when 요청하면 then 404로 다른 파일을 내주지 않는다")
  void build_rejectsUnknownFiles() throws Exception {
    // given / when / then
    for (String name : new String[] {"WebGLBuild.wasm.br", "index.html", "..%2Findex.html", "secret.txt"}) {
      mvc.perform(get(BASE + "Build/" + name).header("Accept-Encoding", "br")).andExpect(status().isNotFound());
    }
  }

  @Test
  @DisplayName("given br을 받지 않는 브라우저 when 빌드 파일을 받으면 then 깨진 파일 대신 406 안내를 준다")
  void build_withoutBrotli_isNotAcceptable() throws Exception {
    // given / when / then
    mvc.perform(get(BASE + "Build/WebGLBuild.wasm").header("Accept-Encoding", "gzip"))
        .andExpect(status().isNotAcceptable())
        .andExpect(header().doesNotExist("Content-Encoding"));
  }

  @Test
  @DisplayName("given 여러 Accept-Encoding 값 when br 허용을 판단하면 then q=0 거절과 없음은 false다")
  void acceptsBrotli_parsesQuality() {
    // given / when / then
    assertThat(HomeReviewGameController.acceptsBrotli("gzip, deflate, br, zstd")).isTrue();
    assertThat(HomeReviewGameController.acceptsBrotli("BR;q=0.5")).isTrue();
    assertThat(HomeReviewGameController.acceptsBrotli("br;q=0")).isFalse();
    assertThat(HomeReviewGameController.acceptsBrotli("br; q=0.000")).isFalse();
    assertThat(HomeReviewGameController.acceptsBrotli("gzip")).isFalse();
    assertThat(HomeReviewGameController.acceptsBrotli(null)).isFalse();
  }

  @Test
  @DisplayName("given 압축 빌드 when 크기를 보면 then 전체 전송량이 12MB 이하로 유지된다")
  void build_staysSmall() throws IOException {
    // given
    long total = 0;
    // when
    for (String name : HomeReviewGameController.BUILD_FILES.keySet()) {
      total += new ClassPathResource("games/g1-oct-review/v1/Build/" + name + ".br").contentLength();
    }
    // then
    assertThat(total).isPositive().isLessThan(12L * 1024 * 1024);
  }
}
