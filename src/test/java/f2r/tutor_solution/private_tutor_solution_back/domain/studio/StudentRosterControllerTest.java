package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/** 가짜 이름·가짜 파일 ID만 사용한다. */
class StudentRosterControllerTest {
  private final JdbcTemplate db = mock(JdbcTemplate.class);
  private final StudioService studio = mock(StudioService.class);
  private final StudentRosterController controller = new StudentRosterController(studio, db);
  private final AuthenticatedPrincipal admin = new AuthenticatedPrincipal(1L, "admin", "ADMIN");

  @AfterEach
  void reset() {
    RequestContextHolder.resetRequestAttributes();
  }

  private static byte[] pdf(int pages) throws Exception {
    try (var doc = new org.apache.pdfbox.pdmodel.PDDocument(); var out = new ByteArrayOutputStream()) {
      for (int i = 0; i < pages; i++) doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
      doc.save(out);
      return out.toByteArray();
    }
  }

  @Test
  void givenTutor_whenGuard_thenForbiddenBeforeDataAccess() {
    // given
    var request = new MockHttpServletRequest("GET", "/admin/student-roster");
    // when / then
    var e = assertThrows(ResponseStatusException.class,
        () -> controller.guard(new AuthenticatedPrincipal(2L, "modu", "TUTOR"), request, new MockHttpServletResponse()));
    assertEquals(403, e.getStatusCode().value());
    verifyNoInteractions(db, studio);
  }

  @Test
  void givenForeignOrigin_whenPost_thenForbidden() {
    // given
    ReflectionTestUtils.setField(controller, "publicBase", "https://feel2real.co.kr");
    var request = new MockHttpServletRequest("POST", "/api/admin/student-roster/entries");
    request.addHeader("Origin", "https://other.example");
    // when / then
    var e = assertThrows(ResponseStatusException.class, () -> controller.guard(admin, request, new MockHttpServletResponse()));
    assertEquals(403, e.getStatusCode().value());
  }

  @Test
  void givenAdminRead_whenGuard_thenPrivateNoStoreAndFixedProject() {
    // given
    var request = new MockHttpServletRequest("GET", "/api/admin/student-roster/entries");
    request.addParameter("projectId", "ai-agent-part1");
    var response = new MockHttpServletResponse();
    // when
    controller.guard(admin, request, response);
    // then
    assertEquals("private, no-store", response.getHeader("Cache-Control"));
    assertEquals(StudentRosterController.PROJECT, request.getAttribute(StudioService.MCP_PROJECT_ATTRIBUTE));
  }

  @Test
  void givenNonFourDigitYear_whenYearOrSave_thenRejectedWithoutWrites() {
    // given / when / then
    for (String bad : List.of("26", "20266", "2O26", "", "1800"))
      assertThrows(ResponseStatusException.class, () -> StudentRosterController.year(bad), bad);
    var e = assertThrows(ResponseStatusException.class,
        () -> controller.save(admin, new StudentRosterController.Entry("26", "pdf-id", "word-id")));
    assertEquals("연도를 4자리로 적어 주세요.", e.getReason());
    assertEquals(2026, StudentRosterController.year(" 2026 "));
    verifyNoInteractions(db, studio);
  }

  @Test
  void givenImageFile_whenUpload_thenRejectedBeforeStorage() {
    // given
    var file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3});
    // when / then
    assertThrows(ResponseStatusException.class, () -> controller.upload(admin, file));
    verifyNoInteractions(db, studio);
  }

  @Test
  void givenWordFile_whenUpload_thenEnsuresProjectAndUsesStudioIntegrityPath() throws Exception {
    // given
    var file = new MockMultipartFile("file", "2026.docx", "application/octet-stream", new byte[] {80, 75, 3, 4});
    when(studio.upload(admin, file)).thenReturn(Map.of("file", Map.of("id", "w1")));
    // when
    var result = controller.upload(admin, file);
    // then
    assertEquals(Map.of("file", Map.of("id", "w1")), result);
    verify(db).update(contains("INSERT IGNORE INTO studio_projects"), eq(StudentRosterController.PROJECT), anyString());
  }

  @Test
  void givenPdfInWordSlot_whenSave_thenRejected() {
    // given
    when(studio.file(admin, "p1")).thenReturn(Map.of("extension", "pdf"));
    when(studio.file(admin, "p2")).thenReturn(Map.of("extension", "pdf"));
    // when / then
    assertThrows(ResponseStatusException.class,
        () -> controller.save(admin, new StudentRosterController.Entry("2026", "p1", "p2")));
    verify(studio, never()).save(any(), any());
  }

  @Test
  void givenExistingYear_whenSaveAgain_thenNewRevisionChainsOnCurrentHead() throws Exception {
    // given
    when(studio.file(admin, "p1")).thenReturn(Map.of("extension", "pdf"));
    when(studio.file(admin, "w1")).thenReturn(Map.of("extension", "doc"));
    when(studio.bytes(admin, "p1")).thenReturn(pdf(3));
    when(db.queryForList(contains("SELECT head_id"), eq(StudentRosterController.PROJECT), eq("roster-2026")))
        .thenReturn(List.of(Map.of("head_id", "old-head")));
    when(studio.save(eq(admin), any())).thenReturn(Map.of("id", "new-head"));
    // when
    var result = (Map<?, ?>) controller.save(admin, new StudentRosterController.Entry("2026", "p1", "w1"));
    // then
    var captor = ArgumentCaptor.forClass(StudioService.Revision.class);
    verify(studio).save(eq(admin), captor.capture());
    var r = captor.getValue();
    assertEquals("roster-2026", r.itemKey());
    assertEquals("old-head", r.parentId());
    assertEquals("2026", r.title());
    assertEquals("p1", r.fileId());
    assertEquals("w1", r.dialogue());
    assertEquals("3", r.direction());
    assertEquals("저장했습니다", result.get("message"));
    assertEquals(3, result.get("pages"));
  }

  @Test
  void givenBrokenPdf_whenPages_thenRejected() {
    // given / when / then
    assertThrows(ResponseStatusException.class, () -> StudentRosterController.pages("not pdf".getBytes()));
  }

  @Test
  void givenDbRow_whenRow_thenScreenFieldsMapped() {
    // given
    var raw = new HashMap<String, Object>(Map.of("title", "2025", "id", "r1", "file_id", "p1", "dialogue", "w1",
        "direction", "9", "created_at", "2026-11-10", "word_filename", "2025.docx", "pdf_filename", "2025.pdf"));
    raw.put("current", 1L);
    // when
    var row = StudentRosterController.row(raw);
    // then
    assertEquals(2025, row.get("year"));
    assertEquals(9, row.get("pages"));
    assertEquals("w1", row.get("wordFileId"));
    assertEquals(true, row.get("current"));
  }

  @Test
  void givenInvalidYear_whenHistory_thenRejectedWithoutQuery() {
    // given / when / then
    assertThrows(ResponseStatusException.class, () -> controller.history("abc"));
    verifyNoInteractions(db);
  }

  @Test
  void givenTutor_whenMcpRead_thenForbidden() {
    // given / when / then
    assertThrows(ResponseStatusException.class, () -> controller.mcpRead(new AuthenticatedPrincipal(2L, "t", "TUTOR")));
    verifyNoInteractions(db, studio);
  }

  @Test
  void givenAdminWithFakeDocYear_whenMcpRead_thenAggregatesWithoutNamesOrFileIds() throws Exception {
    // given
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    byte[] doc;
    try (var in = getClass().getResourceAsStream("/student-roster/fake-roster-2001.doc")) {
      doc = Objects.requireNonNull(in).readAllBytes();
    }
    when(db.queryForList(contains("FROM studio_items i JOIN studio_revisions r"), eq(StudentRosterController.PROJECT)))
        .thenReturn(List.of(Map.of("title", "2001", "id", "r1", "file_id", "pdf-secret-id", "dialogue", "word-secret-id", "direction", "2")));
    when(studio.file(admin, "word-secret-id")).thenReturn(Map.of("extension", "doc"));
    when(studio.bytes(admin, "word-secret-id")).thenReturn(doc);
    when(studio.bytes(admin, "pdf-secret-id")).thenReturn(pdf(1));
    // when
    var result = controller.mcpRead(admin);
    // then
    assertEquals(8, result.get("totalStudents"));
    assertTrue(((String) result.get("instruction")).contains("가명"));
    String text = result.toString();
    for (String hidden : List.of("가나", "다라", "마바", "pdf-secret-id", "word-secret-id")) assertFalse(text.contains(hidden), hidden);
  }

  @Test
  void givenOperatorOnGenericStudioApi_whenRosterProjectInAnyCase_thenForbidden() {
    // given — modu는 초대 없이 다른 프로젝트 EDITOR지만 학생명단은 관리자 전용
    var guard = mock(StudioAccessGuard.class);
    var modu = new AuthenticatedPrincipal(42L, "modu", "TUTOR");
    when(guard.isOperator(modu)).thenReturn(true);
    var service = new StudioService(db, mock(f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage.class));
    ReflectionTestUtils.setField(service, "access", guard);
    for (String project : List.of("student-roster", "STUDENT-ROSTER")) {
      var request = new MockHttpServletRequest();
      request.addParameter("projectId", project);
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
      // when / then
      var e = assertThrows(ResponseStatusException.class, () -> service.permission(modu), project);
      assertEquals(403, e.getStatusCode().value());
    }
    verifyNoInteractions(db);
  }
}
