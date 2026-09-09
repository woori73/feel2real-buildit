# Eduholic homepage video placement

The 53-second `내신대비_라이브_최종_53초` asset is the main 16:9 feature of the Eduholic `2학기 영어 내신 LIVE` section.

It must not appear in `sec-videos` (`에듀홀릭의 실제 수업 현장`) phone cards. Existing LESSON videos remain eligible for the Actual Class section. The guard is hostname-scoped to `eduholic.feel2real.co.kr`, so other academy storefronts keep the shared Academy video behavior.

The main feature continues to be rendered by `eduholic_midterm_home_live.js`; the placement guard only narrows the Eduholic public `renderVideos` input before `academy_public.js` captures the renderer.
