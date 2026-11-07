package dev.jobdragonquest.common.persistence;

import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

/**
 * Base das entidades cujo id (UUID) é gerado no Java.
 *
 * <p>Sem isto, o Spring Data veria o id preenchido e chamaria {@code merge()} em vez de {@code persist()}. O efeito
 * seria um SELECT extra em cada inserção, uma cópia gerenciada diferente do objeto original (as mudanças seguintes se
 * perderiam) e um incremento espúrio do {@code @Version}.
 */
@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<UUID> {

    @Transient
    private boolean newEntity = true;

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostPersist
    @PostLoad
    void markPersisted() {
        newEntity = false;
    }
}
