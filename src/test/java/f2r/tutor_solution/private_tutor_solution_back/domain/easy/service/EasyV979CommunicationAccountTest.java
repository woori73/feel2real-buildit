package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountMapping;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyClassificationRule;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyRulePatternType;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountMappingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyClassificationRuleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportBatchRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportRowRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * V979: 통신비 계정 + 추가 Golden 매핑 3건 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:easyv979;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DisplayName("Easy V979 통신비 계정 및 매핑 테스트")
class EasyV979CommunicationAccountTest {

  /** V979 신규 매핑: 외부 코드 -> 내부 계정 코드. */
  private static final Map<String, String> V979_MAPPINGS = Map.ofEntries(
      Map.entry("전화/휴대폰/인터넷", "EXPENSE_COMMUNICATION"),
      Map.entry("서비스 이용료", "EXPENSE_FEE"),
      Map.entry("거래처 선물", "EXPENSE_ENTERTAIN"));

  /** V978 기존 매핑 복사본 (private access 회피). */
  private static final Map<String, String> V978_MAPPINGS = Map.ofEntries(
      Map.entry("임차료(월세)", "EXPENSE_RENT"),
      Map.entry("세금(면허세,사업소세등)", "EXPENSE_TAX"),
      Map.entry("건물관리비", "EXPENSE_ADMIN"),
      Map.entry("교재/도서", "EXPENSE_PRINT"),
      Map.entry("잡화/소모품", "EXPENSE_SUPPLIES"),
      Map.entry("일회용품", "EXPENSE_SUPPLIES"),
      Map.entry("기타수수료", "EXPENSE_FEE"),
      Map.entry("접대 식대", "EXPENSE_ENTERTAIN"),
      Map.entry("보험료", "EXPENSE_INSURANCE"),
      Map.entry("교통비", "EXPENSE_TRAVEL"),
      Map.entry("출장비", "EXPENSE_TRAVEL"));

  /** V979에서 의도적으로 매핑하지 않는 항목 — global 차.음료 매핑 금지. */
  private static final List<String> FORBIDDEN_MAPPINGS = List.of(
      "차.음료", "차·음료", "차 음료");

  @Autowired EasyAccountRepository accountRepository;
  @Autowired EasyAccountMappingRepository mappingRepository;
  @Autowired EasyClassificationRuleRepository ruleRepository;

  private EasyImportService importService;
  private EasyAccountService accountService;

  @BeforeEach
  void setUp() {
    accountService = new EasyAccountService(accountRepository, mappingRepository, ruleRepository);
    importService = new EasyImportService(
        mock(EasyImportBatchRepository.class),
        mock(EasyImportRowRepository.class),
        mock(EasyBusinessRepository.class),
        mock(EasyTransactionRepository.class),
        accountService,
        mappingRepository,
        mock(EasyJournalService.class));
    
    // V978 기존 계정 seed
    seedAccount("EXPENSE_RENT", "임차료", 12);
    seedAccount("EXPENSE_ADMIN", "관리비", 13);
    seedAccount("EXPENSE_PRINT", "교재·인쇄", 14);
    seedAccount("EXPENSE_SUPPLIES", "소모품", 16);
    seedAccount("EXPENSE_FEE", "지급수수료", 17);
    seedAccount("EXPENSE_TAX", "세금·공과금", 18);
    seedAccount("EXPENSE_ENTERTAIN", "접대비", 20);
    seedAccount("EXPENSE_INSURANCE", "보험료", 21);
    seedAccount("EXPENSE_TRAVEL", "여비교통비", 22);
    
    // V979 신규 계정 seed
    seedAccount("EXPENSE_COMMUNICATION", "통신비", 23);
    
    // V978 매핑 seed
    for (Map.Entry<String, String> entry : V978_MAPPINGS.entrySet()) {
      EasyAccount account = accountRepository.findByCode(entry.getValue()).orElseThrow();
      mappingRepository.save(EasyAccountMapping.builder()
          .account(account).externalCode(entry.getKey()).externalName(entry.getKey())
          .note("test seed (V978 mirror)").build());
    }
    
    // V979 매핑 seed
    for (Map.Entry<String, String> entry : V979_MAPPINGS.entrySet()) {
      EasyAccount account = accountRepository.findByCode(entry.getValue()).orElseThrow();
      mappingRepository.save(EasyAccountMapping.builder()
          .account(account).externalCode(entry.getKey()).externalName(entry.getKey())
          .note("test seed (V979 mirror)").build());
    }
  }

  private void seedAccount(String code, String name, int sortOrder) {
    accountRepository.save(EasyAccount.builder()
        .code(code).name(name).direction(EasyAccountDirection.EXPENSE)
        .sortOrder(sortOrder).active(true).build());
  }

  private void seedEmart24Rule() {
    EasyAccount supplies = accountRepository.findByCode("EXPENSE_SUPPLIES").orElseThrow();
    ruleRepository.save(EasyClassificationRule.builder()
        .patternType(EasyRulePatternType.COUNTERPARTY_REGEX)
        .pattern("이마트24")
        .account(supplies)
        .priority(50)
        .direction(EasyTxnDirection.EXPENSE)
        .active(true)
        .build());
  }

  @Test
  @DisplayName("V980 이마트24 classification rule seed 포함")
  void migrationSeedsEmart24Rule() throws Exception {
    String sql = Files.readString(
        Path.of("src/main/resources/db/migration/V980__add_emart24_expense_classification_rule.sql"),
        StandardCharsets.UTF_8);
    assertThat(sql).contains("COUNTERPARTY_REGEX");
    assertThat(sql).contains("'이마트24'");
    assertThat(sql).contains("EXPENSE_SUPPLIES");
    assertThat(sql).contains("'EXPENSE'");
    assertThat(sql).contains("priority");
  }

  @Test
  @DisplayName("이마트24 expense → EXPENSE_SUPPLIES (classify fallback)")
  void emart24ExpenseClassifiesToSupplies() {
    seedEmart24Rule();
    
    Optional<EasyAccount> resolved = importService.resolveMapping("차.음료");
    assertThat(resolved).as("차.음료 global mapping 없음").isEmpty();
    
    Optional<EasyAccountService.ClassificationMatch> match = accountService.classify(
        "이마트24 압구정점", "차.음료", 5000L, EasyTxnDirection.EXPENSE);
    assertThat(match).as("이마트24 포함 counterparty → 소모품").isPresent();
    assertThat(match.get().account().getCode()).isEqualTo("EXPENSE_SUPPLIES");
  }

  @Test
  @DisplayName("이마트24 포함된 실제적 counterparty 문자열 → EXPENSE_SUPPLIES")
  void emart24VariantsClassifyToSupplies() {
    seedEmart24Rule();
    
    String[] variants = {"이마트24", "이마트24 강남점", "이마트24(압구정)", "이마트24 #1234"};
    for (String variant : variants) {
      Optional<EasyAccountService.ClassificationMatch> match = accountService.classify(
          variant, "차.음료", 3000L, EasyTxnDirection.EXPENSE);
      assertThat(match).as("variant: %s", variant).isPresent();
      assertThat(match.get().account().getCode()).isEqualTo("EXPENSE_SUPPLIES");
    }
  }

  @Test
  @DisplayName("스타벅스 차.음료 → 자동 분류되지 않음")
  void starbucksBeverageNotClassified() {
    seedEmart24Rule();
    
    Optional<EasyAccountService.ClassificationMatch> match = accountService.classify(
        "스타벅스", "차.음료", 5000L, EasyTxnDirection.EXPENSE);
    assertThat(match).as("스타벅스는 이마트24 rule에 매칭 안 됨").isEmpty();
  }

  @Test
  @DisplayName("일반 차.음료 → 자동 분류되지 않음")
  void genericBeverageNotClassified() {
    seedEmart24Rule();
    
    Optional<EasyAccountService.ClassificationMatch> match = accountService.classify(
        "CU", "차.음료", 3000L, EasyTxnDirection.EXPENSE);
    assertThat(match).as("CU는 이마트24 rule에 매칭 안 됨").isEmpty();
  }

  @Test
  @DisplayName("income 방향의 이마트24 → 이 rule 적용 안 됨")
  void emart24IncomeNotClassified() {
    seedEmart24Rule();
    
    Optional<EasyAccountService.ClassificationMatch> match = accountService.classify(
        "이마트24", "환불", 5000L, EasyTxnDirection.INCOME);
    assertThat(match).as("INCOME 방향은 EXPENSE rule에 매칭 안 됨").isEmpty();
  }

  @Test
  @DisplayName("V979에 신규 계정 EXPENSE_COMMUNICATION seed 포함")
  void migrationSeedsCommunicationAccount() throws Exception {
    String sql = Files.readString(
        Path.of("src/main/resources/db/migration/V979__add_easy_communication_account_and_mappings.sql"),
        StandardCharsets.UTF_8);
    assertThat(sql).contains("'EXPENSE_COMMUNICATION', '통신비', 'EXPENSE', 23");
  }

  @Test
  @DisplayName("V979 신규 매핑 3건이 importer 조회 키와 일치")
  void v979MappingsResolve() {
    for (Map.Entry<String, String> entry : V979_MAPPINGS.entrySet()) {
      Optional<EasyAccount> resolved = importService.resolveMapping(entry.getKey());
      assertThat(resolved)
          .as("mapping for %s", entry.getKey())
          .isPresent();
      assertThat(resolved.get().getCode()).isEqualTo(entry.getValue());
      assertThat(resolved.get().isActive()).isTrue();
    }
  }

  @Test
  @DisplayName("차.음료 global mapping 없음 (금지)")
  void noGlobalBeverageMapping() {
    for (String key : FORBIDDEN_MAPPINGS) {
      assertThat(importService.resolveMapping(key))
          .as("no global mapping for %s", key)
          .isEmpty();
    }
  }

  @Test
  @DisplayName("V978 기존 매핑 11건 회귀 없음")
  void v978MappingsStillWork() {
    for (Map.Entry<String, String> entry : V978_MAPPINGS.entrySet()) {
      Optional<EasyAccount> resolved = importService.resolveMapping(entry.getKey());
      assertThat(resolved)
          .as("V978 mapping regression for %s", entry.getKey())
          .isPresent();
      assertThat(resolved.get().getCode()).isEqualTo(entry.getValue());
    }
  }
}