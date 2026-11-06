package rume;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** Small local HTTP adapter. Session owns persistence and Roommates owns matching. */
public final class Web {
    private final Path directory;
    private final String host;

    Web(Path directory, int port) throws IOException {
        this.directory = directory;
        host = "127.0.0.1:" + port;
    }

    private List<String[]> demoProfiles() throws IOException {
        Path local = Path.of("web", "portraits", "profiles.tsv");
        String text;
        if (Files.isRegularFile(local)) text = Files.readString(local, StandardCharsets.UTF_8);
        else try (var stream = Web.class.getResourceAsStream("/web/portraits/profiles.tsv")) {
            if (stream == null) throw new IOException("Missing demo profiles");
            text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        var rows = text.replace("\uFEFF","").lines().filter(line -> !line.isBlank())
            .map(line -> line.split("\t",-1)).toList();
        Set<Integer> ids = new HashSet<>();
        for (var row : rows) {
            if (row.length != 6) throw new IllegalArgumentException("profiles.tsv: в каждой строке нужны 6 полей, разделённых Tab.");
            for (int i = 0; i < row.length; i++) row[i] = row[i].strip();
            var p = new Profile(Integer.parseInt(row[0]),row[1],Integer.parseInt(row[2]),row[3],Integer.parseInt(row[4]));
            if (!ids.add(p.id())) throw new IllegalArgumentException("profiles.tsv: повторяется ID " + p.id());
            if (!row[5].matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("profiles.tsv: неверное имя фотографии.");
        }
        return rows;
    }

    private static Map<String,String> fields(String encoded) {
        Map<String,String> values = new HashMap<>();
        if (encoded != null) for (String pair : encoded.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) values.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return values;
    }

    private static String quote(String value) {
        var out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') out.append('\\');
            if (c < 32) out.append(String.format("\\u%04x", (int)c));
            else out.append(c);
        }
        return out.append('"').toString();
    }

    private static String profile(Profile p) {
        return "{\"id\":" + p.id() + ",\"name\":" + quote(p.name()) + ",\"age\":" + p.age()
            + ",\"district\":" + quote(p.district()) + ",\"budget\":" + p.budget() + "}";
    }
    private static String profiles(Collection<Profile> list) {
        return list.stream().map(Web::profile).collect(Collectors.joining(",", "[", "]"));
    }
    // Demo profiles live in a separate directory so the terminal keeps its real history.
    private Session session(Map<String,String> form) throws IOException {
        if (!"1".equals(form.get("demo"))) return new Session(directory);
        var rows = demoProfiles();
        Path folder = Files.createDirectories(directory.resolve("demo-photos"));
        var demo = new Session(folder);
        Path source = folder.resolve("profiles-source.tsv");
        Map<String,String> previous = new HashMap<>();
        if (Files.exists(source)) for (String line : Files.readAllLines(source))
            previous.put(line.split("\t",2)[0],line);
        List<String> current = new ArrayList<>();
        for (var profile : rows) {
            int id = Integer.parseInt(profile[0]);
            String line = String.join("\t",profile);
            current.add(line);
            if (!line.equals(previous.get(profile[0])) || !demo.profiles.containsKey(id)) {
                var desired = new Profile(id,profile[1],Integer.parseInt(profile[2]),profile[3],Integer.parseInt(profile[4]));
                if (!desired.equals(demo.profiles.get(id)))
                    demo.save("profile",profile[0],profile[1],profile[2],profile[3],profile[4]);
            }
        }
        if (!Files.exists(source) || !current.equals(Files.readAllLines(source))) {
            Path temp = folder.resolve("profiles-source.tmp");
            Files.write(temp,current);
            Files.move(temp,source,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }
        return demo;
    }

    private void photo(HttpExchange x, Map<String,String> f) throws IOException {
        var data = session(f);
        int id = Integer.parseInt(f.getOrDefault("id","0"));
        if (!data.profiles.containsKey(id)) { json(x,404,"{}"); return; }
        if ("POST".equals(x.getRequestMethod())) {
            byte[] bytes = x.getRequestBody().readNBytes(2_000_001);
            if (bytes.length > 2_000_000 || bytes.length < 4 || bytes[0] != (byte)255 || bytes[1] != (byte)216) {
                json(x,400,"{\"error\":\"Выберите фото JPEG до 2 МБ.\"}"); return;
            }
            Path base = "1".equals(f.get("demo")) ? directory.resolve("demo-photos") : directory;
            Path folder = Files.createDirectories(base.resolve("photos"));
            Path target = folder.resolve(id+".jpg");
            Path tmp = Files.createTempFile(folder,"img",".tmp");
            try {
                Files.write(tmp,bytes);
                Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(tmp); }
            data.save("photo",String.valueOf(id),target.toString());
            json(x,200,"{}"); return;
        }
        String saved = data.photos.get(id);
        if (saved != null) {
            Path path = Path.of(saved);
            if (Files.isRegularFile(path) && Files.size(path) <= 10_000_000) {
                byte[] bytes = Files.readAllBytes(path);
                String mime = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(bytes));
                if (mime != null && Set.of("image/jpeg","image/png","image/gif").contains(mime)) {
                    send(x,200,mime,bytes); return;
                }
            }
        } else if ("1".equals(f.get("demo"))) {
            for (var profile : demoProfiles()) {
                if (Integer.parseInt(profile[0]) != id) continue;
                String fileName = profile[5];
                Path localPhoto = Path.of("web","portraits",fileName);
                try (var image = Files.isRegularFile(localPhoto) ? Files.newInputStream(localPhoto)
                        : Web.class.getResourceAsStream("/web/portraits/" + fileName)) {
                    if (image == null) throw new IOException("Missing demo portrait");
                    String mime = fileName.toLowerCase(Locale.ROOT).endsWith(".png") ? "image/png" : "image/jpeg";
                    send(x,200,mime,image.readAllBytes()); return;
                }
            }
        }
        x.sendResponseHeaders(204,-1);
    }
    private String state(Map<String,String> form) throws IOException {
        var data = session(form);
        int id = Integer.parseInt(form.getOrDefault("id", "0"));
        var search = new Search.Builder();
        search.district(form.getOrDefault("district","").strip());
        String limit = form.getOrDefault("budget","");
        if (!limit.isEmpty()) search.maxBudget(Integer.parseInt(limit));
        if (!"id".equals(form.get("sort"))) search.cheapestFirst();
        String all = profiles(data.profiles.values());
        String real = "1".equals(form.get("demo")) ? profiles(new Session(directory).profiles.values()) : all;
        String prefix = "{\"real\":" + real + ",\"profiles\":";
        if (!data.profiles.containsKey(id))
            return prefix + all + ",\"candidates\":[],\"matches\":[],\"inbox\":[]}";
        String messages = data.inbox.read(id).stream().map(Web::quote).collect(Collectors.joining(",","[","]"));
        return prefix + all + ",\"candidates\":" + profiles(data.people.find(id, search.build()))
            + ",\"matches\":" + profiles(data.people.matches(id)) + ",\"inbox\":" + messages + "}";
    }
    private String change(Map<String,String> f) throws IOException {
        var data = session(f);
        String action = f.getOrDefault("action", "");
        int id = Integer.parseInt(f.getOrDefault("id","0"));
        if (action.equals("create")) id = data.profiles.size() + 1;
        else if (!data.profiles.containsKey(id)) throw new IllegalArgumentException("Выберите анкету.");
        String message = switch (action) {
            case "create", "edit" -> data.save("profile", String.valueOf(id), f.getOrDefault("name","").strip(),
                f.getOrDefault("age",""), f.getOrDefault("district","").strip(), f.getOrDefault("budget",""));
            case "like", "skip" -> data.save(action,String.valueOf(id), f.getOrDefault("target",""));
            default -> throw new IllegalArgumentException("Неизвестное действие.");
        };
        return "{\"id\":" + id + ",\"message\":" + quote(message) + "}";
    }

    private static void send(HttpExchange x, int status, String type, byte[] bytes) throws IOException {
        x.getResponseHeaders().set("Content-Type",type + "; charset=utf-8");
        x.getResponseHeaders().set("Cache-Control","no-store");
        x.sendResponseHeaders(status,bytes.length);
        x.getResponseBody().write(bytes);
    }
    private static void json(HttpExchange x,int status,String text) throws IOException {
        send(x,status,"application/json",text.getBytes(StandardCharsets.UTF_8));
    }
    private void handle(HttpExchange x) throws IOException {
        try (x) {
            String path = x.getRequestURI().getPath();
            if (!host.equals(x.getRequestHeaders().getFirst("Host"))) { json(x,403,"{}"); return; }
            try {
                if ("POST".equals(x.getRequestMethod()) && (path.equals("/action") || path.equals("/photo"))) {
                    if (!("http://"+host).equals(x.getRequestHeaders().getFirst("Origin"))) { json(x,403,"{}"); return; }
                    if (path.equals("/photo")) { photo(x,fields(x.getRequestURI().getRawQuery())); return; }
                    byte[] body = x.getRequestBody().readNBytes(16385);
                    if (body.length > 16384) { json(x,413,"{}"); return; }
                    json(x,200,change(fields(new String(body,StandardCharsets.UTF_8))));
                } else if (!"GET".equals(x.getRequestMethod())) {
                    json(x,405,"{}");
                } else if (path.equals("/photo")) {
                    photo(x,fields(x.getRequestURI().getRawQuery()));
                } else if (path.equals("/state")) {
                    json(x,200,state(fields(x.getRequestURI().getRawQuery())));
                } else {
                    String resource = path.equals("/") ? "/index.html" : path;
                    String type = switch (resource) {
                        case "/index.html" -> "text/html";
                        case "/style.css" -> "text/css";
                        case "/app.js" -> "text/javascript";
                        default -> null;
                    };
                    if (type == null) { json(x,404,"{}"); return; }
                    try (var stream = Web.class.getResourceAsStream("/web"+resource)) {
                        if (stream == null) throw new IOException("Missing web resource");
                        send(x,200,type,stream.readAllBytes());
                    }
                }
            } catch (NumberFormatException e) {
                json(x,400,"{\"error\":\"Введите целое число.\"}");
            } catch (IllegalArgumentException e) {
                json(x,400,"{\"error\":"+quote(e.getMessage())+"}");
            } catch (IOException e) {
                System.err.println(e.getMessage());
                json(x,500,"{\"error\":\"Не удалось прочитать или сохранить данные.\"}");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        Path dir = Files.createDirectories(Path.of("data"));
        try (var channel = FileChannel.open(dir.resolve("session.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) { System.out.println("Завершите другой сеанс RuMe и повторите запуск."); return; }
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
            var web = new Web(dir,server.getAddress().getPort());
            server.createContext("/",web::handle);
            // The default executor handles one exchange at a time.
            server.start();
            System.out.println("RuMe: http://"+web.host+"   (Ctrl+C для выхода)");
            new java.util.concurrent.CountDownLatch(1).await();
        }
    }
}
