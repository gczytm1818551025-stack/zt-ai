package org.dialectics.ai.server.interceptor;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 操作数据库前自动填充需要更新的内容，只支持单个对象，不支持批量插入更新时的填充
 **/
@Component
@Slf4j
public class MetaObjectAutoFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        log.info("插入时自动填充...");
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.info("更新时自动填充...");
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }
}
