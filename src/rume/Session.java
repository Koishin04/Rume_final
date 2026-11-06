package rume;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class Session {
    final Roommates people = new Roommates();
    final Inbox inbox = new Inbox();
    final MatchCounter counter = new MatchCounter();
    final Map<Integer,Profile> profiles = new TreeMap<>();
    final Map<Integer,String> photos = new HashMap<>();
    private final Path file;
    private final List<String> history = new ArrayList<>();

    Session(Path directory) throws IOException {
        file = directory.resolve("history.tsv");
        people.onMatch(inbox);
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file)) {
                apply(line);
                history.add(line);
            }
        }
        people.onMatch(counter);
    }
    String save(String... fields) throws IOException {
        for (String field : fields)
            if (field.contains("\t") || field.contains("\n") || field.contains("\r"))
                throw new IllegalArgumentException("Поле содержит недопустимый перенос или табуляцию.");
        String line = String.join("\t", fields);
        String result = apply(line);
        history.add(line);
        Path temp = file.resolveSibling("history.tmp");
        Files.write(temp, history);
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        return result;
    }

    private String apply(String line) {
        String[] f = line.split("\t", -1);
        int id = Integer.parseInt(f[1]);
        switch (f[0]) {
            case "profile" -> {
                var profile = new Profile(id,f[2],Integer.parseInt(f[3]),f[4],Integer.parseInt(f[5]));
                people.saveProfile(profile);
                profiles.put(id,profile);
                return "Анкета сохранена.";
            }
            case "photo" -> { photos.put(id,f[2]); return "Путь к фото сохранён."; }
            case "like", "skip" -> { return people.vote(id,Integer.parseInt(f[2]),f[0].equals("like")); }
            default -> throw new IllegalArgumentException("Неизвестная запись в файле данных.");
        }
    }
}
