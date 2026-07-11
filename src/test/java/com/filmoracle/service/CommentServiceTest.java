package com.filmoracle.service;

import com.filmoracle.model.Movie;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentServiceTest {

    @Test
    void importedMovieKeyFitsTheDatabaseDoubanIdColumn() throws Exception {
        Movie movie = new Movie();
        movie.setId("import-1720000000000");
        movie.setTitle("导入评论验证电影");
        movie.setYear("2026");
        Method movieKey = CommentService.class.getDeclaredMethod("movieKey", Movie.class);
        movieKey.setAccessible(true);

        String key = (String) movieKey.invoke(null, movie);

        assertTrue(key.startsWith("import-"));
        assertTrue(key.length() <= 20);
    }
}
