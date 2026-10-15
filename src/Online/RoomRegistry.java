package Online;

import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;
import Board.GameState.Orientation;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All room mutations share one short critical section; a revision describes one whole state. */
final class RoomRegistry {
    record Reply(OnlineState state, String credential) {}

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final OnlineServer.Limits limits;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<String, Player> credentials = new HashMap<>();

    RoomRegistry(OnlineServer.Limits limits) {
        this.limits = limits;
    }

    synchronized Reply handle(String action, Map<String, String> fields, String token)
            throws IOException {
        long now = System.nanoTime();
        cleanup(now);
        if (action.equals("create") || action.equals("join") || action.equals("match")) {
            if (token != null) {
                throw error("ALREADY_IN_ROOM", 409);
            }
            return enter(action, fields, now);
        }
        Player player = credentials.get(token);
        if (player == null) {
            throw error("UNAUTHORIZED", 401);
        }
        player.lastSeen = now;
        return switch (action) {
            case "state" -> {
                Wire.requireKeys(fields);
                yield new Reply(snapshot(player), null);
            }
            case "leave" -> {
                Wire.requireKeys(fields);
                yield leave(player);
            }
            case "move", "wall" -> act(player, action, fields, now);
            default -> throw error("NOT_FOUND", 404);
        };
    }

    synchronized void cleanup() {
        cleanup(System.nanoTime());
    }

    private Reply enter(String action, Map<String, String> fields, long now) throws IOException {
        Wire.requireKeys(
                fields,
                action.equals("join")
                        ? new String[] {"code", "name"}
                        : new String[] {"count", "name"});
        String name;
        try {
            name =
                    new GameConfig(
                                    fields.get("name"),
                                    "Player",
                                    GameConfig.Mode.LOCAL,
                                    BotPlayer.Difficulty.EASY)
                            .blueName();
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw error("INVALID_REQUEST", 400);
        }
        Room room;
        if (action.equals("join")) {
            String code = fields.get("code");
            if (!code.matches("[A-Z2-9]{8}")) {
                throw error("INVALID_REQUEST", 400);
            }
            room = rooms.get(code);
            if (room == null || !room.privateRoom) {
                throw error("ROOM_NOT_FOUND", 404);
            }
            if (room.status == OnlineState.Status.CLOSED) {
                throw error("ROOM_CLOSED", 410);
            }
            if (room.status != OnlineState.Status.WAITING || room.players.size() >= room.count) {
                throw error("ROOM_FULL", 409);
            }
        } else {
            int count = Wire.integer(fields, "count", 2, 4);
            if (count != 2 && count != 4) {
                throw error("INVALID_REQUEST", 400);
            }
            room = null;
            if (action.equals("match")) {
                for (Room candidate : rooms.values()) {
                    if (!candidate.privateRoom
                            && candidate.count == count
                            && candidate.status == OnlineState.Status.WAITING
                            && candidate.players.size() < count) {
                        room = candidate;
                        break;
                    }
                }
            }
            if (room == null) {
                if (rooms.size() >= limits.maxRooms()) {
                    throw error("SERVER_FULL", 503);
                }
                String code;
                do {
                    StringBuilder value = new StringBuilder(8);
                    for (int index = 0; index < 8; index++) {
                        value.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
                    }
                    code = value.toString();
                } while (rooms.containsKey(code));
                room = new Room(code, count, action.equals("create"), now);
                rooms.put(code, room);
            }
        }
        byte[] secret = new byte[32];
        String credential;
        do {
            random.nextBytes(secret);
            credential = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        } while (credentials.containsKey(credential));
        Player player = new Player(room, name, credential, now);
        room.players.add(player);
        credentials.put(credential, player);
        room.revision++;
        if (room.players.size() == room.count) {
            room.session =
                    new GameSession(
                            new GameState(room.count),
                            new GameConfig(
                                    names(room), GameConfig.Mode.LOCAL, BotPlayer.Difficulty.EASY));
            room.status = OnlineState.Status.PLAYING;
        }
        return new Reply(snapshot(player), credential);
    }

    private Reply act(Player player, String action, Map<String, String> fields, long now)
            throws IOException {
        boolean wall = action.equals("wall");
        Wire.requireKeys(
                fields,
                wall
                        ? new String[] {"row", "col", "orientation", "revision"}
                        : new String[] {"row", "col", "revision"});
        Room room = player.room;
        if (room.status == OnlineState.Status.CLOSED) {
            throw error("ROOM_CLOSED", 410);
        }
        if (room.status != OnlineState.Status.PLAYING) {
            throw error("NOT_PLAYING", 409);
        }
        if (Wire.number(fields, "revision", 0, Long.MAX_VALUE) != room.revision) {
            throw error("STALE_REVISION", 409);
        }
        int index = room.players.indexOf(player);
        if (room.session.game().currentPlayer() != index) {
            throw error("NOT_YOUR_TURN", 409);
        }
        int row = Wire.integer(fields, "row", 0, wall ? 7 : 8);
        int col = Wire.integer(fields, "col", 0, wall ? 7 : 8);
        Orientation orientation = null;
        if (wall) {
            try {
                orientation = Orientation.valueOf(fields.get("orientation"));
            } catch (IllegalArgumentException | NullPointerException invalid) {
                throw error("INVALID_REQUEST", 400);
            }
        }
        boolean accepted =
                wall
                        ? room.session.game().placeWall(row, col, orientation)
                        : room.session.game().moveTo(row, col);
        if (!accepted) {
            throw error("ILLEGAL_MOVE", 409);
        }
        if (room.moves.size() == 1000) {
            room.moves.removeFirst();
        }
        room.moves.addLast(
                index
                        + (wall ? " W " : " M ")
                        + row
                        + " "
                        + col
                        + (wall ? " " + orientation.name() : ""));
        room.revision++;
        if (room.session.game().winner() >= 0) {
            close(room, "FINISHED", now);
        }
        return new Reply(snapshot(player), null);
    }

    private Reply leave(Player player) {
        Room room = player.room;
        OnlineState before = snapshot(player);
        room.revision++;
        credentials.remove(player.token);
        if (room.status == OnlineState.Status.WAITING) {
            room.players.remove(player);
            if (room.players.isEmpty()) {
                rooms.remove(room.code);
            }
        } else if (room.status == OnlineState.Status.PLAYING) {
            close(room, "PLAYER_LEFT", System.nanoTime());
        }
        return new Reply(
                new OnlineState(
                        before.roomCode(),
                        OnlineState.Status.CLOSED,
                        before.playerIndex(),
                        before.playerCount(),
                        before.playerNames(),
                        room.revision,
                        before.session(),
                        "LEFT",
                        before.moves()),
                null);
    }

    private void cleanup(long now) {
        Iterator<Room> iterator = rooms.values().iterator();
        while (iterator.hasNext()) {
            Room room = iterator.next();
            if (room.status == OnlineState.Status.CLOSED) {
                if (now - room.closedAt >= limits.closedRetention().toNanos()) {
                    room.players.forEach(player -> credentials.remove(player.token));
                    iterator.remove();
                }
            } else if (room.status == OnlineState.Status.WAITING
                    && now - room.createdAt >= limits.waitingTimeout().toNanos()) {
                close(room, "WAIT_TIMEOUT", now);
            } else if (room.players.stream()
                    .anyMatch(player -> now - player.lastSeen >= limits.idleTimeout().toNanos())) {
                close(room, "PLAYER_TIMEOUT", now);
            }
        }
    }

    private static void close(Room room, String reason, long now) {
        room.status = OnlineState.Status.CLOSED;
        room.reason = reason;
        room.closedAt = now;
        room.revision++;
    }

    private static List<String> names(Room room) {
        return room.players.stream().map(player -> player.name).toList();
    }

    private static OnlineState snapshot(Player player) {
        Room room = player.room;
        return new OnlineState(
                room.code,
                room.status,
                room.players.indexOf(player),
                room.count,
                names(room),
                room.revision,
                room.session,
                room.reason,
                new ArrayList<>(room.moves));
    }

    private static OnlineException error(String code, int status) {
        return new OnlineException(code, status, code);
    }

    private static final class Room {
        final String code;
        final int count;
        final boolean privateRoom;
        final long createdAt;
        final List<Player> players = new ArrayList<>();
        final ArrayDeque<String> moves = new ArrayDeque<>();
        long revision;
        long closedAt;
        OnlineState.Status status = OnlineState.Status.WAITING;
        String reason = "";
        GameSession session;

        Room(String code, int count, boolean privateRoom, long now) {
            this.code = code;
            this.count = count;
            this.privateRoom = privateRoom;
            createdAt = now;
        }
    }

    private static final class Player {
        final Room room;
        final String name;
        final String token;
        long lastSeen;

        Player(Room room, String name, String token, long now) {
            this.room = room;
            this.name = name;
            this.token = token;
            lastSeen = now;
        }
    }
}
