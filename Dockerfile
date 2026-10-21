# Practica 6 - Servicios Web REST | Contenedor con las 3 cocinas y el coordinador
#
#   docker build -t p6-restaurante .
#   docker run --rm -p 5000:5000 -p 6001-6003:6001-6003 p6-restaurante
#
# Despues: cliente web en http://localhost:5000/web/  (API en :5000/api y :6001-6003/api)

# Etapa 1: compilar con Maven (servidor en src/main/java y clientes en Cliente/)
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
COPY Cliente ./Cliente
RUN mvn -B -q package

# Etapa 2: imagen ligera solo con el JRE y el JAR
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/p6-servicios-web.jar app.jar

# Sin pantalla: los nodos corren solo como servicios REST y escriben su bitacora en consola
ENV JAVA_OPTS="-Djava.awt.headless=true -Xmx256m"

EXPOSE 5000 6001 6002 6003

# Cada nodo es un proceso independiente, igual que fuera del contenedor:
# primero las 3 cocinas y despues el coordinador (en primer plano)
CMD ["sh", "-c", "P=com.mycompany.p6sistemasdistribuidos; \
java $JAVA_OPTS -cp app.jar $P.CocinaNorte & \
java $JAVA_OPTS -cp app.jar $P.CocinaCentro & \
java $JAVA_OPTS -cp app.jar $P.CocinaSur & \
sleep 3; exec java $JAVA_OPTS -cp app.jar $P.CoordinadorDespacho"]
