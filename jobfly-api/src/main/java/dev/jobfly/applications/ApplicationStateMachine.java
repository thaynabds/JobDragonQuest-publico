package dev.jobfly.applications;

import static dev.jobfly.applications.ApplicationState.APPROVED;
import static dev.jobfly.applications.ApplicationState.CLOSED;
import static dev.jobfly.applications.ApplicationState.CONFIRMED;
import static dev.jobfly.applications.ApplicationState.OFFER_RECEIVED;
import static dev.jobfly.applications.ApplicationState.REJECTED;
import static dev.jobfly.applications.ApplicationState.SUBMITTED;

import org.springframework.stereotype.Component;

import dev.jobfly.applications.ApplicationEnums.EventSource;
import dev.jobfly.applications.ApplicationState.Phase;

/**
 * Transições permitidas.
 *
 * <ul>
 *   <li>A pessoa (fonte MANUAL) pode corrigir qualquer etapa, inclusive reabrir um processo encerrado.</li>
 *   <li>Fontes automáticas (e-mail, portal, agente, sistema) só avançam e nunca tiram uma candidatura de um estado
 *       final. As etapas do processo (triagem, entrevista, teste...) podem vir em qualquer ordem, porque cada empresa
 *       organiza a seleção do seu jeito.</li>
 *   <li>Só existe reprovação depois do envio. "Encerrado" vale em qualquer momento (ex.: a vaga saiu do ar).</li>
 * </ul>
 */
@Component
public class ApplicationStateMachine {

    public boolean canTransition(ApplicationState from, ApplicationState to, EventSource source) {
        if (from == to) {
            return false;
        }
        if (source.isHuman()) {
            return true;
        }
        if (from.isFinal()) {
            return false;
        }
        if (to == CLOSED) {
            return true;
        }
        return switch (from.phase()) {
            case PREPARATION -> to.phase() == Phase.PREPARATION ? to.ordinal() > from.ordinal()
                    : to == SUBMITTED || to == CONFIRMED;
            case INTAKE -> from == SUBMITTED
                    ? to != SUBMITTED && to.phase() != Phase.PREPARATION
                    : to.phase() == Phase.PROCESS || to == OFFER_RECEIVED || to == REJECTED || to == APPROVED;
            case PROCESS -> to.phase() == Phase.PROCESS || to == OFFER_RECEIVED || to == REJECTED || to == APPROVED;
            case OFFER -> to == APPROVED || to == REJECTED;
            case FINAL -> false;
        };
    }
}
