"""Eventos de librespot → metadatos para OwnTone.

librespot llama este script en cada evento (canción nueva, play, pausa, volumen…).
Escribe en el pipe «spotify.metadata» el título, artista, álbum, portada y avance,
en el formato de Shairport Sync, que es el que lee OwnTone.
Solo trabaja dentro de la casa: lo único que baja de internet es la portada (i.scdn.co).
"""
import base64, errno, fcntl, json, os, time, urllib.request

PIPE = os.environ.get("META_PIPE", "/pipe/spotify.metadata")
DATA = os.environ.get("META_DATA", "/cache/nebula")
RATE = 44100
START = 1_000_000          # OwnTone no acepta ceros en el avance
os.makedirs(DATA, exist_ok=True)
STATE = os.path.join(DATA, "actual.json")
COVER = os.path.join(DATA, "portada.jpg")


def item(code, data=b""):
    typ = "ssnc" if code in ("PICT", "prgr", "pvol", "pfls") else "core"
    if isinstance(data, str):
        data = data.encode()
    return ("<item><type>%s</type><code>%s</code><length>%d</length>\n"
            "<data encoding=\"base64\">\n%s</data></item>\n" % (
                typ.encode().hex(), code.encode().hex(), len(data), base64.b64encode(data).decode())).encode()


def send(chunks, wait=6.0):
    """Manda al pipe si OwnTone lo está leyendo (espera unos segundos a que lo abra)."""
    end = time.time() + wait
    while True:
        try:
            fd = os.open(PIPE, os.O_WRONLY | os.O_NONBLOCK)
            break
        except OSError as e:
            if e.errno not in (errno.ENXIO, errno.ENOENT) or time.time() >= end:
                return False
            time.sleep(0.5)
    # un evento a la vez: que la portada de uno no se mezcle con el texto de otro
    lk = open(os.path.join(DATA, ".lock"), "w")
    try:
        fcntl.flock(lk, fcntl.LOCK_EX)
        os.set_blocking(fd, True)
        for c in chunks:
            os.write(fd, c)
        return True
    except OSError:
        return False
    finally:
        os.close(fd)
        lk.close()


def load():
    try:
        with open(STATE) as f:
            return json.load(f)
    except Exception:
        return {}


def save(s):
    tmp = STATE + ".tmp"
    with open(tmp, "w") as f:
        json.dump(s, f, ensure_ascii=False)
    os.replace(tmp, STATE)


def progress(s, pos_ms):
    dur = int(s.get("dur", 0))
    pos = max(0, int(pos_ms or 0))
    if dur <= 0:
        return []
    return [item("prgr", "%d/%d/%d" % (START, START + pos * RATE // 1000, START + dur * RATE // 1000))]


def full(s, pos_ms=None):
    out = [item("minm", s.get("title", "")), item("asar", s.get("artist", "")), item("asal", s.get("album", ""))]
    if s.get("cover") and os.path.exists(COVER):
        try:
            with open(COVER, "rb") as f:
                out.append(item("PICT", f.read()))
        except OSError:
            pass
    out += progress(s, s.get("pos", 0) if pos_ms is None else pos_ms)
    return out


def main():
    ev = os.environ.get("PLAYER_EVENT", "")
    s = load()
    if ev == "session_connected":
        s["connected_at"] = time.time()
        save(s)
    elif ev == "track_changed":
        covers = [c for c in os.environ.get("COVERS", "").split("\n") if c.strip()]
        s.update(title=os.environ.get("NAME", ""), album=os.environ.get("ALBUM", ""),
                 artist=", ".join(a for a in os.environ.get("ARTISTS", "").split("\n") if a.strip()),
                 dur=int(os.environ.get("DURATION_MS", "0") or 0), pos=0, cover="")
        # Spotify da varias portadas de distintos tamaños: se queda la más grande
        best = b""
        for url in covers[:4]:
            try:
                with urllib.request.urlopen(url, timeout=8) as r:
                    img = r.read(1_000_001)
                if img[:2] in (b"\xff\xd8", b"\x89P") and len(best) < len(img) <= 1_000_000:
                    best = img
            except Exception:
                pass
        if best:
            with open(COVER, "wb") as f:
                f.write(best)
            s["cover"] = "1"
        save(s)
        send(full(s), wait=1.0)
    elif ev in ("playing", "seeked", "position_correction"):
        s["pos"] = int(os.environ.get("POSITION_MS", "0") or 0)
        save(s)
        # al empezar a sonar OwnTone abre el pipe: se manda todo otra vez
        send(full(s) if ev == "playing" else progress(s, s["pos"]))
    elif ev == "volume_changed":
        # el volumen de la app de Spotify mueve el volumen general de OwnTone
        # (se ignora el que manda Spotify al conectarse, para que no truene de golpe)
        if time.time() - float(s.get("connected_at", 0)) > 5:
            v = int(os.environ.get("VOLUME", "0") or 0) * 100 // 65535
            db = -144.0 if v <= 0 else (v - 100) * 30 / 100
            send([item("pvol", "%.2f,0.00,0.00,0.00" % db)], wait=0.5)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print("evento.py:", e, flush=True)
