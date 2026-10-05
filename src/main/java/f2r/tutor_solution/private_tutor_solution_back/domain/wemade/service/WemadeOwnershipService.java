package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeOwnershipEdge;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeOwnershipNode;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadePerson;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeOwnershipEdgeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeOwnershipNodeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadePersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 지배구조 트리 조립.
 * DB 적재 순서와 무관하게 node_key 기준 정렬로 항상 같은 JSON을 반환한다 (deterministic).
 * 간접 경제지분은 간선 지분율의 곱으로 계산하며 grade D(계산값)로 표기한다.
 */
@Service
@RequiredArgsConstructor
public class WemadeOwnershipService {

    private final WemadeOwnershipNodeRepository nodeRepository;
    private final WemadeOwnershipEdgeRepository edgeRepository;
    private final WemadePersonRepository personRepository;

    public Map<String, Object> tree() {
        List<WemadeOwnershipNode> nodes = nodeRepository.findAllByOrderByNodeKeyAsc();
        List<WemadeOwnershipEdge> edges = edgeRepository.findAllByOrderByParentKeyAscChildKeyAsc();

        Map<String, WemadeOwnershipNode> byKey = new LinkedHashMap<>();
        for (WemadeOwnershipNode n : nodes) byKey.put(n.getNodeKey(), n);

        // parent -> [{edge, child}] (정렬된 간선 순서 유지)
        Map<String, List<Map<String, Object>>> children = new LinkedHashMap<>();
        Set<String> childKeys = new HashSet<>();
        for (WemadeOwnershipEdge e : edges) {
            childKeys.add(e.getChildKey());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("edge", edgeView(e));
            item.put("child", nodeView(byKey.get(e.getChildKey())));
            children.computeIfAbsent(e.getParentKey(), k -> new ArrayList<>()).add(item);
        }

        List<String> roots = new ArrayList<>();
        for (String k : byKey.keySet()) if (!childKeys.contains(k)) roots.add(k);
        Collections.sort(roots);

        List<Map<String, Object>> nodeViews = new ArrayList<>();
        for (WemadeOwnershipNode n : nodes) nodeViews.add(nodeView(n));
        List<Map<String, Object>> edgeViews = new ArrayList<>();
        for (WemadeOwnershipEdge e : edges) edgeViews.add(edgeView(e));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("asOfDate", "2026-10-05");
        out.put("roots", roots);
        out.put("nodes", nodeViews);
        out.put("edges", edgeViews);
        out.put("children", children);
        out.put("effectiveStakes", effectiveStakes(byKey, edges, roots));
        out.put("funding", funding());
        out.put("gradeLegend", gradeLegend());
        return out;
    }

    public List<Map<String, Object>> persons() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WemadePerson p : personRepository.findAllByOrderByPersonKeyAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("personKey", p.getPersonKey());
            m.put("name", p.getName());
            m.put("nameZh", p.getNameZh());
            m.put("bioKo", p.getBioKo());
            m.put("education", p.getEducation());
            m.put("affiliations", p.getAffiliations());
            m.put("pastRoles", p.getPastRoles());
            m.put("relationKingnet", p.getRelationKingnet());
            m.put("relationPark", p.getRelationPark());
            m.put("relationMirIp", p.getRelationMirIp());
            m.put("verificationNote", p.getVerificationNote());
            m.put("grade", p.getGrade());
            m.put("sourceName", p.getSourceName());
            m.put("sourceUrl", p.getSourceUrl());
            m.put("asOfDate", p.getAsOfDate() == null ? null : p.getAsOfDate().toString());
            out.add(m);
        }
        return out;
    }

    /**
     * 루트에서 목표 노드까지 간선 지분율을 곱한 간접 경제지분.
     * 예: mok_pui_ming -> nexpulse(100%) -> neosphere(51%) -> shengsong(100%)
     *     -> neopulse(100%) -> wemade(40.25%) = 20.5275%.
     * 계산값이므로 grade D.
     */
    private List<Map<String, Object>> effectiveStakes(
            Map<String, WemadeOwnershipNode> byKey,
            List<WemadeOwnershipEdge> edges,
            List<String> roots) {
        Map<String, List<WemadeOwnershipEdge>> byParent = new LinkedHashMap<>();
        for (WemadeOwnershipEdge e : edges) {
            byParent.computeIfAbsent(e.getParentKey(), k -> new ArrayList<>()).add(e);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (String root : roots) {
            dfs(byKey, byParent, root, BigDecimal.ONE, new ArrayList<>(), out);
        }
        out.sort(Comparator.comparing(m -> (String) m.get("rootKey")));
        return out;
    }

    private void dfs(Map<String, WemadeOwnershipNode> byKey,
                     Map<String, List<WemadeOwnershipEdge>> byParent,
                     String current, BigDecimal acc, List<String> path,
                     List<Map<String, Object>> out) {
        List<String> p2 = new ArrayList<>(path);
        p2.add(current);
        List<WemadeOwnershipEdge> next = byParent.getOrDefault(current, List.of());
        if (next.isEmpty()) {
            if ("wemade".equals(current) && p2.size() > 1) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("rootKey", p2.get(0));
                m.put("rootName", nameOf(byKey, p2.get(0)));
                m.put("targetKey", "wemade");
                m.put("path", new ArrayList<>(p2));
                m.put("effectivePct", acc.multiply(BigDecimal.valueOf(100))
                        .setScale(4, RoundingMode.HALF_UP));
                m.put("grade", "D");
                m.put("note", "간선 지분율 곱 (계산값). 의결권이 아닌 경제적 지분 추정치.");
                out.add(m);
            }
            return;
        }
        for (WemadeOwnershipEdge e : next) {
            BigDecimal pct = e.getStakePct() == null ? BigDecimal.ONE
                    : e.getStakePct().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
            dfs(byKey, byParent, e.getChildKey(), acc.multiply(pct), p2, out);
        }
    }

    private String nameOf(Map<String, WemadeOwnershipNode> byKey, String key) {
        WemadeOwnershipNode n = byKey.get(key);
        return n == null ? key : n.getName();
    }

    private Map<String, Object> nodeView(WemadeOwnershipNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (n == null) { m.put("missing", true); return m; }
        m.put("nodeKey", n.getNodeKey());
        m.put("name", n.getName());
        m.put("nameZh", n.getNameZh());
        m.put("nodeType", n.getNodeType());
        m.put("country", n.getCountry());
        m.put("stakePct", n.getStakePct());
        m.put("stakeNote", n.getStakeNote());
        m.put("roleDesc", n.getRoleDesc());
        m.put("grade", n.getGrade());
        m.put("sourceName", n.getSourceName());
        m.put("sourceUrl", n.getSourceUrl());
        m.put("asOfDate", n.getAsOfDate() == null ? null : n.getAsOfDate().toString());
        return m;
    }

    private Map<String, Object> edgeView(WemadeOwnershipEdge e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("parentKey", e.getParentKey());
        m.put("childKey", e.getChildKey());
        m.put("stakePct", e.getStakePct());
        m.put("note", e.getNote());
        m.put("grade", e.getGrade());
        m.put("sourceName", e.getSourceName());
        m.put("sourceUrl", e.getSourceUrl());
        m.put("asOfDate", e.getAsOfDate() == null ? null : e.getAsOfDate().toString());
        return m;
    }

    /** 9,200억원 인수자금 조달 구조 — 확인된 사실만, 등급/출처 표기. */
    private List<Map<String, Object>> funding() {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(fund("인수대금 총액", "9,200억원 (39.33%, 주당 68,910원)",
                "A", "DART 원문 rcpNo 20260630900888 「1주당 가액(원)」", null,
                "계약금 10% 920억원(2026-06-30) + 잔금 90% 8,280억원(2026-10-30 예정)"));
        out.add(fund("Nexpulse 부담", "Neosphere에 최소 3.102억 달러 증자 (약 4,188억원), 킹넷보다 먼저 납입",
                "A", "킹넷 9/12 CNINFO 공시", "http://www.cninfo.com.cn/new/disclosure/detail?stockCode=002517",
                "양측 자금은 NeoPulse의 위메이드 지분 인수대금으로 사용"));
        out.add(fund("Kingnet(Cypress) 부담", "Neosphere 지분 49% 취득 + 2.98억 달러 증자 (약 4,023억원)",
                "A", "킹넷 9/12 CNINFO 공시", "http://www.cninfo.com.cn/new/disclosure/detail?stockCode=002517",
                "중국 발개위·상무·외환 승인/신고 필요, 불확실성 명시"));
        out.add(fund("부족액 조달", "Nexpulse가 추가 조달 (공시 기재)",
                "A", "킹넷 9/12 CNINFO 공시", "http://www.cninfo.com.cn/new/disclosure/detail?stockCode=002517",
                "3.102억+2.98억=6.082억 달러(약 8,211억원) 대비 약 9,200억원: 차액 약 989억원은 Nexpulse 추가 조달분 (계산값 D)"));
        out.add(fund("Nexpulse 자금 출처", "미확인",
                "U", null, null,
                "Nexpulse 3.102억 달러 이상의 최종 자금 제공자는 공개 자료 없음. 추측 연결 금지."));
        out.add(fund("미르 IP 독점 라이선스", "거래 완료 후 Wemade→Neosphere 독점 라이선스 추진 → Cypress/킹넷 지정 주체에 재부여 (미체결)",
                "B", "킹넷 공시 + 복수 언론", "https://www.playforum.net/news/articleView.html?idxno=635337",
                "라이선스료 상한=기존 미르 IP 계약 대가 이하. 지역·기간·MG·로열티 미공개."));
        return out;
    }

    private Map<String, Object> fund(String label, String value, String grade,
                                    String sourceName, String sourceUrl, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        m.put("grade", grade);
        m.put("sourceName", sourceName);
        m.put("sourceUrl", sourceUrl);
        m.put("note", note);
        return m;
    }

    private Map<String, String> gradeLegend() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("A", "공식 1차 자료 (DART 원문·CNINFO 공시)");
        m.put("B", "복수 신뢰 매체");
        m.put("C", "단일 매체");
        m.put("D", "계산값 (산식 표기)");
        m.put("U", "미확인");
        return m;
    }
}
