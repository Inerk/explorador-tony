#!/bin/bash
# Arranque del contenedor «nebula-spotify-oficial»: la app oficial de Spotify en el NAS.
# - Pantalla virtual (Xvfb) y una página web (noVNC, solo red de la casa) para iniciar sesión.
# - El sonido sale por PulseAudio al pipe de OwnTone.
export HOME=/data DISPLAY=:1 PULSE_SERVER=unix:/tmp/pulse/native XDG_RUNTIME_DIR=/tmp/run
mkdir -p /tmp/pulse /tmp/run "$HOME" && chmod 700 /tmp/run
# al reiniciar el contenedor se quedan los bloqueos de la pantalla anterior
rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 /tmp/pulse/native
cd "$HOME"

Xvfb :1 -screen 0 1280x800x24 -nolisten tcp &
sleep 2
openbox &
pulseaudio -n -F /app/pulse.pa --exit-idle-time=-1 --daemonize=no --log-target=stderr --log-level=warning &

# contraseña de la página para ver la pantalla (se crea una vez, queda en /data/vnc.txt)
if [ ! -s "$HOME/vnc.txt" ]; then tr -dc 'a-z0-9' </dev/urandom | head -c 8 > "$HOME/vnc.txt"; fi
x11vnc -storepasswd "$(cat "$HOME/vnc.txt")" "$HOME/.vncpass" >/dev/null 2>&1
x11vnc -display :1 -forever -shared -rfbport 5901 -localhost -rfbauth "$HOME/.vncpass" -quiet -bg -o /tmp/x11vnc.log
websockify --web /usr/share/novnc "${WEB_PORT:-6080}" localhost:5901 >/tmp/websockify.log 2>&1 &

# Spotify; si se cierra, se vuelve a abrir
while true; do
  echo "$(date '+%F %T') abriendo Spotify"
  dbus-run-session -- spotify --no-zygote --disable-gpu --disable-dev-shm-usage > /tmp/spotify.log 2>&1
  echo "Spotify se cerró; lo vuelvo a abrir en 5 s"
  sleep 5
done
