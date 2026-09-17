// ---------------------------------------------------------------------------
// SCHOOL B — real 21st.dev code build.
// Source: 21st.dev @ruixen.ui/coverflow-carousel (component id 23997),
//   MIT License. Retrieved via `21st get`.
// Preserved verbatim mechanics: fractional-pos ring-fold loop (no clones),
// DOM-direct paint (no per-frame re-render), pointer drag with velocity +
// flick carry (max 2 cards), rAF exponential settle, keyboard arrows,
// ResizeObserver measure, tilt/depth/falloff/fade math, caption/pagination.
// Gallery adaptations (behavior-neutral):
//  - added optional onSelect(index) callback (fires where setSelected fires)
//  - card media slot renders eduholic stills; theme tokens mapped to gallery
//    palette (bg-white/ink/accent); caption rendered by the wrapper below
//    with real Wiki/analysis links (links live outside the drag surface).
// ---------------------------------------------------------------------------
import * as React from 'react';
import { ArrowRight, ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';
import { LIVE, eduholic } from '@/data/eduholic';
import { VideoThumb } from '@/components/VideoThumb';

const useIsoLayoutEffect =
  typeof window !== 'undefined' ? React.useLayoutEffect : React.useEffect;

export interface CoverflowSlide {
  key: string;
  label: string;
  seek: number;
  title: string;
  subtitle: string;
}

interface CoverflowCarouselProps {
  slides: CoverflowSlide[];
  rotate?: number;
  depth?: number;
  perspective?: number;
  falloff?: number;
  fade?: number;
  cardWidth?: string;
  gap?: number;
  loop?: boolean;
  showPagination?: boolean;
  showNavigation?: boolean;
  label?: string;
  className?: string;
  onSelect?: (index: number) => void;
  /** Custom card media (default: eduholic video still). Gallery adaptation. */
  cardMedia?: (slide: CoverflowSlide) => React.ReactNode;
}

export function CoverflowCarousel({
  slides,
  rotate = 44,
  depth = 0.6,
  perspective = 3,
  falloff = 0.56,
  fade = 0.1,
  cardWidth = 'clamp(148px, 22vw, 260px)',
  gap = 0.05,
  loop = true,
  showPagination = false,
  showNavigation = false,
  label = 'Cover carousel',
  className,
  onSelect,
  cardMedia,
}: CoverflowCarouselProps) {
  const count = slides.length;

  const frameRef = React.useRef<HTMLDivElement>(null);
  const cardRefs = React.useRef<(HTMLDivElement | null)[]>([]);
  const posRef = React.useRef(0);
  const targetRef = React.useRef(0);
  const widthRef = React.useRef(0);
  const rafRef = React.useRef<number | null>(null);
  const onSelectRef = React.useRef(onSelect);
  onSelectRef.current = onSelect;
  const dragRef = React.useRef<{
    id: number;
    x: number;
    pos: number;
    v: number;
    t: number;
  } | null>(null);

  const [selected, setSelected] = React.useState(0);

  const indexAt = React.useCallback(
    (pos: number) => ((Math.round(pos) % count) + count) % count,
    [count],
  );

  const paint = React.useCallback(() => {
    const width = widthRef.current;
    if (!width) return;
    const pitch = width * (1 + gap);
    const pos = posRef.current;

    cardRefs.current.forEach((card, index) => {
      if (!card) return;

      let offset = index - pos;
      if (loop) {
        offset = ((offset % count) + count) % count;
        if (offset > count / 2) offset -= count;
      }

      const distance = Math.abs(offset);
      const ramp = Math.pow(distance, falloff);
      const tilt = Math.min(rotate * ramp, 82) * Math.sign(offset);

      card.style.transform =
        `translateX(calc(-50% + ${offset * pitch}px)) ` +
        `translateZ(${-depth * width * ramp}px) rotateY(${-tilt}deg)`;

      const edge = loop ? Math.min(1, Math.max(0, count / 2 - distance)) : 1;
      card.style.opacity = String(Math.max(0, 1 - fade * distance) * edge);
      card.style.zIndex = String(100 - Math.round(distance));
    });
  }, [count, depth, fade, falloff, gap, loop, rotate]);

  const emitSelect = React.useCallback(
    (index: number) => {
      setSelected(index);
      onSelectRef.current?.(index);
    },
    [],
  );

  const settle = React.useCallback(
    (target: number) => {
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
      targetRef.current = target;
      emitSelect(indexAt(target));

      const step = () => {
        const remaining = target - posRef.current;
        if (Math.abs(remaining) < 0.0004) {
          posRef.current = target;
          paint();
          rafRef.current = null;
          return;
        }
        posRef.current += remaining * 0.16;
        paint();
        rafRef.current = requestAnimationFrame(step);
      };
      rafRef.current = requestAnimationFrame(step);
    },
    [indexAt, paint, emitSelect],
  );

  const clampPos = React.useCallback(
    (pos: number) => (loop ? pos : Math.max(0, Math.min(count - 1, pos))),
    [count, loop],
  );

  const goTo = React.useCallback(
    (index: number) => {
      const target = loop
        ? index + Math.round((targetRef.current - index) / count) * count
        : index;
      settle(clampPos(target));
    },
    [clampPos, count, loop, settle],
  );

  const nudge = React.useCallback(
    (by: number) => settle(clampPos(Math.round(targetRef.current) + by)),
    [clampPos, settle],
  );

  const onPointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if (rafRef.current !== null) {
      cancelAnimationFrame(rafRef.current);
      rafRef.current = null;
    }
    event.currentTarget.setPointerCapture(event.pointerId);
    targetRef.current = posRef.current;
    dragRef.current = {
      id: event.pointerId,
      x: event.clientX,
      pos: posRef.current,
      v: 0,
      t: performance.now(),
    };
  };

  const onPointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    if (!drag || drag.id !== event.pointerId) return;

    const pitch = widthRef.current * (1 + gap);
    if (!pitch) return;

    const now = performance.now();
    const previous = posRef.current;
    posRef.current = clampPos(drag.pos - (event.clientX - drag.x) / pitch);
    drag.v = ((posRef.current - previous) / Math.max(now - drag.t, 1)) * 1000;
    drag.t = now;

    const index = indexAt(posRef.current);
    if (index !== selected) emitSelect(index);
    paint();
  };

  const endDrag = (event: React.PointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    if (!drag || drag.id !== event.pointerId) return;
    dragRef.current = null;
    const carried = Math.max(-2, Math.min(2, drag.v * 0.18));
    settle(clampPos(Math.round(posRef.current + carried)));
  };

  useIsoLayoutEffect(() => {
    const frame = frameRef.current;
    if (!frame) return;

    const measure = () => {
      const card = cardRefs.current[0];
      if (!card) return;
      widthRef.current = card.offsetWidth;
      paint();
    };

    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(frame);
    return () => observer.disconnect();
  }, [paint]);

  React.useEffect(
    () => () => {
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    },
    [],
  );

  return (
    <div
      className={cn('w-full', className)}
      style={{ '--cf-card': cardWidth } as React.CSSProperties}
      role="region"
      aria-roledescription="carousel"
      aria-label={label}
    >
      <div className="relative">
        <div
          ref={frameRef}
          tabIndex={0}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={endDrag}
          onPointerCancel={endDrag}
          onKeyDown={(event) => {
            if (event.key === 'ArrowLeft') {
              event.preventDefault();
              nudge(-1);
            } else if (event.key === 'ArrowRight') {
              event.preventDefault();
              nudge(1);
            }
          }}
          className="cursor-grab overflow-hidden py-10 outline-none focus-visible:ring-2 focus-visible:ring-accent active:cursor-grabbing"
          style={{
            perspective: `calc(var(--cf-card) * ${perspective})`,
            touchAction: 'pan-y',
          }}
        >
          <div
            className="relative select-none"
            style={{
              height: 'var(--cf-card)',
              transformStyle: 'preserve-3d',
            }}
          >
            {slides.map((slide, index) => (
              <div
                key={slide.key}
                ref={(node) => {
                  cardRefs.current[index] = node;
                }}
                role="group"
                aria-roledescription="slide"
                aria-label={`${index + 1} of ${count}`}
                className="absolute left-1/2 top-0 aspect-square overflow-hidden rounded-2xl bg-navy shadow-xl will-change-transform"
                style={{ width: 'var(--cf-card)' }}
              >
                {cardMedia ? (
                  cardMedia(slide)
                ) : (
                  <VideoThumb seek={slide.seek} label={slide.label} className="h-full w-full" />
                )}
              </div>
            ))}
          </div>
        </div>

        {showNavigation && (
          <>
            <button
              type="button"
              aria-label="Previous slide"
              onClick={() => nudge(-1)}
              className="absolute left-3 top-1/2 z-[200] -translate-y-1/2 rounded-full bg-white/80 p-2 text-ink backdrop-blur transition hover:bg-white"
            >
              <ChevronLeft className="h-5 w-5" />
            </button>
            <button
              type="button"
              aria-label="Next slide"
              onClick={() => nudge(1)}
              className="absolute right-3 top-1/2 z-[200] -translate-y-1/2 rounded-full bg-white/80 p-2 text-ink backdrop-blur transition hover:bg-white"
            >
              <ChevronRight className="h-5 w-5" />
            </button>
          </>
        )}
      </div>

      {showPagination && (
        <div className="mt-6 flex items-center justify-center gap-2">
          {slides.map((slide, index) => (
            <button
              key={slide.key}
              type="button"
              aria-label={`Go to slide ${index + 1}`}
              aria-current={index === selected}
              onClick={() => goTo(index)}
              className={cn(
                'h-2 rounded-full bg-white transition-all',
                index === selected ? 'w-6 opacity-100' : 'w-2 opacity-30',
              )}
            />
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * SCHOOL B wrapper — real eduholic school LIVE content on the coverflow.
 * Links live below the drag surface (no drag/click conflict).
 */
export function SchoolB() {
  const schools = eduholic.schools;
  // Carousel position starts at 0 — keep caption in sync from the same index.
  const [active, setActive] = React.useState(0);
  const s = schools[active];

  const slides: CoverflowSlide[] = schools.map((sc, i) => ({
    key: sc.name,
    label: `${sc.name} 내신 자료`,
    seek: 1 + i * 4,
    title: `${sc.name} · ${sc.grade}`,
    subtitle: sc.scope,
  }));

  return (
    <div className="overflow-hidden bg-ink px-4 py-8">
      <p className="text-center text-xs font-extrabold uppercase tracking-widest text-accent-light">학교별 내신 대비</p>
      <h2 className="mt-1 text-center text-xl font-extrabold text-white sm:text-2xl">2학기 영어 내신 LIVE</h2>
      <CoverflowCarousel
        slides={slides}
        label="학교별 내신 대비 커버플로우"
        showPagination
        showNavigation
        onSelect={setActive}
      />
      <div className="mx-auto -mt-2 max-w-xl text-center" aria-live="polite">
        <p className="text-sm text-white/85">
          <strong className="text-white">{s.name} · {s.grade}</strong> — {s.scope} · {s.point}
        </p>
        <div className="mt-3 flex gap-2">
          <a href={s.wiki} target="_blank" rel="noreferrer" className="flex flex-1 items-center justify-center gap-1 rounded-xl bg-white px-4 py-2.5 text-xs font-bold text-ink">
            시험범위 Wiki 보기 <ArrowRight className="h-3.5 w-3.5" />
          </a>
          <a href={`${LIVE}${s.analysis}`} target="_blank" rel="noreferrer" className="flex-1 rounded-xl border border-white/30 px-4 py-2.5 text-xs font-bold text-white">
            출제 분석 보기 →
          </a>
        </div>
      </div>
      <p className="mt-3 text-center text-[11px] text-white/50">← 드래그 / 스와이프 / 방향키로 넘기기 →</p>
    </div>
  );
}
