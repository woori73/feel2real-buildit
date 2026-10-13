package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PublishingCommand;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 출판 AI 수정 서비스 (P3).
 *
 * <p>공통 Agent {@code muse-spark-1.3-contributor}를 사용한다.
 * {@link AiProvider}는 {@code @Primary}인 {@code MuseSparkAcademyAiProvider}가 주입된다
 * (PR #604). BuildIt 홈빌더와 동일한 제안→승인→적용 구조를 재사용한다.
 *
 * <p>흐름:
 * <ol>
 *   <li>{@link #plan} — 자연어를 명령 목록으로 변환만 하고 실행하지 않는다 (dry-run)
 *   <li>사용자가 계획을 확인·승인
 *   <li>{@link #applyPlan} — 스냅샷 생성 후 승인된 계획만 실행
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishingAiService {

  private final AiProvider aiProvider; // muse-spark-1.3-contributor (@Primary)
  private final PublishingJobService jobService;
  private final PublishingEditService editService;
  private final CommandAllowlist allowlist;
  private final ObjectMapper objectMapper;

  /** AI 계획 응답. */
  public record AiPlan(List<PlannedCommand> plan, boolean needsApproval) {
  }

  /** 계획된 단일 명령 (사용자 확인용 설명 포함). */
  public record PlannedCommand(String command, Map<String, Object> params,
      String description) {
  }

  /**
   * 자연어 수정 요청을 명령 계획으로 변환한다 (실행 안 함).
   *
   * @return 계획 + needsApproval=true
   */
  public AiPlan plan(AuthenticatedPrincipal principal, Long jobId, String message) {
    PublishingJob job = jobService.requireOwned(principal, jobId);
    if (message == null || message.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수정 요청을 입력하세요.");
    }
    if (!aiProvider.isReady()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "AI Provider가 준비되지 않았습니다: " + aiProvider.name());
    }

    String prompt = buildPlanPrompt(job, message);
    AiCompletion completion = aiProvider.complete(prompt, new AiTaskLimits(4000));
    if (!completion.ok()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "AI 계획 생성 실패: " + completion.errorMessage());
    }

    List<PlannedCommand> plan = parsePlan(completion.text());
    // allowlist 검증 (AI가 허용 외 명령을 계획했으면 거부)
    List<PublishingCommand> commands = plan.stream()
        .map(p -> new PublishingCommand(p.command(), p.params()))
        .toList();
    allowlist.validateAiPlan(commands);

    log.info("AI 계획 생성: jobId={}, {}건", jobId, plan.size());
    return new AiPlan(plan, true);
  }

  /**
   * 승인된 계획을 실행한다 (스냅샷 후 적용).
   *
   * @return 생성된 스냅샷 ID
   */
  public Long applyPlan(AuthenticatedPrincipal principal, Long jobId,
      List<PlannedCommand> plan) {
    if (plan == null || plan.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "적용할 계획이 없습니다.");
    }
    List<PublishingCommand> commands = plan.stream()
        .map(p -> new PublishingCommand(p.command(), p.params()))
        .toList();
    // 재검증: 승인된 계획이라도 서버에서 allowlist 재확인
    allowlist.validateAiPlan(commands);
    return editService.executeCommands(principal, jobId, commands);
  }

  /** 계획 생성용 시스템 프롬프트. */
  private String buildPlanPrompt(PublishingJob job, String message) {
    return """
        당신은 DesignCraft 출판 편집기의 명령 계획자입니다.
        사용자의 자연어 수정 요청을 아래 허용 명령 목록 안에서 JSON 명령 배열로 변환하세요.
        실행하지 말고 계획만 JSON으로 출력하세요.

        허용 명령 (이 목록 외 절대 사용 금지):
        - file.place: 원고 반입. params: {path, autoflow, styleMap}
        - style.paragraph.create: 문단 스타일 생성. params: {name, chars:{fontFamily,size}, para:{align,firstLineIndent,spaceAfter}}
          - align은 left/center/right/fullyJustified 중 하나. fontFamily는 "Noto Sans KR" 고정 (변경 금지)
        - style.paragraph.apply: 스타일 적용. params: {name}
        - toc.generate: 목차 생성. params: {styles, title, pageNumbers}
        - layout.parents.new: 마스터 생성. params: {prefix, name}
        - edit.undo: 실행 취소. params: {}

        금지: 판형 변경, 마스터 삭제, 폰트 변경, 파일 열기/내보내기, 위 목록 외 모든 명령.

        출력 형식 (JSON만, 설명 문장 없이):
        {"plan": [{"command": "명령id", "params": {...}, "description": "사용자용 한글 설명"}]}

        현재 작업: "%s" (템플릿: %s)
        사용자 요청: %s
        """.formatted(job.getTitle(), job.getTemplate(), message);
  }

  /** AI 응답에서 계획 JSON을 파싱한다. */
  private List<PlannedCommand> parsePlan(String text) {
    try {
      String json = extractJson(text);
      Map<String, Object> map = objectMapper.readValue(json,
          new TypeReference<Map<String, Object>>() {});
      Object planObj = map.get("plan");
      List<PlannedCommand> plan = new ArrayList<>();
      if (planObj instanceof List<?> list) {
        for (Object o : list) {
          if (o instanceof Map<?, ?> m) {
            String command = String.valueOf(m.get("command"));
            @SuppressWarnings("unchecked")
            Map<String, Object> params = m.get("params") instanceof Map<?, ?> pm
                ? (Map<String, Object>) pm : Map.of();
            String description = String.valueOf(
                m.getOrDefault("description", command));
            plan.add(new PlannedCommand(command, new LinkedHashMap<>(params), description));
          }
        }
      }
      if (plan.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "AI가 유효한 계획을 생성하지 못했습니다.");
      }
      return plan;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "AI 계획 파싱 실패: " + e.getMessage(), e);
    }
  }

  private String extractJson(String text) {
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return text.substring(start, end + 1);
    }
    return text;
  }
}
