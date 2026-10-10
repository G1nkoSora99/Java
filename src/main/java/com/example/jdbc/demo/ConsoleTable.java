package com.example.jdbc.demo;

import java.util.List;

/**
 * 控制台表格打印工具（仅用于演示效果，与 ORM 逻辑无关）。
 *
 * <p>支持中文等全角字符的宽度计算，保证控制台输出对齐。</p>
 *
 * @author homework
 */
final class ConsoleTable {

    private ConsoleTable() {
    }

    /**
     * 打印一张表格。
     *
     * @param headers 表头
     * @param rows    数据行（每行长度应与表头一致）
     */
    static void print(List<String> headers, List<List<String>> rows) {
        int[] widths = new int[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            widths[i] = displayWidth(headers.get(i));
        }
        for (List<String> row : rows) {
            for (int i = 0; i < widths.length && i < row.size(); i++) {
                widths[i] = Math.max(widths[i], displayWidth(row.get(i)));
            }
        }

        System.out.println(border(widths));
        System.out.println(row(headers, widths));
        System.out.println(border(widths));
        for (List<String> data : rows) {
            System.out.println(row(data, widths));
        }
        System.out.println(border(widths));
        System.out.println("共 " + rows.size() + " 行");
    }

    private static String border(int[] widths) {
        StringBuilder sb = new StringBuilder("+");
        for (int width : widths) {
            sb.append("-".repeat(width + 2)).append('+');
        }
        return sb.toString();
    }

    private static String row(List<String> cells, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < widths.length; i++) {
            String cell = i < cells.size() ? cells.get(i) : "";
            if (cell == null) {
                cell = "NULL";
            }
            sb.append(' ').append(cell).append(" ".repeat(widths[i] - displayWidth(cell) + 1)).append('|');
        }
        return sb.toString();
    }

    /** 计算字符串在等宽控制台中的显示宽度（中日韩全角字符按 2 列计算）。 */
    static int displayWidth(String text) {
        if (text == null) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += isWide(text.charAt(i)) ? 2 : 1;
        }
        return width;
    }

    private static boolean isWide(char c) {
        return (c >= 0x1100 && c <= 0x115F)
                || c == 0x2329 || c == 0x232A
                || (c >= 0x2E80 && c <= 0xA4CF && c != 0x303F)
                || (c >= 0xAC00 && c <= 0xD7A3)
                || (c >= 0xF900 && c <= 0xFAFF)
                || (c >= 0xFE30 && c <= 0xFE6F)
                || (c >= 0xFF00 && c <= 0xFF60)
                || (c >= 0xFFE0 && c <= 0xFFE6);
    }
}
