package dev.jobdragonquest.profiles;

/** Valores fechados usados no perfil profissional. */
public final class ProfileEnums {

    private ProfileEnums() {
    }

    /** Tipos de contratação e de jornada aceitos. */
    public enum EmploymentType {
        CLT,
        PJ,
        FULL_TIME,
        PART_TIME,
        INTERNSHIP,
        APPRENTICE,
        TRAINEE,
        TEMPORARY,
        FREELANCE,
        VOLUNTEER
    }

    /**
     * O que a coleta automática faz com uma vaga compatível com o perfil. Nunca envia candidatura a site nenhum: o
     * envio é sempre da pessoa.
     */
    public enum AutomationMode {
        /** Só guarda a vaga, sem criar candidatura. */
        OFF,
        /** Cria a candidatura como recomendada, na fila de revisão. */
        REVIEW,
        /** Cria a candidatura já como preparada, pronta para a pessoa enviar. */
        AUTOMATIC
    }

    public enum TravelAvailability {
        NONE,
        OCCASIONAL,
        FREQUENT
    }

    public enum SalaryPeriod {
        HOURLY,
        MONTHLY,
        YEARLY
    }

    /** Grau da formação acadêmica. */
    public enum Degree {
        HIGH_SCHOOL,
        TECHNICAL,
        TECHNOLOGIST,
        BACHELOR,
        LICENTIATE,
        POSTGRADUATE,
        MBA,
        MASTER,
        DOCTORATE,
        POSTDOC,
        OTHER
    }

    /** Situação de uma formação ou curso. */
    public enum EntryStatus {
        IN_PROGRESS,
        COMPLETED,
        INCOMPLETE,
        PAUSED
    }
}
