package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 입시상담 가이드 채팅 API — 비회원 이용.
 *
 * <p>SecurityConfig에서 {@code /api/admissions/chat}은 permitAll.
 * 남용 방지를 위해 IP 기반 rate limit 적용 (분당 10회, 시간당 60회).
 *
 * <p>에러 상세(provider 응답, exception, stack trace)는 브라우저로 노출하지 않는다.
 */
@RestController
@RequestMapping("/api/admissions")
public class AdmissionsChatController {

    private static final Logger log = LoggerFactory.getLogger(AdmissionsChatController.class);

    /** 분당 최대 요청. */
    static final int RATE_LIMIT_PER_MINUTE = 10;
    /** 시간당 최대 요청. */
    static final int RATE_LIMIT_PER_HOUR = 60;

    private final AdmissionsChatService admissionsChatService;

    /** IP → 요청 시각 목록 (rate limit용). */
    private final Map<String, List<Long>> requestTimes = new ConcurrentHashMap<>();

    public AdmissionsChatController(AdmissionsChatService admissionsChatService) {
        this.admissionsChatService = admissionsChatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        long t0 = System.nanoTime();
        String clientIp = clientIp(httpRequest);

        // rate limit 검사
        if (!checkRateLimit(clientIp)) {
            log.warn("admissions chat rate limited: ip={}", maskIp(clientIp));
            return ResponseEntity.status(429).body(ChatResponse.failure(
                    "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", List.of()));
        }

        String message = request == null ? null : request.message();
        Integer year = request == null ? null : request.academicYear();
        String sessionToken = request == null ? null : request.sessionToken();

        // 학년도 검증 (2027/2028만 허용)
        if (year != null && year != 2027 && year != 2028) {
            return ResponseEntity.badRequest().body(ChatResponse.failure(
                    "학년도는 2027 또는 2028만 지정할 수 있습니다.", List.of()));
        }

        AdmissionsChatService.AdmissionsAnswer answer =
                admissionsChatService.ask(message, year, sessionToken);

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

    /**
     * IP 기반 rate limit. 분당 10회, 시간당 60회 초과 시 false.
     */
    boolean checkRateLimit(String ip) {
        long now = Instant.now().toEpochMilli();
        long oneMinuteAgo = now - 60_000;
        long oneHourAgo = now - 3_600_000;
        List<Long> times = requestTimes.computeIfAbsent(ip, k -> new java.util.ArrayList<>());
        synchronized (times) {
            times.removeIf(t -> t < oneHourAgo);
            long lastMinute = times.stream().filter(t -> t >= oneMinuteAgo).count();
            if (lastMinute >= RATE_LIMIT_PER_MINUTE || times.size() >= RATE_LIMIT_PER_HOUR) {
                return false;
            }
            times.add(now);
            return true;
        }
    }

    private static String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
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
        return sources.stream().map(s -> new SourceDto(s.kind(), s.title(), s.url())).toList();
    }

    /**
     * @param message 사용자 질문.
     * @param academicYear 2027 또는 2028 (null=전체).
     * @param sessionToken 비회원 세션 토큰 (상담 이력 연결용, null이면 새로 발급).
     */
    public record ChatRequest(String message, Integer academicYear, String sessionToken) {}

    /** 답변 근거 출처. kind는 "공식 자료 (확정)" / "공식 자료 (계획)" / "공식자료 원문". */
    public record SourceDto(String kind, String title, String url) {}

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
