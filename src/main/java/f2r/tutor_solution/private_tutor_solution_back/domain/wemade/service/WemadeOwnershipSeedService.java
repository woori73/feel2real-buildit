package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeOwnershipEdge;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeOwnershipNode;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadePerson;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeOwnershipEdgeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeOwnershipNodeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadePersonRepository;
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
 * 지배구조 시드 로더. 확인된 사실만 담은 JSON 3종을 앱 기동 시 1회 적재한다.
 * node_key/person_key/content_hash dedupe로 재기동해도 중복 저장하지 않는다.
 * 기존 WemadeSeedService는 수정하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WemadeOwnershipSeedService {

    private final WemadeOwnershipNodeRepository nodeRepository;
    private final WemadeOwnershipEdgeRepository edgeRepository;
    private final WemadePersonRepository personRepository;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void loadOnStartup() {
        try {
            loadNodes();
            loadEdges();
            loadPersons();
        } catch (Exception e) {
            // 시드 실패가 앱 기동을 막지 않는다.
            log.warn("WEMADE ownership seed load failed (non-fatal): {}", e.getMessage());
        }
    }

    private void loadNodes() throws Exception {
        List<Map<String, Object>> rows = readJson("wemade/seed/ownership_nodes.json");
        int n = 0;
        for (Map<String, Object> r : rows) {
            String key = (String) r.get("nodeKey");
            String hash = WemadeCollectionService.sha256("ownership-node|" + key + "|" + r.get("name")
                    + "|" + r.get("stakePct") + "|" + r.get("grade"));
            if (nodeRepository.findByNodeKey(key).isPresent()
                    || nodeRepository.existsByContentHash(hash)) continue;
            nodeRepository.save(WemadeOwnershipNode.builder()
                    .nodeKey(key)
                    .name((String) r.get("name"))
                    .nameZh((String) r.get("nameZh"))
                    .nodeType((String) r.get("nodeType"))
                    .country((String) r.get("country"))
                    .stakePct(toDecimal(r.get("stakePct")))
                    .stakeNote((String) r.get("stakeNote"))
                    .roleDesc((String) r.get("roleDesc"))
                    .grade((String) r.get("grade"))
                    .sourceName((String) r.get("sourceName"))
                    .sourceUrl((String) r.get("sourceUrl"))
                    .asOfDate(toDate((String) r.get("asOfDate")))
                    .contentHash(hash)
                    .build());
            n++;
        }
        log.info("WEMADE seed: ownership_nodes {} rows", n);
    }

    private void loadEdges() throws Exception {
        List<Map<String, Object>> rows = readJson("wemade/seed/ownership_edges.json");
        int n = 0;
        for (Map<String, Object> r : rows) {
            String hash = WemadeCollectionService.sha256("ownership-edge|" + r.get("parentKey")
                    + "|" + r.get("childKey") + "|" + r.get("stakePct"));
            if (edgeRepository.existsByContentHash(hash)) continue;
            edgeRepository.save(WemadeOwnershipEdge.builder()
                    .parentKey((String) r.get("parentKey"))
                    .childKey((String) r.get("childKey"))
                    .stakePct(toDecimal(r.get("stakePct")))
                    .note((String) r.get("note"))
                    .grade((String) r.get("grade"))
                    .sourceName((String) r.get("sourceName"))
                    .sourceUrl((String) r.get("sourceUrl"))
                    .asOfDate(toDate((String) r.get("asOfDate")))
                    .contentHash(hash)
                    .build());
            n++;
        }
        log.info("WEMADE seed: ownership_edges {} rows", n);
    }

    private void loadPersons() throws Exception {
        List<Map<String, Object>> rows = readJson("wemade/seed/persons.json");
        int n = 0;
        for (Map<String, Object> r : rows) {
            String key = (String) r.get("personKey");
            String hash = WemadeCollectionService.sha256("person|" + key + "|" + r.get("name")
                    + "|" + r.get("grade"));
            if (personRepository.findByPersonKey(key).isPresent()
                    || personRepository.existsByContentHash(hash)) continue;
            personRepository.save(WemadePerson.builder()
                    .personKey(key)
                    .name((String) r.get("name"))
                    .nameZh((String) r.get("nameZh"))
                    .bioKo((String) r.get("bioKo"))
                    .education((String) r.get("education"))
                    .affiliations((String) r.get("affiliations"))
                    .pastRoles((String) r.get("pastRoles"))
                    .relationKingnet((String) r.get("relationKingnet"))
                    .relationPark((String) r.get("relationPark"))
                    .relationMirIp((String) r.get("relationMirIp"))
                    .verificationNote((String) r.get("verificationNote"))
                    .grade((String) r.get("grade"))
                    .sourceName((String) r.get("sourceName"))
                    .sourceUrl((String) r.get("sourceUrl"))
                    .asOfDate(toDate((String) r.get("asOfDate")))
                    .contentHash(hash)
                    .build());
            n++;
        }
        log.info("WEMADE seed: persons {} rows", n);
    }

    private List<Map<String, Object>> readJson(String path) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<>() {});
        }
    }

    private BigDecimal toDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        return new BigDecimal(v.toString());
    }

    private LocalDate toDate(String s) {
        return s == null ? null : LocalDate.parse(s);
    }
}
