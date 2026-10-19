package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsMemberAccess;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsChatService;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsCounselingSessionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller.AdmissionsChatController.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard/admissions")
@PreAuthorize("hasAnyRole('ADMIN','TUTOR')")
public class AdmissionsMemberController {
    private final AdmissionsMemberAccess access;
    private final AdmissionsChatService chat;
    private final AdmissionsCounselingSessionRepository sessions;
    private final AdmissionsRateLimiter limiter;
    public AdmissionsMemberController(AdmissionsMemberAccess access, AdmissionsChatService chat,
            AdmissionsCounselingSessionRepository sessions, AdmissionsRateLimiter limiter) {
        this.access=access; this.chat=chat; this.sessions=sessions; this.limiter=limiter;
    }
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> ask(@AuthenticationPrincipal AuthenticatedPrincipal p,
                                           @RequestBody ChatRequest req) {
        String token=access.token(p);
        if (req==null || req.message()==null || req.message().isBlank() || req.message().length()>2000
                || req.academicYear()==null || (req.academicYear()!=2027 && req.academicYear()!=2028))
            return response(400,ChatResponse.failure("질문과 학년도(2027 또는 2028)를 확인해 주세요.",List.of()));
        String otherYear=req.academicYear()==2027?"2028":"2027";
        if (req.message().contains(otherYear))
            return response(400,ChatResponse.failure("질문의 학년도와 선택한 학년도를 일치시켜 주세요. 학년도별로 나누어 질문할 수 있습니다.",List.of()));
        if (!limiter.tryAcquire("member-user-"+p.userId()) || !limiter.tryAcquireDaily())
            return response(429,ChatResponse.failure("상담 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.",List.of()));
        // 직전 질문은 같은 회원·학원·학년도 안에서만 검색 문맥으로 사용. 과거 답변을 사실 근거로 쓰지 않는다.
        String context=sessions.findTop20BySessionTokenOrderByCreatedAtDesc(token).stream()
                .filter(s -> req.academicYear().equals(s.getAcademicYear()))
                .map(s -> s.getQuestion()).findFirst().orElse("");
        String profile=req.student()==null ? null : req.student().summary();
        if (req.student()!=null) {
            var s=req.student();
            context=String.join(" ",clean(s.targetUniversities()),clean(s.targetMajors()),clean(s.admissionTrack()))
                    +" "+context;
        }
        var a=chat.ask(req.message(),req.academicYear(),token,profile,context);
        var sources=a.sources().stream().map(s -> new SourceDto(s.kind(),s.title(),s.url(),s.id())).toList();
        return response(a.ok()?200:400,a.ok()?ChatResponse.success(a.answer(),a.model(),sources,a.academicYear(),a.yearConfirmed())
                :ChatResponse.failure(a.errorMessage(),sources));
    }
    @GetMapping("/history")
    public ResponseEntity<List<HistoryDto>> history(@AuthenticationPrincipal AuthenticatedPrincipal p) {
        var rows=sessions.findTop20BySessionTokenOrderByCreatedAtDesc(access.token(p)).stream()
                .map(s -> new HistoryDto(s.getSessionId(),s.getQuestion(),s.getAnswer(),s.getAcademicYear(),
                        s.getEvidenceSummary(),s.getCreatedAt()==null?null:s.getCreatedAt().toString())).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rows);
    }
    private static String clean(String s) {return s==null?"":s.strip().substring(0,Math.min(s.strip().length(),200));}
    private ResponseEntity<ChatResponse> response(int status,ChatResponse body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
    public record HistoryDto(Long id,String question,String answer,Integer academicYear,String evidenceSummary,String createdAt) {}
}
