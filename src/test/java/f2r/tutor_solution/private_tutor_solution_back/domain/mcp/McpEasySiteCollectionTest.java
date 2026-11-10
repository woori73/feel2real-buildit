package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasySiteCollectionService;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** 모든 값은 합성 자료다. */
class McpEasySiteCollectionTest {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(1L, "admin", "ADMIN");
  private static final String ARGS = "{\"businessId\":7,\"siteCode\":\"KB_BANK\",\"dataset\":\"BANK_ACCOUNT\","
      + "\"accountRef\":\"운영통장\",\"from\":\"2026-10-01\",\"to\":\"2026-10-31\",\"expectedCount\":1,"
      + "\"expectedTotal\":300000,\"idempotencyKey\":\"key-00001\",\"rows\":[{\"date\":\"2026-10-02\","
      + "\"direction\":\"IN\",\"amount\":300000,\"counterparty\":\"학부모A\",\"balance\":1300000}]}";

  @SuppressWarnings("unchecked")
  private McpGateway gateway(String scopes, EasySiteCollectionService sites) {
    var oauth = new McpOAuth(null, null, null, null);
    ReflectionTestUtils.setField(oauth, "allowedScopes", scopes);
    var gateway = new McpGateway(null, oauth, null, null, JSON, null, mock(McpRateLimit.class));
    ObjectProvider<EasySiteCollectionService> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(sites);
    ReflectionTestUtils.setField(gateway, "easySiteCollection", provider);
    return gateway;
  }

  @Test
  @DisplayName("given 운영 허용 목록에 easy-collect:write 없음 when 도구 목록 then 두 도구가 보이지 않는다")
  void toolsHiddenWithoutAllowedScope() {
    // when
    var names = gateway("studio:read academy-move:write", null).tools().stream().map(t -> t.get("name")).toList();

    // then
    assertFalse(names.contains("easy_collection_sites"));
    assertFalse(names.contains("save_easy_site_rows"));
    assertTrue(gateway("", null).tools().stream().map(t -> t.get("name")).toList().contains("save_easy_site_rows"));
  }

  @Test
  @DisplayName("given 별도 동의가 없는 관리자 when 저장 then 403이고 서비스를 부르지 않는다")
  void rejectWithoutConsent() throws Exception {
    // given
    var sites = mock(EasySiteCollectionService.class);
    var identity = new McpOAuth.Identity("grant", ADMIN, Set.of("easy:read"));

    // when / then
    assertThrows(ResponseStatusException.class, () -> gateway("", sites).execute(identity, "save_easy_site_rows", JSON.readTree(ARGS)));
    verifyNoInteractions(sites);
  }

  @Test
  @DisplayName("given 동의는 있지만 관리자가 아님 when 목록 then 403")
  void rejectNonAdmin() {
    // given
    var sites = mock(EasySiteCollectionService.class);
    var identity = new McpOAuth.Identity("grant", new AuthenticatedPrincipal(2L, "tutor", "TUTOR"), Set.of(McpOAuth.EASY_COLLECT_WRITE));

    // when / then
    assertThrows(ResponseStatusException.class, () -> gateway("", sites).execute(identity, "easy_collection_sites", JSON.createObjectNode()));
    verifyNoInteractions(sites);
  }

  @Test
  @DisplayName("given 관리자와 동의 when 저장 then 행을 그대로 옮겨 서비스에 넘긴다")
  void delegatesParsedRows() throws Exception {
    // given
    var sites = mock(EasySiteCollectionService.class);
    var identity = new McpOAuth.Identity("grant", ADMIN, Set.of(McpOAuth.EASY_COLLECT_WRITE));

    // when
    gateway("", sites).execute(identity, "save_easy_site_rows", JSON.readTree(ARGS));

    // then
    verify(sites).save(eq(ADMIN), argThat(r -> r.businessId() == 7L && r.siteCode().equals("KB_BANK")
        && r.expectedTotal() == 300_000L && r.rows().size() == 1 && r.rows().get(0).balance() == 1_300_000L
        && r.rows().get(0).direction().equals("IN")));
  }

  @Test
  @DisplayName("given 검증 실패 when 저장 then saved=false와 사유를 돌려준다")
  void validationFailureIsReported() throws Exception {
    // given
    var sites = mock(EasySiteCollectionService.class);
    when(sites.save(any(), any())).thenThrow(new IllegalArgumentException("건수가 맞지 않습니다."));
    var identity = new McpOAuth.Identity("grant", ADMIN, Set.of(McpOAuth.EASY_COLLECT_WRITE));

    // when
    var out = gateway("", sites).execute(identity, "save_easy_site_rows", JSON.readTree(ARGS));

    // then
    assertEquals(Map.of("saved", false, "reason", "건수가 맞지 않습니다."), out);
  }

  @Test
  @DisplayName("given 행에 비밀번호 같은 모르는 칸 when 저장 then 400으로 막는다")
  void unknownRowFieldRejected() {
    // given
    var sites = mock(EasySiteCollectionService.class);
    var identity = new McpOAuth.Identity("grant", ADMIN, Set.of(McpOAuth.EASY_COLLECT_WRITE));
    String bad = ARGS.replace("\"balance\":1300000", "\"password\":\"x\"");

    // when / then
    assertThrows(ResponseStatusException.class, () -> gateway("", sites).execute(identity, "save_easy_site_rows", JSON.readTree(bad)));
    verifyNoInteractions(sites);
  }
}
