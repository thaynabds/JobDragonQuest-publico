package dev.jobdragonquest.applications;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.jobdragonquest.applications.ApplicationEnums.EventKind;
import dev.jobdragonquest.applications.ApplicationEnums.EventSource;
import dev.jobdragonquest.applications.ApplicationEnums.EventStatus;

interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, UUID> {

    List<ApplicationEvent> findByApplicationIdOrderByOccurredAtAscCreatedAtAsc(UUID applicationId);

    Optional<ApplicationEvent> findByIdAndApplicationId(UUID id, UUID applicationId);

    @Query("""
            select count(e) from ApplicationEvent e, JobApplication a
            where e.applicationId = a.id and a.userId = :userId and e.source = :source and e.toState = :state""")
    long countByUserSourceAndToState(@Param("userId") UUID userId, @Param("source") EventSource source,
            @Param("state") ApplicationState state);

    @Query("""
            select e from ApplicationEvent e, JobApplication a
            where e.applicationId = a.id and a.userId = :userId and e.kind = :kind and e.status = :status
            order by e.createdAt asc""")
    List<ApplicationEvent> findByUserAndKindAndStatus(@Param("userId") UUID userId, @Param("kind") EventKind kind,
            @Param("status") EventStatus status);
}
