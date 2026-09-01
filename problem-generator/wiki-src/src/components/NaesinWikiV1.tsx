import { ChangeEvent, FormEvent, ReactNode, useEffect, useRef, useState } from 'react'
import { api } from '../lib/api'
import { DigitalTwinProfile, exportExamMaterialWrongNotesOffice, fetchQuestions, fetchSchoolTree, fetchStudents, SchoolNode, StoredQuestion, Student } from '../lib/types'
import type { Page } from '../App'

type Context = { school:string; grade:string; year:number; semester:string; exam:string }
type Material = { id:number; core_type:'school_print'|'student_wrong_note'; student_id:number|null; student_name:string|null; title:string; original_name:string; created_at:string; has_html?:number }
type Insight = { id:number; content:string; source:'director'|'school_teacher'; created_at:string }
type FinalDeliverable = { id:number; kind:'final'|'jikbo'; school:string; grade:string; year:number; semester:string; exam:string; title:string; original_name:string; created_at:string; has_html?:number }
type Workspace = 'final'|'jikbo'|null
const years = [new Date().getFullYear(), new Date().getFullYear()-1, new Date().getFullYear()-2]
// 화면 표시는 전체/중간/기말이지만 저장·조회 값은 기존 Digital Twin·Assistant·exam_material_sets가
// 이미 쓰고 있는 '전체'/'중간고사'/'기말고사'를 그대로 재사용한다 (main.py의 중간→중간고사 정규화와 동일 규칙).
const EXAM_OPTIONS: { label: string; value: string }[] = [
  { label: '전체', value: '전체' },
  { label: '중간', value: '중간고사' },
  { label: '기말', value: '기말고사' },
]
const downloadBlob = (filename: string, blob: Blob) => { const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url) }
const MAX_ORIGINALS = 20
const contextQuery = (context: Context) => new URLSearchParams({
  school: context.school,
  grade: context.grade,
  year: String(context.year),
  semester: context.semester,
  exam: context.exam,
}).toString()
const apiErrorMessage = (body: unknown, fallback: string) => {
  if (!body || typeof body !== 'object') return fallback
  const detail = (body as { detail?: unknown }).detail
  if (typeof detail === 'string') return detail
  if (detail && typeof detail === 'object' && !Array.isArray(detail)) {
    const message = (detail as { message?: unknown }).message
    if (typeof message === 'string') return message
  }
  if (Array.isArray(detail)) {
    const messages = detail.map(item => item && typeof item === 'object' ? (item as { msg?: unknown }).msg : null).filter((message): message is string => typeof message === 'string')
    if (messages.length) return `${fallback} (${[...new Set(messages)].join(', ')})`
  }
  return fallback
}

export default function NaesinWikiV1({onNavigate}:{onNavigate:(page:Page)=>void}) {
  const [tree,setTree]=useState<SchoolNode[]>([]), [students,setStudents]=useState<Student[]>([])
  const [context,setContext]=useState<Context>({school:'',grade:'',year:years[0],semester:'1학기',exam:'전체'})
  const [questions,setQuestions]=useState<StoredQuestion[]>([]), [materials,setMaterials]=useState<Material[]>([]), [insights,setInsights]=useState<Insight[]>([])
  const [finals,setFinals]=useState<FinalDeliverable[]>([]), [twinProfile,setTwinProfile]=useState<DigitalTwinProfile|null>(null)
  const [core,setCore]=useState<'school_print'|'student_wrong_note'|null>(null), [workspace,setWorkspace]=useState<Workspace>(null), [notice,setNotice]=useState('')
  const [studentId,setStudentId]=useState(''), [title,setTitle]=useState(''), [originals,setOriginals]=useState<File[]>([]), [jsonFile,setJsonFile]=useState<File|null>(null), [jsonText,setJsonText]=useState('')
  const [htmlText,setHtmlText]=useState('')
  const [finalTitle,setFinalTitle]=useState(''), [finalHtmlText,setFinalHtmlText]=useState(''), [finalOriginal,setFinalOriginal]=useState<File|null>(null), [finalJsonFile,setFinalJsonFile]=useState<File|null>(null), [finalJsonText,setFinalJsonText]=useState('')
  const [savingFinal,setSavingFinal]=useState(false)
  const [insightText,setInsightText]=useState(''), [insightSource,setInsightSource]=useState<'director'|'school_teacher'>('director')
  const [selected,setSelected]=useState<Set<string>>(new Set())
  const [exportingWrong,setExportingWrong]=useState(false)
  const [savingSchoolPrint,setSavingSchoolPrint]=useState(false)
  const [aiContext,setAiContext]=useState<{workspace:'final'|'jikbo';json:string}|null>(null)
  const [loadingAiContext,setLoadingAiContext]=useState(false)
  // fetchSchoolTree()가 늦게 끝나면(운영 네트워크 지연) 사용자가 이미 고른 학교/학년/시험을
  // 마운트 시점 기본값(setContext(...exam:'전체'))이 뒤늦게 덮어써 버리는 경합이 있었다.
  // STEP1에서 학교 프린트를 저장한 뒤 목록을 다시 불러오면 방금 고른 시험이 아니라 '전체' 기준으로
  // 조회되어 자료가 안 보이는(빈 화면처럼 보이는) 원인이었다. 사용자가 이미 손댄 필드는
  // 기본값으로 되돌리지 않도록 어떤 필드를 직접 선택했는지 추적한다.
  const touchedRef = useRef({school:false,grade:false,exam:false})
  const schoolNode=tree.find(item=>item.school===context.school), grades=schoolNode?.grades||[]
  const examLabel=EXAM_OPTIONS.find(o=>o.value===context.exam)?.label||context.exam
  // 출제자 Digital Twin은 exam_material_sets처럼 새로 저장하지 않고, 이미 school/grade/exam
  // 계층으로 저장돼 있는 digital_twin_profiles를 시험 Context 기준으로 찾아 연결만 한다.
  // 정확히 일치하는 시험(중간고사/기말고사)에 Twin이 없으면 '전체' Twin으로 대체한다 —
  // BuildIt Assistant(_assistant_context)가 이미 쓰는 것과 같은 우선순위 규칙이다.
  const gradeNode=grades.find(g=>g.grade===context.grade)
  const twinId=gradeNode?.exams.find(e=>e.exam===context.exam&&e.hasDigitalTwin)?.twinId
    ??gradeNode?.exams.find(e=>e.exam==='전체'&&e.hasDigitalTwin)?.twinId
    ??null
  const load=async(targetContext:Context)=>{
    if(!targetContext.school||!targetContext.grade||!targetContext.exam)return false
    const query=contextQuery(targetContext)
    const [q,materialResponse,i,finalRows]=await Promise.all([
      fetchQuestions({school:targetContext.school,grade:targetContext.grade,exam:targetContext.exam}),
      api(`exam-materials?${query}`),
      api(`educator-insights?limit=100&${query}`).then(r=>r.ok?r.json():[]),
      api(`final-deliverables?kind=final&${query}`).then(r=>r.ok?r.json():[]),
    ])
    if(!materialResponse.ok){
      const body=await materialResponse.json().catch(()=>({}))
      setNotice(apiErrorMessage(body,'현재 시험의 학교 프린트를 불러오지 못했습니다.'))
      setQuestions(q);setInsights(i);setFinals(Array.isArray(finalRows)?finalRows:[]);return false
    }
    const m=await materialResponse.json()
    setQuestions(q);setMaterials(Array.isArray(m)?m:[]);setInsights(i);setFinals(Array.isArray(finalRows)?finalRows:[])
    return true
  }
  useEffect(()=>{fetchSchoolTree().then(nodes=>{setTree(nodes);const first=nodes[0], grade=first?.grades[0];const t=touchedRef.current;if(first)setContext(c=>({...c,school:t.school?c.school:first.school,grade:t.grade?c.grade:(grade?.grade||''),exam:t.exam?c.exam:'전체'}))});fetchStudents().then(setStudents)},[])
  useEffect(()=>{void load(context)},[context.school,context.grade,context.year,context.semester,context.exam])
  useEffect(()=>{
    if(!twinId){setTwinProfile(null);return}
    let cancelled=false
    api(`digital-twins/${twinId}`).then(r=>r.ok?r.json():null).then(profile=>{if(!cancelled)setTwinProfile(profile)}).catch(()=>{if(!cancelled)setTwinProfile(null)})
    return ()=>{cancelled=true}
  },[twinId])
  // behavior:'smooth'는 requestAnimationFrame 기반 애니메이션이라 조건부로 막 마운트된
  // 대상에 걸면 프레임이 씹혀 실제로는 전혀 스크롤되지 않는 경우가 있었다(운영에서 확인됨).
  // behavior:'auto'는 애니메이션 없이 즉시 오프셋을 적용해 신뢰성 있게 동작한다.
  useEffect(()=>{if(core)document.getElementById('material-set-form')?.scrollIntoView({behavior:'auto',block:'start'})},[core])
  const filteredStudents=students.filter(s=>(!s.school||s.school===context.school)&&(!s.grade||s.grade===context.grade))
  const prints=materials.filter(m=>m.core_type==='school_print'), wrongs=materials.filter(m=>m.core_type==='student_wrong_note')
  const resetUpload=()=>{setOriginals([]);setJsonFile(null);setJsonText('');setTitle('');setHtmlText('')}
  const chooseOriginals=(e:ChangeEvent<HTMLInputElement>)=>{const selected=Array.from(e.target.files||[]);e.target.value='';if(!selected.length)return;const merged=[...originals];let added=0,skipped=0;for(const file of selected){const duplicate=merged.some(item=>item.name===file.name&&item.size===file.size&&item.lastModified===file.lastModified);if(duplicate)continue;if(merged.length>=MAX_ORIGINALS){skipped+=1;continue}merged.push(file);added+=1}setOriginals(merged);setNotice(skipped?`원본 자료는 한 번에 최대 ${MAX_ORIGINALS}개까지 선택할 수 있습니다. ${skipped}개를 제외했습니다.`:added?`${added}개 파일을 선택했습니다.`:'')}
  const removeOriginal=(index:number)=>setOriginals(current=>current.filter((_,i)=>i!==index))
  const chooseHtmlFile=(e:ChangeEvent<HTMLInputElement>)=>{
    const file=e.target.files?.[0];e.target.value='';if(!file)return
    const reader=new FileReader()
    reader.onload=()=>{setHtmlText(String(reader.result||''));setNotice('HTML 파일 내용을 불러왔습니다. 필요하면 아래에서 수정한 뒤 저장하세요.')}
    reader.onerror=()=>setNotice('HTML 파일을 읽지 못했습니다.')
    reader.readAsText(file)
  }
  const saveSchoolPrint=async(e:FormEvent)=>{
    e.preventDefault()
    if(savingSchoolPrint)return
    if(!htmlText.trim()){setNotice('학교 프린트 HTML을 입력하거나 HTML 파일을 선택해 주세요.');return}
    if(!context.school||!context.grade||!context.exam){setNotice('학교·학년·시험 Context를 먼저 선택해 주세요.');return}
    const savedContext={...context}
    const form=new FormData()
    Object.entries(savedContext).forEach(([k,v])=>form.append(k,String(v)))
    form.append('core_type','school_print')
    form.append('title',title)
    form.append('html_text',htmlText)
    if(jsonFile)form.append('json_file',jsonFile)
    form.append('json_text',jsonText)
    setSavingSchoolPrint(true);setNotice('학교 프린트를 저장하는 중입니다.')
    try{
      const r=await api('exam-materials',{method:'POST',body:form})
      const b=await r.json().catch(()=>({}))
      if(!r.ok){setNotice(apiErrorMessage(b,'학교 프린트를 저장하지 못했습니다.'));return}
      if(!await load(savedContext))return
      resetUpload();setCore(null);setNotice('HTML과 JSON을 한 세트로 저장했습니다.')
    }catch(error){setNotice(error instanceof Error?`학교 프린트를 저장하지 못했습니다: ${error.message}`:'학교 프린트를 저장하지 못했습니다.')}
    finally{setSavingSchoolPrint(false)}
  }
  const saveStudentWrongNote=async(e:FormEvent)=>{
    e.preventDefault();if(!originals.length)return
    let failed=0
    for(const file of originals){
      const form=new FormData()
      Object.entries(context).forEach(([k,v])=>form.append(k,String(v)))
      form.append('core_type','student_wrong_note')
      form.append('title',originals.length>1&&title.trim()?`${title.trim()} · ${file.name}`:title)
      form.append('original',file)
      if(jsonFile)form.append('json_file',jsonFile)
      form.append('json_text',jsonText)
      form.append('student_id',studentId)
      const r=await api('exam-materials',{method:'POST',body:form})
      if(!r.ok)failed+=1
    }
    if(failed===originals.length){setNotice('저장하지 못했습니다.');return}
    const saved=originals.length-failed
    resetUpload();setCore(null)
    setNotice(failed?`원본 ${saved}개를 저장했습니다. ${failed}개는 저장하지 못했습니다.`:`원본 ${saved}개와 JSON을 한 세트로 저장했습니다.`)
    await load({...context})
  }
  const saveMaterial=(e:FormEvent)=>core==='school_print'?saveSchoolPrint(e):saveStudentWrongNote(e)
  const resetFinalUpload=()=>{setFinalTitle('');setFinalHtmlText('');setFinalOriginal(null);setFinalJsonFile(null);setFinalJsonText('')}
  const chooseFinalOriginal=(e:ChangeEvent<HTMLInputElement>)=>{const file=e.target.files?.[0]||null;e.target.value='';setFinalOriginal(file)}
  const chooseFinalHtmlFile=(e:ChangeEvent<HTMLInputElement>)=>{
    const file=e.target.files?.[0];e.target.value='';if(!file)return
    const reader=new FileReader()
    reader.onload=()=>{setFinalHtmlText(String(reader.result||''));setNotice('HTML 파일 내용을 불러왔습니다. 필요하면 아래에서 수정한 뒤 저장하세요.')}
    reader.onerror=()=>setNotice('HTML 파일을 읽지 못했습니다.')
    reader.readAsText(file)
  }
  const saveFinalDeliverable=async(e:FormEvent)=>{
    e.preventDefault()
    if(savingFinal)return
    if(!finalOriginal&&!finalHtmlText.trim()){setNotice('FINAL 결과 파일을 올리거나 HTML을 붙여넣어 주세요.');return}
    if(!context.school||!context.grade||!context.exam){setNotice('학교·학년·시험 Context를 먼저 선택해 주세요.');return}
    const savedContext={...context}
    const form=new FormData()
    Object.entries(savedContext).forEach(([k,v])=>form.append(k,String(v)))
    form.append('kind','final')
    form.append('title',finalTitle)
    if(finalOriginal)form.append('original',finalOriginal)
    form.append('html_text',finalHtmlText)
    if(finalJsonFile)form.append('json_file',finalJsonFile)
    form.append('json_text',finalJsonText)
    setSavingFinal(true);setNotice('FINAL 완성본을 저장하는 중입니다.')
    try{
      const r=await api('final-deliverables',{method:'POST',body:form})
      const b=await r.json().catch(()=>({}))
      if(!r.ok){setNotice(apiErrorMessage(b,'FINAL 완성본을 저장하지 못했습니다.'));return}
      if(!await load(savedContext))return
      resetFinalUpload();setNotice('FINAL 완성본을 저장했습니다.')
    }catch(error){setNotice(error instanceof Error?`FINAL 완성본을 저장하지 못했습니다: ${error.message}`:'FINAL 완성본을 저장하지 못했습니다.')}
    finally{setSavingFinal(false)}
  }
  const saveInsight=async()=>{if(!insightText.trim())return;const r=await api('educator-insights',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...context,content:insightText,source:insightSource,student_context:'',lesson_context:''})});if(r.ok){setInsightText('');setNotice('수업 인사이트를 저장했습니다.');await load({...context})}}
  const toggle=(key:string)=>setSelected(current=>{const next=new Set(current);next.has(key)?next.delete(key):next.add(key);return next})
  const open=(path:string)=>window.open(`/problem-generator/wiki/api/${path}`,'_blank','noopener')
  const openQuestionMaker=(mode:'new'|'transform')=>{sessionStorage.setItem('buildit_assistant_state',JSON.stringify({school:context.school,grade:context.grade,exam:context.exam,mode}));onNavigate('home')}
  const openChromeWorkbench=()=>{sessionStorage.setItem('buildit_chrome_context',JSON.stringify(context));onNavigate('chrometransform')}
  const selectedWrongMaterialIds=wrongs.filter(m=>selected.has(`m-${m.id}`)).map(m=>m.id)
  const exportSelectedWrong=async()=>{
    if(!selectedWrongMaterialIds.length)return
    setExportingWrong(true)
    try{
      const blob=await exportExamMaterialWrongNotesOffice({material_ids:selectedWrongMaterialIds,title:`${context.school} ${context.exam} 학생 오답 복습 문제`.trim()})
      downloadBlob(`${(context.school||'학생')}_오답_복습_문제`.replace(/[\\/:*?"<>|]/g,'_')+'.docx',blob)
      setNotice('선택한 학생 오답을 오피스 2007(.docx)로 내보냈습니다.')
    }catch(err){setNotice(err instanceof Error?err.message:'학생 오답 복습 문제지를 내보내지 못했습니다.')}finally{setExportingWrong(false)}
  }
  const sortedInsights=[...insights].sort((a,b)=>(a.source==='school_teacher'?0:1)-(b.source==='school_teacher'?0:1))
  // "화면에는 자료가 있는데 AI에는 전달되지 않는" 문제를 확인하기 위한 디버깅 장치.
  // 화면을 다시 조합하지 않고 MCP가 실제로 호출할 Context read API를 그대로 호출해 그 응답을 보여준다.
  const showAiContext=async(kind:'final'|'jikbo')=>{
    setLoadingAiContext(true)
    try{
      const r=await api(`workspaces/${kind}/context?${contextQuery(context)}`)
      const b=await r.json().catch(()=>({}))
      if(!r.ok){setNotice(apiErrorMessage(b,'AI Context를 불러오지 못했습니다.'));return}
      setAiContext({workspace:kind,json:JSON.stringify(b,null,2)})
    }catch(error){setNotice(error instanceof Error?`AI Context를 불러오지 못했습니다: ${error.message}`:'AI Context를 불러오지 못했습니다.')}
    finally{setLoadingAiContext(false)}
  }
  return <section className="naesin-v1">
    <header><p className="kicker">BUILD IT · NAESIN WIKI V1.1</p><h1>시험 자료 한눈에 모으기</h1><p>학교와 시험을 먼저 고르면 STEP 1부터 4까지 순서대로 따라가며 시험을 준비할 수 있습니다.</p></header>
    {notice&&<div className="tool-notice" role="status">{notice}<button onClick={()=>setNotice('')}>×</button></div>}
    <div className="exam-context" aria-label="시험 Context">
      <label>학교<select value={context.school} onChange={e=>{const school=e.target.value,node=tree.find(n=>n.school===school),grade=node?.grades[0];touchedRef.current.school=true;touchedRef.current.grade=true;touchedRef.current.exam=true;setContext(c=>({...c,school,grade:grade?.grade||'',exam:'전체'}))}}>{tree.map(n=><option key={n.school}>{n.school}</option>)}</select></label>
      <label>학년<select value={context.grade} onChange={e=>{const grade=e.target.value;touchedRef.current.grade=true;touchedRef.current.exam=true;setContext(c=>({...c,grade,exam:'전체'}))}}>{grades.map(g=><option key={g.grade}>{g.grade}</option>)}</select></label>
      <label>연도<select value={context.year} onChange={e=>setContext(c=>({...c,year:Number(e.target.value)}))}>{years.map(y=><option key={y}>{y}</option>)}</select></label>
      <label>학기<select value={context.semester} onChange={e=>setContext(c=>({...c,semester:e.target.value}))}><option>1학기</option><option>2학기</option></select></label>
      <label>시험<select value={context.exam} onChange={e=>{touchedRef.current.exam=true;setContext(c=>({...c,exam:e.target.value}))}}>{EXAM_OPTIONS.map(opt=><option key={opt.value} value={opt.value}>{opt.label}</option>)}</select></label>
    </div>
    <div className="context-summary"><b>{context.school} · {context.grade}</b><span>{context.year}년 · {context.semester} · {examLabel}</span></div>

    <ol className="wiki-steps">
      <li className="wiki-step">
        <div className="panel-heading"><div><p className="kicker">STEP 1</p><h2>자료 모으기</h2><p>시험 준비에 필요한 자료부터 모아주세요.</p></div></div>
        <div className="twin-actions">
          <button className="primary-action" onClick={()=>setCore('school_print')}>학교 프린트 추가 · 저장 <strong>{prints.length}</strong>건</button>
          <button className="primary-action" onClick={()=>setCore('student_wrong_note')}>학생 오답 추가 · 저장 <strong>{wrongs.length}</strong>건</button>
        </div>
      </li>
      <li className="wiki-step">
        <div className="panel-heading"><div><p className="kicker">STEP 2</p><h2>수업 기록</h2><p>수업하면서 발견한 내용이나 학교 선생님의 강조사항을 남겨주세요.</p></div></div>
        <div className="twin-actions"><button className="primary-action" onClick={()=>document.getElementById('insight-entry')?.scrollIntoView({behavior:'smooth'})}>수업 인사이트 기록 <strong>{insights.length}</strong></button></div>
      </li>
      <li className="wiki-step">
        <div className="panel-heading"><div><p className="kicker">STEP 3</p><h2>문제 준비</h2><p>모아둔 자료와 출제자 Digital Twin을 활용해 시험 문제를 준비합니다.</p></div></div>
        <div className="twin-actions">
          <button className="primary-action" onClick={()=>openQuestionMaker('new')}>문제 만들기</button>
          <button className="primary-action" onClick={()=>openQuestionMaker('transform')}>기존 문제 변형</button>
          <button className="primary-action chrome-transform-entry" onClick={openChromeWorkbench}>크롬 문제 변형</button>
          <button className="primary-action" onClick={()=>{sessionStorage.setItem('buildit_assistant_state',JSON.stringify({school:context.school,grade:context.grade,exam:context.exam,year:context.year,semester:context.semester}));onNavigate('database')}}>저장된 문제 <strong>{questions.length}</strong></button>
        </div>
        <p className="field-hint">저장된 문제를 선택하면 기존 오피스 2007(.docx) 내보내기로 이어집니다. Digital Twin을 새로 등록하려면 <button type="button" className="link-button" onClick={()=>onNavigate('twin')}>Digital Twin 관리</button>로 이동하세요.</p>
      </li>
      <li className="wiki-step">
        <div className="panel-heading"><div><p className="kicker">STEP 4</p><h2>시험 준비</h2><p>지금까지 모은 자료와 문제로 최종 시험을 준비합니다.</p></div></div>
        <div className="twin-actions">
          <button className="primary-action" onClick={()=>setWorkspace(v=>v==='final'?null:'final')}>FINAL 준비</button>
          <button className="primary-action" onClick={()=>setWorkspace(v=>v==='jikbo'?null:'jikbo')}>직보 준비</button>
        </div>
      </li>
    </ol>

    {core&&<form id="material-set-form" className="work-panel material-set-form" onSubmit={saveMaterial}><div className="panel-heading"><div><p className="kicker">{core==='school_print'?'HTML + JSON SET':'ORIGINAL + JSON SET'}</p><h2>{core==='school_print'?'학교 프린트':'학생 오답'} 추가</h2><p>{context.school} · {context.grade} · {context.year}년 {context.semester} {examLabel}에 자동 연결됩니다.</p></div><button type="button" onClick={()=>setCore(null)}>닫기</button></div>
    {core==='student_wrong_note'&&<label>학생<select required value={studentId} onChange={e=>setStudentId(e.target.value)}><option value="">학생 선택</option>{filteredStudents.map(s=><option value={s.id} key={s.id}>{s.name}</option>)}</select></label>}
    {core==='school_print'&&<label>자료 제목<input value={title} onChange={e=>setTitle(e.target.value)}/></label>}
    {core==='school_print'?<>
      <label>HTML 자료 파일 선택<input type="file" accept=".html,.htm,text/html" onChange={chooseHtmlFile}/></label>
      <label>HTML 붙여넣기<textarea value={htmlText} onChange={e=>setHtmlText(e.target.value)} placeholder="<p>학교 프린트 HTML을 붙여넣거나 파일을 선택해 불러오세요.</p>"/></label>
    </>:<>
      <label>오답 원본 파일<input type="file" multiple onChange={chooseOriginals}/></label>
      <small className="field-hint">원본 자료는 한 번에 최대 {MAX_ORIGINALS}개까지 선택할 수 있습니다.</small>
      {originals.length>0&&<div className="selected-file-list"><b>선택한 원본 파일 {originals.length}개</b>{originals.map((file,index)=><div key={`${file.name}-${index}`}><span>{file.name}</span><button type="button" onClick={()=>removeOriginal(index)}>삭제</button></div>)}</div>}
    </>}
    <label>JSON 파일 업로드<input type="file" accept="application/json,.json" onChange={(e:ChangeEvent<HTMLInputElement>)=>setJsonFile(e.target.files?.[0]||null)}/></label><label>또는 JSON 붙여넣기<textarea value={jsonText} onChange={e=>setJsonText(e.target.value)} placeholder='{"items":[]}'/></label>
    <button type="submit" className="primary-action" disabled={core==='school_print'?savingSchoolPrint||!htmlText.trim():(!originals.length||!jsonFile&&!jsonText.trim())}>{core==='school_print'&&savingSchoolPrint?'저장 중...':'저장'}</button></form>}
    <section id="insight-entry" className="work-panel"><div className="panel-heading"><div><p className="kicker">CLASS INSIGHT</p><h2>수업 인사이트</h2><p>박원장 인사이트와 학교 선생님 힌트를 한곳에 기록합니다.</p></div></div><div className="insight-entry"><select value={insightSource} onChange={e=>setInsightSource(e.target.value as typeof insightSource)}><option value="director">박원장 인사이트</option><option value="school_teacher">학교 선생님 힌트</option></select><textarea value={insightText} onChange={e=>setInsightText(e.target.value)} placeholder="이번 시험에 재사용할 관찰과 힌트"/><button className="primary-action" onClick={()=>void saveInsight()}>저장</button></div></section>

    {workspace==='final'&&<section className="work-panel admin-final-view"><div className="panel-heading"><div><p className="kicker">FINAL WORKSPACE</p><h2>현재 시험 자료</h2><p>필요한 항목만 선택하고 원본과 JSON을 각각 엽니다.</p></div><span className="context-chip">선택 {selected.size}</span><button type="button" className="secondary-button" disabled={loadingAiContext} onClick={()=>void showAiContext('final')}>{loadingAiContext?'불러오는 중...':'AI Context 보기'}</button><button type="button" className="secondary-button" onClick={()=>setWorkspace(null)}>닫기</button></div>
      {aiContext?.workspace==='final'&&<div className="admin-core"><h3>AI Context 미리보기<button type="button" onClick={()=>setAiContext(null)}>닫기</button></h3><p className="field-hint">GET /api/workspaces/final/context 응답 원문입니다. MCP/외부 LLM에 실제로 전달되는 것과 같습니다.</p><pre style={{whiteSpace:'pre-wrap',maxHeight:400,overflow:'auto'}}>{aiContext.json}</pre></div>}
      <Core title="출제자 Digital Twin" count={twinProfile?1:0}>{twinProfile&&<Row checked={false} onToggle={()=>undefined} label={`${twinProfile.description||twinProfile.dna||'Digital Twin'} · v${twinProfile.version}`} actions={<button onClick={()=>open(`digital-twins/${twinProfile.id}`)}>JSON 보기</button>}/>}</Core>
      <Core title="제작 문제" count={questions.length}>{questions.map(q=><Row key={q.id} checked={selected.has(`q-${q.id}`)} onToggle={()=>toggle(`q-${q.id}`)} label={`${q.question_type||'문항'} · ${q.stem.slice(0,70)}`} actions={<button onClick={()=>onNavigate('database')}>상세 열기</button>}/>)}</Core>
      <Core title="학교 프린트" count={prints.length}>{prints.map(m=><Row key={m.id} checked={selected.has(`m-${m.id}`)} onToggle={()=>toggle(`m-${m.id}`)} label={m.title||m.original_name||'학교 프린트'} actions={<>{m.has_html?<button onClick={()=>open(`exam-materials/${m.id}/html`)}>HTML 보기</button>:m.original_name&&<button onClick={()=>open(`exam-materials/${m.id}/original`)}>원본</button>}<button onClick={()=>open(`exam-materials/${m.id}/json`)}>JSON 보기</button></>}/>)}</Core>
      <Core title="학생 오답" count={wrongs.length}>{wrongs.map(m=><Row key={m.id} checked={selected.has(`m-${m.id}`)} onToggle={()=>toggle(`m-${m.id}`)} label={`${m.student_name||'학생'} · ${m.title||m.original_name}`} actions={<><button onClick={()=>open(`exam-materials/${m.id}/original`)}>원본</button><button onClick={()=>open(`exam-materials/${m.id}/json`)}>JSON</button></>}/>)}</Core>
      {wrongs.length>0&&<div className="twin-actions"><button className="primary-action" disabled={!selectedWrongMaterialIds.length||exportingWrong} onClick={()=>void exportSelectedWrong()}>{exportingWrong?'내보내는 중...':`선택 학생 오답 Word 내보내기 (${selectedWrongMaterialIds.length})`}</button></div>}
      <Core title="수업 인사이트" count={insights.length}>{insights.map(i=><Row key={i.id} checked={selected.has(`i-${i.id}`)} onToggle={()=>toggle(`i-${i.id}`)} label={`${i.source==='school_teacher'?'학교 선생님 힌트':'박원장 인사이트'} · ${i.content}`} actions={null}/>)}</Core>
      <div className="admin-core"><h3>FINAL 완성본 <span>{finals.length}</span></h3>
        {finals.length?finals.map(f=><Row key={f.id} checked={false} onToggle={()=>undefined} label={`${f.title||'FINAL 완성본'} · ${new Date(f.created_at).toLocaleString('ko-KR')}`} actions={<>{f.has_html?<button onClick={()=>open(`final-deliverables/${f.id}/html`)}>HTML 보기</button>:f.original_name&&<button onClick={()=>open(`final-deliverables/${f.id}/original`)}>원본</button>}<button onClick={()=>open(`final-deliverables/${f.id}/json`)}>JSON 보기</button></>}/>):<p className="field-hint">아직 저장된 FINAL 완성본이 없습니다. 외부에서 만든 결과물을 아래에서 저장할 수 있습니다.</p>}
        <form className="material-set-form" onSubmit={saveFinalDeliverable}>
          <label>FINAL 제목<input value={finalTitle} onChange={e=>setFinalTitle(e.target.value)}/></label>
          <label>결과 파일 업로드<input type="file" onChange={chooseFinalOriginal}/></label>
          <label>HTML 자료 파일 선택<input type="file" accept=".html,.htm,text/html" onChange={chooseFinalHtmlFile}/></label>
          <label>HTML 붙여넣기<textarea value={finalHtmlText} onChange={e=>setFinalHtmlText(e.target.value)} placeholder="완성된 FINAL HTML을 붙여넣거나 파일을 선택해 불러오세요."/></label>
          <label>JSON 파일 업로드<input type="file" accept="application/json,.json" onChange={(e:ChangeEvent<HTMLInputElement>)=>setFinalJsonFile(e.target.files?.[0]||null)}/></label>
          <label>또는 JSON 붙여넣기<textarea value={finalJsonText} onChange={e=>setFinalJsonText(e.target.value)} placeholder='{"items":[]}'/></label>
          <button type="submit" className="primary-action" disabled={savingFinal||(!finalOriginal&&!finalHtmlText.trim())}>{savingFinal?'저장 중...':'FINAL 완성본 저장'}</button>
        </form>
      </div>
    </section>}

    {workspace==='jikbo'&&<section className="work-panel admin-final-view"><div className="panel-heading"><div><p className="kicker">직보 WORKSPACE</p><h2>직보 준비</h2><p>FINAL 완성본에서 시험 직전 확인할 내용을 추출하고, FINAL 이후 최신 수업 인사이트와 학생 취약점을 반영합니다.</p></div><button type="button" className="secondary-button" disabled={loadingAiContext} onClick={()=>void showAiContext('jikbo')}>{loadingAiContext?'불러오는 중...':'AI Context 보기'}</button><button type="button" className="secondary-button" onClick={()=>setWorkspace(null)}>닫기</button></div>
      {aiContext?.workspace==='jikbo'&&<div className="admin-core"><h3>AI Context 미리보기<button type="button" onClick={()=>setAiContext(null)}>닫기</button></h3><p className="field-hint">GET /api/workspaces/jikbo/context 응답 원문입니다. MCP/외부 LLM에 실제로 전달되는 것과 같습니다.</p><pre style={{whiteSpace:'pre-wrap',maxHeight:400,overflow:'auto'}}>{aiContext.json}</pre></div>}
      {!finals.length?<p className="field-hint">아직 FINAL 자료가 없습니다. FINAL 제작 후 직보를 준비할 수 있습니다.</p>:<>
        <Core title="FINAL 완성본" count={finals.length}>{finals.map(f=><Row key={f.id} checked={false} onToggle={()=>undefined} label={`${f.title||'FINAL 완성본'} · ${new Date(f.created_at).toLocaleString('ko-KR')}`} actions={<>{f.has_html?<button onClick={()=>open(`final-deliverables/${f.id}/html`)}>HTML 보기</button>:f.original_name&&<button onClick={()=>open(`final-deliverables/${f.id}/original`)}>원본</button>}<button onClick={()=>open(`final-deliverables/${f.id}/json`)}>JSON 보기</button></>}/>)}</Core>
        <Core title="최신 수업 인사이트 (학교 힌트 우선)" count={sortedInsights.length}>{sortedInsights.map(i=><Row key={i.id} checked={false} onToggle={()=>undefined} label={`${i.source==='school_teacher'?'학교 선생님 힌트':'박원장 인사이트'} · ${i.content}`} actions={null}/>)}</Core>
        <Core title="학생 반복 오답" count={wrongs.length}>{wrongs.map(m=><Row key={m.id} checked={false} onToggle={()=>undefined} label={`${m.student_name||'학생'} · ${m.title||m.original_name}`} actions={<><button onClick={()=>open(`exam-materials/${m.id}/original`)}>원본</button><button onClick={()=>open(`exam-materials/${m.id}/json`)}>JSON</button></>}/>)}</Core>
      </>}
    </section>}
  </section>
}

function Core({title,count,children}:{title:string;count:number;children:ReactNode}){return <div className="admin-core"><h3>{title} <span>{count}</span></h3>{count?children:<p className="field-hint">현재 시험에 저장된 자료가 없습니다.</p>}</div>}
function Row({checked,onToggle,label,actions}:{checked:boolean;onToggle:()=>void;label:string;actions:ReactNode}){return <div className="admin-source-row"><input type="checkbox" checked={checked} onChange={onToggle}/><span>{label}</span><div>{actions}</div></div>}

