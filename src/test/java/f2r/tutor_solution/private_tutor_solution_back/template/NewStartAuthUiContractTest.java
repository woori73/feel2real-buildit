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
 * 2026-09-21 cleanup — NewStart 네이버 로그인 V1({@code /oauth/naver/*})·V2
 * ({@code /auth/naver/v2/*})·공식 Spring Security OAuth2 Client 구현(PR #341, 미병합)을
 * 전부 제거하고 clean baseline으로 되돌렸다. 이 테스트는 그 baseline을 고정한다:
 * NewStart는 여전히 회원가입·학생/강사 선택·ID/PW 로그인이 없고(방향은 유지), 로그인 버튼은
 * 어떤 endpoint도 가리키지 않는 비활성 상태다. 다음 세션이 새 로그인을 구현하면 이 테스트도
 * 그 구현에 맞춰 다시 바뀌어야 한다.
 *
 * <p>BuildIt/Wiki 등 다른 서비스가 쓰는 공용 fragments/_account_menu.html(회원가입/학생·강사
 * 가입/ID·PW 로그인 모달)은 이 테스트의 대상이 아니다 — 그쪽은 그대로 유지되어야 한다.
 */
@DisplayName("NewStart 인증 UI 계약 — clean baseline(로그인 비활성)")
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
  @DisplayName("/newstart 홈: 로그인 버튼이 비활성 상태이고 회원가입·공용 모달·구 네이버 endpoint가 없다")
  void indexHasDisabledLoginPlaceholder() {
    String html = engine().process("newstart/index", new Context());
    assertCleanBaseline(html);
  }

  @Test
  @DisplayName("/newstart/chat: 로그인 버튼이 비활성 상태이고 회원가입·공용 모달·구 네이버 endpoint가 없다")
  void chatHasDisabledLoginPlaceholder() {
    String html = engine().process("newstart/chat", new Context());
    assertCleanBaseline(html);
  }

  private void assertCleanBaseline(String html) {
    assertThat(html).doesNotContain("th:replace"); // 치환 잔여물 없음

    // 로그인 버튼은 비활성 상태 문구만 있고, 실제 이동 가능한 링크는 없다
    assertThat(html).contains("네이버 로그인 준비 중");

    // 과거/미병합 네이버 로그인 endpoint 중 어느 것도 남아있지 않음
    assertThat(html).doesNotContain("href=\"/oauth/naver/start\"");
    assertThat(html).doesNotContain("href=\"/auth/naver/v2/start\"");
    assertThat(html).doesNotContain("href=\"/oauth2/authorization/naver\"");
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
