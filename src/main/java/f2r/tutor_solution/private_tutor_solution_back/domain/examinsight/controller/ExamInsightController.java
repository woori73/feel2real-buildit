package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightException;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightPromptBuilder.Meta;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightService;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightTextExtractor;
import f2r.tutor_solution.private_tutor_solution_back.program.Program;
import f2r.tutor_solution.private_tutor_solution_back.program.ProgramEntry;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

/**
 * 내신영어 기출분석 + 출제방향 제작.
 *
 * <p>화면({@code /exam-analysis})은 비회원에게 공개 소개를 보여 주고, 학생·강사·관리자 로그인 후 도구 화면을 연다.
 * API({@code /api/exam-insight/**})는 SecurityConfig에서 같은 세 역할만 허용한다.
 */
@Controller
public class ExamInsightController {

  private final ExamInsightService service;

  public ExamInsightController(ExamInsightService service) {
    this.service = service;
  }

  @GetMapping("/exam-analysis")
  public String page(Authentication auth, Model model, HttpServletResponse response) {
    ProgramEntry.State state = ProgramEntry.stateOf(Program.EXAM_ANALYSIS, auth);
    if (state != ProgramEntry.State.ALLOWED) {
      return ProgramEntry.intro(Program.EXAM_ANALYSIS, state, model, response);
    }
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return "public/exam_insight";
  }

  /** 화면이 정확한 안내를 보이도록 지원 형식·한도·남은 횟수를 알려 준다. */
  @GetMapping("/api/exam-insight/config")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> config(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    Map<String, Object> body = new HashMap<>();
    body.put("enabled", service.isEnabled());
    body.put("supportedFormats", ExamInsightTextExtractor.SUPPORTED_EXTENSIONS);
    body.put("unsupported", List.of("이미지(JPG·PNG 등)", "스캔한 PDF", "HWP(구형)", "DOC·PPT·XLS"));
    body.put("maxFilesPerGroup", service.maxFilesPerGroup());
    body.put("perUserDailyLimit", service.perUserDailyLimit());
    body.put("remainingToday", principal == null ? 0 : service.remainingToday(principal.userId()));
    return noStore(ResponseEntity.ok(), body);
  }

  @PostMapping(value = "/api/exam-insight/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseBody
  public ResponseEntity<Map<String, Object>> analyze(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestPart(value = "pastFiles", required = false) List<MultipartFile> pastFiles,
      @RequestPart(value = "scopeFiles", required = false) List<MultipartFile> scopeFiles,
      @RequestParam(value = "pastText", required = false) String pastText,
      @RequestParam(value = "scopeText", required = false) String scopeText,
      @RequestParam(value = "school", required = false) String school,
      @RequestParam(value = "grade", required = false) String grade,
      @RequestParam(value = "publisher", required = false) String publisher,
      @RequestParam(value = "examType", required = false) String examType,
      @RequestParam(value = "note", required = false) String note) {
    ExamInsightService.Result r = service.analyze(
        principal.userId(),
        new ExamInsightService.Input(
            pastFiles, scopeFiles, pastText, scopeText, new Meta(school, grade, publisher, examType), note));
    Map<String, Object> body = new HashMap<>();
    body.put("status", "done");
    body.put("analysisId", r.analysisId());
    body.put("analysisMarkdown", r.analysisMarkdown());
    body.put("directionMarkdown", r.directionMarkdown());
    body.put("truncated", r.truncated());
    body.put("notices", r.notices());
    body.put("model", r.model());
    body.put("remainingToday", r.remainingToday());
    return noStore(ResponseEntity.ok(), body);
  }

  @ExceptionHandler(ExamInsightException.class)
  @ResponseBody
  public ResponseEntity<Map<String, Object>> handle(ExamInsightException e) {
    return noStore(ResponseEntity.status(e.getStatus()), Map.of("status", "error", "code", e.getCode(), "message", e.getMessage()));
  }

  private static ResponseEntity<Map<String, Object>> noStore(
      ResponseEntity.BodyBuilder builder, Map<String, Object> body) {
    return builder.cacheControl(CacheControl.noStore()).body(body);
  }
}
