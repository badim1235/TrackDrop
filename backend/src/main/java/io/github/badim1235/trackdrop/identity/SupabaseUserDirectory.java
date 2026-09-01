package io.github.badim1235.trackdrop.identity;

public interface SupabaseUserDirectory {

	boolean existsByEmail(String normalizedEmail);
}
