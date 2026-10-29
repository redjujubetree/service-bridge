FROM jdk-8u502-with-python:8u502-b07

# 以下均为默认值，运行时可用 docker run -e / k8s env 覆盖
ENV LANG=C.UTF-8 \
    TZ=Asia/Shanghai \
    SERVER_PORT=9999 \
    INVOICE_PARSER_SCRIPT=/app/python/invoice_parser.py \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

WORKDIR /app

COPY target/service-bridge-example-server-0.1.0.jar /app/app.jar
COPY src/main/resources/python/invoice_parser.py /app/python/invoice_parser.py
COPY start.sh /app/start.sh

# 构建期加执行权限；顺带清掉 Windows 下可能带进来的 CRLF
RUN chmod 755 /app/start.sh && sed -i 's/\r$//' /app/start.sh

EXPOSE 9999

ENTRYPOINT ["/app/start.sh"]
