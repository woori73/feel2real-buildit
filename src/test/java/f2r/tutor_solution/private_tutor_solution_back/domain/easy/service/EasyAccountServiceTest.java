package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyAccountRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyMappingRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyRuleRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyClassificationRule;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyRulePatternType;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountMappingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyClassificationRuleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAccountService.ClassificationMatch;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 계정 master + 매핑 + 자동분류 규칙.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EasyAccountService 계정·규칙 테스트")
class EasyAccountServiceTest {

  @Mock EasyAccountRepository accountRepository;
  @Mock EasyAccountMappingRepository mappingRepository;
  @Mock EasyClassificationRuleRepository ruleRepository;

  private EasyAccountService service;
  private EasyAccount telecom;

  @BeforeEach
  void setUp() {
    service = new EasyAccountService(accountRepository, mappingRepository, ruleRepository);
    telecom = EasyAccount.builder().code("EXPENSE_TELECOM").name("통신비")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(1).active(true).build();
  }

  private EasyClassificationRule rule(EasyRulePatternType type, String pattern,
      EasyAccount account, int priority, Long min, Long max, EasyTxnDirection direction) {
    return EasyClassificationRule.builder()
        .patternType(type).pattern(pattern).account(account).priority(priority)
        .amountMin(min).amountMax(max).direction(direction).active(true).build();
  }

  @Test
  @DisplayName("정확일치 우선 + 정규식 + 금액구간 + 방향 조건")
  void classify_priorityAndConditions() {
    EasyAccount meals = EasyAccount.builder().code("EXPENSE_MEALS").name("복리후생비")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(2).active(true).build();
    when(ruleRepository.findAllByActiveTrueOrderByPriorityAsc()).thenReturn(List.of(
        rule(EasyRulePatternType.COUNTERPARTY_EXACT, "KT", telecom, 5, null, null, null),
        rule(EasyRulePatternType.COUNTERPARTY_REGEX, "(KT|SKT)", telecom, 10, null, null, null),
        rule(EasyRulePatternType.DESCRIPTION_REGEX, "(회식|점심)", meals, 40, null, 50000L, null)));

    Optional<ClassificationMatch> exact =
        service.classify("KT", "요금", 30000L, EasyTxnDirection.EXPENSE);
    assertThat(exact).isPresent();
    assertThat(exact.get().account().getName()).isEqualTo("통신비");
    assertThat(exact.get().confidence()).isEqualTo(1.0);

    Optional<ClassificationMatch> regex =
        service.classify("SKT텔레콤", "요금", 30000L, EasyTxnDirection.EXPENSE);
    assertThat(regex).isPresent();
    assertThat(regex.get().confidence()).isEqualTo(0.9);

    // 금액 상한 초과 → 다음 규칙도 없으면 미분류
    assertThat(service.classify("식당", "회식", 100000L, EasyTxnDirection.EXPENSE)).isEmpty();
    Optional<ClassificationMatch> desc =
        service.classify("식당", "회식", 30000L, EasyTxnDirection.EXPENSE);
    assertThat(desc).isPresent();
    assertThat(desc.get().confidence()).isEqualTo(0.8);
  }

  @Test
  @DisplayName("잘못된 정규식 룰은 건너뛴다")
  void classify_invalidRegexSkipped() {
    when(ruleRepository.findAllByActiveTrueOrderByPriorityAsc()).thenReturn(List.of(
        rule(EasyRulePatternType.COUNTERPARTY_REGEX, "([", telecom, 1, null, null, null)));

    assertThat(service.classify("KT", "", 1000L, EasyTxnDirection.EXPENSE)).isEmpty();
  }

  @Test
  @DisplayName("계정 등록 검증 + 매핑 등록 검증")
  void create_validations() {
    when(accountRepository.save(any(EasyAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var created = service.createAccount(
        new EasyAccountRequest("EXPENSE_TEST", "테스트비", "EXPENSE", 99, true));
    assertThat(created.code()).isEqualTo("EXPENSE_TEST");

    assertThatThrownBy(() -> service.createAccount(
        new EasyAccountRequest("", "이름", "EXPENSE", 0, true)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.createAccount(
        new EasyAccountRequest("X", "이름", "WRONG", 0, true)))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> service.createMapping(
        new EasyMappingRequest(1L, "  ", "외부", null, null, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("규칙 등록 검증")
  void rule_validations() {
    assertThatThrownBy(() -> service.createRule(
        new EasyRuleRequest("WRONG_TYPE", "KT", 7L, 10, null, null, null, true)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.createRule(
        new EasyRuleRequest("COUNTERPARTY_REGEX", "  ", 7L, 10, null, null, null, true)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
