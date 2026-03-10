# 基础镜像
FROM ghcr.io/graalvm/jdk-community:17

# 1. 声明环境变量
ENV JAVA_OPTS="-Xms1536m -Xmx1536m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -XX:InitiatingHeapOccupancyPercent=35"
ENV TZ=Asia/Shanghai
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
ENV NODE_VERSION=24.13.1

# API密钥环境变量
ENV AMAP_KEY=""
ENV BAILIAN_KEY=""

# 2. 设定时区与安装系统依赖
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone && \
    microdnf install -y curl findutils tar && \
    curl -fsSL https://rpm.nodesource.com/setup_${NODE_VERSION}.x | bash - && \
    microdnf install -y nodejs && \
    microdnf clean all

# 3. 预安装MCP依赖与Playwright浏览器
WORKDIR /app
RUN npm install -g @playwright/mcp@latest playwright && \
    npx playwright install --with-deps chromium

# 4. 拷贝Java应用
ADD zt-ai.jar /app/zt-ai.jar

# 5. 入口
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/zt-ai.jar --spring.profiles.active=prod"]
