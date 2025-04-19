package com.api.movie_finder.service;

import com.api.movie_finder.dto.MovieDto;
import com.api.movie_finder.model.Movie;
import com.api.movie_finder.repository.MovieRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class MovieService {

    @Autowired
    private MovieRepository movieRepository;

    public Map<String, Object> findByShot(MultipartFile file) throws IOException, ParseException {
        HttpPost post = new HttpPost("http://localhost:8463/find");
        ObjectMapper mapper = new ObjectMapper();

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addBinaryBody("image", file.getInputStream(), ContentType.DEFAULT_BINARY, file.getContentType());

        HttpEntity entity = builder.build();
        post.setEntity(entity);

        CloseableHttpClient closeableHttpClient = HttpClients.createDefault();
        CloseableHttpResponse response = closeableHttpClient.execute(post);

        String clientResponse = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

        closeableHttpClient.close();
        response.close();

        Map<String, Object> responseValues = mapper.readValue(clientResponse ,Map.class);
        return responseValues;
    }

    @Transactional
    public void uploadNewMovie(MultipartFile file, MultipartFile preview, MovieDto dto) throws Exception {
        long start = System.currentTimeMillis();
        log.info("Начали таймер перед обрезанием!");

        // === Сохраняем movie и превью ===
        Movie movie = new Movie();
        movie.setName(dto.getName());
        movie.setDescription(dto.getDescription());
        movie.setRating(dto.getRating());

        Path previewPath = Path.of("C:\\Users\\rshal\\.privateProjects\\movie-finder\\movie-finder\\src\\main\\resources\\images\\previews");
        Files.createDirectories(previewPath);
        preview.transferTo(previewPath.resolve(preview.getOriginalFilename()));
        movie.setImgUrl("/images/previews/" + preview.getOriginalFilename());
        movieRepository.save(movie);

        // === Подготовка директорий и временного файла ===
        Path outputDir = Path.of("C:\\Users\\rshal\\.privateProjects\\movie-finder\\movie-finder\\src\\main\\resources\\images\\screenshots");
        Files.createDirectories(outputDir);

        File tempVideo = File.createTempFile("video", ".mp4");
        file.transferTo(tempVideo);

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(tempVideo);
        Java2DFrameConverter converter = new Java2DFrameConverter();
        Map<String, Map<String, Integer>> fullPathPhotos = new ConcurrentHashMap<>();

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        try {
            grabber.start();

            int frameRate = (int) grabber.getFrameRate(); // Примерно 25–30
            int frameNumber = 0;
            int secondsPassed = 0;

            Frame frame;
            while ((frame = grabber.grabImage()) != null) {
                if (frameNumber % frameRate == 0) { // Каждую секунду
                    BufferedImage bi = converter.convert(frame);
                    int finalSec = secondsPassed;
                    secondsPassed++;

                    executor.submit(() -> {
                        try {
                            UUID id = UUID.randomUUID();
                            String fileName = "frame_" + finalSec + "_" + id + ".png";
                            Path imagePath = outputDir.resolve(fileName);
                            ImageIO.write(bi, "png", imagePath.toFile());

                            fullPathPhotos.put(imagePath.toAbsolutePath().toString(), Map.of(movie.getName(), finalSec));
                        } catch (IOException e) {
                            log.error("Ошибка при сохранении кадра: " + e.getMessage());
                        }
                    });
                }
                frameNumber++;
            }

            grabber.stop();
        } finally {
            grabber.release();
            tempVideo.delete();
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES); // ждем завершения

        long end = System.currentTimeMillis();
        long duration = end - start;

        log.info("Кадры успешно обработаны за " + (duration / 1000.0) + " сек");

        // === Отправляем данные в Python-сервис ===
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        org.springframework.http.HttpEntity<java.util.Map<String, java.util.Map<String, java.lang.Integer>>> request =
                new org.springframework.http.HttpEntity<>(fullPathPhotos, headers);

        ResponseEntity<Void> response = restTemplate.postForEntity("http://localhost:8463/upload", request, Void.class);
        log.info("Данные отправлены в python-service");
    }

}