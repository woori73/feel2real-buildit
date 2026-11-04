package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsExplorerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admissions/explore")
public class AdmissionsExplorerController {
    private final AdmissionsExplorerService explorer;
    private final AdmissionsRateLimiter limiter;
    private final boolean enabled;
    public AdmissionsExplorerController(AdmissionsExplorerService explorer, AdmissionsRateLimiter limiter,
            @Value("${app.admissions.public-api.enabled:false}") boolean enabled) {
        this.explorer=explorer; this.limiter=limiter; this.enabled=enabled;
    }
    @GetMapping("/universities")
    public ResponseEntity<?> universities(@RequestParam int academicYear, HttpServletRequest request) {
        guard(request); return ok(explorer.universities(academicYear));
    }
    @GetMapping("/departments")
    public ResponseEntity<?> departments(@RequestParam int academicYear, @RequestParam long universityId,
            @RequestParam(defaultValue="") String q, HttpServletRequest request) {
        guard(request); return ok(explorer.departments(academicYear, universityId, q));
    }
    @GetMapping("/departments/{id}")
    public ResponseEntity<?> detail(@PathVariable long id, @RequestParam int academicYear,
            @RequestParam(defaultValue="ALL") String round, HttpServletRequest request) {
        guard(request); return ok(explorer.detail(academicYear, id, round));
    }
    private void guard(HttpServletRequest request) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "입시자료 탐색은 아직 이용할 수 없습니다.");
        if (!limiter.tryAcquire("explore-"+limiter.resolveClientIp(request)))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 조회해 주세요.");
    }
    static ResponseEntity<?> ok(Object data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data); }
}
