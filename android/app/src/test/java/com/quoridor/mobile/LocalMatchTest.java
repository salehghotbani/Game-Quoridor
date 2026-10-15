package com.quoridor.mobile;

import static org.junit.Assert.*;

import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;

import org.junit.Test;

import java.util.List;

/** Controller regressions that matter when Android recreates a screen or cancels a worker. */
public final class LocalMatchTest {
    @Test
    public void robotUndoAndRedoRestoreAnEntireHumanRobotTurn() {
        LocalMatch match = match(2, true);
        GameState.Snapshot opening = match.session().game().snapshot();
        assertTrue(match.move(7, 4));
        assertTrue(match.robotTurn());
        assertEquals(1, match.revision());
        assertFalse(match.move(1, 4));
        assertEquals(1, match.revision());
        assertTrue(match.applyRobot(new BotPlayer.Action(1, 4, null), 1));
        GameState.Snapshot afterPair = match.session().game().snapshot();
        assertFalse(match.robotTurn());
        assertEquals(2, match.moves().size());

        match.undo();
        assertEquals(opening, match.session().game().snapshot());
        assertFalse(match.robotTurn());
        assertEquals(0, match.moves().size());
        assertFalse(match.canUndo());
        assertTrue(match.canRedo());
        assertEquals(3, match.revision());

        match.redo();
        assertEquals(afterPair, match.session().game().snapshot());
        assertFalse(match.robotTurn());
        assertEquals(2, match.moves().size());
        assertEquals(4, match.revision());
        assertFalse(match.canRedo());
    }

    @Test
    public void staleRobotCannotApplyAfterUndoRedoOrReplacementTurn() {
        LocalMatch match = match(2, true);
        assertTrue(match.move(7, 4));
        long pendingRevision = match.revision();
        BotPlayer.Action reply = new BotPlayer.Action(1, 4, null);
        match.undo();
        assertFalse(match.applyRobot(reply, pendingRevision));
        match.redo();
        assertTrue(match.robotTurn());
        GameState.Snapshot waiting = match.session().game().snapshot();
        assertFalse(match.applyRobot(reply, pendingRevision));
        assertEquals(waiting, match.session().game().snapshot());
        assertTrue(match.applyRobot(reply, match.revision()));
        assertFalse(match.applyRobot(reply, match.revision()));
        assertEquals(2, match.moves().size());
    }

    @Test
    public void undoWhileRobotIsPendingRestoresHumanAndDropsRedoOnNewMove() {
        LocalMatch match = match(2, true);
        assertTrue(match.move(7, 4));
        match.undo();
        assertFalse(match.robotTurn());
        assertTrue(match.canRedo());
        assertTrue(match.move(8, 3));
        assertFalse(match.canRedo());
        assertEquals(new GameState.Cell(8, 3), match.session().game().playerPosition(0));
        assertEquals(1, match.moves().size());
    }

    @Test
    public void fourPlayerTurnsAndUndoAreSinglePlies() {
        LocalMatch match = match(4, false);
        GameState.Snapshot opening = match.session().game().snapshot();
        assertEquals(0, match.session().game().currentPlayer());
        assertTrue(match.move(7, 4));
        assertEquals(2, match.session().game().currentPlayer());
        assertTrue(match.move(4, 1));
        assertEquals(1, match.session().game().currentPlayer());
        assertTrue(match.move(1, 4));
        assertEquals(3, match.session().game().currentPlayer());
        assertTrue(match.move(4, 7));
        assertEquals(0, match.session().game().currentPlayer());
        GameState.Snapshot afterRound = match.session().game().snapshot();
        assertEquals(4, match.moves().size());
        match.undo();
        assertEquals(3, match.session().game().currentPlayer());
        assertEquals(new GameState.Cell(4, 8), match.session().game().playerPosition(3));
        assertEquals(3, match.moves().size());
        match.undo();
        match.undo();
        match.undo();
        assertEquals(opening, match.session().game().snapshot());
        match.redo();
        match.redo();
        match.redo();
        match.redo();
        assertEquals(afterRound, match.session().game().snapshot());
    }

    @Test
    public void stateAndHistoryAreIndependentAndInvalidActionsAreNoOps() {
        GameSession initial = session(2, false);
        LocalMatch match = new LocalMatch(initial);
        GameState.Snapshot opening = match.session().game().snapshot();
        initial.game().moveTo(7, 4);
        match.session().game().moveTo(8, 3);
        assertEquals(opening, match.session().game().snapshot());
        assertFalse(match.move(8, 4));
        assertFalse(match.move(-1, 4));
        assertFalse(match.wall(null));
        assertFalse(match.applyRobot(new BotPlayer.Action(7, 4, null), 0));
        match.undo();
        match.redo();
        assertEquals(0, match.revision());
        assertEquals(opening, match.session().game().snapshot());
        assertTrue(match.wall(new GameState.Wall(2, 3, GameState.Orientation.HORIZONTAL)));
        assertEquals(9, match.session().game().wallsRemaining(0));
        long revision = match.revision();
        assertFalse(match.wall(new GameState.Wall(2, 3, GameState.Orientation.HORIZONTAL)));
        assertEquals(revision, match.revision());
        assertEquals(List.of("0 W 2 3 HORIZONTAL"), match.moves());
        try {
            match.moves().clear();
            fail("Move descriptions must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertEquals(1, match.moves().size());
        }
    }

    @Test
    public void terminalGameRejectsEveryAction() {
        GameState won =
                GameState.fromSnapshot(
                        new GameState.Snapshot(
                                new GameState.Cell(0, 4),
                                new GameState.Cell(6, 0),
                                10,
                                10,
                                0,
                                0,
                                List.of()));
        LocalMatch match = new LocalMatch(new GameSession(won, session(2, false).config()));
        GameState.Snapshot terminal = match.session().game().snapshot();
        assertFalse(match.move(1, 4));
        assertFalse(match.wall(new GameState.Wall(2, 3, GameState.Orientation.VERTICAL)));
        assertFalse(match.applyRobot(new BotPlayer.Action(7, 0, null), 0));
        assertEquals(terminal, match.session().game().snapshot());
        assertEquals(0, match.revision());
    }

    private static LocalMatch match(int count, boolean bot) {
        return new LocalMatch(session(count, bot));
    }

    private static GameSession session(int count, boolean bot) {
        List<String> names =
                count == 4 ? List.of("آبی", "Green", "Red", "طلایی") : List.of("آبی", "Robot");
        return new GameSession(
                new GameState(count),
                new GameConfig(
                        names,
                        bot ? GameConfig.Mode.BOT : GameConfig.Mode.LOCAL,
                        BotPlayer.Difficulty.HARD));
    }
}
