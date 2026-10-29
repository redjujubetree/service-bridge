#!/bin/sh
# JAVA_OPTS 不加引号：靠 shell 拆成多个参数，支持 -e JAVA_OPTS="-Xmx1g -Xms1g"
exec java $JAVA_OPTS -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -jar /app/app.jar "$@"
