package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountMapping;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountMappingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
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
 * EXPENSE Golden 매핑 검증 (V978).
 *
 * <p>D: 마이그레이션 seed에 신규 계정 3개가 포함되어 있다.
 * E: 확정 매핑 11건이 importer 조회 키와 일치한다.
 * F: REVIEW 대상에는 자동 매핑이 없다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:easygolden;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DisplayName("Easy EXPENSE Golden 매핑 테스트 (V978)")
class EasyExpenseGoldenMappingTest {

  /** 확정 매핑 11건: EasyShop 원문 키 -> Easy 계정 코드. */
  private static final Map<String, String> GOLDEN_MAPPINGS = Map.ofEntries(
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

  /** REVIEW 유지 — 자동 매핑 등록 금지. */
  private static final List<String> REVIEW_KEYS = List.of(
      "거래처 선물", "차.음료", "차량유류", "잡비", "원재료",
      "서비스 이용료", "전화/휴대폰/인터넷");

  @Autowired EasyAccountRepository accountRepository;
  @Autowired EasyAccountMappingRepository mappingRepository;

  private EasyImportService importService;

  @BeforeEach
  void setUp() {
    importService = new EasyImportService(
        mock(EasyImportBatchRepository.class),
        mock(EasyImportRowRepository.class),
        mock(EasyBusinessRepository.class),
        mock(EasyTransactionRepository.class),
        mock(EasyAccountService.class),
        mappingRepository,
        mock(EasyJournalService.class));
    seedAccount("EXPENSE_RENT", "임차료", 12);
    seedAccount("EXPENSE_ADMIN", "관리비", 13);
    seedAccount("EXPENSE_PRINT", "교재·인쇄", 14);
    seedAccount("EXPENSE_SUPPLIES", "소모품", 16);
    seedAccount("EXPENSE_FEE", "지급수수료", 17);
    seedAccount("EXPENSE_TAX", "세금·공과금", 18);
    seedAccount("EXPENSE_ENTERTAIN", "접대비", 20);
    seedAccount("EXPENSE_INSURANCE", "보험료", 21);
    seedAccount("EXPENSE_TRAVEL", "여비교통비", 22);
    for (Map.Entry<String, String> entry : GOLDEN_MAPPINGS.entrySet()) {
      EasyAccount account = accountRepository.findByCode(entry.getValue()).orElseThrow();
      mappingRepository.save(EasyAccountMapping.builder()
          .account(account).externalCode(entry.getKey()).externalName(entry.getKey())
          .note("test seed (V978 mirror)").build());
    }
  }

  private void seedAccount(String code, String name, int sortOrder) {
    accountRepository.save(EasyAccount.builder()
        .code(code).name(name).direction(EasyAccountDirection.EXPENSE)
        .sortOrder(sortOrder).active(true).build());
  }

  @Test
  @DisplayName("D: V978에 신규 비용 계정 3개 seed 포함")
  void migrationSeedsThreeAccounts() throws Exception {
    String sql = Files.readString(
        Path.of("src/main/resources/db/migration/V978__add_easy_expense_accounts.sql"),
        StandardCharsets.UTF_8);
    assertThat(sql).contains("'EXPENSE_ENTERTAIN', '접대비', 'EXPENSE', 20");
    assertThat(sql).contains("'EXPENSE_INSURANCE', '보험료', 'EXPENSE', 21");
    assertThat(sql).contains("'EXPENSE_TRAVEL', '여비교통비', 'EXPENSE', 22");
  }

  @Test
  @DisplayName("E: Golden 매핑 11건이 importer 조회 키와 일치")
  void goldenMappingsResolve() {
    for (Map.Entry<String, String> entry : GOLDEN_MAPPINGS.entrySet()) {
      Optional<EasyAccount> resolved = importService.resolveMapping(entry.getKey());
      assertThat(resolved)
          .as("mapping for %s", entry.getKey())
          .isPresent();
      assertThat(resolved.get().getCode()).isEqualTo(entry.getValue());
      assertThat(resolved.get().isActive()).isTrue();
    }
  }

  @Test
  @DisplayName("F: REVIEW 대상에 자동 매핑 없음")
  void reviewKeysHaveNoMapping() {
    for (String key : REVIEW_KEYS) {
      assertThat(importService.resolveMapping(key))
          .as("no mapping for %s", key)
          .isEmpty();
    }
  }
}
