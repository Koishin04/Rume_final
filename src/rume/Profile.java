package rume;

public record Profile(int id, String name, int age, String district, int budget) {
    public Profile {
        if (id <= 0 || name.isBlank() || age < 18 || district.isBlank() || budget <= 0)
            throw new IllegalArgumentException("Укажите имя, район, возраст от 18 лет и положительный бюджет.");
    }
}
