package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionProviderReadiness;
import java.time.LocalDateTime;

/**
 * ADMIN collection connection view. It never contains connectionRef, credentials, cursor values,
 * tokens, cookies, or raw provider payloads.
 */
public record EasyCollectionManagementResponse(
    Long connectionId,
    Long businessId,
    String label,
    String sourceCode,
    String sourceName,
    String connectionStatus,
    boolean enabled,
    boolean needsReauth,
    boolean hasConnectionRef,
    EasyCollectionProviderReadiness readiness,
    LocalDateTime lastSuccessAt,
    Long latestRunId,
    String latestRunStatus,
    LocalDateTime latestRunStartedAt,
    LocalDateTime latestRunFinishedAt,
    boolean checkpointExists,
    /** 은행 기관코드(4자리) — 비민감. */
    String organization,
    /** B=기업, P=개인. */
    String clientType,
    /** 뒤 4자리만 보이는 계좌번호. 전체 계좌번호는 응답에 넣지 않는다. */
    String accountMasked,
    String lastErrorCode,
    LocalDateTime disconnectedAt,
    /** 화면 표시용 인증 상태: NOT_REGISTERED / REGISTERED / NEEDS_REAUTH / DISCONNECTED. */
    String authStatus,
    Integer latestRunLedgerSavedCount,
    Integer latestRunLedgerReviewCount,
    /** 화면 표시용: 최근 실행의 수집 방식(예: MCP_SITE = 직접 로그인 수집, CSV_FILE). */
    String latestRunAdapterCode,
    /** 화면 표시용: 직접 로그인 수집이면 기관 코드(예: KB_BANK). 아니면 null. */
    String siteCode,
    /** 화면 표시용: 직접 로그인 수집이면 사용자가 정한 계좌 참조(불투명 이름). 아니면 null. */
    String siteAccountRef,
    /** 화면 표시용: 이 연결로 장부에 저장된 건수 합계. */
    long ledgerSavedTotal
) {
  public static EasyCollectionManagementResponse from(EasyCollectionConnection connection,
      EasyCollectionRun latestRun, EasyCollectionProviderReadiness readiness,
      boolean checkpointExists) {
    return from(connection, latestRun, readiness, checkpointExists, null, 0L);
  }

  /** @param siteRawPayload 직접 로그인 수집 실행의 첫 원본 JSON(기관 코드·계좌 참조 표시용), 없으면 null */
  public static EasyCollectionManagementResponse from(EasyCollectionConnection connection,
      EasyCollectionRun latestRun, EasyCollectionProviderReadiness readiness,
      boolean checkpointExists, String siteRawPayload, long ledgerSavedTotal) {
    return new EasyCollectionManagementResponse(
        connection.getConnectionId(),
        connection.getBusiness().getBusinessId(),
        connection.getLabel(),
        connection.getSource().getCode(),
        connection.getSource().getName(),
        connection.getStatus().name(),
        connection.getStatus() == EasyCollectionConnectionStatus.ACTIVE,
        connection.getStatus() == EasyCollectionConnectionStatus.NEEDS_REAUTH,
        connection.getConnectionRef() != null && !connection.getConnectionRef().isBlank(),
        readiness,
        KstTime.toKst(connection.getLastSuccessAt()),
        latestRun == null ? null : latestRun.getRunId(),
        latestRun == null ? null : latestRun.getStatus().name(),
        latestRun == null ? null : KstTime.toKst(latestRun.getStartedAt()),
        latestRun == null ? null : KstTime.toKst(latestRun.getCompletedAt()),
        checkpointExists,
        configField(connection.getProviderConfig(), "organization"),
        configField(connection.getProviderConfig(), "clientType"),
        maskAccount(configField(connection.getProviderConfig(), "account")),
        connection.getLastErrorCode(),
        KstTime.toKst(connection.getDisconnectedAt()),
        authStatus(connection),
        latestRun == null ? null : latestRun.getLedgerSavedCount(),
        latestRun == null ? null : latestRun.getLedgerReviewCount(),
        latestRun == null ? null : latestRun.getAdapterCode(),
        configField(siteRawPayload, "site"),
        configField(siteRawPayload, "account_ref"),
        ledgerSavedTotal);
  }

  static String authStatus(EasyCollectionConnection connection) {
    if (connection.getStatus() == EasyCollectionConnectionStatus.DISCONNECTED) {
      return "DISCONNECTED";
    }
    if (connection.getStatus() == EasyCollectionConnectionStatus.NEEDS_REAUTH) {
      return "NEEDS_REAUTH";
    }
    return connection.getConnectionRef() == null || connection.getConnectionRef().isBlank()
        ? "NOT_REGISTERED" : "REGISTERED";
  }

  private static String configField(String json, String field) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      com.fasterxml.jackson.databind.JsonNode node =
          new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get(field);
      return node == null || node.isNull() ? null : node.asText();
    } catch (Exception e) {
      return null;
    }
  }

  static String maskAccount(String account) {
    if (account == null || account.isBlank()) {
      return null;
    }
    String digits = account.replaceAll("[^0-9]", "");
    if (digits.length() <= 4) {
      return "****";
    }
    return "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
  }
}
