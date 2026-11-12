package f2r.tutor_solution.private_tutor_solution_back.domain.easy.taxreview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TaxDoctor MCP 클라이언트.
 *
 * MCP 장애가 발생해도 EASY 장부 기능은 정상 작동해야 한다.
 * 이 클라이언트의 실패가 거래 저장을 rollback해서는 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaxDoctorMcpClient {

    @Value("${taxdoctor.mcp.endpoint:https://mcp.taxdoctorai.com/mcp}")
    private String mcpEndpoint;

    @Value("${taxdoctor.mcp.timeout:10000}")
    private int timeoutMs;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * MCP 연결 테스트.
     * @return 연결 성공 여부
     */
    public boolean testConnection() {
        try {
            // MCP는 JSON-RPC over HTTP. initialize 메서드로 연결 테스트
            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", UUID.randomUUID().toString());
            request.put("method", "initialize");
            Map<String, Object> params = new HashMap<>();
            params.put("protocolVersion", "2024-11-05");
            Map<String, Object> clientInfo = new HashMap<>();
            clientInfo.put("name", "easy-ledger");
            clientInfo.put("version", "1.0.0");
            params.put("clientInfo", clientInfo);
            request.put("params", params);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    mcpEndpoint, entity, String.class);

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("TaxDoctor MCP connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 세무 검토 요청.
     * 실패 시 null을 반환하며, 호출자가 fallback 처리한다.
     * 절대 예외를 던져서 EASY 트랜잭션을 rollback시키지 않는다.
     */
    public TaxReviewResult reviewTransaction(TaxReviewRequest request) {
        try {
            Map<String, Object> mcpRequest = buildMcpRequest(request);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(mcpRequest, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    mcpEndpoint, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("TaxDoctor MCP returned non-2xx: {}", response.getStatusCode());
                return null;
            }

            return parseResponse(response.getBody(), request);

        } catch (Exception e) {
            // MCP 장애는 EASY 기능에 영향을 주지 않는다
            log.warn("TaxDoctor MCP review failed for txn {}: {}",
                    request.getEasyTxnId(), e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildMcpRequest(TaxReviewRequest request) {
        Map<String, Object> rpc = new HashMap<>();
        rpc.put("jsonrpc", "2.0");
        rpc.put("id", UUID.randomUUID().toString());
        rpc.put("method", "tools/call");

        Map<String, Object> params = new HashMap<>();
        params.put("name", "review_transaction");

        // 개인정보 최소 전송: 필요한 정보만
        Map<String, Object> args = new HashMap<>();
        args.put("occurred_on", request.getOccurredOn());
        args.put("amount", request.getAmount());
        args.put("memo", request.getMemo());
        args.put("business_type", request.getBusinessType());
        args.put("current_account", request.getCurrentAccount());
        args.put("current_tax_classification", request.getCurrentTaxClassification());
        args.put("evidence_type", request.getEvidenceType());
        args.put("review_reason", request.getReviewReason());
        params.put("arguments", args);

        rpc.put("params", params);
        return rpc;
    }

    private TaxReviewResult parseResponse(String body, TaxReviewRequest request) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode result = root.path("result");
            JsonNode content = result.path("content");

            // MCP 응답 구조에 따라 파싱 (실제 응답에 맞게 조정 필요)
            // 기본값으로 안전하게 처리
            return TaxReviewResult.builder()
                    .taxReviewStatus(getTextOrDefault(content, "tax_review_status", "UNCERTAIN"))
                    .recommendedAccount(getTextOrNull(content, "recommended_account"))
                    .deductibility(getTextOrNull(content, "deductibility"))
                    .vatDeductible(getBoolOrNull(content, "vat_deductible"))
                    .evidenceRequirement(getTextOrNull(content, "evidence_requirement"))
                    .businessRelevance(getTextOrNull(content, "business_relevance"))
                    .depreciationCandidate(getBoolOrNull(content, "depreciation_candidate"))
                    .riskLevel(getTextOrDefault(content, "risk_level", "UNCERTAIN"))
                    .reason(getTextOrNull(content, "reason"))
                    .legalBasis(getTextOrNull(content, "legal_basis"))
                    .source(getTextOrNull(content, "source"))
                    .reviewedAt(LocalDateTime.now())
                    .rawResponse(body)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse TaxDoctor MCP response: {}", e.getMessage());
            return null;
        }
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode f = node.path(field);
        return f.isMissingNode() || f.isNull() ? null : f.asText();
    }

    private String getTextOrDefault(JsonNode node, String field, String def) {
        String v = getTextOrNull(node, field);
        return v != null ? v : def;
    }

    private Boolean getBoolOrNull(JsonNode node, String field) {
        JsonNode f = node.path(field);
        return f.isMissingNode() || f.isNull() ? null : f.asBoolean();
    }
}
