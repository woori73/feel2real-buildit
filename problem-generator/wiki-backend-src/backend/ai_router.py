"""Server-side AI router. Secret values never leave this process."""
import json, os, socket, time, urllib.error, urllib.request
from dataclasses import dataclass
from typing import Optional
import prompt_logic as pl
import private_reference_bank

DEFAULT_PROVIDER_TIMEOUT_SECONDS=90.0
MODELS_URL='https://openrouter.ai/api/v1/models'
CHAT_URL='https://openrouter.ai/api/v1/chat/completions'
FREE_ROUTER='openrouter/free'
QWEN_PROVIDER='openrouter'
QWEN_MODEL='qwen/qwen3.7-flash'
GEMINI_PROBLEM_MODEL='google/gemini-3.7-flash'
LUNA_MODEL='gpt-5.6-luna'
_models_cache=(0.0, [])

def provider_timeout_seconds():
    try: value=float(os.getenv('AI_PROVIDER_TIMEOUT_SECONDS', DEFAULT_PROVIDER_TIMEOUT_SECONDS))
    except ValueError: value=DEFAULT_PROVIDER_TIMEOUT_SECONDS
    return max(10.0,min(value,120.0))

class ProviderError(Exception):
    def __init__(self,message,provider,status=None):
        super().__init__(message); self.provider=provider; self.status=status

@dataclass
class ProviderResult:
    text:str
    provider:str
    model:str
    key_source:str

def _server_keys():
    return os.getenv('GEMINI_API_KEY','').strip(),os.getenv('OPENROUTER_API_KEY','').strip()

def _zero(value):
    try:return float(value)==0
    except (TypeError,ValueError):return False

def _free(model):
    pricing=model.get('pricing') or {}
    return str(model.get('id','')).endswith(':free') or (_zero(pricing.get('prompt')) and _zero(pricing.get('completion')))

def _normalized(model):
    architecture=model.get('architecture') or {}; supported=model.get('supported_parameters') or []
    return {'id':str(model.get('id','')),'name':str(model.get('name') or model.get('id') or ''),'contextLength':model.get('context_length'),'textGeneration':not architecture.get('output_modalities') or 'text' in architecture.get('output_modalities',[]),'structuredOutput':'response_format' in supported or 'structured_outputs' in supported,'free':True}

def strict_object_schema(value):
    """OpenRouter strict mode requires every nested object to reject unknown keys."""
    if isinstance(value,list):return [strict_object_schema(item) for item in value]
    if not isinstance(value,dict):return value
    result={key:strict_object_schema(item) for key,item in value.items()}
    if result.get('type')=='object':result['additionalProperties']=False
    return result

def openrouter_free_models(force=False):
    """Dynamic free text models. A stale successful cache is the graceful fallback."""
    global _models_cache
    now=time.time()
    if not force and _models_cache[1] and now-_models_cache[0]<900:return _models_cache[1]
    request=urllib.request.Request(MODELS_URL,headers={'Accept':'application/json','User-Agent':'BuildIt-Wiki/1.0'})
    try:
        with urllib.request.urlopen(request,timeout=min(provider_timeout_seconds(),20)) as response:
            payload=json.loads(response.read().decode())
        models=[_normalized(item) for item in payload.get('data',[]) if _free(item)]
        models=sorted((m for m in models if m['id'] and m['textGeneration']),key=lambda m:(not m['structuredOutput'],-(m['contextLength'] or 0),m['name']))
        _models_cache=(now,models);return models
    except Exception:return _models_cache[1]

def availability():
    gemini,openrouter=_server_keys()
    return {'defaultProvider':'auto','defaultModel':QWEN_MODEL,'providers':{
      'qwen':{'available':bool(openrouter),'experimental':False,'serverKeyConfigured':bool(openrouter),'model':QWEN_MODEL},
      'gpt-5.6-luna':{'available':False,'experimental':True,'serverKeyConfigured':False,'model':LUNA_MODEL,'purpose':'future-transform-review'},
      'gemini':{'available':bool(gemini),'experimental':False,'serverKeyConfigured':bool(gemini),'model':'gemini-flash'},
      'openrouter':{'available':bool(openrouter),'experimental':False,'serverKeyConfigured':bool(openrouter)},
      'openrouter-free':{'available':bool(openrouter),'experimental':False,'serverKeyConfigured':bool(openrouter),'model':FREE_ROUTER},
      'ox-alpha':{'available':bool(openrouter),'experimental':True,'serverKeyConfigured':bool(openrouter),'model':os.getenv('OX_ALPHA_MODEL','stealth/ox-alpha')}}}

def _openrouter(key,model,provider,system,prompt,schema,temperature):
    schema=strict_object_schema(schema) if schema else None
    if schema:prompt+='\n\n반드시 다음 JSON Schema를 따르는 JSON 객체만 반환하라:\n'+json.dumps(schema,ensure_ascii=False)
    body={'model':model,'messages':[{'role':'system','content':system},{'role':'user','content':prompt}],'temperature':temperature}
    if schema:body['response_format']={'type':'json_schema','json_schema':{'name':'buildit_response','strict':True,'schema':schema}}
    request=urllib.request.Request(CHAT_URL,data=json.dumps(body,ensure_ascii=False).encode(),method='POST',headers={'Authorization':f'Bearer {key}','Content-Type':'application/json','HTTP-Referer':'https://feel2real.com','X-Title':'BuildIt Wiki'})
    try:
        with urllib.request.urlopen(request,timeout=provider_timeout_seconds()) as response:payload=json.loads(response.read().decode())
        text=payload.get('choices',[{}])[0].get('message',{}).get('content','')
        if isinstance(text,list):text=''.join(x.get('text','') for x in text if isinstance(x,dict))
        if not str(text).strip():raise ProviderError('OpenRouter 응답 내용이 없습니다.',provider)
        return ProviderResult(str(text),payload.get('provider') or provider,payload.get('model') or model,'server')
    except urllib.error.HTTPError as exc:raise ProviderError(f'OpenRouter 요청 실패 ({exc.code})',provider,exc.code) from exc
    except (TimeoutError,socket.timeout) as exc:raise ProviderError('AI 응답 시간이 초과되었습니다. 다시 시도해 주세요.',provider,504) from exc
    except urllib.error.URLError as exc:raise ProviderError('OpenRouter 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.',provider,502) from exc
    except json.JSONDecodeError as exc:raise ProviderError('OpenRouter 응답을 해석하지 못했습니다.',provider,502) from exc

def generate_multimodal(model,system,content,temperature=.15):
    """Server-key OpenRouter vision call for school-print reconstruction; never falls back."""
    _,openrouter=_server_keys()
    if not openrouter:raise ProviderError('OpenRouter 서버 키가 설정되지 않았습니다.','openrouter',503)
    body={'model':model,'messages':[{'role':'system','content':system},{'role':'user','content':content}],'temperature':temperature}
    request=urllib.request.Request(CHAT_URL,data=json.dumps(body,ensure_ascii=False).encode(),method='POST',headers={'Authorization':f'Bearer {openrouter}','Content-Type':'application/json','HTTP-Referer':'https://feel2real.com','X-Title':'BuildIt Wiki'})
    try:
        with urllib.request.urlopen(request,timeout=provider_timeout_seconds()) as response:payload=json.loads(response.read().decode())
        text=payload.get('choices',[{}])[0].get('message',{}).get('content','')
        if not str(text).strip():raise ProviderError('OpenRouter 응답 내용이 없습니다.','openrouter')
        return ProviderResult(str(text),payload.get('provider') or 'openrouter',payload.get('model') or model,'server')
    except urllib.error.HTTPError as exc:raise ProviderError(f'OpenRouter 요청 실패 ({exc.code})','openrouter',exc.code) from exc
    except (TimeoutError,socket.timeout) as exc:raise ProviderError('AI 응답 시간이 초과되었습니다. 다시 시도해 주세요.','openrouter',504) from exc
    except urllib.error.URLError as exc:raise ProviderError('OpenRouter 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.','openrouter',502) from exc

def generate(provider,system,prompt,*,schema=None,temperature=.25,gemini_key='',openrouter_key='',allow_noncritical_fallback=False):
    """Use server-side Qwen by default.

    Explicit selections never fallback. Auto requests are strict by default so
    question generation cannot silently degrade to a free model. Callers may
    opt into the Ox/free backup chain only for non-critical assistance.
    Browser-supplied keys remain accepted for API compatibility but are ignored.

    Question Wiki free-only requests may carry a compact server-reference marker.
    Only explicit openrouter-free requests are enriched with private examples;
    the reference bank is never returned to the browser.
    """
    requested=provider or 'auto'; gemini_server,openrouter_server=_server_keys()
    gemini=gemini_server; openrouter=openrouter_server
    if requested=='openrouter-free' and private_reference_bank.has_request(prompt):
        prompt=private_reference_bank.enrich_prompt(prompt)
    candidates=['qwen','ox-alpha','openrouter-free'] if requested=='auto' and allow_noncritical_fallback else ['qwen'] if requested=='auto' else [requested]; last_error=None
    for candidate in candidates:
        try:
            if candidate=='gemini':
                if not gemini:raise ProviderError('Gemini 서버 키가 설정되지 않았습니다.',candidate)
                text=pl.call_gemini_structured(gemini,system,prompt,schema,temperature) if schema else pl.call_gemini_text(gemini,system,prompt,temperature)
                return ProviderResult(text,'gemini','gemini-flash','server')
            if candidate=='gpt-5.6-luna':
                raise ProviderError('GPT-5.6 Luna는 향후 변형문제 제작·중요 검수 확장용이며 아직 연결되지 않았습니다.',candidate,503)
            if candidate in ('qwen','ox-alpha','openrouter-free') or candidate.startswith('openrouter:'):
                if not openrouter:raise ProviderError('OpenRouter 서버 키가 설정되지 않았습니다.',candidate)
                model=QWEN_MODEL if candidate=='qwen' else os.getenv('OX_ALPHA_MODEL','stealth/ox-alpha') if candidate=='ox-alpha' else FREE_ROUTER if candidate=='openrouter-free' else candidate.removeprefix('openrouter:')
                provider_name=QWEN_PROVIDER if candidate=='qwen' else candidate
                return _openrouter(openrouter,model,provider_name,system,prompt,schema,temperature)
            raise ProviderError('지원하지 않는 AI provider입니다.',candidate,400)
        except Exception as exc:
            last_error=exc
            if requested!='auto':raise
    raise last_error or ProviderError('사용 가능한 서버 AI credential이 없습니다.',requested)
