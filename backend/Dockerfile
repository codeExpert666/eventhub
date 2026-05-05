# 阶段 0 后端应用镜像。
#
# Dockerfile 是“如何把应用打包成镜像”的脚本。Docker 会从上到下执行这些指令，
# 每条指令通常都会形成一个镜像层；当某一层的输入没有变化时，Docker 可以复用缓存，
# 从而加快后续构建速度。
#
# 本文件采用“多阶段构建”（multi-stage build）：
# 1. build 阶段：使用带 Maven 和 JDK 的镜像编译项目，并生成 Spring Boot 可执行 Jar；
# 2. runtime 阶段：只保留运行 Java 程序需要的 JRE 和打包产物，避免把 Maven、源码、
#    编译缓存等构建期内容放进最终镜像。
#
# 这样做的收益：
# - 镜像更小：最终镜像不包含 Maven、本地仓库缓存和源码；
# - 攻击面更小：运行环境只提供应用真正需要的组件；
# - 职责更清晰：构建依赖只出现在 build 阶段，运行依赖只出现在 runtime 阶段。

# FROM 用于声明当前阶段基于哪个基础镜像。
# 这里选择 maven:3.9.15-eclipse-temurin-17-noble 作为构建阶段基础镜像，原因是：
# - maven:3.9.15：与当前本机 Homebrew Maven 版本保持一致，减少本地构建与容器构建的工具链差异；
# - eclipse-temurin-17：镜像内使用 Eclipse Temurin 发行版的 JDK 17；
# - noble：显式固定 Ubuntu Noble 变体，避免无操作系统后缀的标签在未来切换底层发行版时带来隐性差异；
# - JDK 17：与本项目 Spring Boot 后端当前使用的 Java 版本保持一致；
# - AS build：给这个阶段命名为 build，后续可以通过 COPY --from=build
#   从该阶段复制构建产物到最终运行镜像。
#
# 注意：构建阶段必须使用 JDK，因为编译 Java 代码需要 javac 等开发工具；
# 仅有 JRE 的镜像只能运行 Java 程序，不能完整承担编译职责。
FROM maven:3.9.15-eclipse-temurin-17-noble AS build


# WORKDIR 用于设置后续指令的工作目录。
# 如果目录不存在，Docker 会自动创建。后面的 COPY、RUN 等指令都会以
# /workspace 作为默认执行位置，类似于在命令行里先执行 cd /workspace。
WORKDIR /workspace

# 先只复制 pom.xml，而不是一次性复制全部源码。
# pom.xml 是 Maven 项目的核心构建配置，里面声明了依赖、插件、Java 版本等信息。
# 依赖变化频率通常低于业务源码变化频率，因此先复制 pom.xml 可以让 Docker 更好地
# 复用“下载依赖”这一层缓存：只要 pom.xml 没变，后续修改 Java 源码时通常不需要
# 重新下载所有 Maven 依赖。
COPY pom.xml .

# RUN 会在镜像构建过程中执行一条命令，并把命令结果写入新的镜像层。
# 这里的命令不是在编译代码，也不是在生成 Jar，而是先让 Maven 根据 pom.xml
# 尽量把“后续构建可能用到的依赖和插件”下载到镜像里的 Maven 本地缓存中。
#
# 这条命令可以拆开理解：
# - mvn：调用 Maven；
# - -q：quiet，减少构建日志输出，让日志更聚焦；
# - -DskipTests：告诉 Maven 跳过测试执行。dependency:go-offline 本身通常不会运行测试，
#   这里保留该参数主要是为了和后面的 package 命令保持一致，避免某些插件逻辑触发测试相关步骤；
# - dependency:go-offline：来自 Maven Dependency Plugin，含义是“为离线构建做准备”，
#   也就是尽量预取项目依赖、构建插件以及插件依赖。
#
# 它放在 COPY src 之前，是为了更好地利用 Docker 缓存：
# - 如果只是修改 Java 业务代码，pom.xml 没变，这一层通常可以直接复用，不必重新下载依赖；
# - 如果新增/升级了依赖，pom.xml 发生变化，这一层才会重新执行并刷新依赖缓存。
#
# 注意：go-offline 不是绝对保证后面断网也一定能完成构建，因为少数插件可能在真实 package
# 阶段才解析额外内容；但在常见 Maven 项目里，它能显著减少重复下载依赖的时间。
RUN mvn -q -DskipTests dependency:go-offline

# 再复制业务源码。
# 这里把宿主机当前构建上下文中的 src 目录复制到镜像内的 /workspace/src。
# 之所以放在依赖预取之后，是为了避免每次修改业务代码都导致 Maven 依赖缓存失效。
COPY src ./src

# 执行 Maven 打包，生成 Spring Boot 可执行 Jar。
# - package：执行 Maven 生命周期中的打包阶段；
# - -DskipTests：镜像构建阶段继续跳过测试，避免“构建镜像”和“验证代码正确性”
#   两类职责混在一起；测试应在提交前或 CI 中通过 mvn test / mvn verify 单独执行。
# 打包完成后，Jar 通常会生成在 /workspace/target/ 目录下。
RUN mvn -q -DskipTests package

# 第二个 FROM 开启一个新的阶段，也就是最终运行阶段。
# 这里选择 eclipse-temurin:17-jre-noble，而不是继续使用 Maven/JDK 镜像，原因是：
# - 运行 Spring Boot Jar 只需要 Java 运行环境，通常不需要 Maven 和 javac；
# - JRE 镜像体积通常小于 JDK/Maven 镜像；
# - 运行镜像越精简，越容易降低安全风险和部署成本；
# - 仍然使用 Temurin 17 和 Ubuntu Noble，可以和构建阶段的 Java 主版本及基础发行版保持一致。
FROM eclipse-temurin:17-jre-noble

# 设置最终容器内的应用工作目录。
# 后续会把打包好的 app.jar 放到 /app/app.jar，并在这个目录下启动应用。
WORKDIR /app

# ENV 用于声明容器运行时可见的环境变量。
# SPRING_PROFILES_ACTIVE=prod 表示容器默认使用 Spring Boot 的 prod 配置。
# 数据库地址、Redis 地址、账号密码等环境相关或敏感配置，不应硬编码进镜像，
# 而应由 docker compose、Kubernetes、CI/CD 或 docker run -e 在启动时注入。
ENV SPRING_PROFILES_ACTIVE=prod

# JAVA_OPTS 预留给 JVM 参数，例如：
# - -Xms256m -Xmx512m：设置堆内存；
# - -XX:MaxRAMPercentage=75：按容器内存比例限制 JVM 可用内存；
# - -Duser.timezone=Asia/Shanghai：设置 JVM 时区。
# 默认留空，表示不额外添加 JVM 参数；需要时可在启动容器时覆盖。
ENV JAVA_OPTS=""

# 从 build 阶段复制打包产物到最终运行镜像。
# --from=build 表示来源不是宿主机文件系统，而是前面命名为 build 的构建阶段。
# /workspace/target/backend-0.0.1-SNAPSHOT.jar 是 Maven package 生成的后端 Jar；
# /app/app.jar 是运行阶段中的目标路径，使用固定名称可以让启动命令更稳定。
# 如果未来 pom.xml 中的 artifactId 或 version 发生变化，这里的源 Jar 名称也需要同步调整。
COPY --from=build /workspace/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar

# EXPOSE 用于声明应用在容器内监听的端口。
# Spring Boot 默认在 8080 端口启动，本项目也沿用这个约定。
# 注意：EXPOSE 只是镜像元数据说明，并不会自动把端口映射到宿主机；
# 真正对外暴露端口仍需要 docker run -p 或 docker-compose.yml 中的 ports 配置。
EXPOSE 8080

# ENTRYPOINT 定义容器启动时执行的主命令。容器不是“启动一个完整虚拟机”，
# 而是启动一个前台主进程；这个主进程退出后，容器也会随之退出。
#
# 这里使用的是 Docker 推荐的 JSON 数组形式，也叫 exec form：
#   ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
#
# 这三个数组元素的含义是：
# - "sh"：启动容器内的 shell 程序；
# - "-c"：告诉 shell 后面的字符串是一整段要执行的命令；
# - "exec java $JAVA_OPTS -jar /app/app.jar"：真正要由 shell 执行的启动命令。
#
# 为什么这里不直接写 ["java", "$JAVA_OPTS", "-jar", "/app/app.jar"]？
# 因为 Docker 的 JSON 数组形式不会像 shell 那样自动解析环境变量：
# - "$JAVA_OPTS" 会被当成一个普通字符串参数；
# - 即使 JAVA_OPTS="-Xms256m -Xmx512m"，它也不会自动拆成两个 JVM 参数；
# - 这样 JVM 可能收不到我们期望的内存、GC、时区等启动参数。
#
# 所以这里用 sh -c 包一层 shell，让 shell 在容器启动时完成变量展开。
# 举例：如果启动容器时设置了
#   JAVA_OPTS="-Xms256m -Xmx512m -Duser.timezone=Asia/Shanghai"
# 那么 shell 实际执行的命令会接近于：
#   exec java -Xms256m -Xmx512m -Duser.timezone=Asia/Shanghai -jar /app/app.jar
#
# 这条启动命令本身也可以继续拆开理解：
# - exec：让后面的 java 进程替换当前 shell 进程；
# - java：启动 Java 运行时；
# - $JAVA_OPTS：插入可选 JVM 参数，默认是空字符串；
# - -jar：告诉 Java 以“可执行 Jar”的方式启动应用；
# - /app/app.jar：前面 COPY 到运行镜像中的 Spring Boot 应用包。
#
# exec 很重要：如果没有 exec，容器的 1 号进程会是 shell，Java 只是 shell 的子进程；
# 有了 exec，Java 会替换 shell 成为容器的 1 号进程。这样 Docker 停止容器时发送的
# SIGTERM 等信号能更直接地传递给 Java 应用，有利于 Spring Boot 执行优雅关闭逻辑，
# 例如停止接收新请求、释放数据库连接、关闭线程池等。
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
