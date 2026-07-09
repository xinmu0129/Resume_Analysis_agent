package com.resume.agent.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class JobUploadVO {

    private Long id;
    private String title;
    private String company;
    private LocalDateTime createTime;

}
