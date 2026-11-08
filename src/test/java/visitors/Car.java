package visitors;

public class Car {

    private String name;
    private String favorite_color;
    private Integer horsepower;

    public Car() {
    }

    public Car(String name, String favorite_color, Integer horsepower) {
        this.name = name;
        this.favorite_color = favorite_color;
        this.horsepower = horsepower;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFavorite_color() {
        return favorite_color;
    }

    public void setFavorite_color(String favorite_color) {
        this.favorite_color = favorite_color;
    }

    public Integer getHorsepower() {
        return horsepower;
    }

    public void setHorsepower(Integer horsepower) {
        this.horsepower = horsepower;
    }

    @Override
    public String toString() {
        return "Car{" +
                "name='" + name + '\'' +
                ", favorite_color='" + favorite_color + '\'' +
                ", horsepower=" + horsepower +
                '}';
    }
}
