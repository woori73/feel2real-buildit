package f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.service;

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

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.dto.WikiLearningMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.entity.WikiLearningMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.repository.WikiLearningMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.DocumentUploadValidator;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.UploadSizeGuard;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("WikiLearningMaterialService — 회원 학습 자료 저장/검증/삭제 + uploader 스냅샷")
class WikiLearningMaterialServiceTest {

  private static final long LIMIT = 20L * 1024 * 1024;

  private WikiLearningMaterialRepository repository;
  private UserRepository userRepository;
  private FileStorage fileStorage;
  private WikiLearningMaterialService service;

  @BeforeEach
  void setUp() {
    repository = mock(WikiLearningMaterialRepository.class);
    userRepository = mock(UserRepository.class);
    fileStorage = mock(FileStorage.class);
    service = new WikiLearningMaterialService(repository, userRepository, fileStorage,
        new DocumentUploadValidator(new UploadSizeGuard(LIMIT)), LIMIT);
  }

  private User member(long id, String nickname) {
    User u = User.builder().loginId("m" + id).password("x").name("이름").nickname(nickname)
        .email(null).phone(null).role(Role.STUDENT).build();
    ReflectionTestUtils.setField(u, "userId", id);
    return u;
  }

  @Test
  @DisplayName("given 회원 PDF 업로드 when upload then uploader 는 principal 로만 기록 + 제목 자동생성")
  void upload_snapshotsUploaderFromPrincipal_andAutoTitle() throws Exception {
    when(userRepository.findById(7L)).thenReturn(Optional.of(member(7L, "홍길동")));
    MockMultipartFile file =
        new MockMultipartFile("file", "2026년_9월_고2_어휘정리.pdf", "application/pdf", "%PDF-1.4".getBytes());
    when(fileStorage.saveRaw(anyLong(), anyString(), any(InputStream.class), eq(file.getSize())))
        .thenReturn("stored/vocab.pdf");
    when(repository.save(any(WikiLearningMaterial.class))).thenAnswer(inv -> {
      WikiLearningMaterial m = inv.getArgument(0);
      ReflectionTestUtils.setField(m, "wikiLearningMaterialId", 3L);
      return m;
    });

    // 요청 본문이 다른 uploader 를 주장해도 무시된다 — 서비스는 uploaderUserId 파라미터(=principal)만 믿는다.
    WikiLearningMaterialResponse res = service.upload(7L, "vocab", 2, "  ", file);

    assertThat(res.wikiLearningMaterialId()).isEqualTo(3L);
    assertThat(res.dataType()).isEqualTo("VOCAB");
    assertThat(res.grade()).isEqualTo(2);
    assertThat(res.title()).isEqualTo("2026년 9월 고2 어휘정리");   // 파일명 → '_' 공백 + 확장자 제거
    assertThat(res.uploaderName()).isEqualTo("홍길동");
    assertThat(res.downloadUrl()).isEqualTo("/api/wiki/learning-materials/3/download");

    org.mockito.ArgumentCaptor<WikiLearningMaterial> captor =
        org.mockito.ArgumentCaptor.forClass(WikiLearningMaterial.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getUploaderUserId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("given 사용자 지정 제목 when upload then 그 제목 사용")
  void upload_keepsExplicitTitle() throws Exception {
    when(userRepository.findById(1L)).thenReturn(Optional.of(member(1L, "김철수")));
    MockMultipartFile file = new MockMultipartFile("file", "a.docx", "application/octet-stream",
        new byte[] {0x50, 0x4B, 0x03, 0x04});
    when(fileStorage.saveRaw(anyLong(), anyString(), any(InputStream.class), anyLong())).thenReturn("k");
    when(repository.save(any(WikiLearningMaterial.class))).thenAnswer(inv -> inv.getArgument(0));

    WikiLearningMaterialResponse res = service.upload(1L, "WORKBOOK", 3, "고3 구문 워크북", file);

    assertThat(res.title()).isEqualTo("고3 구문 워크북");
    assertThat(res.dataType()).isEqualTo("WORKBOOK");
  }

  @Test
  @DisplayName("given 실행파일 위장 when upload then 400 + storage 미호출")
  void upload_rejectsExecutable() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(member(1L, "n")));
    MockMultipartFile exe = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[] {0x4D, 0x5A, 0, 0});
    assertThatThrownBy(() -> service.upload(1L, "ETC", 1, null, exe))
        .isInstanceOf(ResponseStatusException.class);
    verify(fileStorage, never()).saveRaw(anyLong(), anyString(), any(), anyLong());
  }

  @Test
  @DisplayName("given 허용 외 확장자 when upload then 400")
  void upload_rejectsBadExtension() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(member(1L, "n")));
    MockMultipartFile html = new MockMultipartFile("file", "x.html", "text/html", "<html>".getBytes());
    assertThatThrownBy(() -> service.upload(1L, "ETC", 1, null, html))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  @DisplayName("given 잘못된 자료 종류 when upload then 400")
  void upload_rejectsBadType() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(member(1L, "n")));
    MockMultipartFile pdf = new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF".getBytes());
    assertThatThrownBy(() -> service.upload(1L, "SOMETHING", 1, null, pdf))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("종류");
  }

  @Test
  @DisplayName("given 잘못된 학년 when upload then 400")
  void upload_rejectsBadGrade() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(member(1L, "n")));
    MockMultipartFile pdf = new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF".getBytes());
    assertThatThrownBy(() -> service.upload(1L, "ETC", 5, "t", pdf))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("학년");
  }

  @Test
  @DisplayName("given 알 수 없는 회원 when upload then 401")
  void upload_unknownUser_unauthorized() {
    when(userRepository.findById(99L)).thenReturn(Optional.empty());
    MockMultipartFile pdf = new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF".getBytes());
    assertThatThrownBy(() -> service.upload(99L, "ETC", 1, "t", pdf))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  @DisplayName("given 자료 when ADMIN 완전 삭제 then row + storage 삭제")
  void adminHardDelete_removesRowAndStorage() {
    WikiLearningMaterial m = WikiLearningMaterial.builder()
        .dataType(f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.entity.WikiLearningMaterialType.ETC)
        .grade(2).title("t").contentType("application/pdf").originalFileName("a.pdf")
        .storageKey("key").fileSize(3L).uploaderUserId(1L).uploaderName("n").build();
    ReflectionTestUtils.setField(m, "wikiLearningMaterialId", 4L);
    when(repository.findById(4L)).thenReturn(Optional.of(m));

    service.adminHardDelete(4L);

    verify(repository).delete(m);
    verify(fileStorage).delete("key", "UNKNOWN");
  }

  @Test
  @DisplayName("given 회원 다운로드 when getDownload then bytes·파일명·content-type")
  void getDownload_returnsFile() throws Exception {
    WikiLearningMaterial m = WikiLearningMaterial.builder()
        .dataType(f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.entity.WikiLearningMaterialType.VOCAB)
        .grade(2).title("t").contentType("application/pdf").originalFileName("어휘.pdf")
        .storageKey("key").fileSize(3L).uploaderUserId(1L).uploaderName("n").build();
    ReflectionTestUtils.setField(m, "wikiLearningMaterialId", 4L);
    when(repository.findActiveById(4L)).thenReturn(Optional.of(m));
    when(fileStorage.readSingleLimited("key", "UNKNOWN", LIMIT)).thenReturn("PDF".getBytes());

    WikiLearningMaterialService.DownloadFile f = service.getDownload(4L);

    assertThat(f.fileName()).isEqualTo("어휘.pdf");
    assertThat(f.contentType()).isEqualTo("application/pdf");
    assertThat(new String(f.bytes())).isEqualTo("PDF");
  }
}
