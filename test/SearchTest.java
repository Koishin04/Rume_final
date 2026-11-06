package rume;

import java.util.List;

public class SearchTest {
    public static void main(String[] args) {
        var people = new Roommates();
        var user = new Profile(1,"A",22,"North",100);
        var boundary = new Profile(2,"B",21,"North",80);
        var cheaper = new Profile(3,"C",23,"North",65);
        var outside = new Profile(4,"D",24,"South",50);
        for (var p : List.of(user,boundary,cheaper,outside)) people.saveProfile(p);
        var builder = new Search.Builder().district("north").maxBudget(80);
        if (!people.find(1,builder.build()).equals(List.of(boundary,cheaper)))
            throw new AssertionError("District, inclusive limit or ID ordering failed");
        if (!people.find(1,builder.cheapestFirst().build()).equals(List.of(cheaper,boundary)))
            throw new AssertionError("Budget ordering failed");
        System.out.println("SearchTest OK");
    }
}
