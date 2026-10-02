package io.github.gflabandon.counselor.config;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("io.github.gflabandon.counselor.mapper")
public class MyBatisConfig {
    @Bean
    DatabaseIdProvider databaseIdProvider() { return new VendorDatabaseIdProvider(); }
}
