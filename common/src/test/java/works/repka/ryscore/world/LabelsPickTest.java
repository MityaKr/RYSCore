package works.repka.ryscore.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Общий отбор подписей: отсечка, сначала пинги, потом цели, потом ближние, предел. */
class LabelsPickTest {
    private record L(String name, double dist, int rank) {}

    @Test
    void cutoffRankDistanceLimit() {
        List<L> all = List.of(new L("far point", 600, 2), new L("near point", 5, 2), new L("ping", 300, 0), new L("goal", 100, 1),
            new L("mid point", 50, 2), new L("far ping", 700, 0));
        List<L> out = Labels.pick(all, L::dist, L::rank, 3, 512);
        assertEquals(List.of("ping", "goal", "near point"), out.stream().map(L::name).toList());
    }

    @Test
    void underLimitKeepsAll() {
        List<L> out = Labels.pick(List.of(new L("a", 10, 2), new L("b", 1, 2)), L::dist, L::rank, 24, 512);
        assertEquals(List.of("b", "a"), out.stream().map(L::name).toList());
    }

    @Test
    void settingsHaveFloors() {
        Labels.setLimit(0);
        assertEquals(1, Labels.limit());
        Labels.setCutoff(1);
        assertEquals(16, Labels.cutoff());
        Labels.setLimit(Labels.LIMIT_DEFAULT);
        Labels.setCutoff(Labels.CUTOFF_DEFAULT);
    }
}
