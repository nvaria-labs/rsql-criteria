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

Invalid queries throw `RsqlParseException` (a `RuntimeException`), whose message says what is wrong, for example
`Unknown field 'yaer' on Movie`. Map it to `400 Bad Request` in a REST API.

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

- **AND** is `;` or ` and `, **OR** is `,` or ` or `. AND binds tighter than OR; parentheses group:
  `genre==drama;(year<1990,year>2010)`.
- **Quotes** for arguments with spaces or reserved characters: `name=='Kill Bill'` or `name=="Kill Bill"`.
- **Escapes**: a backslash escapes the next character, quoted or not: `name=='O\'Brien'`, `name==a\*b` for a
  literal star, `\\` for a backslash.
- **Wildcards**: an unescaped `*` in `==` / `!=` makes it `LIKE` / `NOT LIKE`. `%` and `_` are ordinary
  characters and match only themselves. Whether matching is case-sensitive depends on your database collation.
- **Null**: `releaseDate==null` / `releaseDate!=null` test for `IS NULL` / `IS NOT NULL`. Quoted, `'null'` is the
  string "null".
- **Paths**: dots navigate to-one associations and embeddables: `director.lastName==Nolan`.

## Entity attributes

```java
Predicate p = RsqlToCriteria.parse(rsql, cb, root);
```

`root` can be a `Root` or a `Join`, so a filter can also apply to a joined entity:

```java
Join<Movie, Director> director = movie.join("director");
query.where(RsqlToCriteria.parse("lastName==Nolan", cb, director));
```

Arguments are converted to the attribute's Java type: `String`, `int`/`Integer`, `long`/`Long`,
`double`/`Double`, `float`/`Float`, `BigDecimal`, `boolean`/`Boolean`, `UUID`, enums (by name), and
`LocalDate`, `LocalDateTime` and `OffsetDateTime` in ISO 8601 format (`2024-06-15`, `2024-06-15T10:00`,
`2024-06-15T10:00+02:00`). An unknown selector or an argument of the wrong type throws `RsqlParseException`, so
clients can only filter on mapped attributes.

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

## Contributing

Issues and pull requests are welcome on [GitHub](https://github.com/nvaria-labs/rsql-criteria). The GitHub
repository is a read-only mirror: accepted pull requests are applied to the main repository and arrive on
GitHub with the next sync, with your authorship preserved.

## License

Apache License 2.0, see [LICENSE](LICENSE).

Maintained by [Nvaria GmbH](https://nvaria.ch).
