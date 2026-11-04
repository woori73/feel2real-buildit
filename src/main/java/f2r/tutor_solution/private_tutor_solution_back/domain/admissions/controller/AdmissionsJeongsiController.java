package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsJeongsiScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 정시 수능 환산점수 계산 API — 비회원 이용. AI를 호출하지 않는 계산이라 일일 AI 상한은 쓰지 않고,
 * 공개 API 스위치({@code app.admissions.public-api.enabled})와 IP별 rate limit만 적용한다.
 * 합격 판정은 하지 않는다. 오류 상세는 노출하지 않는다.
 */
@RestController
@RequestMapping("/api/admissions/jeongsi")
public class AdmissionsJeongsiController {

    private static final Logger log = LoggerFactory.getLogger(AdmissionsJeongsiController.class);

    private final AdmissionsJeongsiScoreService scoreService;
    private final AdmissionsRateLimiter rateLimiter;
    private final boolean publicApiEnabled;

    @Autowired
    public AdmissionsJeongsiController(AdmissionsJeongsiScoreService scoreService, AdmissionsRateLimiter rateLimiter,
                                       @Value("${app.admissions.public-api.enabled:false}") boolean publicApiEnabled) {
        this.scoreService = scoreService;
        this.rateLimiter = rateLimiter;
        this.publicApiEnabled = publicApiEnabled;
    }

    public record ScoreRequest(String university, Integer academicYear, String typeName, String departmentName,
                               Map<String, Double> scores) {}

    @PostMapping("/score")
    public ResponseEntity<?> score(@RequestBody(required = false) ScoreRequest request,
                                   jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (!publicApiEnabled) {
            return ResponseEntity.status(503).body(Map.of("error", "입시상담은 아직 이용할 수 없습니다."));
        }
        String ip = rateLimiter.resolveClientIp(httpRequest);
        if (!rateLimiter.tryAcquire(ip)) {
            return ResponseEntity.status(429).body(Map.of("error", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."));
        }
        if (request == null) return ResponseEntity.badRequest().body(Map.of("error", "요청 본문이 필요합니다."));
        int year = request.academicYear() == null ? 2027 : request.academicYear();
        if (year != 2027) return ResponseEntity.badRequest().body(Map.of("error", "정시 환산점수는 2027학년도만 계산합니다."));
        try {
            return ResponseEntity.ok(scoreService.calculate(request.university(), year, request.typeName(),
                    request.departmentName(), request.scores()));
        } catch (AdmissionsJeongsiScoreService.InvalidScoreRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("admissions jeongsi score failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "계산 중 오류가 발생했습니다."));
        }
    }
}
