import { describe,expect,it } from 'vitest'
import { isCanonicalInlineUnderline,isSelfContained,normalizeChromeQuestionResult } from './wikiQuestion'

describe('inline underline objective Chrome preview',()=>{
  const payload={
    type:'mc',
    question_type:'grammar_underlined',
    instruction:'다음 글의 밑줄 친 부분 중 어법상 틀린 것은?',
    passage:{
      text:'① had taken ② could play ③ wishes ④ knows ⑤ were',
      html:'<p>① <u>had taken</u> ② <u>could play</u> ③ <u>wishes</u> ④ <u>knows</u> ⑤ <u>were</u></p>',
      status:'complete',
    },
    choices:[{number:1,text:''},{number:2,text:''},{number:3,text:''},{number:4,text:''},{number:5,text:''}],
    answer:'③',
  }

  it('treats numbered underlines in the passage as the objective choices',()=>{
    const q=normalizeChromeQuestionResult(payload)
    expect(isCanonicalInlineUnderline(q)).toBe(true)
    expect(q.answer.choice_numbers).toEqual([3])
    expect(isSelfContained(q)).toBe(true)
  })

  it('does not allow a normal objective question with blank option text',()=>{
    expect(()=>normalizeChromeQuestionResult({...payload,question_type:'multiple_choice',instruction:'알맞은 것을 고르시오.',passage:{text:'plain passage',html:'<p>plain passage</p>',status:'complete'}})).toThrow(/선택지 텍스트/)
  })
})
