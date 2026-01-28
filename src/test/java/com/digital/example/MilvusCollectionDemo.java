package com.digital.example;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import java.util.List;

public class MilvusCollectionDemo {

    public static void main(String[] args) throws InterruptedException {
        //1. 创建EmbeddingModel
        EmbeddingModel model = embeddingModel();

        //2. 创建EmbeddingStore
        MilvusEmbeddingStore store = embeddingStore();

        // 3. 插入演示数据
        List<String> texts = List.of(
                "LangChain4j 支持与 Milvus 向量数据库集成",
                "Milvus 是高性能的开源向量数据库",
                "混合搜索结合稠密向量和稀疏向量提升检索效果"
        );

        for (String text : texts) {
            TextSegment segment = TextSegment.from(text);
            Embedding embedding = model.embed(segment).content();
            store.add(embedding, segment); // 修复5：直接添加无需sleep [7](@ref)
            Thread.sleep(9000);
        }
        System.out.println("✅ 数据插入完成");

        // 查询示例（修复6：使用新的Search API）
        String query = "如何在LangChain中使用向量数据库？";
        Embedding queryEmbedding = model.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(2)
                .build();

        EmbeddingSearchResult<TextSegment> result = store.search(request);
        System.out.println("\n  查询: \"" + query + "\"");
        result.matches().forEach(match ->
                System.out.printf(" - 相似度: %.2f%% | 内容: %s%n",
                        match.score() * 100,
                        match.embedded().text())
        );
    }

    public static EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    public static MilvusEmbeddingStore embeddingStore() {
        return MilvusEmbeddingStore.builder()
                .host("https://in03-52688eeb6b0252c.serverless.aws-eu-central-1.cloud.zilliz.com")
                .port(19530)
                .collectionName("medium_articles")
                .dimension(384) // 必须与AllMiniLmL6V2维度一致
                .indexType(IndexType.FLAT)
                .metricType(MetricType.COSINE)
                .consistencyLevel(io.milvus.common.clientenum.ConsistencyLevelEnum.STRONG)
                .autoFlushOnInsert(true) // 确保实时写入
                .idFieldName("id")
                .textFieldName("text")
                .vectorFieldName("vector")
                .build();
    }

}