package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.dto.WikiExamOriginalResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamOriginal;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamOriginalDocType;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.repository.WikiExamOriginalRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.DocumentUploadValidator;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.UploadSizeGuard;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("WikiExamOriginalService — 모의고사 원본 PDF 저장/검증/삭제")
class WikiExamOriginalServiceTest {

  private WikiExamOriginalRepository repository;
  private FileStorage fileStorage;
  private WikiExamOriginalService service;

  @BeforeEach
  void setUp() {
    repository = mock(WikiExamOriginalRepository.class);
    fileStorage = mock(FileStorage.class);
    service = new WikiExamOriginalService(repository, fileStorage,
        new DocumentUploadValidator(new UploadSizeGuard(20L * 1024 * 1024)), 20L * 1024 * 1024);
  }

  @Test
  @DisplayName("given PDF when 등록 then storage.saveRaw 저장 + 다운로드 URL 반환")
  void create_storesPdf() throws Exception {
    // given
    MockMultipartFile file = pdf("mock.pdf");
    when(fileStorage.saveRaw(anyLong(), anyString(), any(InputStream.class), eq(file.getSize())))
        .thenReturn("stored/mock.pdf");
    when(repository.save(any(WikiExamOriginal.class))).thenAnswer(inv -> {
      WikiExamOriginal saved = inv.getArgument(0);
      ReflectionTestUtils.setField(saved, "wikiExamOriginalId", 7L);
      return saved;
    });

    // when
    WikiExamOriginalResponse res =
        service.create("고3 9월 문제지", 3, "2026 9월 모의고사", "QUESTION", true, file);

    // then
    assertThat(res.wikiExamOriginalId()).isEqualTo(7L);
    assertThat(res.downloadUrl()).isEqualTo("/api/wiki/originals/7/download");
    assertThat(res.docTypeLabel()).isEqualTo("문제지");
    assertThat(res.isVisible()).isTrue();
  }

  @Test
  @DisplayName("given HWP(OLE2 매직) when 원본 등록 then 저장 — 원본은 PDF·HWP·HWPX 허용")
  void create_acceptsHwp() throws Exception {
    byte[] ole2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    MockMultipartFile file = new MockMultipartFile("file", "mock.hwp", "application/x-hwp", ole2);
    when(fileStorage.saveRaw(anyLong(), anyString(), any(InputStream.class), anyLong())).thenReturn("k");
    when(repository.save(any(WikiExamOriginal.class))).thenAnswer(inv -> {
      WikiExamOriginal saved = inv.getArgument(0);
      ReflectionTestUtils.setField(saved, "wikiExamOriginalId", 9L);
      return saved;
    });

    WikiExamOriginalResponse res = service.create("고3 9월 정답지", 3, "2026 9월 모의고사", "ANSWER", true, file);

    assertThat(res.wikiExamOriginalId()).isEqualTo(9L);
    verify(fileStorage).saveRaw(anyLong(), anyString(), any(), anyLong());
  }

  @Test
  @DisplayName("given XLSX(원본 성격 아님) when 원본 등록 then 400 — 원본은 PDF/HWP/HWPX만")
  void create_rejectsNonOriginalDoc() {
    byte[] zip = {0x50, 0x4B, 0x03, 0x04, 0x14};
    MockMultipartFile file = new MockMultipartFile("file", "mock.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", zip);
    assertThatThrownBy(() -> service.create("t", 3, "2026 9월 모의고사", "QUESTION", true, file))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("형식");
    verify(fileStorage, never()).saveRaw(anyLong(), anyString(), any(), anyLong());
  }

  @Test
  @DisplayName("given 확장자만 pdf, 내용은 실행파일 when 등록 then 400 — 매직 불일치")
  void create_rejectsMagicMismatch() {
    MockMultipartFile file = new MockMultipartFile("file", "mock.pdf", "application/pdf",
        new byte[] {0x4D, 0x5A, 0x00, 0x00}); // MZ = PE 실행파일
    assertThatThrownBy(() -> service.create("t", 3, "2026 9월 모의고사", "QUESTION", true, file))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("실행 파일");
  }

  @Test
  @DisplayName("given 잘못된 학년(4) when 등록 then 400")
  void create_rejectsBadGrade() {
    assertThatThrownBy(() -> service.create("t", 4, "2026 9월 모의고사", "QUESTION", true, pdf("a.pdf")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("학년");
  }

  @Test
  @DisplayName("given 잘못된 구분 when 등록 then 400")
  void create_rejectsBadDocType() {
    assertThatThrownBy(() -> service.create("t", 3, "2026 9월 모의고사", "MEMO", true, pdf("a.pdf")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("구분");
  }

  @Test
  @DisplayName("given 신규 파일 교체 when 수정 then 이전 storage 삭제")
  void update_replacesFile() throws Exception {
    // given
    WikiExamOriginal existing = original("old-key");
    MockMultipartFile file = pdf("new.pdf");
    when(repository.findAdminById(7L)).thenReturn(Optional.of(existing));
    when(fileStorage.saveRaw(anyLong(), anyString(), any(InputStream.class), eq(file.getSize())))
        .thenReturn("new-key");

    // when
    WikiExamOriginalResponse res =
        service.update(7L, "수정", 2, "2026 9월 모의고사", "ANSWER", false, file);

    // then
    assertThat(res.title()).isEqualTo("수정");
    assertThat(res.isVisible()).isFalse();
    verify(fileStorage).delete("old-key", "UNKNOWN");
  }

  @Test
  @DisplayName("given 자료 when 숨김 삭제 then deleted=true·visible=false, storage 유지")
  void softDelete_keepsStorage() {
    // given
    WikiExamOriginal existing = original("key");
    when(repository.findAdminById(7L)).thenReturn(Optional.of(existing));

    // when
    service.softDelete(7L);

    // then
    assertThat(existing.isDeleted()).isTrue();
    assertThat(existing.isVisible()).isFalse();
    verify(fileStorage, never()).delete(anyString(), anyString());
  }

  @Test
  @DisplayName("given 자료 when 완전 삭제 then row + storage 삭제")
  void hardDelete_removesRowAndStorage() {
    // given
    WikiExamOriginal existing = original("key");
    when(repository.findAdminById(7L)).thenReturn(Optional.of(existing));

    // when
    service.hardDelete(7L);

    // then
    verify(repository).delete(existing);
    verify(fileStorage).delete("key", "UNKNOWN");
  }

  @Test
  @DisplayName("given 공개 자료 when 다운로드 then 파일명·bytes 반환")
  void getPublicDownload_returnsFile() throws Exception {
    // given
    WikiExamOriginal existing = original("key");
    when(repository.findPublicById(7L)).thenReturn(Optional.of(existing));
    when(fileStorage.readSingleLimited("key", "UNKNOWN", 20L * 1024 * 1024)).thenReturn("PDFBYTES".getBytes());

    // when
    WikiExamOriginalService.DownloadFile file = service.getPublicDownload(7L);

    // then
    assertThat(file.fileName()).isEqualTo("original.pdf");
    assertThat(new String(file.bytes())).isEqualTo("PDFBYTES");
  }

  @Test
  @DisplayName("given grade 필터 when searchPublic then 그대로 위임")
  void searchPublic_delegatesGrade() {
    // given
    when(repository.findPublicByGrade(3)).thenReturn(List.of(original("k")));

    // when
    List<WikiExamOriginalResponse> res = service.searchPublic(3);

    // then
    assertThat(res).hasSize(1);
    verify(repository).findPublicByGrade(3);
  }

  private MockMultipartFile pdf(String name) {
    return new MockMultipartFile("file", name, "application/pdf", "%PDF-1.4".getBytes());
  }

  private WikiExamOriginal original(String storageKey) {
    WikiExamOriginal o = WikiExamOriginal.builder()
        .title("제목")
        .grade(3)
        .examLabel("2026 9월 모의고사")
        .docType(WikiExamOriginalDocType.QUESTION)
        .originalFileName("original.pdf")
        .storageKey(storageKey)
        .fileSize(8L)
        .visible(true)
        .build();
    ReflectionTestUtils.setField(o, "wikiExamOriginalId", 7L);
    return o;
  }
}
