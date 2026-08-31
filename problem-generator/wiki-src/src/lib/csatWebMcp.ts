import { api } from './api'

type SiteTool = {
  name:string
  description:string
  inputSchema:Record<string,unknown>
  annotations:{readOnlyHint:boolean}
  execute:(input:Record<string,unknown>)=>Promise<unknown>
}

declare global {
  interface Document {
    modelContext?: { registerTool:(tool:SiteTool)=>Promise<unknown>|unknown }
  }
}

let registration:Promise<void>|null=null

const call=async(path:string,init?:RequestInit)=>{
  const response=await api(path,init)
  const body=await response.json()
  if(!response.ok)throw new Error(typeof body.detail==='string'?body.detail:'CSAT Service 요청에 실패했습니다.')
  return body
}

const objectSchema=(properties:Record<string,unknown>,required:string[]=[]):Record<string,unknown>=>({
  type:'object',properties,required,additionalProperties:false,
})

export const registerCsatSiteTools=():Promise<void>=>{
  if(registration)return registration
  const register=document.modelContext?.registerTool
  if(typeof register!=='function')return Promise.resolve()
  const tools:SiteTool[]=[
    {
      name:'search_csat_questions',
      description:'Search the signed-in BuildIt Wiki CSAT Question DB by official CSAT number or type. This does not record usage.',
      inputSchema:objectSchema({
        exam_kind:{type:'string',enum:['half','full']},question_no:{type:'integer',minimum:18,maximum:45},type_id:{type:'string'},
        source_id:{type:'integer',minimum:1},source_filename:{type:'string'},source_round:{type:'string'},
        has_answer:{type:'boolean'},has_explanation:{type:'boolean'},review_status:{type:'string',enum:['classified','review']},
        exclude_used:{type:'boolean'},used_in:{type:'string',enum:['half','full','wrong','daily']},
      }),
      annotations:{readOnlyHint:true},
      execute:async input=>{const query=new URLSearchParams();for(const key of ['exam_kind','question_no','type_id','source_id','source_filename','source_round','has_answer','has_explanation','review_status','exclude_used','used_in'])if(input[key]!==undefined)query.set(key,String(input[key]));return call(`csat/questions${query.size?`?${query}`:''}`)},
    },
    {
      name:'search_csat_wrong_answers',
      description:'Search one student’s saved CSAT wrong answers by exam, official type, number, and Question-link state. The signed-in Wiki session is reused and an exact student condition is required to avoid exposing all student records.',
      inputSchema:objectSchema({
        student:{type:'string',minLength:1},exam:{type:'string'},type_id:{type:'string'},
        question_no:{type:'integer',minimum:18,maximum:45},linked:{type:'boolean'},
      },['student']),
      annotations:{readOnlyHint:true},
      execute:async input=>{if(!String(input.student||'').trim())throw new Error('student 조건이 필요합니다.');const query=new URLSearchParams();for(const key of ['student','exam','type_id','question_no','linked'])if(input[key]!==undefined)query.set(key,String(input[key]));return call(`csat/wrong-answers?${query}`)},
    },
    {
      name:'link_csat_wrong_question',
      description:'Link one existing CSAT wrong-answer record to one existing Question DB ID. The existing server validation requires the official CSAT question number to match.',
      inputSchema:objectSchema({wrong_answer_id:{type:'integer',minimum:1},question_id:{type:'integer',minimum:1}},['wrong_answer_id','question_id']),
      annotations:{readOnlyHint:false},
      execute:input=>call(`csat/wrong-answers/${Number(input.wrong_answer_id)}/question`,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({question_id:Number(input.question_id)})}),
    },
    {
      name:'get_csat_assessment',
      description:'Read one saved CSAT Assessment and its canonical ordered Question IDs so the same result can be reopened in Wiki.',
      inputSchema:objectSchema({assessment_id:{type:'integer',minimum:1}},['assessment_id']),
      annotations:{readOnlyHint:true},
      execute:input=>call(`csat/assessments/${Number(input.assessment_id)}`),
    },
    {
      name:'create_csat_assessment',
      description:'Create and save a HALF, FULL, or DAILY Assessment from existing Question DB assets. This write records usage only after the Assessment saves successfully. FULL always uses one Question for every number 18–45 (28 total); DAILY uses four questions per day. The result includes stable problem/explanation HTML URLs for reopening in Wiki.',
      inputSchema:objectSchema({
        assessment_type:{type:'string',enum:['half','full','daily']},title:{type:'string'},
        question_ids:{type:'array',items:{type:'integer',minimum:1},uniqueItems:true,maxItems:120},
        question_no:{type:'integer',minimum:18,maximum:45},type_id:{type:'string'},
        count:{type:'integer',minimum:1,maximum:45},days:{type:'integer',minimum:1,maximum:30},exclude_used:{type:'boolean'},
      },['assessment_type']),
      annotations:{readOnlyHint:false},
      execute:input=>call('csat/assessments',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({
        assessment_type:input.assessment_type,title:input.title||'',question_ids:input.question_ids||[],
        question_no:input.question_no,type_id:input.type_id||'',count:input.count||4,days:input.days||1,
        exclude_used:input.exclude_used??false,
      })}),
    },
    {
      name:'create_csat_wrong_assessment',
      description:'Create and save a WRONG Assessment from existing linked wrong-answer record IDs. Student identity is not returned; the result contains the Assessment and Question assets only.',
      inputSchema:objectSchema({title:{type:'string'},wrong_answer_ids:{type:'array',items:{type:'integer',minimum:1},minItems:1,maxItems:45,uniqueItems:true},exclude_used:{type:'boolean'}},['wrong_answer_ids']),
      annotations:{readOnlyHint:false},
      execute:input=>call('csat/wrong-assessments',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({title:input.title||'',wrong_answer_ids:input.wrong_answer_ids,exclude_used:input.exclude_used??false})}),
    },
    {
      name:'replace_csat_assessment_question',
      description:'Replace one position in a saved Assessment with another existing Question ID. DAILY problem and explanation outputs both follow the same updated item.',
      inputSchema:objectSchema({assessment_id:{type:'integer',minimum:1},position:{type:'integer',minimum:1,maximum:120},question_id:{type:'integer',minimum:1}},['assessment_id','position','question_id']),
      annotations:{readOnlyHint:false},
      execute:input=>call(`csat/assessments/${Number(input.assessment_id)}/items/${Number(input.position)}`,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({question_id:Number(input.question_id)})}),
    },
  ]
  registration=Promise.all(tools.map(tool=>Promise.resolve(register.call(document.modelContext,tool)))).then(()=>undefined).catch(error=>{registration=null;throw error})
  return registration
}
