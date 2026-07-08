# ─── 构建阶段 ───
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:resolve -q
COPY src ./src
RUN mvn clean package -q -DskipTests

# ─── 运行阶段 ───
FROM tomcat:11.0-jdk21-temurin
LABEL maintainer="FilmOracle"
LABEL description="AI Movie Review Intelligence Platform"

# 清理 Tomcat 默认应用
RUN rm -rf /usr/local/tomcat/webapps/*

# 复制 WAR
COPY --from=builder /build/target/FilmOracle.war /usr/local/tomcat/webapps/ROOT.war

# JVM 参数
ENV JAVA_OPTS="-Xms256m -Xmx768m -Dfile.encoding=UTF-8"

# 暴露端口
EXPOSE 8080

# 启动
CMD ["catalina.sh", "run"]
