package dev.jobfly.applications;

import static dev.jobfly.applications.ApplicationState.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.jobfly.applications.ApplicationEnums.EventSource;

class ApplicationStateMachineTest {

    private final ApplicationStateMachine machine = new ApplicationStateMachine();

    @ParameterizedTest
    @EnumSource(value = EventSource.class, names = "MANUAL", mode = EnumSource.Mode.EXCLUDE)
    void automaticSourcesNeverLeaveFinalStates(EventSource source) {
        for (ApplicationState fin : EnumSet.of(APPROVED, REJECTED, CLOSED)) {
            for (ApplicationState target : ApplicationState.values()) {
                assertThat(machine.canTransition(fin, target, source)).as(fin + "->" + target).isFalse();
            }
        }
    }

    @Test
    void personCanCorrectAndReopen() {
        assertThat(machine.canTransition(REJECTED, SCREENING, EventSource.MANUAL)).isTrue();
        assertThat(machine.canTransition(INTERVIEW_DONE, SUBMITTED, EventSource.MANUAL)).isTrue();
        assertThat(machine.canTransition(SUBMITTED, SUBMITTED, EventSource.MANUAL)).isFalse();
    }

    @Test
    void processStagesCanComeInAnyOrder() {
        assertThat(machine.canTransition(TECH_TEST_DONE, INTERVIEW_SCHEDULED, EventSource.EMAIL)).isTrue();
        assertThat(machine.canTransition(INTERVIEW_DONE, TECH_TEST_PENDING, EventSource.EMAIL)).isTrue();
        assertThat(machine.canTransition(SCREENING, APPROVED, EventSource.EMAIL)).isTrue();
    }

    @Test
    void automaticSourcesDoNotGoBackToIntakeOrPreparation() {
        assertThat(machine.canTransition(SCREENING, CONFIRMED, EventSource.EMAIL)).isFalse();
        assertThat(machine.canTransition(CONFIRMED, SUBMITTED, EventSource.EMAIL)).isFalse();
        assertThat(machine.canTransition(SUBMITTED, PREPARED, EventSource.AGENT)).isFalse();
        assertThat(machine.canTransition(PREPARED, JOB_FOUND, EventSource.AGENT)).isFalse();
    }

    @Test
    void preparationAdvancesUntilSubmissionOrConfirmation() {
        assertThat(machine.canTransition(JOB_FOUND, RECOMMENDED, EventSource.AGENT)).isTrue();
        assertThat(machine.canTransition(AWAITING_USER, SUBMITTED, EventSource.SYSTEM)).isTrue();
        assertThat(machine.canTransition(PREPARED, CONFIRMED, EventSource.EMAIL)).isTrue();
        // Não existe reprovação de algo que nunca foi enviado.
        assertThat(machine.canTransition(PREPARED, REJECTED, EventSource.EMAIL)).isFalse();
        assertThat(machine.canTransition(PREPARED, CLOSED, EventSource.SYSTEM)).isTrue();
    }

    @Test
    void offerOnlyEndsInDecision() {
        assertThat(machine.canTransition(OFFER_RECEIVED, APPROVED, EventSource.EMAIL)).isTrue();
        assertThat(machine.canTransition(OFFER_RECEIVED, REJECTED, EventSource.EMAIL)).isTrue();
        assertThat(machine.canTransition(OFFER_RECEIVED, SCREENING, EventSource.EMAIL)).isFalse();
        assertThat(machine.canTransition(SUBMITTED, OFFER_RECEIVED, EventSource.EMAIL)).isTrue();
    }

    @Test
    void phasesAreConsistent() {
        assertThat(SUBMITTED.isActive()).isTrue();
        assertThat(OFFER_RECEIVED.isActive()).isTrue();
        assertThat(JOB_FOUND.isActive()).isFalse();
        assertThat(CLOSED.isActive()).isFalse();
        assertThat(CLOSED.isFinal()).isTrue();
    }
}
