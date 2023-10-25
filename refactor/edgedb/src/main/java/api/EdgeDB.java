package api;

import com.google.gson.Gson;
import daos.RedisProxyDAO;
import daos.SessionsDAO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static daos.RedisDAO.SESSIONS_DATA;
import static java.util.Map.entry;
import static utils.Logger.Logger;

public class EdgeDB extends RedisProxyDAO {

    private static String sessionId;
    private static EdgeDB instance = new EdgeDB();

    private EdgeDB() {
        super(SESSIONS_DATA);
    }

    public static String get(String key) {
        Logger.info("(EdgeDB.get) (sessionId: " + sessionId + ") get with key: " + key);
        return instance.hget(sessionId, key);
    }

    public static void set(String key, String value) {
        Logger.info("(EdgeDB.set) (sessionId: " + sessionId + ") set with key, value: " + key + ", " + value);
        instance.hset(sessionId, Map.ofEntries(entry(key, value)));
    }

    public static List<String> getList(String key) {
        Logger.info("(EdgeDB.getList) (sessionId: " + sessionId + ") Redis get with key: " + key);
        String rawList = instance.hget(sessionId, key);
        if (rawList == null) {
            Logger.info("(EdgeDB.getList) (sessionId: " + sessionId + ") null value from Redis get with key: <" + key + ">. Returning new empty list");
            return new ArrayList<>();
        }
        Logger.info("(EdgeDB.getList) (sessionId: " + sessionId + ") Parsing with Gson");
        return new Gson().fromJson(rawList, HList.class).list;
    }

    public static void setList(String key, List<String> list) {
        HList hlist = new HList();
        hlist.list = list;
        Logger.info("(EdgeDB.setList) (sessionId: " + sessionId + ") Parsing with Gson.toJson");
        String newJsonList = new Gson().toJson(hlist);
        Logger.info("(EdgeDB.setList) (sessionId: " + sessionId + ") Redis set with key, value: " + key + ", " + newJsonList);
        instance.hset(sessionId, Map.ofEntries(entry(key, newJsonList)));
    }

    public static void delete(String key) {
        Logger.info("(EdgeDB.delete) (sessionId: " + sessionId + ") Deleting with key: " + key);
        instance.hdel(sessionId, key);
    }

    static void setCurrentSession(String sessionId) {
        instance = new EdgeDB();
        Logger.info("(EdgeDB.setCurrentSession) Current session id: " + sessionId);
        EdgeDB.sessionId = sessionId;
    }

    static Map<String, String> getCache() {
        return instance.getLocalCache();
    }

    public static String getCurrentVirtualLocation() {
        return SessionsDAO.getSessionToken(sessionId).proprietaryLocation;
    }

    private static class HList {
        List<String> list = new ArrayList<>();
    }
}
