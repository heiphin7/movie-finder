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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    public void uploadNewMovie(MultipartFile file, // mp4
                               MultipartFile preview,
                               MovieDto dto) throws Exception {
        // Начали таймер
        long start = System.currentTimeMillis();
        log.info("Начали таймер перед обрезанием!");

        // маппим данные и также сохраняем preview и ставим его в imgUrl
        Movie movie = new Movie();
        movie.setName(dto.getName());
        movie.setDescription(dto.getDescription());
        movie.setRating(dto.getRating());

        Path previewPath = Path.of("C:\\Users\\rshal\\.privateProjects\\movie-finder\\movie-finder\\src\\main\\resources\\images\\previews");

        preview.transferTo(previewPath.resolve(preview.getOriginalFilename()));
        movie.setImgUrl("/images/previews/" + preview.getOriginalFilename());
        movieRepository.save(movie);

        /*
            Дальше нужно каждый второй кадр сохранить и передать на python-service
            почему каждый второй? -> для оптимизации. Если бы мы каждый кадр сохраняли то это сильно
            било бы по производительности, но все еще от этого есть шанс что нужный (!ключевой) кадр
            может не сохраниться
        */

        Path outputDir = Path.of("C:\\Users\\rshal\\.privateProjects\\movie-finder\\movie-finder\\src\\main\\resources\\images\\screenshots");
        Files.createDirectories(outputDir); // если нет — создаём

// Сохраняем входной файл во временный .mp4
        File tempVideo = File.createTempFile("video", ".mp4");
        file.transferTo(tempVideo);

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(tempVideo);
        Map<String, Map<String, Integer>> fullPathPhotos = new HashMap<>();
        Java2DFrameConverter converter = new Java2DFrameConverter();

        try {
            grabber.start();

            double duration = grabber.getLengthInTime() / 1_000_000.0; // сек
            int intervalSeconds = 1;

            for (int sec = 0; sec < (int) duration; sec += intervalSeconds) {
                grabber.setTimestamp(sec * 1_000_000L);

                Frame frame = grabber.grabImage();
                if (frame == null) continue;

                BufferedImage bi = converter.convert(frame);
                UUID id = UUID.randomUUID();
                String fileName = "frame_" + sec + "_" + id + ".png";

                Path imagePath = outputDir.resolve(fileName);
                ImageIO.write(bi, "png", imagePath.toFile());

                // 🔄 Сохраняем сразу полный путь
                fullPathPhotos.put(imagePath.toAbsolutePath().toString(), Map.of(movie.getName(), sec));
            }

            grabber.stop();
        } finally {
            grabber.release();
            tempVideo.delete();
        }

        long end = System.currentTimeMillis();
        long duration = end - start;

        log.info("Операция на Java выполнена за: " + (duration / 1000.0) + " секунд!");
        System.out.println("Выполнено за " + (duration / 1000.0) + " секунд.");

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        org.springframework.http.HttpEntity<Map<String, Map<String, Integer>>> request =
                new org.springframework.http.HttpEntity<>(fullPathPhotos, headers); // todo поменять fullPathPhotos

        org.springframework.http.ResponseEntity<Void> response =
                restTemplate.postForEntity("http://localhost:8463/upload", request, Void.class);
    }

}