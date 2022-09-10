#!/usr/bin/sh

apt update && apt install -y curl && \
    curl -Lo /usr/local/bin/sbt https://raw.githubusercontent.com/sbt/sbt/1.8.x/sbt && \
    chmod +x /usr/local/bin/sbt && \
    curl -Lo llvm.sh https://apt.llvm.org/llvm.sh && \
    chmod +x llvm.sh && \
    apt install -y lsb-release wget software-properties-common gnupg autopoint && \
    ./llvm.sh 13 && \
    apt update && \
    apt install -y zip unzip tar make cmake autoconf pkg-config libclang-13-dev git && \
    # install Unit, OpenSSL and Unit development headers
    curl --output /usr/share/keyrings/nginx-keyring.gpg  \
      https://unit.nginx.org/keys/nginx-keyring.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/nginx-keyring.gpg] https://packages.nginx.org/unit/debian/ bullseye unit" >> /etc/apt/sources.list.d/unit.list && \
    echo "deb-src [signed-by=/usr/share/keyrings/nginx-keyring.gpg] https://packages.nginx.org/unit/debian/ bullseye unit" >> /etc/apt/sources.list.d/unit.list && \
    apt update && \
    apt install -y unit-dev
