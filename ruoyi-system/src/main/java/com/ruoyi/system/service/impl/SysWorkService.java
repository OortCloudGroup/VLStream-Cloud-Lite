package com.ruoyi.system.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.system.domain.SysWork;
import com.ruoyi.system.mapper.SysWorkMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SysWorkService {
    private static final Logger log = LoggerFactory.getLogger(SysWorkService.class);
    @Autowired
    private SysWorkMapper mapper;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Transactional
    public SysWork getLayout() {
        SysWork saved = mapper.getLayout();
        if (saved != null) return saved;
        // One-way compatibility import. Retain the old key for rollback; MySQL always wins.
        SysWork legacy;
        try {
            Object value = redisTemplate.opsForValue().get(CacheConstants.SYS_WORK_LIST);
            if (value == null) return null;
            legacy = value instanceof SysWork ? (SysWork) value
                    : JSON.parseObject(value instanceof String ? (String) value : JSON.toJSONString(value), SysWork.class);
        } catch (RuntimeException e) {
            log.warn("旧工作台缓存暂不可读取，保留原数据，后续读取时重试");
            return null;
        }
        if (legacy == null || legacy.getLayoutList() == null) return null;
        mapper.importIfAbsent(legacy);
        return mapper.getLayout();
    }

    @Transactional
    public void save(SysWork work) {
        mapper.save(work);
    }
}
