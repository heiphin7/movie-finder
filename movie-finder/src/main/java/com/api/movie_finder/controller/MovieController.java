package com.api.movie_finder.controller;

import com.api.movie_finder.dto.MovieDto;
import com.api.movie_finder.model.Movie;
import com.api.movie_finder.service.MovieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Controller
@RequestMapping("/movie")
@Slf4j
public class MovieController {

    @Autowired
    private MovieService movieService;

    @PostMapping("/find")
    public ResponseEntity<?> findMovie(@RequestParam MultipartFile file) {
        try {
            Object result = movieService.findByShot(file).get("result");

            if (result == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Фильм не найден по данному кадру.");
            }

            Map<String, Object> foundData = (Map<String, Object>) result;
            String title = (String) foundData.get("title");
            Integer timecode = (Integer) foundData.get("timecode");

            return ResponseEntity.ok(
                    "Фильм успешно найден! \n\n" +
                            "Название фильма: " + title +
                            "\nТайм-код: " + timecode
            );

        } catch (Exception e) {
            return new ResponseEntity<>("Ошибка при обработе запроса: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> saveNewMovie(@RequestParam MultipartFile mp4,
                                          @RequestParam MultipartFile preview,
                                          @ModelAttribute MovieDto dto)  {
        try {
            movieService.uploadNewMovie(mp4, preview, dto);
            return ResponseEntity.ok("Фильм упешно загружен!");
        } catch (Exception e) {
            return new ResponseEntity<>("Ошибка загрузке: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
