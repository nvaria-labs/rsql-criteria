# Cookbook: WildFly

A movie search endpoint with Jakarta REST and Jakarta Persistence on WildFly that accepts an RSQL filter, paging
and sorting:

```
GET /movies/api/movies?filter=director.lastName==Nolan;year=ge=2000&sort=year,desc&page=0&size=20
```

See the [README](README.md) for the query syntax and the full API, and the
[Spring Boot cookbook](COOKBOOK-SPRING-BOOT.md) for the same endpoint with Spring Data.

## Requirements

The library needs Jakarta Persistence 3.2, which WildFly provides from WildFly 40 on (Jakarta EE 11, with
Hibernate ORM 7). This cookbook was tested on WildFly 42.

## Dependencies

The library goes into `WEB-INF/lib`; the Jakarta APIs come from WildFly. Exclude the library's
`jakarta.persistence-api` dependency, so the WAR does not ship a second copy of the API next to WildFly's:

```xml
<dependencies>
  <dependency>
    <groupId>jakarta.platform</groupId>
    <artifactId>jakarta.jakartaee-api</artifactId>
    <version>11.0.0</version>
    <scope>provided</scope>
  </dependency>
  <dependency>
    <groupId>ch.nvaria</groupId>
    <artifactId>rsql-criteria</artifactId>
    <version>1.0.0</version>
    <exclusions>
      <exclusion>
        <groupId>jakarta.persistence</groupId>
        <artifactId>jakarta.persistence-api</artifactId>
      </exclusion>
    </exclusions>
  </dependency>
</dependencies>
```

`WEB-INF/lib` then holds `rsql-criteria` and `antlr4-runtime`.

## Persistence unit

`src/main/resources/META-INF/persistence.xml`, here with WildFly's built-in example data source (H2):

```xml
<persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.2">
  <persistence-unit name="movies">
    <jta-data-source>java:jboss/datasources/ExampleDS</jta-data-source>
    <properties>
      <property name="jakarta.persistence.schema-generation.database.action" value="create"/>
    </properties>
  </persistence-unit>
</persistence>
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
    @Column(name = "release_year") int year;     // YEAR is a reserved word in H2
    @Enumerated(EnumType.STRING) Genre genre;
    LocalDate releaseDate;
    @ManyToOne(fetch = FetchType.LAZY) Director director;
    @ManyToMany List<Actor> actors;
    @ElementCollection Set<String> tags;
}

public enum Genre { DRAMA, COMEDY, HORROR, SCIFI }
```

Selectors use the attribute name, so the filter is still `year=ge=2000`.

## Resource

Without Spring Data, the resource builds the page query and the count query itself. Each query gets its own root
and its own predicate: a predicate belongs to the query it was built for. Passing the query to `parse` enables
filters through collections (`actors.name`, `tags`).

```java
@ApplicationPath("/api")
public class RestApplication extends Application {}

public record MovieDto(Long id, String name, int year, Genre genre, String director) {
    static MovieDto of(Movie m) {
        Director d = m.getDirector();
        return new MovieDto(m.getId(), m.getName(), m.getYear(), m.getGenre(),
                d == null ? null : d.getFirstName() + " " + d.getLastName());
    }
}

public record MoviePage(List<MovieDto> content, int page, int size, long totalElements) {}
```

`jakarta.ws.rs.Path` and `jakarta.persistence.criteria.Path` share a name; import the first and spell out the
second.

```java
@Path("/movies")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Transactional
public class MovieResource {

    private static final int MAX_PAGE_SIZE = 100;

    @PersistenceContext
    EntityManager em;

    @GET
    public MoviePage search(@QueryParam("filter") String filter,
                            @QueryParam("sort") List<String> sort,
                            @QueryParam("page") @DefaultValue("0") int page,
                            @QueryParam("size") @DefaultValue("20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException(
                    RsqlParseExceptionMapper.problem("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE));
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Movie> query = cb.createQuery(Movie.class);
        Root<Movie> movie = query.from(Movie.class);
        query.where(filter(filter, cb, query, movie)).orderBy(orders(sort, cb, movie));
        List<MovieDto> content = em.createQuery(query)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultStream()
                .map(MovieDto::of)
                .toList();

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<Movie> counted = count.from(Movie.class);
        count.select(cb.count(counted)).where(filter(filter, cb, count, counted));
        long total = em.createQuery(count).getSingleResult();

        return new MoviePage(content, page, size, total);
    }

    private static Predicate filter(String filter, CriteriaBuilder cb, CriteriaQuery<?> query, Root<Movie> root) {
        return filter == null || filter.isBlank()
                ? cb.conjunction()
                : RsqlToCriteria.parse(filter, cb, query, root);
    }

    /** {@code sort=year,desc&sort=name}: same rules as filter selectors, plus the id for stable paging. */
    private static List<Order> orders(List<String> sort, CriteriaBuilder cb, Root<Movie> root) {
        List<Order> orders = new ArrayList<>();
        for (String s : sort) {
            String[] parts = s.split(",", 2);
            String property = parts[0].strip();
            String direction = parts.length > 1 ? parts[1].strip().toLowerCase(Locale.ROOT) : "asc";
            if (!direction.equals("asc") && !direction.equals("desc")) {
                throw new BadRequestException(
                        RsqlParseExceptionMapper.problem("Sort direction must be asc or desc: " + s));
            }
            RsqlToCriteria.checkPath(property, root);   // unknown, @RsqlHidden or collection → RsqlParseException
            jakarta.persistence.criteria.Path<?> path = sortPath(root, property);
            orders.add(direction.equals("desc") ? cb.desc(path) : cb.asc(path));
        }
        orders.add(cb.asc(root.get("id")));
        return orders;
    }

    /**
     * Resolves a sort property, left-joining associations: with get() alone, sorting by director.lastName
     * would become an inner join and drop the movies without a director.
     */
    private static jakarta.persistence.criteria.Path<?> sortPath(Root<Movie> root, String property) {
        jakarta.persistence.criteria.Path<?> path = root;
        for (String name : property.split("\\.")) {
            jakarta.persistence.criteria.Path<?> next = path.get(name);
            path = path instanceof From<?, ?> from
                    && next.getModel() instanceof Attribute<?, ?> attribute && attribute.isAssociation()
                    ? from.join(name, JoinType.LEFT)
                    : next;
        }
        return path;
    }
}
```

**Why `@Transactional`.** The injected `EntityManager` is transaction-scoped: its persistence context lives as
long as the current JTA transaction. Without one, every call gets a persistence context that closes right after
the query, so the movies come back detached and reading the lazy `director` in `MovieDto.of` fails with
`LazyInitializationException` (a 500). With `@Transactional`, both queries and the DTO mapping run in one
transaction and one persistence context. An `RsqlParseException` thrown inside it rolls the (read-only)
transaction back and still reaches the exception mapper unchanged. If the DTOs touch no lazy associations, the
annotation is not needed.

## Errors as 400 Bad Request

An `ExceptionMapper` turns `RsqlParseException` into an RFC 9457 problem response. The `problem` helper builds the
same response for the other bad parameters; a plain `BadRequestException` would be an empty 400.

```java
@Provider
public class RsqlParseExceptionMapper implements ExceptionMapper<RsqlParseException> {

    @Override
    public Response toResponse(RsqlParseException e) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        e.getSelector().ifPresent(selector -> extensions.put("selector", selector));
        e.getPosition().ifPresent(position -> extensions.put("position", position));
        return problem(e.getMessage(), extensions);
    }

    public static Response problem(String detail) {
        return problem(detail, Map.of());
    }

    private static Response problem(String detail, Map<String, Object> extensions) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", "Bad Request");
        problem.put("status", 400);
        problem.put("detail", detail);
        problem.putAll(extensions);
        return Response.status(Response.Status.BAD_REQUEST)
                .type("application/problem+json")
                .entity(problem)
                .build();
    }
}
```

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Unknown field 'yaer' on Movie",
  "selector": "yaer",
  "position": 1
}
```

## Calls

URL-encode the filter in real clients (spaces, quotes, `<`, `>`, `&`, `+`, `#`).

| Request (below `/movies/api/movies`) | Finds |
|---|---|
| `?filter=director.lastName==Nolan;year=ge=2000` | Nolan movies from 2000 on |
| `?filter=genre=in=(drama,comedy);name==Inc*` | dramas and comedies starting with "Inc" |
| `?filter=name=ilike=*bill*` | "Kill Bill", "Bill & Ted", any case |
| `?filter=actors.name==Bale` | movies with an actor named Bale |
| `?filter=actors.name!=Bale` | movies without an actor named Bale, including movies without actors |
| `?filter=tags==classic,tags==cult` | movies tagged classic or cult |
| `?filter=releaseDate==null` | movies without a release date |
| `?filter=genre=in=(horror,null)` | horror movies and movies without a genre |
| `?sort=director.lastName&sort=year,desc` | sorted by director (movies without one included), then newest first |
| `?filter=year>2000&page=1&size=2` | the second page of two |
| `?filter=yaer==2000` | 400: `Unknown field 'yaer' on Movie` |
| `?filter=name==` | 400: `Unexpected end of query at position 7, expected a value or '('` |
| `?sort=director.email` | 400: `Unknown field 'email' on Director` |
| `?sort=actors.name` | 400: `Field 'actors' on Movie is a collection and cannot be used` |
| `?size=1000` | 400: `page must be >= 0 and size between 1 and 100` |

## Variations

**Add your own conditions.** Combine the client filter with the rules of your application:

```java
query.where(cb.and(cb.isTrue(movie.get("published")), filter(filter, cb, query, movie)));
```

**Change the limits.** Queries are limited to 4096 characters, 16 levels of parentheses and 500 list arguments:

```java
private static final RsqlLimits LIMITS = RsqlLimits.DEFAULTS.withMaxInArguments(1000);
...
RsqlToCriteria.parse(filter, cb, query, root, LIMITS);
```

**Filter a JSON column.** `parseJsonColumn` uses Hibernate classes. WildFly makes its Hibernate module visible to
deployments that contain a persistence unit, so no extra configuration should be needed. If you get a
`NoClassDefFoundError` for `org.hibernate` classes, add the module in `WEB-INF/jboss-deployment-structure.xml`:

```xml
<jboss-deployment-structure>
  <deployment>
    <dependencies>
      <module name="org.hibernate"/>
    </dependencies>
  </deployment>
</jboss-deployment-structure>
```
