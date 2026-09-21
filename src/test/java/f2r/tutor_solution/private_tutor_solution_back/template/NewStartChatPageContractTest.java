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
 *
 * <p>2026-09-21 cleanup — 네이버 로그인 V1/V2/공식 OAuth2 구현을 전부 제거했다. 이 화면의
 * 로그인 버튼은 지금 비활성 상태다(다음 세션에서 재구현).
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
    assertThat(html).contains("id=\"ns-chat-login-alert\"");          // 로그인 실패/취소 안내 영역(범용, 현재 미사용)
    assertThat(html).contains("메시지를 입력하세요");                  // 하단 입력 placeholder
    assertThat(html).doesNotContain("th:replace");                   // 치환 잔여물 없음

    // clean baseline: 로그인 버튼은 비활성 상태이고, 회원가입/공용 모달/과거 네이버 endpoint가 없다.
    assertThat(html).contains("네이버 로그인 준비 중");
    assertThat(html).doesNotContain("href=\"/oauth/naver/start\"");
    assertThat(html).doesNotContain("href=\"/auth/naver/v2/start\"");
    assertThat(html).doesNotContain("href=\"/oauth2/authorization/naver\"");
    assertThat(html).doesNotContain("data-auth-login");               // 공용 모달 오픈 트리거 없음
    assertThat(html).doesNotContain("data-login-modal");              // 공용 로그인 모달 미포함
    assertThat(html).doesNotContain("data-auth-signup");              // 회원가입 링크 없음
  }
}
