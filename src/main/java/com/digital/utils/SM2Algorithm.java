package com.digital.utils;

import java.util.Calendar;
import java.util.Date;

/**
 * SM-2 算法 Java 实现
 * 核心改进：
 * 1. 记忆失败（评分<3）时EF重置为初始值2.5
 * 2. 下次复习时间基于「上次复习时间」计算
 * 3. 完善参数校验，避免非法值导致的逻辑错误
 */
public class SM2Algorithm {

    // SM-2 官方标准参数
    public static final double INITIAL_EF = 2.5;    // 初始易忘系数
    private static final double MIN_EF = 1.3;         // EF最小值（仅评分≥3时生效）

    /**
     * 复习结果枚举（对应 0-5 分评分）
     */
    public enum Grade {
        FAILED(0),        // 完全忘记（0分）
        HARD(1),          // 很难回忆（1分）
        DIFFICULT(2),     // 有难度（2分）
        NORMAL(3),        // 正常（3分）
        EASY(4),          // 简单（4分）
        VERY_EASY(5);     // 非常简单（5分）

        private final int value;

        Grade(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * SM-2 算法核心计算方法
     * @param grade 本次复习评分（0-5分）
     * @param repetition 已复习次数（初次复习为0）
     * @param ef 当前易忘系数（初次为2.5）
     * @param interval 上次复习间隔（天，初次为0）
     * @param lastReviewTime 上次复习时间（必填，初次复习传当前时间）
     * @return 计算结果：包含下次间隔、新EF、下次复习时间
     */
    public static SM2Result calculate(Grade grade, int repetition, double ef, int interval, Date lastReviewTime) {
        // 参数校验
        if (grade == null) {
            grade = Grade.NORMAL;
        }
        if (repetition < 0) {
            repetition = 0;
        }
        if (ef < MIN_EF) {
            ef = INITIAL_EF;
        }
        if (interval < 0) {
            interval = 0;
        }
        if (lastReviewTime == null) {
            lastReviewTime = new Date();
        }

        int gradeValue = grade.getValue();
        SM2Result result = new SM2Result();
        double newEF = ef;
        int newInterval;
        int newRepetition = repetition;

        // 1. 处理记忆失败（评分<3分）的情况（官方规范）
        if (gradeValue < 3) {
            newEF = INITIAL_EF;          // 重置EF为初始值2.5
            newInterval = 1;             // 下次间隔强制设为1天
            newRepetition = 0;           // 重置复习次数为0
        } else {
            // 2. 记忆成功（评分≥3分）：更新EF并计算间隔
            newEF = ef - 0.8 + 0.28 * gradeValue - 0.02 * Math.pow(gradeValue, 2);
            newEF = Math.max(newEF, MIN_EF); // 确保EF不低于最小值1.3

            // 计算下次复习间隔（天）
            switch (newRepetition) {
                case 0: // 初次复习（第1次）
                    newInterval = 1;
                    break;
                case 1: // 第2次复习
                    newInterval = 6;
                    break;
                default: // 第3次及以后：间隔 = 上次间隔 * EF
                    newInterval = (int) Math.round(interval * newEF);
                    break;
            }
            newRepetition++; // 复习次数+1
        }

        // 3. 计算下次复习时间（基于上次复习时间 + 新间隔）
        Date nextReviewTime = calculateNextReviewDate(lastReviewTime, newInterval);

        // 封装结果
        result.setEf(newEF);
        result.setInterval(newInterval);
        result.setRepetition(newRepetition);
        result.setNextReviewTime(nextReviewTime);

        return result;
    }

    /**
     * 根据上次复习时间和间隔天数计算下次复习时间
     * @param lastReviewTime 上次复习时间
     * @param days 间隔天数
     * @return 下次复习的具体时间
     */
    private static Date calculateNextReviewDate(Date lastReviewTime, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(lastReviewTime); // 基于上次复习时间计算，而非当前系统时间
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }

    /**
     * SM-2 计算结果封装类
     */
    public static class SM2Result {
        private double ef;          // 新的易忘系数
        private int interval;       // 下次复习间隔（天）
        private int repetition;     // 新的复习次数
        private Date nextReviewTime;// 下次复习时间

        // Getter & Setter
        public double getEf() { return ef; }
        public void setEf(double ef) { this.ef = ef; }
        public int getInterval() { return interval; }
        public void setInterval(int interval) { this.interval = interval; }
        public int getRepetition() { return repetition; }
        public void setRepetition(int repetition) { this.repetition = repetition; }
        public Date getNextReviewTime() { return nextReviewTime; }
        public void setNextReviewTime(Date nextReviewTime) { this.nextReviewTime = nextReviewTime; }

        @Override
        public String toString() {
            return "SM2Result{" +
                    "易忘系数=" + String.format("%.2f", ef) +
                    ", 下次复习间隔=" + interval + "天" +
                    ", 累计复习次数=" + repetition +
                    ", 下次复习时间=" + nextReviewTime +
                    '}';
        }
    }
}