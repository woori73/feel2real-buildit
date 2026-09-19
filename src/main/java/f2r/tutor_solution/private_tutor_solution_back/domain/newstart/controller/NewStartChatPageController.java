package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * NewStart 회원 채팅 화면(/newstart/chat) 렌더링.
 *
 * <p>화면 자체는 공개로 두고(기존 /newstart 정책 유지 — SecurityConfig에 새 규칙을 만들지
 * 않는다) 실제 채팅 데이터는 전부 인증 뒤에 있다: REST(/api/newstart/chat/**)와 WebSocket
 * handshake는 {@code /api/**}·handshake interceptor가 비로그인을 401로 거부한다. 비로그인
 * 방문자는 화면에서 기존 Feel2Real 로그인 모달로 안내한다.
 */
@Controller
public class NewStartChatPageController {

  @GetMapping("/newstart/chat")
  public String newstartChat(HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return "newstart/chat";
  }
}
