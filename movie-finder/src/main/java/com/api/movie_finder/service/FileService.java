package com.api.movie_finder.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Service
@Slf4j
public class FileService {
    public void deleteCacheFiles(Map<String, Map<String, Integer>> map) {
        for (Map.Entry<String, Map<String, Integer>> entry: map.entrySet()) {
            try {
                Path path = Paths.get(entry.getKey());
                Files.deleteIfExists(path);
            } catch (Exception e) {
                log.error("Ошибка при удалении кэша: " + e.getMessage());
            }
        }
    }
}
