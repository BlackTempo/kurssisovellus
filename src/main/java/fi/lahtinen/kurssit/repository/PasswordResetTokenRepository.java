package fi.lahtinen.kurssit.repository;

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

    /** Siivotaan vanhentuneet tokenit, ettei taulu kasva loputtomasti. */
    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :now")
    int deleteExpired(Instant now);
}
