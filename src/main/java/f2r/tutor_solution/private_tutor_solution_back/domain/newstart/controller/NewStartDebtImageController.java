package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartDebtImageService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartDebtImageService.DebtImageItem;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * NewStart 채무 이미지 분석 API — Credit4U 채무내역 스크린샷을 서버의 Qwen Vision으로
 * 분석해 채무 목록을 반환한다.
 *
 * <p>기존 브라우저 Tesseract.js OCR의 인식률 문제를 해결하기 위해 도입했다.
 * 업로드된 이미지 원본은 저장하지 않고 분석 후 즉시 버린다.
 *
 * <p>인증은 기존 {@code /api/**} catch-all(인증 필요)을 그대로 따른다. 대상 회원은 항상
 * principal(userId)로만 결정된다.
 */
@RestController
@RequestMapping("/api/newstart/debt-image")
public class NewStartDebtImageController {

  private static final Logger log = LoggerFactory.getLogger(NewStartDebtImageController.class);

  private final NewStartDebtImageService debtImageService;

  public NewStartDebtImageController(NewStartDebtImageService debtImageService) {
    this.debtImageService = debtImageService;
  }

  /**
   * 채무 이미지 분석.
   *
   * @param file JPG/PNG/WebP/PDF (최대 20MB, PDF는 첫 페이지만)
   * @return {@code {"debts": [...]}} 형식의 채무 목록
   */
  @PostMapping("/analyze")
  public ResponseEntity<?> analyze(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam("file") MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return ResponseEntity.badRequest().body(new ErrorResponse("파일을 선택해 주세요."));
    }
    try {
      List<DebtImageItem> debts = debtImageService.analyze(
          file.getBytes(), file.getContentType(), file.getOriginalFilename());
      log.info("debt image analyzed: userId={} debts={}", principal.userId(), debts.size());
      return ResponseEntity.ok(new AnalyzeResponse(debts));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    } catch (IOException e) {
      log.warn("debt image analyze failed: userId={} error={}", principal.userId(), e.getMessage());
      return ResponseEntity.internalServerError().body(new ErrorResponse("이미지 분석에 실패했습니다."));
    }
  }

  public record AnalyzeResponse(List<DebtImageItem> debts) {}

  public record ErrorResponse(String errorMessage) {}
}
