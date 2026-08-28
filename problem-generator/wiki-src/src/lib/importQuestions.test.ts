import { describe, expect, it } from 'vitest'
import { importQuestionFile } from './importQuestions'

describe('importQuestionFile', () => {
  it('BuildIt 구조화 JSON은 regex 없이 그대로 읽고 metadata를 보존한다', () => {
    const json = JSON.stringify({
      title: 'Boiling Frog Vocab', source_material: 'The Boiling Frog Myth', source_page: '2',
      digital_twin: '신사중 3학년 출제자 Digital Twin', difficulty: '중상',
      questions: [
        { type: 'mc', questionType: '문맥 어휘', stem: 'gradually?', options: ['a', 'b', 'c', 'd', 'e'], answer: [1], score: 4, explanation: '점차적으로' },
        { type: 'sa', questionType: '영작', stem: 'gradually로 문장', answer: 'It rose gradually.', explanation: '문법 일치' },
      ],
    })
    const r = importQuestionFile('set.json', json)
    expect(r.format).toBe('json')
    expect(r.questions).toHaveLength(2)
    expect(r.questions[0].type).toBe('mc')
    expect(r.questions[0].answer).toEqual([1])
    expect(r.questions[0].gradingRule).toBe('점차적으로')
    expect(r.questions[1].type).toBe('sa')
    expect(r.questions[1].sampleAnswer).toBe('It rose gradually.')
    expect(r.meta).toMatchObject({ title: 'Boiling Frog Vocab', sourceMaterial: 'The Boiling Frog Myth', sourcePage: '2', digitalTwin: '신사중 3학년 출제자 Digital Twin' })
  })

  it('choices/correct/해설 같은 다른 키 이름도 매핑한다', () => {
    const r = importQuestionFile('x.json', JSON.stringify([
      { question: '옳은 것은?', choices: ['is', 'are', 'was'], correct: 2, 해설: '복수 주어' },
    ]))
    expect(r.questions[0].type).toBe('mc')
    expect(r.questions[0].options).toEqual(['is', 'are', 'was'])
    expect(r.questions[0].answer).toEqual([2])
    expect(r.questions[0].gradingRule).toBe('복수 주어')
  })

  it('HTML 시험지는 평문으로 바꿔 문항을 분리하고 제목 머리글은 문항으로 오인하지 않는다', () => {
    const html = `<html><body>
      <h1>신사중 3학년 Vocabulary</h1>
      <div class="q"><p>1. 다음 빈칸에 알맞은 것은?</p>
      <p>The temperature increased ______ over time.</p>
      <p>정답: gradually</p><p>해설: 점차적으로</p></div>
      <div class="q"><p>2. 어법상 옳은 것은?</p>
      <ol><li>is</li><li>are</li><li>was</li></ol><p>정답: 2</p></div>
      </body></html>`
    const r = importQuestionFile('exam.html', html)
    expect(r.format).toBe('html')
    expect(r.questions).toHaveLength(2)
    expect(r.questions[0].stem).toContain('빈칸에 알맞은')
    expect(r.questions[0].sampleAnswer).toBe('gradually')
    expect(r.questions[1].type).toBe('mc')
    expect(r.questions[1].options).toEqual(['is', 'are', 'was'])
    expect(r.questions[1].answer).toEqual([2])
  })

  it('TXT는 기존 붙여넣기 parser를 그대로 쓴다', () => {
    const r = importQuestionFile('q.txt', 'Q1. 문제\n① a ② b ③ c\n정답: ①')
    expect(r.format).toBe('txt')
    expect(r.questions[0].type).toBe('mc')
    expect(r.questions[0].answer).toEqual([1])
  })
})
