package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * NewStart는 로그인 방법이 "네이버로 로그인" 하나뿐이다. 회원가입·학생/강사 선택·ID/PW 가입은
 * 존재하지 않는다. 로그인 시작점은 공식 Spring Security OAuth2 Client 표준 흐름
 * {@code /oauth2/authorization/naver} 하나로 통일한다(#341) — 손수 작성한 V1
 * {@code /oauth/naver/start}·V2 {@code /auth/naver/v2/start}는 코드 자체는 남아 있지만
 * NewStart UI는 더 이상 그쪽을 가리키지 않는다(V2는 실사용자 검증에서 프로필 파싱 실패로
 * 매번 로그인에 실패했고, 공식 흐름만 실사용자 로그인 완주가 로그로 확인됐다). 실제 Thymeleaf
 * 렌더로 fragments/_newstart_auth 치환까지 확인해 fragment 참조 오류도 함께 잡는다.
 *
 * <p>BuildIt/Wiki 등 다른 서비스가 쓰는 공용 fragments/_account_menu.html(회원가입/학생·강사
 * 가입/ID·PW 로그인 모달)은 이 테스트의 대상이 아니다 — 그쪽은 그대로 유지되어야 한다.
 */
@DisplayName("NewStart 인증 UI 계약 — 네이버 로그인만 존재")
class NewStartAuthUiContractTest {

  private static TemplateEngine engine() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.addDialect(new LayoutDialect());
    return engine;
  }

  @Test
  @DisplayName("/newstart 홈: 네이버 로그인 링크만 있고 회원가입·공용 모달은 없다")
  void indexHasNaverOnlyAuth() {
    String html = engine().process("newstart/index", new Context());
    assertNaverOnlyAuth(html);
  }

  @Test
  @DisplayName("/newstart/chat: 네이버 로그인 링크만 있고 회원가입·공용 모달은 없다")
  void chatHasNaverOnlyAuth() {
    String html = engine().process("newstart/chat", new Context());
    assertNaverOnlyAuth(html);
  }

  private void assertNaverOnlyAuth(String html) {
    assertThat(html).doesNotContain("th:replace"); // 치환 잔여물 없음

    // 네이버 로그인 링크 존재 — 공식 Spring Security OAuth2 Client 표준 흐름 하나로 통일(#341)
    assertThat(html).contains("href=\"/oauth2/authorization/naver\"");
    assertThat(html).doesNotContain("href=\"/auth/naver/v2/start\"");
    assertThat(html).doesNotContain("href=\"/oauth/naver/start\"");
    assertThat(html).contains("네이버로 로그인");

    // 공용 회원가입/로그인 모달·구버전 네이버 버튼 주입 대상 없음
    assertThat(html).doesNotContain("data-auth-signup");
    assertThat(html).doesNotContain("data-login-modal");
    assertThat(html).doesNotContain("login-modal-social");
    assertThat(html).doesNotContain("/js/naver_login.js");

    // 학생/강사 선택 문구 없음(계정 메뉴 등 NewStart 자체 마크업 범위 내)
    assertThat(html).doesNotContain("학생 회원가입");
    assertThat(html).doesNotContain("강사 회원가입");
  }
}
