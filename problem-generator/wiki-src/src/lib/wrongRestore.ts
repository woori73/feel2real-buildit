// 학생 오답 사진 → AI 복원 문제지(HTML) 1차 구현 헬퍼.
// 학교 프린트 2단계 복원기(school-prints/reconstruct)와 동일한 계약을 재사용한다:
// 브라우저 OCR 텍스트가 원문 기준(source of truth), 원본 이미지는 배치 기준.
// 승인 전까지는 서버에 아무것도 저장하지 않는다(1차: 미리보기/수정 → 승인 → 누적 저장).
import type { ExtractedPage } from './ocrExtract'

export const RESTORE_MODEL = 'qwen/qwen3.7-flash'

export type RestorePagePayload = { page: number; name: string; ocr_text: string; image_data_url: string }
export type RestoreRequestPayload = { model: string; metadata: Record<string, string>; pages: RestorePagePayload[] }

const MAX_IMAGE_EDGE = 2200

export const blobToDataUrl = (blob: Blob): Promise<string> => new Promise((resolve, reject) => {
  const reader = new FileReader()
  reader.onload = () => resolve(String(reader.result || ''))
  reader.onerror = () => reject(reader.error || new Error('이미지를 읽지 못했습니다.'))
  reader.readAsDataURL(blob)
})

// 모바일 원본 사진(5~8MB)은 data URL 그대로면 서버 이미지 한도를 넘을 수 있다.
// 학교 프린트 복원기와 같은 2200px 상한으로 통일해 전송 크기를 안정화한다.
export const pageImageDataUrl = async (page: ExtractedPage): Promise<string> => {
  if (page.previewUrl.startsWith('data:image/')) return page.previewUrl
  try {
    const img = new Image(); img.src = page.previewUrl; await img.decode()
    const scale = Math.min(1, MAX_IMAGE_EDGE / Math.max(img.width, img.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(img.width * scale))
    canvas.height = Math.max(1, Math.round(img.height * scale))
    const ctx = canvas.getContext('2d')
    if (!ctx) return ''
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
    return canvas.toDataURL('image/jpeg', 0.85)
  } catch {
    // 이미지를 못 읽어도 OCR 텍스트(source of truth)만으로 복원은 진행한다.
    return ''
  }
}

// 실패했거나 OCR 텍스트가 비어 있는 페이지는 요청에서 제외한다.
// 빈 OCR을 AI에 넣으면 임의 창작을 유도하므로 조용히 채우지 않고 제외가 UI에 드러나야 한다.
export const buildRestorePayload = async (pages: ExtractedPage[], metadata: Record<string, string> = {}): Promise<RestoreRequestPayload> => {
  const usable = pages.filter(page => page.ok && page.text.trim())
  const withImages = await Promise.all(usable.map(async page => ({ page, image: await pageImageDataUrl(page) })))
  return {
    model: RESTORE_MODEL,
    metadata,
    pages: withImages.map((item, index) => ({ page: index + 1, name: item.page.name, ocr_text: item.page.text, image_data_url: item.image })),
  }
}

// 판독 불확실 표시는 <mark>판독 필요: 원문 확인</mark> 텍스트로 약속한다.
// class 속성은 서버 sanitizer가 벗겨내지만 <mark>는 allowlist 태그라 정제 후에도 남는다.
export const countReviewMarks = (html: string): number => (String(html || '').match(/<mark\b/gi) || []).length

// 승인 저장 시 student_wrong_notes.wrong_text에 누적할 평문을 만든다(태그 제거, 블록 개행 유지).
export const htmlToPlainText = (html: string): string => {
  try {
    const doc = new DOMParser().parseFromString(`<div>${html}</div>`, 'text/html')
    doc.querySelectorAll('script,style,noscript').forEach(node => node.remove())
    doc.querySelectorAll('li,p,div,tr,br,h1,h2,h3,h4,h5,h6,section,article').forEach(node => node.after('\n'))
    return (doc.body.textContent || '')
      .replace(/\u00a0/g, ' ')
      .replace(/[ \t]+/g, ' ')
      .replace(/\n{3,}/g, '\n\n')
      .trim()
  } catch {
    return String(html || '').replace(/<[^>]+>/g, ' ').trim()
  }
}
