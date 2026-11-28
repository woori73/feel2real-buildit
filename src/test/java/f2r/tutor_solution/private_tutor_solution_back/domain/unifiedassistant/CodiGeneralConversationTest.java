package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant.CodiChatEngine.PublicAnswer;
import f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant.CodiChatEngine.PublicExchange;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CodiGeneralConversationTest {
  private final UnifiedAssistantService permissions = mock(UnifiedAssistantService.class);
  private final AcademyAssistantAdapter academy = mock(AcademyAssistantAdapter.class);
  private final CodiChatEngine engine = mock(CodiChatEngine.class);
  private final AuthenticatedPrincipal owner = new AuthenticatedPrincipal(1L, "owner", "TUTOR");
  private final CodiGeneralConversation chat = new CodiGeneralConversation(permissions, academy,
      new AssistantModelBudget(20,20,2,System::currentTimeMillis), engine);

  private UUID send(String text, UUID prior) {
    var result = chat.send(owner, 7L, text, prior, true);
    assertThat(result.drafts()).isEmpty();
    assertThat(result.overview()).isNull();
    return UUID.fromString((String) ((Map<?,?>) result.data()).get("conversationId"));
  }

  @Test void givenPublicConversation_whenContinuing_thenOnlyThreeRecentGeneralTurnsReachClaude() {
    when(engine.reply(anyList(), anyString(), anyBoolean())).thenReturn(new PublicAnswer("답", List.of()));
    UUID id = null;
    for (String question : List.of("하나","둘","셋","넷","다섯")) id = send(question, id);
    verify(engine).reply(List.of(new PublicExchange("둘","답"), new PublicExchange("셋","답"),
        new PublicExchange("넷","답")), "다섯", true);
    verify(permissions,times(5)).actor(owner);
    verifyNoMoreInteractions(permissions);
  }

  @Test void givenAnotherUserOrAcademy_whenReusingId_thenNoModelCallOrDisclosure() {
    when(engine.reply(anyList(),anyString(),anyBoolean())).thenReturn(new PublicAnswer("답",List.of()));
    UUID id = send("학습 방법", null);
    clearInvocations(engine);
    var other = new AuthenticatedPrincipal(2L,"other","TUTOR");
    for (Runnable send : List.<Runnable>of(() -> chat.send(other,7L,"계속",id,true),
        () -> chat.send(owner,8L,"계속",id,true))) {
      assertThatThrownBy(send::run).isInstanceOfSatisfying(ResponseStatusException.class,
          e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.GONE));
    }
    verifyNoInteractions(engine);
  }

  @Test void givenRevokedAuthority_whenContinuing_thenRecheckBeforeClaude() {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(academy).owner(owner,7L);
    assertThatThrownBy(() -> chat.send(owner,7L,"안녕",null,true)).isInstanceOf(ResponseStatusException.class);
    verifyNoInteractions(engine);
  }

  @Test void givenSignedInPublicAccount_whenContinuing_thenScopeByUserWithoutCallingAcademyOrSearch() {
    when(engine.reply(anyList(), anyString(), eq(false))).thenReturn(new PublicAnswer("답", List.of()));
    var first = chat.send(owner, null, "안녕", null, true);
    var token = UUID.fromString((String) ((Map<?, ?>) first.data()).get("conversationId"));
    chat.send(owner, null, "계속", token, true);
    verify(permissions, times(2)).actor(owner);
    verify(engine).reply(List.of(new PublicExchange("안녕", "답")), "계속", false);
    verifyNoInteractions(academy);
    var other = new AuthenticatedPrincipal(2L, "other", "TUTOR");
    clearInvocations(engine);
    for (Runnable send : List.<Runnable>of(() -> chat.send(other, null, "계속", token, false),
        () -> chat.send(owner, 7L, "계속", token, false))) {
      assertThatThrownBy(send::run).isInstanceOfSatisfying(ResponseStatusException.class,
          e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.GONE));
    }
    verifyNoInteractions(engine);
  }

  @Test void givenRevokedAccount_whenSendingWithoutAcademy_thenRejectBeforeModel() {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(permissions).actor(owner);
    assertThatThrownBy(() -> chat.send(owner, null, "질문", null, false))
        .isInstanceOf(ResponseStatusException.class);
    verifyNoInteractions(engine, academy);
  }

  @Test void givenPersonalDetails_whenAskingForSearch_thenRejectBeforeClaude() {
    for (String text : List.of("연락처 010-1234-5678", "alpha@example.com", "이름: 민수", "800101-1234567", "학생 김하늘 등록, 학교: 새봄중", "학생 ID 42의 출결")) {
      assertThatThrownBy(() -> chat.send(owner,7L,text,null,true)).isInstanceOfSatisfying(
          ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
    verifyNoInteractions(engine);
  }
}
