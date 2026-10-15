package f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.dto.ExamAnalysisResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.entity.ExamAnalysis;
import f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.repository.ExamAnalysisRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.UploadSizeGuard;

class ExamAnalysisServiceTest {

  private ExamAnalysisRepository examAnalysisRepository;
  private SubjectRepository subjectRepository;
  private FileStorage fileStorage;
  private ExamAnalysisService service;

  @BeforeEach
  void setUp() {
    examAnalysisRepository = org.mockito.Mockito.mock(ExamAnalysisRepository.class);
    subjectRepository = org.mockito.Mockito.mock(SubjectRepository.class);
    fileStorage = org.mockito.Mockito.mock(FileStorage.class);
    service = new ExamAnalysisService(examAnalysisRepository, subjectRepository, fileStorage,
        new UploadSizeGuard(20L * 1024 * 1024));
  }

  @Test
  void create_storesHtmlAndReturnsPublicUrls() throws Exception {
    // given
    Subject subject = english();
    MockMultipartFile file = htmlFile("analysis.html", "<html>ok</html>");
    when(subjectRepository.findById(1)).thenReturn(Optional.of(subject));
    when(fileStorage.saveRaw(anyLong(), anyString(), any(InputStream.class), eq(file.getSize()))).thenReturn("stored/analysis.html");
    when(examAnalysisRepository.save(any(ExamAnalysis.class))).thenAnswer(invocation -> {
      ExamAnalysis analysis = invocation.getArgument(0);
      ReflectionTestUtils.setField(analysis, "examAnalysisId", 10L);
      return analysis;
    });

    // when
    ExamAnalysisResponse response = service.create("압구정중 분석", "압구정중", 2, "1학기", "중간", 1, true, file);

    // then
    assertThat(response.examAnalysisId()).isEqualTo(10L);
    assertThat(response.detailUrl()).isEqualTo("/analyzes/10");
    assertThat(response.contentUrl()).isEqualTo("/api/analyzes/10/content");
    assertThat(response.isVisible()).isTrue();
  }

  @Test
  void create_rejectsNonHtmlFile() throws Exception {
    // given
    MockMultipartFile file = new MockMultipartFile("file", "analysis.txt", "text/plain", "bad".getBytes());

    // when & then
    assertThatThrownBy(() -> service.create("제목", "학교", 2, "1학기", "중간", 1, true, file))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("HTML");
    verify(fileStorage, never()).saveRaw(anyLong(), anyString(), any(), anyLong());
  }

  @Test
  void update_replacesFileAndDeletesOldStorageWhenNewFileGiven() throws Exception {
    // given
    Subject subject = english();
    ExamAnalysis analysis = analysis(subject, "old-key");
    MockMultipartFile file = htmlFile("new.html", "<html>new</html>");
    when(examAnalysisRepository.findAdminById(10L)).thenReturn(Optional.of(analysis));
    when(subjectRepository.findById(1)).thenReturn(Optional.of(subject));
    when(fileStorage.saveRaw(anyLong(), anyString(), any(InputStream.class), eq(file.getSize()))).thenReturn("new-key");

    // when
    ExamAnalysisResponse response = service.update(10L, "새 제목", "압구정중", 3, "2학기", "기말", 1, false, file);

    // then
    assertThat(response.title()).isEqualTo("새 제목");
    assertThat(response.isVisible()).isFalse();
    verify(fileStorage).delete("old-key", "UNKNOWN");
  }

  @Test
  void softDelete_hidesWithoutDeletingStorage() {
    // given
    Subject subject = english();
    ExamAnalysis analysis = analysis(subject, "storage-key");
    when(examAnalysisRepository.findAdminById(10L)).thenReturn(Optional.of(analysis));

    // when
    service.softDelete(10L);

    // then
    assertThat(analysis.isDeleted()).isTrue();
    assertThat(analysis.isVisible()).isFalse();
    verify(fileStorage, never()).delete(anyString(), anyString());
  }

  @Test
  void hardDelete_removesRowAndStorage() {
    // given
    Subject subject = english();
    ExamAnalysis analysis = analysis(subject, "storage-key");
    when(examAnalysisRepository.findAdminById(10L)).thenReturn(Optional.of(analysis));

    // when
    service.hardDelete(10L);

    // then
    verify(examAnalysisRepository).delete(analysis);
    verify(fileStorage).delete("storage-key", "UNKNOWN");
  }

  @Test
  void getPublicHtmlContent_readsOnlyPublicEntity() throws Exception {
    // given
    Subject subject = english();
    ExamAnalysis analysis = analysis(subject, "storage-key");
    when(examAnalysisRepository.findPublicById(10L)).thenReturn(Optional.of(analysis));
    when(fileStorage.readPage("storage-key", "UNKNOWN", 1)).thenReturn("<html>public</html>".getBytes());

    // when
    String html = service.getPublicHtmlContent(10L);

    // then
    assertThat(html).contains("public");
  }

  @Test
  void latestPublic_limitsRequestedSize() {
    // given
    when(examAnalysisRepository.findLatestPublic(any())).thenReturn(List.of());

    // when
    service.latestPublic(100);

    // then
    verify(examAnalysisRepository).findLatestPublic(org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageSize() == 12));
  }

  private Subject english() {
    Subject subject = Subject.builder().nameEn("english").nameKo("영어").build();
    ReflectionTestUtils.setField(subject, "subjectId", 1);
    return subject;
  }

  private ExamAnalysis analysis(Subject subject, String storageKey) {
    ExamAnalysis analysis = ExamAnalysis.builder()
        .title("제목")
        .schoolName("압구정중")
        .grade(2)
        .semester("1학기")
        .examType("중간")
        .subject(subject)
        .originalFileName("analysis.html")
        .storageKey(storageKey)
        .fileSize(10L)
        .visible(true)
        .build();
    ReflectionTestUtils.setField(analysis, "examAnalysisId", 10L);
    return analysis;
  }

  private MockMultipartFile htmlFile(String name, String content) {
    return new MockMultipartFile("file", name, "text/html", content.getBytes());
  }
}
