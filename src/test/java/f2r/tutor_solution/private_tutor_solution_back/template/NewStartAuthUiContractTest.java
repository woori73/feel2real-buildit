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
 * STEP 4(2026-09-21) — 네이버 로그인 버튼을 Spring Security OAuth2 Client의 표준 authorization
 * endpoint(/oauth2/authorization/naver, registrationId=naver)로 연결했다. 이 테스트는 그 활성
 * 상태를 고정한다: NewStart는 여전히 회원가입·학생/강사 선택·ID/PW 로그인이 없고(방향 유지),
 * 로그인 버튼은 고정된 하나의 endpoint만 가리키며(파라미터로 목적지를 바꿀 수 없음 — open
 * redirect 없음) 더 이상 disabled placeholder가 아니다.
 *
 * <p>BuildIt/Wiki 등 다른 서비스가 쓰는 공용 fragments/_account_menu.html(회원가입/학생·강사
 * 가입/ID·PW 로그인 모달)은 이 테스트의 대상이 아니다 — 그쪽은 그대로 유지되어야 한다.
 */
@DisplayName("NewStart 인증 UI 계약 — 네이버 로그인 활성화(STEP 4)")
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
  @DisplayName("/newstart 홈: 로그인 버튼이 /oauth2/authorization/naver로 연결되고 회원가입·공용 모달·구 네이버 endpoint가 없다")
  void indexHasActiveNaverLoginLink() {
    String html = engine().process("newstart/index", new Context());
    assertActiveNaverLogin(html);
  }

  @Test
  @DisplayName("/newstart/chat: 로그인 버튼이 /oauth2/authorization/naver로 연결되고 회원가입·공용 모달·구 네이버 endpoint가 없다")
  void chatHasActiveNaverLoginLink() {
    String html = engine().process("newstart/chat", new Context());
    assertActiveNaverLogin(html);
  }

  private void assertActiveNaverLogin(String html) {
    assertThat(html).doesNotContain("th:replace"); // 치환 잔여물 없음

    // 로그인 버튼은 고정된 공식 OAuth2 authorization endpoint 하나만 가리킨다
    assertThat(html).contains("href=\"/oauth2/authorization/naver\"");
    assertThat(html).contains(">네이버 로그인</a>");
    assertThat(html).doesNotContain("네이버 로그인 준비 중");
    assertThat(html).doesNotContain("aria-disabled=\"true\"");

    // 과거/미병합 네이버 로그인 endpoint 중 어느 것도 남아있지 않음
    assertThat(html).doesNotContain("href=\"/oauth/naver/start\"");
    assertThat(html).doesNotContain("href=\"/auth/naver/v2/start\"");
    assertThat(html).doesNotContain("/js/naver_login.js");

    // 공용 회원가입/로그인 모달 없음(NewStart는 여전히 회원가입/ID-PW 없음)
    assertThat(html).doesNotContain("data-auth-signup");
    assertThat(html).doesNotContain("data-login-modal");
    assertThat(html).doesNotContain("login-modal-social");

    // 학생/강사 선택 문구 없음
    assertThat(html).doesNotContain("학생 회원가입");
    assertThat(html).doesNotContain("강사 회원가입");
  }
}
