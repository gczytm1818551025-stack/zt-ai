package org.dialectics.ai.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dialectics.ai.server.domain.pojo.ChatRecord;

@Mapper
public interface ChatRecordMapper extends BaseMapper<ChatRecord> {
}
