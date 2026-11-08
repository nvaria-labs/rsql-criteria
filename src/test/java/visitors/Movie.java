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

package visitors;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "movie")
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    @Column(name = "release_year")
    private int year;
    private String genre;
    private String actor;
    private LocalDate releaseDate;

    @ManyToOne
    private Director director;

    public Movie() {}

    public Movie(String name, int year, String genre, String actor, Director director) {
        this.name = name;
        this.year = year;
        this.genre = genre;
        this.actor = actor;
        this.director = director;
    }

    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public int getYear() { return year; }
    public String getGenre() { return genre; }
    public String getActor() { return actor; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public Director getDirector() { return director; }
}
