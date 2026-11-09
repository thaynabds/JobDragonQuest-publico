package dev.jobdragonquest.sources.collect;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import dev.jobdragonquest.sources.SourceKind;
import tools.jackson.databind.JsonNode;

/** Postings API pública do Lever: {@code GET api.lever.co/v0/postings/{empresa}?mode=json}. */
@Component
public class LeverClient implements AtsClient {

    private final BoundedHttpFetcher fetcher;

    public LeverClient(BoundedHttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public SourceKind kind() {
        return SourceKind.LEVER;
    }

    @Override
    public List<CollectedJob> fetch(String identifier) {
        return parse(fetcher.get(URI.create("https://api.lever.co/v0/postings/" + AtsPaths.segment(identifier) + "?mode=json")));
    }

    static List<CollectedJob> parse(byte[] body) {
        JsonNode postings = AtsParsing.read(body);
        if (!postings.isArray()) {
            throw new CollectionException("Resposta do Lever não é uma lista de vagas");
        }
        List<CollectedJob> result = new ArrayList<>();
        for (JsonNode posting : postings) {
            if (result.size() >= AtsParsing.MAX_JOBS) {
                break;
            }
            String title = AtsParsing.text(posting, "text");
            String url = AtsParsing.text(posting, "hostedUrl");
            if (title == null || url == null) {
                continue;
            }
            JsonNode categories = posting.path("categories");
            String location = AtsParsing.text(categories, "location");
            String country = AtsParsing.text(posting, "country");
            StringBuilder description = new StringBuilder();
            String plain = AtsParsing.text(posting, "descriptionPlain");
            if (plain != null) {
                description.append(plain);
            }
            for (JsonNode list : posting.path("lists")) {
                String heading = AtsParsing.text(list, "text");
                String content = AtsParsing.plain(AtsParsing.text(list, "content"));
                if (content != null) {
                    description.append("\n\n").append(heading == null ? "" : heading + "\n").append(content);
                }
            }
            result.add(new CollectedJob(AtsParsing.text(posting, "id"), AtsParsing.limit(title, 200), url,
                    AtsParsing.limit(location, 200),
                    country != null && country.length() == 2 ? country.toUpperCase(Locale.ROOT) : null,
                    AtsParsing.workMode(AtsParsing.text(posting, "workplaceType"), null, location),
                    AtsParsing.limit(description.toString(), AtsParsing.MAX_DESCRIPTION)));
        }
        return result;
    }
}
