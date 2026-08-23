import { useEffect, useMemo, useState } from 'react'
import type { Page } from '../App'
import { ApiSettings } from './ApiSettingsModal'
import { api } from '../lib/api'
import { DigitalTwinProfile, SchoolNode, StoredQuestion, TypeDef, fetchQuestions, fetchSchoolTree, fetchTypeDefs } from '../lib/types'
import QuestionCard from './QuestionCard'
import '../styles/generator.css'

type Props = { onNavigate: (page: Page) => void; apiSettings: ApiSettings | null }
type Mode = 'new' | 'transform'

const TYPE_LABELS: Record<string,string> = {
  blank:'빈칸 추론', order:'문장 순서', insert:'문장 삽입', vocab:'어휘', grammar:'어법',
  title:'제목', topic:'주제', summary:'요약', mismatch:'내용 불일치', match:'내용 일치',
  purpose:'목적', implication:'함축 의미', reference:'지칭 추론', short_answer:'서답형',
}

export default function MiddleSchoolExamGenerator({ onNavigate, apiSettings }: Props) {
  const [mode,setMode]=useState<Mode>('new')
  const [tree,setTree]=useState<SchoolNode[]>([])
  const [typeDefs,setTypeDefs]=useState<TypeDef[]>([])
  const [stored,setStored]=useState<StoredQuestion[]>([])
  const [school,setSchool]=useState('')
  const [grade,setGrade]=useState('')
  const [semester,setSemester]=useState('2')
  const [exam,setExam]=useState('midterm')
  const [passage,setPassage]=useState('')
  const [selectedTypes,setSelectedTypes]=useState<string[]>(['blank','grammar','vocab','order','insert'])
  const [count,setCount]=useState(10)
  const [loading,setLoading]=useState(false)
  const [result,setResult]=useState<any[]>([])
  const [profile,setProfile]=useState<DigitalTwinProfile|null>(null)
  const [error,setError]=useState('')

  useEffect(()=>{ Promise.all([fetchSchoolTree(),fetchTypeDefs(),fetchQuestions()]).then(([a,b,c])=>{setTree(a);setTypeDefs(b);setStored(c)}).catch(()=>{}) },[])
  const schoolNode=useMemo(()=>tree.find(x=>x.name===school),[tree,school])
  const grades=schoolNode?.grades||[]

  useEffect(()=>{
    if(!school||!grade) { setProfile(null); return }
    api(`/digital-twin/profile?school=${encodeURIComponent(school)}&grade=${encodeURIComponent(grade)}&semester=${semester}&exam=${exam}`)
      .then(r=>r.ok?r.json():null).then(setProfile).catch(()=>setProfile(null))
  },[school,grade,semester,exam])

  const toggleType=(id:string)=>setSelectedTypes(v=>v.includes(id)?v.filter(x=>x!==id):[...v,id])

  async function generate(){
    setError('')
    if(!school||!grade){setError('학교와 학년을 선택해 주세요.');return}
    if(!passage.trim()){setError('문제 제작에 사용할 지문을 입력해 주세요.');return}
    if(!selectedTypes.length){setError('문제 유형을 하나 이상 선택해 주세요.');return}
    setLoading(true)
    try{
      const body={school,grade,semester,exam,passage,count,types:selectedTypes,mode,digital_twin:profile,api_settings:apiSettings}
      const r=await api('/generate',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
      if(!r.ok) throw new Error(await r.text())
      const data=await r.json()
      setResult(data.questions||data.items||[])
    }catch(e:any){setError(e?.message||'문제 생성 중 오류가 발생했습니다.')}
    finally{setLoading(false)}
  }

  return <div className="f2r-gen">
    <header className="gen-topbar">
      <button className="gen-back" onClick={()=>onNavigate('state')}>← Wiki 홈</button>
      <div><b>내신 문제 제작</b><span> · Educator Brain 컨텍스트 기반</span></div>
      <button className="gen-db" onClick={()=>onNavigate('database')}>문제 DB</button>
    </header>
    <main className="gen-shell">
      <section className="gen-hero"><div><small>BUILD IT · CREATE</small><h1>학교별 내신 문제 만들기</h1><p>학교·학년·시험을 고르면 저장된 Digital Twin과 문제 유형을 제작 컨텍스트에 연결합니다.</p></div></section>
      <section className="gen-panel">
        <div className="gen-tabs"><button className={mode==='new'?'active':''} onClick={()=>setMode('new')}>신규 문제</button><button className={mode==='transform'?'active':''} onClick={()=>setMode('transform')}>기존 문제 변형</button></div>
        <div className="gen-grid four">
          <label>학교<select value={school} onChange={e=>{setSchool(e.target.value);setGrade('')}}><option value="">선택</option>{tree.map(s=><option key={s.name}>{s.name}</option>)}</select></label>
          <label>학년<select value={grade} onChange={e=>setGrade(e.target.value)}><option value="">선택</option>{grades.map((g:any)=><option key={String(g)} value={String(g)}>{g}학년</option>)}</select></label>
          <label>학기<select value={semester} onChange={e=>setSemester(e.target.value)}><option value="1">1학기</option><option value="2">2학기</option></select></label>
          <label>시험<select value={exam} onChange={e=>setExam(e.target.value)}><option value="midterm">중간고사</option><option value="final">기말고사</option></select></label>
        </div>
        {profile&&<div className="twin-strip"><b>Digital Twin 연결됨</b><span>{school} {grade}학년의 저장된 출제 패턴을 이번 제작에 반영합니다.</span></div>}
        <label className="passage-label">영어 지문<textarea value={passage} onChange={e=>setPassage(e.target.value)} placeholder="교과서, 학교 프린트 또는 외부 지문을 붙여 넣으세요." /></label>
        <div className="type-head"><b>문제 유형</b><span>{selectedTypes.length}개 선택</span></div>
        <div className="type-grid">{(typeDefs.length?typeDefs:Object.keys(TYPE_LABELS).map(id=>({id,name:TYPE_LABELS[id]} as any))).map((t:any)=>{const id=t.id||t.code;return <button key={id} className={selectedTypes.includes(id)?'selected':''} onClick={()=>toggleType(id)}>{t.name||t.label||TYPE_LABELS[id]||id}</button>})}</div>
        <div className="gen-actions"><label>문항 수<input type="number" min={1} max={30} value={count} onChange={e=>setCount(Number(e.target.value)||1)} /></label><button className="generate" disabled={loading} onClick={generate}>{loading?'제작 중…':'문제 만들기'}</button></div>
        {error&&<div className="gen-error">{error}</div>}
      </section>
      {!!result.length&&<section className="result-section"><div className="result-title"><h2>제작 결과</h2><span>{result.length}문항</span></div>{result.map((q:any,i)=><QuestionCard key={q.id||i} question={q} index={i} />)}</section>}
      {!result.length&&stored.length>0&&<section className="gen-footnote">현재 문제 DB에 <b>{stored.length}</b>개의 문제가 저장되어 있습니다.</section>}
    </main>
  </div>
}
