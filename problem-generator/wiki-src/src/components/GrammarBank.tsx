import { useCallback, useEffect, useState } from 'react'

// 내신위키 문법 문제은행 — Spring API(/api/problem-generator/grammar-bank)를 직접 사용한다.
// 기존 questions 테이블 + grammar_question_profiles 재활용 관리 화면.

const BASE = '/api/problem-generator/grammar-bank'

type Tag = { tagId: number; nameKo: string; nameEn?: string; aliases?: string; sortOrder: number }
type Item = {
  questionId: number; schoolLevel: string; questionFormat?: string; difficulty?: string
  reviewStatus: string; rightsStatus: string; classificationConfidence?: number
  tags: { tagId: number; nameKo: string; primary: boolean }[]; stemExcerpt: string
  correctAnswer?: string; materialId?: number; sourcePosition?: string; externalRef?: string
}
type Detail = Item & {
  questionType: string; keywords?: string; questionText: string; choices: string[]
  explanation?: string; passageTitle?: string; passageContent?: string
  materialFileName?: string; autoClassification?: string
  variants: { variantId: number; variantQuestionId: number; status: string; changeReason?: string }[]
}
type Stats = {
  total: number; needsReview: number; approved: number; rejected: number
  bySchoolLevel: { level: string; labelKo: string; count: number }[]
  byGrammarTag: { tagId: number; nameKo: string; count: number }[]
}
type Collection = {
  collectionId: number; kind: string; title: string; itemCount: number; designcraftJobId?: number
  items?: { questionId: number; orderNum: number; stemExcerpt: string; tags: string[] }[]
}
type ImportResult = { total: number; created: number; duplicatesSkipped: number; heldForReview: number; failed: number; isNew: boolean; errors: string[] }

const LEVELS = ['', 'M1', 'M2', 'M3', 'H1', 'H2', 'H3', 'COMMON', 'UNKNOWN']
const LEVEL_LABELS: Record<string, string> = { '': '학년 전체', M1: '중1', M2: '중2', M3: '중3', H1: '고1', H2: '고2', H3: '고3', COMMON: '공통', UNKNOWN: '미확정' }
const FORMATS = ['', 'MULTIPLE_CHOICE', 'CORRECT_SENTENCE', 'INCORRECT_SENTENCE', 'UNDERLINE', 'BLANK_FILL', 'SENTENCE_ORDER', 'WRITING', 'SENTENCE_TRANSFORM', 'ERROR_CORRECTION']
const FORMAT_LABELS: Record<string, string> = { '': '형식 전체', MULTIPLE_CHOICE: '객관식', CORRECT_SENTENCE: '어법상 옳은', INCORRECT_SENTENCE: '어법상 틀린', UNDERLINE: '밑줄 어법', BLANK_FILL: '빈칸 완성', SENTENCE_ORDER: '문장 배열', WRITING: '영작·서술형', SENTENCE_TRANSFORM: '문장 전환', ERROR_CORRECTION: '오류 수정' }
const REVIEW_LABELS: Record<string, string> = { AUTO: '자동 분류', NEEDS_REVIEW: '검수 필요', APPROVED: '승인됨', REJECTED: '반려' }

async function req<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(BASE + path, { credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, ...init })
  if (!response.ok) {
    let message = `요청 실패 (${response.status})`
    try { message = (await response.json()).message || message } catch { /* HTML 오류는 상태 코드만 노출 */ }
    if (response.status === 401 || response.status === 403) message = '로그인이 필요합니다. 내신위키에 로그인한 뒤 다시 시도해 주세요.'
    throw new Error(message)
  }
  return response.status === 204 ? (undefined as T) : response.json()
}

export default function GrammarBank() {
  const [tab, setTab] = useState<'dashboard' | 'search' | 'review' | 'basket' | 'books' | 'import' | 'ai'>('dashboard')
  const [stats, setStats] = useState<Stats | null>(null)
  const [tags, setTags] = useState<Tag[]>([])
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')

  const [level, setLevel] = useState('')
  const [tagId, setTagId] = useState('')
  const [format, setFormat] = useState('')
  const [keyword, setKeyword] = useState('')
  const [onlyReview, setOnlyReview] = useState(false)
  const [results, setResults] = useState<Item[]>([])
  const [total, setTotal] = useState(0)
  const [detail, setDetail] = useState<Detail | null>(null)
  const [editText, setEditText] = useState('')
  const [editAnswer, setEditAnswer] = useState('')
  const [editExplain, setEditExplain] = useState('')
  const [selected, setSelected] = useState<number[]>([])
  const [collections, setCollections] = useState<Collection[]>([])
  const [basketTitle, setBasketTitle] = useState('선택함')
  const [importFile, setImportFile] = useState<File | null>(null)
  const [importMaterialId, setImportMaterialId] = useState('')
  const [importResult, setImportResult] = useState<ImportResult | null>(null)
  const [aiRequest, setAiRequest] = useState('')
  const [aiResult, setAiResult] = useState<{ usedAi: boolean; totalFound: number; answer: string; questions: Item[] } | null>(null)

  const notify = (message: string) => { setNotice(message); setError('') }
  const fail = (e: unknown) => { setError(e instanceof Error ? e.message : '요청에 실패했습니다.'); setNotice('') }

  const runSearch = useCallback(async () => {
    const params = new URLSearchParams()
    if (level) params.set('schoolLevel', level)
    if (tagId) params.set('tagIds', tagId)
    if (format) params.set('questionFormat', format)
    if (keyword) params.set('search', keyword)
    if (onlyReview) params.set('reviewStatus', 'NEEDS_REVIEW')
    params.set('size', '50')
    const page = await req<{ content: Item[]; totalElements: number }>(`/questions?${params}`)
    setResults(page.content); setTotal(page.totalElements)
  }, [level, tagId, format, keyword, onlyReview])

  useEffect(() => {
    req<Tag[]>('/tags').then(setTags).catch(fail)
    req<Stats>('/stats').then(setStats).catch(fail)
    req<Collection[]>('/collections').then(setCollections).catch(() => undefined)
  }, [tab])
  useEffect(() => { if (tab === 'search') void runSearch().catch(fail) }, [tab, runSearch])

  const openDetail = async (id: number) => {
    const d = await req<Detail>(`/questions/${id}`)
    setDetail(d); setEditText(d.questionText); setEditAnswer(d.correctAnswer || ''); setEditExplain(d.explanation || '')
  }
  const saveEdit = async () => {
    if (!detail) return
    await req<Detail>(`/questions/${detail.questionId}`, { method: 'PATCH', body: JSON.stringify({ questionText: editText, correctAnswer: editAnswer, explanation: editExplain }) })
    notify(`QID ${detail.questionId} 문제를 저장했습니다.`); setDetail(await req<Detail>(`/questions/${detail.questionId}`))
  }
  const saveTags = async (ids: number[]) => {
    if (!detail) return
    await req<Detail>(`/questions/${detail.questionId}/profile`, { method: 'PATCH', body: JSON.stringify({ tagIds: ids }) })
    notify('문법 태그를 수정했습니다(수동 수정 → 승인 처리).'); setDetail(await req<Detail>(`/questions/${detail.questionId}`))
  }
  const review = async (id: number, status: 'APPROVED' | 'REJECTED') => {
    await req<Detail>(`/questions/${id}/review`, { method: 'POST', body: JSON.stringify({ status }) })
    notify(status === 'APPROVED' ? '승인했습니다.' : '반려했습니다.')
    if (tab === 'review') void loadReviewQueue(); else void runSearch().catch(fail)
    if (detail?.questionId === id) setDetail(await req<Detail>(`/questions/${id}`))
  }
  const loadReviewQueue = async () => {
    const page = await req<{ content: Item[] }>(`/questions?reviewStatus=NEEDS_REVIEW&size=100`)
    setResults(page.content); setTotal(page.content.length)
  }

  const toggleSelected = (id: number) => setSelected(ids => ids.includes(id) ? ids.filter(x => x !== id) : [...ids, id])
  const createBasket = async () => {
    const c = await req<Collection>('/collections', { method: 'POST', body: JSON.stringify({ kind: 'BASKET', title: basketTitle, questionIds: selected }) })
    notify(`선택함 #${c.collectionId}에 ${selected.length}문제를 담았습니다.`); setSelected([])
    setCollections(await req<Collection[]>('/collections'))
  }
  const addExisting = async (collectionId: number) => {
    await req<Collection>(`/collections/${collectionId}/items`, { method: 'POST', body: JSON.stringify({ questionIds: selected }) })
    notify(`${selected.length}문제를 선택함 #${collectionId}에 추가했습니다.`); setSelected([])
  }
  const promoteBook = async (collectionId: number) => {
    const book = await req<Collection>(`/collections/${collectionId}/book`, { method: 'POST', body: JSON.stringify({ title: `${basketTitle} 문제집` }) })
    notify(`문제집 #${book.collectionId}을(를) 만들었습니다.`); setCollections(await req<Collection[]>('/collections'))
  }
  const runImport = async () => {
    if (!importFile) { fail(new Error('CSV/JSON 파일을 선택해 주세요.')); return }
    const text = await importFile.text()
    const material = importMaterialId ? Number(importMaterialId) : undefined
    let result: ImportResult
    if (importFile.name.endsWith('.json')) {
      const records = JSON.parse(text)
      result = await req<ImportResult>('/import', { method: 'POST', body: JSON.stringify({ materialId: material, records: Array.isArray(records) ? records : records.records }) })
    } else {
      result = await req<ImportResult>('/import/csv', { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: text })
    }
    setImportResult(result)
    notify(`수입 완료: 신규 ${result.created}건(검수 보류 ${result.heldForReview}) · 중복 건너뜀 ${result.duplicatesSkipped}건 · 실패 ${result.failed}건`)
  }
  const runAi = async () => {
    setAiResult(await req<{ usedAi: boolean; totalFound: number; answer: string; questions: Item[] }>('/ai/find', { method: 'POST', body: JSON.stringify({ request: aiRequest }) }))
  }

  const tabs: { id: typeof tab; label: string }[] = [
    { id: 'dashboard', label: '대시보드' }, { id: 'search', label: '문제 검색' }, { id: 'review', label: '자동 분류 검수' },
    { id: 'basket', label: '문제 선택함' }, { id: 'books', label: '문제집 제작' }, { id: 'import', label: '자료 수입' }, { id: 'ai', label: 'AI 검색' },
  ]

  return <section className="work-panel grammar-bank">
    <div className="panel-heading">
      <div><p className="kicker">GRAMMAR QUESTION BANK</p><h2>영어 문법 문제은행</h2>
        <p>내신위키 문제 DB(<code>questions</code>) 위에 학년·문법 유형·형식 분류를 얹은 공유 문제은행입니다.</p></div>
      <div className="twin-actions"><button className="secondary-button" onClick={() => { req<Stats>('/stats').then(setStats).catch(fail) }}>새로고침</button></div>
    </div>
    <nav className="state-actions" aria-label="문법 문제은행 탭" style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
      {tabs.map(t => <button key={t.id} className={tab === t.id ? 'primary-action' : 'secondary-button'} style={{ marginTop: 0 }}
        onClick={() => { setTab(t.id); if (t.id === 'review') void loadReviewQueue().catch(fail) }}>{t.label}</button>)}
    </nav>
    {notice && <div className="tool-notice" role="status">{notice}<button onClick={() => setNotice('')} aria-label="닫기">×</button></div>}
    {error && <div className="tool-notice" role="alert">{error}<button onClick={() => setError('')} aria-label="닫기">×</button></div>}

    {tab === 'dashboard' && <div>
      <div className="state-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 8 }}>
        {[['전체 문항', stats?.total], ['검수 필요', stats?.needsReview], ['승인', stats?.approved], ['반려(기본 제외)', stats?.rejected]].map(([label, value]) =>
          <div key={String(label)}><span>{label}</span><b>{value ?? '-'}</b></div>)}
      </div>
      <h3>학년별</h3>
      <div className="state-grid">{(stats?.bySchoolLevel || []).map(row =>
        <button key={row.level} className="secondary-button" style={{ marginTop: 0 }} onClick={() => { setLevel(row.level); setTab('search') }}>{row.labelKo} · {row.count}</button>)}</div>
      <h3>문법 유형별</h3>
      <div className="state-grid">{(stats?.byGrammarTag || []).map(row =>
        <button key={row.tagId} className="secondary-button" style={{ marginTop: 0 }} onClick={() => { setTagId(String(row.tagId)); setTab('search') }}>{row.nameKo} · {row.count}</button>)}</div>
    </div>}

    {(tab === 'search' || tab === 'review') && <div>
      <div className="exam-settings" style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <label>학년<select value={level} onChange={e => setLevel(e.target.value)}>{LEVELS.map(v => <option key={v} value={v}>{LEVEL_LABELS[v]}</option>)}</select></label>
        <label>문법 유형<select value={tagId} onChange={e => setTagId(e.target.value)}><option value="">유형 전체</option>{tags.map(t => <option key={t.tagId} value={t.tagId}>{t.nameKo}</option>)}</select></label>
        <label>형식<select value={format} onChange={e => setFormat(e.target.value)}>{FORMATS.map(v => <option key={v} value={v}>{FORMAT_LABELS[v]}</option>)}</select></label>
        <label>키워드<input value={keyword} onChange={e => setKeyword(e.target.value)} placeholder="발문·키워드" /></label>
        {tab === 'search' && <label className="checkbox"><input type="checkbox" checked={onlyReview} onChange={e => setOnlyReview(e.target.checked)} />검수 필요만</label>}
        <button className="primary-action" style={{ marginTop: 0 }} onClick={() => void runSearch().catch(fail)}>검색</button>
      </div>
      <p>검색 결과 {total}건 · 선택 {selected.length}개</p>
      <div className="db-list">{results.map(item => <article key={item.questionId} style={{ flexWrap: 'wrap' }}>
        <input type="checkbox" aria-label={`${item.questionId} 선택`} checked={selected.includes(item.questionId)} onChange={() => toggleSelected(item.questionId)} />
        <div><b>{LEVEL_LABELS[item.schoolLevel] || item.schoolLevel} · {item.tags.map(t => t.nameKo).join(', ')} · {FORMAT_LABELS[item.questionFormat || ''] || item.questionFormat || '형식 미분류'}</b>
          <p>{REVIEW_LABELS[item.reviewStatus]} · 신뢰도 {item.classificationConfidence ?? '-'} · 원본 {item.materialId ? `파일 #${item.materialId}${item.sourcePosition ? ` (${item.sourcePosition})` : ''}` : '직접 등록'}</p>
          <small>{item.stemExcerpt}</small></div>
        <button onClick={() => void openDetail(item.questionId).catch(fail)}>상세·원본</button>
        <button onClick={() => void review(item.questionId, 'APPROVED').catch(fail)}>승인</button>
        <button onClick={() => void review(item.questionId, 'REJECTED').catch(fail)}>반려</button>
      </article>)}</div>
      {selected.length > 0 && <button className="primary-action" onClick={() => void createBasket().catch(fail)}>선택함으로 보내기 ({selected.length})</button>}

      {detail && <div className="work-panel" style={{ border: '1px solid #dfe9f7', borderRadius: 14, padding: 12 }}>
        <h3>QID {detail.questionId} · {REVIEW_LABELS[detail.reviewStatus]} {detail.rightsStatus && `· 권한 ${detail.rightsStatus}`}</h3>
        {detail.passageContent && <p><b>지문{detail.passageTitle ? ` · ${detail.passageTitle}` : ''}:</b> {detail.passageContent.slice(0, 400)}{detail.passageContent.length > 400 ? '…' : ''}</p>}
        {detail.materialFileName && <p>원본 파일: {detail.materialFileName} {detail.sourcePosition ? `· 위치 ${detail.sourcePosition}` : ''}</p>}
        {detail.autoClassification && <p><small>자동 분류 근거: {detail.autoClassification}</small></p>}
        <label>발문<textarea rows={3} value={editText} onChange={e => setEditText(e.target.value)} style={{ width: '100%' }} /></label>
        {detail.choices.length > 0 && <p>선택지: {detail.choices.join(' / ')}</p>}
        <label>정답<input value={editAnswer} onChange={e => setEditAnswer(e.target.value)} /></label>
        <label>해설<textarea rows={2} value={editExplain} onChange={e => setEditExplain(e.target.value)} style={{ width: '100%' }} /></label>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {tags.map(t => <label key={t.tagId} style={{ fontSize: 12 }}>
            <input type="checkbox" checked={detail.tags.some(x => x.tagId === t.tagId)} onChange={e =>
              saveTags(e.target.checked ? [...detail.tags.map(x => x.tagId), t.tagId] : detail.tags.filter(x => x.tagId !== t.tagId).map(x => x.tagId)).catch(fail)
            } /> {t.nameKo}</label>)}
        </div>
        <button className="primary-action" onClick={() => void saveEdit().catch(fail)}>문제·정답·해설 저장</button>
        <button className="secondary-button" onClick={() => setDetail(null)}>닫기</button>
        {detail.variants.length > 0 && <p>변형본 이력: {detail.variants.map(v => `#${v.variantId} ${v.status}`).join(' · ')}</p>}
      </div>}
    </div>}

    {tab === 'basket' && <div>
      <label>선택함 이름<input value={basketTitle} onChange={e => setBasketTitle(e.target.value)} /></label>
      {selected.length > 0 && <button className="primary-action" onClick={() => void createBasket().catch(fail)}>선택한 {selected.length}개로 새 선택함</button>}
      <div className="material-list">{collections.filter(c => c.kind === 'BASKET').map(c => <article key={c.collectionId}>
        <div><b>{c.title}</b><p>선택함 #{c.collectionId} · {c.itemCount}문제</p></div>
        {selected.length > 0 && <button onClick={() => void addExisting(c.collectionId).catch(fail)}>현재 선택 추가</button>}
        <button onClick={() => void promoteBook(c.collectionId).catch(fail)}>문제집으로 승격</button>
      </article>)}{collections.filter(c => c.kind === 'BASKET').length === 0 && <div className="empty-state"><b>선택함이 없습니다.</b><p>문제 검색에서 문항을 선택해 보내 주세요.</p></div>}</div>
    </div>}

    {tab === 'books' && <div>
      <p>문제집 원고를 내려받아 로컬 DesignCraft에서 조판하세요. (웹 DesignCraft 편집기는 현재 동작하지 않습니다.)</p>
      <div className="material-list">{collections.filter(c => c.kind === 'BOOK').map(c => <article key={c.collectionId}>
        <div><b>{c.title}</b><p>문제집 #{c.collectionId} · {c.itemCount}문제</p></div>
        <a className="primary-action" style={{ marginTop: 0 }} href={`${BASE}/collections/${c.collectionId}/manuscript.html`}>원고 HTML 내려받기</a>
        <a className="secondary-button" style={{ marginTop: 0 }} href={`${BASE}/collections/${c.collectionId}/manuscript.json`}>원고 JSON</a>
        <a className="secondary-button" style={{ marginTop: 0 }} href={`${BASE}/collections/${c.collectionId}/manuscript.md`}>원고 Markdown</a>
      </article>)}{collections.filter(c => c.kind === 'BOOK').length === 0 && <div className="empty-state"><b>문제집이 없습니다.</b><p>선택함에서 '문제집으로 승격'하세요.</p></div>}</div>
    </div>}

    {tab === 'import' && <div>
      <p>2,824개 파일 추출 결과(CSV/JSON)를 등록합니다. 헤더: stem, choices, answer, explanation, grammar_tags, school_level, format, difficulty, confidence, material?, page, question_number. 동일 문항 해시·같은 파일 재등록은 자동으로 건너뜁니다.</p>
      <input type="file" accept=".csv,.json,.txt" onChange={e => setImportFile(e.target.files?.[0] || null)} />
      <label>원본 파일 ID(materialId, 선택)<input value={importMaterialId} onChange={e => setImportMaterialId(e.target.value)} placeholder="예: 12" /></label>
      <button className="primary-action" onClick={() => void runImport().catch(fail)}>수입 실행</button>
      {importResult && <div className="state-grid">
        <div><span>전체</span><b>{importResult.total}</b></div><div><span>신규 등록</span><b>{importResult.created}</b></div>
        <div><span>검수 보류</span><b>{importResult.heldForReview}</b></div><div><span>중복 건너뜀</span><b>{importResult.duplicatesSkipped}</b></div>
        <div><span>실패</span><b>{importResult.failed}</b></div>
      </div>}
      {importResult && importResult.errors.length > 0 && <p><small>오류: {importResult.errors.join(' / ')}</small></p>}
    </div>}

    {tab === 'ai' && <div>
      <p>"중2 수동태 객관식 문제 30개 찾아줘" 같은 요청을 입력하면 필터로 해석해 <b>실제 DB 검색 결과만</b> 반환합니다.</p>
      <label>요청<input value={aiRequest} onChange={e => setAiRequest(e.target.value)} style={{ width: '100%' }} placeholder="중3 관계대명사 문제 중 어려운 문제 20개 선택해" /></label>
      <button className="primary-action" onClick={() => void runAi().catch(fail)}>AI 검색 실행</button>
      {aiResult && <div>
        <p>{aiResult.answer} {aiResult.usedAi ? '(AI 필터)' : '(키워드 매핑 필터)'}</p>
        <p>DB에서 찾은 문항 {aiResult.totalFound}건:</p>
        <div className="db-list">{aiResult.questions.map(item => <article key={item.questionId}>
          <div><b>{LEVEL_LABELS[item.schoolLevel] || item.schoolLevel} · {item.tags.map(t => t.nameKo).join(', ')}</b><small>{item.stemExcerpt}</small></div>
          <button onClick={() => { setDetail(null); toggleSelected(item.questionId); setTab('basket'); notify(`QID ${item.questionId}를 현재 선택에 포함했습니다.`) }}>선택에 포함</button>
        </article>)}</div>
        {selected.length > 0 && <button className="primary-action" onClick={() => void createBasket().catch(fail)}>AI 결과로 선택함 생성 ({selected.length})</button>}
      </div>}
    </div>}
  </section>
}
