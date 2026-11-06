package dev.jobfly.sources.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.jobfly.common.domain.WorkMode;

/** Parsers dos ATS com respostas no formato das APIs públicas, sem rede. */
class SourceParsersTest {

    private static byte[] json(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void greenhouseReadsJobsAndUnescapesHtmlDescription() {
        List<CollectedJob> jobs = GreenhouseClient.parse(json("""
                {"jobs":[
                  {"id":123,"title":"Backend Engineer (Java)","absolute_url":"https://boards.greenhouse.io/acme/jobs/123",
                   "location":{"name":"Remote - Brazil"},
                   "content":"&lt;p&gt;We use &lt;strong&gt;Java&lt;/strong&gt; &amp;amp; Spring Boot.&lt;/p&gt;&lt;ul&gt;&lt;li&gt;Kafka&lt;/li&gt;&lt;/ul&gt;"},
                  {"id":124,"title":null,"absolute_url":"https://x"},
                  {"id":125,"title":"Sem URL"}]}"""));

        assertThat(jobs).hasSize(1);
        CollectedJob job = jobs.getFirst();
        assertThat(job.externalId()).isEqualTo("123");
        assertThat(job.workMode()).isEqualTo(WorkMode.REMOTE);
        assertThat(job.description()).contains("We use Java & Spring Boot.").contains("Kafka").doesNotContain("<");
    }

    @Test
    void leverReadsWorkplaceTypeCountryAndLists() {
        List<CollectedJob> jobs = LeverClient.parse(json("""
                [{"id":"a1","text":"Desenvolvedora Java Júnior","hostedUrl":"https://jobs.lever.co/acme/a1",
                  "categories":{"location":"Recife, PE"},"workplaceType":"hybrid","country":"br",
                  "descriptionPlain":"Time de pagamentos.",
                  "lists":[{"text":"Requisitos","content":"<li>Java</li><li>PostgreSQL</li>"}]}]"""));

        CollectedJob job = jobs.getFirst();
        assertThat(job.workMode()).isEqualTo(WorkMode.HYBRID);
        assertThat(job.country()).isEqualTo("BR");
        assertThat(job.location()).isEqualTo("Recife, PE");
        assertThat(job.description()).contains("Time de pagamentos.").contains("Requisitos").contains("PostgreSQL");
    }

    @Test
    void smartRecruitersBuildsPublicUrlAndReadsRemoteFlag() {
        List<CollectedJob> jobs = SmartRecruitersClient.parse(json("""
                {"content":[{"id":"744","name":"Software Engineer","location":{"city":"São Paulo","country":"br","remote":true}}]}"""),
                "AcmeBR");

        CollectedJob job = jobs.getFirst();
        assertThat(job.url()).isEqualTo("https://jobs.smartrecruiters.com/AcmeBR/744");
        assertThat(job.workMode()).isEqualTo(WorkMode.REMOTE);
        assertThat(job.country()).isEqualTo("BR");
        assertThat(job.location()).isEqualTo("São Paulo, BR");
    }

    @Test
    void ashbySkipsUnlistedJobs() {
        List<CollectedJob> jobs = AshbyClient.parse(json("""
                {"jobs":[
                  {"id":"1","title":"Platform Engineer","jobUrl":"https://jobs.ashbyhq.com/acme/1","location":"Lisbon",
                   "workplaceType":"OnSite","descriptionPlain":"Go and Kubernetes"},
                  {"id":"2","title":"Oculta","jobUrl":"https://jobs.ashbyhq.com/acme/2","isListed":false}]}"""));

        assertThat(jobs).hasSize(1);
        assertThat(jobs.getFirst().workMode()).isEqualTo(WorkMode.ONSITE);
    }

    @Test
    void invalidOrUnexpectedResponsesFailClearly() {
        assertThatThrownBy(() -> GreenhouseClient.parse(json("não é json"))).isInstanceOf(CollectionException.class)
                .hasMessageContaining("JSON inválido");
        assertThatThrownBy(() -> GreenhouseClient.parse(json("{\"erro\":1}"))).hasMessageContaining("sem a lista");
        assertThatThrownBy(() -> LeverClient.parse(json("{}"))).hasMessageContaining("não é uma lista");
        assertThatThrownBy(() -> AshbyClient.parse(json("[]"))).hasMessageContaining("sem a lista");
    }

    @Test
    void workModeIsNeverInventedWithoutSignal() {
        assertThat(AtsParsing.workMode(null, null, "Recife, PE")).isNull();
        assertThat(AtsParsing.workMode(null, null, "Trabalho remoto")).isEqualTo(WorkMode.REMOTE);
        assertThat(AtsParsing.workMode("ON_SITE", null, null)).isEqualTo(WorkMode.ONSITE);
    }

    @Test
    void fetcherOnlyTalksToAllowedAtsHosts() {
        BoundedHttpFetcher fetcher = new BoundedHttpFetcher();
        assertThatThrownBy(() -> fetcher.get(URI.create("https://169.254.169.254/latest/meta-data")))
                .hasMessageContaining("não permitido");
        assertThatThrownBy(() -> fetcher.get(URI.create("http://boards-api.greenhouse.io/v1/boards/x/jobs")))
                .hasMessageContaining("não permitido");
        assertThatThrownBy(() -> fetcher.get(URI.create("https://user:pw@api.lever.co/v0/postings/x")))
                .hasMessageContaining("não permitido");
    }

    @Test
    void skillCatalogFindsWholeWordsOnly() {
        SkillCatalog catalog = new SkillCatalog(List.of("java", "javascript", "spring", "spring boot", "c++", "node.js"));
        assertThat(catalog.detect("Experiência com JavaScript e Node.js")).containsExactlyInAnyOrder("javascript", "node.js");
        assertThat(catalog.detect("Java 17, Spring Boot e C++")).containsExactlyInAnyOrder("java", "spring boot", "spring", "c++");
        assertThat(catalog.detect(null)).isEmpty();
        assertThat(new SkillCatalog().detect("Kotlin and Docker")).contains("kotlin", "docker");
    }

    /** Issue #32: o catálogo reconhece as grafias de uma tecnologia, mas não apelidos que são palavras comuns. */
    @Test
    void skillCatalogFindsAliasesButNotCommonWords() {
        SkillCatalog catalog = new SkillCatalog();
        assertThat(catalog.detect("Vaga para C#, .NET Core e JS no front.")).contains("csharp", "dotnet", "javascript");
        assertThat(catalog.detect("Competitive net salary and Kubernetes node pools.")).contains("kubernetes")
                .doesNotContain("dotnet", "node.js");
    }
}
