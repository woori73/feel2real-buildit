import { describe, expect, it } from 'vitest'
import { EDITOR_BOOTSTRAP_SNIPPET, opsToText, textToOps } from './WeeklyExamEditor'

describe('WeeklyExamEditor edit op (structure only, no AI)', () => {
  it('serializes ops to editable text and parses them back', () => {
    // given
    const ops = [
      { op: 'text' as const, question: 3, detail: '발문 수정' },
      { op: 'move' as const, question: 5, detail: '위로 이동' },
      { op: 'text' as const, question: 2, detail: '보기 정렬 상태 확인' },
    ]
    // when
    const text = opsToText(ops)
    const parsed = textToOps(text)
    // then
    expect(text).toBe('Q3 text: 발문 수정\nQ5 move: 위로 이동\nQ2 text: 보기 정렬 상태 확인')
    expect(parsed).toEqual(ops)
  })

  it('parses free-form lines as generic text ops', () => {
    // given/when
    const ops = textToOps('지문 오타 수정')
    // then
    expect(ops).toEqual([{ op: 'text', question: 0, detail: '지문 오타 수정' }])
  })

  it('bootstrap snippet wires per-question toolbars and parent messages without network calls', () => {
    // then: 편집 부트스트랩은 AI/API 호출이 없어야 하고 부모 메시지 contract를 유지한다
    expect(EDITOR_BOOTSTRAP_SNIPPET).toContain('data-qe-act="up"')
    expect(EDITOR_BOOTSTRAP_SNIPPET).toContain('data-qe-act="down"')
    expect(EDITOR_BOOTSTRAP_SNIPPET).toContain('data-qe-act="sort"')
    expect(EDITOR_BOOTSTRAP_SNIPPET).toContain("parent.postMessage({type:'qe:ready'},'*')")
    expect(EDITOR_BOOTSTRAP_SNIPPET).not.toMatch(/fetch\(|XMLHttpRequest/)
  })
})
