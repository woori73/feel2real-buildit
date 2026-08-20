package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.ConversationSummaryResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service.ConversationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationControllerTest {

  private ConversationService conversationService;
  private AuthenticatedUserResolver authResolver;
  private ConversationController controller;

  @BeforeEach
  void setUp() {
    conversationService = mock(ConversationService.class);
    authResolver = mock(AuthenticatedUserResolver.class);
    controller = new ConversationController(conversationService, authResolver);
  }

  @Test
  @DisplayName("create — given TUTOR with targetStudentId when 호출 then 학생 소유 대화 생성 거부")
  void create_rejectsTutorEvenWithTargetStudentId() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal tutor =
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor", Role.TUTOR.name());

    // when & then
    assertThatThrownBy(() -> controller.create(tutor, 200L))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("강사는 학생 소유 대화를 생성할 수 없습니다");
    verify(authResolver, never()).resolveStudentId(tutor, 200L);
    verify(conversationService, never()).create(200L);
  }

  @Test
  @DisplayName("create — given STUDENT when 호출 then resolved studentId로 생성")
  void create_allowsStudentPath() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal student =
        new JwtTokenProvider.AuthenticatedPrincipal(20L, "student", Role.STUDENT.name());
    ConversationSummaryResponse expected = summary(300L);
    when(authResolver.resolveStudentId(student, null)).thenReturn(201L);
    when(conversationService.create(201L)).thenReturn(expected);

    // when
    ResponseEntity<ConversationSummaryResponse> response = controller.create(student, null);

    // then
    assertThat(response.getBody()).isSameAs(expected);
    verify(conversationService).create(201L);
  }

  @Test
  @DisplayName("create — given ADMIN with targetStudentId when 호출 then target 학생으로 생성")
  void create_allowsAdminTargetPath() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal admin =
        new JwtTokenProvider.AuthenticatedPrincipal(1L, "admin", Role.ADMIN.name());
    ConversationSummaryResponse expected = summary(301L);
    when(authResolver.resolveStudentId(admin, 202L)).thenReturn(202L);
    when(conversationService.create(202L)).thenReturn(expected);

    // when
    ResponseEntity<ConversationSummaryResponse> response = controller.create(admin, 202L);

    // then
    assertThat(response.getBody()).isSameAs(expected);
    verify(conversationService).create(202L);
  }

  private ConversationSummaryResponse summary(Long conversationId) {
    return new ConversationSummaryResponse(
        conversationId,
        "새 학습기록",
        LocalDateTime.of(2026, 7, 10, 0, 0),
        null,
        0,
        0,
        null,
        null,
        null);
  }
}
