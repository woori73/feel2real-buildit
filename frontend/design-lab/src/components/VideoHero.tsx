import * as React from 'react';
import { Button } from '@/components/ui/button';
import { ExternalLink, Loader2 } from 'lucide-react';

interface VideoHeroProps {
  subtitle: string;
  primaryCta: { label: string; onClick: () => void };
  secondaryCta?: { label: string; onClick: () => void };
  videoUrl: string;
  videoTitle: string;
  caption?: {
    title: string;
    description: string;
    externalLink?: string;
  };
  trustLogos?: string[];
}

export function VideoHero({
  subtitle,
  primaryCta,
  secondaryCta,
  videoUrl,
  videoTitle,
  caption,
  trustLogos,
}: VideoHeroProps) {
  const [isLoading, setIsLoading] = React.useState(true);

  return (
    <section className="rounded-2xl overflow-hidden bg-gradient-to-br from-navy via-navy-dark to-navy-light shadow-xl animate-in stagger-1" aria-labelledby="hero-title">
      <div className="grid gap-8 lg:grid-cols-2 lg:gap-12">
        {/* Content Side */}
        <div className="p-6 lg:p-12 flex flex-col justify-center text-white">
          <div className="inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-4 py-2 animate-in stagger-2">
            <span className="w-2 h-2 rounded-full bg-green-400 shadow-[0_0_8px_rgba(34,197,94,0.6)]" />
            <span className="text-sm font-medium text-white/90">AI 기반 · 학교별 맞춤 내신 영어 학습</span>
          </div>

          <h1 id="hero-title" className="mt-6 text-3xl lg:text-4xl xl:text-5xl font-extrabold leading-tight tracking-tight animate-in stagger-3 text-balance">
            데이터로 더 정확하게,{' '}
            <br />
            AI로 더 스마트하게,{' '}
            <br />
            <span className="text-accent-light">성적을 현실로!</span>
          </h1>

          <p className="mt-6 text-base lg:text-lg leading-relaxed text-white/80 max-w-xl animate-in stagger-4">
            {subtitle}
          </p>

          <div className="mt-8 flex flex-col sm:flex-row gap-3 animate-in stagger-5">
            <Button size="lg" onClick={primaryCta.onClick} className="w-full sm:w-auto">
              {primaryCta.label}
            </Button>
            {secondaryCta && (
              <Button variant="outline" size="lg" onClick={secondaryCta.onClick} className="w-full sm:w-auto border-white/30 text-white hover:bg-white/10">
                {secondaryCta.label}
              </Button>
            )}
          </div>

          {trustLogos && trustLogos.length > 0 && (
            <div className="mt-10 pt-8 border-t border-white/10 animate-in stagger-6">
              <p className="text-xs font-semibold uppercase tracking-wider text-white/50 mb-3">이미 검증된 학원들과 함께합니다</p>
              <div className="flex flex-wrap gap-3 lg:gap-6 text-sm font-semibold text-white/70">
                {trustLogos.map((logo, i) => (
                  <span key={logo} className="animate-in" style={{ animationDelay: `${300 + i * 50}ms` }}>
                    {logo}
                  </span>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Video Side */}
        <div className="relative bg-[#0a1633] animate-in stagger-4">
          <div className="aspect-video relative overflow-hidden">
            {isLoading && (
              <div className="absolute inset-0 flex items-center justify-center">
                <Loader2 className="w-8 h-8 text-accent animate-spin" />
              </div>
            )}
            <iframe
              src={videoUrl}
              title={videoTitle}
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
              referrerPolicy="strict-origin-when-cross-origin"
              allowFullScreen
              loading="lazy"
              onLoad={() => setIsLoading(false)}
              className="absolute inset-0 w-full h-full border-0"
            />
          </div>

          {caption && (
            <div className="p-4 lg:p-6 border-t border-white/10 bg-[#0a1633]/50">
              <div className="flex flex-col gap-2">
                <p className="font-semibold text-white">{caption.title}</p>
                <p className="text-sm text-white/60">{caption.description}</p>
                {caption.externalLink && (
                  <a
                    href={caption.externalLink}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-2 mt-2 text-sm font-semibold text-accent-light hover:text-white transition-colors"
                  >
                    YouTube에서 보기 <ExternalLink className="w-4 h-4" />
                  </a>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}