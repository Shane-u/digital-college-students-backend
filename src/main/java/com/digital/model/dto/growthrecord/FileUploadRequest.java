package com.digital.model.dto.growthrecord;

import java.io.Serializable;
import lombok.Data;

/**
 * 文件上传请求
 *
 * @author Shane
 */
@Data
public class FileUploadRequest implements Serializable {

    /**
     * 文件MD5值（用于秒传）
     */
    private String md5;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小
     */
    private Long fileSize;

    private static final long serialVersionUID = 1L;
}

