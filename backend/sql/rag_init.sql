# RAG 课程知识库初始化表结构
# @author <a href="https://codefather.cn">编程导航学习圈</a>

use ai_passage_creator;

-- 课程知识库表
create table if not exists course_knowledge_base
(
    id            bigint auto_increment comment 'id' primary key,
    kbId          varchar(64)                        not null comment '知识库唯一标识（UUID）',
    userId        bigint                             not null comment '创建者用户ID',
    name          varchar(128)                       not null comment '知识库名称',
    courseName    varchar(128)                       null comment '课程名称',
    description   text                               null comment '知识库描述',
    status        varchar(32)  default 'NORMAL'      not null comment '状态：NORMAL/DISABLED',
    documentCount int          default 0             not null comment '文档数量',
    chunkCount    int          default 0             not null comment '切片数量',
    createTime    datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint      default 0             not null comment '是否删除',
    UNIQUE KEY uk_kbId (kbId),
    INDEX idx_userId (userId)
) comment '课程知识库表' collate = utf8mb4_unicode_ci;

-- 课程文档表
create table if not exists course_document
(
    id            bigint auto_increment comment 'id' primary key,
    docId         varchar(64)                        not null comment '文档唯一标识（UUID）',
    kbId          varchar(64)                        not null comment '所属知识库ID',
    userId        bigint                             not null comment '上传用户ID',
    fileName      varchar(256)                       not null comment '原始文件名',
    fileUrl       varchar(512)                       null comment '文件存储路径',
    fileType      varchar(32)                        not null comment '文件类型：txt/md',
    fileSize      bigint                             not null comment '文件大小（字节）',
    parseStatus   varchar(32)  default 'PENDING'     not null comment '解析状态：PENDING/PARSING/SUCCESS/FAILED',
    parseError    text                               null comment '解析失败原因',
    chunkCount    int          default 0             not null comment '切片数量',
    createTime    datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint      default 0             not null comment '是否删除',
    UNIQUE KEY uk_docId (docId),
    INDEX idx_kbId (kbId),
    INDEX idx_userId (userId)
) comment '课程文档表' collate = utf8mb4_unicode_ci;

-- 文档切片表
create table if not exists course_document_chunk
(
    id            bigint auto_increment comment 'id' primary key,
    chunkId       varchar(64)                        not null comment '切片唯一标识（UUID）',
    kbId          varchar(64)                        not null comment '所属知识库ID',
    docId         varchar(64)                        not null comment '所属文档ID',
    chunkIndex    int                                not null comment '切片序号',
    content       text                               not null comment '切片文本内容',
    tokenCount    int          default 0             null comment '大致token数',
    embedding     text                               null comment 'embedding向量（JSON数组格式）',
    createTime    datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint      default 0             not null comment '是否删除',
    UNIQUE KEY uk_chunkId (chunkId),
    INDEX idx_kbId (kbId),
    INDEX idx_docId (docId)
) comment '文档切片表' collate = utf8mb4_unicode_ci;

-- article 表新增 RAG 相关字段
ALTER TABLE article
    ADD COLUMN kbId       VARCHAR(64)  null comment '关联的课程知识库ID，为空表示不使用知识库',
    ADD COLUMN ragEnabled TINYINT      default 0 not null comment '是否启用RAG：0不启用/1启用';