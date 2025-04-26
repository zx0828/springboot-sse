package cn.zuster.sse.util;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;

/**
 * 自定义SseEmitter，强制使用UTF-8编码，解决中文乱码问题
 * 
 * 解决SSE响应中文乱码问题
 */
public class SseEmitterUTF8 extends SseEmitter {

    /**
     * UTF-8编码的text/event-stream媒体类型
     */
    public static final MediaType UTF8_TEXT_EVENT_STREAM = 
            new MediaType("text", "event-stream", StandardCharsets.UTF_8);

    /**
     * 使用默认超时创建一个新的SseEmitter
     */
    public SseEmitterUTF8() {
        super();
    }

    /**
     * 使用指定的超时创建一个新的SseEmitter
     * @param timeout 超时时间（毫秒）
     */
    public SseEmitterUTF8(Long timeout) {
        super(timeout);
    }
} 