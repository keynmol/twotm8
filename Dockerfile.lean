FROM nginx/unit:1.28.0-minimal

COPY build/twotm8 /usr/bin/twotm8
COPY build /www/static

COPY config.json /docker-entrypoint.d/config.json

RUN chmod 0777 /usr/bin/twotm8

EXPOSE 8080

CMD ["unitd", "--no-daemon", "--control", "unix:/var/run/control.unit.sock", "--log", "/dev/stderr"]
