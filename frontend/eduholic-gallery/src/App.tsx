import { useState } from 'react';
import { FlaskConical } from 'lucide-react';
import { CandidateFrame, type PickState } from '@/components/CandidateFrame';
import { HeroA } from '@/components/hero/HeroA';
import { HeroB } from '@/components/hero/HeroB';
import { HeroC } from '@/components/hero/HeroC';
import { SchoolA } from '@/components/school/SchoolA';
import { SchoolB } from '@/components/school/SchoolB';
import { SchoolC } from '@/components/school/SchoolC';
import { UpdateA } from '@/components/update/UpdateA';
import { UpdateB } from '@/components/update/UpdateB';
import { ProofA } from '@/components/proof/ProofA';
import { ProofB } from '@/components/proof/ProofB';
import { CtaA } from '@/components/cta/CtaA';

const AREAS: { key: keyof PickState; label: string; desc: string }[] = [
  { key: 'hero', label: 'HERO · 대표 영상', desc: '원장 소개 영상(principal-intro.mp4)과 학원명·상담 CTA를 쓰는 첫 화면' },
  { key: 'school', label: 'SCHOOL LIVE · 학교별 내신', desc: '압구정중·신구중·신사중·언북중 시험범위 Wiki + 출제 분석 링크' },
  { key: 'update', label: 'UPDATE · 최신 소식', desc: '내신 LIVE·시험범위·출제 분석 업데이트를 시각적으로 노출' },
  { key: 'proof', label: 'PROOF · 성과/실제 자료', desc: '수강료·향상률·경력 수치와 원장 메시지·학습 철학' },
  { key: 'cta', label: 'CTA · 상담 연결', desc: '02-6324-1453 전화 상담 + 대표 수업 보기, 모바일 sticky' },
];

export default function App() {
  const [picks, setPicks] = useState<PickState>({});
  const pick = (area: keyof PickState, code: string) =>
    setPicks((p) => ({ ...p, [area]: p[area] === code ? undefined : code }));

  const summary = AREAS.map((a) => `${a.key.toUpperCase()} ${picks[a.key] ?? '–'}`).join(' · ');

  return (
    <div className="min-h-screen bg-paper text-ink">
      <header className="border-b border-border bg-white px-4 py-6">
        <div className="mx-auto max-w-5xl">
          <p className="inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-3 py-1 text-[11px] font-extrabold text-amber-800">
            <FlaskConical className="h-3.5 w-3.5" /> DESIGN LAB · PREVIEW ONLY (noindex)
          </p>
          <h1 className="mt-2 text-2xl font-extrabold sm:text-3xl">Eduholic Dynamic UI Gallery</h1>
          <p className="mt-1 max-w-2xl text-sm leading-relaxed text-muted-foreground">
            실제 eduholic.feel2real.co.kr 콘텐츠로 만든 동작 후보 모음. 스크롤·호버·탭·스와이프를 직접
            경험하고 마음에 드는 조합(예: HERO B + SCHOOL C + UPDATE A)을 알려주세요. 선택 후 조합 Preview를 제작합니다.
          </p>
          <nav className="no-scrollbar mt-4 flex gap-2 overflow-x-auto" aria-label="영역 이동">
            {AREAS.map((a) => (
              <a key={a.key} href={`#area-${a.key}`} className="shrink-0 rounded-full border border-border bg-paper px-4 py-2 text-xs font-bold">
                {a.label}
              </a>
            ))}
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-5xl space-y-14 px-4 py-8">
        <section id="area-hero" className="scroll-mt-24 space-y-4">
          <AreaHead label="HERO · 대표 영상" desc={AREAS[0].desc} />
          <CandidateFrame id="hero-a" area="hero" code="HERO A" title="스크롤 스크럽 영상 (실코드)" component="scroll-locked-video-hero · @gughigug" source="21st.dev" sourceUrl="https://21st.dev/@gughigug/components/scroll-locked-video-hero" interaction="body pin + wheel/touch → currentTime 스크럽" mobile="touch 스크럽, 375px 확인" license="MIT · 실코드 적용 (id 25500)" picks={picks} onPick={pick}><HeroA /></CandidateFrame>
          <CandidateFrame id="hero-b" area="hero" code="HERO B" title="영상 다이얼로그" component="hero-video-dialog · @dillionverma (Magic UI)" source="21st.dev" sourceUrl="https://21st.dev/@dillionverma/components/hero-video-dialog" interaction="tap → 다이얼로그 재생, Esc/배경 닫기" mobile="전폭 썸네일 + 바텀시트형 다이얼로그" license="MIT (키 수령 후 실제 코드 반입 예정)" picks={picks} onPick={pick}><HeroB /></CandidateFrame>
          <CandidateFrame id="hero-c" area="hero" code="HERO C" title="스크롤 확장 미디어" component="scroll-expansion-hero · @arunachalam" source="21st.dev" sourceUrl="https://21st.dev/@arunachalam/components/scroll-expansion-hero" interaction="scroll → 라운드 축소 → 풀블리드 확장" mobile="375px에서 확장률 동일" license="MIT (키 수령 후 실제 코드 반입 예정)" picks={picks} onPick={pick}><HeroC /></CandidateFrame>
        </section>

        <section id="area-school" className="scroll-mt-24 space-y-4">
          <AreaHead label="SCHOOL LIVE · 학교별 내신" desc={AREAS[1].desc} />
          <CandidateFrame id="school-a" area="school" code="SCHOOL A" title="스퀴즈 아코디언" component="carousel-squeeze · @yura" source="21st.dev" sourceUrl="https://21st.dev/@yura/components/carousel-squeeze" interaction="click 확장 + 자동재생(호버 정지) + 도트" mobile="세로 아코디언으로 전환" license="MIT (키 수령 후 실제 코드 반입 예정)" picks={picks} onPick={pick}><SchoolA /></CandidateFrame>
          <CandidateFrame id="school-b" area="school" code="SCHOOL B" title="커버플로우 드래그" component="coverflow-carousel · @ruixen.ui" source="21st.dev" sourceUrl="https://21st.dev/@ruixen.ui/components/coverflow-carousel" interaction="drag/swipe 스냅 + 캡션 연동" mobile="스와이프 우선, 버튼 보조" license="MIT (키 수령 후 실제 코드 반입 예정)" picks={picks} onPick={pick}><SchoolB /></CandidateFrame>
          <CandidateFrame id="school-c" area="school" code="SCHOOL C" title="스토리 뷰어" component="stories-carousel · @haydenbleasel (Kibo UI)" source="21st.dev" sourceUrl="https://21st.dev/@haydenbleasel/components/stories-carousel" interaction="tap → 프로그레스 자동재생 + 좌우 탭" mobile="스토리 UX 네이티브" license="미표기 — 코드 반입 시 라이선스 확인 필요" picks={picks} onPick={pick}><SchoolC /></CandidateFrame>
        </section>

        <section id="area-update" className="scroll-mt-24 space-y-4">
          <AreaHead label="UPDATE · 최신 소식" desc={AREAS[2].desc} />
          <CandidateFrame id="update-a" area="update" code="UPDATE A" title="스크롤 속도 반응 스트립" component="scroll-based-velocity · @dillionverma (Magic UI)" source="21st.dev" sourceUrl="https://21st.dev/@dillionverma/components/scroll-based-velocity" interaction="scroll 속도에 반응하는 무한 스크롤" mobile="터치 스크롤에서도 반응" license="MIT (키 수령 후 실제 코드 반입 예정)" picks={picks} onPick={pick}><UpdateA /></CandidateFrame>
          <CandidateFrame id="update-b" area="update" code="UPDATE B" title="라이브 티커" component="marquee 패턴 · 21st.dev marquee 카테고리" source="21st.dev" sourceUrl="https://21st.dev/community/components/s/marquee" interaction="자동 티커 + 터치홀드 정지 + 탭 이동" mobile="홀드-투-포즈" license="CSS 기반 자작 (스펙 참조)" picks={picks} onPick={pick}><UpdateB /></CandidateFrame>
        </section>

        <section id="area-proof" className="scroll-mt-24 space-y-4">
          <AreaHead label="PROOF · 성과/실제 자료" desc={AREAS[3].desc} />
          <CandidateFrame id="proof-a" area="proof" code="PROOF A" title="카운트업 수치" component="counting-number · @cnippet-dev" source="21st.dev" sourceUrl="https://21st.dev/@cnippet-dev/components/counting-number" interaction="스크롤 진입 시 1회 카운트업" mobile="3열 유지, tabular-nums" license="미표기 — 코드 반입 시 라이선스 확인 필요" picks={picks} onPick={pick}><ProofA /></CandidateFrame>
          <CandidateFrame id="proof-b" area="proof" code="PROOF B" title="회전 메시지" component="animated-testimonials · @manuarora700 (Aceternity)" source="21st.dev" sourceUrl="https://21st.dev/@manuarora700/components/animated-testimonials" interaction="단어별 등장 + 자동재생 + 이전/다음" mobile="1열, 6초 간격" license="MIT (키 수령 후 실제 코드 반입 예정)" picks={picks} onPick={pick}><ProofB /></CandidateFrame>
        </section>

        <section id="area-cta" className="scroll-mt-24 space-y-4">
          <AreaHead label="CTA · 상담 연결" desc={AREAS[4].desc} />
          <CandidateFrame id="cta-a" area="cta" code="CTA A" title="마퀴 스트립 + sticky 상담바" component="cta-with-text-marquee 계열 · @lyanchouss 참조" source="21st.dev" sourceUrl="https://21st.dev/community/components/s/marquee" interaction="마퀴 + tel: 직결 sticky 바" mobile="sticky 바 우선 설계" license="자작 (스펙 참조)" picks={picks} onPick={pick}><CtaA /></CandidateFrame>
        </section>
      </main>

      <footer className="border-t border-border bg-white px-4 py-8">
        <div className="mx-auto max-w-5xl text-center">
          <p className="text-sm text-muted-foreground">Gallery는 Preview 전용 · production·/·/home 변경 없음</p>
          <p className="mt-1 text-xs text-muted-foreground">실제 21st.dev 코드는 API 키 수령 후 반입 (현재 스펙 기반 구현 + 출처 표기)</p>
        </div>
      </footer>

      <div className="sticky bottom-0 border-t border-border bg-ink px-4 py-3 text-white">
        <div className="mx-auto flex max-w-5xl flex-col gap-1 sm:flex-row sm:items-center">
          <p className="text-xs font-bold">현재 선택: <span className="font-medium text-white/80">{summary}</span></p>
          <p className="text-[11px] text-white/60 sm:ml-auto">예) “HERO B + SCHOOL C + UPDATE A” 라고 알려주세요</p>
        </div>
      </div>
    </div>
  );
}

function AreaHead({ label, desc }: { label: string; desc: string }) {
  return (
    <div>
      <h2 className="text-xl font-extrabold sm:text-2xl">{label}</h2>
      <p className="mt-1 text-sm text-muted-foreground">{desc}</p>
    </div>
  );
}
