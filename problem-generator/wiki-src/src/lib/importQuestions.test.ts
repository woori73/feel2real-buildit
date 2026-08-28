import { describe, expect, it } from 'vitest'
import { importQuestionFile } from './importQuestions'
// 운영에서 실제 오류가 났던 파일을 fixture로 사용한다.
import studentHtml from './__fixtures__/boiling-frog-vocab-student.html?raw'

describe('importQuestionFile — 구조화 JSON', () => {
  it('BuildIt JSON은 regex 없이 그대로 읽고 metadata를 보존한다', () => {
    const json = JSON.stringify({
      title: 'Boiling Frog Vocab', source_material: 'The Boiling Frog Myth', source_page: '2',
      digital_twin: '신사중 3학년 출제자 Digital Twin',
      questions: [
        { type: 'mc', questionType: '문맥 어휘', stem: 'gradually?', options: ['a', 'b', 'c', 'd', 'e'], answer: [1], score: 4, explanation: '점차적으로' },
        { type: 'sa', questionType: '영작', stem: 'gradually로 문장', answer: 'It rose gradually.', explanation: '문법 일치' },
      ],
    })
    const r = importQuestionFile('set.json', json)
    expect(r.format).toBe('json')
    expect(r.questions).toHaveLength(2)
    expect(r.questions[0].answer).toEqual([1])
    expect(r.questions[1].sampleAnswer).toBe('It rose gradually.')
    expect(r.meta).toMatchObject({ title: 'Boiling Frog Vocab', sourceMaterial: 'The Boiling Frog Myth', sourcePage: '2' })
    expect(r.lowConfidence).toBe(false)
  })

  it('choices/correct/해설 같은 다른 키 이름도 매핑한다', () => {
    const r = importQuestionFile('x.json', JSON.stringify([
      { question: '옳은 것은?', choices: ['is', 'are', 'was'], correct: 2, 해설: '복수 주어' },
    ]))
    expect(r.questions[0].type).toBe('mc')
    expect(r.questions[0].options).toEqual(['is', 'are', 'was'])
    expect(r.questions[0].answer).toEqual([2])
  })
})

describe('importQuestionFile — TXT', () => {
  it('기존 붙여넣기 parser를 그대로 쓴다', () => {
    const r = importQuestionFile('q.txt', 'Q1. 문제\n① a ② b ③ c\n정답: ①')
    expect(r.format).toBe('txt')
    expect(r.questions[0].type).toBe('mc')
    expect(r.questions[0].answer).toEqual([1])
  })
})

describe('importQuestionFile — 실제 HTML 시험지 (신사중3 Boiling Frog 학생용)', () => {
  const r = importQuestionFile('신사중3_Boiling_Frog_Vocabulary_Test_학생용_1.html', studentHtml)
  const q = (n: number) => r.questions[n - 1]

  it('HTML DOM 파서로 처리한다 (평문화하지 않음)', () => {
    expect(r.format).toBe('html')
  })

  it('30개 번호 문항 + Ⅷ 도전문제 = 31문항, 순서대로', () => {
    expect(r.questions).toHaveLength(31)
    // Q1~Q30 은 파일의 <b class="qn"> 순서 그대로
  })

  it('meta: 출처 학습자료/페이지를 헤더에서 읽는다', () => {
    expect(r.meta.sourceMaterial).toMatch(/Boiling Frog/)
    expect(r.meta.sourcePage).toBe('2')
  })

  it('Q1 — Ⅰ 단어 뜻 쓰기 (서답형, 2점, 정답 미등록)', () => {
    expect(q(1).type).toBe('sa')
    expect(q(1).stem).toBe('emission')
    expect(q(1).sourceText).toMatch(/우리말 뜻/)
    expect(q(1).score).toBe(2)
    expect(q(1).questionType).toMatch(/단어 뜻/)
    expect(q(1)._missingAnswer).toBe(true)
    expect(q(1).sampleAnswer).toBe('')
    expect(q(1).gradingRule).toBe('')   // 엉뚱한 기본 해설 주입 금지
  })

  it('Q2 — significant', () => {
    expect(q(2).stem).toBe('significant')
    expect(q(2).type).toBe('sa')
  })

  it('Q10 — Ⅱ 영단어 쓰기 (우리말 뜻 제시)', () => {
    expect(q(10).stem).toBe('악화시키다')
    expect(q(10).type).toBe('sa')
    expect(q(10).score).toBe(2)
  })

  it('Q20 — Ⅳ 문장 빈칸 (첫글자 힌트, 서답형 3점)', () => {
    expect(q(20).type).toBe('sa')
    expect(q(20).stem).toMatch(/Scientists have identified 1\.5°C/)
    expect(q(20).stem).toContain('____')
    expect(q(20).score).toBe(3)
    expect(q(20)._missingAnswer).toBe(true)
  })

  it('Q21 — Ⅴ 객관식 4지선다', () => {
    expect(q(21).type).toBe('mc')
    expect(q(21).options).toEqual(['adapt', 'adopt', 'offset', 'trap'])
    expect(q(21).stem).toMatch(/Animals and humans must/)
    expect(q(21).score).toBe(3)
    expect(q(21).answer).toEqual([])         // 학생용 → 정답 없음
    expect(q(21)._missingAnswer).toBe(true)
  })

  it('Q26 — Ⅵ 어색한 어휘 NOT형 (문장형 선택지)', () => {
    expect(q(26).type).toBe('mc')
    expect(q(26).options).toHaveLength(4)
    expect(q(26).options[3]).toMatch(/ignoring it and hoping/)
    expect(q(26).questionType).toMatch(/NOT/)
    expect(q(26).score).toBe(4)
  })

  it('Q29 — Ⅶ 조건 영작', () => {
    expect(q(29).type).toBe('sa')
    expect(q(29).stem).toMatch(/mitigate/)
    expect(q(29).conditions.length).toBeGreaterThanOrEqual(2)
    expect(q(29).score).toBe(5)
  })

  it('Q30 — be oblivious to 조건 영작', () => {
    expect(q(30).stem).toMatch(/be oblivious to/)
    expect(q(30).type).toBe('sa')
    expect(q(30).score).toBe(5)
  })

  it('Q31 — Ⅷ 도전문제 (보기에서 선택, 5점)', () => {
    expect(q(31).options).toEqual(['insidious', 'scalding', 'impending', 'significant', 'net zero'])
    expect(q(31).stem).toMatch(/Climate change is a slow but serious threat/)
    expect(q(31).score).toBe(5)
  })

  it('학생용 파일이므로 전 문항 정답 미등록 + 저조 신뢰도 아님(구조는 인식됨)', () => {
    expect(r.questions.every(x => x._missingAnswer)).toBe(true)
    expect(r.notes.some(n => /정답이 없는 문항 31개/.test(n))).toBe(true)
    expect(r.lowConfidence).toBe(false)   // 구조는 정확히 읽었으므로 저장 허용
  })

  it('어떤 문항에도 엉뚱한 문구가 explanation으로 들어가지 않는다', () => {
    expect(r.questions.every(x => x.gradingRule === '')).toBe(true)
  })
})

describe('importQuestionFile — 알 수 없는 HTML 구조', () => {
  it('문항 container를 못 찾으면 lowConfidence로 저장을 막는다', () => {
    const r = importQuestionFile('weird.html', '<html><body><p>emission →</p><p>gradually</p></body></html>')
    expect(r.lowConfidence).toBe(true)
    expect(r.notes.length).toBeGreaterThan(0)
  })
})
