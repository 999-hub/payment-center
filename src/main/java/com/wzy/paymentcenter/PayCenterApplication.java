package com.wzy.paymentcenter;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.wzy.paymentcenter.mapper")
@EnableScheduling
@SpringBootApplication
public class PayCenterApplication {
    public static void main(String[] args) {
        SpringApplication.run(PayCenterApplication.class, args);
    }
}
//启动类只能扫描启动类所在包及子包下的类
