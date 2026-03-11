package com.zeuslu.esearch.util;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.zeuslu.esearch.exception.NonRetryableException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

public class RetryUtil {
    public static boolean isRetryableError(Exception e) {
        if (e instanceof NonRetryableException) {
            return false;
        }
        // 1. 网络层异常 (连接超时、连接拒绝、SSL 握手失败等)
        if (e instanceof SocketTimeoutException ||
                e instanceof ConnectException ||
                e instanceof javax.net.ssl.SSLHandshakeException) {
            return true;
        }

        // 2. 通用 IO 异常 (通常意味着网络波动)
        if (e instanceof IOException) {
            // 排除一些明确的客户端配置错误 IO (视具体情况而定，通常 IO 都可重试)
            return true;
        }

        // 3. ES 服务端异常 (HTTP 状态码判断)
        if (e instanceof ElasticsearchException) {
            ElasticsearchException esEx = (ElasticsearchException) e;
            int status = esEx.status(); // 获取 HTTP Status Code

            // 429 Too Many Requests (限流，必须重试，配合退避)
            if (status == 429) {
                return true;
            }

            // 503 Service Unavailable (节点不可用，可重试)
            if (status == 503) {
                return true;
            }

            // 500 Internal Server Error (服务端内部错误，通常可重试)
            if (status == 500) {
                return true;
            }

            // 409 Conflict (版本冲突)
            // 注意：如果是外部版本控制，冲突意味着数据已变，盲目重试可能无效。
            // 但如果是网络超时导致的未知状态，重试是幂等的。
            // 建议：如果是纯 Index 操作 (覆盖写)，409 可重试；如果是 Update (带 version)，409 需特殊处理。
            if (status == 409) {
                return true;
            }

            // 4xx 其他错误 (400 Bad Request, 401 Unauthorized, 404 Not Found)
            // 这些通常是客户端数据错误或配置错误，重试无效
            if (status >= 400 && status < 500) {
                return false;
            }
        }

        // 4. ES 特定异常类型 (新客户端有时会将特定错误封装)
        // 例如：UnavailableShardsException (分片不可用)
        // 在 8.x 中通常包裹在 ElasticsearchException 中，通过 error().type() 判断
        if (e instanceof ElasticsearchException) {
            String errorType = ((ElasticsearchException) e).error().type();
            return "unavailable_shards_exception".equals(errorType) ||
                    "node_not_connected_exception".equals(errorType);
        }

        // 默认不重试
        return false;
    }
}
