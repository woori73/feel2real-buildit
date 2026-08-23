import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / 'wiki-src' / 'src'


class FrontendContractTest(unittest.TestCase):
    def test_assistant_click_and_enter_share_form_submit(self):
        source = (ROOT / 'components' / 'AssistantComposer.tsx').read_text(encoding='utf-8')
        self.assertIn('onSubmit={submit}', source)
        self.assertIn('requestSubmit()', source)
        self.assertIn('disabled={value.trim().length === 0}', source)
        self.assertIn('!event.nativeEvent.isComposing', source)
        self.assertIn('event.keyCode !== 229', source)
        self.assertIn('onCompositionEnd=', source)
        self.assertIn('<svg aria-hidden="true"', source)

    def test_assistant_and_primary_actions_have_explicit_layer_and_contrast(self):
        composer_css = (ROOT / 'styles' / 'wiki-ink.css').read_text(encoding='utf-8')
        workspace_css = (ROOT / 'styles' / 'workspace.css').read_text(encoding='utf-8')
        self.assertIn('z-index:1000;isolation:isolate;pointer-events:auto', composer_css)
        self.assertIn('button.primary-action{background:#17211b;color:#fff', workspace_css)

    def test_generator_uses_api_exam_values_and_selected_twin_id(self):
        source = (ROOT / 'components' / 'MiddleSchoolExamGenerator.tsx').read_text(encoding='utf-8')
        self.assertIn("const [exam,setExam]=useState('')", source)
        self.assertIn("setExam(exams[0]?.exam || '')", source)
        self.assertIn('twin_id:profile?.id', source)

    def test_wiki_client_uses_only_spring_proxy_prefix(self):
        source = (ROOT / 'lib' / 'api.ts').read_text(encoding='utf-8')
        self.assertIn("fetch('/problem-generator/wiki/api/'", source)
        self.assertNotIn("fetch('/api/", source)


if __name__ == '__main__':
    unittest.main()
