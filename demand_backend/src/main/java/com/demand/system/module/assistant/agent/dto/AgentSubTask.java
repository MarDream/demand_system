package com.demand.system.module.assistant.agent.dto;

import java.util.ArrayList;
import java.util.List;

/** 规划器拆分出的子任务 */
public class AgentSubTask {

    /** 子任务类型：research / data_analysis / navigation / general */
    private String type;
    /** 子任务标题（展示给用户） */
    private String title;
    /** 执行指令（交给子 Agent 的完整任务描述） */
    private String instruction;

    public AgentSubTask() {}

    public AgentSubTask(String type, String title, String instruction) {
        this.type = type;
        this.title = title;
        this.instruction = instruction;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }

    public static List<AgentSubTask> single(String instruction) {
        List<AgentSubTask> tasks = new ArrayList<>();
        tasks.add(new AgentSubTask("general", "处理用户问题", instruction));
        return tasks;
    }
}
