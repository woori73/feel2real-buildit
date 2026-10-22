#!/usr/bin/env python3
"""Official MCP SDK local-file transfer. No model API and no consumer session access.
OAuth access tokens must be obtained through the user's approved MCP authorization.
"""
import argparse
import asyncio
import base64
from contextlib import asynccontextmanager
import hashlib
import json
from pathlib import Path
import stat
from urllib.parse import urlparse

import httpx2
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client

MAX_BYTES = 32 * 1024 * 1024


@asynccontextmanager
async def connect(url, token):
    parsed = urlparse(url)
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("Use a clean MCP endpoint URL")
    if parsed.scheme != "https" and not (
        parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "localhost"}
    ):
        raise ValueError("HTTPS is required except for isolated loopback tests")
    async with httpx2.AsyncClient(
        headers={"Authorization": "Bearer " + token}, timeout=60
    ) as http:
        async with streamable_http_client(url, http_client=http) as (read, write):
            async with ClientSession(read, write) as session:
                await session.initialize()
                yield session


async def invoke(session, name, arguments):
    result = await session.call_tool(name, arguments)
    if getattr(result, "isError", False):
        raise RuntimeError("MCP request denied; check connection scope, project and task state")
    return result


def metadata(result):
    return json.loads(result.content[0].text)


async def upload_file(session, task_id, path, parent_id=None, paced=True):
    path = Path(path)
    if path.stat().st_size < 1 or path.stat().st_size > MAX_BYTES:
        raise ValueError("File must be 1 byte through 32 MiB")
    data = path.read_bytes()
    args = dict(taskId=task_id, filename=path.name, size=len(data), sha256=hashlib.sha256(data).hexdigest())
    if parent_id:
        args["parentId"] = parent_id
    start = metadata(await invoke(session, "collaboration_begin_upload", args))
    for index, offset in enumerate(range(0, len(data), 32768)):
        if paced:
            await asyncio.sleep(1.1)  # Stay below the existing per-user 60/min MCP limit.
        await invoke(session, "collaboration_upload_chunk", dict(
            uploadId=start["uploadId"], index=index,
            base64=base64.b64encode(data[offset:offset + 32768]).decode("ascii")))
    if paced:
        await asyncio.sleep(1.1)
    file = metadata(await invoke(session, "collaboration_commit_upload", {"uploadId": start["uploadId"]}))
    if file["sha256"] != args["sha256"] or file["status"] != "DRAFT":
        raise RuntimeError("Draft upload integrity check failed")
    return file


async def main(args):
    token_path = Path(args.token_file)
    if not stat.S_ISREG(token_path.stat().st_mode) or token_path.stat().st_mode & 0o077:
        raise ValueError("OAuth token file must be a private regular file (chmod 600)")
    async with connect(args.endpoint, token_path.read_text().strip()) as session:
        file = await upload_file(session, args.task, args.file, args.parent)
        print(json.dumps({"id": file["id"], "sha256": file["sha256"], "status": file["status"]}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--endpoint", required=True)
    parser.add_argument("--token-file", required=True, help="Private file containing an approved OAuth access token, never an API key")
    parser.add_argument("--task", required=True)
    parser.add_argument("--parent")
    parser.add_argument("file")
    try:
        asyncio.run(main(parser.parse_args()))
    except Exception:
        raise SystemExit("Transfer failed. Check file size, OAuth expiry, binding and task; no token is printed.")
