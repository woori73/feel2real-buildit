package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.config.ArtCraftEditorProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.CreateProjectRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.EditCommandRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.EditProjectRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.ProjectResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.RevertProjectRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.SnapshotResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.SourceUploadResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception.ArtCraftCliException;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception.ArtCraftNotFoundException;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception.ArtCraftSourceMissingException;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception.ArtCraftVersionConflictException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@link ArtCraftEditorService} 단위 테스트.
 *
 * <p>CLI 바이너리 없이 실행된다 — {@link ArtCraftCliExecutor}를 mock으로 대체하고
 * 프로젝트 JSON·스냅샷·학원 격리·낙관적 잠금·권한 로직만 검증한다.
 */
class ArtCraftEditorServiceTest {

  @TempDir
  Path tempDir;

  private AcademyRepository academyRepository;
  private ArtCraftCliExecutor cliExecutor;
  private ArtCraftHtmlToPdfConverter htmlToPdfConverter;
  private ArtCraftEditorService service;

  private final AuthenticatedPrincipal tutor =
      new AuthenticatedPrincipal(1L, "tutor1", "TUTOR");
  private final AuthenticatedPrincipal admin =
      new AuthenticatedPrincipal(2L, "admin1", "ADMIN");
  private final AuthenticatedPrincipal student =
      new AuthenticatedPrincipal(3L, "student1", "STUDENT");

  @BeforeEach
  void setUp() {
    academyRepository = mock(AcademyRepository.class);
    cliExecutor = mock(ArtCraftCliExecutor.class);
    htmlToPdfConverter = mock(ArtCraftHtmlToPdfConverter.class);
    ArtCraftEditorProperties props = new ArtCraftEditorProperties();
    props.setStorageRoot(tempDir.toString());
    service = new ArtCraftEditorService(
        academyRepository, props, cliExecutor, htmlToPdfConverter, new ObjectMapper());

    Academy academy = mock(Academy.class);
    when(academy.getAcademyId()).thenReturn(7L);
    when(academyRepository.findByTutor_UserId(1L)).thenReturn(Optional.of(academy));
    when(academyRepository.findByTutor_UserId(3L)).thenReturn(Optional.empty());
  }

  private CreateProjectRequest createRequest(String name) {
    return new CreateProjectRequest(name, null, null, null);
  }

  private EditProjectRequest editRequest(int expectedVersion, String tool) {
    return new EditProjectRequest(expectedVersion,
        List.of(new EditCommandRequest(tool, Map.of("doc", 1))),
        "ai-agent", "테스트 편집");
  }

  private MultipartFile pdfFile(String content) {
    return new MockMultipartFile("file", "source.pdf",
        "application/pdf", content.getBytes());
  }

  private MultipartFile htmlFile(String content) {
    return new MockMultipartFile("file", "worksheet.html",
        "text/html", content.getBytes());
  }

  /** PDF 소스를 등록해 edit 선행조건(source.pdf)을 만족시킨다. */
  private SourceUploadResponse uploadPdfSource(String projectId) {
    return service.uploadSource(tutor, projectId, null, pdfFile("fake-source-pdf"));
  }

  /** HTML→PDF 변환 mock: 변환된 PDF를 가짜 바이트로 기록한다. */
  private void mockChromiumConvert() {
    doAnswer(inv -> {
      Path pdf = inv.getArgument(1);
      Files.writeString(pdf, "fake-converted-pdf");
      return null;
    }).when(htmlToPdfConverter).convert(any(Path.class), any(Path.class));
  }

  @Test
  void createProject_createsVersion1AndSnapshot() {
    ProjectResponse res = service.createProject(tutor, createRequest("중간고사"));

    assertEquals(1, res.version());
    assertEquals(7L, res.academyId());
    assertEquals("tutor1", res.createdBy());
    assertNotNull(res.projectId());
    assertTrue(Files.isRegularFile(
        tempDir.resolve("artcraft-projects/7/" + res.projectId() + "/project.json")));
    assertTrue(Files.isRegularFile(
        tempDir.resolve("artcraft-projects/7/" + res.projectId() + "/snapshots/v1.json")));
  }

  @Test
  void editProject_success_bumpsVersionAndRunsCli() {
    ProjectResponse created = service.createProject(tutor, createRequest("기말고사"));
    uploadPdfSource(created.projectId());
    when(cliExecutor.execute(anyList(), any(Path.class), any(Path.class)))
        .thenReturn(new ArtCraftCliExecutor.CliResult(0, "ok"));

    var res = service.editProject(tutor, created.projectId(), null, editRequest(1, "doc_info"));

    assertEquals(2, res.version());
    assertTrue(res.warnings().isEmpty());
    verify(cliExecutor).execute(anyList(), any(Path.class), any(Path.class));
    assertEquals(2, service.getProject(tutor, created.projectId(), null).version());
  }

  @Test
  void editProject_staleVersion_throws409WithCurrentVersion() {
    ProjectResponse created = service.createProject(tutor, createRequest("모의고사"));
    uploadPdfSource(created.projectId());
    when(cliExecutor.execute(anyList(), any(Path.class), any(Path.class)))
        .thenReturn(new ArtCraftCliExecutor.CliResult(0, "ok"));
    service.editProject(tutor, created.projectId(), null, editRequest(1, "doc_info"));

    ArtCraftVersionConflictException e = assertThrows(
        ArtCraftVersionConflictException.class,
        () -> service.editProject(tutor, created.projectId(), null, editRequest(1, "doc_info")));
    assertEquals(2, e.getCurrentVersion());
  }

  @Test
  void editProject_disallowedTool_rejected() {
    ProjectResponse created = service.createProject(tutor, createRequest("쪽지시험"));

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> service.editProject(tutor, created.projectId(), null, editRequest(1, "doc_delete")));
    assertTrue(e.getMessage().contains("doc_delete"));
  }

  @Test
  void editProject_missingExpectedVersion_rejected() {
    ProjectResponse created = service.createProject(tutor, createRequest("단원평가"));

    assertThrows(IllegalArgumentException.class,
        () -> service.editProject(tutor, created.projectId(), null,
            new EditProjectRequest(null, List.of(new EditCommandRequest("doc_info", Map.of())),
                "ai-agent", null)));
  }

  @Test
  void editProject_userEditedBefore_aiEditWarns() {
    ProjectResponse created = service.createProject(tutor, createRequest("수행평가"));
    uploadPdfSource(created.projectId());
    when(cliExecutor.execute(anyList(), any(Path.class), any(Path.class)))
        .thenReturn(new ArtCraftCliExecutor.CliResult(0, "ok"));
    // 사용자 직접 편집 시뮬레이션
    service.editProject(tutor, created.projectId(), null,
        new EditProjectRequest(1, List.of(new EditCommandRequest("doc_info", Map.of())),
            "user", "사용자 직접 수정"));

    var res = service.editProject(tutor, created.projectId(), null, editRequest(2, "doc_info"));

    assertEquals(3, res.version());
    assertEquals(1, res.warnings().size());
  }

  @Test
  void revertProject_restoresSnapshotAsNewVersion() {
    ProjectResponse created = service.createProject(tutor, createRequest("중간고사"));
    uploadPdfSource(created.projectId());
    when(cliExecutor.execute(anyList(), any(Path.class), any(Path.class)))
        .thenReturn(new ArtCraftCliExecutor.CliResult(0, "ok"));
    service.editProject(tutor, created.projectId(), null, editRequest(1, "doc_info"));

    ProjectResponse reverted = service.revertProject(tutor, created.projectId(), null,
        new RevertProjectRequest(1, "ai-agent", "되돌리기 테스트"));

    assertEquals(3, reverted.version());
    // 되돌리기 전 상태(v2)도 스냅샷으로 보존됨
    List<SnapshotResponse> snaps = service.listSnapshots(tutor, created.projectId(), null);
    assertEquals(List.of(1, 2, 3),
        snaps.stream().map(SnapshotResponse::version).toList());
  }

  @Test
  void revertProject_unknownVersion_notFound() {
    ProjectResponse created = service.createProject(tutor, createRequest("기말고사"));

    assertThrows(ArtCraftNotFoundException.class,
        () -> service.revertProject(tutor, created.projectId(), null,
            new RevertProjectRequest(99, "ai-agent", null)));
  }

  @Test
  void editProject_docSave_registersPdfForDownload() throws Exception {
    ProjectResponse created = service.createProject(tutor, createRequest("출력물"));
    uploadPdfSource(created.projectId());
    when(cliExecutor.execute(anyList(), any(Path.class), any(Path.class)))
        .thenAnswer(inv -> {
          Path projectDir = inv.getArgument(2);
          Files.writeString(projectDir.resolve("output.pdf"), "fake-pdf-bytes");
          return new ArtCraftCliExecutor.CliResult(0, "saved");
        });

    service.editProject(tutor, created.projectId(), null,
        new EditProjectRequest(1,
            List.of(new EditCommandRequest("doc_save", Map.of("doc", 1, "path", "output.pdf"))),
            "ai-agent", "PDF 저장"));

    assertEquals("output.pdf",
        service.getProject(tutor, created.projectId(), null).pdfPath());
    var pdf = service.downloadPdf(tutor, created.projectId(), null);
    assertEquals("fake-pdf-bytes", new String(pdf.bytes()));
  }

  @Test
  void createProject_student_forbidden() {
    assertThrows(AccessDeniedException.class,
        () -> service.createProject(student, createRequest("불가")));
  }

  @Test
  void createProject_invalidProjectId_rejected() {
    assertThrows(IllegalArgumentException.class,
        () -> service.createProject(tutor,
            new CreateProjectRequest("악의적", "../evil", null, null)));
  }

  @Test
  void getProject_otherAcademyId_forbidden() {
    ProjectResponse created = service.createProject(tutor, createRequest("내학원"));

    assertThrows(AccessDeniedException.class,
        () -> service.getProject(tutor, created.projectId(), 999L));
  }

  @Test
  void createProject_adminWithoutAcademyId_rejected() {
    assertThrows(IllegalArgumentException.class,
        () -> service.createProject(admin, createRequest("관리자생성")));
  }

  @Test
  void createProject_adminWithAcademyId_ok() {
    ProjectResponse res = service.createProject(admin,
        new CreateProjectRequest("관리자생성", null, 42L, null));

    assertEquals(42L, res.academyId());
    assertTrue(Files.isRegularFile(
        tempDir.resolve("artcraft-projects/42/" + res.projectId() + "/project.json")));
  }

  @Test
  void getProject_missingProject_notFound() {
    assertThrows(ArtCraftNotFoundException.class,
        () -> service.getProject(tutor, "no-such-project", null));
  }

  @Test
  void downloadPdf_withoutPdf_notFound() {
    ProjectResponse created = service.createProject(tutor, createRequest("빈프로젝트"));

    assertThrows(ArtCraftNotFoundException.class,
        () -> service.downloadPdf(tutor, created.projectId(), null));
  }

  // -----------------------------------------------------------------------
  // 소스 업로드 (P0-④)
  // -----------------------------------------------------------------------

  @Test
  void uploadSource_pdf_savesSourcePdf() throws Exception {
    ProjectResponse created = service.createProject(tutor, createRequest("소스등록"));

    SourceUploadResponse res =
        service.uploadSource(tutor, created.projectId(), null, pdfFile("fake-source-pdf"));

    assertEquals(created.projectId(), res.projectId());
    assertEquals(7L, res.academyId());
    assertEquals(1, res.version()); // 소스 업로드로 버전 증가 없음
    assertEquals("pdf", res.sourceType());
    assertEquals("source.pdf", res.sourcePath());
    assertFalse(res.converted());
    assertEquals("fake-source-pdf", Files.readString(
        tempDir.resolve("artcraft-projects/7/" + created.projectId() + "/source.pdf")));
  }

  @Test
  void uploadSource_html_savesHtmlAndConverts() throws Exception {
    ProjectResponse created = service.createProject(tutor, createRequest("HTML소스"));
    mockChromiumConvert();

    SourceUploadResponse res =
        service.uploadSource(tutor, created.projectId(), null, htmlFile("<html>학습지</html>"));

    assertEquals("html", res.sourceType());
    assertTrue(res.converted());
    Path dir = tempDir.resolve("artcraft-projects/7/" + created.projectId());
    assertTrue(Files.isRegularFile(dir.resolve("source.html"))); // 정본 보존
    assertEquals("<html>학습지</html>", Files.readString(dir.resolve("source.html")));
    assertTrue(Files.isRegularFile(dir.resolve("source.pdf"))); // 변환 산출물
    verify(htmlToPdfConverter).convert(any(Path.class), any(Path.class));
  }

  @Test
  void uploadSource_html_chromiumMissing_throws503() {
    ProjectResponse created = service.createProject(tutor, createRequest("변환불가"));
    doThrow(new ArtCraftCliException(HttpStatus.SERVICE_UNAVAILABLE, "Chromium 없음"))
        .when(htmlToPdfConverter).convert(any(Path.class), any(Path.class));

    ArtCraftCliException e = assertThrows(ArtCraftCliException.class,
        () -> service.uploadSource(tutor, created.projectId(), null,
            htmlFile("<html>학습지</html>")));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
  }

  @Test
  void uploadSource_unsupportedType_rejected() {
    ProjectResponse created = service.createProject(tutor, createRequest("형식오류"));
    MultipartFile txt = new MockMultipartFile("file", "note.txt",
        "text/plain", "hello".getBytes());

    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> service.uploadSource(tutor, created.projectId(), null, txt));
    assertTrue(e.getMessage().contains("HTML"));
  }

  @Test
  void uploadSource_emptyFile_rejected() {
    ProjectResponse created = service.createProject(tutor, createRequest("빈파일"));
    MultipartFile empty = new MockMultipartFile("file", "empty.pdf",
        "application/pdf", new byte[0]);

    assertThrows(IllegalArgumentException.class,
        () -> service.uploadSource(tutor, created.projectId(), null, empty));
  }

  @Test
  void uploadSource_overwritesExistingSource() throws Exception {
    ProjectResponse created = service.createProject(tutor, createRequest("덮어쓰기"));
    service.uploadSource(tutor, created.projectId(), null, pdfFile("v1"));

    SourceUploadResponse res =
        service.uploadSource(tutor, created.projectId(), null, pdfFile("v2"));

    Path sourcePdf =
        tempDir.resolve("artcraft-projects/7/" + created.projectId() + "/source.pdf");
    assertEquals("v2", Files.readString(sourcePdf));
    assertEquals(1, res.version()); // 덮어써도 버전 증가 없음
  }

  @Test
  void uploadSource_pdf_removesStaleSourceHtml() throws Exception {
    ProjectResponse created = service.createProject(tutor, createRequest("정본교체"));
    mockChromiumConvert();
    service.uploadSource(tutor, created.projectId(), null, htmlFile("<html>구</html>"));
    Path dir = tempDir.resolve("artcraft-projects/7/" + created.projectId());
    assertTrue(Files.isRegularFile(dir.resolve("source.html")));

    // PDF 직접 업로드 → 정본은 PDF 자체, 불일치 가능한 이전 source.html 제거
    service.uploadSource(tutor, created.projectId(), null, pdfFile("new-pdf"));

    assertFalse(Files.exists(dir.resolve("source.html")));
    assertEquals("new-pdf", Files.readString(dir.resolve("source.pdf")));
  }

  @Test
  void uploadSource_missingProject_notFound() {
    assertThrows(ArtCraftNotFoundException.class,
        () -> service.uploadSource(tutor, "no-such-project", null, pdfFile("x")));
  }

  @Test
  void uploadSource_student_forbidden() {
    ProjectResponse created = service.createProject(tutor, createRequest("권한"));

    assertThrows(AccessDeniedException.class,
        () -> service.uploadSource(student, created.projectId(), null, pdfFile("x")));
  }

  @Test
  void editProject_withoutSource_throws409() {
    ProjectResponse created = service.createProject(tutor, createRequest("소스없음"));

    ArtCraftSourceMissingException e = assertThrows(ArtCraftSourceMissingException.class,
        () -> service.editProject(tutor, created.projectId(), null, editRequest(1, "doc_info")));

    assertTrue(e.getMessage().contains("소스를 먼저 업로드하세요"));
  }

  @Test
  void editProject_withUploadedSource_ok() {
    ProjectResponse created = service.createProject(tutor, createRequest("소스있음"));
    uploadPdfSource(created.projectId());
    when(cliExecutor.execute(anyList(), any(Path.class), any(Path.class)))
        .thenReturn(new ArtCraftCliExecutor.CliResult(0, "ok"));

    var res = service.editProject(tutor, created.projectId(), null, editRequest(1, "doc_info"));

    assertEquals(2, res.version());
  }
}

