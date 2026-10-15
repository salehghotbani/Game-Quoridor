package Board;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Test-only line protocol; compiled below out, never included in a web/desktop distribution. */
public final class WebRulesOracle {
    private static GameSession session = initial(2);

    public static void main(String[] args) throws Exception {
        try (var input =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            for (String line; (line = input.readLine()) != null; ) {
                try {
                    String[] parts = line.split(" ");
                    boolean accepted =
                            switch (parts[0]) {
                                case "RESET" -> {
                                    session = initial(Integer.parseInt(parts[1]));
                                    yield true;
                                }
                                case "SAVE" -> {
                                    var loaded =
                                            SaveStore.decode(
                                                    new String(
                                                            Base64.getDecoder().decode(parts[1]),
                                                            StandardCharsets.UTF_8));
                                    session = loaded;
                                    yield true;
                                }
                                case "MOVE" ->
                                        session.game()
                                                .moveTo(
                                                        Integer.parseInt(parts[1]),
                                                        Integer.parseInt(parts[2]));
                                case "WALL" ->
                                        session.game()
                                                .placeWall(
                                                        Integer.parseInt(parts[1]),
                                                        Integer.parseInt(parts[2]),
                                                        GameState.Orientation.valueOf(parts[3]));
                                default ->
                                        throw new IllegalArgumentException("Unknown test command");
                            };
                    System.out.println(json(accepted));
                } catch (Exception invalid) {
                    System.out.println("{\"error\":true}");
                }
            }
        }
    }

    private static GameSession initial(int count) {
        return new GameSession(
                new GameState(count),
                new GameConfig(
                        List.of("Blue", "Green", "Red", "Gold").subList(0, count),
                        GameConfig.Mode.LOCAL,
                        BotPlayer.Difficulty.MEDIUM));
    }

    private static String cell(GameState.Cell value) {
        return "{\"row\":" + value.row() + ",\"col\":" + value.col() + "}";
    }

    private static String json(boolean accepted) throws Exception {
        GameState game = session.game();
        GameState.Snapshot state = game.snapshot();
        var players = state.players().stream().map(WebRulesOracle::cell).toList();
        var walls =
                state.walls().stream()
                        .map(
                                wall ->
                                        "{\"row\":"
                                                + wall.row()
                                                + ",\"col\":"
                                                + wall.col()
                                                + ",\"orientation\":\""
                                                + wall.orientation()
                                                + "\"}")
                        .toList();
        var moves = new ArrayList<String>();
        var distances = new ArrayList<String>();
        for (int player = 0; player < game.playerCount(); player++) {
            moves.add(
                    "["
                            + String.join(
                                    ",",
                                    game.legalMovesFor(player).stream()
                                            .map(WebRulesOracle::cell)
                                            .toList())
                            + "]");
            distances.add(java.util.Arrays.toString(GoalDistances.toGoal(game, player)));
        }
        var legality = new ArrayList<String>();
        for (int row = 0; row < GameState.SIZE - 1; row++) {
            for (int col = 0; col < GameState.SIZE - 1; col++) {
                for (var orientation : GameState.Orientation.values()) {
                    legality.add(Boolean.toString(game.canPlaceWall(row, col, orientation)));
                }
            }
        }
        String save =
                Base64.getEncoder()
                        .encodeToString(SaveStore.encode(session).getBytes(StandardCharsets.UTF_8));
        return "{\"accepted\":"
                + accepted
                + ",\"snapshot\":{\"players\":["
                + String.join(",", players)
                + "],\"wallsRemaining\":"
                + state.wallsRemaining()
                + ",\"currentPlayer\":"
                + game.currentPlayer()
                + ",\"winner\":"
                + game.winner()
                + ",\"walls\":["
                + String.join(",", walls)
                + "]},\"moves\":["
                + String.join(",", moves)
                + "],\"distances\":["
                + String.join(",", distances)
                + "],\"wallLegality\":["
                + String.join(",", legality)
                + "],\"save\":\""
                + save
                + "\"}";
    }
}
