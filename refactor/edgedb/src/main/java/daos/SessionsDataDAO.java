package daos;

import model.sessiondata.SessionData;
import model.sessiondata.SessionRecord;
import static utils.Logger.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionsDataDAO extends RedisDAO {

    private static final SessionsDataDAO instance = new SessionsDataDAO();

    private SessionsDataDAO() {
        super(SESSIONS_DATA);
    }

    public static void setSessionData(String sessionId, SessionData data) {
        Map<String, String> mapData = new HashMap<>();

        for (var entry : data.session_data)
            mapData.put(entry.key, entry.data);

        instance.hset(sessionId, mapData);
    }

    public static void deleteSessionData(String sessionId) {
        instance.del(sessionId);
    }

    public static SessionData getSessionData(String sessionId) {
        Long length = instance.hlen(sessionId);
        SessionData data = new SessionData();
        data.session_data = new SessionRecord[Math.toIntExact(length)];

        var map = instance.hgetall(sessionId);

        int i = 0;
        for (var entry : map.entrySet()) {
            data.session_data[i] = new SessionRecord(entry.getKey(), entry.getValue());
            i++;
        }

        Logger.info("(SessionDataDAO.getSessionData) Returning session_data");
        return data;
    }

    public static List<String> getAllSessionsIds() {
        return instance.getAllKeys();
    }

    public static long getMemoryUsage(String sessionId) {
        return instance.memoryUsage(sessionId);
    }
}