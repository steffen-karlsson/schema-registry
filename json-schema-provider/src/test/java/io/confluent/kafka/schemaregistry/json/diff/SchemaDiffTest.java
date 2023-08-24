/*
 * Copyright 2020 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.kafka.schemaregistry.json.diff;

import io.confluent.kafka.schemaregistry.client.rest.entities.Metadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public class SchemaDiffTest {

  @Test
  public void checkJsonSchemaCompatibility() {
    final JSONArray testCases = new JSONArray(Objects.requireNonNull(readFile("diff-schema-examples.json")));
    checkJsonSchemaCompatibility(testCases);
  }

  @Test
  public void checkJsonSchemaCompatibilityForCombinedSchemas() {
    final JSONArray testCases = new JSONArray(Objects.requireNonNull(readFile("diff-combined-schema-examples.json")));
    checkJsonSchemaCompatibility(testCases);
  }

  private void checkJsonSchemaCompatibility(JSONArray testCases) {
    for (final Object testCaseObject : testCases) {
      final JSONObject testCase = (JSONObject) testCaseObject;
      final Schema original = SchemaLoader.load(testCase.getJSONObject("original_schema"));
      final Schema update = SchemaLoader.load(testCase.getJSONObject("update_schema"));
      final JSONArray changes = (JSONArray) testCase.get("changes");
      boolean isCompatible = testCase.getBoolean("compatible");
      final List<String> errorMessages = changes.toList()
          .stream()
          .map(Object::toString)
          .collect(toList());
      final String description = (String) testCase.get("description");

      List<Difference> differences = SchemaDiff.compare(original,
              update,
              getMetadata(testCase, "original"),
              getMetadata(testCase, "update"));
      final List<Difference> incompatibleDiffs = differences.stream()
          .filter(diff -> !SchemaDiff.COMPATIBLE_CHANGES.contains(diff.getType()))
          .collect(Collectors.toList());
      assertThat(description,
          differences.stream()
              .map(change -> change.getType().toString() + " " + change.getJsonPath())
              .collect(toList()),
          is(errorMessages)
      );
      assertEquals(description, isCompatible, incompatibleDiffs.isEmpty());
    }
  }

  private Metadata getMetadata(JSONObject testCase, String version) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      switch (version) {
        case "original":
          if (testCase.has("original_metadata")) {
            return mapper.readValue(testCase.getJSONObject("original_metadata").toString(), Metadata.class);
          }
        case "update":
          if (testCase.has("update_metadata")) {
            return mapper.readValue(testCase.getJSONObject("update_metadata").toString(), Metadata.class);
          }
        default:
          return new Metadata(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet());
      }
    } catch (JsonProcessingException e) {
      return new Metadata(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet());
    }
  }

  @Test
  public void testRecursiveCheck() {
    final Schema original = SchemaLoader.load(new JSONObject(Objects.requireNonNull(readFile("recursive-schema.json"))));
    final Schema newOne = SchemaLoader.load(new JSONObject(Objects.requireNonNull(readFile("recursive-schema.json"))));
    Assert.assertTrue(SchemaDiff.compare(original,
            newOne,
            new Metadata(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet()),
            new Metadata(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet())).isEmpty());
  }

  @Test
  public void testSchemaAddsProperties() {
    final Schema first = SchemaLoader.load(new JSONObject("{}"));

    final Schema second = SchemaLoader.load(new JSONObject(("{\"properties\": {}}")));
    final List<Difference> changes = SchemaDiff.compare(first,
            second,
            new Metadata(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet()),
            new Metadata(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet()));
    // Changing from empty schema to empty object schema is incompatible
    Assert.assertFalse(changes.isEmpty());
  }

  public static String readFile(String fileName) {
    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    InputStream is = classLoader.getResourceAsStream(fileName);
    if (is != null) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      return reader.lines().collect(Collectors.joining(System.lineSeparator()));
    }
    return null;
  }
}