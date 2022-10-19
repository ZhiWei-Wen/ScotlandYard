package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import com.google.common.collect.*;
import javax.annotation.Nonnull;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

public class SmartAI implements Ai {

    @Nonnull
    @Override
    public String name() {
        return "SmartAI";
    }

    public static class TreeNode {
        private final Player MrX;
        private final int DetectiveLocation;
        private List<TreeNode> children;

        public TreeNode(Player MrX, int Location) {
            this.MrX = MrX;
            this.DetectiveLocation = Location;
            this.children = new ArrayList<>();
        }
    }

    public class GameTree {
        private final TreeNode root;
        private final int height;


        public GameTree(Player MrX, int DetectiveLocation, Board board, int height) {
            this.root = new TreeNode(MrX, DetectiveLocation);
            this.height = height;
            this.build(root, board, true, 1);
        }

        public void build(TreeNode node, Board board, boolean isMaxPlayer, int depth) {
            if (depth == height)
                return;
            if(node.MrX.location() == node.DetectiveLocation)
                return;
            if (isMaxPlayer)
                for (int target : board.getSetup().graph.adjacentNodes(node.DetectiveLocation)) {
                    if (target == node.DetectiveLocation)
                        continue;
                    TreeNode NewNode = new TreeNode(node.MrX, target);
                    node.children.add(NewNode);
                    build(NewNode, board, false, depth + 1);
                }

            if (!isMaxPlayer)
                for (Player p : moveMaxPlayer(node.MrX, board)) {
                    TreeNode NewNode = new TreeNode(p, node.DetectiveLocation);
                    node.children.add(NewNode);
                    build(NewNode, board, true, depth + 1);
                }
        }

    }
    // MinMax with Alpha-Beta pruning, A represents Minimum value which MaximizingPlayer can secure (Current State),
    // B represents Maximum value which MinimizingPlayer can secure (Current State)
    public int MiniMax(Board board,TreeNode node, boolean isMaxPlayer, int A, int B) {
        if (node.children.isEmpty())
            return Dijkstra(board,node.MrX.location(), node.DetectiveLocation);
        int max = -2147483648;
        int min = 2147483647;
        if (isMaxPlayer) {
            for (TreeNode treeNode : node.children) {
                int temp = MiniMax(board,treeNode, false, A, B);
                max = Math.max(max, temp);
                if (max >= B) // if A is going to be bigger than B, cut off this node
                    break;
                A = Math.max(A, max);
            }
        } else {
            for (TreeNode treeNode : node.children) {
                int temp = MiniMax(board,treeNode, true, A, B);
                min = Math.min(min, temp);
                if (min <= A) // if B is going to be bigger than A, cut off this node
                    break;
                B = Math.min(B, min);
            }
        }
        return (isMaxPlayer) ? max : min;
    }

    // A class calculate all the ReachablePoints for Detectives within the given round;
    public class Exhaustion {
        private final GameSetup setup;
        private Set<Integer> ReachablePoints;
        private final int round;

        public Exhaustion(Board board, int round) {
            this.setup = board.getSetup();
            this.round = round;
            this.ReachablePoints = new HashSet<>();
            for (Player player: getPlayers(board))
                BFS(player, 0);
        }

        public void BFS(Player player, int depth) {
            if (depth <= round)
                ReachablePoints.add(player.location());
            else return;
            for (int target: setup.graph.adjacentNodes(player.location()))
                for (ScotlandYard.Transport t: setup.graph.edgeValueOrDefault(target, player.location(), ImmutableSet.of()))
                    if (player.has(t.requiredTicket()))
                        BFS(player.use(t.requiredTicket()).at(target), depth+1);
        }
    }

    // A method to filter out more unwinnable moves from a set of moves
    public Set<Move> filter (Set<Move> moves, Board board){
        Set<Move> SelectedMove = new HashSet<>();
        Exhaustion exhaustion = new Exhaustion(board,3);
        for (Move move :moves)
            if (!exhaustion.ReachablePoints.contains(getDestination(move)) )
                SelectedMove.add(move);
        if (SelectedMove.isEmpty()) {
            Exhaustion exhaustion1 = new Exhaustion(board, 2);
            for (Move move :moves)
                if (!exhaustion1.ReachablePoints.contains(getDestination(move))  )
                    SelectedMove.add(move);
        }
        if (SelectedMove.isEmpty()) {
            Exhaustion exhaustion2 = new Exhaustion(board, 1);
            for (Move move :moves)
                if (!exhaustion2.ReachablePoints.contains(getDestination(move))  )
                    SelectedMove.add(move);
        }
        if (SelectedMove.isEmpty())
            return moves;
        return  SelectedMove;
    }

    // get a Player type MrX with a given board
    public Player getPlayer(Board board) {
        List<Move> moves = new ArrayList<>(board.getAvailableMoves());
        int MrXLocation = moves.get(0).source();
        Map<ScotlandYard.Ticket, Integer> MrXTickets = new HashMap<>();
        for (ScotlandYard.Ticket t : ScotlandYard.MRX_TICKETS)
            MrXTickets.put(t, board.getPlayerTickets(Piece.MrX.MRX).get().getCount(t));
        return new Player(Piece.MrX.MRX, ImmutableMap.copyOf(MrXTickets), MrXLocation);
    }

    // get Player type detectives with a given board
    public List<Player> getPlayers (Board board) {
        List<Player> detectives = new ArrayList<>();
        for (Piece piece: board.getPlayers()) {
            if (piece.isDetective()) {
                Map<ScotlandYard.Ticket, Integer> DetectiveTickets = new HashMap<>();
                for (ScotlandYard.Ticket t: ScotlandYard.DETECTIVE_TICKETS)
                    DetectiveTickets.put(t, board.getPlayerTickets(piece).get().getCount(t));
                detectives.add(new Player(piece, ImmutableMap.copyOf(DetectiveTickets),board.getDetectiveLocation((Piece.Detective) piece).get()));
            }
        }
        return detectives;
    }

    // method to get different Player type MrX after moves (Only consider single move without SECRET ticket for simplification
    public List<Player> moveMaxPlayer(Player MaxPlayer, Board board) {
        GameSetup setup = board.getSetup();
        List<Player> NewPlayers = new ArrayList<>();
        for (int target : setup.graph.adjacentNodes(MaxPlayer.location()))
            for (ScotlandYard.Transport t : setup.graph.edgeValueOrDefault(MaxPlayer.location(), target, ImmutableSet.of()))
                if (MaxPlayer.has(t.requiredTicket()))
                    NewPlayers.add(MaxPlayer.use(t.requiredTicket()).at(target));
        return NewPlayers;
    }

    public int Dijkstra(Board board, int source, int destination) {
        GameSetup setup = board.getSetup();

        int[] dis = new int[setup.graph.nodes().size() + 1];
        class sortbyDis implements Comparator<Integer> {
            @Override
            public int compare(Integer o1, Integer o2) {
                return dis[o1] - dis[o2];
            }
        }
        Set<Integer> VisitedNodes = new HashSet<>(setup.graph.nodes());
        for (int node : setup.graph.nodes()) {
            dis[node] = 2147483647;
        }
        dis[source] = 0;
        while (!VisitedNodes.isEmpty()) {
            int u = Collections.min(VisitedNodes, new sortbyDis());
            if (u == destination)
                break;
            VisitedNodes.remove(u);
            for (int v : setup.graph.adjacentNodes(u)) {
                if (VisitedNodes.contains(v)) {
                    int ans = dis[u] + 1;
                    if (ans < dis[v])
                        dis[v] = ans;
                }
            }
        }
        return dis[destination];
    }

    // get the locations of detectives
    public List<Integer> getLocations(Board board) {
        List<Integer> Locations = new ArrayList<>();
        for (Piece p : board.getPlayers())
            if (p.isDetective())
                Locations.add(board.getDetectiveLocation((Piece.Detective) p).get());
        return Locations;
    }

    public int getDestination(Move move) {
        Move.SingleMove sgMove = move.accept(new Move.Visitor<>() {
            @Override
            public Move.SingleMove visit(Move.SingleMove singleMove) {
                return singleMove;
            }

            @Override
            public Move.SingleMove visit(Move.DoubleMove doubleMove) {
                return null;
            }
        });
        Move.DoubleMove dbMove = move.accept(new Move.Visitor<>() {
            @Override
            public Move.DoubleMove visit(Move.SingleMove singleMove) {
                return null;
            }

            @Override
            public Move.DoubleMove visit(Move.DoubleMove doubleMove) {
                return doubleMove;
            }
        });
        int s = move.source();
        if (sgMove != null)
            s = sgMove.destination;
        if (dbMove != null)
            s = dbMove.destination2;
        return s;
    }

    @Nonnull
    @Override
    public Move pickMove(
            @Nonnull Board board,
            Pair<Long, TimeUnit> timeoutPair) {
        List<GameTree> gameTreeList = new ArrayList<>();
        int[] value = new int[board.getSetup().graph.nodes().size()+1];
        Player MrX = getPlayer(board);
        // Game trees are build between MrX and every detective (Separate the whole game into multiple two-player zero-sum games).
        for (Player player: moveMaxPlayer(MrX,board)) {
            int ans = 0;
            for (int i: getLocations(board)) {
                GameTree  NewGameTree = new GameTree(player, i, board, 8);
                gameTreeList.add(NewGameTree);
                ans = ans + MiniMax(board,NewGameTree.root,true, -2147483648,2147383647);
            }
            value[player.location()] = ans;
        }
        class sortbyValue implements Comparator<Move> {
            @Override
            public int compare(Move m1, Move m2) {
                return value[getDestination(m1)] - value[getDestination(m2)];
            }
        }
        return Collections.max(filter(board.getAvailableMoves(), board), new sortbyValue());
    }
}