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
    assertThat(html).contains("data-auth-login");                    // 로그인 안내 버튼
    assertThat(html).contains("id=\"ns-chat-login-alert\"");          // 네이버 OAuth 실패/취소 알림 영역
    assertThat(html).contains("data-login-modal");                   // 공통 로그인 모달 fragment 치환 완료
    assertThat(html).contains("메시지를 입력하세요");                  // 하단 입력 placeholder
    assertThat(html).doesNotContain("th:replace");                   // 치환 잔여물 없음
  }
}
