package com.gm.demo.tool;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 幂等护栏：同一个 (requestId, 动作) 只真正执行一次，重复调用直接回上一次的结果。
 *
 * <p><b>它解决的是"模型在一次回答里对同一笔写操作手抖调了两次"</b>——数值累加类操作（调货币）
 * 一旦重复就会翻倍发钱，靠状态机挡不住（不像 ban 有终态可比），必须显式记一次"这个 requestId 我做过了"。
 *
 * <p><b>边界，必须诚实说明</b>：这是<b>单进程内存版</b>——
 * <ul>
 *   <li>进程重启即清空，不保证跨重启幂等；</li>
 *   <li>多实例部署时各存各的，挡不住跨节点的重复请求。</li>
 * </ul>
 * 真要上生产，这里应换成 Redis 的 {@code SET key val NX EX ttl}（原子占位）或数据库唯一索引。
 * 当前是原型 + 单机演示，用带 TTL 和容量上限的内存 Map 足够，且把"该换什么"写清楚比假装它够生产更可信。
 */
@Component
public class IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGuard.class);

    /** 容量护栏：超了按插入顺序（近似）淘汰最旧，避免长跑把堆吃满。 */
    private static final int MAX_ENTRIES = 1000;
    /** 结果只保留这么久——比一次会话长、比一天短，够挡同 turn 重复又不无限占内存。 */
    private static final Duration TTL = Duration.ofMinutes(10);

    private record Entry(String result, Instant at) {
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /**
     * 有则复用、无则执行并记录。action 抛异常时不写缓存——失败的写操作没产生副作用，
     * 下次同样的 requestId 再来应允许重试，而不是把一次失败钉死。
     *
     * @return 结果文本；{@link Outcome#replayed()} 标记这次是否命中了缓存
     */
    public Outcome executeOnce(String key, Supplier<String> action) {
        Entry hit = store.get(key);
        if (hit != null && hit.at().plus(TTL).isAfter(Instant.now())) {
            log.info("[幂等] 命中缓存 key={}，跳过重复执行", key);
            return new Outcome(hit.result(), true);
        }
        String result = action.get();   // 抛异常则下面的 put 不执行
        store.put(key, new Entry(result, Instant.now()));
        sweep();
        return new Outcome(result, false);
    }

    /** 惰性清理过期项；量小时够用，不必起定时线程。 */
    private void sweep() {
        if (store.size() <= MAX_ENTRIES) {
            return;
        }
        Instant deadline = Instant.now().minus(TTL);
        store.entrySet().removeIf(e -> e.getValue().at().isBefore(deadline));
        // 清完仍超限（都是新鲜的），按 key 顺序丢弃最老的一批兜底
        if (store.size() > MAX_ENTRIES) {
            int drop = store.size() - MAX_ENTRIES;
            store.keySet().stream().limit(drop).forEach(store::remove);
        }
    }

    /** 执行结果 + 是否命中缓存。replayed=true 时调用方要如实告诉模型"这次没重复执行"。 */
    public record Outcome(String result, boolean replayed) {
    }
}
