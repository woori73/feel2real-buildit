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
 * handshake는 {@code /api/**}·handshake interceptor가 비로그인을 401로 거부한다.
 *
 * <p>2026-09-21 cleanup: 네이버 로그인 V1/V2/공식 Spring Security OAuth2 Client 구현을
 * 전부 제거해 현재 로그인 수단이 없다(다음 세션에서 재구현 예정) — 비로그인 방문자 화면의
 * "네이버 로그인 준비 중" 버튼은 비활성 상태다. 회원가입·ID/PW 로그인은 여전히 없다(NewStart 방향).
 */
@Controller
public class NewStartChatPageController {

  @GetMapping("/newstart/chat")
  public String newstartChat(HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return "newstart/chat";
  }
}
