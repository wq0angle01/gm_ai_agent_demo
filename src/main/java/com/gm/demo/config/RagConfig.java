package com.gm.demo.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 向量库装配。
 *
 * <p>SimpleVectorStore 是内存版暴力余弦（O(n) 无 ANN 索引），适合原型和几百条 chunk 的量级。
 * 重启即丢索引——需要 KnowledgeBaseLoader 在启动时重新灌入，或手动调 /reindex。
 * 生产要换持久化向量库（Milvus/Pinecone/Weaviate 等 Spring AI 都有 starter）。
 *
 * <p>EmbeddingModel 由 DashScope EmbeddingAutoConfiguration 自动装配（需 DASHSCOPE_API_KEY）。
 * 无 Key 时 sk-none 兜底不启动 EmbeddingModel——应用照常起，只有调 /reindex 或 RAG 问答才报错。
 */
@Configuration
public class RagConfig {

    @Bean
    public VectorStore vectorStore(@Autowired EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
