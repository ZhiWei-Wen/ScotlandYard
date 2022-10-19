package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;


import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

public class EasyAI implements Ai {

    @Nonnull @Override public String name() { return "EasyAI"; }

    public int[] Dijkstra (Board board, int source) {
        GameSetup setup = board.getSetup();

        int[] dis = new int[setup.graph.nodes().size() + 1];
        class sortbyDis implements Comparator<Integer> {
            @Override
            public int compare(Integer o1, Integer o2) {
                return dis[o1] - dis[o2];
            }
        }
        Set<Integer> VisitedNodes = new HashSet<>(setup.graph.nodes());
        for (int node: setup.graph.nodes()) {
            dis[node] = 2147483647;
        }
        dis[source] = 0;
        while(!VisitedNodes.isEmpty()) {
            int u = Collections.min(VisitedNodes, new sortbyDis());
            VisitedNodes.remove(u);
            for (int v: setup.graph.adjacentNodes(u)) {
                if (VisitedNodes.contains(v)) {
                    int ans = dis[u] + 1;
                    if (ans < dis[v])
                        dis[v] = ans;
                }
            }
        }
        return dis;
    }

    public List<Integer> getLocations (Board board) {
        List<Integer> Locations = new ArrayList<>();
        for (Piece p: board.getPlayers())
            if (p.isDetective())
                Locations.add(board.getDetectiveLocation((Piece.Detective) p).get());
        return Locations;
    }

    public int score (Move move, Board board) {
        int source = getDestination(move);
        int ans = 0;
        int[] dis = Dijkstra(board, source);
        for (int i: getLocations(board))
            if (dis[i] == 1)
                return 0;
                    else ans = ans + dis[i];
        return ans;
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

    @Nonnull @Override public Move pickMove(
            @Nonnull Board board,
            Pair<Long, TimeUnit> timeoutPair) {
        class sortbyScore implements Comparator<Move> {

            @Override
            public int compare(Move m1, Move m2) {
                return score(m1, board) - score(m2, board);
            }
        }

        return Collections.max(board.getAvailableMoves(), new sortbyScore());
    }
}