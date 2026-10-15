import { describe, expect, it } from 'vitest'
import { parseCsatWrongBulk } from './csatWrongBulk'

describe('CSAT WRONG bulk parser',()=>{
  it('표 붙여넣기를 행별로 preview하고 오류를 격리한다',()=>{
    const rows=parseCsatWrongBulk('학생명\t시험명\t문항번호\t학생답\t정답\tQuestion ID\nTEST_STUDENT_001\tTEST_EXAM\t21\t2\t1\t7\nTEST_STUDENT_002\tTEST_EXAM\t99\t3\t1\t')
    expect(rows.map(row=>row.status)).toEqual(['READY','FAILED'])
    expect(rows[0].input).toMatchObject({student_name:'TEST_STUDENT_001',question_no:21,question_id:7})
    expect(rows[1].errors).toContain('문항번호는 18~45 사이여야 합니다.')
  })

  it('CSV와 JSON을 동일한 canonical row로 정규화한다',()=>{
    const csv=parseCsatWrongBulk('student_name,exam_name,question_no,wrong_answer,correct_answer\nTEST_STUDENT_003,TEST_EXAM,34,4,2')
    const json=parseCsatWrongBulk('[{"student_name":"TEST_STUDENT_003","exam_name":"TEST_EXAM","question_no":34,"wrong_answer":"4","correct_answer":"2"}]')
    expect(csv[0].input).toEqual(json[0].input)
    expect(csv[0].status).toBe('READY')
  })
})
