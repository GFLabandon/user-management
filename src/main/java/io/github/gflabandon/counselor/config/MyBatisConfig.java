package io.github.gflabandon.counselor.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("io.github.gflabandon.counselor.mapper")
public class MyBatisConfig {
}