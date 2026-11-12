package dev.jobdragonquest.assist;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface EnvioAutomaticoRepository extends JpaRepository<EnvioAutomaticoConfig, UUID> {
}
