/**
 * Zones — 3-area partial-improvement lab. MASTER stays untouched.
 * [1] HERO · [2] 2학기 영어 내신 LIVE · [3] 학습 철학.
 * Each zone: CURRENT (live-faithful) vs OPTION A/B (UI-only improvements).
 * No production changes. No new 21st gets (quota 0/2) — only already
 * retrieved real code (coverflow id 23997) + dependency-free motion.
 */
import { HeroCurrent, HeroOptionA, HeroOptionB } from './ZoneHero';
import { LiveCurrent, LiveOptionA, LiveOptionB } from './ZoneLive';
import { PhilCurrent, PhilOptionA, PhilOptionB } from './ZonePhil';
import { Variant, ZoneFrame } from './frame';

export function ZonesApp() {
  return (
    <div className="min-h-screen bg-paper text-ink">
      <header className="border-b border-border bg-white px-4 py-6">
        <div className="mx-auto max-w-5xl">
          <p className="inline-block rounded-full bg-amber-100 px-3 py-1 text-[11px] font-extrabold text-amber-800">
            DESIGN LAB · 부분 개선 실험 (MASTER 불변)
          </p>
          <h1 className="mt-2 text-2xl font-extrabold sm:text-3xl">Eduholic 3-Zone Lab</h1>
          <p className="mt-1 max-w-2xl text-sm leading-relaxed text-muted-foreground">
            운영 홈페이지를 MASTER로 두고 HERO · 내신 LIVE · 학습 철학 3곳만 비교합니다.
            각 영역 CURRENT가 운영과 같은지 먼저 확인하고, OPTION을 눌러·밀어·스크롤하며 비교하세요.
            승인 없는 OPTION은 어디에도 반영하지 않습니다.
          </p>
          <nav className="no-scrollbar mt-4 flex gap-2 overflow-x-auto" aria-label="영역 이동">
            <a href="#zone-hero" className="shrink-0 rounded-full border border-border bg-paper px-4 py-2 text-xs font-bold">[1] HERO</a>
            <a href="#zone-live" className="shrink-0 rounded-full border border-border bg-paper px-4 py-2 text-xs font-bold">[2] 내신 LIVE</a>
            <a href="#zone-phil" className="shrink-0 rounded-full border border-border bg-paper px-4 py-2 text-xs font-bold">[3] 학습 철학</a>
          </nav>
        </div>
      </header>

      <main>
        <ZoneFrame
          id="zone-hero"
          no="[1] HERO"
          title="첫 화면"
          keep={['박정민 원장 사진·영상 원본', '소개 문구', '상담 신청하기', '대표 수업 보기', '35–58만원·87%·20년']}
        >
          <Variant code="CURRENT" title="현재 운영 모습" note="사진·문구·버튼·수치·링크 그대로. 상담=tel: 직결(운영 상담 모달과 동일 채널), 수업 보기=실제 LIVE 페이지.">
            <HeroCurrent />
          </Variant>
          <Variant code="OPTION A" title="등장 애니메이션 + 사진 연출" note="같은 콘텐츠에 스태거 등장·CTA 모션·운영 배지 추가. scroll-lock 미사용.">
            <HeroOptionA />
          </Variant>
          <Variant code="OPTION B" title="스크롤 패럴랙스 + 수치 카운트업" note="사진 패럴랙스·수치 진입 시 카운트업. 사진·문구·링크 동일.">
            <HeroOptionB />
          </Variant>
        </ZoneFrame>

        <ZoneFrame
          id="zone-live"
          no="[2] 2학기 영어 내신 LIVE"
          title="학교별 내신 영역"
          keep={['압구정중·신구중·신사중·언북중', '학년·Lesson', '시험범위 Wiki 링크', '출제 분석 링크', '상담 신청 목적지']}
        >
          <Variant code="CURRENT" title="현재 운영 모습" note="탭 + 스냅 행 + 1/4 진행 + 상담 버튼. 모든 링크 목적지 동일.">
            <LiveCurrent />
          </Variant>
          <Variant code="OPTION A" title="스냅 스와이프" note="같은 카드를 모바일 우선 스와이프 행으로. 도트 + 엣지 피크. 링크 동일.">
            <LiveOptionA />
          </Variant>
          <Variant code="OPTION B" title="커버플로우 (21st 실코드)" note="21st coverflow 실동작 + 타이포 카드(이미지 없음). 캡션 링크 동일." selected>
            <LiveOptionB />
          </Variant>
        </ZoneFrame>

        <ZoneFrame
          id="zone-phil"
          no="[3] 학습 철학"
          title="박정민 원장은 이렇게 가르칩니다."
          keep={['원칙 01–04 전문', 'PC 2×2 · 모바일 스와이프 구조']}
        >
          <Variant code="CURRENT" title="현재 운영 모습" note="PC 2열 그리드 / 모바일 스와이프 + 01/04 카운터. 문구 동일.">
            <PhilCurrent />
          </Variant>
          <Variant code="OPTION A" title="스크롤 리빌" note="같은 레이아웃에 진입 시 스태거 등장 + 호버 리프트. 문구 동일.">
            <PhilOptionA />
          </Variant>
          <Variant code="OPTION B" title="스텝 진행형 (TEMP)" note="같은 4개 원칙을 스텝 카드로 (탭·버튼·스와이프). 문구 동일. TEMP: 21st 실코드 미수령(할당량 0). " selected>
            <PhilOptionB />
          </Variant>
        </ZoneFrame>
      </main>

      <footer className="border-t border-border bg-white px-4 py-8">
        <div className="mx-auto max-w-5xl text-center">
          <p className="text-sm text-muted-foreground">MASTER(운영) 변경 없음 · 승인 없는 OPTION 반영 금지</p>
          <p className="mt-1 text-xs text-muted-foreground">마음에 드는 OPTION이 있으면 영역+코드를 알려주세요 (예: “[2] OPTION B 승인”)</p>
        </div>
      </footer>
    </div>
  );
}
