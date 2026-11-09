package dev.jobdragonquest.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/currency")
class CurrencyController {

    private final CurrencyService currency;

    CurrencyController(CurrencyService currency) {
        this.currency = currency;
    }

    @GetMapping("/rates")
    CurrencyService.Rates rates() {
        return currency.rates();
    }
}
