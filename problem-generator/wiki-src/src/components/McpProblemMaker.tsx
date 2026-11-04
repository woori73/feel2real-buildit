import { useEffect, useMemo, useRef, useState } from 'react'
import type { Page } from '../App'
import { api } from '../lib/api'
import { fetchSchoolTree, SchoolNode } from '../lib/types'
import { ExamContext, loadExamContext, resolveSavedContext, saveExamContext } from '../lib/wikiContext'
import { buildMcpPrompt, ContextSummaryRow, JOB_LABELS, McpJob, McpJobKind, mcpEndpointUrl, summarizeFinalContext } from '../lib/mcpPrompt'
import '../styles/mcp-maker.css'

const years = [new Date().getFullYear(), new Date().getFullYear() - 1, new Date().getFullYear() - 2]
const EXAMS = [{ label: '전체', value: '전체' }, { label: '중간', value: '중간고사' }, { label: '기말', value: '기말고사' }]
const COUNTS = [20, 40, 70, 100]
const KINDS: McpJobKind[] = ['expected', 'jikbo']
// 학년은 자료 유무와 관계없이 항상 1~3학년을 고를 수 있다. 자료가 없으면 2단계에 "없음"으로 보인다.
const GRADES = ['1학년', '2학년', '3학년']
const firstGradeWithData = (node?: SchoolNode) => node?.grades.find(g => GRADES.includes(g.grade))?.grade || GRADES[0]

// MCP 문제제작 — 시험 고르기 → 자료 확인 → 만들 것 고르기 → 지시문 복사 → 결과 확인.
// 이 화면은 자료를 읽기만 한다(GET). 문제 생성·저장은 Claude가 MCP tool로 한다.
export default function McpProblemMaker({ onNavigate }: { onNavigate: (page: Page) => void }) {
  const [tree, setTree] = useState<SchoolNode[]>([])
  const [context, setContext] = useState<ExamContext>(() => ({ school: '', grade: '', year: years[0], semester: '1학기', exam: '전체', ...loadExamContext() }))
  const [rows, setRows] = useState<ContextSummaryRow[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [job, setJob] = useState<McpJob>({ kind: 'expected', count: 40, mix: 'school', saCount: 8, note: '' })
  const [copied, setCopied] = useState(false)
  const touched = useRef(false)

  useEffect(() => {
    fetchSchoolTree().then(nodes => {
      setTree(nodes)
      if (nodes.length && !touched.current) setContext(c => {
        const resolved = resolveSavedContext(c, nodes, years[0])
        const grade = resolved.school === c.school && GRADES.includes(c.grade) ? c.grade : firstGradeWithData(nodes.find(n => n.school === resolved.school))
        return { ...resolved, grade }
      })
    }).catch(() => setError('학교 목록을 불러오지 못했습니다.'))
  }, [])

  const ready = Boolean(context.school && context.grade)

  const loadContext = async (c: ExamContext) => {
    if (!c.school || !c.grade) return
    setLoading(true); setError('')
    try {
      const query = new URLSearchParams({ school: c.school, grade: c.grade, year: String(c.year), semester: c.semester, exam: c.exam }).toString()
      const response = await api(`workspaces/final/context?${query}`)
      if (!response.ok) throw new Error()
      setRows(summarizeFinalContext(await response.json()))
    } catch {
      setRows(null); setError('자료를 확인하지 못했습니다. 잠시 뒤 다시 확인해 주세요.')
    } finally { setLoading(false) }
  }

  useEffect(() => { saveExamContext(context); setCopied(false); void loadContext(context) }, [context.school, context.grade, context.year, context.semester, context.exam])

  const update = (patch: Partial<ExamContext>) => { touched.current = true; setContext(c => ({ ...c, ...patch })) }
  const updateJob = (patch: Partial<McpJob>) => { setCopied(false); setJob(j => ({ ...j, ...patch })) }
  const prompt = useMemo(() => buildMcpPrompt(context, job), [context, job])
  const missingEssential = rows?.filter(row => row.essential && row.count === 0) || []
  const endpoint = mcpEndpointUrl(window.location.origin)

  const copyPrompt = async () => {
    try { await navigator.clipboard.writeText(prompt); setCopied(true) } catch { setError('복사하지 못했습니다. 아래 글을 직접 선택해 복사해 주세요.') }
  }

  return <section className="mcp-maker" aria-label="MCP 문제 만들기">
    <details className="mcp-connect">
      <summary>처음 한 번: Claude에 사이트 연결하기</summary>
      <p>Claude의 커넥터 설정에서 아래 주소를 사용자 지정 커넥터로 추가하고, 이 사이트 계정으로 로그인해 연결합니다. 한 번 연결하면 다음부터는 2단계부터 하면 됩니다.</p>
      <div className="mcp-endpoint"><code>{endpoint}</code><button type="button" onClick={() => void navigator.clipboard?.writeText(endpoint)}>주소 복사</button></div>
    </details>

    <ol className="mcp-steps">
      <li className="mcp-step">
        <h2><span>1</span>시험 고르기</h2>
        <div className="mcp-fields">
          <label>학교<select value={context.school} onChange={e => update({ school: e.target.value, grade: firstGradeWithData(tree.find(n => n.school === e.target.value)) })}>{!tree.length && <option value="">학교 없음</option>}{tree.map(n => <option key={n.school}>{n.school}</option>)}</select></label>
          <label>학년<select value={context.grade} onChange={e => update({ grade: e.target.value })}>{GRADES.map(g => <option key={g}>{g}</option>)}</select></label>
          <label>연도<select value={context.year} onChange={e => update({ year: Number(e.target.value) })}>{years.map(y => <option key={y}>{y}</option>)}</select></label>
          <label>학기<select value={context.semester} onChange={e => update({ semester: e.target.value })}><option>1학기</option><option>2학기</option></select></label>
          <label>시험<select value={context.exam} onChange={e => update({ exam: e.target.value })}>{EXAMS.map(x => <option key={x.value} value={x.value}>{x.label}</option>)}</select></label>
        </div>
      </li>

      <li className="mcp-step">
        <h2><span>2</span>올라온 자료 확인<button type="button" className="mcp-link" disabled={!ready || loading} onClick={() => void loadContext(context)}>{loading ? '확인 중' : '다시 확인'}</button></h2>
        {error && <p className="mcp-error" role="alert">{error}</p>}
        {rows && <ul className="mcp-context" aria-label="자료 현황">{rows.map(row => <li key={row.key} className={row.count ? 'has' : 'none'}><b>{row.label}</b><span>{row.count ? `${row.count}건` : '없음'}</span></li>)}</ul>}
        {missingEssential.length > 0 && <p className="mcp-warn">아직 없는 자료: {missingEssential.map(r => r.label).join(', ')}. 먼저 <button type="button" className="mcp-link" onClick={() => onNavigate('state')}>문제·자료 업로드</button>에서 올리면 더 정확하게 만듭니다.</p>}
      </li>

      <li className="mcp-step">
        <h2><span>3</span>만들 것 고르기</h2>
        <div className="mcp-choice" role="radiogroup" aria-label="만들 것">{KINDS.map(kind => <button type="button" role="radio" aria-checked={job.kind === kind} key={kind} onClick={() => updateJob({ kind })}>{JOB_LABELS[kind]}</button>)}</div>
        {job.kind === 'expected' && <>
          <div className="mcp-choice" role="radiogroup" aria-label="문항 수">{COUNTS.map(count => <button type="button" role="radio" aria-checked={job.count === count} key={count} onClick={() => updateJob({ count })}>{count}문항</button>)}</div>
          <div className="mcp-fields">
            <label>문항 구성<select value={job.mix} onChange={e => updateJob({ mix: e.target.value as McpJob['mix'] })}><option value="school">학교 기출 비율대로</option><option value="mcOnly">모두 객관식</option><option value="custom">서답형 수 직접 정하기</option></select></label>
            {job.mix === 'custom' && <label>서답형 문항 수<input type="number" min={0} max={job.count} value={job.saCount} onChange={e => updateJob({ saCount: Number(e.target.value) })} /></label>}
          </div>
        </>}
        <label className="mcp-note">추가 요청 (선택)<textarea value={job.note} placeholder="예: Lesson 5~6 본문 위주, 서술형은 조건 영작으로" onChange={e => updateJob({ note: e.target.value })} /></label>
      </li>

      <li className="mcp-step">
        <h2><span>4</span>지시문 복사해서 Claude에 붙여 넣기</h2>
        <pre className="mcp-prompt" aria-label="MCP 작업 지시문">{prompt}</pre>
        <div className="mcp-actions">
          <button type="button" className="primary-action" disabled={!ready} onClick={() => void copyPrompt()}>{copied ? '복사했습니다' : '지시문 복사'}</button>
          <a className="secondary-button" href="https://claude.ai/new" target="_blank" rel="noreferrer">Claude 열기</a>
        </div>
      </li>

      <li className="mcp-step">
        <h2><span>5</span>저장된 결과 보기</h2>
        <p className="mcp-hint">Claude가 저장을 마치면 2단계 [다시 확인]을 누르세요. 저장된 문항 수가 늘어납니다.</p>
        <div className="mcp-actions">
          <button type="button" className="secondary-button" onClick={() => onNavigate('database')}>강사 문제 DB</button>
          <button type="button" className="secondary-button" onClick={() => onNavigate('gentests')}>생성 문제</button>
          <button type="button" className="secondary-button" onClick={() => onNavigate('exam')}>시험지로 묶기</button>
        </div>
      </li>
    </ol>
  </section>
}
