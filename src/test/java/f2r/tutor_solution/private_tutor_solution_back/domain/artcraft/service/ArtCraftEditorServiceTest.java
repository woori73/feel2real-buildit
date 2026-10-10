package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception.ArtCraftNotFoundException;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception.ArtCraftVersionConflictException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.AccessDeniedException;

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
    ArtCraftEditorProperties props = new ArtCraftEditorProperties();
    props.setStorageRoot(tempDir.toString());
    service = new ArtCraftEditorService(
        academyRepository, props, cliExecutor, new ObjectMapper());

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
}
