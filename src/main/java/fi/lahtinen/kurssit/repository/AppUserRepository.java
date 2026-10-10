package fi.lahtinen.kurssit.repository;

import fi.lahtinen.kurssit.domain.AppUser;
import fi.lahtinen.kurssit.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Käyttäjien repository (vaatimus 16). */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    List<AppUser> findByRolesContaining(Role role);

    List<AppUser> findAllByOrderByUsernameAsc();
}
