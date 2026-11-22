# ──────────────────────────────────────────
#  Stage 1 — Build
# ──────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Copia apenas o pom.xml primeiro → Docker cacheia as deps enquanto o pom não mudar
COPY pom.xml .

# Baixa todas as dependências (usa o mvn da própria imagem base, sem precisar do mvnw)
RUN mvn -q -DskipTests dependency:go-offline

# Copia o código-fonte e gera o JAR
COPY src/ src/
RUN mvn -DskipTests clean package

# ──────────────────────────────────────────
#  Stage 2 — Runtime (imagem final mínima)
# ──────────────────────────────────────────
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Roda como usuário sem privilégio (boa prática de segurança)
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser

# Copia apenas o JAR gerado no estágio anterior
COPY --from=build /app/target/*.jar app.jar

# O Render injeta a variável PORT em tempo de execução (padrão: 8080)
ENV PORT=8080
EXPOSE ${PORT}

# -XX:+UseContainerSupport  → respeita os limites de CPU/RAM do container
# -XX:MaxRAMPercentage=75   → usa até 75 % da RAM disponível para o heap
ENTRYPOINT ["sh", "-c", "java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -Dserver.port=${PORT} \
  -jar app.jar"]
