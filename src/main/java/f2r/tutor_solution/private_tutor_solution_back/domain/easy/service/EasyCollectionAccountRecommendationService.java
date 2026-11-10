package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRawRecord;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRawRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.OpenAiLunaLlmTransport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 후 미분류로 저장된 거래의 계정과목 "추천"만 만든다(저장·분류 확정 없음).
 *
 * <p>분류 우선순위는 이미 저장 단계에서 적용된다: 원본 계정 매핑 → 과거 장부(이지샵 이관분) →
 * 명시 규칙. 그래도 미분류로 남은 거래에만 GPT 루나 6.0 추천을 붙인다. 추천은 활성 계정 목록 중에서만
 * 고르게 하고, 목록에 없는 이름은 버린다. 개인으로 보이는 거래처 이름·계좌번호는 보내지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyCollectionAccountRecommendationService {

  static final int MAX_ITEMS = 20;
  private static final Pattern PERSON_NAME = Pattern.compile("^[가-힣]{2,4}$");
  private static final String SYSTEM = """
      당신은 한국 학원 사업자의 장부 계정과목 추천기다. 확정하거나 저장하지 않고 추천만 한다.
      입력된 거래마다 계정 목록 중 하나의 이름을 그대로 고르거나, 판단할 수 없으면 null을 준다.
      카드대금 출금·카드사 정산입금·대출 입금은 손익 계정으로 추천하지 않는다.
      JSON 배열 하나만 반환한다: [{"txnId":숫자,"account":"계정명 또는 null","reason":"짧은 근거"}]
      """;

  private final EasyCollectionRunRepository runRepository;
  private final EasyCollectionRawRecordRepository rawRepository;
  private final EasyTransactionRepository txnRepository;
  private final EasyAccountRepository accountRepository;
  private final EasyImportService importService;
  private final OpenAiLunaLlmTransport luna;
  private final ObjectMapper mapper = new ObjectMapper();

  /** 추천 1건. accountId가 null이면 추천 없음. */
  public record Recommendation(Long txnId, String counterparty, String description, long amount,
      String direction, Long accountId, String accountName, String reason) {}

  public record Result(boolean available, String message, List<Recommendation> items) {}

  public Result recommendForRun(Long businessId, Long runId) {
    EasyCollectionRun run = runRepository.findById(runId)
        .orElseThrow(() -> new IllegalArgumentException("수집 실행을 찾을 수 없습니다."));
    if (!run.getBusiness().getBusinessId().equals(businessId)) {
      throw new AccessDeniedException("다른 사업체의 수집 실행입니다.");
    }
    List<EasyTransaction> unclassified = new ArrayList<>();
    for (EasyCollectionRawRecord raw : rawRepository.findAllByRun_RunIdOrderByRawIdAsc(runId)) {
      Optional<Long> txnId = importService.createdTxnIdOfRow(raw.getImportRowId());
      txnId.flatMap(id -> txnRepository.findByTxnIdAndBusiness_BusinessId(id, businessId))
          .filter(txn -> txn.getAccount() == null)
          .ifPresent(unclassified::add);
      if (unclassified.size() >= MAX_ITEMS) {
        break;
      }
    }
    if (unclassified.isEmpty()) {
      return new Result(true, "미분류 거래가 없습니다.", List.of());
    }
    if (!luna.available()) {
      return new Result(false, "AI 추천이 꺼져 있습니다(OPENAI_API_KEY 미설정).",
          unclassified.stream().map(txn -> toItem(txn, null, null)).toList());
    }
    List<EasyAccount> accounts = accountRepository.findAllByActiveTrueOrderBySortOrderAsc();
    NewStartLlmCompletion completion = luna.complete(SYSTEM, prompt(unclassified, accounts), 4000);
    if (!completion.ok()) {
      return new Result(false, "AI 추천을 받지 못했습니다. 잠시 후 다시 시도하세요.",
          unclassified.stream().map(txn -> toItem(txn, null, null)).toList());
    }
    Map<Long, JsonNode> byTxn = parse(completion.text());
    List<Recommendation> items = new ArrayList<>();
    for (EasyTransaction txn : unclassified) {
      JsonNode node = byTxn.get(txn.getTxnId());
      String name = node == null || node.path("account").isNull()
          ? null : node.path("account").asText(null);
      EasyAccount match = name == null ? null : accounts.stream()
          .filter(account -> account.getName().equals(name.trim()))
          .findFirst().orElse(null);
      String reason = node == null ? null : node.path("reason").asText(null);
      items.add(toItem(txn, match, match == null ? null : reason));
    }
    return new Result(true, "추천은 참고용입니다. 반영은 검토함에서 직접 하세요.", items);
  }

  String prompt(List<EasyTransaction> txns, List<EasyAccount> accounts) {
    StringBuilder sb = new StringBuilder("계정 목록: ");
    sb.append(String.join(", ", accounts.stream().map(EasyAccount::getName).toList()));
    sb.append("\n거래:\n");
    for (EasyTransaction txn : txns) {
      sb.append("- txnId=").append(txn.getTxnId())
          .append(" 방향=").append(txn.getDirection())
          .append(" 금액=").append(txn.getAmount())
          .append(" 거래처=").append(safeCounterparty(txn.getCounterparty()))
          .append(" 적요=").append(clip(txn.getDescription()))
          .append(" 결제수단=").append(clip(txn.getPayMethod()))
          .append('\n');
    }
    return sb.toString();
  }

  /** 개인 이름처럼 보이면 보내지 않는다(학부모·강사 개인정보 보호). */
  static String safeCounterparty(String counterparty) {
    if (counterparty == null || counterparty.isBlank()) {
      return "(없음)";
    }
    String trimmed = counterparty.trim();
    if (PERSON_NAME.matcher(trimmed).matches()) {
      return "(개인)";
    }
    return clip(trimmed);
  }

  private Map<Long, JsonNode> parse(String text) {
    Map<Long, JsonNode> out = new LinkedHashMap<>();
    try {
      String json = text == null ? "" : text.strip()
          .replace("```json", "").replace("```", "").strip();
      JsonNode root = mapper.readTree(json);
      if (root != null && root.isArray()) {
        root.forEach(node -> {
          if (node.path("txnId").canConvertToLong()) {
            out.put(node.path("txnId").asLong(), node);
          }
        });
      }
    } catch (Exception ignored) {
      // 형식이 깨진 응답은 추천 없음으로 처리한다.
    }
    return out;
  }

  private static Recommendation toItem(EasyTransaction txn, EasyAccount account, String reason) {
    return new Recommendation(txn.getTxnId(), txn.getCounterparty(), txn.getDescription(),
        txn.getAmount(), txn.getDirection().name(),
        account == null ? null : account.getAccountId(),
        account == null ? null : account.getName(), reason);
  }

  private static String clip(String value) {
    if (value == null) {
      return "";
    }
    return value.length() <= 60 ? value : value.substring(0, 60);
  }
}
