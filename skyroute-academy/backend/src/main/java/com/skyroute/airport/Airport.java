package com.skyroute.airport;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Reference data. The IATA code ("DFW") is the natural key, so we use it as _id. */
@Document("airports")
public record Airport(@Id String code, String name, String city, String country, String timeZone) {
}
