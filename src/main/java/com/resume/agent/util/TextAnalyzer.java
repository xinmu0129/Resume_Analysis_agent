package com.resume.agent.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 结构化文本分析 — 从简历/JD 中提取技能、经验年限、学历。
 */
public class TextAnalyzer {

    // ====== 技能词典 ======
    private static final Set<String> SKILL_DICT = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    static {
        // 编程语言
        SKILL_DICT.addAll(Set.of("Java", "Python", "JavaScript", "TypeScript", "Go", "Golang",
                "C++", "C#", "Rust", "PHP", "Ruby", "Swift", "Kotlin", "Scala", "Shell", "SQL"));

        // 框架 & 库
        SKILL_DICT.addAll(Set.of("Spring", "Spring Boot", "Spring Cloud", "Spring MVC",
                "MyBatis", "MyBatis-Plus", "Hibernate", "JPA", "Struts",
                "Django", "Flask", "FastAPI", "Express", "NestJS",
                "React", "Vue", "Vue.js", "Angular", "Next.js", "Nuxt", "jQuery",
                "Node.js", "Bootstrap", "Tailwind CSS", "Redux", "Webpack", "Vite",
                "gRPC", "GraphQL", "RESTful", "WebSocket"));

        // 数据库 & 中间件
        SKILL_DICT.addAll(Set.of("MySQL", "PostgreSQL", "Oracle", "SQL Server", "MongoDB",
                "Redis", "Elasticsearch", "ES", "Kafka", "RabbitMQ", "RocketMQ",
                "Nacos", "ZooKeeper", "Consul", "Etcd", "Sentinel"));

        // DevOps & 云
        SKILL_DICT.addAll(Set.of("Docker", "Kubernetes", "K8s", "Jenkins", "GitLab CI",
                "GitHub Actions", "Nginx", "Tomcat", "Apache", "Linux",
                "AWS", "Azure", "GCP", "阿里云", "腾讯云", "华为云", "Terraform", "Ansible"));

        // 大数据 & AI
        SKILL_DICT.addAll(Set.of("Hadoop", "Spark", "Flink", "Hive", "HBase",
                "TensorFlow", "PyTorch", "Scikit-learn", "Pandas", "NumPy",
                "LangChain", "LlamaIndex"));

        // 架构 & 方法论
        SKILL_DICT.addAll(Set.of("微服务", "分布式", "高并发", "高可用", "DDD", "TDD",
                "敏捷", "Scrum", "DevOps", "CI/CD", "MVC", "MVVM"));

        // 中文技能
        SKILL_DICT.addAll(Set.of("多线程", "机器学习", "深度学习", "自然语言处理", "计算机视觉",
                "数据仓库", "数据挖掘", "ETL"));
    }

    // ====== 经验正则 ======
    private static final Pattern[] EXP_PATTERNS = {
            Pattern.compile("(\\d+)\\s*年\\s*(以上|左右|的)?\\s*(工作)?\\s*经验"),
            Pattern.compile("工作\\s*(年限|经验)[：:]*\\s*(\\d+)\\s*年"),
            Pattern.compile("经验[：:]\\s*\\d+\\s*年"),  // 确认存在经验表述
    };

    // ====== 学历正则 ======
    private static final Pattern EDU_PATTERN = Pattern.compile(
            "博士|硕士|本科|大专|学士|MBA|PhD|Master|Bachelor|研究生");

    private TextAnalyzer() {}

    /**
     * 分析结果
     */
    public record AnalysisResult(String skills, String experience, String education) {
        public static final AnalysisResult EMPTY = new AnalysisResult("", "", "");
    }

    /**
     * 分析文本 — 提取技能、经验、学历
     */
    public static AnalysisResult analyze(String text) {
        if (text == null || text.isBlank()) return AnalysisResult.EMPTY;

        String skills = extractSkills(text);
        String experience = extractExperience(text);
        String education = extractEducation(text);

        return new AnalysisResult(skills, experience, education);
    }

    // ====== 技能提取 ======
    private static String extractSkills(String text) {
        Set<String> found = new LinkedHashSet<>();
        for (String skill : SKILL_DICT) {
            // 需要词边界匹配，避免 "React" 匹配 "Reactive"
            Pattern p = Pattern.compile("(?i)\\b?" + Pattern.quote(skill) + "\\b?");
            if (p.matcher(text).find()) {
                found.add(skill);
            }
        }
        return String.join(", ", found);
    }

    // ====== 经验提取 ======
    private static String extractExperience(String text) {
        for (Pattern pattern : EXP_PATTERNS) {
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                // 尝试从捕获组中提取数字
                for (int i = 1; i <= m.groupCount(); i++) {
                    String g = m.group(i);
                    if (g != null && g.matches("\\d+")) {
                        int years = Integer.parseInt(g);
                        if (years > 0 && years <= 50) {
                            return years + "年";
                        }
                    }
                }
            }
        }
        return "";
    }

    // ====== 学历提取 ======
    private static String extractEducation(String text) {
        Matcher m = EDU_PATTERN.matcher(text);
        if (m.find()) {
            return m.group();
        }
        return "";
    }

}
