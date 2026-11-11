package f2r.tutor_solution.private_tutor_solution_back.domain.academyhome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudentRosterController;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioService;
import f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant.AcademyAssistantAdapter;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/** 정보 화면 서비스 규칙: 권한·자료 묶음 제한·연결 확인 검증. 외부 호출은 모두 mock이다. */
class AcademyMaterialServiceTest {
  final JdbcTemplate db = mock(JdbcTemplate.class);
  final StudioService studio = mock(StudioService.class);
  final StudentRosterController roster = mock(StudentRosterController.class);
  final AcademyAssistantAdapter academy = mock(AcademyAssistantAdapter.class);
  final AcademyRosterStudentLinkRepository links = mock(AcademyRosterStudentLinkRepository.class);
  final AcademyMaterialService service = new AcademyMaterialService(db, studio, roster, academy, links);
  final ObjectMapper json = new ObjectMapper();
  final AuthenticatedPrincipal admin = new AuthenticatedPrincipal(1L, "admin", "ADMIN");
  final AuthenticatedPrincipal tutor = new AuthenticatedPrincipal(2L, "tutor", "TUTOR");
  MockHttpServletRequest request;

  @BeforeEach
  void bind() {
    request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @AfterEach
  void clear() { RequestContextHolder.resetRequestAttributes(); }

  @Test
  @DisplayName("given 강사 계정 when 정보 목록을 열면 then 관리자 전용 자료를 조회하지 않고 이유를 알린다")
  void tutorSeesNoAdminMaterials() {
    // when
    var out = service.list(tutor);
    // then
    assertThat((List<?>) out.get("items")).isEmpty();
    assertThat(out.get("notice").toString()).contains("관리자 전용");
    verifyNoInteractions(db);
  }

  @Test
  @DisplayName("given 허용되지 않은 자료 묶음 when 원문을 읽으면 then 400으로 막고 파일을 열지 않는다")
  void rejectsOtherProjects() {
    assertThatThrownBy(() -> service.read(admin, "studio-main", "f-1")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("자료 묶음");
    verifyNoInteractions(studio);
  }

  @Test
  @DisplayName("given 학생명단 파일 when 원문을 읽으면 then 기존 스튜디오 권한 경로로 열고 그 묶음으로 검사한다")
  void readsThroughStudioPermission() throws Exception {
    // given
    when(studio.file(admin, "f-1")).thenReturn(Map.of("extension", "txt", "filename", "명단.txt", "size_bytes", 10L));
    when(studio.bytes(admin, "f-1")).thenReturn("가상 본문".getBytes());
    // when
    var out = service.read(admin, StudentRosterController.PROJECT, "f-1");
    // then
    assertThat(request.getAttribute(StudioService.MCP_PROJECT_ATTRIBUTE)).isEqualTo(StudentRosterController.PROJECT);
    assertThat(((MaterialTextExtractor.Result) out.get("result")).status()).isEqualTo("READ");
    assertThat(out.get("original")).isEqualTo("/api/studio/files/f-1/content?projectId=student-roster");
  }

  @Test
  @DisplayName("given 연결 확인 when 명단에 없는 이름이면 then 404, 재원 학생이 아니면 400으로 저장하지 않는다")
  void linkValidation() throws Exception {
    // given
    when(academy.owner(admin, 12L)).thenReturn(json.readTree("{\"academyId\":12,\"role\":\"ACADEMY_OWNER\"}"));
    when(roster.lessonsOf(admin, 2026)).thenReturn(List.of(new StudentRosterController.RosterLesson(LocalDate.of(2026, 3, 2), "가상가", 1)));
    when(academy.base(12L)).thenReturn("/api/rea/12");
    when(academy.request(eq(admin), eq("GET"), eq("/api/rea/12/students"), isNull())).thenReturn(json.readTree("[{\"id\":5,\"name\":\"가상가\"}]"));
    when(academy.request(eq(admin), eq("GET"), eq("/api/rea/12/students/inactive"), isNull())).thenReturn(json.readTree("[{\"id\":6,\"name\":\"가상가\"}]"));
    // when / then
    assertThatThrownBy(() -> service.link(admin, new AcademyMaterialService.LinkRequest(12L, 2026, "없는이름", "LINKED", 5L))).hasMessageContaining("명단에 없는");
    assertThatThrownBy(() -> service.link(admin, new AcademyMaterialService.LinkRequest(12L, 2026, "가상가", "LINKED", 6L))).hasMessageContaining("재원 학생");
    assertThatThrownBy(() -> service.link(admin, new AcademyMaterialService.LinkRequest(12L, 2026, "가상가", "MERGE", 5L))).hasMessageContaining("연결 또는 제외");
    verify(links, never()).save(any());
  }

  @Test
  @DisplayName("given 원장이 아닌 연결 when 연결을 확인하면 then 기존 원장 확인 규칙으로 막는다")
  void linkRequiresOwner() {
    when(academy.owner(tutor, 12L)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "원장만"));
    assertThatThrownBy(() -> service.link(tutor, new AcademyMaterialService.LinkRequest(12L, 2026, "가상가", "EXCLUDED", null))).hasMessageContaining("원장만");
    verify(links, never()).save(any());
  }

  @Test
  @DisplayName("given 다른 학원 문맥 when 대조를 요청하면 then 403으로 막는다")
  void reconcileChecksAcademy() throws Exception {
    when(academy.context(admin, 20L)).thenReturn(json.readTree("{\"academyId\":12}"));
    assertThatThrownBy(() -> service.reconcile(admin, 2026, 20L)).hasMessageContaining("권한");
    verifyNoInteractions(roster);
  }
}
