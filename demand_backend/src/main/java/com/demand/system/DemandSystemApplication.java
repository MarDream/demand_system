package com.demand.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.demand.system.module.*.mapper")
@EnableScheduling
@EnableAsync
public class DemandSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemandSystemApplication.class, args);
    }
}
