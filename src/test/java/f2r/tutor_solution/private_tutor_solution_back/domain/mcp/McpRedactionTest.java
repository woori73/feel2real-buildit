package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class McpRedactionTest {
  @Test
  void phoneEmailAndAccountInFreeTextAreMasked() {
    // given
    String text = "학부모 010-1234-5678 / kim.parent@example.com / 계좌 110-123-456789";
    // when
    String masked = McpRedaction.text(text);
    // then
    assertFalse(masked.contains("1234-5678"));
    assertTrue(masked.contains("010-****-5678"));
    assertTrue(masked.contains("k***@example.com"));
    assertTrue(masked.contains("110-****-456789"));
  }

  @Test
  void residentRegistrationNumberIsMasked() {
    // given / when
    String masked = McpRedaction.text("주민번호 900101-1234567");
    // then
    assertEquals("주민번호 900101-*******", masked);
  }

  @Test
  void amountsAndDatesAreNotMasked() {
    // given
    String text = "2026-10-22 지출 1,234,500원 거래 ID 12345678";
    // when / then
    assertEquals(text, McpRedaction.text(text));
  }

  @Test
  void piiKeysKeepOnlyLastFourCharacters() {
    // given / when
    String masked = McpRedaction.value("parentPhone", "01099998888");
    String account = McpRedaction.value("accountNumber", "12345678901");
    // then
    assertEquals("010-****-8888", masked);
    assertTrue(account.endsWith("8901"));
    assertFalse(account.contains("12345"));
  }

  @Test
  void ordinaryKeysOnlyGetTextMasking() {
    // given / when / then
    assertEquals("수학 보강", McpRedaction.value("title", "수학 보강"));
  }
}
