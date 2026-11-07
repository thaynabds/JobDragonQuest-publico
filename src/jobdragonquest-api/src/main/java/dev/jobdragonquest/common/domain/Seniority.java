package dev.jobdragonquest.common.domain;

/**
 * Senioridade, de jovem aprendiz a executivo, na ordem em que aparece nas vagas e nos perfis profissionais.
 *
 * <p>O {@code rank} agrupa níveis equivalentes para a análise de compatibilidade: estágio e aprendiz ficam no 0,
 * trainee, assistente e júnior no 1, pleno no 2, sênior no 3, especialista e liderança técnica no 4, coordenação e
 * gerência no 5, diretoria no 6 e executivo no 7. Assim a distância entre os níveis antigos (estágio, júnior, pleno,
 * sênior, liderança) continua a mesma.
 */
public enum Seniority {
    APPRENTICE(0),
    INTERN(0),
    TRAINEE(1),
    ENTRY(1),
    JUNIOR(1),
    MID(2),
    SENIOR(3),
    SPECIALIST(4),
    LEAD(4),
    COORDINATOR(5),
    MANAGER(5),
    DIRECTOR(6),
    EXECUTIVE(7);

    private final int rank;

    Seniority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    /** Quantos degraus a vaga pede acima do perfil (negativo quando o perfil está acima). */
    public int gapTo(Seniority required) {
        return required.rank - rank;
    }
}
