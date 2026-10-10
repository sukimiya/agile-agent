# JDK21 runtime image
FROM eclipse-temurin:21-jdk-alpine-3.22

# 装 bash：Alpine 默认只有 busybox 的 /bin/sh，Portainer 的 Console 功能默认尝试 /bin/bash
# 会连不上，装了才能在线排查（unzip 查 jar 内容等）
RUN apk add --no-cache bash

WORKDIR /app
EXPOSE 8088

# Copy the executable server JAR from the Maven reactor module.
# Build context is the repository root.
COPY agent-server/target/agile-agent-server-0.0.1-SNAPSHOT.jar /app/agile-agent-server.jar

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$PATH:$JAVA_HOME/bin
ENV APP_DATA_DIR=/data

ENTRYPOINT ["java","-server","-Xms1024m","-Xmx1024m","-Djava.security.egd=file:/dev/urandom","-Duser.timezone=Asia/Shanghai","-jar","/app/agile-agent-server.jar"]
