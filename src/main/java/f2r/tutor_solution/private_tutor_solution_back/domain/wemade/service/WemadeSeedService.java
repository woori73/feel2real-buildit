package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 시드 로더. 검증된 1차 데이터(실측 OHLCV, 정정된 Fact Check, 확인된 이벤트)를
 * 앱 기동 시 1회 적재한다. hash/claim_key dedupe로 재기동해도 중복 저장하지 않는다.
 * 확보되지 않은 데이터는 만들지 않는다 — 없는 것은 비워 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WemadeSeedService {

    private final WemadeRawPriceRepository priceRepository;
    private final WemadeEventRepository eventRepository;
    private final WemadeFactCheckRepository factCheckRepository;
    private final WemadeGameTrackingRepository gameTrackingRepository;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void loadOnStartup() {
        try {
            loadPrices();
            loadEvents();
            loadFactChecks();
            loadGameTracking();
        } catch (Exception e) {
            // 시드 실패가 앱 기동을 막지 않는다.
            log.warn("WEMADE seed load failed (non-fatal): {}", e.getMessage());
        }
    }

    private void loadPrices() throws Exception {
        if (priceRepository.count() > 0) return;
        List<Map<String, Object>> rows = readJson("wemade/seed/prices.json",
                new TypeReference<>() {});
        int n = 0;
        for (Map<String, Object> r : rows) {
            String date = (String) r.get("date");
            String hash = WemadeCollectionService.sha256(
                    date + "|" + r.get("open") + "|" + r.get("high") + "|" + r.get("low")
                            + "|" + r.get("close") + "|" + r.get("volume"));
            if (priceRepository.existsByContentHash(hash)) continue;
            WemadeRawPrice p = WemadeRawPrice.builder()
                    .tradeDate(LocalDate.parse(date, java.time.format.DateTimeFormatter.BASIC_ISO_DATE))
                    .openPrice((Integer) r.get("open"))
                    .highPrice((Integer) r.get("high"))
                    .lowPrice((Integer) r.get("low"))
                    .closePrice((Integer) r.get("close"))
                    .volume(((Number) r.get("volume")).longValue())
                    .source("NAVER_FCHART_SEED")
                    .contentHash(hash)
                    .collectedAt(LocalDateTime.now())
                    .build();
            priceRepository.save(p);
            n++;
        }
        log.info("WEMADE seed: prices {} rows", n);
    }

    private void loadEvents() throws Exception {
        List<Map<String, Object>> rows = readJson("wemade/seed/events.json",
                new TypeReference<>() {});
        int n = 0;
        for (Map<String, Object> r : rows) {
            String hash = WemadeCollectionService.sha256(r.get("date") + "|" + r.get("title"));
            if (eventRepository.existsByContentHash(hash)) continue;
            eventRepository.save(WemadeEvent.builder()
                    .eventDate(LocalDate.parse((String) r.get("date")))
                    .category((String) r.get("category"))
                    .title((String) r.get("title"))
                    .summary((String) r.get("summary"))
                    .source((String) r.get("source"))
                    .sourceName((String) r.get("sourceName"))
                    .sourceUrl((String) r.get("sourceUrl"))
                    .factGrade((String) r.get("grade"))
                    .contentHash(hash)
                    .build());
            n++;
        }
        log.info("WEMADE seed: events {} rows", n);
    }

    private void loadFactChecks() throws Exception {
        List<Map<String, Object>> rows = readJson("wemade/seed/fact_checks.json",
                new TypeReference<>() {});
        int n = 0;
        for (Map<String, Object> r : rows) {
            String key = (String) r.get("claimKey");
            if (factCheckRepository.findByClaimKey(key).isPresent()) continue;
            factCheckRepository.save(WemadeFactCheck.builder()
                    .claimKey(key)
                    .claim((String) r.get("claim"))
                    .grade((String) r.get("grade"))
                    .verdict((String) r.get("verdict"))
                    .basis((String) r.get("basis"))
                    .sourceName((String) r.get("sourceName"))
                    .sourceUrl((String) r.get("sourceUrl"))
                    .reviewedAt(LocalDateTime.now())
                    .build());
            n++;
        }
        log.info("WEMADE seed: fact_checks {} rows", n);
    }

    private void loadGameTracking() throws Exception {
        List<Map<String, Object>> rows = readJson("wemade/seed/game_tracking.json",
                new TypeReference<>() {});
        int n = 0;
        for (Map<String, Object> r : rows) {
            String hash = WemadeCollectionService.sha256(
                    r.get("trackedAt") + "|" + r.get("gameName") + "|" + r.get("item") + "|" + r.get("detail"));
            if (gameTrackingRepository.existsByContentHash(hash)) continue;
            gameTrackingRepository.save(WemadeGameTracking.builder()
                    .trackedAt(LocalDate.parse((String) r.get("trackedAt")))
                    .gameName((String) r.get("gameName"))
                    .item((String) r.get("item"))
                    .detail((String) r.get("detail"))
                    .sourceType((String) r.get("sourceType"))
                    .sourceName((String) r.get("sourceName"))
                    .sourceUrl((String) r.get("sourceUrl"))
                    .contentHash(hash)
                    .collectedAt(LocalDateTime.now())
                    .build());
            n++;
        }
        log.info("WEMADE seed: game_tracking {} rows", n);
    }

    private <T> T readJson(String path, TypeReference<T> type) throws Exception {
        ClassPathResource res = new ClassPathResource(path);
        try (InputStream in = res.getInputStream()) {
            return objectMapper.readValue(in, type);
        }
    }
}
