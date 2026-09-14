import { Button } from '@/components/ui/button';
import { ArrowRight } from 'lucide-react';

interface CTASectionProps {
  title: string;
  description?: string;
  primaryCta: { label: string; href: string };
}

export function CTASection({ title, description, primaryCta }: CTASectionProps) {
  return (
    <section className="rounded-2xl overflow-hidden bg-gradient-to-br from-navy via-navy-dark to-navy-light shadow-xl animate-in" aria-labelledby="cta-title">
      <div className="grid gap-8 lg:grid-cols-[1fr_auto] lg:gap-12 items-center p-6 lg:p-10">
        <div className="text-center lg:text-left">
          <h2 id="cta-title" className="text-2xl lg:text-3xl xl:text-4xl font-extrabold leading-tight tracking-tight text-white text-balance">
            {title}
          </h2>
          {description && (
            <p className="mt-3 text-base lg:text-lg leading-relaxed text-white/70 max-w-md mx-auto lg:mx-0">
              {description}
            </p>
          )}
        </div>

        <div className="flex justify-center lg:justify-end">
          <Button
            size="lg"
            asChild
            className="w-full lg:w-auto min-w-[200px] bg-white text-navy hover:bg-white/90 shadow-xl hover:shadow-2xl"
          >
            <a href={primaryCta.href} className="flex items-center justify-center gap-2 px-6 py-3">
              {primaryCta.label}
              <ArrowRight className="w-5 h-5" aria-hidden="true" />
            </a>
          </Button>
        </div>
      </div>
    </section>
  );
}