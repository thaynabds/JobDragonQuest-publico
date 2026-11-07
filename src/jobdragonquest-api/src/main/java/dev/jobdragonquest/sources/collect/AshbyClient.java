package dev.jobdragonquest.sources.collect;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.jobdragonquest.sources.SourceKind;
import tools.jackson.databind.JsonNode;

/** Posting API pública do Ashby: {@code GET api.ashbyhq.com/posting-api/job-board/{board}}. */
@Component
public class AshbyClient implements AtsClient {

    private final BoundedHttpFetcher fetcher;

    public AshbyClient(BoundedHttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public SourceKind kind() {
        return SourceKind.ASHBY;
    }

    @Override
    public List<CollectedJob> fetch(String identifier) {
        return parse(fetcher.get(URI.create("https://api.ashbyhq.com/posting-api/job-board/" + identifier)));
    }

    static List<CollectedJob> parse(byte[] body) {
        JsonNode jobs = AtsParsing.read(body).path("jobs");
        if (!jobs.isArray()) {
            throw new CollectionException("Resposta do Ashby sem a lista de vagas");
        }
        List<CollectedJob> result = new ArrayList<>();
        for (JsonNode job : jobs) {
            if (result.size() >= AtsParsing.MAX_JOBS) {
                break;
            }
            if (job.has("isListed") && !job.path("isListed").asBoolean(true)) {
                continue;
            }
            String title = AtsParsing.text(job, "title");
            String url = AtsParsing.text(job, "jobUrl");
            if (title == null || url == null) {
                continue;
            }
            String location = AtsParsing.text(job, "location");
            Boolean remote = job.has("isRemote") ? job.path("isRemote").asBoolean(false) : null;
            String description = AtsParsing.text(job, "descriptionPlain");
            result.add(new CollectedJob(AtsParsing.text(job, "id"), AtsParsing.limit(title, 200), url,
                    AtsParsing.limit(location, 200), null,
                    AtsParsing.workMode(AtsParsing.text(job, "workplaceType"), remote, location),
                    description != null ? AtsParsing.limit(description, AtsParsing.MAX_DESCRIPTION)
                            : AtsParsing.plain(AtsParsing.text(job, "descriptionHtml"))));
        }
        return result;
    }
}
