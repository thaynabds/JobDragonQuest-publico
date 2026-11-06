package dev.jobfly.sources.collect;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.jobfly.sources.SourceKind;
import tools.jackson.databind.JsonNode;

/** Job Board API pública do Greenhouse: {@code GET boards-api.greenhouse.io/v1/boards/{board}/jobs?content=true}. */
@Component
public class GreenhouseClient implements AtsClient {

    private final BoundedHttpFetcher fetcher;

    public GreenhouseClient(BoundedHttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public SourceKind kind() {
        return SourceKind.GREENHOUSE;
    }

    @Override
    public List<CollectedJob> fetch(String identifier) {
        return parse(fetcher.get(URI.create("https://boards-api.greenhouse.io/v1/boards/" + identifier + "/jobs?content=true")));
    }

    static List<CollectedJob> parse(byte[] body) {
        JsonNode jobs = AtsParsing.read(body).path("jobs");
        if (!jobs.isArray()) {
            throw new CollectionException("Resposta do Greenhouse sem a lista de vagas");
        }
        List<CollectedJob> result = new ArrayList<>();
        for (JsonNode job : jobs) {
            if (result.size() >= AtsParsing.MAX_JOBS) {
                break;
            }
            String title = AtsParsing.text(job, "title");
            String url = AtsParsing.text(job, "absolute_url");
            if (title == null || url == null) {
                continue;
            }
            String location = AtsParsing.text(job.path("location"), "name");
            result.add(new CollectedJob(AtsParsing.text(job, "id"), AtsParsing.limit(title, 200), url,
                    AtsParsing.limit(location, 200), null, AtsParsing.workMode(null, null, location),
                    AtsParsing.plain(AtsParsing.text(job, "content"))));
        }
        return result;
    }
}
