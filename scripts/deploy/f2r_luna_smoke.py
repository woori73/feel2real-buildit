#!/usr/bin/env python3
"""One small capability check only when an operator explicitly selects Luna.
Uses a synthetic image, no user records; never prints credentials or response bodies.
"""
import json
from pathlib import Path
import sys
import urllib.error
import urllib.request


def check(key_path):
    key = Path(key_path).read_text().strip()
    body = {
        'model': 'gpt-6-luna', 'instructions': 'Read the synthetic image and return its printed text only.',
        'input': [{'role': 'user', 'content': [
            {'type': 'input_text', 'text': 'Read the word in this image. Return only LUNA_OK.'},
            {'type': 'input_image', 'image_url': 'data:image/png;base64,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'}]}],
        'tools': [{'type': 'web_search'}], 'reasoning': {'effort': 'low'},
        'max_output_tokens': 1024, 'store': False,
    }
    request = urllib.request.Request('https://api.openai.com/v1/responses',
        data=json.dumps(body).encode(), method='POST',
        headers={'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            result = json.loads(response.read())
        text = ''.join(part.get('text', '') for item in result.get('output', [])
            if item.get('type') == 'message' and item.get('role') == 'assistant'
            for part in item.get('content', []) if part.get('type') == 'output_text')
        if result.get('status') != 'completed' or text.strip() != 'LUNA_OK':
            raise ValueError('unexpected response')
        usage = result.get('usage') or {}
        print('Luna capability smoke PASS model=gpt-6-luna input_image=true web_search_tool=accepted',
              'inputTokens=' + str(usage.get('input_tokens', 0)),
              'outputTokens=' + str(usage.get('output_tokens', 0)))
    except urllib.error.HTTPError as error:
        raise SystemExit('Luna capability smoke failed HTTP ' + str(error.code)) from None
    except (OSError, ValueError, TypeError, AttributeError):
        raise SystemExit('Luna capability smoke failed (controlled error; no response body logged)') from None


if __name__ == '__main__':
    check(sys.argv[1])
