package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.dto.PresenceMemberView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.dto.PresenceSettingsRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.dto.PresenceSettingsView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.service.NewStartPresenceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * NewStart 접속상태 API. {@code /api/**} catch-all로 로그인 회원만 사용한다.
 * GRANT/OAuth/THANKS 판정과 무관한 별도 기능이다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/newstart/presence")
public class NewStartPresenceController {

  private final NewStartPresenceService presenceService;

  /** heartbeat 수신 + 본인 설정 반환. */
  @PostMapping("/ping")
  public ResponseEntity<PresenceSettingsView> ping(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(presenceService.ping(principal));
  }

  /** 현재 접속 회원 목록(숨김 회원 제외, 본인 제외). */
  @GetMapping("/online")
  public ResponseEntity<List<PresenceMemberView>> online(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(presenceService.onlineMembers(principal));
  }

  /** 본인 설정 조회. */
  @GetMapping("/settings")
  public ResponseEntity<PresenceSettingsView> settings(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(presenceService.getSettings(principal));
  }

  /** 본인 설정 변경(접속상태 숨김·쪽지 수신 거부). */
  @PutMapping("/settings")
  public ResponseEntity<PresenceSettingsView> updateSettings(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody PresenceSettingsRequest request) {
    return ResponseEntity.ok(presenceService.updateSettings(
        principal, request.hideOnline(), request.dmBlocked()));
  }
}
