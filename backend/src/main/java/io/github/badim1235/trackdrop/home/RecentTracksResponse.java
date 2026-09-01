package io.github.badim1235.trackdrop.home;

import io.github.badim1235.trackdrop.home.HomeResponse.TrackCard;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaSnapshot;
import java.time.Instant;
import java.util.List;

public record RecentTracksResponse(
	Instant asOf,
	List<TrackCard> items,
	Page page,
	DailyQuotaSnapshot quota
) {
	public record Page(int size, boolean hasMore, String nextCursor) {
	}
}
