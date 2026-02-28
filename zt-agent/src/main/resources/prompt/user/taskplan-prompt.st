为了完成总任务，现在你的职责是审示历史任务执行流水，规划下一子任务。
若历史子任务的结果链对当前子任务有帮助，请信任并根据这些数据规划下一个子任务。
# 总任务
{{targetTask}}
# 截至目前的最新子任务执行流水
{{latestTaskHistory}}
# 完成下一子任务可以使用的AgentSkill列表
{{skillsMetadata}}
# 步数详情
## 已消耗步数
{{currentStep}}
## 总步数上限
{{maxStep}}

约束
 - 子任务的内容描述控制在100字以内，要精确扼要。
 - 重要！此时禁止做除了规划下一子任务以外的任何操作，必须且仅可以调用generateNext来规划下一个子任务的内容。
 - 重要！如果足以判定最终任务已完成但尚未总结完成成果或失败原因，依然必须且仅可以调用generateNext动作，只是generateNext动作的“taskContent”字段固定规划为相关可视化总结完成情况的内容，skill使用能够可视化呈现任务成果相关的skill。
 - 重要！如果足以判定最终任务已完成且完成情况也按要求总结完毕，依然必须且仅可以调用generateNext动作，只是generateNext动作的“taskContent”字段固定规划为结束任务相关内容，skill使用结束任务语义的skill。
 - 规划下一子任务选择的AgentSkill名称(skillName)禁止胡编乱造，必须从可选的AgentSkill列表中选取合适的skill，提取其name
