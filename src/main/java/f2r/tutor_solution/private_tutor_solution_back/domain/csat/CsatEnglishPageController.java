package f2r.tutor_solution.private_tutor_solution_back.domain.csat;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 2027학년도 수능 영어(홀수형) 모션 해설(2026-11-19 사용자 지시).
 *
 * <p>화면({@code /csat/2027-english})은 누구나 본다. 자료는 정적 파일({@code /img/csat/2027-en/}). 필기본 PDF는 로그인한 사용자에게만
 * 무료로 내려준다 — 파일은 정적 경로가 아닌 {@code classpath:private/csat/}에 두어 주소를 알아도 로그인 없이 받을 수 없다.
 * 로그인하지 않았으면 네이버 로그인으로 보내고, 로그인 뒤 해설 화면으로 돌아온다.
 */
@Controller
public class CsatEnglishPageController {

  static final String PAGE = "/csat/2027-english";
  static final String PDF_PATH = "private/csat/2027-english-odd-f2r.pdf";
  static final String PDF_NAME = "2027학년도_수능영어_홀수형_F2R해설.pdf";

  private final Resource pdf;

  public CsatEnglishPageController() {
    this(new ClassPathResource(PDF_PATH));
  }

  CsatEnglishPageController(Resource pdf) {
    this.pdf = pdf;
  }

  @GetMapping(PAGE)
  public String page() {
    return "public/csat_2027_english";
  }

  @GetMapping(PAGE + "/pdf")
  public ResponseEntity<?> pdf(@AuthenticationPrincipal AuthenticatedPrincipal principal) throws IOException {
    if (principal == null) {
      String back = URLEncoder.encode(PAGE + "?download=1", StandardCharsets.UTF_8);
      return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "/auth/naver/start?returnUrl=" + back).build();
    }
    if (!pdf.exists()) return ResponseEntity.notFound().build();
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(PDF_NAME, StandardCharsets.UTF_8).build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .contentLength(pdf.contentLength())
        .body(pdf);
  }
}
