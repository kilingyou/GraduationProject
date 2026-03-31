package com.scm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Supply chain backend entry point.
 */
@SpringBootApplication
@MapperScan("com.scm.module.*.mapper")
@EnableScheduling
public class SupplyChainApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupplyChainApplication.class, args);
    }
}
