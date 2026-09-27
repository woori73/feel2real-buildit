import { useEffect, useState } from 'react'
import { api, apiUrl } from '../lib/api'
import type { Page } from '../App'

// 수능영어 Wiki V1 — DAY 1 범위: ①평가원 기출 ②평가원 출제 DNA만 활성.
// ③학생 오답 ④학생 약점 DNA ⑤개인별 대비는 구조만 노출하고 "준비 중" 안내(가짜 데이터 금지).
type Evidence = { question_no:number; exam_id?:number; exam_year?:number; exam_type?:string }
type CsatExam = { id:number; exam_year:number; exam_type:string; subject:string; exam_date:string; label:string; question_count:number; resource_count:number; dna_count:number; claim_count:number }
type ExamResource = { id:number; resource_type:string; original_name:string; original_type:string; created_at:string }
type DnaEntry = { id:number; question_id:number; exam_id:number; entry_type:'DNA'|'TRAP'; tag:string; choice_no:number|null; note:string; evidence:Evidence[]; importance:number; provenance:string; expert:string; agreement:boolean; status:string }
type DnaClaim = { id:number; exam_id:number; claim_type:string; tag:string; claim_text:string; evidence:Evidence[]; importance:number; provenance:string; expert:string; agreement:boolean; status:string }
type Comparison = { id:number; exam_a_id:number; exam_b_id:number; tag:string; verdict:string; claim_text:string; evidence:Evidence[]; provenance:string; expert:string; agreement:boolean; status:string }
type ExamQuestion = { id:number; question_no:number; csat_type_id:string; csat_type_name:string; prompt:string; passage:string; answer:string; solution:string; dna:DnaEntry[] }
type ExamDna = { exam:CsatExam; tags:{entry_type:string;tag:string;question_nos:number[];notes:{question_no:number;note:string;provenance:string;expert:string}[]}[]; traps:{entry_type:string;tag:string;question_nos:number[]}[]; per_question:{question_no:number;entries:DnaEntry[]}[]; claims:DnaClaim[] }

const EXAM_TYPE_LABEL:Record<string,string>={JUNE_MOCK:'6월 모평',SEPTEMBER_MOCK:'9월 모평',CSAT:'수능'}
const STATUS_LABEL:Record<string,string>={AI_PROPOSED:'AI 제안',TEACHER_CONFIRMED:'선생님 확정',TEACHER_EDITED:'선생님 수정'}
const VERDICT_LABEL:Record<string,string>={REPEATED:'반복',STRENGTHENED:'강화',REDUCED:'완화',NEW:'신규',CHANGED:'변형'}
const TRAP_LABEL_KO:Record<string,string>={WORD_ECHO:'지선 워드에코',PARTIAL_TRUE:'부분 참',TRUE_NOT_ANSWER:'참이지만 정답 아님',SCOPE_EXPANSION:'범위 확대',CAUSAL_REVERSAL:'인과 반전',SUBJECT_SHIFT:'주체 전환',EXTREME_GENERALIZATION:'극단 일반화',OPPOSITE_MEANING:'정반대 의미',DETAIL_AS_MAIN:'세부를 main으로',NO_SUPPORT:'본문 근거 없음'}
const MENU:[string,string][]=[['questions','① 평가원 기출'],['dna','② 평가원 출제 DNA'],['wrong','③ 학생 오답'],['weakness','④ 학생 약점 DNA'],['plan','⑤ 개인별 대비']]
const COMPARE=-1

const Badge=({text,tone=''}:{text:string;tone?:string})=><span className="context-chip" style={{fontSize:11,marginLeft:4,background:tone||undefined}}>{text}</span>
const provenanceBadge=(entry:{provenance:string;expert:string;agreement:boolean},key?:string)=>entry.provenance==='EXPERT_ANALYSIS'?<span key={key}> <Badge text={`[${entry.expert}${entry.agreement?' · 분석가 합의':''} 분석]`} tone="#6b5b1e" /></span>:entry.provenance==='WIKI_SYNTHESIS'?<span key={key}> <Badge text="[Wiki 종합 분석]" tone="#31445c" /></span>:entry.provenance==='ACTUAL_EXAM'?<span key={key}> <Badge text="[실제 문항 근거]" tone="#1f4d36" /></span>:<span key={key}> <Badge text="[공식 자료]" tone="#41414d" /></span>
const statusBadge=(status:string,key?:string)=><span key={key}> <Badge text={STATUS_LABEL[status]||status} tone={status==='AI_PROPOSED'?'#5c4a12':'#1f4d36'} /></span>

export default function CsatWikiV1({onNavigate}:{onNavigate:(page:Page)=>void}){
  const [menu,setMenu]=useState<'questions'|'dna'|'wrong'|'weakness'|'plan'>('questions')
  const [exams,setExams]=useState<CsatExam[]>([])
  const [trapTags,setTrapTags]=useState<string[]>([])
  const [activeExamId,setActiveExamId]=useState<number|null>(null)
  const [resources,setResources]=useState<ExamResource[]>([])
  const [questions,setQuestions]=useState<ExamQuestion[]>([])
  const [dna,setDna]=useState<ExamDna|null>(null),[dnaLoading,setDnaLoading]=useState(false)
  const [comparisons,setComparisons]=useState<Comparison[]>([])
  const [focusQuestion,setFocusQuestion]=useState<number|null>(null)
  const [notice,setNotice]=useState('')

  useEffect(()=>{api('csat/exams').then(r=>r.json()).then((rows:CsatExam[])=>{setExams(rows);setActiveExamId(rows[0]?.id??null)}).catch(()=>setNotice('기출 목록을 불러오지 못했습니다.'));api('csat/dna/taxonomy').then(r=>r.ok?r.json():null).then(t=>setTrapTags(t?.traps||[])).catch(()=>undefined)},[])
  useEffect(()=>{
    if(activeExamId===null||activeExamId===COMPARE)return
    api(`csat/exams/${activeExamId}`).then(r=>r.ok?r.json():{resources:[]}).then(detail=>setResources(detail.resources||[])).catch(()=>setResources([]))
    api(`csat/exams/${activeExamId}/questions`).then(r=>r.ok?r.json():[]).then(setQuestions).catch(()=>setQuestions([]))
    setDnaLoading(true)
    api(`csat/exams/${activeExamId}/dna`).then(r=>r.ok?r.json():null).then(setDna).catch(()=>setDna(null)).finally(()=>setDnaLoading(false))
  },[activeExamId])
  useEffect(()=>{
    if(exams.length<2){setComparisons([]);return}
    const [a,b]=exams
    api(`csat/dna-comparisons?exam_a_id=${a.id}&exam_b_id=${b.id}`).then(r=>r.ok?r.json():[]).then(setComparisons).catch(()=>setComparisons([]))
  },[exams,menu,dna])

  const exam=exams.find(item=>item.id===activeExamId)||null
  const years=[...new Set(exams.map(item=>item.exam_year))]
  // evidence는 exam_id 또는 exam_year+exam_type 중 하나로 시험을 가리킨다 — 시험 목록에서 exam_id로 해석한다.
  const resolveExamId=(item:Evidence)=>item.exam_id??exams.find(e=>e.exam_year===item.exam_year&&e.exam_type===item.exam_type)?.id
  const examLabelOf=(examId?:number)=>{const target=exams.find(item=>item.id===examId);return target?EXAM_TYPE_LABEL[target.exam_type]||target.exam_type:''}
  const openEvidence=(evidence:Evidence[])=>{const first=evidence.map(item=>({...item,exam_id:resolveExamId(item)})).find(item=>item.exam_id);if(!first?.exam_id){setNotice('이 화면에서 이동할 수 없는 근거 참조입니다.');return}if(first.exam_id!==activeExamId)setActiveExamId(first.exam_id);setMenu('questions');setFocusQuestion(first.question_no);window.setTimeout(()=>document.getElementById(`csat-q-${first.question_no}`)?.scrollIntoView({behavior:'auto',block:'center'}),80)}
  const evidenceChips=(evidence:Evidence[],keyPrefix:string)=><span style={{marginLeft:6}}>{evidence.map((item,index)=><button key={`${keyPrefix}-${index}`} className="btn-ghost" style={{padding:'1px 8px',fontSize:12,marginLeft:4}} onClick={()=>openEvidence([item])}>{examLabelOf(resolveExamId(item))} {item.question_no}번</button>)}</span>
  const tagLabel=(tag:string)=>trapTags.includes(tag)?`${tag}${TRAP_LABEL_KO[tag]?` · ${TRAP_LABEL_KO[tag]}`:''}`:tag.split('_').join(' ')
  const claimGroups=(types:string[])=>(dna?.claims||[]).filter(claim=>types.includes(claim.claim_type))
  const repeatedSkills=(dna?.tags||[]).filter(tag=>tag.entry_type==='DNA'&&new Set(tag.question_nos).size>=2)
  const renderClaim=(claim:DnaClaim)=> <p key={claim.id}><b>{claim.claim_text}</b>{evidenceChips(claim.evidence,`claim-${claim.id}`)}{provenanceBadge(claim)}{statusBadge(claim.status)}</p>

  return <section className="naesin-v1 csat-wiki-v1">
    <header>
      <p className="kicker">BUILD IT · CSAT ENGLISH WIKI V1</p>
      <div role="group" aria-label="Wiki 섹션">
        <button className="btn-ghost" onClick={()=>onNavigate('state')}>[내신 Wiki]</button>
        <button className="btn-ghost" disabled style={{fontWeight:700}}>[수능영어 Wiki]</button>
      </div>
      <h1>{years.length?`${years[0]}학년도`:'수능영어'} 평가원 출제 DNA</h1>
      <p>평가원 기출(LEVEL 1)이 최우선 근거입니다. 강사 분석은 실제 기출보다 우선하지 않으며, 모든 CLAIM은 근거 문항을 클릭해 직접 검증할 수 있습니다.</p>
    </header>
    {notice&&<div className="tool-notice" role="status">{notice}<button onClick={()=>setNotice('')}>×</button></div>}
    <nav className="provider-tabs" aria-label="수능영어 Wiki 메뉴">{MENU.map(([id,label])=><button key={id} className={menu===id?'active':''} onClick={()=>setMenu(id as typeof menu)}>{label}</button>)}</nav>

    {menu==='questions'&&<>
      <div className="provider-tabs" aria-label="시험 선택">{exams.map(item=><button key={item.id} className={activeExamId===item.id?'active':''} onClick={()=>{setActiveExamId(item.id);setFocusQuestion(null)}}>{item.label||`${item.exam_year} ${EXAM_TYPE_LABEL[item.exam_type]||item.exam_type}`}</button>)}{!exams.length&&<span>등록된 평가원 시험이 없습니다. ADMIN이 기출을 등록하면 여기에 나타납니다.</span>}</div>
      {exam&&<article className="work-panel"><div className="panel-heading"><div><p className="kicker">SOURCE · 평가원 원본</p><h2>{exam.label} · 시행 {exam.exam_date}</h2><p>문제지·정답 등 평가원이 공개한 원본 자료입니다. SOURCE와 분석은 구분되어 표시됩니다.</p></div></div>
        <div className="csat-question-grid">{resources.map(item=><article key={item.id}><div><b>{item.resource_type}</b><p>{item.original_name}</p><a href={apiUrl(`csat/exam-resources/${item.id}/original`)} target="_blank" rel="noreferrer">원본 열기</a></div></article>)}{!resources.length&&<div className="empty-state"><b>등록된 원본 자료가 없습니다.</b></div>}</div>
      </article>}
      {exam&&<article className="work-panel"><div className="panel-heading"><div><p className="kicker">QUESTION · 문항-정답-DNA</p><h2>문항 목록 ({questions.length})</h2><p>듣기(1~17번)는 분석 제외. 학생들이 자주 틀리는 핵심 구간(20~24 · 29~42) 기준으로 등록합니다.</p></div></div>
        <div className="csat-question-grid">{questions.map(item=><article key={item.id} id={`csat-q-${item.question_no}`} style={focusQuestion===item.question_no?{outline:'2px solid #c9a227'}:undefined}>
          <div><b>{item.question_no}. {item.csat_type_name} <span className="context-chip">정답 {item.answer||'-'}</span></b><p>{String(item.prompt||'').slice(0,140)}</p>
            <div>{item.dna.map(entry=><span key={entry.id}><Badge text={`${entry.entry_type==='TRAP'?'함정':'DNA'} · ${tagLabel(entry.tag)}${entry.choice_no?` (${entry.choice_no}번 선지)`:''}`} tone={entry.entry_type==='TRAP'?'#5c1f2e':''}/>{provenanceBadge(entry,`p-${entry.id}`)}{statusBadge(entry.status,`s-${entry.id}`)}</span>)}{!item.dna.length&&<small>DNA 미등록</small>}</div>
            {item.dna.some(entry=>entry.note)&&<details><summary>DNA 상세</summary><ul>{item.dna.filter(entry=>entry.note).map(entry=><li key={entry.id}><b>{tagLabel(entry.tag)}</b> {entry.note}{provenanceBadge(entry,`n-${entry.id}`)}{statusBadge(entry.status,`ns-${entry.id}`)}</li>)}</ul></details>}
          </div></article>)}{!questions.length&&exam&&<div className="empty-state"><b>이 시험에는 아직 문항이 등록되지 않았습니다.</b></div>}</div>
      </article>}
    </>}

    {menu==='dna'&&exams.length>0&&<>
      <div className="provider-tabs" aria-label="DNA 시험 선택">
        {exams.map(item=><button key={item.id} className={activeExamId===item.id?'active':''} onClick={()=>setActiveExamId(item.id)}>{EXAM_TYPE_LABEL[item.exam_type]||item.exam_type}</button>)}
        {exams.length>=2&&<button className={activeExamId===COMPARE?'active':''} onClick={()=>setActiveExamId(COMPARE)}>{EXAM_TYPE_LABEL[exams[0].exam_type] || exams[0].label}↔{EXAM_TYPE_LABEL[exams[1].exam_type] || exams[1].label} 비교</button>}
      </div>
      {dnaLoading&&<p>DNA를 불러오는 중입니다...</p>}
      {!dnaLoading&&exam&&dna&&<>
        <article className="work-panel"><div className="panel-heading"><div><p className="kicker">CORE TRAITS · {exam.label}</p><h2>핵심 출제 특징</h2></div></div>
          {claimGroups(['CORE_TRAIT','SKILL_DEMAND']).map(renderClaim)}
          {!claimGroups(['CORE_TRAIT','SKILL_DEMAND']).length&&<p>아직 등록된 CLAIM이 없습니다. (근거 없는 단정은 표시하지 않습니다.)</p>}
        </article>
        <article className="work-panel"><div className="panel-heading"><div><p className="kicker">REPEATED SKILLS</p><h2>반복되는 사고 능력</h2></div></div>
          {repeatedSkills.map(tag=><p key={tag.tag}><b>{tagLabel(tag.tag)}</b> · {new Set(tag.question_nos).size}개 문항 {tag.question_nos.map(no=><button key={no} className="btn-ghost" style={{padding:'1px 8px',fontSize:12,marginLeft:4}} onClick={()=>openEvidence([{question_no:no,exam_id:exam.id}])}>{no}번</button>)}</p>)}
          {!repeatedSkills.length&&<p>2개 문항 이상에서 반복된 DNA 태그가 아직 없습니다.</p>}
        </article>
        <article className="work-panel"><div className="panel-heading"><div><p className="kicker">TRAPS</p><h2>오답 함정</h2></div></div>
          {dna.traps.map(trap=><p key={trap.tag}><b>{tagLabel(trap.tag)}</b> · {trap.question_nos.map(no=><button key={no} className="btn-ghost" style={{padding:'1px 8px',fontSize:12,marginLeft:4}} onClick={()=>openEvidence([{question_no:no,exam_id:exam.id}])}>{no}번</button>)}</p>)}
          {claimGroups(['MAJOR_TRAP']).map(renderClaim)}
          {!dna.traps.length&&!claimGroups(['MAJOR_TRAP']).length&&<p>등록된 함정 기록이 없습니다.</p>}
        </article>
        <article className="work-panel"><div className="panel-heading"><div><p className="kicker">HIGH DIFFICULTY</p><h2>고난도 문항 구조</h2></div></div>
          {claimGroups(['HIGH_DIFFICULTY']).map(renderClaim)}
          {!claimGroups(['HIGH_DIFFICULTY']).length&&<p>등록된 고난도 CLAIM이 없습니다.</p>}
        </article>
        <article className="work-panel"><div className="panel-heading"><div><p className="kicker">PER-QUESTION DNA</p><h2>문항별 DNA</h2></div></div>
          <div className="csat-question-grid">{dna.per_question.map(row=><article key={row.question_no}><div><b>{row.question_no}번</b> {row.entries.map(entry=><span key={entry.id}><Badge text={`${entry.entry_type==='TRAP'?'함정':'DNA'} · ${tagLabel(entry.tag)}`} tone={entry.entry_type==='TRAP'?'#5c1f2e':''}/>{provenanceBadge(entry,`q-${entry.id}`)}{statusBadge(entry.status,`qs-${entry.id}`)}</span>)}
            {row.entries.some(entry=>entry.note)&&<p style={{margin:'4px 0'}}>{row.entries.filter(entry=>entry.note).map(entry=><span key={entry.id}>{entry.tag}: {entry.note} </span>)}</p>}
            <button className="btn-ghost" onClick={()=>openEvidence(row.entries.flatMap(entry=>entry.evidence).length?row.entries.flatMap(entry=>entry.evidence):[{question_no:row.question_no,exam_id:exam.id}])}>문항 근거 보기</button></div></article>)}</div>
          {!dna.per_question.length&&<p>문항 단위 DNA가 아직 없습니다.</p>}
        </article>
      </>}
      {activeExamId===COMPARE&&exams.length>=2&&<article className="work-panel"><div className="panel-heading"><div><p className="kicker">CROSS-EXAM</p><h2>{exams[0].label} ↔ {exams[1].label} 비교</h2><p>비교도 CLAIM+EVIDENCE — 판정마다 근거 문항이 연결됩니다. 근거 없는 출제 예측("반드시 나온다")은 표시하지 않습니다.</p></div></div>
        {['REPEATED','STRENGTHENED','REDUCED','NEW','CHANGED'].map(verdict=>{const rows=comparisons.filter(item=>item.verdict===verdict);return rows.length?<div key={verdict}><h3>{VERDICT_LABEL[verdict]}</h3>{rows.map(item=><p key={item.id}><b>{item.tag?`${tagLabel(item.tag)} — `:''}{item.claim_text}</b>{evidenceChips(item.evidence,`cmp-${item.id}`)}{provenanceBadge(item)}{statusBadge(item.status)}</p>)}</div>:null})}
        {!comparisons.length&&<p>등록된 비교 CLAIM이 없습니다. (두 시험 문항 등록 후 근거와 함께 채워집니다.)</p>}
      </article>}
      {!exam&&activeExamId!==COMPARE&&<p>상단에서 시험을 선택해 주세요.</p>}
    </>}
    {menu==='dna'&&!exams.length&&<div className="empty-state"><b>아직 등록된 평가원 시험이 없습니다.</b><p>기출 등록 후 DNA 화면이 채워집니다.</p></div>}

    {(menu==='wrong'||menu==='weakness'||menu==='plan')&&<article className="work-panel"><div className="panel-heading"><div><p className="kicker">DAY 2 · 준비 중</p><h2>{MENU.find(([id])=>id===menu)?.[1]}</h2><p>학생 오답 등록, 학생별 문항-DNA × 선택 오답 연결, 약점 DNA 크로스레퍼런스는 DAY 2 작업입니다. 근거 없는 학생 분석은 표시하지 않습니다.</p></div></div><div className="empty-state"><b>준비 중인 메뉴입니다.</b><p>기존 학생 수능 오답 DB는 「고3 수능 영어」 제작 워크스페이스에서 계속 관리됩니다.</p><button className="primary-action" onClick={()=>onNavigate('csat')}>기존 수능 제작 워크스페이스 열기</button></div></article>}
  </section>
}
