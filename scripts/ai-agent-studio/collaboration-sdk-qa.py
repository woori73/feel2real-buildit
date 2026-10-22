#!/usr/bin/env python3
"""Two official MCP SDK clients, actual OAuth tokens, isolated synthetic files only."""
import asyncio
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile

spec = importlib.util.spec_from_file_location("transfer", Path(__file__).with_name("collaboration-transfer.py"))
transfer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(transfer)


async def main():
    fixture = json.loads(Path("/tmp/f2r-collab-browser.json").read_text())
    url = "http://127.0.0.1:19088/mcp"
    async with transfer.connect(url, fixture["gpt"]) as gpt:
        async with transfer.connect(url, fixture["claude"]) as claude:
            a = transfer.metadata(await transfer.invoke(gpt, "collaboration_read", {"fileId": fixture["document"]}))
            b = transfer.metadata(await transfer.invoke(claude, "collaboration_read", {"fileId": fixture["document"]}))
            assert a["text"] == b["text"] and a["metadata"]["sha256"] == b["metadata"]["sha256"]
            with tempfile.TemporaryDirectory(prefix="f2r-sdk-qa-") as directory:
                source = Path(directory) / "synthetic-sdk-handoff.md"
                source.write_text("# SDK 합성 파일\n공식 MCP SDK 파일 전송 검증. 실제 AI 앱 실행·원본 EP01 아님.")
                file = await transfer.upload_file(gpt, fixture["sdktask"], source)
                read = transfer.metadata(await transfer.invoke(claude, "collaboration_read", {"fileId": file["id"]}))
                assert read["metadata"]["status"] == "DRAFT"
                assert read["text"] == source.read_text()
                assert read["metadata"]["sha256"] == hashlib.sha256(source.read_bytes()).hexdigest()
    report = dict(sdk="official Python mcp 2.1.1", oauthClients=2,
                  sameOriginalTextAndSha=True, localFileToDraftToSecondClient=True,
                  actualChatGptClaudeProductLogin=False, paidModelApiCalls=0)
    Path("docs/collaboration/sdk-results.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report))


if __name__ == "__main__":
    asyncio.run(main())
