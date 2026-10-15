package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeCatalystEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadePolicyTracking;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadePriceEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeCatalystEventRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadePolicyTrackingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadePriceEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 2026 Q4 Triple Catalyst 분석. 읽기 전용.
 * - 3대 이벤트(10/30 거래종결 / Night Crows W / 디지털자산기본법) 상태 + D-Day 자동 계산
 * - 디지털자산법 입법 단계 추적 (확정 사건으로 표시하지 않음)
 * - 복합 시나리오: 2026-01 정책 기대 → 6/29 판호 → 6/30 공시 → 7/ 급락 → 반등/재하락 → Q4 3대 이벤트 타임라인
 * 모든 수치는 DB 실측 + 확정 상수의 deterministic 계산. 단일 목표주가 없음. 투자 권유가 아니다.
 */
@Service
@RequiredArgsConstructor
public class WemadeCatalystService {

    private final WemadeCatalystEventRepository catalystRepository;
    private final WemadePolicyTrackingRepository policyRepository;
    private final WemadePriceEventRepository priceEventRepository;
    private final ObjectMapper objectMapper;

    /** 3대 이벤트 카드. D-Day는 서버 일자로 자동 계산한다. */
    public List<Map<String, Object>> catalysts() {
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (WemadeCatalystEvent e : catalystRepository.findAllByOrderByCatalystKeyAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("catalystKey", e.getCatalystKey());
            m.put("nameKo", e.getNameKo());
            m.put("status", e.getStatus());
            m.put("targetDate", e.getTargetDate() == null ? null : e.getTargetDate().toString());
            m.put("targetLabel", e.getTargetLabel());
            m.put("dday", ddayLabel(e.getTargetDate(), today));
            m.put("summary", e.getSummary());
            m.put("keyFacts", parseKeyFacts(e.getKeyFacts()));
            m.put("grade", e.getGrade());
            m.put("sourceName", e.getSourceName());
            m.put("sourceUrl", e.getSourceUrl());
            m.put("asOfDate", e.getAsOfDate() == null ? null : e.getAsOfDate().toString());
            out.add(m);
        }
        return out;
    }

    /** 디지털자산기본법 입법 추적. 통과 미확정이면 isConfirmed=false. */
    public List<Map<String, Object>> policy() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WemadePolicyTracking p : policyRepository.findAllByOrderByPolicyKeyAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("policyKey", p.getPolicyKey());
            m.put("title", p.getTitle());
            m.put("stage", p.getStage());
            m.put("stageDate", p.getStageDate() == null ? null : p.getStageDate().toString());
            m.put("summary", p.getSummary());
            m.put("p2eRelevance", p.getP2eRelevance());
            m.put("isConfirmed", Boolean.TRUE.equals(p.getIsConfirmed()));
            m.put("grade", p.getGrade());
            m.put("sourceName", p.getSourceName());
            m.put("sourceUrl", p.getSourceUrl());
            m.put("asOfDate", p.getAsOfDate() == null ? null : p.getAsOfDate().toString());
            out.add(m);
        }
        return out;
    }

    /**
     * 복합 시나리오: 2026-01 정책 기대 → 6/29 판호 → 6/30 최대주주 변경 공시 →
     * 7/1 상한가 → 7/2~3 급락 → 반등/재하락 → Q4 Triple Catalyst.
     * 가격 이벤트 타임라인(wemade_price_events 실측)을 재사용하고,
     * 각 구간의 서사를 phase로 묶어 반환한다. 수급 데이터는 미확보이므로 PENDING.
     */
    public Map<String, Object> composite() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> phases = new ArrayList<>();

        phases.add(phase("2026-01", "정책 기대 상승",
                "디지털자산 정책 기대감으로 연초 상승 구간. (정책 이벤트는 별도 추적)",
                "POLICY"));
        phases.add(phase("2026-06-29", "Night Crows 중국 판호 (夜鸦)",
                "NPPA 판호 획득: 夜鸦 / 国新出审〔2026〕1504号. 중국 사업 기대 점화.",
                "DEAL"));
        phases.add(phase("2026-06-30", "최대주주 변경 공시",
                "DART rcpNo 20260630900888: 박관호 39.33%를 NeoPulse가 9,200억원(주당 68,910원)에 인수. 공시 당일 +11.99% 상승 실측.",
                "DEAL"));
        phases.add(phase("2026-07-01", "상한가 (+29.85%)",
                "경영권 변경 기대 극대화. 고점 25,100원대.",
                "SURGE"));
        phases.add(phase("2026-07-02~03", "급락 (-11.95% / -13.21%)",
                "7/2 장중 28,600원 고점 후 종가 22,100원(196만주). 공매도 과열 동반.",
                "CRASH"));
        phases.add(phase("2026-07-04~09", "반등과 재하락 (lower highs)",
                "고점 낮아지는 패턴: 28,600 → 20,600 → 17,020. 7/29 저점까지 고점 대비 -52.4%.",
                "VOLUME_SPIKE"));
        phases.add(phase("2026-10-30 (예정)", "Q4 Triple Catalyst",
                "① 거래종결(잔금 8,280억) ② Night Crows W 연내 론칭 ③ 디지털자산기본법 입법 추진 — 세 축이 같은 분기에 겹친다.",
                "MILESTONE"));

        out.put("phases", phases);
        out.put("timeline", timelineWithFlow());
        out.put("flowStatus", "PENDING");
        out.put("flowNote", "외국인/기관 일별 수급 데이터는 미확보 — 확보 전까지 각 이벤트 전후 수급은 PENDING으로 표시한다. 절대 창작하지 않는다.");
        out.put("pricingNote", "현재 약 15,000원대에서 시장이 세 이벤트를 얼마나 선반영했는지는 수급 데이터 확보 후 정량 분석한다. SPA 시사 100% 가치(68,910원) 대비 현재가는 약 77% 할인 상태(가격괴리 분석 참조).");
        return out;
    }

    private Map<String, Object> phase(String when, String title, String desc, String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("when", when);
        m.put("title", title);
        m.put("description", desc);
        m.put("eventType", type);
        return m;
    }

    /** 가격 이벤트 타임라인 재사용 (OHLCV 실측 기반). */
    private List<Map<String, Object>> timelineWithFlow() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WemadePriceEvent e : priceEventRepository.findAllByOrderByEventDateAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventDate", e.getEventDate().toString());
            m.put("title", e.getTitle());
            m.put("closePrice", e.getClosePrice());
            m.put("priceChangePct", e.getPriceChangePct());
            m.put("volume", e.getVolume());
            m.put("eventType", e.getEventType());
            m.put("grade", e.getGrade());
            // 수급 미확보: flow 필드는 PENDING
            m.put("foreignNet", "PENDING");
            m.put("institutionNet", "PENDING");
            out.add(m);
        }
        return out;
    }

    private String ddayLabel(LocalDate target, LocalDate today) {
        if (target == null) return null;
        long d = ChronoUnit.DAYS.between(today, target);
        if (d > 0) return "D-" + d;
        if (d == 0) return "D-Day";
        return "D+" + (-d);
    }

    private List<Map<String, String>> parseKeyFacts(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
