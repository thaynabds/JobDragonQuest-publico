package dev.jobdragonquest.assist;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Liga do envio automático de uma conta. Desligada por padrão; ligar exige o aprendizado e o aviso aceito. */
@Entity
@Table(name = "envio_automatico")
class EnvioAutomaticoConfig {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "aviso_aceito_em")
    private Instant avisoAceitoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected EnvioAutomaticoConfig() {
    }

    EnvioAutomaticoConfig(UUID userId) {
        this.userId = userId;
    }

    void definir(boolean ativo, Instant agora) {
        this.ativo = ativo;
        if (ativo) {
            this.avisoAceitoEm = agora;
        }
        this.atualizadoEm = agora;
    }

    boolean ativo() {
        return ativo;
    }
}
