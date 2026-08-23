import { ChangeEvent, useEffect, useMemo, useState } from 'react'
import { api } from '../lib/api'
export type ImportedTwinProfile = { id?: number; version?: number; school: string; grade: string; exam: string; dna: string; description: string; types: { name: string; description: string; ratio: string; difficulty: string }[]; rules: string[]; difficulty: string; questionRatio: Record<string, string | number>; trapPatterns: string[]; choiceStyle: string; writingStyle: string; updatedAt: string; rawSource?: string }
type Selection = { school: string; grade: string }
type Props = { onApplied: (profile: ImportedTwinProfile) => void; selectedSchool?: Selection }
type UnknownRecord = Record<string, unknown>
const typeNames = ['어법', '내용일치', '빈칸추론', '문장삽입', '순서배열', '영영풀이', '영작', '서답형', '문법', '독해', '추론', '어휘']
const cssPattern = /:root\s*\{[^}]*\}|(?:--[\w-]+\s*:[^;{}]+;?|[\w-]+\s*:\s*(?:#[\w]+|rgb\([^)]*\)|[^;{}]+);?)/gi
const isJunk = (value: string) => /(^|\s)(:root|--[\w-]+|\.\w+\s*\{|<\/?style|<\/?script|@media|display\s*:|color\s*:|background\s*:|학교명 미확인)/i.test(value)
const clean = (value: unknown) => typeof value === 'string' ? value.replace(/<style[\s\S]*?<\/style>|<script[\s\S]*?<\/script>/gi, ' ').replace(cssPattern, ' ').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim() : ''
const usable = (value: unknown) => { const result = clean(value); return result && !isJunk(result) ? result : '' }
const cleanProfile = (profile: ImportedTwinProfile, selected?: Selection): ImportedTwinProfile => {
  const selectedSchool = usable(selected?.school)
  const selectedGrade = usable(selected?.grade)
  const school = selectedSchool || usable(profile.school) || '업로드 자료'
  const grade = selectedGrade || usable(profile.grade)
  return {
    ...profile,
    school,
    grade,
    exam: usable(profile.exam),
    dna: usable(profile.dna),
    description: usable(profile.description),
    difficulty: usable(profile.difficulty),
    rules: profile.rules.map(usable).filter(Boolean),
    types: profile.types.map(type => ({ name: usable(type.name), description: usable(type.description), ratio: usable(type.ratio), difficulty: usable(type.difficulty) })).filter(type => Boolean(type.name)),
    trapPatterns: profile.trapPatterns.map(usable).filter(Boolean),
    choiceStyle: usable(profile.choiceStyle),
    writingStyle: usable(profile.writingStyle),
  }
}
const parseLooseJson = (source: string): unknown => {
  try { return JSON.parse(source) } catch { return null }
}
const collectObjects = (value: unknown, depth = 0): UnknownRecord[] => {
  if (depth > 6 || !value) return []
  if (Array.isArray(value)) return value.flatMap(item => collectObjects(item, depth + 1))
  if (typeof value !== 'object') return []
  const record = value as UnknownRecord
  return [record, ...Object.values(record).flatMap(item => collectObjects(item, depth + 1))]
}
const findFirst = (records: UnknownRecord[], keys: string[]) => {
  for (const record of records) for (const key of keys) {
    const value = usable(record[key])
    if (value) return value
  }
  return ''
}
const toType = (value: unknown) => {
  if (typeof value === 'string') return { name: usable(value), description: '', ratio: '', difficulty: '' }
  if (!value || typeof value !== 'object') return null
  const item = value as UnknownRecord
  const name = usable(item.name) || usable(item.type) || usable(item.label) || usable(item.title)
  if (!name || isJunk(name)) return null
  return { name, description: usable(item.description) || usable(item.desc) || usable(item.detail), ratio: usable(item.ratio) || usable(item.frequency) || usable(item.rate), difficulty: usable(item.difficulty) || usable(item.level) }
}
const extractAssignment = (source: string, name: string) => {
  const marker = new RegExp(`(?:const|let|var)\\s+${name}\\s*=`, 'i').exec(source)
  if (!marker) return null
  const start = (marker.index || 0) + marker[0].length
  const opener = source.slice(start).match(/^\s*([\[{])/)
  if (!opener) return null
  const openIndex = start + (opener.index || 0)
  const open = opener[1]
  const close = open === '[' ? ']' : '}'
  let quote = ''; let escaped = false; let depth = 0
  for (let index = openIndex; index < source.length; index += 1) {
    const char = source[index]
    if (quote) { if (escaped) escaped = false; else if (char === '\\') escaped = true; else if (char === quote) quote = ''; continue }
    if (char === '"' || char === "'") { quote = char; continue }
    if (char === open) depth += 1
    if (char === close) { depth -= 1; if (depth === 0) return parseLooseJson(source.slice(openIndex, index + 1)) }
  }
  return null
}
const plainText = (source: string) => source
  .replace(/<style[\s\S]*?<\/style>|<script[\s\S]*?<\/script>/gi, ' ')
  .replace(/<[^>]*>/g, ' ')
  .replace(/\s+/g, ' ')
  .trim()
const inferTypes = (source: string) => typeNames.filter(name => new RegExp(name, 'g').test(source)).map(name => ({ name, description: '업로드 자료에서 감지한 출제 유형입니다.', ratio: '', difficulty: '' }))
const inferSchool = (source: string, fileName: string) => {
  const match = source.match(/([가-힣A-Za-z0-9]+(?:중학교|고등학교|중|고))/)
  return match?.[1] || fileName.replace(/\.[^.]+$/, '').replace(/[_-]+/g, ' ').trim() || '업로드 자료'
}
const normalize = (raw: UnknownRecord, source: string, fileName: string): ImportedTwinProfile => {
  const records = collectObjects(raw)
  const text = plainText(source)
  const school = findFirst(records, ['school', 'schoolName', '학교명']) || inferSchool(text, fileName)
  const grade = findFirst(records, ['grade', '학년']) || (text.match(/(중학교|고등학교)\s*[1-3]학년/)?.[0] || '')
  const exam = findFirst(records, ['exam', 'examName', '시험명']) || (text.match(/(중간고사|기말고사|모의고사)/)?.[0] || '')
  const dna = findFirst(records, ['dna', 'teacherDna', '출제DNA', '학교별 DNA']) || '업로드 자료를 기준으로 출제 특성을 분석합니다.'
  const description = findFirst(records, ['description', '출제특징', 'analysis', 'summary']) || text.slice(0, 240)
  const typeSource = raw.types || raw.TYPE_DEFS || records.find(record => Array.isArray(record.types))?.types || []
  const extractedTypes = (Array.isArray(typeSource) ? typeSource : []).map(toType).filter((item): item is NonNullable<typeof item> => Boolean(item))
  const types = extractedTypes.length ? extractedTypes : inferTypes(text)
  const rulesSource = raw.rules || raw.generationRules || records.find(record => Array.isArray(record.rules))?.rules || []
  const rules = (Array.isArray(rulesSource) ? rulesSource : []).map(usable).filter(Boolean)
  const questionRatio = (raw.questionRatio || raw.question_ratio || raw.ratios || {}) as Record<string, string | number>
  const trapSource = raw.trapPatterns || raw.trap_patterns || raw.traps || records.find(record => Array.isArray(record.trapPatterns))?.trapPatterns || []
  const trapPatterns = (Array.isArray(trapSource) ? trapSource : []).map(usable).filter(Boolean)
  const choiceStyle = findFirst(records, ['choiceStyle', 'choice_style', '선택지스타일', '선택지 스타일'])
  const writingStyle = findFirst(records, ['writingStyle', 'writing_style', '서답형스타일', '서술형 스타일', '서답형 스타일'])
  return { school, grade, exam, dna, description, types: types.length ? types : [{ name: '기출 분석 대기', description: '업로드한 자료의 문항을 분석해 유형을 확정합니다.', ratio: '', difficulty: '' }], rules: rules.length ? rules : ['업로드 자료의 문항 구성과 난이도를 기준으로 생성'], difficulty: findFirst(records, ['difficulty', '난이도']) || '업로드 자료 기준', questionRatio, trapPatterns, choiceStyle, writingStyle, updatedAt: new Date().toLocaleString('ko-KR'), rawSource: source.slice(0, 50000) }
}
const parseSource = (source: string, fileName: string) => {
  const parsed = parseLooseJson(source)
  if (parsed && typeof parsed === 'object') return normalize(parsed as UnknownRecord, source, fileName)
  const profiles = extractAssignment(source, 'SCHOOL_PROFILES')
  const types = extractAssignment(source, 'TYPE_DEFS')
  const traps = extractAssignment(source, 'TRAP_PATTERNS')
  const primary = Array.isArray(profiles) ? profiles[0] : profiles
  const combined: UnknownRecord = { ...(primary && typeof primary === 'object' ? primary as UnknownRecord : {}), TYPE_DEFS: types || [], trapPatterns: traps || [] }
  return normalize(combined, source, fileName)
}
export default function DigitalTwinImport({ onApplied, selectedSchool }: Props) {
  const [profiles, setProfiles] = useState<ImportedTwinProfile[]>([])
  const [preview, setPreview] = useState<ImportedTwinProfile | null>(null)
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(true)
  const loadProfiles = async () => { try { setLoading(true); const response = await api('digital-twins'); if (!response.ok) throw new Error(); const saved = await response.json() as ImportedTwinProfile[]; setProfiles(saved.map(profile => cleanProfile(profile, selectedSchool))) } catch { setNotice('저장된 Digital Twin 목록을 불러오지 못했습니다.') } finally { setLoading(false) } }
  useEffect(() => { void loadProfiles() }, [selectedSchool?.school, selectedSchool?.grade])
  useEffect(() => { if (preview && (selectedSchool?.school || selectedSchool?.grade)) setPreview(current => current ? cleanProfile(current, selectedSchool) : null) }, [selectedSchool?.school, selectedSchool?.grade])
  const chooseFile = (event: ChangeEvent<HTMLInputElement>) => { const file = event.target.files?.[0]; event.target.value = ''; if (!file) return; if (!/\.(html?|json|txt|pdf)$/i.test(file.name)) { setNotice('HTML, JSON, PDF, TXT 파일만 가져올 수 있습니다.'); return } const reader = new FileReader(); reader.onload = () => { try { const source = String(reader.result || ''); if (!source.trim()) throw new Error('파일에서 읽을 수 있는 텍스트를 찾지 못했습니다. 이미지형 PDF는 OCR 처리 후 다시 업로드해 주세요.'); setPreview(parseSource(source, file.name)); setNotice('자료에서 Digital Twin 정보를 추출했습니다. 내용을 확인한 뒤 저장해 주세요.') } catch (error) { setPreview(null); setNotice(error instanceof Error ? error.message : '파일을 분석하지 못했습니다.') } }; reader.onerror = () => setNotice('파일을 읽지 못했습니다.'); reader.readAsText(file) }
  const save = async () => { if (!preview) return; const profileToSave = cleanProfile(preview, selectedSchool); try { const response = await api('digital-twins/import', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(profileToSave) }); const body = await response.json() as ImportedTwinProfile & { detail?: string }; if (!response.ok) throw new Error(body.detail || '저장하지 못했습니다.'); const saved = cleanProfile(body, selectedSchool); setPreview(saved); onApplied(saved); setNotice(`${saved.school} Digital Twin 버전 ${saved.version}으로 저장하고 현재 생성기에 적용했습니다.`); await loadProfiles() } catch (error) { setNotice(error instanceof Error ? error.message : '저장하지 못했습니다.') } }
  const ratios = useMemo(() => Object.entries(preview?.questionRatio || {}), [preview])
  return <section className="twin-dashboard" aria-label="출제자 Digital Twin 가져오기">
    <div className="panel-heading"><div><p className="kicker">DIGITAL TWIN IMPORT</p><h2>기존 출제자 Digital Twin 가져오기</h2><p>HTML, JSON, PDF, TXT 자료에서 읽을 수 있는 내용을 추출해 현재 생성기에 적용합니다.</p></div><label className="file-picker">파일 업로드<input type="file" accept=".html,.htm,.json,.txt,.pdf,text/html,application/json,text/plain,application/pdf" onChange={chooseFile} /></label></div>
    {notice && <div className="tool-notice" role="status">{notice}<button onClick={() => setNotice('')} aria-label="안내 닫기">×</button></div>}
    {preview && <article className="work-panel"><div className="panel-heading"><div><p className="kicker">EXTRACTION PREVIEW</p><h2>{preview.school} {preview.grade} {preview.exam}</h2><p>{preview.description || '추출된 출제 특징 설명이 없습니다.'}</p></div><span className="context-chip">유형 {preview.types.length}개 감지</span></div><div className="import-meta"><div><span>출제 DNA</span><b>{preview.dna || '추출 정보 없음'}</b></div><div><span>난이도</span><b>{preview.difficulty || '자료 기준 분석'}</b></div><div><span>출제 규칙</span><b>{preview.rules.length}개</b></div><div><span>함정패턴</span><b>{preview.trapPatterns.length}개</b></div></div><div className="import-cards">{preview.types.map(type => <article key={type.name}><b>{type.name}</b><span>{type.ratio || '비율 추출 대기'}</span><p>{type.description || '업로드 자료에서 감지한 유형입니다.'}</p><small>난이도 {type.difficulty || '자료 기준'}</small></article>)}</div>{ratios.length > 0 && <div className="tag-row">{ratios.map(([name, value]) => <span key={name}>{name} {value}</span>)}</div>}{preview.trapPatterns.length > 0 && <div className="tag-row">{preview.trapPatterns.map(pattern => <span key={pattern}>{pattern}</span>)}</div>}{(preview.choiceStyle || preview.writingStyle) && <div className="import-meta">{preview.choiceStyle && <div><span>선택지 스타일</span><b>{preview.choiceStyle}</b></div>}{preview.writingStyle && <div><span>서술형 스타일</span><b>{preview.writingStyle}</b></div>}</div>}<div className="twin-actions"><button className="primary-action" onClick={save}>Digital Twin DB 저장 · 학교 계층 자동 생성</button></div></article>}
    <article className="work-panel"><div className="panel-heading"><div><p className="kicker">SAVED PROFILES</p><h2>저장된 학교별 Digital Twin</h2></div></div>{loading ? <p className="empty-paper">불러오는 중입니다…</p> : profiles.length ? <div className="material-list">{profiles.map(profile => <article key={profile.id}><div><b>{profile.school} · {profile.grade} · {profile.exam || '시험명 미입력'}</b><p>버전 {profile.version} · {profile.updatedAt}</p><small>{profile.types.slice(0, 4).map(type => type.name).join(' · ') || '유형 정보 없음'}</small></div><button onClick={() => onApplied(profile)}>생성기에 적용</button></article>)}</div> : <div className="empty-state"><b>저장된 Digital Twin이 없습니다.</b><p>기존 HTML 또는 JSON 파일을 업로드해 첫 프로필을 만들어 주세요.</p></div>}</article>
  </section>
}
