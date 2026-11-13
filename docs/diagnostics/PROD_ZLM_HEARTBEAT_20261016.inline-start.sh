# Sanitized reference only; replace the example WVP address before authorized use.
cd /opt/media/bin
zlm_previous_umask=$(umask)
umask 077
{ cat /opt/media/conf/config.ini; printf '\n[hook]\nenable=1\nalive_interval=10\non_server_started=http://wvp-service.example.invalid:8080/index/hook/on_server_started\non_server_keepalive=http://wvp-service.example.invalid:8080/index/hook/on_server_keepalive\n'; } > /tmp/zlm-wvp-config.ini
umask "$zlm_previous_umask"
exec /opt/media/bin/MediaServer -s /opt/media/conf/default.pem -c /tmp/zlm-wvp-config.ini -l 0
