package com.resume.agent.util;

/**
 * 文本清洗工具 — 去除 PDF/Tika 提取产物的常见噪声。
 * 保守策略：只做安全操作，不删除任何正文内容。
 */
public class TextCleaner {

    /** C0 控制字符（保留 \t \n） */
    private static final String C0_REMOVABLE = "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]";

    /** 全角字母/数字 → 半角映射 */
    private static final int FULLWIDTH_A = 0xFF21; // Ａ
    private static final int FULLWIDTH_Z = 0xFF3A; // Ｚ
    private static final int FULLWIDTH_a = 0xFF41; // ａ
    private static final int FULLWIDTH_z = 0xFF5A; // ｚ
    private static final int FULLWIDTH_0 = 0xFF10; // ０
    private static final int FULLWIDTH_9 = 0xFF19; // ９

    /** 页码模式 */
    private static final String[] PAGE_PATTERNS = {
            "第\\s*\\d+\\s*页(\\s*[/／]\\s*共\\s*\\d+\\s*页)?",
            "Page\\s+\\d+\\s*(of\\s+\\d+)?",
            "\\d+\\s*/\\s*\\d+\\s*(页)?"
    };

    private TextCleaner() {}

    /**
     * 清洗文本 — 去除 PDF 噪声
     */
    public static String clean(String text) {
        if (text == null || text.isEmpty()) return text;

        text = removeControlChars(text);
        text = normalizeFullwidth(text);
        text = removePageNumberLines(text);
        text = collapseBlankLines(text);
        text = text.trim();

        return text;
    }

    // ========== private ==========

    /** 移除 C0 控制字符 */
    private static String removeControlChars(String text) {
        return text.replaceAll(C0_REMOVABLE, "");
    }

    /** 全角字母/数字/空格 → 半角 */
    private static String normalizeFullwidth(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '　') {                    // 全角空格 → 半角
                sb.append(' ');
            } else if (c >= FULLWIDTH_A && c <= FULLWIDTH_Z) {   // Ａ-Ｚ
                sb.append((char) (c - FULLWIDTH_A + 'A'));
            } else if (c >= FULLWIDTH_a && c <= FULLWIDTH_z) {   // ａ-ｚ
                sb.append((char) (c - FULLWIDTH_a + 'a'));
            } else if (c >= FULLWIDTH_0 && c <= FULLWIDTH_9) {   // ０-９
                sb.append((char) (c - FULLWIDTH_0 + '0'));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 移除含页码标记的整行 */
    private static String removePageNumberLines(String text) {
        for (String pattern : PAGE_PATTERNS) {
            text = text.replaceAll("(?m)^.*" + pattern + ".*$(\n|\r\n)?", "");
        }
        return text;
    }

    /** 合并连续空行: ≥3 个 \n → 最多 2 个 */
    private static String collapseBlankLines(String text) {
        return text.replaceAll("(\n\\s*){3,}", "\n\n");
    }

}
