package rume;

import java.nio.file.Files;
import java.util.List;

public class MatchingTest {
    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("rume-match-test");
        try {
            var session = new Session(dir);
            session.save("profile","1","A","21","North","70");
            session.save("profile","2","B","22","North","80");
            session.save("like","1","2");
            if (session.counter.total() != 0) throw new AssertionError("One like is not a match");
            session.save("like","2","1");
            session.save("like","2","1");
            if (session.counter.total() != 1 || session.inbox.read(1).size() != 1 || session.inbox.read(2).size() != 1)
                throw new AssertionError("Expected one event and one message per participant");
            var reopened = new Session(dir);
            if (reopened.counter.total() != 0 || !reopened.people.matches(1).equals(List.of(reopened.profiles.get(2))))
                throw new AssertionError("Historical matches must survive without incrementing the new session count");
            System.out.println("MatchingTest OK");
        } finally {
            Files.deleteIfExists(dir.resolve("history.tsv"));
            Files.deleteIfExists(dir.resolve("history.tmp"));
            Files.delete(dir);
        }
    }
}
