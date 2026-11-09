# rsql-criteria

[![Maven Central](https://img.shields.io/maven-central/v/ch.nvaria/rsql-criteria)](https://central.sonatype.com/artifact/ch.nvaria/rsql-criteria)
[![javadoc](https://javadoc.io/badge2/ch.nvaria/rsql-criteria/javadoc.svg)](https://javadoc.io/doc/ch.nvaria/rsql-criteria)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)
[![Java](https://img.shields.io/badge/java-21%2B-blue)](#installation)

Turns [RSQL / FIQL](https://github.com/jirutka/rsql-parser#grammar-and-semantic) query strings such as
`director.lastName==Nolan;year=ge=2000` into JPA Criteria API predicates, so a REST endpoint can accept
a `?filter=` parameter and pass it straight to the database.

- Filters on entity attributes with any JPA provider. Every selector is checked against the JPA metamodel, and
  arguments are converted to the attribute's type.
- Filters on fields inside a JSON column (Hibernate ORM), including JSON written by Avro's JSON encoder.
- Combine the predicate with your own: it is a plain `jakarta.persistence.criteria.Predicate`.

## Installation

Gradle:

```kotlin
implementation("ch.nvaria:rsql-criteria:1.0.0")
```

Maven:

```xml
<dependency>
  <groupId>ch.nvaria</groupId>
  <artifactId>rsql-criteria</artifactId>
  <version>1.0.0</version>
</dependency>
```

Requires Java 21 or later and the Jakarta Persistence 3.2 API. The only other runtime dependency is
`org.antlr:antlr4-runtime`. Filters on JSON columns also need Hibernate ORM 7, which your application brings.
With the Java module system, add `requires ch.nvaria.rsql;`.

## Quick start

```java
CriteriaBuilder cb = em.getCriteriaBuilder();
CriteriaQuery<Movie> query = cb.createQuery(Movie.class);
Root<Movie> movie = query.from(Movie.class);

query.where(RsqlToCriteria.parse("director.lastName==Nolan;year=ge=2000", cb, movie));
List<Movie> result = em.createQuery(query).getResultList();
```

Invalid queries throw `RsqlParseException` (a `RuntimeException`), whose message says what is wrong and is safe to
show to clients, for example `Unknown field 'yaer' on Movie` or
`Unexpected end of query at position 7, expected a value or '('`. Where known, `getSelector()` and `getPosition()`
(1 for the first character) say which comparison and where in the query. Map it to `400 Bad Request` in a REST API,
for example with Spring:

```java
@ExceptionHandler(RsqlParseException.class)
ProblemDetail badFilter(RsqlParseException e) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    e.getSelector().ifPresent(selector -> problem.setProperty("selector", selector));
    e.getPosition().ifPresent(position -> problem.setProperty("position", position));
    return problem;
}
```

## Syntax

| Operator | Meaning | Example |
|---|---|---|
| `==` | equal (with `*`: `LIKE`) | `name==Inception`, `name==Inc*` |
| `!=` | not equal (with `*`: `NOT LIKE`) | `genre!=horror` |
| `=gt=` or `>` | greater than | `year=gt=2000`, `year>2000` |
| `=ge=` or `>=` | greater than or equal | `year=ge=2000` |
| `=lt=` or `<` | less than | `year=lt=2010` |
| `=le=` or `<=` | less than or equal | `year<=2010` |
| `=in=` | in list | `genre=in=(drama,comedy)` |
| `=out=` | not in list | `genre=out=(horror,western)` |
| `=like=` | `LIKE`, `*` is the wildcard | `name=like=*Knight*` |
| `=ilike=` | `LIKE` ignoring case, `*` is the wildcard | `name=ilike=*knight*` |

- **AND** is `;` or `and`, **OR** is `,` or `or`, in any case and with whitespace on both sides
  (`year>2000 AND genre==drama`). AND binds tighter than OR; parentheses group: `genre==drama;(year<1990,year>2010)`.
- **Whitespace** (blanks, tabs, line breaks) between tokens is ignored; inside a value it needs quotes.
- **Case**: whether `==` and `=like=` match case-sensitively depends on the database collation (on PostgreSQL, H2
  and Oracle they usually do). `=ilike=` always ignores case, as `lower(column) LIKE ?`; on large tables that needs
  an index on `lower(column)` to be fast.
- **Quotes** for arguments with spaces or reserved characters: `name=='Kill Bill'` or `name=="Kill Bill"`.
- **Escapes**: a backslash escapes the next character, quoted or not: `name=='O\'Brien'`, `name==a\*b` for a
  literal star, `\\` for a backslash.
- **Wildcards**: an unescaped `*` in `==` / `!=` makes it `LIKE` / `NOT LIKE`. `%` and `_` are ordinary
  characters and match only themselves.
- **Null**: `releaseDate==null` / `releaseDate!=null` test for `IS NULL` / `IS NOT NULL`. In a list, `null` adds
  that test: `genre=in=(drama,null)` is `genre IN ('drama') OR genre IS NULL`, `genre=out=(drama,null)` is
  `genre NOT IN ('drama') AND genre IS NOT NULL`. Quoted, `'null'` is the string "null". As in SQL, `genre!=drama`
  and `genre=out=(drama)` do not match rows where `genre` is null; write `genre!=drama,genre==null` to include them.
- **Paths**: dots navigate to-one associations and embeddables: `director.lastName==Nolan`. Paths through collections
  need the query, see [Collections](#collections).

## Entity attributes

```java
Predicate p = RsqlToCriteria.parse(rsql, cb, root);
```

`root` can be a `Root` or a `Join`, so a filter can also apply to a joined entity:

```java
Join<Movie, Director> director = movie.join("director");
query.where(RsqlToCriteria.parse("lastName==Nolan", cb, director));
```

Arguments are converted to the attribute's Java type:

| Type | Example |
|---|---|
| `String` | `name==Inception` |
| `byte`, `short`, `int`, `long` and their wrappers, `BigInteger` | `year==2010` |
| `float`, `double` and their wrappers, `BigDecimal` | `rating=gt=8.5` |
| `boolean`/`Boolean` (`true` or `false`, any case) | `featured==true` |
| `char`/`Character` (exactly one character) | `grade==A` |
| `UUID` | `id==3f2a…` |
| enums, by name, ignoring case unless that is ambiguous | `genre==drama` |
| `LocalDate`, `LocalDateTime`, `LocalTime` | `2024-06-15`, `2024-06-15T10:00`, `10:00` |
| `OffsetDateTime`, `OffsetTime` | `2024-06-15T10:00+02:00`, `10:00Z` |
| `Instant` (offset required) | `2024-06-15T08:00:00Z`, `2024-06-15T10:00:00+02:00` |
| `ZonedDateTime` | `2024-06-15T10:00+02:00`, `2024-06-15T10:00+02:00[Europe/Zurich]` |
| `Year`, `YearMonth` | `2024`, `2024-06` |

Dates and times use ISO 8601. An unknown selector or an argument of the wrong type throws `RsqlParseException`,
so clients can only filter on mapped attributes.

Ordering operators (`=gt=`, `=lt=`, …) compare the values as the database stores them. Hibernate has no column type
for some Java types, such as `YearMonth`, and stores them serialized as bytes, which do not sort meaningfully. For
such attributes `==`, `!=`, `=in=` and `=out=` work, but to compare them by order, store them as sortable text or
numbers with an `AttributeConverter`:

```java
@Convert(converter = YearMonthConverter.class)   // stores "2024-06", which sorts like the dates
private YearMonth billingPeriod;

class YearMonthConverter implements AttributeConverter<YearMonth, String> {
    public String convertToDatabaseColumn(YearMonth value) { return value == null ? null : value.toString(); }
    public YearMonth convertToEntityAttribute(String column) { return column == null ? null : YearMonth.parse(column); }
}
```

### Collections

To filter through collections (`@OneToMany`, `@ManyToMany`, `@ElementCollection`), pass the query the predicate
is used in. With Spring Data, the `Specification` provides it:

```java
Specification<Movie> spec = (root, query, cb) -> RsqlToCriteria.parse(filter, cb, query, root);
```

| Query | Matches |
|---|---|
| `actors.name==Bale` | movies with at least one actor named Bale |
| `actors.name!=Bale` | movies with no actor named Bale, including movies without actors (same for `=out=`) |
| `actors==null` / `actors!=null` | movies without / with actors |
| `tags==classic` | movies tagged classic (element collection of basic values) |
| `director.movies.tags==cult` | paths may go through several collections and to-one associations |

Each comparison becomes an `EXISTS` subquery, so every row appears once and paging and counts stay correct. Each
comparison is independent: `actors.name==Bale;actors.age=gt=50` may be satisfied by two different actors. A
collection of entities or embeddables cannot be compared to a value itself; name a field of its elements. Map
attributes are not supported. Without the query, paths through collections are rejected.

### Hiding attributes

Annotate an attribute with `@RsqlHidden` to keep it out of filters:

```java
@Entity
public class Director {
    private String lastName;

    @RsqlHidden
    private String email;
}
```

A selector that names a hidden attribute or navigates through one (`director.email`) is rejected with the same
`RsqlParseException` as an unknown attribute, so clients cannot tell that it exists. Hiding an association or
embeddable hides every path below it. Put the annotation on the field or on its getter; both work with field and
property access, and on attributes inherited from a `@MappedSuperclass`. Attributes are visible unless annotated,
so remember to hide new sensitive columns. `@RsqlHidden` does not apply to fields inside JSON columns.

Other client input that names attributes should follow the same rules. Sorting on a hidden attribute, for
example, would reveal the order of its values. `checkPath` applies the filter rules to a single path; with
Spring Data, check the sort properties of the `Pageable`:

```java
Specification<Movie> spec = (root, query, cb) -> {
    pageable.getSort().forEach(order -> RsqlToCriteria.checkPath(order.getProperty(), root));
    return RsqlToCriteria.parse(filter, cb, root);
};
return movies.findAll(spec, pageable);
```

## JSON columns

For an entity that stores a JSON document in one attribute, `parseJsonColumn` filters on fields inside it.
Selectors are JSON paths below the document root: `price` or `$.price`, `dimensions.width`.

```java
// attributes: {"name":"Widget","price":9.99,"stock":100,"available":true,"releaseDate":"2023-06-15"}
Root<Product> product = query.from(Product.class);
query.where(RsqlToCriteria.parseJsonColumn("price=lt=10;available==true", cb, product, "attributes",
        JsonLayout.PLAIN));
```

The values are extracted with `json_value` and compared as text, except for numeric fields, which are cast and
compared as numbers. A field's type comes from:

1. the optional `fieldTypes` map, e.g. `Map.of("price", Double.class, "stock", Integer.class)`;
2. otherwise, for `=gt=`, `=ge=`, `=lt=` and `=le=`, the argument: an ISO 8601 date-time or date is compared as
   text (which sorts correctly), a number numerically;
3. otherwise text.

```java
RsqlToCriteria.parseJsonColumn(rsql, cb, product, "attributes",
        Map.of("price", Double.class, "available", Boolean.class), JsonLayout.PLAIN);
```

Ordering operators on a text field are rejected, and `Boolean` fields allow only `==` and `!=`. A missing field
and a JSON `null` both count as null for `==null` / `!=null`.

**Avro.** With `JsonLayout.AVRO`, values of union-typed fields may be wrapped in an object keyed by their type, as
Avro's JSON encoder writes them: `{"horsepower": {"int": 400}}`. The selector `horsepower` finds the value with
or without the wrapper.

**Requirements.** JSON filters use Hibernate-specific functions and need Hibernate ORM 7 as the JPA provider.
They work on the databases whose Hibernate dialect supports `json_value` (among others PostgreSQL, MySQL,
MariaDB, Oracle, SQL Server, DB2 and H2). On PostgreSQL the library registers a `json_value_text` function
automatically (through Hibernate's `FunctionContributor` mechanism) to work around wrong results of Hibernate's
default `json_value` rendering in filter predicates.

## Limits

Queries are checked against size limits before they are parsed: at most 4096 characters, 16 levels of
parentheses (the parentheses of `=in=` / `=out=` lists count) and 500 arguments per `=in=` / `=out=` list. A
query over a limit throws `RsqlParseException`. This keeps client input from exhausting the parser's stack or the
database's limit on bind parameters. To change them, pass `RsqlLimits` to the overloads that take it:

```java
RsqlLimits limits = RsqlLimits.DEFAULTS.withMaxInArguments(1000);
query.where(RsqlToCriteria.parse(rsql, cb, movie, limits));
```

## Contributing

Issues and pull requests are welcome on [GitHub](https://github.com/nvaria-labs/rsql-criteria). The GitHub
repository is a read-only mirror: accepted pull requests are applied to the main repository and arrive on
GitHub with the next sync, with your authorship preserved.

## License

Apache License 2.0, see [LICENSE](LICENSE).

Maintained by [Nvaria GmbH](https://nvaria.ch).
