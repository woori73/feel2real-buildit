package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGalmuriService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGalmuriService.GalmuriPage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGalmuriService.GalmuriResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 갈무리 게시판 API(설계 §6) — 회원 read-only feed.
 *
 * <p>새 저장 endpoint가 없다. 갈무리는 회원이 글을 올리는 게시판이 아니라 운영자가 ACTIVE로 승인한
 * 지식 항목의 노출 레이어이기 때문이다(§6 "free board 아님"). 쓰기 경로가 여기에 있다는 즉시
 * 설계 위반이므로 만들지 않는다.
 *
 * <p>§7 경계: 페이지 셸은 열리지만 데이터는 인증 회원만(비로그인은 {@code /api/**} catch-all이 401).
 * 실행형 권한(THANKS_MEMBER)을 요구하지 않는다 — 읽기만 하는 공개 지식 피드이기 때문이다.
 */
@RestController
@RequestMapping("/api/newstart/galmuri")
public class NewStartGalmuriController {

  private final NewStartGalmuriService galmuriService;

  public NewStartGalmuriController(NewStartGalmuriService galmuriService) {
    this.galmuriService = galmuriService;
  }

  /** 지금 효력 있는 ACTIVE 갈무리 항목(최신 출처 기준시점 순, 최대 50건/페이지). */
  @GetMapping
  public GalmuriPage feed(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return galmuriService.feed(category, page, size);
  }

  /** 상세(feed에 오른 항목만). REVIEW/DRAFT·만료 항목은 목록과 동일하게 404로 숨긴다. */
  @GetMapping("/{knowledgeId}")
  public ResponseEntity<DetailResponse> detail(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long knowledgeId) {
    GalmuriResult result = galmuriService.detail(knowledgeId);
    if (!result.ok()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(new DetailResponse("error", result.errorMessage(), null));
    }
    return ResponseEntity.ok(new DetailResponse("ok", null, result.item()));
  }

  /** @param errorMessage 실패 시 사용자 안내 문구(내부 id·예외 상세 미노출). */
  public record DetailResponse(String status, String errorMessage,
      NewStartGalmuriService.GalmuriDetail item) {}
}
