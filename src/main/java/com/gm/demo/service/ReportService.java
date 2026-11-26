package com.gm.demo.service;

import com.gm.demo.entity.response.LevelCountDto;
import com.gm.demo.entity.response.ReportOverviewResponse;
import com.gm.demo.entity.response.TopSpenderResponse;
import com.gm.demo.repository.PlayerRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 报表业务层：只读聚合，不新增实体也不写库。
 *
 * <p>聚合全部下推数据库执行（JPQL GROUP BY / CASE WHEN）， Service 只做类型转换与派生指标计算。
 * 当前规模直接查 player 表够用；数据量上来后应改为定时任务预聚合到报表表，接口契约不变。
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final PlayerRepository repository;

    @Autowired
    public ReportService(PlayerRepository repository) {
        this.repository = repository;
    }

    /** 全服概览。ARPU = 充值总额 / 总人数，空表时返回 0 而不是除零异常。 */
    public ReportOverviewResponse overview() {
        java.util.Map<String, Object> row = repository.overviewStats();
        long total = toLong(row.get("total"));
        long banned = toLong(row.get("banned"));
        long paying = toLong(row.get("paying"));
        BigDecimal recharge = toBigDecimal(row.get("totalRecharge"));
        BigDecimal arpu = total == 0
                ? BigDecimal.ZERO
                : recharge.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new ReportOverviewResponse(total, banned, paying, recharge, arpu);
    }

    /** 等级分布。SQL 里给 COUNT(*) 取了别名 cnt，避免依赖 MySQL 方言的列名。 */
    public List<LevelCountDto> levelDistribution() {
        return repository.levelDistribution().stream()
                .map(row -> new LevelCountDto(
                        (int) toLong(row.get("level")),
                        toLong(row.get("cnt"))))
                .toList();
    }

    /** 大 R 排行前 N。上限护栏防止 topN 被填成天文数字拖库。 */
    public List<TopSpenderResponse> topSpenders(int topN) {
        int safeN = Math.min(Math.max(topN, 1), 100);
        return repository.topSpenders(safeN).stream()
                .map(row -> new TopSpenderResponse(
                        (String) row.get("username"),
                        (String) row.get("nickname"),
                        toBigDecimal(row.get("rechargeAmount"))))
                .toList();
    }

    // ---------- 行数据类型适配 ----------

    /** MySQL COUNT 回 Long，SUM(int) 可能回 BigDecimal，用 Number 统一收。 */
    private static long toLong(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        return new BigDecimal(v.toString());
    }
}
