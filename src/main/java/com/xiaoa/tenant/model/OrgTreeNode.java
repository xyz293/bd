package com.xiaoa.tenant.model;

import java.util.ArrayList;
import java.util.List;

public class OrgTreeNode {

    private Long id;
    private Long parentId;
    private Integer type;
    private String name;
    private List<OrgTreeNode> children = new ArrayList<>();

    public OrgTreeNode() {
    }

    public OrgTreeNode(Org org) {
        this.id = org.getId();
        this.parentId = org.getParentId();
        this.type = org.getType();
        this.name = org.getName();
    }

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }

    public Integer getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public List<OrgTreeNode> getChildren() {
        return children;
    }
}
