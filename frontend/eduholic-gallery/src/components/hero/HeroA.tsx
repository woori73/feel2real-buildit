// ---------------------------------------------------------------------------
// HERO A — real 21st.dev code build.
// Source: 21st.dev @gughigug/scroll-locked-video-hero (component id 25500),
//   MIT License, author Guglielmo Giannattasio. Retrieved via `21st get`.
// Preserved verbatim mechanics: body-pin lock, wheel/touch input driving
// video.currentTime both directions, rAF smoothing (0.18 lerp), seek queue,
// iOS kickstart play-pause, scale 1→1.06, title blur-fade, tagline payoff at
// 0.82–1.0, progress bar, scroll-hint hide, bounce keyframes, overlays.
// Gallery adaptations (behavior-neutral outside the demo frame):
//  - lock is scoped to the in-view section: engaging when the hero reaches
//    the viewport top, releasing at progress 1 (scroll down) / 0 (scroll up)
//    so the rest of the gallery stays reachable; re-locks on return.
//    (Matches the author's documented intent in the original header comment.)
//  - touchAction toggles none/pan-y with the lock so swipes work when free.
//  - font stack switched to Pretendard; content defaulted to real eduholic
//    hero (principal-intro.mp4); end-CTA appears at completion (>0.95).
//  - author signature off in-demo (attribution lives in the gallery label).
// ---------------------------------------------------------------------------
import { useEffect, useRef, useState } from 'react';
import { eduholic } from '@/data/eduholic';

const SANS = "'Pretendard Variable', 'Pretendard', system-ui, sans-serif";
const COL_BG = '#05070d';
const COL_TEXT = '#f2f4f8';

function clamp(v: number, min: number, max: number) {
  return Math.min(max, Math.max(min, v));
}

export function HeroA({
  videoSrc = eduholic.heroVideo,
  title = eduholic.academy,
  scrollHint = 'SCROLL · 스크롤',
  tagline = '2학기 영어 내신 LIVE — 학교별 실제 자료로 만드는 예상문제',
  scrubDistance = 2600,
}: {
  videoSrc?: string;
  title?: string;
  scrollHint?: string;
  tagline?: string;
  scrubDistance?: number;
}) {
  const sectionRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const titleRef = useRef<HTMLDivElement>(null);
  const hintRef = useRef<HTMLDivElement>(null);
  const taglineRef = useRef<HTMLDivElement>(null);
  const progressBarRef = useRef<HTMLDivElement>(null);
  const ctaRef = useRef<HTMLDivElement>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const videoEl = videoRef.current;
    const sectionEl = sectionRef.current;
    if (!videoEl || !sectionEl) return;
    const video: HTMLVideoElement = videoEl;
    const section: HTMLDivElement = sectionEl;

    const reduceMotion =
      typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

    let duration = 0;
    let rafId = 0;
    let targetProgress = 0;
    let currentProgress = 0;
    let hasStartedScrolling = false;
    let isSeeking = false;
    let pendingTime: number | null = null;
    let locked = false;
    let lockedScrollY = 0;
    let touchStartY = 0;

    const onLoadedData = () => {
      duration = video.duration || 0;
      setReady(true);
      if (reduceMotion) {
        video.currentTime = duration * 0.92;
      }
    };
    video.addEventListener('loadeddata', onLoadedData);

    // Original iOS kickstart: silent play-then-pause so seeks actually load.
    const kickstartLoad = () => {
      const p = video.play();
      if (p && typeof p.then === 'function') {
        p.then(() => video.pause()).catch(() => {});
      } else {
        video.pause();
      }
    };
    kickstartLoad();

    const onSeeked = () => {
      isSeeking = false;
      if (pendingTime !== null) {
        const t = pendingTime;
        pendingTime = null;
        isSeeking = true;
        video.currentTime = t;
      }
    };
    video.addEventListener('seeked', onSeeked);

    function seekTo(t: number) {
      if (isSeeking) {
        pendingTime = t;
        return;
      }
      isSeeking = true;
      video.currentTime = t;
    }

    function engageLock() {
      if (locked || typeof document === 'undefined') return;
      locked = true;
      lockedScrollY = window.scrollY;
      const b = document.body.style;
      b.position = 'fixed';
      b.top = `-${lockedScrollY}px`;
      b.left = '0';
      b.right = '0';
      b.width = '100%';
      b.height = '100%';
      b.overscrollBehavior = 'none';
      section.style.touchAction = 'none';
    }

    function releaseLock() {
      if (!locked || typeof document === 'undefined') return;
      locked = false;
      const y = lockedScrollY;
      const b = document.body.style;
      b.position = '';
      b.top = '';
      b.left = '';
      b.right = '';
      b.width = '';
      b.height = '';
      b.overscrollBehavior = '';
      section.style.touchAction = 'pan-y';
      window.scrollTo(0, y);
    }

    function addDelta(deltaY: number) {
      const next = clamp(targetProgress + deltaY / scrubDistance, 0, 1);
      targetProgress = next;
      if (targetProgress > 0.001) hasStartedScrolling = true;
    }

    const atEnd = () => targetProgress >= 0.999;
    const atStart = () => targetProgress <= 0.001;

    const onWheel = (e: WheelEvent) => {
      if (!locked) return;
      // Release valves (author's documented intent): end + push forward,
      // or start + pull back, hands control back to the page.
      if ((atEnd() && e.deltaY > 0) || (atStart() && e.deltaY < 0)) {
        releaseLock();
        return;
      }
      addDelta(e.deltaY);
      e.preventDefault();
    };

    const onTouchStart = (e: TouchEvent) => {
      touchStartY = e.touches[0]?.clientY ?? 0;
    };
    const onTouchMove = (e: TouchEvent) => {
      if (!locked) return;
      const y = e.touches[0]?.clientY ?? touchStartY;
      const deltaY = touchStartY - y;
      touchStartY = y;
      if ((atEnd() && deltaY > 0) || (atStart() && deltaY < 0)) {
        releaseLock();
        return;
      }
      addDelta(deltaY);
      e.preventDefault();
    };

    // Engage when the hero fills the viewport top while scrolling freely.
    const onScroll = () => {
      if (locked) return;
      const r = section.getBoundingClientRect();
      if (r.top <= 0 && r.bottom >= window.innerHeight) {
        engageLock();
      }
    };

    window.addEventListener('wheel', onWheel, { passive: false });
    window.addEventListener('touchstart', onTouchStart, { passive: true });
    window.addEventListener('touchmove', onTouchMove, { passive: false });
    // Capture on the section itself (original iOS race fix).
    section.addEventListener('touchstart', onTouchStart, { passive: true, capture: true });
    section.addEventListener('touchmove', onTouchMove, { passive: false, capture: true });
    window.addEventListener('scroll', onScroll, { passive: true });
    // In case the gallery loads already scrolled onto the hero.
    onScroll();

    function frame() {
      currentProgress += (targetProgress - currentProgress) * 0.18;

      if (duration > 0) {
        seekTo(currentProgress * duration);
      }

      if (videoRef.current) {
        const scale = 1 + currentProgress * 0.06;
        videoRef.current.style.transform = `scale(${scale})`;
      }
      if (titleRef.current) {
        const t = 1 - clamp(currentProgress / 0.35, 0, 1);
        titleRef.current.style.opacity = String(t);
        titleRef.current.style.transform = `translateY(${(1 - t) * -24}px) scale(${0.96 + t * 0.04})`;
        titleRef.current.style.filter = `blur(${(1 - t) * 10}px)`;
      }
      if (hintRef.current) {
        hintRef.current.style.opacity = hasStartedScrolling ? '0' : '1';
      }
      if (taglineRef.current) {
        const t = clamp((currentProgress - 0.82) / 0.18, 0, 1);
        taglineRef.current.style.opacity = String(t);
        taglineRef.current.style.transform = `translateY(${(1 - t) * 20}px) scale(${0.97 + t * 0.03})`;
        taglineRef.current.style.filter = `blur(${(1 - t) * 8}px)`;
      }
      if (ctaRef.current) {
        const t = clamp((currentProgress - 0.95) / 0.05, 0, 1);
        ctaRef.current.style.opacity = String(t);
        ctaRef.current.style.pointerEvents = t > 0.5 ? 'auto' : 'none';
        ctaRef.current.style.transform = `translateY(${(1 - t) * 12}px)`;
      }
      if (progressBarRef.current) {
        progressBarRef.current.style.transform = `scaleX(${currentProgress})`;
      }

      rafId = requestAnimationFrame(frame);
    }

    if (!reduceMotion) {
      rafId = requestAnimationFrame(frame);
    }

    return () => {
      video.removeEventListener('loadeddata', onLoadedData);
      video.removeEventListener('seeked', onSeeked);
      window.removeEventListener('wheel', onWheel);
      window.removeEventListener('touchstart', onTouchStart);
      window.removeEventListener('touchmove', onTouchMove);
      section.removeEventListener('touchstart', onTouchStart, true);
      section.removeEventListener('touchmove', onTouchMove, true);
      window.removeEventListener('scroll', onScroll);
      cancelAnimationFrame(rafId);
      releaseLock();
    };
  }, [scrubDistance]);

  return (
    <div
      ref={sectionRef}
      style={{
        position: 'relative',
        height: '100dvh',
        width: '100%',
        overflow: 'hidden',
        background: COL_BG,
        touchAction: 'pan-y',
      }}
    >
      <video
        ref={videoRef}
        src={videoSrc}
        poster={eduholic.principalPhoto}
        muted
        playsInline
        preload="auto"
        style={{
          position: 'absolute',
          inset: 0,
          width: '100%',
          height: '100%',
          objectFit: 'cover',
          opacity: ready ? 1 : 0,
          transformOrigin: 'center center',
          willChange: 'transform',
          transition: 'opacity 0.6s ease',
          touchAction: 'none',
          pointerEvents: 'none',
        }}
      />

      <div
        style={{
          position: 'absolute',
          inset: 0,
          background:
            'linear-gradient(180deg, rgba(5,7,13,0.35), rgba(5,7,13,0) 30%, rgba(5,7,13,0.15) 70%, rgba(5,7,13,0.55))',
          pointerEvents: 'none',
        }}
      />

      <div
        ref={titleRef}
        style={{
          position: 'absolute',
          inset: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexDirection: 'column',
          gap: 12,
          padding: '0 6%',
          textAlign: 'center',
          pointerEvents: 'none',
        }}
      >
        <span
          style={{
            fontFamily: SANS,
            fontWeight: 800,
            fontSize: 'clamp(30px, 7vw, 96px)',
            lineHeight: 1.05,
            letterSpacing: '-0.02em',
            color: COL_TEXT,
            textShadow: '0 4px 30px rgba(0,0,0,0.5)',
            display: 'inline-block',
            willChange: 'transform, filter, opacity',
          }}
        >
          {title}
        </span>
        <span
          style={{
            fontFamily: SANS,
            fontWeight: 600,
            fontSize: 'clamp(13px, 2vw, 20px)',
            color: 'rgba(242,244,248,0.8)',
            textShadow: '0 2px 16px rgba(0,0,0,0.5)',
          }}
        >
          {eduholic.director} · 2학기 영어 내신 LIVE
        </span>
      </div>

      {tagline && (
        <div
          ref={taglineRef}
          style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '0 8%',
            textAlign: 'center',
            opacity: 0,
            pointerEvents: 'none',
          }}
        >
          <span
            style={{
              fontFamily: SANS,
              fontWeight: 700,
              fontSize: 'clamp(20px, 3.4vw, 40px)',
              lineHeight: 1.2,
              letterSpacing: '-0.01em',
              color: COL_TEXT,
              textShadow: '0 4px 24px rgba(0,0,0,0.5)',
            }}
          >
            {tagline}
          </span>
        </div>
      )}

      <div
        ref={ctaRef}
        style={{
          position: 'absolute',
          left: 0,
          right: 0,
          bottom: 'clamp(56px, 12vh, 96px)',
          display: 'flex',
          justifyContent: 'center',
          opacity: 0,
          pointerEvents: 'none',
        }}
      >
        <a
          href={`tel:${eduholic.cta.phone.replace(/-/g, '')}`}
          style={{
            fontFamily: SANS,
            fontWeight: 800,
            fontSize: 'clamp(14px, 2vw, 17px)',
            color: '#0a0a0c',
            background: '#fff',
            borderRadius: 999,
            padding: '12px 28px',
            textDecoration: 'none',
            boxShadow: '0 8px 32px rgba(0,0,0,0.45)',
          }}
        >
          {eduholic.cta.counsel} · {eduholic.cta.phone}
        </a>
      </div>

      <div
        ref={hintRef}
        style={{
          position: 'absolute',
          left: '50%',
          bottom: 'clamp(20px, 6vh, 48px)',
          transform: 'translateX(-50%)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 8,
          color: 'rgba(240,244,248,0.75)',
          fontFamily: SANS,
          fontSize: 'clamp(10px, 1.4vw, 12px)',
          fontWeight: 600,
          letterSpacing: '0.3em',
          transition: 'opacity 0.4s ease',
          pointerEvents: 'none',
        }}
      >
        <span>{scrollHint}</span>
        <svg width="14" height="18" viewBox="0 0 14 18" style={{ animation: 'metro-hero-bounce 1.6s ease-in-out infinite' }}>
          <style>{`
            @keyframes metro-hero-bounce {
              0%, 100% { transform: translateY(0); opacity: 0.5; }
              50% { transform: translateY(5px); opacity: 1; }
            }
          `}</style>
          <path d="M7 1 L7 17 M2 12 L7 17 L12 12" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </div>

      {/* Thin progress line — fills as the video advances. */}
      <div
        style={{
          position: 'absolute',
          left: 0,
          right: 0,
          bottom: 0,
          height: 2,
          background: 'rgba(255,255,255,0.12)',
        }}
      >
        <div
          ref={progressBarRef}
          style={{
            height: '100%',
            width: '100%',
            background: 'linear-gradient(90deg, rgba(255,255,255,0.5), rgba(255,255,255,0.95))',
            transform: 'scaleX(0)',
            transformOrigin: 'left center',
          }}
        />
      </div>
    </div>
  );
}
