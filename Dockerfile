FROM openjdk:17-bullseye as builder

RUN apt update && \
    apt install -y curl && \
    # install SBT
    curl -Lo /usr/bin/sbt https://raw.githubusercontent.com/sbt/sbt/v1.6.2/sbt && \
    chmod +x /usr/bin/sbt &&\
    # install LLVM installer dependencies
    apt install -y lsb-release wget software-properties-common && \
    wget https://apt.llvm.org/llvm.sh && \
    chmod +x llvm.sh && \
    # install LLVM 13
    ./llvm.sh 13 && \
    apt install -y libclang-13-dev &&\
    # install libpq for postgres
    apt install -y libpq-dev && \
    # install Unit, OpenSSL and Unit development headers
    curl --output /usr/share/keyrings/nginx-keyring.gpg  \
      https://unit.nginx.org/keys/nginx-keyring.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/nginx-keyring.gpg] https://packages.nginx.org/unit/debian/ bullseye unit" >> /etc/apt/sources.list.d/unit.list && \
    echo "deb-src [signed-by=/usr/share/keyrings/nginx-keyring.gpg] https://packages.nginx.org/unit/debian/ bullseye unit" >> /etc/apt/sources.list.d/unit.list && \
    apt update && \
    apt install -y unit-dev libssl-dev

ENV LLVM_BIN "/usr/lib/llvm-13/bin"

ENV SN_RELEASE "fast"
ENV CI "true"

COPY . /sources

RUN cd /sources && sbt clean app/test buildApp

FROM nginx/unit:1.26.1-minimal as runtime_deps

RUN apt update && apt install libpq5

FROM runtime_deps

COPY --from=builder /sources/build/twotm8 /usr/bin/twotm8
COPY --from=builder /sources/build/ /www/static

COPY config.json /docker-entrypoint.d/config.json

RUN chmod 0777 /usr/bin/twotm8

EXPOSE 8080

CMD ["unitd", "--no-daemon", "--control", "unix:/var/run/control.unit.sock", "--log", "/dev/stderr"]
