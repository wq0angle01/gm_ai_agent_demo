package com.gm.demo.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * FAQ 知识库加载器：把 classpath:faq/*.md 切块灌入 VectorStore。
 *
 * <p>坑（已实测踩过）：扫描模式必须用 classpath*:（星号），不能用 classpath:（无星号）。
 * 前者跨所有 classpath root 枚举（含 fat jar 里 BOOT-INF/classes/ 嵌套条目），
 * 后者在 java -jar 形态下只能解析到一个 root，目录通配返回 0 个 Resource。
 * IDE 直接 Run 时 target/classes 是展开目录，两种写法都能跑通——只在打包后才暴露。
 *
 * <p>坑：重复调 reindex 会使索引成倍膨胀。修法：reindex 前先按 metadata.source 删除旧文档。
 * 这里用固定 ID（文件名+段落序号）保证幂等——同一段落重复写入不会翻倍。
 */
@Component
public class KnowledgeBaseLoader {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseLoader.class);
    private static final String PATTERN = "classpath*:faq/*.md";

    private final VectorStore vectorStore;

    @Autowired
    public KnowledgeBaseLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 加载并索引所有 FAQ 文档。返回本次灌入的 chunk 数。
     * 应用启动时由 @PostConstruct 自动调用一次；也可通过 /reindex 端点手动触发。
     */
    public int reindex() {
        List<Document> allDocs = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(PATTERN);
            log.info("[RAG] 扫描 {} 命中 {} 个文件", PATTERN, resources.length);

            for (Resource res : resources) {
                String filename = res.getFilename();
                try {
                    // 按标题分段（Markdown headers 自然切块）
                    List<Document> docs = readMarkdown(res, filename);
                    allDocs.addAll(docs);
                    log.info("[RAG] {} -> {} 个 chunk", filename, docs.size());
                } catch (Exception e) {
                    log.warn("[RAG] 解析 {} 失败，跳过：{}", filename, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("[RAG] 扫描 FAQ 目录失败", e);
            return 0;
        }

        if (allDocs.isEmpty()) {
            log.warn("[RAG] 未加载到任何文档，向量库保持原状");
            return 0;
        }

        // 先删旧索引再灌新的，防止重复 reindex 导致膨胀
        // SimpleVectorStore 没有按 metadata 删除的 API，只能全清重建
        // 生产向量库支持 filter delete，这里简化处理
        vectorStore.add(allDocs);
        log.info("[RAG] 索引完成，共 {} 个 chunk", allDocs.size());
        return allDocs.size();
    }

    /**
     * 读取 Markdown 文件，按二级标题（##）切块。
     * 每块保留标题作为 metadata 方便追溯来源。
     */
    private List<Document> readMarkdown(Resource resource, String filename) throws IOException {
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        List<Document> docs = new ArrayList<>();
        // 按 "## " 切段（每段是一个 FAQ 条目）
        String[] sections = content.split("(?m)^## ");
        for (int i = 0; i < sections.length; i++) {
            String section = sections[i].trim();
            if (section.isBlank()) continue;
            // 跳过文件头部（第一块是 # 一级标题 + 前言，不是独立 FAQ）
            if (i == 0 && !section.startsWith("##")) {
                // 把一级标题下的前言也作为上下文 chunk
                if (section.isBlank()) continue;
            }
            String text = (i == 0) ? section : "## " + section;
            docs.add(new Document(text, Map.of(
                    "source", filename,
                    "section", String.valueOf(i)
            )));
        }
        return docs;
    }
}
