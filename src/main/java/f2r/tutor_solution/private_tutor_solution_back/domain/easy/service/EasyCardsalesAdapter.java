package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 카드사 승인/매입/입금 내역 수집 어댑터 스켈레톤 (PHASE 2 준비②).
 * <p>
 * 실제 카드사 API 연동은 하지 않는다(자격증명 저장 금지, 네트워크 호출 금지).
 * 대신 합성 픽스처(synthetic fixtures)로 approval/purchase/deposit 세 종류를 생성해
 * 공통 수집 코어({@link EasyCollectionIngestService#ingestParsedRows})를 검증한다.
 * </p>
 * <ul>
 *   <li>connection_ref: {@link EasyCollectionConnection#connectionRef}에 opaque reference로만 보관</li>
 *   <li>external_id: 승인번호/매입번호/입금번호 등 제공자 고유 ID → 1차 dedupe 키(EXT)</li>
 *   <li>provenance: 원본 응답 전체를 {@link CollectionParsedRow#rawJson()}에 보존</li>
 *   <li>auto-confirm 없음: 수집만 하고 confirm은 관리자 권한의 별도 경계</li>
 *   <li>direct write 없음: easy_transactions 직접 INSERT 금지</li>
 * </ul>
 */
@Service
public class EasyCardsalesAdapter implements EasyCollectionAdapter {

    public static final String ADAPTER_CODE = "CARDSales";
    public static final String ADAPTER_VERSION = "v1-skeleton";
    private static final List<String> CONFIG_KEYS = List.of(
        "clientId", "merchantId", "connectedAccountId", "apiEndpointBaseUrl");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public String adapterCode() {
        return ADAPTER_CODE;
    }

    @Override
    public String adapterVersion() {
        return ADAPTER_VERSION;
    }

    @Override
    public List<String> configKeys() {
        return CONFIG_KEYS;
    }

    @Override
    public List<CollectionParsedRow> fetch(String connectionRef, LocalDateTime since, int limit) {
        if (connectionRef == null || connectionRef.isBlank()) {
            throw new IllegalArgumentException("connectionRef가 비어 있습니다. 연결 설정을 확인하세요.");
        }
        if (limit <= 0 || limit > 5000) {
            throw new IllegalArgumentException("limit은 1~5000 사이여야 합니다.");
        }

        // 스켈레톤: 실제 API 호출 대신 합성 픽스처 생성
        // connectionRef는 provider connectedId/동의ID 등으로 해석되나,
        // 이 어댑터에서는 검증용 토큰으로만 사용하고 네트워크 호출은 하지 않는다.
        List<CollectionParsedRow> all = buildSyntheticFixtures(connectionRef);
        return since == null ? all : all.stream()
            .filter(r -> r.occurredOn() != null && r.occurredOn().atStartOfDay().isAfter(since))
            .limit(limit)
            .toList();
    }

    @Override
    public void validateConnection(String connectionRef) {
        if (connectionRef == null || connectionRef.isBlank()) {
            throw new IllegalArgumentException("connectionRef가 비어 있습니다.");
        }
        // 스켈레톤: 실제 네트워크 ping 대신 형식만 검증
        // 운영 구현 시: 카드사 API /health 또는 최소 권한 엔드포인트 HEAD 호출
        if (!connectionRef.startsWith("cardsales-")) {
            throw new IllegalStateException("알 수 없는 connectionRef 형식: " + connectionRef);
        }
    }

    /**
     * 합성 픽스처 생성 — approval(승인), purchase(매입), deposit(입금) 세 종류.
     * 각 레코드는 provider 고유 externalId를 가져 1차 dedupe(EXT 키) 검증 가능.
     * rawJson에 원본 전체를 보존해 provenance 체인 유지.
     */
    private List<CollectionParsedRow> buildSyntheticFixtures(String connectionRef) {
        List<CollectionParsedRow> rows = new ArrayList<>();

        // 1) 승인 내역 (approval) — 카드 승인 실시간 내역
        rows.add(buildRow(connectionRef, "approval",
            "APPR-20260901-001", "2026-09-01", "EXPENSE", 45000L,
            "스타벅스 강남점", "법인카드 결제", "CARD",
            40909L, 4091L, "CARD_RECEIPT", "법인카드-1234",
            Map.of("approvalNo", "APPR-20260901-001",
                   "cardType", "CORPORATE",
                   "merchantCategory", "5812",
                   "installmentMonths", "0")));

        rows.add(buildRow(connectionRef, "approval",
            "APPR-20260902-002", "2026-09-02", "EXPENSE", 120000L,
            "교보문고 광화문", "교재 구매", "CARD",
            109091L, 10909L, "CARD_RECEIPT", "법인카드-1234",
            Map.of("approvalNo", "APPR-20260902-002",
                   "cardType", "CORPORATE",
                   "merchantCategory", "5942",
                   "installmentMonths", "0")));

        // 2) 매입 내역 (purchase) — 카드사 정산 기준 매입 확정 건
        rows.add(buildRow(connectionRef, "purchase",
            "PUR-20260903-001", "2026-09-03", "EXPENSE", 330000L,
            "AWS Korea", "클라우드 이용료 8월분", "CARD",
            300000L, 30000L, "TAX_INVOICE", "법인카드-1234",
            Map.of("purchaseNo", "PUR-20260903-001",
                   "cardType", "CORPORATE",
                   "merchantCategory", "7372",
                   "settlementDate", "2026-09-05",
                   "vatType", "TAXABLE")));

        rows.add(buildRow(connectionRef, "purchase",
            "PUR-20260904-002", "2026-09-04", "EXPENSE", 550000L,
            "네이버 클라우드", "워크플레이스 이용료", "CARD",
            500000L, 50000L, "TAX_INVOICE", "법인카드-1234",
            Map.of("purchaseNo", "PUR-20260904-002",
                   "cardType", "CORPORATE",
                   "merchantCategory", "7372",
                   "settlementDate", "2026-09-06",
                   "vatType", "TAXABLE")));

        // 3) 입금 내역 (deposit) — 카드사 가맹점 정산 입금
        rows.add(buildRow(connectionRef, "deposit",
            "DEP-20260905-001", "2026-09-05", "INCOME", 2500000L,
            "카드사 정산", "9월 1차 정산 입금", "ACCOUNT_TRANSFER",
            null, null, "BANK_STATEMENT", "정산계좌-9876",
            Map.of("depositNo", "DEP-20260905-001",
                   "settlementPeriod", "2026-09-01~2026-09-03",
                   "fee", "50000",
                   "netAmount", "2450000")));

        rows.add(buildRow(connectionRef, "deposit",
            "DEP-20260906-002", "2026-09-06", "INCOME", 1800000L,
            "카드사 정산", "9월 2차 정산 입금", "ACCOUNT_TRANSFER",
            null, null, "BANK_STATEMENT", "정산계좌-9876",
            Map.of("depositNo", "DEP-20260906-002",
                   "settlementPeriod", "2026-09-04~2026-09-05",
                   "fee", "36000",
                   "netAmount", "1764000")));

        return rows;
    }

    private CollectionParsedRow buildRow(String connectionRef, String recordType,
        String externalId, String occurredOn, String direction, Long amount,
        String counterparty, String description, String payMethod,
        Long supplyAmount, Long vatAmount, String evidenceType, String externalAccount,
        Map<String, Object> extraFields) {

        Map<String, String> original = new LinkedHashMap<>();
        original.put("recordType", recordType);
        original.put("connectionRef", connectionRef);
        original.put("externalId", externalId);
        original.put("date", occurredOn);
        original.put("direction", direction);
        original.put("amount", String.valueOf(amount));
        original.put("counterparty", counterparty);
        original.put("description", description);
        original.put("payMethod", payMethod);
        original.put("supplyAmount", supplyAmount == null ? "" : String.valueOf(supplyAmount));
        original.put("vat", vatAmount == null ? "" : String.valueOf(vatAmount));
        original.put("evidenceType", evidenceType);
        original.put("account", externalAccount);
        original.putAll(extraFields.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, e -> String.valueOf(e.getValue()))));

        String rawJson;
        try {
            rawJson = objectMapper.writeValueAsString(original);
        } catch (IOException e) {
            rawJson = original.toString();
        }

        LocalDate date = LocalDate.parse(occurredOn, DATE_FMT);
        String error = null;
        if (amount == null || amount < 0) {
            error = "금액이 유효하지 않습니다: " + amount;
        }

        return new CollectionParsedRow(
            0, // rowNo는 ingestParsedRows에서 1부터 부여
            Map.copyOf(original),
            rawJson,
            date,
            direction,
            amount,
            counterparty,
            description,
            payMethod,
            supplyAmount,
            vatAmount,
            evidenceType,
            externalAccount,
            externalId,
            error);
    }
}