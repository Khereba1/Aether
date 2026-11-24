package com.khereba1.app.data

import android.util.Base64

/**
 * Embedded zero-dependency Python 3 bridge server + Termux/Ubuntu bootstrap helpers.
 * Uses only Python standard library so zero pip installs are required inside Ubuntu proot.
 */
object BridgeScripts {

    const val DEFAULT_PORT = 8765
    const val BRIDGE_VERSION = "1.2.0"

    const val ENABLE_EXTERNAL_APPS_CMD =
        "mkdir -p ~/.termux && grep -qxF 'allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings"

    private const val BRIDGE_PY = """#!/usr/bin/env python3
import base64
import json
import mimetypes
import os
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

BRIDGE_VERSION = "1.2.0"
PORT = int(os.environ.get("AETHER_PORT", "8765"))
WORKSPACES = Path.home() / "aether_workspaces"
WORKSPACES.mkdir(parents=True, exist_ok=True)
META_FILE = ".aether_meta.json"
SKIP_DIRS = {".git", "__pycache__", "node_modules", ".venv", "venv", ".gradle", "build"}

ACTIVE_PROCS = {}
PROCS_LOCK = threading.Lock()


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


def read_meta(p: Path) -> dict:
    mp = p / META_FILE
    if mp.exists():
        try:
            data = json.loads(mp.read_text(encoding="utf-8"))
            if isinstance(data, dict):
                return data
        except Exception:
            pass
    return {"name": p.name}


def write_meta(p: Path, meta: dict):
    (p / META_FILE).write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")


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


def list_agy_models() -> dict:
    models = []
    current = ""
    if shutil.which("agy") is None:
        return {"models": models, "current": current}
    try:
        res = subprocess.run(
            ["agy", "models"],
            capture_output=True,
            text=True,
            timeout=12,
        )
        out = (res.stdout or "") + "\n" + (res.stderr or "")
        seen = set()
        for raw in out.splitlines():
            line = re.sub(r"\x1b\[[0-9;]*m", "", raw).strip()
            if not line:
                continue
            low = line.lower()
            if low.startswith(("available", "current", "usage", "model ", "---", "===")):
                continue
            cleaned = line.lstrip(">*• ").strip()
            parts = re.split(r"\s{2,}|\t+", cleaned, maxsplit=1)
            if len(parts) == 2:
                mid, label = parts[0].strip(), parts[1].strip()
            else:
                toks = cleaned.split(None, 1)
                mid = toks[0].strip()
                label = toks[1].strip() if len(toks) > 1 else mid
            if not re.match(r"^[a-zA-Z0-9][a-zA-Z0-9._\-:/]{2,}$", mid):
                continue
            if "(current)" in label.lower():
                current = mid
                label = re.sub(r"\s*\(current\)\s*", "", label, flags=re.I).strip() or mid
            if mid not in seen:
                seen.add(mid)
                models.append({"id": mid, "label": label or mid})
    except Exception:
        pass

    if not current:
        try:
            settings_path = Path.home() / ".gemini" / "antigravity-cli" / "settings.json"
            if settings_path.exists():
                cfg = json.loads(settings_path.read_text(encoding="utf-8", errors="ignore"))
                if isinstance(cfg, dict):
                    cur = cfg.get("model") or cfg.get("defaultModel") or ""
                    if isinstance(cur, str):
                        current = cur.strip()
        except Exception:
            pass
    return {"models": models, "current": current}


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


def build_cli_cmd(
    engine: str,
    custom_bin: str,
    model: str,
    effort: str,
    prompt: str,
    conversation_id: str = "",
) -> tuple:
    binary = custom_bin.strip() if engine == "custom" else {
        "antigravity": "agy",
        "gemini": "gemini",
        "claude": "claude",
    }.get(engine, "agy")
    if not binary or shutil.which(binary) is None:
        return None, None
    if binary == "agy":
        cmd = [binary, "-p", prompt, "--output-format", "stream-json"]
        if conversation_id:
            cmd += ["--conversation", conversation_id]
        if model:
            cmd += ["--model", model]
        if effort in ("low", "medium", "high"):
            cmd += ["--effort", effort]
        return cmd, None
    if binary == "gemini":
        cmd = [binary, "-p", prompt, "--output-format", "stream-json"]
        if model:
            cmd += ["-m", model]
        return cmd, None
    if binary == "claude":
        cmd = [binary, "-p", prompt, "--output-format", "stream-json", "--verbose"]
        if conversation_id:
            cmd += ["--resume", conversation_id]
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
    ev_name = str(obj.get("event", "")).lower()
    if ev_name == "init":
        cid = obj.get("conversation_id") or ""
        if isinstance(cid, str) and cid:
            return {"type": "session", "conversation_id": cid}
        return None
    if ev_name == "step_update":
        su = obj.get("step_update") or {}
        td = su.get("text_delta")
        if isinstance(td, str) and td:
            return {"type": "delta", "text": td}
        st = str(su.get("step_type", ""))
        if st and st not in ("user_input", "agent_response") and su.get("state") == "ACTIVE":
            return {"type": "tool", "name": str(su.get("tool_name") or st)}
        return None
    if ev_name == "result":
        res = obj.get("result") or {}
        cid = res.get("conversation_id") or obj.get("conversation_id") or ""
        if res.get("error"):
            return {"type": "error", "text": str(res.get("error"))}
        resp = res.get("response")
        return {
            "type": "final",
            "text": resp if isinstance(resp, str) else "",
            "conversation_id": cid if isinstance(cid, str) else "",
        }
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
        resp = obj.get("response") or obj.get("result") or obj.get("text") or ""
        cid = obj.get("session_id") or obj.get("conversation_id") or ""
        return {
            "type": "final",
            "text": resp if isinstance(resp, str) else "",
            "conversation_id": cid if isinstance(cid, str) else "",
        }
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
                    "bridge_version": BRIDGE_VERSION,
                    "ubuntu": is_ubuntu(),
                    "python": sys.version.split()[0],
                    "clis": detect_clis(),
                    "workspaces_dir": str(WORKSPACES),
                })
                return

            if parts == ["models"]:
                self._send_json(200, list_agy_models())
                return

            if parts == ["projects"]:
                items = []
                for p in sorted(WORKSPACES.iterdir(), key=lambda x: x.stat().st_mtime, reverse=True):
                    if not p.is_dir():
                        continue
                    meta = read_meta(p)
                    display = meta.get("name", p.name)
                    files = snapshot_files(p)
                    items.append({
                        "slug": p.name,
                        "name": display,
                        "file_count": len(files),
                        "has_rules": (p / "GEMINI.md").exists(),
                        "has_session": bool(meta.get("conversation_id")),
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

            if len(parts) == 3 and parts[0] == "projects" and parts[2] == "file":
                p = project_dir(parts[1])
                qs = parse_qs(parsed.query)
                rel = (qs.get("path") or [""])[0].strip()
                if not rel:
                    self._send_json(400, {"error": "Missing path"})
                    return
                target = (p / rel).resolve()
                if not str(target).startswith(str(p.resolve()) + os.sep) or not target.is_file():
                    self._send_json(404, {"error": "File not found"})
                    return
                data = target.read_bytes()
                ctype = mimetypes.guess_type(target.name)[0] or "application/octet-stream"
                self.send_response(200)
                self.send_header("Content-Type", ctype)
                self.send_header("Content-Disposition", f'attachment; filename="{target.name}"')
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
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
                write_meta(p, {"name": name})
                if rules.strip():
                    (p / "GEMINI.md").write_text(rules, encoding="utf-8")
                self._send_json(200, {
                    "slug": p.name,
                    "name": name,
                    "file_count": 1 if rules.strip() else 0,
                    "has_rules": bool(rules.strip()),
                    "has_session": False,
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

            if len(parts) == 3 and parts[0] == "projects" and parts[2] == "reset":
                p = project_dir(parts[1])
                if p.exists():
                    meta = read_meta(p)
                    meta.pop("conversation_id", None)
                    write_meta(p, meta)
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

            if parts == ["chat", "cancel"]:
                body = self._read_json()
                slug = str(body.get("project", "")).strip()
                killed = False
                with PROCS_LOCK:
                    proc = ACTIVE_PROCS.pop(slug, None)
                    if proc and proc.poll() is None:
                        try:
                            proc.terminate()
                            killed = True
                        except Exception:
                            pass
                self._send_json(200, {"ok": True, "killed": killed})
                return

            if parts == ["chat"]:
                body = self._read_json()
                slug = str(body.get("project", "")).strip()
                prompt = str(body.get("prompt", "")).strip()
                engine = str(body.get("engine", "antigravity")).strip()
                custom_bin = str(body.get("custom_bin", "")).strip()
                model = str(body.get("model", "")).strip()
                effort = str(body.get("effort", "")).strip()
                continue_session = bool(body.get("continue_session", True))
                p = project_dir(slug)
                p.mkdir(parents=True, exist_ok=True)
                meta = read_meta(p)
                saved_cid = str(meta.get("conversation_id", "")).strip() if continue_session else ""

                cmd, stdin_text = build_cli_cmd(engine, custom_bin, model, effort, prompt, saved_cid)
                if not cmd:
                    self._send_json(400, {"error": f"CLI not installed ({engine}). Open Setup to install it."})
                    return

                before = snapshot_files(p)
                self.send_response(200)
                self.send_header("Content-Type", "application/x-ndjson; charset=utf-8")
                self.send_header("Cache-Control", "no-cache")
                self.end_headers()

                def emit(obj: dict):
                    try:
                        line = (json.dumps(obj, ensure_ascii=False) + "\n").encode("utf-8")
                        self.wfile.write(line)
                        self.wfile.flush()
                    except Exception:
                        pass

                def run_once(run_cmd: list, run_stdin):
                    proc = subprocess.Popen(
                        run_cmd,
                        cwd=str(p),
                        stdin=subprocess.PIPE if run_stdin is not None else subprocess.DEVNULL,
                        stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,
                        text=True,
                        bufsize=1,
                    )
                    with PROCS_LOCK:
                        ACTIVE_PROCS[slug] = proc
                    if run_stdin is not None and proc.stdin:
                        try:
                            proc.stdin.write(run_stdin + "\n")
                            proc.stdin.close()
                        except Exception:
                            pass

                    got_delta = False
                    final_fallback = ""
                    new_cid = ""
                    if proc.stdout:
                        for raw_line in proc.stdout:
                            ev = extract_stream_event(raw_line)
                            if not ev:
                                continue
                            etype = ev.get("type")
                            if etype == "session":
                                new_cid = ev.get("conversation_id") or new_cid
                            elif etype == "delta":
                                got_delta = True
                                emit(ev)
                            elif etype == "final":
                                if ev.get("text"):
                                    final_fallback = ev["text"]
                                if ev.get("conversation_id"):
                                    new_cid = ev["conversation_id"]
                            else:
                                emit(ev)

                    stderr_txt = proc.stderr.read().strip() if proc.stderr else ""
                    rc = proc.wait()
                    with PROCS_LOCK:
                        if ACTIVE_PROCS.get(slug) is proc:
                            ACTIVE_PROCS.pop(slug, None)
                    return rc, got_delta, final_fallback, stderr_txt, new_cid

                rc, got_delta, final_fallback, stderr_txt, new_cid = run_once(cmd, stdin_text)

                # If resuming a stale conversation_id failed cleanly with no output, retry fresh once
                if rc != 0 and not got_delta and not final_fallback and saved_cid:
                    meta.pop("conversation_id", None)
                    write_meta(p, meta)
                    fresh_cmd, fresh_stdin = build_cli_cmd(engine, custom_bin, model, effort, prompt, "")
                    if fresh_cmd:
                        rc, got_delta, final_fallback, stderr_txt, new_cid = run_once(fresh_cmd, fresh_stdin)

                if new_cid:
                    meta["conversation_id"] = new_cid
                    write_meta(p, meta)

                if not got_delta and final_fallback:
                    emit({"type": "delta", "text": final_fallback})
                if rc != 0 and not got_delta and not final_fallback:
                    emit({"type": "error", "text": stderr_txt[-600:] or f"CLI exited with code {rc}"})

                after = snapshot_files(p)
                changed = [k for k, v in after.items() if before.get(k) != v]
                emit({
                    "type": "done",
                    "changed_files": changed,
                    "exit_code": rc,
                    "has_session": bool(meta.get("conversation_id")),
                })
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
    print(f"Aether bridge v{BRIDGE_VERSION} listening on http://127.0.0.1:{PORT}", flush=True)
    server.serve_forever()
"""

    fun bridgeBase64(): String =
        Base64.encodeToString(BRIDGE_PY.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    /**
     * Full Termux bootstrap command:
     * 1) Checks proot-distro + ubuntu (installs if absent)
     * 2) Writes ~/aether/aether_bridge.py inside Ubuntu
     * 3) Restarts the bridge on 127.0.0.1:<port>
     */
    fun termuxStartBridgeCommand(port: Int): String {
        val b64 = bridgeBase64()
        val prepareUbuntu = """
mkdir -p ~/aether ~/aether_workspaces
if ! command -v python3 >/dev/null 2>&1; then
  apt-get update -y && apt-get install -y python3 curl ca-certificates
fi
echo '$b64' | base64 -d > ~/aether/aether_bridge.py
chmod +x ~/aether/aether_bridge.py
""".trimIndent().replace("\"", "\\\"")

        return """
termux-wake-lock 2>/dev/null || true
if ! command -v proot-distro >/dev/null 2>&1; then
  pkg update -y && pkg install -y proot-distro
fi
if [ ! -d "${'$'}PREFIX/var/lib/proot-distro/installed-rootfs/ubuntu" ]; then
  proot-distro install ubuntu
fi
proot-distro login ubuntu -- bash -c "$prepareUbuntu"
pkill -f aether_bridge.py 2>/dev/null || true
nohup proot-distro login ubuntu -- bash -c "AETHER_PORT=$port python3 ~/aether/aether_bridge.py" > ~/aether_bridge.log 2>&1 </dev/null &
sleep 2
""".trimIndent()
    }

    /**
     * Interactive Termux command to guide the user to install or sign in to their chosen CLI inside Ubuntu.
     */
    fun cliSetupCommand(engine: CliEngine): String = when (engine) {
        CliEngine.ANTIGRAVITY ->
            "proot-distro login ubuntu -- bash -c 'echo \"=== Aether: Antigravity CLI Setup ===\"; if ! command -v agy >/dev/null 2>&1; then echo \"Installing Antigravity CLI...\"; curl -fsSL https://antigravity.google/cli/install.sh | bash && ln -sf \"\$HOME/.local/bin/agy\" /usr/local/bin/agy; fi; agy --version; echo \"Starting sign-in (open the link in your browser)...\"; agy; exec bash'"
        CliEngine.GEMINI ->
            "proot-distro login ubuntu -- bash -c 'echo \"=== Aether: Gemini CLI Setup ===\"; if ! command -v npm >/dev/null 2>&1; then apt-get update -y && apt-get install -y nodejs npm; fi; npm install -g @google/gemini-cli; gemini --version; exec bash'"
        CliEngine.CLAUDE ->
            "proot-distro login ubuntu -- bash -c 'echo \"=== Aether: Claude Code Setup ===\"; if ! command -v npm >/dev/null 2>&1; then apt-get update -y && apt-get install -y nodejs npm; fi; npm install -g @anthropic-ai/claude-code; claude --version; exec bash'"
        CliEngine.CUSTOM ->
            "proot-distro login ubuntu"
    }
}
