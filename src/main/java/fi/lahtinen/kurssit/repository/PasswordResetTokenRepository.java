package fi.lahtinen.kurssit.repository;

import fi.lahtinen.kurssit.domain.AppUser;
import fi.lahtinen.kurssit.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

/** Salasanan palautustokenit (vaatimus 27). */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Onko käyttäjälle luotu käyttämätön token annetun hetken jälkeen vanhenevaksi (uudelleenlähetyksen rajoitus). */
    boolean existsByUserIdAndUsedAtIsNullAndExpiresAtAfter(Long userId, Instant expiresAfter);

    long countByUserId(Long userId);

    /** Siivotaan vanhentuneet tokenit, ettei taulu kasva loputtomasti. */
    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :now")
    int deleteExpired(Instant now);

    /**
     * Mitätöi käyttäjän kaikki käyttämättömät tokenit. Kutsutaan salasanan vaihdon yhteydessä, jotta
     * myös aiemmin pyydetyt (esim. väärään paikkaan päätyneet) linkit lakkaavat toimimasta.
     */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.user = :user and t.usedAt is null")
    int invalidateAllForUser(AppUser user, Instant now);

    /** Tokenit viittaavat käyttäjään pakollisella vierasavaimella, joten ne poistetaan ennen käyttäjää. */
    @Modifying
    @Query("delete from PasswordResetToken t where t.user = :user")
    int deleteByUser(AppUser user);
}
