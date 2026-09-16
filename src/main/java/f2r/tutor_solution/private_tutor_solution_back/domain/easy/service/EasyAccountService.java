package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyAccountRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyAccountResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyMappingRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyMappingResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyRuleRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyRuleResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountMapping;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyClassificationRule;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyRulePatternType;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountMappingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyClassificationRuleRepository;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy 계정 master + 매핑 + 자동분류 규칙.
 * 규칙 발상 참고: kimlawtech/korean-jangbu-for (Apache-2.0) — 코드 복사 없이 재구현.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyAccountService {

  private final EasyAccountRepository accountRepository;
  private final EasyAccountMappingRepository mappingRepository;
  private final EasyClassificationRuleRepository ruleRepository;

  /** 자동분류 결과 — 계정 + 확신도. */
  public record ClassificationMatch(EasyAccount account, double confidence) {
  }

  public List<EasyAccountResponse> listAccounts(String direction) {
    if (direction == null || direction.isBlank()) {
      return accountRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
          .map(EasyAccountResponse::from)
          .toList();
    }
    EasyAccountDirection parsed;
    try {
      parsed = EasyAccountDirection.valueOf(direction.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("계정 방향 값이 올바르지 않습니다.");
    }
    return accountRepository.findAllByActiveTrueAndDirectionOrderBySortOrderAsc(parsed).stream()
        .map(EasyAccountResponse::from)
        .toList();
  }

  @Transactional
  public EasyAccountResponse createAccount(EasyAccountRequest request) {
    if (request.code() == null || request.code().isBlank()) {
      throw new IllegalArgumentException("계정 코드를 입력해 주세요.");
    }
    if (request.name() == null || request.name().isBlank()) {
      throw new IllegalArgumentException("계정명을 입력해 주세요.");
    }
    EasyAccount account = EasyAccount.builder()
        .code(request.code().trim())
        .name(request.name().trim())
        .direction(parseAccountDirection(request.direction()))
        .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
        .active(request.active() == null || request.active())
        .build();
    return EasyAccountResponse.from(accountRepository.save(account));
  }

  @Transactional
  public EasyAccountResponse updateAccount(Long accountId, EasyAccountRequest request) {
    EasyAccount account = requireAccount(accountId);
    if (request.name() == null || request.name().isBlank()) {
      throw new IllegalArgumentException("계정명을 입력해 주세요.");
    }
    account.update(
        request.name().trim(),
        request.sortOrder() == null ? account.getSortOrder() : request.sortOrder(),
        request.active() == null || request.active());
    return EasyAccountResponse.from(account);
  }

  public List<EasyMappingResponse> listMappings() {
    return mappingRepository.findAllByOrderByMappingIdAsc().stream()
        .map(EasyMappingResponse::from)
        .toList();
  }

  /**
   * 매핑 등록 — Work 실데이터의 외부 코드를 내부 계정에 연결한다.
   * 표준 코드/명은 확정 전까지 비워 둘 수 있다.
   */
  @Transactional
  public EasyMappingResponse createMapping(EasyMappingRequest request) {
    if (request.externalCode() == null || request.externalCode().isBlank()) {
      throw new IllegalArgumentException("외부 코드를 입력해 주세요.");
    }
    EasyAccountMapping mapping = EasyAccountMapping.builder()
        .account(requireAccount(request.accountId()))
        .externalCode(request.externalCode().trim())
        .externalName(request.externalName())
        .standardCode(request.standardCode())
        .standardName(request.standardName())
        .note(request.note())
        .build();
    return EasyMappingResponse.from(mappingRepository.save(mapping));
  }

  @Transactional
  public void deleteMapping(Long mappingId) {
    EasyAccountMapping mapping = mappingRepository.findById(mappingId)
        .orElseThrow(() -> new IllegalArgumentException("매핑을 찾을 수 없습니다: " + mappingId));
    mappingRepository.delete(mapping);
  }

  public List<EasyRuleResponse> listRules() {
    return ruleRepository.findAllByActiveTrueOrderByPriorityAsc().stream()
        .map(EasyRuleResponse::from)
        .toList();
  }

  @Transactional
  public EasyRuleResponse createRule(EasyRuleRequest request) {
    EasyClassificationRule rule = EasyClassificationRule.builder()
        .patternType(parsePatternType(request.patternType()))
        .pattern(requirePattern(request.pattern()))
        .account(requireAccount(request.accountId()))
        .priority(request.priority() == null ? 100 : request.priority())
        .amountMin(request.amountMin())
        .amountMax(request.amountMax())
        .direction(parseTxnDirection(request.direction()))
        .active(request.active() == null || request.active())
        .build();
    return EasyRuleResponse.from(ruleRepository.save(rule));
  }

  @Transactional
  public EasyRuleResponse updateRule(Long ruleId, EasyRuleRequest request) {
    EasyClassificationRule rule = ruleRepository.findById(ruleId)
        .orElseThrow(() -> new IllegalArgumentException("규칙을 찾을 수 없습니다: " + ruleId));
    rule.update(
        requirePattern(request.pattern()),
        request.priority() == null ? rule.getPriority() : request.priority(),
        request.amountMin(),
        request.amountMax(),
        parseTxnDirection(request.direction()),
        request.active() == null || request.active());
    return EasyRuleResponse.from(rule);
  }

  @Transactional
  public void deleteRule(Long ruleId) {
    EasyClassificationRule rule = ruleRepository.findById(ruleId)
        .orElseThrow(() -> new IllegalArgumentException("규칙을 찾을 수 없습니다: " + ruleId));
    ruleRepository.delete(rule);
  }

  /**
   * 자동분류 — 우선순위 순으로 패턴 매칭. 잘못된 정규식 룰은 건너뛴다.
   * 매칭 없으면 empty (호출 측이 미분류로 둔다).
   */
  public Optional<ClassificationMatch> classify(
      String counterparty, String description, long amount, EasyTxnDirection direction) {
    String target = counterparty == null ? "" : counterparty.trim();
    String desc = description == null ? "" : description.trim();
    for (EasyClassificationRule rule : ruleRepository.findAllByActiveTrueOrderByPriorityAsc()) {
      if (rule.getAmountMin() != null && amount < rule.getAmountMin()) {
        continue;
      }
      if (rule.getAmountMax() != null && amount > rule.getAmountMax()) {
        continue;
      }
      if (rule.getDirection() != null && rule.getDirection() != direction) {
        continue;
      }
      switch (rule.getPatternType()) {
        case COUNTERPARTY_EXACT -> {
          if (!target.isEmpty() && target.equals(rule.getPattern())) {
            return Optional.of(new ClassificationMatch(rule.getAccount(), 1.0));
          }
        }
        case COUNTERPARTY_REGEX -> {
          if (matches(rule.getPattern(), target)) {
            return Optional.of(new ClassificationMatch(rule.getAccount(), 0.9));
          }
        }
        case DESCRIPTION_REGEX -> {
          if (matches(rule.getPattern(), desc)) {
            return Optional.of(new ClassificationMatch(rule.getAccount(), 0.8));
          }
        }
      }
    }
    return Optional.empty();
  }

  private boolean matches(String regex, String text) {
    if (text.isEmpty()) {
      return false;
    }
    try {
      return Pattern.compile(regex).matcher(text).find();
    } catch (PatternSyntaxException e) {
      return false;
    }
  }

  private EasyAccount requireAccount(Long accountId) {
    if (accountId == null) {
      throw new IllegalArgumentException("계정을 선택해 주세요.");
    }
    return accountRepository.findById(accountId)
        .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다: " + accountId));
  }

  private EasyAccountDirection parseAccountDirection(String direction) {
    if (direction == null || direction.isBlank()) {
      throw new IllegalArgumentException("계정 방향을 선택해 주세요.");
    }
    try {
      return EasyAccountDirection.valueOf(direction.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("계정 방향 값이 올바르지 않습니다.");
    }
  }

  private EasyRulePatternType parsePatternType(String patternType) {
    if (patternType == null || patternType.isBlank()) {
      throw new IllegalArgumentException("패턴 종류를 선택해 주세요.");
    }
    try {
      return EasyRulePatternType.valueOf(patternType.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("패턴 종류 값이 올바르지 않습니다.");
    }
  }

  private EasyTxnDirection parseTxnDirection(String direction) {
    if (direction == null || direction.isBlank()) {
      return null;
    }
    try {
      return EasyTxnDirection.valueOf(direction.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("수입/지출 값이 올바르지 않습니다.");
    }
  }

  private String requirePattern(String pattern) {
    if (pattern == null || pattern.isBlank()) {
      throw new IllegalArgumentException("패턴을 입력해 주세요.");
    }
    return pattern;
  }
}
