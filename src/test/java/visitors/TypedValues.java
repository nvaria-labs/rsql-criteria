package visitors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;

/** One attribute per additional built-in type. */
@Entity
@Table(name = "typed_values")
public class TypedValues {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    String label;
    Instant instant;
    ZonedDateTime zoned;
    OffsetTime offsetTime;
    LocalTime timeOfDay;
    Year yearValue;
    @Convert(converter = YearMonthConverter.class)
    YearMonth yearMonth;
    Short shortValue;
    short shortPrimitive;
    Byte byteValue;
    byte bytePrimitive;
    BigInteger bigInteger;
    Character charValue;
    char charPrimitive;
    @Enumerated(EnumType.STRING)
    Rating rating;

    public enum Rating { G, PG, PG_13, R }

    public TypedValues() {}

    public String getLabel() { return label; }

    /** Stores {@code 2024-06} as text, which sorts correctly. */
    public static class YearMonthConverter implements AttributeConverter<YearMonth, String> {
        @Override public String convertToDatabaseColumn(YearMonth value) { return value == null ? null : value.toString(); }
        @Override public YearMonth convertToEntityAttribute(String db) { return db == null ? null : YearMonth.parse(db); }
    }
}
