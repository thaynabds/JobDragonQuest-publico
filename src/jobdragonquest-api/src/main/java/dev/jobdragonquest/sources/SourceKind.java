package dev.jobdragonquest.sources;

/**
 * Tipos de fonte vigiada. O modo de coleta vem da realidade de cada plataforma (docs/04-integracoes.md):
 * <ul>
 *   <li>{@link CollectionMode#AUTOMATIC}: API pública e documentada de vagas. O JobDragonQuest busca sozinho.</li>
 *   <li>{@link CollectionMode#ASSISTED}: a plataforma não permite coleta automática (termos de uso ou falta de API).
 *       O JobDragonQuest lembra de conferir na frequência escolhida, abre o link e recebe as vagas que a pessoa registra.</li>
 * </ul>
 */
public enum SourceKind {
    GREENHOUSE(CollectionMode.AUTOMATIC, "Greenhouse", true),
    LEVER(CollectionMode.AUTOMATIC, "Lever", true),
    SMARTRECRUITERS(CollectionMode.AUTOMATIC, "SmartRecruiters", true),
    ASHBY(CollectionMode.AUTOMATIC, "Ashby", true),
    LINKEDIN(CollectionMode.ASSISTED, "LinkedIn", false),
    INSTAGRAM(CollectionMode.ASSISTED, "Instagram", false),
    FACEBOOK(CollectionMode.ASSISTED, "Facebook", false),
    INDEED(CollectionMode.ASSISTED, "Indeed", false),
    GLASSDOOR(CollectionMode.ASSISTED, "Glassdoor", false),
    GUPY(CollectionMode.ASSISTED, "Gupy", false),
    WORKDAY(CollectionMode.ASSISTED, "Workday", false),
    CAREER_PAGE(CollectionMode.ASSISTED, "Página de carreiras", false),
    OTHER(CollectionMode.ASSISTED, "Outro site ou rede", false);

    public enum CollectionMode {
        AUTOMATIC,
        ASSISTED
    }

    /**
     * Identificador do board no ATS (ex.: "nubank" em boards.greenhouse.io/nubank). Aceita qualquer caractere, inclusive
     * emojis. Fica de fora só o que não é texto (caractere de controle). Ele vai sempre codificado como um único segmento
     * de caminho de um host fixo ({@code AtsPaths}), então não muda o destino da requisição.
     */
    static boolean validIdentifier(String id) {
        return !id.isEmpty() && id.length() <= 100 && !id.equals(".") && !id.equals("..")
                && id.chars().noneMatch(Character::isISOControl);
    }

    private final CollectionMode mode;
    private final String label;
    private final boolean needsIdentifier;

    SourceKind(CollectionMode mode, String label, boolean needsIdentifier) {
        this.mode = mode;
        this.label = label;
        this.needsIdentifier = needsIdentifier;
    }

    public CollectionMode mode() {
        return mode;
    }

    public String label() {
        return label;
    }

    /** Fontes automáticas são identificadas pelo board. As assistidas, pela URL que a pessoa vai conferir. */
    public boolean needsIdentifier() {
        return needsIdentifier;
    }
}
