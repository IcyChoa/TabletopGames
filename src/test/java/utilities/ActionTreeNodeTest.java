package utilities;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Leaf IDs are the discrete action space for PyTAG. They must follow construction
 * order (DFS). BFS puts shallow leaves first and scrambles grid-shaped branches.
 */
public class ActionTreeNodeTest {

    @Test
    public void raggedTreeLeafOrderIsDepthFirst() {
        ActionTreeNode root = new ActionTreeNode(0, "root");
        ActionTreeNode place = root.addChild(0, "place");
        ActionTreeNode x0 = place.addChild(0, "x0");
        x0.addChild(0, "y0");
        x0.addChild(0, "y1");
        ActionTreeNode x1 = place.addChild(0, "x1");
        x1.addChild(0, "y0");
        x1.addChild(0, "y1");
        root.addChild(0, "doNothing");

        List<ActionTreeNode> leaves = root.getLeafNodes();
        assertEquals(5, leaves.size());
        assertEquals("y0", leaves.get(0).getName());
        assertEquals("y1", leaves.get(1).getName());
        assertEquals("y0", leaves.get(2).getName());
        assertEquals("y1", leaves.get(3).getName());
        assertEquals("doNothing", leaves.get(4).getName());
    }

    @Test
    public void getActionByVectorFollowsChildIndices() {
        ActionTreeNode root = new ActionTreeNode(0, "root");
        ActionTreeNode x0 = root.addChild(0, "x0");
        ActionTreeNode x1 = root.addChild(0, "x1");
        x0.addChild(0, "y0");
        x0.addChild(0, "y1");
        ActionTreeNode target = x1.addChild(0, "y0");
        x1.addChild(0, "y1");

        core.actions.DoNothing action = new core.actions.DoNothing();
        target.setAction(action);
        assertEquals(action, root.getActionByVector(new int[]{1, 0}));
        assertNull(root.getActionByVector(new int[]{0, 0}));
    }

    @Test
    public void flattenTreeDoesNotDuplicateRoot() {
        ActionTreeNode root = new ActionTreeNode(0, "root");
        root.addChild(0, "a");
        root.addChild(0, "b");
        List<ActionTreeNode> flat = root.flattenTree();
        assertEquals(3, flat.size());
        assertEquals("root", flat.get(0).getName());
        assertEquals("a", flat.get(1).getName());
        assertEquals("b", flat.get(2).getName());
    }
}
