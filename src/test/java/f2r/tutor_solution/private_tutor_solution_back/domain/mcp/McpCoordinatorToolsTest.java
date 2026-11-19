package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("MCP 코디네이터 도구 — coord:draft 동의·관리자만, 임의 실행·삭제·발송 도구 없음")
class McpCoordinatorToolsTest {

  McpOAuth oauth(String allowed) {
    var oauth = new McpOAuth(null, null, null, null);
    ReflectionTestUtils.setField(oauth, "allowedScopes", allowed);
    return oauth;
  }

  @Test
  @DisplayName("given coord:draft가 운영 허용 목록에 없음 when 도구 목록 then 코디네이터 도구 숨김")
  void hiddenWhenScopeNotAllowed() {
    // given
    var gateway = new McpGateway(null, oauth("studio:read"), null, null, null, null, null);

    // when
    var names = gateway.tools().stream().map(t -> (String) t.get("name")).toList();

    // then
    assertThat(names).doesNotContainAnyElementsOf(McpGateway.COORD_TOOLS);
  }

  @Test
  @DisplayName("given 허용 목록에 있음 when 동의한 관리자·동의 안 한 관리자·동의한 강사 then 동의한 관리자만 본다")
  void visibleOnlyForConsentingAdmin() {
    // given
    var gateway = new McpGateway(null, oauth("studio:read coord:draft"), null, null, null, null, null);
    var admin = new AuthenticatedPrincipal(1L, "owner", "ADMIN");
    var tutor = new AuthenticatedPrincipal(2L, "t", "TUTOR");

    // when
    var consenting = gateway.tools(new McpOAuth.Identity("g1", admin, Set.of(McpOAuth.COORD_DRAFT)));
    var noConsent = gateway.tools(new McpOAuth.Identity("g2", admin, Set.of("studio:read")));
    var tutorGrant = gateway.tools(new McpOAuth.Identity("g3", tutor, Set.of(McpOAuth.COORD_DRAFT)));

    // then
    assertThat(consenting).extracting(t -> t.get("name")).containsAll(McpGateway.COORD_TOOLS);
    assertThat(noConsent).extracting(t -> t.get("name")).doesNotContainAnyElementsOf(McpGateway.COORD_TOOLS);
    assertThat(tutorGrant).extracting(t -> t.get("name")).doesNotContainAnyElementsOf(McpGateway.COORD_TOOLS);
    assertThat(consenting).extracting(t -> (String) t.get("name"))
        .noneMatch(n -> n.matches(".*(approve|delete|send|execute).*"));
  }

  @Test
  @DisplayName("given 동의 없는 연결 when f2r_attendance_mark 실행 then 403 coord_admin_consent_required")
  void executeRequiresConsent() {
    // given
    var gateway = new McpGateway(null, oauth("coord:draft"), null, null, null, null, null);
    var identity = new McpOAuth.Identity("g", new AuthenticatedPrincipal(1L, "owner", "ADMIN"), Set.of("studio:read"));
    var args = JsonMapper.builder().build().createObjectNode()
        .put("requestId", "8e4b5f07-7704-4d36-b7af-8347498a93d3").put("studentId", 11);

    // when & then
    assertThatThrownBy(() -> gateway.execute(identity, "f2r_attendance_mark", args))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("coord_admin_consent_required");
  }

  @Test
  @DisplayName("given 동의한 관리자 when 도구 목록 then 대화창 확인·취소 도구는 쓰기 도구(readOnlyHint=false)로 표시된다")
  void confirmAndUndoAreWriteTools() {
    // given
    var gateway = new McpGateway(null, oauth("coord:draft"), null, null, null, null, null);
    var admin = new AuthenticatedPrincipal(1L, "owner", "ADMIN");

    // when
    var tools = gateway.tools(new McpOAuth.Identity("g1", admin, Set.of(McpOAuth.COORD_DRAFT)));

    // then
    for (String name : List.of("f2r_confirm", "f2r_undo", "f2r_student_change", "f2r_class_change", "f2r_tuition_change", "f2r_record_change", "f2r_grading_save", "f2r_weakness_save")) {
      var tool = tools.stream().filter(t -> name.equals(t.get("name"))).findFirst().orElseThrow();
      assertThat(((Map<?, ?>) tool.get("annotations")).get("readOnlyHint")).isEqualTo(false);
      assertThat((String) tool.get("description")).containsAnyOf("사용자", "학생부");
    }
  }

  @Test
  @DisplayName("given 동의 없는 연결 when f2r_confirm 실행 then 403 coord_admin_consent_required")
  void confirmRequiresConsent() {
    // given
    var gateway = new McpGateway(null, oauth("coord:draft"), null, null, null, null, null);
    var identity = new McpOAuth.Identity("g", new AuthenticatedPrincipal(1L, "owner", "ADMIN"), Set.of("studio:read"));
    var args = JsonMapper.builder().build().createObjectNode().put("jobId", "j").put("planHash", "h");

    // when & then
    assertThatThrownBy(() -> gateway.execute(identity, "f2r_confirm", args))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("coord_admin_consent_required");
  }

  @Test
  @DisplayName("given 새 scope when 알려진 scope 목록 then coord:draft 포함")
  void scopeKnown() {
    // given & when & then
    assertThat(oauth("").scopes()).contains(McpOAuth.COORD_DRAFT);
  }
}
