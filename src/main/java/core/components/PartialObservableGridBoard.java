package core.components;

import java.util.*;

public class PartialObservableGridBoard extends GridBoard
{
    //visibility of Board for each player
    private boolean[] gridBoardVisibility;

    //visibility of cell in Grid for each player, order corresponds to playerID
    private List<boolean[][]> elementVisibility = new ArrayList<>();
//--------------------------------------------------------------------------------------------------//
//region Constructor
    public PartialObservableGridBoard(int width, int height, int nPlayers, boolean defaultValue)
    {
        super(width, height);
        initVisibility(width, height, nPlayers, defaultValue);
    }

    private PartialObservableGridBoard(int width, int height, int nPlayers, boolean defaultValue, int componentID)
    {
        super(width, height, componentID);
        initVisibility(width, height, nPlayers, defaultValue);
    }

    private PartialObservableGridBoard(BoardNode[][] grid, boolean[] gridBoardVisibility, List<boolean[][]> elementVisibility, int componentID)
    {
        super(grid, componentID);
        this.gridBoardVisibility = gridBoardVisibility.clone();
        this.elementVisibility = deepCopyVisibility(elementVisibility);
    }

    /** Each player must own a separate visibility grid; sharing one array leaks Map peeks across players. */
    private void initVisibility(int width, int height, int nPlayers, boolean defaultValue) {
        elementVisibility = new ArrayList<>(nPlayers);
        for (int p = 0; p < nPlayers; p++) {
            boolean[][] visibilityGrid = new boolean[height][width];
            for (int i = 0; i < height; i++) {
                Arrays.fill(visibilityGrid[i], defaultValue);
            }
            elementVisibility.add(visibilityGrid);
        }
        gridBoardVisibility = new boolean[nPlayers];
        Arrays.fill(gridBoardVisibility, defaultValue);
    }

    private static List<boolean[][]> deepCopyVisibility(List<boolean[][]> source) {
        List<boolean[][]> copy = new ArrayList<>(source.size());
        for (boolean[][] visibility : source) {
            boolean[][] gridCopy = new boolean[visibility.length][];
            for (int r = 0; r < visibility.length; r++) {
                gridCopy[r] = visibility[r].clone();
            }
            copy.add(gridCopy);
        }
        return copy;
    }
//endregion
//--------------------------------------------------------------------------------------------------//
//region isVisible Functions
    public boolean isBoardVisible(int playerID)
    {
        checkBoardVisibilityArgument(playerID);

        if(!gridBoardVisibility[playerID])
        {
            return false;
        }

        return gridBoardVisibility[playerID];
    }

    public boolean isCellVisible(int x, int y, int playerID)
    {
        checkBoardVisibilityArgument(playerID);
        CheckGridRangeArgument(x,y);

        return elementVisibility.get(playerID)[y][x];
    }
//endregion
//--------------------------------------------------------------------------------------------------//

    public void setElementVisibility(int x, int y, int playerID, boolean value)
    {
        checkBoardVisibilityArgument(playerID);

        elementVisibility.get(playerID)[y][x] = value;
    }

    public boolean getElementVisibility(int x, int y, int player) {
        return elementVisibility.get(player)[y][x];
    }

    public List<Boolean> getElementVisibility(int x, int y) {
        List<Boolean> vis = new ArrayList<>();
        for (boolean[][] booleans : elementVisibility) {
            vis.add(booleans[y][x]);
        }
        return vis;
    }

//region Argument Checks
    public void checkBoardVisibilityArgument(int playerID)
    {
        if (playerID < 0 || playerID >= gridBoardVisibility.length)
            throw new IllegalArgumentException("playerID " + playerID + " needs to be in range [0," + (gridBoardVisibility.length - 1) + "]");
    }

    public void CheckGridRangeArgument(int x, int y)
    {
        if(getElement(x, y) == null)
        {
            throw new IllegalArgumentException("Cell Coordinates need to be between [0," + getWidth() + "] [0," + getHeight() + "]");
        }
    }
//endregion
//--------------------------------------------------------------------------------------------------//
    @Override
    public PartialObservableGridBoard copy()
    {
        BoardNode[][] gridCopy = new BoardNode[getHeight()][getWidth()];
        Map<Integer, BoardNode> nodeCopies = new HashMap<>();
        for (int i = 0; i < getHeight(); i++) {
            for (int j = 0; j < getWidth(); j++) {
                if (getGridValues()[i][j] != null) {
                    gridCopy[i][j] = new BoardNode(getGridValues()[i][j]);
                    getGridValues()[i][j].copyComponentTo(gridCopy[i][j]);
                    nodeCopies.put(gridCopy[i][j].componentID, gridCopy[i][j]);
                }
            }
        }
        for (int i = 0; i < getHeight(); i++) {
            for (int j = 0; j < getWidth(); j++) {
                if (getGridValues()[i][j] != null) {
                    for (Map.Entry<BoardNode, Double> neighbour : getGridValues()[i][j].getNeighbours().entrySet()) {
                        gridCopy[i][j].addNeighbourWithCost(nodeCopies.get(neighbour.getKey().componentID), neighbour.getValue());
                    }
                    for (Map.Entry<BoardNode, Integer> neighbour : getGridValues()[i][j].getNeighbourSideMapping().entrySet()) {
                        gridCopy[i][j].addNeighbourOnSide(nodeCopies.get(neighbour.getKey().componentID), neighbour.getValue());
                    }
                }
            }
        }
        PartialObservableGridBoard copy = new PartialObservableGridBoard(gridCopy, gridBoardVisibility, elementVisibility, componentID);
        copyComponentTo(copy);
        return copy;
    }

    public PartialObservableGridBoard emptyCopy() {
        // default hidden; callers that need visibility must copy it from the source state
        PartialObservableGridBoard g = new PartialObservableGridBoard(getWidth(), getHeight(), elementVisibility.size(), false, componentID);
        copyComponentTo(g);
        return g;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
/*        for(int i = 0; i < gridBoardVisibility.length; i++)
        {
            sb.append("Player ").append(i).append(" visibility: ").append(gridBoardVisibility[i]).append("\n");
        }*/
        for(int j = 0; j < getHeight(); j++)
        {
            for(int k = 0; k < getWidth(); k++)
            {
                BoardNode pathCard = getElement(k, j);
                if(pathCard != null)
                {
                    sb.append(pathCard);
                }
                else
                {
                    sb.append("░");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public String toString(int x, int y)
    {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < gridBoardVisibility.length; i++)
        {
            sb.append("Player ").append(i).append(" visibility: ").append(gridBoardVisibility[i]).append("\n");
        }
        for(int j = 0; j < getHeight(); j++)
        {
            for(int k = 0; k < getWidth(); k++)
            {
                if(j == y && x == k)
                {
                    sb.append("X");
                    continue;
                }
                BoardNode pathCard = getElement(k, j);
                if(pathCard != null)
                {
                    sb.append(pathCard);
                }
                else
                {
                    sb.append("░");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PartialObservableGridBoard that)) return false;
        if (!super.equals(o)) return false;
        return Arrays.equals(gridBoardVisibility, that.gridBoardVisibility) && Objects.equals(elementVisibility, that.elementVisibility);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(super.hashCode());
        result = 31 * result + elementVisibility.stream()
                .mapToInt(Arrays::deepHashCode) // Apply deepHashCode for each boolean[][]
                .reduce(31, (acc, hash) -> 31 * acc + hash);
        result = 31 * result + Arrays.hashCode(gridBoardVisibility);
        return result;
    }
}
