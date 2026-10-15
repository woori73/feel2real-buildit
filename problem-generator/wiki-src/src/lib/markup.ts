// 기존 HTML의 esc()/stripChoicePrefix()/normalizeUnderlineMarkup()/renderMarkup() 를 이식.
// (원본의 마지막 정리용 정규식은 [[u]]...[[/u]] 쌍 자체를 지워버리는 결함이 있어,
//  밑줄이 실제로 보이도록 그 부분만 제외하고 나머지 로직은 그대로 옮겼다.)

export const escHtml = (x: unknown) => String(x == null ? '' : x).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' } as Record<string, string>)[c])

export const stripChoicePrefix = (s: string) => String(s || '').replace(/^\s*(?:[①②③④⑤]|[1-5][.)]|\([1-5]\))\s*/, '')

// 문제3(5차): 이미 올바르게 짝지어진 [[u]]...[[/u]] 구간은 "복구용" 정규식이 건드리지 못하도록
// 임시 토큰으로 보호한 뒤, 나머지(손상된 변형)만 복구하고 마지막에 원래대로 되돌린다.
// 기존 코드는 정상 쌍까지 복구 정규식이 부분적으로 다시 매치해 [[u]]/[[/u]] 토큰이 화면에 그대로
// 새어나가는 결함이 있었다 (예: "①knows[[/u]]").
export const normalizeUnderlineMarkup = (s: string) => {
  let x = String(s || '').replace(/<u>/gi, '[[u]]').replace(/<\/u>/gi, '[[/u]]')
  const guarded: string[] = []
  x = x.replace(/\[\[u\]\]([\s\S]*?)\[\[\/u\]\]/g, (_match, inner: string) => {
    guarded.push(inner)
    return `\u0000${guarded.length - 1}\u0000`
  })
  x = x.replace(/\[\[u\]\]([^\n]{1,180}?)\[\/u\]/gi, '[[u]]$1[[/u]]')
  x = x.replace(/\[\[u\]\]([^\n]{1,180}?)\]\](?=\s|[.,!?;:]|$)/g, '[[u]]$1[[/u]]')
  x = x.replace(/([①②③④⑤])\s*\[([^\[\]\n]{1,120}?)\]\]/g, '$1[[u]]$2[[/u]]')
  x = x.replace(/\u0000(\d+)\u0000/g, (_match, idx: string) => `[[u]]${guarded[Number(idx)]}[[/u]]`)
  return x
}

export const renderMarkupHtml = (s: string) => {
  let x = escHtml(stripChoicePrefix(normalizeUnderlineMarkup(s)))
  x = x.replace(/\[\[u\]\]([\s\S]*?)\[\[\/u\]\]/g, '<u>$1</u>')
  // 안전장치: 위 복구 로직으로도 짝을 못 찾은 손상된 토큰은 사용자 화면에 그대로 노출하지 않고 제거한다.
  x = x.replace(/\[\[\/?u\]\]/g, '')
  x = x.replace(/(^|\s)([①②③④⑤])(?=\s|$)/g, '$1<span class="position-mark">$2</span>')
  return x
}

export const plainMarkup = (s: string) => stripChoicePrefix(normalizeUnderlineMarkup(s)).replace(/\[\[u\]\]([\s\S]*?)\[\[\/u\]\]/g, '$1').replace(/\[\[\/?u\]\]/g, '')

export const isInlineUnderline = (questionType: string, stem: string, sourceText: string) => {
  if (/(?:grammar|vocab)_underlined/i.test(questionType || '')) return true
  const hits = (sourceText || '').match(/\[\[u\]\]/g) || []
  return /밑줄/.test(stem || '') && hits.length >= 3
}
