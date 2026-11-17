"""Nebula Keeper: mantiene conectadas las bocinas de OwnTone.

La app DriveNebula le dice qué bocinas quieres prendidas (POST /keep).
Si una se cae mientras suena música, cada minuto se intenta reconectar;
si se cayeron todas y la música se detuvo por eso, la vuelve a poner.
Solo trabaja dentro de la casa (OwnTone en localhost).
"""
import json, os, threading, time, urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

OWNTONE = os.environ.get("OWNTONE", "http://127.0.0.1:3689")
PORT = int(os.environ.get("PORT", "3690"))
RETRY = int(os.environ.get("INTERVAL", "60"))
DATA = "/data/keep.json"
lock = threading.Lock()
keep = {}          # id -> volumen
status = {"ultimo": "", "reconexiones": 0}

def load():
    global keep
    try:
        with open(DATA) as f: keep = {str(k): int(v) for k, v in json.load(f).items()}
    except Exception: keep = {}

def save():
    try:
        os.makedirs("/data", exist_ok=True)
        with open(DATA, "w") as f: json.dump(keep, f)
    except Exception as e: print("no pude guardar:", e, flush=True)

def api(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(OWNTONE + path, data=data, method=method, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as r:
        t = r.read()
        return json.loads(t) if t.strip().startswith(b"{") else None

def log(msg):
    status["ultimo"] = time.strftime("%H:%M:%S ") + msg
    print(status["ultimo"], flush=True)

def loop():
    last_state, last_sel = None, set()
    resume_until, next_try = 0.0, 0.0
    while True:
        try:
            st = (api("GET", "/api/player") or {}).get("state", "stop")
            outs = {o["id"]: o for o in (api("GET", "/api/outputs") or {}).get("outputs", [])}
            sel = {i for i, o in outs.items() if o.get("selected")}
            with lock: k = dict(keep)
            dropped = {i for i in k if i in last_sel and i not in sel}
            # se cayeron bocinas y por eso se detuvo la música → hay que volver a ponerla
            if last_state == "play" and st != "play" and dropped:
                resume_until = time.time() + 30 * 60
                log("se detuvo porque se cayeron bocinas; intentaré reconectar")
            missing = [i for i in k if i in outs and i not in sel]
            now = time.time()
            if missing and (st == "play" or now < resume_until) and now >= next_try:
                next_try = now + RETRY
                for i in missing:
                    try:
                        api("PUT", f"/api/outputs/{i}", {"selected": True, "volume": k[i]})
                        status["reconexiones"] += 1
                        log(f"reconectando {outs[i].get('name')}")
                    except Exception as e: log(f"{outs[i].get('name')} no respondió ({e})")
                if st != "play" and now < resume_until:
                    time.sleep(3)
                    try:
                        if any(o.get("selected") for o in (api("GET", "/api/outputs") or {}).get("outputs", [])):
                            api("PUT", "/api/player/play"); log("música reanudada"); resume_until = 0
                    except Exception as e: log(f"no pude reanudar ({e})")
            if st == "play": resume_until = 0
            last_state, last_sel = st, sel
        except Exception as e:
            log(f"OwnTone no respondió ({e})")
        time.sleep(10)

class H(BaseHTTPRequestHandler):
    def _send(self, code, obj):
        b = json.dumps(obj).encode()
        self.send_response(code); self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def do_GET(self):
        with lock: self._send(200, {"keep": keep, "intervalo": RETRY, **status})
    def do_POST(self):
        if self.path != "/keep": return self._send(404, {})
        try:
            n = int(self.headers.get("Content-Length", "0"))
            d = json.loads(self.rfile.read(n) or b"{}")
            with lock:
                keep.clear(); keep.update({str(k): int(v) for k, v in d.get("keep", {}).items()}); save()
            self._send(200, {"ok": True})
        except Exception as e: self._send(400, {"error": str(e)})
    def log_message(self, *a): pass

if __name__ == "__main__":
    load()
    threading.Thread(target=loop, daemon=True).start()
    print(f"Nebula Keeper en el puerto {PORT}, reintento cada {RETRY}s", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), H).serve_forever()
