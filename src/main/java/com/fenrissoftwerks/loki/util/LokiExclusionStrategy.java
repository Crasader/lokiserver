package com.fenrissoftwerks.loki.util;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

/**
 * LokiExclusionStrategy - A class for telling Gson to exclude specific fields from its JSON serialization of
 * associated objects.
 */
public class LokiExclusionStrategy implements ExclusionStrategy {

    public LokiExclusionStrategy() {
    }

    public boolean shouldSkipClass(Class<?> clazz) {
      return false;
    }

    public boolean shouldSkipField(FieldAttributes f) {
      return f.getAnnotation(SkipWhenSerializing.class) != null;
    }
  }