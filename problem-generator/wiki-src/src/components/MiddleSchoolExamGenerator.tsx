import { useEffect, useMemo, useState } from 'react'
import type { Page } from '../App'
import { ApiSettings } from './ApiSettingsModal'
import { api, normalizedApiError } from '../lib/api'
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

const initialContext = () => { try { return JSON.parse(sessionStorage.getItem('buildit_assistant_state') || '{}') as { school?:string; grade?:string; exam?:string } } catch { return {} } }
const normalizeTypeName = (value:string) => value.toLowerCase().replace(/[\s·_\-()/]/g,'').replace('문제','').replace('서술형','서답형').replace('문법','어법').replace('순서배열','문장순서')

export default function MiddleSchoolExamGenerator({ onNavigate, apiSettings }: Props) {
  const [mode,setMode]=useState<Mode>('new')
  const [tree,setTree]=useState<SchoolNode[]>([])
  const [typeDefs,setTypeDefs]=useState<TypeDef[]>([])
  const [stored,setStored]=useState<StoredQuestion[]>([])
  const context=useMemo(initialContext,[])
  const [school,setSchool]=useState(context.school||'')
  const [grade,setGrade]=useState(context.grade||'')
  const [semester,setSemester]=useState('2')
  const [exam,setExam]=useState(context.exam||'')
  const [passage,setPassage]=useState('')
  const [grammarFocus,setGrammarFocus]=useState('')
  const [selectedTypes,setSelectedTypes]=useState<string[]>([])
  const [count,setCount]=useState(10)
  const [loading,setLoading]=useState(false)
  const [result,setResult]=useState<any[]>([])
  const [resultProvider,setResultProvider]=useState<{provider:string;model:string}|null>(null)
  const [profile,setProfile]=useState<DigitalTwinProfile|null>(null)
  const [error,setError]=useState('')
  const provider='auto'
  const [abMode,setAbMode]=useState(false)
  const [comparison,setComparison]=useState<any>(null)

  useEffect(()=>{ Promise.all([fetchSchoolTree(),fetchTypeDefs(),fetchQuestions()]).then(([a,b,c])=>{setTree(a);setTypeDefs(b);setStored(c)}).catch(()=>{}) },[])
  const schoolNode=useMemo(()=>tree.find(x=>x.school===school),[tree,school])
  const grades=schoolNode?.grades||[]
  const gradeNode=useMemo(()=>grades.find(x=>x.grade===grade),[grades,grade])
  const exams=gradeNode?.exams||[]

  useEffect(()=>{ if(grade&&exams.length&&!exam) setExam(exams[0].exam) },[grade,exam,exams])

  useEffect(()=>{
    if (!school || !tree.some(item => item.school === school)) { setGrade(''); setExam(''); return }
    if (!grade || !grades.some(item => item.grade === grade)) { setGrade(grades[0]?.grade || ''); setExam('') }
  },[school,tree,grade,grades])

  useEffect(()=>{
    if (!grade) { setExam(''); return }
    if (!exam || !exams.some(item => item.exam === exam)) setExam(exams[0]?.exam || '')
  },[grade,exam,exams])

  useEffect(()=>{
    if(!school||!grade) { setProfile(null); return }
    const selected=exams.find(x=>x.exam===exam)
    if(!selected?.twinId){setProfile(null);return}
    api(`digital-twins/${selected.twinId}`)
      .then(r=>r.ok?r.json():null).then(setProfile).catch(()=>setProfile(null))
  },[school,grade,exam,exams])

  useEffect(()=>{
    try { sessionStorage.setItem('buildit_page_context', JSON.stringify({ school, grade, exam, selected_twin_id: profile?.id ?? null })) } catch { /* selection still works without storage */ }
  },[school,grade,exam,profile])

  useEffect(()=>{
    if(!profile||!typeDefs.length)return
    const twinNames=profile.types.map(item=>normalizeTypeName(item.name))
    const matched=typeDefs.filter(item=>{
      const candidates=[item.id,item.label].map(normalizeTypeName)
      return twinNames.some(twin=>candidates.some(candidate=>twin===candidate||twin.includes(candidate)||candidate.includes(twin)))
    }).map(item=>item.id)
    if(matched.length)setSelectedTypes(matched)
  },[profile,typeDefs])

  const toggleType=(id:string)=>setSelectedTypes(v=>v.includes(id)?v.filter(x=>x!==id):[...v,id])

  async function generate(){
    setError('')
    if(!school||!grade){setError('학교와 학년을 선택해 주세요.');return}
    if(!profile){setError('선택한 학교·학년·시험에 연결된 Digital Twin이 없습니다. Digital Twin 관리에서 먼저 연결해 주세요.');return}
    if(!passage.trim()){setError('문제 제작에 사용할 지문을 입력해 주세요.');return}
    if(mode==='new'&&!grammarFocus.trim()){setError('시험 범위 주요 문법 사항을 입력해 주세요.');return}
    if(!selectedTypes.length){setError('문제 유형을 하나 이상 선택해 주세요.');return}
    setLoading(true)
    try{
      const endpoint=abMode&&mode==='new'?'questions/ab-compare':mode==='new'?'questions/generate':'questions/transform'
      const body=mode==='new'
        ? {provider,twin_id:profile?.id,school,grade,exam,passage,grammar:grammarFocus.trim(),count,types:selectedTypes}
        : {provider,twin_id:profile?.id,school,grade,exam,original:passage,count}
      const r=await api(endpoint,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
      if(!r.ok) throw new Error(await normalizedApiError(r, provider))
      const data=await r.json()
      if(abMode&&mode==='new'){setComparison(data);setResult([]);setResultProvider(null)}else{setComparison(null);setResult(data.questions||data.items||[]);setResultProvider({provider:data.provider,model:data.model})}
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
      <section className="gen-hero"><div><small>BUILD IT · CREATE</small><h1>내신·수능 문제 제작</h1><p>기존 내신 Digital Twin 흐름을 유지합니다. 학교·학년·시험을 고르면 저장된 Twin과 문제 유형을 제작 컨텍스트에 연결합니다.</p></div></section>
      <section className="gen-panel">
        <div className="gen-tabs"><button className={mode==='new'?'active':''} onClick={()=>setMode('new')}>신규 문제</button><button className={mode==='transform'?'active':''} onClick={()=>setMode('transform')}>기존 문제 변형</button></div>
        <div className="gen-grid four">
          <label>학교<select value={school} onChange={e=>{setSchool(e.target.value);setGrade('');setExam('')}}><option value="">선택</option>{tree.map(s=><option key={s.school} value={s.school}>{s.school}</option>)}</select></label>
          <label>학년<select value={grade} onChange={e=>{setGrade(e.target.value);setExam('')}}><option value="">선택</option>{grades.map(g=><option key={g.grade} value={g.grade}>{g.grade}</option>)}</select></label>
          <label>학기<select value={semester} onChange={e=>setSemester(e.target.value)}><option value="1">1학기</option><option value="2">2학기</option></select></label>
          <label>시험<select value={exam} onChange={e=>setExam(e.target.value)}><option value="">선택</option>{exams.map((x,index)=><option key={`${x.exam}-${x.twinId ?? index}`} value={x.exam}>{x.exam}{x.hasDigitalTwin?' · Twin 연결':' · 기록 있음'}</option>)}</select></label>
        </div>
        {profile?<div className="twin-strip"><b>Digital Twin 연결됨 · v{profile.version}</b><span>{profile.types.slice(0,6).map(item=>item.name).join(' · ') || profile.dna}</span></div>:school&&grade&&<div className="twin-strip"><b>기존 기록 연결됨</b><span>이 학교·학년의 기록은 있으나 Digital Twin은 아직 없습니다.</span></div>}
        <label className="passage-label">영어 지문<textarea value={passage} onChange={e=>setPassage(e.target.value)} placeholder="교과서, 학교 프린트 또는 외부 지문을 붙여 넣으세요." /></label>
        {mode==='new'&&<label className="passage-label grammar-focus-label">시험 범위 주요 문법 사항<textarea value={grammarFocus} onChange={e=>setGrammarFocus(e.target.value)} placeholder="예: 관계대명사 what, 가정법, 수동태" /><small>문법·서술형 문항에 우선 반영하고 생성 기록에도 함께 보관합니다.</small></label>}
        <div className="type-head"><b>문제 유형 {profile?'· Digital Twin 자동 적용':''}</b><span>{selectedTypes.length}개 선택</span></div>
        <div className="type-grid">{(typeDefs.length?typeDefs:Object.keys(TYPE_LABELS).map(id=>({id,label:TYPE_LABELS[id]} as TypeDef))).map(t=>{const id=t.id;return <button type="button" key={id} className={selectedTypes.includes(id)?'selected':''} onClick={()=>toggleType(id)}>{t.label||TYPE_LABELS[id]||id}</button>})}</div>
        <div className="gen-actions"><label>문항 수<input type="number" min={1} max={30} value={count} onChange={e=>setCount(Number(e.target.value)||1)} /></label><span className="context-chip">기본 AI · Qwen3.7 Flash</span>{mode==='new'&&<label><input type="checkbox" checked={abMode} onChange={e=>setAbMode(e.target.checked)} /> A/B 비교</label>}<button className="generate" disabled={loading} onClick={generate}>{loading?'제작 중…':abMode?'A/B 비교 생성':'문제 만들기'}</button></div>
        {error&&<div className="gen-error">{error}</div>}
      </section>
      {comparison&&<AbResults data={comparison} context={{school,grade,exam,twinId:profile?.id,questionType:selectedTypes.join(', ')}} />}
      {!!result.length&&<section className="result-section"><div className="result-title"><h2>제작 결과</h2><span>{result.length}문항</span></div><p className="storage-status" role="status">저장됨 · 문제 DB{resultProvider ? ` · ${resultProvider.provider} · ${resultProvider.model}` : ''}</p><div className="question-list">{result.map((q:any,i)=><QuestionCard key={q.id||i} q={q} index={i+1} />)}</div></section>}
      {!result.length&&stored.length>0&&<section className="gen-footnote">현재 문제 DB에 <b>{stored.length}</b>개의 문제가 저장되어 있습니다.</section>}
    </main>
  </div>
}

function AbResults({data,context}:{data:any;context:any}){
  const [saved,setSaved]=useState('')
  const gem=data.results?.gemini, ox=data.results?.['ox-alpha']
  const evaluate=async(choice:'gemini'|'ox-alpha'|'both-revise')=>{if(!gem||!ox){setSaved('두 결과가 모두 있어야 평가를 저장할 수 있습니다.');return}const r=await api('questions/ab-evaluations',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({school:context.school,grade:context.grade,exam:context.exam,twin_id:context.twinId,question_type:context.questionType,gemini_model:gem.model,gemini_result_id:gem.resultId,ox_model:ox.model,ox_result_id:ox.resultId,choice})});setSaved(r.ok?'평가를 저장했습니다.':'평가 저장에 실패했습니다.')}
  return <section className="result-section"><div className="result-title"><h2>Gemini ↔ Ox Alpha Experimental A/B</h2></div><p className="field-hint">비교 기준: 영어 내신 문제 생성 품질 · Digital Twin 반영률 · 한국어 지시 이해 · 긴 학교 자료 처리 · JSON 문제 구조 준수</p><div className="gen-grid" style={{gridTemplateColumns:'1fr 1fr'}}>{[['Gemini',gem],['Ox Alpha · Experimental',ox]].map(([label,r]:any)=><article className="gen-panel" key={label}><h3>{label}</h3>{r?r.questions.map((q:any,i:number)=><QuestionCard key={i} q={q} index={i+1}/>):<p>{data.errors?.[label==='Gemini'?'gemini':'ox-alpha']||'사용 불가'}</p>}</article>)}</div><div className="gen-actions"><button onClick={()=>evaluate('gemini')}>Gemini 우수</button><button onClick={()=>evaluate('ox-alpha')}>Ox Alpha 우수</button><button onClick={()=>evaluate('both-revise')}>둘 다 수정 필요</button><span>{saved}</span></div></section>
}


