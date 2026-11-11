export const PAPER_ACCEPT = '.html,.htm,.pdf,text/html,application/pdf'
export const VERIFICATION_ACCEPT = '.md,.markdown,.txt,text/markdown,text/plain'
export const MAX_ARTIFACT_BYTES = 30 * 1024 * 1024

export function readExamArtifact(file: File, verification = false): Promise<string> {
  const supported = verification ? /\.(md|markdown|txt)$/i : /\.(html?|pdf)$/i
  if (!supported.test(file.name)) return Promise.reject(new Error(verification ? '제작·검수는 MD·TXT 파일을 선택해 주세요.' : '문제지·해설지는 HTML 또는 PDF 파일을 선택해 주세요.'))
  if (!file.size || file.size > MAX_ARTIFACT_BYTES) return Promise.reject(new Error('파일당 30MB 이하의 비어 있지 않은 파일을 선택해 주세요.'))
  const isPdf = !verification && /\.pdf$/i.test(file.name)
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const content = String(reader.result || '')
      // MIME이 누락된 PDF도 서버에 동일한 binary envelope로 보낸다.
      resolve(isPdf ? 'data:application/pdf;base64,' + content.slice(content.indexOf(',') + 1) : content)
    }
    reader.onerror = () => reject(new Error(`${file.name} 파일을 읽지 못했습니다. 파일을 다시 선택해 주세요.`))
    if (isPdf) reader.readAsDataURL(file)
    else reader.readAsText(file)
  })
}
