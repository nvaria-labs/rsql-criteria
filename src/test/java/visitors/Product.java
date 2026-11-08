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

package visitors;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long movieId;

    private String attributes;

    private String car;

    public Product() {}

    public Product(String attributes) {
        this.attributes = attributes;
    }

    public Product(Long movieId, String attributes) {
        this.movieId = movieId;
        this.attributes = attributes;
    }

    public Long getId() { return id; }
    public Long getMovieId() { return movieId; }
    public String getAttributes() { return attributes; }

    public String getCar() {
        return car;
    }

    public void setCar(String car) {
        this.car = car;
    }
}
