// Deterministic workflow for source_code=KB_CARD — KB국민카드 개인사업자용 이용내역.
//
// SOURCE OF TRUTH (empirically verified on the academy PC, 2026-09-24):
//   LOGIN:      human certificate login only — https://card.kbcard.com/
//   EXTRA AUTH: none. After login, period change / re-query needs no re-auth.
//   MENU:       My KB → 개인사업자용 이용내역
//   QUERY:      2026-09-01 ~ 2026-09-24 in ONE request (no monthly split), 50 rows.
//   DOWNLOAD:   official Excel button (인쇄·저장하기 → 엑셀), real BIFF .xls.
//   FORMAT:     14 columns (이용일..승인번호), see KbCardExcelParser.
//
// HARD BOUNDARY: this workflow NEVER selects a certificate, types a password/OTP/CVC,
// exports cookies, or touches the auth UI. The engine (collector.js) blocks in
// WAITING_FOR_USER_AUTH until a human finishes logging in, then only detects the
// post-login usage page (authDetect) and performs query/download steps.
//
// Selectors below are the integration contract: if KB's real DOM differs at runtime,
// steps fail and the task goes FAILED (fail-closed) — never guessed around.
// The mock KB-shaped pages used by test/e2e-kb-lifecycle.js implement this same contract.
export const kbCardWorkflow = {
  code: 'KB_CARD',
  loginUrl: (baseUrl) => {
    // Production: always the real KB site. Tests pass a mock base URL instead —
    // the real site is never driven by automated tests.
    if (baseUrl && baseUrl !== 'https://card.kbcard.com') return `${baseUrl}/kb-login.html`;
    return 'https://card.kbcard.com/';
  },
  // Post-login usage page marker: reaching it IS the "authenticated" signal.
  authDetect: { type: 'selector', selector: '#kb-usage-marker' },
  // KB query form accepts yyyy-MM-dd (verified file header: "2026-09-01 ~ 2026-09-24").
  collectSteps: (task) => [
    { action: 'fill', selector: '#kb-start-date', value: task.requestedFrom },
    { action: 'fill', selector: '#kb-end-date', value: task.requestedTo },
    { action: 'click', selector: '#kb-search-btn' },
  ],
  // Official Excel download trigger (인쇄·저장하기 → 엑셀).
  downloadTrigger: { action: 'click', selector: '#kb-excel-download' },
};
