package com.digital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.digital.model.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户画像Mapper
 *
 * @author Shane
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
