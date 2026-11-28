package f2r.tutor_solution.private_tutor_solution_back.domain.parentnotice;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ParentNoticeStorage {
  private final JdbcTemplate jdbc;

  boolean insertDraft(String id, AuthenticatedPrincipal owner, ParentNoticeService.Kind kind,
      String title, String body, ParentNoticeService.Source source, Instant savedAt) {
    // A plain INSERT distinguishes duplicate keys even with Connector/J's CLIENT_FOUND_ROWS.
    try {
      return jdbc.update("""
          INSERT INTO parent_notice_drafts (id, owner_id, owner_role, kind, title, body, source, created_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          """, id, owner.userId(), owner.role(), kind.name(), title, body, source.name(),
          LocalDateTime.ofInstant(savedAt, ZoneOffset.UTC)) == 1;
    } catch (DuplicateKeyException alreadySaved) {
      return false;
    }
  }

  void confirm(long academyId, String draftId, ParentNoticeService.Source source, Instant savedAt) {
    try {
      jdbc.update("""
          INSERT INTO codi_task_ledger
            (task_id, academy_id, task_type, channel, request_key, result_kind, result_id, qty, status, created_at)
          VALUES (?, ?, 'PARENT_NOTICE', ?, ?, 'PARENT_NOTICE_DRAFT', ?, 1, 'CONFIRMED', ?)
          """, UUID.randomUUID().toString(), academyId, source.name(), draftId, draftId,
          LocalDateTime.ofInstant(savedAt, ZoneOffset.UTC).withNano(0));
    } catch (DuplicateKeyException alreadyCounted) {
      return;
    }
    YearMonth month = YearMonth.from(savedAt.atZone(KstTime.ZONE));
    jdbc.update("""
        INSERT INTO codi_usage_counter (academy_id, period_start, period_end, task_type, confirmed_qty)
        VALUES (?, ?, ?, 'PARENT_NOTICE', 1)
        ON DUPLICATE KEY UPDATE confirmed_qty = confirmed_qty + 1
        """, academyId, month.atDay(1), month.atEndOfMonth());
  }

  long monthlyConfirmed(long academyId, YearMonth month) {
    return jdbc.queryForObject("""
        SELECT COALESCE(MAX(confirmed_qty), 0) FROM codi_usage_counter
        WHERE academy_id = ? AND period_start = ? AND task_type = 'PARENT_NOTICE'
        """, Long.class, academyId, month.atDay(1));
  }
}
