import { MapPin, Phone } from 'lucide-react';
import { eduholic } from '@/data/eduholic';

/**
 * TEMP — spec implementation, NOT 21st real code (awaiting retrieval).
 * Spec: 모바일 상담바 패턴 + @lyanchouss/cta-with-text-marquee 계열(21st.dev) 참조.
 * 실제 상담 전화·주소로 연결.
 */
export function CtaA() {
  const tel = `tel:${eduholic.cta.phone.replace(/-/g, '')}`;
  return (
    <div>
      <div className="overflow-hidden bg-amber-300 py-2">
        <div className="flex w-max gap-8 whitespace-nowrap pr-8 animate-gallery-marquee-fast">
          {[0, 1].map((c) => (
            <span key={c} className="text-xs font-extrabold text-ink" aria-hidden={c > 0}>
              2학기 내신 상담 신청 · {eduholic.cta.phone} · {eduholic.cta.address} · 2학기 내신 상담 신청 · {eduholic.cta.phone} · {eduholic.cta.address} ·&nbsp;
            </span>
          ))}
        </div>
      </div>
      <div className="bg-white px-4 py-8">
        <div className="mx-auto flex max-w-3xl flex-col items-center gap-3 text-center sm:flex-row sm:justify-between sm:text-left">
          <div>
            <p className="text-lg font-extrabold sm:text-2xl">2학기 내신 상담 신청</p>
            <p className="mt-1 flex items-center justify-center gap-1 text-xs text-muted-foreground sm:justify-start sm:text-sm">
              <MapPin className="h-3.5 w-3.5" /> {eduholic.cta.address} · {eduholic.cta.phone}
            </p>
          </div>
          <div className="flex w-full gap-2 sm:w-auto">
            <a href={tel} className="flex flex-1 items-center justify-center gap-1.5 whitespace-nowrap rounded-xl bg-navy px-4 py-3 text-sm font-bold text-white sm:flex-none sm:gap-2 sm:px-6">
              <Phone className="h-4 w-4 shrink-0" /> {eduholic.cta.counsel}
            </a>
            <a href={eduholic.livePage} target="_blank" rel="noreferrer" className="flex-1 whitespace-nowrap rounded-xl border border-border px-4 py-3 text-center text-sm font-bold sm:flex-none sm:px-6">대표 수업 보기</a>
          </div>
        </div>
      </div>
      {/* 화면 하단 고정 상담바 */}
      <div className="sticky bottom-0 border-t border-border bg-white/95 px-4 py-2 backdrop-blur">
        <div className="mx-auto flex max-w-xl gap-2">
          <a href={tel} className="flex flex-1 items-center justify-center gap-1.5 whitespace-nowrap rounded-xl bg-navy px-2 py-3 text-sm font-bold text-white">
            <Phone className="h-4 w-4 shrink-0" /> {eduholic.cta.counsel}
          </a>
          <a href={eduholic.livePage} target="_blank" rel="noreferrer" className="flex-1 whitespace-nowrap rounded-xl border border-border px-2 py-3 text-center text-sm font-bold">대표 수업 보기</a>
        </div>
        <p className="mt-1 text-center text-[10px] text-muted-foreground">↑ 화면 하단에 고정되는 상담바</p>
      </div>
    </div>
  );
}
