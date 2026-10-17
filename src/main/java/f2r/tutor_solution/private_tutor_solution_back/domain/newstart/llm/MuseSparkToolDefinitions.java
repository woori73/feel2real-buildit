package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

import java.util.List;
import java.util.Map;

/**
 * Muse Spark 함수 호출용 도구 정의.
 *
 * <p>P1에서 허용된 EASY 자동수집 함수 6종을 정의한다.
 * 실제 금융 사이트 연동은 하지 않으며, Worker 작업 요청·상태 조회·미리보기·승인 요청만 수행한다.
 */
public final class MuseSparkToolDefinitions {

  private MuseSparkToolDefinitions() {
  }

  public static final String FN_REQUEST_CARD_SALES = "request_card_sales_download";
  public static final String FN_REQUEST_CASH_RECEIPT = "request_cash_receipt_download";
  public static final String FN_REQUEST_BANK_STATEMENT = "request_bank_statement_download";
  public static final String FN_CHECK_DOWNLOAD_STATUS = "check_download_status";
  public static final String FN_PREVIEW_IMPORT = "preview_import";
  public static final String FN_CONFIRM_IMPORT = "confirm_import";

  /** 허용된 함수 화이트리스트. */
  public static final List<String> WHITELIST = List.of(
      FN_REQUEST_CARD_SALES,
      FN_REQUEST_CASH_RECEIPT,
      FN_REQUEST_BANK_STATEMENT,
      FN_CHECK_DOWNLOAD_STATUS,
      FN_PREVIEW_IMPORT,
      FN_CONFIRM_IMPORT);

  private static Map<String, Object> dateRangeParams() {
    return Map.of(
        "type", "object",
        "properties", Map.of(
            "business_id", Map.of("type", "string", "description", "사업자등록번호 (예: 759-91-01977)"),
            "from_date", Map.of("type", "string", "description", "조회 시작일 (YYYY-MM-DD)"),
            "to_date", Map.of("type", "string", "description", "조회 종료일 (YYYY-MM-DD)")),
        "required", List.of("business_id", "from_date", "to_date"));
  }

  /** OpenAI 호환 tools 배열을 반환한다. */
  public static List<Map<String, Object>> all() {
    return List.of(
        Map.of("type", "function", "function", Map.of(
            "name", FN_REQUEST_CARD_SALES,
            "description", "카드매출 내역 다운로드를 Worker에 요청한다. P1에서는 로컬 테스트 사이트만 사용한다.",
            "parameters", dateRangeParams())),
        Map.of("type", "function", "function", Map.of(
            "name", FN_REQUEST_CASH_RECEIPT,
            "description", "현금영수증 수입 내역 다운로드를 Worker에 요청한다. P1에서는 로컬 테스트 사이트만 사용한다.",
            "parameters", dateRangeParams())),
        Map.of("type", "function", "function", Map.of(
            "name", FN_REQUEST_BANK_STATEMENT,
            "description", "은행 입출금 내역 다운로드를 Worker에 요청한다. P1에서는 로컬 테스트 사이트만 사용한다.",
            "parameters", dateRangeParams())),
        Map.of("type", "function", "function", Map.of(
            "name", FN_CHECK_DOWNLOAD_STATUS,
            "description", "다운로드 작업 상태를 조회한다.",
            "parameters", Map.of(
                "type", "object",
                "properties", Map.of(
                    "job_id", Map.of("type", "string", "description", "다운로드 작업 ID")),
                "required", List.of("job_id")))),
        Map.of("type", "function", "function", Map.of(
            "name", FN_PREVIEW_IMPORT,
            "description", "수집된 자료의 장부 반영 미리보기를 조회한다 (중복 검사 결과 포함).",
            "parameters", Map.of(
                "type", "object",
                "properties", Map.of(
                    "run_id", Map.of("type", "string", "description", "수집 실행 ID")),
                "required", List.of("run_id")))),
        Map.of("type", "function", "function", Map.of(
            "name", FN_CONFIRM_IMPORT,
            "description", "수집 자료의 장부 반영을 승인 요청한다. 실제 반영은 별도 승인 API를 통해서만 수행된다.",
            "parameters", Map.of(
                "type", "object",
                "properties", Map.of(
                    "run_id", Map.of("type", "string", "description", "수집 실행 ID")),
                "required", List.of("run_id")))));
  }

  /** 화이트리스트에 있는 함수인지 확인한다. */
  public static boolean isAllowed(String functionName) {
    return WHITELIST.contains(functionName);
  }
}
