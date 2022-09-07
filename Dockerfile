FROM eclipse-temurin:17-focal as builder

RUN apt update && apt install -y curl && \
    curl -Lo /usr/local/bin/sbt https://raw.githubusercontent.com/sbt/sbt/1.8.x/sbt && \
    chmod +x /usr/local/bin/sbt && \
    curl -Lo llvm.sh https://apt.llvm.org/llvm.sh && \
    chmod +x llvm.sh && \
    apt install -y lsb-release wget software-properties-common gnupg && \
    ./llvm.sh 13 && \
    apt update && \
    apt install -y zip unzip tar make cmake autoconf pkg-config libclang-13-dev git

ENV LLVM_BIN "/usr/lib/llvm-13/bin"
ENV CC "/usr/lib/llvm-13/bin/clang"

ENV SN_RELEASE "fast"
ENV CI "true"

COPY . /sources

RUN cd /sources && sbt clean app/test buildApp

FROM nginx/unit:1.26.1-minimal as runtime_deps

FROM runtime_deps

COPY --from=builder /sources/build/twotm8 /usr/bin/twotm8
COPY --from=builder /sources/build/ /www/static

COPY config.json /docker-entrypoint.d/config.json

RUN chmod 0777 /usr/bin/twotm8

EXPOSE 8080

CMD ["unitd", "--no-daemon", "--control", "unix:/var/run/control.unit.sock", "--log", "/dev/stderr"]
