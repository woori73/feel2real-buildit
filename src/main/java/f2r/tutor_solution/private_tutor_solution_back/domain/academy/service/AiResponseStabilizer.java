package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * P1 AI 응답 안정성 강화.
 *
 * <p>제공 기능:
 * <ul>
 *   <li>JSON 구조 검증
 *   <li>LLM 오류 시 안전한 실패 처리 (fallback)
 *   <li>타임아웃 및 재시도 정책
 *   <li>토큰 사용량 기록
 *   <li>동일 요청 중복 호출 방지 (deduplication)
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiResponseStabilizer {

  private final AiProvider aiProvider;

  /** 최대 재시도 횟수. */
  private static final int MAX_RETRIES = 2;

  /** 재시도 간격 (ms). */
  private static final long RETRY_DELAY_MS = 1000;

  /** 요청 중복 방지 캐시 (prompt hash → 결과, 5분 TTL). */
  private final Map<String, CachedResult> dedupCache = new ConcurrentHashMap<>();

  private static final Duration DEDUP_TTL = Duration.ofMinutes(5);

  private record CachedResult(AiCompletion completion, Instant timestamp) {
    boolean isExpired() {
      return Instant.now().isAfter(timestamp.plus(DEDUP_TTL));
    }
  }

  /** 토큰 사용량 기록. */
  public record TokenUsage(
      String taskType,
      int promptChars,
      int responseChars,
      long durationMs,
      boolean success,
      int retryCount) {}

  /**
   * 안정화된 AI 완성을 수행한다.
   *
   * @param prompt 프롬프트
   * @param limits 제한
   * @param taskType 작업 유형 (로깅용)
   * @return AI 완성 결과 (실패 시 ok=false)
   */
  public AiCompletion completeWithStability(
      String prompt, AiTaskLimits limits, String taskType) {
    if (!aiProvider.isReady()) {
      log.warn("[AI] Provider not ready for task: {}", taskType);
      return AiCompletion.failed("AI provider not ready");
    }

    // 1. 중복 요청 검사
    String promptHash = computeHash(prompt);
    CachedResult cached = dedupCache.get(promptHash);
    if (cached != null && !cached.isExpired()) {
      log.info("[AI] Deduplicated request for task: {}", taskType);
      return cached.completion();
    }

    // 2. 재시도 루프
    Instant start = Instant.now();
    AiCompletion lastResult = null;
    int retryCount = 0;

    for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      try {
        lastResult = aiProvider.complete(prompt, limits);

        if (lastResult.ok() && !lastResult.text().isBlank()) {
          // 성공: 캐시에 저장
          dedupCache.put(promptHash,
              new CachedResult(lastResult, Instant.now()));

          long durationMs = Duration.between(start, Instant.now()).toMillis();
          logTokenUsage(new TokenUsage(
              taskType, prompt.length(), lastResult.text().length(),
              durationMs, true, retryCount));

          return lastResult;
        }

        // 빈 응답: 재시도
        log.warn("[AI] Empty response for task {} (attempt {}/{})",
            taskType, attempt + 1, MAX_RETRIES + 1);

      } catch (Exception e) {
        log.error("[AI] Exception for task {} (attempt {}/{}): {}",
            taskType, attempt + 1, MAX_RETRIES + 1, e.getMessage());
        lastResult = AiCompletion.failed("Exception: " + e.getMessage());
      }

      retryCount++;
      if (attempt < MAX_RETRIES) {
        try {
          Thread.sleep(RETRY_DELAY_MS * (attempt + 1));
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }

    // 3. 모든 재시도 실패
    long durationMs = Duration.between(start, Instant.now()).toMillis();
    logTokenUsage(new TokenUsage(
        taskType, prompt.length(), 0, durationMs, false, retryCount));
    log.error("[AI] All retries failed for task: {}", taskType);

    return lastResult != null ? lastResult
        : AiCompletion.failed("All retries failed");
  }

  /**
   * JSON 응답을 검증하고 파싱한다.
   *
   * @param completion AI 완성 결과
   * @param allowedKeys 허용된 키 집합
   * @return 파싱된 맵 (실패 시 빈 맵)
   */
  public Map<String, String> parseAndValidateJson(
      AiCompletion completion, Set<String> allowedKeys) {
    Map<String, String> result = new java.util.HashMap<>();

    if (!completion.ok() || completion.text().isBlank()) {
      return result;
    }

    try {
      String json = extractJson(completion.text());
      java.util.regex.Pattern pattern =
          java.util.regex.Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]*)\"");
      java.util.regex.Matcher m = pattern.matcher(json);

      while (m.find()) {
        String key = m.group(1);
        String value = m.group(2);

        // allowlist 검증
        if (!allowedKeys.contains(key)) {
          log.warn("[AI] Rejected non-allowlisted key from LLM: {}", key);
          continue;
        }

        // 값 검증
        if (value == null || value.isBlank() || value.length() > 2000) {
          log.warn("[AI] Rejected invalid value for key: {}", key);
          continue;
        }

        // 위험 콘텐츠 검증
        if (containsDangerousContent(value)) {
          log.warn("[AI] Rejected dangerous content for key: {}", key);
          continue;
        }

        result.put(key, value.trim());
      }
    } catch (Exception e) {
      log.error("[AI] JSON parse failed: {}", e.getMessage());
    }

    return result;
  }

  private String extractJson(String text) {
    String trimmed = text.trim();
    int start = trimmed.indexOf("{");
    int end = trimmed.lastIndexOf("}");
    if (start >= 0 && end > start) {
      return trimmed.substring(start, end + 1);
    }
    return trimmed;
  }

  private boolean containsDangerousContent(String value) {
    String lower = value.toLowerCase();
    return lower.contains("<script")
        || lower.contains("javascript:")
        || lower.contains("onerror=")
        || lower.contains("onload=")
        || lower.contains("onclick=");
  }

  private String computeHash(String prompt) {
    try {
      java.security.MessageDigest md =
          java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString().substring(0, 32);
    } catch (Exception e) {
      return String.valueOf(prompt.hashCode());
    }
  }

  private void logTokenUsage(TokenUsage usage) {
    log.info("[AI Token] task={}, promptChars={}, responseChars={}, "
            + "durationMs={}, success={}, retries={}",
        usage.taskType(), usage.promptChars(), usage.responseChars(),
        usage.durationMs(), usage.success(), usage.retryCount());
    // TODO: academy_ai_actions 테이블에 토큰 사용량 기록
    // 2일차에 DB 스키마 확장 시 연동
  }

  /**
   * 만료된 캐시 항목을 정리한다.
   * (주기적으로 호출하거나, 캐시 크기 제한 시 사용)
   */
  public void evictExpiredCache() {
    dedupCache.entrySet().removeIf(e -> e.getValue().isExpired());
  }
}
