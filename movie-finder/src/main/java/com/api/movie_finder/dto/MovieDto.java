package com.api.movie_finder.dto;

import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class MovieDto {
    private String name;
    private String description;
    private Double rating;
}
