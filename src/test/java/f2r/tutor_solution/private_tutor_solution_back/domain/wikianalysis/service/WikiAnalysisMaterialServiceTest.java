package f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.service;

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

import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.dto.WikiAnalysisMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.entity.WikiAnalysisMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.repository.WikiAnalysisMaterialRepository;
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

@DisplayName("WikiAnalysisMaterialService — 분석 자료 문서 저장/검증/삭제")
class WikiAnalysisMaterialServiceTest {

  private static final byte[] OLE2 =
      {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

  private WikiAnalysisMaterialRepository repository;
  private FileStorage fileStorage;
  private WikiAnalysisMaterialService service;

  @BeforeEach
  void setUp() {
    repository = mock(WikiAnalysisMaterialRepository.class);
    fileStorage = mock(FileStorage.class);
    service = new WikiAnalysisMaterialService(repository, fileStorage,
        new DocumentUploadValidator(new UploadSizeGuard(20L * 1024 * 1024)), 20L * 1024 * 1024);
  }

  @Test
  @DisplayName("given HWP when 등록 then storage 저장 + content-type·다운로드 URL 반환")
  void create_storesHwp() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "분석.hwp", "application/x-hwp", OLE2);
    when(fileStorage.saveRaw(anyLong(), anyString(), any(InputStream.class), eq(file.getSize())))
        .thenReturn("stored/분석.hwp");
    when(repository.save(any(WikiAnalysisMaterial.class))).thenAnswer(inv -> {
      WikiAnalysisMaterial saved = inv.getArgument(0);
      ReflectionTestUtils.setField(saved, "wikiAnalysisMaterialId", 5L);
      return saved;
    });

    WikiAnalysisMaterialResponse res = service.create(
        "신사중 3학년 기말 분석", " ", null, 3, 2026, "1학기", "기말", "", true, file);

    assertThat(res.wikiAnalysisMaterialId()).isEqualTo(5L);
    assertThat(res.downloadUrl()).isEqualTo("/api/wiki/analysis-materials/5/download");
    assertThat(res.contentType()).isEqualTo("application/x-hwp");
    assertThat(res.subject()).isEqualTo("영어");     // blank → 기본값
    assertThat(res.description()).isNull();          // blank → null
  }

  @Test
  @DisplayName("given 허용 외 확장자 when 등록 then 400 + storage 미호출")
  void create_rejectsBadExtension() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "a.exe", "application/octet-stream",
        new byte[] {0x4D, 0x5A});
    assertThatThrownBy(() -> service.create("t", null, null, 3, 2026, "1학기", "기말", null, true, file))
        .isInstanceOf(ResponseStatusException.class);
    verify(fileStorage, never()).saveRaw(anyLong(), anyString(), any(), anyLong());
  }

  @Test
  @DisplayName("given 잘못된 학년/연도 when 등록 then 400")
  void create_rejectsBadGradeAndYear() {
    MockMultipartFile pdf = new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF".getBytes());
    assertThatThrownBy(() -> service.create("t", null, null, 5, 2026, "1학기", "기말", null, true, pdf))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("학년");
    assertThatThrownBy(() -> service.create("t", null, null, 3, 1800, "1학기", "기말", null, true, pdf))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("연도");
  }

  @Test
  @DisplayName("given 신규 파일 교체 when 수정 then 이전 storage 삭제 + content-type 갱신")
  void update_replacesFile() throws Exception {
    WikiAnalysisMaterial existing = material("old-key", "application/pdf");
    MockMultipartFile file = new MockMultipartFile("file", "new.docx", "application/octet-stream",
        new byte[] {0x50, 0x4B, 0x03, 0x04});
    when(repository.findAdminById(5L)).thenReturn(Optional.of(existing));
    when(fileStorage.saveRaw(anyLong(), anyString(), any(InputStream.class), eq(file.getSize())))
        .thenReturn("new-key");

    WikiAnalysisMaterialResponse res =
        service.update(5L, "수정", null, "전국", 2, 2025, "2학기", "중간", "영어", false, file);

    assertThat(res.title()).isEqualTo("수정");
    assertThat(res.isVisible()).isFalse();
    assertThat(res.contentType()).contains("wordprocessingml");
    verify(fileStorage).delete("old-key", "UNKNOWN");
  }

  @Test
  @DisplayName("given 자료 when 숨김 삭제 then deleted=true·visible=false, storage 유지")
  void softDelete_keepsStorage() {
    WikiAnalysisMaterial existing = material("key", "application/pdf");
    when(repository.findAdminById(5L)).thenReturn(Optional.of(existing));

    service.softDelete(5L);

    assertThat(existing.isDeleted()).isTrue();
    assertThat(existing.isVisible()).isFalse();
    verify(fileStorage, never()).delete(anyString(), anyString());
  }

  @Test
  @DisplayName("given 자료 when 완전 삭제 then row + storage 삭제")
  void hardDelete_removesRowAndStorage() {
    WikiAnalysisMaterial existing = material("key", "application/pdf");
    when(repository.findAdminById(5L)).thenReturn(Optional.of(existing));

    service.hardDelete(5L);

    verify(repository).delete(existing);
    verify(fileStorage).delete("key", "UNKNOWN");
  }

  @Test
  @DisplayName("given 공개 자료 when 다운로드 then 파일명·bytes·content-type 반환")
  void getPublicDownload_returnsFile() throws Exception {
    WikiAnalysisMaterial existing = material("key", "application/x-hwp");
    when(repository.findPublicById(5L)).thenReturn(Optional.of(existing));
    when(fileStorage.readSingleLimited("key", "UNKNOWN", 20L * 1024 * 1024)).thenReturn("HWPBYTES".getBytes());

    WikiAnalysisMaterialService.DownloadFile file = service.getPublicDownload(5L);

    assertThat(file.fileName()).isEqualTo("original.hwp");
    assertThat(file.contentType()).isEqualTo("application/x-hwp");
    assertThat(new String(file.bytes())).isEqualTo("HWPBYTES");
  }

  @Test
  @DisplayName("given grade 필터 when searchPublic then 그대로 위임")
  void searchPublic_delegatesGrade() {
    when(repository.findPublicByGrade(3)).thenReturn(List.of(material("k", "application/pdf")));
    assertThat(service.searchPublic(3)).hasSize(1);
    verify(repository).findPublicByGrade(3);
  }

  private WikiAnalysisMaterial material(String storageKey, String contentType) {
    WikiAnalysisMaterial m = WikiAnalysisMaterial.builder()
        .title("제목").grade(3).year(2026).semester("1학기").examType("기말").subject("영어")
        .contentType(contentType).originalFileName("original.hwp")
        .storageKey(storageKey).fileSize(8L).visible(true)
        .build();
    ReflectionTestUtils.setField(m, "wikiAnalysisMaterialId", 5L);
    return m;
  }
}
