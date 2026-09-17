import { useEffect, useRef, useState } from 'react';
import { ArrowRight } from 'lucide-react';
import { cn } from '@/lib/utils';
import { LIVE, eduholic } from '@/data/eduholic';
import { CoverflowCarousel, type CoverflowSlide } from '@/components/school/SchoolB';

const tel = 'tel:0263241453';

export interface SchoolDatum {
  name: string;
  grade: string;
  scope: string;
  point: string;
  wiki: string;
  analysis: string;
}

const SCHOOLS: SchoolDatum[] = [
  { name: '압구정중', grade: '2학년', scope: 'Lesson 5', point: '핵심 문법 · 간접의문문 · 5형식', wiki: eduholic.livePage, analysis: `${LIVE}/analyzes/11` },
  { name: '신구중', grade: '3학년', scope: 'Lesson 6', point: '학교 프린트 × 출제 패턴 분석', wiki: eduholic.livePage, analysis: `${LIVE}/analyzes/12` },
  { name: '신사중', grade: '3학년', scope: '핵심 문법 · used to', point: '시험범위 Wiki + 출제 분석', wiki: eduholic.livePage, analysis: `${LIVE}/analyzes/13` },
  { name: '언북중', grade: '3학년 · 1학기 · 기말', scope: '출제 경향', point: '출제자 분석 2건', wiki: eduholic.livePage, analysis: `${LIVE}/analyzes/10` },
];

function SchoolCard({ s }: { s: SchoolDatum }) {
  return (
    <div className="w-[270px] shrink-0 snap-center rounded-2xl border border-border bg-white p-5 shadow-sm sm:w-[300px]">
      <p className="flex items-baseline gap-2 border-b border-border pb-3">
        <span className="text-base font-extrabold">{s.name}</span>
        <span className="text-[11px] font-bold text-accent">학교별 준비 LIVE</span>
      </p>
      <p className="mt-3 inline-block rounded-full bg-accent-soft px-2.5 py-0.5 text-[11px] font-bold text-navy">시험범위</p>
      <p className="mt-2 text-sm font-extrabold">{s.name} · {s.grade}</p>
      <p className="mt-1 text-xs text-muted-foreground">{s.scope}</p>
      <p className="mt-1 text-xs text-muted-foreground">{s.point}</p>
      <a href={s.wiki} target="_blank" rel="noreferrer" className="mt-3 block text-xs font-bold text-accent">시험범위 Wiki 보기 →</a>
      <a href={s.analysis} target="_blank" rel="noreferrer" className="mt-2 block text-xs font-bold text-navy">출제 분석 보기 →</a>
    </div>
  );
}

function ZoneHead() {
  return (
    <div className="bg-[#faf6ec] px-4 pb-2 pt-8 text-center">
      <a href={eduholic.livePage} target="_blank" rel="noreferrer" className="inline-block rounded-full bg-navy px-5 py-2 text-xs font-bold text-white">
        2026 중간고사 LIVE →
      </a>
      <p className="mx-auto mt-3 max-w-xl text-sm text-muted-foreground">학교별 실제 자료를 읽고, 학교에 맞는 예상문제를 만듭니다.</p>
      <h2 className="mt-6 text-xl font-extrabold sm:text-2xl">학교별 내신 대비</h2>
    </div>
  );
}

/** CURRENT — live replica: tabs + snap row + 1/4 progress + counsel button. */
export function LiveCurrent() {
  const [active, setActive] = useState(0);
  const rowRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const row = rowRef.current;
    if (!row) return;
    const onScroll = () => {
      const i = Math.round(row.scrollLeft / (row.scrollWidth - row.clientWidth || 1) * (SCHOOLS.length - 1));
      setActive(Math.max(0, Math.min(SCHOOLS.length - 1, i)));
    };
    row.addEventListener('scroll', onScroll, { passive: true });
    return () => row.removeEventListener('scroll', onScroll);
  }, []);

  const jump = (i: number) => {
    const row = rowRef.current;
    const card = row?.children[i] as HTMLElement | undefined;
    card?.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
    setActive(i);
  };

  return (
    <div className="bg-[#faf6ec]">
      <ZoneHead />
      <div className="mx-auto max-w-5xl px-4">
        <div className="mt-4 flex justify-center gap-2" role="tablist" aria-label="학교 선택">
          {SCHOOLS.map((s, i) => (
            <button
              key={s.name}
              role="tab"
              aria-selected={i === active}
              onClick={() => jump(i)}
              className={cn(
                'rounded-full px-4 py-1.5 text-xs font-bold',
                i === active ? 'bg-navy text-white' : 'border border-border bg-white text-muted-foreground',
              )}
            >
              {s.name}
            </button>
          ))}
        </div>
        <div ref={rowRef} className="no-scrollbar mt-4 flex snap-x snap-mandatory gap-3 overflow-x-auto px-4 pb-2 sm:justify-center">
          {SCHOOLS.map((s) => (
            <SchoolCard key={s.name} s={s} />
          ))}
        </div>
        <div className="mt-2 flex items-center justify-center gap-2" aria-hidden>
          <span className="text-xs font-bold text-muted-foreground">{active + 1} / 4</span>
          <span className="flex gap-1">
            {SCHOOLS.map((s, i) => (
              <span key={s.name} className={cn('h-4 w-1.5 rounded-full', i === active ? 'bg-navy' : 'bg-border')} />
            ))}
          </span>
        </div>
        <div className="flex justify-center py-6">
          <a href={tel} className="rounded-xl bg-ink px-6 py-3 text-sm font-bold text-white">2학기 내신 상담 신청</a>
        </div>
      </div>
    </div>
  );
}

/** OPTION A — same cards, mobile-first snap swipe with dots + edge peek. */
export function LiveOptionA() {
  const [active, setActive] = useState(0);
  const rowRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const row = rowRef.current;
    if (!row) return;
    let raf = 0;
    const onScroll = () => {
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(() => {
        const cards = Array.from(row.children) as HTMLElement[];
        let best = 0;
        let bestD = Infinity;
        const c = row.scrollLeft + row.clientWidth / 2;
        cards.forEach((card, i) => {
          const d = Math.abs(card.offsetLeft + card.offsetWidth / 2 - c);
          if (d < bestD) {
            bestD = d;
            best = i;
          }
        });
        setActive(best);
      });
    };
    row.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      row.removeEventListener('scroll', onScroll);
      cancelAnimationFrame(raf);
    };
  }, []);

  return (
    <div className="bg-ink px-4 py-8">
      <p className="text-center text-xs font-extrabold uppercase tracking-widest text-accent-light">학교별 내신 대비 · swipe</p>
      <div
        ref={rowRef}
        className="no-scrollbar mt-4 flex snap-x snap-mandatory gap-4 overflow-x-auto px-[8vw] pb-2"
        style={{ touchAction: 'pan-x pan-y' }}
      >
        {SCHOOLS.map((s) => (
          <div key={s.name} className="w-[78vw] max-w-[320px] shrink-0 snap-center">
            <SchoolCard s={s} />
          </div>
        ))}
      </div>
      <div className="mt-3 flex justify-center gap-1.5">
        {SCHOOLS.map((s, i) => (
          <span key={s.name} className={cn('h-1.5 rounded-full transition-all', i === active ? 'w-6 bg-white' : 'w-1.5 bg-white/30')} />
        ))}
      </div>
      <p className="mt-2 text-center text-[11px] text-white/50">← 손가락으로 밀어서 넘기기 →</p>
    </div>
  );
}

/** OPTION B — real 21st coverflow code (id 23997, MIT) with typographic cards. No stock imagery. */
export function LiveOptionB() {
  // Carousel position starts at 0 — keep caption in sync from the same index.
  const [active, setActive] = useState(0);
  const s = SCHOOLS[active];
  const slides: CoverflowSlide[] = SCHOOLS.map((sc, i) => ({
    key: sc.name,
    label: `${sc.name} 내신 자료`,
    seek: 1 + i * 4,
    title: `${sc.name} · ${sc.grade}`,
    subtitle: sc.scope,
  }));

  return (
    <div className="overflow-hidden bg-ink px-4 py-8">
      <p className="text-center text-xs font-extrabold uppercase tracking-widest text-accent-light">학교별 내신 대비 · coverflow</p>
      <CoverflowCarousel
        slides={slides}
        label="학교별 내신 대비 커버플로우"
        showPagination
        showNavigation
        onSelect={setActive}
        cardMedia={(slide) => (
          <div className="flex h-full w-full flex-col justify-between bg-gradient-to-br from-navy-light via-navy to-navy-dark p-4">
            <p className="text-[11px] font-extrabold uppercase tracking-widest text-white/60">학교별 준비 LIVE</p>
            <div>
              <p className="break-keep text-2xl font-extrabold leading-snug text-white">{slide.title}</p>
              <p className="mt-1 text-xs font-semibold text-white/75">{slide.subtitle}</p>
            </div>
          </div>
        )}
      />
      <div className="mx-auto mt-4 max-w-xl text-center" aria-live="polite">
        <p className="text-sm text-white/85">
          <strong className="text-white">{s.name} · {s.grade}</strong> — {s.scope} · {s.point}
        </p>
        <div className="mt-3 flex gap-2">
          <a href={s.wiki} target="_blank" rel="noreferrer" className="flex flex-1 items-center justify-center gap-1 rounded-xl bg-white px-4 py-2.5 text-xs font-bold text-ink">
            시험범위 Wiki 보기 <ArrowRight className="h-3.5 w-3.5" />
          </a>
          <a href={s.analysis} target="_blank" rel="noreferrer" className="flex-1 rounded-xl border border-white/30 px-4 py-2.5 text-xs font-bold text-white">
            출제 분석 보기 →
          </a>
        </div>
      </div>
    </div>
  );
}
