# ==========================================
# Etapa 1: Construcción (Maven + Java 21)
# ==========================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Caché de dependencias
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compilación y empaquetado Quarkus
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# Etapa 2: Imagen de ejecución ligera (Ubuntu noble con glibc para ONNX Runtime)
# ==========================================
FROM eclipse-temurin:21-jre-noble
WORKDIR /deployments

# Usuario sin privilegios por seguridad
RUN groupadd -r bayanogroup && useradd -r -g bayanogroup bayamouser

# Copia de artefactos Quarkus Fast-JAR
COPY --from=builder --chown=bayamouser:bayanogroup /workspace/target/quarkus-app/lib/ /deployments/lib/
COPY --from=builder --chown=bayamouser:bayanogroup /workspace/target/quarkus-app/*.jar /deployments/
COPY --from=builder --chown=bayamouser:bayanogroup /workspace/target/quarkus-app/app/ /deployments/app/
COPY --from=builder --chown=bayamouser:bayanogroup /workspace/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER bayamouser

ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /deployments/quarkus-run.jar"]
