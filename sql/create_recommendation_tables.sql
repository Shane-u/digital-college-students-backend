-- 推荐系统相关表结构

-- 用户行为记录表（用于收集用户对竞赛和职业的交互数据）
create table if not exists user_behavior
(
    id           bigint auto_increment comment 'id' primary key,
    userId       bigint                                 not null comment '用户id',
    itemType     varchar(32)                            not null comment '物品类型：CONTEST(竞赛)/JOB(职业)',
    itemId       bigint                                 not null comment '物品id（竞赛id或职业id）',
    behaviorType varchar(32)                            not null comment '行为类型：VIEW(浏览)/CLICK(点击)/COLLECT(收藏)/APPLY(报名/申请)/SHARE(分享)',
    behaviorValue decimal(5,2) default 1.0              null comment '行为权重值（用于计算推荐分数）',
    context      varchar(512)                           null comment '行为上下文（如来源页面、搜索关键词等）',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '行为发生时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    index idx_userId (userId),
    index idx_itemType_itemId (itemType, itemId),
    index idx_behaviorType (behaviorType),
    index idx_createTime (createTime),
    index idx_userId_itemType (userId, itemType),
    unique key uk_user_item_behavior (userId, itemType, itemId, behaviorType)
) comment '用户行为记录表' collate = utf8mb4_unicode_ci;

-- 用户画像表（存储用户特征向量和偏好）
create table if not exists user_profile
(
    id           bigint auto_increment comment 'id' primary key,
    userId       bigint                                 not null comment '用户id',
    profileType  varchar(32)                            not null comment '画像类型：CONTEST(竞赛偏好)/JOB(职业偏好)',
    featureVector text                                   null comment '特征向量（JSON格式，存储用户偏好特征）',
    preferenceTags varchar(512)                         null comment '偏好标签（逗号分隔，如：算法竞赛,机器学习,Python）',
    lastUpdateTime datetime default CURRENT_TIMESTAMP   not null comment '最后更新时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    unique key uk_user_profile (userId, profileType),
    index idx_userId (userId),
    index idx_profileType (profileType)
) comment '用户画像表' collate = utf8mb4_unicode_ci;

-- 推荐结果缓存表（存储推荐结果，减少实时计算）
create table if not exists recommendation_result
(
    id           bigint auto_increment comment 'id' primary key,
    userId       bigint                                 not null comment '用户id',
    itemType     varchar(32)                            not null comment '物品类型：CONTEST(竞赛)/JOB(职业)',
    itemId       bigint                                 not null comment '物品id',
    score        decimal(10,6)                          not null comment '推荐分数',
    algorithm    varchar(64)                            null comment '推荐算法：CONTENT_BASED(内容推荐)/COLLABORATIVE_FILTERING(协同过滤)/HYBRID(混合推荐)',
    reason       varchar(512)                            null comment '推荐理由',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    expireTime   datetime                               null comment '过期时间（推荐结果有效期）',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    index idx_userId_itemType (userId, itemType),
    index idx_score (score),
    index idx_expireTime (expireTime),
    index idx_createTime (createTime)
) comment '推荐结果缓存表' collate = utf8mb4_unicode_ci;

-- 物品特征表（存储竞赛和职业的特征向量，用于内容推荐）
create table if not exists item_feature
(
    id           bigint auto_increment comment 'id' primary key,
    itemType     varchar(32)                            not null comment '物品类型：CONTEST(竞赛)/JOB(职业)',
    itemId       bigint                                 not null comment '物品id',
    featureVector text                                   null comment '特征向量（JSON格式）',
    tags         varchar(512)                           null comment '标签（逗号分隔）',
    category     varchar(128)                            null comment '分类',
    keywords     varchar(512)                           null comment '关键词（逗号分隔）',
    lastUpdateTime datetime default CURRENT_TIMESTAMP   not null comment '最后更新时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    unique key uk_item (itemType, itemId),
    index idx_itemType (itemType),
    index idx_category (category)
) comment '物品特征表' collate = utf8mb4_unicode_ci;
