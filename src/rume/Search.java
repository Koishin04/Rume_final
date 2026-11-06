package rume;

import java.util.Comparator;

public record Search(String district, int limit, Comparator<Profile> ranking) {
    boolean accepts(Profile p) {
        return (district.isEmpty() || district.equalsIgnoreCase(p.district())) && p.budget() <= limit;
    }

    public static class Builder {
        private String district = "";
        private int limit = Integer.MAX_VALUE;
        private Comparator<Profile> ranking = Comparator.comparingInt(Profile::id);

        public Builder district(String value) { district = value; return this; }
        public Builder maxBudget(int value) {
            if (value <= 0) throw new IllegalArgumentException("Бюджет должен быть положительным.");
            limit = value;
            return this;
        }
        public Builder cheapestFirst() {
            ranking = Comparator.comparingInt(Profile::budget).thenComparingInt(Profile::id);
            return this;
        }
        public Search build() { return new Search(district, limit, ranking); }
    }
}
