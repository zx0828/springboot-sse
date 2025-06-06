package cn.zuster.sse.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 相关业务接口
 *
 * @author zuster
 * @date 2021/1/5
 */
public interface SseService {
    /**
     * 新建连接
     *
     * @param clientId 客户端ID
     * @param autoCloseAfterData 数据发送后是否自动关闭连接
     * @return
     */
    SseEmitter start(String clientId, Boolean autoCloseAfterData);

    /**
     * 发送数据
     *
     * @param clientId 客户端ID
     * @return
     */
    String send(String clientId);

    /**
     * 关闭连接
     *
     * @param clientId 客户端ID
     * @return
     */
    String close(String clientId);
}
