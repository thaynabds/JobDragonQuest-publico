package dev.jobdragonquest.assist;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jobdragonquest.applications.ApplicationService;
import dev.jobdragonquest.common.error.DomainException;

/**
 * Envio automático pela extensão. Só liga depois de {@link #LIMITE_APRENDIZADO} envios que a pessoa confirmou com um
 * clique (é assim que a IA aprende a preencher) e com o aviso aceito. O servidor confere tudo: a tela não basta.
 */
@Service
public class EnvioAutomaticoService {

    /** Envios com um clique confirmados antes de liberar o automático. */
    public static final int LIMITE_APRENDIZADO = 10;

    /** {@code liberado}: o aprendizado chegou ao limite. {@code ativo}: a pessoa ligou o automático. */
    public record Estado(long confirmados, int limite, boolean liberado, boolean ativo) {
    }

    private final EnvioAutomaticoRepository configs;
    private final ApplicationService applications;

    EnvioAutomaticoService(EnvioAutomaticoRepository configs, ApplicationService applications) {
        this.configs = configs;
        this.applications = applications;
    }

    @Transactional(readOnly = true)
    public Estado estado(UUID userId) {
        long confirmados = applications.confirmedExtensionSubmissions(userId);
        boolean ativo = configs.findById(userId).map(EnvioAutomaticoConfig::ativo).orElse(false);
        return new Estado(confirmados, LIMITE_APRENDIZADO, confirmados >= LIMITE_APRENDIZADO, ativo);
    }

    /** Liga ou desliga. Ligar sem aviso aceito ou antes do aprendizado é recusado; desligar sempre é permitido. */
    @Transactional
    public Estado definir(UUID userId, boolean ativo, boolean avisoAceito) {
        if (ativo) {
            if (!avisoAceito) {
                throw DomainException.badRequest("aviso_nao_aceito", "Leia e aceite o aviso antes de ligar o envio automático.");
            }
            if (applications.confirmedExtensionSubmissions(userId) < LIMITE_APRENDIZADO) {
                throw DomainException.conflict("aprendizado_incompleto",
                        "Antes, confirme " + LIMITE_APRENDIZADO + " envios com um clique para a IA aprender a preencher.");
            }
        }
        EnvioAutomaticoConfig config = configs.findById(userId).orElseGet(() -> new EnvioAutomaticoConfig(userId));
        config.definir(ativo, Instant.now());
        configs.save(config);
        return estado(userId);
    }

    /** Recusa o envio automático se a conta não o tem ligado e liberado. */
    @Transactional(readOnly = true)
    public void exigirLigado(UUID userId) {
        Estado estado = estado(userId);
        if (!estado.ativo() || !estado.liberado()) {
            throw DomainException.conflict("automatico_desligado", "O envio automático não está ligado nesta conta.");
        }
    }
}
