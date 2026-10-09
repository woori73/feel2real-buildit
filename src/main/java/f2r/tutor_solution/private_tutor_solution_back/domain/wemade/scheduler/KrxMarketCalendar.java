package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.scheduler;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * KRX 시장 휴장일 판정.
 * 2026-10-09 사용자 지시: 휴장일에는 KIS 수집을 실행하지 않는다.
 * 공휴일 목록은 매년 초 업데이트 필요.
 */
public class KrxMarketCalendar {

    // KRX 휴장일 (2026~2027). 매년 초 갱신.
    private static final Set<LocalDate> HOLIDAYS_2026_2027 = Set.of(
        // 2026년
        LocalDate.of(2026, 1, 1),   // 신정
        LocalDate.of(2026, 2, 16),  // 설날
        LocalDate.of(2026, 2, 17),
        LocalDate.of(2026, 2, 18),
        LocalDate.of(2026, 3, 2),   // 삼일절 대체휴일
        LocalDate.of(2026, 5, 5),   // 어린이날
        LocalDate.of(2026, 5, 25),  // 부처님오신날
        LocalDate.of(2026, 6, 8),   // 현충일 대체휴일
        LocalDate.of(2026, 8, 17),  // 광복절 대체휴일
        LocalDate.of(2026, 9, 24),  // 추석
        LocalDate.of(2026, 9, 25),
        LocalDate.of(2026, 9, 26),
        LocalDate.of(2026, 10, 5),  // 개천절 대체휴일
        LocalDate.of(2026, 10, 9),  // 한글날
        LocalDate.of(2026, 12, 25), // 성탄절
        LocalDate.of(2026, 12, 31), // 연말 휴장
        // 2027년
        LocalDate.of(2027, 1, 1),
        LocalDate.of(2027, 2, 8),   // 설날
        LocalDate.of(2027, 2, 9),
        LocalDate.of(2027, 2, 10),
        LocalDate.of(2027, 3, 1),
        LocalDate.of(2027, 5, 5),
        LocalDate.of(2027, 5, 19),  // 부처님오신날
        LocalDate.of(2027, 6, 7),   // 현충일 대체휴일
        LocalDate.of(2027, 8, 16),  // 광복절 대체휴일
        LocalDate.of(2027, 9, 14),  // 추석
        LocalDate.of(2027, 9, 15),
        LocalDate.of(2027, 9, 16),
        LocalDate.of(2027, 10, 4),   // 개천절 대체휴일
        LocalDate.of(2027, 10, 11),  // 한글날 대체휴일
        LocalDate.of(2027, 12, 27),  // 성탄절 대체휴일
        LocalDate.of(2027, 12, 31)
    );

    /**
     * 오늘이 KRX 거래일인지 판정.
     * 주말 또는 공휴일이면 false (휴장).
     */
    public static boolean isTradingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !HOLIDAYS_2026_2027.contains(date);
    }

    public static boolean isTradingDay() {
        return isTradingDay(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")));
    }
}
