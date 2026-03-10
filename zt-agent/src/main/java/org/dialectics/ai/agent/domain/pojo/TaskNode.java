package org.dialectics.ai.agent.domain.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务节点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskNode {
    /// 任务应使用的skill名称
    private String skillName;
    /// 任务内容
    private String taskContent;
    /// 任务执行的步骤思考
    private String thinking;
    /// 任务是否成功
    private Boolean success;
    /// 任务执行结果
    private String result;

    private static final TaskNode HEAD = TaskNode.builder().skillName("待定").taskContent("请规划第一个子任务节点").build();

    static TaskNode head() {
        return HEAD;
    }
}
