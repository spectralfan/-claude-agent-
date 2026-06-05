package com.kama.jchatmind.service.impl;

import com.kama.jchatmind.memory.config.MemoryProperties;
import com.kama.jchatmind.mapper.ChunkBgeM3Mapper;
import com.kama.jchatmind.model.entity.ChunkBgeM3;
import com.kama.jchatmind.service.RagService;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class RagServiceImpl implements RagService {

    /** bge-m3 经 Ollama 嵌入时过长文本会 500，保守截断 */
    private static final int MAX_EMBED_CHARS = 4000;

    // 封装本地的模型调用
    private final WebClient webClient;
    private final ChunkBgeM3Mapper chunkBgeM3Mapper;

    public RagServiceImpl(WebClient.Builder builder,
                          ChunkBgeM3Mapper chunkBgeM3Mapper,
                          MemoryProperties memoryProperties) {
        String baseUrl = memoryProperties.getOllamaBaseUrl();
        this.webClient = builder.baseUrl(baseUrl).build();
        this.chunkBgeM3Mapper = chunkBgeM3Mapper;
        this.embeddingModel = memoryProperties.getEmbeddingModel();
    }

    private final String embeddingModel;

    @Data
    private static class EmbeddingResponse {
        private float[] embedding;
    }

    private float[] doEmbed(String text) {
        EmbeddingResponse resp = webClient.post()
                .uri("/api/embeddings")
                .bodyValue(Map.of(
                        "model", embeddingModel,
                        "prompt", text
                ))
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .block();
        Assert.notNull(resp, "Embedding response cannot be null");
        return resp.getEmbedding();
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        if (text.length() > MAX_EMBED_CHARS) {
            text = text.substring(0, MAX_EMBED_CHARS);
        }
        return doEmbed(text);
    }

    @Override
    public List<String> similaritySearch(String kbId, String title) {
        String queryEmbedding = toPgVector(doEmbed(title));
        List<ChunkBgeM3> chunks = chunkBgeM3Mapper.similaritySearch(kbId, queryEmbedding, 3);
        return chunks.stream().map(ChunkBgeM3::getContent).toList();
    }

    private String toPgVector(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            sb.append(v[i]);
            if (i < v.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
