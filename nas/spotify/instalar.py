"""Instala (o reinstala) «NAS Nebula»: Spotify Connect en el NAS que suena por OwnTone.

Se corre en el NAS desde el puente:
    python3 /nas/inerk/Proyectos/Nebula-Spotify/instalar.py
Necesita que antes se haya compilado librespot en Nebula-Spotify/build/bin/librespot.

Qué hace:
  1. Arma la imagen «nebula-spotify:1» (Debian + Python + las librerías que usa librespot).
  2. Crea el contenedor «nebula-spotify» en red host (para que Spotify lo vea en la casa).
     librespot manda el audio al pipe Music/Spotify/spotify, que OwnTone toca solo.
No abre puertos a internet: Spotify Connect solo se anuncia en la red de la casa.
"""
import os, sys, time
sys.path.insert(0, "/nas/inerk/Proyectos/_herramientas-android")
import docker_api as d

HOST = "/home/inerk/Proyectos/Nebula-Spotify"     # la misma carpeta vista desde el NAS
LOCAL = "/nas/inerk/Proyectos/Nebula-Spotify"
PIPES = "/home/inerk/Music/Spotify"
IMAGEN = "nebula-spotify:1"
NOMBRE = "nebula-spotify"

ARGS = [
    "/app/librespot",
    "--name", "NAS Nebula",
    "--device-type", "speaker",
    "--bitrate", "320",
    "--backend", "pipe", "--device", "/pipe/spotify", "--format", "S16",
    "--cache", "/cache", "--system-cache", "/cache", "--disable-audio-cache",
    "--volume-ctrl", "fixed",
    "--zeroconf-port", "5354",
    "--onevent", "/app/evento.sh",
]


def esperar(cid, seg=600):
    st, r = d.api("POST", f"/containers/{cid}/wait")
    return r


def imagen():
    st, imgs = d.api("GET", "/images/json")
    if any(IMAGEN in (i.get("RepoTags") or []) for i in imgs):
        print("imagen ya existe:", IMAGEN); return
    print("armando la imagen", IMAGEN)
    st, c = d.api("POST", "/containers/create", {
        "Image": "python:3.12-slim",
        "Cmd": ["sh", "-c", "apt-get update -qq && apt-get install -y -qq --no-install-recommends libasound2t64 libssl3t64 ca-certificates >/dev/null && rm -rf /var/lib/apt/lists/*"],
        "HostConfig": {"NetworkMode": "host"}})
    if st >= 300: raise SystemExit(c)
    cid = c["Id"]; d.api("POST", f"/containers/{cid}/start")
    r = esperar(cid)
    if (r or {}).get("StatusCode") != 0: raise SystemExit(f"falló apt: {r}")
    st, r = d.api("POST", f"/commit?container={cid}&repo=nebula-spotify&tag=1", {})
    d.api("DELETE", f"/containers/{cid}?force=1")
    if st >= 300: raise SystemExit(r)


def contenedor():
    os.makedirs(f"{LOCAL}/app", exist_ok=True)
    os.makedirs(f"{LOCAL}/cache", exist_ok=True); os.chmod(f"{LOCAL}/cache", 0o700)
    # el binario y el script de eventos
    with open(f"{LOCAL}/build/bin/librespot", "rb") as s, open(f"{LOCAL}/app/librespot.tmp", "wb") as t: t.write(s.read())
    os.chmod(f"{LOCAL}/app/librespot.tmp", 0o755); os.replace(f"{LOCAL}/app/librespot.tmp", f"{LOCAL}/app/librespot")
    with open(f"{LOCAL}/app/evento.sh", "w") as f:
        f.write("#!/bin/sh\nexec python3 /app/evento.py\n")
    os.chmod(f"{LOCAL}/app/evento.sh", 0o755)
    aqui = os.path.dirname(os.path.abspath(__file__))
    with open(os.path.join(aqui, "evento.py")) as s, open(f"{LOCAL}/app/evento.py", "w") as t: t.write(s.read())

    st, _ = d.api("GET", f"/containers/{NOMBRE}/json")
    if st == 200:
        print("reemplazando el contenedor", NOMBRE)
        d.api("DELETE", f"/containers/{NOMBRE}?force=1")
    st, c = d.api("POST", f"/containers/create?name={NOMBRE}", {
        "Image": IMAGEN, "Cmd": ARGS,
        "Env": ["RUST_LOG=warn,librespot=info", "META_PIPE=/pipe/spotify.metadata", "META_DATA=/cache/nebula"],
        "HostConfig": {"NetworkMode": "host", "RestartPolicy": {"Name": "unless-stopped"},
                       "Binds": [f"{HOST}/app:/app:ro", f"{HOST}/cache:/cache:rw", f"{PIPES}:/pipe:rw"]}})
    if st >= 300: raise SystemExit(c)
    st, r = d.api("POST", f"/containers/{NOMBRE}/start")
    print("contenedor", NOMBRE, "arrancado" if st < 300 else r)


if __name__ == "__main__":
    if not os.path.exists(f"{LOCAL}/build/bin/librespot"):
        raise SystemExit("Falta compilar librespot (Nebula-Spotify/build/bin/librespot)")
    if not os.path.exists("/nas/inerk/Music/Spotify/spotify"):
        raise SystemExit("Falta el pipe Music/Spotify/spotify (mkfifo)")
    imagen(); contenedor()
