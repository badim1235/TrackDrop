package io.github.badim1235.trackdrop.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/genres")
public class GenreController {
	private final GenreService genreService;

	GenreController(GenreService genreService) {
		this.genreService = genreService;
	}

	@GetMapping
	GenreResponse genres() {
		return genreService.activeGenres();
	}
}
