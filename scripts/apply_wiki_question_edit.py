from pathlib import Path

root=Path(__file__).resolve().parents[1]

def replace(path, old, new):
    p=root/path
    text=p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'anchor not found: {path}: {old[:80]!r}')
    p.write_text(text.replace(old,new,1),encoding='utf-8')

# Backend: preserve QID and update legacy + canonical fields together.
main=Path('problem-generator/wiki-backend-src/backend/main.py')
anchor="""@app.delete('/api/questions/{question_id}')\ndef delete_question(question_id: int):\n"""
route="""class QuestionUpdate(BaseModel):
    stem: str
    source_text: str = ''
    options: list[str] = []
    answer: list[int] = []
    score: int = 0
    grading_rule: str = ''
    sample_answer: str = ''
    accepted_answers: list[str] = []


@app.patch('/api/questions/{question_id}')
def update_question(question_id: int, payload: QuestionUpdate):
    with get_db() as conn:
        row=conn.execute('SELECT * FROM generated_questions WHERE id = ?', (question_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail='문항을 찾지 못했습니다.')
        kind=row['q_kind']
        if not payload.stem.strip():
            raise HTTPException(status_code=422, detail='발문을 입력해 주세요.')
        if kind=='mc' and (len(payload.options)<2 or not payload.answer):
            raise HTTPException(status_code=422, detail='객관식은 선택지 2개 이상과 정답 번호가 필요합니다.')
        if kind=='sa' and not (payload.sample_answer.strip() or payload.accepted_answers):
            raise HTTPException(status_code=422, detail='서답형은 모범답안 또는 허용답안이 필요합니다.')
        canonical={}
        try:
            canonical=json.loads(row['canonical_json'] or '{}')
        except (TypeError,json.JSONDecodeError):
            canonical={}
        if canonical.get('schema_version')=='wiki-question-v1':
            canonical['instruction']={'text':payload.stem.strip(),'html':_legacy_markup_html(payload.stem.strip())}
            passage=canonical.get('passage') or {}
            passage.update({'text':payload.source_text.strip(),'html':_legacy_markup_html(payload.source_text.strip())})
            if payload.source_text.strip() and passage.get('status') in ('missing','unknown','not_applicable',None): passage['status']='complete'
            canonical['passage']=passage
            canonical['choices']=[{'number':i+1,'text':v.strip(),'html':_legacy_markup_html(v.strip())} for i,v in enumerate(payload.options)]
            answer=canonical.get('answer') or {}
            answer.update({'choice_numbers':payload.answer if kind=='mc' else [],'accepted_answers':[v.strip() for v in payload.accepted_answers if v.strip()],'sample_answer':payload.sample_answer.strip()})
            canonical['answer']=answer
            canonical['explanation']={'text':payload.grading_rule.strip(),'html':_legacy_markup_html(payload.grading_rule.strip())}
            scoring=canonical.get('scoring') or {};scoring['score']=payload.score;canonical['scoring']=scoring
        conn.execute('''UPDATE generated_questions SET stem=?, source_text=?, options_json=?, answer_json=?, score=?, accepted_answers_json=?, sample_answer=?, grading_rule=?, canonical_json=?, passage_status=? WHERE id=?''',(
            payload.stem.strip(),payload.source_text.strip(),json.dumps(payload.options,ensure_ascii=False),json.dumps(payload.answer if kind=='mc' else [],ensure_ascii=False),payload.score,
            json.dumps([v.strip() for v in payload.accepted_answers if v.strip()],ensure_ascii=False),payload.sample_answer.strip(),payload.grading_rule.strip(),
            json.dumps(canonical,ensure_ascii=False) if canonical else row['canonical_json'],(canonical.get('passage') or {}).get('status',row['passage_status']) if canonical else row['passage_status'],question_id))
    return get_question(question_id)


@app.delete('/api/questions/{question_id}')
def delete_question(question_id: int):
"""
replace(main,anchor,route)

# Frontend API helper.
types=Path('problem-generator/wiki-src/src/lib/types.ts')
anchor="export const fetchQuestion=async(id:number):Promise<StoredQuestion>=>{const r=await api(`questions/${id}`);if(!r.ok)throw new Error('문항을 불러오지 못했습니다.');return r.json()}\n"
new=anchor+"export const updateQuestion=async(id:number,payload:{stem:string;source_text:string;options:string[];answer:number[];score:number;grading_rule:string;sample_answer:string;accepted_answers:string[]}):Promise<StoredQuestion>=>{const r=await api(`questions/${id}`,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});const b=await r.json() as StoredQuestion&{detail?:string};if(!r.ok)throw new Error(b.detail||'문항을 수정하지 못했습니다.');return b}\n"
replace(types,anchor,new)

# New edit modal.
component=root/'problem-generator/wiki-src/src/components/QuestionEditModal.tsx'
component.write_text("""import { useState } from 'react'\nimport type { StoredQuestion } from '../lib/types'\nimport { updateQuestion } from '../lib/types'\n\nexport default function QuestionEditModal({question,onSaved,onClose}:{question:StoredQuestion;onSaved:(q:StoredQuestion)=>void;onClose:()=>void}){\n  const [stem,setStem]=useState(question.stem||'')\n  const [sourceText,setSourceText]=useState(question.source_text||'')\n  const [options,setOptions]=useState((question.options||[]).join('\\n'))\n  const [answer,setAnswer]=useState((question.answer||[]).join(','))\n  const [score,setScore]=useState(question.score||0)\n  const [gradingRule,setGradingRule]=useState(question.grading_rule||'')\n  const [sampleAnswer,setSampleAnswer]=useState(question.sample_answer||'')\n  const [accepted,setAccepted]=useState((question.accepted_answers||[]).join('\\n'))\n  const [busy,setBusy]=useState(false),[error,setError]=useState('')\n  const save=async()=>{setBusy(true);setError('');try{const saved=await updateQuestion(question.id,{stem,source_text:sourceText,options:options.split('\\n').map(v=>v.trim()).filter(Boolean),answer:answer.split(/[,\\s]+/).map(Number).filter(v=>Number.isInteger(v)&&v>0),score:Number(score)||0,grading_rule:gradingRule,sample_answer:sampleAnswer,accepted_answers:accepted.split('\\n').map(v=>v.trim()).filter(Boolean)});onSaved(saved)}catch(e){setError(e instanceof Error?e.message:'문항을 수정하지 못했습니다.')}finally{setBusy(false)}}\n  const fieldStyle={display:'grid',gap:5} as const,inputStyle={width:'100%',boxSizing:'border-box' as const}\n  return <div role=\"dialog\" aria-modal=\"true\" style={{position:'fixed',inset:0,zIndex:1000,background:'rgba(0,0,0,.42)',display:'grid',placeItems:'center',padding:20}}><section style={{background:'#fff',width:'min(760px,96vw)',maxHeight:'90vh',overflow:'auto',padding:24,borderRadius:16,boxShadow:'0 20px 60px rgba(0,0,0,.25)'}}><div style={{display:'flex',justifyContent:'space-between',alignItems:'center',gap:12}}><div><p className=\"kicker\">EDIT QUESTION · QID {question.id}</p><h2 style={{marginTop:4}}>저장 문항 수정</h2></div><button onClick={onClose}>닫기</button></div><div style={{display:'grid',gap:14}}><label style={fieldStyle}>발문<textarea rows={3} style={inputStyle} value={stem} onChange={e=>setStem(e.target.value)}/></label><label style={fieldStyle}>지문<textarea rows={7} style={inputStyle} value={sourceText} onChange={e=>setSourceText(e.target.value)}/></label>{question.q_kind==='mc'?<><label style={fieldStyle}>선택지 · 한 줄에 하나<textarea rows={6} style={inputStyle} value={options} onChange={e=>setOptions(e.target.value)}/></label><label style={fieldStyle}>정답 번호 · 복수면 쉼표로 구분<input style={inputStyle} value={answer} onChange={e=>setAnswer(e.target.value)}/></label></>:<><label style={fieldStyle}>모범답안<textarea rows={3} style={inputStyle} value={sampleAnswer} onChange={e=>setSampleAnswer(e.target.value)}/></label><label style={fieldStyle}>허용답안 · 한 줄에 하나<textarea rows={4} style={inputStyle} value={accepted} onChange={e=>setAccepted(e.target.value)}/></label></>}<label style={fieldStyle}>정답 해설<textarea rows={6} style={inputStyle} value={gradingRule} onChange={e=>setGradingRule(e.target.value)}/></label><label style={fieldStyle}>점수<input type=\"number\" min=\"0\" style={inputStyle} value={score} onChange={e=>setScore(Number(e.target.value))}/></label>{error&&<p style={{color:'#a22'}}>{error}</p>}<div style={{display:'flex',justifyContent:'flex-end',gap:8}}><button onClick={onClose}>취소</button><button className=\"primary-action\" disabled={busy} onClick={()=>void save()}>{busy?'저장 중...':'수정 저장'}</button></div></div></section></div>\n}\n""",encoding='utf-8')

# App wiring.
app=Path('problem-generator/wiki-src/src/App.tsx')
replace(app,"import ChromeQuestionWorkbench from './components/ChromeQuestionWorkbench'\n", "import ChromeQuestionWorkbench from './components/ChromeQuestionWorkbench'\nimport QuestionEditModal from './components/QuestionEditModal'\n")
replace(app,"  const [manualSaveOpen, setManualSaveOpen] = useState(false)\n", "  const [manualSaveOpen, setManualSaveOpen] = useState(false)\n  const [editingQuestion, setEditingQuestion] = useState<StoredQuestion|null>(null)\n")
replace(app,"<button onClick={() => sendToExam(item.id)}>실전 제작</button><button onClick={() => deleteQuestion(item.id)}>삭제</button>","<button onClick={() => setEditingQuestion(item)}>수정</button><button onClick={() => sendToExam(item.id)}>실전 제작</button><button onClick={() => deleteQuestion(item.id)}>삭제</button>")
replace(app,"    <AssistantComposer messages={assistantOpen ? assistantMessages : []}","    {editingQuestion&&<QuestionEditModal question={editingQuestion} onClose={()=>setEditingQuestion(null)} onSaved={saved=>{setQuestions(items=>items.map(item=>item.id===saved.id?saved:item));setEditingQuestion(null);setNotice(`QID ${saved.id} 문항을 수정했습니다.`)}}/>}\n    <AssistantComposer messages={assistantOpen ? assistantMessages : []}")

print('wiki question edit patch applied')
