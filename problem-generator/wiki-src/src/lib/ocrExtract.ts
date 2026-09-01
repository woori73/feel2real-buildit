const MAX_FILES = 12
// 페이지 상한을 두지 않는다. 예전 8쪽 하드컷이 55쪽 PDF를 조용히 8쪽으로 잘라 저장하던 원인이었다.
// 브라우저 메모리 보호용 안전장치만 높게 둔다 — 초과분은 조용히 버리지 않고 실패로 보고한다.
const PDF_PAGE_SAFETY_CAP = 400
const OCR_LANG = 'kor+eng'
export type ExtractedPage = { name: string; previewUrl: string; text: string; usedOcr: boolean; ok: boolean; error?: string }
export type ExtractionSummary = { total: number; succeeded: number; failed: number; failedPages: string[] }
const ensurePdfWorker = async () => { const pdfjsLib: typeof import('pdfjs-dist') = await import('pdfjs-dist'); const workerUrl = (await import('pdfjs-dist/build/pdf.worker.min.js?url')).default as string; pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl; return pdfjsLib }
type TesseractProgress = { status: string; progress: number }
const ocrCanvas = async (canvas: HTMLCanvasElement, label: string, onProgress?: (message: string) => void): Promise<string> => { const Tesseract = await import('tesseract.js'); const { data } = await Tesseract.recognize(canvas, OCR_LANG, { logger: (message: TesseractProgress) => { if (message.status === 'recognizing text' && onProgress) onProgress(`${label} 문자 인식 중 ${Math.round((message.progress || 0) * 100)}%`) } }); return (data.text || '').trim() }
const canvasFromImage = (img: HTMLImageElement, maxSize = 2200): HTMLCanvasElement => { const scale = Math.min(1, maxSize / Math.max(img.width, img.height)); const canvas = document.createElement('canvas'); canvas.width = Math.max(1, Math.round(img.width * scale)); canvas.height = Math.max(1, Math.round(img.height * scale)); const ctx = canvas.getContext('2d'); if (!ctx) throw new Error('이미지를 처리할 수 없습니다.'); ctx.drawImage(img, 0, 0, canvas.width, canvas.height); return canvas }
export const extractImageFile = async (file: File, onProgress?: (message: string) => void): Promise<ExtractedPage> => {
  const previewUrl = URL.createObjectURL(file)
  try {
    const img = new Image(); img.src = previewUrl; await img.decode()
    onProgress?.(`${file.name} 이미지를 읽는 중…`)
    const canvas = canvasFromImage(img)
    const text = await ocrCanvas(canvas, file.name, onProgress)
    return { name: file.name, previewUrl, text, usedOcr: true, ok: true }
  } catch (error) {
    return { name: file.name, previewUrl, text: '', usedOcr: true, ok: false, error: error instanceof Error ? error.message : '이미지 처리 실패' }
  }
}
const extractPdfPage = async (pdf: import('pdfjs-dist').PDFDocumentProxy, p: number, label: string, onProgress?: (message: string) => void): Promise<ExtractedPage> => {
  try {
    onProgress?.(`${label} 처리 중…`)
    const page = await pdf.getPage(p)
    const textContent = await page.getTextContent()
    const items = textContent.items as { str: string }[]
    const rawText = items.map(item => item.str).join(' ').replace(/\s+/g, ' ').trim()
    const viewport = page.getViewport({ scale: 1.6 })
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(viewport.width)); canvas.height = Math.max(1, Math.round(viewport.height))
    const ctx = canvas.getContext('2d')
    if (ctx) await page.render({ canvasContext: ctx, viewport }).promise
    const previewUrl = canvas.toDataURL('image/png')
    const needsOcr = rawText.replace(/\s/g, '').length < 30
    const text = needsOcr ? await ocrCanvas(canvas, label, onProgress) : rawText
    return { name: label, previewUrl, text, usedOcr: needsOcr, ok: true }
  } catch (error) {
    return { name: label, previewUrl: '', text: '', usedOcr: false, ok: false, error: error instanceof Error ? error.message : '페이지 추출 실패' }
  }
}
export const extractPdfFile = async (file: File, onProgress?: (message: string) => void): Promise<ExtractedPage[]> => {
  const pdfjsLib = await ensurePdfWorker()
  const buffer = await file.arrayBuffer()
  const pdf = await pdfjsLib.getDocument({ data: new Uint8Array(buffer), isEvalSupported: false }).promise
  const total = pdf.numPages
  const processable = Math.min(total, PDF_PAGE_SAFETY_CAP)
  const results: ExtractedPage[] = []
  for (let p = 1; p <= processable; p += 1) {
    results.push(await extractPdfPage(pdf, p, `${file.name} ${p}/${total}쪽`, onProgress))
  }
  // 안전장치 초과분은 사라진 것처럼 두지 않고 실패 페이지로 명시한다.
  for (let p = processable + 1; p <= total; p += 1) {
    results.push({ name: `${file.name} ${p}/${total}쪽`, previewUrl: '', text: '', usedOcr: false, ok: false, error: `한 번에 처리 가능한 최대 ${PDF_PAGE_SAFETY_CAP}쪽을 초과했습니다` })
  }
  return results
}
const isPdfFile = (file: File) => file.type === 'application/pdf' || /\.pdf$/i.test(file.name)
const isTextFile = (file: File) => file.type === 'text/plain' || /\.txt$/i.test(file.name)
export const extractFiles = async (files: File[], onProgress?: (message: string) => void): Promise<ExtractedPage[]> => {
  const out: ExtractedPage[] = []
  for (const file of files.slice(0, MAX_FILES)) {
    if (isPdfFile(file)) out.push(...await extractPdfFile(file, onProgress))
    else if (isTextFile(file)) { onProgress?.(`${file.name} 텍스트를 읽는 중…`); out.push({ name: file.name, previewUrl: '', text: await file.text(), usedOcr: false, ok: true }) }
    else out.push(await extractImageFile(file, onProgress))
  }
  return out
}
export const extractionSummary = (pages: ExtractedPage[]): ExtractionSummary => {
  const failed = pages.filter(page => !page.ok)
  return { total: pages.length, succeeded: pages.length - failed.length, failed: failed.length, failedPages: failed.map(page => page.name) }
}
export const summaryMessage = (summary: ExtractionSummary): string =>
  summary.failed === 0
    ? `전체 ${summary.total}쪽 모두 처리했습니다.`
    : `전체 ${summary.total}쪽 · 성공 ${summary.succeeded}쪽 · 실패 ${summary.failed}쪽 (${summary.failedPages.slice(0, 6).join(', ')}${summary.failedPages.length > 6 ? ' …' : ''})`
export const joinExtractedPages = (pages: ExtractedPage[]): string => pages.map(page => `──────── ${page.name} ────────\n${page.ok ? page.text : `[추출 실패: ${page.error || '알 수 없는 오류'}]`}`).join('\n\n')
export const acceptedFileTypes = 'image/jpeg,image/png,image/jpg,application/pdf,.jpg,.jpeg,.png,.pdf'
export const maxUploadFiles = MAX_FILES
