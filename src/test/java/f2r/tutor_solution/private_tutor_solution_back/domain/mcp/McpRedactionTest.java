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

  @Test
  void internationalKoreanMobileAndLandlineNumbersAreMasked() {
    for (String value :
        java.util.List.of(
            "+82-10-1234-5678",
            "+82 10 1234 5678",
            "+821012345678",
            "+82 (0)10-1234-5678",
            "+82-2-123-4567",
            "+82-31-1234-5678")) {
      String masked = McpRedaction.text(value);
      assertNotEquals(value, masked, value);
      assertTrue(masked.contains("****"), masked);
    }
  }

  @Test
  void unseparatedAccountAndCardNumbersAreMaskedWithoutChangingNumericAmounts() {
    assertEquals("******7890", McpRedaction.text("1234567890"));
    assertEquals("************1111", McpRedaction.text("4111111111111111"));
    assertEquals("****-****-****-1111", McpRedaction.text("4111-1111-1111-1111"));
    var json = new tools.jackson.databind.ObjectMapper();
    var gateway = new McpGateway(null, null, null, null, json, null);
    var source =
        json.readTree(
            "{\"accountNumber\":12345678901,\"cardNumber\":4111111111111111,\"amount\":1234567890,\"accountId\":12345,\"notes\":[\"+821012345678\"]}");
    tools.jackson.databind.JsonNode masked =
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(gateway, "redact", source);
    assertTrue(masked.path("accountNumber").isTextual());
    assertFalse(masked.path("accountNumber").asText().contains("12345"));
    assertTrue(masked.path("cardNumber").asText().endsWith("1111"));
    assertTrue(masked.path("amount").isNumber());
    assertEquals(1234567890L, masked.path("amount").asLong());
    assertEquals(12345, masked.path("accountId").asInt());
    assertFalse(masked.path("notes").get(0).asText().contains("1234"));
  }

  @Test
  void canonicalHashesAndUuidsWithLongDigitRunsRemainVerifiable() {
    String sha = ("deadbeef1234567890123" + "abcdef".repeat(8)).substring(0, 64);
    String uuid = "12345678-1234-4123-8123-123456789012";
    assertEquals(sha, McpRedaction.value("sha256", sha));
    assertEquals(uuid, McpRedaction.value("fileId", uuid));
    assertNotEquals(sha, McpRedaction.value("accountNumber", sha));
  }

  @Test
  void givenGitCommitWithLongDigits_whenMemoryIsRedacted_thenExactCommitRestoresAndPiiStillMasks() {
    String sha = "6148b8d9c90054485194057e14feff76c37f55b3";
    var json = new tools.jackson.databind.ObjectMapper();
    var gateway = new McpGateway(null, null, null, null, json, null);
    var input = json.createObjectNode();
    input.putObject("memory").put("commitSha", sha);
    input.put("accountNumber", sha);
    input.put("cardNumber", "4111111111111111");
    input.put("commit", "4111111111111111");
    input.put("notes", "1234567890123456789");
    tools.jackson.databind.JsonNode actual =
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(gateway, "redact", input);
    assertEquals(sha, actual.path("memory").path("commitSha").asText());
    assertNotEquals(sha, actual.path("accountNumber").asText());
    assertFalse(actual.path("cardNumber").asText().contains("41111111"));
    assertFalse(actual.path("commit").asText().contains("41111111"));
    assertFalse(actual.path("notes").asText().contains("1234567890"));
  }

  @Test
  void givenSha256InsideMarkdownTable_whenDocumentIsRedacted_thenHashStaysVerifiableAndPhoneIsMasked() {
    // given: a SHA-256 whose hex digits contain a 12-digit run, as found in MANIFEST.md rows
    String sha = "461926ffdb3a391c6b31d55aa759a471667426420a9947d9cf55c7732e7f23be";
    String row = "| `a/b.png` | 12345 | " + sha + " | 연락 010-1234-5678 |";
    // when
    String masked = McpRedaction.text(row);
    // then
    assertTrue(masked.contains(sha), masked);
    assertTrue(masked.contains("010-****-5678"), masked);
    assertFalse(masked.contains("1234-5678"), masked);
  }

  @Test
  void givenGitShaInsideFreeText_whenRedacted_thenExactCommitIsPreserved() {
    // given: the real handoff commit contains the 10-digit run 0054485194
    String commit = "6148b8d9c90054485194057e14feff76c37f55b3";
    // when
    String masked = McpRedaction.text("최신 커밋은 " + commit + " 입니다. 계좌 1234567890123");
    // then
    assertTrue(masked.contains(commit), masked);
    assertFalse(masked.contains("1234567890123"), masked);
  }

  @Test
  void givenUuidInsideFreeText_whenRedacted_thenIdentifierIsPreserved() {
    // given: a file id whose last group is purely numeric
    String id = "8b67e41f-608c-4d4a-a9f6-123456789012";
    // when / then
    assertTrue(McpRedaction.text("근거 파일 " + id + " 참고").contains(id));
  }

  @Test
  void givenHashGluedToLetters_whenRedacted_thenItIsNotTreatedAsStandaloneDigest() {
    // given: a hex string glued to a word is not a standalone digest, so ordinary masking applies
    String glued = "x6148b8d9c90054485194057e14feff76c37f55b3";
    // when / then
    assertFalse(McpRedaction.text(glued).contains("90054485194"));
  }
}
