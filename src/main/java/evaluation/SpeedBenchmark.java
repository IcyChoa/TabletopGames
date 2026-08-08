package evaluation;

import core.AbstractForwardModel;
import core.AbstractGameState;
import core.Game;
import core.actions.AbstractAction;
import games.GameType;
import core.AbstractPlayer;
import players.simple.RandomPlayer;

import java.util.ArrayList;
import java.util.List;

public class SpeedBenchmark {

    public static void main(String[] args) {
        // 1. Setup: Choose the game and number of players
        GameType gameToTest = GameType.Saboteur; // Change this to test other games (e.g., Pandemic, TicTacToe)
        int numPlayers = 4;
        int numGamesToRun = 1000;

        // Create Random Players
        List<AbstractPlayer> players = new ArrayList<>();
        for (int i = 0; i < numPlayers; i++) {
            players.add(new RandomPlayer());
        }

        long totalNextTimeNs = 0;
        long totalCopyTimeNs = 0;
        int totalTicks = 0;

        System.out.println("Starting speed benchmark for: " + gameToTest.name());

        // 2. Benchmarking Loop
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numGamesToRun; i++) {
            // Initialize the game
            Game game = gameToTest.createGameInstance(numPlayers);
            game.reset(players);
            
            AbstractGameState gameState = game.getGameState();
            AbstractForwardModel forwardModel = game.getForwardModel();

            // Run the game until it finishes
            while (gameState.isNotTerminal()) {
                int currentPlayer = gameState.getCurrentPlayer();
                
                // --- Measure Copy Speed ---
                long startCopy = System.nanoTime();
                AbstractGameState stateCopy = gameState.copy(currentPlayer);
                totalCopyTimeNs += (System.nanoTime() - startCopy);

                // Ask the random player for an action
                List<AbstractAction> availableActions = forwardModel.computeAvailableActions(stateCopy);
                AbstractAction action = players.get(currentPlayer).getAction(stateCopy, availableActions);

                // --- Measure Next Speed ---
                long startNext = System.nanoTime();
                forwardModel.next(gameState, action);
                totalNextTimeNs += (System.nanoTime() - startNext);
                
                totalTicks++;
            }
        }

        long totalExecutionMs = System.currentTimeMillis() - startTime;

        // 3. Print Results
        System.out.println("=========================================");
        System.out.println("Games played: " + numGamesToRun);
        System.out.println("Total Game Ticks (Actions): " + totalTicks);
        System.out.println("Total Time: " + (totalExecutionMs / 1000.0) + " seconds");
        System.out.println("-----------------------------------------");
        System.out.println("Average Copy() time: " + (totalCopyTimeNs / (double) totalTicks / 1_000_000.0) + " ms");
        System.out.println("Average Next() time: " + (totalNextTimeNs / (double) totalTicks / 1_000_000.0) + " ms");
        System.out.println("=========================================");
    }
}