"""Nebula Keeper: el ayudante del NAS para las bocinas de OwnTone.

- Mantiene conectadas las bocinas (POST /keep): si una se cae mientras suena música, cada minuto la reconecta;
  si se cayeron todas y la música se detuvo por eso, la vuelve a poner.
- Guarda la configuración compartida de las apps (GET/PUT /config): apodos de bocinas, perfiles de sonido y alarmas.
  Así cualquier teléfono que abra Control Nebula ve lo mismo.
- Hace sonar las alarmas desde el NAS (aunque el teléfono esté apagado o fuera de casa).
- Temporizador para dormir (GET/POST/DELETE /timer): baja el volumen poco a poco y pausa a la hora elegida,
  o al terminar la canción.
Solo trabaja dentro de la casa (OwnTone en localhost).
"""
import json, os, threading, time, urllib.parse, urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

OWNTONE = os.environ.get("OWNTONE", "http://127.0.0.1:3689")
PORT = int(os.environ.get("PORT", "3690"))
RETRY = int(os.environ.get("INTERVAL", "60"))
DATA = "/data/keep.json"
CONFIG = "/data/config.json"
lock = threading.RLock()
keep = {}          # id -> volumen
status = {"ultimo": "", "reconexiones": 0}
# apodos, perfiles y alarmas; tz = minutos respecto a UTC del teléfono (México: -360)
config = {"rev": 0, "nicks": {}, "profiles": {}, "alarms": [], "tz": -360, "fired": {}}
timer = {}         # {"at": epoch s, "kind": "time"|"song", "fade": bool, "item": id}

def load():
    global keep, config
    try:
        with open(DATA) as f: keep = {str(k): int(v) for k, v in json.load(f).items()}
    except Exception: keep = {}
    try:
        with open(CONFIG) as f: config.update(json.load(f))
    except Exception: pass

def write(path, obj):
    try:
        os.makedirs("/data", exist_ok=True)
        tmp = path + ".tmp"
        with open(tmp, "w") as f: json.dump(obj, f, ensure_ascii=False)
        os.replace(tmp, path)
    except Exception as e: print("no pude guardar:", e, flush=True)

def save(): write(DATA, keep)
def save_config(): write(CONFIG, config)

def api(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(OWNTONE + path, data=data, method=method, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as r:
        t = r.read()
        return json.loads(t) if t.strip().startswith(b"{") else None

def log(msg):
    status["ultimo"] = time.strftime("%H:%M:%S ") + msg
    print(status["ultimo"], flush=True)

# ───────── reconexión ─────────

def keeper_tick(state):
    st = (api("GET", "/api/player") or {}).get("state", "stop")
    outs = {o["id"]: o for o in (api("GET", "/api/outputs") or {}).get("outputs", [])}
    sel = {i for i, o in outs.items() if o.get("selected")}
    with lock: k = dict(keep)
    dropped = {i for i in k if i in state["last_sel"] and i not in sel}
    if state["last_state"] == "play" and st != "play" and dropped and not state.get("timer_stop"):
        state["resume_until"] = time.time() + 30 * 60
        log("se detuvo porque se cayeron bocinas; intentaré reconectar")
    missing = [i for i in k if i in outs and i not in sel]
    now = time.time()
    if missing and (st == "play" or now < state["resume_until"]) and now >= state["next_try"]:
        state["next_try"] = now + RETRY
        for i in missing:
            try:
                api("PUT", f"/api/outputs/{i}", {"selected": True, "volume": k[i]})
                status["reconexiones"] += 1
                log(f"reconectando {outs[i].get('name')}")
            except Exception as e: log(f"{outs[i].get('name')} no respondió ({e})")
        if st != "play" and now < state["resume_until"]:
            time.sleep(3)
            try:
                if any(o.get("selected") for o in (api("GET", "/api/outputs") or {}).get("outputs", [])):
                    api("PUT", "/api/player/play"); log("música reanudada"); state["resume_until"] = 0
            except Exception as e: log(f"no pude reanudar ({e})")
    if st == "play": state["resume_until"] = 0; state["timer_stop"] = False
    state["last_state"], state["last_sel"] = st, sel

# ───────── alarmas ─────────

def local_now():
    with lock: tz = int(config.get("tz", -360))
    return time.gmtime(time.time() + tz * 60)

def fire_alarm(a):
    log(f"alarma de las {a.get('h')}:{int(a.get('m', 0)):02d}")
    with lock: prof = config.get("profiles", {}).get(a.get("p") or "")
    if prof:
        outs = (api("GET", "/api/outputs") or {}).get("outputs", [])
        sel = {}
        for o in outs:
            oid = str(o.get("id"))
            if o.get("name", "").endswith("+") or o.get("type") == "ALSA": continue
            sv = prof.get(oid) or {"s": False, "v": o.get("volume", 30)}
            body = {"selected": bool(sv.get("s"))}
            if sv.get("s"): body["volume"] = int(sv.get("v", 30)); sel[oid] = int(sv.get("v", 30))
            try: api("PUT", f"/api/outputs/{oid}", body)
            except Exception as e: log(f"{o.get('name')} no respondió ({e})")
        with lock: keep.clear(); keep.update(sel); save()
    v = int(a.get("v", -1))
    if v >= 0: api("PUT", f"/api/player/volume?volume={v}")
    api("PUT", f"/api/player/shuffle?state={'true' if a.get('sh', True) else 'false'}")
    src = str(a.get("s", ""))
    if a.get("k") == "folder":
        expr = 'media_kind is music and path starts with "%s"' % src.replace('"', "")
        what = "expression=" + urllib.parse.quote(expr)
    else:
        what = "uris=" + urllib.parse.quote(f"library:playlist:{src}")
    api("POST", f"/api/queue/items/add?clear=true&playback=start&{what}")

def alarms_tick():
    t = local_now()
    key = time.strftime("%Y-%m-%d %H:%M", t)
    wday = (t.tm_wday + 1) % 7 + 1          # Calendar de Android: 1 = domingo … 7 = sábado
    with lock: alarms = list(config.get("alarms", []))
    changed = False
    for a in alarms:
        if not a.get("on", True): continue
        if int(a.get("h", -1)) != t.tm_hour or int(a.get("m", -1)) != t.tm_min: continue
        days = a.get("d") or []
        if days and wday not in days: continue
        aid = str(a.get("id"))
        with lock:
            if config["fired"].get(aid) == key: continue
            config["fired"][aid] = key
        try: fire_alarm(a)
        except Exception as e: log(f"la alarma no pudo sonar ({e})")
        if not days:                         # era de una sola vez: se apaga
            with lock:
                for b in config["alarms"]:
                    if str(b.get("id")) == aid: b["on"] = False
                config["rev"] = int(config.get("rev", 0)) + 1
        changed = True
    if changed:
        with lock: save_config()

# ───────── temporizador ─────────

def timer_tick(state):
    with lock: tm = dict(timer)
    if not tm: return
    pl = api("GET", "/api/player") or {}
    if pl.get("state") != "play":
        if tm.get("kind") == "song": return   # espera a que vuelva a sonar
    due = False
    if tm.get("kind") == "song":
        due = pl.get("item_id") != tm.get("item") or (pl.get("item_length_ms", 0) - pl.get("item_progress_ms", 0)) < 1500
    else:
        due = time.time() >= tm.get("at", 0)
    if not due: return
    with lock: timer.clear()
    log("temporizador: apagando la música")
    state["timer_stop"] = True
    try:
        vol = int(pl.get("volume", 30))
        if tm.get("fade", True) and tm.get("kind") != "song":
            for i in range(10, 0, -1):          # baja el volumen en ~20 s
                api("PUT", f"/api/player/volume?volume={max(1, vol * i // 10)}"); time.sleep(2)
        api("PUT", "/api/player/pause")
        time.sleep(1)
        api("PUT", f"/api/player/volume?volume={vol}")   # para la próxima vez
    except Exception as e: log(f"no pude apagar ({e})")

def loop():
    state = {"last_state": None, "last_sel": set(), "resume_until": 0.0, "next_try": 0.0, "timer_stop": False}
    n = 0
    while True:
        try: timer_tick(state)
        except Exception as e: log(f"temporizador: OwnTone no respondió ({e})")
        try: alarms_tick()
        except Exception as e: log(f"alarmas: {e}")
        if n % 2 == 0:
            try: keeper_tick(state)
            except Exception as e: log(f"OwnTone no respondió ({e})")
        n += 1
        time.sleep(5)

class H(BaseHTTPRequestHandler):
    def _send(self, code, obj):
        b = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(code); self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def _body(self):
        n = int(self.headers.get("Content-Length", "0"))
        return json.loads(self.rfile.read(n) or b"{}")
    def _timer_info(self):
        with lock: tm = dict(timer)
        if not tm: return {"activo": False}
        out = {"activo": True, "kind": tm.get("kind"), "fade": tm.get("fade", True)}
        if tm.get("kind") != "song": out["restante_s"] = max(0, int(tm.get("at", 0) - time.time())); out["at"] = int(tm.get("at", 0))
        return out
    def do_GET(self):
        if self.path.startswith("/config"):
            with lock: c = {k: v for k, v in config.items() if k != "fired"}
            return self._send(200, c)
        if self.path.startswith("/timer"): return self._send(200, self._timer_info())
        info = self._timer_info()
        with lock: k = dict(keep)
        self._send(200, {"keep": k, "intervalo": RETRY, **status, "timer": info})
    def do_PUT(self):
        if not self.path.startswith("/config"): return self._send(404, {})
        try:
            d = self._body()
            with lock:
                for k in ("nicks", "profiles", "alarms", "radios"):
                    if k in d: config[k] = d[k]
                if "tz" in d: config["tz"] = int(d["tz"])
                config["rev"] = int(config.get("rev", 0)) + 1
                save_config()
                rev = config["rev"]
            self._send(200, {"ok": True, "rev": rev})
        except Exception as e: self._send(400, {"error": str(e)})
    def do_POST(self):
        try:
            if self.path.startswith("/keep"):
                d = self._body()
                with lock:
                    keep.clear(); keep.update({str(k): int(v) for k, v in d.get("keep", {}).items()}); save()
                return self._send(200, {"ok": True})
            if self.path.startswith("/timer"):
                d = self._body()
                pl = (api("GET", "/api/player") or {}) if d.get("song") else {}
                with lock:
                    timer.clear()
                    if d.get("song"):
                        timer.update({"kind": "song", "item": pl.get("item_id"), "fade": False})
                    elif d.get("at"):
                        timer.update({"kind": "time", "at": float(d["at"]), "fade": bool(d.get("fade", True))})
                    else:
                        timer.update({"kind": "time", "at": time.time() + float(d.get("minutes", 30)) * 60, "fade": bool(d.get("fade", True))})
                log("temporizador puesto")
                return self._send(200, self._timer_info())
            self._send(404, {})
        except Exception as e: self._send(400, {"error": str(e)})
    def do_DELETE(self):
        if not self.path.startswith("/timer"): return self._send(404, {})
        with lock: timer.clear()
        log("temporizador quitado")
        self._send(200, {"activo": False})
    def log_message(self, *a): pass

if __name__ == "__main__":
    load()
    threading.Thread(target=loop, daemon=True).start()
    print(f"Nebula Keeper en el puerto {PORT}, reintento cada {RETRY}s, con alarmas y temporizador", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), H).serve_forever()
