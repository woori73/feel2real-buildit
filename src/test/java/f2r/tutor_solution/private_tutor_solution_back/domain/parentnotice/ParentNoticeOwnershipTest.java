package f2r.tutor_solution.private_tutor_solution_back.domain.parentnotice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant.AcademyAssistantAdapter;
import f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant.UnifiedAssistantService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ParentNoticeOwnershipTest {
  private final AcademyAssistantAdapter academy = mock(AcademyAssistantAdapter.class);
  private final UnifiedAssistantService assistant = mock(UnifiedAssistantService.class);
  private final ParentNoticeStorage storage = mock(ParentNoticeStorage.class);
  private final ParentNoticeService notices = new ParentNoticeService(
      mock(ParentNoticeDraftRepository.class), assistant, academy, storage);

  @Test
  void givenAdminWithoutAnOwnerAcademy_whenReadingUsage_thenNoAcademyIsChargedOrDisclosed() {
    var admin = new AuthenticatedPrincipal(9L, "test-admin", "ADMIN");

    long confirmed = notices.monthlyConfirmed(admin);

    assertThat(confirmed).isZero();
    verify(assistant).actor(admin);
    verifyNoInteractions(academy, storage);
  }

  @Test
  void givenIncompleteOwnerContext_whenReadingUsage_thenNeverGuessAnAcademy() {
    var owner = new AuthenticatedPrincipal(10L, "test-owner", "TUTOR");
    when(academy.context(owner)).thenReturn(JsonNodeFactory.instance.objectNode()
        .put("role", "ACADEMY_OWNER"));

    assertThatThrownBy(() -> notices.monthlyConfirmed(owner))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    verifyNoInteractions(storage);
  }
}
