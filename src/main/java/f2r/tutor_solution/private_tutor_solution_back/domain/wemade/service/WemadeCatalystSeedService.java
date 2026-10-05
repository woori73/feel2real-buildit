package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeCatalystEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadePolicyTracking;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeCatalystEventRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadePolicyTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Q4 Triple Catalyst 시드 로더. 앱 기동 시 1회 적재.
 * content_hash dedupe로 재기동해도 중복 저장하지 않는다.
 * 시드 실패가 앱 기동을 막지 않는다.
 * (신규 파일: 기존 SeedService들은 수정하지 않는다.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WemadeCatalystSeedService {

    private final WemadeCatalystEventRepository catalystRepository;
    private final WemadePolicyTrackingRepository policyRepository;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void loadOnStartup() {
        try {
            loadCatalysts();
            loadPolicies();
        } catch (Exception e) {
            log.warn("WEMADE catalyst seed load failed (non-fatal): {}", e.getMessage());
        }
    }

    private void loadCatalysts() throws Exception {
        List<Map<String, Object>> rows = readJson("wemade/seed/catalyst_events.json",
                new TypeReference<>() {});
        int n = 0;
        for (Map<String, Object> r : rows) {
            String hash = (String) r.get("content_hash");
            if (catalystRepository.existsByContentHash(hash)) continue;
            catalystRepository.save(WemadeCatalystEvent.builder()
                    .catalystKey((String) r.get("catalyst_key"))
                    .nameKo((String) r.get("name_ko"))
                    .status((String) r.getOrDefault("status", "SCHEDULED"))
                    .targetDate(r.get("target_date") == null ? null : LocalDate.parse((String) r.get("target_date")))
                    .targetLabel((String) r.get("target_label"))
                    .summary((String) r.get("summary"))
                    .keyFacts((String) r.get("key_facts"))
                    .grade((String) r.getOrDefault("grade", "U"))
                    .sourceName((String) r.get("source_name"))
                    .sourceUrl((String) r.get("source_url"))
                    .asOfDate(r.get("as_of_date") == null ? null : LocalDate.parse((String) r.get("as_of_date")))
                    .contentHash(hash)
                    .build());
            n++;
        }
        log.info("WEMADE catalyst seed: events {} rows", n);
    }

    private void loadPolicies() throws Exception {
        List<Map<String, Object>> rows = readJson("wemade/seed/policy_tracking.json",
                new TypeReference<>() {});
        int n = 0;
        for (Map<String, Object> r : rows) {
            String hash = (String) r.get("content_hash");
            if (policyRepository.existsByContentHash(hash)) continue;
            policyRepository.save(WemadePolicyTracking.builder()
                    .policyKey((String) r.get("policy_key"))
                    .title((String) r.get("title"))
                    .stage((String) r.getOrDefault("stage", "TRACKING"))
                    .stageDate(r.get("stage_date") == null ? null : LocalDate.parse((String) r.get("stage_date")))
                    .summary((String) r.get("summary"))
                    .p2eRelevance((String) r.get("p2e_relevance"))
                    .isConfirmed(Boolean.TRUE.equals(r.get("is_confirmed")))
                    .grade((String) r.getOrDefault("grade", "U"))
                    .sourceName((String) r.get("source_name"))
                    .sourceUrl((String) r.get("source_url"))
                    .asOfDate(r.get("as_of_date") == null ? null : LocalDate.parse((String) r.get("as_of_date")))
                    .contentHash(hash)
                    .build());
            n++;
        }
        log.info("WEMADE catalyst seed: policies {} rows", n);
    }

    private <T> T readJson(String path, TypeReference<T> type) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, type);
        }
    }
}
