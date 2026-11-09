package dev.jobdragonquest.sources.collect;

import java.util.List;

import dev.jobdragonquest.sources.SourceKind;

/** Adaptador de um ATS com API pública de vagas. Cada implementação fala com um único host fixo. */
public interface AtsClient {

    SourceKind kind();

    /**
     * @param identifier board já validado (já codificado com {@code AtsPaths} ao montar a URL)
     * @throws CollectionException quando o ATS recusa, não responde ou devolve algo inesperado
     */
    List<CollectedJob> fetch(String identifier);
}
