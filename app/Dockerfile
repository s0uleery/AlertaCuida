# Imagen del backend. El contexto es la raíz del repositorio porque compila todos los módulos de Maven.
# Se usa desde docker-compose.yml; la configuración (perfil y base de datos) llega por variables de entorno.

# Compilación: las pruebas y el estilo no se repiten aquí, ya los revisa ./mvnw verify en el CI
FROM eclipse-temurin:21-jdk AS compilacion
WORKDIR /fuente
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY domain domain
COPY application application
COPY adapters adapters
COPY app app
# La caché evita descargar las dependencias de Maven otra vez en cada compilación
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode package -DskipTests -Dcheckstyle.skip

# Ejecución: solo el JRE y el jar, sin el código fuente ni Maven
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S alertacuida && adduser -S alertacuida -G alertacuida
USER alertacuida
WORKDIR /aplicacion
COPY --from=compilacion /fuente/app/target/alertacuida-app-*.jar alertacuida.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "alertacuida.jar"]
