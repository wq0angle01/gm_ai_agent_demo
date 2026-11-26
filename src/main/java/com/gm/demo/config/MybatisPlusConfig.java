package com.gm.demo.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.time.Instant;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 基础设施：分页拦截器 + 时间戳自动填充。
 *
 * <p>MP 不像 Hibernate 有 {@code @PrePersist}，时间戳要显式交给 MetaObjectHandler，
 * 实体字段用 {@code @TableField(fill = INSERT / INSERT_UPDATE)} 标记即可，字段缺失会被静默跳过。
 */
@Configuration
@MapperScan("com.gm.demo.repository")
public class MybatisPlusConfig {

    /** MySQL 方言的分页拦截器；不加这个，MP 的 selectPage 会退化成全表 + 内存截断。 */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /** 时间戳统一由这里写入，业务代码不再手调 Instant.now()。 */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                Instant now = Instant.now();
                strictInsertFill(metaObject, "createdAt", Instant.class, now);
                strictInsertFill(metaObject, "updatedAt", Instant.class, now);
                strictInsertFill(metaObject, "obtainedAt", Instant.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updatedAt", Instant.class, Instant.now());
            }
        };
    }
}
