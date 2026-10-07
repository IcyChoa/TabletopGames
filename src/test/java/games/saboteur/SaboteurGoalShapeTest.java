package games.saboteur;

import core.Game;
import core.actions.AbstractAction;
import core.actions.DoNothing;
import core.components.Deck;
import core.components.PartialObservableGridBoard;
import games.GameType;
import games.saboteur.actions.PlacePathCard;
import games.saboteur.actions.PlayMapCard;
import games.saboteur.components.ActionCard;
import games.saboteur.components.PathCard;
import games.saboteur.components.SaboteurCard;
import org.junit.Test;
import utilities.Vector2D;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Official goal cards: one gold cross and two coal corners (north-west, north-east).
 * Direction index matches {@code SaboteurForwardModel#getCardOffset}: 0 north, 1 south, 2 west, 3 east.
 */
public class SaboteurGoalShapeTest {

    private static final boolean[] CROSS = {true, true, true, true};
    private static final boolean[] NORTH_WEST = {true, false, true, false};
    private static final boolean[] NORTH_EAST = {true, false, false, true};
    private static final boolean[] SOUTH_WEST = {false, true, true, false};
    private static final boolean[] SOUTH_EAST = {false, true, false, true};
    /** dx, dy for directions 0..3 (north, south, west, east). */
    private static final int[] DX = {0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0};

    private static final int CH_GOAL_UNKNOWN = 4;
    private static final int CH_GOAL_GOLD = 5;
    private static final int CH_GOAL_EMPTY = 6;
    private static final int CH_DIR_U = 7;

    private record Table(SaboteurForwardModel fm, SaboteurGameState state) {}

    private record GoalCell(int x, int y, PathCard card) {}

    private static Table newTable(long seed, boolean singleRound) {
        SaboteurForwardModel fm = new SaboteurForwardModel();
        SaboteurGameParameters params = new SaboteurGameParameters();
        params.setParameterValue("singleRoundEpisode", singleRound);
        params.setRandomSeed(seed);
        Game game = new Game(GameType.Saboteur, fm, new SaboteurGameState(params, 3));
        return new Table(fm, (SaboteurGameState) game.getGameState());
    }

    private static List<GoalCell> goals(SaboteurGameState state) {
        List<GoalCell> found = new ArrayList<>();
        PartialObservableGridBoard grid = state.getGridBoard();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                PathCard card = (PathCard) grid.getElement(x, y);
                if (card != null && card.type == PathCard.PathCardType.Goal) {
                    found.add(new GoalCell(x, y, card));
                }
            }
        }
        return found;
    }

    private static void assertOfficialShapes(SaboteurGameState state) {
        int gold = 0;
        int northWest = 0;
        int northEast = 0;
        for (GoalCell goal : goals(state)) {
            boolean[] dirs = goal.card.getDirections().clone();
            if (goal.card.hasTreasure()) {
                gold++;
                assertArrayEquals(CROSS, dirs);
            } else if (Arrays.equals(dirs, NORTH_WEST)) {
                northWest++;
            } else if (Arrays.equals(dirs, NORTH_EAST)) {
                northEast++;
            } else {
                throw new AssertionError("coal is not a default corner: " + Arrays.toString(dirs));
            }
            assertFalse("goals start the round face down", faceUp(state, goal.x, goal.y));
        }
        assertEquals(1, gold);
        assertEquals(1, northWest);
        assertEquals(1, northEast);
    }

    private static boolean faceUp(SaboteurGameState state, int x, int y) {
        for (int p = 0; p < state.getNPlayers(); p++) {
            if (!state.getGridBoard().getElementVisibility(x, y, p)) {
                return false;
            }
        }
        return true;
    }

    private static void endRoundAsSaboteurs(Table table) {
        SaboteurGameState state = table.state;
        Deck<SaboteurCard> draw = state.getDrawDeck();
        while (draw.getSize() > 0) {
            state.getDiscardDeck().add(draw.draw());
        }
        for (Deck<SaboteurCard> hand : state.getPlayerDecks()) {
            while (hand.getSize() > 0) {
                state.getDiscardDeck().add(hand.draw());
            }
        }
        table.fm.next(state, new DoNothing());
    }

    private static int opposite(int dir) {
        return switch (dir) {
            case 0 -> 1;
            case 1 -> 0;
            case 2 -> 3;
            case 3 -> 2;
            default -> throw new IllegalArgumentException(String.valueOf(dir));
        };
    }

    private static int directionIndex(Vector2D from, Vector2D to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        if (dx == 0 && dy == -1) return 0;
        if (dx == 0 && dy == 1) return 1;
        if (dx == -1 && dy == 0) return 2;
        if (dx == 1 && dy == 0) return 3;
        throw new AssertionError(from + " -> " + to);
    }

    private static List<Vector2D> pathToAnchor(SaboteurGameState state, Vector2D anchor) {
        PartialObservableGridBoard grid = state.getGridBoard();
        Set<String> blocked = new HashSet<>();
        for (GoalCell goal : goals(state)) {
            blocked.add(goal.x + "," + goal.y);
            for (int i = 0; i < 4; i++) {
                String key = (goal.x + DX[i]) + "," + (goal.y + DY[i]);
                if (!key.equals(anchor.getX() + "," + anchor.getY())) {
                    blocked.add(key);
                }
            }
        }
        Vector2D start = state.startingSquare;
        blocked.add(start.getX() + "," + start.getY());

        Map<String, Vector2D> prev = new HashMap<>();
        ArrayDeque<Vector2D> queue = new ArrayDeque<>();
        queue.add(start);
        prev.put(start.getX() + "," + start.getY(), start);
        boolean found = false;
        while (!queue.isEmpty()) {
            Vector2D cur = queue.removeFirst();
            if (cur.equals(anchor)) {
                found = true;
                break;
            }
            for (int i = 0; i < 4; i++) {
                int nx = cur.getX() + DX[i];
                int ny = cur.getY() + DY[i];
                if (nx < 0 || ny < 0 || nx >= grid.getWidth() || ny >= grid.getHeight()) {
                    continue;
                }
                String key = nx + "," + ny;
                if (prev.containsKey(key)) {
                    continue;
                }
                Vector2D next = new Vector2D(nx, ny);
                if (blocked.contains(key) && !next.equals(anchor)) {
                    continue;
                }
                prev.put(key, cur);
                queue.add(next);
            }
        }
        assertTrue("no corridor to " + anchor, found);
        List<Vector2D> path = new ArrayList<>();
        Vector2D cur = anchor;
        while (!cur.equals(start)) {
            path.add(cur);
            cur = prev.get(cur.getX() + "," + cur.getY());
            assertNotNull(cur);
        }
        path.add(start);
        java.util.Collections.reverse(path);
        return path;
    }

    private static boolean[] link(Vector2D at, Vector2D a, Vector2D b) {
        boolean[] dirs = new boolean[4];
        dirs[directionIndex(at, a)] = true;
        dirs[directionIndex(at, b)] = true;
        return dirs;
    }

    private static PlacePathCard findPlacement(SaboteurGameState state, List<AbstractAction> actions,
                                               int x, int y, boolean rotated) {
        for (AbstractAction action : actions) {
            if (action instanceof PlacePathCard place
                    && place.getX() == x && place.getY() == y && place.isRotated() == rotated) {
                return place;
            }
        }
        return null;
    }

    private static void playPath(Table table, int x, int y, boolean[] dirs) {
        SaboteurGameState state = table.state;
        Deck<SaboteurCard> hand = state.getPlayerDecks().get(state.getCurrentPlayer());
        hand.clear();
        hand.add(new PathCard(PathCard.PathCardType.Path, dirs.clone()));
        List<AbstractAction> actions = table.fm.computeAvailableActions(state);
        PlacePathCard place = findPlacement(state, actions, x, y, false);
        assertNotNull(
                "no placement at " + x + "," + y + " dirs=" + Arrays.toString(dirs)
                        + " option=" + state.getPathCardOptions().contains(new Vector2D(x, y)),
                place);
        table.fm.next(state, place);
    }

    private static boolean canPlace(Table table, int x, int y, boolean[] dirs) {
        SaboteurGameState state = table.state;
        Deck<SaboteurCard> hand = state.getPlayerDecks().get(state.getCurrentPlayer());
        hand.clear();
        hand.add(new PathCard(PathCard.PathCardType.Path, dirs.clone()));
        return findPlacement(state, table.fm.computeAvailableActions(state), x, y, false) != null;
    }

    /** Lay a corridor and play {@code reaching} on the cell next to {@code goal} on side {@code side}. */
    private static void reachFrom(Table table, GoalCell goal, int side, boolean[] reaching) {
        Vector2D anchor = new Vector2D(goal.x + DX[side], goal.y + DY[side]);
        PartialObservableGridBoard grid = table.state.getGridBoard();
        assertNull(grid.getElement(anchor.getX(), anchor.getY()));
        List<Vector2D> path = pathToAnchor(table.state, anchor);
        assertTrue(path.size() >= 3);
        for (int i = 2; i < path.size() - 1; i++) {
            Vector2D cell = path.get(i);
            boolean[] dirs = link(cell, path.get(i - 1), path.get(i + 1));
            grid.setElement(cell.getX(), cell.getY(), new PathCard(PathCard.PathCardType.Path, dirs));
        }
        Vector2D first = path.get(1);
        playPath(table, first.getX(), first.getY(), link(first, path.get(0), path.get(2)));
        if (reaching == null) {
            Vector2D pred = path.get(path.size() - 2);
            reaching = link(anchor, pred, new Vector2D(goal.x, goal.y));
        }
        playPath(table, anchor.getX(), anchor.getY(), reaching);
    }

    private static GoalCell coal(SaboteurGameState state) {
        for (GoalCell goal : goals(state)) {
            if (!goal.card.hasTreasure()) {
                return goal;
            }
        }
        throw new AssertionError("no coal goal");
    }

    private static GoalCell gold(SaboteurGameState state) {
        for (GoalCell goal : goals(state)) {
            if (goal.card.hasTreasure()) {
                return goal;
            }
        }
        throw new AssertionError("no gold goal");
    }

    private static GoalCell goalAt(SaboteurGameState state, int x, int y) {
        for (GoalCell goal : goals(state)) {
            if (goal.x == x && goal.y == y) {
                return goal;
            }
        }
        throw new AssertionError("no goal at " + x + "," + y);
    }

    @Test
    public void oneGoldCrossAndTwoCoalCornersEveryRound() {
        Set<String> goldCells = new HashSet<>();
        for (int seed = 0; seed < 24; seed++) {
            Table table = newTable(seed * 97L + 3, false);
            for (int round = 0; round < 3; round++) {
                assertOfficialShapes(table.state);
                GoalCell gold = gold(table.state);
                goldCells.add(gold.x + "," + gold.y);
                if (round < 2) {
                    endRoundAsSaboteurs(table);
                    assertTrue(table.state.isNotTerminal());
                }
            }
        }
        assertTrue("gold should occupy more than one cell across seeds, got " + goldCells, goldCells.size() >= 2);
    }

    @Test
    public void wallMayFaceAFaceDownGoalAndDoesNotTunnelThroughIt() {
        Table table = newTable(11L, false);
        GoalCell goal = coal(table.state);
        int side = 2; // played card sits west of the goal
        Vector2D anchor = new Vector2D(goal.x + DX[side], goal.y + DY[side]);
        List<Vector2D> path = pathToAnchor(table.state, anchor);
        PartialObservableGridBoard grid = table.state.getGridBoard();
        for (int i = 2; i < path.size() - 1; i++) {
            Vector2D cell = path.get(i);
            grid.setElement(cell.getX(), cell.getY(),
                    new PathCard(PathCard.PathCardType.Path, link(cell, path.get(i - 1), path.get(i + 1))));
        }
        Vector2D first = path.get(1);
        playPath(table, first.getX(), first.getY(), link(first, path.get(0), path.get(2)));
        boolean[] wall = new boolean[4];
        wall[directionIndex(anchor, path.get(path.size() - 2))] = true;
        assertFalse("the placed card keeps a wall toward the face-down goal", wall[opposite(side)]);
        playPath(table, anchor.getX(), anchor.getY(), wall);

        assertFalse(faceUp(table.state, goal.x, goal.y));
        assertFalse(table.state.isLastRoundResolved());
        PathCard hidden = goalAt(table.state, goal.x, goal.y).card;
        for (int i = 0; i < 4; i++) {
            if (!hidden.getDirections()[i]) {
                continue;
            }
            int nx = goal.x + DX[i];
            int ny = goal.y + DY[i];
            if (grid.getElement(nx, ny) != null) {
                continue;
            }
            assertFalse(
                    "face-down goal must not extend the frontier through " + Arrays.toString(hidden.getDirections()),
                    table.state.getPathCardOptions().contains(new Vector2D(nx, ny)));
        }
    }

    @Test
    public void reachingCoalFromEachSideOrientsItTowardThePlayedCard() {
        boolean[][][] expected = {
                {NORTH_WEST, NORTH_EAST},
                {SOUTH_WEST, SOUTH_EAST},
                {NORTH_WEST, SOUTH_WEST},
                {NORTH_EAST, SOUTH_EAST},
        };
        for (int side = 0; side < 4; side++) {
            Table table = newTable(20L + side, false);
            GoalCell goal = coal(table.state);
            int id = goal.card.getComponentID();
            reachFrom(table, goal, side, null);

            GoalCell revealed = goalAt(table.state, goal.x, goal.y);
            assertEquals(id, revealed.card.getComponentID());
            assertFalse(revealed.card.hasTreasure());
            assertTrue(faceUp(table.state, goal.x, goal.y));
            assertTrue(revealed.card.getDirections()[side]);
            boolean[] dirs = revealed.card.getDirections().clone();
            assertTrue(
                    "side " + side + " produced " + Arrays.toString(dirs),
                    Arrays.equals(dirs, expected[side][0]) || Arrays.equals(dirs, expected[side][1]));
            assertFalse(table.state.isLastRoundResolved());
            for (GoalCell other : goals(table.state)) {
                if (other.x == goal.x && other.y == goal.y) {
                    continue;
                }
                assertFalse(faceUp(table.state, other.x, other.y));
            }

            for (int i = 0; i < 4; i++) {
                int nx = goal.x + DX[i];
                int ny = goal.y + DY[i];
                if (table.state.getGridBoard().getElement(nx, ny) != null) {
                    continue;
                }
                boolean option = table.state.getPathCardOptions().contains(new Vector2D(nx, ny));
                assertEquals(
                        "side " + side + " dir " + i + " exits " + Arrays.toString(dirs),
                        dirs[i],
                        option);
            }

            int otherExit = -1;
            for (int i = 0; i < 4; i++) {
                if (i != side && dirs[i]) {
                    otherExit = i;
                }
            }
            assertTrue(otherExit >= 0);
            int nx = goal.x + DX[otherExit];
            int ny = goal.y + DY[otherExit];
            boolean[] matching = new boolean[4];
            matching[opposite(otherExit)] = true;
            boolean[] away = new boolean[4];
            away[otherExit] = true;
            assertTrue(canPlace(table, nx, ny, matching));
            assertFalse(canPlace(table, nx, ny, away));
        }
    }

    @Test
    public void reachingGoldEndsTheRoundAsAMinerWin() {
        Table table = newTable(8L, true);
        GoalCell goal = gold(table.state);
        reachFrom(table, goal, 2, null);
        assertTrue(faceUp(table.state, goal.x, goal.y));
        assertArrayEquals(CROSS, goalAt(table.state, goal.x, goal.y).card.getDirections());
        assertTrue(table.state.isLastRoundResolved());
        assertTrue(table.state.didMinersWinLastRound());
        assertFalse(table.state.isNotTerminal());
    }

    @Test
    public void coalOrientationResetsAtTheNextRound() {
        Table table = newTable(5L, false);
        GoalCell goal = coal(table.state);
        int id = goal.card.getComponentID();
        reachFrom(table, goal, 1, null); // south forces a 180° turn; both defaults have north open
        boolean[] turned = goalAt(table.state, goal.x, goal.y).card.getDirections().clone();
        assertFalse(turned[0]);
        assertTrue(turned[1]);

        endRoundAsSaboteurs(table);
        assertTrue(table.state.isNotTerminal());
        PathCard restored = null;
        for (GoalCell cell : goals(table.state)) {
            if (cell.card.getComponentID() == id) {
                restored = cell.card;
                assertFalse(faceUp(table.state, cell.x, cell.y));
            }
        }
        assertNotNull(restored);
        assertFalse(restored.hasTreasure());
        boolean[] dirs = restored.getDirections().clone();
        assertTrue(Arrays.equals(dirs, NORTH_WEST) || Arrays.equals(dirs, NORTH_EAST));
        assertOfficialShapes(table.state);
    }

    @Test
    public void determinisedCopyKeepsRevealedGoalsFixed() {
        Table table = newTable(9L, false);
        GoalCell goal = coal(table.state);
        reachFrom(table, goal, 1, null);
        boolean[] revealedDirs = goalAt(table.state, goal.x, goal.y).card.getDirections().clone();
        boolean revealedTreasure = goalAt(table.state, goal.x, goal.y).card.hasTreasure();
        int revealedId = goalAt(table.state, goal.x, goal.y).card.getComponentID();

        List<String> before = shapeKeys(table.state);
        for (int n = 0; n < 12; n++) {
            SaboteurGameState copy = (SaboteurGameState) table.state.copy(0);
            PathCard copied = (PathCard) copy.getGridBoard().getElement(goal.x, goal.y);
            assertNotNull(copied);
            assertEquals(revealedId, copied.getComponentID());
            assertEquals(revealedTreasure, copied.hasTreasure());
            assertArrayEquals(revealedDirs, copied.getDirections());
            assertTrue(faceUp(copy, goal.x, goal.y));
            assertEquals(before, shapeKeys(copy));
            assertEquals(3, copy.getGoalDeck().getSize());
        }
        assertArrayEquals(revealedDirs, goalAt(table.state, goal.x, goal.y).card.getDirections());
        assertEquals(before, shapeKeys(table.state));
    }

    private static List<String> shapeKeys(SaboteurGameState state) {
        List<String> keys = new ArrayList<>();
        for (GoalCell goal : goals(state)) {
            keys.add(goal.card.hasTreasure() + Arrays.toString(goal.card.getDirections()));
        }
        java.util.Collections.sort(keys);
        return keys;
    }

    @Test
    public void observationHidesHiddenAndPeekedGoalExits() {
        assertEquals(2254, SaboteurFeatures.OBS_SIZE);
        Table table = newTable(4L, false);
        SaboteurFeatures features = new SaboteurFeatures();
        GoalCell hiddenCoal = coal(table.state);
        for (int p = 0; p < table.state.getNPlayers(); p++) {
            double[] v = features.doubleVector(table.state, p);
            assertEquals(2254, v.length);
            for (GoalCell goal : goals(table.state)) {
                assertEquals(1.0, at(v, CH_GOAL_UNKNOWN, goal.y, goal.x), 0.0);
                assertEquals(0.0, at(v, CH_GOAL_GOLD, goal.y, goal.x), 0.0);
                assertEquals(0.0, at(v, CH_GOAL_EMPTY, goal.y, goal.x), 0.0);
                assertEquals(0.0, dirSum(v, goal.y, goal.x), 0.0);
            }
        }

        table.state.setTurnOwner(0);
        Deck<SaboteurCard> hand = table.state.getPlayerDecks().get(0);
        hand.clear();
        hand.add(new ActionCard(ActionCard.ActionCardType.Map, new ActionCard.ToolCardType[]{}));
        table.fm.next(table.state, new PlayMapCard(hiddenCoal.x, hiddenCoal.y));

        double[] peeker = features.doubleVector(table.state, 0);
        assertEquals(1.0, at(peeker, CH_GOAL_EMPTY, hiddenCoal.y, hiddenCoal.x), 0.0);
        assertEquals(0.0, at(peeker, CH_GOAL_UNKNOWN, hiddenCoal.y, hiddenCoal.x), 0.0);
        assertEquals(0.0, dirSum(peeker, hiddenCoal.y, hiddenCoal.x), 0.0);
        double[] other = features.doubleVector(table.state, 1);
        assertEquals(1.0, at(other, CH_GOAL_UNKNOWN, hiddenCoal.y, hiddenCoal.x), 0.0);
        assertEquals(0.0, dirSum(other, hiddenCoal.y, hiddenCoal.x), 0.0);

        GoalCell still = goalAt(table.state, hiddenCoal.x, hiddenCoal.y);
        reachFrom(table, still, 2, null);
        boolean[] dirs = goalAt(table.state, hiddenCoal.x, hiddenCoal.y).card.getDirections();
        for (int p = 0; p < table.state.getNPlayers(); p++) {
            double[] v = features.doubleVector(table.state, p);
            assertEquals(2254, v.length);
            assertEquals(1.0, at(v, CH_GOAL_EMPTY, hiddenCoal.y, hiddenCoal.x), 0.0);
            assertEquals(0.0, at(v, CH_GOAL_GOLD, hiddenCoal.y, hiddenCoal.x), 0.0);
            for (int i = 0; i < 4; i++) {
                assertEquals(dirs[i] ? 1.0 : 0.0, at(v, CH_DIR_U + i, hiddenCoal.y, hiddenCoal.x), 0.0);
            }
        }
    }

    private static double at(double[] v, int channel, int y, int x) {
        return v[channel * SaboteurFeatures.H * SaboteurFeatures.W + y * SaboteurFeatures.W + x];
    }

    private static double dirSum(double[] v, int y, int x) {
        double sum = 0;
        for (int i = 0; i < 4; i++) {
            sum += at(v, CH_DIR_U + i, y, x);
        }
        return sum;
    }
}
