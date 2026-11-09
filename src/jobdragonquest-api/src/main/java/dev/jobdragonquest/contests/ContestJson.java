package dev.jobdragonquest.contests;

import java.util.Arrays;
import java.util.List;

import dev.jobdragonquest.contests.ContestDtos.Stage;
import tools.jackson.databind.json.JsonMapper;

/** Etapas do concurso em JSON já validado pela API. */
final class ContestJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ContestJson() {
    }

    static String write(List<Stage> stages) {
        return JSON.writeValueAsString(stages);
    }

    static List<Stage> read(String stages) {
        return Arrays.asList(JSON.readValue(stages, Stage[].class));
    }
}
