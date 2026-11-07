# Imagem da API do JobFly para deploy opcional (o uso normal é local, com scripts/instalar.sh).
# Build em duas etapas: o JDK só compila; a imagem final tem só o JRE e roda com usuário sem privilégios.
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -q -B -ntp -DskipTests package && cp target/jobfly-api-*.jar /src/app.jar

FROM eclipse-temurin:21-jre-noble
RUN groupadd --system jobfly && useradd --system --gid jobfly --home /app --shell /usr/sbin/nologin jobfly
WORKDIR /app
COPY --from=build --chown=jobfly:jobfly /src/app.jar app.jar
USER jobfly
# Num contêiner a API precisa escutar fora do loopback; o HTTPS fica no proxy da plataforma (X-Forwarded-*).
ENV JOBFLY_ADDRESS=0.0.0.0 \
    JOBFLY_FORWARD_HEADERS=native \
    JOBFLY_SECURE_COOKIES=true \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
