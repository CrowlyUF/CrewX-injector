package crewx.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;

/** Reads the server health objective when present, without changing the entity's health. */
public final class PlayerHealth {
    private PlayerHealth() {}

    /** Returns NaN when this player has no score in the below-name health objective. */
    public static float scoreboard(EntityPlayer player) {
        if (player == null) return Float.NaN;
        Scoreboard board = player.getWorldScoreboard();
        if (board == null) return Float.NaN;
        ScoreObjective objective = board.getObjectiveInDisplaySlot(2);
        if (objective == null) return Float.NaN;
        Score score = board.getObjectivesForEntity(player.getName()).get(objective);
        return score == null ? Float.NaN : score.getScorePoints();
    }

    public static float current(EntityPlayer player) {
        float score = scoreboard(player);
        return Float.isNaN(score) ? player.getHealth() : score;
    }
}
