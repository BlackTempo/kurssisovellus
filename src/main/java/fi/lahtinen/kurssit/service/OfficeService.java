package fi.lahtinen.kurssit.service;

import fi.lahtinen.kurssit.domain.Office;
import fi.lahtinen.kurssit.repository.OfficeRepository;
import fi.lahtinen.kurssit.security.SecurityUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static fi.lahtinen.kurssit.service.DataChangedEvent.Action;

/** Työhuoneiden liiketoimintalogiikka (vaatimus 2: CRUD + 1:1-relaation eheys). */
@Service
public class OfficeService {

    private final OfficeRepository officeRepository;
    private final ApplicationEventPublisher events;

    public OfficeService(OfficeRepository officeRepository, ApplicationEventPublisher events) {
        this.officeRepository = officeRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Office> findAll() {
        return officeRepository.findAllByOrderByRoomNumberAsc();
    }

    @Transactional
    public Office save(Office office) {
        // 1:1-relaatio: tarkistetaan ettei opettajalla ole jo toista huonetta. Tietokannan unique-
        // rajoite estäisi tämän myös, mutta antaisi käyttäjälle käsittämättömän virheilmoituksen.
        Long teacherId = office.getTeacher().getId();
        boolean taken = office.isNew()
                ? officeRepository.existsByTeacherId(teacherId)
                : officeRepository.existsByTeacherIdAndIdNot(teacherId, office.getId());
        if (taken) {
            throw new BusinessException("Opettajalla " + office.getTeacher().getFullName() + " on jo työhuone");
        }
        Action action = office.isNew() ? Action.CREATED : Action.UPDATED;
        Office saved = officeRepository.save(office);
        events.publishEvent(new DataChangedEvent("Työhuone", action, saved.getRoomNumber(),
                SecurityUtils.currentUsernameOrSystem(), Instant.now()));
        return saved;
    }

    @Transactional
    public void delete(Office office) {
        officeRepository.deleteById(office.getId());
        events.publishEvent(new DataChangedEvent("Työhuone", Action.DELETED, office.getRoomNumber(),
                SecurityUtils.currentUsernameOrSystem(), Instant.now()));
    }
}
