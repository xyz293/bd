package com.xiaoa.ai.chat.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * 事实核验报告（方案 ④ verify_and_charge）：商品事实必须来自已确认资料，
 * 核对不上的字段不臆造，生成时强制替换为占位符，最终在回复中列「待你核实」。
 */
public class FactReport {

    /** 已确认事实：「商品：对戒（来自你的设定）」 */
    private List<String> confirmedFacts = new ArrayList<>();
    /** 待核实项：「价格」（商品资料未提供，生成时用 [待核实：价格] 占位） */
    private List<String> toVerifyFacts = new ArrayList<>();

    public void addConfirmed(String fact) {
        if (fact != null && !fact.trim().isEmpty()) {
            confirmedFacts.add(fact.trim());
        }
    }

    public void addTodoVerify(String field) {
        if (field != null && !field.trim().isEmpty()) {
            toVerifyFacts.add(field.trim());
        }
    }

    /** 生成端 prompt 用的事实片段：价格等缺失字段标注占位符要求。 */
    public String toPromptFragment() {
        if (toVerifyFacts.isEmpty()) {
            return "全部信息已确认，无占位符";
        }
        StringBuilder fragment = new StringBuilder("以下字段资料缺失，文案中必须使用占位符：");
        for (int i = 0; i < toVerifyFacts.size(); i++) {
            String field = toVerifyFacts.get(i);
            fragment.append("[待核实：").append(field).append("]");
            if (i < toVerifyFacts.size() - 1) {
                fragment.append("、");
            }
        }
        return fragment.toString();
    }

    public List<String> getConfirmedFacts() { return confirmedFacts; }
    public void setConfirmedFacts(List<String> confirmedFacts) { this.confirmedFacts = confirmedFacts; }
    public List<String> getTodoVerifyFacts() { return toVerifyFacts; }
    public void setTodoVerifyFacts(List<String> toVerifyFacts) { this.toVerifyFacts = toVerifyFacts; }
}
