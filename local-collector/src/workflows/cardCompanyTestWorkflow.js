// Deterministic workflow for source_code=CARD_COMPANY_TEST — the safe mock site (Section 10),
// never a real financial site. A real institution's workflow (Section 11, future phase) would be
// a new file shaped exactly like this one: login URL, a deterministic "auth succeeded" signal to
// wait for, and a declarative step list. No LLM inference anywhere in this file or its runner.
export const cardCompanyTestWorkflow = {
  code: 'CARD_COMPANY_TEST',
  loginUrl: (baseUrl) => `${baseUrl}/`,
  // How the collector knows the human finished authenticating, without ever touching the
  // credential itself: the mock site's post-login page has a stable marker element.
  authDetect: { type: 'selector', selector: '#dashboard-marker' },
  collectSteps: (task) => [
    { action: 'fill', selector: '#startDate', value: task.requestedFrom },
    { action: 'fill', selector: '#endDate', value: task.requestedTo },
    { action: 'click', selector: '#searchBtn' },
  ],
  downloadTrigger: { action: 'click', selector: '#downloadLink' },
};
