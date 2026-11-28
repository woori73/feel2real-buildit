package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyGradingPhotoTarget;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyGradingRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import java.time.LocalDate;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

class AssistantGradingPhotosTest {
  private final UnifiedAssistantService permissions = mock(UnifiedAssistantService.class);
  private final EasyTutorAccessService access = mock(EasyTutorAccessService.class);
  private final EasyGradingRecordRepository gradings = mock(EasyGradingRecordRepository.class);
  private final EasyLessonRecordService records = mock(EasyLessonRecordService.class);
  private final AssistantGradingPhotos api = new AssistantGradingPhotos(permissions, access, gradings, records);
  private final AuthenticatedPrincipal owner = new AuthenticatedPrincipal(1L, "owner", "TUTOR");
  private final MockMultipartFile file = new MockMultipartFile("file", "quiz.png", "image/png", new byte[] {1, 2, 3});

  @Test
  void givenOwner_whenListing_thenLimitAndReturnNextCursor() {
    var rows = LongStream.range(0, 51).mapToObj(n -> new EasyGradingPhotoTarget(
        100 - n, 2L, "가명", LocalDate.now(), "영어", "학습지", "PENDING", 1)).toList();
    when(gradings.photoTargets(7L, null, PageRequest.of(0, 51))).thenReturn(rows);

    var page = api.list(owner, 7L, null);

    assertThat(page.targets()).hasSize(50);
    assertThat(page.nextBefore()).isEqualTo(51L);
    verify(permissions).actor(owner);
    verify(access).requireAllowedBusiness(owner, 7L);
    verify(permissions, never()).requireWrites();
    verifyNoInteractions(records);
  }

  @Test
  void givenMatchingSelection_whenConfirmed_thenReuseExistingPhotoWriteOnly() throws Exception {
    when(gradings.existsByGradingIdAndBusiness_BusinessIdAndStudent_StudentId(9L, 7L, 2L)).thenReturn(true);
    var receipt = new EasyLessonRecordService.PhotoView(15L, "STUDENT", "학생 답안", "image/jpeg", 900);
    when(records.addPhoto(7L, 9L, file.getBytes(), "STUDENT", 1L)).thenReturn(receipt);

    assertThat(api.attach(owner, 7L, 2L, 9L, "STUDENT", file)).isEqualTo(receipt);

    var ordered = inOrder(permissions, access, gradings, records);
    ordered.verify(permissions).actor(owner);
    ordered.verify(access).requireAllowedBusiness(owner, 7L);
    ordered.verify(permissions).requireWrites();
    ordered.verify(gradings).existsByGradingIdAndBusiness_BusinessIdAndStudent_StudentId(9L, 7L, 2L);
    ordered.verify(records).addPhoto(7L, 9L, file.getBytes(), "STUDENT", 1L);
    verifyNoMoreInteractions(records);
  }

  @Test
  void givenUnlinkedBusiness_whenReadingOrAttaching_thenNoRecordsAccess() {
    doThrow(new AccessDeniedException("미연결")).when(access).requireAllowedBusiness(owner, 7L);

    assertThatThrownBy(() -> api.list(owner, 7L, null)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> api.attach(owner, 7L, 2L, 9L, "STUDENT", file)).isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(gradings, records);
  }

  @Test
  void givenWritesDisabledOrWrongTarget_whenUploading_thenNoPhotoWrite() {
    assertThatThrownBy(() -> api.attach(owner, 7L, 3L, 9L, "STUDENT", file))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(permissions).requireWrites();

    assertThatThrownBy(() -> api.attach(owner, 7L, 2L, 9L, "STUDENT", file))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    verifyNoInteractions(records);
  }
}
