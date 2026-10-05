package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.scheduler;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeChinaService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 장 마감 후 자동수집. 평일 16:30 KST.
 * source별 독립 실행 — 실패한 source가 전체를 rollback하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WemadeCollectionScheduler {

    private final WemadeCollectionService collectionService;
    private final WemadeChinaService chinaService;

    @Scheduled(cron = "0 30 16 * * MON-FRI", zone = "Asia/Seoul")
    public void collectAfterMarketClose() {
        log.info("WEMADE scheduled collection start");
        runSafely("PRICE", collectionService::collectPrices);
        runSafely("FLOW", collectionService::collectFlows);
        runSafely("DISCLOSURE", collectionService::collectDisclosures);
        runSafely("GAME", collectionService::collectGameInfo);
        runSafely("CHINA_DISCLOSURE", chinaService::collectDisclosures);
        runSafely("CHINA_NEWS", chinaService::collectNews);
        runSafely("CATALYST_POLICY", this::checkCatalystPolicy);
        log.info("WEMADE scheduled collection done");
    }

    /**
     * Q4 Triple Catalyst 정책 추적 일일 점검.
     * 디지털자산기본법 입법 단계는 국회·금융위 공식 자료 확인이 필요하므로
     * 자동 수집원이 확정되기 전까지는 상태 점검 로그만 남긴다.
     */
    private void checkCatalystPolicy() {
        log.info("WEMADE catalyst policy check: 디지털자산기본법 입법 단계 수동 확인 필요");
    }

    private void runSafely(String name, Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            // 한 source 실패가 나머지를 막지 않는다.
            log.warn("WEMADE collection source {} failed (isolated): {}", name, e.getMessage());
        }
    }
}
