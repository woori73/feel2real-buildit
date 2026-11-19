package f2r.tutor_solution.private_tutor_solution_back.domain.csat;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@DisplayName("CsatEnglishPageController — 2027 수능 영어 모션 해설 화면 · 로그인 사용자 PDF 무료 다운로드")
class CsatEnglishPageControllerTest {

  static final AuthenticatedPrincipal MEMBER = new AuthenticatedPrincipal(7L, "student", "STUDENT");

  @Test
  @DisplayName("given 누구나 when 해설 화면 then 공개 템플릿")
  void pageIsPublic() {
    // given
    var controller = new CsatEnglishPageController(new ByteArrayResource(new byte[0]));

    // when
    String view = controller.page();

    // then
    assertThat(view).isEqualTo("public/csat_2027_english");
  }

  @Test
  @DisplayName("given 로그인 안 함 when PDF then 네이버 로그인으로 보내고 로그인 뒤 해설 화면(download=1)으로 돌아옴")
  void anonymousIsSentToLogin() throws Exception {
    // given
    var controller = new CsatEnglishPageController(new ByteArrayResource("%PDF".getBytes()));

    // when
    var res = controller.pdf(null);

    // then
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(res.getHeaders().getFirst(HttpHeaders.LOCATION))
        .isEqualTo("/auth/naver/start?returnUrl=%2Fcsat%2F2027-english%3Fdownload%3D1");
    assertThat(res.getBody()).isNull();
  }

  @Test
  @DisplayName("given 로그인 사용자 when PDF then 첨부 파일(PDF, 한글 파일명, 캐시 안 함)")
  void memberGetsPdf() throws Exception {
    // given
    Resource pdf = new ByteArrayResource("%PDF-1.7 test".getBytes()) {
      @Override
      public boolean exists() {
        return true;
      }
    };
    var controller = new CsatEnglishPageController(pdf);

    // when
    var res = controller.pdf(MEMBER);

    // then
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment").contains("UTF-8''2027");
    assertThat(res.getHeaders().getCacheControl()).contains("no-store");
    assertThat(res.getBody()).isSameAs(pdf);
  }

  @Test
  @DisplayName("given 파일 없음 when 로그인 사용자 PDF then 404")
  void missingFile() throws Exception {
    // given
    var controller = new CsatEnglishPageController(new ClassPathResource("private/csat/none.pdf"));

    // when & then
    assertThat(controller.pdf(MEMBER).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("given 배포 자원 when 실제 PDF then 정적 경로가 아닌 private 아래에 있다")
  void realPdfIsPrivate() {
    // given & when & then
    assertThat(new ClassPathResource(CsatEnglishPageController.PDF_PATH).exists()).isTrue();
    assertThat(new ClassPathResource("static/" + CsatEnglishPageController.PDF_PATH).exists()).isFalse();
  }
}
