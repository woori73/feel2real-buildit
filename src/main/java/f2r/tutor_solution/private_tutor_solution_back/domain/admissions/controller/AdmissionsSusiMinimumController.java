package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsJeongsiScoreService;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsSusiMinimumService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 수시 수능최저학력기준 충족 확인 API — 비회원 이용. AI 호출 없음. 공개 API 스위치와 IP별 rate limit만 적용한다.
 * 합격 여부는 판단하지 않는다. 오류 상세는 노출하지 않는다.
 */
@RestController
@RequestMapping("/api/admissions/susi")
public class AdmissionsSusiMinimumController {

    private static final Logger log = LoggerFactory.getLogger(AdmissionsSusiMinimumController.class);

    private final AdmissionsSusiMinimumService service;
    private final AdmissionsRateLimiter rateLimiter;
    private final boolean publicApiEnabled;

    @Autowired
    public AdmissionsSusiMinimumController(AdmissionsSusiMinimumService service, AdmissionsRateLimiter rateLimiter,
                                           @Value("${app.admissions.public-api.enabled:false}") boolean publicApiEnabled) {
        this.service = service;
        this.rateLimiter = rateLimiter;
        this.publicApiEnabled = publicApiEnabled;
    }

    public record CheckRequest(String university, Integer academicYear, String typeName, String departmentName,
                               Map<String, Double> grades) {}

    @PostMapping("/minimum-check")
    public ResponseEntity<?> check(@RequestBody(required = false) CheckRequest request,
                                   jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (!publicApiEnabled) {
            return ResponseEntity.status(503).body(Map.of("error", "입시상담은 아직 이용할 수 없습니다."));
        }
        if (!rateLimiter.tryAcquire("minimum-" + rateLimiter.resolveClientIp(httpRequest))) {
            return ResponseEntity.status(429).body(Map.of("error", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."));
        }
        if (request == null) return ResponseEntity.badRequest().body(Map.of("error", "요청 본문이 필요합니다."));
        int year = request.academicYear() == null ? 2027 : request.academicYear();
        if (year != 2027) return ResponseEntity.badRequest().body(Map.of("error", "수능최저 확인은 2027학년도만 지원합니다."));
        try {
            var result = service.check(request.university(), year, request.typeName(), request.departmentName(), request.grades());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
        } catch (AdmissionsJeongsiScoreService.InvalidScoreRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("admissions susi minimum check failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "확인 중 오류가 발생했습니다."));
        }
    }
}
