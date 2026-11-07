package dev.jobdragonquest.dashboard;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jobdragonquest.common.security.CurrentUser;
import dev.jobdragonquest.dashboard.DashboardService.PendingItem;
import dev.jobdragonquest.dashboard.DashboardService.Summary;

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
