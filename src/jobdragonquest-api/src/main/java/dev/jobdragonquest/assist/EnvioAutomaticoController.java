package dev.jobdragonquest.assist;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.common.security.CurrentUser;

/**
 * Estado e liga do envio automático. POST nos dois: a extensão só tem POST liberado no CORS. A extensão chama daqui.
 */
@RestController
@RequestMapping("/api/v1/assist/envio-automatico")
class EnvioAutomaticoController {

    private final EnvioAutomaticoService service;
    private final CurrentUser currentUser;

    EnvioAutomaticoController(EnvioAutomaticoService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/estado")
    EnvioAutomaticoService.Estado estado() {
        return service.estado(currentUser.id());
    }

    @PostMapping
    EnvioAutomaticoService.Estado definir(@Valid @RequestBody Pedido pedido) {
        return service.definir(currentUser.id(), pedido.ativo(), pedido.avisoAceito());
    }

    record Pedido(boolean ativo, boolean avisoAceito) {
    }
}
