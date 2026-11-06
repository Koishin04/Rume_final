package rume;

import java.util.function.Consumer;

final class MatchCounter implements Consumer<MatchCreated> {
    private int count;

    @Override
    public void accept(MatchCreated match) {
        count++;
    }

    int total() { return count; }
}
