package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsExplorerService;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsMemberAccess;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsJeongsiScoreService;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard/admissions/explore")
@PreAuthorize("hasAnyRole('ADMIN','TUTOR')")
public class AdmissionsMemberExplorerController {
    private final AdmissionsMemberAccess access;
    private final AdmissionsExplorerService explorer;
    private final AdmissionsJeongsiScoreService scores;
    private final AdmissionsRateLimiter limiter;
    public AdmissionsMemberExplorerController(AdmissionsMemberAccess access, AdmissionsExplorerService explorer,
            AdmissionsJeongsiScoreService scores, AdmissionsRateLimiter limiter) {
        this.access=access; this.explorer=explorer; this.scores=scores; this.limiter=limiter;
    }
    @PostMapping("/score")
    public ResponseEntity<?> score(@AuthenticationPrincipal AuthenticatedPrincipal p,
            @RequestBody AdmissionsJeongsiController.ScoreRequest request) {
        access.token(p);
        if (request==null || request.academicYear()==null || request.academicYear()!=2027)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "정시 환산점수는 2027학년도만 계산합니다.");
        if (!limiter.tryAcquire("score-member-"+p.userId()))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 계산해 주세요.");
        try {
            return AdmissionsExplorerController.ok(scores.calculate(request.university(), 2027, request.typeName(),
                    request.departmentName(), request.scores()));
        } catch (AdmissionsJeongsiScoreService.InvalidScoreRequestException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
    @GetMapping("/universities")
    public ResponseEntity<?> universities(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam int academicYear) {
        access.token(p); return AdmissionsExplorerController.ok(explorer.universities(academicYear));
    }
    @GetMapping("/departments")
    public ResponseEntity<?> departments(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam int academicYear,
            @RequestParam long universityId, @RequestParam(defaultValue="") String q) {
        access.token(p); return AdmissionsExplorerController.ok(explorer.departments(academicYear, universityId, q));
    }
    @GetMapping("/departments/{id}")
    public ResponseEntity<?> detail(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable long id,
            @RequestParam int academicYear, @RequestParam(defaultValue="ALL") String round) {
        access.token(p); return AdmissionsExplorerController.ok(explorer.detail(academicYear, id, round));
    }
}
