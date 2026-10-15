export type CsatWrongBulkInput={
  question_id?:number
  student_name:string
  exam_name:string
  question_no:number
  wrong_answer:string
  correct_answer:string
  memo:string
  source_filename:string
}

export type CsatWrongBulkRow={
  row_no:number
  status:'READY'|'FAILED'|'IMPORTED'
  errors:string[]
  input:CsatWrongBulkInput
  saved_id?:number
}

const aliases:Record<string,keyof CsatWrongBulkInput>={
  student:'student_name',student_name:'student_name','학생':'student_name','학생명':'student_name',
  exam:'exam_name',exam_name:'exam_name','시험':'exam_name','시험명':'exam_name',
  question_no:'question_no','번호':'question_no','문항번호':'question_no',
  question_id:'question_id','qid':'question_id','question id':'question_id',
  wrong_answer:'wrong_answer','학생답':'wrong_answer','오답':'wrong_answer',
  correct_answer:'correct_answer','정답':'correct_answer',
  memo:'memo','메모':'memo',source_filename:'source_filename','출처파일':'source_filename','원본파일':'source_filename',
}

const parseCsv=(text:string):string[][]=>{
  const rows:string[][]=[]
  let row:string[]=[],cell='',quoted=false
  for(let index=0;index<text.length;index+=1){
    const char=text[index]
    if(char==='"'){
      if(quoted&&text[index+1]==='"'){cell+='"';index+=1}else quoted=!quoted
    }else if(char===','&&!quoted){row.push(cell.trim());cell=''}
    else if((char==='\n'||char==='\r')&&!quoted){if(char==='\r'&&text[index+1]==='\n')index+=1;row.push(cell.trim());if(row.some(Boolean))rows.push(row);row=[];cell=''}
    else cell+=char
  }
  row.push(cell.trim());if(row.some(Boolean))rows.push(row)
  return rows
}

const normalise=(value:Record<string,unknown>,row_no:number):CsatWrongBulkRow=>{
  const questionNo=Number(value.question_no||0),questionId=value.question_id===undefined||value.question_id===''?undefined:Number(value.question_id)
  const input:CsatWrongBulkInput={
    student_name:String(value.student_name||'').trim(),exam_name:String(value.exam_name||'').trim(),question_no:questionNo,
    wrong_answer:String(value.wrong_answer||'').trim(),correct_answer:String(value.correct_answer||'').trim(),
    memo:String(value.memo||'').trim(),source_filename:String(value.source_filename||'').trim(),
    ...(questionId===undefined?{}:{question_id:questionId}),
  }
  const errors:string[]=[]
  if(!input.student_name)errors.push('학생명이 필요합니다.')
  if(!Number.isInteger(questionNo)||questionNo<18||questionNo>45)errors.push('문항번호는 18~45 사이여야 합니다.')
  if(questionId!==undefined&&(!Number.isInteger(questionId)||questionId<1))errors.push('Question ID는 양의 정수여야 합니다.')
  return {row_no,status:errors.length?'FAILED':'READY',errors,input}
}

export const parseCsatWrongBulk=(text:string):CsatWrongBulkRow[]=>{
  const source=text.trim()
  if(!source)return[]
  if(source.startsWith('[')||source.startsWith('{')){
    let parsed:unknown
    try{parsed=JSON.parse(source)}catch{return[{row_no:1,status:'FAILED',errors:['JSON 형식이 올바르지 않습니다.'],input:{student_name:'',exam_name:'',question_no:0,wrong_answer:'',correct_answer:'',memo:'',source_filename:''}}]}
    const items=Array.isArray(parsed)?parsed:[parsed]
    return items.map((item,index)=>normalise(item&&typeof item==='object'?item as Record<string,unknown>:{},index+1))
  }
  const matrix=source.includes('\t')?source.split(/\r?\n/).filter(Boolean).map(line=>line.split('\t').map(cell=>cell.trim())):parseCsv(source)
  if(matrix.length<2)return[{row_no:1,status:'FAILED',errors:['헤더와 한 행 이상의 데이터가 필요합니다.'],input:{student_name:'',exam_name:'',question_no:0,wrong_answer:'',correct_answer:'',memo:'',source_filename:''}}]
  const headers=matrix[0].map(header=>aliases[header.trim().toLowerCase()]||aliases[header.trim()])
  if(!headers.includes('student_name')||!headers.includes('question_no'))return[{row_no:1,status:'FAILED',errors:['학생명(student_name)과 문항번호(question_no) 열이 필요합니다.'],input:{student_name:'',exam_name:'',question_no:0,wrong_answer:'',correct_answer:'',memo:'',source_filename:''}}]
  return matrix.slice(1).map((cells,index)=>{
    const item:Record<string,unknown>={}
    headers.forEach((header,column)=>{if(header)item[header]=cells[column]||''})
    return normalise(item,index+2)
  })
}
