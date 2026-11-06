package dev.jobfly.dashboard;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jobfly.common.security.CurrentUser;
import dev.jobfly.dashboard.DashboardService.PendingItem;
import dev.jobfly.dashboard.DashboardService.Summary;

@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController {

    private final DashboardService dashboard;
    private final CurrentUser currentUser;

    DashboardController(DashboardService dashboard, CurrentUser currentUser) {
        this.dashboard = dashboard;
        this.currentUser = currentUser;
    }

    @GetMapping("/summary")
    Summary summary() {
        return dashboard.summary(currentUser.id());
    }

    @GetMapping("/pending")
    List<PendingItem> pending() {
        return dashboard.pending(currentUser.id());
    }
}
