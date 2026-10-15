package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 입시상담 가이드 채팅 API — 비회원 이용.
 *
 * <p>SecurityConfig에서 {@code /api/admissions/chat}은 permitAll.
 * 남용 방지를 위해 IP 기반 rate limit 적용 (분당 10회, 시간당 60회) —
 * {@link AdmissionsRateLimiter}가 X-Forwarded-For 위조 방지와 기록 상한을 담당한다.
 *
 * <p>에러 상세(provider 응답, exception, stack trace)는 브라우저로 노출하지 않는다.
 */
@RestController
@RequestMapping("/api/admissions")
public class AdmissionsChatController {

    private static final Logger log = LoggerFactory.getLogger(AdmissionsChatController.class);

    private final AdmissionsChatService admissionsChatService;
    private final AdmissionsRateLimiter rateLimiter;

    public AdmissionsChatController(AdmissionsChatService admissionsChatService,
                                    AdmissionsRateLimiter rateLimiter) {
        this.admissionsChatService = admissionsChatService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        long t0 = System.nanoTime();
        String clientIp = rateLimiter.resolveClientIp(httpRequest);

        // rate limit 검사
        if (!rateLimiter.tryAcquire(clientIp)) {
            log.warn("admissions chat rate limited: ip={}", maskIp(clientIp));
            return ResponseEntity.status(429).body(ChatResponse.failure(
                    "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", List.<SourceDto>of()));
        }

        String message = request == null ? null : request.message();
        Integer year = request == null ? null : request.academicYear();
        String sessionToken = request == null ? null : request.sessionToken();
        String studentProfile = request == null || request.student() == null ? null : request.student().summary();

        // 학년도 검증 (2027/2028만 허용)
        if (year != null && year != 2027 && year != 2028) {
            return ResponseEntity.badRequest().body(ChatResponse.failure(
                    "학년도는 2027 또는 2028만 지정할 수 있습니다.", List.<SourceDto>of()));
        }

        AdmissionsChatService.AdmissionsAnswer answer =
                admissionsChatService.ask(message, year, sessionToken, studentProfile);

        log.info("admissions chat endpoint ip={} handlerMs={} status={} sourceCount={} answerChars={}",
                maskIp(clientIp), ms(System.nanoTime() - t0), answer.ok() ? "ok" : "error",
                answer.sources().size(), answer.answer().length());

        if (!answer.ok()) {
            return ResponseEntity.badRequest().body(ChatResponse.failure(
                    answer.errorMessage(), toSourceDtos(answer.sources())));
        }
        return ResponseEntity.ok(ChatResponse.success(
                answer.answer(), answer.model(), toSourceDtos(answer.sources()),
                answer.academicYear(), answer.yearConfirmed()));
    }

    /** IP 마스킹 (로그용). */
    private static String maskIp(String ip) {
        if (ip == null) return "unknown";
        int lastDot = ip.lastIndexOf('.');
        int lastColon = ip.lastIndexOf(':');
        if (lastDot > 0) return ip.substring(0, lastDot) + ".xxx";
        if (lastColon > 0) return ip.substring(0, lastColon) + ":xxxx";
        return "xxx";
    }

    private static long ms(long nanos) {
        return Math.round(nanos / 1_000_000.0);
    }

    private static List<SourceDto> toSourceDtos(List<AdmissionsChatService.AnswerSource> sources) {
        return sources.stream().map(s -> new SourceDto(s.kind(), s.title(), s.url(), s.id())).toList();
    }

    /**
     * @param message 사용자 질문.
     * @param academicYear 2027 또는 2028 (null=전체).
     * @param sessionToken 비회원 세션 토큰 (상담 이력 연결용, null이면 새로 발급).
     */
    public record ChatRequest(String message, Integer academicYear, String sessionToken, StudentProfile student) {
        public ChatRequest(String message, Integer academicYear, String sessionToken) {
            this(message, academicYear, sessionToken, null);
        }
    }

    public record StudentProfile(String grade, String targetUniversities, String targetMajors,
                                 String admissionTrack, String academicSummary) {
        String summary() {
            return "학년: " + clean(grade, 30)
                    + "\n희망 대학: " + clean(targetUniversities, 200)
                    + "\n희망 전공: " + clean(targetMajors, 200)
                    + "\n관심 전형: " + clean(admissionTrack, 100)
                    + "\n성적 요약: " + clean(academicSummary, 500);
        }
        private static String clean(String value, int max) {
            if (value == null) return "미입력";
            String cleaned = value.strip().replaceAll("[\\r\\n]+", " ");
            return cleaned.isEmpty() ? "미입력" : cleaned.substring(0, Math.min(cleaned.length(), max));
        }
    }

    /** 답변 근거 출처. kind는 "공식 자료 (확정)" / "공식 자료 (계획)" / "공식자료 원문". */
    public record SourceDto(String kind, String title, String url, String id) {}

    /**
     * @param status "ok" | "error".
     * @param academicYear 답변 기준 학년도 (null=전체).
     * @param yearConfirmed true=확정자료 기반, false=계획자료 포함.
     */
    public record ChatResponse(String status, String answer, String errorMessage, String model,
                               List<SourceDto> sources, Integer academicYear, Boolean yearConfirmed) {
        public static ChatResponse success(String answer, String model, List<SourceDto> sources,
                                           Integer academicYear, Boolean yearConfirmed) {
            return new ChatResponse("ok", answer, null, model, sources, academicYear, yearConfirmed);
        }

        public static ChatResponse failure(String errorMessage, List<SourceDto> sources) {
            return new ChatResponse("error", "", errorMessage, null, sources, null, null);
        }
    }
}
