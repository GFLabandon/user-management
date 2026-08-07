package com.example.usermanagement.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.example.usermanagement.mapper")
public class MyBatisConfig {
}