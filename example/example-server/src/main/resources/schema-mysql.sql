-- ============================================================
-- OCR 业务表
-- ============================================================

CREATE TABLE IF NOT EXISTS ocr_file_metadata (
                                                 file_id       varchar(64)  NOT NULL                             COMMENT '业务文件ID，由Client生成',
    original_name varchar(512) NOT NULL                             COMMENT '客户端上传时的原始文件名',
    extension     varchar(32)  DEFAULT NULL                         COMMENT '文件扩展名，不带点',
    content_type  varchar(255) DEFAULT NULL                         COMMENT '客户端声明的MIME类型',
    file_size     bigint       NOT NULL                             COMMENT '原始文件总字节数',
    chunk_size    int          NOT NULL                             COMMENT '每个文件分块的最大字节数',
    chunk_count   int          NOT NULL                             COMMENT '文件分块数量',
    sha256        char(64)     NOT NULL                             COMMENT '原始文件SHA-256十六进制摘要',
    created_at    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (file_id),
    KEY idx_ocr_file_created_at (created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCR业务文件元数据';


CREATE TABLE IF NOT EXISTS ocr_file_chunk (
                                              file_id     varchar(64) NOT NULL                                COMMENT '对应ocr_file_metadata.file_id',
    chunk_index int         NOT NULL                                COMMENT '分块序号，从0开始',
    chunk_data  mediumblob  NOT NULL                                COMMENT '文件原始二进制分块',
    chunk_size  int         NOT NULL                                COMMENT '当前分块实际字节数',
    created_at  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (file_id, chunk_index)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCR业务文件二进制分块';


CREATE TABLE IF NOT EXISTS ocr_result (
                                          file_id        varchar(64)  NOT NULL                            COMMENT '业务文件ID，也是OCR结果关联键',
    request_id     varchar(128) DEFAULT NULL                        COMMENT 'ServiceBridge请求ID，供排查关联使用',
    status         varchar(20)  NOT NULL                            COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED',
    result_content longtext                                         COMMENT 'OCR服务返回的原始响应文本，通常是JSON',
    result_type    varchar(128) DEFAULT NULL                        COMMENT 'OCR响应的Content-Type',
    error_code     varchar(128) DEFAULT NULL                        COMMENT '失败错误码',
    error_message  text                                             COMMENT '失败原因，注意不要写入敏感信息',
    started_at     datetime(3)  DEFAULT NULL,
    finished_at    datetime(3)  DEFAULT NULL,
    created_at     datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (file_id),
    KEY idx_ocr_result_request_id (request_id),
    KEY idx_ocr_result_status_updated (status, updated_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCR业务处理结果';
