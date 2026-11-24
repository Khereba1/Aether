package com.khereba1.app.data

import android.util.Base64

/**
 * Embedded zero-dependency Python 3 bridge server + Termux/Ubuntu bootstrap helpers.
 * Uses only Python standard library so zero pip installs are required inside Ubuntu proot.
 */
object BridgeScripts {

    const val DEFAULT_PORT = 8765

    const val ENABLE_EXTERNAL_APPS_CMD =
        "mkdir -p ~/.termux && grep -qxF 'allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings"

    private const val BRIDGE_PY = """#!/usr/bin/env python3
import base64
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlparse

PORT = int(os.environ.get("AETHER_PORT", "8765"))
WORKSPACES = Path.home() / "aether_workspaces"
WORKSPACES.mkdir(parents=True, exist_ok=True)
META_FILE = ".aether_meta.json"
SKIP_DIRS = {".git", "__pycache__", "node_modules", ".venv", "venv", ".gradle", "build"}


def safe_slug(name: str) -> str:
    s = re.sub(r"[^a-zA-Z0-9_\-\u0600-\u06FF]+", "_", name.strip()).strip("_")
    if not s:
        s = f"project_{int(time.time())}"
    return s[:48]


def project_dir(slug: str) -> Path:
    if not slug or "/" in slug or "\\" in slug or slug in (".", ".."):
        raise ValueError("Invalid project slug")
    p = (WORKSPACES / slug).resolve()
    if p.parent != WORKSPACES.resolve():
        raise ValueError("Path escapes workspace")
    return p


def detect_clis() -> dict:
    found = {}
    for cmd in ("agy", "gemini", "claude"):
        found[cmd] = shutil.which(cmd) is not None
    return found


def is_ubuntu() -> bool:
    try:
        txt = Path("/etc/os-release").read_text(encoding="utf-8", errors="ignore").lower()
        return "ubuntu" in txt or "debian" in txt
    except Exception:
        return False


def snapshot_files(root: Path) -> dict:
    snap = {}
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if fn == META_FILE:
                continue
            full = Path(dirpath) / fn
            try:
                st = full.stat()
                rel = str(full.relative_to(root))
                snap[rel] = (st.st_mtime_ns, st.st_size)
            except OSError:
                pass
    return snap


def build_cli_cmd(engine: str, custom_bin: str, model: str, prompt: str) -> tuple:
    binary = custom_bin.strip() if engine == "custom" else {
        "antigravity": "agy",
        "gemini": "gemini",
        "claude": "claude",
    }.get(engine, "agy")
    if not binary or shutil.which(binary) is None:
        return None, None
    if binary == "agy":
        cmd = [binary, "--output-format", "stream-json"]
        if model:
            cmd += ["--model", model]
        return cmd, prompt
    if binary == "gemini":
        cmd = [binary, "-p", prompt, "--output-format", "stream-json"]
        if model:
            cmd += ["-m", model]
        return cmd, None
    if binary == "claude":
        cmd = [binary, "-p", prompt, "--output-format", "stream-json", "--verbose"]
        if model:
            cmd += ["--model", model]
        return cmd, None
    cmd = [binary, prompt]
    return cmd, None


def extract_stream_event(line: str):
    s = line.strip()
    if not s:
        return None
    if not s.startswith("{"):
        return {"type": "delta", "text": s + "\n"}
    try:
        obj = json.loads(s)
    except Exception:
        return {"type": "delta", "text": s + "\n"}
    etype = str(obj.get("type", "")).lower()
    if etype in ("content_block_delta", "message_delta", "delta", "text", "assistant", "message"):
        for key in ("delta", "text", "content", "response", "message"):
            val = obj.get(key)
            if isinstance(val, str) and val:
                return {"type": "delta", "text": val}
            if isinstance(val, dict):
                t = val.get("text") or val.get("content")
                if isinstance(t, str) and t:
                    return {"type": "delta", "text": t}
    if etype in ("tool_use", "tool_call", "tool"):
        tname = obj.get("name") or obj.get("tool") or obj.get("tool_name") or "tool"
        return {"type": "tool", "name": str(tname)}
    if etype in ("result", "final", "done"):
        resp = obj.get("response") or obj.get("result") or obj.get("text")
        if isinstance(resp, str) and resp:
            return {"type": "final", "text": resp}
    if etype == "error":
        err = obj.get("error") or obj.get("message") or str(obj)
        return {"type": "error", "text": str(err)}
    return None


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass

    def _send_json(self, code: int, payload: dict):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _read_json(self) -> dict:
        length = int(self.headers.get("Content-Length", "0") or 0)
        raw = self.rfile.read(length) if length > 0 else b"{}"
        return json.loads(raw.decode("utf-8"))

    def do_GET(self):
        parsed = urlparse(self.path)
        parts = [unquote(p) for p in parsed.path.strip("/").split("/") if p]
        try:
            if parts == ["health"]:
                self._send_json(200, {
                    "ok": True,
                    "ubuntu": is_ubuntu(),
                    "python": sys.version.split()[0],
                    "clis": detect_clis(),
                    "workspaces_dir": str(WORKSPACES),
                })
                return

            if parts == ["projects"]:
                items = []
                for p in sorted(WORKSPACES.iterdir(), key=lambda x: x.stat().st_mtime, reverse=True):
                    if not p.is_dir():
                        continue
                    meta_path = p / META_FILE
                    display = p.name
                    if meta_path.exists():
                        try:
                            display = json.loads(meta_path.read_text(encoding="utf-8")).get("name", p.name)
                        except Exception:
                            pass
                    files = snapshot_files(p)
                    items.append({
                        "slug": p.name,
                        "name": display,
                        "file_count": len(files),
                        "has_rules": (p / "GEMINI.md").exists(),
                        "updated_at": int(p.stat().st_mtime * 1000),
                    })
                self._send_json(200, {"projects": items})
                return

            if len(parts) == 3 and parts[0] == "projects" and parts[2] == "rules":
                p = project_dir(parts[1])
                rf = p / "GEMINI.md"
                text = rf.read_text(encoding="utf-8") if rf.exists() else ""
                self._send_json(200, {"rules": text})
                return

            if len(parts) == 3 and parts[0] == "projects" and parts[2] == "files":
                p = project_dir(parts[1])
                files = []
                if p.exists():
                    for child in sorted(p.iterdir(), key=lambda x: (not x.is_dir(), x.name.lower())):
                        if child.name == META_FILE or child.name in SKIP_DIRS:
                            continue
                        st = child.stat()
                        files.append({"name": child.name, "size": st.st_size, "is_dir": child.is_dir()})
                self._send_json(200, {"files": files})
                return

            if len(parts) == 3 and parts[0] == "projects" and parts[2] == "zip":
                p = project_dir(parts[1])
                if not p.exists():
                    self._send_json(404, {"error": "Project not found"})
                    return
                with tempfile.NamedTemporaryFile(suffix=".zip", delete=False) as tf:
                    tmp_path = Path(tf.name)
                try:
                    with zipfile.ZipFile(tmp_path, "w", zipfile.ZIP_DEFLATED) as zf:
                        for dirpath, dirnames, filenames in os.walk(p):
                            dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
                            for fn in filenames:
                                if fn == META_FILE:
                                    continue
                                full = Path(dirpath) / fn
                                zf.write(full, str(full.relative_to(p)))
                    data = tmp_path.read_bytes()
                    self.send_response(200)
                    self.send_header("Content-Type", "application/zip")
                    self.send_header("Content-Disposition", f'attachment; filename="{p.name}.zip"')
                    self.send_header("Content-Length", str(len(data)))
                    self.end_headers()
                    self.wfile.write(data)
                finally:
                    tmp_path.unlink(missing_ok=True)
                return

            self._send_json(404, {"error": "Not found"})
        except Exception as e:
            self._send_json(500, {"error": str(e)})

    def do_POST(self):
        parsed = urlparse(self.path)
        parts = [unquote(p) for p in parsed.path.strip("/").split("/") if p]
        try:
            if parts == ["projects"]:
                body = self._read_json()
                name = str(body.get("name", "")).strip() or "New Project"
                rules = str(body.get("rules", ""))
                slug = safe_slug(name)
                p = project_dir(slug)
                idx = 2
                while p.exists():
                    p = project_dir(f"{slug}_{idx}")
                    idx += 1
                p.mkdir(parents=True, exist_ok=True)
                (p / META_FILE).write_text(json.dumps({"name": name}, ensure_ascii=False), encoding="utf-8")
                if rules.strip():
                    (p / "GEMINI.md").write_text(rules, encoding="utf-8")
                self._send_json(200, {
                    "slug": p.name,
                    "name": name,
                    "file_count": 1 if rules.strip() else 0,
                    "has_rules": bool(rules.strip()),
                    "updated_at": int(time.time() * 1000),
                })
                return

            if len(parts) == 3 and parts[0] == "projects" and parts[2] == "rules":
                body = self._read_json()
                p = project_dir(parts[1])
                p.mkdir(parents=True, exist_ok=True)
                (p / "GEMINI.md").write_text(str(body.get("rules", "")), encoding="utf-8")
                self._send_json(200, {"ok": True})
                return

            if len(parts) == 3 and parts[0] == "projects" and parts[2] == "upload":
                body = self._read_json()
                p = project_dir(parts[1])
                p.mkdir(parents=True, exist_ok=True)
                fname = os.path.basename(str(body.get("filename", "upload.bin"))).strip() or "upload.bin"
                raw = base64.b64decode(str(body.get("data_base64", "")))
                (p / fname).write_bytes(raw)
                self._send_json(200, {"ok": True, "filename": fname, "size": len(raw)})
                return

            if parts == ["chat"]:
                body = self._read_json()
                slug = str(body.get("project", "")).strip()
                prompt = str(body.get("prompt", "")).strip()
                engine = str(body.get("engine", "antigravity")).strip()
                custom_bin = str(body.get("custom_bin", "")).strip()
                model = str(body.get("model", "")).strip()
                p = project_dir(slug)
                p.mkdir(parents=True, exist_ok=True)

                cmd, stdin_text = build_cli_cmd(engine, custom_bin, model, prompt)
                if not cmd:
                    self._send_json(400, {"error": f"CLI not installed ({engine}). Open Setup to install it."})
                    return

                before = snapshot_files(p)
                self.send_response(200)
                self.send_header("Content-Type", "application/x-ndjson; charset=utf-8")
                self.send_header("Cache-Control", "no-cache")
                self.end_headers()

                def emit(obj: dict):
                    line = (json.dumps(obj, ensure_ascii=False) + "\n").encode("utf-8")
                    self.wfile.write(line)
                    self.wfile.flush()

                proc = subprocess.Popen(
                    cmd,
                    cwd=str(p),
                    stdin=subprocess.PIPE if stdin_text is not None else subprocess.DEVNULL,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    bufsize=1,
                )
                if stdin_text is not None and proc.stdin:
                    try:
                        proc.stdin.write(stdin_text + "\n")
                        proc.stdin.close()
                    except Exception:
                        pass

                got_delta = False
                final_fallback = ""
                if proc.stdout:
                    for raw_line in proc.stdout:
                        ev = extract_stream_event(raw_line)
                        if not ev:
                            continue
                        if ev["type"] == "delta":
                            got_delta = True
                            emit(ev)
                        elif ev["type"] == "final":
                            final_fallback = ev["text"]
                        else:
                            emit(ev)

                stderr_txt = proc.stderr.read().strip() if proc.stderr else ""
                rc = proc.wait()
                if not got_delta and final_fallback:
                    emit({"type": "delta", "text": final_fallback})
                if rc != 0 and not got_delta and not final_fallback:
                    emit({"type": "error", "text": stderr_txt[-600:] or f"CLI exited with code {rc}"})

                after = snapshot_files(p)
                changed = [k for k, v in after.items() if before.get(k) != v]
                emit({"type": "done", "changed_files": changed, "exit_code": rc})
                return

            self._send_json(404, {"error": "Not found"})
        except Exception as e:
            self._send_json(500, {"error": str(e)})

    def do_DELETE(self):
        parsed = urlparse(self.path)
        parts = [unquote(p) for p in parsed.path.strip("/").split("/") if p]
        try:
            if len(parts) == 2 and parts[0] == "projects":
                p = project_dir(parts[1])
                if p.exists():
                    shutil.rmtree(p)
                self._send_json(200, {"ok": True})
                return
            self._send_json(404, {"error": "Not found"})
        except Exception as e:
            self._send_json(500, {"error": str(e)})


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"Aether bridge listening on http://127.0.0.1:{PORT}", flush=True)
    server.serve_forever()
"""

    fun bridgeBase64(): String =
        Base64.encodeToString(BRIDGE_PY.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    /**
     * Full Termux bootstrap command:
     * 1) Checks proot-distro + ubuntu (installs if absent)
     * 2) Writes ~/aether/aether_bridge.py inside Ubuntu
     * 3) Starts the bridge on 127.0.0.1:<port> if not already listening
     */
    fun termuxStartBridgeCommand(port: Int): String {
        val b64 = bridgeBase64()
        val innerUbuntu = """
mkdir -p ~/aether ~/aether_workspaces
if ! command -v python3 >/dev/null 2>&1; then
  apt-get update -y && apt-get install -y python3 curl ca-certificates
fi
echo '$b64' | base64 -d > ~/aether/aether_bridge.py
chmod +x ~/aether/aether_bridge.py
if ! pgrep -f 'aether_bridge.py' >/dev/null 2>&1; then
  AETHER_PORT=$port nohup python3 ~/aether/aether_bridge.py > ~/aether/bridge.log 2>&1 &
  sleep 1
fi
""".trimIndent().replace("\"", "\\\"")

        return """
termux-wake-lock 2>/dev/null || true
if ! command -v proot-distro >/dev/null 2>&1; then
  pkg update -y && pkg install -y proot-distro
fi
if [ ! -d "${'$'}PREFIX/var/lib/proot-distro/installed-rootfs/ubuntu" ]; then
  proot-distro install ubuntu
fi
proot-distro login ubuntu -- bash -c "$innerUbuntu"
""".trimIndent()
    }

    /**
     * Interactive Termux command to guide the user to install or sign in to their chosen CLI inside Ubuntu.
     */
    fun cliSetupCommand(engine: CliEngine): String = when (engine) {
        CliEngine.ANTIGRAVITY ->
            "proot-distro login ubuntu -- bash -c 'echo \"=== Aether: Antigravity CLI Setup ===\"; if ! command -v agy >/dev/null 2>&1; then echo \"Installing Antigravity CLI...\"; curl -fsSL https://antigravity.google/install.sh | bash; fi; agy --version; echo \"Starting sign-in (open the link in your browser)...\"; agy; exec bash'"
        CliEngine.GEMINI ->
            "proot-distro login ubuntu -- bash -c 'echo \"=== Aether: Gemini CLI Setup ===\"; if ! command -v npm >/dev/null 2>&1; then apt-get update -y && apt-get install -y nodejs npm; fi; npm install -g @google/gemini-cli; gemini --version; exec bash'"
        CliEngine.CLAUDE ->
            "proot-distro login ubuntu -- bash -c 'echo \"=== Aether: Claude Code Setup ===\"; if ! command -v npm >/dev/null 2>&1; then apt-get update -y && apt-get install -y nodejs npm; fi; npm install -g @anthropic-ai/claude-code; claude --version; exec bash'"
        CliEngine.CUSTOM ->
            "proot-distro login ubuntu"
    }
}
