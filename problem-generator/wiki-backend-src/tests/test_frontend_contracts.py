import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / 'wiki-src' / 'src'


class FrontendContractTest(unittest.TestCase):
    def test_assistant_click_and_enter_share_form_submit(self):
        source = (ROOT / 'components' / 'AssistantComposer.tsx').read_text(encoding='utf-8')
        self.assertIn('onSubmit={submit}', source)
        self.assertIn('submit()', source)
        self.assertIn('disabled={!value.trim()}', source)
        self.assertIn('!event.nativeEvent.isComposing', source)
        self.assertIn('<span aria-hidden="true">↑</span>', source)

    def test_wiki_client_uses_only_spring_proxy_prefix(self):
        source = (ROOT / 'lib' / 'api.ts').read_text(encoding='utf-8')
        self.assertIn("fetch('/problem-generator/wiki/api/'", source)
        self.assertNotIn("fetch('/api/", source)


if __name__ == '__main__':
    unittest.main()
