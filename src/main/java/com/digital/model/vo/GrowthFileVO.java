package com.digital.model.vo;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 成长记录文件视图对象
 *
 * @author Shane
 */
@Data
public class GrowthFileVO implements Serializable {

    /**
     * 文件id
     */
    private Long id;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件访问地址
     */
    private String fileUrl;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 存储时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}

