# Imagen del ApiRest del chatbot (Clase 12: del .jar a internet).
# Se construye desde la raíz del repositorio, después de compilar con Maven:
#   cd api && .\mvnw.cmd clean package && cd ..
#   docker build -f api/Dockerfile -t chatbot-api:1.0.0 .
# No contiene credenciales: la API key y la contraseña llegan como variables al arrancar.

# Solo el JRE: en producción no se compila, así que el JDK sobra.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Usuario sin privilegios para ejecutar el proceso.
RUN addgroup -S app && adduser -S app -G app

# El .jar que Maven ya compiló en la laptop.
COPY api/target/chatbot-api-*.jar app.jar

# Contenido de referencia, para ejecutar la ingesta (--ingesta) en la nube.
COPY content/ content/
ENV CHATBOT_CHUNKING_RUTA=/app/content

USER app

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
