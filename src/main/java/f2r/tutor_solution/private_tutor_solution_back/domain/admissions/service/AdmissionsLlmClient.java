package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.config.AdmissionsLlmProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.MetaMuseSparkLlmTransport;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmTransport;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NvidiaNimLlmTransport;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.OpenRouterQwenLlmTransport;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.WikiRouterLlmTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 입시상담 가이드 전용 LLM 클라이언트.
 *
 * <p>지피지기의 {@code NewStartLlmClient}와 동일한 호출 계약을 따른다:
 * 정상 경로 primary 1회, 기술적 실패 시에만 fallback 1회. 그 이상 재시도 없음.
 *
 * <p>transport 빈(WikiRouter/Meta/NVIDIA/OpenRouter)은 NewStart와 공유한다 (인프라 재사용).
 * primary 선택만 {@code app.admissions.llm.primary} 설정으로 분리한다.
 * NewStart 운영 코드는 변경하지 않는다.
 *
 * <p>retrieval은 여기서 하지 않는다 — 근거 조립은 호출 전 {@code AdmissionsResearchService}가 1회만 수행한다.
 */
@Component
public class AdmissionsLlmClient {

    private static final Logger log = LoggerFactory.getLogger(AdmissionsLlmClient.class);

    private final NewStartLlmTransport primary;
    private final NewStartLlmTransport fallback;

    /** 테스트·수동 조립용. */
    public AdmissionsLlmClient(NewStartLlmTransport primary, NewStartLlmTransport fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    /** Spring 조립: primary는 입시상담 설정으로 선택하고 선택되지 않은 transport는 보존된다. */
    @Autowired
    public AdmissionsLlmClient(
            AdmissionsLlmProperties properties,
            WikiRouterLlmTransport wikiRouter,
            NvidiaNimLlmTransport nvidia,
            MetaMuseSparkLlmTransport metaMuseSpark,
            OpenRouterQwenLlmTransport openRouter) {
        this(selectPrimary(properties == null ? null : properties.getPrimary(), wikiRouter, nvidia, metaMuseSpark),
                openRouter);
        log.info("admissions llm primary={} fallback={}", this.primary.name(), openRouter.name());
    }

    static NewStartLlmTransport selectPrimary(String configured, NewStartLlmTransport wikiRouter,
                                             NewStartLlmTransport nvidia, NewStartLlmTransport metaMuseSpark) {
        String value = configured == null ? "" : configured.trim();
        if ("meta".equalsIgnoreCase(value) && metaMuseSpark != null) {
            return metaMuseSpark;
        }
        return "nvidia".equalsIgnoreCase(value) ? nvidia : wikiRouter;
    }

    public NewStartLlmCompletion complete(String systemPrompt, String prompt, int maxOutputChars) {
        long tPrimary = System.nanoTime();
        NewStartLlmCompletion first = primary.complete(systemPrompt, prompt, maxOutputChars);
        if (first.ok()) {
            log.info("admissions llm attempts=1 fallbackUsed=false primaryMs={} model={}",
                    ms(System.nanoTime() - tPrimary), first.model());
            return cap(first, maxOutputChars);
        }
        if (!first.fallbackEligible()) {
            log.info("admissions llm attempts=1 fallbackUsed=false nonRetryablePrimaryFailure primaryMs={}",
                    ms(System.nanoTime() - tPrimary));
            return first;
        }
        long tFallback = System.nanoTime();
        NewStartLlmCompletion second = fallback.complete(systemPrompt, prompt, maxOutputChars);
        log.info("admissions llm attempts=2 fallbackUsed=true primaryMs={} fallbackMs={} fallbackOk={} model={}",
                ms(tFallback - tPrimary), ms(System.nanoTime() - tFallback), second.ok(), second.model());
        if (second.ok()) {
            return cap(second, maxOutputChars);
        }
        return second;
    }

    /** 답변 출력 상한. */
    private static NewStartLlmCompletion cap(NewStartLlmCompletion completion, int maxOutputChars) {
        if (maxOutputChars <= 0 || completion.text().length() <= maxOutputChars) {
            return completion;
        }
        return NewStartLlmCompletion.success(
                completion.text().substring(0, maxOutputChars), completion.provider(), completion.model());
    }

    private static long ms(long nanos) {
        return Math.round(nanos / 1_000_000.0);
    }
}
