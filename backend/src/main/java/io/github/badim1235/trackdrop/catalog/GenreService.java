package io.github.badim1235.trackdrop.catalog;

import io.github.badim1235.trackdrop.catalog.GenreResponse.Genre;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class GenreService {
	private final JdbcClient jdbcClient;

	GenreService(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public GenreResponse activeGenres() {
		return new GenreResponse(jdbcClient.sql("""
				SELECT id, code, display_name, sort_order
				FROM genres
				WHERE active = TRUE
				ORDER BY sort_order
				""")
			.query((row, rowNumber) -> new Genre(
				row.getObject("id", UUID.class),
				row.getString("code"),
				row.getString("display_name"),
				row.getInt("sort_order")))
			.list());
	}
}
