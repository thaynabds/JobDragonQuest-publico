package dev.jobdragonquest.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Versão em execução e última Release, para o painel avisar quando há versão nova. */
@RestController
@RequestMapping("/api/v1/system")
class SystemVersionController {

    private final SystemVersionService versions;

    SystemVersionController(SystemVersionService versions) {
        this.versions = versions;
    }

    @GetMapping("/version")
    SystemVersionService.VersionInfo version() {
        return versions.info();
    }
}
