package fi.lahtinen.kurssit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Varmistaa, että koko Spring-konteksti (JPA, Envers, Security, Vaadin) käynnistyy. */
@SpringBootTest
@ActiveProfiles("test")
class KurssisovellusApplicationTests {

    @Test
    void contextLoads() {
    }
}
