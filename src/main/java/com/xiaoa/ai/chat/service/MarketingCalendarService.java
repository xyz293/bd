package com.xiaoa.ai.chat.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 营销日历（方案 ① fetch_context 数据源之一）：一期内置静态节日表，
 * 提供未来 30 天内的营销节点，供意图识别/增益选项（A 加节日氛围）使用。
 * 二期迁移 MySQL 营销日历表后仅替换本类实现。
 */
@Service
public class MarketingCalendarService {

    /** 静态节日表（MM-dd -> 名称）；七夕等农历节日一期按近似公历日维护 */
    private static final SortedMap<String, String> FESTIVALS = new TreeMap<>();

    static {
        FESTIVALS.put("01-01", "元旦");
        FESTIVALS.put("02-14", "情人节");
        FESTIVALS.put("03-08", "女神节");
        FESTIVALS.put("05-01", "五一");
        FESTIVALS.put("05-20", "520");
        FESTIVALS.put("06-01", "儿童节");
        FESTIVALS.put("08-14", "七夕");
        FESTIVALS.put("09-10", "教师节");
        FESTIVALS.put("10-01", "国庆");
        FESTIVALS.put("11-11", "双十一");
        FESTIVALS.put("12-24", "平安夜");
        FESTIVALS.put("12-25", "圣诞");
    }

    /** 未来 30 天内的营销节点：「520（05-20）」，按日期升序。 */
    public List<String> upcoming30Days() {
        List<String> upcoming = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int offset = 0; offset <= 30; offset++) {
            LocalDate date = today.plusDays(offset);
            String festival = FESTIVALS.get(MonthDay.from(date).toString().substring(1));
            if (festival != null) {
                upcoming.add(festival + "(" + date.getMonthValue() + "-" + date.getDayOfMonth() + ")");
            }
        }
        return upcoming;
    }

    /** 最近一个营销节点名（无则返回 null）。 */
    public String nearest() {
        List<String> upcoming = upcoming30Days();
        if (upcoming.isEmpty()) {
            return null;
        }
        String first = upcoming.get(0);
        int paren = first.indexOf('(');
        return paren > 0 ? first.substring(0, paren) : first;
    }
}
