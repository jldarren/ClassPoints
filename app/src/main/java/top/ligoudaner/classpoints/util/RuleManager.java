package top.ligoudaner.classpoints.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import top.ligoudaner.classpoints.model.Rule;

public class RuleManager {
    private static List<Rule> allRules = new ArrayList<>();
    private static Map<String, List<Rule>> ruleGroups = new HashMap<>();

    static {
        // 纪律: 包括<纪律>一项
        addRule("纪律", "违反班规", -2);
        addRule("纪律", "迟到早退", -4);
        addRule("纪律", "无故旷课", -10);
        addRule("纪律", "自习课随意下座位/聊天/去厕所", -5);
        addRule("纪律", "自习课随意下座位/聊天/去厕所(经提醒不改)", -10);
        addRule("纪律", "同学间发生矛盾/起口角/动手", -10);
        addRule("纪律", "劝架", 5);
        addRule("纪律", "顶撞班长或纪律/行规委员", -5);

        // 学习: 包括<课堂>和<作业>两项
        addRule("学习", "自习、课堂迟到或不守纪律", -1);
        addRule("学习", "顶撞老师", -5);
        addRule("学习", "导致课堂无法进行", -10);
        addRule("学习", "月考270分以上", 5);
        addRule("学习", "月考240分以上", 4);
        addRule("学习", "月考210分以上", 3);
        addRule("学习", "月考180分以上", 1);
        addRule("学习", "年级前十(期中/期末)", 6);
        addRule("学习", "考试提前交卷/不认真检查", -10);
        addRule("学习", "上课聊天/喝水/睡觉", -5);
        addRule("学习", "上课聊天/喝水/睡觉(不改)", -10);
        addRule("学习", "未按要求完成复习/预习/背诵", -2);
        addRule("学习", "提前完成复习/预习/背诵", 2);
        addRule("学习", "作业迟交/忘带/未做", -5);
        addRule("学习", "作业字迹不工整清楚", -2);

        // 安全: 包括<公物保护>一项
        addRule("安全", "损坏班级、学校公物", -5);
        addRule("安全", "严重损坏班级、学校公物", -10);
        addRule("安全", "乱画/浪费教学用品", -5);

        // 劳动: 包括<劳动>一项
        addRule("劳动", "无故不参加组值日", -5);
        addRule("劳动", "打扫不积极导致上课迟到", -2);
        addRule("劳动", "不服从组长安排", -5);
        addRule("劳动", "参加义务劳动不到", -2);
        addRule("劳动", "参加义务劳动不积极", -1);
        addRule("劳动", "参加义务劳动积极", 3);

        // 文明礼仪: 包括<卫生>和<个人素质/修养>两项
        addRule("文明礼仪", "浪费饮用水/倒入垃圾桶", -1);
        addRule("文明礼仪", "乱扔垃圾", -2);
        addRule("文明礼仪", "对老师没礼貌", -1);
        addRule("文明礼仪", "顶撞任课教师", -3);
        addRule("文明礼仪", "侮辱/嘲笑/欺负同学", -5);
        addRule("文明礼仪", "无礼取闹/顶撞班干部", -2);
        addRule("文明礼仪", "班干部带头违反班级纪律(双倍)", -4);
        addRule("文明礼仪", "班干部以权谋私", -20);
        addRule("文明礼仪", "打架/肆意闹事", -10);
        addRule("文明礼仪", "对班级形象造成不良影响", -20);
        addRule("文明礼仪", "说脏话/粗话/绰号", -5);
        addRule("文明礼仪", "参加班级黑板报出刊", 5);
        addRule("文明礼仪", "被学校表扬", 20);

        // 两操: 包括<两操>一项
        addRule("两操", "两操/眼保不认真", -2);
        addRule("两操", "集会迟到/不遵守纪律", -1);
    }

    private static void addRule(String category, String desc, double score) {
        Rule rule = new Rule(category, desc, score);
        allRules.add(rule);
        if (!ruleGroups.containsKey(category)) {
            ruleGroups.put(category, new ArrayList<>());
        }
        ruleGroups.get(category).add(rule);
    }

    public static List<String> getCategories() {
        return new ArrayList<>(ruleGroups.keySet());
    }

    public static List<Rule> getRulesByCategory(String category) {
        return ruleGroups.getOrDefault(category, new ArrayList<>());
    }
}
