package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Easy Historical Replay (Phase Tax-1).
 *
 * 실제 신고 당시 자료 → EASY Tax Engine → 실제 신고 결과 비교를 위한 분류기.
 * 실제 신고값이 repo에 없으므로 현재는 MISSING_INPUT 보고가 정상 결과다.
 * 숫자를 맞추기 위한 hard-code/역산/임의 조정을 하지 않는다.
 */
@Service
public class EasyTaxReplayService {

  /** 재현 비교 분류. */
  public enum ReplayStatus {
    MATCHED,
    MISSING_INPUT,
    RULE_MISMATCH,
    SOURCE_MISMATCH,
    ROUNDING_DIFFERENCE,
    MANUAL_TAX_ADJUSTMENT,
    UNKNOWN
  }

  public record ReplayLine(String field, Long computed, Long filed, Long difference,
      ReplayStatus status, String note) {
  }

  public record ReplayResult(int fiscalYear, List<ReplayLine> lines, ReplayStatus overall,
      List<String> missing) {
  }

  /**
   * READ-ONLY 재현 — filed map에 없는 항목은 MISSING_INPUT.
   * computed가 null이면 EASY 측 자료 없음으로 MISSING_INPUT.
   */
  public ReplayResult replay(int fiscalYear, Map<String, Long> computed,
      Map<String, Long> filed) {
    Map<String, Long> safeComputed = computed == null ? Map.of() : computed;
    Map<String, Long> safeFiled = filed == null ? Map.of() : filed;
    java.util.Set<String> fields = new java.util.LinkedHashSet<>();
    fields.addAll(safeComputed.keySet());
    fields.addAll(safeFiled.keySet());
    List<ReplayLine> lines = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    for (String field : fields) {
      Long left = safeComputed.get(field);
      Long right = safeFiled.get(field);
      if (left == null || right == null) {
        lines.add(new ReplayLine(field, left, right, null,
            ReplayStatus.MISSING_INPUT, "신고값 또는 EASY 계산값 없음"));
        missing.add("MISSING_INPUT: " + field);
      } else if (left.equals(right)) {
        lines.add(new ReplayLine(field, left, right, 0L,
            ReplayStatus.MATCHED, "일치"));
      } else {
        lines.add(new ReplayLine(field, left, right, left - right,
            ReplayStatus.SOURCE_MISMATCH, "차이 발생 — 역산하지 않고 원인 추적 필요"));
      }
    }
    ReplayStatus overall = missing.isEmpty()
        && lines.stream().allMatch(line -> line.status() == ReplayStatus.MATCHED)
        ? ReplayStatus.MATCHED
        : lines.stream().anyMatch(line -> line.status() == ReplayStatus.SOURCE_MISMATCH)
            ? ReplayStatus.SOURCE_MISMATCH : ReplayStatus.MISSING_INPUT;
    return new ReplayResult(fiscalYear, lines, overall, missing);
  }
}
