package cn.com.vortexa.script_bot.daily.magic_newton_v2;

import cn.hutool.core.lang.Pair;

import java.util.*;

public class MinesweeperSolver {

    public static Map<String, Set<Pair<Integer, Integer>>> solve(List<List<Integer>> board) {
        int rows = board.size();
        int cols = board.getFirst().size();

        Set<Pair<Integer, Integer>> toClick = new HashSet<>();
        Set<Pair<Integer, Integer>> toMarkBomb = new HashSet<>();

        int[][] directions = {
                {-1, -1}, {-1, 0}, {-1, 1},
                {0, -1}, {0, 1},
                {1, -1}, {1, 0}, {1, 1}
        };

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Integer val = board.get(r).get(c);
                if (val == null || val == -1 || val == 0) continue;

                List<Pair<Integer, Integer>> unknowns = new ArrayList<>();
                int bombCount = 0;

                for (int[] d : directions) {
                    int nr = r + d[0], nc = c + d[1];
                    if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                        Integer neighbor =  board.get(nr).get(nc);
                        if (neighbor == null) {
                            unknowns.add(new Pair<Integer, Integer>(nr, nc));
                        } else if (neighbor == -1) {
                            bombCount++;
                        }
                    }
                }

                int remaining = val - bombCount;
                if (remaining == unknowns.size()) {
                    toMarkBomb.addAll(unknowns);
                } else if (remaining == 0) {
                    toClick.addAll(unknowns);
                }
            }
        }

        // 防止交叉重复：点击集不包括已确认是炸弹的格子
        toClick.removeAll(toMarkBomb);

        Map<String, Set<Pair<Integer, Integer>>> result = new HashMap<>();
        result.put("click", toClick);
        result.put("boom", toMarkBomb);
        return result;
    }

    public static void main(String[] args) {
        List<List<Integer>> board = List.of(
                new ArrayList<>(Arrays.asList(null, null, null, null, null)),
                new ArrayList<>(Arrays.asList(null, -1, null, null, null)),
                new ArrayList<>(Arrays.asList(null, null, 1, null, null)),
                new ArrayList<>(Arrays.asList(null, null, null, null, null)),
                new ArrayList<>(Arrays.asList(null, null, null, null, null)),
                new ArrayList<>(Arrays.asList(null, null, null, null, null)),
                new ArrayList<>(Arrays.asList(null, null, null, null, null))
        );
        System.out.println(solve(board));
    }
}
