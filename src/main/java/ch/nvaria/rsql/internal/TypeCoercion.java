/*
 * Copyright 2024-2026 Nvaria GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.nvaria.rsql.internal;

import ch.nvaria.rsql.RsqlParseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
        Map.entry(Boolean.class,        Boolean::parseBoolean),
        Map.entry(boolean.class,        Boolean::parseBoolean),
        Map.entry(OffsetDateTime.class, OffsetDateTime::parse),
        Map.entry(LocalDate.class,      LocalDate::parse),
        Map.entry(LocalDateTime.class,  LocalDateTime::parse),
        Map.entry(UUID.class,           UUID::fromString)
    );

    @SuppressWarnings("unchecked")
    static Object coerce(String raw, Class<?> type) {
        Function<String, Object> parser = COERCERS.get(type);
        try {
            if (parser != null)  return parser.apply(raw);
            if (type.isEnum())   return Enum.valueOf((Class<Enum>) type, raw);
        } catch (Exception e) {
            throw new RsqlParseException("Cannot coerce '" + raw + "' to " + type.getSimpleName(), e);
        }
        throw new RsqlParseException("Unsupported field type for coercion: " + type.getName());
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
