export type RichText={text:string;html:string}
export type PassageStatus='complete'|'not_applicable'|'missing'|'recovered'|'needs_review'
export type WikiQuestionV1={
  schema_version:'wiki-question-v1';kind:'mc'|'sa';question_type:string
  instruction:RichText;passage:RichText&{status:PassageStatus}
  choices:Array<RichText&{number:number}>;conditions:RichText[]
  answer:{choice_numbers:number[];accepted_answers:string[];sample_answer:string}
  explanation:RichText;scoring:{score:number;multi:boolean;answer_format:string;grading_rule:string}
  source:{school?:string;grade?:string;year?:number;semester?:string;exam?:string;source_material_id?:number;source_page?:string;source_question_number?:string;common_passage_id?:string}
}

const ALLOWED=new Set(['P','BR','DIV','SPAN','U','STRONG','B','EM','I','TABLE','THEAD','TBODY','TR','TH','TD','UL','OL','LI','BOX','HR','SUP','SUB'])
export const sanitizeQuestionHtml=(html:string)=>{
  if(!html)return''
  const doc=new DOMParser().parseFromString(`<div>${html}</div>`,'text/html'),root=doc.body.firstElementChild as HTMLElement
  root.querySelectorAll('script,style,iframe,object,embed,link,meta').forEach(n=>n.remove())
  for(const el of [...root.querySelectorAll('*')]){
    if(!ALLOWED.has(el.tagName)){el.replaceWith(...Array.from(el.childNodes));continue}
    for(const attr of [...el.attributes]){
      const classes=attr.value.split(/\s+/).filter(Boolean)
      if(attr.name!=='class'||!classes.length||classes.some(v=>!['blank','box','underline','emphasis','passage-paragraph'].includes(v)))el.removeAttribute(attr.name)
    }
  }
  return root.innerHTML.trim()
}
export const textOfHtml=(html:string)=>{const doc=new DOMParser().parseFromString(html,'text/html');return(doc.body.textContent||'').replace(/\u00a0/g,' ').replace(/[ \t]+/g,' ').replace(/\n\s*\n/g,'\n').trim()}
export const rich=(html:string,text=''):RichText=>{const safe=sanitizeQuestionHtml(html);return{text:text||textOfHtml(safe),html:safe}}
export const needsPassage=(instruction:string,questionType:string)=>/읽고|글의|지문|Reading\s*\d+|본문|밑줄|순서|삽입|내용|주제|제목|요지/i.test(`${instruction} ${questionType}`)
export const referenceLabel=(instruction:string)=>instruction.match(/(?:Reading\s*\d+|\d+과\s*본문|학습지\s*[A-Z가-힣0-9]+)/i)?.[0]||''
export const isSelfContained=(q:WikiQuestionV1)=>{
  if(!q.instruction.text.trim())return false
  if(q.kind==='mc'&&q.choices.length<2)return false
  if(needsPassage(q.instruction.text,q.question_type)&&!['complete','recovered'].includes(q.passage.status))return false
  const markup=`${q.instruction.html} ${q.passage.html}`
  if(/밑줄/.test(q.instruction.text)&&!/<u(?:\s|>)/i.test(markup)&&!markup.includes('class="underline"'))return false
  return true
}

export const WIKI_QUESTION_SCHEMA={
  $id:'wiki-question-v1',type:'object',required:['schema_version','kind','question_type','instruction','passage','choices','conditions','answer','explanation','scoring','source'],
  properties:{
    schema_version:{const:'wiki-question-v1'},kind:{enum:['mc','sa']},question_type:{type:'string'},
    instruction:{$ref:'#/$defs/rich'},passage:{allOf:[{$ref:'#/$defs/rich'},{type:'object',required:['status'],properties:{status:{enum:['complete','recovered','not_applicable','missing','needs_review']}}}]},
    choices:{type:'array',items:{allOf:[{$ref:'#/$defs/rich'},{type:'object',required:['number'],properties:{number:{type:'integer'}}}]}},
    conditions:{type:'array',items:{$ref:'#/$defs/rich'}},
    answer:{type:'object',required:['choice_numbers','accepted_answers','sample_answer']},explanation:{$ref:'#/$defs/rich'},scoring:{type:'object'},source:{type:'object'},
  },$defs:{rich:{type:'object',required:['text','html'],properties:{text:{type:'string'},html:{type:'string'}}}},
} as const

export const wikiQuestionToLegacy=(q:WikiQuestionV1)=>({
  type:q.kind,questionType:q.question_type,sourceText:q.passage.text,stem:q.instruction.text,
  options:q.choices.map(c=>c.text),answer:q.answer.choice_numbers,multi:q.scoring.multi,score:q.scoring.score,
  conditions:q.conditions.map(c=>c.text),answerFormat:q.scoring.answer_format,acceptedAnswers:q.answer.accepted_answers,
  sampleAnswer:q.answer.sample_answer,gradingRule:q.explanation.text||q.scoring.grading_rule,
  canonical:q,sourceQuestionNumber:q.source.source_question_number||'',
})

type Material={id:number;title:string;html_text?:string|null;json_text?:unknown}
export const resolveReferencedPassage=(label:string,materials:Material[]):{materialId:number;value:RichText}|null=>{
  if(!label)return null
  const found:Array<{materialId:number;value:RichText}>=[]
  for(const material of materials){
    const doc=new DOMParser().parseFromString(material.html_text||'','text/html')
    const headings=[...doc.querySelectorAll('h1,h2,h3,h4,[data-reading],[data-section-title],.reading-title,.lesson-title')]
    const normalized=(value:string)=>value.replace(/\s+/g,' ').trim().toLowerCase()
    const start=headings.find(h=>normalized(h.textContent||'')===normalized(label))
    if(!start)continue
    const nodes:Element[]=[];let cursor=start.nextElementSibling
    while(cursor&&!/^H[1-4]$/.test(cursor.tagName)&&!cursor.matches('[data-reading],[data-section-title],.reading-title,.lesson-title')){nodes.push(cursor);cursor=cursor.nextElementSibling}
    const html=nodes.map(n=>n.outerHTML).join('')
    if(textOfHtml(html).length>=40)found.push({materialId:material.id,value:rich(html)})
  }
  return found.length===1?found[0]:null
}
