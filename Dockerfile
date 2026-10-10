# syntax=docker/dockerfile:1
#
# Monivaiheinen build (vaatimus 24):
#  1) build-vaihe kääntää sovelluksen ja Vaadinin frontend-bundlen JDK:lla (Node.js ladataan
#     automaattisesti Vaadinin Maven-pluginilla),
#  2) ajovaihe sisältää vain JRE:n ja valmiin jarin. Lopullinen image on näin selvästi pienempi,
#     eikä siinä ole kääntäjää, Mavenia, Nodea tai lähdekoodia, joita hyökkääjä voisi hyödyntää.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Riippuvuudet ladataan omassa kerroksessaan ennen lähdekoodin kopiointia: Docker käyttää välimuistia
# niin kauan kuin pom.xml ei muutu, joten koodimuutoksen jälkeinen build on huomattavasti nopeampi.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline -Pproduction

COPY src src
RUN ./mvnw -B -Pproduction -DskipTests package \
    && cp target/kurssisovellus-*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

# Sovellus ajetaan omana käyttäjänään eikä rootina: mahdollinen haavoittuvuus ei anna
# hyökkääjälle pääkäyttäjän oikeuksia konttiin.
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /data/uploads && chown -R app:app /data
USER app

COPY --from=build /build/app.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker \
    APP_UPLOAD_DIR=/data/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
VOLUME /data/uploads

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
