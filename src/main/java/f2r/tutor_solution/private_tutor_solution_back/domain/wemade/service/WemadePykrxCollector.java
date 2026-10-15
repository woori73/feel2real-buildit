package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeInvestorFlow;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeRawPrice;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeInvestorFlowRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeRawPriceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * pykrx 기반 일봉 + 투자자별 수급 수집기.
 *
 * - pykrx는 Python 라이브러리이므로 classpath의 pykrx_collect.py를 임시 파일로 복사해
 *   python3 서브프로세스로 실행하고 JSON 결과를 파싱한다.
 * - OHLCV는 KRX 로그인 없이 동작한다. 투자자별 수급은 KRX_ID/KRX_PW 없이 빈 결과를
 *   반환하므로, 이 경우 조용히 PENDING으로 기록한다 (네이버 bot 우회를 시도하지 않는다).
 * - 동일 데이터 재수집 시 contentHash dedupe로 중복 저장하지 않는다.
 * - 서버에 python3/pykrx가 없으면 명확한 에러 메시지를 run 로그에 남긴다.
 */
@Slf4j
@Component
public class WemadePykrxCollector {

    private static final String SCRIPT_PATH = "wemade/pykrx_collect.py";
    private static final DateTimeFormatter D8 = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WemadeRawPriceRepository priceRepository;
    private final WemadeInvestorFlowRepository flowRepository;
    private final ObjectMapper objectMapper;

    public WemadePykrxCollector(WemadeRawPriceRepository priceRepository,
                                WemadeInvestorFlowRepository flowRepository,
                                ObjectMapper objectMapper) {
        this.priceRepository = priceRepository;
        this.flowRepository = flowRepository;
        this.objectMapper = objectMapper;
    }

    public record CollectResult(int fetched, int accepted, int duplicate, String note) {}

    /**
     * 일봉 수집. symbols 예: ["112040", "101730"].
     */
    public CollectResult collectPrices(List<String> symbols, LocalDate from, LocalDate to) {
        JsonNode out;
        try {
            out = runScript("ohlcv", symbols, from, to);
        } catch (Exception e) {
            log.warn("WEMADE pykrx ohlcv failed: {} (fallback: NAVER_FCHART)", e.getMessage());
            return new CollectResult(0, 0, 0,
                    "pykrx unavailable: " + e.getMessage() + " — fallback NAVER_FCHART");
        }
        int fetched = 0, accepted = 0, duplicate = 0;
        List<String> scriptErrors = new ArrayList<>();
        for (JsonNode r : out.path("rows")) {
            fetched++;
            String symbol = r.path("symbol").asText();
            LocalDate date = LocalDate.parse(r.path("date").asText());
            String hash = WemadeCollectionService.sha256(
                    "PYKRX|" + symbol + "|" + date + "|" + r.path("close").asLong());
            if (priceRepository.existsByContentHash(hash)) { duplicate++; continue; }
            WemadeRawPrice.PriceId id = new WemadeRawPrice.PriceId(symbol, date);
            WemadeRawPrice row = priceRepository.findById(id).orElse(null);
            if (row == null) {
                row = new WemadeRawPrice();
                row.setSymbol(symbol);
                row.setTradeDate(date);
            }
            row.setOpenPrice(r.path("open").asInt());
            row.setHighPrice(r.path("high").asInt());
            row.setLowPrice(r.path("low").asInt());
            row.setClosePrice(r.path("close").asInt());
            row.setVolume(r.path("volume").asLong());
            row.setSource("PYKRX");
            row.setContentHash(hash);
            row.setCollectedAt(LocalDateTime.now());
            priceRepository.save(row);
            accepted++;
        }
        for (JsonNode e : out.path("errors")) scriptErrors.add(e.toString());
        String note = scriptErrors.isEmpty() ? "source=PYKRX"
                : "source=PYKRX scriptErrors=" + String.join(";", scriptErrors);
        return new CollectResult(fetched, accepted, duplicate, note);
    }

    /**
     * 투자자별 수급 수집.
     * KRX 로그인 없이는 pykrx가 빈 결과를 반환한다. 이 경우 accepted=0으로 두고
     * PENDING 사유를 note에 남긴다. 네이버 bot 우회는 시도하지 않는다.
     * 대체 경로: KIS Open API (NEEDS_OWNER), KRX CSV 다운로드.
     */
    public CollectResult collectFlows(List<String> symbols, LocalDate from, LocalDate to) {
        JsonNode out;
        try {
            out = runScript("flows", symbols, from, to);
        } catch (Exception e) {
            log.warn("WEMADE pykrx flows failed: {}", e.getMessage());
            return new CollectResult(0, 0, 0,
                    "PENDING: pykrx unavailable (" + e.getMessage() + "); "
                    + "fallback=KIS Open API (NEEDS_OWNER) or KRX CSV download");
        }
        List<String> scriptErrors = new ArrayList<>();
        for (JsonNode e : out.path("errors")) scriptErrors.add(e.toString());
        // 현재 pykrx는 일별 종목별 투자자 데이터를 로그인 없이 제공하지 않으므로
        // rows는 비어 있고, 아래는 향후 KRX 로그인/KIS 연동 시 저장 로직이다.
        int fetched = 0, accepted = 0, duplicate = 0;
        for (JsonNode r : out.path("rows")) {
            fetched++;
            String investorType = r.path("investorType").asText(); // FOREIGN/INSTITUTION/RETAIL
            LocalDate date = LocalDate.parse(r.path("date").asText());
            String hash = WemadeCollectionService.sha256(
                    "PYKRX_FLOW|" + date + "|" + investorType + "|" + r.path("netBuyAmount").asLong());
            if (flowRepository.existsByContentHash(hash)) { duplicate++; continue; }
            WemadeInvestorFlow.FlowId id =
                    new WemadeInvestorFlow.FlowId(date, investorType);
            WemadeInvestorFlow row = flowRepository.findById(id).orElse(null);
            if (row == null) {
                row = new WemadeInvestorFlow();
                row.setTradeDate(date);
                row.setInvestorType(investorType);
            }
            row.setNetBuyAmount(r.path("netBuyAmount").asLong());
            if (!r.path("buyAmount").isMissingNode()) row.setBuyAmount(r.path("buyAmount").asLong());
            if (!r.path("sellAmount").isMissingNode()) row.setSellAmount(r.path("sellAmount").asLong());
            row.setSource("PYKRX");
            row.setContentHash(hash);
            row.setCollectedAt(LocalDateTime.now());
            flowRepository.save(row);
            accepted++;
        }
        String note = accepted > 0 ? "source=PYKRX"
                : "PENDING: investor flows not available without KRX login; "
                + "fallback=KIS Open API (NEEDS_OWNER) or KRX CSV download"
                + (scriptErrors.isEmpty() ? "" : " | " + String.join(";", scriptErrors));
        return new CollectResult(fetched, accepted, duplicate, note);
    }

    // ---- subprocess ----

    private JsonNode runScript(String mode, List<String> symbols, LocalDate from, LocalDate to)
            throws Exception {
        Path script = extractScript();
        List<String> cmd = new ArrayList<>(List.of(
                "python3", script.toAbsolutePath().toString(),
                "--mode", mode,
                "--symbols", String.join(",", symbols),
                "--from", from.format(D8),
                "--to", to.format(D8)));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process p = pb.start();
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean done = p.waitFor(300, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
            throw new IllegalStateException("pykrx script timeout (300s)");
        }
        if (p.exitValue() != 0) {
            throw new IllegalStateException("pykrx script exit=" + p.exitValue()
                    + " stderr=" + stderr.substring(0, Math.min(300, stderr.length())));
        }
        return objectMapper.readTree(stdout);
    }

    /** classpath 리소스를 임시 파일로 복사 (jar 패키징 대응). */
    private Path extractScript() throws Exception {
        Path tmp = Files.createTempFile("pykrx_collect", ".py");
        try (InputStream in = new ClassPathResource(SCRIPT_PATH).getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        tmp.toFile().deleteOnExit();
        return tmp;
    }
}
