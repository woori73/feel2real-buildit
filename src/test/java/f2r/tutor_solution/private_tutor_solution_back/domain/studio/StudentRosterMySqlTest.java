package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** 실제 MySQL·실제 Studio 저장소로 연도 재업로드 버전·이력, Word 허용 범위를 확인한다. 가짜 이름·가짜 파일만 쓴다. */
@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:mysql://127.0.0.1:3318/f2r_studio_test?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci",
      "spring.datasource.username=f2r_test",
      "spring.datasource.password=isolated_test_password",
      "spring.flyway.locations=classpath:db/migration",
      "spring.jpa.show-sql=false",
      "app.file.upload-dir=/tmp/f2r-student-roster-test-files"
    })
@EnabledIfEnvironmentVariable(named = "F2R_STUDIO_MYSQL_TEST", matches = "true")
@Transactional
class StudentRosterMySqlTest {
  @Autowired StudentRosterController roster;
  @Autowired StudioService studio;
  @Autowired StudioProjectService projects;
  @Autowired UserRepository users;
  @Autowired ObjectMapper json;
  AuthenticatedPrincipal admin, tutor;

  AuthenticatedPrincipal user(Role role) {
    String id = "roster-" + UUID.randomUUID().toString().substring(0, 10);
    var u = users.saveAndFlush(User.builder().loginId(id).password("{isolated-only}").name("격리 명단 담당").nickname(id).role(role).build());
    return new AuthenticatedPrincipal(u.getUserId(), u.getLoginId(), role.name(), "isolated");
  }

  void select(String project) {
    var request = new MockHttpServletRequest();
    request.setAttribute(StudioService.MCP_PROJECT_ATTRIBUTE, project);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @BeforeEach
  void setup() {
    admin = user(Role.ADMIN);
    tutor = user(Role.TUTOR);
    select(StudentRosterController.PROJECT);
  }

  @AfterEach
  void reset() {
    RequestContextHolder.resetRequestAttributes();
  }

  static byte[] pdf(int pages, String marker) throws Exception {
    try (var doc = new org.apache.pdfbox.pdmodel.PDDocument(); var out = new ByteArrayOutputStream()) {
      for (int i = 0; i < pages; i++) doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
      doc.getDocumentInformation().setTitle(marker);
      doc.save(out);
      return out.toByteArray();
    }
  }

  static byte[] docx(String fakeName) throws Exception {
    try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(); var out = new ByteArrayOutputStream()) {
      var t = doc.createTable(3, 7);
      t.getRow(0).getCell(0).setText("3월 진행표 (2026년)");
      t.getRow(2).getCell(0).setText("2");
      t.getRow(2).getCell(0).addParagraph().createRun().setText(fakeName + "1");
      doc.write(out);
      return out.toByteArray();
    }
  }

  String upload(String name, byte[] bytes) throws Exception {
    var result = (Map<?, ?>) roster.upload(admin, new MockMultipartFile("file", name, "application/octet-stream", bytes));
    return (String) ((Map<?, ?>) result.get("file")).get("id");
  }

  @Test
  void givenSameYearUploadedTwice_whenListed_thenLatestInListAndPreviousInHistory() throws Exception {
    // given
    String pdf1 = upload("2026.pdf", pdf(2, "first")), word1 = upload("2026.docx", docx("가나"));
    roster.save(admin, new StudentRosterController.Entry("2026", pdf1, word1));
    String pdf2 = upload("2026.pdf", pdf(3, "second")), word2 = upload("2026.doc",
        Objects.requireNonNull(getClass().getResourceAsStream("/student-roster/fake-roster-2001.doc")).readAllBytes());
    // when
    roster.save(admin, new StudentRosterController.Entry("2026", pdf2, word2));
    var entries = roster.entries();
    var history = roster.history("2026");
    // then
    assertEquals(1, entries.size());
    var latest = entries.getFirst();
    assertEquals(2026, latest.get("year"));
    assertEquals(pdf2, latest.get("pdfFileId"));
    assertEquals(word2, latest.get("wordFileId"));
    assertEquals(3, latest.get("pages"));
    assertEquals(2L, ((Number) latest.get("versions")).longValue());
    assertEquals(2, history.size());
    assertEquals(Set.of(pdf1, pdf2), Set.of(history.get(0).get("pdfFileId"), history.get(1).get("pdfFileId")));
    assertEquals(1, history.stream().filter(r -> Boolean.TRUE.equals(r.get("current"))).count());
    assertEquals(pdf2, history.stream().filter(r -> Boolean.TRUE.equals(r.get("current"))).findFirst().orElseThrow().get("pdfFileId"));
  }

  @Test
  void givenRenamedFakeWordFile_whenUpload_thenRejectedByContentCheck() {
    // given / when / then
    assertThrows(ResponseStatusException.class, () -> upload("2026.docx", "%PDF-1.7 not word".getBytes()));
    assertThrows(ResponseStatusException.class, () -> upload("2026.doc", new byte[] {80, 75, 3, 4, 0, 0}));
  }

  @Test
  void givenOtherStudioProject_whenUploadWord_thenStillRejected() throws Exception {
    // given
    String other = json.valueToTree(projects.create(admin, "격리 다른 제작 프로젝트")).path("id").asText();
    select(other);
    // when / then
    var e = assertThrows(ResponseStatusException.class,
        () -> studio.upload(admin, new MockMultipartFile("file", "a.docx", "application/octet-stream", docx("다라"))));
    assertEquals(400, e.getStatusCode().value());
  }

  @Test
  void givenTutor_whenGenericStudioFileRead_thenForbidden() throws Exception {
    // given
    String pdfId = upload("2025.pdf", pdf(1, "tutor"));
    // when / then
    assertThrows(ResponseStatusException.class, () -> studio.file(tutor, pdfId));
  }

  @Test
  void givenInvalidYear_whenSave_thenRejectedWithPlainMessage() {
    // given / when
    var e = assertThrows(ResponseStatusException.class, () -> roster.save(admin, new StudentRosterController.Entry("26", "x", "y")));
    // then
    assertEquals("연도를 4자리로 적어 주세요.", e.getReason());
  }
}
