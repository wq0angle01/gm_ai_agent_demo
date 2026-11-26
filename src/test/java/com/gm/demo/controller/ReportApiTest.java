package com.gm.demo.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 报表接口集成测试。不断言精确总数——测试运行时会创建/删除玩家，只断言 seed 保证的下界
 * （hero_001 充值 128 + whale_002 充值 64800 合计 64928）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void overviewReturnsAggregateFields() throws Exception {
        mvc.perform(get("/api/reports/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPlayers").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.payingPlayers").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.totalRecharge").value(greaterThanOrEqualTo(64928.0)))
                .andExpect(jsonPath("$.arpu").isNumber());
    }

    @Test
    void levelDistributionIsNotEmpty() throws Exception {
        mvc.perform(get("/api/reports/level-distribution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].level").exists())
                .andExpect(jsonPath("$[0].playerCount").exists());
    }

    @Test
    void topSpendersHeadIsWhale() throws Exception {
        mvc.perform(get("/api/reports/top-spenders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("whale_002"))
                .andExpect(jsonPath("$[0].rechargeAmount").value(greaterThanOrEqualTo(64800.0)));
    }

    @Test
    void topSpenderLimitIsRespected() throws Exception {
        mvc.perform(get("/api/reports/top-spenders").param("topN", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }
}
