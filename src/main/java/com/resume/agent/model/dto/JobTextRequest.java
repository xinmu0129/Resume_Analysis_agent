package com.resume.agent.model.dto;

import lombok.Data;

@Data
public class JobTextRequest {

    /** 岗位名称 (必填) */
    private String title;

    /** 公司名称 (选填) */
    private String company;

    /** JD正文 (必填) */
    private String content;

}
