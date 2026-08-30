FROM node:24-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
COPY docs/api/openapi.yaml /workspace/docs/api/openapi.yaml
RUN npm run build

FROM eclipse-temurin:25-jdk-alpine AS backend-build
WORKDIR /workspace/backend
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B -DskipTests dependency:go-offline
COPY backend/src/ src/
COPY --from=frontend-build /workspace/frontend/dist/ src/main/resources/static/
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S trackdrop && adduser -S trackdrop -G trackdrop
COPY --from=backend-build /workspace/backend/target/trackdrop-backend-*.jar app.jar
USER trackdrop
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
