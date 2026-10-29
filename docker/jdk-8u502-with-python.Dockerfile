FROM eclipse-temurin:8u502-b07-jdk

LABEL org.opencontainers.image.title="jdk-8u502-with-python" \
      org.opencontainers.image.base.name="eclipse-temurin:8u502-b07-jdk"

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 \
    && rm -rf /var/lib/apt/lists/* \
    && python3 -V

ENV PYTHONIOENCODING=utf-8 \
    PYTHONUNBUFFERED=1

# 不覆盖 ENTRYPOINT：保留 temurin 的 /__cacert_entrypoint.sh（自定义 CA 证书导入用）
