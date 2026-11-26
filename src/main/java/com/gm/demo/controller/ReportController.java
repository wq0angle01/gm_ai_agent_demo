package com.gm.demo.controller;

import com.gm.demo.entity.response.LevelCountDto;
import com.gm.demo.entity.response.ReportOverviewResponse;
import com.gm.demo.entity.response.TopSpenderResponse;
import com.gm.demo.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 数据报表接口：全服概览、等级分布、大 R 排行。全部只读，不改状态所以无需审计与 operator。 */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "08-数据报表")
public class ReportController {

    private final ReportService service;

    @Autowired
    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @Operation(summary = "全服概览（总数/封禁/付费/充值总额/ARPU）")
    public ReportOverviewResponse overview() {
        return service.overview();
    }

    @GetMapping("/level-distribution")
    @Operation(summary = "等级分布（按 level 分组计数）")
    public List<LevelCountDto> levelDistribution() {
        return service.levelDistribution();
    }

    @GetMapping("/top-spenders")
    @Operation(summary = "大 R 排行（按累计充值降序，默认前 10）")
    public List<TopSpenderResponse> topSpenders(@RequestParam(defaultValue = "10") int topN) {
        return service.topSpenders(topN);
    }
}
