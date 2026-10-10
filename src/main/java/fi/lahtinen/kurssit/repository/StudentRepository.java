package fi.lahtinen.kurssit.repository;

import fi.lahtinen.kurssit.domain.Student;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Opiskelijoiden repository (vaatimus 4). Kurssit haetaan mukaan M:N-sarakkeen ja lomakkeen tarpeisiin. */
public interface StudentRepository extends JpaRepository<Student, Long> {

    @EntityGraph(attributePaths = "courses")
    List<Student> findAllByOrderByLastNameAscFirstNameAsc();

    @EntityGraph(attributePaths = "courses")
    Optional<Student> findByStudentNumber(String studentNumber);

    boolean existsByStudentNumberAndIdNot(String studentNumber, Long id);

    boolean existsByStudentNumber(String studentNumber);
}
