# 基础镜像
FROM ghcr.io/graalvm/jdk-community:17

# 0. 声明构建参数（jar包名称，默认值为zt-server.jar）
ARG JAR_FILE=zt-server.jar

# 1. 声明环境变量
ENV JAVA_OPTS="-Xms1536m -Xmx1536m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -XX:InitiatingHeapOccupancyPercent=35"
ENV TZ=Asia/Shanghai
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
ENV NODE_VERSION=20

# 2. 设定时区与安装系统依赖
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

RUN microdnf install -y curl findutils tar && \
    curl -fsSL https://rpm.nodesource.com/setup_${NODE_VERSION}.x | bash - && \
    microdnf install -y nodejs && \
    microdnf clean all

# 3. 安装Playwright Chromium所需的系统依赖（RHEL/Fedora系）
RUN microdnf install -y \
    atk cups-libs gtk3 libXcomposite libXcursor libXdamage libXext libXi \
    libXrandr libXScrnSaver libXtst pango alsa-lib mesa-libgbm nss nspr \
    libdrm libxkbcommon libwayland-client xorg-x11-fonts-100dpi \
    xorg-x11-fonts-75dpi xorg-x11-utils xorg-x11-fonts-cyrillic \
    xorg-x11-fonts-Type1 xorg-x11-fonts-misc && \
    microdnf clean all

# 4. 预安装MCP依赖与Playwright浏览器
WORKDIR /app
RUN npm install -g @playwright/mcp@latest playwright && \
    npx playwright install chromium

# 5. 拷贝Java应用 根据logback.xml配置，日志将生成在/app/logs
ADD ${JAR_FILE} /app/zt-server.jar

# 6. 入口（API密钥通过运行时 -e 参数注入）
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/zt-server.jar --spring.profiles.active=prod"]
