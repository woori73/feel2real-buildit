package f2r.tutor_solution.private_tutor_solution_back.domain.sns.instagram;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpInstagramApiTest {

  @Test
  @DisplayName("given 앱 설정 when authorizeUrl then 콘텐츠 게시 권한·돌아올 주소·state 포함")
  void authorizeUrl() {
    // given
    SnsProperties p = new SnsProperties();
    p.getMeta().setAppId("123");
    p.setPublicBaseUrl("https://feel2real.co.kr");
    // when
    String url = new HttpInstagramApi(p).authorizeUrl("st8");
    // then
    assertThat(url).startsWith("https://www.instagram.com/oauth/authorize?client_id=123")
        .contains("redirect_uri=https%3A%2F%2Ffeel2real.co.kr%2Fadmin%2Fsns%2Faccounts%2Fcallback")
        .contains("instagram_business_content_publish").contains("state=st8");
  }

  @Test
  @DisplayName("given 토큰 같은 긴 문자열이 섞인 오류 when scrub then 가림")
  void scrub_hidesTokens() {
    // given
    String msg = "Invalid OAuth access token IGAAabcdefghijklmnopqrstuvwxyz0123456789ABCDEFGH";
    // when / then
    assertThat(HttpInstagramApi.scrub(msg)).doesNotContain("IGAAabcdef").contains("***");
  }

  @Test
  @DisplayName("given 한글 문구 when form then URL 인코딩")
  void form_encodes() {
    // given
    Map<String, String> m = new LinkedHashMap<>();
    m.put("caption", "새출발 #채무조정");
    // when / then
    assertThat(HttpInstagramApi.form(m)).isEqualTo("caption=%EC%83%88%EC%B6%9C%EB%B0%9C+%23%EC%B1%84%EB%AC%B4%EC%A1%B0%EC%A0%95");
  }
}
