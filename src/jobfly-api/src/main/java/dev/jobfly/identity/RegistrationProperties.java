package dev.jobfly.identity;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Quem pode criar conta. Vazio (padrão): qualquer e-mail, como no uso local. Num servidor público, defina
 * JOBFLY_ALLOWED_EMAILS com os e-mails permitidos (separados por vírgula) para estranhos não criarem contas.
 */
@ConfigurationProperties(prefix = "jobfly.registration")
public record RegistrationProperties(List<String> allowedEmails) {

    public RegistrationProperties {
        allowedEmails = allowedEmails == null ? List.of()
                : allowedEmails.stream().map(email -> email.strip().toLowerCase(Locale.ROOT)).filter(email -> !email.isEmpty()).toList();
    }

    public boolean allows(String normalizedEmail) {
        return allowedEmails.isEmpty() || allowedEmails.contains(normalizedEmail);
    }
}
