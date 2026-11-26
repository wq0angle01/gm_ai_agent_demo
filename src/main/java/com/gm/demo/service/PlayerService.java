package com.gm.demo.service;

import com.gm.demo.entity.request.CreatePlayerRequest;
import com.gm.demo.entity.response.PlayerResponse;
import com.gm.demo.entity.domain.Player;
import com.gm.demo.entity.domain.PlayerStatus;
import com.gm.demo.error.ConflictException;
import com.gm.demo.error.NotFoundException;
import com.gm.demo.repository.PlayerRepository;
import java.math.RoundingMode;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 玩家业务层，所有写入的唯一入口（HTTP 接口、定时任务、将来的 AI 工具都走这里）。
 *
 * <p>构造器注入：字段可 final、单测能直接 new、循环依赖在启动期就暴露。
 */
@Service
@Transactional(readOnly = true)
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    /** 防止 size=999999 拖垮内存和数据库。 */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 单次货币调整绝对值上限。这道闸门 AI 和人工共用——模型填的 delta 不可信，
     * 一个 999999999 就能把 economy 打穿，所以上限写死在服务层而不是只靠工具描述里那句“不要超过”。
     */
    private static final long MAX_CURRENCY_DELTA = 100_000L;

    private final PlayerRepository repository;
    private final AuditService audit;

    @Autowired
    public PlayerService(PlayerRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    /**
     * 创建玩家。operator 来自请求头，本阶段只写日志，A8 接审计表时在此落库。
     *
     * <p>不在此处捕获数据库约束异常：事务遇到约束冲突即被标记 rollback-only，
     * 吞掉异常继续写只会再抛 UnexpectedRollbackException，交给全局处理器翻译成 409。
     */
    @Transactional
    public PlayerResponse createPlayer(CreatePlayerRequest req, String operator) {
        if (repository.existsByUsername(req.username())) {
            throw new ConflictException("username already exists: " + req.username());
        }

        Player p = Player.create(req.username(), req.nickname());
        p.setGold(req.gold() == null ? 0L : req.gold());
        p.setDiamond(req.diamond() == null ? 0L : req.diamond());
        if (req.rechargeAmount() != null) {
            p.setRechargeAmount(req.rechargeAmount().setScale(2, RoundingMode.HALF_UP));
        }

        Player saved = repository.save(p);
        log.info("[写入] 创建玩家 id={} username={} gold={} diamond={} 操作人={}",
                saved.getId(), saved.getUsername(), saved.getGold(), saved.getDiamond(), operator);
        return PlayerResponse.from(saved);
    }

    public PlayerResponse getPlayer(Long id) {
        return PlayerResponse.from(requireEntity(id));
    }

    /** 按登录名精确查。查不到返回空而不是抛异常：调用方要把「没这个人」如实说清楚。 */
    public Optional<PlayerResponse> findByUsername(String username) {
        return repository.findByUsername(username).map(PlayerResponse::from);
    }

    /** 分页 + 关键字搜索，kw 为空表示不过滤。 */
    public Page<PlayerResponse> search(String keyword, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        String kw = keyword == null ? "" : keyword.trim();
        PageRequest pr = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<PlayerResponse> result = repository.search(kw, pr).map(PlayerResponse::from);
        log.debug("[读取] 搜索玩家 kw={} page={} size={} 命中={}", kw, safePage, safeSize, result.getTotalElements());
        return result;
    }

    /** 封禁玩家：NORMAL → BANNED。幂等——已经 BANNED 就报 409。 */
    @Transactional
    public PlayerResponse ban(Long id, String operator) {
        Player p = requireEntity(id);
        if (p.getStatus() == PlayerStatus.BANNED) {
            throw new ConflictException("玩家 id=" + id + " 已经是封禁状态");
        }
        p.setStatus(PlayerStatus.BANNED);
        Player saved = repository.save(p);
        audit.record("BAN", "Player", String.valueOf(id),
                "封禁玩家 " + saved.getUsername(), operator);
        log.info("[写入] 封禁玩家 id={} username={} operator={}", id, saved.getUsername(), operator);
        return PlayerResponse.from(saved);
    }

    /** 解封：BANNED → NORMAL。 */
    @Transactional
    public PlayerResponse unban(Long id, String operator) {
        Player p = requireEntity(id);
        if (p.getStatus() != PlayerStatus.BANNED) {
            throw new ConflictException("玩家 id=" + id + " 当前状态 " + p.getStatus() + "，不是封禁态");
        }
        p.setStatus(PlayerStatus.NORMAL);
        Player saved = repository.save(p);
        audit.record("UNBAN", "Player", String.valueOf(id),
                "解封玩家 " + saved.getUsername(), operator);
        log.info("[写入] 解封玩家 id={} username={} operator={}", id, saved.getUsername(), operator);
        return PlayerResponse.from(saved);
    }

    /** 包内可见：不把可变实体散播到 Service 之外。 */
    Player requireEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Player", id));
    }

    /**
     * 调整玩家货币（充值/扣减）。与 ban/unban 的关键差别：这是数值累加、不幂等，
     * 所以护栏必须在这里（而不是调用方）兜死三件事：币种合法、单次幅度不超上限、余额不被扣成负。
     *
     * <p>任何一条不过都抛 ConflictException（映射成 409），事务回滚、不落审计——被挡在门外的请求
     * 本身没有改动数据，不需要留“写痕”；真正的写成功才 record。
     *
     * @param currency "gold" 或 "diamond"
     * @param delta    带符号增量，正加负减，绝对值上限 {@value #MAX_CURRENCY_DELTA}
     */
    @Transactional
    public PlayerResponse adjustCurrency(Long id, String currency, long delta, String operator) {
        if (currency == null || !(currency.equalsIgnoreCase("gold") || currency.equalsIgnoreCase("diamond"))) {
            throw new ConflictException("币种只能是 gold 或 diamond，收到：" + currency);
        }
        if (delta == 0) {
            throw new ConflictException("调整额度不能为 0");
        }
        if (Math.abs(delta) > MAX_CURRENCY_DELTA) {
            throw new ConflictException(
                    "单次调整额度 " + delta + " 超上限 " + MAX_CURRENCY_DELTA);
        }

        Player p = requireEntity(id);
        boolean isGold = currency.equalsIgnoreCase("gold");
        long current = isGold ? p.getGold() : p.getDiamond();
        long next = current + delta;
        if (next < 0) {
            // 把当前余额回给调用方，模型能据此自我纠正（改成小额或不扣）
            throw new ConflictException(
                    "余额不足：" + currency + " 当前 " + current + "，无法扣减 " + Math.abs(delta));
        }
        if (isGold) {
            p.setGold(next);
        } else {
            p.setDiamond(next);
        }

        Player saved = repository.save(p);
        audit.record("ADJUST_CURRENCY", "Player", String.valueOf(id),
                "调整 " + currency + " " + (delta > 0 ? "+" : "") + delta + "，余额 " + current + "→" + next,
                operator);
        log.info("[写入] 调整货币 id={} {} {}→{} operator={}",
                id, currency, current, next, operator);
        return PlayerResponse.from(saved);
    }
}
