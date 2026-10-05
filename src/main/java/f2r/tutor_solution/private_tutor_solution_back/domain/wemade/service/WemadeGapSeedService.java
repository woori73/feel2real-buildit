package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeGapFactor;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadePriceEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeGapFactorRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadePriceEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 가격괴리 분석 시드 로더. 검증된 1차 데이터(괴리 요인, 가격 이벤트 타임라인)를
 * 앱 기동 시 1회 적재한다. content_hash dedupe로 재기동해도 중복 저장하지 않는다.
 * 수급 데이터는 시드에 포함하지 않는다 — 확보되지 않은 데이터는 만들지 않는다.
 * (신규 파일: 기존 WemadeSeedService는 수정하지 않는다.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WemadeGapSeedService {

    private final WemadeGapFactorRepository factorRepository;
    private final WemadePriceEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void loadOnStartup() {
        try {
            loadGapFactors();
            loadPriceEvents();
        } catch (Exception e) {
            // 시드 실패가 앱 기동을 막지 않는다.
            log.warn("WEMADE gap seed load failed (non-fatal): {}", e.getMessage());
        }
    }

    private void loadGapFactors() throws Exception {
        List<Map<String, Object>> rows = readJson("wemade/seed/gap_factors.json",
                new TypeReference<>() {});
        int n = 0;
        for (Map<String, Object> r : rows) {
            String hash = (String) r.get("content_hash");
            if (factorRepository.existsByContentHash(hash)) continue;
            factorRepository.save(WemadeGapFactor.builder()
                    .factorKey((String) r.get("factor_key"))
                    .hypothesisNo(((Number) r.get("hypothesis_no")).intValue())
                    .nameKo((String) r.get("name_ko"))
                    .amountLowKrw(toLong(r.get("amount_low_krw")))
                    .amountHighKrw(toLong(r.get("amount_high_krw")))
                    .basis((String) r.get("basis"))
                    .grade((String) r.getOrDefault("grade", "U"))
                    .sourceName((String) r.get("source_name"))
                    .sourceUrl((String) r.get("source_url"))
                    .asOfDate(r.get("as_of_date") == null ? null : LocalDate.parse((String) r.get("as_of_date")))
                    .contentHash(hash)
                    .build());
            n++;
        }
        log.info("WEMADE gap seed: factors {} rows", n);
    }

    private void loadPriceEvents() throws Exception {
        List<Map<String, Object>> rows = readJson("wemade/seed/price_events.json",
                new TypeReference<>() {});
        int n = 0;
        for (Map<String, Object> r : rows) {
            String hash = (String) r.get("content_hash");
            if (eventRepository.existsByContentHash(hash)) continue;
            eventRepository.save(WemadePriceEvent.builder()
                    .eventDate(LocalDate.parse((String) r.get("event_date")))
                    .title((String) r.get("title"))
                    .summary((String) r.get("summary"))
                    .priceChangePct(r.get("price_change_pct") == null ? null
                            : new BigDecimal(r.get("price_change_pct").toString()))
                    .closePrice(toInt(r.get("close_price")))
                    .highPrice(toInt(r.get("high_price")))
                    .lowPrice(toInt(r.get("low_price")))
                    .volume(toLong(r.get("volume")))
                    .eventType((String) r.get("event_type"))
                    .grade((String) r.getOrDefault("grade", "U"))
                    .sourceName((String) r.get("source_name"))
                    .sourceUrl((String) r.get("source_url"))
                    .contentHash(hash)
                    .build());
            n++;
        }
        log.info("WEMADE gap seed: price_events {} rows", n);
    }

    private Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private Integer toInt(Object v) {
        return v == null ? null : ((Number) v).intValue();
    }

    private <T> T readJson(String path, TypeReference<T> type) throws Exception {
        ClassPathResource res = new ClassPathResource(path);
        try (InputStream in = res.getInputStream()) {
            return objectMapper.readValue(in, type);
        }
    }
}
