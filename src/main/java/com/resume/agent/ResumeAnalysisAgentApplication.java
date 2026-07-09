package com.resume.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.resume.agent.mapper")
public class ResumeAnalysisAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeAnalysisAgentApplication.class, args);
    }

}
