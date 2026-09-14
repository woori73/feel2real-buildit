import * as React from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { ChevronRight } from 'lucide-react';

interface SchoolCardProps {
  title: string;
  description: string;
  tags: string[];
  href: string;
  icon?: React.ReactNode;
  coverLabel?: string;
  coverColor?: string;
}

export function SchoolCard({
  title,
  description,
  tags,
  href,
  icon,
  coverLabel = 'Card',
  coverColor = 'from-accent-soft to-blue-100',
}: SchoolCardProps) {
  return (
    <article className="group">
      <Card className="h-full overflow-hidden border-border transition-all duration-300 hover:shadow-xl hover:border-accent hover:-translate-y-1">
        <a href={href} className="block h-full">
          <div className="relative aspect-video w-full overflow-hidden bg-gradient-to-br" style={{ background: coverColor }}>
            {icon && (
              <div className="absolute inset-0 flex items-center justify-center text-navy/20">
                {React.isValidElement(icon) ? React.cloneElement(icon as React.ReactElement, { className: 'w-16 h-16' }) : icon}
              </div>
            )}
            <div className="absolute inset-0 flex items-center justify-center">
              <span className="text-sm font-extrabold uppercase tracking-wider text-navy">
                {coverLabel}
              </span>
            </div>
          </div>

          <CardContent className="p-5 flex flex-col h-full">
            <h3 className="text-lg font-bold text-foreground mb-2 group-hover:text-accent transition-colors">
              {title}
            </h3>
            <p className="text-sm text-muted-foreground leading-relaxed flex-1 mb-3">
              {description}
            </p>

            <div className="flex flex-wrap gap-2 mb-4">
              {tags.map((tag) => (
                <Badge key={tag} variant="accent" className="text-xs px-2 py-1">
                  {tag}
                </Badge>
              ))}
            </div>

            <div className="flex items-center justify-between pt-2 border-t border-border/50">
              <span className="text-xs text-muted-foreground">자세히 보기</span>
              <div className="group-hover:translate-x-1 transition-transform">
                <ChevronRight className="w-5 h-5 text-accent" aria-hidden="true" />
              </div>
            </div>
          </CardContent>
        </a>
      </Card>
    </article>
  );
}