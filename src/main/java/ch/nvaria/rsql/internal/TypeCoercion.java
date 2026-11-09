package ch.nvaria.rsql.internal;

import ch.nvaria.rsql.RsqlParseException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

final class TypeCoercion {

    private TypeCoercion() {}

    /**
     * Registry of raw-String parsers keyed by target type. Primitive and boxed classes
     * are registered separately since {@code int.class != Integer.class}. Enums are not
     * here — they have no single class key and are handled via {@link Class#isEnum()}.
     */
    private static final Map<Class<?>, Function<String, Object>> COERCERS = Map.ofEntries(
        Map.entry(String.class,         raw -> raw),
        Map.entry(Integer.class,        Integer::parseInt),
        Map.entry(int.class,            Integer::parseInt),
        Map.entry(Long.class,           Long::parseLong),
        Map.entry(long.class,           Long::parseLong),
        Map.entry(Double.class,         Double::parseDouble),
        Map.entry(double.class,         Double::parseDouble),
        Map.entry(Float.class,          Float::parseFloat),
        Map.entry(float.class,          Float::parseFloat),
        Map.entry(BigDecimal.class,     BigDecimal::new),
        Map.entry(Boolean.class,        TypeCoercion::parseBoolean),
        Map.entry(boolean.class,        TypeCoercion::parseBoolean),
        Map.entry(OffsetDateTime.class, OffsetDateTime::parse),
        Map.entry(LocalDate.class,      LocalDate::parse),
        Map.entry(LocalDateTime.class,  LocalDateTime::parse),
        Map.entry(UUID.class,           UUID::fromString),
        Map.entry(Short.class,          Short::parseShort),
        Map.entry(short.class,          Short::parseShort),
        Map.entry(Byte.class,           Byte::parseByte),
        Map.entry(byte.class,           Byte::parseByte),
        Map.entry(BigInteger.class,     BigInteger::new),
        Map.entry(Character.class,      TypeCoercion::parseCharacter),
        Map.entry(char.class,           TypeCoercion::parseCharacter),
        Map.entry(Instant.class,        Instant::parse),
        Map.entry(ZonedDateTime.class,  ZonedDateTime::parse),
        Map.entry(OffsetTime.class,     OffsetTime::parse),
        Map.entry(LocalTime.class,      LocalTime::parse),
        Map.entry(Year.class,           Year::parse),
        Map.entry(YearMonth.class,      YearMonth::parse)
    );

    /**
     * The constant named {@code raw}: an exact match, otherwise the only one whose name matches
     * ignoring case. Two constants that differ only in case make the case-insensitive form
     * ambiguous, so it is rejected.
     */
    private static Enum<?> enumConstant(Class<? extends Enum<?>> type, String raw) {
        Enum<?> match = null;
        for (Enum<?> constant : type.getEnumConstants()) {
            if (constant.name().equals(raw)) return constant;
            if (constant.name().equalsIgnoreCase(raw)) {
                if (match != null) throw new IllegalArgumentException("ambiguous: " + raw);
                match = constant;
            }
        }
        if (match == null) throw new IllegalArgumentException("no constant " + raw);
        return match;
    }

    /** Exactly one character. */
    private static Character parseCharacter(String raw) {
        if (raw.length() != 1) throw new IllegalArgumentException("not a single character: " + raw);
        return raw.charAt(0);
    }

    /** Strict: only {@code true} / {@code false} (any case), unlike {@link Boolean#parseBoolean}. */
    private static Boolean parseBoolean(String raw) {
        if (raw.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (raw.equalsIgnoreCase("false")) return Boolean.FALSE;
        throw new IllegalArgumentException("not a boolean: " + raw);
    }

    @SuppressWarnings("unchecked")
    static Object coerce(String raw, Class<?> type) {
        Function<String, Object> parser = COERCERS.get(type);
        try {
            if (parser != null)  return parser.apply(raw);
            if (type.isEnum())   return enumConstant((Class<? extends Enum<?>>) type, raw);
        } catch (Exception e) {
            throw new RsqlParseException("Cannot coerce '" + raw + "' to " + type.getSimpleName(), e);
        }
        throw new RsqlParseException("Fields of type " + type.getSimpleName() + " cannot be compared to a value");
    }

    // ISO-8601 date/time fragments. Classification is by *shape* only — the value is
    // not validated (e.g. month 13), because downstream binding compares dates lexically
    // as strings and never parses them. Order of checks below is significant.
    private static final String DATE      = "\\d{4}-\\d{2}-\\d{2}";
    private static final String TIME      = "\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?";
    private static final String OFFSET    = "(Z|[+-]\\d{2}:\\d{2})";
    private static final Pattern OFFSET_DATETIME = Pattern.compile(DATE + "T" + TIME + OFFSET);
    private static final Pattern LOCAL_DATETIME  = Pattern.compile(DATE + "T" + TIME);
    private static final Pattern LOCAL_DATE      = Pattern.compile(DATE);
    private static final Pattern NUMBER          = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");

    /**
     * Infers an orderable type from the *shape* of an argument value, for un-typed
     * selectors used with an ordering operator (GT/GE/LT/LE). Matches by regex rather
     * than trial-parsing, so the common "not orderable" reject path costs no exceptions.
     */
    static Class<?> inferComparableType(String selector, String raw) {
        if (OFFSET_DATETIME.matcher(raw).matches()) return OffsetDateTime.class;
        if (LOCAL_DATETIME.matcher(raw).matches())  return LocalDateTime.class;
        if (LOCAL_DATE.matcher(raw).matches())      return LocalDate.class;
        if (NUMBER.matcher(raw).matches())          return Double.class;
        throw new RsqlParseException(
            "Field '" + selector + "': cannot infer orderable type from '" + raw + "': not a datetime, date, or number");
    }
}
