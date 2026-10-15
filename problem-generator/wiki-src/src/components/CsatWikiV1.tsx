import { useEffect, useState } from 'react'
import { api, apiUrl } from '../lib/api'
import type { Page } from '../App'

// 수능영어 Wiki V1 — DAY 1 범위: ①평가원 기출 ②평가원 출제 DNA만 활성.
// ③학생 오답 ④학생 약점 DNA ⑤개인별 대비는 구조만 노출하고 "준비 중" 안내(가짜 데이터 금지).
type Evidence = { question_no:number; exam_id?:number; exam_year?:number; exam_type?:string }
type CsatExam = { id:number; exam_year:number; exam_type:string; subject:string; exam_date:string; label:string; question_count:number; resource_count:number; dna_count:number; claim_count:number; expert_count:number }
type ExamResource = { id:number; resource_type:string; original_name:string; original_type:string; created_at:string }
type DnaEntry = { id:number; question_id:number; exam_id:number; entry_type:'DNA'|'TRAP'; tag:string; choice_no:number|null; note:string; evidence:Evidence[]; importance:number; provenance:string; expert:string; agreement:boolean; agreement_kind?:string; timestamp_start?:number|null; timestamp_end?:number|null; expert_video_url?:string; status:string }
type DnaClaim = { id:number; exam_id:number; claim_type:string; tag:string; claim_text:string; evidence:Evidence[]; importance:number; provenance:string; expert:string; agreement:boolean; agreement_kind?:string; timestamp_start?:number|null; timestamp_end?:number|null; expert_video_url?:string; status:string }
type Comparison = { id:number; exam_a_id:number; exam_b_id:number; tag:string; verdict:string; claim_text:string; evidence:Evidence[]; provenance:string; expert:string; agreement:boolean; agreement_kind?:string; timestamp_start?:number|null; timestamp_end?:number|null; expert_video_url?:string; status:string }
type ExamQuestion = { id:number; question_no:number; csat_type_id:string; csat_type_name:string; prompt:string; passage:string; answer:string; solution:string; dna:DnaEntry[] }
type ExamDna = { exam:CsatExam; tags:{entry_type:string;tag:string;question_nos:number[];notes:{question_no:number;note:string;provenance:string;expert:string;agreement_kind?:string;timestamp_start?:number|null;video_url?:string}[]}[]; traps:{entry_type:string;tag:string;question_nos:number[]}[]; per_question:{question_no:number;entries:DnaEntry[]}[]; claims:DnaClaim[] }

type Student = {id:number; name:string}
type WrongHtml = {id:number; student_id:number; student_name:string; title:string; original_name:string; original_type:string; created_at:string}
// ④ 학생 약점 DNA — 백엔드(csat_weakness)가 ③ 오답 원문 + ①② 평가원 DNA만으로 만든 구조.
type WeaknessEvidence = {item_id:string; source:string; number:string; type_label:string; student_answer:string|null; answer:string; answer_known:boolean; excerpt:string}
type WeaknessEntry = {exam_id?:number|null; exam_label:string; question_no?:number|null; entry_type:string; tag:string; choice_no?:number|null; note:string; provenance:string; expert:string; agreement:boolean; agreement_kind?:string; timestamp_start?:number|null; video_url?:string; status:string; evidence:Evidence[]}
type WeaknessClaim = {exam_id?:number|null; exam_label:string; claim_type:string; tag:string; claim_text:string; provenance:string; expert:string; agreement:boolean; agreement_kind?:string; timestamp_start?:number|null; video_url?:string; status:string; evidence:Evidence[]}
type WeaknessCard = {key:string; category:string; title:string; type_name:string; wrong_count:number; source_count:number; sources:string[]; student_answer_known:boolean; problem:string; failed_process:string; evidence:WeaknessEvidence[]; exam_dna:WeaknessEntry[]; exam_dna_tags:{entry_type:string;tag:string;count:number}[]; claims:WeaknessClaim[]; steps:string[]}
type WeaknessObservation = {key:string; category:string; title:string; type_name:string; wrong_count:number; source_count:number; sources:string[]; reason:string; evidence:WeaknessEvidence[]}
type WeaknessAnalysis = {student:{id:number;name:string}; wrong_html_id:number|null; original_name:string; wrong_total:number; dna_count:number; observation_count:number; student_answer_known:boolean; analyzed_at:string; prescription:{key:string;category:string;title:string;wrong_count:number;steps:string[]}[]; observations:WeaknessObservation[]}
type WeaknessResponse = {analysis:WeaknessAnalysis|null; dna:WeaknessCard[]}

const EXAM_TYPE_LABEL:Record<string,string>={JUNE_MOCK:'6월 모평',SEPTEMBER_MOCK:'9월 모평',CSAT:'수능'}
const STATUS_LABEL:Record<string,string>={AI_PROPOSED:'AI 제안',TEACHER_CONFIRMED:'선생님 확정',TEACHER_EDITED:'선생님 수정'}
const VERDICT_LABEL:Record<string,string>={REPEATED:'반복',STRENGTHENED:'강화',REDUCED:'완화',NEW:'신규',CHANGED:'변형'}
const TRAP_LABEL_KO:Record<string,string>={WORD_ECHO:'지선 워드에코',PARTIAL_TRUE:'부분 참',TRUE_NOT_ANSWER:'참이지만 정답 아님',SCOPE_EXPANSION:'범위 확대',CAUSAL_REVERSAL:'인과 반전',SUBJECT_SHIFT:'주체 전환',EXTREME_GENERALIZATION:'극단 일반화',OPPOSITE_MEANING:'정반대 의미',DETAIL_AS_MAIN:'세부를 main으로',NO_SUPPORT:'본문 근거 없음'}
const MENU:[string,string][]=[['questions','① 평가원 기출'],['dna','② 평가원 출제 DNA'],['wrong','③ 학생 오답'],['weakness','④ 학생 약점 DNA'],['plan','⑤ 개인별 대비']]
const COMPARE=-1

const Badge=({text,tone=''}:{text:string;tone?:string})=><span className="context-chip" style={{fontSize:11,marginLeft:4,background:tone||undefined}}>{text}</span>
const AGREEMENT_LABEL:Record<string,string>={AGREEMENT:'근거 분석과 합의',DIFFERENT_EMPHASIS:'관점 차이 보존',EXPERT_ONLY:'강사 고유 진단',UNKNOWN:'검토 필요'}
const clock=(seconds?:number|null)=>seconds==null?'':`${Math.floor(seconds/60)}:${String(seconds%60).padStart(2,'0')}`
type ProvenanceInfo={provenance:string;expert:string;agreement:boolean;agreement_kind?:string;timestamp_start?:number|null;timestamp_end?:number|null;expert_video_url?:string;video_url?:string}
const provenanceBadge=(entry:ProvenanceInfo,key?:string)=>entry.provenance==='EXPERT_ANALYSIS'?<span key={key}> <Badge text={`[${entry.expert} 분석${entry.agreement_kind?` · ${AGREEMENT_LABEL[entry.agreement_kind]||entry.agreement_kind}`:''}${entry.timestamp_start!=null?` · ${clock(entry.timestamp_start)}`:''}]`} tone="#6b5b1e" />{entry.expert_video_url&&<a href={`${entry.expert_video_url}&t=${entry.timestamp_start||0}s`} target="_blank" rel="noreferrer" style={{marginLeft:6,fontSize:12}}>영상 구간</a>}</span>:entry.provenance==='WIKI_SYNTHESIS'?<span key={key}> <Badge text="[Wiki 종합 분석]" tone="#31445c" /></span>:entry.provenance==='ACTUAL_EXAM'?<span key={key}> <Badge text="[실제 문항 근거]" tone="#1f4d36" /></span>:<span key={key}> <Badge text="[공식 자료]" tone="#41414d" /></span>
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
  const [students,setStudents]=useState<Student[]>([])
  const [wrongFiles,setWrongFiles]=useState<WrongHtml[]>([])
  const [wrongStudent,setWrongStudent]=useState<number|''>('')
  const [wrongFile,setWrongFile]=useState<File|null>(null)
  const [wrongBusy,setWrongBusy]=useState(false)

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
  // ③ 학생 오답: 원문 HTML을 그대로 저장만 한다 — 파싱·분류·연결·AI 분석은 이 화면에서 수행하지 않는다.
  const loadWrong=()=>api('csat/wrong-html').then(r=>r.ok?r.json():[]).then(setWrongFiles).catch(()=>setWrongFiles([]))
  useEffect(()=>{if(menu!=='wrong'&&menu!=='weakness')return; api('students').then(r=>r.ok?r.json():[]).then(rows=>{setStudents(rows);setWrongStudent(current=>current||rows[0]?.id||'')}).catch(()=>setStudents([])); if(menu==='wrong')loadWrong()},[menu])
  // ④ 학생 약점 DNA: 저장된 분석만 읽는다(가짜 데이터 금지). 실행은 명시적 버튼으로만 POST한다.
  const [weakData,setWeakData]=useState<WeaknessResponse|null>(null)
  const [weakLoading,setWeakLoading]=useState(false)
  const [weakBusy,setWeakBusy]=useState(false)
  const loadWeakness=(studentId:number)=>{if(!studentId){setWeakData(null);return}
    setWeakLoading(true)
    api(`csat/students/${studentId}/weakness-dna`).then(r=>r.ok?r.json():null).then(setWeakData).catch(()=>setWeakData(null)).finally(()=>setWeakLoading(false))}
  useEffect(()=>{if(menu!=='weakness')return; loadWeakness(Number(wrongStudent)||0)},[menu,wrongStudent])
  const runWeaknessAnalyze=async()=>{const studentId=Number(wrongStudent); if(!studentId)return
    setWeakBusy(true)
    try{const response=await api(`csat/students/${studentId}/weakness-dna/analyze`,{method:'POST'})
      if(response.ok)setWeakData(await response.json())
      else setNotice(response.status===403?'약점 DNA 분석은 ADMIN만 실행할 수 있습니다.':(await response.json().catch(()=>null))?.detail||'분석에 실패했습니다.')}
    finally{setWeakBusy(false)}}
  const registerWrong=async()=>{if(!wrongStudent||!wrongFile)return; setWrongBusy(true)
    const form=new FormData(); form.append('student_id',String(wrongStudent)); form.append('file',wrongFile)
    try{const response=await api('csat/wrong-html',{method:'POST',body:form})
      if(response.ok){setNotice('원문 그대로 저장했습니다 — 자동 분류·문항 연결·AI 분석은 하지 않았습니다.'); setWrongFile(null); loadWrong()}
      else setNotice(response.status===403?'학생 오답 원본은 ADMIN만 등록할 수 있습니다.':(await response.json().catch(()=>null))?.detail||'등록에 실패했습니다.')}
    finally{setWrongBusy(false)}}
  const removeWrong=async(id:number)=>{const response=await api(`csat/wrong-html/${id}`,{method:'DELETE'}); if(response.ok)loadWrong(); else setNotice('삭제하지 못했습니다.')}

  const exam=exams.find(item=>item.id===activeExamId)||null
  const years=[...new Set(exams.map(item=>item.exam_year))]
  // evidence는 exam_id 또는 exam_year+exam_type 중 하나로 시험을 가리킨다 — 시험 목록에서 exam_id로 해석한다.
  const resolveExamId=(item:Evidence)=>item.exam_id??exams.find(e=>e.exam_year===item.exam_year&&e.exam_type===item.exam_type)?.id
  const examLabelOf=(examId?:number)=>{const target=exams.find(item=>item.id===examId);return target?EXAM_TYPE_LABEL[target.exam_type]||target.exam_type:''}
  const openEvidence=(evidence:Evidence[])=>{const first=evidence.map(item=>({...item,exam_id:resolveExamId(item)})).find(item=>item.exam_id);if(!first?.exam_id){setNotice('이 화면에서 이동할 수 없는 근거 참조입니다.');return}if(first.exam_id!==activeExamId)setActiveExamId(first.exam_id);setMenu('questions');setFocusQuestion(first.question_no);window.setTimeout(()=>document.getElementById(`csat-q-${first.question_no}`)?.scrollIntoView({behavior:'auto',block:'center'}),80)}
  const evidenceChips=(evidence:Evidence[],keyPrefix:string)=><span style={{marginLeft:6}}>{evidence.map((item,index)=><button key={`${keyPrefix}-${index}`} className="btn-ghost" style={{padding:'1px 8px',fontSize:12,marginLeft:4}} onClick={()=>openEvidence([item])}>{examLabelOf(resolveExamId(item))} {item.question_no}번</button>)}</span>
  const tagLabel=(tag:string)=>trapTags.includes(tag)?`${tag}${TRAP_LABEL_KO[tag]?` · ${TRAP_LABEL_KO[tag]}`:''}`:tag.split('_').join(' ')
  // 같은 태그에 근거 유형(평가원/강사)이 다르면 같은 문항번호가 반복된다 — chip key는 중복 금지
  const uniqueNos=(nos:number[])=>[...new Set(nos)].sort((a,b)=>a-b)
  const claimGroups=(types:string[])=>(dna?.claims||[]).filter(claim=>types.includes(claim.claim_type))
  // 비교(COMPARE) 선택 상태에서 ①를 열면 회차가 없는 빈 화면이 된다 → 첫 회차로 되돌린다.
  const openMenu=(id:typeof menu)=>{if(id!=='dna'&&activeExamId===COMPARE&&exams.length)setActiveExamId(exams[0].id);setMenu(id)}
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
    <nav className="provider-tabs" aria-label="수능영어 Wiki 메뉴">{MENU.map(([id,label])=><button key={id} className={menu===id?'active':''} onClick={()=>openMenu(id as typeof menu)}>{label}</button>)}</nav>

    {menu==='questions'&&<>
      <div className="provider-tabs" aria-label="시험 선택">{exams.map(item=><button key={item.id} className={activeExamId===item.id?'active':''} onClick={()=>{setActiveExamId(item.id);setFocusQuestion(null)}}>{item.label||`${item.exam_year} ${EXAM_TYPE_LABEL[item.exam_type]||item.exam_type}`}</button>)}{!exams.length&&<span>등록된 평가원 시험이 없습니다. ADMIN이 기출을 등록하면 여기에 나타납니다.</span>}</div>
      {exam&&<article className="work-panel"><div className="panel-heading"><div><p className="kicker">SOURCE · 평가원 원본</p><h2>{exam.label} · 시행 {exam.exam_date}</h2><p>문제지·정답 등 평가원이 공개한 원본 자료입니다. SOURCE와 분석은 구분되어 표시됩니다.</p></div></div>
        <div className="csat-question-grid">{resources.map(item=><article key={item.id}><div><b>{item.resource_type}</b><p>{item.original_name}</p><a href={apiUrl(`csat/exam-resources/${item.id}/original`)} target="_blank" rel="noreferrer">원본 열기</a></div></article>)}{!resources.length&&<div className="empty-state"><b>등록된 원본 자료가 없습니다.</b></div>}</div>
      </article>}
      {exam&&<article className="work-panel"><div className="panel-heading"><div><p className="kicker">QUESTION · 문항-정답-DNA</p><h2>문항 목록 ({questions.length})</h2><p>듣기(1~17번)는 분석 제외. 학생들이 자주 틀리는 핵심 구간(20~24 · 29~42) 기준으로 등록합니다.</p></div></div>
        <div className="csat-question-grid">{questions.map(item=><article key={item.id} id={`csat-q-${item.question_no}`} style={focusQuestion===item.question_no?{outline:'2px solid #c9a227'}:undefined}>
          <div><b>{item.question_no}. {item.csat_type_name} <span className="context-chip">정답 {item.answer||'-'}</span></b><p>{String(item.prompt||'').slice(0,140)}</p>
            <div>{item.dna.map(entry=><span key={entry.id}><Badge text={`${entry.entry_type==='TRAP'?'함정':'DNA'} · ${tagLabel(entry.tag)}${entry.choice_no?` (${entry.choice_no}번 선지)`:''}`} tone={entry.entry_type==='TRAP'?'#5c1f2e':''}/>{provenanceBadge(entry,`p-${entry.id}`)}{statusBadge(entry.status,`s-${entry.id}`)}</span>)}{!item.dna.length&&<small>DNA 미등록</small>}</div>
            {item.dna.some(entry=>entry.note)&&<details><summary>DNA 상세</summary><ul>{item.dna.filter(entry=>entry.note).map(entry=><li key={entry.id}><b>{tagLabel(entry.tag)}</b> {entry.note}{provenanceBadge(entry,`n-${entry.id}`)}{statusBadge(entry.status,`ns-${entry.id}`)}</li>)}</ul></details>}          </div></article>)}{!questions.length&&exam&&<div className="empty-state"><b>이 시험에는 아직 문항이 등록되지 않았습니다.</b></div>}</div>
      </article>}
    </>}

    {menu==='dna'&&exams.length>0&&<>
      <div className="provider-tabs" aria-label="DNA 시험 선택">
        {exams.map(item=><button key={item.id} className={activeExamId===item.id?'active':''} onClick={()=>setActiveExamId(item.id)}>{EXAM_TYPE_LABEL[item.exam_type]||item.exam_type}</button>)}
        {exams.length>=2&&<button className={activeExamId===COMPARE?'active':''} onClick={()=>setActiveExamId(COMPARE)}>{EXAM_TYPE_LABEL[exams[0].exam_type] || exams[0].label}↔{EXAM_TYPE_LABEL[exams[1].exam_type] || exams[1].label} 비교</button>}
      </div>
      {dnaLoading&&<p>DNA를 불러오는 중입니다...</p>}
      {!dnaLoading&&exam&&dna&&<>
        <article className="work-panel"><div className="panel-heading"><div><p className="kicker">CORE TRAITS · {exam.label}</p><h2>핵심 출제 특징</h2><p>강사 분석 {exam.expert_count}건 · 평가원/기출 근거 {exam.dna_count + exam.claim_count - exam.expert_count}건</p></div></div>
          {claimGroups(['CORE_TRAIT','SKILL_DEMAND']).map(renderClaim)}
          {!claimGroups(['CORE_TRAIT','SKILL_DEMAND']).length&&<p>아직 등록된 CLAIM이 없습니다. (근거 없는 단정은 표시하지 않습니다.)</p>}
        </article>
        <article className="work-panel"><div className="panel-heading"><div><p className="kicker">REPEATED SKILLS</p><h2>반복되는 사고 능력</h2></div></div>
          {repeatedSkills.map(tag=><p key={tag.tag}><b>{tagLabel(tag.tag)}</b> · {uniqueNos(tag.question_nos).length}개 문항 {uniqueNos(tag.question_nos).map(no=><button key={no} className="btn-ghost" style={{padding:'1px 8px',fontSize:12,marginLeft:4}} onClick={()=>openEvidence([{question_no:no,exam_id:exam.id}])}>{no}번</button>)}</p>)}
          {!repeatedSkills.length&&<p>2개 문항 이상에서 반복된 DNA 태그가 아직 없습니다.</p>}
        </article>
        <article className="work-panel"><div className="panel-heading"><div><p className="kicker">TRAPS</p><h2>오답 함정</h2></div></div>
          {dna.traps.map(trap=><p key={trap.tag}><b>{tagLabel(trap.tag)}</b> · {uniqueNos(trap.question_nos).map(no=><button key={no} className="btn-ghost" style={{padding:'1px 8px',fontSize:12,marginLeft:4}} onClick={()=>openEvidence([{question_no:no,exam_id:exam.id}])}>{no}번</button>)}</p>)}
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

    {menu==='wrong'&&<article className="work-panel"><div className="panel-heading"><div><p className="kicker">STUDENT · 오답 원본 보관</p><h2>학생 오답 HTML 등록</h2><p>ADMIN이 기존 학생별로 오답 HTML(.html/.htm) 원문을 그대로 저장합니다. 시험 자동 분류, 문항번호·답 추출, question_id/DNA/TRAP 연결, AI 분석·약점 판정은 수행하지 않습니다.</p></div></div>
      <div role="group" aria-label="오답 HTML 등록" style={{display:'flex',gap:8,flexWrap:'wrap',alignItems:'center',margin:'8px 0 14px'}}>
        <select aria-label="학생 선택" value={wrongStudent} onChange={e=>setWrongStudent(e.target.value?Number(e.target.value):'')}><option value="">학생 선택</option>{students.map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select>
        <input key={wrongFiles.length} type="file" accept=".html,.htm" aria-label="오답 HTML 파일" onChange={e=>setWrongFile(e.target.files?.[0]||null)}/>
        <button className="primary-action" disabled={wrongBusy||!wrongStudent||!wrongFile} onClick={registerWrong}>{wrongBusy?'저장 중…':'HTML 등록'}</button>
      </div>
      {!students.length&&<p>등록된 학생이 없습니다. 학생은 기존 학생 목록에서 선택하며, 여기서 새로 만들지 않습니다.</p>}
      {wrongFiles.map(item=><p key={item.id} style={{display:'flex',gap:10,flexWrap:'wrap',alignItems:'center'}}><b>{item.student_name}</b><span>{item.title||item.original_name}</span><span className="context-chip" style={{fontSize:11}}>{item.created_at.slice(0,10)}</span><a href={apiUrl(`csat/wrong-html/${item.id}/raw`)} target="_blank" rel="noreferrer">보기</a><button className="btn-ghost" style={{padding:'1px 8px',fontSize:12}} onClick={()=>removeWrong(item.id)}>삭제</button></p>)}
      {!wrongFiles.length&&<div className="empty-state"><b>등록된 학생 오답 원본이 없습니다.</b></div>}
    </article>}
    {menu==='weakness'&&<>
      <article className="work-panel"><div className="panel-heading"><div><p className="kicker">DAY 2 · STUDENT WEAKNESS DNA</p><h2>④ 학생 약점 DNA</h2><p>③에 등록된 오답 원문과 ①②의 평가원 출제 DNA만으로 분석합니다. 원문에 없는 정보(학생이 고른 선지 등)는 만들지 않으며, 서로 다른 시험 2개 이상 × 문항 2개 이상에서 반복된 유형만 약점으로 확정합니다.</p></div></div>
        <div style={{display:'flex',gap:8,flexWrap:'wrap',alignItems:'center',margin:'8px 0 14px'}}>
          <select aria-label="약점 분석 학생 선택" value={wrongStudent} onChange={e=>setWrongStudent(e.target.value?Number(e.target.value):'')}><option value="">학생 선택</option>{students.map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select>
          <button className="primary-action" disabled={weakBusy||!wrongStudent} onClick={runWeaknessAnalyze}>{weakBusy?'분석 중…':weakData?.analysis?'재분석':'약점 DNA 분석 실행'}</button>
          {weakData?.analysis&&<a className="btn-ghost" href={apiUrl(`csat/wrong-html/${weakData.analysis.wrong_html_id}/raw`)} target="_blank" rel="noreferrer">근거 원문 전체 보기</a>}
        </div>
        {!students.length&&<p>등록된 학생이 없습니다.</p>}
        {weakLoading&&<p>저장된 분석을 불러오는 중입니다...</p>}
        {!weakLoading&&wrongStudent&&!weakData?.analysis&&<div className="empty-state"><b>아직 분석하지 않았습니다.</b><p>위에서 학생을 선택하고 「약점 DNA 분석 실행」을 누르면, 등록된 오답 원문과 평가원 출제 DNA만으로 약점 DNA가 만들어집니다.</p></div>}
        {weakData?.analysis&&<>
          <div className="csat-question-grid" aria-label="분석 요약">
            <article><div><b>{weakData.analysis.student.name}</b><p>학생</p></div></article>
            <article><div><b>{weakData.analysis.wrong_total}문항</b><p>분석한 오답 문항 수</p></div></article>
            <article><div><b>{weakData.analysis.dna_count}개</b><p>확인된 반복 약점 DNA</p></div></article>
            <article><div><b>{weakData.analysis.observation_count}건</b><p>관찰(반복 규칙 미달 — 약점 아님)</p></div></article>
          </div>
          <p style={{margin:'10px 0 0',fontSize:12,color:'var(--work-muted)'}}>근거 원문: {weakData.analysis.original_name} · 분석 {weakData.analysis.analyzed_at.slice(0,16).replace('T',' ')} · 학생 선택 답 {weakData.analysis.student_answer_known?'원문 표기 있음':'원문 미표기(추정하지 않음)'}</p>
        </>}
      </article>
      {weakData?.analysis&&weakData.dna.map(card=><article className="work-panel" key={card.key}>
        <div className="panel-heading"><div><p className="kicker">{card.category} · 반복 {card.wrong_count}회 / {card.source_count}개 시험</p><h2>{card.title}</h2></div></div>
        <h3>무엇이 문제인가</h3>
        <p>{card.problem}</p>
        <h3>실제 오답 근거</h3>
        <p style={{fontSize:12,color:'var(--work-muted)'}}>근거 문항을 누르면 ③에 등록된 오답 원문(텍스트)이 새 창에서 열립니다 — <code>{card.evidence[0]?.item_id}</code>·{card.evidence[0]?.source}·{card.evidence[0]?.number}번으로 원문에서 위치를 확인할 수 있습니다.</p>
        <ul style={{listStyle:'none',padding:0,margin:'6px 0 0'}}>{card.evidence.map(ev=><li key={ev.item_id} style={{display:'flex',gap:6,flexWrap:'wrap',alignItems:'baseline',margin:'3px 0'}}>
          <a href={apiUrl(`csat/wrong-html/${weakData.analysis?.wrong_html_id}/raw`)} target="_blank" rel="noreferrer" className="btn-ghost" style={{padding:'1px 8px',fontSize:12,textDecoration:'none'}} title="오답 원문 열기">{ev.source} · {ev.number}번</a>
          <Badge text={`학생답 ${ev.student_answer??'원문 미표기'}`} tone={ev.student_answer?undefined:'#eceff3'}/>
          <Badge text={`정답 ${ev.answer_known?ev.answer:'미확인'}`} tone="#e4efe7"/>
          <small style={{color:'var(--work-muted)'}}>{ev.type_label}</small>
        </li>)}</ul>
        <h3>평가원 출제 DNA</h3>
        {card.exam_dna_tags.length>0&&<p>{card.exam_dna_tags.map(tag=><Badge key={`${tag.entry_type}-${tag.tag}`} text={`${tag.entry_type==='TRAP'?'함정':'DNA'} · ${tagLabel(tag.tag)} ×${tag.count}`} tone={tag.entry_type==='TRAP'?'#5c1f2e':''}/>)}</p>}
        {card.exam_dna.length>0&&<details><summary>문항별 출제 DNA {card.exam_dna.length}건 (근거 문항 이동)</summary><ul>{card.exam_dna.map((entry,index)=><li key={`${entry.exam_id}-${entry.question_no}-${index}`} style={{margin:'6px 0'}}>
          <b>{entry.exam_label} {entry.question_no}번</b> <small>{tagLabel(entry.tag)}{entry.choice_no?` · ${entry.choice_no}번 선지`:''}</small>
          {entry.note&&<p style={{margin:'2px 0'}}>{entry.note}</p>}
          {provenanceBadge({...entry,expert_video_url:entry.video_url},`w-${card.key}-${index}`)}{statusBadge(entry.status,`ws-${card.key}-${index}`)}
          {entry.evidence.length>0&&<button className="btn-ghost" style={{padding:'1px 8px',fontSize:12,marginLeft:6}} onClick={()=>openEvidence(entry.evidence)}>근거 {entry.question_no}번 문항</button>}
        </li>)}</ul></details>}
        {card.claims.length>0&&<details><summary>회차 CLAIM {card.claims.length}건</summary>{card.claims.map((claim,index)=><p key={`${claim.exam_id}-${index}`}><b>{claim.claim_text}</b>{evidenceChips(claim.evidence,`wclaim-${card.key}-${index}`)}{provenanceBadge({...claim,expert_video_url:claim.video_url},`wc-${card.key}-${index}`)}{statusBadge(claim.status,`wcs-${card.key}-${index}`)}</p>)}</details>}
        {!card.exam_dna.length&&<p><small>이 유형에 연결된 평가원 출제 DNA가 아직 없습니다.</small></p>}
        <h3>학생의 잘못된 풀이 과정</h3>
        <p>{card.failed_process}</p>
        <h3>앞으로의 풀이 행동</h3>
        <ol>{card.steps.map((step,index)=><li key={index}>{step}</li>)}</ol>
        <p><a className="btn-ghost" href={apiUrl(`csat/wrong-html/${weakData.analysis?.wrong_html_id}/raw`)} target="_blank" rel="noreferrer">근거 문항 보기 — 오답 원문 열기</a></p>
      </article>)}
      {weakData?.analysis&&weakData.dna.length>0&&<article className="work-panel"><div className="panel-heading"><div><p className="kicker">PRESCRIPTION · 행동 단위</p><h2>{weakData.analysis.student.name}의 수능영어 풀이 처방</h2><p>약점 이름(예: "빈칸에 약함")이 아니라, 문제를 보면 무엇을 확인하고 어디에서 근거를 찾고 선택지를 어떻게 제거할지 행동 단위로 정리합니다.</p></div></div>
        {weakData.analysis.prescription.map(item=><div key={item.key} style={{margin:'12px 0'}}><b>{item.title}</b> <Badge text={`${item.wrong_count}회 반복`} tone="#f3ead3"/><ol>{item.steps.map((step,index)=><li key={index}>{step}</li>)}</ol></div>)}
      </article>}
      {weakData?.analysis&&weakData.analysis.observations.length>0&&<article className="work-panel"><div className="panel-heading"><div><p className="kicker">OBSERVATIONS · 관찰</p><h2>약점으로 확정하지 않은 오답</h2><p>반복 규칙(서로 다른 시험 2개 이상 × 문항 2개 이상)을 넘지 못해 약점으로 단정하지 않습니다.</p></div></div>
        {weakData.analysis.observations.map(row=><p key={row.key}><b>{row.title}</b> · {row.wrong_count}문항 / {row.source_count}개 시험 <small>({row.sources.join(', ')})</small> <small>{row.reason}</small></p>)}
      </article>}
    </>}
    {menu==='plan'&&<article className="work-panel"><div className="panel-heading"><div><p className="kicker">DAY 2 · 준비 중</p><h2>⑤ 개인별 대비</h2><p>④에서 저장한 약점 DNA(오답 원문 → 약점 DNA → 평가원 출제 DNA 연결 구조)를 그대로 사용해 최승원 맞춤 복습 수능 문제지를 만듭니다. 아직 생성하지 않습니다.</p></div></div><div className="empty-state"><b>준비 중인 메뉴입니다.</b><p>먼저 ④ 학생 약점 DNA의 정확성을 완성합니다.</p><button className="primary-action" onClick={()=>openMenu('weakness')}>④ 학생 약점 DNA 보기</button></div></article>}
  </section>
}
