/*
 * Copyright 2024 Nvaria GmbH.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package visitor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public final class RsqlTypeCoercion {

    private RsqlTypeCoercion() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object coerce(String raw, Class<?> type) {
        try {
            if (type == String.class) return raw;
            if (type == Integer.class || type == int.class) return Integer.parseInt(raw);
            if (type == Long.class    || type == long.class) return Long.parseLong(raw);
            if (type == Double.class  || type == double.class) return Double.parseDouble(raw);
            if (type == Float.class   || type == float.class) return Float.parseFloat(raw);
            if (type == BigDecimal.class) return new BigDecimal(raw);
            if (type == Boolean.class || type == boolean.class) return Boolean.parseBoolean(raw);
            if (type == LocalDate.class) return LocalDate.parse(raw);
            if (type == LocalDateTime.class) return LocalDateTime.parse(raw);
            if (type == UUID.class) return UUID.fromString(raw);
            if (type.isEnum()) return Enum.valueOf((Class<Enum>) type, raw);
        } catch (Exception e) {
            throw new RsqlParseException("Cannot coerce '" + raw + "' to " + type.getSimpleName(), e);
        }
        throw new RsqlParseException("Unsupported field type for coercion: " + type.getName());
    }
}
