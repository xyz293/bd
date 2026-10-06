package com.xiaoa.ai.chat.dto;

/**
 * 选项卡选项（对齐创作顾问人设的出题规范）：
 * label 短句通俗、hint 写易懂后果；slotKey 为该选项归属的槽位维度（可空），
 * 用户点选后由后端把选择回填到对应槽位（Gate 复判「已有值不重复问」），前端无需感知。
 */
public class QOptionVO {

    /** 选项标识：A/B/C/D */
    private String key;
    /** 展示文案（短句）：「朋友圈」 */
    private String label;
    /** 辅助说明（易懂后果）：「熟客日常触达，九宫格或单图配短文案」 */
    private String hint;
    /** 选项归属槽位维度（platform/scene/product/festival/style，可空=无对应槽位） */
    private String slotKey;

    public QOptionVO() {
    }

    public QOptionVO(String key, String label, String hint) {
        this.key = key;
        this.label = label;
        this.hint = hint;
    }

    public QOptionVO(String key, String label, String hint, String slotKey) {
        this.key = key;
        this.label = label;
        this.hint = hint;
        this.slotKey = slotKey;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }
    public String getSlotKey() { return slotKey; }
    public void setSlotKey(String slotKey) { this.slotKey = slotKey; }
}
