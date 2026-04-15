ARG NODE_IMAGE=node:25-bookworm
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-25
ARG RUNTIME_IMAGE=eclipse-temurin:25-jre

FROM ${NODE_IMAGE} AS webui-build
WORKDIR /workspace/webui

COPY webui/package*.json ./
RUN npm ci --no-audit --no-fund --progress=false

COPY webui/ ./
RUN npm run build

FROM ${MAVEN_IMAGE} AS server-build
WORKDIR /workspace

COPY server/ server/
COPY --from=webui-build /workspace/webui/dist/ server/lightflare-app/src/main/resources/static/
RUN mvn -q -f server/pom.xml -pl lightflare-app -am package -DskipTests

FROM ${RUNTIME_IMAGE}
WORKDIR /app

COPY --from=server-build /workspace/server/lightflare-app/target/lightflare-app-1.0.jar app.jar

EXPOSE 8066
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
