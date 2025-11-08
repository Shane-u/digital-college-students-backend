package com.digital.model.dto.contest;

import com.digital.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 竞赛查询请求
 *
 * @author Shane
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ContestQueryRequest extends PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 竞赛分类ID（多个用逗号分隔）
     */
    private String classId;

    /**
     * 级别：0-不限，1-校级，2-市级，3-省级，4-全国性，5-全球性，6-自由，7-其他
     */
    private Integer level;

    /**
     * 竞赛名称（模糊查询）
     */
    private String contestName;
    /**
     * 时间状态
     * 13 → "报名结束"
     * 22 → "比赛进行中"
     * 23 → "比赛结束"
     */
    private Integer timeStatus;
}

