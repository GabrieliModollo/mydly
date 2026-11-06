package com.mydly.entries.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EntryRepository extends JpaRepository<Entry, UUID> {
}
