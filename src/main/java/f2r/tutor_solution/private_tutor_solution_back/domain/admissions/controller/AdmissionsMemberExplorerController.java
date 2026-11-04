package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsExplorerService;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsMemberAccess;
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
    public AdmissionsMemberExplorerController(AdmissionsMemberAccess access, AdmissionsExplorerService explorer) {
        this.access=access; this.explorer=explorer;
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
