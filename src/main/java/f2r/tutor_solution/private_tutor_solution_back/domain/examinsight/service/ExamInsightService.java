package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightPromptBuilder.Documents;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightPromptBuilder.Meta;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightPromptBuilder.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 내신영어 기출분석 + 출제방향 문서 생성.
 *
 * <p>분석(이 서비스)과 유료 문제 제작 의뢰는 분리돼 있다. 이 서비스는 결제·주문을 만들지 않는다.
 * 올린 파일과 생성 결과는 서버에 저장하지 않는다(요청 처리 동안 메모리에만 존재) — 사용자별 자료 격리와 개인정보 보호를
 * 가장 단순하게 지키는 방법이다. 결과는 화면에서 Markdown으로 내려받는다. 분석 호출량은 {@link ExamInsightRateLimiter}가,
 * 사진 인식 호출량은 {@link ExamInsightImageBudget}이 묶는다.
 *
 * <p>입력은 문서(PDF·DOCX·HWPX·TXT·MD), 웹 문서(HTML), 사진(JPG·PNG·WebP)을 한 요청에 섞어 올릴 수 있다. 사진과 HTML 속
 * 내장 이미지는 이미지 인식으로 글자를 옮기고, 이후에는 모두 같은 텍스트 소스로 취급해 통합 분석한다.
 * 화면은 먼저 {@link #extract}로 파일별 추출 결과를 보여 주고, 사용자가 고친 텍스트({@link Item})만 분석에 보낸다
 * (같은 사진을 다시 인식하지 않으므로 사진 인식 비용은 한 번만 든다).
 */
@Service
public class ExamInsightService {

  private static final Logger log = LoggerFactory.getLogger(ExamInsightService.class);
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final ExamInsightTextExtractor extractor;
  private final ExamInsightRateLimiter limiter;
  private final ExamInsightLlmClient llm;
  private final ExamInsightImagePreprocessor images;
  private final ExamInsightVisionClient vision;
  private final ExamInsightImageBudget imageBudget;
  private final boolean enabled;
  private final int maxFilesPerGroup;
  private final int maxImagesPerRequest;
  private final long maxImageBytes;
  private final int maxCharsPerGroup;
  private final int maxOutputChars;

  @Autowired
  public ExamInsightService(
      ExamInsightTextExtractor extractor, ExamInsightRateLimiter limiter, ExamInsightLlmClient llm,
      ExamInsightImagePreprocessor images, ExamInsightVisionClient vision, ExamInsightImageBudget imageBudget,
      @Value("${app.exam-insight.enabled:false}") boolean enabled,
      @Value("${app.exam-insight.max-files-per-group:20}") int maxFilesPerGroup,
      @Value("${app.exam-insight.max-images-per-request:20}") int maxImagesPerRequest,
      @Value("${app.exam-insight.max-image-bytes:10485760}") long maxImageBytes,
      @Value("${app.exam-insight.max-chars-per-group:40000}") int maxCharsPerGroup,
      @Value("${app.exam-insight.max-output-chars:40000}") int maxOutputChars) {
    this.extractor = extractor;
    this.limiter = limiter;
    this.llm = llm;
    this.images = images;
    this.vision = vision;
    this.imageBudget = imageBudget;
    this.enabled = enabled;
    this.maxFilesPerGroup = maxFilesPerGroup;
    this.maxImagesPerRequest = maxImagesPerRequest;
    this.maxImageBytes = maxImageBytes;
    this.maxCharsPerGroup = maxCharsPerGroup;
    this.maxOutputChars = maxOutputChars;
  }

  /** 사용자가 추출 결과를 확인·수정한 뒤 분석에 보내는 한 덩어리(파일 한 개 또는 사진 한 장). */
  public record Item(String label, String text) {}

  /** 화면이 받은 입력. 파일·항목은 null일 수 있다. */
  public record Input(
      List<MultipartFile> pastFiles, List<MultipartFile> scopeFiles, String pastText, String scopeText,
      Meta meta, String note, List<Item> pastItems, List<Item> scopeItems) {
    public Input(
        List<MultipartFile> pastFiles, List<MultipartFile> scopeFiles, String pastText, String scopeText,
        Meta meta, String note) {
      this(pastFiles, scopeFiles, pastText, scopeText, meta, note, null, null);
    }
  }

  /** 파일 한 개의 추출 결과(확인·수정 단계에서 화면에 그대로 보인다). {@code status}는 ok 또는 error. */
  public record ExtractedItem(
      String name, String kind, String status, String text, int uncertainCount, int chars,
      String code, String message, List<String> notices) {}

  /** {@link #extract}의 결과. */
  public record ExtractResult(List<ExtractedItem> items, int remainingImagesToday) {}

  /** 분석 결과. 두 문서는 Markdown 원문이다. */
  public record Result(
      String analysisId, String analysisMarkdown, String directionMarkdown, boolean truncated,
      List<String> notices, String model, int remainingToday) {}

  public boolean isEnabled() {
    return enabled;
  }

  public int remainingToday(long userId) {
    return limiter.remaining(userId);
  }

  public int maxFilesPerGroup() {
    return maxFilesPerGroup;
  }

  public int maxCharsPerGroup() {
    return maxCharsPerGroup;
  }

  public int maxImagesPerRequest() {
    return maxImagesPerRequest;
  }

  public long maxImageBytes() {
    return maxImageBytes;
  }

  /** 사진 인식을 쓸 수 있는지(Vision 키 설정 여부). false면 화면이 사진 업로드를 열지 않는다. */
  public boolean imageAnalysisAvailable() {
    return vision.available();
  }

  public int remainingImagesToday(long userId) {
    return imageBudget.remaining(userId);
  }

  public int imageDailyLimit() {
    return imageBudget.perUserDailyLimit();
  }

  public int perUserDailyLimit() {
    return limiter.perUserDailyLimit();
  }

  public Result analyze(long userId, Input in) {
    if (!enabled) {
      throw new ExamInsightException(HttpStatus.SERVICE_UNAVAILABLE, "DISABLED",
          "기출분석 서비스는 지금 사용할 수 없어요. 곧 다시 열릴 예정이에요.");
    }
    List<String> notices = new ArrayList<>();
    // 사진 인식에 돈을 쓰기 전에, 어차피 분석 횟수가 없으면 먼저 알린다(획득은 아래에서 따로 한다).
    if (limiter.remaining(userId) <= 0) {
      ExamInsightRateLimiter.Result probe = limiter.tryAcquire(userId);
      if (probe != ExamInsightRateLimiter.Result.OK) throw limitError(probe);
      limiter.refund(userId);
    }
    int imageFiles = countImages(in.pastFiles()) + countImages(in.scopeFiles());
    if (imageFiles > maxImagesPerRequest) {
      throw ExamInsightException.badRequest("TOO_MANY_IMAGES",
          "한 번에 분석할 수 있는 사진은 최대 " + maxImagesPerRequest + "장이에요.");
    }
    ImageRun run = new ImageRun(userId);
    List<Source> past = collect("기출문제", in.pastFiles(), in.pastText(), in.pastItems(), notices, run);
    List<Source> scope = collect("시험범위", in.scopeFiles(), in.scopeText(), in.scopeItems(), notices, run);
    boolean truncated = capTotal(past, notices, "기출문제") | capTotal(scope, notices, "시험범위");

    ExamInsightRateLimiter.Result gate = limiter.tryAcquire(userId);
    if (gate != ExamInsightRateLimiter.Result.OK) throw limitError(gate);

    long started = System.nanoTime();
    try {
      String prompt = ExamInsightPromptBuilder.userPrompt(past, scope, in.meta(), in.note());
      NewStartLlmCompletion c = llm.complete(ExamInsightPromptBuilder.SYSTEM_PROMPT, prompt, maxOutputChars);
      if (!c.ok() || c.text() == null || c.text().isBlank()) {
        limiter.refund(userId);
        log.warn("exam-insight llm failed userId={} pastChars={} scopeChars={}", userId, chars(past), chars(scope));
        throw new ExamInsightException(HttpStatus.SERVICE_UNAVAILABLE, "LLM_UNAVAILABLE",
            "AI 분석을 지금 진행할 수 없어요. 분석은 완료되지 않았고, 이번 시도는 이용 횟수에서 빼 드렸어요. 잠시 후 다시 시도해 주세요.");
      }
      Documents docs;
      try {
        docs = ExamInsightPromptBuilder.parse(c.text());
      } catch (ExamInsightException e) {
        limiter.refund(userId);
        log.warn("exam-insight invalid output userId={} outChars={}", userId, c.text().length());
        throw e;
      }
      log.info("exam-insight ok userId={} pastChars={} scopeChars={} outChars={} ms={}",
          userId, chars(past), chars(scope), c.text().length(), Math.round((System.nanoTime() - started) / 1_000_000.0));
      return new Result(
          analysisId(docs), docs.analysis(), docs.direction(), truncated, List.copyOf(notices),
          c.model(), limiter.remaining(userId));
    } catch (RuntimeException e) {
      if (!(e instanceof ExamInsightException)) {
        limiter.refund(userId);
        log.error("exam-insight unexpected failure userId={}", userId, e);
        throw new ExamInsightException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL",
            "분석 중 오류가 났어요. 분석은 완료되지 않았고, 이번 시도는 이용 횟수에서 빼 드렸어요.");
      }
      throw e;
    }
  }

  // ───────────────────────── 파일 읽기(문서·HTML·사진) ─────────────────────────

  /**
   * 파일 하나를 읽어 화면·분석에 쓸 텍스트로 만든다. 문서·HTML은 글자를 뽑고, 사진은 이미지 인식으로 옮긴다.
   * HTML 속 내장 이미지는 같은 방식으로 읽어 본문의 자리표시자 자리에 넣는다.
   *
   * @throws ExamInsightException 읽을 수 없는 파일(형식·손상·인식 실패·한도)
   */
  private Read readFile(MultipartFile f, ImageRun run) {
    String name = ExamInsightTextExtractor.safe(f.getOriginalFilename());
    if (ExamInsightTextExtractor.isReadableImage(f.getOriginalFilename())) {
      if (f.getSize() > maxImageBytes) {
        throw ExamInsightException.badRequest("IMAGE_TOO_BIG",
            "사진 한 장은 " + (maxImageBytes / (1024 * 1024)) + "MB까지 올릴 수 있어요: " + name);
      }
      byte[] bytes = extractor.validatedBytes(f, ExamInsightTextExtractor.IMAGE_EXTENSIONS);
      Transcribed t = run.transcribe(bytes, name);
      return new Read("[사진] " + name, "image", t.text(), t.uncertain(), List.of());
    }
    ExamInsightTextExtractor.Extracted ex = extractor.extract(f);
    if (ex.images().isEmpty() && ex.skippedExternalImages() == 0 && ex.skippedEmbeddedImages() == 0) {
      return new Read((ex.format().equals("html") || ex.format().equals("htm") ? "[HTML] " : "") + ex.filename(),
          ex.format().equals("html") || ex.format().equals("htm") ? "html" : "document", ex.text(), 0, List.of());
    }
    // HTML: 내장 이미지는 읽어서 자리표시자에 넣고, 가져오지 않은 이미지는 사실대로 알린다.
    String text = ex.text();
    int uncertain = 0;
    List<String> notes = new ArrayList<>();
    for (ExamInsightHtmlExtractor.EmbeddedImage img : ex.images()) {
      String placeholder = String.format(ExamInsightHtmlExtractor.IMAGE_PLACEHOLDER, img.index());
      String replacement;
      try {
        Transcribed t = run.transcribe(img.bytes(), ex.filename() + " 내장 이미지 " + img.index());
        uncertain += t.uncertain();
        replacement = "\n[내장 이미지 " + img.index() + " 내용]\n" + t.text() + "\n[/내장 이미지 " + img.index() + "]\n";
      } catch (ExamInsightException e) {
        notes.add(ex.filename() + "의 내장 이미지 " + img.index() + "번은 읽지 못했어요(" + e.getMessage() + ")");
        replacement = "[내장 이미지 " + img.index() + ": 읽지 못함]";
      }
      text = text.replace(placeholder, replacement);
    }
    if (ex.skippedExternalImages() > 0) {
      notes.add(ex.filename() + "의 외부 이미지 " + ex.skippedExternalImages()
          + "개는 보안 정책상 가져오지 않았어요. 필요하면 사진으로 따로 올려 주세요.");
    }
    if (ex.skippedEmbeddedImages() > 0) {
      notes.add(ex.filename() + "의 내장 이미지 " + ex.skippedEmbeddedImages() + "개는 형식·크기·개수 제한으로 읽지 않았어요.");
    }
    return new Read("[HTML] " + ex.filename(), "html", ExamInsightTextExtractor.clean(text), uncertain, notes);
  }

  /** 파일별 결과(성공·실패)를 모아 돌려준다. 한 파일이 실패해도 나머지는 계속 읽는다. */
  public ExtractResult extract(long userId, String group, List<MultipartFile> files) {
    if (!enabled) {
      throw new ExamInsightException(HttpStatus.SERVICE_UNAVAILABLE, "DISABLED",
          "기출분석 서비스는 지금 사용할 수 없어요. 곧 다시 열릴 예정이에요.");
    }
    List<MultipartFile> real = realFiles(files);
    if (real.isEmpty()) {
      throw ExamInsightException.badRequest("NO_FILES", "읽을 파일을 선택해 주세요.");
    }
    if (real.size() > maxFilesPerGroup) {
      throw ExamInsightException.badRequest("TOO_MANY_FILES", "한 번에 최대 " + maxFilesPerGroup + "개까지 올릴 수 있어요.");
    }
    if (countImages(real) > maxImagesPerRequest) {
      throw ExamInsightException.badRequest("TOO_MANY_IMAGES",
          "한 번에 읽을 수 있는 사진은 최대 " + maxImagesPerRequest + "장이에요.");
    }
    ImageRun run = new ImageRun(userId);
    List<ExtractedItem> out = new ArrayList<>();
    for (MultipartFile f : real) {
      String name = ExamInsightTextExtractor.safe(f.getOriginalFilename());
      String kind = ExamInsightTextExtractor.isReadableImage(f.getOriginalFilename()) ? "image" : "document";
      try {
        Read r = readFile(f, run);
        out.add(new ExtractedItem(name, r.kind(), "ok", r.text(), r.uncertain(), r.text().length(), null, null,
            r.notes()));
      } catch (ExamInsightException e) {
        out.add(new ExtractedItem(name, kind, "error", "", 0, 0, e.getCode(), e.getMessage(), List.of()));
      } catch (RuntimeException e) {
        log.error("exam-insight extract unexpected failure userId={}", userId, e);
        out.add(new ExtractedItem(name, kind, "error", "", 0, 0, "INTERNAL", "파일을 읽는 중 오류가 났어요.", List.of()));
      }
    }
    log.info("exam-insight extract userId={} group={} files={} images={}", userId, group, real.size(), run.calls);
    return new ExtractResult(List.copyOf(out), imageBudget.remaining(userId));
  }

  private record Read(String label, String kind, String text, int uncertain, List<String> notes) {}

  private record Transcribed(String text, int uncertain) {}

  /** 요청 하나 안에서 사진 인식을 묶는다: 같은 사진은 한 번만 읽고, 한 장마다 하루 장수 한도에서 차감하며, 실패하면 돌려준다. */
  private final class ImageRun {
    private final long userId;
    private final Map<String, Transcribed> seen = new HashMap<>();
    int calls;

    ImageRun(long userId) {
      this.userId = userId;
    }

    Transcribed transcribe(byte[] original, String label) {
      ExamInsightImagePreprocessor.Prepared prepared = images.prepare(original, label);
      Transcribed cached = seen.get(prepared.sha256());
      if (cached != null) return cached;
      if (!vision.available()) vision.transcribe(prepared.jpeg(), label); // VISION_UNAVAILABLE을 그대로 던진다
      ExamInsightImageBudget.Result gate = imageBudget.tryAcquire(userId, 1);
      if (gate != ExamInsightImageBudget.Result.OK) {
        throw new ExamInsightException(HttpStatus.TOO_MANY_REQUESTS, "IMAGE_LIMIT",
            gate == ExamInsightImageBudget.Result.USER_LIMIT
                ? "오늘 읽을 수 있는 사진 수(" + imageBudget.perUserDailyLimit() + "장)를 모두 사용했어요. 내일 다시 이용하거나 본문을 붙여 넣어 주세요."
                : "오늘 서비스 전체 사진 분석 한도에 도달했어요. 내일 다시 이용하거나 본문을 붙여 넣어 주세요.");
      }
      calls++;
      try {
        ExamInsightVisionClient.Transcription t = vision.transcribe(prepared.jpeg(), label);
        Transcribed result = new Transcribed(t.text(), t.uncertainCount());
        seen.put(prepared.sha256(), result);
        return result;
      } catch (RuntimeException e) {
        imageBudget.refund(userId, 1);
        throw e;
      }
    }
  }

  private static List<MultipartFile> realFiles(List<MultipartFile> files) {
    List<MultipartFile> real = new ArrayList<>();
    if (files != null) {
      for (MultipartFile f : files) {
        if (f != null && !f.isEmpty()) real.add(f);
      }
    }
    return real;
  }

  private static int countImages(List<MultipartFile> files) {
    int n = 0;
    for (MultipartFile f : realFiles(files)) {
      if (ExamInsightTextExtractor.isReadableImage(f.getOriginalFilename())) n++;
    }
    return n;
  }

  private ExamInsightException limitError(ExamInsightRateLimiter.Result gate) {
    if (gate == ExamInsightRateLimiter.Result.USER_LIMIT) {
      return new ExamInsightException(HttpStatus.TOO_MANY_REQUESTS, "USER_LIMIT",
          "오늘 분석 가능 횟수(" + limiter.perUserDailyLimit() + "회)를 모두 사용했어요. 내일 다시 이용해 주세요.");
    }
    return new ExamInsightException(HttpStatus.TOO_MANY_REQUESTS, "GLOBAL_LIMIT",
        "오늘 서비스 전체 분석 한도에 도달했어요. 내일 다시 이용해 주세요.");
  }

  // ───────────────────────── 입력 정리 ─────────────────────────

  private List<Source> collect(
      String group, List<MultipartFile> files, String pasted, List<Item> items, List<String> notices, ImageRun run) {
    List<Source> out = new ArrayList<>();
    List<MultipartFile> real = realFiles(files);
    if (real.size() > maxFilesPerGroup) {
      throw ExamInsightException.badRequest("TOO_MANY_FILES",
          group + " 파일은 최대 " + maxFilesPerGroup + "개까지 올릴 수 있어요.");
    }
    // 화면에서 확인·수정한 텍스트가 있으면 먼저(사용자가 정한 순서대로) 싣는다.
    if (items != null) {
      if (items.size() > maxFilesPerGroup) {
        throw ExamInsightException.badRequest("TOO_MANY_FILES",
            group + " 자료는 최대 " + maxFilesPerGroup + "개까지 보낼 수 있어요.");
      }
      for (Item it : items) {
        String text = ExamInsightTextExtractor.clean(it == null ? null : it.text());
        if (!text.isBlank()) {
          String label = it.label() == null || it.label().isBlank() ? "자료" : ExamInsightTextExtractor.safe(it.label());
          out.add(new Source(label, text));
        }
      }
    }
    for (MultipartFile f : real) {
      Read r = readFile(f, run);
      notices.addAll(r.notes());
      if (r.uncertain() > 0) {
        notices.add(r.label() + "에서 읽기 불확실한 부분 " + r.uncertain() + "곳이 [확인필요]로 표시됐어요. 분석에서 사실로 쓰지 않아요.");
      }
      out.add(new Source(r.label(), r.text()));
    }
    String text = ExamInsightTextExtractor.clean(pasted);
    if (!text.isBlank()) out.add(new Source("붙여 넣은 " + group + " 글", text));
    if (out.isEmpty()) {
      throw ExamInsightException.badRequest("MISSING_" + (group.equals("기출문제") ? "PAST" : "SCOPE"),
          group.equals("기출문제")
              ? "최근 학교 기출문제 자료를 올리거나 본문을 붙여 넣어 주세요."
              : "이번 시험범위 자료(교과서·본문·학교 학습지)를 올리거나 본문을 붙여 넣어 주세요.");
    }
    return out;
  }

  /** 그룹 전체 글자 수를 상한으로 자른다. 잘렸으면 true. */
  private boolean capTotal(List<Source> sources, List<String> notices, String group) {
    int budget = maxCharsPerGroup;
    boolean cut = false;
    for (int i = 0; i < sources.size(); i++) {
      Source s = sources.get(i);
      if (budget <= 0) {
        sources.set(i, new Source(s.label(), ""));
        cut = true;
      } else if (s.text().length() > budget) {
        sources.set(i, new Source(s.label(), s.text().substring(0, budget)));
        budget = 0;
        cut = true;
      } else {
        budget -= s.text().length();
      }
    }
    sources.removeIf(s -> s.text().isEmpty());
    if (cut) {
      notices.add(group + " 자료가 길어 앞부분 약 " + maxCharsPerGroup + "자만 분석했어요. 뒷부분은 반영되지 않았어요.");
    }
    return cut;
  }

  private static int chars(List<Source> sources) {
    return sources.stream().mapToInt(s -> s.text().length()).sum();
  }

  /** 분석 번호: 내용으로 만든 짧은 식별자. 제작 의뢰 때 어떤 분석 문서인지 알리는 용도이며 비밀 값이 아니다. */
  static String analysisId(Documents docs) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest((docs.analysis() + "\n" + docs.direction()).getBytes(StandardCharsets.UTF_8));
      String hex = HexFormat.of().withUpperCase().formatHex(d, 0, 3);
      return "EI-" + LocalDate.now(KST).toString().replace("-", "") + "-" + hex;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
