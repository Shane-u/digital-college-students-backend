package com.digital.model.vo;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 成长记录图片视图对象
 *
 * @author Shane
 */
@Data
public class GrowthImageVO implements Serializable {

    /**
     * 图片id
     */
    private Long id;

    /**
     * 图片名
     */
    private String imageName;

    /**
     * 图片访问地址
     */
    private String imageUrl;

    /**
     * 图片大小（字节）
     */
    private Long imageSize;

    /**
     * 类型：1-单纯作为照片存储，2-成长记录中的照片
     */
    private Integer type;

    /**
     * 目标上传时间（用户选择的上传时间）
     */
    private Date uploadTime;

    /**
     * 存储时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}

