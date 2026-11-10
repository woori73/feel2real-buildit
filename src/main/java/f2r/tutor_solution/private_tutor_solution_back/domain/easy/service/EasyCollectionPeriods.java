package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** 조회 기간을 기관의 1회 최대 조회 기간 단위로 나눈다. 구간은 날짜가 겹치지 않고 빠짐없이 이어진다. */
final class EasyCollectionPeriods {

  private EasyCollectionPeriods() {}

  /** [from, to]를 최대 maxDays일 구간으로 나눈다. from이 to보다 늦으면 [to, to] 한 구간. */
  static List<LocalDate[]> split(LocalDate from, LocalDate to, int maxDays) {
    if (from == null || to == null) {
      throw new IllegalArgumentException("조회 기간이 필요합니다.");
    }
    int days = Math.max(1, maxDays);
    LocalDate start = from.isAfter(to) ? to : from;
    List<LocalDate[]> windows = new ArrayList<>();
    LocalDate cursor = start;
    while (!cursor.isAfter(to)) {
      LocalDate end = cursor.plusDays(days - 1L);
      if (end.isAfter(to)) {
        end = to;
      }
      windows.add(new LocalDate[] {cursor, end});
      cursor = end.plusDays(1);
    }
    return windows;
  }

  /** 하루보다 긴 구간을 둘로 나눈다(앞 구간이 같거나 하루 길다). */
  static List<LocalDate[]> halve(LocalDate from, LocalDate to) {
    long span = ChronoUnit.DAYS.between(from, to);
    if (span < 1) {
      throw new IllegalArgumentException("하루 구간은 나눌 수 없습니다.");
    }
    LocalDate mid = from.plusDays(span / 2);
    return List.of(new LocalDate[] {from, mid}, new LocalDate[] {mid.plusDays(1), to});
  }
}
