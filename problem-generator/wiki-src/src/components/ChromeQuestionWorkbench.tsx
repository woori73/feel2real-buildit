import { ReactNode, useEffect, useMemo, useState } from 'react'
import type { Page } from '../App'
import { api } from '../lib/api'
import { fetchQuestions, StoredQuestion } from '../lib/types'
import { registerChromeQuestionTools } from '../lib/chromeQuestionWebMcp'
import { normalizeChromeQuestionResult, normalizeChromeQuestion, WikiQuestionV1, wikiQuestionToLegacy } from '../lib/wikiQuestion'
import QuestionCard from './QuestionCard'

type Context = { school:string; grade:string; year:number; semester:string; exam:string }
type Material = { id:number; title:string; original_name?:string; html_text?:string|null; json_text?:unknown }
type Insight = { id:number; content:string; source:string; created_at:string }
type WorkspaceContext = { exam_context:Context; digital_twin:any; school_prints:Material[]; class_insights:Insight[] }
type PreviewPayload = { question?:unknown; revision_note?:string }

const emptyContext:Context={school:'',grade:'',year:new Date().getFullYear(),semester:'1학기',exam:'전체'}
const canonicalToStored=(question:WikiQuestionV1,source:StoredQuestion):StoredQuestion=>{const legacy=wikiQuestionToLegacy(question);return{
  ...source,id:0,mode:'transform',q_kind:legacy.type,question_type:legacy.questionType,source_text:legacy.sourceText,stem:legacy.stem,
  options:legacy.options,answer:legacy.answer,multi:legacy.multi?1:0,score:legacy.score,conditions:legacy.conditions,answer_format:legacy.answerFormat,
  accepted_answers:legacy.acceptedAnswers,sample_answer:legacy.sampleAnswer,grading_rule:legacy.gradingRule,canonical:question,
  schema_version:question.schema_version,passage_status:question.passage.status,created_at:new Date().toISOString(),
}}

export default function ChromeQuestionWorkbench({onNavigate}:{onNavigate:(page:Page)=>void}){
  const [context]=useState<Context>(()=>{try{return JSON.parse(sessionStorage.getItem('buildit_chrome_context')||'null')||emptyContext}catch{return emptyContext}})
  const [workspace,setWorkspace]=useState<WorkspaceContext|null>(null)
  const [questions,setQuestions]=useState<StoredQuestion[]>([])
  const [index,setIndex]=useState(0)
  const [materialIds,setMaterialIds]=useState<number[]>([]),[insightIds,setInsightIds]=useState<number[]>([])
  const [expanded,setExpanded]=useState<Set<string>>(()=>new Set(['twin']))
  const [preview,setPreview]=useState<StoredQuestion|null>(null),[revisionNote,setRevisionNote]=useState('')
  const [resultInput,setResultInput]=useState('')
  const [view,setView]=useState<'original'|'transformed'>('original'),[status,setStatus]=useState('자료를 불러오는 중입니다.')
  const current=questions[index]
  useEffect(()=>{
    const query=new URLSearchParams({...context,year:String(context.year)} as any).toString()
    Promise.all([
      api(`workspaces/final/context?${query}`).then(r=>r.ok?r.json():Promise.reject(new Error('참고자료를 불러오지 못했습니다.'))),
      fetchQuestions({school:context.school,grade:context.grade,exam:context.exam}),
    ]).then(([ctx,rows])=>{setWorkspace(ctx);setQuestions(rows);setExpanded(new Set(['twin']));setStatus(rows.length?`${rows.length}개 DB 문제를 불러왔습니다.`:'현재 Context에 저장된 문제가 없습니다.')}).catch(e=>setStatus(e instanceof Error?e.message:'자료를 불러오지 못했습니다.'))
  },[context])
  useEffect(()=>{void registerChromeQuestionTools().then(ok=>{if(ok)setStatus(s=>`${s} Chrome AI 연결 준비가 완료되었습니다.`)},()=>setStatus(s=>`${s} WebMCP 연결은 사용할 수 없습니다. 변형 결과 입력을 사용하세요.`))},[])
  const toggleReference=(key:string)=>setExpanded(current=>{const next=new Set(current);next.has(key)?next.delete(key):next.add(key);return next})
  const included=useMemo(()=>({
    exam_context:context,digital_twin:workspace?.digital_twin||null,
    school_prints:(workspace?.school_prints||[]).filter(m=>materialIds.includes(m.id)),
    class_insights:(workspace?.class_insights||[]).filter(i=>insightIds.includes(i.id)),source_question:current?.canonical||null,source_question_id:current?.id||null,
  }),[context,workspace,materialIds,insightIds,current])
  useEffect(()=>{sessionStorage.setItem('buildit_chrome_workbench_context',JSON.stringify(included))},[included])
  useEffect(()=>{setPreview(null);setRevisionNote('');setResultInput('');setView('original');sessionStorage.removeItem('buildit_chrome_preview_question')},[current?.id])
  const showPreview=(canonical:WikiQuestionV1,note='')=>{
    if(!current)return
    setPreview(canonicalToStored(canonical,current));setRevisionNote(note);setView('transformed')
    sessionStorage.setItem('buildit_chrome_preview_question',JSON.stringify({source_question_id:current.id,question:canonical}))
    setStatus('Chrome AI 변형안을 Preview에 반영했습니다.')
  }
  useEffect(()=>{
    const onPreview=(event:Event)=>{const detail=(event as CustomEvent<PreviewPayload>).detail;if(!current||!detail?.question)return;showPreview(normalizeChromeQuestion(detail.question),detail.revision_note||'')}
    const onSaved=()=>setStatus('변형본을 기존 강사 문제 DB에 저장했습니다. STEP 3 저장된 문제와 실전 제작에서 사용할 수 있습니다.')
    window.addEventListener('buildit-chrome-question-preview',onPreview);window.addEventListener('buildit-chrome-question-saved',onSaved)
    return()=>{window.removeEventListener('buildit-chrome-question-preview',onPreview);window.removeEventListener('buildit-chrome-question-saved',onSaved)}
  },[current])
  const previewResult=()=>{
    if(!current){setStatus('Preview할 원본 문제를 먼저 불러와 주세요.');return}
    try{showPreview(normalizeChromeQuestionResult(resultInput));setStatus('입력한 변형 결과를 검증하여 Preview에 반영했습니다.')}
    catch(error){setStatus(error instanceof Error?error.message:'변형 결과 JSON을 확인해 주세요.')}
  }
  const save=async()=>{if(!current||!preview?.canonical)return;setStatus('변형본을 저장하는 중입니다.');const canonical=preview.canonical;const provenance={source_question_id:current.id,digital_twin_id:workspace?.digital_twin?.id||null,material_ids:materialIds,insight_ids:insightIds,revision_note:revisionNote,workbench:'chrome-question-transform'};const response=await api('generated-tests',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({school:current.school,grade:current.grade,year:context.year,semester:context.semester,exam:current.exam,source_material:`기존 문제 QID ${current.id}`,source_page:`question:${current.id}`,questions:[wikiQuestionToLegacy(canonical)],test_type:'chrome-transform',title:`Chrome 변형 · QID ${current.id}`,digital_twin:workspace?.digital_twin?.id?`Digital Twin #${workspace.digital_twin.id}`:'',twin_pattern:JSON.stringify(provenance),target_passage:revisionNote,external_id:`chrome_${Date.now()}_${Math.random().toString(16).slice(2)}`,generation_source:'transform'})});const body=await response.json().catch(()=>({}));setStatus(response.ok?'변형본을 저장했습니다. 저장된 문제와 실전 제작 흐름에 연결되었습니다.':body.detail||'저장하지 못했습니다.')}
  return <section className="chrome-workbench">
    <header className="chrome-workbench-head"><div><p className="kicker">WIKI STEP 3 · CHROME AI</p><h2>크롬 문제 변형 Workbench</h2><p>{context.school} · {context.grade} · {context.year}년 · {context.semester} · {context.exam}</p></div><button onClick={()=>onNavigate('state')}>← STEP 3로 돌아가기</button></header>
    <p className="tool-notice" role="status">{status}</p>
    <div className="chrome-workbench-grid">
      <div className="chrome-source-pane">
        <section className="chrome-reference-panel"><h3>AI 참고자료 <small>선택한 실제 내용만 포함</small></h3>
          <Reference title="Digital Twin" checked={Boolean(workspace?.digital_twin)} active={expanded.has('twin')} onOpen={()=>toggleReference('twin')}><pre>{JSON.stringify(workspace?.digital_twin||{},null,2)}</pre></Reference>
          {(workspace?.class_insights||[]).map(i=><Reference key={`i-${i.id}`} title={`${i.source==='school_teacher'?'학교 선생님':'원장'} Insight #${i.id}`} checked={insightIds.includes(i.id)} onCheck={()=>setInsightIds(v=>v.includes(i.id)?v.filter(x=>x!==i.id):[...v,i.id])} active={expanded.has(`i-${i.id}`)} onOpen={()=>toggleReference(`i-${i.id}`)}><p>{i.content}</p></Reference>)}
          {(workspace?.school_prints||[]).map(m=><Reference key={`m-${m.id}`} title={m.title||m.original_name||`학습지 #${m.id}`} checked={materialIds.includes(m.id)} onCheck={()=>setMaterialIds(v=>v.includes(m.id)?v.filter(x=>x!==m.id):[...v,m.id])} active={expanded.has(`m-${m.id}`)} onOpen={()=>toggleReference(`m-${m.id}`)}><div className="reference-html" dangerouslySetInnerHTML={{__html:m.html_text||'<p>저장된 HTML 내용이 없습니다. JSON 내용을 확인하세요.</p>'}}/><pre>{m.json_text?JSON.stringify(m.json_text,null,2):''}</pre></Reference>)}
        </section>
        <section className="chrome-question-panel"><div className="question-nav"><button disabled={index<=0} onClick={()=>setIndex(i=>i-1)}>← 이전 문제</button><b>{questions.length?`${index+1} / ${questions.length} · QID ${current?.id}`:'문제 없음'}</b><button disabled={index>=questions.length-1} onClick={()=>setIndex(i=>i+1)}>다음 문제 →</button></div>
          <div className="view-tabs"><button className={view==='original'?'active':''} onClick={()=>setView('original')}>원본 보기</button><button className={view==='transformed'?'active':''} disabled={!preview} onClick={()=>setView('transformed')}>변형본 보기</button></div>
          <div className="f2r-gen embedded-question-view"><div className="exam-sheet show-answers"><div className="question-list">{(view==='transformed'?preview:current)&&<QuestionCard q={(view==='transformed'?preview:current)!} index={1}/>}</div></div></div>
        </section>
      </div>
      <aside className="chrome-ai-pane"><h3>Chrome AI 작업 영역</h3><p>Chrome AI가 만든 구조화 결과를 아래에 붙여넣어 Preview합니다. 지원되는 WebMCP 도구는 기존 호환 경로로 유지됩니다.</p><div className="included-context"><b>현재 AI Context</b><span>Digital Twin {workspace?.digital_twin?'포함':'없음'}</span><span>Insight {insightIds.length}개</span><span>학습지 {materialIds.length}개</span><span>원본 QID {current?.id||'-'}</span></div>
        <label className="chrome-result-input"><b>변형 결과 입력</b><span>Chrome AI가 생성한 JSON을 붙여넣으세요.</span><textarea value={resultInput} onChange={event=>setResultInput(event.target.value)} rows={14} placeholder={'{"type":"서답형","instruction":"...","passage":{"text":"...","html":"...","status":"complete"},"model_answer":"..."}'}/></label>
        <details className="chrome-output-format"><summary>Chrome AI 출력 형식</summary><pre>{`직접 문항 JSON
{ "type": "서답형", "instruction": "...", "passage": {...}, "model_answer": "..." }

또는 wrapper
{ "action": "UPDATE_VARIANT_QUESTION", "data": { ...문항 JSON... } }`}</pre></details>
        <div className="chrome-ai-actions"><button disabled={!resultInput.trim()||!current} onClick={previewResult}>Preview</button><button onClick={()=>setView('original')}>원본과 다시 비교</button><button className="primary-action" disabled={!preview} onClick={()=>void save()}>변형본 저장</button></div>
        {preview?.canonical&&<details><summary>개발자용 canonical JSON</summary><pre>{JSON.stringify(preview.canonical,null,2)}</pre></details>}
      </aside>
    </div>
  </section>
}

function Reference({title,checked,onCheck,active,onOpen,children}:{title:string;checked:boolean;onCheck?:()=>void;active:boolean;onOpen:()=>void;children:ReactNode}){
  return <article className={`chrome-reference${active?' expanded':''}`}><header>{onCheck?<input type="checkbox" aria-label={`${title} AI Context 포함`} checked={checked} onChange={onCheck}/>:<span className="context-on">포함</span>}<button aria-expanded={active} onClick={onOpen}>{title}<span>{active?'접기':'내용 보기'}</span></button></header><div className={`chrome-reference-content${active?'':' collapsed'}`}>{children}</div></article>
}


