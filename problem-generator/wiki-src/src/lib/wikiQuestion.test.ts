import { describe,expect,it } from 'vitest'
import { importQuestionFile } from './importQuestions'
import { isSelfContained,normalizeChromeQuestion,normalizeChromeQuestionResult,resolveReferencedPassage,wikiQuestionToLegacy } from './wikiQuestion'

const dataQuestion=(id:string,stem:string,passage:string,extra='')=>`<article data-question-id="${id}" data-question-type="mc" data-answer="1"><div class="stem">${stem}</div>${passage}<ol class="choices"><li>정답</li><li>오답</li><li>오답</li><li>오답</li><li>오답</li></ol>${extra}</article>`

describe('wiki-question-v1 importer',()=>{
  it('stores a complete long passage and five choices in one question',()=>{
    const passage='A complete English passage '.repeat(12)
    const q=importQuestionFile('exam.html',dataQuestion('18','다음 글을 읽고 답하시오.',`<div class="passage">${passage}</div>`)).questions[0].canonical!
    expect(q.passage.text).toContain('complete English passage');expect(q.choices).toHaveLength(5);expect(isSelfContained(q)).toBe(true)
  })
  it('duplicates one common passage into both QIDs',()=>{
    const html=`<section data-common-passage-id="41-42"><p>${'Shared passage '.repeat(10)}</p></section>${dataQuestion('41','글을 읽고 답하시오.','').replace('data-answer','data-passage-ref="41-42" data-answer')}${dataQuestion('42','글을 읽고 답하시오.','').replace('data-answer','data-passage-ref="41-42" data-answer')}`
    const qs=importQuestionFile('common.html',html).questions.map(q=>q.canonical!)
    expect(qs).toHaveLength(2);expect(qs[0].passage.text).toBe(qs[1].passage.text);expect(qs[0].source.common_passage_id).toBe('41-42')
  })
  it('resolves an exact Reading label from a stored school print',()=>{
    const result=resolveReferencedPassage('Reading 1',[{id:7,title:'lesson',html_text:`<h2>Reading 1</h2><p>${'Verified source text '.repeat(5)}</p><h2>Reading 2</h2><p>other</p>`}])
    expect(result?.materialId).toBe(7);expect(result?.value.text).toContain('Verified source text')
  })
  it('preserves the exact underline position',()=>{
    const q=importQuestionFile('underline.html',dataQuestion('19','밑줄 친 부분의 의미는?',`<div class="passage">The problem is <u>more complicated than it appears</u>.</div>`)).questions[0].canonical!
    expect(q.passage.html).toContain('<u>more complicated than it appears</u>');expect(isSelfContained(q)).toBe(true)
  })
  it('preserves blank, bold, numbering, box, table and condition markup',()=>{
    const extra='<div class="cond"><strong>조건</strong>: ① 한 문장</div>'
    const q=importQuestionFile('markup.html',dataQuestion('20','빈칸에 알맞은 것은?',`<div class="passage"><p><strong>① Important</strong> <span class="blank box">_____</span></p><table><tbody><tr><td>표</td></tr></tbody></table></div>`,extra)).questions[0].canonical!
    expect(q.passage.html).toContain('<strong>① Important</strong>');expect(q.passage.html).toContain('class="blank box"');expect(q.passage.html).toContain('<table>')
  })
  it('allows a truly independent vocabulary question without passage',()=>{
    const q=importQuestionFile('vocab.html',dataQuestion('1','rapid과 의미가 같은 것은?','')).questions[0].canonical!
    expect(q.passage.status).toBe('not_applicable');expect(isSelfContained(q)).toBe(true)
  })
})

describe('Chrome transformed question normalization',()=>{
  const sentence='If we had not turned trash into music, we could not have given people hope.'
  const base={instruction:'다음 조건에 맞게 영작하시오.',passage:{text:'Turn trash into music and give people hope.','html':'<p>Turn trash into music and give people hope.</p>',status:'complete'},conditions:['가정법 과거완료를 사용할 것'],explanation:'가정법 과거완료 해설',score:5}
  for(const value of ['서답형','서술형','주관식','subjective','short_answer','short-answer','essay','sa'])it(`normalizes ${value} to sa`,()=>{
    const q=normalizeChromeQuestion({...base,type:value,correct_answer:sentence})
    expect(q.kind).toBe('sa');expect(q.answer.sample_answer).toBe(sentence)
  })
  it('recognizes all compatible type locations without mc fallback',()=>{
    for(const payload of [{kind:'서답형'},{question_type:'subjective'},{item_type:'essay'},{is_subjective:true}])expect(normalizeChromeQuestion({...base,...payload,options:['A','B']}).kind).toBe('sa')
  })
  it('normalizes every subjective answer alias and accepted answers',()=>{
    for(const key of ['correct_answer','model_answer','answer','answer_text','sample_answer','sampleAnswer','subjective_answer'])expect(normalizeChromeQuestion({...base,type:'sa',[key]:sentence}).answer.sample_answer).toBe(sentence)
    const q=normalizeChromeQuestion({...base,type:'sa',accepted_answers:[sentence,'Alternative answer.']})
    expect(q.answer.sample_answer).toBe(sentence);expect(q.answer.accepted_answers).toHaveLength(2)
  })
  it('keeps the same canonical answer through the storage projection',()=>{
    const canonical=normalizeChromeQuestion({...base,type:'서답형',model_answer:sentence})
    const stored=wikiQuestionToLegacy(canonical)
    expect(stored.type).toBe('sa');expect(stored.sampleAnswer).toBe(sentence);expect(stored.canonical).toBe(canonical)
  })
  it('keeps objective choice answers unchanged',()=>{
    const q=normalizeChromeQuestion({...base,type:'mc',choices:['A','B','C','D','E'],answer:[3]})
    expect(q.kind).toBe('mc');expect(q.answer.choice_numbers).toEqual([3]);expect(q.answer.sample_answer).toBe('')
  })
  it('accepts direct and UPDATE_VARIANT_QUESTION wrapped QID 82 results',()=>{
    const payload={type:'서답형',question_type:'subjective',instruction:'다음 조건에 맞게 영작하시오.',
      passage:{text:'Turn trash into music and give people hope.',html:'<p>Turn trash into music and give people hope.</p>',status:'complete'},
      conditions:['가정법 과거완료를 사용할 것'],model_answer:sentence,explanation:'가정법 과거완료 해설'}
    const direct=normalizeChromeQuestionResult(JSON.stringify(payload))
    const wrapped=normalizeChromeQuestionResult(JSON.stringify({action:'UPDATE_VARIANT_QUESTION',data:payload}))
    expect(direct.kind).toBe('sa');expect(direct.answer.sample_answer).toBe(sentence)
    expect(wrapped).toEqual(direct);expect(isSelfContained(wrapped)).toBe(true)
  })
  it('rejects invalid JSON and non-self-contained transformed results',()=>{
    expect(()=>normalizeChromeQuestionResult('{bad json')).toThrow(/올바른 JSON/)
    expect(()=>normalizeChromeQuestionResult(JSON.stringify({type:'서답형',instruction:'밑줄 친 부분을 설명하시오.',model_answer:'답'}))).toThrow(/self-contained/)
  })
})
