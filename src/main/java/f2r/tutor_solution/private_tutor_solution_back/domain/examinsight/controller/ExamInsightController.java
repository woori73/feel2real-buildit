package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightException;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightPromptBuilder.Meta;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightService;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightTextExtractor;
import f2r.tutor_solution.private_tutor_solution_back.program.Program;
import f2r.tutor_solution.private_tutor_solution_back.program.ProgramEntry;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
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

  private static final int MAX_ITEM_JSON_CHARS = 1_000_000;

  private final ExamInsightService service;
  private final ObjectMapper objectMapper = new ObjectMapper();

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
    boolean images = service.imageAnalysisAvailable();
    // 사진은 이미지 인식이 실제로 설정돼 있을 때만 지원 형식에 넣는다(읽지 못하는 것을 읽는다고 안내하지 않는다).
    List<String> supported = new ArrayList<>(ExamInsightTextExtractor.SUPPORTED_EXTENSIONS);
    if (images) supported.addAll(ExamInsightTextExtractor.IMAGE_EXTENSIONS);
    body.put("supportedFormats", supported);
    body.put("documentFormats", ExamInsightTextExtractor.DOCUMENT_EXTENSIONS);
    body.put("webFormats", ExamInsightTextExtractor.WEB_EXTENSIONS);
    body.put("imageFormats", images ? ExamInsightTextExtractor.IMAGE_EXTENSIONS : List.of());
    body.put("imageAnalysis", images);
    body.put("heicSupported", false);
    List<String> unsupported = new ArrayList<>(List.of("HEIC(JPG로 저장해 올려 주세요)", "스캔한 PDF", "HWP(구형)", "DOC·PPT·XLS"));
    if (!images) unsupported.add(0, "사진(JPG·PNG·WebP) — 현재 사진 분석을 사용할 수 없어요");
    body.put("unsupported", unsupported);
    body.put("maxFilesPerGroup", service.maxFilesPerGroup());
    body.put("maxImagesPerRequest", service.maxImagesPerRequest());
    body.put("maxCharsPerGroup", service.maxCharsPerGroup());
    body.put("maxImageBytes", service.maxImageBytes());
    body.put("imageDailyLimit", service.imageDailyLimit());
    body.put("remainingImagesToday", principal == null ? 0 : service.remainingImagesToday(principal.userId()));
    body.put("perUserDailyLimit", service.perUserDailyLimit());
    body.put("remainingToday", principal == null ? 0 : service.remainingToday(principal.userId()));
    return noStore(ResponseEntity.ok(), body);
  }

  /**
   * 파일(문서·HTML·사진)을 읽어 파일별 텍스트를 돌려준다. 화면이 사용자에게 확인·수정을 받은 뒤 그 텍스트를 분석에 보낸다.
   * 한 파일이 실패해도 나머지는 계속 읽고, 파일별 status(ok/error)·code·message를 돌려준다.
   */
  @PostMapping(value = "/api/exam-insight/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseBody
  public ResponseEntity<Map<String, Object>> extract(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(value = "group", required = false, defaultValue = "past") String group,
      @RequestPart(value = "files", required = false) List<MultipartFile> files) {
    ExamInsightService.ExtractResult r = service.extract(principal.userId(), group, files);
    Map<String, Object> body = new HashMap<>();
    body.put("status", "done");
    body.put("items", r.items());
    body.put("remainingImagesToday", r.remainingImagesToday());
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
      @RequestParam(value = "pastItems", required = false) String pastItems,
      @RequestParam(value = "scopeItems", required = false) String scopeItems,
      @RequestParam(value = "school", required = false) String school,
      @RequestParam(value = "grade", required = false) String grade,
      @RequestParam(value = "publisher", required = false) String publisher,
      @RequestParam(value = "examType", required = false) String examType,
      @RequestParam(value = "note", required = false) String note) {
    ExamInsightService.Result r = service.analyze(
        principal.userId(),
        new ExamInsightService.Input(
            pastFiles, scopeFiles, pastText, scopeText, new Meta(school, grade, publisher, examType), note,
            parseItems(pastItems), parseItems(scopeItems)));
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

  /** 화면이 보낸 확인·수정 텍스트 JSON({@code [{"label":"…","text":"…"}]})을 읽는다. 형식이 틀리면 400. */
  private List<ExamInsightService.Item> parseItems(String json) {
    if (json == null || json.isBlank()) return null;
    if (json.length() > MAX_ITEM_JSON_CHARS) {
      throw ExamInsightException.badRequest("ITEMS_TOO_LARGE", "보낸 자료가 너무 길어요.");
    }
    try {
      return objectMapper.readValue(json, new TypeReference<List<ExamInsightService.Item>>() {});
    } catch (java.io.IOException e) {
      throw ExamInsightException.badRequest("INVALID_ITEMS", "자료 형식이 올바르지 않아요. 화면을 새로고침한 뒤 다시 시도해 주세요.");
    }
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
