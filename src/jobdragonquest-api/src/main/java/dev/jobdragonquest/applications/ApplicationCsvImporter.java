package dev.jobdragonquest.applications;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.jobdragonquest.applications.ApplicationDtos.CreateApplicationRequest;
import dev.jobdragonquest.applications.ApplicationDtos.CreateResult;
import dev.jobdragonquest.applications.ApplicationEnums.Origin;
import dev.jobdragonquest.common.error.DomainException;
import dev.jobdragonquest.common.text.TextNormalizer;

/**
 * Importa o histórico de candidaturas de uma planilha CSV (issue #114). Cada linha passa pela mesma criação do cadastro
 * manual, com chave de idempotência derivada da linha: importar o mesmo arquivo de novo não duplica nada, e uma
 * candidatura que já existe (mesmo link, ou mesma empresa e cargo) volta como duplicada. Linha com erro não impede as
 * outras e aparece no relatório com o número da linha.
 *
 * <p>Cabeçalho obrigatório, em português, inglês ou espanhol. Separador vírgula ou ponto e vírgula (Excel em
 * português). Aspas duplas seguem o padrão CSV.
 */
@Service
public class ApplicationCsvImporter {

    static final int MAX_ROWS = 500;
    private static final Map<String, String> COLUMNS = Map.ofEntries(
            Map.entry("empresa", "company"), Map.entry("company", "company"),
            Map.entry("cargo", "title"), Map.entry("vaga", "title"), Map.entry("title", "title"), Map.entry("puesto", "title"),
            Map.entry("link", "url"), Map.entry("url", "url"), Map.entry("enlace", "url"),
            Map.entry("plataforma", "platform"), Map.entry("platform", "platform"),
            Map.entry("data", "appliedAt"), Map.entry("data de envio", "appliedAt"), Map.entry("applied at", "appliedAt"),
            Map.entry("applied", "appliedAt"), Map.entry("date", "appliedAt"), Map.entry("fecha", "appliedAt"),
            Map.entry("etapa", "state"), Map.entry("state", "state"), Map.entry("stage", "state"), Map.entry("estado", "state"),
            Map.entry("pais", "country"), Map.entry("country", "country"),
            Map.entry("notas", "notes"), Map.entry("observacoes", "notes"), Map.entry("notes", "notes"));
    private static final Map<String, ApplicationState> STATE_WORDS = Map.ofEntries(
            Map.entry("enviada", ApplicationState.SUBMITTED), Map.entry("applied", ApplicationState.SUBMITTED),
            Map.entry("aplicada", ApplicationState.SUBMITTED), Map.entry("salva", ApplicationState.PREPARED),
            Map.entry("saved", ApplicationState.PREPARED), Map.entry("triagem", ApplicationState.SCREENING),
            Map.entry("contato", ApplicationState.RECRUITER_CONTACT), Map.entry("contacted", ApplicationState.RECRUITER_CONTACT),
            Map.entry("entrevista", ApplicationState.INTERVIEW_SCHEDULED), Map.entry("interviewing", ApplicationState.INTERVIEW_SCHEDULED),
            Map.entry("teste", ApplicationState.TECH_TEST_PENDING), Map.entry("oferta", ApplicationState.OFFER_RECEIVED),
            Map.entry("offer", ApplicationState.OFFER_RECEIVED), Map.entry("aprovada", ApplicationState.APPROVED),
            Map.entry("reprovada", ApplicationState.REJECTED), Map.entry("rejected", ApplicationState.REJECTED),
            Map.entry("encerrada", ApplicationState.CLOSED), Map.entry("closed", ApplicationState.CLOSED));

    /** Resultado da importação. */
    public record ImportResult(int created, int duplicates, List<LineError> errors) {
    }

    /** {@code line}: número da linha no arquivo (o cabeçalho é a linha 1). */
    public record LineError(int line, String code) {
    }

    private final ApplicationService applications;

    ApplicationCsvImporter(ApplicationService applications) {
        this.applications = applications;
    }

    public ImportResult importCsv(UUID userId, String csv) {
        List<List<String>> rows = parse(csv == null ? "" : csv.replace("﻿", ""));
        if (rows.isEmpty()) {
            throw DomainException.badRequest("csv_empty", "O arquivo está vazio.");
        }
        if (rows.size() - 1 > MAX_ROWS) {
            throw DomainException.badRequest("csv_too_many_rows", "Importe até " + MAX_ROWS + " linhas por vez.");
        }
        List<String> header = rows.getFirst().stream().map(h -> COLUMNS.get(TextNormalizer.normalize(h).replace('_', ' '))).toList();
        if (!header.contains("company") || !header.contains("title")) {
            throw DomainException.badRequest("csv_header", "O cabeçalho precisa ter as colunas empresa e cargo.");
        }
        int created = 0;
        int duplicates = 0;
        List<LineError> errors = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            int line = i + 1;
            try {
                CreateApplicationRequest request = toRequest(header, row);
                CreateResult result = applications.create(userId, request, "csv-" + digest(row));
                if (result.duplicate() || result.replayed()) {
                    duplicates++;
                } else {
                    created++;
                }
            } catch (DomainException ex) {
                errors.add(new LineError(line, ex.code()));
            }
        }
        return new ImportResult(created, duplicates, List.copyOf(errors));
    }

    private static CreateApplicationRequest toRequest(List<String> header, List<String> row) {
        String company = null;
        String title = null;
        String url = null;
        String platform = null;
        Instant appliedAt = null;
        ApplicationState state = ApplicationState.SUBMITTED;
        String country = null;
        String notes = null;
        for (int c = 0; c < header.size() && c < row.size(); c++) {
            String column = header.get(c);
            String value = row.get(c).strip();
            if (column == null || value.isEmpty()) {
                continue;
            }
            switch (column) {
                case "company" -> company = limit(value, 200);
                case "title" -> title = limit(value, 200);
                case "url" -> url = limit(value, 2048);
                case "platform" -> platform = limit(value, 80);
                case "appliedAt" -> appliedAt = date(value);
                case "state" -> state = state(value);
                case "country" -> country = value.toUpperCase(Locale.ROOT).matches("[A-Z]{2}") ? value.toUpperCase(Locale.ROOT) : null;
                case "notes" -> notes = limit(value, 4000);
                default -> { }
            }
        }
        if (company == null || title == null) {
            throw DomainException.badRequest("csv_missing_fields", "Empresa e cargo são obrigatórios.");
        }
        return new CreateApplicationRequest(null, null, Origin.IMPORT, platform, company, title, url, country, appliedAt, state, notes,
                null, null);
    }

    private static String limit(String value, int max) {
        if (value.length() > max) {
            throw DomainException.badRequest("csv_value_too_long", "Valor maior que " + max + " caracteres.");
        }
        return value;
    }

    private static Instant date(String value) {
        try {
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            if (value.matches("\\d{2}/\\d{2}/\\d{4}")) {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)).atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw DomainException.badRequest("csv_invalid_date", "Data inválida: use AAAA-MM-DD ou DD/MM/AAAA.");
        }
    }

    private static ApplicationState state(String value) {
        String normalized = TextNormalizer.normalize(value);
        ApplicationState word = STATE_WORDS.get(normalized);
        if (word != null) {
            return word;
        }
        try {
            return ApplicationState.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw DomainException.badRequest("csv_invalid_state", "Etapa desconhecida.");
        }
    }

    /** CSV (RFC 4180) com separador detectado no cabeçalho: vírgula ou ponto e vírgula. */
    static List<List<String>> parse(String text) {
        int firstBreak = text.indexOf('\n');
        String headerLine = firstBreak < 0 ? text : text.substring(0, firstBreak);
        char separator = count(headerLine, ';') > count(headerLine, ',') ? ';' : ',';
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"' && field.isEmpty()) {
                quoted = true;
            } else if (ch == separator) {
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                if (rows.size() > MAX_ROWS + 1) {
                    return rows;
                }
            } else {
                field.append(ch);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static int count(String text, char ch) {
        return (int) text.chars().filter(c -> c == ch).count();
    }

    private static String digest(List<String> row) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\u001f", row).strip().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
