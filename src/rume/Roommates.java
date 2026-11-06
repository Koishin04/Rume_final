package rume;

import java.util.*;
import java.util.function.Consumer;

public final class Roommates {
    private record Vote(int from, int to) {}
    private final Map<Integer, Profile> profiles = new HashMap<>();
    private final Set<Vote> liked = new HashSet<>();
    private final Set<Vote> skipped = new HashSet<>();
    private final List<Consumer<MatchCreated>> listeners = new ArrayList<>();
    private final Map<Integer, List<Integer>> partners = new HashMap<>();

    public void saveProfile(Profile profile) {
        profiles.put(profile.id(), profile);

    }

    private void requireProfile(int id) {
        if (!profiles.containsKey(id)) throw new IllegalArgumentException("Неизвестная анкета #" + id);
    }

    public List<Profile> find(int id, Search search) {
        requireProfile(id);
        return profiles.values().stream()
            .filter(p -> p.id() != id && !seen(new Vote(id, p.id())))
            .filter(search::accepts).sorted(search.ranking()).toList();
    }

    private boolean seen(Vote vote) { return liked.contains(vote) || skipped.contains(vote); }

    public String vote(int from, int to, boolean like) {
        requireProfile(from);
        requireProfile(to);
        if (from == to) throw new IllegalArgumentException("Нельзя оценить свою анкету.");
        Vote vote = new Vote(from, to);
        if (seen(vote)) return "Вы уже оценили эту анкету.";
        if (!like) {
            skipped.add(vote);
            return "Анкета пропущена.";
        }
        liked.add(vote);
        if (!liked.contains(new Vote(to, from))) return "Лайк сохранён.";
        partners.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
        partners.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
        var event = new MatchCreated(from, to);
        for (var listener : listeners) listener.accept(event);
        return "Взаимный лайк! У вас совпадение.";
    }

    public List<Profile> matches(int id) {
        requireProfile(id);
        return partners.getOrDefault(id, List.of()).stream().map(profiles::get).toList();
    }

    public void onMatch(Consumer<MatchCreated> listener) { listeners.add(listener); }
}
