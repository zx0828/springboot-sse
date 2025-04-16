package cn.zuster.sse.session;

import cn.zuster.sse.exception.SseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * SSE Session
 *
 * @author zuster
 * @date 2021/1/5
 */
public class SseSession {
    private static final Logger logger = LoggerFactory.getLogger(SseSession.class);

    /**
     * Session维护Map
     */
    private static Map<String, SseEmitter> SESSION = new ConcurrentHashMap<>();
    
    /**
     * 存储每个emitter对应的clientId，用于安全地处理onCompletion回调
     */
    private static Map<Integer, String> EMITTER_IDS = new ConcurrentHashMap<>();

    /**
     * 判断Session是否存在
     */
    public static boolean exist(String id) {
        return SESSION.get(id) != null;
    }

    /**
     * 增加Session
     */
    public static void add(String id, SseEmitter emitter) {
        logger.info("MSG: Adding SSE Session | ID: {} | EmitterHash: {} | Date: {}", id, emitter.hashCode(), new Date());
        final SseEmitter oldEmitter = SESSION.get(id);
        
        if (oldEmitter != null) {
            EMITTER_IDS.remove(oldEmitter.hashCode());
            try {
                oldEmitter.complete();
            } catch (Exception e) {
                logger.warn("MSG: Error completing old emitter | ID: {} | Error: {}", id, e.getMessage());
            }
        }
        
        SESSION.put(id, emitter);
        EMITTER_IDS.put(emitter.hashCode(), id);
    }

    /**
     * 删除Session
     */
    public static boolean del(String id) {
        final SseEmitter emitter = SESSION.remove(id);
        if (emitter != null) {
            try {
                EMITTER_IDS.remove(emitter.hashCode());
                emitter.complete();
                return true;
            } catch (Exception e) {
                logger.warn("MSG: Error completing emitter during removal | ID: {} | Error: {}", id, e.getMessage());
            }
        }
        return false;
    }

    /**
     * 发送消息
     */
    public static boolean send(String id, Object msg) {
        final SseEmitter emitter = SESSION.get(id);
        if (emitter != null) {
            try {
                emitter.send(msg);
                return true;
            } catch (IOException e) {
                SESSION.remove(id);
                EMITTER_IDS.remove(emitter.hashCode());
                return false;
            }
        }
        return false;
    }

    /**
     * SseEmitter onCompletion 后执行的逻辑
     */
    public static void onCompletion(String id, ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    /**
     * SseEmitter onTimeout 或 onError 后执行的逻辑
     */
    public static void onError(String id, SseException e) {
        final SseEmitter emitter = SESSION.get(id);
        if (emitter != null) {
            try {
                int emitterHash = emitter.hashCode();
                SESSION.remove(id);
                EMITTER_IDS.remove(emitterHash);
                emitter.completeWithError(e);
            } catch (Exception ex) {
                logger.warn("MSG: Error completing emitter with error | ID: {} | Error: {}", id, ex.getMessage());
            }
        }
    }
    
    /**
     * 获取当前活跃的SSE会话数
     */
    public static int getActiveSessionCount() {
        return SESSION.size();
    }
}