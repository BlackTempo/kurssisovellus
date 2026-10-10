package fi.lahtinen.kurssit.repository;

import fi.lahtinen.kurssit.domain.Teacher;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Opettajien repository (vaatimus 1).
 * <p>
 * {@link EntityGraph} hakee työhuoneen samassa kyselyssä. Koska open-in-view on pois päältä,
 * gridin "Työhuone"-sarake kaatuisi muuten LazyInitializationExceptioniin – ja vaikka ei kaatuisi,
 * jokainen rivi tekisi oman kyselynsä (N+1).
 */
public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    @EntityGraph(attributePaths = "office")
    List<Teacher> findAllByOrderByLastNameAscFirstNameAsc();

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByEmailIgnoreCase(String email);
}
