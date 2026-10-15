#!/usr/bin/env python3
"""
pykrx 기반 위메이드 일봉 + 투자자별 수급 수집 스크립트.

용법:
    python3 pykrx_collect.py --mode ohlcv --symbols 112040,101730 --from 20260101 --to 20261005
    python3 pykrx_collect.py --mode flows --symbols 112040,101730 --from 20260101 --to 20261005

출력: JSON (stdout)
    {"ok": true, "mode": "ohlcv", "rows": [...], "errors": [...]}
    {"ok": true, "mode": "flows", "rows": [...], "errors": [...]}

주의:
- 투자자별 수급 엔드포인트는 KRX 로그인(KRX_ID/KRX_PW 환경변수) 없이
  빈 응답을 반환한다. 이 경우 rows=[] 와 함께 errors에 사유를 기록하고
  종료코드 0으로 끝낸다 (Java 측에서 PENDING 처리).
- OHLCV는 로그인 없이 동작한다.
"""
import argparse
import json
import sys
import traceback


def collect_ohlcv(symbols, from_date, to_date):
    from pykrx import stock
    rows, errors = [], []
    for symbol in symbols:
        try:
            df = stock.get_market_ohlcv(from_date, to_date, symbol)
            for idx, r in df.iterrows():
                rows.append({
                    "symbol": symbol,
                    "date": idx.strftime("%Y-%m-%d"),
                    "open": int(r["시가"]),
                    "high": int(r["고가"]),
                    "low": int(r["저가"]),
                    "close": int(r["종가"]),
                    "volume": int(r["거래량"]),
                })
        except Exception as e:
            errors.append({"symbol": symbol, "error": f"{type(e).__name__}: {e}"})
    return rows, errors


def collect_flows(symbols, from_date, to_date):
    """종목별 일별 투자자 순매수. KRX 로그인 없으면 빈 결과 + 사유 기록."""
    from pykrx import stock
    rows, errors = [], []
    # pykrx의 종목별 투자자 API(get_market_trading_value_by_investor)는
    # KRX_ID/KRX_PW 없이 빈 응답을 반환한다. 일자별로 시도하되 실패 시 기록만 한다.
    import os
    if not os.environ.get("KRX_ID") or not os.environ.get("KRX_PW"):
        errors.append({
            "reason": "KRX_LOGIN_REQUIRED",
            "detail": "pykrx investor endpoints return empty without KRX_ID/KRX_PW; "
                      "use KIS Open API (NEEDS_OWNER) or KRX CSV download as fallback",
        })
        return rows, errors
    for symbol in symbols:
        try:
            df = stock.get_market_trading_value_by_investor(from_date, to_date, symbol)
            # index=투자자구분, columns=매도/매수/순매수 (기간 합산이므로 일별 아님)
            errors.append({"symbol": symbol,
                           "note": "pykrx returns period-aggregated investor values; "
                                   "daily breakdown requires KRX login session"})
        except Exception as e:
            errors.append({"symbol": symbol, "error": f"{type(e).__name__}: {e}"})
    return rows, errors


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", required=True, choices=["ohlcv", "flows"])
    ap.add_argument("--symbols", required=True)
    ap.add_argument("--from", dest="from_date", required=True)
    ap.add_argument("--to", dest="to_date", required=True)
    args = ap.parse_args()

    symbols = [s.strip() for s in args.symbols.split(",") if s.strip()]
    try:
        if args.mode == "ohlcv":
            rows, errors = collect_ohlcv(symbols, args.from_date, args.to_date)
        else:
            rows, errors = collect_flows(symbols, args.from_date, args.to_date)
        print(json.dumps({"ok": True, "mode": args.mode, "rows": rows, "errors": errors},
                         ensure_ascii=False))
    except Exception:
        print(json.dumps({"ok": False, "mode": args.mode, "rows": [],
                          "errors": [{"error": traceback.format_exc(limit=3)}]},
                         ensure_ascii=False))
        sys.exit(1)


if __name__ == "__main__":
    main()
