package com.gm.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 应用入口。组件扫描范围 = 本类所在包及其子包（com.gm.demo.**）。 */
@SpringBootApplication
public class GmDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(GmDemoApplication.class, args);
    }
}
