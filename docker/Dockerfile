# 基础镜像（与 mes-service 保持一致：仅 JDK21）
FROM 192.168.3.119:80/jowosmart/eclipse-temurin:21-jdk-alpine-3.22

# 装 bash：Alpine 默认只有 busybox 的 /bin/sh，Portainer 的 Console 功能默认尝试 /bin/bash
# 会连不上，装了才能在线排查（unzip 查 jar 内容等）
RUN apk add --no-cache bash

WORKDIR /app
EXPOSE 8088

# 把可执行 jar 包复制到基础镜像的目录下（前端已拆分为独立项目 l7-skill-frontend，单独部署）
# 构建上下文为仓库根目录（Jenkins 直接检出到工作区根），故路径不带 mcp-tool/ 前缀
ADD target/agent-0.0.1-SNAPSHOT.jar /app/app.jar

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$PATH:$JAVA_HOME/bin
ENV APP_DATA_DIR=/data

ENTRYPOINT ["java","-server","-Xms1024m","-Xmx1024m","-Djava.security.egd=file:/dev/urandom","-Duser.timezone=Asia/Shanghai","-Dspring.profiles.active=dev","-jar","/app/app.jar"]
