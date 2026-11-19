package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyPageSettingsUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyWorkingCopyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsContentDtos.AdminContentView;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsContentDtos.AdminContentsView;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.service.LmsContentAdminService;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.dto.LmsEnrollmentDtos.CandidateStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.dto.LmsEnrollmentDtos.EnrollmentView;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.dto.LmsEnrollmentDtos.LessonEnrollmentView;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.service.LmsEnrollmentService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayName("McpAcademyTools — 원장 계정 LMS 공개·수강(대화 확인 후)·홈페이지 작업본")
class McpAcademyToolsTest {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final AuthenticatedPrincipal OWNER = new AuthenticatedPrincipal(7L, "owner", "TUTOR");
  static final byte[] KEY = new byte[32];
  AcademyService academyService;
  AcademyWorkingCopyService workingCopy;
  LmsContentAdminService contents;
  LmsEnrollmentService enrollments;
  AcademyRepository academies;

  @BeforeEach
  void setUp() {
    academies = mock(AcademyRepository.class);
    academyService = mock(AcademyService.class);
    workingCopy = mock(AcademyWorkingCopyService.class);
    contents = mock(LmsContentAdminService.class);
    enrollments = mock(LmsEnrollmentService.class);
    Academy academy = mock(Academy.class);
    when(academy.getHandle()).thenReturn("edu");
    when(academies.findByTutor_UserId(7L)).thenReturn(Optional.of(academy));
    when(contents.list("edu")).thenReturn(new AdminContentsView(List.of(), List.of(new AdminContentView(
        30L, 5L, "중2 문법", "3과 관계대명사", null, "DRAFT", null, null, null, null, 1, true, 600, 1000L, null, null))));
  }

  McpAcademyTools tools(Instant now) {
    return new McpAcademyTools(academies, academyService, workingCopy, contents, enrollments,
        Clock.fixed(now, ZoneOffset.UTC), KEY);
  }

  ObjectNode args() {
    return JSON.createObjectNode();
  }

  @Test
  @DisplayName("given 공개 요청 when 토큰 없이 then 실행 안 하고 요약·토큰, 같은 값+토큰이면 공개 실행")
  void publishNeedsConfirmation() {
    // given
    var t = tools(Instant.parse("2026-11-19T10:00:00Z"));
    var a = args().put("action", "publish").put("contentId", 30);

    // when
    Map<String, Object> first = t.lmsChange(OWNER, "g1", a);
    when(contents.setPublished("edu", 30L, true)).thenReturn(new AdminContentView(
        30L, 5L, "중2 문법", "3과 관계대명사", null, "PUBLISHED", null, null, null, null, 1, true, 600, 1000L, null, null));
    Map<String, Object> done = t.lmsChange(OWNER, "g1", a.deepCopy().put("confirm_token", (String) first.get("confirm_token")));

    // then
    assertThat(first).containsEntry("status", "needs_confirmation");
    assertThat((String) first.get("summary")).contains("3과 관계대명사", "공개");
    assertThat(done).containsEntry("status", "done").containsEntry("contentStatus", "PUBLISHED");
    verify(contents).setPublished("edu", 30L, true);
  }

  @Test
  @DisplayName("given 받은 토큰 when 다른 콘텐츠·다른 연결·30분 뒤 then 거절하고 실행하지 않는다")
  void tokenBoundToContentGrantAndTime() {
    // given
    var t = tools(Instant.parse("2026-11-19T10:00:00Z"));
    when(contents.list("edu")).thenReturn(new AdminContentsView(List.of(), List.of(
        new AdminContentView(30L, 5L, "중2", "A", null, "DRAFT", null, null, null, null, 1, true, 1, 1L, null, null),
        new AdminContentView(31L, 5L, "중2", "B", null, "DRAFT", null, null, null, null, 2, true, 1, 1L, null, null))));
    String token = (String) t.lmsChange(OWNER, "g1", args().put("action", "PUBLISH").put("contentId", 30)).get("confirm_token");

    // when & then
    assertThatThrownBy(() -> t.lmsChange(OWNER, "g1", args().put("action", "PUBLISH").put("contentId", 31).put("confirm_token", token)))
        .hasMessageContaining("다릅니다");
    assertThatThrownBy(() -> t.lmsChange(OWNER, "g2", args().put("action", "PUBLISH").put("contentId", 30).put("confirm_token", token)))
        .hasMessageContaining("다릅니다");
    var later = tools(Instant.parse("2026-11-19T10:31:00Z"));
    assertThatThrownBy(() -> later.lmsChange(OWNER, "g1", args().put("action", "PUBLISH").put("contentId", 30).put("confirm_token", token)))
        .hasMessageContaining("30분");
    verify(contents, never()).setPublished(anyString(), anyLong(), anyBoolean());
  }

  @Test
  @DisplayName("given 수강 허용 when 이미 수강 중·없는 학생·모르는 action then 거절, 정상이면 가린 이름 요약")
  void grantChecks() {
    // given
    var t = tools(Instant.parse("2026-11-19T10:00:00Z"));
    when(enrollments.getView("edu", 5L)).thenReturn(new LessonEnrollmentView(5L,
        List.of(new CandidateStudent(11L, "김민수", "가짜중", "중2", false), new CandidateStudent(12L, "이수", "가짜중", "중2", true)),
        List.of(new EnrollmentView(90L, 12L, "이수", "ACTIVE", "MANUAL", null, null))));

    // when
    Map<String, Object> ok = t.lmsChange(OWNER, "g1", args().put("action", "GRANT").put("lessonId", 5).put("studentId", 11));

    // then
    assertThat((String) ok.get("summary")).contains("김○수").doesNotContain("김민수");
    assertThatThrownBy(() -> t.lmsChange(OWNER, "g1", args().put("action", "GRANT").put("lessonId", 5).put("studentId", 12)))
        .hasMessageContaining("이미 수강");
    assertThatThrownBy(() -> t.lmsChange(OWNER, "g1", args().put("action", "GRANT").put("lessonId", 5).put("studentId", 99)))
        .hasMessageContaining("학생이 아닙니다");
    assertThatThrownBy(() -> t.lmsChange(OWNER, "g1", args().put("action", "DELETE"))).hasMessageContaining("PUBLISH");
    verify(enrollments, never()).grant(anyString(), anyLong(), any());
  }

  @Test
  @DisplayName("given 홈페이지 문구 when 작업본 저장 then 허용 문구만 builderDraft 부분 변경으로, 공개 게시는 하지 않는다")
  void homepageDraftOnlyWorkingCopy() {
    // given
    var t = tools(Instant.parse("2026-11-19T10:00:00Z"));
    var state = new AcademyWorkingCopyService.State(4L, "소개", new AcademyPageSettings(null, null, null, null, null, null,
        Map.of("ctaTitle", "겨울특강 안내"), null), true, false);
    when(workingCopy.save(eq(OWNER), eq(3L), eq(null), any())).thenReturn(state);
    var a = args().put("revision", 3);
    a.putObject("texts").put("ctaTitle", "겨울특강 안내");

    // when
    Map<String, Object> out = t.homepageDraft(OWNER, a, "https://f2r.test");

    // then
    verify(workingCopy).save(OWNER, 3L, null,
        new AcademyPageSettingsUpdateRequest(null, null, null, null, null, null, null, Map.of("ctaTitle", "겨울특강 안내")));
    verify(workingCopy, never()).publish(any(), anyLong(), anyBoolean());
    assertThat(out).containsEntry("revision", 4L).containsEntry("editorUrl", "https://f2r.test/tutor/academy");
    assertThat((String) out.get("message")).contains("작업본에만", "직접");
  }

  @Test
  @DisplayName("given 색·영상 같은 허용 밖 항목·revision 없음·바꿀 것 없음 when 작업본 저장 then 거절")
  void homepageRejects() {
    // given
    var t = tools(Instant.parse("2026-11-19T10:00:00Z"));
    var bad = args().put("revision", 3);
    bad.putObject("texts").put("colorPrimary", "#000");

    // when & then
    assertThatThrownBy(() -> t.homepageDraft(OWNER, bad, "")).hasMessageContaining("말로 바꿀 수 없는");
    assertThatThrownBy(() -> t.homepageDraft(OWNER, args().put("intro", "x"), "")).hasMessageContaining("revision");
    assertThatThrownBy(() -> t.homepageDraft(OWNER, args().put("revision", 3), "")).hasMessageContaining("intro 또는 texts");
    verify(workingCopy, never()).save(any(), anyLong(), any(), any());
  }

  @Test
  @DisplayName("given MCP 사용자 when asUser 안에서 then SecurityContext가 그 사용자, 끝나면 원래대로")
  void asUserRestoresContext() {
    // given
    SecurityContextHolder.clearContext();

    // when
    Object inside = McpAcademyTools.asUser(OWNER, () -> SecurityContextHolder.getContext().getAuthentication().getPrincipal());

    // then
    assertThat(inside).isEqualTo(OWNER);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
