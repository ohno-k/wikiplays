# =========================================================
#  Wikiplays Backend - 二段階ビルド
# =========================================================
# Stage 1: Maven で jar をビルド
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 依存だけ先に取得 (Docker レイヤーキャッシュを効かせるため)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# ソースをコピーして jar を作成
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: 実行用の軽量 JRE イメージ
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /build/target/backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
