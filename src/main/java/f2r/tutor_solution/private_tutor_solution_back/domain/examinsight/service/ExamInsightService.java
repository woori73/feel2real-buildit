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
import java.util.HexFormat;
import java.util.List;
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
 * 가장 단순하게 지키는 방법이다. 결과는 화면에서 Markdown으로 내려받는다. 호출량은 {@link ExamInsightRateLimiter}가 묶는다.
 */
@Service
public class ExamInsightService {

  private static final Logger log = LoggerFactory.getLogger(ExamInsightService.class);
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final ExamInsightTextExtractor extractor;
  private final ExamInsightRateLimiter limiter;
  private final ExamInsightLlmClient llm;
  private final boolean enabled;
  private final int maxFilesPerGroup;
  private final int maxCharsPerGroup;
  private final int maxOutputChars;

  @Autowired
  public ExamInsightService(
      ExamInsightTextExtractor extractor, ExamInsightRateLimiter limiter, ExamInsightLlmClient llm,
      @Value("${app.exam-insight.enabled:true}") boolean enabled,
      @Value("${app.exam-insight.max-files-per-group:3}") int maxFilesPerGroup,
      @Value("${app.exam-insight.max-chars-per-group:40000}") int maxCharsPerGroup,
      @Value("${app.exam-insight.max-output-chars:16000}") int maxOutputChars) {
    this.extractor = extractor;
    this.limiter = limiter;
    this.llm = llm;
    this.enabled = enabled;
    this.maxFilesPerGroup = maxFilesPerGroup;
    this.maxCharsPerGroup = maxCharsPerGroup;
    this.maxOutputChars = maxOutputChars;
  }

  /** 화면이 받은 입력. 파일은 null일 수 있다. */
  public record Input(
      List<MultipartFile> pastFiles, List<MultipartFile> scopeFiles, String pastText, String scopeText,
      Meta meta, String note) {}

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

  public int perUserDailyLimit() {
    return limiter.perUserDailyLimit();
  }

  public Result analyze(long userId, Input in) {
    if (!enabled) {
      throw new ExamInsightException(HttpStatus.SERVICE_UNAVAILABLE, "DISABLED",
          "기출분석 서비스는 지금 사용할 수 없어요. 곧 다시 열릴 예정이에요.");
    }
    List<String> notices = new ArrayList<>();
    List<Source> past = collect("기출문제", in.pastFiles(), in.pastText(), notices);
    List<Source> scope = collect("시험범위", in.scopeFiles(), in.scopeText(), notices);
    boolean truncated = capTotal(past, notices, "기출문제") | capTotal(scope, notices, "시험범위");

    ExamInsightRateLimiter.Result gate = limiter.tryAcquire(userId);
    if (gate == ExamInsightRateLimiter.Result.USER_LIMIT) {
      throw new ExamInsightException(HttpStatus.TOO_MANY_REQUESTS, "USER_LIMIT",
          "오늘 분석 가능 횟수(" + limiter.perUserDailyLimit() + "회)를 모두 사용했어요. 내일 다시 이용해 주세요.");
    }
    if (gate == ExamInsightRateLimiter.Result.GLOBAL_LIMIT) {
      throw new ExamInsightException(HttpStatus.TOO_MANY_REQUESTS, "GLOBAL_LIMIT",
          "오늘 서비스 전체 분석 한도에 도달했어요. 내일 다시 이용해 주세요.");
    }

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

  // ───────────────────────── 입력 정리 ─────────────────────────

  private List<Source> collect(String group, List<MultipartFile> files, String pasted, List<String> notices) {
    List<Source> out = new ArrayList<>();
    List<MultipartFile> real = new ArrayList<>();
    if (files != null) {
      for (MultipartFile f : files) {
        if (f != null && !f.isEmpty()) real.add(f);
      }
    }
    if (real.size() > maxFilesPerGroup) {
      throw ExamInsightException.badRequest("TOO_MANY_FILES",
          group + " 파일은 최대 " + maxFilesPerGroup + "개까지 올릴 수 있어요.");
    }
    for (MultipartFile f : real) {
      ExamInsightTextExtractor.Extracted ex = extractor.extract(f);
      out.add(new Source(ex.filename(), ex.text()));
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
