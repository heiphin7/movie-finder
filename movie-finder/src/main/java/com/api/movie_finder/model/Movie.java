package com.api.movie_finder.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Table(name = "movies")
@Entity
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column
    private String name;

    @Column
    private String description;

    @Column
    private Double rating;

    @Column
    private String imgUrl;
}
