import { ReactNode, useEffect, useMemo, useState } from 'react'
import type { Page } from '../App'
import { api } from '../lib/api'
import { fetchQuestions, fetchTextbookQuestions, StoredQuestion } from '../lib/types'
import { registerChromeQuestionTools } from '../lib/chromeQuestionWebMcp'
import { generateWithChromeBuiltInAi, getChromeAiRuntimeStatus, type QuestionPreset, type ChromeAiRuntimeStatus } from '../lib/chromeBuiltInAi'
import { buildTransformProvenance, questionOrigin, type SourceKind } from '../lib/questionSource'
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
  const [source,setSource]=useState<SourceKind>('teacher')
  const [tbFilter,setTbFilter]=useState({publisher:'',lesson:'',question_type:''})
  const [index,setIndex]=useState(0)
  const [materialIds,setMaterialIds]=useState<number[]>([]),[insightIds,setInsightIds]=useState<number[]>([])
  const [expanded,setExpanded]=useState<Set<string>>(()=>new Set(['twin']))
  const [preview,setPreview]=useState<StoredQuestion|null>(null),[revisionNote,setRevisionNote]=useState('')
  const [resultInput,setResultInput]=useState('')
  const [preset,setPreset]=useState<QuestionPreset>('similar'),[freeRequest,setFreeRequest]=useState(''),[schoolPattern,setSchoolPattern]=useState('')
  const [runtime,setRuntime]=useState<ChromeAiRuntimeStatus>({prompt:'unavailable',koToEn:'unavailable',enToKo:'unavailable'}),[generating,setGenerating]=useState(false)
  const [view,setView]=useState<'original'|'transformed'>('original'),[status,setStatus]=useState('자료를 불러오는 중입니다.')
  const current=questions[index]
  useEffect(()=>{
    const query=new URLSearchParams({...context,year:String(context.year)} as any).toString()
    api(`workspaces/final/context?${query}`).then(r=>r.ok?r.json():Promise.reject(new Error('참고자료를 불러오지 못했습니다.')))
      .then((ctx:WorkspaceContext)=>{setWorkspace(ctx);setExpanded(new Set(['twin',...(ctx.class_insights||[]).map((i:Insight)=>`i-${i.id}`),...(ctx.school_prints||[]).map((m:Material)=>`m-${m.id}`)]))})
      .catch(e=>setStatus(e instanceof Error?e.message:'참고자료를 불러오지 못했습니다.'))
  },[context])
  // Source(강사 문제 DB | 교과서 DB | 전체)만 다르고 이후 pipeline 은 하나. 두 Source 모두
  // wiki-question-v1 canonical + StoredQuestion 형태라 QuestionCard/변형/저장 코드가 분기하지 않는다.
  useEffect(()=>{
    setIndex(0)
    const teacher=source==='textbook'?Promise.resolve([]):fetchQuestions({school:context.school,grade:context.grade,exam:context.exam}).then(rows=>rows.map(r=>({...r,source_kind:'teacher' as const})))
    const tb=source==='teacher'?Promise.resolve([]):fetchTextbookQuestions({...(tbFilter.publisher?{publisher:tbFilter.publisher}:{}),...(tbFilter.lesson?{lesson:tbFilter.lesson}:{}),...(tbFilter.question_type?{question_type:tbFilter.question_type}:{}),...(context.grade?{grade:context.grade}:{})})
    Promise.all([teacher,tb]).then(([t,b])=>{
      const rows=source==='textbook'?b:source==='teacher'?t:[...t,...b]
      setQuestions(rows)
      setStatus(rows.length?`${rows.length}개 문제를 불러왔습니다 (강사 ${t.length} · 교과서 ${b.length}).`:'선택한 Source에 문제가 없습니다.')
    }).catch(e=>setStatus(e instanceof Error?e.message:'문제를 불러오지 못했습니다.'))
  },[context,source,tbFilter])
  useEffect(()=>{void registerChromeQuestionTools().then(ok=>{if(ok)setStatus(s=>`${s} Chrome AI 연결 준비가 완료되었습니다.`)},()=>setStatus(s=>`${s} WebMCP 연결은 사용할 수 없습니다. 변형 결과 입력을 사용하세요.`))},[])
  useEffect(()=>{void getChromeAiRuntimeStatus().then(setRuntime)},[])
  const toggleReference=(key:string)=>setExpanded(current=>{const next=new Set(current);next.has(key)?next.delete(key):next.add(key);return next})
  const included=useMemo(()=>({
    exam_context:context,digital_twin:workspace?.digital_twin||null,
    school_prints:(workspace?.school_prints||[]).filter(m=>materialIds.includes(m.id)),
    class_insights:(workspace?.class_insights||[]).filter(i=>insightIds.includes(i.id)),source_question:current?.canonical||null,source_question_id:current?.id||null,
    source_question_meta:current?{source_kind:current.source_kind==='textbook'?'textbook':'teacher',id:current.id,textbook_material_id:current.textbook_material_id||null,page:current.page||null,question_number:current.question_number||'',publisher:current.publisher||'',lesson:current.lesson||'',school:current.school||'',grade:current.grade||'',exam:current.exam||''}:null,
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
  const generate=async()=>{
    if(!current)return
    setGenerating(true);setStatus('Chrome 기기 내 AI로 문제를 생성하는 중입니다.')
    try{
      const result=await generateWithChromeBuiltInAi({preset,freeRequest,schoolPatternReference:schoolPattern,contextPack:included,sourceQuestionId:current.id,onDownloadProgress:(name,loaded)=>setStatus(`${name} 모델 다운로드 ${Math.round(loaded*100)}%`)})
      const canonical=normalizeChromeQuestion({...result.question,explanation:result.question.explanationKo||result.question.explanation})
      const note=JSON.stringify({generationMode:'CHROME_BUILT_IN_AI',requestedType:preset,userInstruction:freeRequest,schoolPatternReferenceUsed:Boolean(schoolPattern),englishExplanation:result.question.explanation})
      showPreview(canonical,note);setResultInput(JSON.stringify(result.question,null,2));setStatus(result.notice||'Chrome 기기 내 AI 생성 결과를 Preview에 반영했습니다. 저장 전 내용을 확인하세요.')
    }catch(error){setStatus(error instanceof Error?error.message:'Chrome AI 문제 생성에 실패했습니다.')}finally{setGenerating(false)}
  }
  const save=async()=>{
    if(!current||!preview?.canonical)return
    setStatus('변형본을 저장하는 중입니다.')
    const {kind,provenance,source_material,source_page}=buildTransformProvenance(current,context,{digital_twin_id:workspace?.digital_twin?.id||null,material_ids:materialIds,insight_ids:insightIds,revision_note:revisionNote})
    // 교과서 원본은 절대 덮어쓰지 않는다 — 별도 generated_test row로만 저장된다.
    const response=await api('generated-tests',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({
      school:current.school||context.school,grade:current.grade||context.grade,year:context.year,semester:context.semester,exam:current.exam||context.exam||'전체',
      publisher:current.publisher||'',lesson:current.lesson||'',
      source_material,source_page,questions:[wikiQuestionToLegacy(preview.canonical)],test_type:'chrome-transform',
      title:`Chrome 변형 · ${kind==='textbook'?'교과서 ':''}QID ${current.id}`,
      digital_twin:workspace?.digital_twin?.id?`Digital Twin #${workspace.digital_twin.id}`:'',twin_pattern:JSON.stringify(provenance),
      target_passage:revisionNote,external_id:`chrome_${Date.now()}_${Math.random().toString(16).slice(2)}`,generation_source:'transform',
    })})
    const body=await response.json().catch(()=>({}))
    setStatus(response.ok?`변형본을 저장했습니다 (원본: ${questionOrigin(current)}). 저장된 문제와 실전 제작 흐름에 연결되었습니다.`:body.detail||'저장하지 못했습니다.')
  }
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
        <section className="chrome-question-panel">
          <div className="chrome-source-tabs" role="group" aria-label="문제 Source">
            {(['teacher','textbook','all'] as SourceKind[]).map(k=><button key={k} className={source===k?'active':''} onClick={()=>setSource(k)}>{k==='teacher'?'강사 문제 DB':k==='textbook'?'교과서 DB':'전체'}</button>)}
          </div>
          {source!=='teacher'&&<div className="chrome-textbook-filter"><input aria-label="교과서 출판사" placeholder="출판사" value={tbFilter.publisher} onChange={e=>setTbFilter(f=>({...f,publisher:e.target.value}))}/><input aria-label="교과서 단원" placeholder="단원(Lesson)" value={tbFilter.lesson} onChange={e=>setTbFilter(f=>({...f,lesson:e.target.value}))}/><input aria-label="교과서 문제 유형" placeholder="유형" value={tbFilter.question_type} onChange={e=>setTbFilter(f=>({...f,question_type:e.target.value}))}/></div>}
          <div className="question-nav"><button disabled={index<=0} onClick={()=>setIndex(i=>i-1)}>← 이전 문제</button><b>{questions.length?`${index+1} / ${questions.length}`:'문제 없음'}</b><button disabled={index>=questions.length-1} onClick={()=>setIndex(i=>i+1)}>다음 문제 →</button></div>
          {current&&<p className="chrome-question-origin" aria-label="현재 문제 출처">{questionOrigin(current)}</p>}
          <div className="view-tabs"><button className={view==='original'?'active':''} onClick={()=>setView('original')}>원본 보기</button><button className={view==='transformed'?'active':''} disabled={!preview} onClick={()=>setView('transformed')}>변형본 보기</button></div>
          <div className="f2r-gen embedded-question-view"><div className="exam-sheet show-answers"><div className="question-list">{(view==='transformed'?preview:current)&&<QuestionCard q={(view==='transformed'?preview:current)!} index={1}/>}</div></div></div>
        </section>
      </div>
      <aside className="chrome-ai-pane"><h3>✨ AI 문제제작</h3><p>현재 문항 Context만 사용하여 Chrome 기기 안에서 문제를 만듭니다. Preview 전에는 저장되지 않습니다.</p><div className="chrome-runtime-status" aria-label="Chrome AI runtime 상태"><b>기기 내 AI 상태</b><span>Prompt AI: {runtime.prompt}</span><span>번역 ko→en: {runtime.koToEn}</span><span>번역 en→ko: {runtime.enToKo}</span></div><div className="included-context"><b>현재 AI Context</b><span>Digital Twin {workspace?.digital_twin?'포함':'없음'}</span><span>Insight {insightIds.length}개</span><span>학습지 {materialIds.length}개</span><span>원본 QID {current?.id||'-'}</span></div>
        <label><b>문제 유형</b><select aria-label="문제 유형" value={preset} onChange={e=>setPreset(e.target.value as QuestionPreset)}>{[['similar','유사 변형'],['grammar','어법'],['vocabulary','어휘'],['blank','빈칸'],['order','글의 순서'],['insertion','문장 삽입'],['title-topic','제목/주제'],['subjective','서술형']].map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></label>
        <label><b>자유 요청</b><textarea aria-label="자유 요청" rows={3} value={freeRequest} onChange={e=>setFreeRequest(e.target.value)} placeholder="예: 학교 시험처럼 어법 5지선다 문제를 만들어줘"/></label>
        <label><b>학교 유형 예시</b><span>내용 출처가 아니라 문제 구조 참고용으로만 사용합니다.</span><textarea aria-label="학교 유형 예시" rows={5} value={schoolPattern} onChange={e=>setSchoolPattern(e.target.value)} placeholder="학교의 독특한 발문·선택지·답안 형식 예시를 붙여넣으세요."/></label>
        <button className="primary-action" disabled={!current||generating||runtime.prompt==='unavailable'||runtime.prompt==='error'} onClick={()=>void generate()}>{generating?'생성 중…':'문제 생성'}</button>
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


