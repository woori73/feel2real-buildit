package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartKnowledgeItemResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartKnowledgeRetrievalService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Muse 에이전트용 NewStart 지식 검색 API (read-only, Phase 1).
 *
 * <p>기존 {@link NewstartKnowledgeRetrievalService#retrieveForQuestion(String, int)} 를
 * 그대로 래핑한다. ACTIVE 상태 지식만 반환되며, 어떤 쓰기 작업도 수행하지 않는다
 * (서비스 자체가 {@code @Transactional(readOnly = true)}).
 *
 * <p>인증: {@code NEWSTART_AGENT_TOKEN} Bearer 토큰
 * ({@code ROLE_NEWSTART_AGENT}) 또는 ADMIN JWT. 토큰 미설정 시 에이전트 호출은
 * 401/403으로 거부된다 (fail-closed).
 */
@RestController
@RequestMapping("/api/muse/newstart/knowledge")
public class NewStartMuseKnowledgeController {

  /** 검색 결과 상한 (남용 방지). */
  private static final int MAX_LIMIT = 20;

  private final NewstartKnowledgeRetrievalService retrievalService;

  public NewStartMuseKnowledgeController(NewstartKnowledgeRetrievalService retrievalService) {
    this.retrievalService = retrievalService;
  }

  /**
   * 질문 키워드로 ACTIVE NewStart 지식을 검색한다.
   *
   * @param q     검색 질문/키워드 (필수)
   * @param limit 최대 반환 건수 (기본 5, 1~20)
   * @return 관련도순 지식 목록 (빈 질문이면 빈 배열)
   */
  @GetMapping("/search")
  public ResponseEntity<List<NewstartKnowledgeItemResponse>> search(
      @RequestParam("q") String q,
      @RequestParam(value = "limit", defaultValue = "5") int limit) {
    int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
    return ResponseEntity.ok(retrievalService.retrieveForQuestion(q, safeLimit));
  }
}
