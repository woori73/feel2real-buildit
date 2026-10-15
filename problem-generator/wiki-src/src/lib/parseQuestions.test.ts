import { describe, expect, it } from 'vitest'
import { parsePastedQuestions, toContractQuestion } from './parseQuestions'

describe('parsePastedQuestions', () => {
  it('서답형(정답/해설)과 객관식(선택지/번호정답)을 분리한다', () => {
    const text = `Q1. 다음 빈칸에 알맞은 단어를 쓰시오.
The temperature increased ______ over time.
정답: gradually
해설: gradually는 '점차적으로'라는 뜻이다.

Q2. 다음 중 어법상 옳은 것은?
① is  ② are  ③ was  ④ were  ⑤ be
정답: ②
해설: 주어가 복수이므로 are.
유형: 어법`
    const qs = parsePastedQuestions(text)
    expect(qs).toHaveLength(2)

    expect(qs[0].type).toBe('sa')
    expect(qs[0].stem).toContain('빈칸에 알맞은 단어')
    expect(qs[0].sampleAnswer).toBe('gradually')
    expect(qs[0].gradingRule).toContain('점차적으로')

    expect(qs[1].type).toBe('mc')
    expect(qs[1].options).toEqual(['is', 'are', 'was', 'were', 'be'])
    expect(qs[1].answer).toEqual([2])
    expect(qs[1].questionType).toBe('어법')
  })

  it('번호 마커가 없으면 빈 줄로 분리하고, 인식 못한 정답은 경고를 남긴다', () => {
    const qs = parsePastedQuestions(`첫 문제 지문\n① a ② b ③ c\n\n둘째 문제 지문\n정답: 서술`)
    expect(qs).toHaveLength(2)
    expect(qs[0]._warnings).toContain('정답 번호를 인식하지 못했습니다')
  })

  it('toContractQuestion은 미리보기 전용 필드를 제거한다', () => {
    const [q] = parsePastedQuestions('1. 문제\n정답: 답')
    const c = toContractQuestion(q) as Record<string, unknown>
    expect(c._warnings).toBeUndefined()
    expect(c._answerText).toBeUndefined()
    expect(c).toHaveProperty('stem')
    expect(c).toHaveProperty('gradingRule')
  })
})
