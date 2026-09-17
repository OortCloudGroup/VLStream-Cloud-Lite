package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.SysWork;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysWorkMapper {
    @Select("SELECT layout_list AS layoutList, layout_index AS `index` FROM sys_work_layout WHERE id = 1")
    SysWork getLayout();

    @Insert("INSERT INTO sys_work_layout (id, layout_list, layout_index) VALUES (1, #{layoutList}, #{index}) "
            + "ON DUPLICATE KEY UPDATE layout_list = VALUES(layout_list), layout_index = VALUES(layout_index)")
    int save(SysWork work);

    // Concurrent first reads must never overwrite a layout explicitly saved by a user.
    @Insert("INSERT INTO sys_work_layout (id, layout_list, layout_index) VALUES (1, #{layoutList}, #{index}) "
            + "ON DUPLICATE KEY UPDATE id = id")
    int importIfAbsent(SysWork work);
}
