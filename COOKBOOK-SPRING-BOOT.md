# Cookbook: Spring Boot 4

A movie search endpoint with Spring Boot 4 and Spring Data JPA that accepts an RSQL filter, paging and sorting:

```
GET /movies?filter=director.lastName==Nolan;year=ge=2000&sort=year,desc&page=0&size=20
```

`RsqlToCriteria.parse` returns a plain `Predicate`, so it fits into a Spring Data `Specification`. See the
[README](README.md) for the query syntax and the full API.

## Dependencies

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("ch.nvaria:rsql-criteria:1.0.0")
    runtimeOnly("org.postgresql:postgresql")
}
```

## Entities

Getters are omitted for brevity. Read entities through getters, not fields: a lazy association is a Hibernate
proxy, and the fields of a proxy are never loaded.

```java
@Entity
public class Director {
    @Id @GeneratedValue Long id;
    String firstName;
    String lastName;

    @RsqlHidden          // never filterable or sortable, e.g. director.email==x is "Unknown field"
    String email;
}

@Entity
public class Actor {
    @Id @GeneratedValue Long id;
    String name;
    int age;
}

@Entity
public class Movie {
    @Id @GeneratedValue Long id;
    String name;
    int year;
    @Enumerated(EnumType.STRING) Genre genre;
    LocalDate releaseDate;
    @ManyToOne(fetch = FetchType.LAZY) Director director;
    @ManyToMany List<Actor> actors;
    @ElementCollection Set<String> tags;
}

public enum Genre { DRAMA, COMEDY, HORROR, SCIFI }
```

## Repository

```java
public interface MovieRepository extends JpaRepository<Movie, Long>, JpaSpecificationExecutor<Movie> {}
```

## Controller

Return DTOs rather than entities, so lazy associations and hidden attributes never end up in the JSON.

```java
public record MovieDto(Long id, String name, int year, Genre genre, String director) {
    static MovieDto of(Movie m) {
        Director d = m.getDirector();
        return new MovieDto(m.getId(), m.getName(), m.getYear(), m.getGenre(),
                d == null ? null : d.getFirstName() + " " + d.getLastName());
    }
}

@RestController
@RequestMapping("/movies")
class MovieController {

    private final MovieRepository movies;

    MovieController(MovieRepository movies) {
        this.movies = movies;
    }

    @GetMapping
    PagedModel<MovieDto> search(@RequestParam(required = false) String filter, Pageable pageable) {
        Specification<Movie> spec = (root, query, cb) -> {
            // sort properties follow the same rules as filter selectors (unknown or @RsqlHidden → 400)
            pageable.getSort().forEach(order -> RsqlToCriteria.checkPath(order.getProperty(), root));
            return filter == null || filter.isBlank()
                    ? cb.conjunction()
                    : RsqlToCriteria.parse(filter, cb, query, root);   // passing query enables actors.name, tags
        };
        return new PagedModel<>(movies.findAll(spec, pageable).map(MovieDto::of));
    }
}
```

Spring Data calls the specification for both the page query and the count query, and applies it before the sort.
So an invalid sort property fails with `RsqlParseException` (400) instead of Spring's `PropertyReferenceException`
(500).

`MovieDto.of` reads the lazy `director` after the repository call returned. That works because Spring Boot keeps
the persistence context open for the whole request (`spring.jpa.open-in-view`, on by default). If you turn it off,
map the DTOs in a `@Transactional(readOnly = true)` service method instead.

## Errors as 400 Bad Request

```java
@RestControllerAdvice
class RsqlErrors {

    @ExceptionHandler(RsqlParseException.class)
    ProblemDetail badFilter(RsqlParseException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        e.getSelector().ifPresent(selector -> problem.setProperty("selector", selector));
        e.getPosition().ifPresent(position -> problem.setProperty("position", position));
        return problem;
    }
}
```

```json
{
  "detail": "Unknown field 'yaer' on Movie",
  "instance": "/movies",
  "status": 400,
  "title": "Bad Request",
  "selector": "yaer",
  "position": 1
}
```

The response has the content type `application/problem+json`. Spring leaves out `"type"` when it is the default
`about:blank`.

## Calls

URL-encode the filter in real clients (spaces, quotes, `<`, `>`, `&`, `+`, `#`).

| Request | Finds |
|---|---|
| `/movies?filter=director.lastName==Nolan;year=ge=2000` | Nolan movies from 2000 on |
| `/movies?filter=genre=in=(drama,comedy);name==Inc*` | dramas and comedies starting with "Inc" |
| `/movies?filter=name=ilike=*bill*` | "Kill Bill", "Bill & Ted", any case |
| `/movies?filter=actors.name==Bale` | movies with an actor named Bale |
| `/movies?filter=tags==classic,tags==cult` | movies tagged classic or cult |
| `/movies?filter=releaseDate==null` | movies without a release date |
| `/movies?filter=genre=in=(horror,null)` | horror movies and movies without a genre |
| `/movies?sort=director.lastName&sort=year,desc` | sorted by director, then newest first |
| `/movies?filter=yaer==2000` | 400: `Unknown field 'yaer' on Movie` |
| `/movies?sort=director.email` | 400: `Unknown field 'email' on Director` |

## Variations

**Add your own conditions.** The client filter is just one predicate; combine it with the rules of your
application:

```java
Specification<Movie> published = (root, query, cb) -> cb.isTrue(root.get("published"));
movies.findAll(published.and(spec), pageable);
```

**Change the limits.** Queries are limited to 4096 characters, 16 levels of parentheses and 500 list arguments.
To change them:

```java
private static final RsqlLimits LIMITS = RsqlLimits.DEFAULTS.withMaxInArguments(1000);
...
RsqlToCriteria.parse(filter, cb, query, root, LIMITS);
```

**Cap the page size.** The filter is limited, but the page size is up to Spring, which allows up to 2000 by
default and silently lowers larger values to the maximum:

```properties
spring.data.web.pageable.max-page-size=100
```

**Filter a JSON column.** For a `@JdbcTypeCode(SqlTypes.JSON)` attribute, use `parseJsonColumn` in the same way:

```java
Specification<Product> spec = (root, query, cb) ->
        RsqlToCriteria.parseJsonColumn(filter, cb, root, "attributes",
                Map.of("price", Double.class), JsonLayout.PLAIN);
```
