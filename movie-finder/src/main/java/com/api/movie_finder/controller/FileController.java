package com.api.movie_finder.controller;

import com.api.movie_finder.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {
    private final FileService fileService;

    @PostMapping("/delete")
    public ResponseEntity<?> deleteCacheFiles(@RequestBody Map<String, Map<String, Integer>> map) {
        log.info("Поступил запрос на удаление!");
        try {
            fileService.deleteCacheFiles(map);
            return ResponseEntity.ok("Файлы успешно удалены!");
        } catch (Exception e) {
            return new ResponseEntity<>("Ошибка при обработке файлов: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
