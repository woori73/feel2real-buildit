import { ChangeEvent, FormEvent, ReactNode, useEffect, useState } from 'react'
import { api } from '../lib/api'
import { fetchQuestions, fetchSchoolTree, fetchStudents, SchoolNode, StoredQuestion, Student } from '../lib/types'
import type { Page } from '../App'

type Context = { school:string; grade:string; year:number; semester:string; exam:string }
type Material = { id:number; core_type:'school_print'|'student_wrong_note'; student_id:number|null; student_name:string|null; title:string; original_name:string; created_at:string }
type Insight = { id:number; content:string; source:'director'|'school_teacher'; created_at:string }
const years = [new Date().getFullYear(), new Date().getFullYear()-1, new Date().getFullYear()-2]

export default function NaesinWikiV1({onNavigate}:{onNavigate:(page:Page)=>void}) {
  const [tree,setTree]=useState<SchoolNode[]>([]), [students,setStudents]=useState<Student[]>([])
  const [context,setContext]=useState<Context>({school:'',grade:'',year:years[0],semester:'1학기',exam:''})
  const [questions,setQuestions]=useState<StoredQuestion[]>([]), [materials,setMaterials]=useState<Material[]>([]), [insights,setInsights]=useState<Insight[]>([])
  const [core,setCore]=useState<'school_print'|'student_wrong_note'|null>(null), [admin,setAdmin]=useState(false), [notice,setNotice]=useState('')
  const [studentId,setStudentId]=useState(''), [title,setTitle]=useState(''), [original,setOriginal]=useState<File|null>(null), [jsonFile,setJsonFile]=useState<File|null>(null), [jsonText,setJsonText]=useState('')
  const [insightText,setInsightText]=useState(''), [insightSource,setInsightSource]=useState<'director'|'school_teacher'>('director')
  const [selected,setSelected]=useState<Set<string>>(new Set())
  const schoolNode=tree.find(item=>item.school===context.school), grades=schoolNode?.grades||[]
  const gradeNode=grades.find(item=>item.grade===context.grade), exams=gradeNode?.exams||[]
  const exactExam=context.exam
  const ready=Boolean(context.school&&context.grade&&context.exam)
  const contextQuery=new URLSearchParams({school:context.school,grade:context.grade,year:String(context.year),semester:context.semester,exam:context.exam})
  const load=async()=>{if(!ready)return;const [q,m,i]=await Promise.all([
    fetchQuestions({school:context.school,grade:context.grade,exam:exactExam}),
    api(`exam-materials?${contextQuery}`).then(r=>r.ok?r.json():[]),
    api(`educator-insights?limit=100&${contextQuery}`).then(r=>r.ok?r.json():[]),
  ]);setQuestions(q);setMaterials(m);setInsights(i)}
  useEffect(()=>{fetchSchoolTree().then(nodes=>{setTree(nodes);const first=nodes[0], grade=first?.grades[0];if(first)setContext(c=>({...c,school:first.school,grade:grade?.grade||'',exam:grade?.exams[0]?.exam||''}))});fetchStudents().then(setStudents)},[])
  useEffect(()=>{void load()},[context.school,context.grade,context.year,context.semester,context.exam])
  const filteredStudents=students.filter(s=>(!s.school||s.school===context.school)&&(!s.grade||s.grade===context.grade))
  const prints=materials.filter(m=>m.core_type==='school_print'), wrongs=materials.filter(m=>m.core_type==='student_wrong_note')
  const resetUpload=()=>{setOriginal(null);setJsonFile(null);setJsonText('');setTitle('')}
  const saveMaterial=async(e:FormEvent)=>{e.preventDefault();if(!core||!original)return;const form=new FormData();Object.entries(context).forEach(([k,v])=>form.append(k,String(v)));form.append('core_type',core);form.append('title',title);form.append('original',original);if(jsonFile)form.append('json_file',jsonFile);form.append('json_text',jsonText);if(core==='student_wrong_note')form.append('student_id',studentId);const r=await api('exam-materials',{method:'POST',body:form});const b=await r.json();if(!r.ok){setNotice(b.detail||'저장하지 못했습니다.');return}resetUpload();setCore(null);setNotice('원본과 JSON을 한 세트로 저장했습니다.');void load()}
  const saveInsight=async()=>{if(!insightText.trim())return;const r=await api('educator-insights',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...context,content:insightText,source:insightSource,student_context:'',lesson_context:''})});if(r.ok){setInsightText('');setNotice('수업 인사이트를 저장했습니다.');void load()}}
  const toggle=(key:string)=>setSelected(current=>{const next=new Set(current);next.has(key)?next.delete(key):next.add(key);return next})
  const open=(path:string)=>window.open(`/problem-generator/wiki/api/${path}`,'_blank','noopener')
  const openQuestionMaker=()=>{sessionStorage.setItem('buildit_assistant_state',JSON.stringify({school:context.school,grade:context.grade,exam:context.exam}));onNavigate('home')}
  const cards=[
    ['제작 문제',questions.length,openQuestionMaker],['학교 프린트',prints.length,()=>setCore('school_print')],['학생 오답',wrongs.length,()=>setCore('student_wrong_note')],['수업 인사이트',insights.length,()=>document.getElementById('insight-entry')?.scrollIntoView({behavior:'smooth'})]
  ] as const
  return <section className="naesin-v1">
    <header><p className="kicker">BUILD IT · NAESIN WIKI V1</p><h1>시험 자료 한눈에 모으기</h1><p>학교와 시험을 먼저 고르면 제작 문제, 학교 프린트, 학생 오답, 수업 인사이트만 모아 보여줍니다.</p></header>
    {notice&&<div className="tool-notice" role="status">{notice}<button onClick={()=>setNotice('')}>×</button></div>}
    <div className="exam-context" aria-label="시험 Context">
      <label>학교<select value={context.school} onChange={e=>{const school=e.target.value,node=tree.find(n=>n.school===school),grade=node?.grades[0];setContext(c=>({...c,school,grade:grade?.grade||'',exam:grade?.exams[0]?.exam||''}))}}>{tree.map(n=><option key={n.school}>{n.school}</option>)}</select></label>
      <label>학년<select value={context.grade} onChange={e=>{const grade=e.target.value,node=grades.find(g=>g.grade===grade);setContext(c=>({...c,grade,exam:node?.exams[0]?.exam||''}))}}>{grades.map(g=><option key={g.grade}>{g.grade}</option>)}</select></label>
      <label>연도<select value={context.year} onChange={e=>setContext(c=>({...c,year:Number(e.target.value)}))}>{years.map(y=><option key={y}>{y}</option>)}</select></label>
      <label>학기<select value={context.semester} onChange={e=>setContext(c=>({...c,semester:e.target.value}))}><option>1학기</option><option>2학기</option></select></label>
      <label>시험<select value={context.exam} onChange={e=>setContext(c=>({...c,exam:e.target.value}))}>{exams.map((item,index)=><option key={`${item.exam}-${index}`}>{item.exam}</option>)}</select></label>
    </div>
    <div className="context-summary"><b>{context.school} · {context.grade}</b><span>{context.year}년 · {context.semester} · {context.exam}</span></div>
    <div className="core-grid">{cards.map(([label,count,action],i)=><button key={label} onClick={action}><span>0{i+1}</span><b>{label}</b><strong>{count}</strong></button>)}</div>
    <div className="final-actions"><div><b>FINAL / 직보</b><p>파일을 만들지 않고, 현재 시험에 필요한 자료만 골라 확인합니다.</p></div><button className="primary-action" onClick={()=>setAdmin(v=>!v)}>Admin / Final 자료 보기</button></div>
    {core&&<form className="work-panel material-set-form" onSubmit={saveMaterial}><div className="panel-heading"><div><p className="kicker">ORIGINAL + JSON SET</p><h2>{core==='school_print'?'학교 프린트':'학생 오답'} 추가</h2><p>{context.school} · {context.grade} · {context.year}년 {context.semester} {context.exam}에 자동 연결됩니다.</p></div><button type="button" onClick={()=>setCore(null)}>닫기</button></div>{core==='student_wrong_note'&&<label>학생<select required value={studentId} onChange={e=>setStudentId(e.target.value)}><option value="">학생 선택</option>{filteredStudents.map(s=><option value={s.id} key={s.id}>{s.name}</option>)}</select></label>}<label>자료 이름 (선택)<input value={title} onChange={e=>setTitle(e.target.value)}/></label><label>원본 업로드<input required type="file" onChange={(e:ChangeEvent<HTMLInputElement>)=>setOriginal(e.target.files?.[0]||null)}/></label><label>JSON 파일 업로드<input type="file" accept="application/json,.json" onChange={(e:ChangeEvent<HTMLInputElement>)=>setJsonFile(e.target.files?.[0]||null)}/></label><label>또는 JSON 붙여넣기<textarea value={jsonText} onChange={e=>setJsonText(e.target.value)} placeholder='{"items":[]}'/></label><button className="primary-action" disabled={!original||(!jsonFile&&!jsonText.trim())}>원본 + JSON 저장</button></form>}
    <section id="insight-entry" className="work-panel"><div className="panel-heading"><div><p className="kicker">CLASS INSIGHT</p><h2>수업 인사이트</h2><p>박원장 인사이트와 학교 선생님 힌트를 한곳에 기록합니다.</p></div></div><div className="insight-entry"><select value={insightSource} onChange={e=>setInsightSource(e.target.value as typeof insightSource)}><option value="director">박원장 인사이트</option><option value="school_teacher">학교 선생님 힌트</option></select><textarea value={insightText} onChange={e=>setInsightText(e.target.value)} placeholder="이번 시험에 재사용할 관찰과 힌트"/><button className="primary-action" onClick={()=>void saveInsight()}>저장</button></div></section>
    {admin&&<section className="work-panel admin-final-view"><div className="panel-heading"><div><p className="kicker">ADMIN / FINAL SOURCES</p><h2>현재 시험 자료</h2><p>필요한 항목만 선택하고 원본과 JSON을 각각 엽니다.</p></div><span className="context-chip">선택 {selected.size}</span></div>
      <Core title="제작 문제" count={questions.length}>{questions.map(q=><Row key={q.id} checked={selected.has(`q-${q.id}`)} onToggle={()=>toggle(`q-${q.id}`)} label={`${q.question_type||'문항'} · ${q.stem.slice(0,70)}`} actions={<button onClick={()=>onNavigate('database')}>상세 열기</button>}/>)}</Core>
      <Core title="학교 프린트" count={prints.length}>{prints.map(m=><Row key={m.id} checked={selected.has(`m-${m.id}`)} onToggle={()=>toggle(`m-${m.id}`)} label={m.title||m.original_name} actions={<><button onClick={()=>open(`exam-materials/${m.id}/original`)}>원본</button><button onClick={()=>open(`exam-materials/${m.id}/json`)}>JSON</button></>}/>)}</Core>
      <Core title="학생 오답" count={wrongs.length}>{wrongs.map(m=><Row key={m.id} checked={selected.has(`m-${m.id}`)} onToggle={()=>toggle(`m-${m.id}`)} label={`${m.student_name||'학생'} · ${m.title||m.original_name}`} actions={<><button onClick={()=>open(`exam-materials/${m.id}/original`)}>원본</button><button onClick={()=>open(`exam-materials/${m.id}/json`)}>JSON</button></>}/>)}</Core>
      <Core title="수업 인사이트" count={insights.length}>{insights.map(i=><Row key={i.id} checked={selected.has(`i-${i.id}`)} onToggle={()=>toggle(`i-${i.id}`)} label={`${i.source==='school_teacher'?'학교 선생님 힌트':'박원장 인사이트'} · ${i.content}`} actions={null}/>)}</Core>
    </section>}
  </section>
}

function Core({title,count,children}:{title:string;count:number;children:ReactNode}){return <div className="admin-core"><h3>{title} <span>{count}</span></h3>{count?children:<p className="field-hint">현재 시험에 저장된 자료가 없습니다.</p>}</div>}
function Row({checked,onToggle,label,actions}:{checked:boolean;onToggle:()=>void;label:string;actions:ReactNode}){return <div className="admin-source-row"><input type="checkbox" checked={checked} onChange={onToggle}/><span>{label}</span><div>{actions}</div></div>}
