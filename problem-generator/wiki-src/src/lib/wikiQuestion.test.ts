import { describe,expect,it } from 'vitest'
import { importQuestionFile } from './importQuestions'
import { isSelfContained,resolveReferencedPassage } from './wikiQuestion'

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
