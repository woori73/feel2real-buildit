import { useState } from 'react'
import type { StoredQuestion } from '../lib/types'
import { updateQuestion } from '../lib/types'

export default function QuestionEditModal({question,onSaved,onClose}:{question:StoredQuestion;onSaved:(q:StoredQuestion)=>void;onClose:()=>void}){
  const [stem,setStem]=useState(question.stem||'')
  const [sourceText,setSourceText]=useState(question.source_text||'')
  const [options,setOptions]=useState((question.options||[]).join('\n'))
  const [answer,setAnswer]=useState((question.answer||[]).join(','))
  const [score,setScore]=useState(question.score||0)
  const [gradingRule,setGradingRule]=useState(question.grading_rule||'')
  const [sampleAnswer,setSampleAnswer]=useState(question.sample_answer||'')
  const [accepted,setAccepted]=useState((question.accepted_answers||[]).join('\n'))
  const [busy,setBusy]=useState(false),[error,setError]=useState('')
  const save=async()=>{setBusy(true);setError('');try{const saved=await updateQuestion(question.id,{stem,source_text:sourceText,options:options.split('\n').map(v=>v.trim()).filter(Boolean),answer:answer.split(/[,\s]+/).map(Number).filter(v=>Number.isInteger(v)&&v>0),score:Number(score)||0,grading_rule:gradingRule,sample_answer:sampleAnswer,accepted_answers:accepted.split('\n').map(v=>v.trim()).filter(Boolean)});onSaved(saved)}catch(e){setError(e instanceof Error?e.message:'문항을 수정하지 못했습니다.')}finally{setBusy(false)}}
  const fieldStyle={display:'grid',gap:5} as const,inputStyle={width:'100%',boxSizing:'border-box' as const}
  return <div role="dialog" aria-modal="true" style={{position:'fixed',inset:0,zIndex:1000,background:'rgba(0,0,0,.42)',display:'grid',placeItems:'center',padding:20}}><section style={{background:'#fff',width:'min(760px,96vw)',maxHeight:'90vh',overflow:'auto',padding:24,borderRadius:16,boxShadow:'0 20px 60px rgba(0,0,0,.25)'}}><div style={{display:'flex',justifyContent:'space-between',alignItems:'center',gap:12}}><div><p className="kicker">EDIT QUESTION · QID {question.id}</p><h2 style={{marginTop:4}}>저장 문항 수정</h2></div><button onClick={onClose}>닫기</button></div><div style={{display:'grid',gap:14}}><label style={fieldStyle}>발문<textarea rows={3} style={inputStyle} value={stem} onChange={e=>setStem(e.target.value)}/></label><label style={fieldStyle}>지문<textarea rows={7} style={inputStyle} value={sourceText} onChange={e=>setSourceText(e.target.value)}/></label>{question.q_kind==='mc'?<><label style={fieldStyle}>선택지 · 한 줄에 하나<textarea rows={6} style={inputStyle} value={options} onChange={e=>setOptions(e.target.value)}/></label><label style={fieldStyle}>정답 번호 · 복수면 쉼표로 구분<input style={inputStyle} value={answer} onChange={e=>setAnswer(e.target.value)}/></label></>:<><label style={fieldStyle}>모범답안<textarea rows={3} style={inputStyle} value={sampleAnswer} onChange={e=>setSampleAnswer(e.target.value)}/></label><label style={fieldStyle}>허용답안 · 한 줄에 하나<textarea rows={4} style={inputStyle} value={accepted} onChange={e=>setAccepted(e.target.value)}/></label></>}<label style={fieldStyle}>정답 해설<textarea rows={6} style={inputStyle} value={gradingRule} onChange={e=>setGradingRule(e.target.value)}/></label><label style={fieldStyle}>점수<input type="number" min="0" style={inputStyle} value={score} onChange={e=>setScore(Number(e.target.value))}/></label>{error&&<p style={{color:'#a22'}}>{error}</p>}<div style={{display:'flex',justifyContent:'flex-end',gap:8}}><button onClick={onClose}>취소</button><button className="primary-action" disabled={busy} onClick={()=>void save()}>{busy?'저장 중...':'수정 저장'}</button></div></div></section></div>
}
