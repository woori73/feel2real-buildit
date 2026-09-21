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
 * NewStart 회원 채팅 화면(/newstart/chat) 계약. WikiPageContractTest와 동일하게 실제
 * Thymeleaf 렌더로 fragment(_head/_account_menu) 치환 오류까지 잡는다.
 */
@DisplayName("NewStart 회원 채팅 화면 계약")
class NewStartChatPageContractTest {

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
  @DisplayName("chat 템플릿이 fragment 치환까지 실제로 렌더되고 메신저 구조를 갖는다")
  void chatTemplateRendersWithFragments() {
    String html = engine().process("newstart/chat", new Context());

    assertThat(html).contains("<!DOCTYPE html>");
    assertThat(html).contains("새출발기금 부실차주");                 // 서비스명 헤더
    assertThat(html).contains("회원 채팅");                           // 채팅방 제목
    assertThat(html).contains("id=\"ns-chat-list\"");                // 메시지 목록
    assertThat(html).contains("id=\"ns-chat-form\"");                // 전송 폼
    assertThat(html).contains("id=\"ns-chat-input\"");               // 입력창
    assertThat(html).contains("/js/newstart-chat.js");               // 채팅 전용 JS
    assertThat(html).contains("id=\"ns-chat-login-alert\"");          // 네이버 OAuth 실패/취소 알림 영역
    assertThat(html).contains("메시지를 입력하세요");                  // 하단 입력 placeholder
    assertThat(html).doesNotContain("th:replace");                   // 치환 잔여물 없음

    // NewStart는 로그인 방법이 "네이버로 로그인" 하나뿐이다 — 공용 ID/PW 모달 기반 로그인은
    // 더 이상 쓰지 않는다. 로그인 시작점은 공식 Spring Security OAuth2 Client 표준 흐름
    // /oauth2/authorization/naver 하나로 통일한다(#341 — V2 /auth/naver/v2/start는 실사용자
    // 검증에서 프로필 파싱 실패로 매번 로그인에 실패해 UI 연결에서 제외했다).
    assertThat(html).contains("href=\"/oauth2/authorization/naver\""); // 네이버 로그인 공식 흐름 직결 링크
    assertThat(html).doesNotContain("href=\"/auth/naver/v2/start\"");
    assertThat(html).doesNotContain("href=\"/oauth/naver/start\"");
    assertThat(html).doesNotContain("data-auth-login");               // 공용 모달 오픈 트리거 없음
    assertThat(html).doesNotContain("data-login-modal");              // 공용 로그인 모달 미포함
    assertThat(html).doesNotContain("data-auth-signup");              // 회원가입 링크 없음
  }
}
