import { render,screen } from '@testing-library/react'
import { describe,expect,it } from 'vitest'
import { normalizeChromeQuestion,wikiQuestionToLegacy } from '../lib/wikiQuestion'
import type { StoredQuestion } from '../lib/types'
import QuestionCard from './QuestionCard'

const stored=(payload:Record<string,unknown>):StoredQuestion=>{
  const canonical=normalizeChromeQuestion(payload),legacy=wikiQuestionToLegacy(canonical)
  return{id:0,twin_id:null,school:'신구중',grade:'3학년',exam:'중간고사',mode:'transform',q_kind:legacy.type,
    question_type:legacy.questionType,stem:legacy.stem,source_text:legacy.sourceText,options:legacy.options,answer:legacy.answer,
    multi:legacy.multi?1:0,score:legacy.score,conditions:legacy.conditions,answer_format:legacy.answerFormat,
    accepted_answers:legacy.acceptedAnswers,sample_answer:legacy.sampleAnswer,grading_rule:legacy.gradingRule,
    exam_title:'Chrome 변형',exam_group_id:'preview',favorite:0,created_at:'now',canonical}
}

describe('Chrome Question Workbench preview binding',()=>{
  it('renders a Chrome subjective writing result and its model answer',()=>{
    const answer='If we had not turned trash into music, we could not have given people hope.'
    render(<QuestionCard index={1} q={stored({item_type:'서답형',instruction:'다음 조건에 맞게 영작하시오.',
      passage:{text:'Turn trash into music and give people hope.',html:'<p>Turn trash into music and give people hope.</p>',status:'complete'},
      conditions:['가정법 과거완료를 사용할 것'],model_answer:answer,explanation:'가정법 과거완료 해설',score:5})}/>)
    expect(screen.getByText(/서답형 1/)).toBeInTheDocument();expect(screen.getByText(`모범답안: ${answer}`)).toBeInTheDocument()
    expect(screen.getByText('가정법 과거완료를 사용할 것')).toBeInTheDocument()
  })

  it('keeps an objective Chrome result and choice answer',()=>{
    render(<QuestionCard index={1} q={stored({kind:'mc',instruction:'정답을 고르시오.',choices:['A','B','C','D','E'],answer:[3]})}/>)
    expect(screen.queryByText(/서답형/)).not.toBeInTheDocument();expect(screen.getByText('정답: 3')).toBeInTheDocument()
  })
})
