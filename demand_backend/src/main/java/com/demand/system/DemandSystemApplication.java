package com.demand.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.demand.system.module.*.mapper")
public class DemandSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemandSystemApplication.class, args);
    }
}
