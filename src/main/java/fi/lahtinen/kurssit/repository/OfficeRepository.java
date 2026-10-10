package fi.lahtinen.kurssit.repository;

import fi.lahtinen.kurssit.domain.Office;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Työhuoneiden repository (vaatimus 2). Opettaja haetaan mukaan, koska grid näyttää 1:1-relaation. */
public interface OfficeRepository extends JpaRepository<Office, Long> {

    @EntityGraph(attributePaths = "teacher")
    List<Office> findAllByOrderByRoomNumberAsc();

    Optional<Office> findByTeacherId(Long teacherId);

    boolean existsByTeacherIdAndIdNot(Long teacherId, Long id);

    boolean existsByTeacherId(Long teacherId);
}
