package br.com.battlebits.commons.core.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import br.com.battlebits.commons.core.data.payload.DataServerMessage;
import br.com.battlebits.commons.core.data.payload.DataServerMessage.*;
import org.bson.Document;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

import br.com.battlebits.commons.BattlebitsAPI;

import br.com.battlebits.commons.core.server.ServerType;
import br.com.battlebits.commons.core.server.loadbalancer.server.BattleServer;
import br.com.battlebits.commons.core.server.loadbalancer.server.MinigameState;
import br.com.battlebits.commons.core.translate.Language;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

public class DataServer extends Data {

    // TRANSLATIONS
    @SuppressWarnings("unchecked")
    public static Map<String, String> loadTranslation(Language language) {
        MongoDatabase database = BattlebitsAPI.getCommonsMongo().getClient().getDatabase("commons");
        MongoCollection<Document> collection = database.getCollection("translation");
        Document found = collection.find(Filters.eq("language", language.toString())).first();
        if (found != null) {
            return (Map<String, String>) found.get("map");
        }
        collection.insertOne(new Document("language", language.toString()).append("map", new HashMap<>()));
        return new HashMap<>();
    }

    public static void addTranslationTag(Language language, String tag, String dbStr) {
        MongoDatabase database = BattlebitsAPI.getCommonsMongo().getClient().getDatabase(dbStr);
        MongoCollection<Document> collection = database.getCollection("translation");
        Document found = collection.find(Filters.eq("language", language.toString())).first();
        if (found != null) {
            collection.updateOne(Filters.eq("language", language.toString()), new Document("$set", new Document("map." + tag, tag.replace("-", " "))));
        } else {
            HashMap<String, String> str = new HashMap<>();
            str.put(tag, "[NOT FOUND: '" + tag + "']");
            collection.insertOne(new Document("language", language.toString()).append("map", str));
        }
    }

    // SERVER STATUS

    public static String getServerId(String ipAddress) {
        try {
            MongoDatabase database = BattlebitsAPI.getCommonsMongo().getClient().getDatabase("commons");
            MongoCollection<Document> collection = database.getCollection("serverId");
            Document found = collection.find(Filters.eq("address", ipAddress)).first();
            if (found != null) {
                return found.getString("hostname");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ipAddress;
    }

    public static ServerType getServerType(String ipAddress) {
        try {
            MongoDatabase database = BattlebitsAPI.getCommonsMongo().getClient().getDatabase("commons");
            MongoCollection<Document> collection = database.getCollection("serverId");
            Document found = collection.find(Filters.eq("address", ipAddress)).first();
            if (found != null) {
                return ServerType.valueOf(found.getString("serverType"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ServerType.NONE;
    }

    public static Set<UUID> getPlayers(String serverId) {
        Set<UUID> players = new HashSet<>();
        try (Jedis jedis = BattlebitsAPI.getCommonsRedis().getPool().getResource()) {
            for (String uuid : jedis.hgetAll("server:" + serverId + ":players").values()) {
                UUID uniqueId = UUID.fromString(uuid);
                players.add(uniqueId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return players;
    }

    public static Map<String, Map<String, String>> getAllServers() {
        Map<String, Map<String, String>> map = new HashMap<>();
        try (Jedis jedis = BattlebitsAPI.getCommonsRedis().getPool().getResource()) {
            String[] str = new String[ServerType.values().length];
            for (int i = 0; i < ServerType.values().length; i++) {
                str[i] = "server:type:" + ServerType.values()[i].toString().toLowerCase();
            }
            for (String server : jedis.sunion(str)) {
                Map<String, String> m = jedis.hgetAll("server:" + server);
                m.put("onlineplayers", getPlayerCount(server) + "");
                map.put(server, m);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
        return map;
    }

    public static void newServer(int maxPlayers) {
        try (Jedis jedis = BattlebitsAPI.getCommonsRedis().getPool().getResource()) {
            Pipeline pipe = jedis.pipelined();
            pipe.sadd("server:type:" + BattlebitsAPI.getServerType().toString().toLowerCase(), BattlebitsAPI.getServerId());
            HashMap<String, String> map = new HashMap<>();
            map.put("type", BattlebitsAPI.getServerType().toString().toLowerCase());
            map.put("maxplayers", maxPlayers + "");
            map.put("joinenabled", "true");
            map.put("address", BattlebitsAPI.getServerAddress());
            pipe.hmset("server:" + BattlebitsAPI.getServerId(), map);
            pipe.del("server:" + BattlebitsAPI.getServerId() + ":players");
            BattleServer server = new BattleServer(BattlebitsAPI.getServerId(), BattlebitsAPI.getServerType(), new HashSet<>(), maxPlayers, true);
            pipe.publish("server-info", BattlebitsAPI.getGson().toJson(new DataServerMessage<StartPayload>(BattlebitsAPI.getServerId(), BattlebitsAPI.getServerType(), Action.START, new StartPayload(BattlebitsAPI.getServerAddress(), server))));
            pipe.sync();
        }
    }

    public static void updateStatus(MinigameState state, int time) {
        try (Jedis jedis = BattlebitsAPI.getCommonsRedis().getPool().getResource()) {
            Pipeline pipe = jedis.pipelined();
            pipe.hset("server:" + BattlebitsAPI.getServerId(), "state", state.toString().toLowerCase());
            pipe.hset("server:" + BattlebitsAPI.getServerId(), "time", time + "");
            pipe.publish("server-info", BattlebitsAPI.getGson().toJson(new DataServerMessage<UpdatePayload>(BattlebitsAPI.getServerId(), BattlebitsAPI.getServerType(), Action.UPDATE, new UpdatePayload(time, state))));
            pipe.sync();
        }
    }

    public static void setJoinEnabled(boolean bol) {
        try (Jedis jedis = BattlebitsAPI.getCommonsRedis().getPool().getResource()) {
            Pipeline pipe = jedis.pipelined();
            pipe.hset("server:" + BattlebitsAPI.getServerId(), "joinenabled", bol + "");
            pipe.publish("server-info", BattlebitsAPI.getGson().toJson(new DataServerMessage<JoinEnablePayload>(BattlebitsAPI.getServerId(), BattlebitsAPI.getServerType(), Action.JOIN_ENABLE, new JoinEnablePayload(bol))));
            pipe.sync();
        }
    }

    public static void stopServer() {
        try (Jedis jedis = BattlebitsAPI.getCommonsRedis().getPool().getResource()) {
            Pipeline pipe = jedis.pipelined();
            pipe.srem("server:type:" + BattlebitsAPI.getServerType().toString().toLowerCase(), BattlebitsAPI.getServerId());
            pipe.del("server:" + BattlebitsAPI.getServerId());
            pipe.del("server:" + BattlebitsAPI.getServerId() + ":players");
            pipe.publish("server-info", BattlebitsAPI.getGson().toJson(new DataServerMessage<StopPayload>(BattlebitsAPI.getServerId(), BattlebitsAPI.getServerType(), Action.STOP, new StopPayload(BattlebitsAPI.getServerId()))));
            pipe.sync();
        }
    }

    public static void joinPlayer(UUID uuid) {
        try (Jedis jedis = BattlebitsAPI.getCommonsRedis().getPool().getResource()) {
            Pipeline pipe = jedis.pipelined();
            pipe.sadd("server:" + BattlebitsAPI.getServerId() + ":players", uuid.toString());
            pipe.publish("server-info", BattlebitsAPI.getGson().toJson(new DataServerMessage<JoinPayload>(BattlebitsAPI.getServerId(), BattlebitsAPI.getServerType(), Action.JOIN, new JoinPayload(uuid))));
            pipe.sync();
        }
    }

    public static void leavePlayer(UUID uuid) {
        try (Jedis jedis = BattlebitsAPI.getCommonsRedis().getPool().getResource()) {
            Pipeline pipe = jedis.pipelined();
            pipe.srem("server:" + BattlebitsAPI.getServerId() + ":players", uuid.toString());
            pipe.publish("server-info", BattlebitsAPI.getGson().toJson(new DataServerMessage<LeavePayload>(BattlebitsAPI.getServerId(), BattlebitsAPI.getServerType(), Action.LEAVE, new LeavePayload(uuid))));
            pipe.sync();
        }
    }

    public static long getPlayerCount(String serverId) {
        long number;
        try (Jedis jedis = BattlebitsAPI.getCommonsRedis().getPool().getResource()) {
            number = jedis.scard("server:" + serverId + ":players");
        }
        return number;
    }

    public static long getPlayerCount(ServerType serverType) {
        long number = 0L;
        try (Jedis jedis = BattlebitsAPI.getCommonsRedis().getPool().getResource()) {
            Set<String> servers = jedis.smembers("server:type:" + serverType.toString().toLowerCase());
            for (String serverId : servers) {
                number += jedis.scard("server:" + serverId + ":players");
            }
        }
        return number;
    }
}
