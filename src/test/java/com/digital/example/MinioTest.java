package com.digital.example;

import com.digital.config.MinioConfig;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Iterator;

@SpringBootTest
public class MinioTest {
    @Autowired
    private MinioClient minioClient;
    @Autowired
    private MinioConfig minioConfig;

    @Test
    public void testListFiles() throws Exception {
        // 列出存储桶中所有文件
        Iterator<Result<Item>> iterator = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .recursive(true) // 递归查询子目录
                        .build()
        ).iterator();

        while (iterator.hasNext()) {
            Item item = iterator.next().get();
            System.out.println("文件名：" + item.objectName() + "，创建时间：" + item.lastModified());
        }
    }
}