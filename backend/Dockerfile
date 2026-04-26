# 阶段 0 后端应用镜像。
# 采用多阶段构建：
# 1. build 阶段使用 Maven + JDK 编译并打包 Spring Boot Jar；
# 2. runtime 阶段只保留 JRE 和打包产物，减少最终镜像体积与运行时攻击面。

FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# 先复制 pom.xml 并预取依赖，让 Docker 可以更好地复用依赖缓存。
# 业务代码变化时，只要 pom.xml 不变，依赖下载层通常可以直接命中缓存。
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# 再复制源码并打包。
# 镜像构建阶段跳过测试，测试由本地/CI 的 mvn test 单独负责，避免容器构建和验证职责混在一起。
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

# 容器默认按 prod profile 启动。
# 数据库、Redis 等敏感或环境相关配置必须由 Compose/Kubernetes/启动命令通过环境变量注入。
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""

COPY --from=build /workspace/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

# 使用 sh -c 是为了允许 JAVA_OPTS 在启动时展开，例如后续设置堆大小或 GC 参数。
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
