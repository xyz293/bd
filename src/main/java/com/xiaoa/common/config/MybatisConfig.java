package com.xiaoa.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;

@Configuration
@ConditionalOnBean(SqlSessionFactory.class)
@MapperScan({"com.xiaoa.tenant.mapper", "com.xiaoa.task.mapper", "com.xiaoa.admin.mapper", "com.xiaoa.quota.mapper", "com.xiaoa.ai.mapper", "com.xiaoa.asset.mapper"})
public class MybatisConfig {
}
