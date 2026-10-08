package com.skyroute.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/** Enables @CreatedDate / @LastModifiedDate / @CreatedBy on documents. */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
