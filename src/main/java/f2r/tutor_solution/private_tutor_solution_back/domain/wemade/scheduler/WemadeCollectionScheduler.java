package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.scheduler;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeAnomalyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeChinaService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeCollectionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeSurgeAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 장 마감 후 자동수집. 평일 16:30 KST.
 * source별 독립 실행 — 실패한 source가 전체를 rollback하지 않는다.
 * PRICE/FLOW는 pykrx 우선 (KRX 공식, 키 불필요). pykrx 불가 시 PRICE만 네이버 폴백.
 * 네이버 bot 우회는 시도하지 않는다.
 *
 * 장중 이상수급 지표 수집: 평일 09:10~15:30, 10분 간격.
 * KIS 원시 틱 데이터는 DB에 저장하지 않는다 (PRIVATE). 파생 지표만 저장 (INTERNAL).
 * 주문/자동매매 API는 절대 연결하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WemadeCollectionScheduler {

    private final WemadeCollectionService collectionService;
    private final WemadeChinaService chinaService;
    private final WemadeSurgeAnalysisService surgeAnalysisService;
    private final WemadeAnomalyService anomalyService;

    @Scheduled(cron = "0 30 16 * * MON-FRI", zone = "Asia/Seoul")
    public void collectAfterMarketClose() {
        // 2026-10-09 사용자 지시: 휴장일에는 KIS 수집 실행 안 함
        if (!KrxMarketCalendar.isTradingDay()) {
            log.info("WEMADE scheduled collection skipped (market holiday)");
            return;
        }
        log.info("WEMADE scheduled collection start");
        runSafely("PRICE", collectionService::collectPrices);
        runSafely("FLOW", collectionService::collectFlows);
        // KIS 일별 투자자 수급 (pykrx 미확보 시 대체원)
        runSafely("KIS_FLOW", collectionService::collectKisFlows);
        // 급등일 수급 분석 (pykrx 백필 후): 수급 미확보 시 수급 필드는 null로 둔다.
        runSafely("SURGE_ANALYSIS", () -> surgeAnalysisService.analyzeSurges("112040"));
        // 과거 유사 패턴 검색 (2026-06-29 이후)
        runSafely("SIMILAR_PATTERNS", anomalyService::searchSimilarPatterns);
        runSafely("DISCLOSURE", collectionService::collectDisclosures);
        runSafely("GAME", collectionService::collectGameInfo);
        runSafely("CHINA_DISCLOSURE", chinaService::collectDisclosures);
        runSafely("CHINA_NEWS", chinaService::collectNews);
        runSafely("CATALYST_POLICY", this::checkCatalystPolicy);
        log.info("WEMADE scheduled collection done");
    }

    /**
     * 장중 이상수급 지표 수집. 평일 09:10~15:30, 10분 간격.
     * 각 지표를 독립 수치로 저장하고 (1단계), 조합 분석은 별도로 수행한다 (2단계).
     * KIS 키가 없으면 조용히 스킵된다 (PENDING).
     */
    @Scheduled(cron = "0 10/10 9-15 * * MON-FRI", zone = "Asia/Seoul")
    public void collectIntradayAnomalyMetrics() {
        // 2026-10-09 사용자 지시: 휴장일에는 KIS 수집 실행 안 함
        if (!KrxMarketCalendar.isTradingDay()) {
            log.info("WEMADE intraday anomaly metrics skipped (market holiday)");
            return;
        }
        log.info("WEMADE intraday anomaly metrics start");
        runSafely("ANOMALY_METRICS", anomalyService::collectIntradayMetrics);
        runSafely("ANOMALY_ANALYZE", anomalyService::analyzeCombined);
        log.info("WEMADE intraday anomaly metrics done");
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
