# syntax=docker/dockerfile:1
FROM node:20-alpine AS ui-build
WORKDIR /workspace/ruoyi-ui-vue3
COPY ruoyi-ui-vue3/package*.json ./
RUN npm install --legacy-peer-deps --no-audit --no-fund --loglevel=warn
COPY ruoyi-ui-vue3/ ./
RUN npm run build:prod

FROM maven:3.9.9-eclipse-temurin-8 AS java-build
WORKDIR /workspace
COPY . ./
RUN mvn -B -pl ruoyi-admin -am package -DskipTests

FROM eclipse-temurin:8-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl libuuid1 libstdc++6 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=java-build /workspace/ruoyi-admin/target/ruoyi-admin.jar /app/app.jar
COPY --from=java-build /workspace/ruoyi-isup/linux-lib/ /app/ruoyi-isup/linux-lib/
COPY --from=ui-build /workspace/ruoyi-ui-vue3/dist /app/ui
RUN mkdir -p /app/data/uploadPath /app/logs
EXPOSE 8080 8116/udp
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
