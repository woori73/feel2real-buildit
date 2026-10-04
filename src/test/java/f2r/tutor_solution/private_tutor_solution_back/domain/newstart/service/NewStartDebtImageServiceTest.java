package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * NewStartDebtImageService 단위 테스트 — 네트워크 호출 없이 개인정보 마스킹과
 * Vision API JSON 파싱 로직만 검증한다.
 */
class NewStartDebtImageServiceTest {

  @Test
  void mask_removesResidentRegistrationNumber() {
    assertThat(NewStartDebtImageService.mask("홍길동 900101-1234567"))
        .doesNotContain("900101-1234567")
        .contains("[개인정보 제거]");
  }

  @Test
  void mask_removesAccountNumberButKeepsAmount() {
    // 계좌번호(뒤에 '원' 없음)는 제거
    assertThat(NewStartDebtImageService.mask("계좌 123456789012"))
        .contains("[개인정보 제거]");
    // 금액(뒤에 '원' 있음)은 보존
    assertThat(NewStartDebtImageService.mask("잔액 7,500,000원"))
        .contains("7,500,000원");
  }

  @Test
  void mask_handlesNullAndBlank() {
    assertThat(NewStartDebtImageService.mask(null)).isEmpty();
    assertThat(NewStartDebtImageService.mask("  ")).isEmpty();
  }

  @Test
  void parseDebts_extractsDebtList() throws Exception {
    NewStartDebtImageService service = newServiceWithoutNetwork();
    String json = """
        {
          "debts": [
            {"creditor": "신한은행", "debtType": "일반신용대출", "balance": "7,500,000원", "overdueInfo": "", "creditorChange": ""},
            {"creditor": "삼성카드", "debtType": "카드론", "balance": "2,500,000원", "overdueInfo": "장기연체", "creditorChange": ""}
          ]
        }
        """;
    List<NewStartDebtImageService.DebtImageItem> items = service.parseDebts(json);
    assertThat(items).hasSize(2);
    assertThat(items.get(0).creditor()).isEqualTo("신한은행");
    assertThat(items.get(0).balance()).isEqualTo("7,500,000원");
    assertThat(items.get(1).overdueInfo()).isEqualTo("장기연체");
  }

  @Test
  void parseDebts_handlesMarkdownCodeBlock() throws Exception {
    NewStartDebtImageService service = newServiceWithoutNetwork();
    String json = "```json\n{\"debts\": [{\"creditor\": \"국민은행\", \"debtType\": \"\", \"balance\": \"1,000,000원\", \"overdueInfo\": \"\", \"creditorChange\": \"\"}]}\n```";
    List<NewStartDebtImageService.DebtImageItem> items = service.parseDebts(json);
    assertThat(items).hasSize(1);
    assertThat(items.get(0).creditor()).isEqualTo("국민은행");
  }

  @Test
  void parseDebts_returnsEmptyOnInvalidJson() throws Exception {
    NewStartDebtImageService service = newServiceWithoutNetwork();
    assertThat(service.parseDebts("not json at all")).isEmpty();
    assertThat(service.parseDebts("{\"foo\": 1}")).isEmpty();
  }

  @Test
  void parseDebts_masksPersonalInfoInResponse() throws Exception {
    NewStartDebtImageService service = newServiceWithoutNetwork();
    String json = """
        {"debts": [{"creditor": "신한은행 900101-1234567", "debtType": "", "balance": "1,000,000원", "overdueInfo": "", "creditorChange": ""}]}
        """;
    List<NewStartDebtImageService.DebtImageItem> items = service.parseDebts(json);
    assertThat(items).hasSize(1);
    assertThat(items.get(0).creditor()).doesNotContain("900101-1234567");
  }

  /** 네트워크를 타지 않는 생성자 경로 (properties는 null 허용). */
  private NewStartDebtImageService newServiceWithoutNetwork() throws Exception {
    // NewStartLlmProperties가 null이면 NPE — 리플렉션 없이 기본 생성 불가하므로
    // OpenRouter 설정이 비어 있는 properties로 생성한다.
    f2r.tutor_solution.private_tutor_solution_back.config.NewStartLlmProperties props =
        new f2r.tutor_solution.private_tutor_solution_back.config.NewStartLlmProperties();
    return new NewStartDebtImageService(props);
  }
}
