import { describe, expect, it } from 'vitest'
import { renderFigureMarkers, renderMarkupHtml } from './markup'

describe('문항 그림 표시', () => {
  it('given /naesin/ 그림 표시 when 렌더링하면 then 같은 사이트 그림 태그로 바뀐다', () => {
    // given
    const text = '그림을 보고 답하시오.\n[[img:/naesin/singu-m2-2026-2/r01/fig-q08-1.webp]]'
    // when
    const html = renderMarkupHtml(text)
    // then
    expect(html).toContain('<img class="q-figure" src="/naesin/singu-m2-2026-2/r01/fig-q08-1.webp" alt="문항 그림" loading="lazy">')
    expect(html).not.toContain('[[img:')
  })

  it('given 다른 사이트·상위 폴더·스크립트 주소 when 렌더링하면 then 그림을 만들지 않고 지운다', () => {
    // given
    const bad = ['[[img:https://evil.example/x.png]]', '[[img:/naesin/../secret.png]]', '[[img:javascript:alert(1)]]', '[[img:/naesin/a.svg]]']
    // when
    const out = bad.map(renderFigureMarkers)
    // then
    out.forEach(html => { expect(html).not.toContain('<img'); expect(html).not.toContain('[[img:') })
  })

  it('given 그림 표시가 없는 글 when 렌더링하면 then 기존 결과가 그대로다', () => {
    // given
    const text = 'I <u>hear</u> you.'
    // when
    const html = renderMarkupHtml(text)
    // then
    expect(html).toBe('I <u>hear</u> you.')
  })
})
