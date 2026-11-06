package rume;

import java.util.*;
import java.util.function.Consumer;

public final class Inbox implements Consumer<MatchCreated> {
    private final Map<Integer, List<String>> messages = new HashMap<>();

    @Override
    public void accept(MatchCreated event) {
        notifyUser(event.first(), event.second());
        notifyUser(event.second(), event.first());
    }

    private void notifyUser(int user, int partner) {
        messages.computeIfAbsent(user, k -> new ArrayList<>()).add("Новое совпадение с анкетой #" + partner);
    }

    public List<String> read(int user) { return List.copyOf(messages.getOrDefault(user, List.of())); }
}
