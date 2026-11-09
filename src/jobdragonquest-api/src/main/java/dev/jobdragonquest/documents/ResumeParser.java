package dev.jobdragonquest.documents;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.jobdragonquest.profiles.ProfileEntry;

/**
 * Lê um currículo em texto e propõe os itens por seção (experiência, formação, cursos, projetos). Só extrai o que está
 * escrito no arquivo, e a pessoa confere e escolhe o que entra no perfil. Texto que não parece currículo volta null.
 */
final class ResumeParser {

    record Item(ProfileEntry.Kind kind, String title, String organization, LocalDate startDate, LocalDate endDate, boolean current,
            String description) {
    }

    record Proposal(String email, String phone, String summary, List<String> skillLines, List<String> languages, List<Item> items) {
    }

    private static final int MAX_ITEMS = 150;
    private static final Map<String, Integer> MONTHS = Map.ofEntries(Map.entry("jan", 1), Map.entry("fev", 2), Map.entry("feb", 2),
            Map.entry("mar", 3), Map.entry("abr", 4), Map.entry("apr", 4), Map.entry("mai", 5), Map.entry("may", 5), Map.entry("jun", 6),
            Map.entry("jul", 7), Map.entry("ago", 8), Map.entry("aug", 8), Map.entry("set", 9), Map.entry("sep", 9), Map.entry("out", 10),
            Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dez", 12), Map.entry("dec", 12));
    private static final String MONTH = "(?:jan|fev|feb|mar|abr|apr|mai|may|jun|jul|ago|aug|set|sep|out|oct|nov|dez|dec)[a-zçã]*\\.?";
    private static final Pattern DATE = Pattern.compile("(?i)(?<![\\p{L}\\d])(?:(" + MONTH + ")\\s*(?:/|de|\\s)\\s*)?((?:19|20)\\d{2})(?!\\d)");
    private static final Pattern BARE_MONTH = Pattern.compile("(?i)(?<![\\p{L}])(" + MONTH + ")\\s*[–—-]\\s*" + MONTH + "\\s*/?\\s*(?:de\\s*)?((?:19|20)\\d{2})");
    private static final Pattern PERIOD = Pattern.compile("(?i)(?<![\\p{L}\\d])(?:\\d{1,2}\\s*(?:a|-|–)\\s*\\d{1,2}/\\d{1,2}/\\d{4}|(?:" + MONTH
            + "\\s*(?:/|de|\\s)?\\s*)?(?:(?:19|20)\\d{2})?\\s*(?:[–—-]|\\ba\\b|\\baté\\b|\\bto\\b)\\s*(?:" + MONTH + "\\s*(?:/|de|\\s)?\\s*)?(?:(?:19|20)\\d{2}|atual|present|o momento)"
            + "|" + MONTH + "\\s*(?:/|de|\\s)\\s*(?:19|20)\\d{2}|(?:19|20)\\d{2})(?![\\d/]|\\.\\d)");
    private static final Pattern FULL_DATE = Pattern.compile("\\b\\d{1,2}/(\\d{1,2})/((?:19|20)\\d{2})\\b");
    private static final Pattern CURRENT = Pattern.compile("(?i)atual|present|em andamento|o momento|in progress|current");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern PHONE = Pattern.compile("(?:\\+?\\d{1,3}[\\s-]?)?\\(?\\d{2}\\)?[\\s-]?9?\\d{4}[\\s-]?\\d{4}");

    private enum Section { SUMMARY, EXPERIENCE, EDUCATION, COURSE, PROJECT, SKILLS, LANGUAGES, OTHER }

    private static final Map<String, Section> HEADINGS = Map.ofEntries(
            Map.entry("resumoprofissional", Section.SUMMARY), Map.entry("resumo", Section.SUMMARY), Map.entry("sobremim", Section.SUMMARY),
            Map.entry("objetivo", Section.SUMMARY), Map.entry("summary", Section.SUMMARY), Map.entry("professionalsummary", Section.SUMMARY),
            Map.entry("about", Section.SUMMARY), Map.entry("aboutme", Section.SUMMARY),
            Map.entry("experienciaprofissional", Section.EXPERIENCE), Map.entry("experiencia", Section.EXPERIENCE), Map.entry("experiencias", Section.EXPERIENCE),
            Map.entry("professionalexperience", Section.EXPERIENCE), Map.entry("workexperience", Section.EXPERIENCE), Map.entry("experience", Section.EXPERIENCE),
            Map.entry("formacaoacademica", Section.EDUCATION), Map.entry("formacao", Section.EDUCATION), Map.entry("education", Section.EDUCATION),
            Map.entry("academicbackground", Section.EDUCATION),
            Map.entry("cursosecertificacoes", Section.COURSE), Map.entry("cursos", Section.COURSE), Map.entry("certificacoes", Section.COURSE),
            Map.entry("licencasecertificados", Section.COURSE), Map.entry("outroscursos", Section.COURSE), Map.entry("courses", Section.COURSE),
            Map.entry("certifications", Section.COURSE), Map.entry("coursesandcertifications", Section.COURSE),
            Map.entry("projetosemdestaque", Section.PROJECT), Map.entry("projetos", Section.PROJECT), Map.entry("outrosprojetoseexercicios", Section.PROJECT),
            Map.entry("projects", Section.PROJECT), Map.entry("featuredprojects", Section.PROJECT), Map.entry("otherprojectsandexercises", Section.PROJECT),
            Map.entry("tecnologiasprogramaseferramentas", Section.SKILLS), Map.entry("habilidades", Section.SKILLS), Map.entry("habilidadestecnicas", Section.SKILLS),
            Map.entry("hardskills", Section.SKILLS), Map.entry("competencias", Section.SKILLS), Map.entry("skills", Section.SKILLS),
            Map.entry("technicalskills", Section.SKILLS), Map.entry("technologiesprogramsandtools", Section.SKILLS),
            Map.entry("idiomas", Section.LANGUAGES), Map.entry("languages", Section.LANGUAGES),
            Map.entry("softskills", Section.OTHER), Map.entry("competenciascomportamentais", Section.OTHER), Map.entry("voluntariado", Section.OTHER),
            Map.entry("referencias", Section.OTHER), Map.entry("competenciascomportamentaissoftskills", Section.OTHER), Map.entry("behavioralskillssoftskills", Section.OTHER),
            Map.entry("palavraschaveats", Section.OTHER), Map.entry("keywordsats", Section.OTHER), Map.entry("volunteering", Section.OTHER), Map.entry("volunteerwork", Section.OTHER),
            Map.entry("habilidadestecnicashardskills", Section.SKILLS), Map.entry("technicalskillshardskills", Section.SKILLS), Map.entry("techstack", Section.SKILLS),
            Map.entry("stackeferramentas", Section.SKILLS), Map.entry("tecnologias", Section.SKILLS), Map.entry("workhistory", Section.EXPERIENCE), Map.entry("employment", Section.EXPERIENCE), Map.entry("references", Section.OTHER));

    private ResumeParser() {
    }

    static Proposal parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Map<Section, List<String>> sections = new java.util.EnumMap<>(Section.class);
        Section current = null;
        int found = 0;
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Section heading = line.length() > 90 ? null : HEADINGS.get(normalize(line));
            if (heading != null) {
                current = heading;
                found++;
                continue;
            }
            if (current != null) {
                sections.computeIfAbsent(current, key -> new ArrayList<>()).add(line);
            }
        }
        if (found < 2) {
            return null;
        }
        List<Item> items = new ArrayList<>();
        entries(sections.get(Section.EXPERIENCE), ProfileEntry.Kind.EXPERIENCE, items);
        entries(sections.get(Section.EDUCATION), ProfileEntry.Kind.EDUCATION, items);
        entries(sections.get(Section.COURSE), ProfileEntry.Kind.COURSE, items);
        entries(sections.get(Section.PROJECT), ProfileEntry.Kind.PROJECT, items);
        Matcher email = EMAIL.matcher(text);
        Matcher phone = PHONE.matcher(text);
        return new Proposal(email.find() ? email.group() : null, phone.find() ? phone.group().strip() : null,
                limit(String.join(" ", sections.getOrDefault(Section.SUMMARY, List.of())), 4000),
                List.copyOf(sections.getOrDefault(Section.SKILLS, List.of())), List.copyOf(sections.getOrDefault(Section.LANGUAGES, List.of())),
                items.size() > MAX_ITEMS ? List.copyOf(items.subList(0, MAX_ITEMS)) : List.copyOf(items));
    }

    /** Título sem espaços, emoji e acento: "P R O J E T O S E M D E S TA Q U E" e "🚀 Projetos em destaque" ficam iguais. */
    static String normalize(String line) {
        String plain = Normalizer.normalize(line, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return plain.replace("&", "and").replaceAll("[^a-z]+", "");
    }

    private static void entries(List<String> lines, ProfileEntry.Kind kind, List<Item> out) {
        if (lines == null) {
            return;
        }
        List<String> pending = new ArrayList<>();
        Builder open = null;
        for (String line : lines) {
            // O que está entre parênteses é detalhe ("em andamento, desde 03/11"), e linha longa é descrição, não cabeçalho.
            String visible = line.replaceAll("\\([^)]*\\)?", " ").replaceAll("^[^(]*\\)\\s*$", "").strip();
            Matcher period = PERIOD.matcher(visible);
            boolean piped = visible.contains("|");
            if (visible.isEmpty() || (visible.length() > 120 && !piped) || !period.find()) {
                pending.add(line);
                continue;
            }
            String rest = (visible.substring(0, period.start()) + visible.substring(period.end())).replaceAll("(?i)\\b(previsão|conclusão|expected|concluído em)\\s*:?", " ");
            if (!piped && rest.strip().endsWith(".")) {
                pending.add(line);
                continue;
            }
            String header = visible + (CURRENT.matcher(line).find() ? " atual" : "");
            if (piped && pending.isEmpty() && open != null && open.organization == null) {
                // Linha de cabeçalho em duas partes: "Curso  Previsão: 2027" e depois "Instituição | Cidade | período".
                open = open.withOrganization(tidy(rest.split("\\|")[0]), header);
                continue;
            }
            List<String> parts = java.util.Arrays.stream(rest.split("\\|")).map(ResumeParser::tidy).filter(part -> !part.isEmpty()).toList();
            String title;
            String organization = null;
            if (piped || parts.isEmpty()) {
                if (pending.isEmpty() && parts.size() < 2) {
                    pending.add(line);
                    continue;
                }
                if (pending.isEmpty()) {
                    title = parts.get(0);
                    organization = parts.get(1);
                } else {
                    title = tidy(pending.remove(pending.size() - 1));
                    organization = parts.isEmpty() ? null : parts.get(0);
                }
            } else {
                title = parts.get(0);
            }
            if (open != null) {
                open.description.addAll(pending);
                open.finish(out);
            }
            pending.clear();
            open = new Builder(kind, title, organization, header);
        }
        if (open != null) {
            open.description.addAll(pending);
            open.finish(out);
        }
    }

    private static String tidy(String value) {
        return value.replaceAll("^[^\\p{L}\\p{N}]+", "").replaceAll("[\\s,·|–—-]+$", "").strip();
    }

    private static String limit(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static final class Builder {
        final ProfileEntry.Kind kind;
        final String title;
        final String organization;
        final String periodLine;
        final List<String> description = new ArrayList<>();

        Builder withOrganization(String value, String line) {
            Builder merged = new Builder(kind, title, value, line + " " + periodLine);
            merged.description.addAll(description);
            return merged;
        }

        Builder(ProfileEntry.Kind kind, String title, String organization, String periodLine) {
            this.kind = kind;
            this.title = title;
            this.organization = organization;
            this.periodLine = periodLine;
        }

        void finish(List<Item> out) {
            if (title.isEmpty()) {
                return;
            }
            List<LocalDate> dates = dates(periodLine);
            boolean current = CURRENT.matcher(periodLine).find();
            LocalDate start = dates.isEmpty() ? null : dates.get(0);
            LocalDate end = dates.size() > 1 ? dates.get(dates.size() - 1) : current ? null : start;
            if (dates.size() == 1 && !current && kind != ProfileEntry.Kind.EXPERIENCE) {
                start = null;
            }
            out.add(new Item(kind, limit(title, 200), limit(organization, 200), start, end, current, limit(String.join("\n", description), 4000)));
        }
    }

    private static List<LocalDate> dates(String line) {
        List<LocalDate> result = new ArrayList<>();
        Matcher bare = BARE_MONTH.matcher(line);
        if (bare.find()) {
            result.add(date(bare.group(1), bare.group(2)));
        }
        String rest = line.replaceAll("\\d{4}\\.\\d", " ");
        Matcher full = FULL_DATE.matcher(rest);
        if (full.find()) {
            int month = Integer.parseInt(full.group(1));
            if (month >= 1 && month <= 12) {
                result.add(LocalDate.of(Integer.parseInt(full.group(2)), month, 1));
                return result;
            }
        }
        Matcher matcher = DATE.matcher(rest);
        while (matcher.find()) {
            result.add(date(matcher.group(1), matcher.group(2)));
        }
        return result;
    }

    private static LocalDate date(String month, String year) {
        int number = month == null ? 1 : MONTHS.getOrDefault(month.toLowerCase(Locale.ROOT).substring(0, 3), 1);
        return LocalDate.of(Integer.parseInt(year), number, 1);
    }
}
