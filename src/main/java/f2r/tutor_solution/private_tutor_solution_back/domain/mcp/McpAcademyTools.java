package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyPageSettingsUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyWorkingCopyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.coordinator.CoordinatorService;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.service.LmsContentAdminService;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.dto.LmsEnrollmentDtos.GrantRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.service.LmsEnrollmentService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP {@code academy:manage} — 학원 원장 계정이 말로 자기 학원을 관리한다(2026-11-19 사용자 허용).
 *
 * <ul>
 *   <li>LMS: 강좌·콘텐츠 목록, 수강 현황(가린 이름) 조회. 콘텐츠 공개/비공개와 수강 허용/회수는 <b>대화 확인 뒤</b>에만 실행한다 —
 *       1차 호출은 요약과 확인 토큰만 주고, 같은 값 + 토큰으로 다시 불러야 실행된다(토큰 30분, 서버에 저장하지 않음).
 *   <li>홈페이지: 작업본(공개본 아님)의 소개·문구만 바꾼다. 공개 게시는 하지 않는다 — 원장이 편집기에서 직접 게시한다.
 *   <li>모든 호출은 그 계정이 소유한 학원에만 미친다(기존 서비스의 소유자 검사를 그대로 쓴다).
 * </ul>
 *
 * <p>기존 학원·LMS 서비스는 SecurityContext에서 로그인 사용자를 읽으므로, MCP 호출 동안에만 MCP 연결의 사용자로 SecurityContext를
 * 채우고 끝나면 원래대로 되돌린다({@link #asUser}).
 */
@Component
public class McpAcademyTools {

  static final long CONFIRM_TTL_SECONDS = 1800;
  /** 말로 바꿀 수 있는 홈페이지 문구(작업본 builderDraft 키). 색·레이아웃·영상·JSON 설정은 제외. */
  static final Map<String, String> TEXT_FIELDS = textFields();
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final AcademyRepository academies;
  private final AcademyService academyService;
  private final AcademyWorkingCopyService workingCopy;
  private final LmsContentAdminService lmsContents;
  private final LmsEnrollmentService enrollments;
  private final Clock clock;
  private final byte[] secret;

  @Autowired
  public McpAcademyTools(AcademyRepository academies, AcademyService academyService,
      AcademyWorkingCopyService workingCopy, LmsContentAdminService lmsContents, LmsEnrollmentService enrollments) {
    this(academies, academyService, workingCopy, lmsContents, enrollments, Clock.systemUTC(), randomSecret());
  }

  McpAcademyTools(AcademyRepository academies, AcademyService academyService, AcademyWorkingCopyService workingCopy,
      LmsContentAdminService lmsContents, LmsEnrollmentService enrollments, Clock clock, byte[] secret) {
    this.academies = academies;
    this.academyService = academyService;
    this.workingCopy = workingCopy;
    this.lmsContents = lmsContents;
    this.enrollments = enrollments;
    this.clock = clock;
    this.secret = secret.clone();
  }

  /* ---------------- LMS ---------------- */

  /** 강좌와 콘텐츠(공개 상태) 목록. */
  public Map<String, Object> lmsOverview(AuthenticatedPrincipal p, String base) {
    return asUser(p, () -> {
      String handle = handle(p);
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("academyHandle", handle);
      out.put("lessons", academyService.listOwnedLmsLessonsByHandle(handle).stream().map(l -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lessonId", l.lessonId());
        m.put("name", l.name());
        m.put("schoolName", l.schoolName());
        m.put("gradeLabel", l.gradeLabel());
        return m;
      }).toList());
      out.put("contents", lmsContents.list(handle).contents().stream().map(c -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contentId", c.lmsContentId());
        m.put("lessonId", c.lessonId());
        m.put("lessonName", c.lessonName());
        m.put("title", c.title());
        m.put("status", c.status());
        m.put("publishedAt", c.publishedAt() == null ? null : c.publishedAt().toString());
        return m;
      }).toList());
      out.put("manageUrl", base + "/lms/" + handle + "/manage");
      return out;
    });
  }

  /** 강좌 하나의 수강 현황: 수강 중인 학생·수강 가능한 학생(가린 이름). */
  public Map<String, Object> lmsEnrollments(AuthenticatedPrincipal p, Long lessonId) {
    return asUser(p, () -> {
      var view = enrollments.getView(handle(p), require(lessonId, "lessonId"));
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("lessonId", view.lessonId());
      out.put("enrollments", view.enrollments().stream().map(e -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enrollmentId", e.lmsEnrollmentId());
        m.put("studentId", e.studentId());
        m.put("displayName", CoordinatorService.maskName(e.studentName()));
        m.put("status", e.status());
        return m;
      }).toList());
      out.put("candidates", view.candidates().stream().filter(c -> !c.enrolled()).map(c -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("studentId", c.studentId());
        m.put("displayName", CoordinatorService.maskName(c.studentName()));
        m.put("gradeLabel", c.gradeLabel());
        return m;
      }).toList());
      out.put("note", "이름은 가려서 보여 줍니다. 같은 이름이 여럿이면 학년을 보여 주고 사용자가 고르게 하세요.");
      return out;
    });
  }

  /**
   * LMS 변경: PUBLISH·UNPUBLISH {contentId} / GRANT {lessonId, studentId} / REVOKE {lessonId, enrollmentId}.
   * confirm_token이 없으면 실행하지 않고 요약 + 토큰을 준다.
   */
  public Map<String, Object> lmsChange(AuthenticatedPrincipal p, String grantId, JsonNode a) {
    return asUser(p, () -> {
      String handle = handle(p);
      String action = text(a, "action").toUpperCase();
      Map<String, Object> payload = new TreeMap<>();
      payload.put("action", action);
      String summary;
      switch (action) {
        case "PUBLISH", "UNPUBLISH" -> {
          long contentId = require(num(a, "contentId"), "contentId");
          var c = lmsContents.list(handle).contents().stream().filter(x -> x.lmsContentId() == contentId).findFirst()
              .orElseThrow(() -> new IllegalArgumentException("내 학원의 콘텐츠를 찾을 수 없습니다: " + contentId));
          payload.put("contentId", contentId);
          summary = "LMS 콘텐츠 「" + c.title() + "」(" + c.lessonName() + ") " + ("PUBLISH".equals(action) ? "공개" : "비공개로 전환")
              + " — 지금 상태 " + c.status();
        }
        case "GRANT" -> {
          long lessonId = require(num(a, "lessonId"), "lessonId");
          long studentId = require(num(a, "studentId"), "studentId");
          var cand = enrollments.getView(handle, lessonId).candidates().stream().filter(x -> x.studentId() == studentId)
              .findFirst().orElseThrow(() -> new IllegalArgumentException("이 강좌에 수강을 줄 수 있는 학생이 아닙니다: " + studentId));
          if (cand.enrolled()) throw new IllegalStateException("이미 수강 중인 학생입니다.");
          payload.put("lessonId", lessonId);
          payload.put("studentId", studentId);
          summary = CoordinatorService.maskName(cand.studentName()) + " 학생에게 강좌 #" + lessonId + " 수강 허용";
        }
        case "REVOKE" -> {
          long lessonId = require(num(a, "lessonId"), "lessonId");
          long enrollmentId = require(num(a, "enrollmentId"), "enrollmentId");
          var e = enrollments.getView(handle, lessonId).enrollments().stream()
              .filter(x -> x.lmsEnrollmentId() == enrollmentId).findFirst()
              .orElseThrow(() -> new IllegalArgumentException("이 강좌의 수강 기록을 찾을 수 없습니다: " + enrollmentId));
          payload.put("lessonId", lessonId);
          payload.put("enrollmentId", enrollmentId);
          summary = CoordinatorService.maskName(e.studentName()) + " 학생의 강좌 #" + lessonId + " 수강 회수";
        }
        default -> throw new IllegalArgumentException("action은 PUBLISH·UNPUBLISH·GRANT·REVOKE 중 하나입니다. 콘텐츠 올리기·삭제는 LMS 화면에서 합니다.");
      }
      String token = text(a, "confirm_token");
      String subject = grantId + "|" + p.userId() + "|" + JSON.writeValueAsString(payload);
      if (token.isEmpty()) {
        long expires = clock.instant().getEpochSecond() + CONFIRM_TTL_SECONDS;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "needs_confirmation");
        out.put("summary", summary);
        out.put("confirm_token", expires + "." + digest(subject, expires));
        out.put("message", "아직 바꾸지 않았습니다. summary를 사용자에게 보여 주고 분명히 동의하면 같은 값에 confirm_token만 더해"
            + " 다시 부르세요. 사용자 답 없이 다시 부르지 마세요.");
        return out;
      }
      verify(token, subject);
      Map<String, Object> out = new LinkedHashMap<>();
      switch (action) {
        case "PUBLISH", "UNPUBLISH" -> {
          var c = lmsContents.setPublished(handle, (Long) payload.get("contentId"), "PUBLISH".equals(action));
          out.put("contentId", c.lmsContentId());
          out.put("contentStatus", c.status());
          out.put("undo", "되돌리려면 같은 콘텐츠로 " + ("PUBLISH".equals(action) ? "UNPUBLISH" : "PUBLISH") + "를 요청하세요.");
        }
        case "GRANT" -> {
          var e = enrollments.grant(handle, (Long) payload.get("lessonId"), new GrantRequest((Long) payload.get("studentId"), null));
          out.put("enrollmentId", e.lmsEnrollmentId());
          out.put("undo", "되돌리려면 REVOKE {lessonId, enrollmentId:" + e.lmsEnrollmentId() + "}를 요청하세요.");
        }
        default -> {
          enrollments.revoke(handle, (Long) payload.get("lessonId"), (Long) payload.get("enrollmentId"));
          out.put("undo", "다시 허용하려면 GRANT를 요청하세요.");
        }
      }
      out.put("status", "done");
      out.put("summary", summary);
      return out;
    });
  }

  /* ---------------- 홈페이지 작업본 ---------------- */

  /** 작업본의 소개·문구(말로 바꿀 수 있는 항목만)와 revision, 미리보기·편집기 링크. */
  public Map<String, Object> homepageRead(AuthenticatedPrincipal p, String base) {
    return asUser(p, () -> state(workingCopy.open(p), base));
  }

  /**
   * 작업본에 소개(intro)·문구(texts)를 저장한다. 공개 홈페이지는 바뀌지 않는다. revision은 homepageRead가 준 값(다른 창 수정 충돌 방지).
   */
  public Map<String, Object> homepageDraft(AuthenticatedPrincipal p, JsonNode a, String base) {
    return asUser(p, () -> {
      Long revision = num(a, "revision");
      if (revision == null) throw new IllegalArgumentException("revision이 필요합니다. 먼저 academy_homepage_read로 작업본을 여세요.");
      String intro = a.hasNonNull("intro") ? a.get("intro").asText() : null;
      Map<String, String> patch = new LinkedHashMap<>();
      if (a.has("texts") && a.get("texts").isObject()) {
        for (var e : a.get("texts").properties()) {
          if (!TEXT_FIELDS.containsKey(e.getKey())) {
            throw new IllegalArgumentException("말로 바꿀 수 없는 항목입니다: " + e.getKey() + " (색·레이아웃·영상 등은 편집기에서)");
          }
          patch.put(e.getKey(), e.getValue().asText());
        }
      }
      if (intro == null && patch.isEmpty()) throw new IllegalArgumentException("바꿀 intro 또는 texts를 주세요.");
      var settings = patch.isEmpty() ? null
          : new AcademyPageSettingsUpdateRequest(null, null, null, null, null, null, null, patch);
      Map<String, Object> out = state(workingCopy.save(p, revision, intro, settings), base);
      out.put("message", "작업본에만 저장했습니다. 공개 홈페이지는 그대로입니다. 미리보기(previewUrl)로 확인한 뒤 편집기(editorUrl)에서"
          + " 원장이 직접 「게시」를 눌러야 공개됩니다. 되돌리려면 이전 값으로 다시 저장하거나 편집기에서 작업본을 버리세요.");
      return out;
    });
  }

  /* ---------------- 내부 ---------------- */

  private Map<String, Object> state(AcademyWorkingCopyService.State s, String base) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("revision", s.revision());
    out.put("intro", s.intro());
    Map<String, String> draft = s.pageSettings() == null || s.pageSettings().builderDraft() == null ? Map.of()
        : s.pageSettings().builderDraft();
    List<Map<String, Object>> texts = new ArrayList<>();
    TEXT_FIELDS.forEach((k, label) -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("key", k);
      m.put("label", label);
      m.put("value", draft.getOrDefault(k, ""));
      texts.add(m);
    });
    out.put("texts", texts);
    out.put("dirty", s.dirty());
    out.put("previewUrl", base + "/tutor/academy/preview");
    out.put("editorUrl", base + "/tutor/academy");
    return out;
  }

  private String handle(AuthenticatedPrincipal p) {
    Academy academy = academies.findByTutor_UserId(p.userId())
        .orElseThrow(() -> new IllegalArgumentException("이 계정이 운영하는 학원을 찾을 수 없습니다."));
    return academy.getHandle();
  }

  /** 기존 학원·LMS 서비스의 소유자 검사(SecurityContext)가 MCP 연결 사용자를 보게 한다. 호출이 끝나면 원래대로. */
  static <T> T asUser(AuthenticatedPrincipal p, Supplier<T> body) {
    SecurityContext previous = SecurityContextHolder.getContext();
    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(new UsernamePasswordAuthenticationToken(p, null,
        List.of(new SimpleGrantedAuthority("ROLE_" + p.role()))));
    SecurityContextHolder.setContext(ctx);
    try {
      return body.get();
    } finally {
      SecurityContextHolder.setContext(previous);
    }
  }

  String digest(String subject, long expires) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal((subject + "|" + expires).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private void verify(String token, String subject) {
    String[] parts = token.split("\\.", 2);
    long expires;
    try {
      expires = Long.parseLong(parts[0]);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("confirm_token 형식이 맞지 않습니다. 토큰 없이 다시 불러 사용자 확인을 받으세요.");
    }
    if (parts.length != 2) throw new IllegalArgumentException("confirm_token 형식이 맞지 않습니다.");
    if (expires < clock.instant().getEpochSecond()) {
      throw new IllegalArgumentException("확인 시간(30분)이 지났습니다. 토큰 없이 다시 불러 사용자 확인을 받으세요.");
    }
    if (!MessageDigest.isEqual(parts[1].getBytes(StandardCharsets.UTF_8),
        digest(subject, expires).getBytes(StandardCharsets.UTF_8))) {
      throw new IllegalArgumentException("확인한 내용과 실행하려는 내용이 다릅니다. 토큰 없이 다시 불러 사용자 확인을 받으세요.");
    }
  }

  private static byte[] randomSecret() {
    byte[] b = new byte[32];
    new SecureRandom().nextBytes(b);
    return b;
  }

  private static Long num(JsonNode a, String f) {
    return a != null && a.hasNonNull(f) && a.get(f).canConvertToLong() ? a.get(f).asLong() : null;
  }

  private static long require(Long v, String f) {
    if (v == null) throw new IllegalArgumentException(f + "가 필요합니다.");
    return v;
  }

  private static String text(JsonNode a, String f) {
    return a != null && a.hasNonNull(f) ? a.get(f).asText().strip() : "";
  }

  private static Map<String, String> textFields() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("headerTitle", "상단 학원 이름");
    m.put("heroAccent", "첫 화면 강조 문구");
    m.put("introHeading", "소개 제목");
    m.put("introMotto", "소개 표어");
    m.put("introBodySecondary", "소개 본문(추가)");
    m.put("introFeatures", "소개 특징");
    m.put("philosophyTitle", "교육 철학 제목");
    m.put("philosophyItem1", "교육 철학 1");
    m.put("philosophyItem2", "교육 철학 2");
    m.put("philosophyItem3", "교육 철학 3");
    m.put("philosophyItem4", "교육 철학 4");
    m.put("curriculumTitle", "커리큘럼 제목");
    m.put("curriculumSubtitle", "커리큘럼 부제");
    m.put("principalDesc", "원장 소개");
    m.put("principalQuote", "원장 한마디");
    m.put("principalCareer", "원장 경력");
    m.put("featuredTitle", "추천 수업 제목");
    m.put("featuredDescription", "추천 수업 설명");
    m.put("featuredItem1", "추천 항목 1");
    m.put("featuredItem2", "추천 항목 2");
    m.put("featuredItem3", "추천 항목 3");
    m.put("featuredItem4", "추천 항목 4");
    m.put("ctaTitle", "상담 안내 제목");
    m.put("ctaSubtitle", "상담 안내 문구");
    return java.util.Collections.unmodifiableMap(m);
  }
}
