# 数据库初始化

-- 创建库
create database if not exists digital_college_students_backend;

-- 切换库
use digital_college_students_backend;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号（邮箱或者手机号）',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userPhone    varchar(512)                           null comment '用户手机号',
    userEmail    varchar(512)                           null comment '用户邮箱',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin/ban',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除'
) comment '用户表' collate = utf8mb4_unicode_ci;

-- 验证码表
create table if not exists verification_code
(
    id           bigint auto_increment comment 'id' primary key,
    account      varchar(256)                           not null comment '手机号/邮箱',
    code         varchar(6)                             not null comment '验证码',
    type         varchar(10)                            not null comment '类型：PHONE/EMAIL',
    status       tinyint      default 0                 not null comment '状态：0-未使用 1-已使用',
    expireTime   datetime                               not null comment '过期时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除'
) comment '验证码表' collate = utf8mb4_unicode_ci;

-- BOSS直聘招聘信息表
create table if not exists job_info
(
    id           bigint auto_increment comment 'id' primary key,
    url          varchar(1024)                          null comment '招聘链接',
    workName     varchar(256)                          null comment '工作名称',
    workSalary   varchar(128)                           null comment '薪水',
    workAddress  varchar(512)                          null comment '工作地址',
    workContent  text                                   null comment '工作内容',
    workYear     varchar(128)                           null comment '要求工作年限',
    graduate     varchar(128)                           null comment '学历要求',
    hrTime       varchar(128)                           null comment '招聘人什么时候活跃',
    companyName  varchar(256)                          null comment '公司名',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    index idx_url (url(255)),
    index idx_companyName (companyName),
    index idx_createTime (createTime)
) comment 'BOSS直聘招聘信息表' collate = utf8mb4_unicode_ci;

-- 成长记录表
create table if not exists growth_record
(
    id           bigint auto_increment comment '成长记录id' primary key,
    userId       bigint                                 not null comment '用户id',
    eventDesc    varchar(500)                           null comment '事件描述',
    reflection   text                                   null comment '个人感悟',
    importance   decimal(2,1)                           null comment '重要程度（最多五颗星，支持4.5颗星）',
    recordTime   datetime                               null comment '记录的时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    index idx_userId (userId),
    index idx_recordTime (recordTime),
    index idx_importance (importance),
    index idx_createTime (createTime)
) comment '成长记录表' collate = utf8mb4_unicode_ci;

-- 文件表
create table if not exists growth_file
(
    id           bigint auto_increment comment '文件id' primary key,
    userId       bigint                                 not null comment '用户id',
    growthRecordId bigint                               null comment '成长记录id',
    fileUrl      varchar(1024)                          not null comment '存储在minio的文件地址',
    fileName     varchar(256)                           null comment '文件名',
    fileSize     bigint                                 null comment '文件大小（字节）',
    fileMd5      varchar(64)                            null comment '文件MD5值',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '存储时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    index idx_userId (userId),
    index idx_growthRecordId (growthRecordId),
    index idx_fileMd5 (fileMd5),
    index idx_createTime (createTime)
) comment '文件表' collate = utf8mb4_unicode_ci;

-- 图片表
create table if not exists growth_image
(
    id           bigint auto_increment comment '图片id' primary key,
    userId       bigint                                 not null comment '用户id',
    growthRecordId bigint                               null comment '成长记录id（如果是记录在成长记录中的才有）',
    imageUrl     varchar(1024)                          not null comment '存储在minio的图片地址',
    imageName    varchar(256)                            null comment '图片名',
    imageSize    bigint                                 null comment '图片大小（字节）',
    imageMd5     varchar(64)                            null comment '图片MD5值',
    type         tinyint      default 1                 not null comment '类型：1-单纯作为照片存储，2-成长记录中的照片',
    uploadTime   datetime                               null comment '目标上传时间（用户选择的上传时间）',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '存储时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    index idx_userId (userId),
    index idx_growthRecordId (growthRecordId),
    index idx_type (type),
    index idx_imageMd5 (imageMd5),
    index idx_uploadTime (uploadTime),
    index idx_createTime (createTime)
) comment '图片表' collate = utf8mb4_unicode_ci;