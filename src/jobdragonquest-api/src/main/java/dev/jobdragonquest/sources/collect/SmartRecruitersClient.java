package dev.jobdragonquest.sources.collect;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import dev.jobdragonquest.sources.SourceKind;
import tools.jackson.databind.JsonNode;

/**
 * Posting API pública do SmartRecruiters: {@code GET api.smartrecruiters.com/v1/companies/{empresa}/postings}.
 * A listagem não traz a descrição, então a análise usa o cargo até a vaga ser aberta.
 */
@Component
public class SmartRecruitersClient implements AtsClient {

    private final BoundedHttpFetcher fetcher;

    public SmartRecruitersClient(BoundedHttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public SourceKind kind() {
        return SourceKind.SMARTRECRUITERS;
    }

    @Override
    public List<CollectedJob> fetch(String identifier) {
        return parse(fetcher.get(URI.create("https://api.smartrecruiters.com/v1/companies/" + AtsPaths.segment(identifier) + "/postings?limit=100")),
                identifier);
    }

    static List<CollectedJob> parse(byte[] body, String identifier) {
        JsonNode content = AtsParsing.read(body).path("content");
        if (!content.isArray()) {
            throw new CollectionException("Resposta do SmartRecruiters sem a lista de vagas");
        }
        List<CollectedJob> result = new ArrayList<>();
        for (JsonNode posting : content) {
            if (result.size() >= AtsParsing.MAX_JOBS) {
                break;
            }
            String id = AtsParsing.text(posting, "id");
            String title = AtsParsing.text(posting, "name");
            if (id == null || title == null) {
                continue;
            }
            JsonNode location = posting.path("location");
            String city = AtsParsing.text(location, "city");
            String country = AtsParsing.text(location, "country");
            boolean remote = location.path("remote").asBoolean(false);
            String place = city == null ? country : (country == null ? city : city + ", " + country.toUpperCase(Locale.ROOT));
            result.add(new CollectedJob(id, AtsParsing.limit(title, 200),
                    "https://jobs.smartrecruiters.com/" + identifier + "/" + id, AtsParsing.limit(place, 200),
                    country != null && country.length() == 2 ? country.toUpperCase(Locale.ROOT) : null,
                    AtsParsing.workMode(null, remote, place), null));
        }
        return result;
    }
}
