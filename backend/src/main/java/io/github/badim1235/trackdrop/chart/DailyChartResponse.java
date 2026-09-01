package io.github.badim1235.trackdrop.chart;

import io.github.badim1235.trackdrop.catalog.GenreResponse.Genre;
import io.github.badim1235.trackdrop.catalog.MusicProvider;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DailyChartResponse(
	LocalDate date,
	Status status,
	Scope scope,
	Instant asOf,
	List<Item> items,
	Page page,
	DailyQuotaSnapshot quota,
	Actions actions
) {
	public enum Status {
		LIVE
	}

	public record Scope(String type, Genre genre) {
	}

	public record Item(long rank, int voteCount, boolean hasVotedToday, Track track) {
	}

	public record Track(
		UUID id,
		String title,
		String artistName,
		String albumName,
		String albumCoverUrl,
		Integer releaseYear,
		boolean explicit,
		Genre primaryGenre,
		String comment,
		String recommenderNickname,
		Preview preview,
		String externalUrl
	) {
	}

	public record Preview(boolean available, MusicProvider provider, String url) {
	}

	public record Page(int size, boolean hasMore, String nextCursor) {
	}

	public record Actions(boolean canVote) {
	}
}
