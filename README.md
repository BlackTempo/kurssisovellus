# Kurssisovellus

Vaadin + Spring Boot -harjoitustyö: kurssien, opettajien, työhuoneiden ja opiskelijoiden hallinta.

**Tekijä:** Joonas Lahtinen · **Raportti:** [RAPORTTI.md](RAPORTTI.md)

| Teknologia | Versio |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.16 |
| Vaadin (Flow + Lumo) | 24.10.7 (viimeinen ilmainen 24.x-versio) |
| Hibernate / Envers | 6.6 |
| Tietokanta | H2 (kehitys), PostgreSQL 16 (Docker) |
| Ulkoinen JS-komponentti | Quill.js 2.0.3 |

## Käynnistys

### Docker (suositeltu)

```bash
docker compose up --build
```

| Palvelu | Osoite |
|---|---|
| Sovellus | http://localhost:8080 |
| Mailpit (lähetetyt sähköpostit) | http://localhost:8025 |

Data säilyy nimetyissä volumeissa. Kaiken datan saa nollattua komennolla `docker compose down -v`.

### Kehitystila

Vaatii JDK 21:n. Maven Wrapper lataa Mavenin ja Vaadin Node.js:n automaattisesti.

```bash
# Sähköpostin testipalvelin (valinnainen, mutta ilman sitä viestit eivät lähde)
docker run -d --name mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit

./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run
```

Kehitystila käyttää tiedostopohjaista H2-kantaa (`./data/`).

### Testit

```bash
./mvnw test
```

## Demotunnukset

| Tunnus | Salasana | Rooli | Mitä näkee |
|---|---|---|---|
| `admin` | `admin123` | ADMIN | Käyttäjähallinta ja muutoshistoria. Opiskelijasivulla näkyy pääsy evätty -sivu. |
| `user` | `user123` | USER | Opiskelijat. Ylläpitosivuilla näkyy pääsy evätty -sivu. |
| `super` | `super123` | SUPER | Kuten USER |
| *(ei kirjautunut)* | | | Etusivu, kirjautuminen, rekisteröityminen ja salasanan palautus |

Rekisteröitymällä luodaan uusi USER-tunnus. Ylläpitäjä saa siitä sähköposti-ilmoituksen Mailpitiin.

## Rakenne

```
src/main/java/fi/lahtinen/kurssit/
├── config/       AppShell (@Theme, @Push), SecurityConfig, AuditConfig, AsyncConfig, DataInitializer
├── domain/       JPA-entiteetit, Envers-revisio
├── repository/   Spring Data -repositoriot + Criteria API -haku (CourseRepositoryImpl)
├── security/     UserDetailsService, kirjautunut käyttäjä
├── service/      Liiketoimintalogiikka, sähköposti, CSV, tiedostot, historia, Push-broadcaster
└── ui/           MainLayout, komponentit (Quill, aikajana, footer) ja näkymät
src/main/frontend/
├── components/quill-editor.ts      Lit-kääre Quill.js:lle
└── themes/kurssiteema/             Globaali teema + näkymäkohtainen CSS
src/main/resources/vaadin-i18n/     Käännökset (fi/en)
```
