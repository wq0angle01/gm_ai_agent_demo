package com.gm.demo.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gm.demo.repository.NoticeRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 公告接口集成测试：草稿 → 发布 → 重复发布 409 → 活跃查询 → 校验。 */
@SpringBootTest
@AutoConfigureMockMvc
class NoticeApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private NoticeRepository repo;

    private final String title = "notice_test_" + Long.toHexString(System.nanoTime());

    @AfterEach
    void cleanup() {
        repo.findByTitle(title).ifPresent(repo::delete);
    }

    private long createDraft() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "title", title,
                "content", "公告测试正文",
                "priority", 10,
                "pinned", true));
        String resp = mvc.perform(post("/api/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator", "tester")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.priority").value(10))
                .andExpect(jsonPath("$.pinned").value(true))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("id").asLong();
    }

    @Test
    void createDraftReturns201() throws Exception {
        createDraft();
    }

    @Test
    void publishTransitionsToPublished() throws Exception {
        long id = createDraft();
        mvc.perform(post("/api/notices/" + id + "/publish")
                        .header("X-Operator", "tester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.effectiveAt").isNotEmpty());
    }

    @Test
    void republishReturns409() throws Exception {
        long id = createDraft();
        mvc.perform(post("/api/notices/" + id + "/publish")
                        .header("X-Operator", "tester"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/notices/" + id + "/publish")
                        .header("X-Operator", "tester"))
                .andExpect(status().isConflict());
    }

    @Test
    void activeListContainsPublishedNotice() throws Exception {
        long id = createDraft();
        mvc.perform(post("/api/notices/" + id + "/publish")
                        .header("X-Operator", "tester"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/notices/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void blankTitleRejectedWith400() throws Exception {
        String body = om.writeValueAsString(Map.of("title", "", "content", "x"));
        mvc.perform(post("/api/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
