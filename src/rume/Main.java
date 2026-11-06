package rume;

import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;

public final class Main {
    @FunctionalInterface interface Command { void run() throws IOException; }
    private final Scanner input = new Scanner(System.in, java.nio.charset.StandardCharsets.UTF_8);
    private final Session data;
    private final Map<String,Command> commands = new LinkedHashMap<>();
    private int active;

    Main(Session data) {
        this.data = data;
        commands.put("1", () -> profile(true));
        commands.put("2", this::select);
        commands.put("3", member(this::search));
        commands.put("4", member(() -> vote("like")));
        commands.put("5", member(() -> vote("skip")));
        commands.put("6", member(() -> show(data.people.matches(active))));
        commands.put("7", member(() -> {
            var messages = data.inbox.read(active);
            if (messages.isEmpty()) System.out.println("Уведомлений пока нет.");
            else messages.forEach(System.out::println);
        }));
        commands.put("8", member(new ConfirmCommand(
            () -> ask("Изменить выбранную анкету? (да/нет)").equalsIgnoreCase("да"), () -> profile(false))));
        commands.put("9", member(() -> {
            Path file = Path.of(ask("Путь к фото")).toAbsolutePath().normalize();
            if (!Files.isRegularFile(file)) throw new IllegalArgumentException("Файл не найден.");
            System.out.println(data.save("photo",id(),file.toString()));
        }));
    }
    // Decorator around commands that require an active profile.
    private Command member(Command next) {
        return new RegisteredCommand(() -> active != 0, next);
    }
    private String id() { return String.valueOf(active); }
    private String ask(String prompt) {
        System.out.print(prompt+" > ");
        System.out.flush();
        return input.nextLine().strip();
    }
    private void show(Collection<Profile> list) {
        if (list.isEmpty()) System.out.println("Список пуст.");
        for (var p : list) {
            System.out.printf("#%d  %s, %d  |  %s  |  %d тг/мес%n",p.id(),p.name(),p.age(),p.district(),p.budget());
            if (data.photos.containsKey(p.id())) System.out.println("Фото: "+data.photos.get(p.id()));
        }
    }
    private void profile(boolean create) throws IOException {
        int target = create ? data.profiles.size()+1 : active;
        String result=data.save("profile",String.valueOf(target),ask("Имя"),ask("Возраст (18+)"),ask("Район"),ask("Бюджет в тенге за месяц"));
        active=target;
        System.out.println(result+" Ваш ID: #"+active);
    }
    private void select() {
        show(data.profiles.values());
        if (data.profiles.isEmpty()) return;
        int target=Integer.parseInt(ask("ID вашей анкеты"));
        if (!data.profiles.containsKey(target)) throw new IllegalArgumentException("Такой анкеты нет.");
        active=target;
    }
    private void search() {
        var builder=new Search.Builder();
        String area=ask("Район (Enter — любой)");
        if (!area.isEmpty()) builder.district(area);
        String limit=ask("Максимальный бюджет (Enter — любой)");
        if (!limit.isEmpty()) builder.maxBudget(Integer.parseInt(limit));
        String order=ask("Сортировка: 1 — по бюджету, 2 — по ID (Enter — бюджет)");
        if (order.isEmpty() || order.equals("1")) builder.cheapestFirst();
        else if (!order.equals("2")) throw new IllegalArgumentException("Выберите 1 или 2.");
        show(data.people.find(active,builder.build()));
    }
    private void vote(String type) throws IOException {
        System.out.println(data.save(type,id(),ask("ID кандидата")));
    }
    private void run() throws IOException {
        while (true) {
            System.out.println("\nRuMe  |  "+(active==0 ? "Анкета не выбрана" : data.profiles.get(active).name()+" #"+active));
            System.out.println("1 Создать анкету     2 Сменить пользователя   3 Найти соседей");
            System.out.println("4 Лайк               5 Пропустить             6 Совпадения");
            System.out.println("7 Уведомления        8 Изменить анкету        9 Указать фото");
            System.out.println("Новых совпадений за этот сеанс: " + data.counter.total());
            try {
                String choice=ask("0 Выход  |  Ваш выбор");
                if (choice.equals("0")) return;
                var command=commands.get(choice);
                if (command==null) System.out.println("Выберите пункт от 0 до 9.");
                else command.run();
            } catch (NoSuchElementException e) { return; }
              catch (NumberFormatException e) { System.out.println("Введите целое число."); }
              catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
        }
    }
    public static void main(String[] args) throws IOException {
        Path dir=Path.of("data");
        Files.createDirectories(dir);
        try (var channel=FileChannel.open(dir.resolve("session.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
             var lock=channel.tryLock()) {
            if (lock==null) { System.out.println("Закройте второе окно RuMe."); return; }
            new Main(new Session(dir)).run();
        }
    }
}
