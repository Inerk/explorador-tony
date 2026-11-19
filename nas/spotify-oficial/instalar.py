"""Instala «nebula-spotify-oficial»: la app oficial de Spotify para Linux corriendo en el NAS.

Se corre en el NAS desde el puente:
    python3 /nas/inerk/Proyectos/Nebula-SpotifyOficial/instalar.py
Antes hay que tener la imagen «nebula-spotify-oficial:1» (Debian + spotify-client del
repositorio oficial de Spotify + Xvfb + PulseAudio + noVNC).

- Red host. La página para ver la pantalla (noVNC) queda en el puerto 6080, solo en la red de la casa,
  con contraseña (Nebula-SpotifyOficial/datos/vnc.txt). Tony inicia sesión ahí él mismo.
- El sonido sale al pipe Music/Spotify/spotify, que OwnTone toca solo en las bocinas escogidas.
"""
import os, shutil, sys
sys.path.insert(0, "/nas/inerk/Proyectos/_herramientas-android")
import docker_api as d

HOST = "/home/inerk/Proyectos/Nebula-SpotifyOficial"
LOCAL = "/nas/inerk/Proyectos/Nebula-SpotifyOficial"
PIPES = "/home/inerk/Music/Spotify"
IMAGEN = "nebula-spotify-oficial:1"
NOMBRE = "nebula-spotify-oficial"

if __name__ == "__main__":
    aqui = os.path.dirname(os.path.abspath(__file__))
    os.makedirs(f"{LOCAL}/app", exist_ok=True)
    os.makedirs(f"{LOCAL}/datos", exist_ok=True)
    os.chown(f"{LOCAL}/datos", 1000, 1000); os.chmod(f"{LOCAL}/datos", 0o700)
    for f in ("inicio.sh", "pulse.pa"):
        shutil.copy(os.path.join(aqui, f), f"{LOCAL}/app/{f}")
    os.chmod(f"{LOCAL}/app/inicio.sh", 0o755)

    st, _ = d.api("GET", f"/containers/{NOMBRE}/json")
    if st == 200:
        print("reemplazando el contenedor", NOMBRE)
        d.api("DELETE", f"/containers/{NOMBRE}?force=1")
    st, c = d.api("POST", f"/containers/create?name={NOMBRE}", {
        "Image": IMAGEN, "User": "1000:1000", "Cmd": ["/bin/bash", "/app/inicio.sh"],
        "Env": ["WEB_PORT=6080", "LANG=C.UTF-8"],
        "HostConfig": {"NetworkMode": "host", "RestartPolicy": {"Name": "unless-stopped"},
                       "ShmSize": 512 * 1024 * 1024, "Memory": 1536 * 1024 * 1024,
                       "Binds": [f"{HOST}/app:/app:ro", f"{HOST}/datos:/data:rw", f"{PIPES}:/pipe:rw"]}})
    if st >= 300: raise SystemExit(c)
    st, r = d.api("POST", f"/containers/{NOMBRE}/start")
    print("contenedor", NOMBRE, "arrancado" if st < 300 else r)
