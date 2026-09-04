# Imagen de produccion del backend.
#
# Tres fases: compilar con el JDK, descomponer el jar en capas, y ejecutar
# sobre un JRE sin compilador ni herramientas de build. Lo que se despliega no
# lleva Maven, ni el codigo fuente, ni el JDK.

# ---------------------------------------------------------------------------
# 1. Compilacion
# ---------------------------------------------------------------------------
# Se usa el wrapper y no la imagen "maven:", para que la version de Maven la
# siga fijando el repositorio (3.9.16) y no el tag de una imagen que puede
# moverse bajo los pies.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# El wrapper y el pom van primero, solos: mientras no cambien, Docker reutiliza
# la capa de dependencias y no vuelve a descargar medio Maven Central.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
# skipTests no es un atajo: la suite necesita Testcontainers, es decir un Docker
# dentro del build, que aqui no existe. Quien ejecuta los tests es la CI, y esta
# imagen solo debe construirse sobre un commit que ya paso por ella.
RUN ./mvnw --batch-mode --no-transfer-progress clean package -DskipTests

# ---------------------------------------------------------------------------
# 2. Descomposicion en capas
# ---------------------------------------------------------------------------
# El jar ejecutable pesa 59 MB, de los que el codigo propio es una fraccion
# minima. Copiado de una pieza, cada cambio de una linea genera una capa nueva
# de 59 MB que hay que subir y bajar entera. Separado en capas, un cambio de
# codigo solo invalida la ultima, que ocupa poco mas de un megabyte.
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /layers
COPY --from=build /build/target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extraido

# ---------------------------------------------------------------------------
# 3. Ejecucion
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario sin privilegios. Si alguien consigue ejecucion dentro del
# contenedor, la diferencia entre root y este usuario es la diferencia entre
# poder escribir en el sistema de ficheros de la imagen y no poder.
RUN addgroup -S app && adduser -S -G app app

# El orden importa: de la que menos cambia a la que mas, para que las de
# arriba se reutilicen entre builds.
COPY --from=layers --chown=app:app /layers/extraido/dependencies/ ./
COPY --from=layers --chown=app:app /layers/extraido/spring-boot-loader/ ./
COPY --from=layers --chown=app:app /layers/extraido/snapshot-dependencies/ ./
COPY --from=layers --chown=app:app /layers/extraido/application/ ./

USER app
EXPOSE 8080

# MaxRAMPercentage=75: sin esta opcion la JVM reserva como maximo el 25 % de la
# memoria del contenedor y desperdicia el resto, con lo que un limite de 512 MB
# se traduce en un heap de 128 MB y en OutOfMemoryError con memoria libre de
# sobra. El porcentaje se deja configurable para poder ajustarlo sin
# reconstruir la imagen.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENV SPRING_PROFILES_ACTIVE=prod

# Hace falta una shell para expandir $JAVA_OPTS, pero el "exec" es
# imprescindible: sin el, sh se queda como PID 1, recibe el SIGTERM de
# "docker stop" y no se lo reenvia a java, con lo que cada parada acabaria a
# los diez segundos por SIGKILL, cortando las peticiones en vuelo. Con exec,
# java sustituye a la shell y es quien recibe la senal.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
