package f2r.tutor_solution.private_tutor_solution_back.domain.wemade;

import java.time.LocalDate;

/**
 * 68,910원 인수계약 핵심 상수 — 단일 출처.
 *
 * 근거: DART rcpNo 20260630900888 (2026-06-30 체결 공시, A등급).
 * 숫자 변경(잔금일 변경 등) 시 여기만 수정한다.
 * 화면/JS의 하드코딩은 /api/wemade/deal으로 대체한다.
 */
public final class WemadeDealConstants {

    private WemadeDealConstants() {}

    /** 1주당 가액 (원) */
    public static final long PER_SHARE_KRW = 68_910L;

    /** 양수도 대금 총액 (원) = 9,200억 */
    public static final long TOTAL_AMOUNT_KRW = 920_000_000_000L;

    /** 양도 지분율 (%) */
    public static final double STAKE_PCT = 39.33;

    /** 양도 주식수 (주) */
    public static final long STAKE_SHARES = 13_350_738L;

    /** 계약금 (원) = 10%, 2026-06-30 지급 완료 */
    public static final long DEPOSIT_KRW = 92_000_000_000L;

    /** 잔금 (원) = 90%, 2026-10-30 예정 */
    public static final long BALANCE_KRW = 828_000_000_000L;

    /** 잔금/거래종결 예정일 (D-day 기준) */
    public static final LocalDate CLOSE_DATE = LocalDate.of(2026, 10, 30);

    /** 양수인 */
    public static final String BUYER = "NeoPulse Co., Ltd.";

    /** DART 접수번호 */
    public static final String RCP_NO = "20260630900888";

    /**
     * 인수 타임라인 단계 (고정 사실).
     * 각 단계: {제목, 부제, 상태} — 상태는 done/next/todo.
     */
    public static final String[][] TIMELINE = {
            {"6/30 SPA 체결", "DART rcpNo 20260630900888", "done"},
            {"계약금 920억 지급", "2026-06-30 · 대금의 10%", "done"},
            {"킹넷 참여 공시", "9/12 CNINFO · Neosphere 49%", "done"},
            {"공동입장문", "9/14 · 10/30 종결 목표", "done"},
            {"잔금 8,280억", "2026-10-30 예정 · 대금의 90%", "next"},
            {"최대주주 변경 종결", "선행조건 충족 시", "todo"},
    };
}
