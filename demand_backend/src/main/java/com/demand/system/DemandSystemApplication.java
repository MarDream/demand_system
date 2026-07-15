package com.demand.system;

import com.demand.system.module.rbac.support.RbacConstants;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@MapperScan("com.demand.system.module.*.mapper")
@EnableScheduling
public class DemandSystemApplication {

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("RBAC Constants Debug:");
        System.out.println("Total permissions: " + RbacConstants.ALL_PERMISSION_CODES.size());
        System.out.println("Has menu:bitable: " + RbacConstants.ALL_PERMISSION_CODES.contains("menu:bitable"));
        System.out.println("PERMISSION_MENU_BITABLE: " + RbacConstants.PERMISSION_MENU_BITABLE);
        System.out.println("==========================================");
        SpringApplication.run(DemandSystemApplication.class, args);
    }
}
