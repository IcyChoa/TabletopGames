package games.saboteur;

import core.AbstractGameState;
import core.components.Deck;
import core.components.PartialObservableGridBoard;
import core.interfaces.IStateFeatureVector;
import games.saboteur.components.ActionCard;
import games.saboteur.components.ActionCard.ToolCardType;
import games.saboteur.components.PathCard;
import games.saboteur.components.RoleCard.RoleCardType;
import games.saboteur.components.SaboteurCard;
import utilities.Vector2D;

import java.util.Arrays;
import java.util.Set;

/**
 * FOW-correct observation for Saboteur (PyTAG).
 *
 * Layout (flat double[], channel-major board then vectors):
 * <pre>
 *   BOARD:  C * H * W   (C=12, H=12, W=14)  -> 2016
 *   HAND:   MAX_HAND * F_HAND               -> 156
 *   PLAYER: MAX_PLAYERS * F_PLAYER          -> 70
 *   SCALAR: N_SCALARS                       -> 12
 *   TOTAL                                -> 2254
 * </pre>
 *
 * Hand path cards are one-hot over a fixed shape catalog (9 Edge + 7 Path) plus a single
 * {@code flipped} bit (0 = catalog orientation, 1 = 180° rotate). Board cells keep 4
 * connectivity bits (U/D/L/R exits) — those are geometry, not hand rotation.
 */
public class SaboteurFeatures implements IStateFeatureVector {

    // Default board from SaboteurGameParameters (goalSpacingX=8, padding, nGoals=3)
    public static final int W = 14;
    public static final int H = 12;
    public static final int C = 12;
    public static final int BOARD_SIZE = C * H * W; // 2016

    public static final int MAX_HAND = 6;
    public static final int N_PATH_SHAPES = 16; // 9 edge + 7 path (catalog order below)
    // empty(1) + pathShape(16) + flipped(1) + Map + RockFall + Broken*3 + Fix*3
    public static final int F_HAND = 1 + N_PATH_SHAPES + 1 + 2 + 3 + 3; // 26
    public static final int HAND_SIZE = MAX_HAND * F_HAND; // 156

    public static final int MAX_PLAYERS = 10;
    public static final int F_PLAYER = 7; // tools*3 + role*3 + handSize
    public static final int PLAYER_SIZE = MAX_PLAYERS * F_PLAYER; // 70

    public static final int N_SCALARS = 12;
    public static final int OBS_SIZE = BOARD_SIZE + HAND_SIZE + PLAYER_SIZE + N_SCALARS; // 2254

    public static final int BOARD_OFF = 0;
    public static final int HAND_OFF = BOARD_SIZE;
    public static final int PLAYER_OFF = HAND_OFF + HAND_SIZE;
    public static final int SCALAR_OFF = PLAYER_OFF + PLAYER_SIZE;

    // Board channel indices
    private static final int CH_EMPTY = 0;
    private static final int CH_START = 1;
    private static final int CH_PATH = 2;
    private static final int CH_EDGE = 3;
    private static final int CH_GOAL_UNKNOWN = 4;
    private static final int CH_GOAL_GOLD = 5;
    private static final int CH_GOAL_EMPTY = 6;
    private static final int CH_DIR_U = 7;
    private static final int CH_DIR_D = 8;
    private static final int CH_DIR_L = 9;
    private static final int CH_DIR_R = 10;
    private static final int CH_LEGAL = 11;

    /**
     * Canonical path/edge shapes as stored in {@link SaboteurGameParameters#pathCardDeck}.
     * Matching uses exact dirs or 180°-rotated dirs; {@code flipped=1} means the hand card
     * is in the rotated orientation relative to this catalog entry.
     */
    private static final PathShape[] PATH_SHAPES = {
            // Edges (9)
            new PathShape(PathCard.PathCardType.Edge, new boolean[]{false, true, false, false}),
            new PathShape(PathCard.PathCardType.Edge, new boolean[]{false, false, true, false}),
            new PathShape(PathCard.PathCardType.Edge, new boolean[]{true, true, false, false}),
            new PathShape(PathCard.PathCardType.Edge, new boolean[]{false, false, true, true}),
            new PathShape(PathCard.PathCardType.Edge, new boolean[]{false, true, false, true}),
            new PathShape(PathCard.PathCardType.Edge, new boolean[]{false, true, true, false}),
            new PathShape(PathCard.PathCardType.Edge, new boolean[]{true, true, false, true}),
            new PathShape(PathCard.PathCardType.Edge, new boolean[]{true, false, true, true}),
            new PathShape(PathCard.PathCardType.Edge, new boolean[]{true, true, true, true}),
            // Paths (7)
            new PathShape(PathCard.PathCardType.Path, new boolean[]{true, true, false, false}),
            new PathShape(PathCard.PathCardType.Path, new boolean[]{false, false, true, true}),
            new PathShape(PathCard.PathCardType.Path, new boolean[]{false, true, false, true}),
            new PathShape(PathCard.PathCardType.Path, new boolean[]{false, true, true, false}),
            new PathShape(PathCard.PathCardType.Path, new boolean[]{true, true, false, true}),
            new PathShape(PathCard.PathCardType.Path, new boolean[]{true, false, true, true}),
            new PathShape(PathCard.PathCardType.Path, new boolean[]{true, true, true, true}),
    };

    private static final String[] NAMES = buildNames();

    private static final double DECK_MAX = 71.0; // path+action cards in default deck

    private record PathShape(PathCard.PathCardType type, boolean[] dirs) {
        boolean matches(boolean[] other) {
            return Arrays.equals(dirs, other);
        }

        boolean[] rotated() {
            // 180°: swap U/D and L/R (same as PathCard.rotate)
            return new boolean[]{dirs[1], dirs[0], dirs[3], dirs[2]};
        }
    }

    private static String[] buildNames() {
        String[] names = new String[OBS_SIZE];
        int i = 0;
        String[] chNames = {
                "empty", "start", "path", "edge",
                "goalUnknown", "goalGold", "goalEmpty",
                "dirU", "dirD", "dirL", "dirR", "legalPlace"
        };
        for (int c = 0; c < C; c++) {
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    names[i++] = "board_" + chNames[c] + "_" + y + "_" + x;
                }
            }
        }
        for (int h = 0; h < MAX_HAND; h++) {
            names[i++] = "hand" + h + "_empty";
            for (int s = 0; s < N_PATH_SHAPES; s++) {
                names[i++] = "hand" + h + "_shape" + s;
            }
            names[i++] = "hand" + h + "_flipped";
            names[i++] = "hand" + h + "_map";
            names[i++] = "hand" + h + "_rockfall";
            names[i++] = "hand" + h + "_brkPickaxe";
            names[i++] = "hand" + h + "_brkLantern";
            names[i++] = "hand" + h + "_brkMineCart";
            names[i++] = "hand" + h + "_fixPickaxe";
            names[i++] = "hand" + h + "_fixLantern";
            names[i++] = "hand" + h + "_fixMineCart";
        }
        for (int p = 0; p < MAX_PLAYERS; p++) {
            names[i++] = "p" + p + "_pickaxeOk";
            names[i++] = "p" + p + "_lanternOk";
            names[i++] = "p" + p + "_minecartOk";
            names[i++] = "p" + p + "_roleSaboteur";
            names[i++] = "p" + p + "_roleMiner";
            names[i++] = "p" + p + "_roleUnknown";
            names[i++] = "p" + p + "_handSize";
        }
        names[i++] = "ownIsSaboteur";
        names[i++] = "drawDeck";
        names[i++] = "discardDeck";
        names[i++] = "pathOptions";
        names[i++] = "goalsVisible";
        names[i++] = "roundIndex";
        names[i++] = "minersWinR0";
        names[i++] = "minersWinR1";
        names[i++] = "minersWinR2";
        names[i++] = "nPlayers";
        names[i++] = "paramSaboteurs";
        names[i++] = "paramMiners";
        if (i != OBS_SIZE) {
            throw new IllegalStateException("NAMES length mismatch: " + i + " vs " + OBS_SIZE);
        }
        return names;
    }

    @Override
    public String[] names() {
        return NAMES;
    }

    @Override
    public double[] doubleVector(AbstractGameState gameState, int playerId) {
        SaboteurGameState sgs = (SaboteurGameState) gameState;
        double[] v = new double[OBS_SIZE];
        encodeBoard(v, sgs, playerId);
        encodeHand(v, sgs, playerId);
        encodePlayers(v, sgs, playerId);
        encodeScalars(v, sgs, playerId);
        return v;
    }

    private void encodeBoard(double[] v, SaboteurGameState sgs, int playerId) {
        PartialObservableGridBoard grid = sgs.getGridBoard();
        if (grid.getWidth() != W || grid.getHeight() != H) {
            throw new IllegalStateException(
                    "Board size " + grid.getWidth() + "x" + grid.getHeight()
                            + " != expected " + W + "x" + H
                            + "; update SaboteurFeatures constants if parameters changed.");
        }
        Set<Vector2D> legal = sgs.getPathCardOptions();

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                PathCard card = (PathCard) grid.getElement(x, y);
                boolean legalHere = legal.contains(new Vector2D(x, y));

                if (card == null) {
                    setBoard(v, CH_EMPTY, y, x, 1.0);
                } else {
                    switch (card.type) {
                        case Start -> {
                            setBoard(v, CH_START, y, x, 1.0);
                            writeDirs(v, y, x, card.getDirections());
                        }
                        case Path -> {
                            setBoard(v, CH_PATH, y, x, 1.0);
                            writeDirs(v, y, x, card.getDirections());
                        }
                        case Edge -> {
                            setBoard(v, CH_EDGE, y, x, 1.0);
                            writeDirs(v, y, x, card.getDirections());
                        }
                        case Goal -> {
                            if (!grid.getElementVisibility(x, y, playerId)) {
                                setBoard(v, CH_GOAL_UNKNOWN, y, x, 1.0);
                            } else if (card.hasTreasure()) {
                                setBoard(v, CH_GOAL_GOLD, y, x, 1.0);
                                writeDirs(v, y, x, card.getDirections());
                            } else {
                                setBoard(v, CH_GOAL_EMPTY, y, x, 1.0);
                                writeDirs(v, y, x, card.getDirections());
                            }
                        }
                    }
                }
                if (legalHere) {
                    setBoard(v, CH_LEGAL, y, x, 1.0);
                }
            }
        }
    }

    private void writeDirs(double[] v, int y, int x, boolean[] dirs) {
        setBoard(v, CH_DIR_U, y, x, dirs[0] ? 1.0 : 0.0);
        setBoard(v, CH_DIR_D, y, x, dirs[1] ? 1.0 : 0.0);
        setBoard(v, CH_DIR_L, y, x, dirs[2] ? 1.0 : 0.0);
        setBoard(v, CH_DIR_R, y, x, dirs[3] ? 1.0 : 0.0);
    }

    private void setBoard(double[] v, int c, int y, int x, double val) {
        // channel-major: (c, y, x)
        v[BOARD_OFF + c * (H * W) + y * W + x] = val;
    }

    private void encodeHand(double[] v, SaboteurGameState sgs, int playerId) {
        Deck<SaboteurCard> hand = sgs.getPlayerDecks().get(playerId);
        for (int h = 0; h < MAX_HAND; h++) {
            int base = HAND_OFF + h * F_HAND;
            if (h >= hand.getSize()) {
                v[base] = 1.0; // empty
                continue;
            }
            SaboteurCard card = hand.peek(h);
            if (card instanceof PathCard path) {
                int shapeIdx = matchPathShape(path);
                if (shapeIdx >= 0) {
                    v[base + 1 + shapeIdx] = 1.0;
                    v[base + 1 + N_PATH_SHAPES] = isFlipped(path, shapeIdx) ? 1.0 : 0.0;
                }
            } else if (card instanceof ActionCard action) {
                switch (action.actionType) {
                    case Map -> v[base + 1 + N_PATH_SHAPES + 1] = 1.0;
                    case RockFall -> v[base + 1 + N_PATH_SHAPES + 2] = 1.0;
                    case BrokenTools -> {
                        for (ToolCardType t : action.toolTypes) {
                            v[base + 1 + N_PATH_SHAPES + 3 + t.ordinal()] = 1.0;
                        }
                    }
                    case FixTools -> {
                        for (ToolCardType t : action.toolTypes) {
                            v[base + 1 + N_PATH_SHAPES + 6 + t.ordinal()] = 1.0;
                        }
                    }
                }
            }
        }
    }

    /** Index into PATH_SHAPES, or -1 if unknown. Prefers catalog orientation, then flipped. */
    private int matchPathShape(PathCard path) {
        boolean[] dirs = path.getDirections();
        for (int i = 0; i < PATH_SHAPES.length; i++) {
            PathShape s = PATH_SHAPES[i];
            if (s.type != path.type) continue;
            if (s.matches(dirs) || Arrays.equals(s.rotated(), dirs)) return i;
        }
        return -1;
    }

    private boolean isFlipped(PathCard path, int shapeIdx) {
        PathShape s = PATH_SHAPES[shapeIdx];
        if (s.matches(path.getDirections())) return false;
        return Arrays.equals(s.rotated(), path.getDirections());
    }

    private void encodePlayers(double[] v, SaboteurGameState sgs, int playerId) {
        int n = sgs.getNPlayers();
        for (int b = 0; b < MAX_PLAYERS; b++) {
            int base = PLAYER_OFF + b * F_PLAYER;
            if (b >= n) continue; // padded zeros
            int pid = (playerId + b) % n;

            v[base] = sgs.isToolFunctional(pid, ToolCardType.Pickaxe) ? 1.0 : 0.0;
            v[base + 1] = sgs.isToolFunctional(pid, ToolCardType.Lantern) ? 1.0 : 0.0;
            v[base + 2] = sgs.isToolFunctional(pid, ToolCardType.MineCart) ? 1.0 : 0.0;

            boolean known = sgs.getRoleDeck().getVisibilityForPlayer(pid, playerId);
            if (known) {
                if (sgs.getRole(pid) == RoleCardType.Saboteur) {
                    v[base + 3] = 1.0;
                } else {
                    v[base + 4] = 1.0;
                }
            } else {
                v[base + 5] = 1.0;
            }
            v[base + 6] = sgs.getPlayerDecks().get(pid).getSize() / (double) MAX_HAND;
        }
    }

    private void encodeScalars(double[] v, SaboteurGameState sgs, int playerId) {
        SaboteurGameParameters sgp = (SaboteurGameParameters) sgs.getGameParameters();
        int n = sgs.getNPlayers();
        int paramS = sgp.saboteursForPlayerCount[n];
        int paramM = sgp.minersForPlayerCount[n];
        int roleDeckSize = paramS + paramM;

        int i = SCALAR_OFF;
        v[i++] = sgs.getRole(playerId) == RoleCardType.Saboteur ? 1.0 : 0.0;
        v[i++] = sgs.getDrawDeck().getSize() / DECK_MAX;
        v[i++] = sgs.getDiscardDeck().getSize() / DECK_MAX;
        v[i++] = sgs.getPathCardOptions().size() / (double) (W * H);
        v[i++] = countVisibleGoals(sgs, playerId) / (double) sgp.nGoals;
        v[i++] = sgs.getRoundCounter() / 2.0;
        v[i++] = sgs.minersWinByRound[0] ? 1.0 : 0.0;
        v[i++] = sgs.minersWinByRound[1] ? 1.0 : 0.0;
        v[i++] = sgs.minersWinByRound[2] ? 1.0 : 0.0;
        v[i++] = n / (double) MAX_PLAYERS;
        v[i++] = paramS / (double) roleDeckSize;
        v[i++] = paramM / (double) roleDeckSize;
    }

    private int countVisibleGoals(SaboteurGameState sgs, int playerId) {
        int count = 0;
        PartialObservableGridBoard gridBoard = sgs.getGridBoard();
        for (int x = 0; x < gridBoard.getWidth(); x++) {
            for (int y = 0; y < gridBoard.getHeight(); y++) {
                PathCard card = (PathCard) gridBoard.getElement(x, y);
                if (card != null && card.type == PathCard.PathCardType.Goal
                        && gridBoard.getElementVisibility(x, y, playerId)) {
                    count++;
                }
            }
        }
        return count;
    }
}
