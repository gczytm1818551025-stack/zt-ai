package org.dialectics.ai.agent.agent;

import org.dialectics.ai.common.enums.AgentTypeEnum;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentFactory {
    public static final Map<AgentTypeEnum, Agent> AGENT_CLASS_MAP = new ConcurrentHashMap<>();

    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void initializingRegisterAgents() {
        applicationContext.getBeansOfType(Agent.class).forEach((beanName, bean) -> {
            Assert.notNull(AgentTypeEnum.nameOf(beanName), "agentType must not be null");
            Assert.notNull(bean, "agent bean must not be null");
            AGENT_CLASS_MAP.put(AgentTypeEnum.nameOf(beanName), bean);
        });
    }

    public Agent getAgent(AgentTypeEnum agentType) {
        Agent agent = AGENT_CLASS_MAP.get(agentType);
        if (agent == null) {
            throw new RuntimeException("未找到对应的智能体");
        }
        return agent;
    }

}
